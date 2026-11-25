package com.jarvisai.assistant.mcp.bridges

import com.jarvisai.assistant.browser.BrowserSessionManager
import com.jarvisai.assistant.browser.mcp.BrowserMcp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Clean delegation bridge exposing the full 17-tool AntiGem-compatible `browser_mcp`
 * specification while retaining backward-compatible legacy aliases in [callTool].
 */
object BrowserMcpBridge {

    fun listTools(): JSONArray = BrowserMcp.getToolDefinitions()

    suspend fun callTool(name: String, args: JSONObject): JSONObject {
        val manager = BrowserSessionManager.instance
        val ctx = manager.appContext

        // Legacy compatibility aliases routed into the unified 17-tool engine
        val normalizedName: String
        val normalizedArgs: JSONObject
        when (name) {
            "browser_screenshot" -> {
                normalizedName = "browser_computer"
                normalizedArgs = JSONObject(args.toString()).put("action", "screenshot")
            }
            "browser_inspect_dom" -> {
                normalizedName = "browser_read_page"
                normalizedArgs = JSONObject(args.toString())
            }
            "browser_interact" -> {
                val act = args.optString("action", "CLICK").uppercase()
                normalizedName = "browser_computer"
                normalizedArgs = JSONObject(args.toString()).apply {
                    when (act) {
                        "SCROLL_DOWN" -> {
                            put("action", "scroll")
                            put("scroll_direction", "down")
                        }
                        "SCROLL_UP" -> {
                            put("action", "scroll")
                            put("scroll_direction", "up")
                        }
                        "TYPE" -> {
                            put("action", "type")
                            put("text", args.optString("value", ""))
                        }
                        else -> put("action", "left_click")
                    }
                }
            }
            else -> {
                normalizedName = name
                normalizedArgs = args
            }
        }

        if (ctx != null) {
            return BrowserMcp.executeTool(ctx, normalizedName, normalizedArgs)
        }

        // Minimal fallback if context wasn't attached yet
        return when (normalizedName) {
            "browser_list_tabs" -> {
                val activeId = manager.getActiveTab()?.id
                val array = JSONArray()
                manager.listTabs().forEach {
                    array.put(JSONObject().apply {
                        put("tab_id", it.id)
                        put("url", it.url)
                        put("title", it.title)
                        put("active", it.id == activeId)
                    })
                }
                JSONObject().put("active_tab_id", activeId.orEmpty()).put("tabs", array)
            }
            else -> JSONObject().put("error", "BrowserSessionManager context not initialized")
        }
    }
}
