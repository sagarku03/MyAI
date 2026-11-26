package com.jarvisai.assistant.voice.agy

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class VoiceChatLine(
    val role: String = "",
    val text: String = "",
    val time: String? = null
) {
    val isUser: Boolean get() = role == "ROLE_USER"
    val isVoice: Boolean get() = role == "ROLE_VOICE"
    val isHandoff: Boolean get() = role == "ROLE_HANDOFF"
    val timeMs: Long? get() = parseIsoMillis(time)
}

data class VoiceChatRecord(
    val startedAt: String? = null,
    val stoppedAt: String? = null,
    val lines: List<VoiceChatLine> = emptyList()
) {
    val startedAtMs: Long? get() = parseIsoMillis(startedAt)
    val stoppedAtMs: Long? get() = parseIsoMillis(stoppedAt)
    val durationMs: Long? get() = startedAtMs?.let { s -> stoppedAtMs?.let { e -> (e - s).coerceAtLeast(0) } }
    val hasTalk: Boolean get() = lines.any { (it.isUser || it.isVoice) && it.text.isNotBlank() }
}

/**
 * Fetches saved Antigravity voice calls & transcripts per cascade (`GET /voice/chats?conversation_id=...`)
 * served by `agy --hub` (1.5+).
 */
object AgyVoiceChats {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private val _chats = MutableStateFlow<Map<String, List<VoiceChatRecord>>>(emptyMap())
    val chats: StateFlow<Map<String, List<VoiceChatRecord>>> = _chats.asStateFlow()

    fun refresh(conversationId: String, context: Context? = null) {
        if (conversationId.isBlank()) return
        scope.launch { fetch(conversationId, context) }
    }

    suspend fun fetch(conversationId: String, context: Context? = null): List<VoiceChatRecord>? = withContext(Dispatchers.IO) {
        if (conversationId.isBlank()) return@withContext null
        val base = AgyVoiceHubResolver.currentHubBaseUrl(context)
        val url = "$base/voice/chats".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("conversation_id", conversationId)
            ?.build()
            ?: return@withContext null
        val req = AgyVoiceHubResolver.attachHubAuthHeaders(Request.Builder().url(url).get(), url, context).build()
        val list = runCatching {
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) null
                else parseChatsResponse(r.body?.string().orEmpty())
            }
        }.getOrNull() ?: return@withContext null
        val sorted = list.filter { it.hasTalk }.sortedBy { it.startedAtMs ?: 0L }
        _chats.update { if (it[conversationId] == sorted) it else it + (conversationId to sorted) }
        sorted
    }

    internal fun parseChatsResponse(body: String): List<VoiceChatRecord> {
        if (body.isBlank()) return emptyList()
        val root = JSONObject(body)
        val arr = root.optJSONArray("chats") ?: return emptyList()
        val out = ArrayList<VoiceChatRecord>(arr.length())
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val linesArr = c.optJSONArray("lines")
            val lines = if (linesArr == null) emptyList() else buildList {
                for (j in 0 until linesArr.length()) {
                    val l = linesArr.optJSONObject(j) ?: continue
                    add(
                        VoiceChatLine(
                            role = l.optString("role", ""),
                            text = l.optString("text", ""),
                            time = l.optString("time", "").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
            out.add(
                VoiceChatRecord(
                    startedAt = c.optString("startedAt", "").takeIf { it.isNotBlank() },
                    stoppedAt = c.optString("stoppedAt", "").takeIf { it.isNotBlank() },
                    lines = lines
                )
            )
        }
        return out
    }
}

internal fun parseIsoMillis(s: String?): Long? {
    if (s.isNullOrBlank() || s.length < 19) return null
    return runCatching {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        var ms = fmt.parse(s.substring(0, 19))!!.time
        var i = 19
        if (i < s.length && s[i] == '.') {
            i++
            val start = i
            while (i < s.length && s[i].isDigit()) i++
            ms += s.substring(start, i).padEnd(3, '0').take(3).toLong()
        }
        if (i < s.length && (s[i] == '+' || s[i] == '-')) {
            val sign = if (s[i] == '+') 1 else -1
            val hh = s.substring(i + 1, i + 3).toInt()
            val mm = s.substring(i + 4, i + 6).toInt()
            ms -= sign * (hh * 3_600_000L + mm * 60_000L)
        }
        ms
    }.getOrNull()
}
