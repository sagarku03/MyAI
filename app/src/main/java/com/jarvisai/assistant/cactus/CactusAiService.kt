package com.jarvisai.assistant.cactus

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.AlarmClock
import android.util.Base64
import android.util.Log
import com.jarvisai.assistant.data.local.ChatMessageEntity
import com.jarvisai.assistant.service.AppLauncherManager
import com.jarvisai.assistant.service.McpClientBridge
import com.jarvisai.assistant.service.PhoneControlsManager
import com.jarvisai.assistant.service.SystemStatusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Dual-engine multimodal AI service powered by:
 * 1. Official Cactus Android JNI SDK (`com.cactuscompute:cactus:1.4.3-beta`, `libcactus.so`) for
 *    in-process Qwen3/Gemma/LFM2 chat, tools, LFM2-VL vision, Whisper STT, and local embeddings.
 * 2. Cactus v4 Native CLI/HTTP binary (`needle3.cact` phone-tool router & `whistle.cact` STT) +
 *    optional OpenAI-compatible external server.
 */
object CactusAiService {
    private const val TAG = "CactusAiService"
    internal const val NEEDLE_AUTO_EXECUTE_MIN_CONFIDENCE = 0.55

    private val streamClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val quickEmbedClient = OkHttpClient.Builder()
        .connectTimeout(350, TimeUnit.MILLISECONDS)
        .readTimeout(1800, TimeUnit.MILLISECONDS)
        .build()

    private val standardEmbedClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val screenAgentClient = OkHttpClient.Builder()
        .connectTimeout(900, TimeUnit.MILLISECONDS)
        .readTimeout(6500, TimeUnit.MILLISECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()

    fun streamChatCompletion(
        context: Context,
        modelId: String,
        userPrompt: String,
        conversationHistory: List<ChatMessageEntity>,
        systemInstruction: String,
        enablePhoneTools: Boolean = true
    ): Flow<CactusStreamEvent> = channelFlow {
        val appCtx = context.applicationContext
        val startMs = System.currentTimeMillis()
        val cfg = CactusNativeRuntime.loadConfig(appCtx)
        val requestedSpec = CactusModelCatalog.findById(modelId)
            ?: CactusModelCatalog.findById(cfg.activeLlmModelId)
            ?: CactusModelCatalog.allModels.first()

        val sdkSpec = resolveRunnableSdkChatSpec(appCtx, requestedSpec)
        if (sdkSpec == null) {
            send(CactusStreamEvent.Status("🌵 Preparing ${requestedSpec.displayName}..."))
        }

        // ROUTE 0: In-Process Cactus Android SDK (Qwen3 / Gemma 3 / LFM2 / FunctionGemma — zero HTTP server!)
        if (sdkSpec != null && CactusInProcessEngine.isAvailable(appCtx)) {
            if (CactusInProcessEngine.getActiveLoadedSlug() != sdkSpec.sdkSlug) {
                send(CactusStreamEvent.Status("🌵 Warming up ${sdkSpec.displayName}..."))
            }
            val historyPairs = conversationHistory.map { it.role to it.content }
            val outcome = CactusInProcessEngine.generateChatOrToolCompletion(
                context = appCtx,
                spec = sdkSpec,
                systemInstruction = systemInstruction,
                conversationMessages = historyPairs,
                userPrompt = userPrompt,
                enablePhoneTools = enablePhoneTools,
                temperature = cfg.temperature,
                maxContextTokens = cfg.maxContextTokens,
                onPartialUpdate = { acc ->
                    trySend(CactusStreamEvent.Token(accumulatedText = acc, delta = ""))
                }
            )
            when (outcome) {
                is CactusInProcessEngine.SdkChatOutcome.Success -> {
                    val toolSummaries = mutableListOf<String>()
                    if (enablePhoneTools && outcome.toolCalls.isNotEmpty()) {
                        for (toolReq in outcome.toolCalls) {
                            val (ok, summary) = executeLocalDeviceTool(appCtx, toolReq.name, toolReq.argumentsJson)
                            toolSummaries.add(summary)
                            send(
                                CactusStreamEvent.ToolExecuted(
                                    toolCallId = toolReq.id,
                                    toolName = toolReq.name,
                                    argumentsSummary = toolReq.argumentsJson.take(120),
                                    resultSummary = summary,
                                    succeeded = ok
                                )
                            )
                        }
                    } else {
                        val inlineTools = if (enablePhoneTools) extractInlineToolCalls(outcome.text) else emptyList()
                        for (toolReq in inlineTools) {
                            val (ok, summary) = executeLocalDeviceTool(appCtx, toolReq.name, toolReq.argumentsJson)
                            toolSummaries.add(summary)
                            send(
                                CactusStreamEvent.ToolExecuted(
                                    toolCallId = toolReq.id,
                                    toolName = toolReq.name,
                                    argumentsSummary = toolReq.argumentsJson.take(120),
                                    resultSummary = summary,
                                    succeeded = ok
                                )
                            )
                        }
                    }

                    val cleanReply = outcome.text
                        .replace(Regex("<tool_call>[\\s\\S]*?</tool_call>"), "")
                        .trim()
                    val finalOutput = when {
                        cleanReply.isNotBlank() && toolSummaries.isNotEmpty() ->
                            "$cleanReply\n\n⚡ Local Actions:\n${toolSummaries.joinToString("\n") { "• $it" }}"
                        cleanReply.isNotBlank() -> cleanReply
                        toolSummaries.isNotEmpty() -> toolSummaries.joinToString("\n")
                        else -> ""
                    }
                    if (finalOutput.isNotBlank()) {
                        send(
                            CactusStreamEvent.Completed(
                                fullText = finalOutput,
                                modelUsed = sdkSpec.id,
                                latencyMs = outcome.latencyMs,
                                promptTokens = outcome.promptTokens,
                                outputTokens = outcome.outputTokens
                            )
                        )
                        return@channelFlow
                    }
                }
                is CactusInProcessEngine.SdkChatOutcome.Failure -> {
                    Log.w(TAG, "In-process SDK inference failed for ${sdkSpec.id}: ${outcome.reason}")
                }
            }
        }

        // ROUTE 1A/1B: Probe local server (`OPENAI` external bridge OR `CACTUS_NATIVE` v4 `--serve`)
        var serverProbe = CactusNativeRuntime.probeServerEndpoint(cfg.serverBaseUrl)
        val hasLocalCact = CactusNativeRuntime.findLocalCactFile(appCtx, requestedSpec) != null ||
            CactusNativeRuntime.findAnyDownloadedNativeCactTextModel(appCtx) != null
        if (!serverProbe.isOnline &&
            !CactusNativeRuntime.shouldSkipAutoServerStart(requestedSpec.id) &&
            CactusNativeRuntime.resolveCactusBinaryPath(appCtx) != null &&
            hasLocalCact &&
            (requestedSpec.runtime == CactusModelRuntime.NATIVE_V4_CACT || sdkSpec == null)
        ) {
            send(CactusStreamEvent.Status("🌵 Starting local Cactus v4 server..."))
            CactusNativeRuntime.startLocalServer(appCtx, requestedSpec)
            serverProbe = CactusNativeRuntime.probeServerEndpoint(cfg.serverBaseUrl)
        }

        if (serverProbe.isOnline && serverProbe.apiStyle == CactusServerApiStyle.OPENAI) {
            val baseUrl = cfg.serverBaseUrl.trim().removeSuffix("/")
            val serverModelName = serverProbe.modelName.ifBlank { requestedSpec.slug }
            val messagesArray = buildMessagesArray(systemInstruction, conversationHistory, userPrompt)

            val payload = JSONObject().apply {
                put("model", serverModelName)
                put("messages", messagesArray)
                put("stream", true)
                put("temperature", cfg.temperature.toDouble())
                put("max_tokens", 1024)
                if (enablePhoneTools && requestedSpec.supportsToolCalling) {
                    put("tools", buildLocalPhoneToolsSchema())
                    put("tool_choice", "auto")
                }
            }

            try {
                val request = Request.Builder()
                    .url("$baseUrl/v1/chat/completions")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val accText = StringBuilder()
                val toolCallAccumulators = linkedMapOf<Int, Triple<String, String, StringBuilder>>()
                var promptTok = 0
                var outTok = 0

                streamClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val errBody = resp.body?.string()?.take(240).orEmpty()
                        throw IllegalStateException("Cactus Server HTTP ${resp.code}: $errBody")
                    }
                    val body = resp.body ?: throw IllegalStateException("Empty Cactus stream body")
                    BufferedReader(InputStreamReader(body.byteStream())).use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            val trimmed = line.trim()
                            if (trimmed.isEmpty() || !trimmed.startsWith("data:")) continue
                            val dataStr = trimmed.removePrefix("data:").trim()
                            if (dataStr == "[DONE]") break

                            val chunkObj = runCatching { JSONObject(dataStr) }.getOrNull() ?: continue
                            val usage = chunkObj.optJSONObject("usage")
                            if (usage != null) {
                                promptTok = usage.optInt("prompt_tokens", promptTok)
                                outTok = usage.optInt("completion_tokens", outTok)
                            }
                            val choice = chunkObj.optJSONArray("choices")?.optJSONObject(0) ?: continue
                            val delta = choice.optJSONObject("delta") ?: choice.optJSONObject("message") ?: continue

                            val contentPiece = delta.optString("content", "")
                            if (contentPiece.isNotEmpty() && contentPiece != "null") {
                                accText.append(contentPiece)
                                val cleanedPartial = CactusInProcessEngine.stripThinkingMarkup(accText.toString())
                                if (cleanedPartial.isNotBlank()) {
                                    send(CactusStreamEvent.Token(cleanedPartial, contentPiece))
                                }
                            }

                            val deltaTools = delta.optJSONArray("tool_calls")
                            if (deltaTools != null) {
                                for (i in 0 until deltaTools.length()) {
                                    val tc = deltaTools.optJSONObject(i) ?: continue
                                    val idx = tc.optInt("index", i)
                                    val tcId = tc.optString("id").ifBlank { "cactus_tool_$idx" }
                                    val fn = tc.optJSONObject("function")
                                    val fnName = fn?.optString("name").orEmpty()
                                    val fnArgs = fn?.optString("arguments").orEmpty()
                                    val existing = toolCallAccumulators[idx]
                                    if (existing == null) {
                                        toolCallAccumulators[idx] = Triple(tcId, fnName, StringBuilder(fnArgs))
                                    } else {
                                        existing.third.append(fnArgs)
                                        if (fnName.isNotBlank() && existing.second.isBlank()) {
                                            toolCallAccumulators[idx] = Triple(existing.first, fnName, existing.third)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val requestedTools = mutableListOf<CactusToolCallRequest>()
                toolCallAccumulators.values.forEach { (id, name, argsSb) ->
                    if (name.isNotBlank()) {
                        requestedTools.add(CactusToolCallRequest(id, name, argsSb.toString()))
                    }
                }
                if (requestedTools.isEmpty()) {
                    requestedTools.addAll(extractInlineToolCalls(accText.toString()))
                }

                val toolSummaries = mutableListOf<String>()
                for (toolReq in requestedTools) {
                    val (ok, summary) = executeLocalDeviceTool(appCtx, toolReq.name, toolReq.argumentsJson)
                    toolSummaries.add(summary)
                    send(
                        CactusStreamEvent.ToolExecuted(
                            toolCallId = toolReq.id,
                            toolName = toolReq.name,
                            argumentsSummary = toolReq.argumentsJson.take(120),
                            resultSummary = summary,
                            succeeded = ok
                        )
                    )
                }

                val cleanReply = CactusInProcessEngine.stripThinkingMarkup(
                    accText.toString().replace(Regex("<tool_call>[\\s\\S]*?</tool_call>"), "")
                )
                val finalOutput = when {
                    cleanReply.isNotBlank() && toolSummaries.isNotEmpty() ->
                        "$cleanReply\n\n⚡ Local Actions:\n${toolSummaries.joinToString("\n") { "• $it" }}"
                    cleanReply.isNotBlank() -> cleanReply
                    toolSummaries.isNotEmpty() -> toolSummaries.joinToString("\n")
                    else -> "Cactus local model returned an empty response."
                }

                send(
                    CactusStreamEvent.Completed(
                        fullText = finalOutput,
                        modelUsed = "cactus:${requestedSpec.slug}",
                        latencyMs = System.currentTimeMillis() - startMs,
                        promptTokens = promptTok,
                        outputTokens = outTok
                    )
                )
                return@channelFlow
            } catch (e: Exception) {
                Log.w(TAG, "Cactus OpenAI HTTP stream failed, trying native v4 HTTP / CLI: ${e.message}")
            }
        }

        if (serverProbe.isOnline && serverProbe.apiStyle == CactusServerApiStyle.CACTUS_NATIVE) {
            val baseUrl = cfg.serverBaseUrl.trim().removeSuffix("/")
            val loadedModelTag = resolveActualLoadedLocalModelTag(requestedSpec)
            val nativeRes = callCactusNativeHttpComplete(
                baseUrl = baseUrl,
                prompt = userPrompt,
                messagesArray = buildMessagesArray(systemInstruction, conversationHistory, userPrompt),
                client = streamClient,
                resetBeforeTurn = true
            )
            nativeRes.onSuccess { rawReply ->
                val handled = emitParsedCactusNativeOutput(
                    appCtx = appCtx,
                    rawReply = rawReply,
                    userPrompt = userPrompt,
                    modelTag = loadedModelTag,
                    enablePhoneTools = enablePhoneTools,
                    hybridFallbackEnabled = cfg.hybridCloudFallbackEnabled,
                    startMs = startMs
                ) { event -> send(event) }
                if (handled) return@channelFlow
            }.onFailure { err ->
                Log.w(TAG, "Cactus native HTTP complete failed: ${err.message}")
            }
        }

        // ROUTE 2: Direct Native ARM64 Cactus v4 CLI (`needle3.cact --tools tools.json --prompt ...`)
        if (CactusNativeRuntime.resolveCactusBinaryPath(appCtx) != null && hasLocalCact) {
            val resolvedCact = CactusNativeRuntime.findLocalCactFile(appCtx, requestedSpec)
                ?: CactusNativeRuntime.findAnyDownloadedNativeCactTextModel(appCtx)?.second
            if (resolvedCact != null) {
                val actualTag = "cactus:${resolvedCact.nameWithoutExtension}"
                send(CactusStreamEvent.Status("🌵 Running native Cactus v4 (${resolvedCact.name})..."))
                val cliRes = runAdaptiveNativeCli(
                    context = appCtx,
                    modelTarget = resolvedCact.absolutePath,
                    prompt = userPrompt,
                    includeTools = true,
                    timeoutSeconds = 45L
                )
                cliRes.onSuccess { rawText ->
                    emitParsedCactusNativeOutput(
                        appCtx = appCtx,
                        rawReply = rawText,
                        userPrompt = userPrompt,
                        modelTag = actualTag,
                        enablePhoneTools = enablePhoneTools,
                        hybridFallbackEnabled = cfg.hybridCloudFallbackEnabled,
                        startMs = startMs
                    ) { event -> send(event) }
                    return@channelFlow
                }.onFailure { err ->
                    send(
                        CactusStreamEvent.Error(
                            message = "Cactus Local Engine error: ${err.message}",
                            canFallbackToCloud = cfg.hybridCloudFallbackEnabled
                        )
                    )
                    return@channelFlow
                }
            }
        }

        val helpfulMsg = if (requestedSpec.isInProcessSdkModel) {
            "${requestedSpec.displayName} abhi downloaded nahi hai. Settings → Cactus On-Device AI me is model ko download karein (0 server setup needed!)."
        } else {
            "Cactus On-Device model ready nahi hai (${cfg.serverBaseUrl}). Settings → Cactus On-Device AI me Qwen3 / Gemma3 / Needle3 download karein."
        }
        send(
            CactusStreamEvent.Error(
                message = helpfulMsg,
                canFallbackToCloud = cfg.hybridCloudFallbackEnabled
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun resolveRunnableSdkChatSpec(appCtx: Context, requested: CactusModelSpec): CactusModelSpec? {
        if (requested.isInProcessSdkModel &&
            (requested.capability == CactusModelCapability.LLM || requested.capability == CactusModelCapability.TOOL_CALLING) &&
            CactusNativeRuntime.isModelOnDisk(appCtx, requested)
        ) {
            return requested
        }
        // Only fall back automatically to another downloaded SDK LLM when requested model isn't explicitly a downloaded `.cact`
        val hasExactLocalCact = requested.runtime == CactusModelRuntime.NATIVE_V4_CACT &&
            CactusNativeRuntime.findLocalCactFile(appCtx, requested) != null
        if (hasExactLocalCact) return null
        return CactusNativeRuntime.findAnyDownloadedSdkModel(
            appCtx,
            setOf(CactusModelCapability.LLM, CactusModelCapability.TOOL_CALLING)
        )
    }

    /**
     * Private Offline Vision analysis using In-Process Cactus SDK VLM (`LFM 2 VL 450M`) first,
     * falling back to an external OpenAI Vision server if running.
     */
    suspend fun analyzeImageOffline(
        context: Context,
        imageBase64: String,
        mimeType: String = "image/jpeg",
        prompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val cfg = CactusNativeRuntime.loadConfig(appCtx)
        val requestedVlm = CactusModelCatalog.findById(cfg.activeVlmModelId)
            ?: CactusModelCatalog.visionCapableModels().first()

        // 1. In-Process Cactus SDK Vision (`LFM 2 VL 450M`)
        val sdkVlm = when {
            requestedVlm.isInProcessSdkModel && requestedVlm.supportsVision && CactusNativeRuntime.isModelOnDisk(appCtx, requestedVlm) ->
                requestedVlm
            else -> CactusNativeRuntime.findAnyDownloadedSdkModel(appCtx, setOf(CactusModelCapability.VLM))
        }
        if (sdkVlm != null && CactusInProcessEngine.isAvailable(appCtx)) {
            val sdkRes = CactusInProcessEngine.analyzeImage(
                context = appCtx,
                spec = sdkVlm,
                systemInstruction = "You are Agentra Offline Vision AI. Describe clearly and accurately in Hinglish & English.",
                userPrompt = prompt,
                imageBase64 = imageBase64
            )
            if (sdkRes.isSuccess) return@withContext sdkRes
        }

        // 2. External OpenAI-compatible multimodal endpoint (never hit CACTUS_NATIVE with `/v1/chat/completions`!)
        val serverProbe = CactusNativeRuntime.probeServerEndpoint(cfg.serverBaseUrl)
        if (serverProbe.isOnline && serverProbe.apiStyle == CactusServerApiStyle.OPENAI) {
            try {
                val baseUrl = cfg.serverBaseUrl.trim().removeSuffix("/")
                val contentParts = JSONArray().apply {
                    put(JSONObject().put("type", "text").put("text", prompt))
                    put(
                        JSONObject().put("type", "image_url").put(
                            "image_url",
                            JSONObject().put("url", "data:$mimeType;base64,$imageBase64")
                        )
                    )
                }
                val payload = JSONObject().apply {
                    put("model", serverProbe.modelName.ifBlank { requestedVlm.slug })
                    put("stream", false)
                    put("max_tokens", 1024)
                    put(
                        "messages",
                        JSONArray().put(
                            JSONObject().put("role", "user").put("content", contentParts)
                        )
                    )
                }
                val req = Request.Builder()
                    .url("$baseUrl/v1/chat/completions")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                streamClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val root = JSONObject(resp.body?.string().orEmpty())
                        val text = root.optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("message")
                            ?.optString("content")
                            .orEmpty()
                            .trim()
                        if (text.isNotBlank()) {
                            return@withContext Result.success(text)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cactus HTTP vision call failed: ${e.message}")
            }
        }

        // 3. CLI `--image` only if future binary ever adds `--image`
        val caps = CactusNativeRuntime.getBinaryCliCapabilities(appCtx)
        if (caps.hasDoubleDashImage && CactusNativeRuntime.resolveCactusBinaryPath(appCtx) != null &&
            CactusNativeRuntime.isModelOnDisk(appCtx, requestedVlm)
        ) {
            val tmpImg = File(appCtx.cacheDir, "cactus_vlm_${System.currentTimeMillis()}.jpg")
            try {
                tmpImg.writeBytes(Base64.decode(imageBase64, Base64.DEFAULT))
                val modelTarget = CactusNativeRuntime.resolveModelArtifactPath(appCtx, requestedVlm)
                val cliRes = runAdaptiveNativeCli(
                    context = appCtx,
                    modelTarget = modelTarget,
                    prompt = prompt,
                    imagePath = tmpImg.absolutePath,
                    includeTools = false,
                    timeoutSeconds = 90L
                )
                if (cliRes.isSuccess) return@withContext cliRes
            } finally {
                runCatching { tmpImg.delete() }
            }
        }

        Result.failure(
            IllegalStateException(
                "Offline Vision (${requestedVlm.displayName}) ready nahi hai. " +
                    "Settings → Cactus On-Device AI me 'LFM 2 VL 450M' download karein (bina server ke app me directly chalta hai!)."
            )
        )
    }

    suspend fun analyzeDocumentOffline(
        context: Context,
        documentText: String,
        documentName: String,
        prompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val topChunks = CactusVectorMemoryIndex.selectTopDocumentChunks(
            context = appCtx,
            documentText = documentText,
            query = prompt,
            maxChunks = 5
        )
        val combinedContext = topChunks.joinToString("\n\n---\n\n")
        val fullPrompt = """
            Document Name: $documentName
            
            Relevant Excerpts (100% Private On-Device RAG):
            $combinedContext
            
            User Instruction: $prompt
            Provide a clear, structured response in natural Hinglish & English.
        """.trimIndent()

        var collected = ""
        var failureMsg: String? = null
        streamChatCompletion(
            context = appCtx,
            modelId = CactusNativeRuntime.config.value.activeLlmModelId,
            userPrompt = fullPrompt,
            conversationHistory = emptyList(),
            systemInstruction = "You are Agentra Private Offline Document Analyst powered by Cactus On-Device AI.",
            enablePhoneTools = false
        ).collect { ev ->
            when (ev) {
                is CactusStreamEvent.Completed -> collected = ev.fullText
                is CactusStreamEvent.HandoffToCloud -> if (collected.isBlank()) failureMsg = ev.offlineFriendlyReply
                is CactusStreamEvent.Error -> failureMsg = ev.message
                else -> Unit
            }
        }

        if (collected.isNotBlank()) {
            Result.success(collected)
        } else {
            Result.failure(IllegalStateException(failureMsg ?: "Cactus offline document analysis ke liye Qwen3 ya Gemma model download karein."))
        }
    }

    /**
     * Extracts clean transcript text from Whistle CLI JSON (`{"text":"...","language":"en",...}`)
     * or returns plain non-JSON text; returns null when silent or empty so raw JSON never reaches UI.
     */
    internal fun parseWhistleCliTranscript(rawOutput: String): String? {
        val trimmed = rawOutput.trim()
        if (trimmed.isEmpty() || CactusNativeRuntime.looksLikeCliHelpDump(trimmed)) return null
        val jsonLine = trimmed.lines().lastOrNull { it.trim().startsWith("{") && it.trim().endsWith("}") }?.trim()
        if (jsonLine != null) {
            val obj = runCatching { JSONObject(jsonLine) }.getOrNull()
            if (obj != null) {
                val text = obj.optString("text", "").ifBlank { obj.optString("response", "") }.trim()
                return text.takeIf { it.isNotBlank() && it != "null" }
            }
        }
        return if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            null
        } else {
            trimmed
        }
    }

    /**
     * 100% Offline Audio Transcription supporting:
     * 1. Selected/downloaded In-Process Whisper SDK (`CactusSTT`)
     * 2. Cactus v4 `whistle.cact` CLI binary (with clean JSON `"text"` extraction!)
     * 3. External OpenAI `/v1/audio/transcriptions` server
     */
    suspend fun transcribeAudioOffline(
        context: Context,
        wavFile: File
    ): Result<String> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        if (!wavFile.exists() || wavFile.length() < 128L) {
            return@withContext Result.failure(IllegalArgumentException("Recorded audio file is empty."))
        }
        val cfg = CactusNativeRuntime.loadConfig(appCtx)
        val activeStt = CactusModelCatalog.findById(cfg.activeSttModelId)
            ?: CactusModelCatalog.sttModels().first()

        // Helper for in-process Whisper SDK STT
        suspend fun trySdkStt(spec: CactusModelSpec): Result<String>? {
            if (!spec.isInProcessSdkModel || !CactusInProcessEngine.isAvailable(appCtx) ||
                !CactusNativeRuntime.isModelOnDisk(appCtx, spec)
            ) {
                return null
            }
            return CactusInProcessEngine.transcribeAudio(appCtx, spec, wavFile)
        }

        // Helper for v4 `whistle.cact` CLI STT (always parses JSON `"text"` field cleanly!)
        suspend fun tryWhistleCli(): Result<String>? {
            val whistleFile = CactusNativeRuntime.findDownloadedWhistleCact(appCtx) ?: return null
            if (CactusNativeRuntime.resolveCactusBinaryPath(appCtx) == null) return null
            val cliRes = runAdaptiveNativeCli(
                context = appCtx,
                modelTarget = whistleFile.absolutePath,
                prompt = null,
                audioPath = wavFile.absolutePath,
                includeTools = false,
                timeoutSeconds = 45L
            )
            if (cliRes.isSuccess) {
                val parsed = parseWhistleCliTranscript(cliRes.getOrNull().orEmpty())
                return if (!parsed.isNullOrBlank()) {
                    Result.success(parsed)
                } else {
                    Result.failure(IllegalStateException("Whistle ne koi clear speech detect nahi ki."))
                }
            }
            return Result.failure(cliRes.exceptionOrNull() ?: IllegalStateException("Whistle CLI failed."))
        }

        // 1. Respect user's selected STT model first, then cleanly cross-fallback to the other local STT
        if (activeStt.isInProcessSdkModel) {
            trySdkStt(activeStt)?.let { if (it.isSuccess) return@withContext it }
            CactusNativeRuntime.findAnyDownloadedSdkModel(appCtx, setOf(CactusModelCapability.STT))?.let { alt ->
                if (alt.id != activeStt.id) trySdkStt(alt)?.let { if (it.isSuccess) return@withContext it }
            }
            tryWhistleCli()?.let { if (it.isSuccess) return@withContext it }
        } else {
            tryWhistleCli()?.let { if (it.isSuccess) return@withContext it }
            CactusNativeRuntime.findAnyDownloadedSdkModel(appCtx, setOf(CactusModelCapability.STT))?.let { alt ->
                trySdkStt(alt)?.let { if (it.isSuccess) return@withContext it }
            }
        }

        // 2. External OpenAI `/v1/audio/transcriptions` server
        val serverProbe = CactusNativeRuntime.probeServerEndpoint(cfg.serverBaseUrl)
        if (serverProbe.isOnline && serverProbe.apiStyle == CactusServerApiStyle.OPENAI) {
            try {
                val baseUrl = cfg.serverBaseUrl.trim().removeSuffix("/")
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("model", activeStt.slug)
                    .addFormDataPart(
                        "file",
                        wavFile.name,
                        wavFile.asRequestBody("audio/wav".toMediaTypeOrNull())
                    )
                    .build()
                val req = Request.Builder()
                    .url("$baseUrl/v1/audio/transcriptions")
                    .post(body)
                    .build()
                streamClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val raw = resp.body?.string().orEmpty().trim()
                        val parsed = runCatching { JSONObject(raw).optString("text").trim() }
                            .getOrDefault(raw)
                        if (parsed.isNotBlank()) {
                            return@withContext Result.success(parsed)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cactus HTTP STT failed: ${e.message}")
            }
        }

        Result.failure(
            IllegalStateException(
                "Cactus Offline STT ready nahi hai. Settings → Cactus On-Device AI me Whistle (.cact) ya Whisper Tiny download karein."
            )
        )
    }

    /**
     * Computes a dense embedding vector via:
     * 1. In-Process Cactus SDK (`cactus_embed`) using active embedding model or any downloaded SDK model
     * 2. External OpenAI server `/v1/embeddings` (skips CACTUS_NATIVE to avoid fruitless 404 calls)
     */
    suspend fun computeLocalEmbedding(
        context: Context,
        text: String,
        quickTimeout: Boolean = false
    ): FloatArray? = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val clean = text.trim()
        if (clean.isEmpty()) return@withContext null
        val cfg = CactusNativeRuntime.config.value

        // 1. In-Process SDK Embeddings (`qwen3-0.6-embed` or fallback downloaded SDK LLM)
        if (CactusInProcessEngine.isAvailable(appCtx)) {
            val preferredEmbed = CactusModelCatalog.findById(cfg.activeEmbeddingModelId)
                ?.takeIf { it.isInProcessSdkModel && CactusNativeRuntime.isModelOnDisk(appCtx, it) }
                ?: CactusNativeRuntime.findAnyDownloadedSdkModel(
                    appCtx,
                    setOf(CactusModelCapability.EMBEDDING, CactusModelCapability.LLM, CactusModelCapability.TOOL_CALLING)
                )
            if (preferredEmbed != null) {
                val vec = CactusInProcessEngine.generateEmbedding(appCtx, preferredEmbed, clean)
                if (vec != null && vec.isNotEmpty()) return@withContext vec
            }
        }

        // 2. External OpenAI `/v1/embeddings` ONLY when OpenAI server is actually present
        val serverProbe = CactusNativeRuntime.probeServerEndpoint(cfg.serverBaseUrl)
        if (!serverProbe.isOnline || serverProbe.apiStyle != CactusServerApiStyle.OPENAI) {
            return@withContext null
        }

        val baseUrl = cfg.serverBaseUrl.trim().removeSuffix("/")
        val embedSpec = CactusModelCatalog.findById(cfg.activeEmbeddingModelId)
            ?: CactusModelCatalog.embeddingModels().first()

        val client = if (quickTimeout) quickEmbedClient else standardEmbedClient
        try {
            val payload = JSONObject().apply {
                put("model", embedSpec.slug)
                put("input", clean.take(1500))
            }
            val req = Request.Builder()
                .url("$baseUrl/v1/embeddings")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val root = JSONObject(resp.body?.string().orEmpty())
                val vecArr = root.optJSONArray("data")
                    ?.optJSONObject(0)
                    ?.optJSONArray("embedding")
                    ?: root.optJSONArray("embedding")
                    ?: return@withContext null
                if (vecArr.length() == 0) return@withContext null
                FloatArray(vecArr.length()) { i -> vecArr.optDouble(i, 0.0).toFloat() }
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun quickOfflinePrompt(
        context: Context,
        prompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        var answer = ""
        var handoffReply = ""
        var errMsg: String? = null
        streamChatCompletion(
            context = context,
            modelId = CactusNativeRuntime.config.value.activeLlmModelId,
            userPrompt = prompt,
            conversationHistory = emptyList(),
            systemInstruction = "You are Agentra Offline Core powered by Cactus Compute. Give concise, helpful Hinglish answers.",
            enablePhoneTools = true
        ).collect { ev ->
            when (ev) {
                is CactusStreamEvent.Completed -> answer = ev.fullText
                is CactusStreamEvent.HandoffToCloud -> if (handoffReply.isBlank()) handoffReply = ev.offlineFriendlyReply
                is CactusStreamEvent.Error -> errMsg = ev.message
                else -> Unit
            }
        }
        val finalAnswer = answer.ifBlank { handoffReply }
        if (finalAnswer.isNotBlank()) Result.success(finalAnswer)
        else Result.failure(IllegalStateException(errMsg ?: "Cactus offline inference unavailable"))
    }

    fun encodeCompactScreenForVlm(
        bitmap: Bitmap,
        maxDimension: Int = 640,
        quality: Int = 65
    ): String {
        val w = bitmap.width.coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(1)
        val longest = maxOf(w, h)
        val scaled = if (longest > maxDimension) {
            val scale = maxDimension.toFloat() / longest.toFloat()
            val nw = (w * scale).toInt().coerceAtLeast(64)
            val nh = (h * scale).toInt().coerceAtLeast(64)
            Bitmap.createScaledBitmap(bitmap, nw, nh, true)
        } else {
            bitmap
        }
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(40, 85), baos)
        if (scaled !== bitmap) {
            runCatching { scaled.recycle() }
        }
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Generates Android UI screen automation JSON using genuine JSON-capable local models ONLY:
     * 1. In-Process Cactus SDK (`Qwen3` / `LFM2-1.2B-Tool` for Tier 1 text UI-Tree, `LFM 2 VL` for Tier 2 vision)
     * 2. External OpenAI `/v1/chat/completions` server
     * Explicitly refuses Needle3 phone-tool envelopes so UI navigation is never corrupted.
     */
    suspend fun generateFastScreenDecisionJson(
        context: Context,
        systemInstruction: String,
        userPrompt: String,
        imageBase64: String? = null,
        mimeType: String = "image/jpeg"
    ): Result<Pair<String, Long>> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val startMs = System.currentTimeMillis()
        if (!CactusNativeRuntime.isFastLocalInferenceReady(appCtx)) {
            return@withContext Result.failure(IllegalStateException("Cactus local LLM/VLM not downloaded or running."))
        }
        val cfg = CactusNativeRuntime.config.value

        // PATH A: In-Process Cactus SDK (Tier 1 LLM or Tier 2 VLM)
        if (CactusInProcessEngine.isAvailable(appCtx)) {
            if (!imageBase64.isNullOrBlank()) {
                val sdkVlm = CactusNativeRuntime.findAnyDownloadedSdkModel(appCtx, setOf(CactusModelCapability.VLM))
                if (sdkVlm != null) {
                    val vlmRes = CactusInProcessEngine.analyzeImage(
                        context = appCtx,
                        spec = sdkVlm,
                        systemInstruction = systemInstruction,
                        userPrompt = userPrompt,
                        imageBase64 = imageBase64,
                        maxTokens = 260
                    ).getOrNull()?.trim().orEmpty()
                    if (looksLikeScreenDecisionJson(vlmRes)) {
                        return@withContext Result.success(vlmRes to (System.currentTimeMillis() - startMs))
                    }
                }
            } else {
                val requested = CactusModelCatalog.findById(cfg.activeLlmModelId)
                val sdkLlm = requested?.takeIf {
                    it.isInProcessSdkModel &&
                        (it.capability == CactusModelCapability.LLM || it.capability == CactusModelCapability.TOOL_CALLING) &&
                        CactusNativeRuntime.isModelOnDisk(appCtx, it)
                } ?: CactusNativeRuntime.findAnyDownloadedSdkModel(
                    appCtx,
                    setOf(CactusModelCapability.LLM, CactusModelCapability.TOOL_CALLING)
                )
                if (sdkLlm != null) {
                    val outcome = CactusInProcessEngine.generateChatOrToolCompletion(
                        context = appCtx,
                        spec = sdkLlm,
                        systemInstruction = systemInstruction,
                        conversationMessages = emptyList(),
                        userPrompt = userPrompt.take(3200),
                        enablePhoneTools = false,
                        temperature = 0.1f,
                        maxContextTokens = 2048,
                        maxOutputTokens = 260,
                        onPartialUpdate = {}
                    )
                    if (outcome is CactusInProcessEngine.SdkChatOutcome.Success && looksLikeScreenDecisionJson(outcome.text)) {
                        return@withContext Result.success(outcome.text to (System.currentTimeMillis() - startMs))
                    }
                }
            }
        }

        // PATH B: External OpenAI-compatible HTTP server ONLY (never Needle3 native CLI/HTTP)
        val serverProbe = CactusNativeRuntime.probeServerEndpoint(cfg.serverBaseUrl)
        if (serverProbe.isOnline && serverProbe.apiStyle == CactusServerApiStyle.OPENAI) {
            val activeSpec = if (!imageBase64.isNullOrBlank()) {
                CactusModelCatalog.findById(cfg.activeVlmModelId) ?: CactusModelCatalog.visionCapableModels().first()
            } else {
                CactusModelCatalog.findById(cfg.activeLlmModelId) ?: CactusModelCatalog.chatCapableModels().first()
            }
            val baseUrl = cfg.serverBaseUrl.trim().removeSuffix("/")
            val serverModel = serverProbe.modelName.ifBlank { activeSpec.slug }

            fun buildPayload(includeResponseFormat: Boolean): JSONObject {
                val messages = JSONArray().apply {
                    if (systemInstruction.isNotBlank()) {
                        put(JSONObject().put("role", "system").put("content", systemInstruction.take(1800)))
                    }
                    if (!imageBase64.isNullOrBlank()) {
                        val contentParts = JSONArray().apply {
                            put(JSONObject().put("type", "text").put("text", userPrompt.take(3600)))
                            put(
                                JSONObject().put("type", "image_url").put(
                                    "image_url",
                                    JSONObject().put("url", "data:$mimeType;base64,$imageBase64")
                                )
                            )
                        }
                        put(JSONObject().put("role", "user").put("content", contentParts))
                    } else {
                        put(JSONObject().put("role", "user").put("content", userPrompt.take(4000)))
                    }
                }
                return JSONObject().apply {
                    put("model", serverModel)
                    put("messages", messages)
                    put("stream", false)
                    put("temperature", 0.1)
                    put("max_tokens", 260)
                    if (includeResponseFormat) {
                        put("response_format", JSONObject().put("type", "json_object"))
                    }
                }
            }

            for (withFormat in listOf(true, false)) {
                try {
                    val req = Request.Builder()
                        .url("$baseUrl/v1/chat/completions")
                        .post(buildPayload(withFormat).toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    screenAgentClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val root = JSONObject(resp.body?.string().orEmpty())
                            val content = root.optJSONArray("choices")
                                ?.optJSONObject(0)
                                ?.optJSONObject("message")
                                ?.optString("content")
                                .orEmpty()
                                .trim()
                            if (looksLikeScreenDecisionJson(content)) {
                                return@withContext Result.success(content to (System.currentTimeMillis() - startMs))
                            }
                        } else if (resp.code !in 400..422) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Fast Cactus screen HTTP call failed (withFormat=$withFormat): ${e.message}")
                    if (!withFormat) break
                }
            }
        }

        Result.failure(IllegalStateException("Cactus fast screen decision unavailable; falling back cleanly."))
    }

    internal fun looksLikeScreenDecisionJson(raw: String): Boolean {
        val clean = raw.trim()
        if (!clean.contains("{") || !clean.contains("}")) return false
        // Explicitly reject Needle3 tool-call envelopes (`{"type":"call","function_calls":[...]}`)
        if (parseNeedleCallEnvelope(clean) != null) return false
        return clean.contains("\"action\"", ignoreCase = true)
    }

    private fun callCactusNativeHttpComplete(
        baseUrl: String,
        prompt: String,
        messagesArray: JSONArray?,
        client: OkHttpClient,
        resetBeforeTurn: Boolean = false
    ): Result<String> {
        if (resetBeforeTurn) {
            runCatching {
                val resetReq = Request.Builder()
                    .url("$baseUrl/reset")
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(resetReq).execute().close()
            }
        }

        val payloadVariants = buildList {
            add(JSONObject().put("input", prompt).toString())
            if (messagesArray != null) {
                add(
                    JSONObject().apply {
                        put("input", prompt)
                        put("messages", messagesArray)
                    }.toString()
                )
            }
        }

        var lastErr = "Native Cactus HTTP server returned no response."
        val url = "$baseUrl/complete"
        for (jsonPayload in payloadVariants) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty().trim()
                    if (resp.isSuccessful && raw.isNotBlank()) {
                        val extracted = extractNativeCactusHttpReply(raw)
                        if (extracted.isNotBlank()) {
                            return Result.success(extracted)
                        }
                    } else if (resp.code == 404 || resp.code == 405) {
                        lastErr = "HTTP ${resp.code} on $url"
                        break
                    } else {
                        lastErr = "HTTP ${resp.code}: ${raw.take(160)}"
                    }
                }
            } catch (e: Exception) {
                lastErr = e.message ?: "Connection failed"
            }
        }
        return Result.failure(IllegalStateException(lastErr))
    }

    internal data class NeedleCallEnvelope(
        val type: String,
        val success: Boolean,
        val functionCalls: List<CactusToolCallRequest>,
        val suppressedCalls: List<CactusToolCallRequest>,
        val reasoning: String,
        val confidence: Double,
        val decodeTps: Double
    )

    internal fun shouldAutoExecuteNeedleCall(
        envelope: NeedleCallEnvelope,
        enablePhoneTools: Boolean
    ): Boolean = enablePhoneTools &&
        envelope.functionCalls.isNotEmpty() &&
        envelope.confidence >= NEEDLE_AUTO_EXECUTE_MIN_CONFIDENCE

    private fun resolveActualLoadedLocalModelTag(requestedSpec: CactusModelSpec): String {
        val loaded = CactusNativeRuntime.status.value.loadedServerModel.lowercase()
        return when {
            loaded.contains("needle") -> "cactus:needle3"
            loaded.contains("whistle") -> "cactus:whistle"
            loaded.contains("mirage") -> "cactus:mirage"
            else -> "cactus:${requestedSpec.slug}"
        }
    }

    internal fun parseNeedleCallEnvelope(rawBody: String): NeedleCallEnvelope? {
        val trimmed = rawBody.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        val hasNeedleSignature = obj.optString("type").equals("call", ignoreCase = true) ||
            obj.has("function_calls") ||
            obj.has("suppressed_calls")
        if (!hasNeedleSignature) return null

        fun parseCallArray(arr: JSONArray?, prefix: String): List<CactusToolCallRequest> {
            if (arr == null) return emptyList()
            val out = mutableListOf<CactusToolCallRequest>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val name = item.optString("name").ifBlank {
                    item.optJSONObject("function")?.optString("name").orEmpty()
                }.trim()
                val argsObj = item.optJSONObject("arguments")
                    ?: item.optJSONObject("function")?.optJSONObject("arguments")
                    ?: JSONObject()
                if (name.isNotBlank()) {
                    out.add(CactusToolCallRequest("${prefix}_$i", name, argsObj.toString()))
                }
            }
            return out
        }

        return NeedleCallEnvelope(
            type = obj.optString("type", "call"),
            success = obj.optBoolean("success", true),
            functionCalls = parseCallArray(obj.optJSONArray("function_calls") ?: obj.optJSONArray("tool_calls"), "needle_call"),
            suppressedCalls = parseCallArray(obj.optJSONArray("suppressed_calls"), "needle_sup"),
            reasoning = obj.optString("reasoning", "").trim(),
            confidence = obj.optDouble("confidence", 0.0),
            decodeTps = obj.optDouble("decode_tps", 0.0)
        )
    }

    private suspend fun emitParsedCactusNativeOutput(
        appCtx: Context,
        rawReply: String,
        userPrompt: String,
        modelTag: String,
        enablePhoneTools: Boolean,
        hybridFallbackEnabled: Boolean,
        startMs: Long,
        emitEvent: suspend (CactusStreamEvent) -> Unit
    ): Boolean {
        val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        val needle = parseNeedleCallEnvelope(rawReply)
        if (needle != null) {
            val actualTag = "cactus:needle3"
            val speedSuffix = if (needle.decodeTps > 1.0) {
                String.format(java.util.Locale.US, " (%.1fs • %.0f tok/s)", elapsedMs / 1000.0, needle.decodeTps)
            } else {
                ""
            }

            // Confidence-gated execution: reject hallucinated low-confidence calls on casual Hinglish/general prompts!
            if (shouldAutoExecuteNeedleCall(needle, enablePhoneTools)) {
                val summaries = mutableListOf<String>()
                for (toolReq in needle.functionCalls) {
                    val (ok, summary) = executeLocalDeviceTool(appCtx, toolReq.name, toolReq.argumentsJson)
                    summaries.add(summary)
                    emitEvent(
                        CactusStreamEvent.ToolExecuted(
                            toolCallId = toolReq.id,
                            toolName = toolReq.name,
                            argumentsSummary = toolReq.argumentsJson.take(120),
                            resultSummary = summary,
                            succeeded = ok
                        )
                    )
                }
                val finalOutput = if (summaries.size == 1) {
                    "⚡ ${summaries.first()}$speedSuffix"
                } else {
                    "⚡ Executed locally via Cactus Needle3$speedSuffix:\n" +
                        summaries.joinToString("\n") { "• $it" }
                }
                emitEvent(CactusStreamEvent.Token(finalOutput, finalOutput))
                emitEvent(
                    CactusStreamEvent.Completed(
                        fullText = finalOutput,
                        modelUsed = actualTag,
                        latencyMs = elapsedMs
                    )
                )
                return true
            }

            // If Needle3 matched a call with missing params (or low confidence that was held back)
            if (needle.functionCalls.isEmpty() && needle.suppressedCalls.isNotEmpty()) {
                val firstTool = needle.suppressedCalls.first().name
                val clarification = buildSuppressedToolClarification(firstTool)
                emitEvent(
                    CactusStreamEvent.HandoffToCloud(
                        offlineFriendlyReply = clarification,
                        modelUsed = actualTag,
                        latencyMs = elapsedMs,
                        decodeTps = needle.decodeTps,
                        confidence = needle.confidence,
                        canFallbackToCloud = hybridFallbackEnabled
                    )
                )
                return true
            }

            // General conversation or low-confidence (< 0.55) hallucination -> hand off to Cloud or guide to Qwen3!
            val friendlyOffline = buildFriendlyNeedleOfflineReply(userPrompt, needle.decodeTps)
            emitEvent(
                CactusStreamEvent.HandoffToCloud(
                    offlineFriendlyReply = friendlyOffline,
                    modelUsed = actualTag,
                    latencyMs = elapsedMs,
                    decodeTps = needle.decodeTps,
                    confidence = needle.confidence,
                    canFallbackToCloud = hybridFallbackEnabled
                )
            )
            return true
        }

        val requestedTools = if (enablePhoneTools) extractInlineToolCalls(rawReply) else emptyList()
        val toolSummaries = mutableListOf<String>()
        for (toolReq in requestedTools) {
            val (ok, summary) = executeLocalDeviceTool(appCtx, toolReq.name, toolReq.argumentsJson)
            toolSummaries.add(summary)
            emitEvent(
                CactusStreamEvent.ToolExecuted(
                    toolCallId = toolReq.id,
                    toolName = toolReq.name,
                    argumentsSummary = toolReq.argumentsJson.take(120),
                    resultSummary = summary,
                    succeeded = ok
                )
            )
        }
        val cleanReply = rawReply.replace(Regex("<tool_call>[\\s\\S]*?</tool_call>"), "").trim()
        val finalOutput = when {
            cleanReply.isNotBlank() && toolSummaries.isNotEmpty() ->
                "$cleanReply\n\n⚡ Local Actions:\n${toolSummaries.joinToString("\n") { "• $it" }}"
            cleanReply.isNotBlank() -> cleanReply
            toolSummaries.isNotEmpty() -> toolSummaries.joinToString("\n")
            else -> return false
        }
        emitEvent(CactusStreamEvent.Token(finalOutput, finalOutput))
        emitEvent(
            CactusStreamEvent.Completed(
                fullText = finalOutput,
                modelUsed = modelTag,
                latencyMs = elapsedMs
            )
        )
        return true
    }

    private fun buildSuppressedToolClarification(toolName: String): String = when (toolName.lowercase()) {
        "launch_app" -> "Kaunsa app open karu? Jaise: \"WhatsApp open karo\" ya \"YouTube kholo\"."
        "set_timer" -> "Kitne minute ka timer set karu? Jaise: \"5 minute ka timer lagao\"."
        "set_volume" -> "Volume kitne percent set karu (0–100)? Jaise: \"Volume 50% karo\"."
        "set_flashlight" -> "Flashlight ON karna hai ya OFF? Jaise: \"Flashlight on karo\"."
        else -> "Thoda aur clear batao kya action karna hai (jaise flashlight, volume, battery, timer, ya koi app open karna)."
    }

    private fun buildFriendlyNeedleOfflineReply(userPrompt: String, decodeTps: Double): String {
        val clean = userPrompt.trim().lowercase()
        val speedBadge = if (decodeTps > 1.0) " (${String.format(java.util.Locale.US, "%.0f", decodeTps)} tok/s)" else ""
        val isGreeting = clean in setOf("hi", "hii", "hello", "hey", "heyy", "namaste", "namaskar", "hola", "yo", "sup") ||
            clean.startsWith("hi ") || clean.startsWith("hello ") || clean.startsWith("kaise ho") ||
            clean.startsWith("good morning") || clean.startsWith("good evening")
        return if (isGreeting) {
            "Hello! Main Agentra hu — Cactus Needle3 On-Device Tool Engine$speedBadge active hai.\n\n" +
                "⚡ **Phone Control commands offline try karein:**\n" +
                "• \"Flashlight on karo\" • \"Battery aur RAM check karo\"\n" +
                "• \"Volume 50% set karo\" • \"WhatsApp open karo\" • \"5 minute timer lagao\"\n\n" +
                "💬 Full offline chat ke liye **Settings → Cactus On-Device AI → Qwen3 0.6B (ya Gemma 3 270M)** download karke select karein!"
        } else {
            "🌵 Cactus **Needle3**$speedBadge sirf **Phone Tool-Calling Engine** hai (general chatbot nahi), isliye normal sawaal par tool match nahi hua.\n\n" +
                "💬 **Offline General Chat** ke liye Cactus Manager se **Qwen3 0.6B** (ya **Gemma 3 270M**) download karein, ya online par Hybrid Cloud Fallback ON rakhein."
        }
    }

    private fun extractNativeCactusHttpReply(rawBody: String): String {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty() || trimmed == "{}") return ""
        val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return trimmed

        if (obj.optString("type").equals("call", ignoreCase = true) ||
            obj.has("function_calls") ||
            obj.has("suppressed_calls")
        ) {
            return trimmed
        }

        val directText = listOf("response", "text", "output", "content", "reply", "result")
            .firstNotNullOfOrNull { key ->
                obj.optString(key, "").trim().takeIf { it.isNotBlank() && it != "null" }
            }
        if (!directText.isNullOrBlank()) return directText

        val choiceContent = obj.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.let { c ->
                c.optJSONObject("message")?.optString("content").orEmpty().ifBlank { c.optString("text").orEmpty() }
            }
            ?.trim()
            .orEmpty()
        if (choiceContent.isNotBlank()) return choiceContent

        return trimmed
    }

    /**
     * Runs native Cactus v4 CLI (`cactus --model file.cact [--tools tools.json] [--system system.txt] [--prompt "..."] [--audio clip.wav]`).
     */
    private suspend fun runAdaptiveNativeCli(
        context: Context,
        modelTarget: String,
        prompt: String? = null,
        imagePath: String? = null,
        audioPath: String? = null,
        includeTools: Boolean = false,
        timeoutSeconds: Long = 45L
    ): Result<String> {
        val caps = CactusNativeRuntime.getBinaryCliCapabilities(context)
        val isV4CactOnlyBinary = caps.hasDoubleDashServe && caps.hasDoubleDashModel && !caps.hasRunSubcommand
        val targetFile = File(modelTarget)
        if (isV4CactOnlyBinary && (!targetFile.isFile || !modelTarget.endsWith(".cact", ignoreCase = true))) {
            return Result.failure(
                IllegalStateException(
                    "Cactus v4 CLI sirf `.cact` files run karta hai. Qwen3/Gemma/LFM2-VL in-process SDK se directly chalte hain."
                )
            )
        }
        if (!imagePath.isNullOrBlank() && !caps.hasDoubleDashImage) {
            return Result.failure(
                IllegalStateException("Installed Cactus v4 binary does not accept --image CLI flag.")
            )
        }

        val rtFlags = buildList {
            if (!imagePath.isNullOrBlank() && caps.hasDoubleDashImage) addAll(listOf("--image", imagePath))
            if (!audioPath.isNullOrBlank() && caps.hasDoubleDashAudio) addAll(listOf("--audio", audioPath))
            if (includeTools && audioPath.isNullOrBlank()) {
                val (toolsFile, systemFile) = CactusNativeRuntime.prepareRuntimeToolsAndSystemFiles(context)
                if (caps.hasDoubleDashTools && toolsFile.exists()) addAll(listOf("--tools", toolsFile.absolutePath))
                if (caps.hasDoubleDashSystem && systemFile.exists()) addAll(listOf("--system", systemFile.absolutePath))
            }
        }

        val baseModelFlag = listOf("--model", modelTarget) + rtFlags
        val candidates = buildList<Pair<List<String>, String?>> {
            if (caps.hasDoubleDashModel) {
                if (!prompt.isNullOrBlank()) {
                    if (caps.hasDoubleDashPrompt) {
                        add((baseModelFlag + listOf("--prompt", prompt)) to null)
                    } else {
                        add(baseModelFlag to prompt)
                    }
                } else {
                    add(baseModelFlag to null)
                }
            } else {
                val basePositional = listOf(modelTarget) + rtFlags
                if (!prompt.isNullOrBlank()) {
                    if (caps.hasDoubleDashPrompt) add((basePositional + listOf("--prompt", prompt)) to null)
                    add(basePositional to prompt)
                } else {
                    add(basePositional to null)
                }
            }
            if (caps.hasRunSubcommand) {
                val runArgs = if (!prompt.isNullOrBlank()) {
                    listOf("run", modelTarget) + rtFlags + listOf("--prompt", prompt)
                } else {
                    listOf("run", modelTarget) + rtFlags
                }
                add(runArgs to null)
            }
        }

        var lastFailure: Result<String>? = null
        for ((args, stdinText) in candidates) {
            val res = CactusNativeRuntime.executeNativeCli(
                context = context,
                args = args,
                timeoutSeconds = timeoutSeconds,
                stdinInput = stdinText
            )
            if (res.isSuccess) return res
            lastFailure = res
            val msg = res.exceptionOrNull()?.message.orEmpty()
            if (msg.contains("timed out", ignoreCase = true) ||
                msg.contains("cannot load", ignoreCase = true) ||
                msg.contains("unreadable", ignoreCase = true) ||
                msg.contains("not found", ignoreCase = true)
            ) {
                break
            }
        }
        return lastFailure ?: Result.failure(IllegalStateException("Cactus native CLI execution failed."))
    }

    private fun buildMessagesArray(
        systemInstruction: String,
        history: List<ChatMessageEntity>,
        userPrompt: String
    ): JSONArray {
        val arr = JSONArray()
        if (systemInstruction.isNotBlank()) {
            arr.put(JSONObject().put("role", "system").put("content", systemInstruction.take(4000)))
        }
        history.takeLast(8).forEach { msg ->
            val role = if (msg.role.equals("user", ignoreCase = true)) "user" else "assistant"
            val content = msg.content.trim().take(1200)
            if (content.isNotBlank()) {
                arr.put(JSONObject().put("role", role).put("content", content))
            }
        }
        arr.put(JSONObject().put("role", "user").put("content", userPrompt))
        return arr
    }

    private fun buildLocalPhoneToolsSchema(): JSONArray {
        fun toolDef(name: String, desc: String, props: JSONObject, required: List<String> = emptyList()): JSONObject =
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", name)
                    put("description", desc)
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", props)
                        if (required.isNotEmpty()) put("required", JSONArray(required))
                    })
                })
            }

        return JSONArray().apply {
            put(
                toolDef(
                    name = "set_flashlight",
                    desc = "Turn the device flashlight or torch ON or OFF.",
                    props = JSONObject().put("enabled", JSONObject().put("type", "boolean")),
                    required = listOf("enabled")
                )
            )
            put(
                toolDef(
                    name = "set_volume",
                    desc = "Set device media volume percentage from 0 to 100.",
                    props = JSONObject().put("percent", JSONObject().put("type", "integer")),
                    required = listOf("percent")
                )
            )
            put(
                toolDef(
                    name = "get_device_telemetry",
                    desc = "Get current device battery level, charging state, free RAM, and India IST time.",
                    props = JSONObject()
                )
            )
            put(
                toolDef(
                    name = "set_timer",
                    desc = "Set a countdown timer on the device in minutes.",
                    props = JSONObject().put("minutes", JSONObject().put("type", "integer")),
                    required = listOf("minutes")
                )
            )
            put(
                toolDef(
                    name = "launch_app",
                    desc = "Launch an installed Android app by name (e.g. WhatsApp, YouTube, Settings).",
                    props = JSONObject().put("app_name", JSONObject().put("type", "string")),
                    required = listOf("app_name")
                )
            )
            put(
                toolDef(
                    name = "invoke_mcp_telemetry",
                    desc = "Read embedded Android MCP OS telemetry locally.",
                    props = JSONObject()
                )
            )
        }
    }

    private fun extractInlineToolCalls(text: String): List<CactusToolCallRequest> {
        val list = mutableListOf<CactusToolCallRequest>()
        val regex = Regex("<tool_call>\\s*(\\{[\\s\\S]*?\\})\\s*</tool_call>")
        regex.findAll(text).forEachIndexed { idx, match ->
            val rawJson = match.groupValues[1]
            runCatching {
                val obj = JSONObject(rawJson)
                val name = obj.optString("name").trim()
                val argsObj = obj.optJSONObject("arguments") ?: JSONObject()
                if (name.isNotBlank()) {
                    list.add(CactusToolCallRequest("cactus_inline_$idx", name, argsObj.toString()))
                }
            }
        }
        return list
    }

    private suspend fun executeLocalDeviceTool(
        context: Context,
        toolName: String,
        argsJson: String
    ): Pair<Boolean, String> {
        val args = runCatching { JSONObject(argsJson.ifBlank { "{}" }) }.getOrDefault(JSONObject())
        val phoneControls = PhoneControlsManager(context)
        return try {
            when (toolName.trim().lowercase()) {
                "set_flashlight" -> {
                    val on = args.optBoolean("enabled", true)
                    val res = phoneControls.setFlashlight(on)
                    res.success to "Flashlight ${if (on) "ON" else "OFF"} (${res.message})"
                }
                "set_volume" -> {
                    val pct = args.optInt("percent", 50).coerceIn(0, 100)
                    phoneControls.systemStatusManager.controlVolume(targetPercent = pct)
                    true to "Media volume set to $pct%."
                }
                "get_device_telemetry" -> {
                    val sys = SystemStatusManager(context)
                    val bat = sys.getBatteryInfo()
                    val ram = sys.getStorageAndRamInfo()
                    val timeStr = com.jarvisai.assistant.util.IndiaTimeUtils.nowTime12h()
                    true to "IST $timeStr • Battery ${bat.levelPercent}% (${if (bat.isCharging) "Charging" else "Discharging"}) • Free RAM ${ram.availableRamMb} MB"
                }
                "set_timer" -> {
                    val mins = args.optInt("minutes", 5).coerceIn(1, 720)
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, mins * 60)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true to "$mins minute offline timer started."
                }
                "launch_app" -> {
                    val appName = args.optString("app_name").trim()
                    if (appName.isBlank()) {
                        false to "Missing app_name argument."
                    } else {
                        val launchRes = AppLauncherManager(context, phoneControls).launchApp(appName = appName, allowBrowserFallback = false)
                        launchRes.executed to if (launchRes.executed) "Launched ${launchRes.appName}" else "App '$appName' not found."
                    }
                }
                "invoke_mcp_telemetry" -> {
                    val res = McpClientBridge.invokeMcpTool(
                        context = context,
                        serverIdOrToolName = "mcp_android_os_telemetry",
                        toolName = "get_device_telemetry"
                    )
                    res.success to res.spokenHinglishSummary
                }
                else -> false to "Unknown local Cactus tool: $toolName"
            }
        } catch (e: Exception) {
            false to "Tool '$toolName' error: ${e.message}"
        }
    }
}
