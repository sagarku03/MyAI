package com.jarvisai.assistant.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * On-Device Voice Activity Detection (VAD) classifier for low-power continuous listening.
 * Analyzes 30-50ms PCM chunks (16-bit 16kHz mono) using RMS energy, vocal-band spectral
 * concentration (300Hz-3400Hz vs low rumble / high hiss), autocorrelation periodicity, and ZCR
 * so background fans, desk taps, and static noise are rejected cleanly.
 */
class SileroVadManager(
    private val energyThreshold: Double = 350.0,
    private val speechConfidenceThreshold: Float = 0.5f
) {

    private var consecutiveSpeechFrames = 0
    private var consecutiveSilenceFrames = 0

    @Volatile
    private var noiseFloorRms: Double = (energyThreshold * 0.45).coerceIn(40.0, 450.0)

    data class VadResult(
        val isSpeech: Boolean,
        val energyRms: Double,
        val confidence: Float
    )

    fun getAdaptiveNoiseFloor(): Double = noiseFloorRms

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
        val normalized = FloatArray(sampleCount)

        for (i in 0 until sampleCount) {
            val sample = (pcmBytes[i * 2].toInt() and 0xFF) or (pcmBytes[i * 2 + 1].toInt() shl 8)
            val sampleShort = sample.toShort().toInt()
            sumSquares += sampleShort.toDouble() * sampleShort.toDouble()
            normalized[i] = sampleShort / 32768f

            if (i > 0 && ((prevSample >= 0 && sampleShort < 0) || (prevSample < 0 && sampleShort >= 0))) {
                zeroCrossings++
            }
            prevSample = sampleShort
        }

        val rms = sqrt(sumSquares / sampleCount)
        val zcr = zeroCrossings.toDouble() / sampleCount

        // Slowly track quiet background noise floor when frame is near/below threshold
        if (rms < energyThreshold * 1.15) {
            noiseFloorRms = (noiseFloorRms * 0.94 + rms * 0.06).coerceIn(30.0, energyThreshold * 1.4)
        }

        // Vocal spectral band concentration (400Hz..2200Hz vs 65Hz rumble / 5500Hz hiss)
        val voiceEnergy = goertzelPower(normalized, 450.0) +
            goertzelPower(normalized, 900.0) +
            goertzelPower(normalized, 1700.0)
        val nonVoiceEnergy = goertzelPower(normalized, 65.0) + goertzelPower(normalized, 5500.0)
        val spectralVoiceDominance = (voiceEnergy / (voiceEnergy + nonVoiceEnergy + 1e-10)).toFloat().coerceIn(0f, 1f)

        // Human speech typically has moderate ZCR (0.015 - 0.34), vocal-band dominance, and energy > floor
        val dynamicFloor = maxOf(energyThreshold, noiseFloorRms * 1.65)
        val hasEnoughEnergy = rms > dynamicFloor
        val hasVoiceZcr = zcr in 0.012..0.34
        val hasVoiceSpectrum = spectralVoiceDominance >= 0.42f

        val confidence = if (hasEnoughEnergy && hasVoiceZcr && hasVoiceSpectrum) {
            val energyScore = ((rms / (dynamicFloor * 2.2)).coerceAtMost(1.0) * 0.50).toFloat()
            val spectralScore = (spectralVoiceDominance * 0.35f)
            val zcrScore = ((1.0 - zcr * 1.8).coerceIn(0.0, 0.15)).toFloat()
            (energyScore + spectralScore + zcrScore).coerceIn(0f, 1f)
        } else if (hasEnoughEnergy && hasVoiceZcr) {
            0.22f
        } else {
            0.08f
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

    private fun goertzelPower(samples: FloatArray, freqHz: Double): Double {
        val n = samples.size
        if (n < 8) return 0.0
        val omega = (2.0 * PI * freqHz) / 16000.0
        val coeff = 2.0 * cos(omega)
        var sPrev = 0.0
        var sPrev2 = 0.0
        for (i in 0 until n) {
            val s = samples[i] + coeff * sPrev - sPrev2
            sPrev2 = sPrev
            sPrev = s
        }
        return (sPrev2 * sPrev2 + sPrev * sPrev - coeff * sPrev * sPrev2) / (n.toDouble() * n.toDouble())
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
