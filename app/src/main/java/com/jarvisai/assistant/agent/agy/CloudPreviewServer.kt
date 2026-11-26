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

        val socket = runCatching {
            ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
        }.getOrElse {
            runCatching { ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")) }.getOrNull()
        }

        if (socket == null) {
            _isRunning.value = false
            return "http://127.0.0.1:$port"
        }

        val boundPort = socket.localPort
        val url = "http://127.0.0.1:$boundPort"
        serverSocket = socket
        _isRunning.value = true
        _previewUrl.value = url

        acceptJob = scope.launch {
            try {
                while (isActive && !socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    launch { runCatching { handleClient(client) } }
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
            var requestOrigin: String? = null
            while (true) {
                val hdr = reader.readLine() ?: break
                if (hdr.isEmpty()) break
                if (hdr.startsWith("Origin:", ignoreCase = true)) {
                    requestOrigin = hdr.substringAfter(":").trim()
                }
            }
            val allowOrigin = sanitizeLoopbackOrigin(requestOrigin)
            if (requestOrigin != null && allowOrigin == null) {
                writeResponse(
                    output = sock.getOutputStream(),
                    status = "403 Forbidden",
                    contentType = "text/plain; charset=utf-8",
                    body = "Forbidden origin".toByteArray(Charsets.UTF_8),
                    allowOrigin = null
                )
                return
            }

            val parts = requestLine.split(" ")
            val rawPath = if (parts.size >= 2) parts[1] else "/"
            val cleanPath = URLDecoder.decode(rawPath.substringBefore("?"), "UTF-8")
                .removePrefix("/")
                .ifBlank { "index.html" }

            val base = rootDirectory ?: return
            val baseCanonical = base.canonicalPath
            val basePrefix = if (baseCanonical.endsWith(File.separator)) baseCanonical else baseCanonical + File.separator
            val targetFile = File(base, cleanPath).canonicalFile
            val output = sock.getOutputStream()

            if (!targetFile.path.startsWith(basePrefix) || !targetFile.exists() || targetFile.isDirectory) {
                val safePathHtml = escapeHtml(cleanPath)
                writeResponse(
                    output = output,
                    status = "404 Not Found",
                    contentType = "text/html; charset=utf-8",
                    body = "<html><body style='background:#0D1117;color:#E6EDF3;font-family:sans-serif;padding:24px;'><h3>404 - Preview File Not Found</h3><p>$safePathHtml</p></body></html>".toByteArray(Charsets.UTF_8),
                    allowOrigin = allowOrigin
                )
                return
            }

            if (targetFile.length() > MAX_PREVIEW_BYTES) {
                writeResponse(
                    output = output,
                    status = "413 Payload Too Large",
                    contentType = "text/plain; charset=utf-8",
                    body = "Preview file exceeds 32 MB limit".toByteArray(Charsets.UTF_8),
                    allowOrigin = allowOrigin
                )
                return
            }

            val mime = guessMimeType(targetFile.name)
            writeResponse(
                output = output,
                status = "200 OK",
                contentType = mime,
                body = targetFile.readBytes(),
                allowOrigin = allowOrigin
            )
        }
    }

    private fun writeResponse(
        output: OutputStream,
        status: String,
        contentType: String,
        body: ByteArray,
        allowOrigin: String?
    ) {
        val headers = buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            if (!allowOrigin.isNullOrBlank()) {
                append("Access-Control-Allow-Origin: $allowOrigin\r\n")
                append("Vary: Origin\r\n")
            }
            append("X-Content-Type-Options: nosniff\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.write(body)
        output.flush()
    }

    private fun sanitizeLoopbackOrigin(origin: String?): String? {
        val o = origin?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (
            o.startsWith("http://127.0.0.1") ||
            o.startsWith("http://localhost") ||
            o.startsWith("https://127.0.0.1") ||
            o.startsWith("https://localhost")
        ) o else null
    }

    private fun escapeHtml(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }

    private const val MAX_PREVIEW_BYTES = 32L * 1024L * 1024L

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

    const val DEFAULT_PREVIEW_PORT = com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_WEB_PREVIEW_PORT // 2239
}
