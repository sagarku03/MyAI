package com.jarvisai.assistant.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
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
import java.lang.ref.WeakReference
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

data class BrowserMediaPlaybackState(
    val tabId: String = "",
    val title: String = "",
    val artist: String = "",
    val url: String = "",
    val artworkUrl: String = "",
    val isPlaying: Boolean = false,
    val durationMs: Long = -1L,
    val positionMs: Long = 0L,
    val hasActiveMedia: Boolean = false,
    val updatedAtMs: Long = System.currentTimeMillis()
)

data class FullscreenVideoState(
    val tabId: String,
    val customView: View,
    val callback: WebChromeClient.CustomViewCallback,
    val attachedToDecorView: Boolean = false,
    val overlayContainer: ViewGroup? = null,
    val hostActivityRef: WeakReference<Activity>? = null,
    val previousOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
)

/**
 * Custom WebView that keeps Chromium's media pipeline active when the app moves to the background,
 * when switching between in-app screens, or when the device screen is locked/turned off.
 */
class BackgroundPlayWebView(context: Context) : WebView(context) {
    @Volatile
    var allowBackgroundPlayback: Boolean = true

    @Volatile
    var suppressSoftKeyboard: Boolean = false

    private fun shouldKeepVisible(): Boolean =
        allowBackgroundPlayback && BrowserDataStore.preferences.value.backgroundMediaPlayback

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (suppressSoftKeyboard) return null
        return super.onCreateInputConnection(outAttrs)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (visibility != View.VISIBLE && shouldKeepVisible()) {
            super.onWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }

    override fun dispatchWindowVisibilityChanged(visibility: Int) {
        if (visibility != View.VISIBLE && shouldKeepVisible()) {
            super.dispatchWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.dispatchWindowVisibilityChanged(visibility)
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        if (visibility != View.VISIBLE && shouldKeepVisible()) {
            super.onVisibilityChanged(changedView, View.VISIBLE)
        } else {
            super.onVisibilityChanged(changedView, visibility)
        }
    }
}

/**
 * Optional UI callbacks attached by the visible `BrowserScreen` to receive
 * page lifecycle, ad-block, extension interception, download, and progress events
 * from the shared `WebView` instances owned by [BrowserSessionManager].
 */
interface BrowserHostUiCallbacks {
    fun shouldInterceptRequest(tabId: String, view: WebView?, request: WebResourceRequest?): WebResourceResponse? = null
    fun shouldOverrideUrlLoading(tabId: String, view: WebView?, request: WebResourceRequest?): Boolean = false
    fun onPageStarted(tabId: String, view: WebView?, url: String?) {}
    fun onPageCommitVisible(tabId: String, view: WebView?, url: String?) {}
    fun onPageFinished(tabId: String, view: WebView?, url: String?) {}
    fun onProgressChanged(tabId: String, newProgress: Int) {}
    fun onReceivedTitle(tabId: String, title: String) {}
    fun onConsoleMessage(tabId: String, message: ConsoleMessage) {}
    fun onDownloadRequested(tabId: String, url: String, contentDisposition: String?, mimeType: String?) {}
    fun onFindMatches(tabId: String, activeOrdinal: Int, totalMatches: Int) {}
    fun attachAdditionalJsInterfaces(tabId: String, webView: WebView) {}
    fun onShowFileChooser(
        tabId: String,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: WebChromeClient.FileChooserParams?
    ): Boolean = false
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
    var aiZoomPinned: Boolean = false
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

    @Volatile
    private var uiContextRef: WeakReference<Context>? = null

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

    private val _mediaPlaybackState = MutableStateFlow(BrowserMediaPlaybackState())
    val mediaPlaybackState: StateFlow<BrowserMediaPlaybackState> = _mediaPlaybackState.asStateFlow()

    private var idleStatusJob: Job? = null

    @Volatile
    private var webDebuggingConfigured = false

    @Volatile
    private var backgroundHostContainerRef: WeakReference<FrameLayout>? = null

    @Volatile
    private var backgroundParkedTabId: String? = null

    @Volatile
    private var lastNotificationPushMs: Long = 0L

    @Volatile
    private var lastNotifiedPlaying: Boolean? = null

    @Volatile
    private var lastNotifiedTitle: String = ""

    @Volatile
    private var lastNotifiedTabId: String = ""

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        ensureWebDebuggingConfigured()
    }

    fun attachUiContext(context: Context?) {
        uiContextRef = context?.let { WeakReference(it) }
    }

    private fun preferredContext(fallback: Context? = null): Context? {
        return uiContextRef?.get() ?: fallback ?: appContext
    }

    private fun resolveActivity(context: Context?): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun pauseTab(tabId: String) {
        val tab = getTab(tabId) ?: return
        val action = {
            runCatching {
                tab.webView?.evaluateJavascript(
                    "(function(){try{window.__jarvisUserPaused=true;document.querySelectorAll('video,audio').forEach(function(m){if(!m.paused)m.pause();});}catch(e){}})();",
                    null
                )
            }
            if (_mediaPlaybackState.value.tabId == tabId) {
                val updated = _mediaPlaybackState.value.copy(isPlaying = false, updatedAtMs = System.currentTimeMillis())
                _mediaPlaybackState.value = updated
                appContext?.let { BrowserMediaPlaybackService.update(it, updated) }
            }
            Unit
        }
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    fun pauseInactiveMedia() {
        val snapshotTabs = synchronized(tabsLock) { tabs.values.toList() }
        val action = {
            snapshotTabs.forEach { tab ->
                if (!tab.isAgentControlled) {
                    runCatching {
                        tab.webView?.evaluateJavascript(
                            "(function(){try{window.__jarvisUserPaused=true;document.querySelectorAll('video,audio').forEach(function(m){if(!m.paused)m.pause();});}catch(e){}})();",
                            null
                        )
                    }
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    /**
     * Parks an actively playing (or media-capable) WebView inside a 1x1px non-interactive container
     * at index 0 of the host Activity's decorView when BrowserScreen leaves composition (e.g., switching
     * to Chat screen). Because the WebView remains attached to a Window, Chromium keeps playing audio
     * smoothly without triggering onDetachedFromWindow() or pausing playback.
     */
    fun parkPlayingWebViewInActivity(activity: Activity?): Boolean {
        if (!BrowserDataStore.preferences.value.backgroundMediaPlayback) return false
        val act = activity ?: resolveActivity(preferredContext()) ?: return false
        if (act.isFinishing || act.isDestroyed) return false

        val mediaState = _mediaPlaybackState.value
        val targetTab = mediaState.takeIf { it.hasActiveMedia && it.tabId.isNotBlank() }
            ?.let { getTab(it.tabId) }
            ?: getActiveNonHomeTab()?.takeIf { !it.isHomePage && !it.isExtensionPage && it.webView != null }
            ?: return false

        val wv = targetTab.webView ?: return false
        val wasPlaying = mediaState.tabId == targetTab.id && mediaState.isPlaying

        val parkAction: () -> Unit = {
            runCatching {
                val decor = act.window?.decorView as? ViewGroup
                if (decor != null) {
                    var host = backgroundHostContainerRef?.get()
                    if (host == null || host.parent !== decor) {
                        (host?.parent as? ViewGroup)?.removeView(host)
                        host = FrameLayout(act).apply {
                            isClickable = false
                            isFocusable = false
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        }
                        decor.addView(host, 0, FrameLayout.LayoutParams(1, 1))
                        backgroundHostContainerRef = WeakReference(host)
                    }
                    if (wv.parent !== host) {
                        markMediaTransitionWindow(wv)
                        (wv.parent as? ViewGroup)?.removeView(wv)
                        wv.layoutParams = FrameLayout.LayoutParams(1, 1)
                        host.addView(wv)
                    }
                    backgroundParkedTabId = targetTab.id
                    wv.onResume()
                    wv.resumeTimers()
                    if (wasPlaying) {
                        scheduleAutoResumeIfInterrupted(wv)
                    }
                }
            }.onFailure { Log.w(TAG, "parkPlayingWebViewInActivity error: ${it.message}") }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) parkAction() else mainHandler.post(parkAction)
        return true
    }

    /**
     * Detaches the hidden 1x1 background media host from the Activity's decorView (called in
     * MainActivity.onDestroy or before re-mounting in BrowserScreen) so no Activity is ever leaked.
     */
    fun detachBackgroundMediaHost(resetContextToApp: Boolean = true) {
        val action: () -> Unit = {
            val host = backgroundHostContainerRef?.get()
            if (host != null) {
                runCatching {
                    host.removeAllViews()
                    (host.parent as? ViewGroup)?.removeView(host)
                }
            }
            backgroundHostContainerRef = null
            backgroundParkedTabId = null
            if (resetContextToApp) {
                resetWebViewsToAppContext(forceAll = true)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    fun isParkedInBackgroundHost(view: View): Boolean {
        val host = backgroundHostContainerRef?.get() ?: return false
        return view.parent === host
    }

    fun requestFocusTab(tabId: String?) {
        val target = tabId?.takeIf { it.isNotBlank() }?.let { getTab(it) }
            ?: _mediaPlaybackState.value.tabId.takeIf { it.isNotBlank() }?.let { getTab(it) }
            ?: getActiveNonHomeTab()
            ?: getActiveTab()
        if (target != null) {
            switchTab(target.id)
            _openInAppBrowserRequests.tryEmit(
                BrowserOpenRequest(
                    url = target.url,
                    tabId = target.id,
                    switchToBrowser = true,
                    fromAgent = false
                )
            )
        } else {
            _openInAppBrowserRequests.tryEmit(
                BrowserOpenRequest(
                    url = "https://www.google.com",
                    tabId = "",
                    switchToBrowser = true,
                    fromAgent = false
                )
            )
        }
    }

    fun dispatchMediaControl(tabId: String?, action: String, seekPositionMs: Long = -1L) {
        val targetTab = tabId?.takeIf { it.isNotBlank() }?.let { getTab(it) }
            ?: _mediaPlaybackState.value.tabId.takeIf { it.isNotBlank() }?.let { getTab(it) }
            ?: getActiveNonHomeTab()
        val wv = targetTab?.webView

        if (action == "stop") {
            val stopAction: () -> Unit = {
                runCatching {
                    wv?.evaluateJavascript(
                        "(function(){try{window.__jarvisUserPaused=true;document.querySelectorAll('video,audio').forEach(function(m){m.pause();});}catch(e){}})();",
                        null
                    )
                }
                _mediaPlaybackState.value = BrowserMediaPlaybackState()
                lastNotifiedPlaying = null
                appContext?.let { BrowserMediaPlaybackService.stop(it) }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) stopAction() else mainHandler.post(stopAction)
            return
        }

        if (wv == null || targetTab == null) return

        val js = when (action) {
            "play" -> """
                (function(){
                  try {
                    window.__jarvisUserPaused = false;
                    var list = Array.prototype.slice.call(document.querySelectorAll('video,audio'));
                    var target = list.find(function(m){ return m.readyState > 0 && !m.ended; }) || list[0];
                    if (target) {
                      var p = target.play();
                      if (p && p.catch) p.catch(function(){});
                    } else {
                      var btn = document.querySelector('.ytp-play-button, button[aria-label*="Play"]');
                      if (btn) btn.click();
                    }
                    if (window.__jarvisEmitMediaState) setTimeout(window.__jarvisEmitMediaState, 120);
                  } catch(e) {}
                })();
            """.trimIndent()
            "pause" -> """
                (function(){
                  try {
                    window.__jarvisUserPaused = true;
                    document.querySelectorAll('video,audio').forEach(function(m){ if (!m.paused) m.pause(); });
                    if (window.__jarvisEmitMediaState) setTimeout(window.__jarvisEmitMediaState, 120);
                  } catch(e) {}
                })();
            """.trimIndent()
            "toggle" -> """
                (function(){
                  try {
                    var list = Array.prototype.slice.call(document.querySelectorAll('video,audio'));
                    var playing = list.filter(function(m){ return !m.paused && !m.ended; });
                    if (playing.length > 0) {
                      window.__jarvisUserPaused = true;
                      playing.forEach(function(m){ m.pause(); });
                    } else {
                      window.__jarvisUserPaused = false;
                      var target = list.find(function(m){ return m.readyState > 0; }) || list[0];
                      if (target) {
                        var p = target.play();
                        if (p && p.catch) p.catch(function(){});
                      } else {
                        var btn = document.querySelector('.ytp-play-button, button[aria-label*="Play"]');
                        if (btn) btn.click();
                      }
                    }
                    if (window.__jarvisEmitMediaState) setTimeout(window.__jarvisEmitMediaState, 120);
                  } catch(e) {}
                })();
            """.trimIndent()
            "next" -> """
                (function(){
                  try {
                    var nextBtn = document.querySelector('.ytp-next-button, button[aria-label*="Next video"], button[aria-label*="Next"], a.ytp-next-button');
                    if (nextBtn && !nextBtn.disabled && nextBtn.offsetParent !== null) {
                      nextBtn.click();
                      return;
                    }
                    var list = Array.prototype.slice.call(document.querySelectorAll('video,audio'));
                    var target = list.find(function(m){ return m.readyState > 0; }) || list[0];
                    if (target && isFinite(target.duration)) {
                      target.currentTime = Math.min(target.duration, target.currentTime + 10);
                    }
                    if (window.__jarvisEmitMediaState) setTimeout(window.__jarvisEmitMediaState, 180);
                  } catch(e) {}
                })();
            """.trimIndent()
            "prev" -> """
                (function(){
                  try {
                    var list = Array.prototype.slice.call(document.querySelectorAll('video,audio'));
                    var target = list.find(function(m){ return m.readyState > 0; }) || list[0];
                    var prevBtn = document.querySelector('.ytp-prev-button, button[aria-label*="Previous video"], button[aria-label*="Previous"]');
                    if (prevBtn && !prevBtn.disabled && prevBtn.offsetParent !== null && (!target || target.currentTime < 4)) {
                      prevBtn.click();
                      return;
                    }
                    if (target) {
                      target.currentTime = Math.max(0, target.currentTime - 10);
                    }
                    if (window.__jarvisEmitMediaState) setTimeout(window.__jarvisEmitMediaState, 180);
                  } catch(e) {}
                })();
            """.trimIndent()
            "seek" -> """
                (function(){
                  try {
                    var list = Array.prototype.slice.call(document.querySelectorAll('video,audio'));
                    var target = list.find(function(m){ return m.readyState > 0; }) || list[0];
                    if (target && ${seekPositionMs} >= 0) {
                      target.currentTime = ${seekPositionMs} / 1000.0;
                    }
                    if (window.__jarvisEmitMediaState) setTimeout(window.__jarvisEmitMediaState, 120);
                  } catch(e) {}
                })();
            """.trimIndent()
            else -> return
        }

        val runControl: () -> Unit = {
            runCatching {
                wv.onResume()
                wv.resumeTimers()
                wv.evaluateJavascript(js, null)
            }
            // Optimistic UI/Notification state update for instant play/pause feedback
            val cur = _mediaPlaybackState.value
            if (cur.hasActiveMedia && (action == "play" || action == "pause" || action == "toggle")) {
                val nextPlaying = when (action) {
                    "play" -> true
                    "pause" -> false
                    else -> !cur.isPlaying
                }
                val updated = cur.copy(isPlaying = nextPlaying, updatedAtMs = System.currentTimeMillis())
                _mediaPlaybackState.value = updated
                lastNotifiedPlaying = nextPlaying
                lastNotificationPushMs = System.currentTimeMillis()
                appContext?.let { BrowserMediaPlaybackService.update(it, updated) }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) runControl() else mainHandler.post(runControl)
    }

    private fun markMediaTransitionWindow(webView: WebView?) {
        val wv = webView ?: return
        runCatching {
            wv.evaluateJavascript(
                "(function(){try{window.__jarvisTransitionUntil=Date.now()+2200;window.__jarvisUserPaused=false;}catch(e){}})();",
                null
            )
        }
    }

    private fun scheduleAutoResumeIfInterrupted(webView: WebView?) {
        val wv = webView ?: return
        val resumeJs = """
            (function(){
              try {
                if (window.__jarvisUserPaused) return;
                var list = Array.prototype.slice.call(document.querySelectorAll('video,audio'));
                var v = list.find(function(m){ return m.readyState > 0 && !m.ended; }) || list[0];
                if (v && v.paused && !v.ended) {
                  var p = v.play();
                  if (p && p.catch) p.catch(function(){});
                }
              } catch(e) {}
            })();
        """.trimIndent()
        listOf(140L, 380L, 760L).forEach { delayMs ->
            mainHandler.postDelayed({
                runCatching {
                    wv.onResume()
                    wv.resumeTimers()
                    wv.evaluateJavascript(resumeJs, null)
                }
            }, delayMs)
        }
    }

    private fun handleMediaReportFromJs(tabId: String, jsonPayload: String) {
        val tab = getTab(tabId) ?: return
        if (tab.isHomePage || tab.isExtensionPage) return
        val obj = runCatching { JSONObject(jsonPayload) }.getOrNull() ?: return
        val hasMedia = obj.optBoolean("hasMedia", false)
        val isPlaying = obj.optBoolean("playing", false)
        val ended = obj.optBoolean("ended", false)

        if (!hasMedia) {
            if (_mediaPlaybackState.value.tabId == tabId) {
                _mediaPlaybackState.value = BrowserMediaPlaybackState()
                lastNotifiedPlaying = null
                appContext?.let { BrowserMediaPlaybackService.stop(it) }
            }
            return
        }

        val rawTitle = obj.optString("title", "").trim()
            .replace(Regex("\\s*-\\s*YouTube$", RegexOption.IGNORE_CASE), "")
            .trim()
        val resolvedTitle = rawTitle.ifBlank {
            tab.title.replace(Regex("\\s*-\\s*YouTube$", RegexOption.IGNORE_CASE), "").trim()
                .ifBlank { extractHostLabel(tab.url) }
        }
        val resolvedArtist = obj.optString("artist", "").trim()
            .ifBlank { extractHostLabel(tab.url).ifBlank { "Agentra Browser" } }
        val artworkUrl = obj.optString("artwork", "").trim()
        val durationMs = obj.optLong("durationMs", -1L)
        val positionMs = obj.optLong("positionMs", 0L).coerceAtLeast(0L)

        // Do not let an idle background tab overwrite a different currently playing tab
        val existing = _mediaPlaybackState.value
        if (!isPlaying && existing.hasActiveMedia && existing.isPlaying && existing.tabId != tabId) {
            return
        }

        val nextState = BrowserMediaPlaybackState(
            tabId = tabId,
            title = resolvedTitle,
            artist = resolvedArtist,
            url = tab.url,
            artworkUrl = artworkUrl,
            isPlaying = isPlaying && !ended,
            durationMs = durationMs,
            positionMs = positionMs,
            hasActiveMedia = true,
            updatedAtMs = System.currentTimeMillis()
        )
        _mediaPlaybackState.value = nextState

        val now = System.currentTimeMillis()
        val stateChanged = lastNotifiedPlaying != nextState.isPlaying ||
            lastNotifiedTitle != nextState.title ||
            lastNotifiedTabId != nextState.tabId
        if (stateChanged || now - lastNotificationPushMs >= 3000L) {
            lastNotifiedPlaying = nextState.isPlaying
            lastNotifiedTitle = nextState.title
            lastNotifiedTabId = nextState.tabId
            lastNotificationPushMs = now
            appContext?.let { BrowserMediaPlaybackService.update(it, nextState) }
        }
    }

    private inner class BrowserMediaJsBridge(private val tabId: String) {
        @JavascriptInterface
        fun onMediaState(json: String?) {
            if (json.isNullOrBlank()) return
            mainHandler.post {
                handleMediaReportFromJs(tabId, json)
            }
        }
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

    private fun restoreActivityFromFullscreen(state: FullscreenVideoState) {
        if (!state.attachedToDecorView) return
        val act = state.hostActivityRef?.get()
        val win = act?.window
        runCatching {
            state.overlayContainer?.removeAllViews()
            (state.overlayContainer?.parent as? ViewGroup)?.removeView(state.overlayContainer)
        }
        if (act != null && win != null) {
            runCatching {
                val controller = WindowCompat.getInsetsController(win, win.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                act.requestedOrientation = if (state.previousOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                } else {
                    state.previousOrientation
                }
            }
        }
    }

    fun exitFullscreenVideo() {
        val current = _fullscreenVideoState.value ?: return
        val wasPlaying = _mediaPlaybackState.value.let { it.tabId == current.tabId && it.isPlaying }
        _fullscreenVideoState.value = null
        val detachAction: () -> Unit = {
            val wv = getTab(current.tabId)?.webView
            if (wasPlaying) markMediaTransitionWindow(wv)
            runCatching { (current.customView.parent as? ViewGroup)?.removeView(current.customView) }
            restoreActivityFromFullscreen(current)
            runCatching { current.callback.onCustomViewHidden() }
            wv?.let {
                syncVideoLayoutAfterResize(it, getTab(current.tabId)?.url)
                if (wasPlaying) scheduleAutoResumeIfInterrupted(it)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            detachAction()
        } else {
            mainHandler.post(detachAction)
        }
    }

    /**
     * Restores cached WebViews' MutableContextWrapper back to applicationContext when
     * BrowserScreen leaves composition so no Activity instance is ever leaked.
     * Skips a WebView currently parked in the active Activity's background media host unless [forceAll] is true.
     */
    fun resetWebViewsToAppContext(forceAll: Boolean = false) {
        val ctx = appContext ?: return
        val parkedId = if (forceAll) null else backgroundParkedTabId
        val snapshotTabs = synchronized(tabsLock) { tabs.values.toList() }
        val resetAction = {
            snapshotTabs.forEach { tab ->
                if (parkedId != null && tab.id == parkedId) return@forEach
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
                if (fromAgent) {
                    existing.desktopMode = desktopMode
                    existing.isAgentControlled = true
                }
                existing.title = extractHostLabel(url).ifBlank { existing.title }
                existing.updatedAt = System.currentTimeMillis()
            }
            activeTabId = tabToUse!!.id
        }

        val tab = tabToUse!!
        val effectiveUrl = if (tab.desktopMode) {
            BrowserIdentity.rewriteUrlForDesktopMode(url, desktop = true)
        } else {
            url
        }
        if (effectiveUrl != tab.url) {
            tab.url = effectiveUrl
        }
        tab.resetPageLoadedDeferred()
        emitTabsState()

        val loadOnMain: () -> Unit = {
            val wvCtx = preferredContext(context) ?: context
            val wv = getOrCreateWebViewLockedOnMain(tab, wvCtx)
            BrowserIdentity.applyTo(wv.settings, wvCtx, desktop = tab.desktopMode)
            wv.onResume()
            wv.loadUrl(effectiveUrl)
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
        val normalized = normalizeUrl(rawUrl)
        val tab = synchronized(tabsLock) { tabs[tabId] } ?: return
        val url = if (tab.desktopMode) {
            BrowserIdentity.rewriteUrlForDesktopMode(normalized, desktop = true)
        } else {
            normalized
        }
        tab.url = url
        tab.isHomePage = false
        tab.isExtensionPage = false
        tab.extensionId = null
        tab.updatedAt = System.currentTimeMillis()
        tab.resetPageLoadedDeferred()
        emitTabsState()

        val ctx = preferredContext()
        val runNav: () -> Unit = {
            val wv = tab.webView ?: ctx?.let { getOrCreateWebViewLockedOnMain(tab, it) }
            if (wv != null && ctx != null) {
                BrowserIdentity.applyTo(wv.settings, ctx, desktop = tab.desktopMode)
                wv.onResume()
                wv.loadUrl(url)
            }
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
        val wasPlaying = _mediaPlaybackState.value.let { it.tabId == tabId && it.isPlaying }
        if (wasPlaying) markMediaTransitionWindow(wv)

        // If this WebView was parked in the 1x1 background media host, clean up the host cleanly
        if (backgroundParkedTabId == tabId) {
            val host = backgroundHostContainerRef?.get()
            if (host != null && wv.parent === host) {
                runCatching {
                    host.removeView(wv)
                    (host.parent as? ViewGroup)?.removeView(host)
                }
                backgroundHostContainerRef = null
            }
            backgroundParkedTabId = null
        }

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
        // IMPORTANT: Keep LAYER_TYPE_NONE so WebView renders directly into the hardware-accelerated
        // window surface. Explicitly setting LAYER_TYPE_HARDWARE creates an offscreen FBO that breaks
        // SurfaceTexture/MediaCodec HTML5 <video> compositing (black screen on YouTube & streaming sites).
        wv.setLayerType(View.LAYER_TYPE_NONE, null)
        wv.onResume()
        wv.resumeTimers()
        wv.requestLayout()
        wv.post {
            syncVideoLayoutAfterResize(wv, tab.url)
            if (wasPlaying) scheduleAutoResumeIfInterrupted(wv)
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
                var stale = document.getElementById('agentra-yt-video-fit');
                if (stale && stale.parentNode) stale.parentNode.removeChild(stale);
                window.dispatchEvent(new Event('resize'));
              } catch(e) {}
            })();
        """.trimIndent()
        runCatching { wv.evaluateJavascript(js, null) }
    }

    private fun injectDesktopViewportIfNeeded(tab: BrowserTab?, view: WebView?, url: String?) {
        if (tab == null || view == null || !tab.desktopMode || tab.isExtensionPage) return
        if (url.isNullOrBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) return
        if (BrowserIdentity.isGoogleSignInUrl(url)) return
        runCatching { view.evaluateJavascript(BrowserIdentity.buildDesktopViewportScript(), null) }
    }

    private fun injectBackgroundMediaScript(tab: BrowserTab?, view: WebView?, url: String?) {
        if (tab == null || view == null || tab.isExtensionPage) return
        if (url.isNullOrBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) return
        if (BrowserIdentity.isGoogleSignInUrl(url)) return
        val allowBg = BrowserDataStore.preferences.value.backgroundMediaPlayback
        val js = """
            (function(){
              try {
                window.__jarvisBgPlayEnabled = ${if (allowBg) "true" else "false"};
                if (window.__jarvisBgPlayEnabled && !window.__jarvisVisSpoofed) {
                  window.__jarvisVisSpoofed = true;
                  var visProto = Document.prototype || document;
                  try { Object.defineProperty(document, 'hidden', { get: function(){ return false; }, configurable: true }); } catch(e){}
                  try { Object.defineProperty(document, 'webkitHidden', { get: function(){ return false; }, configurable: true }); } catch(e){}
                  try { Object.defineProperty(document, 'visibilityState', { get: function(){ return 'visible'; }, configurable: true }); } catch(e){}
                  try { Object.defineProperty(document, 'webkitVisibilityState', { get: function(){ return 'visible'; }, configurable: true }); } catch(e){}
                  try { Object.defineProperty(document, 'onvisibilitychange', { get: function(){ return null; }, set: function(){}, configurable: true }); } catch(e){}
                  var blocked = { visibilitychange: 1, webkitvisibilitychange: 1, mozvisibilitychange: 1, pagehide: 1, freeze: 1 };
                  var origDocAdd = Document.prototype.addEventListener;
                  if (origDocAdd) {
                    Document.prototype.addEventListener = function(type, listener, opts) {
                      if (window.__jarvisBgPlayEnabled && blocked[type]) return;
                      return origDocAdd.call(this, type, listener, opts);
                    };
                  }
                  var origWinAdd = Window.prototype.addEventListener;
                  if (origWinAdd) {
                    Window.prototype.addEventListener = function(type, listener, opts) {
                      if (window.__jarvisBgPlayEnabled && blocked[type]) return;
                      return origWinAdd.call(this, type, listener, opts);
                    };
                  }
                }

                if (window.__jarvisMediaHookInstalled) {
                  if (window.__jarvisEmitMediaState) window.__jarvisEmitMediaState();
                  return;
                }
                window.__jarvisMediaHookInstalled = true;
                window.__jarvisUserPaused = false;
                window.__jarvisTransitionUntil = 0;

                function pickPrimaryMedia() {
                  var nodes = Array.prototype.slice.call(document.querySelectorAll('video,audio'));
                  if (!nodes.length) return null;
                  var playing = nodes.find(function(m){ return !m.paused && !m.ended && m.readyState > 0; });
                  if (playing) return playing;
                  var withSrc = nodes.find(function(m){ return (m.currentSrc || m.src) && m.readyState > 0; });
                  return withSrc || nodes[0];
                }

                function resolveMeta() {
                  var title = '';
                  var artist = '';
                  var artwork = '';
                  try {
                    var ms = navigator.mediaSession && navigator.mediaSession.metadata;
                    if (ms) {
                      if (ms.title) title = String(ms.title);
                      if (ms.artist) artist = String(ms.artist);
                      if (ms.artwork && ms.artwork.length) {
                        artwork = String(ms.artwork[ms.artwork.length - 1].src || ms.artwork[0].src || '');
                      }
                    }
                  } catch(e) {}
                  if (!title) {
                    var h = document.querySelector('h1.ytd-watch-metadata, .slim-video-information-title, ytm-slim-video-metadata-renderer h2, h1');
                    if (h && h.innerText && h.innerText.trim()) title = h.innerText.trim();
                  }
                  if (!title) title = (document.title || '').replace(/\s*-\s*YouTube$/i, '').trim();
                  if (!artist) {
                    var ch = document.querySelector('ytd-channel-name a, .slim-owner-channel-name, ytm-slim-owner-renderer .slim-owner-channel-name');
                    if (ch && ch.innerText && ch.innerText.trim()) artist = ch.innerText.trim();
                  }
                  return { title: title, artist: artist, artwork: artwork };
                }

                function emitState() {
                  if (typeof window.__jarvis_media_bridge === 'undefined') return;
                  var m = pickPrimaryMedia();
                  if (!m) {
                    window.__jarvis_media_bridge.onMediaState(JSON.stringify({ hasMedia: false }));
                    return;
                  }
                  var meta = resolveMeta();
                  var dur = (isFinite(m.duration) && m.duration > 0) ? Math.round(m.duration * 1000) : -1;
                  var pos = (isFinite(m.currentTime) && m.currentTime >= 0) ? Math.round(m.currentTime * 1000) : 0;
                  window.__jarvis_media_bridge.onMediaState(JSON.stringify({
                    hasMedia: true,
                    playing: !m.paused && !m.ended,
                    ended: !!m.ended,
                    title: meta.title,
                    artist: meta.artist,
                    artwork: meta.artwork,
                    durationMs: dur,
                    positionMs: pos
                  }));
                }
                window.__jarvisEmitMediaState = emitState;

                document.addEventListener('pointerdown', function(ev) {
                  try {
                    var t = ev.target;
                    if (!t || !t.closest) return;
                    if (t.closest('.ytp-play-button, button[aria-label*="Pause"], button[aria-label*="pause"], .player-control-play-pause-icon')) {
                      window.__jarvisLastPlayToggleTap = Date.now();
                    }
                  } catch(e) {}
                }, true);

                document.addEventListener('play', function() {
                  window.__jarvisUserPaused = false;
                  emitState();
                }, true);
                document.addEventListener('playing', function() {
                  window.__jarvisUserPaused = false;
                  emitState();
                }, true);
                document.addEventListener('pause', function(ev) {
                  var m = ev.target;
                  var now = Date.now();
                  var inTransition = now < (window.__jarvisTransitionUntil || 0);
                  var tappedControl = (now - (window.__jarvisLastPlayToggleTap || 0)) < 1200;
                  if (inTransition && !tappedControl && m && m.play && !m.ended) {
                    setTimeout(function() {
                      try {
                        if (!window.__jarvisUserPaused && m.paused && !m.ended) {
                          var p = m.play();
                          if (p && p.catch) p.catch(function(){});
                        }
                      } catch(e) {}
                    }, 90);
                  } else if (tappedControl) {
                    window.__jarvisUserPaused = true;
                  }
                  emitState();
                }, true);
                document.addEventListener('ended', function() { emitState(); }, true);
                document.addEventListener('loadedmetadata', function() { emitState(); }, true);
                document.addEventListener('fullscreenchange', function() {
                  window.__jarvisTransitionUntil = Date.now() + 1800;
                  emitState();
                }, true);
                document.addEventListener('webkitfullscreenchange', function() {
                  window.__jarvisTransitionUntil = Date.now() + 1800;
                  emitState();
                }, true);

                setInterval(function() {
                  var m = pickPrimaryMedia();
                  if (m && !m.paused && !m.ended) {
                    emitState();
                  }
                }, 2000);

                emitState();
              } catch(e) {}
            })();
        """.trimIndent()
        runCatching { view.evaluateJavascript(js, null) }
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
        val creationCtx = preferredContext(context) ?: context
        val wrappedContext = MutableContextWrapper(creationCtx)

        val wv = BackgroundPlayWebView(wrappedContext).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Keep LAYER_TYPE_NONE so hardware-accelerated window compositing renders <video> properly
            setLayerType(View.LAYER_TYPE_NONE, null)
            setBackgroundColor(Color.parseColor("#121214"))
            if (!tab.isExtensionPage) {
                addJavascriptInterface(BrowserMediaJsBridge(tabId), "__jarvis_media_bridge")
            }
            settings.apply {
                javaScriptEnabled = prefs.javaScriptEnabled || tab.isExtensionPage || tab.isAgentControlled
                domStorageEnabled = !tab.isPrivate || tab.isExtensionPage
                databaseEnabled = !tab.isPrivate || tab.isExtensionPage
                allowFileAccess = tab.url.startsWith("file://")
                allowContentAccess = true
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                cacheMode = if (tab.isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                BrowserIdentity.applyTo(this, creationCtx, desktop = tab.desktopMode)
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
            if (creationCtx === creationCtx.applicationContext) {
                val dm = creationCtx.resources.displayMetrics
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
                    injectBackgroundMediaScript(currentTab, view, url)
                    hostUiCallbacks?.onPageStarted(tabId, view, url)
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                    val currentTab = getTab(tabId)
                    injectDesktopViewportIfNeeded(currentTab, view, url)
                    injectBackgroundMediaScript(currentTab, view, url)
                    hostUiCallbacks?.onPageCommitVisible(tabId, view, url)
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
                        injectDesktopViewportIfNeeded(currentTab, view, loadedUrl)
                        injectBackgroundMediaScript(currentTab, view, loadedUrl)
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

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    Log.w(TAG, "WebView render process gone for tab $tabId (didCrash=${detail?.didCrash()}); recovering")
                    val currentTab = getTab(tabId)
                    if (currentTab != null) {
                        runCatching {
                            (view?.parent as? ViewGroup)?.removeView(view)
                            view?.destroy()
                        }
                        currentTab.webView = null
                        currentTab.jsInterfacesAttached = false
                        emitTabsState()
                    }
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun getDefaultVideoPoster(): Bitmap {
                    return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
                        eraseColor(Color.BLACK)
                    }
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    if (request == null) return
                    val grantable = mutableListOf<String>()
                    val ctx = preferredContext()
                    for (res in request.resources) {
                        when (res) {
                            PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> {
                                // Required for EME / DRM HTML5 video streams (YouTube & OTT players)
                                grantable.add(res)
                            }
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                                val ok = ctx != null && ContextCompat.checkSelfPermission(
                                    ctx, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (ok) grantable.add(res)
                            }
                            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                                val ok = ctx != null && ContextCompat.checkSelfPermission(
                                    ctx, Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (ok) grantable.add(res)
                            }
                        }
                    }
                    if (grantable.isNotEmpty()) {
                        runCatching { request.grant(grantable.toTypedArray()) }
                    } else {
                        runCatching { request.deny() }
                    }
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                    val parentTab = getTab(tabId)
                    val ctx = preferredContext(view?.context) ?: return false
                    val newTabId = UUID.randomUUID().toString().substring(0, 8)
                    val childTab = BrowserTab(
                        id = newTabId,
                        url = "about:blank",
                        title = "New Tab",
                        isHomePage = false,
                        isPrivate = parentTab?.isPrivate == true,
                        desktopMode = parentTab?.desktopMode ?: BrowserDataStore.preferences.value.desktopModeDefault,
                        groupId = parentTab?.groupId,
                        updatedAt = System.currentTimeMillis()
                    )
                    synchronized(tabsLock) {
                        tabs[newTabId] = childTab
                        activeTabId = newTabId
                    }
                    val childWebView = getOrCreateWebViewLockedOnMain(childTab, ctx)
                    emitTabsState()
                    transport.webView = childWebView
                    resultMsg.sendToTarget()
                    return true
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    if (filePathCallback == null) return false
                    return hostUiCallbacks?.onShowFileChooser(tabId, filePathCallback, fileChooserParams) == true
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view == null || callback == null) return
                    val wv = getTab(tabId)?.webView
                    val wasPlaying = _mediaPlaybackState.value.let { it.tabId == tabId && it.isPlaying }
                    if (wasPlaying) markMediaTransitionWindow(wv)

                    val previous = _fullscreenVideoState.value
                    if (previous != null) {
                        runCatching { (previous.customView.parent as? ViewGroup)?.removeView(previous.customView) }
                        restoreActivityFromFullscreen(previous)
                        runCatching { previous.callback.onCustomViewHidden() }
                    }

                    // Attach fullscreen customView synchronously to the host Activity's decorView on the
                    // same UI frame to avoid Compose recomposition delay / surface handoff pause on YouTube.
                    val hostAct = resolveActivity(preferredContext(view.context))
                    val decor = hostAct?.window?.decorView as? ViewGroup
                    if (hostAct != null && decor != null && !hostAct.isFinishing && !hostAct.isDestroyed) {
                        val prevOrientation = hostAct.requestedOrientation
                        val overlay = FrameLayout(hostAct).apply {
                            setBackgroundColor(Color.BLACK)
                            isClickable = true
                            isFocusable = true
                            keepScreenOn = true
                        }
                        (view.parent as? ViewGroup)?.removeView(view)
                        overlay.addView(
                            view,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        decor.addView(
                            overlay,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        runCatching {
                            hostAct.window?.let { win ->
                                win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                val controller = WindowCompat.getInsetsController(win, win.decorView)
                                controller.systemBarsBehavior =
                                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                controller.hide(WindowInsetsCompat.Type.systemBars())
                            }
                            hostAct.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                        _fullscreenVideoState.value = FullscreenVideoState(
                            tabId = tabId,
                            customView = view,
                            callback = callback,
                            attachedToDecorView = true,
                            overlayContainer = overlay,
                            hostActivityRef = WeakReference(hostAct),
                            previousOrientation = prevOrientation
                        )
                    } else {
                        _fullscreenVideoState.value = FullscreenVideoState(
                            tabId = tabId,
                            customView = view,
                            callback = callback,
                            attachedToDecorView = false
                        )
                    }

                    if (wasPlaying) {
                        scheduleAutoResumeIfInterrupted(wv)
                    }
                }

                override fun onHideCustomView() {
                    val current = _fullscreenVideoState.value
                    val wasPlaying = _mediaPlaybackState.value.let { it.tabId == tabId && it.isPlaying }
                    val wv = getTab(tabId)?.webView
                    if (wasPlaying) markMediaTransitionWindow(wv)
                    _fullscreenVideoState.value = null
                    if (current != null) {
                        runCatching { (current.customView.parent as? ViewGroup)?.removeView(current.customView) }
                        restoreActivityFromFullscreen(current)
                        runCatching { current.callback.onCustomViewHidden() }
                    }
                    wv?.let {
                        it.post { syncVideoLayoutAfterResize(it, getTab(tabId)?.url) }
                        if (wasPlaying) scheduleAutoResumeIfInterrupted(it)
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

    fun goBackTab(tabId: String): Boolean {
        val tab = getTab(tabId) ?: return false
        val wv = tab.webView ?: return false
        if (!wv.canGoBack()) return false
        tab.resetPageLoadedDeferred()
        wv.goBack()
        return true
    }

    fun goForwardTab(tabId: String): Boolean {
        val tab = getTab(tabId) ?: return false
        val wv = tab.webView ?: return false
        if (!wv.canGoForward()) return false
        tab.resetPageLoadedDeferred()
        wv.goForward()
        return true
    }

    fun reloadTab(tabId: String): Boolean {
        val tab = getTab(tabId) ?: return false
        val wv = tab.webView ?: return false
        tab.resetPageLoadedDeferred()
        wv.reload()
        return true
    }

    fun setDesktopMode(tabId: String, desktop: Boolean) {
        val context = preferredContext() ?: return
        val tab = (if (tabId.isNotBlank()) getTab(tabId) else null) ?: getActiveTab() ?: return
        tab.desktopMode = desktop
        val currentUrl = tab.webView?.url?.takeIf { it.isNotBlank() } ?: tab.url
        val rewrittenUrl = if (!tab.isHomePage && !tab.isExtensionPage) {
            BrowserIdentity.rewriteUrlForDesktopMode(currentUrl, desktop)
        } else {
            currentUrl
        }
        if (rewrittenUrl != tab.url && !tab.isHomePage && !tab.isExtensionPage) {
            tab.url = rewrittenUrl
        }
        emitTabsState()
        val applyAction = {
            tab.webView?.let { wv ->
                BrowserIdentity.applyTo(wv.settings, context, desktop)
                wv.setInitialScale(0)
                wv.onResume()
                if (!tab.isHomePage && !tab.isExtensionPage) {
                    if (rewrittenUrl.isNotBlank() && rewrittenUrl != wv.url) {
                        wv.loadUrl(rewrittenUrl)
                    } else {
                        wv.reload()
                    }
                }
            }
            Unit
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyAction()
        } else {
            mainHandler.post(applyAction)
        }
    }

    fun closeTab(tabId: String) {
        if (_fullscreenVideoState.value?.tabId == tabId) {
            exitFullscreenVideo()
        }
        if (backgroundParkedTabId == tabId) {
            detachBackgroundMediaHost(resetContextToApp = false)
        }
        if (_mediaPlaybackState.value.tabId == tabId) {
            _mediaPlaybackState.value = BrowserMediaPlaybackState()
            lastNotifiedPlaying = null
            appContext?.let { BrowserMediaPlaybackService.stop(it) }
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
                    if (wv is BackgroundPlayWebView) {
                        wv.allowBackgroundPlayback = false
                    }
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
