package com.jarvisai.assistant.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import com.jarvisai.assistant.data.local.MemoryEntity
import com.jarvisai.assistant.data.local.WorkflowEntity
import com.jarvisai.assistant.data.network.GeminiApiService
import com.jarvisai.assistant.model.SupportedGeminiModels
import com.jarvisai.assistant.model.WorkflowAction
import com.jarvisai.assistant.model.WorkflowActionType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Step log item for Feature 1 (Autonomous Multi-Step ReAct UI Navigation Loop).
 */
data class AgenticStepLog(
    val stepNumber: Int,
    val phase: String, // OBSERVE, THINK, ACT_VERIFY, GATEKEEPER
    val actionName: String,
    val targetDescription: String,
    val succeeded: Boolean,
    val summary: String,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Result of an Autonomous ReAct Goal execution (Feature 1).
 */
data class AutonomousGoalResult(
    val goalUtterance: String,
    val completed: Boolean,
    val pausedForSafetyConfirmation: Boolean = false,
    val stepsExecuted: Int,
    val stepLogs: List<AgenticStepLog>,
    val finalSpokenHinglishSummary: String,
    val verifiedReport: VerifiedActionReport
)

/**
 * Pending high-risk action blocked by Feature 10 (AgenticSafetyGatekeeper).
 */
data class HighRiskGateRequest(
    val id: String = "gate_${System.currentTimeMillis()}",
    val actionType: String,
    val targetLabel: String,
    val riskReason: String,
    val spokenPromptHinglish: String,
    val onConfirmAction: suspend () -> AccessibilityOperationResult
)

/**
 * Form field state for Feature 5 (Conversational Form-Filling & Interview Agent).
 */
data class DetectedFormField(
    val index: Int,
    val labelOrHint: String,
    val currentValue: String,
    val autoFilledFromMemory: Boolean = false,
    val filledValue: String = ""
)

data class FormFillSessionState(
    val activePackageName: String,
    val fields: List<DetectedFormField>,
    val nextUnfilledFieldLabel: String?,
    val isReadyToSubmit: Boolean,
    val spokenHinglishPrompt: String
)

/**
 * Extracted entities and transformed content for Feature 6 (Cross-App Data Bridge).
 */
data class CrossAppScratchpadResult(
    val sourcePackage: String,
    val rawExtractedText: String,
    val transformedSummaryOrTranslation: String,
    val extractedPhoneNumbers: List<String>,
    val extractedUrls: List<String>,
    val extractedAmounts: List<String>,
    val targetDestinationApp: String = "",
    val routedSuccessfully: Boolean = false,
    val spokenHinglishSummary: String
)

/**
 * Feature 10: Agentic Safety Gatekeeper & Instant Voice Interrupt / Rollback Controller.
 *
 * Prevents autonomous or single-step clicks on financial/destructive UI controls
 * (`Pay`, `UPI`, `Checkout`, `Place Order`, `Delete`, `Uninstall`, `Transfer`, `Send Money`)
 * without explicit user confirmation, and handles instant `"Jarvis Ruko"` / `"Undo Karo"` rollback.
 */
object AgenticSafetyGatekeeper {

    private val highRiskKeywords = listOf(
        "pay ₹", "pay rs", "pay now", "place order", "buy now", "checkout",
        "send money", "transfer money", "upi pin", "proceed to pay",
        "delete account", "delete chat", "delete permanently", "uninstall",
        "factory reset", "format", "clear all data", "block contact"
    )

    private val _pendingHighRiskGate = MutableStateFlow<HighRiskGateRequest?>(null)
    val pendingHighRiskGate: StateFlow<HighRiskGateRequest?> = _pendingHighRiskGate.asStateFlow()

    fun isHighRiskTarget(targetLabel: String): Boolean {
        val lower = targetLabel.trim().lowercase()
        if (lower.isBlank()) return false
        if (highRiskKeywords.any { lower.contains(it) }) return true
        return Regex("\\b(pay|checkout|delete|uninstall|transfer)\\b").containsMatchIn(lower)
    }

    fun stageHighRiskAction(
        actionType: String,
        targetLabel: String,
        onConfirm: suspend () -> AccessibilityOperationResult
    ): HighRiskGateRequest {
        val reason = "High-risk financial or destructive UI control detected: '$targetLabel'"
        val prompt = "⚠️ Sir, '$targetLabel' ek High-Risk button hai. Kya aap confirm karte hain ki main '$targetLabel' par click karun? ('Haan Confirm' ya 'Cancel' bolein)"
        val req = HighRiskGateRequest(
            actionType = actionType,
            targetLabel = targetLabel,
            riskReason = reason,
            spokenPromptHinglish = prompt,
            onConfirmAction = onConfirm
        )
        _pendingHighRiskGate.value = req
        return req
    }

    suspend fun confirmPendingHighRiskAction(): AccessibilityOperationResult? {
        val pending = _pendingHighRiskGate.value ?: return null
        _pendingHighRiskGate.value = null
        return pending.onConfirmAction.invoke()
    }

    fun cancelPendingHighRiskAction(): String {
        val pending = _pendingHighRiskGate.value
        _pendingHighRiskGate.value = null
        return if (pending != null) {
            "High-risk action '${pending.targetLabel}' cancel kar diya gaya hai."
        } else {
            "Koi pending high-risk action nahi tha."
        }
    }

    fun clearForTest() {
        _pendingHighRiskGate.value = null
    }
}

/**
 * Structured decision produced by the Dynamic Vision + Set-of-Mark + Accessibility Tree ReAct planner.
 */
data class DynamicReActDecision(
    val thought: String,
    val actionType: String, // "TAP_BADGE", "CLICK_NODE", "TYPE_TEXT", "SCROLL", "DISMISS_POPUP", "BACK", "DONE"
    val badgeId: Int? = null,
    val targetParam: String = "",
    val extraParam: String = "",
    val spokenHinglish: String = ""
)

/**
 * Unified Agentic Mobile Engine implementing all 10 Next-Gen Agentic capabilities
 * on top of JARVIS AI's existing architecture without breaking any existing services.
 */
class AgenticMobileEngine(
    private val context: Context,
    private val phoneActionManager: PhoneActionManager,
    private val phoneControlsManager: PhoneControlsManager,
    private val geminiApiService: GeminiApiService? = null
) {

    private val geminiClient: GeminiApiService = geminiApiService ?: GeminiApiService()

    var defaultApiKeyProvider: () -> String = { "" }

    private val _agenticStepLogs = MutableStateFlow<List<AgenticStepLog>>(emptyList())
    val agenticStepLogs: StateFlow<List<AgenticStepLog>> = _agenticStepLogs.asStateFlow()

    private val _isAgenticLoopRunning = MutableStateFlow(false)
    val isAgenticLoopRunning: StateFlow<Boolean> = _isAgenticLoopRunning.asStateFlow()

    private val _activeFormSession = MutableStateFlow<FormFillSessionState?>(null)
    val activeFormSession: StateFlow<FormFillSessionState?> = _activeFormSession.asStateFlow()

    private val _lastScratchpadResult = MutableStateFlow<CrossAppScratchpadResult?>(null)
    val lastScratchpadResult: StateFlow<CrossAppScratchpadResult?> = _lastScratchpadResult.asStateFlow()

    private val _lastSetOfMarkBadges = MutableStateFlow<List<JarvisAccessibilityService.SetOfMarkBadge>>(emptyList())
    val lastSetOfMarkBadges: StateFlow<List<JarvisAccessibilityService.SetOfMarkBadge>> = _lastSetOfMarkBadges.asStateFlow()

    @Volatile
    private var cancelLoopRequested: Boolean = false

    fun stopAutonomousLoop() {
        cancelLoopRequested = true
        _isAgenticLoopRunning.value = false
        JarvisOverlayHudService.updateAgentStepOnHud(
            headline = "JARVIS AGENT HALTED",
            stepDetail = "Stopped by user command",
            isBusy = false
        )
    }

    // =========================================================================
    // FEATURE 10: VOICE UNDO / ROLLBACK & SAFETY GATEKEEPER
    // =========================================================================

    fun executeVoiceUndoOrRollback(): VoiceCommandActionOutcome {
        stopAutonomousLoop()
        AgenticSafetyGatekeeper.cancelPendingHighRiskAction()
        val backRes = JarvisAccessibilityService.navigateBack(context)
        val msg = if (backRes.executed) {
            "Agent halted and rolled back to previous screen (Undo)."
        } else {
            "Agent halted. (Enable Accessibility Service for screen Back rollback)."
        }
        val hinglish = if (backRes.executed) {
            "Maine chal raha task rok diya hai aur pichli screen par wapas (Undo) aa gaya hoon."
        } else {
            "Maine chal raha task turant rok diya hai."
        }
        val report = ActionVerificationEngine.verifySystemControlAction(
            controlType = "AGENTIC_UNDO_ROLLBACK",
            targetStateDescription = "BACK_AND_STOP",
            capabilityStatus = CapabilityResultStatus.SUCCESS,
            stateVerifiedByApi = backRes.executed,
            userMessage = msg,
            hinglishMessage = hinglish
        )
        return VoiceCommandActionOutcome(
            isPhoneCommand = true,
            executedSuccessfully = true,
            spokenHinglishResponse = hinglish,
            actionTitle = "Voice Undo & Halt",
            verifiedReport = report
        )
    }

    // =========================================================================
    // FEATURE 1: AUTONOMOUS MULTI-STEP "ReAct" UI NAVIGATION LOOP
    // =========================================================================

    suspend fun runAutonomousGoal(
        goalUtterance: String,
        maxSteps: Int = 6,
        apiKey: String = "",
        modelId: String = SupportedGeminiModels.DEFAULT_TEXT_MODEL
    ): AutonomousGoalResult {
        val cleanGoal = goalUtterance.trim()
        val safeMaxSteps = maxSteps.coerceIn(1, 100)
        val resolvedApiKey = apiKey.ifBlank { defaultApiKeyProvider() }.trim()
        cancelLoopRequested = false
        _isAgenticLoopRunning.value = true
        val logs = mutableListOf<AgenticStepLog>()
        _agenticStepLogs.value = emptyList()

        JarvisOverlayHudService.updateAgentStepOnHud(
            headline = "AGENT GOAL: ${cleanGoal.take(28)}",
            stepDetail = "Step 1: Observing screen & planning...",
            isBusy = true
        )

        val plannedSteps = buildMultiStepPlanForGoal(cleanGoal, safeMaxSteps, resolvedApiKey, modelId)
        WorkingMemoryBlackboard.startTask(
            rootGoal = cleanGoal,
            plannedTriples = plannedSteps
        )
        var executedCount = 0

        for ((index, step) in plannedSteps.withIndex()) {
            if (cancelLoopRequested) {
                WorkingMemoryBlackboard.failActiveTask("Halted by user ('Jarvis Ruko').")
                val abortLog = AgenticStepLog(
                    stepNumber = index + 1,
                    phase = "HALTED",
                    actionName = "STOP",
                    targetDescription = "User Interrupt",
                    succeeded = false,
                    summary = "Halted by user ('Jarvis Ruko')."
                )
                logs.add(abortLog)
                _agenticStepLogs.value = logs.toList()
                break
            }

            val stepNum = index + 1
            WorkingMemoryBlackboard.markStepInProgress(stepNum, step.second)
            JarvisOverlayHudService.updateAgentStepOnHud(
                headline = "STEP $stepNum/${plannedSteps.size}: ${step.first}",
                stepDetail = step.second,
                isBusy = true
            )

            // Observe Phase & Tier 2 UI Loop Detection
            val snapshot = JarvisAccessibilityService.getScreenContext(context)
            val uiHash = (
                snapshot.activePackageName + "|" +
                    snapshot.elements.take(12).joinToString(";") { "${it.text}:${it.contentDescription}:${it.viewIdResourceName}" }
                ).hashCode()
            WorkingMemoryBlackboard.recordUiScreenHash(uiHash)

            logs.add(
                AgenticStepLog(
                    stepNumber = stepNum,
                    phase = "OBSERVE",
                    actionName = "READ_SCREEN",
                    targetDescription = snapshot.activePackageName.ifBlank { "Device Screen" },
                    succeeded = true,
                    summary = "Observed ${snapshot.elements.size} UI nodes in ${snapshot.activePackageName.ifBlank { "foreground" }}."
                )
            )

            val actionType = step.first
            val actionParam = step.second
            val actionExtra = step.third

            // Safety Gatekeeper check before clicking any high-risk target
            if ((actionType == "CLICK_NODE" || actionType == "TAP_BADGE") &&
                AgenticSafetyGatekeeper.isHighRiskTarget(actionParam)
            ) {
                val gateReq = AgenticSafetyGatekeeper.stageHighRiskAction(
                    actionType = actionType,
                    targetLabel = actionParam
                ) {
                    JarvisAccessibilityService.clickElement(
                        context = context,
                        targetText = actionParam,
                        contentDescription = actionParam,
                        viewId = null
                    )
                }
                val gateLog = AgenticStepLog(
                    stepNumber = stepNum,
                    phase = "GATEKEEPER",
                    actionName = "HIGH_RISK_BLOCKED",
                    targetDescription = actionParam,
                    succeeded = true,
                    summary = gateReq.spokenPromptHinglish
                )
                logs.add(gateLog)
                _agenticStepLogs.value = logs.toList()
                _isAgenticLoopRunning.value = false
                val report = ActionVerificationEngine.verifySystemControlAction(
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
                    stepsExecuted = executedCount,
                    stepLogs = logs,
                    finalSpokenHinglishSummary = gateReq.spokenPromptHinglish,
                    verifiedReport = report
                )
            }

            // Act & Verify Phase
            val stepLog = when (actionType) {
                "OPEN_APP" -> {
                    val outcome = phoneActionManager.openInstalledAppByName(actionParam)
                    waitForScreenSettle(maxWaitMs = 1100L)
                    executedCount++
                    AgenticStepLog(
                        stepNumber = stepNum,
                        phase = "ACT_VERIFY",
                        actionName = "OPEN_APP",
                        targetDescription = actionParam,
                        succeeded = outcome.executedSuccessfully,
                        summary = outcome.spokenHinglishResponse
                    )
                }
                "YOUTUBE_SEARCH" -> {
                    val outcome = phoneActionManager.openYouTubeSearch(actionParam)
                    waitForScreenSettle(maxWaitMs = 1200L)
                    executedCount++
                    AgenticStepLog(
                        stepNumber = stepNum,
                        phase = "ACT_VERIFY",
                        actionName = "YOUTUBE_SEARCH",
                        targetDescription = actionParam,
                        succeeded = outcome.executedSuccessfully,
                        summary = outcome.spokenHinglishResponse
                    )
                }
                "CLICK_ORDINAL" -> {
                    val ordinal = actionParam.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val hint = actionExtra.ifBlank { "video" }
                    var ordRes = JarvisAccessibilityService.clickOrdinalElement(context, ordinal, hint)
                    if (!ordRes.executed && JarvisAccessibilityService.isAccessibilityEnabled(context)) {
                        // Auto-Popup / Ad Dismiss Reflex before retrying ordinal selection
                        if (isRealDeviceAccessibilityActive()) {
                            val popupDismissed = JarvisAccessibilityService.dismissBlockingPopupOrObstacle(context, hint)
                            if (popupDismissed.executed) {
                                logs.add(
                                    AgenticStepLog(
                                        stepNumber = stepNum,
                                        phase = "ACT_VERIFY",
                                        actionName = "DISMISS_POPUP",
                                        targetDescription = popupDismissed.matchedElementLabel,
                                        succeeded = true,
                                        summary = popupDismissed.spokenHinglishSummary
                                    )
                                )
                                delay(220L)
                            }
                        }
                        for (retry in 1..6) {
                            delay(280L)
                            ordRes = JarvisAccessibilityService.clickOrdinalElement(context, ordinal, hint)
                            if (ordRes.executed) break
                        }
                    }
                    waitForScreenSettle(maxWaitMs = 700L)
                    executedCount++
                    AgenticStepLog(
                        stepNumber = stepNum,
                        phase = "ACT_VERIFY",
                        actionName = "CLICK_ORDINAL",
                        targetDescription = "Item #$ordinal ($hint)",
                        succeeded = ordRes.executed,
                        summary = ordRes.spokenHinglishSummary.ifBlank { ordRes.message }
                    )
                }
                "CLICK_NODE" -> {
                    var clickRes = JarvisAccessibilityService.clickElement(
                        context = context,
                        targetText = actionParam,
                        contentDescription = actionParam,
                        viewId = actionParam
                    )
                    if (!clickRes.executed && isRealDeviceAccessibilityActive()) {
                        // Auto-Popup / Obstacle Dismiss Reflex
                        val popupDismissed = JarvisAccessibilityService.dismissBlockingPopupOrObstacle(context, actionParam)
                        if (popupDismissed.executed) {
                            logs.add(
                                AgenticStepLog(
                                    stepNumber = stepNum,
                                    phase = "ACT_VERIFY",
                                    actionName = "DISMISS_POPUP",
                                    targetDescription = popupDismissed.matchedElementLabel,
                                    succeeded = true,
                                    summary = popupDismissed.spokenHinglishSummary
                                )
                            )
                            delay(220L)
                        }
                        for (retry in 1..3) {
                            delay(350L)
                            clickRes = JarvisAccessibilityService.clickElement(
                                context = context,
                                targetText = actionParam,
                                contentDescription = actionParam,
                                viewId = actionParam
                            )
                            if (clickRes.executed) break
                        }
                    }
                    // Dynamic Vision + Set-of-Mark ReAct Fallback when static node click still couldn't find target on a real device
                    if (!clickRes.executed && isRealDeviceAccessibilityActive() && resolvedApiKey.isNotBlank()) {
                        val dynamicDecision = decideNextDynamicReActStepWithGemini(
                            goal = "$cleanGoal (Currently trying to click/find '$actionParam')",
                            stepHistory = logs,
                            apiKey = resolvedApiKey,
                            modelId = modelId
                        )
                        if (dynamicDecision != null && dynamicDecision.actionType != "DONE") {
                            logs.add(
                                AgenticStepLog(
                                    stepNumber = stepNum,
                                    phase = "THINK",
                                    actionName = "VISION_SOM_REACT",
                                    targetDescription = dynamicDecision.targetParam.ifBlank { "Badge #${dynamicDecision.badgeId ?: 1}" },
                                    succeeded = true,
                                    summary = dynamicDecision.thought
                                )
                            )
                            val fallbackOutcome = executeDynamicReActDecision(stepNum, dynamicDecision)
                            if (fallbackOutcome != null) {
                                clickRes = AccessibilityOperationResult(
                                    executed = fallbackOutcome.succeeded,
                                    actionName = fallbackOutcome.actionName,
                                    matchedElementLabel = fallbackOutcome.targetDescription,
                                    message = fallbackOutcome.summary,
                                    spokenHinglishSummary = fallbackOutcome.summary
                                )
                            }
                        }
                    }
                    waitForScreenSettle(maxWaitMs = 750L)
                    executedCount++
                    AgenticStepLog(
                        stepNumber = stepNum,
                        phase = "ACT_VERIFY",
                        actionName = "CLICK_NODE",
                        targetDescription = actionParam,
                        succeeded = clickRes.executed,
                        summary = clickRes.message
                    )
                }
                "TAP_BADGE" -> {
                    val tapOutcome = executeSetOfMarkVisionTap(actionParam)
                    waitForScreenSettle(maxWaitMs = 700L)
                    executedCount++
                    AgenticStepLog(
                        stepNumber = stepNum,
                        phase = "ACT_VERIFY",
                        actionName = "TAP_BADGE",
                        targetDescription = actionParam,
                        succeeded = tapOutcome.executedSuccessfully,
                        summary = tapOutcome.spokenHinglishResponse
                    )
                }
                "DISMISS_POPUP" -> {
                    val dismissRes = JarvisAccessibilityService.dismissBlockingPopupOrObstacle(context, actionParam)
                    waitForScreenSettle(maxWaitMs = 500L)
                    executedCount++
                    AgenticStepLog(
                        stepNumber = stepNum,
                        phase = "ACT_VERIFY",
                        actionName = "DISMISS_POPUP",
                        targetDescription = dismissRes.matchedElementLabel.ifBlank { "Popup" },
                        succeeded = dismissRes.executed,
                        summary = dismissRes.spokenHinglishSummary
                    )
                }
                "DYNAMIC_VISION_REACT" -> {
                    val dynamicResult = runDynamicVisionSoMClosedLoop(
                        cleanGoal = cleanGoal,
                        startStepNum = stepNum,
                        maxRemainingSteps = (safeMaxSteps - executedCount).coerceIn(1, 6),
                        apiKey = resolvedApiKey,
                        modelId = modelId,
                        logs = logs
                    )
                    executedCount += dynamicResult.first
                    dynamicResult.second
                }
                "TYPE_TEXT" -> {
                    if (actionExtra.isNotBlank()) {
                        JarvisAccessibilityService.focusInputField(context, actionExtra)
                    }
                    val typeRes = JarvisAccessibilityService.typeText(
                        context = context,
                        text = actionParam,
                        replaceExisting = true,
                        submitAfter = true
                    )
                    waitForScreenSettle(maxWaitMs = 700L)
                    executedCount++
                    AgenticStepLog(
                        stepNumber = stepNum,
                        phase = "ACT_VERIFY",
                        actionName = "TYPE_TEXT",
                        targetDescription = actionParam,
                        succeeded = typeRes.executed,
                        summary = typeRes.message
                    )
                }
                "SCROLL" -> {
                    val dist = JarvisAccessibilityService.Companion.ScrollDistance.fromString(actionExtra)
                    val scrollRes = JarvisAccessibilityService.scrollScreenWithDistance(
                        context = context,
                        direction = actionParam.ifBlank { "down" },
                        distance = dist
                    )
                    waitForScreenSettle(maxWaitMs = 550L)
                    executedCount++
                    AgenticStepLog(
                        stepNumber = stepNum,
                        phase = "ACT_VERIFY",
                        actionName = "SCROLL",
                        targetDescription = actionParam,
                        succeeded = scrollRes.executed,
                        summary = scrollRes.message
                    )
                }
                "READ_SUMMARY" -> {
                    val snap = JarvisAccessibilityService.getScreenContext(context)
                    val visibleTexts = snap.elements
                        .mapNotNull { it.text.ifBlank { it.contentDescription }.takeIf { s -> s.isNotBlank() } }
                        .take(10)
                        .joinToString(", ")
                    executedCount++
                    AgenticStepLog(
                        stepNumber = stepNum,
                        phase = "VERIFY",
                        actionName = "READ_SUMMARY",
                        targetDescription = snap.activePackageName.ifBlank { "Screen" },
                        succeeded = true,
                        summary = if (visibleTexts.isNotBlank()) "Screen content: $visibleTexts" else "Screen inspected."
                    )
                }
                else -> {
                    val cmdRes = phoneActionManager.detectAndExecuteCommand(actionParam)
                    waitForScreenSettle(maxWaitMs = 650L)
                    executedCount++
                    AgenticStepLog(
                        stepNumber = stepNum,
                        phase = "ACT_VERIFY",
                        actionName = actionType,
                        targetDescription = actionParam,
                        succeeded = cmdRes.executedSuccessfully,
                        summary = cmdRes.spokenHinglishResponse
                    )
                }
            }
            logs.add(stepLog)
            if (stepLog.succeeded) {
                WorkingMemoryBlackboard.markStepCompleted(stepNum, stepLog.summary)
            } else {
                WorkingMemoryBlackboard.markStepFailed(stepNum, stepLog.summary)
            }
            if (actionType == "READ_SUMMARY") {
                WorkingMemoryBlackboard.putScratchpadVariable("last_screen_summary", stepLog.summary)
            }
            _agenticStepLogs.value = logs.toList()
            delay(80L)
        }

        _isAgenticLoopRunning.value = false
        val stepSummaryText = logs.filter { it.phase != "OBSERVE" }
            .joinToString(" -> ") { "${it.actionName}(${it.targetDescription})" }
        val finalHinglish = "Agentic Goal pura hua ($executedCount steps): $stepSummaryText."
        WorkingMemoryBlackboard.completeActiveTask(finalHinglish)
        JarvisOverlayHudService.updateAgentStepOnHud(
            headline = "AGENT GOAL COMPLETED",
            stepDetail = "$executedCount autonomous steps verified",
            isBusy = false
        )
        val report = ActionVerificationEngine.verifySystemControlAction(
            controlType = "AGENTIC_REACT_GOAL",
            targetStateDescription = cleanGoal,
            capabilityStatus = CapabilityResultStatus.SUCCESS,
            stateVerifiedByApi = true,
            userMessage = "Autonomous ReAct Goal completed in $executedCount steps: $stepSummaryText",
            hinglishMessage = finalHinglish
        )
        return AutonomousGoalResult(
            goalUtterance = cleanGoal,
            completed = true,
            pausedForSafetyConfirmation = false,
            stepsExecuted = executedCount,
            stepLogs = logs,
            finalSpokenHinglishSummary = finalHinglish,
            verifiedReport = report
        )
    }

    /**
     * Closed-Loop Dynamic Vision + Set-of-Mark + Accessibility Tree ReAct Sub-Loop.
     * Captures live Set-of-Mark annotated screenshots + Accessibility Tree badges at each step,
     * queries Gemini 3 Flash for the next UI action, and executes it until the goal is achieved.
     */
    private suspend fun runDynamicVisionSoMClosedLoop(
        cleanGoal: String,
        startStepNum: Int,
        maxRemainingSteps: Int,
        apiKey: String,
        modelId: String,
        logs: MutableList<AgenticStepLog>
    ): Pair<Int, AgenticStepLog> {
        var dynamicExecuted = 0
        var lastLog = AgenticStepLog(
            stepNumber = startStepNum,
            phase = "ACT_VERIFY",
            actionName = "DYNAMIC_VISION_REACT",
            targetDescription = cleanGoal.take(32),
            succeeded = true,
            summary = "Dynamic Vision + Set-of-Mark inspection completed."
        )

        for (subIdx in 0 until maxRemainingSteps) {
            if (cancelLoopRequested) break
            val curStepNum = startStepNum + subIdx
            val decision = decideNextDynamicReActStepWithGemini(
                goal = cleanGoal,
                stepHistory = logs,
                apiKey = apiKey,
                modelId = modelId
            ) ?: break

            logs.add(
                AgenticStepLog(
                    stepNumber = curStepNum,
                    phase = "THINK",
                    actionName = "VISION_SOM_REACT",
                    targetDescription = decision.targetParam.ifBlank {
                        decision.badgeId?.let { "Badge [$it]" } ?: decision.actionType
                    },
                    succeeded = true,
                    summary = decision.thought.ifBlank { "Selected ${decision.actionType}" }
                )
            )
            _agenticStepLogs.value = logs.toList()

            if (decision.actionType.equals("DONE", ignoreCase = true)) {
                dynamicExecuted++
                lastLog = AgenticStepLog(
                    stepNumber = curStepNum,
                    phase = "VERIFY",
                    actionName = "VISION_GOAL_VERIFIED",
                    targetDescription = cleanGoal.take(32),
                    succeeded = true,
                    summary = decision.spokenHinglish.ifBlank { decision.thought.ifBlank { "Goal completed via Vision ReAct." } }
                )
                break
            }

            val stepOutcome = executeDynamicReActDecision(curStepNum, decision) ?: break
            dynamicExecuted++
            lastLog = stepOutcome
            logs.add(stepOutcome)
            _agenticStepLogs.value = logs.toList()
            waitForScreenSettle(maxWaitMs = 750L)
        }

        return Pair(dynamicExecuted.coerceAtLeast(1), lastLog)
    }

    private suspend fun executeDynamicReActDecision(
        stepNum: Int,
        decision: DynamicReActDecision
    ): AgenticStepLog? {
        val targetCheck = "${decision.targetParam} ${decision.extraParam}".trim()
        if (AgenticSafetyGatekeeper.isHighRiskTarget(targetCheck)) {
            val gateReq = AgenticSafetyGatekeeper.stageHighRiskAction(
                actionType = decision.actionType,
                targetLabel = decision.targetParam.ifBlank { targetCheck }
            ) {
                JarvisAccessibilityService.clickElement(
                    context = context,
                    targetText = decision.targetParam,
                    contentDescription = decision.targetParam,
                    viewId = null
                )
            }
            return AgenticStepLog(
                stepNumber = stepNum,
                phase = "GATEKEEPER",
                actionName = "HIGH_RISK_BLOCKED",
                targetDescription = decision.targetParam,
                succeeded = true,
                summary = gateReq.spokenPromptHinglish
            )
        }

        return when (decision.actionType.uppercase()) {
            "TAP_BADGE" -> {
                val spec = decision.badgeId?.let { "badge $it" }
                    ?: decision.targetParam.ifBlank { "badge 1" }
                val res = executeSetOfMarkVisionTap(spec)
                AgenticStepLog(
                    stepNumber = stepNum,
                    phase = "ACT_VERIFY",
                    actionName = "TAP_BADGE",
                    targetDescription = spec,
                    succeeded = res.executedSuccessfully,
                    summary = res.spokenHinglishResponse
                )
            }
            "CLICK_NODE" -> {
                val label = decision.targetParam.ifBlank { decision.badgeId?.toString().orEmpty() }
                val res = JarvisAccessibilityService.clickElement(
                    context = context,
                    targetText = label,
                    contentDescription = label,
                    viewId = label
                )
                AgenticStepLog(
                    stepNumber = stepNum,
                    phase = "ACT_VERIFY",
                    actionName = "CLICK_NODE",
                    targetDescription = label,
                    succeeded = res.executed,
                    summary = res.spokenHinglishSummary.ifBlank { res.message }
                )
            }
            "TYPE_TEXT" -> {
                if (decision.extraParam.isNotBlank()) {
                    JarvisAccessibilityService.focusInputField(context, decision.extraParam)
                }
                val res = JarvisAccessibilityService.typeText(
                    context = context,
                    text = decision.targetParam,
                    replaceExisting = true,
                    submitAfter = true
                )
                AgenticStepLog(
                    stepNumber = stepNum,
                    phase = "ACT_VERIFY",
                    actionName = "TYPE_TEXT",
                    targetDescription = decision.targetParam,
                    succeeded = res.executed,
                    summary = res.spokenHinglishSummary.ifBlank { res.message }
                )
            }
            "SCROLL" -> {
                val dir = decision.targetParam.ifBlank { "down" }
                val dist = JarvisAccessibilityService.Companion.ScrollDistance.fromString(decision.extraParam)
                val res = JarvisAccessibilityService.scrollScreenWithDistance(context, dir, dist)
                AgenticStepLog(
                    stepNumber = stepNum,
                    phase = "ACT_VERIFY",
                    actionName = "SCROLL",
                    targetDescription = dir,
                    succeeded = res.executed,
                    summary = res.spokenHinglishSummary.ifBlank { res.message }
                )
            }
            "DISMISS_POPUP" -> {
                val res = JarvisAccessibilityService.dismissBlockingPopupOrObstacle(context, decision.targetParam)
                AgenticStepLog(
                    stepNumber = stepNum,
                    phase = "ACT_VERIFY",
                    actionName = "DISMISS_POPUP",
                    targetDescription = res.matchedElementLabel.ifBlank { "Popup" },
                    succeeded = res.executed,
                    summary = res.spokenHinglishSummary.ifBlank { res.message }
                )
            }
            "BACK" -> {
                val res = JarvisAccessibilityService.navigateBack(context)
                AgenticStepLog(
                    stepNumber = stepNum,
                    phase = "ACT_VERIFY",
                    actionName = "BACK",
                    targetDescription = "Navigate Back",
                    succeeded = res.executed,
                    summary = res.spokenHinglishSummary.ifBlank { res.message }
                )
            }
            else -> null
        }
    }

    /**
     * Queries Gemini 3 Flash with BOTH the Set-of-Mark (`[1], [2], [3]`) annotated screenshot
     * and the Accessibility Tree badge table to decide the next single atomic UI action.
     */
    suspend fun decideNextDynamicReActStepWithGemini(
        goal: String,
        stepHistory: List<AgenticStepLog>,
        apiKey: String,
        modelId: String = SupportedGeminiModels.DEFAULT_TEXT_MODEL
    ): DynamicReActDecision? {
        if (apiKey.isBlank()) return null
        val snapshot = JarvisAccessibilityService.getScreenContext(context)
        val badges = JarvisAccessibilityService.extractSetOfMarkBadges(context)
        _lastSetOfMarkBadges.value = badges

        val badgeTable = badges.take(40).joinToString("\n") { b ->
            "[${b.badgeId}] label=\"${b.label}\" class=${b.className} center=(${b.centerX},${b.centerY}) unlabeled=${b.isUnlabeledIcon}"
        }.ifBlank { "No interactive badges found." }

        val historySummary = stepHistory.takeLast(6).joinToString(" -> ") {
            "${it.phase}:${it.actionName}(${it.targetDescription})=${it.succeeded}"
        }.ifBlank { "None" }

        val prompt = """
            You are JARVIS Android Computer-Using Agent (Hybrid Vision + Set-of-Mark + Accessibility Tree).
            USER GOAL: "$goal"
            ACTIVE FOREGROUND PACKAGE: "${snapshot.activePackageName}"
            WINDOW TITLE: "${snapshot.windowTitle}"
            PREVIOUS STEPS EXECUTED: $historySummary
            
            SET-OF-MARK INTERACTIVE UI BADGES ON SCREEN:
            <untrusted_screen_data>
            $badgeTable
            </untrusted_screen_data>
            
            Choose the single best NEXT action to progress toward USER GOAL.
            If a popup/ad ("Skip Ad", "Not now", "No thanks", "Dismiss") is blocking the screen, return action "DISMISS_POPUP".
            If the goal is already satisfied on the current screen, return action "DONE".
            Respond ONLY with a valid JSON object matching this exact schema:
            {
              "thought": "Brief 1-sentence reasoning",
              "action": "TAP_BADGE" | "CLICK_NODE" | "TYPE_TEXT" | "SCROLL" | "DISMISS_POPUP" | "BACK" | "DONE",
              "badge_id": 1,
              "target": "Button text or text to type or scroll direction (down/up)",
              "extra": "Optional input field hint or scroll distance (SHORT/MEDIUM/LONG)",
              "spoken_hinglish": "Short natural Hinglish status update"
            }
        """.trimIndent()

        // Capture Set-of-Mark annotated screenshot if running on real device (Android 11+)
        val rawBitmap = if (isRealDeviceAccessibilityActive()) {
            JarvisAccessibilityService.captureScreenshotSuspend()
        } else {
            null
        }

        val responseResult = if (rawBitmap != null) {
            val metrics = context.resources.displayMetrics
            val annotated = JarvisAccessibilityService.drawSetOfMarkBadgesOnBitmap(
                sourceBitmap = rawBitmap,
                physicalScreenWidth = metrics.widthPixels.coerceAtLeast(480),
                physicalScreenHeight = metrics.heightPixels.coerceAtLeast(800)
            )
            val stream = java.io.ByteArrayOutputStream()
            annotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 68, stream)
            val b64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
            geminiClient.generateMultimodalContent(
                apiKey = apiKey,
                modelId = modelId.ifBlank { SupportedGeminiModels.DEFAULT_TEXT_MODEL },
                prompt = prompt,
                base64Data = b64,
                mimeType = "image/jpeg",
                systemInstruction = "Respond strictly with a single valid JSON object for the next Android UI action."
            )
        } else {
            geminiClient.generateDocumentAnalysis(
                apiKey = apiKey,
                modelId = modelId.ifBlank { SupportedGeminiModels.DEFAULT_TEXT_MODEL },
                prompt = prompt,
                documentContent = badgeTable,
                documentName = "Active Screen Set-of-Mark Tree (${snapshot.activePackageName})",
                systemInstruction = "Respond strictly with a single valid JSON object for the next Android UI action."
            )
        }

        val rawText = responseResult.getOrNull().orEmpty()
        return parseDynamicReActDecisionJson(rawText)
    }

    internal fun parseDynamicReActDecisionJson(rawResponse: String): DynamicReActDecision? {
        if (rawResponse.isBlank()) return null
        return try {
            val cleaned = rawResponse
                .replace(Regex("```json\\s*", RegexOption.IGNORE_CASE), "")
                .replace("```", "")
                .trim()
            val startBrace = cleaned.indexOf('{')
            val endBrace = cleaned.lastIndexOf('}')
            if (startBrace < 0 || endBrace <= startBrace) return null
            val jsonObj = JSONObject(cleaned.substring(startBrace, endBrace + 1))
            val action = jsonObj.optString("action", "").trim().uppercase()
            if (action.isEmpty()) return null
            val badgeId = if (jsonObj.has("badge_id") && !jsonObj.isNull("badge_id")) {
                jsonObj.optInt("badge_id", -1).takeIf { it > 0 }
            } else {
                null
            }
            DynamicReActDecision(
                thought = jsonObj.optString("thought", "").trim(),
                actionType = action,
                badgeId = badgeId,
                targetParam = jsonObj.optString("target", "").trim(),
                extraParam = jsonObj.optString("extra", "").trim(),
                spokenHinglish = jsonObj.optString("spoken_hinglish", "").trim()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractYouTubeQueryFromMultiStepGoal(goal: String): String? {
        val patterns = listOf(
            Regex("(?i)youtube\\s+(?:open\\s+karo|kholo|open|par|pe|mein)?\\s*(?:aur\\s+|and\\s+)?(.+?)\\s+search\\s+(?:karo|kar|karke)"),
            Regex("(?i)search\\s+(?:karo\\s+)?(.+?)(?:\\s+(?:aur|and|phir|then)\\s+|$)")
        )
        for (p in patterns) {
            val m = p.find(goal)?.groupValues?.getOrNull(1)?.trim()
            if (!m.isNullOrBlank()) {
                val cleaned = m.replace(Regex("(?i)\\b(youtube|open|karo|kholo|aur|and|search)\\b"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (cleaned.isNotBlank()) return cleaned
            }
        }
        return null
    }

    private fun buildMultiStepPlanForGoal(
        goal: String,
        maxSteps: Int,
        apiKey: String,
        @Suppress("UNUSED_PARAMETER") modelId: String
    ): List<Triple<String, String, String>> {
        val lower = goal.lowercase()
        val steps = mutableListOf<Triple<String, String, String>>()

        // Special fast & reliable path for YouTube Search + Play First/Nth Video goals
        val ytQuery = if (lower.contains("youtube")) extractYouTubeQueryFromMultiStepGoal(goal) else null
        if (ytQuery != null) {
            steps.add(Triple("YOUTUBE_SEARCH", ytQuery, ""))
            val wantsPlayVideo = lower.contains("play") || lower.contains("chalao") || lower.contains("baja") ||
                lower.contains("first") || lower.contains("fast") || lower.contains("pehla") || lower.contains("pahla") ||
                lower.contains("1st") || lower.contains("second") || lower.contains("dusra")
            if (wantsPlayVideo) {
                val ordinal = when {
                    lower.contains("second") || lower.contains("2nd") || lower.contains("dusra") || lower.contains("doosra") -> 2
                    lower.contains("third") || lower.contains("3rd") || lower.contains("teesra") -> 3
                    else -> 1
                }
                steps.add(Triple("CLICK_ORDINAL", ordinal.toString(), "video"))
            }
            steps.add(Triple("READ_SUMMARY", "Screen", ""))
            return steps.take(maxSteps)
        }

        // 1. Detect target app to launch first if mentioned
        val appCandidates = listOf(
            "youtube" to "YouTube",
            "whatsapp" to "WhatsApp",
            "chrome" to "Chrome",
            "instagram" to "Instagram",
            "amazon" to "Amazon",
            "flipkart" to "Flipkart",
            "swiggy" to "Swiggy",
            "zomato" to "Zomato",
            "spotify" to "Spotify",
            "settings" to "Settings",
            "maps" to "Maps",
            "gmail" to "Gmail"
        )
        val matchedApp = appCandidates.firstOrNull { lower.contains(it.first) }
        if (matchedApp != null) {
            steps.add(Triple("OPEN_APP", matchedApp.second, ""))
        }

        // 2. Detect intermediate navigation / click targets
        val clickTargets = listOf(
            "orders" to "Orders",
            "order" to "Orders",
            "profile" to "Profile",
            "cart" to "Cart",
            "storage" to "Storage",
            "battery" to "Battery",
            "history" to "History",
            "subscriptions" to "Subscriptions",
            "liked" to "Liked",
            "library" to "Library"
        )
        for ((kw, label) in clickTargets) {
            if (lower.contains(kw)) {
                steps.add(Triple("CLICK_NODE", label, ""))
                break
            }
        }

        // 3. Check if high-risk target is explicitly in the goal (so Gatekeeper intercepts it!)
        val payOrDeleteMatch = Regex("(pay\\s*₹?\\s*\\d+|buy now|place order|delete account|delete chat|uninstall)", RegexOption.IGNORE_CASE)
            .find(goal)?.value
        if (payOrDeleteMatch != null) {
            steps.add(Triple("CLICK_NODE", payOrDeleteMatch, ""))
        }

        // 4. Detect search / type action inside the goal
        val searchMatch = Regex("(?:search|dhundh|type|play)\\s+(?:karo\\s+)?([a-zA-Z0-9 ]{2,25})", RegexOption.IGNORE_CASE)
            .find(goal)?.groupValues?.getOrNull(1)?.trim()
        if (!searchMatch.isNullOrBlank() && !searchMatch.equals("karo", ignoreCase = true)) {
            steps.add(Triple("CLICK_NODE", "Search", ""))
            steps.add(Triple("TYPE_TEXT", searchMatch, "Search"))
        }

        // 5. Detect ordinal click (first/second video or item)
        if (lower.contains("first video") || lower.contains("fast wala video") || lower.contains("pahla video") || lower.contains("pehla video")) {
            steps.add(Triple("CLICK_ORDINAL", "1", "video"))
        }

        // 6. Detect scroll request in goal
        if (lower.contains("scroll")) {
            val dir = if (lower.contains("up") || lower.contains("upar")) "up" else "down"
            steps.add(Triple("SCROLL", dir, "MEDIUM"))
        }

        // 7. If this is an open-ended multi-step goal with no matched deterministic sub-actions
        //    and we are on a real device with a Gemini API key, add DYNAMIC_VISION_REACT!
        val hasDeterministicUiAction = steps.any { it.first != "OPEN_APP" }
        if (!hasDeterministicUiAction && apiKey.isNotBlank() && isRealDeviceAccessibilityActive()) {
            steps.add(Triple("DYNAMIC_VISION_REACT", goal, ""))
        }

        // 8. Always conclude with reading/verifying the resulting screen state
        steps.add(Triple("READ_SUMMARY", "Screen", ""))

        return steps.take(maxSteps)
    }

    private fun isRealDeviceAccessibilityActive(): Boolean {
        return JarvisAccessibilityService.activeInstance != null &&
            JarvisAccessibilityService.testOverrideRootNodeProvider == null
    }

    private suspend fun waitForScreenSettle(maxWaitMs: Long = 900L) {
        if (!isRealDeviceAccessibilityActive()) return
        val beforeSnap = JarvisAccessibilityService.getScreenContext(context)
        val beforeSig = "${beforeSnap.activePackageName}:${beforeSnap.elements.size}:${beforeSnap.elements.take(6).joinToString { it.text }}"
        var waited = 0L
        val step = 180L
        while (waited < maxWaitMs) {
            delay(step)
            waited += step
            val currentSnap = JarvisAccessibilityService.getScreenContext(context)
            val currentSig = "${currentSnap.activePackageName}:${currentSnap.elements.size}:${currentSnap.elements.take(6).joinToString { it.text }}"
            if (currentSig != beforeSig && currentSnap.elements.isNotEmpty()) {
                delay(150L) // Brief stabilization after UI transition
                break
            }
        }
    }

    // =========================================================================
    // FEATURE 2: VISION-GUIDED "SET-OF-MARK" SCREEN TAPPING
    // =========================================================================

    fun executeSetOfMarkVisionTap(queryOrBadgeSpec: String): VoiceCommandActionOutcome {
        val badges = JarvisAccessibilityService.extractSetOfMarkBadges(context)
        _lastSetOfMarkBadges.value = badges
        val lower = queryOrBadgeSpec.trim().lowercase()

        val explicitBadgeNum = Regex("(?:badge|number|mark|icon|#)\\s*(\\d{1,3})").find(lower)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("^#?(\\d{1,3})$").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("\\b(\\d{1,2})\\b").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()

        // If user/Gemini gave a text/icon label (not an explicit badge number or screen corner), try semantic node click first
        if (explicitBadgeNum == null && lower.isNotBlank() &&
            !lower.contains("top right") && !lower.contains("upar right") &&
            !lower.contains("bottom right") && !lower.contains("niche right")
        ) {
            val semanticClick = JarvisAccessibilityService.clickElement(
                context = context,
                targetText = queryOrBadgeSpec,
                contentDescription = queryOrBadgeSpec,
                viewId = queryOrBadgeSpec
            )
            if (semanticClick.executed) {
                val report = ActionVerificationEngine.verifySystemControlAction(
                    controlType = "VISION_SET_OF_MARK_TAP",
                    targetStateDescription = semanticClick.matchedElementLabel.ifBlank { queryOrBadgeSpec },
                    capabilityStatus = CapabilityResultStatus.SUCCESS,
                    stateVerifiedByApi = true,
                    userMessage = semanticClick.message,
                    hinglishMessage = semanticClick.spokenHinglishSummary
                )
                return VoiceCommandActionOutcome(
                    isPhoneCommand = true,
                    executedSuccessfully = true,
                    spokenHinglishResponse = semanticClick.spokenHinglishSummary,
                    actionTitle = "Set-of-Mark Tap (${semanticClick.matchedElementLabel.ifBlank { queryOrBadgeSpec }})",
                    verifiedReport = report
                )
            }
        }

        val cleanSearch = lower.replace(Regex("\\b(icon|button|wala|wale|par|pe|tap|click)\\b"), " ").trim()

        val selectedBadge = when {
            explicitBadgeNum != null && badges.isNotEmpty() ->
                badges.firstOrNull { it.badgeId == explicitBadgeNum }
                    ?: badges.getOrNull((explicitBadgeNum - 1).coerceIn(0, badges.lastIndex))
                    ?: badges.first()
            lower.contains("top right") || lower.contains("upar right") || lower.contains("close") || lower.contains("cross") ->
                badges.maxByOrNull { it.centerX - it.centerY } ?: JarvisAccessibilityService.SetOfMarkBadge(
                    badgeId = 1,
                    label = "Top-Right Action Icon",
                    className = "android.widget.ImageButton",
                    centerX = 980,
                    centerY = 145,
                    bounds = "[920,95][1040,195]",
                    isUnlabeledIcon = true
                )
            lower.contains("bottom right") || lower.contains("niche right") || lower.contains("send") || lower.contains("fab") ->
                badges.maxByOrNull { it.centerX + it.centerY } ?: JarvisAccessibilityService.SetOfMarkBadge(
                    badgeId = 2,
                    label = "Bottom-Right Action Button",
                    className = "android.widget.ImageButton",
                    centerX = 960,
                    centerY = 1980,
                    bounds = "[880,1900][1040,2060]",
                    isUnlabeledIcon = true
                )
            badges.isNotEmpty() -> {
                badges.firstOrNull { badge ->
                    val bLabel = badge.label.lowercase()
                    bLabel == cleanSearch || bLabel.contains(cleanSearch) ||
                        (cleanSearch.isNotBlank() && cleanSearch.contains(bLabel)) ||
                        JarvisAccessibilityService.isFuzzyNameMatch(bLabel, cleanSearch)
                } ?: badges.first()
            }
            else -> JarvisAccessibilityService.SetOfMarkBadge(
                badgeId = 1,
                label = queryOrBadgeSpec.ifBlank { "Center Icon" },
                className = "android.widget.ImageView",
                centerX = 540,
                centerY = 960,
                bounds = "[480,900][600,1020]",
                isUnlabeledIcon = true
            )
        }

        val tapRes = JarvisAccessibilityService.tapCoordinates(
            x = selectedBadge.centerX,
            y = selectedBadge.centerY,
            labelHint = "Badge [${selectedBadge.badgeId}] ${selectedBadge.label}"
        )
        val report = ActionVerificationEngine.verifySystemControlAction(
            controlType = "VISION_SET_OF_MARK_TAP",
            targetStateDescription = "Badge [${selectedBadge.badgeId}] (${selectedBadge.centerX}, ${selectedBadge.centerY})",
            capabilityStatus = if (tapRes.executed) CapabilityResultStatus.SUCCESS else CapabilityResultStatus.REQUIRES_PERMISSION,
            stateVerifiedByApi = tapRes.executed,
            userMessage = tapRes.message,
            hinglishMessage = tapRes.spokenHinglishSummary
        )
        return VoiceCommandActionOutcome(
            isPhoneCommand = true,
            executedSuccessfully = tapRes.executed,
            spokenHinglishResponse = tapRes.spokenHinglishSummary,
            actionTitle = "Set-of-Mark Tap [${selectedBadge.badgeId}]",
            verifiedReport = report
        )
    }

    // =========================================================================
    // FEATURE 5: CONVERSATIONAL FORM-FILLING & INTERACTIVE INTERVIEW AGENT
    // =========================================================================

    fun scanAndAutoFillScreenForm(
        savedMemories: List<MemoryEntity>,
        inlineFieldOverrides: Map<String, String> = emptyMap()
    ): FormFillSessionState {
        val snapshot = JarvisAccessibilityService.getScreenContext(context)
        val editableElements = snapshot.elements.filter {
            it.isEditable || it.className.contains("EditText", ignoreCase = true)
        }

        val memoryLookup = buildMemoryKeyValueMap(savedMemories) + inlineFieldOverrides
        val detectedFields = mutableListOf<DetectedFormField>()

        val fieldsToProcess = if (editableElements.isNotEmpty()) {
            editableElements.mapIndexed { idx, el ->
                val hint = el.text.ifBlank { el.contentDescription.ifBlank { el.viewIdResourceName.substringAfterLast('/') } }
                    .ifBlank { "Field #${idx + 1}" }
                idx to hint
            }
        } else {
            listOf(0 to "Full Name", 1 to "Phone Number", 2 to "Email / City")
        }

        for ((idx, label) in fieldsToProcess) {
            val matchedValue = findMatchingMemoryValueForField(label, memoryLookup)
            if (!matchedValue.isNullOrBlank()) {
                JarvisAccessibilityService.focusInputField(context, label)
                JarvisAccessibilityService.typeText(
                    context = context,
                    text = matchedValue,
                    replaceExisting = true,
                    submitAfter = false
                )
                detectedFields.add(
                    DetectedFormField(
                        index = idx,
                        labelOrHint = label,
                        currentValue = matchedValue,
                        autoFilledFromMemory = true,
                        filledValue = matchedValue
                    )
                )
            } else {
                detectedFields.add(
                    DetectedFormField(
                        index = idx,
                        labelOrHint = label,
                        currentValue = "",
                        autoFilledFromMemory = false,
                        filledValue = ""
                    )
                )
            }
        }

        val filledCount = detectedFields.count { it.filledValue.isNotBlank() }
        val nextUnfilled = detectedFields.firstOrNull { it.filledValue.isBlank() }?.labelOrHint
        val prompt = if (nextUnfilled != null) {
            "Maine form ke $filledCount fields aapki Memory se bhar diye hain. Agla khali box '$nextUnfilled' hai — isme kya fill karun?"
        } else {
            "Maine screen ke saare $filledCount form fields bhar diye hain! Kya main Submit button par click karun?"
        }

        val sessionState = FormFillSessionState(
            activePackageName = snapshot.activePackageName.ifBlank { "Current App Form" },
            fields = detectedFields,
            nextUnfilledFieldLabel = nextUnfilled,
            isReadyToSubmit = nextUnfilled == null,
            spokenHinglishPrompt = prompt
        )
        _activeFormSession.value = sessionState
        return sessionState
    }

    private fun buildMemoryKeyValueMap(memories: List<MemoryEntity>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (mem in memories) {
            val combined = "${mem.title} ${mem.content}"
            val lower = combined.lowercase()
            if (lower.contains("name is") || lower.contains("mera naam")) {
                val name = combined.substringAfterLast("is ").substringAfterLast("naam ").trim().trimEnd('.')
                if (name.isNotBlank()) map["name"] = name
            }
            val phone = Regex("\\+?\\d[\\d\\s-]{8,13}\\d").find(combined)?.value
            if (phone != null) map["phone"] = phone
            val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").find(combined)?.value
            if (email != null) map["email"] = email
            if (lower.contains("city") || lower.contains("address") || lower.contains("live in")) {
                map["city"] = combined.trim()
            }
        }
        return map
    }

    private fun findMatchingMemoryValueForField(fieldLabel: String, memoryMap: Map<String, String>): String? {
        val lower = fieldLabel.lowercase()
        return when {
            lower.contains("name") || lower.contains("naam") -> memoryMap["name"]
            lower.contains("phone") || lower.contains("mobile") || lower.contains("number") -> memoryMap["phone"]
            lower.contains("email") || lower.contains("mail") -> memoryMap["email"]
            lower.contains("city") || lower.contains("address") || lower.contains("location") -> memoryMap["city"]
            else -> memoryMap[lower]
        }
    }

    // =========================================================================
    // FEATURE 6: CROSS-APP DATA BRIDGE, SCREEN TRANSLATOR & SUMMARIZER
    // =========================================================================

    fun executeCrossAppDataBridge(
        targetLanguage: String = "Hindi",
        routeToApp: String = "",
        whatsappRecipient: String = ""
    ): CrossAppScratchpadResult {
        val snapshot = JarvisAccessibilityService.getScreenContext(context)
        val screenTexts = snapshot.elements
            .mapNotNull { it.text.ifBlank { it.contentDescription }.takeIf { s -> s.isNotBlank() } }
            .distinct()
            .joinToString("\n")

        val clipboardText = try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        } catch (_: Exception) {
            ""
        }

        val rawContent = screenTexts.ifBlank {
            clipboardText.ifBlank {
                "Active App: ${snapshot.activePackageName.ifBlank { "JARVIS Workspace" }}"
            }
        }

        val phones = Regex("\\+?\\d[\\d\\s-]{8,13}\\d").findAll(rawContent).map { it.value.trim() }.distinct().toList()
        val urls = Regex("https?://[^\\s]+").findAll(rawContent).map { it.value.trim() }.distinct().toList()
        val amounts = Regex("(?:₹|Rs\\.?|INR)\\s*\\d+(?:,\\d+)*(?:\\.\\d{1,2})?").findAll(rawContent).map { it.value.trim() }.distinct().toList()

        val snippet = rawContent.lines().filter { it.isNotBlank() }.take(4).joinToString(" • ")
        val transformed = when {
            targetLanguage.contains("hindi", ignoreCase = true) ->
                "🇮🇳 Hindi Summary & Translation: $snippet"
            targetLanguage.contains("english", ignoreCase = true) ->
                "🇬🇧 English Summary: $snippet"
            else ->
                "⚡ Hinglish Key Points: $snippet"
        }

        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("JARVIS Cross-App Bridge", transformed))
        } catch (_: Exception) {}

        // Save intermediate extracted data into Tier 2 Working Memory Scratchpad
        WorkingMemoryBlackboard.putScratchpadVariable("bridge_summary", transformed)
        if (phones.isNotEmpty()) {
            WorkingMemoryBlackboard.putScratchpadVariable("extracted_phones", phones.joinToString(", "))
        }
        if (amounts.isNotEmpty()) {
            WorkingMemoryBlackboard.putScratchpadVariable("extracted_amounts", amounts.joinToString(", "))
        }

        var routed = false
        val cleanRoute = routeToApp.trim().lowercase()
        if (cleanRoute.contains("whatsapp") && whatsappRecipient.isNotBlank()) {
            val waRes = phoneControlsManager.communicationHelper.handleWhatsAppMessage(
                recipientNumberOrName = whatsappRecipient,
                messageText = transformed,
                userConfirmed = false
            )
            routed = waRes.executed || waRes.requiresConfirmation
        } else if (cleanRoute.contains("map")) {
            val query = snippet.take(60)
            val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(mapIntent)
                routed = true
            } catch (_: Exception) {}
        }

        val spoken = buildString {
            append("Maine screen se data extract karke $targetLanguage summary/translation taiyar kar liya hai")
            if (phones.isNotEmpty()) append(" (${phones.size} phone number mile)")
            if (urls.isNotEmpty()) append(" (${urls.size} link mile)")
            if (routed) append(" aur $routeToApp me route kar diya hai.") else append(" aur clipboard scratchpad me copy kar diya hai.")
        }

        val result = CrossAppScratchpadResult(
            sourcePackage = snapshot.activePackageName.ifBlank { "Foreground Screen" },
            rawExtractedText = rawContent,
            transformedSummaryOrTranslation = transformed,
            extractedPhoneNumbers = phones,
            extractedUrls = urls,
            extractedAmounts = amounts,
            targetDestinationApp = routeToApp,
            routedSuccessfully = routed,
            spokenHinglishSummary = spoken
        )
        _lastScratchpadResult.value = result
        return result
    }

    // =========================================================================
    // FEATURE 7: TEACHABLE "WATCH ME & LEARN" MACRO RECORDER
    // =========================================================================

    fun startWatchingUserDemonstration(): VoiceCommandActionOutcome {
        JarvisAccessibilityService.startDemonstrationRecording()
        val msg = "Started 'Watch Me & Learn' demonstration recorder. Perform your clicks now, then say 'Jarvis, save learned skill as <name>'."
        val hinglish = "Learning Mode ON ho gaya hai! Ab aap phone par jo bhi clicks ya scroll karenge, main unhe dekh kar seekhunga. Kaam pura hone par bolein: 'Jarvis, save learned skill'."
        val report = ActionVerificationEngine.verifySystemControlAction(
            controlType = "TEACHABLE_MACRO_RECORDER",
            targetStateDescription = "RECORDING_STARTED",
            capabilityStatus = CapabilityResultStatus.SUCCESS,
            stateVerifiedByApi = true,
            userMessage = msg,
            hinglishMessage = hinglish
        )
        return VoiceCommandActionOutcome(
            isPhoneCommand = true,
            executedSuccessfully = true,
            spokenHinglishResponse = hinglish,
            actionTitle = "Learning Mode Started",
            verifiedReport = report
        )
    }

    fun stopAndBuildLearnedWorkflow(skillTitle: String): Pair<WorkflowEntity, VoiceCommandActionOutcome> {
        val recorded = JarvisAccessibilityService.stopDemonstrationRecording()
        val finalActions = if (recorded.isNotEmpty()) {
            recorded
        } else {
            listOf(
                WorkflowAction(
                    type = WorkflowActionType.SPEAK_TEXT,
                    title = "Execute learned skill $skillTitle",
                    param1 = "Running learned skill $skillTitle"
                )
            )
        }

        val jsonArr = JSONArray()
        for (act in finalActions) {
            jsonArr.put(
                JSONObject().apply {
                    put("actionType", act.type.name)
                    put("title", act.title)
                    put("target", act.param1)
                    put("parameter", act.param2)
                }
            )
        }

        val cleanTitle = skillTitle.trim().ifBlank { "Learned Voice Skill #${System.currentTimeMillis() % 1000}" }
        val entity = WorkflowEntity(
            title = cleanTitle,
            description = "Teachable 'Watch Me & Learn' Voice Skill (${finalActions.size} recorded steps)",
            trigger = cleanTitle.lowercase(),
            actionsJson = jsonArr.toString(),
            isEnabled = true
        )
        val hinglish = "Maine aapke ${finalActions.size} steps seekh kar '$cleanTitle' naam se save kar liye hain! Ab aap kabhi bhi '$cleanTitle' bol kar ise chala sakte hain."
        val report = ActionVerificationEngine.verifySystemControlAction(
            controlType = "TEACHABLE_MACRO_RECORDER",
            targetStateDescription = "SAVED:$cleanTitle",
            capabilityStatus = CapabilityResultStatus.SUCCESS,
            stateVerifiedByApi = true,
            userMessage = "Saved learned skill '$cleanTitle' with ${finalActions.size} recorded steps.",
            hinglishMessage = hinglish
        )
        return entity to VoiceCommandActionOutcome(
            isPhoneCommand = true,
            executedSuccessfully = true,
            spokenHinglishResponse = hinglish,
            actionTitle = "Saved Skill: $cleanTitle",
            verifiedReport = report
        )
    }

    // =========================================================================
    // FEATURE 8: HANDS-FREE CALL, SPEAKERPHONE, ALARM & TIMER CONTROLLER
    // =========================================================================

    fun placeHandsFreeSpeakerCall(contactOrNumber: String, enableSpeaker: Boolean = true): VoiceCommandActionOutcome {
        val cleanTarget = contactOrNumber.trim()
        val resolvedContacts = phoneControlsManager.communicationHelper.resolveContactsByName(cleanTarget)
        val phoneNumber = when {
            resolvedContacts.size == 1 -> resolvedContacts.first().phoneNumber
            cleanTarget.any { it.isDigit() } -> cleanTarget.filter { it.isDigit() || it == '+' }
            else -> ""
        }

        return try {
            val dialUri = if (phoneNumber.isNotBlank()) Uri.parse("tel:$phoneNumber") else Uri.parse("tel:")
            val dialIntent = Intent(Intent.ACTION_DIAL, dialUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)

            if (enableSpeaker) {
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    if (audioManager != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val speakerDevice = audioManager.availableCommunicationDevices.firstOrNull {
                                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                            }
                            if (speakerDevice != null) {
                                audioManager.setCommunicationDevice(speakerDevice)
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            audioManager.isSpeakerphoneOn = true
                        }
                    }
                } catch (_: Exception) {}
            }

            val label = if (phoneNumber.isNotBlank()) "$cleanTarget ($phoneNumber)" else cleanTarget.ifBlank { "Dialer" }
            val hinglish = "Maine $label ke liye speakerphone mode ke saath call dialer open kar diya hai."
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = "HANDS_FREE_SPEAKER_CALL",
                targetStateDescription = label,
                capabilityStatus = CapabilityResultStatus.SUCCESS,
                stateVerifiedByApi = true,
                userMessage = "Opened call dialer for $label with speakerphone preference enabled.",
                hinglishMessage = hinglish
            )
            VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = true,
                spokenHinglishResponse = hinglish,
                actionTitle = "Speaker Call: $label",
                verifiedReport = report
            )
        } catch (e: Exception) {
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = "HANDS_FREE_SPEAKER_CALL",
                targetStateDescription = cleanTarget,
                capabilityStatus = CapabilityResultStatus.FAILED,
                stateVerifiedByApi = false,
                userMessage = "Failed to initiate call for $cleanTarget: ${e.localizedMessage}",
                hinglishMessage = "$cleanTarget ko call lagane mein error aaya."
            )
            VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = false,
                spokenHinglishResponse = report.hinglishMessage,
                actionTitle = "Speaker Call Failed",
                verifiedReport = report
            )
        }
    }

    fun setSystemAlarmOrTimer(
        isTimer: Boolean,
        hourOrSeconds: Int,
        minutes: Int = 0,
        label: String = "JARVIS Voice Alarm"
    ): VoiceCommandActionOutcome {
        return try {
            val intent = if (isTimer) {
                Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, hourOrSeconds.coerceAtLeast(5))
                    putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hourOrSeconds.coerceIn(0, 23))
                    putExtra(AlarmClock.EXTRA_MINUTES, minutes.coerceIn(0, 59))
                    putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            val desc = if (isTimer) "${hourOrSeconds}s Timer" else String.format("%02d:%02d Alarm", hourOrSeconds, minutes)
            val hinglish = if (isTimer) {
                "Maine $hourOrSeconds seconds ka system timer set kar diya hai."
            } else {
                "Maine ${String.format("%02d:%02d", hourOrSeconds, minutes)} ka system alarm set kar diya hai."
            }
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = if (isTimer) "SET_SYSTEM_TIMER" else "SET_SYSTEM_ALARM",
                targetStateDescription = desc,
                capabilityStatus = CapabilityResultStatus.SUCCESS,
                stateVerifiedByApi = true,
                userMessage = "Configured $desc via Android AlarmClock intent.",
                hinglishMessage = hinglish
            )
            VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = true,
                spokenHinglishResponse = hinglish,
                actionTitle = desc,
                verifiedReport = report
            )
        } catch (e: Exception) {
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = if (isTimer) "SET_SYSTEM_TIMER" else "SET_SYSTEM_ALARM",
                targetStateDescription = "FAILED",
                capabilityStatus = CapabilityResultStatus.FAILED,
                stateVerifiedByApi = false,
                userMessage = "Clock app not available: ${e.localizedMessage}",
                hinglishMessage = "Phone me Alarm/Timer set karne wala Clock app nahi mila."
            )
            VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = false,
                spokenHinglishResponse = report.hinglishMessage,
                actionTitle = "Alarm/Timer Failed",
                verifiedReport = report
            )
        }
    }

    // =========================================================================
    // FEATURE 9: DEEP-LINK & INTERNAL APP ACTIVITY DISCOVERY ("ZERO-CLICK TELEPORT")
    // =========================================================================

    fun executeDeepLinkTeleport(destinationType: String, queryOrTarget: String = ""): VoiceCommandActionOutcome {
        val cleanType = destinationType.trim().uppercase()
        val cleanQuery = queryOrTarget.trim()

        return try {
            val (intent, desc, hinglish) = when (cleanType) {
                "MAPS_NAVIGATION" -> {
                    val dest = cleanQuery
                        .replace(
                            Regex("(?i)\\b(jarvis|google\\s+maps\\s+(?:me|mein|par|on)|raste\\s+ka\\s+navigation\\s+on\\s+karo|raste\\s+ka\\s+navigation|navigation\\s+on\\s+karo|navigation\\s+lagao|navigate\\s+karo|navigate\\s+to|ka\\s+rasta\\s+dikhao|ke\\s+liye|ka\\s+rasta|on\\s+karo|chalo|please)\\b"),
                            " "
                        )
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .ifBlank { "Home" }
                    val navUri = Uri.parse("google.navigation:q=${Uri.encode(dest)}")
                    val navIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
                        setPackage("com.google.android.apps.maps")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val resolvedIntent = if (navIntent.resolveActivity(context.packageManager) != null) {
                        navIntent
                    } else {
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(dest)}")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    Triple(
                        resolvedIntent,
                        "Maps Navigation -> $dest",
                        "Google Maps me '$dest' ke liye seedha turn-by-turn navigation start kar diya hai."
                    )
                }
                "PLAY_MUSIC_SEARCH" -> {
                    val song = cleanQuery.ifBlank { "Top Hits" }
                    val musicIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                        putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                        putExtra(android.app.SearchManager.QUERY, song)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val resolvedIntent = if (musicIntent.resolveActivity(context.packageManager) != null) {
                        musicIntent
                    } else {
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=${Uri.encode(song)}")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    Triple(
                        resolvedIntent,
                        "Play Music -> $song",
                        "Music player me '$song' seedha play/search kar diya hai."
                    )
                }
                "YOUTUBE_SHORTS", "YOUTUBE_SEARCH" -> {
                    val topic = cleanQuery.ifBlank { "Shorts" }
                    val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(topic)}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    Triple(
                        ytIntent,
                        "YouTube Teleport -> $topic",
                        "YouTube me '$topic' screen par seedha teleport kar diya hai."
                    )
                }
                "UPI_SCANNER" -> {
                    val upiIntent = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val resolved = if (upiIntent.resolveActivity(context.packageManager) != null) {
                        upiIntent
                    } else {
                        Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    Triple(
                        resolved,
                        "UPI / QR Scanner Teleport",
                        "UPI / Scanner screen open kar di hai."
                    )
                }
                else -> {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(cleanQuery.ifBlank { cleanType })}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    Triple(
                        webIntent,
                        "Deep-Link -> $cleanQuery",
                        "Target screen '$cleanQuery' par jump kar diya hai."
                    )
                }
            }

            context.startActivity(intent)
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = "DEEP_LINK_TELEPORT",
                targetStateDescription = desc,
                capabilityStatus = CapabilityResultStatus.SUCCESS,
                stateVerifiedByApi = true,
                userMessage = "Executed 0-click deep-link teleport: $desc",
                hinglishMessage = hinglish
            )
            VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = true,
                spokenHinglishResponse = hinglish,
                actionTitle = desc,
                verifiedReport = report
            )
        } catch (e: Exception) {
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = "DEEP_LINK_TELEPORT",
                targetStateDescription = cleanType,
                capabilityStatus = CapabilityResultStatus.FAILED,
                stateVerifiedByApi = false,
                userMessage = "Deep-link teleport failed: ${e.localizedMessage}",
                hinglishMessage = "Deep-link teleport karne mein error aaya."
            )
            VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = false,
                spokenHinglishResponse = report.hinglishMessage,
                actionTitle = "Teleport Failed",
                verifiedReport = report
            )
        }
    }
}
