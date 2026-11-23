package com.jarvisai.assistant.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.UUID

enum class BrowserJsDialogType {
    ALERT,
    CONFIRM,
    PROMPT,
    BEFORE_UNLOAD
}

data class BrowserJsDialogRequest(
    val id: String = UUID.randomUUID().toString(),
    val type: BrowserJsDialogType,
    val url: String,
    val message: String,
    val defaultPromptValue: String? = null,
    val onConfirm: (String?) -> Unit,
    val onCancel: () -> Unit
)

/**
 * Immutable UI snapshot of a live browser tab owned by [BrowserSessionManager].
 * Observed reactively by `BrowserScreen` so agent actions and user interactions
 * always share the exact same live tabs and `WebView` instances.
 */
data class BrowserTabSnapshot(
    val id: String,
    val url: String,
    val title: String,
    val isHomePage: Boolean = false,
    val isPrivate: Boolean = false,
    val isExtensionPage: Boolean = false,
    val extensionId: String? = null,
    val desktopMode: Boolean = false,
    val groupId: String? = null,
    val blockedCount: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isAgentControlled: Boolean = false,
    val thumbnail: Bitmap? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

data class BrowserOpenRequest(
    val url: String,
    val tabId: String,
    val switchToBrowser: Boolean = true,
    val fromAgent: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

data class AgentBrowserBanner(
    val tabId: String,
    val url: String,
    val title: String,
    val summary: String,
    val isWorking: Boolean,
    val updatedAtMs: Long = System.currentTimeMillis()
)

data class FullscreenVideoState(
    val tabId: String,
    val customView: View,
    val callback: WebChromeClient.CustomViewCallback
)

/**
 * Optional UI callbacks attached by the visible `BrowserScreen` to receive
 * page lifecycle, ad-block, extension interception, download, and progress events
 * from the shared `WebView` instances owned by [BrowserSessionManager].
 */
interface BrowserHostUiCallbacks {
    fun shouldInterceptRequest(tabId: String, view: WebView?, request: WebResourceRequest?): WebResourceResponse? = null
    fun shouldOverrideUrlLoading(tabId: String, view: WebView?, request: WebResourceRequest?): Boolean = false
    fun onPageStarted(tabId: String, view: WebView?, url: String?) {}
    fun onPageFinished(tabId: String, view: WebView?, url: String?) {}
    fun onProgressChanged(tabId: String, newProgress: Int) {}
    fun onReceivedTitle(tabId: String, title: String) {}
    fun onConsoleMessage(tabId: String, message: ConsoleMessage) {}
    fun onDownloadRequested(tabId: String, url: String, contentDisposition: String?, mimeType: String?) {}
    fun onFindMatches(tabId: String, activeOrdinal: Int, totalMatches: Int) {}
    fun attachAdditionalJsInterfaces(tabId: String, webView: WebView) {}
}

class BrowserTab(
    val id: String,
    var url: String,
    var title: String,
    var webView: WebView? = null,
    val consoleLogs: MutableList<String> = mutableListOf(),
    @Volatile var pageLoadedDeferred: CompletableDeferred<Boolean> = CompletableDeferred(),
    var desktopMode: Boolean = false,
    var isHomePage: Boolean = false,
    var isPrivate: Boolean = false,
    var isExtensionPage: Boolean = false,
    var extensionId: String? = null,
    var groupId: String? = null,
    var blockedCount: Int = 0,
    var canGoBack: Boolean = false,
    var canGoForward: Boolean = false,
    var isAgentControlled: Boolean = false,
    var thumbnail: Bitmap? = null,
    var updatedAt: Long = System.currentTimeMillis(),
    @Volatile var jsInterfacesAttached: Boolean = false
) {
    var aiBusy: Boolean by mutableStateOf(false)
    var aiLocked: Boolean by mutableStateOf(false)
    var activeJsDialog: BrowserJsDialogRequest? by mutableStateOf(null)

    fun resetPageLoadedDeferred(): CompletableDeferred<Boolean> {
        val next = CompletableDeferred<Boolean>()
        pageLoadedDeferred = next
        return next
    }

    fun recordConsoleLog(entry: String) {
        synchronized(consoleLogs) {
            consoleLogs.add(entry)
            if (consoleLogs.size > 300) {
                consoleLogs.removeAt(0)
            }
        }
    }

    fun snapshotConsoleLogs(): List<String> = synchronized(consoleLogs) { consoleLogs.toList() }

    fun toSnapshot(): BrowserTabSnapshot = BrowserTabSnapshot(
        id = id,
        url = url,
        title = title,
        isHomePage = isHomePage,
        isPrivate = isPrivate,
        isExtensionPage = isExtensionPage,
        extensionId = extensionId,
        desktopMode = desktopMode,
        groupId = groupId,
        blockedCount = blockedCount,
        canGoBack = canGoBack,
        canGoForward = canGoForward,
        isAgentControlled = isAgentControlled,
        thumbnail = thumbnail,
        updatedAt = updatedAt
    )
}

class BrowserSessionManager private constructor() {

    companion object {
        private const val TAG = "BrowserSessionManager"
        val instance: BrowserSessionManager by lazy { BrowserSessionManager() }

        fun normalizeUrl(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return "https://www.google.com"
            val lower = trimmed.lowercase()
            return when {
                lower.startsWith("http://") ||
                    lower.startsWith("https://") ||
                    lower.startsWith("file://") ||
                    lower.startsWith("about:") ||
                    lower.startsWith("javascript:") ||
                    lower.startsWith("data:") -> trimmed
                lower.startsWith("localhost") ||
                    Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$").matches(trimmed) -> "http://$trimmed"
                !trimmed.contains(" ") && trimmed.contains(".") -> "https://$trimmed"
                else -> "https://www.google.com/search?q=" + Uri.encode(trimmed)
            }
        }
    }

    var appContext: Context? = null
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tabsLock = Any()
    private val tabs = LinkedHashMap<String, BrowserTab>()
    @Volatile
    private var activeTabId: String? = null

    @Volatile
    private var hostUiCallbacks: BrowserHostUiCallbacks? = null

    private val _tabsState = MutableStateFlow<List<BrowserTabSnapshot>>(emptyList())
    val tabsState: StateFlow<List<BrowserTabSnapshot>> = _tabsState.asStateFlow()

    private val _activeTabIdState = MutableStateFlow<String?>(null)
    val activeTabIdState: StateFlow<String?> = _activeTabIdState.asStateFlow()

    private val _isAgentActive = MutableStateFlow(false)
    val isAgentActive: StateFlow<Boolean> = _isAgentActive.asStateFlow()

    private val _agentActionSummary = MutableStateFlow("Agentra automating page...")
    val agentActionSummary: StateFlow<String> = _agentActionSummary.asStateFlow()

    private val _agentCursorPosition = MutableStateFlow<Pair<Float, Float>?>(null)
    val agentCursorPosition: StateFlow<Pair<Float, Float>?> = _agentCursorPosition.asStateFlow()

    private val _isTouchLocked = MutableStateFlow(false)
    val isTouchLocked: StateFlow<Boolean> = _isTouchLocked.asStateFlow()

    private val _agentBrowserBanner = MutableStateFlow<AgentBrowserBanner?>(null)
    val agentBrowserBanner: StateFlow<AgentBrowserBanner?> = _agentBrowserBanner.asStateFlow()

    private val _openInAppBrowserRequests = MutableSharedFlow<BrowserOpenRequest>(extraBufferCapacity = 8)
    val openInAppBrowserRequests: SharedFlow<BrowserOpenRequest> = _openInAppBrowserRequests.asSharedFlow()

    private val _fullscreenVideoState = MutableStateFlow<FullscreenVideoState?>(null)
    val fullscreenVideoState: StateFlow<FullscreenVideoState?> = _fullscreenVideoState.asStateFlow()

    private var idleStatusJob: Job? = null

    @Volatile
    private var webDebuggingConfigured = false

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        ensureWebDebuggingConfigured()
    }

    fun attachHostUiCallbacks(callbacks: BrowserHostUiCallbacks?) {
        hostUiCallbacks = callbacks
        if (callbacks != null) {
            val snapshotTabs = synchronized(tabsLock) { tabs.values.toList() }
            mainHandler.post {
                snapshotTabs.forEach { tab ->
                    val wv = tab.webView
                    if (wv != null && !tab.jsInterfacesAttached) {
                        runCatching {
                            callbacks.attachAdditionalJsInterfaces(tab.id, wv)
                            tab.jsInterfacesAttached = true
                        }
                    }
                }
            }
        }
    }

    fun dismissAgentBanner() {
        _agentBrowserBanner.value = null
    }

    fun exitFullscreenVideo() {
        val current = _fullscreenVideoState.value ?: return
        _fullscreenVideoState.value = null
        val detachAction: () -> Unit = {
            runCatching { (current.customView.parent as? ViewGroup)?.removeView(current.customView) }
            runCatching { current.callback.onCustomViewHidden() }
            getTab(current.tabId)?.webView?.let { wv ->
                syncVideoLayoutAfterResize(wv, getTab(current.tabId)?.url)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            detachAction()
        } else {
            mainHandler.post(detachAction)
        }
    }

    /**
     * Restores all cached WebViews' MutableContextWrapper back to applicationContext when
     * BrowserScreen leaves composition so no Activity instance is ever leaked.
     */
    fun resetWebViewsToAppContext() {
        val ctx = appContext ?: return
        val snapshotTabs = synchronized(tabsLock) { tabs.values.toList() }
        val resetAction = {
            snapshotTabs.forEach { tab ->
                val wrapper = tab.webView?.context as? MutableContextWrapper
                if (wrapper != null && wrapper.baseContext !== ctx) {
                    runCatching { wrapper.baseContext = ctx }
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            resetAction()
        } else {
            mainHandler.post(resetAction)
        }
    }

    private fun emitTabsState() {
        val list: List<BrowserTabSnapshot>
        val currentActive: String?
        synchronized(tabsLock) {
            if (activeTabId == null || !tabs.containsKey(activeTabId)) {
                activeTabId = tabs.keys.lastOrNull()
            }
            list = tabs.values.map { it.toSnapshot() }
            currentActive = activeTabId
        }
        _tabsState.value = list
        _activeTabIdState.value = currentActive
    }

    private fun ensureWebDebuggingConfigured() {
        if (webDebuggingConfigured) return
        webDebuggingConfigured = true
        mainHandler.post {
            runCatching { WebView.setWebContentsDebuggingEnabled(true) }
        }
    }

    fun updateAgentStatus(
        active: Boolean,
        summary: String = "Agentra automating page...",
        cursor: Pair<Float, Float>? = null
    ) {
        idleStatusJob?.cancel()
        _isAgentActive.value = active
        if (summary.isNotBlank()) {
            _agentActionSummary.value = summary
        }
        if (cursor != null || !active) {
            _agentCursorPosition.value = cursor
        }
        val activeTab = getActiveTab()
        if (active && activeTab != null && !activeTab.isHomePage) {
            _agentBrowserBanner.value = AgentBrowserBanner(
                tabId = activeTab.id,
                url = activeTab.url,
                title = activeTab.title.ifBlank { extractHostLabel(activeTab.url) },
                summary = summary.ifBlank { "Agentra browsing page..." },
                isWorking = true
            )
        } else if (!active) {
            _agentBrowserBanner.value?.let { current ->
                _agentBrowserBanner.value = current.copy(isWorking = false)
            }
        }
    }

    /**
     * Schedules agent active indicator clear after [delayMs] unless superseded by another tool call.
     */
    fun scheduleAgentIdleClear(delayMs: Long = 2200L) {
        idleStatusJob?.cancel()
        idleStatusJob = managerScope.launch {
            delay(delayMs)
            updateAgentStatus(active = false)
        }
    }

    fun setTouchLocked(locked: Boolean) {
        _isTouchLocked.value = locked
    }

    /**
     * Ensures at least one tab exists (used by `BrowserScreen` on cold open when no agent tabs exist yet).
     */
    fun ensureInitialTab(initialUrl: String = "https://www.google.com"): BrowserTab {
        val trimmed = initialUrl.trim()
        val isDefaultHome = trimmed.isBlank() || trimmed == "https://www.google.com"

        synchronized(tabsLock) {
            if (tabs.isNotEmpty()) {
                if (!isDefaultHome) {
                    val normalized = normalizeUrl(trimmed)
                    val matching = tabs.values.firstOrNull { !it.isHomePage && it.url == normalized }
                    if (matching != null) {
                        activeTabId = matching.id
                        emitTabsState()
                        return matching
                    }
                } else {
                    // Prefer showing any active real tab (e.g., opened by the AI agent!) over resetting to Start Page
                    val existing = activeTabId?.let { tabs[it] } ?: tabs.values.last()
                    activeTabId = existing.id
                    emitTabsState()
                    return existing
                }
            }
        }

        return if (isDefaultHome) {
            createHomeTab(isPrivate = false, groupId = null)
        } else {
            openUrlFromUi(trimmed, switchToBrowser = false)
        }
    }

    fun createHomeTab(isPrivate: Boolean = false, groupId: String? = null): BrowserTab {
        val tabId = UUID.randomUUID().toString().substring(0, 8)
        val tab = BrowserTab(
            id = tabId,
            url = "https://www.google.com",
            title = "Start Page",
            isHomePage = true,
            isPrivate = isPrivate,
            groupId = groupId,
            desktopMode = BrowserDataStore.preferences.value.desktopModeDefault
        )
        synchronized(tabsLock) {
            tabs[tabId] = tab
            activeTabId = tabId
        }
        emitTabsState()
        return tab
    }

    fun createExtensionTab(
        url: String,
        title: String,
        extensionId: String?,
        makeActive: Boolean = true
    ): BrowserTab {
        synchronized(tabsLock) {
            val existing = tabs.values.firstOrNull {
                it.isExtensionPage && extensionId != null && it.extensionId == extensionId
            }
            if (existing != null) {
                existing.url = url
                existing.title = title
                existing.isHomePage = false
                existing.updatedAt = System.currentTimeMillis()
                if (makeActive) activeTabId = existing.id
                emitTabsState()
                mainHandler.post { existing.webView?.loadUrl(url) }
                return existing
            }
        }

        val tabId = UUID.randomUUID().toString().substring(0, 8)
        val tab = BrowserTab(
            id = tabId,
            url = url,
            title = title,
            isHomePage = false,
            isExtensionPage = true,
            extensionId = extensionId,
            isPrivate = false
        )
        synchronized(tabsLock) {
            tabs[tabId] = tab
            if (makeActive) activeTabId = tabId
        }
        emitTabsState()
        return tab
    }

    fun restoreTab(snapshot: BrowserTabSnapshot): BrowserTab {
        val tab = BrowserTab(
            id = snapshot.id,
            url = snapshot.url,
            title = snapshot.title,
            isHomePage = snapshot.isHomePage,
            isPrivate = snapshot.isPrivate,
            isExtensionPage = snapshot.isExtensionPage,
            extensionId = snapshot.extensionId,
            desktopMode = snapshot.desktopMode,
            groupId = snapshot.groupId,
            updatedAt = System.currentTimeMillis()
        )
        synchronized(tabsLock) {
            tabs[tab.id] = tab
            activeTabId = tab.id
        }
        emitTabsState()
        return tab
    }

    /**
     * Opens a URL from UI surfaces (RunScreen preview, IDE Runner preview, AgentToolCallCard,
     * `termux-open-url`, etc.) directly inside Agentra's shared in-app browser.
     */
    fun openUrlFromUi(
        rawUrl: String,
        switchToBrowser: Boolean = true,
        newTab: Boolean = false,
        isPrivate: Boolean = false,
        groupId: String? = null
    ): BrowserTab {
        val url = normalizeUrl(rawUrl)
        val tab = openOrNavigateInternal(
            url = url,
            newTab = newTab,
            targetTabId = null,
            desktopMode = BrowserDataStore.preferences.value.desktopModeDefault,
            isPrivate = isPrivate,
            groupId = groupId,
            fromAgent = false
        )
        if (switchToBrowser) {
            _openInAppBrowserRequests.tryEmit(
                BrowserOpenRequest(
                    url = url,
                    tabId = tab.id,
                    switchToBrowser = true,
                    fromAgent = false
                )
            )
        }
        return tab
    }

    /**
     * Opens or navigates a URL on behalf of an AI agent (Browser MCP / CloudCode PA / CLI agent).
     * Reuses a blank Start Page tab or matching tab when appropriate, loads the URL in the real
     * shared `WebView`, and broadcasts a live preview banner so the user can watch or jump straight in!
     */
    fun openAgentUrl(
        rawUrl: String,
        newTab: Boolean = false,
        tabId: String? = null,
        desktopMode: Boolean = false
    ): BrowserTab {
        val url = normalizeUrl(rawUrl)
        val tab = openOrNavigateInternal(
            url = url,
            newTab = newTab,
            targetTabId = tabId?.takeIf { it.isNotBlank() },
            desktopMode = desktopMode,
            isPrivate = false,
            groupId = null,
            fromAgent = true
        )
        _agentBrowserBanner.value = AgentBrowserBanner(
            tabId = tab.id,
            url = url,
            title = tab.title.ifBlank { extractHostLabel(url) },
            summary = "Agent opened ${extractHostLabel(url)}",
            isWorking = true
        )
        _openInAppBrowserRequests.tryEmit(
            BrowserOpenRequest(
                url = url,
                tabId = tab.id,
                switchToBrowser = false,
                fromAgent = true
            )
        )
        return tab
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun createTab(url: String = "about:blank", desktopMode: Boolean = false): BrowserTab {
        return openAgentUrl(rawUrl = url, newTab = false, tabId = null, desktopMode = desktopMode)
    }

    private fun openOrNavigateInternal(
        url: String,
        newTab: Boolean,
        targetTabId: String?,
        desktopMode: Boolean,
        isPrivate: Boolean,
        groupId: String?,
        fromAgent: Boolean
    ): BrowserTab {
        val context = appContext ?: throw IllegalStateException("BrowserSessionManager not initialized")
        ensureWebDebuggingConfigured()

        var tabToUse: BrowserTab? = null
        synchronized(tabsLock) {
            if (!targetTabId.isNullOrBlank()) {
                tabToUse = tabs[targetTabId]
            }
            if (tabToUse == null && !newTab) {
                val active = activeTabId?.let { tabs[it] }
                // Reuse blank Start Page tab or existing same-URL tab so we don't spam empty tabs
                if (active != null && active.isHomePage && !active.isPrivate && !isPrivate) {
                    tabToUse = active
                } else {
                    tabToUse = tabs.values.firstOrNull {
                        !it.isHomePage && !it.isPrivate && !it.isExtensionPage && it.url == url
                    }
                }
            }
            if (tabToUse == null && !newTab && fromAgent) {
                // If agent already controls the active tab and didn't explicitly ask for a new tab,
                // reuse that active agent tab when navigating sequentially unless multiple tabs requested
                val active = activeTabId?.let { tabs[it] }
                if (active != null && active.isAgentControlled && !active.isPrivate && !active.isExtensionPage) {
                    tabToUse = active
                }
            }

            if (tabToUse == null) {
                val id = UUID.randomUUID().toString().substring(0, 8)
                tabToUse = BrowserTab(
                    id = id,
                    url = url,
                    title = extractHostLabel(url).ifBlank { "Loading..." },
                    desktopMode = desktopMode,
                    isHomePage = false,
                    isPrivate = isPrivate,
                    groupId = groupId,
                    isAgentControlled = fromAgent,
                    updatedAt = System.currentTimeMillis()
                )
                tabs[id] = tabToUse!!
            } else {
                val existing = tabToUse!!
                existing.url = url
                existing.isHomePage = false
                existing.isExtensionPage = false
                existing.extensionId = null
                existing.desktopMode = desktopMode || existing.desktopMode
                if (fromAgent) existing.isAgentControlled = true
                existing.title = extractHostLabel(url).ifBlank { existing.title }
                existing.updatedAt = System.currentTimeMillis()
            }
            activeTabId = tabToUse!!.id
        }

        val tab = tabToUse!!
        tab.resetPageLoadedDeferred()
        emitTabsState()

        val loadOnMain: () -> Unit = {
            val wv = getOrCreateWebViewLockedOnMain(tab, context)
            wv.loadUrl(url)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            loadOnMain()
        } else {
            mainHandler.post(loadOnMain)
        }
        return tab
    }

    /**
     * Navigates an existing tab to [rawUrl] (used by BrowserScreen omnibox / speed dials / links).
     */
    fun navigateTab(tabId: String, rawUrl: String) {
        val url = normalizeUrl(rawUrl)
        val tab = synchronized(tabsLock) { tabs[tabId] } ?: return
        tab.url = url
        tab.isHomePage = false
        tab.updatedAt = System.currentTimeMillis()
        tab.resetPageLoadedDeferred()
        emitTabsState()

        val ctx = appContext
        val runNav: () -> Unit = {
            val wv = tab.webView ?: ctx?.let { getOrCreateWebViewLockedOnMain(tab, it) }
            wv?.loadUrl(url)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runNav()
        } else {
            mainHandler.post(runNav)
        }
    }

    fun updateTabMeta(tabId: String, transform: (BrowserTab) -> Unit) {
        val changed = synchronized(tabsLock) {
            val t = tabs[tabId] ?: return@synchronized false
            transform(t)
            true
        }
        if (changed) emitTabsState()
    }

    /**
     * Returns the live [WebView] for [tabId] on the Main thread, creating and configuring it if needed,
     * and detaching it from any previous parent ViewGroup so Compose `AndroidView` can safely mount it.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun obtainWebViewForUi(tabId: String, context: Context): WebView? {
        if (appContext == null) appContext = context.applicationContext
        val tab = synchronized(tabsLock) { tabs[tabId] } ?: return null
        val wv = getOrCreateWebViewLockedOnMain(tab, context)
        // Bind live Activity context to MutableContextWrapper so Chromium video surfaces,
        // HTML5 fullscreen custom views, and native popups measure & render accurately.
        (wv.context as? MutableContextWrapper)?.let { wrapper ->
            if (wrapper.baseContext !== context) {
                runCatching { wrapper.baseContext = context }
            }
        }
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        wv.setInitialScale(0)
        wv.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        wv.requestLayout()
        wv.post {
            syncVideoLayoutAfterResize(wv, tab.url)
        }
        // Ensure UI-specific JS interfaces (like Chrome Extension bridge) are attached
        val callbacks = hostUiCallbacks
        if (callbacks != null && !tab.jsInterfacesAttached) {
            runCatching {
                callbacks.attachAdditionalJsInterfaces(tab.id, wv)
                tab.jsInterfacesAttached = true
            }
        }
        return wv
    }

    private fun syncVideoLayoutAfterResize(webView: WebView?, url: String?) {
        val wv = webView ?: return
        val js = """
            (function(){
              try {
                var host = (window.location && window.location.hostname || '').toLowerCase();
                var path = (window.location && window.location.pathname || '').toLowerCase();
                if (host.indexOf('youtube.com') !== -1 && path.indexOf('/shorts') === -1) {
                  var styleId = 'agentra-yt-video-fit';
                  if (!document.getElementById(styleId)) {
                    var st = document.createElement('style');
                    st.id = styleId;
                    st.textContent = [
                      '#player-container-id, #movie_player, .html5-video-player, .html5-video-container { width: 100% !important; max-width: 100% !important; }',
                      '#player-container-id video.html5-main-video, #movie_player video.html5-main-video, .html5-video-player:not(.ytp-shorts-mode) video.html5-main-video, ytm-player video { width: 100% !important; height: 100% !important; left: 0px !important; top: 0px !important; object-fit: contain !important; }'
                    ].join('\n');
                    (document.head || document.documentElement).appendChild(st);
                  }
                }
                window.dispatchEvent(new Event('resize'));
              } catch(e) {}
            })();
        """.trimIndent()
        runCatching { wv.evaluateJavascript(js, null) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun getOrCreateWebViewLockedOnMain(tab: BrowserTab, context: Context): WebView {
        tab.webView?.let { existing ->
            val callbacks = hostUiCallbacks
            if (callbacks != null && !tab.jsInterfacesAttached) {
                runCatching {
                    callbacks.attachAdditionalJsInterfaces(tab.id, existing)
                    tab.jsInterfacesAttached = true
                }
            }
            return existing
        }

        ensureWebDebuggingConfigured()
        val prefs = BrowserDataStore.preferences.value
        val tabId = tab.id
        val wrappedContext = MutableContextWrapper(context)

        val wv = WebView(wrappedContext).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.apply {
                javaScriptEnabled = prefs.javaScriptEnabled || tab.isExtensionPage || tab.isAgentControlled
                domStorageEnabled = !tab.isPrivate || tab.isExtensionPage
                databaseEnabled = !tab.isPrivate || tab.isExtensionPage
                allowFileAccess = true
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort = true
                loadWithOverviewMode = true
                layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                textZoom = 100
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                javaScriptCanOpenWindowsAutomatically = true
                cacheMode = if (tab.isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                BrowserIdentity.applyTo(this, context, desktop = tab.desktopMode)
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(
                        this,
                        prefs.forceDarkPages && !tab.isExtensionPage
                    )
                }
            }

            runCatching {
                CookieManager.getInstance().setAcceptCookie(!tab.isPrivate)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, !tab.isPrivate)
            }

            // Only pre-measure when constructed offscreen (without an Activity UI host) so
            // on-screen Compose AndroidView sizing and YouTube player aspect ratios stay exact.
            if (context === context.applicationContext) {
                val dm = context.resources.displayMetrics
                val initW = dm.widthPixels.coerceAtLeast(720)
                val initH = (dm.heightPixels * 0.85f).toInt().coerceAtLeast(1200)
                val widthSpec = View.MeasureSpec.makeMeasureSpec(initW, View.MeasureSpec.EXACTLY)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(initH, View.MeasureSpec.EXACTLY)
                measure(widthSpec, heightSpec)
                layout(0, 0, initW, initH)
            }

            setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                hostUiCallbacks?.onFindMatches(tabId, activeMatchOrdinal, numberOfMatches)
            }

            setDownloadListener(DownloadListener { dlUrl, _, contentDisposition, mimeType, _ ->
                hostUiCallbacks?.onDownloadRequested(tabId, dlUrl, contentDisposition, mimeType)
            })

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val intercepted = hostUiCallbacks?.shouldInterceptRequest(tabId, view, request)
                    if (intercepted != null) return intercepted
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    val currentTab = getTab(tabId)
                    if (currentTab != null && !url.isNullOrBlank()) {
                        currentTab.url = url
                        currentTab.isHomePage = false
                        currentTab.canGoBack = view?.canGoBack() == true
                        currentTab.canGoForward = view?.canGoForward() == true
                        emitTabsState()
                    }
                    hostUiCallbacks?.onPageStarted(tabId, view, url)
                }

                override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                    super.onPageFinished(view, loadedUrl)
                    val currentTab = getTab(tabId)
                    if (currentTab != null) {
                        if (!loadedUrl.isNullOrBlank()) {
                            currentTab.url = loadedUrl
                        }
                        val pageTitle = view?.title?.takeIf { it.isNotBlank() }
                        if (!currentTab.isExtensionPage && pageTitle != null) {
                            currentTab.title = pageTitle
                        }
                        currentTab.canGoBack = view?.canGoBack() == true
                        currentTab.canGoForward = view?.canGoForward() == true
                        currentTab.updatedAt = System.currentTimeMillis()
                        currentTab.pageLoadedDeferred.complete(true)
                        emitTabsState()

                        // Refresh live agent banner with resolved title/URL
                        val currentBanner = _agentBrowserBanner.value
                        if (currentBanner != null && currentBanner.tabId == tabId) {
                            _agentBrowserBanner.value = currentBanner.copy(
                                url = currentTab.url,
                                title = currentTab.title.ifBlank { extractHostLabel(currentTab.url) }
                            )
                        }
                        BrowserAutomationController.onPageFinished(currentTab, loadedUrl)
                        syncVideoLayoutAfterResize(view, loadedUrl)
                    }
                    hostUiCallbacks?.onPageFinished(tabId, view, loadedUrl)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val handled = hostUiCallbacks?.shouldOverrideUrlLoading(tabId, view, request)
                    if (handled == true) return true
                    return super.shouldOverrideUrlLoading(view, request)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun getDefaultVideoPoster(): Bitmap {
                    return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
                        eraseColor(Color.BLACK)
                    }
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view == null || callback == null) return
                    val previous = _fullscreenVideoState.value
                    if (previous != null) {
                        runCatching { (previous.customView.parent as? ViewGroup)?.removeView(previous.customView) }
                        runCatching { previous.callback.onCustomViewHidden() }
                    }
                    _fullscreenVideoState.value = FullscreenVideoState(
                        tabId = tabId,
                        customView = view,
                        callback = callback
                    )
                }

                override fun onHideCustomView() {
                    val current = _fullscreenVideoState.value
                    _fullscreenVideoState.value = null
                    if (current != null) {
                        runCatching { (current.customView.parent as? ViewGroup)?.removeView(current.customView) }
                        runCatching { current.callback.onCustomViewHidden() }
                    }
                    getTab(tabId)?.webView?.let { wv ->
                        wv.post { syncVideoLayoutAfterResize(wv, getTab(tabId)?.url) }
                    }
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    hostUiCallbacks?.onProgressChanged(tabId, newProgress)
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank()) {
                        val currentTab = getTab(tabId)
                        if (currentTab != null && !currentTab.isExtensionPage) {
                            currentTab.title = title
                            emitTabsState()
                        }
                        hostUiCallbacks?.onReceivedTitle(tabId, title)
                    }
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        getTab(tabId)?.recordConsoleLog(
                            "[${it.messageLevel()}] ${it.message()} (${it.sourceId()}:${it.lineNumber()})"
                        )
                        hostUiCallbacks?.onConsoleMessage(tabId, it)
                    }
                    return true
                }

                override fun onJsAlert(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    val currentTab = getTab(tabId) ?: return false
                    if (result == null) return false
                    currentTab.activeJsDialog = BrowserJsDialogRequest(
                        type = BrowserJsDialogType.ALERT,
                        url = url.orEmpty(),
                        message = message.orEmpty(),
                        onConfirm = { result.confirm() },
                        onCancel = { result.cancel() }
                    )
                    return true
                }

                override fun onJsConfirm(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    val currentTab = getTab(tabId) ?: return false
                    if (result == null) return false
                    currentTab.activeJsDialog = BrowserJsDialogRequest(
                        type = BrowserJsDialogType.CONFIRM,
                        url = url.orEmpty(),
                        message = message.orEmpty(),
                        onConfirm = { result.confirm() },
                        onCancel = { result.cancel() }
                    )
                    return true
                }

                override fun onJsPrompt(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    defaultValue: String?,
                    result: JsPromptResult?
                ): Boolean {
                    val currentTab = getTab(tabId) ?: return false
                    if (result == null) return false
                    currentTab.activeJsDialog = BrowserJsDialogRequest(
                        type = BrowserJsDialogType.PROMPT,
                        url = url.orEmpty(),
                        message = message.orEmpty(),
                        defaultPromptValue = defaultValue,
                        onConfirm = { input -> result.confirm(input ?: defaultValue.orEmpty()) },
                        onCancel = { result.cancel() }
                    )
                    return true
                }

                override fun onJsBeforeUnload(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    val currentTab = getTab(tabId) ?: return false
                    if (result == null) return false
                    currentTab.activeJsDialog = BrowserJsDialogRequest(
                        type = BrowserJsDialogType.BEFORE_UNLOAD,
                        url = url.orEmpty(),
                        message = message.orEmpty(),
                        onConfirm = { result.confirm() },
                        onCancel = { result.cancel() }
                    )
                    return true
                }
            }

            val callbacks = hostUiCallbacks
            if (callbacks != null) {
                runCatching {
                    callbacks.attachAdditionalJsInterfaces(tabId, this)
                    tab.jsInterfacesAttached = true
                }
            }
        }

        tab.webView = wv
        return wv
    }

    suspend fun awaitTabReady(tabId: String, timeoutMs: Long = 8000L) {
        val tab = getTab(tabId) ?: return
        withTimeoutOrNull(timeoutMs) {
            while (tab.webView == null) {
                delay(40)
            }
            tab.pageLoadedDeferred.await()
        }
    }

    fun listTabs(): List<BrowserTab> = synchronized(tabsLock) { tabs.values.toList() }

    fun getTab(tabId: String): BrowserTab? = synchronized(tabsLock) { tabs[tabId] }

    fun getActiveTab(): BrowserTab? = synchronized(tabsLock) {
        val id = activeTabId
        if (id != null && tabs.containsKey(id)) {
            tabs[id]
        } else {
            tabs.values.lastOrNull()
        }
    }

    fun getActiveNonHomeTab(): BrowserTab? = synchronized(tabsLock) {
        val active = activeTabId?.let { tabs[it] }
        if (active != null && !active.isHomePage && active.webView != null) {
            active
        } else {
            tabs.values.lastOrNull { !it.isHomePage && it.webView != null } ?: active
        }
    }

    fun bindLiveWebViewTab(tabId: String, url: String, title: String, webView: WebView?, desktopMode: Boolean) {
        synchronized(tabsLock) {
            val tab = tabs.getOrPut(tabId) {
                BrowserTab(
                    id = tabId,
                    url = url,
                    title = title,
                    webView = webView,
                    desktopMode = desktopMode
                )
            }
            tab.url = url
            tab.title = title
            tab.desktopMode = desktopMode
            if (webView != null) {
                tab.webView = webView
            }
            activeTabId = tabId
        }
        emitTabsState()
    }

    fun unbindLiveTab(tabId: String) {
        synchronized(tabsLock) {
            tabs.remove(tabId)
            if (activeTabId == tabId) {
                activeTabId = tabs.keys.lastOrNull()
            }
        }
        emitTabsState()
    }

    fun switchTab(tabId: String): Boolean {
        if (_fullscreenVideoState.value != null && _fullscreenVideoState.value?.tabId != tabId) {
            exitFullscreenVideo()
        }
        val target: BrowserTab?
        synchronized(tabsLock) {
            target = tabs[tabId]
            if (target != null) {
                activeTabId = tabId
            }
        }
        if (target != null) {
            _isTouchLocked.value = target.aiLocked
            emitTabsState()
            return true
        }
        return false
    }

    fun setDesktopMode(tabId: String, desktop: Boolean) {
        val context = appContext ?: return
        val tab = (if (tabId.isNotBlank()) getTab(tabId) else null) ?: getActiveTab() ?: return
        tab.desktopMode = desktop
        emitTabsState()
        mainHandler.post {
            tab.webView?.let { wv ->
                BrowserIdentity.applyTo(wv.settings, context, desktop)
                wv.reload()
            }
        }
    }

    fun closeTab(tabId: String) {
        if (_fullscreenVideoState.value?.tabId == tabId) {
            exitFullscreenVideo()
        }
        BrowserAutomationController.unlockAll(tabId)
        val removed = synchronized(tabsLock) {
            val t = tabs.remove(tabId)
            if (activeTabId == tabId) {
                activeTabId = tabs.keys.lastOrNull()
            }
            t
        }
        if (_agentBrowserBanner.value?.tabId == tabId) {
            _agentBrowserBanner.value = null
        }
        mainHandler.post {
            runCatching {
                removed?.webView?.let { wv ->
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.stopLoading()
                    wv.destroy()
                }
            }
        }
        emitTabsState()
    }

    suspend fun evalJs(tabId: String, script: String): Result<String> = withContext(Dispatchers.Main) {
        val tab = (if (tabId.isNotBlank()) getTab(tabId) else null) ?: getActiveNonHomeTab()
            ?: return@withContext Result.failure(IllegalArgumentException("Tab not found"))
        val wv = tab.webView ?: return@withContext Result.failure(IllegalStateException("WebView not initialized"))

        val deferred = CompletableDeferred<String>()
        wv.evaluateJavascript(script) { result ->
            deferred.complete(result ?: "null")
        }
        val res = withTimeoutOrNull(8000L) { deferred.await() } ?: "null"
        Result.success(res)
    }

    suspend fun inspectDom(tabId: String): Result<String> = withContext(Dispatchers.Main) {
        val script = """
            (function() {
                function serialize(el, depth) {
                    if (depth > 6 || !el) return null;
                    var tag = el.tagName ? el.tagName.toLowerCase() : '';
                    if (!tag || tag === 'script' || tag === 'style') return null;
                    var text = el.innerText ? el.innerText.trim().slice(0, 100) : '';
                    var children = [];
                    for (var i = 0; i < el.children.length; i++) {
                        var child = serialize(el.children[i], depth + 1);
                        if (child) children.push(child);
                    }
                    var res = { tag: tag };
                    if (el.id) res.id = el.id;
                    if (el.className) res.class = el.className;
                    if (text && children.length === 0) res.text = text;
                    if (children.length > 0) res.children = children;
                    return res;
                }
                return JSON.stringify(serialize(document.body, 0));
            })();
        """.trimIndent()
        evalJs(tabId, script)
    }

    suspend fun captureScreenshot(tabId: String): Result<Bitmap> = withContext(Dispatchers.Main) {
        val tab = (if (tabId.isNotBlank()) getTab(tabId) else null) ?: getActiveNonHomeTab()
            ?: return@withContext Result.failure(IllegalArgumentException("Tab not found"))
        val wv = tab.webView ?: return@withContext Result.failure(IllegalStateException("WebView not initialized"))

        try {
            var width = wv.width
            var height = wv.height
            if (width <= 0 || height <= 0) {
                val dm = appContext?.resources?.displayMetrics
                width = (dm?.widthPixels ?: 1080).coerceAtLeast(720)
                height = ((dm?.heightPixels ?: 1920) * 0.85f).toInt().coerceAtLeast(1080)
                wv.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                wv.layout(0, 0, width, height)
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            wv.draw(canvas)
            Result.success(bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "captureScreenshot error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun captureScreenshotBase64(tabId: String): Result<String> {
        return captureScreenshot(tabId).mapCatching { bitmap ->
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 78, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    }

    private fun extractHostLabel(url: String): String {
        return runCatching {
            val uri = Uri.parse(url)
            val host = uri.host.orEmpty()
            val port = uri.port
            if (host.isNotBlank() && port > 0) "$host:$port" else host.ifBlank { url }
        }.getOrDefault(url)
    }
}
