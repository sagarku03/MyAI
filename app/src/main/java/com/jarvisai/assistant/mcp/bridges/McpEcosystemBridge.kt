package com.jarvisai.assistant.mcp.bridges

import android.content.Context
import android.util.Log
import com.jarvisai.assistant.browser.BrowserSessionManager
import com.jarvisai.assistant.data.remote.JarvisHubConfig
import com.jarvisai.assistant.extension.BrowserExtensionManager
import com.jarvisai.assistant.ide.ActiveCodeEditorRegistry
import com.jarvisai.assistant.mcp.McpServerSpec
import com.jarvisai.assistant.mcp.McpServersManager
import com.jarvisai.assistant.skills.SkillsAndPluginsManager
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.LocalTerminalBridge
import com.jarvisai.assistant.terminal.WorkspaceSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Provides:
 * 1. Native `@modelcontextprotocol/server-filesystem` compatible tools on `/filesystem` (zero npx latency).
 * 2. Dynamic Agent-Authored Custom MCP Servers & Tools (`/custom/<server_name>`).
 * 3. First-class MCP, Plugin/Browser-Extension, and Agent Skill (`SKILL.md`) creation & hot-reload tools
 *    so Antigravity can inspect, use, and author new MCP servers, plugins, and skills on the fly.
 */
object McpEcosystemBridge {

    private const val TAG = "McpEcosystemBridge"

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun requireContext(): Context? = appContext

    private fun resolvePath(rawPath: String): File {
        val ctx = appContext
        val homeDir = if (ctx != null) LocalEnvironmentManager.getHomeDir(ctx) else File("/data/data/com.termux/files/home")
        val defaultRoot = ActiveCodeEditorRegistry.currentProjectPath
            ?.takeIf { it.isNotBlank() && File(it).exists() }
            ?.let { File(it) }
            ?: if (ctx != null) LocalEnvironmentManager.getIsolatedWorkspaceDir(ctx) else homeDir
        val trimmed = rawPath.trim()
        return when {
            trimmed.isEmpty() || trimmed == "." -> defaultRoot
            trimmed == "~" -> homeDir
            trimmed.startsWith("~/") -> File(homeDir, trimmed.removePrefix("~/"))
            trimmed.startsWith("/") -> File(trimmed)
            else -> File(defaultRoot, trimmed)
        }
    }

    private fun getCustomRegistryFile(context: Context): File {
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        val dir = File(homeDir, ".gemini/${JarvisHubConfig.ISOLATED_APP_DATA_DIR}").apply { mkdirs() }
        return File(dir, "custom_mcp_registry.json")
    }

    fun loadCustomServersRegistry(context: Context? = appContext): JSONObject {
        val ctx = context ?: return JSONObject()
        val f = getCustomRegistryFile(ctx)
        if (!f.exists()) return JSONObject()
        return runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
    }

    private fun saveCustomServersRegistry(context: Context, root: JSONObject) {
        runCatching {
            val f = getCustomRegistryFile(context)
            f.parentFile?.mkdirs()
            f.writeText(root.toString(2))
        }
    }

    // -------------------------------------------------------------------------
    // 1. Native Filesystem MCP Tools (/filesystem)
    // -------------------------------------------------------------------------

    fun listFilesystemTools(): JSONArray = JSONArray().apply {
        put(
            JSONObject()
                .put("name", "read_file")
                .put("description", "Read complete or partial contents of a file from the workspace or device storage.")
                .put(
                    "inputSchema",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("path", JSONObject().put("type", "string").put("description", "File path to read"))
                                .put("head", JSONObject().put("type", "integer").put("description", "Optional first N lines"))
                                .put("tail", JSONObject().put("type", "integer").put("description", "Optional last N lines"))
                        )
                        .put("required", JSONArray().put("path"))
                )
        )
        put(
            JSONObject()
                .put("name", "write_file")
                .put("description", "Create a new file or overwrite an existing file with new content.")
                .put(
                    "inputSchema",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("path", JSONObject().put("type", "string").put("description", "Target file path"))
                                .put("content", JSONObject().put("type", "string").put("description", "File content to write"))
                        )
                        .put("required", JSONArray().put("path").put("content"))
                )
        )
        put(
            JSONObject()
                .put("name", "edit_file")
                .put("description", "Make line-based or exact text replacements in a file.")
                .put(
                    "inputSchema",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("path", JSONObject().put("type", "string").put("description", "Target file path"))
                                .put("oldText", JSONObject().put("type", "string").put("description", "Exact text to find"))
                                .put("newText", JSONObject().put("type", "string").put("description", "Replacement text"))
                        )
                        .put("required", JSONArray().put("path").put("oldText").put("newText"))
                )
        )
        put(
            JSONObject()
                .put("name", "list_directory")
                .put("description", "Get a detailed listing of all files and directories in a specified path.")
                .put(
                    "inputSchema",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("path", JSONObject().put("type", "string").put("description", "Directory path"))
                        )
                        .put("required", JSONArray().put("path"))
                )
        )
        put(
            JSONObject()
                .put("name", "create_directory")
                .put("description", "Create a new directory or ensure a directory exists.")
                .put(
                    "inputSchema",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("path", JSONObject().put("type", "string").put("description", "Directory path to create"))
                        )
                        .put("required", JSONArray().put("path"))
                )
        )
        put(
            JSONObject()
                .put("name", "search_files")
                .put("description", "Recursively search for files and directories matching a pattern.")
                .put(
                    "inputSchema",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("path", JSONObject().put("type", "string").put("description", "Root directory for search"))
                                .put("pattern", JSONObject().put("type", "string").put("description", "Filename or content substring pattern"))
                        )
                        .put("required", JSONArray().put("path").put("pattern"))
                )
        )
        put(
            JSONObject()
                .put("name", "get_file_info")
                .put("description", "Retrieve detailed metadata about a file or directory.")
                .put(
                    "inputSchema",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("path", JSONObject().put("type", "string").put("description", "File or directory path"))
                        )
                        .put("required", JSONArray().put("path"))
                )
        )
        put(
            JSONObject()
                .put("name", "list_allowed_directories")
                .put("description", "Returns the list of directories that this server is allowed to access.")
                .put("inputSchema", JSONObject().put("type", "object").put("properties", JSONObject()))
        )
    }

    fun callFilesystemTool(toolName: String, args: JSONObject): JSONObject? {
        return when (toolName) {
            "read_file", "fs_read_file" -> {
                val file = resolvePath(args.optString("path", ""))
                if (!file.exists() || !file.isFile) {
                    return JSONObject().put("error", "File not found: ${file.absolutePath}")
                }
                val lines = file.readLines()
                val head = args.optInt("head", 0)
                val tail = args.optInt("tail", 0)
                val content = when {
                    head > 0 -> lines.take(head).joinToString("\n")
                    tail > 0 -> lines.takeLast(tail).joinToString("\n")
                    else -> lines.joinToString("\n")
                }
                JSONObject()
                    .put("path", file.absolutePath)
                    .put("totalLines", lines.size)
                    .put("content", content.take(30000))
            }
            "write_file", "fs_write_file" -> {
                val file = resolvePath(args.optString("path", ""))
                val content = args.optString("content", "")
                file.parentFile?.mkdirs()
                file.writeText(content)
                WorkspaceSyncManager.notifyExternalChange(file.absolutePath)
                JSONObject()
                    .put("success", true)
                    .put("path", file.absolutePath)
                    .put("bytesWritten", content.length)
            }
            "edit_file", "fs_edit_file" -> {
                val file = resolvePath(args.optString("path", ""))
                if (!file.exists() || !file.isFile) {
                    return JSONObject().put("error", "File not found: ${file.absolutePath}")
                }
                val current = file.readText()
                val oldText = args.optString("oldText", args.optString("target_content", ""))
                val newText = args.optString("newText", args.optString("replacement_content", ""))
                if (oldText.isEmpty() || !current.contains(oldText)) {
                    return JSONObject().put("error", "oldText not found in ${file.absolutePath}")
                }
                file.writeText(current.replaceFirst(oldText, newText))
                WorkspaceSyncManager.notifyExternalChange(file.absolutePath)
                JSONObject().put("success", true).put("path", file.absolutePath)
            }
            "list_directory", "fs_list_directory" -> {
                val dir = resolvePath(args.optString("path", "."))
                if (!dir.exists() || !dir.isDirectory) {
                    return JSONObject().put("error", "Directory not found: ${dir.absolutePath}")
                }
                val entries = (dir.listFiles() ?: emptyArray())
                    .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                    .map { if (it.isDirectory) "[DIR] ${it.name}" else "[FILE] ${it.name} (${it.length()}B)" }
                JSONObject()
                    .put("path", dir.absolutePath)
                    .put("entries", JSONArray(entries))
            }
            "create_directory", "fs_create_directory" -> {
                val dir = resolvePath(args.optString("path", ""))
                dir.mkdirs()
                JSONObject().put("success", dir.exists()).put("path", dir.absolutePath)
            }
            "search_files", "fs_search_files" -> {
                val root = resolvePath(args.optString("path", "."))
                val pattern = args.optString("pattern", "").lowercase()
                val matches = mutableListOf<String>()
                fun walk(f: File) {
                    if (matches.size >= 100) return
                    if (f.name in setOf(".git", "node_modules", "build", ".gradle")) return
                    if (f.name.lowercase().contains(pattern)) {
                        matches.add(f.absolutePath)
                    }
                    if (f.isDirectory) f.listFiles()?.forEach { walk(it) }
                }
                if (root.exists()) walk(root)
                JSONObject().put("root", root.absolutePath).put("matches", JSONArray(matches))
            }
            "get_file_info", "fs_get_file_info" -> {
                val f = resolvePath(args.optString("path", ""))
                if (!f.exists()) return JSONObject().put("error", "Path does not exist: ${f.absolutePath}")
                JSONObject()
                    .put("path", f.absolutePath)
                    .put("isDirectory", f.isDirectory)
                    .put("isFile", f.isFile)
                    .put("sizeBytes", f.length())
                    .put("lastModified", f.lastModified())
                    .put("canRead", f.canRead())
                    .put("canWrite", f.canWrite())
            }
            "list_allowed_directories" -> {
                val ctx = appContext
                val dirs = mutableListOf<String>()
                if (ctx != null) {
                    dirs.add(LocalEnvironmentManager.getHomeDir(ctx).absolutePath)
                    dirs.add(LocalEnvironmentManager.getIsolatedWorkspaceDir(ctx).absolutePath)
                    dirs.add(LocalEnvironmentManager.getIsolatedProjectsDir(ctx).absolutePath)
                }
                ActiveCodeEditorRegistry.currentProjectPath?.takeIf { it.isNotBlank() }?.let { dirs.add(it) }
                JSONObject().put("allowedDirectories", JSONArray(dirs.distinct()))
            }
            else -> null
        }
    }

    // -------------------------------------------------------------------------
    // 2. MCP, Plugin/Extension & Skill Self-Authoring Tools
    // -------------------------------------------------------------------------

    fun listAuthoringAndManagementTools(): JSONArray = JSONArray().apply {
        put(
            JSONObject()
                .put("name", "mcp_list_servers")
                .put("description", "List all configured and active MCP servers in Jarvis, their connection status, endpoints/commands, and their available tools.")
                .put("inputSchema", JSONObject().put("type", "object").put("properties", JSONObject()))
        )
        put(
            JSONObject()
                .put("name", "mcp_call_tool")
                .put("description", "Invoke any tool on any active MCP server (e.g. agentra-android-mcp, browser_mcp, filesystem, or custom MCP servers).")
                .put(
                    "inputSchema",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("server_name", JSONObject().put("type", "string").put("description", "MCP server name (e.g. 'agentra-android-mcp', 'browser_mcp', 'filesystem')"))
                                .put("tool_name", JSONObject().put("type", "string").put("description", "Tool name to execute"))
                                .put("arguments", JSONObject().put("type", "object").put("description", "JSON arguments object for the tool"))
                        )
                        .put("required", JSONArray().put("server_name").put("tool_name"))
                )
        )
        put(
            JSONObject()
                .put("name", "mcp_create_server")
                .put("description", "Create, register, and hot-reload a new MCP server or custom MCP toolset into mcp_config.json and Jarvis's live MCP engine.")
                .put(
                    "inputSchema",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("server_name", JSONObject().put("type", "string").put("description", "Unique MCP server identifier (e.g. 'weather-mcp', 'apk-tools-mcp')"))
                                .put("description", JSONObject().put("type", "string").put("description", "Human-readable description of this MCP server"))
                                .put("server_url", JSONObject().put("type", "string").put("description", "Optional remote HTTP/SSE MCP URL. Leave blank if defining custom_tools or script_content."))
                                .put("command", JSONObject().put("type", "string").put("description", "Optional stdio command (e.g. 'node', 'python3', 'sh')"))
                                .put("args", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")).put("description", "Optional command arguments"))
                                .put("script_content", JSONObject().put("type", "string").put("description", "Optional full script source code if creating a standalone stdio MCP server script"))
                                .put("runtime", JSONObject().put("type", "string").put("description", "Runtime for script_content: 'node', 'python', or 'sh'"))
                                .put(
                                    "custom_tools",
                                    JSONObject()
                                        .put("type", "array")
                                        .put("description", "List of custom tools hosted natively by JarvisEmbeddedMcp at /custom/<server_name>. Each item: {name, description, command_template, js_code, parameters_json}")
                                        .put("items", JSONObject().put("type", "object"))
                                )
                        )
                        .put("required", JSONArray().put("server_name"))
                )
        )
        put(
            JSONObject()
                .put("name", "mcp_remove_server")
                .put("description", "Remove an MCP server from mcp_config.json and refresh the active MCP registry.")
                .put(
                    "inputSchema",
                    JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject().put("server_name", JSONObject().put("type", "string")))
                        .put("required", JSONArray().put("server_name"))
                )
        )
        put(
            JSONObject()
                .put("name", "skill_list_all")
                .put("description", "List all installed Agent Skills (SKILL.md) across Global, Workspace, Plugins, and Browser Extensions.")
                .put("inputSchema", JSONObject().put("type", "object").put("properties", JSONObject()))
        )
        put(
            JSONObject()
                .put("name", "skill_create_or_update")
                .put("description", "Create or update an Agent Skill (SKILL.md) in ~/.gemini/jarvis-antigravity/skills/<name>/SKILL.md (or workspace) and immediately activate it.")
                .put(
                    "inputSchema",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("name", JSONObject().put("type", "string").put("description", "Skill slug name (e.g. 'kotlin-coroutines-pro')"))
                                .put("display_name", JSONObject().put("type", "string").put("description", "Human-readable title"))
                                .put("description", JSONObject().put("type", "string").put("description", "1-2 sentence description of when and how to use this skill"))
                                .put("content", JSONObject().put("type", "string").put("description", "Markdown instructions body for SKILL.md"))
                                .put("scope", JSONObject().put("type", "string").put("description", "'GLOBAL' (default) or 'WORKSPACE'"))
                        )
                        .put("required", JSONArray().put("name").put("description").put("content"))
                )
        )
        put(
            JSONObject()
                .put("name", "skill_delete")
                .put("description", "Delete an Agent Skill by name and refresh the skills registry.")
                .put(
                    "inputSchema",
                    JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject().put("name", JSONObject().put("type", "string")))
                        .put("required", JSONArray().put("name"))
                )
        )
        put(
            JSONObject()
                .put("name", "plugin_list_all")
                .put("description", "List all installed Antigravity Plugins and Chrome MV3 Browser Extensions.")
                .put("inputSchema", JSONObject().put("type", "object").put("properties", JSONObject()))
        )
        put(
            JSONObject()
                .put("name", "plugin_create_or_update")
                .put("description", "Create and install a complete Chrome MV3 Browser Extension and/or Antigravity Plugin (with manifest.json, popup.html, service-worker.js, content.js, bundled SKILL.md, and MCP tools) and hot-reload it immediately.")
                .put(
                    "inputSchema",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("id", JSONObject().put("type", "string").put("description", "Plugin/Extension ID (lowercase letters, digits, hyphens)"))
                                .put("name", JSONObject().put("type", "string").put("description", "Display name"))
                                .put("version", JSONObject().put("type", "string").put("description", "Semantic version (default '1.0.0')"))
                                .put("description", JSONObject().put("type", "string").put("description", "Plugin/Extension description"))
                                .put("popup_html", JSONObject().put("type", "string").put("description", "HTML content for the extension's interactive popup.html UI"))
                                .put("service_worker_js", JSONObject().put("type", "string").put("description", "JavaScript code for background service-worker.js"))
                                .put("content_js", JSONObject().put("type", "string").put("description", "Optional JavaScript content script injected into web pages"))
                                .put("skill_markdown", JSONObject().put("type", "string").put("description", "Optional SKILL.md markdown bundled with this plugin"))
                                .put("mcp_tools", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")).put("description", "Optional list of MCP tool names exposed by this extension"))
                        )
                        .put("required", JSONArray().put("id").put("name"))
                )
        )
    }

    suspend fun callAuthoringOrManagementTool(toolName: String, args: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        val ctx = requireContext() ?: return@withContext JSONObject().put("error", "Application context not initialized")
        when (toolName) {
            "mcp_list_servers" -> {
                val states = McpServersManager.ensureServersLoadedAndProbed(ctx)
                val serversArr = JSONArray()
                states.forEach { st ->
                    val toolsArr = JSONArray()
                    st.tools.forEach { t ->
                        toolsArr.put(JSONObject().put("name", t.name).put("description", t.description))
                    }
                    serversArr.put(
                        JSONObject()
                            .put("serverName", st.serverName)
                            .put("status", st.status)
                            .put("enabled", st.isEnabled)
                            .put("endpointOrCommand", if (st.spec.isRemote) st.spec.serverUrl else "${st.spec.command} ${st.spec.args.joinToString(" ")}".trim())
                            .put("toolCount", st.tools.size)
                            .put("tools", toolsArr)
                    )
                }
                JSONObject()
                    .put("activeServerCount", states.count { it.isEnabled && it.status == "READY" })
                    .put("totalConfiguredCount", states.size)
                    .put("configPath", McpServersManager.getPrimaryConfigFile(ctx).absolutePath)
                    .put("servers", serversArr)
            }

            "mcp_call_tool" -> {
                val serverName = args.optString("server_name", args.optString("serverName", "")).trim()
                val targetTool = args.optString("tool_name", args.optString("toolName", "")).trim()
                val toolArgs = args.optJSONObject("arguments") ?: JSONObject()
                if (serverName.isBlank() || targetTool.isBlank()) {
                    return@withContext JSONObject().put("error", "Both 'server_name' and 'tool_name' are required.")
                }
                val res = McpServersManager.callMcpTool(ctx, serverName, targetTool, toolArgs)
                res.fold(
                    onSuccess = { it },
                    onFailure = { err -> JSONObject().put("error", err.message ?: "MCP call failed") }
                )
            }

            "mcp_create_server" -> {
                val rawName = args.optString("server_name", args.optString("serverName", "")).trim()
                if (rawName.isBlank()) {
                    return@withContext JSONObject().put("error", "'server_name' is required")
                }
                val safeName = rawName.lowercase().replace(Regex("[^a-z0-9_-]"), "-").trim('-')
                val description = args.optString("description", "Custom MCP server ($safeName)")
                val singleToolName = args.optString("tool_name", args.optString("toolName", "")).trim()
                val customToolsArr = (args.optJSONArray("custom_tools") ?: args.optJSONArray("tools"))
                    ?: if (singleToolName.isNotBlank()) {
                        JSONArray().put(
                            JSONObject()
                                .put("name", singleToolName)
                                .put("description", args.optString("tool_description", args.optString("toolDescription", "Custom tool $singleToolName")))
                                .put("command_template", args.optString("command_template", args.optString("commandTemplate", "")))
                                .put("js_code", args.optString("js_code", args.optString("jsCode", "")))
                        )
                    } else null
                val scriptContent = args.optString("script_content", "").trim()
                val explicitUrl = args.optString("server_url", args.optString("url", "")).trim()
                val explicitCommand = args.optString("command", "").trim()

                val stdioBridgePath = LocalEnvironmentManager.ensureMcpStdioBridgeScript(ctx).absolutePath

                val spec: McpServerSpec = when {
                    customToolsArr != null && customToolsArr.length() > 0 -> {
                        val registry = loadCustomServersRegistry(ctx)
                        val normalizedTools = JSONArray()
                        for (i in 0 until customToolsArr.length()) {
                            val item = customToolsArr.optJSONObject(i) ?: continue
                            val tName = item.optString("name", "").trim()
                            if (tName.isBlank()) continue
                            normalizedTools.put(
                                JSONObject()
                                    .put("name", tName)
                                    .put("description", item.optString("description", "Custom tool $tName"))
                                    .put("command_template", item.optString("command_template", item.optString("command", "")))
                                    .put("js_code", item.optString("js_code", ""))
                                    .put("inputSchema", item.optJSONObject("inputSchema") ?: item.optJSONObject("parameters_json") ?: JSONObject().put("type", "object").put("properties", JSONObject()))
                            )
                        }
                        registry.put(
                            safeName,
                            JSONObject()
                                .put("serverName", safeName)
                                .put("description", description)
                                .put("createdAt", System.currentTimeMillis())
                                .put("tools", normalizedTools)
                        )
                        saveCustomServersRegistry(ctx, registry)
                        val customEndpoint = "http://127.0.0.1:${JarvisHubConfig.ISOLATED_MCP_PORT}/custom/$safeName"
                        McpServerSpec(
                            serverName = safeName,
                            command = "/system/bin/sh",
                            args = listOf(stdioBridgePath, customEndpoint),
                            serverUrl = customEndpoint,
                            disabled = false
                        )
                    }

                    scriptContent.isNotEmpty() -> {
                        val runtime = args.optString("runtime", "sh").lowercase()
                        val homeDir = LocalEnvironmentManager.getHomeDir(ctx)
                        val customDir = File(homeDir, ".gemini/${JarvisHubConfig.ISOLATED_APP_DATA_DIR}/custom-mcp/$safeName").apply { mkdirs() }
                        val ext = when {
                            runtime.contains("node") || runtime.contains("js") -> "js"
                            runtime.contains("py") -> "py"
                            else -> "sh"
                        }
                        val scriptFile = File(customDir, "server.$ext")
                        scriptFile.writeText(scriptContent)
                        scriptFile.setExecutable(true, false)
                        runCatching { android.system.Os.chmod(scriptFile.absolutePath, 493) }
                        val cmd = when (ext) {
                            "js" -> "node"
                            "py" -> "python3"
                            else -> "/system/bin/sh"
                        }
                        McpServerSpec(
                            serverName = safeName,
                            command = cmd,
                            args = listOf(scriptFile.absolutePath),
                            disabled = false
                        )
                    }

                    explicitUrl.isNotEmpty() -> {
                        val isLocalBridge = explicitUrl.startsWith("http://127.0.0.1:") || explicitUrl.startsWith("http://localhost:")
                        McpServerSpec(
                            serverName = safeName,
                            command = if (isLocalBridge) "/system/bin/sh" else "",
                            args = if (isLocalBridge) listOf(stdioBridgePath, explicitUrl) else emptyList(),
                            serverUrl = explicitUrl,
                            disabled = false
                        )
                    }

                    explicitCommand.isNotEmpty() -> {
                        val argsList = mutableListOf<String>()
                        args.optJSONArray("args")?.let { arr ->
                            for (i in 0 until arr.length()) {
                                arr.optString(i).takeIf { it.isNotEmpty() }?.let { argsList.add(it) }
                            }
                        }
                        McpServerSpec(
                            serverName = safeName,
                            command = explicitCommand,
                            args = argsList,
                            disabled = false
                        )
                    }

                    else -> {
                        // Default: create a ready-to-use custom MCP endpoint on JarvisEmbeddedMcp with a starter tool
                        val registry = loadCustomServersRegistry(ctx)
                        val defaultTool = JSONObject()
                            .put("name", "${safeName.replace('-', '_')}_status")
                            .put("description", "Check status and execute actions for $safeName")
                            .put("command_template", "echo 'MCP server $safeName is online and active'")
                            .put(
                                "inputSchema",
                                JSONObject()
                                    .put("type", "object")
                                    .put("properties", JSONObject().put("input", JSONObject().put("type", "string").put("description", "Optional input parameter")))
                            )
                        registry.put(
                            safeName,
                            JSONObject()
                                .put("serverName", safeName)
                                .put("description", description)
                                .put("createdAt", System.currentTimeMillis())
                                .put("tools", JSONArray().put(defaultTool))
                        )
                        saveCustomServersRegistry(ctx, registry)
                        val customEndpoint = "http://127.0.0.1:${JarvisHubConfig.ISOLATED_MCP_PORT}/custom/$safeName"
                        McpServerSpec(
                            serverName = safeName,
                            command = "/system/bin/sh",
                            args = listOf(stdioBridgePath, customEndpoint),
                            serverUrl = customEndpoint,
                            disabled = false
                        )
                    }
                }

                val stateRes = McpServersManager.upsertServer(ctx, spec)
                val state = stateRes.getOrNull()
                McpServersManager.notifyLocalAgyDaemonRefresh(ctx)
                JSONObject()
                    .put("success", stateRes.isSuccess)
                    .put("serverName", safeName)
                    .put("server_name", safeName)
                    .put("status", state?.status ?: "READY")
                    .put("toolCount", state?.tools?.size ?: 0)
                    .put("tools", JSONArray((state?.tools ?: emptyList()).map { it.name }))
                    .put("configPath", McpServersManager.getPrimaryConfigFile(ctx).absolutePath)
            }

            "mcp_remove_server" -> {
                val serverName = args.optString("server_name", args.optString("serverName", "")).trim()
                if (serverName.isBlank()) {
                    return@withContext JSONObject().put("error", "'server_name' is required")
                }
                val registry = loadCustomServersRegistry(ctx)
                if (registry.has(serverName)) {
                    registry.remove(serverName)
                    saveCustomServersRegistry(ctx, registry)
                }
                McpServersManager.deleteServer(ctx, serverName)
                McpServersManager.notifyLocalAgyDaemonRefresh(ctx)
                JSONObject().put("success", true).put("removed", serverName)
            }

            "skill_list_all" -> {
                val skills = SkillsAndPluginsManager.refreshSkills(ctx)
                val arr = JSONArray()
                skills.forEach { s ->
                    arr.put(
                        JSONObject()
                            .put("identifier", s.identifier)
                            .put("name", s.name)
                            .put("displayName", s.displayName)
                            .put("description", s.description)
                            .put("scope", s.scope)
                            .put("path", s.path)
                    )
                }
                JSONObject()
                    .put("count", skills.size)
                    .put("skillsRoot", SkillsAndPluginsManager.getGlobalSkillsRoot(ctx).absolutePath)
                    .put("skills", arr)
            }

            "skill_create_or_update" -> {
                val rawName = args.optString("name", "").trim()
                if (rawName.isBlank()) {
                    return@withContext JSONObject().put("error", "Skill 'name' is required")
                }
                val slug = rawName.lowercase().replace(Regex("[^a-z0-9_-]"), "-").trim('-')
                val displayName = args.optString("display_name", args.optString("displayName", rawName)).trim().ifBlank { slug }
                val description = args.optString("description", "Custom agent workflow skill ($displayName)").trim()
                val body = args.optString("content", args.optString("markdown", args.optString("instructions", ""))).trim()
                val scope = args.optString("scope", "GLOBAL").uppercase()

                val fullMarkdown = if (body.startsWith("---")) {
                    body
                } else {
                    """
                    |---
                    |name: $slug
                    |display_name: $displayName
                    |description: $description
                    |---
                    |
                    |$body
                    """.trimMargin()
                }

                val targetDirs = mutableListOf<File>()
                if (scope == "WORKSPACE" && !ActiveCodeEditorRegistry.currentProjectPath.isNullOrBlank()) {
                    targetDirs.add(File(ActiveCodeEditorRegistry.currentProjectPath!!, ".agent/skills/$slug"))
                    targetDirs.add(File(ActiveCodeEditorRegistry.currentProjectPath!!, ".gemini/skills/$slug"))
                } else {
                    val homeDir = LocalEnvironmentManager.getHomeDir(ctx)
                    targetDirs.add(File(SkillsAndPluginsManager.getGlobalSkillsRoot(ctx), slug))
                    targetDirs.add(File(homeDir, ".gemini/skills/$slug"))
                    targetDirs.add(File(homeDir, ".agents/skills/$slug"))
                }

                var primaryFile: File? = null
                targetDirs.forEach { dir ->
                    runCatching {
                        dir.mkdirs()
                        val f = File(dir, "SKILL.md")
                        f.writeText(fullMarkdown)
                        if (primaryFile == null) primaryFile = f
                    }
                }

                val refreshed = SkillsAndPluginsManager.refreshSkills(ctx)
                JSONObject()
                    .put("success", true)
                    .put("name", slug)
                    .put("displayName", displayName)
                    .put("path", primaryFile?.absolutePath.orEmpty())
                    .put("totalSkillsActive", refreshed.size)
            }

            "skill_delete" -> {
                val name = args.optString("name", "").trim()
                val current = SkillsAndPluginsManager.refreshSkills(ctx)
                val target = current.firstOrNull { it.name.equals(name, ignoreCase = true) || it.identifier.endsWith(":$name", ignoreCase = true) }
                    ?: return@withContext JSONObject().put("error", "Skill '$name' not found")
                SkillsAndPluginsManager.deleteSkill(ctx, target)
                JSONObject().put("success", true).put("deleted", target.name)
            }

            "plugin_list_all" -> {
                val plugins = SkillsAndPluginsManager.refreshInstalledPlugins(ctx)
                val arr = JSONArray()
                plugins.forEach { p ->
                    arr.put(
                        JSONObject()
                            .put("id", p.id)
                            .put("name", p.name)
                            .put("version", p.version)
                            .put("description", p.description)
                            .put("enabled", p.isEnabled)
                            .put("isWebExtension", p.isWebExtension)
                            .put("pluginUri", p.pluginUri)
                    )
                }
                JSONObject()
                    .put("count", plugins.size)
                    .put("plugins", arr)
            }

            "plugin_create_or_update" -> {
                val rawId = args.optString("id", args.optString("name", "")).trim()
                if (rawId.isBlank()) {
                    return@withContext JSONObject().put("error", "Plugin 'id' or 'name' is required")
                }
                val safeId = rawId.lowercase().replace(Regex("[^a-z0-9-]"), "-").replace(Regex("-+"), "-").trim('-')
                val name = args.optString("name", safeId).trim()
                val version = args.optString("version", "1.0.0").trim().ifBlank { "1.0.0" }
                val description = args.optString("description", "Custom Jarvis Browser Extension & Antigravity Plugin").trim()

                // 1. Write Chrome MV3 Extension to filesDir/extensions/<safeId>
                val extDir = File(ctx.filesDir, "extensions/$safeId").apply { mkdirs() }
                val popupHtml = args.optString("popup_html", "").trim().ifBlank {
                    """
                    <!DOCTYPE html>
                    <html>
                    <head>
                      <meta charset="utf-8">
                      <meta name="viewport" content="width=device-width,initial-scale=1">
                      <title>$name</title>
                      <style>
                        body { font-family: system-ui, sans-serif; background: #0D1117; color: #E6EDF3; padding: 16px; margin: 0; }
                        .card { background: #161B22; border: 1px solid #30363D; border-radius: 12px; padding: 14px; margin-bottom: 12px; }
                        h2 { margin: 0 0 6px 0; font-size: 16px; color: #58A6FF; }
                        p { margin: 0 0 12px 0; font-size: 13px; color: #8B949E; }
                        button { background: #238636; color: #fff; border: none; padding: 10px 14px; border-radius: 8px; font-weight: 600; width: 100%; cursor: pointer; }
                        #status { margin-top: 10px; font-size: 12px; color: #3FB950; }
                      </style>
                    </head>
                    <body>
                      <div class="card">
                        <h2>$name <span style="font-size:11px;color:#8B949E;">v$version</span></h2>
                        <p>$description</p>
                        <button id="runBtn">Run Extension Action on Active Tab</button>
                        <div id="status">Ready</div>
                      </div>
                      <script>
                        document.getElementById('runBtn').addEventListener('click', () => {
                          document.getElementById('status').textContent = 'Action dispatched to active tab!';
                          if (typeof chrome !== 'undefined' && chrome.runtime && chrome.runtime.sendMessage) {
                            chrome.runtime.sendMessage({ type: 'RUN_ACTION', pluginId: '$safeId' });
                          }
                        });
                      </script>
                    </body>
                    </html>
                    """.trimIndent()
                }
                File(extDir, "popup.html").writeText(popupHtml)

                val swJs = args.optString("service_worker_js", "").trim().ifBlank {
                    """
                    chrome.runtime.onInstalled.addListener(() => {
                      console.log('[$name] Extension installed and active');
                    });
                    chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
                      sendResponse({ ok: true, plugin: '$safeId', received: msg });
                      return true;
                    });
                    """.trimIndent()
                }
                File(extDir, "service-worker.js").writeText(swJs)

                val contentJs = args.optString("content_js", "").trim()
                if (contentJs.isNotBlank()) {
                    File(extDir, "content.js").writeText(contentJs)
                }

                val mcpToolsList = mutableListOf<String>()
                args.optJSONArray("mcp_tools")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        arr.optString(i).takeIf { it.isNotBlank() }?.let { mcpToolsList.add(it) }
                    }
                }
                if (mcpToolsList.isEmpty()) {
                    mcpToolsList.add("${safeId.replace('-', '_')}_run")
                }

                val manifestJson = JSONObject().apply {
                    put("manifest_version", 3)
                    put("id", safeId)
                    put("name", name)
                    put("version", version)
                    put("description", description)
                    put("permissions", JSONArray(listOf("storage", "tabs", "activeTab", "scripting")))
                    put("host_permissions", JSONArray(listOf("<all_urls>")))
                    put("action", JSONObject().put("default_title", name).put("default_popup", "popup.html"))
                    put("background", JSONObject().put("service_worker", "service-worker.js"))
                    if (contentJs.isNotBlank()) {
                        put(
                            "content_scripts",
                            JSONArray().put(
                                JSONObject()
                                    .put("matches", JSONArray().put("<all_urls>"))
                                    .put("js", JSONArray().put("content.js"))
                                    .put("run_at", "document_idle")
                            )
                        )
                    }
                    put(
                        "jarvis_capabilities",
                        JSONObject().put("mcp_tools", JSONArray(mcpToolsList))
                    )
                }
                File(extDir, "manifest.json").writeText(manifestJson.toString(2))

                // 2. Also register in Antigravity Plugins directory (~/.gemini/jarvis-antigravity/plugins/<safeId>)
                val pluginDir = File(SkillsAndPluginsManager.getPluginsRoot(ctx), safeId).apply { mkdirs() }
                File(pluginDir, "plugin.json").writeText(
                    JSONObject()
                        .put("id", safeId)
                        .put("name", name)
                        .put("version", version)
                        .put("description", description)
                        .put("pluginUri", extDir.absolutePath)
                        .put("enabled", true)
                        .toString(2)
                )

                val skillMd = args.optString("skill_markdown", "").trim()
                if (skillMd.isNotBlank()) {
                    val skillsSubDir = File(pluginDir, "skills/$safeId").apply { mkdirs() }
                    File(skillsSubDir, "SKILL.md").writeText(skillMd)
                }

                // 3. Reload BrowserExtensionManager + SkillsAndPluginsManager + McpServersManager
                BrowserExtensionManager.init(ctx)
                BrowserExtensionManager.reloadExtensions()
                SkillsAndPluginsManager.refreshSkills(ctx)
                val plugins = SkillsAndPluginsManager.refreshInstalledPlugins(ctx)

                JSONObject()
                    .put("success", true)
                    .put("id", safeId)
                    .put("name", name)
                    .put("extensionDir", extDir.absolutePath)
                    .put("pluginDir", pluginDir.absolutePath)
                    .put("popupUrl", "https://$safeId.chromiumapp.org/popup.html")
                    .put("mcpEndpoint", "http://127.0.0.1:${JarvisHubConfig.ISOLATED_MCP_PORT}/extension/$safeId")
                    .put("totalPluginsInstalled", plugins.size)
            }

            else -> null
        }
    }

    // -------------------------------------------------------------------------
    // 3. Dynamic Custom MCP Server Execution (/custom/<server_name>)
    // -------------------------------------------------------------------------

    fun listCustomServerTools(serverName: String): JSONArray {
        val ctx = appContext ?: return JSONArray()
        val registry = loadCustomServersRegistry(ctx)
        val srvObj = registry.optJSONObject(serverName) ?: return JSONArray()
        val tools = srvObj.optJSONArray("tools") ?: return JSONArray()
        val out = JSONArray()
        for (i in 0 until tools.length()) {
            val t = tools.optJSONObject(i) ?: continue
            out.put(
                JSONObject()
                    .put("name", t.optString("name"))
                    .put("description", t.optString("description", ""))
                    .put("inputSchema", t.optJSONObject("inputSchema") ?: JSONObject().put("type", "object").put("properties", JSONObject()))
            )
        }
        return out
    }

    fun listAllCustomToolsAcrossServers(): JSONArray {
        val ctx = appContext ?: return JSONArray()
        val registry = loadCustomServersRegistry(ctx)
        val out = JSONArray()
        val keys = registry.keys()
        while (keys.hasNext()) {
            val srvName = keys.next()
            val arr = listCustomServerTools(srvName)
            for (i in 0 until arr.length()) {
                out.put(arr.getJSONObject(i))
            }
        }
        return out
    }

    suspend fun callCustomServerTool(serverName: String?, toolName: String, args: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext null
        val registry = loadCustomServersRegistry(ctx)
        val candidateServers = if (!serverName.isNullOrBlank() && registry.has(serverName)) {
            listOf(serverName)
        } else {
            registry.keys().asSequence().toList()
        }

        for (srv in candidateServers) {
            val srvObj = registry.optJSONObject(srv) ?: continue
            val tools = srvObj.optJSONArray("tools") ?: continue
            for (i in 0 until tools.length()) {
                val t = tools.optJSONObject(i) ?: continue
                if (t.optString("name") == toolName) {
                    val cmdTemplate = t.optString("command_template", "").trim()
                    val jsCode = t.optString("js_code", "").trim()
                    if (jsCode.isNotBlank()) {
                        if (com.jarvisai.assistant.browser.mcp.BrowserMcp.isAutomationHalted(ctx)) {
                            return@withContext JSONObject()
                                .put("server", srv)
                                .put("tool", toolName)
                                .put("stopped_by_user", true)
                                .put("error", "Browser automation was stopped by the user.")
                        }
                        val evalRes = BrowserSessionManager.instance.evalJs(
                            "",
                            "(function(args){ $jsCode })(${args})"
                        ).getOrElse { it.message.orEmpty() }
                        return@withContext JSONObject()
                            .put("server", srv)
                            .put("tool", toolName)
                            .put("result", evalRes)
                    }
                    if (cmdTemplate.isNotBlank()) {
                        var resolvedCmd = cmdTemplate
                        val argKeys = args.keys()
                        val envPrefix = StringBuilder()
                        val homePath = LocalEnvironmentManager.getHomeDir(ctx).absolutePath
                        envPrefix.append("cd '$homePath' 2>/dev/null || true; ")
                        envPrefix.append("export MCP_ARGS_JSON='${args.toString().replace("'", "'\\''")}'; ")
                        while (argKeys.hasNext()) {
                            val k = argKeys.next()
                            val v = args.optString(k, "")
                            val safeEnvKey = k.uppercase().replace(Regex("[^A-Z0-9_]"), "_")
                            val escapedVal = v.replace("'", "'\\''")
                            envPrefix.append("export MCP_ARG_$safeEnvKey='$escapedVal'; ")
                            resolvedCmd = resolvedCmd.replace("{{$k}}", escapedVal).replace("\${$k}", escapedVal)
                        }
                        LocalTerminalBridge.instance.init(ctx)
                        val execRes = LocalTerminalBridge.instance.executeCommand(
                            "$envPrefix$resolvedCmd",
                            60_000L
                        )
                        return@withContext JSONObject()
                            .put("server", srv)
                            .put("tool", toolName)
                            .put("exitCode", execRes.exitCode)
                            .put("output", execRes.output)
                    }
                    return@withContext JSONObject()
                        .put("server", srv)
                        .put("tool", toolName)
                        .put("status", "ok")
                        .put("argumentsReceived", args)
                }
            }
        }
        null
    }
}
