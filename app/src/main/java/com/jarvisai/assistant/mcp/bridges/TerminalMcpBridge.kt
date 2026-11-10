package com.jarvisai.assistant.mcp.bridges

import com.jarvisai.assistant.terminal.LocalTerminalBridge
import com.jarvisai.assistant.terminal.LocalTerminalManager
import com.jarvisai.assistant.terminal.WorkspaceSyncManager
import org.json.JSONArray
import org.json.JSONObject

object TerminalMcpBridge {

    fun listTools(): JSONArray = JSONArray().apply {
        put(JSONObject().apply {
            put("name", "terminal_list_sessions")
            put("description", "Lists all active terminal PTY sessions")
            put("inputSchema", JSONObject().apply { put("type", "object") })
        })
        put(JSONObject().apply {
            put("name", "terminal_send_command")
            put("description", "Executes a shell command inside the native Linux/Termux environment")
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("command", JSONObject().apply { put("type", "string") })
                    put("timeoutMs", JSONObject().apply { put("type", "integer") })
                })
                put("required", JSONArray(listOf("command")))
            })
        })
        put(JSONObject().apply {
            put("name", "terminal_read_output")
            put("description", "Reads output buffer of active terminal session")
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("sessionId", JSONObject().apply { put("type", "string") })
                    put("maxLines", JSONObject().apply { put("type", "integer") })
                })
            })
        })
        put(JSONObject().apply {
            put("name", "terminal_create_session")
            put("description", "Creates a new bash terminal PTY session")
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("title", JSONObject().apply { put("type", "string") })
                    put("initialCommand", JSONObject().apply { put("type", "string") })
                })
            })
        })
        put(JSONObject().apply {
            put("name", "terminal_kill_process")
            put("description", "Sends SIGINT (Ctrl+C) or terminates active process in terminal session")
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("sessionId", JSONObject().apply { put("type", "string") })
                })
            })
        })
        put(JSONObject().apply {
            put("name", "terminal_close_session")
            put("description", "Closes and terminates a terminal session")
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("sessionId", JSONObject().apply { put("type", "string") })
                })
                put("required", JSONArray(listOf("sessionId")))
            })
        })
    }

    suspend fun callTool(name: String, args: JSONObject): JSONObject = JSONObject().apply {
        when (name) {
            "terminal_list_sessions" -> {
                put("sessions", LocalTerminalBridge.instance.listActiveSessionsJson())
            }
            "terminal_send_command" -> {
                val cmd = args.getString("command")
                val timeout = args.optLong("timeoutMs", 60000L)
                val res = LocalTerminalBridge.instance.executeCommand(cmd, timeout)
                put("exitCode", res.exitCode)
                put("output", res.output)
                put("durationMs", res.durationMs)
                WorkspaceSyncManager.notifyExternalChange("/data/data/com.termux/files/home")
            }
            "terminal_read_output" -> {
                val pty = LocalTerminalManager.getActiveSession()
                val transcript = try {
                    pty?.terminalSession?.emulator?.screen?.transcriptText ?: ""
                } catch (_: Exception) { "" }
                put("output", transcript.takeLast(2000))
            }
            "terminal_create_session" -> {
                val session = LocalTerminalManager.getActiveSession()
                put("sessionId", session?.id ?: "new")
                put("status", "CREATED")
            }
            "terminal_kill_process" -> {
                val pty = LocalTerminalManager.getActiveSession()
                pty?.write("\u0003") // SIGINT
                put("killed", true)
            }
            "terminal_close_session" -> {
                val sessionId = args.getString("sessionId")
                LocalTerminalManager.closeSession(sessionId)
                put("closed", true)
            }
            else -> {
                put("error", "Unknown terminal tool: $name")
            }
        }
    }
}
