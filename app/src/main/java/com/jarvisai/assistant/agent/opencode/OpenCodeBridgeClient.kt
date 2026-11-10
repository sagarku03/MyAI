package com.jarvisai.assistant.agent.opencode

import com.jarvisai.assistant.agent.core.AgyBridgeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class OpenCodeProvider(
    val id: String,
    val name: String,
    val kind: String = "openai",
    val baseUrl: String,
    val apiKey: String? = null,
    val models: List<String> = emptyList()
)

data class OpenCodeCliStatus(
    val installed: Boolean,
    val running: Boolean,
    val version: String? = null,
    val error: String? = null
)

class OpenCodeBridgeClient(private val bridge: AgyBridgeService = AgyBridgeService.instance) {

    suspend fun getStatus(): Result<OpenCodeCliStatus> = withContext(Dispatchers.IO) {
        bridge.get("/api/opencode/status").map { text ->
            val json = JSONObject(text)
            OpenCodeCliStatus(
                installed = json.optBoolean("installed", false),
                running = json.optBoolean("running", false),
                version = json.optString("version", null),
                error = json.optString("error", null)
            )
        }
    }

    suspend fun listProviders(): Result<List<OpenCodeProvider>> = withContext(Dispatchers.IO) {
        bridge.get("/api/opencode/providers").map { text ->
            val json = JSONObject(text)
            val list = mutableListOf<OpenCodeProvider>()
            val array = json.optJSONArray("providers") ?: JSONArray()
            for (i in 0 until array.length()) {
                val p = array.getJSONObject(i)
                val modelsList = mutableListOf<String>()
                val modelsArray = p.optJSONArray("models") ?: JSONArray()
                for (j in 0 until modelsArray.length()) {
                    modelsList.add(modelsArray.getJSONObject(j).optString("id"))
                }
                list.add(
                    OpenCodeProvider(
                        id = p.getString("id"),
                        name = p.getString("name"),
                        kind = p.optString("kind", "openai"),
                        baseUrl = p.getString("base_url"),
                        models = modelsList
                    )
                )
            }
            list
        }
    }

    suspend fun addCustomProvider(provider: OpenCodeProvider): Result<Unit> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("id", provider.id)
            put("name", provider.name)
            put("kind", provider.kind)
            put("base_url", provider.baseUrl)
            put("api_key", provider.apiKey ?: "")
        }
        bridge.post("/api/opencode/providers", payload.toString()).map { Unit }
    }

    suspend fun installOrUpdateCli(): Result<String> = withContext(Dispatchers.IO) {
        bridge.post("/api/opencode/install", "{}")
    }
}
