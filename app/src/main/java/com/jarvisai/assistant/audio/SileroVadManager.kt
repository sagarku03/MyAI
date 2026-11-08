package com.jarvisai.assistant.audio

import kotlin.math.sqrt

/**
 * On-Device Voice Activity Detection (VAD) classifier for low-power continuous listening.
 * Analyzes 30-50ms PCM chunks (16-bit 16kHz mono) to determine whether the incoming audio
 * contains human vocal tract acoustics or ambient background silence/noise.
 */
class SileroVadManager(
    private val energyThreshold: Double = 350.0,
    private val speechConfidenceThreshold: Float = 0.5f
) {

    private var consecutiveSpeechFrames = 0
    private var consecutiveSilenceFrames = 0

    data class VadResult(
        val isSpeech: Boolean,
        val energyRms: Double,
        val confidence: Float
    )

    /**
     * Evaluates a PCM 16-bit 16kHz byte buffer (typically 512 to 1024 samples / 1024 to 2048 bytes).
     */
    fun processFrame(pcmBytes: ByteArray, length: Int = pcmBytes.size): VadResult {
        if (length < 2) {
            return VadResult(isSpeech = false, energyRms = 0.0, confidence = 0.0f)
        }

        var sumSquares = 0.0
        var zeroCrossings = 0
        var prevSample = 0
        val sampleCount = length / 2

        for (i in 0 until sampleCount) {
            val sample = (pcmBytes[i * 2].toInt() and 0xFF) or (pcmBytes[i * 2 + 1].toInt() shl 8)
            val sampleShort = sample.toShort().toInt()
            sumSquares += sampleShort * sampleShort

            if (i > 0 && ((prevSample >= 0 && sampleShort < 0) || (prevSample < 0 && sampleShort >= 0))) {
                zeroCrossings++
            }
            prevSample = sampleShort
        }

        val rms = sqrt(sumSquares / sampleCount)
        val zcr = zeroCrossings.toDouble() / sampleCount

        // Human speech typically has moderate zero-crossing rates (0.02 - 0.25) and sufficient RMS energy
        val isAcousticSpeech = rms > energyThreshold && zcr in 0.02..0.32
        val confidence = if (isAcousticSpeech) {
            ((rms / (energyThreshold * 2.5)).coerceAtMost(1.0) * 0.7 + (1.0 - zcr * 2.0).coerceIn(0.0, 0.3)).toFloat()
        } else {
            0.1f
        }

        if (confidence >= speechConfidenceThreshold) {
            consecutiveSpeechFrames++
            consecutiveSilenceFrames = 0
        } else {
            consecutiveSilenceFrames++
            if (consecutiveSilenceFrames > 3) {
                consecutiveSpeechFrames = 0
            }
        }

        return VadResult(
            isSpeech = consecutiveSpeechFrames >= 2,
            energyRms = rms,
            confidence = confidence
        )
    }

    /**
     * Combines acoustic VAD silence tracking with [com.jarvisai.assistant.service.LinguisticTurnTakingEngine]
     * so interim speech ending with hesitations ("aur...", "lekin...", "and...") receives a 900ms grace period,
     * while completed commands ("karo", "hai", "batao", "?") commit in 200ms.
     */
    fun evaluateTurnWithVad(
        interimTranscript: String,
        silenceDurationMs: Long
    ): com.jarvisai.assistant.service.LinguisticTurnTakingEngine.TurnDecision {
        return com.jarvisai.assistant.service.LinguisticTurnTakingEngine.evaluateTurn(
            transcript = interimTranscript,
            silenceDurationMs = silenceDurationMs
        )
    }

    fun reset() {
        consecutiveSpeechFrames = 0
        consecutiveSilenceFrames = 0
    }
}
