package com.jarvisai.assistant.agent.agy

import android.content.Context
import com.jarvisai.assistant.agent.AgentApprovalRequest
import com.jarvisai.assistant.agent.AgentChatBackend
import com.jarvisai.assistant.agent.AgentKind
import com.jarvisai.assistant.agent.ChatMessage
import com.jarvisai.assistant.agent.ChatSessionStore
import com.jarvisai.assistant.agent.Conversation
import com.jarvisai.assistant.agent.core.AgyBridgeService
import com.jarvisai.assistant.data.remote.JarvisHubConfig
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.LocalServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Phase 3: Antigravity (`agy --hub`) Backend implementing [AgentChatBackend].
 * Connects to the local Antigravity hub daemon (`JarvisHubConfig.getHubBaseUrl(context)`) for live SSE
 * trajectory synchronization, step streaming, and interactive approval resolution.
 */
class AgyChatBackend(
    private val context: Context,
    private val store: ChatSessionStore = ChatSessionStore(
        agentKind = AgentKind.AGY,
        defaultWorkingDir = LocalEnvironmentManager.getHomeDir(context).absolutePath
    )
) : AgentChatBackend {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override val kind: AgentKind = AgentKind.AGY
    override val conversations: StateFlow<List<Conversation>> = store.conversations
    override val currentConversation: StateFlow<Conversation?> = store.currentConversation
    override val messages: StateFlow<List<ChatMessage>> = store.messages
    override val isStreaming: StateFlow<Boolean> = store.isStreaming
    override val isLoadingConversation: StateFlow<Boolean> = store.isLoadingConversation
    override val conversationError: StateFlow<String?> = store.conversationError
    override val pendingApproval: StateFlow<AgentApprovalRequest?> = store.pendingApproval

    init {
        store.ensureActiveConversation(defaultModel = "agy-hub")
    }

    override fun startNewChat() {
        store.createNewConversation(
            title = "Agy Trajectory ${conversations.value.size + 1}",
            workingDirectory = LocalEnvironmentManager.getHomeDir(context).absolutePath,
            modelId = "agy-hub"
        )
    }

    override fun selectConversation(id: String) {
        store.selectConversation(id)
    }

    override fun sendMessage(content: String) {
        if (content.isBlank()) return
        val conv = store.ensureActiveConversation(defaultModel = "agy-hub")
        store.appendMessage(
            ChatMessage(
                conversationId = conv.id,
                role = "user",
                content = content
            )
        )
        store.setError(null)
        store.setStreaming(true)

        val assistantMsgId = UUID.randomUUID().toString()

        activeJob?.cancel()
        activeJob = scope.launch {
            val startTime = System.currentTimeMillis()
            try {
                LocalServerManager.ensureAgyHubAvailable(context)

                val payload = JSONObject().apply {
                    put("model", "claude-sonnet-4-20250514")
                    put("stream", true)
                    put(
                        "messages",
                        JSONArray().apply {
                            put(
                                JSONObject().apply {
                                    put("role", "user")
                                    put("content", content)
                                }
                            )
                        }
                    )
                }

                val hubBaseUrl = JarvisHubConfig.getHubBaseUrl(context)
                val request = Request.Builder()
                    .url("$hubBaseUrl/v1/chat/completions")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        store.setError("Antigravity Hub returned HTTP ${response.code}")
                        return@use
                    }
                    val bodyStream = response.body?.byteStream() ?: return@use
                    BufferedReader(InputStreamReader(bodyStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val raw = line?.trim() ?: continue
                            if (!raw.startsWith("data:")) continue
                            val dataStr = raw.removePrefix("data:").trim()
                            if (dataStr == "[DONE]" || dataStr.isEmpty()) break

                            runCatching {
                                val json = JSONObject(dataStr)
                                val choices = json.optJSONArray("choices")
                                val delta = choices?.optJSONObject(0)?.optJSONObject("delta")
                                if (delta != null) {
                                    val reasoning = delta.optString("reasoning_content", "")
                                    val textChunk = delta.optString("content", "")
                                    if (reasoning.isNotEmpty()) {
                                        store.upsertStreamingAssistantMessage(
                                            conversationId = conv.id,
                                            messageId = assistantMsgId,
                                            thoughtDelta = reasoning,
                                            thoughtDurationMs = System.currentTimeMillis() - startTime,
                                            modelUsed = "agy-hub",
                                            isStreaming = true
                                        )
                                    }
                                    if (textChunk.isNotEmpty()) {
                                        store.upsertStreamingAssistantMessage(
                                            conversationId = conv.id,
                                            messageId = assistantMsgId,
                                            textDelta = textChunk,
                                            modelUsed = "agy-hub",
                                            isStreaming = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                store.updateMessage(conv.id, assistantMsgId) { it.copy(isStreaming = false) }
            } catch (e: Exception) {
                store.setError("Antigravity stream failed: ${e.localizedMessage ?: "Connection refused"}")
            } finally {
                store.setStreaming(false)
            }
        }
    }

    override fun stopStreaming() {
        activeJob?.cancel()
        activeJob = null
        store.setStreaming(false)
    }

    override fun deleteConversation(id: String) {
        store.deleteConversation(id)
    }

    override fun forkConversation(id: String) {
        store.forkConversation(id)
    }

    override fun retryConnections() {
        scope.launch {
            LocalServerManager.ensureAgyHubAvailable(context)
            AgyBridgeService.instance.connectWebSocket()
        }
    }

    override fun onAppForegrounded() {
        scope.launch {
            LocalServerManager.ensureAgyHubAvailable(context)
        }
    }

    override fun resolveApproval(requestId: String, allow: Boolean, alwaysAllowForSession: Boolean) {
        store.setPendingApproval(null)
    }
}
