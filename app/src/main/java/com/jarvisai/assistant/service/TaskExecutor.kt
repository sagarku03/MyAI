package com.jarvisai.assistant.service

import android.content.Context
import android.os.Looper
import android.util.Log
import com.jarvisai.assistant.data.network.GeminiApiService
import com.jarvisai.assistant.model.SupportedGeminiModels
import com.jarvisai.assistant.termux.JarvisTermuxBridge
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Executes [block] on the Android Main Thread safely without deadlocking if the caller
 * is already running on the Main Looper (e.g. inside `runBlocking` on the main thread).
 */
internal suspend fun <T> runOnMainSafe(block: () -> T): T {
    return if (Looper.myLooper() == Looper.getMainLooper()) {
        block()
    } else {
        withContext(Dispatchers.Main.immediate) {
            block()
        }
    }
}

/**
 * Execution lifecycle states for [TaskExecutor] (`AbuZar-Ansarii/Jarvis` PrivateAgent architecture).
 */
enum class ExecutorState {
    IDLE,
    RUNNING,
    PAUSED
}

/**
 * Structured single-step decision returned by Gemini or Ollama during the Think phase.
 *
 * Schema:
 * `{"action": "<click_text|click_xy|type_text|scroll|press_enter|press_back|open_app|wait|done>", "params": {...}, "reasoning": "...", "is_complete": false}`
 */
data class AgentDecision(
    val action: String,
    val params: JSONObject = JSONObject(),
    val reasoning: String = "",
    val isComplete: Boolean = false
) {
    fun actionSignature(): String {
        val normalizedAction = action.trim().lowercase()
        val keyParam = when (normalizedAction) {
            "click_text" -> params.optString("text", params.optString("target", params.optString("view_id", ""))).trim().lowercase()
            "click_xy" -> "${params.optInt("x", -1)},${params.optInt("y", -1)}"
            "type_text" -> "${params.optString("target", "").trim().lowercase()}:${params.optString("text", "").trim().lowercase()}"
            "scroll" -> params.optString("direction", "down").trim().lowercase()
            "open_app" -> params.optString("app_name", params.optString("package_name", "")).trim().lowercase()
            else -> ""
        }
        return "$normalizedAction($keyParam)"
    }
}

/**
 * Recovery action outcome produced by [RecoveryEngine] when UI deadlock or action repetition is detected.
 */
data class RecoveryExecutionResult(
    val triggered: Boolean,
    val recoveryStage: Int,
    val strategyName: String,
    val succeeded: Boolean,
    val summary: String
)

/**
 * Autonomous Recovery Engine (`AbuZar-Ansarii/Jarvis` `recovery_engine.dart` architecture).
 *
 * Monitors the last 3 screen hierarchy hashes and last 3 action signatures.
 * When the screen remains unchanged for >= 3 consecutive turns or the exact same action
 * repeats 3 times consecutively, it intervenes with an escalating 3-step recovery strategy:
 *   Stage 1 -> Scroll down (`JarvisAccessibilityService.scrollScreen(context, "down")`)
 *   Stage 2 -> Wait 2000ms for slow UI/network settle (`delay(2000L)`)
 *   Stage 3 -> Press Back (`JarvisAccessibilityService.navigateBack(context)`)
 */
class RecoveryEngine {

    private val screenHashHistory = CopyOnWriteArrayList<Int>()
    private val actionSignatureHistory = CopyOnWriteArrayList<String>()
    private var consecutiveRecoveryStage: Int = 0

    @Synchronized
    fun reset() {
        screenHashHistory.clear()
        actionSignatureHistory.clear()
        consecutiveRecoveryStage = 0
    }

    @Synchronized
    fun recordScreenHash(hash: Int) {
        if (hash == 0) return
        screenHashHistory.add(hash)
        while (screenHashHistory.size > 6) {
            screenHashHistory.removeAt(0)
        }
    }

    @Synchronized
    fun recordActionSignature(signature: String) {
        if (signature.isBlank()) return
        actionSignatureHistory.add(signature)
        while (actionSignatureHistory.size > 6) {
            actionSignatureHistory.removeAt(0)
        }
    }

    @Synchronized
    fun isScreenStuck(): Boolean {
        if (screenHashHistory.size < 3) return false
        val lastThree = screenHashHistory.takeLast(3)
        return lastThree[0] != 0 && lastThree[0] == lastThree[1] && lastThree[1] == lastThree[2]
    }

    @Synchronized
    fun isActionRepeating(): Boolean {
        if (actionSignatureHistory.size < 3) return false
        val lastThree = actionSignatureHistory.takeLast(3)
        return lastThree[0].isNotBlank() && lastThree[0] == lastThree[1] && lastThree[1] == lastThree[2]
    }

    /**
     * Detects 2-action alternating oscillation deadlocks (A -> B -> A -> B)
     * such as Click(More options) -> Scroll(down) -> Click(More options) -> Scroll(down).
     */
    @Synchronized
    fun isOscillatingLoop(): Boolean {
        if (actionSignatureHistory.size < 4) return false
        val last4 = actionSignatureHistory.takeLast(4)
        val isActionOscillating = (last4[0] == last4[2] && last4[1] == last4[3] &&
            last4[0] != last4[1] && last4[0].isNotBlank() && last4[1].isNotBlank())
        if (isActionOscillating) return true

        if (screenHashHistory.size >= 4) {
            val hashes = screenHashHistory.takeLast(4)
            if (hashes[0] != 0 && hashes[0] == hashes[2] && hashes[1] == hashes[3] && hashes[0] != hashes[1]) {
                return true
            }
        }
        return false
    }

    @Synchronized
    fun shouldIntervene(): Boolean = isScreenStuck() || isActionRepeating() || isOscillatingLoop()

    suspend fun executeRecovery(context: Context): RecoveryExecutionResult {
        val stuckScreen = isScreenStuck()
        val repeatedAction = isActionRepeating()
        val oscillating = isOscillatingLoop()
        if (!stuckScreen && !repeatedAction && !oscillating) {
            return RecoveryExecutionResult(
                triggered = false,
                recoveryStage = 0,
                strategyName = "NONE",
                succeeded = false,
                summary = "No recovery needed."
            )
        }

        val stage = (consecutiveRecoveryStage % 3) + 1
        consecutiveRecoveryStage++

        // Clear the repeating tail so the next recovery only triggers if the deadlock persists
        if (screenHashHistory.isNotEmpty()) {
            screenHashHistory.removeAt(screenHashHistory.lastIndex)
        }
        if (actionSignatureHistory.isNotEmpty()) {
            actionSignatureHistory.removeAt(actionSignatureHistory.lastIndex)
        }

        val triggerReason = when {
            oscillating -> "2-action alternating oscillation deadlock (A -> B -> A -> B) detected"
            stuckScreen && repeatedAction -> "screen hash & action signature stuck for 3 turns"
            stuckScreen -> "screen hierarchy unchanged for 3 consecutive turns"
            else -> "identical action repeated 3 times consecutively"
        }

        // For oscillation deadlock (e.g. menu open/close loop), prioritize Back or alternate tap
        if (oscillating) {
            val backRes = runOnMainSafe {
                JarvisAccessibilityService.navigateBack(context)
            }
            delay(400L)
            return RecoveryExecutionResult(
                triggered = true,
                recoveryStage = 1,
                strategyName = "RECOVERY_BREAK_OSCILLATION",
                succeeded = backRes.executed,
                summary = "Recovery Stage 1 ($triggerReason): Dismissed stuck popup/overlay to escape infinite loop."
            )
        }

        return when (stage) {
            1 -> {
                val scrollRes = runOnMainSafe {
                    JarvisAccessibilityService.scrollScreen(context, "down")
                }
                delay(850L)
                RecoveryExecutionResult(
                    triggered = true,
                    recoveryStage = 1,
                    strategyName = "RECOVERY_SCROLL_DOWN",
                    succeeded = scrollRes.executed,
                    summary = "Recovery Stage 1 ($triggerReason): Auto-scrolled screen down to expose off-screen elements."
                )
            }
            2 -> {
                delay(2000L)
                RecoveryExecutionResult(
                    triggered = true,
                    recoveryStage = 2,
                    strategyName = "RECOVERY_WAIT_SETTLE",
                    succeeded = true,
                    summary = "Recovery Stage 2 ($triggerReason): Waited 2000ms for slow UI rendering or network response to settle."
                )
            }
            else -> {
                val backRes = runOnMainSafe {
                    JarvisAccessibilityService.navigateBack(context)
                }
                delay(850L)
                RecoveryExecutionResult(
                    triggered = true,
                    recoveryStage = 3,
                    strategyName = "RECOVERY_PRESS_BACK",
                    succeeded = backRes.executed,
                    summary = "Recovery Stage 3 ($triggerReason): Pressed Android Back to escape stuck modal or dead-end screen."
                )
            }
        }
    }
}

/**
 * Production-ready Autonomous Screen Operator (`AbuZar-Ansarii/Jarvis` `task_executor.dart` architecture).
 *
 * Implements the closed-loop `Observe -> Compress -> Think -> Act -> Verify -> Recovery` cycle:
 *   a) Observe: Dump live Accessibility hierarchy via [JarvisAccessibilityService.getScreenContext].
 *   b) Compress: Format nodes into `[#index] "text" | "contentDescription" | bounds(left,top,right,bottom) | clickable=true | editable=true`.
 *   c) Think: Query [GeminiApiService.generateAgentDecision] (with automatic failover to [OllamaLocalLlmBridge.generateAgentDecisionJson])
 *      for strictly ONE JSON action:
 *      `{"action": "<click_text|click_xy|type_text|scroll|press_enter|press_back|open_app|wait|done>", "params": {...}, "reasoning": "...", "is_complete": false}`
 *   d) Act: Execute the single atomic action via [JarvisAccessibilityService] and [AppLauncherManager].
 *   e) Verify: Wait 850ms settling window, compute UI-tree hash and active package diff, and record in history.
 *   f) Recovery Engine: Track last 3 action signatures and screen hashes; intervene with (1) Scroll down, (2) Wait 2000ms, (3) Press back.
 *   g) Conclude: Finalize when `action == "done"` or `is_complete == true` and emit a truthful Hinglish briefing.
 */
object TaskExecutor {

    private const val TAG = "TaskExecutor"
    private const val SETTLING_WINDOW_MS = 150L // Reduced from 400L for ultra-fast loop turnaround

    @Volatile
    private var appContext: Context? = null

    private val defaultGeminiService = GeminiApiService()

    var defaultApiKeyProvider: () -> String = { "" }
    var defaultModelIdProvider: () -> String = { SupportedGeminiModels.DEFAULT_TEXT_MODEL }

    /**
     * Optional test hook allowing Robolectric / unit tests to supply deterministic LLM JSON responses
     * without hitting external network sockets when testing the Observe-Think-Act loop.
     */
    @Volatile
    var testDecisionOverride: ((goal: String, compressedScreen: String, history: List<String>, stepIndex: Int) -> String?)? = null

    val recoveryEngine: RecoveryEngine = RecoveryEngine()

    private val _state = MutableStateFlow(ExecutorState.IDLE)
    val state: StateFlow<ExecutorState> = _state.asStateFlow()

    private val _stepLogs = MutableStateFlow<List<AgenticStepLog>>(emptyList())
    val stepLogs: StateFlow<List<AgenticStepLog>> = _stepLogs.asStateFlow()

    @Volatile
    private var abortRequested: Boolean = false

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun pause() {
        if (_state.value == ExecutorState.RUNNING) {
            _state.value = ExecutorState.PAUSED
        }
    }

    fun resume() {
        if (_state.value == ExecutorState.PAUSED) {
            _state.value = ExecutorState.RUNNING
        }
    }

    fun stop() {
        abortRequested = true
        _state.value = ExecutorState.IDLE
        JarvisOverlayHudService.updateAgentStepOnHud(
            headline = "JARVIS AGENT HALTED",
            stepDetail = "Stopped by user command",
            isBusy = false
        )
    }

    /**
     * Primary entry point matching `executeTask(goal: String, maxSteps: Int = 12): AutonomousGoalResult`.
     */
    suspend fun executeTask(
        goal: String,
        maxSteps: Int = 12
    ): AutonomousGoalResult {
        val ctx = appContext
            ?: JarvisAccessibilityService.activeInstance?.applicationContext
            ?: throw IllegalStateException("TaskExecutor is not initialized with an Android Context. Call TaskExecutor.initialize(context) or pass context to executeTask.")
        return executeTask(
            context = ctx,
            goal = goal,
            maxSteps = maxSteps,
            apiKey = defaultApiKeyProvider(),
            modelId = defaultModelIdProvider(),
            geminiApiService = defaultGeminiService,
            phoneControlsManager = PhoneControlsManager(ctx)
        )
    }

    /**
     * Full context-aware entry point used by [AgenticMobileEngine] and foreground/background services.
     */
    suspend fun executeTask(
        context: Context,
        goal: String,
        maxSteps: Int = 12,
        apiKey: String = "",
        modelId: String = SupportedGeminiModels.DEFAULT_TEXT_MODEL,
        geminiApiService: GeminiApiService = defaultGeminiService,
        phoneControlsManager: PhoneControlsManager = PhoneControlsManager(context),
        onStepLogUpdate: (List<AgenticStepLog>) -> Unit = {}
    ): AutonomousGoalResult {
        appContext = context.applicationContext
        val cleanGoal = goal.trim()
        val safeMaxSteps = maxSteps.coerceIn(1, 50)
        val resolvedApiKey = apiKey.trim().ifBlank { defaultApiKeyProvider().trim() }
        val resolvedModelId = modelId.trim().ifBlank { defaultModelIdProvider().trim().ifBlank { SupportedGeminiModels.DEFAULT_TEXT_MODEL } }
        val appLauncher = AppLauncherManager(context.applicationContext, phoneControlsManager)

        abortRequested = false
        _state.value = ExecutorState.RUNNING
        recoveryEngine.reset()

        val logs = mutableListOf<AgenticStepLog>()
        val history = mutableListOf<String>()
        _stepLogs.value = emptyList()
        onStepLogUpdate(emptyList())

        WorkingMemoryBlackboard.startTask(
            rootGoal = cleanGoal,
            plannedTriples = emptyList()
        )
        JarvisOverlayHudService.updateAgentStepOnHud(
            headline = "AGENT GOAL: ${cleanGoal.take(28)}",
            stepDetail = "Starting Observe-Think-Act loop...",
            isBusy = true
        )

        var executedActionsCount = 0
        var goalMarkedComplete = false
        var finalReasoningOrSummary = ""
        var lastErrorDetail = ""

        for (stepIndex in 1..safeMaxSteps) {
            // Respect PAUSED state
            while (_state.value == ExecutorState.PAUSED && !abortRequested) {
                delay(250L)
            }

            if (abortRequested) {
                val abortMsg = "Halted by user ('Jarvis Ruko')."
                WorkingMemoryBlackboard.failActiveTask(abortMsg)
                logs.add(
                    AgenticStepLog(
                        stepNumber = stepIndex,
                        phase = "HALTED",
                        actionName = "STOP",
                        targetDescription = "User Interrupt",
                        succeeded = false,
                        summary = abortMsg
                    )
                )
                publishLogs(logs, onStepLogUpdate)
                break
            }

            // =================================================================
            // a) OBSERVE: Dump current Accessibility UI hierarchy
            // =================================================================
            var beforeSnapshot = runOnMainSafe {
                JarvisAccessibilityService.getScreenContext(context)
            }
            var beforeHash = computeScreenHash(beforeSnapshot)
            recoveryEngine.recordScreenHash(beforeHash)
            if (beforeHash != 0) {
                WorkingMemoryBlackboard.recordUiScreenHash(beforeHash)
            }

            logs.add(
                AgenticStepLog(
                    stepNumber = stepIndex,
                    phase = "OBSERVE",
                    actionName = "OBSERVE_SCREEN",
                    targetDescription = beforeSnapshot.activePackageName.ifBlank { "Device Screen" },
                    succeeded = true,
                    summary = "Observed ${beforeSnapshot.elements.size} visible nodes in '${beforeSnapshot.activePackageName.ifBlank { "foreground" }}'."
                )
            )
            publishLogs(logs, onStepLogUpdate)

            // =================================================================
            // f) RECOVERY ENGINE CHECK: Intervene if screen or action is stuck >= 3 turns
            // =================================================================
            if (recoveryEngine.shouldIntervene()) {
                val recoveryRes = recoveryEngine.executeRecovery(context)
                if (recoveryRes.triggered) {
                    logs.add(
                        AgenticStepLog(
                            stepNumber = stepIndex,
                            phase = "SELF_HEAL",
                            actionName = recoveryRes.strategyName,
                            targetDescription = "Stage ${recoveryRes.recoveryStage}",
                            succeeded = recoveryRes.succeeded,
                            summary = recoveryRes.summary
                        )
                    )
                    history.add("Step $stepIndex [RECOVERY]: ${recoveryRes.strategyName} -> ${recoveryRes.summary}")
                    publishLogs(logs, onStepLogUpdate)

                    // Re-observe screen after recovery action settled
                    beforeSnapshot = runOnMainSafe {
                        JarvisAccessibilityService.getScreenContext(context)
                    }
                    beforeHash = computeScreenHash(beforeSnapshot)
                }
            }

            // =================================================================
            // b) COMPRESS: Token-efficient node tree representation
            // =================================================================
            val compressedScreen = compressScreenHierarchy(beforeSnapshot)

            // =================================================================
            // c) THINK: Query Gemini / Local Ollama for strictly ONE JSON action
            // =================================================================
            JarvisOverlayHudService.updateAgentStepOnHud(
                headline = "STEP $stepIndex/$safeMaxSteps: THINKING",
                stepDetail = "Analyzing ${beforeSnapshot.elements.size} UI nodes...",
                isBusy = true
            )

            val decisionResult = requestSingleAgentDecision(
                context = context,
                goal = cleanGoal,
                compressedScreen = compressedScreen,
                history = history,
                stepIndex = stepIndex,
                apiKey = resolvedApiKey,
                modelId = resolvedModelId,
                geminiApiService = geminiApiService
            )

            val decision = decisionResult.getOrNull()
            if (decision == null) {
                val errReason = decisionResult.exceptionOrNull()?.message
                    ?: "Neither Gemini API nor Local Ollama returned a valid JSON decision."
                lastErrorDetail = errReason
                logs.add(
                    AgenticStepLog(
                        stepNumber = stepIndex,
                        phase = "THINK",
                        actionName = "LLM_DECISION_ERROR",
                        targetDescription = cleanGoal.take(32),
                        succeeded = false,
                        summary = errReason
                    )
                )
                publishLogs(logs, onStepLogUpdate)
                break
            }

            val normalizedAction = decision.action.trim().lowercase()
            recoveryEngine.recordActionSignature(decision.actionSignature())

            logs.add(
                AgenticStepLog(
                    stepNumber = stepIndex,
                    phase = "THINK",
                    actionName = normalizedAction.uppercase(),
                    targetDescription = describeDecisionTarget(decision),
                    succeeded = true,
                    summary = decision.reasoning.ifBlank { "Selected action '$normalizedAction'." }
                )
            )
            publishLogs(logs, onStepLogUpdate)

            // =================================================================
            // g) CONCLUDE: Check if action == "done" or is_complete == true
            // =================================================================
            if (normalizedAction == "done" || decision.isComplete) {
                goalMarkedComplete = true
                finalReasoningOrSummary = decision.params.optString("spoken_summary", "").trim()
                    .ifBlank { decision.reasoning.trim() }
                logs.add(
                    AgenticStepLog(
                        stepNumber = stepIndex,
                        phase = "VERIFY",
                        actionName = "DONE",
                        targetDescription = cleanGoal.take(36),
                        succeeded = true,
                        summary = finalReasoningOrSummary.ifBlank { "Goal verified complete." }
                    )
                )
                WorkingMemoryBlackboard.markStepCompleted(stepIndex, finalReasoningOrSummary)
                publishLogs(logs, onStepLogUpdate)
                break
            }

            // High-Risk Safety Gatekeeper check before executing destructive/financial clicks
            val candidateRiskLabel = describeDecisionTarget(decision)
            if (normalizedAction in setOf("click_text", "click_xy", "type_text") &&
                AgenticSafetyGatekeeper.isHighRiskTarget(candidateRiskLabel)
            ) {
                val gateReq = AgenticSafetyGatekeeper.stageHighRiskAction(
                    actionType = normalizedAction.uppercase(),
                    targetLabel = candidateRiskLabel
                ) {
                    runOnMainSafe {
                        JarvisAccessibilityService.clickElement(
                            context = context,
                            targetText = candidateRiskLabel,
                            contentDescription = candidateRiskLabel,
                            viewId = null
                        )
                    }
                }
                logs.add(
                    AgenticStepLog(
                        stepNumber = stepIndex,
                        phase = "GATEKEEPER",
                        actionName = "HIGH_RISK_BLOCKED",
                        targetDescription = candidateRiskLabel,
                        succeeded = true,
                        summary = gateReq.spokenPromptHinglish
                    )
                )
                publishLogs(logs, onStepLogUpdate)
                _state.value = ExecutorState.PAUSED
                val gateReport = ActionVerificationEngine.verifySystemControlAction(
                    controlType = "AGENTIC_REACT_GOAL",
                    targetStateDescription = cleanGoal,
                    capabilityStatus = CapabilityResultStatus.REQUIRES_USER_ACTION,
                    stateVerifiedByApi = true,
                    userMessage = gateReq.riskReason,
                    hinglishMessage = gateReq.spokenPromptHinglish
                )
                return AutonomousGoalResult(
                    goalUtterance = cleanGoal,
                    completed = false,
                    pausedForSafetyConfirmation = true,
                    stepsExecuted = executedActionsCount,
                    stepLogs = logs.toList(),
                    finalSpokenHinglishSummary = gateReq.spokenPromptHinglish,
                    verifiedReport = gateReport
                )
            }

            // =================================================================
            // d) ACT: Execute the single atomic action on the device
            // =================================================================
            val targetDesc = describeDecisionTarget(decision)
            WorkingMemoryBlackboard.addCustomStep(
                title = "${normalizedAction.uppercase()}: $targetDesc",
                actionType = normalizedAction.uppercase(),
                targetParam = targetDesc
            )
            WorkingMemoryBlackboard.markStepInProgress(stepIndex, decision.reasoning)
            JarvisOverlayHudService.updateAgentStepOnHud(
                headline = "STEP $stepIndex/$safeMaxSteps: ${normalizedAction.uppercase()}",
                stepDetail = targetDesc.take(36),
                isBusy = true
            )

            val actOutcome = executeSingleAction(
                context = context,
                decision = decision,
                beforeSnapshot = beforeSnapshot,
                appLauncher = appLauncher,
                phoneControlsManager = phoneControlsManager
            )
            executedActionsCount++

            // =================================================================
            // e) VERIFY: Wait settling window & check UI-tree hash / package diff
            // =================================================================
            if (normalizedAction != "done") {
                val settleDelay = if (normalizedAction == "open_app") 450L else SETTLING_WINDOW_MS
                delay(settleDelay)
            }
            var afterSnapshot = runOnMainSafe {
                JarvisAccessibilityService.getScreenContext(context)
            }
            var afterHash = computeScreenHash(afterSnapshot)
            var uiChanged = (afterHash != beforeHash) ||
                (afterSnapshot.activePackageName != beforeSnapshot.activePackageName)

            // Secondary Coordinate Gesture / Termux Shell Fallback if click was absorbed without UI change
            if (!uiChanged && normalizedAction in setOf("click_text", "click_xy")) {
                val fallbackCoords = resolveFallbackCoordinates(decision, afterSnapshot)
                if (fallbackCoords != null) {
                    Log.d(TAG, "UI unchanged after $normalizedAction; firing coordinate/Termux fallback at (${fallbackCoords.first}, ${fallbackCoords.second})")
                    val tapFallback = runOnMainSafe {
                        JarvisAccessibilityService.tapCoordinates(
                            x = fallbackCoords.first,
                            y = fallbackCoords.second,
                            labelHint = targetDesc
                        )
                    }
                    val termuxBridge = JarvisTermuxBridge(context)
                    if (!tapFallback.executed && termuxBridge.isTermuxInstalled()) {
                        withContext(Dispatchers.IO) {
                            termuxBridge.executeCommand(
                                command = "input tap ${fallbackCoords.first} ${fallbackCoords.second}",
                                background = true
                            )
                        }
                    }
                    delay(450L)
                    afterSnapshot = runOnMainSafe {
                        JarvisAccessibilityService.getScreenContext(context)
                    }
                    afterHash = computeScreenHash(afterSnapshot)
                    uiChanged = (afterHash != beforeHash) ||
                        (afterSnapshot.activePackageName != beforeSnapshot.activePackageName)
                }
            }

            val stepSucceeded = actOutcome.first || uiChanged
            val verifyNote = buildString {
                append(actOutcome.second)
                append(if (uiChanged) " [UI_CHANGED=true, pkg=${afterSnapshot.activePackageName.ifBlank { "screen" }}]" else " [UI_CHANGED=false]")
            }

            logs.add(
                AgenticStepLog(
                    stepNumber = stepIndex,
                    phase = "ACT_VERIFY",
                    actionName = normalizedAction.uppercase(),
                    targetDescription = targetDesc,
                    succeeded = stepSucceeded,
                    summary = verifyNote
                )
            )
            if (stepSucceeded) {
                WorkingMemoryBlackboard.markStepCompleted(stepIndex, verifyNote)
            } else {
                WorkingMemoryBlackboard.markStepFailed(stepIndex, verifyNote)
                lastErrorDetail = actOutcome.second
            }
            publishLogs(logs, onStepLogUpdate)

            history.add(
                "Step $stepIndex: action=$normalizedAction, target=\"$targetDesc\", executed=${actOutcome.first}, ui_changed=$uiChanged, active_pkg=\"${afterSnapshot.activePackageName}\", reasoning=\"${decision.reasoning}\""
            )
        }

        _state.value = ExecutorState.IDLE
        val isA11yEnabled = JarvisAccessibilityService.isAccessibilityEnabled(context)
        val actionLogs = logs.filter { it.phase == "ACT_VERIFY" }
        val stepChainSummary = actionLogs.joinToString(" -> ") { "${it.actionName}(${it.targetDescription})" }

        val (completed, spokenHinglish, capabilityStatus) = when {
            goalMarkedComplete -> {
                val summary = finalReasoningOrSummary.ifBlank {
                    if (stepChainSummary.isNotBlank()) {
                        "Sir, maine $executedActionsCount steps execute karke aapka task poora kar diya hai: $stepChainSummary."
                    } else {
                        "Sir, current screen inspect karne ke baad aapka goal poora ho chuka hai."
                    }
                }
                Triple(true, summary, CapabilityResultStatus.SUCCESS)
            }
            !isA11yEnabled && executedActionsCount > 0 && actionLogs.any { !it.succeeded } -> {
                val firstSuccess = actionLogs.firstOrNull { it.succeeded }?.targetDescription
                val msg = if (!firstSuccess.isNullOrBlank()) {
                    "Maine $firstSuccess open kar diya hai. Lekin aage screen elements click/type karne ke liye JARVIS Accessibility Service OFF hai. Kripya Android Accessibility Settings mein jakar JARVIS enable karein."
                } else {
                    "Screen automation ke liye phone ki JARVIS Accessibility Service OFF hai. Kripya Android Settings mein jakar JARVIS Accessibility enable karein."
                }
                Triple(false, msg, CapabilityResultStatus.REQUIRES_USER_ACTION)
            }
            executedActionsCount > 0 && actionLogs.all { it.succeeded } -> {
                val msg = "Maine $executedActionsCount verified steps execute kiye ($stepChainSummary), lekin maximum steps ($safeMaxSteps) tak pahunchne se pehle final confirmation nahi mila."
                Triple(false, msg, CapabilityResultStatus.PARTIAL_SUCCESS)
            }
            else -> {
                val reason = lastErrorDetail.ifBlank { "Screen par target action verify nahi ho saka." }
                val msg = "Autonomous task poora nahi ho saka: $reason"
                Triple(false, msg, CapabilityResultStatus.FAILED)
            }
        }

        if (completed) {
            WorkingMemoryBlackboard.completeActiveTask(spokenHinglish)
            JarvisOverlayHudService.updateAgentStepOnHud(
                headline = "AGENT GOAL COMPLETED",
                stepDetail = "$executedActionsCount verified steps",
                isBusy = false
            )
        } else {
            WorkingMemoryBlackboard.failActiveTask(spokenHinglish)
            JarvisOverlayHudService.updateAgentStepOnHud(
                headline = if (!isA11yEnabled) "ACCESSIBILITY OFF" else "GOAL INCOMPLETE",
                stepDetail = spokenHinglish.take(36),
                isBusy = false
            )
        }

        val verifiedReport = ActionVerificationEngine.verifySystemControlAction(
            controlType = "AGENTIC_REACT_GOAL",
            targetStateDescription = cleanGoal,
            capabilityStatus = capabilityStatus,
            stateVerifiedByApi = completed,
            userMessage = spokenHinglish,
            hinglishMessage = spokenHinglish
        )

        return AutonomousGoalResult(
            goalUtterance = cleanGoal,
            completed = completed,
            pausedForSafetyConfirmation = false,
            stepsExecuted = executedActionsCount,
            stepLogs = logs.toList(),
            finalSpokenHinglishSummary = spokenHinglish,
            verifiedReport = verifiedReport
        )
    }

    /**
     * Formats the observed [ScreenContextSnapshot] into a ultra-compact, token-efficient representation
     * filtering out empty non-interactive layouts to keep Gemini latency minimal.
     */
    fun compressScreenHierarchy(snapshot: ScreenContextSnapshot, maxNodes: Int = 40): String {
        val sb = StringBuilder()
        sb.appendLine(
            "Package: ${snapshot.activePackageName.ifBlank { "unknown" }} | Window: ${snapshot.windowTitle.ifBlank { "none" }} | TotalNodes: ${snapshot.elements.size}"
        )
        if (snapshot.elements.isEmpty()) {
            sb.appendLine("[No visible interactive accessibility nodes on screen]")
            return sb.toString().trim()
        }

        // Prioritize interactive or labelled elements; prune empty containers
        val filtered = snapshot.elements.filter { el ->
            el.text.isNotBlank() || el.contentDescription.isNotBlank() || el.isEditable ||
                (el.isClickable && el.viewIdResourceName.isNotBlank()) || el.isScrollable
        }.take(maxNodes)

        for (el in filtered) {
            val label = el.text.ifBlank { el.contentDescription }.replace("\n", " ").trim()
            val formattedBounds = formatBoundsTuple(el.boundsInScreen)
            val viewIdSuffix = if (el.viewIdResourceName.isNotBlank()) {
                " id=${el.viewIdResourceName.substringAfterLast('/')}"
            } else {
                ""
            }
            val flags = buildString {
                if (el.isClickable) append(" click=true")
                if (el.isEditable) append(" edit=true")
                if (el.isScrollable) append(" scroll=true")
            }
            sb.appendLine("[#${el.index}] \"$label\" $formattedBounds$flags$viewIdSuffix")
        }
        return sb.toString().trim()
    }

    internal fun formatBoundsTuple(rawBounds: String): String {
        val nums = Regex("-?\\d+").findAll(rawBounds).mapNotNull { it.value.toIntOrNull() }.toList()
        return if (nums.size >= 4) {
            "bounds(${nums[0]},${nums[1]},${nums[2]},${nums[3]})"
        } else {
            "bounds(0,0,0,0)"
        }
    }

    internal fun parseBoundsCenter(rawBounds: String): Pair<Int, Int>? {
        val nums = Regex("-?\\d+").findAll(rawBounds).mapNotNull { it.value.toIntOrNull() }.toList()
        if (nums.size >= 4) {
            val cx = (nums[0] + nums[2]) / 2
            val cy = (nums[1] + nums[3]) / 2
            if (cx > 0 && cy > 0) return cx to cy
        }
        return null
    }

    internal fun computeScreenHash(snapshot: ScreenContextSnapshot): Int {
        if (!snapshot.serviceEnabled && snapshot.elements.isEmpty() && snapshot.activePackageName.isBlank()) {
            return 0
        }
        var hash = snapshot.activePackageName.hashCode()
        hash = 31 * hash + snapshot.windowTitle.hashCode()
        hash = 31 * hash + snapshot.elements.size
        for (el in snapshot.elements.take(30)) {
            hash = 31 * hash + el.text.hashCode()
            hash = 31 * hash + el.contentDescription.hashCode()
            hash = 31 * hash + el.boundsInScreen.hashCode()
        }
        return hash
    }

    private suspend fun requestSingleAgentDecision(
        context: Context,
        goal: String,
        compressedScreen: String,
        history: List<String>,
        stepIndex: Int,
        apiKey: String,
        modelId: String,
        geminiApiService: GeminiApiService
    ): Result<AgentDecision> {
        // 1. Check test override hook if set by unit test
        testDecisionOverride?.let { hook ->
            val rawOverride = hook(goal, compressedScreen, history, stepIndex)
            if (!rawOverride.isNullOrBlank()) {
                val parsed = parseAgentDecisionJson(rawOverride)
                if (parsed != null) return Result.success(parsed)
            }
        }

        // 2. Primary: Query Gemini API using selected model (with internal automatic model fallback cascade)
        var lastErrorMsg = ""
        if (apiKey.isNotBlank()) {
            val primaryModel = modelId.trim().ifBlank { SupportedGeminiModels.DEFAULT_TEXT_MODEL }
            val geminiRes = geminiApiService.generateAgentDecision(
                goal = goal,
                compressedScreen = compressedScreen,
                history = history,
                apiKey = apiKey,
                modelId = primaryModel
            )
            if (geminiRes.isSuccess) {
                val rawText = geminiRes.getOrThrow()
                val parsed = parseAgentDecisionJson(rawText)
                if (parsed != null) {
                    return Result.success(parsed)
                } else {
                    Log.w(TAG, "Primary Gemini response could not be parsed as JSON, retrying once...")
                    // Rapid 1-time retry with fallback
                    val retryRes = geminiApiService.generateAgentDecision(
                        goal = goal,
                        compressedScreen = compressedScreen,
                        history = history,
                        apiKey = apiKey,
                        modelId = primaryModel
                    )
                    val retryParsed = retryRes.getOrNull()?.let { parseAgentDecisionJson(it) }
                    if (retryParsed != null) {
                        return Result.success(retryParsed)
                    }
                    lastErrorMsg = "Gemini response was not valid agent decision JSON."
                }
            } else {
                lastErrorMsg = geminiRes.exceptionOrNull()?.message.orEmpty()
            }
        } else {
            lastErrorMsg = "Gemini API key not configured."
        }

        // 3. Secondary Failover: Query Local Ollama / OpenAI-Compatible Endpoint
        val historyText = if (history.isEmpty()) "None (Initial step)" else history.takeLast(8).joinToString("\n")
        val ollamaSys = """
            You are the JARVIS Autonomous Android Screen Operator.
            Return strictly ONE JSON object matching this schema:
            {"action": "<click_text|click_xy|type_text|scroll|press_enter|press_back|open_app|wait|done>", "params": {"text": "", "target": "", "x": 0, "y": 0, "direction": "down", "app_name": "", "spoken_summary": ""}, "reasoning": "...", "is_complete": false}
        """.trimIndent()
        val ollamaUserPrompt = """
            USER GOAL: $goal
            HISTORY:
            $historyText
            COMPRESSED SCREEN:
            $compressedScreen
        """.trimIndent()

        val ollamaRes = OllamaLocalLlmBridge.generateAgentDecisionJson(
            context = context,
            systemInstruction = ollamaSys,
            userPrompt = ollamaUserPrompt
        )
        if (ollamaRes.isSuccess) {
            val parsed = parseAgentDecisionJson(ollamaRes.getOrThrow())
            if (parsed != null) {
                return Result.success(parsed)
            }
        }

        return Result.failure(
            Exception(
                lastErrorMsg.ifBlank {
                    "Unable to reach Gemini API or Local Ollama daemon for autonomous decision."
                }
            )
        )
    }

    /**
     * Parses raw LLM output into an [AgentDecision], stripping markdown fences safely,
     * unwrapping nested decision wrappers, normalizing action synonyms, and auto-repairing malformed JSON.
     */
    fun parseAgentDecisionJson(rawResponse: String): AgentDecision? {
        if (rawResponse.isBlank()) return null
        return try {
            val cleaned = rawResponse
                .replace(Regex("```json\\s*", RegexOption.IGNORE_CASE), "")
                .replace("```", "")
                .trim()

            val startBrace = cleaned.indexOf('{')
            var endBrace = cleaned.lastIndexOf('}')
            if (startBrace < 0) return null

            var jsonString = if (endBrace > startBrace) {
                cleaned.substring(startBrace, endBrace + 1)
            } else {
                // Auto-repair truncated JSON by closing the root object
                cleaned.substring(startBrace) + "}"
            }

            var json = try {
                JSONObject(jsonString)
            } catch (e: Exception) {
                // Try aggressive repair if missing closing quote or brace
                if (!jsonString.endsWith("}")) jsonString += "}"
                JSONObject(jsonString)
            }

            // Check for nested decision wrapper objects
            val targetJson = when {
                json.has("decision") && json.optJSONObject("decision") != null -> json.getJSONObject("decision")
                json.has("agent_decision") && json.optJSONObject("agent_decision") != null -> json.getJSONObject("agent_decision")
                json.has("tool_call") && json.optJSONObject("tool_call") != null -> json.getJSONObject("tool_call")
                json.has("next_action") && json.optJSONObject("next_action") != null -> json.getJSONObject("next_action")
                else -> json
            }

            // Extract action name from multiple possible keys
            var rawAction = when {
                targetJson.has("action") && targetJson.optJSONObject("action") != null -> {
                    val actObj = targetJson.getJSONObject("action")
                    actObj.optString("name", actObj.optString("action", actObj.optString("type", "")))
                }
                targetJson.has("action") -> targetJson.optString("action", "")
                targetJson.has("name") -> targetJson.optString("name", "")
                targetJson.has("tool") -> targetJson.optString("tool", "")
                targetJson.has("type") -> targetJson.optString("type", "")
                targetJson.has("command") -> targetJson.optString("command", "")
                else -> ""
            }.trim().lowercase()

            if (rawAction.isEmpty()) return null

            val paramsObj = targetJson.optJSONObject("params")
                ?: targetJson.optJSONObject("parameters")
                ?: targetJson.optJSONObject("arguments")
                ?: JSONObject()

            // Support flat top-level parameter fields
            for (key in listOf("text", "target", "view_id", "index", "x", "y", "direction", "app_name", "package_name", "search_query", "spoken_summary", "query", "hint")) {
                if (targetJson.has(key) && !paramsObj.has(key)) {
                    paramsObj.put(key, targetJson.get(key))
                }
            }

            // Normalize action synonyms into canonical agent primitives
            val normalizedAction = when (rawAction) {
                "click", "tap", "press", "click_element", "tap_element" -> {
                    if (paramsObj.has("x") && paramsObj.has("y") && !paramsObj.has("text") && !paramsObj.has("target")) {
                        "click_xy"
                    } else {
                        "click_text"
                    }
                }
                "click_text", "click_xy" -> rawAction
                "type", "input", "write", "enter_text", "fill" -> "type_text"
                "type_text" -> "type_text"
                "open", "launch", "start", "start_app", "launch_app", "open_application" -> "open_app"
                "open_app" -> "open_app"
                "scroll", "scroll_down", "swipe_up", "swipe_down", "scroll_up" -> "scroll"
                "enter", "submit", "press_enter", "send" -> "press_enter"
                "back", "return", "go_back", "press_back" -> "press_back"
                "wait", "sleep", "pause" -> "wait"
                "done", "complete", "finish", "finished", "success", "stop" -> "done"
                else -> rawAction
            }

            val reasoning = targetJson.optString("reasoning", targetJson.optString("thought", targetJson.optString("explanation", ""))).trim()
            val isComplete = targetJson.optBoolean("is_complete", normalizedAction == "done")

            AgentDecision(
                action = normalizedAction,
                params = paramsObj,
                reasoning = reasoning,
                isComplete = isComplete
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse AgentDecision JSON: ${e.message}, raw='$rawResponse'")
            null
        }
    }

    private fun describeDecisionTarget(decision: AgentDecision): String {
        val p = decision.params
        return when (decision.action.lowercase()) {
            "click_text" -> p.optString("text", p.optString("target", p.optString("view_id", "Element"))).trim()
            "click_xy" -> "(${p.optInt("x", 0)}, ${p.optInt("y", 0)})"
            "type_text" -> {
                val txt = p.optString("text", "").trim()
                val into = p.optString("target", "").trim()
                if (into.isNotBlank()) "\"$txt\" -> $into" else "\"$txt\""
            }
            "scroll" -> p.optString("direction", "down").trim()
            "open_app" -> p.optString("app_name", p.optString("package_name", "App")).trim()
            "press_enter" -> "Enter / Submit"
            "press_back" -> "Android Back"
            "wait" -> "${p.optLong("duration_ms", 1500L)}ms"
            "done" -> p.optString("spoken_summary", "Goal Complete").take(40)
            else -> decision.action
        }.ifBlank { decision.action }
    }

    private suspend fun executeSingleAction(
        context: Context,
        decision: AgentDecision,
        beforeSnapshot: ScreenContextSnapshot,
        appLauncher: AppLauncherManager,
        phoneControlsManager: PhoneControlsManager
    ): Pair<Boolean, String> {
        val action = decision.action.trim().lowercase()
        val p = decision.params

        return when (action) {
            "click_text" -> {
                val targetText = p.optString("text", p.optString("target", "")).trim()
                val viewId = p.optString("view_id", "").trim().takeIf { it.isNotBlank() }
                val nodeIndex = p.optInt("index", -1)

                val clickRes = runOnMainSafe {
                    if (targetText.isNotBlank() || viewId != null) {
                        JarvisAccessibilityService.clickElement(
                            context = context,
                            targetText = targetText.ifBlank { null },
                            contentDescription = targetText.ifBlank { null },
                            viewId = viewId
                        )
                    } else if (nodeIndex >= 0) {
                        val indexedEl = beforeSnapshot.elements.firstOrNull { it.index == nodeIndex }
                        val label = indexedEl?.text?.ifBlank { indexedEl.contentDescription }.orEmpty()
                        if (label.isNotBlank()) {
                            JarvisAccessibilityService.clickElement(
                                context = context,
                                targetText = label,
                                contentDescription = label,
                                viewId = indexedEl?.viewIdResourceName?.ifBlank { null }
                            )
                        } else {
                            val coords = indexedEl?.let { parseBoundsCenter(it.boundsInScreen) }
                            if (coords != null) {
                                JarvisAccessibilityService.tapCoordinates(coords.first, coords.second, "Node #$nodeIndex")
                            } else {
                                AccessibilityOperationResult(
                                    executed = false,
                                    actionName = "click_text",
                                    errorCode = "ELEMENT_NOT_FOUND",
                                    message = "Node #$nodeIndex not found on screen.",
                                    spokenHinglishSummary = "Screen par element #$nodeIndex nahi mila."
                                )
                            }
                        }
                    } else {
                        AccessibilityOperationResult(
                            executed = false,
                            actionName = "click_text",
                            errorCode = "MISSING_TARGET",
                            message = "click_text requires 'text', 'view_id', or 'index'.",
                            spokenHinglishSummary = "Click karne ke liye target text provide nahi kiya gaya."
                        )
                    }
                }

                if (!clickRes.executed && targetText.isNotBlank()) {
                    // Coordinate fallback from observed snapshot bounds
                    val candidate = beforeSnapshot.elements.firstOrNull { el ->
                        el.text.contains(targetText, ignoreCase = true) ||
                            el.contentDescription.contains(targetText, ignoreCase = true)
                    }
                    val center = candidate?.let { parseBoundsCenter(it.boundsInScreen) }
                    if (center != null) {
                        val tapRes = runOnMainSafe {
                            JarvisAccessibilityService.tapCoordinates(center.first, center.second, targetText)
                        }
                        return tapRes.executed to tapRes.message
                    }
                }
                clickRes.executed to clickRes.message
            }

            "click_xy" -> {
                val rawX = p.optDouble("x", -1.0).toFloat()
                val rawY = p.optDouble("y", -1.0).toFloat()
                if (rawX < 0f || rawY < 0f) {
                    return false to "Invalid coordinates ($rawX, $rawY) for click_xy."
                }
                val (physX, physY) = JarvisAccessibilityService.resolvePhysicalScreenCoordinates(
                    context = context,
                    rawX = rawX,
                    rawY = rawY
                )
                val tapRes = runOnMainSafe {
                    JarvisAccessibilityService.tapCoordinates(
                        x = physX.toInt(),
                        y = physY.toInt(),
                        labelHint = p.optString("text", "(${physX.toInt()}, ${physY.toInt()})")
                    )
                }
                tapRes.executed to tapRes.message
            }

            "type_text" -> {
                val textToType = p.optString("text", p.optString("query", p.optString("value", ""))).trim()
                val targetField = p.optString("target", p.optString("hint", "")).trim()
                val submitAfter = p.optBoolean("submit", true)

                if (textToType.isEmpty()) {
                    return false to "type_text requires non-empty 'text' parameter."
                }

                runOnMainSafe {
                    if (targetField.isNotBlank()) {
                        val focused = JarvisAccessibilityService.focusInputField(context, targetField)
                        if (!focused.executed) {
                            JarvisAccessibilityService.clickElement(
                                context = context,
                                targetText = targetField,
                                contentDescription = targetField,
                                viewId = targetField
                            )
                        }
                    } else {
                        val firstEditable = beforeSnapshot.elements.firstOrNull { it.isEditable }
                        if (firstEditable != null) {
                            val hint = firstEditable.text.ifBlank { firstEditable.contentDescription }
                            JarvisAccessibilityService.focusInputField(context, hint.ifBlank { null })
                        }
                    }
                }
                delay(50L)
                val typeRes = runOnMainSafe {
                    JarvisAccessibilityService.typeText(
                        context = context,
                        text = textToType,
                        replaceExisting = true,
                        submitAfter = false
                    )
                }
                if (typeRes.executed && submitAfter) {
                    // Wait briefly for search dropdown suggestions to populate, then trigger press_enter / suggestion click
                    delay(80L)
                    val submitRes = runOnMainSafe {
                        JarvisAccessibilityService.submitInput(context)
                    }
                    if (!submitRes.executed) {
                        val liveAfterType = runOnMainSafe {
                            JarvisAccessibilityService.getScreenContext(context)
                        }
                        val firstDropdownSuggestion = liveAfterType.elements.firstOrNull { el ->
                            !el.isEditable && (
                                el.viewIdResourceName.contains("search_suggestion", ignoreCase = true) ||
                                    el.viewIdResourceName.contains("text_layout", ignoreCase = true) ||
                                    el.viewIdResourceName.contains("suggestion_item", ignoreCase = true) ||
                                    (el.isClickable && el.text.contains(textToType, ignoreCase = true))
                                )
                        }
                        if (firstDropdownSuggestion != null) {
                            val center = parseBoundsCenter(firstDropdownSuggestion.boundsInScreen)
                            runOnMainSafe {
                                if (center != null) {
                                    JarvisAccessibilityService.tapCoordinates(
                                        x = center.first,
                                        y = center.second,
                                        labelHint = firstDropdownSuggestion.text.ifBlank { "search_suggestion" }
                                    )
                                } else {
                                    JarvisAccessibilityService.clickElement(
                                        context = context,
                                        targetText = firstDropdownSuggestion.text,
                                        contentDescription = firstDropdownSuggestion.contentDescription,
                                        viewId = firstDropdownSuggestion.viewIdResourceName
                                    )
                                }
                            }
                        }
                    }
                }
                typeRes.executed to typeRes.message
            }

            "scroll" -> {
                val direction = p.optString("direction", "down").trim().ifBlank { "down" }
                val scrollRes = runOnMainSafe {
                    JarvisAccessibilityService.scrollScreen(context, direction)
                }
                scrollRes.executed to scrollRes.message
            }

            "press_enter" -> {
                val submitRes = runOnMainSafe {
                    JarvisAccessibilityService.submitInput(context)
                }
                submitRes.executed to submitRes.message
            }

            "press_back" -> {
                val backRes = runOnMainSafe {
                    JarvisAccessibilityService.navigateBack(context)
                }
                backRes.executed to backRes.message
            }

            "open_app" -> {
                val appName = p.optString("app_name", p.optString("app", p.optString("text", ""))).trim()
                val pkgName = p.optString("package_name", "").trim()
                val launchOutcome = appLauncher.launchApp(
                    appName = appName,
                    packageName = pkgName,
                    allowBrowserFallback = true
                )
                launchOutcome.executed to launchOutcome.message
            }

            "wait" -> {
                val waitMs = p.optLong("duration_ms", 600L).coerceIn(200L, 3000L)
                delay(waitMs)
                true to "Waited ${waitMs}ms for screen state to settle."
            }

            else -> {
                false to "Unsupported action '$action'."
            }
        }
    }

    private fun resolveFallbackCoordinates(
        decision: AgentDecision,
        snapshot: ScreenContextSnapshot
    ): Pair<Int, Int>? {
        val action = decision.action.trim().lowercase()
        val p = decision.params
        if (action == "click_xy") {
            val x = p.optInt("x", -1)
            val y = p.optInt("y", -1)
            if (x > 0 && y > 0) return x to y
        }
        val targetText = p.optString("text", p.optString("target", "")).trim()
        if (targetText.isNotBlank()) {
            val match = snapshot.elements.firstOrNull { el ->
                el.text.contains(targetText, ignoreCase = true) ||
                    el.contentDescription.contains(targetText, ignoreCase = true)
            }
            if (match != null) {
                return parseBoundsCenter(match.boundsInScreen)
            }
        }
        val idx = p.optInt("index", -1)
        if (idx >= 0) {
            val match = snapshot.elements.firstOrNull { it.index == idx }
            if (match != null) {
                return parseBoundsCenter(match.boundsInScreen)
            }
        }
        return null
    }

    private fun publishLogs(
        logs: List<AgenticStepLog>,
        onStepLogUpdate: (List<AgenticStepLog>) -> Unit
    ) {
        val copy = logs.toList()
        _stepLogs.value = copy
        onStepLogUpdate(copy)
    }
}
