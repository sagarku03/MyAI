package com.jarvisai.assistant.cactus

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.flow.flow
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
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * High-level multimodal AI service powered by Cactus Compute (`cactus-compute/cactus`).
 *
 * Powers all 6 on-device features across Agentra:
 * 1. Streaming On-Device Chat (`POST /v1/chat/completions` SSE + native `cactus run` fallback)
 * 2. Fast On-Device Phone Controls & MCP Tool Calling (OpenAI `tools` schema + `<tool_call>` parsing)
 * 3. Private Offline Vision & Optical Solver (`Cactus-Compute/mirage` / `gemma-4-E2B-it`)
 * 4. Private Offline Document RAG Q&A (semantic chunking + local LLM summarization)
 * 5. Offline Speech-to-Text (`Cactus-Compute/whistle` / `whisper-small`)
 * 6. Local Semantic Embeddings (`POST /v1/embeddings`) for 5-Tier Cognitive Memory Center
 */
object CactusAiService {
    private const val TAG = "CactusAiService"

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

    /**
     * Streams a chat completion from the local Cactus engine with real on-device tool calling.
     */
    fun streamChatCompletion(
        context: Context,
        modelId: String,
        userPrompt: String,
        conversationHistory: List<ChatMessageEntity>,
        systemInstruction: String,
        enablePhoneTools: Boolean = true
    ): Flow<CactusStreamEvent> = flow {
        val appCtx = context.applicationContext
        val startMs = System.currentTimeMillis()
        val cfg = CactusNativeRuntime.loadConfig(appCtx)
        val spec = CactusModelCatalog.findById(modelId)
            ?: CactusModelCatalog.findById(cfg.activeLlmModelId)
            ?: CactusModelCatalog.allModels.first()

        emit(CactusStreamEvent.Status("🌵 Cactus On-Device (${spec.displayName}) initializing..."))

        // Auto-detect server reachability; if binary is installed & server isn't up yet, try booting it once
        var serverProbe = CactusNativeRuntime.probeServerEndpoint(cfg.serverBaseUrl)
        if (!serverProbe.first && CactusNativeRuntime.resolveCactusBinaryPath(appCtx) != null &&
            CactusNativeRuntime.isModelDownloaded(spec.id)
        ) {
            emit(CactusStreamEvent.Status("🌵 Starting local Cactus server for ${spec.displayName}..."))
            CactusNativeRuntime.startLocalServer(appCtx, spec)
            serverProbe = CactusNativeRuntime.probeServerEndpoint(cfg.serverBaseUrl)
        }

        // PATH 1: Local OpenAI-Compatible `cactus serve` HTTP SSE Streaming (supports Tool Calling!)
        if (serverProbe.first) {
            val baseUrl = cfg.serverBaseUrl.trim().removeSuffix("/")
            val serverModelName = serverProbe.second.ifBlank { spec.slug }
            val messagesArray = buildMessagesArray(systemInstruction, conversationHistory, userPrompt)

            val payload = JSONObject().apply {
                put("model", serverModelName)
                put("messages", messagesArray)
                put("stream", true)
                put("temperature", cfg.temperature.toDouble())
                put("max_tokens", 1024)
                if (enablePhoneTools && spec.supportsToolCalling) {
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
                                emit(CactusStreamEvent.Token(accText.toString(), contentPiece))
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

                // Execute any structured tool calls emitted by Cactus (either OpenAI delta tool_calls or inline <tool_call>)
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
                    emit(
                        CactusStreamEvent.ToolExecuted(
                            toolCallId = toolReq.id,
                            toolName = toolReq.name,
                            argumentsSummary = toolReq.argumentsJson.take(120),
                            resultSummary = summary,
                            succeeded = ok
                        )
                    )
                }

                val cleanReply = accText.toString()
                    .replace(Regex("<tool_call>[\\s\\S]*?</tool_call>"), "")
                    .trim()
                val finalOutput = when {
                    cleanReply.isNotBlank() && toolSummaries.isNotEmpty() ->
                        "$cleanReply\n\n⚡ Local Actions:\n${toolSummaries.joinToString("\n") { "• $it" }}"
                    cleanReply.isNotBlank() -> cleanReply
                    toolSummaries.isNotEmpty() -> toolSummaries.joinToString("\n")
                    else -> "Cactus local model returned an empty response."
                }

                emit(
                    CactusStreamEvent.Completed(
                        fullText = finalOutput,
                        modelUsed = "cactus:${spec.slug}",
                        latencyMs = System.currentTimeMillis() - startMs,
                        promptTokens = promptTok,
                        outputTokens = outTok
                    )
                )
                return@flow
            } catch (e: Exception) {
                Log.w(TAG, "Cactus HTTP stream failed, checking CLI fallback: ${e.message}")
            }
        }

        // PATH 2: Direct Native ARM64 `cactus run` CLI execution
        if (CactusNativeRuntime.resolveCactusBinaryPath(appCtx) != null) {
            val localDir = CactusNativeRuntime.getModelDir(appCtx, spec)
            val modelTarget = if (CactusNativeRuntime.isModelDownloaded(spec.id)) {
                localDir.absolutePath
            } else {
                spec.huggingFaceRepo
            }
            val compactPrompt = buildString {
                if (systemInstruction.isNotBlank()) {
                    appendLine("[System]: ${systemInstruction.take(700)}")
                }
                appendLine("[User]: $userPrompt")
            }
            emit(CactusStreamEvent.Status("🌵 Running native Cactus CLI (${spec.displayName})..."))
            val cliArgs = mutableListOf("run", modelTarget, "--prompt", compactPrompt, "--bits", spec.defaultBits.toString())
            if (cfg.computeBackend != CactusComputeBackend.AUTO) {
                cliArgs.addAll(listOf("--backend", cfg.computeBackend.cliFlag))
            }
            val cliRes = CactusNativeRuntime.executeNativeCli(appCtx, cliArgs, timeoutSeconds = 60L)
            cliRes.onSuccess { text ->
                emit(CactusStreamEvent.Token(text, text))
                emit(
                    CactusStreamEvent.Completed(
                        fullText = text,
                        modelUsed = "cactus:${spec.slug}",
                        latencyMs = System.currentTimeMillis() - startMs
                    )
                )
                return@flow
            }.onFailure { err ->
                emit(
                    CactusStreamEvent.Error(
                        message = "Cactus Local Engine error: ${err.message}",
                        canFallbackToCloud = cfg.hybridCloudFallbackEnabled
                    )
                )
                return@flow
            }
        }

        // Neither local server nor binary is ready — emit honest actionable guidance (never fake text)
        emit(
            CactusStreamEvent.Error(
                message = "Cactus On-Device Engine abhi setup nahi hai ya local server (${cfg.serverBaseUrl}) band hai. " +
                    "Settings → Cactus On-Device AI me jaakar engine install karein ya `cactus serve` start karein.",
                canFallbackToCloud = cfg.hybridCloudFallbackEnabled
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Private Offline Vision analysis (VisionScreen & OpticalSolverScreen) using Cactus VLM (Mirage / Gemma-4-E2B).
     */
    suspend fun analyzeImageOffline(
        context: Context,
        imageBase64: String,
        mimeType: String = "image/jpeg",
        prompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val cfg = CactusNativeRuntime.loadConfig(appCtx)
        val vlmSpec = CactusModelCatalog.findById(cfg.activeVlmModelId)
            ?: CactusModelCatalog.visionCapableModels().first()

        // 1. Try local OpenAI-compatible multimodal endpoint (`cactus serve`)
        val serverProbe = CactusNativeRuntime.probeServerEndpoint(cfg.serverBaseUrl)
        if (serverProbe.first) {
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
                    put("model", serverProbe.second.ifBlank { vlmSpec.slug })
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

        // 2. Fallback to native CLI `cactus run <vlmModel> --image <tmpFile> --prompt <prompt>`
        if (CactusNativeRuntime.resolveCactusBinaryPath(appCtx) != null) {
            val tmpImg = File(appCtx.cacheDir, "cactus_vlm_${System.currentTimeMillis()}.jpg")
            try {
                val rawBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                tmpImg.writeBytes(rawBytes)
                val modelTarget = if (CactusNativeRuntime.isModelDownloaded(vlmSpec.id)) {
                    CactusNativeRuntime.getModelDir(appCtx, vlmSpec).absolutePath
                } else {
                    vlmSpec.huggingFaceRepo
                }
                val cliRes = CactusNativeRuntime.executeNativeCli(
                    context = appCtx,
                    args = listOf("run", modelTarget, "--image", tmpImg.absolutePath, "--prompt", prompt),
                    timeoutSeconds = 90L
                )
                if (cliRes.isSuccess) return@withContext cliRes
            } finally {
                runCatching { tmpImg.delete() }
            }
        }

        Result.failure(
            IllegalStateException(
                "Cactus Offline Vision (${vlmSpec.displayName}) ready nahi hai. " +
                    "Settings → Cactus On-Device AI me engine install karein aur Mirage/Gemma VLM download ya start karein."
            )
        )
    }

    /**
     * Private Offline Document Q&A & Summarization using Cactus Local Embeddings RAG + Cactus LLM.
     */
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
                is CactusStreamEvent.Error -> failureMsg = ev.message
                else -> Unit
            }
        }

        if (collected.isNotBlank()) {
            Result.success(collected)
        } else {
            Result.failure(IllegalStateException(failureMsg ?: "Cactus offline document analysis failed."))
        }
    }

    /**
     * 100% Offline Audio Transcription via Cactus Whistle / Whisper-Small.
     * Supports local `/v1/audio/transcriptions` or `cactus run <sttModel> --audio <wavFile>`.
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
        val sttSpec = CactusModelCatalog.findById(cfg.activeSttModelId)
            ?: CactusModelCatalog.sttModels().first()

        // 1. Try local OpenAI-compatible `/v1/audio/transcriptions`
        val serverProbe = CactusNativeRuntime.probeServerEndpoint(cfg.serverBaseUrl)
        if (serverProbe.first) {
            try {
                val baseUrl = cfg.serverBaseUrl.trim().removeSuffix("/")
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("model", sttSpec.slug)
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

        // 2. Fallback to native CLI `cactus run <sttModel> --audio <wavFile>`
        if (CactusNativeRuntime.resolveCactusBinaryPath(appCtx) != null) {
            val modelTarget = if (CactusNativeRuntime.isModelDownloaded(sttSpec.id)) {
                CactusNativeRuntime.getModelDir(appCtx, sttSpec).absolutePath
            } else {
                sttSpec.huggingFaceRepo
            }
            val cliRes = CactusNativeRuntime.executeNativeCli(
                context = appCtx,
                args = listOf("run", modelTarget, "--audio", wavFile.absolutePath),
                timeoutSeconds = 45L
            )
            if (cliRes.isSuccess) return@withContext cliRes
        }

        Result.failure(
            IllegalStateException(
                "Cactus Offline STT (${sttSpec.displayName}) ready nahi hai. Settings → Cactus On-Device AI me Whistle ya Whisper download/start karein."
            )
        )
    }

    /**
     * Computes a dense semantic embedding vector via Cactus local server (`POST /v1/embeddings`).
     * Returns null cleanly if server/embeddings endpoint is unavailable (never blocks UI).
     */
    suspend fun computeLocalEmbedding(
        context: Context,
        text: String,
        quickTimeout: Boolean = false
    ): FloatArray? = withContext(Dispatchers.IO) {
        val clean = text.trim()
        if (clean.isEmpty()) return@withContext null
        val cfg = CactusNativeRuntime.config.value
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

    /**
     * One-shot non-streaming offline text completion helper (used by PocketStrikeOfflineEngine fallback).
     */
    suspend fun quickOfflinePrompt(
        context: Context,
        prompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        var answer = ""
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
                is CactusStreamEvent.Error -> errMsg = ev.message
                else -> Unit
            }
        }
        if (answer.isNotBlank()) Result.success(answer)
        else Result.failure(IllegalStateException(errMsg ?: "Cactus offline inference unavailable"))
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
        fun toolDef(name: String, desc: String, props: JSONObject, required: List<String> = emptyList()): JSONObject {
            return JSONObject().put("type", "function").put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put("description", desc)
                    .put(
                        "parameters",
                        JSONObject()
                            .put("type", "object")
                            .put("properties", props)
                            .put("required", JSONArray(required))
                    )
            )
        }

        return JSONArray().apply {
            put(
                toolDef(
                    name = "set_flashlight",
                    desc = "Turn device flashlight/torch ON or OFF offline.",
                    props = JSONObject().put("enabled", JSONObject().put("type", "boolean")),
                    required = listOf("enabled")
                )
            )
            put(
                toolDef(
                    name = "set_volume",
                    desc = "Set device media volume percentage (0 to 100).",
                    props = JSONObject().put("percent", JSONObject().put("type", "integer")),
                    required = listOf("percent")
                )
            )
            put(
                toolDef(
                    name = "get_device_telemetry",
                    desc = "Get current battery percent, charging status, free RAM, and India IST time.",
                    props = JSONObject()
                )
            )
            put(
                toolDef(
                    name = "set_timer",
                    desc = "Start an Android countdown timer in minutes.",
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

    /**
     * Executes structured local phone tool calls emitted by on-device Cactus models (Qwen3 / FunctionGemma / Needle3).
     */
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
