package com.jarvisai.assistant.mcp.bridges

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * notes_mcp: Persistent on-device Notes & To-Do lists MCP server exposed via AndroidLocalBridgeServer (/notes).
 */
object NotesMcpBridge {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun notesFile(): File? {
        val ctx = appContext ?: return null
        return File(ctx.filesDir, "jarvis_mcp_notes.json")
    }

    @Synchronized
    private fun loadNotes(): JSONArray {
        val file = notesFile() ?: return JSONArray()
        if (!file.exists()) return JSONArray()
        return runCatching { JSONArray(file.readText()) }.getOrElse { JSONArray() }
    }

    @Synchronized
    private fun saveNotes(arr: JSONArray) {
        val file = notesFile() ?: return
        file.parentFile?.mkdirs()
        file.writeText(arr.toString(2))
    }

    fun listTools(): JSONArray = JSONArray().apply {
        put(
            JSONObject().apply {
                put("name", "notes_list")
                put("description", "List saved notes and to-do lists stored on this device.")
                put(
                    "inputSchema",
                    JSONObject().apply {
                        put("type", "object")
                        put(
                            "properties",
                            JSONObject().put(
                                "kind",
                                JSONObject().put("type", "string").put("description", "Optional filter: 'note' or 'todo'")
                            )
                        )
                    }
                )
            }
        )
        put(
            JSONObject().apply {
                put("name", "notes_get")
                put("description", "Read one note or to-do list by id.")
                put(
                    "inputSchema",
                    JSONObject().apply {
                        put("type", "object")
                        put(
                            "properties",
                            JSONObject().put("id", JSONObject().put("type", "string").put("description", "Note ID"))
                        )
                        put("required", JSONArray(listOf("id")))
                    }
                )
            }
        )
        put(
            JSONObject().apply {
                put("name", "notes_create")
                put("description", "Create a note or to-do list.")
                put(
                    "inputSchema",
                    JSONObject().apply {
                        put("type", "object")
                        put(
                            "properties",
                            JSONObject()
                                .put("title", JSONObject().put("type", "string").put("description", "Title"))
                                .put("text", JSONObject().put("type", "string").put("description", "Note body text"))
                                .put("kind", JSONObject().put("type", "string").put("description", "'note' or 'todo'"))
                                .put("items", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")))
                        )
                    }
                )
            }
        )
        put(
            JSONObject().apply {
                put("name", "notes_update")
                put("description", "Update a note's title, text, or to-do items.")
                put(
                    "inputSchema",
                    JSONObject().apply {
                        put("type", "object")
                        put(
                            "properties",
                            JSONObject()
                                .put("id", JSONObject().put("type", "string"))
                                .put("title", JSONObject().put("type", "string"))
                                .put("text", JSONObject().put("type", "string"))
                                .put("done", JSONObject().put("type", "boolean"))
                        )
                        put("required", JSONArray(listOf("id")))
                    }
                )
            }
        )
        put(
            JSONObject().apply {
                put("name", "notes_delete")
                put("description", "Delete a note or to-do list by id.")
                put(
                    "inputSchema",
                    JSONObject().apply {
                        put("type", "object")
                        put(
                            "properties",
                            JSONObject().put("id", JSONObject().put("type", "string"))
                        )
                        put("required", JSONArray(listOf("id")))
                    }
                )
            }
        )
    }

    @Synchronized
    fun callTool(name: String, args: JSONObject): JSONObject {
        val arr = loadNotes()
        return when (name) {
            "notes_list" -> {
                val filterKind = args.optString("kind", "").trim().lowercase()
                val out = JSONArray()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (filterKind.isBlank() || obj.optString("kind", "note").equals(filterKind, ignoreCase = true)) {
                        out.put(obj)
                    }
                }
                JSONObject().put("count", out.length()).put("notes", out)
            }
            "notes_get" -> {
                val id = args.optString("id", "").trim()
                val found = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                    .firstOrNull { it.optString("id") == id }
                found ?: JSONObject().put("error", "Note '$id' not found")
            }
            "notes_create" -> {
                val title = args.optString("title", "Untitled").trim()
                val text = args.optString("text", "")
                val rawItems = args.optJSONArray("items")
                val kind = args.optString("kind", if (rawItems != null && rawItems.length() > 0) "todo" else "note")
                val itemsArray = JSONArray()
                if (rawItems != null) {
                    for (i in 0 until rawItems.length()) {
                        val itemVal = rawItems.opt(i)
                        if (itemVal is JSONObject) {
                            itemsArray.put(itemVal)
                        } else if (itemVal != null) {
                            itemsArray.put(JSONObject().put("text", itemVal.toString()).put("done", false))
                        }
                    }
                }
                val entry = JSONObject().apply {
                    put("id", UUID.randomUUID().toString().take(8))
                    put("kind", kind)
                    put("title", title)
                    put("text", text)
                    put("items", itemsArray)
                    put("updatedAt", System.currentTimeMillis())
                }
                arr.put(entry)
                saveNotes(arr)
                JSONObject().put("created", true).put("note", entry)
            }
            "notes_update" -> {
                val id = args.optString("id", "").trim()
                var updatedEntry: JSONObject? = null
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (obj.optString("id") == id) {
                        if (args.has("title")) obj.put("title", args.optString("title"))
                        if (args.has("text")) obj.put("text", args.optString("text"))
                        if (args.has("done")) obj.put("done", args.optBoolean("done"))
                        obj.put("updatedAt", System.currentTimeMillis())
                        updatedEntry = obj
                        break
                    }
                }
                if (updatedEntry == null) {
                    JSONObject().put("error", "Note '$id' not found")
                } else {
                    saveNotes(arr)
                    JSONObject().put("updated", true).put("note", updatedEntry)
                }
            }
            "notes_delete" -> {
                val id = args.optString("id", "").trim()
                val next = JSONArray()
                var removed = false
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (obj.optString("id") == id) {
                        removed = true
                    } else {
                        next.put(obj)
                    }
                }
                if (removed) saveNotes(next)
                JSONObject().put("deleted", removed).put("id", id)
            }
            else -> JSONObject().put("error", "Unknown notes tool: $name")
        }
    }
}
