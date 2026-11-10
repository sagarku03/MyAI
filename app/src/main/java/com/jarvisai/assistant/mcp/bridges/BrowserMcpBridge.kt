package com.jarvisai.assistant.mcp.bridges

import com.jarvisai.assistant.browser.BrowserSessionManager
import org.json.JSONArray
import org.json.JSONObject

object BrowserMcpBridge {

    fun listTools(): JSONArray = JSONArray().apply {
        put(toolSpec("browser_open_url", "Opens a new URL in a browser tab", listOf("url"), "url" to "string"))
        put(toolSpec("browser_list_tabs", "Lists all open browser tabs", emptyList()))
        put(toolSpec("browser_inspect_dom", "Extracts structured DOM tree from a tab", listOf("tabId"), "tabId" to "string"))
        put(toolSpec("browser_eval_js", "Evaluates JavaScript expression in a tab", listOf("tabId", "script"), "tabId" to "string", "script" to "string"))
        put(toolSpec("browser_screenshot", "Captures a Base64 JPEG screenshot of a tab", listOf("tabId"), "tabId" to "string"))
        put(toolSpec("browser_close_tab", "Closes an open tab", listOf("tabId"), "tabId" to "string"))
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
            "browser_list_tabs" -> {
                val array = JSONArray()
                manager.listTabs().forEach {
                    array.put(JSONObject().apply { put("id", it.id); put("url", it.url); put("title", it.title) })
                }
                put("tabs", array)
            }
            "browser_inspect_dom" -> {
                val tabId = args.getString("tabId")
                put("dom", manager.inspectDom(tabId).getOrElse { it.message.orEmpty() })
            }
            "browser_eval_js" -> {
                val tabId = args.getString("tabId")
                val script = args.getString("script")
                put("result", manager.evalJs(tabId, script).getOrElse { it.message.orEmpty() })
            }
            "browser_screenshot" -> {
                val tabId = args.getString("tabId")
                put("image_base64", manager.captureScreenshot(tabId).getOrElse { it.message.orEmpty() })
            }
            "browser_close_tab" -> {
                val tabId = args.getString("tabId")
                manager.closeTab(tabId)
                put("closed", true)
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
