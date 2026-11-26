package com.jarvisai.assistant.extension

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val pendingCallbacks = ConcurrentHashMap<String, CompletableDeferred<String>>()
    @Volatile
    private var initStarted = false
    private var readyDeferred = CompletableDeferred<Boolean>()

    @SuppressLint("SetJavaScriptEnabled")
    fun initialize(onReady: () -> Unit) {
        if (initStarted) {
            if (readyDeferred.isCompleted) onReady()
            return
        }
        initStarted = true
        mainHandler.post {
            try {
                webView = WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        allowFileAccessFromFileURLs = false
                        allowUniversalAccessFromFileURLs = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    }

                    // Unified Chrome APIs + legacy MCP bridge
                    addJavascriptInterface(
                        BrowserExtensionRuntimeBridge.ChromeJsBridge(
                            context = context,
                            ownerWebViewProvider = { webView }
                        ),
                        "__jarvis_chrome_bridge"
                    )
                    addJavascriptInterface(McpBridge(), "__android_chrome_bridge")

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                            val msg = "${cm?.message()} (${cm?.sourceId()}:${cm?.lineNumber()})"
                            Log.d(TAG, "[Worker Console] $msg")
                            BrowserExtensionManager.recordLog(extension.id, msg)
                            return true
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val uri = request?.url ?: return true
                            val expectedOrigin = extension.extensionOrigin()
                            val targetOrigin = "${uri.scheme}://${uri.host}"
                            return !(targetOrigin.equals(expectedOrigin, ignoreCase = true) ||
                                uri.scheme.equals("chrome-extension", ignoreCase = true))
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            return BrowserExtensionRuntimeBridge.interceptExtensionRequest(context, request)
                                ?: super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            Log.i(TAG, "Extension worker host ready (${extension.serviceWorkerFile})")
                            BrowserExtensionManager.recordLog(
                                extension.id,
                                "Service worker ready: ${extension.serviceWorkerFile.ifBlank { "default" }}"
                            )
                            // Fire chrome.runtime.onInstalled + setup MCP dispatcher
                            val postInitJs = """
                                (function() {
                                    window.__dispatchExtensionTool = function(toolName, argsJson, callId) {
                                        if (window.onMcpToolCall) {
                                            Promise.resolve(window.onMcpToolCall(toolName, JSON.parse(argsJson)))
                                                .then(function(res) { window.__android_chrome_bridge.onToolSuccess(JSON.stringify(res), callId); })
                                                .catch(function(err) { window.__android_chrome_bridge.onToolError(err.toString(), callId); });
                                        } else {
                                            window.__android_chrome_bridge.onToolError("No tool handler registered in worker", callId);
                                        }
                                    };
                                    var list = (window.__jarvisInstalledListeners && window.__jarvisInstalledListeners[${JSONObject.quote(extension.id)}]) || [];
                                    list.forEach(function(fn) {
                                        try { fn({ reason: 'install', previousVersion: ${JSONObject.quote(extension.version)} }); } catch(e) {}
                                    });
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(postInitJs, null)
                            if (!readyDeferred.isCompleted) readyDeferred.complete(true)
                            onReady()
                        }
                    }

                    loadUrl("${extension.extensionOrigin()}/__jarvis_worker_host.html")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "WebView initialization skipped or unavailable: ${t.message}")
                if (!readyDeferred.isCompleted) readyDeferred.complete(false)
            }
        }
    }

    fun dispatchRuntimeMessage(msgJson: String, senderJson: String, replyKey: String) {
        if (!initStarted) {
            initialize {
                postMessageToWorker(msgJson, senderJson, replyKey)
            }
        } else if (!readyDeferred.isCompleted) {
            readyDeferred.invokeOnCompletion {
                postMessageToWorker(msgJson, senderJson, replyKey)
            }
        } else {
            postMessageToWorker(msgJson, senderJson, replyKey)
        }
    }

    fun dispatchActionClicked(tabJson: String) {
        val runJs = {
            mainHandler.post {
                val wv = webView ?: return@post
                val js = """
                    if (window.__jarvisDispatchActionClicked) {
                        window.__jarvisDispatchActionClicked(
                            ${JSONObject.quote(extension.id)},
                            ${JSONObject.quote(tabJson)}
                        );
                    }
                """.trimIndent()
                wv.evaluateJavascript(js, null)
            }
        }
        if (!initStarted) {
            initialize { runJs() }
        } else if (!readyDeferred.isCompleted) {
            readyDeferred.invokeOnCompletion { runJs() }
        } else {
            runJs()
        }
    }

    private fun postMessageToWorker(msgJson: String, senderJson: String, replyKey: String) {
        mainHandler.post {
            val wv = webView ?: return@post
            val js = """
                if (window.__jarvisDispatchExtMessage) {
                    window.__jarvisDispatchExtMessage(
                        ${JSONObject.quote(extension.id)},
                        ${JSONObject.quote(msgJson)},
                        ${JSONObject.quote(senderJson)},
                        ${JSONObject.quote(replyKey)},
                        'worker'
                    );
                }
            """.trimIndent()
            wv.evaluateJavascript(js, null)
        }
    }

    suspend fun executeTool(toolName: String, args: JSONObject): Result<String> {
        if (!initStarted) {
            initialize {}
        }
        val initialized = kotlinx.coroutines.withTimeoutOrNull(8000L) { readyDeferred.await() } ?: false
        if (!initialized || webView == null) {
            return Result.failure(IllegalStateException("Extension WebView host unavailable"))
        }

        return withContext(Dispatchers.Main) {
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
    }

    suspend fun evalRawJs(script: String): Result<String> {
        if (!initStarted) {
            initialize {}
        }
        val initialized = kotlinx.coroutines.withTimeoutOrNull(8000L) { readyDeferred.await() } ?: false
        if (!initialized || webView == null) {
            return Result.failure(IllegalStateException("Extension worker host unavailable"))
        }
        return withContext(Dispatchers.Main) {
            val deferred = CompletableDeferred<String>()
            webView?.evaluateJavascript(script) { result ->
                deferred.complete(result ?: "undefined")
            }
            Result.success(deferred.await())
        }
    }

    fun destroy() {
        initStarted = false
        readyDeferred = CompletableDeferred()
        mainHandler.post {
            runCatching { webView?.destroy() }
            webView = null
        }
    }

    private inner class McpBridge {
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
