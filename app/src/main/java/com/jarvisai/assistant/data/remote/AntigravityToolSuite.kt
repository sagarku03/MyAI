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
            description = "Control Jarvis's built-in Android WebView CDP Browser via MCP (actions: browser_open_url, browser_read_page, browser_inspect_dom, browser_eval_js, browser_interact, browser_list_tabs, browser_get_console_logs).",
            properties = JSONObject()
                .put("tool_name", JSONObject().put("type", "STRING").put("description", "MCP browser tool name."))
                .put("arguments", JSONObject().put("type", "OBJECT").put("description", "JSON arguments for the browser tool.")),
            required = listOf("tool_name")
        )

        addTool(
            name = "android_screen_control",
            description = "Read or control the Android phone screen via Accessibility & PrivateAgentScreenOperator (inspect UI tree, launch apps, tap, type text, scroll, press BACK/HOME).",
            properties = JSONObject()
                .put("action", JSONObject().put("type", "STRING").put("description", "Action: inspect_ui, launch_app, tap_label, type_text, scroll, press_key, tap_xy."))
                .put("target", JSONObject().put("type", "STRING").put("description", "App name, button label, scroll direction (UP/DOWN), or key (BACK/HOME/RECENTS)."))
                .put("text", JSONObject().put("type", "STRING").put("description", "Text to type when action=type_text."))
                .put("x", JSONObject().put("type", "INTEGER").put("description", "X coordinate for action=tap_xy."))
                .put("y", JSONObject().put("type", "INTEGER").put("description", "Y coordinate for action=tap_xy.")),
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
            description = "Dispatch a specialized coding or shell subagent task in the background (claude, opencode, or bash) to run parallel work.",
            properties = JSONObject()
                .put("prompt", JSONObject().put("type", "STRING").put("description", "Clear autonomous task goal for the subagent."))
                .put("working_dir", JSONObject().put("type", "STRING").put("description", "Project directory."))
                .put("agent_type", JSONObject().put("type", "STRING").put("description", "'claude', 'opencode', or 'bash'.")),
            required = listOf("prompt")
        )

        return JSONArray().put(JSONObject().put("functionDeclarations", decls))
    }

    private fun defaultWorkspaceRoot(context: Context): File {
        val activeProj = ActiveCodeEditorRegistry.currentProjectPath?.takeIf { it.isNotBlank() }
        if (activeProj != null) {
            val f = File(activeProj)
            if (f.exists() && f.isDirectory) return f
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
                    val dir = fnArgs.optString("working_dir", "").trim().ifBlank { defaultWorkspaceRoot(context).absolutePath }
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

                else -> failTool(initialTool, "Unsupported tool: $fnName", startMs)
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
        val workingDir = fnArgs.optString("working_dir", "").trim()
            .ifBlank { defaultWorkspaceRoot(context).absolutePath }
        val targetExternalTermux = fnArgs.optBoolean("target_external_termux", false)

        if (cmd.isBlank()) {
            return failTool(initialTool, "Error: 'command' parameter is empty.", startMs)
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
                    val optText = optsArr.optString(oi, "")
                    if (optText.isNotBlank()) {
                        optsList.add(QuestionnaireOption(id = "opt_$oi", text = optText))
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
        val action = fnArgs.optString("action", "inspect_ui").lowercase()
        val target = fnArgs.optString("target", "")
        val text = fnArgs.optString("text", "")

        val resJson = when (action) {
            "inspect_ui" -> AndroidDeviceMcpBridge.callTool("android_get_ui_tree", JSONObject())
            "launch_app" -> AndroidDeviceMcpBridge.callTool("android_launch_app", JSONObject().put("appName", target))
            "press_key" -> AndroidDeviceMcpBridge.callTool("android_press_key", JSONObject().put("key", target.ifBlank { "BACK" }))
            "tap_xy" -> AndroidDeviceMcpBridge.callTool(
                "android_tap",
                JSONObject().put("x", fnArgs.optInt("x", 0)).put("y", fnArgs.optInt("y", 0))
            )
            else -> {
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
        val out = resJson.toString(2).take(10000)
        val hasErr = resJson.has("error") || resJson.optBoolean("completed", true) == false
        val done = initialTool.copy(
            status = if (hasErr) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS,
            output = out,
            exitCode = if (hasErr) 1 else 0,
            durationMs = System.currentTimeMillis() - startMs
        )
        emit(AntigravityStreamEvent.ToolCallUpdate(done))
        return done to resJson
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
