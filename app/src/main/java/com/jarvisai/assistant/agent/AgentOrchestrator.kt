package com.jarvisai.assistant.agent

import android.content.Context
import com.jarvisai.assistant.agent.claude.ClaudeBridgeClient
import com.jarvisai.assistant.agent.core.AgentTrajectoryParser
import com.jarvisai.assistant.agent.core.AgyBridgeService
import com.jarvisai.assistant.agent.opencode.OpenCodeBridgeClient
import com.jarvisai.assistant.core.SuperJarvisOrchestrator
import com.jarvisai.assistant.service.JarvisOverlayHudService
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.LocalServerManager
import com.jarvisai.assistant.terminal.LocalTerminalBridge
import com.jarvisai.assistant.ui.claude.ClaudePendingCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class ActiveCodingAgent {
    CLAUDE_CODE,
    OPEN_CODE,
    GEMINI_DIRECT,
    ANTIGRAVITY_GRPC
}

enum class AgentTaskStatus {
    QUEUED,
    RUNNING,
    WAITING_FOR_APPROVAL,
    COMPLETED,
    FAILED
}

data class AgentTask(
    val id: String = UUID.randomUUID().toString().substring(0, 8),
    val prompt: String,
    val workingDirectory: String,
    val agentType: String,
    val status: AgentTaskStatus = AgentTaskStatus.RUNNING,
    val progressStep: Int = 1,
    val totalSteps: Int = 10,
    val activeTool: String = "",
    val outputSummary: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

object AgentOrchestrator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null

    private val _currentAgent = MutableStateFlow(ActiveCodingAgent.CLAUDE_CODE)
    val currentAgent: StateFlow<ActiveCodingAgent> = _currentAgent.asStateFlow()

    private val _tasks = MutableStateFlow<List<AgentTask>>(emptyList())
    val tasks: StateFlow<List<AgentTask>> = _tasks.asStateFlow()

    private val _pendingInteractiveApproval = MutableStateFlow<ClaudePendingCommand?>(null)
    val pendingInteractiveApproval: StateFlow<ClaudePendingCommand?> = _pendingInteractiveApproval.asStateFlow()

    private var activeApprovalDeferred: CompletableDeferred<Boolean>? = null

    val claudeClient: ClaudeBridgeClient by lazy { ClaudeBridgeClient() }
    val openCodeClient: OpenCodeBridgeClient by lazy { OpenCodeBridgeClient() }

    private val _activeAgentKind = MutableStateFlow(AgentKind.CLAUDE)
    val activeAgentKind: StateFlow<AgentKind> = _activeAgentKind.asStateFlow()

    private val backends = mutableMapOf<AgentKind, AgentChatBackend>()

    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        AgyBridgeService.instance.init(appCtx)
        LocalTerminalBridge.instance.init(appCtx)
        if (backends.isEmpty()) {
            backends[AgentKind.AGY] = com.jarvisai.assistant.agent.agy.AgyChatBackend(appCtx)
            backends[AgentKind.CLAUDE] = com.jarvisai.assistant.agent.claude.ClaudeChatBackend(appCtx)
            backends[AgentKind.CODEX] = com.jarvisai.assistant.agent.codex.CodexChatBackend(appCtx)
            backends[AgentKind.OPENCODE] = com.jarvisai.assistant.agent.opencode.OpenCodeChatBackend(appCtx)
        }
    }

    fun getBackend(kind: AgentKind): AgentChatBackend? = backends[kind]

    fun selectAgentKind(kind: AgentKind) {
        _activeAgentKind.value = kind
        _currentAgent.value = when (kind) {
            AgentKind.CLAUDE -> ActiveCodingAgent.CLAUDE_CODE
            AgentKind.OPENCODE -> ActiveCodingAgent.OPEN_CODE
            AgentKind.AGY -> ActiveCodingAgent.ANTIGRAVITY_GRPC
            AgentKind.CODEX -> ActiveCodingAgent.OPEN_CODE
        }
    }

    suspend fun bootstrapDaemon(context: Context): Result<Unit> {
        return LocalServerManager.startServer(context).onSuccess {
            AgyBridgeService.instance.connectWebSocket()
        }
    }

    fun selectAgent(agent: ActiveCodingAgent) {
        _currentAgent.value = agent
    }

    /**
     * Called by UI or Voice when user allows or denies a destructive tool.
     */
    fun resolvePendingApproval(approved: Boolean) {
        activeApprovalDeferred?.complete(approved)
        _pendingInteractiveApproval.value = null
    }

    suspend fun dispatchTask(
        prompt: String,
        workingDirectory: String? = null,
        agentType: String = "claude"
    ): AgentTask {
        val ctx = appContext
        val defaultHome = if (ctx != null) LocalEnvironmentManager.getHomeDir(ctx).absolutePath else "/data/data/com.termux/files/home"
        val resolvedDir = workingDirectory ?: defaultHome

        val task = AgentTask(
            prompt = prompt,
            workingDirectory = resolvedDir,
            agentType = agentType,
            status = AgentTaskStatus.RUNNING,
            progressStep = 1,
            totalSteps = 10,
            activeTool = "INITIALIZING",
            outputSummary = "Booting $agentType subagent in $resolvedDir..."
        )
        _tasks.value = listOf(task) + _tasks.value

        scope.launch {
            try {
                // Determine execution command: if claude CLI binary exists, use it, else emulate via terminal bridge
                val cleanPrompt = prompt.replace("'", "\\'")
                val execCommand = when (agentType.lowercase()) {
                    "claude", "claude_code" -> "claude -p '$cleanPrompt' --dangerously-skip-permissions 2>&1 || claude '$cleanPrompt' 2>&1"
                    "opencode" -> "opencode run '$cleanPrompt' 2>&1"
                    else -> "bash -c '$cleanPrompt'"
                }

                var currentStepCount = 1
                val buffer = StringBuilder()

                val result = LocalTerminalBridge.instance.executeStreamingCommand(
                    command = "cd '$resolvedDir' && $execCommand",
                    workingDir = resolvedDir,
                    timeoutMs = 180000
                ) { rawLine ->
                    buffer.appendLine(rawLine)

                    // Trajectory inspection
                    val stepInfo = AgentTrajectoryParser.parseLine(rawLine, currentStepCount)
                    if (stepInfo != null) {
                        currentStepCount = stepInfo.stepNumber

                        // Safety Interceptor: If destructive, pause and request user authorization
                        if (stepInfo.isDestructive) {
                            val deferred = CompletableDeferred<Boolean>()
                            activeApprovalDeferred = deferred
                            val approvalCmd = ClaudePendingCommand(
                                id = "approval_${task.id}_${System.currentTimeMillis()}",
                                command = stepInfo.target.ifBlank { rawLine },
                                workingDir = resolvedDir,
                                riskLevel = "HIGH"
                            )
                            _pendingInteractiveApproval.value = approvalCmd
                            updateTaskStatus(
                                taskId = task.id,
                                status = AgentTaskStatus.WAITING_FOR_APPROVAL,
                                tool = "SECURITY_GATE",
                                summary = "Waiting for authorization to execute: ${stepInfo.target}",
                                step = currentStepCount
                            )

                            // Also notify central orchestrator for live voice approval
                            ctx?.let { appCtx ->
                                scope.launch {
                                    val approved = SuperJarvisOrchestrator.getInstance(appCtx).requestVoiceApproval(
                                        agentName = agentType.uppercase(),
                                        toolName = stepInfo.toolName,
                                        toolDetails = stepInfo.target
                                    )
                                    resolvePendingApproval(approved)
                                }
                            }
                        } else {
                            updateTaskStatus(
                                taskId = task.id,
                                status = AgentTaskStatus.RUNNING,
                                tool = stepInfo.toolName,
                                summary = stepInfo.summary,
                                step = currentStepCount
                            )

                            JarvisOverlayHudService.updateAgentProgress(
                                isBusy = true,
                                headline = "${agentType.uppercase()} STEP $currentStepCount",
                                detail = stepInfo.summary.take(40)
                            )
                        }
                    } else {
                        // Incremental update of output summary
                        updateTaskLog(task.id, buffer.toString().takeLast(800))
                    }
                }

                val finalStatus = if (result.exitCode == 0) AgentTaskStatus.COMPLETED else AgentTaskStatus.FAILED
                val finalOutput = buffer.toString().ifBlank { result.output }
                updateTaskStatus(
                    taskId = task.id,
                    status = finalStatus,
                    tool = "COMPLETED",
                    summary = finalOutput.takeLast(1000),
                    step = maxOf(currentStepCount, 10)
                )

                JarvisOverlayHudService.updateAgentProgress(
                    isBusy = false,
                    headline = "JARVIS ONLINE",
                    detail = "${agentType.uppercase()} task ${if (result.exitCode == 0) "completed" else "finished with status ${result.exitCode}"}"
                )

            } catch (e: Exception) {
                updateTaskStatus(
                    taskId = task.id,
                    status = AgentTaskStatus.FAILED,
                    tool = "ERROR",
                    summary = "Execution failed: ${e.localizedMessage}",
                    step = 10
                )
            }
        }

        return task
    }

    private fun updateTaskStatus(
        taskId: String,
        status: AgentTaskStatus,
        tool: String,
        summary: String,
        step: Int
    ) {
        _tasks.value = _tasks.value.map { existing ->
            if (existing.id == taskId) {
                existing.copy(
                    status = status,
                    activeTool = tool,
                    outputSummary = summary,
                    progressStep = step
                )
            } else {
                existing
            }
        }
    }

    private fun updateTaskLog(taskId: String, logSlice: String) {
        _tasks.value = _tasks.value.map { existing ->
            if (existing.id == taskId) {
                existing.copy(outputSummary = logSlice)
            } else {
                existing
            }
        }
    }
}
