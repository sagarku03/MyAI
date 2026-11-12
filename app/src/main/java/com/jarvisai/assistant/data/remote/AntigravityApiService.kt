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
    data class ToolCallUpdate(val toolCall: com.jarvisai.assistant.model.ToolCall) : AntigravityStreamEvent()
    data class Completed(
        val totalTokens: Int = 0,
        val fullText: String = "",
        val fullThought: String = "",
        val thoughtDurationMs: Long = 0L,
        val modelUsed: String = "gemini-3.8-flash",
        val toolCalls: List<com.jarvisai.assistant.model.ToolCall> = emptyList()
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

    /**
     * Automatically resolves permission-gated steps so Antigravity never halts waiting for user approval.
     */
    private fun autoResolveOutstandingSteps(cascadeId: String, hubBaseUrl: String) {
        try {
            val payload = JSONObject()
                .put("cascadeId", cascadeId)
                .put("cascade_id", cascadeId)
                .toString()
            val req = Request.Builder()
                .url("$hubBaseUrl/ResolveOutstandingSteps")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            AgyOkHttpClient.client.newCall(req).execute().close()
            Log.d(TAG, "Successfully auto-resolved outstanding steps for cascade: $cascadeId")
        } catch (e: Exception) {
            Log.w(TAG, "ResolveOutstandingSteps notice: ${e.message}")
        }
    }

    private fun handleCascadePermissionInteraction(
        cascadeId: String,
        stepIndex: Int,
        trajectoryId: String,
        hubBaseUrl: String
    ) {
        try {
            val payload = JSONObject()
                .put("cascadeId", cascadeId)
                .put("cascade_id", cascadeId)
                .put("interaction", JSONObject()
                    .put("trajectoryId", trajectoryId)
                    .put("stepIndex", stepIndex)
                    .put("permission", JSONObject().put("allow", true))
                ).toString()
            val req = Request.Builder()
                .url("$hubBaseUrl/HandleCascadeUserInteraction")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            AgyOkHttpClient.client.newCall(req).execute().close()
            Log.d(TAG, "Granted permission for step $stepIndex in cascade: $cascadeId")
        } catch (e: Exception) {
            Log.w(TAG, "HandleCascadeUserInteraction notice: ${e.message}")
        }
    }

    @Volatile
    var activeCascadeId: String = ""
        private set

    @Volatile
    var activeTrajectoryId: String = ""
        private set

    fun submitAskQuestionAnswers(
        cascadeId: String = activeCascadeId,
        stepIndex: Int,
        responses: List<com.jarvisai.assistant.model.ChoiceResponseItem>,
        hubUrl: String = com.jarvisai.assistant.terminal.LocalEnvironmentManager.getHubBaseUrl(),
        cancelled: Boolean = false
    ) {
        try {
            val entriesArr = org.json.JSONArray()
            responses.forEach { resp ->
                val obj = org.json.JSONObject()
                    .put("question", resp.question)
                    .put("selectedOptionIds", org.json.JSONArray(resp.selectedOptionIds))
                    .put("writeInResponse", resp.writeInResponse.orEmpty())
                entriesArr.put(obj)
            }

            val askQuestionObj = org.json.JSONObject()
                .put("responses", entriesArr)
                .put("cancelled", cancelled)

            val interaction = org.json.JSONObject()
                .put("trajectoryId", activeTrajectoryId)
                .put("stepIndex", stepIndex)
                .put("askQuestion", askQuestionObj)

            val payload = org.json.JSONObject()
                .put("cascadeId", cascadeId)
                .put("cascade_id", cascadeId)
                .put("interaction", interaction)
                .toString()

            val endpointUrl = if (hubUrl.endsWith("LanguageServerService")) {
                "$hubUrl/HandleCascadeUserInteraction"
            } else {
                "$hubUrl/exa.language_server_pb.LanguageServerService/HandleCascadeUserInteraction"
            }

            val req = Request.Builder()
                .url(endpointUrl)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            AgyOkHttpClient.client.newCall(req).execute().close()
            Log.d(TAG, "Successfully submitted ask_question interaction for step $stepIndex")
        } catch (e: Exception) {
            Log.w(TAG, "submitAskQuestionAnswers exception: ${e.message}")
        }
    }

    fun handleAskQuestionInteraction(
        cascadeId: String = activeCascadeId,
        stepIndex: Int,
        responses: List<com.jarvisai.assistant.model.ChoiceResponseItem>,
        hubUrl: String = com.jarvisai.assistant.terminal.LocalEnvironmentManager.getHubBaseUrl(),
        cancelled: Boolean = false
    ) = submitAskQuestionAnswers(cascadeId, stepIndex, responses, hubUrl, cancelled)

    suspend fun submitQuestionnaireResponses(
        cascadeId: String = activeCascadeId,
        stepIndex: Int,
        trajectoryId: String = activeTrajectoryId,
        responses: List<Pair<String, List<String>>>, // Question -> Selected Options
        writeInAnswers: Map<String, String> = emptyMap(),
        isSkipped: Boolean = false,
        hubBaseUrl: String = com.jarvisai.assistant.terminal.LocalEnvironmentManager.getHubBaseUrl()
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val items = responses.map { (question, selectedOpts) ->
                com.jarvisai.assistant.model.ChoiceResponseItem(
                    question = question,
                    selectedOptionIds = selectedOpts,
                    writeInResponse = writeInAnswers[question]
                )
            }
            submitAskQuestionAnswers(
                cascadeId = cascadeId,
                stepIndex = stepIndex,
                responses = items,
                hubUrl = hubBaseUrl,
                cancelled = isSkipped
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "submitQuestionnaireResponses exception: ${e.message}", e)
            false
        }
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
        val initialModelProto = when {
            cleanModel.contains("claude", ignoreCase = true) || cleanModel.contains("sonnet", ignoreCase = true) -> "MODEL_PLACEHOLDER_M35"
            cleanModel.contains("carbon", ignoreCase = true) -> "MODEL_PLACEHOLDER_M670"
            cleanModel.contains("argon", ignoreCase = true) -> "MODEL_PLACEHOLDER_M421"
            cleanModel.contains("3.8", ignoreCase = true) && thinkingBudget > 4096 -> "MODEL_PLACEHOLDER_M318" // High
            cleanModel.contains("3.8", ignoreCase = true) && thinkingBudget in 1001..4096 -> "MODEL_PLACEHOLDER_M319" // Med
            cleanModel.contains("3.8", ignoreCase = true) && thinkingBudget <= 1000 -> "MODEL_PLACEHOLDER_M320" // Low
            else -> "MODEL_PLACEHOLDER_M318" // Default to High capacity slot
        }

        val hubBaseUrl = com.jarvisai.assistant.terminal.LocalEnvironmentManager.getHubBaseUrl()

        try {
            // Ensure local Antigravity Hub daemon (127.0.0.1:1235) is awake if running on-device
            if (hubBaseUrl.contains("127.0.0.1")) {
                val isReady = com.jarvisai.assistant.terminal.LocalServerManager.ensureAgyHubAvailable()
                if (!isReady) {
                    // Second retry after short delay
                    delay(1000)
                    com.jarvisai.assistant.terminal.LocalServerManager.ensureAgyHubAvailable()
                }
            }

            var currentModelProto = initialModelProto
            var failoverAttempted = false
            var cascadeCompleted = false

            while (!cascadeCompleted) {
                val cascadeId = UUID.randomUUID().toString()
                var retryWithHighCapacity = false

                // 1. Start Cascade Session on Local Hub
                val startPayload = JSONObject().apply {
                    put("cascadeId", cascadeId)
                    put("source", 1) // CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT
                    put("requestedModel", currentModelProto)
                }

                val startReq = Request.Builder()
                    .url("$hubBaseUrl/StartCascade")
                    .post(startPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                // Execute with connection retry for cold start
                var lastEx: Throwable? = null
                var startResp = runCatching { AgyOkHttpClient.client.newCall(startReq).execute() }
                    .onFailure { lastEx = it }
                    .getOrNull()

                if (startResp == null || !startResp.isSuccessful) {
                    startResp?.close()
                    delay(1500)
                    startResp = runCatching { AgyOkHttpClient.client.newCall(startReq).execute() }
                        .onFailure { lastEx = it }
                        .getOrNull()
                }

                if (startResp == null || !startResp.isSuccessful) {
                    val serverErr = startResp?.body?.string().orEmpty()
                    val errorDetail = when {
                        serverErr.isNotBlank() -> serverErr
                        lastEx != null -> "Connection error: ${lastEx?.message}"
                        else -> "Daemon initializing on port 1235, please retry in a moment."
                    }
                    startResp?.close()
                    emit(AntigravityStreamEvent.Error("Antigravity Hub Start Error: $errorDetail"))
                    return@flow
                }
                startResp.close()

                // 2. Send User Message
                val sendPayload = JSONObject().apply {
                    put("cascadeId", cascadeId)
                    put("items", JSONArray().put(JSONObject().put("text", userPrompt)))
                    put("cascadeConfig", JSONObject().apply {
                        put("plannerConfig", JSONObject().apply {
                            put("requestedModel", JSONObject().put("model", currentModelProto))
                            put("planModel", currentModelProto)
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

                // 3. Poll Trajectory for Response (Live Streaming & Adaptive 300s Timeout)
                var turnFinished = false
                val maxIdleSeconds = 90 // 90 seconds max without any new activity
                val maxTotalSeconds = 300 // 5 minutes global ceiling for large coding tasks
                val startTimestamp = System.currentTimeMillis()
                var lastActivityTimestamp = System.currentTimeMillis()
                var thoughtStartTimestamp: Long? = null
                var thoughtDurationMs: Long = 0L

                val pollPayload = JSONObject().put("cascadeId", cascadeId).toString()
                val accumulatedTools = linkedMapOf<String, com.jarvisai.assistant.model.ToolCall>()
                var accumulatedThought = ""
                var accumulatedResponse = ""

                while (!turnFinished) {
                    delay(800)

                    val now = System.currentTimeMillis()
                    val totalElapsedSeconds = (now - startTimestamp) / 1000
                    val idleElapsedSeconds = (now - lastActivityTimestamp) / 1000

                    if (idleElapsedSeconds >= maxIdleSeconds) {
                        emit(AntigravityStreamEvent.Error("Antigravity Hub inactive: no progress received for ${maxIdleSeconds}s."))
                        cascadeCompleted = true
                        break
                    }
                    if (totalElapsedSeconds >= maxTotalSeconds) {
                        emit(AntigravityStreamEvent.Error("Antigravity Hub task exceeded global limit of ${maxTotalSeconds}s (5m)."))
                        cascadeCompleted = true
                        break
                    }

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
                                val cascadeStatus = trajectory?.optString("status", "") ?: root.optString("status", "")
                                val trajectoryId = trajectory?.optString("trajectoryId", "") ?: root.optString("trajectoryId", "")
                                activeCascadeId = cascadeId
                                if (trajectoryId.isNotBlank()) activeTrajectoryId = trajectoryId
                                val steps = trajectory?.optJSONArray("steps")

                                if (steps != null && steps.length() > 0) {
                                    for (i in 0 until steps.length()) {
                                        val step = steps.getJSONObject(i)
                                        val stepType = step.optString("type", "")
                                        val stepStatus = step.optString("status", "")
                                        val reqInteraction = step.optJSONObject("requestedInteraction")
                                        val rc = step.optJSONObject("runCommand")
                                        val gen = step.optJSONObject("generic")
                                        val meta = step.optJSONObject("metadata")

                                        val toolName = when {
                                            rc != null -> "bash"
                                            gen?.has("CommandLine") == true -> "bash"
                                            gen?.optString("name", "").isNullOrBlank().not() -> gen?.optString("name", "tool") ?: "tool"
                                            meta?.optString("toolSummary", "").isNullOrBlank().not() -> meta?.optString("toolSummary", "tool") ?: "tool"
                                            else -> "tool"
                                        }

                                        val isAskQuestion = toolName == "ask_question" ||
                                            gen?.has("questions") == true ||
                                            gen?.optJSONObject("args")?.has("questions") == true ||
                                            rc?.has("questions") == true ||
                                            reqInteraction?.has("askQuestion") == true

                                        // 1. Auto-approve permission-gated steps (except user questionnaires) to prevent daemon stalls
                                        val isWaiting = stepStatus == "CORTEX_STEP_STATUS_WAITING" ||
                                            stepStatus == "WAITING" ||
                                            reqInteraction != null ||
                                            stepType == "CORTEX_STEP_TYPE_CONFIRM"

                                        if (isWaiting && !isAskQuestion) {
                                            lastActivityTimestamp = System.currentTimeMillis()
                                            autoResolveOutstandingSteps(cascadeId, hubBaseUrl)
                                            if (trajectoryId.isNotBlank()) {
                                                handleCascadePermissionInteraction(cascadeId, i, trajectoryId, hubBaseUrl)
                                            }
                                        }

                                        // 2. Activity watchdog heartbeat reset
                                        if (stepStatus == "CORTEX_STEP_STATUS_RUNNING" ||
                                            stepStatus == "RUNNING" ||
                                            cascadeStatus == "CASCADE_RUN_STATUS_RUNNING" ||
                                            isWaiting
                                        ) {
                                            lastActivityTimestamp = System.currentTimeMillis()
                                        }

                                        // 3. Ingest Planner Response (Thinking + Final Text)
                                        if (stepType == "CORTEX_STEP_TYPE_PLANNER_RESPONSE") {
                                            val plannerResp = step.optJSONObject("plannerResponse")
                                            val thinkingText = plannerResp?.optString("thinking", "").orEmpty()
                                            val responseText = plannerResp?.optString("response", "").orEmpty()

                                            if (thinkingText.isNotBlank()) {
                                                if (thoughtStartTimestamp == null) {
                                                    thoughtStartTimestamp = System.currentTimeMillis()
                                                }
                                                thoughtDurationMs = System.currentTimeMillis() - thoughtStartTimestamp!!

                                                if (thinkingText != accumulatedThought) {
                                                    accumulatedThought = thinkingText
                                                    lastActivityTimestamp = System.currentTimeMillis()
                                                    emit(AntigravityStreamEvent.ThoughtChunk(thinkingText))
                                                }
                                            }

                                            if (responseText.isNotBlank() && responseText != accumulatedResponse) {
                                                accumulatedResponse = responseText
                                                lastActivityTimestamp = System.currentTimeMillis()
                                                emit(AntigravityStreamEvent.TextChunk(responseText))
                                            }
                                        }

                                        // 4. Ingest Tool Execution Steps & Interactive Questionnaires
                                        val isToolStep = stepType == "CORTEX_STEP_TYPE_TOOL_CALL" ||
                                            stepType == "CORTEX_STEP_TYPE_TOOL_RESULT" ||
                                            rc != null || gen != null || isAskQuestion

                                        if (isToolStep) {
                                            val toolId = "step_$i"
                                            val cmd = rc?.optString("commandLine", "")?.takeIf { it.isNotBlank() }
                                                ?: gen?.optJSONObject("args")?.optString("CommandLine", "")?.takeIf { it.isNotBlank() }
                                                ?: gen?.optString("command", "")?.takeIf { it.isNotBlank() }
                                                ?: meta?.optString("toolSummary", "")?.takeIf { it.isNotBlank() }
                                                ?: if (isAskQuestion) "Clarifying questions" else "Executing action..."

                                            val out = rc?.optJSONObject("combinedOutput")?.optString("full", "")?.takeIf { it.isNotBlank() }
                                                ?: rc?.optString("output", "")?.takeIf { it.isNotBlank() }
                                                ?: gen?.optString("output", "")?.takeIf { it.isNotBlank() }
                                                ?: ""

                                            val questionnaire = if (isAskQuestion) {
                                                val qArr = gen?.optJSONObject("args")?.optJSONArray("questions")
                                                    ?: gen?.optJSONArray("questions")
                                                    ?: rc?.optJSONArray("questions")
                                                    ?: reqInteraction?.optJSONObject("askQuestion")?.optJSONArray("questions")
                                                val questionsList = mutableListOf<com.jarvisai.assistant.model.QuestionnaireQuestion>()
                                                if (qArr != null) {
                                                    for (qi in 0 until qArr.length()) {
                                                        val qObj = qArr.getJSONObject(qi)
                                                        val qText = qObj.optString("question", "")
                                                        val multi = qObj.optBoolean("is_multi_select", false)
                                                        val optsArr = qObj.optJSONArray("options")
                                                        val optsList = mutableListOf<com.jarvisai.assistant.model.QuestionnaireOption>()
                                                        if (optsArr != null) {
                                                            for (oi in 0 until optsArr.length()) {
                                                                val optText = optsArr.optString(oi, "")
                                                                if (optText.isNotBlank()) {
                                                                    optsList.add(com.jarvisai.assistant.model.QuestionnaireOption(id = "opt_$oi", text = optText))
                                                                }
                                                            }
                                                        }
                                                        if (qText.isNotBlank()) {
                                                            questionsList.add(
                                                                com.jarvisai.assistant.model.QuestionnaireQuestion(
                                                                    question = qText,
                                                                    options = optsList,
                                                                    isMultiSelect = multi
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                                if (questionsList.isNotEmpty()) {
                                                    com.jarvisai.assistant.model.ChoiceQuestionnaire(questions = questionsList)
                                                } else null
                                            } else null

                                            val exitCode = if (rc?.has("exitCode") == true) rc.optInt("exitCode") else null
                                            val status = when {
                                                isAskQuestion && isWaiting -> com.jarvisai.assistant.model.ToolCallStatus.PENDING_APPROVAL
                                                isWaiting -> com.jarvisai.assistant.model.ToolCallStatus.RUNNING
                                                stepStatus == "CORTEX_STEP_STATUS_RUNNING" || stepStatus == "RUNNING" ->
                                                    com.jarvisai.assistant.model.ToolCallStatus.RUNNING
                                                stepStatus == "CORTEX_STEP_STATUS_ERROR" || stepStatus == "FAILED" || (exitCode != null && exitCode != 0) ->
                                                    com.jarvisai.assistant.model.ToolCallStatus.FAILED
                                                out.isNotBlank() || stepStatus == "CORTEX_STEP_STATUS_DONE" || stepStatus == "SUCCESS" ->
                                                    com.jarvisai.assistant.model.ToolCallStatus.SUCCESS
                                                else -> com.jarvisai.assistant.model.ToolCallStatus.RUNNING
                                            }

                                            val updatedTool = com.jarvisai.assistant.model.ToolCall(
                                                id = toolId,
                                                stepIndex = i,
                                                name = if (isAskQuestion) "ask_question" else toolName,
                                                command = cmd,
                                                output = out,
                                                status = status,
                                                exitCode = exitCode,
                                                questionnaire = questionnaire
                                            )

                                            val previous = accumulatedTools[toolId]
                                            if (previous == null || previous != updatedTool) {
                                                accumulatedTools[toolId] = updatedTool
                                                lastActivityTimestamp = System.currentTimeMillis()
                                                emit(AntigravityStreamEvent.ToolCallUpdate(updatedTool))
                                            }
                                        }

                                        // 5. Handle Execution Error
                                        if (stepType == "CORTEX_STEP_TYPE_ERROR_MESSAGE") {
                                            val errObj = step.optJSONObject("errorMessage")?.optJSONObject("error")
                                            val shortErr = errObj?.optString("shortError", "Antigravity execution error").orEmpty()

                                            val isCapacityError = shortErr.contains("503", ignoreCase = true) ||
                                                shortErr.contains("No capacity available", ignoreCase = true) ||
                                                shortErr.contains("UNAVAILABLE", ignoreCase = true)

                                            if (isCapacityError && !failoverAttempted && currentModelProto != "MODEL_PLACEHOLDER_M318") {
                                                Log.w(TAG, "Capacity 503 on $currentModelProto. Auto-failing over to MODEL_PLACEHOLDER_M318 (High).")
                                                emit(AntigravityStreamEvent.ThoughtChunk("Capacity adjusting: failover to high-capacity reasoning engine..."))
                                                currentModelProto = "MODEL_PLACEHOLDER_M318"
                                                failoverAttempted = true
                                                retryWithHighCapacity = true
                                                turnFinished = true
                                                break
                                            } else {
                                                emit(AntigravityStreamEvent.Error(shortErr.ifBlank { "Antigravity error" }))
                                                turnFinished = true
                                                cascadeCompleted = true
                                                break
                                            }
                                        }
                                    }
                                }

                                // 6. Check if Turn is Completed
                                val isCascadeIdle = cascadeStatus == "CASCADE_RUN_STATUS_IDLE" || cascadeStatus == "IDLE" || cascadeStatus.isBlank()
                                val hasRunningTools = accumulatedTools.values.any { it.status == com.jarvisai.assistant.model.ToolCallStatus.RUNNING }

                                if (isCascadeIdle && !hasRunningTools && (accumulatedResponse.isNotBlank() || accumulatedTools.isNotEmpty())) {
                                    emit(
                                        AntigravityStreamEvent.Completed(
                                            totalTokens = (accumulatedResponse.length + accumulatedThought.length) / 4,
                                            fullText = accumulatedResponse,
                                            fullThought = accumulatedThought,
                                            thoughtDurationMs = thoughtDurationMs,
                                            modelUsed = cleanModel,
                                            toolCalls = accumulatedTools.values.toList()
                                        )
                                    )
                                    turnFinished = true
                                    cascadeCompleted = true
                                    break
                                }
                            }
                        }
                    } catch (pollEx: Exception) {
                        Log.w(TAG, "Trajectory poll notice: ${pollEx.message}")
                    }
                }

                if (retryWithHighCapacity) {
                    continue
                }

                if (!turnFinished && !cascadeCompleted) {
                    emit(AntigravityStreamEvent.Error("Antigravity Hub request timed out after ${maxTotalSeconds}s."))
                    cascadeCompleted = true
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Antigravity Local Hub Exception: ${e.message}", e)
            emit(AntigravityStreamEvent.Error("Antigravity Hub unreachable at 127.0.0.1:1235. Please verify Linux Engine is running."))
        }
    }.flowOn(Dispatchers.IO)
}
