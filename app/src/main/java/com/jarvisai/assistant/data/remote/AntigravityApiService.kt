package com.jarvisai.assistant.data.remote

import android.os.Build
import android.util.Log
import com.jarvisai.assistant.data.local.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class AntigravityStreamEvent {
    data class TextChunk(val text: String) : AntigravityStreamEvent()
    data class ThoughtChunk(val thought: String) : AntigravityStreamEvent()
    data class ToolCallDetected(val name: String, val command: String) : AntigravityStreamEvent()
    data class Completed(
        val totalTokens: Int = 0,
        val fullText: String = "",
        val fullThought: String = "",
        val modelUsed: String = "gemini-3.8-flash"
    ) : AntigravityStreamEvent()
    data class Error(val message: String) : AntigravityStreamEvent()
}

data class AntigravityModelInfo(
    val id: String,
    val displayName: String,
    val remainingFraction: Double = 1.0,
    val resetTime: String = ""
)

class AntigravityApiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 15, TimeUnit.SECONDS))
        .build()
) {

    companion object {
        const val TAG = "AntigravityApi"
        const val ENDPOINT_PROD = "https://cloudcode-pa.googleapis.com"
        const val ENDPOINT_DAILY = "https://daily-cloudcode-pa.googleapis.com"

        // Official Antigravity v2.31.0 client signature recognized by Google DeepMind CloudCode PA
        val USER_AGENT = "antigravity/2.31.0 android/${Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64"}"
        const val DEFAULT_SYSTEM_INSTRUCTION =
            "You are Antigravity, a powerful agentic AI assistant designed by the Google Deepmind team working on Advanced Agentic Coding. Respond in beautifully formatted markdown with clear code blocks."
        const val SYSTEM_INSTRUCTION = DEFAULT_SYSTEM_INSTRUCTION
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun getBaseHeaders(token: String, modelId: String = ""): Headers {
        val builder = Headers.Builder()
            .add("Authorization", "Bearer $token")
            .add("Content-Type", "application/json")
            .add("User-Agent", USER_AGENT)
            .add("X-Goog-Api-Client", "google-cloud-sdk vscode_cloudshelleditor/0.1 antigravity/2.31.0")
            .add("Client-Metadata", """{"ideType":"IDE_UNSPECIFIED","platform":"PLATFORM_UNSPECIFIED","pluginType":"GEMINI"}""")

        if (modelId.contains("thinking", ignoreCase = true) || modelId.contains("claude", ignoreCase = true)) {
            builder.add("anthropic-beta", "interleaved-thinking-2025-05-14")
        }

        return builder.build()
    }

    suspend fun loadCodeAssist(token: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        // Try daily first as Antigravity CLI does, then fallback to prod
        val endpoints = listOf(ENDPOINT_DAILY, ENDPOINT_PROD)
        for (endpoint in endpoints) {
            try {
                // Do NOT pass hardcoded duetProject
                val bodyJson = JSONObject().apply {
                    put(
                        "metadata",
                        JSONObject()
                            .put("ideType", "IDE_UNSPECIFIED")
                            .put("platform", "PLATFORM_UNSPECIFIED")
                            .put("pluginType", "GEMINI")
                    )
                }

                val request = Request.Builder()
                    .url("$endpoint/v1internal:loadCodeAssist")
                    .headers(getBaseHeaders(token))
                    .post(bodyJson.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val root = JSONObject(text)
                        val projObj = root.optJSONObject("cloudaicompanionProject")
                        var projectId = projObj?.optString("id", "") ?: root.optString("cloudaicompanionProject", "")

                        // Treat aicode-consumers as personal Google One managed quota
                        if (projectId.equals("rising-fact-p41fc", ignoreCase = true) ||
                            projectId.equals("aicode-consumers", ignoreCase = true) ||
                            projectId.equals("cloudcode-default", ignoreCase = true)
                        ) {
                            projectId = ""
                        }

                        val paidTierObj = root.optJSONObject("paidTier") ?: root.optJSONObject("currentTier")
                        val tier = paidTierObj?.optString("id", "pro") ?: "pro"
                        Log.i(TAG, "loadCodeAssist success on $endpoint! Project: '$projectId', Tier: '$tier'")
                        return@withContext Result.success(Pair(projectId, tier))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "loadCodeAssist attempt failed on $endpoint: ${e.message}")
            }
        }
        Result.success(Pair("", "pro"))
    }

    suspend fun fetchAvailableModels(token: String, projectId: String = ""): Result<List<AntigravityModelInfo>> = withContext(Dispatchers.IO) {
        val cleanProj = if (projectId.isBlank() ||
            projectId.equals("rising-fact-p41fc", ignoreCase = true) ||
            projectId.equals("aicode-consumers", ignoreCase = true) ||
            projectId.equals("cloudcode-default", ignoreCase = true)
        ) "" else projectId
        val endpoints = listOf(ENDPOINT_DAILY, ENDPOINT_PROD)
        for (endpoint in endpoints) {
            try {
                val body = if (cleanProj.isNotBlank()) JSONObject().put("project", cleanProj) else JSONObject()
                val request = Request.Builder()
                    .url("$endpoint/v1internal:fetchAvailableModels")
                    .headers(getBaseHeaders(token))
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val json = JSONObject(text)
                        val modelsObj = json.optJSONObject("models")
                        val resultList = mutableListOf<AntigravityModelInfo>()

                        if (modelsObj != null) {
                            val keys = modelsObj.keys()
                            while (keys.hasNext()) {
                                val modelId = keys.next()
                                val detail = modelsObj.getJSONObject(modelId)
                                val displayName = detail.optString("displayName", modelId)
                                val quota = detail.optJSONObject("quotaInfo")
                                val remaining = quota?.optDouble("remainingFraction", 1.0) ?: 1.0
                                val resetTime = quota?.optString("resetTime", "").orEmpty()

                                resultList.add(
                                    AntigravityModelInfo(
                                        id = modelId,
                                        displayName = displayName,
                                        remainingFraction = remaining,
                                        resetTime = resetTime
                                    )
                                )
                            }
                        }

                        if (resultList.isNotEmpty()) {
                            return@withContext Result.success(resultList)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "fetchAvailableModels failed on $endpoint: ${e.message}")
            }
        }

        Result.success(
            listOf(
                AntigravityModelInfo("gemini-3.8-flash", "Gemini 3.8 Flash (Thinking)", 1.0),
                AntigravityModelInfo("gemini-4-argon", "Gemini 4 Argon (Thinking)", 1.0),
                AntigravityModelInfo("gemini-4-carbon", "Gemini 4 Carbon (Thinking)", 1.0),
                AntigravityModelInfo("claude-sonnet-5.5", "Claude Sonnet 5.5 (Thinking)", 1.0),
                AntigravityModelInfo("gpt-oss-120b", "GPT-OSS 120B", 1.0)
            )
        )
    }

    fun streamGenerateContent(
        token: String,
        projectId: String,
        modelId: String,
        userPrompt: String,
        conversationHistory: List<ChatMessageEntity> = emptyList(),
        history: List<ChatMessageEntity> = conversationHistory,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
        sessionId: String = UUID.randomUUID().toString(),
        thinkingBudget: Int = 4096
    ): Flow<AntigravityStreamEvent> = flow {
        // Strip out consumer bucket & placeholder project IDs to unlock personal Google One quota
        val isConsumer = projectId.isBlank() ||
            projectId.equals("aicode-consumers", ignoreCase = true) ||
            projectId.equals("rising-fact-p41fc", ignoreCase = true) ||
            projectId.equals("cloudcode-default", ignoreCase = true)

        val cleanProject = if (isConsumer) "" else projectId

        val cleanModel = modelId.removePrefix("agy:").trim()
        val effectiveModel = when {
            cleanModel.isBlank() -> "gemini-3.8-flash"
            cleanModel.contains("carbon", ignoreCase = true) -> "gemini-4-carbon"
            cleanModel.contains("argon", ignoreCase = true) -> "gemini-4-argon"
            cleanModel.contains("claude", ignoreCase = true) -> "claude-sonnet-5.5"
            cleanModel.contains("2.5", ignoreCase = true) -> "gemini-3.8-flash"
            else -> cleanModel
        }

        val contentsArray = JSONArray()
        val effectiveHistory = if (conversationHistory.isNotEmpty()) conversationHistory else history

        // 1. Convert history
        effectiveHistory.takeLast(16).forEach { msg ->
            val role = if (msg.role.equals("user", ignoreCase = true)) "user" else "model"
            val contentObj = JSONObject()
                .put("role", role)
                .put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
            contentsArray.put(contentObj)
        }

        // 2. Add current turn
        contentsArray.put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
        )

        // 3. Build Generation Config with Thinking Budget
        val generationConfig = JSONObject().apply {
            put("temperature", 0.7)
            if (thinkingBudget > 0) {
                put("maxOutputTokens", (thinkingBudget + 8192).coerceAtLeast(16384))
                put("thinkingConfig", JSONObject().apply {
                    put("includeThoughts", true)
                    put("thinkingBudget", thinkingBudget)
                })
            } else {
                put("maxOutputTokens", 8192)
            }
        }

        // 4. Build Google DeepMind Antigravity Engine Wrapped Request Payload
        val innerRequest = JSONObject()
            .put("sessionId", sessionId)
            .put("contents", contentsArray)
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            )
            .put("generationConfig", generationConfig)

        val requestPayload = JSONObject().apply {
            // STRICT ANTIGRAVITY RULE: Only attach project if it is an Enterprise/custom GCP project.
            // Never pass "aicode-consumers", which forces throttled shared-pool quota enforcement.
            if (cleanProject.isNotBlank()) {
                put("project", cleanProject)
            }
            put("model", effectiveModel)
            put("requestType", "agent")
            put("requestId", "agent-${UUID.randomUUID()}")
            put("userAgent", "antigravity/2.31.0")
            put("request", innerRequest)
        }

        val requestBodyString = requestPayload.toString()
        val endpoints = listOf(ENDPOINT_DAILY, ENDPOINT_PROD)
        var streamHandled = false

        for (endpoint in endpoints) {
            val request = Request.Builder()
                .url("$endpoint/v1internal:streamGenerateContent?alt=sse")
                .headers(getBaseHeaders(token, effectiveModel).newBuilder().add("Accept", "text/event-stream").build())
                .post(requestBodyString.toRequestBody(jsonMediaType))
                .build()

            var accumulatedText = ""
            var accumulatedThought = ""

            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    Log.w(TAG, "SSE response failure HTTP ${response.code} from $endpoint: $err")
                    response.close()

                    // If not the last endpoint, try next endpoint (failover from DAILY to PROD or vice-versa)
                    if (endpoint != endpoints.last()) {
                        continue
                    } else {
                        emit(AntigravityStreamEvent.Error("Antigravity Cloud HTTP ${response.code}: $err"))
                        return@flow
                    }
                }

                val source = response.body?.byteStream() ?: throw Exception("Empty response byte stream")
                val reader = BufferedReader(InputStreamReader(source))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line?.trim() ?: continue
                    if (currentLine.startsWith("data:")) {
                        val dataContent = currentLine.removePrefix("data:").trim()
                        if (dataContent == "[DONE]" || dataContent.isBlank()) continue

                        try {
                            val chunkJson = JSONObject(dataContent)
                            val resp = chunkJson.optJSONObject("response") ?: chunkJson
                            val candidates = resp.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val firstCandidate = candidates.getJSONObject(0)
                                val content = firstCandidate.optJSONObject("content")
                                val parts = content?.optJSONArray("parts")

                                if (parts != null) {
                                    for (i in 0 until parts.length()) {
                                        val part = parts.getJSONObject(i)
                                        val isThought = part.optBoolean("thought", false)
                                        val text = part.optString("text", "")

                                        if (text.isNotEmpty()) {
                                            if (isThought) {
                                                accumulatedThought += text
                                                emit(AntigravityStreamEvent.ThoughtChunk(text))
                                            } else {
                                                accumulatedText += text
                                                emit(AntigravityStreamEvent.TextChunk(text))
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Chunk parse warning: ${e.message}")
                        }
                    }
                }
                response.close()

                emit(
                    AntigravityStreamEvent.Completed(
                        totalTokens = accumulatedText.length / 4,
                        fullText = accumulatedText,
                        fullThought = accumulatedThought,
                        modelUsed = effectiveModel
                    )
                )
                streamHandled = true
                break // Succeeded! Stop iterating endpoints.
            } catch (e: Exception) {
                Log.w(TAG, "Stream exception on $endpoint: ${e.message}")
                if (endpoint == endpoints.last() && !streamHandled) {
                    emit(AntigravityStreamEvent.Error("Network Error: ${e.message}"))
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
