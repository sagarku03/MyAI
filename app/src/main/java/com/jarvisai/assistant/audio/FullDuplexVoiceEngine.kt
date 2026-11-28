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

    enum class BargeInStage {
        NONE,
        PAUSED,
        COMMITTED
    }

    data class PlaybackFrameDecision(
        val shouldTriggerBargeIn: Boolean,
        val shouldStreamFrameImmediately: Boolean,
        val nearEndRms: Float,
        val farEndRms: Float,
        val doubleTalkRatio: Float,
        val prosodyTone: ProsodyTone,
        val reason: String,
        val bargeInStage: BargeInStage = BargeInStage.NONE,
        val shouldPausePlayback: Boolean = false,
        val shouldResumeDuckedPlayback: Boolean = false,
        val erleDb: Float = 0f,
        val residualRms: Float = nearEndRms,
        val cleanedPcmFrame: ByteArray? = null
    )

    val adaptiveEchoCanceller = AdaptiveEchoCanceller()

    // Decaying envelope of far-end AI speaker output (0f..1f)
    @Volatile
    private var farEndEnvelopeRms: Float = 0f

    @Volatile
    private var lastFarEndFrameTimestampMs: Long = 0L

    // Real-time measured mic echo residual floor while AI is speaking cleanly on the current phone & volume
    @Volatile
    private var adaptiveEchoFloorRms: Float = INITIAL_ECHO_FLOOR_RMS

    // Opening frames of a fresh playback turn used to calibrate real speaker-to-mic bleed
    @Volatile
    private var playbackCalibrationFrames: Int = 0

    @Volatile
    private var bargeInStage: BargeInStage = BargeInStage.NONE

    private var consecutiveDoubleTalkFrames: Int = 0
    private var consecutiveCalmFrames: Int = 0
    private var pausedCalmFrames: Int = 0
    private var lastBargeInFiredAtMs: Long = 0L

    // 8 x 40ms = 320ms circular pre-roll ring buffer of echo-cleaned 16kHz PCM16 frames
    private val preRollBuffer = ArrayDeque<ByteArray>(MAX_PREROLL_FRAMES + 1)

    fun reset() {
        farEndEnvelopeRms = 0f
        lastFarEndFrameTimestampMs = 0L
        adaptiveEchoFloorRms = INITIAL_ECHO_FLOOR_RMS
        playbackCalibrationFrames = 0
        bargeInStage = BargeInStage.NONE
        consecutiveDoubleTalkFrames = 0
        consecutiveCalmFrames = 0
        pausedCalmFrames = 0
        adaptiveEchoCanceller.reset()
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
        bargeInStage = BargeInStage.NONE
        consecutiveDoubleTalkFrames = 0
        consecutiveCalmFrames = 0
        pausedCalmFrames = 0
        adaptiveEchoFloorRms = INITIAL_ECHO_FLOOR_RMS
        adaptiveEchoCanceller.onPlaybackTurnStarted()
    }

    /**
     * Feeds every outgoing AI AudioTrack PCM chunk so the engine tracks far-end playback activity
     * and keeps the software NLMS adaptive echo canceller synchronized.
     */
    fun reportFarEndPlaybackFrame(
        pcmBytes: ByteArray,
        length: Int = pcmBytes.size,
        sampleRate: Int = 24000,
        playbackFramePosition: Long = -1L
    ) {
        if (length < 2) return
        val rms = computeNormalizedRms(pcmBytes, length)
        val now = System.currentTimeMillis()
        farEndEnvelopeRms = maxOf(rms, farEndEnvelopeRms * 0.72f).coerceIn(0f, 1f)
        lastFarEndFrameTimestampMs = now
        adaptiveEchoCanceller.pushFarEndPcm(
            pcmBytes = pcmBytes,
            length = length,
            sampleRate = sampleRate,
            playbackPositionFrames = playbackFramePosition
        )
    }

    fun markFarEndSilent() {
        farEndEnvelopeRms = 0f
        playbackCalibrationFrames = 0
        bargeInStage = BargeInStage.NONE
        consecutiveDoubleTalkFrames = 0
        consecutiveCalmFrames = 0
        pausedCalmFrames = 0
        adaptiveEchoCanceller.markFarEndSilent()
    }

    /**
     * Pushes a copy of the latest 40ms mic frame into the 320ms circular pre-roll buffer.
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
     * Drains the buffered ~320ms pre-roll frames in chronological order when barge-in fires
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
     * Runs NLMS software echo subtraction + ERLE verification + pitch/spectral double-talk check
     * and executes a 2-stage barge-in machine (`NONE -> PAUSED -> COMMITTED`) so speaker self-echo
     * never cuts off AI speech while genuine human interruptions commit cleanly with 320ms pre-roll.
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

        val aecRes = adaptiveEchoCanceller.processCaptureFrame(
            nearEndPcm = gainedMicPcm,
            length = bytesRead,
            applyEchoSubtraction = !isExternalHeadset,
            nowMs = now
        )

        val cleanedFrame = if (isExternalHeadset) gainedMicPcm.copyOf(bytesRead) else aecRes.cleanedPcm
        val nearRms = aecRes.nearRms
        val residualRms = if (isExternalHeadset) nearRms else aecRes.residualRms
        val vadResult = vadManager.processFrame(cleanedFrame, cleanedFrame.size)
        val zcr = computeZeroCrossingRate(cleanedFrame, cleanedFrame.size)
        val isHumanVoiceBand = (zcr in 0.010f..0.35f) &&
            (aecRes.voiceBandRatio >= 0.40f || aecRes.harmonicity >= 0.30f)

        // Always keep the last ~320ms of echo-cleaned audio ready in pre-roll
        pushPreRollFrame(cleanedFrame, cleanedFrame.size)

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
                reason = "Barge-in cooldown active",
                bargeInStage = BargeInStage.NONE,
                erleDb = aecRes.erleDb,
                residualRms = residualRms,
                cleanedPcmFrame = cleanedFrame
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
                bargeInStage = BargeInStage.COMMITTED
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
                },
                bargeInStage = if (trigger) BargeInStage.COMMITTED else BargeInStage.NONE,
                shouldPausePlayback = trigger,
                erleDb = aecRes.erleDb,
                residualRms = residualRms,
                cleanedPcmFrame = cleanedFrame
            )
        }

        // Phone Loudspeaker Path with NLMS Echo Subtraction + ERLE & Pitch Double-Talk Gate:
        if (playbackCalibrationFrames < ECHO_CALIBRATION_FRAMES) {
            playbackCalibrationFrames++
            if (decayedFarEnd > 0.015f) {
                adaptiveEchoFloorRms = if (playbackCalibrationFrames == 1) {
                    residualRms.coerceIn(0.010f, 0.22f)
                } else {
                    maxOf(adaptiveEchoFloorRms * 0.65f + residualRms * 0.35f, residualRms * 0.88f)
                        .coerceIn(0.010f, 0.22f)
                }
            }
            val urgentOverride = residualRms >= maxOf(adaptiveEchoFloorRms * 2.15f, 0.13f) &&
                isHumanVoiceBand &&
                (aecRes.harmonicity >= 0.32f || aecRes.voiceBandRatio >= 0.52f)
            if (!urgentOverride) {
                return PlaybackFrameDecision(
                    shouldTriggerBargeIn = false,
                    shouldStreamFrameImmediately = false,
                    nearEndRms = nearRms,
                    farEndRms = decayedFarEnd,
                    doubleTalkRatio = 0f,
                    prosodyTone = ProsodyTone.CALM,
                    reason = "Calibrating loudspeaker echo floor (${playbackCalibrationFrames}/$ECHO_CALIBRATION_FRAMES, erle=${"%.1f".format(aecRes.erleDb)}dB)",
                    bargeInStage = BargeInStage.NONE,
                    erleDb = aecRes.erleDb,
                    residualRms = residualRms,
                    cleanedPcmFrame = cleanedFrame
                )
            }
        }

        // Dynamic ceiling anchored on AEC residual rather than raw speaker bleed
        val expectedResidualCeiling = maxOf(
            ambientNoiseRms * 2.15f,
            adaptiveEchoFloorRms * 1.35f,
            0.025f
        ).coerceAtMost(0.26f)

        val ratio = if (expectedResidualCeiling > 0.001f) residualRms / expectedResidualCeiling else 0f
        // If NLMS achieved strong echo cancellation (>=18dB ERLE) leaving only tiny residual, guard against self-triggering
        val strongCleanCancellation = aecRes.erleDb >= 18f && residualRms < expectedResidualCeiling * 1.15f
        val hasVocalPitchEvidence = aecRes.pitchHz in 75f..420f && aecRes.harmonicity >= 0.34f
        val isStrongCloseVoice = !strongCleanCancellation &&
            residualRms >= maxOf(adaptiveEchoFloorRms * 1.70f, 0.055f) &&
            isHumanVoiceBand &&
            (hasVocalPitchEvidence || aecRes.voiceBandRatio >= 0.52f)
        val isHumanDoubleTalk = !strongCleanCancellation &&
            residualRms >= expectedResidualCeiling &&
            isHumanVoiceBand &&
            (vadResult.isSpeech || vadResult.confidence >= 0.42f || ratio >= 1.25f || hasVocalPitchEvidence)

        var shouldPauseNow = false
        var shouldResumeNow = false
        var commitTrigger = false

        if (isHumanDoubleTalk || isStrongCloseVoice) {
            consecutiveDoubleTalkFrames++
            consecutiveCalmFrames = 0
            pausedCalmFrames = 0

            if (bargeInStage == BargeInStage.NONE && consecutiveDoubleTalkFrames >= 1) {
                // Stage 1: Instant local duck/pause while verifying human double-talk continues
                bargeInStage = BargeInStage.PAUSED
                shouldPauseNow = true
            }
            val requiredCommitFrames = if (isStrongCloseVoice) FAST_SPEAKER_BARGE_IN_FRAMES else SPEAKER_BARGE_IN_FRAMES
            if (consecutiveDoubleTalkFrames >= requiredCommitFrames) {
                bargeInStage = BargeInStage.COMMITTED
                commitTrigger = true
                consecutiveDoubleTalkFrames = 0
                lastBargeInFiredAtMs = now
            }
        } else {
            // Clean echo/ambient frame
            if (decayedFarEnd > 0.015f && residualRms < expectedResidualCeiling) {
                adaptiveEchoFloorRms = (adaptiveEchoFloorRms * 0.88f + residualRms * 0.12f)
                    .coerceIn(0.010f, 0.20f)
            }
            consecutiveCalmFrames++
            if (bargeInStage == BargeInStage.PAUSED) {
                pausedCalmFrames++
                if (pausedCalmFrames >= 3) {
                    // False alarm / transient thud — revert Stage-1 pause and resume AI output cleanly
                    bargeInStage = BargeInStage.NONE
                    shouldResumeNow = true
                    consecutiveDoubleTalkFrames = 0
                    pausedCalmFrames = 0
                }
            } else if (consecutiveCalmFrames >= 2) {
                consecutiveDoubleTalkFrames = 0
            }
        }

        return PlaybackFrameDecision(
            shouldTriggerBargeIn = commitTrigger,
            shouldStreamFrameImmediately = false,
            nearEndRms = nearRms,
            farEndRms = decayedFarEnd,
            doubleTalkRatio = ratio,
            prosodyTone = when {
                commitTrigger -> ProsodyTone.INTERRUPTING
                residualRms > 0.16f -> ProsodyTone.ENERGETIC
                else -> ProsodyTone.CALM
            },
            reason = when {
                commitTrigger -> "Stage-2 Committed loudspeaker barge-in (res=${"%.3f".format(residualRms)}, floor=${"%.3f".format(expectedResidualCeiling)}, erle=${"%.1f".format(aecRes.erleDb)}dB, pitch=${aecRes.pitchHz.toInt()}Hz)"
                shouldPauseNow -> "Stage-1 Paused playback verifying human double-talk (res=${"%.3f".format(residualRms)}, erle=${"%.1f".format(aecRes.erleDb)}dB)"
                shouldResumeNow -> "Reverted Stage-1 barge-in pause (transient noise/echo subsided)"
                else -> "Loudspeaker AEC-guarded (res=${"%.3f".format(residualRms)}, floor=${"%.3f".format(expectedResidualCeiling)}, erle=${"%.1f".format(aecRes.erleDb)}dB)"
            },
            bargeInStage = bargeInStage,
            shouldPausePlayback = shouldPauseNow,
            shouldResumeDuckedPlayback = shouldResumeNow,
            erleDb = aecRes.erleDb,
            residualRms = residualRms,
            cleanedPcmFrame = cleanedFrame
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
        private const val MAX_PREROLL_FRAMES = 8 // 8 x 40ms = 320ms cleaned pre-roll
        private const val FAR_END_REVERB_TAIL_MS = 260L
        private const val INITIAL_ECHO_FLOOR_RMS = 0.018f
        private const val ECHO_CALIBRATION_FRAMES = 5 // 5 x 40ms = 200ms auto-calibration
        private const val HEADSET_BARGE_IN_FRAMES = 2 // ~80ms
        private const val FAST_SPEAKER_BARGE_IN_FRAMES = 2 // ~80ms for clear close-range speech
        private const val SPEAKER_BARGE_IN_FRAMES = 3 // ~120ms for sustained double-talk commit
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
