package com.jarvisai.assistant.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

data class BrowserTab(
    val id: String,
    var url: String,
    var title: String,
    var webView: WebView? = null,
    val consoleLogs: MutableList<String> = mutableListOf()
)

class BrowserSessionManager private constructor() {

    companion object {
        private const val TAG = "BrowserSessionManager"
        val instance: BrowserSessionManager by lazy { BrowserSessionManager() }
    }

    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tabs = ConcurrentHashMap<String, BrowserTab>()
    private var activeTabId: String? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun createTab(url: String = "about:blank"): BrowserTab {
        val context = appContext ?: throw IllegalStateException("BrowserSessionManager not initialized")
        val tabId = java.util.UUID.randomUUID().toString().substring(0, 8)

        val deferred = CompletableDeferred<WebView>()
        mainHandler.post {
            val wv = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                        tabs[tabId]?.url = loadedUrl.orEmpty()
                        tabs[tabId]?.title = view?.title.orEmpty()
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        consoleMessage?.let {
                            tabs[tabId]?.consoleLogs?.add("[${it.messageLevel()}] ${it.message()} (${it.sourceId()}:${it.lineNumber()})")
                        }
                        return true
                    }
                }

                loadUrl(url)
            }
            deferred.complete(wv)
        }

        val tab = BrowserTab(id = tabId, url = url, title = "New Tab")
        tabs[tabId] = tab
        if (activeTabId == null) activeTabId = tabId

        serverScopeLaunch {
            tab.webView = deferred.await()
        }
        return tab
    }

    fun listTabs(): List<BrowserTab> = tabs.values.toList()

    fun getActiveTab(): BrowserTab? {
        val id = activeTabId ?: return tabs.values.firstOrNull()
        return tabs[id] ?: tabs.values.firstOrNull()
    }

    fun switchTab(tabId: String): Boolean {
        if (tabs.containsKey(tabId)) {
            activeTabId = tabId
            return true
        }
        return false
    }

    fun closeTab(tabId: String) {
        val tab = tabs.remove(tabId)
        mainHandler.post {
            tab?.webView?.destroy()
        }
        if (activeTabId == tabId) {
            activeTabId = tabs.keys.firstOrNull()
        }
    }

    suspend fun evalJs(tabId: String, script: String): Result<String> = withContext(Dispatchers.Main) {
        val tab = tabs[tabId] ?: return@withContext Result.failure(IllegalArgumentException("Tab not found"))
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

    suspend fun captureScreenshot(tabId: String): Result<String> = withContext(Dispatchers.Main) {
        val tab = tabs[tabId] ?: return@withContext Result.failure(IllegalArgumentException("Tab not found"))
        val wv = tab.webView ?: return@withContext Result.failure(IllegalStateException("WebView not initialized"))

        try {
            val width = wv.width.coerceAtLeast(800)
            val height = wv.height.coerceAtLeast(600)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            wv.draw(canvas)

            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            Result.success(base64)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun serverScopeLaunch(block: suspend () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch { block() }
    }
}
