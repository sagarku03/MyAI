package com.jarvisai.assistant.browser.mcp

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import com.jarvisai.assistant.browser.BrowserSessionManager
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream

object BrowserMcp {
    private const val TAG = "BrowserMcp"
    private var activeCdpSession: CdpSession? = null

    private suspend fun getOrConnectCdp(): CdpSession? {
        val current = activeCdpSession
        if (current != null && !current.closed) return current
        return runCatching {
            CdpSession.connect().also { activeCdpSession = it }
        }.onFailure {
            Log.d(TAG, "CDP socket fallback to WebView JS bridge: ${it.message}")
        }.getOrNull()
    }

    fun getToolDefinitions(): JSONArray = JSONArray().apply {
        put(buildToolDef("browser_open_url", "Opens a URL in the built-in browser", JSONObject()
            .put("type", "object")
            .put("required", JSONArray().put("url"))
            .put("properties", JSONObject().put("url", JSONObject().put("type", "string")))
        ))
        put(buildToolDef("browser_read_page", "Inspects current page structure and returns interactive element references (ref_12)", JSONObject()
            .put("type", "object")
            .put("properties", JSONObject())
        ))
        put(buildToolDef("browser_find", "Finds an interactive button or field by visual/textual description", JSONObject()
            .put("type", "object")
            .put("required", JSONArray().put("description"))
            .put("properties", JSONObject().put("description", JSONObject().put("type", "string")))
        ))
        put(buildToolDef("browser_computer", "Executes clicks, typing, keypresses, scrolls, or takes screenshots", JSONObject()
            .put("type", "object")
            .put("required", JSONArray().put("action"))
            .put("properties", JSONObject()
                .put("action", JSONObject().put("type", "string").put("enum", JSONArray(listOf("left_click", "type", "key", "hover", "scroll_to", "screenshot"))))
                .put("x", JSONObject().put("type", "number"))
                .put("y", JSONObject().put("type", "number"))
                .put("text", JSONObject().put("type", "string"))
                .put("key", JSONObject().put("type", "string"))
                .put("ref", JSONObject().put("type", "string"))
            )
        ))
        put(buildToolDef("browser_form_input", "Fills form inputs, selects options, or checks boxes by ref", JSONObject()
            .put("type", "object")
            .put("required", JSONArray(listOf("ref", "value")))
            .put("properties", JSONObject()
                .put("ref", JSONObject().put("type", "string"))
                .put("value", JSONObject().put("type", "string"))
            )
        ))
        put(buildToolDef("browser_get_page_text", "Extracts clean readable article/page text", JSONObject()
            .put("type", "object")
            .put("properties", JSONObject())
        ))
        put(buildToolDef("browser_lock", "Locks/unlocks user touches during multi-step automated execution", JSONObject()
            .put("type", "object")
            .put("properties", JSONObject().put("locked", JSONObject().put("type", "boolean")))
        ))
        put(buildToolDef("browser_batch", "Executes a sequence of browser tool actions in a single batch call", JSONObject()
            .put("type", "object")
            .put("required", JSONArray().put("steps"))
            .put("properties", JSONObject().put("steps", JSONObject().put("type", "array")))
        ))
    }

    private fun buildToolDef(name: String, description: String, schema: JSONObject): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", schema)
            put("inputSchema", schema)
        }
    }

    private fun ensurePageAgentInjected(context: Context, webView: WebView?) {
        if (webView == null) return
        if ( com.jarvisai.assistant.browser.BrowserIdentity.isGoogleSignInUrl(webView.url)) return
        val script = runCatching {
            context.assets.open("browser-mcp/page-agent.js").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return
        webView.evaluateJavascript(script, null)
    }

    private suspend fun evalJsString(webView: WebView?, js: String): String {
        if (webView == null) return ""
        val deferred = CompletableDeferred<String>()
        webView.evaluateJavascript(js) { raw ->
            val unquoted = if (raw != null && raw.startsWith("\"") && raw.endsWith("\"")) {
                runCatching { JSONTokener(raw).nextValue().toString() }.getOrElse {
                    raw.removePrefix("\"").removeSuffix("\"").replace("\\\"", "\"").replace("\\n", "\n")
                }
            } else {
                raw ?: ""
            }
            deferred.complete(unquoted)
        }
        return withTimeoutOrNull(8000L) { deferred.await() } ?: ""
    }

    suspend fun executeTool(context: Context, name: String, args: JSONObject): JSONObject = withContext(Dispatchers.Main) {
        val manager = BrowserSessionManager.instance
        manager.updateAgentStatus(active = true, summary = "Antigravity running $name...")

        try {
            val activeTab = manager.getActiveTab()
            if (activeTab != null && name != "browser_lock") {
                com.jarvisai.assistant.browser.BrowserAutomationController.markBusy(activeTab.id)
            }

            when (name) {
                "browser_open_url" -> {
                    val url = args.optString("url", "https://www.google.com")
                    manager.updateAgentStatus(active = true, summary = "Opening $url...")
                    val tab = manager.createTab(url)
                    manager.awaitTabReady(tab.id, timeoutMs = 6000L)
                    ensurePageAgentInjected(context, tab.webView)
                    JSONObject()
                        .put("status", "success")
                        .put("tabId", tab.id)
                        .put("url", tab.url.ifBlank { url })
                        .put("title", tab.title)
                }

                "browser_read_page" -> {
                    val tab = activeTab ?: manager.createTab("https://www.google.com").also {
                        manager.awaitTabReady(it.id, timeoutMs = 5000L)
                    }
                    ensurePageAgentInjected(context, tab.webView)
                    val raw = evalJsString(
                        tab.webView,
                        "window.__antigravityPageAgent ? JSON.stringify(window.__antigravityPageAgent.scanElements()) : '[]'"
                    )
                    val elements = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
                    JSONObject()
                        .put("url", tab.url)
                        .put("title", tab.title)
                        .put("elements", elements)
                }

                "browser_find" -> {
                    val description = args.optString("description", "")
                    val tab = activeTab ?: return@withContext JSONObject().put("error", "No active browser tab")
                    ensurePageAgentInjected(context, tab.webView)
                    val escaped = description.replace("\\", "\\\\").replace("'", "\\'")
                    val raw = evalJsString(
                        tab.webView,
                        "window.__antigravityPageAgent ? JSON.stringify(window.__antigravityPageAgent.findElements('$escaped')) : '[]'"
                    )
                    val matches = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
                    JSONObject()
                        .put("description", description)
                        .put("matches", matches)
                }

                "browser_form_input" -> {
                    val ref = args.optString("ref", "")
                    val value = args.optString("value", "")
                    val tab = activeTab ?: return@withContext JSONObject().put("error", "No active browser tab")
                    ensurePageAgentInjected(context, tab.webView)
                    val escapedRef = ref.replace("\\", "\\\\").replace("'", "\\'")
                    val escapedVal = value.replace("\\", "\\\\").replace("'", "\\'")

                    // Move visual cursor to target element if bounds available
                    val centerRaw = evalJsString(
                        tab.webView,
                        "window.__antigravityPageAgent ? JSON.stringify(window.__antigravityPageAgent.getElementCenter('$escapedRef')) : 'null'"
                    )
                    runCatching {
                        if (centerRaw.startsWith("{")) {
                            val pt = JSONObject(centerRaw)
                            manager.updateAgentStatus(
                                active = true,
                                summary = "Filling $ref...",
                                cursor = pt.optDouble("x", 0.0).toFloat() to pt.optDouble("y", 0.0).toFloat()
                            )
                        }
                    }

                    val filled = evalJsString(
                        tab.webView,
                        "window.__antigravityPageAgent ? String(window.__antigravityPageAgent.fillInput('$escapedRef', '$escapedVal')) : 'false'"
                    )
                    JSONObject()
                        .put("status", if (filled == "true") "success" else "not_found")
                        .put("ref", ref)
                        .put("value", value)
                }

                "browser_computer" -> {
                    val action = args.optString("action", "")
                    val tab = activeTab
                    when (action) {
                        "screenshot" -> {
                            // Try CDP screenshot first, fallback to WebView canvas capture
                            val cdpBase64 = runCatching {
                                getOrConnectCdp()?.captureScreenshotBase64(quality = 80).orEmpty()
                            }.getOrDefault("")
                            if (cdpBase64.isNotBlank()) {
                                JSONObject().put("image_base64", cdpBase64)
                            } else {
                                val bitmap = manager.captureScreenshot(tab?.id ?: "").getOrNull()
                                if (bitmap != null) {
                                    val baos = ByteArrayOutputStream()
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                                    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                                    JSONObject().put("image_base64", b64)
                                } else {
                                    JSONObject().put("error", "Failed to capture screenshot")
                                }
                            }
                        }

                        "left_click" -> {
                            val ref = args.optString("ref", "")
                            var clickX = args.optDouble("x", Double.NaN)
                            var clickY = args.optDouble("y", Double.NaN)

                            if (ref.isNotBlank() && tab?.webView != null) {
                                ensurePageAgentInjected(context, tab.webView)
                                val escapedRef = ref.replace("\\", "\\\\").replace("'", "\\'")
                                val centerRaw = evalJsString(
                                    tab.webView,
                                    "window.__antigravityPageAgent ? JSON.stringify(window.__antigravityPageAgent.getElementCenter('$escapedRef')) : 'null'"
                                )
                                if (centerRaw.startsWith("{")) {
                                    val pt = JSONObject(centerRaw)
                                    clickX = pt.optDouble("x", clickX)
                                    clickY = pt.optDouble("y", clickY)
                                }
                                tab.webView?.evaluateJavascript(
                                    "window.__antigravityPageAgent.getElement('$escapedRef')?.click()",
                                    null
                                )
                            }

                            if (!clickX.isNaN() && !clickY.isNaN()) {
                                manager.updateAgentStatus(
                                    active = true,
                                    summary = "Clicking (${clickX.toInt()}, ${clickY.toInt()})...",
                                    cursor = clickX.toFloat() to clickY.toFloat()
                                )
                                runCatching { getOrConnectCdp()?.dispatchMouseClick(clickX, clickY) }
                                if (ref.isBlank() && tab?.webView != null) {
                                    tab.webView?.evaluateJavascript(
                                        "document.elementFromPoint($clickX, $clickY)?.click()",
                                        null
                                    )
                                }
                            }
                            JSONObject().put("status", "clicked").put("ref", ref)
                        }

                        "type" -> {
                            val text = args.optString("text", "")
                            val ref = args.optString("ref", "")
                            if (ref.isNotBlank() && tab?.webView != null) {
                                ensurePageAgentInjected(context, tab.webView)
                                val escapedRef = ref.replace("\\", "\\\\").replace("'", "\\'")
                                val escapedText = text.replace("\\", "\\\\").replace("'", "\\'")
                                evalJsString(tab.webView, "window.__antigravityPageAgent.fillInput('$escapedRef', '$escapedText')")
                            } else {
                                val cdpSent = runCatching {
                                    val cdp = getOrConnectCdp()
                                    if (cdp != null) {
                                        cdp.dispatchInsertText(text)
                                        true
                                    } else false
                                }.getOrDefault(false)
                                if (!cdpSent && tab?.webView != null) {
                                    val escapedText = text.replace("\\", "\\\\").replace("'", "\\'")
                                    tab.webView?.evaluateJavascript(
                                        "(function(){ var el = document.activeElement; if(el){ el.value = (el.value || '') + '$escapedText'; el.dispatchEvent(new Event('input', {bubbles:true})); } })()",
                                        null
                                    )
                                }
                            }
                            JSONObject().put("status", "typed").put("text", text)
                        }

                        "key" -> {
                            val key = args.optString("key", args.optString("text", "Enter"))
                            runCatching { getOrConnectCdp()?.dispatchKey(key) }
                            if (key.equals("Enter", ignoreCase = true) && tab?.webView != null) {
                                tab.webView?.evaluateJavascript(
                                    "(function(){ var el = document.activeElement; if(el && el.form){ el.form.submit(); } else if(el){ el.dispatchEvent(new KeyboardEvent('keydown', {key:'Enter', keyCode:13, bubbles:true})); } })()",
                                    null
                                )
                            }
                            JSONObject().put("status", "key_pressed").put("key", key)
                        }

                        "hover" -> {
                            val x = args.optDouble("x", 0.0)
                            val y = args.optDouble("y", 0.0)
                            manager.updateAgentStatus(
                                active = true,
                                summary = "Hovering (${x.toInt()}, ${y.toInt()})...",
                                cursor = x.toFloat() to y.toFloat()
                            )
                            runCatching { getOrConnectCdp()?.dispatchMouseMove(x, y) }
                            JSONObject().put("status", "hovered").put("x", x).put("y", y)
                        }

                        "scroll_to" -> {
                            val ref = args.optString("ref", "")
                            val x = args.optDouble("x", 0.0)
                            val y = args.optDouble("y", 500.0)
                            if (ref.isNotBlank() && tab?.webView != null) {
                                ensurePageAgentInjected(context, tab.webView)
                                val escapedRef = ref.replace("\\", "\\\\").replace("'", "\\'")
                                tab.webView?.evaluateJavascript(
                                    "window.__antigravityPageAgent.getElement('$escapedRef')?.scrollIntoView({behavior:'smooth', block:'center'})",
                                    null
                                )
                            } else {
                                tab?.webView?.evaluateJavascript("window.scrollTo({left:$x, top:$y, behavior:'smooth'})", null)
                            }
                            JSONObject().put("status", "scrolled")
                        }

                        else -> JSONObject().put("status", "executed").put("action", action)
                    }
                }

                "browser_get_page_text" -> {
                    val tab = activeTab ?: return@withContext JSONObject().put("text", "")
                    ensurePageAgentInjected(context, tab.webView)
                    val text = evalJsString(
                        tab.webView,
                        "window.__antigravityPageAgent ? window.__antigravityPageAgent.getPageText() : (document.body ? document.body.innerText : '')"
                    )
                    JSONObject()
                        .put("url", tab.url)
                        .put("title", tab.title)
                        .put("text", text)
                }

                "browser_lock" -> {
                    val locked = args.optBoolean("locked", false)
                    val tabId = activeTab?.id.orEmpty()
                    if (tabId.isNotBlank()) {
                        if (locked) {
                            com.jarvisai.assistant.browser.BrowserAutomationController.lockTab(tabId)
                        } else {
                            com.jarvisai.assistant.browser.BrowserAutomationController.unlockTab(tabId)
                        }
                    }
                    manager.setTouchLocked(locked)
                    JSONObject().put("status", "success").put("locked", locked)
                }

                "browser_batch" -> {
                    val steps = args.optJSONArray("steps") ?: JSONArray()
                    val results = JSONArray()
                    for (i in 0 until steps.length()) {
                        val step = steps.optJSONObject(i) ?: continue
                        val stepTool = step.optString("name", step.optString("tool", ""))
                        val stepArgs = step.optJSONObject("arguments") ?: step.optJSONObject("args") ?: step
                        if (stepTool.isNotBlank() && stepTool != "browser_batch") {
                            results.put(executeTool(context, stepTool, stepArgs))
                        }
                    }
                    JSONObject().put("status", "success").put("results", results)
                }

                else -> JSONObject().put("error", "Unknown tool $name")
            }
        } finally {
            CoroutineScope(Dispatchers.Main).launch {
                delay(1800)
                manager.updateAgentStatus(active = false)
            }
        }
    }
}
