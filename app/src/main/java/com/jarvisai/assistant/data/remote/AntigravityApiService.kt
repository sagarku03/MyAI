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
import com.jarvisai.assistant.terminal.AgyCliManager
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.LocalServerManager
import com.jarvisai.assistant.terminal.WorkspaceSyncManager
import com.jarvisai.assistant.termux.JarvisTermuxBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
            "You are Agentra powered by Antigravity, a hyper-capable agentic AI coding and device assistant on Android. Always report time and dates in India Standard Time (IST / Asia/Kolkata). Respond clearly in clean markdown with accurate code blocks."
        const val SYSTEM_INSTRUCTION = DEFAULT_SYSTEM_INSTRUCTION

        private const val HUB_INSPECTION_TTL_MS = 180_000L
        private val cachedHubInspections = ConcurrentHashMap<String, Pair<Long, Any>>()
        private val preferredStartPayloadByHub = ConcurrentHashMap<String, Int>()
        private val preferredSendProfileByHubModel = ConcurrentHashMap<String, Int>()
        private val preferredCliModelArgById = ConcurrentHashMap<String, String>()

        fun invalidateHubInspectionCache() {
            cachedHubInspections.clear()
            preferredStartPayloadByHub.clear()
            preferredSendProfileByHubModel.clear()
            preferredCliModelArgById.clear()
        }
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
        hubBaseUrl: String,
        requestedInteraction: JSONObject? = null,
        stepType: String = ""
    ) {
        val endpoint = if (hubBaseUrl.endsWith("LanguageServerService")) {
            "$hubBaseUrl/HandleCascadeUserInteraction"
        } else {
            "$hubBaseUrl/exa.language_server_pb.LanguageServerService/HandleCascadeUserInteraction"
        }

        val candidates = mutableListOf<Pair<String, JSONObject>>()
        val reqKeys = mutableSetOf<String>()
        requestedInteraction?.keys()?.forEach { reqKeys.add(it) }

        val lowerStep = stepType.lowercase()
        when {
            reqKeys.any { it.contains("runCommand", true) || it.contains("command", true) } || lowerStep.contains("run_command") -> {
                candidates.add("runCommand" to JSONObject().put("confirm", true))
                candidates.add("confirmCommand" to JSONObject().put("confirm", true))
                candidates.add("permission" to JSONObject().put("allow", true))
            }
            reqKeys.any { it.contains("file", true) } || lowerStep.contains("write") || lowerStep.contains("replace") || lowerStep.contains("edit") -> {
                candidates.add("filePermission" to JSONObject().put("allow", true).put("scope", "PERMISSION_SCOPE_ONCE"))
                candidates.add("permission" to JSONObject().put("allow", true))
            }
            reqKeys.any { it.contains("mcp", true) } || lowerStep.contains("mcp") -> {
                candidates.add("mcp" to JSONObject().put("confirm", true))
                candidates.add("permission" to JSONObject().put("allow", true))
            }
            reqKeys.any { it.contains("browser", true) } || lowerStep.contains("browser") -> {
                candidates.add("confirmBrowserSetup" to JSONObject().put("confirm", true))
                candidates.add("browserAction" to JSONObject().put("confirm", true))
                candidates.add("permission" to JSONObject().put("allow", true))
            }
            else -> {
                candidates.add("permission" to JSONObject().put("allow", true))
                candidates.add("runCommand" to JSONObject().put("confirm", true))
                candidates.add("filePermission" to JSONObject().put("allow", true))
                candidates.add("mcp" to JSONObject().put("confirm", true))
            }
        }

        for ((fieldKey, fieldValue) in candidates) {
            try {
                val interactionObj = JSONObject()
                    .put("trajectoryId", trajectoryId)
                    .put("stepIndex", stepIndex)
                    .put(fieldKey, fieldValue)
                val payload = JSONObject()
                    .put("cascadeId", cascadeId)
                    .put("cascade_id", cascadeId)
                    .put("interaction", interactionObj)
                    .toString()
                val req = Request.Builder()
                    .url(endpoint)
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
                AgyOkHttpClient.client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        Log.d(TAG, "Granted $fieldKey interaction for step $stepIndex in cascade: $cascadeId")
                        return
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "HandleCascadeUserInteraction ($fieldKey) notice: ${e.message}")
            }
        }
    }

    private fun cancelCascadeInvocationSilently(resolvedHubUrl: String, cascadeId: String) {
        runCatching {
            val cancelEndpoint = if (resolvedHubUrl.endsWith("LanguageServerService")) {
                "$resolvedHubUrl/CancelCascadeInvocation"
            } else {
                "$resolvedHubUrl/exa.language_server_pb.LanguageServerService/CancelCascadeInvocation"
            }
            AgyOkHttpClient.client.newCall(
                Request.Builder()
                    .url(cancelEndpoint)
                    .post(JSONObject().put("cascadeId", cascadeId).put("cascade_id", cascadeId).toString().toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute().close()
        }
    }

    private fun isStepDoneStatus(stepStatus: String): Boolean =
        stepStatus.contains("DONE", true) ||
            stepStatus.contains("COMPLETED", true) ||
            stepStatus.contains("COMPLETE", true) ||
            stepStatus.contains("FINISHED", true) ||
            stepStatus.contains("SUCCESS", true) ||
            stepStatus.contains("ERROR", true) ||
            stepStatus.contains("FAILED", true) ||
            stepStatus.contains("CANCEL", true) ||
            stepStatus.contains("SKIPPED", true) ||
            stepStatus.contains("CLEARED", true) ||
            stepStatus.contains("ABORT", true) ||
            stepStatus.contains("REJECT", true) ||
            stepStatus.contains("TIMED_OUT", true)

    private fun isCascadeIdleStatus(cascadeStatus: String): Boolean =
        cascadeStatus.contains("IDLE", true) ||
            cascadeStatus.contains("DONE", true) ||
            cascadeStatus.contains("COMPLETED", true) ||
            cascadeStatus.contains("FINISHED", true) ||
            cascadeStatus.contains("STOPPED", true) ||
            cascadeStatus.contains("CANCEL", true) ||
            cascadeStatus.contains("PAUSED", true) ||
            cascadeStatus.contains("INACTIVE", true)

    private fun isCascadeRunningStatus(cascadeStatus: String): Boolean =
        cascadeStatus.contains("RUNNING", true) ||
            cascadeStatus.contains("GENERATING", true) ||
            cascadeStatus.contains("PENDING", true) ||
            cascadeStatus.contains("ACTIVE", true) ||
            cascadeStatus.contains("WAITING", true)

    private fun isStepActiveStatus(
        stepStatus: String,
        stepType: String,
        isStepDone: Boolean,
        isCascadeExplicitlyIdle: Boolean,
        isLastStep: Boolean
    ): Boolean = !isStepDone &&
        !isCascadeExplicitlyIdle &&
        stepType != "CORTEX_STEP_TYPE_ERROR_MESSAGE" && (
            stepStatus.contains("RUNNING", true) ||
                stepStatus.contains("GENERATING", true) ||
                stepStatus.contains("PENDING", true) ||
                stepStatus.contains("WAITING", true) ||
                stepStatus.contains("QUEUED", true) ||
                stepStatus.contains("STARTING", true) ||
                (stepStatus.isBlank() && isLastStep)
            )

    private fun resolveToolCallStatus(
        stepStatus: String,
        isAskQuestion: Boolean,
        hasSubmittedAsk: Boolean,
        isWaiting: Boolean,
        isStepExplicitlyActive: Boolean,
        isStepDone: Boolean,
        isCascadeExplicitlyIdle: Boolean,
        isSupersededByLaterStep: Boolean,
        hasOutput: Boolean,
        exitCode: Int?,
        toolInvError: Boolean
    ): ToolCallStatus {
        val isExplicitFailure = stepStatus.contains("ERROR", true) ||
            stepStatus.contains("FAILED", true) ||
            stepStatus.contains("REJECT", true) ||
            stepStatus.contains("TIMED_OUT", true) ||
            (exitCode != null && exitCode != 0) ||
            toolInvError
        val isCancelledOrSkipped = stepStatus.contains("CANCEL", true) ||
            stepStatus.contains("SKIPPED", true) ||
            stepStatus.contains("CLEARED", true) ||
            stepStatus.contains("ABORT", true)
        return when {
            isAskQuestion && hasSubmittedAsk -> ToolCallStatus.SUCCESS
            isAskQuestion && (isWaiting || isStepExplicitlyActive) -> ToolCallStatus.PENDING_APPROVAL
            isExplicitFailure -> ToolCallStatus.FAILED
            isCancelledOrSkipped -> if (exitCode == 0) ToolCallStatus.SUCCESS else ToolCallStatus.FAILED
            isStepDone || hasOutput || exitCode == 0 -> ToolCallStatus.SUCCESS
            isCascadeExplicitlyIdle -> ToolCallStatus.SUCCESS
            isWaiting -> ToolCallStatus.RUNNING
            stepStatus.contains("RUNNING", true) ||
                stepStatus.contains("GENERATING", true) ||
                stepStatus.contains("PENDING", true) ||
                stepStatus.contains("QUEUED", true) ||
                stepStatus.contains("STARTING", true) -> ToolCallStatus.RUNNING
            isSupersededByLaterStep -> ToolCallStatus.SUCCESS
            else -> ToolCallStatus.RUNNING
        }
    }

    private fun isHubModelOrCapacityError(shortErr: String): Boolean =
        shortErr.contains("503", ignoreCase = true) ||
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

    private fun isHubTransientNetworkError(shortErr: String): Boolean =
        shortErr.contains("EOF", ignoreCase = true) ||
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

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AntigravityStreamEvent>.finalizeToolsOnWatchdog(
        accumulatedTools: MutableMap<String, ToolCall>,
        accumulatedResponse: String,
        accumulatedThought: String,
        thoughtDurationMs: Long,
        elapsedMs: Long,
        userPrompt: String,
        cleanModel: String,
        effectiveIdleLimit: Int
    ) {
        accumulatedTools.keys.removeAll { it.contains("_ptc_") }
        val finalizedOnTimeout = accumulatedTools.values.map { tc ->
            if (tc.status == ToolCallStatus.RUNNING) {
                val resolved = tc.copy(
                    status = if (tc.exitCode != null && tc.exitCode != 0) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS
                )
                emit(AntigravityStreamEvent.ToolCallUpdate(resolved))
                resolved
            } else tc
        }
        if (accumulatedResponse.isNotBlank() || finalizedOnTimeout.isNotEmpty()) {
            val finalMsg = accumulatedResponse.ifBlank {
                val doneCount = finalizedOnTimeout.count { it.status == ToolCallStatus.SUCCESS }
                "Completed $doneCount tool step(s), then the local Agentra Hub session paused before sending a final summary. Send 'continue' if you want me to proceed further."
            }
            val estOut = ((finalMsg.length + accumulatedThought.length) / 4).coerceAtLeast(1)
            val estPrompt = ((userPrompt.length + finalizedOnTimeout.sumOf { it.command.length + it.output.length }) / 4).coerceAtLeast(1)
            emit(
                AntigravityStreamEvent.Completed(
                    totalTokens = estPrompt + estOut,
                    fullText = finalMsg,
                    fullThought = accumulatedThought,
                    thoughtDurationMs = thoughtDurationMs.takeIf { it > 0L } ?: elapsedMs,
                    modelUsed = cleanModel,
                    toolCalls = finalizedOnTimeout,
                    promptTokens = estPrompt,
                    outputTokens = estOut,
                    cachedTokens = 0
                )
            )
        } else {
            emit(AntigravityStreamEvent.Error("Agentra Hub inactive: no progress received for ${effectiveIdleLimit}s."))
        }
    }

    private data class AutoContinueAskOutcome(
        val activeCid: String,
        val turnBaselineStepCount: Int,
        val pollPayload: String? = null
    )

    private fun buildMcpServersConfigArray(resolvedContext: Context?): JSONArray = JSONArray().apply {
        val configuredSpecs: List<com.jarvisai.assistant.mcp.McpServerSpec> = if (resolvedContext != null) {
            runCatching {
                com.jarvisai.assistant.mcp.McpServersManager.loadConfigSpecs(resolvedContext)
                    .values
                    .filter { !it.disabled }
            }.getOrDefault(emptyList())
        } else emptyList()

        if (configuredSpecs.isNotEmpty()) {
            val stdioHelper = resolvedContext?.let {
                com.jarvisai.assistant.terminal.LocalEnvironmentManager.ensureMcpStdioBridgeScript(it).absolutePath
            } ?: "/data/user/0/${com.example.BuildConfig.APPLICATION_ID}/files/usr/bin/agentra-mcp-stdio"
            configuredSpecs.forEach { spec ->
                val entry = JSONObject()
                    .put("serverName", spec.serverName)
                    .put("server_name", spec.serverName)
                val resolvedUrl = spec.serverUrl.ifBlank {
                    when (spec.serverName) {
                        "agentra-android-mcp", "jarvis-android-mcp" -> JarvisHubConfig.getMcpUrl()
                        "agentra-browser-mcp", "jarvis-browser-mcp" -> JarvisHubConfig.getBrowserMcpUrl()
                        "filesystem" -> "http://127.0.0.1:${JarvisHubConfig.ISOLATED_MCP_PORT}/filesystem"
                        else -> ""
                    }
                }
                if (resolvedUrl.isNotBlank()) {
                    entry.put("serverUrl", resolvedUrl)
                    entry.put("server_url", resolvedUrl)
                    entry.put("url", resolvedUrl)
                }
                val cmd = spec.command.ifBlank {
                    if (resolvedUrl.startsWith("http://127.0.0.1") || resolvedUrl.startsWith("http://localhost")) {
                        stdioHelper
                    } else ""
                }
                if (cmd.isNotBlank()) {
                    entry.put("command", cmd)
                    val argsList: List<String> = if (spec.args.isNotEmpty()) spec.args else if (resolvedUrl.isNotBlank()) listOf(resolvedUrl) else emptyList()
                    entry.put("args", JSONArray(argsList))
                }
                if (spec.env.isNotEmpty()) {
                    val envObj = JSONObject()
                    spec.env.forEach { (k, v) -> envObj.put(k, v) }
                    entry.put("env", envObj)
                }
                put(entry)
            }
        } else {
            put(
                JSONObject()
                    .put("serverName", "agentra-android-mcp")
                    .put("server_name", "agentra-android-mcp")
                    .put("serverUrl", JarvisHubConfig.getMcpUrl())
                    .put("server_url", JarvisHubConfig.getMcpUrl())
            )
            put(
                JSONObject()
                    .put("serverName", "agentra-browser-mcp")
                    .put("server_name", "agentra-browser-mcp")
                    .put("serverUrl", JarvisHubConfig.getBrowserMcpUrl())
                    .put("server_url", JarvisHubConfig.getBrowserMcpUrl())
            )
            put(
                JSONObject()
                    .put("serverName", "filesystem")
                    .put("server_name", "filesystem")
                    .put("serverUrl", "http://127.0.0.1:${JarvisHubConfig.ISOLATED_MCP_PORT}/filesystem")
                    .put("server_url", "http://127.0.0.1:${JarvisHubConfig.ISOLATED_MCP_PORT}/filesystem")
            )
        }
    }

    private fun ensureLocalCascadeStarted(
        targetCascadeId: String,
        sessionId: String,
        resolvedHubUrl: String,
        workspaceUri: String,
        hubMetadataJson: JSONObject,
        currentSpec: CatalogModelCandidate,
        mcpServersArr: JSONArray
    ): String {
        if (startedCascades.contains(targetCascadeId)) return targetCascadeId

        val startPayloads = listOf(
            JSONObject().apply {
                put("cascadeId", targetCascadeId)
                put("source", 1) // CORTEX_TRAJECTORY_SOURCE_CASCADE_CLIENT
                put("workspaceUri", workspaceUri)
                put("workspaceUris", JSONArray().put(workspaceUri))
                put("metadata", hubMetadataJson)
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

        val preferredStartIdx = (preferredStartPayloadByHub[resolvedHubUrl] ?: 0).coerceIn(0, startPayloads.lastIndex)
        val orderedPayloadIndices = (preferredStartIdx..startPayloads.lastIndex).toList() + (0 until preferredStartIdx).toList()
        for (idx in orderedPayloadIndices) {
            val startPayload = startPayloads[idx]
            val startReq = Request.Builder()
                .url("$resolvedHubUrl/StartCascade")
                .post(startPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            AgyOkHttpClient.client.newCall(startReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                when {
                    resp.isSuccessful -> {
                        preferredStartPayloadByHub[resolvedHubUrl] = idx
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
                        Log.w(TAG, "StartCascade HTTP 400 with schema=$idx, trying fallback schema: $body")
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

    private fun buildLocalHubPromptText(
        isFreshLocalCascade: Boolean,
        isMcpOrSkillIntent: Boolean,
        lowerPrompt: String,
        userPrompt: String,
        history: List<ChatMessageEntity>,
        resolvedContext: Context?,
        activeEditorFile: String?,
        workspaceDir: String,
        resolvedAttachments: List<ResolvedAttachmentPart>
    ): String {
        val priorHistoryBlock = if (isFreshLocalCascade && history.isNotEmpty()) {
            val cleanPrior = history.filter { !it.isError && it.content.isNotBlank() }
                .let { list ->
                    val last = list.lastOrNull()
                    if (last != null && last.role.equals("user", true) && last.content.trim() == userPrompt.trim()) {
                        list.dropLast(1)
                    } else list
                }
                .takeLast(8)
            if (cleanPrior.isNotEmpty()) {
                buildString {
                    appendLine("[RECENT CONVERSATION CONTEXT]")
                    cleanPrior.forEach { msg ->
                        val speaker = if (msg.role.equals("model", true)) "AGENTRA" else "USER"
                        appendLine("$speaker: ${com.jarvisai.assistant.model.ChatAttachmentCodec.toPromptReadableText(msg.content).take(450)}")
                    }
                    appendLine("[END CONTEXT]")
                }
            } else ""
        } else ""

        return buildString {
            if (isFreshLocalCascade || lowerPrompt.contains("time") || lowerPrompt.contains("date") || lowerPrompt.contains("samay") || lowerPrompt.contains("waqt") || lowerPrompt.contains("baje")) {
                appendLine(com.jarvisai.assistant.util.IndiaTimeUtils.buildAiRuntimeClockDirective())
            }
            if (priorHistoryBlock.isNotBlank()) {
                appendLine(priorHistoryBlock)
            }
            if (resolvedContext != null && (isFreshLocalCascade || isMcpOrSkillIntent)) {
                val liveMcpBlock = runCatching {
                    com.jarvisai.assistant.mcp.McpServersManager.buildLiveMcpStatusPromptSection(
                        context = resolvedContext,
                        compact = !isMcpOrSkillIntent
                    )
                }.getOrDefault("")
                if (liveMcpBlock.isNotBlank()) {
                    appendLine(liveMcpBlock)
                    appendLine()
                }
                if (isMcpOrSkillIntent) {
                    val liveSkillsBlock = runCatching {
                        com.jarvisai.assistant.skills.SkillsAndPluginsManager.buildActiveSkillsPromptSection(resolvedContext)
                    }.getOrDefault("")
                    if (liveSkillsBlock.isNotBlank()) {
                        appendLine(liveSkillsBlock)
                        appendLine()
                    }
                }
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
    }

    private fun buildLocalCascadeSendPayload(
        targetCascadeId: String,
        profile: Int,
        itemsArray: JSONArray,
        workspaceUri: String,
        workspaceDir: String,
        hubMetadataJson: JSONObject,
        requestedModelJson: JSONObject,
        planModelEnumValue: Any?,
        thinkingBudget: Int,
        isGemini4Family: Boolean,
        mappedThinkingLevel: String,
        effectiveAgenticMode: Boolean,
        isActionOrWorkspaceTurn: Boolean,
        isFreshLocalCascade: Boolean,
        mcpServersArr: JSONArray
    ): JSONObject = JSONObject().apply {
        put("cascadeId", targetCascadeId)
        put("items", itemsArray)
        when (profile) {
            0 -> {
                put("workspaceUri", workspaceUri)
                put("workspaceUris", JSONArray().put(workspaceUri))
                put("metadata", hubMetadataJson)
                put("deliveryStrategy", 1)
                put("cascadeConfig", JSONObject().apply {
                    put("plannerConfig", JSONObject().apply {
                        put("requestedModel", requestedModelJson)
                        put("conversational", JSONObject().apply {
                            put("plannerMode", 1)
                            put("agenticMode", effectiveAgenticMode)
                        })
                        put("toolConfig", JSONObject().apply {
                            put("runCommand", JSONObject().apply {
                                put("autoCommandConfig", JSONObject().put("autoExecutionPolicy", 2))
                            })
                            put("notifyUser", JSONObject())
                        })
                        put("enableSubagents", effectiveAgenticMode)
                        put("supportsLatexRendering", true)
                    })
                    if (isFreshLocalCascade) {
                        put("mcpServers", mcpServersArr)
                    }
                    put("conversationHistoryConfig", JSONObject().put("preserveHistory", true))
                })
            }
            1 -> {
                put("metadata", hubMetadataJson)
                put("cascadeConfig", JSONObject().apply {
                    put("plannerConfig", JSONObject().apply {
                        put("requestedModel", requestedModelJson)
                        put("conversational", JSONObject().apply {
                            put("plannerMode", 1)
                            put("agenticMode", effectiveAgenticMode)
                        })
                    })
                    if (isFreshLocalCascade) {
                        put("mcpServers", mcpServersArr)
                    }
                })
            }
            2 -> {
                put("blocking", false)
                put("metadata", hubMetadataJson)
                put("cascadeConfig", JSONObject().apply {
                    put("plannerConfig", JSONObject().apply {
                        put("requestedModel", requestedModelJson)
                        if (planModelEnumValue != null) {
                            put("planModel", planModelEnumValue)
                        }
                        put("includeThinking", thinkingBudget > 0)
                        if (thinkingBudget > 0) {
                            if (isGemini4Family) {
                                put("thinkingLevel", mappedThinkingLevel)
                            } else {
                                put("thinkingBudget", thinkingBudget)
                            }
                        }
                        put("conversational", JSONObject().apply {
                            put("plannerMode", 1)
                            put("agenticMode", effectiveAgenticMode)
                        })
                        put("toolConfig", JSONObject().apply {
                            put("runCommand", JSONObject().apply {
                                put("autoCommandConfig", JSONObject().put("autoExecutionPolicy", 2))
                            })
                            put("notifyUser", JSONObject())
                        })
                    })
                    if (isFreshLocalCascade) {
                        put("mcpServers", mcpServersArr)
                    }
                })
            }
            else -> {
                put("workspaceUri", workspaceUri)
                put("workspaceUris", JSONArray().put(workspaceUri))
                put("deliveryStrategy", 1)
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
                            put("agenticMode", isActionOrWorkspaceTurn)
                        })
                        put("toolConfig", JSONObject().apply {
                            put("runCommand", JSONObject().apply {
                                put("autoCommandConfig", JSONObject().put("autoExecutionPolicy", 2))
                            })
                            put("notifyUser", JSONObject())
                        })
                        put("enableSubagents", isActionOrWorkspaceTurn)
                        put("supportsLatexRendering", true)
                        put("useAiCredits", false)
                    })
                    if (isFreshLocalCascade) {
                        put("mcpServers", mcpServersArr)
                    }
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
        }
    }

    private fun extractToolStepCommandAndOutput(
        step: JSONObject,
        rc: JSONObject?,
        cmdStatus: JSONObject?,
        viewFile: JSONObject?,
        viewOutline: JSONObject?,
        writeFile: JSONObject?,
        replaceFile: JSONObject?,
        codeAction: JSONObject?,
        listDir: JSONObject?,
        grepSearch: JSONObject?,
        webSearch: JSONObject?,
        subagent: JSONObject?,
        mcpTool: JSONObject?,
        toolInv: JSONObject?,
        gen: JSONObject?,
        meta: JSONObject?,
        isAskQuestion: Boolean,
        toolName: String
    ): Pair<String, String> {
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

        return cmd to out
    }

    private fun parseAskQuestionQuestionnaire(
        step: JSONObject,
        gen: JSONObject?,
        rc: JSONObject?,
        reqInteraction: JSONObject?,
        submittedAskRecord: SubmittedAskQuestionRecord?
    ): com.jarvisai.assistant.model.ChoiceQuestionnaire? {
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
        return if (questionsList.isNotEmpty()) {
            com.jarvisai.assistant.model.ChoiceQuestionnaire(questions = questionsList)
        } else null
    }

    private suspend fun attemptAutoContinueStalledAskQuestion(
        activeCid: String,
        stepIndex: Int,
        stepCount: Int,
        steps: JSONArray,
        submittedAskRecord: SubmittedAskQuestionRecord,
        autoContinuedAskSteps: MutableSet<String>,
        resolvedHubUrl: String,
        sessionId: String,
        workspaceUri: String,
        hubMetadataJson: JSONObject,
        currentSpec: CatalogModelCandidate,
        requestedModelJson: JSONObject,
        planModelEnumValue: Any?,
        thinkingBudget: Int,
        mcpServersArr: JSONArray,
        userPrompt: String
    ): AutoContinueAskOutcome? {
        val askKey = "$activeCid:$stepIndex"
        if (autoContinuedAskSteps.contains(askKey)) return null
        val hasLaterProgress = (stepIndex + 1 until stepCount).any { laterIdx ->
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
        if (hasLaterProgress) return null

        val elapsedSinceSubmit = System.currentTimeMillis() - submittedAskRecord.submittedAtMs
        val shouldForceContinue = (!submittedAskRecord.interactionAccepted && elapsedSinceSubmit >= 700L) ||
            (elapsedSinceSubmit >= 2200L)
        if (!shouldForceContinue) return null

        autoContinuedAskSteps.add(askKey)
        Log.w(TAG, "ask_question step $stepIndex stalled ${elapsedSinceSubmit}ms after user submit (accepted=${submittedAskRecord.interactionAccepted}); forcing continuation.")
        cancelCascadeInvocationSilently(resolvedHubUrl, activeCid)

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
            return AutoContinueAskOutcome(activeCid = activeCid, turnBaselineStepCount = stepCount)
        }

        Log.w(TAG, "Cancelled cascade $activeCid did not advance; launching fresh continuation cascade.")
        startedCascades.remove(activeCid)
        val freshCid = ensureLocalCascadeStarted(
            targetCascadeId = UUID.randomUUID().toString(),
            sessionId = sessionId,
            resolvedHubUrl = resolvedHubUrl,
            workspaceUri = workspaceUri,
            hubMetadataJson = hubMetadataJson,
            currentSpec = currentSpec,
            mcpServersArr = mcpServersArr
        )
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
        return if (freshSent) {
            activeCascadeId = freshCid
            sessionCascadeIds[sessionId] = freshCid
            AutoContinueAskOutcome(
                activeCid = freshCid,
                turnBaselineStepCount = 0,
                pollPayload = JSONObject().put("cascadeId", freshCid).toString()
            )
        } else null
    }

    private val sessionCascadeIds = ConcurrentHashMap<String, String>()
    private val cliConversationIds = ConcurrentHashMap<String, String>()
    private val startedCascades = ConcurrentHashMap.newKeySet<String>()
    @Volatile
    private var activeCliProcess: Process? = null
    @Volatile
    private var cachedRemoteModels: Triple<String, Long, List<AntigravityModelInfo>>? = null
    @Volatile
    private var lastCloudCodeError: String = ""

    @Volatile
    var activeCascadeId: String = ""
        private set

    @Volatile
    var activeTrajectoryId: String = ""
        private set

    fun getCascadeIdForSession(sessionId: String): String? {
        return sessionCascadeIds[sessionId]?.takeIf { it.isNotBlank() }
            ?: cliConversationIds[sessionId]?.takeIf { it.isNotBlank() }
    }

    fun resetCascadeSession(sessionId: String) {
        cliConversationIds.remove(sessionId)
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
        runCatching { activeCliProcess?.destroyForcibly() }
        activeCliProcess = null
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

    fun clearCachedCatalogs() {
        cachedRemoteModels = null
        cachedHubInspections.clear()
    }

    private fun logRouteEvent(message: String) {
        Log.i(TAG, message)
        runCatching {
            val ts = com.jarvisai.assistant.util.IndiaTimeUtils.nowLogTimestamp()
            com.jarvisai.assistant.terminal.LocalServerManager.appendServerLog("$ts [Antigravity Route] $message")
        }
    }

    suspend fun fetchAvailableModels(
        token: String,
        projectId: String = "",
        hubBaseUrl: String = JarvisHubConfig.getLanguageServiceUrl(),
        useCache: Boolean = false,
        executionMode: com.jarvisai.assistant.model.AntigravityExecutionMode = com.jarvisai.assistant.model.AntigravityExecutionMode.HYBRID_BOTH
    ): Result<List<AntigravityModelInfo>> = withContext(Dispatchers.IO) {
        val allowLocalCli = executionMode.cliEnabled
        val allowCloudCodePa = executionMode.cloudCodePaEnabled

        val cleanProj = if (projectId.isBlank() ||
            projectId.equals("rising-fact-p41fc", ignoreCase = true) ||
            projectId.equals("aicode-consumers", ignoreCase = true) ||
            projectId.equals("cloudcode-default", ignoreCase = true)
        ) "" else projectId

        val cacheKey = "${executionMode.name}:${token.takeLast(16)}"
        if (useCache) {
            val cached = cachedRemoteModels
            if (cached != null && cached.first == cacheKey &&
                (System.currentTimeMillis() - cached.second) < 180_000L &&
                cached.third.isNotEmpty()
            ) {
                return@withContext Result.success(cached.third)
            }
        }

        val collected = linkedMapOf<String, AntigravityModelInfo>()

        // 1. Query CloudCode PA endpoints ONLY when CloudCode PA is enabled
        if (allowCloudCodePa && token.isNotBlank()) {
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
                cachedRemoteModels = Triple(cacheKey, System.currentTimeMillis(), grouped)
                Log.i(TAG, "fetchAvailableModels (${executionMode.name}) merged ${collected.size} variants -> ${grouped.size} clean models from $remoteSucceededCount endpoint(s)")
                return@withContext Result.success(grouped)
            }
        }

        // 2. Query live Local Hub (`agy --hub`) catalog ONLY when Local CLI is enabled
        if (allowLocalCli && collected.isEmpty() && (hubBaseUrl.contains("127.0.0.1") || hubBaseUrl.contains("localhost"))) {
            runCatching {
                val localInspection = inspectLocalHubCatalog(hubBaseUrl, useCache = useCache)
                val runnableLocal = localInspection.candidates.filter { it.isRunnableOnLocalHub() }
                val sourceCandidates = runnableLocal.ifEmpty { localInspection.candidates }
                if (sourceCandidates.isNotEmpty()) {
                    sourceCandidates.forEach { c ->
                        val canonicalId = sanitizeBrokenModelId(
                            c.modelUid?.takeIf { it.isNotBlank() }
                                ?: c.modelSlug.takeIf { it.isNotBlank() && !it.startsWith("model_") }
                                ?: c.protoName
                                ?: c.label
                        )
                        val display = c.label.ifBlank { canonicalId }
                        if (canonicalId.isNotBlank()) {
                            val key = canonicalId.lowercase()
                            val existing = collected[key]
                            if (existing == null) {
                                collected[key] = AntigravityModelInfo(
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
        }

        if (collected.isNotEmpty()) {
            val grouped = groupModelsByThinkingFamily(collected.values.toList())
            cachedRemoteModels = Triple(cacheKey, System.currentTimeMillis(), grouped)
            return@withContext Result.success(grouped)
        }
        if (!allowCloudCodePa) {
            Result.failure(Exception("Local Antigravity CLI catalog empty hai. Ensure local AGY Hub online aur signed-in hai."))
        } else if (token.isNotBlank()) {
            Result.failure(Exception("Could not load supported models for your Antigravity account."))
        } else {
            Result.failure(Exception("Not signed in to Antigravity"))
        }
    }

    private data class CatalogModelCandidate(
        val enumTag: Int? = null,
        val protoName: String? = null,
        val aliasTag: Int? = null,
        val aliasName: String? = null,
        val modelUid: String? = null,
        val rawModelOrAliasJson: String? = null,
        val label: String = "",
        val modelSlug: String = "",
        val isRecommended: Boolean = false,
        val remainingFraction: Double? = null,
        val resetTime: String? = null,
        val supportsThinking: Boolean? = null,
        val tagTitle: String? = null,
        val supportsImages: Boolean? = null,
        val defaultThinkingLevel: String? = null,
        val rawConfigKeys: String? = null
    ) {
        fun isRunnableOnLocalHub(): Boolean {
            if (!rawModelOrAliasJson.isNullOrBlank()) {
                val parsed = runCatching { JSONObject(rawModelOrAliasJson) }.getOrNull()
                if (parsed != null && (parsed.has("model") || parsed.has("alias"))) {
                    return true
                }
            }
            return enumTag != null ||
                aliasTag != null ||
                !aliasName.isNullOrBlank() ||
                (!protoName.isNullOrBlank() && protoName.startsWith("MODEL_"))
        }

        fun uniqueKey(): String = listOfNotNull(
            rawModelOrAliasJson?.takeIf { it.isNotBlank() },
            protoName?.takeIf { it.isNotBlank() },
            enumTag?.toString(),
            aliasName?.takeIf { it.isNotBlank() }?.let { "alias:$it" },
            aliasTag?.let { "alias:$it" },
            modelUid?.takeIf { it.isNotBlank() }
        ).firstOrNull() ?: label

        fun toModelOrAliasJson(): JSONObject {
            if (!rawModelOrAliasJson.isNullOrBlank()) {
                runCatching {
                    val raw = JSONObject(rawModelOrAliasJson)
                    if (raw.length() > 0 && (raw.has("model") || raw.has("alias"))) {
                        return raw
                    }
                }
            }
            return JSONObject().apply {
                when {
                    enumTag != null -> put("model", enumTag)
                    !protoName.isNullOrBlank() -> put("model", protoName)
                    aliasTag != null -> put("alias", aliasTag)
                    !aliasName.isNullOrBlank() -> put("alias", aliasName)
                    !modelUid.isNullOrBlank() -> put("modelUid", modelUid)
                }
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
            .ifBlank { obj.optString("display_name", "") }
            .ifBlank { obj.optString("name", "") }
            .ifBlank { fallbackKey }

        val moa = obj.optJSONObject("modelOrAlias")
            ?: obj.optJSONObject("model_or_alias")
            ?: obj.optJSONObject("requestedModel")
            ?: obj.optJSONObject("requested_model")
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
        var aliasTag: Int? = null
        var aliasName: String? = null
        when (rawAlias) {
            is Number -> aliasTag = rawAlias.toInt()
            is String -> {
                val trimmed = rawAlias.trim()
                val asInt = trimmed.toIntOrNull()
                if (asInt != null) aliasTag = asInt else if (trimmed.isNotBlank()) aliasName = trimmed
            }
        }

        val explicitUid = moa?.optString("modelUid", "")?.takeIf { it.isNotBlank() }
            ?: moa?.optString("model_uid", "")?.takeIf { it.isNotBlank() }
            ?: obj.optString("modelUid", "").takeIf { it.isNotBlank() }
            ?: obj.optString("model_uid", "").takeIf { it.isNotBlank() }
        val fallbackKeyUid = fallbackKey.takeIf {
            it.isNotBlank() &&
                !it.startsWith("MODEL_") &&
                !it.equals("default", ignoreCase = true)
        }
        val fallbackIdAsUid = obj.optString("id", "").takeIf { it.isNotBlank() && !it.startsWith("MODEL_") }
        val modelUid = explicitUid ?: fallbackIdAsUid ?: fallbackKeyUid

        val rawMoaJson = moa?.takeIf { it.length() > 0 && (it.has("model") || it.has("alias")) }?.toString()

        if (enumTag == null && protoName.isNullOrBlank() && aliasTag == null && aliasName.isNullOrBlank() && modelUid.isNullOrBlank()) {
            return null
        }

        val quota = obj.optJSONObject("quotaInfo") ?: obj.optJSONObject("quota_info") ?: obj.optJSONObject("quota")
        val remaining = quota?.optDouble("remainingFraction", -1.0)?.takeIf { it >= 0.0 }
        val resetTime = quota?.optString("resetTime", "")?.takeIf { it.isNotBlank() }
        val supportsThinking = if (obj.has("supportsThinking")) obj.optBoolean("supportsThinking") else null
        val supportsImages = if (obj.has("supportsImages")) obj.optBoolean("supportsImages") else null
        val tagTitle = obj.optString("tagTitle", "").ifBlank { obj.optString("tag_title", "") }.takeIf { it.isNotBlank() }
        val defaultThinkingLevel = obj.optString("defaultThinkingLevel", "")
            .ifBlank { obj.optString("default_thinking_level", "") }
            .ifBlank { obj.optString("thinkingLevel", "") }
            .ifBlank { obj.optString("reasoningEffort", "") }
            .takeIf { it.isNotBlank() }
        val rawConfigKeys = runCatching {
            val kList = mutableListOf<String>()
            val itKeys = obj.keys()
            while (itKeys.hasNext()) kList.add(itKeys.next())
            kList.joinToString(",")
        }.getOrNull()
        val cleanSlug = sanitizeBrokenModelId(
            modelUid ?: obj.optString("id", "").takeIf { it.isNotBlank() } ?: label.lowercase().replace(" ", "-")
        )

        return CatalogModelCandidate(
            enumTag = enumTag,
            protoName = protoName,
            aliasTag = aliasTag,
            aliasName = aliasName,
            modelUid = modelUid,
            rawModelOrAliasJson = rawMoaJson,
            label = label.ifBlank { protoName ?: aliasName ?: modelUid ?: "model" },
            modelSlug = cleanSlug,
            isRecommended = obj.optBoolean("isRecommended", false) || obj.optBoolean("is_recommended", false),
            remainingFraction = remaining,
            resetTime = resetTime,
            supportsThinking = supportsThinking,
            tagTitle = tagTitle,
            supportsImages = supportsImages,
            defaultThinkingLevel = defaultThinkingLevel,
            rawConfigKeys = rawConfigKeys
        )
    }

    private fun extractCandidatesFromHubJson(body: String, target: MutableList<CatalogModelCandidate>) {
        if (body.isBlank()) return
        runCatching {
            val root = JSONObject(body)
            val userStatus = root.optJSONObject("userStatus") ?: root.optJSONObject("user_status")
            val cascadeConfigData = userStatus?.optJSONObject("cascadeModelConfigData")
                ?: userStatus?.optJSONObject("cascade_model_config_data")
                ?: root.optJSONObject("cascadeModelConfigData")
                ?: root.optJSONObject("cascade_model_config_data")

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
                root.optJSONObject("available_models"),
                cascadeConfigData?.optJSONObject("models")
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

    suspend fun preWarmLocalHubCatalog(resolvedHubUrl: String) {
        if (resolvedHubUrl.isBlank()) return
        runCatching { inspectLocalHubCatalog(resolvedHubUrl, useCache = true) }
    }

    fun clearCachedHubInspection(hubUrl: String? = null) {
        if (hubUrl.isNullOrBlank()) {
            cachedHubInspections.clear()
        } else {
            cachedHubInspections.remove(hubUrl)
        }
    }

    private suspend fun inspectLocalHubCatalog(
        resolvedHubUrl: String,
        useCache: Boolean = true
    ): HubInspectionResult = coroutineScope {
        if (useCache) {
            val cached = cachedHubInspections[resolvedHubUrl]
            if (cached != null && (System.currentTimeMillis() - cached.first) < HUB_INSPECTION_TTL_MS) {
                (cached.second as? HubInspectionResult)?.let { hit ->
                    if (hit.candidates.isNotEmpty() && hit.hasValidAuth == true) {
                        return@coroutineScope hit
                    }
                }
            }
        }

        val discovered = mutableListOf<CatalogModelCandidate>()
        var hasValidAuth: Boolean? = null
        val metadataPayload = """{"metadata":{"ideName":"antigravity","extensionName":"antigravity","locale":"en"}}"""

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

        val authDeferred = async(Dispatchers.IO) { postHub("GetAuthStatus") }
        val configDeferred = async(Dispatchers.IO) {
            postHub("GetCascadeModelConfigData", metadataPayload)
                .ifBlank { postHub("GetCascadeModelConfigData", "{}") }
        }
        val userStatusDeferred = async(Dispatchers.IO) {
            postHub("GetUserStatus", metadataPayload)
                .ifBlank { postHub("GetUserStatus", "{}") }
        }
        val availDeferred = async(Dispatchers.IO) {
            postHub("GetAvailableModels", """{"forceRefresh":false}""")
        }

        val authBody = authDeferred.await()
        val configBody = configDeferred.await()
        val userStatusBody = userStatusDeferred.await()
        val availBody = availDeferred.await()

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

        // Order matters: daemon-native runnable configs (GetCascadeModelConfigData & GetUserStatus) FIRST!
        extractCandidatesFromHubJson(configBody, discovered)
        extractCandidatesFromHubJson(userStatusBody, discovered)
        extractCandidatesFromHubJson(availBody, discovered)
        if (discovered.none { it.isRunnableOnLocalHub() } && hasValidAuth == true) {
            extractCandidatesFromHubJson(postHub("GetAvailableModels", """{"forceRefresh":true}"""), discovered)
        }

        // Prioritize runnable entries (enumTag/protoName/alias/rawModelOrAlias) before deduplicating
        val sortedByRunnability = discovered.sortedByDescending { if (it.isRunnableOnLocalHub()) 1 else 0 }
        val deduped = sortedByRunnability.distinctBy { it.uniqueKey() }
        val result = HubInspectionResult(deduped, hasValidAuth)
        if (hasValidAuth == true && deduped.isNotEmpty()) {
            cachedHubInspections[resolvedHubUrl] = System.currentTimeMillis() to result
        }
        val runnableSummary = deduped.filter { it.isRunnableOnLocalHub() }
            .joinToString(", ") { c ->
                val ref = c.protoName ?: c.enumTag?.toString() ?: c.aliasName ?: c.aliasTag?.let { "alias:$it" } ?: c.modelUid ?: "?"
                "${c.label}[$ref]"
            }
        Log.i(TAG, "Local Hub inspection: auth=$hasValidAuth, catalogCount=${deduped.size}, runnable=${deduped.count { it.isRunnableOnLocalHub() }}, models=$runnableSummary")
        if (deduped.isNotEmpty()) {
            logRouteEvent("Local CLI Catalog (${deduped.count { it.isRunnableOnLocalHub() }}/${deduped.size} runnable): ${runnableSummary.ifBlank { "none" }.take(380)}")
        }
        result
    }

    private fun matchesRequestedModelFamily(
        candidateHaystack: String,
        cleanModel: String,
        enumTag: Int? = null,
        protoName: String? = null
    ): Boolean {
        val hay = "$candidateHaystack ${protoName.orEmpty()}".lowercase()
        val req = cleanModel.lowercase()
        val reqSpaced = req.replace("-", " ").replace("_", " ")
        if (hay.contains(req) || hay.contains(reqSpaced)) return true
        return when {
            req.contains("orchestrator") && req.contains("carbon") ->
                (hay.contains("orchestrator") && hay.contains("carbon")) || hay.contains("carbon")
            req.contains("orchestrator") && req.contains("argon") ->
                (hay.contains("orchestrator") && hay.contains("argon")) || hay.contains("argon")
            req.contains("orchestrator") && req.contains("neon") ->
                (hay.contains("orchestrator") && hay.contains("neon")) || hay.contains("neon")
            req.contains("opus") ->
                hay.contains("opus") || (req.contains("claude") && hay.contains("claude"))
            req.contains("claude") || req.contains("sonnet") ->
                hay.contains("claude") || hay.contains("sonnet") || enumTag == 1035 || protoName == "MODEL_PLACEHOLDER_M35"
            req.contains("neon") ->
                hay.contains("neon")
            req.contains("carbon") ->
                hay.contains("carbon") || enumTag == 1670 || protoName == "MODEL_PLACEHOLDER_M670"
            req.contains("argon") ->
                hay.contains("argon") || enumTag == 1421 || protoName == "MODEL_PLACEHOLDER_M421"
            req.contains("hydrogen") ->
                hay.contains("hydrogen")
            req.contains("oss") || req.contains("gpt") ->
                hay.contains("oss") || hay.contains("gpt")
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
        val hay = "${candidate.label} ${candidate.protoName.orEmpty()} ${candidate.aliasName.orEmpty()} ${candidate.modelUid.orEmpty()} ${candidate.modelSlug}".lowercase()
        if (!matchesRequestedModelFamily(hay, cleanModel, candidate.enumTag, candidate.protoName)) {
            return Int.MIN_VALUE
        }

        var score = 0
        // Strongly prioritize candidates that carry a real daemon modelOrAlias (enumTag/protoName/alias)
        if (candidate.isRunnableOnLocalHub()) score += 250
        if (!candidate.rawModelOrAliasJson.isNullOrBlank()) score += 50

        val reqLower = cleanModel.lowercase()
        if (hay.contains(reqLower) || hay.contains(reqLower.replace("-", " "))) score += 80
        val wantsClaude = cleanModel.contains("claude", ignoreCase = true) || cleanModel.contains("sonnet", ignoreCase = true) || cleanModel.contains("opus", ignoreCase = true)
        val wantsNeon = cleanModel.contains("neon", ignoreCase = true)
        val wantsCarbon = cleanModel.contains("carbon", ignoreCase = true)
        val wantsArgon = cleanModel.contains("argon", ignoreCase = true)
        val wantsHydrogen = cleanModel.contains("hydrogen", ignoreCase = true)
        val wantsFlash38 = cleanModel.contains("3.8", ignoreCase = true) || cleanModel.contains("flash", ignoreCase = true)

        fun applyThinkingEffortBonus() {
            when {
                thinkingBudget >= 12000 && (hay.contains("xhigh") || hay.contains("extra")) -> score += 45
                thinkingBudget > 4096 && hay.contains("high") && !hay.contains("xhigh") -> score += 35
                thinkingBudget in 1001..4096 && hay.contains("med") -> score += 35
                thinkingBudget <= 1000 && hay.contains("low") -> score += 35
            }
        }

        when {
            wantsClaude -> {
                if (cleanModel.contains("opus", true) && hay.contains("opus")) score += 120
                if (cleanModel.contains("sonnet", true) && hay.contains("sonnet")) score += 120
                if (hay.contains("claude")) score += 60
                if (hay.contains("5.5") || hay.contains("4.6")) score += 20
                if (candidate.enumTag == 1035 || candidate.protoName == "MODEL_PLACEHOLDER_M35") score += 40
            }
            wantsNeon -> {
                if (hay.contains("neon")) score += 120
                if (hay.contains("orchestrator") == cleanModel.contains("orchestrator", true)) score += 35
                applyThinkingEffortBonus()
            }
            wantsCarbon -> {
                if (hay.contains("carbon")) score += 120
                if (hay.contains("orchestrator") == cleanModel.contains("orchestrator", true)) score += 35
                if (candidate.enumTag == 1670 || candidate.protoName == "MODEL_PLACEHOLDER_M670") score += 40
                applyThinkingEffortBonus()
            }
            wantsArgon -> {
                if (hay.contains("argon")) score += 120
                if (hay.contains("orchestrator") == cleanModel.contains("orchestrator", true)) score += 35
                if (candidate.enumTag == 1421 || candidate.protoName == "MODEL_PLACEHOLDER_M421") score += 40
                applyThinkingEffortBonus()
            }
            wantsHydrogen -> {
                if (hay.contains("hydrogen")) score += 120
                if (cleanModel.contains("sonar", true) && hay.contains("sonar")) score += 50
                applyThinkingEffortBonus()
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
        // Only emit daemon-valid enumTag/protoName fallbacks (never emit invalid modelUid-only objects
        // which Go protojson strips into empty {} -> "plan model not specified").
        return when {
            cleanModel.contains("claude", ignoreCase = true) || cleanModel.contains("sonnet", ignoreCase = true) -> listOf(
                CatalogModelCandidate(1035, "MODEL_PLACEHOLDER_M35", label = "Claude Sonnet 5.5", modelSlug = cleanModel)
            )
            cleanModel.contains("3.8", ignoreCase = true) && cleanModel.contains("flash", ignoreCase = true) -> when {
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
            else -> emptyList()
        }
    }

    private fun buildAntigravityCliToolsDeclaration(context: Context? = null): JSONArray =
        AntigravityToolSuite.buildToolsDeclaration(context)

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
        val availableModels = fetchAvailableModels(
            token = token,
            projectId = effectiveProject,
            hubBaseUrl = "",
            useCache = true,
            executionMode = com.jarvisai.assistant.model.AntigravityExecutionMode.CLOUDCODE_PA_ONLY
        ).getOrDefault(emptyList())
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
                appendLine("=== AGENTRA ANTIGRAVITY AGENT RUNTIME ===")
                appendLine("- ${com.jarvisai.assistant.util.IndiaTimeUtils.buildAiRuntimeClockDirective()}")
                appendLine("- Built-in Agentra Linux Engine installed: $envInstalled (Home: $homePath)")
                appendLine("- Active IDE Workspace Root (Agentra Isolated): $activeProject")
                if (activeFile != null) {
                    appendLine("- Currently Open IDE Editor File: $activeFile (dirty=${ActiveCodeEditorRegistry.isDirty})")
                }
                appendLine("- External com.termux app installed: $extTermuxInstalled, RUN_COMMAND permission granted: $extTermuxPerm, Isolated Bridge Port: 2238")
                appendLine("- Embedded Isolated MCP Bridges Active on 127.0.0.1:2236 & 127.0.0.1:2237 (Full Android OS Control matching Gemini Live Voice, Browser CDP, Filesystem, Terminal PTY, 5-Tier Memory, Google Flow, Skills, Plugins, Custom MCP Servers).")
                appendLine("- Full Android Mobile Control Parity (`agentra-android-mcp` & `android_screen_control`): You have the SAME full phone control as Gemini Live Voice — `android_execute_goal` (multi-step autonomous UI pilot across Instagram, WhatsApp, YouTube, Settings, etc.), `android_take_screenshot` (real visual JPEG + Set-of-Mark badges), `android_inspect_screen` / `android_get_ui_tree`, `android_click_element`, `android_tap`, `android_swipe`, `android_scroll`, `android_focus_input`, `android_type_text`, `android_press_key`, `android_launch_app`, `android_control_hardware`, `android_send_message`, `android_dial_phone`, `android_set_alarm_or_timer`, `android_media_control`, `android_deep_link`, `android_share_file`, `android_get_system_status`, `android_reply_notification`, `android_auto_fill_otp`, and `android_open_accessibility_settings` (with automatic Wireless ADB fallback if Accessibility is disconnected).")
                appendLine("- Full Tool Suite: `run_command` (live streaming + `agentra-mcp` CLI), `read_file`, `write_to_file`, `replace_file_content`, `multi_replace_file_content`, `list_dir`, `grep_search`, `find_by_name`, `read_active_editor`, `open_file_in_ide`, `ask_question`, `web_search`, `fetch_url`, `browser_mcp_tool`, `android_screen_control`, `jarvis_memory`, `spawn_subagent`, `mcp_list_servers`, `mcp_call_tool`, `mcp_create_server`, `mcp_remove_server`, `skill_list_all`, `skill_create_or_update`, `skill_delete`, `plugin_list_all`, `plugin_create_or_update`.")
                appendLine("- Always prefer direct tool execution over claiming inability. Use `ask_question` when requirements are ambiguous. After writing code edits, use `open_file_in_ide` or `run_command` to verify builds/tests.")
                val wantsFullMcpGuide = userPrompt.contains("mcp", ignoreCase = true) ||
                    userPrompt.contains("plugin", ignoreCase = true) ||
                    userPrompt.contains("skill", ignoreCase = true) ||
                    userPrompt.contains("extension", ignoreCase = true) ||
                    userPrompt.contains("tool", ignoreCase = true)
                val liveMcpPrompt = runCatching {
                    com.jarvisai.assistant.mcp.McpServersManager.buildLiveMcpStatusPromptSection(
                        context = context,
                        compact = !wantsFullMcpGuide
                    )
                }.getOrDefault("")
                if (liveMcpPrompt.isNotBlank()) {
                    appendLine()
                    appendLine(liveMcpPrompt)
                }
                val liveSkillsPrompt = runCatching {
                    com.jarvisai.assistant.skills.SkillsAndPluginsManager.buildActiveSkillsPromptSection(context)
                }.getOrDefault("")
                if (liveSkillsPrompt.isNotBlank()) {
                    appendLine()
                    appendLine(liveSkillsPrompt)
                }
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
                                    put("tools", buildAntigravityCliToolsDeclaration(context))
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
                            var turnFinishReason = ""

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
                                                val cand0 = candidates?.optJSONObject(0)
                                                val fr = cand0?.optString("finishReason", "").orEmpty()
                                                if (fr.isNotBlank()) {
                                                    turnFinishReason = fr
                                                }
                                                val parts = cand0
                                                    ?.optJSONObject("content")
                                                    ?.optJSONArray("parts")
                                                if (parts != null) {
                                                    for (pi in 0 until parts.length()) {
                                                        val part = parts.optJSONObject(pi) ?: continue
                                                        val hasThoughtSig = part.has("thoughtSignature") || part.has("thought_signature")
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
                                                        if (part.optBoolean("thought", false)) {
                                                            if (hasThoughtSig) {
                                                                rawModelParts.put(JSONObject(part.toString()))
                                                            }
                                                            if (text.isNotEmpty()) {
                                                                accumulatedThought += text
                                                                thoughtDurationMs = System.currentTimeMillis() - thoughtStart
                                                                emit(AntigravityStreamEvent.ThoughtChunk(accumulatedThought))
                                                            }
                                                        } else if (text.isNotEmpty()) {
                                                            turnText += text
                                                            accumulatedText += text
                                                            emit(AntigravityStreamEvent.TextChunk(accumulatedText))
                                                        } else if (hasThoughtSig) {
                                                            rawModelParts.put(JSONObject(part.toString()))
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
                                if (turnText.isBlank() && accumulatedText.isBlank() && allToolCalls.isEmpty()) {
                                    Log.w(
                                        TAG,
                                        "CloudCode PA ($candidateModel) returned thought-only/empty turn (finishReason=$turnFinishReason, useTools=$useTools, thinking=$includeThinkingPayload)"
                                    )
                                    if (useTools && agentTurn == 0) {
                                        useTools = false
                                        retryWithoutTools = true
                                    } else if (includeThinkingPayload && agentTurn == 0) {
                                        includeThinkingPayload = false
                                        retryWithoutThinking = true
                                    } else {
                                        lastErrorMsg = "CloudCode PA returned empty output (finishReason=${turnFinishReason.ifBlank { "UNKNOWN" }})"
                                    }
                                }
                                break
                            }
                        }

                        if (!abortedByHttpError && (accumulatedText.isNotBlank() || allToolCalls.isNotEmpty())) {
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

        lastCloudCodeError = lastErrorMsg
        if (lastErrorMsg.isNotBlank()) {
            Log.e(TAG, "CloudCode PA attempts failed for $cleanModel: $lastErrorMsg")
        }
        return false
    }

    private fun extractCliJsonError(obj: JSONObject, depth: Int = 0): String? {
        if (depth > 3) return null

        val nestedErrorObj = obj.optJSONObject("error")
            ?: obj.optJSONObject("errorMessage")?.optJSONObject("error")
            ?: obj.optJSONObject("error_message")?.optJSONObject("error")
        val rawErrorVal = obj.opt("error")
        val directErrorStr = if (rawErrorVal is String) rawErrorVal.trim() else ""

        val errMsgFromObj = nestedErrorObj?.let { errObj ->
            errObj.optString("shortError", "")
                .ifBlank { errObj.optString("userErrorMessage", "") }
                .ifBlank { errObj.optString("message", "") }
                .ifBlank { errObj.optString("detail", "") }
                .ifBlank { errObj.optString("status", "") }
                .ifBlank {
                    val c = errObj.optInt("code", 0)
                    if (c >= 400) "HTTP $c" else ""
                }
        }.orEmpty()

        val status = obj.optString("status", "").trim().uppercase()
        val subtype = obj.optString("subtype", "").trim().lowercase()
        val ev = obj.optString("event", "").ifBlank { obj.optString("type", "") }.trim().lowercase()
        val finishReason = obj.optString("finished_reason", "")
            .ifBlank { obj.optString("finish_reason", "") }
            .trim()
            .lowercase()
        val code = obj.optInt("code", 0)
        val topMsg = obj.optString("message", "").trim()
        val isExplicitErrFlag = obj.optBoolean("is_error", false) ||
            obj.optBoolean("isError", false) ||
            ev == "error" ||
            subtype.contains("error") ||
            subtype.contains("fail") ||
            finishReason == "error" ||
            nestedErrorObj != null ||
            directErrorStr.isNotBlank() ||
            code >= 400 ||
            status in setOf(
                "ERROR", "FAILED", "NOT_FOUND", "UNAUTHENTICATED",
                "PERMISSION_DENIED", "INVALID_ARGUMENT", "RESOURCE_EXHAUSTED",
                "UNAVAILABLE", "INTERNAL", "DEADLINE_EXCEEDED", "ABORTED"
            )

        val respCandidates = listOf(
            obj.opt("result"),
            obj.opt("response"),
            obj.opt("output"),
            obj.opt("text")
        )
        for (candidate in respCandidates) {
            when (candidate) {
                is JSONObject -> {
                    extractCliJsonError(candidate, depth + 1)?.let { return it }
                }
                is JSONArray -> {
                    for (i in 0 until candidate.length()) {
                        candidate.optJSONObject(i)?.let { elem ->
                            extractCliJsonError(elem, depth + 1)?.let { return it }
                        }
                    }
                }
                is String -> {
                    val trimmedVal = candidate.trim()
                    if (trimmedVal.startsWith("{") || trimmedVal.startsWith("[")) {
                        extractCliOutputError(trimmedVal, depth + 1)?.let { return it }
                    }
                }
            }
        }

        if (!isExplicitErrFlag) return null

        val plainResp = respCandidates.filterIsInstance<String>()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        return errMsgFromObj
            .ifBlank { directErrorStr }
            .ifBlank { topMsg }
            .ifBlank { plainResp }
            .ifBlank {
                val hint = listOfNotNull(
                    status.takeIf { it.isNotBlank() },
                    code.takeIf { it >= 400 }?.toString()
                ).joinToString(" ")
                if (hint.isNotBlank()) "Native agy CLI error ($hint)" else "Native agy CLI error"
            }
    }

    private fun extractCliOutputError(rawText: String, depth: Int = 0): String? {
        if (depth > 3) return null
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null

        // Strip common error prefixes before JSON payloads (e.g. "Error: {...}" or "API Error: [...]")
        val jsonStartIdx = trimmed.indexOfFirst { it == '{' || it == '[' }
        if (jsonStartIdx >= 0) {
            val prefix = trimmed.substring(0, jsonStartIdx).trim().lowercase()
            val canBeJsonPayload = jsonStartIdx == 0 ||
                prefix.contains("error") ||
                prefix.contains("failed") ||
                prefix.contains("exception") ||
                prefix.contains("http ")
            if (canBeJsonPayload) {
                val jsonSlice = trimmed.substring(jsonStartIdx).trim()
                if (jsonSlice.startsWith("{")) {
                    val obj = runCatching { JSONObject(jsonSlice) }.getOrNull()
                    if (obj != null) {
                        extractCliJsonError(obj, depth + 1)?.let { return it }
                    }
                } else if (jsonSlice.startsWith("[")) {
                    val arr = runCatching { JSONArray(jsonSlice) }.getOrNull()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            arr.optJSONObject(i)?.let { elem ->
                                extractCliJsonError(elem, depth + 1)?.let { return it }
                            }
                        }
                    }
                }
            }
        }

        val lower = trimmed.lowercase()
        val looksLikeBareCliFailure = trimmed.length < 600 && (
            lower.startsWith("error:") ||
                lower.startsWith("api error:") ||
                lower.contains("authentication failed or timed out") ||
                lower.contains("requested entity was not found") ||
                (lower.contains("\"code\"") && lower.contains("\"status\"") && lower.contains("\"message\""))
            )
        return if (looksLikeBareCliFailure) trimmed else null
    }

    private fun jsonBracketDelta(chunk: String, state: IntArray) {
        // state[0] = depth, state[1] = inString (0/1), state[2] = escaped (0/1)
        for (ch in chunk) {
            if (state[2] == 1) {
                state[2] = 0
                continue
            }
            if (state[1] == 1) {
                when (ch) {
                    '\\' -> state[2] = 1
                    '"' -> state[1] = 0
                }
                continue
            }
            when (ch) {
                '"' -> state[1] = 1
                '{', '[' -> state[0]++
                '}', ']' -> if (state[0] > 0) state[0]--
            }
        }
    }

    /**
     * Executes the user's selected model directly via the official local Antigravity CLI binary
     * (`~/.local/bin/agy -p ... --model ... --output-format stream-json`).
     *
     * Used as a pure 100%-local CLI layer whenever `agy --hub` JSON-RPC rejects a model (e.g., Gemini 4
     * `404 NOT_FOUND` on hub RPC), allowing `agy` itself to build its exact native protobuf request
     * without ever touching CloudCode PA or swapping to a different model family.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<AntigravityStreamEvent>.streamViaNativeAgyCliSubprocess(
        context: Context,
        cleanModel: String,
        matchedCandidate: CatalogModelCandidate?,
        promptText: String,
        workspaceDir: String,
        sessionId: String,
        thinkingBudget: Int,
        history: List<ChatMessageEntity>,
        systemInstruction: String
    ): Pair<Boolean, String> {
        val authPrefs = com.jarvisai.assistant.data.preferences.AuthPreferences.getInstance(context)
        val storedAccess = authPrefs.getAccessToken()
        val storedRefresh = authPrefs.getRefreshToken()
        if ((storedAccess.isBlank() || authPrefs.isTokenExpired()) && storedRefresh.isNotBlank()) {
            val refreshed = GoogleOAuthManager().refreshToken(storedRefresh).getOrNull()
            if (refreshed != null && refreshed.accessToken.isNotBlank()) {
                authPrefs.saveTokens(
                    accessToken = refreshed.accessToken,
                    refreshToken = refreshed.refreshToken,
                    email = authPrefs.getUserEmail(),
                    name = authPrefs.getUserName(),
                    pictureUrl = authPrefs.getPictureUrl().takeIf { it.isNotBlank() },
                    expiresInSeconds = refreshed.expiresInSeconds
                )
            }
        }
        LocalServerManager.syncAllAgyCliAuthDirs(context)

        val agyBin = LocalServerManager.resolveAgyBinary(context)
            ?: return false to "Local agy CLI binary not found."
        if (!agyBin.exists() || !agyBin.isFile) {
            return false to "Local agy CLI binary missing at ${agyBin.absolutePath}."
        }
        runCatching {
            agyBin.setExecutable(true, false)
            android.system.Os.chmod(agyBin.absolutePath, 493)
        }

        // Dedicated non-colliding CLI profile directory (never shares lock/IPC files with running `agy --hub`)
        val cliAppDataDir = "${JarvisHubConfig.ISOLATED_APP_DATA_DIR}-cli"
        val homeDir = LocalEnvironmentManager.getHomeDir(context).apply { mkdirs() }
        val prefixDir = LocalEnvironmentManager.getPrefixDir(context).apply { mkdirs() }
        val binDir = File(context.filesDir, "bin").apply { mkdirs() }
        val resolvedWorkDir = LocalEnvironmentManager.resolveWritableWorkingDir(context, workspaceDir)
        val safeWorkDirPath = resolvedWorkDir.absolutePath
        val safePromptText = if (promptText.startsWith("-")) " $promptText" else promptText
        val mcpUrl = JarvisHubConfig.getMcpUrl()

        val effortSuffix = when {
            thinkingBudget > 12000 -> "xhigh"
            thinkingBudget > 6000 -> "high"
            thinkingBudget > 1500 -> "medium"
            thinkingBudget > 0 -> "low"
            else -> ""
        }
        val lowerClean = cleanModel.lowercase()
        val alreadyHasEffortSuffix = lowerClean.endsWith("-low") ||
            lowerClean.endsWith("-medium") ||
            lowerClean.endsWith("-high") ||
            lowerClean.endsWith("-xhigh") ||
            lowerClean.endsWith("-minimal")

        val cliModelCandidates = buildList {
            preferredCliModelArgById[lowerClean]?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (cleanModel.isNotBlank()) add(cleanModel)
            if (!alreadyHasEffortSuffix && effortSuffix.isNotBlank() && cleanModel.isNotBlank()) {
                add("$cleanModel-$effortSuffix")
                if (effortSuffix == "xhigh") add("$cleanModel-high")
            }
            matchedCandidate?.modelUid?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            matchedCandidate?.aliasName?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            matchedCandidate?.label?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            matchedCandidate?.protoName?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.distinct()

        var lastErr = ""
        val ansiEscapeRegex = Regex("""\u001B\[[0-9;]*[A-Za-z]""")
        var abortModelAliasesOnAuthFail = false

        for (modelArg in cliModelCandidates) {
            if (abortModelAliasesOnAuthFail) break
            currentCoroutineContext().ensureActive()

            var includeAppDataDirFlag = true
            var triedWithoutAppDataDir = false
            var includeSkipPermsFlag = true
            var includeAddDirFlag = true
            var useStreamJson = true
            var includeConversationFlag = !cliConversationIds[sessionId].isNullOrBlank()
            var flagRetryCount = 0

            while (flagRetryCount < 5) {
                currentCoroutineContext().ensureActive()
                val args = mutableListOf(agyBin.absolutePath, "-p", safePromptText, "--model", modelArg)
                if (useStreamJson) {
                    args.add("--output-format")
                    args.add("stream-json")
                } else {
                    args.add("--output-format")
                    args.add("json")
                }
                if (includeSkipPermsFlag) {
                    args.add("--dangerously-skip-permissions")
                }
                if (includeAppDataDirFlag) {
                    args.add("--app_data_dir=$cliAppDataDir")
                }
                if (includeAddDirFlag && safeWorkDirPath.isNotBlank()) {
                    args.add("--add-dir=$safeWorkDirPath")
                }
                val savedConvId = if (includeConversationFlag) cliConversationIds[sessionId].orEmpty() else ""
                if (savedConvId.isNotBlank()) {
                    args.add("--conversation=$savedConvId")
                }

                logRouteEvent("Native AGY CLI subprocess: agy -p --model '$modelArg' (streamJson=$useStreamJson, appDataDir=$includeAppDataDirFlag, dir=${if (includeAppDataDirFlag) cliAppDataDir else "default"}, conv=${savedConvId.take(8)})")

                var proc: Process? = null
                val accumulatedText = StringBuilder()
                val accumulatedThought = StringBuilder()
                val pendingPlainLines = StringBuilder()
                val errorLines = mutableListOf<String>()
                var capturedConvId = savedConvId
                var reportedInputTokens = 0
                var reportedOutputTokens = 0
                var reportedCachedTokens = 0
                var reportedTotalTokens = 0
                var streamErrorText = ""
                var sawJsonError = false
                var emittedAnyText = false
                val startMs = System.currentTimeMillis()
                var thoughtStartMs: Long? = null
                var thoughtDurationMs = 0L

                val exitCode: Int = try {
                    val pb = ProcessBuilder(args)
                    pb.directory(resolvedWorkDir)
                    val env = pb.environment()
                    LocalEnvironmentManager.applyIsolatedToolEnvironment(context, env)
                    // Standalone headless `agy -p` must NEVER inherit VS Code host / Hub daemon flags
                    env.remove("AGY_ENABLE_HUB")
                    env.remove("ANTIGRAVITY_VSCODE_HOST")
                    env.remove("ANTIGRAVITY_AUTH_SUCCESS_APP")
                    env.remove("ANTIGRAVITY_CLI_HOST")
                    env["TZ"] = com.jarvisai.assistant.util.IndiaTimeUtils.IST_ZONE_ID
                    if (includeAppDataDirFlag) {
                        env["ANTIGRAVITY_APP_DATA_DIR"] = cliAppDataDir
                        env["XDG_CONFIG_HOME"] = File(homeDir, ".config/$cliAppDataDir").apply { mkdirs() }.absolutePath
                        env["XDG_DATA_HOME"] = File(homeDir, ".local/share/$cliAppDataDir").apply { mkdirs() }.absolutePath
                    } else {
                        env.remove("ANTIGRAVITY_APP_DATA_DIR")
                        env["XDG_CONFIG_HOME"] = File(homeDir, ".config").apply { mkdirs() }.absolutePath
                        env["XDG_DATA_HOME"] = File(homeDir, ".local/share").apply { mkdirs() }.absolutePath
                    }
                    env["LD_LIBRARY_PATH"] = "${prefixDir.absolutePath}/lib:${homeDir.absolutePath}/.local/lib:/data/data/com.termux/files/usr/lib"
                    env["JARVIS_MCP_URL"] = mcpUrl
                    env["ANTIGRAVITY_MCP_URL"] = mcpUrl
                    env["WORKSPACE_DIR"] = safeWorkDirPath
                    env["CI"] = "1"
                    env["AGY_NO_UPDATE_PROMPT"] = "1"
                    env["NO_COLOR"] = "1"
                    env["TERM"] = "dumb"
                    env["GODEBUG"] = "http2client=0,tlsmlkem=0"
                    env["PATH"] = "${binDir.absolutePath}:${homeDir.absolutePath}/.local/bin:${prefixDir.absolutePath}/bin:${prefixDir.absolutePath}/bin/applets:/system/bin:/system/xbin"

                    val started = pb.start()
                    proc = started
                    activeCliProcess = started
                    runCatching { started.outputStream.close() }

                    // Drain stderr concurrently so the subprocess can never block on a full stderr pipe
                    val stderrBuffer = StringBuilder()
                    val stderrThread = Thread {
                        runCatching {
                            started.errorStream.bufferedReader().useLines { lines ->
                                lines.forEach { rawLine ->
                                    if (!AgyCliManager.isAgyStartupLog(rawLine)) {
                                        val trimmed = rawLine.trim()
                                        if (trimmed.isNotBlank()) {
                                            synchronized(stderrBuffer) {
                                                if (stderrBuffer.length < 4096) {
                                                    if (stderrBuffer.isNotEmpty()) stderrBuffer.append('\n')
                                                    stderrBuffer.append(trimmed)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }.apply { isDaemon = true; start() }

                    var lastStepResponseLen = 0
                    var lastStepThoughtLen = 0
                    val jsonBuffer = StringBuilder()
                    val jsonBracketState = intArrayOf(0, 0, 0)

                    suspend fun handleParsedCliJsonObject(parsedJson: JSONObject) {
                        val convCandidate = parsedJson.optString("conversation_id", "")
                            .ifBlank { parsedJson.optString("conversationId", "") }
                            .ifBlank { parsedJson.optJSONObject("step_update")?.optString("conversation_id", "") ?: "" }
                        if (convCandidate.isNotBlank()) {
                            capturedConvId = convCandidate
                        }

                        val usage = parsedJson.optJSONObject("usage")
                        if (usage != null) {
                            reportedInputTokens = usage.optInt("input_tokens", usage.optInt("prompt_tokens", 0))
                            reportedOutputTokens = usage.optInt("output_tokens", usage.optInt("completion_tokens", 0))
                            reportedCachedTokens = usage.optInt("cache_read_tokens", usage.optInt("cached_tokens", 0))
                            reportedTotalTokens = usage.optInt("total_tokens", 0)
                        }

                        // Always intercept JSON errors BEFORE emitting any text
                        extractCliJsonError(parsedJson)?.let { detectedErr ->
                            sawJsonError = true
                            streamErrorText = detectedErr
                            return
                        }

                        val ev = parsedJson.optString("event", "")
                            .ifBlank { parsedJson.optString("type", "") }
                            .lowercase()

                        fun safeScalarResp(key: String): String {
                            val v = parsedJson.opt(key)
                            return if (v is String) v else ""
                        }
                        val topRespStr = safeScalarResp("result")
                            .ifBlank { safeScalarResp("response") }
                            .ifBlank { safeScalarResp("output") }
                            .ifBlank { safeScalarResp("text") }

                        when (ev) {
                            "init" -> { /* metadata captured above */ }
                            "step_update" -> {
                                val su = parsedJson.optJSONObject("step_update") ?: parsedJson
                                val stepObj = su.optJSONObject("step") ?: su
                                val pr = stepObj.optJSONObject("plannerResponse")
                                    ?: stepObj.optJSONObject("planner_response")
                                val respText = pr?.optString("response", "")
                                    ?.ifBlank { pr.optString("modifiedResponse", "") }
                                    ?.ifBlank { stepObj.optString("text", "") }
                                    ?: su.optString("text", "").ifBlank { su.optString("delta", "") }
                                val thoughtText = pr?.optString("thinking", "")
                                    ?.ifBlank { stepObj.optString("thinking", "") }
                                    ?.ifBlank { stepObj.optString("thought", "") }
                                    ?: su.optString("thinking", "").ifBlank { su.optString("thought", "") }

                                val stepNestedErr = extractCliJsonError(stepObj)
                                if (stepNestedErr != null) {
                                    sawJsonError = true
                                    streamErrorText = stepNestedErr
                                    return
                                }

                                if (thoughtText.isNotEmpty()) {
                                    if (thoughtStartMs == null) thoughtStartMs = System.currentTimeMillis()
                                    if (thoughtText.length >= lastStepThoughtLen && thoughtText.startsWith(accumulatedThought.toString())) {
                                        accumulatedThought.setLength(0)
                                        accumulatedThought.append(thoughtText)
                                    } else if (thoughtText != accumulatedThought.toString()) {
                                        accumulatedThought.append(thoughtText)
                                    }
                                    lastStepThoughtLen = accumulatedThought.length
                                    thoughtDurationMs = System.currentTimeMillis() - (thoughtStartMs ?: startMs)
                                    emit(AntigravityStreamEvent.ThoughtChunk(accumulatedThought.toString()))
                                }
                                if (respText.isNotEmpty()) {
                                    val nestedRespErr = extractCliOutputError(respText)
                                    if (nestedRespErr != null) {
                                        sawJsonError = true
                                        streamErrorText = nestedRespErr
                                        return
                                    }
                                    if (respText.length >= lastStepResponseLen && respText.startsWith(accumulatedText.toString())) {
                                        accumulatedText.setLength(0)
                                        accumulatedText.append(respText)
                                    } else if (respText != accumulatedText.toString()) {
                                        accumulatedText.append(respText)
                                    }
                                    lastStepResponseLen = accumulatedText.length
                                    emittedAnyText = true
                                    emit(AntigravityStreamEvent.TextChunk(accumulatedText.toString()))
                                }
                            }
                            "delta", "content_block_delta", "assistant", "message", "text" -> {
                                val deltaObj = parsedJson.optJSONObject("delta")
                                val msgObj = parsedJson.optJSONObject("message")
                                val thoughtPart = deltaObj?.optString("thinking", "")
                                    ?.ifBlank { parsedJson.optString("thinking", "") }
                                    ?.ifBlank { parsedJson.optString("thought", "") }
                                    .orEmpty()
                                val textPart = deltaObj?.optString("text", "")
                                    ?.ifBlank { parsedJson.optString("text", "") }
                                    ?.ifBlank { parsedJson.optString("content", "") }
                                    ?.ifBlank { msgObj?.optString("content", "") ?: "" }
                                    .orEmpty()
                                if (thoughtPart.isNotEmpty()) {
                                    if (thoughtStartMs == null) thoughtStartMs = System.currentTimeMillis()
                                    accumulatedThought.append(thoughtPart)
                                    thoughtDurationMs = System.currentTimeMillis() - (thoughtStartMs ?: startMs)
                                    emit(AntigravityStreamEvent.ThoughtChunk(accumulatedThought.toString()))
                                }
                                if (textPart.isNotEmpty()) {
                                    accumulatedText.append(textPart)
                                    emittedAnyText = true
                                    emit(AntigravityStreamEvent.TextChunk(accumulatedText.toString()))
                                }
                            }
                            "result" -> {
                                if (topRespStr.isNotBlank() && accumulatedText.isBlank()) {
                                    accumulatedText.append(topRespStr)
                                    emittedAnyText = true
                                    emit(AntigravityStreamEvent.TextChunk(accumulatedText.toString()))
                                }
                            }
                            else -> {
                                if (topRespStr.isNotBlank() && accumulatedText.isBlank()) {
                                    accumulatedText.append(topRespStr)
                                    emittedAnyText = true
                                    emit(AntigravityStreamEvent.TextChunk(accumulatedText.toString()))
                                }
                            }
                        }
                    }

                    suspend fun tryConsumeBufferedJson(rawCandidate: String): Boolean {
                        val candidate = rawCandidate.trim()
                        val startIdx = candidate.indexOfFirst { it == '{' || it == '[' }
                        if (startIdx < 0) return false
                        val jsonPart = candidate.substring(startIdx).trim()
                        if (jsonPart.startsWith("{")) {
                            val obj = runCatching { JSONObject(jsonPart) }.getOrNull() ?: return false
                            handleParsedCliJsonObject(obj)
                            return true
                        }
                        if (jsonPart.startsWith("[")) {
                            val arr = runCatching { JSONArray(jsonPart) }.getOrNull() ?: return false
                            var handled = false
                            for (i in 0 until arr.length()) {
                                arr.optJSONObject(i)?.let { elem ->
                                    handled = true
                                    handleParsedCliJsonObject(elem)
                                }
                            }
                            if (!handled) {
                                extractCliOutputError(jsonPart)?.let { err ->
                                    sawJsonError = true
                                    streamErrorText = err
                                    handled = true
                                }
                            }
                            return handled
                        }
                        return false
                    }

                    started.inputStream.bufferedReader().use { reader ->
                        var rawLine: String?
                        while (reader.readLine().also { rawLine = it } != null) {
                            currentCoroutineContext().ensureActive()
                            val line = rawLine ?: continue
                            if (AgyCliManager.isAgyStartupLog(line)) continue
                            val cleanLine = line.replace(ansiEscapeRegex, "").replace("\uFEFF", "")
                            val trimmed = cleanLine.trim()

                            // Continue accumulating an in-flight multi-line JSON object/array
                            if (jsonBuffer.isNotEmpty()) {
                                if (jsonBuffer.length < 524_288) {
                                    if (jsonBuffer.isNotEmpty()) jsonBuffer.append('\n')
                                    jsonBuffer.append(trimmed)
                                }
                                jsonBracketDelta(trimmed, jsonBracketState)
                                if (jsonBracketState[0] <= 0) {
                                    val fullJsonBlock = jsonBuffer.toString()
                                    jsonBuffer.setLength(0)
                                    jsonBracketState.fill(0)
                                    if (!tryConsumeBufferedJson(fullJsonBlock)) {
                                        extractCliOutputError(fullJsonBlock)?.let {
                                            sawJsonError = true
                                            streamErrorText = it
                                        }
                                    }
                                }
                                continue
                            }

                            val firstJsonIdx = trimmed.indexOfFirst { it == '{' || it == '[' }
                            val startsJsonOrPrefixedError = firstJsonIdx == 0 || (
                                firstJsonIdx > 0 && trimmed.substring(0, firstJsonIdx).lowercase().let { p ->
                                    p.contains("error") || p.contains("failed") || p.contains("http ")
                                }
                            )
                            if (startsJsonOrPrefixedError) {
                                val slice = trimmed.substring(firstJsonIdx)
                                jsonBracketState.fill(0)
                                jsonBracketDelta(slice, jsonBracketState)
                                if (jsonBracketState[0] <= 0) {
                                    jsonBracketState.fill(0)
                                    if (tryConsumeBufferedJson(trimmed)) {
                                        continue
                                    }
                                    // Malformed JSON starting with '{'/'[': never stream blindly as chat text
                                    extractCliOutputError(trimmed)?.let {
                                        sawJsonError = true
                                        streamErrorText = it
                                        continue
                                    }
                                } else {
                                    jsonBuffer.append(trimmed)
                                    continue
                                }
                            }

                            // Defer lines resembling bare error banners instead of streaming immediately as model reply
                            val lowerTrim = trimmed.lowercase()
                            if (lowerTrim.startsWith("error:") || lowerTrim.startsWith("api error:") || lowerTrim.startsWith("http 4") || lowerTrim.startsWith("http 5")) {
                                if (pendingPlainLines.isNotEmpty()) pendingPlainLines.append('\n')
                                pendingPlainLines.append(cleanLine)
                                continue
                            }

                            // Plain-text fallback line (preserves intentional blank lines!)
                            if (pendingPlainLines.isNotEmpty()) {
                                if (accumulatedText.isNotEmpty()) accumulatedText.append('\n')
                                accumulatedText.append(pendingPlainLines)
                                pendingPlainLines.setLength(0)
                            }
                            if (accumulatedText.isNotEmpty()) {
                                accumulatedText.append('\n')
                            }
                            if (cleanLine.isNotEmpty() || accumulatedText.isNotEmpty()) {
                                accumulatedText.append(cleanLine)
                                if (trimmed.isNotEmpty()) {
                                    emittedAnyText = true
                                    emit(AntigravityStreamEvent.TextChunk(accumulatedText.toString()))
                                }
                            }
                        }

                        // Flush any trailing unclosed buffer safely
                        if (jsonBuffer.isNotEmpty()) {
                            val leftover = jsonBuffer.toString()
                            jsonBuffer.setLength(0)
                            if (!tryConsumeBufferedJson(leftover)) {
                                extractCliOutputError(leftover)?.let {
                                    sawJsonError = true
                                    streamErrorText = it
                                }
                            }
                        }
                        if (pendingPlainLines.isNotEmpty()) {
                            val leftoverPlain = pendingPlainLines.toString().trim()
                            pendingPlainLines.setLength(0)
                            val plainErr = extractCliOutputError(leftoverPlain)
                            if (plainErr != null) {
                                sawJsonError = true
                                if (streamErrorText.isBlank()) streamErrorText = plainErr
                            } else if (leftoverPlain.isNotBlank()) {
                                if (accumulatedText.isNotEmpty()) accumulatedText.append('\n')
                                accumulatedText.append(leftoverPlain)
                            }
                        }
                    }

                    val finished = started.waitFor(180, TimeUnit.SECONDS)
                    if (!finished) {
                        started.destroyForcibly()
                        errorLines.add("Native agy CLI timed out after 180s.")
                    }
                    runCatching { stderrThread.join(1000) }
                    synchronized(stderrBuffer) {
                        if (stderrBuffer.isNotBlank()) {
                            errorLines.add(stderrBuffer.toString())
                        }
                    }
                    if (finished) started.exitValue() else 124
                } catch (e: Exception) {
                    errorLines.add(e.message ?: "agy subprocess error")
                    -1
                } finally {
                    runCatching { proc?.destroyForcibly() }
                    if (activeCliProcess === proc) {
                        activeCliProcess = null
                    }
                }

                val finalText = accumulatedText.toString().trim()
                val rawOutputErr = extractCliOutputError(finalText)
                if (rawOutputErr != null && streamErrorText.isBlank()) {
                    streamErrorText = rawOutputErr
                }

                val combinedErr = listOfNotNull(
                    streamErrorText.takeIf { it.isNotBlank() },
                    errorLines.joinToString(" | ").takeIf { it.isNotBlank() }
                ).joinToString(" | ").trim()

                if (finalText.isNotBlank() &&
                    rawOutputErr == null &&
                    !sawJsonError &&
                    streamErrorText.isBlank() &&
                    exitCode == 0
                ) {
                    preferredCliModelArgById[lowerClean] = modelArg
                    if (capturedConvId.isNotBlank()) {
                        cliConversationIds[sessionId] = capturedConvId
                    }
                    val historyChars = history.sumOf { it.content.length } + systemInstruction.length + safePromptText.length
                    val estPrompt = (historyChars / 4).coerceAtLeast(1)
                    val estOutput = ((finalText.length + accumulatedThought.length) / 4).coerceAtLeast(1)
                    val finalPrompt = if (reportedInputTokens > 0) reportedInputTokens else estPrompt
                    val finalOutput = if (reportedOutputTokens > 0) reportedOutputTokens else estOutput
                    val finalTotal = if (reportedTotalTokens > 0) reportedTotalTokens else (finalPrompt + finalOutput)
                    logRouteEvent("Completed via LOCAL_AGY_CLI_SUBPROCESS (agy -p --model '$modelArg', chars=${finalText.length})")
                    emit(
                        AntigravityStreamEvent.Completed(
                            totalTokens = finalTotal,
                            fullText = finalText,
                            fullThought = accumulatedThought.toString(),
                            thoughtDurationMs = thoughtDurationMs.takeIf { it > 0L } ?: (System.currentTimeMillis() - startMs),
                            modelUsed = cleanModel,
                            toolCalls = emptyList(),
                            promptTokens = finalPrompt,
                            outputTokens = finalOutput,
                            cachedTokens = reportedCachedTokens
                        )
                    )
                    return true to ""
                }

                lastErr = combinedErr.ifBlank { "agy CLI exited with code $exitCode for model '$modelArg'" }
                logRouteEvent("Native AGY CLI attempt failed (model='$modelArg', exit=$exitCode): ${lastErr.take(220)}")

                // Automatically strip any single unrecognized CLI flag and retry immediately
                val isUnknownFlag = lastErr.contains("unknown flag", ignoreCase = true) ||
                    lastErr.contains("flag provided but not defined", ignoreCase = true) ||
                    lastErr.contains("unrecognized option", ignoreCase = true)
                if (isUnknownFlag) {
                    flagRetryCount++
                    when {
                        includeAppDataDirFlag && lastErr.contains("app_data_dir", ignoreCase = true) -> includeAppDataDirFlag = false
                        includeSkipPermsFlag && lastErr.contains("dangerously-skip-permissions", ignoreCase = true) -> includeSkipPermsFlag = false
                        includeAddDirFlag && lastErr.contains("add-dir", ignoreCase = true) -> includeAddDirFlag = false
                        includeConversationFlag && lastErr.contains("conversation", ignoreCase = true) -> {
                            includeConversationFlag = false
                            cliConversationIds.remove(sessionId)
                        }
                        useStreamJson && lastErr.contains("output-format", ignoreCase = true) -> useStreamJson = false
                        else -> break
                    }
                    continue
                }

                // If `agy -p` failed auth/timeout with `--app_data_dir`, retry once without `--app_data_dir`
                // so it picks up credentials mirrored to ~/.gemini/antigravity & default XDG paths
                val isAuthTimeoutErr = lastErr.contains("authentication failed", ignoreCase = true) ||
                    lastErr.contains("unauthenticated", ignoreCase = true) ||
                    lastErr.contains("not signed in", ignoreCase = true) ||
                    lastErr.contains("timed out", ignoreCase = true)
                if (isAuthTimeoutErr) {
                    if (includeAppDataDirFlag && !triedWithoutAppDataDir) {
                        includeAppDataDirFlag = false
                        triedWithoutAppDataDir = true
                        flagRetryCount++
                        continue
                    } else {
                        abortModelAliasesOnAuthFail = true
                        break
                    }
                }

                if (includeConversationFlag && (lastErr.contains("conversation", ignoreCase = true) || lastErr.contains("404", ignoreCase = true))) {
                    cliConversationIds.remove(sessionId)
                    includeConversationFlag = false
                    flagRetryCount++
                    continue
                }

                break
            }
        }

        return false to lastErr
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
        resolvedAttachments: List<ResolvedAttachmentPart> = emptyList(),
        executionMode: com.jarvisai.assistant.model.AntigravityExecutionMode = com.jarvisai.assistant.model.AntigravityExecutionMode.HYBRID_BOTH
    ): Flow<AntigravityStreamEvent> = flow {

        val cleanModel = sanitizeBrokenModelId(modelId.removePrefix("agy:").trim())
        val resolvedHubUrl = com.jarvisai.assistant.data.pc.PcConnect.getActiveHubBaseUrl()
            ?: hubBaseUrl.ifBlank { JarvisHubConfig.getLanguageServiceUrl() }
        val hubPort = JarvisHubConfig.getHubPort()
        val allowLocalCli = executionMode.cliEnabled
        val allowCloudCodePa = executionMode.cloudCodePaEnabled

        try {
            // STRICT CLOUDCODE PA ONLY MODE — NEVER touch Local Antigravity CLI Hub!
            if (!allowLocalCli && allowCloudCodePa) {
                logRouteEvent("Mode=CLOUDCODE_PA_ONLY | Model=$cleanModel -> Direct CloudCode PA (Local AGY CLI disabled)")
                if (token.isBlank()) {
                    emit(AntigravityStreamEvent.Error("CloudCode PA Only mode ON hai, lekin Antigravity OAuth sign-in nahi hai. Menu -> Antigravity OAuth se sign-in karein."))
                    return@flow
                }
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
                if (cloudOk) {
                    logRouteEvent("Completed via CLOUDCODE_PA (Strict CloudCode PA Only mode)")
                    return@flow
                }
                val failMsg = lastCloudCodeError.ifBlank {
                    "CloudCode PA Only mode: '$cleanModel' par response fail hua. Kripya OAuth ya network check karein."
                }
                emit(AntigravityStreamEvent.Error(failMsg))
                return@flow
            }

            var localHubReady = true
            // Ensure local Antigravity Hub daemon on isolated port is awake if running on-device
            if (resolvedHubUrl.contains("127.0.0.1") || resolvedHubUrl.contains("localhost")) {
                localHubReady = com.jarvisai.assistant.terminal.LocalServerManager.isAgyHubReachable() ||
                    com.jarvisai.assistant.terminal.LocalServerManager.warmUpAgyHub(
                        maxWaitMs = if (!allowCloudCodePa) 8000L else if (token.isNotBlank()) 5000L else 6000L
                    )
                if (!localHubReady && (!allowCloudCodePa || token.isBlank())) {
                    delay(500)
                    localHubReady = com.jarvisai.assistant.terminal.LocalServerManager.warmUpAgyHub(maxWaitMs = 5000L)
                }
            }

            // STRICT CLI ONLY MODE: Fail cleanly if Local Hub is offline (NEVER fall back to CloudCode PA!)
            if (!allowCloudCodePa && !localHubReady) {
                logRouteEvent("Mode=CLI_ONLY | Local AGY Hub offline at 127.0.0.1:$hubPort (CloudCode PA fallback blocked)")
                emit(AntigravityStreamEvent.Error("Antigravity CLI Only mode ON hai, lekin Local AGY Hub (127.0.0.1:$hubPort) offline hai. Local Server start karein."))
                return@flow
            }

            val hasBinaryAttachments = resolvedAttachments.any {
                !it.base64Data.isNullOrBlank() || it.extraFrameBase64List.isNotEmpty()
            }

            // In HYBRID_BOTH mode only: if local daemon isn't running or binary media is attached and OAuth token exists, route via CloudCode PA
            if (allowCloudCodePa && (!localHubReady || hasBinaryAttachments) && token.isNotBlank()) {
                val reason = if (!localHubReady) "Local Hub unreachable" else "Multimodal binary attachment"
                logRouteEvent("Mode=HYBRID_BOTH | Model=$cleanModel -> Routing to CloudCode PA ($reason)")
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
                if (cloudOk) {
                    logRouteEvent("Completed via CLOUDCODE_PA ($reason)")
                    return@flow
                }
            }

            // Inspect live local daemon catalog & auth status (uses fast 3-minute cache once online)
            var inspection = inspectLocalHubCatalog(resolvedHubUrl, useCache = true)
            if (inspection.candidates.isEmpty() && (!allowCloudCodePa || token.isBlank())) {
                delay(800)
                inspection = inspectLocalHubCatalog(resolvedHubUrl, useCache = false)
            }

            // Auto-heal: if local agy --hub reports unsigned while the user IS signed in via Agentra's Google OAuth,
            // write the exact nested Go oauth2.Token format to ~/.gemini/jetski-standalone-oauth-token (which agy
            // re-reads on every GetAuthStatus call, per AntiGem's agy_accounts.go) and re-check immediately!
            if (inspection.hasValidAuth == false && context != null && (resolvedHubUrl.contains("127.0.0.1") || resolvedHubUrl.contains("localhost"))) {
                val authPrefs = com.jarvisai.assistant.data.preferences.AuthPreferences.getInstance(context)
                val hasAppOAuth = token.isNotBlank() || authPrefs.getAccessToken().isNotBlank() || authPrefs.getRefreshToken().isNotBlank()
                if (hasAppOAuth) {
                    logRouteEvent("Local AGY Hub unsigned while Google OAuth is active -> Auto-syncing nested jetski-standalone-oauth-token & re-checking")
                    com.jarvisai.assistant.terminal.LocalServerManager.syncAuthPreferencesToAgyDirs(
                        context = context,
                        forceOverwrite = true,
                        overrideAccessToken = token.takeIf { it.isNotBlank() }
                    )
                    delay(350)
                    inspection = inspectLocalHubCatalog(resolvedHubUrl, useCache = false)
                    if (inspection.hasValidAuth == true) {
                        logRouteEvent("Auto-healed Local AGY Hub auth! Local daemon is now signed in.")
                        com.jarvisai.assistant.terminal.LocalServerManager.verifyRealHubAuthNow(context)
                    }
                }
            }

            if (!allowCloudCodePa && inspection.hasValidAuth == false) {
                context?.let { com.jarvisai.assistant.terminal.LocalServerManager.verifyRealHubAuthNow(it) }
                logRouteEvent("Mode=CLI_ONLY | Local AGY Hub not signed in (CloudCode PA fallback blocked)")
                emit(
                    AntigravityStreamEvent.Error(
                        "Antigravity CLI Only mode ON hai, lekin Local AGY CLI daemon signed-in nahi hai. " +
                            "Menu → Antigravity Account mein 'Sign in Local AGY CLI' par tap karein (ya Settings → Antigravity → AI Execution Engine mein 'Both' mode ON karein)."
                    )
                )
                return@flow
            }

            // In HYBRID_BOTH mode only: if local hub is unauthenticated or lacks a model catalog and Cloud OAuth token exists, route to CloudCode PA
            if (allowCloudCodePa && token.isNotBlank() && (inspection.hasValidAuth == false || inspection.candidates.isEmpty())) {
                val reason = if (inspection.hasValidAuth == false) "Local Hub unsigned" else "Local Hub catalog empty"
                logRouteEvent("Mode=HYBRID_BOTH | Model=$cleanModel -> Failover to CloudCode PA ($reason)")
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
                if (cloudOk) {
                    logRouteEvent("Completed via CLOUDCODE_PA ($reason)")
                    return@flow
                }
                val failMsg = lastCloudCodeError.ifBlank {
                    "Local Antigravity Hub signed out hai aur CloudCode PA stream fail hua. Menu -> '☁️ Antigravity OAuth' se re-sign in karein."
                }
                emit(AntigravityStreamEvent.Error(failMsg))
                return@flow
            }

            var matchingLive = inspection.candidates
                .filter { it.isRunnableOnLocalHub() }
                .map { it to scoreCatalogCandidate(it, cleanModel, thinkingBudget) }
                .filter { it.second > Int.MIN_VALUE }
                .sortedByDescending { it.second }
                .map { it.first }

            // If cached inspection didn't find a runnable match for this model, force one live refresh from Local AGY Hub
            if (matchingLive.isEmpty()) {
                inspection = inspectLocalHubCatalog(resolvedHubUrl, useCache = false)
                matchingLive = inspection.candidates
                    .filter { it.isRunnableOnLocalHub() }
                    .map { it to scoreCatalogCandidate(it, cleanModel, thinkingBudget) }
                    .filter { it.second > Int.MIN_VALUE }
                    .sortedByDescending { it.second }
                    .map { it.first }
            }

            val fallbackHardcoded = buildDefaultHardcodedCandidates(cleanModel, thinkingBudget)
                .filter { it.isRunnableOnLocalHub() }
            val candidateQueue: List<CatalogModelCandidate> = (matchingLive + fallbackHardcoded)
                .distinctBy { it.uniqueKey() }
                .take(5)

            if (candidateQueue.isEmpty()) {
                val availableLabels = inspection.candidates
                    .filter { it.isRunnableOnLocalHub() }
                    .map { stripThinkingSuffixFromDisplay(it.label, it.modelSlug) }
                    .distinct()
                    .take(8)
                // Even if Local Hub catalog didn't expose a runnable entry yet, try direct native agy CLI subprocess on-device!
                if (allowLocalCli && context != null && (resolvedHubUrl.contains("127.0.0.1") || resolvedHubUrl.contains("localhost"))) {
                    val wsFallback = ActiveCodeEditorRegistry.currentProjectPath?.takeIf { it.isNotBlank() && File(it).exists() }
                        ?: LocalEnvironmentManager.getIsolatedProjectsDir(context).absolutePath
                    val (cliOk, cliErr) = streamViaNativeAgyCliSubprocess(
                        context = context,
                        cleanModel = cleanModel,
                        matchedCandidate = null,
                        promptText = userPrompt,
                        workspaceDir = wsFallback,
                        sessionId = sessionId,
                        thinkingBudget = thinkingBudget,
                        history = history,
                        systemInstruction = systemInstruction
                    )
                    if (cliOk) {
                        return@flow
                    }
                    if (cliErr.isNotBlank()) {
                        logRouteEvent("Native AGY CLI fallback (empty catalog) failed: ${cliErr.take(200)}")
                    }
                }
                if (allowCloudCodePa && token.isNotBlank()) {
                    logRouteEvent("Mode=HYBRID_BOTH | Model=$cleanModel not in Local CLI catalog (${availableLabels.joinToString(", ")}) -> Direct CloudCode PA")
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
                    if (cloudOk) {
                        logRouteEvent("Completed via CLOUDCODE_PA (Model not in Local CLI catalog)")
                        return@flow
                    }
                    emit(AntigravityStreamEvent.Error(lastCloudCodeError.ifBlank { "Model '$cleanModel' CloudCode PA par run nahi ho paya." }))
                    return@flow
                } else {
                    val availText = if (availableLabels.isNotEmpty()) {
                        "Local AGY CLI par abhi ye models available hain: ${availableLabels.joinToString(", ")}. '$cleanModel' use karne ke liye CloudCode PA ya Both mode ON karein."
                    } else {
                        "Local AGY CLI catalog mein '$cleanModel' nahi mila. Antigravity CLI update/sign-in check karein ya CloudCode PA / Both mode ON karein."
                    }
                    logRouteEvent("Mode=CLI_ONLY | Model=$cleanModel not present in Local AGY CLI runnable catalog (${availableLabels.joinToString(", ")})")
                    emit(AntigravityStreamEvent.Error("Antigravity CLI Only mode: $availText"))
                    return@flow
                }
            }

            logRouteEvent(
                "Mode=${executionMode.name} | Model=$cleanModel -> Executing on Local AGY CLI Hub ($resolvedHubUrl, catalog=${inspection.candidates.size}, target=${candidateQueue.first().label} ${candidateQueue.first().toModelOrAliasJson()})"
            )

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

            if (resolvedContext != null && com.jarvisai.assistant.mcp.McpServersManager.servers.value.isEmpty()) {
                LocalServerManager.provisionEmbeddedMcpConfig(resolvedContext)
                com.jarvisai.assistant.mcp.McpServersManager.notifyLocalAgyDaemonRefresh(resolvedContext)
            }

            val mcpServersArr = buildMcpServersConfigArray(resolvedContext)

            val lowerPrompt = userPrompt.lowercase()
            val isMcpOrSkillIntent = lowerPrompt.contains("mcp") ||
                lowerPrompt.contains("plugin") ||
                lowerPrompt.contains("skill") ||
                lowerPrompt.contains("extension") ||
                lowerPrompt.contains("server") ||
                lowerPrompt.contains("tool")
            val isActionOrWorkspaceTurn = resolvedAttachments.isNotEmpty() ||
                activeEditorFile != null ||
                isMcpOrSkillIntent ||
                listOf(
                    "file", "folder", "dir", "code", "edit", "write", "create", "fix", "debug", "build",
                    "compile", "test", "run ", "command", "terminal", "shell", "bash", "git", "npm", "pip",
                    "python", "gradle", "kotlin", "android", "browser", "web", "search", "open ", "click",
                    "screenshot", "termux", "install", "script", "project", "app ", "bana", "likh", "check",
                    "karo", "kardo", "banao", "chalao", "dekho", "repo", "github", "api", "/"
                ).any { lowerPrompt.contains(it) }

            var lastBuiltHubPromptText = userPrompt
            val rejectedHubPayloads = mutableSetOf<String>()

            for (currentSpec in candidateQueue) {
                if (cascadeCompleted || abortLocalHubToCloudFallback) break
                currentCoroutineContext().ensureActive()

                val requestedModelJson = currentSpec.toModelOrAliasJson()
                val requestedModelKey = requestedModelJson.toString()
                if (rejectedHubPayloads.contains(requestedModelKey)) {
                    logRouteEvent("Skipping duplicate Hub payload $requestedModelKey (already rejected across profiles)")
                    continue
                }

                val isGemini4Family = cleanModel.contains("gemini-4", ignoreCase = true) ||
                    cleanModel.contains("carbon", ignoreCase = true) ||
                    cleanModel.contains("argon", ignoreCase = true) ||
                    cleanModel.contains("hydrogen", ignoreCase = true) ||
                    cleanModel.contains("neon", ignoreCase = true) ||
                    cleanModel.contains("orchestrator", ignoreCase = true) ||
                    currentSpec.label.contains("Gemini 4", ignoreCase = true)

                val profileCacheKey = "$resolvedHubUrl|${if (isGemini4Family) "g4" else "std"}"
                // Profile 0 = Clean Standard Cascade (no useCoreDirect, no customAgentSpec, no useAiCredits=false, no thinkingBudget)
                // Profile 1 = Bare Minimal Cascade (only requestedModel + conversational)
                // Profile 2 = Clean Cascade + planModel + thinkingLevel/Budget (no useCoreDirect/customAgentSpec)
                // Profile 3 = Legacy CoreDirect Cascade (proven working for Gemini 3.8 Flash)
                val baseProfileOrder = if (isGemini4Family) listOf(0, 1, 2, 3) else listOf(3, 0, 1, 2)
                val cachedProfile = preferredSendProfileByHubModel[profileCacheKey]
                val orderedProfiles = if (cachedProfile != null && cachedProfile in baseProfileOrder) {
                    listOf(cachedProfile) + baseProfileOrder.filter { it != cachedProfile }
                } else {
                    baseProfileOrder
                }
                val planModelEnumValue: Any? = currentSpec.enumTag
                    ?: currentSpec.protoName?.takeIf { it.isNotBlank() && it.startsWith("MODEL_") }
                val mappedThinkingLevel = currentSpec.defaultThinkingLevel?.takeIf { it.isNotBlank() } ?: when {
                    thinkingBudget > 12000 -> if (isGemini4Family) "xhigh" else "high"
                    thinkingBudget > 6000 -> "high"
                    thinkingBudget > 1500 -> "medium"
                    else -> "low"
                }

                val hubMetadataJson = JSONObject().apply {
                    put("ideName", "antigravity")
                    put("extensionName", "antigravity")
                    put("locale", "en")
                }

                for (profile in orderedProfiles) {
                    if (cascadeCompleted || abortLocalHubToCloudFallback) break
                    currentCoroutineContext().ensureActive()

                    var cascadeId = sessionCascadeIds.getOrPut(sessionId) { UUID.randomUUID().toString() }
                    val isFreshLocalCascade = !startedCascades.contains(cascadeId)
                    // 1. Ensure persistent Cascade session is started with Workspace + MCP
                    cascadeId = ensureLocalCascadeStarted(
                        targetCascadeId = cascadeId,
                        sessionId = sessionId,
                        resolvedHubUrl = resolvedHubUrl,
                        workspaceUri = workspaceUri,
                        hubMetadataJson = hubMetadataJson,
                        currentSpec = currentSpec,
                        mcpServersArr = mcpServersArr
                    )

                    val hubPromptText = buildLocalHubPromptText(
                        isFreshLocalCascade = isFreshLocalCascade,
                        isMcpOrSkillIntent = isMcpOrSkillIntent,
                        lowerPrompt = lowerPrompt,
                        userPrompt = userPrompt,
                        history = history,
                        resolvedContext = resolvedContext,
                        activeEditorFile = activeEditorFile,
                        workspaceDir = workspaceDir,
                        resolvedAttachments = resolvedAttachments
                    )
                    lastBuiltHubPromptText = hubPromptText

                    val itemsArray = JSONArray().apply {
                        put(JSONObject().put("text", hubPromptText))
                        if (activeEditorFile != null) {
                            put(JSONObject().put("fileUri", "file://$activeEditorFile"))
                        }
                    }

                    val effectiveAgenticMode = isGemini4Family || isActionOrWorkspaceTurn

                    var activeCid = cascadeId
                    // Skip pre-send GetCascadeTrajectory on fresh cascades (known 0 baseline steps)
                    val baselineStepCount: Int = if (isFreshLocalCascade) {
                        0
                    } else {
                        runCatching {
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
                    }

                    val sendPayload = buildLocalCascadeSendPayload(
                        targetCascadeId = activeCid,
                        profile = profile,
                        itemsArray = itemsArray,
                        workspaceUri = workspaceUri,
                        workspaceDir = workspaceDir,
                        hubMetadataJson = hubMetadataJson,
                        requestedModelJson = requestedModelJson,
                        planModelEnumValue = planModelEnumValue,
                        thinkingBudget = thinkingBudget,
                        isGemini4Family = isGemini4Family,
                        mappedThinkingLevel = mappedThinkingLevel,
                        effectiveAgenticMode = effectiveAgenticMode,
                        isActionOrWorkspaceTurn = isActionOrWorkspaceTurn,
                        isFreshLocalCascade = isFreshLocalCascade,
                        mcpServersArr = mcpServersArr
                    )
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
                        activeCid = ensureLocalCascadeStarted(
                            targetCascadeId = freshCid,
                            sessionId = sessionId,
                            resolvedHubUrl = resolvedHubUrl,
                            workspaceUri = workspaceUri,
                            hubMetadataJson = hubMetadataJson,
                            currentSpec = currentSpec,
                            mcpServersArr = mcpServersArr
                        )
                        val retryPayload = buildLocalCascadeSendPayload(
                            targetCascadeId = activeCid,
                            profile = profile,
                            itemsArray = itemsArray,
                            workspaceUri = workspaceUri,
                            workspaceDir = workspaceDir,
                            hubMetadataJson = hubMetadataJson,
                            requestedModelJson = requestedModelJson,
                            planModelEnumValue = planModelEnumValue,
                            thinkingBudget = thinkingBudget,
                            isGemini4Family = isGemini4Family,
                            mappedThinkingLevel = mappedThinkingLevel,
                            effectiveAgenticMode = effectiveAgenticMode,
                            isActionOrWorkspaceTurn = isActionOrWorkspaceTurn,
                            isFreshLocalCascade = isFreshLocalCascade,
                            mcpServersArr = mcpServersArr
                        )
                        val retryReq = Request.Builder()
                            .url("$resolvedHubUrl/SendUserCascadeMessage")
                            .post(retryPayload.toString().toRequestBody("application/json".toMediaType()))
                            .build()
                        sendResp = AgyOkHttpClient.client.newCall(retryReq).execute()
                    }

                    if (!sendResp.isSuccessful) {
                        val respCode = sendResp.code
                        val err = sendResp.body?.string().orEmpty()
                        sendResp.close()
                        startedCascades.remove(activeCid)
                        sessionCascadeIds.remove(sessionId)
                        lastDaemonError = "Antigravity Hub Send Error (HTTP $respCode): $err"
                        Log.w(TAG, "SendUserCascadeMessage profile=$profile failed: $lastDaemonError")
                        if (respCode == 400 || respCode == 404) {
                            continue
                        }
                        break
                    }
                    sendResp.close()

                    // 3. Poll Trajectory for Response (Adaptive Polling respecting strict CLI_ONLY vs HYBRID_BOTH mode)
                    var retryNextProfile = false
                    var retryNextCandidate = false
                    var turnFinished = false
                val maxIdleSeconds = 120
                val maxRunningIdleSeconds = 480
                val maxNoOutputStallMs = when {
                    !allowCloudCodePa -> 45_000L
                    token.isNotBlank() -> 22_000L
                    else -> 30_000L
                }
                val maxTotalSeconds = 900
                val startTimestamp = System.currentTimeMillis()
                var lastActivityTimestamp = System.currentTimeMillis()
                var lastRealProgressMs = System.currentTimeMillis()
                var lastPollHadRunningWork = false
                var thoughtStartTimestamp: Long? = null
                var thoughtDurationMs: Long = 0L

                var pollPayload = JSONObject().put("cascadeId", activeCid).toString()
                val accumulatedTools = linkedMapOf<String, com.jarvisai.assistant.model.ToolCall>()
                val plannerThoughtsByStep = linkedMapOf<String, String>()
                val plannerResponsesByStep = linkedMapOf<String, String>()
                var accumulatedThought = ""
                var accumulatedResponse = ""
                var lastObservedSignature = ""
                var prevObservedResponseLen = 0
                var prevObservedThoughtLen = 0
                var prevObservedToolSig = ""
                var unchangedStablePolls = 0

                var turnBaselineStepCount = baselineStepCount
                val autoContinuedAskSteps = mutableSetOf<String>()
                val lastPermissionAttemptByStep = mutableMapOf<Int, Long>()

                var pollIdx = 0
                while (!turnFinished) {
                    currentCoroutineContext().ensureActive()
                    val pollDelayMs = when {
                        pollIdx < 10 -> 120L
                        pollIdx < 30 -> 220L
                        else -> 350L
                    }
                    delay(pollDelayMs)
                    pollIdx++

                    val now = System.currentTimeMillis()
                    val totalElapsedSeconds = (now - startTimestamp) / 1000
                    val idleElapsedSeconds = (now - lastRealProgressMs) / 1000
                    val hasAnyOutputYet = accumulatedResponse.isNotBlank() || accumulatedThought.isNotBlank() || accumulatedTools.isNotEmpty()

                    if (!hasAnyOutputYet && (now - lastRealProgressMs) >= maxNoOutputStallMs) {
                        Log.w(TAG, "Local daemon stalled ${maxNoOutputStallMs / 1000}s with zero output for cascade $activeCid; cancelling.")
                        cancelCascadeInvocationSilently(resolvedHubUrl, activeCid)
                        startedCascades.remove(activeCid)
                        sessionCascadeIds.remove(sessionId)
                        lastDaemonError = "Local Antigravity Hub timed out without generating a response (${maxNoOutputStallMs / 1000}s)."
                        if (allowCloudCodePa && token.isNotBlank()) {
                            abortLocalHubToCloudFallback = true
                        } else {
                            retryNextCandidate = true
                        }
                        turnFinished = true
                        break
                    }

                    val effectiveIdleLimit = if (lastPollHadRunningWork) maxRunningIdleSeconds else maxIdleSeconds
                    if (idleElapsedSeconds >= effectiveIdleLimit) {
                        Log.w(TAG, "Antigravity Hub idle watchdog triggered at ${idleElapsedSeconds}s (limit=${effectiveIdleLimit}s, hasResp=${accumulatedResponse.isNotBlank()}, tools=${accumulatedTools.size})")
                        cancelCascadeInvocationSilently(resolvedHubUrl, activeCid)
                        startedCascades.remove(activeCid)
                        sessionCascadeIds.remove(sessionId)
                        finalizeToolsOnWatchdog(
                            accumulatedTools = accumulatedTools,
                            accumulatedResponse = accumulatedResponse,
                            accumulatedThought = accumulatedThought,
                            thoughtDurationMs = thoughtDurationMs,
                            elapsedMs = now - startTimestamp,
                            userPrompt = userPrompt,
                            cleanModel = cleanModel,
                            effectiveIdleLimit = effectiveIdleLimit
                        )
                        turnFinished = true
                        cascadeCompleted = true
                        break
                    }
                    if (totalElapsedSeconds >= maxTotalSeconds) {
                        emit(AntigravityStreamEvent.Error("Agentra Hub task exceeded global limit of ${maxTotalSeconds}s (15m)."))
                        turnFinished = true
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

                                        val isStepDone = isStepDoneStatus(stepStatus)
                                        val isCascadeExplicitlyIdle = isCascadeIdleStatus(cascadeStatus)
                                        val isStepExplicitlyActive = isStepActiveStatus(
                                            stepStatus = stepStatus,
                                            stepType = stepType,
                                            isStepDone = isStepDone,
                                            isCascadeExplicitlyIdle = isCascadeExplicitlyIdle,
                                            isLastStep = i == stepCount - 1
                                        )

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
                                                lastRealProgressMs = System.currentTimeMillis()
                                            }
                                        } else if (isWaiting) {
                                            lastActivityTimestamp = System.currentTimeMillis()
                                            hasAnyStepInProgress = true
                                            val lastTryMs = lastPermissionAttemptByStep[i] ?: 0L
                                            if (System.currentTimeMillis() - lastTryMs >= 1800L) {
                                                lastPermissionAttemptByStep[i] = System.currentTimeMillis()
                                                autoResolveOutstandingSteps(activeCid, resolvedHubUrl)
                                                if (trajectoryId.isNotBlank()) {
                                                    handleCascadePermissionInteraction(
                                                        cascadeId = activeCid,
                                                        stepIndex = i,
                                                        trajectoryId = trajectoryId,
                                                        hubBaseUrl = resolvedHubUrl,
                                                        requestedInteraction = reqInteraction,
                                                        stepType = stepType
                                                    )
                                                }
                                            }
                                        }

                                        if (isWaiting) {
                                            lastActivityTimestamp = System.currentTimeMillis()
                                        }

                                        if (stepType == "CORTEX_STEP_TYPE_PLANNER_RESPONSE" ||
                                            stepType == "TRAJECTORY_STEP_TYPE_MODEL_THINKING" ||
                                            stepType == "TRAJECTORY_STEP_TYPE_MODEL_RESPONSE" ||
                                            stepType == "CORTEX_STEP_TYPE_FINAL_RESPONSE"
                                        ) {
                                            val plannerResp = step.optJSONObject("plannerResponse")
                                                ?: step.optJSONObject("finalResponse")
                                            val thinkingText = plannerResp?.optString("thinking", "")
                                                ?.ifBlank { step.optString("thinkingTrace", "") }
                                                .orEmpty()
                                            val responseText = plannerResp?.optString("response", "")
                                                ?.ifBlank { plannerResp.optString("modifiedResponse", "") }
                                                ?.ifBlank { plannerResp.optString("text", "") }
                                                ?.ifBlank { plannerResp.optString("message", "") }
                                                ?.ifBlank { step.optString("contentMarkdown", "") }
                                                ?.ifBlank { step.optString("text", "") }
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
                                        } else if (stepType == "CORTEX_STEP_TYPE_NOTIFY_USER" ||
                                            stepType == "TRAJECTORY_STEP_TYPE_NOTIFY_USER" ||
                                            step.has("notifyUser")
                                        ) {
                                            val notifyObj = step.optJSONObject("notifyUser")
                                            val notifyMsg = notifyObj?.optString("message", "")
                                                ?.ifBlank { notifyObj.optString("Message", "") }
                                                ?.ifBlank { notifyObj.optString("notificationContent", "") }
                                                ?.ifBlank { step.optString("message", "") }
                                                ?.ifBlank { step.optString("contentMarkdown", "") }
                                                .orEmpty()
                                            if (notifyMsg.isNotBlank()) {
                                                latestRecoveryStepIdx = i
                                                plannerResponsesByStep["$activeCid:$i"] = notifyMsg
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
                                            val (cmd, out) = extractToolStepCommandAndOutput(
                                                step = step,
                                                rc = rc,
                                                cmdStatus = cmdStatus,
                                                viewFile = viewFile,
                                                viewOutline = viewOutline,
                                                writeFile = writeFile,
                                                replaceFile = replaceFile,
                                                codeAction = codeAction,
                                                listDir = listDir,
                                                grepSearch = grepSearch,
                                                webSearch = webSearch,
                                                subagent = subagent,
                                                mcpTool = mcpTool,
                                                toolInv = toolInv,
                                                gen = gen,
                                                meta = meta,
                                                isAskQuestion = isAskQuestion,
                                                toolName = toolName
                                            )
                                            val questionnaire = if (isAskQuestion) {
                                                parseAskQuestionQuestionnaire(
                                                    step = step,
                                                    gen = gen,
                                                    rc = rc,
                                                    reqInteraction = reqInteraction,
                                                    submittedAskRecord = submittedAskRecord
                                                )
                                            } else null

                                            val exitCode = if (rc?.has("exitCode") == true) rc.optInt("exitCode") else null
                                            val status = resolveToolCallStatus(
                                                stepStatus = stepStatus,
                                                isAskQuestion = isAskQuestion,
                                                hasSubmittedAsk = submittedAskRecord != null,
                                                isWaiting = isWaiting,
                                                isStepExplicitlyActive = isStepExplicitlyActive,
                                                isStepDone = isStepDone,
                                                isCascadeExplicitlyIdle = isCascadeExplicitlyIdle,
                                                isSupersededByLaterStep = i < stepCount - 1,
                                                hasOutput = out.isNotBlank(),
                                                exitCode = exitCode,
                                                toolInvError = toolInv?.optBoolean("isError", false) == true
                                            )

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
                                                if (status == ToolCallStatus.SUCCESS && resolvedContext != null &&
                                                    (cmd.contains("mcp_config.json") || cmd.contains("custom_mcp_registry.json") || cmd.contains("agentra-mcp") || cmd.contains("jarvis-mcp") || cmd.contains("SKILL.md") || cmd.contains("plugin.json") || cmd.contains("/skills/") || cmd.contains("/plugins/"))
                                                ) {
                                                    runCatching {
                                                        com.jarvisai.assistant.mcp.McpServersManager.ensureServersLoadedAndProbed(resolvedContext)
                                                        com.jarvisai.assistant.skills.SkillsAndPluginsManager.refreshSkills(resolvedContext)
                                                        com.jarvisai.assistant.skills.SkillsAndPluginsManager.refreshInstalledPlugins(resolvedContext)
                                                        com.jarvisai.assistant.extension.BrowserExtensionManager.init(resolvedContext)
                                                        com.jarvisai.assistant.extension.BrowserExtensionManager.reloadExtensions()
                                                    }
                                                }
                                            }

                                            if (isAskQuestion && !isStepDone && submittedAskRecord != null) {
                                                val contOutcome = attemptAutoContinueStalledAskQuestion(
                                                    activeCid = activeCid,
                                                    stepIndex = i,
                                                    stepCount = stepCount,
                                                    steps = steps,
                                                    submittedAskRecord = submittedAskRecord,
                                                    autoContinuedAskSteps = autoContinuedAskSteps,
                                                    resolvedHubUrl = resolvedHubUrl,
                                                    sessionId = sessionId,
                                                    workspaceUri = workspaceUri,
                                                    hubMetadataJson = hubMetadataJson,
                                                    currentSpec = currentSpec,
                                                    requestedModelJson = requestedModelJson,
                                                    planModelEnumValue = planModelEnumValue,
                                                    thinkingBudget = thinkingBudget,
                                                    mcpServersArr = mcpServersArr,
                                                    userPrompt = userPrompt
                                                )
                                                if (contOutcome != null) {
                                                    activeCid = contOutcome.activeCid
                                                    turnBaselineStepCount = contOutcome.turnBaselineStepCount
                                                    contOutcome.pollPayload?.let { pollPayload = it }
                                                    lastActivityTimestamp = System.currentTimeMillis()
                                                    unchangedStablePolls = 0
                                                    skipCompletionThisPoll = true
                                                    break
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

                                val currentToolSig = accumulatedTools.values.joinToString(";") { "${it.id}:${it.status}:${it.output.length}" }
                                if (accumulatedResponse.length != prevObservedResponseLen ||
                                    accumulatedThought.length != prevObservedThoughtLen ||
                                    currentToolSig != prevObservedToolSig
                                ) {
                                    lastRealProgressMs = System.currentTimeMillis()
                                    lastActivityTimestamp = lastRealProgressMs
                                    prevObservedResponseLen = accumulatedResponse.length
                                    prevObservedThoughtLen = accumulatedThought.length
                                    prevObservedToolSig = currentToolSig
                                }

                                val signature = "$stepCount|${accumulatedResponse.length}|${accumulatedThought.length}|${accumulatedTools.size}|$cascadeStatus|$hasAnyStepInProgress|$latestErrorStepIdx"
                                if (signature == lastObservedSignature) {
                                    unchangedStablePolls++
                                } else {
                                    lastObservedSignature = signature
                                    unchangedStablePolls = 0
                                }

                                val explicitIdle = isCascadeIdleStatus(cascadeStatus)
                                val explicitRunning = isCascadeRunningStatus(cascadeStatus)
                                lastPollHadRunningWork = hasAnyStepInProgress || explicitRunning

                                val hasUnrecoveredError = latestErrorStepIdx >= 0 && latestErrorStepIdx > latestRecoveryStepIdx
                                if (hasUnrecoveredError && (!explicitRunning || unchangedStablePolls >= 12)) {
                                    // Reset poisoned cascadeId so subsequent turns/retries never stick to a broken cascade
                                    startedCascades.remove(activeCid)
                                    sessionCascadeIds.remove(sessionId)

                                    val shortErr = latestErrorMsg
                                    val isModelOrCapacityErr = isHubModelOrCapacityError(shortErr)
                                    val isTransientNetworkErr = isHubTransientNetworkError(shortErr)

                                    if (isTransientNetworkErr) {
                                        if (localHubTransientRetries < 1) {
                                            localHubTransientRetries++
                                            Log.w(TAG, "Local daemon hit transient EOF/network error (retrying fresh cascade): $shortErr")
                                            delay(350)
                                            retryNextCandidate = true
                                            turnFinished = true
                                            break
                                        } else if (allowCloudCodePa && token.isNotBlank()) {
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
                                        Log.w(TAG, "Local daemon rejected profile=$profile for ${currentSpec.protoName ?: currentSpec.label}: $shortErr")
                                        logRouteEvent("Hub profile=$profile rejected ${currentSpec.label} (${shortErr.take(140)}) -> trying next clean profile")
                                        retryNextProfile = true
                                        turnFinished = true
                                        break
                                    } else if (allowCloudCodePa && token.isNotBlank() && accumulatedResponse.isBlank() && accumulatedTools.isEmpty()) {
                                        Log.w(TAG, "Local daemon failed without output ($shortErr); attempting CloudCode PA failover.")
                                        abortLocalHubToCloudFallback = true
                                        turnFinished = true
                                        break
                                    } else {
                                        emit(AntigravityStreamEvent.Error(shortErr.ifBlank { "Agentra execution error" }))
                                        turnFinished = true
                                        cascadeCompleted = true
                                        break
                                    }
                                }

                                // If the Cascade is explicitly IDLE/DONE with no active step, resolve any stale
                                // non-interactive RUNNING tools (e.g. backgrounded/superseded bash commands) so they
                                // never block turn completion for 120s!
                                val hasPendingUserQuestion = accumulatedTools.entries.any { (k, it) ->
                                    !k.contains("_ptc_") && it.status == ToolCallStatus.PENDING_APPROVAL
                                }
                                if (explicitIdle && !hasAnyStepInProgress && !hasPendingUserQuestion && unchangedStablePolls >= 3) {
                                    accumulatedTools.entries.forEach { (k, tc) ->
                                        if (!k.contains("_ptc_") && tc.status == ToolCallStatus.RUNNING) {
                                            val resolved = tc.copy(
                                                status = if (tc.exitCode != null && tc.exitCode != 0) ToolCallStatus.FAILED else ToolCallStatus.SUCCESS
                                            )
                                            accumulatedTools[k] = resolved
                                            emit(AntigravityStreamEvent.ToolCallUpdate(resolved))
                                        }
                                    }
                                }

                                val hasRealTools = accumulatedTools.keys.any { !it.contains("_ptc_") }
                                val hasPendingPlannedTools = accumulatedTools.keys.any { it.contains("_ptc_") }
                                val hasRunningTools = accumulatedTools.entries.any { (k, it) ->
                                    !k.contains("_ptc_") && (it.status == ToolCallStatus.RUNNING || it.status == ToolCallStatus.PENDING_APPROVAL)
                                }
                                // CRITICAL: Never treat `_ptc_` planned-tool placeholders as completed content!
                                // Completing while only `_ptc_` entries exist strips them and emits an empty response!
                                val hasContent = accumulatedResponse.isNotBlank() || hasRealTools
                                val canCompleteOnIdle = explicitIdle && !hasAnyStepInProgress &&
                                    (!hasPendingPlannedTools || unchangedStablePolls >= 14)
                                val canCompleteOnBlankStatus = cascadeStatus.isBlank() &&
                                    !hasAnyStepInProgress &&
                                    !hasPendingPlannedTools &&
                                    hasCompletedPlannerOrToolStep &&
                                    unchangedStablePolls >= 2
                                val canCompleteOnStableStagnation = !explicitRunning &&
                                    !hasAnyStepInProgress &&
                                    !hasPendingUserQuestion &&
                                    hasContent &&
                                    unchangedStablePolls >= 25

                                val elapsedSinceStartMs = System.currentTimeMillis() - startTimestamp
                                val minEmptyIdleWaitMs = if (accumulatedThought.isNotBlank() || hasPendingPlannedTools) 6500L else 2800L
                                val minEmptyIdlePolls = if (accumulatedThought.isNotBlank() || hasPendingPlannedTools) 14 else 10

                                if (explicitIdle && !hasRunningTools && !hasContent && !hasAnyStepInProgress &&
                                    unchangedStablePolls >= minEmptyIdlePolls && elapsedSinceStartMs >= minEmptyIdleWaitMs
                                ) {
                                    Log.w(TAG, "Local daemon returned IDLE with empty output for $activeCid (profile=$profile) after ${elapsedSinceStartMs}ms; resetting cascade.")
                                    startedCascades.remove(activeCid)
                                    sessionCascadeIds.remove(sessionId)
                                    lastDaemonError = "Local Agentra Hub returned an empty idle state."
                                    retryNextProfile = true
                                    turnFinished = true
                                    break
                                }

                                if (!skipCompletionThisPoll && !explicitRunning && !hasRunningTools && hasContent && (canCompleteOnIdle || canCompleteOnBlankStatus || canCompleteOnStableStagnation)) {
                                    accumulatedTools.keys.removeAll { it.contains("_ptc_") }
                                    val finalizedTools = accumulatedTools.values.toList()
                                    if (accumulatedResponse.isBlank() && finalizedTools.isEmpty()) {
                                        continue
                                    }
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
                                    preferredSendProfileByHubModel[profileCacheKey] = profile
                                    logRouteEvent("Completed via LOCAL_AGY_CLI (hub profile=$profile, $cleanModel, tools=${finalizedTools.size}, chars=${accumulatedResponse.length})")
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

                    if (abortLocalHubToCloudFallback || cascadeCompleted) {
                        break
                    }

                    if (retryNextProfile) {
                        continue
                    }

                    if (retryNextCandidate) {
                        break
                    }

                    if (!turnFinished && !cascadeCompleted) {
                        emit(AntigravityStreamEvent.Error("Agentra Hub request timed out after ${maxTotalSeconds}s."))
                        cascadeCompleted = true
                        break
                    }
                }
                if (!cascadeCompleted && !abortLocalHubToCloudFallback) {
                    rejectedHubPayloads.add(requestedModelKey)
                }
            }

            // 3B. Direct Native `agy` CLI Headless Subprocess Layer (100% Local CLI — NEVER CloudCode PA!)
            // Runs automatically if Local `agy --hub` RPC rejected the model across all payload profiles (e.g. Gemini 4 404).
            if (!cascadeCompleted && !abortLocalHubToCloudFallback && allowLocalCli && resolvedContext != null &&
                (resolvedHubUrl.contains("127.0.0.1") || resolvedHubUrl.contains("localhost"))
            ) {
                candidateQueue.firstOrNull()?.let { spec ->
                    logRouteEvent("Hub Spec: label=${spec.label}, proto=${spec.protoName}, enum=${spec.enumTag}, keys=${spec.rawConfigKeys?.take(140)}")
                }
                logRouteEvent("Mode=${executionMode.name} | Local Hub RPC unresolved ($lastDaemonError) -> Executing via native agy CLI subprocess for $cleanModel")
                val (cliOk, cliErr) = streamViaNativeAgyCliSubprocess(
                    context = resolvedContext,
                    cleanModel = cleanModel,
                    matchedCandidate = candidateQueue.firstOrNull(),
                    promptText = lastBuiltHubPromptText.ifBlank { userPrompt },
                    workspaceDir = workspaceDir,
                    sessionId = sessionId,
                    thinkingBudget = thinkingBudget,
                    history = history,
                    systemInstruction = systemInstruction
                )
                if (cliOk) {
                    cascadeCompleted = true
                } else if (cliErr.isNotBlank()) {
                    lastDaemonError = if (lastDaemonError.isNotBlank()) {
                        "$lastDaemonError | Native CLI: ${cliErr.take(180)}"
                    } else {
                        cliErr
                    }
                }
            }

            // 4. Automatic Direct CloudCode PA Failover ONLY when CloudCode PA is enabled (HYBRID_BOTH)
            if (!cascadeCompleted) {
                if (allowCloudCodePa && token.isNotBlank()) {
                    logRouteEvent("Mode=HYBRID_BOTH | Local CLI unresolved ($lastDaemonError) -> Failover to CloudCode PA for $cleanModel")
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
                    if (cloudOk) {
                        logRouteEvent("Completed via CLOUDCODE_PA (Hybrid failover)")
                        return@flow
                    }
                }

                val authHint = if (!allowCloudCodePa) {
                    val lowerErr = lastDaemonError.lowercase()
                    when {
                        lowerErr.contains("authentication failed") || lowerErr.contains("unauthenticated") ->
                            "Antigravity CLI Only mode: Local agy CLI authentication fail/timeout hua. Settings -> Antigravity OAuth se dubara sign-in karein ya terminal me `agy auth login` check karein."
                        (lowerErr.contains("404") || lowerErr.contains("not_found") || lowerErr.contains("requested entity was not found")) &&
                            cleanModel.contains("gemini-4", ignoreCase = true) ->
                            "Antigravity CLI Only mode: Local agy CLI/Hub ke upstream backend ne '$cleanModel' ke liye 404 NOT_FOUND diya hai. `agy` CLI ko latest version par update karein (Settings -> AGY CLI), ya Gemini 4 ke liye Settings me 'Both (Hybrid)' / 'CloudCode PA' mode ON karein."
                        else ->
                            "Antigravity CLI Only mode: ${lastDaemonError.ifBlank { "Local AGY Hub '$cleanModel' par response nahi de paya. Local Server Logs check karein." }}"
                    }
                } else if (token.isBlank() && inspection.hasValidAuth == false) {
                    "Local Antigravity Hub is not signed in (catalog empty). Tap Menu -> '☁️ Antigravity OAuth' (or type /antigravity) to sign in with Google."
                } else if (token.isBlank()) {
                    "${lastDaemonError.ifBlank { "Model catalog missing on local Antigravity Hub" }}. Sign in via Menu -> '☁️ Antigravity OAuth' (/antigravity) so Agentra can hydrate models or stream via Google DeepMind CloudCode."
                } else {
                    lastDaemonError.ifBlank { "Selected model '$cleanModel' failed on both local Hub and CloudCode PA. Please re-authenticate via /antigravity." }
                }
                emit(AntigravityStreamEvent.Error(authHint))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Antigravity Local Hub Exception: ${e.message}", e)
            if (allowCloudCodePa && token.isNotBlank()) {
                logRouteEvent("Mode=${executionMode.name} | Local Hub exception (${e.message}) -> Failover to CloudCode PA")
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
                if (cloudOk) {
                    logRouteEvent("Completed via CLOUDCODE_PA (Exception failover)")
                    return@flow
                }
            }
            val errMsg = if (!allowCloudCodePa) {
                "Antigravity CLI Only mode error (127.0.0.1:$hubPort): ${e.message ?: "Local Hub unreachable"}"
            } else {
                "Antigravity Hub unreachable at 127.0.0.1:$hubPort. Please verify Linux Engine is running or sign in via /antigravity."
            }
            emit(AntigravityStreamEvent.Error(errMsg))
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
