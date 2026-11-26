package com.jarvisai.assistant.mcp

import android.content.Context
import android.util.Log
import com.jarvisai.assistant.data.remote.JarvisHubConfig
import com.jarvisai.assistant.mcp.server.AndroidLocalBridgeServer
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class McpToolInfo(
    val name: String,
    val description: String = "",
    val inputSchemaJson: String = """{"type":"object","properties":{}}"""
)

data class McpServerSpec(
    val serverName: String,
    val command: String = "",
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val serverUrl: String = "",
    val headers: Map<String, String> = emptyMap(),
    val disabled: Boolean = false
) {
    val isRemote: Boolean get() = serverUrl.isNotBlank()
}

data class McpServerState(
    val serverName: String,
    val status: String, // READY, DISABLED, ERROR, NEEDS_AUTH, STARTING
    val error: String? = null,
    val tools: List<McpToolInfo> = emptyList(),
    val spec: McpServerSpec
) {
    val isEnabled: Boolean get() = !spec.disabled
}

data class CascadeCatalogPlugin(
    val id: String,
    val title: String,
    val description: String,
    val trustLevel: String = "TRUSTED",
    val isRemote: Boolean = false,
    val serverUrl: String = "",
    val authHeaderName: String? = null,
    val command: String = "",
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val readme: String = "",
    val category: String = "Developer Tools"
)

/**
 * Complete on-device Model Context Protocol (MCP) Manager:
 * - Reads/writes `~/.gemini/jarvis-antigravity/mcp_config.json` (+ mirrors).
 * - Performs real HTTP/SSE and Stdio ProcessBuilder `initialize` + `tools/list` probing.
 * - Executes live `tools/call` RPCs against configured servers and exposes discovered tools
 *   to `AntigravityToolSuite`.
 */
object McpServersManager {

    private const val TAG = "McpServersManager"

    private val rpcClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val rpcIdCounter = AtomicInteger(1)

    private val _servers = MutableStateFlow<List<McpServerState>>(emptyList())
    val servers: StateFlow<List<McpServerState>> = _servers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    @Volatile
    private var lastContext: Context? = null

    val CASCADE_CATALOG: List<CascadeCatalogPlugin> by lazy {
        val localBase = "http://127.0.0.1:${JarvisHubConfig.ISOLATED_MCP_PORT}"
        val browserBase = JarvisHubConfig.getBrowserMcpUrl()
        listOf(
            CascadeCatalogPlugin(
                id = "agentra-android-mcp",
                title = "Agentra Android & Device Bridge",
                description = "Built-in on-device Accessibility UI control, PTY Terminal, Memory Blackboard & Notes.",
                trustLevel = "TRUSTED",
                isRemote = true,
                serverUrl = "$localBase/mcp",
                category = "Built-in Android",
                readme = "# Agentra Android MCP Bridge\nRuns natively inside Agentra on `127.0.0.1:${JarvisHubConfig.ISOLATED_MCP_PORT}/mcp`.\n\nIncludes Android device actions, shell PTY, working memory, browser tabs, and local notes."
            ),
            CascadeCatalogPlugin(
                id = "browser-cdp",
                title = "Chrome WebView CDP Bridge",
                description = "Autonomous in-app browser control: open URLs, read DOM, evaluate JS, inspect console logs.",
                trustLevel = "TRUSTED",
                isRemote = true,
                serverUrl = browserBase,
                category = "Built-in Android",
                readme = "# Browser CDP MCP\nConnects Cascade/Gemini directly to Agentra's embedded WebView tabs on port `${JarvisHubConfig.ISOLATED_BROWSER_MCP_PORT}`."
            ),
            CascadeCatalogPlugin(
                id = "firebase",
                title = "Firebase MCP Server",
                description = "Manage Firestore collections, Auth users, Crashlytics, and deploy rules with Firebase CLI MCP.",
                trustLevel = "TRUSTED",
                command = "npx",
                args = listOf("-y", "firebase-tools@latest", "mcp"),
                category = "Google Cloud",
                readme = "# Firebase MCP\nRequires Node.js in Local CLI Toolchain and Firebase login.\nProvides tools for Cloud Firestore, Authentication, Hosting, and Project metadata."
            ),
            CascadeCatalogPlugin(
                id = "stitch",
                title = "Google Stitch UI Design MCP",
                description = "Generate high-fidelity Stitch UI mockups and design tokens directly from prompts.",
                trustLevel = "TRUSTED",
                isRemote = true,
                serverUrl = "https://stitch.googleapis.com/mcp",
                authHeaderName = "X-Goog-Api-Key",
                category = "Google Design",
                readme = "# Google Stitch MCP\nRemote SSE/HTTP MCP endpoint (`https://stitch.googleapis.com/mcp`).\nAdd your `X-Goog-Api-Key` header in Edit Server after installing."
            ),
            CascadeCatalogPlugin(
                id = "github",
                title = "GitHub MCP Server",
                description = "Search repositories, read issues/PRs, create branches and inspect commits via GitHub API.",
                trustLevel = "TRUSTED",
                command = "npx",
                args = listOf("-y", "@modelcontextprotocol/server-github"),
                env = mapOf("GITHUB_PERSONAL_ACCESS_TOKEN" to ""),
                category = "Developer Tools",
                readme = "# GitHub MCP Server\nSet `GITHUB_PERSONAL_ACCESS_TOKEN` in env variables to inspect private repos and submit pull requests."
            ),
            CascadeCatalogPlugin(
                id = "filesystem",
                title = "Filesystem Workspace MCP",
                description = "Read, search, edit and scaffold files safely inside your Jarvis coding workspace.",
                trustLevel = "TRUSTED",
                isRemote = true,
                serverUrl = "$localBase/filesystem",
                category = "Core Utilities",
                readme = "# Filesystem MCP\nExposes directory listing, file reads/writes, and tree inspection inside configured workspace paths."
            ),
            CascadeCatalogPlugin(
                id = "context7",
                title = "Context7 Up-to-Date Docs",
                description = "Fetch version-exact API documentation and code snippets for Android, React, Python & Kotlin libs.",
                trustLevel = "TRUSTED",
                command = "npx",
                args = listOf("-y", "@upstash/context7-mcp@latest"),
                category = "Documentation",
                readme = "# Context7 MCP\nPulls live, up-to-date documentation directly into context so models stop hallucinating deprecated APIs."
            ),
            CascadeCatalogPlugin(
                id = "playwright",
                title = "Playwright Browser Automation",
                description = "Drive headless Chromium sessions via accessibility snapshots, network capture & form fills.",
                trustLevel = "TRUSTED",
                command = "npx",
                args = listOf("-y", "@playwright/mcp@latest"),
                category = "Testing & QA",
                readme = "# Playwright MCP\nStructured accessibility-tree browser automation for web testing and scraping."
            ),
            CascadeCatalogPlugin(
                id = "postgres",
                title = "PostgreSQL Read/Schema MCP",
                description = "Inspect PostgreSQL database schemas and execute read-only analytical queries.",
                trustLevel = "COMMUNITY",
                command = "npx",
                args = listOf("-y", "@modelcontextprotocol/server-postgres", "postgresql://localhost/mydb"),
                category = "Databases",
                readme = "# PostgreSQL MCP\nReplace the connection URI argument with your PostgreSQL database connection string."
            ),
            CascadeCatalogPlugin(
                id = "sqlite",
                title = "SQLite Inspector MCP",
                description = "Query local `.db` SQLite databases, inspect tables, and summarize schema relations.",
                trustLevel = "TRUSTED",
                command = "npx",
                args = listOf("-y", "@modelcontextprotocol/server-sqlite", "--db-path", "workspace.db"),
                category = "Databases",
                readme = "# SQLite MCP\nPoint `--db-path` to any SQLite file inside your project directory."
            ),
            CascadeCatalogPlugin(
                id = "memory",
                title = "Knowledge Graph Memory MCP",
                description = "Persistent entity-relation knowledge graph for long-term architectural notes.",
                trustLevel = "TRUSTED",
                command = "npx",
                args = listOf("-y", "@modelcontextprotocol/server-memory"),
                category = "Core Utilities",
                readme = "# Memory MCP\nStores knowledge graph entities and observations locally."
            ),
            CascadeCatalogPlugin(
                id = "brave-search",
                title = "Brave Search MCP",
                description = "Private web and news search API tailored for autonomous research workflows.",
                trustLevel = "COMMUNITY",
                command = "npx",
                args = listOf("-y", "@modelcontextprotocol/server-brave-search"),
                env = mapOf("BRAVE_API_KEY" to ""),
                category = "Search & Web",
                readme = "# Brave Search MCP\nRequires a `BRAVE_API_KEY` environment variable."
            )
        )
    }

    fun getPrimaryConfigFile(context: Context): File {
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        return File(homeDir, ".gemini/${JarvisHubConfig.ISOLATED_APP_DATA_DIR}/mcp_config.json")
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun loadConfigSpecs(context: Context): LinkedHashMap<String, McpServerSpec> {
        lastContext = context.applicationContext
        val primaryFile = getPrimaryConfigFile(context)
        if (!primaryFile.exists() || primaryFile.readText().isBlank()) {
            ensureDefaultConfigExists(primaryFile)
        }
        val raw = runCatching { primaryFile.readText() }.getOrDefault("{}")
        return parseMcpConfigJson(raw)
    }

    private fun ensureDefaultConfigExists(file: File) {
        val port = JarvisHubConfig.ISOLATED_MCP_PORT
        val browserPort = JarvisHubConfig.ISOLATED_BROWSER_MCP_PORT
        val stdioBridge = lastContext?.let {
            runCatching { LocalEnvironmentManager.ensureMcpStdioBridgeScript(it).absolutePath }.getOrNull()
        }
        fun buildLocalBridgeEntry(url: String): JSONObject = JSONObject().apply {
            if (!stdioBridge.isNullOrBlank()) {
                put("command", "/system/bin/sh")
                put("args", JSONArray(listOf(stdioBridge, url)))
            }
            put("serverUrl", url)
            put("url", url)
            put("transport", "http")
        }
        val defaults = JSONObject().put(
            "mcpServers",
            JSONObject()
                .put("jarvis-android-mcp", buildLocalBridgeEntry("http://127.0.0.1:$port/mcp"))
                .put("jarvis-browser-mcp", buildLocalBridgeEntry("http://127.0.0.1:$browserPort/mcp/browser"))
                .put("filesystem", buildLocalBridgeEntry("http://127.0.0.1:$port/filesystem"))
        )
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(defaults.toString(2))
        }
    }

    fun parseMcpConfigJson(rawJson: String): LinkedHashMap<String, McpServerSpec> {
        val out = LinkedHashMap<String, McpServerSpec>()
        val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return out
        val serversObj = root.optJSONObject("mcpServers") ?: root
        val keys = serversObj.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val entry = serversObj.optJSONObject(name) ?: continue
            val argsList = mutableListOf<String>()
            entry.optJSONArray("args")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotEmpty() }?.let { argsList.add(it) }
                }
            }
            val envMap = linkedMapOf<String, String>()
            entry.optJSONObject("env")?.let { envObj ->
                val eKeys = envObj.keys()
                while (eKeys.hasNext()) {
                    val k = eKeys.next()
                    envMap[k] = envObj.optString(k, "")
                }
            }
            val headersMap = linkedMapOf<String, String>()
            entry.optJSONObject("headers")?.let { hObj ->
                val hKeys = hObj.keys()
                while (hKeys.hasNext()) {
                    val k = hKeys.next()
                    headersMap[k] = hObj.optString(k, "")
                }
            }
            val url = entry.optString("serverUrl", entry.optString("url", "")).trim()
            val command = entry.optString("command", "").trim()
            val disabled = entry.optBoolean("disabled", false)
            out[name] = McpServerSpec(
                serverName = name,
                command = command,
                args = argsList,
                env = envMap,
                serverUrl = url,
                headers = headersMap,
                disabled = disabled
            )
        }
        return out
    }

    fun writeSpecsToDisk(context: Context, specs: Map<String, McpServerSpec>) {
        val stdioBridge = runCatching { LocalEnvironmentManager.ensureMcpStdioBridgeScript(context).absolutePath }.getOrNull()
        val serversObj = JSONObject()
        specs.forEach { (name, spec) ->
            val obj = JSONObject().apply {
                if (spec.isRemote) {
                    put("serverUrl", spec.serverUrl)
                    put("url", spec.serverUrl)
                    put("transport", "http")
                    if (spec.command.isNotBlank()) {
                        put("command", spec.command)
                        put("args", JSONArray(spec.args))
                    } else if (!stdioBridge.isNullOrBlank() && (spec.serverUrl.startsWith("http://127.0.0.1:") || spec.serverUrl.startsWith("http://localhost:"))) {
                        put("command", "/system/bin/sh")
                        put("args", JSONArray(listOf(stdioBridge, spec.serverUrl)))
                    }
                    if (spec.headers.isNotEmpty()) {
                        put("headers", JSONObject().apply {
                            spec.headers.forEach { (k, v) -> put(k, v) }
                        })
                    }
                } else {
                    put("command", spec.command)
                    put("args", JSONArray(spec.args))
                    if (spec.env.isNotEmpty()) {
                        put("env", JSONObject().apply {
                            spec.env.forEach { (k, v) -> put(k, v) }
                        })
                    }
                }
                if (spec.disabled) {
                    put("disabled", true)
                }
            }
            serversObj.put(name, obj)
        }
        val content = JSONObject().put("mcpServers", serversObj).toString(2)
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        val dataDirName = JarvisHubConfig.ISOLATED_APP_DATA_DIR
        val targets = buildList {
            add(File(homeDir, ".gemini/$dataDirName/mcp_config.json"))
            add(File(homeDir, ".gemini/antigravity/mcp_config.json"))
            add(File(homeDir, ".gemini/mcp_config.json"))
            add(File(context.filesDir, "home/.gemini/$dataDirName/mcp_config.json"))
            add(File(context.filesDir, "home/.gemini/antigravity/mcp_config.json"))
            add(File(context.filesDir, "home/.gemini/mcp_config.json"))
            add(File(homeDir, ".agentra/mcp_config.json"))
            add(File(homeDir, ".claude/mcp_servers.json"))
            if (LocalEnvironmentManager.isTermuxFlavor(context)) {
                add(File("/data/data/com.termux/files/home/.gemini/$dataDirName/mcp_config.json"))
            }
        }.distinctBy { it.absolutePath }
        targets.forEach { f ->
            runCatching {
                f.parentFile?.mkdirs()
                f.writeText(content)
            }
        }
    }

    suspend fun refreshAll(context: Context): List<McpServerState> = withContext(Dispatchers.IO) {
        lastContext = context.applicationContext
        _isLoading.value = true
        _error.value = null

        AndroidLocalBridgeServer.instance.start(context.applicationContext)
        val specs = loadConfigSpecs(context)

        // Upgrade legacy broken npx filesystem spec to native embedded /filesystem bridge automatically
        val fsSpec = specs["filesystem"]
        if (fsSpec != null && fsSpec.serverUrl.isBlank() && fsSpec.command == "npx") {
            val fsUrl = "http://127.0.0.1:${JarvisHubConfig.ISOLATED_MCP_PORT}/filesystem"
            val stdioPath = runCatching { LocalEnvironmentManager.ensureMcpStdioBridgeScript(context).absolutePath }.getOrDefault("")
            specs["filesystem"] = fsSpec.copy(
                command = if (stdioPath.isNotBlank()) "/system/bin/sh" else fsSpec.command,
                args = if (stdioPath.isNotBlank()) listOf(stdioPath, fsUrl) else fsSpec.args,
                serverUrl = fsUrl
            )
            writeSpecsToDisk(context, specs)
        }

        val currentByName = _servers.value.associateBy { it.serverName }
        val results = specs.values.map { spec ->
            if (spec.disabled) {
                McpServerState(
                    serverName = spec.serverName,
                    status = "DISABLED",
                    error = null,
                    tools = emptyList(),
                    spec = spec
                )
            } else {
                probeServer(context, spec, previous = currentByName[spec.serverName])
            }
        }
        _servers.value = results
        _isLoading.value = false
        results
    }

    fun ensureServersLoadedAndProbed(context: Context? = lastContext): List<McpServerState> {
        val ctx = context?.applicationContext ?: lastContext
        if (ctx != null) {
            AndroidLocalBridgeServer.instance.start(ctx)
        }
        val current = _servers.value
        if (current.isNotEmpty() && current.any { it.tools.isNotEmpty() }) {
            return current
        }
        if (ctx == null) return current
        val specs = loadConfigSpecs(ctx)
        val populated = specs.values.map { spec ->
            if (spec.disabled) {
                McpServerState(spec.serverName, "DISABLED", null, emptyList(), spec)
            } else {
                val immediateTools = resolveEmbeddedToolsImmediate(spec)
                if (immediateTools.isNotEmpty()) {
                    McpServerState(spec.serverName, "READY", null, immediateTools, spec)
                } else {
                    probeServer(ctx, spec)
                }
            }
        }
        _servers.value = populated
        return populated
    }

    private fun resolveEmbeddedToolsImmediate(spec: McpServerSpec): List<McpToolInfo> {
        val url = spec.serverUrl.trim()
        val isLocalEmbedded = url.startsWith("http://127.0.0.1:${JarvisHubConfig.ISOLATED_MCP_PORT}") ||
            url.startsWith("http://127.0.0.1:${JarvisHubConfig.ISOLATED_BROWSER_MCP_PORT}") ||
            spec.serverName in setOf("agentra-android-mcp", "agentra-browser-mcp", "jarvis-android-mcp", "jarvis-browser-mcp", "filesystem", "jarvis_mcp", "browser_mcp", "android_mcp")
        if (!isLocalEmbedded) return emptyList()

        val path = when {
            url.isNotBlank() -> "/" + url.substringAfter("://").substringAfter("/", "mcp")
            spec.serverName.contains("browser") -> "/mcp/browser"
            spec.serverName == "filesystem" -> "/filesystem"
            else -> "/mcp"
        }
        val rawResp = AndroidLocalBridgeServer.instance.processRpcJson(
            path,
            """{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}"""
        ) ?: return emptyList()
        val toolsArr = runCatching {
            JSONObject(rawResp).optJSONObject("result")?.optJSONArray("tools")
        }.getOrNull() ?: return emptyList()
        return parseToolsArray(toolsArr)
    }

    fun notifyLocalAgyDaemonRefresh(context: Context? = lastContext) {
        val ctx = context ?: lastContext ?: return
        val hubUrl = JarvisHubConfig.getLanguageServiceUrl(ctx)
        val specs = loadConfigSpecs(ctx).values.filter { !it.disabled }
        val mcpArr = JSONArray()
        specs.forEach { s ->
            mcpArr.put(
                JSONObject().apply {
                    put("serverName", s.serverName)
                    put("server_name", s.serverName)
                    if (s.serverUrl.isNotBlank()) {
                        put("serverUrl", s.serverUrl)
                        put("server_url", s.serverUrl)
                    }
                    if (s.command.isNotBlank()) {
                        put("command", s.command)
                        if (s.args.isNotEmpty()) put("args", JSONArray(s.args))
                    }
                }
            )
        }
        val payload = JSONObject().put("mcpServers", mcpArr).toString()
        Thread {
            runCatching {
                val req = Request.Builder()
                    .url("$hubUrl/RefreshMcpServers")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
                rpcClient.newCall(req).execute().close()
            }
        }.start()
    }

    fun buildLiveMcpStatusPromptSection(context: Context? = lastContext, compact: Boolean = false): String {
        val states = ensureServersLoadedAndProbed(context)
        val activeStates = states.filter { it.isEnabled && (it.status == "READY" || it.tools.isNotEmpty()) }
        if (activeStates.isEmpty()) return ""
        if (compact) {
            return buildString {
                appendLine("<mcp_servers>")
                appendLine("Connected MCP servers (${activeStates.size}, ${activeStates.sumOf { it.tools.size }} tools): ${activeStates.joinToString(", ") { "${it.serverName} (${it.tools.size})" }}.")
                append("Use `mcp_call_tool` / `mcp_create_server` / `agentra-mcp` whenever device, browser, or custom MCP tools are needed.")
                appendLine("</mcp_servers>")
            }.trim()
        }
        val configPath = (context ?: lastContext)?.let { getPrimaryConfigFile(it).absolutePath }
            ?: "~/.gemini/agentra-antigravity/mcp_config.json"

        return buildString {
            appendLine("<mcp_servers>")
            appendLine("## Active MCP Servers & Live Tool Registry (CONNECTED & ONLINE IN THIS SESSION)")
            appendLine("- **Active Connected MCP Servers**: ${activeStates.size} (${activeStates.joinToString(", ") { "`${it.serverName}`" }})")
            appendLine("- **Total Active MCP Tools**: ${activeStates.sumOf { it.tools.size }}")
            appendLine("- **Config File**: `$configPath`")
            appendLine()
            activeStates.forEachIndexed { idx, st ->
                val endpointDesc = if (st.spec.isRemote) st.spec.serverUrl else "${st.spec.command} ${st.spec.args.joinToString(" ")}".trim()
                val toolNames = st.tools.map { "`${it.name}`" }.joinToString(", ")
                appendLine("${idx + 1}. **`${st.serverName}`** — Status: **CONNECTED (`READY`)** | Endpoint: `$endpointDesc` | Tools (${st.tools.size}): $toolNames")
            }
            appendLine()
            appendLine("### How to Use & Author MCP Servers, Plugins/Extensions, and Skills:")
            appendLine("- **Call Any MCP Tool**: Invoke `mcp__<server>__<tool>` or `mcp_call_tool(server_name, tool_name, arguments)`, OR via `run_command` execute `agentra-mcp call <server_name> <tool_name> '<json_args>'` (e.g. `agentra-mcp status`).")
            appendLine("- **Create & Add a New MCP Server / Custom MCP Tool**: Call `mcp_create_server` OR run `agentra-mcp create-server '{\"server_name\":\"my-mcp\",\"description\":\"...\",\"custom_tools\":[{\"name\":\"my_tool\",\"description\":\"...\",\"command_template\":\"...\"}]}'`. It immediately registers in `mcp_config.json` and hot-reloads as `READY`!")
            appendLine("- **Create & Add a New Plugin / Chrome Browser Extension**: Call `plugin_create_or_update` OR run `agentra-mcp create-plugin '{\"id\":\"my-plugin\",\"name\":\"My Plugin\",\"description\":\"...\",\"popup_html\":\"...\",\"service_worker_js\":\"...\"}'`.")
            appendLine("- **Create & Add a New Agent Skill (`SKILL.md`)**: Call `skill_create_or_update` OR run `agentra-mcp create-skill '{\"name\":\"my-skill\",\"display_name\":\"My Skill\",\"description\":\"...\",\"content\":\"# Instructions...\"}'`.")
            appendLine("</mcp_servers>")
        }.trim()
    }

    suspend fun refreshSingleServer(context: Context, serverName: String): McpServerState? = withContext(Dispatchers.IO) {
        lastContext = context.applicationContext
        val specs = loadConfigSpecs(context)
        val spec = specs[serverName] ?: return@withContext null
        val updated = if (spec.disabled) {
            McpServerState(serverName, "DISABLED", null, emptyList(), spec)
        } else {
            probeServer(context, spec, previous = _servers.value.firstOrNull { it.serverName == serverName })
        }
        _servers.value = _servers.value.map { if (it.serverName == serverName) updated else it }
            .ifEmpty { listOf(updated) }
        updated
    }

    suspend fun upsertServer(
        context: Context,
        spec: McpServerSpec,
        originalName: String? = null
    ): Result<McpServerState> = withContext(Dispatchers.IO) {
        runCatching {
            require(spec.serverName.isNotBlank()) { "Server name cannot be blank" }
            require(spec.isRemote || spec.command.isNotBlank()) { "Provide a command or server URL" }

            val specs = loadConfigSpecs(context)
            if (!originalName.isNullOrBlank() && originalName != spec.serverName) {
                specs.remove(originalName)
            }
            specs[spec.serverName] = spec
            writeSpecsToDisk(context, specs)

            if (spec.isRemote && spec.serverUrl.contains("127.0.0.1")) {
                AndroidLocalBridgeServer.instance.start(context.applicationContext)
            }

            val probed = if (spec.disabled) {
                McpServerState(spec.serverName, "DISABLED", null, emptyList(), spec)
            } else {
                probeServer(context, spec)
            }
            val list = specs.values.map { s ->
                if (s.serverName == spec.serverName) probed
                else _servers.value.firstOrNull { it.serverName == s.serverName }
                    ?: McpServerState(s.serverName, if (s.disabled) "DISABLED" else "STARTING", null, emptyList(), s)
            }
            _servers.value = list
            _statusMessage.value = "Saved '${spec.serverName}' (${probed.tools.size} tools)"
            probed
        }
    }

    suspend fun upsertFromRawJson(
        context: Context,
        serverName: String,
        rawJson: String,
        originalName: String? = null
    ): Result<McpServerState> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanName = serverName.trim()
            require(cleanName.isNotBlank()) { "Server name is required" }
            val obj = JSONObject(rawJson)
            val parsedMap = parseMcpConfigJson(JSONObject().put("mcpServers", JSONObject().put(cleanName, obj)).toString())
            val spec = parsedMap[cleanName] ?: error("Invalid MCP configuration JSON")
            upsertServer(context, spec, originalName).getOrThrow()
        }
    }

    suspend fun toggleServer(context: Context, serverName: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val specs = loadConfigSpecs(context)
        val existing = specs[serverName] ?: return@withContext
        val updated = existing.copy(disabled = !enabled)
        specs[serverName] = updated
        writeSpecsToDisk(context, specs)

        val newState = if (!enabled) {
            McpServerState(serverName, "DISABLED", null, emptyList(), updated)
        } else {
            probeServer(context, updated)
        }
        _servers.value = _servers.value.map { if (it.serverName == serverName) newState else it }
        _statusMessage.value = if (enabled) "Enabled '$serverName' (${newState.tools.size} tools)" else "Disabled '$serverName'"
    }

    suspend fun deleteServer(context: Context, serverName: String) = withContext(Dispatchers.IO) {
        val specs = loadConfigSpecs(context)
        specs.remove(serverName)
        writeSpecsToDisk(context, specs)
        _servers.value = _servers.value.filterNot { it.serverName == serverName }
        _statusMessage.value = "Removed '$serverName'"
    }

    suspend fun installCatalogPlugin(context: Context, plugin: CascadeCatalogPlugin): Result<McpServerState> {
        val spec = McpServerSpec(
            serverName = plugin.id,
            command = if (plugin.isRemote) "" else plugin.command,
            args = if (plugin.isRemote) emptyList() else plugin.args,
            env = plugin.env,
            serverUrl = if (plugin.isRemote) plugin.serverUrl else "",
            headers = if (plugin.authHeaderName != null) mapOf(plugin.authHeaderName to "") else emptyMap(),
            disabled = false
        )
        return upsertServer(context, spec)
    }

    suspend fun callMcpTool(
        context: Context,
        serverName: String,
        toolName: String,
        argumentsJson: JSONObject = JSONObject()
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        lastContext = context.applicationContext
        val spec = loadConfigSpecs(context)[serverName]
            ?: _servers.value.firstOrNull { it.serverName == serverName }?.spec
            ?: return@withContext Result.failure(IllegalArgumentException("MCP server '$serverName' is not configured"))

        if (spec.disabled) {
            return@withContext Result.failure(IllegalStateException("MCP server '$serverName' is disabled"))
        }

        if (spec.isRemote) {
            callRemoteJsonRpcTool(spec, toolName, argumentsJson)
        } else {
            callStdioJsonRpcTool(context, spec, toolName, argumentsJson)
        }
    }

    fun getDynamicToolDeclarations(context: Context? = lastContext): List<JSONObject> {
        val activeServers = ensureServersLoadedAndProbed(context)
        val list = mutableListOf<JSONObject>()
        val seenFnNames = mutableSetOf<String>()
        for (srv in activeServers) {
            if (!srv.isEnabled || srv.status == "ERROR") continue
            for (tool in srv.tools) {
                val safeSrv = srv.serverName.replace(Regex("[^a-zA-Z0-9_]"), "_").take(22)
                val safeTool = tool.name.replace(Regex("[^a-zA-Z0-9_]"), "_").take(36)
                val fnName = "mcp__${safeSrv}__$safeTool"
                if (!seenFnNames.add(fnName)) continue

                val schemaObj = runCatching { JSONObject(tool.inputSchemaJson) }.getOrNull()
                val props = schemaObj?.optJSONObject("properties") ?: JSONObject()
                val reqArr = schemaObj?.optJSONArray("required")

                val paramsObj = JSONObject()
                    .put("type", "OBJECT")
                    .put("properties", normalizeSchemaProperties(props))
                if (reqArr != null && reqArr.length() > 0) {
                    paramsObj.put("required", reqArr)
                }

                list.add(
                    JSONObject()
                        .put("name", fnName)
                        .put("description", "[MCP:${srv.serverName}] ${tool.description.ifBlank { tool.name }}")
                        .put("parameters", paramsObj)
                )
            }
        }
        return list
    }

    fun resolveDynamicToolTarget(fnName: String): Pair<String, String>? {
        if (!fnName.startsWith("mcp__")) return null
        val rest = fnName.removePrefix("mcp__")
        val sep = rest.indexOf("__")
        if (sep <= 0) return null
        val srvPart = rest.substring(0, sep)
        val toolPart = rest.substring(sep + 2)
        for (srv in _servers.value) {
            val safeSrv = srv.serverName.replace(Regex("[^a-zA-Z0-9_]"), "_").take(22)
            if (safeSrv == srvPart) {
                val matchedTool = srv.tools.firstOrNull {
                    it.name.replace(Regex("[^a-zA-Z0-9_]"), "_").take(36) == toolPart
                }
                if (matchedTool != null) {
                    return srv.serverName to matchedTool.name
                }
            }
        }
        return srvPart to toolPart
    }

    private fun normalizeSchemaProperties(rawProps: JSONObject): JSONObject {
        val out = JSONObject()
        val keys = rawProps.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val propObj = rawProps.optJSONObject(k) ?: continue
            val norm = JSONObject()
            norm.put("type", propObj.optString("type", "STRING").uppercase())
            if (propObj.has("description")) norm.put("description", propObj.optString("description"))
            out.put(k, norm)
        }
        return out
    }

    private fun probeServer(
        context: Context,
        spec: McpServerSpec,
        previous: McpServerState? = null
    ): McpServerState {
        return if (spec.isRemote) {
            probeRemoteHttpServer(spec)
        } else {
            probeStdioServer(context, spec, previous)
        }
    }

    private fun probeRemoteHttpServer(spec: McpServerSpec): McpServerState {
        if (spec.headers.any { (k, v) -> k.contains("key", ignoreCase = true) && v.isBlank() }) {
            return McpServerState(
                serverName = spec.serverName,
                status = "NEEDS_AUTH",
                error = "API key header value is empty. Tap Edit Server to supply credentials.",
                tools = emptyList(),
                spec = spec
            )
        }
        val immediateTools = resolveEmbeddedToolsImmediate(spec)
        if (immediateTools.isNotEmpty()) {
            return McpServerState(
                serverName = spec.serverName,
                status = "READY",
                error = null,
                tools = immediateTools,
                spec = spec
            )
        }
        return runCatching {
            val initPayload = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", rpcIdCounter.getAndIncrement())
                put("method", "initialize")
                put(
                    "params",
                    JSONObject()
                        .put("protocolVersion", "2024-11-05")
                        .put("capabilities", JSONObject())
                        .put("clientInfo", JSONObject().put("name", "Agentra").put("version", "4.0"))
                )
            }
            postRpcExpectingJson(spec, initPayload)

            val listPayload = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", rpcIdCounter.getAndIncrement())
                put("method", "tools/list")
                put("params", JSONObject())
            }
            val listResp = postRpcExpectingJson(spec, listPayload)
            if (listResp.has("error")) {
                val errMsg = listResp.optJSONObject("error")?.optString("message", "tools/list error") ?: "RPC error"
                return McpServerState(spec.serverName, "ERROR", errMsg, emptyList(), spec)
            }
            val toolsArr = listResp.optJSONObject("result")?.optJSONArray("tools") ?: JSONArray()
            val tools = parseToolsArray(toolsArr)
            McpServerState(
                serverName = spec.serverName,
                status = "READY",
                error = null,
                tools = tools,
                spec = spec
            )
        }.getOrElse { err ->
            val msg = err.message ?: "Connection failed"
            val authNeeded = msg.contains("401") || msg.contains("403") || msg.contains("unauthorized", ignoreCase = true)
            McpServerState(
                serverName = spec.serverName,
                status = if (authNeeded) "NEEDS_AUTH" else "ERROR",
                error = msg,
                tools = emptyList(),
                spec = spec
            )
        }
    }

    private fun postRpcExpectingJson(spec: McpServerSpec, payload: JSONObject): JSONObject {
        val builder = Request.Builder()
            .url(spec.serverUrl)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
        spec.headers.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) builder.header(k, v)
        }
        val req = builder
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        rpcClient.newCall(req).execute().use { resp ->
            val rawBody = resp.body?.string().orEmpty().trim()
            if (!resp.isSuccessful) {
                error("HTTP ${resp.code}: ${rawBody.take(220).ifBlank { resp.message }}")
            }
            if (rawBody.isBlank()) {
                return JSONObject()
            }
            val jsonCandidate = if (rawBody.startsWith("{")) {
                rawBody
            } else {
                rawBody.lines()
                    .filter { it.startsWith("data:") }
                    .map { it.removePrefix("data:").trim() }
                    .lastOrNull { it.startsWith("{") }
                    ?: rawBody
            }
            return JSONObject(jsonCandidate)
        }
    }

    private fun callRemoteJsonRpcTool(
        spec: McpServerSpec,
        toolName: String,
        argumentsJson: JSONObject
    ): Result<JSONObject> {
        return runCatching {
            val payload = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", rpcIdCounter.getAndIncrement())
                put("method", "tools/call")
                put(
                    "params",
                    JSONObject()
                        .put("name", toolName)
                        .put("arguments", argumentsJson)
                )
            }
            val resp = postRpcExpectingJson(spec, payload)
            if (resp.has("error")) {
                val errMsg = resp.optJSONObject("error")?.optString("message", "Tool execution failed") ?: "MCP error"
                error(errMsg)
            }
            resp.optJSONObject("result") ?: resp
        }
    }

    private fun resolveExecutableCommand(context: Context, rawCmd: String): String {
        val cmd = rawCmd.trim()
        if (cmd.startsWith("/") && File(cmd).exists()) return cmd
        val prefixBin = File(LocalEnvironmentManager.getPrefixDir(context), "bin/$cmd")
        if (prefixBin.exists() && prefixBin.canExecute()) return prefixBin.absolutePath
        val termuxBin = File("/data/data/com.termux/files/usr/bin/$cmd")
        if (termuxBin.exists() && termuxBin.canExecute()) return termuxBin.absolutePath
        return cmd
    }

    private fun buildStdioProcessBuilder(context: Context, spec: McpServerSpec): ProcessBuilder {
        val resolvedCmd = resolveExecutableCommand(context, spec.command)
        val cmdLine = listOf(resolvedCmd) + spec.args
        val pb = ProcessBuilder(cmdLine)
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        if (homeDir.exists()) pb.directory(homeDir)
        val env = pb.environment()
        val prefixBin = File(LocalEnvironmentManager.getPrefixDir(context), "bin").absolutePath
        val termuxBin = "/data/data/com.termux/files/usr/bin"
        val existingPath = env["PATH"].orEmpty()
        env["PATH"] = "$prefixBin:$termuxBin:$existingPath"
        env["HOME"] = homeDir.absolutePath
        spec.env.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) env[k] = v
        }
        return pb
    }

    private fun probeStdioServer(
        context: Context,
        spec: McpServerSpec,
        previous: McpServerState?
    ): McpServerState {
        return runCatching {
            val resolvedCmd = resolveExecutableCommand(context, spec.command)
            if (!resolvedCmd.startsWith("/") && !isCommandInSystemPath(resolvedCmd)) {
                return McpServerState(
                    serverName = spec.serverName,
                    status = "ERROR",
                    error = "Executable '${spec.command}' not found on device. Install Node.js/Python in Local CLI Toolchain or check command path.",
                    tools = previous?.tools ?: emptyList(),
                    spec = spec
                )
            }
            val pb = buildStdioProcessBuilder(context, spec)
            val proc = pb.start()
            try {
                val writer = BufferedWriter(OutputStreamWriter(proc.outputStream, Charsets.UTF_8))
                val reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8))

                val initId = rpcIdCounter.getAndIncrement()
                val initReq = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", initId)
                    .put("method", "initialize")
                    .put("params", JSONObject()
                        .put("protocolVersion", "2024-11-05")
                        .put("capabilities", JSONObject())
                        .put("clientInfo", JSONObject().put("name", "Agentra").put("version", "4.0")))
                writer.write(initReq.toString())
                writer.newLine()
                writer.flush()

                readJsonRpcLineWithTimeout(reader, initId, timeoutMs = 5000L)

                val notif = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("method", "notifications/initialized")
                writer.write(notif.toString())
                writer.newLine()

                val listId = rpcIdCounter.getAndIncrement()
                val listReq = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", listId)
                    .put("method", "tools/list")
                    .put("params", JSONObject())
                writer.write(listReq.toString())
                writer.newLine()
                writer.flush()

                val listResp = readJsonRpcLineWithTimeout(reader, listId, timeoutMs = 6000L)
                    ?: error("Stdio MCP process timed out waiting for tools/list response")

                val toolsArr = listResp.optJSONObject("result")?.optJSONArray("tools") ?: JSONArray()
                val tools = parseToolsArray(toolsArr)
                McpServerState(
                    serverName = spec.serverName,
                    status = "READY",
                    error = null,
                    tools = tools,
                    spec = spec
                )
            } finally {
                runCatching { proc.destroyForcibly() }
            }
        }.getOrElse { err ->
            Log.w(TAG, "Stdio probe failed for ${spec.serverName}: ${err.message}")
            McpServerState(
                serverName = spec.serverName,
                status = "ERROR",
                error = err.message ?: "Stdio launch failed",
                tools = emptyList(),
                spec = spec
            )
        }
    }

    private fun callStdioJsonRpcTool(
        context: Context,
        spec: McpServerSpec,
        toolName: String,
        argumentsJson: JSONObject
    ): Result<JSONObject> {
        return runCatching {
            val pb = buildStdioProcessBuilder(context, spec)
            val proc = pb.start()
            try {
                val writer = BufferedWriter(OutputStreamWriter(proc.outputStream, Charsets.UTF_8))
                val reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8))

                val initId = rpcIdCounter.getAndIncrement()
                writer.write(
                    JSONObject()
                        .put("jsonrpc", "2.0")
                        .put("id", initId)
                        .put("method", "initialize")
                        .put("params", JSONObject()
                            .put("protocolVersion", "2024-11-05")
                            .put("capabilities", JSONObject())
                            .put("clientInfo", JSONObject().put("name", "Agentra").put("version", "4.0")))
                        .toString()
                )
                writer.newLine()
                writer.flush()
                readJsonRpcLineWithTimeout(reader, initId, 5000L)

                writer.write(JSONObject().put("jsonrpc", "2.0").put("method", "notifications/initialized").toString())
                writer.newLine()

                val callId = rpcIdCounter.getAndIncrement()
                writer.write(
                    JSONObject()
                        .put("jsonrpc", "2.0")
                        .put("id", callId)
                        .put("method", "tools/call")
                        .put("params", JSONObject().put("name", toolName).put("arguments", argumentsJson))
                        .toString()
                )
                writer.newLine()
                writer.flush()

                val callResp = readJsonRpcLineWithTimeout(reader, callId, 15000L)
                    ?: error("Stdio MCP tool call timed out")
                if (callResp.has("error")) {
                    error(callResp.optJSONObject("error")?.optString("message", "Stdio tool error") ?: "Stdio error")
                }
                callResp.optJSONObject("result") ?: callResp
            } finally {
                runCatching { proc.destroyForcibly() }
            }
        }
    }

    private fun readJsonRpcLineWithTimeout(
        reader: BufferedReader,
        expectedId: Int,
        timeoutMs: Long
    ): JSONObject? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (reader.ready()) {
                val line = reader.readLine()?.trim() ?: return null
                if (line.startsWith("{")) {
                    val obj = runCatching { JSONObject(line) }.getOrNull()
                    if (obj != null && (obj.optInt("id", -1) == expectedId || obj.has("result") || obj.has("error"))) {
                        return obj
                    }
                }
            } else {
                Thread.sleep(30)
            }
        }
        return null
    }

    private fun isCommandInSystemPath(cmd: String): Boolean {
        val pathDirs = System.getenv("PATH")?.split(File.pathSeparator).orEmpty()
        return pathDirs.any { dir ->
            val f = File(dir, cmd)
            f.exists() && f.canExecute()
        }
    }

    private fun parseToolsArray(arr: JSONArray): List<McpToolInfo> {
        val list = mutableListOf<McpToolInfo>()
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            val name = t.optString("name").trim()
            if (name.isBlank()) continue
            val desc = t.optString("description", "")
            val schema = t.optJSONObject("inputSchema")?.toString() ?: """{"type":"object","properties":{}}"""
            list.add(McpToolInfo(name = name, description = desc, inputSchemaJson = schema))
        }
        return list
    }
}
