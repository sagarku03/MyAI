package com.jarvisai.assistant.mcp.bridges

import com.jarvisai.assistant.service.WorkingMemoryBlackboard
import org.json.JSONArray
import org.json.JSONObject

object JarvisMemoryMcpBridge {

    fun listTools(): JSONArray = JSONArray().apply {
        put(JSONObject().apply {
            put("name", "jarvis_memory_read")
            put("description", "Reads active context from Jarvis 5-Tier Memory Blackboard")
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("key", JSONObject().apply { put("type", "string") })
                })
            })
        })
        put(JSONObject().apply {
            put("name", "jarvis_memory_write")
            put("description", "Writes a persistent observation or fact into Jarvis Memory Blackboard")
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("key", JSONObject().apply { put("type", "string") })
                    put("value", JSONObject().apply { put("type", "string") })
                })
                put("required", JSONArray(listOf("key", "value")))
            })
        })
    }

    suspend fun callTool(name: String, args: JSONObject): JSONObject = JSONObject().apply {
        when (name) {
            "jarvis_memory_read" -> {
                val key = args.optString("key")
                if (key.isNotBlank()) {
                    put("value", WorkingMemoryBlackboard.get(key) ?: JSONObject.NULL)
                } else {
                    put("all_memories", WorkingMemoryBlackboard.getAllJson())
                }
            }
            "jarvis_memory_write" -> {
                val key = args.getString("key")
                val value = args.getString("value")
                WorkingMemoryBlackboard.put(key, value)
                put("saved", true)
            }
        }
    }
}
