package com.jarvisai.assistant.voice.agy

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
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
import android.os.SystemClock
import android.util.Log
import com.jarvisai.assistant.terminal.LocalServerManager
import exa.language_server_pb.Model
import exa.remote_agent_proxy_pb.VoiceClientMessage
import exa.remote_agent_proxy_pb.VoiceServerMessage
import exa.remote_agent_proxy_pb.VoiceServerMessage.SessionStatus.FailureReason
import exa.remote_agent_proxy_pb.VoiceServerMessage.SessionStatus.State as LinkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Antigravity native live voice call (`ws://<hub>/voice/ws`, `jetski-voice-proto`):
 * streams 16 kHz mono PCM mic audio up and plays 24 kHz mono PCM voice responses down,
 * while Google's voice agent drives the linked Antigravity chat cascade in the background.
 */
object AgyVoiceCall {
    private const val TAG = "AgyVoiceCall"
    private const val MIC_RATE = 16_000
    private const val MIC_CHUNK_SAMPLES = 640 // 40 ms frames
    private const val VOICE_RATE = 24_000
    private const val PLAY_SLICE_BYTES = 960 // 20 ms slice for instant barge-in cut
    private const val PROTOCOL = "jetski-voice-proto"
    private const val MAX_LINK_RETRIES = 3
    private const val LOG_MAX = 600

    enum class Phase { IDLE, CONNECTING, LIVE, RECONNECTING, ENDING, FAILED }
    enum class Role { USER, AGENT }
    enum class Problem { NONE, UPDATE_AGY, SIGN_IN, MIC, OTHER }

    data class Line(
        val key: Long,
        val role: Role,
        val seq: Int,
        val text: String,
        val interim: Boolean
    )

    data class State(
        val phase: Phase = Phase.IDLE,
        val conversationId: String? = null,
        val title: String = "",
        val voice: String = "Fola",
        val language: String = AgyVoicePreferences.LANGUAGE_AUTO,
        val connectedAt: Long = 0L,
        val startedAtWall: Long = 0L,
        val muted: Boolean = false,
        val speaker: Boolean = true,
        val agentSpeaking: Boolean = false,
        val userSpeaking: Boolean = false,
        val attempt: Int = 0,
        val maxAttempts: Int = 0,
        val statusText: String = "",
        val error: String? = null,
        val problem: Problem = Problem.NONE,
        val lines: List<Line> = emptyList(),
        val minimized: Boolean = false,
        val log: List<String> = emptyList()
    ) {
        val active: Boolean get() = phase != Phase.IDLE
        val inCall: Boolean get() = phase == Phase.CONNECTING || phase == Phase.LIVE || phase == Phase.RECONNECTING
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _voiceLevel = MutableStateFlow(0f)
    val voiceLevel: StateFlow<Float> = _voiceLevel.asStateFlow()

    private val _conversationMoved = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val conversationMoved: SharedFlow<String> = _conversationMoved.asSharedFlow()

    /** Hooks wired by ViewModel/App to avoid circular service dependencies (e.g. pause wake word). */
    @Volatile var onCallLifecycleChanged: ((active: Boolean) -> Unit)? = null

    private val lineKeys = AtomicLong(0)

    @Volatile private var session: Session? = null
    private var lastStart: Pair<String, suspend () -> Pair<String, Int>>? = null
    private var carryLog: List<String> = emptyList()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
    private val healthClient: OkHttpClient by lazy {
        client.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()
    }

    class CallRefused(message: String) : Exception(message)

    const val CLOUD_CHAT_NO_VOICE =
        "Voice calls only work on local or desktop Antigravity cascades (not cloud-only sessions)."

    fun start(context: Context, title: String, prepare: suspend () -> Pair<String, Int>) {
        val cur = _state.value
        if (cur.inCall || cur.phase == Phase.ENDING) {
            _state.update { it.copy(minimized = false) }
            return
        }
        val app = context.applicationContext
        AgyVoicePreferences.init(app)
        session?.let { teardown(it) }
        lastStart = title to prepare
        val voice = AgyVoicePreferences.voice.value
        val language = AgyVoicePreferences.language.value
        val s = Session(app, voice)
        s.language = language
        session = s
        _micLevel.value = 0f
        _voiceLevel.value = 0f
        _state.value = State(
            phase = Phase.CONNECTING,
            title = title,
            voice = voice,
            language = language,
            speaker = AgyVoicePreferences.speaker.value && !AudioRoute.hasHeadset(app),
            statusText = "Getting the chat ready…",
            startedAtWall = System.currentTimeMillis(),
            log = carryLog
        )
        carryLog = emptyList()
        runCatching { onCallLifecycleChanged?.invoke(true) }
        runCatching { AgyVoiceCallService.start(app) }.onFailure { Log.w(TAG, "foreground service: ${it.message}") }

        s.job = scope.launch {
            val (cid, model) = try {
                prepare()
            } catch (e: CallRefused) {
                fail(s, e.message.orEmpty())
                return@launch
            } catch (e: Exception) {
                s.lastFailure = e
                fail(s, "Couldn't start the chat: ${e.message ?: e.javaClass.simpleName}")
                return@launch
            }
            if (session !== s) return@launch
            s.conversationId = cid
            s.planModel = model
            _state.update { it.copy(conversationId = cid, statusText = "Checking Antigravity hub…") }

            var base = AgyVoiceHubResolver.currentHubBaseUrl(app)
            if (!AgyVoiceHubResolver.isRemoteHub(app)) {
                LocalServerManager.ensureAgyHubAvailable(app)
                base = AgyVoiceHubResolver.currentHubBaseUrl(app)
            }
            AgyVoicePreferences.rememberCallChat(base, cid)

            when (val h = checkHealth(s, base)) {
                is Health.Missing -> {
                    val remote = AgyVoiceHubResolver.isRemoteHub(app)
                    fail(
                        s,
                        if (remote) "This hub doesn't support voice calls yet (requires Antigravity / agy 1.5+)."
                        else "Voice calls need agy 1.5 or newer. Update Antigravity CLI in Settings.",
                        Problem.UPDATE_AGY
                    )
                    return@launch
                }
                is Health.Mismatch404 -> {
                    fail(
                        s,
                        "Antigravity hub at ${h.url} returned HTTP 404 (installed agy is ${h.installedVersion}). Tap Details or restart Local Server.",
                        Problem.OTHER
                    )
                    return@launch
                }
                is Health.Unreachable -> {
                    fail(s, "Can't reach the Antigravity hub (${h.message}). Make sure agy --hub is running.")
                    return@launch
                }
                is Health.Other -> {
                    fail(s, "Antigravity hub health check failed (HTTP ${h.code}). Make sure agy --hub is running.")
                    return@launch
                }
                else -> Unit
            }
            if (session !== s) return@launch

            s.route.begin(_state.value.speaker)
            s.player.start()
            s.startPulses()
            _state.update { it.copy(statusText = "Connecting to the voice agent…") }
            openSocket(s)
        }
    }

    fun retry(context: Context) {
        val (title, prepare) = lastStart ?: return
        carryLog = (_state.value.log + "── Retrying call ──").takeLast(LOG_MAX)
        _state.value = State()
        start(context, title, prepare)
    }

    fun end() {
        val s = session
        if (s == null) {
            _state.value = State()
            runCatching { onCallLifecycleChanged?.invoke(false) }
            return
        }
        if (_state.value.phase == Phase.FAILED) {
            teardown(s)
            _state.value = State()
            return
        }
        s.endRequested = true
        _state.update {
            it.copy(
                phase = Phase.ENDING,
                statusText = "Ending…",
                agentSpeaking = false,
                userSpeaking = false
            )
        }
        s.micRunning = false
        s.player.clear()
        s.player.enqueue(Chimes.end)
        runCatching { s.ws?.close(1000, "bye") }
        scope.launch {
            delay(600)
            if (session === s) {
                teardown(s)
                _state.value = State()
            }
        }
    }

    fun changeVoice(name: String) {
        val s = session ?: return
        switchTo(name, s.language)
    }

    fun switchTo(voice: String, language: String) {
        val s = session ?: return
        val st = _state.value
        if (!st.inCall || (voice == s.voice && language == s.language)) return
        val what = listOfNotNull(
            voice.takeIf { it != s.voice },
            AgyVoicePreferences.languageLabel(language).takeIf { language != s.language }
        ).joinToString(", ")
        s.voice = voice
        s.language = language
        val old = s.ws
        if (old == null) {
            _state.update { it.copy(voice = voice, language = language) }
            return
        }
        note(s, "Switching to $what…")
        _state.update {
            it.copy(
                voice = voice,
                language = language,
                phase = Phase.RECONNECTING,
                statusText = "Switching to $what…",
                attempt = 0,
                maxAttempts = 0,
                agentSpeaking = false
            )
        }
        s.player.clear()
        s.socketOpen = false
        openSocket(s)
        runCatching { old.close(1000, "voice change") }
    }

    fun toggleMute() = _state.update { it.copy(muted = !it.muted) }

    fun toggleSpeaker() {
        val on = !_state.value.speaker
        _state.update { it.copy(speaker = on) }
        AgyVoicePreferences.setSpeaker(on)
        session?.route?.route(on)
    }

    fun setMinimized(minimized: Boolean) = _state.update { it.copy(minimized = minimized) }

    fun retitle(title: String) {
        if (title.isBlank()) return
        _state.update { if (it.active) it.copy(title = title) else it }
    }

    fun setSpeed(speed: Double) {
        AgyVoicePreferences.setSpeed(speed)
        session?.send(
            VoiceClientMessage(
                settings_update = VoiceClientMessage.SettingsUpdate(playback_speed = speed)
            )
        )
    }

    fun setPlanModel(model: Int) {
        val s = session ?: return
        if (model == 0 || model == s.planModel) return
        s.planModel = model
        val m = Model.fromValue(model) ?: return
        s.send(
            VoiceClientMessage(
                settings_update = VoiceClientMessage.SettingsUpdate(plan_model = m)
            )
        )
    }

    private fun note(s: Session, text: String) {
        val sec = (SystemClock.elapsedRealtime() - s.startedAt) / 1000
        val line = "%02d:%02d  %s".format(sec / 60, sec % 60, text)
        Log.i(TAG, text)
        _state.update { it.copy(log = (it.log + line).takeLast(LOG_MAX)) }
    }

    private sealed class Health {
        data object Ready : Health()
        data object Missing : Health()
        data class Mismatch404(val installedVersion: String, val url: String, val body: String) : Health()
        data class Unreachable(val message: String) : Health()
        data class Other(val code: Int) : Health()
    }

    internal fun shouldSuggestCliUpdateOnHealth404(
        isRemoteHub: Boolean,
        installedVersion: String
    ): Boolean {
        if (isRemoteHub) return true
        val ver = installedVersion.trim()
        if (ver.isBlank()) return true
        return com.jarvisai.assistant.terminal.AgyCliManager.compareSemanticVersions(ver, "1.5.0") < 0
    }

    private fun probeVoiceHealthOnce(app: Context, url: okhttp3.HttpUrl): Result<Pair<Int, String>> = runCatching {
        val req = AgyVoiceHubResolver.attachHubAuthHeaders(Request.Builder().url(url).get(), url, app).build()
        healthClient.newCall(req).execute().use { r ->
            val body = runCatching { r.body?.string()?.trim()?.take(160) }.getOrNull().orEmpty()
            r.code to body
        }
    }

    private suspend fun checkHealth(s: Session, base: String): Health = withContext(Dispatchers.IO) {
        val app = s.app
        val url = "$base/voice/health".toHttpUrlOrNull()
            ?: return@withContext Health.Unreachable("invalid URL")
        var restartedLocalHub = false
        repeat(15) { attempt ->
            val res = probeVoiceHealthOnce(app, url)
            val (code, body) = res.getOrElse { e ->
                return@withContext Health.Unreachable(e.message ?: e.javaClass.simpleName)
            }
            when (code) {
                200 -> return@withContext Health.Ready
                404 -> {
                    val remote = AgyVoiceHubResolver.isRemoteHub(app)
                    val cliInfo = runCatching {
                        com.jarvisai.assistant.terminal.AgyCliManager.refreshCliInfo(app, checkRemoteManifest = false)
                    }.getOrNull()
                    val installedVer = cliInfo?.version.orEmpty().ifBlank {
                        LocalServerManager.agyDiagnostics.value.agyVersion
                    }
                    val detail = "GET $url -> HTTP 404${if (body.isNotBlank()) " ($body)" else ""}"
                    if (shouldSuggestCliUpdateOnHealth404(remote, installedVer)) {
                        s.handshake = detail
                        note(s, "Hub health check failed: $detail")
                        return@withContext Health.Missing
                    }
                    // Local agy is >= 1.5, so 404 means the listener on hubPort hasn't mounted voice yet
                    // or a stale process holds the port. Retry briefly, then perform one clean restart.
                    if (attempt < 2) {
                        delay(500)
                    } else if (!restartedLocalHub) {
                        restartedLocalHub = true
                        note(s, "Hub returned HTTP 404 with agy $installedVer installed; restarting local hub…")
                        val restartedCode = LocalServerManager.restartAgyHubAndWaitForVoice(app)
                        if (restartedCode == 200) return@withContext Health.Ready
                    } else {
                        s.handshake = detail
                        note(s, "Hub health check failed even with agy $installedVer: $detail")
                        return@withContext Health.Mismatch404(installedVer, url.toString(), body)
                    }
                }
                503 -> delay(1000)
                else -> {
                    val detail = "GET $url -> HTTP $code${if (body.isNotBlank()) " ($body)" else ""}"
                    s.handshake = detail
                    note(s, "Hub health check returned HTTP $code")
                    return@withContext Health.Other(code)
                }
            }
        }
        Health.Other(503)
    }

    private fun openSocket(s: Session) {
        val base = AgyVoiceHubResolver.currentHubBaseUrl(s.app)
        val url = "$base/voice/ws".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("conversation_id", s.conversationId)
            ?.addQueryParameter("plan_model", s.planModel.toString())
            ?.addQueryParameter("sample_rate", MIC_RATE.toString())
            ?.addQueryParameter("voice", s.voice)
            ?.build()
        if (url == null) {
            fail(s, "The hub address is not valid ($base)")
            return
        }
        val reqBuilder = Request.Builder().url(url)
            .header("Sec-WebSocket-Protocol", PROTOCOL)
            .header("Accept-Language", AgyVoicePreferences.acceptLanguage(s.language))
        AgyVoiceHubResolver.attachHubAuthHeaders(reqBuilder, url, s.app)
        s.ws = client.newWebSocket(reqBuilder.build(), Listener(s))
    }

    private class Listener(val s: Session) : WebSocketListener() {
        private fun current(ws: WebSocket) = session === s && s.ws === ws

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!current(webSocket)) return
            s.linkRetries = 0
            s.socketOpen = true
            s.openedAt = SystemClock.elapsedRealtime()
            note(s, "Connected to hub (voice=${s.voice}, lang=${AgyVoicePreferences.acceptLanguage(s.language)})")
            val speed = AgyVoicePreferences.speed.value
            if (speed != 1.0) {
                s.send(
                    VoiceClientMessage(
                        settings_update = VoiceClientMessage.SettingsUpdate(playback_speed = speed)
                    )
                )
            }
            if (!s.micStarted) s.startMic()
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (!current(webSocket)) return
            val m = try {
                VoiceServerMessage.ADAPTER.decode(bytes)
            } catch (e: Exception) {
                Log.w(TAG, "bad frame: ${e.message}")
                return
            }
            s.framesIn++
            s.lastFrame = frameKind(m)
            onServerMessage(s, m)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (reason.isNotBlank() && code != 1000) s.closeReason = reason
            runCatching { webSocket.close(1000, null) }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!current(webSocket)) return
            s.socketOpen = false
            s.closeCode = code
            val why = (s.closeReason ?: reason).takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
            note(s, "The hub closed the call ($code$why)")
            socketEnded(s, s.closeReason ?: reason.takeIf { code != 1000 && it.isNotBlank() })
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!current(webSocket)) return
            s.socketOpen = false
            Log.w(TAG, "voice link failed", t)
            note(s, "Link failed: ${response?.code?.let { "HTTP $it, " } ?: ""}${t.javaClass.name}${t.message?.let { ": $it" } ?: ""}")
            val code = response?.code
            val body = runCatching { response?.body?.string()?.trim()?.take(200) }.getOrNull().orEmpty()
            s.lastFailure = t
            s.handshake = response?.let { r ->
                "HTTP ${r.code} ${r.message}".trim() + (if (body.isNotBlank()) ", body: $body" else "")
            }
            when {
                code != null && code in 400..599 && code != 101 ->
                    fail(s, "The hub refused the call (HTTP $code${if (body.isNotBlank()) ": $body" else ""})")
                else -> socketEnded(s, t.message ?: t.javaClass.simpleName, network = true)
            }
        }
    }

    private fun socketEnded(s: Session, reason: String?, network: Boolean = false) {
        if (session !== s || s.endRequested) return
        val st = _state.value
        if (st.phase == Phase.FAILED) return
        if (s.serverStopping) {
            s.player.enqueue(Chimes.end)
            scope.launch {
                delay(600)
                if (session === s) {
                    teardown(s)
                    _state.value = State()
                }
            }
            return
        }
        if (network && st.connectedAt > 0 && s.linkRetries < MAX_LINK_RETRIES) {
            s.linkRetries++
            val attempt = s.linkRetries
            _state.update {
                it.copy(
                    phase = Phase.RECONNECTING,
                    attempt = attempt,
                    maxAttempts = MAX_LINK_RETRIES,
                    statusText = "Reconnecting…",
                    agentSpeaking = false
                )
            }
            s.player.clear()
            s.startPulses()
            scope.launch {
                delay(1000L shl (attempt - 1))
                if (session === s && !s.endRequested) openSocket(s)
            }
            return
        }
        fail(
            s,
            if (st.connectedAt > 0) "Call disconnected${reason?.let { ": $it" } ?: ""}"
            else "Couldn't connect to the voice agent${reason?.let { ": $it" } ?: ""}"
        )
    }

    private fun onServerMessage(s: Session, m: VoiceServerMessage) {
        when {
            m.voice_audio != null -> {
                s.voiceStarted = true
                s.player.enqueue(m.voice_audio.toByteArray())
            }
            m.clear_audio != null -> {
                if (_state.value.agentSpeaking) note(s, "Voice stopped — you talked over")
                s.player.clear()
            }
            m.user_speech != null -> {
                if (!m.user_speech.interim && m.user_speech.text.isNotBlank()) {
                    note(s, "Heard you: \"${m.user_speech.text.trim().take(80)}\"")
                }
                onUserSpeech(m.user_speech)
            }
            m.voice_transcript != null -> {
                if (SystemClock.elapsedRealtime() - s.lastAgentChunkAt > 1500) {
                    note(s, "The voice answers")
                }
                onVoiceTranscript(s, m.voice_transcript)
            }
            m.status != null -> onStatus(s, m.status)
            m.event != null -> {
                val attrs = m.event.attributes.entries.joinToString(", ") { "${it.key}=${it.value.take(60)}" }
                note(s, "Event ${m.event.event_name}${if (attrs.isNotEmpty()) " ($attrs)" else ""}")
            }
        }
    }

    private fun onStatus(s: Session, st: VoiceServerMessage.SessionStatus) {
        st.cascade_id.takeIf { it.isNotBlank() && it != s.conversationId }?.let { id ->
            s.conversationId = id
            _state.update { it.copy(conversationId = id) }
            _conversationMoved.tryEmit(id)
            AgyVoicePreferences.rememberCallChat(AgyVoiceHubResolver.currentHubBaseUrl(s.app), id)
            note(s, "Voice agent moved call to chat $id")
        }
        val stateName = when (st.state) {
            LinkState.STARTING -> "starting"
            LinkState.LISTENING -> "listening"
            LinkState.RETRYING -> "reconnecting (${st.attempt}/${st.max_attempts})"
            LinkState.STOPPING -> "stopping"
            LinkState.FAILED -> "failed (${st.failure_reason.name.lowercase()})"
            else -> st.state.name.lowercase()
        }
        note(s, "Voice agent: $stateName${st.message.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}")
        when (st.state) {
            LinkState.STARTING -> _state.update {
                if (it.connectedAt > 0) it else it.copy(phase = Phase.CONNECTING, statusText = "Starting the voice agent…")
            }
            LinkState.LISTENING -> {
                val first = _state.value.connectedAt == 0L
                _state.update {
                    it.copy(
                        phase = Phase.LIVE,
                        connectedAt = if (it.connectedAt == 0L) SystemClock.elapsedRealtime() else it.connectedAt,
                        attempt = 0,
                        maxAttempts = 0,
                        statusText = "",
                        error = null
                    )
                }
                s.stopPulses()
                if (first) s.player.enqueue(Chimes.connected)
            }
            LinkState.RETRYING -> {
                _state.update {
                    it.copy(
                        phase = Phase.RECONNECTING,
                        attempt = st.attempt,
                        maxAttempts = st.max_attempts,
                        statusText = st.message.ifBlank { "Reconnecting…" }
                    )
                }
                s.startPulses()
            }
            LinkState.STOPPING -> {
                s.serverStopping = true
                _state.update { it.copy(phase = Phase.ENDING, statusText = "Ending…") }
            }
            LinkState.FAILED -> fail(
                s,
                failureText(st.failure_reason, st.message),
                if (st.failure_reason == FailureReason.ACCESS_DENIED) Problem.SIGN_IN else Problem.NONE
            )
            else -> Unit
        }
    }

    private fun frameKind(m: VoiceServerMessage): String = when {
        m.voice_audio != null -> "voice audio"
        m.clear_audio != null -> "stop audio"
        m.user_speech != null -> "your words"
        m.voice_transcript != null -> "voice words"
        m.status != null -> "status ${m.status.state.name.lowercase()}"
        m.event != null -> "event ${m.event.event_name}"
        else -> "other"
    }

    private fun failureText(reason: FailureReason, message: String): String {
        val base = when (reason) {
            FailureReason.SERVER_UNAVAILABLE -> "The voice service is unavailable right now"
            FailureReason.THROTTLED -> "Too many voice calls right now — try again shortly"
            FailureReason.SETUP_STALLED -> "The voice agent didn't start in time"
            FailureReason.CONNECTION_FAILED -> "Lost the connection to the voice service"
            FailureReason.ACCESS_DENIED -> "This Antigravity account can't use voice calls"
            FailureReason.TIMEOUT -> "The call timed out"
            FailureReason.VOICE_STALLED -> "The voice stopped responding"
            FailureReason.CONVERSATION_GONE -> "This chat no longer exists on the hub"
            else -> if (_state.value.connectedAt > 0) "The call was disconnected" else "Couldn't connect to the voice agent"
        }
        return if (message.isNotBlank() && !base.contains(message, ignoreCase = true)) "$base ($message)" else base
    }

    private fun onUserSpeech(us: VoiceServerMessage.UserSpeech) {
        val text = us.text.trim()
        _state.update { st ->
            val idx = if (us.seq > 0) st.lines.indexOfLast { it.role == Role.USER && it.seq == us.seq }
            else st.lines.indexOfLast { it.role == Role.USER && it.interim }
            val lines = when {
                idx >= 0 -> st.lines.toMutableList().also { l ->
                    if (text.isBlank()) l.removeAt(idx) else l[idx] = l[idx].copy(text = text, interim = us.interim)
                }
                text.isBlank() -> st.lines
                else -> st.lines + Line(lineKeys.incrementAndGet(), Role.USER, us.seq, text, us.interim)
            }
            st.copy(lines = lines.takeLast(80), userSpeaking = us.interim && text.isNotBlank())
        }
    }

    private fun onVoiceTranscript(s: Session, vt: VoiceServerMessage.VoiceTranscript) {
        val now = SystemClock.elapsedRealtime()
        val recent = now - s.lastAgentChunkAt < 1500
        s.lastAgentChunkAt = now
        _state.update { st ->
            val idx = if (vt.seq > 0) st.lines.indexOfLast { it.role == Role.AGENT && it.seq == vt.seq }
            else st.lines.lastIndex.takeIf { it >= 0 && recent && st.lines[it].role == Role.AGENT } ?: -1
            val lines = if (idx >= 0) {
                st.lines.toMutableList().also { l -> l[idx] = l[idx].copy(text = l[idx].text + vt.text) }
            } else {
                val t = vt.text.trimStart()
                if (t.isEmpty()) st.lines else st.lines + Line(lineKeys.incrementAndGet(), Role.AGENT, vt.seq, t, false)
            }
            st.copy(lines = lines.takeLast(80), userSpeaking = false)
        }
    }

    private fun fail(s: Session, message: String, problem: Problem = Problem.NONE) {
        if (session !== s) return
        note(s, "Call failed: $message")
        val st = _state.value
        val now = SystemClock.elapsedRealtime()
        val details = AgyVoiceDiagnostics.immediate(
            s.app,
            AgyVoiceDiagnostics.Facts(
                error = message,
                cause = s.lastFailure,
                startedAtWall = st.startedAtWall,
                failedAtWall = System.currentTimeMillis(),
                sinceStartMs = now - s.startedAt,
                openForMs = s.openedAt.takeIf { it > 0 }?.let { now - it },
                framesIn = s.framesIn,
                lastFrame = s.lastFrame,
                listened = st.connectedAt > 0,
                handshake = s.handshake,
                closeCode = s.closeCode,
                closeReason = s.closeReason,
                conversationId = s.conversationId,
                voice = s.voice,
                language = s.language,
                planModel = s.planModel,
                linkRetries = s.linkRetries
            )
        )
        _state.update {
            it.copy(
                phase = Phase.FAILED,
                error = message,
                problem = problem,
                agentSpeaking = false,
                userSpeaking = false,
                statusText = "",
                log = (it.log + details).takeLast(LOG_MAX)
            )
        }
        val hubBase = AgyVoiceHubResolver.currentHubBaseUrl(s.app)
        scope.launch {
            val more = runCatching { AgyVoiceDiagnostics.fromHub(s.app, hubBase, healthClient) }
                .getOrElse { listOf("Hub diagnostics error: ${it.message}") }
            _state.update { it.copy(log = (it.log + more).takeLast(LOG_MAX)) }
        }
        s.endRequested = true
        runCatching { s.ws?.close(1000, null) }
        s.release()
        _micLevel.value = 0f
        _voiceLevel.value = 0f
        runCatching { onCallLifecycleChanged?.invoke(false) }
    }

    private fun teardown(s: Session) {
        s.endRequested = true
        s.job?.cancel()
        runCatching { s.ws?.cancel() }
        s.release()
        if (session === s) session = null
        _micLevel.value = 0f
        _voiceLevel.value = 0f
        runCatching { onCallLifecycleChanged?.invoke(false) }
        val cid = s.conversationId
        if (cid.isNotBlank()) {
            scope.launch {
                delay(1500)
                AgyVoiceChats.refresh(cid, s.app)
            }
        }
    }

    private class Session(val app: Context, voice: String) {
        @Volatile var voice: String = voice
        @Volatile var language: String = AgyVoicePreferences.LANGUAGE_AUTO
        val startedAt = SystemClock.elapsedRealtime()
        var job: Job? = null
        @Volatile var ws: WebSocket? = null
        @Volatile var conversationId: String = ""
        @Volatile var planModel: Int = 0
        @Volatile var socketOpen = false
        @Volatile var endRequested = false
        @Volatile var serverStopping = false
        @Volatile var voiceStarted = false
        @Volatile var micRunning = false
        @Volatile var closeReason: String? = null
        @Volatile var lastAgentChunkAt = 0L
        @Volatile var lastFailure: Throwable? = null
        @Volatile var handshake: String? = null
        @Volatile var openedAt = 0L
        @Volatile var framesIn = 0
        @Volatile var lastFrame = ""
        @Volatile var closeCode = 0
        var micStarted = false
        var linkRetries = 0
        private var pulseJob: Job? = null
        private var micThread: Thread? = null
        private var released = false

        val route = AudioRoute(app)
        val player = Player { level, speaking ->
            _voiceLevel.value = level
            if (_state.value.agentSpeaking != speaking) _state.update { it.copy(agentSpeaking = speaking) }
        }

        fun send(msg: VoiceClientMessage): Boolean =
            socketOpen && (ws?.send(VoiceClientMessage.ADAPTER.encodeByteString(msg)) == true)

        fun startPulses() {
            pulseJob?.cancel()
            pulseJob = scope.launch {
                delay(1000)
                while (isActive && !endRequested) {
                    val p = _state.value.phase
                    if (p != Phase.CONNECTING && p != Phase.RECONNECTING) break
                    player.enqueue(Chimes.pulse)
                    delay(2000)
                }
            }
        }

        fun stopPulses() {
            pulseJob?.cancel()
            pulseJob = null
        }

        fun startMic() {
            micStarted = true
            micRunning = true
            micThread = Thread({ micLoop() }, "agy-voice-mic").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
        }

        @SuppressLint("MissingPermission")
        private fun micLoop() {
            val minBuf = AudioRecord.getMinBufferSize(
                MIC_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val rec = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    MIC_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    max(minBuf, MIC_CHUNK_SAMPLES * 2 * 4)
                )
            } catch (e: Exception) {
                lastFailure = e
                fail(this, "Microphone unavailable: ${e.message ?: "permission denied"}", Problem.MIC)
                return
            }
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                fail(this, "Microphone failed to initialize — check app permissions.", Problem.MIC)
                return
            }
            val aec = if (AcousticEchoCanceler.isAvailable()) {
                runCatching { AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true } }.getOrNull()
            } else null
            val ns = if (NoiseSuppressor.isAvailable()) {
                runCatching { NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true } }.getOrNull()
            } else null
            val agc = if (AutomaticGainControl.isAvailable()) {
                runCatching { AutomaticGainControl.create(rec.audioSessionId)?.apply { enabled = false } }.getOrNull()
            } else null
            try {
                rec.startRecording()
                if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    fail(this, "Another app is using the microphone.", Problem.MIC)
                    return
                }
                val buf = ShortArray(MIC_CHUNK_SAMPLES)
                val bytes = ByteArray(MIC_CHUNK_SAMPLES * 2)
                var level = 0f
                while (micRunning && !endRequested) {
                    var n = 0
                    while (n < MIC_CHUNK_SAMPLES && micRunning) {
                        val r = rec.read(buf, n, MIC_CHUNK_SAMPLES - n)
                        if (r < 0) throw IllegalStateException("microphone read failed ($r)")
                        n += r
                    }
                    if (n < MIC_CHUNK_SAMPLES) break
                    val muted = _state.value.muted || AgyVoicePreview.playing.value != null
                    var sum = 0.0
                    for (i in 0 until MIC_CHUNK_SAMPLES) {
                        val v = if (muted) 0 else buf[i].toInt()
                        sum += (v * v).toDouble()
                        bytes[2 * i] = (v and 0xff).toByte()
                        bytes[2 * i + 1] = ((v shr 8) and 0xff).toByte()
                    }
                    val raw = min(1f, (sqrt(sum / MIC_CHUNK_SAMPLES) / 32768.0 * 7.0).toFloat())
                    level = max(raw, level * 0.8f)
                    _micLevel.value = level
                    send(VoiceClientMessage(mic_audio = bytes.toByteString()))
                }
            } catch (e: Exception) {
                if (micRunning && !endRequested) {
                    lastFailure = e
                    fail(this, "Microphone stopped: ${e.message ?: "error"}", Problem.MIC)
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
                runCatching { aec?.release() }
                runCatching { ns?.release() }
                runCatching { agc?.release() }
                _micLevel.value = 0f
            }
        }

        fun release() {
            if (released) return
            released = true
            micRunning = false
            stopPulses()
            micThread?.interrupt()
            player.release()
            route.end()
        }
    }

    private class Player(private val onLevel: (Float, Boolean) -> Unit) {
        private val queue = LinkedBlockingQueue<ByteArray>()
        @Volatile private var running = false
        @Volatile private var generation = 0
        @Volatile private var framesWritten = 0L
        private var track: AudioTrack? = null
        private var thread: Thread? = null

        fun start() {
            if (running) return
            val minBuf = AudioTrack.getMinBufferSize(
                VOICE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val t = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(VOICE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(max(minBuf, VOICE_RATE * 2 / 5))
                    .build()
            } catch (e: Exception) {
                Log.w(TAG, "AudioTrack init error: ${e.message}")
                return
            }
            track = t
            runCatching { t.play() }
            running = true
            thread = Thread({ loop(t) }, "agy-voice-play").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
        }

        fun enqueue(pcm: ByteArray) {
            if (running) queue.offer(pcm)
        }

        fun clear() {
            generation++
            queue.clear()
            val t = track ?: return
            runCatching {
                t.pause()
                t.flush()
                framesWritten = 0
                t.play()
            }
        }

        private fun loop(t: AudioTrack) {
            var level = 0f
            var speaking = false
            while (running) {
                val chunk = try {
                    queue.poll(60, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    break
                }
                if (chunk == null) {
                    val head = runCatching { t.playbackHeadPosition.toLong() and 0xffffffffL }.getOrDefault(Long.MAX_VALUE)
                    val stillPlaying = head < framesWritten
                    level *= 0.7f
                    if (level < 0.01f) level = 0f
                    if (speaking != stillPlaying || level > 0f) {
                        speaking = stillPlaying
                        onLevel(level, speaking)
                    }
                    continue
                }
                val gen = generation
                var off = 0
                while (off < chunk.size && running && gen == generation) {
                    val len = min(PLAY_SLICE_BYTES, chunk.size - off)
                    var sum = 0.0
                    var i = off
                    while (i + 1 < off + len) {
                        val v = ((chunk[i + 1].toInt() shl 8) or (chunk[i].toInt() and 0xff)).toShort().toInt()
                        sum += (v * v).toDouble()
                        i += 2
                    }
                    val raw = min(1f, (sqrt(sum / (len / 2).coerceAtLeast(1)) / 32768.0 * 5.0).toFloat())
                    level = max(raw, level * 0.85f)
                    speaking = true
                    onLevel(level, true)
                    val w = t.write(chunk, off, len, AudioTrack.WRITE_BLOCKING)
                    if (w <= 0) break
                    if (gen == generation) framesWritten += w / 2
                    off += w
                }
            }
            onLevel(0f, false)
        }

        fun release() {
            running = false
            generation++
            queue.clear()
            thread?.interrupt()
            track?.let { t ->
                runCatching { t.pause(); t.flush() }
                runCatching { t.release() }
            }
            track = null
        }
    }

    private class AudioRoute(context: Context) {
        private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        private var focus: AudioFocusRequest? = null
        private var started = false
        private var previousMode = AudioManager.MODE_NORMAL

        fun begin(speaker: Boolean) {
            if (started) return
            started = true
            runCatching {
                previousMode = am.mode
                am.mode = AudioManager.MODE_IN_COMMUNICATION
            }
            if (Build.VERSION.SDK_INT >= 26) runCatching {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                am.requestAudioFocus(req)
                focus = req
            }
            route(speaker)
        }

        fun route(speaker: Boolean) {
            if (!started) return
            runCatching {
                if (Build.VERSION.SDK_INT >= 31) {
                    val devices = am.availableCommunicationDevices
                    val target = if (speaker) devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    else devices.firstOrNull { it.type in headsetTypes() }
                        ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                    if (target != null) am.setCommunicationDevice(target) else am.clearCommunicationDevice()
                } else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = speaker
                }
            }
        }

        fun end() {
            if (!started) return
            started = false
            runCatching {
                if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice()
                else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = false
                }
            }
            runCatching {
                am.mode = if (previousMode == AudioManager.MODE_IN_COMMUNICATION) AudioManager.MODE_NORMAL else previousMode
            }
            if (Build.VERSION.SDK_INT >= 26) focus?.let { f -> runCatching { am.abandonAudioFocusRequest(f) } }
            focus = null
        }

        companion object {
            fun headsetTypes(): Set<Int> = buildSet {
                add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
                add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
                add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
                if (Build.VERSION.SDK_INT >= 26) add(AudioDeviceInfo.TYPE_USB_HEADSET)
                if (Build.VERSION.SDK_INT >= 31) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
            }

            fun hasHeadset(context: Context): Boolean = runCatching {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val types = headsetTypes()
                am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in types }
            }.getOrDefault(false)
        }
    }

    private object Chimes {
        val connected: ByteArray by lazy { tones(listOf(523.25 to 90, 0.0 to 30, 783.99 to 150), 0.16) }
        val end: ByteArray by lazy { tones(listOf(659.25 to 90, 0.0 to 30, 440.0 to 170), 0.16) }
        val pulse: ByteArray by lazy { tones(listOf(320.0 to 110), 0.07) }

        private fun tones(parts: List<Pair<Double, Int>>, volume: Double): ByteArray {
            val total = parts.sumOf { it.second } * VOICE_RATE / 1000
            val out = ByteArray(total * 2)
            var pos = 0
            for ((freq, ms) in parts) {
                val n = ms * VOICE_RATE / 1000
                val fade = min(n / 4, VOICE_RATE * 12 / 1000)
                for (i in 0 until n) {
                    val env = when {
                        freq == 0.0 -> 0.0
                        i < fade -> i.toDouble() / fade
                        i > n - fade -> (n - i).toDouble() / fade
                        else -> 1.0
                    }
                    val v = (sin(2 * PI * freq * i / VOICE_RATE) * env * volume * 32767).toInt()
                    out[2 * pos] = (v and 0xff).toByte()
                    out[2 * pos + 1] = ((v shr 8) and 0xff).toByte()
                    pos++
                }
            }
            return out
        }
    }
}
