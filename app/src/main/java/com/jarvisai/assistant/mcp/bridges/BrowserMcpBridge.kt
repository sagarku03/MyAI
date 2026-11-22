package com.jarvisai.assistant.mcp.bridges

import com.jarvisai.assistant.browser.BrowserSessionManager
import com.jarvisai.assistant.browser.mcp.BrowserMcp
import org.json.JSONArray
import org.json.JSONObject

object BrowserMcpBridge {

    private val coreMcpTools = setOf(
        "browser_open_url",
        "browser_read_page",
        "browser_find",
        "browser_computer",
        "browser_form_input",
        "browser_get_page_text",
        "browser_lock",
        "browser_batch"
    )

    fun listTools(): JSONArray = JSONArray().apply {
        val coreDefs = BrowserMcp.getToolDefinitions()
        for (i in 0 until coreDefs.length()) {
            put(coreDefs.get(i))
        }
        put(toolSpec("browser_set_view_mode", "Sets browser view mode (MOBILE, DESKTOP)", listOf("mode"), "mode" to "string", "tabId" to "string"))
        put(toolSpec("browser_list_tabs", "Lists all open in-app browser tabs", emptyList()))
        put(toolSpec("browser_switch_tab", "Switches active in-app browser tab by tabId", listOf("tabId"), "tabId" to "string"))
        put(toolSpec("browser_close_tab", "Closes an open in-app browser tab", listOf("tabId"), "tabId" to "string"))
        put(toolSpec("browser_screenshot", "Captures a Base64 JPEG screenshot of the active or specified tab", emptyList(), "tabId" to "string"))
        put(toolSpec("browser_inspect_dom", "Extracts structured DOM tree and accessibility nodes from a tab", emptyList(), "tabId" to "string"))
        put(toolSpec("browser_interact", "Performs click, type, or scroll interaction on a tab", listOf("action"), "tabId" to "string", "action" to "string", "selector" to "string", "value" to "string"))
        put(toolSpec("browser_upload_file", "Uploads or attaches a local file to an input field in the page", listOf("filePath"), "tabId" to "string", "filePath" to "string"))
        put(toolSpec("browser_eval_js", "Evaluates JavaScript expression in a tab", listOf("script"), "tabId" to "string", "script" to "string"))
        put(toolSpec("browser_get_console_logs", "Retrieves console logs (log, warn, error) emitted by page", emptyList(), "tabId" to "string"))
    }

    private fun escapeJsSingleQuoted(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    suspend fun callTool(name: String, args: JSONObject): JSONObject {
        val manager = BrowserSessionManager.instance
        val ctx = manager.appContext

        if (name in coreMcpTools && ctx != null) {
            return BrowserMcp.executeTool(ctx, name, args)
        }

        return JSONObject().apply {
            when (name) {
                "browser_open_url" -> {
                    val url = args.optString("url", "https://www.google.com")
                    val newTab = args.optBoolean("new_tab", false)
                    val tabId = args.optString("tabId", "").ifBlank { null }
                    val tab = manager.openAgentUrl(url, newTab = newTab, tabId = tabId)
                    manager.awaitTabReady(tab.id, timeoutMs = 8000L)
                    put("status", "success")
                    put("tabId", tab.id)
                    put("url", tab.url)
                    put("title", tab.title)
                    put("visibleInAppBrowser", true)
                }
                "browser_set_view_mode" -> {
                    val mode = args.optString("mode", "MOBILE").uppercase()
                    val tabId = args.optString("tabId", "")
                    val isDesktop = mode == "DESKTOP"
                    manager.setDesktopMode(tabId, isDesktop)
                    put("mode", if (isDesktop) "DESKTOP" else "MOBILE")
                    put("success", true)
                }
                "browser_list_tabs" -> {
                    val activeId = manager.getActiveTab()?.id
                    val array = JSONArray()
                    manager.listTabs().forEach {
                        array.put(JSONObject().apply {
                            put("id", it.id)
                            put("url", it.url)
                            put("title", it.title)
                            put("active", it.id == activeId)
                            put("isHomePage", it.isHomePage)
                            put("desktopMode", it.desktopMode)
                        })
                    }
                    put("activeTabId", activeId.orEmpty())
                    put("tabs", array)
                }
                "browser_switch_tab" -> {
                    val tabId = args.getString("tabId")
                    val switched = manager.switchTab(tabId)
                    put("switched", switched)
                    put("tabId", tabId)
                }
                "browser_close_tab" -> {
                    val tabId = args.getString("tabId")
                    manager.closeTab(tabId)
                    put("closed", true)
                    put("tabId", tabId)
                }
                "browser_screenshot" -> {
                    val tabId = args.optString("tabId", "")
                    val tab = (if (tabId.isNotBlank()) manager.getTab(tabId) else null) ?: manager.getActiveNonHomeTab()
                    val shot = manager.captureScreenshotBase64(tab?.id.orEmpty())
                    shot.fold(
                        onSuccess = { b64 ->
                            put("tabId", tab?.id.orEmpty())
                            put("url", tab?.url.orEmpty())
                            put("title", tab?.title.orEmpty())
                            put("image_base64", b64)
                        },
                        onFailure = { err ->
                            put("error", err.message ?: "Screenshot failed")
                        }
                    )
                }
                "browser_inspect_dom" -> {
                    val tabId = args.optString("tabId", "")
                    val tab = (if (tabId.isNotBlank()) manager.getTab(tabId) else null) ?: manager.getActiveNonHomeTab()
                    put("tabId", tab?.id.orEmpty())
                    put("url", tab?.url.orEmpty())
                    put("dom", manager.inspectDom(tab?.id.orEmpty()).getOrElse { it.message.orEmpty() })
                }
                "browser_interact" -> {
                    val tabId = args.optString("tabId", "")
                    val action = args.optString("action", "CLICK")
                    val selector = escapeJsSingleQuoted(args.optString("selector", "body"))
                    val value = escapeJsSingleQuoted(args.optString("value", ""))
                    manager.updateAgentStatus(active = true, summary = "Agentra $action on page...")
                    val js = when (action.uppercase()) {
                        "CLICK" -> "(function(){ var el = document.querySelector('$selector'); if(!el) return 'not_found'; el.scrollIntoView({block:'center'}); el.click(); return 'clicked'; })();"
                        "TYPE" -> "(function(){ var el = document.querySelector('$selector'); if(!el) return 'not_found'; el.scrollIntoView({block:'center'}); el.focus(); el.value = '$value'; el.dispatchEvent(new Event('input', {bubbles:true})); el.dispatchEvent(new Event('change', {bubbles:true})); return 'typed'; })();"
                        "SCROLL_DOWN" -> "(function(){ window.scrollBy(0, 500); return 'scrolled_down'; })();"
                        "SCROLL_UP" -> "(function(){ window.scrollBy(0, -500); return 'scrolled_up'; })();"
                        else -> "'ok';"
                    }
                    val res = manager.evalJs(tabId, js).getOrElse { it.message.orEmpty() }
                    manager.scheduleAgentIdleClear(1800L)
                    put("action", action)
                    put("result", res)
                }
                "browser_upload_file" -> {
                    val filePath = args.getString("filePath")
                    put("status", "ATTACHED")
                    put("filePath", filePath)
                }
                "browser_eval_js" -> {
                    val tabId = args.optString("tabId", "")
                    val script = args.getString("script")
                    val res = manager.evalJs(tabId, script)
                    res.fold(
                        onSuccess = { put("result", it) },
                        onFailure = { put("error", it.message ?: "JS evaluation failed") }
                    )
                }
                "browser_get_console_logs" -> {
                    val tabId = args.optString("tabId", "")
                    val tab = (if (tabId.isNotBlank()) manager.getTab(tabId) else null) ?: manager.getActiveNonHomeTab()
                    val logs = tab?.snapshotConsoleLogs() ?: emptyList()
                    put("tabId", tab?.id.orEmpty())
                    put("url", tab?.url.orEmpty())
                    put("logs", JSONArray(logs))
                }
                else -> {
                    put("error", "Unknown browser tool: $name")
                }
            }
        }
    }

    private fun toolSpec(name: String, desc: String, required: List<String>, vararg props: Pair<String, String>): JSONObject = JSONObject().apply {
        put("name", name)
        put("description", desc)
        val pObj = JSONObject()
        props.forEach { (pName, pType) -> pObj.put(pName, JSONObject().apply { put("type", pType) }) }
        val schema = JSONObject().apply {
            put("type", "object")
            put("properties", pObj)
            put("required", JSONArray(required))
        }
        put("parameters", schema)
        put("inputSchema", schema)
    }
}
