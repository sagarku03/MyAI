package com.jarvisai.assistant.data.remote

import android.os.Build
import android.util.Log
import com.jarvisai.assistant.agent.core.AgyOkHttpClient
import com.jarvisai.assistant.data.local.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        val endpoints = listOf(ENDPOINT_DAILY, ENDPOINT_PROD)
        for (endpoint in endpoints) {
            try {
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

        val cleanModel = modelId.removePrefix("agy:").trim()
        val mappedModelProto = when {
            cleanModel.contains("claude", ignoreCase = true) || cleanModel.contains("sonnet", ignoreCase = true) -> "MODEL_PLACEHOLDER_M35"
            cleanModel.contains("carbon", ignoreCase = true) -> "MODEL_PLACEHOLDER_M670"
            cleanModel.contains("argon", ignoreCase = true) -> "MODEL_PLACEHOLDER_M421"
            cleanModel.contains("3.8", ignoreCase = true) && thinkingBudget > 4096 -> "MODEL_PLACEHOLDER_M318"
            cleanModel.contains("3.8", ignoreCase = true) -> "MODEL_PLACEHOLDER_M319"
            else -> "MODEL_PLACEHOLDER_M18" // Gemini 3 Flash default
        }

        val cascadeId = UUID.randomUUID().toString()
        val hubBaseUrl = "http://127.0.0.1:1235/exa.language_server_pb.LanguageServerService"

        try {
            // 1. Start Cascade Session on Local Hub
            val startPayload = JSONObject().apply {
                put("cascadeId", cascadeId)
                put("source", 1) // CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT
                put("requestedModel", mappedModelProto)
            }

            val startReq = Request.Builder()
                .url("$hubBaseUrl/StartCascade")
                .post(startPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val startResp = AgyOkHttpClient.client.newCall(startReq).execute()
            if (!startResp.isSuccessful) {
                val err = startResp.body?.string().orEmpty()
                startResp.close()
                emit(AntigravityStreamEvent.Error("Antigravity Hub Start Error (HTTP ${startResp.code}): $err"))
                return@flow
            }
            startResp.close()

            // 2. Send User Message
            val sendPayload = JSONObject().apply {
                put("cascadeId", cascadeId)
                put("items", JSONArray().put(JSONObject().put("text", userPrompt)))
                put("cascadeConfig", JSONObject().apply {
                    put("plannerConfig", JSONObject().apply {
                        put("requestedModel", JSONObject().put("model", mappedModelProto))
                        put("planModel", mappedModelProto)
                    })
                })
            }

            val sendReq = Request.Builder()
                .url("$hubBaseUrl/SendUserCascadeMessage")
                .post(sendPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val sendResp = AgyOkHttpClient.client.newCall(sendReq).execute()
            if (!sendResp.isSuccessful) {
                val err = sendResp.body?.string().orEmpty()
                sendResp.close()
                emit(AntigravityStreamEvent.Error("Antigravity Hub Send Error (HTTP ${sendResp.code}): $err"))
                return@flow
            }
            sendResp.close()

            // 3. Poll Trajectory for Response (Live Streaming)
            var turnFinished = false
            var pollAttempts = 0
            val maxAttempts = 45 // 45 seconds timeout
            val pollPayload = JSONObject().put("cascadeId", cascadeId).toString()

            while (!turnFinished && pollAttempts < maxAttempts) {
                delay(1000)
                pollAttempts++

                val pollReq = Request.Builder()
                    .url("$hubBaseUrl/GetCascadeTrajectory")
                    .post(pollPayload.toRequestBody("application/json".toMediaType()))
                    .build()

                try {
                    AgyOkHttpClient.client.newCall(pollReq).execute().use { pollResp ->
                        if (pollResp.isSuccessful) {
                            val respStr = pollResp.body?.string().orEmpty()
                            val root = JSONObject(respStr)
                            val trajectory = root.optJSONObject("trajectory")
                            val steps = trajectory?.optJSONArray("steps")

                            if (steps != null && steps.length() > 0) {
                                for (i in 0 until steps.length()) {
                                    val step = steps.getJSONObject(i)
                                    val stepType = step.optString("type", "")

                                    if (stepType == "CORTEX_STEP_TYPE_PLANNER_RESPONSE") {
                                        val plannerResp = step.optJSONObject("plannerResponse")
                                        val thinkingText = plannerResp?.optString("thinking", "").orEmpty()
                                        val responseText = plannerResp?.optString("response", "").orEmpty()

                                        if (thinkingText.isNotBlank()) {
                                            emit(AntigravityStreamEvent.ThoughtChunk(thinkingText))
                                        }
                                        if (responseText.isNotBlank()) {
                                            emit(AntigravityStreamEvent.TextChunk(responseText))
                                            emit(
                                                AntigravityStreamEvent.Completed(
                                                    totalTokens = responseText.length / 4,
                                                    fullText = responseText,
                                                    fullThought = thinkingText,
                                                    modelUsed = cleanModel
                                                )
                                            )
                                            turnFinished = true
                                            break
                                        }
                                    } else if (stepType == "CORTEX_STEP_TYPE_ERROR_MESSAGE") {
                                        val errObj = step.optJSONObject("errorMessage")?.optJSONObject("error")
                                        val shortErr = errObj?.optString("shortError", "Antigravity execution error")
                                        emit(AntigravityStreamEvent.Error(shortErr ?: "Antigravity error"))
                                        turnFinished = true
                                        break
                                    }
                                }
                            }
                        }
                    }
                } catch (pollEx: Exception) {
                    Log.w(TAG, "Trajectory poll attempt $pollAttempts warning: ${pollEx.message}")
                }
            }

            if (!turnFinished) {
                emit(AntigravityStreamEvent.Error("Antigravity Hub response timed out after ${maxAttempts}s."))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Antigravity Local Hub Exception: ${e.message}", e)
            emit(AntigravityStreamEvent.Error("Antigravity Hub unreachable at 127.0.0.1:1235. Please verify Linux Engine is running."))
        }
    }.flowOn(Dispatchers.IO)
}
