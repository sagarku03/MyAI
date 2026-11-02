package com.jarvisai.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
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
}

/**
 * Official user-controlled Android [AccessibilityService] for JARVIS AI Live Agent.
 *
 * Uses official Android APIs:
 * - [AccessibilityNodeInfo]
 * - [AccessibilityNodeInfo.ACTION_CLICK]
 * - [AccessibilityNodeInfo.ACTION_FOCUS]
 * - [AccessibilityNodeInfo.ACTION_SCROLL_FORWARD]
 * - [AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD]
 * - [performGlobalAction] ([GLOBAL_ACTION_BACK], [GLOBAL_ACTION_HOME])
 * - [AccessibilityEvent]
 *
 * Never silently enables itself and never returns fake success results.
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

        override fun scrollScreen(context: Context, direction: String): AccessibilityOperationResult {
            if (!isAccessibilityEnabled(context)) {
                return AccessibilityOperationResult(
                    executed = false,
                    actionName = "scroll_screen",
                    errorCode = "ACCESSIBILITY_SERVICE_DISABLED",
                    message = "Accessibility Service is disabled. Please enable 'JARVIS AI Live Screen Agent' in Android Accessibility Settings to scroll the screen.",
                    spokenHinglishSummary = "Screen scroll karne ke liye JARVIS Accessibility permission on honi chahiye. Kripya Accessibility Settings mein ise enable karein."
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
            val scrollForward = normDir in setOf("down", "forward", "next", "neeche", "niche", "right", "")
            val actionConstant = if (scrollForward) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            val dirLabel = if (scrollForward) "down" else "up"

            val allNodes = mutableListOf<AccessibilityNodeInfo>()
            flattenAllNodes(root, allNodes, maxNodes = 200)

            val scrollableNode = allNodes.firstOrNull { node ->
                node.isVisibleToUser && (
                    node.isScrollable ||
                        node.actionList.any { it.id == actionConstant }
                    )
            } ?: root.takeIf { it.isScrollable }

            if (scrollableNode == null) {
                return AccessibilityOperationResult(
                    executed = false,
                    actionName = "scroll_screen",
                    activePackageName = root.packageName?.toString().orEmpty(),
                    errorCode = "NO_SCROLLABLE_CONTAINER",
                    message = "No scrollable list or view was found on the current screen.",
                    spokenHinglishSummary = "Current screen par koi scrollable list ya page nahi mila."
                )
            }

            val scrolledOk = try {
                scrollableNode.performAction(actionConstant)
            } catch (e: Exception) {
                Log.e(TAG, "Scroll action failed: ${e.localizedMessage}")
                false
            }

            return if (scrolledOk) {
                AccessibilityOperationResult(
                    executed = true,
                    actionName = "scroll_screen",
                    matchedElementLabel = scrollableNode.className?.toString()?.substringAfterLast('.').orEmpty(),
                    activePackageName = root.packageName?.toString().orEmpty(),
                    errorCode = null,
                    message = "Successfully scrolled screen $dirLabel.",
                    spokenHinglishSummary = "Maine screen ko $dirLabel scroll kar diya hai."
                )
            } else {
                AccessibilityOperationResult(
                    executed = false,
                    actionName = "scroll_screen",
                    activePackageName = root.packageName?.toString().orEmpty(),
                    errorCode = "SCROLL_FAILED",
                    message = "Could not scroll $dirLabel any further on the current screen.",
                    spokenHinglishSummary = "Screen $dirLabel scroll nahi ho payi (shayad page ke end par hai)."
                )
            }
        }

        override fun navigateBack(context: Context): AccessibilityOperationResult {
            if (!isAccessibilityEnabled(context)) {
                return AccessibilityOperationResult(
                    executed = false,
                    actionName = "navigate_back",
                    errorCode = "ACCESSIBILITY_SERVICE_DISABLED",
                    message = "Accessibility Service is disabled. Please enable 'JARVIS AI Live Screen Agent' in Android Accessibility Settings to perform Back navigation.",
                    spokenHinglishSummary = "Back jaane ke liye JARVIS Accessibility permission on honi chahiye."
                )
            }

            val performed = testGlobalActionHandler?.invoke(GLOBAL_ACTION_BACK)
                ?: activeInstance?.performGlobalAction(GLOBAL_ACTION_BACK)
                ?: false

            return if (performed) {
                AccessibilityOperationResult(
                    executed = true,
                    actionName = "navigate_back",
                    errorCode = null,
                    message = "Successfully executed Android global Back navigation.",
                    spokenHinglishSummary = "Maine Back button press kar diya hai."
                )
            } else {
                AccessibilityOperationResult(
                    executed = false,
                    actionName = "navigate_back",
                    errorCode = "GLOBAL_ACTION_FAILED",
                    message = "Android global Back action failed.",
                    spokenHinglishSummary = "Back navigation execute nahi ho paya."
                )
            }
        }

        override fun navigateHome(context: Context): AccessibilityOperationResult {
            if (!isAccessibilityEnabled(context)) {
                return AccessibilityOperationResult(
                    executed = false,
                    actionName = "navigate_home",
                    errorCode = "ACCESSIBILITY_SERVICE_DISABLED",
                    message = "Accessibility Service is disabled. Please enable 'JARVIS AI Live Screen Agent' in Android Accessibility Settings to navigate to the Home screen.",
                    spokenHinglishSummary = "Home screen par jaane ke liye JARVIS Accessibility permission on honi chahiye."
                )
            }

            val performed = testGlobalActionHandler?.invoke(GLOBAL_ACTION_HOME)
                ?: activeInstance?.performGlobalAction(GLOBAL_ACTION_HOME)
                ?: false

            return if (performed) {
                AccessibilityOperationResult(
                    executed = true,
                    actionName = "navigate_home",
                    errorCode = null,
                    message = "Successfully navigated to the Android Home screen.",
                    spokenHinglishSummary = "Main aapko Home screen par le aaya hoon."
                )
            } else {
                AccessibilityOperationResult(
                    executed = false,
                    actionName = "navigate_home",
                    errorCode = "GLOBAL_ACTION_FAILED",
                    message = "Android global Home action failed.",
                    spokenHinglishSummary = "Home screen navigation execute nahi ho paya."
                )
            }
        }

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
            // 1. Exact or suffix match on viewIdResourceName if provided
            if (viewId.isNotEmpty()) {
                nodes.firstOrNull { node ->
                    val id = node.viewIdResourceName.orEmpty()
                    id.equals(viewId, ignoreCase = true) ||
                        id.endsWith(":id/$viewId", ignoreCase = true) ||
                        id.substringAfterLast('/').equals(viewId, ignoreCase = true)
                }?.let { return it }
            }

            val queryCandidates = listOf(targetText, contentDescription).filter { it.isNotEmpty() }
            if (queryCandidates.isEmpty()) return null

            // 2. Exact match on text or contentDescription (prefer clickable nodes first)
            for (q in queryCandidates) {
                nodes.firstOrNull { node ->
                    node.isClickable && (
                        node.text?.toString()?.trim()?.equals(q, ignoreCase = true) == true ||
                            node.contentDescription?.toString()?.trim()?.equals(q, ignoreCase = true) == true
                        )
                }?.let { return it }

                nodes.firstOrNull { node ->
                    node.text?.toString()?.trim()?.equals(q, ignoreCase = true) == true ||
                        node.contentDescription?.toString()?.trim()?.equals(q, ignoreCase = true) == true
                }?.let { return it }
            }

            // 3. Case-insensitive contains match on text, contentDescription, or viewId
            for (q in queryCandidates) {
                val qLower = q.lowercase()
                nodes.firstOrNull { node ->
                    node.isClickable && (
                        node.text?.toString()?.lowercase()?.contains(qLower) == true ||
                            node.contentDescription?.toString()?.lowercase()?.contains(qLower) == true ||
                            node.viewIdResourceName?.lowercase()?.contains(qLower) == true
                        )
                }?.let { return it }

                nodes.firstOrNull { node ->
                    node.text?.toString()?.lowercase()?.contains(qLower) == true ||
                        node.contentDescription?.toString()?.lowercase()?.contains(qLower) == true ||
                        node.viewIdResourceName?.lowercase()?.contains(qLower) == true
                }?.let { return it }
            }

            return null
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
