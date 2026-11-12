package com.jarvisai.assistant.agent

import com.jarvisai.assistant.model.ChoiceResponseItem
import com.jarvisai.assistant.model.ToolCall
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Unified conversation metadata model shared across all 4 AgentChatBackend implementations.
 */
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val agentKind: AgentKind,
    val workingDirectory: String = "/data/data/com.termux/files/home",
    val modelId: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val isRunning: Boolean = false
)

/**
 * Unified polymorphic message model supporting streaming text, thinking blocks,
 * subagent runs, tool execution cards, and interactive approval/question prompts.
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: String, // "user", "assistant", "system", "tool"
    val content: String,
    val thoughtText: String? = null,
    val thoughtDurationMs: Long = 0L,
    val toolCalls: List<ToolCall> = emptyList(),
    val modelUsed: String? = null,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cachedTokens: Int = 0,
    val costUsd: Double = 0.0,
    val timestampMs: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val rawPayloadJson: String? = null
)

/**
 * Interactive tool approval request surfaced by any AgentChatBackend (Claude, Codex, OpenCode, Agy).
 */
data class AgentApprovalRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val agentKind: AgentKind,
    val toolName: String,
    val commandOrInput: String,
    val workingDirectory: String = "/data/data/com.termux/files/home",
    val riskLevel: String = "MEDIUM", // LOW, MEDIUM, HIGH
    val description: String = "",
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Polymorphic Multi-Agent Backend Contract (`AGY`, `CLAUDE`, `CODEX`, `OPENCODE`).
 * Each implementation is backed by a dedicated [ChatSessionStore] instance and exposes
 * reactive [StateFlow] streams for seamless Jetpack Compose observation.
 */
interface AgentChatBackend {
    val kind: AgentKind
    val conversations: StateFlow<List<Conversation>>
    val currentConversation: StateFlow<Conversation?>
    val messages: StateFlow<List<ChatMessage>>
    val isStreaming: StateFlow<Boolean>
    val isLoadingConversation: StateFlow<Boolean>
    val conversationError: StateFlow<String?>
    val pendingApproval: StateFlow<AgentApprovalRequest?>

    fun startNewChat()
    fun selectConversation(id: String)
    fun sendMessage(content: String)
    fun stopStreaming()
    fun deleteConversation(id: String)
    fun forkConversation(id: String)
    fun retryConnections()
    fun onAppForegrounded()

    fun resolveApproval(requestId: String, allow: Boolean, alwaysAllowForSession: Boolean = false) {}
    fun submitQuestionAnswers(toolCall: ToolCall, responses: List<ChoiceResponseItem>) {}
}
