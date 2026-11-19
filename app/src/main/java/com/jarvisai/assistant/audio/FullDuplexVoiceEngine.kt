package com.jarvisai.assistant.audio

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Real-time Full-Duplex Acoustic & Paralinguistic Voice Engine for JARVIS AI.
 *
 * Brings natural phone-call full-duplex conversational dynamics to Android Live Voice:
 * 1. Continuous Double-Talk & Instant Barge-In Detection:
 *    - Keeps microphone analysis active even while JARVIS is speaking out loud.
 *    - Compares near-end microphone energy & vocal-tract ZCR (via [SileroVadManager]) against
 *      a decaying far-end loudspeaker energy envelope so JARVIS's own playback never causes
 *      self-interruption, while genuine human voice cuts in within ~80ms (headset) or ~120ms (speaker).
 * 2. Zero-Syllable-Loss Pre-Roll Ring Buffer (200ms):
 *    - Maintains a circular buffer of recent 40ms PCM frames so the moment a barge-in triggers,
 *      the user's opening syllables ("Ruko...", "Suno...", "Wait...") are flushed immediately
 *      to the Live WebSocket without clipping.
 * 3. Lightweight Paralinguistic Prosody Telemetry:
 *    - Tracks live speaking intensity and burstiness to classify conversational delivery tone.
 * 4. Full-Duplex & Paralinguistic System Directive:
 *    - Guides native-audio dialogue for natural phone-call turn-taking, graceful mid-sentence
 *      interruption recovery, hesitation patience, and emotional tone mirroring.
 */
class FullDuplexVoiceEngine(
    private val vadManager: SileroVadManager = SileroVadManager(
        energyThreshold = 380.0,
        speechConfidenceThreshold = 0.52f
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

    // Adaptive echo floor learned while AI is speaking cleanly without double-talk
    @Volatile
    private var adaptiveEchoFloorRms: Float = 0.025f

    private var consecutiveDoubleTalkFrames: Int = 0
    private var consecutiveCalmFrames: Int = 0
    private var lastBargeInFiredAtMs: Long = 0L

    // 5 x 40ms = 200ms circular pre-roll ring buffer of raw 16kHz PCM16 frames
    private val preRollBuffer = ArrayDeque<ByteArray>(MAX_PREROLL_FRAMES + 1)

    fun reset() {
        farEndEnvelopeRms = 0f
        lastFarEndFrameTimestampMs = 0L
        adaptiveEchoFloorRms = 0.025f
        consecutiveDoubleTalkFrames = 0
        consecutiveCalmFrames = 0
        vadManager.reset()
        synchronized(preRollBuffer) {
            preRollBuffer.clear()
        }
    }

    /**
     * Feeds every outgoing AI AudioTrack PCM chunk so the engine tracks the exact far-end
     * loudspeaker acoustic energy envelope in real time.
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
        consecutiveDoubleTalkFrames = 0
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
     * Evaluates a 40ms near-end microphone PCM frame while JARVIS is actively playing audio.
     *
     * - When using Headphones / Bluetooth: Hardware acoustic bleed is near-zero, so frames can
     *   be streamed continuously in true full-duplex and barge-in triggers after just 2 frames (~80ms).
     * - When using Phone Loudspeaker (with Android hardware AEC in MODE_IN_COMMUNICATION):
     *   Requires clear vocal-tract speech exceeding the adaptive echo leakage floor + far-end ratio
     *   for 3 consecutive frames (~120ms) before triggering barge-in and releasing pre-roll audio.
     */
    fun evaluateDuringPlayback(
        rawMicPcm: ByteArray,
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

        val nearRms = computeNormalizedRms(rawMicPcm, bytesRead)
        val vadResult = vadManager.processFrame(rawMicPcm, bytesRead)
        val zcr = computeZeroCrossingRate(rawMicPcm, bytesRead)
        val isHumanVoiceBand = zcr in 0.018f..0.29f

        // Always keep the last 200ms ready in the ring buffer for instant pre-roll replay
        pushPreRollFrame(rawMicPcm, bytesRead)

        // Cooldown guard so we don't fire duplicate barge-in events within 350ms
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
            // Headphones / Bluetooth: zero speaker echo into mic -> true unrestricted full-duplex
            val headsetThreshold = maxOf(ambientNoiseRms * 2.1f, 0.022f)
            val isUserTalking = nearRms >= headsetThreshold && isHumanVoiceBand && (vadResult.isSpeech || nearRms >= 0.045f)
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

        // Phone Loudspeaker + Hardware AEC path:
        // Hardware AEC attenuates far-end speaker playback significantly, but loud speaker volume
        // can leave residual acoustic bleed. We adaptively track the echo floor and require
        // a clear double-talk energy margin + vocal tract ZCR across 3 consecutive 40ms frames (~120ms).
        val expectedEchoCeiling = maxOf(
            ambientNoiseRms * 2.8f,
            adaptiveEchoFloorRms * 1.65f,
            decayedFarEnd * 0.24f,
            0.058f
        ).coerceAtMost(0.32f)

        val ratio = if (expectedEchoCeiling > 0.001f) nearRms / expectedEchoCeiling else 0f
        val isStrongHumanDoubleTalk = nearRms >= expectedEchoCeiling &&
            isHumanVoiceBand &&
            (vadResult.isSpeech || vadResult.confidence >= 0.58f || ratio >= 1.35f)

        if (isStrongHumanDoubleTalk) {
            consecutiveDoubleTalkFrames++
            consecutiveCalmFrames = 0
        } else {
            // Smoothly update adaptive echo floor only on non-double-talk playback frames
            if (decayedFarEnd > 0.03f && nearRms < expectedEchoCeiling) {
                adaptiveEchoFloorRms = (adaptiveEchoFloorRms * 0.90f + nearRms * 0.10f)
                    .coerceIn(0.015f, 0.14f)
            }
            consecutiveCalmFrames++
            if (consecutiveCalmFrames >= 2) {
                consecutiveDoubleTalkFrames = 0
            }
        }

        val trigger = consecutiveDoubleTalkFrames >= SPEAKER_BARGE_IN_FRAMES
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
                "Loudspeaker AEC double-talk barge-in (near=${"%.3f".format(nearRms)}, floor=${"%.3f".format(expectedEchoCeiling)}, ratio=${"%.2f".format(ratio)})"
            } else {
                "Loudspeaker echo-guarded listening"
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
        private const val FAR_END_REVERB_TAIL_MS = 280L
        private const val HEADSET_BARGE_IN_FRAMES = 2 // ~80ms
        private const val SPEAKER_BARGE_IN_FRAMES = 3 // ~120ms
        private const val BARGE_IN_COOLDOWN_MS = 350L

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
         * Returns the preferred external communication device (Bluetooth/USB/Wired Headset)
         * or built-in speakerphone on Android 12+ (`API 31+`) for hardware AEC full-duplex calls.
         */
        fun findPreferredCommunicationDevice(audioManager: AudioManager): AudioDeviceInfo? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
            return try {
                val available = audioManager.availableCommunicationDevices
                val priorityTypes = listOf(
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                )
                for (type in priorityTypes) {
                    val match = available.firstOrNull { it.type == type }
                    if (match != null) return match
                }
                available.firstOrNull()
            } catch (_: Exception) {
                null
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
