package com.jarvisai.assistant.agent.opencode

import android.content.Context
import com.jarvisai.assistant.agent.AgentApprovalRequest
import com.jarvisai.assistant.agent.AgentChatBackend
import com.jarvisai.assistant.agent.AgentKind
import com.jarvisai.assistant.agent.ChatMessage
import com.jarvisai.assistant.agent.ChatSessionStore
import com.jarvisai.assistant.agent.Conversation
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.LocalTerminalBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Phase 3: OpenCode multi-provider CLI & HTTP Bridge Backend implementing [AgentChatBackend].
 */
class OpenCodeChatBackend(
    private val context: Context,
    private val store: ChatSessionStore = ChatSessionStore(
        agentKind = AgentKind.OPENCODE,
        defaultWorkingDir = LocalEnvironmentManager.getHomeDir(context).absolutePath
    )
) : AgentChatBackend {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    val auth = OpenCodeAuth(context)
    val bridgeClient = OpenCodeBridgeClient()

    override val kind: AgentKind = AgentKind.OPENCODE
    override val conversations: StateFlow<List<Conversation>> = store.conversations
    override val currentConversation: StateFlow<Conversation?> = store.currentConversation
    override val messages: StateFlow<List<ChatMessage>> = store.messages
    override val isStreaming: StateFlow<Boolean> = store.isStreaming
    override val isLoadingConversation: StateFlow<Boolean> = store.isLoadingConversation
    override val conversationError: StateFlow<String?> = store.conversationError
    override val pendingApproval: StateFlow<AgentApprovalRequest?> = store.pendingApproval

    init {
        val activeProvider = auth.getActiveProvider()
        store.ensureActiveConversation(defaultModel = "${activeProvider.providerId}/${activeProvider.selectedModel}")
    }

    override fun startNewChat() {
        val activeProvider = auth.getActiveProvider()
        store.createNewConversation(
            title = "OpenCode Session ${conversations.value.size + 1}",
            workingDirectory = LocalEnvironmentManager.getHomeDir(context).absolutePath,
            modelId = "${activeProvider.providerId}/${activeProvider.selectedModel}"
        )
    }

    override fun selectConversation(id: String) {
        store.selectConversation(id)
    }

    override fun sendMessage(content: String) {
        if (content.isBlank()) return
        val activeProvider = auth.getActiveProvider()
        val modelSpec = "${activeProvider.providerId}/${activeProvider.selectedModel}"
        val conv = store.ensureActiveConversation(defaultModel = modelSpec)
        val workDir = conv.workingDirectory.ifBlank { LocalEnvironmentManager.getHomeDir(context).absolutePath }

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
            try {
                val cleanPrompt = content.replace("'", "'\\''")
                val cmd = "cd '$workDir' && opencode run --model '$modelSpec' '$cleanPrompt' 2>&1"
                LocalTerminalBridge.instance.executeStreamingCommand(
                    command = cmd,
                    workingDir = workDir,
                    timeoutMs = 240_000L
                ) { line ->
                    store.upsertStreamingAssistantMessage(
                        conversationId = conv.id,
                        messageId = assistantMsgId,
                        textDelta = line + "\n",
                        modelUsed = modelSpec,
                        isStreaming = true
                    )
                }

                store.updateMessage(conv.id, assistantMsgId) { it.copy(isStreaming = false) }
            } catch (e: Exception) {
                store.setError("OpenCode execution failed: ${e.localizedMessage ?: "Unknown error"}")
            } finally {
                store.setStreaming(false)
            }
        }
    }

    override fun stopStreaming() {
        activeJob?.cancel()
        activeJob = null
        scope.launch {
            LocalTerminalBridge.instance.executeCommand("pkill -f 'opencode run' 2>/dev/null || true")
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
        scope.launch {
            bridgeClient.getStatus()
        }
    }

    override fun onAppForegrounded() {
        scope.launch {
            bridgeClient.getStatus()
        }
    }

    override fun resolveApproval(requestId: String, allow: Boolean, alwaysAllowForSession: Boolean) {
        store.setPendingApproval(null)
    }
}
