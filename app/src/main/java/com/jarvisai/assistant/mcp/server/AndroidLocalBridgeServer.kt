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
import java.util.concurrent.Executors

class AndroidLocalBridgeServer private constructor() {

    companion object {
        private const val TAG = "LocalBridgeServer"
        val instance: AndroidLocalBridgeServer by lazy { AndroidLocalBridgeServer() }
    }

    private var serverSocket: ServerSocket? = null
    private var secondaryServerSocket: ServerSocket? = null
    @Volatile
    private var isRunning = false
    @Volatile
    private var appContext: Context? = null
    private val threadPool = Executors.newCachedThreadPool()
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    internal fun attachContextForTest(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        NotesMcpBridge.init(ctx)
        ThemeMcpBridge.init(ctx)
    }

    fun start(context: Context, port: Int = com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_MCP_PORT) {
        val ctx = context.applicationContext
        appContext = ctx
        NotesMcpBridge.init(ctx)
        ThemeMcpBridge.init(ctx)
        BrowserSessionManager.instance.init(ctx)
        BrowserExtensionManager.init(ctx)
        if (isRunning) return
        isRunning = true

        val browserPort = com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_BROWSER_MCP_PORT

        serverScope.launch {
            val bindAddr = runCatching { InetAddress.getByName("127.0.0.1") }.getOrNull()
            try {
                serverSocket = ServerSocket(port, 50, bindAddr)
                Log.i(TAG, "Isolated Jarvis MCP Server running at http://127.0.0.1:$port")

                while (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
                    val client = serverSocket!!.accept()
                    threadPool.execute { handleClient(client) }
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
                        threadPool.execute { handleClient(client) }
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
        try { serverSocket?.close() } catch (_: Exception) {}
        try { secondaryServerSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        secondaryServerSocket = null
        Log.i(TAG, "Embedded MCP Server stopped")
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 30000
            val input = s.getInputStream()
            val output = s.getOutputStream()

            val request = readHttpRequest(input) ?: return
            if (request.method.equals("OPTIONS", ignoreCase = true)) {
                val preflight = "HTTP/1.1 204 No Content\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                    "Access-Control-Allow-Headers: Content-Type, Accept, X-Device-Id, Authorization\r\n" +
                    "Connection: close\r\n\r\n"
                output.write(preflight.toByteArray(Charsets.UTF_8))
                output.flush()
                return
            }

            if (request.method.equals("GET", ignoreCase = true)) {
                val infoJson = JSONObject()
                    .put("status", "ok")
                    .put("server", "JarvisEmbeddedMcp")
                    .put("path", request.path)
                    .put("endpoints", JSONArray(listOf("/mcp", "/browser", "/terminal", "/android", "/jarvis", "/flow", "/notes", "/theme", "/extension_developer", "/mini_app_developer")))
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

    private data class ParsedRequest(val method: String, val path: String, val body: String)

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
        val path = reqLine[1].substringBefore('?')

        var contentLength = 0
        for (line in lines) {
            val colon = line.indexOf(':')
            if (colon > 0 && line.substring(0, colon).trim().equals("Content-Length", ignoreCase = true)) {
                contentLength = line.substring(colon + 1).trim().toIntOrNull() ?: 0
            }
        }

        val bodyBuffer = ByteArray(contentLength.coerceAtLeast(0).coerceAtMost(2_000_000))
        var totalRead = 0
        while (totalRead < bodyBuffer.size) {
            val r = input.read(bodyBuffer, totalRead, bodyBuffer.size - totalRead)
            if (r == -1) break
            totalRead += r
        }

        return ParsedRequest(method, path, String(bodyBuffer, 0, totalRead, Charsets.UTF_8))
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
                            put("capabilities", JSONObject().apply { put("tools", JSONObject()) })
                            put("serverInfo", JSONObject().apply {
                                put("name", "JarvisEmbeddedMcp")
                                put("version", "4.1.0")
                            })
                        }
                        createJsonRpcSuccess(id, res)
                    }
                    "ping" -> createJsonRpcSuccess(id, JSONObject())
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

                        createJsonRpcSuccess(id, JSONObject().apply {
                            put("content", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("type", "text")
                                    put("text", result.toString(2))
                                })
                            })
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
        return when {
            clean.contains("browser") -> BrowserMcpBridge.listTools()
            clean.contains("terminal") -> TerminalMcpBridge.listTools()
            clean.contains("android") -> AndroidDeviceMcpBridge.listTools()
            clean.contains("jarvis") -> JarvisMemoryMcpBridge.listTools()
            clean.contains("flow") -> FlowMcpBridge.listTools()
            clean.contains("notes") -> NotesMcpBridge.listTools()
            clean.contains("theme") -> ThemeMcpBridge.listTools()
            clean.contains("extension_developer") -> listExtensionDeveloperTools()
            clean.contains("mini_app_developer") -> listMiniAppDeveloperTools()
            clean.contains("/extension/") -> listExtensionSpecificTools(path.substringAfter("/extension/").trim('/'))
            else -> {
                val combined = JSONArray()
                fun appendAll(src: JSONArray) {
                    for (i in 0 until src.length()) combined.put(src.get(i))
                }
                appendAll(BrowserMcpBridge.listTools())
                appendAll(TerminalMcpBridge.listTools())
                appendAll(AndroidDeviceMcpBridge.listTools())
                appendAll(JarvisMemoryMcpBridge.listTools())
                appendAll(NotesMcpBridge.listTools())
                appendAll(ThemeMcpBridge.listTools())
                appendAll(FlowMcpBridge.listTools())
                combined
            }
        }
    }

    private suspend fun dispatchToolCall(path: String, toolName: String, args: JSONObject): JSONObject? {
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
            else -> null
        }
    }

    private fun listExtensionDeveloperTools(): JSONArray = JSONArray().apply {
        put(JSONObject()
            .put("name", "ext_dev_list_extensions")
            .put("description", "List all installed Jarvis WebView browser extensions and their status.")
            .put("inputSchema", JSONObject().put("type", "object").put("properties", JSONObject())))
        put(JSONObject()
            .put("name", "ext_dev_validate_manifest")
            .put("description", "Validate a Chrome/AntiGem extension manifest.json payload.")
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
            .put("description", "List local workspace projects in Jarvis.")
            .put("inputSchema", JSONObject().put("type", "object").put("properties", JSONObject())))
    }

    private fun callMiniAppDeveloperTool(name: String, args: JSONObject): JSONObject {
        val ctx = appContext ?: return JSONObject().put("error", "Context unavailable")
        val projectsDir = com.jarvisai.assistant.terminal.LocalEnvironmentManager.getIsolatedProjectsDir(ctx)
        val dirs = projectsDir.listFiles { f -> f.isDirectory }?.map { it.name }.orEmpty()
        return JSONObject().put("projectsDir", projectsDir.absolutePath).put("projects", JSONArray(dirs))
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
