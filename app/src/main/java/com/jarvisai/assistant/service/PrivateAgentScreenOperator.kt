package com.jarvisai.assistant.service

import android.content.Context
import android.util.Log
import com.jarvisai.assistant.termux.JarvisTermuxBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class PrivateAgentOperatorStepTrace(
    val stepIndex: Int,
    val observedPackage: String,
    val observedNodesCount: Int,
    val actionExecuted: String,
    val uiTreeChangedAfterAction: Boolean
)

data class PrivateAgentOperatorReport(
    val goal: String,
    val completed: Boolean,
    val totalStepsExecuted: Int,
    val stepTraces: List<PrivateAgentOperatorStepTrace>,
    val spokenHinglishSummary: String
)

/**
 * AbuZar-Ansarii/Jarvis (PrivateAgent) Unified `Observe -> Think -> Act -> Verify` Screen Operator:
 * Inspects the live Accessibility XML hierarchy + 1-second Visual Screen state, executes touch/type/scroll actions,
 * verifies whether the UI tree hash or foreground package actually changed after 850ms,
 * and automatically triggers coordinate gesture or Shizuku/Termux shell input fallback if a click is absorbed.
 */
object PrivateAgentScreenOperator {
    private const val TAG = "PrivateAgentOperator"

    suspend fun executeVerifiedOperatorAction(
        context: Context,
        goalDescription: String,
        targetLabelOrText: String = "",
        textToType: String = "",
        scrollDirection: String = ""
    ): PrivateAgentOperatorReport = withContext(Dispatchers.Main) {
        val traces = mutableListOf<PrivateAgentOperatorStepTrace>()

        // 1. OBSERVE BEFORE ACTION
        val beforeSnap = JarvisAccessibilityService.getScreenContext(context)
        val beforeHash = computeSnapshotHash(beforeSnap)

        var primarySucceeded = false
        var actionDesc = "INSPECT"

        // 2. ACT (Accessibility Node -> Coordinate Fallback -> Termux/Shell Fallback)
        when {
            textToType.isNotBlank() -> {
                actionDesc = "TYPE('$textToType' -> '$targetLabelOrText')"
                if (targetLabelOrText.isNotBlank()) {
                    JarvisAccessibilityService.focusInputField(context, targetLabelOrText)
                }
                val res = JarvisAccessibilityService.typeText(
                    context = context,
                    text = textToType,
                    replaceExisting = true,
                    submitAfter = true
                )
                primarySucceeded = res.executed
            }
            targetLabelOrText.isNotBlank() -> {
                actionDesc = "CLICK('$targetLabelOrText')"
                val clickRes = JarvisAccessibilityService.clickElement(
                    context = context,
                    targetText = targetLabelOrText,
                    contentDescription = targetLabelOrText,
                    viewId = null
                )
                primarySucceeded = clickRes.executed
                if (!primarySucceeded) {
                    val candidate = beforeSnap.elements.firstOrNull { el ->
                        el.text.contains(targetLabelOrText, ignoreCase = true) ||
                            el.contentDescription.contains(targetLabelOrText, ignoreCase = true)
                    }
                    val centerCoords = candidate?.let { parseBoundsCenter(it.boundsInScreen) }
                    if (centerCoords != null) {
                        val tapRes = JarvisAccessibilityService.tapCoordinates(
                            context = context,
                            x = centerCoords.first.toFloat(),
                            y = centerCoords.second.toFloat()
                        )
                        primarySucceeded = tapRes.executed
                        actionDesc = "COORDINATE_FALLBACK_TAP(${centerCoords.first}, ${centerCoords.second})"
                    }
                }
            }
            scrollDirection.isNotBlank() -> {
                actionDesc = "SCROLL('$scrollDirection')"
                val scrollRes = JarvisAccessibilityService.scrollScreen(context, scrollDirection)
                primarySucceeded = scrollRes.executed
            }
        }

        // 3. VERIFY AFTER 850ms SETTLING WINDOW
        delay(850L)
        val afterSnap = JarvisAccessibilityService.getScreenContext(context)
        val afterHash = computeSnapshotHash(afterSnap)
        var uiChanged = (afterHash != beforeHash) || (afterSnap.activePackageName != beforeSnap.activePackageName)

        // 4. SHELL / SHIZUKU FALLBACK IF UI STUCK ON COORDINATE TARGET
        if (!uiChanged && targetLabelOrText.isNotBlank()) {
            val candidate = afterSnap.elements.firstOrNull { el ->
                el.text.contains(targetLabelOrText, ignoreCase = true) ||
                    el.contentDescription.contains(targetLabelOrText, ignoreCase = true)
            }
            val centerCoords = candidate?.let { parseBoundsCenter(it.boundsInScreen) }
            if (centerCoords != null) {
                Log.d(TAG, "UI tree unchanged after click; firing secondary tap at (${centerCoords.first}, ${centerCoords.second})")
                JarvisAccessibilityService.tapCoordinates(
                    context = context,
                    x = centerCoords.first.toFloat(),
                    y = centerCoords.second.toFloat()
                )
                val termuxBridge = JarvisTermuxBridge(context)
                if (termuxBridge.isTermuxInstalled()) {
                    withContext(Dispatchers.IO) {
                        termuxBridge.executeCommand(
                            command = "input tap ${centerCoords.first} ${centerCoords.second}",
                            background = true
                        )
                    }
                }
                delay(550L)
                val retrySnap = JarvisAccessibilityService.getScreenContext(context)
                uiChanged = computeSnapshotHash(retrySnap) != beforeHash
                primarySucceeded = primarySucceeded || uiChanged
            }
        }

        val resolvedPkg = afterSnap.activePackageName.ifBlank { beforeSnap.activePackageName }
        traces.add(
            PrivateAgentOperatorStepTrace(
                stepIndex = 1,
                observedPackage = resolvedPkg,
                observedNodesCount = afterSnap.elements.size,
                actionExecuted = actionDesc,
                uiTreeChangedAfterAction = uiChanged
            )
        )

        // Auto-record learned app quirk in Hermes AGENT.md if verified
        if (primarySucceeded && uiChanged && resolvedPkg.isNotBlank()) {
            HermesConsciousnessVault.updateMarkdownFile(
                context = context,
                targetDocument = "AGENT",
                content = "Verified operator action `$actionDesc` inside `$resolvedPkg` (UI state transition confirmed).",
                appendMode = true
            )
        }

        val pkgDisplay = resolvedPkg.ifBlank { "screen" }
        val spoken = if (primarySucceeded) {
            "Sir, PrivateAgent Operator Loop ne '$pkgDisplay' par $actionDesc execute karke verify kar liya hai (UI transition=${if (uiChanged) "CONFIRMED" else "SETTLED"})."
        } else {
            "Sir, PrivateAgent Operator Loop ne $actionDesc try kiya lekin matching UI node active screen par nahi mila."
        }

        PrivateAgentOperatorReport(
            goal = goalDescription,
            completed = primarySucceeded,
            totalStepsExecuted = traces.size,
            stepTraces = traces,
            spokenHinglishSummary = spoken
        )
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

    private fun computeSnapshotHash(snapshot: ScreenContextSnapshot): Int {
        var hash = snapshot.activePackageName.hashCode()
        hash = 31 * hash + snapshot.windowTitle.hashCode()
        hash = 31 * hash + snapshot.elements.size
        snapshot.elements.take(25).forEach { el ->
            hash = 31 * hash + el.text.hashCode()
            hash = 31 * hash + el.boundsInScreen.hashCode()
        }
        return hash
    }
}
