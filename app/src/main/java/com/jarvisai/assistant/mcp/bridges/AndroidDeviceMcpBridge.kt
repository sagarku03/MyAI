package com.jarvisai.assistant.mcp.bridges

import com.jarvisai.assistant.service.JarvisAccessibilityService
import org.json.JSONArray
import org.json.JSONObject

object AndroidDeviceMcpBridge {

    fun listTools(): JSONArray = JSONArray().apply {
        put(toolSpec("android_get_ui_tree", "Reads on-screen accessibility element hierarchy with coordinates and IDs", emptyList()))
        put(toolSpec("android_tap", "Taps an on-screen element by ID or coordinates", listOf("x", "y"), "x" to "integer", "y" to "integer"))
        put(toolSpec("android_type_text", "Types text into active input field", listOf("text"), "text" to "string"))
        put(toolSpec("android_scroll", "Performs scroll gesture (UP, DOWN, LEFT, RIGHT)", listOf("direction"), "direction" to "string"))
        put(toolSpec("android_launch_app", "Launches an installed app by package name or app label", listOf("appName"), "appName" to "string"))
    }

    suspend fun callTool(name: String, args: JSONObject): JSONObject = JSONObject().apply {
        val service = JarvisAccessibilityService.instance
        if (service == null) {
            put("error", "JarvisAccessibilityService is not running or enabled")
            return@apply
        }

        when (name) {
            "android_get_ui_tree" -> {
                put("ui_hierarchy", service.captureWindowHierarchyJson())
            }
            "android_tap" -> {
                val x = args.getInt("x")
                val y = args.getInt("y")
                val success = service.clickCoordinates(x.toFloat(), y.toFloat())
                put("tapped", success)
            }
            "android_type_text" -> {
                val text = args.getString("text")
                val success = service.inputText(text)
                put("typed", success)
            }
            "android_scroll" -> {
                val direction = args.optString("direction", "DOWN")
                val success = service.performScroll(direction)
                put("scrolled", success)
            }
            "android_launch_app" -> {
                val app = args.getString("appName")
                val success = service.launchApplicationByName(app)
                put("launched", success)
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
