package com.jarvisai.assistant.cactus

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Lightweight 16kHz 16-bit Mono PCM WAV recorder for Cactus Compute Offline STT
 * (`Cactus-Compute/whistle` and `Cactus-Compute/whisper-small`).
 */
class CactusAudioRecorder(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private val pcmBuffer = ByteArrayOutputStream()

    @Volatile
    private var isRecording = false

    @SuppressLint("MissingPermission")
    fun startRecording(onAmplitudeUpdate: (Float) -> Unit): Boolean {
        if (isRecording) return true
        val sampleRate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        return try {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return false
            }
            synchronized(pcmBuffer) { pcmBuffer.reset() }
            recorder.startRecording()
            audioRecord = recorder
            isRecording = true

            recordJob = scope.launch {
                val readBuf = ByteArray(minBuf)
                while (isActive && isRecording) {
                    val read = recorder.read(readBuf, 0, readBuf.size)
                    if (read > 0) {
                        synchronized(pcmBuffer) {
                            pcmBuffer.write(readBuf, 0, read)
                        }
                        onAmplitudeUpdate(computeNormalizedRms(readBuf, read))
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Cactus WAV recorder: ${e.message}")
            cleanup()
            false
        }
    }

    /**
     * Stops recording and writes a standard 16kHz PCM WAV file in cacheDir ready for Cactus STT.
     */
    fun stopAndWriteWav(): File? {
        if (!isRecording && audioRecord == null) return null
        isRecording = false
        runCatching { recordJob?.cancel() }
        recordJob = null
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null

        val pcmData = synchronized(pcmBuffer) {
            val copy = pcmBuffer.toByteArray()
            pcmBuffer.reset()
            copy
        }
        if (pcmData.size < 640) return null

        return try {
            val outFile = File(context.cacheDir, "cactus_stt_${System.currentTimeMillis()}.wav")
            writeWavFile(outFile, pcmData, sampleRate = 16_000, channels = 1, bitsPerSample = 16)
            outFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing WAV file: ${e.message}")
            null
        }
    }

    fun cleanup() {
        isRecording = false
        runCatching { recordJob?.cancel() }
        recordJob = null
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
        synchronized(pcmBuffer) { pcmBuffer.reset() }
    }

    private fun computeNormalizedRms(bytes: ByteArray, length: Int): Float {
        var sumSq = 0.0
        var count = 0
        var i = 0
        while (i + 1 < length) {
            val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xFF)).toShort().toDouble()
            sumSq += sample * sample
            count++
            i += 2
        }
        if (count == 0) return 0f
        val rms = sqrt(sumSq / count)
        return ((rms / 9000.0).toFloat()).coerceIn(0f, 1f)
    }

    private fun writeWavFile(
        file: File,
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val chunkSize = 36 + dataSize

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(chunkSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort((channels * bitsPerSample / 8).toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()

        FileOutputStream(file).use { out ->
            out.write(header)
            out.write(pcmData)
        }
    }

    companion object {
        private const val TAG = "CactusAudioRecorder"
    }
}
