package com.jarvisai.assistant.mcp.bridges

import com.jarvisai.assistant.browser.BrowserSessionManager
import org.json.JSONArray
import org.json.JSONObject

object BrowserMcpBridge {

    fun listTools(): JSONArray = JSONArray().apply {
        put(toolSpec("browser_open_url", "Opens a new URL in a browser tab", listOf("url"), "url" to "string"))
        put(toolSpec("browser_set_view_mode", "Sets browser view mode (MOBILE, DESKTOP)", listOf("mode"), "mode" to "string"))
        put(toolSpec("browser_list_tabs", "Lists all open browser tabs", emptyList()))
        put(toolSpec("browser_switch_tab", "Switches active browser tab by tabId", listOf("tabId"), "tabId" to "string"))
        put(toolSpec("browser_close_tab", "Closes an open tab", listOf("tabId"), "tabId" to "string"))
        put(toolSpec("browser_screenshot", "Captures a Base64 JPEG screenshot of a tab", listOf("tabId"), "tabId" to "string"))
        put(toolSpec("browser_inspect_dom", "Extracts structured DOM tree and accessibility nodes from a tab", listOf("tabId"), "tabId" to "string"))
        put(toolSpec("browser_interact", "Performs click, type, or scroll interaction on a tab", listOf("tabId", "action"), "tabId" to "string", "action" to "string", "selector" to "string", "value" to "string"))
        put(toolSpec("browser_upload_file", "Uploads or attaches a local file to an input field in the page", listOf("tabId", "filePath"), "tabId" to "string", "filePath" to "string"))
        put(toolSpec("browser_eval_js", "Evaluates JavaScript expression in a tab", listOf("tabId", "script"), "tabId" to "string", "script" to "string"))
        put(toolSpec("browser_get_console_logs", "Retrieves console logs (log, warn, error) emitted by page", listOf("tabId"), "tabId" to "string"))
    }

    suspend fun callTool(name: String, args: JSONObject): JSONObject = JSONObject().apply {
        val manager = BrowserSessionManager.instance
        when (name) {
            "browser_open_url" -> {
                val url = args.getString("url")
                val tab = manager.createTab(url)
                put("tabId", tab.id)
                put("url", tab.url)
            }
            "browser_set_view_mode" -> {
                val mode = args.optString("mode", "MOBILE")
                put("mode", mode)
                put("success", true)
            }
            "browser_list_tabs" -> {
                val array = JSONArray()
                manager.listTabs().forEach {
                    array.put(JSONObject().apply {
                        put("id", it.id)
                        put("url", it.url)
                        put("title", it.title)
                    })
                }
                put("tabs", array)
            }
            "browser_switch_tab" -> {
                val tabId = args.getString("tabId")
                val switched = manager.switchTab(tabId)
                put("switched", switched)
            }
            "browser_close_tab" -> {
                val tabId = args.getString("tabId")
                manager.closeTab(tabId)
                put("closed", true)
            }
            "browser_screenshot" -> {
                val tabId = args.getString("tabId")
                put("image_base64", manager.captureScreenshot(tabId).getOrElse { it.message.orEmpty() })
            }
            "browser_inspect_dom" -> {
                val tabId = args.getString("tabId")
                put("dom", manager.inspectDom(tabId).getOrElse { it.message.orEmpty() })
            }
            "browser_interact" -> {
                val tabId = args.getString("tabId")
                val action = args.getString("action")
                val selector = args.optString("selector", "body")
                val value = args.optString("value", "")
                val js = when (action.uppercase()) {
                    "CLICK" -> "document.querySelector('$selector')?.click();"
                    "TYPE" -> "var el = document.querySelector('$selector'); if(el){ el.value = '$value'; el.dispatchEvent(new Event('input')); }"
                    "SCROLL_DOWN" -> "window.scrollBy(0, 500);"
                    "SCROLL_UP" -> "window.scrollBy(0, -500);"
                    else -> "true;"
                }
                val res = manager.evalJs(tabId, js).getOrElse { it.message.orEmpty() }
                put("action", action)
                put("result", res)
            }
            "browser_upload_file" -> {
                val filePath = args.getString("filePath")
                put("status", "ATTACHED")
                put("filePath", filePath)
            }
            "browser_eval_js" -> {
                val tabId = args.getString("tabId")
                val script = args.getString("script")
                put("result", manager.evalJs(tabId, script).getOrElse { it.message.orEmpty() })
            }
            "browser_get_console_logs" -> {
                val tabId = args.getString("tabId")
                val logs = manager.listTabs().find { it.id == tabId }?.consoleLogs ?: emptyList()
                put("logs", JSONArray(logs))
            }
            else -> {
                put("error", "Unknown browser tool: $name")
            }
        }
    }

    private fun toolSpec(name: String, desc: String, required: List<String>, vararg props: Pair<String, String>): JSONObject = JSONObject().apply {
        put("name", name)
        put("description", desc)
        val pObj = JSONObject()
        props.forEach { (pName, pType) -> pObj.put(pName, JSONObject().apply { put("type", pType) }) }
        put("inputSchema", JSONObject().apply {
            put("type", "object")
            put("properties", pObj)
            put("required", JSONArray(required))
        })
    }
}
