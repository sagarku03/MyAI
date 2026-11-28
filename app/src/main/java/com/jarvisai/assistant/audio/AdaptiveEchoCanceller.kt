package com.jarvisai.assistant.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure-Kotlin Normalized Least Mean Squares (NLMS) Adaptive Acoustic Echo Canceller,
 * ERLE (Echo Return Loss Enhancement) estimator, harmonic pitch detector, and voice-band
 * spectral energy analyzer for 16kHz 16-bit mono PCM capture.
 *
 * Inspired by production duplex voice pipelines:
 * - Buffers far-end playback (typically 24kHz Gemini Native Audio) resampled to 16kHz
 *   with monotonic sample indexing for Render-Capture delay alignment.
 * - Adapts an FIR echo impulse response model on echo-only segments while freezing adaptation
 *   during double-talk so user speech never corrupts the filter.
 * - Reports `erleDb`, `residualRms`, `pitchHz`, `harmonicity`, and `voiceBandRatio` so
 *   [FullDuplexVoiceEngine] can run a reliable 2-stage barge-in commit without loudspeaker self-echo.
 */
class AdaptiveEchoCanceller(
    private val filterTaps: Int = DEFAULT_FILTER_TAPS,
    private val ringCapacitySamples: Int = RENDER_RING_CAPACITY
) {

    data class AecProcessResult(
        val cleanedPcm: ByteArray,
        val nearRms: Float,
        val farAlignedRms: Float,
        val residualRms: Float,
        val erleDb: Float,
        val pitchHz: Float,
        val harmonicity: Float,
        val voiceBandRatio: Float,
        val estimatedDelaySamples: Int,
        val farEndActive: Boolean
    )

    private val renderRing = FloatArray(ringCapacitySamples)
    private val filterWeights = FloatArray(filterTaps)

    @Volatile
    private var renderWriteCursor: Int = 0

    @Volatile
    private var totalRenderSamplesWritten: Long = 0L

    @Volatile
    private var lastFarEndTimestampMs: Long = 0L

    @Volatile
    private var farEndEnvelope: Float = 0f

    @Volatile
    private var currentDelaySamples: Int = DEFAULT_ACOUSTIC_DELAY_SAMPLES

    private var frameCounter: Int = 0
    private val syncLock = Any()

    fun reset() {
        synchronized(syncLock) {
            renderRing.fill(0f)
            filterWeights.fill(0f)
            renderWriteCursor = 0
            totalRenderSamplesWritten = 0L
            lastFarEndTimestampMs = 0L
            farEndEnvelope = 0f
            currentDelaySamples = DEFAULT_ACOUSTIC_DELAY_SAMPLES
            frameCounter = 0
        }
    }

    fun onPlaybackTurnStarted() {
        synchronized(syncLock) {
            lastFarEndTimestampMs = System.currentTimeMillis()
        }
    }

    fun markFarEndSilent() {
        synchronized(syncLock) {
            farEndEnvelope = 0f
            lastFarEndTimestampMs = 0L
        }
    }

    /**
     * Feeds outgoing AudioTrack PCM16 chunks into the 16kHz render reference ring buffer,
     * resampling from `sampleRate` (e.g. 24000Hz) cleanly via linear interpolation.
     */
    fun pushFarEndPcm(
        pcmBytes: ByteArray,
        length: Int = pcmBytes.size,
        sampleRate: Int = 24000,
        @Suppress("UNUSED_PARAMETER") playbackFramePosition: Long = -1L
    ) {
        val sampleCount = length / 2
        if (sampleCount <= 0) return

        val safeRate = sampleRate.coerceIn(8000, 48000)
        val inputFloats = FloatArray(sampleCount)
        var sumSq = 0.0
        for (i in 0 until sampleCount) {
            val s = ((pcmBytes[i * 2 + 1].toInt() shl 8) or (pcmBytes[i * 2].toInt() and 0xFF)).toShort().toFloat() / 32768f
            inputFloats[i] = s
            sumSq += (s * s).toDouble()
        }
        val blockRms = sqrt(sumSq / sampleCount.coerceAtLeast(1)).toFloat()

        val targetSamples = if (safeRate == TARGET_SAMPLE_RATE) {
            inputFloats
        } else {
            val outLen = ((sampleCount.toLong() * TARGET_SAMPLE_RATE) / safeRate).toInt().coerceAtLeast(1)
            FloatArray(outLen) { outIdx ->
                val srcPos = outIdx.toDouble() * safeRate / TARGET_SAMPLE_RATE
                val idx0 = srcPos.toInt().coerceIn(0, sampleCount - 1)
                val idx1 = (idx0 + 1).coerceAtMost(sampleCount - 1)
                val frac = (srcPos - idx0).toFloat()
                inputFloats[idx0] * (1f - frac) + inputFloats[idx1] * frac
            }
        }

        synchronized(syncLock) {
            for (sample in targetSamples) {
                renderRing[renderWriteCursor] = sample
                renderWriteCursor = (renderWriteCursor + 1) % ringCapacitySamples
                totalRenderSamplesWritten++
            }
            farEndEnvelope = maxOf(blockRms, farEndEnvelope * 0.78f).coerceIn(0f, 1f)
            lastFarEndTimestampMs = System.currentTimeMillis()
        }
    }

    /**
     * Analyzes a 16kHz PCM16 capture frame, optionally performs NLMS acoustic echo subtraction,
     * and computes ERLE (`erleDb`), pitch/harmonicity, and speech-band spectral dominance.
     */
    fun processCaptureFrame(
        nearEndPcm: ByteArray,
        length: Int = nearEndPcm.size,
        applyEchoSubtraction: Boolean = true,
        nowMs: Long = System.currentTimeMillis()
    ): AecProcessResult {
        val n = length / 2
        if (n <= 8) {
            return AecProcessResult(
                cleanedPcm = nearEndPcm.copyOf(length.coerceAtLeast(0)),
                nearRms = 0f,
                farAlignedRms = 0f,
                residualRms = 0f,
                erleDb = 0f,
                pitchHz = 0f,
                harmonicity = 0f,
                voiceBandRatio = 0f,
                estimatedDelaySamples = currentDelaySamples,
                farEndActive = false
            )
        }

        val near = FloatArray(n)
        var nearSumSq = 0.0
        for (i in 0 until n) {
            val s = ((nearEndPcm[i * 2 + 1].toInt() shl 8) or (nearEndPcm[i * 2].toInt() and 0xFF)).toShort().toFloat() / 32768f
            near[i] = s
            nearSumSq += (s * s).toDouble()
        }
        val nearRms = (sqrt(nearSumSq / n) * RMS_DISPLAY_SCALE).toFloat().coerceIn(0f, 1f)

        val cleanedFloats = near.copyOf()
        var residualSumSq = nearSumSq
        var farAlignedSumSq = 0.0
        var activeFarEnd = false
        var delayUsed: Int

        synchronized(syncLock) {
            frameCounter++
            val elapsedMs = nowMs - lastFarEndTimestampMs
            activeFarEnd = lastFarEndTimestampMs > 0L &&
                elapsedMs in 0L..FAR_END_TAIL_WINDOW_MS &&
                totalRenderSamplesWritten > (currentDelaySamples + filterTaps + n)

            if (activeFarEnd && applyEchoSubtraction) {
                // Re-estimate acoustic round-trip delay periodically when echo energy is present
                if (frameCounter % 4 == 1 && nearRms > 0.010f) {
                    currentDelaySamples = estimateBestLagLocked(near, n)
                }
                delayUsed = currentDelaySamples

                val startHistoryOffset = min(totalRenderSamplesWritten, ringCapacitySamples.toLong()).toInt() - 1
                var blockPredSq = 0.0
                var blockNearSq = 0.0
                val predBuffer = FloatArray(n)

                for (i in 0 until n) {
                    // Map capture sample i to recent render history anchored at delayUsed
                    val baseOffsetFromEnd = n - i + delayUsed
                    if (baseOffsetFromEnd + filterTaps >= startHistoryOffset) {
                        predBuffer[i] = 0f
                        continue
                    }

                    var yHat = 0f
                    var normSq = NLMS_REGULARIZATION
                    for (k in 0 until filterTaps) {
                        val ringIdx = ((renderWriteCursor - baseOffsetFromEnd - k) % ringCapacitySamples + ringCapacitySamples) % ringCapacitySamples
                        val xk = renderRing[ringIdx]
                        yHat += filterWeights[k] * xk
                        normSq += xk * xk
                        if (k == 0) {
                            farAlignedSumSq += (xk * xk).toDouble()
                        }
                    }

                    predBuffer[i] = yHat.coerceIn(-1f, 1f)
                    blockPredSq += (predBuffer[i] * predBuffer[i]).toDouble()
                    blockNearSq += (near[i] * near[i]).toDouble()

                    // Double-talk detector freeze: do not adapt filter when near-end energy strongly dominates predicted echo
                    val err = near[i] - predBuffer[i]
                    val isLikelyDoubleTalk = blockNearSq > (blockPredSq * 2.3 + 0.0008) && nearRms > 0.032f
                    if (!isLikelyDoubleTalk) {
                        val step = (NLMS_STEP_SIZE * err) / normSq
                        for (k in 0 until filterTaps) {
                            val ringIdx = ((renderWriteCursor - baseOffsetFromEnd - k) % ringCapacitySamples + ringCapacitySamples) % ringCapacitySamples
                            val xk = renderRing[ringIdx]
                            filterWeights[k] = (filterWeights[k] + step * xk).coerceIn(-1.5f, 1.5f)
                        }
                    }
                }

                // Compute cleaned residual & verify it doesn't add energy
                var tempResSq = 0.0
                for (i in 0 until n) {
                    val e = (near[i] - predBuffer[i]).coerceIn(-1f, 1f)
                    cleanedFloats[i] = e
                    tempResSq += (e * e).toDouble()
                }
                if (tempResSq < nearSumSq) {
                    residualSumSq = tempResSq
                } else {
                    // Filter not converged yet; preserve raw near-end signal to avoid distortion
                    near.copyInto(cleanedFloats)
                    residualSumSq = nearSumSq
                }
            } else {
                delayUsed = currentDelaySamples
            }
        }

        val residualRms = (sqrt(residualSumSq / n) * RMS_DISPLAY_SCALE).toFloat().coerceIn(0f, 1f)
        val farAlignedRms = (sqrt(farAlignedSumSq / n.coerceAtLeast(1)) * RMS_DISPLAY_SCALE).toFloat().coerceIn(0f, 1f)
        val erleDb = if (activeFarEnd && nearSumSq > 1e-9 && residualSumSq > 1e-10) {
            (10.0 * log10((nearSumSq + 1e-9) / (residualSumSq + 1e-9))).toFloat().coerceIn(0f, 45f)
        } else {
            0f
        }

        val (pitchHz, harmonicity) = estimatePitchAndHarmonicity(cleanedFloats, n)
        val voiceBandRatio = estimateVoiceBandRatio(cleanedFloats, n, residualSumSq / n)

        val outBytes = if (activeFarEnd && applyEchoSubtraction && erleDb >= 1.2f) {
            ByteArray(n * 2).also { buf ->
                for (i in 0 until n) {
                    val s = (cleanedFloats[i] * 32767f).toInt().coerceIn(-32768, 32767)
                    buf[i * 2] = (s and 0xFF).toByte()
                    buf[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                }
            }
        } else {
            nearEndPcm.copyOf(n * 2)
        }

        return AecProcessResult(
            cleanedPcm = outBytes,
            nearRms = nearRms,
            farAlignedRms = farAlignedRms,
            residualRms = residualRms,
            erleDb = erleDb,
            pitchHz = pitchHz,
            harmonicity = harmonicity,
            voiceBandRatio = voiceBandRatio,
            estimatedDelaySamples = delayUsed,
            farEndActive = activeFarEnd
        )
    }

    private fun estimateBestLagLocked(near: FloatArray, n: Int): Int {
        val available = min(totalRenderSamplesWritten.toInt(), ringCapacitySamples)
        val maxSearch = min(MAX_SEARCH_LAG_SAMPLES, available - n - filterTaps - 1)
        if (maxSearch <= MIN_SEARCH_LAG_SAMPLES) return currentDelaySamples

        var nearEnergy = 1e-7f
        val stepSample = 2
        var i = 0
        while (i < n) {
            nearEnergy += near[i] * near[i]
            i += stepSample
        }

        var bestLag = currentDelaySamples.coerceIn(MIN_SEARCH_LAG_SAMPLES, maxSearch)
        var bestScore = -1f

        var lag = MIN_SEARCH_LAG_SAMPLES
        while (lag <= maxSearch) {
            var dot = 0f
            var farEnergy = 1e-7f
            var j = 0
            while (j < n) {
                val baseOffsetFromEnd = n - j + lag
                val ringIdx = ((renderWriteCursor - baseOffsetFromEnd) % ringCapacitySamples + ringCapacitySamples) % ringCapacitySamples
                val r = renderRing[ringIdx]
                dot += near[j] * r
                farEnergy += r * r
                j += stepSample
            }
            val score = kotlin.math.abs(dot) / sqrt(nearEnergy * farEnergy)
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
            lag += 16
        }

        // Smoothly slew delay estimate so jitter doesn't whip filter weights
        return if (bestScore > 0.25f) {
            ((currentDelaySamples * 2 + bestLag) / 3).coerceIn(MIN_SEARCH_LAG_SAMPLES, maxSearch)
        } else {
            currentDelaySamples
        }
    }

    private fun estimatePitchAndHarmonicity(samples: FloatArray, n: Int): Pair<Float, Float> {
        val maxLag = min(213, n / 2)
        if (n < 280 || maxLag <= 38) return 0f to 0f

        val lags = IntArray((maxLag - 38) / 2 + 1)
        val corrs = FloatArray(lags.size)
        var count = 0
        var globalMax = 0f

        // Downsampled stride-2 autocorrelation for ~75Hz..420Hz @ 16kHz
        var lag = 38
        while (lag <= maxLag) {
            var num = 0f
            var denA = 1e-8f
            var denB = 1e-8f
            val limit = n - lag
            var idx = 0
            while (idx < limit) {
                val a = samples[idx]
                val b = samples[idx + lag]
                num += a * b
                denA += a * a
                denB += b * b
                idx += 2
            }
            val norm = num / sqrt(denA * denB)
            lags[count] = lag
            corrs[count] = norm
            if (norm > globalMax) globalMax = norm
            count++
            lag += 2
        }

        if (count == 0 || globalMax < 0.28f) {
            return 0f to globalMax.coerceIn(0f, 1f)
        }

        // First-harmonic-peak selection avoids 2T subharmonic octave errors (e.g. picking 90Hz for 180Hz)
        val peakThreshold = maxOf(0.28f, globalMax * 0.85f)
        var chosenLag = lags[0]
        var chosenCorr = globalMax
        var foundPeak = false
        for (k in 0 until count) {
            val c = corrs[k]
            val prev = if (k > 0) corrs[k - 1] else -1f
            val next = if (k + 1 < count) corrs[k + 1] else -1f
            if (c >= peakThreshold && c >= prev && c >= next) {
                chosenLag = lags[k]
                chosenCorr = c
                foundPeak = true
                break
            }
        }
        if (!foundPeak) {
            val maxIdx = (0 until count).maxByOrNull { corrs[it] } ?: 0
            chosenLag = lags[maxIdx]
            chosenCorr = corrs[maxIdx]
        }

        val pitchHz = TARGET_SAMPLE_RATE.toFloat() / chosenLag.toFloat()
        return pitchHz to chosenCorr.coerceIn(0f, 1f)
    }

    private fun estimateVoiceBandRatio(samples: FloatArray, n: Int, meanSqEnergy: Double): Float {
        if (n <= 8 || meanSqEnergy < 1e-9) return 0f
        // Fast multi-frequency Goertzel probe across fundamental vocal fold (150-250Hz) & formant (350-2200Hz) bands
        val voiceFreqs = floatArrayOf(150f, 220f, 350f, 700f, 1200f, 2200f)
        val outBandFreqs = floatArrayOf(65f, 5200f)

        var voiceEnergy = 0.0
        for (f in voiceFreqs) {
            voiceEnergy += goertzelMagnitudeSq(samples, n, f)
        }
        var nonVoiceEnergy = 0.0
        for (f in outBandFreqs) {
            nonVoiceEnergy += goertzelMagnitudeSq(samples, n, f)
        }
        val denom = voiceEnergy + nonVoiceEnergy + 1e-9
        return (voiceEnergy / denom).toFloat().coerceIn(0f, 1f)
    }

    private fun goertzelMagnitudeSq(samples: FloatArray, n: Int, freqHz: Float): Double {
        val omega = (2.0 * PI * freqHz) / TARGET_SAMPLE_RATE.toDouble()
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

    companion object {
        const val TARGET_SAMPLE_RATE = 16000
        private const val RENDER_RING_CAPACITY = 24000 // 1.5s @ 16kHz
        private const val DEFAULT_FILTER_TAPS = 96     // 6ms local reflection window
        private const val DEFAULT_ACOUSTIC_DELAY_SAMPLES = 960 // 60ms default speaker-to-mic lag
        private const val MIN_SEARCH_LAG_SAMPLES = 240         // 15ms
        private const val MAX_SEARCH_LAG_SAMPLES = 3600        // 225ms
        private const val FAR_END_TAIL_WINDOW_MS = 280L
        private const val NLMS_STEP_SIZE = 0.22f
        private const val NLMS_REGULARIZATION = 1e-4f
        private const val RMS_DISPLAY_SCALE = 3.2768f // matches rms/10000 scale on Short samples
    }
}
