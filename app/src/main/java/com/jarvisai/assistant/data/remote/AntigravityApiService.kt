package com.jarvisai.assistant.data.remote

import android.content.Context
import android.os.Build
import android.util.Log
import com.jarvisai.assistant.agent.core.AgyOkHttpClient
import com.jarvisai.assistant.data.local.ChatMessageEntity
import com.jarvisai.assistant.ide.ActiveCodeEditorRegistry
import com.jarvisai.assistant.model.ResolvedAttachmentPart
import com.jarvisai.assistant.model.ToolCall
import com.jarvisai.assistant.model.ToolCallStatus
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.LocalServerManager
import com.jarvisai.assistant.terminal.WorkspaceSyncManager
import com.jarvisai.assistant.termux.JarvisTermuxBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
        val toolCalls: List<com.jarvisai.assistant.model.ToolCall> = emptyList(),
        val promptTokens: Int = 0,
        val outputTokens: Int = 0,
        val cachedTokens: Int = 0
    ) : AntigravityStreamEvent()
    data class Error(val message: String) : AntigravityStreamEvent()
}

data class AntigravityModelInfo(
    val id: String,
    val displayName: String,
    val remainingFraction: Double = 1.0,
    val resetTime: String = "",
    val supportsThinking: Boolean = true,
    val isRecommended: Boolean = false,
    val maxTokens: Int = 0,
    val tagTitle: String = "",
    val supportsImages: Boolean = true,
    val thinkingVariantIds: Map<String, String> = emptyMap(),
    val supportedThinkingLevels: List<String> = emptyList()
) {
    val quotaPercent: Int
        get() = (remainingFraction.coerceIn(0.0, 1.0) * 100.0).toInt()
}

class AntigravityApiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 15, TimeUnit.SECONDS))
        .build()
) {

    private val http1Client: OkHttpClient by lazy {
        client.newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionPool(ConnectionPool(2, 10, TimeUnit.SECONDS))
            .build()
    }

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

    private val sessionCascadeIds = ConcurrentHashMap<String, String>()
    private val startedCascades = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    var activeCascadeId: String = ""
        private set

    @Volatile
    var activeTrajectoryId: String = ""
        private set

    fun resetCascadeSession(sessionId: String) {
        val removed = sessionCascadeIds.remove(sessionId)
        if (removed != null) {
            startedCascades.remove(removed)
        }
        if (removed == activeCascadeId) {
            activeCascadeId = ""
            activeTrajectoryId = ""
        }
    }

    data class SubmittedAskQuestionRecord(
        val readableSummary: String,
        val cancelled: Boolean,
        val submittedAtMs: Long,
        val interactionAccepted: Boolean,
        val responses: List<com.jarvisai.assistant.model.ChoiceResponseItem> = emptyList()
    )

    private object AskQuestionSharedState {
        @Volatile
        var lastCascadeId: String = ""
        @Volatile
        var lastTrajectoryId: String = ""
        @Volatile
        var lastInteractionId: String = ""
        @Volatile
        var lastInteractionKey: String = "askQuestion"
        val submittedRecords = ConcurrentHashMap<String, SubmittedAskQuestionRecord>()
    }

    fun cancelActiveCascade(hubUrl: String = LocalEnvironmentManager.getHubBaseUrl()) {
        AntigravityToolSuite.cancelAllPendingQuestions()
        val cid = activeCascadeId
        if (cid.isBlank()) return
        try {
            val endpoint = if (hubUrl.endsWith("LanguageServerService")) {
                "$hubUrl/CancelCascadeInvocation"
            } else {
                "$hubUrl/exa.language_server_pb.LanguageServerService/CancelCascadeInvocation"
            }
            val payload = JSONObject()
                .put("cascadeId", cid)
                .put("cascade_id", cid)
                .toString()
            val req = Request.Builder()
                .url(endpoint)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            AgyOkHttpClient.client.newCall(req).execute().close()
            Log.i(TAG, "Sent CancelCascadeInvocation for cascade: $cid")
        } catch (e: Exception) {
            Log.w(TAG, "CancelCascadeInvocation notice: ${e.message}")
        }
    }

    fun resolveCascadePermission(
        cascadeId: String = activeCascadeId,
        stepIndex: Int,
        allow: Boolean,
        trajectoryId: String = activeTrajectoryId,
        hubUrl: String = LocalEnvironmentManager.getHubBaseUrl()
    ) {
        if (cascadeId.isBlank()) return
        try {
            val endpoint = if (hubUrl.endsWith("LanguageServerService")) {
                "$hubUrl/HandleCascadeUserInteraction"
            } else {
                "$hubUrl/exa.language_server_pb.LanguageServerService/HandleCascadeUserInteraction"
            }
            val payload = JSONObject()
                .put("cascadeId", cascadeId)
                .put("cascade_id", cascadeId)
                .put(
                    "interaction",
                    JSONObject()
                        .put("trajectoryId", trajectoryId)
                        .put("stepIndex", stepIndex)
                        .put("permission", JSONObject().put("allow", allow))
                ).toString()
            val req = Request.Builder()
                .url(endpoint)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            AgyOkHttpClient.client.newCall(req).execute().close()
        } catch (e: Exception) {
            Log.w(TAG, "resolveCascadePermission notice: ${e.message}")
        }
    }

    fun submitAskQuestionAnswers(
        cascadeId: String = activeCascadeId,
        stepIndex: Int,
        responses: List<com.jarvisai.assistant.model.ChoiceResponseItem>,
        hubUrl: String = com.jarvisai.assistant.terminal.LocalEnvironmentManager.getHubBaseUrl(),
        cancelled: Boolean = false,
        readableSummaryOverride: String? = null
    ): Boolean {
        val resolvedCloud = AntigravityToolSuite.resolvePendingQuestion(stepIndex, responses, cancelled)
        val effectiveCascadeId = cascadeId.ifBlank { activeCascadeId }.ifBlank { AskQuestionSharedState.lastCascadeId }
        val effectiveTrajectoryId = activeTrajectoryId.ifBlank { AskQuestionSharedState.lastTrajectoryId }

        val computedSummary = readableSummaryOverride?.takeIf { it.isNotBlank() } ?: if (cancelled) {
            "User skipped/cancelled the question. Proceed autonomously using sensible defaults."
        } else {
            responses.joinToString("\n") { resp ->
                val selected = resp.selectedOptionIds.filter { it.isNotBlank() }.joinToString(", ")
                val writeIn = resp.writeInResponse?.trim().orEmpty()
                buildString {
                    append("- ${resp.question}: ")
                    if (selected.isNotBlank()) append(selected)
                    if (selected.isNotBlank() && writeIn.isNotBlank()) append(" | Note: ")
                    if (writeIn.isNotBlank()) append(writeIn)
                    if (selected.isBlank() && writeIn.isBlank()) append("Confirmed")
                }
            }.ifBlank { "Confirmed by user." }
        }

        if (effectiveCascadeId.isNotBlank()) {
            val initialRec = SubmittedAskQuestionRecord(
                readableSummary = computedSummary,
                cancelled = cancelled,
                submittedAtMs = System.currentTimeMillis(),
                interactionAccepted = resolvedCloud,
                responses = responses
            )
            AskQuestionSharedState.submittedRecords["$effectiveCascadeId:$stepIndex"] = initialRec
            AskQuestionSharedState.submittedRecords["latest:$effectiveCascadeId"] = initialRec
        }

        if (resolvedCloud && effectiveCascadeId.isBlank()) {
            return true
        }
        if (effectiveCascadeId.isBlank()) return false

        return try {
            val endpointUrl = if (hubUrl.endsWith("LanguageServerService")) {
                "$hubUrl/HandleCascadeUserInteraction"
            } else {
                "$hubUrl/exa.language_server_pb.LanguageServerService/HandleCascadeUserInteraction"
            }
            val detectedInteractionId = AskQuestionSharedState.lastInteractionId
            val detectedKey = AskQuestionSharedState.lastInteractionKey.ifBlank { "askQuestion" }

            fun buildVariantPayload(variant: Int): String {
                val entriesArr = org.json.JSONArray()
                val answersMap = org.json.JSONObject()
                responses.forEach { resp ->
                    val cleanIds = resp.selectedOptionIds.filter { it.isNotBlank() }
                    val writeIn = resp.writeInResponse?.trim().orEmpty()
                    val joinedAnswer = (cleanIds + listOfNotNull(writeIn.takeIf { it.isNotBlank() }))
                        .joinToString(", ")
                    answersMap.put(resp.question, joinedAnswer.ifBlank { "Confirmed" })
                    val obj = org.json.JSONObject()
                    when (variant) {
                        0 -> {
                            obj.put("question", resp.question)
                            obj.put("selectedOptionIds", org.json.JSONArray(cleanIds))
                            if (writeIn.isNotBlank()) obj.put("writeInResponse", writeIn)
                        }
                        1 -> {
                            obj.put("question", resp.question)
                            obj.put("selectedOptions", org.json.JSONArray(cleanIds))
                            if (writeIn.isNotBlank()) obj.put("freeTextResponse", writeIn)
                        }
                        2 -> {
                            obj.put("question", resp.question)
                            obj.put("selectedOptions", org.json.JSONArray(cleanIds))
                            if (writeIn.isNotBlank()) obj.put("customAnswer", writeIn)
                            if (joinedAnswer.isNotBlank()) obj.put("answer", joinedAnswer)
                        }
                        else -> {
                            obj.put("question", resp.question)
                            obj.put("selected_option_ids", org.json.JSONArray(cleanIds))
                            if (writeIn.isNotBlank()) obj.put("write_in_response", writeIn)
                        }
                    }
                    entriesArr.put(obj)
                }

                val askQuestionObj = org.json.JSONObject().apply {
                    put("responses", entriesArr)
                    if (variant == 2) {
                        put("answers", answersMap)
                    }
                    if (variant == 3) {
                        if (cancelled) put("cancelled", true)
                    } else {
                        put("cancelled", cancelled)
                    }
                }

                val interaction = org.json.JSONObject().apply {
                    if (effectiveTrajectoryId.isNotBlank()) {
                        put(if (variant == 3) "trajectory_id" else "trajectoryId", effectiveTrajectoryId)
                    }
                    if (detectedInteractionId.isNotBlank()) {
                        put(if (variant == 3) "interaction_id" else "interactionId", detectedInteractionId)
                    }
                    put(if (variant == 3) "step_index" else "stepIndex", stepIndex)
                    val fieldKey = when (variant) {
                        0 -> detectedKey
                        1 -> "askQuestion"
                        2 -> "askUserQuestion"
                        else -> "ask_question"
                    }
                    put(fieldKey, askQuestionObj)
                }

                return org.json.JSONObject()
                    .put("cascadeId", effectiveCascadeId)
                    .put("cascade_id", effectiveCascadeId)
                    .apply {
                        if (detectedInteractionId.isNotBlank()) {
                            put("interactionId", detectedInteractionId)
                            put("interaction_id", detectedInteractionId)
                        }
                    }
                    .put("interaction", interaction)
                    .toString()
            }

            var accepted = false
            for (variant in 0..3) {
                val payload = buildVariantPayload(variant)
                val req = Request.Builder()
                    .url(endpointUrl)
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                AgyOkHttpClient.client.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    val explicitlyRejected = bodyStr.contains("\"handled\":false", ignoreCase = true) ||
                        bodyStr.contains("\"handled\": false", ignoreCase = true) ||
                        bodyStr.contains("\"error\"", ignoreCase = true)
                    if (resp.isSuccessful && !explicitlyRejected) {
                        Log.i(TAG, "HandleCascadeUserInteraction accepted ask_question (variant=$variant, step=$stepIndex, body=$bodyStr)")
                        accepted = true
                    } else {
                        Log.w(TAG, "HandleCascadeUserInteraction variant=$variant rejected/HTTP ${resp.code}: $bodyStr")
                    }
                }
                if (accepted) break
            }

            if (accepted) {
                val rec = SubmittedAskQuestionRecord(
                    readableSummary = computedSummary,
                    cancelled = cancelled,
                    submittedAtMs = System.currentTimeMillis(),
                    interactionAccepted = true,
                    responses = responses
                )
                AskQuestionSharedState.submittedRecords["$effectiveCascadeId:$stepIndex"] = rec
                AskQuestionSharedState.submittedRecords["latest:$effectiveCascadeId"] = rec
            }
            accepted || resolvedCloud
        } catch (e: Exception) {
            Log.w(TAG, "submitAskQuestionAnswers exception: ${e.message}")
            resolvedCloud
        }
    }

    fun handleAskQuestionInteraction(
        cascadeId: String = activeCascadeId,
        stepIndex: Int,
        responses: List<com.jarvisai.assistant.model.ChoiceResponseItem>,
        hubUrl: String = com.jarvisai.assistant.terminal.LocalEnvironmentManager.getHubBaseUrl(),
        cancelled: Boolean = false
    ): Boolean = submitAskQuestionAnswers(cascadeId, stepIndex, responses, hubUrl, cancelled)

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
        } catch (e: Exception) {
            Log.e(TAG, "submitQuestionnaireResponses exception: ${e.message}", e)
            false
        }
    }

    suspend fun loadCodeAssist(token: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val endpoints = listOf(ENDPOINT_PROD, ENDPOINT_DAILY)
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
                    if (response.isSuccessful && text.isNotBlank()) {
                        val root = JSONObject(text)
                        val projObj = root.optJSONObject("cloudaicompanionProject")
                        var projectId = projObj?.optString("id", "") ?: root.optString("cloudaicompanionProject", "")

                        if (projectId.equals("rising-fact-p41fc", ignoreCase = true) ||
                            projectId.equals("aicode-consumers", ignoreCase = true) ||
                            projectId.equals("cloudcode-default", ignoreCase = true)
                        ) {
                            projectId = ""
                        }

                        val paidTierObj = root.optJSONObject("paidTier")
                        val currentTierObj = root.optJSONObject("currentTier")
                        val rawTier = paidTierObj?.optString("id", "")?.takeIf { it.isNotBlank() }
                            ?: currentTierObj?.optString("id", "")?.takeIf { it.isNotBlank() }
                            ?: paidTierObj?.optString("name", "")?.takeIf { it.isNotBlank() }
                            ?: currentTierObj?.optString("name", "")?.takeIf { it.isNotBlank() }
                            ?: "free"
                        Log.i(TAG, "loadCodeAssist success on $endpoint! Project: '$projectId', Tier: '$rawTier'")
                        return@withContext Result.success(Pair(projectId, rawTier))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "loadCodeAssist attempt failed on $endpoint: ${e.message}")
            }
        }
        Result.failure(Exception("Unable to load Antigravity account entitlements"))
    }

    private fun inferSupportsThinking(modelId: String, displayName: String): Boolean {
        val hay = "$modelId $displayName".lowercase()
        if (hay.contains("lite") || hay.contains("oss") || hay.contains("gemma")) return false
        return hay.contains("thinking") ||
            hay.contains("gemini-4") ||
            hay.contains("gemini-3") ||
            hay.contains("gemini-2.5") ||
            hay.contains("carbon") ||
            hay.contains("argon") ||
            hay.contains("hydrogen") ||
            hay.contains("orchestrator") ||
            hay.contains("claude") ||
            hay.contains("sonnet") ||
            hay.contains("opus")
    }

    private fun inferSupportedThinkingLevels(modelId: String, displayName: String, supportsThinking: Boolean): List<String> {
        if (!supportsThinking) return emptyList()
        val hay = "$modelId $displayName".lowercase()
        return when {
            hay.contains("gemini-4") || hay.contains("carbon") || hay.contains("argon") || hay.contains("hydrogen") ->
                listOf("low", "medium", "high", "xhigh")
            hay.contains("lite") -> listOf("minimal", "low", "medium", "high")
            else -> listOf("low", "medium", "high")
        }
    }

    private fun extractThinkingEffortSuffixKey(id: String, displayName: String): String {
        val hay = "$id $displayName".lowercase()
        return when {
            Regex("""(?:^|[-_\s(])(xhigh|extra(?:[\s_-]?high)?)\)?$""").containsMatchIn(id.lowercase()) ||
                Regex("""\(\s*(?:xhigh|extra(?:\s*high)?)\s*\)$""").containsMatchIn(displayName.lowercase()) ||
                hay.endsWith(" xhigh") || hay.endsWith(" extra") -> "xhigh"
            Regex("""(?:^|[-_\s(])high\)?$""").containsMatchIn(id.lowercase()) ||
                Regex("""\(\s*high\s*\)$""").containsMatchIn(displayName.lowercase()) ||
                Regex("""\s+high$""").containsMatchIn(displayName.lowercase()) -> "high"
            Regex("""(?:^|[-_\s(])(?:med|medium)\)?$""").containsMatchIn(id.lowercase()) ||
                Regex("""\(\s*(?:med|medium)\s*\)$""").containsMatchIn(displayName.lowercase()) ||
                Regex("""\s+(?:med|medium)$""").containsMatchIn(displayName.lowercase()) -> "medium"
            Regex("""(?:^|[-_\s(])low\)?$""").containsMatchIn(id.lowercase()) ||
                Regex("""\(\s*low\s*\)$""").containsMatchIn(displayName.lowercase()) ||
                Regex("""\s+low$""").containsMatchIn(displayName.lowercase()) -> "low"
            Regex("""(?:^|[-_\s(])minimal\)?$""").containsMatchIn(id.lowercase()) ||
                Regex("""\(\s*minimal\s*\)$""").containsMatchIn(displayName.lowercase()) -> "minimal"
            else -> "base"
        }
    }

    private fun sanitizeBrokenModelId(id: String): String {
        return id.trim()
            .replace(Regex("""[-_\s]*\((?:xhigh|extra\s*high|extra|high|medium|med|low|minimal)\)$""", RegexOption.IGNORE_CASE), "")
            .trimEnd('-', '_')
    }

    private fun stripThinkingSuffixFromId(id: String): String {
        return sanitizeBrokenModelId(id)
            .replace(Regex("""[-_](xhigh|extra-high|extra_high|extra|high|medium|med|low|minimal)$""", RegexOption.IGNORE_CASE), "")
            .trimEnd('-', '_')
    }

    private fun stripThinkingSuffixFromDisplay(displayName: String, baseId: String): String {
        val cleaned = displayName.trim()
            .replace(Regex("""\s*\((?:xhigh|extra\s*high|extra|high|medium|med|low|minimal)\)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:xhigh|extra\s*high|extra|high|medium|med|low|minimal)$""", RegexOption.IGNORE_CASE), "")
            .trim()
        return cleaned.ifBlank { prettyModelDisplayName(baseId) }
    }

    private fun prettyModelDisplayName(modelId: String): String {
        val bare = modelId.lowercase()
        return when {
            bare.contains("orchestrator") && bare.contains("carbon") -> "Orchestrator (Carbon)"
            bare.contains("orchestrator") && bare.contains("argon") -> "Orchestrator (Argon)"
            bare.contains("gemini-4") && bare.contains("carbon") -> "Gemini 4 Carbon"
            bare.contains("gemini-4") && bare.contains("argon") -> "Gemini 4 Argon"
            bare.contains("gemini-4") && bare.contains("hydrogen") -> "Gemini 4 Hydrogen"
            bare.contains("3.8") && bare.contains("flash") -> "Gemini 3.8 Flash"
            bare.contains("3.7") && bare.contains("flash") -> "Gemini 3.7 Flash"
            bare.contains("3.6") && bare.contains("flash") -> "Gemini 3.6 Flash"
            bare.contains("3.1") && bare.contains("pro") -> "Gemini 3.1 Pro"
            else -> modelId
        }
    }

    private fun groupModelsByThinkingFamily(rawList: List<AntigravityModelInfo>): List<AntigravityModelInfo> {
        if (rawList.isEmpty()) return emptyList()
        val grouped = linkedMapOf<String, MutableList<AntigravityModelInfo>>()
        rawList.forEach { item ->
            val baseId = stripThinkingSuffixFromId(item.id)
            val key = baseId.lowercase()
            grouped.getOrPut(key) { mutableListOf() }.add(item)
        }

        return grouped.entries.map { (_, variants) ->
            val variantMap = linkedMapOf<String, String>()
            variants.forEach { v ->
                val effort = extractThinkingEffortSuffixKey(v.id, v.displayName)
                variantMap.putIfAbsent(effort, v.id)
                v.thinkingVariantIds.forEach { (k, vid) -> variantMap.putIfAbsent(k, vid) }
            }

            // Pick clean base ID (prefer exact un-suffixed ID if present, else stripped baseId)
            val baseVariant = variants.firstOrNull {
                extractThinkingEffortSuffixKey(it.id, it.displayName) == "base"
            } ?: variants.first()

            val cleanBaseId = stripThinkingSuffixFromId(baseVariant.id).ifBlank { baseVariant.id }
            val cleanDisplay = stripThinkingSuffixFromDisplay(baseVariant.displayName, cleanBaseId)
            val supportsThinking = variants.any { it.supportsThinking } || inferSupportsThinking(cleanBaseId, cleanDisplay)
            val detectedLevels = variantMap.keys.filter { it in listOf("minimal", "low", "medium", "high", "xhigh") }
            val supportedLevels = if (detectedLevels.isNotEmpty()) {
                detectedLevels
            } else {
                inferSupportedThinkingLevels(cleanBaseId, cleanDisplay, supportsThinking)
            }

            AntigravityModelInfo(
                id = cleanBaseId,
                displayName = cleanDisplay,
                remainingFraction = variants.maxOf { it.remainingFraction },
                resetTime = variants.firstOrNull { it.resetTime.isNotBlank() }?.resetTime.orEmpty(),
                supportsThinking = supportsThinking,
                isRecommended = variants.any { it.isRecommended },
                maxTokens = variants.maxOf { it.maxTokens },
                tagTitle = variants.firstOrNull { it.tagTitle.isNotBlank() }?.tagTitle.orEmpty(),
                supportsImages = variants.any { it.supportsImages },
                thinkingVariantIds = variantMap,
                supportedThinkingLevels = supportedLevels
            )
        }.sortedByDescending { rankModelForDisplay(it) }
    }

    private fun rankModelForDisplay(model: AntigravityModelInfo): Int {
        val hay = "${model.id} ${model.displayName}".lowercase()
        return when {
            model.isRecommended -> 100
            hay.contains("orchestrator") && hay.contains("carbon") -> 96
            hay.contains("orchestrator") && hay.contains("argon") -> 94
            hay.contains("orchestrator") -> 90
            hay.contains("gemini-4") && hay.contains("carbon") -> 88
            hay.contains("gemini-4") && hay.contains("argon") -> 86
            hay.contains("gemini-4") && hay.contains("hydrogen") -> 84
            hay.contains("3.8") && hay.contains("flash") -> 80
            hay.contains("3.7") || hay.contains("3.6") || hay.contains("3.1") -> 75
            hay.contains("claude") && hay.contains("opus") -> 72
            hay.contains("claude") && hay.contains("sonnet") -> 70
            hay.contains("2.5") -> 65
            else -> 50
        }
    }

    private fun parseCloudCodeModelDetail(
        modelId: String,
        detail: JSONObject,
        allowInternalIds: Set<String>
    ): AntigravityModelInfo? {
        if (modelId.isBlank()) return null
        val isInternal = detail.optBoolean("isInternal", false)
        if (isInternal && !allowInternalIds.contains(modelId.lowercase())) {
            return null
        }
        val displayName = detail.optString("displayName", "")
            .ifBlank { detail.optString("label", "") }
            .ifBlank { detail.optString("name", "") }
            .ifBlank { prettyModelDisplayName(modelId) }
        val quota = detail.optJSONObject("quotaInfo") ?: detail.optJSONObject("quota")
        val remaining = quota?.optDouble("remainingFraction", 1.0)?.coerceIn(0.0, 1.0) ?: 1.0
        val resetTime = quota?.optString("resetTime", "").orEmpty()
        val supportsThinking = if (detail.has("supportsThinking")) {
            detail.optBoolean("supportsThinking", true)
        } else {
            inferSupportsThinking(modelId, displayName)
        }
        val isRecommended = detail.optBoolean("recommended", false) ||
            detail.optBoolean("isRecommended", false)
        val maxTokens = detail.optInt("maxTokens", 0)
        val tagTitle = detail.optString("tagTitle", "").orEmpty()
        val supportsImages = detail.optBoolean("supportsImages", true)

        return AntigravityModelInfo(
            id = modelId,
            displayName = displayName,
            remainingFraction = remaining,
            resetTime = resetTime,
            supportsThinking = supportsThinking,
            isRecommended = isRecommended,
            maxTokens = maxTokens,
            tagTitle = tagTitle,
            supportsImages = supportsImages
        )
    }

    suspend fun fetchAvailableModels(
        token: String,
        projectId: String = "",
        hubBaseUrl: String = JarvisHubConfig.getLanguageServiceUrl()
    ): Result<List<AntigravityModelInfo>> = withContext(Dispatchers.IO) {
        val cleanProj = if (projectId.isBlank() ||
            projectId.equals("rising-fact-p41fc", ignoreCase = true) ||
            projectId.equals("aicode-consumers", ignoreCase = true) ||
            projectId.equals("cloudcode-default", ignoreCase = true)
        ) "" else projectId

        val collected = linkedMapOf<String, AntigravityModelInfo>()

        // 1. Query BOTH daily-cloudcode-pa (preview/Gemini 4 models like Argon & Hydrogen) AND prod cloudcode-pa FIRST
        // so canonical API model IDs take priority over local hub display-slugs.
        if (token.isNotBlank()) {
            val endpoints = listOf(ENDPOINT_DAILY, ENDPOINT_PROD)
            var lastRemoteErr: String? = null
            var remoteSucceededCount = 0

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
                        if (response.isSuccessful && text.isNotBlank()) {
                            remoteSucceededCount++
                            val json = JSONObject(text)

                            // Collect explicitly promoted IDs from agentModelSorts so internal-flagged preview models stay visible when listed
                            val sortedIds = mutableSetOf<String>()
                            val sortsArr = json.optJSONArray("agentModelSorts")
                            if (sortsArr != null) {
                                for (si in 0 until sortsArr.length()) {
                                    val sortObj = sortsArr.optJSONObject(si) ?: continue
                                    val groups = sortObj.optJSONArray("groups") ?: continue
                                    for (gi in 0 until groups.length()) {
                                        val grp = groups.optJSONObject(gi) ?: continue
                                        val mIds = grp.optJSONArray("modelIds") ?: continue
                                        for (mi in 0 until mIds.length()) {
                                            val mid = mIds.optString(mi, "").trim().lowercase()
                                            if (mid.isNotBlank()) sortedIds.add(mid)
                                        }
                                    }
                                }
                            }

                            val modelsObj = json.optJSONObject("models")
                            if (modelsObj != null) {
                                val keys = modelsObj.keys()
                                while (keys.hasNext()) {
                                    val modelId = keys.next()
                                    val detail = modelsObj.optJSONObject(modelId) ?: continue
                                    parseCloudCodeModelDetail(modelId, detail, sortedIds)?.let { parsed ->
                                        val key = parsed.id.lowercase()
                                        if (!collected.containsKey(key)) {
                                            collected[key] = parsed
                                        }
                                    }
                                }
                            }

                            val modelsArr = json.optJSONArray("models")
                            if (modelsArr != null) {
                                for (ai in 0 until modelsArr.length()) {
                                    val detail = modelsArr.optJSONObject(ai) ?: continue
                                    val modelId = detail.optString("id", "")
                                        .ifBlank { detail.optString("model", "") }
                                        .ifBlank { detail.optString("name", "") }
                                        .trim()
                                    parseCloudCodeModelDetail(modelId, detail, sortedIds)?.let { parsed ->
                                        val key = parsed.id.lowercase()
                                        if (!collected.containsKey(key)) {
                                            collected[key] = parsed
                                        }
                                    }
                                }
                            }
                        } else {
                            lastRemoteErr = "HTTP ${response.code}"
                        }
                    }
                } catch (e: Exception) {
                    lastRemoteErr = e.message
                    Log.w(TAG, "fetchAvailableModels failed on $endpoint: ${e.message}")
                }
            }

            if (collected.isNotEmpty()) {
                val grouped = groupModelsByThinkingFamily(collected.values.toList())
                Log.i(TAG, "fetchAvailableModels merged ${collected.size} variants -> ${grouped.size} clean models from $remoteSucceededCount endpoint(s)")
                return@withContext Result.success(grouped)
            }
        }

        // 2. Fallback to live Local Hub (`agy --hub`) catalog only if CloudCode PA returned nothing or no token is present
        if (collected.isEmpty() && (hubBaseUrl.contains("127.0.0.1") || hubBaseUrl.contains("localhost"))) {
            runCatching {
                val localInspection = inspectLocalHubCatalog(hubBaseUrl)
                if (localInspection.hasValidAuth == true && localInspection.candidates.isNotEmpty()) {
                    localInspection.candidates.forEach { c ->
                        val canonicalId = sanitizeBrokenModelId(
                            c.modelUid?.takeIf { it.isNotBlank() }
                                ?: c.modelSlug.takeIf { it.isNotBlank() && !it.startsWith("model_") }
                                ?: c.protoName
                                ?: c.label
                        )
                        val display = c.label.ifBlank { canonicalId }
                        if (canonicalId.isNotBlank()) {
                            collected[canonicalId.lowercase()] = AntigravityModelInfo(
                                id = canonicalId,
                                displayName = display,
                                remainingFraction = c.remainingFraction ?: 1.0,
                                resetTime = c.resetTime.orEmpty(),
                                supportsThinking = c.supportsThinking ?: inferSupportsThinking(canonicalId, display),
                                isRecommended = c.isRecommended,
                                tagTitle = c.tagTitle.orEmpty(),
                                supportsImages = c.supportsImages ?: true
                            )
                        }
                    }
                }
            }
        }

        if (collected.isNotEmpty()) {
            return@withContext Result.success(groupModelsByThinkingFamily(collected.values.toList()))
        }
        if (token.isNotBlank()) {
            Result.failure(Exception("Could not load supported models for your Antigravity account."))
        } else {
            Result.failure(Exception("Not signed in to Antigravity"))
        }
    }

    private data class CatalogModelCandidate(
        val enumTag: Int? = null,
        val protoName: String? = null,
        val aliasTag: Int? = null,
        val modelUid: String? = null,
        val label: String = "",
        val modelSlug: String = "",
        val isRecommended: Boolean = false,
        val remainingFraction: Double? = null,
        val resetTime: String? = null,
        val supportsThinking: Boolean? = null,
        val tagTitle: String? = null,
        val supportsImages: Boolean? = null
    ) {
        fun uniqueKey(): String = listOfNotNull(
            modelUid?.takeIf { it.isNotBlank() },
            protoName?.takeIf { it.isNotBlank() },
            enumTag?.toString(),
            aliasTag?.let { "alias:$it" }
        ).firstOrNull() ?: label

        fun toModelOrAliasJson(): JSONObject = JSONObject().apply {
            when {
                enumTag != null -> put("model", enumTag)
                aliasTag != null -> put("alias", aliasTag)
                !protoName.isNullOrBlank() -> put("model", protoName)
                !modelUid.isNullOrBlank() -> put("modelUid", modelUid)
            }
        }
    }

    private data class HubInspectionResult(
        val candidates: List<CatalogModelCandidate>,
        val hasValidAuth: Boolean?
    )

    private fun parseModelEntryFromJson(obj: JSONObject, fallbackKey: String = ""): CatalogModelCandidate? {
        val label = obj.optString("label", "")
            .ifBlank { obj.optString("displayName", "") }
            .ifBlank { obj.optString("name", "") }
            .ifBlank { fallbackKey }

        val moa = obj.optJSONObject("modelOrAlias") ?: obj.optJSONObject("model_or_alias")
        val rawModel = moa?.opt("model") ?: obj.opt("model")
        val rawAlias = moa?.opt("alias") ?: obj.opt("alias")

        var enumTag: Int? = null
        var protoName: String? = null
        when (rawModel) {
            is Number -> enumTag = rawModel.toInt()
            is String -> {
                val trimmed = rawModel.trim()
                val asInt = trimmed.toIntOrNull()
                if (asInt != null) enumTag = asInt else if (trimmed.isNotBlank()) protoName = trimmed
            }
        }
        if (protoName == null && fallbackKey.startsWith("MODEL_")) {
            protoName = fallbackKey
        }
        val aliasTag: Int? = when (rawAlias) {
            is Number -> rawAlias.toInt()
            is String -> rawAlias.trim().toIntOrNull()
            else -> null
        }

        val explicitUid = moa?.optString("modelUid", "")?.takeIf { it.isNotBlank() }
            ?: moa?.optString("model_uid", "")?.takeIf { it.isNotBlank() }
            ?: obj.optString("modelUid", "").takeIf { it.isNotBlank() }
            ?: obj.optString("model_uid", "").takeIf { it.isNotBlank() }
        val fallbackIdAsUid = if (enumTag == null && aliasTag == null && protoName.isNullOrBlank()) {
            obj.optString("id", "").takeIf { it.isNotBlank() && !it.startsWith("MODEL_") }
        } else null
        val modelUid = explicitUid ?: fallbackIdAsUid

        if (enumTag == null && protoName.isNullOrBlank() && aliasTag == null && modelUid.isNullOrBlank()) {
            return null
        }

        val quota = obj.optJSONObject("quotaInfo") ?: obj.optJSONObject("quota_info") ?: obj.optJSONObject("quota")
        val remaining = quota?.optDouble("remainingFraction", -1.0)?.takeIf { it >= 0.0 }
        val resetTime = quota?.optString("resetTime", "")?.takeIf { it.isNotBlank() }
        val supportsThinking = if (obj.has("supportsThinking")) obj.optBoolean("supportsThinking") else null
        val supportsImages = if (obj.has("supportsImages")) obj.optBoolean("supportsImages") else null
        val tagTitle = obj.optString("tagTitle", "").ifBlank { obj.optString("tag_title", "") }.takeIf { it.isNotBlank() }
        val cleanSlug = sanitizeBrokenModelId(
            modelUid ?: obj.optString("id", "").takeIf { it.isNotBlank() } ?: label.lowercase().replace(" ", "-")
        )

        return CatalogModelCandidate(
            enumTag = enumTag,
            protoName = protoName,
            aliasTag = aliasTag,
            modelUid = modelUid,
            label = label.ifBlank { protoName ?: modelUid ?: "model" },
            modelSlug = cleanSlug,
            isRecommended = obj.optBoolean("isRecommended", false) || obj.optBoolean("is_recommended", false),
            remainingFraction = remaining,
            resetTime = resetTime,
            supportsThinking = supportsThinking,
            tagTitle = tagTitle,
            supportsImages = supportsImages
        )
    }

    private fun extractCandidatesFromHubJson(body: String, target: MutableList<CatalogModelCandidate>) {
        if (body.isBlank()) return
        runCatching {
            val root = JSONObject(body)
            val userStatus = root.optJSONObject("userStatus") ?: root.optJSONObject("user_status")
            val cascadeConfigData = userStatus?.optJSONObject("cascadeModelConfigData")
                ?: userStatus?.optJSONObject("cascade_model_config_data")

            val arrays = listOfNotNull(
                root.optJSONArray("clientModelConfigs"),
                root.optJSONArray("client_model_configs"),
                cascadeConfigData?.optJSONArray("clientModelConfigs"),
                cascadeConfigData?.optJSONArray("client_model_configs"),
                root.optJSONArray("models")
            )
            for (arr in arrays) {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { item ->
                        parseModelEntryFromJson(item)?.let { target.add(it) }
                    }
                }
            }

            val modelMaps = listOfNotNull(
                root.optJSONObject("models"),
                root.optJSONObject("availableModels"),
                root.optJSONObject("available_models")
            )
            for (mapObj in modelMaps) {
                val keys = mapObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val entry = mapObj.optJSONObject(key)
                    if (entry != null) {
                        parseModelEntryFromJson(entry, fallbackKey = key)?.let { target.add(it) }
                    }
                }
            }

            val defaultObj = root.optJSONObject("defaultOverrideModelConfig")
                ?: root.optJSONObject("default_override_model_config")
                ?: cascadeConfigData?.optJSONObject("defaultOverrideModelConfig")
                ?: cascadeConfigData?.optJSONObject("default_override_model_config")
            if (defaultObj != null) {
                parseModelEntryFromJson(defaultObj, fallbackKey = "default")?.let { target.add(it) }
            }
        }
    }

    private fun inspectLocalHubCatalog(resolvedHubUrl: String): HubInspectionResult {
        val discovered = mutableListOf<CatalogModelCandidate>()
        var hasValidAuth: Boolean? = null

        fun postHub(rpcName: String, jsonBody: String = "{}"): String {
            return try {
                val req = Request.Builder()
                    .url("$resolvedHubUrl/$rpcName")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()
                AgyOkHttpClient.client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
                }
            } catch (_: Exception) {
                ""
            }
        }

        val authBody = postHub("GetAuthStatus")
        if (authBody.isNotBlank()) {
            runCatching {
                val authJson = JSONObject(authBody)
                val authResult = authJson.optJSONObject("authResult") ?: authJson.optJSONObject("auth_result")
                if (authResult != null) {
                    hasValidAuth = authResult.optBoolean("hasValidAuth", false) ||
                        authResult.optBoolean("has_valid_auth", false)
                }
            }
        }

        extractCandidatesFromHubJson(postHub("GetCascadeModelConfigData"), discovered)
        extractCandidatesFromHubJson(postHub("GetAvailableModels", """{"forceRefresh":false}"""), discovered)
        if (discovered.isEmpty()) {
            extractCandidatesFromHubJson(postHub("GetAvailableModels", """{"forceRefresh":true}"""), discovered)
        }
        if (discovered.isEmpty()) {
            extractCandidatesFromHubJson(postHub("GetUserStatus"), discovered)
        }

        val deduped = discovered.distinctBy { it.uniqueKey() }
        Log.i(TAG, "Local Hub inspection: auth=$hasValidAuth, catalogCount=${deduped.size}, models=${deduped.joinToString { "${it.label}(${it.protoName ?: it.enumTag ?: it.modelUid})" }}")
        return HubInspectionResult(deduped, hasValidAuth)
    }

    private fun matchesRequestedModelFamily(
        candidateHaystack: String,
        cleanModel: String,
        enumTag: Int? = null,
        protoName: String? = null
    ): Boolean {
        val hay = "$candidateHaystack ${protoName.orEmpty()}".lowercase()
        val req = cleanModel.lowercase()
        if (hay.contains(req)) return true
        return when {
            req.contains("orchestrator") && req.contains("carbon") ->
                (hay.contains("orchestrator") && hay.contains("carbon")) || hay.contains("carbon")
            req.contains("orchestrator") && req.contains("argon") ->
                (hay.contains("orchestrator") && hay.contains("argon")) || hay.contains("argon")
            req.contains("opus") ->
                hay.contains("opus") || (req.contains("claude") && hay.contains("claude"))
            req.contains("claude") || req.contains("sonnet") ->
                hay.contains("claude") || hay.contains("sonnet") || enumTag == 1035 || protoName == "MODEL_PLACEHOLDER_M35"
            req.contains("carbon") ->
                hay.contains("carbon") || enumTag == 1670 || protoName == "MODEL_PLACEHOLDER_M670"
            req.contains("argon") ->
                hay.contains("argon") || enumTag == 1421 || protoName == "MODEL_PLACEHOLDER_M421"
            req.contains("hydrogen") ->
                hay.contains("hydrogen")
            req.contains("oss") ->
                hay.contains("oss")
            req.contains("3.8") && req.contains("flash") ->
                (hay.contains("3.8") && hay.contains("flash")) ||
                    enumTag in listOf(1318, 1319, 1320) ||
                    protoName in listOf("MODEL_PLACEHOLDER_M318", "MODEL_PLACEHOLDER_M319", "MODEL_PLACEHOLDER_M320")
            req.contains("3.7") && req.contains("flash") ->
                hay.contains("3.7") && hay.contains("flash")
            req.contains("3.6") && req.contains("flash") ->
                hay.contains("3.6") && hay.contains("flash")
            req.contains("3.1") && req.contains("pro") ->
                hay.contains("3.1") && hay.contains("pro")
            else -> false
        }
    }

    private fun scoreCatalogCandidate(
        candidate: CatalogModelCandidate,
        cleanModel: String,
        thinkingBudget: Int
    ): Int {
        val hay = "${candidate.label} ${candidate.protoName.orEmpty()} ${candidate.modelUid.orEmpty()} ${candidate.modelSlug}".lowercase()
        if (!matchesRequestedModelFamily(hay, cleanModel, candidate.enumTag, candidate.protoName)) {
            return Int.MIN_VALUE
        }

        var score = 0
        if (hay.contains(cleanModel.lowercase())) score += 80
        val wantsClaude = cleanModel.contains("claude", ignoreCase = true) || cleanModel.contains("sonnet", ignoreCase = true) || cleanModel.contains("opus", ignoreCase = true)
        val wantsCarbon = cleanModel.contains("carbon", ignoreCase = true)
        val wantsArgon = cleanModel.contains("argon", ignoreCase = true)
        val wantsHydrogen = cleanModel.contains("hydrogen", ignoreCase = true)
        val wantsFlash38 = cleanModel.contains("3.8", ignoreCase = true) || cleanModel.contains("flash", ignoreCase = true)

        when {
            wantsClaude -> {
                if (cleanModel.contains("opus", true) && hay.contains("opus")) score += 120
                if (cleanModel.contains("sonnet", true) && hay.contains("sonnet")) score += 120
                if (hay.contains("claude")) score += 60
                if (hay.contains("5.5") || hay.contains("4.6")) score += 20
                if (candidate.enumTag == 1035 || candidate.protoName == "MODEL_PLACEHOLDER_M35") score += 40
            }
            wantsCarbon -> {
                if (hay.contains("carbon")) score += 100
                if (hay.contains("orchestrator") == cleanModel.contains("orchestrator", true)) score += 35
                if (candidate.enumTag == 1670 || candidate.protoName == "MODEL_PLACEHOLDER_M670") score += 40
            }
            wantsArgon -> {
                if (hay.contains("argon")) score += 100
                if (hay.contains("orchestrator") == cleanModel.contains("orchestrator", true)) score += 35
                if (candidate.enumTag == 1421 || candidate.protoName == "MODEL_PLACEHOLDER_M421") score += 40
            }
            wantsHydrogen -> {
                if (hay.contains("hydrogen")) score += 120
                when {
                    thinkingBudget >= 12000 && (hay.contains("xhigh") || hay.contains("extra")) -> score += 45
                    thinkingBudget > 4096 && hay.contains("high") -> score += 35
                    thinkingBudget in 1001..4096 && hay.contains("med") -> score += 35
                    thinkingBudget <= 1000 && hay.contains("low") -> score += 35
                }
            }
            wantsFlash38 -> {
                if (hay.contains("3.8") && hay.contains("flash")) score += 120
                when {
                    thinkingBudget > 4096 -> {
                        if (hay.contains("high")) score += 35
                        if (candidate.enumTag == 1318 || candidate.protoName == "MODEL_PLACEHOLDER_M318") score += 40
                    }
                    thinkingBudget in 1001..4096 -> {
                        if (hay.contains("med")) score += 35
                        if (candidate.enumTag == 1319 || candidate.protoName == "MODEL_PLACEHOLDER_M319") score += 40
                    }
                    else -> {
                        if (hay.contains("low")) score += 35
                        if (candidate.enumTag == 1320 || candidate.protoName == "MODEL_PLACEHOLDER_M320") score += 40
                    }
                }
            }
        }
        if (candidate.isRecommended) score += 5
        return score
    }

    private fun buildDefaultHardcodedCandidates(cleanModel: String, thinkingBudget: Int): List<CatalogModelCandidate> {
        return when {
            cleanModel.contains("opus", ignoreCase = true) -> listOf(
                CatalogModelCandidate(modelUid = cleanModel, label = cleanModel, modelSlug = cleanModel)
            )
            cleanModel.contains("claude", ignoreCase = true) || cleanModel.contains("sonnet", ignoreCase = true) -> listOf(
                CatalogModelCandidate(modelUid = cleanModel, label = "Claude Sonnet 5.5", modelSlug = cleanModel),
                CatalogModelCandidate(1035, "MODEL_PLACEHOLDER_M35", label = "Claude Sonnet 5.5", modelSlug = cleanModel)
            )
            cleanModel.contains("carbon", ignoreCase = true) -> listOf(
                CatalogModelCandidate(modelUid = cleanModel, label = "Gemini 4 Carbon", modelSlug = cleanModel),
                CatalogModelCandidate(1670, "MODEL_PLACEHOLDER_M670", label = "Gemini 4 Carbon", modelSlug = cleanModel)
            )
            cleanModel.contains("argon", ignoreCase = true) -> listOf(
                CatalogModelCandidate(modelUid = cleanModel, label = "Gemini 4 Argon", modelSlug = cleanModel),
                CatalogModelCandidate(1421, "MODEL_PLACEHOLDER_M421", label = "Gemini 4 Argon", modelSlug = cleanModel)
            )
            cleanModel.contains("hydrogen", ignoreCase = true) || cleanModel.contains("3.7", true) || cleanModel.contains("3.6", true) || cleanModel.contains("3.1", true) || cleanModel.contains("oss", true) -> listOf(
                CatalogModelCandidate(modelUid = cleanModel, label = cleanModel, modelSlug = cleanModel)
            )
            thinkingBudget in 1001..4096 -> listOf(
                CatalogModelCandidate(modelUid = cleanModel, label = cleanModel, modelSlug = cleanModel),
                CatalogModelCandidate(1319, "MODEL_PLACEHOLDER_M319", label = "Gemini 3.8 Flash Medium", modelSlug = "gemini-3.8-flash-medium"),
                CatalogModelCandidate(1318, "MODEL_PLACEHOLDER_M318", label = "Gemini 3.8 Flash High", modelSlug = "gemini-3.8-flash-high")
            )
            thinkingBudget <= 1000 -> listOf(
                CatalogModelCandidate(modelUid = cleanModel, label = cleanModel, modelSlug = cleanModel),
                CatalogModelCandidate(1320, "MODEL_PLACEHOLDER_M320", label = "Gemini 3.8 Flash Low", modelSlug = "gemini-3.8-flash-low"),
                CatalogModelCandidate(1318, "MODEL_PLACEHOLDER_M318", label = "Gemini 3.8 Flash High", modelSlug = "gemini-3.8-flash-high")
            )
            else -> listOf(
                CatalogModelCandidate(modelUid = cleanModel, label = cleanModel, modelSlug = cleanModel),
                CatalogModelCandidate(1318, "MODEL_PLACEHOLDER_M318", label = "Gemini 3.8 Flash High", modelSlug = "gemini-3.8-flash-high"),
                CatalogModelCandidate(1319, "MODEL_PLACEHOLDER_M319", label = "Gemini 3.8 Flash Medium", modelSlug = "gemini-3.8-flash-medium")
            )
        }
    }

    private fun buildAntigravityCliToolsDeclaration(): JSONArray = AntigravityToolSuite.buildToolsDeclaration()

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AntigravityStreamEvent>.executeAntigravityCliTool(
        context: Context,
        toolId: String,
        stepIndex: Int,
        fnName: String,
        fnArgs: JSONObject
    ): Pair<ToolCall, JSONObject> = with(AntigravityToolSuite) {
        executeTool(context, toolId, stepIndex, fnName, fnArgs)
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AntigravityStreamEvent>.streamViaCloudCodePa(
        token: String,
        projectId: String,
        cleanModel: String,
        userPrompt: String,
        history: List<ChatMessageEntity>,
        systemInstruction: String,
        sessionId: String,
        thinkingBudget: Int,
        context: Context? = null,
        resolvedAttachments: List<ResolvedAttachmentPart> = emptyList()
    ): Boolean {
        if (token.isBlank()) return false

        var effectiveProject = projectId
        var rawEndpointProject = ""
        if (effectiveProject.isBlank()) {
            effectiveProject = loadCodeAssist(token).getOrNull()?.first.orEmpty()
        }

        // Strict Model Lock: ONLY execute the user's selected model (resolving exact Thinking Level variant from CloudCode PA).
        val availableModels = fetchAvailableModels(token, effectiveProject, hubBaseUrl = "").getOrDefault(emptyList())
        val normalizedRequested = sanitizeBrokenModelId(cleanModel)
        val effortKey = when {
            thinkingBudget <= 0 -> "none"
            thinkingBudget <= 1500 -> "low"
            thinkingBudget <= 6000 -> "medium"
            thinkingBudget <= 12000 -> "high"
            else -> "xhigh"
        }
        val matchedFamily = availableModels.firstOrNull {
            it.id.equals(normalizedRequested, ignoreCase = true) ||
                matchesRequestedModelFamily("${it.id} ${it.displayName}", normalizedRequested)
        }
        val preferredVariantId = if (matchedFamily != null) {
            when (effortKey) {
                "xhigh" -> matchedFamily.thinkingVariantIds["xhigh"] ?: matchedFamily.thinkingVariantIds["high"]
                "high" -> matchedFamily.thinkingVariantIds["high"] ?: matchedFamily.thinkingVariantIds["medium"]
                "medium" -> matchedFamily.thinkingVariantIds["medium"] ?: matchedFamily.thinkingVariantIds["low"]
                "low" -> matchedFamily.thinkingVariantIds["low"] ?: matchedFamily.thinkingVariantIds["base"]
                else -> matchedFamily.thinkingVariantIds["minimal"]
                    ?: matchedFamily.thinkingVariantIds["base"]
                    ?: matchedFamily.thinkingVariantIds["low"]
            }
        } else null

        val orderedCloudModels = buildList {
            preferredVariantId?.takeIf { it.isNotBlank() }?.let { add(it) }
            matchedFamily?.id?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (normalizedRequested.isNotBlank()) add(normalizedRequested)
            matchedFamily?.thinkingVariantIds?.values?.forEach { if (it.isNotBlank()) add(it) }
            availableModels
                .filter { matchesRequestedModelFamily("${it.id} ${it.displayName}", normalizedRequested) }
                .sortedByDescending { if (it.id.equals(normalizedRequested, ignoreCase = true)) 200 else 100 }
                .forEach { add(it.id) }
        }.map { sanitizeBrokenModelId(it) }.filter { it.isNotBlank() }.distinct()

        val cliRuntimeContext = if (context != null) {
            val homePath = LocalEnvironmentManager.getHomeDir(context).absolutePath
            val isolatedProjectsPath = LocalEnvironmentManager.getIsolatedProjectsDir(context).absolutePath
            val activeProject = ActiveCodeEditorRegistry.currentProjectPath?.takeIf { it.isNotBlank() } ?: isolatedProjectsPath
            val activeFile = ActiveCodeEditorRegistry.activeFilePath?.takeIf { it.isNotBlank() }
            val envInstalled = LocalEnvironmentManager.isEnvironmentInstalled(context)
            val termuxBridge = JarvisTermuxBridge(context)
            val extTermuxInstalled = termuxBridge.isTermuxInstalled()
            val extTermuxPerm = if (extTermuxInstalled) termuxBridge.hasRunCommandPermission() else false
            buildString {
                appendLine(systemInstruction)
                appendLine()
                appendLine("=== ANTIGRAVITY FULL-POWER AGENT RUNTIME (JARVIS ISOLATED) ===")
                appendLine("- Built-in Jarvis Linux Engine installed: $envInstalled (Home: $homePath)")
                appendLine("- Active IDE Workspace Root (Isolated from AntiGem): $activeProject")
                if (activeFile != null) {
                    appendLine("- Currently Open IDE Editor File: $activeFile (dirty=${ActiveCodeEditorRegistry.isDirty})")
                }
                appendLine("- External com.termux app installed: $extTermuxInstalled, RUN_COMMAND permission granted: $extTermuxPerm, Isolated Bridge Port: 2238")
                appendLine("- Embedded Isolated MCP Bridges Active on 127.0.0.1:2236 & 127.0.0.1:2237 (Android OS, Browser CDP, Terminal PTY, 5-Tier Memory, Google Flow).")
                appendLine("- Full Tool Suite: `run_command` (live streaming), `read_file`, `write_to_file`, `replace_file_content`, `multi_replace_file_content`, `list_dir`, `grep_search`, `find_by_name`, `read_active_editor`, `open_file_in_ide`, `ask_question`, `web_search`, `fetch_url`, `browser_mcp_tool`, `android_screen_control`, `jarvis_memory`, `spawn_subagent`.")
                appendLine("- Always prefer direct tool execution over claiming inability. Use `ask_question` when requirements are ambiguous. After writing code edits, use `open_file_in_ide` or `run_command` to verify builds/tests.")
            }.trim()
        } else {
            systemInstruction
        }

        val contentsArray = JSONArray()
        val normalizedPrompt = userPrompt
            .replace(Regex("\\s*\\[STM Context:[^\\]]*\\]"), "")
            .replace(Regex("\\s*\\[(?:bhai|brother|behen|didi|sister|papa|father|dad|mummy|mother|mom|best friend|boss)\\s*=\\s*[^\\]]+\\]"), "")
            .trim()
        val validHistory = history.filter { !it.isError && it.content.isNotBlank() }
        val lastHistMsg = validHistory.lastOrNull()
        val lastHistReadable = lastHistMsg?.let { com.jarvisai.assistant.model.ChatAttachmentCodec.toPromptReadableText(it.content).trim() }
        val trimmedHistory = if (lastHistMsg != null &&
            lastHistMsg.role.equals("user", ignoreCase = true) &&
            (lastHistMsg.content.trim() == userPrompt.trim() ||
                lastHistMsg.content.trim() == normalizedPrompt ||
                lastHistReadable == normalizedPrompt)
        ) {
            validHistory.dropLast(1)
        } else {
            validHistory
        }
        trimmedHistory.takeLast(24).forEach { msg ->
            val role = if (msg.role.equals("model", ignoreCase = true) ||
                msg.role.equals("assistant", ignoreCase = true)
            ) "model" else "user"
            val readableContent = com.jarvisai.assistant.model.ChatAttachmentCodec.toPromptReadableText(msg.content)
            contentsArray.put(
                JSONObject()
                    .put("role", role)
                    .put("parts", JSONArray().put(JSONObject().put("text", readableContent)))
            )
        }
        val currentUserParts = JSONArray().apply {
            val effectiveText = userPrompt.ifBlank {
                if (resolvedAttachments.isNotEmpty()) "Please analyze the attached file(s)/media thoroughly." else ""
            }
            if (effectiveText.isNotBlank()) {
                put(JSONObject().put("text", effectiveText))
            }
            resolvedAttachments.forEach { att ->
                val pathNote = att.savedWorkspacePath?.let { " (Saved at workspace path: $it)" }.orEmpty()
                if (!att.extractedText.isNullOrBlank()) {
                    put(
                        JSONObject().put(
                            "text",
                            "[Attached ${att.kind.name}: ${att.displayName}$pathNote]\n```\n${att.extractedText}\n```"
                        )
                    )
                } else if (att.savedWorkspacePath != null && att.base64Data == null && att.extraFrameBase64List.isEmpty()) {
                    put(JSONObject().put("text", "[Attached ${att.kind.name}: ${att.displayName}, mime=${att.mimeType}$pathNote]"))
                } else if (att.savedWorkspacePath != null) {
                    put(JSONObject().put("text", "[Attached ${att.kind.name}: ${att.displayName}$pathNote]"))
                }
                if (!att.base64Data.isNullOrBlank()) {
                    put(
                        JSONObject().put(
                            "inlineData",
                            JSONObject()
                                .put("mimeType", att.mimeType)
                                .put("data", att.base64Data)
                        )
                    )
                }
                att.extraFrameBase64List.forEach { frameB64 ->
                    put(
                        JSONObject().put(
                            "inlineData",
                            JSONObject()
                                .put("mimeType", "image/jpeg")
                                .put("data", frameB64)
                        )
                    )
                }
            }
        }
        contentsArray.put(
            JSONObject()
                .put("role", "user")
                .put("parts", currentUserParts)
        )

        val endpoints = listOf(ENDPOINT_DAILY, ENDPOINT_PROD)
        var lastErrorMsg = ""

        for (candidateModel in orderedCloudModels.take(6)) {
            for (endpoint in endpoints) {
                var useTools = context != null && !candidateModel.contains("oss", ignoreCase = true)
                var includeThinkingPayload = true
                var activeProjectForAttempt = effectiveProject
                var triedWithoutProject = activeProjectForAttempt.isBlank()
                var useHttp1Transport = false
                var transientNetworkRetries = 0
                var retryWithoutTools = false
                var retryWithoutThinking = false
                var retryProjectSwitch = false
                var retryTransientTransport = false
                do {
                    retryWithoutTools = false
                    retryWithoutThinking = false
                    retryProjectSwitch = false
                    retryTransientTransport = false
                    try {
                        val turnContents = JSONArray(contentsArray.toString())
                        val allToolCalls = mutableListOf<ToolCall>()
                        var accumulatedThought = ""
                        var accumulatedText = ""
                        var promptTokens = 0
                        var outputTokens = 0
                        var cachedTokens = 0
                        var totalTokens = 0
                        val thoughtStart = System.currentTimeMillis()
                        var thoughtDurationMs = 0L
                        var abortedByHttpError = false

                        val maxAgentTurns = if (useTools) 25 else 1
                        for (agentTurn in 0 until maxAgentTurns) {
                            currentCoroutineContext().ensureActive()
                            val innerRequest = JSONObject().apply {
                                put("contents", turnContents)
                                if (cliRuntimeContext.isNotBlank()) {
                                    put(
                                        "systemInstruction",
                                        JSONObject()
                                            .put("role", "user")
                                            .put("parts", JSONArray().put(JSONObject().put("text", cliRuntimeContext)))
                                    )
                                }
                                if (useTools) {
                                    put("tools", buildAntigravityCliToolsDeclaration())
                                }
                                val modelSupportsThinking = availableModels
                                    .firstOrNull {
                                        it.id.equals(candidateModel, ignoreCase = true) ||
                                            it.thinkingVariantIds.values.any { v -> v.equals(candidateModel, ignoreCase = true) }
                                    }
                                    ?.supportsThinking
                                    ?: inferSupportsThinking(candidateModel, candidateModel)
                                val lowerCandidate = candidateModel.lowercase()
                                val usesThinkingLevel = lowerCandidate.contains("gemini-4") ||
                                    lowerCandidate.contains("gemini-3") ||
                                    lowerCandidate.contains("carbon") ||
                                    lowerCandidate.contains("argon") ||
                                    lowerCandidate.contains("hydrogen") ||
                                    lowerCandidate.contains("orchestrator")
                                if (includeThinkingPayload && modelSupportsThinking) {
                                    if (usesThinkingLevel) {
                                        if (thinkingBudget > 0) {
                                            val isGemini4 = lowerCandidate.contains("gemini-4") ||
                                                lowerCandidate.contains("carbon") ||
                                                lowerCandidate.contains("argon") ||
                                                lowerCandidate.contains("hydrogen")
                                            val mappedLevel = when {
                                                thinkingBudget > 12000 -> if (isGemini4) "xhigh" else "high"
                                                thinkingBudget > 6000 -> "high"
                                                thinkingBudget > 1500 -> "medium"
                                                else -> "low"
                                            }
                                            put(
                                                "generationConfig",
                                                JSONObject().put(
                                                    "thinkingConfig",
                                                    JSONObject()
                                                        .put("includeThoughts", true)
                                                        .put("thinkingLevel", mappedLevel)
                                                )
                                            )
                                        }
                                        // When Off on Gemini 3/4, omit thinkingConfig to use default non-thinking without risking HTTP 400
                                    } else if (thinkingBudget > 0) {
                                        put(
                                            "generationConfig",
                                            JSONObject().put(
                                                "thinkingConfig",
                                                JSONObject()
                                                    .put("includeThoughts", true)
                                                    .put("thinkingBudget", thinkingBudget)
                                            )
                                        )
                                    } else {
                                        put(
                                            "generationConfig",
                                            JSONObject().put(
                                                "thinkingConfig",
                                                JSONObject()
                                                    .put("includeThoughts", false)
                                                    .put("thinkingBudget", 0)
                                            )
                                        )
                                    }
                                }
                                if (sessionId.isNotBlank()) {
                                    put("sessionId", sessionId)
                                }
                            }

                            val envelope = JSONObject().apply {
                                if (activeProjectForAttempt.isNotBlank()) {
                                    put("project", activeProjectForAttempt)
                                }
                                put("model", candidateModel)
                                put("userAgent", "antigravity")
                                put("requestId", "agent-${UUID.randomUUID()}")
                                put("request", innerRequest)
                            }

                            val headers = getBaseHeaders(token, candidateModel).newBuilder()
                                .add("Accept", "text/event-stream")
                                .build()

                            val req = Request.Builder()
                                .url("$endpoint/v1internal:streamGenerateContent?alt=sse")
                                .headers(headers)
                                .post(envelope.toString().toRequestBody(jsonMediaType))
                                .build()

                            val rawModelParts = JSONArray()
                            val turnFunctionCalls = mutableListOf<Pair<String, JSONObject>>()
                            var turnText = ""

                            val activeClient = if (useHttp1Transport) http1Client else client
                            val httpOk = activeClient.newCall(req).execute().use { resp ->
                                if (!resp.isSuccessful) {
                                    val errBody = resp.body?.string().orEmpty()
                                    lastErrorMsg = "CloudCode PA HTTP ${resp.code}: ${errBody.take(240)}"
                                    Log.w(TAG, "CloudCode PA $endpoint ($candidateModel) failed: $lastErrorMsg")
                                    if (resp.code == 400 && useTools && agentTurn == 0 &&
                                        (errBody.contains("tools", true) || errBody.contains("function", true))
                                    ) {
                                        useTools = false
                                        retryWithoutTools = true
                                    } else if (resp.code == 400 && includeThinkingPayload && agentTurn == 0 &&
                                        (errBody.contains("thinking", true) ||
                                            errBody.contains("level", true) ||
                                            errBody.contains("budget", true) ||
                                            errBody.contains("generationConfig", true))
                                    ) {
                                        includeThinkingPayload = false
                                        retryWithoutThinking = true
                                    } else if ((resp.code == 404 || resp.code == 403 || resp.code == 400) && agentTurn == 0) {
                                        if (activeProjectForAttempt.isNotBlank() && !triedWithoutProject) {
                                            triedWithoutProject = true
                                            activeProjectForAttempt = ""
                                            retryProjectSwitch = true
                                        } else if (activeProjectForAttempt.isBlank() && rawEndpointProject.isBlank()) {
                                            rawEndpointProject = runCatching {
                                                val assistReq = Request.Builder()
                                                    .url("$endpoint/v1internal:loadCodeAssist")
                                                    .headers(getBaseHeaders(token))
                                                    .post(
                                                        JSONObject().put(
                                                            "metadata",
                                                            JSONObject()
                                                                .put("ideType", "IDE_UNSPECIFIED")
                                                                .put("platform", "PLATFORM_UNSPECIFIED")
                                                                .put("pluginType", "GEMINI")
                                                        ).toString().toRequestBody(jsonMediaType)
                                                    )
                                                    .build()
                                                client.newCall(assistReq).execute().use { aResp ->
                                                    if (aResp.isSuccessful) {
                                                        val aRoot = JSONObject(aResp.body?.string().orEmpty())
                                                        aRoot.optJSONObject("cloudaicompanionProject")?.optString("id", "")
                                                            ?.ifBlank { aRoot.optString("cloudaicompanionProject", "") }
                                                            .orEmpty()
                                                    } else ""
                                                }
                                            }.getOrDefault("")
                                            if (rawEndpointProject.isNotBlank()) {
                                                activeProjectForAttempt = rawEndpointProject
                                                retryProjectSwitch = true
                                            }
                                        }
                                    }
                                    abortedByHttpError = true
                                    false
                                } else {
                                    val bodyStream = resp.body?.byteStream() ?: run {
                                        abortedByHttpError = true
                                        return@use false
                                    }
                                    bodyStream.bufferedReader().use { reader ->
                                        var line: String?
                                        while (reader.readLine().also { line = it } != null) {
                                            val trimmed = line?.trim().orEmpty()
                                            if (!trimmed.startsWith("data:")) continue
                                            val dataPayload = trimmed.removePrefix("data:").trim()
                                            if (dataPayload.isEmpty() || dataPayload == "[DONE]") continue

                                            runCatching {
                                                val root = JSONObject(dataPayload)
                                                val payloadObj = root.optJSONObject("response") ?: root
                                                val usage = payloadObj.optJSONObject("usageMetadata")
                                                if (usage != null) {
                                                    val p = usage.optInt("promptTokenCount", 0)
                                                    val c = usage.optInt("candidatesTokenCount", usage.optInt("outputTokenCount", 0))
                                                    val cached = usage.optInt("cachedContentTokenCount", 0)
                                                    val t = usage.optInt("totalTokenCount", 0)
                                                    if (p > 0) promptTokens = p
                                                    if (c > 0) outputTokens = c
                                                    if (cached > 0) cachedTokens = cached
                                                    if (t > 0) totalTokens = t
                                                }
                                                val candidates = payloadObj.optJSONArray("candidates")
                                                val parts = candidates?.optJSONObject(0)
                                                    ?.optJSONObject("content")
                                                    ?.optJSONArray("parts")
                                                if (parts != null) {
                                                    for (pi in 0 until parts.length()) {
                                                        val part = parts.optJSONObject(pi) ?: continue
                                                        val fc = part.optJSONObject("functionCall")
                                                        if (fc != null) {
                                                            rawModelParts.put(JSONObject(part.toString()))
                                                            val fnName = fc.optString("name", "")
                                                            val fnArgs = fc.optJSONObject("args") ?: JSONObject()
                                                            if (fnName.isNotBlank()) {
                                                                turnFunctionCalls.add(fnName to fnArgs)
                                                            }
                                                            continue
                                                        }
                                                        val text = part.optString("text", "")
                                                        if (text.isEmpty()) continue
                                                        if (part.optBoolean("thought", false)) {
                                                            accumulatedThought += text
                                                            thoughtDurationMs = System.currentTimeMillis() - thoughtStart
                                                            emit(AntigravityStreamEvent.ThoughtChunk(accumulatedThought))
                                                        } else {
                                                            turnText += text
                                                            accumulatedText += text
                                                            emit(AntigravityStreamEvent.TextChunk(accumulatedText))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    true
                                }
                            }

                            if (!httpOk || abortedByHttpError) break

                            if (turnFunctionCalls.isNotEmpty() && context != null) {
                                if (turnText.isNotBlank()) {
                                    rawModelParts.put(JSONObject().put("text", turnText))
                                }
                                turnContents.put(
                                    JSONObject()
                                        .put("role", "model")
                                        .put("parts", rawModelParts)
                                )
                                val fnResponseParts = JSONArray()
                                turnFunctionCalls.forEachIndexed { idx, (fnName, fnArgs) ->
                                    val stepIdx = allToolCalls.size
                                    val (completedTool, resultObj) = executeAntigravityCliTool(
                                        context = context,
                                        toolId = "cli_${agentTurn}_${idx}_${System.currentTimeMillis()}",
                                        stepIndex = stepIdx,
                                        fnName = fnName,
                                        fnArgs = fnArgs
                                    )
                                    allToolCalls.add(completedTool)
                                    fnResponseParts.put(
                                        JSONObject().put(
                                            "functionResponse",
                                            JSONObject()
                                                .put("name", fnName)
                                                .put("response", resultObj)
                                        )
                                    )
                                }
                                turnContents.put(
                                    JSONObject()
                                        .put("role", "user")
                                        .put("parts", fnResponseParts)
                                )
                                if (accumulatedText.isNotBlank() && !accumulatedText.endsWith("\n")) {
                                    accumulatedText += "\n"
                                }
                                continue
                            } else {
                                break
                            }
                        }

                        if (!abortedByHttpError && (accumulatedText.isNotBlank() || accumulatedThought.isNotBlank() || allToolCalls.isNotEmpty())) {
                            val fallbackPrompt = ((cliRuntimeContext.length + userPrompt.length + history.sumOf { it.content.length }) / 4).coerceAtLeast(1)
                            val fallbackOutput = ((accumulatedText.length + accumulatedThought.length) / 4).coerceAtLeast(1)
                            val resolvedPrompt = if (promptTokens > 0) promptTokens else fallbackPrompt
                            val resolvedOutput = if (outputTokens > 0) outputTokens else fallbackOutput
                            val resolvedTotal = if (totalTokens > 0) totalTokens else (resolvedPrompt + resolvedOutput)
                            emit(
                                AntigravityStreamEvent.Completed(
                                    totalTokens = resolvedTotal,
                                    fullText = accumulatedText.trim(),
                                    fullThought = accumulatedThought,
                                    thoughtDurationMs = thoughtDurationMs,
                                    modelUsed = cleanModel,
                                    toolCalls = allToolCalls,
                                    promptTokens = resolvedPrompt,
                                    outputTokens = resolvedOutput,
                                    cachedTokens = cachedTokens
                                )
                            )
                            return true
                        }
                    } catch (e: Exception) {
                        lastErrorMsg = e.message ?: "CloudCode PA network error"
                        Log.w(TAG, "CloudCode PA exception ($endpoint / $candidateModel, http1=$useHttp1Transport): $lastErrorMsg")
                        val isEofOrStreamReset = e is java.io.EOFException ||
                            e is java.io.IOException ||
                            lastErrorMsg.contains("EOF", ignoreCase = true) ||
                            lastErrorMsg.contains("stream", ignoreCase = true) ||
                            lastErrorMsg.contains("reset", ignoreCase = true)
                        if (isEofOrStreamReset && transientNetworkRetries < 1) {
                            transientNetworkRetries++
                            useHttp1Transport = true
                            retryTransientTransport = true
                            delay(250)
                        }
                    }
                } while (retryWithoutTools || retryWithoutThinking || retryProjectSwitch || retryTransientTransport)
            }
        }

        if (lastErrorMsg.isNotBlank()) {
            Log.e(TAG, "CloudCode PA attempts failed for $cleanModel: $lastErrorMsg")
        }
        return false
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
        thinkingBudget: Int = 4096,
        hubBaseUrl: String = JarvisHubConfig.getLanguageServiceUrl(),
        context: Context? = null,
        resolvedAttachments: List<ResolvedAttachmentPart> = emptyList()
    ): Flow<AntigravityStreamEvent> = flow {

        val cleanModel = sanitizeBrokenModelId(modelId.removePrefix("agy:").trim())
        val resolvedHubUrl = com.jarvisai.assistant.data.pc.PcConnect.getActiveHubBaseUrl()
            ?: hubBaseUrl.ifBlank { JarvisHubConfig.getLanguageServiceUrl() }
        val hubPort = JarvisHubConfig.getHubPort()

        try {
            var localHubReady = true
            // Ensure local Antigravity Hub daemon on isolated port is awake if running on-device
            if (resolvedHubUrl.contains("127.0.0.1") || resolvedHubUrl.contains("localhost")) {
                localHubReady = com.jarvisai.assistant.terminal.LocalServerManager.warmUpAgyHub(maxWaitMs = 8000)
                if (!localHubReady) {
                    delay(1000)
                    localHubReady = com.jarvisai.assistant.terminal.LocalServerManager.warmUpAgyHub(maxWaitMs = 8000)
                }
            }

            val hasBinaryAttachments = resolvedAttachments.any {
                !it.base64Data.isNullOrBlank() || it.extraFrameBase64List.isNotEmpty()
            }

            // If local daemon isn't running (or binary media like photos/videos/PDFs are attached) and OAuth token exists, stream via CloudCode PA multimodal pipeline
            if ((!localHubReady || hasBinaryAttachments) && token.isNotBlank()) {
                val cloudOk = streamViaCloudCodePa(
                    token = token,
                    projectId = projectId,
                    cleanModel = cleanModel,
                    userPrompt = userPrompt,
                    history = history,
                    systemInstruction = systemInstruction,
                    sessionId = sessionId,
                    thinkingBudget = thinkingBudget,
                    context = context,
                    resolvedAttachments = resolvedAttachments
                )
                if (cloudOk) return@flow
            }

            // Inspect live local daemon catalog & auth status before selecting model key
            var inspection = inspectLocalHubCatalog(resolvedHubUrl)
            if (inspection.candidates.isEmpty()) {
                delay(1200)
                inspection = inspectLocalHubCatalog(resolvedHubUrl)
            }

            val matchingLive = inspection.candidates
                .map { it to scoreCatalogCandidate(it, cleanModel, thinkingBudget) }
                .filter { it.second > Int.MIN_VALUE }
                .sortedByDescending { it.second }
                .map { it.first }

            val fallbackHardcoded = buildDefaultHardcodedCandidates(cleanModel, thinkingBudget)
            val candidateQueue: List<CatalogModelCandidate> = (matchingLive + fallbackHardcoded)
                .distinctBy { it.uniqueKey() }
                .take(5)

            var cascadeCompleted = false
            var abortLocalHubToCloudFallback = false
            var localHubTransientRetries = 0
            var lastDaemonError = ""
            val resolvedContext = context
            val homeRoot = resolvedContext?.let { LocalEnvironmentManager.getHomeDir(it).absolutePath }
                ?: "/data/data/com.termux/files/home"
            val defaultIsolatedProjectsDir = resolvedContext?.let { LocalEnvironmentManager.getIsolatedProjectsDir(it) }
                ?: File(homeRoot, "${JarvisHubConfig.ISOLATED_WORKSPACE_DIR_NAME}/projects").apply { runCatching { mkdirs() } }
            val workspaceDir = ActiveCodeEditorRegistry.currentProjectPath?.takeIf { it.isNotBlank() && File(it).exists() }
                ?: defaultIsolatedProjectsDir.absolutePath
            val workspaceUri = "file://$workspaceDir"
            val activeEditorFile = ActiveCodeEditorRegistry.activeFilePath?.takeIf { it.isNotBlank() && File(it).exists() }

            if (resolvedContext != null) {
                LocalServerManager.provisionEmbeddedMcpConfig(resolvedContext)
            }

            val mcpServersArr = JSONArray().apply {
                put(
                    JSONObject()
                        .put("serverName", "jarvis-android-mcp")
                        .put("serverUrl", JarvisHubConfig.getMcpUrl())
                )
                put(
                    JSONObject()
                        .put("serverName", "jarvis-browser-mcp")
                        .put("serverUrl", JarvisHubConfig.getBrowserMcpUrl())
                )
            }

            for (currentSpec in candidateQueue) {
                if (cascadeCompleted || abortLocalHubToCloudFallback) break
                currentCoroutineContext().ensureActive()

                var cascadeId = sessionCascadeIds.getOrPut(sessionId) { UUID.randomUUID().toString() }
                var retryNextCandidate = false

                fun ensureCascadeStarted(targetCascadeId: String): String {
                    if (startedCascades.contains(targetCascadeId)) return targetCascadeId

                    val startPayloads = listOf(
                        JSONObject().apply {
                            put("cascadeId", targetCascadeId)
                            put("source", 1) // CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT
                            put("workspaceUri", workspaceUri)
                            put("workspaceUris", JSONArray().put(workspaceUri))
                            currentSpec.enumTag?.let { put("requestedModel", it) }
                            put("config", JSONObject().put("mcpServers", mcpServersArr))
                        },
                        JSONObject().apply {
                            put("workspaceUri", workspaceUri)
                            put("config", JSONObject().put("mcpServers", mcpServersArr))
                        },
                        JSONObject().apply {
                            put("workspaceUri", workspaceUri)
                        }
                    )

                    for (startPayload in startPayloads) {
                        val startReq = Request.Builder()
                            .url("$resolvedHubUrl/StartCascade")
                            .post(startPayload.toString().toRequestBody("application/json".toMediaType()))
                            .build()

                        AgyOkHttpClient.client.newCall(startReq).execute().use { resp ->
                            val body = resp.body?.string().orEmpty()
                            when {
                                resp.isSuccessful -> {
                                    val serverCid = runCatching {
                                        val j = JSONObject(body)
                                        j.optString("cascadeId", "").ifBlank { j.optString("cascade_id", "") }
                                    }.getOrDefault("")
                                    val effectiveCid = serverCid.ifBlank { targetCascadeId }
                                    sessionCascadeIds[sessionId] = effectiveCid
                                    startedCascades.add(effectiveCid)
                                    startedCascades.add(targetCascadeId)
                                    return effectiveCid
                                }
                                resp.code == 409 -> {
                                    startedCascades.add(targetCascadeId)
                                    return targetCascadeId
                                }
                                resp.code == 400 -> {
                                    Log.w(TAG, "StartCascade HTTP 400 with extended payload, trying minimal schema: $body")
                                }
                                else -> {
                                    Log.w(TAG, "StartCascade non-200: ${resp.code} -> $body")
                                    return targetCascadeId
                                }
                            }
                        }
                    }
                    return targetCascadeId
                }

                val isFreshLocalCascade = !startedCascades.contains(cascadeId)
                // 1. Ensure persistent Cascade session is started with Workspace + MCP
                cascadeId = ensureCascadeStarted(cascadeId)

                // 2. Send User Message with Full Agentic Planning, AST Workspace, MCP & Subagent Config
                val requestedModelJson = currentSpec.toModelOrAliasJson()
                val planModelEnumValue: Any? = currentSpec.enumTag
                    ?: currentSpec.protoName?.takeIf { it.isNotBlank() && it.startsWith("MODEL_") }
                val priorHistoryBlock = if (isFreshLocalCascade && history.isNotEmpty()) {
                    val cleanPrior = history.filter { !it.isError && it.content.isNotBlank() }
                        .let { list ->
                            val last = list.lastOrNull()
                            if (last != null && last.role.equals("user", true) && last.content.trim() == userPrompt.trim()) {
                                list.dropLast(1)
                            } else list
                        }
                        .takeLast(12)
                    if (cleanPrior.isNotEmpty()) {
                        buildString {
                            appendLine("[RECENT CONVERSATION CONTEXT]")
                            cleanPrior.forEach { msg ->
                                val speaker = if (msg.role.equals("model", true)) "JARVIS" else "USER"
                                appendLine("$speaker: ${com.jarvisai.assistant.model.ChatAttachmentCodec.toPromptReadableText(msg.content).take(600)}")
                            }
                            appendLine("[END CONTEXT — ANSWER THE NEW USER MESSAGE BELOW IN CONTINUATION]")
                        }
                    } else ""
                } else ""
                val hubPromptText = buildString {
                    if (priorHistoryBlock.isNotBlank()) {
                        appendLine(priorHistoryBlock)
                    }
                    append(userPrompt)
                    if (activeEditorFile != null) {
                        append("\n\n[Active Sora IDE File: $activeEditorFile | Workspace: $workspaceDir]")
                    }
                    resolvedAttachments.forEach { att ->
                        appendLine()
                        append("[Attached ${att.kind.name}: ${att.displayName}")
                        att.savedWorkspacePath?.let { append(" | path=$it") }
                        appendLine("]")
                        if (!att.extractedText.isNullOrBlank()) {
                            appendLine(att.extractedText.take(12000))
                        }
                    }
                }.trim()

                val itemsArray = JSONArray().apply {
                    put(JSONObject().put("text", hubPromptText))
                    if (activeEditorFile != null) {
                        put(JSONObject().put("fileUri", "file://$activeEditorFile"))
                    }
                }

                fun buildSendPayload(targetCascadeId: String, tier: Int): JSONObject = JSONObject().apply {
                    put("cascadeId", targetCascadeId)
                    put("items", itemsArray)
                    when (tier) {
                        0 -> {
                            put("workspaceUri", workspaceUri)
                            put("workspaceUris", JSONArray().put(workspaceUri))
                            put("deliveryStrategy", 1) // MESSAGE_DELIVERY_STRATEGY_WHEN_IDLE
                            put("cascadeConfig", JSONObject().apply {
                                put("plannerConfig", JSONObject().apply {
                                    put("requestedModel", requestedModelJson)
                                    if (planModelEnumValue != null) {
                                        put("planModel", planModelEnumValue)
                                    }
                                    put("includeThinking", thinkingBudget > 0)
                                    if (thinkingBudget > 0) {
                                        put("thinkingBudget", thinkingBudget)
                                    }
                                    put("conversational", JSONObject().apply {
                                        put("plannerMode", 1)
                                        put("agenticMode", true)
                                    })
                                    put("toolConfig", JSONObject().apply {
                                        put("runCommand", JSONObject().apply {
                                            put("autoCommandConfig", JSONObject().put("autoExecutionPolicy", 2))
                                        })
                                        put("notifyUser", JSONObject())
                                    })
                                    put("enableSubagents", true)
                                    put("supportsLatexRendering", true)
                                    put("useAiCredits", false)
                                })
                                put("mcpServers", mcpServersArr)
                                put("executorConfig", JSONObject().apply {
                                    put("useCoreDirect", true)
                                    put("workspaceDirectory", workspaceDir)
                                })
                                put("conversationHistoryConfig", JSONObject().put("preserveHistory", true))
                            })
                            put("customAgentSpec", JSONObject().apply {
                                put("builtinAgent", JSONObject().apply {
                                    put("defaultAgent", JSONObject().apply {
                                        put("isGoogle", false)
                                        put("isInteractive", true)
                                    })
                                })
                            })
                        }
                        1 -> {
                            put("blocking", false)
                            put("cascadeConfig", JSONObject().apply {
                                put("plannerConfig", JSONObject().apply {
                                    put("requestedModel", requestedModelJson)
                                    if (planModelEnumValue != null) {
                                        put("planModel", planModelEnumValue)
                                    }
                                    put("includeThinking", thinkingBudget > 0)
                                    if (thinkingBudget > 0) {
                                        put("thinkingBudget", thinkingBudget)
                                    }
                                    put("conversational", JSONObject().apply {
                                        put("plannerMode", 1)
                                        put("agenticMode", true)
                                    })
                                    put("toolConfig", JSONObject().apply {
                                        put("runCommand", JSONObject().apply {
                                            put("autoCommandConfig", JSONObject().put("autoExecutionPolicy", 2))
                                        })
                                        put("notifyUser", JSONObject())
                                    })
                                })
                                put("mcpServers", mcpServersArr)
                            })
                        }
                        else -> {
                            put("cascadeConfig", JSONObject().apply {
                                put("plannerConfig", JSONObject().apply {
                                    put("requestedModel", requestedModelJson)
                                    put("conversational", JSONObject().apply {
                                        put("plannerMode", 1)
                                        put("agenticMode", true)
                                    })
                                })
                            })
                        }
                    }
                }

                var activeCid = cascadeId
                // Snapshot existing step count before sending so multi-turn Cascades never replay prior turn steps
                val baselineStepCount: Int = runCatching {
                    val preReq = Request.Builder()
                        .url("$resolvedHubUrl/GetCascadeTrajectory")
                        .post(JSONObject().put("cascadeId", activeCid).toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    AgyOkHttpClient.client.newCall(preReq).execute().use { preResp ->
                        if (preResp.isSuccessful) {
                            JSONObject(preResp.body?.string().orEmpty())
                                .optJSONObject("trajectory")
                                ?.optJSONArray("steps")
                                ?.length() ?: 0
                        } else 0
                    }
                }.getOrDefault(0)

                var sendSucceeded = false
                for (tier in 0..2) {
                    val sendPayload = buildSendPayload(activeCid, tier)
                    val sendReq = Request.Builder()
                        .url("$resolvedHubUrl/SendUserCascadeMessage")
                        .post(sendPayload.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    var sendResp = AgyOkHttpClient.client.newCall(sendReq).execute()
                    if (!sendResp.isSuccessful && sendResp.code in listOf(404, 410)) {
                        // Re-create Cascade if daemon restarted and lost prior in-memory cascadeId
                        sendResp.close()
                        startedCascades.remove(activeCid)
                        val freshCid = UUID.randomUUID().toString()
                        sessionCascadeIds[sessionId] = freshCid
                        activeCid = ensureCascadeStarted(freshCid)
                        val retryPayload = buildSendPayload(activeCid, tier)
                        val retryReq = Request.Builder()
                            .url("$resolvedHubUrl/SendUserCascadeMessage")
                            .post(retryPayload.toString().toRequestBody("application/json".toMediaType()))
                            .build()
                        sendResp = AgyOkHttpClient.client.newCall(retryReq).execute()
                    }

                    if (sendResp.isSuccessful) {
                        sendResp.close()
                        sendSucceeded = true
                        break
                    }

                    val respCode = sendResp.code
                    val err = sendResp.body?.string().orEmpty()
                    sendResp.close()
                    lastDaemonError = "Antigravity Hub Send Error (HTTP $respCode): $err"
                    Log.w(TAG, "SendUserCascadeMessage tier=$tier failed: $lastDaemonError")
                    if (respCode != 400) {
                        break
                    }
                }

                if (!sendSucceeded) {
                    continue
                }

                // 3. Poll Trajectory for Response (Live Streaming & Adaptive 300s Timeout)
                var turnFinished = false
                val maxIdleSeconds = 300
                val maxTotalSeconds = 600
                val startTimestamp = System.currentTimeMillis()
                var lastActivityTimestamp = System.currentTimeMillis()
                var thoughtStartTimestamp: Long? = null
                var thoughtDurationMs: Long = 0L

                var pollPayload = JSONObject().put("cascadeId", activeCid).toString()
                val accumulatedTools = linkedMapOf<String, com.jarvisai.assistant.model.ToolCall>()
                val plannerThoughtsByStep = linkedMapOf<String, String>()
                val plannerResponsesByStep = linkedMapOf<String, String>()
                var accumulatedThought = ""
                var accumulatedResponse = ""
                var lastObservedSignature = ""
                var unchangedStablePolls = 0

                var turnBaselineStepCount = baselineStepCount
                val autoContinuedAskSteps = mutableSetOf<String>()

                while (!turnFinished) {
                    currentCoroutineContext().ensureActive()
                    delay(450)

                    val now = System.currentTimeMillis()
                    val totalElapsedSeconds = (now - startTimestamp) / 1000
                    val idleElapsedSeconds = (now - lastActivityTimestamp) / 1000

                    if (idleElapsedSeconds >= maxIdleSeconds) {
                        emit(AntigravityStreamEvent.Error("Antigravity Hub inactive: no progress received for ${maxIdleSeconds}s."))
                        cascadeCompleted = true
                        break
                    }
                    if (totalElapsedSeconds >= maxTotalSeconds) {
                        emit(AntigravityStreamEvent.Error("Antigravity Hub task exceeded global limit of ${maxTotalSeconds}s (10m)."))
                        cascadeCompleted = true
                        break
                    }

                    val pollReq = Request.Builder()
                        .url("$resolvedHubUrl/GetCascadeTrajectory")
                        .post(pollPayload.toRequestBody("application/json".toMediaType()))
                        .build()

                    try {
                        AgyOkHttpClient.client.newCall(pollReq).execute().use { pollResp ->
                            if (pollResp.isSuccessful) {
                                val respStr = pollResp.body?.string().orEmpty()
                                val root = JSONObject(respStr)
                                val trajectory = root.optJSONObject("trajectory")
                                val cascadeStatus = listOfNotNull(
                                    trajectory?.optString("status")?.takeIf { it.isNotBlank() },
                                    root.optString("status").takeIf { it.isNotBlank() },
                                    root.optString("cascadeStatus").takeIf { it.isNotBlank() },
                                    trajectory?.optString("cascadeStatus")?.takeIf { it.isNotBlank() },
                                    root.optString("runStatus").takeIf { it.isNotBlank() },
                                    trajectory?.optString("runStatus")?.takeIf { it.isNotBlank() }
                                ).firstOrNull().orEmpty()
                                val trajectoryId = trajectory?.optString("trajectoryId", "") ?: root.optString("trajectoryId", "")
                                activeCascadeId = activeCid
                                AskQuestionSharedState.lastCascadeId = activeCid
                                if (trajectoryId.isNotBlank()) {
                                    activeTrajectoryId = trajectoryId
                                    AskQuestionSharedState.lastTrajectoryId = trajectoryId
                                }
                                val steps = trajectory?.optJSONArray("steps")
                                val stepCount = steps?.length() ?: 0

                                var hasAnyStepInProgress = false
                                var hasCompletedPlannerOrToolStep = false
                                var latestErrorStepIdx = -1
                                var latestErrorMsg = ""
                                var latestRecoveryStepIdx = -1
                                var skipCompletionThisPoll = false

                                if (steps != null && stepCount > turnBaselineStepCount) {
                                    // Determine start index of THIS turn strictly past turnBaselineStepCount (never replay prior turns)
                                    var turnStartIdx = turnBaselineStepCount.coerceAtLeast(0)
                                    for (idx in turnStartIdx until stepCount) {
                                        val sType = steps.optJSONObject(idx)?.optString("type", "").orEmpty()
                                        if (sType == "CORTEX_STEP_TYPE_USER_INPUT" || sType == "TRAJECTORY_STEP_TYPE_USER_INPUT") {
                                            turnStartIdx = idx + 1
                                        }
                                    }

                                    for (i in turnStartIdx until stepCount) {
                                        val step = steps.getJSONObject(i)
                                        val stepType = step.optString("type", "")
                                        val stepStatus = step.optString("status", "")
                                        val reqInteraction = step.optJSONObject("requestedInteraction")
                                        val rc = step.optJSONObject("runCommand")
                                        val cmdStatus = step.optJSONObject("commandStatus")
                                        val viewFile = step.optJSONObject("viewFile") ?: step.optJSONObject("readFile")
                                        val viewOutline = step.optJSONObject("viewFileOutline") ?: step.optJSONObject("viewCodeItem")
                                        val writeFile = step.optJSONObject("writeToFile") ?: step.optJSONObject("writeFile")
                                        val replaceFile = step.optJSONObject("replaceFileContent") ?: step.optJSONObject("multiReplaceFileContent") ?: step.optJSONObject("editFile")
                                        val codeAction = step.optJSONObject("codeAction")
                                        val listDir = step.optJSONObject("listDirectory") ?: step.optJSONObject("listDir") ?: step.optJSONObject("findByName")
                                        val grepSearch = step.optJSONObject("grepSearch")
                                        val webSearch = step.optJSONObject("searchWeb") ?: step.optJSONObject("readUrlContent")
                                        val subagent = step.optJSONObject("subagent") ?: step.optJSONObject("browserSubagent") ?: step.optJSONObject("taskBoundary")
                                        val mcpTool = step.optJSONObject("mcpTool") ?: step.optJSONObject("callMcpTool")
                                        val toolInv = step.optJSONObject("toolInvocation")
                                        val gen = step.optJSONObject("generic")
                                        val meta = step.optJSONObject("metadata")

                                        if (stepType == "CORTEX_STEP_TYPE_USER_INPUT" || stepType == "TRAJECTORY_STEP_TYPE_USER_INPUT") {
                                            continue
                                        }

                                        val isStepDone = stepStatus == "CORTEX_STEP_STATUS_DONE" ||
                                            stepStatus == "TRAJECTORY_STEP_STATUS_COMPLETED" ||
                                            stepStatus == "CORTEX_STEP_STATUS_ERROR" ||
                                            stepStatus == "TRAJECTORY_STEP_STATUS_FAILED" ||
                                            stepStatus == "DONE" ||
                                            stepStatus == "COMPLETED" ||
                                            stepStatus == "SUCCESS" ||
                                            stepStatus == "FAILED" ||
                                            stepStatus == "CANCELLED"
                                        val isStepExplicitlyActive = stepStatus.contains("RUNNING", true) ||
                                            stepStatus.contains("GENERATING", true) ||
                                            stepStatus.contains("PENDING", true) ||
                                            stepStatus.contains("WAITING", true) ||
                                            stepStatus.contains("QUEUED", true) ||
                                            stepStatus.contains("STARTING", true) ||
                                            stepStatus.isBlank()

                                        val toolName = when {
                                            rc != null || stepType == "CORTEX_STEP_TYPE_RUN_COMMAND" || stepType == "TRAJECTORY_STEP_TYPE_TERMINAL_EXEC" -> "run_command"
                                            cmdStatus != null || stepType == "CORTEX_STEP_TYPE_COMMAND_STATUS" -> "command_status"
                                            viewOutline != null || stepType == "CORTEX_STEP_TYPE_VIEW_FILE_OUTLINE" || stepType == "CORTEX_STEP_TYPE_VIEW_CODE_ITEM" -> "ast_outline"
                                            viewFile != null || stepType == "CORTEX_STEP_TYPE_VIEW_FILE" -> "read_file"
                                            writeFile != null || stepType == "CORTEX_STEP_TYPE_WRITE_TO_FILE" -> "write_to_file"
                                            replaceFile != null || codeAction != null || stepType == "CORTEX_STEP_TYPE_REPLACE_FILE_CONTENT" || stepType == "CORTEX_STEP_TYPE_MULTI_REPLACE_FILE_CONTENT" || stepType == "CORTEX_STEP_TYPE_CODE_ACTION" || stepType == "TRAJECTORY_STEP_TYPE_FILE_EDIT" -> "edit_file"
                                            listDir != null || stepType == "CORTEX_STEP_TYPE_LIST_DIRECTORY" || stepType == "CORTEX_STEP_TYPE_FIND_BY_NAME" -> "list_dir"
                                            grepSearch != null || stepType == "CORTEX_STEP_TYPE_GREP_SEARCH" -> "grep_search"
                                            webSearch != null || stepType == "CORTEX_STEP_TYPE_SEARCH_WEB" || stepType == "CORTEX_STEP_TYPE_READ_URL_CONTENT" -> "web_search"
                                            subagent != null || stepType.contains("SUBAGENT", ignoreCase = true) || stepType == "CORTEX_STEP_TYPE_TASK_BOUNDARY" -> "subagent"
                                            mcpTool != null || stepType.contains("MCP", ignoreCase = true) -> mcpTool?.optString("toolName", "mcp_tool").orEmpty().ifBlank { "mcp_tool" }
                                            toolInv != null -> toolInv.optString("toolName", "tool")
                                            gen?.has("CommandLine") == true -> "bash"
                                            gen?.optString("name", "").isNullOrBlank().not() -> gen?.optString("name", "tool") ?: "tool"
                                            meta?.optString("toolSummary", "").isNullOrBlank().not() -> meta?.optString("toolSummary", "tool") ?: "tool"
                                            else -> "tool"
                                        }

                                        val isAskQuestion = toolName == "ask_question" ||
                                            toolName == "ask_user_question" ||
                                            stepType == "CORTEX_STEP_TYPE_ASK_USER_QUESTION" ||
                                            stepType == "CORTEX_STEP_TYPE_ASK_QUESTION" ||
                                            stepType == "TRAJECTORY_STEP_TYPE_ASK_QUESTION" ||
                                            step.has("askQuestion") ||
                                            step.has("askUserQuestion") ||
                                            gen?.has("questions") == true ||
                                            gen?.optJSONObject("args")?.has("questions") == true ||
                                            rc?.has("questions") == true ||
                                            reqInteraction?.has("askQuestion") == true ||
                                            reqInteraction?.has("askUserQuestion") == true ||
                                            reqInteraction?.has("userQuestion") == true

                                        if (isAskQuestion && reqInteraction != null) {
                                            val detectedId = reqInteraction.optString("interactionId", "")
                                                .ifBlank { reqInteraction.optString("id", "") }
                                            if (detectedId.isNotBlank()) {
                                                AskQuestionSharedState.lastInteractionId = detectedId
                                            }
                                            when {
                                                reqInteraction.has("askUserQuestion") -> AskQuestionSharedState.lastInteractionKey = "askUserQuestion"
                                                reqInteraction.has("userQuestion") -> AskQuestionSharedState.lastInteractionKey = "userQuestion"
                                                reqInteraction.has("ask_question") -> AskQuestionSharedState.lastInteractionKey = "ask_question"
                                                else -> AskQuestionSharedState.lastInteractionKey = "askQuestion"
                                            }
                                        }

                                        val isWaiting = stepStatus == "CORTEX_STEP_STATUS_WAITING" ||
                                            stepStatus == "TRAJECTORY_STEP_STATUS_WAITING_APPROVAL" ||
                                            stepStatus == "WAITING" ||
                                            reqInteraction != null ||
                                            stepType == "CORTEX_STEP_TYPE_CONFIRM"

                                        val submittedAskRecord = if (isAskQuestion) {
                                            AskQuestionSharedState.submittedRecords["$activeCid:$i"]
                                                ?: AskQuestionSharedState.submittedRecords["latest:$activeCid"]
                                        } else null

                                        if (isStepExplicitlyActive && !isStepDone && !(isAskQuestion && submittedAskRecord != null)) {
                                            hasAnyStepInProgress = true
                                        }
                                        if (isStepDone || (isAskQuestion && submittedAskRecord != null)) {
                                            hasCompletedPlannerOrToolStep = true
                                        }

                                        if (isWaiting && isAskQuestion) {
                                            lastActivityTimestamp = System.currentTimeMillis()
                                            if (submittedAskRecord == null) {
                                                hasAnyStepInProgress = true
                                            }
                                        } else if (isWaiting) {
                                            lastActivityTimestamp = System.currentTimeMillis()
                                            hasAnyStepInProgress = true
                                            autoResolveOutstandingSteps(activeCid, resolvedHubUrl)
                                            if (trajectoryId.isNotBlank()) {
                                                handleCascadePermissionInteraction(activeCid, i, trajectoryId, resolvedHubUrl)
                                            }
                                        }

                                        if (isStepExplicitlyActive ||
                                            cascadeStatus.contains("RUNNING", true) ||
                                            isWaiting
                                        ) {
                                            lastActivityTimestamp = System.currentTimeMillis()
                                        }

                                        if (stepType == "CORTEX_STEP_TYPE_PLANNER_RESPONSE" || stepType == "TRAJECTORY_STEP_TYPE_MODEL_THINKING" || stepType == "TRAJECTORY_STEP_TYPE_MODEL_RESPONSE") {
                                            val plannerResp = step.optJSONObject("plannerResponse")
                                            val thinkingText = plannerResp?.optString("thinking", "")
                                                ?.ifBlank { step.optString("thinkingTrace", "") }
                                                .orEmpty()
                                            val responseText = plannerResp?.optString("response", "")
                                                ?.ifBlank { step.optString("contentMarkdown", "") }
                                                .orEmpty()

                                            if (thinkingText.isNotBlank()) {
                                                latestRecoveryStepIdx = i
                                                if (thoughtStartTimestamp == null) {
                                                    thoughtStartTimestamp = startTimestamp
                                                }
                                                thoughtDurationMs = (System.currentTimeMillis() - thoughtStartTimestamp!!).coerceAtLeast(100L)
                                                plannerThoughtsByStep["$activeCid:$i"] = thinkingText
                                                val mergedThought = plannerThoughtsByStep.values.joinToString("\n\n")
                                                if (mergedThought != accumulatedThought) {
                                                    accumulatedThought = mergedThought
                                                    lastActivityTimestamp = System.currentTimeMillis()
                                                    emit(AntigravityStreamEvent.ThoughtChunk(accumulatedThought))
                                                }
                                            }

                                            val plannerToolCalls = plannerResp?.optJSONArray("toolCalls")
                                            if (plannerToolCalls != null) {
                                                latestRecoveryStepIdx = i
                                                val hasRealSubsequentSteps = (i + 1 until stepCount).any { laterIdx ->
                                                    val lType = steps.optJSONObject(laterIdx)?.optString("type", "").orEmpty()
                                                    lType != "CORTEX_STEP_TYPE_PLANNER_RESPONSE" &&
                                                        lType != "TRAJECTORY_STEP_TYPE_MODEL_THINKING" &&
                                                        lType != "TRAJECTORY_STEP_TYPE_MODEL_RESPONSE"
                                                }
                                                if (!hasRealSubsequentSteps) {
                                                    for (ti in 0 until plannerToolCalls.length()) {
                                                        val ptc = plannerToolCalls.optJSONObject(ti) ?: continue
                                                        val pName = ptc.optString("name", "tool")
                                                        if (pName.equals("ask_question", ignoreCase = true) ||
                                                            pName.equals("ask_user_question", ignoreCase = true)
                                                        ) {
                                                            continue
                                                        }
                                                        val pArgs = ptc.optString("argumentsJson", ptc.optString("args", ""))
                                                        val pId = "step_${i}_ptc_$ti"
                                                        if (!accumulatedTools.containsKey(pId)) {
                                                            val cleanCmd = parseReadableToolCommand(pArgs, pName)
                                                            val plannedTool = ToolCall(
                                                                id = pId,
                                                                stepIndex = i,
                                                                name = pName,
                                                                command = cleanCmd,
                                                                args = pArgs,
                                                                output = "Queued...",
                                                                status = ToolCallStatus.RUNNING
                                                            )
                                                            accumulatedTools[pId] = plannedTool
                                                            emit(AntigravityStreamEvent.ToolCallUpdate(plannedTool))
                                                        }
                                                    }
                                                }
                                            }

                                            if (responseText.isNotBlank()) {
                                                latestRecoveryStepIdx = i
                                                plannerResponsesByStep["$activeCid:$i"] = responseText
                                                val mergedResponse = plannerResponsesByStep.values.joinToString("\n\n")
                                                if (mergedResponse != accumulatedResponse) {
                                                    accumulatedResponse = mergedResponse
                                                    lastActivityTimestamp = System.currentTimeMillis()
                                                    emit(AntigravityStreamEvent.TextChunk(accumulatedResponse))
                                                }
                                            }
                                        }

                                        val isToolStep = stepType == "CORTEX_STEP_STATUS_TOOL_CALL" ||
                                            stepType == "CORTEX_STEP_TYPE_TOOL_CALL" ||
                                            stepType == "CORTEX_STEP_TYPE_TOOL_RESULT" ||
                                            stepType == "TRAJECTORY_STEP_TYPE_TOOL_CALL" ||
                                            stepType == "TRAJECTORY_STEP_TYPE_TOOL_RESULT" ||
                                            stepType == "TRAJECTORY_STEP_TYPE_FILE_EDIT" ||
                                            stepType == "TRAJECTORY_STEP_TYPE_TERMINAL_EXEC" ||
                                            stepType == "CORTEX_STEP_TYPE_TASK_BOUNDARY" ||
                                            stepType.contains("SUBAGENT", ignoreCase = true) ||
                                            rc != null || cmdStatus != null || viewFile != null || viewOutline != null ||
                                            writeFile != null || replaceFile != null || codeAction != null ||
                                            listDir != null || grepSearch != null || webSearch != null ||
                                            subagent != null || mcpTool != null ||
                                            toolInv != null || gen != null || isAskQuestion

                                        if (isToolStep) {
                                            latestRecoveryStepIdx = i
                                            // Remove earlier planner placeholder (_ptc_) entries once real execution steps start
                                            accumulatedTools.keys.removeAll { it.contains("_ptc_") }
                                            val toolId = "${activeCid}_step_$i"
                                            val rawCmd = rc?.optString("commandLine", "")?.takeIf { it.isNotBlank() }
                                                ?: rc?.optString("command", "")?.takeIf { it.isNotBlank() }
                                                ?: cmdStatus?.optString("commandId", "")?.takeIf { it.isNotBlank() }
                                                ?: viewFile?.optString("absolutePath", "")?.takeIf { it.isNotBlank() }
                                                ?: viewFile?.optString("filePath", "")?.takeIf { it.isNotBlank() }
                                                ?: viewOutline?.optString("filePath", "")?.takeIf { it.isNotBlank() }
                                                ?: writeFile?.optString("targetFile", "")?.takeIf { it.isNotBlank() }
                                                ?: replaceFile?.optString("targetFile", "")?.takeIf { it.isNotBlank() }
                                                ?: replaceFile?.optString("filePath", "")?.takeIf { it.isNotBlank() }
                                                ?: codeAction?.optString("targetFile", "")?.takeIf { it.isNotBlank() }
                                                ?: listDir?.optString("directoryPath", "")?.takeIf { it.isNotBlank() }
                                                ?: listDir?.optString("searchDirectory", "")?.takeIf { it.isNotBlank() }
                                                ?: grepSearch?.optString("query", "")?.takeIf { it.isNotBlank() }
                                                ?: webSearch?.optString("query", "")?.takeIf { it.isNotBlank() }
                                                ?: webSearch?.optString("url", "")?.takeIf { it.isNotBlank() }
                                                ?: subagent?.optString("taskName", "")?.takeIf { it.isNotBlank() }
                                                ?: subagent?.optString("task", "")?.takeIf { it.isNotBlank() }
                                                ?: mcpTool?.optString("argumentsJson", "")?.takeIf { it.isNotBlank() }
                                                ?: toolInv?.optString("argumentsJson", "")?.takeIf { it.isNotBlank() }
                                                ?: gen?.optJSONObject("args")?.optString("CommandLine", "")?.takeIf { it.isNotBlank() }
                                                ?: gen?.optString("command", "")?.takeIf { it.isNotBlank() }
                                                ?: meta?.optString("toolSummary", "")?.takeIf { it.isNotBlank() }
                                                ?: if (isAskQuestion) "Clarifying questions" else "Executing $toolName..."
                                            val cmd = parseReadableToolCommand(rawCmd, toolName)

                                            val patchesArr = step.optJSONArray("filePatches")
                                            val patchDiff = if (patchesArr != null && patchesArr.length() > 0) {
                                                (0 until patchesArr.length()).mapNotNull { pi ->
                                                    patchesArr.optJSONObject(pi)?.optString("unifiedDiff", "")?.takeIf { it.isNotBlank() }
                                                }.joinToString("\n")
                                            } else ""

                                            val out = rc?.optJSONObject("combinedOutput")?.optString("full", "")?.takeIf { it.isNotBlank() }
                                                ?: rc?.optString("output", "")?.takeIf { it.isNotBlank() }
                                                ?: cmdStatus?.optString("output", "")?.takeIf { it.isNotBlank() }
                                                ?: viewFile?.optString("content", "")?.takeIf { it.isNotBlank() }
                                                ?: writeFile?.optString("description", "")?.takeIf { it.isNotBlank() }
                                                ?: replaceFile?.optString("diff", "")?.takeIf { it.isNotBlank() }
                                                ?: codeAction?.optString("diff", "")?.takeIf { it.isNotBlank() }
                                                ?: patchDiff.takeIf { it.isNotBlank() }
                                                ?: listDir?.optString("output", "")?.takeIf { it.isNotBlank() }
                                                ?: grepSearch?.optString("output", "")?.takeIf { it.isNotBlank() }
                                                ?: webSearch?.optString("output", "")?.takeIf { it.isNotBlank() }
                                                ?: toolInv?.optString("resultJson", "")?.takeIf { it.isNotBlank() }
                                                ?: gen?.optString("output", "")?.takeIf { it.isNotBlank() }
                                                ?: ""

                                            val questionnaire = if (isAskQuestion) {
                                                val qArr = gen?.optJSONObject("args")?.optJSONArray("questions")
                                                    ?: gen?.optJSONArray("questions")
                                                    ?: rc?.optJSONArray("questions")
                                                    ?: step.optJSONObject("askQuestion")?.optJSONArray("questions")
                                                    ?: step.optJSONObject("askUserQuestion")?.optJSONArray("questions")
                                                    ?: reqInteraction?.optJSONObject("askQuestion")?.optJSONArray("questions")
                                                    ?: reqInteraction?.optJSONObject("askUserQuestion")?.optJSONArray("questions")
                                                    ?: reqInteraction?.optJSONObject("userQuestion")?.optJSONArray("questions")
                                                val questionsList = mutableListOf<com.jarvisai.assistant.model.QuestionnaireQuestion>()
                                                if (qArr != null) {
                                                    for (qi in 0 until qArr.length()) {
                                                        val qObj = qArr.optJSONObject(qi) ?: continue
                                                        val qText = qObj.optString("question", "")
                                                        val multi = qObj.optBoolean("is_multi_select", qObj.optBoolean("isMultiSelect", false))
                                                        val optsArr = qObj.optJSONArray("options")
                                                        val optsList = mutableListOf<com.jarvisai.assistant.model.QuestionnaireOption>()
                                                        if (optsArr != null) {
                                                            for (oi in 0 until optsArr.length()) {
                                                                com.jarvisai.assistant.model.parseQuestionnaireOptionFromAny(optsArr.opt(oi), oi)?.let {
                                                                    optsList.add(it)
                                                                }
                                                            }
                                                        }
                                                        if (qText.isNotBlank()) {
                                                            val matchedResp = submittedAskRecord?.responses?.find {
                                                                it.question.equals(qText, ignoreCase = true)
                                                            }
                                                            val resolvedSelectedLabels = matchedResp?.selectedOptionIds?.map { selId ->
                                                                optsList.find {
                                                                    it.resolvedOptionId() == selId || it.resolvedDisplayText() == selId
                                                                }?.resolvedDisplayText() ?: selId
                                                            } ?: emptyList()
                                                            questionsList.add(
                                                                com.jarvisai.assistant.model.QuestionnaireQuestion(
                                                                    question = qText,
                                                                    options = optsList,
                                                                    isMultiSelect = multi,
                                                                    selectedOptionIds = resolvedSelectedLabels,
                                                                    customAnswer = matchedResp?.writeInResponse?.trim().orEmpty()
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
                                                isAskQuestion && submittedAskRecord != null -> ToolCallStatus.SUCCESS
                                                isAskQuestion && (isWaiting || isStepExplicitlyActive) -> ToolCallStatus.PENDING_APPROVAL
                                                isWaiting -> ToolCallStatus.RUNNING
                                                stepStatus == "CORTEX_STEP_STATUS_RUNNING" || stepStatus == "TRAJECTORY_STEP_STATUS_RUNNING" || stepStatus == "RUNNING" ->
                                                    ToolCallStatus.RUNNING
                                                stepStatus == "CORTEX_STEP_STATUS_ERROR" || stepStatus == "TRAJECTORY_STEP_STATUS_FAILED" || stepStatus == "FAILED" || (exitCode != null && exitCode != 0) || toolInv?.optBoolean("isError", false) == true ->
                                                    ToolCallStatus.FAILED
                                                out.isNotBlank() || stepStatus == "CORTEX_STEP_STATUS_DONE" || stepStatus == "TRAJECTORY_STEP_STATUS_COMPLETED" || stepStatus == "SUCCESS" ->
                                                    ToolCallStatus.SUCCESS
                                                else -> ToolCallStatus.RUNNING
                                            }

                                            val updatedTool = ToolCall(
                                                id = toolId,
                                                stepIndex = i,
                                                name = if (isAskQuestion) "ask_question" else toolName,
                                                command = cmd,
                                                output = if (isAskQuestion && submittedAskRecord != null && out.isBlank()) {
                                                    submittedAskRecord.readableSummary
                                                } else {
                                                    out
                                                },
                                                status = status,
                                                durationMs = toolInv?.optLong("durationMs", 0L) ?: 0L,
                                                exitCode = exitCode,
                                                questionnaire = questionnaire
                                            )

                                            val previous = accumulatedTools[toolId]
                                            if (previous == null || previous != updatedTool) {
                                                accumulatedTools[toolId] = updatedTool
                                                lastActivityTimestamp = System.currentTimeMillis()
                                                emit(AntigravityStreamEvent.ToolCallUpdate(updatedTool))
                                                if (status == ToolCallStatus.SUCCESS && (toolName == "write_to_file" || toolName == "edit_file") && cmd.startsWith("/")) {
                                                    WorkspaceSyncManager.notifyExternalChange(cmd)
                                                }
                                            }

                                            // Watchdog: if user submitted an answer for an ask_question step and the daemon
                                            // rejected HandleCascadeUserInteraction OR stalled without creating subsequent progress,
                                            // cancel the blocked invocation and seamlessly continue via SendUserCascadeMessage
                                            // (or spin up a fresh cascade if the cancelled cascade ignores new messages).
                                            val askKey = "$activeCid:$i"
                                            val hasLaterProgress = (i + 1 until stepCount).any { laterIdx ->
                                                val lStep = steps.optJSONObject(laterIdx)
                                                val lStatus = lStep?.optString("status", "").orEmpty()
                                                val lType = lStep?.optString("type", "").orEmpty()
                                                val lResp = lStep?.optJSONObject("plannerResponse")?.optString("response", "").orEmpty()
                                                lResp.isNotBlank() ||
                                                    lType == "CORTEX_STEP_TYPE_USER_INPUT" ||
                                                    lStatus.contains("DONE", true) ||
                                                    lStatus.contains("COMPLETED", true) ||
                                                    lStatus.contains("RUNNING", true)
                                            }
                                            if (isAskQuestion && !isStepDone && !hasLaterProgress && submittedAskRecord != null && !autoContinuedAskSteps.contains(askKey)) {
                                                val elapsedSinceSubmit = System.currentTimeMillis() - submittedAskRecord.submittedAtMs
                                                val shouldForceContinue = (!submittedAskRecord.interactionAccepted && elapsedSinceSubmit >= 700L) ||
                                                    (elapsedSinceSubmit >= 2200L)
                                                if (shouldForceContinue) {
                                                    autoContinuedAskSteps.add(askKey)
                                                    Log.w(TAG, "ask_question step $i stalled ${elapsedSinceSubmit}ms after user submit (accepted=${submittedAskRecord.interactionAccepted}); forcing continuation.")
                                                    runCatching {
                                                        val cancelEndpoint = if (resolvedHubUrl.endsWith("LanguageServerService")) {
                                                            "$resolvedHubUrl/CancelCascadeInvocation"
                                                        } else {
                                                            "$resolvedHubUrl/exa.language_server_pb.LanguageServerService/CancelCascadeInvocation"
                                                        }
                                                        val cancelReq = Request.Builder()
                                                            .url(cancelEndpoint)
                                                            .post(JSONObject().put("cascadeId", activeCid).put("cascade_id", activeCid).toString().toRequestBody("application/json".toMediaType()))
                                                            .build()
                                                        AgyOkHttpClient.client.newCall(cancelReq).execute().close()
                                                    }
                                                    // Wait briefly for daemon to transition out of WAITING
                                                    for (cancelPoll in 0..3) {
                                                        delay(300)
                                                        val checkIdle = runCatching {
                                                            val chkReq = Request.Builder()
                                                                .url("$resolvedHubUrl/GetCascadeTrajectory")
                                                                .post(JSONObject().put("cascadeId", activeCid).toString().toRequestBody("application/json".toMediaType()))
                                                                .build()
                                                            AgyOkHttpClient.client.newCall(chkReq).execute().use { r ->
                                                                if (r.isSuccessful) {
                                                                    val s = JSONObject(r.body?.string().orEmpty())
                                                                        .optJSONObject("trajectory")
                                                                        ?.optString("status", "").orEmpty()
                                                                    !s.contains("WAITING", true) && !s.contains("RUNNING", true)
                                                                } else false
                                                            }
                                                        }.getOrDefault(false)
                                                        if (checkIdle) break
                                                    }

                                                    val continuePrompt = buildString {
                                                        appendLine("[USER ANSWER TO ASK_QUESTION]")
                                                        appendLine(submittedAskRecord.readableSummary)
                                                        appendLine()
                                                        append("Proceed immediately and execute the user's request based on this answer. Do not ask this question again.")
                                                    }

                                                    fun buildContSendPayload(targetCid: String, promptText: String, tier: Int): JSONObject = JSONObject().apply {
                                                        put("cascadeId", targetCid)
                                                        put("items", JSONArray().put(JSONObject().put("text", promptText)))
                                                        if (tier == 0) {
                                                            put("workspaceUri", workspaceUri)
                                                            put("workspaceUris", JSONArray().put(workspaceUri))
                                                            put("deliveryStrategy", 1)
                                                        }
                                                        put("cascadeConfig", JSONObject().apply {
                                                            put("plannerConfig", JSONObject().apply {
                                                                put("requestedModel", requestedModelJson)
                                                                if (planModelEnumValue != null) put("planModel", planModelEnumValue)
                                                                put("includeThinking", thinkingBudget > 0)
                                                                if (thinkingBudget > 0) put("thinkingBudget", thinkingBudget)
                                                                put("conversational", JSONObject().put("plannerMode", 1).put("agenticMode", true))
                                                                put("toolConfig", JSONObject().apply {
                                                                    put("runCommand", JSONObject().put("autoCommandConfig", JSONObject().put("autoExecutionPolicy", 2)))
                                                                    put("notifyUser", JSONObject())
                                                                })
                                                            })
                                                            put("mcpServers", mcpServersArr)
                                                        })
                                                    }

                                                    var contSent = false
                                                    for (tier in 0..1) {
                                                        val contReq = Request.Builder()
                                                            .url("$resolvedHubUrl/SendUserCascadeMessage")
                                                            .post(buildContSendPayload(activeCid, continuePrompt, tier).toString().toRequestBody("application/json".toMediaType()))
                                                            .build()
                                                        runCatching {
                                                            AgyOkHttpClient.client.newCall(contReq).execute().use { contResp ->
                                                                contSent = contResp.isSuccessful
                                                                if (!contResp.isSuccessful) {
                                                                    Log.w(TAG, "Auto-continuation SendUserCascadeMessage tier=$tier HTTP ${contResp.code}: ${contResp.body?.string()}")
                                                                }
                                                            }
                                                        }
                                                        if (contSent) break
                                                    }

                                                    var sameCascadeAcceptedNewStep = false
                                                    if (contSent) {
                                                        for (chk in 0..3) {
                                                            delay(350)
                                                            val grew = runCatching {
                                                                val chkReq = Request.Builder()
                                                                    .url("$resolvedHubUrl/GetCascadeTrajectory")
                                                                    .post(JSONObject().put("cascadeId", activeCid).toString().toRequestBody("application/json".toMediaType()))
                                                                    .build()
                                                                AgyOkHttpClient.client.newCall(chkReq).execute().use { r ->
                                                                    if (r.isSuccessful) {
                                                                        val j = JSONObject(r.body?.string().orEmpty())
                                                                        val traj = j.optJSONObject("trajectory")
                                                                        val newLen = traj?.optJSONArray("steps")?.length() ?: 0
                                                                        val newStatus = traj?.optString("status", "").orEmpty()
                                                                        newLen > stepCount || newStatus.contains("RUNNING", true)
                                                                    } else false
                                                                }
                                                            }.getOrDefault(false)
                                                            if (grew) {
                                                                sameCascadeAcceptedNewStep = true
                                                                break
                                                            }
                                                        }
                                                    }

                                                    if (sameCascadeAcceptedNewStep) {
                                                        turnBaselineStepCount = stepCount
                                                        lastActivityTimestamp = System.currentTimeMillis()
                                                        unchangedStablePolls = 0
                                                        skipCompletionThisPoll = true
                                                        break
                                                    } else {
                                                        // Fallback: Cancelled cascade refused or ignored continuation — spin up a fresh cascade seamlessly!
                                                        Log.w(TAG, "Cancelled cascade $activeCid did not advance; launching fresh continuation cascade.")
                                                        startedCascades.remove(activeCid)
                                                        val freshCid = ensureCascadeStarted(UUID.randomUUID().toString())
                                                        val freshPrompt = buildString {
                                                            appendLine("[CONTINUATION FROM PREVIOUS REQUEST]")
                                                            appendLine("Original User Request: $userPrompt")
                                                            appendLine()
                                                            appendLine("[USER ANSWER TO CLARIFYING QUESTION]")
                                                            appendLine(submittedAskRecord.readableSummary)
                                                            appendLine()
                                                            append("Execute the user's request right away using the answers above. Do not ask the same clarifying question again.")
                                                        }
                                                        var freshSent = false
                                                        for (tier in 0..1) {
                                                            val freshReq = Request.Builder()
                                                                .url("$resolvedHubUrl/SendUserCascadeMessage")
                                                                .post(buildContSendPayload(freshCid, freshPrompt, tier).toString().toRequestBody("application/json".toMediaType()))
                                                                .build()
                                                            runCatching {
                                                                AgyOkHttpClient.client.newCall(freshReq).execute().use { fResp ->
                                                                    freshSent = fResp.isSuccessful
                                                                }
                                                            }
                                                            if (freshSent) break
                                                        }
                                                        if (freshSent) {
                                                            activeCid = freshCid
                                                            activeCascadeId = freshCid
                                                            sessionCascadeIds[sessionId] = freshCid
                                                            pollPayload = JSONObject().put("cascadeId", freshCid).toString()
                                                            turnBaselineStepCount = 0
                                                            lastActivityTimestamp = System.currentTimeMillis()
                                                            unchangedStablePolls = 0
                                                            skipCompletionThisPoll = true
                                                            break
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (stepType == "CORTEX_STEP_TYPE_ERROR_MESSAGE") {
                                            val errWrapper = step.optJSONObject("errorMessage")
                                            val errObj = errWrapper?.optJSONObject("error")
                                            val shortErr = errObj?.optString("shortError", "")
                                                ?.ifBlank { errObj.optString("userErrorMessage", "") }
                                                ?.ifBlank { errObj.optString("message", "") }
                                                ?.ifBlank { errWrapper?.optString("message", "") ?: "" }
                                                .orEmpty()
                                                .ifBlank { "Antigravity execution error" }
                                            lastDaemonError = shortErr
                                            latestErrorStepIdx = i
                                            latestErrorMsg = shortErr
                                        }
                                    }
                                }

                                val signature = "$stepCount|${accumulatedResponse.length}|${accumulatedThought.length}|${accumulatedTools.size}|$cascadeStatus|$hasAnyStepInProgress|$latestErrorStepIdx"
                                if (signature == lastObservedSignature) {
                                    unchangedStablePolls++
                                } else {
                                    lastObservedSignature = signature
                                    unchangedStablePolls = 0
                                }

                                val explicitIdle = cascadeStatus == "CASCADE_RUN_STATUS_IDLE" ||
                                    cascadeStatus == "IDLE" ||
                                    cascadeStatus == "CASCADE_RUN_STATUS_DONE" ||
                                    cascadeStatus == "DONE" ||
                                    cascadeStatus == "COMPLETED"
                                val explicitRunning = cascadeStatus.contains("RUNNING", true) ||
                                    cascadeStatus.contains("GENERATING", true) ||
                                    cascadeStatus.contains("PENDING", true) ||
                                    cascadeStatus.contains("ACTIVE", true) ||
                                    cascadeStatus.contains("WAITING", true)

                                val hasUnrecoveredError = latestErrorStepIdx >= 0 && latestErrorStepIdx > latestRecoveryStepIdx
                                if (hasUnrecoveredError && (!explicitRunning || unchangedStablePolls >= 12)) {
                                    // Reset poisoned cascadeId so subsequent turns/retries never stick to a broken cascade
                                    startedCascades.remove(activeCid)
                                    sessionCascadeIds.remove(sessionId)

                                    val shortErr = latestErrorMsg
                                    val isModelOrCapacityErr = shortErr.contains("503", ignoreCase = true) ||
                                        shortErr.contains("404", ignoreCase = true) ||
                                        shortErr.contains("NOT_FOUND", ignoreCase = true) ||
                                        shortErr.contains("Requested entity was not found", ignoreCase = true) ||
                                        shortErr.contains("403", ignoreCase = true) ||
                                        shortErr.contains("PERMISSION_DENIED", ignoreCase = true) ||
                                        shortErr.contains("UNAUTHENTICATED", ignoreCase = true) ||
                                        shortErr.contains("INVALID_ARGUMENT", ignoreCase = true) ||
                                        shortErr.contains("No capacity available", ignoreCase = true) ||
                                        shortErr.contains("UNAVAILABLE", ignoreCase = true) ||
                                        shortErr.contains("unknown model key", ignoreCase = true) ||
                                        shortErr.contains("model not found", ignoreCase = true) ||
                                        shortErr.contains("failed to construct executor", ignoreCase = true)

                                    val isTransientNetworkErr = shortErr.contains("EOF", ignoreCase = true) ||
                                        shortErr.contains("request failed", ignoreCase = true) ||
                                        shortErr.contains("API error (attempt", ignoreCase = true) ||
                                        shortErr.contains("connection reset", ignoreCase = true) ||
                                        shortErr.contains("broken pipe", ignoreCase = true) ||
                                        shortErr.contains("timeout", ignoreCase = true) ||
                                        shortErr.contains("timed out", ignoreCase = true) ||
                                        shortErr.contains("context deadline", ignoreCase = true) ||
                                        shortErr.contains("TLS", ignoreCase = true) ||
                                        shortErr.contains("http2", ignoreCase = true) ||
                                        shortErr.contains("stream error", ignoreCase = true) ||
                                        shortErr.contains("429", ignoreCase = true) ||
                                        shortErr.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                                        shortErr.contains("500", ignoreCase = true) ||
                                        shortErr.contains("502", ignoreCase = true) ||
                                        shortErr.contains("504", ignoreCase = true) ||
                                        shortErr.contains("INTERNAL", ignoreCase = true)

                                    if (isTransientNetworkErr) {
                                        if (localHubTransientRetries < 1) {
                                            localHubTransientRetries++
                                            Log.w(TAG, "Local daemon hit transient EOF/network error (retrying fresh cascade): $shortErr")
                                            delay(350)
                                            retryNextCandidate = true
                                            turnFinished = true
                                            break
                                        } else if (token.isNotBlank()) {
                                            Log.w(TAG, "Local daemon repeated EOF/network error; failing over to CloudCode PA: $shortErr")
                                            abortLocalHubToCloudFallback = true
                                            turnFinished = true
                                            break
                                        } else {
                                            retryNextCandidate = true
                                            turnFinished = true
                                            break
                                        }
                                    } else if (isModelOrCapacityErr) {
                                        Log.w(TAG, "Local daemon rejected ${currentSpec.protoName ?: currentSpec.label}: $shortErr")
                                        retryNextCandidate = true
                                        turnFinished = true
                                        break
                                    } else if (token.isNotBlank() && accumulatedResponse.isBlank() && accumulatedTools.isEmpty()) {
                                        Log.w(TAG, "Local daemon failed without output ($shortErr); attempting CloudCode PA failover.")
                                        abortLocalHubToCloudFallback = true
                                        turnFinished = true
                                        break
                                    } else {
                                        emit(AntigravityStreamEvent.Error(shortErr.ifBlank { "Antigravity error" }))
                                        turnFinished = true
                                        cascadeCompleted = true
                                        break
                                    }
                                }
                                val hasRunningTools = accumulatedTools.entries.any { (k, it) ->
                                    !k.contains("_ptc_") && (it.status == ToolCallStatus.RUNNING || it.status == ToolCallStatus.PENDING_APPROVAL)
                                }
                                val hasContent = accumulatedResponse.isNotBlank() || accumulatedTools.isNotEmpty()
                                val canCompleteOnIdle = explicitIdle && !hasAnyStepInProgress && unchangedStablePolls >= 1
                                val canCompleteOnBlankStatus = cascadeStatus.isBlank() &&
                                    !hasAnyStepInProgress &&
                                    hasCompletedPlannerOrToolStep &&
                                    unchangedStablePolls >= 3

                                if (!skipCompletionThisPoll && !explicitRunning && !hasRunningTools && hasContent && (canCompleteOnIdle || canCompleteOnBlankStatus)) {
                                    accumulatedTools.keys.removeAll { it.contains("_ptc_") }
                                    val finalizedTools = accumulatedTools.values.toList()
                                    val trajUsage = trajectory?.optJSONObject("usageMetadata")
                                        ?: root.optJSONObject("usageMetadata")
                                        ?: trajectory?.optJSONObject("tokenUsage")
                                    val reportedPrompt = trajUsage?.optInt("promptTokenCount", trajUsage.optInt("inputTokens", 0)) ?: 0
                                    val reportedOutput = trajUsage?.optInt("candidatesTokenCount", trajUsage.optInt("outputTokens", 0)) ?: 0
                                    val reportedCached = trajUsage?.optInt("cachedContentTokenCount", trajUsage.optInt("cachedTokens", 0)) ?: 0
                                    val reportedTotal = trajUsage?.optInt("totalTokenCount", trajUsage.optInt("totalTokens", 0)) ?: 0

                                    val toolContextChars = finalizedTools.sumOf { it.command.length + it.output.length }
                                    val historyChars = history.sumOf { it.content.length } + systemInstruction.length + userPrompt.length
                                    val estPrompt = ((historyChars + toolContextChars) / 4).coerceAtLeast(1)
                                    val estOutput = ((accumulatedResponse.length + accumulatedThought.length) / 4).coerceAtLeast(1)
                                    val finalPrompt = if (reportedPrompt > 0) reportedPrompt else estPrompt
                                    val finalOutput = if (reportedOutput > 0) reportedOutput else estOutput
                                    val finalTotal = if (reportedTotal > 0) reportedTotal else (finalPrompt + finalOutput)
                                    emit(
                                        AntigravityStreamEvent.Completed(
                                            totalTokens = finalTotal,
                                            fullText = accumulatedResponse,
                                            fullThought = accumulatedThought,
                                            thoughtDurationMs = thoughtDurationMs.takeIf { it > 0L }
                                                ?: (System.currentTimeMillis() - startTimestamp),
                                            modelUsed = cleanModel,
                                            toolCalls = finalizedTools,
                                            promptTokens = finalPrompt,
                                            outputTokens = finalOutput,
                                            cachedTokens = reportedCached
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

                if (abortLocalHubToCloudFallback) {
                    break
                }

                if (retryNextCandidate) {
                    continue
                }

                if (!turnFinished && !cascadeCompleted) {
                    emit(AntigravityStreamEvent.Error("Antigravity Hub request timed out after ${maxTotalSeconds}s."))
                    cascadeCompleted = true
                    break
                }
            }

            // 4. Automatic Direct CloudCode PA Failover when local daemon lacks model key or OAuth catalog
            if (!cascadeCompleted) {
                if (token.isNotBlank()) {
                    Log.i(TAG, "Local daemon did not resolve model ($lastDaemonError); activating CloudCode PA direct stream for $cleanModel.")
                    val cloudOk = streamViaCloudCodePa(
                        token = token,
                        projectId = projectId,
                        cleanModel = cleanModel,
                        userPrompt = userPrompt,
                        history = history,
                        systemInstruction = systemInstruction,
                        sessionId = sessionId,
                        thinkingBudget = thinkingBudget,
                        context = context,
                        resolvedAttachments = resolvedAttachments
                    )
                    if (cloudOk) return@flow
                }

                val authHint = if (token.isBlank() && inspection.hasValidAuth == false) {
                    "Local Antigravity Hub is not signed in (catalog empty). Tap Menu -> '☁️ Antigravity OAuth' (or type /antigravity) to sign in with Google."
                } else if (token.isBlank()) {
                    "${lastDaemonError.ifBlank { "Model catalog missing on local Antigravity Hub" }}. Sign in via Menu -> '☁️ Antigravity OAuth' (/antigravity) so Jarvis can hydrate models or stream via Google DeepMind CloudCode."
                } else {
                    lastDaemonError.ifBlank { "Selected model '$cleanModel' failed on both local Hub and CloudCode PA. Please re-authenticate via /antigravity." }
                }
                emit(AntigravityStreamEvent.Error(authHint))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Antigravity Local Hub Exception: ${e.message}", e)
            if (token.isNotBlank()) {
                val cloudOk = streamViaCloudCodePa(
                    token = token,
                    projectId = projectId,
                    cleanModel = cleanModel,
                    userPrompt = userPrompt,
                    history = history,
                    systemInstruction = systemInstruction,
                    sessionId = sessionId,
                    thinkingBudget = thinkingBudget,
                    context = context,
                    resolvedAttachments = resolvedAttachments
                )
                if (cloudOk) return@flow
            }
            emit(AntigravityStreamEvent.Error("Antigravity Hub unreachable at 127.0.0.1:$hubPort. Please verify Linux Engine is running or sign in via /antigravity."))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun generateSingleStepDecision(
        token: String,
        projectId: String = "",
        modelId: String = "gemini-3.8-flash",
        systemInstruction: String,
        userPrompt: String,
        imageBase64: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) {
            return@withContext Result.failure(Exception("Antigravity OAuth token is missing."))
        }
        val cleanModel = sanitizeBrokenModelId(modelId.removePrefix("agy:").removePrefix("models/"))
            .ifBlank { "gemini-3.8-flash" }
        var activeProject = projectId.trim()
        if (activeProject.isBlank()) {
            activeProject = loadCodeAssist(cleanToken).getOrNull()?.first.orEmpty()
        }

        val userParts = JSONArray().apply {
            if (!imageBase64.isNullOrBlank()) {
                put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject()
                            .put("mimeType", "image/jpeg")
                            .put("data", imageBase64)
                    )
                )
            }
            put(JSONObject().put("text", userPrompt))
        }

        val candidateModels = listOf(cleanModel, "gemini-3.8-flash")
            .distinct()
            .take(2)
        val endpoints = listOf(ENDPOINT_PROD, ENDPOINT_DAILY)
        var lastError = "Antigravity CloudCode step decision failed."

        for (candidate in candidateModels) {
            for (endpoint in endpoints) {
                var attemptProject = activeProject
                for (projectRetry in 0..1) {
                    val lowerCand = candidate.lowercase()
                    val genCfg = JSONObject()
                        .put("temperature", 0.0)
                        .put("maxOutputTokens", 384)
                    if (!lowerCand.contains("flash-lite")) {
                        if (lowerCand.contains("gemini-3") || lowerCand.contains("gemini-4") ||
                            lowerCand.contains("hydrogen") || lowerCand.contains("argon")
                        ) {
                            genCfg.put("thinkingConfig", JSONObject().put("thinkingLevel", "minimal"))
                        } else if (lowerCand.contains("gemini-2.5")) {
                            genCfg.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                        }
                    }
                    val innerRequest = JSONObject().apply {
                        put(
                            "contents",
                            JSONArray().put(
                                JSONObject()
                                    .put("role", "user")
                                    .put("parts", userParts)
                            )
                        )
                        if (systemInstruction.isNotBlank()) {
                            put(
                                "systemInstruction",
                                JSONObject()
                                    .put("role", "user")
                                    .put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
                            )
                        }
                        put("generationConfig", genCfg)
                    }
                    val envelope = JSONObject().apply {
                        if (attemptProject.isNotBlank()) {
                            put("project", attemptProject)
                        }
                        put("model", candidate)
                        put("userAgent", "antigravity")
                        put("requestId", "taskstep-${UUID.randomUUID()}")
                        put("request", innerRequest)
                    }
                    val headers = getBaseHeaders(cleanToken, candidate).newBuilder()
                        .add("Accept", "text/event-stream")
                        .build()
                    val req = Request.Builder()
                        .url("$endpoint/v1internal:streamGenerateContent?alt=sse")
                        .headers(headers)
                        .post(envelope.toString().toRequestBody(jsonMediaType))
                        .build()
                    try {
                        val sb = StringBuilder()
                        var retryWithoutProj = false
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) {
                                val errBody = resp.body?.string().orEmpty()
                                lastError = "CloudCode HTTP ${resp.code}: ${errBody.take(180)}"
                                if ((resp.code == 400 || resp.code == 403 || resp.code == 404) &&
                                    attemptProject.isNotBlank() && projectRetry == 0
                                ) {
                                    attemptProject = ""
                                    retryWithoutProj = true
                                }
                            } else {
                                resp.body?.byteStream()?.bufferedReader()?.use { reader ->
                                    var line: String?
                                    while (reader.readLine().also { line = it } != null) {
                                        val trimmed = line?.trim().orEmpty()
                                        if (!trimmed.startsWith("data:")) continue
                                        val dataPayload = trimmed.removePrefix("data:").trim()
                                        if (dataPayload.isEmpty() || dataPayload == "[DONE]") continue
                                        runCatching {
                                            val root = JSONObject(dataPayload)
                                            val payloadObj = root.optJSONObject("response") ?: root
                                            val parts = payloadObj.optJSONArray("candidates")
                                                ?.optJSONObject(0)
                                                ?.optJSONObject("content")
                                                ?.optJSONArray("parts")
                                            if (parts != null) {
                                                for (pi in 0 until parts.length()) {
                                                    val part = parts.optJSONObject(pi) ?: continue
                                                    if (!part.optBoolean("thought", false)) {
                                                        val txt = part.optString("text", "")
                                                        if (txt.isNotEmpty()) sb.append(txt)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (sb.toString().trim().isNotBlank()) {
                            return@withContext Result.success(sb.toString().trim())
                        }
                        if (!retryWithoutProj) break
                    } catch (e: Exception) {
                        lastError = e.localizedMessage ?: "CloudCode step request error"
                        break
                    }
                }
            }
        }
        Result.failure(Exception(lastError))
    }

    private fun parseReadableToolCommand(rawInput: String, fallbackName: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.startsWith("{")) {
            val parsed = runCatching {
                val obj = JSONObject(trimmed)
                val keys = listOf(
                    "CommandLine", "commandLine", "command", "cmd",
                    "TargetFile", "targetFile", "AbsolutePath", "absolutePath",
                    "filePath", "path", "DirectoryPath", "directoryPath",
                    "Query", "query", "SearchTerm", "Pattern", "pattern",
                    "Url", "url", "toolSummary"
                )
                keys.firstNotNullOfOrNull { k ->
                    obj.optString(k, "").trim().takeIf { it.isNotBlank() }
                }
            }.getOrNull()
            if (!parsed.isNullOrBlank()) return parsed.take(140)
        }
        return trimmed.takeIf { it.isNotBlank() && !it.startsWith("{") }?.take(140)
            ?: fallbackName.replace('_', ' ')
    }
}
