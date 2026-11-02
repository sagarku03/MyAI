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
        private var lastObservedPackage: String = ""

        @Volatile
        private var lastObservedWindowTitle: String = ""

        @Volatile
        private var lastAccessibilityEventTimeMs: Long = 0L

        private val _isConnectedFlow = MutableStateFlow(false)
        val isConnectedFlow: StateFlow<Boolean> = _isConnectedFlow.asStateFlow()

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
                return provider.invoke()
            }
            val service = activeInstance ?: return null
            return try {
                service.rootInActiveWindow ?: service.windows
                    ?.firstOrNull { it.isActive && it.root != null }
                    ?.root
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

            return ScreenContextSnapshot(
                serviceEnabled = true,
                activePackageName = activePkg,
                windowTitle = lastObservedWindowTitle,
                elements = elements,
                errorCode = null,
                message = "Captured ${elements.size} visible screen elements from $activePkg."
            )
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
                ?: return AccessibilityOperationResult(
                    executed = false,
                    actionName = "click_accessible_element",
                    matchedElementLabel = matchedLabel,
                    activePackageName = activePkg,
                    errorCode = "ELEMENT_NOT_CLICKABLE",
                    message = "Found element '$matchedLabel' on screen, but neither it nor its parent container is clickable.",
                    spokenHinglishSummary = "Screen par '$matchedLabel' mila, lekin woh clickable button nahi hai."
                )

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

            return if (clickedOk) {
                AccessibilityOperationResult(
                    executed = true,
                    actionName = "click_accessible_element",
                    matchedElementLabel = matchedLabel,
                    activePackageName = activePkg,
                    errorCode = null,
                    message = "Successfully clicked '$matchedLabel' on screen.",
                    spokenHinglishSummary = "Maine screen par '$matchedLabel' click kar diya hai."
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

        // ==========================================
        // 2. DIRECTIONAL SCROLLING & GESTURES
        // ==========================================
        override fun scrollScreen(context: Context, direction: String): AccessibilityOperationResult {
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
            val isHorizontal = normDir in setOf("left", "right")
            val scrollForward = normDir in setOf("down", "forward", "next", "neeche", "niche", "right", "")
            val actionConstant = if (scrollForward) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            val dirLabel = normDir.ifEmpty { if (scrollForward) "down" else "up" }

            val allNodes = mutableListOf<AccessibilityNodeInfo>()
            flattenAllNodes(root, allNodes, maxNodes = 200)

            val scrollableNode = allNodes.firstOrNull { node ->
                node.isVisibleToUser && (
                    node.isScrollable ||
                        node.actionList.any { it.id == actionConstant }
                    )
            } ?: root.takeIf { it.isScrollable }

            if (scrollableNode != null) {
                val scrolledOk = try {
                    scrollableNode.performAction(actionConstant)
                } catch (e: Exception) {
                    false
                }
                if (scrolledOk) {
                    return AccessibilityOperationResult(
                        executed = true,
                        actionName = "scroll_screen",
                        matchedElementLabel = dirLabel,
                        activePackageName = root.packageName?.toString().orEmpty(),
                        errorCode = null,
                        message = "Successfully scrolled screen $dirLabel.",
                        spokenHinglishSummary = "Maine screen ko $dirLabel scroll kar diya hai."
                    )
                }
            }

            // Fallback to gesture-based scroll if direct action not supported
            return swipeScreen(context, if (scrollForward) "up" else "down")
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

            return if (performed) {
                AccessibilityOperationResult(true, actionName, "", "", null, "Global action $actionName executed.", spokenSuccess)
            } else {
                AccessibilityOperationResult(false, actionName, "", "", "GLOBAL_ACTION_FAILED", "Failed to execute global action $actionName.", "$actionName execute nahi ho paya.")
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
        override fun focusInputField(context: Context, targetLabel: String?): AccessibilityOperationResult {
            val root = obtainActiveRootNode()
                ?: return AccessibilityOperationResult(false, "focus_input", "", "", "NO_WINDOW", "No active window", "Active window nahi mila.")

            val allNodes = mutableListOf<AccessibilityNodeInfo>()
            flattenAllNodes(root, allNodes, maxNodes = 150)

            val target = if (!targetLabel.isNullOrBlank()) {
                findBestMatchingNode(allNodes, targetLabel, targetLabel, "")
            } else {
                allNodes.firstOrNull { it.isEditable || it.isFocusable }
            }

            if (target == null) {
                return AccessibilityOperationResult(false, "focus_input", "", "", "FIELD_NOT_FOUND", "No editable field found.", "Screen par koi input field nahi mila.")
            }

            val focused = target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            return if (focused) {
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

            // Find currently focused editable node or any visible editable node
            val editableNode = allNodes.firstOrNull { it.isFocused && (it.isEditable || it.className?.toString()?.contains("Edit", true) == true) }
                ?: allNodes.firstOrNull { it.isEditable }
                ?: allNodes.firstOrNull { it.className?.toString()?.contains("EditText", true) == true }

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
            val textToSet = if (replaceExisting || existingText.isBlank()) text else "$existingText $text"

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
            val rawQuery = targetText.ifEmpty { contentDescription.ifEmpty { viewId } }.trim()
            if (rawQuery.isEmpty()) return null

            val normQuery = rawQuery.lowercase().replace(Regex("[^a-z0-9\\u0900-\\u097F]"), "")
            val queryTokens = rawQuery.lowercase().split(" ").filter { it.length >= 2 }

            var bestNode: AccessibilityNodeInfo? = null
            var bestScore = 0

            for (node in nodes) {
                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val id = node.viewIdResourceName?.trim().orEmpty()
                val normText = text.lowercase().replace(Regex("[^a-z0-9\\u0900-\\u097F]"), "")
                val normDesc = desc.lowercase().replace(Regex("[^a-z0-9\\u0900-\\u097F]"), "")

                var score = 0

                // 1. Exact viewId match
                if (viewId.isNotBlank() && id.isNotBlank()) {
                    if (id.equals(viewId, ignoreCase = true) || id.endsWith(":id/$viewId", ignoreCase = true) || id.substringAfterLast('/').equals(viewId, ignoreCase = true)) {
                        score += 95
                    }
                }

                // 2. Exact text or description match
                if (text.equals(rawQuery, ignoreCase = true)) {
                    score += 100
                } else if (desc.equals(rawQuery, ignoreCase = true)) {
                    score += 90
                }

                // 3. Normalized string match
                if (normQuery.isNotEmpty()) {
                    if (normText == normQuery) {
                        score += 85
                    } else if (normDesc == normQuery) {
                        score += 80
                    } else if (normText.contains(normQuery) || (normText.length >= 4 && normQuery.contains(normText))) {
                        score += 65
                    } else if (normDesc.contains(normQuery)) {
                        score += 60
                    }
                }

                // 4. Token matches
                if (queryTokens.isNotEmpty()) {
                    val combined = "$text $desc $id".lowercase()
                    val tokenMatches = queryTokens.count { combined.contains(it) }
                    if (tokenMatches == queryTokens.size) {
                        score += 50
                    } else if (tokenMatches > 0) {
                        score += tokenMatches * 15
                    }
                }

                // Bonus for clickable / enabled elements
                if (score > 0) {
                    if (node.isClickable) score += 15
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
