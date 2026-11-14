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
        cancelled: Boolean = false
    ) {
        val resolvedCloud = AntigravityToolSuite.resolvePendingQuestion(stepIndex, responses, cancelled)
        if (resolvedCloud && cascadeId.isBlank()) {
            return
        }
        if (cascadeId.isBlank()) return
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

    private data class CatalogModelCandidate(
        val enumTag: Int? = null,
        val protoName: String? = null,
        val aliasTag: Int? = null,
        val modelUid: String? = null,
        val label: String = "",
        val modelSlug: String = "",
        val isRecommended: Boolean = false
    ) {
        fun uniqueKey(): String = listOfNotNull(
            modelUid?.takeIf { it.isNotBlank() },
            protoName?.takeIf { it.isNotBlank() },
            enumTag?.toString(),
            aliasTag?.let { "alias:$it" }
        ).firstOrNull() ?: label

        fun toModelOrAliasJson(): JSONObject = JSONObject().apply {
            when {
                !modelUid.isNullOrBlank() -> put("modelUid", modelUid)
                aliasTag != null -> put("alias", aliasTag)
                enumTag != null -> put("model", enumTag)
                !protoName.isNullOrBlank() -> put("model", protoName)
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
        val modelUid = moa?.optString("modelUid", "")?.takeIf { it.isNotBlank() }
            ?: moa?.optString("model_uid", "")?.takeIf { it.isNotBlank() }
            ?: obj.optString("modelUid", "").takeIf { it.isNotBlank() }
            ?: obj.optString("model_uid", "").takeIf { it.isNotBlank() }
            ?: obj.optString("id", "").takeIf { it.isNotBlank() && !it.startsWith("MODEL_") }

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

        if (enumTag == null && protoName.isNullOrBlank() && aliasTag == null && modelUid.isNullOrBlank()) {
            return null
        }

        return CatalogModelCandidate(
            enumTag = enumTag,
            protoName = protoName,
            aliasTag = aliasTag,
            modelUid = modelUid,
            label = label.ifBlank { protoName ?: modelUid ?: "model" },
            modelSlug = label.lowercase().replace(" ", "-"),
            isRecommended = obj.optBoolean("isRecommended", false) || obj.optBoolean("is_recommended", false)
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
        return when {
            req.contains("claude") || req.contains("sonnet") ->
                hay.contains("claude") || hay.contains("sonnet") || enumTag == 1035 || protoName == "MODEL_PLACEHOLDER_M35"
            req.contains("carbon") ->
                hay.contains("carbon") || enumTag == 1670 || protoName == "MODEL_PLACEHOLDER_M670"
            req.contains("argon") ->
                hay.contains("argon") || enumTag == 1421 || protoName == "MODEL_PLACEHOLDER_M421"
            req.contains("oss") ->
                hay.contains("oss")
            req.contains("3.8") && req.contains("flash") ->
                (hay.contains("3.8") && hay.contains("flash")) ||
                    enumTag in listOf(1318, 1319, 1320) ||
                    protoName in listOf("MODEL_PLACEHOLDER_M318", "MODEL_PLACEHOLDER_M319", "MODEL_PLACEHOLDER_M320")
            else -> hay.contains(req)
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
        val wantsClaude = cleanModel.contains("claude", ignoreCase = true) || cleanModel.contains("sonnet", ignoreCase = true)
        val wantsCarbon = cleanModel.contains("carbon", ignoreCase = true)
        val wantsArgon = cleanModel.contains("argon", ignoreCase = true)
        val wantsFlash38 = cleanModel.contains("3.8", ignoreCase = true) || cleanModel.contains("flash", ignoreCase = true)

        when {
            wantsClaude -> {
                if (hay.contains("claude") || hay.contains("sonnet")) score += 100
                if (hay.contains("5.5") || hay.contains("4.5")) score += 20
                if (candidate.enumTag == 1035 || candidate.protoName == "MODEL_PLACEHOLDER_M35") score += 40
            }
            wantsCarbon -> {
                if (hay.contains("carbon")) score += 100
                if (hay.contains("ultra") || hay.contains("pro")) score += 30
                if (candidate.enumTag == 1670 || candidate.protoName == "MODEL_PLACEHOLDER_M670") score += 40
            }
            wantsArgon -> {
                if (hay.contains("argon")) score += 100
                if (hay.contains("gemini") && hay.contains("pro")) score += 25
                if (candidate.enumTag == 1421 || candidate.protoName == "MODEL_PLACEHOLDER_M421") score += 40
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
            cleanModel.contains("claude", ignoreCase = true) || cleanModel.contains("sonnet", ignoreCase = true) -> listOf(
                CatalogModelCandidate(1035, "MODEL_PLACEHOLDER_M35", label = "Claude Sonnet 5.5", modelSlug = "claude-sonnet-5-5-max")
            )
            cleanModel.contains("carbon", ignoreCase = true) -> listOf(
                CatalogModelCandidate(1670, "MODEL_PLACEHOLDER_M670", label = "Gemini 4 Carbon", modelSlug = "gemini-4-carbon-medium")
            )
            cleanModel.contains("argon", ignoreCase = true) -> listOf(
                CatalogModelCandidate(1421, "MODEL_PLACEHOLDER_M421", label = "Gemini 4 Argon", modelSlug = "gemini-4-argon-medium")
            )
            thinkingBudget in 1001..4096 -> listOf(
                CatalogModelCandidate(1319, "MODEL_PLACEHOLDER_M319", label = "Gemini 3.8 Flash Medium", modelSlug = "gemini-3.8-flash-medium"),
                CatalogModelCandidate(1318, "MODEL_PLACEHOLDER_M318", label = "Gemini 3.8 Flash High", modelSlug = "gemini-3.8-flash-high")
            )
            thinkingBudget <= 1000 -> listOf(
                CatalogModelCandidate(1320, "MODEL_PLACEHOLDER_M320", label = "Gemini 3.8 Flash Low", modelSlug = "gemini-3.8-flash-low"),
                CatalogModelCandidate(1318, "MODEL_PLACEHOLDER_M318", label = "Gemini 3.8 Flash High", modelSlug = "gemini-3.8-flash-high")
            )
            else -> listOf(
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
        if (effectiveProject.isBlank()) {
            effectiveProject = loadCodeAssist(token).getOrNull()?.first.orEmpty()
        }

        // Strict Model Lock: ONLY execute the user's selected model (or its direct CloudCode alias of the EXACT same model).
        val availableModels = fetchAvailableModels(token, effectiveProject).getOrDefault(emptyList())
        val orderedCloudModels = buildList {
            add(cleanModel)
            availableModels
                .filter { matchesRequestedModelFamily("${it.id} ${it.displayName}", cleanModel) }
                .sortedByDescending { if (it.id.equals(cleanModel, ignoreCase = true)) 200 else 100 }
                .forEach { add(it.id) }
        }.distinct()

        val cliRuntimeContext = if (context != null) {
            val homePath = LocalEnvironmentManager.getHomeDir(context).absolutePath
            val activeProject = ActiveCodeEditorRegistry.currentProjectPath?.takeIf { it.isNotBlank() } ?: "$homePath/projects"
            val activeFile = ActiveCodeEditorRegistry.activeFilePath?.takeIf { it.isNotBlank() }
            val envInstalled = LocalEnvironmentManager.isEnvironmentInstalled(context)
            val termuxBridge = JarvisTermuxBridge(context)
            val extTermuxInstalled = termuxBridge.isTermuxInstalled()
            val extTermuxPerm = if (extTermuxInstalled) termuxBridge.hasRunCommandPermission() else false
            buildString {
                appendLine(systemInstruction)
                appendLine()
                appendLine("=== ANTIGRAVITY FULL-POWER AGENT RUNTIME ===")
                appendLine("- Built-in Jarvis Linux Engine installed: $envInstalled (Home: $homePath)")
                appendLine("- Active IDE Workspace Root: $activeProject")
                if (activeFile != null) {
                    appendLine("- Currently Open IDE Editor File: $activeFile (dirty=${ActiveCodeEditorRegistry.isDirty})")
                }
                appendLine("- External com.termux app installed: $extTermuxInstalled, RUN_COMMAND permission granted: $extTermuxPerm")
                appendLine("- Embedded MCP Bridges Active on 127.0.0.1:8765 (Android OS, Browser CDP, Terminal PTY, 5-Tier Memory, Google Flow).")
                appendLine("- Full Tool Suite: `run_command` (live streaming), `read_file`, `write_to_file`, `replace_file_content`, `multi_replace_file_content`, `list_dir`, `grep_search`, `find_by_name`, `read_active_editor`, `open_file_in_ide`, `ask_question`, `web_search`, `fetch_url`, `browser_mcp_tool`, `android_screen_control`, `jarvis_memory`, `spawn_subagent`.")
                appendLine("- Always prefer direct tool execution over claiming inability. Use `ask_question` when requirements are ambiguous. After writing code edits, use `open_file_in_ide` or `run_command` to verify builds/tests.")
            }.trim()
        } else {
            systemInstruction
        }

        val contentsArray = JSONArray()
        val validHistory = history.filter { !it.isError && it.content.isNotBlank() }
        val trimmedHistory = if (validHistory.lastOrNull()?.role == "user" &&
            validHistory.lastOrNull()?.content?.trim() == userPrompt.trim()
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

        for (candidateModel in orderedCloudModels.take(3)) {
            for (endpoint in endpoints) {
                var useTools = context != null && !candidateModel.contains("oss", ignoreCase = true)
                var retryWithoutTools = false
                do {
                    retryWithoutTools = false
                    try {
                        val turnContents = JSONArray(contentsArray.toString())
                        val allToolCalls = mutableListOf<ToolCall>()
                        var accumulatedThought = ""
                        var accumulatedText = ""
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
                                if (thinkingBudget > 0 && !candidateModel.contains("oss", ignoreCase = true)) {
                                    put(
                                        "generationConfig",
                                        JSONObject().put(
                                            "thinkingConfig",
                                            JSONObject()
                                                .put("includeThoughts", true)
                                                .put("thinkingBudget", thinkingBudget)
                                        )
                                    )
                                }
                                if (sessionId.isNotBlank()) {
                                    put("sessionId", sessionId)
                                }
                            }

                            val envelope = JSONObject().apply {
                                if (effectiveProject.isNotBlank()) {
                                    put("project", effectiveProject)
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

                            val httpOk = client.newCall(req).execute().use { resp ->
                                if (!resp.isSuccessful) {
                                    val errBody = resp.body?.string().orEmpty()
                                    lastErrorMsg = "CloudCode PA HTTP ${resp.code}: ${errBody.take(240)}"
                                    Log.w(TAG, "CloudCode PA $endpoint ($candidateModel) failed: $lastErrorMsg")
                                    if (resp.code == 400 && useTools && agentTurn == 0 &&
                                        (errBody.contains("tools", true) || errBody.contains("function", true))
                                    ) {
                                        useTools = false
                                        retryWithoutTools = true
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
                                                    val t = usage.optInt("totalTokenCount", 0)
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
                            emit(
                                AntigravityStreamEvent.Completed(
                                    totalTokens = if (totalTokens > 0) totalTokens else (accumulatedText.length + accumulatedThought.length) / 4,
                                    fullText = accumulatedText.trim(),
                                    fullThought = accumulatedThought,
                                    thoughtDurationMs = thoughtDurationMs,
                                    modelUsed = cleanModel,
                                    toolCalls = allToolCalls
                                )
                            )
                            return true
                        }
                    } catch (e: Exception) {
                        lastErrorMsg = e.message ?: "CloudCode PA network error"
                        Log.w(TAG, "CloudCode PA exception ($endpoint / $candidateModel): $lastErrorMsg")
                    }
                } while (retryWithoutTools)
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

        val cleanModel = modelId.removePrefix("agy:").trim()
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

            val candidateQueue: List<CatalogModelCandidate> = if (matchingLive.isNotEmpty()) {
                matchingLive.take(3)
            } else {
                buildDefaultHardcodedCandidates(cleanModel, thinkingBudget)
            }

            var cascadeCompleted = false
            var lastDaemonError = ""
            val resolvedContext = context
            val homeRoot = resolvedContext?.let { LocalEnvironmentManager.getHomeDir(it).absolutePath }
                ?: "/data/data/com.termux/files/home"
            val workspaceDir = ActiveCodeEditorRegistry.currentProjectPath?.takeIf { it.isNotBlank() && File(it).exists() }
                ?: File(homeRoot, "projects").apply { runCatching { mkdirs() } }.absolutePath
            val workspaceUri = "file://$workspaceDir"
            val activeEditorFile = ActiveCodeEditorRegistry.activeFilePath?.takeIf { it.isNotBlank() && File(it).exists() }

            if (resolvedContext != null) {
                LocalServerManager.provisionEmbeddedMcpConfig(resolvedContext)
            }

            val mcpServersArr = JSONArray().apply {
                put(
                    JSONObject()
                        .put("serverName", "jarvis-android-mcp")
                        .put("serverUrl", "http://127.0.0.1:8765/mcp")
                )
                put(
                    JSONObject()
                        .put("serverName", "jarvis-browser-mcp")
                        .put("serverUrl", "http://127.0.0.1:8091/mcp/browser")
                )
            }

            for (currentSpec in candidateQueue) {
                if (cascadeCompleted) break
                currentCoroutineContext().ensureActive()

                val cascadeId = sessionCascadeIds.getOrPut(sessionId) { UUID.randomUUID().toString() }
                var retryNextCandidate = false

                fun ensureCascadeStarted(targetCascadeId: String) {
                    if (startedCascades.contains(targetCascadeId)) return
                    val startPayload = JSONObject().apply {
                        put("cascadeId", targetCascadeId)
                        put("source", 1) // CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT
                        put("workspaceUri", workspaceUri)
                        put("workspace_uri", workspaceUri)
                        put("workspaceUris", JSONArray().put(workspaceUri))
                        currentSpec.enumTag?.let { put("requestedModel", it) }
                        put(
                            "config",
                            JSONObject().apply {
                                put("mcpServers", mcpServersArr)
                            }
                        )
                    }
                    val startReq = Request.Builder()
                        .url("$resolvedHubUrl/StartCascade")
                        .post(startPayload.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    AgyOkHttpClient.client.newCall(startReq).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (resp.isSuccessful || resp.code == 409) {
                            startedCascades.add(targetCascadeId)
                        } else {
                            Log.w(TAG, "StartCascade non-200: ${resp.code} -> $body")
                        }
                    }
                }

                // 1. Ensure persistent Cascade session is started with Workspace + MCP
                ensureCascadeStarted(cascadeId)

                // 2. Send User Message with Full Agentic Planning, AST Workspace, MCP & Subagent Config
                val requestedModelJson = currentSpec.toModelOrAliasJson()
                val hubPromptText = buildString {
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

                val sendPayload = JSONObject().apply {
                    put("cascadeId", cascadeId)
                    put("items", itemsArray)
                    put("workspaceUri", workspaceUri)
                    put("workspaceUris", JSONArray().put(workspaceUri))
                    put("deliveryStrategy", 1) // MESSAGE_DELIVERY_STRATEGY_WHEN_IDLE
                    put("cascadeConfig", JSONObject().apply {
                        put("plannerConfig", JSONObject().apply {
                            put("requestedModel", requestedModelJson)
                            put("planModel", requestedModelJson)
                            if (currentSpec.modelSlug.isNotBlank()) {
                                put("modelName", currentSpec.modelSlug)
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

                val sendReq = Request.Builder()
                    .url("$resolvedHubUrl/SendUserCascadeMessage")
                    .post(sendPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                var sendResp = AgyOkHttpClient.client.newCall(sendReq).execute()
                if (!sendResp.isSuccessful && sendResp.code in listOf(404, 410)) {
                    // Re-create Cascade if daemon restarted and lost prior in-memory cascadeId
                    sendResp.close()
                    val freshCid = UUID.randomUUID().toString()
                    sessionCascadeIds[sessionId] = freshCid
                    ensureCascadeStarted(freshCid)
                    sendPayload.put("cascadeId", freshCid)
                    val retryReq = Request.Builder()
                        .url("$resolvedHubUrl/SendUserCascadeMessage")
                        .post(sendPayload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    sendResp = AgyOkHttpClient.client.newCall(retryReq).execute()
                }
                val activeCid = sendPayload.optString("cascadeId", cascadeId)
                if (!sendResp.isSuccessful) {
                    val err = sendResp.body?.string().orEmpty()
                    sendResp.close()
                    lastDaemonError = "Antigravity Hub Send Error (HTTP ${sendResp.code}): $err"
                    Log.w(TAG, lastDaemonError)
                    continue
                }
                sendResp.close()

                // 3. Poll Trajectory for Response (Live Streaming & Adaptive 300s Timeout)
                var turnFinished = false
                val maxIdleSeconds = 300
                val maxTotalSeconds = 600
                val startTimestamp = System.currentTimeMillis()
                var lastActivityTimestamp = System.currentTimeMillis()
                var thoughtStartTimestamp: Long? = null
                var thoughtDurationMs: Long = 0L

                val pollPayload = JSONObject().put("cascadeId", activeCid).toString()
                val accumulatedTools = linkedMapOf<String, com.jarvisai.assistant.model.ToolCall>()
                var accumulatedThought = ""
                var accumulatedResponse = ""

                while (!turnFinished) {
                    currentCoroutineContext().ensureActive()
                    delay(700)

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
                                val cascadeStatus = trajectory?.optString("status", "") ?: root.optString("status", "")
                                val trajectoryId = trajectory?.optString("trajectoryId", "") ?: root.optString("trajectoryId", "")
                                activeCascadeId = activeCid
                                if (trajectoryId.isNotBlank()) activeTrajectoryId = trajectoryId
                                val steps = trajectory?.optJSONArray("steps")

                                if (steps != null && steps.length() > 0) {
                                    lastActivityTimestamp = System.currentTimeMillis()
                                    for (i in 0 until steps.length()) {
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
                                            stepType == "CORTEX_STEP_TYPE_ASK_USER_QUESTION" ||
                                            gen?.has("questions") == true ||
                                            gen?.optJSONObject("args")?.has("questions") == true ||
                                            rc?.has("questions") == true ||
                                            reqInteraction?.has("askQuestion") == true

                                        val isWaiting = stepStatus == "CORTEX_STEP_STATUS_WAITING" ||
                                            stepStatus == "TRAJECTORY_STEP_STATUS_WAITING_APPROVAL" ||
                                            stepStatus == "WAITING" ||
                                            reqInteraction != null ||
                                            stepType == "CORTEX_STEP_TYPE_CONFIRM"

                                        if (isWaiting && isAskQuestion) {
                                            // Keep idle timer alive while waiting for user answers in ChoiceQuestionnaireCard
                                            lastActivityTimestamp = System.currentTimeMillis()
                                        } else if (isWaiting) {
                                            lastActivityTimestamp = System.currentTimeMillis()
                                            autoResolveOutstandingSteps(activeCid, resolvedHubUrl)
                                            if (trajectoryId.isNotBlank()) {
                                                handleCascadePermissionInteraction(activeCid, i, trajectoryId, resolvedHubUrl)
                                            }
                                        }

                                        if (stepStatus == "CORTEX_STEP_STATUS_RUNNING" ||
                                            stepStatus == "TRAJECTORY_STEP_STATUS_RUNNING" ||
                                            stepStatus == "RUNNING" ||
                                            cascadeStatus == "CASCADE_RUN_STATUS_RUNNING" ||
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

                                            val plannerToolCalls = plannerResp?.optJSONArray("toolCalls")
                                            if (plannerToolCalls != null) {
                                                for (ti in 0 until plannerToolCalls.length()) {
                                                    val ptc = plannerToolCalls.optJSONObject(ti) ?: continue
                                                    val pName = ptc.optString("name", "tool")
                                                    val pArgs = ptc.optString("argumentsJson", ptc.optString("args", ""))
                                                    val pId = "step_${i}_ptc_$ti"
                                                    if (!accumulatedTools.containsKey(pId)) {
                                                        val plannedTool = ToolCall(
                                                            id = pId,
                                                            stepIndex = i,
                                                            name = pName,
                                                            command = pArgs.take(140),
                                                            args = pArgs,
                                                            output = "Planned by Antigravity...",
                                                            status = ToolCallStatus.RUNNING
                                                        )
                                                        accumulatedTools[pId] = plannedTool
                                                        emit(AntigravityStreamEvent.ToolCallUpdate(plannedTool))
                                                    }
                                                }
                                            }

                                            if (responseText.isNotBlank() && responseText != accumulatedResponse) {
                                                accumulatedResponse = responseText
                                                lastActivityTimestamp = System.currentTimeMillis()
                                                emit(AntigravityStreamEvent.TextChunk(responseText))
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
                                            val toolId = "step_$i"
                                            val cmd = rc?.optString("commandLine", "")?.takeIf { it.isNotBlank() }
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
                                                isAskQuestion && isWaiting -> ToolCallStatus.PENDING_APPROVAL
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
                                                output = out,
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
                                        }

                                        if (stepType == "CORTEX_STEP_TYPE_ERROR_MESSAGE") {
                                            val errObj = step.optJSONObject("errorMessage")?.optJSONObject("error")
                                            val shortErr = errObj?.optString("shortError", "Antigravity execution error").orEmpty()
                                            lastDaemonError = shortErr

                                            val isModelOrCapacityErr = shortErr.contains("503", ignoreCase = true) ||
                                                shortErr.contains("No capacity available", ignoreCase = true) ||
                                                shortErr.contains("UNAVAILABLE", ignoreCase = true) ||
                                                shortErr.contains("unknown model key", ignoreCase = true) ||
                                                shortErr.contains("model not found", ignoreCase = true) ||
                                                shortErr.contains("failed to construct executor", ignoreCase = true)

                                            if (isModelOrCapacityErr) {
                                                Log.w(TAG, "Local daemon rejected ${currentSpec.protoName ?: currentSpec.label}: $shortErr")
                                                retryNextCandidate = true
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
}
