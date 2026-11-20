package com.jarvisai.assistant.terminal

import android.content.Context
import android.util.Log
import com.jarvisai.assistant.data.remote.JarvisHubConfig
import com.jarvisai.assistant.util.IndiaTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Pure-Kotlin Jarvis AI IDE Bridge HTTP Server (Port 2234).
 * Replaces external third-party `agy_ide_bridge` binaries so there are zero branding collisions,
 * zero binary write races ("Text file busy"), zero raw ANSI garbage, and working `/health` & CLI routes.
 */
class JarvisIdeBridgeServer private constructor() {

    companion object {
        private const val TAG = "JarvisIdeBridgeServer"
        val instance: JarvisIdeBridgeServer by lazy { JarvisIdeBridgeServer() }
    }

    private var serverSocket: ServerSocket? = null
    @Volatile
    private var isRunning = false
    @Volatile
    private var activePort = JarvisHubConfig.ISOLATED_BRIDGE_PORT
    @Volatile
    private var appContext: Context? = null

    private val threadPool = Executors.newCachedThreadPool()
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun isActive(): Boolean = isRunning && serverSocket != null && serverSocket?.isClosed == false

    fun start(context: Context, port: Int = JarvisHubConfig.getBridgePort(context)) {
        val ctx = context.applicationContext
        appContext = ctx
        if (isActive() && activePort == port) return
        stop()
        activePort = port
        isRunning = true

        serverScope.launch {
            val bindAddr = runCatching { InetAddress.getByName("127.0.0.1") }.getOrNull()
            try {
                serverSocket = ServerSocket(port, 50, bindAddr)
                Log.i(TAG, "Jarvis AI IDE Bridge running on http://127.0.0.1:$port (IST)")
                LocalServerManager.appendServerLog(
                    "${IndiaTimeUtils.nowLogTimestamp()} [Jarvis Bridge] Listening on http://127.0.0.1:$port (Timezone: ${IndiaTimeUtils.IST_ZONE_ID})"
                )

                while (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
                    val client = serverSocket!!.accept()
                    threadPool.execute { handleClient(client) }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.w(TAG, "Bridge socket error on port $port: ${e.message}")
                    LocalServerManager.appendServerLog(
                        "${IndiaTimeUtils.nowLogTimestamp()} [Jarvis Bridge] Port $port bind notice: ${e.message}"
                    )
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 15000
            val input = s.getInputStream()
            val output = s.getOutputStream()
            val req = readHttpRequest(input) ?: return

            if (req.method.equals("OPTIONS", ignoreCase = true)) {
                val preflight = "HTTP/1.1 204 No Content\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                    "Access-Control-Allow-Headers: Content-Type, Accept, Authorization, X-Device-Id\r\n" +
                    "Connection: close\r\n\r\n"
                output.write(preflight.toByteArray(Charsets.UTF_8))
                output.flush()
                return
            }

            val ctx = appContext
            val hubPort = JarvisHubConfig.getHubPort(ctx)
            val diag = LocalServerManager.agyDiagnostics.value

            when {
                req.path == "/health" || req.path == "/api/health" || req.path == "/" -> {
                    val body = JSONObject().apply {
                        put("status", "ok")
                        put("service", "Agentra IDE Bridge")
                        put("bridgePort", activePort)
                        put("hubPort", hubPort)
                        put("hubOnline", diag.agyHubReachable)
                        put("agyVersion", diag.agyVersion)
                        put("timezone", IndiaTimeUtils.IST_ZONE_ID)
                        put("localTimeIst", IndiaTimeUtils.nowBannerTimestamp())
                    }.toString()
                    writeJsonResponse(output, 200, "OK", body)
                }

                req.path == "/api/status" -> {
                    val body = JSONObject().apply {
                        put("status", LocalServerManager.status.value::class.java.simpleName)
                        put("bridgePort", activePort)
                        put("hubPort", hubPort)
                        put("agyBinaryFound", diag.agyBinaryFound)
                        put("agyBinaryPath", diag.agyBinaryPath ?: "")
                        put("agyVersion", diag.agyVersion)
                        put("agyHubReachable", diag.agyHubReachable)
                        put("mcpConfigured", diag.mcpConfigured)
                        put("timezone", IndiaTimeUtils.IST_ZONE_ID)
                        put("localTimeIst", IndiaTimeUtils.nowBannerTimestamp())
                    }.toString()
                    writeJsonResponse(output, 200, "OK", body)
                }

                req.path == "/api/agy/cli" && req.method.equals("GET", ignoreCase = true) -> {
                    val body = if (ctx != null) {
                        kotlinx.coroutines.runBlocking {
                            AgyCliManager.getCliStatusSnapshotJson(ctx).toString()
                        }
                    } else {
                        JSONObject().put("success", false).put("error", "Context unavailable").toString()
                    }
                    writeJsonResponse(output, 200, "OK", body)
                }

                (req.path == "/api/agy/cli/install" || req.path == "/api/agy/cli/update") &&
                    req.method.equals("POST", ignoreCase = true) -> {
                    val body = if (ctx != null) {
                        if (req.path.endsWith("/install")) AgyCliManager.installAgy(ctx) else AgyCliManager.updateAgy(ctx)
                        kotlinx.coroutines.runBlocking {
                            AgyCliManager.getCliStatusSnapshotJson(ctx).toString()
                        }
                    } else {
                        JSONObject().put("success", false).put("error", "Context unavailable").toString()
                    }
                    writeJsonResponse(output, 200, "OK", body)
                }

                req.path == "/api/claude/status" -> {
                    val body = JSONObject().put("installed", false).put("running", false).put("sessions", 0).toString()
                    writeJsonResponse(output, 200, "OK", body)
                }

                req.path == "/api/claude/sessions" -> {
                    val body = JSONObject().put("sessions", JSONArray()).toString()
                    writeJsonResponse(output, 200, "OK", body)
                }

                req.path == "/api/opencode/status" -> {
                    val body = JSONObject().put("installed", false).put("running", false).toString()
                    writeJsonResponse(output, 200, "OK", body)
                }

                req.path == "/api/opencode/providers" -> {
                    val body = JSONObject().put("providers", JSONArray()).toString()
                    writeJsonResponse(output, 200, "OK", body)
                }

                else -> {
                    val body = JSONObject()
                        .put("error", "Not found")
                        .put("path", req.path)
                        .put("service", "Agentra IDE Bridge")
                        .toString()
                    writeJsonResponse(output, 404, "Not Found", body)
                }
            }
        }
    }

    private fun writeJsonResponse(output: OutputStream, code: Int, reason: String, jsonBody: String) {
        val bytes = jsonBody.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $code $reason\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private data class HttpRequestData(
        val method: String,
        val path: String,
        val body: String
    )

    private fun readHttpRequest(rawInput: InputStream): HttpRequestData? {
        val buffered = BufferedInputStream(rawInput)
        val headerBuf = ByteArrayOutputStream()
        var prev1 = 0
        var prev2 = 0
        var prev3 = 0
        while (true) {
            val b = buffered.read()
            if (b == -1) break
            headerBuf.write(b)
            if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) break
            if (prev1 == '\n'.code && b == '\n'.code) break
            prev3 = prev2
            prev2 = prev1
            prev1 = b
            if (headerBuf.size() > 32 * 1024) break
        }
        val headerText = headerBuf.toString("UTF-8")
        if (headerText.isBlank()) return null
        val lines = headerText.split("\r\n", "\n")
        val requestLine = lines.firstOrNull()?.split(" ") ?: return null
        if (requestLine.size < 2) return null
        val method = requestLine[0].trim().uppercase()
        val rawTarget = requestLine[1].trim()
        val path = rawTarget.substringBefore("?").ifBlank { "/" }
        var contentLength = 0
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
            }
        }
        val body = if (contentLength > 0 && contentLength < 1_048_576) {
            val bytes = ByteArray(contentLength)
            var total = 0
            while (total < contentLength) {
                val read = buffered.read(bytes, total, contentLength - total)
                if (read == -1) break
                total += read
            }
            String(bytes, 0, total, Charsets.UTF_8)
        } else ""
        return HttpRequestData(method = method, path = path, body = body)
    }
}
