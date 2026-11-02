package com.jarvisai.assistant.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
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
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.jarvisai.assistant.data.local.MemoryEntity
import com.jarvisai.assistant.data.network.BidiModelCandidate
import com.jarvisai.assistant.data.network.GeminiApiService
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.LiveVoiceUiStateStatus
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.VoiceGender
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
    val speaker: String, // "USER", "JARVIS", "SYSTEM"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

private data class QueuedPcmFrame(
    val pcmBytes: ByteArray,
    val sampleRate: Int,
    val isEndOfTurnMarker: Boolean = false
)

/**
 * Official Gemini Multimodal Live API (`BidiGenerateContent`) real-time bidirectional
 * WebSocket audio engine.
 *
 * Root-cause fixes implemented:
 * 1. Enables `inputAudioTranscription: {}` and `outputAudioTranscription: {}` in `BidiGenerateContentSetup`
 *    so the user's spoken words (`USER_TRANSCRIPT_RECEIVED`) and Gemini's spoken words appear in real time
 *    in the Live Voice transcript UI.
 * 2. Streams continuous 16kHz 16-bit mono PCM microphone audio (`audio/pcm;rate=16000`) directly to
 *    Gemini Live API without dropping quiet speech via an aggressive client silence gate.
 * 3. Uses a multi-source `AudioRecord` initializer (`VOICE_RECOGNITION` -> `MIC` -> `VOICE_COMMUNICATION`)
 *    with software pre-gain normalization so OEM Android phones never attenuate microphone input.
 * 4. Eliminates audio playback glitching/stuttering by using a 5x `minBufferSize` jitter buffer on
 *    `AudioTrack` (`USAGE_MEDIA` loudspeaker output) and pre-buffering initial chunks before playback start.
 * 5. Synchronizes state with [LiveVoiceSessionBridge] and [LiveVoiceForegroundService] so Live Voice
 *    continues seamlessly in the background with a floating HUD control widget.
 */
class GeminiLiveVoiceManager(
    private val context: Context,
    private val geminiApiService: GeminiApiService
) {

    private val appContext = context.applicationContext
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
    private val micChunksCapturedCount = AtomicLong(0L)
    private val micChunksSentCount = AtomicLong(0L)
    private val audioChunksReceivedCount = AtomicLong(0L)

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var currentTrackSampleRate: Int = DEFAULT_OUTPUT_SAMPLE_RATE

    private var recordingJob: Job? = null
    private var playbackJob: Job? = null
    private var sessionInitJob: Job? = null
    private var turnWatchdogJob: Job? = null
    private var reconnectJob: Job? = null

    private var pcmPlaybackChannel = Channel<QueuedPcmFrame>(Channel.UNLIMITED)
    private var audioFocusRequest: AudioFocusRequest? = null

    // Accumulates the current user spoken turn transcript so we can trigger phone commands & auto-memories
    private val pendingUserTranscriptBuffer = StringBuilder()
    private var lastProcessedUserTurnText: String = ""

    /**
     * Optional callback invoked whenever a spoken user utterance finishes or a live text turn is sent.
     * Used by [JarvisViewModel] to execute spoken phone commands ("JARVIS, YouTube kholo")
     * and automatic memory saving ("My name is Sagar. Remember this.").
     */
    @Volatile
    var onUserUtteranceListener: ((String) -> Unit)? = null

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _status = MutableStateFlow(AssistantStatus.IDLE)
    val status: StateFlow<AssistantStatus> = _status.asStateFlow()

    private val _liveUiStatus = MutableStateFlow(LiveVoiceUiStateStatus.STANDBY)
    val liveUiStatus: StateFlow<LiveVoiceUiStateStatus> = _liveUiStatus.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _connectionStateLabel = MutableStateFlow("STANDBY // OFFLINE")
    val connectionStateLabel: StateFlow<String> = _connectionStateLabel.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _transcripts = MutableStateFlow<List<LiveTranscriptItem>>(emptyList())
    val transcripts: StateFlow<List<LiveTranscriptItem>> = _transcripts.asStateFlow()

    // Retained parameters for automatic reconnection
    private var lastApiKey: String = ""
    private var lastVoiceModel: String = ""
    private var lastVoiceGender: VoiceGender = VoiceGender.MALE
    private var lastVoicePersona: String = "Puck"
    private var lastAssistantName: String = "JARVIS"
    private var lastLanguage: SupportedLanguage = SupportedLanguage.HINGLISH
    private var lastMemories: List<MemoryEntity> = emptyList()
    private var reconnectAttempts = 0

    init {
        LiveVoiceSessionBridge.onToggleMuteCallback = {
            toggleMuteMicrophone()
        }
        LiveVoiceSessionBridge.onEndSessionCallback = {
            stopLiveSession()
        }
    }

    private fun updateLiveUiStatus(newStatus: LiveVoiceUiStateStatus) {
        _liveUiStatus.value = newStatus
        LiveVoiceSessionBridge.updateSessionState(
            active = _isSessionActive.value,
            muted = _isMicMuted.value,
            statusLabel = newStatus.displayLabel
        )
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
        voiceGender: VoiceGender = VoiceGender.MALE,
        voicePersona: String,
        assistantName: String,
        language: SupportedLanguage,
        voiceSpeed: Float,
        voicePitch: Float,
        memories: List<MemoryEntity>
    ) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            logEvent("LIVE_SESSION_ERROR", "Missing Gemini API key", showInHud = true)
            logEvent("SESSION_ERROR", "Missing Gemini API key", showInHud = false)
            _errorMessage.value = "Gemini API Key missing hai. Kripya pehle API Settings mein apni key save karein."
            updateLiveUiStatus(LiveVoiceUiStateStatus.CONNECTION_ERROR)
            return
        }

        val hasMicPerm = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPerm) {
            logEvent("LIVE_SESSION_ERROR", "RECORD_AUDIO permission not granted", showInHud = true)
            _errorMessage.value = "Microphone permission required hai. Kripya RECORD_AUDIO allow karein."
            updateLiveUiStatus(LiveVoiceUiStateStatus.MIC_ERROR)
            return
        }

        logEvent("MIC_PERMISSION_GRANTED", "Android RECORD_AUDIO permission verified", showInHud = false)

        val currentGen = sessionGeneration.incrementAndGet()
        userInitiatedStop.set(false)
        wsSetupCompleted.set(false)
        isModelCurrentlyResponding.set(false)
        isAudioPlaybackActive.set(false)
        micChunksCapturedCount.set(0L)
        micChunksSentCount.set(0L)
        audioChunksReceivedCount.set(0L)
        reconnectAttempts = 0
        synchronized(pendingUserTranscriptBuffer) {
            pendingUserTranscriptBuffer.clear()
        }

        cleanupActiveResources()

        val resolvedPersona = when {
            voicePersona.isNotBlank() -> voicePersona
            voiceGender == VoiceGender.FEMALE -> "Kore"
            else -> "Puck"
        }

        lastApiKey = cleanKey
        lastVoiceModel = voiceModel
        lastVoiceGender = voiceGender
        lastVoicePersona = resolvedPersona
        lastAssistantName = assistantName.ifBlank { "JARVIS" }
        lastLanguage = language
        lastMemories = memories

        _errorMessage.value = null
        _isMicMuted.value = false
        _isSessionActive.value = true
        _status.value = AssistantStatus.THINKING
        updateLiveUiStatus(LiveVoiceUiStateStatus.CONNECTING)
        _connectionStateLabel.value = "Connecting..."

        LiveVoiceForegroundService.startServiceIfNeeded(appContext)
        requestVoiceAudioFocus()
        configureAudioRouting(active = true)
        initAudioTrackPlayback(DEFAULT_OUTPUT_SAMPLE_RATE)
        startJitterBufferedPlaybackWorker(currentGen)

        sessionInitJob?.cancel()
        sessionInitJob = scope.launch {
            val candidates = resolveBidiCandidates(cleanKey, voiceModel)
            if (!isActive || sessionGeneration.get() != currentGen || !_isSessionActive.value) {
                return@launch
            }
            if (candidates.isEmpty()) {
                val errMsg = "Connection mein problem aa rahi hai: Koi Gemini Live audio model available nahi mila."
                logEvent("LIVE_SESSION_ERROR", errMsg, showInHud = true)
                _errorMessage.value = errMsg
                updateLiveUiStatus(LiveVoiceUiStateStatus.CONNECTION_ERROR)
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

        catalog.bidiCandidates.forEach { candidate ->
            if (result.none { it.modelId == candidate.modelId && it.apiVersion == candidate.apiVersion }) {
                result.add(candidate)
            }
        }

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
            val msg = "Connection mein problem aa rahi hai. Kripya apna internet aur Gemini API Key check karein."
            logEvent("LIVE_SESSION_ERROR", msg, showInHud = true)
            logEvent("SESSION_ERROR", msg, showInHud = false)
            _errorMessage.value = msg
            updateLiveUiStatus(LiveVoiceUiStateStatus.CONNECTION_ERROR)
            stopLiveSessionInternal(resetError = false)
            return
        }

        val currentCandidate = candidates[candidateIndex]
        wsSetupCompleted.set(false)
        updateLiveUiStatus(LiveVoiceUiStateStatus.CONNECTING)
        _connectionStateLabel.value = "Connecting... (${currentCandidate.modelId})"

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
                        voiceGender = lastVoiceGender,
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

                    if (!wsSetupCompleted.get() || code == 1008 || code == 1007 || code == 1003) {
                        logEvent(
                            "LIVE_SESSION_ERROR",
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
                        logEvent(
                            "LIVE_SESSION_ERROR",
                            "Live session disconnected ($code: $reason). Reconnecting...",
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
                            "LIVE_SESSION_ERROR",
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
                        logEvent("LIVE_SESSION_ERROR", "Connection drop ($detail). Reconnecting...", showInHud = true)
                        scheduleAutoReconnect(generation, candidates, candidateIndex)
                    } else {
                        logEvent("LIVE_SESSION_ERROR", "Fatal WebSocket failure: $detail", showInHud = true)
                        logEvent("SESSION_ERROR", "Fatal WebSocket failure: $detail", showInHud = false)
                        _errorMessage.value = "Connection mein problem aa rahi hai: $detail"
                        updateLiveUiStatus(LiveVoiceUiStateStatus.CONNECTION_ERROR)
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
            logEvent("LIVE_SESSION_ERROR", "Maximum reconnection attempts reached.", showInHud = true)
            _errorMessage.value = "Connection mein problem aa rahi hai. Retry limit khatam ho gayi."
            updateLiveUiStatus(LiveVoiceUiStateStatus.CONNECTION_ERROR)
            stopLiveSessionInternal(resetError = false)
            return
        }

        reconnectAttempts++
        val delayMs = 1000L * reconnectAttempts
        updateLiveUiStatus(LiveVoiceUiStateStatus.CONNECTING)
        _connectionStateLabel.value = "Connecting... (Retry $reconnectAttempts)"

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

    /**
     * Builds the official Gemini Live API `BidiGenerateContentSetup` payload.
     * Critically includes `inputAudioTranscription: {}` and `outputAudioTranscription: {}`
     * so user speech and AI audio are both transcribed in real time!
     */
    private fun buildSetupMessage(
        modelPath: String,
        voicePersona: String,
        voiceGender: VoiceGender,
        assistantName: String,
        language: SupportedLanguage,
        memories: List<MemoryEntity>
    ): JSONObject {
        val systemPrompt = buildString {
            append("You are $assistantName, an intelligent real-time voice AI assistant on Android. ")
            append("Default conversational style: natural Hinglish (Hindi + English mix like 'Namaste, main JARVIS hoon. Ji, main sun raha hoon.'). ")
            append("Understand Hindi, Hinglish, and English effortlessly. ")
            append("Respond immediately, warmly, and concisely (1 to 3 sentences unless asked for detail) like a real phone call. ")
            append("Voice profile: ${voiceGender.displayName} ($voicePersona). ")
            append(language.systemPromptDirective)
            if (memories.isNotEmpty()) {
                append("\n\n=== VERIFIED USER MEMORIES (ALWAYS USE THESE FACTS) ===\n")
                memories.take(10).forEach { mem ->
                    append("- ${mem.title}: ${mem.content}\n")
                }
                append("=======================================================\n")
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
                    // Enable real-time speech-to-text transcripts for both user mic input & AI audio output
                    put("inputAudioTranscription", JSONObject())
                    put("outputAudioTranscription", JSONObject())
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
                _connectionStateLabel.value = "Listening... (${candidate.modelId})"
                _status.value = if (_isMicMuted.value) AssistantStatus.IDLE else AssistantStatus.LISTENING
                updateLiveUiStatus(if (_isMicMuted.value) LiveVoiceUiStateStatus.STANDBY else LiveVoiceUiStateStatus.LISTENING)
                logEvent(
                    "SESSION_CONNECTED",
                    "WebSocket setupComplete on ${candidate.modelId} (${candidate.apiVersion}) — Ji, main sun raha hoon.",
                    showInHud = true
                )
                ensureMicrophoneCaptureRunning(generation)
                return
            }

            // 2. LIVE_SESSION_ERROR from server payload
            if (json.has("error")) {
                val errObj = json.optJSONObject("error")
                val code = errObj?.optInt("code", 0) ?: 0
                val msg = errObj?.optString("message") ?: "Gemini Live server error"
                logEvent("LIVE_SESSION_ERROR", "Server error ($code): $msg", showInHud = true)
                logEvent("SESSION_ERROR", "Server error ($code): $msg", showInHud = false)
                _errorMessage.value = "Connection mein problem aa rahi hai ($code): $msg"
                updateLiveUiStatus(LiveVoiceUiStateStatus.CONNECTION_ERROR)
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
                    updateLiveUiStatus(if (_isMicMuted.value) LiveVoiceUiStateStatus.STANDBY else LiveVoiceUiStateStatus.LISTENING)
                }
                return
            }

            // 4. Input transcription (User's spoken words transcribed by Gemini Live API)
            val inputTranscription = serverContent.optJSONObject("inputTranscription")
                ?: serverContent.optJSONObject("inputAudioTranscription")
            if (inputTranscription != null) {
                val userTranscriptText = inputTranscription.optString("text", "")
                if (userTranscriptText.isNotBlank()) {
                    logEvent("USER_TRANSCRIPT_RECEIVED", userTranscriptText.trim(), showInHud = false)
                    appendOrMergeSpeakerTranscript("USER", userTranscriptText.trim())
                    synchronized(pendingUserTranscriptBuffer) {
                        if (pendingUserTranscriptBuffer.isNotEmpty() && !pendingUserTranscriptBuffer.endsWith(" ")) {
                            pendingUserTranscriptBuffer.append(" ")
                        }
                        pendingUserTranscriptBuffer.append(userTranscriptText.trim())
                    }
                }
                if (inputTranscription.optBoolean("finished", false)) {
                    dispatchCompletedUserTranscriptIfNeeded()
                }
            }

            // 5. Output transcription (Gemini's spoken response transcript)
            val outputTranscription = serverContent.optJSONObject("outputTranscription")
                ?: serverContent.optJSONObject("outputAudioTranscription")
            if (outputTranscription != null) {
                val aiTranscriptText = outputTranscription.optString("text", "").trim()
                if (aiTranscriptText.isNotEmpty()) {
                    logEvent("GEMINI_RESPONSE_RECEIVED", "Transcript: $aiTranscriptText", showInHud = false)
                    appendOrMergeSpeakerTranscript("JARVIS", aiTranscriptText)
                }
            }

            // 6. Model Turn (Audio & Text chunks)
            val modelTurn = serverContent.optJSONObject("modelTurn")
            if (modelTurn != null) {
                turnWatchdogJob?.cancel()
                dispatchCompletedUserTranscriptIfNeeded()

                if (isModelCurrentlyResponding.compareAndSet(false, true)) {
                    logEvent("GEMINI_RESPONSE_RECEIVED", "First modelTurn packet received from Gemini", showInHud = false)
                    logEvent("GEMINI_RESPONSE_STARTED", "First modelTurn packet received from Gemini", showInHud = false)
                }

                val parts = modelTurn.optJSONArray("parts")
                if (parts != null) {
                    val textBuilder = StringBuilder()
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue

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
                                    val parsedRate = parseSampleRateFromMimeType(mimeType)
                                    val count = audioChunksReceivedCount.incrementAndGet()
                                    if (count == 1L || count % 20L == 0L) {
                                        logEvent(
                                            "GEMINI_AUDIO_RECEIVED",
                                            "Chunk #$count (${pcmBytes.size} bytes, rate=${parsedRate}Hz)",
                                            showInHud = false
                                        )
                                    }
                                    pcmPlaybackChannel.trySend(
                                        QueuedPcmFrame(
                                            pcmBytes = pcmBytes,
                                            sampleRate = parsedRate,
                                            isEndOfTurnMarker = false
                                        )
                                    )
                                }
                            }
                        }
                    }

                    val combinedText = textBuilder.toString().trim()
                    if (combinedText.isNotEmpty()) {
                        logEvent("GEMINI_RESPONSE_RECEIVED", combinedText, showInHud = false)
                        appendOrMergeSpeakerTranscript("JARVIS", combinedText)
                    }
                }
            }

            // 7. Turn Complete
            if (serverContent.optBoolean("turnComplete", false)) {
                turnWatchdogJob?.cancel()
                dispatchCompletedUserTranscriptIfNeeded()
                isModelCurrentlyResponding.set(false)
                logEvent(
                    "GEMINI_RESPONSE_COMPLETED",
                    "Server turnComplete received (total audio chunks=${audioChunksReceivedCount.get()})",
                    showInHud = false
                )
                pcmPlaybackChannel.trySend(
                    QueuedPcmFrame(
                        pcmBytes = ByteArray(0),
                        sampleRate = currentTrackSampleRate,
                        isEndOfTurnMarker = true
                    )
                )
            }
        } catch (e: Exception) {
            logEvent("LIVE_SESSION_ERROR", "JSON parse exception: ${e.localizedMessage}", showInHud = false)
        }
    }

    private fun dispatchCompletedUserTranscriptIfNeeded() {
        val fullTranscript = synchronized(pendingUserTranscriptBuffer) {
            val text = pendingUserTranscriptBuffer.toString().replace(Regex("\\s+"), " ").trim()
            pendingUserTranscriptBuffer.clear()
            text
        }
        if (fullTranscript.length >= 2 && fullTranscript != lastProcessedUserTurnText) {
            lastProcessedUserTurnText = fullTranscript
            logEvent("USER_TRANSCRIPT_RECEIVED", "Completed utterance: $fullTranscript", showInHud = false)
            onUserUtteranceListener?.invoke(fullTranscript)
        }
    }

    private fun parseSampleRateFromMimeType(mimeType: String): Int {
        val match = Regex("rate=(\\d+)", RegexOption.IGNORE_CASE).find(mimeType)
        val rate = match?.groupValues?.getOrNull(1)?.toIntOrNull()
        return if (rate != null && rate in 8000..48000) rate else DEFAULT_OUTPUT_SAMPLE_RATE
    }

    /**
     * Jitter-buffered non-blocking audio playback coroutine.
     * Fixes Problem 4 (AI Voice Glitching & Stuttering) by:
     * 1. Pre-buffering ~80ms of audio at the beginning of a response burst so slight WebSocket
     *    packet arrival jitter never causes an `AudioTrack` underrun click.
     * 2. Ensuring `AudioTrack` sample rate matches the incoming PCM stream (`24000 Hz`).
     * 3. Writing smooth, ordered 16-bit PCM blocks on a dedicated high-priority audio thread.
     */
    private fun startJitterBufferedPlaybackWorker(generation: Int) {
        playbackJob?.cancel()
        pcmPlaybackChannel.close()
        pcmPlaybackChannel = Channel(Channel.UNLIMITED)

        playbackJob = scope.launch(Dispatchers.IO) {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            var isFirstBurstChunk = true
            val burstPreBuffer = ArrayList<QueuedPcmFrame>(4)
            var preBufferedBytes = 0

            for (frame in pcmPlaybackChannel) {
                if (!isActive || sessionGeneration.get() != generation || !_isSessionActive.value) {
                    break
                }

                if (frame.isEndOfTurnMarker) {
                    // Flush any remaining pre-buffered frames
                    if (burstPreBuffer.isNotEmpty()) {
                        for (queued in burstPreBuffer) {
                            writeFrameToAudioTrack(queued)
                        }
                        burstPreBuffer.clear()
                        preBufferedBytes = 0
                    }
                    isFirstBurstChunk = true
                    isAudioPlaybackActive.set(false)
                    if (_isSessionActive.value && !isModelCurrentlyResponding.get()) {
                        _status.value = if (_isMicMuted.value) AssistantStatus.IDLE else AssistantStatus.LISTENING
                        updateLiveUiStatus(
                            if (_isMicMuted.value) LiveVoiceUiStateStatus.STANDBY else LiveVoiceUiStateStatus.LISTENING
                        )
                        _connectionStateLabel.value = "Listening..."
                        _audioLevel.value = 0.04f
                    }
                    continue
                }

                // Pre-buffer up to ~3840 bytes (~80ms @ 24kHz) at the start of an utterance burst
                // so playback starts smoothly without underrun clicks
                if (isFirstBurstChunk) {
                    burstPreBuffer.add(frame)
                    preBufferedBytes += frame.pcmBytes.size
                    if (preBufferedBytes < INITIAL_JITTER_PREBUFFER_BYTES && burstPreBuffer.size < 2) {
                        continue
                    }
                    isFirstBurstChunk = false
                    for (queued in burstPreBuffer) {
                        writeFrameToAudioTrack(queued)
                    }
                    burstPreBuffer.clear()
                    preBufferedBytes = 0
                } else {
                    writeFrameToAudioTrack(frame)
                }
            }
        }
    }

    private fun writeFrameToAudioTrack(frame: QueuedPcmFrame) {
        if (frame.pcmBytes.isEmpty()) return

        if (audioTrack == null || currentTrackSampleRate != frame.sampleRate) {
            initAudioTrackPlayback(frame.sampleRate)
        }
        val track = audioTrack ?: return

        try {
            if (isAudioPlaybackActive.compareAndSet(false, true)) {
                logEvent(
                    "AUDIO_PLAYBACK_STARTED",
                    "Streaming playback started on AudioTrack (${frame.sampleRate}Hz 16-bit mono PCM)",
                    showInHud = false
                )
            }
            _status.value = AssistantStatus.SPEAKING
            updateLiveUiStatus(LiveVoiceUiStateStatus.AI_SPEAKING)
            _connectionStateLabel.value = "AI Speaking..."
            _audioLevel.value = calculateNormalizedRms(frame.pcmBytes, frame.pcmBytes.size).coerceAtLeast(0.28f)

            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
            }
            track.write(frame.pcmBytes, 0, frame.pcmBytes.size, AudioTrack.WRITE_BLOCKING)
        } catch (e: Exception) {
            logEvent("LIVE_SESSION_ERROR", "AudioTrack write error: ${e.localizedMessage}", showInHud = false)
        }
    }

    /**
     * Continuous 16kHz 16-bit mono PCM microphone capture loop.
     *
     * Key fixes for Problem 1 (Live Voice Microphone Not Working):
     * - Streams EVERY captured 16kHz PCM chunk continuously to Gemini (`MIC_AUDIO_CAPTURED` ->
     *   `MIC_AUDIO_SENT_TO_GEMINI`) so Gemini's server-side VAD hears the user's natural voice
     *   without any client noise-gate cutting off words!
     * - Applies gentle software gain normalization (`1.6x` clamped) so quiet phone microphones
     *   are clearly audible to Gemini.
     * - Tracks user speech energy to display `"User Speaking..."` and `"Processing..."` in real time.
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
            ).coerceAtLeast(FRAME_SIZE_BYTES * 6)

            val record = createAndStartAudioRecord(minBufferSize)
            if (record == null) {
                val errMsg = "Microphone initialize nahi ho paya. Kripya check karein ki koi dusra app mic use toh nahi kar raha."
                logEvent("LIVE_SESSION_ERROR", errMsg, showInHud = true)
                _errorMessage.value = errMsg
                updateLiveUiStatus(LiveVoiceUiStateStatus.MIC_ERROR)
                return@launch
            }
            audioRecord = record

            val chunkBuffer = ByteArray(FRAME_SIZE_BYTES) // 40ms @ 16kHz 16-bit mono = 1280 bytes

            var isUserCurrentlySpeaking = false
            var consecutiveSpeechFrames = 0
            var consecutiveBargeInFrames = 0
            var silenceDurationMs = 0L
            var ambientNoiseRms = 0.012f

            while (isActive && _isSessionActive.value && sessionGeneration.get() == generation) {
                val activeRecord = audioRecord ?: break
                val bytesRead = activeRecord.read(chunkBuffer, 0, chunkBuffer.size)

                if (bytesRead <= 0) {
                    delay(15)
                    if (activeRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        try {
                            activeRecord.startRecording()
                        } catch (_: Exception) {
                        }
                    }
                    continue
                }

                val capturedCount = micChunksCapturedCount.incrementAndGet()
                if (capturedCount == 1L || capturedCount % 50L == 0L) {
                    logEvent("MIC_AUDIO_CAPTURED", "Captured PCM chunk #$capturedCount ($bytesRead bytes @ 16000Hz)", showInHud = false)
                }

                if (_isMicMuted.value || !wsSetupCompleted.get()) {
                    _audioLevel.value = 0f
                    isUserCurrentlySpeaking = false
                    consecutiveSpeechFrames = 0
                    silenceDurationMs = 0L
                    continue
                }

                // Apply gentle 1.6x software pre-gain so quiet device mics are crystal clear
                val gainedBytes = applyPcmGainInPlace(
                    source = chunkBuffer,
                    bytesRead = bytesRead,
                    gainFactor = if (isAudioPlaybackActive.get()) 1.0f else 1.6f
                )

                val rawRms = calculateNormalizedRms(gainedBytes, bytesRead)

                if (!isUserCurrentlySpeaking && !isAudioPlaybackActive.get() && rawRms < ambientNoiseRms * 1.5f) {
                    ambientNoiseRms = (ambientNoiseRms * 0.95f + rawRms * 0.05f).coerceIn(0.005f, 0.045f)
                }

                val speechStartThreshold = (ambientNoiseRms * 2.1f).coerceAtLeast(0.022f)
                val speechContinueThreshold = (ambientNoiseRms * 1.5f).coerceAtLeast(0.015f)
                val bargeInThreshold = 0.20f

                // Barge-in interruption when user speaks loudly over AI
                if (isAudioPlaybackActive.get() || _status.value == AssistantStatus.SPEAKING) {
                    if (rawRms > bargeInThreshold) {
                        consecutiveBargeInFrames++
                        if (consecutiveBargeInFrames >= 4) {
                            logEvent("USER_TURN_COMPLETED", "Barge-in detected: interrupting AI playback", showInHud = false)
                            flushAudioPlaybackImmediately()
                            isUserCurrentlySpeaking = true
                            consecutiveSpeechFrames = 4
                            silenceDurationMs = 0L
                            _status.value = AssistantStatus.LISTENING
                            updateLiveUiStatus(LiveVoiceUiStateStatus.USER_SPEAKING)
                            _connectionStateLabel.value = "User Speaking..."
                        }
                    } else {
                        consecutiveBargeInFrames = 0
                    }
                } else {
                    consecutiveBargeInFrames = 0
                }

                // Update UI status indicators based on speech activity
                if (!isUserCurrentlySpeaking) {
                    if (rawRms >= speechStartThreshold && !isAudioPlaybackActive.get()) {
                        consecutiveSpeechFrames++
                        if (consecutiveSpeechFrames >= 2) {
                            isUserCurrentlySpeaking = true
                            silenceDurationMs = 0L
                            turnWatchdogJob?.cancel()
                            _status.value = AssistantStatus.LISTENING
                            updateLiveUiStatus(LiveVoiceUiStateStatus.USER_SPEAKING)
                            _connectionStateLabel.value = "User Speaking..."
                        }
                    } else {
                        consecutiveSpeechFrames = 0
                    }
                } else {
                    if (rawRms >= speechContinueThreshold) {
                        consecutiveSpeechFrames++
                        silenceDurationMs = 0L
                        if (!isAudioPlaybackActive.get()) {
                            updateLiveUiStatus(LiveVoiceUiStateStatus.USER_SPEAKING)
                            _connectionStateLabel.value = "User Speaking..."
                        }
                    } else {
                        silenceDurationMs += FRAME_DURATION_MS
                        if (silenceDurationMs >= END_OF_SPEECH_SILENCE_MS) {
                            isUserCurrentlySpeaking = false
                            consecutiveSpeechFrames = 0
                            silenceDurationMs = 0L
                            _status.value = AssistantStatus.THINKING
                            updateLiveUiStatus(LiveVoiceUiStateStatus.PROCESSING)
                            _connectionStateLabel.value = "Processing..."
                            logEvent(
                                "USER_TURN_COMPLETED",
                                "User finished speaking (${END_OF_SPEECH_SILENCE_MS}ms pause); awaiting Gemini response",
                                showInHud = false
                            )
                            startResponseWatchdog(generation)
                        }
                    }
                }

                if (!isAudioPlaybackActive.get()) {
                    _audioLevel.value = rawRms
                }

                // CRITICAL: Always stream the live 16kHz PCM chunk to Gemini while not playing AI audio
                // (or while user is barging in) so Gemini's server-side VAD receives the complete waveform!
                if (!isAudioPlaybackActive.get() || isUserCurrentlySpeaking) {
                    val b64Audio = Base64.encodeToString(gainedBytes, Base64.NO_WRAP)
                    sendRealtimeAudioChunk(b64Audio)

                    val sent = micChunksSentCount.incrementAndGet()
                    if (sent == 1L || sent % 40L == 0L) {
                        logEvent(
                            "MIC_AUDIO_SENT_TO_GEMINI",
                            "Sent live PCM chunk #$sent ($bytesRead bytes, rms=${String.format(Locale.US, "%.3f", rawRms)})",
                            showInHud = false
                        )
                        logEvent(
                            "MIC_AUDIO_SENT",
                            "Sent live PCM chunk #$sent ($bytesRead bytes)",
                            showInHud = false
                        )
                    }
                }
            }
        }
    }

    private fun applyPcmGainInPlace(source: ByteArray, bytesRead: Int, gainFactor: Float): ByteArray {
        val out = ByteArray(bytesRead)
        if (gainFactor == 1.0f) {
            System.arraycopy(source, 0, out, 0, bytesRead)
            return out
        }
        var i = 0
        while (i + 1 < bytesRead) {
            val sample = ((source[i + 1].toInt() shl 8) or (source[i].toInt() and 0xFF)).toShort()
            val boosted = (sample * gainFactor).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            out[i] = (boosted.toInt() and 0xFF).toByte()
            out[i + 1] = ((boosted.toInt() shr 8) and 0xFF).toByte()
            i += 2
        }
        return out
    }

    private fun startResponseWatchdog(generation: Int) {
        turnWatchdogJob?.cancel()
        turnWatchdogJob = scope.launch {
            delay(1300L)
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
        val chunkObj = JSONObject().apply {
            put("mimeType", "audio/pcm;rate=16000")
            put("data", base64Pcm)
        }
        val realtimeInputJson = JSONObject().apply {
            put(
                "realtimeInput",
                JSONObject().apply {
                    put("mediaChunks", JSONArray().put(chunkObj))
                }
            )
        }
        ws.send(realtimeInputJson.toString())
    }

    /**
     * Tries multiple Android audio sources (`VOICE_RECOGNITION`, `MIC`, `VOICE_COMMUNICATION`)
     * so devices that attenuate `VOICE_COMMUNICATION` still capture full-volume microphone audio.
     */
    @SuppressLint("MissingPermission")
    private fun createAndStartAudioRecord(bufferSize: Int): AudioRecord? {
        val sourcesToTry = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
            MediaRecorder.AudioSource.MIC to "MIC",
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION"
        )

        for ((sourceConst, sourceName) in sourcesToTry) {
            try {
                val record = AudioRecord(
                    sourceConst,
                    INPUT_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    continue
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
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    logEvent(
                        "MICROPHONE_INITIALIZED",
                        "AudioRecord initialized with source=$sourceName, rate=${INPUT_SAMPLE_RATE}Hz, buffer=$bufferSize bytes",
                        showInHud = false
                    )
                    return record
                } else {
                    record.release()
                }
            } catch (e: Exception) {
                logEvent("LIVE_SESSION_ERROR", "AudioRecord source $sourceName failed: ${e.localizedMessage}", showInHud = false)
            }
        }
        return null
    }

    /**
     * Initializes `AudioTrack` with a 5x `minBufferSize` jitter buffer and `USAGE_MEDIA`
     * so AI speech plays smoothly through the main speaker without buffer underruns or glitches.
     */
    private fun initAudioTrackPlayback(sampleRate: Int) {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Exception) {
        }

        currentTrackSampleRate = sampleRate
        val minOutBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4800)

        // 5x minBufferSize (~28,800 bytes = 600ms ring buffer at 24kHz) prevents underrun glitches
        val jitterSafeBufferSize = (minOutBuffer * 5).coerceAtLeast(28800)

        audioTrack = try {
            AudioTrack.Builder()
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
                .setBufferSizeInBytes(jitterSafeBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .apply { play() }
        } catch (e: Exception) {
            logEvent("LIVE_SESSION_ERROR", "AudioTrack init error: ${e.localizedMessage}", showInHud = false)
            null
        }
    }

    private fun flushAudioPlaybackImmediately() {
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
        appendTranscript("SYSTEM", "Live Voice call समाप्त (ended).")
    }

    private fun stopLiveSessionInternal(resetError: Boolean) {
        sessionGeneration.incrementAndGet()
        _isSessionActive.value = false
        _isMicMuted.value = false
        _status.value = AssistantStatus.IDLE
        _audioLevel.value = 0f
        _connectionStateLabel.value = "Standby"
        if (resetError) {
            _errorMessage.value = null
            updateLiveUiStatus(LiveVoiceUiStateStatus.STANDBY)
        } else {
            LiveVoiceSessionBridge.updateSessionState(false, false, "Standby")
        }

        LiveVoiceForegroundService.stopServiceIfRunning(appContext)
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
            updateLiveUiStatus(LiveVoiceUiStateStatus.STANDBY)
            _connectionStateLabel.value = "Mic Muted"
            appendTranscript("SYSTEM", "Microphone mute kar diya gaya hai.")
        } else {
            if (_status.value == AssistantStatus.IDLE) {
                _status.value = AssistantStatus.LISTENING
            }
            updateLiveUiStatus(LiveVoiceUiStateStatus.LISTENING)
            _connectionStateLabel.value = "Listening..."
            appendTranscript("SYSTEM", "Microphone unmute ho gaya — Ji, main sun raha hoon.")
        }
    }

    fun interruptAssistantPlayback() {
        turnWatchdogJob?.cancel()
        isModelCurrentlyResponding.set(false)
        flushAudioPlaybackImmediately()
        if (_isSessionActive.value) {
            _status.value = if (_isMicMuted.value) AssistantStatus.IDLE else AssistantStatus.LISTENING
            updateLiveUiStatus(if (_isMicMuted.value) LiveVoiceUiStateStatus.STANDBY else LiveVoiceUiStateStatus.LISTENING)
            _connectionStateLabel.value = if (_isMicMuted.value) "Mic Muted" else "Listening..."
            _audioLevel.value = 0.05f
        }
    }

    fun sendLiveTextTurn(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || !_isSessionActive.value) return
        interruptAssistantPlayback()
        _status.value = AssistantStatus.THINKING
        updateLiveUiStatus(LiveVoiceUiStateStatus.PROCESSING)
        _connectionStateLabel.value = "Processing..."
        appendTranscript("USER", clean)
        logEvent("USER_TRANSCRIPT_RECEIVED", clean, showInHud = false)
        logEvent("USER_TURN_COMPLETED", "Sent live text turn over WebSocket: $clean", showInHud = false)

        onUserUtteranceListener?.invoke(clean)

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

    fun appendSystemNotice(notice: String) {
        appendTranscript("SYSTEM", notice)
    }

    fun clearTranscripts() {
        _transcripts.value = emptyList()
    }

    private fun requestVoiceAudioFocus() {
        try {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
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
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun abandonVoiceAudioFocus() {
        try {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
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
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (active) {
                // Keep MODE_NORMAL so MediaRecorder.AudioSource.VOICE_RECOGNITION/MIC and USAGE_MEDIA
                // use the phone's full-bandwidth main microphone and loudspeaker without OEM attenuation.
                audioManager.mode = AudioManager.MODE_NORMAL
            }
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
        return (rms / 10000.0).toFloat().coerceIn(0f, 1f)
    }

    private fun appendTranscript(speaker: String, text: String) {
        val current = _transcripts.value.toMutableList()
        current.add(LiveTranscriptItem(speaker = speaker, text = text))
        _transcripts.value = current.takeLast(80)
    }

    private fun appendOrMergeSpeakerTranscript(speaker: String, chunk: String) {
        val current = _transcripts.value.toMutableList()
        val last = current.lastOrNull()
        if (last != null && last.speaker == speaker && System.currentTimeMillis() - last.timestamp < 5000L) {
            val merged = if (last.text.endsWith(chunk)) {
                last.text
            } else {
                "${last.text} $chunk".replace(Regex("\\s+"), " ").trim()
            }
            current[current.lastIndex] = last.copy(
                text = merged,
                timestamp = System.currentTimeMillis()
            )
        } else {
            current.add(LiveTranscriptItem(speaker = speaker, text = chunk))
        }
        _transcripts.value = current.takeLast(80)
    }

    fun release() {
        userInitiatedStop.set(true)
        stopLiveSessionInternal(resetError = true)
        pcmPlaybackChannel.close()
    }

    companion object {
        private const val TAG = "JarvisLiveVoice"
        private const val INPUT_SAMPLE_RATE = 16000
        private const val DEFAULT_OUTPUT_SAMPLE_RATE = 24000
        // 40ms frame at 16,000 Hz 16-bit mono = 640 samples = 1280 bytes
        private const val FRAME_DURATION_MS = 40L
        private const val FRAME_SIZE_BYTES = 1280
        // ~80ms initial pre-buffer at 24kHz 16-bit mono = 3840 bytes to eliminate network jitter underruns
        private const val INITIAL_JITTER_PREBUFFER_BYTES = 3840
        private const val END_OF_SPEECH_SILENCE_MS = 520L
        private const val MAX_AUTO_RECONNECTS = 3
    }
}
