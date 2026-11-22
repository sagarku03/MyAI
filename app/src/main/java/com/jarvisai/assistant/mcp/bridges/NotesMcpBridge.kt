package com.jarvisai.assistant.mcp.bridges

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class NoteKind(val wire: String) {
    NOTE("note"),
    TODO("todo");

    companion object {
        fun fromWire(value: String?): NoteKind =
            if (value.equals("todo", ignoreCase = true)) TODO else NOTE
    }
}

data class TodoEntry(
    val id: String = UUID.randomUUID().toString().take(8),
    val text: String = "",
    val done: Boolean = false
)

data class NoteItem(
    val id: String,
    val kind: NoteKind,
    val title: String,
    val text: String,
    val items: List<TodoEntry> = emptyList(),
    val pinned: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val doneCount: Int get() = items.count { it.done }
}

/**
 * notes_mcp: Persistent on-device Notes & To-Do lists MCP server exposed via AndroidLocalBridgeServer (/notes)
 * and observed live by NotesScreen.
 */
object NotesMcpBridge {

    @Volatile
    private var appContext: Context? = null

    private val _notes = MutableStateFlow<List<NoteItem>>(emptyList())
    val notes: StateFlow<List<NoteItem>> = _notes.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        refresh()
    }

    private fun notesFile(): File? {
        val ctx = appContext ?: return null
        val target = File(ctx.filesDir, "agentra_mcp_notes.json")
        if (!target.exists()) {
            val legacy = File(ctx.filesDir, "jarvis_mcp_notes.json")
            if (legacy.exists()) {
                runCatching {
                    if (!legacy.renameTo(target)) {
                        legacy.copyTo(target, overwrite = false)
                        legacy.delete()
                    }
                }
            }
        }
        return target
    }

    @Synchronized
    fun refresh() {
        val arr = loadNotesJson()
        _notes.value = parseNoteItems(arr)
    }

    @Synchronized
    private fun loadNotesJson(): JSONArray {
        val file = notesFile() ?: return JSONArray()
        if (!file.exists()) return JSONArray()
        return runCatching { JSONArray(file.readText()) }.getOrElse { JSONArray() }
    }

    @Synchronized
    private fun saveNotesJson(arr: JSONArray) {
        val file = notesFile() ?: return
        file.parentFile?.mkdirs()
        file.writeText(arr.toString(2))
        _notes.value = parseNoteItems(arr)
    }

    private fun parseNoteItems(arr: JSONArray): List<NoteItem> {
        val list = mutableListOf<NoteItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id").ifBlank { UUID.randomUUID().toString().take(8) }
            val kind = NoteKind.fromWire(obj.optString("kind", "note"))
            val title = obj.optString("title", "")
            val text = obj.optString("text", "")
            val pinned = obj.optBoolean("pinned", false)
            val updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            val rawItems = obj.optJSONArray("items")
            val entries = mutableListOf<TodoEntry>()
            if (rawItems != null) {
                for (j in 0 until rawItems.length()) {
                    val item = rawItems.opt(j)
                    when (item) {
                        is JSONObject -> entries.add(
                            TodoEntry(
                                id = item.optString("id").ifBlank { "${id}_$j" },
                                text = item.optString("text", ""),
                                done = item.optBoolean("done", false)
                            )
                        )
                        is String -> entries.add(
                            TodoEntry(id = "${id}_$j", text = item, done = false)
                        )
                    }
                }
            }
            list.add(
                NoteItem(
                    id = id,
                    kind = kind,
                    title = title,
                    text = text,
                    items = entries,
                    pinned = pinned,
                    updatedAt = updatedAt
                )
            )
        }
        return list.sortedWith(compareByDescending<NoteItem> { it.pinned }.thenByDescending { it.updatedAt })
    }

    private fun toJsonObject(note: NoteItem): JSONObject = JSONObject().apply {
        put("id", note.id)
        put("kind", note.kind.wire)
        put("title", note.title)
        put("text", note.text)
        put("pinned", note.pinned)
        put("updatedAt", note.updatedAt)
        put(
            "items",
            JSONArray().apply {
                note.items.forEach { entry ->
                    put(
                        JSONObject()
                            .put("id", entry.id)
                            .put("text", entry.text)
                            .put("done", entry.done)
                    )
                }
            }
        )
    }

    @Synchronized
    private fun saveTypedList(items: List<NoteItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(toJsonObject(it)) }
        saveNotesJson(arr)
    }

    @Synchronized
    fun getNote(id: String): NoteItem? = _notes.value.firstOrNull { it.id == id }

    @Synchronized
    fun createNote(
        kind: NoteKind = NoteKind.NOTE,
        title: String = "",
        text: String = "",
        items: List<TodoEntry> = if (kind == NoteKind.TODO) listOf(TodoEntry()) else emptyList()
    ): NoteItem {
        val current = parseNoteItems(loadNotesJson()).toMutableList()
        val note = NoteItem(
            id = UUID.randomUUID().toString().take(8),
            kind = kind,
            title = title,
            text = text,
            items = items,
            pinned = false,
            updatedAt = System.currentTimeMillis()
        )
        current.add(0, note)
        saveTypedList(current)
        return note
    }

    @Synchronized
    fun updateNote(id: String, transform: (NoteItem) -> NoteItem): NoteItem? {
        val current = parseNoteItems(loadNotesJson()).toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx == -1) return null
        val updated = transform(current[idx]).copy(updatedAt = System.currentTimeMillis())
        current[idx] = updated
        saveTypedList(current)
        return updated
    }

    @Synchronized
    fun togglePin(id: String): NoteItem? = updateNote(id) { it.copy(pinned = !it.pinned) }

    @Synchronized
    fun toggleTodoItem(noteId: String, itemId: String): NoteItem? = updateNote(noteId) { note ->
        note.copy(
            items = note.items.map { item ->
                if (item.id == itemId) item.copy(done = !item.done) else item
            }
        )
    }

    @Synchronized
    fun deleteNote(id: String): Boolean {
        val current = parseNoteItems(loadNotesJson())
        val next = current.filterNot { it.id == id }
        if (next.size == current.size) return false
        saveTypedList(next)
        return true
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
                put("description", "Update a note's title, text, pinned status, or to-do items.")
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
                                .put("pinned", JSONObject().put("type", "boolean"))
                                .put("done", JSONObject().put("type", "boolean"))
                                .put("items", JSONObject().put("type", "array").put("items", JSONObject().put("type", "object")))
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
        val arr = loadNotesJson()
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
                            if (!itemVal.has("id")) itemVal.put("id", UUID.randomUUID().toString().take(8))
                            itemsArray.put(itemVal)
                        } else if (itemVal != null) {
                            itemsArray.put(
                                JSONObject()
                                    .put("id", UUID.randomUUID().toString().take(8))
                                    .put("text", itemVal.toString())
                                    .put("done", false)
                            )
                        }
                    }
                }
                val entry = JSONObject().apply {
                    put("id", UUID.randomUUID().toString().take(8))
                    put("kind", kind)
                    put("title", title)
                    put("text", text)
                    put("pinned", args.optBoolean("pinned", false))
                    put("items", itemsArray)
                    put("updatedAt", System.currentTimeMillis())
                }
                arr.put(entry)
                saveNotesJson(arr)
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
                        if (args.has("pinned")) obj.put("pinned", args.optBoolean("pinned"))
                        if (args.has("done")) obj.put("done", args.optBoolean("done"))
                        if (args.has("items")) {
                            val nextItems = args.optJSONArray("items")
                            if (nextItems != null) obj.put("items", nextItems)
                        }
                        obj.put("updatedAt", System.currentTimeMillis())
                        updatedEntry = obj
                        break
                    }
                }
                if (updatedEntry == null) {
                    JSONObject().put("error", "Note '$id' not found")
                } else {
                    saveNotesJson(arr)
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
                if (removed) saveNotesJson(next)
                JSONObject().put("deleted", removed).put("id", id)
            }
            else -> JSONObject().put("error", "Unknown notes tool: $name")
        }
    }
}
