package com.jarvisai.assistant.agent.agy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Phase 3: Lightweight local HTTP static & live artifact preview server (`CloudPreviewServer`)
 * for Antigravity workspace web apps and generated HTML/JS/CSS artifacts.
 */
object CloudPreviewServer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _previewUrl = MutableStateFlow<String?>(null)
    val previewUrl: StateFlow<String?> = _previewUrl.asStateFlow()

    private var rootDirectory: File? = null

    fun start(rootDir: File, port: Int = DEFAULT_PREVIEW_PORT): String {
        if (_isRunning.value && rootDirectory?.absolutePath == rootDir.absolutePath) {
            return _previewUrl.value ?: "http://127.0.0.1:$port"
        }
        stop()
        rootDirectory = rootDir
        val url = "http://127.0.0.1:$port"

        acceptJob = scope.launch {
            try {
                val socket = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
                serverSocket = socket
                _isRunning.value = true
                _previewUrl.value = url

                while (isActive && !socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    launch { handleClient(client) }
                }
            } catch (_: Exception) {
                _isRunning.value = false
            }
        }
        return url
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        _isRunning.value = false
        _previewUrl.value = null
    }

    private fun handleClient(client: Socket) {
        client.use { sock ->
            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            val rawPath = if (parts.size >= 2) parts[1] else "/"
            val cleanPath = URLDecoder.decode(rawPath.substringBefore("?"), "UTF-8")
                .removePrefix("/")
                .ifBlank { "index.html" }

            val base = rootDirectory ?: return
            val targetFile = File(base, cleanPath).canonicalFile
            val output = sock.getOutputStream()

            if (!targetFile.path.startsWith(base.canonicalPath) || !targetFile.exists() || targetFile.isDirectory) {
                writeResponse(
                    output = output,
                    status = "404 Not Found",
                    contentType = "text/html; charset=utf-8",
                    body = "<html><body style='background:#0D1117;color:#E6EDF3;font-family:sans-serif;padding:24px;'><h3>404 - Preview File Not Found</h3><p>$cleanPath</p></body></html>".toByteArray()
                )
                return
            }

            val mime = guessMimeType(targetFile.name)
            writeResponse(
                output = output,
                status = "200 OK",
                contentType = mime,
                body = targetFile.readBytes()
            )
        }
    }

    private fun writeResponse(output: OutputStream, status: String, contentType: String, body: ByteArray) {
        val headers = buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.write(body)
        output.flush()
    }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "html", "htm" -> "text/html; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "js", "mjs" -> "application/javascript; charset=utf-8"
            "json" -> "application/json; charset=utf-8"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "text/plain; charset=utf-8"
        }
    }

    const val DEFAULT_PREVIEW_PORT = 4321
}
