package com.jarvisai.assistant.extension

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.jarvisai.assistant.browser.BrowserDataStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.net.URLConnection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unified Chrome Extensions Runtime Bridge (`MV2` + `MV3`).
 *
 * Bridges all contexts identically to desktop Chromium / Jarvis:
 * 1. Virtual HTTPS extension origin (`https://ext-<id>.jarvis.invalid/...` + `chrome-extension://<id>/...`)
 *    intercepted in WebView so ES modules (`<script type="module">`), `fetch()`, and assets work with zero CORS issues.
 * 2. Numeric integer tab IDs (`1, 2, 3...`) tracking both web tabs and extension UI tabs,
 *    preserving `lastActiveWebTabId` so popups/sidepanels query the real webpage (e.g. `flow.google.com`).
 * 3. Full two-way async messaging (`runtime.sendMessage` <-> worker/pages, `tabs.sendMessage` <-> content scripts)
 *    plus `chrome.scripting.executeScript` / `insertCSS` and `chrome.storage` + `chrome.downloads`.
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
        var webView: WebView?
    )

    private val tabNumericIds = ConcurrentHashMap<String, Int>()
    private val liveTabs = ConcurrentHashMap<String, LiveTabRecord>()
    private val pageContentWebViews = ConcurrentHashMap<String, WebView>() // tabId -> WebView

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
        webView: WebView?
    ): Int {
        val numId = numericIdFor(tabId)
        val record = liveTabs.getOrPut(tabId) {
            LiveTabRecord(tabId, numId, url, title, isActive, isPrivate, isExtensionPage, webView)
        }
        record.url = url
        record.title = title
        record.isActive = isActive
        record.isPrivate = isPrivate
        record.isExtensionPage = isExtensionPage
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
        return numId
    }

    fun unregisterTab(tabId: String) {
        liveTabs.remove(tabId)
        pageContentWebViews.remove(tabId)
        if (lastActiveWebTabId == tabId) {
            lastActiveWebTabId = liveTabs.values.firstOrNull { !it.isExtensionPage }?.tabId.orEmpty()
        }
    }

    fun findTabByNumericId(numericId: Int): LiveTabRecord? {
        return liveTabs.values.firstOrNull { it.numericId == numericId }
            ?: liveTabs.values.firstOrNull { it.tabId == lastActiveWebTabId }
            ?: liveTabs.values.firstOrNull { !it.isExtensionPage }
            ?: liveTabs.values.firstOrNull()
    }

    /**
     * Extracts extension ID & relative path from either:
     * - `https://ext-<id>.jarvis.invalid/<path>`
     * - `chrome-extension://<id>/<path>`
     */
    fun parseExtensionResourceUrl(rawUrl: String?): Pair<String, String>? {
        if (rawUrl.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()

        val extId = when {
            scheme == "chrome-extension" && host.isNotBlank() -> host
            scheme == "https" && host.startsWith("ext-") && host.endsWith(".jarvis.invalid") ->
                host.removePrefix("ext-").removeSuffix(".jarvis.invalid")
            else -> return null
        }
        val relPath = uri.encodedPath?.trimStart('/').orEmpty()
        return extId to relPath
    }

    /**
     * Serves files directly from the extension's directory via `WebViewClient.shouldInterceptRequest`.
     * Automatically injects `window.chrome` polyfill into the top of `<head>` for `.html` files
     * so scripts and ES modules inside `popup.html` / `sidepanel.html` have `chrome.*` ready at line 1!
     */
    fun interceptExtensionRequest(
        context: Context,
        request: WebResourceRequest?,
        defaultTabNumericId: Int = 1
    ): WebResourceResponse? {
        val rawUrl = request?.url?.toString() ?: return null
        val (extId, relPathRaw) = parseExtensionResourceUrl(rawUrl) ?: return null
        val ext = BrowserExtensionManager.extensions.value.firstOrNull {
            it.id.equals(extId, ignoreCase = true)
        } ?: return null

        val cleanPath = Uri.decode(relPathRaw).ifBlank { ext.uiEntryPage.ifBlank { "index.html" } }

        // Special virtual service worker host HTML (`/__jarvis_worker_host.html`)
        if (cleanPath == "__jarvis_worker_host.html") {
            val workerTag = if (ext.serviceWorkerFile.isNotBlank()) {
                val safeSrc = ext.serviceWorkerFile.removePrefix("/")
                if (ext.backgroundIsModule) {
                    "<script type=\"module\" src=\"/$safeSrc\"></script>"
                } else {
                    "<script src=\"/$safeSrc\"></script>"
                }
            } else ""
            val html = """
                <!DOCTYPE html>
                <html><head><meta charset="utf-8">
                <script>${buildUnifiedChromePolyfillJs(ext, "worker", defaultTabNumericId)}</script>
                </head><body>$workerTag</body></html>
            """.trimIndent()
            return WebResourceResponse(
                "text/html",
                "UTF-8",
                200,
                "OK",
                mapOf("Access-Control-Allow-Origin" to "*"),
                ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
            )
        }

        val rootDir = File(ext.dirPath).canonicalFile
        val targetFile = File(rootDir, cleanPath).canonicalFile
        if (!targetFile.path.startsWith(rootDir.path) || !targetFile.exists() || !targetFile.isFile) {
            return null
        }

        val mime = guessMimeType(targetFile.name)
        val headers = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
            "Access-Control-Allow-Headers" to "*"
        )

        if (mime == "text/html") {
            val rawHtml = runCatching { targetFile.readText() }.getOrDefault("")
            val polyfillScript = "<script>\n${buildUnifiedChromePolyfillJs(ext, "ui", defaultTabNumericId)}\n</script>"
            val patchedHtml = injectScriptIntoHead(rawHtml, polyfillScript)
            return WebResourceResponse(
                "text/html",
                "UTF-8",
                200,
                "OK",
                headers,
                ByteArrayInputStream(patchedHtml.toByteArray(Charsets.UTF_8))
            )
        }

        return WebResourceResponse(
            mime,
            if (mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json")) "UTF-8" else null,
            200,
            "OK",
            headers,
            FileInputStream(targetFile)
        )
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
        val manifestJson = runCatching { File(ext.dirPath, "manifest.json").readText() }.getOrDefault("{}")
        val safeManifest = try { JSONObject(manifestJson).toString() } catch (_: Exception) { "{}" }

        return """
        (function() {
            if (typeof window.self === 'undefined') { window.self = window; }
            window.__jarvisCtxType = ${JSONObject.quote(contextType)};
            window.__jarvisTabId = $tabNumericId;
            window.__jarvisExtCbs = window.__jarvisExtCbs || {};
            window.__jarvisMakeChromeApi = function(extId, originBase, manifestObj) {
                var B = window.__jarvis_chrome_bridge;
                var listeners = (window.__jarvisMsgListeners = window.__jarvisMsgListeners || {});
                listeners[extId] = listeners[extId] || [];
                var storageListeners = (window.__jarvisStorageListeners = window.__jarvisStorageListeners || {});
                storageListeners[extId] = storageListeners[extId] || [];
                var installedListeners = (window.__jarvisInstalledListeners = window.__jarvisInstalledListeners || {});
                installedListeners[extId] = installedListeners[extId] || [];
                var tabUpdatedListeners = (window.__jarvisTabUpdatedListeners = window.__jarvisTabUpdatedListeners || {});
                tabUpdatedListeners[extId] = tabUpdatedListeners[extId] || [];

                function resolvePromiseOrCb(val, cb) {
                    if (typeof cb === 'function') { try { cb(val); } catch(e) {} }
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
                                    resolve(undefined);
                                }
                            });
                        },
                        connect: function(connectInfo) {
                            var portName = (connectInfo && connectInfo.name) ? connectInfo.name : 'default';
                            return {
                                name: portName,
                                postMessage: function(msg) {
                                    api.runtime.sendMessage({ __port: portName, data: msg });
                                },
                                onMessage: { addListener: function() {} },
                                onDisconnect: { addListener: function() {} },
                                disconnect: function() {}
                            };
                        },
                        onMessage: {
                            addListener: function(fn) { if (typeof fn === 'function') listeners[extId].push(fn); },
                            removeListener: function(fn) {
                                listeners[extId] = listeners[extId].filter(function(f){ return f !== fn; });
                            },
                            hasListener: function(fn) { return listeners[extId].indexOf(fn) !== -1; }
                        },
                        onInstalled: {
                            addListener: function(fn) { if (typeof fn === 'function') installedListeners[extId].push(fn); }
                        },
                        onConnect: { addListener: function() {} },
                        openOptionsPage: function(cb) {
                            var p = (manifestObj.options_ui && manifestObj.options_ui.page) || manifestObj.options_page;
                            if (p) api.tabs.create({ url: api.runtime.getURL(p) });
                            return resolvePromiseOrCb(undefined, cb);
                        }
                    },
                    storage: {
                        local: localArea,
                        sync: localArea,
                        session: makeStorageArea('session'),
                        onChanged: {
                            addListener: function(fn) { if (typeof fn === 'function') storageListeners[extId].push(fn); },
                            removeListener: function(fn) {
                                storageListeners[extId] = storageListeners[extId].filter(function(f){ return f !== fn; });
                            }
                        }
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
                            var hit = all.find(function(t){ return t.id === tabId; }) || null;
                            return resolvePromiseOrCb(hit, cb);
                        },
                        create: function(props, cb) {
                            var u = (props && props.url) ? String(props.url) : 'https://www.google.com';
                            var makeActive = (props && props.active !== undefined) ? !!props.active : true;
                            var created = B ? JSON.parse(B.tabsCreate(u, makeActive)) : { id: 1, url: u, active: makeActive };
                            return resolvePromiseOrCb(created, cb);
                        },
                        update: function(tabId, props, cb) {
                            if (typeof tabId === 'object') { cb = props; props = tabId; tabId = null; }
                            var res = B ? JSON.parse(B.tabsUpdate(tabId ? Number(tabId) : -1, JSON.stringify(props || {}))) : {};
                            return resolvePromiseOrCb(res, cb);
                        },
                        remove: function(tabIds, cb) {
                            var list = Array.isArray(tabIds) ? tabIds : [tabIds];
                            list.forEach(function(id) { if (B) B.tabsRemove(Number(id)); });
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        reload: function(tabId, cb) {
                            if (typeof tabId === 'function') { cb = tabId; tabId = -1; }
                            if (B) B.tabsReload(tabId ? Number(tabId) : -1);
                            return resolvePromiseOrCb(undefined, cb);
                        },
                        sendMessage: function(tabId, msg, optOrCb, maybeCb) {
                            var cb = (typeof optOrCb === 'function') ? optOrCb : maybeCb;
                            return new Promise(function(resolve) {
                                var cid = nextCbId(function(res) {
                                    if (cb) { try { cb(res); } catch(e) {} }
                                    resolve(res);
                                });
                                if (B) {
                                    B.tabsSendMessage(extId, Number(tabId), JSON.stringify(msg === undefined ? {} : msg), cid);
                                } else {
                                    resolve(undefined);
                                }
                            });
                        },
                        onUpdated: { addListener: function(fn) { if (typeof fn === 'function') tabUpdatedListeners[extId].push(fn); } },
                        onActivated: { addListener: function() {} },
                        onRemoved: { addListener: function() {} }
                    },
                    scripting: {
                        executeScript: function(injection, cb) {
                            return new Promise(function(resolve) {
                                var tabId = (injection && injection.target && injection.target.tabId) ? Number(injection.target.tabId) : -1;
                                var code = '';
                                if (injection && injection.func) {
                                    var args = Array.isArray(injection.args) ? injection.args.map(function(a){ return JSON.stringify(a); }).join(',') : '';
                                    code = '(' + injection.func.toString() + ')(' + args + ')';
                                } else if (injection && injection.code) {
                                    code = String(injection.code);
                                }
                                var filesJson = JSON.stringify((injection && injection.files) ? injection.files : []);
                                var cid = nextCbId(function(resArr) {
                                    if (cb) { try { cb(resArr); } catch(e) {} }
                                    resolve(resArr);
                                });
                                if (B) B.scriptingExecute(extId, tabId, code, filesJson, cid);
                                else resolve([{ result: null }]);
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
                        registerContentScripts: function(scripts, cb) { return resolvePromiseOrCb(undefined, cb); }
                    },
                    sidePanel: {
                        setPanelBehavior: function(opts, cb) { return resolvePromiseOrCb(undefined, cb); },
                        setOptions: function(opts, cb) { return resolvePromiseOrCb(undefined, cb); },
                        open: function(opts, cb) {
                            var p = (manifestObj.side_panel && manifestObj.side_panel.default_path) || manifestObj.action && manifestObj.action.default_popup;
                            if (p && B) B.tabsCreate(api.runtime.getURL(p), true);
                            return resolvePromiseOrCb(undefined, cb);
                        }
                    },
                    action: {
                        setBadgeText: function(d, cb) { return resolvePromiseOrCb(undefined, cb); },
                        setBadgeBackgroundColor: function(d, cb) { return resolvePromiseOrCb(undefined, cb); },
                        setIcon: function(d, cb) { return resolvePromiseOrCb(undefined, cb); },
                        onClicked: { addListener: function() {} }
                    },
                    browserAction: {
                        setBadgeText: function(d, cb) { return resolvePromiseOrCb(undefined, cb); },
                        setBadgeBackgroundColor: function(d, cb) { return resolvePromiseOrCb(undefined, cb); },
                        onClicked: { addListener: function() {} }
                    },
                    downloads: {
                        download: function(opts, cb) {
                            var u = (opts && opts.url) ? String(opts.url) : '';
                            var fname = (opts && opts.filename) ? String(opts.filename) : '';
                            var dlId = B ? B.downloadsEnqueue(u, fname) : 1;
                            return resolvePromiseOrCb(dlId, cb);
                        },
                        onChanged: { addListener: function() {} }
                    },
                    notifications: {
                        create: function(idOrOpts, optsOrCb, cb) {
                            var o = (typeof idOrOpts === 'object') ? idOrOpts : optsOrCb;
                            var msg = (o && (o.message || o.title)) ? String(o.title || '') + ' ' + String(o.message || '') : 'Notification';
                            if (B) B.showNotification(msg.trim());
                            var callback = (typeof optsOrCb === 'function') ? optsOrCb : cb;
                            return resolvePromiseOrCb('notif_1', callback);
                        }
                    },
                    permissions: {
                        contains: function(p, cb) { return resolvePromiseOrCb(true, cb); },
                        request: function(p, cb) { return resolvePromiseOrCb(true, cb); },
                        getAll: function(cb) { return resolvePromiseOrCb({ permissions: manifestObj.permissions || [] }, cb); }
                    },
                    alarms: {
                        create: function() {},
                        clear: function(n, cb) { return resolvePromiseOrCb(true, cb); },
                        getAll: function(cb) { return resolvePromiseOrCb([], cb); },
                        onAlarm: { addListener: function() {} }
                    },
                    i18n: {
                        getMessage: function(k) { return k || ''; },
                        getUILanguage: function() { return navigator.language || 'en-US'; }
                    }
                };
                return api;
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
                    } catch(e) { console.warn('[JarvisExt] listener error', e); }
                }
                if (!keptOpen && !responded && replyCallbackId && window.__jarvis_chrome_bridge) {
                    window.__jarvis_chrome_bridge.deliverAsyncReply(replyCallbackId, 'null', originContextType || 'worker');
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
                "window.chrome = window.__jarvisMakeChromeApi(${JSONObject.quote(ext.id)}, ${JSONObject.quote(baseOrigin)}, $safeManifest);"
            } else {
                ""
            }
        }
        })();
        """.trimIndent()
    }

    private val replyTargets = ConcurrentHashMap<String, Pair<WebView?, String>>() // replyId -> (callerWebView, callerCbId)

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
            if (v is String && ((v.startsWith("{") && v.endsWith("}")) || (v.startsWith("[") && v.endsWith("]")))) {
                return runCatching {
                    if (v.startsWith("[")) JSONArray(v) else JSONObject(v)
                }.getOrDefault(v)
            }
            return v ?: JSONObject.NULL
        }

        @JavascriptInterface
        fun storageSet(extId: String, area: String, itemsJson: String) {
            runCatching {
                val sp = context.getSharedPreferences("ext_${extId}_$area", Context.MODE_PRIVATE)
                val editor = sp.edit()
                val obj = JSONObject(itemsJson)
                obj.keys().forEach { k ->
                    val v = obj.opt(k)
                    when (v) {
                        is Boolean -> editor.putBoolean(k, v)
                        is Int -> editor.putInt(k, v)
                        is Long -> editor.putLong(k, v)
                        else -> editor.putString(k, v?.toString() ?: "")
                    }
                }
                editor.apply()
            }
        }

        @JavascriptInterface
        fun storageRemove(extId: String, area: String, keysJson: String) {
            runCatching {
                val arr = JSONArray(keysJson)
                val editor = context.getSharedPreferences("ext_${extId}_$area", Context.MODE_PRIVATE).edit()
                for (i in 0 until arr.length()) {
                    editor.remove(arr.optString(i))
                }
                editor.apply()
            }
        }

        @JavascriptInterface
        fun storageClear(extId: String, area: String) {
            context.getSharedPreferences("ext_${extId}_$area", Context.MODE_PRIVATE).edit().clear().apply()
        }

        @JavascriptInterface
        fun tabsQuery(callerCtxType: String, queryJson: String): String {
            val q = runCatching { JSONObject(queryJson) }.getOrElse { JSONObject() }
            val wantActive = if (q.has("active")) q.optBoolean("active") else null
            val wantUrlPattern = q.optString("url", "")

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
                val isTabActive = rec.tabId == effectiveActiveId || rec.isActive
                if (wantActive != null && isTabActive != wantActive) continue
                if (wantUrlPattern.isNotBlank() && !ExtensionPageInjector.matchesPattern(rec.url, wantUrlPattern)) continue
                out.put(rec.toChromeTabJson(isTabActive))
            }
            return out.toString()
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

            val senderObj = JSONObject().apply {
                put("id", extId)
                if (callerTabId > 0) {
                    findTabByNumericId(callerTabId)?.let { t ->
                        put("tab", t.toChromeTabJson(t.isActive))
                        put("url", t.url)
                    }
                }
            }.toString()

            BrowserExtensionManager.recordLog(extId, "[$callerCtxType] sendMessage: ${msgJson.take(100)}")

            // Route to background worker + any open extension UI tabs
            val dispatchSuccess = BrowserExtensionManager.dispatchRuntimeMessageToWorker(
                extId = extId,
                msgJson = msgJson,
                senderJson = senderObj,
                replyKey = replyKey
            )
            if (!dispatchSuccess && callerWv != null) {
                mainHandler.post {
                    callerWv.evaluateJavascript("window.__jarvisResolveCallback('$cbId', 'null');", null)
                }
                replyTargets.remove(replyKey)
            }
        }

        @JavascriptInterface
        fun tabsSendMessage(extId: String, numericTabId: Int, msgJson: String, cbId: String) {
            val callerWv = ownerWebViewProvider()
            val target = findTabByNumericId(numericTabId)
            val targetWv = target?.webView
            if (targetWv == null) {
                callerWv?.post {
                    callerWv.evaluateJavascript("window.__jarvisResolveCallback('$cbId', 'null');", null)
                }
                return
            }

            val replyKey = "${extId}_tab_${System.nanoTime()}"
            replyTargets[replyKey] = callerWv to cbId
            val senderJson = JSONObject().put("id", extId).toString()

            mainHandler.post {
                val js = "if (window.__jarvisDispatchExtMessage) { window.__jarvisDispatchExtMessage(${JSONObject.quote(extId)}, ${JSONObject.quote(msgJson)}, ${JSONObject.quote(senderJson)}, ${JSONObject.quote(replyKey)}, 'content'); }"
                targetWv.evaluateJavascript(js, null)
            }
        }

        @JavascriptInterface
        fun deliverAsyncReply(replyKey: String, replyJson: String, fromCtx: String) {
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
            val callerWv = ownerWebViewProvider()
            val target = findTabByNumericId(numericTabId)
            val targetWv = target?.webView
            val ext = BrowserExtensionManager.extensions.value.firstOrNull { it.id == extId }

            if (targetWv == null) {
                callerWv?.post {
                    callerWv.evaluateJavascript("window.__jarvisResolveCallback('$cbId', '[{\"result\":null}]');", null)
                }
                return
            }

            val combinedCode = buildString {
                if (ext != null) {
                    append(buildUnifiedChromePolyfillJs(ext, "content", target.numericId)).append('\n')
                    append("var chrome = window.__jarvisMakeChromeApi(${JSONObject.quote(ext.id)}, ${JSONObject.quote(ext.extensionOrigin())}, {});\n")
                    val files = runCatching { JSONArray(filesJson) }.getOrNull()
                    if (files != null) {
                        for (i in 0 until files.length()) {
                            val rel = files.optString(i).removePrefix("/")
                            val f = File(ext.dirPath, rel)
                            if (f.exists() && f.isFile) {
                                append(runCatching { f.readText() }.getOrDefault("")).append('\n')
                            }
                        }
                    }
                }
                if (inlineCode.isNotBlank()) {
                    append(inlineCode)
                }
            }

            mainHandler.post {
                val wrapped = "(function(){ try { return JSON.stringify({ result: (function(){ $combinedCode })() }); } catch(e) { return JSON.stringify({ error: e.message }); } })();"
                targetWv.evaluateJavascript(wrapped) { raw ->
                    val parsed = runCatching {
                        val unquoted = JSONObject("{\"v\":$raw}").optString("v", "{}")
                        JSONObject(unquoted)
                    }.getOrElse { JSONObject().put("result", JSONObject.NULL) }
                    val resArr = JSONArray().put(parsed).toString()
                    callerWv?.evaluateJavascript(
                        "window.__jarvisResolveCallback('$cbId', ${JSONObject.quote(resArr)});",
                        null
                    )
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
            if (fullCss.isBlank()) return
            val js = "(function(){var s=document.createElement('style');s.textContent=${JSONObject.quote(fullCss)};(document.head||document.documentElement).appendChild(s);})();"
            mainHandler.post { targetWv.evaluateJavascript(js, null) }
        }

        @JavascriptInterface
        fun downloadsEnqueue(url: String, filename: String): Int {
            if (url.isBlank()) return 0
            BrowserDataStore.enqueueSystemDownload(
                context = context,
                url = url,
                contentDisposition = if (filename.isNotBlank()) "attachment; filename=\"$filename\"" else null,
                mimeType = null
            )
            mainHandler.post {
                tabActionCallbacks?.onToast("Downloading ${filename.ifBlank { "file" }}")
            }
            return System.currentTimeMillis().toInt().and(0x7FFFFFFF)
        }

        @JavascriptInterface
        fun showNotification(message: String) {
            mainHandler.post { tabActionCallbacks?.onToast(message) }
        }

        private fun LiveTabRecord.toChromeTabJson(activeFlag: Boolean): JSONObject {
            return JSONObject().apply {
                put("id", numericId)
                put("index", numericId - 1)
                put("windowId", 1)
                put("url", url)
                put("title", title)
                put("active", activeFlag)
                put("incognito", isPrivate)
                put("status", "complete")
            }
        }
    }
}
