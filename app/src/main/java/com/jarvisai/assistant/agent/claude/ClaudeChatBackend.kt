package com.jarvisai.assistant.agent.claude

import android.content.Context
import com.jarvisai.assistant.agent.AgentApprovalRequest
import com.jarvisai.assistant.agent.AgentChatBackend
import com.jarvisai.assistant.agent.AgentKind
import com.jarvisai.assistant.agent.ChatMessage
import com.jarvisai.assistant.agent.ChatSessionStore
import com.jarvisai.assistant.agent.Conversation
import com.jarvisai.assistant.model.ChoiceResponseItem
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
import java.util.UUID

/**
 * Phase 3: Native Claude Code CLI backend implementing [AgentChatBackend] backed by [ChatSessionStore].
 * Supports JSONL streaming (`--output-format stream-json`), subagent hierarchy tracking,
 * thinking token budgets, interactive tool approvals, and multi-account quota tracking.
 */
class ClaudeChatBackend(
    private val context: Context,
    private val store: ChatSessionStore = ChatSessionStore(
        agentKind = AgentKind.CLAUDE,
        defaultWorkingDir = LocalEnvironmentManager.getHomeDir(context).absolutePath
    )
) : AgentChatBackend {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeStreamingJob: Job? = null

    override val kind: AgentKind = AgentKind.CLAUDE
    override val conversations: StateFlow<List<Conversation>> = store.conversations
    override val currentConversation: StateFlow<Conversation?> = store.currentConversation
    override val messages: StateFlow<List<ChatMessage>> = store.messages
    override val isStreaming: StateFlow<Boolean> = store.isStreaming
    override val isLoadingConversation: StateFlow<Boolean> = store.isLoadingConversation
    override val conversationError: StateFlow<String?> = store.conversationError
    override val pendingApproval: StateFlow<AgentApprovalRequest?> = store.pendingApproval

    val cliStatus: StateFlow<ClaudeCliStatus> = ClaudeAccountManager.cliStatus
    val loginState: StateFlow<ClaudeLoginState> = ClaudeAccountManager.loginState
    val settings: StateFlow<ClaudeConfigManager.ClaudeSettingsState> = ClaudeConfigManager.settings
    val rateLimits: StateFlow<ClaudeRateLimits> = ClaudeQuotaGroups.rateLimits

    private val _lastUsageInfo = MutableStateFlow(ClaudeUsageInfo())
    val lastUsageInfo: StateFlow<ClaudeUsageInfo> = _lastUsageInfo.asStateFlow()

    private val _subagentRuns = MutableStateFlow<List<ClaudeSubagentRun>>(emptyList())
    val subagentRuns: StateFlow<List<ClaudeSubagentRun>> = _subagentRuns.asStateFlow()

    init {
        store.ensureActiveConversation(defaultModel = ClaudeConfigManager.settings.value.selectedModel)
        scope.launch {
            ClaudeConfigManager.loadSettings(context)
            ClaudeAccountManager.refreshAuthStatus(context)
        }
    }

    override fun startNewChat() {
        store.createNewConversation(
            title = "Claude Code Session ${conversations.value.size + 1}",
            workingDirectory = LocalEnvironmentManager.getHomeDir(context).absolutePath,
            modelId = ClaudeConfigManager.settings.value.selectedModel
        )
    }

    override fun selectConversation(id: String) {
        store.selectConversation(id)
    }

    override fun sendMessage(content: String) {
        if (content.isBlank()) return
        val conv = store.ensureActiveConversation(defaultModel = ClaudeConfigManager.settings.value.selectedModel)
        val userMsg = ChatMessage(
            conversationId = conv.id,
            role = "user",
            content = content
        )
        store.appendMessage(userMsg)
        store.setError(null)
        store.setStreaming(true)

        val assistantMsgId = UUID.randomUUID().toString()
        val cfg = ClaudeConfigManager.settings.value
        val workDir = conv.workingDirectory.ifBlank { LocalEnvironmentManager.getHomeDir(context).absolutePath }
        val escapedPrompt = content.replace("'", "'\\''")
        val skipPermsFlag = if (cfg.autoApproveBashInSession) "--dangerously-skip-permissions" else ""

        activeStreamingJob?.cancel()
        activeStreamingJob = scope.launch {
            val startTime = System.currentTimeMillis()
            val rawFallback = StringBuilder()
            try {
                val cmd = "cd '$workDir' && claude -p '$escapedPrompt' --model ${cfg.selectedModel} --output-format stream-json $skipPermsFlag 2>&1 || claude -p '$escapedPrompt' 2>&1"
                val execResult = LocalTerminalBridge.instance.executeStreamingCommand(
                    command = cmd,
                    workingDir = workDir,
                    timeoutMs = 240_000L
                ) { line ->
                    val frames = ClaudeProtocolParser.parseJsonFrame(line)
                    if (frames.isNotEmpty()) {
                        frames.forEach { frame ->
                            when (frame) {
                                is ClaudeProtocolParser.ParsedFrame.TextChunk -> {
                                    store.upsertStreamingAssistantMessage(
                                        conversationId = conv.id,
                                        messageId = assistantMsgId,
                                        textDelta = frame.text,
                                        modelUsed = cfg.selectedModel,
                                        isStreaming = true
                                    )
                                }
                                is ClaudeProtocolParser.ParsedFrame.ThinkingChunk -> {
                                    store.upsertStreamingAssistantMessage(
                                        conversationId = conv.id,
                                        messageId = assistantMsgId,
                                        thoughtDelta = frame.thinking,
                                        thoughtDurationMs = System.currentTimeMillis() - startTime,
                                        modelUsed = cfg.selectedModel,
                                        isStreaming = true
                                    )
                                }
                                is ClaudeProtocolParser.ParsedFrame.ToolUseStart -> {
                                    val tc = ToolCall(
                                        id = frame.toolCallId,
                                        name = frame.toolName,
                                        status = ToolCallStatus.RUNNING,
                                        command = frame.inputSummary,
                                        args = frame.rawInputJson
                                    )
                                    store.upsertStreamingAssistantMessage(
                                        conversationId = conv.id,
                                        messageId = assistantMsgId,
                                        toolCall = tc,
                                        modelUsed = cfg.selectedModel,
                                        isStreaming = true
                                    )
                                }
                                is ClaudeProtocolParser.ParsedFrame.ToolUseResult -> {
                                    val tc = ToolCall(
                                        id = frame.toolCallId,
                                        name = "Tool Result",
                                        status = if (frame.isError) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS,
                                        output = frame.output
                                    )
                                    store.upsertStreamingAssistantMessage(
                                        conversationId = conv.id,
                                        messageId = assistantMsgId,
                                        toolCall = tc,
                                        modelUsed = cfg.selectedModel,
                                        isStreaming = true
                                    )
                                }
                                is ClaudeProtocolParser.ParsedFrame.PermissionRequired -> {
                                    store.setPendingApproval(
                                        AgentApprovalRequest(
                                            requestId = frame.toolCallId,
                                            conversationId = conv.id,
                                            agentKind = AgentKind.CLAUDE,
                                            toolName = frame.toolName,
                                            commandOrInput = frame.commandOrInput,
                                            workingDirectory = workDir,
                                            riskLevel = "HIGH",
                                            description = frame.description
                                        )
                                    )
                                }
                                is ClaudeProtocolParser.ParsedFrame.SubagentUpdate -> {
                                    _subagentRuns.value = _subagentRuns.value
                                        .filterNot { it.subagentId == frame.subagent.subagentId } + frame.subagent
                                }
                                is ClaudeProtocolParser.ParsedFrame.TurnResult -> {
                                    _lastUsageInfo.value = frame.usage
                                    ClaudeQuotaGroups.recordTurnUsage(cfg.selectedModel, frame.usage)
                                    store.upsertStreamingAssistantMessage(
                                        conversationId = conv.id,
                                        messageId = assistantMsgId,
                                        fullText = frame.fullText.takeIf { it.isNotBlank() },
                                        modelUsed = cfg.selectedModel,
                                        inputTokens = frame.usage.inputTokens,
                                        outputTokens = frame.usage.outputTokens,
                                        cachedTokens = frame.usage.cacheReadInputTokens,
                                        costUsd = frame.usage.totalCostUsd,
                                        isStreaming = false
                                    )
                                }
                                is ClaudeProtocolParser.ParsedFrame.ErrorFrame -> {
                                    store.setError(frame.error)
                                }
                            }
                        }
                    } else {
                        rawFallback.appendLine(line)
                        store.upsertStreamingAssistantMessage(
                            conversationId = conv.id,
                            messageId = assistantMsgId,
                            textDelta = line + "\n",
                            modelUsed = cfg.selectedModel,
                            isStreaming = true
                        )
                    }
                }

                val existingMsgs = store.messages.value
                val currentMsg = existingMsgs.firstOrNull { it.id == assistantMsgId }
                if (currentMsg == null && (rawFallback.isNotEmpty() || execResult.output.isNotBlank())) {
                    store.upsertStreamingAssistantMessage(
                        conversationId = conv.id,
                        messageId = assistantMsgId,
                        fullText = rawFallback.toString().ifBlank { execResult.output }.trim(),
                        modelUsed = cfg.selectedModel,
                        isStreaming = false
                    )
                } else if (currentMsg != null) {
                    store.updateMessage(conv.id, assistantMsgId) { it.copy(isStreaming = false) }
                }
            } catch (e: Exception) {
                store.setError("Claude execution error: ${e.localizedMessage ?: "Unknown failure"}")
            } finally {
                store.setStreaming(false)
            }
        }
    }

    override fun stopStreaming() {
        activeStreamingJob?.cancel()
        activeStreamingJob = null
        scope.launch {
            LocalTerminalBridge.instance.executeCommand("pkill -f 'claude -p' 2>/dev/null || true")
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
            ClaudeAccountManager.refreshAuthStatus(context)
        }
    }

    override fun onAppForegrounded() {
        scope.launch {
            ClaudeAccountManager.refreshAuthStatus(context)
        }
    }

    override fun resolveApproval(requestId: String, allow: Boolean, alwaysAllowForSession: Boolean) {
        val pending = store.pendingApproval.value
        store.setPendingApproval(null)
        if (alwaysAllowForSession && pending != null) {
            scope.launch {
                ClaudeConfigManager.addAllowedToolRule(context, pending.toolName)
            }
        }
    }

    override fun submitQuestionAnswers(toolCall: ToolCall, responses: List<ChoiceResponseItem>) {
        val formatted = responses.joinToString("\n") { item ->
            val ans = (item.selectedOptionIds + listOfNotNull(item.writeInResponse)).joinToString(", ")
            "${item.question}: $ans"
        }
        sendMessage(formatted)
    }
}
