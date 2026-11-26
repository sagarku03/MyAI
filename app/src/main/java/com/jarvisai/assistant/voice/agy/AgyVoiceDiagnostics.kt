package com.jarvisai.assistant.voice.agy

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import com.jarvisai.assistant.terminal.LocalServerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Collects local + hub diagnostic details when an Antigravity voice call fails
 * so failures can be inspected or copied from the Call Log sheet.
 */
internal object AgyVoiceDiagnostics {
    private const val MAX_STACK_LINES = 40

    data class Facts(
        val error: String,
        val cause: Throwable?,
        val startedAtWall: Long,
        val failedAtWall: Long,
        val sinceStartMs: Long,
        val openForMs: Long?,
        val framesIn: Int,
        val lastFrame: String,
        val listened: Boolean,
        val handshake: String?,
        val closeCode: Int,
        val closeReason: String?,
        val conversationId: String,
        val voice: String,
        val language: String,
        val planModel: Int,
        val linkRetries: Int
    )

    private fun localTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(ms))

    private fun utcTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(ms))

    fun immediate(context: Context, f: Facts): List<String> {
        val out = mutableListOf<String>()
        out += "──── Details of the failure (Copy or screenshot) ────"
        out += "Error shown: ${f.error}"
        val c = f.cause
        if (c != null) {
            out += "Exception: ${c.javaClass.name}${c.message?.let { ": $it" } ?: " (no message)"}"
            var cause = c.cause
            var depth = 0
            while (cause != null && cause !== c && depth < 5) {
                out += "Caused by: ${cause.javaClass.name}${cause.message?.let { ": $it" } ?: ""}"
                cause = cause.cause
                depth++
            }
            out += "Stack trace:"
            c.stackTraceToString().lines().drop(1).filter { it.isNotBlank() }.take(MAX_STACK_LINES)
                .forEach { out += "  ${it.trim()}" }
        } else {
            out += "Exception: none (the app or hub ended the call with the message above)"
        }
        out += "When: ${localTime(f.failedAtWall)} phone time (UTC ${utcTime(f.failedAtWall)}), " +
            "${"%.1f".format(Locale.US, f.sinceStartMs / 1000.0)} s after call started at ${localTime(f.startedAtWall)}"
        out += "Hub link: " + when (val open = f.openForMs) {
            null -> "never accepted (no HTTP 101)" + (f.handshake?.let { "; hub answered: $it" } ?: "")
            else -> "accepted (HTTP 101), ${"%.1f".format(Locale.US, open / 1000.0)} s before failure"
        }
        out += "Messages from hub: ${f.framesIn}" + (f.lastFrame.takeIf { it.isNotBlank() }?.let { ", last: $it" } ?: "")
        out += "Voice agent was listening: ${if (f.listened) "yes" else "no"}"
        if (f.closeCode != 0 || !f.closeReason.isNullOrBlank()) {
            out += "Hub closed link: code ${f.closeCode}${f.closeReason?.let { " ($it)" } ?: ""}"
        }
        if (f.linkRetries > 0) out += "Reconnects before failure: ${f.linkRetries}"
        out += "Call: chat ${f.conversationId.ifBlank { "(none yet)" }}, voice ${f.voice}, " +
            "language ${AgyVoicePreferences.acceptLanguage(f.language)}, plan model ${f.planModel}"
        out += "App: ${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID} ${BuildConfig.BUILD_TYPE})"
        out += "Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        val diag = LocalServerManager.agyDiagnostics.value
        out += "agy: ${diag.agyVersion.ifBlank { "unknown" }} (found=${diag.agyBinaryFound}, hubPort=${diag.hubPort})"
        out += "Hub URL: ${AgyVoiceHubResolver.currentHubBaseUrl(context)}"
        return out
    }

    suspend fun fromHub(context: Context, hubBase: String, healthClient: OkHttpClient): List<String> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<String>()
            val url = "$hubBase/voice/health".toHttpUrlOrNull()
            out += if (url == null) {
                "Hub health check skipped: invalid URL ($hubBase)"
            } else {
                runCatching {
                    val req = AgyVoiceHubResolver.attachHubAuthHeaders(Request.Builder().url(url).get(), url, context).build()
                    healthClient.newCall(req).execute().use { r ->
                        val body = runCatching { r.body?.string()?.trim()?.take(180) }.getOrNull().orEmpty()
                        "Hub /voice/health after failure: HTTP ${r.code}${if (body.isNotBlank()) " ($body)" else ""}"
                    }
                }.getOrElse {
                    "Hub /voice/health unreachable: ${it.javaClass.simpleName}${it.message?.let { m -> ": $m" } ?: ""}"
                }
            }
            val diag = LocalServerManager.agyDiagnostics.value
            out += "Local supervisor status: ${LocalServerManager.status.value::class.java.simpleName}, reachable=${diag.agyHubReachable}"
            if (diag.lastNotice.isNotBlank()) {
                out += "Supervisor note: ${diag.lastNotice}"
            }
            out += "──── End of details ────"
            out
        }
}
