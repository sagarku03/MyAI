package com.jarvisai.assistant.extension

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.jarvisai.assistant.browser.BrowserDataStore
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.URLConnection
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unified Chrome Extensions Runtime Bridge (`MV2` + `MV3`).
 *
 * Bridges all contexts identically to desktop Chromium / Quetta:
 * 1. Virtual HTTPS extension origin (`https://ext-<id>.jarvis.invalid/...` + `chrome-extension://<id>/...`)
 *    intercepted in WebView so ES modules (`<script type="module">`), `importScripts()`, `fetch()`,
 *    and extension assets work with zero CORS issues.
 * 2. Numeric integer tab IDs (`1, 2, 3...`) tracking both web tabs and extension UI tabs,
 *    preserving `lastActiveWebTabId` so popups/sidepanels query the real webpage.
 * 3. Full two-way async messaging (`runtime.sendMessage` / `connect` / `tabs.sendMessage`),
 *    plus `storage.onChanged`, `tabs.onUpdated/onActivated/onCreated/onRemoved`,
 *    `alarms`, `declarativeNetRequest`, `i18n`, `action`, `contextMenus`, `webNavigation`,
 *    `cookies`, `offscreen`, `scripting`, and `downloads`.
 */
object BrowserExtensionRuntimeBridge {

    private const val TAG = "ExtRuntimeBridge"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val nextTabSeq = AtomicInteger(1)

    data class LiveTabRecord(
        val tabId: String,
        val numericId: Int,
        var url: String,
        var title: String,
        var isActive: Boolean,
        var isPrivate: Boolean,
        var isExtensionPage: Boolean,
        var webView: WebView?,
        var status: String = "complete"
    )

    private val tabNumericIds = ConcurrentHashMap<String, Int>()
    private val liveTabs = ConcurrentHashMap<String, LiveTabRecord>()
    private val pageContentWebViews = ConcurrentHashMap<String, WebView>() // tabId -> WebView
    private val uiSurfaces = ConcurrentHashMap<String, CopyOnWriteArraySet<WebView>>() // extId -> popup/sidepanel WebViews
    private val activeAlarms = ConcurrentHashMap<String, MutableMap<String, Runnable>>() // extId -> (alarmName -> runnable)
    private val alarmMeta = ConcurrentHashMap<String, MutableMap<String, JSONObject>>() // extId -> (alarmName -> info)
    private val offscreenWebViews = ConcurrentHashMap<String, WebView>() // extId -> hidden WebView
    private val hostFetchExecutor = Executors.newCachedThreadPool()
    private val hostHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Volatile
    private var activeTabStringId: String = ""

    @Volatile
    private var lastActiveWebTabId: String = ""

    @Volatile
    private var tabActionCallbacks: TabActionCallbacks? = null

    interface TabActionCallbacks {
        fun onCreateTab(url: String, makeActive: Boolean): LiveTabRecord
        fun onSelectTab(tabId: String)
        fun onCloseTab(tabId: String)
        fun onUpdateTabUrl(tabId: String, url: String)
        fun onReloadTab(tabId: String)
        fun onToast(message: String)
        fun onRequestWebStoreInstall(extensionId: String, suggestedTitle: String) {}
        fun onRequestOpenPopup(extensionId: String) {}
    }

    fun setTabCallbacks(callbacks: TabActionCallbacks?) {
        tabActionCallbacks = callbacks
    }

    fun numericIdFor(tabId: String): Int {
        return tabNumericIds.getOrPut(tabId) { nextTabSeq.getAndIncrement() }
    }

    fun registerOrUpdateTab(
        tabId: String,
        url: String,
        title: String,
        isActive: Boolean,
        isPrivate: Boolean,
        isExtensionPage: Boolean,
        webView: WebView?,
        status: String = "complete"
    ): Int {
        val numId = numericIdFor(tabId)
        val existing = liveTabs[tabId]
        val isNewTab = existing == null
        val prevActive = existing?.isActive == true
        val record = existing ?: LiveTabRecord(tabId, numId, url, title, isActive, isPrivate, isExtensionPage, webView, status).also {
            liveTabs[tabId] = it
        }
        record.url = url
        record.title = title
        record.isActive = isActive
        record.isPrivate = isPrivate
        record.isExtensionPage = isExtensionPage
        record.status = status
        if (webView != null) {
            record.webView = webView
            pageContentWebViews[tabId] = webView
        }
        if (isActive) {
            activeTabStringId = tabId
            liveTabs.values.forEach { if (it.tabId != tabId) it.isActive = false }
            if (!isExtensionPage && url.startsWith("http")) {
                lastActiveWebTabId = tabId
            }
        }
        if (isNewTab) {
            dispatchTabCreatedEvent(record)
        }
        if (isActive && !prevActive) {
            dispatchTabActivatedEvent(record.numericId)
        }
        return numId
    }

    fun unregisterTab(tabId: String) {
        val removed = liveTabs.remove(tabId)
        pageContentWebViews.remove(tabId)
        cleanupPortsForWebView(removed?.webView)
        if (lastActiveWebTabId == tabId) {
            lastActiveWebTabId = liveTabs.values.firstOrNull { !it.isExtensionPage }?.tabId.orEmpty()
        }
        if (removed != null) {
            dispatchTabRemovedEvent(removed.numericId)
        }
    }

    fun dispatchTabUpdatedEvent(tabId: String, changeInfo: JSONObject) {
        val rec = liveTabs[tabId] ?: return
        val tabJson = buildTabJson(rec, rec.isActive).toString()
        val changeStr = changeInfo.toString()
        broadcastEventToExtensions { extId ->
            "if (window.__jarvisDispatchTabUpdated) { window.__jarvisDispatchTabUpdated(${JSONObject.quote(extId)}, ${rec.numericId}, $changeStr, $tabJson); }"
        }
    }

    private fun dispatchTabActivatedEvent(numericTabId: Int) {
        broadcastEventToExtensions { extId ->
            "if (window.__jarvisDispatchTabActivated) { window.__jarvisDispatchTabActivated(${JSONObject.quote(extId)}, $numericTabId); }"
        }
    }

    private fun dispatchTabCreatedEvent(rec: LiveTabRecord) {
        val tabJson = buildTabJson(rec, rec.isActive).toString()
        broadcastEventToExtensions { extId ->
            "if (window.__jarvisDispatchTabCreated) { window.__jarvisDispatchTabCreated(${JSONObject.quote(extId)}, $tabJson); }"
        }
    }

    private fun dispatchTabRemovedEvent(numericTabId: Int) {
        broadcastEventToExtensions { extId ->
            "if (window.__jarvisDispatchTabRemoved) { window.__jarvisDispatchTabRemoved(${JSONObject.quote(extId)}, $numericTabId); }"
        }
    }

    fun dispatchWebNavigationEvent(eventName: String, tabId: String, url: String) {
        if (url.isBlank() || url == "about:blank") return
        val numId = numericIdFor(tabId)
        val details = JSONObject().apply {
            put("tabId", numId)
            put("url", url)
            put("frameId", 0)
            put("parentFrameId", -1)
            put("timeStamp", System.currentTimeMillis())
        }.toString()
        broadcastEventToExtensions { extId ->
            "if (window.__jarvisDispatchWebNavigation) { window.__jarvisDispatchWebNavigation(${JSONObject.quote(extId)}, ${JSONObject.quote(eventName)}, $details); }"
        }
    }

    fun registerUiSurface(extId: String, webView: WebView) {
        if (extId.isBlank()) return
        uiSurfaces.getOrPut(extId) { CopyOnWriteArraySet() }.add(webView)
    }

    fun unregisterUiSurface(extId: String, webView: WebView?) {
        if (extId.isBlank() || webView == null) return
        uiSurfaces[extId]?.remove(webView)
        cleanupPortsForWebView(webView)
    }

    private fun cleanupPortsForWebView(webView: WebView?) {
        if (webView == null) return
        val iter = activePorts.entries.iterator()
        while (iter.hasNext()) {
            val (_, session) = iter.next()
            session.serverWebViews.remove(webView)
            if (session.clientWebView === webView || (session.serverWebViews.isEmpty() && session.serverExtId.isNullOrBlank())) {
                iter.remove()
            }
        }
    }

    internal fun activeUiSurfaceCount(extId: String): Int = uiSurfaces[extId]?.size ?: 0

    private fun activeExtensionWebViewsFor(extId: String, exclude: WebView? = null): List<WebView> {
        return buildList {
            liveTabs.values.filter { it.isExtensionPage && it.webView != null }.mapNotNullTo(this) { it.webView }
            uiSurfaces[extId]?.let { addAll(it) }
            offscreenWebViews[extId]?.let { add(it) }
        }.distinct().filter { it !== exclude }
    }

    private fun dispatchStorageChangedEvent(extId: String, area: String, changesJson: String) {
        val js = "if (window.__jarvisDispatchStorageChanged) { window.__jarvisDispatchStorageChanged(${JSONObject.quote(extId)}, $changesJson, ${JSONObject.quote(area)}); }"
        BrowserExtensionManager.getWorkerHost(extId)?.evalJsFireAndForget(js)
        val matchingUiAndOffscreen = activeExtensionWebViewsFor(extId)
        if (matchingUiAndOffscreen.isNotEmpty()) {
            mainHandler.post {
                matchingUiAndOffscreen.forEach { wv -> wv.evaluateJavascript(js, null) }
            }
        }
        val contentWebViews = pageContentWebViews.values.toList()
        if (contentWebViews.isNotEmpty()) {
            mainHandler.post {
                contentWebViews.forEach { wv -> wv.evaluateJavascript(js, null) }
            }
        }
    }

    private fun broadcastEventToExtensions(jsForExtension: (String) -> String) {
        val enabledExts = BrowserExtensionManager.extensions.value.filter { it.isEnabled }
        if (enabledExts.isEmpty()) return
        for (ext in enabledExts) {
            val js = jsForExtension(ext.id)
            BrowserExtensionManager.getWorkerHost(ext.id)?.evalJsFireAndForget(js)
            val targets = activeExtensionWebViewsFor(ext.id)
            if (targets.isNotEmpty()) {
                mainHandler.post {
                    targets.forEach { wv -> wv.evaluateJavascript(js, null) }
                }
            }
        }
    }

    private fun buildTabJson(rec: LiveTabRecord, activeFlag: Boolean): JSONObject {
        return JSONObject().apply {
            put("id", rec.numericId)
            put("index", (rec.numericId - 1).coerceAtLeast(0))
            put("windowId", 1)
            put("url", rec.url)
            put("title", rec.title)
            put("active", activeFlag)
            put("incognito", rec.isPrivate)
            put("status", rec.status)
        }
    }

    fun rewriteExtensionAssetReferences(ext: BrowserExtension, content: String): String {
        if (content.isBlank()) return content
        val origin = ext.extensionOrigin()
        var out = BrowserExtension.resolveLocalizedTextForExtension(ext, content)
        out = out.replace("chrome-extension://__MSG_@@extension_id__/", "$origin/")
        out = out.replace("chrome-extension://${ext.id}/", "$origin/")
        out = out.replace("chrome-extension://${ext.safeHostId()}/", "$origin/")
        out = out.replace(Regex("chrome-extension://([a-z0-9-]{3,})/")) { m ->
            val id = m.groupValues[1].lowercase().replace(Regex("[^a-z0-9-]"), "-")
            if (id == ext.safeHostId()) "$origin/" else "https://ext-$id.jarvis.invalid/"
        }
        return out
    }

    fun findTabByNumericId(numericId: Int): LiveTabRecord? {
        return liveTabs.values.firstOrNull { it.numericId == numericId }
            ?: liveTabs.values.firstOrNull { it.tabId == lastActiveWebTabId }
            ?: liveTabs.values.firstOrNull { !it.isExtensionPage }
            ?: liveTabs.values.firstOrNull()
    }

    private val HTTPS_EXT_URL_REGEX = Regex(
        "^https?://ext-([^/?#]+)\\.jarvis\\.invalid(?:/([^?#]*))?",
        RegexOption.IGNORE_CASE
    )
    private val CHROME_EXT_URL_REGEX = Regex(
        "^chrome-extension://([^/?#]+)(?:/([^?#]*))?",
        RegexOption.IGNORE_CASE
    )
    private val CSP_META_REGEX = Regex(
        "<meta[^>]*http-equiv\\s*=\\s*[\"']?Content-Security-Policy[\"']?[^>]*>",
        RegexOption.IGNORE_CASE
    )
    private val VIEWPORT_META_REGEX = Regex(
        "<meta\\b[^>]*\\bname\\s*=\\s*[\"']?viewport[\"']?[^>]*>",
        RegexOption.IGNORE_CASE
    )

    /**
     * Extracts extension ID & relative path from either:
     * - `https://ext-<id>.jarvis.invalid/<path>`
     * - `chrome-extension://<id>/<path>`
     * Uses direct Regex matching so even IDs containing underscores or dots never fail `Uri.host`.
     */
    fun parseExtensionResourceUrl(rawUrl: String?): Pair<String, String>? {
        if (rawUrl.isNullOrBlank()) return null
        val trimmed = rawUrl.trim()
        HTTPS_EXT_URL_REGEX.find(trimmed)?.let { m ->
            val extId = m.groupValues[1].lowercase()
            val relPath = m.groupValues.getOrNull(2).orEmpty().trimStart('/')
            return extId to relPath
        }
        CHROME_EXT_URL_REGEX.find(trimmed)?.let { m ->
            val extId = m.groupValues[1].lowercase()
            val relPath = m.groupValues.getOrNull(2).orEmpty().trimStart('/')
            return extId to relPath
        }
        return null
    }

    /**
     * Serves files directly from the extension's directory via `WebViewClient.shouldInterceptRequest`.
     * Automatically strips restrictive CSP meta tags and injects `window.chrome` polyfill into the top of `<head>`
     * so scripts and ES modules inside `popup.html` / `sidepanel.html` have `chrome.*` ready at line 1.
     */
    fun interceptExtensionRequest(
        context: Context,
        request: WebResourceRequest?,
        defaultTabNumericId: Int = 1
    ): WebResourceResponse? {
        val rawUrl = request?.url?.toString() ?: return null
        val (extId, relPathRaw) = parseExtensionResourceUrl(rawUrl) ?: return null
        val normReqId = extId.lowercase().replace(Regex("[^a-z0-9-]"), "-")
        val ext = BrowserExtensionManager.extensions.value.firstOrNull {
            it.id.equals(extId, ignoreCase = true) ||
                it.safeHostId().equals(normReqId, ignoreCase = true)
        } ?: return null

        val decodedPath = Uri.decode(relPathRaw)
            .substringBefore("?")
            .substringBefore("#")
            .trimStart('/')
        val cleanPath = decodedPath.ifBlank { ext.uiEntryPage.ifBlank { "__jarvis_ext_dashboard.html" } }

        val corsHeaders = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, PUT, DELETE, OPTIONS",
            "Access-Control-Allow-Headers" to "*"
        )

        // 1. Same-origin external polyfill script (`/ __jarvis_chrome_polyfill.js`)
        if (cleanPath == "__jarvis_chrome_polyfill.js" || cleanPath.endsWith("/__jarvis_chrome_polyfill.js")) {
            val js = buildUnifiedChromePolyfillJs(ext, "ui", defaultTabNumericId)
            return WebResourceResponse(
                "application/javascript",
                "UTF-8",
                200,
                "OK",
                corsHeaders,
                ByteArrayInputStream(js.toByteArray(Charsets.UTF_8))
            )
        }

        if (cleanPath == "__jarvis_worker_polyfill.js") {
            val js = buildUnifiedChromePolyfillJs(ext, "worker", defaultTabNumericId)
            return WebResourceResponse(
                "application/javascript",
                "UTF-8",
                200,
                "OK",
                corsHeaders,
                ByteArrayInputStream(js.toByteArray(Charsets.UTF_8))
            )
        }

        // 2. Virtual service worker host HTML (`/__jarvis_worker_host.html`)
        if (cleanPath == "__jarvis_worker_host.html") {
            val workerTag = buildString {
                if (ext.backgroundScripts.isNotEmpty()) {
                    for (scriptRel in ext.backgroundScripts) {
                        val safeSrc = scriptRel.removePrefix("/")
                        if (ext.backgroundIsModule) {
                            append("<script type=\"module\" src=\"/$safeSrc\"></script>\n")
                        } else {
                            append("<script src=\"/$safeSrc\"></script>\n")
                        }
                    }
                } else if (ext.serviceWorkerFile.isNotBlank() && ext.serviceWorkerFile != ext.backgroundPage) {
                    val safeSrc = ext.serviceWorkerFile.removePrefix("/")
                    if (ext.backgroundIsModule) {
                        append("<script type=\"module\" src=\"/$safeSrc\"></script>")
                    } else {
                        append("<script src=\"/$safeSrc\"></script>")
                    }
                }
            }
            val html = """
                <!DOCTYPE html>
                <html><head><meta charset="utf-8">
                <script src="/__jarvis_worker_polyfill.js"></script>
                <script>${buildUnifiedChromePolyfillJs(ext, "worker", defaultTabNumericId)}</script>
                </head><body>$workerTag</body></html>
            """.trimIndent()
            return WebResourceResponse(
                "text/html",
                "UTF-8",
                200,
                "OK",
                corsHeaders,
                ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
            )
        }

        // 3. Virtual Interactive Extension Dashboard (`/__jarvis_ext_dashboard.html`)
        if (cleanPath == "__jarvis_ext_dashboard.html") {
            val dashboardHtml = buildVirtualExtensionDashboardHtml(ext, defaultTabNumericId)
            return WebResourceResponse(
                "text/html",
                "UTF-8",
                200,
                "OK",
                corsHeaders,
                ByteArrayInputStream(dashboardHtml.toByteArray(Charsets.UTF_8))
            )
        }

        val rootDir = File(ext.dirPath).canonicalFile
        var targetFile = File(rootDir, cleanPath).canonicalFile
        if (!targetFile.path.startsWith(rootDir.path) || !targetFile.exists() || !targetFile.isFile) {
            // Try matching by filename inside extension directory if relative path had a prefix difference
            val fallbackName = cleanPath.substringAfterLast('/')
            val found = if (fallbackName.isNotBlank()) {
                runCatching {
                    rootDir.walkTopDown().maxDepth(3).firstOrNull {
                        it.isFile && it.name.equals(fallbackName, ignoreCase = true)
                    }
                }.getOrNull()
            } else null

            if (found != null) {
                targetFile = found.canonicalFile
            } else if (cleanPath.endsWith(".html", ignoreCase = true) || cleanPath.endsWith(".htm", ignoreCase = true)) {
                val dashboardHtml = buildVirtualExtensionDashboardHtml(ext, defaultTabNumericId)
                return WebResourceResponse(
                    "text/html",
                    "UTF-8",
                    200,
                    "OK",
                    corsHeaders,
                    ByteArrayInputStream(dashboardHtml.toByteArray(Charsets.UTF_8))
                )
            } else {
                return null
            }
        }

        val mime = guessMimeType(targetFile.name)

        if (mime == "text/html") {
            val rawHtml = runCatching { targetFile.readText() }.getOrDefault("")
            val localizedHtml = rewriteExtensionAssetReferences(ext, rawHtml)
            val strippedCspHtml = localizedHtml.replace(CSP_META_REGEX, "")
            val hasViewportMeta = VIEWPORT_META_REGEX.containsMatchIn(strippedCspHtml)
            val viewportTag = if (hasViewportMeta) {
                ""
            } else {
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
            }
            val isBgPage = ext.backgroundPage.isNotBlank() &&
                cleanPath.equals(ext.backgroundPage, ignoreCase = true)
            val ctxKind = if (isBgPage) "worker" else "ui"
            val polyUrl = if (isBgPage) "/__jarvis_worker_polyfill.js" else "/__jarvis_chrome_polyfill.js"
            val polyfillScript = "$viewportTag<script src=\"$polyUrl\"></script>\n<script>\n${buildUnifiedChromePolyfillJs(ext, ctxKind, defaultTabNumericId)}\n</script>"
            val patchedHtml = injectScriptIntoHead(strippedCspHtml, polyfillScript)
            return WebResourceResponse(
                "text/html",
                "UTF-8",
                200,
                "OK",
                corsHeaders,
                ByteArrayInputStream(patchedHtml.toByteArray(Charsets.UTF_8))
            )
        }

        if (mime == "text/css") {
            val rawCss = runCatching { targetFile.readText() }.getOrDefault("")
            val patchedCss = rewriteExtensionAssetReferences(ext, rawCss)
            return WebResourceResponse(
                "text/css",
                "UTF-8",
                200,
                "OK",
                corsHeaders,
                ByteArrayInputStream(patchedCss.toByteArray(Charsets.UTF_8))
            )
        }

        return WebResourceResponse(
            mime,
            if (mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json")) "UTF-8" else null,
            200,
            "OK",
            corsHeaders,
            FileInputStream(targetFile)
        )
    }

    private fun buildVirtualExtensionDashboardHtml(ext: BrowserExtension, defaultTabNumericId: Int): String {
        val polyfill = buildUnifiedChromePolyfillJs(ext, "ui", defaultTabNumericId)
        val csSummary = if (ext.contentScripts.isEmpty()) {
            "No static content_scripts (uses background/action/scripting)"
        } else {
            ext.contentScripts.flatMap { it.matches }.distinct().joinToString(", ")
        }
        val jsFilesList = ext.contentScripts.flatMap { it.js }.distinct()
        val jsFilesJson = JSONArray(jsFilesList).toString()

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
                <title>${ext.name}</title>
                <script src="/__jarvis_chrome_polyfill.js"></script>
                <script>$polyfill</script>
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
                    body { background: #101218; color: #F5F7FF; padding: 16px; }
                    .card { background: #1A1D26; border: 1px solid #2A2F3D; border-radius: 16px; padding: 16px; margin-bottom: 14px; }
                    .title { font-size: 18px; font-weight: 800; color: #FFFFFF; margin-bottom: 4px; }
                    .sub { font-size: 12px; color: #9DA5B4; margin-bottom: 10px; }
                    .badge { display: inline-block; background: rgba(255,107,87,0.16); color: #FF6B57; font-size: 11px; font-weight: 700; padding: 4px 10px; border-radius: 999px; margin-right: 6px; }
                    .btn { display: block; width: 100%; background: #FF6B57; color: #fff; font-weight: 700; font-size: 14px; border: none; border-radius: 12px; padding: 13px; margin-top: 10px; cursor: pointer; text-align: center; }
                    .btn.secondary { background: #252A38; color: #63B3ED; border: 1px solid #343B4F; }
                    .label { font-size: 11px; font-weight: 700; color: #9DA5B4; text-transform: uppercase; letter-spacing: 0.6px; margin-bottom: 6px; }
                    pre { background: #0B0D12; border: 1px solid #242938; border-radius: 10px; padding: 10px; color: #34D399; font-family: monospace; font-size: 11.5px; overflow-x: auto; white-space: pre-wrap; max-height: 180px; overflow-y: auto; }
                    input, textarea { width: 100%; background: #0B0D12; border: 1px solid #2A2F3D; border-radius: 10px; color: #fff; padding: 10px; font-size: 12.5px; margin-top: 6px; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="title">${ext.name}</div>
                    <div class="sub">v${ext.version} • ID: ${ext.id}</div>
                    <div>
                        <span class="badge">MV${ext.manifestVersion}</span>
                        <span class="badge">Active</span>
                    </div>
                    <p style="font-size:13px;color:#CBD5E1;margin-top:10px;">${ext.description.ifBlank { "Chrome Extension active in Agentra Browser." }}</p>
                    <button class="btn" id="triggerActionBtn">⚡ Trigger Extension Action on Active Tab</button>
                    <button class="btn secondary" id="injectScriptsBtn">🚀 Run Extension Scripts on Current Webpage</button>
                </div>

                <div class="card">
                    <div class="label">Active Target Webpage</div>
                    <div id="activeTabInfo" style="font-size:13px;color:#63B3ED;word-break:break-all;">Checking active tab...</div>
                    <div style="font-size:11.5px;color:#9DA5B4;margin-top:6px;">Matches: $csSummary</div>
                </div>

                <div class="card">
                    <div class="label">Send Message to Background Worker</div>
                    <input id="msgInput" value='{"action":"ping","source":"popup"}' />
                    <button class="btn secondary" id="sendMsgBtn">Send chrome.runtime.sendMessage</button>
                    <div class="label" style="margin-top:12px;">Response / Live Console</div>
                    <pre id="outputBox">Ready.</pre>
                </div>

                <div class="card">
                    <div class="label">chrome.storage.local Inspector</div>
                    <pre id="storageBox">{}</pre>
                </div>

                <script>
                    var jsFiles = $jsFilesJson;
                    var out = document.getElementById('outputBox');
                    function log(msg) {
                        out.textContent = '[' + new Date().toLocaleTimeString() + '] ' + msg + '\n' + out.textContent;
                    }
                    function refreshState() {
                        chrome.tabs.query({ active: true }, function(tabs) {
                            var t = (tabs && tabs[0]) ? tabs[0] : null;
                            document.getElementById('activeTabInfo').textContent = t ? (t.title + ' — ' + t.url + ' (Tab #' + t.id + ')') : 'No active web tab';
                        });
                        chrome.storage.local.get(null, function(items) {
                            document.getElementById('storageBox').textContent = JSON.stringify(items || {}, null, 2);
                        });
                    }
                    document.getElementById('triggerActionBtn').addEventListener('click', function() {
                        if (window.__jarvis_chrome_bridge && window.__jarvis_chrome_bridge.triggerExtensionAction) {
                            window.__jarvis_chrome_bridge.triggerExtensionAction(${JSONObject.quote(ext.id)});
                            log('Dispatched chrome.action.onClicked to background worker.');
                        }
                        chrome.runtime.sendMessage({ type: 'ACTION_CLICKED', action: 'open' }, function(res) {
                            log('Worker reply: ' + JSON.stringify(res));
                            refreshState();
                        });
                    });
                    document.getElementById('injectScriptsBtn').addEventListener('click', function() {
                        chrome.tabs.query({ active: true }, function(tabs) {
                            var t = (tabs && tabs[0]) ? tabs[0] : null;
                            if (!t) { log('Open a webpage tab first.'); return; }
                            if (jsFiles.length > 0) {
                                chrome.scripting.executeScript({ target: { tabId: t.id }, files: jsFiles }, function(res) {
                                    log('Injected ' + jsFiles.join(', ') + ' into Tab #' + t.id);
                                });
                            } else {
                                log('Triggered extension on Tab #' + t.id + ' (' + t.url + ')');
                            }
                        });
                    });
                    document.getElementById('sendMsgBtn').addEventListener('click', function() {
                        var raw = document.getElementById('msgInput').value;
                        var parsed = raw;
                        try { parsed = JSON.parse(raw); } catch(e) {}
                        chrome.runtime.sendMessage(parsed, function(res) {
                            log('Response: ' + JSON.stringify(res));
                            refreshState();
                        });
                    });
                    refreshState();
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun injectScriptIntoHead(html: String, scriptTag: String): String {
        val headRegex = Regex("<head[^>]*>", RegexOption.IGNORE_CASE)
        val match = headRegex.find(html)
        return if (match != null) {
            html.substring(0, match.range.last + 1) + "\n$scriptTag\n" + html.substring(match.range.last + 1)
        } else {
            val htmlRegex = Regex("<html[^>]*>", RegexOption.IGNORE_CASE)
            val htmlMatch = htmlRegex.find(html)
            if (htmlMatch != null) {
                html.substring(0, htmlMatch.range.last + 1) + "\n<head>$scriptTag</head>\n" + html.substring(htmlMatch.range.last + 1)
            } else {
                "$scriptTag\n$html"
            }
        }
    }

    private fun guessMimeType(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html"
            lower.endsWith(".js") || lower.endsWith(".mjs") -> "application/javascript"
            lower.endsWith(".css") -> "text/css"
            lower.endsWith(".json") -> "application/json"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".svg") -> "image/svg+xml"
            lower.endsWith(".woff2") -> "font/woff2"
            lower.endsWith(".woff") -> "font/woff"
            lower.endsWith(".ttf") -> "font/ttf"
            lower.endsWith(".wasm") -> "application/wasm"
            else -> URLConnection.guessContentTypeFromName(fileName) ?: "application/octet-stream"
        }
    }

    /**
     * Generates full dual Promise + Callback `window.chrome` polyfill used across Extension UI Tabs,
     * Background Service Workers, and Content Scripts.
     */
    fun buildUnifiedChromePolyfillJs(
        ext: BrowserExtension,
        contextType: String, // "worker" | "ui" | "content"
        tabNumericId: Int
    ): String {
        val baseOrigin = ext.extensionOrigin()
        val extDir = File(ext.dirPath)
        val manifestJson = runCatching { File(extDir, "manifest.json").readText() }.getOrDefault("{}")
        val safeManifest = try { JSONObject(manifestJson).toString() } catch (_: Exception) { "{}" }
        val localeMessagesJson = runCatching {
            BrowserExtension.loadLocaleMessages(extDir, ext.defaultLocale).toString()
        }.getOrDefault("{}")
        val actionTitleDefault = JSONObject.quote(ext.actionTitle.ifBlank { ext.name })
        val popupDefault = JSONObject.quote(ext.popupFile)

        return """
        (function() {
            if (window.__jarvisPolyfillLoaded_${ext.safeHostId().replace('-', '_')} && window.chrome && window.chrome.runtime) {
                return;
            }
            window.__jarvisPolyfillLoaded_${ext.safeHostId().replace('-', '_')} = true;
            if (typeof window.self === 'undefined') { window.self = window; }
            if (typeof window.global === 'undefined') { window.global = window; }
            window.__jarvisCtxType = ${JSONObject.quote(contextType)};
            window.__jarvisTabId = $tabNumericId;
            window.__jarvisExtCbs = window.__jarvisExtCbs || {};

            function resolveExtUrl(rawUrl, fallbackBase) {
                var s = String(rawUrl || '');
                if (!s) return s;
                if (s.indexOf('chrome-extension://__MSG_@@extension_id__/') === 0) {
                    return ${JSONObject.quote(baseOrigin)} + '/' + s.substring('chrome-extension://__MSG_@@extension_id__/'.length);
                }
                if (s.indexOf('chrome-extension://') === 0) {
                    var rest = s.substring('chrome-extension://'.length);
                    var slashIdx = rest.indexOf('/');
                    var hostId = slashIdx >= 0 ? rest.substring(0, slashIdx) : rest;
                    var pathPart = slashIdx >= 0 ? rest.substring(slashIdx + 1) : '';
                    var normHost = hostId.toLowerCase().replace(/[^a-z0-9-]/g, '-');
                    var targetOrigin = (normHost === ${JSONObject.quote(ext.safeHostId())}) ? ${JSONObject.quote(baseOrigin)} : ('https://ext-' + normHost + '.jarvis.invalid');
                    return targetOrigin + '/' + pathPart;
                }
                if (fallbackBase && s.indexOf('://') === -1 && s.indexOf('data:') !== 0 && s.indexOf('blob:') !== 0) {
                    return fallbackBase + '/' + s.replace(/^\/+/, '');
                }
                return s;
            }

            if (window.__jarvisCtxType === 'worker') {
                if (typeof window.importScripts !== 'function') {
                    window.importScripts = function() {
                        for (var i = 0; i < arguments.length; i++) {
                            var targetUrl = resolveExtUrl(String(arguments[i] || ''), ${JSONObject.quote(baseOrigin)});
                            try {
                                var xhr = new XMLHttpRequest();
                                xhr.open('GET', targetUrl, false);
                                xhr.send(null);
                                if (xhr.status >= 200 && xhr.status < 400 && xhr.responseText) {
                                    (0, eval)(xhr.responseText + '\n//# sourceURL=' + targetUrl);
                                }
                            } catch (err) {
                                console.error('[JarvisExt] importScripts failed for ' + targetUrl, err);
                            }
                        }
                    };
                }
                if (typeof window.skipWaiting !== 'function') {
                    window.skipWaiting = function() { return Promise.resolve(); };
                }
                if (typeof window.clients === 'undefined') {
                    window.clients = {
                        matchAll: function() { return Promise.resolve([]); },
                        openWindow: function(u) {
                            if (window.__jarvis_chrome_bridge) {
                                window.__jarvis_chrome_bridge.tabsCreate(String(u || 'about:blank'), true);
                            }
                            return Promise.resolve(null);
                        },
                        claim: function() { return Promise.resolve(); }
                    };
                }
                if (typeof window.registration === 'undefined') {
                    window.registration = { active: { state: 'activated' }, scope: ${JSONObject.quote(baseOrigin)} + '/' };
                }
            }

            // Rewrite chrome-extension://<id>/... AND route host-permitted cross-origin fetch()
            // calls in extension UI/worker contexts without WebView CORS blocks.
            var origFetch = window.fetch;
            if (typeof origFetch === 'function' && !window.__jarvisFetchPatched) {
                window.__jarvisFetchPatched = true;
                function __jStrToB64(str) {
                    try { return btoa(unescape(encodeURIComponent(str))); } catch (e) { return ''; }
                }
                function __jBytesToB64(buf) {
                    var bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
                    var chunks = [];
                    for (var i = 0; i < bytes.length; i += 8192) {
                        chunks.push(String.fromCharCode.apply(null, bytes.subarray(i, Math.min(i + 8192, bytes.length))));
                    }
                    return btoa(chunks.join(''));
                }
                function __jB64ToBytes(b64) {
                    var bin = atob(b64 || '');
                    var out = new Uint8Array(bin.length);
                    for (var i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
                    return out;
                }
                function __jEncodeBody(rawBody, hdrs) {
                    if (rawBody === undefined || rawBody === null) return Promise.resolve({ hasBody: false, b64: '' });
                    if (typeof rawBody === 'string') return Promise.resolve({ hasBody: true, b64: __jStrToB64(rawBody) });
                    if (typeof URLSearchParams !== 'undefined' && rawBody instanceof URLSearchParams) {
                        if (!hdrs['content-type'] && !hdrs['Content-Type']) {
                            hdrs['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
                        }
                        return Promise.resolve({ hasBody: true, b64: __jStrToB64(rawBody.toString()) });
                    }
                    if (rawBody instanceof ArrayBuffer || ArrayBuffer.isView(rawBody)) {
                        var view = rawBody instanceof ArrayBuffer ? new Uint8Array(rawBody) : new Uint8Array(rawBody.buffer, rawBody.byteOffset, rawBody.byteLength);
                        return Promise.resolve({ hasBody: true, b64: __jBytesToB64(view) });
                    }
                    if (typeof Blob !== 'undefined' && rawBody instanceof Blob && typeof rawBody.arrayBuffer === 'function') {
                        if (rawBody.type && !hdrs['content-type'] && !hdrs['Content-Type']) {
                            hdrs['Content-Type'] = rawBody.type;
                        }
                        return rawBody.arrayBuffer().then(function(buf) {
                            return { hasBody: true, b64: __jBytesToB64(buf) };
                        });
                    }
                    return Promise.resolve(null);
                }
                window.fetch = function(input, init) {
                    var origInput = input;
                    if (typeof input === 'string') {
                        input = resolveExtUrl(input);
                    } else if (typeof URL !== 'undefined' && input instanceof URL) {
                        input = resolveExtUrl(input.href);
                    } else if (input && typeof input === 'object' && typeof input.url === 'string') {
                        var rewrittenUrl = resolveExtUrl(input.url);
                        if (rewrittenUrl !== input.url && typeof Request === 'function') {
                            try {
                                input = new Request(rewrittenUrl, {
                                    method: input.method,
                                    headers: input.headers,
                                    mode: input.mode,
                                    credentials: input.credentials,
                                    cache: input.cache,
                                    redirect: input.redirect,
                                    referrer: input.referrer
                                });
                            } catch (e) {
                                input = rewrittenUrl;
                            }
                        }
                    }
                    var B = window.__jarvis_chrome_bridge;
                    var rawUrl = (typeof input === 'string') ? input : (input && input.url ? String(input.url) : '');
                    var absUrl = rawUrl;
                    try { absUrl = new URL(rawUrl, location.href).href; } catch (e) {}
                    var reqMode = (init && init.mode) || (input && typeof input === 'object' && input.mode) || '';
                    if (B && B.canHostFetch && B.hostFetch && window.__jarvisCtxType !== 'content' &&
                        reqMode !== 'no-cors' && /^https?:\/\//i.test(absUrl) &&
                        absUrl.indexOf(${JSONObject.quote(baseOrigin)} + '/') !== 0 &&
                        absUrl !== ${JSONObject.quote(baseOrigin)} &&
                        B.canHostFetch(${JSONObject.quote(ext.id)}, absUrl)) {
                        var reqMethod = ((init && init.method) || (input && input.method) || 'GET').toUpperCase();
                        var hdrObj = {};
                        var addHdrs = function(src) {
                            if (!src) return;
                            if (typeof Headers !== 'undefined' && src instanceof Headers) {
                                src.forEach(function(v, k) { hdrObj[k] = v; });
                            } else if (Array.isArray(src)) {
                                src.forEach(function(pair) { if (pair && pair.length >= 2) hdrObj[pair[0]] = pair[1]; });
                            } else if (typeof src === 'object') {
                                Object.keys(src).forEach(function(k) { hdrObj[k] = src[k]; });
                            }
                        };
                        if (input && typeof input === 'object' && input.headers) addHdrs(input.headers);
                        if (init && init.headers) addHdrs(init.headers);
                        var rawBody = (init && init.body !== undefined) ? init.body : null;
                        var creds = (init && init.credentials) || (input && input.credentials) || 'include';
                        return __jEncodeBody(rawBody, hdrObj).then(function(enc) {
                            if (!enc) return origFetch.call(window, origInput, init);
                            return new Promise(function(resolve, reject) {
                                var cid = 'hf_' + Math.random().toString(36).slice(2) + '_' + Date.now();
                                window.__jarvisExtCbs = window.__jarvisExtCbs || {};
                                window.__jarvisExtCbs[cid] = function(res) {
                                    if (!res || res.error) {
                                        reject(new TypeError((res && res.error) || 'Failed to fetch'));
                                        return;
                                    }
                                    try {
                                        var hdrs = new Headers();
                                        if (Array.isArray(res.headers)) {
                                            res.headers.forEach(function(pair) {
                                                try { if (pair && pair.length >= 2) hdrs.append(pair[0], pair[1]); } catch (e) {}
                                            });
                                        }
                                        var noBody = (res.status === 204 || res.status === 205 || res.status === 304);
                                        var bytes = noBody ? null : __jB64ToBytes(res.bodyB64 || '');
                                        var resp = new Response(bytes, {
                                            status: res.status || 200,
                                            statusText: res.statusText || '',
                                            headers: hdrs
                                        });
                                        if (res.url) {
                                            try { Object.defineProperty(resp, 'url', { value: res.url }); } catch (e) {}
                                        }
                                        resolve(resp);
                                    } catch (e) {
                                        reject(e);
                                    }
                                };
                                B.hostFetch(${JSONObject.quote(ext.id)}, absUrl, reqMethod, JSON.stringify(hdrObj), enc.b64, enc.hasBody, creds, cid);
                            });
                        });
                    }
                    return origFetch.call(this, input, init);
                };
            }
            if (window.XMLHttpRequest && !window.__jarvisXhrPatched) {
                window.__jarvisXhrPatched = true;
                var origOpen = window.XMLHttpRequest.prototype.open;
                window.XMLHttpRequest.prototype.open = function(method, url) {
                    if (url !== undefined && url !== null) {
                        arguments[1] = resolveExtUrl(String(url));
                    }
                    return origOpen.apply(this, arguments);
                };
            }

            window.__jarvisMakeChromeApi = function(extId, originBase, manifestObj) {
                var B = window.__jarvis_chrome_bridge;
                var localeMessages = $localeMessagesJson;
                var listeners = (window.__jarvisMsgListeners = window.__jarvisMsgListeners || {});
                listeners[extId] = listeners[extId] || [];
                var storageListeners = (window.__jarvisStorageListeners = window.__jarvisStorageListeners || {});
                storageListeners[extId] = storageListeners[extId] || [];
                var installedListeners = (window.__jarvisInstalledListeners = window.__jarvisInstalledListeners || {});
                installedListeners[extId] = installedListeners[extId] || [];
                var startupListeners = (window.__jarvisStartupListeners = window.__jarvisStartupListeners || {});
                startupListeners[extId] = startupListeners[extId] || [];
                var connectListeners = (window.__jarvisConnectListeners = window.__jarvisConnectListeners || {});
                connectListeners[extId] = connectListeners[extId] || [];
                var tabUpdatedListeners = (window.__jarvisTabUpdatedListeners = window.__jarvisTabUpdatedListeners || {});
                tabUpdatedListeners[extId] = tabUpdatedListeners[extId] || [];
                var tabActivatedListeners = (window.__jarvisTabActivatedListeners = window.__jarvisTabActivatedListeners || {});
                tabActivatedListeners[extId] = tabActivatedListeners[extId] || [];
                var tabCreatedListeners = (window.__jarvisTabCreatedListeners = window.__jarvisTabCreatedListeners || {});
                tabCreatedListeners[extId] = tabCreatedListeners[extId] || [];
                var tabRemovedListeners = (window.__jarvisTabRemovedListeners = window.__jarvisTabRemovedListeners || {});
                tabRemovedListeners[extId] = tabRemovedListeners[extId] || [];
                var actionClickedListeners = (window.__jarvisActionClickedListeners = window.__jarvisActionClickedListeners || {});
                actionClickedListeners[extId] = actionClickedListeners[extId] || [];
                var alarmListeners = (window.__jarvisAlarmListeners = window.__jarvisAlarmListeners || {});
                alarmListeners[extId] = alarmListeners[extId] || [];
                var contextMenuListeners = (window.__jarvisContextMenuListeners = window.__jarvisContextMenuListeners || {});
                contextMenuListeners[extId] = contextMenuListeners[extId] || [];
                var webNavListeners = (window.__jarvisWebNavListeners = window.__jarvisWebNavListeners || {});
                webNavListeners[extId] = webNavListeners[extId] || {
                    onBeforeNavigate: [],
                    onCommitted: [],
                    onDOMContentLoaded: [],
                    onCompleted: [],
                    onHistoryStateUpdated: []
                };

                function makeEventList(arr) {
                    return {
                        addListener: function(fn) { if (typeof fn === 'function' && arr.indexOf(fn) === -1) arr.push(fn); },
                        removeListener: function(fn) {
                            var idx = arr.indexOf(fn);
                            if (idx !== -1) arr.splice(idx, 1);
                        },
                        hasListener: function(fn) { return arr.indexOf(fn) !== -1; },
                        hasListeners: function() { return arr.length > 0; }
                    };
                }

                function makeEventStub() {
                    return makeEventList([]);
                }

                function resolvePromiseOrCb(val, cb) {
                    if (typeof cb === 'function') {
                        setTimeout(function() { try { cb(val); } catch(e) { console.warn('[JarvisExt] cb err', e); } }, 0);
                    }
                    return Promise.resolve(val);
                }
                function nextCbId(cb) {
                    var cid = 'cb_' + Math.random().toString(36).slice(2) + '_' + Date.now();
                    if (typeof cb === 'function') { window.__jarvisExtCbs[cid] = cb; }
                    return cid;
                }
                function makeStorageArea(areaName) {
                    return {
                        get: function(keys, cb) {
                            if (typeof keys === 'function') { cb = keys; keys = null; }
                            var out = {};
                            try {
                                var raw = B ? B.storageGet(extId, areaName) : '{}';
                                var all = JSON.parse(raw || '{}');
                                if (keys === null || keys === undefined) {
                                    out = all;
                                } else if (typeof keys === 'string') {
                                    if (all[keys] !== undefined) out[keys] = all[keys];
                                } else if (Array.isArray(keys)) {
                                    keys.forEach(function(k) { if (all[k] !== undefined) out[k] = all[k]; });
                                } else if (typeof keys === 'object') {
                                    Object.keys(keys).forEach(function(k) {
                                        out[k] = (all[k] !== undefined) ? all[k] : keys[k];
                                    });
                                }
                            } catch(e) {}
                            return resolvePromiseOrCb(out, cb);
                        },
                        set: function(items, cb) {
                            try {
                                if (B) B.storageSet(extId, areaName, JSON.stringify(items || {}));
                            } catch(e) {}
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        remove: function(keys, cb) {
                            var list = Array.isArray(keys) ? keys : [String(keys)];
                            try { if (B) B.storageRemove(extId, areaName, JSON.stringify(list)); } catch(e) {}
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        clear: function(cb) {
                            try { if (B) B.storageClear(extId, areaName); } catch(e) {}
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        getBytesInUse: function(keys, cb) {
                            if (typeof keys === 'function') { cb = keys; }
                            return resolvePromiseOrCb(0, cb);
                        }
                    };
                }
                var localArea = makeStorageArea('local');
                var api = {
                    runtime: {
                        id: extId,
                        lastError: null,
                        getManifest: function() { return manifestObj; },
                        getURL: function(p) {
                            var clean = String(p || '').replace(/^\/+/, '');
                            return originBase + '/' + clean;
                        },
                        getPlatformInfo: function(cb) {
                            return resolvePromiseOrCb({ os: 'android', arch: 'arm64', nacl_arch: 'arm' }, cb);
                        },
                        sendMessage: function(extOrMsg, msgOrOpt, optOrCb, maybeCb) {
                            var msg = (typeof extOrMsg === 'string') ? msgOrOpt : extOrMsg;
                            var cb = [msgOrOpt, optOrCb, maybeCb].find(function(x){ return typeof x === 'function'; });
                            return new Promise(function(resolve) {
                                var cid = nextCbId(function(res) {
                                    if (cb) { try { cb(res); } catch(e) {} }
                                    resolve(res);
                                });
                                if (B) {
                                    B.runtimeSendMessage(extId, window.__jarvisCtxType, window.__jarvisTabId, JSON.stringify(msg === undefined ? {} : msg), cid);
                                } else {
                                    if (cb) { try { cb(undefined); } catch(e) {} }
                                    resolve(undefined);
                                }
                            });
                        },
                        connect: function(connectInfo) {
                            var portName = (connectInfo && connectInfo.name) ? String(connectInfo.name) : 'default';
                            var portId = 'port_' + Math.random().toString(36).slice(2) + '_' + Date.now();
                            var portListeners = [];
                            var disconnectListeners = [];
                            var portState = { portListeners: portListeners, disconnectListeners: disconnectListeners, disconnected: false };
                            window.__jarvisPorts = window.__jarvisPorts || {};
                            window.__jarvisPorts[portId] = portState;
                            var clientPort = {
                                name: portName,
                                postMessage: function(msg) {
                                    if (portState.disconnected) return;
                                    if (B && B.portPostMessage) {
                                        B.portPostMessage(portId, true, JSON.stringify(msg === undefined ? null : msg));
                                    }
                                },
                                onMessage: makeEventList(portListeners),
                                onDisconnect: makeEventList(disconnectListeners),
                                disconnect: function() {
                                    if (portState.disconnected) return;
                                    portState.disconnected = true;
                                    delete window.__jarvisPorts[portId];
                                    if (B && B.portDisconnect) B.portDisconnect(portId, true);
                                    disconnectListeners.forEach(function(fn) { try { fn(clientPort); } catch(e) {} });
                                }
                            };
                            if (B && B.runtimeConnectOpen) {
                                B.runtimeConnectOpen(extId, window.__jarvisCtxType, window.__jarvisTabId, portId, portName);
                            } else if (window.__jarvisDispatchPortConnect) {
                                setTimeout(function() {
                                    window.__jarvisDispatchPortConnect(extId, portId, portName, portListeners);
                                }, 0);
                            }
                            return clientPort;
                        },
                        onMessage: makeEventList(listeners[extId]),
                        onInstalled: makeEventList(installedListeners[extId]),
                        onStartup: makeEventList(startupListeners[extId]),
                        onSuspend: makeEventStub(),
                        onConnect: makeEventList(connectListeners[extId]),
                        onMessageExternal: makeEventStub(),
                        openOptionsPage: function(cb) {
                            var p = (manifestObj.options_ui && manifestObj.options_ui.page) || manifestObj.options_page;
                            if (p) api.tabs.create({ url: api.runtime.getURL(p) });
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        setUninstallURL: function(u, cb) { return resolvePromiseOrCb(undefined, cb); }
                    },
                    storage: {
                        local: localArea,
                        sync: localArea,
                        managed: localArea,
                        session: makeStorageArea('session'),
                        onChanged: makeEventList(storageListeners[extId])
                    },
                    tabs: {
                        query: function(queryInfo, cb) {
                            var arr = [];
                            try {
                                arr = B ? JSON.parse(B.tabsQuery(window.__jarvisCtxType, JSON.stringify(queryInfo || {}))) : [];
                            } catch(e) {}
                            return resolvePromiseOrCb(arr, cb);
                        },
                        get: function(tabId, cb) {
                            var all = B ? JSON.parse(B.tabsQuery(window.__jarvisCtxType, '{}')) : [];
                            var hit = all.find(function(t){ return t.id === tabId; }) || all[0] || { id: tabId || 1, url: 'https://www.google.com', active: true, status: 'complete' };
                            return resolvePromiseOrCb(hit, cb);
                        },
                        getCurrent: function(cb) {
                            var all = B ? JSON.parse(B.tabsQuery(window.__jarvisCtxType, '{"active":true}')) : [];
                            return resolvePromiseOrCb(all[0] || null, cb);
                        },
                        create: function(props, cb) {
                            var u = (props && props.url) ? String(props.url) : 'https://www.google.com';
                            u = resolveExtUrl(u, originBase);
                            var makeActive = (props && props.active !== undefined) ? !!props.active : true;
                            var created = B ? JSON.parse(B.tabsCreate(u, makeActive)) : { id: 1, url: u, active: makeActive };
                            return resolvePromiseOrCb(created, cb);
                        },
                        update: function(tabId, props, cb) {
                            if (typeof tabId === 'object') { cb = props; props = tabId; tabId = null; }
                            if (props && props.url) {
                                props = Object.assign({}, props, { url: resolveExtUrl(String(props.url), originBase) });
                            }
                            var res = B ? JSON.parse(B.tabsUpdate(tabId ? Number(tabId) : -1, JSON.stringify(props || {}))) : {};
                            return resolvePromiseOrCb(res, cb);
                        },
                        remove: function(tabIds, cb) {
                            var list = Array.isArray(tabIds) ? tabIds : [tabIds];
                            list.forEach(function(id) { if (B) B.tabsRemove(Number(id)); });
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        reload: function(tabId, propsOrCb, maybeCb) {
                            var cb = (typeof tabId === 'function') ? tabId : ((typeof propsOrCb === 'function') ? propsOrCb : maybeCb);
                            var tid = (typeof tabId === 'number') ? tabId : -1;
                            if (B) B.tabsReload(tid);
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        captureVisibleTab: function(winId, opts, cb) {
                            if (typeof winId === 'function') { cb = winId; }
                            else if (typeof opts === 'function') { cb = opts; }
                            var dataUrl = B ? B.tabsCaptureVisibleTab() : '';
                            return resolvePromiseOrCb(dataUrl, cb);
                        },
                        sendMessage: function(tabId, msg, optOrCb, maybeCb) {
                            var cb = (typeof optOrCb === 'function') ? optOrCb : maybeCb;
                            return new Promise(function(resolve) {
                                var cid = nextCbId(function(res) {
                                    if (cb) { try { cb(res); } catch(e) {} }
                                    resolve(res);
                                });
                                if (B) {
                                    B.tabsSendMessage(extId, Number(tabId || -1), JSON.stringify(msg === undefined ? {} : msg), cid);
                                } else {
                                    if (cb) { try { cb(undefined); } catch(e) {} }
                                    resolve(undefined);
                                }
                            });
                        },
                        connect: function(tabId, connectInfo) {
                            var portName = (connectInfo && connectInfo.name) ? String(connectInfo.name) : 'default';
                            var portId = 'tport_' + Math.random().toString(36).slice(2) + '_' + Date.now();
                            var portListeners = [];
                            var disconnectListeners = [];
                            var portState = { portListeners: portListeners, disconnectListeners: disconnectListeners, disconnected: false };
                            window.__jarvisPorts = window.__jarvisPorts || {};
                            window.__jarvisPorts[portId] = portState;
                            var clientPort = {
                                name: portName,
                                postMessage: function(msg) {
                                    if (portState.disconnected) return;
                                    if (B && B.portPostMessage) {
                                        B.portPostMessage(portId, true, JSON.stringify(msg === undefined ? null : msg));
                                    }
                                },
                                onMessage: makeEventList(portListeners),
                                onDisconnect: makeEventList(disconnectListeners),
                                disconnect: function() {
                                    if (portState.disconnected) return;
                                    portState.disconnected = true;
                                    delete window.__jarvisPorts[portId];
                                    if (B && B.portDisconnect) B.portDisconnect(portId, true);
                                    disconnectListeners.forEach(function(fn) { try { fn(clientPort); } catch(e) {} });
                                }
                            };
                            if (B && B.tabsConnectOpen) {
                                B.tabsConnectOpen(extId, Number(tabId || -1), portId, portName);
                            }
                            return clientPort;
                        },
                        executeScript: function(tabId, details, cb) {
                            if (typeof tabId === 'object') { cb = details; details = tabId; tabId = -1; }
                            return api.scripting.executeScript({
                                target: { tabId: tabId || -1 },
                                code: details && details.code,
                                files: details && details.file ? [details.file] : []
                            }, cb);
                        },
                        insertCSS: function(tabId, details, cb) {
                            if (typeof tabId === 'object') { cb = details; details = tabId; tabId = -1; }
                            return api.scripting.insertCSS({
                                target: { tabId: tabId || -1 },
                                css: details && details.code,
                                files: details && details.file ? [details.file] : []
                            }, cb);
                        },
                        onUpdated: makeEventList(tabUpdatedListeners[extId]),
                        onActivated: makeEventList(tabActivatedListeners[extId]),
                        onCreated: makeEventList(tabCreatedListeners[extId]),
                        onRemoved: makeEventList(tabRemovedListeners[extId])
                    },
                    windows: {
                        get: function(winId, opts, cb) {
                            if (typeof opts === 'function') { cb = opts; opts = {}; }
                            var win = { id: winId || 1, focused: true, type: 'normal', state: 'maximized' };
                            if (opts && opts.populate) {
                                win.tabs = B ? JSON.parse(B.tabsQuery(window.__jarvisCtxType, '{}')) : [];
                            }
                            return resolvePromiseOrCb(win, cb);
                        },
                        getCurrent: function(opts, cb) {
                            if (typeof opts === 'function') { cb = opts; opts = {}; }
                            var win = { id: 1, focused: true, type: 'normal', state: 'maximized' };
                            if (opts && opts.populate) {
                                win.tabs = B ? JSON.parse(B.tabsQuery(window.__jarvisCtxType, '{}')) : [];
                            }
                            return resolvePromiseOrCb(win, cb);
                        },
                        getLastFocused: function(opts, cb) {
                            if (typeof opts === 'function') { cb = opts; opts = {}; }
                            var win = { id: 1, focused: true, type: 'normal', state: 'maximized' };
                            if (opts && opts.populate) {
                                win.tabs = B ? JSON.parse(B.tabsQuery(window.__jarvisCtxType, '{}')) : [];
                            }
                            return resolvePromiseOrCb(win, cb);
                        },
                        getAll: function(opts, cb) {
                            if (typeof opts === 'function') { cb = opts; opts = {}; }
                            var win = { id: 1, focused: true, type: 'normal', state: 'maximized' };
                            if (opts && opts.populate) {
                                win.tabs = B ? JSON.parse(B.tabsQuery(window.__jarvisCtxType, '{}')) : [];
                            }
                            return resolvePromiseOrCb([win], cb);
                        },
                        create: function(createData, cb) {
                            var u = (createData && createData.url) ? (Array.isArray(createData.url) ? createData.url[0] : createData.url) : 'https://www.google.com';
                            api.tabs.create({ url: u, active: true });
                            return resolvePromiseOrCb({ id: 1, focused: true, type: 'normal' }, cb);
                        },
                        update: function(winId, updateInfo, cb) {
                            return resolvePromiseOrCb({ id: winId || 1, focused: true }, cb);
                        },
                        onFocusChanged: makeEventStub(),
                        onCreated: makeEventStub(),
                        onRemoved: makeEventStub()
                    },
                    scripting: {
                        executeScript: function(injection, cb) {
                            return new Promise(function(resolve) {
                                var tabId = (injection && injection.target && injection.target.tabId) ? Number(injection.target.tabId) : -1;
                                var hasFunc = !!(injection && injection.func);
                                var code = '';
                                if (hasFunc) {
                                    var args = Array.isArray(injection.args)
                                        ? injection.args.map(function(a) { return a === undefined ? 'undefined' : JSON.stringify(a); }).join(',')
                                        : '';
                                    code = '(' + injection.func.toString() + ')(' + args + ')';
                                } else if (injection && injection.code) {
                                    code = String(injection.code);
                                }
                                var filesJson = JSON.stringify((injection && injection.files) ? injection.files : []);
                                var cid = nextCbId(function(resArr) {
                                    if (cb) { try { cb(resArr); } catch(e) {} }
                                    resolve(resArr);
                                });
                                if (B) B.scriptingExecute(extId, tabId, code, filesJson, hasFunc, cid);
                                else resolve([{ frameId: 0, result: null }]);
                            });
                        },
                        insertCSS: function(injection, cb) {
                            var tabId = (injection && injection.target && injection.target.tabId) ? Number(injection.target.tabId) : -1;
                            var css = (injection && injection.css) ? String(injection.css) : '';
                            var filesJson = JSON.stringify((injection && injection.files) ? injection.files : []);
                            if (B) B.scriptingInsertCss(extId, tabId, css, filesJson);
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        removeCSS: function(injection, cb) { return resolvePromiseOrCb(undefined, cb); },
                        registerContentScripts: function(scripts, cb) { return resolvePromiseOrCb(undefined, cb); },
                        unregisterContentScripts: function(filter, cb) { return resolvePromiseOrCb(undefined, cb); }
                    },
                    sidePanel: {
                        setPanelBehavior: function(opts, cb) { return resolvePromiseOrCb(undefined, cb); },
                        setOptions: function(opts, cb) {
                            if (opts && opts.path && B && B.registerDynamicUiPath) {
                                B.registerDynamicUiPath(extId, String(opts.path));
                            }
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        getOptions: function(opts, cb) {
                            if (typeof opts === 'function') cb = opts;
                            var p = (manifestObj.side_panel && manifestObj.side_panel.default_path) || '';
                            return resolvePromiseOrCb({ enabled: true, path: p }, cb);
                        },
                        open: function(opts, cb) {
                            var p = (opts && opts.path) || (manifestObj.side_panel && manifestObj.side_panel.default_path) || (manifestObj.action && manifestObj.action.default_popup) || '__jarvis_ext_dashboard.html';
                            if (B) B.tabsCreate(api.runtime.getURL(p), true);
                            return resolvePromiseOrCb(undefined, cb);
                        }
                    },
                    action: {
                        setBadgeText: function(d, cb) {
                            if (B && B.actionSetBadgeText) B.actionSetBadgeText(extId, (d && d.text !== undefined) ? String(d.text) : '');
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        getBadgeText: function(d, cb) {
                            if (typeof d === 'function') cb = d;
                            var st = (B && B.actionGetState) ? JSON.parse(B.actionGetState(extId) || '{}') : {};
                            return resolvePromiseOrCb(st.badgeText || '', cb);
                        },
                        setBadgeBackgroundColor: function(d, cb) {
                            if (B && B.actionSetBadgeColor && d && d.color !== undefined) {
                                B.actionSetBadgeColor(extId, typeof d.color === 'string' ? d.color : JSON.stringify(d.color));
                            }
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        setBadgeTextColor: function(d, cb) { return resolvePromiseOrCb(undefined, cb); },
                        setIcon: function(d, cb) { return resolvePromiseOrCb(undefined, cb); },
                        setTitle: function(d, cb) {
                            if (B && B.actionSetTitle) B.actionSetTitle(extId, (d && d.title) ? String(d.title) : '');
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        getTitle: function(d, cb) {
                            if (typeof d === 'function') cb = d;
                            var st = (B && B.actionGetState) ? JSON.parse(B.actionGetState(extId) || '{}') : {};
                            return resolvePromiseOrCb(st.title || $actionTitleDefault, cb);
                        },
                        setPopup: function(d, cb) {
                            if (d && d.popup && B && B.registerDynamicUiPath) {
                                B.registerDynamicUiPath(extId, String(d.popup));
                            }
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        getPopup: function(d, cb) {
                            if (typeof d === 'function') cb = d;
                            return resolvePromiseOrCb(api.runtime.getURL($popupDefault), cb);
                        },
                        openPopup: function(cb) {
                            if (B && B.actionOpenPopup) B.actionOpenPopup(extId);
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        onClicked: makeEventList(actionClickedListeners[extId])
                    },
                    browserAction: null,
                    contextMenus: {
                        create: function(props, cb) {
                            var menuId = (props && props.id) ? String(props.id) : ('menu_' + Date.now() + '_' + Math.random().toString(36).slice(2, 6));
                            var payload = Object.assign({}, props || {}, { id: menuId });
                            if (B && B.contextMenusCreate) B.contextMenusCreate(extId, JSON.stringify(payload));
                            if (typeof cb === 'function') setTimeout(function() { try { cb(); } catch(e) {} }, 0);
                            return menuId;
                        },
                        update: function(id, props, cb) {
                            var payload = Object.assign({}, props || {}, { id: String(id) });
                            if (B && B.contextMenusCreate) B.contextMenusCreate(extId, JSON.stringify(payload));
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        remove: function(id, cb) {
                            if (B && B.contextMenusRemove) B.contextMenusRemove(extId, String(id));
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        removeAll: function(cb) {
                            if (B && B.contextMenusRemoveAll) B.contextMenusRemoveAll(extId);
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        onClicked: makeEventList(contextMenuListeners[extId])
                    },
                    commands: {
                        getAll: function(cb) { return resolvePromiseOrCb([], cb); },
                        onCommand: makeEventStub()
                    },
                    declarativeNetRequest: {
                        updateDynamicRules: function(opts, cb) {
                            if (B && B.dnrUpdateRules) B.dnrUpdateRules(extId, false, JSON.stringify(opts || {}));
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        getDynamicRules: function(cb) {
                            var arr = [];
                            try { arr = (B && B.dnrGetRules) ? JSON.parse(B.dnrGetRules(extId, false)) : []; } catch(e) {}
                            return resolvePromiseOrCb(arr, cb);
                        },
                        updateSessionRules: function(opts, cb) {
                            if (B && B.dnrUpdateRules) B.dnrUpdateRules(extId, true, JSON.stringify(opts || {}));
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        getSessionRules: function(cb) {
                            var arr = [];
                            try { arr = (B && B.dnrGetRules) ? JSON.parse(B.dnrGetRules(extId, true)) : []; } catch(e) {}
                            return resolvePromiseOrCb(arr, cb);
                        },
                        getEnabledRulesets: function(cb) { return resolvePromiseOrCb([], cb); },
                        isRegexSupported: function(opts, cb) { return resolvePromiseOrCb({ isSupported: true }, cb); }
                    },
                    webNavigation: {
                        onBeforeNavigate: makeEventList(webNavListeners[extId].onBeforeNavigate),
                        onCommitted: makeEventList(webNavListeners[extId].onCommitted),
                        onDOMContentLoaded: makeEventList(webNavListeners[extId].onDOMContentLoaded),
                        onCompleted: makeEventList(webNavListeners[extId].onCompleted),
                        onHistoryStateUpdated: makeEventList(webNavListeners[extId].onHistoryStateUpdated)
                    },
                    offscreen: {
                        createDocument: function(opts, cb) {
                            var u = (opts && opts.url) ? String(opts.url) : '';
                            if (B && B.offscreenCreateDocument) B.offscreenCreateDocument(extId, u);
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        closeDocument: function(cb) {
                            if (B && B.offscreenCloseDocument) B.offscreenCloseDocument(extId);
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        hasDocument: function(cb) {
                            var has = (B && B.offscreenHasDocument) ? !!B.offscreenHasDocument(extId) : false;
                            return resolvePromiseOrCb(has, cb);
                        }
                    },
                    cookies: {
                        get: function(d, cb) {
                            var list = [];
                            try {
                                list = (B && B.cookiesGetAll) ? JSON.parse(B.cookiesGetAll((d && d.url) || '', (d && d.name) || '')) : [];
                            } catch(e) {}
                            return resolvePromiseOrCb(list[0] || null, cb);
                        },
                        getAll: function(d, cb) {
                            var list = [];
                            try {
                                list = (B && B.cookiesGetAll) ? JSON.parse(B.cookiesGetAll((d && d.url) || '', (d && d.name) || '')) : [];
                            } catch(e) {}
                            return resolvePromiseOrCb(list, cb);
                        },
                        set: function(d, cb) {
                            if (B && B.cookiesSet && d) {
                                B.cookiesSet(String(d.url || ''), String(d.name || ''), String(d.value || ''), String(d.path || '/'), !!d.secure);
                            }
                            return resolvePromiseOrCb(d, cb);
                        },
                        remove: function(d, cb) {
                            if (B && B.cookiesRemove && d) {
                                B.cookiesRemove(String(d.url || ''), String(d.name || ''));
                            }
                            return resolvePromiseOrCb(d, cb);
                        },
                        onChanged: makeEventStub()
                    },
                    identity: {
                        getAuthToken: function(d, cb) { return resolvePromiseOrCb({ token: '' }, cb); },
                        getRedirectURL: function(p) { return originBase + '/' + (p || ''); }
                    },
                    downloads: {
                        download: function(opts, cb) {
                            var u = (opts && opts.url) ? String(opts.url) : '';
                            var fname = (opts && opts.filename) ? String(opts.filename) : '';
                            if (u.indexOf('blob:') === 0 && typeof fetch === 'function' && typeof FileReader !== 'undefined') {
                                return new Promise(function(resolve) {
                                    fetch(u).then(function(r) { return r.blob(); }).then(function(b) {
                                        var fr = new FileReader();
                                        fr.onloadend = function() {
                                            var dlId = B ? B.downloadsEnqueue(String(fr.result || ''), fname) : 1;
                                            if (cb) { try { cb(dlId); } catch(e) {} }
                                            resolve(dlId);
                                        };
                                        fr.onerror = function() {
                                            if (cb) { try { cb(0); } catch(e) {} }
                                            resolve(0);
                                        };
                                        fr.readAsDataURL(b);
                                    }).catch(function() {
                                        if (cb) { try { cb(0); } catch(e) {} }
                                        resolve(0);
                                    });
                                });
                            }
                            var dlId = B ? B.downloadsEnqueue(u, fname) : 1;
                            return resolvePromiseOrCb(dlId, cb);
                        },
                        onChanged: makeEventStub(),
                        onCreated: makeEventStub()
                    },
                    notifications: {
                        create: function(idOrOpts, optsOrCb, cb) {
                            var o = (typeof idOrOpts === 'object') ? idOrOpts : optsOrCb;
                            var msg = (o && (o.message || o.title)) ? String(o.title || '') + ' ' + String(o.message || '') : 'Notification';
                            if (B) B.showNotification(msg.trim());
                            var callback = (typeof optsOrCb === 'function') ? optsOrCb : cb;
                            return resolvePromiseOrCb('notif_1', callback);
                        },
                        clear: function(id, cb) { return resolvePromiseOrCb(true, cb); },
                        onClicked: makeEventStub(),
                        onButtonClicked: makeEventStub()
                    },
                    permissions: {
                        contains: function(p, cb) {
                            var granted = manifestObj.permissions || [];
                            var req = (p && p.permissions) || [];
                            var ok = req.every(function(perm) { return granted.indexOf(perm) !== -1; });
                            return resolvePromiseOrCb(ok || req.length === 0, cb);
                        },
                        request: function(p, cb) { return resolvePromiseOrCb(true, cb); },
                        remove: function(p, cb) { return resolvePromiseOrCb(true, cb); },
                        getAll: function(cb) { return resolvePromiseOrCb({ permissions: manifestObj.permissions || [], origins: manifestObj.host_permissions || [] }, cb); }
                    },
                    alarms: {
                        create: function(nameOrInfo, maybeInfo) {
                            var name = (typeof nameOrInfo === 'string') ? nameOrInfo : '';
                            var info = (typeof nameOrInfo === 'object') ? nameOrInfo : (maybeInfo || {});
                            var delayMs = 0;
                            if (info.when) delayMs = Math.max(0, Number(info.when) - Date.now());
                            else if (info.delayInMinutes) delayMs = Math.max(50, Number(info.delayInMinutes) * 60000);
                            var periodMs = info.periodInMinutes ? Math.max(1000, Number(info.periodInMinutes) * 60000) : 0;
                            if (B && B.alarmsCreate) B.alarmsCreate(extId, String(name), Math.round(delayMs), Math.round(periodMs));
                        },
                        clear: function(name, cb) {
                            if (typeof name === 'function') { cb = name; name = ''; }
                            if (B && B.alarmsClear) B.alarmsClear(extId, String(name || ''));
                            return resolvePromiseOrCb(true, cb);
                        },
                        clearAll: function(cb) {
                            if (B && B.alarmsClearAll) B.alarmsClearAll(extId);
                            return resolvePromiseOrCb(true, cb);
                        },
                        get: function(name, cb) {
                            if (typeof name === 'function') { cb = name; name = ''; }
                            var all = (B && B.alarmsGetAll) ? JSON.parse(B.alarmsGetAll(extId) || '[]') : [];
                            var hit = all.find(function(a) { return a.name === (name || ''); }) || null;
                            return resolvePromiseOrCb(hit, cb);
                        },
                        getAll: function(cb) {
                            var all = (B && B.alarmsGetAll) ? JSON.parse(B.alarmsGetAll(extId) || '[]') : [];
                            return resolvePromiseOrCb(all, cb);
                        },
                        onAlarm: makeEventList(alarmListeners[extId])
                    },
                    i18n: {
                        getMessage: function(k, substitutions) {
                            if (!k) return '';
                            if (k === '@@extension_id') return extId;
                            if (k === '@@ui_locale') return (navigator.language || 'en-US').replace('-', '_');
                            var entry = localeMessages[String(k).toLowerCase()];
                            if (!entry || !entry.message) return '';
                            var msg = String(entry.message);
                            var subs = Array.isArray(substitutions) ? substitutions : (substitutions !== undefined ? [String(substitutions)] : []);
                            if (entry.placeholders) {
                                Object.keys(entry.placeholders).forEach(function(phKey) {
                                    var phObj = entry.placeholders[phKey];
                                    var content = (phObj && phObj.content) ? String(phObj.content) : '';
                                    content = content.replace(/\$(\d+)/g, function(_, num) {
                                        var idx = parseInt(num, 10) - 1;
                                        return (subs[idx] !== undefined) ? String(subs[idx]) : '';
                                    });
                                    msg = msg.replace(new RegExp('\\$' + phKey + '\\$', 'gi'), content);
                                });
                            }
                            msg = msg.replace(/\$(\d+)/g, function(_, num) {
                                var idx = parseInt(num, 10) - 1;
                                return (subs[idx] !== undefined) ? String(subs[idx]) : '';
                            });
                            return msg.replace(/\$\$/g, function() { return '$'; });
                        },
                        getUILanguage: function() { return navigator.language || 'en-US'; },
                        getAcceptLanguages: function(cb) { return resolvePromiseOrCb(navigator.languages || [navigator.language || 'en-US'], cb); }
                    }
                };
                api.browserAction = api.action;
                api.pageAction = api.action;
                return api;
            };

            window.__jarvisDispatchActionClicked = function(extId, tabJson) {
                var list = (window.__jarvisActionClickedListeners && window.__jarvisActionClickedListeners[extId]) || [];
                var tabObj = {};
                try { tabObj = JSON.parse(tabJson || '{}'); } catch(e) {}
                list.forEach(function(fn) {
                    try { fn(tabObj); } catch(e) { console.warn('[JarvisExt] action.onClicked error', e); }
                });
            };

            window.__jarvisDispatchStorageChanged = function(extId, changesObj, area) {
                var list = (window.__jarvisStorageListeners && window.__jarvisStorageListeners[extId]) || [];
                list.forEach(function(fn) {
                    try { fn(changesObj || {}, area || 'local'); } catch(e) {}
                });
            };

            window.__jarvisDispatchTabUpdated = function(extId, tabId, changeInfo, tabObj) {
                var list = (window.__jarvisTabUpdatedListeners && window.__jarvisTabUpdatedListeners[extId]) || [];
                list.forEach(function(fn) {
                    try { fn(tabId, changeInfo || {}, tabObj || {}); } catch(e) {}
                });
            };

            window.__jarvisDispatchTabActivated = function(extId, tabId) {
                var list = (window.__jarvisTabActivatedListeners && window.__jarvisTabActivatedListeners[extId]) || [];
                list.forEach(function(fn) {
                    try { fn({ tabId: tabId, windowId: 1 }); } catch(e) {}
                });
            };

            window.__jarvisDispatchTabCreated = function(extId, tabObj) {
                var list = (window.__jarvisTabCreatedListeners && window.__jarvisTabCreatedListeners[extId]) || [];
                list.forEach(function(fn) {
                    try { fn(tabObj || {}); } catch(e) {}
                });
            };

            window.__jarvisDispatchTabRemoved = function(extId, tabId) {
                var list = (window.__jarvisTabRemovedListeners && window.__jarvisTabRemovedListeners[extId]) || [];
                list.forEach(function(fn) {
                    try { fn(tabId, { windowId: 1, isWindowClosing: false }); } catch(e) {}
                });
            };

            window.__jarvisDispatchAlarm = function(extId, alarmObj) {
                var list = (window.__jarvisAlarmListeners && window.__jarvisAlarmListeners[extId]) || [];
                list.forEach(function(fn) {
                    try { fn(alarmObj); } catch(e) {}
                });
            };

            window.__jarvisDispatchWebNavigation = function(extId, eventName, detailsObj) {
                var map = (window.__jarvisWebNavListeners && window.__jarvisWebNavListeners[extId]) || null;
                var list = (map && map[eventName]) || [];
                list.forEach(function(fn) {
                    try { fn(detailsObj); } catch(e) {}
                });
            };

            window.__jarvisDispatchContextMenuClick = function(extId, infoObj, tabObj) {
                var list = (window.__jarvisContextMenuListeners && window.__jarvisContextMenuListeners[extId]) || [];
                list.forEach(function(fn) {
                    try { fn(infoObj, tabObj); } catch(e) {}
                });
            };

            window.__jarvisDispatchRemotePortConnect = function(extId, portId, portName, senderJson) {
                var connectList = (window.__jarvisConnectListeners && window.__jarvisConnectListeners[extId]) || [];
                if (!connectList.length) return;
                var senderObj = {};
                try { senderObj = JSON.parse(senderJson || '{}'); } catch(e) {}
                var B = window.__jarvis_chrome_bridge;
                var portListeners = [];
                var disconnectListeners = [];
                var portState = { portListeners: portListeners, disconnectListeners: disconnectListeners, disconnected: false };
                window.__jarvisPorts = window.__jarvisPorts || {};
                window.__jarvisPorts[portId] = portState;
                var serverPort = {
                    name: portName,
                    sender: senderObj,
                    postMessage: function(reply) {
                        if (portState.disconnected) return;
                        if (B && B.portPostMessage) {
                            B.portPostMessage(portId, false, JSON.stringify(reply === undefined ? null : reply));
                        }
                    },
                    onMessage: {
                        addListener: function(fn) {
                            if (typeof fn === 'function' && portListeners.indexOf(fn) === -1) portListeners.push(fn);
                        },
                        removeListener: function(fn) {
                            var idx = portListeners.indexOf(fn);
                            if (idx !== -1) portListeners.splice(idx, 1);
                        },
                        hasListener: function(fn) { return portListeners.indexOf(fn) !== -1; },
                        hasListeners: function() { return portListeners.length > 0; }
                    },
                    onDisconnect: {
                        addListener: function(fn) {
                            if (typeof fn === 'function' && disconnectListeners.indexOf(fn) === -1) disconnectListeners.push(fn);
                        },
                        removeListener: function(fn) {
                            var idx = disconnectListeners.indexOf(fn);
                            if (idx !== -1) disconnectListeners.splice(idx, 1);
                        },
                        hasListener: function(fn) { return disconnectListeners.indexOf(fn) !== -1; },
                        hasListeners: function() { return disconnectListeners.length > 0; }
                    },
                    disconnect: function() {
                        if (portState.disconnected) return;
                        portState.disconnected = true;
                        delete window.__jarvisPorts[portId];
                        if (B && B.portDisconnect) B.portDisconnect(portId, false);
                        disconnectListeners.forEach(function(fn) { try { fn(serverPort); } catch(e) {} });
                    }
                };
                connectList.forEach(function(fn) { try { fn(serverPort); } catch(e) {} });
            };

            window.__jarvisDispatchRemotePortMessage = function(portId, msgJson) {
                var portState = window.__jarvisPorts && window.__jarvisPorts[portId];
                if (!portState || portState.disconnected) return;
                var msg = null;
                try { msg = JSON.parse(msgJson); } catch(e) {}
                portState.portListeners.slice().forEach(function(fn) { try { fn(msg); } catch(e) {} });
            };

            window.__jarvisDispatchRemotePortDisconnect = function(portId) {
                var portState = window.__jarvisPorts && window.__jarvisPorts[portId];
                if (!portState) return;
                portState.disconnected = true;
                delete window.__jarvisPorts[portId];
                portState.disconnectListeners.slice().forEach(function(fn) { try { fn(); } catch(e) {} });
            };

            window.__jarvisDispatchPortConnect = function(extId, portId, portName, clientListeners) {
                var connectList = (window.__jarvisConnectListeners && window.__jarvisConnectListeners[extId]) || [];
                if (!connectList.length) return;
                var serverPort = {
                    name: portName,
                    postMessage: function(reply) {
                        clientListeners.forEach(function(fn) { try { fn(reply); } catch(e) {} });
                    },
                    onMessage: {
                        addListener: function(fn) {
                            if (typeof fn !== 'function') return;
                            var msgList = (window.__jarvisMsgListeners && window.__jarvisMsgListeners[extId]) || [];
                            msgList.push(function(m, s, sendResponse) {
                                if (m && m.__jarvisPortId === portId) {
                                    fn(m.data, serverPort);
                                    return true;
                                }
                                return false;
                            });
                        },
                        removeListener: function() {}
                    },
                    onDisconnect: { addListener: function() {}, removeListener: function() {} },
                    disconnect: function() {}
                };
                connectList.forEach(function(fn) { try { fn(serverPort); } catch(e) {} });
            };

            window.__jarvisDispatchExtMessage = function(extId, msgJson, senderJson, replyCallbackId, originContextType) {
                var list = (window.__jarvisMsgListeners && window.__jarvisMsgListeners[extId]) || [];
                var msg = {};
                var sender = {};
                try { msg = JSON.parse(msgJson || '{}'); } catch(e) {}
                try { sender = JSON.parse(senderJson || '{}'); } catch(e) {}
                var responded = false;
                var sendResponse = function(reply) {
                    if (responded) return;
                    responded = true;
                    if (window.__jarvis_chrome_bridge && replyCallbackId) {
                        window.__jarvis_chrome_bridge.deliverAsyncReply(replyCallbackId, JSON.stringify(reply === undefined ? null : reply), originContextType || 'worker');
                    }
                };
                var keptOpen = false;
                for (var i = 0; i < list.length; i++) {
                    try {
                        var ret = list[i](msg, sender, sendResponse);
                        if (ret === true) keptOpen = true;
                        else if (ret && typeof ret.then === 'function') {
                            keptOpen = true;
                            ret.then(function(asyncRes) { sendResponse(asyncRes); }).catch(function() { sendResponse(null); });
                        }
                    } catch(e) { console.warn('[JarvisExt] listener error', e); }
                }
                if (!keptOpen && !responded && replyCallbackId && window.__jarvis_chrome_bridge) {
                    if (window.__jarvis_chrome_bridge.deliverNoListenerReply) {
                        window.__jarvis_chrome_bridge.deliverNoListenerReply(replyCallbackId);
                    } else {
                        window.__jarvis_chrome_bridge.deliverAsyncReply(replyCallbackId, 'null', originContextType || 'worker');
                    }
                }
            };

            window.__jarvisResolveCallback = function(cbId, resultJson) {
                var fn = window.__jarvisExtCbs && window.__jarvisExtCbs[cbId];
                if (typeof fn === 'function') {
                    delete window.__jarvisExtCbs[cbId];
                    try { fn(JSON.parse(resultJson)); } catch(e) { fn(null); }
                }
            };

            ${
            if (contextType != "content") {
                "window.chrome = window.__jarvisMakeChromeApi(${JSONObject.quote(ext.id)}, ${JSONObject.quote(baseOrigin)}, $safeManifest); window.browser = window.chrome;"
            } else {
                ""
            }
        }
        })();
        """.trimIndent()
    }

    private val replyTargets = ConcurrentHashMap<String, Pair<WebView?, String>>() // replyId -> (callerWebView, callerCbId)
    private val pendingNoReplyCounts = ConcurrentHashMap<String, AtomicInteger>()
    private data class PortSession(
        val clientWebView: WebView?,
        val serverWebViews: CopyOnWriteArraySet<WebView>,
        val serverExtId: String? = null
    )
    private val activePorts = ConcurrentHashMap<String, PortSession>()

    /**
     * Attached as `"__jarvis_chrome_bridge"` to ALL WebViews (Web tabs, Extension UI tabs, AND Service Worker hosts).
     */
    class ChromeJsBridge(
        private val context: Context,
        private val ownerWebViewProvider: () -> WebView?,
        private val currentTabNumericIdProvider: () -> Int = { 1 }
    ) {

        @JavascriptInterface
        fun storageGet(extId: String, area: String): String {
            val prefs = context.getSharedPreferences("ext_${extId}_$area", Context.MODE_PRIVATE)
            val fallbackPrefs = context.getSharedPreferences("ext_$extId", Context.MODE_PRIVATE)
            val json = JSONObject()
            fallbackPrefs.all.forEach { (k, v) -> json.put(k, parseStoredValue(v)) }
            prefs.all.forEach { (k, v) -> json.put(k, parseStoredValue(v)) }
            return json.toString()
        }

        private fun parseStoredValue(v: Any?): Any {
            if (v is String) {
                if (v.startsWith("__jarvis_num__:")) {
                    return v.removePrefix("__jarvis_num__:").toDoubleOrNull() ?: v
                }
                if ((v.startsWith("{") && v.endsWith("}")) || (v.startsWith("[") && v.endsWith("]"))) {
                    return runCatching {
                        if (v.startsWith("[")) JSONArray(v) else JSONObject(v)
                    }.getOrDefault(v)
                }
            }
            return v ?: JSONObject.NULL
        }

        @JavascriptInterface
        fun storageSet(extId: String, area: String, itemsJson: String) {
            runCatching {
                val sp = context.getSharedPreferences("ext_${extId}_$area", Context.MODE_PRIVATE)
                val existingAll = sp.all
                val editor = sp.edit()
                val obj = JSONObject(itemsJson)
                val changes = JSONObject()
                obj.keys().forEach { k ->
                    val v = obj.opt(k)
                    val oldVal = parseStoredValue(existingAll[k])
                    val changeEntry = JSONObject().apply {
                        if (oldVal != JSONObject.NULL) put("oldValue", oldVal)
                        put("newValue", v ?: JSONObject.NULL)
                    }
                    changes.put(k, changeEntry)
                    when (v) {
                        is Boolean -> editor.putBoolean(k, v)
                        is Int -> editor.putInt(k, v)
                        is Long -> editor.putLong(k, v)
                        is Double -> {
                            if (v == v.toLong().toDouble()) editor.putLong(k, v.toLong())
                            else editor.putString(k, "__jarvis_num__:$v")
                        }
                        else -> editor.putString(k, v?.toString() ?: "")
                    }
                }
                editor.apply()
                if (changes.length() > 0) {
                    dispatchStorageChangedEvent(extId, area, changes.toString())
                }
            }
        }

        @JavascriptInterface
        fun storageRemove(extId: String, area: String, keysJson: String) {
            runCatching {
                val sp = context.getSharedPreferences("ext_${extId}_$area", Context.MODE_PRIVATE)
                val existingAll = sp.all
                val arr = JSONArray(keysJson)
                val editor = sp.edit()
                val changes = JSONObject()
                for (i in 0 until arr.length()) {
                    val key = arr.optString(i)
                    if (existingAll.containsKey(key)) {
                        changes.put(key, JSONObject().put("oldValue", parseStoredValue(existingAll[key])))
                    }
                    editor.remove(key)
                }
                editor.apply()
                if (changes.length() > 0) {
                    dispatchStorageChangedEvent(extId, area, changes.toString())
                }
            }
        }

        @JavascriptInterface
        fun storageClear(extId: String, area: String) {
            val sp = context.getSharedPreferences("ext_${extId}_$area", Context.MODE_PRIVATE)
            val existingAll = sp.all
            val changes = JSONObject()
            existingAll.forEach { (k, v) ->
                changes.put(k, JSONObject().put("oldValue", parseStoredValue(v)))
            }
            sp.edit().clear().apply()
            if (changes.length() > 0) {
                dispatchStorageChangedEvent(extId, area, changes.toString())
            }
        }

        @JavascriptInterface
        fun tabsQuery(callerCtxType: String, queryJson: String): String {
            val q = runCatching { JSONObject(queryJson) }.getOrElse { JSONObject() }
            val wantActive = if (q.has("active")) q.optBoolean("active") else null
            val urlPatterns = buildList {
                val raw = q.opt("url")
                when (raw) {
                    is String -> if (raw.isNotBlank()) add(raw)
                    is JSONArray -> for (i in 0 until raw.length()) {
                        val s = raw.optString(i).trim()
                        if (s.isNotBlank()) add(s)
                    }
                }
            }

            val records = liveTabs.values.sortedBy { it.numericId }
            val effectiveActiveId = if (callerCtxType != "content" &&
                liveTabs[activeTabStringId]?.isExtensionPage == true &&
                lastActiveWebTabId.isNotBlank()
            ) {
                lastActiveWebTabId
            } else {
                activeTabStringId
            }

            val out = JSONArray()
            for (rec in records) {
                if (rec.url.isBlank() || rec.url == "about:blank") continue
                val isTabActive = if (effectiveActiveId.isNotBlank()) {
                    rec.tabId == effectiveActiveId
                } else {
                    rec.isActive
                }
                if (wantActive != null && isTabActive != wantActive) continue
                if (urlPatterns.isNotEmpty() && urlPatterns.none { ExtensionPageInjector.matchesPattern(rec.url, it) }) continue
                out.put(rec.toChromeTabJson(isTabActive))
            }
            return out.toString()
        }

        @JavascriptInterface
        fun tabsCaptureVisibleTab(): String {
            val targetWv = findTabByNumericId(-1)?.webView ?: return ""
            val encodeBitmap: () -> String = {
                runCatching {
                    val w = targetWv.width.coerceAtLeast(1)
                    val h = targetWv.height.coerceAtLeast(1)
                    val scale = minOf(1f, 720f / w.toFloat())
                    val outW = (w * scale).toInt().coerceAtLeast(1)
                    val outH = (h * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    canvas.scale(scale, scale)
                    targetWv.draw(canvas)
                    val baos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.PNG, 90, baos)
                    bmp.recycle()
                    "data:image/png;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                }.getOrDefault("")
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                return encodeBitmap()
            }
            var result = ""
            val latch = CountDownLatch(1)
            mainHandler.post {
                result = encodeBitmap()
                latch.countDown()
            }
            runCatching { latch.await(1500L, TimeUnit.MILLISECONDS) }
            return result
        }

        @JavascriptInterface
        fun tabsCreate(url: String, makeActive: Boolean): String {
            val created = tabActionCallbacks?.onCreateTab(url, makeActive)
            return created?.toChromeTabJson(makeActive)?.toString()
                ?: JSONObject().put("id", 1).put("url", url).put("active", makeActive).toString()
        }

        @JavascriptInterface
        fun tabsUpdate(numericTabId: Int, propsJson: String): String {
            val target = if (numericTabId > 0) findTabByNumericId(numericTabId) else findTabByNumericId(-1)
            val props = runCatching { JSONObject(propsJson) }.getOrElse { JSONObject() }
            if (target != null) {
                if (props.optBoolean("active", false)) {
                    mainHandler.post { tabActionCallbacks?.onSelectTab(target.tabId) }
                }
                val newUrl = props.optString("url", "")
                if (newUrl.isNotBlank()) {
                    mainHandler.post { tabActionCallbacks?.onUpdateTabUrl(target.tabId, newUrl) }
                }
                return target.toChromeTabJson(target.isActive).toString()
            }
            return "{}"
        }

        @JavascriptInterface
        fun tabsRemove(numericTabId: Int) {
            val target = findTabByNumericId(numericTabId) ?: return
            mainHandler.post { tabActionCallbacks?.onCloseTab(target.tabId) }
        }

        @JavascriptInterface
        fun tabsReload(numericTabId: Int) {
            val target = findTabByNumericId(numericTabId) ?: return
            mainHandler.post { tabActionCallbacks?.onReloadTab(target.tabId) }
        }

        @JavascriptInterface
        fun runtimeSendMessage(
            extId: String,
            callerCtxType: String,
            callerTabId: Int,
            msgJson: String,
            cbId: String
        ) {
            val callerWv = ownerWebViewProvider()
            val replyKey = "${extId}_${System.nanoTime()}"
            replyTargets[replyKey] = callerWv to cbId

            val ext = BrowserExtensionManager.extensions.value.firstOrNull { it.id == extId }
            val senderObj = JSONObject().apply {
                put("id", extId)
                if (ext != null) put("origin", ext.extensionOrigin())
                if (callerTabId > 0) {
                    findTabByNumericId(callerTabId)?.let { t ->
                        put("tab", t.toChromeTabJson(t.isActive))
                        put("url", t.url)
                    }
                }
            }.toString()

            BrowserExtensionManager.recordLog(extId, "[$callerCtxType] sendMessage: ${msgJson.take(100)}")

            val uiTargets = activeExtensionWebViewsFor(extId, exclude = callerWv)
            val hasWorker = BrowserExtensionManager.extensions.value.any {
                it.id == extId && it.isEnabled &&
                    (it.serviceWorkerFile.isNotBlank() || it.backgroundScripts.isNotEmpty() || it.backgroundPage.isNotBlank())
            }
            val expectedCount = uiTargets.size + (if (hasWorker) 1 else 0)
            if (expectedCount <= 0) {
                replyTargets.remove(replyKey)
                callerWv?.let { wv ->
                    mainHandler.post {
                        wv.evaluateJavascript("window.__jarvisResolveCallback('$cbId', 'null');", null)
                    }
                }
                return
            }
            pendingNoReplyCounts[replyKey] = AtomicInteger(expectedCount)

            if (uiTargets.isNotEmpty()) {
                mainHandler.post {
                    val js = "if (window.__jarvisDispatchExtMessage) { window.__jarvisDispatchExtMessage(${JSONObject.quote(extId)}, ${JSONObject.quote(msgJson)}, ${JSONObject.quote(senderObj)}, ${JSONObject.quote(replyKey)}, 'ui'); } else if (window.__jarvis_chrome_bridge && window.__jarvis_chrome_bridge.deliverNoListenerReply) { window.__jarvis_chrome_bridge.deliverNoListenerReply(${JSONObject.quote(replyKey)}); }"
                    uiTargets.forEach { wv -> wv.evaluateJavascript(js, null) }
                }
            }

            if (hasWorker) {
                val dispatchSuccess = BrowserExtensionManager.dispatchRuntimeMessageToWorker(
                    extId = extId,
                    msgJson = msgJson,
                    senderJson = senderObj,
                    replyKey = replyKey
                )
                if (!dispatchSuccess) {
                    deliverNoListenerReply(replyKey)
                }
            }
        }

        @JavascriptInterface
        fun runtimeConnectOpen(
            extId: String,
            callerCtxType: String,
            callerTabId: Int,
            portId: String,
            portName: String
        ) {
            val callerWv = ownerWebViewProvider()
            val peerSet = CopyOnWriteArraySet<WebView>()
            activeExtensionWebViewsFor(extId, exclude = callerWv).forEach { peerSet.add(it) }
            val workerHost = BrowserExtensionManager.getWorkerHost(extId)
            val workerWv = workerHost?.peekWebView()
            if (workerWv != null && workerWv !== callerWv) {
                peerSet.add(workerWv)
            }
            activePorts[portId] = PortSession(
                clientWebView = callerWv,
                serverWebViews = peerSet,
                serverExtId = if (workerHost != null) extId else null
            )

            val ext = BrowserExtensionManager.extensions.value.firstOrNull { it.id == extId }
            val senderJson = JSONObject().apply {
                put("id", extId)
                if (ext != null) put("origin", ext.extensionOrigin())
                if (callerTabId > 0) {
                    findTabByNumericId(callerTabId)?.let { t ->
                        put("tab", t.toChromeTabJson(t.isActive))
                        put("url", t.url)
                    }
                }
            }.toString()

            val js = "if (window.__jarvisDispatchRemotePortConnect) { window.__jarvisDispatchRemotePortConnect(${JSONObject.quote(extId)}, ${JSONObject.quote(portId)}, ${JSONObject.quote(portName)}, ${JSONObject.quote(senderJson)}); }"
            mainHandler.post {
                peerSet.forEach { it.evaluateJavascript(js, null) }
            }
            if (workerWv == null && workerHost != null) {
                workerHost.evalJsFireAndForget(js)
            }
        }

        @JavascriptInterface
        fun tabsConnectOpen(extId: String, numericTabId: Int, portId: String, portName: String) {
            val callerWv = ownerWebViewProvider()
            val target = findTabByNumericId(numericTabId)
            val targetWv = target?.webView ?: return
            val ext = BrowserExtensionManager.extensions.value.firstOrNull { it.id == extId }
            val peerSet = CopyOnWriteArraySet<WebView>().apply { add(targetWv) }
            activePorts[portId] = PortSession(clientWebView = callerWv, serverWebViews = peerSet)

            val senderJson = JSONObject().apply {
                put("id", extId)
                if (ext != null) {
                    put("origin", ext.extensionOrigin())
                    put("url", "${ext.extensionOrigin()}/")
                }
            }.toString()

            mainHandler.post {
                if (ext != null && ext.contentScripts.isNotEmpty() && !target.isExtensionPage) {
                    ExtensionPageInjector.injectForPage(
                        webView = targetWv,
                        url = target.url,
                        extensions = listOf(ext),
                        tabNumericId = target.numericId,
                        isPrivateTab = target.isPrivate,
                        runAt = "any",
                        onlyExtensionId = extId
                    )
                }
                val js = "if (window.__jarvisDispatchRemotePortConnect) { window.__jarvisDispatchRemotePortConnect(${JSONObject.quote(extId)}, ${JSONObject.quote(portId)}, ${JSONObject.quote(portName)}, ${JSONObject.quote(senderJson)}); }"
                targetWv.evaluateJavascript(js, null)
            }
        }

        @JavascriptInterface
        fun portPostMessage(portId: String, fromClient: Boolean, msgJson: String) {
            val session = activePorts[portId] ?: return
            val js = "if (window.__jarvisDispatchRemotePortMessage) { window.__jarvisDispatchRemotePortMessage(${JSONObject.quote(portId)}, ${JSONObject.quote(msgJson)}); }"
            mainHandler.post {
                if (fromClient) {
                    session.serverWebViews.forEach { it.evaluateJavascript(js, null) }
                    val eid = session.serverExtId
                    if (eid != null) {
                        val host = BrowserExtensionManager.getWorkerHost(eid)
                        val wv = host?.peekWebView()
                        if (wv == null && host != null) host.evalJsFireAndForget(js)
                    }
                } else {
                    session.clientWebView?.evaluateJavascript(js, null)
                }
            }
        }

        @JavascriptInterface
        fun portDisconnect(portId: String, fromClient: Boolean) {
            val session = activePorts.remove(portId) ?: return
            val js = "if (window.__jarvisDispatchRemotePortDisconnect) { window.__jarvisDispatchRemotePortDisconnect(${JSONObject.quote(portId)}); }"
            mainHandler.post {
                if (fromClient) {
                    session.serverWebViews.forEach { it.evaluateJavascript(js, null) }
                    val eid = session.serverExtId
                    if (eid != null) {
                        val host = BrowserExtensionManager.getWorkerHost(eid)
                        if (host?.peekWebView() == null) host?.evalJsFireAndForget(js)
                    }
                } else {
                    session.clientWebView?.evaluateJavascript(js, null)
                }
            }
        }

        @JavascriptInterface
        fun canHostFetch(extId: String, url: String): Boolean {
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                return false
            }
            val ext = BrowserExtensionManager.extensions.value.firstOrNull { it.id == extId && it.isEnabled }
                ?: return false
            return ext.hostPermissions.any { ExtensionPageInjector.matchesPattern(url, it) }
        }

        @JavascriptInterface
        fun hostFetch(
            extId: String,
            url: String,
            method: String,
            headersJson: String,
            bodyBase64: String,
            hasBody: Boolean,
            credentialsMode: String,
            cbId: String
        ) {
            val callerWv = ownerWebViewProvider() ?: return
            if (!canHostFetch(extId, url)) {
                val errPayload = JSONObject().put("error", "Permission denied for host: $url").toString()
                mainHandler.post {
                    callerWv.evaluateJavascript("window.__jarvisResolveCallback('$cbId', ${JSONObject.quote(errPayload)});", null)
                }
                return
            }

            hostFetchExecutor.execute {
                val payloadJson = runCatching {
                    val hdrMap = mutableMapOf<String, String>()
                    runCatching { JSONObject(headersJson) }.getOrNull()?.let { obj ->
                        obj.keys().forEach { k -> hdrMap[k] = obj.optString(k, "") }
                    }
                    val upperMethod = method.uppercase(Locale.US).ifBlank { "GET" }
                    val contentTypeStr = hdrMap.entries
                        .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                        ?.value
                    val mediaType = contentTypeStr?.toMediaTypeOrNull()

                    val bodyBytes = if (hasBody && bodyBase64.isNotEmpty()) {
                        Base64.decode(bodyBase64, Base64.DEFAULT)
                    } else {
                        ByteArray(0)
                    }
                    val needsBody = upperMethod == "POST" || upperMethod == "PUT" || upperMethod == "PATCH"
                    val requestBody = when {
                        hasBody -> bodyBytes.toRequestBody(mediaType)
                        needsBody -> ByteArray(0).toRequestBody(mediaType)
                        else -> null
                    }

                    val reqBuilder = Request.Builder().url(url).method(upperMethod, requestBody)
                    var hasExplicitCookie = false
                    hdrMap.forEach { (k, v) ->
                        if (k.equals("Cookie", ignoreCase = true)) hasExplicitCookie = true
                        if (!k.equals("Content-Length", ignoreCase = true) && !k.equals("Host", ignoreCase = true)) {
                            runCatching { reqBuilder.addHeader(k, v) }
                        }
                    }
                    if (!hasExplicitCookie && !credentialsMode.equals("omit", ignoreCase = true)) {
                        val cmCookie = runCatching { CookieManager.getInstance()?.getCookie(url) }.getOrNull().orEmpty()
                        if (cmCookie.isNotBlank()) {
                            runCatching { reqBuilder.header("Cookie", cmCookie) }
                        }
                    }

                    hostHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                        val cm = runCatching { CookieManager.getInstance() }.getOrNull()
                        val respHeaders = JSONArray()
                        for (i in 0 until resp.headers.size) {
                            val name = resp.headers.name(i)
                            val value = resp.headers.value(i)
                            if (name.equals("Set-Cookie", ignoreCase = true) && cm != null && !credentialsMode.equals("omit", ignoreCase = true)) {
                                runCatching { cm.setCookie(resp.request.url.toString(), value) }
                            }
                            respHeaders.put(JSONArray().put(name).put(value))
                        }
                        if (cm != null) runCatching { cm.flush() }

                        val rawBytes = resp.body?.bytes() ?: ByteArray(0)
                        val b64 = if (rawBytes.isNotEmpty()) Base64.encodeToString(rawBytes, Base64.NO_WRAP) else ""
                        JSONObject().apply {
                            put("status", resp.code)
                            put("statusText", resp.message)
                            put("url", resp.request.url.toString())
                            put("headers", respHeaders)
                            put("bodyB64", b64)
                        }.toString()
                    }
                }.getOrElse { err ->
                    JSONObject().put("error", err.message ?: "Network request failed").toString()
                }

                mainHandler.post {
                    callerWv.evaluateJavascript(
                        "window.__jarvisResolveCallback('$cbId', ${JSONObject.quote(payloadJson)});",
                        null
                    )
                }
            }
        }

        @JavascriptInterface
        fun registerDynamicUiPath(extId: String, path: String) {
            BrowserExtensionManager.updateDynamicUiPath(extId, path)
        }

        @JavascriptInterface
        fun triggerExtensionAction(extId: String) {
            val activeRec = findTabByNumericId(-1)
            val tabJson = activeRec?.toChromeTabJson(true)?.toString() ?: "{\"id\":1,\"url\":\"https://www.google.com\",\"active\":true}"
            BrowserExtensionManager.dispatchActionClickedToWorker(extId, tabJson)
        }

        @JavascriptInterface
        fun requestWebStoreInstall(extId: String, title: String) {
            val cleanId = CrxExtensionInstaller.extractChromeWebStoreId(extId) ?: return
            mainHandler.post { tabActionCallbacks?.onRequestWebStoreInstall(cleanId, title.trim()) }
        }

        @JavascriptInterface
        fun actionSetBadgeText(extId: String, text: String) {
            BrowserExtensionManager.updateActionBadge(extId, text)
        }

        @JavascriptInterface
        fun actionSetBadgeColor(extId: String, colorRaw: String) {
            val trimmed = colorRaw.trim()
            val argb = when {
                trimmed.startsWith("[") -> runCatching {
                    val arr = JSONArray(trimmed)
                    val r = arr.optInt(0, 242).coerceIn(0, 255)
                    val g = arr.optInt(1, 95).coerceIn(0, 255)
                    val b = arr.optInt(2, 112).coerceIn(0, 255)
                    val a = arr.optInt(3, 255).coerceIn(0, 255)
                    ((a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()) and 0xFFFFFFFFL
                }.getOrDefault(0xFFF25F70L)
                trimmed.startsWith("#") -> runCatching {
                    android.graphics.Color.parseColor(trimmed).toLong() and 0xFFFFFFFFL
                }.getOrDefault(0xFFF25F70L)
                else -> 0xFFF25F70L
            }
            BrowserExtensionManager.updateActionBadgeColor(extId, argb)
        }

        @JavascriptInterface
        fun actionSetTitle(extId: String, title: String) {
            BrowserExtensionManager.updateActionTitle(extId, title)
        }

        @JavascriptInterface
        fun actionGetState(extId: String): String {
            val st = BrowserExtensionManager.actionUiStates.value[extId]
            return JSONObject().apply {
                put("title", st?.title.orEmpty())
                put("badgeText", st?.badgeText.orEmpty())
            }.toString()
        }

        @JavascriptInterface
        fun actionOpenPopup(extId: String) {
            mainHandler.post { tabActionCallbacks?.onRequestOpenPopup(extId) }
        }

        @JavascriptInterface
        fun contextMenusCreate(extId: String, propsJson: String) {
            val obj = runCatching { JSONObject(propsJson) }.getOrNull() ?: return
            val id = obj.optString("id").ifBlank { "menu_${System.currentTimeMillis()}" }
            val title = obj.optString("title", "Extension Action")
            val contexts = buildList {
                val arr = obj.optJSONArray("contexts")
                if (arr != null) for (i in 0 until arr.length()) add(arr.optString(i))
            }.ifEmpty { listOf("page") }
            val patterns = buildList {
                val arr = obj.optJSONArray("documentUrlPatterns")
                if (arr != null) for (i in 0 until arr.length()) add(arr.optString(i))
            }
            BrowserExtensionManager.upsertContextMenuItem(
                ExtensionContextMenuItem(extId, id, title, contexts, patterns)
            )
        }

        @JavascriptInterface
        fun contextMenusRemove(extId: String, menuId: String) {
            BrowserExtensionManager.removeContextMenuItem(extId, menuId)
        }

        @JavascriptInterface
        fun contextMenusRemoveAll(extId: String) {
            BrowserExtensionManager.clearContextMenuItems(extId)
        }

        @JavascriptInterface
        fun dnrUpdateRules(extId: String, isSession: Boolean, optsJson: String) {
            val obj = runCatching { JSONObject(optsJson) }.getOrNull() ?: return
            val removeArr = obj.optJSONArray("removeRuleIds")
            val rmIds = buildList {
                if (removeArr != null) {
                    for (i in 0 until removeArr.length()) add(removeArr.optInt(i))
                }
            }
            val addArr = obj.optJSONArray("addRules")
            BrowserExtensionManager.updateDeclarativeRules(extId, isSession, rmIds, addArr)
        }

        @JavascriptInterface
        fun dnrGetRules(extId: String, isSession: Boolean): String {
            return BrowserExtensionManager.getDeclarativeRulesJson(extId, isSession)
        }

        @JavascriptInterface
        fun alarmsCreate(extId: String, name: String, delayMs: Long, periodMs: Long) {
            val map = activeAlarms.getOrPut(extId) { ConcurrentHashMap() }
            val meta = alarmMeta.getOrPut(extId) { ConcurrentHashMap() }
            map.remove(name)?.let { mainHandler.removeCallbacks(it) }
            val safeDelay = delayMs.coerceIn(25L, 86_400_000L)
            val scheduledTime = System.currentTimeMillis() + safeDelay
            meta[name] = JSONObject().apply {
                put("name", name)
                put("scheduledTime", scheduledTime)
                if (periodMs > 0) put("periodInMinutes", periodMs / 60000.0)
            }
            val runner = object : Runnable {
                override fun run() {
                    val info = meta[name] ?: JSONObject().put("name", name).put("scheduledTime", System.currentTimeMillis())
                    val js = "if (window.__jarvisDispatchAlarm) { window.__jarvisDispatchAlarm(${JSONObject.quote(extId)}, $info); }"
                    BrowserExtensionManager.getWorkerHost(extId)?.evalJsFireAndForget(js)
                    if (periodMs > 0L) {
                        meta[name] = JSONObject().apply {
                            put("name", name)
                            put("scheduledTime", System.currentTimeMillis() + periodMs)
                            put("periodInMinutes", periodMs / 60000.0)
                        }
                        mainHandler.postDelayed(this, periodMs)
                    } else {
                        map.remove(name)
                        meta.remove(name)
                    }
                }
            }
            map[name] = runner
            mainHandler.postDelayed(runner, safeDelay)
        }

        @JavascriptInterface
        fun alarmsClear(extId: String, name: String) {
            activeAlarms[extId]?.remove(name)?.let { mainHandler.removeCallbacks(it) }
            alarmMeta[extId]?.remove(name)
        }

        @JavascriptInterface
        fun alarmsClearAll(extId: String) {
            activeAlarms.remove(extId)?.values?.forEach { mainHandler.removeCallbacks(it) }
            alarmMeta.remove(extId)
        }

        @JavascriptInterface
        fun alarmsGetAll(extId: String): String {
            val arr = JSONArray()
            alarmMeta[extId]?.values?.forEach { arr.put(it) }
            return arr.toString()
        }

        @JavascriptInterface
        fun cookiesGetAll(url: String, nameFilter: String): String {
            val raw = runCatching { CookieManager.getInstance()?.getCookie(url) }.getOrNull().orEmpty()
            val out = JSONArray()
            if (raw.isBlank()) return out.toString()
            val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
            val isSecure = url.startsWith("https://", ignoreCase = true)
            for (pair in raw.split(';')) {
                val idx = pair.indexOf('=')
                if (idx <= 0) continue
                val k = pair.substring(0, idx).trim()
                val v = pair.substring(idx + 1).trim()
                if (nameFilter.isNotBlank() && k != nameFilter) continue
                out.put(
                    JSONObject().apply {
                        put("name", k)
                        put("value", v)
                        put("domain", host)
                        put("path", "/")
                        put("secure", isSecure)
                        put("httpOnly", false)
                    }
                )
            }
            return out.toString()
        }

        @JavascriptInterface
        fun cookiesSet(url: String, name: String, value: String, path: String, secure: Boolean) {
            if (url.isBlank() || name.isBlank()) return
            runCatching {
                val cm = CookieManager.getInstance() ?: return
                val secAttr = if (secure) "; Secure" else ""
                cm.setCookie(url, "$name=$value; Path=${path.ifBlank { "/" }}$secAttr")
                cm.flush()
            }
        }

        @JavascriptInterface
        fun cookiesRemove(url: String, name: String) {
            if (url.isBlank() || name.isBlank()) return
            runCatching {
                val cm = CookieManager.getInstance() ?: return
                cm.setCookie(url, "$name=; Max-Age=0; Path=/")
                cm.flush()
            }
        }

        @JavascriptInterface
        fun offscreenCreateDocument(extId: String, docUrl: String) {
            val ext = BrowserExtensionManager.extensions.value.firstOrNull { it.id == extId } ?: return
            val rel = docUrl.removePrefix("/")
            val resolved = if (rel.startsWith("https://") || rel.startsWith("chrome-extension://")) {
                parseExtensionResourceUrl(rel)?.let { (_, p) -> "${ext.extensionOrigin()}/$p" } ?: rel
            } else {
                "${ext.extensionOrigin()}/$rel"
            }
            mainHandler.post {
                runCatching {
                    offscreenWebViews.remove(extId)?.destroy()
                    val wv = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        addJavascriptInterface(
                            ChromeJsBridge(context, { offscreenWebViews[extId] }, currentTabNumericIdProvider),
                            "__jarvis_chrome_bridge"
                        )
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                                BrowserExtensionManager.recordLog(extId, "[Offscreen] ${cm?.message()}")
                                return true
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                return interceptExtensionRequest(context, request) ?: super.shouldInterceptRequest(view, request)
                            }
                        }
                        loadUrl(resolved)
                    }
                    offscreenWebViews[extId] = wv
                }
            }
        }

        @JavascriptInterface
        fun offscreenCloseDocument(extId: String) {
            mainHandler.post {
                runCatching { offscreenWebViews.remove(extId)?.destroy() }
            }
        }

        @JavascriptInterface
        fun offscreenHasDocument(extId: String): Boolean = offscreenWebViews.containsKey(extId)

        @JavascriptInterface
        fun tabsSendMessage(extId: String, numericTabId: Int, msgJson: String, cbId: String) {
            val callerWv = ownerWebViewProvider()
            val target = findTabByNumericId(numericTabId)
            val targetWv = target?.webView
            if (target == null || targetWv == null) {
                callerWv?.post {
                    callerWv.evaluateJavascript("window.__jarvisResolveCallback('$cbId', 'null');", null)
                }
                return
            }

            val ext = BrowserExtensionManager.extensions.value.firstOrNull { it.id == extId }
            val replyKey = "${extId}_tab_${System.nanoTime()}"
            replyTargets[replyKey] = callerWv to cbId
            val senderJson = JSONObject().apply {
                put("id", extId)
                if (ext != null) {
                    put("origin", ext.extensionOrigin())
                    put("url", "${ext.extensionOrigin()}/")
                }
            }.toString()

            mainHandler.post {
                if (ext != null && ext.contentScripts.isNotEmpty() && !target.isExtensionPage) {
                    ExtensionPageInjector.injectForPage(
                        webView = targetWv,
                        url = target.url,
                        extensions = listOf(ext),
                        tabNumericId = target.numericId,
                        isPrivateTab = target.isPrivate,
                        runAt = "any",
                        onlyExtensionId = extId
                    )
                }
                val js = """
                    (function() {
                        if (window.__jarvisDispatchExtMessage) {
                            window.__jarvisDispatchExtMessage(${JSONObject.quote(extId)}, ${JSONObject.quote(msgJson)}, ${JSONObject.quote(senderJson)}, ${JSONObject.quote(replyKey)}, 'content');
                            return "dispatched";
                        } else if (window.__jarvis_chrome_bridge && window.__jarvis_chrome_bridge.deliverAsyncReply) {
                            window.__jarvis_chrome_bridge.deliverAsyncReply(${JSONObject.quote(replyKey)}, 'null', 'content');
                            return "no_listener";
                        }
                        return "no_bridge";
                    })();
                """.trimIndent()
                targetWv.evaluateJavascript(js) { rawStatus ->
                    if (rawStatus == null || rawStatus == "null" || rawStatus.contains("no_bridge")) {
                        deliverNoListenerReply(replyKey)
                    }
                }
            }
        }

        @JavascriptInterface
        fun deliverNoListenerReply(replyKey: String) {
            val counter = pendingNoReplyCounts[replyKey]
            if (counter == null || counter.decrementAndGet() <= 0) {
                pendingNoReplyCounts.remove(replyKey)
                val (callerWv, cbId) = replyTargets.remove(replyKey) ?: return
                if (callerWv != null) {
                    mainHandler.post {
                        callerWv.evaluateJavascript("window.__jarvisResolveCallback('$cbId', 'null');", null)
                    }
                }
            }
        }

        @JavascriptInterface
        fun deliverAsyncReply(replyKey: String, replyJson: String, fromCtx: String) {
            pendingNoReplyCounts.remove(replyKey)
            val (callerWv, cbId) = replyTargets.remove(replyKey) ?: return
            if (callerWv != null) {
                mainHandler.post {
                    val safeJsonLiteral = JSONObject.quote(replyJson.ifBlank { "null" })
                    callerWv.evaluateJavascript("window.__jarvisResolveCallback('$cbId', $safeJsonLiteral);", null)
                }
            }
        }

        @JavascriptInterface
        fun scriptingExecute(
            extId: String,
            numericTabId: Int,
            inlineCode: String,
            filesJson: String,
            cbId: String
        ) {
            scriptingExecute(extId, numericTabId, inlineCode, filesJson, false, cbId)
        }

        @JavascriptInterface
        fun scriptingExecute(
            extId: String,
            numericTabId: Int,
            inlineCode: String,
            filesJson: String,
            isFuncExpression: Boolean,
            cbId: String
        ) {
            val callerWv = ownerWebViewProvider()
            val target = findTabByNumericId(numericTabId)
            val targetWv = target?.webView
            val ext = BrowserExtensionManager.extensions.value.firstOrNull { it.id == extId }

            if (target == null || targetWv == null) {
                callerWv?.post {
                    callerWv.evaluateJavascript("window.__jarvisResolveCallback('$cbId', '[{\"frameId\":0,\"result\":null}]');", null)
                }
                return
            }

            val filesCode = buildString {
                if (ext != null) {
                    val manifestRaw = runCatching { File(ext.dirPath, "manifest.json").readText() }.getOrDefault("{}")
                    val safeManifest = try { JSONObject(manifestRaw).toString() } catch (_: Exception) { "{}" }
                    append(buildUnifiedChromePolyfillJs(ext, "content", target.numericId)).append('\n')
                    append("var chrome = window.__jarvisMakeChromeApi(${JSONObject.quote(ext.id)}, ${JSONObject.quote(ext.extensionOrigin())}, $safeManifest);\n")
                    append("var browser = chrome;\n")
                    val files = runCatching { JSONArray(filesJson) }.getOrNull()
                    if (files != null) {
                        for (i in 0 until files.length()) {
                            val rel = files.optString(i).removePrefix("/")
                            val f = File(ext.dirPath, rel)
                            if (f.exists() && f.isFile) {
                                append(runCatching { f.readText() }.getOrDefault("")).append("\n;\n")
                            }
                        }
                    }
                }
            }

            val trimmedInline = inlineCode.trim()
            val looksSingleExpression = !isFuncExpression &&
                trimmedInline.isNotBlank() &&
                !trimmedInline.contains('\n') &&
                !trimmedInline.contains(';') &&
                !Regex("^(var|let|const|function|async\\s+function|return|if|for|while|try|class|switch|\\{)\\b").containsMatchIn(trimmedInline)

            val execBody = buildString {
                append(filesCode)
                when {
                    isFuncExpression && trimmedInline.isNotBlank() -> {
                        append("\nvar __jarvisExecRes = ($inlineCode);\n")
                    }
                    looksSingleExpression -> {
                        append("\nvar __jarvisExecRes = ($trimmedInline);\n")
                    }
                    trimmedInline.isNotBlank() -> {
                        append("\nvar __jarvisExecRes = (function(){\n$inlineCode\n})();\n")
                    }
                    else -> {
                        append("\nvar __jarvisExecRes = null;\n")
                    }
                }
            }

            val replyKey = "${extId}_exec_${System.nanoTime()}"
            replyTargets[replyKey] = callerWv to cbId

            mainHandler.post {
                // Direct injection bypasses strict page CSP (no eval()) and awaits Promises for both
                // synchronous and async MV3 `chrome.scripting.executeScript({ func })` callbacks.
                val wrapped = """
                    (function() {
                        var __rk = ${JSONObject.quote(replyKey)};
                        var __send = function(arr) {
                            try {
                                if (window.__jarvis_chrome_bridge && window.__jarvis_chrome_bridge.deliverAsyncReply) {
                                    window.__jarvis_chrome_bridge.deliverAsyncReply(__rk, JSON.stringify(arr), 'content');
                                    return true;
                                }
                            } catch (e) {}
                            return false;
                        };
                        try {
                            $execBody
                            Promise.resolve(__jarvisExecRes).then(function(val) {
                                __send([{ documentId: 'jarvis-doc-0', frameId: 0, result: val === undefined ? null : val }]);
                            }).catch(function(err) {
                                __send([{ documentId: 'jarvis-doc-0', frameId: 0, result: null, error: String(err && err.message ? err.message : err) }]);
                            });
                            return "pending";
                        } catch (e) {
                            var ok = __send([{ documentId: 'jarvis-doc-0', frameId: 0, result: null, error: String(e && e.message ? e.message : e) }]);
                            return ok ? "sent_err" : "no_bridge";
                        }
                    })();
                """.trimIndent()
                targetWv.evaluateJavascript(wrapped) { rawStatus ->
                    if (rawStatus == null || rawStatus == "null" || rawStatus.contains("no_bridge")) {
                        val targetPair = replyTargets.remove(replyKey)
                        if (targetPair != null && targetPair.first != null) {
                            targetPair.first?.evaluateJavascript(
                                "window.__jarvisResolveCallback('${targetPair.second}', '[{\"documentId\":\"jarvis-doc-0\",\"frameId\":0,\"result\":null}]');",
                                null
                            )
                        }
                    }
                }
            }
        }

        @JavascriptInterface
        fun scriptingInsertCss(extId: String, numericTabId: Int, css: String, filesJson: String) {
            val targetWv = findTabByNumericId(numericTabId)?.webView ?: return
            val ext = BrowserExtensionManager.extensions.value.firstOrNull { it.id == extId }
            val fullCss = buildString {
                if (css.isNotBlank()) append(css).append('\n')
                if (ext != null) {
                    val files = runCatching { JSONArray(filesJson) }.getOrNull()
                    if (files != null) {
                        for (i in 0 until files.length()) {
                            val rel = files.optString(i).removePrefix("/")
                            val f = File(ext.dirPath, rel)
                            if (f.exists() && f.isFile) append(runCatching { f.readText() }.getOrDefault("")).append('\n')
                        }
                    }
                }
            }
            val resolvedCss = if (ext != null) rewriteExtensionAssetReferences(ext, fullCss) else fullCss
            if (resolvedCss.isBlank()) return
            val js = "(function(){var s=document.createElement('style');s.textContent=${JSONObject.quote(resolvedCss)};(document.head||document.documentElement).appendChild(s);})();"
            mainHandler.post { targetWv.evaluateJavascript(js, null) }
        }

        @JavascriptInterface
        fun downloadsEnqueue(url: String, filename: String): Int {
            if (url.isBlank()) return 0
            val cleanName = filename.trim().takeIf { it.isNotBlank() }
            val entry = BrowserDataStore.enqueueSystemDownload(
                context = context,
                url = url,
                contentDisposition = if (cleanName != null) "attachment; filename=\"$cleanName\"" else null,
                mimeType = null,
                sourceWebView = ownerWebViewProvider(),
                suggestedFilename = cleanName
            )
            mainHandler.post {
                tabActionCallbacks?.onToast("Downloading ${entry.fileName} -> Agentra Downloads")
            }
            return System.currentTimeMillis().toInt().and(0x7FFFFFFF)
        }

        @JavascriptInterface
        fun showNotification(message: String) {
            mainHandler.post { tabActionCallbacks?.onToast(message) }
        }

        private fun LiveTabRecord.toChromeTabJson(activeFlag: Boolean): JSONObject {
            return buildTabJson(this, activeFlag)
        }
    }
}
