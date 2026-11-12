package com.jarvisai.assistant.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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

class BrowserTab(
    val id: String,
    var url: String,
    var title: String,
    var webView: WebView? = null,
    val consoleLogs: MutableList<String> = mutableListOf(),
    val pageLoadedDeferred: CompletableDeferred<Boolean> = CompletableDeferred(),
    var desktopMode: Boolean = false
) {
    var aiBusy: Boolean by mutableStateOf(false)
    var aiLocked: Boolean by mutableStateOf(false)
    var activeJsDialog: BrowserJsDialogRequest? by mutableStateOf(null)
}

class BrowserSessionManager private constructor() {

    companion object {
        private const val TAG = "BrowserSessionManager"
        val instance: BrowserSessionManager by lazy { BrowserSessionManager() }
    }

    var appContext: Context? = null
        private set
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tabs = ConcurrentHashMap<String, BrowserTab>()
    private var activeTabId: String? = null

    private val _isAgentActive = MutableStateFlow(false)
    val isAgentActive: StateFlow<Boolean> = _isAgentActive.asStateFlow()

    private val _agentActionSummary = MutableStateFlow("Antigravity automating page...")
    val agentActionSummary: StateFlow<String> = _agentActionSummary.asStateFlow()

    private val _agentCursorPosition = MutableStateFlow<Pair<Float, Float>?>(null)
    val agentCursorPosition: StateFlow<Pair<Float, Float>?> = _agentCursorPosition.asStateFlow()

    private val _isTouchLocked = MutableStateFlow(false)
    val isTouchLocked: StateFlow<Boolean> = _isTouchLocked.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        mainHandler.post {
            runCatching { WebView.setWebContentsDebuggingEnabled(true) }
        }
    }

    fun updateAgentStatus(
        active: Boolean,
        summary: String = "Antigravity automating page...",
        cursor: Pair<Float, Float>? = null
    ) {
        _isAgentActive.value = active
        if (summary.isNotBlank()) {
            _agentActionSummary.value = summary
        }
        if (cursor != null || !active) {
            _agentCursorPosition.value = cursor
        }
    }

    fun setTouchLocked(locked: Boolean) {
        _isTouchLocked.value = locked
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun createTab(url: String = "about:blank", desktopMode: Boolean = false): BrowserTab {
        val context = appContext ?: throw IllegalStateException("BrowserSessionManager not initialized")
        val tabId = UUID.randomUUID().toString().substring(0, 8)
        val tab = BrowserTab(
            id = tabId,
            url = url,
            title = "New Tab",
            desktopMode = desktopMode
        )
        tabs[tabId] = tab
        activeTabId = tabId

        val initWebView: () -> WebView = {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                BrowserIdentity.applyTo(settings, context, desktop = desktopMode)

                // Layout offscreen with standard mobile viewport so getBoundingClientRect() and screenshots work accurately
                val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY)
                measure(widthSpec, heightSpec)
                layout(0, 0, 1080, 2400)

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                        val currentTab = tabs[tabId] ?: return
                        currentTab.url = loadedUrl.orEmpty()
                        currentTab.title = view?.title.orEmpty()
                        currentTab.pageLoadedDeferred.complete(true)
                        BrowserAutomationController.onPageFinished(currentTab, loadedUrl)
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        consoleMessage?.let {
                            tabs[tabId]?.consoleLogs?.add(
                                "[${it.messageLevel()}] ${it.message()} (${it.sourceId()}:${it.lineNumber()})"
                            )
                        }
                        return true
                    }

                    override fun onJsAlert(
                        view: WebView?,
                        url: String?,
                        message: String?,
                        result: JsResult?
                    ): Boolean {
                        val currentTab = tabs[tabId] ?: return false
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
                        val currentTab = tabs[tabId] ?: return false
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
                        val currentTab = tabs[tabId] ?: return false
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
                        val currentTab = tabs[tabId] ?: return false
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

                loadUrl(url)
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            tab.webView = initWebView()
        } else {
            val deferred = CompletableDeferred<WebView>()
            mainHandler.post {
                deferred.complete(initWebView())
            }
            serverScopeLaunch {
                tab.webView = deferred.await()
            }
        }
        return tab
    }

    suspend fun awaitTabReady(tabId: String, timeoutMs: Long = 8000L) {
        val tab = tabs[tabId] ?: return
        withTimeoutOrNull(timeoutMs) {
            while (tab.webView == null) {
                kotlinx.coroutines.delay(50)
            }
            tab.pageLoadedDeferred.await()
        }
    }

    fun listTabs(): List<BrowserTab> = tabs.values.toList()

    fun getTab(tabId: String): BrowserTab? = tabs[tabId]

    fun getActiveTab(): BrowserTab? {
        val id = activeTabId ?: return tabs.values.firstOrNull()
        return tabs[id] ?: tabs.values.firstOrNull()
    }

    fun switchTab(tabId: String): Boolean {
        if (tabs.containsKey(tabId)) {
            activeTabId = tabId
            val tab = tabs[tabId]
            _isTouchLocked.value = tab?.aiLocked == true
            return true
        }
        return false
    }

    fun setDesktopMode(tabId: String, desktop: Boolean) {
        val context = appContext ?: return
        val tab = tabs[tabId] ?: getActiveTab() ?: return
        tab.desktopMode = desktop
        mainHandler.post {
            tab.webView?.let { wv ->
                BrowserIdentity.applyTo(wv.settings, context, desktop)
                wv.reload()
            }
        }
    }

    fun closeTab(tabId: String) {
        BrowserAutomationController.unlockAll(tabId)
        val tab = tabs.remove(tabId)
        mainHandler.post {
            tab?.webView?.destroy()
        }
        if (activeTabId == tabId) {
            activeTabId = tabs.keys.firstOrNull()
        }
    }

    suspend fun evalJs(tabId: String, script: String): Result<String> = withContext(Dispatchers.Main) {
        val tab = tabs[tabId] ?: getActiveTab() ?: return@withContext Result.failure(IllegalArgumentException("Tab not found"))
        val wv = tab.webView ?: return@withContext Result.failure(IllegalStateException("WebView not initialized"))

        val deferred = CompletableDeferred<String>()
        wv.evaluateJavascript(script) { result ->
            deferred.complete(result ?: "null")
        }
        Result.success(deferred.await())
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
        val tab = (if (tabId.isNotBlank()) tabs[tabId] else null) ?: getActiveTab()
            ?: return@withContext Result.failure(IllegalArgumentException("Tab not found"))
        val wv = tab.webView ?: return@withContext Result.failure(IllegalStateException("WebView not initialized"))

        try {
            val width = wv.width.coerceAtLeast(800)
            val height = wv.height.coerceAtLeast(600)
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
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    }

    private fun serverScopeLaunch(block: suspend () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch { block() }
    }
}
