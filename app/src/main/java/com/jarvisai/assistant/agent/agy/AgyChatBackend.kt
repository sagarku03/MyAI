package com.jarvisai.assistant.agent.agy

import android.content.Context
import com.jarvisai.assistant.agent.AgentApprovalRequest
import com.jarvisai.assistant.agent.AgentChatBackend
import com.jarvisai.assistant.agent.AgentKind
import com.jarvisai.assistant.agent.ChatMessage
import com.jarvisai.assistant.agent.ChatSessionStore
import com.jarvisai.assistant.agent.Conversation
import com.jarvisai.assistant.agent.core.AgyBridgeService
import com.jarvisai.assistant.data.local.ChatMessageEntity
import com.jarvisai.assistant.data.preferences.AuthPreferences
import com.jarvisai.assistant.data.preferences.UserPreferencesRepository
import com.jarvisai.assistant.data.remote.AntigravityApiService
import com.jarvisai.assistant.data.remote.AntigravityStreamEvent
import com.jarvisai.assistant.data.remote.GoogleOAuthManager
import com.jarvisai.assistant.data.remote.JarvisHubConfig
import com.jarvisai.assistant.model.ChoiceResponseItem
import com.jarvisai.assistant.model.ToolCall
import com.jarvisai.assistant.model.ToolCallStatus
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.LocalServerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Antigravity (`agy --hub`) Backend implementing [AgentChatBackend].
 * Uses [AntigravityApiService] for full-power persistent Cascade sessions on Port 2235
 * (with automatic CloudCode PA 25-turn tool-suite failover), live thinking & tool cards,
 * interactive permissions, and `ask_question` resolution.
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

    private val antigravityApi = AntigravityApiService()
    private val oauthManager = GoogleOAuthManager()
    private val authPreferences by lazy { AuthPreferences.getInstance(context) }
    private val userPrefsRepo by lazy { UserPreferencesRepository(context) }

    @Volatile
    private var pendingApprovalStepIndex: Int? = null

    override val kind: AgentKind = AgentKind.AGY
    override val conversations: StateFlow<List<Conversation>> = store.conversations
    override val currentConversation: StateFlow<Conversation?> = store.currentConversation
    override val messages: StateFlow<List<ChatMessage>> = store.messages
    override val isStreaming: StateFlow<Boolean> = store.isStreaming
    override val isLoadingConversation: StateFlow<Boolean> = store.isLoadingConversation
    override val conversationError: StateFlow<String?> = store.conversationError
    override val pendingApproval: StateFlow<AgentApprovalRequest?> = store.pendingApproval

    init {
        store.ensureActiveConversation(defaultModel = "gemini-3.8-flash")
    }

    override fun startNewChat() {
        store.currentConversation.value?.id?.let { oldId ->
            antigravityApi.resetCascadeSession(oldId)
        }
        store.createNewConversation(
            title = "Agy Cascade ${conversations.value.size + 1}",
            workingDirectory = LocalEnvironmentManager.getHomeDir(context).absolutePath,
            modelId = ""
        )
    }

    override fun selectConversation(id: String) {
        store.selectConversation(id)
    }

    private suspend fun resolveEffectiveModel(conv: Conversation): String {
        val prefsModel = runCatching { userPrefsRepo.getCurrentSettings().textModel }.getOrNull().orEmpty()
        val convModel = conv.modelId.takeIf { it.isNotBlank() && it != "agy-hub" }.orEmpty()
        return prefsModel.ifBlank { convModel }.ifBlank { "gemini-3.8-flash" }
    }

    private suspend fun resolveFreshTokenAndProject(): Pair<String, String> {
        var token = authPreferences.getAccessToken()
        val refreshToken = authPreferences.getRefreshToken()
        var projectId = authPreferences.getProjectId()

        if ((token.isBlank() || authPreferences.isTokenExpired()) && refreshToken.isNotBlank()) {
            val refreshed = oauthManager.refreshToken(refreshToken).getOrNull()
            if (refreshed != null) {
                token = refreshed.accessToken
                authPreferences.saveTokens(
                    accessToken = refreshed.accessToken,
                    refreshToken = refreshed.refreshToken,
                    email = authPreferences.getUserEmail(),
                    name = authPreferences.getUserName(),
                    pictureUrl = authPreferences.getPictureUrl().takeIf { it.isNotBlank() },
                    expiresInSeconds = refreshed.expiresInSeconds
                )
                LocalServerManager.syncAuthPreferencesToAgyDirs(context)
            }
        }
        if (token.isNotBlank() && projectId.isBlank()) {
            val assist = antigravityApi.loadCodeAssist(token).getOrNull()
            if (assist != null) {
                projectId = assist.first
                authPreferences.saveProjectInfo(assist.first, assist.second)
            }
        }
        return token to projectId
    }

    override fun sendMessage(content: String) {
        if (content.isBlank()) return
        val conv = store.ensureActiveConversation(defaultModel = "")
        val historySnapshot = store.messages.value
            .filter { it.conversationId == conv.id && (it.role == "user" || it.role == "assistant" || it.role == "model") && it.content.isNotBlank() }
            .map {
                ChatMessageEntity(
                    sessionId = 0L,
                    role = if (it.role == "assistant") "model" else it.role,
                    content = it.content,
                    timestamp = it.timestampMs,
                    modelUsed = it.modelUsed.orEmpty()
                )
            }

        store.appendMessage(
            ChatMessage(
                conversationId = conv.id,
                role = "user",
                content = content
            )
        )
        store.setError(null)
        store.setPendingApproval(null)
        pendingApprovalStepIndex = null
        store.setStreaming(true)

        val assistantMsgId = UUID.randomUUID().toString()

        activeJob?.cancel()
        activeJob = scope.launch {
            val startTime = System.currentTimeMillis()
            try {
                LocalServerManager.ensureAgyHubAvailable(context)
                val selectedModel = resolveEffectiveModel(conv)
                val (freshToken, freshProjectId) = resolveFreshTokenAndProject()
                val hubServiceUrl = JarvisHubConfig.getLanguageServiceUrl(context)

                antigravityApi.streamGenerateContent(
                    token = freshToken,
                    projectId = freshProjectId,
                    modelId = selectedModel,
                    userPrompt = content,
                    history = historySnapshot,
                    systemInstruction = AntigravityApiService.DEFAULT_SYSTEM_INSTRUCTION,
                    sessionId = conv.id,
                    thinkingBudget = 4096,
                    hubBaseUrl = hubServiceUrl,
                    context = context
                ).collect { event ->
                    when (event) {
                        is AntigravityStreamEvent.ThoughtChunk -> {
                            store.upsertStreamingAssistantMessage(
                                conversationId = conv.id,
                                messageId = assistantMsgId,
                                fullThought = event.thought,
                                thoughtDurationMs = System.currentTimeMillis() - startTime,
                                modelUsed = selectedModel,
                                isStreaming = true
                            )
                        }
                        is AntigravityStreamEvent.ToolCallDetected -> {
                            val detectedTool = ToolCall(
                                id = "detected_${event.name}_${event.command.hashCode()}",
                                name = event.name,
                                command = event.command,
                                status = ToolCallStatus.RUNNING
                            )
                            store.upsertStreamingAssistantMessage(
                                conversationId = conv.id,
                                messageId = assistantMsgId,
                                toolCall = detectedTool,
                                modelUsed = selectedModel,
                                isStreaming = true
                            )
                        }
                        is AntigravityStreamEvent.ToolCallUpdate -> {
                            val tc = event.toolCall
                            if (tc.status == ToolCallStatus.PENDING_APPROVAL && tc.questionnaire == null) {
                                pendingApprovalStepIndex = tc.stepIndex
                                store.setPendingApproval(
                                    AgentApprovalRequest(
                                        requestId = tc.id,
                                        conversationId = conv.id,
                                        agentKind = AgentKind.AGY,
                                        toolName = tc.name,
                                        commandOrInput = tc.command,
                                        workingDirectory = conv.workingDirectory,
                                        riskLevel = "MEDIUM",
                                        description = "Antigravity requested confirmation to run ${tc.name}"
                                    )
                                )
                            }
                            store.upsertStreamingAssistantMessage(
                                conversationId = conv.id,
                                messageId = assistantMsgId,
                                toolCall = tc,
                                modelUsed = selectedModel,
                                isStreaming = true
                            )
                        }
                        is AntigravityStreamEvent.TextChunk -> {
                            store.upsertStreamingAssistantMessage(
                                conversationId = conv.id,
                                messageId = assistantMsgId,
                                fullText = event.text,
                                modelUsed = selectedModel,
                                isStreaming = true
                            )
                        }
                        is AntigravityStreamEvent.Completed -> {
                            store.upsertStreamingAssistantMessage(
                                conversationId = conv.id,
                                messageId = assistantMsgId,
                                fullText = event.fullText,
                                fullThought = event.fullThought.takeIf { it.isNotBlank() },
                                thoughtDurationMs = event.thoughtDurationMs.takeIf { it > 0L }
                                    ?: (System.currentTimeMillis() - startTime),
                                modelUsed = event.modelUsed.ifBlank { selectedModel },
                                outputTokens = event.totalTokens,
                                isStreaming = false
                            )
                            store.setPendingApproval(null)
                            pendingApprovalStepIndex = null
                        }
                        is AntigravityStreamEvent.Error -> {
                            store.setError(event.message)
                            store.updateMessage(conv.id, assistantMsgId) { it.copy(isStreaming = false) }
                        }
                    }
                }

                store.updateMessage(conv.id, assistantMsgId) { it.copy(isStreaming = false) }
            } catch (ce: CancellationException) {
                store.updateMessage(conv.id, assistantMsgId) { it.copy(isStreaming = false) }
                throw ce
            } catch (e: Exception) {
                store.setError("Antigravity stream failed: ${e.localizedMessage ?: "Connection refused"}")
                store.updateMessage(conv.id, assistantMsgId) { it.copy(isStreaming = false) }
            } finally {
                store.setStreaming(false)
            }
        }
    }

    override fun stopStreaming() {
        activeJob?.cancel()
        activeJob = null
        store.setStreaming(false)
        store.setPendingApproval(null)
        scope.launch {
            antigravityApi.cancelActiveCascade(JarvisHubConfig.getLanguageServiceUrl(context))
        }
    }

    override fun deleteConversation(id: String) {
        antigravityApi.resetCascadeSession(id)
        store.deleteConversation(id)
    }

    override fun forkConversation(id: String) {
        store.forkConversation(id)
    }

    override fun retryConnections() {
        scope.launch {
            LocalServerManager.ensureAgyHubAvailable(context)
            LocalServerManager.refreshDiagnostics(context)
            AgyBridgeService.instance.connectWebSocket()
        }
    }

    override fun onAppForegrounded() {
        scope.launch {
            LocalServerManager.ensureAgyHubAvailable(context)
            LocalServerManager.refreshDiagnostics(context)
        }
    }

    override fun resolveApproval(requestId: String, allow: Boolean, alwaysAllowForSession: Boolean) {
        val stepIdx = pendingApprovalStepIndex ?: requestId.removePrefix("step_").toIntOrNull()
        store.setPendingApproval(null)
        pendingApprovalStepIndex = null
        if (stepIdx != null) {
            scope.launch {
                antigravityApi.resolveCascadePermission(
                    stepIndex = stepIdx,
                    allow = allow,
                    hubUrl = JarvisHubConfig.getLanguageServiceUrl(context)
                )
            }
        }
    }

    override fun submitQuestionAnswers(toolCall: ToolCall, responses: List<ChoiceResponseItem>) {
        val convId = store.currentConversation.value?.id
        scope.launch {
            antigravityApi.submitAskQuestionAnswers(
                stepIndex = toolCall.stepIndex,
                responses = responses,
                hubUrl = JarvisHubConfig.getLanguageServiceUrl(context),
                cancelled = responses.isEmpty()
            )
        }
        if (convId != null) {
            val answeredSummary = responses.joinToString("; ") { r ->
                val sel = r.selectedOptionIds.joinToString(", ")
                val extra = r.writeInResponse?.takeIf { it.isNotBlank() }.orEmpty()
                listOf(sel, extra).filter { it.isNotBlank() }.joinToString(" - ")
            }
            val updatedMessages = store.messages.value.map { msg ->
                if (msg.conversationId == convId && msg.toolCalls.any { it.id == toolCall.id }) {
                    msg.copy(
                        toolCalls = msg.toolCalls.map { tc ->
                            if (tc.id == toolCall.id) {
                                tc.copy(
                                    status = ToolCallStatus.SUCCESS,
                                    output = answeredSummary.ifBlank { "Answered" }
                                )
                            } else tc
                        }
                    )
                } else msg
            }
            updatedMessages.find { it.toolCalls.any { tc -> tc.id == toolCall.id } }?.let { updatedMsg ->
                store.updateMessage(convId, updatedMsg.id) { updatedMsg }
            }
        }
    }
}
