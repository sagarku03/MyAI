package com.jarvisai.assistant.agent.claude

import com.jarvisai.assistant.agent.core.AgyBridgeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

data class ClaudeSessionInfo(
    val id: String,
    val title: String,
    val isRunning: Boolean,
    val lastMessage: String? = null
)

data class ClaudeToolApproval(
    val toolCallId: String,
    val toolName: String,
    val inputJson: String,
    val description: String
)

sealed class ClaudeStreamEvent {
    data class TextDelta(val delta: String) : ClaudeStreamEvent()
    data class ThoughtDelta(val thought: String) : ClaudeStreamEvent()
    data class ToolApprovalRequested(val approval: ClaudeToolApproval) : ClaudeStreamEvent()
    data class SubagentProgress(val subagentId: String, val role: String, val status: String) : ClaudeStreamEvent()
    data class ExecutionComplete(val fullResponse: String) : ClaudeStreamEvent()
    data class Error(val message: String) : ClaudeStreamEvent()
}

class ClaudeBridgeClient(private val bridge: AgyBridgeService = AgyBridgeService.instance) {

    suspend fun getStatus(): Result<JSONObject> = withContext(Dispatchers.IO) {
        bridge.get("/api/claude/status").map { JSONObject(it) }
    }

    suspend fun listSessions(): Result<List<ClaudeSessionInfo>> = withContext(Dispatchers.IO) {
        bridge.get("/api/claude/sessions").map { responseText ->
            val json = JSONObject(responseText)
            val list = mutableListOf<ClaudeSessionInfo>()
            val sessionsArray = json.optJSONArray("sessions") ?: JSONArray()
            for (i in 0 until sessionsArray.length()) {
                val obj = sessionsArray.getJSONObject(i)
                list.add(
                    ClaudeSessionInfo(
                        id = obj.getString("id"),
                        title = obj.optString("title", "Claude Chat"),
                        isRunning = obj.optBoolean("isRunning", false),
                        lastMessage = obj.optString("lastMessage")
                    )
                )
            }
            list
        }
    }

    suspend fun sendPrompt(sessionId: String, prompt: String): Result<Unit> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("prompt", prompt)
        }
        bridge.post("/api/claude/sessions/$sessionId/prompt", payload.toString()).map { Unit }
    }

    suspend fun submitToolApproval(sessionId: String, toolCallId: String, approved: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("toolCallId", toolCallId)
            put("approved", approved)
        }
        bridge.post("/api/claude/sessions/$sessionId/approval", payload.toString()).map { Unit }
    }

    fun attachSessionStream(sessionId: String): Flow<ClaudeStreamEvent> = callbackFlow {
        val wsUrl = "${bridge.getBaseUrl().replace("http", "ws")}/api/claude/sessions/$sessionId/events"
        val request = Request.Builder().url(wsUrl).build()

        val ws = bridge.httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    when (json.optString("type")) {
                        "text_delta" -> trySend(ClaudeStreamEvent.TextDelta(json.optString("delta")))
                        "thought_delta" -> trySend(ClaudeStreamEvent.ThoughtDelta(json.optString("thought")))
                        "approval_required" -> {
                            val approval = ClaudeToolApproval(
                                toolCallId = json.getString("toolCallId"),
                                toolName = json.getString("toolName"),
                                inputJson = json.optString("input"),
                                description = json.optString("description")
                            )
                            trySend(ClaudeStreamEvent.ToolApprovalRequested(approval))
                        }
                        "subagent_progress" -> {
                            trySend(ClaudeStreamEvent.SubagentProgress(
                                subagentId = json.getString("subagentId"),
                                role = json.optString("role"),
                                status = json.optString("status")
                            ))
                        }
                        "complete" -> trySend(ClaudeStreamEvent.ExecutionComplete(json.optString("fullResponse")))
                        "error" -> trySend(ClaudeStreamEvent.Error(json.optString("message")))
                    }
                } catch (e: Exception) {
                    trySend(ClaudeStreamEvent.Error("Parse failure: ${e.message}"))
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                trySend(ClaudeStreamEvent.Error("Stream disconnected: ${t.message}"))
                close()
            }
        })

        awaitClose { ws.close(1000, "Flow closed") }
    }

    suspend fun killSession(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        bridge.post("/api/claude/sessions/$sessionId/kill", "{}").map { Unit }
    }
}
