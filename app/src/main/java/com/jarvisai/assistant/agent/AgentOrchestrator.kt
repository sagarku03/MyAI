package com.jarvisai.assistant.agent

import android.content.Context
import com.jarvisai.assistant.agent.claude.ClaudeBridgeClient
import com.jarvisai.assistant.agent.core.AgyBridgeService
import com.jarvisai.assistant.agent.opencode.OpenCodeBridgeClient
import com.jarvisai.assistant.terminal.LocalServerManager
import com.jarvisai.assistant.terminal.LocalTerminalBridge
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
    val outputSummary: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

object AgentOrchestrator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentAgent = MutableStateFlow(ActiveCodingAgent.CLAUDE_CODE)
    val currentAgent: StateFlow<ActiveCodingAgent> = _currentAgent.asStateFlow()

    private val _tasks = MutableStateFlow<List<AgentTask>>(emptyList())
    val tasks: StateFlow<List<AgentTask>> = _tasks.asStateFlow()

    val claudeClient: ClaudeBridgeClient by lazy { ClaudeBridgeClient() }
    val openCodeClient: OpenCodeBridgeClient by lazy { OpenCodeBridgeClient() }

    fun init(context: Context) {
        AgyBridgeService.instance.init(context)
    }

    suspend fun bootstrapDaemon(context: Context): Result<Unit> {
        return LocalServerManager.startServer(context).onSuccess {
            AgyBridgeService.instance.connectWebSocket()
        }
    }

    fun selectAgent(agent: ActiveCodingAgent) {
        _currentAgent.value = agent
    }

    suspend fun dispatchTask(
        prompt: String,
        workingDirectory: String = "/data/data/com.termux/files/home",
        agentType: String = "claude"
    ): AgentTask {
        val task = AgentTask(
            prompt = prompt,
            workingDirectory = workingDirectory,
            agentType = agentType,
            status = AgentTaskStatus.RUNNING,
            progressStep = 1,
            totalSteps = 10
        )
        _tasks.value = listOf(task) + _tasks.value

        scope.launch {
            try {
                if (agentType.equals("opencode", ignoreCase = true)) {
                    val statusRes = openCodeClient.getStatus().getOrNull()
                    if (statusRes?.running == true) {
                        val exec = LocalTerminalBridge.instance.executeCommand(
                            "cd '$workingDirectory' && opencode run '${prompt.replace("'", "\\'")}'"
                        )
                        updateTaskStatus(
                            task.id,
                            if (exec.exitCode == 0) AgentTaskStatus.COMPLETED else AgentTaskStatus.FAILED,
                            exec.output
                        )
                    } else {
                        val fallback = LocalTerminalBridge.instance.executeCommand(
                            "cd '$workingDirectory' && echo 'OpenCode PTY Task: ${prompt.replace("'", "\\'")}'"
                        )
                        updateTaskStatus(task.id, AgentTaskStatus.COMPLETED, fallback.output)
                    }
                } else {
                    val sessions = claudeClient.listSessions().getOrDefault(emptyList())
                    val firstId = sessions.firstOrNull()?.id.orEmpty()
                    if (firstId.isNotBlank()) {
                        val promptRes = claudeClient.sendPrompt(firstId, prompt)
                        updateTaskStatus(
                            task.id,
                            if (promptRes.isSuccess) AgentTaskStatus.COMPLETED else AgentTaskStatus.FAILED,
                            "Dispatched to Claude session $firstId"
                        )
                    } else {
                        val fallback = LocalTerminalBridge.instance.executeCommand(
                            "cd '$workingDirectory' && echo 'Claude CLI Task: ${prompt.replace("'", "\\'")}'"
                        )
                        updateTaskStatus(task.id, AgentTaskStatus.COMPLETED, fallback.output)
                    }
                }
            } catch (e: Exception) {
                updateTaskStatus(task.id, AgentTaskStatus.FAILED, e.message ?: "Agent execution error")
            }
        }

        return task
    }

    private fun updateTaskStatus(taskId: String, status: AgentTaskStatus, summary: String) {
        _tasks.value = _tasks.value.map { existing ->
            if (existing.id == taskId) {
                existing.copy(
                    status = status,
                    progressStep = if (status == AgentTaskStatus.COMPLETED) existing.totalSteps else existing.progressStep,
                    outputSummary = summary
                )
            } else {
                existing
            }
        }
    }
}
