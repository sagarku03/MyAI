package com.jarvisai.assistant.mcp.server

import android.content.Context
import android.util.Log
import com.jarvisai.assistant.browser.BrowserSessionManager
import com.jarvisai.assistant.browser.PluginRepositories
import com.jarvisai.assistant.extension.BrowserExtensionManager
import com.jarvisai.assistant.mcp.bridges.AndroidDeviceMcpBridge
import com.jarvisai.assistant.mcp.bridges.BrowserMcpBridge
import com.jarvisai.assistant.mcp.bridges.FlowMcpBridge
import com.jarvisai.assistant.mcp.bridges.JarvisMemoryMcpBridge
import com.jarvisai.assistant.mcp.bridges.McpEcosystemBridge
import com.jarvisai.assistant.mcp.bridges.NotesMcpBridge
import com.jarvisai.assistant.mcp.bridges.TerminalMcpBridge
import com.jarvisai.assistant.mcp.bridges.ThemeMcpBridge
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class AndroidLocalBridgeServer private constructor() {

    companion object {
        private const val TAG = "LocalBridgeServer"
        val instance: AndroidLocalBridgeServer by lazy { AndroidLocalBridgeServer() }
    }

    private data class SseSession(
        val sessionId: String,
        val path: String,
        val output: OutputStream
    )

    private var serverSocket: ServerSocket? = null
    private var secondaryServerSocket: ServerSocket? = null
    @Volatile
    private var isRunning = false
    @Volatile
    private var appContext: Context? = null
    private val threadPool = Executors.newCachedThreadPool()
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sseSessions = ConcurrentHashMap<String, SseSession>()

    internal fun attachContextForTest(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        NotesMcpBridge.init(ctx)
        ThemeMcpBridge.init(ctx)
        McpEcosystemBridge.init(ctx)
    }

    fun start(context: Context, port: Int = com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_MCP_PORT) {
        val ctx = context.applicationContext
        appContext = ctx
        NotesMcpBridge.init(ctx)
        ThemeMcpBridge.init(ctx)
        McpEcosystemBridge.init(ctx)
        BrowserSessionManager.instance.init(ctx)
        BrowserExtensionManager.init(ctx)
        runCatching {
            com.jarvisai.assistant.terminal.LocalEnvironmentManager.ensureMcpStdioBridgeScript(ctx)
            com.jarvisai.assistant.terminal.LocalEnvironmentManager.ensureJarvisMcpCliScript(ctx)
        }
        if (isRunning && serverSocket != null && !serverSocket!!.isClosed) return
        isRunning = true

        val browserPort = com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_BROWSER_MCP_PORT

        serverScope.launch {
            val bindAddr = runCatching { InetAddress.getByName("127.0.0.1") }.getOrNull()
            try {
                serverSocket = ServerSocket(port, 50, bindAddr)
                Log.i(TAG, "Isolated Jarvis MCP Server running at http://127.0.0.1:$port")

                while (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
                    val client = serverSocket!!.accept()
                    threadPool.execute { handleClient(client, port) }
                }
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "Server socket error on $port", e)
            }
        }

        if (port != browserPort) {
            serverScope.launch {
                val bindAddr = runCatching { InetAddress.getByName("127.0.0.1") }.getOrNull()
                try {
                    secondaryServerSocket = ServerSocket(browserPort, 50, bindAddr)
                    Log.i(TAG, "Isolated Jarvis Browser MCP Server running at http://127.0.0.1:$browserPort")

                    while (isRunning && secondaryServerSocket != null && !secondaryServerSocket!!.isClosed) {
                        val client = secondaryServerSocket!!.accept()
                        threadPool.execute { handleClient(client, browserPort) }
                    }
                } catch (e: Exception) {
                    if (isRunning) Log.w(TAG, "Secondary MCP socket on $browserPort unavailable: ${e.message}")
                }
            }
        }
    }

    fun isActive(): Boolean = isRunning

    fun stop() {
        isRunning = false
        sseSessions.clear()
        try { serverSocket?.close() } catch (_: Exception) {}
        try { secondaryServerSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        secondaryServerSocket = null
        Log.i(TAG, "Embedded MCP Server stopped")
    }

    private fun handleClient(socket: Socket, localPort: Int) {
        socket.use { s ->
            s.soTimeout = 30000
            val input = s.getInputStream()
            val output = s.getOutputStream()

            val request = readHttpRequest(input) ?: return
            if (request.method.equals("OPTIONS", ignoreCase = true)) {
                val preflight = "HTTP/1.1 204 No Content\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                    "Access-Control-Allow-Headers: Content-Type, Accept, X-Device-Id, Authorization, Mcp-Session-Id\r\n" +
                    "Connection: close\r\n\r\n"
                output.write(preflight.toByteArray(Charsets.UTF_8))
                output.flush()
                return
            }

            if (request.path == "/api/agy/cli" && request.method.equals("GET", ignoreCase = true)) {
                val ctx = appContext
                val jsonStr = if (ctx != null) {
                    runBlocking {
                        com.jarvisai.assistant.terminal.AgyCliManager.getCliStatusSnapshotJson(ctx).toString()
                    }
                } else {
                    JSONObject().put("success", false).put("error", "Context not attached").toString()
                }
                val bytes = jsonStr.toByteArray(Charsets.UTF_8)
                val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Connection: close\r\n\r\n"
                output.write(header.toByteArray(Charsets.UTF_8))
                output.write(bytes)
                output.flush()
                return
            }

            if ((request.path == "/api/agy/cli/install" || request.path == "/api/agy/cli/update") &&
                request.method.equals("POST", ignoreCase = true)
            ) {
                val ctx = appContext
                val jsonStr = if (ctx != null) {
                    if (request.path.endsWith("/install")) {
                        com.jarvisai.assistant.terminal.AgyCliManager.installAgy(ctx)
                    } else {
                        com.jarvisai.assistant.terminal.AgyCliManager.updateAgy(ctx)
                    }
                    runBlocking {
                        com.jarvisai.assistant.terminal.AgyCliManager.getCliStatusSnapshotJson(ctx).toString()
                    }
                } else {
                    JSONObject().put("success", false).put("error", "Context not attached").toString()
                }
                val bytes = jsonStr.toByteArray(Charsets.UTF_8)
                val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Connection: close\r\n\r\n"
                output.write(header.toByteArray(Charsets.UTF_8))
                output.write(bytes)
                output.flush()
                return
            }

            if (request.method.equals("GET", ignoreCase = true)) {
                val acceptHeader = request.headers["accept"].orEmpty()
                val userAgent = request.headers["user-agent"].orEmpty()
                val wantsSse = acceptHeader.contains("text/event-stream", ignoreCase = true) ||
                    request.query.contains("sse", ignoreCase = true) ||
                    request.path.endsWith("/sse") ||
                    (userAgent.contains("Go-http-client", ignoreCase = true) && !request.query.contains("info"))

                if (wantsSse) {
                    handleSseStream(s, output, request.path, localPort)
                    return
                }

                val pathTools = listToolsForPath(request.path)
                val toolNames = JSONArray()
                for (i in 0 until pathTools.length()) {
                    pathTools.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let { toolNames.put(it) }
                }
                val infoJson = JSONObject()
                    .put("status", "ok")
                    .put("server", "AgentraEmbeddedMcp")
                    .put("connected", true)
                    .put("path", request.path)
                    .put("endpoints", JSONArray(listOf("/mcp", "/browser", "/mcp/browser", "/filesystem", "/terminal", "/android", "/jarvis", "/flow", "/notes", "/theme", "/skills", "/plugins", "/extension_developer", "/mini_app_developer", "/api/agy/cli")))
                    .put("toolCount", toolNames.length())
                    .put("tools", toolNames)
                    .toString()
                val bytes = infoJson.toByteArray(Charsets.UTF_8)
                val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Connection: close\r\n\r\n"
                output.write(header.toByteArray(Charsets.UTF_8))
                output.write(bytes)
                output.flush()
                return
            }

            val responseBody = processRpcJson(request.path, request.body)

            // Also forward response to any active SSE subscriber for this sessionId or path
            if (responseBody != null) {
                val targetSessionId = request.query
                    .split("&")
                    .firstOrNull { it.startsWith("sessionId=") }
                    ?.substringAfter("sessionId=")
                    .orEmpty()
                pushToSseSubscribers(targetSessionId, request.path, responseBody)
            }

            if (responseBody == null) {
                val accepted = "HTTP/1.1 202 Accepted\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Content-Length: 0\r\n" +
                    "Connection: close\r\n\r\n"
                output.write(accepted.toByteArray(Charsets.UTF_8))
                output.flush()
                return
            }

            val responseBytes = responseBody.toByteArray(Charsets.UTF_8)
            val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Length: ${responseBytes.size}\r\n" +
                "Connection: close\r\n\r\n"

            output.write(header.toByteArray(Charsets.UTF_8))
            output.write(responseBytes)
            output.flush()
        }
    }

    private fun handleSseStream(socket: Socket, output: OutputStream, rawPath: String, localPort: Int) {
        val sessionId = UUID.randomUUID().toString()
        val basePath = if (rawPath.endsWith("/sse")) {
            rawPath.removeSuffix("/sse").ifBlank { "/mcp" }
        } else {
            rawPath.ifBlank { "/mcp" }
        }
        val endpointUri = "http://127.0.0.1:$localPort$basePath?sessionId=$sessionId"
        try {
            socket.soTimeout = 0
            val sseHeaders = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/event-stream\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Connection: keep-alive\r\n" +
                "Access-Control-Allow-Origin: *\r\n\r\n"
            synchronized(output) {
                output.write(sseHeaders.toByteArray(Charsets.UTF_8))
                val initialEvent = "event: endpoint\ndata: $endpointUri\n\n"
                output.write(initialEvent.toByteArray(Charsets.UTF_8))
                output.flush()
            }
            sseSessions[sessionId] = SseSession(sessionId, basePath, output)
            while (isRunning && !socket.isClosed) {
                Thread.sleep(15000)
                synchronized(output) {
                    output.write(": keepalive\n\n".toByteArray(Charsets.UTF_8))
                    output.flush()
                }
            }
        } catch (_: Exception) {
        } finally {
            sseSessions.remove(sessionId)
        }
    }

    private fun pushToSseSubscribers(sessionId: String, path: String, jsonMessage: String) {
        val eventPayload = "event: message\ndata: $jsonMessage\n\n".toByteArray(Charsets.UTF_8)
        if (sessionId.isNotBlank()) {
            val sub = sseSessions[sessionId]
            if (sub != null) {
                runCatching {
                    synchronized(sub.output) {
                        sub.output.write(eventPayload)
                        sub.output.flush()
                    }
                }.onFailure { sseSessions.remove(sessionId) }
                return
            }
        }
        sseSessions.values.filter { it.path == path }.forEach { sub ->
            runCatching {
                synchronized(sub.output) {
                    sub.output.write(eventPayload)
                    sub.output.flush()
                }
            }.onFailure { sseSessions.remove(sub.sessionId) }
        }
    }

    private data class ParsedRequest(
        val method: String,
        val path: String,
        val query: String,
        val headers: Map<String, String>,
        val body: String
    )

    private fun readHttpRequest(input: InputStream): ParsedRequest? {
        val headerStream = ByteArrayOutputStream()
        var b: Int
        var prev = 0
        while (input.read().also { b = it } != -1) {
            headerStream.write(b)
            if (prev == '\n'.code && b == '\n'.code) break
            if (prev == '\n'.code && b == '\r'.code) {
                val next = input.read()
                if (next != -1) headerStream.write(next)
                break
            }
            if (b != '\r'.code) prev = b
        }

        val headerText = headerStream.toString(Charsets.UTF_8.name())
        val lines = headerText.split("\r\n", "\n")
        val reqLine = lines.firstOrNull()?.split(" ") ?: return null
        if (reqLine.size < 2) return null

        val method = reqLine[0]
        val fullUri = reqLine[1]
        val path = fullUri.substringBefore('?')
        val query = fullUri.substringAfter('?', "")

        var contentLength = 0
        val headersMap = linkedMapOf<String, String>()
        for (line in lines.drop(1)) {
            val colon = line.indexOf(':')
            if (colon > 0) {
                val k = line.substring(0, colon).trim().lowercase()
                val v = line.substring(colon + 1).trim()
                headersMap[k] = v
                if (k == "content-length") {
                    contentLength = v.toIntOrNull() ?: 0
                }
            }
        }

        val bodyBuffer = ByteArray(contentLength.coerceAtLeast(0).coerceAtMost(2_000_000))
        var totalRead = 0
        while (totalRead < bodyBuffer.size) {
            val r = input.read(bodyBuffer, totalRead, bodyBuffer.size - totalRead)
            if (r == -1) break
            totalRead += r
        }

        return ParsedRequest(method, path, query, headersMap, String(bodyBuffer, 0, totalRead, Charsets.UTF_8))
    }

    internal fun processRpcJson(path: String, body: String): String? {
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            return createJsonRpcError(JSONObject.NULL, -32600, "Invalid Request: Malformed JSON")
        }
        val hasId = json.has("id") && !json.isNull("id")
        val id = if (hasId) json.opt("id") ?: JSONObject.NULL else JSONObject.NULL
        val method = json.optString("method")
        if (method.isBlank()) {
            return createJsonRpcError(id, -32600, "Invalid Request: Missing method")
        }
        // Handle notifications per MCP JSON-RPC spec
        if (!hasId || method.startsWith("notifications/")) {
            return null
        }
        val params = json.optJSONObject("params") ?: JSONObject()

        return runBlocking {
            try {
                when (method) {
                    "initialize" -> {
                        val res = JSONObject().apply {
                            put("protocolVersion", "2024-11-05")
                            put("capabilities", JSONObject().apply {
                                put("tools", JSONObject().put("listChanged", true))
                                put("prompts", JSONObject())
                                put("resources", JSONObject())
                            })
                            put("serverInfo", JSONObject().apply {
                                put("name", "AgentraEmbeddedMcp")
                                put("version", "4.2.0")
                            })
                        }
                        createJsonRpcSuccess(id, res)
                    }
                    "ping" -> createJsonRpcSuccess(id, JSONObject())
                    "prompts/list" -> createJsonRpcSuccess(id, JSONObject().put("prompts", JSONArray()))
                    "resources/list" -> createJsonRpcSuccess(id, JSONObject().put("resources", JSONArray()))
                    "resources/templates/list" -> createJsonRpcSuccess(id, JSONObject().put("resourceTemplates", JSONArray()))
                    "tools/list" -> {
                        val tools = listToolsForPath(path)
                        createJsonRpcSuccess(id, JSONObject().apply { put("tools", tools) })
                    }
                    "tools/call" -> {
                        val toolName = params.optString("name")
                        if (toolName.isBlank()) {
                            return@runBlocking createJsonRpcError(id, -32602, "Invalid params: Missing tool name")
                        }
                        val args = params.optJSONObject("arguments") ?: JSONObject()
                        val result = dispatchToolCall(path, toolName, args)
                            ?: return@runBlocking createJsonRpcError(id, -32601, "Method not found: Unknown tool $toolName")

                        val contentArray = JSONArray()
                        val imageB64 = result.optString("image_base64", "").trim()
                        if (imageB64.isNotBlank() && !result.has("error")) {
                            val metaCopy = JSONObject(result.toString()).apply {
                                remove("image_base64")
                                put("image_attached", true)
                            }
                            contentArray.put(JSONObject().apply {
                                put("type", "text")
                                put("text", metaCopy.toString(2))
                            })
                            contentArray.put(JSONObject().apply {
                                put("type", "image")
                                put("data", imageB64)
                                put("mimeType", "image/jpeg")
                            })
                        } else {
                            contentArray.put(JSONObject().apply {
                                put("type", "text")
                                put("text", result.toString(2))
                            })
                        }

                        createJsonRpcSuccess(id, JSONObject().apply {
                            put("content", contentArray)
                            put("isError", result.has("error"))
                        })
                    }
                    else -> createJsonRpcError(id, -32601, "Method not found: $method")
                }
            } catch (e: org.json.JSONException) {
                createJsonRpcError(id, -32602, "Invalid params: ${e.message}")
            } catch (e: Exception) {
                createJsonRpcError(id, -32603, "Internal error: ${e.message}")
            }
        }
    }

    private fun listToolsForPath(path: String): JSONArray {
        val clean = path.lowercase()
        fun appendUnique(dest: JSONArray, seen: MutableSet<String>, src: JSONArray) {
            for (i in 0 until src.length()) {
                val obj = src.optJSONObject(i) ?: continue
                val n = obj.optString("name")
                if (n.isNotBlank() && seen.add(n)) {
                    dest.put(obj)
                }
            }
        }
        return when {
            clean.contains("filesystem") -> McpEcosystemBridge.listFilesystemTools()
            clean.contains("/custom/") -> {
                val srvName = path.substringAfter("/custom/").trim('/')
                McpEcosystemBridge.listCustomServerTools(srvName)
            }
            clean.contains("browser") -> {
                val combined = JSONArray()
                val seen = mutableSetOf<String>()
                appendUnique(combined, seen, BrowserMcpBridge.listTools())
                appendUnique(combined, seen, listExtensionDeveloperTools())
                appendUnique(combined, seen, listAllEnabledExtensionsTools())
                combined
            }
            clean.contains("terminal") -> TerminalMcpBridge.listTools()
            clean.contains("android") -> AndroidDeviceMcpBridge.listTools()
            clean.contains("jarvis") -> JarvisMemoryMcpBridge.listTools()
            clean.contains("flow") -> FlowMcpBridge.listTools()
            clean.contains("notes") -> NotesMcpBridge.listTools()
            clean.contains("theme") -> ThemeMcpBridge.listTools()
            clean.contains("skills") || clean.contains("plugins") -> McpEcosystemBridge.listAuthoringAndManagementTools()
            clean.contains("extension_developer") -> {
                val combined = JSONArray()
                val seen = mutableSetOf<String>()
                appendUnique(combined, seen, listExtensionDeveloperTools())
                appendUnique(combined, seen, McpEcosystemBridge.listAuthoringAndManagementTools())
                combined
            }
            clean.contains("mini_app_developer") -> listMiniAppDeveloperTools()
            clean.contains("/extension/") -> listExtensionSpecificTools(path.substringAfter("/extension/").trim('/'))
            else -> {
                val combined = JSONArray()
                val seen = mutableSetOf<String>()
                appendUnique(combined, seen, BrowserMcpBridge.listTools())
                appendUnique(combined, seen, TerminalMcpBridge.listTools())
                appendUnique(combined, seen, AndroidDeviceMcpBridge.listTools())
                appendUnique(combined, seen, JarvisMemoryMcpBridge.listTools())
                appendUnique(combined, seen, NotesMcpBridge.listTools())
                appendUnique(combined, seen, ThemeMcpBridge.listTools())
                appendUnique(combined, seen, FlowMcpBridge.listTools())
                appendUnique(combined, seen, McpEcosystemBridge.listFilesystemTools())
                appendUnique(combined, seen, McpEcosystemBridge.listAuthoringAndManagementTools())
                appendUnique(combined, seen, McpEcosystemBridge.listAllCustomToolsAcrossServers())
                appendUnique(combined, seen, listExtensionDeveloperTools())
                appendUnique(combined, seen, listAllEnabledExtensionsTools())
                combined
            }
        }
    }

    private suspend fun dispatchToolCall(path: String, toolName: String, args: JSONObject): JSONObject? {
        McpEcosystemBridge.callFilesystemTool(toolName, args)?.let { return it }
        McpEcosystemBridge.callAuthoringOrManagementTool(toolName, args)?.let { return it }

        if (path.contains("/custom/")) {
            val srvName = path.substringAfter("/custom/").trim('/')
            McpEcosystemBridge.callCustomServerTool(srvName, toolName, args)?.let { return it }
        }

        return when {
            toolName.startsWith("browser_") -> BrowserMcpBridge.callTool(toolName, args)
            toolName.startsWith("terminal_") -> TerminalMcpBridge.callTool(toolName, args)
            toolName.startsWith("android_") -> AndroidDeviceMcpBridge.callTool(toolName, args)
            toolName.startsWith("jarvis_") -> JarvisMemoryMcpBridge.callTool(toolName, args)
            toolName.startsWith("notes_") -> NotesMcpBridge.callTool(toolName, args)
            toolName.startsWith("theme_") -> ThemeMcpBridge.callTool(toolName, args)
            toolName.startsWith("flow_") || toolName.startsWith("deepx_") -> FlowMcpBridge.callTool(toolName, args)
            toolName.startsWith("ext_dev_") -> callExtensionDeveloperTool(toolName, args)
            toolName.startsWith("mini_app_") -> callMiniAppDeveloperTool(toolName, args)
            path.contains("/extension/") -> {
                val extId = path.substringAfter("/extension/").trim('/')
                val res = BrowserExtensionManager.executeTool(extId, toolName, args)
                res.fold(
                    onSuccess = { JSONObject().put("result", it) },
                    onFailure = { JSONObject().put("error", it.message ?: "Extension tool failed") }
                )
            }
            else -> {
                // Check installed browser extensions or custom MCP server tools
                val matchedExt = BrowserExtensionManager.extensions.value.firstOrNull { ext ->
                    ext.isEnabled && (ext.tools.contains(toolName) || toolName.startsWith("ext_${ext.id.replace('-', '_')}_"))
                }
                if (matchedExt != null) {
                    val actualTool = toolName.removePrefix("ext_${matchedExt.id.replace('-', '_')}_")
                    val res = BrowserExtensionManager.executeTool(matchedExt.id, actualTool, args)
                    return res.fold(
                        onSuccess = { JSONObject().put("result", it) },
                        onFailure = { JSONObject().put("error", it.message ?: "Extension tool failed") }
                    )
                }
                McpEcosystemBridge.callCustomServerTool(null, toolName, args)
            }
        }
    }

    private fun listAllEnabledExtensionsTools(): JSONArray {
        val arr = JSONArray()
        BrowserExtensionManager.extensions.value.filter { it.isEnabled }.forEach { ext ->
            ext.tools.forEach { toolName ->
                arr.put(
                    JSONObject()
                        .put("name", toolName)
                        .put("description", "[Extension:${ext.name}] Execute '$toolName'")
                        .put("inputSchema", JSONObject().put("type", "object").put("properties", JSONObject()))
                )
            }
        }
        return arr
    }

    private fun listExtensionDeveloperTools(): JSONArray = JSONArray().apply {
        put(JSONObject()
            .put("name", "ext_dev_list_extensions")
            .put("description", "List all installed Jarvis WebView browser extensions and their status.")
            .put("inputSchema", JSONObject().put("type", "object").put("properties", JSONObject())))
        put(JSONObject()
            .put("name", "ext_dev_validate_manifest")
            .put("description", "Validate a Chrome/Jarvis extension manifest.json payload.")
            .put("inputSchema", JSONObject().put("type", "object")
                .put("properties", JSONObject().put("manifest_json", JSONObject().put("type", "string")))
                .put("required", JSONArray(listOf("manifest_json")))))
    }

    private fun callExtensionDeveloperTool(name: String, args: JSONObject): JSONObject {
        return when (name) {
            "ext_dev_list_extensions" -> {
                val arr = JSONArray()
                BrowserExtensionManager.extensions.value.forEach { ext ->
                    arr.put(JSONObject().put("id", ext.id).put("name", ext.name).put("version", ext.version).put("enabled", ext.isEnabled))
                }
                JSONObject().put("extensions", arr)
            }
            "ext_dev_validate_manifest" -> {
                val raw = args.optString("manifest_json", "{}")
                val obj = runCatching { JSONObject(raw) }.getOrNull()
                    ?: return JSONObject().put("error", "Invalid JSON syntax")
                val validation = PluginRepositories.validateManifest(obj)
                JSONObject()
                    .put("valid", validation.isValid)
                    .put("id", validation.parsedId)
                    .put("name", validation.parsedName)
                    .put("errors", JSONArray(validation.errors))
            }
            else -> JSONObject().put("error", "Unknown extension developer tool: $name")
        }
    }

    private fun listMiniAppDeveloperTools(): JSONArray = JSONArray().apply {
        put(JSONObject()
            .put("name", "mini_app_list_workspaces")
            .put("description", "List local workspace projects in Agentra.")
            .put("inputSchema", JSONObject().put("type", "object").put("properties", JSONObject())))
        put(JSONObject()
            .put("name", "mini_app_list")
            .put("description", "List installed Agentra Mini-Apps in ~/mini-apps.")
            .put("inputSchema", JSONObject().put("type", "object").put("properties", JSONObject())))
        put(JSONObject()
            .put("name", "mini_app_create")
            .put("description", "Create a new starter Mini-App scaffold with manifest.json and index.html.")
            .put("inputSchema", JSONObject().put("type", "object")
                .put("properties", JSONObject()
                    .put("name", JSONObject().put("type", "string").put("description", "Display name"))
                    .put("description", JSONObject().put("type", "string").put("description", "Short summary")))
                .put("required", JSONArray(listOf("name")))))
        put(JSONObject()
            .put("name", "mini_app_validate")
            .put("description", "Validate a Mini-App manifest.json string.")
            .put("inputSchema", JSONObject().put("type", "object")
                .put("properties", JSONObject().put("manifest_json", JSONObject().put("type", "string")))
                .put("required", JSONArray(listOf("manifest_json")))))
        put(JSONObject()
            .put("name", "mini_app_read_file")
            .put("description", "Read a file inside a Mini-App folder.")
            .put("inputSchema", JSONObject().put("type", "object")
                .put("properties", JSONObject()
                    .put("slug", JSONObject().put("type", "string"))
                    .put("path", JSONObject().put("type", "string")))
                .put("required", JSONArray(listOf("slug", "path")))))
        put(JSONObject()
            .put("name", "mini_app_write_file")
            .put("description", "Write or update a file inside a Mini-App folder.")
            .put("inputSchema", JSONObject().put("type", "object")
                .put("properties", JSONObject()
                    .put("slug", JSONObject().put("type", "string"))
                    .put("path", JSONObject().put("type", "string"))
                    .put("content", JSONObject().put("type", "string")))
                .put("required", JSONArray(listOf("slug", "path", "content")))))
    }

    private fun callMiniAppDeveloperTool(name: String, args: JSONObject): JSONObject {
        val ctx = appContext ?: return JSONObject().put("error", "Context unavailable")
        return when (name) {
            "mini_app_list_workspaces" -> {
                val projectsDir = com.jarvisai.assistant.terminal.LocalEnvironmentManager.getIsolatedProjectsDir(ctx)
                val dirs = projectsDir.listFiles { f -> f.isDirectory }?.map { it.name }.orEmpty()
                JSONObject().put("projectsDir", projectsDir.absolutePath).put("projects", JSONArray(dirs))
            }
            "mini_app_list" -> {
                val apps = com.jarvisai.assistant.data.miniapps.MiniAppsManager.refresh(ctx)
                val arr = JSONArray()
                apps.forEach { a ->
                    arr.put(
                        JSONObject()
                            .put("id", a.id)
                            .put("slug", a.slug)
                            .put("name", a.name)
                            .put("version", a.version)
                            .put("entry", a.entry)
                            .put("rootDir", a.rootDir.absolutePath)
                    )
                }
                JSONObject().put("count", arr.length()).put("miniApps", arr)
            }
            "mini_app_create" -> {
                val appName = args.optString("name", "").trim()
                val desc = args.optString("description", "Built by Agentra")
                val res = com.jarvisai.assistant.data.miniapps.MiniAppsManager.createStarterApp(ctx, appName, desc)
                res.fold(
                    onSuccess = { a ->
                        JSONObject()
                            .put("created", true)
                            .put("id", a.id)
                            .put("slug", a.slug)
                            .put("rootDir", a.rootDir.absolutePath)
                    },
                    onFailure = { e -> JSONObject().put("error", e.message ?: "Create failed") }
                )
            }
            "mini_app_validate" -> {
                val raw = args.optString("manifest_json", "{}")
                val v = com.jarvisai.assistant.data.miniapps.MiniAppsManager.validateManifest(raw)
                JSONObject()
                    .put("valid", v.valid)
                    .put("errors", JSONArray(v.errors))
                    .put("warnings", JSONArray(v.warnings))
            }
            "mini_app_read_file" -> {
                val slug = args.optString("slug", "")
                val rel = args.optString("path", "index.html")
                com.jarvisai.assistant.data.miniapps.MiniAppsManager.readAppFile(ctx, slug, rel).fold(
                    onSuccess = { text -> JSONObject().put("slug", slug).put("path", rel).put("content", text) },
                    onFailure = { e -> JSONObject().put("error", e.message ?: "Read failed") }
                )
            }
            "mini_app_write_file" -> {
                val slug = args.optString("slug", "")
                val rel = args.optString("path", "")
                val content = args.optString("content", "")
                com.jarvisai.assistant.data.miniapps.MiniAppsManager.writeAppFile(ctx, slug, rel, content).fold(
                    onSuccess = { f -> JSONObject().put("written", true).put("path", f.absolutePath) },
                    onFailure = { e -> JSONObject().put("error", e.message ?: "Write failed") }
                )
            }
            else -> JSONObject().put("error", "Unknown mini-app tool: $name")
        }
    }

    private fun listExtensionSpecificTools(extId: String): JSONArray {
        val ext = BrowserExtensionManager.extensions.value.firstOrNull { it.id == extId } ?: return JSONArray()
        val arr = JSONArray()
        ext.tools.forEach { toolName ->
            arr.put(
                JSONObject()
                    .put("name", toolName)
                    .put("description", "Tool '$toolName' from extension ${ext.name}")
                    .put("inputSchema", JSONObject().put("type", "object").put("properties", JSONObject()))
            )
        }
        return arr
    }

    private fun createJsonRpcSuccess(id: Any, result: JSONObject): String = JSONObject().apply {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }.toString()

    private fun createJsonRpcError(id: Any, code: Int, message: String): String = JSONObject().apply {
        put("jsonrpc", "2.0")
        put("id", id)
        put("error", JSONObject().apply { put("code", code); put("message", message) })
    }.toString()
}
