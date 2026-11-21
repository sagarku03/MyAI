package com.jarvisai.assistant.terminal

import java.io.ByteArrayOutputStream

/**
 * Byte-level streaming parser for `tmux -CC` (Control Mode) notifications and responses.
 * Handles `%output`, `%begin`/`%end`/`%error`, `%window-add`, `%window-close`,
 * `%window-renamed`, `%session-window-changed`, and `%exit`, plus octal/hex codecs
 * and screen-title OSC stripping.
 */
class TmuxControlParser(
    private val onOutput: (paneId: String, data: ByteArray) -> Unit,
    private val onBlockResponse: (blockNum: Long, isError: Boolean, lines: List<String>) -> Unit = { _, _, _ -> },
    private val onWindowAdded: (windowId: String) -> Unit = {},
    private val onWindowClosed: (windowId: String) -> Unit = {},
    private val onWindowRenamed: (windowId: String, name: String) -> Unit = { _, _ -> },
    private val onActiveWindowChanged: (sessionId: String, windowId: String) -> Unit = { _, _ -> },
    private val onExit: (reason: String) -> Unit = {}
) {
    private val lineBuffer = ByteArrayOutputStream(4096)
    private var inBlock = false
    private var currentBlockNum = -1L
    private val currentBlockLines = mutableListOf<String>()

    fun feed(bytes: ByteArray, length: Int = bytes.size) {
        for (i in 0 until length) {
            val b = bytes[i]
            if (b == '\n'.code.toByte()) {
                val rawLine = lineBuffer.toByteArray()
                lineBuffer.reset()
                processRawLine(rawLine)
            } else if (b != '\r'.code.toByte()) {
                lineBuffer.write(b.toInt() and 0xFF)
            }
        }
    }

    fun reset() {
        lineBuffer.reset()
        inBlock = false
        currentBlockNum = -1L
        currentBlockLines.clear()
    }

    private fun processRawLine(rawLine: ByteArray) {
        if (rawLine.isEmpty()) return
        val line = rawLine.toString(Charsets.UTF_8)

        if (line.startsWith("%begin ")) {
            val parts = line.split(" ")
            currentBlockNum = parts.getOrNull(2)?.toLongOrNull() ?: -1L
            currentBlockLines.clear()
            inBlock = true
            return
        }

        if (line.startsWith("%end ")) {
            val parts = line.split(" ")
            val blockNum = parts.getOrNull(2)?.toLongOrNull() ?: currentBlockNum
            val lines = currentBlockLines.toList()
            inBlock = false
            currentBlockLines.clear()
            onBlockResponse(blockNum, false, lines)
            return
        }

        if (line.startsWith("%error ")) {
            val parts = line.split(" ")
            val blockNum = parts.getOrNull(2)?.toLongOrNull() ?: currentBlockNum
            val lines = currentBlockLines.toList()
            inBlock = false
            currentBlockLines.clear()
            onBlockResponse(blockNum, true, lines)
            return
        }

        if (inBlock) {
            currentBlockLines.add(line)
            return
        }

        when {
            line.startsWith("%output ") -> {
                val afterPrefix = line.substring(8)
                val spaceIdx = afterPrefix.indexOf(' ')
                if (spaceIdx > 0) {
                    val paneId = afterPrefix.substring(0, spaceIdx)
                    val encodedPayload = afterPrefix.substring(spaceIdx + 1)
                    val decoded = decodeTmuxOctal(encodedPayload)
                    onOutput(paneId, decoded)
                }
            }
            line.startsWith("%window-add ") -> {
                val windowId = line.removePrefix("%window-add ").trim().substringBefore(' ')
                if (windowId.isNotEmpty()) onWindowAdded(windowId)
            }
            line.startsWith("%window-close ") -> {
                val windowId = line.removePrefix("%window-close ").trim().substringBefore(' ')
                if (windowId.isNotEmpty()) onWindowClosed(windowId)
            }
            line.startsWith("%unlinked-window-close ") -> {
                val windowId = line.removePrefix("%unlinked-window-close ").trim().substringBefore(' ')
                if (windowId.isNotEmpty()) onWindowClosed(windowId)
            }
            line.startsWith("%window-renamed ") -> {
                val rest = line.removePrefix("%window-renamed ").trim()
                val spaceIdx = rest.indexOf(' ')
                if (spaceIdx > 0) {
                    val windowId = rest.substring(0, spaceIdx)
                    val newName = rest.substring(spaceIdx + 1)
                    onWindowRenamed(windowId, newName)
                }
            }
            line.startsWith("%session-window-changed ") -> {
                val parts = line.removePrefix("%session-window-changed ").trim().split(" ")
                if (parts.size >= 2) {
                    onActiveWindowChanged(parts[0], parts[1])
                }
            }
            line.startsWith("%exit") -> {
                val reason = line.removePrefix("%exit").trim()
                onExit(reason)
            }
        }
    }

    companion object {
        private val OSC_TITLE_REGEX = Regex("""\u001B\][0-9]*;[^\u0007\u001B]*(?:\u0007|\u001B\\)""")

        /**
         * Decodes tmux `%output` octal escape sequences (`\015`, `\033`, `\134`) into exact raw bytes.
         */
        fun decodeTmuxOctal(encoded: String): ByteArray {
            val out = ByteArrayOutputStream(encoded.length)
            var i = 0
            val len = encoded.length
            while (i < len) {
                val c = encoded[i]
                if (c == '\\' && i + 3 < len) {
                    val d1 = encoded[i + 1]
                    val d2 = encoded[i + 2]
                    val d3 = encoded[i + 3]
                    if (d1 in '0'..'7' && d2 in '0'..'7' && d3 in '0'..'7') {
                        val value = ((d1 - '0') shl 6) or ((d2 - '0') shl 3) or (d3 - '0')
                        out.write(value and 0xFF)
                        i += 4
                        continue
                    }
                }
                if (c.code < 0x80) {
                    out.write(c.code)
                } else {
                    val utf8 = c.toString().toByteArray(Charsets.UTF_8)
                    out.write(utf8)
                }
                i++
            }
            return out.toByteArray()
        }

        /**
         * Encodes arbitrary UTF-8 string into space-separated hex tokens for `tmux send-keys -H`.
         */
        fun encodeToHex(text: String): String = encodeBytesToHex(text.toByteArray(Charsets.UTF_8))

        fun encodeBytesToHex(bytes: ByteArray): String {
            if (bytes.isEmpty()) return ""
            return bytes.joinToString(" ") { b ->
                String.format("%02x", b.toInt() and 0xFF)
            }
        }

        /**
         * Removes terminal OSC title-change escape sequences when restoring tmux capture history.
         */
        fun stripScreenTitle(input: String): String {
            return OSC_TITLE_REGEX.replace(input, "")
        }
    }
}
