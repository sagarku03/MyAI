package com.jarvisai.assistant.browser.mcp

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import com.jarvisai.assistant.browser.BrowserSessionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream

object BrowserMcp {
    private const val TAG = "BrowserMcp"
    private var activeCdpSession: CdpSession? = null
    private var activeCdpTargetUrl: String = ""

    private suspend fun getOrConnectCdp(preferredUrl: String = ""): CdpSession? {
        val current = activeCdpSession
        if (current != null && !current.closed && (preferredUrl.isBlank() || activeCdpTargetUrl == preferredUrl)) {
            return current
        }
        runCatching { current?.close() }
        return runCatching {
            CdpSession.connect(preferredUrl = preferredUrl).also {
                activeCdpSession = it
                activeCdpTargetUrl = preferredUrl
            }
        }.onFailure {
            Log.d(TAG, "CDP socket fallback to WebView JS bridge: ${it.message}")
        }.getOrNull()
    }

    fun getToolDefinitions(): JSONArray = JSONArray().apply {
        put(buildToolDef(
            "browser_open_url",
            "Opens a URL (including http://localhost:PORT previews) in Agentra's visible built-in browser.",
            JSONObject()
                .put("type", "object")
                .put("required", JSONArray().put("url"))
                .put("properties", JSONObject()
                    .put("url", JSONObject().put("type", "string").put("description", "URL to open in the in-app browser"))
                    .put("new_tab", JSONObject().put("type", "boolean").put("description", "Set true to force opening a separate new tab"))
                    .put("tabId", JSONObject().put("type", "string").put("description", "Optional existing tabId to navigate"))
                )
        ))
        put(buildToolDef(
            "browser_read_page",
            "Inspects the visible in-app browser page structure and returns interactive element references (ref_12).",
            JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("tabId", JSONObject().put("type", "string"))
                )
        ))
        put(buildToolDef(
            "browser_find",
            "Finds an interactive button or field by visual/textual description in the visible browser tab.",
            JSONObject()
                .put("type", "object")
                .put("required", JSONArray().put("description"))
                .put("properties", JSONObject()
                    .put("description", JSONObject().put("type", "string"))
                    .put("tabId", JSONObject().put("type", "string"))
                )
        ))
        put(buildToolDef(
            "browser_computer",
            "Executes clicks, typing, keypresses, hovers, scrolls, or captures a live screenshot in Agentra's in-app browser.",
            JSONObject()
                .put("type", "object")
                .put("required", JSONArray().put("action"))
                .put("properties", JSONObject()
                    .put("action", JSONObject().put("type", "string").put("enum", JSONArray(listOf("left_click", "type", "key", "hover", "scroll_to", "screenshot"))))
                    .put("x", JSONObject().put("type", "number"))
                    .put("y", JSONObject().put("type", "number"))
                    .put("text", JSONObject().put("type", "string"))
                    .put("key", JSONObject().put("type", "string"))
                    .put("ref", JSONObject().put("type", "string"))
                    .put("tabId", JSONObject().put("type", "string"))
                )
        ))
        put(buildToolDef(
            "browser_form_input",
            "Fills form inputs, selects options, or checks boxes by ref in the visible browser tab.",
            JSONObject()
                .put("type", "object")
                .put("required", JSONArray(listOf("ref", "value")))
                .put("properties", JSONObject()
                    .put("ref", JSONObject().put("type", "string"))
                    .put("value", JSONObject().put("type", "string"))
                    .put("tabId", JSONObject().put("type", "string"))
                )
        ))
        put(buildToolDef(
            "browser_get_page_text",
            "Extracts clean readable article/page text from the active in-app browser tab.",
            JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("tabId", JSONObject().put("type", "string"))
                )
        ))
        put(buildToolDef(
            "browser_lock",
            "Locks/unlocks user touches on the visible browser tab during multi-step automated execution.",
            JSONObject()
                .put("type", "object")
                .put("properties", JSONObject().put("locked", JSONObject().put("type", "boolean")))
        ))
        put(buildToolDef(
            "browser_batch",
            "Executes a sequence of browser tool actions in a single batch call.",
            JSONObject()
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

    private suspend fun ensurePageAgentInjected(context: Context, webView: WebView?) {
        if (webView == null) return
        if (com.jarvisai.assistant.browser.BrowserIdentity.isGoogleSignInUrl(webView.url)) return
        val script = runCatching {
            context.assets.open("browser-mcp/page-agent.js").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return
        val injected = CompletableDeferred<Boolean>()
        webView.evaluateJavascript(script) {
            injected.complete(true)
        }
        withTimeoutOrNull(2500L) { injected.await() }
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

    private fun cssToViewPixels(webView: WebView?, cssX: Double, cssY: Double): Pair<Float, Float> {
        val density = webView?.resources?.displayMetrics?.density ?: 1f
        return (cssX.toFloat() * density) to (cssY.toFloat() * density)
    }

    suspend fun executeTool(context: Context, name: String, args: JSONObject): JSONObject = withContext(Dispatchers.Main) {
        val manager = BrowserSessionManager.instance
        manager.init(context.applicationContext)
        val requestedTabId = args.optString("tabId", "").trim()
        val resolvedActiveTab = (if (requestedTabId.isNotBlank()) manager.getTab(requestedTabId) else null)
            ?: manager.getActiveNonHomeTab()

        manager.updateAgentStatus(active = true, summary = "Agentra running $name...")

        try {
            if (resolvedActiveTab != null && name != "browser_lock") {
                com.jarvisai.assistant.browser.BrowserAutomationController.markBusy(resolvedActiveTab.id)
            }

            when (name) {
                "browser_open_url" -> {
                    val url = args.optString("url", "https://www.google.com")
                    val newTab = args.optBoolean("new_tab", false)
                    manager.updateAgentStatus(active = true, summary = "Agentra opening $url...")
                    val tab = manager.openAgentUrl(
                        rawUrl = url,
                        newTab = newTab,
                        tabId = requestedTabId.ifBlank { null }
                    )
                    manager.awaitTabReady(tab.id, timeoutMs = 8000L)
                    ensurePageAgentInjected(context, tab.webView)
                    JSONObject()
                        .put("status", "success")
                        .put("tabId", tab.id)
                        .put("url", tab.url.ifBlank { url })
                        .put("title", tab.title)
                        .put("visibleInAppBrowser", true)
                }

                "browser_read_page" -> {
                    val tab = resolvedActiveTab?.takeIf { !it.isHomePage && it.webView != null }
                        ?: manager.openAgentUrl("https://www.google.com").also {
                            manager.awaitTabReady(it.id, timeoutMs = 6000L)
                        }
                    ensurePageAgentInjected(context, tab.webView)
                    val raw = evalJsString(
                        tab.webView,
                        "window.__antigravityPageAgent ? JSON.stringify(window.__antigravityPageAgent.scanElements()) : '[]'"
                    )
                    val elements = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
                    JSONObject()
                        .put("tabId", tab.id)
                        .put("url", tab.url)
                        .put("title", tab.title)
                        .put("elements", elements)
                }

                "browser_find" -> {
                    val description = args.optString("description", "")
                    val tab = resolvedActiveTab?.takeIf { !it.isHomePage && it.webView != null }
                        ?: return@withContext JSONObject().put("error", "No active browser tab")
                    ensurePageAgentInjected(context, tab.webView)
                    val escaped = description.replace("\\", "\\\\").replace("'", "\\'")
                    val raw = evalJsString(
                        tab.webView,
                        "window.__antigravityPageAgent ? JSON.stringify(window.__antigravityPageAgent.findElements('$escaped')) : '[]'"
                    )
                    val matches = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
                    JSONObject()
                        .put("tabId", tab.id)
                        .put("description", description)
                        .put("matches", matches)
                }

                "browser_form_input" -> {
                    val ref = args.optString("ref", "")
                    val value = args.optString("value", "")
                    val tab = resolvedActiveTab?.takeIf { !it.isHomePage && it.webView != null }
                        ?: return@withContext JSONObject().put("error", "No active browser tab")
                    ensurePageAgentInjected(context, tab.webView)
                    val escapedRef = ref.replace("\\", "\\\\").replace("'", "\\'")
                    val escapedVal = value.replace("\\", "\\\\").replace("'", "\\'")

                    val centerRaw = evalJsString(
                        tab.webView,
                        "window.__antigravityPageAgent ? JSON.stringify(window.__antigravityPageAgent.getElementCenter('$escapedRef')) : 'null'"
                    )
                    runCatching {
                        if (centerRaw.startsWith("{")) {
                            val pt = JSONObject(centerRaw)
                            val px = cssToViewPixels(tab.webView, pt.optDouble("x", 0.0), pt.optDouble("y", 0.0))
                            manager.updateAgentStatus(
                                active = true,
                                summary = "Agentra filling $ref...",
                                cursor = px
                            )
                        }
                    }

                    val filled = evalJsString(
                        tab.webView,
                        "window.__antigravityPageAgent ? String(window.__antigravityPageAgent.fillInput('$escapedRef', '$escapedVal')) : 'false'"
                    )
                    JSONObject()
                        .put("status", if (filled == "true") "success" else "not_found")
                        .put("tabId", tab.id)
                        .put("ref", ref)
                        .put("value", value)
                }

                "browser_computer" -> {
                    val action = args.optString("action", "")
                    val tab = resolvedActiveTab?.takeIf { !it.isHomePage && it.webView != null }
                    when (action) {
                        "screenshot" -> {
                            manager.updateAgentStatus(active = true, summary = "Agentra capturing screenshot...")
                            // Prefer direct live WebView capture so it matches the exact active tab shown in BrowserScreen
                            val bitmap = manager.captureScreenshot(tab?.id ?: "").getOrNull()
                            if (bitmap != null) {
                                val baos = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 78, baos)
                                val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                                JSONObject()
                                    .put("tabId", tab?.id.orEmpty())
                                    .put("url", tab?.url.orEmpty())
                                    .put("title", tab?.title.orEmpty())
                                    .put("image_base64", b64)
                            } else {
                                val cdpBase64 = runCatching {
                                    getOrConnectCdp(tab?.url.orEmpty())?.captureScreenshotBase64(quality = 78).orEmpty()
                                }.getOrDefault("")
                                if (cdpBase64.isNotBlank()) {
                                    JSONObject()
                                        .put("tabId", tab?.id.orEmpty())
                                        .put("url", tab?.url.orEmpty())
                                        .put("image_base64", cdpBase64)
                                } else {
                                    JSONObject().put("error", "Failed to capture screenshot: no active webpage loaded")
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
                                val px = cssToViewPixels(tab?.webView, clickX, clickY)
                                manager.updateAgentStatus(
                                    active = true,
                                    summary = "Agentra clicking (${clickX.toInt()}, ${clickY.toInt()})...",
                                    cursor = px
                                )
                                runCatching { getOrConnectCdp(tab?.url.orEmpty())?.dispatchMouseClick(clickX, clickY) }
                                if (ref.isBlank() && tab?.webView != null) {
                                    tab.webView?.evaluateJavascript(
                                        "document.elementFromPoint($clickX, $clickY)?.click()",
                                        null
                                    )
                                }
                            }
                            JSONObject().put("status", "clicked").put("tabId", tab?.id.orEmpty()).put("ref", ref)
                        }

                        "type" -> {
                            val text = args.optString("text", "")
                            val ref = args.optString("ref", "")
                            manager.updateAgentStatus(active = true, summary = "Agentra typing text...")
                            if (ref.isNotBlank() && tab?.webView != null) {
                                ensurePageAgentInjected(context, tab.webView)
                                val escapedRef = ref.replace("\\", "\\\\").replace("'", "\\'")
                                val escapedText = text.replace("\\", "\\\\").replace("'", "\\'")
                                evalJsString(tab.webView, "window.__antigravityPageAgent.fillInput('$escapedRef', '$escapedText')")
                            } else {
                                val cdpSent = runCatching {
                                    val cdp = getOrConnectCdp(tab?.url.orEmpty())
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
                            manager.updateAgentStatus(active = true, summary = "Agentra pressing $key...")
                            runCatching { getOrConnectCdp(tab?.url.orEmpty())?.dispatchKey(key) }
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
                            val px = cssToViewPixels(tab?.webView, x, y)
                            manager.updateAgentStatus(
                                active = true,
                                summary = "Agentra hovering (${x.toInt()}, ${y.toInt()})...",
                                cursor = px
                            )
                            runCatching { getOrConnectCdp(tab?.url.orEmpty())?.dispatchMouseMove(x, y) }
                            JSONObject().put("status", "hovered").put("x", x).put("y", y)
                        }

                        "scroll_to" -> {
                            val ref = args.optString("ref", "")
                            val x = args.optDouble("x", 0.0)
                            val y = args.optDouble("y", 500.0)
                            manager.updateAgentStatus(active = true, summary = "Agentra scrolling page...")
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
                    val tab = resolvedActiveTab?.takeIf { !it.isHomePage && it.webView != null }
                        ?: return@withContext JSONObject().put("text", "")
                    ensurePageAgentInjected(context, tab.webView)
                    val text = evalJsString(
                        tab.webView,
                        "window.__antigravityPageAgent ? window.__antigravityPageAgent.getPageText() : (document.body ? document.body.innerText : '')"
                    )
                    JSONObject()
                        .put("tabId", tab.id)
                        .put("url", tab.url)
                        .put("title", tab.title)
                        .put("text", text)
                }

                "browser_lock" -> {
                    val locked = args.optBoolean("locked", false)
                    val tabId = resolvedActiveTab?.id.orEmpty()
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
            manager.scheduleAgentIdleClear(2200L)
        }
    }
}
