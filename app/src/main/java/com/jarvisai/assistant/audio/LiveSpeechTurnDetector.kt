package com.jarvisai.assistant.audio

import com.jarvisai.assistant.service.LinguisticTurnTakingEngine
import java.util.ArrayDeque
import kotlin.math.abs

/**
 * Adaptive Client-Side Acoustic + Spectral + Linguistic Speech Turn Detector for Live Voice.
 *
 * Designed specifically to solve:
 * 1. "Stuck User Speaking..." lockups in rooms with fans, AC, or distant TV/family chatter
 *    (continuous quantile/floor tracking + stationary-hum anti-stick recovery up to 0.085f).
 * 2. 8-12s Live API response stalls caused by streaming un-gated background room noise
 *    (comfort-attenuates idle noise by 0.05x while preserving a 160ms onset pre-roll and
 *    240ms post-speech hangover tail, and emits Hybrid VAD `audioStreamEnd` signals on real turn end).
 * 3. Foreign-language / Vietnamese ASR hallucinations ("Chào mí ngụi.") triggered when
 *    continuous low-SNR static/hum feeds `inputAudioTranscription`.
 */
class LiveSpeechTurnDetector(
    private val vadManager: SileroVadManager = SileroVadManager(
        energyThreshold = 190.0,
        speechConfidenceThreshold = 0.36f
    )
) {

    data class FrameDecision(
        val isUserSpeaking: Boolean,
        val justStartedSpeaking: Boolean,
        val shouldEmitAudioStreamEnd: Boolean,
        val shouldTransitionToProcessing: Boolean,
        val noiseFalseAlarmReleased: Boolean,
        val sendUnattenuatedSpeech: Boolean,
        val isCandidateOnsetFrame: Boolean,
        val preRollFramesToFlush: List<ByteArray>,
        val ambientNoiseRms: Float,
        val vadConfidence: Float,
        val turnEndReason: String = ""
    )

    private var ambientNoiseRms: Float = INITIAL_AMBIENT_RMS
    private var calibrationFrames: Int = 0

    private var isUserSpeaking: Boolean = false
    private var consecutiveOnsetFrames: Int = 0
    private var speakingDurationMs: Long = 0L
    private var confirmedSpeechFrames: Int = 0
    private var silenceDurationMs: Long = 0L
    private var hangoverFramesRemaining: Int = 0

    // Tracks contrast/modulation during active speech so flat stationary TV/fan hum cannot lock the turn
    private var speakingFloorRms: Float = INITIAL_AMBIENT_RMS
    private var peakTurnRms: Float = 0f
    private var prevFrameRms: Float = 0f
    private var lowModulationFrames: Int = 0
    private var lastServerTranscriptTimestampMs: Long = 0L

    private val preRollQueue = ArrayDeque<ByteArray>(MAX_PREROLL_FRAMES + 1)

    fun reset(keepCalibratedNoiseFloor: Boolean = false) {
        if (!keepCalibratedNoiseFloor) {
            ambientNoiseRms = INITIAL_AMBIENT_RMS
            calibrationFrames = 0
        }
        isUserSpeaking = false
        consecutiveOnsetFrames = 0
        speakingDurationMs = 0L
        confirmedSpeechFrames = 0
        silenceDurationMs = 0L
        hangoverFramesRemaining = 0
        speakingFloorRms = ambientNoiseRms
        peakTurnRms = 0f
        prevFrameRms = 0f
        lowModulationFrames = 0
        lastServerTranscriptTimestampMs = 0L
        vadManager.reset()
        preRollQueue.clear()
    }

    /**
     * Clears only the active turn state (e.g., during half-duplex speaker guard) while keeping
     * the calibrated room noise floor so we don't re-trigger immediately after AI stops speaking.
     */
    fun resetTurnKeepNoiseFloor() {
        reset(keepCalibratedNoiseFloor = true)
    }

    /**
     * Marks speech as already started by Full-Duplex barge-in so when the user finishes their
     * interruption sentence, Hybrid VAD still finalizes cleanly with `audioStreamEnd`.
     */
    fun notifyExternalBargeInStarted(nowMs: Long = System.currentTimeMillis(), initialFrames: Int = 4) {
        isUserSpeaking = true
        consecutiveOnsetFrames = initialFrames
        confirmedSpeechFrames = maxOf(confirmedSpeechFrames, initialFrames)
        speakingDurationMs = initialFrames * FRAME_DURATION_MS
        silenceDurationMs = 0L
        hangoverFramesRemaining = HANGOVER_FRAMES
        speakingFloorRms = ambientNoiseRms
        peakTurnRms = maxOf(peakTurnRms, ambientNoiseRms * 2.5f)
        prevFrameRms = ambientNoiseRms
        lowModulationFrames = 0
        lastServerTranscriptTimestampMs = 0L
        preRollQueue.clear()
    }

    fun notifyServerTranscriptReceived(nowMs: Long = System.currentTimeMillis()) {
        lastServerTranscriptTimestampMs = nowMs
        lowModulationFrames = 0
    }

    fun currentAmbientNoiseRms(): Float = ambientNoiseRms

    fun processFrame(
        gainedPcm: ByteArray,
        bytesRead: Int,
        rawRms: Float,
        interimTranscript: String,
        nowMs: Long = System.currentTimeMillis()
    ): FrameDecision {
        val vadResult = vadManager.processFrame(gainedPcm, bytesRead)

        // Rapid initial calibration on the first ~320ms so medium/noisy rooms never get stuck
        // below a frozen 0.008f threshold from Frame #1.
        if (!isUserSpeaking) {
            if (calibrationFrames < CALIBRATION_FRAMES) {
                calibrationFrames++
                val alpha = if (calibrationFrames <= 2) 0.45f else 0.20f
                if (vadResult.confidence < 0.58f && rawRms < MAX_NOISE_FLOOR_RMS) {
                    ambientNoiseRms = (ambientNoiseRms * (1f - alpha) + rawRms * alpha)
                        .coerceIn(MIN_NOISE_FLOOR_RMS, MAX_NOISE_FLOOR_RMS)
                }
            } else if (rawRms < ambientNoiseRms * 1.55f || (vadResult.confidence < 0.36f && rawRms < MAX_NOISE_FLOOR_RMS)) {
                ambientNoiseRms = (ambientNoiseRms * 0.91f + rawRms * 0.09f)
                    .coerceIn(MIN_NOISE_FLOOR_RMS, MAX_NOISE_FLOOR_RMS)
            }
        }

        val speechStartThreshold = (ambientNoiseRms * 1.75f).coerceIn(MIN_START_THRESHOLD, MAX_START_THRESHOLD)
        val speechContinueThreshold = (ambientNoiseRms * 1.35f).coerceIn(MIN_CONTINUE_THRESHOLD, MAX_CONTINUE_THRESHOLD)
        val strongVoiceOverride = rawRms >= (ambientNoiseRms * 2.65f).coerceAtLeast(STRONG_VOICE_RMS)

        if (!isUserSpeaking) {
            if (calibrationFrames <= INITIAL_CALIBRATION_HOLDOFF_FRAMES) {
                pushPreRoll(gainedPcm, bytesRead)
                return FrameDecision(
                    isUserSpeaking = false,
                    justStartedSpeaking = false,
                    shouldEmitAudioStreamEnd = false,
                    shouldTransitionToProcessing = false,
                    noiseFalseAlarmReleased = false,
                    sendUnattenuatedSpeech = false,
                    isCandidateOnsetFrame = false,
                    preRollFramesToFlush = emptyList(),
                    ambientNoiseRms = ambientNoiseRms,
                    vadConfidence = vadResult.confidence
                )
            }
            val isVocalOnset = rawRms >= speechStartThreshold &&
                (strongVoiceOverride || vadResult.isSpeech || vadResult.confidence >= 0.34f)

            if (isVocalOnset) {
                consecutiveOnsetFrames++
                val immediateCommit = strongVoiceOverride && rawRms >= INSTANT_ONSET_RMS
                if (consecutiveOnsetFrames >= ONSET_CONFIRM_FRAMES || immediateCommit) {
                    val flushed = drainPreRollTail(maxFrames = 3)
                    isUserSpeaking = true
                    confirmedSpeechFrames = consecutiveOnsetFrames
                    speakingDurationMs = consecutiveOnsetFrames * FRAME_DURATION_MS
                    silenceDurationMs = 0L
                    hangoverFramesRemaining = HANGOVER_FRAMES
                    speakingFloorRms = ambientNoiseRms
                    peakTurnRms = rawRms
                    prevFrameRms = rawRms
                    lowModulationFrames = 0
                    lastServerTranscriptTimestampMs = 0L

                    return FrameDecision(
                        isUserSpeaking = true,
                        justStartedSpeaking = true,
                        shouldEmitAudioStreamEnd = false,
                        shouldTransitionToProcessing = false,
                        noiseFalseAlarmReleased = false,
                        sendUnattenuatedSpeech = true,
                        isCandidateOnsetFrame = false,
                        preRollFramesToFlush = flushed,
                        ambientNoiseRms = ambientNoiseRms,
                        vadConfidence = vadResult.confidence
                    )
                }

                // Hold candidate frame #1 in preRollQueue (sending idle comfort audio for this 40ms
                // confirm step) so if frame #2 confirms speech, pre-roll flushes in strict monotonic order.
                pushPreRoll(gainedPcm, bytesRead)
                return FrameDecision(
                    isUserSpeaking = false,
                    justStartedSpeaking = false,
                    shouldEmitAudioStreamEnd = false,
                    shouldTransitionToProcessing = false,
                    noiseFalseAlarmReleased = false,
                    sendUnattenuatedSpeech = false,
                    isCandidateOnsetFrame = true,
                    preRollFramesToFlush = emptyList(),
                    ambientNoiseRms = ambientNoiseRms,
                    vadConfidence = vadResult.confidence
                )
            } else {
                consecutiveOnsetFrames = 0
                pushPreRoll(gainedPcm, bytesRead)
                val keepHangover = hangoverFramesRemaining > 0
                if (keepHangover) {
                    hangoverFramesRemaining--
                }
                return FrameDecision(
                    isUserSpeaking = false,
                    justStartedSpeaking = false,
                    shouldEmitAudioStreamEnd = false,
                    shouldTransitionToProcessing = false,
                    noiseFalseAlarmReleased = false,
                    sendUnattenuatedSpeech = keepHangover,
                    isCandidateOnsetFrame = false,
                    preRollFramesToFlush = emptyList(),
                    ambientNoiseRms = ambientNoiseRms,
                    vadConfidence = vadResult.confidence
                )
            }
        }

        // Currently in active USER_SPEAKING state
        speakingDurationMs += FRAME_DURATION_MS
        peakTurnRms = maxOf(peakTurnRms, rawRms)
        speakingFloorRms = (speakingFloorRms * 0.90f + minOf(rawRms, speakingFloorRms * 1.15f + 0.004f) * 0.10f)
            .coerceIn(MIN_NOISE_FLOOR_RMS, MAX_NOISE_FLOOR_RMS)

        val rmsDelta = abs(rawRms - prevFrameRms)
        prevFrameRms = rawRms

        val hasTranscriptRecently = interimTranscript.isNotBlank() ||
            (lastServerTranscriptTimestampMs > 0L && (nowMs - lastServerTranscriptTimestampMs) < STALL_NO_TRANSCRIPT_MS)

        // Detect steady stationary room/TV hum pretending to be active speech: flat delta + low vocal confidence + no ASR words
        val looksLikeFlatNoise = rawRms < maxOf(ambientNoiseRms * 2.65f, peakTurnRms * 0.50f, MAX_NOISE_FLOOR_RMS) &&
            rmsDelta < maxOf(ambientNoiseRms * 0.45f, 0.010f) &&
            vadResult.confidence < 0.45f
        if (looksLikeFlatNoise && !hasTranscriptRecently) {
            lowModulationFrames++
        } else if (rmsDelta >= maxOf(ambientNoiseRms * 0.55f, 0.015f) || vadResult.confidence >= 0.50f) {
            lowModulationFrames = (lowModulationFrames - 2).coerceAtLeast(0)
        }

        if (lowModulationFrames >= STALL_RECOVERY_FRAMES && speakingDurationMs >= STALL_RECOVERY_MIN_MS) {
            ambientNoiseRms = maxOf(ambientNoiseRms, rawRms * 0.96f, speakingFloorRms * 1.04f)
                .coerceIn(MIN_NOISE_FLOOR_RMS, MAX_NOISE_FLOOR_RMS)
            val hadEnoughSpeechForTurn = confirmedSpeechFrames >= MIN_GENUINE_UTTERANCE_FRAMES &&
                interimTranscript.isNotBlank()
            reset(keepCalibratedNoiseFloor = true)
            return FrameDecision(
                isUserSpeaking = false,
                justStartedSpeaking = false,
                shouldEmitAudioStreamEnd = hadEnoughSpeechForTurn,
                shouldTransitionToProcessing = hadEnoughSpeechForTurn,
                noiseFalseAlarmReleased = !hadEnoughSpeechForTurn,
                sendUnattenuatedSpeech = false,
                isCandidateOnsetFrame = false,
                preRollFramesToFlush = emptyList(),
                ambientNoiseRms = ambientNoiseRms,
                vadConfidence = vadResult.confidence,
                turnEndReason = "Adaptive noise-floor anti-stick release"
            )
        }

        val stillVocalizing = rawRms >= speechContinueThreshold &&
            (rawRms >= ambientNoiseRms * 1.65f || vadResult.confidence >= 0.34f || vadResult.isSpeech)

        if (stillVocalizing) {
            confirmedSpeechFrames++
            silenceDurationMs = 0L
            hangoverFramesRemaining = HANGOVER_FRAMES
            return FrameDecision(
                isUserSpeaking = true,
                justStartedSpeaking = false,
                shouldEmitAudioStreamEnd = false,
                shouldTransitionToProcessing = false,
                noiseFalseAlarmReleased = false,
                sendUnattenuatedSpeech = true,
                isCandidateOnsetFrame = false,
                preRollFramesToFlush = emptyList(),
                ambientNoiseRms = ambientNoiseRms,
                vadConfidence = vadResult.confidence
            )
        }

        // In trailing silence pause
        silenceDurationMs += FRAME_DURATION_MS
        val inHangover = hangoverFramesRemaining > 0
        if (inHangover) {
            hangoverFramesRemaining--
        }

        val cleanTranscript = interimTranscript.trim()
        val turnDecision = if (cleanTranscript.isNotEmpty()) {
            LinguisticTurnTakingEngine.evaluateTurn(
                transcript = cleanTranscript,
                silenceDurationMs = silenceDurationMs
            )
        } else {
            LinguisticTurnTakingEngine.TurnDecision(
                isThoughtComplete = silenceDurationMs >= DEFAULT_END_OF_SPEECH_SILENCE_MS,
                recommendedSilenceWaitMs = DEFAULT_END_OF_SPEECH_SILENCE_MS,
                reason = "Acoustic VAD silence threshold (${DEFAULT_END_OF_SPEECH_SILENCE_MS}ms)"
            )
        }

        if (turnDecision.isThoughtComplete) {
            val isGenuineUtterance = confirmedSpeechFrames >= MIN_GENUINE_UTTERANCE_FRAMES || cleanTranscript.isNotEmpty()
            reset(keepCalibratedNoiseFloor = true)
            return FrameDecision(
                isUserSpeaking = false,
                justStartedSpeaking = false,
                shouldEmitAudioStreamEnd = isGenuineUtterance,
                shouldTransitionToProcessing = isGenuineUtterance,
                noiseFalseAlarmReleased = !isGenuineUtterance,
                sendUnattenuatedSpeech = false,
                isCandidateOnsetFrame = false,
                preRollFramesToFlush = emptyList(),
                ambientNoiseRms = ambientNoiseRms,
                vadConfidence = vadResult.confidence,
                turnEndReason = turnDecision.reason
            )
        }

        return FrameDecision(
            isUserSpeaking = true,
            justStartedSpeaking = false,
            shouldEmitAudioStreamEnd = false,
            shouldTransitionToProcessing = false,
            noiseFalseAlarmReleased = false,
            sendUnattenuatedSpeech = inHangover,
            isCandidateOnsetFrame = false,
            preRollFramesToFlush = emptyList(),
            ambientNoiseRms = ambientNoiseRms,
            vadConfidence = vadResult.confidence
        )
    }

    private fun pushPreRoll(pcm: ByteArray, length: Int) {
        if (length <= 0) return
        val copy = pcm.copyOf(length)
        if (preRollQueue.size >= MAX_PREROLL_FRAMES) {
            preRollQueue.removeFirst()
        }
        preRollQueue.addLast(copy)
    }

    private fun drainPreRollTail(maxFrames: Int): List<ByteArray> {
        if (preRollQueue.isEmpty()) return emptyList()
        val all = ArrayList<ByteArray>(preRollQueue)
        preRollQueue.clear()
        return if (all.size <= maxFrames) all else all.subList(all.size - maxFrames, all.size)
    }

    companion object {
        const val FRAME_DURATION_MS = 40L
        const val DEFAULT_END_OF_SPEECH_SILENCE_MS = 500L
        const val IDLE_NOISE_ATTENUATION_FACTOR = 0.05f

        private const val CALIBRATION_FRAMES = 8
        private const val INITIAL_CALIBRATION_HOLDOFF_FRAMES = 3 // ~120ms mic settle calibration before onset trigger
        private const val ONSET_CONFIRM_FRAMES = 2
        private const val HANGOVER_FRAMES = 6 // 240ms trailing consonant protection
        private const val MIN_GENUINE_UTTERANCE_FRAMES = 4 // >= 160ms voice before emitting audioStreamEnd
        private const val MAX_PREROLL_FRAMES = 4 // 160ms look-back
        private const val STALL_RECOVERY_FRAMES = 38 // ~1.52s of flat low-SNR noise while "speaking"
        private const val STALL_RECOVERY_MIN_MS = 1800L
        private const val STALL_NO_TRANSCRIPT_MS = 1800L

        private const val INITIAL_AMBIENT_RMS = 0.009f
        private const val MIN_NOISE_FLOOR_RMS = 0.004f
        private const val MAX_NOISE_FLOOR_RMS = 0.085f
        private const val MIN_START_THRESHOLD = 0.013f
        private const val MAX_START_THRESHOLD = 0.145f
        private const val MIN_CONTINUE_THRESHOLD = 0.010f
        private const val MAX_CONTINUE_THRESHOLD = 0.115f
        private const val STRONG_VOICE_RMS = 0.036f
        private const val INSTANT_ONSET_RMS = 0.065f

        /**
         * Scales 16-bit mono PCM audio cleanly into [dest] (or returns a new buffer if needed).
         * Used both for speech soft-boost and idle room noise comfort-attenuation (0.05x).
         */
        fun scalePcmInto(
            source: ByteArray,
            dest: ByteArray,
            bytesRead: Int,
            gainFactor: Float
        ): ByteArray {
            val out = if (dest.size >= bytesRead) dest else ByteArray(bytesRead)
            if (gainFactor == 1.0f) {
                System.arraycopy(source, 0, out, 0, bytesRead)
                return out
            }
            var i = 0
            while (i + 1 < bytesRead) {
                val sample = ((source[i + 1].toInt() shl 8) or (source[i].toInt() and 0xFF)).toShort()
                val scaled = (sample * gainFactor).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
                out[i] = (scaled.toInt() and 0xFF).toByte()
                out[i + 1] = ((scaled.toInt() shr 8) and 0xFF).toByte()
                i += 2
            }
            return out
        }
    }
}
