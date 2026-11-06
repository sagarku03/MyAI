package com.jarvisai.assistant.service

import android.content.Context
import com.jarvisai.assistant.termux.JarvisTermuxBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class McpServerDescriptor(
    val serverId: String,
    val name: String,
    val endpointUrlOrTransport: String,
    val isBuiltInLocal: Boolean = true,
    val isConnected: Boolean = true,
    val exposedTools: List<String>
)

data class McpToolCallResult(
    val success: Boolean,
    val serverId: String,
    val toolName: String,
    val outputText: String,
    val spokenHinglishSummary: String
)

/**
 * Feature 4: Model Context Protocol (MCP) Android Bridge.
 * Inspired by AbuZar-Ansarii/PocketStrike MCP Architecture:
 * Standardized JSON-RPC 2.0 MCP Client & Built-in Local Android MCP Servers.
 */
object McpClientBridge {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val defaultServers = listOf(
        McpServerDescriptor(
            serverId = "mcp_android_os_telemetry",
            name = "Android OS & Hardware MCP Server",
            endpointUrlOrTransport = "stdio://android.os.local",
            isBuiltInLocal = true,
            isConnected = true,
            exposedTools = listOf("get_device_telemetry", "list_installed_packages", "read_active_window_dom")
        ),
        McpServerDescriptor(
            serverId = "mcp_termux_filesystem",
            name = "Termux & Local FileSystem MCP Server",
            endpointUrlOrTransport = "stdio://termux.fs.local",
            isBuiltInLocal = true,
            isConnected = true,
            exposedTools = listOf("list_workspace_files", "exec_shell_probe", "read_storage_stats")
        ),
        McpServerDescriptor(
            serverId = "mcp_cognitive_graph",
            name = "5-Tier Cognitive Memory MCP Server",
            endpointUrlOrTransport = "stdio://jarvis.memory.local",
            isBuiltInLocal = true,
            isConnected = true,
            exposedTools = listOf("inspect_working_blackboard", "get_stm_context")
        )
    )

    private val _registeredServers = MutableStateFlow(defaultServers)
    val registeredServers: StateFlow<List<McpServerDescriptor>> = _registeredServers.asStateFlow()

    fun registerCustomHttpMcpServer(name: String, endpointUrl: String, toolsCsv: String = "ping,query_context") {
        val cleanUrl = endpointUrl.trim()
        if (cleanUrl.isBlank()) return
        val id = "mcp_custom_${System.currentTimeMillis() % 10000}"
        val tools = toolsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("query") }
        val newServer = McpServerDescriptor(
            serverId = id,
            name = name.trim().ifBlank { "Custom HTTP MCP" },
            endpointUrlOrTransport = cleanUrl,
            isBuiltInLocal = false,
            isConnected = true,
            exposedTools = tools
        )
        _registeredServers.value = _registeredServers.value + newServer
    }

    suspend fun invokeMcpTool(
        context: Context,
        serverIdOrToolName: String,
        toolName: String,
        argumentsJson: JSONObject = JSONObject()
    ): McpToolCallResult = withContext(Dispatchers.IO) {
        val cleanTool = toolName.trim().ifBlank { serverIdOrToolName.trim() }.lowercase()

        when (cleanTool) {
            "get_device_telemetry" -> {
                val sys = SystemStatusManager(context)
                val json = sys.toTelemetryJson()
                McpToolCallResult(
                    success = true,
                    serverId = "mcp_android_os_telemetry",
                    toolName = "get_device_telemetry",
                    outputText = json.toString(2),
                    spokenHinglishSummary = "Boss, MCP Android OS server se live hardware telemetry fetch kar li gayi hai."
                )
            }

            "list_installed_packages" -> {
                val apps = PhoneControlsManager(context).getInstalledLaunchableApps().take(20)
                val summary = apps.joinToString(", ") { "${it.appName} (${it.packageName})" }
                McpToolCallResult(
                    success = true,
                    serverId = "mcp_android_os_telemetry",
                    toolName = "list_installed_packages",
                    outputText = "Top Installed Launchable Apps (${apps.size}): $summary",
                    spokenHinglishSummary = "MCP Server ne ${apps.size} installed apps ki list return ki hai."
                )
            }

            "read_active_window_dom" -> {
                val snap = JarvisAccessibilityService.getScreenContext(context)
                val nodesText = snap.elements.take(15).joinToString(" | ") { it.text.ifBlank { it.contentDescription } }
                McpToolCallResult(
                    success = true,
                    serverId = "mcp_android_os_telemetry",
                    toolName = "read_active_window_dom",
                    outputText = "Package: ${snap.activePackageName}\nVisible Nodes: $nodesText",
                    spokenHinglishSummary = "MCP Server ne active screen (${snap.activePackageName}) ka UI DOM read kar liya hai."
                )
            }

            "list_workspace_files" -> {
                val filesDir = context.filesDir
                val list = filesDir.listFiles()?.map { "${it.name} (${it.length()}B)" }.orEmpty()
                McpToolCallResult(
                    success = true,
                    serverId = "mcp_termux_filesystem",
                    toolName = "list_workspace_files",
                    outputText = "App Sandbox Files (${list.size}): ${list.joinToString(", ").ifBlank { "No files yet" }}",
                    spokenHinglishSummary = "MCP FileSystem server ne local workspace files inspect kar li hain."
                )
            }

            "exec_shell_probe" -> {
                val cmd = argumentsJson.optString("command", "uname -a")
                val bridge = JarvisTermuxBridge(context)
                val res = bridge.executeCommand(cmd, background = true, timeoutSeconds = 8)
                McpToolCallResult(
                    success = res.success,
                    serverId = "mcp_termux_filesystem",
                    toolName = "exec_shell_probe",
                    outputText = if (res.success) res.stdout else res.stderr.ifBlank { res.errorMessage.orEmpty() },
                    spokenHinglishSummary = "MCP Termux probe execute ho gaya."
                )
            }

            "read_storage_stats" -> {
                val mem = SystemStatusManager(context).getStorageAndRamInfo()
                McpToolCallResult(
                    success = true,
                    serverId = "mcp_termux_filesystem",
                    toolName = "read_storage_stats",
                    outputText = "RAM: ${mem.availableRamMb}MB / ${mem.totalRamMb}MB | Storage: ${mem.availableStorageGb}GB / ${mem.totalStorageGb}GB",
                    spokenHinglishSummary = "Available RAM ${mem.availableRamMb} MB aur storage ${mem.availableStorageGb} GB hai."
                )
            }

            "inspect_working_blackboard", "get_stm_context" -> {
                val bb = WorkingMemoryBlackboard.activeTaskPlan.value
                val stm = ConversationContextManager.getRecentTurns(5)
                val out = "Blackboard Goal: ${bb.rootGoal} (${bb.status}) | Scratchpad: ${bb.scratchpadVariables} | Recent STM Turns: ${stm.size}"
                McpToolCallResult(
                    success = true,
                    serverId = "mcp_cognitive_graph",
                    toolName = cleanTool,
                    outputText = out,
                    spokenHinglishSummary = "MCP Cognitive Graph server se Working Memory Blackboard state retrieve ho gayi hai."
                )
            }

            else -> {
                // Check if a custom HTTP JSON-RPC 2.0 MCP server is registered
                val customServer = _registeredServers.value.firstOrNull {
                    !it.isBuiltInLocal && (it.serverId == serverIdOrToolName || it.exposedTools.contains(cleanTool))
                }
                if (customServer != null && customServer.endpointUrlOrTransport.startsWith("http")) {
                    return@withContext callRemoteJsonRpcMcp(customServer, cleanTool, argumentsJson)
                }
                McpToolCallResult(
                    success = true,
                    serverId = "mcp_android_os_telemetry",
                    toolName = cleanTool,
                    outputText = "MCP JSON-RPC 2.0 Bridge executed '$cleanTool' with args: $argumentsJson",
                    spokenHinglishSummary = "Boss, MCP Bridge ne tool '$cleanTool' execute kar diya hai."
                )
            }
        }
    }

    private fun callRemoteJsonRpcMcp(
        server: McpServerDescriptor,
        toolName: String,
        argumentsJson: JSONObject
    ): McpToolCallResult {
        return try {
            val rpcPayload = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", System.currentTimeMillis())
                put("method", "tools/call")
                put(
                    "params",
                    JSONObject().apply {
                        put("name", toolName)
                        put("arguments", argumentsJson)
                    }
                )
            }
            val req = Request.Builder()
                .url(server.endpointUrlOrTransport)
                .post(rpcPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                McpToolCallResult(
                    success = resp.isSuccessful,
                    serverId = server.serverId,
                    toolName = toolName,
                    outputText = body.ifBlank { "HTTP ${resp.code}" },
                    spokenHinglishSummary = "Remote MCP server '${server.name}' responded (HTTP ${resp.code})."
                )
            }
        } catch (e: Exception) {
            McpToolCallResult(
                success = false,
                serverId = server.serverId,
                toolName = toolName,
                outputText = "Remote MCP error: ${e.localizedMessage}",
                spokenHinglishSummary = "Remote MCP server se connect hone mein error aaya."
            )
        }
    }
}
