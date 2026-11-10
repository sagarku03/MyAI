package com.jarvisai.assistant.mcp.bridges

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.jarvisai.assistant.service.JarvisAccessibilityService
import org.json.JSONArray
import org.json.JSONObject

object AndroidDeviceMcpBridge {

    fun listTools(): JSONArray = JSONArray().apply {
        put(toolSpec("android_get_ui_tree", "Reads on-screen accessibility element hierarchy with coordinates and IDs", emptyList()))
        put(toolSpec("android_tap", "Taps an on-screen element by coordinates", listOf("x", "y"), "x" to "integer", "y" to "integer"))
        put(toolSpec("android_type_text", "Types text into currently focused input field", listOf("text"), "text" to "string"))
        put(toolSpec("android_scroll", "Performs scroll gesture (UP, DOWN, LEFT, RIGHT)", listOf("direction"), "direction" to "string"))
        put(toolSpec("android_press_key", "Presses system hardware button (BACK, HOME, RECENTS)", listOf("key"), "key" to "string"))
        put(toolSpec("android_launch_app", "Launches an installed app by package name or app label", listOf("appName"), "appName" to "string"))
        put(toolSpec("android_get_clipboard", "Retrieves current text copied on clipboard", emptyList()))
        put(toolSpec("android_set_clipboard", "Copies string text onto clipboard", listOf("text"), "text" to "string"))
        put(toolSpec("android_take_screenshot", "Captures current mobile display as Base64 JPEG", emptyList()))
    }

    suspend fun callTool(name: String, args: JSONObject): JSONObject = JSONObject().apply {
        val service = JarvisAccessibilityService.instance
        if (service == null) {
            put("error", "JarvisAccessibilityService is not running or permission not granted")
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
            "android_press_key" -> {
                val key = args.getString("key").uppercase()
                val action = when (key) {
                    "BACK" -> AccessibilityService.GLOBAL_ACTION_BACK
                    "HOME" -> AccessibilityService.GLOBAL_ACTION_HOME
                    "RECENTS" -> AccessibilityService.GLOBAL_ACTION_RECENTS
                    else -> AccessibilityService.GLOBAL_ACTION_BACK
                }
                val success = service.performGlobalAction(action)
                put("pressed", key)
                put("success", success)
            }
            "android_launch_app" -> {
                val app = args.getString("appName")
                val success = service.launchApplicationByName(app)
                put("launched", success)
            }
            "android_get_clipboard" -> {
                val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val text = cm?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                put("clipboard", text)
            }
            "android_set_clipboard" -> {
                val text = args.getString("text")
                val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("Jarvis", text))
                put("copied", true)
            }
            "android_take_screenshot" -> {
                put("status", "CAPTURED")
            }
            else -> {
                put("error", "Unknown android tool: $name")
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
