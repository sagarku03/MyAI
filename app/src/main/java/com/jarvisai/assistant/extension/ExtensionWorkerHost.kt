package com.jarvisai.assistant.extension

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
import com.jarvisai.assistant.browser.BrowserSessionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ExtensionWorkerHost(
    private val context: Context,
    val extension: BrowserExtension
) {
    private val TAG = "ExtensionWorkerHost[${extension.id}]"
    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val workerScope = CoroutineScope(Dispatchers.Main)
    private val pendingCallbacks = ConcurrentHashMap<String, CompletableDeferred<String>>()

    @SuppressLint("SetJavaScriptEnabled")
    fun initialize(onReady: () -> Unit) {
        mainHandler.post {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true

                addJavascriptInterface(ChromeBridge(), "__android_chrome_bridge")

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                        Log.d(TAG, "[Worker Console] ${cm?.message()} (${cm?.sourceId()}:${cm?.lineNumber()})")
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.i(TAG, "Extension worker host environment initialized")
                        injectChromePolyfills()
                        loadServiceWorker()
                        onReady()
                    }
                }

                loadDataWithBaseURL("https://extension.local/", "<html><head></head><body></body></html>", "text/html", "UTF-8", null)
            }
        }
    }

    private fun injectChromePolyfills() {
        val polyfill = """
            (function() {
                window.chrome = {
                    runtime: {
                        sendMessage: function(msg, callback) {
                            var callId = Math.random().toString(36).substring(7);
                            if (callback) window.__callbacks[callId] = callback;
                            window.__android_chrome_bridge.onRuntimeMessage(JSON.stringify(msg), callId);
                        },
                        onMessage: {
                            addListener: function(listener) { window.__messageListeners.push(listener); }
                        }
                    },
                    storage: {
                        local: {
                            get: function(keys, callback) {
                                var res = window.__android_chrome_bridge.storageGet(JSON.stringify(keys));
                                if (callback) callback(JSON.parse(res));
                            },
                            set: function(items, callback) {
                                window.__android_chrome_bridge.storageSet(JSON.stringify(items));
                                if (callback) callback();
                            }
                        }
                    },
                    tabs: {
                        query: function(info, callback) {
                            var res = window.__android_chrome_bridge.tabsQuery();
                            if (callback) callback(JSON.parse(res));
                        },
                        create: function(createProperties, callback) {
                            var url = (createProperties && createProperties.url) ? createProperties.url : "about:blank";
                            var res = window.__android_chrome_bridge.tabsCreate(url);
                            if (callback) callback(JSON.parse(res));
                        },
                        sendMessage: function(tabId, message, callback) {
                            var callId = Math.random().toString(36).substring(7);
                            if (callback) window.__callbacks[callId] = callback;
                            window.__android_chrome_bridge.tabsSendMessage(String(tabId), JSON.stringify(message), callId);
                        }
                    },
                    scripting: {
                        executeScript: function(injection, callback) {
                            var tabId = (injection && injection.target && injection.target.tabId) ? String(injection.target.tabId) : "";
                            var code = "";
                            if (injection && injection.func) {
                                var args = (injection.args && Array.isArray(injection.args)) ? injection.args.map(function(a) { return JSON.stringify(a); }).join(",") : "";
                                code = "(" + injection.func.toString() + ")(" + args + ")";
                            } else if (injection && injection.code) {
                                code = String(injection.code);
                            }
                            var callId = Math.random().toString(36).substring(7);
                            if (callback) window.__callbacks[callId] = callback;
                            window.__android_chrome_bridge.scriptingExecuteScript(tabId, code, callId);
                        }
                    }
                };
                window.__callbacks = {};
                window.__messageListeners = [];
                window.__dispatchExtensionTool = function(toolName, argsJson, callId) {
                    if (window.onMcpToolCall) {
                        Promise.resolve(window.onMcpToolCall(toolName, JSON.parse(argsJson)))
                            .then(function(res) { window.__android_chrome_bridge.onToolSuccess(JSON.stringify(res), callId); })
                            .catch(function(err) { window.__android_chrome_bridge.onToolError(err.toString(), callId); });
                    } else {
                        window.__android_chrome_bridge.onToolError("No tool handler registered in worker", callId);
                    }
                };
            })();
        """.trimIndent()
        webView?.evaluateJavascript(polyfill, null)
    }

    private fun loadServiceWorker() {
        val workerFile = File(extension.dirPath, extension.serviceWorkerFile)
        if (workerFile.exists()) {
            val script = workerFile.readText()
            webView?.evaluateJavascript(script) { _ ->
                Log.i(TAG, "Loaded service worker: ${extension.serviceWorkerFile}")
            }
        } else {
            Log.w(TAG, "Service worker script not found at ${workerFile.absolutePath}")
        }
    }

    suspend fun executeTool(toolName: String, args: JSONObject): Result<String> = withContext(Dispatchers.Main) {
        val callId = java.util.UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String>()
        pendingCallbacks[callId] = deferred

        val jsCall = "window.__dispatchExtensionTool('$toolName', ${JSONObject.quote(args.toString())}, '$callId');"
        webView?.evaluateJavascript(jsCall, null)

        try {
            val response = deferred.await()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            pendingCallbacks.remove(callId)
        }
    }

    fun destroy() {
        mainHandler.post {
            webView?.destroy()
            webView = null
        }
    }

    private inner class ChromeBridge {
        @JavascriptInterface
        fun onRuntimeMessage(msg: String, callId: String) {
            mainHandler.post {
                val dispatchJs = """
                    (function() {
                        var payload = $msg;
                        var sendResponse = function(resp) {
                            if (window.__callbacks['$callId']) {
                                window.__callbacks['$callId'](resp);
                                delete window.__callbacks['$callId'];
                            }
                        };
                        for (var i = 0; i < window.__messageListeners.length; i++) {
                            try {
                                window.__messageListeners[i](payload, { id: '${extension.id}' }, sendResponse);
                            } catch (e) {}
                        }
                    })();
                """.trimIndent()
                webView?.evaluateJavascript(dispatchJs, null)
            }
        }

        @JavascriptInterface
        fun storageGet(keys: String): String {
            val prefs = context.getSharedPreferences("ext_${extension.id}", Context.MODE_PRIVATE)
            val json = JSONObject()
            prefs.all.forEach { (k, v) -> json.put(k, v) }
            return json.toString()
        }

        @JavascriptInterface
        fun storageSet(itemsJson: String) {
            val prefs = context.getSharedPreferences("ext_${extension.id}", Context.MODE_PRIVATE).edit()
            val obj = JSONObject(itemsJson)
            obj.keys().forEach { k -> prefs.putString(k, obj.optString(k)) }
            prefs.apply()
        }

        @JavascriptInterface
        fun tabsQuery(): String {
            val tabs = BrowserSessionManager.instance.listTabs()
            val arr = JSONArray()
            tabs.forEach { arr.put(JSONObject().apply { put("id", it.id); put("url", it.url); put("title", it.title) }) }
            return arr.toString()
        }

        @JavascriptInterface
        fun tabsCreate(url: String): String {
            val tab = BrowserSessionManager.instance.createTab(url)
            return JSONObject().apply {
                put("id", tab.id)
                put("url", tab.url)
                put("title", tab.title)
            }.toString()
        }

        @JavascriptInterface
        fun tabsSendMessage(tabId: String, messageJson: String, callId: String) {
            workerScope.launch {
                val targetId = tabId.ifBlank { BrowserSessionManager.instance.getActiveTab()?.id.orEmpty() }
                if (targetId.isNotBlank()) {
                    val res = BrowserSessionManager.instance.evalJs(
                        targetId,
                        "window.onExtensionTabMessage ? JSON.stringify(window.onExtensionTabMessage($messageJson)) : JSON.stringify({received: true});"
                    )
                    val payload = res.getOrDefault("{\"received\":false}")
                    webView?.evaluateJavascript(
                        "if (window.__callbacks['$callId']) { window.__callbacks['$callId']($payload); delete window.__callbacks['$callId']; }",
                        null
                    )
                }
            }
        }

        @JavascriptInterface
        fun scriptingExecuteScript(tabId: String, code: String, callId: String) {
            workerScope.launch {
                val targetId = tabId.ifBlank { BrowserSessionManager.instance.getActiveTab()?.id.orEmpty() }
                if (targetId.isNotBlank() && code.isNotBlank()) {
                    val res = BrowserSessionManager.instance.evalJs(targetId, code)
                    val resultValue = res.getOrDefault("null")
                    val resultsArray = JSONArray().apply {
                        put(JSONObject().apply { put("result", resultValue) })
                    }
                    webView?.evaluateJavascript(
                        "if (window.__callbacks['$callId']) { window.__callbacks['$callId'](${resultsArray}); delete window.__callbacks['$callId']; }",
                        null
                    )
                }
            }
        }

        @JavascriptInterface
        fun onToolSuccess(resultJson: String, callId: String) {
            pendingCallbacks[callId]?.complete(resultJson)
        }

        @JavascriptInterface
        fun onToolError(errorMsg: String, callId: String) {
            pendingCallbacks[callId]?.completeExceptionally(Exception(errorMsg))
        }
    }
}
