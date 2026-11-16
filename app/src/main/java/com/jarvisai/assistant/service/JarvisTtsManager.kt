package com.jarvisai.assistant.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.util.LruCache
import com.jarvisai.assistant.data.network.GeminiApiService
import com.jarvisai.assistant.data.preferences.UserPreferencesRepository
import com.jarvisai.assistant.model.SupportedGeminiModels
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.VoiceGender
import com.jarvisai.assistant.model.isRobolectricTestEnvironment
import com.jarvisai.assistant.security.SecureApiKeyManager
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Zero-Latency Streaming Google Gemini Voice & Speech Synthesizer for JARVIS AI.
 *
 * Architecture & Zero-Delay Pipeline:
 * 1. Primary Engine: Official Free Google AI Studio TTS (`gemini-2.5-flash-preview-tts` primary,
 *    with automatic free `gemini-2.0-flash` fallback) streamed over persistent HTTP Keep-Alive SSE
 *    (`:streamGenerateContent?alt=sse`) directly into a low-latency `AudioTrack`.
 * 2. Real-Time LLM-to-Audio Streaming:
 *    - During LLM text streaming (`GEMINI_API` / `ANTIGRAVITY`), complete clauses/sentences are fed
 *      into `feedStreamingAccumulatedText()` as they arrive and synthesized immediately in parallel.
 *    - For single-turn or final replies, `speakSynchronizedWithCommit()` prepares AudioTrack & buffers
 *      chunk #0, then triggers message UI commit at the EXACT 0ms instant audio begins playing.
 * 3. Two-Tier Caching (0ms Latency):
 *    - L1 RAM `LruCache` (~8 MB) + L2 MD5 PCM Disk Cache (`${appContext.cacheDir}/gemini_tts/`).
 * 4. Offline Fallback:
 *    - When offline or API key is empty, gracefully falls back to Android's built-in Google
 *      `TextToSpeech` engine (`hi_IN` / `en_IN`).
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

    @Volatile
    var lockedTtsModel: String = SupportedGeminiModels.DEFAULT_TTS_MODEL
        private set

    @Volatile
    private var lockedSpeed: Float = 1.0f

    @Volatile
    private var lockedPitch: Float = 1.0f

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _voiceStatusError = MutableStateFlow<String?>(null)
    val voiceStatusError: StateFlow<String?> = _voiceStatusError.asStateFlow()

    // L1 In-Memory Hot PCM Cache (key -> PCM bytes + sampleRate) for 0ms instant playback
    private val hotPcmCache = object : LruCache<String, Pair<ByteArray, Int>>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Pair<ByteArray, Int>): Int = value.first.size.coerceAtLeast(1)
    }

    private val pcmCacheDirectory: File by lazy {
        File(appContext.cacheDir, GEMINI_TTS_CACHE_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    @Volatile
    private var nativeTts: TextToSpeech? = null

    @Volatile
    private var isNativeTtsReady: Boolean = false

    private val pendingNativeQueue = mutableListOf<String>()

    // Streaming LLM-to-Speech session state
    private val sessionLock = Any()
    private var activeSessionId = AtomicInteger(0)
    private var currentStreamChannel: Channel<String>? = null
    private var currentStreamJob: Job? = null
    private var currentStreamFedIndex: Int = 0
    private var currentStreamEmittedSegments: Int = 0
    private var currentStreamLanguage: SupportedLanguage = SupportedLanguage.HINGLISH
    private var currentStreamGender: VoiceGender = VoiceGender.MALE
    private var currentStreamPersona: String = DEFAULT_MALE_PERSONA
    private var currentStreamModel: String = SupportedGeminiModels.DEFAULT_TTS_MODEL
    private var currentStreamSpeed: Float = 1.0f
    private var currentStreamPitch: Float = 1.0f
    private var currentStreamFirstAudioSignal: CompletableDeferred<Boolean>? = null

    init {
        initializeNativeTtsFallback()
        scope.launch {
            runCatching {
                if (!isRobolectricTestEnvironment()) {
                    val key = secureApiKeyManager.getApiKey().trim()
                    if (key.isNotEmpty() && isNetworkConnected()) {
                        geminiApiService.prewarmTtsConnection(key)
                    }
                }
            }
        }
        scope.launch {
            runCatching {
                preferencesRepository.userSettingsFlow.collect { settings ->
                    lockedVoiceGender = settings.voiceGender
                    lockedVoicePersona = resolveStrictGeminiVoicePersona(
                        gender = settings.voiceGender,
                        preferredPersona = settings.voicePersona
                    )
                    lockedTtsModel = settings.ttsModel.ifBlank { SupportedGeminiModels.DEFAULT_TTS_MODEL }
                    lockedSpeed = settings.voiceSpeed
                    lockedPitch = settings.voicePitch
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

    // -------------------------------------------------------------------------
    // LIVE LLM STREAMING-TO-AUDIO PIPELINE (0ms Delay Between Message & Voice)
    // -------------------------------------------------------------------------

    fun startStreamingSpeechSession(
        language: SupportedLanguage = SupportedLanguage.HINGLISH,
        voiceGender: VoiceGender? = null,
        voicePersona: String? = null,
        ttsModel: String? = null,
        speed: Float = lockedSpeed,
        pitch: Float = lockedPitch
    ) {
        stopSpeaking()
        val effectiveGender = voiceGender ?: lockedVoiceGender
        val effectivePersona = resolveStrictGeminiVoicePersona(effectiveGender, voicePersona ?: lockedVoicePersona)
        val effectiveModel = ttsModel?.takeIf { it.isNotBlank() } ?: lockedTtsModel

        val channel = Channel<String>(Channel.UNLIMITED)
        val signal = CompletableDeferred<Boolean>()
        val sessionNum = activeSessionId.incrementAndGet()

        synchronized(sessionLock) {
            currentStreamChannel = channel
            currentStreamFedIndex = 0
            currentStreamEmittedSegments = 0
            currentStreamLanguage = language
            currentStreamGender = effectiveGender
            currentStreamPersona = effectivePersona
            currentStreamModel = effectiveModel
            currentStreamSpeed = speed
            currentStreamPitch = pitch
            currentStreamFirstAudioSignal = signal
        }

        val apiKey = secureApiKeyManager.getApiKey().trim()
        if (apiKey.isNotEmpty() && isNetworkConnected() && !isRobolectricTestEnvironment()) {
            scope.launch { geminiApiService.prewarmTtsConnection(apiKey) }
        }

        val job = scope.launch {
            _voiceStatusError.value = null
            var sharedTrack: AudioTrack? = null
            var sharedSampleRate = 24000
            var everStartedAudio = false

            fun ensureTrack(sampleRate: Int): AudioTrack {
                val existing = sharedTrack
                if (existing != null && sharedSampleRate == sampleRate && existing.state == AudioTrack.STATE_INITIALIZED) {
                    return existing
                }
                closeTrackQuietly(existing)
                val created = createLowLatencyAudioTrack(sampleRate, speed, pitch)
                sharedTrack = created
                sharedSampleRate = sampleRate
                activeAudioTrack = created
                created.play()
                return created
            }

            try {
                // Keep reading sentences as they stream from LLM, pre-fetching the next sentence in parallel
                val iterator = channel.iterator()
                var bufferedNextDeferred: Deferred<Result<Pair<ByteArray, Int>>>? = null
                var bufferedNextSentence: String? = null

                suspend fun speakSentenceOrStream(sentence: String, prefetched: Deferred<Result<Pair<ByteArray, Int>>>?) {
                    val clean = sanitizeTextForSpeech(sentence)
                    if (clean.isBlank() || activeSessionId.get() != sessionNum) return
                    _isSpeaking.value = true

                    val cacheKey = buildCacheKey(effectiveModel, effectivePersona, clean)
                    val ramHit = hotPcmCache.get(cacheKey)
                    if (ramHit != null && ramHit.first.isNotEmpty()) {
                        val tr = ensureTrack(ramHit.second)
                        if (!everStartedAudio) {
                            everStartedAudio = true
                            signal.complete(true)
                        }
                        writePcmChunkToTrack(tr, ramHit.first, sessionNum)
                        return
                    }

                    val diskFile = getPcmCacheFile(cacheKey)
                    if (diskFile.exists() && diskFile.length() > MIN_VALID_PCM_BYTES) {
                        val bytes = runCatching { diskFile.readBytes() }.getOrNull()
                        if (bytes != null && bytes.isNotEmpty()) {
                            hotPcmCache.put(cacheKey, bytes to 24000)
                            val tr = ensureTrack(24000)
                            if (!everStartedAudio) {
                                everStartedAudio = true
                                signal.complete(true)
                            }
                            writePcmChunkToTrack(tr, bytes, sessionNum)
                            return
                        }
                    }

                    if (prefetched != null) {
                        val res = runCatching { prefetched.await() }.getOrNull()
                        if (res != null && res.isSuccess) {
                            val (pcm, sr) = res.getOrThrow()
                            if (pcm.isNotEmpty()) {
                                hotPcmCache.put(cacheKey, pcm to sr)
                                scope.launch(Dispatchers.IO) { runCatching { FileOutputStream(diskFile).use { it.write(pcm) } } }
                                val tr = ensureTrack(sr)
                                if (!everStartedAudio) {
                                    everStartedAudio = true
                                    signal.complete(true)
                                }
                                writePcmChunkToTrack(tr, pcm, sessionNum)
                                return
                            }
                        }
                    }

                    if (apiKey.isNotEmpty() && isNetworkConnected()) {
                        var streamedSuccess = false
                        val res = geminiApiService.streamGeminiSpeech(
                            apiKey = apiKey,
                            text = clean,
                            voiceName = effectivePersona,
                            modelId = effectiveModel
                        ) { pcmChunk, sr ->
                            if (!isActive || activeSessionId.get() != sessionNum) return@streamGeminiSpeech
                            val tr = ensureTrack(sr)
                            if (!everStartedAudio) {
                                everStartedAudio = true
                                signal.complete(true)
                            }
                            writePcmChunkToTrack(tr, pcmChunk, sessionNum)
                            streamedSuccess = true
                        }
                        if (res.isSuccess) {
                            val (fullPcm, sr) = res.getOrThrow()
                            if (fullPcm.isNotEmpty()) {
                                hotPcmCache.put(cacheKey, fullPcm to sr)
                                scope.launch(Dispatchers.IO) { runCatching { FileOutputStream(diskFile).use { it.write(fullPcm) } } }
                                return
                            }
                        } else if (!streamedSuccess) {
                            _voiceStatusError.value = res.exceptionOrNull()?.message
                        }
                    }

                    // Fallback to instant on-device Google TTS
                    if (!everStartedAudio) {
                        everStartedAudio = true
                        signal.complete(true)
                    }
                    speakWithNativeGoogleTtsSuspend(clean, language)
                }

                while (isActive && activeSessionId.get() == sessionNum) {
                    val currentSentence: String = if (bufferedNextSentence != null) {
                        val s = bufferedNextSentence!!
                        bufferedNextSentence = null
                        s
                    } else if (iterator.hasNext()) {
                        iterator.next()
                    } else {
                        break
                    }
                    val currentPrefetch = bufferedNextDeferred
                    bufferedNextDeferred = null

                    // Immediately peek if another sentence is already waiting in channel and pre-fetch it while speaking current!
                    val peekNext = channel.tryReceive().getOrNull()
                    if (!peekNext.isNullOrBlank() && apiKey.isNotEmpty() && isNetworkConnected()) {
                        bufferedNextSentence = peekNext
                        val cleanNext = sanitizeTextForSpeech(peekNext)
                        if (cleanNext.isNotBlank()) {
                            val nextKey = buildCacheKey(effectiveModel, effectivePersona, cleanNext)
                            if (hotPcmCache.get(nextKey) == null) {
                                bufferedNextDeferred = scope.async(Dispatchers.IO) {
                                    geminiApiService.synthesizeGeminiSpeech(
                                        apiKey = apiKey,
                                        text = cleanNext,
                                        voiceName = effectivePersona,
                                        modelId = effectiveModel
                                    )
                                }
                            }
                        }
                    }

                    speakSentenceOrStream(currentSentence, currentPrefetch)
                }

                sharedTrack?.let { waitForAudioTailBrief(it, sessionNum) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.w(TAG, "Streaming speech session notice: ${e.message}")
            } finally {
                if (!signal.isCompleted) signal.complete(false)
                closeTrackQuietly(sharedTrack)
                if (activeAudioTrack === sharedTrack) activeAudioTrack = null
                _isSpeaking.value = false
            }
        }

        synchronized(sessionLock) {
            currentStreamJob = job
            activePlaybackJob = job
        }
    }

    /**
     * Called incrementally during LLM token streaming (`Partial` / `TextChunk`).
     * Extracts complete clauses/sentences immediately so audio synthesis begins while text is still generating.
     */
    fun feedStreamingAccumulatedText(accumulatedRawText: String) {
        val channel = synchronized(sessionLock) { currentStreamChannel } ?: return
        val startIdx = synchronized(sessionLock) { currentStreamFedIndex }
        if (accumulatedRawText.length <= startIdx) return

        val pending = accumulatedRawText.substring(startIdx)
        val splitPoint = findSpeakableClauseBoundary(pending)
        if (splitPoint > 0) {
            val clause = pending.substring(0, splitPoint)
            synchronized(sessionLock) {
                currentStreamFedIndex = startIdx + splitPoint
                currentStreamEmittedSegments++
            }
            if (clause.isNotBlank()) {
                channel.trySend(clause)
            }
        }
    }

    /**
     * Finalizes a live streaming speech session AND executes [onReadyToShowMessage] at the exact 0ms
     * millisecond that audio begins playing (or immediately if audio already started during streaming).
     */
    suspend fun finishStreamingSpeechAndCommit(
        fullRawText: String,
        maxPreBufferWaitMs: Long = 1600L,
        onReadyToShowMessage: suspend () -> Unit
    ) {
        val channel: Channel<String>?
        val signal: CompletableDeferred<Boolean>?
        val alreadyEmitted: Int
        val fedIdx: Int

        synchronized(sessionLock) {
            channel = currentStreamChannel
            signal = currentStreamFirstAudioSignal
            alreadyEmitted = currentStreamEmittedSegments
            fedIdx = currentStreamFedIndex
            currentStreamChannel = null
        }

        if (channel == null || signal == null) {
            speakSynchronizedWithCommit(
                rawText = fullRawText,
                maxPreBufferWaitMs = maxPreBufferWaitMs,
                onReadyToShowMessage = onReadyToShowMessage
            )
            return
        }

        val tail = if (fedIdx < fullRawText.length) fullRawText.substring(fedIdx) else ""
        if (tail.isNotBlank()) {
            channel.trySend(tail)
        }
        channel.close()

        if (alreadyEmitted == 0 && sanitizeTextForSpeech(fullRawText).isNotBlank()) {
            // Wait ONLY for chunk #0 to begin playing on AudioTrack so message + voice appear together at 0ms offset!
            withTimeoutOrNull(maxPreBufferWaitMs) {
                signal.await()
            }
        }
        onReadyToShowMessage()
    }

    fun cancelStreamingSpeechSession() {
        val ch = synchronized(sessionLock) {
            val c = currentStreamChannel
            currentStreamChannel = null
            c
        }
        ch?.close()
        stopSpeaking()
    }

    /**
     * Speaks [rawText] AND waits ONLY for the first audio frame to become ready to play (0ms on cache hit,
     * first SSE chunk on streaming Gemini TTS), then invokes [onReadyToShowMessage] at the exact instant
     * audio output starts — eliminating the ~2-second gap between message appearing and voice starting.
     */
    suspend fun speakSynchronizedWithCommit(
        rawText: String,
        language: SupportedLanguage = SupportedLanguage.HINGLISH,
        voiceGender: VoiceGender? = null,
        voicePersona: String? = null,
        speed: Float = lockedSpeed,
        pitch: Float = lockedPitch,
        maxPreBufferWaitMs: Long = 1600L,
        onReadyToShowMessage: suspend () -> Unit
    ) {
        val cleanText = sanitizeTextForSpeech(rawText)
        if (cleanText.isBlank()) {
            onReadyToShowMessage()
            return
        }

        stopSpeaking()
        val sessionNum = activeSessionId.incrementAndGet()
        val effectiveGender = voiceGender ?: lockedVoiceGender
        val effectivePersona = resolveStrictGeminiVoicePersona(
            gender = effectiveGender,
            preferredPersona = voicePersona?.takeIf { it.isNotBlank() } ?: lockedVoicePersona
        )
        val effectiveModel = lockedTtsModel
        lockedVoiceGender = effectiveGender
        lockedVoicePersona = effectivePersona

        val firstAudioReady = CompletableDeferred<Boolean>()
        _voiceStatusError.value = null
        _isSpeaking.value = true

        val job = scope.launch {
            try {
                executeChunkedStreamingSpeak(
                    cleanText = cleanText,
                    language = language,
                    effectivePersona = effectivePersona,
                    effectiveModel = effectiveModel,
                    speed = speed,
                    pitch = pitch,
                    sessionNum = sessionNum,
                    onFirstAudioFrameStarted = {
                        if (!firstAudioReady.isCompleted) firstAudioReady.complete(true)
                    }
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "GEMINI_TTS_SYNC_PLAYBACK_ERROR: ${e.message}", e)
                _voiceStatusError.value = e.localizedMessage
            } finally {
                if (!firstAudioReady.isCompleted) firstAudioReady.complete(false)
                _isSpeaking.value = false
            }
        }
        activePlaybackJob = job

        withTimeoutOrNull(maxPreBufferWaitMs) {
            firstAudioReady.await()
        }
        onReadyToShowMessage()
    }

    /**
     * Standalone non-blocking speak using persistent Keep-Alive SSE streaming + RAM/Disk cache +
     * parallel multi-sentence pre-fetching so AudioTrack starts outputting audio immediately.
     */
    fun speak(
        rawText: String,
        language: SupportedLanguage = SupportedLanguage.HINGLISH,
        voiceGender: VoiceGender? = null,
        voicePersona: String = "Puck",
        speed: Float = lockedSpeed,
        pitch: Float = lockedPitch
    ): Boolean {
        val cleanText = sanitizeTextForSpeech(rawText)
        if (cleanText.isBlank()) return false

        stopSpeaking()
        val sessionNum = activeSessionId.incrementAndGet()

        _isSpeaking.value = true
        _voiceStatusError.value = null

        activePlaybackJob = scope.launch {
            try {
                speakSuspendInternal(
                    rawText = cleanText,
                    language = language,
                    voiceGender = voiceGender,
                    voicePersona = voicePersona,
                    speed = speed,
                    pitch = pitch,
                    sessionNum = sessionNum
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

    suspend fun speakSuspend(
        rawText: String,
        language: SupportedLanguage = SupportedLanguage.HINGLISH,
        voiceGender: VoiceGender? = null,
        voicePersona: String = "Puck",
        speed: Float = lockedSpeed,
        pitch: Float = lockedPitch
    ): Boolean = withContext(Dispatchers.IO) {
        val sessionNum = activeSessionId.incrementAndGet()
        speakSuspendInternal(rawText, language, voiceGender, voicePersona, speed, pitch, sessionNum)
    }

    private suspend fun speakSuspendInternal(
        rawText: String,
        language: SupportedLanguage,
        voiceGender: VoiceGender?,
        voicePersona: String,
        speed: Float,
        pitch: Float,
        sessionNum: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanText = sanitizeTextForSpeech(rawText)
        if (cleanText.isBlank()) return@withContext false

        val effectiveGender = voiceGender ?: lockedVoiceGender
        val effectivePersona = resolveStrictGeminiVoicePersona(
            gender = effectiveGender,
            preferredPersona = voicePersona.takeIf { it.isNotBlank() && it != "Puck" }
                ?: lockedVoicePersona
        )
        val effectiveModel = lockedTtsModel
        lockedVoiceGender = effectiveGender
        lockedVoicePersona = effectivePersona

        _voiceStatusError.value = null
        _isSpeaking.value = true

        try {
            executeChunkedStreamingSpeak(
                cleanText = cleanText,
                language = language,
                effectivePersona = effectivePersona,
                effectiveModel = effectiveModel,
                speed = speed,
                pitch = pitch,
                sessionNum = sessionNum,
                onFirstAudioFrameStarted = {}
            )
        } finally {
            _isSpeaking.value = false
        }
    }

    private suspend fun executeChunkedStreamingSpeak(
        cleanText: String,
        language: SupportedLanguage,
        effectivePersona: String,
        effectiveModel: String,
        speed: Float,
        pitch: Float,
        sessionNum: Int,
        onFirstAudioFrameStarted: () -> Unit
    ): Boolean = coroutineScope {
        val fullKey = buildCacheKey(effectiveModel, effectivePersona, cleanText)

        // 1. L1 RAM Cache (0ms)
        hotPcmCache.get(fullKey)?.let { (bytes, sr) ->
            if (bytes.isNotEmpty()) {
                val track = createLowLatencyAudioTrack(sr, speed, pitch)
                activeAudioTrack = track
                try {
                    track.play()
                    onFirstAudioFrameStarted()
                    writePcmChunkToTrack(track, bytes, sessionNum)
                    waitForAudioTailBrief(track, sessionNum)
                } finally {
                    closeTrackQuietly(track)
                    if (activeAudioTrack === track) activeAudioTrack = null
                }
                return@coroutineScope true
            }
        }

        // 2. L2 MD5 Disk Cache (0ms)
        val cacheFile = getPcmCacheFile(fullKey)
        if (cacheFile.exists() && cacheFile.length() > MIN_VALID_PCM_BYTES) {
            val cachedBytes = runCatching { cacheFile.readBytes() }.getOrNull()
            if (cachedBytes != null && cachedBytes.isNotEmpty()) {
                hotPcmCache.put(fullKey, cachedBytes to 24000)
                val track = createLowLatencyAudioTrack(24000, speed, pitch)
                activeAudioTrack = track
                try {
                    track.play()
                    onFirstAudioFrameStarted()
                    writePcmChunkToTrack(track, cachedBytes, sessionNum)
                    waitForAudioTailBrief(track, sessionNum)
                } finally {
                    closeTrackQuietly(track)
                    if (activeAudioTrack === track) activeAudioTrack = null
                }
                return@coroutineScope true
            }
        }

        // 3. Official Free Google Gemini SSE Streaming TTS (First chunk plays immediately without waiting for full response)
        val apiKey = secureApiKeyManager.getApiKey().trim()
        if (apiKey.isNotEmpty() && isNetworkConnected()) {
            // Split long messages cleanly so sentence #1 audio starts ASAP while remaining sentences pre-fetch in parallel
            val (headText, tailText) = splitFirstSentenceIfLong(cleanText)
            val tailPrefetch: Deferred<Result<Pair<ByteArray, Int>>>? = if (!tailText.isNullOrBlank()) {
                async(Dispatchers.IO) {
                    geminiApiService.synthesizeGeminiSpeech(
                        apiKey = apiKey,
                        text = tailText,
                        voiceName = effectivePersona,
                        modelId = effectiveModel
                    )
                }
            } else null

            var track: AudioTrack? = null
            var trackSampleRate = 24000
            var startedSignaled = false
            var succeeded = false

            fun ensureTrack(sampleRate: Int): AudioTrack {
                val cur = track
                if (cur != null && trackSampleRate == sampleRate && cur.state == AudioTrack.STATE_INITIALIZED) {
                    return cur
                }
                closeTrackQuietly(cur)
                val created = createLowLatencyAudioTrack(sampleRate, speed, pitch)
                track = created
                trackSampleRate = sampleRate
                activeAudioTrack = created
                created.play()
                return created
            }

            try {
                val headKey = buildCacheKey(effectiveModel, effectivePersona, headText)
                val headRes = geminiApiService.streamGeminiSpeech(
                    apiKey = apiKey,
                    text = headText,
                    voiceName = effectivePersona,
                    modelId = effectiveModel
                ) { chunkBytes, sr ->
                    if (!isActive || activeSessionId.get() != sessionNum) return@streamGeminiSpeech
                    val tr = ensureTrack(sr)
                    if (!startedSignaled) {
                        startedSignaled = true
                        onFirstAudioFrameStarted()
                    }
                    writePcmChunkToTrack(tr, chunkBytes, sessionNum)
                    succeeded = true
                }

                if (headRes.isSuccess) {
                    val (headPcm, sr) = headRes.getOrThrow()
                    if (headPcm.isNotEmpty()) {
                        hotPcmCache.put(headKey, headPcm to sr)
                        val headFile = getPcmCacheFile(headKey)
                        scope.launch(Dispatchers.IO) { runCatching { FileOutputStream(headFile).use { it.write(headPcm) } } }

                        if (tailPrefetch != null && isActive && activeSessionId.get() == sessionNum) {
                            val tailRes = runCatching { tailPrefetch.await() }.getOrNull()
                            if (tailRes != null && tailRes.isSuccess) {
                                val (tailPcm, tailSr) = tailRes.getOrThrow()
                                if (tailPcm.isNotEmpty()) {
                                    val tr = ensureTrack(tailSr)
                                    writePcmChunkToTrack(tr, tailPcm, sessionNum)
                                    if (tailSr == sr) {
                                        val combined = headPcm + tailPcm
                                        hotPcmCache.put(fullKey, combined to sr)
                                        scope.launch(Dispatchers.IO) { runCatching { FileOutputStream(cacheFile).use { it.write(combined) } } }
                                    }
                                }
                            } else if (!tailText.isNullOrBlank()) {
                                geminiApiService.streamGeminiSpeech(
                                    apiKey = apiKey,
                                    text = tailText,
                                    voiceName = effectivePersona,
                                    modelId = effectiveModel
                                ) { chunkBytes, tailSr ->
                                    if (!isActive || activeSessionId.get() != sessionNum) return@streamGeminiSpeech
                                    val tr = ensureTrack(tailSr)
                                    writePcmChunkToTrack(tr, chunkBytes, sessionNum)
                                }
                            }
                        }
                        track?.let { waitForAudioTailBrief(it, sessionNum) }
                        return@coroutineScope true
                    }
                } else {
                    tailPrefetch?.cancel()
                    if (!succeeded) {
                        val err = headRes.exceptionOrNull()?.message ?: "Gemini TTS synthesis failed"
                        Log.w(TAG, "GEMINI_TTS_STREAM_ERROR: $err -> Falling back to Google on-device TTS")
                        _voiceStatusError.value = err
                    } else {
                        track?.let { waitForAudioTailBrief(it, sessionNum) }
                        return@coroutineScope true
                    }
                }
            } finally {
                closeTrackQuietly(track)
                if (activeAudioTrack === track) activeAudioTrack = null
            }
        }

        // 4. Native Google TextToSpeech On-Device Fallback (0ms instant local speech)
        onFirstAudioFrameStarted()
        speakWithNativeGoogleTtsSuspend(cleanText, language)
    }

    /**
     * Plays a real natural voice sample for the selected Gemini voice persona.
     */
    fun previewVoice(
        voiceGender: VoiceGender,
        voicePersona: String,
        language: SupportedLanguage,
        speed: Float = lockedSpeed,
        pitch: Float = lockedPitch
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

    private fun createLowLatencyAudioTrack(sampleRate: Int, speed: Float, pitch: Float): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (speed != 1.0f || pitch != 1.0f)) {
            runCatching {
                track.playbackParams = PlaybackParams()
                    .setSpeed(speed.coerceIn(0.5f, 2.0f))
                    .setPitch(pitch.coerceIn(0.5f, 2.0f))
            }
        }
        return track
    }

    private fun writePcmChunkToTrack(track: AudioTrack, pcmData: ByteArray, sessionNum: Int) {
        val alignedSize = pcmData.size - (pcmData.size % 2)
        if (alignedSize <= 0) return
        var offset = 0
        val chunkSize = 4096
        while (offset < alignedSize && activeSessionId.get() == sessionNum) {
            val toWrite = minOf(chunkSize, alignedSize - offset)
            val written = track.write(pcmData, offset, toWrite, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) break
            offset += written
        }
    }

    private fun waitForAudioTailBrief(track: AudioTrack, sessionNum: Int) {
        val startWait = System.currentTimeMillis()
        var lastHead = -1
        while (activeSessionId.get() == sessionNum && System.currentTimeMillis() - startWait < 280L) {
            try {
                if (track.state == AudioTrack.STATE_INITIALIZED && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    val head = track.playbackHeadPosition
                    if (head == lastHead && head > 0) break
                    lastHead = head
                } else {
                    break
                }
            } catch (_: Exception) {
                break
            }
            Thread.sleep(20)
        }
    }

    private fun closeTrackQuietly(track: AudioTrack?) {
        if (track == null) return
        runCatching {
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.stop()
            }
        }
        runCatching { track.release() }
    }

    private fun findSpeakableClauseBoundary(text: String): Int {
        if (text.length < 18) return -1
        for (i in 12 until text.length) {
            val ch = text[i]
            if (ch == '\n' || ch == '!' || ch == '?' || ch == '।') {
                return i + 1
            }
            if (ch == '.' && (i + 1 >= text.length || text[i + 1].isWhitespace())) {
                return i + 1
            }
        }
        if (text.length >= 64) {
            for (i in 63 downTo 36) {
                if (text[i] == ',' || text[i] == ';' || text[i] == ':') {
                    return i + 1
                }
            }
            for (i in 63 downTo 40) {
                if (text[i].isWhitespace()) {
                    return i + 1
                }
            }
        }
        return -1
    }

    private fun splitFirstSentenceIfLong(cleanText: String): Pair<String, String?> {
        if (cleanText.length <= 105) return cleanText to null
        for (i in 22 until minOf(120, cleanText.length)) {
            val ch = cleanText[i]
            if (ch == '\n' || ch == '!' || ch == '?' || ch == '।' ||
                (ch == '.' && (i + 1 >= cleanText.length || cleanText[i + 1].isWhitespace()))
            ) {
                val head = cleanText.substring(0, i + 1).trim()
                val tail = cleanText.substring(i + 1).trim()
                if (head.length >= 16 && tail.length >= 12) {
                    return head to tail
                }
            }
        }
        return cleanText to null
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

    private fun buildCacheKey(modelId: String, voicePersona: String, cleanText: String): String {
        return md5("${modelId.trim().lowercase()}:${voicePersona.trim().lowercase()}:${cleanText.trim()}")
    }

    private fun getPcmCacheFile(cacheKey: String): File {
        return File(pcmCacheDirectory, "$cacheKey.pcm")
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
        activeSessionId.incrementAndGet()
        synchronized(sessionLock) {
            runCatching { currentStreamChannel?.close() }
            currentStreamChannel = null
            runCatching { currentStreamJob?.cancel() }
            currentStreamJob = null
            currentStreamFirstAudioSignal?.complete(false)
            currentStreamFirstAudioSignal = null
        }
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
