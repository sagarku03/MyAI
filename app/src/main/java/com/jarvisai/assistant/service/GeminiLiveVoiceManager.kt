package com.jarvisai.assistant.service

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import com.jarvisai.assistant.data.local.MemoryEntity
import com.jarvisai.assistant.data.network.BidiModelCandidate
import com.jarvisai.assistant.data.network.GeminiApiService
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.SupportedLanguage
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

data class LiveTranscriptItem(
    val id: String = UUID.randomUUID().toString(),
    val speaker: String, // "USER", "JARVIS", "SYSTEM", "DEBUG"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Official Gemini Multimodal Live API (`BidiGenerateContent`) real-time bidirectional
 * WebSocket audio engine.
 *
 * Key architecture guarantees:
 * - Streams 16kHz 16-bit mono PCM microphone audio in 40ms (1280-byte) low-latency chunks
 *   in real time without buffering full sentences.
 * - Uses adaptive client-side VAD + noise gate to send crystal-clear digital silence frames
 *   immediately after user speech ends (`USER_TURN_COMPLETED`), eliminating 5-10s server VAD
 *   delays caused by room background noise.
 * - Decouples WebSocket message reception from `AudioTrack.write()` via a dedicated lock-free
 *   playback channel (`pcmPlaybackChannel`) so incoming audio chunks play immediately
 *   (`AUDIO_PLAYBACK_STARTED`) without blocking the WebSocket reader thread.
 * - Supports instant barge-in interruption while AI is speaking, proper Android AudioFocus,
 *   hardware AEC/NS, and automatic reconnection on unexpected drops.
 */
class GeminiLiveVoiceManager(
    private val context: Context,
    private val geminiApiService: GeminiApiService
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val webSocketClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Full-duplex persistent stream
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Concurrency & session lifecycle guards
    private val sessionGeneration = AtomicInteger(0)
    private val userInitiatedStop = AtomicBoolean(false)
    private val wsSetupCompleted = AtomicBoolean(false)
    private val isModelCurrentlyResponding = AtomicBoolean(false)
    private val isAudioPlaybackActive = AtomicBoolean(false)
    private val micChunksSentCount = AtomicLong(0L)
    private val audioChunksReceivedCount = AtomicLong(0L)

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var audioTrack: AudioTrack? = null

    private var recordingJob: Job? = null
    private var playbackJob: Job? = null
    private var sessionInitJob: Job? = null
    private var turnWatchdogJob: Job? = null
    private var reconnectJob: Job? = null

    private var pcmPlaybackChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private var audioFocusRequest: AudioFocusRequest? = null

    // Standalone TTS used only by TextChatScreen's "Speak Aloud" button
    private var tts: TextToSpeech? = null
    private var ttsInitialized = false

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _status = MutableStateFlow(AssistantStatus.IDLE)
    val status: StateFlow<AssistantStatus> = _status.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _connectionStateLabel = MutableStateFlow("DISCONNECTED")
    val connectionStateLabel: StateFlow<String> = _connectionStateLabel.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _transcripts = MutableStateFlow<List<LiveTranscriptItem>>(emptyList())
    val transcripts: StateFlow<List<LiveTranscriptItem>> = _transcripts.asStateFlow()

    // Retained parameters for automatic reconnection
    private var lastApiKey: String = ""
    private var lastVoiceModel: String = ""
    private var lastVoicePersona: String = "Puck"
    private var lastAssistantName: String = "JARVIS"
    private var lastLanguage: SupportedLanguage = SupportedLanguage.ENGLISH
    private var lastMemories: List<MemoryEntity> = emptyList()
    private var reconnectAttempts = 0

    init {
        initStandaloneChatTts()
    }

    private fun logEvent(eventTag: String, detail: String, showInHud: Boolean = false) {
        val message = "[$eventTag] $detail"
        Log.d(TAG, message)
        if (showInHud) {
            appendTranscript("SYSTEM", message)
        }
    }

    fun startLiveSession(
        apiKey: String,
        voiceModel: String,
        textModel: String,
        voicePersona: String,
        assistantName: String,
        language: SupportedLanguage,
        voiceSpeed: Float,
        voicePitch: Float,
        memories: List<MemoryEntity>
    ) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            logEvent("SESSION_ERROR", "Missing Gemini API key", showInHud = true)
            _errorMessage.value = "Gemini API Key missing. Configure your key in API Settings first."
            return
        }

        // Increment generation so any previous WebSocket listeners are ignored
        val currentGen = sessionGeneration.incrementAndGet()
        userInitiatedStop.set(false)
        wsSetupCompleted.set(false)
        isModelCurrentlyResponding.set(false)
        isAudioPlaybackActive.set(false)
        micChunksSentCount.set(0L)
        audioChunksReceivedCount.set(0L)
        reconnectAttempts = 0

        cleanupActiveResources()

        lastApiKey = cleanKey
        lastVoiceModel = voiceModel
        lastVoicePersona = voicePersona.ifBlank { "Puck" }
        lastAssistantName = assistantName.ifBlank { "JARVIS" }
        lastLanguage = language
        lastMemories = memories

        _errorMessage.value = null
        _isMicMuted.value = false
        _isSessionActive.value = true
        _status.value = AssistantStatus.THINKING
        _connectionStateLabel.value = "INITIALIZING LIVE AUDIO..."

        requestVoiceAudioFocus()
        configureAudioRouting(active = true)
        initAudioTrackPlayback()
        startNonBlockingPlaybackWorker(currentGen)

        sessionInitJob?.cancel()
        sessionInitJob = scope.launch {
            val candidates = resolveBidiCandidates(cleanKey, voiceModel)
            if (!isActive || sessionGeneration.get() != currentGen || !_isSessionActive.value) {
                return@launch
            }
            if (candidates.isEmpty()) {
                logEvent("SESSION_ERROR", "No Bidi Live models available for key", showInHud = true)
                _errorMessage.value = "No Gemini Live audio models available for this API key."
                stopLiveSessionInternal(resetError = false)
                return@launch
            }

            connectWebSocketWithCandidates(
                generation = currentGen,
                apiKey = cleanKey,
                candidates = candidates,
                candidateIndex = 0
            )
        }
    }

    private suspend fun resolveBidiCandidates(
        apiKey: String,
        preferredVoiceModel: String
    ): List<BidiModelCandidate> {
        val catalog = geminiApiService.fetchAvailableModels(apiKey)
        val cleanPreferred = preferredVoiceModel.removePrefix("models/").trim()

        val result = mutableListOf<BidiModelCandidate>()

        // 1. If user explicitly chose a specific Bidi model in API Settings, try it first
        if (cleanPreferred.isNotBlank() &&
            cleanPreferred != "auto-live-voice" &&
            cleanPreferred != "gemini-2.0-flash-exp"
        ) {
            val matchedFromCatalog = catalog.bidiCandidates.find { it.modelId == cleanPreferred }
            if (matchedFromCatalog != null) {
                result.add(matchedFromCatalog)
            } else {
                result.add(BidiModelCandidate(cleanPreferred, cleanPreferred, "v1beta"))
                result.add(BidiModelCandidate(cleanPreferred, cleanPreferred, "v1alpha"))
            }
        }

        // 2. Add all discovered `bidiGenerateContent` models from ListModels (already ranked with native-audio / live first)
        catalog.bidiCandidates.forEach { candidate ->
            if (result.none { it.modelId == candidate.modelId && it.apiVersion == candidate.apiVersion }) {
                result.add(candidate)
            }
        }

        // 3. Fallback known official Gemini Live Bidi models in case ListModels was restricted
        val knownLiveFallbacks = listOf(
            BidiModelCandidate("gemini-2.5-flash-native-audio-preview-12-2025", "Gemini 2.5 Flash Native Audio", "v1beta"),
            BidiModelCandidate("gemini-2.5-flash-native-audio-preview-09-2025", "Gemini 2.5 Flash Native Audio", "v1beta"),
            BidiModelCandidate("gemini-2.0-flash-live-001", "Gemini 2.0 Flash Live", "v1beta"),
            BidiModelCandidate("gemini-live-2.5-flash-preview", "Gemini Live 2.5 Flash", "v1beta"),
            BidiModelCandidate("gemini-2.0-flash-live-001", "Gemini 2.0 Flash Live (v1alpha)", "v1alpha")
        )
        for (fb in knownLiveFallbacks) {
            if (result.none { it.modelId == fb.modelId && it.apiVersion == fb.apiVersion }) {
                result.add(fb)
            }
        }

        return result
    }

    private fun connectWebSocketWithCandidates(
        generation: Int,
        apiKey: String,
        candidates: List<BidiModelCandidate>,
        candidateIndex: Int
    ) {
        if (sessionGeneration.get() != generation || !_isSessionActive.value || userInitiatedStop.get()) {
            return
        }
        if (candidateIndex >= candidates.size) {
            val msg = "All Gemini Live Bidi models failed handshake. Check your API key or model permissions."
            logEvent("SESSION_ERROR", msg, showInHud = true)
            _errorMessage.value = msg
            stopLiveSessionInternal(resetError = false)
            return
        }

        val currentCandidate = candidates[candidateIndex]
        wsSetupCompleted.set(false)
        _connectionStateLabel.value = "CONNECTING // ${currentCandidate.modelId} (${currentCandidate.apiVersion})"

        val wsUrl =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.${currentCandidate.apiVersion}.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(wsUrl).build()
        val fullModelPath = if (currentCandidate.modelId.startsWith("models/")) {
            currentCandidate.modelId
        } else {
            "models/${currentCandidate.modelId}"
        }

        try {
            webSocket?.cancel()
        } catch (_: Exception) {
        }

        webSocket = webSocketClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (sessionGeneration.get() != generation || !_isSessionActive.value) {
                        webSocket.close(1000, null)
                        return
                    }
                    val setupJson = buildSetupMessage(
                        modelPath = fullModelPath,
                        voicePersona = lastVoicePersona,
                        assistantName = lastAssistantName,
                        language = lastLanguage,
                        memories = lastMemories
                    )
                    webSocket.send(setupJson.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (sessionGeneration.get() != generation || !_isSessionActive.value) return
                    handleServerMessage(generation, currentCandidate, text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    if (sessionGeneration.get() != generation || !_isSessionActive.value) return
                    handleServerMessage(generation, currentCandidate, bytes.utf8())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    if (sessionGeneration.get() != generation || !_isSessionActive.value || userInitiatedStop.get()) {
                        return
                    }

                    // If handshake hasn't completed or model returned 1008/1007, immediately try next candidate model
                    if (!wsSetupCompleted.get() || code == 1008 || code == 1007 || code == 1003) {
                        logEvent(
                            "SESSION_ERROR",
                            "Model ${currentCandidate.modelId} (${currentCandidate.apiVersion}) closed with $code ($reason). Trying next Live model...",
                            showInHud = false
                        )
                        connectWebSocketWithCandidates(
                            generation = generation,
                            apiKey = apiKey,
                            candidates = candidates,
                            candidateIndex = candidateIndex + 1
                        )
                    } else {
                        // Session expired or dropped mid-call: attempt automatic reconnection
                        logEvent(
                            "SESSION_ERROR",
                            "Live session disconnected ($code: $reason). Attempting auto-reconnect...",
                            showInHud = true
                        )
                        scheduleAutoReconnect(generation, candidates, candidateIndex)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (sessionGeneration.get() != generation || !_isSessionActive.value || userInitiatedStop.get()) {
                        return
                    }
                    val detail = response?.message?.takeIf { it.isNotBlank() }
                        ?: t.localizedMessage
                        ?: "WebSocket transport failure"

                    if (!wsSetupCompleted.get() && candidateIndex + 1 < candidates.size) {
                        logEvent(
                            "SESSION_ERROR",
                            "Handshake failed on ${currentCandidate.modelId}: $detail. Trying next model...",
                            showInHud = false
                        )
                        connectWebSocketWithCandidates(
                            generation = generation,
                            apiKey = apiKey,
                            candidates = candidates,
                            candidateIndex = candidateIndex + 1
                        )
                    } else if (wsSetupCompleted.get() && reconnectAttempts < MAX_AUTO_RECONNECTS) {
                        logEvent("SESSION_ERROR", "Connection drop ($detail). Reconnecting...", showInHud = true)
                        scheduleAutoReconnect(generation, candidates, candidateIndex)
                    } else {
                        logEvent("SESSION_ERROR", "Fatal WebSocket failure: $detail", showInHud = true)
                        _errorMessage.value = "Live Voice connection failed: $detail"
                        stopLiveSessionInternal(resetError = false)
                    }
                }
            }
        )
    }

    private fun scheduleAutoReconnect(
        generation: Int,
        candidates: List<BidiModelCandidate>,
        candidateIndex: Int
    ) {
        if (userInitiatedStop.get() || !_isSessionActive.value || sessionGeneration.get() != generation) return
        if (reconnectAttempts >= MAX_AUTO_RECONNECTS) {
            logEvent("SESSION_ERROR", "Maximum reconnection attempts reached.", showInHud = true)
            _errorMessage.value = "Live Voice connection lost after retries."
            stopLiveSessionInternal(resetError = false)
            return
        }

        reconnectAttempts++
        val delayMs = 1000L * reconnectAttempts
        _connectionStateLabel.value = "RECONNECTING (ATTEMPT $reconnectAttempts)..."

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (sessionGeneration.get() == generation && _isSessionActive.value && !userInitiatedStop.get()) {
                connectWebSocketWithCandidates(
                    generation = generation,
                    apiKey = lastApiKey,
                    candidates = candidates,
                    candidateIndex = candidateIndex
                )
            }
        }
    }

    private fun buildSetupMessage(
        modelPath: String,
        voicePersona: String,
        assistantName: String,
        language: SupportedLanguage,
        memories: List<MemoryEntity>
    ): JSONObject {
        val systemPrompt = buildString {
            append("You are $assistantName, a real-time voice AI assistant in a natural phone-call style conversation. ")
            append("Respond immediately, naturally, concisely, and conversationally. Keep answers brief (1 to 3 sentences unless asked for detail) for fast spoken dialogue. ")
            append(language.systemPromptDirective)
            if (memories.isNotEmpty()) {
                append("\nVerified user memories:\n")
                memories.take(6).forEach { mem ->
                    append("- ${mem.title}: ${mem.content}\n")
                }
            }
        }

        return JSONObject().apply {
            put(
                "setup",
                JSONObject().apply {
                    put("model", modelPath)
                    put(
                        "generationConfig",
                        JSONObject().apply {
                            put("responseModalities", JSONArray().put("AUDIO"))
                            put(
                                "speechConfig",
                                JSONObject().apply {
                                    put(
                                        "voiceConfig",
                                        JSONObject().apply {
                                            put(
                                                "prebuiltVoiceConfig",
                                                JSONObject().apply {
                                                    put("voiceName", voicePersona.ifBlank { "Puck" })
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                    put(
                        "systemInstruction",
                        JSONObject().apply {
                            put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().put("text", systemPrompt)
                                )
                            )
                        }
                    )
                }
            )
        }
    }

    private fun handleServerMessage(
        generation: Int,
        candidate: BidiModelCandidate,
        rawJson: String
    ) {
        try {
            val json = JSONObject(rawJson)

            // 1. SESSION_CONNECTED (setupComplete)
            if (json.has("setupComplete")) {
                wsSetupCompleted.set(true)
                reconnectAttempts = 0
                _errorMessage.value = null
                _connectionStateLabel.value = "LIVE // ${candidate.modelId}"
                _status.value = if (_isMicMuted.value) AssistantStatus.IDLE else AssistantStatus.LISTENING
                logEvent(
                    "SESSION_CONNECTED",
                    "WebSocket setupComplete on ${candidate.modelId} (${candidate.apiVersion})",
                    showInHud = true
                )
                ensureMicrophoneCaptureRunning(generation)
                return
            }

            // 2. SESSION_ERROR from server payload
            if (json.has("error")) {
                val errObj = json.optJSONObject("error")
                val code = errObj?.optInt("code", 0) ?: 0
                val msg = errObj?.optString("message") ?: "Gemini Live server error"
                logEvent("SESSION_ERROR", "Server error ($code): $msg", showInHud = true)
                _errorMessage.value = msg
                return
            }

            val serverContent = json.optJSONObject("serverContent") ?: return

            // 3. Server-side interruption notification
            if (serverContent.optBoolean("interrupted", false)) {
                logEvent("GEMINI_RESPONSE_COMPLETED", "Response interrupted by user speech", showInHud = false)
                flushAudioPlaybackImmediately()
                isModelCurrentlyResponding.set(false)
                if (_isSessionActive.value) {
                    _status.value = if (_isMicMuted.value) AssistantStatus.IDLE else AssistantStatus.LISTENING
                }
                return
            }

            // 4. Input transcription (if server sends inputTranscription)
            val inputTranscription = serverContent.optJSONObject("inputTranscription")
            if (inputTranscription != null) {
                val userTranscriptText = inputTranscription.optString("text", "").trim()
                if (userTranscriptText.isNotEmpty()) {
                    appendOrMergeSpeakerTranscript("USER", userTranscriptText)
                }
            }

            // 5. Output transcription (if server sends outputTranscription)
            val outputTranscription = serverContent.optJSONObject("outputTranscription")
            if (outputTranscription != null) {
                val aiTranscriptText = outputTranscription.optString("text", "").trim()
                if (aiTranscriptText.isNotEmpty()) {
                    appendOrMergeSpeakerTranscript("JARVIS", aiTranscriptText)
                }
            }

            // 6. Model Turn (Audio & Text chunks)
            val modelTurn = serverContent.optJSONObject("modelTurn")
            if (modelTurn != null) {
                turnWatchdogJob?.cancel()

                if (isModelCurrentlyResponding.compareAndSet(false, true)) {
                    logEvent("GEMINI_RESPONSE_STARTED", "First modelTurn packet received from Gemini", showInHud = false)
                }

                val parts = modelTurn.optJSONArray("parts")
                if (parts != null) {
                    val textBuilder = StringBuilder()
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue

                        // Ignore internal thought parts if marked thought=true
                        if (!part.optBoolean("thought", false)) {
                            val textPart = part.optString("text", "")
                            if (textPart.isNotBlank()) {
                                textBuilder.append(textPart)
                            }
                        }

                        val inlineData = part.optJSONObject("inlineData")
                        if (inlineData != null) {
                            val mimeType = inlineData.optString("mimeType", "")
                            val b64Data = inlineData.optString("data", "")
                            if (b64Data.isNotEmpty() && mimeType.startsWith("audio/pcm")) {
                                val pcmBytes = Base64.decode(b64Data, Base64.DEFAULT)
                                if (pcmBytes.isNotEmpty()) {
                                    val count = audioChunksReceivedCount.incrementAndGet()
                                    if (count == 1L || count % 25L == 0L) {
                                        logEvent(
                                            "GEMINI_AUDIO_RECEIVED",
                                            "Chunk #$count (${pcmBytes.size} bytes, $mimeType)",
                                            showInHud = false
                                        )
                                    }
                                    // Enqueue immediately without blocking WebSocket thread!
                                    pcmPlaybackChannel.trySend(pcmBytes)
                                }
                            }
                        }
                    }

                    val combinedText = textBuilder.toString().trim()
                    if (combinedText.isNotEmpty()) {
                        appendOrMergeSpeakerTranscript("JARVIS", combinedText)
                    }
                }
            }

            // 7. Turn Complete
            if (serverContent.optBoolean("turnComplete", false)) {
                turnWatchdogJob?.cancel()
                isModelCurrentlyResponding.set(false)
                logEvent(
                    "GEMINI_RESPONSE_COMPLETED",
                    "Server turnComplete received (total audio chunks=${audioChunksReceivedCount.get()})",
                    showInHud = false
                )
                // Enqueue an empty marker so playback worker transitions status to LISTENING once queued audio finishes draining
                pcmPlaybackChannel.trySend(ByteArray(0))
            }
        } catch (e: Exception) {
            logEvent("SESSION_ERROR", "JSON parse exception: ${e.localizedMessage}", showInHud = false)
        }
    }

    /**
     * Dedicated non-blocking audio playback coroutine.
     * Pulls PCM chunks from [pcmPlaybackChannel] and writes them to [AudioTrack] immediately
     * so the first chunk plays within milliseconds of arrival (`AUDIO_PLAYBACK_STARTED`)
     * while the WebSocket thread remains 100% unblocked.
     */
    private fun startNonBlockingPlaybackWorker(generation: Int) {
        playbackJob?.cancel()
        pcmPlaybackChannel.close()
        pcmPlaybackChannel = Channel(Channel.UNLIMITED)

        playbackJob = scope.launch(Dispatchers.IO) {
            for (pcmBytes in pcmPlaybackChannel) {
                if (!isActive || sessionGeneration.get() != generation || !_isSessionActive.value) {
                    break
                }

                // Empty byte array is our end-of-turn sentinel after all audio chunks in the turn are written
                if (pcmBytes.isEmpty()) {
                    isAudioPlaybackActive.set(false)
                    if (_isSessionActive.value && !isModelCurrentlyResponding.get()) {
                        _status.value = if (_isMicMuted.value) AssistantStatus.IDLE else AssistantStatus.LISTENING
                        _audioLevel.value = 0.04f
                    }
                    continue
                }

                val track = audioTrack ?: continue
                try {
                    if (isAudioPlaybackActive.compareAndSet(false, true)) {
                        logEvent(
                            "AUDIO_PLAYBACK_STARTED",
                            "Immediate streaming playback started on AudioTrack (24kHz PCM)",
                            showInHud = false
                        )
                    }
                    _status.value = AssistantStatus.SPEAKING
                    _audioLevel.value = calculateNormalizedRms(pcmBytes, pcmBytes.size).coerceAtLeast(0.28f)

                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        track.play()
                    }
                    track.write(pcmBytes, 0, pcmBytes.size, AudioTrack.WRITE_BLOCKING)
                } catch (e: Exception) {
                    logEvent("SESSION_ERROR", "AudioTrack write error: ${e.localizedMessage}", showInHud = false)
                }
            }
        }
    }

    /**
     * Real-time 16kHz 16-bit mono PCM microphone capture loop with:
     * - 40ms low-latency frames (1280 bytes)
     * - Adaptive voice activity detection (VAD) & end-of-turn silence framing (`USER_TURN_COMPLETED`)
     * - Instant client-side barge-in interruption when user speaks over AI
     * - Automatic recovery if AudioRecord ever encounters a read error
     */
    @SuppressLint("MissingPermission")
    private fun ensureMicrophoneCaptureRunning(generation: Int) {
        if (recordingJob?.isActive == true) return

        recordingJob = scope.launch(Dispatchers.IO) {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

            val minBufferSize = AudioRecord.getMinBufferSize(
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(FRAME_SIZE_BYTES * 4)

            val record = createAndStartAudioRecord(minBufferSize)
            if (record == null) {
                logEvent("SESSION_ERROR", "Microphone AudioRecord initialization failed", showInHud = true)
                _errorMessage.value = "Unable to initialize microphone hardware."
                return@launch
            }
            audioRecord = record

            val chunkBuffer = ByteArray(FRAME_SIZE_BYTES) // 40ms @ 16kHz 16-bit mono = 1280 bytes
            val digitalSilenceB64 = Base64.encodeToString(ByteArray(FRAME_SIZE_BYTES), Base64.NO_WRAP)

            // Adaptive VAD state
            var isUserCurrentlySpeaking = false
            var consecutiveSpeechFrames = 0
            var consecutiveBargeInFrames = 0
            var silenceDurationMs = 0L
            var ambientNoiseRms = 0.015f

            while (isActive && _isSessionActive.value && sessionGeneration.get() == generation) {
                val activeRecord = audioRecord ?: break
                val bytesRead = activeRecord.read(chunkBuffer, 0, chunkBuffer.size)

                if (bytesRead <= 0) {
                    // Prevent unexpected mic stop: recover AudioRecord if needed
                    delay(15)
                    if (activeRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        try {
                            activeRecord.startRecording()
                        } catch (_: Exception) {
                        }
                    }
                    continue
                }

                if (_isMicMuted.value || !wsSetupCompleted.get()) {
                    _audioLevel.value = 0f
                    isUserCurrentlySpeaking = false
                    consecutiveSpeechFrames = 0
                    silenceDurationMs = 0L
                    continue
                }

                val rawRms = calculateNormalizedRms(chunkBuffer, bytesRead)

                // Slowly adapt ambient noise floor when neither user nor AI is speaking
                if (!isUserCurrentlySpeaking && !isAudioPlaybackActive.get() && rawRms < ambientNoiseRms * 1.6f) {
                    ambientNoiseRms = (ambientNoiseRms * 0.95f + rawRms * 0.05f).coerceIn(0.008f, 0.06f)
                }

                val speechStartThreshold = (ambientNoiseRms * 2.4f).coerceAtLeast(0.032f)
                val speechContinueThreshold = (ambientNoiseRms * 1.7f).coerceAtLeast(0.022f)
                val bargeInThreshold = 0.16f // Higher threshold while AI is speaking to reject speakerphone echo

                // Check for user barge-in interruption while AI is speaking
                if (isAudioPlaybackActive.get() || _status.value == AssistantStatus.SPEAKING) {
                    if (rawRms > bargeInThreshold) {
                        consecutiveBargeInFrames++
                        if (consecutiveBargeInFrames >= 3) { // 120ms of sustained user speech interrupts AI
                            logEvent("USER_TURN_COMPLETED", "Barge-in detected: interrupting AI playback", showInHud = false)
                            flushAudioPlaybackImmediately()
                            isUserCurrentlySpeaking = true
                            consecutiveSpeechFrames = 3
                            silenceDurationMs = 0L
                            _status.value = AssistantStatus.LISTENING
                        }
                    } else {
                        consecutiveBargeInFrames = 0
                    }
                } else {
                    consecutiveBargeInFrames = 0
                }

                // Voice Activity Detection (VAD) for real-time streaming & fast end-of-turn
                if (!isUserCurrentlySpeaking) {
                    if (rawRms >= speechStartThreshold && !isAudioPlaybackActive.get()) {
                        consecutiveSpeechFrames++
                        if (consecutiveSpeechFrames >= 2) { // 80ms confirms voice start
                            isUserCurrentlySpeaking = true
                            silenceDurationMs = 0L
                            turnWatchdogJob?.cancel()
                            _status.value = AssistantStatus.LISTENING
                        }
                    } else {
                        consecutiveSpeechFrames = 0
                    }
                } else {
                    if (rawRms >= speechContinueThreshold) {
                        consecutiveSpeechFrames++
                        silenceDurationMs = 0L
                    } else {
                        silenceDurationMs += FRAME_DURATION_MS
                    }
                }

                if (!isAudioPlaybackActive.get()) {
                    _audioLevel.value = rawRms
                }

                // Decide whether to stream live mic PCM or clean digital silence frame
                if (isUserCurrentlySpeaking) {
                    val b64Audio = Base64.encodeToString(
                        if (bytesRead == chunkBuffer.size) chunkBuffer else chunkBuffer.copyOfRange(0, bytesRead),
                        Base64.NO_WRAP
                    )
                    sendRealtimeAudioChunk(b64Audio)

                    val sent = micChunksSentCount.incrementAndGet()
                    if (sent == 1L || sent % 25L == 0L) {
                        logEvent(
                            "MIC_AUDIO_SENT",
                            "Streaming live PCM chunk #$sent ($bytesRead bytes, rms=${String.format(Locale.US, "%.3f", rawRms)})",
                            showInHud = false
                        )
                    }

                    // End-of-turn detection: user spoke and has now paused for 480ms
                    if (silenceDurationMs >= END_OF_SPEECH_SILENCE_MS) {
                        isUserCurrentlySpeaking = false
                        consecutiveSpeechFrames = 0
                        silenceDurationMs = 0L
                        _status.value = AssistantStatus.THINKING
                        logEvent(
                            "USER_TURN_COMPLETED",
                            "End of speech detected after ${END_OF_SPEECH_SILENCE_MS}ms silence; awaiting Gemini response",
                            showInHud = false
                        )
                        startResponseWatchdog(generation)
                    }
                } else if (!isAudioPlaybackActive.get()) {
                    // Send clean zero-amplitude digital silence so server-side VAD immediately sees a
                    // noise-free silence boundary instead of waiting 5-10s on microphone background hiss
                    sendRealtimeAudioChunk(digitalSilenceB64)
                }
            }
        }
    }

    /**
     * If server-side VAD hasn't started responding within 1400ms after `USER_TURN_COMPLETED`,
     * sends an explicit `audioStreamEnd: true` nudge so the model responds without stalling.
     */
    private fun startResponseWatchdog(generation: Int) {
        turnWatchdogJob?.cancel()
        turnWatchdogJob = scope.launch {
            delay(1400L)
            if (isActive &&
                _isSessionActive.value &&
                sessionGeneration.get() == generation &&
                !isModelCurrentlyResponding.get() &&
                !isAudioPlaybackActive.get() &&
                _status.value == AssistantStatus.THINKING
            ) {
                val streamEndMsg = JSONObject().apply {
                    put("realtimeInput", JSONObject().put("audioStreamEnd", true))
                }
                webSocket?.send(streamEndMsg.toString())
                logEvent("USER_TURN_COMPLETED", "Sent explicit audioStreamEnd flush signal", showInHud = false)
            }
        }
    }

    private fun sendRealtimeAudioChunk(base64Pcm: String) {
        val ws = webSocket ?: return
        val realtimeInputJson = JSONObject().apply {
            put(
                "realtimeInput",
                JSONObject().apply {
                    put(
                        "mediaChunks",
                        JSONArray().put(
                            JSONObject().apply {
                                put("mimeType", "audio/pcm;rate=16000")
                                put("data", base64Pcm)
                            }
                        )
                    )
                }
            )
        }
        ws.send(realtimeInputJson.toString())
    }

    @SuppressLint("MissingPermission")
    private fun createAndStartAudioRecord(bufferSize: Int): AudioRecord? {
        return try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return null
            }
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    AcousticEchoCanceler.create(record.audioSessionId)?.enabled = true
                }
                if (NoiseSuppressor.isAvailable()) {
                    NoiseSuppressor.create(record.audioSessionId)?.enabled = true
                }
                if (AutomaticGainControl.isAvailable()) {
                    AutomaticGainControl.create(record.audioSessionId)?.enabled = true
                }
            } catch (_: Exception) {
            }
            record.startRecording()
            record
        } catch (e: Exception) {
            logEvent("SESSION_ERROR", "AudioRecord start error: ${e.localizedMessage}", showInHud = false)
            null
        }
    }

    private fun initAudioTrackPlayback() {
        try {
            audioTrack?.release()
        } catch (_: Exception) {
        }

        val minOutBuffer = AudioTrack.getMinBufferSize(
            OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4800)

        audioTrack = try {
            val builder = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(OUTPUT_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minOutBuffer)
                .setTransferMode(AudioTrack.MODE_STREAM)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            builder.build().apply { play() }
        } catch (e: Exception) {
            logEvent("SESSION_ERROR", "AudioTrack init error: ${e.localizedMessage}", showInHud = false)
            null
        }
    }

    private fun flushAudioPlaybackImmediately() {
        // Drain any queued PCM chunks in the channel
        while (pcmPlaybackChannel.tryReceive().isSuccess) {
            // discard queued chunk
        }
        isAudioPlaybackActive.set(false)
        try {
            val track = audioTrack
            if (track != null) {
                track.pause()
                track.flush()
                track.play()
            }
        } catch (_: Exception) {
        }
    }

    fun stopLiveSession() {
        userInitiatedStop.set(true)
        stopLiveSessionInternal(resetError = true)
        appendTranscript("SYSTEM", "Voice session ended by operator.")
    }

    private fun stopLiveSessionInternal(resetError: Boolean) {
        sessionGeneration.incrementAndGet()
        _isSessionActive.value = false
        _isMicMuted.value = false
        _status.value = AssistantStatus.IDLE
        _audioLevel.value = 0f
        _connectionStateLabel.value = "STANDBY // OFFLINE"
        if (resetError) {
            _errorMessage.value = null
        }

        cleanupActiveResources()
        abandonVoiceAudioFocus()
        configureAudioRouting(active = false)
    }

    private fun cleanupActiveResources() {
        sessionInitJob?.cancel()
        sessionInitJob = null
        turnWatchdogJob?.cancel()
        turnWatchdogJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        recordingJob?.cancel()
        recordingJob = null
        playbackJob?.cancel()
        playbackJob = null

        while (pcmPlaybackChannel.tryReceive().isSuccess) {
            // drain
        }

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null

        try {
            webSocket?.close(1000, "Session ended")
        } catch (_: Exception) {
        }
        webSocket = null

        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
    }

    fun toggleMuteMicrophone() {
        if (!_isSessionActive.value) return
        val newMuted = !_isMicMuted.value
        _isMicMuted.value = newMuted
        if (newMuted) {
            _audioLevel.value = 0f
            if (_status.value == AssistantStatus.LISTENING) {
                _status.value = AssistantStatus.IDLE
            }
            appendTranscript("SYSTEM", "Microphone muted.")
        } else {
            if (_status.value == AssistantStatus.IDLE) {
                _status.value = AssistantStatus.LISTENING
            }
            appendTranscript("SYSTEM", "Microphone unmuted — listening.")
        }
    }

    fun interruptAssistantPlayback() {
        turnWatchdogJob?.cancel()
        isModelCurrentlyResponding.set(false)
        flushAudioPlaybackImmediately()
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        if (_isSessionActive.value) {
            _status.value = if (_isMicMuted.value) AssistantStatus.IDLE else AssistantStatus.LISTENING
            _audioLevel.value = 0.05f
        }
    }

    fun sendLiveTextTurn(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || !_isSessionActive.value) return
        interruptAssistantPlayback()
        _status.value = AssistantStatus.THINKING
        appendTranscript("USER", clean)
        logEvent("USER_TURN_COMPLETED", "Sent live text turn over WebSocket: $clean", showInHud = false)

        val clientContentMsg = JSONObject().apply {
            put(
                "clientContent",
                JSONObject().apply {
                    put(
                        "turns",
                        JSONArray().put(
                            JSONObject().apply {
                                put("role", "user")
                                put(
                                    "parts",
                                    JSONArray().put(
                                        JSONObject().put("text", clean)
                                    )
                                )
                            }
                        )
                    )
                    put("turnComplete", true)
                }
            )
        }
        webSocket?.send(clientContentMsg.toString())
    }

    fun clearTranscripts() {
        _transcripts.value = emptyList()
    }

    private fun requestVoiceAudioFocus() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                            interruptAssistantPlayback()
                        }
                    }
                    .build()
                audioFocusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun abandonVoiceAudioFocus() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
        }
    }

    private fun configureAudioRouting(active: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (active) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val speaker = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speaker != null) {
                        audioManager.setCommunicationDevice(speaker)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = true
                }
            } else {
                audioManager.mode = AudioManager.MODE_NORMAL
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.clearCommunicationDevice()
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = false
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun initStandaloneChatTts() {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsInitialized = true
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {}
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {}
                    })
                }
            }
        } catch (_: Exception) {
        }
    }

    fun speakWithDeviceTts(text: String, language: SupportedLanguage, speed: Float, pitch: Float) {
        if (!ttsInitialized) return
        val engine = tts ?: return
        try {
            val locale = when (language) {
                SupportedLanguage.HINDI -> Locale.forLanguageTag("hi-IN")
                SupportedLanguage.HINGLISH -> Locale.forLanguageTag("en-IN")
                SupportedLanguage.ENGLISH -> Locale.US
            }
            engine.language = locale
            engine.setSpeechRate(speed.coerceIn(0.5f, 2.0f))
            engine.setPitch(pitch.coerceIn(0.5f, 2.0f))
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        } catch (_: Exception) {
        }
    }

    private fun calculateNormalizedRms(buffer: ByteArray, length: Int): Float {
        if (length < 2) return 0f
        var sumSquares = 0.0
        val sampleCount = length / 2
        var i = 0
        while (i + 1 < length) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            sumSquares += sample.toDouble() * sample.toDouble()
            i += 2
        }
        val rms = sqrt(sumSquares / sampleCount)
        return (rms / 12000.0).toFloat().coerceIn(0f, 1f)
    }

    private fun appendTranscript(speaker: String, text: String) {
        val current = _transcripts.value.toMutableList()
        current.add(LiveTranscriptItem(speaker = speaker, text = text))
        _transcripts.value = current.takeLast(60)
    }

    private fun appendOrMergeSpeakerTranscript(speaker: String, chunk: String) {
        val current = _transcripts.value.toMutableList()
        val last = current.lastOrNull()
        if (last != null && last.speaker == speaker && System.currentTimeMillis() - last.timestamp < 4500L) {
            current[current.lastIndex] = last.copy(
                text = "${last.text} $chunk".replace(Regex("\\s+"), " ").trim(),
                timestamp = System.currentTimeMillis()
            )
        } else {
            current.add(LiveTranscriptItem(speaker = speaker, text = chunk))
        }
        _transcripts.value = current.takeLast(60)
    }

    fun release() {
        userInitiatedStop.set(true)
        stopLiveSessionInternal(resetError = true)
        pcmPlaybackChannel.close()
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "JarvisLiveVoice"
        private const val INPUT_SAMPLE_RATE = 16000
        private const val OUTPUT_SAMPLE_RATE = 24000
        // 40ms frame at 16,000 Hz 16-bit mono = 640 samples = 1280 bytes
        private const val FRAME_DURATION_MS = 40L
        private const val FRAME_SIZE_BYTES = 1280
        // 480ms silence after speech triggers immediate end-of-turn digital silence framing
        private const val END_OF_SPEECH_SILENCE_MS = 480L
        private const val MAX_AUTO_RECONNECTS = 3
    }
}
