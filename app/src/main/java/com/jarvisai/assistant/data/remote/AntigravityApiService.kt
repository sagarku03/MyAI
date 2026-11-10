package com.jarvisai.assistant.data.remote

import android.os.Build
import android.util.Log
import com.jarvisai.assistant.data.local.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

sealed class AntigravityStreamEvent {
    data class TextChunk(val text: String) : AntigravityStreamEvent()
    data class ThoughtChunk(val thought: String) : AntigravityStreamEvent()
    data class ToolCallDetected(val name: String, val command: String) : AntigravityStreamEvent()
    data class Completed(val fullText: String, val fullThought: String, val modelUsed: String) : AntigravityStreamEvent()
    data class Error(val message: String) : AntigravityStreamEvent()
}

data class AntigravityModelInfo(
    val id: String,
    val displayName: String,
    val remainingFraction: Double = 1.0,
    val resetTime: String = ""
)

/**
 * Direct Client for Google CloudCode PA Internal Antigravity APIs.
 * Connects directly to Google's DeepMind infrastructure for unmetered Gemini 2.5 Flash Thinking,
 * Claude 3.5 Sonnet, and Gemini Pro via Google Account Authorization.
 */
class AntigravityApiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {

    companion object {
        private const val TAG = "AntigravityApi"
        const val ENDPOINT_PROD = "https://cloudcode-pa.googleapis.com"
        const val ENDPOINT_DAILY = "https://daily-cloudcode-pa.googleapis.com"

        val USER_AGENT = "antigravity/1.11.5 android/${Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64"}"
        const val SYSTEM_INSTRUCTION = "You are JARVIS AI Ultra, an elite autonomous developer and assistant running on Android. Respond in beautifully formatted GitHub-flavored markdown with clean code blocks."
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun getBaseHeaders(token: String, modelId: String = ""): Headers {
        val builder = Headers.Builder()
            .add("Authorization", "Bearer $token")
            .add("Content-Type", "application/json")
            .add("User-Agent", USER_AGENT)
            .add("X-Goog-Api-Client", "google-cloud-sdk vscode_cloudshelleditor/0.1")
            .add("Client-Metadata", """{"ideType":"IDE_UNSPECIFIED","platform":"PLATFORM_UNSPECIFIED","pluginType":"GEMINI"}""")

        if (modelId.contains("thinking", ignoreCase = true) || modelId.contains("claude", ignoreCase = true)) {
            builder.add("anthropic-beta", "interleaved-thinking-2025-05-14")
        }

        return builder.build()
    }

    suspend fun loadCodeAssist(token: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val endpoints = listOf(ENDPOINT_PROD, ENDPOINT_DAILY)
        for (endpoint in endpoints) {
            try {
                val body = """{"metadata":{"ideType":"IDE_UNSPECIFIED","platform":"PLATFORM_UNSPECIFIED","pluginType":"GEMINI","duetProject":"rising-fact-p41fc"}}"""
                    .toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url("$endpoint/v1internal:loadCodeAssist")
                    .headers(getBaseHeaders(token))
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val json = JSONObject(text)
                        val projectObj = json.optJSONObject("cloudaicompanionProject")
                        val projectId = projectObj?.optString("id") ?: json.optString("cloudaicompanionProject", "rising-fact-p41fc")
                        val paidTier = json.optJSONObject("paidTier")?.optString("id")
                        val currentTier = json.optJSONObject("currentTier")?.optString("id")
                        val tier = paidTier ?: currentTier ?: "pro"
                        Log.i(TAG, "loadCodeAssist SUCCESS! Project: $projectId, Tier: $tier")
                        return@withContext Result.success(Pair(projectId, tier))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "loadCodeAssist attempt failed on $endpoint: ${e.message}")
            }
        }
        Result.success(Pair("rising-fact-p41fc", "pro"))
    }

    suspend fun fetchAvailableModels(token: String, projectId: String = "rising-fact-p41fc"): Result<List<AntigravityModelInfo>> = withContext(Dispatchers.IO) {
        val endpoints = listOf(ENDPOINT_PROD, ENDPOINT_DAILY)
        for (endpoint in endpoints) {
            try {
                val body = JSONObject().put("project", projectId).toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("$endpoint/v1internal:fetchAvailableModels")
                    .headers(getBaseHeaders(token))
                    .post(body)
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
                            Log.i(TAG, "Discovered ${resultList.size} Antigravity models!")
                            return@withContext Result.success(resultList)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "fetchAvailableModels failed on $endpoint: ${e.message}")
            }
        }

        // Standard Fallback Models if listing fails
        Result.success(
            listOf(
                AntigravityModelInfo("gemini-2.5-flash-thinking", "Gemini 2.5 Flash Thinking (Antigravity)", 1.0),
                AntigravityModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash (Antigravity)", 1.0),
                AntigravityModelInfo("claude-3-5-sonnet", "Claude 3.5 Sonnet (Antigravity)", 1.0)
            )
        )
    }

    fun streamGenerateContent(
        token: String,
        projectId: String,
        modelId: String,
        history: List<ChatMessageEntity>,
        userPrompt: String,
        systemInstruction: String = SYSTEM_INSTRUCTION
    ): Flow<AntigravityStreamEvent> = flow {
        val effectiveModel = when {
            modelId.isBlank() -> "gemini-2.5-flash-thinking"
            modelId.contains("Claude", ignoreCase = true) -> "claude-3-5-sonnet"
            modelId.contains("Thinking", ignoreCase = true) -> "gemini-2.5-flash-thinking"
            else -> modelId
        }
        val endpoint = ENDPOINT_PROD

        val contentsArray = JSONArray()

        // 1. Add historical conversation
        history.takeLast(16).forEach { msg ->
            val role = if (msg.role.equals("user", ignoreCase = true)) "user" else "model"
            val part = JSONObject().put("text", msg.content)
            val contentObj = JSONObject()
                .put("role", role)
                .put("parts", JSONArray().put(part))
            contentsArray.put(contentObj)
        }

        // 2. Add current turn
        val currentPart = JSONObject().put("text", userPrompt)
        contentsArray.put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(currentPart))
        )

        // 3. Build Request payload
        val requestJson = JSONObject()
            .put("project", projectId)
            .put("model", effectiveModel)
            .put("contents", contentsArray)
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.7)
                    .put("maxOutputTokens", 8192)
            )

        val request = Request.Builder()
            .url("$endpoint/v1internal:streamGenerateCode?alt=sse")
            .headers(getBaseHeaders(token, effectiveModel))
            .post(requestJson.toString().toRequestBody(jsonMediaType))
            .build()

        var accumulatedText = ""
        var accumulatedThought = ""

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    emit(AntigravityStreamEvent.Error("Antigravity Cloud HTTP ${response.code}: $err"))
                    return@flow
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
                            val candidates = chunkJson.optJSONArray("candidates") ?: continue
                            if (candidates.length() > 0) {
                                val firstCandidate = candidates.getJSONObject(0)
                                val content = firstCandidate.optJSONObject("content")
                                val parts = content?.optJSONArray("parts")

                                if (parts != null) {
                                    for (i in 0 until parts.length()) {
                                        val p = parts.getJSONObject(i)
                                        if (p.has("thought")) {
                                            val thoughtText = p.optString("thought")
                                            accumulatedThought += thoughtText
                                            emit(AntigravityStreamEvent.ThoughtChunk(thoughtText))
                                        }
                                        if (p.has("text")) {
                                            val textContent = p.optString("text")
                                            accumulatedText += textContent
                                            emit(AntigravityStreamEvent.TextChunk(textContent))
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                emit(AntigravityStreamEvent.Completed(accumulatedText, accumulatedThought, effectiveModel))
            }
        } catch (e: Exception) {
            emit(AntigravityStreamEvent.Error("Antigravity streaming exception: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
}
