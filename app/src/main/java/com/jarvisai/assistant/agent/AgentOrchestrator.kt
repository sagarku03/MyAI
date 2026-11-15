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
import kotlinx.coroutines.flow.first
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

    fun clearCompletedTasks() {
        _tasks.value = _tasks.value.filter {
            it.status == AgentTaskStatus.RUNNING || it.status == AgentTaskStatus.WAITING_FOR_APPROVAL
        }
    }

    fun dismissTask(taskId: String) {
        _tasks.value = _tasks.value.filterNot { it.id == taskId }
    }

    suspend fun dispatchTask(
        prompt: String,
        workingDirectory: String? = null,
        agentType: String = "claude"
    ): AgentTask {
        val ctx = appContext
        val resolvedDir = if (ctx != null) {
            LocalEnvironmentManager.resolveWritableWorkingDir(ctx, workingDirectory).absolutePath
        } else {
            workingDirectory ?: "/data/user/0/com.jarvisai.assistant/files/home"
        }

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
                val normalizedType = agentType.lowercase()
                val isClaude = normalizedType == "claude" || normalizedType == "claude_code"
                val isOpenCode = normalizedType == "opencode"

                val cliBinary = if (ctx != null && (isClaude || isOpenCode)) {
                    LocalEnvironmentManager.findCliBinary(ctx, if (isOpenCode) "opencode" else "claude")
                } else {
                    null
                }

                // If external claude/opencode CLI is NOT installed locally, execute cleanly via built-in Gemini Coding Fallback
                if ((isClaude || isOpenCode) && cliBinary == null && ctx != null) {
                    executeBuiltInGeminiCodingFallback(ctx, task.id, prompt, resolvedDir, agentType)
                    return@launch
                }

                val cleanPrompt = prompt.replace("'", "\\'")
                val execCommand = when {
                    isClaude && cliBinary != null -> "'${cliBinary.absolutePath}' -p '$cleanPrompt' --dangerously-skip-permissions 2>&1 || '${cliBinary.absolutePath}' '$cleanPrompt' 2>&1"
                    isOpenCode && cliBinary != null -> "'${cliBinary.absolutePath}' run '$cleanPrompt' 2>&1"
                    else -> cleanPrompt
                }

                var currentStepCount = 1
                val buffer = StringBuilder()

                val result = LocalTerminalBridge.instance.executeStreamingCommand(
                    command = execCommand,
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
                        updateTaskLog(task.id, buffer.toString().takeLast(800))
                    }
                }

                val finalStatus = if (result.exitCode == 0) AgentTaskStatus.COMPLETED else AgentTaskStatus.FAILED
                val finalOutput = buffer.toString().ifBlank { result.output }
                updateTaskStatus(
                    taskId = task.id,
                    status = finalStatus,
                    tool = if (result.exitCode == 0) "COMPLETED" else "FAILED",
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

    private suspend fun executeBuiltInGeminiCodingFallback(
        ctx: Context,
        taskId: String,
        prompt: String,
        resolvedDir: String,
        agentType: String
    ) {
        updateTaskStatus(
            taskId = taskId,
            status = AgentTaskStatus.RUNNING,
            tool = "IN_APP_CODEGEN",
            summary = "External '$agentType' CLI not installed; using Jarvis Built-In Autonomous Code Engine in $resolvedDir...",
            step = 2
        )

        val keyManager = com.jarvisai.assistant.security.SecureApiKeyManager(ctx)
        val apiKey = keyManager.getActiveApiKeySync().trim()
        if (apiKey.isBlank()) {
            updateTaskStatus(
                taskId = taskId,
                status = AgentTaskStatus.FAILED,
                tool = "CONFIG_REQUIRED",
                summary = "External '$agentType' CLI binary installed nahi hai aur Built-In Gemini API Key bhi set nahi hai. Kripya AI Studio API Key add karein ya Terminal mein '$agentType' install karein.",
                step = 10
            )
            return
        }

        val prefs = com.jarvisai.assistant.data.preferences.UserPreferencesRepository(ctx)
        val preferredModel = runCatching {
            prefs.userSettingsFlow.first().textModel
        }.getOrDefault(com.jarvisai.assistant.model.SupportedGeminiModels.DEFAULT_TEXT_MODEL)

        val systemInstruction = """
            You are Jarvis Autonomous Coding Subagent. Generate clean, complete, production-ready code files for the user's request.
            Respond STRICTLY in valid JSON only (no markdown fences outside JSON):
            {
              "files": [
                { "path": "relative/filename.ext", "content": "full file content" }
              ],
              "summary": "short 1-2 line Hinglish/English summary of created files"
            }
        """.trimIndent()

        updateTaskStatus(
            taskId = taskId,
            status = AgentTaskStatus.RUNNING,
            tool = "GENERATING_CODE",
            summary = "Synthesizing code with $preferredModel...",
            step = 5
        )

        val genResult = com.jarvisai.assistant.data.network.GeminiApiService().generateCodeResponse(
            apiKey = apiKey,
            modelId = preferredModel,
            systemPrompt = systemInstruction,
            userPrompt = "Workspace: $resolvedDir\nTask: $prompt"
        )

        if (genResult.isFailure) {
            updateTaskStatus(
                taskId = taskId,
                status = AgentTaskStatus.FAILED,
                tool = "ERROR",
                summary = "Built-In Code Engine failed: ${genResult.exceptionOrNull()?.localizedMessage ?: "unknown error"}",
                step = 10
            )
            return
        }

        val rawResponse = genResult.getOrNull().orEmpty().trim()
        val writtenFiles = mutableListOf<String>()
        var summaryText = "Code generated successfully."

        runCatching {
            val cleanedJson = rawResponse
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val startIdx = cleanedJson.indexOf('{')
            val endIdx = cleanedJson.lastIndexOf('}')
            val jsonSlice = if (startIdx >= 0 && endIdx > startIdx) {
                cleanedJson.substring(startIdx, endIdx + 1)
            } else {
                cleanedJson
            }
            val root = org.json.JSONObject(jsonSlice)
            val filesArr = root.optJSONArray("files")
            summaryText = root.optString("summary", summaryText).ifBlank { summaryText }
            if (filesArr != null) {
                val baseDir = java.io.File(resolvedDir).apply { mkdirs() }
                for (i in 0 until filesArr.length()) {
                    val fObj = filesArr.optJSONObject(i) ?: continue
                    val relPath = fObj.optString("path", "").trim().removePrefix("/")
                    val content = fObj.optString("content", "")
                    if (relPath.isNotBlank() && content.isNotBlank()) {
                        val outFile = java.io.File(baseDir, relPath)
                        outFile.parentFile?.mkdirs()
                        outFile.writeText(content)
                        writtenFiles.add(outFile.absolutePath)
                    }
                }
            }
        }

        if (writtenFiles.isEmpty()) {
            val fallbackName = if (prompt.contains("html", ignoreCase = true) || prompt.contains("page", ignoreCase = true)) {
                "index.html"
            } else {
                "output_${System.currentTimeMillis() % 10000}.txt"
            }
            val cleanedCode = rawResponse
                .replace(Regex("^```[a-zA-Z0-9_-]*\\s*"), "")
                .replace(Regex("```\\s*$"), "")
                .trim()
            if (cleanedCode.isNotBlank()) {
                val outFile = java.io.File(java.io.File(resolvedDir).apply { mkdirs() }, fallbackName)
                outFile.writeText(cleanedCode)
                writtenFiles.add(outFile.absolutePath)
            }
        }

        if (writtenFiles.isNotEmpty()) {
            com.jarvisai.assistant.terminal.WorkspaceSyncManager.notifyExternalChange(resolvedDir)
            com.jarvisai.assistant.terminal.WorkspaceSyncManager.requestOpenFileInIde(writtenFiles.first())
            updateTaskStatus(
                taskId = taskId,
                status = AgentTaskStatus.COMPLETED,
                tool = "COMPLETED",
                summary = "✓ $summaryText\nSaved file(s):\n${writtenFiles.joinToString("\n") { "• $it" }}",
                step = 10
            )
            JarvisOverlayHudService.updateAgentProgress(
                isBusy = false,
                headline = "JARVIS ONLINE",
                detail = "Created ${writtenFiles.size} file(s) in workspace"
            )
        } else {
            updateTaskStatus(
                taskId = taskId,
                status = AgentTaskStatus.FAILED,
                tool = "ERROR",
                summary = "No output files were produced by the coding engine.",
                step = 10
            )
        }
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
