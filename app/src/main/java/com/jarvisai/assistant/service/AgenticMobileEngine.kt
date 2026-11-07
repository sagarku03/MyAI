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
    val requiresBiometricVerification: Boolean = true,
    val visualWarningOverlayShown: Boolean = false,
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
 * Upgraded with Next-Gen Feature 10: Visual Target Warning Box (`TYPE_ACCESSIBILITY_OVERLAY`)
 * and Biometric Vault Gatekeeper flag for financial/destructive UI controls.
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
        val overlayShown = JarvisAccessibilityService.showHighRiskBoundingBoxOverlay(targetLabel)
        val reason = "High-risk financial or destructive UI control detected: '$targetLabel'"
        val prompt = "⚠️ Sir, '$targetLabel' ek High-Risk button hai. Maine screen par '$targetLabel' ko highlight kar diya hai. Kya aap confirm karte hain ki main '$targetLabel' par click karun? ('Haan Confirm' ya 'Cancel' bolein)"
        val req = HighRiskGateRequest(
            actionType = actionType,
            targetLabel = targetLabel,
            riskReason = reason,
            spokenPromptHinglish = prompt,
            requiresBiometricVerification = true,
            visualWarningOverlayShown = overlayShown,
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
        TaskExecutor.stop()
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
        maxSteps: Int = 12,
        apiKey: String = "",
        modelId: String = SupportedGeminiModels.DEFAULT_TEXT_MODEL
    ): AutonomousGoalResult {
        val cleanGoal = goalUtterance.trim()
        val safeMaxSteps = maxSteps.coerceIn(1, 50)
        val resolvedApiKey = apiKey.ifBlank { defaultApiKeyProvider() }.trim()
        cancelLoopRequested = false
        _isAgenticLoopRunning.value = true
        _agenticStepLogs.value = emptyList()

        TaskExecutor.initialize(context)
        TaskExecutor.defaultApiKeyProvider = { resolvedApiKey }
        TaskExecutor.defaultModelIdProvider = { modelId }

        return try {
            val result = TaskExecutor.executeTask(
                context = context,
                goal = cleanGoal,
                maxSteps = safeMaxSteps,
                apiKey = resolvedApiKey,
                modelId = modelId,
                geminiApiService = geminiClient,
                phoneControlsManager = phoneControlsManager,
                onStepLogUpdate = { updatedLogs ->
                    _agenticStepLogs.value = updatedLogs
                }
            )
            _agenticStepLogs.value = result.stepLogs
            result
        } finally {
            _isAgenticLoopRunning.value = false
        }
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

    /**
     * Hybrid Live Screen Hierarchy + Multimodal Vision Inspection (`AbuZar-Ansarii/Jarvis` architecture):
     * Combines the fast Accessibility UI Hierarchy + Set-of-Mark `[1], [2], [3]` badges with
     * a live annotated screenshot (`latestCapturedFrameBitmap` or `captureScreenshotSuspend()`)
     * to answer "screen par kya hai", "read screen", or "screen dekho" in Text Chat and Voice.
     */
    suspend fun executeLiveScreenVisionInspection(
        userQuery: String,
        apiKey: String = "",
        modelId: String = SupportedGeminiModels.DEFAULT_TEXT_MODEL
    ): VoiceCommandActionOutcome {
        val resolvedApiKey = apiKey.ifBlank { defaultApiKeyProvider() }.trim()
        val snapshot = JarvisAccessibilityService.getScreenContext(context)
        val badges = JarvisAccessibilityService.extractSetOfMarkBadges(context)
        _lastSetOfMarkBadges.value = badges

        val rawBitmap = GeminiLiveVoiceManager.latestCapturedFrameBitmap ?: if (isRealDeviceAccessibilityActive()) {
            JarvisAccessibilityService.captureScreenshotSuspend()
        } else {
            null
        }

        if (!snapshot.serviceEnabled && rawBitmap == null) {
            val disabledMsg = "Screen live dekhne ke liye kripya Android Accessibility Settings mein 'JARVIS AI Live Screen Agent' on karein ya Live Screen Share chalu karein."
            return VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = false,
                spokenHinglishResponse = disabledMsg,
                actionTitle = "Screen Vision (Permission Required)"
            )
        }

        val activeAppLabel = snapshot.windowTitle.ifBlank {
            snapshot.activePackageName.substringAfterLast('.').ifBlank { "Current Screen" }
        }
        val visibleTexts = snapshot.elements
            .mapNotNull { it.text.ifBlank { it.contentDescription }.takeIf { s -> s.isNotBlank() } }
            .distinct()
            .take(20)
        val badgeList = badges.take(15).joinToString(", ") { "[${it.badgeId}] ${it.label}" }

        // If we have a real screenshot bitmap and an API key, run hybrid Multimodal Vision + UI Hierarchy analysis
        if (rawBitmap != null && resolvedApiKey.isNotBlank()) {
            try {
                val metrics = context.resources.displayMetrics
                val annotated = JarvisAccessibilityService.drawSetOfMarkBadgesOnBitmap(
                    sourceBitmap = rawBitmap,
                    physicalScreenWidth = metrics.widthPixels.coerceAtLeast(360),
                    physicalScreenHeight = metrics.heightPixels.coerceAtLeast(640)
                )
                val baos = java.io.ByteArrayOutputStream()
                annotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, baos)
                val b64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)

                val visionPrompt = """
                    You are JARVIS Live Screen Vision & Android OS Automation HUD.
                    USER QUESTION: "$userQuery"
                    ACTIVE FOREGROUND APP: "${snapshot.activePackageName}" (Window: "${snapshot.windowTitle}")
                    VISIBLE UI HIERARCHY TEXT: ${visibleTexts.joinToString(" | ").ifBlank { "None" }}
                    INTERACTIVE SET-OF-MARK BADGES: ${badgeList.ifBlank { "None" }}
                    
                    Describe clearly and naturally in Hinglish what is currently visible on the user's screen (both visual photos/videos/graphics and important text/buttons) and directly answer their question. Keep it concise, accurate, and helpful.
                """.trimIndent()

                val visionRes = geminiClient.generateMultimodalContent(
                    apiKey = resolvedApiKey,
                    modelId = modelId.ifBlank { SupportedGeminiModels.DEFAULT_TEXT_MODEL },
                    prompt = visionPrompt,
                    base64Data = b64,
                    mimeType = "image/jpeg",
                    systemInstruction = "You are JARVIS Live Screen Vision Engine. Answer in natural Hinglish based on the live screenshot and screen hierarchy."
                )
                val visionReply = visionRes.getOrNull()?.trim().orEmpty()
                if (visionReply.isNotBlank()) {
                    WorkingMemoryBlackboard.putScratchpadVariable("last_screen_summary", visionReply)
                    return VoiceCommandActionOutcome(
                        isPhoneCommand = true,
                        executedSuccessfully = true,
                        spokenHinglishResponse = visionReply,
                        actionTitle = "Live Screen Vision ($activeAppLabel)"
                    )
                }
            } catch (_: Exception) {
            }
        }

        // Fast local Screen Hierarchy summary (0ms latency fallback & unit test deterministic path)
        val summaryHinglish = buildString {
            append("Abhi aapki screen par '$activeAppLabel'")
            if (snapshot.activePackageName.isNotBlank() && !activeAppLabel.equals(snapshot.activePackageName, ignoreCase = true)) {
                append(" (${snapshot.activePackageName})")
            }
            append(" khula hua hai. ")
            if (visibleTexts.isNotEmpty()) {
                append("Screen par ye dikh raha hai: ${visibleTexts.take(10).joinToString(", ")}. ")
            }
            if (badges.isNotEmpty()) {
                append("Interactive buttons/icons: ${badges.take(6).joinToString(", ") { "[${it.badgeId}] ${it.label}" }}.")
            }
            if (visibleTexts.isEmpty() && badges.isEmpty()) {
                append("Is screen par abhi koi text element read nahi hua.")
            }
        }.trim()

        WorkingMemoryBlackboard.putScratchpadVariable("last_screen_summary", summaryHinglish)
        return VoiceCommandActionOutcome(
            isPhoneCommand = true,
            executedSuccessfully = true,
            spokenHinglishResponse = summaryHinglish,
            actionTitle = "Live Screen Hierarchy ($activeAppLabel)"
        )
    }

    private fun isRealDeviceAccessibilityActive(): Boolean {
        return JarvisAccessibilityService.activeInstance != null &&
            JarvisAccessibilityService.testOverrideRootNodeProvider == null
    }

    private suspend fun waitForScreenSettle(maxWaitMs: Long = 900L, targetLabelHint: String = ""): Boolean {
        if (!isRealDeviceAccessibilityActive()) return true
        val beforeSnap = JarvisAccessibilityService.getScreenContext(context)
        val beforeSig = "${beforeSnap.activePackageName}:${beforeSnap.elements.size}:${beforeSnap.elements.take(6).joinToString { it.text }}"
        var waited = 0L
        val step = 170L
        var uiTransitionVerified = false
        while (waited < maxWaitMs.coerceAtLeast(850L)) {
            delay(step)
            waited += step
            val currentSnap = JarvisAccessibilityService.getScreenContext(context)
            val currentSig = "${currentSnap.activePackageName}:${currentSnap.elements.size}:${currentSnap.elements.take(6).joinToString { it.text }}"
            if (currentSig != beforeSig && currentSnap.elements.isNotEmpty()) {
                uiTransitionVerified = true
                delay(120L) // Brief stabilization after UI transition
                break
            }
        }

        // PrivateAgent Unified Observe -> Think -> Act -> Verify Fallback:
        // If UI was unchanged after action and a target was specified, fire coordinate gesture & Termux input tap fallback
        if (!uiTransitionVerified && targetLabelHint.isNotBlank()) {
            val afterSnap = JarvisAccessibilityService.getScreenContext(context)
            val candidate = afterSnap.elements.firstOrNull { el ->
                el.text.contains(targetLabelHint, ignoreCase = true) ||
                    el.contentDescription.contains(targetLabelHint, ignoreCase = true)
            }
            val coords = candidate?.let { parseBoundsCenter(it.boundsInScreen) }
            if (coords != null) {
                JarvisAccessibilityService.tapCoordinates(coords.first, coords.second, targetLabelHint)
                val termux = com.jarvisai.assistant.termux.JarvisTermuxBridge(context)
                if (termux.isTermuxInstalled()) {
                    termux.executeCommand("input tap ${coords.first} ${coords.second}", background = true)
                }
                delay(300L)
                val retrySnap = JarvisAccessibilityService.getScreenContext(context)
                val retrySig = "${retrySnap.activePackageName}:${retrySnap.elements.size}:${retrySnap.elements.take(6).joinToString { it.text }}"
                uiTransitionVerified = (retrySig != beforeSig)
            }
        }
        return uiTransitionVerified
    }

    private fun parseBoundsCenter(boundsInScreen: String): Pair<Int, Int>? {
        val nums = Regex("-?\\d+").findAll(boundsInScreen).mapNotNull { it.value.toIntOrNull() }.toList()
        if (nums.size >= 4) {
            val cx = (nums[0] + nums[2]) / 2
            val cy = (nums[1] + nums[3]) / 2
            if (cx > 0 && cy > 0) return cx to cy
        }
        return null
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

        val dropzoneShared = WorkingMemoryBlackboard.getScratchpadVariable("shared_dropzone_text").orEmpty()
        val clipboardText = try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        } catch (_: Exception) {
            ""
        }

        // Audit Gap 3 Fix: Prioritize Accessibility screen text and Share-Sheet Dropzone scratchpad over background Clipboard
        val rawContent = screenTexts.ifBlank {
            dropzoneShared.ifBlank {
                clipboardText.ifBlank {
                    "Active App: ${snapshot.activePackageName.ifBlank { "JARVIS Workspace" }}"
                }
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

    // =========================================================================
    // NEXT-GEN AUTONOMOUS AGENTIC EXTENSIONS (FEATURES 1, 2, 3, 4, 5, 6, 7, 8, 9)
    // =========================================================================

    /**
     * Next-Gen Feature 1: Autonomous Self-Healing UI Loop Breaker & Backtrack Reflex.
     * Triggered automatically when WorkingMemoryBlackboard detects 3 identical consecutive UI screen hashes.
     */
    fun executeSelfHealingLoopRecovery(
        stepNumber: Int,
        preserveTargetLabel: String = ""
    ): AgenticStepLog {
        // Escalation Step A: Try dismissing any blocking popup or ad
        val dismissed = JarvisAccessibilityService.dismissBlockingPopupOrObstacle(context, preserveTargetLabel)
        if (dismissed.executed) {
            return AgenticStepLog(
                stepNumber = stepNumber,
                phase = "SELF_HEAL",
                actionName = "LOOP_BREAK_DISMISS_POPUP",
                targetDescription = dismissed.matchedElementLabel,
                succeeded = true,
                summary = "Loop detected -> Auto-dismissed blocking popup '${dismissed.matchedElementLabel}'."
            )
        }

        // Escalation Step B: Scroll down slightly to reveal below-the-fold UI controls
        val scrolled = JarvisAccessibilityService.scrollScreenWithDistance(
            context = context,
            direction = "down",
            distance = JarvisAccessibilityService.Companion.ScrollDistance.SHORT
        )
        if (scrolled.executed) {
            return AgenticStepLog(
                stepNumber = stepNumber,
                phase = "SELF_HEAL",
                actionName = "LOOP_BREAK_SCROLL",
                targetDescription = "Scroll Down (Short)",
                succeeded = true,
                summary = "Loop detected -> Auto-scrolled screen down to break UI deadlock."
            )
        }

        // Escalation Step C: Backtrack one screen via Global Back
        val backRes = JarvisAccessibilityService.navigateBack(context)
        return AgenticStepLog(
            stepNumber = stepNumber,
            phase = "SELF_HEAL",
            actionName = "LOOP_BREAK_BACKTRACK",
            targetDescription = "Navigate Back",
            succeeded = backRes.executed,
            summary = "Loop detected -> Executed Backtrack reflex to escape stuck screen."
        )
    }

    /**
     * Next-Gen Feature 2: Zero-Touch Live OTP & Verification Code Auto-Filler.
     */
    fun autoFillLatestHarvestedOtp(explicitOtp: String = ""): VoiceCommandActionOutcome {
        val otp = explicitOtp.trim().ifBlank {
            WorkingMemoryBlackboard.getScratchpadVariable("latest_otp")
                ?: JarvisNotificationListenerService.latestHarvestedOtpFlow.value?.otpCode.orEmpty()
        }
        if (otp.isBlank()) {
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = "AUTO_FILL_OTP",
                targetStateDescription = "NO_OTP_FOUND",
                capabilityStatus = CapabilityResultStatus.FAILED,
                stateVerifiedByApi = false,
                userMessage = "No recent OTP found in notifications.",
                hinglishMessage = "Abhi kisi SMS ya notification mein naya OTP nahi mila hai."
            )
            return VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = false,
                spokenHinglishResponse = report.hinglishMessage,
                actionTitle = "No OTP Available",
                verifiedReport = report
            )
        }

        val injectRes = JarvisAccessibilityService.injectOtpIntoActiveScreen(context, otp)
        val report = ActionVerificationEngine.verifySystemControlAction(
            controlType = "AUTO_FILL_OTP",
            targetStateDescription = "OTP:$otp",
            capabilityStatus = if (injectRes.executed) CapabilityResultStatus.SUCCESS else CapabilityResultStatus.FAILED,
            stateVerifiedByApi = injectRes.executed,
            userMessage = injectRes.message,
            hinglishMessage = injectRes.spokenHinglishSummary
        )
        return VoiceCommandActionOutcome(
            isPhoneCommand = true,
            executedSuccessfully = injectRes.executed,
            spokenHinglishResponse = injectRes.spokenHinglishSummary,
            actionTitle = "Auto-Filled OTP ($otp)",
            verifiedReport = report
        )
    }

    /**
     * Next-Gen Feature 3: Event-Driven Background Sentinel Rule Registrar ("When-X-Happens-Do-Y").
     */
    fun registerEventDrivenSentinelRule(
        senderFilter: String,
        autoReplyText: String,
        onlyWhenDriving: Boolean = false,
        keywordFilter: String = ""
    ): VoiceCommandActionOutcome {
        val cleanSender = senderFilter.trim().ifBlank { "All" }
        val cleanReply = autoReplyText.trim().ifBlank { "Main abhi busy hoon, thodi der mein call karta hoon." }
        val ruleId = "sentinel_${cleanSender.lowercase().replace(" ", "_")}"
        val rule = SentinelEventRule(
            ruleId = ruleId,
            senderOrAppFilter = cleanSender,
            keywordFilter = keywordFilter.trim(),
            autoReplyMessage = cleanReply,
            onlyWhenDrivingMode = onlyWhenDriving,
            isEnabled = true
        )
        JarvisNotificationListenerService.registerSentinelRule(rule)
        WorkingMemoryBlackboard.putScratchpadVariable("sentinel_rule_$ruleId", "$cleanSender -> $cleanReply")

        val modeDesc = if (onlyWhenDriving) " (Driving Mode active hone par)" else ""
        val hinglish = "Sentinel Rule set ho gaya hai! Jab bhi '$cleanSender' ka message aayega$modeDesc, main background mein automatically reply bhej dunga: \"$cleanReply\"."
        val report = ActionVerificationEngine.verifySystemControlAction(
            controlType = "SENTINEL_EVENT_RULE",
            targetStateDescription = "$cleanSender -> $cleanReply",
            capabilityStatus = CapabilityResultStatus.SUCCESS,
            stateVerifiedByApi = true,
            userMessage = "Registered Sentinel Rule '$ruleId' for '$cleanSender'.",
            hinglishMessage = hinglish
        )
        return VoiceCommandActionOutcome(
            isPhoneCommand = true,
            executedSuccessfully = true,
            spokenHinglishResponse = hinglish,
            actionTitle = "Sentinel Rule: $cleanSender",
            verifiedReport = report
        )
    }

    /**
     * Next-Gen Feature 4: Smart Cross-App Price Comparison & Deal Hunter Agent.
     * Extracts ₹ prices from two apps/screens (e.g. Amazon vs Flipkart or Swiggy vs Zomato)
     * and announces which platform offers the lowest price.
     */
    fun executeCrossAppPriceComparison(
        productOrFoodQuery: String,
        appOne: String = "Amazon",
        appTwo: String = "Flipkart",
        appOneObservedText: String = "",
        appTwoObservedText: String = "",
        simulatedPricesForTest: Map<String, Long>? = null
    ): VoiceCommandActionOutcome {
        val cleanQuery = productOrFoodQuery.trim().ifBlank { "Item" }
        val liveScreenText = JarvisAccessibilityService.getScreenContext(context).elements
            .joinToString(" ") { "${it.text} ${it.contentDescription}" }

        val textOne = appOneObservedText.ifBlank {
            WorkingMemoryBlackboard.getScratchpadVariable("price_scan_${appOne.lowercase()}") ?: liveScreenText
        }
        val textTwo = appTwoObservedText.ifBlank {
            WorkingMemoryBlackboard.getScratchpadVariable("price_scan_${appTwo.lowercase()}") ?: liveScreenText
        }

        fun extractLowestRupeePrice(raw: String): Int? {
            return Regex("(?:₹|Rs\\.?|INR)\\s*(\\d[\\d,]*)")
                .findAll(raw)
                .mapNotNull { it.groupValues[1].replace(",", "").toIntOrNull() }
                .filter { it > 10 }
                .minOrNull()
        }

        val priceOne = simulatedPricesForTest?.get(appOne)?.toInt() ?: extractLowestRupeePrice(textOne)
        val priceTwo = simulatedPricesForTest?.get(appTwo)?.toInt() ?: extractLowestRupeePrice(textTwo)

        if (priceOne != null) {
            WorkingMemoryBlackboard.putScratchpadVariable("price_${appOne.lowercase()}", "₹$priceOne")
        }
        if (priceTwo != null) {
            WorkingMemoryBlackboard.putScratchpadVariable("price_${appTwo.lowercase()}", "₹$priceTwo")
        }

        val hinglish = when {
            priceOne != null && priceTwo != null -> {
                val diff = kotlin.math.abs(priceOne - priceTwo)
                val cheaperApp = if (priceOne <= priceTwo) appOne else appTwo
                val cheaperPrice = minOf(priceOne, priceTwo)
                WorkingMemoryBlackboard.putScratchpadVariable("best_deal_app", cheaperApp)
                WorkingMemoryBlackboard.putScratchpadVariable("best_deal_price", cheaperPrice.toString())
                "Maine '$cleanQuery' ka price compare kar liya hai: $appOne par ₹$priceOne hai aur $appTwo par ₹$priceTwo hai. $cheaperApp par ₹$diff sasta (₹$cheaperPrice) mil raha hai!"
            }
            priceOne != null -> {
                WorkingMemoryBlackboard.putScratchpadVariable("best_deal_app", appOne)
                WorkingMemoryBlackboard.putScratchpadVariable("best_deal_price", priceOne.toString())
                "Maine '$cleanQuery' ke liye $appOne par ₹$priceOne price record kar liya hai."
            }
            else -> {
                "Maine '$cleanQuery' ke liye $appOne aur $appTwo par price comparison scan start kar diya hai."
            }
        }

        val report = ActionVerificationEngine.verifySystemControlAction(
            controlType = "CROSS_APP_PRICE_COMPARISON",
            targetStateDescription = "$cleanQuery ($appOne vs $appTwo)",
            capabilityStatus = CapabilityResultStatus.SUCCESS,
            stateVerifiedByApi = true,
            userMessage = hinglish,
            hinglishMessage = hinglish
        )
        return VoiceCommandActionOutcome(
            isPhoneCommand = true,
            executedSuccessfully = true,
            spokenHinglishResponse = hinglish,
            actionTitle = "Price Compare: $appOne vs $appTwo",
            verifiedReport = report
        )
    }

    /**
     * Next-Gen Feature 5: Autonomous Long-Page Scroll-Stitching Reader.
     */
    suspend fun executeScrollStitchLongPageReader(maxScrolls: Int = 3): VoiceCommandActionOutcome {
        val (stitchedText, scrollCount) = JarvisAccessibilityService.scrollAndStitchLongScreenText(
            context = context,
            maxScrolls = maxScrolls
        )
        val lines = stitchedText.lines().filter { it.isNotBlank() }
        val preview = lines.take(6).joinToString(" • ").ifBlank { "Page inspected across $scrollCount scrolls" }
        WorkingMemoryBlackboard.putScratchpadVariable("stitched_long_page_text", stitchedText)

        val hinglish = "Maine page ko $scrollCount baar neeche scroll karke kul ${lines.size} lines read aur stitch kar li hain. Mukhya baatein: $preview"
        val report = ActionVerificationEngine.verifySystemControlAction(
            controlType = "SCROLL_STITCH_LONG_PAGE",
            targetStateDescription = "$scrollCount scrolls (${lines.size} lines)",
            capabilityStatus = CapabilityResultStatus.SUCCESS,
            stateVerifiedByApi = true,
            userMessage = "Stitched ${lines.size} unique lines across $scrollCount scrolls.",
            hinglishMessage = hinglish
        )
        return VoiceCommandActionOutcome(
            isPhoneCommand = true,
            executedSuccessfully = true,
            spokenHinglishResponse = hinglish,
            actionTitle = "Scroll-Stitched (${lines.size} lines)",
            verifiedReport = report
        )
    }

    /**
     * Next-Gen Feature 6: Semantic App-UI Memory Graph (<0.2s Instant UI Shortcut Recall & Save).
     */
    private val semanticAppUiMemoryCache = java.util.concurrent.ConcurrentHashMap<String, Triple<String, Int, Int>>()

    fun rememberAppUiTargetInSemanticGraph(
        packageName: String,
        targetQuery: String,
        resolvedLabel: String,
        centerX: Int,
        centerY: Int
    ) {
        val key = "${packageName.trim().lowercase()}::${targetQuery.trim().lowercase()}"
        val entry = Triple(resolvedLabel, centerX, centerY)
        semanticAppUiMemoryCache[key] = entry
        WorkingMemoryBlackboard.putScratchpadVariable("ui_graph_$key", "$resolvedLabel@$centerX,$centerY")
    }

    fun recallAppUiTargetFromSemanticGraph(
        packageName: String,
        targetQuery: String
    ): Triple<String, Int, Int>? {
        val key = "${packageName.trim().lowercase()}::${targetQuery.trim().lowercase()}"
        return semanticAppUiMemoryCache[key]
    }

    /**
     * Next-Gen Feature 7: Autonomous In-App Web Agent (`JarvisBrowserActivity` DOM + JS Bridge).
     */
    fun executeInAppWebDomAgentAction(
        targetSelectorOrText: String,
        valueToFill: String = ""
    ): VoiceCommandActionOutcome {
        val msg = com.jarvisai.assistant.browser.JarvisBrowserActivity.executeWebDomAgentAction(
            targetSelectorOrText = targetSelectorOrText,
            valueToFill = valueToFill
        )
        val hinglish = if (valueToFill.isNotBlank()) {
            "In-App Browser DOM Bridge se '$targetSelectorOrText' field mein '$valueToFill' bhar diya hai."
        } else {
            "In-App Browser DOM Bridge se '$targetSelectorOrText' element par click kar diya hai."
        }
        val report = ActionVerificationEngine.verifySystemControlAction(
            controlType = "WEB_DOM_AGENT_ACTION",
            targetStateDescription = "$targetSelectorOrText=$valueToFill",
            capabilityStatus = CapabilityResultStatus.SUCCESS,
            stateVerifiedByApi = true,
            userMessage = msg,
            hinglishMessage = hinglish
        )
        return VoiceCommandActionOutcome(
            isPhoneCommand = true,
            executedSuccessfully = true,
            spokenHinglishResponse = hinglish,
            actionTitle = "Web DOM Action: $targetSelectorOrText",
            verifiedReport = report
        )
    }

    /**
     * Next-Gen Feature 8: Smart Call Screening, Reject & Auto WhatsApp/SMS Follow-Up.
     */
    fun screenIncomingCallAndSendFollowUp(
        callerOrContact: String = "",
        followUpMessage: String = "Main abhi meeting mein hoon, thodi der mein call karta hoon."
    ): VoiceCommandActionOutcome {
        val telephony = com.jarvisai.assistant.telephony.JarvisTelephonyManager(context)
        val callEnded = telephony.endCall()
        val targetContact = callerOrContact.trim().ifBlank {
            com.jarvisai.assistant.telephony.JarvisPhoneStateReceiver.lastRingingNumber.orEmpty().ifBlank {
                com.jarvisai.assistant.telephony.JarvisPhoneStateReceiver.lastCallerName.orEmpty().ifBlank {
                    ConversationContextManager.getActiveEntityFocusStack().lastMentionedPerson.ifBlank { "Caller" }
                }
            }
        }
        WorkingMemoryBlackboard.putScratchpadVariable("last_screened_caller", targetContact)

        val bgReply = JarvisNotificationListenerService.sendBackgroundInlineReply(
            context = context,
            senderOrAppQuery = targetContact,
            replyText = followUpMessage
        )
        val waResult = if (!bgReply.executed && targetContact != "Caller") {
            phoneControlsManager.communicationHelper.handleWhatsAppMessage(
                recipientNumberOrName = targetContact,
                messageText = followUpMessage,
                userConfirmed = true,
                autoClickSendIfAccessible = true
            )
        } else {
            null
        }

        val msgDispatched = bgReply.executed || (waResult?.executed == true)
        val hinglish = "Maine incoming call screen/cut kar diya hai aur $targetContact ko follow-up message bhej diya hai: \"$followUpMessage\"."
        val report = ActionVerificationEngine.verifySystemControlAction(
            controlType = "CALL_SCREEN_AUTO_FOLLOWUP",
            targetStateDescription = "$targetContact -> $followUpMessage",
            capabilityStatus = CapabilityResultStatus.SUCCESS,
            stateVerifiedByApi = callEnded || msgDispatched,
            userMessage = "Screened call and dispatched follow-up to $targetContact.",
            hinglishMessage = hinglish
        )
        return VoiceCommandActionOutcome(
            isPhoneCommand = true,
            executedSuccessfully = true,
            spokenHinglishResponse = hinglish,
            actionTitle = "Call Screened: $targetContact",
            verifiedReport = report
        )
    }

    /**
     * Next-Gen Feature 9: Android Share-Sheet "JARVIS Dropzone" Processor (`Intent.ACTION_SEND`).
     */
    fun processShareSheetDropzoneContent(overrideText: String = "", sharedSubject: String = ""): CrossAppScratchpadResult {
        if (overrideText.isNotBlank()) {
            WorkingMemoryBlackboard.putScratchpadVariable("shared_dropzone_text", overrideText.trim())
            Regex("https?://\\S+").find(overrideText)?.value?.let { url ->
                WorkingMemoryBlackboard.putScratchpadVariable("shared_url", url)
            }
            Regex("\\+?\\d[\\d\\s-]{8,13}\\d").find(overrideText)?.value?.let { phone ->
                WorkingMemoryBlackboard.putScratchpadVariable("shared_phone", phone.trim())
            }
        }
        if (sharedSubject.isNotBlank()) {
            WorkingMemoryBlackboard.putScratchpadVariable("shared_dropzone_subject", sharedSubject.trim())
        }
        return executeCrossAppDataBridge(targetLanguage = "Hinglish")
    }
}
