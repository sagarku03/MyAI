package com.jarvisai.assistant.mcp.bridges

import com.jarvisai.assistant.terminal.LocalTerminalBridge
import com.jarvisai.assistant.terminal.LocalTerminalManager
import org.json.JSONArray
import org.json.JSONObject

object TerminalMcpBridge {

    fun listTools(): JSONArray = JSONArray().apply {
        put(JSONObject().apply {
            put("name", "terminal_send_command")
            put("description", "Executes a shell command inside the native Linux/Termux environment")
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("command", JSONObject().apply { put("type", "string") })
                })
                put("required", JSONArray(listOf("command")))
            })
        })
        put(JSONObject().apply {
            put("name", "terminal_list_sessions")
            put("description", "Lists active terminal PTY sessions")
            put("inputSchema", JSONObject().apply { put("type", "object") })
        })
    }

    suspend fun callTool(name: String, args: JSONObject): JSONObject = JSONObject().apply {
        when (name) {
            "terminal_send_command" -> {
                val cmd = args.getString("command")
                val res = LocalTerminalBridge.instance.executeCommand(cmd)
                put("exitCode", res.exitCode)
                put("output", res.output)
                put("durationMs", res.durationMs)
            }
            "terminal_list_sessions" -> {
                put("sessions", LocalTerminalBridge.instance.listActiveSessionsJson())
            }
        }
    }
}
