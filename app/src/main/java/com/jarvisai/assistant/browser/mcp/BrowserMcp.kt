package com.jarvisai.assistant.browser.mcp

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.webkit.MimeTypeMap
import android.webkit.WebView
import com.jarvisai.assistant.browser.BrowserAutomationController
import com.jarvisai.assistant.browser.BrowserIdentity
import com.jarvisai.assistant.browser.BrowserJsDialogType
import com.jarvisai.assistant.browser.BrowserSessionManager
import com.jarvisai.assistant.browser.BrowserTab
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Full 17-tool Browser MCP implementation matching AntiGem's browser automation architecture:
 * - Rich indented accessibility tree (`browser_read_page`) with self-healing refs (`ref_N`)
 * - Natural-language element finder (`browser_find`)
 * - Full 13-action `browser_computer` (`screenshot`, `left_click`, `right_click`, `double_click`,
 *   `triple_click`, `hover`, `left_click_drag`, `type`, `key`, `scroll`, `scroll_to`, `zoom`, `wait`)
 * - React/framework-safe `browser_form_input`
 * - Multi-step `browser_batch` returning ordered text + images in one round-trip
 * - Live `browser_read_network`, `browser_get_console_logs`, `browser_eval_js`,
 *   `browser_upload_file`, `browser_lock`, `browser_handle_dialog`
 * - Native visual arrow cursor + floating tooltip pill + pulsing indigo glow via [BrowserAgentIndicatorState]
 */
object BrowserMcp {
    private const val TAG = "BrowserMcp"

    const val INSTRUCTIONS =
        "Controls the app's built-in browser (Android WebView tabs) like a person would. " +
        "ALWAYS browse interactively inside the browser rather than using bash/python/curl to scrape URLs and jump straight to a final link. " +
        "Look, then act: for any multi-step task (such as searching and playing a video on YouTube or filling forms), " +
        "first call browser_lock (locked=true) so the user's touches, keyboard, and zoom stay out of your way on every tab you use or open; " +
        "open the site's homepage or entry page with browser_open_url (e.g. https://m.youtube.com), " +
        "find elements with browser_find or inspect the page with browser_read_page to get refs (like ref_12), " +
        "and act on refs like a real person using browser_computer (left_click, type, key, scroll, hover, wait) or browser_form_input. " +
        "Group predictable steps into one browser_batch call (e.g. click search button, type query, press Enter, wait, then read results). " +
        "Read the results in the browser, click the matching video or link directly on the page, wait and verify it loaded/started playing, " +
        "and call browser_lock with locked=false (without tab_id) when finished to release control back to the user. " +
        "Take a screenshot (browser_computer action=screenshot) whenever you need to see the page; coordinates are pixels in the latest screenshot. " +
        "Read articles with browser_get_page_text. Refs stay valid until the page changes. Never click file inputs: use browser_upload_file. " +
        "If a page opens an alert/confirm/prompt, answer it with browser_handle_dialog. " +
        "Pass action_summary (2-5 clear plain words like \"Searching MC Stan on YouTube\" or \"Opening video\") on every action so the user can follow along. " +
        "Omit tab_id to use the active tab; browser_list_tabs lists all tabs."

    private var activeCdpSession: CdpSession? = null
    private var activeCdpTargetUrl: String = ""
    private var cachedPageAgentScript: String? = null

    private data class LastScreenshotFrame(
        val bitmap: Bitmap,
        val imageWidth: Int,
        val imageHeight: Int,
        val viewportWidthCss: Double,
        val viewportHeightCss: Double,
        val savedPath: String
    )

    private const val PREFS_NAME = "agentra_browser_mcp_prefs"
    private const val KEY_AUTOMATION_HALTED = "automation_halted_by_user"
    private const val KEY_LAST_TURN_ACTIVE = "last_ai_turn_active"

    private val lastScreenshots = ConcurrentHashMap<String, LastScreenshotFrame>()
    private val userUnlockedTabs = ConcurrentHashMap.newKeySet<String>()
    @Volatile
    private var agentLockAllTabs = false
    @Volatile
    private var automationHaltedCache: Boolean? = null

    private fun getPrefs(context: Context?): android.content.SharedPreferences? {
        val appCtx = context?.applicationContext ?: BrowserSessionManager.instance.appContext ?: return null
        return runCatching { appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }.getOrNull()
    }

    fun isAutomationHalted(context: Context? = BrowserSessionManager.instance.appContext): Boolean {
        automationHaltedCache?.let { return it }
        val stored = getPrefs(context)?.getBoolean(KEY_AUTOMATION_HALTED, false) ?: false
        automationHaltedCache = stored
        return stored
    }

    /**
     * Checks on cold process launch whether automation was halted by the user OR an AI turn was
     * interrupted by closing/killing the app, and if so latches halt mode and unlocks everything.
     * Returns true when lingering background cascades on the local hub should also be cancelled.
     */
    fun checkAndHaltOrphanedAutomationOnColdStart(context: Context): Boolean {
        val prefs = getPrefs(context)
        val wasHalted = prefs?.getBoolean(KEY_AUTOMATION_HALTED, false) == true
        val hadUnfinishedTurn = prefs?.getBoolean(KEY_LAST_TURN_ACTIVE, false) == true
        if (wasHalted || hadUnfinishedTurn) {
            haltAutomationByUser(context, reason = "App restarted after interrupted session")
            return true
        }
        return false
    }

    fun markTurnActiveState(context: Context? = BrowserSessionManager.instance.appContext, active: Boolean) {
        getPrefs(context)?.edit()?.putBoolean(KEY_LAST_TURN_ACTIVE, active)?.apply()
    }

    /**
     * Immediately halts all AI browser control, persists the halted state across app restarts,
     * unlocks every tab, clears overlays/banners/locks, and rejects any subsequent MCP calls
     * until [resumeAutomationForNewTurn] is called by a fresh user prompt.
     */
    fun haltAutomationByUser(
        context: Context? = BrowserSessionManager.instance.appContext,
        reason: String = "Stopped by user"
    ) {
        automationHaltedCache = true
        getPrefs(context)?.edit()
            ?.putBoolean(KEY_AUTOMATION_HALTED, true)
            ?.putBoolean(KEY_LAST_TURN_ACTIVE, false)
            ?.apply()
        agentLockAllTabs = false
        userUnlockedTabs.clear()
        runCatching { activeCdpSession?.close() }
        activeCdpSession = null
        activeCdpTargetUrl = ""
        BrowserAgentIndicatorState.clearAll()
        BrowserAutomationController.unlockAllTabs()
        BrowserSessionManager.instance.clearAllAgentControl()
        Log.i(TAG, "Browser automation halted ($reason)")
    }

    /**
     * Halts browser automation immediately and asynchronously cancels all running cascades
     * on the local `agy --hub` daemon so background subagents stop immediately.
     */
    fun haltAutomationAndCancelHub(
        context: Context? = BrowserSessionManager.instance.appContext,
        reason: String = "Stopped by user"
    ) {
        haltAutomationByUser(context, reason)
        val appCtx = context?.applicationContext ?: BrowserSessionManager.instance.appContext
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val hubUrl = if (appCtx != null) {
                com.jarvisai.assistant.data.remote.JarvisHubConfig.getLanguageServiceUrl(appCtx)
            } else {
                com.jarvisai.assistant.terminal.LocalEnvironmentManager.getHubBaseUrl()
            }
            runCatching {
                com.jarvisai.assistant.data.remote.AntigravityApiService.cancelAllRunningCascadesOnHub(hubUrl)
            }
        }
    }

    /**
     * Re-enables browser automation when the user explicitly sends a new prompt or starts a new turn.
     */
    fun resumeAutomationForNewTurn(context: Context? = BrowserSessionManager.instance.appContext) {
        automationHaltedCache = false
        userUnlockedTabs.clear()
        getPrefs(context)?.edit()
            ?.putBoolean(KEY_AUTOMATION_HALTED, false)
            ?.putBoolean(KEY_LAST_TURN_ACTIVE, true)
            ?.apply()
    }

    fun onUserUnlocked(tabId: String, context: Context? = BrowserSessionManager.instance.appContext) {
        if (tabId.isNotBlank()) {
            userUnlockedTabs.add(tabId)
        }
        haltAutomationByUser(context, reason = "User tapped Unlock in browser")
    }

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

    private fun prop(type: String, description: String, enumVals: List<String>? = null): JSONObject =
        JSONObject().apply {
            put("type", type)
            put("description", description)
            if (enumVals != null) put("enum", JSONArray(enumVals))
        }

    private fun numberPair(description: String, size: Int = 2): JSONObject =
        JSONObject().apply {
            put("type", "array")
            put("items", JSONObject().put("type", "number"))
            put("minItems", size)
            put("maxItems", size)
            put("description", description)
        }

    private fun toolDef(
        name: String,
        description: String,
        required: List<String>,
        vararg props: Pair<String, JSONObject>
    ): JSONObject {
        val properties = JSONObject()
        props.forEach { (k, v) -> properties.put(k, v) }
        val schema = JSONObject().apply {
            put("type", "object")
            put("properties", properties)
            if (required.isNotEmpty()) put("required", JSONArray(required))
        }
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", schema)
            put("inputSchema", schema)
        }
    }

    fun getToolDefinitions(): JSONArray {
        val tabId = "tab_id" to prop("string", "Target tab id (default: active tab). See browser_list_tabs.")
        val summaryProp = "action_summary" to prop("string", "Short plain-English caption shown beside the live cursor (e.g. \"Opening search results\")")

        return JSONArray().apply {
            put(toolDef(
                "browser_list_tabs",
                "List the in-app browser's tabs: id, URL, title, which is active, loading state, open dialogs, and view mode.",
                emptyList()
            ))
            put(toolDef(
                "browser_open_url",
                "Open a URL in the in-app browser (including http://localhost:PORT previews) and wait for it to load, or navigate tab history (\"back\", \"forward\", \"reload\"). Opens in the active tab unless new_tab=true or tab_id is given.",
                listOf("url"),
                "url" to prop("string", "URL (https://..., localhost:5173, or search words), or \"back\", \"forward\", \"reload\""),
                "new_tab" to prop("boolean", "Open in a brand-new tab instead of navigating the active tab (default false)"),
                "mode" to prop("string", "Optional view mode for this tab: mobile or desktop", listOf("mobile", "desktop")),
                tabId,
                summaryProp
            ))
            put(toolDef(
                "browser_switch_tab",
                "Show this tab in the browser (make it the active tab).",
                listOf("tab_id"),
                "tab_id" to prop("string", "Tab id to activate")
            ))
            put(toolDef(
                "browser_close_tab",
                "Close a tab in the in-app browser.",
                listOf("tab_id"),
                "tab_id" to prop("string", "Tab id to close")
            ))
            put(toolDef(
                "browser_set_view_mode",
                "Switch a tab between mobile and desktop view mode (updates User-Agent and viewport and reloads).",
                emptyList(),
                tabId,
                "mode" to prop("string", "View mode: mobile or desktop", listOf("mobile", "desktop")),
                "desktop_mode" to prop("boolean", "true for desktop, false for mobile")
            ))
            put(toolDef(
                "browser_read_page",
                "Read the page as an indented tree of its elements: role, name, state and value, each with a ref (e.g. ref_12) that browser_computer, browser_form_input and browser_upload_file accept. Short text blocks appear as one line. filter=\"interactive\" lists only what you can click or type into (much smaller). Controls outside the visible area are marked (off-screen); acting on a ref scrolls to it. Refs stay the same for an element until the page changes. Output is cut at max_chars; use ref_id to read one part of a big page.",
                emptyList(),
                tabId,
                "filter" to prop("string", "all (default) or interactive", listOf("all", "interactive")),
                "depth" to prop("integer", "Maximum tree depth (default 25)"),
                "ref_id" to prop("string", "Read only this element and what is inside it"),
                "max_chars" to prop("integer", "Maximum characters returned (default 50000)"),
                summaryProp
            ))
            put(toolDef(
                "browser_find",
                "Find elements by what they are or what they say, e.g. \"search box\", \"Add to cart button\", \"price of the blue shirt\", \"email field\". Returns up to 20 matches, best first, each with a ref to act on. Cheaper than reading the whole page.",
                listOf("query"),
                tabId,
                "query" to prop("string", "What to look for, in plain words"),
                summaryProp
            ))
            put(toolDef(
                "browser_get_page_text",
                "Read the page's main text as plain text (the article or main content when the page has one): articles, docs, search results, messages. Has no refs; use browser_find or browser_read_page to act on things. Says when part of the text is hidden (collapsed sections, tabs); include_hidden=true reads that too.",
                emptyList(),
                tabId,
                "max_chars" to prop("integer", "Maximum characters returned (default 50000)"),
                "include_hidden" to prop("boolean", "Also read hidden text: collapsed sections, inactive tabs, closed menus (default false)"),
                summaryProp
            ))
            put(toolDef(
                "browser_computer",
                "Use the mouse and keyboard on a tab, and take screenshots. Input is real (trusted) input, like a person's. Target elements by ref (from browser_find / browser_read_page; preferred, it scrolls the element into view) or by coordinate: [x, y] pixels in the latest screenshot of this tab (take one first). Click the centre of elements. Each action reports what it hit and whether the page navigated or opened a tab.\n" +
                    "Actions:\n" +
                    "* screenshot: see the visible part of the page (sets the coordinate frame for this tab and saves a local image path)\n" +
                    "* left_click / double_click / triple_click / right_click: click ref or coordinate (modifiers like \"ctrl\" or \"shift\" optional)\n" +
                    "* hover: move the mouse over ref or coordinate (menus, tooltips)\n" +
                    "* left_click_drag: drag from start_coordinate to coordinate\n" +
                    "* type: type text into the focused text field (click it first; nothing is typed if no text field has focus); \\n presses Enter\n" +
                    "* key: press keys, space-separated: \"Enter\", \"Tab\", \"Escape\", \"Backspace\", \"ArrowDown\", \"ctrl+a\", \"shift+Tab\"; repeat for many presses\n" +
                    "* scroll: scroll_direction up/down/left/right by scroll_amount wheel ticks (default 3) at coordinate (default: page centre); returns a screenshot\n" +
                    "* scroll_to: scroll ref into view\n" +
                    "* zoom: closer look at region [x0, y0, x1, y1] of the latest screenshot (coordinates stay in the full screenshot's frame)\n" +
                    "* wait: wait duration seconds (max 10) for the page to change",
                listOf("action"),
                tabId,
                "action" to prop("string", "What to do", listOf(
                    "screenshot", "left_click", "right_click", "double_click", "triple_click",
                    "hover", "left_click_drag", "type", "key", "scroll", "scroll_to", "zoom", "wait"
                )),
                "ref" to prop("string", "Element ref, e.g. ref_12 (clicks, hover, scroll_to)"),
                "coordinate" to numberPair("[x, y] in pixels of the latest screenshot (clicks, hover, scroll; drag end point)"),
                "start_coordinate" to numberPair("[x, y] where left_click_drag starts"),
                "text" to prop("string", "Text to type (type), or keys to press (key)"),
                "modifiers" to prop("string", "Keys held during a click: ctrl, shift, alt, meta; combine with + (e.g. \"ctrl+shift\")"),
                "repeat" to prop("integer", "key: press the sequence this many times (1-100, default 1)"),
                "scroll_direction" to prop("string", "scroll direction", listOf("up", "down", "left", "right")),
                "scroll_amount" to prop("integer", "scroll: wheel ticks, 1-10 (default 3)"),
                "region" to numberPair("zoom: [x0, y0, x1, y1] rectangle in pixels of the latest screenshot", 4),
                "duration" to prop("number", "wait: seconds, up to 10"),
                "scale" to prop("number", "screenshot/zoom: 0.1-1 to get a smaller image (fewer tokens); coordinates still use the full-size frame"),
                summaryProp
            ))
            put(toolDef(
                "browser_form_input",
                "Set a form field by ref, the way frameworks like React expect: text, number, date and time fields and textareas (value replaces the content), <select> dropdowns (option text or value; a list for multi-selects), checkboxes and switches (true/false; never clicks twice), radio buttons (true), and contenteditable editors. For custom dropdowns, click them with browser_computer instead.",
                listOf("ref", "value"),
                tabId,
                "ref" to prop("string", "Field ref from browser_find / browser_read_page"),
                "value" to JSONObject()
                    .put("description", "New value: string, number, boolean, or a list of options for a multi-select")
                    .put("type", JSONArray(listOf("string", "number", "boolean", "array"))),
                summaryProp
            ))
            put(toolDef(
                "browser_eval_js",
                "Run JavaScript in the page and get the value of the last expression (top-level await works; values come back as JSON). For reading data or debugging; prefer browser_computer and browser_form_input for acting, which behave like a real user.",
                listOf("script"),
                tabId,
                "script" to prop("string", "JavaScript to run, e.g. document.querySelectorAll('li').length or await fetch('/api').then(r => r.json())"),
                summaryProp
            ))
            put(toolDef(
                "browser_get_console_logs",
                "Read the tab's console messages (console.log/warn/error and uncaught errors), oldest first. Use pattern to filter.",
                emptyList(),
                tabId,
                "pattern" to prop("string", "Regular expression (case-insensitive) the message must match"),
                "only_errors" to prop("boolean", "Only errors"),
                "level" to prop("string", "Only this level", listOf("ALL", "ERROR", "WARN", "LOG", "INFO", "DEBUG")),
                "limit" to prop("integer", "Return the newest N messages, listed oldest first (default 100, max 300)"),
                "clear" to prop("boolean", "Clear the messages after reading, so the next read shows only new ones")
            ))
            put(toolDef(
                "browser_read_network",
                "List the tab's network requests (documents, XHR/fetch, scripts, images...) with method, status, type, size and time. Recording starts the first time the browser MCP uses the tab, so reload the page to see its initial requests.",
                emptyList(),
                tabId,
                "url_pattern" to prop("string", "Only URLs containing this text (e.g. /api/)"),
                "only_failed" to prop("boolean", "Only failed requests and HTTP errors (status >= 400)"),
                "limit" to prop("integer", "Return the newest N requests, listed oldest first (default 100, max 500)"),
                "clear" to prop("boolean", "Clear the list after reading")
            ))
            put(toolDef(
                "browser_upload_file",
                "Upload local files to the page like a person picking them in a file dialog: give the ref of the upload button, its label or the file input (hidden inputs work), or omit it when the page has a single file input. Supports DataTransfer injection for hidden inputs and drag-and-drop areas (up to 25 MiB). Check the page afterwards to confirm the site accepted them.",
                listOf("file_paths"),
                tabId,
                "file_paths" to JSONObject()
                    .put("type", "array")
                    .put("items", JSONObject().put("type", "string"))
                    .put("description", "1-20 local files: absolute, ~/... or relative to workspace"),
                "ref" to prop("string", "Ref of the upload button, its label or the file input"),
                "selector" to prop("string", "Instead of ref: CSS selector of the upload element"),
                "method" to prop("string", "auto (default): file chooser, else in-page fallback; chooser: only file chooser; drop: build files in page", listOf("auto", "chooser", "drop")),
                "timeout_seconds" to prop("integer", "How long to wait for the page to open its file chooser (1-30, default 10)"),
                summaryProp
            ))
            put(toolDef(
                "browser_lock",
                "Take or give back control of the browser for your task. locked=true (default) turns on the agent lock: the tab (tab_id, or the active tab) is locked now, and so is every tab you use or open while it is on. On locked tabs the user's touches are blocked so pages behave consistently; the user sees an Unlock button and can take over a tab at any time. locked=false without tab_id turns the lock off and releases every locked tab: do this when your task is done. Recommended for any task of several steps.",
                emptyList(),
                "tab_id" to prop("string", "Lock: the tab to lock now (default: active tab). Unlock: release only this tab (default: all)"),
                "locked" to prop("boolean", "true to lock (default), false to unlock"),
                summaryProp
            ))
            put(toolDef(
                "browser_handle_dialog",
                "Answer the JavaScript alert, confirm or prompt (or \"Leave this page?\") a page opened. While one is open every page is paused. accept=true presses OK (or Leave), accept=false presses Cancel (or Stay). browser_list_tabs shows open dialogs.",
                listOf("accept"),
                "tab_id" to prop("string", "Tab with the dialog; omit for the one that has it"),
                "accept" to prop("boolean", "true for OK / Leave, false for Cancel / Stay"),
                "prompt_text" to prop("string", "For a prompt: the text to answer with (default: the page's suggested text)")
            ))
            put(toolDef(
                "browser_batch",
                "Run several browser tool calls in one round trip, in order, stopping at the first error. Use it whenever you can predict two or more steps, e.g. click a field, type, press Enter, then screenshot. Each item is {\"name\": tool name, \"input\": that tool's arguments}. Coordinates inside a batch refer to the screenshot taken before the batch. Results and images come back in order. Batches cannot be nested.",
                listOf("actions"),
                "actions" to JSONObject()
                    .put("type", "array")
                    .put("minItems", 1)
                    .put("description", "Tool calls to run in order")
                    .put("items", JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject()
                            .put("name", prop("string", "Tool name, e.g. browser_computer"))
                            .put("input", JSONObject().put("type", "object").put("description", "The tool's arguments")))
                        .put("required", JSONArray(listOf("name", "input"))))
            ))
        }
    }

    private fun readPageAgentScript(context: Context): String? {
        cachedPageAgentScript?.let { return it }
        return runCatching {
            context.assets.open("browser-mcp/page-agent.js").bufferedReader().use { it.readText() }
        }.onSuccess { cachedPageAgentScript = it }.getOrNull()
    }

    private suspend fun ensurePageAgentInjected(context: Context, webView: WebView?) {
        if (webView == null) return
        if (BrowserIdentity.isGoogleSignInUrl(webView.url)) return
        val script = readPageAgentScript(context) ?: return
        val injected = CompletableDeferred<Boolean>()
        webView.evaluateJavascript(script) { injected.complete(true) }
        withTimeoutOrNull(3000L) { injected.await() }
    }

    private suspend fun evalJsRaw(webView: WebView?, js: String, timeoutMs: Long = 10_000L): String {
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
        return withTimeoutOrNull(timeoutMs) { deferred.await() } ?: ""
    }

    private suspend fun callPageAgentJson(context: Context, webView: WebView?, callExpr: String): JSONObject {
        ensurePageAgentInjected(context, webView)
        val raw = evalJsRaw(
            webView,
            "(function(){ try { if(!window.__antigemAgent) return JSON.stringify({ok:false,error:'page-agent not loaded'}); var r = window.__antigemAgent.$callExpr; return JSON.stringify(r == null ? {} : r); } catch(e){ return JSON.stringify({ok:false,error:String(e && e.message || e)}); } })()"
        )
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject().put("ok", false).put("error", raw.ifBlank { "empty JS response" }) }
    }

    private fun extractTabId(args: JSONObject): String {
        return args.optString("tab_id", "").ifBlank { args.optString("tabId", "") }.trim()
    }

    private fun extractSummary(args: JSONObject, fallback: String): String {
        val explicit = args.optString("action_summary", "").ifBlank { args.optString("summary", "") }.trim()
        return (explicit.ifBlank { fallback }).take(52)
    }

    private fun resolveTab(manager: BrowserSessionManager, args: JSONObject): BrowserTab? {
        val requested = extractTabId(args)
        return (if (requested.isNotBlank()) manager.getTab(requested) else null)
            ?: manager.getActiveNonHomeTab()
            ?: manager.getActiveTab()
    }

    private fun resolveCoordinates(
        tabId: String,
        args: JSONObject,
        vpWidth: Double,
        vpHeight: Double
    ): Pair<Double, Double>? {
        val coordArr = args.optJSONArray("coordinate")
        if (coordArr != null && coordArr.length() >= 2) {
            val rawX = coordArr.optDouble(0, Double.NaN)
            val rawY = coordArr.optDouble(1, Double.NaN)
            if (!rawX.isNaN() && !rawY.isNaN()) {
                val frame = lastScreenshots[tabId]
                return if (frame != null && frame.imageWidth > 0 && frame.imageHeight > 0 && vpWidth > 0 && vpHeight > 0) {
                    (rawX * vpWidth / frame.imageWidth.toDouble()) to (rawY * vpHeight / frame.imageHeight.toDouble())
                } else {
                    rawX to rawY
                }
            }
        }
        val legacyX = args.optDouble("x", Double.NaN)
        val legacyY = args.optDouble("y", Double.NaN)
        if (!legacyX.isNaN() && !legacyY.isNaN()) {
            return legacyX to legacyY
        }
        return null
    }

    private fun showPointerFraction(tab: BrowserTab?, cssX: Double, cssY: Double, vpW: Double, vpH: Double, label: String) {
        val id = tab?.id ?: return
        val w = if (vpW > 1.0) vpW else 390.0
        val h = if (vpH > 1.0) vpH else 800.0
        val fx = (cssX / w).coerceIn(0.02, 0.98)
        val fy = (cssY / h).coerceIn(0.02, 0.98)
        BrowserAgentIndicatorState.moveTo(id, fx, fy, label)
        val density = tab.webView?.resources?.displayMetrics?.density ?: 1f
        BrowserSessionManager.instance.updateAgentStatus(
            active = true,
            summary = label,
            cursor = (cssX.toFloat() * density) to (cssY.toFloat() * density)
        )
    }

    private suspend fun dispatchTouchTap(webView: WebView?, fx: Double, fy: Double) {
        if (webView == null || webView.width <= 0 || webView.height <= 0) return
        val px = (fx.coerceIn(0.0, 1.0) * webView.width).toFloat()
        val py = (fy.coerceIn(0.0, 1.0) * webView.height).toFloat()
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, px, py, 0)
        val up = MotionEvent.obtain(downTime, downTime + 45L, MotionEvent.ACTION_UP, px, py, 0)
        runCatching {
            webView.dispatchTouchEvent(down)
            delay(45L)
            webView.dispatchTouchEvent(up)
        }
        down.recycle()
        up.recycle()
    }

    private suspend fun dispatchTouchDrag(webView: WebView?, fx0: Double, fy0: Double, fx1: Double, fy1: Double, steps: Int = 12) {
        if (webView == null || webView.width <= 0 || webView.height <= 0) return
        val x0 = (fx0.coerceIn(0.0, 1.0) * webView.width).toFloat()
        val y0 = (fy0.coerceIn(0.0, 1.0) * webView.height).toFloat()
        val x1 = (fx1.coerceIn(0.0, 1.0) * webView.width).toFloat()
        val y1 = (fy1.coerceIn(0.0, 1.0) * webView.height).toFloat()
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x0, y0, 0)
        runCatching { webView.dispatchTouchEvent(down) }
        down.recycle()
        for (i in 1..steps) {
            delay(18L)
            val t = i.toFloat() / steps.toFloat()
            val move = MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_MOVE,
                x0 + (x1 - x0) * t,
                y0 + (y1 - y0) * t,
                0
            )
            runCatching { webView.dispatchTouchEvent(move) }
            move.recycle()
        }
        val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x1, y1, 0)
        runCatching { webView.dispatchTouchEvent(up) }
        up.recycle()
    }

    private suspend fun captureTabScreenshot(
        context: Context,
        manager: BrowserSessionManager,
        tab: BrowserTab?,
        scale: Double = 1.0,
        cropRegion: JSONArray? = null
    ): JSONObject {
        val targetTab = tab ?: return JSONObject().put("error", "No active webpage loaded to capture screenshot")
        val vp = callPageAgentJson(context, targetTab.webView, "viewport()")
        val vpW = vp.optDouble("w", 390.0).takeIf { it > 0 } ?: 390.0
        val vpH = vp.optDouble("h", 800.0).takeIf { it > 0 } ?: 800.0

        val rawBitmap = if (cropRegion != null) {
            lastScreenshots[targetTab.id]?.bitmap ?: manager.captureScreenshot(targetTab.id).getOrNull()
        } else {
            manager.captureScreenshot(targetTab.id).getOrNull()
        }

        if (rawBitmap == null) {
            val cdpB64 = runCatching {
                getOrConnectCdp(targetTab.url)?.captureScreenshotBase64(quality = 78).orEmpty()
            }.getOrDefault("")
            if (cdpB64.isBlank()) {
                return JSONObject().put("error", "Failed to capture screenshot: WebView has no active frame")
            }
            val savedFile = saveBase64Capture(context, cdpB64)
            return JSONObject()
                .put("tab_id", targetTab.id)
                .put("url", targetTab.url)
                .put("title", targetTab.title)
                .put("saved_path", savedFile?.absolutePath.orEmpty())
                .put("image_base64", cdpB64)
        }

        var workingBitmap = rawBitmap
        if (cropRegion != null && cropRegion.length() >= 4) {
            val x0 = cropRegion.optDouble(0, 0.0).roundToInt().coerceIn(0, rawBitmap.width - 1)
            val y0 = cropRegion.optDouble(1, 0.0).roundToInt().coerceIn(0, rawBitmap.height - 1)
            val x1 = cropRegion.optDouble(2, rawBitmap.width.toDouble()).roundToInt().coerceIn(x0 + 1, rawBitmap.width)
            val y1 = cropRegion.optDouble(3, rawBitmap.height.toDouble()).roundToInt().coerceIn(y0 + 1, rawBitmap.height)
            workingBitmap = Bitmap.createBitmap(rawBitmap, x0, y0, max(1, x1 - x0), max(1, y1 - y0))
        }

        val clampedScale = scale.coerceIn(0.15, 1.0)
        val maxDim = 1280
        val baseRatio = min(1.0, maxDim.toDouble() / max(workingBitmap.width, workingBitmap.height).toDouble())
        val finalRatio = baseRatio * clampedScale
        val outW = max(1, (workingBitmap.width * finalRatio).roundToInt())
        val outH = max(1, (workingBitmap.height * finalRatio).roundToInt())
        val finalBitmap = if (outW != workingBitmap.width || outH != workingBitmap.height) {
            Bitmap.createScaledBitmap(workingBitmap, outW, outH, true)
        } else {
            workingBitmap
        }

        val baos = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 78, baos)
        val jpegBytes = baos.toByteArray()
        val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

        val savedFile = saveBytesCapture(context, jpegBytes)
        if (cropRegion == null) {
            lastScreenshots[targetTab.id] = LastScreenshotFrame(
                bitmap = finalBitmap,
                imageWidth = outW,
                imageHeight = outH,
                viewportWidthCss = vpW,
                viewportHeightCss = vpH,
                savedPath = savedFile?.absolutePath.orEmpty()
            )
        }

        return JSONObject()
            .put("tab_id", targetTab.id)
            .put("url", targetTab.url)
            .put("title", targetTab.title)
            .put("width", outW)
            .put("height", outH)
            .put("saved_path", savedFile?.absolutePath.orEmpty())
            .put("text", "Screenshot captured (${outW}x${outH}) of ${targetTab.title.ifBlank { targetTab.url }}. Local path: ${savedFile?.absolutePath.orEmpty()}")
            .put("image_base64", b64)
    }

    private fun saveBytesCapture(context: Context, bytes: ByteArray): File? {
        return runCatching {
            val dir = File(context.filesDir, "browser_captures").apply { mkdirs() }
            // Keep capture folder tidy (latest 40 shots)
            val existing = dir.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
            if (existing.size > 40) {
                existing.drop(40).forEach { runCatching { it.delete() } }
            }
            val file = File(dir, "media_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { it.write(bytes) }
            file
        }.getOrNull()
    }

    private fun saveBase64Capture(context: Context, b64: String): File? {
        return runCatching {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            saveBytesCapture(context, bytes)
        }.getOrNull()
    }

    suspend fun executeTool(context: Context, name: String, args: JSONObject): JSONObject = withContext(Dispatchers.Main) {
        val manager = BrowserSessionManager.instance
        manager.init(context.applicationContext)

        if (isAutomationHalted(context)) {
            val isUnlockCall = name == "browser_lock" && !args.optBoolean("locked", true)
            if (isUnlockCall) {
                agentLockAllTabs = false
                BrowserAutomationController.unlockAllTabs()
                manager.clearAllAgentControl()
                return@withContext JSONObject()
                    .put("status", "unlocked")
                    .put("locked", false)
                    .put("text", "Browser control is already released.")
            }
            manager.setTouchLocked(false)
            return@withContext JSONObject()
                .put("error", "Browser automation was stopped by the user. Stop immediately and do not control the browser until the user sends a new request.")
                .put("stopped_by_user", true)
                .put("status", "stopped")
        }

        val resolvedActiveTab = resolveTab(manager, args)
        val tabId = resolvedActiveTab?.id.orEmpty()

        val initialLabel = extractSummary(
            args,
            when (name) {
                "browser_open_url" -> {
                    val raw = args.optString("url", "").trim()
                    when (raw.lowercase()) {
                        "back" -> "Going back"
                        "forward" -> "Going forward"
                        "reload" -> "Reloading"
                        else -> {
                            val host = runCatching {
                                android.net.Uri.parse(BrowserSessionManager.normalizeUrl(raw)).host?.removePrefix("www.")
                            }.getOrNull()
                            "Opening ${(host ?: raw.ifBlank { "page" }).take(28)}"
                        }
                    }
                }
                "browser_read_page" -> "Reading the page"
                "browser_find" -> "Looking for \"${args.optString("query", args.optString("description", "")).take(26)}\""
                "browser_get_page_text" -> "Reading page text"
                "browser_form_input" -> "Filling form field"
                "browser_eval_js" -> "Running a script"
                "browser_get_console_logs" -> "Reading console logs"
                "browser_read_network" -> "Checking network requests"
                "browser_upload_file" -> "Uploading file"
                "browser_handle_dialog" -> "Answering page dialog"
                "browser_set_view_mode" -> "Switching view mode"
                "browser_switch_tab" -> "Switching tab"
                "browser_close_tab" -> "Closing tab"
                "browser_lock" -> if (args.optBoolean("locked", true)) "Taking control" else "Done"
                "browser_batch" -> "Working in browser"
                "browser_computer" -> {
                    when (args.optString("action", "").trim().lowercase()) {
                        "screenshot" -> "Taking screenshot"
                        "wait" -> "Waiting for page"
                        "scroll", "scroll_to" -> "Scrolling page"
                        "type" -> "Typing text"
                        "key" -> "Pressing key"
                        "hover" -> "Moving pointer"
                        else -> "Interacting with page"
                    }
                }
                else -> "Working in browser"
            }
        )

        if (name != "browser_list_tabs" && name != "browser_lock" && name != "browser_batch") {
            manager.updateAgentStatus(active = true, summary = initialLabel)
            if (tabId.isNotBlank()) {
                BrowserAgentIndicatorState.activity(tabId, initialLabel)
                BrowserAutomationController.markBusy(tabId)
                if (agentLockAllTabs && resolvedActiveTab?.aiLocked == false && !userUnlockedTabs.contains(tabId)) {
                    BrowserAutomationController.lockTab(tabId)
                    manager.setTouchLocked(true)
                }
            }
        }

        try {
            // If an unanswered JS dialog is open and caller isn't handling it, warn cleanly
            val dialogTab = manager.listTabs().firstOrNull { it.activeJsDialog != null }
            if (dialogTab != null && name != "browser_handle_dialog" && name != "browser_list_tabs" && name != "browser_lock") {
                val dlg = dialogTab.activeJsDialog!!
                return@withContext JSONObject()
                    .put("error", "Tab ${dialogTab.id} has an open JavaScript ${dlg.type.name.lowercase()} dialog: \"${dlg.message}\". Call browser_handle_dialog first.")
                    .put("tab_id", dialogTab.id)
                    .put("dialog_type", dlg.type.name.lowercase())
            }

            when (name) {
                "browser_list_tabs" -> {
                    val activeId = manager.getActiveTab()?.id
                    val array = JSONArray()
                    val lines = StringBuilder()
                    manager.listTabs().forEach { t ->
                        val dlg = t.activeJsDialog
                        array.put(JSONObject().apply {
                            put("tab_id", t.id)
                            put("id", t.id)
                            put("url", t.url)
                            put("title", t.title)
                            put("active", t.id == activeId)
                            put("is_home_page", t.isHomePage)
                            put("view_mode", if (t.desktopMode) "desktop" else "mobile")
                            put("locked", t.aiLocked)
                            if (dlg != null) {
                                put("dialog", JSONObject().put("type", dlg.type.name.lowercase()).put("message", dlg.message))
                            }
                        })
                        val activeMark = if (t.id == activeId) " [active]" else ""
                        val modeMark = if (t.desktopMode) " [desktop]" else " [mobile]"
                        val lockMark = if (t.aiLocked) " [locked]" else ""
                        val dlgMark = if (dlg != null) " [DIALOG: ${dlg.type.name.lowercase()}]" else ""
                        lines.appendLine("- ${t.id}$activeMark$modeMark$lockMark$dlgMark: ${t.title.ifBlank { "Untitled" }} (${t.url})")
                    }
                    JSONObject()
                        .put("active_tab_id", activeId.orEmpty())
                        .put("count", array.length())
                        .put("tabs", array)
                        .put("text", lines.toString().ifBlank { "No open browser tabs." })
                }

                "browser_open_url" -> {
                    val url = args.optString("url", "https://www.google.com").trim()
                    val newTab = args.optBoolean("new_tab", false)
                    val requestedId = extractTabId(args).ifBlank { null }
                    val requestedDesktop: Boolean? = when {
                        args.has("mode") -> args.optString("mode", "").equals("desktop", ignoreCase = true)
                        args.has("desktop_mode") -> args.optBoolean("desktop_mode")
                        else -> null
                    }
                    val tab = when (url.lowercase()) {
                        "back", "forward", "reload" -> {
                            val target = resolvedActiveTab?.takeIf { !it.isHomePage && it.webView != null }
                                ?: return@withContext JSONObject().put("error", "No active webpage tab for '$url'")
                            val moved = when (url.lowercase()) {
                                "back" -> manager.goBackTab(target.id)
                                "forward" -> manager.goForwardTab(target.id)
                                else -> manager.reloadTab(target.id)
                            }
                            if (!moved) {
                                return@withContext JSONObject().put("error", "This tab has no ${if (url.equals("back", true)) "earlier" else "later"} page in its history.")
                            }
                            delay(250L)
                            target
                        }
                        else -> {
                            val existingMode = requestedId?.let { manager.getTab(it)?.desktopMode }
                                ?: resolvedActiveTab?.desktopMode
                                ?: false
                            manager.openAgentUrl(
                                rawUrl = url,
                                newTab = newTab,
                                tabId = requestedId,
                                desktopMode = requestedDesktop ?: existingMode
                            )
                        }
                    }
                    if (agentLockAllTabs && !userUnlockedTabs.contains(tab.id)) {
                        BrowserAutomationController.lockTab(tab.id)
                        manager.setTouchLocked(true)
                    }
                    BrowserAgentIndicatorState.activity(tab.id, initialLabel)
                    manager.awaitTabReady(tab.id, timeoutMs = 10_000L)
                    delay(250L)
                    ensurePageAgentInjected(context, tab.webView)
                    JSONObject()
                        .put("status", "success")
                        .put("tab_id", tab.id)
                        .put("tabId", tab.id)
                        .put("url", tab.url.ifBlank { url })
                        .put("title", tab.title)
                        .put("visibleInAppBrowser", true)
                }

                "browser_switch_tab" -> {
                    val targetId = extractTabId(args)
                    val switched = manager.switchTab(targetId)
                    JSONObject().put("switched", switched).put("tab_id", targetId)
                }

                "browser_close_tab" -> {
                    val targetId = extractTabId(args)
                    lastScreenshots.remove(targetId)
                    BrowserAgentIndicatorState.clear(targetId)
                    manager.closeTab(targetId)
                    JSONObject().put("closed", true).put("tab_id", targetId)
                }

                "browser_set_view_mode" -> {
                    val targetId = extractTabId(args).ifBlank { tabId }
                    val isDesktop = when {
                        args.has("desktop_mode") -> args.optBoolean("desktop_mode", false)
                        args.has("mode") -> args.optString("mode", "mobile").equals("desktop", ignoreCase = true)
                        else -> !(manager.getTab(targetId)?.desktopMode ?: false)
                    }
                    manager.setDesktopMode(targetId, isDesktop)
                    delay(400L)
                    JSONObject()
                        .put("success", true)
                        .put("tab_id", targetId)
                        .put("mode", if (isDesktop) "desktop" else "mobile")
                        .put("desktop_mode", isDesktop)
                }

                "browser_read_page" -> {
                    val tab = resolvedActiveTab?.takeIf { !it.isHomePage && it.webView != null }
                        ?: manager.openAgentUrl("https://www.google.com").also {
                            manager.awaitTabReady(it.id, timeoutMs = 6000L)
                        }
                    BrowserAgentIndicatorState.activity(tab.id, extractSummary(args, "Reading the page"))
                    val filter = args.optString("filter", "all").let { if (it == "interactive") "interactive" else "all" }
                    val depth = args.optInt("depth", 25).coerceIn(1, 50)
                    val refId = args.optString("ref_id", "").trim()
                    val maxChars = args.optInt("max_chars", 50000).coerceIn(500, 200000)
                    val opts = JSONObject()
                        .put("filter", filter)
                        .put("depth", depth)
                        .put("maxChars", maxChars)
                        .apply { if (refId.isNotBlank()) put("refId", refId) }
                    val res = callPageAgentJson(context, tab.webView, "tree($opts)")
                    if (res.optBoolean("ok", false)) {
                        JSONObject()
                            .put("tab_id", tab.id)
                            .put("url", tab.url)
                            .put("title", tab.title)
                            .put("tree", res.optString("tree", res.optString("text", "")))
                    } else {
                        JSONObject()
                            .put("tab_id", tab.id)
                            .put("url", tab.url)
                            .put("error", res.optString("error", "Failed to read page tree"))
                    }
                }

                "browser_find" -> {
                    val query = args.optString("query", "").ifBlank { args.optString("description", "") }.trim()
                    if (query.isBlank()) {
                        return@withContext JSONObject().put("error", "Missing required parameter: query")
                    }
                    val tab = resolvedActiveTab?.takeIf { !it.isHomePage && it.webView != null }
                        ?: return@withContext JSONObject().put("error", "No active browser tab")
                    BrowserAgentIndicatorState.activity(tab.id, extractSummary(args, "Looking for \"$query\""))
                    val res = callPageAgentJson(context, tab.webView, "find(${JSONObject.quote(query)})")
                    if (res.optBoolean("ok", false)) {
                        JSONObject()
                            .put("tab_id", tab.id)
                            .put("query", query)
                            .put("matches", res.optString("text", res.opt("matches")?.toString().orEmpty()))
                    } else {
                        JSONObject()
                            .put("tab_id", tab.id)
                            .put("query", query)
                            .put("error", res.optString("error", "No matching elements found"))
                    }
                }

                "browser_get_page_text" -> {
                    val tab = resolvedActiveTab?.takeIf { !it.isHomePage && it.webView != null }
                        ?: return@withContext JSONObject().put("error", "No active browser tab")
                    BrowserAgentIndicatorState.activity(tab.id, extractSummary(args, "Reading page text"))
                    val maxChars = args.optInt("max_chars", 50000).coerceIn(500, 400000)
                    val includeHidden = args.optBoolean("include_hidden", false)
                    val res = callPageAgentJson(context, tab.webView, "pageText($maxChars, $includeHidden)")
                    if (res.optBoolean("ok", false)) {
                        JSONObject()
                            .put("tab_id", tab.id)
                            .put("url", tab.url)
                            .put("title", tab.title)
                            .put("text", res.optString("text", ""))
                    } else {
                        JSONObject()
                            .put("tab_id", tab.id)
                            .put("error", res.optString("error", "No readable text on page"))
                    }
                }

                "browser_form_input" -> {
                    val ref = args.optString("ref", "").trim()
                    if (ref.isBlank()) return@withContext JSONObject().put("error", "Missing ref")
                    val tab = resolvedActiveTab?.takeIf { !it.isHomePage && it.webView != null }
                        ?: return@withContext JSONObject().put("error", "No active browser tab")
                    val valueObj = args.opt("value")
                    val label = extractSummary(args, "Filling $ref")
                    val resolvedPt = callPageAgentJson(context, tab.webView, "resolve(${JSONObject.quote(ref)}, true)")
                    val vp = callPageAgentJson(context, tab.webView, "viewport()")
                    if (resolvedPt.optBoolean("ok", false)) {
                        showPointerFraction(
                            tab,
                            resolvedPt.optDouble("x", 180.0),
                            resolvedPt.optDouble("y", 240.0),
                            vp.optDouble("w", 390.0),
                            vp.optDouble("h", 800.0),
                            label
                        )
                    } else {
                        BrowserAgentIndicatorState.activity(tab.id, label)
                    }
                    val jsVal = when (valueObj) {
                        is Boolean -> valueObj.toString()
                        is Number -> valueObj.toString()
                        is JSONArray -> valueObj.toString()
                        else -> JSONObject.quote(valueObj?.toString().orEmpty())
                    }
                    val res = callPageAgentJson(context, tab.webView, "formInput(${JSONObject.quote(ref)}, $jsVal)")
                    if (res.optBoolean("ok", false)) {
                        JSONObject()
                            .put("status", "success")
                            .put("tab_id", tab.id)
                            .put("ref", ref)
                            .put("result", res.optString("text", "Updated $ref"))
                    } else {
                        JSONObject()
                            .put("error", res.optString("error", "Failed to fill $ref"))
                            .put("tab_id", tab.id)
                    }
                }

                "browser_computer" -> executeComputerAction(context, manager, resolvedActiveTab, args)

                "browser_eval_js" -> {
                    val script = args.optString("script", "").ifBlank { args.optString("text", "") }
                    if (script.isBlank()) return@withContext JSONObject().put("error", "Missing script")
                    val tab = resolvedActiveTab?.takeIf { it.webView != null }
                        ?: return@withContext JSONObject().put("error", "No active browser tab")
                    BrowserAgentIndicatorState.activity(tab.id, extractSummary(args, "Running a script"))
                    val wrapped = """
                        (function() {
                            try {
                                var __r = (function() { return ($script); })();
                                return JSON.stringify({ ok: true, value: __r === undefined ? null : __r });
                            } catch (e1) {
                                try {
                                    var __r2 = (function() { $script })();
                                    return JSON.stringify({ ok: true, value: __r2 === undefined ? null : __r2 });
                                } catch (e2) {
                                    return JSON.stringify({ ok: false, error: String(e2 && e2.message || e2) });
                                }
                            }
                        })()
                    """.trimIndent()
                    val raw = evalJsRaw(tab.webView, wrapped)
                    val parsed = runCatching { JSONObject(raw) }.getOrNull()
                    if (parsed != null && parsed.optBoolean("ok", false)) {
                        JSONObject().put("tab_id", tab.id).put("result", parsed.opt("value") ?: JSONObject.NULL)
                    } else {
                        JSONObject().put("tab_id", tab.id).put("error", parsed?.optString("error") ?: raw)
                    }
                }

                "browser_get_console_logs" -> {
                    val tab = resolvedActiveTab ?: return@withContext JSONObject().put("error", "No active browser tab")
                    val pattern = args.optString("pattern", "").trim()
                    val onlyErrors = args.optBoolean("only_errors", false)
                    val level = args.optString("level", "ALL").uppercase()
                    val limit = args.optInt("limit", 100).coerceIn(1, 300)
                    val clear = args.optBoolean("clear", false)

                    val regex = if (pattern.isNotBlank()) runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull() else null
                    val filtered = tab.snapshotConsoleLogs().filter { line ->
                        val errMatch = !onlyErrors || line.contains("[ERROR]", ignoreCase = true)
                        val lvlMatch = level == "ALL" || line.contains("[$level]", ignoreCase = true)
                        val patMatch = regex == null || regex.containsMatchIn(line)
                        errMatch && lvlMatch && patMatch
                    }.takeLast(limit)

                    if (clear) {
                        synchronized(tab.consoleLogs) { tab.consoleLogs.clear() }
                    }
                    JSONObject()
                        .put("tab_id", tab.id)
                        .put("url", tab.url)
                        .put("count", filtered.size)
                        .put("logs", JSONArray(filtered))
                }

                "browser_read_network" -> {
                    val tab = resolvedActiveTab?.takeIf { !it.isHomePage && it.webView != null }
                        ?: return@withContext JSONObject().put("error", "No active browser tab")
                    ensurePageAgentInjected(context, tab.webView)
                    val urlPattern = args.optString("url_pattern", "")
                    val onlyFailed = args.optBoolean("only_failed", false)
                    val limit = args.optInt("limit", 100).coerceIn(1, 500)
                    val clear = args.optBoolean("clear", false)
                    val raw = evalJsRaw(
                        tab.webView,
                        "window.__agentraNet ? JSON.stringify(window.__agentraNet.read(${JSONObject.quote(urlPattern)}, $onlyFailed, $limit, $clear)) : '[]'"
                    )
                    val arr = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
                    JSONObject()
                        .put("tab_id", tab.id)
                        .put("url", tab.url)
                        .put("count", arr.length())
                        .put("requests", arr)
                }

                "browser_upload_file" -> {
                    val tab = resolvedActiveTab?.takeIf { !it.isHomePage && it.webView != null }
                        ?: return@withContext JSONObject().put("error", "No active browser tab")
                    val paths = mutableListOf<String>()
                    args.optJSONArray("file_paths")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            arr.optString(i).trim().takeIf { it.isNotBlank() }?.let { paths.add(it) }
                        }
                    }
                    args.optString("filePath", "").trim().takeIf { it.isNotBlank() }?.let { paths.add(it) }
                    if (paths.isEmpty()) return@withContext JSONObject().put("error", "Missing file_paths")

                    ensurePageAgentInjected(context, tab.webView)
                    val payloadArr = JSONArray()
                    val homeDir = context.filesDir
                    for (rawPath in paths.take(20)) {
                        val f = when {
                            rawPath.startsWith("~/") -> File(homeDir, rawPath.removePrefix("~/"))
                            rawPath.startsWith("/") -> File(rawPath)
                            else -> File(homeDir, rawPath)
                        }
                        if (!f.exists() || !f.canRead()) {
                            return@withContext JSONObject().put("error", "File not found or unreadable: $rawPath")
                        }
                        if (f.length() > 25L * 1024L * 1024L) {
                            return@withContext JSONObject().put("error", "File exceeds 25 MiB limit: ${f.name}")
                        }
                        val ext = f.extension.lowercase()
                        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                        val b64 = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                        payloadArr.put(
                            JSONObject()
                                .put("name", f.name)
                                .put("mime", mime)
                                .put("base64", b64)
                        )
                    }
                    val ref = args.optString("ref", "")
                    val selector = args.optString("selector", "")
                    BrowserAgentIndicatorState.activity(tab.id, extractSummary(args, "Uploading ${paths.size} file(s)"))
                    val raw = evalJsRaw(
                        tab.webView,
                        "window.__agentraUpload ? JSON.stringify(window.__agentraUpload(${JSONObject.quote(ref)}, ${JSONObject.quote(selector)}, $payloadArr)) : JSON.stringify({ok:false,error:'upload helper unavailable'})"
                    )
                    val parsed = runCatching { JSONObject(raw) }.getOrElse { JSONObject().put("ok", false).put("error", raw) }
                    if (parsed.optBoolean("ok", false)) {
                        JSONObject()
                            .put("status", "uploaded")
                            .put("tab_id", tab.id)
                            .put("method", parsed.optString("method", "input"))
                            .put("count", parsed.optInt("count", paths.size))
                    } else {
                        JSONObject().put("error", parsed.optString("error", "File upload failed"))
                    }
                }

                "browser_lock" -> {
                    val locked = args.optBoolean("locked", true)
                    val explicitTabId = extractTabId(args)
                    val label = extractSummary(args, if (locked) "Taking control" else "Done")
                    if (locked) {
                        agentLockAllTabs = true
                        val targetId = explicitTabId.ifBlank { tabId }
                        if (targetId.isNotBlank()) {
                            userUnlockedTabs.remove(targetId)
                            BrowserAutomationController.lockTab(targetId)
                            BrowserAgentIndicatorState.activity(targetId, label)
                        }
                        manager.setTouchLocked(true)
                        JSONObject()
                            .put("status", "locked")
                            .put("locked", true)
                            .put("tab_id", targetId)
                            .put("text", "Agent lock on. Browser control locked on tab $targetId. User touches, keyboard, and zoom are locked until browser_lock(locked=false).")
                    } else {
                        if (explicitTabId.isBlank()) {
                            agentLockAllTabs = false
                            userUnlockedTabs.clear()
                            manager.listTabs().forEach {
                                BrowserAutomationController.unlockAll(it.id)
                                BrowserAgentIndicatorState.activity(it.id, label)
                            }
                            manager.setTouchLocked(false)
                            JSONObject()
                                .put("status", "unlocked")
                                .put("locked", false)
                                .put("text", "Agent lock off. Released browser control on all tabs. The user can use the browser again.")
                        } else {
                            BrowserAutomationController.unlockTab(explicitTabId)
                            BrowserAgentIndicatorState.activity(explicitTabId, label)
                            val anyStillLocked = manager.listTabs().any { it.aiLocked }
                            manager.setTouchLocked(anyStillLocked)
                            JSONObject()
                                .put("status", "unlocked")
                                .put("locked", false)
                                .put("tab_id", explicitTabId)
                                .put("text", "Released browser control on tab $explicitTabId.")
                        }
                    }
                }

                "browser_handle_dialog" -> {
                    val accept = args.optBoolean("accept", true)
                    val promptText = args.optString("prompt_text", "")
                    val explicitTabId = extractTabId(args)
                    val target = (if (explicitTabId.isNotBlank()) manager.getTab(explicitTabId) else null)
                        ?: manager.listTabs().firstOrNull { it.activeJsDialog != null }
                    val dialog = target?.activeJsDialog
                        ?: return@withContext JSONObject().put("error", "No open JavaScript dialog found")
                    val dlgType = dialog.type
                    val dlgMsg = dialog.message
                    if (accept) {
                        val reply = if (dlgType == BrowserJsDialogType.PROMPT) {
                            promptText.ifBlank { dialog.defaultPromptValue.orEmpty() }
                        } else null
                        dialog.onConfirm(reply)
                    } else {
                        dialog.onCancel()
                    }
                    target.activeJsDialog = null
                    JSONObject()
                        .put("status", "handled")
                        .put("tab_id", target.id)
                        .put("dialog_type", dlgType.name.lowercase())
                        .put("message", dlgMsg)
                        .put("accepted", accept)
                }

                "browser_batch" -> {
                    val actions = args.optJSONArray("actions") ?: args.optJSONArray("steps") ?: JSONArray()
                    if (actions.length() == 0) {
                        return@withContext JSONObject().put("error", "browser_batch requires a non-empty actions array")
                    }
                    val results = JSONArray()
                    val collectedImages = JSONArray()
                    var stoppedWithError = false
                    for (i in 0 until actions.length()) {
                        val item = actions.optJSONObject(i) ?: continue
                        val stepTool = item.optString("name", item.optString("tool", "")).trim()
                        if (stepTool.isBlank() || stepTool == "browser_batch") continue
                        val stepInput = item.optJSONObject("input")
                            ?: item.optJSONObject("arguments")
                            ?: item.optJSONObject("args")
                            ?: item
                        val stepRes = executeTool(context, stepTool, stepInput)
                        val stepImg = stepRes.optString("image_base64", "").trim()
                        if (stepImg.isNotBlank()) {
                            collectedImages.put(stepImg)
                            val copyWithoutB64 = JSONObject(stepRes.toString()).apply {
                                remove("image_base64")
                                put("image_attached", true)
                            }
                            results.put(JSONObject().put("step", i + 1).put("name", stepTool).put("result", copyWithoutB64))
                        } else {
                            results.put(JSONObject().put("step", i + 1).put("name", stepTool).put("result", stepRes))
                        }
                        if (stepRes.has("error")) {
                            stoppedWithError = true
                            break
                        }
                    }
                    JSONObject().apply {
                        put("status", if (stoppedWithError) "stopped_on_error" else "success")
                        put("completed_steps", results.length())
                        put("results", results)
                        if (collectedImages.length() > 0) {
                            put("images", collectedImages)
                        }
                    }
                }

                else -> JSONObject().put("error", "Unknown browser tool: $name")
            }
        } finally {
            manager.scheduleAgentIdleClear(4500L)
        }
    }

    private suspend fun executeComputerAction(
        context: Context,
        manager: BrowserSessionManager,
        resolvedActiveTab: BrowserTab?,
        args: JSONObject
    ): JSONObject {
        val action = args.optString("action", "").trim().lowercase()
        val tab = resolvedActiveTab?.takeIf { !it.isHomePage && it.webView != null }

        if (action == "wait") {
            val sec = args.optDouble("duration", 1.5).coerceIn(0.1, 10.0)
            val label = extractSummary(args, "Waiting ${String.format("%.1f", sec)}s")
            tab?.id?.let { BrowserAgentIndicatorState.activity(it, label) }
            delay((sec * 1000.0).toLong())
            return JSONObject().put("status", "waited").put("duration", sec).put("tab_id", tab?.id.orEmpty())
        }

        if (tab == null) {
            return JSONObject().put("error", "No active webpage loaded. Call browser_open_url first.")
        }

        val vp = callPageAgentJson(context, tab.webView, "viewport()")
        val vpW = vp.optDouble("w", 390.0).takeIf { it > 0 } ?: 390.0
        val vpH = vp.optDouble("h", 800.0).takeIf { it > 0 } ?: 800.0

        return when (action) {
            "screenshot" -> {
                val scale = args.optDouble("scale", 1.0)
                BrowserAgentIndicatorState.activity(tab.id, extractSummary(args, "Taking screenshot"))
                captureTabScreenshot(context, manager, tab, scale = scale)
            }

            "zoom" -> {
                val region = args.optJSONArray("region")
                    ?: return JSONObject().put("error", "zoom requires region [x0, y0, x1, y1]")
                val scale = args.optDouble("scale", 1.0)
                BrowserAgentIndicatorState.activity(tab.id, extractSummary(args, "Zooming into region"))
                captureTabScreenshot(context, manager, tab, scale = scale, cropRegion = region)
            }

            "left_click", "double_click", "triple_click", "right_click" -> {
                val ref = args.optString("ref", "").trim()
                val beforeUrl = tab.url
                var targetX: Double? = null
                var targetY: Double? = null
                var targetDesc = ref.ifBlank { "page point" }

                if (ref.isNotBlank()) {
                    val res = callPageAgentJson(context, tab.webView, "resolve(${JSONObject.quote(ref)}, true)")
                    if (!res.optBoolean("ok", false)) {
                        return JSONObject().put("error", res.optString("error", "Element $ref not found"))
                    }
                    targetX = res.optDouble("x", vpW / 2.0)
                    targetY = res.optDouble("y", vpH / 2.0)
                    targetDesc = res.optString("desc", ref)
                } else {
                    val pt = resolveCoordinates(tab.id, args, vpW, vpH)
                    if (pt != null) {
                        targetX = pt.first
                        targetY = pt.second
                        val hit = callPageAgentJson(context, tab.webView, "hitTest(${pt.first}, ${pt.second})")
                        targetDesc = hit.optString("desc", "(${pt.first.toInt()}, ${pt.second.toInt()})")
                    }
                }

                if (targetX == null || targetY == null) {
                    return JSONObject().put("error", "$action requires either ref or coordinate [x, y]")
                }

                val label = extractSummary(args, "Clicking ${targetDesc.take(30)}")
                showPointerFraction(tab, targetX, targetY, vpW, vpH, label)
                BrowserAgentIndicatorState.press(tab.id, true)
                delay(120L)

                val clickCount = when (action) {
                    "double_click" -> 2
                    "triple_click" -> 3
                    else -> 1
                }
                val isRight = action == "right_click"
                val fx = (targetX / vpW).coerceIn(0.01, 0.99)
                val fy = (targetY / vpH).coerceIn(0.01, 0.99)

                var clickedViaCdp = false
                val cdp = getOrConnectCdp(tab.url)
                if (cdp != null) {
                    runCatching {
                        repeat(clickCount) { cdp.dispatchMouseClick(targetX, targetY) }
                        clickedViaCdp = true
                    }
                }
                if (!clickedViaCdp) {
                    if (isRight) {
                        callPageAgentJson(context, tab.webView, "pointerEvent($targetX, $targetY, 'contextmenu')")
                    } else {
                        // Use real trusted Android MotionEvent tap + DOM pointerEvent fallback
                        repeat(clickCount) {
                            dispatchTouchTap(tab.webView, fx, fy)
                        }
                        if (clickCount == 2) {
                            callPageAgentJson(context, tab.webView, "pointerEvent($targetX, $targetY, 'dblclick')")
                        }
                    }
                }

                BrowserAgentIndicatorState.click(tab.id)
                delay(280L)
                val navigated = tab.url != beforeUrl
                JSONObject()
                    .put("status", "clicked")
                    .put("action", action)
                    .put("tab_id", tab.id)
                    .put("hit", targetDesc)
                    .put("navigated", navigated)
                    .put("url", tab.url)
                    .put("title", tab.title)
            }

            "hover" -> {
                val ref = args.optString("ref", "").trim()
                val pt = if (ref.isNotBlank()) {
                    val res = callPageAgentJson(context, tab.webView, "resolve(${JSONObject.quote(ref)}, true)")
                    if (res.optBoolean("ok", false)) res.optDouble("x", vpW / 2.0) to res.optDouble("y", vpH / 2.0) else null
                } else {
                    resolveCoordinates(tab.id, args, vpW, vpH)
                } ?: return JSONObject().put("error", "hover requires ref or coordinate")

                val label = extractSummary(args, "Hovering ${ref.ifBlank { "(${pt.first.toInt()}, ${pt.second.toInt()})" }}")
                showPointerFraction(tab, pt.first, pt.second, vpW, vpH, label)
                runCatching { getOrConnectCdp(tab.url)?.dispatchMouseMove(pt.first, pt.second) }
                callPageAgentJson(context, tab.webView, "pointerEvent(${pt.first}, ${pt.second}, 'hover')")
                JSONObject().put("status", "hovered").put("tab_id", tab.id)
            }

            "left_click_drag" -> {
                val startArr = args.optJSONArray("start_coordinate")
                val endArr = args.optJSONArray("coordinate")
                if (startArr == null || endArr == null || startArr.length() < 2 || endArr.length() < 2) {
                    return JSONObject().put("error", "left_click_drag requires start_coordinate and coordinate")
                }
                val startPt = resolveCoordinates(tab.id, JSONObject().put("coordinate", startArr), vpW, vpH)
                val endPt = resolveCoordinates(tab.id, JSONObject().put("coordinate", endArr), vpW, vpH)
                if (startPt == null || endPt == null) {
                    return JSONObject().put("error", "Invalid drag coordinates")
                }
                val label = extractSummary(args, "Dragging across page")
                showPointerFraction(tab, startPt.first, startPt.second, vpW, vpH, label)
                BrowserAgentIndicatorState.press(tab.id, true)
                dispatchTouchDrag(tab.webView, startPt.first / vpW, startPt.second / vpH, endPt.first / vpW, endPt.second / vpH)
                showPointerFraction(tab, endPt.first, endPt.second, vpW, vpH, label)
                BrowserAgentIndicatorState.press(tab.id, false)
                JSONObject().put("status", "dragged").put("tab_id", tab.id)
            }

            "type" -> {
                val text = args.optString("text", "")
                val ref = args.optString("ref", "").trim()
                val label = extractSummary(args, "Typing \"${text.replace("\n", " ").take(24)}\"")
                BrowserAgentIndicatorState.activity(tab.id, label)
                if (ref.isNotBlank()) {
                    val res = callPageAgentJson(context, tab.webView, "formInput(${JSONObject.quote(ref)}, ${JSONObject.quote(text.trimEnd('\n'))})")
                    if (text.endsWith("\n")) {
                        sendSingleKey(tab, "Enter")
                    }
                    return JSONObject()
                        .put("status", if (res.optBoolean("ok", false)) "typed" else "error")
                        .put("tab_id", tab.id)
                        .put("text", text)
                }
                val lines = text.split("\n")
                for (idx in lines.indices) {
                    val segment = lines[idx]
                    if (segment.isNotEmpty()) {
                        val cdpSent = runCatching {
                            getOrConnectCdp(tab.url)?.let { it.dispatchInsertText(segment); true } ?: false
                        }.getOrDefault(false)
                        if (!cdpSent) {
                            val ins = callPageAgentJson(context, tab.webView, "insertText(${JSONObject.quote(segment)})")
                            if (!ins.optBoolean("ok", false)) {
                                return JSONObject().put("error", ins.optString("error", "No text field focused. Click a field first."))
                            }
                        }
                    }
                    if (idx < lines.lastIndex) {
                        sendSingleKey(tab, "Enter")
                    }
                }
                JSONObject().put("status", "typed").put("tab_id", tab.id).put("text", text)
            }

            "key" -> {
                val rawKeys = args.optString("text", "").ifBlank { args.optString("key", "Enter") }.trim()
                val repeat = args.optInt("repeat", 1).coerceIn(1, 100)
                val label = extractSummary(args, "Pressing $rawKeys")
                BrowserAgentIndicatorState.activity(tab.id, label)
                val tokens = rawKeys.split(Regex("\\s+")).filter { it.isNotBlank() }
                repeat(repeat) {
                    for (token in tokens) {
                        sendSingleKey(tab, token)
                        delay(45L)
                    }
                }
                delay(180L)
                JSONObject().put("status", "key_pressed").put("tab_id", tab.id).put("keys", rawKeys)
            }

            "scroll" -> {
                val direction = args.optString("scroll_direction", "down").lowercase()
                val amount = args.optInt("scroll_amount", 3).coerceIn(1, 10)
                val pt = resolveCoordinates(tab.id, args, vpW, vpH) ?: (vpW / 2.0 to vpH / 2.0)
                val label = extractSummary(args, "Scrolling $direction")
                showPointerFraction(tab, pt.first, pt.second, vpW, vpH, label)
                val pxStep = amount * 140.0
                val dx = when (direction) {
                    "left" -> -pxStep
                    "right" -> pxStep
                    else -> 0.0
                }
                val dy = when (direction) {
                    "up" -> -pxStep
                    "down" -> pxStep
                    else -> 0.0
                }
                callPageAgentJson(context, tab.webView, "scrollAt(${pt.first}, ${pt.second}, $dx, $dy)")
                delay(280L)
                captureTabScreenshot(context, manager, tab, scale = args.optDouble("scale", 1.0))
            }

            "scroll_to" -> {
                val ref = args.optString("ref", "").trim()
                val label = extractSummary(args, "Scrolling to ${ref.ifBlank { "target" }}")
                BrowserAgentIndicatorState.activity(tab.id, label)
                if (ref.isNotBlank()) {
                    val res = callPageAgentJson(context, tab.webView, "scrollIntoViewRef(${JSONObject.quote(ref)})")
                    if (!res.optBoolean("ok", false)) {
                        return JSONObject().put("error", res.optString("error", "Could not scroll to $ref"))
                    }
                    delay(180L)
                    return JSONObject().put("status", "scrolled").put("tab_id", tab.id).put("ref", ref)
                } else {
                    val x = args.optDouble("x", 0.0)
                    val y = args.optDouble("y", 500.0)
                    evalJsRaw(tab.webView, "window.scrollTo({left:$x, top:$y, behavior:'instant'}); 'ok'")
                    return JSONObject().put("status", "scrolled").put("tab_id", tab.id)
                }
            }

            else -> JSONObject().put("error", "Unsupported browser_computer action: $action")
        }
    }

    private suspend fun sendSingleKey(tab: BrowserTab, keyToken: String) {
        val cdpSent = runCatching {
            getOrConnectCdp(tab.url)?.let { it.dispatchKey(keyToken); true } ?: false
        }.getOrDefault(false)
        if (cdpSent) return

        val keyLower = keyToken.lowercase()
        val js = when {
            keyLower == "enter" || keyLower == "return" -> """
                (function(){
                  var el = document.activeElement;
                  if (!el) return;
                  var opts = { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true };
                  el.dispatchEvent(new KeyboardEvent('keydown', opts));
                  el.dispatchEvent(new KeyboardEvent('keypress', opts));
                  el.dispatchEvent(new KeyboardEvent('keyup', opts));
                  if (el.form && typeof el.form.requestSubmit === 'function') { try { el.form.requestSubmit(); } catch(e){ el.form.submit(); } }
                  else if (el.form) { try { el.form.submit(); } catch(e){} }
                })()
            """.trimIndent()
            keyLower == "backspace" -> """
                (function(){
                  var el = document.activeElement;
                  if (el && typeof el.value === 'string' && el.value.length > 0) {
                    var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                    var setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
                    var next = el.value.slice(0, -1);
                    if (setter) setter.call(el, next); else el.value = next;
                    el.dispatchEvent(new Event('input', {bubbles:true}));
                  }
                })()
            """.trimIndent()
            keyLower == "ctrl+a" || keyLower == "meta+a" -> "(function(){ var el = document.activeElement; if(el && el.select) el.select(); else document.execCommand('selectAll'); })()"
            else -> """
                (function(){
                  var el = document.activeElement || document.body;
                  var k = ${JSONObject.quote(keyToken)};
                  var opts = { key: k, code: k, bubbles: true, cancelable: true };
                  el.dispatchEvent(new KeyboardEvent('keydown', opts));
                  el.dispatchEvent(new KeyboardEvent('keyup', opts));
                })()
            """.trimIndent()
        }
        evalJsRaw(tab.webView, js)
    }
}
