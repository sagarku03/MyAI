package com.jarvisai.assistant.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * Complete, zero-API-key Microsoft Studio Neural TTS Engine (`EdgeTtsManager`)
 * inspired by `AbuZar-Ansarii/Jarvis` (`edge_tts_service.dart`).
 *
 * Features:
 * 1. Direct WebSocket synthesis against Microsoft Edge Read-Aloud endpoint:
 *    `wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=6A5AA1D4EA6511D8AC7550A4C1A01CA2`
 * 2. Authentic British JARVIS Neural Voice:
 *    - Full voice descriptor: `Microsoft Server Speech Text to Speech Voice (en-GB, RyanNeural)`
 *    - Output format: `audio-24khz-48kbitrate-mono-mp3`
 * 3. Deterministic Local Disk Caching:
 *    - Computes MD5 hash of `"${voiceName}_${cleanText}"`.
 *    - Checks `${context.cacheDir}/edge_tts/${hash}.mp3`.
 *    - Plays immediately via `MediaPlayer` at 0ms network latency (100% offline capable for cached phrases).
 * 4. Native Android `TextToSpeech` Fallback:
 *    - Gracefully falls back to on-device `TextToSpeech` (`Locale.UK`) if offline on an uncached phrase or if the WebSocket fails.
 * 5. Audio Focus & Feedback Loop Protection:
 *    - Checks `AudioManager.isMusicActive`, requests transient audio focus, and pauses active speech recognition listeners while speaking.
 */
class EdgeTtsManager(
    context: Context,
    private val okHttpClient: OkHttpClient = defaultOkHttpClient()
) {
    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioManager: AudioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val cacheDirectory: File by lazy {
        File(appContext.cacheDir, CACHE_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Volatile
    private var activeJob: Job? = null

    @Volatile
    private var activeWebSocket: WebSocket? = null

    @Volatile
    private var activeMediaPlayer: MediaPlayer? = null

    @Volatile
    private var activeFocusRequest: AudioFocusRequest? = null

    @Volatile
    private var nativeTts: TextToSpeech? = null

    @Volatile
    private var isNativeTtsReady: Boolean = false

    private val pendingNativeQueue = mutableListOf<String>()

    /**
     * Optional callbacks invoked before and after JARVIS speaks so recognition listeners
     * can pause/resume and avoid self-triggering audio feedback loops.
     */
    @Volatile
    var onRecognitionPauseRequested: (() -> Unit)? = null

    @Volatile
    var onRecognitionResumeRequested: (() -> Unit)? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost ($focusChange) — stopping active TTS playback.")
                stop()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                try {
                    activeMediaPlayer?.setVolume(0.25f, 0.25f)
                } catch (_: Exception) {
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                try {
                    activeMediaPlayer?.setVolume(1.0f, 1.0f)
                } catch (_: Exception) {
                }
            }
        }
    }

    init {
        initializeNativeTtsFallback()
    }

    /**
     * Speaks [text] using Microsoft Edge Studio Neural TTS (`en-GB, RyanNeural`),
     * serving from local disk cache when available and falling back to native Android TTS on error.
     */
    fun speak(
        text: String,
        voiceName: String = DEFAULT_VOICE_FULL_NAME,
        ratePercent: Int = 5,
        pitchHz: Int = -2,
        volumePercent: Int = 100,
        onComplete: (() -> Unit)? = null
    ) {
        val cleanText = sanitizeTextForSpeech(text)
        if (cleanText.isBlank()) {
            onComplete?.invoke()
            return
        }

        stop()
        activeJob = scope.launch {
            try {
                speakSuspend(
                    text = cleanText,
                    voiceName = voiceName,
                    ratePercent = ratePercent,
                    pitchHz = pitchHz,
                    volumePercent = volumePercent
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in EdgeTtsManager.speak: ${e.localizedMessage}", e)
                _lastError.value = e.localizedMessage
            } finally {
                onComplete?.invoke()
            }
        }
    }

    /**
     * Suspending speech synthesis and playback pipeline:
     * 1. Check MD5 disk cache `${context.cacheDir}/edge_tts/${hash}.mp3`.
     * 2. If cached -> play immediately via `MediaPlayer` (0ms latency, zero internet required).
     * 3. If not cached -> stream MP3 chunks from Edge WebSocket, write to disk cache, and play.
     * 4. If network/WebSocket fails -> gracefully fall back to native Android `TextToSpeech`.
     */
    suspend fun speakSuspend(
        text: String,
        voiceName: String = DEFAULT_VOICE_FULL_NAME,
        ratePercent: Int = 5,
        pitchHz: Int = -2,
        volumePercent: Int = 100
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanText = sanitizeTextForSpeech(text)
        if (cleanText.isBlank()) return@withContext false

        val normalizedVoice = normalizeVoiceDescriptor(voiceName)
        val cachedFile = getCacheFile(normalizedVoice, cleanText)

        _lastError.value = null
        _isSpeaking.value = true
        engageAudioFocusAndListenerGuard()

        try {
            // 1. Local Disk Cache Fast-Path (0ms latency, offline-ready)
            if (cachedFile.exists() && cachedFile.length() > MIN_VALID_MP3_BYTES) {
                Log.i(TAG, "EDGE_TTS_CACHE_HIT: Playing cached neural audio (${cachedFile.length()} bytes) -> ${cachedFile.name}")
                val playedCached = playMp3FileSuspend(cachedFile)
                if (playedCached) {
                    return@withContext true
                } else {
                    Log.w(TAG, "Corrupted cached MP3 detected; deleting ${cachedFile.name} and re-synthesizing.")
                    cachedFile.delete()
                }
            }

            // 2. Live WebSocket Synthesis via Microsoft Edge Read-Aloud Service
            if (isNetworkConnected()) {
                val synthesizedFile = synthesizeToCacheViaWebSocket(
                    cleanText = cleanText,
                    voiceDescriptor = normalizedVoice,
                    targetFile = cachedFile,
                    ratePercent = ratePercent,
                    pitchHz = pitchHz,
                    volumePercent = volumePercent
                )
                if (synthesizedFile != null && synthesizedFile.exists() && synthesizedFile.length() > MIN_VALID_MP3_BYTES) {
                    val playedNetwork = playMp3FileSuspend(synthesizedFile)
                    if (playedNetwork) {
                        return@withContext true
                    }
                }
            } else {
                Log.w(TAG, "Device is offline and phrase is not in local Edge TTS cache; falling back to native Android TTS.")
            }

            // 3. Graceful Native Android TextToSpeech Fallback
            Log.i(TAG, "EDGE_TTS_FALLBACK: Using native Android TextToSpeech for text='${cleanText.take(60)}'")
            return@withContext speakWithNativeTtsFallbackSuspend(cleanText)
        } finally {
            releaseAudioFocusAndListenerGuard()
            _isSpeaking.value = false
        }
    }

    /**
     * Computes deterministic MD5 hash of `"${voiceName}_${cleanText}"`.
     */
    fun computeCacheHash(voiceName: String, cleanText: String): String {
        val normalizedVoice = normalizeVoiceDescriptor(voiceName)
        val key = "${normalizedVoice}_${cleanText.trim()}"
        val digest = MessageDigest.getInstance("MD5")
        val hashBytes = digest.digest(key.toByteArray(StandardCharsets.UTF_8))
        return buildString(hashBytes.size * 2) {
            for (b in hashBytes) {
                append(String.format(Locale.US, "%02x", b.toInt() and 0xFF))
            }
        }
    }

    /**
     * Returns the target cache file `${context.cacheDir}/edge_tts/${hash}.mp3`.
     */
    fun getCacheFile(voiceName: String, cleanText: String): File {
        val hash = computeCacheHash(voiceName, cleanText)
        if (!cacheDirectory.exists()) {
            cacheDirectory.mkdirs()
        }
        return File(cacheDirectory, "$hash.mp3")
    }

    /**
     * Builds the `speech.config` frame required by the Microsoft Edge Read-Aloud WebSocket protocol.
     */
    fun buildSpeechConfigFrame(timestamp: String = buildEdgeTimestamp()): String {
        return buildString {
            append("X-Timestamp:").append(timestamp).append("\r\n")
            append("Content-Type:application/json; charset=utf-8\r\n")
            append("Path:speech.config\r\n\r\n")
            append(
                """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"$AUDIO_OUTPUT_FORMAT"}}}}"""
            )
        }
    }

    /**
     * Builds the SSML WebSocket frame for `Microsoft Server Speech Text to Speech Voice (en-GB, RyanNeural)`.
     */
    fun buildSsmlFrame(
        requestId: String,
        cleanText: String,
        voiceDescriptor: String = DEFAULT_VOICE_FULL_NAME,
        ratePercent: Int = 5,
        pitchHz: Int = -2,
        volumePercent: Int = 100,
        timestamp: String = buildEdgeTimestamp()
    ): String {
        val escapedText = escapeXml(cleanText)
        val rateStr = if (ratePercent >= 0) "+${ratePercent}%" else "${ratePercent}%"
        val pitchStr = if (pitchHz >= 0) "+${pitchHz}Hz" else "${pitchHz}Hz"
        val volumeStr = "+${volumePercent.coerceIn(0, 100)}%"
        val xmlLang = extractLocaleFromVoiceDescriptor(voiceDescriptor)

        val ssmlBody = buildString {
            append("<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='").append(xmlLang).append("'>")
            append("<voice name='").append(voiceDescriptor).append("'>")
            append("<prosody pitch='").append(pitchStr)
            append("' rate='").append(rateStr)
            append("' volume='").append(volumeStr).append("'>")
            append(escapedText)
            append("</prosody>")
            append("</voice>")
            append("</speak>")
        }

        return buildString {
            append("X-RequestId:").append(requestId).append("\r\n")
            append("Content-Type:application/ssml+xml\r\n")
            append("X-Timestamp:").append(timestamp).append("Z\r\n")
            append("Path:ssml\r\n\r\n")
            append(ssmlBody)
        }
    }

    /**
     * Connects to the Microsoft Edge Read-Aloud WebSocket, sends the `speech.config` and `ssml` frames,
     * collects all binary MP3 chunks until `Path:turn.end`, and writes the result atomically to [targetFile].
     */
    private suspend fun synthesizeToCacheViaWebSocket(
        cleanText: String,
        voiceDescriptor: String,
        targetFile: File,
        ratePercent: Int,
        pitchHz: Int,
        volumePercent: Int
    ): File? = withTimeoutOrNull(WEBSOCKET_SYNTHESIS_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            val requestId = UUID.randomUUID().toString().replace("-", "")
            val urlWithConnectionId = "$EDGE_TTS_WEBSOCKET_URL&ConnectionId=$requestId"

            val request = Request.Builder()
                .url(urlWithConnectionId)
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("Origin", EDGE_EXTENSION_ORIGIN)
                .header("User-Agent", EDGE_USER_AGENT)
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Accept-Language", "en-GB,en-US;q=0.9,en;q=0.8")
                .build()

            val audioBuffer = ByteArrayOutputStream(32 * 1024)
            var completed = false

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "Edge TTS WebSocket connected (HTTP ${response.code}), requestId=$requestId")
                    val timestamp = buildEdgeTimestamp()
                    val configSent = webSocket.send(buildSpeechConfigFrame(timestamp))
                    val ssmlSent = webSocket.send(
                        buildSsmlFrame(
                            requestId = requestId,
                            cleanText = cleanText,
                            voiceDescriptor = voiceDescriptor,
                            ratePercent = ratePercent,
                            pitchHz = pitchHz,
                            volumePercent = volumePercent,
                            timestamp = timestamp
                        )
                    )
                    if (!configSent || !ssmlSent) {
                        Log.w(TAG, "Failed to dispatch Edge TTS handshake frames on open.")
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    try {
                        val audioPayload = extractMp3ChunkFromBinaryFrame(bytes)
                        if (audioPayload != null && audioPayload.isNotEmpty()) {
                            synchronized(audioBuffer) {
                                audioBuffer.write(audioPayload)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing binary frame from Edge TTS: ${e.localizedMessage}", e)
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("Path:turn.end", ignoreCase = true)) {
                        synchronized(this) {
                            if (completed) return
                            completed = true
                        }
                        try {
                            webSocket.close(1000, "Synthesis complete")
                        } catch (_: Exception) {
                        }
                        val mp3Bytes = synchronized(audioBuffer) { audioBuffer.toByteArray() }
                        if (mp3Bytes.size > MIN_VALID_MP3_BYTES) {
                            val saved = writeBytesAtomically(targetFile, mp3Bytes)
                            if (continuation.isActive) {
                                continuation.resume(if (saved) targetFile else null)
                            }
                        } else {
                            Log.w(TAG, "Edge TTS turn.end received but MP3 buffer was empty (${mp3Bytes.size} bytes).")
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    try {
                        webSocket.close(code, reason)
                    } catch (_: Exception) {
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    synchronized(this) {
                        if (completed) return
                        completed = true
                    }
                    val mp3Bytes = synchronized(audioBuffer) { audioBuffer.toByteArray() }
                    if (mp3Bytes.size > MIN_VALID_MP3_BYTES) {
                        val saved = writeBytesAtomically(targetFile, mp3Bytes)
                        if (continuation.isActive) {
                            continuation.resume(if (saved) targetFile else null)
                        }
                    } else if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "Edge TTS WebSocket failure: ${t.localizedMessage} (HTTP ${response?.code})")
                    _lastError.value = t.localizedMessage ?: "Edge TTS WebSocket failure"
                    synchronized(this) {
                        if (completed) return
                        completed = true
                    }
                    val mp3Bytes = synchronized(audioBuffer) { audioBuffer.toByteArray() }
                    if (mp3Bytes.size > MIN_VALID_MP3_BYTES) {
                        val saved = writeBytesAtomically(targetFile, mp3Bytes)
                        if (continuation.isActive) {
                            continuation.resume(if (saved) targetFile else null)
                        }
                    } else if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }

            val ws = okHttpClient.newWebSocket(request, listener)
            activeWebSocket = ws

            continuation.invokeOnCancellation {
                try {
                    ws.cancel()
                } catch (_: Exception) {
                }
                if (activeWebSocket === ws) {
                    activeWebSocket = null
                }
            }
        }
    }

    /**
     * Parses a binary WebSocket message from Microsoft Edge Read-Aloud service.
     *
     * Binary frame layout:
     * - Bytes [0..1]: Big-endian uint16 header length `N`
     * - Bytes [2..2+N-1]: UTF-8 header string (contains `Path:audio`)
     * - Bytes [2+N..end]: Raw MP3 audio stream bytes
     */
    internal fun extractMp3ChunkFromBinaryFrame(bytes: ByteString): ByteArray? {
        val totalSize = bytes.size
        if (totalSize <= 2) return null

        val headerLength = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
        val payloadOffset = 2 + headerLength

        if (headerLength in 1..(totalSize - 2)) {
            val headerText = bytes.substring(2, payloadOffset).utf8()
            if (headerText.contains("Path:audio", ignoreCase = true)) {
                return if (payloadOffset < totalSize) {
                    bytes.substring(payloadOffset, totalSize).toByteArray()
                } else {
                    ByteArray(0)
                }
            }
            return null
        }

        // Fallback if raw MP3 frame (e.g., ID3 tag or MPEG sync word 0xFF 0xFB/0xF3) was sent directly
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        if ((b0 == 0x49 && b1 == 0x44) || (b0 == 0xFF && (b1 and 0xE0) == 0xE0)) {
            return bytes.toByteArray()
        }
        return null
    }

    private fun writeBytesAtomically(targetFile: File, data: ByteArray): Boolean {
        return try {
            targetFile.parentFile?.let { parent ->
                if (!parent.exists()) parent.mkdirs()
            }
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            FileOutputStream(tempFile).use { fos ->
                fos.write(data)
                fos.flush()
                fos.fd.sync()
            }
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            targetFile.exists() && targetFile.length() > MIN_VALID_MP3_BYTES
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing MP3 cache file ${targetFile.absolutePath}: ${e.localizedMessage}", e)
            false
        }
    }

    /**
     * Plays the given MP3 file using Android `MediaPlayer` on the main/audio thread
     * and suspends until playback finishes or fails.
     */
    private suspend fun playMp3FileSuspend(mp3File: File): Boolean = withContext(Dispatchers.Main) {
        if (!mp3File.exists() || mp3File.length() <= 0L) return@withContext false

        suspendCancellableCoroutine { continuation ->
            var resumed = false
            val player = MediaPlayer()
            activeMediaPlayer = player

            fun safeResume(success: Boolean) {
                if (!resumed) {
                    resumed = true
                    if (continuation.isActive) {
                        continuation.resume(success)
                    }
                }
            }

            try {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                player.setDataSource(mp3File.absolutePath)
                player.setOnCompletionListener { mp ->
                    try {
                        mp.reset()
                        mp.release()
                    } catch (_: Exception) {
                    }
                    if (activeMediaPlayer === mp) {
                        activeMediaPlayer = null
                    }
                    safeResume(true)
                }
                player.setOnErrorListener { mp, what, extra ->
                    Log.w(TAG, "MediaPlayer error (what=$what, extra=$extra) for ${mp3File.name}")
                    try {
                        mp.reset()
                        mp.release()
                    } catch (_: Exception) {
                    }
                    if (activeMediaPlayer === mp) {
                        activeMediaPlayer = null
                    }
                    safeResume(false)
                    true
                }
                player.prepare()
                player.start()
            } catch (e: Exception) {
                Log.e(TAG, "MediaPlayer exception playing ${mp3File.name}: ${e.localizedMessage}", e)
                try {
                    player.release()
                } catch (_: Exception) {
                }
                if (activeMediaPlayer === player) {
                    activeMediaPlayer = null
                }
                safeResume(false)
            }

            continuation.invokeOnCancellation {
                try {
                    if (player.isPlaying) {
                        player.stop()
                    }
                } catch (_: Exception) {
                }
                try {
                    player.release()
                } catch (_: Exception) {
                }
                if (activeMediaPlayer === player) {
                    activeMediaPlayer = null
                }
            }
        }
    }

    /**
     * Audio Focus & Echo/Feedback Loop Protection:
     * - Checks `AudioManager.isMusicActive`.
     * - Requests transient audio focus (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` or `AUDIOFOCUS_GAIN_TRANSIENT`).
     * - Signals any active speech recognition listener to pause while JARVIS speaks.
     */
    private fun engageAudioFocusAndListenerGuard() {
        try {
            val externalMusicPlaying = audioManager.isMusicActive
            if (externalMusicPlaying) {
                Log.d(TAG, "External audio/music active detected via AudioManager.isMusicActive — requesting transient audio focus and pausing recognition listener.")
            }

            onRecognitionPauseRequested?.invoke()

            val focusGainType = if (externalMusicPlaying) {
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            } else {
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(focusGainType)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                activeFocusRequest = focusRequest
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    focusGainType
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request audio focus: ${e.localizedMessage}")
        }
    }

    private fun releaseAudioFocusAndListenerGuard() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeFocusRequest?.let { req ->
                    audioManager.abandonAudioFocusRequest(req)
                }
                activeFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to abandon audio focus: ${e.localizedMessage}")
        } finally {
            try {
                onRecognitionResumeRequested?.invoke()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Initializes native Android `TextToSpeech` as an offline/network-failure fallback
     * with British English (`Locale.UK`) voice selection.
     */
    private fun initializeNativeTtsFallback() {
        try {
            nativeTts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = nativeTts ?: return@TextToSpeech
                    val ukResult = engine.setLanguage(Locale.UK)
                    if (ukResult == TextToSpeech.LANG_MISSING_DATA || ukResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale.US)
                    }
                    engine.setPitch(0.95f)
                    engine.setSpeechRate(1.03f)
                    isNativeTtsReady = true
                    synchronized(pendingNativeQueue) {
                        if (pendingNativeQueue.isNotEmpty()) {
                            val next = pendingNativeQueue.removeAt(0)
                            pendingNativeQueue.clear()
                            speakNativeImmediate(next)
                        }
                    }
                } else {
                    Log.w(TAG, "Native Android TextToSpeech initialization returned status=$status")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize native Android TextToSpeech fallback: ${e.localizedMessage}")
        }
    }

    private suspend fun speakWithNativeTtsFallbackSuspend(cleanText: String): Boolean =
        withTimeoutOrNull(15_000L) {
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

                    val utteranceId = "jarvis_fallback_${UUID.randomUUID()}"
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
                    Log.e(TAG, "Native Android TTS fallback error: ${e.localizedMessage}", e)
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
            val utteranceId = "jarvis_queued_${UUID.randomUUID()}"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (e: Exception) {
            Log.w(TAG, "speakNativeImmediate failed: ${e.localizedMessage}")
        }
    }

    /**
     * Stops any active WebSocket stream, MediaPlayer playback, or native TTS utterance immediately.
     */
    fun stop() {
        try {
            activeJob?.cancel()
            activeJob = null
        } catch (_: Exception) {
        }
        try {
            activeWebSocket?.cancel()
            activeWebSocket = null
        } catch (_: Exception) {
        }
        try {
            activeMediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.reset()
                mp.release()
            }
        } catch (_: Exception) {
        }
        activeMediaPlayer = null
        try {
            nativeTts?.stop()
        } catch (_: Exception) {
        }
        releaseAudioFocusAndListenerGuard()
        _isSpeaking.value = false
    }

    /**
     * Clears cached MP3 files in `${context.cacheDir}/edge_tts/`.
     */
    fun clearDiskCache(): Int {
        var deletedCount = 0
        try {
            cacheDirectory.listFiles()?.forEach { file ->
                if (file.isFile && file.delete()) {
                    deletedCount++
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed clearing Edge TTS cache: ${e.localizedMessage}")
        }
        return deletedCount
    }

    fun shutdown() {
        stop()
        try {
            nativeTts?.shutdown()
            nativeTts = null
            isNativeTtsReady = false
        } catch (_: Exception) {
        }
        scope.cancel()
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

    private fun normalizeVoiceDescriptor(voice: String): String {
        val trimmed = voice.trim()
        if (trimmed.isEmpty()) return DEFAULT_VOICE_FULL_NAME
        if (trimmed.startsWith("Microsoft Server Speech Text to Speech Voice", ignoreCase = true)) {
            return trimmed
        }
        // Convert short neural name like "en-GB-RyanNeural" -> "Microsoft Server Speech Text to Speech Voice (en-GB, RyanNeural)"
        val parts = trimmed.split("-")
        return if (parts.size >= 3) {
            val locale = "${parts[0]}-${parts[1]}"
            val shortVoice = parts.drop(2).joinToString("-")
            "Microsoft Server Speech Text to Speech Voice ($locale, $shortVoice)"
        } else {
            DEFAULT_VOICE_FULL_NAME
        }
    }

    private fun extractLocaleFromVoiceDescriptor(voiceDescriptor: String): String {
        val match = Regex("\\(([a-zA-Z]{2}-[a-zA-Z]{2}),").find(voiceDescriptor)
        return match?.groupValues?.getOrNull(1) ?: "en-GB"
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

    private fun escapeXml(input: String): String {
        return buildString(input.length + 16) {
            for (ch in input) {
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(ch)
                }
            }
        }
    }

    private fun buildEdgeTimestamp(): String {
        val sdf = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    companion object {
        private const val TAG = "EdgeTtsManager"
        const val CACHE_DIR_NAME = "edge_tts"
        private const val MIN_VALID_MP3_BYTES = 256L
        private const val WEBSOCKET_SYNTHESIS_TIMEOUT_MS = 12_000L

        const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EA6511D8AC7550A4C1A01CA2"
        const val EDGE_TTS_WEBSOCKET_URL =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=$TRUSTED_CLIENT_TOKEN"

        const val DEFAULT_VOICE_SHORT_NAME = "en-GB-RyanNeural"
        const val DEFAULT_VOICE_FULL_NAME =
            "Microsoft Server Speech Text to Speech Voice (en-GB, RyanNeural)"
        const val AUDIO_OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3"

        private const val EDGE_EXTENSION_ORIGIN =
            "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold"
        private const val EDGE_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"

        @Volatile
        private var instance: EdgeTtsManager? = null

        fun getInstance(context: Context): EdgeTtsManager {
            return instance ?: synchronized(this) {
                instance ?: EdgeTtsManager(context.applicationContext).also { instance = it }
            }
        }

        private fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .pingInterval(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
