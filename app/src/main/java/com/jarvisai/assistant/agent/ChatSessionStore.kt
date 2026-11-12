package com.jarvisai.assistant.agent

import com.jarvisai.assistant.model.ToolCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe reactive memory blackboard managing conversations, message histories,
 * streaming deltas, tool call cards, error states, and pending tool approvals
 * for each [AgentChatBackend].
 */
class ChatSessionStore(
    val agentKind: AgentKind,
    private val defaultWorkingDir: String = "/data/data/com.termux/files/home"
) {
    private val messagesByConversation = ConcurrentHashMap<String, List<ChatMessage>>()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    val currentConversation: StateFlow<Conversation?> = _currentConversation.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _isLoadingConversation = MutableStateFlow(false)
    val isLoadingConversation: StateFlow<Boolean> = _isLoadingConversation.asStateFlow()

    private val _conversationError = MutableStateFlow<String?>(null)
    val conversationError: StateFlow<String?> = _conversationError.asStateFlow()

    private val _pendingApproval = MutableStateFlow<AgentApprovalRequest?>(null)
    val pendingApproval: StateFlow<AgentApprovalRequest?> = _pendingApproval.asStateFlow()

    fun ensureActiveConversation(defaultModel: String = ""): Conversation {
        val current = _currentConversation.value
        if (current != null) return current
        return createNewConversation(title = "New ${agentKind.displayName} Session", modelId = defaultModel)
    }

    fun createNewConversation(
        title: String = "New ${agentKind.displayName} Session",
        workingDirectory: String = defaultWorkingDir,
        modelId: String = ""
    ): Conversation {
        val conv = Conversation(
            id = UUID.randomUUID().toString(),
            title = title,
            agentKind = agentKind,
            workingDirectory = workingDirectory,
            modelId = modelId
        )
        messagesByConversation[conv.id] = emptyList()
        _conversations.update { listOf(conv) + it }
        _currentConversation.value = conv
        _messages.value = emptyList()
        _conversationError.value = null
        return conv
    }

    fun selectConversation(conversationId: String) {
        val target = _conversations.value.firstOrNull { it.id == conversationId } ?: return
        _currentConversation.value = target
        _messages.value = messagesByConversation[conversationId].orEmpty()
        _conversationError.value = null
    }

    fun setConversations(list: List<Conversation>) {
        _conversations.value = list
        val activeId = _currentConversation.value?.id
        if (activeId != null) {
            _currentConversation.value = list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
        } else if (list.isNotEmpty()) {
            _currentConversation.value = list.first()
        }
    }

    fun appendMessage(message: ChatMessage) {
        val convId = message.conversationId
        val updatedList = messagesByConversation[convId].orEmpty() + message
        messagesByConversation[convId] = updatedList

        if (_currentConversation.value?.id == convId) {
            _messages.value = updatedList
        }

        // Auto-title conversation from first user prompt
        _conversations.update { convs ->
            convs.map { c ->
                if (c.id == convId) {
                    val newTitle = if (c.messageCount == 0 && message.role == "user" && message.content.isNotBlank()) {
                        message.content.lines().first().take(48)
                    } else {
                        c.title
                    }
                    c.copy(
                        title = newTitle,
                        updatedAtMs = System.currentTimeMillis(),
                        messageCount = updatedList.size
                    ).also { updatedConv ->
                        if (_currentConversation.value?.id == convId) {
                            _currentConversation.value = updatedConv
                        }
                    }
                } else {
                    c
                }
            }
        }
    }

    fun updateMessage(
        conversationId: String,
        messageId: String,
        transform: (ChatMessage) -> ChatMessage
    ) {
        val existing = messagesByConversation[conversationId].orEmpty()
        val updated = existing.map { msg ->
            if (msg.id == messageId) transform(msg) else msg
        }
        messagesByConversation[conversationId] = updated
        if (_currentConversation.value?.id == conversationId) {
            _messages.value = updated
        }
    }

    fun upsertStreamingAssistantMessage(
        conversationId: String,
        messageId: String,
        textDelta: String? = null,
        fullText: String? = null,
        thoughtDelta: String? = null,
        fullThought: String? = null,
        thoughtDurationMs: Long? = null,
        toolCall: ToolCall? = null,
        modelUsed: String? = null,
        inputTokens: Int? = null,
        outputTokens: Int? = null,
        cachedTokens: Int? = null,
        costUsd: Double? = null,
        isStreaming: Boolean = true
    ) {
        val existingList = messagesByConversation[conversationId].orEmpty()
        val index = existingList.indexOfFirst { it.id == messageId }

        val updatedList = if (index == -1) {
            val created = ChatMessage(
                id = messageId,
                conversationId = conversationId,
                role = "assistant",
                content = fullText ?: textDelta.orEmpty(),
                thoughtText = fullThought ?: thoughtDelta,
                thoughtDurationMs = thoughtDurationMs ?: 0L,
                toolCalls = if (toolCall != null) listOf(toolCall) else emptyList(),
                modelUsed = modelUsed,
                inputTokens = inputTokens ?: 0,
                outputTokens = outputTokens ?: 0,
                cachedTokens = cachedTokens ?: 0,
                costUsd = costUsd ?: 0.0,
                isStreaming = isStreaming
            )
            existingList + created
        } else {
            existingList.mapIndexed { i, current ->
                if (i != index) current
                else {
                    val mergedTools = if (toolCall != null) {
                        val existingToolIdx = current.toolCalls.indexOfFirst { it.id == toolCall.id }
                        if (existingToolIdx == -1) current.toolCalls + toolCall
                        else current.toolCalls.toMutableList().apply { set(existingToolIdx, toolCall) }
                    } else {
                        current.toolCalls
                    }
                    current.copy(
                        content = fullText ?: (current.content + textDelta.orEmpty()),
                        thoughtText = fullThought ?: ((current.thoughtText.orEmpty()) + thoughtDelta.orEmpty()).ifBlank { null },
                        thoughtDurationMs = thoughtDurationMs ?: current.thoughtDurationMs,
                        toolCalls = mergedTools,
                        modelUsed = modelUsed ?: current.modelUsed,
                        inputTokens = inputTokens ?: current.inputTokens,
                        outputTokens = outputTokens ?: current.outputTokens,
                        cachedTokens = cachedTokens ?: current.cachedTokens,
                        costUsd = costUsd ?: current.costUsd,
                        isStreaming = isStreaming
                    )
                }
            }
        }

        messagesByConversation[conversationId] = updatedList
        if (_currentConversation.value?.id == conversationId) {
            _messages.value = updatedList
        }
    }

    fun deleteConversation(conversationId: String) {
        messagesByConversation.remove(conversationId)
        _conversations.update { list -> list.filterNot { it.id == conversationId } }
        if (_currentConversation.value?.id == conversationId) {
            val next = _conversations.value.firstOrNull()
            _currentConversation.value = next
            _messages.value = if (next != null) messagesByConversation[next.id].orEmpty() else emptyList()
        }
    }

    fun forkConversation(conversationId: String): Conversation? {
        val source = _conversations.value.firstOrNull { it.id == conversationId } ?: return null
        val sourceMessages = messagesByConversation[conversationId].orEmpty()
        val forkedId = UUID.randomUUID().toString()
        val forkedConv = source.copy(
            id = forkedId,
            title = "${source.title} (Fork)",
            createdAtMs = System.currentTimeMillis(),
            updatedAtMs = System.currentTimeMillis()
        )
        val clonedMessages = sourceMessages.map {
            it.copy(id = UUID.randomUUID().toString(), conversationId = forkedId)
        }
        messagesByConversation[forkedId] = clonedMessages
        _conversations.update { listOf(forkedConv) + it }
        _currentConversation.value = forkedConv
        _messages.value = clonedMessages
        return forkedConv
    }

    fun setStreaming(streaming: Boolean) {
        _isStreaming.value = streaming
    }

    fun setLoadingConversation(loading: Boolean) {
        _isLoadingConversation.value = loading
    }

    fun setError(error: String?) {
        _conversationError.value = error
    }

    fun setPendingApproval(request: AgentApprovalRequest?) {
        _pendingApproval.value = request
    }
}
