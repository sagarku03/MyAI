package com.jarvisai.assistant.agent.codex

import android.content.Context
import com.jarvisai.assistant.agent.AgentApprovalRequest
import com.jarvisai.assistant.agent.AgentChatBackend
import com.jarvisai.assistant.agent.AgentKind
import com.jarvisai.assistant.agent.ChatMessage
import com.jarvisai.assistant.agent.ChatSessionStore
import com.jarvisai.assistant.agent.Conversation
import com.jarvisai.assistant.model.ToolCall
import com.jarvisai.assistant.model.ToolCallStatus
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.LocalTerminalBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/**
 * Phase 3: OpenAI Codex CLI Backend implementing [AgentChatBackend] backed by [ChatSessionStore].
 */
class CodexChatBackend(
    private val context: Context,
    private val store: ChatSessionStore = ChatSessionStore(
        agentKind = AgentKind.CODEX,
        defaultWorkingDir = LocalEnvironmentManager.getHomeDir(context).absolutePath
    )
) : AgentChatBackend {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    val preferences = CodexPreferences(context)

    override val kind: AgentKind = AgentKind.CODEX
    override val conversations: StateFlow<List<Conversation>> = store.conversations
    override val currentConversation: StateFlow<Conversation?> = store.currentConversation
    override val messages: StateFlow<List<ChatMessage>> = store.messages
    override val isStreaming: StateFlow<Boolean> = store.isStreaming
    override val isLoadingConversation: StateFlow<Boolean> = store.isLoadingConversation
    override val conversationError: StateFlow<String?> = store.conversationError
    override val pendingApproval: StateFlow<AgentApprovalRequest?> = store.pendingApproval

    private val _lastWorkspaceDiff = MutableStateFlow("")
    val lastWorkspaceDiff: StateFlow<String> = _lastWorkspaceDiff.asStateFlow()

    init {
        store.ensureActiveConversation(defaultModel = preferences.state.value.model)
    }

    private fun resolveWorkingDirectory(): String {
        val configured = preferences.state.value.workingDirectory
        return configured.ifBlank { LocalEnvironmentManager.getHomeDir(context).absolutePath }
    }

    suspend fun refreshWorkspaceDiff() {
        val dir = resolveWorkingDirectory()
        val diffRes = LocalTerminalBridge.instance.executeCommand("cd '$dir' && git diff --stat 2>/dev/null || true")
        _lastWorkspaceDiff.value = diffRes.output.trim()
    }

    override fun startNewChat() {
        store.createNewConversation(
            title = "Codex Task ${conversations.value.size + 1}",
            workingDirectory = resolveWorkingDirectory(),
            modelId = preferences.state.value.model
        )
    }

    override fun selectConversation(id: String) {
        store.selectConversation(id)
    }

    override fun sendMessage(content: String) {
        if (content.isBlank()) return
        val cfg = preferences.state.value
        val conv = store.ensureActiveConversation(defaultModel = cfg.model)
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
        val workDir = resolveWorkingDirectory()
        val cleanPrompt = content.replace("'", "'\\''")

        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                val cmd = "cd '$workDir' && codex --model ${cfg.model} --approval-mode ${cfg.approvalPolicy.cliFlag} -q '$cleanPrompt' 2>&1"
                LocalTerminalBridge.instance.executeStreamingCommand(
                    command = cmd,
                    workingDir = workDir,
                    timeoutMs = 240_000L
                ) { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                        runCatching {
                            val json = JSONObject(trimmed)
                            when (json.optString("type")) {
                                "reasoning", "thinking" -> {
                                    val summary = json.optString("text", json.optString("summary"))
                                    if (summary.isNotBlank()) {
                                        store.upsertStreamingAssistantMessage(
                                            conversationId = conv.id,
                                            messageId = assistantMsgId,
                                            thoughtDelta = summary + "\n",
                                            modelUsed = cfg.model,
                                            isStreaming = true
                                        )
                                    }
                                }
                                "command_execution", "patch_apply" -> {
                                    val commandStr = json.optString("command", json.optString("file_path", "workspace edit"))
                                    val tc = ToolCall(
                                        id = json.optString("id", UUID.randomUUID().toString()),
                                        name = json.optString("type", "CodexTool"),
                                        status = ToolCallStatus.SUCCESS,
                                        command = commandStr
                                    )
                                    store.upsertStreamingAssistantMessage(
                                        conversationId = conv.id,
                                        messageId = assistantMsgId,
                                        toolCall = tc,
                                        modelUsed = cfg.model,
                                        isStreaming = true
                                    )
                                }
                                else -> {
                                    val textChunk = json.optString("content", json.optString("text", trimmed))
                                    store.upsertStreamingAssistantMessage(
                                        conversationId = conv.id,
                                        messageId = assistantMsgId,
                                        textDelta = textChunk + "\n",
                                        modelUsed = cfg.model,
                                        isStreaming = true
                                    )
                                }
                            }
                        }.onFailure {
                            store.upsertStreamingAssistantMessage(
                                conversationId = conv.id,
                                messageId = assistantMsgId,
                                textDelta = line + "\n",
                                modelUsed = cfg.model,
                                isStreaming = true
                            )
                        }
                    } else {
                        store.upsertStreamingAssistantMessage(
                            conversationId = conv.id,
                            messageId = assistantMsgId,
                            textDelta = line + "\n",
                            modelUsed = cfg.model,
                            isStreaming = true
                        )
                    }
                }

                store.updateMessage(conv.id, assistantMsgId) { it.copy(isStreaming = false) }
                refreshWorkspaceDiff()
            } catch (e: Exception) {
                store.setError("Codex error: ${e.localizedMessage ?: "Failed to run codex CLI"}")
            } finally {
                store.setStreaming(false)
            }
        }
    }

    override fun stopStreaming() {
        activeJob?.cancel()
        activeJob = null
        scope.launch {
            LocalTerminalBridge.instance.executeCommand("pkill -f 'codex' 2>/dev/null || true")
        }
        store.setStreaming(false)
    }

    override fun deleteConversation(id: String) {
        store.deleteConversation(id)
    }

    override fun forkConversation(id: String) {
        store.forkConversation(id)
    }

    override fun retryConnections() {
        scope.launch { refreshWorkspaceDiff() }
    }

    override fun onAppForegrounded() {
        scope.launch { refreshWorkspaceDiff() }
    }

    override fun resolveApproval(requestId: String, allow: Boolean, alwaysAllowForSession: Boolean) {
        store.setPendingApproval(null)
    }
}
