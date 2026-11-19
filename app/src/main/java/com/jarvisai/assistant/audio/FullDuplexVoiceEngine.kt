package com.jarvisai.assistant.audio

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import java.util.ArrayDeque
import kotlin.math.sqrt

/**
 * Real-time Full-Duplex Acoustic & Paralinguistic Voice Engine for JARVIS AI.
 *
 * Brings natural phone-call full-duplex conversational dynamics to Android Live Voice
 * without breaking speaker volume or microphone gain across Android OEM devices:
 * 1. Self-Calibrating Double-Talk & Instant Barge-In Detection:
 *    - Measures the device's real live speaker-to-mic bleed (`adaptiveEchoFloorRms`) during
 *      the opening ~200ms of each AI playback turn so it automatically adapts to any phone
 *      model and speaker volume level (low, medium, or max).
 *    - Triggers barge-in in ~80ms (2 frames) on headsets/Bluetooth or ~80-120ms (2-3 frames)
 *      on phone loudspeaker whenever human voice clearly rises above the measured echo floor.
 * 2. Zero-Syllable-Loss Pre-Roll Ring Buffer (200ms):
 *    - Maintains a circular buffer of recent 40ms gained PCM frames so the moment a barge-in
 *      triggers, the user's opening syllables ("Ruko...", "Suno...", "Wait...") are flushed
 *      immediately to the Live WebSocket without clipping.
 * 3. Lightweight Paralinguistic Prosody Telemetry:
 *    - Tracks live speaking intensity and burstiness to classify conversational delivery tone.
 * 4. Full-Duplex & Paralinguistic System Directive:
 *    - Guides native-audio dialogue for natural phone-call turn-taking, graceful mid-sentence
 *      interruption recovery, hesitation patience, and emotional tone mirroring.
 */
class FullDuplexVoiceEngine(
    private val vadManager: SileroVadManager = SileroVadManager(
        energyThreshold = 180.0,
        speechConfidenceThreshold = 0.42f
    )
) {

    enum class ProsodyTone(val label: String) {
        CALM("Calm"),
        ENERGETIC("Energetic"),
        HESITANT("Hesitant"),
        INTERRUPTING("Barge-In")
    }

    data class PlaybackFrameDecision(
        val shouldTriggerBargeIn: Boolean,
        val shouldStreamFrameImmediately: Boolean,
        val nearEndRms: Float,
        val farEndRms: Float,
        val doubleTalkRatio: Float,
        val prosodyTone: ProsodyTone,
        val reason: String
    )

    // Decaying envelope of far-end AI speaker output (0f..1f)
    @Volatile
    private var farEndEnvelopeRms: Float = 0f

    @Volatile
    private var lastFarEndFrameTimestampMs: Long = 0L

    // Real-time measured mic echo floor while AI is speaking cleanly on the current phone & volume
    @Volatile
    private var adaptiveEchoFloorRms: Float = INITIAL_ECHO_FLOOR_RMS

    // Opening frames of a fresh playback turn used to calibrate real speaker-to-mic bleed
    @Volatile
    private var playbackCalibrationFrames: Int = 0

    private var consecutiveDoubleTalkFrames: Int = 0
    private var consecutiveCalmFrames: Int = 0
    private var lastBargeInFiredAtMs: Long = 0L

    // 5 x 40ms = 200ms circular pre-roll ring buffer of 16kHz PCM16 frames
    private val preRollBuffer = ArrayDeque<ByteArray>(MAX_PREROLL_FRAMES + 1)

    fun reset() {
        farEndEnvelopeRms = 0f
        lastFarEndFrameTimestampMs = 0L
        adaptiveEchoFloorRms = INITIAL_ECHO_FLOOR_RMS
        playbackCalibrationFrames = 0
        consecutiveDoubleTalkFrames = 0
        consecutiveCalmFrames = 0
        vadManager.reset()
        synchronized(preRollBuffer) {
            preRollBuffer.clear()
        }
    }

    /**
     * Called when a new AI playback turn starts so the echo floor quickly re-calibrates
     * to the user's current speaker volume level.
     */
    fun onPlaybackTurnStarted() {
        playbackCalibrationFrames = 0
        consecutiveDoubleTalkFrames = 0
        consecutiveCalmFrames = 0
        adaptiveEchoFloorRms = INITIAL_ECHO_FLOOR_RMS
    }

    /**
     * Feeds every outgoing AI AudioTrack PCM chunk so the engine tracks far-end playback activity.
     */
    fun reportFarEndPlaybackFrame(pcmBytes: ByteArray, length: Int = pcmBytes.size) {
        if (length < 2) return
        val rms = computeNormalizedRms(pcmBytes, length)
        val now = System.currentTimeMillis()
        farEndEnvelopeRms = maxOf(rms, farEndEnvelopeRms * 0.72f).coerceIn(0f, 1f)
        lastFarEndFrameTimestampMs = now
    }

    fun markFarEndSilent() {
        farEndEnvelopeRms = 0f
        playbackCalibrationFrames = 0
        consecutiveDoubleTalkFrames = 0
        consecutiveCalmFrames = 0
    }

    /**
     * Pushes a copy of the latest 40ms mic frame into the 200ms circular pre-roll buffer.
     */
    fun pushPreRollFrame(pcmBytes: ByteArray, length: Int) {
        if (length <= 0) return
        val copy = pcmBytes.copyOf(length)
        synchronized(preRollBuffer) {
            if (preRollBuffer.size >= MAX_PREROLL_FRAMES) {
                preRollBuffer.removeFirst()
            }
            preRollBuffer.addLast(copy)
        }
    }

    /**
     * Drains the buffered 200ms pre-roll frames in chronological order when barge-in fires
     * so the opening syllable of the user's interruption is never lost.
     */
    fun drainPreRollFrames(): List<ByteArray> {
        synchronized(preRollBuffer) {
            if (preRollBuffer.isEmpty()) return emptyList()
            val drained = ArrayList<ByteArray>(preRollBuffer)
            preRollBuffer.clear()
            return drained
        }
    }

    /**
     * Evaluates a 40ms **gained** near-end microphone PCM frame while JARVIS is actively playing audio.
     *
     * Expects the same 1.55x gained PCM scale used during normal listening so thresholds stay
     * consistent across every Android device.
     */
    fun evaluateDuringPlayback(
        gainedMicPcm: ByteArray,
        bytesRead: Int,
        ambientNoiseRms: Float,
        isExternalHeadset: Boolean
    ): PlaybackFrameDecision {
        val now = System.currentTimeMillis()
        val elapsedSinceFarEnd = now - lastFarEndFrameTimestampMs
        val decayedFarEnd = if (elapsedSinceFarEnd > FAR_END_REVERB_TAIL_MS) {
            0f
        } else {
            val decay = 1f - (elapsedSinceFarEnd.toFloat() / FAR_END_REVERB_TAIL_MS.toFloat())
            (farEndEnvelopeRms * decay).coerceIn(0f, 1f)
        }

        val nearRms = computeNormalizedRms(gainedMicPcm, bytesRead)
        val vadResult = vadManager.processFrame(gainedMicPcm, bytesRead)
        val zcr = computeZeroCrossingRate(gainedMicPcm, bytesRead)
        val isHumanVoiceBand = zcr in 0.012f..0.34f

        // Always keep the last 200ms ready in the ring buffer for instant pre-roll replay
        pushPreRollFrame(gainedMicPcm, bytesRead)

        // Short cooldown guard so we don't fire duplicate barge-in events within 280ms
        if (now - lastBargeInFiredAtMs < BARGE_IN_COOLDOWN_MS) {
            consecutiveDoubleTalkFrames = 0
            return PlaybackFrameDecision(
                shouldTriggerBargeIn = false,
                shouldStreamFrameImmediately = isExternalHeadset,
                nearEndRms = nearRms,
                farEndRms = decayedFarEnd,
                doubleTalkRatio = 0f,
                prosodyTone = ProsodyTone.CALM,
                reason = "Barge-in cooldown active"
            )
        }

        if (isExternalHeadset) {
            // Headphones / Bluetooth: near-zero speaker echo -> true unrestricted full-duplex
            val headsetThreshold = maxOf(ambientNoiseRms * 1.9f, 0.016f)
            val isUserTalking = nearRms >= headsetThreshold &&
                isHumanVoiceBand &&
                (vadResult.isSpeech || nearRms >= 0.028f)
            if (isUserTalking) {
                consecutiveDoubleTalkFrames++
                consecutiveCalmFrames = 0
            } else {
                consecutiveCalmFrames++
                if (consecutiveCalmFrames >= 2) {
                    consecutiveDoubleTalkFrames = 0
                }
            }
            val trigger = consecutiveDoubleTalkFrames >= HEADSET_BARGE_IN_FRAMES
            if (trigger) {
                consecutiveDoubleTalkFrames = 0
                lastBargeInFiredAtMs = now
            }
            return PlaybackFrameDecision(
                shouldTriggerBargeIn = trigger,
                shouldStreamFrameImmediately = true,
                nearEndRms = nearRms,
                farEndRms = decayedFarEnd,
                doubleTalkRatio = if (headsetThreshold > 0f) nearRms / headsetThreshold else 0f,
                prosodyTone = if (trigger) ProsodyTone.INTERRUPTING else if (nearRms > 0.14f) ProsodyTone.ENERGETIC else ProsodyTone.CALM,
                reason = if (trigger) {
                    "Headset full-duplex voice barge-in (nearRms=${"%.3f".format(nearRms)})"
                } else {
                    "Headset full-duplex monitoring"
                }
            )
        }

        // Phone Loudspeaker Path:
        // During the first ~200ms (5 frames) of AI playback, rapidly learn the actual speaker-to-mic
        // echo level on this specific phone & volume setting so we neither self-trigger on loud
        // speaker playback nor miss normal human voice at medium/low speaker volume.
        if (playbackCalibrationFrames < ECHO_CALIBRATION_FRAMES) {
            playbackCalibrationFrames++
            if (decayedFarEnd > 0.015f) {
                adaptiveEchoFloorRms = if (playbackCalibrationFrames == 1) {
                    nearRms.coerceIn(0.012f, 0.22f)
                } else {
                    maxOf(adaptiveEchoFloorRms * 0.65f + nearRms * 0.35f, nearRms * 0.90f)
                        .coerceIn(0.012f, 0.22f)
                }
            }
            // Still allow an unmistakable close-range shout/voice during calibration
            val urgentOverride = nearRms >= maxOf(adaptiveEchoFloorRms * 2.1f, 0.14f) && isHumanVoiceBand
            if (!urgentOverride) {
                return PlaybackFrameDecision(
                    shouldTriggerBargeIn = false,
                    shouldStreamFrameImmediately = false,
                    nearEndRms = nearRms,
                    farEndRms = decayedFarEnd,
                    doubleTalkRatio = 0f,
                    prosodyTone = ProsodyTone.CALM,
                    reason = "Calibrating loudspeaker echo floor (${playbackCalibrationFrames}/$ECHO_CALIBRATION_FRAMES)"
                )
            }
        }

        // Compute dynamic double-talk threshold purely from measured ambient noise + real measured echo floor
        val expectedEchoCeiling = maxOf(
            ambientNoiseRms * 2.3f,
            adaptiveEchoFloorRms * 1.38f,
            0.028f
        ).coerceAtMost(0.28f)

        val ratio = if (expectedEchoCeiling > 0.001f) nearRms / expectedEchoCeiling else 0f
        val isStrongCloseVoice = nearRms >= maxOf(adaptiveEchoFloorRms * 1.75f, 0.058f) && isHumanVoiceBand
        val isHumanDoubleTalk = nearRms >= expectedEchoCeiling &&
            isHumanVoiceBand &&
            (vadResult.isSpeech || vadResult.confidence >= 0.42f || ratio >= 1.25f)

        if (isHumanDoubleTalk || isStrongCloseVoice) {
            consecutiveDoubleTalkFrames++
            consecutiveCalmFrames = 0
        } else {
            // Smoothly adapt the echo floor on clean playback frames (no human double-talk)
            if (decayedFarEnd > 0.015f && nearRms < expectedEchoCeiling) {
                adaptiveEchoFloorRms = (adaptiveEchoFloorRms * 0.88f + nearRms * 0.12f)
                    .coerceIn(0.012f, 0.20f)
            }
            consecutiveCalmFrames++
            if (consecutiveCalmFrames >= 2) {
                consecutiveDoubleTalkFrames = 0
            }
        }

        val requiredFrames = if (isStrongCloseVoice) FAST_SPEAKER_BARGE_IN_FRAMES else SPEAKER_BARGE_IN_FRAMES
        val trigger = consecutiveDoubleTalkFrames >= requiredFrames
        if (trigger) {
            consecutiveDoubleTalkFrames = 0
            lastBargeInFiredAtMs = now
        }

        return PlaybackFrameDecision(
            shouldTriggerBargeIn = trigger,
            shouldStreamFrameImmediately = false,
            nearEndRms = nearRms,
            farEndRms = decayedFarEnd,
            doubleTalkRatio = ratio,
            prosodyTone = when {
                trigger -> ProsodyTone.INTERRUPTING
                nearRms > 0.16f -> ProsodyTone.ENERGETIC
                else -> ProsodyTone.CALM
            },
            reason = if (trigger) {
                "Loudspeaker voice barge-in (near=${"%.3f".format(nearRms)}, floor=${"%.3f".format(expectedEchoCeiling)}, ratio=${"%.2f".format(ratio)})"
            } else {
                "Loudspeaker echo-guarded listening (near=${"%.3f".format(nearRms)}, floor=${"%.3f".format(expectedEchoCeiling)})"
            }
        )
    }

    /**
     * Classifies the user's current vocal delivery prosody during normal listening frames.
     */
    fun classifyListeningProsody(rawRms: Float, silenceMs: Long, isUserSpeaking: Boolean): ProsodyTone {
        return when {
            isUserSpeaking && rawRms >= 0.18f -> ProsodyTone.ENERGETIC
            !isUserSpeaking && silenceMs in 200L..850L -> ProsodyTone.HESITANT
            else -> ProsodyTone.CALM
        }
    }

    private fun computeNormalizedRms(buffer: ByteArray, length: Int): Float {
        if (length < 2) return 0f
        var sumSq = 0.0
        val samples = length / 2
        var i = 0
        while (i + 1 < length) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort().toDouble()
            sumSq += sample * sample
            i += 2
        }
        val rms = sqrt(sumSq / samples.coerceAtLeast(1))
        return (rms / 10000.0).toFloat().coerceIn(0f, 1f)
    }

    private fun computeZeroCrossingRate(buffer: ByteArray, length: Int): Float {
        if (length < 4) return 0f
        val samples = length / 2
        var crossings = 0
        var prev = ((buffer[1].toInt() shl 8) or (buffer[0].toInt() and 0xFF)).toShort().toInt()
        var i = 2
        while (i + 1 < length) {
            val curr = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort().toInt()
            if ((prev >= 0 && curr < 0) || (prev < 0 && curr >= 0)) {
                crossings++
            }
            prev = curr
            i += 2
        }
        return crossings.toFloat() / samples.coerceAtLeast(1).toFloat()
    }

    companion object {
        private const val MAX_PREROLL_FRAMES = 5 // 5 x 40ms = 200ms pre-roll
        private const val FAR_END_REVERB_TAIL_MS = 260L
        private const val INITIAL_ECHO_FLOOR_RMS = 0.018f
        private const val ECHO_CALIBRATION_FRAMES = 5 // 5 x 40ms = 200ms auto-calibration
        private const val HEADSET_BARGE_IN_FRAMES = 2 // ~80ms
        private const val FAST_SPEAKER_BARGE_IN_FRAMES = 2 // ~80ms for clear close-range speech
        private const val SPEAKER_BARGE_IN_FRAMES = 3 // ~120ms for normal distance speech
        private const val BARGE_IN_COOLDOWN_MS = 280L

        /**
         * Detects whether audio output is routed to an external headset/Bluetooth device
         * (zero acoustic speaker-to-mic bleed) versus the phone's built-in loudspeaker.
         */
        fun isExternalHeadsetConnected(audioManager: AudioManager?): Boolean {
            if (audioManager == null) return false
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    outputs.any { device ->
                        when (device.type) {
                            AudioDeviceInfo.TYPE_WIRED_HEADSET,
                            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                            AudioDeviceInfo.TYPE_USB_HEADSET,
                            AudioDeviceInfo.TYPE_HEARING_AID -> true
                            else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                (device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                                    device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.isWiredHeadsetOn || audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn
                }
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Builds the Sophon-inspired Full-Duplex Phone-Call & Paralinguistic Conversational Directive
         * for Gemini Native Audio sessions.
         */
        fun buildFullDuplexDirective(isHumanEmotionalPersona: Boolean): String = buildString {
            append("\n=== FULL-DUPLEX PHONE-CALL & PARALINGUISTIC VOICE PROTOCOL ===\n")
            append("- NATURAL PHONE-CALL RHYTHM: Speak in crisp, natural spoken turns (1-3 punchy sentences at a time unless the user asks for a deep explanation) so conversation flows back-and-forth like a real live phone call.\n")
            append("- INSTANT BARGE-IN YIELD: The user can interrupt you mid-sentence at any moment by speaking. If interrupted, NEVER repeat, resume, or apologize for your cut-off sentence—immediately pivot to what the user just said.\n")
            append("- PARALINGUISTIC & EMOTION MIRRORING: Listen to the user's vocal tone, speed, hesitation, laughter, stress, or sighs directly from audio and respond with matching human vocal expressiveness, natural pacing, soft pauses, and warm intonation.\n")
            if (isHumanEmotionalPersona) {
                append("- EXPRESSIVE VOCAL NUANCE: Use authentic human reactions naturally in voice (warm chuckles, gentle sighs, playful pitch shifts, attentive 'hmm' / 'achha') without ever reading bracket tags like '[laugh]' aloud.\n")
            }
            append("- THOUGHTFUL TURN-TAKING: When the user pauses briefly mid-sentence after connectors ('aur...', 'lekin...', 'umm...', 'actually...'), wait patiently for them to finish rather than cutting them off.\n")
            append("===============================================================\n")
        }
    }
}
