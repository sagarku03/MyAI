package com.jarvisai.assistant.extension

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Matches Chrome extension `content_scripts` patterns against active URLs and injects
 * `chrome.runtime` / `chrome.storage` / CSS / JS into WebView pages, plus bridges
 * extension `popup.html` WebViews to the active browser tab.
 */
object ExtensionPageInjector {

    fun matchesPattern(pageUrl: String, pattern: String): Boolean {
        val trimmedUrl = pageUrl.trim()
        val trimmedPattern = pattern.trim()
        if (trimmedUrl.isEmpty() || trimmedPattern.isEmpty()) return false
        if (trimmedPattern == "<all_urls>") {
            return trimmedUrl.startsWith("http://") ||
                trimmedUrl.startsWith("https://") ||
                trimmedUrl.startsWith("file://")
        }
        if (trimmedPattern == "*" || trimmedPattern == "*://*/*") {
            return trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")
        }

        val schemeSep = trimmedPattern.indexOf("://")
        if (schemeSep <= 0) return false
        val patScheme = trimmedPattern.substring(0, schemeSep).lowercase()
        val rest = trimmedPattern.substring(schemeSep + 3)
        val pathSep = rest.indexOf('/')
        val patHost = if (pathSep >= 0) rest.substring(0, pathSep).lowercase() else rest.lowercase()
        val patPath = if (pathSep >= 0) rest.substring(pathSep) else "/*"

        val urlSchemeSep = trimmedUrl.indexOf("://")
        if (urlSchemeSep <= 0) return false
        val urlScheme = trimmedUrl.substring(0, urlSchemeSep).lowercase()
        val urlRest = trimmedUrl.substring(urlSchemeSep + 3)
        val urlPathSep = urlRest.indexOf('/')
        val urlHostWithPort = if (urlPathSep >= 0) urlRest.substring(0, urlPathSep).lowercase() else urlRest.lowercase()
        val urlHost = urlHostWithPort.substringBefore(':')
        val urlPath = if (urlPathSep >= 0) urlRest.substring(urlPathSep) else "/"

        if (patScheme != "*" && patScheme != urlScheme) return false
        if (patScheme == "*" && urlScheme != "http" && urlScheme != "https") return false

        val hostMatch = when {
            patHost == "*" -> true
            patHost.startsWith("*.") -> {
                val baseDomain = patHost.removePrefix("*.")
                urlHost == baseDomain || urlHost.endsWith(".$baseDomain")
            }
            else -> patHost.substringBefore(':') == urlHost
        }
        if (!hostMatch) return false

        val regexStr = buildString {
            append('^')
            for (ch in patPath) {
                if (ch == '*') append(".*") else append(Regex.escape(ch.toString()))
            }
            append('$')
        }
        val urlPathClean = urlPath.substringBefore('#')
        return runCatching { Regex(regexStr).matches(urlPathClean) }.getOrDefault(false)
    }

    fun matchesContentScript(url: String, spec: ContentScriptSpec): Boolean {
        if (spec.excludeMatches.any { matchesPattern(url, it) }) return false
        return spec.matches.any { matchesPattern(url, it) }
    }

    /**
     * Injects all matching enabled extension CSS and JS scripts for [url] at the requested [runAt] phase.
     */
    fun injectForPage(
        webView: WebView?,
        url: String?,
        extensions: List<BrowserExtension>,
        runAt: String = "document_idle"
    ) {
        if (webView == null || url.isNullOrBlank()) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) return

        val enabled = extensions.filter { it.isEnabled && it.contentScripts.isNotEmpty() }
        if (enabled.isEmpty()) return

        val matchingPairs = enabled.flatMap { ext ->
            ext.contentScripts
                .filter { spec ->
                    val phaseMatches = when (runAt) {
                        "document_start" -> spec.runAt == "document_start"
                        else -> spec.runAt != "document_start"
                    }
                    phaseMatches && matchesContentScript(url, spec)
                }
                .map { ext to it }
        }

        if (matchingPairs.isEmpty()) return

        val scriptBuilder = StringBuilder()
        scriptBuilder.append(buildPageChromeBootstrapJs())

        for ((ext, spec) in matchingPairs) {
            val dir = File(ext.dirPath)
            if (!dir.exists()) continue

            for (cssRel in spec.css) {
                val cssFile = File(dir, cssRel.removePrefix("/"))
                if (cssFile.exists() && cssFile.isFile) {
                    val cssContent = runCatching { cssFile.readText() }.getOrNull().orEmpty()
                    if (cssContent.isNotBlank()) {
                        scriptBuilder.append(
                            """
                            (function(){
                                try {
                                    var st = document.createElement('style');
                                    st.setAttribute('data-jarvis-ext', ${JSONObject.quote(ext.id)});
                                    st.textContent = ${JSONObject.quote(cssContent)};
                                    (document.head || document.documentElement).appendChild(st);
                                } catch(e) {}
                            })();
                            """.trimIndent()
                        ).append('\n')
                    }
                }
            }

            for (jsRel in spec.js) {
                val jsFile = File(dir, jsRel.removePrefix("/"))
                if (jsFile.exists() && jsFile.isFile) {
                    val jsContent = runCatching { jsFile.readText() }.getOrNull().orEmpty()
                    if (jsContent.isNotBlank()) {
                        scriptBuilder.append(
                            """
                            (function() {
                                var chrome = window.__jarvisMakeChrome(${JSONObject.quote(ext.id)}, ${JSONObject.quote("file://${dir.absolutePath}/")});
                                try {
                                    $jsContent
                                } catch(err) {
                                    console.warn('[JarvisExt:' + ${JSONObject.quote(ext.id)} + ']', err);
                                }
                            })();
                            """.trimIndent()
                        ).append('\n')
                    }
                }
            }
        }

        val finalScript = scriptBuilder.toString()
        if (finalScript.isNotBlank()) {
            webView.evaluateJavascript(finalScript, null)
        }
    }

    private fun buildPageChromeBootstrapJs(): String = """
        (function() {
            if (!window.__jarvisMakeChrome) {
                window.__jarvisExtListeners = window.__jarvisExtListeners || {};
                window.__jarvisMakeChrome = function(extId, baseUrl) {
                    var bridge = window.__jarvis_ext_page_bridge;
                    window.__jarvisExtListeners[extId] = window.__jarvisExtListeners[extId] || [];
                    return {
                        runtime: {
                            id: extId,
                            getURL: function(path) {
                                var clean = String(path || '').replace(/^\/+/, '');
                                return baseUrl + clean;
                            },
                            sendMessage: function(msg, cb) {
                                if (bridge) {
                                    var res = bridge.onMessage(extId, JSON.stringify(msg || {}));
                                    if (cb) {
                                        try { cb(JSON.parse(res)); } catch(e) { cb({}); }
                                    }
                                } else if (cb) { cb({}); }
                            },
                            onMessage: {
                                addListener: function(fn) {
                                    window.__jarvisExtListeners[extId].push(fn);
                                }
                            }
                        },
                        storage: {
                            local: {
                                get: function(keys, cb) {
                                    var out = {};
                                    try {
                                        var raw = bridge ? bridge.storageGet(extId) : localStorage.getItem('__jarvis_ext_' + extId);
                                        var all = raw ? JSON.parse(raw) : {};
                                        if (typeof keys === 'string') {
                                            if (all[keys] !== undefined) out[keys] = all[keys];
                                        } else if (Array.isArray(keys)) {
                                            keys.forEach(function(k) { if (all[k] !== undefined) out[k] = all[k]; });
                                        } else if (keys && typeof keys === 'object') {
                                            Object.keys(keys).forEach(function(k) { out[k] = all[k] !== undefined ? all[k] : keys[k]; });
                                        } else {
                                            out = all;
                                        }
                                    } catch(e) {}
                                    if (cb) cb(out);
                                    return Promise.resolve(out);
                                },
                                set: function(items, cb) {
                                    try {
                                        if (bridge) {
                                            bridge.storageSet(extId, JSON.stringify(items || {}));
                                        } else {
                                            var cur = JSON.parse(localStorage.getItem('__jarvis_ext_' + extId) || '{}');
                                            Object.assign(cur, items || {});
                                            localStorage.setItem('__jarvis_ext_' + extId, JSON.stringify(cur));
                                        }
                                    } catch(e) {}
                                    if (cb) cb();
                                    return Promise.resolve();
                                },
                                remove: function(keys, cb) {
                                    if (cb) cb();
                                    return Promise.resolve();
                                }
                            },
                            sync: {
                                get: function(keys, cb) {
                                    return window.__jarvisMakeChrome(extId, baseUrl).storage.local.get(keys, cb);
                                },
                                set: function(items, cb) {
                                    return window.__jarvisMakeChrome(extId, baseUrl).storage.local.set(items, cb);
                                }
                            }
                        },
                        i18n: {
                            getMessage: function(k) { return k || ''; },
                            getUILanguage: function() { return navigator.language || 'en-US'; }
                        }
                    };
                };
            }
        })();
    """.trimIndent() + "\n"

    /**
     * Injected into extension `popup.html` WebView so popups can query the active tab,
     * execute scripts inside the active page WebView, and read/write extension storage.
     */
    fun buildPopupChromeBridgeJs(extension: BrowserExtension): String {
        val baseUrl = "file://${extension.dirPath}/"
        return """
            (function() {
                var bridge = window.__jarvis_popup_bridge;
                var extId = ${JSONObject.quote(extension.id)};
                window.__popupCallbacks = {};
                window.chrome = {
                    runtime: {
                        id: extId,
                        getManifest: function() {
                            return bridge ? JSON.parse(bridge.getManifest()) : {};
                        },
                        getURL: function(p) { return ${JSONObject.quote(baseUrl)} + String(p || '').replace(/^\/+/, ''); },
                        sendMessage: function(msg, cb) {
                            if (bridge) {
                                var res = bridge.sendMessage(JSON.stringify(msg || {}));
                                if (cb) { try { cb(JSON.parse(res)); } catch(e) { cb({ok:true}); } }
                            } else if (cb) { cb({}); }
                        },
                        onMessage: { addListener: function() {} }
                    },
                    storage: {
                        local: {
                            get: function(keys, cb) {
                                var all = bridge ? JSON.parse(bridge.storageGet()) : {};
                                var out = {};
                                if (typeof keys === 'string') {
                                    if (all[keys] !== undefined) out[keys] = all[keys];
                                } else if (Array.isArray(keys)) {
                                    keys.forEach(function(k) { if (all[k] !== undefined) out[k] = all[k]; });
                                } else if (keys && typeof keys === 'object') {
                                    Object.keys(keys).forEach(function(k) { out[k] = all[k] !== undefined ? all[k] : keys[k]; });
                                } else { out = all; }
                                if (cb) cb(out);
                                return Promise.resolve(out);
                            },
                            set: function(items, cb) {
                                if (bridge) bridge.storageSet(JSON.stringify(items || {}));
                                if (cb) cb();
                                return Promise.resolve();
                            }
                        },
                        sync: {
                            get: function(keys, cb) { return window.chrome.storage.local.get(keys, cb); },
                            set: function(items, cb) { return window.chrome.storage.local.set(items, cb); }
                        }
                    },
                    tabs: {
                        query: function(queryInfo, cb) {
                            var list = bridge ? JSON.parse(bridge.tabsQuery()) : [];
                            if (cb) cb(list);
                            return Promise.resolve(list);
                        },
                        create: function(props, cb) {
                            var tab = bridge ? JSON.parse(bridge.tabsCreate(props && props.url ? props.url : 'https://www.google.com')) : {};
                            if (cb) cb(tab);
                            return Promise.resolve(tab);
                        },
                        sendMessage: function(tabId, msg, cb) {
                            if (bridge) bridge.tabSendMessage(String(tabId || ''), JSON.stringify(msg || {}));
                            if (cb) cb({received: true});
                            return Promise.resolve({received: true});
                        }
                    },
                    scripting: {
                        executeScript: function(injection, cb) {
                            if (bridge && injection && injection.func) {
                                var args = Array.isArray(injection.args) ? injection.args.map(JSON.stringify).join(',') : '';
                                var code = '(' + injection.func.toString() + ')(' + args + ');';
                                bridge.executeOnActiveTab(code);
                            } else if (bridge && injection && injection.files && injection.files.length) {
                                bridge.executeFilesOnActiveTab(JSON.stringify(injection.files));
                            }
                            if (cb) cb([{result: true}]);
                            return Promise.resolve([{result: true}]);
                        }
                    },
                    i18n: {
                        getMessage: function(k) { return k || ''; },
                        getUILanguage: function() { return navigator.language || 'en-US'; }
                    }
                };
            })();
        """.trimIndent()
    }

    /**
     * JS interface attached to browsing WebViews (`__jarvis_ext_page_bridge`) so content scripts
     * persist storage cleanly into per-extension SharedPreferences.
     */
    class PageExtensionBridge(private val context: Context) {
        @JavascriptInterface
        fun storageGet(extId: String): String {
            val prefs = context.getSharedPreferences("ext_$extId", Context.MODE_PRIVATE)
            val json = JSONObject()
            prefs.all.forEach { (k, v) -> json.put(k, v) }
            return json.toString()
        }

        @JavascriptInterface
        fun storageSet(extId: String, itemsJson: String) {
            runCatching {
                val prefs = context.getSharedPreferences("ext_$extId", Context.MODE_PRIVATE).edit()
                val obj = JSONObject(itemsJson)
                obj.keys().forEach { k ->
                    val v = obj.opt(k)
                    when (v) {
                        is Boolean -> prefs.putBoolean(k, v)
                        is Int -> prefs.putInt(k, v)
                        is Long -> prefs.putLong(k, v)
                        else -> prefs.putString(k, v?.toString() ?: "")
                    }
                }
                prefs.apply()
            }
        }

        @JavascriptInterface
        fun onMessage(extId: String, msgJson: String): String {
            BrowserExtensionManager.recordLog(extId, "content->runtime msg: ${msgJson.take(120)}")
            return JSONObject().put("received", true).put("extensionId", extId).toString()
        }
    }

    /**
     * JS interface attached to popup WebViews (`__jarvis_popup_bridge`) connecting the popup HTML
     * directly to the currently visible WebView tab.
     */
    class PopupExtensionBridge(
        private val context: Context,
        private val extension: BrowserExtension,
        private val activeTabId: String,
        private val activeTabUrl: String,
        private val activeTabTitle: String,
        private val onEvalActiveTab: (String) -> Unit,
        private val onOpenNewTab: (String) -> Unit
    ) {
        @JavascriptInterface
        fun getManifest(): String {
            return runCatching { File(extension.dirPath, "manifest.json").readText() }.getOrDefault("{}")
        }

        @JavascriptInterface
        fun storageGet(): String {
            val prefs = context.getSharedPreferences("ext_${extension.id}", Context.MODE_PRIVATE)
            val json = JSONObject()
            prefs.all.forEach { (k, v) -> json.put(k, v) }
            return json.toString()
        }

        @JavascriptInterface
        fun storageSet(itemsJson: String) {
            runCatching {
                val prefs = context.getSharedPreferences("ext_${extension.id}", Context.MODE_PRIVATE).edit()
                val obj = JSONObject(itemsJson)
                obj.keys().forEach { k ->
                    val v = obj.opt(k)
                    when (v) {
                        is Boolean -> prefs.putBoolean(k, v)
                        is Int -> prefs.putInt(k, v)
                        else -> prefs.putString(k, v?.toString() ?: "")
                    }
                }
                prefs.apply()
            }
        }

        @JavascriptInterface
        fun tabsQuery(): String {
            return JSONArray().put(
                JSONObject().apply {
                    put("id", activeTabId)
                    put("url", activeTabUrl)
                    put("title", activeTabTitle)
                    put("active", true)
                    put("currentWindow", true)
                }
            ).toString()
        }

        @JavascriptInterface
        fun tabsCreate(url: String): String {
            onOpenNewTab(url)
            return JSONObject().put("url", url).toString()
        }

        @JavascriptInterface
        fun tabSendMessage(tabId: String, msgJson: String) {
            val js = """
                (function(){
                    var list = (window.__jarvisExtListeners && window.__jarvisExtListeners[${JSONObject.quote(extension.id)}]) || [];
                    list.forEach(function(fn){ try { fn($msgJson, {id: ${JSONObject.quote(extension.id)}}, function(){}); } catch(e){} });
                })();
            """.trimIndent()
            onEvalActiveTab(js)
        }

        @JavascriptInterface
        fun executeOnActiveTab(code: String) {
            onEvalActiveTab(code)
        }

        @JavascriptInterface
        fun executeFilesOnActiveTab(filesJson: String) {
            runCatching {
                val arr = JSONArray(filesJson)
                for (i in 0 until arr.length()) {
                    val fName = arr.optString(i).removePrefix("/")
                    val file = File(extension.dirPath, fName)
                    if (file.exists()) {
                        onEvalActiveTab(file.readText())
                    }
                }
            }
        }

        @JavascriptInterface
        fun sendMessage(msgJson: String): String {
            BrowserExtensionManager.recordLog(extension.id, "popup->runtime msg: ${msgJson.take(120)}")
            return JSONObject().put("ok", true).toString()
        }
    }
}
