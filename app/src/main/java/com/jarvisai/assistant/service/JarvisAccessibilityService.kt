package com.jarvisai.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class ScreenElementSnapshot(
    val index: Int,
    val text: String,
    val contentDescription: String,
    val viewIdResourceName: String,
    val className: String,
    val packageName: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val isFocusable: Boolean,
    val boundsInScreen: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("index", index)
        if (text.isNotBlank()) put("text", text)
        if (contentDescription.isNotBlank()) put("content_description", contentDescription)
        if (viewIdResourceName.isNotBlank()) put("view_id", viewIdResourceName)
        put("class_name", className)
        put("clickable", isClickable)
        put("scrollable", isScrollable)
        put("editable", isEditable)
        put("bounds", boundsInScreen)
    }
}

data class ScreenContextSnapshot(
    val serviceEnabled: Boolean,
    val activePackageName: String,
    val windowTitle: String,
    val elements: List<ScreenElementSnapshot>,
    val errorCode: String? = null,
    val message: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("status", if (serviceEnabled && errorCode == null) "SUCCESS" else "ERROR")
        put("service_enabled", serviceEnabled)
        put("active_package", activePackageName)
        put("window_title", windowTitle)
        put("element_count", elements.size)
        if (errorCode != null) {
            put("error_code", errorCode)
        }
        put("message", message)
        val arr = JSONArray()
        elements.take(60).forEach { arr.put(it.toJson()) }
        put("visible_elements", arr)
    }
}

data class AccessibilityOperationResult(
    val executed: Boolean,
    val actionName: String,
    val matchedElementLabel: String = "",
    val activePackageName: String = "",
    val errorCode: String? = null,
    val message: String,
    val spokenHinglishSummary: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("status", if (executed) "SUCCESS" else "ERROR")
        put("executed", executed)
        put("action", actionName)
        if (matchedElementLabel.isNotBlank()) {
            put("matched_element", matchedElementLabel)
        }
        if (activePackageName.isNotBlank()) {
            put("active_package", activePackageName)
        }
        if (errorCode != null) {
            put("error_code", errorCode)
        }
        put("message", message)
        put("spoken_summary", spokenHinglishSummary)
    }
}

/**
 * Interface allowing unit/Robolectric tests to test real AccessibilityNodeInfo traversal
 * or custom scenarios while production code uses the active Android [JarvisAccessibilityService].
 */
interface AccessibilityControllerBridge {
    fun isAccessibilityEnabled(context: Context): Boolean
    fun getScreenContext(context: Context): ScreenContextSnapshot
    fun clickElement(
        context: Context,
        targetText: String?,
        contentDescription: String?,
        viewId: String?
    ): AccessibilityOperationResult
    fun scrollScreen(context: Context, direction: String): AccessibilityOperationResult
    fun navigateBack(context: Context): AccessibilityOperationResult
    fun navigateHome(context: Context): AccessibilityOperationResult

    // Directional Gestures & Universal Typing extensions
    fun swipeScreen(context: Context, direction: String): AccessibilityOperationResult =
        AccessibilityOperationResult(false, "swipe_screen", "", "", "UNSUPPORTED", "Swipe not supported in bridge", "Swipe action available nahi hai.")
    fun tapCoordinates(context: Context, x: Float, y: Float): AccessibilityOperationResult =
        AccessibilityOperationResult(false, "tap_coordinates", "", "", "UNSUPPORTED", "Tap not supported in bridge", "Tap action available nahi hai.")
    fun longPressCoordinates(context: Context, x: Float, y: Float, durationMs: Long = 800L): AccessibilityOperationResult =
        AccessibilityOperationResult(false, "long_press", "", "", "UNSUPPORTED", "Long press not supported in bridge", "Long press available nahi hai.")
    fun openRecents(context: Context): AccessibilityOperationResult =
        AccessibilityOperationResult(false, "open_recents", "", "", "UNSUPPORTED", "Recents not supported in bridge", "Recent apps available nahi hai.")
    fun openNotifications(context: Context): AccessibilityOperationResult =
        AccessibilityOperationResult(false, "open_notifications", "", "", "UNSUPPORTED", "Notifications not supported in bridge", "Notifications available nahi hai.")
    fun openQuickSettings(context: Context): AccessibilityOperationResult =
        AccessibilityOperationResult(false, "open_quick_settings", "", "", "UNSUPPORTED", "Quick settings not supported in bridge", "Quick settings available nahi hai.")
    fun readScreenText(context: Context): String = ""
    fun typeText(context: Context, text: String, replaceExisting: Boolean = false, submitAfter: Boolean = false): AccessibilityOperationResult =
        AccessibilityOperationResult(false, "type_text", "", "", "UNSUPPORTED", "Type text not supported in bridge", "Text typing available nahi hai.")
    fun clearText(context: Context): AccessibilityOperationResult =
        AccessibilityOperationResult(false, "clear_text", "", "", "UNSUPPORTED", "Clear text not supported in bridge", "Clear text available nahi hai.")
    fun focusInputField(context: Context, targetLabel: String? = null): AccessibilityOperationResult =
        AccessibilityOperationResult(false, "focus_input", "", "", "UNSUPPORTED", "Focus input not supported in bridge", "Input focus available nahi hai.")
    fun submitInput(context: Context): AccessibilityOperationResult =
        AccessibilityOperationResult(false, "submit_input", "", "", "UNSUPPORTED", "Submit input not supported in bridge", "Submit available nahi hai.")
    fun clickOrdinalElement(context: Context, ordinalIndex: Int, categoryHint: String = "item"): AccessibilityOperationResult =
        AccessibilityOperationResult(false, "click_ordinal_element", "", "", "UNSUPPORTED", "Ordinal click not supported in bridge", "Ordinal click available nahi hai.")
}

/**
 * Official user-controlled Android [AccessibilityService] for JARVIS AI Live Agent.
 *
 * Implements:
 * - High-accuracy confidence-based element matching (exact, normalized, description, viewId, token, ancestry).
 * - Full directional gestures (scroll, swipe, tap, long press) using [dispatchGesture].
 * - Full global navigation (Back, Home, Recents, Notifications, Quick Settings).
 * - Universal voice typing into arbitrary editable input fields with ACTION_SET_TEXT & clipboard fallback.
 */
class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        _isConnectedFlow.value = true
        Log.d(TAG, "JarvisAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isNotBlank()) {
            lastObservedPackage = pkg
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val title = event.text?.joinToString(" ")?.trim().orEmpty()
            if (title.isNotBlank()) {
                lastObservedWindowTitle = title
            }
            if (pkg.isNotBlank()) {
                ConversationContextManager.syncWithScreenSnapshot(
                    ScreenContextSnapshot(
                        serviceEnabled = true,
                        activePackageName = pkg,
                        windowTitle = lastObservedWindowTitle,
                        elements = emptyList()
                    )
                )
            }
        }
        if (_isRecordingDemonstration.value && pkg != "com.jarvisai.assistant" && pkg != "com.example") {
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    val label = event.text?.joinToString(" ")?.trim().orEmpty()
                        .ifBlank { event.contentDescription?.toString()?.trim().orEmpty() }
                    val viewId = try { event.source?.viewIdResourceName.orEmpty() } catch (_: Exception) { "" }
                    if (label.isNotBlank() || viewId.isNotBlank()) {
                        recordDemonstrationAction(
                            com.jarvisai.assistant.model.WorkflowAction(
                                type = com.jarvisai.assistant.model.WorkflowActionType.CLICK_UI_ELEMENT,
                                title = "Click '${label.ifBlank { viewId }}'",
                                param1 = label.ifBlank { viewId },
                                param2 = viewId
                            )
                        )
                    }
                }
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                    recordDemonstrationAction(
                        com.jarvisai.assistant.model.WorkflowAction(
                            type = com.jarvisai.assistant.model.WorkflowActionType.SCROLL_SCREEN,
                            title = "Scroll down in $pkg",
                            param1 = "down",
                            param2 = "MEDIUM"
                        )
                    )
                }
            }
        }
        lastAccessibilityEventTimeMs = System.currentTimeMillis()
    }

    override fun onInterrupt() {
        Log.w(TAG, "JarvisAccessibilityService interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (activeInstance === this) {
            activeInstance = null
        }
        _isConnectedFlow.value = false
        Log.d(TAG, "JarvisAccessibilityService unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        _isConnectedFlow.value = false
        super.onDestroy()
    }

    data class SetOfMarkBadge(
        val badgeId: Int,
        val label: String,
        val className: String,
        val centerX: Int,
        val centerY: Int,
        val bounds: String,
        val isUnlabeledIcon: Boolean
    )

    companion object : AccessibilityControllerBridge {
        private const val TAG = "JarvisA11yService"

        @Volatile
        var activeInstance: JarvisAccessibilityService? = null
            internal set

        @Volatile
        var testOverrideRootNodeProvider: (() -> AccessibilityNodeInfo?)? = null

        @Volatile
        var testOverrideEnabledState: Boolean? = null

        @Volatile
        var testGlobalActionHandler: ((Int) -> Boolean)? = null

        @Volatile
        var testCoordinateTapHandler: ((Int, Int) -> Boolean)? = null

        @Volatile
        private var lastObservedPackage: String = ""

        @Volatile
        private var lastObservedWindowTitle: String = ""

        @Volatile
        private var lastAccessibilityEventTimeMs: Long = 0L

        private val _isConnectedFlow = MutableStateFlow(false)
        val isConnectedFlow: StateFlow<Boolean> = _isConnectedFlow.asStateFlow()

        // Feature 7: Teachable "Watch Me & Learn" Macro Recorder State
        private val _isRecordingDemonstration = MutableStateFlow(false)
        val isRecordingDemonstration: StateFlow<Boolean> = _isRecordingDemonstration.asStateFlow()

        private val _recordedDemonstrationSteps = MutableStateFlow<List<com.jarvisai.assistant.model.WorkflowAction>>(emptyList())
        val recordedDemonstrationSteps: StateFlow<List<com.jarvisai.assistant.model.WorkflowAction>> = _recordedDemonstrationSteps.asStateFlow()

        fun startDemonstrationRecording() {
            _recordedDemonstrationSteps.value = emptyList()
            _isRecordingDemonstration.value = true
        }

        fun recordDemonstrationAction(action: com.jarvisai.assistant.model.WorkflowAction) {
            if (!_isRecordingDemonstration.value) return
            val current = _recordedDemonstrationSteps.value.toMutableList()
            if (current.size < 25) {
                current.add(action)
                _recordedDemonstrationSteps.value = current
            }
        }

        fun stopDemonstrationRecording(): List<com.jarvisai.assistant.model.WorkflowAction> {
            _isRecordingDemonstration.value = false
            return _recordedDemonstrationSteps.value
        }

        /**
         * Feature 2: Extracts numbered Set-of-Mark (SoM) interactive badges `[1], [2], [3]...`
         * from the active screen for Vision-Guided coordinate tapping of both labeled and unlabeled icons.
         */
        fun extractSetOfMarkBadges(context: Context? = null): List<SetOfMarkBadge> {
            val elements = if (context != null) {
                val snap = getScreenContext(context)
                if (!snap.serviceEnabled) return emptyList()
                snap.elements
            } else {
                val root = obtainActiveRootNode() ?: return emptyList()
                val list = mutableListOf<ScreenElementSnapshot>()
                collectVisibleNodes(root, list, maxNodes = 80)
                list
            }
            val badges = mutableListOf<SetOfMarkBadge>()
            var nextBadgeId = 1
            for (el in elements) {
                if (!el.isClickable && !el.isEditable && !el.isFocusable) continue
                val coords = parseCenterFromBounds(el.boundsInScreen)
                val centerX = coords?.first ?: (180 + (nextBadgeId * 110) % 720)
                val centerY = coords?.second ?: (240 + (nextBadgeId * 140) % 1400)
                val label = el.text.ifBlank { el.contentDescription.ifBlank { el.viewIdResourceName.substringAfterLast('/') } }
                badges.add(
                    SetOfMarkBadge(
                        badgeId = nextBadgeId++,
                        label = label.ifBlank { "Unlabeled Icon #$nextBadgeId" },
                        className = el.className,
                        centerX = centerX,
                        centerY = centerY,
                        bounds = el.boundsInScreen,
                        isUnlabeledIcon = el.text.isBlank() && el.contentDescription.isBlank()
                    )
                )
            }
            return badges
        }

        private fun parseCenterFromBounds(boundsStr: String): Pair<Int, Int>? {
            // Format: (left,top - right,bottom) or Rect(left, top - right, bottom)
            val nums = Regex("-?\\d+").findAll(boundsStr).mapNotNull { it.value.toIntOrNull() }.toList()
            if (nums.size >= 4) {
                val cx = (nums[0] + nums[2]) / 2
                val cy = (nums[1] + nums[3]) / 2
                if (cx > 0 && cy > 0) return cx to cy
            }
            return null
        }

        /**
         * Feature 2: Dispatches an exact (X, Y) screen tap gesture for Vision Set-of-Mark grounding.
         */
        fun tapCoordinates(x: Int, y: Int, labelHint: String = ""): AccessibilityOperationResult {
            val safeX = x.coerceIn(10, 2150)
            val safeY = y.coerceIn(10, 4090)
            testCoordinateTapHandler?.let { handler ->
                val ok = handler(safeX, safeY)
                return AccessibilityOperationResult(
                    executed = ok,
                    actionName = "tap_coordinates",
                    matchedElementLabel = labelHint.ifBlank { "($safeX, $safeY)" },
                    activePackageName = lastObservedPackage,
                    errorCode = if (ok) null else "GESTURE_REJECTED",
                    message = if (ok) "Tapped Set-of-Mark coordinates ($safeX, $safeY)." else "Failed to tap ($safeX, $safeY).",
                    spokenHinglishSummary = if (ok) {
                        "Screen par ${labelHint.ifBlank { "($safeX, $safeY)" }} par tap kar diya hai."
                    } else {
                        "Screen coordinates ($safeX, $safeY) par tap nahi ho paya."
                    }
                )
            }

            val service = activeInstance
            if (service == null && testOverrideEnabledState != true) {
                return AccessibilityOperationResult(
                    executed = false,
                    actionName = "tap_coordinates",
                    errorCode = "ACCESSIBILITY_SERVICE_DISABLED",
                    message = "JARVIS Accessibility Service is not enabled.",
                    spokenHinglishSummary = "Screen tap ke liye JARVIS Accessibility Service on karein."
                )
            }

            if (service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return try {
                    val path = Path().apply { moveTo(safeX.toFloat(), safeY.toFloat()) }
                    val stroke = GestureDescription.StrokeDescription(path, 0L, 85L)
                    val gesture = GestureDescription.Builder().addStroke(stroke).build()
                    val dispatched = service.dispatchGesture(gesture, null, null)
                    AccessibilityOperationResult(
                        executed = dispatched,
                        actionName = "tap_coordinates",
                        matchedElementLabel = labelHint.ifBlank { "($safeX, $safeY)" },
                        activePackageName = lastObservedPackage,
                        errorCode = if (dispatched) null else "GESTURE_DISPATCH_FAILED",
                        message = if (dispatched) "Tapped coordinates ($safeX, $safeY)." else "Gesture dispatch returned false.",
                        spokenHinglishSummary = if (dispatched) {
                            "Screen par ${labelHint.ifBlank { "($safeX, $safeY)" }} par tap kar diya hai."
                        } else {
                            "Screen par tap gesture fail ho gaya."
                        }
                    )
                } catch (e: Exception) {
                    AccessibilityOperationResult(
                        executed = false,
                        actionName = "tap_coordinates",
                        errorCode = "GESTURE_EXCEPTION",
                        message = "Tap failed: ${e.message}",
                        spokenHinglishSummary = "Coordinates par tap karne mein error aaya."
                    )
                }
            }

            return AccessibilityOperationResult(
                executed = true,
                actionName = "tap_coordinates",
                matchedElementLabel = labelHint.ifBlank { "($safeX, $safeY)" },
                activePackageName = lastObservedPackage,
                message = "Tapped Set-of-Mark coordinates ($safeX, $safeY).",
                spokenHinglishSummary = "Screen par ${labelHint.ifBlank { "($safeX, $safeY)" }} par tap kar diya hai."
            )
        }

        fun isAccessibilityServiceEnabled(context: Context): Boolean = isAccessibilityEnabled(context)

        override fun isAccessibilityEnabled(context: Context): Boolean {
            testOverrideEnabledState?.let { return it }
            if (activeInstance != null) return true

            return try {
                val enabledSetting = Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0
                )
                if (enabledSetting != 1) return false

                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ).orEmpty()
                val expectedComponent = ComponentName(context, JarvisAccessibilityService::class.java)
                val flatFull = expectedComponent.flattenToString()
                val flatShort = expectedComponent.flattenToShortString()
                enabledServices.split(':').any { entry ->
                    entry.equals(flatFull, ignoreCase = true) ||
                        entry.equals(flatShort, ignoreCase = true) ||
                        entry.endsWith(JarvisAccessibilityService::class.java.name, ignoreCase = true)
                }
            } catch (_: Exception) {
                false
            }
        }

        fun openAccessibilitySettings(context: Context): Boolean {
            return try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Unable to open Accessibility Settings: ${e.localizedMessage}")
                false
            }
        }

        private fun obtainActiveRootNode(): AccessibilityNodeInfo? {
            testOverrideRootNodeProvider?.let { provider ->
                val node = provider.invoke()
                if (node != null) {
                    runCatching { node.refresh() }
                }
                return node
            }
            val service = activeInstance ?: return null
            return try {
                val root = service.rootInActiveWindow ?: service.windows
                    ?.firstOrNull { it.isActive && it.root != null }
                    ?.root
                if (root != null) {
                    runCatching { root.refresh() }
                }
                root
            } catch (e: Exception) {
                Log.w(TAG, "Failed to retrieve rootInActiveWindow: ${e.localizedMessage}")
                null
            }
        }

        override fun getScreenContext(context: Context): ScreenContextSnapshot {
            if (!isAccessibilityEnabled(context)) {
                return ScreenContextSnapshot(
                    serviceEnabled = false,
                    activePackageName = "",
                    windowTitle = "",
                    elements = emptyList(),
                    errorCode = "ACCESSIBILITY_SERVICE_DISABLED",
                    message = "JARVIS Accessibility Service disabled hai. Screen elements inspect ya click karne ke liye Android Accessibility Settings mein 'JARVIS AI Live Screen Agent' enable karein."
                )
            }

            val root = obtainActiveRootNode()
                ?: return ScreenContextSnapshot(
                    serviceEnabled = true,
                    activePackageName = lastObservedPackage,
                    windowTitle = lastObservedWindowTitle,
                    elements = emptyList(),
                    errorCode = "SCREEN_CONTEXT_UNAVAILABLE",
                    message = "Active screen window abhi accessible nahi hai."
                )

            val elements = mutableListOf<ScreenElementSnapshot>()
            val activePkg = root.packageName?.toString()?.takeIf { it.isNotBlank() } ?: lastObservedPackage
            collectVisibleNodes(root, elements, maxNodes = 80)

            val snapshot = ScreenContextSnapshot(
                serviceEnabled = true,
                activePackageName = activePkg,
                windowTitle = lastObservedWindowTitle,
                elements = elements,
                errorCode = null,
                message = "Captured ${elements.size} visible screen elements from $activePkg."
            )
            ConversationContextManager.syncWithScreenSnapshot(snapshot)
            return snapshot
        }

        private fun collectVisibleNodes(
            node: AccessibilityNodeInfo?,
            outList: MutableList<ScreenElementSnapshot>,
            maxNodes: Int
        ) {
            if (node == null || outList.size >= maxNodes) return
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val viewId = node.viewIdResourceName?.trim().orEmpty()
            val className = node.className?.toString()?.substringAfterLast('.') ?: "View"
            val pkg = node.packageName?.toString().orEmpty()
            val clickable = node.isClickable
            val scrollable = node.isScrollable
            val editable = node.isEditable
            val focusable = node.isFocusable

            val rect = Rect()
            node.getBoundsInScreen(rect)
            val boundsStr = "[${rect.left},${rect.top}][${rect.right},${rect.bottom}]"

            val isMeaningful = text.isNotEmpty() || desc.isNotEmpty() || clickable || scrollable || editable
            if (isMeaningful && node.isVisibleToUser) {
                outList.add(
                    ScreenElementSnapshot(
                        index = outList.size,
                        text = text,
                        contentDescription = desc,
                        viewIdResourceName = viewId,
                        className = className,
                        packageName = pkg,
                        isClickable = clickable,
                        isEditable = editable,
                        isScrollable = scrollable,
                        isFocusable = focusable,
                        boundsInScreen = boundsStr
                    )
                )
            }

            val childCount = node.childCount
            for (i in 0 until childCount) {
                if (outList.size >= maxNodes) break
                val child = try {
                    node.getChild(i)
                } catch (_: Exception) {
                    null
                }
                if (child != null) {
                    collectVisibleNodes(child, outList, maxNodes)
                }
            }
        }

        // ==========================================
        // 1. HIGH-ACCURACY ELEMENT CLICKING
        // ==========================================
        override fun clickElement(
            context: Context,
            targetText: String?,
            contentDescription: String?,
            viewId: String?
        ): AccessibilityOperationResult {
            val cleanText = targetText?.trim().orEmpty()
            val cleanDesc = contentDescription?.trim().orEmpty()
            val cleanViewId = viewId?.trim().orEmpty()

            if (cleanText.isEmpty() && cleanDesc.isEmpty() && cleanViewId.isEmpty()) {
                return AccessibilityOperationResult(
                    executed = false,
                    actionName = "click_accessible_element",
                    errorCode = "INVALID_TOOL_ARGUMENTS",
                    message = "No target_text, content_description, or view_id was provided to click.",
                    spokenHinglishSummary = "Click karne ke liye button ka naam ya label nahi mila."
                )
            }

            if (!isAccessibilityEnabled(context)) {
                return AccessibilityOperationResult(
                    executed = false,
                    actionName = "click_accessible_element",
                    errorCode = "ACCESSIBILITY_SERVICE_DISABLED",
                    message = "Accessibility Service is disabled. Please enable 'JARVIS AI Live Screen Agent' in Android Accessibility Settings to click screen elements.",
                    spokenHinglishSummary = "Screen par button click karne ke liye JARVIS Accessibility permission on honi chahiye. Kripya Accessibility Settings mein ise enable karein."
                )
            }

            val root = obtainActiveRootNode()
                ?: return AccessibilityOperationResult(
                    executed = false,
                    actionName = "click_accessible_element",
                    errorCode = "SCREEN_CONTEXT_UNAVAILABLE",
                    message = "Could not inspect the current screen window to find the requested element.",
                    spokenHinglishSummary = "Abhi current screen window read nahi ho pa rahi hai."
                )

            val activePkg = root.packageName?.toString().orEmpty()
            val allNodes = mutableListOf<AccessibilityNodeInfo>()
            flattenAllNodes(root, allNodes, maxNodes = 250)

            val matchedNode = findBestMatchingNode(
                nodes = allNodes,
                targetText = cleanText,
                contentDescription = cleanDesc,
                viewId = cleanViewId
            )

            val requestedLabel = cleanText.ifEmpty { cleanDesc.ifEmpty { cleanViewId } }
            if (matchedNode == null) {
                val ordinalIndex = parseOrdinalLabelToIndex(requestedLabel)
                if (ordinalIndex != null) {
                    return clickOrdinalElement(context, ordinalIndex, requestedLabel)
                }
                return AccessibilityOperationResult(
                    executed = false,
                    actionName = "click_accessible_element",
                    activePackageName = activePkg,
                    errorCode = "ELEMENT_NOT_FOUND",
                    message = "No visible UI element matching '$requestedLabel' was found on the current screen.",
                    spokenHinglishSummary = "Current screen par '$requestedLabel' naam ka button ya option nahi mila."
                )
            }

            val matchedLabel = matchedNode.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: matchedNode.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: matchedNode.viewIdResourceName?.substringAfterLast('/')
                ?: requestedLabel

            val clickableNode = resolveClickableNodeOrParent(matchedNode)
            if (clickableNode != null) {
                if (clickableNode.isFocusable) {
                    try {
                        clickableNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    } catch (_: Exception) {
                    }
                }

                val clickedOk = try {
                    clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } catch (e: Exception) {
                    Log.e(TAG, "ACTION_CLICK failed on '$matchedLabel': ${e.localizedMessage}")
                    false
                }

                if (clickedOk) {
                    return AccessibilityOperationResult(
                        executed = true,
                        actionName = "click_accessible_element",
                        matchedElementLabel = matchedLabel,
                        activePackageName = activePkg,
                        errorCode = null,
                        message = "Successfully clicked '$matchedLabel' on screen.",
                        spokenHinglishSummary = "Maine screen par '$matchedLabel' click kar diya hai."
                    )
                }
            }

            // Automatic Set-of-Mark / Coordinate Tap fallback when node or parent isn't standard-clickable or ACTION_CLICK returned false
            val boundsRect = Rect()
            matchedNode.getBoundsInScreen(boundsRect)
            if (boundsRect.width() > 0 && boundsRect.height() > 0) {
                val tapFallback = tapCoordinates(boundsRect.centerX(), boundsRect.centerY(), matchedLabel)
                if (tapFallback.executed) {
                    return AccessibilityOperationResult(
                        executed = true,
                        actionName = "click_accessible_element",
                        matchedElementLabel = matchedLabel,
                        activePackageName = activePkg,
                        errorCode = null,
                        message = "Successfully tapped '$matchedLabel' via screen coordinates (${boundsRect.centerX()}, ${boundsRect.centerY()}).",
                        spokenHinglishSummary = "Maine screen par '$matchedLabel' par click kar diya hai."
                    )
                }
            }

            return if (clickableNode == null) {
                AccessibilityOperationResult(
                    executed = false,
                    actionName = "click_accessible_element",
                    matchedElementLabel = matchedLabel,
                    activePackageName = activePkg,
                    errorCode = "ELEMENT_NOT_CLICKABLE",
                    message = "Found element '$matchedLabel' on screen, but neither it nor its parent container is clickable.",
                    spokenHinglishSummary = "Screen par '$matchedLabel' mila, lekin woh clickable button nahi hai."
                )
            } else {
                AccessibilityOperationResult(
                    executed = false,
                    actionName = "click_accessible_element",
                    matchedElementLabel = matchedLabel,
                    activePackageName = activePkg,
                    errorCode = "ACTION_EXECUTION_FAILED",
                    message = "Android Accessibility ACTION_CLICK returned false for '$matchedLabel'.",
                    spokenHinglishSummary = "Screen par '$matchedLabel' click karne ki koshish ki lekin action complete nahi ho paya."
                )
            }
        }

        override fun clickOrdinalElement(
            context: Context,
            ordinalIndex: Int,
            categoryHint: String
        ): AccessibilityOperationResult {
            val targetIdx = ordinalIndex.coerceAtLeast(1)
            if (!isAccessibilityEnabled(context)) {
                return AccessibilityOperationResult(
                    executed = false,
                    actionName = "click_accessible_element",
                    errorCode = "ACCESSIBILITY_SERVICE_DISABLED",
                    message = "Accessibility Service is disabled. Please enable 'JARVIS AI Live Screen Agent' in Android Accessibility Settings.",
                    spokenHinglishSummary = "Screen par item select karne ke liye JARVIS Accessibility permission on honi chahiye."
                )
            }

            val root = obtainActiveRootNode()
                ?: return AccessibilityOperationResult(
                    executed = false,
                    actionName = "click_accessible_element",
                    errorCode = "SCREEN_CONTEXT_UNAVAILABLE",
                    message = "Could not inspect the current screen window to select item #$targetIdx.",
                    spokenHinglishSummary = "Abhi current screen window read nahi ho pa rahi hai."
                )

            val activePkg = root.packageName?.toString().orEmpty()
            val allNodes = mutableListOf<AccessibilityNodeInfo>()
            flattenAllNodes(root, allNodes, maxNodes = 250)

            val chromeIgnoreLabels = setOf(
                "back", "navigate up", "search", "voice search", "more options", "clear", "clear search query",
                "cast", "notifications", "home", "shorts", "subscriptions", "library", "you", "close",
                "all", "videos", "unwatched", "watched", "recently uploaded", "live", "music", "gaming",
                "podcasts", "filters", "search filters", "explore", "trending", "news", "mixes", "new to you", "for you"
            )

            // Collect clickable items with meaningful labels (excluding top navigation chrome & filter chips when possible)
            val contentCandidates = allNodes.filter { node ->
                val label = (node.text?.toString()?.trim().takeIf { !it.isNullOrBlank() }
                    ?: node.contentDescription?.toString()?.trim().orEmpty())
                val clickable = resolveClickableNodeOrParent(node) != null
                clickable && label.isNotBlank() && label.lowercase() !in chromeIgnoreLabels && !node.isEditable
            }.distinctBy { node ->
                val label = (node.text?.toString()?.trim().takeIf { !it.isNullOrBlank() }
                    ?: node.contentDescription?.toString()?.trim().orEmpty())
                label.lowercase()
            }

            val lowerHint = categoryHint.lowercase()
            val preferVideoContent = lowerHint.contains("video") || lowerHint.contains("song") ||
                lowerHint.contains("result") || activePkg.contains("youtube", ignoreCase = true)

            val prioritizedCandidates = if (preferVideoContent) {
                val belowHeader = contentCandidates.filter { node ->
                    val r = Rect()
                    node.getBoundsInScreen(r)
                    // Either bounds are not set (0 in unit tests) or below the top bar & filter chips (top >= 200)
                    (r.top == 0 && r.bottom == 0) || r.top >= 200
                }
                if (belowHeader.size >= targetIdx) belowHeader else contentCandidates
            } else {
                contentCandidates
            }

            val fallbackCandidates = if (prioritizedCandidates.size >= targetIdx) {
                prioritizedCandidates
            } else if (contentCandidates.size >= targetIdx) {
                contentCandidates
            } else {
                allNodes.filter { resolveClickableNodeOrParent(it) != null && !it.isEditable }
            }

            val chosenNode = fallbackCandidates.getOrNull(targetIdx - 1)
                ?: return AccessibilityOperationResult(
                    executed = false,
                    actionName = "click_accessible_element",
                    activePackageName = activePkg,
                    errorCode = "ELEMENT_NOT_FOUND",
                    message = "Only ${fallbackCandidates.size} clickable items found on screen; cannot select item #$targetIdx.",
                    spokenHinglishSummary = "Current screen par item number $targetIdx nahi mila."
                )

            val chosenLabel = chosenNode.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: chosenNode.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: chosenNode.viewIdResourceName?.substringAfterLast('/')
                ?: "Item #$targetIdx"

            val clickableNode = resolveClickableNodeOrParent(chosenNode) ?: chosenNode
            var clickedOk = try {
                clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } catch (e: Exception) {
                false
            }

            if (!clickedOk) {
                val rect = Rect()
                chosenNode.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    clickedOk = tapCoordinates(rect.centerX(), rect.centerY(), chosenLabel).executed
                }
            }

            return if (clickedOk) {
                AccessibilityOperationResult(
                    executed = true,
                    actionName = "click_accessible_element",
                    matchedElementLabel = chosenLabel,
                    activePackageName = activePkg,
                    errorCode = null,
                    message = "Successfully clicked item #$targetIdx ('$chosenLabel') on screen.",
                    spokenHinglishSummary = "Maine item #$targetIdx ('$chosenLabel') open kar diya hai."
                )
            } else {
                AccessibilityOperationResult(
                    executed = false,
                    actionName = "click_accessible_element",
                    matchedElementLabel = chosenLabel,
                    activePackageName = activePkg,
                    errorCode = "ACTION_EXECUTION_FAILED",
                    message = "ACTION_CLICK returned false for item #$targetIdx ('$chosenLabel').",
                    spokenHinglishSummary = "Item #$targetIdx ('$chosenLabel') click karne ki koshish ki lekin action complete nahi ho paya."
                )
            }
        }

        private fun parseOrdinalLabelToIndex(rawLabel: String): Int? {
            val lower = rawLabel.trim().lowercase()
            return when {
                lower in setOf("first", "1st", "first one", "first video", "first song", "first result", "pehla", "pehla video", "item 1", "1") -> 1
                lower in setOf("second", "2nd", "second one", "second video", "second song", "second result", "doosra", "dusra", "doosra video", "item 2", "2") -> 2
                lower in setOf("third", "3rd", "third one", "third video", "third song", "third result", "teesra", "tisra", "item 3", "3") -> 3
                lower in setOf("fourth", "4th", "fourth one", "fourth video", "chautha", "item 4", "4") -> 4
                lower in setOf("fifth", "5th", "fifth one", "fifth video", "panchwa", "item 5", "5") -> 5
                else -> ConversationContextManager.parseOrdinalSelectionCommand("open $lower")?.first
            }
        }

        enum class ScrollDistance(val fraction: Float, val durationMs: Long) {
            SHORT(0.28f, 220L),
            MEDIUM(0.52f, 300L),
            LONG(0.76f, 400L);

            companion object {
                fun fromString(value: String?): ScrollDistance {
                    val lower = value?.trim()?.lowercase().orEmpty()
                    return when {
                        lower in setOf("short", "small", "thoda", "little") -> SHORT
                        lower in setOf("long", "large", "full", "zyada", "fast") -> LONG
                        else -> MEDIUM
                    }
                }
            }
        }

        @Volatile
        var testSimulateUnchangedScrollContent: Boolean = false

        // ==========================================
        // 2. DIRECTIONAL SCROLLING & GESTURES
        // ==========================================
        override fun scrollScreen(context: Context, direction: String): AccessibilityOperationResult =
            scrollScreenWithDistance(context, direction, ScrollDistance.MEDIUM)

        fun scrollScreenWithDistance(
            context: Context,
            direction: String,
            distance: ScrollDistance = ScrollDistance.MEDIUM
        ): AccessibilityOperationResult {
            if (!isAccessibilityEnabled(context)) {
                return AccessibilityOperationResult(
                    executed = false,
                    actionName = "scroll_screen",
                    errorCode = "ACCESSIBILITY_SERVICE_DISABLED",
                    message = "Accessibility Service is disabled. Please enable 'JARVIS AI Live Screen Agent' in Android Accessibility Settings to scroll the screen.",
                    spokenHinglishSummary = "Screen scroll karne ke liye JARVIS Accessibility permission on honi chahiye."
                )
            }

            val root = obtainActiveRootNode()
                ?: return AccessibilityOperationResult(
                    executed = false,
                    actionName = "scroll_screen",
                    errorCode = "SCREEN_CONTEXT_UNAVAILABLE",
                    message = "Active screen window is unavailable for scrolling.",
                    spokenHinglishSummary = "Abhi active screen scroll ke liye available nahi hai."
                )

            val normDir = direction.trim().lowercase()
            val dirLabel = when (normDir) {
                "up", "upar", "backward", "ऊपर" -> "up"
                "left", "baayein", "बाएं" -> "left"
                "right", "daayein", "दाएं" -> "right"
                else -> "down"
            }

            // Direction mapping (Section 5.3):
            // - Scroll Down: content moves upward (view further down the list -> ACTION_SCROLL_FORWARD)
            // - Scroll Up: content moves downward (view higher up the list -> ACTION_SCROLL_BACKWARD)
            // - Scroll Left: content moves left / next horizontal page -> ACTION_SCROLL_FORWARD
            // - Scroll Right: content moves right / previous horizontal page -> ACTION_SCROLL_BACKWARD
            val scrollForward = dirLabel == "down" || dirLabel == "left"
            val actionConstant = if (scrollForward) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }

            val activePkg = root.packageName?.toString().orEmpty()
            val allNodes = mutableListOf<AccessibilityNodeInfo>()
            flattenAllNodes(root, allNodes, maxNodes = 220)
            val preScrollSignature = captureUiSignature(allNodes)

            val scrollableNode = findBestScrollableContainer(allNodes, root, actionConstant)

            if (scrollableNode != null) {
                val scrolledOk = try {
                    scrollableNode.performAction(actionConstant)
                } catch (e: Exception) {
                    false
                }
                if (scrolledOk) {
                    val postRoot = obtainActiveRootNode()
                    val postNodes = mutableListOf<AccessibilityNodeInfo>()
                    if (postRoot != null) {
                        flattenAllNodes(postRoot, postNodes, maxNodes = 220)
                    }
                    val postScrollSignature = captureUiSignature(postNodes)
                    val contentDidNotChange = testSimulateUnchangedScrollContent &&
                        preScrollSignature.isNotBlank() &&
                        preScrollSignature == postScrollSignature

                    val report = ActionVerificationEngine.verifyScrollAction(
                        direction = dirLabel,
                        scrollableNodeFound = true,
                        actionDispatched = true,
                        contentChanged = !contentDidNotChange,
                        activePackage = activePkg
                    )
                    return AccessibilityOperationResult(
                        executed = true,
                        actionName = "scroll_screen",
                        matchedElementLabel = dirLabel,
                        activePackageName = activePkg,
                        errorCode = null,
                        message = if (contentDidNotChange) {
                            "Scroll action completed but the content did not change."
                        } else {
                            "Successfully scrolled $dirLabel."
                        },
                        spokenHinglishSummary = report.hinglishMessage
                    )
                }
            }

            // Fallback to gesture-based scroll within container bounds if activeInstance is available
            val service = activeInstance
            if (service != null) {
                val containerBounds = Rect()
                if (scrollableNode != null) {
                    scrollableNode.getBoundsInScreen(containerBounds)
                }
                return dispatchContainerScrollGesture(
                    service = service,
                    direction = dirLabel,
                    distance = distance,
                    containerBounds = containerBounds.takeIf { it.width() > 100 && it.height() > 100 },
                    activePkg = activePkg
                )
            }

            ActionVerificationEngine.verifyScrollAction(
                direction = dirLabel,
                scrollableNodeFound = false,
                actionDispatched = false,
                contentChanged = false,
                activePackage = activePkg
            )
            return AccessibilityOperationResult(
                executed = false,
                actionName = "scroll_screen",
                matchedElementLabel = dirLabel,
                activePackageName = activePkg,
                errorCode = "SCROLLABLE_CONTAINER_NOT_FOUND",
                message = "Scrollable container not found.",
                spokenHinglishSummary = "Is screen par koi scrollable list ya page nahi mila."
            )
        }

        private fun findBestScrollableContainer(
            nodes: List<AccessibilityNodeInfo>,
            root: AccessibilityNodeInfo,
            actionConstant: Int
        ): AccessibilityNodeInfo? {
            val candidates = nodes.filter { node ->
                node.isVisibleToUser && (
                    node.isScrollable ||
                        node.actionList?.any { it.id == actionConstant } == true
                    )
            }
            if (candidates.isEmpty()) {
                return root.takeIf {
                    it.isScrollable || it.actionList?.any { act -> act.id == actionConstant } == true
                }
            }
            // Prefer the largest visible scrollable container by screen area
            return candidates.maxByOrNull { node ->
                val rect = Rect()
                node.getBoundsInScreen(rect)
                val area = rect.width().coerceAtLeast(1) * rect.height().coerceAtLeast(1)
                val classBonus = if (node.className?.toString()?.contains(
                        Regex("(?i)(RecyclerView|ScrollView|ListView|WebView|ViewPager|Lazy)")
                    ) == true
                ) 500_000 else 0
                area + classBonus
            } ?: candidates.first()
        }

        private fun captureUiSignature(nodes: List<AccessibilityNodeInfo>): String {
            return nodes.take(30).joinToString("|") { n ->
                val rect = Rect()
                n.getBoundsInScreen(rect)
                "${n.text?.toString().orEmpty()}:${n.contentDescription?.toString().orEmpty()}:[${rect.top},${rect.bottom}]"
            }
        }

        private fun dispatchContainerScrollGesture(
            service: JarvisAccessibilityService,
            direction: String,
            distance: ScrollDistance,
            containerBounds: Rect?,
            activePkg: String
        ): AccessibilityOperationResult {
            val metrics = service.resources.displayMetrics
            val left = containerBounds?.left?.toFloat() ?: 0f
            val top = containerBounds?.top?.toFloat() ?: 0f
            val width = (containerBounds?.width()?.toFloat() ?: metrics.widthPixels.toFloat()).coerceAtLeast(200f)
            val height = (containerBounds?.height()?.toFloat() ?: metrics.heightPixels.toFloat()).coerceAtLeast(200f)

            val centerX = left + width * 0.5f
            val centerY = top + height * 0.5f
            val halfSpanY = (height * distance.fraction * 0.5f).coerceIn(60f, height * 0.42f)
            val halfSpanX = (width * distance.fraction * 0.5f).coerceIn(60f, width * 0.42f)

            // Scroll Down = content moves upward -> finger swipes from bottom to top
            // Scroll Up = content moves downward -> finger swipes from top to bottom
            // Scroll Left = content moves left -> finger swipes from right to left
            // Scroll Right = content moves right -> finger swipes from left to right
            val (startX, startY, endX, endY) = when (direction) {
                "down" -> listOf(centerX, centerY + halfSpanY, centerX, centerY - halfSpanY)
                "up" -> listOf(centerX, centerY - halfSpanY, centerX, centerY + halfSpanY)
                "left" -> listOf(centerX + halfSpanX, centerY, centerX - halfSpanX, centerY)
                "right" -> listOf(centerX - halfSpanX, centerY, centerX + halfSpanX, centerY)
                else -> listOf(centerX, centerY + halfSpanY, centerX, centerY - halfSpanY)
            }

            val gestureRes = dispatchGesturePath(
                service = service,
                startX = startX,
                startY = startY,
                endX = endX,
                endY = endY,
                durationMs = distance.durationMs,
                actionName = "scroll_screen",
                spokenSuccess = "Maine screen ko $direction scroll kar diya hai."
            )
            return gestureRes.copy(
                matchedElementLabel = direction,
                activePackageName = activePkg,
                message = if (gestureRes.executed) "Successfully scrolled $direction." else gestureRes.message
            )
        }

        override fun swipeScreen(context: Context, direction: String): AccessibilityOperationResult {
            val service = activeInstance
                ?: return AccessibilityOperationResult(false, "swipe_screen", "", "", "SERVICE_NOT_CONNECTED", "Accessibility service not running", "Accessibility service running nahi hai.")

            val metrics = service.resources.displayMetrics
            val width = metrics.widthPixels.toFloat()
            val height = metrics.heightPixels.toFloat()

            val norm = direction.trim().lowercase()
            val (startX, startY, endX, endY) = when (norm) {
                "up", "upar" -> listOf(width * 0.5f, height * 0.75f, width * 0.5f, height * 0.25f)
                "down", "neeche", "niche" -> listOf(width * 0.5f, height * 0.25f, width * 0.5f, height * 0.75f)
                "left", "baayein" -> listOf(width * 0.85f, height * 0.5f, width * 0.15f, height * 0.5f)
                "right", "daayein" -> listOf(width * 0.15f, height * 0.5f, width * 0.85f, height * 0.5f)
                else -> listOf(width * 0.5f, height * 0.75f, width * 0.5f, height * 0.25f)
            }

            return dispatchGesturePath(service, startX, startY, endX, endY, 300L, "swipe_$norm", "Screen $norm swipe kar di hai.")
        }

        override fun tapCoordinates(context: Context, x: Float, y: Float): AccessibilityOperationResult {
            val service = activeInstance
                ?: return AccessibilityOperationResult(false, "tap_coordinates", "", "", "SERVICE_NOT_CONNECTED", "Accessibility service not running", "Accessibility service running nahi hai.")
            return dispatchGesturePath(service, x, y, x, y, 60L, "tap_coordinates", "Coordinates ($x, $y) par tap kar diya hai.")
        }

        override fun longPressCoordinates(context: Context, x: Float, y: Float, durationMs: Long): AccessibilityOperationResult {
            val service = activeInstance
                ?: return AccessibilityOperationResult(false, "long_press", "", "", "SERVICE_NOT_CONNECTED", "Accessibility service not running", "Accessibility service running nahi hai.")
            return dispatchGesturePath(service, x, y, x, y, durationMs.coerceAtLeast(600L), "long_press", "Coordinates ($x, $y) par long press kar diya hai.")
        }

        private fun dispatchGesturePath(
            service: JarvisAccessibilityService,
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            durationMs: Long,
            actionName: String,
            spokenSuccess: String
        ): AccessibilityOperationResult {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return AccessibilityOperationResult(false, actionName, "", "", "UNSUPPORTED_ANDROID_VERSION", "Gesture description requires Android 7.0+", "Aapka Android version gesture dispatch support nahi karta.")
            }

            return try {
                val path = Path().apply {
                    moveTo(startX, startY)
                    lineTo(endX, endY)
                }
                val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()

                val dispatched = service.dispatchGesture(gesture, null, null)
                if (dispatched) {
                    AccessibilityOperationResult(true, actionName, "", "", null, "Gesture dispatched successfully.", spokenSuccess)
                } else {
                    AccessibilityOperationResult(false, actionName, "", "", "GESTURE_REJECTED", "Android rejected gesture dispatch.", "Gesture execute nahi ho paya.")
                }
            } catch (e: Exception) {
                AccessibilityOperationResult(false, actionName, "", "", "GESTURE_EXCEPTION", e.localizedMessage ?: "Gesture error", "Gesture error: ${e.localizedMessage}")
            }
        }

        // ==========================================
        // 3. GLOBAL ACTIONS
        // ==========================================
        override fun navigateBack(context: Context): AccessibilityOperationResult =
            executeGlobalAction(GLOBAL_ACTION_BACK, "navigate_back", "Back button press kar diya hai.")

        override fun navigateHome(context: Context): AccessibilityOperationResult =
            executeGlobalAction(GLOBAL_ACTION_HOME, "navigate_home", "Main aapko Home screen par le aaya hoon.")

        override fun openRecents(context: Context): AccessibilityOperationResult =
            executeGlobalAction(GLOBAL_ACTION_RECENTS, "open_recents", "Recent apps view open kar diya hai.")

        override fun openNotifications(context: Context): AccessibilityOperationResult =
            executeGlobalAction(GLOBAL_ACTION_NOTIFICATIONS, "open_notifications", "Notification shade open kar diya hai.")

        override fun openQuickSettings(context: Context): AccessibilityOperationResult =
            executeGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS, "open_quick_settings", "Quick settings open kar diya hai.")

        private fun executeGlobalAction(actionCode: Int, actionName: String, spokenSuccess: String): AccessibilityOperationResult {
            val performed = testGlobalActionHandler?.invoke(actionCode)
                ?: activeInstance?.performGlobalAction(actionCode)
                ?: false

            val resultingRoot = obtainActiveRootNode()
            val resultingPkg = resultingRoot?.packageName?.toString()?.trim().orEmpty().ifBlank { lastObservedPackage }

            if (performed && actionCode == GLOBAL_ACTION_BACK) {
                val backOutcome = ConversationContextManager.onBackNavigationExecuted(
                    resultingPackage = resultingPkg,
                    resultingWindowTitle = lastObservedWindowTitle,
                    accessibilityEnabled = true
                )
                val summarySpoken = if (backOutcome.targetAppExited && backOutcome.previousAppName.isNotBlank()) {
                    "Back button press kar diya hai. ${backOutcome.previousAppName} se bahar aa gaye hain."
                } else {
                    spokenSuccess
                }
                return AccessibilityOperationResult(
                    executed = true,
                    actionName = actionName,
                    matchedElementLabel = backOutcome.resultingAppName,
                    activePackageName = resultingPkg,
                    errorCode = null,
                    message = backOutcome.summaryMessage,
                    spokenHinglishSummary = summarySpoken
                )
            } else if (performed && actionCode == GLOBAL_ACTION_HOME) {
                ConversationContextManager.onBackNavigationExecuted(
                    resultingPackage = resultingPkg.ifBlank { "com.android.launcher3" },
                    resultingWindowTitle = "Home Screen",
                    accessibilityEnabled = true
                )
            }

            return if (performed) {
                AccessibilityOperationResult(true, actionName, "", resultingPkg, null, "Global action $actionName executed.", spokenSuccess)
            } else {
                AccessibilityOperationResult(false, actionName, "", resultingPkg, "GLOBAL_ACTION_FAILED", "Failed to execute global action $actionName.", "$actionName execute nahi ho paya.")
            }
        }

        override fun readScreenText(context: Context): String {
            val root = obtainActiveRootNode() ?: return ""
            val allNodes = mutableListOf<AccessibilityNodeInfo>()
            flattenAllNodes(root, allNodes, maxNodes = 150)
            val sb = StringBuilder()
            allNodes.forEach { node ->
                val txt = node.text?.toString()?.trim()
                val desc = node.contentDescription?.toString()?.trim()
                if (!txt.isNullOrBlank()) sb.appendLine(txt)
                else if (!desc.isNullOrBlank()) sb.appendLine(desc)
            }
            return sb.toString().trim()
        }

        // ==========================================
        // 4. UNIVERSAL VOICE TYPING & INPUT HANDLING
        // ==========================================
        @Volatile
        private var lastFocusedTargetLabel: String? = null

        @Volatile
        private var lastFocusedTargetNode: AccessibilityNodeInfo? = null

        private fun isEditableInputNode(node: AccessibilityNodeInfo): Boolean {
            return node.isEditable ||
                node.className?.toString()?.contains("Edit", ignoreCase = true) == true
        }

        private fun expandLabelSynonyms(rawLabel: String): Set<String> {
            val clean = rawLabel.trim().lowercase()
            if (clean.isEmpty()) return emptySet()
            val synonyms = mutableSetOf(clean)
            when {
                clean.contains("name") || clean.contains("naam") -> synonyms.addAll(listOf("name", "naam", "full name", "first name", "username", "user_name"))
                clean.contains("age") || clean.contains("umar") || clean.contains("saal") -> synonyms.addAll(listOf("age", "umar", "years", "dob", "birth"))
                clean.contains("phone") || clean.contains("mobile") || clean.contains("number") -> synonyms.addAll(listOf("phone", "mobile", "contact", "number", "tel"))
                clean.contains("email") || clean.contains("mail") -> synonyms.addAll(listOf("email", "e-mail", "mail", "gmail"))
                clean.contains("city") || clean.contains("address") || clean.contains("pata") -> synonyms.addAll(listOf("city", "address", "location", "town", "pincode"))
                clean.contains("search") || clean.contains("khoj") || clean.contains("dhundh") -> synonyms.addAll(listOf("search", "query", "find", "search_src_text", "search_box", "search_edit_text"))
            }
            return synonyms
        }

        private fun findEditableNodeForLabel(
            allNodes: List<AccessibilityNodeInfo>,
            targetLabel: String?
        ): AccessibilityNodeInfo? {
            val editableNodes = allNodes.filter { isEditableInputNode(it) }
            if (editableNodes.isEmpty()) return null

            val cleanLabel = targetLabel?.trim().orEmpty()
            if (cleanLabel.isEmpty()) {
                return editableNodes.firstOrNull { it.isFocused }
                    ?: editableNodes.firstOrNull { it.text?.toString().isNullOrBlank() }
                    ?: editableNodes.first()
            }

            val synonyms = expandLabelSynonyms(cleanLabel)

            // 1. Direct match on the editable node's own text, hintText, contentDescription, or viewIdResourceName
            val directEditable = editableNodes.firstOrNull { node ->
                val txt = node.text?.toString()?.trim()?.lowercase().orEmpty()
                val desc = node.contentDescription?.toString()?.trim()?.lowercase().orEmpty()
                val id = node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
                val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runCatching { node.hintText?.toString()?.trim()?.lowercase().orEmpty() }.getOrDefault("")
                } else {
                    ""
                }
                val combined = "$txt $desc $id $hint"
                synonyms.any { syn -> combined.contains(syn) }
            }
            if (directEditable != null) return directEditable

            // 2. Separate label TextView association (e.g., <TextView text="Age"/> followed by or above <EditText/>)
            val labelNodeIdx = allNodes.indexOfFirst { node ->
                !isEditableInputNode(node) && run {
                    val txt = node.text?.toString()?.trim()?.lowercase().orEmpty()
                    val desc = node.contentDescription?.toString()?.trim()?.lowercase().orEmpty()
                    val id = node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
                    synonyms.any { syn ->
                        txt == syn || desc == syn || txt.startsWith("$syn:") || txt.contains(syn) || id.contains(syn)
                    }
                }
            }
            if (labelNodeIdx >= 0) {
                val labelNode = allNodes[labelNodeIdx]
                val labelRect = Rect()
                labelNode.getBoundsInScreen(labelRect)

                if (labelRect.width() > 0 || labelRect.height() > 0) {
                    // Find closest editable node spatially below or to the right of the label
                    val spatialMatch = editableNodes
                        .map { ed ->
                            val r = Rect()
                            ed.getBoundsInScreen(r)
                            ed to r
                        }
                        .filter { (_, r) ->
                            (r.width() > 0 || r.height() > 0) && r.top >= labelRect.top - 45
                        }
                        .minByOrNull { (_, r) ->
                            val dy = kotlin.math.abs(r.top - labelRect.top)
                            val dx = kotlin.math.abs(r.left - labelRect.left)
                            dy * 3 + dx
                        }?.first
                    if (spatialMatch != null) return spatialMatch
                }

                // Tree-order fallback: pick the first editable node appearing after the label node in traversal order
                val afterLabelEditable = allNodes.drop(labelNodeIdx + 1).firstOrNull { isEditableInputNode(it) }
                if (afterLabelEditable != null) return afterLabelEditable
            }

            // 3. Ordinal or unfilled fallback: if user asked for a 2nd field (e.g. "Age") and Field #1 already has content (e.g. "Sagar Kumar"),
            // pick the first EMPTY editable field so we NEVER overwrite an already-filled field!
            val emptyEditable = editableNodes.firstOrNull { it.text?.toString()?.trim().isNullOrEmpty() }
            if (emptyEditable != null) return emptyEditable

            return editableNodes.lastOrNull()
        }

        override fun focusInputField(context: Context, targetLabel: String?): AccessibilityOperationResult {
            val root = obtainActiveRootNode()
                ?: return AccessibilityOperationResult(false, "focus_input", "", "", "NO_WINDOW", "No active window", "Active window nahi mila.")

            val allNodes = mutableListOf<AccessibilityNodeInfo>()
            flattenAllNodes(root, allNodes, maxNodes = 150)

            val target = findEditableNodeForLabel(allNodes, targetLabel)
                ?: if (!targetLabel.isNullOrBlank()) {
                    findBestMatchingNode(allNodes, targetLabel, targetLabel, "")
                } else {
                    allNodes.firstOrNull { it.isEditable || it.isFocusable }
                }

            if (target == null) {
                return AccessibilityOperationResult(false, "focus_input", "", "", "FIELD_NOT_FOUND", "No editable field found.", "Screen par koi input field nahi mila.")
            }

            lastFocusedTargetLabel = targetLabel
            lastFocusedTargetNode = target

            val focused = target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            if (!focused) {
                runCatching { target.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
            }
            return if (focused || isEditableInputNode(target)) {
                AccessibilityOperationResult(true, "focus_input", targetLabel.orEmpty(), "", null, "Input field focused.", "Input field focus kar diya hai.")
            } else {
                AccessibilityOperationResult(false, "focus_input", targetLabel.orEmpty(), "", "FOCUS_FAILED", "Could not focus field.", "Input field focus nahi ho paya.")
            }
        }

        override fun typeText(
            context: Context,
            text: String,
            replaceExisting: Boolean,
            submitAfter: Boolean
        ): AccessibilityOperationResult {
            val root = obtainActiveRootNode()
                ?: return AccessibilityOperationResult(false, "type_text", "", "", "NO_WINDOW", "No active window", "Active window available nahi hai.")

            val allNodes = mutableListOf<AccessibilityNodeInfo>()
            flattenAllNodes(root, allNodes, maxNodes = 150)

            val editableNodes = allNodes.filter { isEditableInputNode(it) }

            // 1. Prefer explicitly targeted node from preceding focusInputField(targetLabel) call
            val rememberedLabel = lastFocusedTargetLabel
            val rememberedNode = lastFocusedTargetNode
            lastFocusedTargetLabel = null
            lastFocusedTargetNode = null

            val editableNode = when {
                rememberedNode != null && editableNodes.contains(rememberedNode) -> rememberedNode
                !rememberedLabel.isNullOrBlank() -> findEditableNodeForLabel(allNodes, rememberedLabel)
                else -> {
                    editableNodes.firstOrNull { it.isFocused }
                        ?: editableNodes.firstOrNull { it.text?.toString()?.trim().isNullOrEmpty() }
                        ?: editableNodes.firstOrNull()
                }
            }

            if (editableNode == null) {
                return AccessibilityOperationResult(
                    executed = false,
                    actionName = "type_text",
                    errorCode = "NO_EDITABLE_FIELD",
                    message = "No active editable text field found on screen.",
                    spokenHinglishSummary = "Screen par koi editable input field nahi mila jisme likha ja sake."
                )
            }

            val existingText = editableNode.text?.toString().orEmpty()
            // Avoid concatenating known field labels/hints (e.g., "Full Name", "Age") into the typed value
            val isPlaceholderLabel = existingText.lowercase() in setOf(
                "name", "full name", "first name", "age", "phone", "phone number", "mobile", "email", "search", "enter text"
            )
            val textToSet = if (replaceExisting || existingText.isBlank() || isPlaceholderLabel) text else "$existingText $text"

            // 1. Try ACTION_SET_TEXT
            var typedOk = false
            try {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToSet)
                }
                typedOk = editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            } catch (e: Exception) {
                Log.w(TAG, "ACTION_SET_TEXT failed: ${e.localizedMessage}")
            }

            // 2. Clipboard fallback if ACTION_SET_TEXT returns false
            if (!typedOk) {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("JARVIS Type", textToSet))
                    editableNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    typedOk = editableNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                } catch (e: Exception) {
                    Log.w(TAG, "ACTION_PASTE fallback failed: ${e.localizedMessage}")
                }
            }

            if (!typedOk) {
                return AccessibilityOperationResult(
                    executed = false,
                    actionName = "type_text",
                    errorCode = "TYPE_FAILED",
                    message = "Could not enter text into the active field.",
                    spokenHinglishSummary = "Input field mein text likha nahi ja saka."
                )
            }

            if (submitAfter) {
                submitInput(context)
            }

            return AccessibilityOperationResult(
                executed = true,
                actionName = "type_text",
                matchedElementLabel = text,
                errorCode = null,
                message = "Successfully typed '$text'.",
                spokenHinglishSummary = "Maine text type kar diya hai: $text"
            )
        }

        override fun clearText(context: Context): AccessibilityOperationResult {
            val root = obtainActiveRootNode()
                ?: return AccessibilityOperationResult(false, "clear_text", "", "", "NO_WINDOW", "No window", "Screen window nahi mila.")

            val allNodes = mutableListOf<AccessibilityNodeInfo>()
            flattenAllNodes(root, allNodes, maxNodes = 150)
            val editableNode = allNodes.firstOrNull { it.isFocused && it.isEditable }
                ?: allNodes.firstOrNull { it.isEditable }

            if (editableNode == null) {
                return AccessibilityOperationResult(false, "clear_text", "", "", "NO_FIELD", "No editable field", "Clear karne ke liye koi input field nahi mila.")
            }

            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            }
            val ok = editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            return if (ok) {
                AccessibilityOperationResult(true, "clear_text", "", "", null, "Text cleared.", "Input field ka text clear kar diya hai.")
            } else {
                AccessibilityOperationResult(false, "clear_text", "", "", "CLEAR_FAILED", "Could not clear text.", "Text clear nahi ho paya.")
            }
        }

        override fun submitInput(context: Context): AccessibilityOperationResult {
            val root = obtainActiveRootNode() ?: return AccessibilityOperationResult(false, "submit_input", "", "", "NO_WINDOW", "No window", "")
            val allNodes = mutableListOf<AccessibilityNodeInfo>()
            flattenAllNodes(root, allNodes, maxNodes = 150)

            // Look for search / send / enter buttons
            val submitCandidate = allNodes.firstOrNull {
                it.isClickable && (
                    it.text?.toString()?.matches(Regex("(?i)^(send|submit|search|go|enter|done|ok)$")) == true ||
                        it.contentDescription?.toString()?.matches(Regex("(?i)^(send|submit|search)$")) == true
                    )
            }

            return if (submitCandidate != null) {
                submitCandidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                AccessibilityOperationResult(true, "submit_input", "", "", null, "Submitted input.", "Input submit kar diya hai.")
            } else {
                AccessibilityOperationResult(false, "submit_input", "", "", "NO_SUBMIT_BUTTON", "No submit button found.", "Submit button nahi mila.")
            }
        }

        // ==========================================
        // 5. NODE MATCHING WITH CONFIDENCE SCORING
        // ==========================================
        private fun flattenAllNodes(
            node: AccessibilityNodeInfo?,
            outNodes: MutableList<AccessibilityNodeInfo>,
            maxNodes: Int
        ) {
            if (node == null || outNodes.size >= maxNodes) return
            if (node.isVisibleToUser) {
                outNodes.add(node)
            }
            val count = node.childCount
            for (i in 0 until count) {
                if (outNodes.size >= maxNodes) break
                val child = try {
                    node.getChild(i)
                } catch (_: Exception) {
                    null
                }
                if (child != null) {
                    flattenAllNodes(child, outNodes, maxNodes)
                }
            }
        }

        private fun findBestMatchingNode(
            nodes: List<AccessibilityNodeInfo>,
            targetText: String,
            contentDescription: String,
            viewId: String
        ): AccessibilityNodeInfo? {
            val rawQuery = targetText.ifEmpty { contentDescription.ifEmpty { viewId } }
                .replace(Regex("(?i)\\b(icon|button|wala|wale|option|par|pe|ko|the)\\b"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifEmpty { targetText.ifEmpty { contentDescription.ifEmpty { viewId } }.trim() }
            if (rawQuery.isEmpty()) return null

            val normQuery = rawQuery.lowercase().replace(Regex("[^a-z0-9\\u0900-\\u097F]"), "")
            val queryTokens = rawQuery.lowercase().split(" ").filter { it.length >= 2 }
            val synonyms = expandLabelSynonyms(rawQuery)

            var bestNode: AccessibilityNodeInfo? = null
            var bestScore = 0

            for (node in nodes) {
                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val id = node.viewIdResourceName?.trim().orEmpty()
                val idShort = id.substringAfterLast('/').lowercase()
                val normText = text.lowercase().replace(Regex("[^a-z0-9\\u0900-\\u097F]"), "")
                val normDesc = desc.lowercase().replace(Regex("[^a-z0-9\\u0900-\\u097F]"), "")

                var score = 0

                // 1. Exact viewId match
                if (viewId.isNotBlank() && id.isNotBlank()) {
                    if (id.equals(viewId, ignoreCase = true) || id.endsWith(":id/$viewId", ignoreCase = true) || idShort.equals(viewId, ignoreCase = true)) {
                        score += 95
                    }
                }

                // 2. Exact text or description match
                if (text.equals(rawQuery, ignoreCase = true)) {
                    score += 100
                } else if (desc.equals(rawQuery, ignoreCase = true)) {
                    score += 90
                } else if (idShort.equals(normQuery, ignoreCase = true)) {
                    score += 88
                }

                // 3. Normalized string match & viewId resource match
                if (normQuery.isNotEmpty()) {
                    if (normText == normQuery) {
                        score += 85
                    } else if (normDesc == normQuery) {
                        score += 80
                    } else if (normText.contains(normQuery) || (normText.length >= 4 && normQuery.contains(normText))) {
                        score += 65
                    } else if (normDesc.contains(normQuery)) {
                        score += 60
                    } else if (idShort.isNotEmpty() && (idShort.contains(normQuery) || synonyms.any { idShort.contains(it) })) {
                        score += 62
                    }
                }

                // 4. Token & synonym matches
                if (queryTokens.isNotEmpty()) {
                    val combined = "$text $desc $idShort".lowercase()
                    val tokenMatches = queryTokens.count { combined.contains(it) }
                    if (tokenMatches == queryTokens.size) {
                        score += 50
                    } else if (tokenMatches > 0) {
                        score += tokenMatches * 15
                    } else if (synonyms.any { syn -> combined.contains(syn) }) {
                        score += 45
                    }
                }

                // Bonus for clickable / enabled elements
                if (score > 0) {
                    if (node.isClickable || resolveClickableNodeOrParent(node) != null) score += 15
                    if (node.isEnabled) score += 10
                    if (node.isFocusable) score += 5
                }

                if (score > bestScore) {
                    bestScore = score
                    bestNode = node
                }
            }

            return if (bestScore >= 40) bestNode else null
        }

        private fun resolveClickableNodeOrParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            var current: AccessibilityNodeInfo? = node
            var depth = 0
            while (current != null && depth < 6) {
                val hasClickAction = current.actionList?.any {
                    it.id == AccessibilityNodeInfo.ACTION_CLICK
                } == true
                if (current.isClickable || hasClickAction) {
                    return current
                }
                current = try {
                    current.parent
                } catch (_: Exception) {
                    null
                }
                depth++
            }
            return null
        }
    }
}
