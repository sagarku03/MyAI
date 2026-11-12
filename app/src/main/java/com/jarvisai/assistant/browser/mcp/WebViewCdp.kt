package com.jarvisai.assistant.browser.mcp

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Process
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class CdpException(message: String) : Exception(message)

/**
 * Direct Unix Domain Socket connection to Android WebView's DevTools socket
 * (`webview_devtools_remote_<pid>`). Enables trusted mouse, touch, keyboard, and screenshot capture.
 */
class CdpSession private constructor(
    private val socket: LocalSocket,
    val targetId: String,
    private val useWebSocketFraming: Boolean = false
) {
    private val input = BufferedInputStream(socket.inputStream, 64 * 1024)
    private val output: OutputStream = socket.outputStream
    private val ids = AtomicInteger()
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
    private val writeMutex = Mutex()

    @Volatile
    var closed = false
        private set

    companion object {
        private const val TAG = "WebViewCdp"

        /**
         * Queries `/json/list` on `webview_devtools_remote_<pid>` to discover active WebView targets.
         */
        suspend fun listTargets(): JSONArray = withContext(Dispatchers.IO) {
            val socket = LocalSocket()
            try {
                val socketName = "webview_devtools_remote_${Process.myPid()}"
                socket.connect(LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT))
                socket.soTimeout = 5000
                val out = socket.outputStream
                val req = "GET /json/list HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
                out.write(req.toByteArray(Charsets.UTF_8))
                out.flush()

                val responseText = socket.inputStream.bufferedReader().readText()
                val bodyStart = responseText.indexOf("\r\n\r\n")
                if (bodyStart != -1) {
                    val body = responseText.substring(bodyStart + 4).trim()
                    if (body.startsWith("[")) {
                        return@withContext JSONArray(body)
                    }
                }
                JSONArray()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to list CDP targets: ${e.message}")
                JSONArray()
            } finally {
                runCatching { socket.close() }
            }
        }

        suspend fun connect(targetId: String = ""): CdpSession = withContext(Dispatchers.IO) {
            val resolvedTargetId = targetId.ifBlank {
                val targets = listTargets()
                if (targets.length() > 0) {
                    targets.optJSONObject(0)?.optString("id", "").orEmpty()
                } else {
                    ""
                }
            }

            val socket = LocalSocket()
            val socketName = "webview_devtools_remote_${Process.myPid()}"
            socket.connect(LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT))

            var upgradedToWs = false
            if (resolvedTargetId.isNotBlank()) {
                val keyBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val wsKey = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
                val handshake = buildString {
                    append("GET /devtools/page/$resolvedTargetId HTTP/1.1\r\n")
                    append("Host: localhost\r\n")
                    append("Upgrade: websocket\r\n")
                    append("Connection: Upgrade\r\n")
                    append("Sec-WebSocket-Key: $wsKey\r\n")
                    append("Sec-WebSocket-Version: 13\r\n\r\n")
                }
                socket.outputStream.write(handshake.toByteArray(Charsets.UTF_8))
                socket.outputStream.flush()

                // Read HTTP 101 Switching Protocols headers
                val headerBuf = ByteArrayOutputStream()
                var prev = 0
                val ins = socket.inputStream
                while (true) {
                    val b = ins.read()
                    if (b == -1) break
                    headerBuf.write(b)
                    if (prev == '\n'.code && b == '\n'.code) break
                    if (prev == '\n'.code && b == '\r'.code) {
                        val next = ins.read()
                        if (next != -1) headerBuf.write(next)
                        break
                    }
                    if (b != '\r'.code) prev = b
                }
                val headerStr = headerBuf.toString(Charsets.UTF_8.name())
                upgradedToWs = headerStr.contains("101")
            }

            val session = CdpSession(socket, resolvedTargetId, upgradedToWs)
            session.startReader()
            session
        }
    }

    private fun startReader() {
        thread(name = "cdp-reader", isDaemon = true) {
            try {
                if (useWebSocketFraming) {
                    while (!closed) {
                        val payload = readWebSocketFrame() ?: break
                        if (payload.startsWith("{")) {
                            val json = JSONObject(payload)
                            val id = json.optInt("id", -1)
                            if (id != -1) {
                                pending.remove(id)?.complete(json)
                            }
                        }
                    }
                } else {
                    val reader = input.bufferedReader()
                    while (!closed) {
                        val line = reader.readLine() ?: break
                        if (line.startsWith("{")) {
                            val json = JSONObject(line)
                            val id = json.optInt("id", -1)
                            if (id != -1) {
                                pending.remove(id)?.complete(json)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (!closed) Log.w(TAG, "CDP reader terminated: ${e.message}")
            } finally {
                close()
            }
        }
    }

    private fun readWebSocketFrame(): String? {
        val b0 = input.read()
        if (b0 == -1) return null
        val b1 = input.read()
        if (b1 == -1) return null

        val masked = (b1 and 0x80) != 0
        var length = (b1 and 0x7F).toLong()
        if (length == 126L) {
            val hi = input.read()
            val lo = input.read()
            if (hi == -1 || lo == -1) return null
            length = ((hi and 0xFF) shl 8 or (lo and 0xFF)).toLong()
        } else if (length == 127L) {
            var l = 0L
            for (i in 0 until 8) {
                val b = input.read()
                if (b == -1) return null
                l = (l shl 8) or (b.toLong() and 0xFF)
            }
            length = l
        }

        val maskKey = ByteArray(4)
        if (masked) {
            var read = 0
            while (read < 4) {
                val r = input.read(maskKey, read, 4 - read)
                if (r == -1) return null
                read += r
            }
        }

        val payload = ByteArray(length.toInt())
        var total = 0
        while (total < payload.size) {
            val r = input.read(payload, total, payload.size - total)
            if (r == -1) return null
            total += r
        }

        if (masked) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
            }
        }
        return String(payload, Charsets.UTF_8)
    }

    private fun writeWebSocketFrame(text: String) {
        val payload = text.toByteArray(Charsets.UTF_8)
        val mask = ByteArray(4).also { SecureRandom().nextBytes(it) }
        val frame = ByteArrayOutputStream()
        frame.write(0x81) // FIN + text frame

        when {
            payload.size <= 125 -> {
                frame.write(0x80 or payload.size)
            }
            payload.size <= 65535 -> {
                frame.write(0x80 or 126)
                frame.write((payload.size shr 8) and 0xFF)
                frame.write(payload.size and 0xFF)
            }
            else -> {
                frame.write(0x80 or 127)
                val len = payload.size.toLong()
                for (i in 7 downTo 0) {
                    frame.write(((len shr (i * 8)) and 0xFF).toInt())
                }
            }
        }

        frame.write(mask)
        val maskedPayload = ByteArray(payload.size)
        for (i in payload.indices) {
            maskedPayload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
        }
        frame.write(maskedPayload)
        output.write(frame.toByteArray())
        output.flush()
    }

    suspend fun send(method: String, params: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        if (closed) throw CdpException("CDP session is closed")
        val id = ids.incrementAndGet()
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred

        val req = JSONObject()
            .put("id", id)
            .put("method", method)
            .put("params", params)

        writeMutex.withLock {
            synchronized(output) {
                if (useWebSocketFraming) {
                    writeWebSocketFrame(req.toString())
                } else {
                    output.write((req.toString() + "\n").toByteArray(Charsets.UTF_8))
                    output.flush()
                }
            }
        }

        withTimeout(15000) {
            val resp = deferred.await()
            if (resp.has("error")) {
                throw CdpException(resp.getJSONObject("error").optString("message", "CDP Error"))
            }
            resp.optJSONObject("result") ?: JSONObject()
        }
    }

    suspend fun dispatchMouseClick(x: Double, y: Double) {
        val pressParams = JSONObject()
            .put("type", "mousePressed")
            .put("x", x)
            .put("y", y)
            .put("button", "left")
            .put("clickCount", 1)
        send("Input.dispatchMouseEvent", pressParams)

        val releaseParams = JSONObject()
            .put("type", "mouseReleased")
            .put("x", x)
            .put("y", y)
            .put("button", "left")
            .put("clickCount", 1)
        send("Input.dispatchMouseEvent", releaseParams)
    }

    suspend fun dispatchMouseMove(x: Double, y: Double) {
        val moveParams = JSONObject()
            .put("type", "mouseMoved")
            .put("x", x)
            .put("y", y)
        send("Input.dispatchMouseEvent", moveParams)
    }

    suspend fun dispatchInsertText(text: String) {
        val params = JSONObject().put("text", text)
        send("Input.insertText", params)
    }

    suspend fun dispatchKey(key: String) {
        val chord = BrowserKeys.resolveChord(key)
        val def = chord.keyDef
        val down = JSONObject()
            .put("type", if (def.text != null) "keyDown" else "rawKeyDown")
            .put("key", def.key)
            .put("code", def.code)
            .put("windowsVirtualKeyCode", def.keyCode)
            .put("nativeVirtualKeyCode", def.keyCode)
            .put("modifiers", chord.modifiers)
        if (!def.text.isNullOrEmpty()) {
            down.put("text", def.text)
            down.put("unmodifiedText", def.text)
        }
        send("Input.dispatchKeyEvent", down)

        val up = JSONObject()
            .put("type", "keyUp")
            .put("key", def.key)
            .put("code", def.code)
            .put("windowsVirtualKeyCode", def.keyCode)
            .put("nativeVirtualKeyCode", def.keyCode)
            .put("modifiers", chord.modifiers)
        send("Input.dispatchKeyEvent", up)
    }

    suspend fun captureScreenshotBase64(quality: Int = 80): String {
        val params = JSONObject()
            .put("format", "jpeg")
            .put("quality", quality)
        val res = send("Page.captureScreenshot", params)
        return res.optString("data", "")
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching { socket.close() }
        pending.values.forEach { it.cancel() }
        pending.clear()
    }
}
