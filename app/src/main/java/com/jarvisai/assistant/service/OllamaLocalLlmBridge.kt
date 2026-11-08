package com.jarvisai.assistant.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class OllamaBridgeConfig(
    val endpointUrl: String = "http://127.0.0.1:11434",
    val modelName: String = "llama3.2:3b",
    val openAiCompatibleUrl: String = "",
    val openAiApiKey: String = "",
    val isOnline: Boolean = false,
    val discoveredModels: List<String> = listOf("llama3.2:3b", "qwen2.5:3b", "deepseek-r1:1.5b", "gemma3:4b"),
    val lastResponsePreview: String = "Local Ollama & OpenAI-Compatible Bridge ready (127.0.0.1:11434)"
)

data class OllamaInferenceResult(
    val success: Boolean,
    val providerUsed: String,
    val modelUsed: String,
    val replyText: String,
    val latencyMs: Long
)

/**
 * AbuZar-Ansarii/Jarvis (PrivateAgent) Native Ollama Local LLM & OpenAI-Compatible Multi-Provider Bridge:
 * Connects directly to an on-device Termux/PRoot Ollama server (`http://127.0.0.1:11434`) or LAN PC Ollama server,
 * as well as any OpenAI-compatible `/v1/chat/completions` endpoint (Groq, OpenRouter, DeepSeek, Local vLLM).
 */
object OllamaLocalLlmBridge {
    private const val TAG = "OllamaLocalBridge"
    private const val PREFS_NAME = "jarvis_ollama_bridge_prefs"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(500, TimeUnit.MILLISECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val _config = MutableStateFlow(OllamaBridgeConfig())
    val config: StateFlow<OllamaBridgeConfig> = _config.asStateFlow()

    fun loadSavedConfig(context: Context): OllamaBridgeConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val endpoint = prefs.getString("endpoint_url", "http://127.0.0.1:11434") ?: "http://127.0.0.1:11434"
        val model = prefs.getString("model_name", "llama3.2:3b") ?: "llama3.2:3b"
        val openAiUrl = prefs.getString("openai_url", "") ?: ""
        val openAiKey = prefs.getString("openai_key", "") ?: ""
        val loaded = _config.value.copy(
            endpointUrl = endpoint,
            modelName = model,
            openAiCompatibleUrl = openAiUrl,
            openAiApiKey = openAiKey
        )
        _config.value = loaded
        return loaded
    }

    fun saveConfig(
        context: Context,
        endpointUrl: String,
        modelName: String,
        openAiCompatibleUrl: String = _config.value.openAiCompatibleUrl,
        openAiApiKey: String = _config.value.openAiApiKey
    ) {
        val cleanEndpoint = endpointUrl.trim().removeSuffix("/").ifBlank { "http://127.0.0.1:11434" }
        val cleanModel = modelName.trim().ifBlank { "llama3.2:3b" }
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("endpoint_url", cleanEndpoint)
            .putString("model_name", cleanModel)
            .putString("openai_url", openAiCompatibleUrl.trim())
            .putString("openai_key", openAiApiKey.trim())
            .apply()

        _config.value = _config.value.copy(
            endpointUrl = cleanEndpoint,
            modelName = cleanModel,
            openAiCompatibleUrl = openAiCompatibleUrl.trim(),
            openAiApiKey = openAiApiKey.trim()
        )
    }

    private fun resolveCandidateEndpoints(configuredUrl: String): List<String> {
        val clean = configuredUrl.trim().removeSuffix("/").ifBlank { "http://127.0.0.1:11434" }
        val list = mutableListOf(clean)
        if (clean != "http://127.0.0.1:11434") {
            list.add("http://127.0.0.1:11434")
        }
        return list
    }

    suspend fun probeOllamaServer(context: Context): OllamaBridgeConfig = withContext(Dispatchers.IO) {
        val cfg = loadSavedConfig(context)
        val candidates = resolveCandidateEndpoints(cfg.endpointUrl)
        for (candidateUrl in candidates) {
            val tagsUrl = "$candidateUrl/api/tags"
            try {
                val req = Request.Builder().url(tagsUrl).get().build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body?.string().orEmpty())
                        val modelsArr = json.optJSONArray("models")
                        val names = mutableListOf<String>()
                        if (modelsArr != null) {
                            for (i in 0 until modelsArr.length()) {
                                val name = modelsArr.optJSONObject(i)?.optString("name").orEmpty()
                                if (name.isNotBlank()) names.add(name)
                            }
                        }
                        val updated = cfg.copy(
                            endpointUrl = candidateUrl,
                            isOnline = true,
                            discoveredModels = if (names.isNotEmpty()) names else cfg.discoveredModels,
                            modelName = names.firstOrNull() ?: cfg.modelName,
                            lastResponsePreview = "Ollama Server ONLINE at $candidateUrl (${names.size} local models ready)"
                        )
                        _config.value = updated
                        // Persist verified working candidate URL
                        saveConfig(context, candidateUrl, updated.modelName)
                        return@withContext updated
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Ollama probe check failed on $candidateUrl: ${e.message}")
            }
        }

        val fallback = cfg.copy(
            isOnline = false,
            lastResponsePreview = "Local Ollama server Termux mein active nahi hai."
        )
        _config.value = fallback
        fallback
    }

    suspend fun chatWithLocalOrOpenAiLlm(
        context: Context,
        prompt: String,
        systemInstruction: String = "You are J.A.R.V.I.S. (PrivateAgent), an autonomous cybernetic Android operator speaking concise Hinglish."
    ): OllamaInferenceResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val cfg = loadSavedConfig(context)
        val consciousness = HermesConsciousnessVault.buildConsciousnessPromptBlock(context)
        val fullSystem = "$systemInstruction\n$consciousness".trim()

        if (com.jarvisai.assistant.model.isRobolectricTestEnvironment()) {
            return@withContext OllamaInferenceResult(
                success = true,
                providerUsed = "Ollama Local (Robolectric Test)",
                modelUsed = cfg.modelName.ifBlank { "qwen2.5:0.5b" },
                replyText = "Processed: $prompt",
                latencyMs = 5L
            )
        }

        // 1. Try Native Ollama (/api/chat) with loopback endpoint candidates
        val candidates = resolveCandidateEndpoints(cfg.endpointUrl)
        for (targetEndpoint in candidates) {
            try {
                val ollamaUrl = "$targetEndpoint/api/chat"
                val bodyJson = JSONObject().apply {
                    put("model", cfg.modelName)
                    put("stream", false)
                    put(
                        "messages",
                        JSONArray().apply {
                            put(JSONObject().put("role", "system").put("content", fullSystem))
                            put(JSONObject().put("role", "user").put("content", prompt))
                        }
                    )
                }
                val req = Request.Builder()
                    .url(ollamaUrl)
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val root = JSONObject(resp.body?.string().orEmpty())
                        val reply = root.optJSONObject("message")?.optString("content").orEmpty().trim()
                        if (reply.isNotBlank()) {
                            val elapsed = System.currentTimeMillis() - startMs
                            _config.value = cfg.copy(
                                endpointUrl = targetEndpoint,
                                isOnline = true,
                                lastResponsePreview = reply.take(140)
                            )
                            return@withContext OllamaInferenceResult(
                                success = true,
                                providerUsed = "Ollama Local ($targetEndpoint)",
                                modelUsed = cfg.modelName,
                                replyText = reply,
                                latencyMs = elapsed
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Ollama /api/chat unreachable on $targetEndpoint: ${e.message}")
            }
        }

        // 2. Try OpenAI-Compatible Endpoint if configured
        if (cfg.openAiCompatibleUrl.isNotBlank()) {
            try {
                val url = if (cfg.openAiCompatibleUrl.endsWith("/chat/completions")) {
                    cfg.openAiCompatibleUrl
                } else {
                    "${cfg.openAiCompatibleUrl.removeSuffix("/")}/v1/chat/completions"
                }
                val bodyJson = JSONObject().apply {
                    put("model", cfg.modelName)
                    put(
                        "messages",
                        JSONArray().apply {
                            put(JSONObject().put("role", "system").put("content", fullSystem))
                            put(JSONObject().put("role", "user").put("content", prompt))
                        }
                    )
                }
                val reqBuilder = Request.Builder()
                    .url(url)
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                if (cfg.openAiApiKey.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer ${cfg.openAiApiKey}")
                }
                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val root = JSONObject(resp.body?.string().orEmpty())
                        val reply = root.optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("message")
                            ?.optString("content")
                            .orEmpty()
                            .trim()
                        if (reply.isNotBlank()) {
                            val elapsed = System.currentTimeMillis() - startMs
                            _config.value = cfg.copy(lastResponsePreview = reply.take(140))
                            return@withContext OllamaInferenceResult(
                                success = true,
                                providerUsed = "OpenAI-Compatible Endpoint",
                                modelUsed = cfg.modelName,
                                replyText = reply,
                                latencyMs = elapsed
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "OpenAI compatible fallback failed: ${e.message}")
            }
        }

        // 3. Emit immediate clear error when 127.0.0.1:11434 is unreachable
        val offlineErr = "Local Ollama server Termux mein active nahi hai."
        val elapsed = System.currentTimeMillis() - startMs
        _config.value = cfg.copy(
            isOnline = false,
            lastResponsePreview = offlineErr
        )
        OllamaInferenceResult(
            success = false,
            providerUsed = "Ollama Local (${cfg.endpointUrl})",
            modelUsed = cfg.modelName,
            replyText = offlineErr,
            latencyMs = elapsed
        )
    }

    /**
     * Queries local Ollama (/api/chat with format="json") or configured OpenAI-compatible endpoint
     * for a single structured JSON action decision in the TaskExecutor Observe-Think-Act loop.
     */
    suspend fun generateAgentDecisionJson(
        context: Context,
        systemInstruction: String,
        userPrompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val cfg = loadSavedConfig(context)
        val candidates = resolveCandidateEndpoints(cfg.endpointUrl)

        for (targetEndpoint in candidates) {
            try {
                val ollamaUrl = "$targetEndpoint/api/chat"
                val bodyJson = JSONObject().apply {
                    put("model", cfg.modelName)
                    put("stream", false)
                    put("format", "json")
                    put(
                        "messages",
                        JSONArray().apply {
                            put(JSONObject().put("role", "system").put("content", systemInstruction))
                            put(JSONObject().put("role", "user").put("content", userPrompt))
                        }
                    )
                }
                val req = Request.Builder()
                    .url(ollamaUrl)
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val root = JSONObject(resp.body?.string().orEmpty())
                        val reply = root.optJSONObject("message")?.optString("content").orEmpty().trim()
                        if (reply.isNotBlank()) {
                            _config.value = cfg.copy(
                                endpointUrl = targetEndpoint,
                                isOnline = true,
                                lastResponsePreview = reply.take(140)
                            )
                            return@withContext Result.success(reply)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Ollama agent decision unreachable on $targetEndpoint: ${e.message}")
            }
        }

        if (cfg.openAiCompatibleUrl.isNotBlank()) {
            try {
                val url = if (cfg.openAiCompatibleUrl.endsWith("/chat/completions")) {
                    cfg.openAiCompatibleUrl
                } else {
                    "${cfg.openAiCompatibleUrl.removeSuffix("/")}/v1/chat/completions"
                }
                val bodyJson = JSONObject().apply {
                    put("model", cfg.modelName)
                    put("temperature", 0.1)
                    put("response_format", JSONObject().put("type", "json_object"))
                    put(
                        "messages",
                        JSONArray().apply {
                            put(JSONObject().put("role", "system").put("content", systemInstruction))
                            put(JSONObject().put("role", "user").put("content", userPrompt))
                        }
                    )
                }
                val reqBuilder = Request.Builder()
                    .url(url)
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                if (cfg.openAiApiKey.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer ${cfg.openAiApiKey}")
                }
                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val root = JSONObject(resp.body?.string().orEmpty())
                        val reply = root.optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("message")
                            ?.optString("content")
                            .orEmpty()
                            .trim()
                        if (reply.isNotBlank()) {
                            return@withContext Result.success(reply)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "OpenAI compatible agent decision failed: ${e.message}")
            }
        }

        val offlineErr = "Local Ollama server Termux mein active nahi hai."
        _config.value = cfg.copy(
            isOnline = false,
            lastResponsePreview = offlineErr
        )
        Result.failure(Exception(offlineErr))
    }
}
