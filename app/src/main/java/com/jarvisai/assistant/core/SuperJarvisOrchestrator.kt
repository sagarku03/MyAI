package com.jarvisai.assistant.core

import android.content.Context
import android.util.Log
import com.jarvisai.assistant.agent.AgentOrchestrator
import com.jarvisai.assistant.agent.AgentTask
import com.jarvisai.assistant.agent.AgentTaskStatus
import com.jarvisai.assistant.data.local.JarvisDatabase
import com.jarvisai.assistant.data.repository.JarvisRepository
import com.jarvisai.assistant.service.CognitiveMemoryOrchestrator
import com.jarvisai.assistant.service.GeminiLiveVoiceManager
import com.jarvisai.assistant.service.JarvisAccessibilityService
import com.jarvisai.assistant.service.JarvisOverlayHudService
import com.jarvisai.assistant.service.WorkingMemoryBlackboard
import com.jarvisai.assistant.terminal.LocalTerminalBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed class UnifiedTaskDestination {
    data class AndroidSystem(val action: String, val parameters: Map<String, Any>) : UnifiedTaskDestination()
    data class CodingSubagent(val prompt: String, val workingDir: String, val agentType: String) : UnifiedTaskDestination()
    data class TerminalCommand(val command: String, val workingDir: String) : UnifiedTaskDestination()
    data class ScreenPerception(val focusArea: String) : UnifiedTaskDestination()
    data class MemoryQuery(val query: String) : UnifiedTaskDestination()
}

data class PendingVoiceApproval(
    val approvalId: String = UUID.randomUUID().toString(),
    val agentName: String,
    val toolName: String,
    val toolDetails: String,
    val deferredResponse: CompletableDeferred<Boolean>
)

/**
 * SuperJarvisOrchestrator — The Central Cognitive Brain of Jarvis AI Ultra.
 * Unites real-time Gemini Live WebSocket duplex audio, Go daemon Multi-Agent Supervisor,
 * embedded Termux PTY execution, Android Accessibility automation, and 5-tier memory.
 */
class SuperJarvisOrchestrator private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val terminalBridge: LocalTerminalBridge
        get() = LocalTerminalBridge.instance.also { it.init(context) }

    val agentOrchestrator: AgentOrchestrator
        get() = AgentOrchestrator.also { it.init(context) }

    val memoryOrchestrator: CognitiveMemoryOrchestrator by lazy {
        val db = JarvisDatabase.getInstance(context)
        CognitiveMemoryOrchestrator(JarvisRepository(db.jarvisDao()))
    }

    private val _pendingApprovalFlow = MutableStateFlow<PendingVoiceApproval?>(null)
    val pendingApprovalFlow: StateFlow<PendingVoiceApproval?> = _pendingApprovalFlow.asStateFlow()

    private val _orchestratorState = MutableStateFlow("JARVIS ONLINE")
    val orchestratorState: StateFlow<String> = _orchestratorState.asStateFlow()

    private val _activeVoiceIntent = MutableStateFlow("Idle")
    val activeVoiceIntent: StateFlow<String> = _activeVoiceIntent.asStateFlow()

    companion object {
        private const val TAG = "SuperJarvisOrchestrator"

        @Volatile
        private var instance: SuperJarvisOrchestrator? = null

        fun getInstance(context: Context): SuperJarvisOrchestrator {
            return instance ?: synchronized(this) {
                instance ?: SuperJarvisOrchestrator(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        observeSubagentTasks()
    }

    /**
     * Observes background Claude Code and OpenCode tasks and updates the Floating Arc Reactor HUD.
     */
    private fun observeSubagentTasks() {
        scope.launch {
            agentOrchestrator.tasks.collect { taskList ->
                val activeTask = taskList.firstOrNull { it.status == AgentTaskStatus.RUNNING }
                if (activeTask != null) {
                    val progressTicker = "${activeTask.agentType.uppercase()}: ${activeTask.prompt.take(28)} (${activeTask.progressStep}/${activeTask.totalSteps})"
                    _orchestratorState.value = "AGENT BUSY: $progressTicker"
                    _activeVoiceIntent.value = progressTicker
                    JarvisOverlayHudService.updateAgentProgress(
                        isBusy = true,
                        headline = "AGENT AT WORK",
                        detail = progressTicker
                    )
                } else {
                    _orchestratorState.value = "JARVIS STANDBY"
                    _activeVoiceIntent.value = "Idle"
                    JarvisOverlayHudService.updateAgentProgress(
                        isBusy = false,
                        headline = "JARVIS ONLINE",
                        detail = "Tap orb or speak 'Hey Jarvis'"
                    )
                }
            }
        }
    }

    /**
     * Real-Time Voice Intent Classifier:
     * Automatically differentiates between Android OS control (WhatsApp, phone calls, screen clicks,
     * volume, Wi-Fi), Developer/Coding tasks ("Build project", "Fix tests", "Git commit", "Run bash script"),
     * Screen Visual Perception, and 5-Tier Memory Queries.
     */
    fun classifyVoiceIntent(utterance: String): UnifiedTaskDestination {
        val clean = utterance.trim()
        val lower = clean.lowercase()

        return when {
            lower.startsWith("bash ") || lower.startsWith("terminal ") || lower.contains("run command") ||
                lower.contains("git status") || lower.contains("git log") || lower.contains("ls -la") -> {
                val cmd = clean.replace(Regex("(?i)^(bash|terminal|run command)\\s*:?\\s*"), "").ifBlank { "git status -s" }
                _activeVoiceIntent.value = "Terminal: $cmd"
                UnifiedTaskDestination.TerminalCommand(
                    command = cmd,
                    workingDir = "/data/data/com.termux/files/home"
                )
            }
            lower.contains("build project") || lower.contains("fix test") || lower.contains("git commit") ||
                lower.contains("refactor") || lower.contains("claude code") || lower.contains("opencode") ||
                lower.contains("code likho") || lower.contains("script likho") || lower.contains("bug fix") -> {
                val agent = if (lower.contains("opencode")) "opencode" else "claude"
                _activeVoiceIntent.value = "Agent ($agent): ${clean.take(28)}"
                UnifiedTaskDestination.CodingSubagent(
                    prompt = clean,
                    workingDir = "/data/data/com.termux/files/home/projects",
                    agentType = agent
                )
            }
            lower.contains("screen par kya") || lower.contains("what is on screen") || lower.contains("inspect screen") -> {
                _activeVoiceIntent.value = "Screen Perception"
                UnifiedTaskDestination.ScreenPerception(focusArea = "full_screen")
            }
            lower.contains("yaad") || lower.contains("remember") || lower.contains("memory") -> {
                _activeVoiceIntent.value = "Memory Lookup"
                UnifiedTaskDestination.MemoryQuery(query = clean)
            }
            else -> {
                val isA11yActive = JarvisAccessibilityService.isConnectedFlow.value || JarvisAccessibilityService.activeInstance != null
                _activeVoiceIntent.value = "OS Control"
                UnifiedTaskDestination.AndroidSystem(
                    action = "EXECUTE_OS_GOAL",
                    parameters = mapOf("goal" to clean, "accessibilityActive" to isA11yActive)
                )
            }
        }
    }

    /**
     * Delegates a developer task to background Claude Code or OpenCode subagent
     * and persists the task context into 5-Tier Cognitive Memory and WorkingMemoryBlackboard.
     */
    suspend fun delegateCodingTask(
        prompt: String,
        workingDir: String = "/data/data/com.termux/files/home",
        agentType: String = "claude"
    ): AgentTask {
        Log.i(TAG, "Delegating coding task to $agentType: $prompt (dir: $workingDir)")
        val task = agentOrchestrator.dispatchTask(
            prompt = prompt,
            workingDirectory = workingDir,
            agentType = agentType
        )

        WorkingMemoryBlackboard.putScratchpadVariable("last_coding_agent_task_id", task.id)
        WorkingMemoryBlackboard.putScratchpadVariable("last_coding_agent_prompt", prompt)
        WorkingMemoryBlackboard.putScratchpadVariable("last_coding_agent_dir", workingDir)

        scope.launch {
            try {
                memoryOrchestrator.recordInteractionEpisode(
                    userUtterance = "Delegate to $agentType: $prompt",
                    assistantReply = "Dispatched agent task ${task.id} in $workingDir",
                    involvedApp = "Jarvis IDE / $agentType",
                    eventType = "CODING_AGENT_DISPATCH",
                    succeeded = true,
                    importanceValence = 3
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record task in memory: ${e.message}")
            }
        }

        return task
    }

    /**
     * Executes a command directly in the embedded Termux Linux PTY environment
     * and records the output in WorkingMemoryBlackboard & CognitiveMemoryOrchestrator.
     */
    suspend fun executeTerminalCommand(
        command: String,
        workingDir: String = "/data/data/com.termux/files/home"
    ): String {
        Log.i(TAG, "Executing terminal command: $command in $workingDir")
        val fullCommand = "cd '$workingDir' && $command"
        val execResult = terminalBridge.executeCommand(fullCommand)
        val output = execResult.output.ifBlank { "(exit code ${execResult.exitCode})" }

        WorkingMemoryBlackboard.putScratchpadVariable("last_terminal_command", command)
        WorkingMemoryBlackboard.putScratchpadVariable("last_terminal_output", output.take(500))

        scope.launch {
            try {
                memoryOrchestrator.recordInteractionEpisode(
                    userUtterance = "Terminal ($workingDir): $command",
                    assistantReply = output.take(240),
                    involvedApp = "Termux PTY",
                    eventType = "TERMINAL_EXECUTION",
                    succeeded = execResult.exitCode == 0,
                    importanceValence = 2
                )
            } catch (_: Exception) {}
        }
        return output
    }

    /**
     * Requests Hands-Free Voice Approval from the user via Gemini Live Voice.
     * Invoked when Claude Code CLI or an OpenCode agent requires permission for a sensitive tool.
     */
    suspend fun requestVoiceApproval(agentName: String, toolName: String, toolDetails: String): Boolean {
        Log.w(TAG, "Approval requested by $agentName for $toolName: $toolDetails")
        val deferred = CompletableDeferred<Boolean>()
        val approval = PendingVoiceApproval(
            agentName = agentName,
            toolName = toolName,
            toolDetails = toolDetails,
            deferredResponse = deferred
        )

        _pendingApprovalFlow.value = approval

        val hinglishPrompt =
            "Sir, $agentName is requesting permission to execute `$toolName` ($toolDetails). Should I authorize it? Aap 'haan kar do' ya 'cancel' bol sakte hain."

        GeminiLiveVoiceManager.getInstance(context).injectAssistantVoiceSpeech(hinglishPrompt)

        return try {
            val approved = deferred.await()
            Log.i(TAG, "Voice approval outcome for $toolName: $approved")
            approved
        } finally {
            _pendingApprovalFlow.value = null
        }
    }

    /**
     * Resolves pending voice approval when user replies verbally ("haan", "allow", "yes", "reject", "cancel").
     */
    fun resolveVoiceApproval(spokenInput: String): Boolean {
        val pending = _pendingApprovalFlow.value ?: return false
        val cleanInput = spokenInput.trim().lowercase()

        val isApproved = cleanInput.contains("allow") ||
            cleanInput.contains("yes") ||
            cleanInput.contains("haan") ||
            cleanInput.contains("kar do") ||
            cleanInput.contains("approve") ||
            cleanInput.contains("bhej do") ||
            cleanInput.contains("authorize") ||
            cleanInput.contains("proceed")

        val isRejected = cleanInput.contains("no") ||
            cleanInput.contains("reject") ||
            cleanInput.contains("cancel") ||
            cleanInput.contains("mat karo") ||
            cleanInput.contains("ruk jao") ||
            cleanInput.contains("deny")

        if (isApproved) {
            pending.deferredResponse.complete(true)
            GeminiLiveVoiceManager.getInstance(context).injectAssistantVoiceSpeech("Action authorized, sir. Proceeding.")
            return true
        } else if (isRejected) {
            pending.deferredResponse.complete(false)
            GeminiLiveVoiceManager.getInstance(context).injectAssistantVoiceSpeech("Action cancelled, sir.")
            return true
        }
        return false
    }
}
