package com.jarvisai.assistant.data.remote

import android.content.Context
import android.util.Log
import com.jarvisai.assistant.agent.AgentOrchestrator
import com.jarvisai.assistant.browser.BrowserSessionManager
import com.jarvisai.assistant.ide.ActiveCodeEditorRegistry
import com.jarvisai.assistant.mcp.bridges.AndroidDeviceMcpBridge
import com.jarvisai.assistant.mcp.bridges.BrowserMcpBridge
import com.jarvisai.assistant.mcp.bridges.JarvisMemoryMcpBridge
import com.jarvisai.assistant.model.ChoiceQuestionnaire
import com.jarvisai.assistant.model.ChoiceResponseItem
import com.jarvisai.assistant.model.QuestionnaireOption
import com.jarvisai.assistant.model.QuestionnaireQuestion
import com.jarvisai.assistant.model.ToolCall
import com.jarvisai.assistant.model.ToolCallStatus
import com.jarvisai.assistant.service.PrivateAgentScreenOperator
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.LocalTerminalBridge
import com.jarvisai.assistant.terminal.WorkspaceSyncManager
import com.jarvisai.assistant.termux.JarvisTermuxBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Full-Power On-Device Tool Suite for Antigravity:
 * Combines Linux/Termux live-streaming execution, workspace & AST-like code search,
 * multi-chunk atomic edits, Sora IDE editor sync, interactive `ask_question`,
 * live Web Search/Fetch, Embedded Browser CDP MCP, Accessibility Screen Operator,
 * 5-Tier Memory Blackboard, and Background Subagent dispatch.
 */
object AntigravityToolSuite {

    private const val TAG = "AntigravityToolSuite"

    private val webClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val pendingQuestions = ConcurrentHashMap<Int, CompletableDeferred<String>>()

    fun resolvePendingQuestion(
        stepIndex: Int,
        responses: List<ChoiceResponseItem>,
        cancelled: Boolean
    ): Boolean {
        val deferred = pendingQuestions.remove(stepIndex)
            ?: pendingQuestions.entries.firstOrNull()?.let { pendingQuestions.remove(it.key) }
            ?: return false

        val payload = JSONObject().apply {
            put("cancelled", cancelled)
            val arr = JSONArray()
            responses.forEach { r ->
                arr.put(
                    JSONObject()
                        .put("question", r.question)
                        .put("selectedOptionIds", JSONArray(r.selectedOptionIds))
                        .put("writeInResponse", r.writeInResponse.orEmpty())
                )
            }
            put("responses", arr)
        }.toString()
        deferred.complete(payload)
        return true
    }

    fun cancelAllPendingQuestions() {
        val keys = pendingQuestions.keys.toList()
        keys.forEach { key ->
            pendingQuestions.remove(key)?.complete(
                JSONObject().put("cancelled", true).toString()
            )
        }
    }

    fun buildToolsDeclaration(): JSONArray {
        val decls = JSONArray()

        fun addTool(name: String, description: String, properties: JSONObject, required: List<String> = emptyList()) {
            decls.put(
                JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put(
                        "parameters",
                        JSONObject()
                            .put("type", "OBJECT")
                            .put("properties", properties)
                            .apply {
                                if (required.isNotEmpty()) put("required", JSONArray(required))
                            }
                    )
            )
        }

        addTool(
            name = "run_command",
            description = "Execute a bash/shell command directly on the user's Android Linux/Termux engine (Node.js, Python, Git, builds, tests) with live streaming output.",
            properties = JSONObject()
                .put("command", JSONObject().put("type", "STRING").put("description", "Exact shell command to execute."))
                .put("working_dir", JSONObject().put("type", "STRING").put("description", "Optional working directory path; defaults to active IDE project or Linux home."))
                .put("target_external_termux", JSONObject().put("type", "BOOLEAN").put("description", "Set true ONLY when external com.termux app execution is explicitly needed and permitted.")),
            required = listOf("command")
        )

        addTool(
            name = "read_file",
            description = "Read file contents (with optional line range) from workspace or device storage.",
            properties = JSONObject()
                .put("path", JSONObject().put("type", "STRING").put("description", "Absolute or home-relative file path."))
                .put("start_line", JSONObject().put("type", "INTEGER").put("description", "1-indexed start line."))
                .put("end_line", JSONObject().put("type", "INTEGER").put("description", "1-indexed end line.")),
            required = listOf("path")
        )

        addTool(
            name = "write_to_file",
            description = "Create or overwrite a file in the workspace and immediately notify the Sora Code IDE.",
            properties = JSONObject()
                .put("path", JSONObject().put("type", "STRING").put("description", "File path to write."))
                .put("content", JSONObject().put("type", "STRING").put("description", "Complete file contents.")),
            required = listOf("path", "content")
        )

        addTool(
            name = "replace_file_content",
            description = "Replace an exact text block in an existing file and notify the Sora Code IDE.",
            properties = JSONObject()
                .put("path", JSONObject().put("type", "STRING").put("description", "Target file path."))
                .put("target_content", JSONObject().put("type", "STRING").put("description", "Exact text to replace."))
                .put("replacement_content", JSONObject().put("type", "STRING").put("description", "Replacement text.")),
            required = listOf("path", "target_content", "replacement_content")
        )

        addTool(
            name = "multi_replace_file_content",
            description = "Apply multiple non-contiguous exact replacements cleanly in a single file atomically.",
            properties = JSONObject()
                .put("path", JSONObject().put("type", "STRING").put("description", "Target file path."))
                .put(
                    "edits",
                    JSONObject()
                        .put("type", "ARRAY")
                        .put("description", "Ordered list of {target_content, replacement_content} edits.")
                        .put(
                            "items",
                            JSONObject()
                                .put("type", "OBJECT")
                                .put(
                                    "properties",
                                    JSONObject()
                                        .put("target_content", JSONObject().put("type", "STRING"))
                                        .put("replacement_content", JSONObject().put("type", "STRING"))
                                )
                                .put("required", JSONArray(listOf("target_content", "replacement_content")))
                        )
                ),
            required = listOf("path", "edits")
        )

        addTool(
            name = "list_dir",
            description = "List files and subdirectories inside a directory.",
            properties = JSONObject()
                .put("path", JSONObject().put("type", "STRING").put("description", "Directory path (defaults to active project or home)."))
        )

        addTool(
            name = "grep_search",
            description = "Search recursively for a text pattern inside files across a workspace directory.",
            properties = JSONObject()
                .put("pattern", JSONObject().put("type", "STRING").put("description", "Text or substring pattern to search."))
                .put("path", JSONObject().put("type", "STRING").put("description", "Root directory or file path.")),
            required = listOf("pattern")
        )

        addTool(
            name = "find_by_name",
            description = "Search recursively for files or directories matching a filename substring/glob pattern.",
            properties = JSONObject()
                .put("pattern", JSONObject().put("type", "STRING").put("description", "Filename substring or extension (e.g. '.kt', 'package.json')."))
                .put("path", JSONObject().put("type", "STRING").put("description", "Search root directory.")),
            required = listOf("pattern")
        )

        addTool(
            name = "read_active_editor",
            description = "Inspect the active file path, unsaved buffer, dirty state, and open project directory currently focused in Jarvis's Sora Code IDE.",
            properties = JSONObject()
        )

        addTool(
            name = "open_file_in_ide",
            description = "Open or focus a file in Jarvis's embedded Sora Code IDE so the user can inspect edits immediately.",
            properties = JSONObject()
                .put("path", JSONObject().put("type", "STRING").put("description", "File path to open in IDE.")),
            required = listOf("path")
        )

        addTool(
            name = "ask_question",
            description = "Render an interactive multiple-choice or single-choice questionnaire card in the chat UI and wait for the user's answer before proceeding.",
            properties = JSONObject()
                .put("title", JSONObject().put("type", "STRING").put("description", "Short card header."))
                .put(
                    "questions",
                    JSONObject()
                        .put("type", "ARRAY")
                        .put("description", "List of clarifying questions with options.")
                        .put(
                            "items",
                            JSONObject()
                                .put("type", "OBJECT")
                                .put(
                                    "properties",
                                    JSONObject()
                                        .put("question", JSONObject().put("type", "STRING"))
                                        .put("is_multi_select", JSONObject().put("type", "BOOLEAN"))
                                        .put(
                                            "options",
                                            JSONObject()
                                                .put("type", "ARRAY")
                                                .put("items", JSONObject().put("type", "STRING"))
                                        )
                                )
                                .put("required", JSONArray(listOf("question", "options")))
                        )
                ),
            required = listOf("questions")
        )

        addTool(
            name = "web_search",
            description = "Search the live web for documentation, APIs, errors, or current facts.",
            properties = JSONObject()
                .put("query", JSONObject().put("type", "STRING").put("description", "Search query.")),
            required = listOf("query")
        )

        addTool(
            name = "fetch_url",
            description = "Fetch readable text content from a webpage or documentation URL.",
            properties = JSONObject()
                .put("url", JSONObject().put("type", "STRING").put("description", "HTTP/HTTPS URL to read.")),
            required = listOf("url")
        )

        addTool(
            name = "browser_mcp_tool",
            description = "Control Agentra's visible built-in WebView CDP Browser via MCP (actions: browser_open_url, browser_read_page, browser_find, browser_computer, browser_form_input, browser_get_page_text, browser_screenshot, browser_inspect_dom, browser_eval_js, browser_interact, browser_list_tabs, browser_switch_tab, browser_close_tab, browser_set_view_mode, browser_get_console_logs, browser_lock, browser_batch). URLs opened here (including http://localhost:PORT previews) appear live inside Agentra's in-app browser.",
            properties = JSONObject()
                .put("tool_name", JSONObject().put("type", "STRING").put("description", "MCP browser tool name (e.g. browser_open_url, browser_read_page, browser_computer, browser_screenshot, browser_get_console_logs)."))
                .put("arguments", JSONObject().put("type", "OBJECT").put("description", "JSON arguments for the browser tool.")),
            required = listOf("tool_name")
        )

        addTool(
            name = "android_screen_control",
            description = "Full Android phone control matching Gemini Live Voice: run multi-step autonomous UI goals (execute_goal), inspect UI tree & Set-of-Mark badges (inspect_ui), capture live screenshot (take_screenshot), launch apps (launch_app), click elements by text/badge (click_element), tap/long-press/swipe/scroll/type/focus/clear/press keys, toggle hardware settings (control_hardware: wifi/bluetooth/flashlight/volume/brightness/hotspot/dnd), send WhatsApp/Telegram/SMS (send_message), dial phone (dial_phone), set alarms/timers (set_alarm_or_timer), control media (media_control), open deep links (deep_link), share files to apps (share_file), read/write clipboard, reply to notifications, auto-fill OTP, get system telemetry (system_status), or enable/open Accessibility Settings (open_accessibility_settings).",
            properties = JSONObject()
                .put("action", JSONObject().put("type", "STRING").put("description", "Action: execute_goal, inspect_ui, take_screenshot, launch_app, click_element, tap_label, tap_xy, swipe, scroll, focus_input, type_text, clear_text, press_key, control_hardware, send_message, dial_phone, set_alarm_or_timer, media_control, system_status, deep_link, share_file, get_clipboard, set_clipboard, reply_notification, auto_fill_otp, open_accessibility_settings, stop_task."))
                .put("target", JSONObject().put("type", "STRING").put("description", "Goal description, app name, button label, setting name, deep link URI, file path, scroll direction (UP/DOWN/LEFT/RIGHT), or key (BACK/HOME/RECENTS/NOTIFICATIONS/QUICK_SETTINGS/ENTER)."))
                .put("text", JSONObject().put("type", "STRING").put("description", "Text to type, message body, caption, clipboard text, or hardware state (on/off)."))
                .put("x", JSONObject().put("type", "INTEGER").put("description", "X coordinate for tap_xy."))
                .put("y", JSONObject().put("type", "INTEGER").put("description", "Y coordinate for tap_xy."))
                .put("arguments", JSONObject().put("type", "OBJECT").put("description", "Optional structured JSON arguments forwarded directly to the underlying android_* MCP tool (e.g. badge_id, package_name, recipient, platform, submit, long_press, start_x, start_y, end_x, end_y, mode, hour, minute, duration_seconds).")),
            required = listOf("action")
        )

        addTool(
            name = "jarvis_memory",
            description = "Read or write persistent facts in Jarvis's 5-Tier Working Memory Blackboard.",
            properties = JSONObject()
                .put("operation", JSONObject().put("type", "STRING").put("description", "'read' or 'write'."))
                .put("key", JSONObject().put("type", "STRING").put("description", "Memory key."))
                .put("value", JSONObject().put("type", "STRING").put("description", "Memory value when operation='write'.")),
            required = listOf("operation")
        )

        addTool(
            name = "spawn_subagent",
            description = "Dispatch an external background shell/CLI subagent task ONLY when user explicitly asks for Claude Code CLI / OpenCode / parallel background execution. For creating HTML, CSS, JS, or code files, ALWAYS use write_to_file or edit_file directly instead.",
            properties = JSONObject()
                .put("prompt", JSONObject().put("type", "STRING").put("description", "Clear autonomous task goal for the subagent."))
                .put("working_dir", JSONObject().put("type", "STRING").put("description", "Project directory."))
                .put("agent_type", JSONObject().put("type", "STRING").put("description", "'claude', 'opencode', or 'bash'.")),
            required = listOf("prompt")
        )

        // Include first-class MCP, Plugin/Extension, and Skill authoring & management tools
        val authoringDecls = com.jarvisai.assistant.mcp.bridges.McpEcosystemBridge.listAuthoringAndManagementTools()
        for (i in 0 until authoringDecls.length()) {
            val item = authoringDecls.optJSONObject(i) ?: continue
            val tName = item.optString("name")
            val tDesc = item.optString("description")
            val schema = item.optJSONObject("inputSchema") ?: JSONObject()
            val props = schema.optJSONObject("properties") ?: JSONObject()
            val reqArr = schema.optJSONArray("required")
            val reqList = mutableListOf<String>()
            if (reqArr != null) {
                for (ri in 0 until reqArr.length()) {
                    reqArr.optString(ri).takeIf { it.isNotBlank() }?.let { reqList.add(it) }
                }
            }
            addTool(name = tName, description = tDesc, properties = props, required = reqList)
        }

        // Dynamically include enabled discovered MCP tools from McpServersManager
        com.jarvisai.assistant.mcp.McpServersManager.getDynamicToolDeclarations(null).forEach { dynDecl ->
            decls.put(dynDecl)
        }

        return JSONArray().put(JSONObject().put("functionDeclarations", decls))
    }

    fun buildToolsDeclaration(context: Context?): JSONArray {
        val base = buildToolsDeclaration()
        if (context == null) return base
        val decls = base.optJSONObject(0)?.optJSONArray("functionDeclarations") ?: return base
        val existingNames = mutableSetOf<String>()
        for (i in 0 until decls.length()) {
            decls.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let { existingNames.add(it) }
        }
        com.jarvisai.assistant.mcp.McpServersManager.getDynamicToolDeclarations(context).forEach { dynDecl ->
            val n = dynDecl.optString("name")
            if (n.isNotBlank() && existingNames.add(n)) {
                decls.put(dynDecl)
            }
        }
        return base
    }

    private fun defaultWorkspaceRoot(context: Context): File {
        val activeProj = ActiveCodeEditorRegistry.currentProjectPath?.takeIf { it.isNotBlank() }
        if (activeProj != null) {
            val f = File(activeProj)
            if (f.exists() && f.isDirectory && f.canWrite()) return f
        }
        return LocalEnvironmentManager.getHomeDir(context)
    }

    fun resolveWorkspaceFile(context: Context, rawPath: String): File {
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        val trimmed = rawPath.trim()
        return when {
            trimmed.isBlank() -> defaultWorkspaceRoot(context)
            trimmed == "~" -> homeDir
            trimmed.startsWith("~/") -> File(homeDir, trimmed.removePrefix("~/"))
            trimmed.startsWith("/data/data/com.termux/files/home") && !LocalEnvironmentManager.isTermuxFlavor(context) -> {
                val rel = trimmed.removePrefix("/data/data/com.termux/files/home").removePrefix("/")
                if (rel.isBlank()) homeDir else File(homeDir, rel)
            }
            trimmed.startsWith("/") -> File(trimmed)
            else -> File(defaultWorkspaceRoot(context), trimmed)
        }
    }

    suspend fun FlowCollector<AntigravityStreamEvent>.executeTool(
        context: Context,
        toolId: String,
        stepIndex: Int,
        fnName: String,
        fnArgs: JSONObject
    ): Pair<ToolCall, JSONObject> {
        val startMs = System.currentTimeMillis()
        val displayCmd = when (fnName) {
            "run_command" -> fnArgs.optString("command", "")
            "read_file", "write_to_file", "replace_file_content", "multi_replace_file_content", "list_dir", "open_file_in_ide" ->
                fnArgs.optString("path", "~")
            "grep_search", "find_by_name" -> "${fnArgs.optString("pattern", "")} in ${fnArgs.optString("path", "~").ifBlank { "~" }}"
            "web_search" -> fnArgs.optString("query", "")
            "fetch_url" -> fnArgs.optString("url", "")
            "browser_mcp_tool" -> fnArgs.optString("tool_name", "browser")
            "android_screen_control" -> "${fnArgs.optString("action", "")}: ${fnArgs.optString("target", "")}".trimEnd(':', ' ')
            "jarvis_memory" -> "${fnArgs.optString("operation", "read")} ${fnArgs.optString("key", "")}".trim()
            "spawn_subagent" -> "[${fnArgs.optString("agent_type", "claude")}] ${fnArgs.optString("prompt", "").take(90)}"
            "ask_question" -> fnArgs.optString("title", "Clarifying questions")
            "read_active_editor" -> "Inspect active Sora IDE buffer"
            "mcp_list_servers" -> "List active MCP servers & tools"
            "mcp_call_tool" -> "${fnArgs.optString("server_name", "")}/${fnArgs.optString("tool_name", "")}"
            "mcp_create_server" -> "Create MCP server: ${fnArgs.optString("server_name", "")}"
            "mcp_remove_server" -> "Remove MCP server: ${fnArgs.optString("server_name", "")}"
            "skill_list_all" -> "List installed Agent Skills"
            "skill_create_or_update" -> "Create/Update Skill: ${fnArgs.optString("name", "")}"
            "skill_delete" -> "Delete Skill: ${fnArgs.optString("name", "")}"
            "plugin_list_all" -> "List installed Plugins & Browser Extensions"
            "plugin_create_or_update" -> "Create/Update Plugin & Extension: ${fnArgs.optString("name", fnArgs.optString("id", ""))}"
            else -> fnArgs.toString()
        }

        val initialTool = ToolCall(
            id = toolId,
            stepIndex = stepIndex,
            name = fnName,
            command = displayCmd,
            args = fnArgs.toString(),
            output = "Executing...",
            status = ToolCallStatus.RUNNING
        )
        emit(AntigravityStreamEvent.ToolCallUpdate(initialTool))

        return try {
            when (fnName) {
                "run_command" -> executeRunCommandStreaming(context, initialTool, fnArgs, startMs)

                "read_file" -> {
                    val file = resolveWorkspaceFile(context, fnArgs.optString("path", ""))
                    if (!file.exists() || !file.isFile) {
                        return failTool(initialTool, "File not found: ${file.absolutePath}", startMs)
                    }
                    val allLines = file.readLines()
                    val startLine = fnArgs.optInt("start_line", 1).coerceAtLeast(1)
                    val endLine = fnArgs.optInt("end_line", allLines.size).coerceAtMost(allLines.size).coerceAtLeast(startLine)
                    val slice = if (allLines.isEmpty()) "(empty file)" else {
                        allLines.subList(startLine - 1, endLine).mapIndexed { idx, l ->
                            "${startLine + idx}: $l"
                        }.joinToString("\n")
                    }
                    val done = initialTool.copy(
                        status = ToolCallStatus.SUCCESS,
                        output = slice.take(10000),
                        exitCode = 0,
                        durationMs = System.currentTimeMillis() - startMs
                    )
                    emit(AntigravityStreamEvent.ToolCallUpdate(done))
                    done to JSONObject()
                        .put("path", file.absolutePath)
                        .put("totalLines", allLines.size)
                        .put("content", slice.take(12000))
                }

                "write_to_file" -> {
                    val file = resolveWorkspaceFile(context, fnArgs.optString("path", ""))
                    val content = fnArgs.optString("content", "")
                    file.parentFile?.mkdirs()
                    file.writeText(content)
                    WorkspaceSyncManager.notifyExternalChange(file.absolutePath)
                    val summary = "Wrote ${content.length} chars (${content.lines().size} lines) to ${file.absolutePath}"
                    val done = initialTool.copy(
                        status = ToolCallStatus.SUCCESS,
                        output = summary,
                        exitCode = 0,
                        durationMs = System.currentTimeMillis() - startMs
                    )
                    emit(AntigravityStreamEvent.ToolCallUpdate(done))
                    done to JSONObject().put("success", true).put("path", file.absolutePath).put("bytes", content.length)
                }

                "replace_file_content" -> {
                    val file = resolveWorkspaceFile(context, fnArgs.optString("path", ""))
                    val targetContent = fnArgs.optString("target_content", "")
                    val replacement = fnArgs.optString("replacement_content", "")
                    if (!file.exists() || !file.isFile) {
                        return failTool(initialTool, "File not found: ${file.absolutePath}", startMs)
                    }
                    val current = file.readText()
                    if (!current.contains(targetContent)) {
                        return failTool(initialTool, "target_content not found in ${file.absolutePath}", startMs)
                    }
                    file.writeText(current.replaceFirst(targetContent, replacement))
                    WorkspaceSyncManager.notifyExternalChange(file.absolutePath)
                    val diffPreview = buildString {
                        targetContent.lines().take(8).forEach { appendLine("- $it") }
                        replacement.lines().take(8).forEach { appendLine("+ $it") }
                    }.trim()
                    val done = initialTool.copy(
                        status = ToolCallStatus.SUCCESS,
                        output = diffPreview,
                        exitCode = 0,
                        durationMs = System.currentTimeMillis() - startMs
                    )
                    emit(AntigravityStreamEvent.ToolCallUpdate(done))
                    done to JSONObject().put("success", true).put("path", file.absolutePath).put("diff", diffPreview)
                }

                "multi_replace_file_content" -> {
                    val file = resolveWorkspaceFile(context, fnArgs.optString("path", ""))
                    val edits = fnArgs.optJSONArray("edits") ?: JSONArray()
                    if (!file.exists() || !file.isFile) {
                        return failTool(initialTool, "File not found: ${file.absolutePath}", startMs)
                    }
                    var updated = file.readText()
                    val appliedDiff = StringBuilder()
                    for (i in 0 until edits.length()) {
                        val edit = edits.optJSONObject(i) ?: continue
                        val target = edit.optString("target_content", "")
                        val replacement = edit.optString("replacement_content", "")
                        if (target.isEmpty() || !updated.contains(target)) {
                            return failTool(initialTool, "Edit #${i + 1} target_content not found in ${file.absolutePath}", startMs)
                        }
                        updated = updated.replaceFirst(target, replacement)
                        target.lines().take(3).forEach { appliedDiff.appendLine("- $it") }
                        replacement.lines().take(3).forEach { appliedDiff.appendLine("+ $it") }
                    }
                    file.writeText(updated)
                    WorkspaceSyncManager.notifyExternalChange(file.absolutePath)
                    val diffStr = appliedDiff.toString().trim().ifBlank { "Applied ${edits.length()} edits." }
                    val done = initialTool.copy(
                        status = ToolCallStatus.SUCCESS,
                        output = diffStr,
                        exitCode = 0,
                        durationMs = System.currentTimeMillis() - startMs
                    )
                    emit(AntigravityStreamEvent.ToolCallUpdate(done))
                    done to JSONObject().put("success", true).put("path", file.absolutePath).put("editsApplied", edits.length())
                }

                "list_dir" -> {
                    val dir = resolveWorkspaceFile(context, fnArgs.optString("path", ""))
                    if (!dir.exists() || !dir.isDirectory) {
                        return failTool(initialTool, "Directory not found: ${dir.absolutePath}", startMs)
                    }
                    val listing = (dir.listFiles() ?: emptyArray())
                        .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                        .take(150)
                        .joinToString("\n") { f ->
                            if (f.isDirectory) "[DIR]  ${f.name}/" else "[FILE] ${f.name} (${f.length()} B)"
                        }.ifBlank { "(empty directory)" }
                    val done = initialTool.copy(
                        status = ToolCallStatus.SUCCESS,
                        output = listing,
                        exitCode = 0,
                        durationMs = System.currentTimeMillis() - startMs
                    )
                    emit(AntigravityStreamEvent.ToolCallUpdate(done))
                    done to JSONObject().put("path", dir.absolutePath).put("entries", listing)
                }

                "grep_search" -> {
                    val pattern = fnArgs.optString("pattern", "")
                    val root = resolveWorkspaceFile(context, fnArgs.optString("path", ""))
                    val matches = mutableListOf<String>()
                    fun walk(f: File) {
                        if (matches.size >= 100) return
                        if (f.isDirectory) {
                            if (f.name in setOf(".git", "node_modules", "build", ".gradle")) return
                            f.listFiles()?.forEach { walk(it) }
                        } else if (f.isFile && f.length() < 512_000) {
                            runCatching {
                                f.useLines { seq ->
                                    seq.forEachIndexed { idx, line ->
                                        if (matches.size < 100 && line.contains(pattern, ignoreCase = true)) {
                                            val rel = runCatching { f.relativeTo(root).path }.getOrDefault(f.name)
                                            matches.add("$rel:${idx + 1}: ${line.trim().take(160)}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (root.exists()) walk(root)
                    val out = if (matches.isEmpty()) "No matches for '$pattern' in ${root.absolutePath}" else matches.joinToString("\n")
                    val done = initialTool.copy(status = ToolCallStatus.SUCCESS, output = out, exitCode = 0, durationMs = System.currentTimeMillis() - startMs)
                    emit(AntigravityStreamEvent.ToolCallUpdate(done))
                    done to JSONObject().put("matches", out)
                }

                "find_by_name" -> {
                    val pattern = fnArgs.optString("pattern", "").lowercase()
                    val root = resolveWorkspaceFile(context, fnArgs.optString("path", ""))
                    val found = mutableListOf<String>()
                    fun walk(f: File) {
                        if (found.size >= 120) return
                        if (f.name in setOf(".git", "node_modules", "build", ".gradle")) return
                        if (f.name.lowercase().contains(pattern)) {
                            found.add(f.absolutePath)
                        }
                        if (f.isDirectory) {
                            f.listFiles()?.forEach { walk(it) }
                        }
                    }
                    if (root.exists()) walk(root)
                    val out = if (found.isEmpty()) "No files matching '$pattern' in ${root.absolutePath}" else found.joinToString("\n")
                    val done = initialTool.copy(status = ToolCallStatus.SUCCESS, output = out, exitCode = 0, durationMs = System.currentTimeMillis() - startMs)
                    emit(AntigravityStreamEvent.ToolCallUpdate(done))
                    done to JSONObject().put("files", JSONArray(found)).put("output", out)
                }

                "read_active_editor" -> {
                    val info = JSONObject().apply {
                        put("activeFilePath", ActiveCodeEditorRegistry.activeFilePath.orEmpty())
                        put("activeFileTitle", ActiveCodeEditorRegistry.activeFileTitle.orEmpty())
                        put("isDirty", ActiveCodeEditorRegistry.isDirty)
                        put("currentProjectPath", ActiveCodeEditorRegistry.currentProjectPath ?: defaultWorkspaceRoot(context).absolutePath)
                        put("content", ActiveCodeEditorRegistry.activeContent.take(12000))
                    }
                    val summary = if (!ActiveCodeEditorRegistry.activeFilePath.isNullOrBlank()) {
                        "Active IDE file: ${ActiveCodeEditorRegistry.activeFilePath} (${ActiveCodeEditorRegistry.activeContent.lines().size} lines)"
                    } else {
                        "Project root: ${ActiveCodeEditorRegistry.currentProjectPath ?: defaultWorkspaceRoot(context).absolutePath} (no active tab open)"
                    }
                    val done = initialTool.copy(status = ToolCallStatus.SUCCESS, output = summary, exitCode = 0, durationMs = System.currentTimeMillis() - startMs)
                    emit(AntigravityStreamEvent.ToolCallUpdate(done))
                    done to info
                }

                "open_file_in_ide" -> {
                    val file = resolveWorkspaceFile(context, fnArgs.optString("path", ""))
                    if (!file.exists()) {
                        return failTool(initialTool, "File not found: ${file.absolutePath}", startMs)
                    }
                    WorkspaceSyncManager.requestOpenFileInIde(file.absolutePath)
                    val msg = "Opened ${file.absolutePath} in Sora Code IDE."
                    val done = initialTool.copy(status = ToolCallStatus.SUCCESS, output = msg, exitCode = 0, durationMs = System.currentTimeMillis() - startMs)
                    emit(AntigravityStreamEvent.ToolCallUpdate(done))
                    done to JSONObject().put("opened", true).put("path", file.absolutePath)
                }

                "ask_question" -> executeAskQuestionInteractive(initialTool, stepIndex, fnArgs, startMs)

                "web_search" -> executeWebSearch(initialTool, fnArgs.optString("query", ""), startMs)

                "fetch_url" -> executeFetchUrl(initialTool, fnArgs.optString("url", ""), startMs)

                "browser_mcp_tool" -> {
                    val mcpTool = fnArgs.optString("tool_name", "").trim()
                    val args = fnArgs.optJSONObject("arguments") ?: JSONObject()
                    BrowserSessionManager.instance.init(context.applicationContext)
                    val res = BrowserMcpBridge.callTool(mcpTool, args)
                    val out = res.toString(2).take(10000)
                    val done = initialTool.copy(
                        status = if (res.has("error")) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS,
                        output = out,
                        exitCode = if (res.has("error")) 1 else 0,
                        durationMs = System.currentTimeMillis() - startMs
                    )
                    emit(AntigravityStreamEvent.ToolCallUpdate(done))
                    done to res
                }

                "android_screen_control" -> executeAndroidScreenControl(context, initialTool, fnArgs, startMs)

                "jarvis_memory" -> {
                    val op = fnArgs.optString("operation", "read").lowercase()
                    val bridgeTool = if (op == "write") "jarvis_memory_write" else "jarvis_memory_read"
                    val res = JarvisMemoryMcpBridge.callTool(bridgeTool, fnArgs)
                    val out = res.toString(2)
                    val done = initialTool.copy(status = ToolCallStatus.SUCCESS, output = out, exitCode = 0, durationMs = System.currentTimeMillis() - startMs)
                    emit(AntigravityStreamEvent.ToolCallUpdate(done))
                    done to res
                }

                "spawn_subagent" -> {
                    val prompt = fnArgs.optString("prompt", "").trim()
                    val rawDir = fnArgs.optString("working_dir", "").trim()
                    val dir = LocalEnvironmentManager.resolveWritableWorkingDir(
                        context,
                        rawDir.ifBlank { defaultWorkspaceRoot(context).absolutePath }
                    ).absolutePath
                    val agentType = fnArgs.optString("agent_type", "claude").trim()
                    if (prompt.isBlank()) {
                        return failTool(initialTool, "Subagent prompt is empty.", startMs)
                    }
                    AgentOrchestrator.init(context.applicationContext)
                    val task = AgentOrchestrator.dispatchTask(prompt = prompt, workingDirectory = dir, agentType = agentType)
                    val summary = "Dispatched subagent [${task.id}] ($agentType) in $dir"
                    val done = initialTool.copy(status = ToolCallStatus.SUCCESS, output = summary, exitCode = 0, durationMs = System.currentTimeMillis() - startMs)
                    emit(AntigravityStreamEvent.ToolCallUpdate(done))
                    done to JSONObject()
                        .put("taskId", task.id)
                        .put("agentType", agentType)
                        .put("workingDir", dir)
                        .put("status", task.status.name)
                }

                "mcp_list_servers",
                "mcp_call_tool",
                "mcp_create_server",
                "mcp_remove_server",
                "skill_list_all",
                "skill_create_or_update",
                "skill_delete",
                "plugin_list_all",
                "plugin_create_or_update" -> {
                    com.jarvisai.assistant.mcp.bridges.McpEcosystemBridge.init(context.applicationContext)
                    val res = com.jarvisai.assistant.mcp.bridges.McpEcosystemBridge.callAuthoringOrManagementTool(fnName, fnArgs)
                        ?: JSONObject().put("error", "Tool $fnName returned null")
                    val hasErr = res.has("error")
                    val out = res.toString(2).take(10000)
                    val done = initialTool.copy(
                        status = if (hasErr) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS,
                        output = out,
                        exitCode = if (hasErr) 1 else 0,
                        durationMs = System.currentTimeMillis() - startMs
                    )
                    emit(AntigravityStreamEvent.ToolCallUpdate(done))
                    done to res
                }

                else -> {
                    if (fnName.startsWith("mcp__")) {
                        executeDynamicMcpTool(context, initialTool, fnName, fnArgs, startMs)
                    } else {
                        failTool(initialTool, "Unsupported tool: $fnName", startMs)
                    }
                }
            }
        } catch (e: Exception) {
            failTool(initialTool, "Execution error: ${e.message ?: "unknown"}", startMs)
        }
    }

    private suspend fun FlowCollector<AntigravityStreamEvent>.executeRunCommandStreaming(
        context: Context,
        initialTool: ToolCall,
        fnArgs: JSONObject,
        startMs: Long
    ): Pair<ToolCall, JSONObject> {
        val cmd = fnArgs.optString("command", "").trim()
        val rawWorkingDir = fnArgs.optString("working_dir", "").trim()
        val workingDir = LocalEnvironmentManager.resolveWritableWorkingDir(
            context,
            rawWorkingDir.ifBlank { defaultWorkspaceRoot(context).absolutePath }
        ).absolutePath
        val targetExternalTermux = fnArgs.optBoolean("target_external_termux", false)

        if (cmd.isBlank()) {
            return failTool(initialTool, "Error: 'command' parameter is empty.", startMs)
        }

        // Enforce Antigravity Commands & Permissions Security Policy
        val decision = com.jarvisai.assistant.agent.CommandsPermissionManager.evaluateShellCommand(context, cmd)
        if (decision == "DENY") {
            return failTool(initialTool, "Blocked by Commands & Permissions policy: '$cmd'", startMs)
        } else if (decision == "ASK") {
            val questionnaire = ChoiceQuestionnaire(
                title = "Command Approval Required",
                questions = listOf(
                    QuestionnaireQuestion(
                        question = "Allow executing shell command?\n`$cmd`",
                        options = listOf(
                            QuestionnaireOption(id = "allow_once", label = "Allow Once"),
                            QuestionnaireOption(id = "always_allow", label = "Always Allow This Command"),
                            QuestionnaireOption(id = "deny", label = "Deny")
                        ),
                        isMultiSelect = false
                    )
                )
            )
            val waiting = initialTool.copy(
                status = ToolCallStatus.PENDING_APPROVAL,
                output = "Waiting for user approval...",
                questionnaire = questionnaire
            )
            emit(AntigravityStreamEvent.ToolCallUpdate(waiting))
            val deferred = CompletableDeferred<String>()
            pendingQuestions[initialTool.stepIndex] = deferred
            val approvalRaw = try { deferred.await() } finally { pendingQuestions.remove(initialTool.stepIndex) }
            val approvalJson = runCatching { JSONObject(approvalRaw) }.getOrNull()
            val cancelled = approvalJson?.optBoolean("cancelled", false) == true
            val selected = approvalJson?.optJSONArray("responses")?.optJSONObject(0)
                ?.optJSONArray("selectedOptionIds")?.optString(0).orEmpty()
            if (cancelled || selected == "deny" || selected.isBlank()) {
                return failTool(initialTool, "Command denied by user.", startMs)
            }
            if (selected == "always_allow") {
                com.jarvisai.assistant.agent.CommandsPermissionManager.addOrUpdateRule(context, "bash", cmd, "ALLOW")
            }
        }

        val termuxBridge = JarvisTermuxBridge(context)
        if (targetExternalTermux && termuxBridge.isTermuxInstalled() && termuxBridge.hasRunCommandPermission()) {
            val termuxRes = termuxBridge.executeCommand(cmd, background = true, timeoutSeconds = 60)
            val combined = buildString {
                if (termuxRes.stdout.isNotBlank()) appendLine(termuxRes.stdout)
                if (termuxRes.stderr.isNotBlank()) appendLine("[ERR] ${termuxRes.stderr}")
            }.trim().ifBlank { "(exit ${termuxRes.exitCode})" }
            val done = initialTool.copy(
                status = if (termuxRes.exitCode == 0) ToolCallStatus.SUCCESS else ToolCallStatus.FAILED,
                output = combined.take(12000),
                exitCode = termuxRes.exitCode,
                durationMs = System.currentTimeMillis() - startMs
            )
            emit(AntigravityStreamEvent.ToolCallUpdate(done))
            return done to JSONObject()
                .put("environment", "com.termux")
                .put("exitCode", termuxRes.exitCode)
                .put("output", combined.take(12000))
        }

        LocalTerminalBridge.instance.init(context.applicationContext)
        val lineChannel = Channel<String>(Channel.UNLIMITED)
        val execRes = coroutineScope {
            val jobDeferred = async(Dispatchers.IO) {
                try {
                    LocalTerminalBridge.instance.executeStreamingCommand(
                        command = cmd,
                        workingDir = workingDir,
                        timeoutMs = 180_000L
                    ) { line ->
                        lineChannel.trySend(line)
                    }
                } finally {
                    lineChannel.close()
                }
            }

            val streamBuf = StringBuilder()
            var lastEmitMs = 0L
            for (line in lineChannel) {
                streamBuf.appendLine(line)
                val now = System.currentTimeMillis()
                if (now - lastEmitMs >= 250L) {
                    lastEmitMs = now
                    emit(
                        AntigravityStreamEvent.ToolCallUpdate(
                            initialTool.copy(
                                output = streamBuf.toString().trimEnd().takeLast(6000),
                                durationMs = now - startMs
                            )
                        )
                    )
                }
            }
            jobDeferred.await()
        }

        WorkspaceSyncManager.notifyExternalChange(workingDir)
        val finalOut = execRes.output.ifBlank { "(exit code ${execRes.exitCode})" }
        val done = initialTool.copy(
            status = if (execRes.exitCode == 0) ToolCallStatus.SUCCESS else ToolCallStatus.FAILED,
            output = finalOut.take(12000),
            exitCode = execRes.exitCode,
            durationMs = execRes.durationMs
        )
        emit(AntigravityStreamEvent.ToolCallUpdate(done))
        return done to JSONObject()
            .put("environment", "jarvis_linux_engine")
            .put("cwd", workingDir)
            .put("exitCode", execRes.exitCode)
            .put("durationMs", execRes.durationMs)
            .put("output", finalOut.take(12000))
    }

    private suspend fun FlowCollector<AntigravityStreamEvent>.executeAskQuestionInteractive(
        initialTool: ToolCall,
        stepIndex: Int,
        fnArgs: JSONObject,
        startMs: Long
    ): Pair<ToolCall, JSONObject> {
        val title = fnArgs.optString("title", "Questions for you")
        val qArr = fnArgs.optJSONArray("questions") ?: JSONArray()
        val questionsList = mutableListOf<QuestionnaireQuestion>()
        for (i in 0 until qArr.length()) {
            val qObj = qArr.optJSONObject(i) ?: continue
            val qText = qObj.optString("question", "").trim()
            val multi = qObj.optBoolean("is_multi_select", false)
            val optsArr = qObj.optJSONArray("options")
            val optsList = mutableListOf<QuestionnaireOption>()
            if (optsArr != null) {
                for (oi in 0 until optsArr.length()) {
                    com.jarvisai.assistant.model.parseQuestionnaireOptionFromAny(optsArr.opt(oi), oi)?.let {
                        optsList.add(it)
                    }
                }
            }
            if (qText.isNotBlank()) {
                questionsList.add(QuestionnaireQuestion(question = qText, options = optsList, isMultiSelect = multi))
            }
        }
        val questionnaire = if (questionsList.isNotEmpty()) ChoiceQuestionnaire(title = title, questions = questionsList) else null
        val waitingTool = initialTool.copy(
            status = ToolCallStatus.PENDING_APPROVAL,
            output = "Waiting for user response...",
            questionnaire = questionnaire
        )
        emit(AntigravityStreamEvent.ToolCallUpdate(waitingTool))

        val deferred = CompletableDeferred<String>()
        pendingQuestions[stepIndex] = deferred
        val responseStr = try {
            deferred.await()
        } finally {
            pendingQuestions.remove(stepIndex)
        }
        val respJson = runCatching { JSONObject(responseStr) }.getOrElse { JSONObject().put("raw", responseStr) }
        val done = waitingTool.copy(
            status = ToolCallStatus.SUCCESS,
            output = respJson.toString(2),
            durationMs = System.currentTimeMillis() - startMs
        )
        emit(AntigravityStreamEvent.ToolCallUpdate(done))
        return done to respJson
    }

    private suspend fun FlowCollector<AntigravityStreamEvent>.executeWebSearch(
        initialTool: ToolCall,
        query: String,
        startMs: Long
    ): Pair<ToolCall, JSONObject> {
        if (query.isBlank()) return failTool(initialTool, "Search query is empty.", startMs)
        val encoded = URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder()
            .url("https://html.duckduckgo.com/html/?q=$encoded")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .get()
            .build()

        return webClient.newCall(req).execute().use { resp ->
            val html = resp.body?.string().orEmpty()
            val linkRegex = Regex("""<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            val snippetRegex = Regex("""<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            val links = linkRegex.findAll(html).toList()
            val snippets = snippetRegex.findAll(html).toList()
            val results = JSONArray()
            val formatted = StringBuilder()

            for (i in 0 until minOf(links.size, 8)) {
                val url = links[i].groupValues[1].replace("&amp;", "&")
                val title = stripHtml(links[i].groupValues[2])
                val snippet = snippets.getOrNull(i)?.let { stripHtml(it.groupValues[1]) }.orEmpty()
                results.put(JSONObject().put("title", title).put("url", url).put("snippet", snippet))
                formatted.appendLine("${i + 1}. $title\n   URL: $url\n   $snippet\n")
            }

            val outputText = formatted.toString().trim().ifBlank { "No web results found for '$query'." }
            val done = initialTool.copy(
                status = ToolCallStatus.SUCCESS,
                output = outputText.take(10000),
                exitCode = 0,
                durationMs = System.currentTimeMillis() - startMs
            )
            emit(AntigravityStreamEvent.ToolCallUpdate(done))
            done to JSONObject().put("query", query).put("results", results).put("summary", outputText.take(10000))
        }
    }

    private suspend fun FlowCollector<AntigravityStreamEvent>.executeFetchUrl(
        initialTool: ToolCall,
        url: String,
        startMs: Long
    ): Pair<ToolCall, JSONObject> {
        if (url.isBlank()) return failTool(initialTool, "URL is empty.", startMs)
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .get()
            .build()

        return webClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val cleanText = stripHtml(raw).take(14000)
            val done = initialTool.copy(
                status = if (resp.isSuccessful) ToolCallStatus.SUCCESS else ToolCallStatus.FAILED,
                output = cleanText.ifBlank { "HTTP ${resp.code}" },
                exitCode = if (resp.isSuccessful) 0 else 1,
                durationMs = System.currentTimeMillis() - startMs
            )
            emit(AntigravityStreamEvent.ToolCallUpdate(done))
            done to JSONObject().put("url", url).put("httpCode", resp.code).put("content", cleanText)
        }
    }

    private suspend fun FlowCollector<AntigravityStreamEvent>.executeAndroidScreenControl(
        context: Context,
        initialTool: ToolCall,
        fnArgs: JSONObject,
        startMs: Long
    ): Pair<ToolCall, JSONObject> {
        AndroidDeviceMcpBridge.init(context.applicationContext)
        val action = fnArgs.optString("action", "inspect_ui").trim().lowercase()
        val target = fnArgs.optString("target", "").trim()
        val text = fnArgs.optString("text", "")
        val extraArgs = fnArgs.optJSONObject("arguments")?.let { JSONObject(it.toString()) } ?: JSONObject()

        val resJson = when (action) {
            "execute_goal", "android_execute_goal" -> {
                if (!extraArgs.has("goal")) extraArgs.put("goal", target.ifBlank { text })
                AndroidDeviceMcpBridge.callTool("android_execute_goal", extraArgs)
            }
            "inspect_ui", "inspect_screen", "android_get_ui_tree", "android_inspect_screen" ->
                AndroidDeviceMcpBridge.callTool("android_inspect_screen", extraArgs)
            "take_screenshot", "screenshot", "android_take_screenshot" ->
                AndroidDeviceMcpBridge.callTool("android_take_screenshot", extraArgs)
            "launch_app", "open_app", "android_launch_app" -> {
                if (!extraArgs.has("appName") && target.isNotBlank()) extraArgs.put("appName", target)
                if (!extraArgs.has("searchQuery") && text.isNotBlank()) extraArgs.put("searchQuery", text)
                AndroidDeviceMcpBridge.callTool("android_launch_app", extraArgs)
            }
            "click_element", "tap_label", "android_click_element" -> {
                if (!extraArgs.has("text") && !extraArgs.has("badge_id") && target.isNotBlank()) {
                    extraArgs.put("text", target)
                }
                AndroidDeviceMcpBridge.callTool("android_click_element", extraArgs)
            }
            "press_key", "android_press_key" -> {
                if (!extraArgs.has("key")) extraArgs.put("key", target.ifBlank { "BACK" })
                AndroidDeviceMcpBridge.callTool("android_press_key", extraArgs)
            }
            "tap_xy", "tap", "android_tap" -> {
                if (!extraArgs.has("x")) extraArgs.put("x", fnArgs.optInt("x", 0))
                if (!extraArgs.has("y")) extraArgs.put("y", fnArgs.optInt("y", 0))
                AndroidDeviceMcpBridge.callTool("android_tap", extraArgs)
            }
            "swipe", "android_swipe" -> {
                if (!extraArgs.has("direction") && !extraArgs.has("start_x") && target.isNotBlank()) {
                    extraArgs.put("direction", target)
                }
                AndroidDeviceMcpBridge.callTool("android_swipe", extraArgs)
            }
            "scroll", "android_scroll" -> {
                if (!extraArgs.has("direction")) extraArgs.put("direction", target.ifBlank { "DOWN" })
                AndroidDeviceMcpBridge.callTool("android_scroll", extraArgs)
            }
            "focus_input", "android_focus_input" -> {
                if (!extraArgs.has("target_label") && target.isNotBlank()) extraArgs.put("target_label", target)
                AndroidDeviceMcpBridge.callTool("android_focus_input", extraArgs)
            }
            "type_text", "android_type_text" -> {
                if (!extraArgs.has("text")) extraArgs.put("text", text)
                if (!extraArgs.has("target_field") && target.isNotBlank()) extraArgs.put("target_field", target)
                AndroidDeviceMcpBridge.callTool("android_type_text", extraArgs)
            }
            "clear_text", "android_clear_text" -> AndroidDeviceMcpBridge.callTool("android_clear_text", extraArgs)
            "control_hardware", "hardware", "android_control_hardware" -> {
                if (!extraArgs.has("setting") && target.isNotBlank()) extraArgs.put("setting", target)
                if (!extraArgs.has("state") && text.isNotBlank()) extraArgs.put("state", text)
                AndroidDeviceMcpBridge.callTool("android_control_hardware", extraArgs)
            }
            "send_message", "android_send_message" -> {
                if (!extraArgs.has("recipient") && target.isNotBlank()) extraArgs.put("recipient", target)
                if (!extraArgs.has("message") && text.isNotBlank()) extraArgs.put("message", text)
                AndroidDeviceMcpBridge.callTool("android_send_message", extraArgs)
            }
            "dial_phone", "call", "android_dial_phone" -> {
                if (!extraArgs.has("phone_number") && !extraArgs.has("contact_name") && target.isNotBlank()) {
                    if (target.any { it.isDigit() }) extraArgs.put("phone_number", target) else extraArgs.put("contact_name", target)
                }
                AndroidDeviceMcpBridge.callTool("android_dial_phone", extraArgs)
            }
            "set_alarm_or_timer", "android_set_alarm_or_timer" -> {
                if (!extraArgs.has("mode")) extraArgs.put("mode", target.ifBlank { "timer" })
                AndroidDeviceMcpBridge.callTool("android_set_alarm_or_timer", extraArgs)
            }
            "media_control", "android_media_control" -> {
                if (!extraArgs.has("action")) extraArgs.put("action", target.ifBlank { "toggle" })
                AndroidDeviceMcpBridge.callTool("android_media_control", extraArgs)
            }
            "system_status", "android_get_system_status" ->
                AndroidDeviceMcpBridge.callTool("android_get_system_status", extraArgs)
            "deep_link", "android_deep_link" -> {
                if (!extraArgs.has("uri") && target.isNotBlank()) extraArgs.put("uri", target)
                AndroidDeviceMcpBridge.callTool("android_deep_link", extraArgs)
            }
            "share_file", "android_share_file" -> {
                if (!extraArgs.has("file_path") && target.isNotBlank()) extraArgs.put("file_path", target)
                if (!extraArgs.has("caption") && text.isNotBlank()) extraArgs.put("caption", text)
                AndroidDeviceMcpBridge.callTool("android_share_file", extraArgs)
            }
            "get_clipboard", "android_get_clipboard" ->
                AndroidDeviceMcpBridge.callTool("android_get_clipboard", extraArgs)
            "set_clipboard", "android_set_clipboard" -> {
                if (!extraArgs.has("text")) extraArgs.put("text", text.ifBlank { target })
                AndroidDeviceMcpBridge.callTool("android_set_clipboard", extraArgs)
            }
            "reply_notification", "android_reply_notification" -> {
                if (!extraArgs.has("reply_text")) extraArgs.put("reply_text", text)
                if (!extraArgs.has("sender") && target.isNotBlank()) extraArgs.put("sender", target)
                AndroidDeviceMcpBridge.callTool("android_reply_notification", extraArgs)
            }
            "auto_fill_otp", "android_auto_fill_otp" ->
                AndroidDeviceMcpBridge.callTool("android_auto_fill_otp", extraArgs)
            "open_accessibility_settings", "android_open_accessibility_settings" ->
                AndroidDeviceMcpBridge.callTool("android_open_accessibility_settings", extraArgs)
            "stop_task", "android_stop_task" ->
                AndroidDeviceMcpBridge.callTool("android_stop_task", extraArgs)
            else -> {
                if (action.startsWith("android_")) {
                    AndroidDeviceMcpBridge.callTool(action, extraArgs)
                } else {
                    val report = PrivateAgentScreenOperator.executeVerifiedOperatorAction(
                        context = context,
                        goalDescription = "$action $target $text".trim(),
                        targetLabelOrText = target,
                        textToType = text,
                        scrollDirection = if (action == "scroll") target.ifBlank { "DOWN" } else ""
                    )
                    JSONObject()
                        .put("completed", report.completed)
                        .put("summary", report.spokenHinglishSummary)
                        .put("stepsExecuted", report.totalStepsExecuted)
                }
            }
        }
        val safeDisplay = JSONObject(resJson.toString()).apply {
            if (has("image_base64")) {
                remove("image_base64")
                put("image_attached", true)
            }
        }
        val out = safeDisplay.toString(2).take(10000)
        val hasErr = resJson.has("error") || resJson.optBoolean("completed", true) == false
        val done = initialTool.copy(
            status = if (hasErr) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS,
            output = out,
            exitCode = if (hasErr) 1 else 0,
            durationMs = System.currentTimeMillis() - startMs
        )
        emit(AntigravityStreamEvent.ToolCallUpdate(done))
        return done to safeDisplay
    }

    private suspend fun FlowCollector<AntigravityStreamEvent>.executeDynamicMcpTool(
        context: Context,
        initialTool: ToolCall,
        fnName: String,
        fnArgs: JSONObject,
        startMs: Long
    ): Pair<ToolCall, JSONObject> {
        val target = com.jarvisai.assistant.mcp.McpServersManager.resolveDynamicToolTarget(fnName)
            ?: return failTool(initialTool, "Unknown dynamic MCP tool: $fnName", startMs)
        val (serverName, toolName) = target
        val res = com.jarvisai.assistant.mcp.McpServersManager.callMcpTool(context, serverName, toolName, fnArgs)
        return res.fold(
            onSuccess = { jsonResult ->
                val textSummary = jsonResult.optJSONArray("content")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        arr.optJSONObject(i)?.optString("text")?.takeIf(String::isNotBlank)
                    }.joinToString("\n")
                }?.takeIf { it.isNotBlank() } ?: jsonResult.toString(2)
                val isErr = jsonResult.optBoolean("isError", false)
                val done = initialTool.copy(
                    status = if (isErr) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS,
                    output = textSummary.take(10000),
                    exitCode = if (isErr) 1 else 0,
                    durationMs = System.currentTimeMillis() - startMs
                )
                emit(AntigravityStreamEvent.ToolCallUpdate(done))
                done to jsonResult
            },
            onFailure = { err ->
                failTool(initialTool, "MCP $serverName/$toolName failed: ${err.message}", startMs)
            }
        )
    }

    private suspend fun FlowCollector<AntigravityStreamEvent>.failTool(
        initialTool: ToolCall,
        err: String,
        startMs: Long
    ): Pair<ToolCall, JSONObject> {
        Log.w(TAG, "${initialTool.name} failed: $err")
        val done = initialTool.copy(
            status = ToolCallStatus.FAILED,
            output = err,
            exitCode = 1,
            durationMs = System.currentTimeMillis() - startMs
        )
        emit(AntigravityStreamEvent.ToolCallUpdate(done))
        return done to JSONObject().put("error", err)
    }

    private fun stripHtml(raw: String): String {
        return raw
            .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
