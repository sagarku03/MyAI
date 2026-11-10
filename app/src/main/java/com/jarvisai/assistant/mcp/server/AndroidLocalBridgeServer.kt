package com.jarvisai.assistant.mcp.server

import android.content.Context
import android.util.Log
import com.jarvisai.assistant.browser.BrowserSessionManager
import com.jarvisai.assistant.mcp.bridges.AndroidDeviceMcpBridge
import com.jarvisai.assistant.mcp.bridges.BrowserMcpBridge
import com.jarvisai.assistant.mcp.bridges.FlowMcpBridge
import com.jarvisai.assistant.mcp.bridges.JarvisMemoryMcpBridge
import com.jarvisai.assistant.mcp.bridges.TerminalMcpBridge
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
    @Volatile
    private var isRunning = false
    private val threadPool = Executors.newCachedThreadPool()
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(context: Context, port: Int = 8765) {
        if (isRunning) return
        BrowserSessionManager.instance.init(context)

        serverScope.launch {
            try {
                val bindAddr = InetAddress.getByName("127.0.0.1")
                serverSocket = ServerSocket(port, 50, bindAddr)
                isRunning = true
                Log.i(TAG, "Embedded MCP Server running at http://127.0.0.1:$port")

                while (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
                    val client = serverSocket!!.accept()
                    threadPool.execute { handleClient(client) }
                }
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "Server socket error", e)
            }
        }
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        Log.i(TAG, "Embedded MCP Server stopped")
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 30000
            val input = s.getInputStream()
            val output = s.getOutputStream()

            val request = readHttpRequest(input) ?: return
            val responseBody = processMcpRequest(request.path, request.body)

            val responseBytes = responseBody.toByteArray(Charsets.UTF_8)
            val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
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
        val path = reqLine[1]

        var contentLength = 0
        for (line in lines) {
            val colon = line.indexOf(':')
            if (colon > 0 && line.substring(0, colon).trim().equals("Content-Length", ignoreCase = true)) {
                contentLength = line.substring(colon + 1).trim().toIntOrNull() ?: 0
            }
        }

        val bodyBuffer = ByteArray(contentLength)
        var totalRead = 0
        while (totalRead < contentLength) {
            val r = input.read(bodyBuffer, totalRead, contentLength - totalRead)
            if (r == -1) break
            totalRead += r
        }

        return ParsedRequest(method, path, String(bodyBuffer, Charsets.UTF_8))
    }

    private fun processMcpRequest(path: String, body: String): String {
        val json = try { JSONObject(body) } catch (_: Exception) {
            return createJsonRpcError(JSONObject.NULL, -32600, "Invalid Request: Malformed JSON")
        }
        val id = json.opt("id") ?: JSONObject.NULL
        val method = json.optString("method")
        if (method.isBlank()) {
            return createJsonRpcError(id, -32600, "Invalid Request: Missing method")
        }
        val params = json.optJSONObject("params") ?: JSONObject()

        return runBlocking {
            try {
                when (method) {
                    "initialize" -> {
                        val res = JSONObject().apply {
                            put("protocolVersion", "2024-11-05")
                            put("capabilities", JSONObject().apply { put("tools", JSONObject()) })
                            put("serverInfo", JSONObject().apply { put("name", "JarvisEmbeddedMcp"); put("version", "4.0.0") })
                        }
                        createJsonRpcSuccess(id, res)
                    }
                    "tools/list" -> {
                        val tools = when {
                            path.contains("browser") -> BrowserMcpBridge.listTools()
                            path.contains("terminal") -> TerminalMcpBridge.listTools()
                            path.contains("android") -> AndroidDeviceMcpBridge.listTools()
                            path.contains("jarvis") -> JarvisMemoryMcpBridge.listTools()
                            path.contains("flow") -> FlowMcpBridge.listTools()
                            else -> {
                                val combined = JSONArray()
                                BrowserMcpBridge.listTools().let { for (i in 0 until it.length()) combined.put(it.get(i)) }
                                TerminalMcpBridge.listTools().let { for (i in 0 until it.length()) combined.put(it.get(i)) }
                                AndroidDeviceMcpBridge.listTools().let { for (i in 0 until it.length()) combined.put(it.get(i)) }
                                JarvisMemoryMcpBridge.listTools().let { for (i in 0 until it.length()) combined.put(it.get(i)) }
                                FlowMcpBridge.listTools().let { for (i in 0 until it.length()) combined.put(it.get(i)) }
                                combined
                            }
                        }
                        createJsonRpcSuccess(id, JSONObject().apply { put("tools", tools) })
                    }
                    "tools/call" -> {
                        val toolName = params.optString("name")
                        if (toolName.isBlank()) {
                            return@runBlocking createJsonRpcError(id, -32602, "Invalid params: Missing tool name")
                        }
                        val args = params.optJSONObject("arguments") ?: JSONObject()

                        val result = when {
                            toolName.startsWith("browser_") -> BrowserMcpBridge.callTool(toolName, args)
                            toolName.startsWith("terminal_") -> TerminalMcpBridge.callTool(toolName, args)
                            toolName.startsWith("android_") -> AndroidDeviceMcpBridge.callTool(toolName, args)
                            toolName.startsWith("jarvis_") -> JarvisMemoryMcpBridge.callTool(toolName, args)
                            toolName.startsWith("flow_") || toolName.startsWith("deepx_") -> FlowMcpBridge.callTool(toolName, args)
                            else -> return@runBlocking createJsonRpcError(id, -32601, "Method not found: Unknown tool $toolName")
                        }

                        createJsonRpcSuccess(id, JSONObject().apply {
                            put("content", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("type", "text")
                                    put("text", result.toString(2))
                                })
                            })
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
