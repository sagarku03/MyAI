package com.jarvisai.assistant.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.jarvisai.assistant.data.network.GeminiApiService
import com.jarvisai.assistant.data.preferences.UserPreferencesRepository
import com.jarvisai.assistant.model.SupportedGeminiModels
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.VoiceGender
import com.jarvisai.assistant.security.SecureApiKeyManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Google Gemini Expressive Natural Human Voice & Speech Synthesizer for JARVIS AI.
 *
 * Architecture & Execution Pipeline:
 * 1. Primary Engine: Official Google Gemini 2.0 Flash (`gemini-2.0-flash:generateContent`) with
 *    native Hindi/Hinglish expressive human voices:
 *    - Male Voice: "Puck" (Conversational human) or "Charon" (Authoritative tone)
 *    - Female Voice: "Aoede" or "Kore" (Natural studio tone)
 * 2. Deterministic MD5 PCM Disk Caching:
 *    - Cache folder: `${appContext.cacheDir}/gemini_tts/`
 *    - Hash key: MD5 of `"$voicePersona:$cleanText"`
 *    - If `{hash}.pcm` exists on disk: read and play immediately via AudioTrack at 0ms network latency.
 *    - If not cached: call `geminiApiService.synthesizeGeminiSpeech()`, play audio via `AudioTrack`,
 *      and persist PCM bytes to cache file asynchronously on Dispatchers.IO.
 * 3. Offline Fallback:
 *    - When the device is offline or API key is not configured, gracefully falls back to Android's
 *      built-in Google TextToSpeech engine configured with Hindi (`hi_IN`) or Indian English (`en_IN`).
 *      Never uses British RyanNeural.
 */
class JarvisTtsManager(
    context: Context,
    private val geminiApiService: GeminiApiService,
    private val secureApiKeyManager: SecureApiKeyManager
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferencesRepository = UserPreferencesRepository(appContext)

    @Volatile
    private var activePlaybackJob: Job? = null

    @Volatile
    private var activeAudioTrack: AudioTrack? = null

    @Volatile
    var lockedVoiceGender: VoiceGender = VoiceGender.MALE
        private set

    @Volatile
    var lockedVoicePersona: String = DEFAULT_MALE_PERSONA
        private set

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _voiceStatusError = MutableStateFlow<String?>(null)
    val voiceStatusError: StateFlow<String?> = _voiceStatusError.asStateFlow()

    private val pcmCacheDirectory: File by lazy {
        File(appContext.cacheDir, GEMINI_TTS_CACHE_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    @Volatile
    private var nativeTts: TextToSpeech? = null

    @Volatile
    private var isNativeTtsReady: Boolean = false

    private val pendingNativeQueue = mutableListOf<String>()

    init {
        initializeNativeTtsFallback()
        scope.launch {
            runCatching {
                preferencesRepository.userSettingsFlow.collect { settings ->
                    lockedVoiceGender = settings.voiceGender
                    lockedVoicePersona = resolveStrictGeminiVoicePersona(
                        gender = settings.voiceGender,
                        preferredPersona = settings.voicePersona
                    )
                }
            }
        }
    }

    fun setLockedVoicePreference(gender: VoiceGender, persona: String? = null) {
        lockedVoiceGender = gender
        lockedVoicePersona = resolveStrictGeminiVoicePersona(gender, persona)
    }

    fun resolveStrictGeminiVoicePersona(
        gender: VoiceGender = lockedVoiceGender,
        preferredPersona: String? = lockedVoicePersona
    ): String {
        val candidate = preferredPersona?.trim().orEmpty()
        return if (gender == VoiceGender.FEMALE) {
            when {
                candidate.equals("Aoede", ignoreCase = true) -> "Aoede"
                candidate.equals("Kore", ignoreCase = true) -> "Kore"
                candidate.equals("Leda", ignoreCase = true) -> "Leda"
                candidate.equals("Zephyr", ignoreCase = true) -> "Zephyr"
                else -> DEFAULT_FEMALE_PERSONA
            }
        } else {
            when {
                candidate.equals("Puck", ignoreCase = true) -> "Puck"
                candidate.equals("Charon", ignoreCase = true) -> "Charon"
                candidate.equals("Fenrir", ignoreCase = true) -> "Fenrir"
                candidate.equals("Orus", ignoreCase = true) -> "Orus"
                else -> DEFAULT_MALE_PERSONA
            }
        }
    }

    // Retained for backward compatibility with existing callers
    fun resolveStrictEdgeVoiceName(
        gender: VoiceGender = lockedVoiceGender,
        preferredName: String? = lockedVoicePersona
    ): String = resolveStrictGeminiVoicePersona(gender, preferredName)

    /**
     * Synthesizes and plays expressive Google Gemini natural voice audio for the given text.
     * Serves from local MD5 PCM disk cache at 0ms latency when available, queries Gemini 2.0 Flash
     * for fresh speech, and falls back to Google on-device TextToSpeech with Hindi/English locale when offline.
     */
    fun speak(
        rawText: String,
        language: SupportedLanguage = SupportedLanguage.HINGLISH,
        voiceGender: VoiceGender? = null,
        voicePersona: String = "Puck",
        speed: Float = 1.0f,
        pitch: Float = 1.0f
    ): Boolean {
        val cleanText = sanitizeTextForSpeech(rawText)
        if (cleanText.isBlank()) return false

        stopSpeaking()

        _isSpeaking.value = true
        _voiceStatusError.value = null

        activePlaybackJob = scope.launch {
            try {
                speakSuspend(
                    rawText = cleanText,
                    language = language,
                    voiceGender = voiceGender,
                    voicePersona = voicePersona,
                    speed = speed,
                    pitch = pitch
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "GEMINI_TTS_PLAYBACK_EXCEPTION: ${e.message}", e)
                _voiceStatusError.value = e.localizedMessage
            } finally {
                _isSpeaking.value = false
            }
        }
        return true
    }

    /**
     * Suspending speech pipeline:
     * 1. Check MD5 disk cache `${appContext.cacheDir}/gemini_tts/${hash}.pcm`.
     * 2. If cached -> play immediately via AudioTrack (0ms latency, zero internet required).
     * 3. If not cached & online -> call `geminiApiService.synthesizeGeminiSpeech()`, write PCM bytes
     *    to disk cache asynchronously, and stream via `AudioTrack`.
     * 4. If offline or Gemini API error -> fallback to native Google Android TTS (Hindi/English).
     */
    suspend fun speakSuspend(
        rawText: String,
        language: SupportedLanguage = SupportedLanguage.HINGLISH,
        voiceGender: VoiceGender? = null,
        voicePersona: String = "Puck",
        speed: Float = 1.0f,
        pitch: Float = 1.0f
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanText = sanitizeTextForSpeech(rawText)
        if (cleanText.isBlank()) return@withContext false

        val persistedSettings = runCatching { preferencesRepository.getCurrentSettings() }.getOrNull()
        val effectiveGender = voiceGender ?: persistedSettings?.voiceGender ?: lockedVoiceGender
        val effectivePersona = resolveStrictGeminiVoicePersona(
            gender = effectiveGender,
            preferredPersona = voicePersona.takeIf { it.isNotBlank() && it != "Puck" }
                ?: persistedSettings?.voicePersona
                ?: lockedVoicePersona
        )
        lockedVoiceGender = effectiveGender
        lockedVoicePersona = effectivePersona

        val cacheFile = getPcmCacheFile(effectivePersona, cleanText)

        _voiceStatusError.value = null
        _isSpeaking.value = true

        try {
            // 1. MD5 Disk Cache Fast-Path (0ms latency, 100% offline-ready)
            if (cacheFile.exists() && cacheFile.length() > MIN_VALID_PCM_BYTES) {
                Log.i(TAG, "GEMINI_TTS_CACHE_HIT: Playing cached PCM (${cacheFile.length()} bytes) -> ${cacheFile.name}")
                val cachedBytes = cacheFile.readBytes()
                if (cachedBytes.isNotEmpty()) {
                    playPcmBytes(cachedBytes, sampleRate = 24000)
                    return@withContext true
                }
            }

            // 2. Official Google Gemini 2.0 Flash Audio Synthesis
            val apiKey = secureApiKeyManager.getApiKey().trim()
            if (apiKey.isNotEmpty() && isNetworkConnected()) {
                Log.i(TAG, "GEMINI_TTS_SYNTHESIS_REQUEST: persona=$effectivePersona gender=${effectiveGender.name} text='${cleanText.take(45)}'")
                val result = geminiApiService.synthesizeGeminiSpeech(
                    apiKey = apiKey,
                    text = cleanText,
                    voiceName = effectivePersona
                )
                if (result.isSuccess) {
                    val (pcmBytes, sampleRate) = result.getOrThrow()
                    if (pcmBytes.isNotEmpty()) {
                        // Persist to local disk cache asynchronously
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                FileOutputStream(cacheFile).use { it.write(pcmBytes) }
                                Log.d(TAG, "GEMINI_TTS_CACHED: Wrote ${pcmBytes.size} bytes to ${cacheFile.name}")
                            }
                        }
                        playPcmBytes(pcmBytes, sampleRate)
                        return@withContext true
                    }
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Gemini TTS synthesis failed"
                    Log.w(TAG, "GEMINI_TTS_API_ERROR: $err -> Falling back to Google on-device TTS")
                    _voiceStatusError.value = err
                }
            } else {
                Log.i(TAG, "Device offline or API key empty -> Using Google on-device TTS fallback")
            }

            // 3. Native Google TextToSpeech On-Device Fallback (Hindi/English Locale)
            speakWithNativeGoogleTtsSuspend(cleanText, language)
        } finally {
            _isSpeaking.value = false
        }
    }

    /**
     * Plays a real natural voice sample for the selected Gemini voice persona.
     */
    fun previewVoice(
        voiceGender: VoiceGender,
        voicePersona: String,
        language: SupportedLanguage,
        speed: Float = 1.0f,
        pitch: Float = 1.0f
    ): Boolean {
        val option = SupportedGeminiModels.findVoiceOption(voicePersona, voiceGender)
        val strictPersona = resolveStrictGeminiVoicePersona(voiceGender, voicePersona)
        return speak(
            rawText = option.previewSampleHinglish,
            language = language,
            voiceGender = voiceGender,
            voicePersona = strictPersona,
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
            Log.i(TAG, "GEMINI_VOICE_PLAYBACK_COMPLETED: bytes=$alignedSize sampleRate=$sampleRate")
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

    private fun initializeNativeTtsFallback() {
        try {
            nativeTts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = nativeTts ?: return@TextToSpeech
                    val hindiLocale = Locale("hi", "IN")
                    val result = engine.setLanguage(hindiLocale)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale("en", "IN"))
                    }
                    engine.setPitch(1.0f)
                    engine.setSpeechRate(1.0f)
                    isNativeTtsReady = true
                    synchronized(pendingNativeQueue) {
                        if (pendingNativeQueue.isNotEmpty()) {
                            val next = pendingNativeQueue.removeAt(0)
                            pendingNativeQueue.clear()
                            speakNativeImmediate(next)
                        }
                    }
                } else {
                    Log.w(TAG, "Native Google TextToSpeech init returned status=$status")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize native Google TextToSpeech fallback: ${e.localizedMessage}")
        }
    }

    private suspend fun speakWithNativeGoogleTtsSuspend(
        cleanText: String,
        language: SupportedLanguage
    ): Boolean = withTimeoutOrNull(12_000L) {
        suspendCancellableCoroutine { continuation ->
            try {
                val engine = nativeTts
                if (engine == null || !isNativeTtsReady) {
                    synchronized(pendingNativeQueue) {
                        pendingNativeQueue.add(cleanText)
                    }
                    initializeNativeTtsFallback()
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                    return@suspendCancellableCoroutine
                }

                if (language == SupportedLanguage.HINDI || language == SupportedLanguage.HINGLISH) {
                    engine.language = Locale("hi", "IN")
                } else {
                    engine.language = Locale("en", "IN")
                }

                val utteranceId = "jarvis_gemini_fallback_${UUID.randomUUID()}"
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}

                    override fun onDone(id: String?) {
                        if (id == utteranceId && continuation.isActive) {
                            continuation.resume(true)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) {
                        if (id == utteranceId && continuation.isActive) {
                            continuation.resume(false)
                        }
                    }

                    override fun onError(id: String?, errorCode: Int) {
                        if (id == utteranceId && continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                })

                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }
                val speakRes = engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                if (speakRes == TextToSpeech.ERROR && continuation.isActive) {
                    continuation.resume(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Native Google TTS error: ${e.localizedMessage}", e)
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
        }
    } ?: false

    private fun speakNativeImmediate(cleanText: String) {
        try {
            val engine = nativeTts ?: return
            if (!isNativeTtsReady) return
            val utteranceId = "jarvis_gemini_queued_${UUID.randomUUID()}"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (e: Exception) {
            Log.w(TAG, "speakNativeImmediate failed: ${e.localizedMessage}")
        }
    }

    private fun getPcmCacheFile(voicePersona: String, cleanText: String): File {
        val hash = md5("${voicePersona.trim().lowercase()}:${cleanText.trim()}")
        return File(pcmCacheDirectory, "$hash.pcm")
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun isNetworkConnected(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
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
        try {
            nativeTts?.stop()
        } catch (_: Exception) {
        }
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
        try {
            nativeTts?.shutdown()
            nativeTts = null
            isNativeTtsReady = false
        } catch (_: Exception) {
        }
        scope.cancel()
    }

    companion object {
        private const val TAG = "JarvisTtsManager"
        private const val GEMINI_TTS_CACHE_DIR = "gemini_tts"
        private const val MIN_VALID_PCM_BYTES = 512L

        const val DEFAULT_MALE_PERSONA = "Puck"
        const val DEFAULT_FEMALE_PERSONA = "Aoede"

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
