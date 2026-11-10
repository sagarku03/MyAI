package com.jarvisai.assistant.mcp.bridges

import com.jarvisai.assistant.extension.BrowserExtensionManager
import org.json.JSONArray
import org.json.JSONObject

object FlowMcpBridge {

    fun listTools(): JSONArray = JSONArray().apply {
        put(JSONObject().apply {
            put("name", "flow_status")
            put("description", "Checks Google Flow authentication and credit status")
            put("inputSchema", JSONObject().apply { put("type", "object") })
        })
        put(JSONObject().apply {
            put("name", "flow_generate_image")
            put("description", "Generates an AI image from a prompt using Google Flow models")
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("prompt", JSONObject().apply { put("type", "string") })
                    put("aspectRatio", JSONObject().apply { put("type", "string") })
                })
                put("required", JSONArray(listOf("prompt")))
            })
        })
        put(JSONObject().apply {
            put("name", "flow_generate_video")
            put("description", "Generates video from prompt using Google Flow models")
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("prompt", JSONObject().apply { put("type", "string") })
                })
                put("required", JSONArray(listOf("prompt")))
            })
        })
        put(JSONObject().apply {
            put("name", "deepx_rapid_batch_dispatch")
            put("description", "Dispatches a rapid batch of prompts to the Google Flow / DeepX pipeline")
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("prompts", JSONObject().apply {
                        put("type", "array")
                        put("items", JSONObject().apply { put("type", "string") })
                    })
                })
                put("required", JSONArray(listOf("prompts")))
            })
        })
        put(JSONObject().apply {
            put("name", "deepx_rapid_storyboard_autopilot")
            put("description", "Autopilot script breakdown and storyboard generation pipeline")
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("script", JSONObject().apply { put("type", "string") })
                })
                put("required", JSONArray(listOf("script")))
            })
        })
    }

    suspend fun callTool(name: String, args: JSONObject): JSONObject {
        val res = BrowserExtensionManager.executeTool("google-flow", name, args)
        return res.fold(
            onSuccess = { JSONObject(it) },
            onFailure = { JSONObject().apply { put("error", it.message) } }
        )
    }
}
