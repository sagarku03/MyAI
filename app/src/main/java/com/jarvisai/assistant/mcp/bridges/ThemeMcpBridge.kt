package com.jarvisai.assistant.mcp.bridges

import android.content.Context
import com.jarvisai.assistant.theme.ThemeTerminal
import org.json.JSONArray
import org.json.JSONObject

/**
 * theme_mcp: Lets AI inspect and switch Jarvis terminal & UI themes via AndroidLocalBridgeServer (/theme).
 */
object ThemeMcpBridge {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun listTools(): JSONArray = JSONArray().apply {
        put(
            JSONObject().apply {
                put("name", "theme_list")
                put("description", "List all available terminal and UI palettes along with the active palette.")
                put("inputSchema", JSONObject().put("type", "object").put("properties", JSONObject()))
            }
        )
        put(
            JSONObject().apply {
                put("name", "theme_get")
                put("description", "Get the active terminal color palette and ANSI hex values.")
                put("inputSchema", JSONObject().put("type", "object").put("properties", JSONObject()))
            }
        )
        put(
            JSONObject().apply {
                put("name", "theme_apply")
                put("description", "Switch the active terminal palette by name.")
                put(
                    "inputSchema",
                    JSONObject().apply {
                        put("type", "object")
                        put(
                            "properties",
                            JSONObject().put("name", JSONObject().put("type", "string").put("description", "Palette name (e.g. Dracula, Cyberpunk, Monokai)"))
                        )
                        put("required", JSONArray(listOf("name")))
                    }
                )
            }
        )
    }

    fun callTool(name: String, args: JSONObject): JSONObject {
        return when (name) {
            "theme_list" -> {
                val arr = JSONArray()
                ThemeTerminal.ALL_PALETTES.forEach { p ->
                    arr.put(
                        JSONObject()
                            .put("name", p.name)
                            .put("background", p.background)
                            .put("foreground", p.foreground)
                            .put("cursor", p.cursor)
                            .put("active", p.name == ThemeTerminal.currentPalette.name)
                    )
                }
                JSONObject()
                    .put("activePalette", ThemeTerminal.currentPalette.name)
                    .put("palettes", arr)
            }
            "theme_get" -> {
                val p = ThemeTerminal.currentPalette
                JSONObject()
                    .put("name", p.name)
                    .put("background", p.background)
                    .put("foreground", p.foreground)
                    .put("cursor", p.cursor)
                    .put("ansiColors", JSONArray(p.ansiColors))
            }
            "theme_apply" -> {
                val requested = args.optString("name", "")
                    .ifBlank { args.optString("id", "") }
                    .ifBlank { args.optString("palette", "") }
                    .trim()
                val match = ThemeTerminal.ALL_PALETTES.firstOrNull {
                    it.name.equals(requested, ignoreCase = true) || it.name.contains(requested, ignoreCase = true)
                }
                if (match == null) {
                    JSONObject().put("error", "Theme palette '$requested' not found")
                } else {
                    ThemeTerminal.applyPalette(match)
                    appContext?.getSharedPreferences("jarvis_theme_prefs", Context.MODE_PRIVATE)
                        ?.edit()
                        ?.putString("terminal_palette", match.name)
                        ?.apply()
                    JSONObject().put("applied", true).put("name", match.name)
                }
            }
            else -> JSONObject().put("error", "Unknown theme tool: $name")
        }
    }
}
