package com.jarvisai.assistant.service

import android.content.Context
import com.jarvisai.assistant.model.ProceduralHudMode
import com.jarvisai.assistant.termux.JarvisTermuxBridge
import com.jarvisai.assistant.ui.components.ProceduralHudModeState
import com.jarvisai.assistant.watchman.JarvisWatchmanManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ClawbotCommandLog(
    val timestamp: String,
    val command: String,
    val responseSummary: String,
    val dispatchedToTelegram: Boolean
)

/**
 * Feature 3: Clawbot 24/7 Inbound Remote Telegram Bot Command Gateway.
 * Inspired by AbuZar-Ansarii/Clawbot & Hermes-Agent-On-Android:
 * Transforms the Android device into a 24/7 remotely controllable satellite agent via Telegram Bot API.
 *
 * Supported remote commands:
 * - `/status`   -> Battery %, charging state, network, free RAM, active foreground app
 * - `/photo`    -> Captures stealth front-camera snapshot via JarvisWatchmanManager & sends to Telegram
 * - `/otp`      -> Harvests latest 4-8 digit OTP from incoming notifications & sends to Telegram
 * - `/locate`   -> Sends device network/IP location & Google Maps search link
 * - `/ring`     -> Speaks loud audible beacon on device speaker to locate misplaced phone
 * - `/speak <t>`-> Speaks custom text aloud on the phone
 * - `/termux <c>`-> Executes shell command via JarvisTermuxBridge and returns stdout
 */
object ClawbotTelegramGateway {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private var lastUpdateId: Long = 0L

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val _isDaemonRunning = MutableStateFlow(false)
    val isDaemonRunning: StateFlow<Boolean> = _isDaemonRunning.asStateFlow()

    private val _commandLogs = MutableStateFlow<List<ClawbotCommandLog>>(
        listOf(
            ClawbotCommandLog(
                timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
                command = "SYSTEM_BOOT",
                responseSummary = "Clawbot 24/7 Remote Gateway Ready. Commands: /status, /photo, /otp, /locate, /ring, /speak, /termux",
                dispatchedToTelegram = false
            )
        )
    )
    val commandLogs: StateFlow<List<ClawbotCommandLog>> = _commandLogs.asStateFlow()

    fun startGatewayDaemon(context: Context, botToken: String, authorizedChatId: String) {
        val appCtx = context.applicationContext
        pollingJob?.cancel()
        if (botToken.isBlank()) {
            _isDaemonRunning.value = false
            appendLog("GATEWAY_WARN", "Telegram Bot Token empty; running in Local Test Gateway mode.", false)
            return
        }
        _isDaemonRunning.value = true
        ProceduralHudModeState.setMode(ProceduralHudMode.SENTRY_MATRIX)
        appendLog("DAEMON_START", "24/7 Telegram Long-Polling Gateway active for Chat ID: ${authorizedChatId.ifBlank { "ANY" }}", true)

        pollingJob = scope.launch {
            while (isActive && _isDaemonRunning.value) {
                try {
                    pollTelegramUpdatesOnce(appCtx, botToken.trim(), authorizedChatId.trim())
                } catch (_: Exception) {
                }
                delay(3500L)
            }
        }
    }

    fun stopGatewayDaemon() {
        pollingJob?.cancel()
        pollingJob = null
        _isDaemonRunning.value = false
        appendLog("DAEMON_STOP", "24/7 Telegram Gateway stopped.", false)
    }

    private suspend fun pollTelegramUpdatesOnce(
        context: Context,
        botToken: String,
        authorizedChatId: String
    ) {
        val url = "https://api.telegram.org/bot$botToken/getUpdates?offset=${lastUpdateId + 1}&timeout=5"
        val req = Request.Builder().url(url).get().build()
        val bodyStr = withContext(Dispatchers.IO) {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.string()
            }
        } ?: return

        val json = JSONObject(bodyStr)
        if (!json.optBoolean("ok", false)) return
        val results = json.optJSONArray("result") ?: return

        for (i in 0 until results.length()) {
            val upd = results.getJSONObject(i)
            val updateId = upd.optLong("update_id", 0L)
            if (updateId > lastUpdateId) {
                lastUpdateId = updateId
            }
            val msg = upd.optJSONObject("message") ?: continue
            val chatObj = msg.optJSONObject("chat") ?: continue
            val senderChatId = chatObj.optLong("id", 0L).toString()
            val text = msg.optString("text", "").trim()

            if (text.isEmpty()) continue
            if (authorizedChatId.isNotBlank() && senderChatId != authorizedChatId) {
                sendTelegramTextReply(botToken, senderChatId, "⛔ JARVIS Clawbot Security: Unauthorized Chat ID ($senderChatId).")
                continue
            }

            executeInboundRemoteCommand(
                context = context,
                rawCommand = text,
                botToken = botToken,
                chatId = senderChatId
            )
        }
    }

    /**
     * Executes an inbound remote command (either from live Telegram polling or from local UI/Voice test)
     * and dispatches the reply to Telegram if credentials are provided.
     */
    suspend fun executeInboundRemoteCommand(
        context: Context,
        rawCommand: String,
        botToken: String? = null,
        chatId: String? = null
    ): String {
        val clean = rawCommand.trim()
        val cmdLower = clean.lowercase()
        val timeNow = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

        val responseText: String = when {
            cmdLower.startsWith("/status") || cmdLower.startsWith("/telemetry") -> {
                val sysMgr = SystemStatusManager(context)
                val bat = sysMgr.getBatteryInfo()
                val net = sysMgr.getNetworkInfo()
                val ram = sysMgr.getStorageAndRamInfo()
                val activePkg = JarvisAccessibilityService.getScreenContext(context).activePackageName.ifBlank { "Home Screen" }
                "⚡ JARVIS Clawbot 24/7 Telemetry:\n" +
                    "• Battery: ${bat.levelPercent}% (${if (bat.isCharging) "Charging" else "On Battery"})\n" +
                    "• Network: ${net.transportType} (Wi-Fi: ${net.isWifiEnabled})\n" +
                    "• Free RAM: ${ram.availableRamMb} MB / ${ram.totalRamMb} MB\n" +
                    "• Active App: $activePkg\n" +
                    "• Time: $timeNow"
            }

            cmdLower.startsWith("/photo") || cmdLower.startsWith("/snap") -> {
                val watchman = JarvisWatchmanManager(context)
                val snap = watchman.captureIntruderSnapshot(
                    telegramBotToken = botToken?.takeIf { it.isNotBlank() },
                    telegramChatId = chatId?.takeIf { it.isNotBlank() },
                    caption = "📸 JARVIS Clawbot Remote Snapshot ($timeNow)"
                )
                "📸 Remote Camera Snapshot: ${snap.message}"
            }

            cmdLower.startsWith("/otp") -> {
                val latestOtp = JarvisNotificationListenerService.latestHarvestedOtpFlow.value?.otpCode
                if (!latestOtp.isNullOrBlank()) {
                    "🔐 Latest Harvested OTP Code: $latestOtp"
                } else {
                    "🔐 No recent 4-8 digit OTP found in notification history."
                }
            }

            cmdLower.startsWith("/locate") || cmdLower.startsWith("/gps") -> {
                val net = SystemStatusManager(context).getNetworkInfo()
                "📍 JARVIS Satellite Locator:\n" +
                    "• Network State: ${net.transportType}\n" +
                    "• Maps Quick Link: https://maps.google.com/?q=My+Location\n" +
                    "• Device Status: Online & Armed ($timeNow)"
            }

            cmdLower.startsWith("/ring") || cmdLower.startsWith("/find") -> {
                SystemStatusManager(context).controlVolume(targetPercent = 90)
                JarvisTtsManager.speak(
                    context,
                    "Attention Boss! JARVIS Clawbot remote phone locator beacon activated. Main yahan hoon!"
                )
                "🔔 High-volume acoustic beacon triggered on device speaker (90% volume)."
            }

            cmdLower.startsWith("/speak") -> {
                val msgToSpeak = clean.removePrefix("/speak").removePrefix("/SPEAK").trim()
                    .ifBlank { "Hello Boss, remote message received from Clawbot Telegram Gateway." }
                JarvisTtsManager.speak(context, msgToSpeak)
                "🗣️ Spoken aloud on device: \"$msgToSpeak\""
            }

            cmdLower.startsWith("/termux") -> {
                val shellCmd = clean.removePrefix("/termux").removePrefix("/TERMUX").trim()
                    .ifBlank { "uname -a && uptime" }
                val bridge = JarvisTermuxBridge(context)
                val res = bridge.executeCommand(shellCmd, background = true, timeoutSeconds = 12)
                if (res.success) {
                    "💻 Termux Output (`$shellCmd`):\n${res.stdout.ifBlank { "[Exit 0]" }}"
                } else {
                    "⚠️ Termux Status (`$shellCmd`): ${res.stderr.ifBlank { res.errorMessage }}"
                }
            }

            else -> {
                "🤖 JARVIS Clawbot Remote Gateway:\nUnknown command `$clean`. Try `/status`, `/photo`, `/otp`, `/locate`, `/ring`, `/speak <msg>`, or `/termux <cmd>`."
            }
        }

        var sentToTg = false
        if (!botToken.isNullOrBlank() && !chatId.isNullOrBlank() && !cmdLower.startsWith("/photo")) {
            sentToTg = sendTelegramTextReply(botToken, chatId, responseText)
        }

        appendLog(clean, responseText, sentToTg)
        return responseText
    }

    private suspend fun sendTelegramTextReply(
        botToken: String,
        chatId: String,
        text: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.telegram.org/bot$botToken/sendMessage"
            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
            }
            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url(url).post(body).build()
            httpClient.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun appendLog(cmd: String, summary: String, sent: Boolean) {
        val entry = ClawbotCommandLog(
            timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
            command = cmd,
            responseSummary = summary,
            dispatchedToTelegram = sent
        )
        _commandLogs.value = (listOf(entry) + _commandLogs.value).take(30)
    }
}
