package com.jarvisai.assistant.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import com.jarvisai.assistant.data.network.GeminiApiService
import com.jarvisai.assistant.model.SupportedGeminiModels
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.VoiceGender
import com.jarvisai.assistant.security.SecureApiKeyManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real Gemini AI Voice Preview & Speech Synthesizer for JARVIS AI.
 *
 * Uses official Google Gemini TTS (`generateContent` with `responseModalities: ["AUDIO"]`
 * and `prebuiltVoiceConfig.voiceName`) and plays 24kHz 16-bit mono PCM audio via Android
 * `AudioTrack`. Never uses Android's robotic system `TextToSpeech`.
 */
class JarvisTtsManager(
    context: Context,
    private val geminiApiService: GeminiApiService,
    private val secureApiKeyManager: SecureApiKeyManager
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var activePlaybackJob: Job? = null

    @Volatile
    private var activeAudioTrack: AudioTrack? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _voiceStatusError = MutableStateFlow<String?>(null)
    val voiceStatusError: StateFlow<String?> = _voiceStatusError.asStateFlow()

    /**
     * Synthesizes and plays real Gemini voice audio for the given text and official Gemini voice ID.
     */
    fun speak(
        rawText: String,
        language: SupportedLanguage = SupportedLanguage.HINGLISH,
        voiceGender: VoiceGender = VoiceGender.MALE,
        voicePersona: String = "Puck",
        speed: Float = 1.0f,
        pitch: Float = 1.0f
    ): Boolean {
        val cleanText = sanitizeTextForSpeech(rawText)
        if (cleanText.isBlank()) return false

        val apiKey = secureApiKeyManager.getApiKey().trim()
        if (apiKey.isEmpty()) {
            _voiceStatusError.value = "Gemini API Key set nahi hai. Kripya API Settings mein apna Gemini API Key enter karein."
            return false
        }

        val voiceOption = SupportedGeminiModels.findVoiceOption(voicePersona, voiceGender)
        stopSpeaking()

        _isSpeaking.value = true
        _voiceStatusError.value = null

        activePlaybackJob = scope.launch {
            try {
                Log.i(TAG, "GEMINI_VOICE_PREVIEW_REQUEST: voice=${voiceOption.id} gender=${voiceOption.gender.name}")
                val result = geminiApiService.synthesizeGeminiSpeech(
                    apiKey = apiKey,
                    text = cleanText,
                    voiceName = voiceOption.id
                )
                if (!isActive) return@launch

                if (result.isFailure) {
                    val err = result.exceptionOrNull()?.message ?: "Gemini voice preview failed."
                    Log.e(TAG, "GEMINI_VOICE_PREVIEW_ERROR: $err")
                    _voiceStatusError.value = err
                    _isSpeaking.value = false
                    return@launch
                }

                val (pcmBytes, sampleRate) = result.getOrThrow()
                playPcmBytes(pcmBytes, sampleRate)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "GEMINI_VOICE_PREVIEW_EXCEPTION: ${e.message}", e)
                _voiceStatusError.value = "Gemini voice playback error: ${e.localizedMessage ?: "Unknown"}"
            } finally {
                _isSpeaking.value = false
            }
        }
        return true
    }

    /**
     * Plays a real Gemini AI voice sample for the selected Male or Female Gemini voice persona.
     */
    fun previewVoice(
        voiceGender: VoiceGender,
        voicePersona: String,
        language: SupportedLanguage,
        speed: Float = 1.0f,
        pitch: Float = 1.0f
    ): Boolean {
        val option = SupportedGeminiModels.findVoiceOption(voicePersona, voiceGender)
        return speak(
            rawText = option.previewSampleHinglish,
            language = language,
            voiceGender = option.gender,
            voicePersona = option.id,
            speed = speed,
            pitch = pitch
        )
    }

    private fun playPcmBytes(pcmData: ByteArray, sampleRate: Int) {
        if (pcmData.isEmpty()) return
        val alignedSize = pcmData.size - (pcmData.size % 2)
        if (alignedSize <= 0) return

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                }
            }
            .build()

        activeAudioTrack = track
        try {
            track.play()
            var offset = 0
            val chunkSize = 4096
            while (offset < alignedSize && activePlaybackJob?.isActive == true) {
                val toWrite = minOf(chunkSize, alignedSize - offset)
                val written = track.write(pcmData, offset, toWrite, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) break
                offset += written
            }
            // Wait briefly for hardware buffer tail to finish playing
            val totalSamples = alignedSize / 2
            val durationMs = ((totalSamples.toLong() * 1000L) / sampleRate.coerceAtLeast(1)).coerceIn(100L, 15000L)
            val startWait = System.currentTimeMillis()
            while (activePlaybackJob?.isActive == true && System.currentTimeMillis() - startWait < 350L) {
                try {
                    if (track.state == AudioTrack.STATE_INITIALIZED && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        val head = track.playbackHeadPosition
                        if (head >= totalSamples) break
                    } else {
                        break
                    }
                } catch (_: Exception) {
                    break
                }
                Thread.sleep(25)
            }
            Log.i(TAG, "GEMINI_VOICE_PREVIEW_COMPLETED: bytes=$alignedSize durationMs=$durationMs")
        } finally {
            try {
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.stop()
                }
            } catch (_: Exception) {
            }
            try {
                track.release()
            } catch (_: Exception) {
            }
            if (activeAudioTrack === track) {
                activeAudioTrack = null
            }
        }
    }

    fun stopSpeaking() {
        try {
            activePlaybackJob?.cancel()
            activePlaybackJob = null
        } catch (_: Exception) {
        }
        try {
            activeAudioTrack?.pause()
            activeAudioTrack?.flush()
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
        } catch (_: Exception) {
        }
        activeAudioTrack = null
        _isSpeaking.value = false
    }

    private fun sanitizeTextForSpeech(raw: String): String {
        return raw
            .replace(Regex("```[\\s\\S]*?```"), " code block ")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("[*_#~>|]+"), " ")
            .replace(Regex("\\[(.*?)]\\(.*?\\)"), "$1")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun shutdown() {
        stopSpeaking()
        scope.cancel()
    }

    companion object {
        private const val TAG = "JarvisTtsManager"

        @Volatile
        private var defaultInstance: JarvisTtsManager? = null

        fun getInstance(context: Context): JarvisTtsManager {
            return defaultInstance ?: synchronized(this) {
                defaultInstance ?: JarvisTtsManager(
                    context = context.applicationContext,
                    geminiApiService = GeminiApiService(),
                    secureApiKeyManager = SecureApiKeyManager(context.applicationContext)
                ).also { defaultInstance = it }
            }
        }

        fun speak(context: Context, text: String) {
            try {
                getInstance(context).speak(text)
            } catch (e: Exception) {
                Log.w(TAG, "Failed static speak: ${e.message}")
            }
        }
    }
}
