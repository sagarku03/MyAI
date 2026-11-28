package com.jarvisai.assistant.cactus

import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import com.cactus.CactusCompletionParams
import com.cactus.CactusContextInitializer
import com.cactus.CactusInitParams
import com.cactus.CactusLM
import com.cactus.CactusSTT
import com.cactus.CactusTranscriptionParams
import com.cactus.ChatMessage
import com.cactus.InferenceMode
import com.cactus.ToolCall
import com.cactus.TranscriptionMode
import com.cactus.models.CactusTool
import com.cactus.models.ToolParameter
import com.cactus.models.createTool
import com.cactus.services.CactusConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * In-process native JNI adapter around `com.cactuscompute:cactus:1.4.3-beta` (`libcactus.so`).
 *
 * Safely guards against 32-bit devices or JVM/Robolectric unit test environments where
 * `arm64-v8a/libcactus.so` cannot load, disables background telemetry by default, reuses
 * initialized handles under a mutex, and cleanly strips Qwen3 `<think>` blocks.
 */
object CactusInProcessEngine {
    private const val TAG = "CactusInProcessEngine"
    private val DEFAULT_STOP_SEQUENCES = listOf("<|im_end|>", "<|endoftext|>", "<|eot_id|>", "<end_of_turn>")
    private val CONTROL_TOKEN_REGEX = Regex(
        "<\\|(?:im_end|im_start|endoftext|eot_id|end|assistant|user|system)\\|>|<end_of_turn>|<start_of_turn>",
        RegexOption.IGNORE_CASE
    )
    private val TRAILING_PARTIAL_TAG_REGEX = Regex(
        "(?:<\\|[^\\n|>]*|<(?:end|start)_of_[^\\n>]*|<think[^\\n>]*)$",
        RegexOption.IGNORE_CASE
    )
    private val APP_LAUNCH_INTENT_REGEX = Regex(
        "\\b(?:open|launch|kholo|khol|chalu|whatsapp|youtube|instagram|camera|settings|chrome|spotify|maps|calculator|clock)\\b",
        RegexOption.IGNORE_CASE
    )
    private val THINK_BLOCK_REGEX = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE)

    private val engineMutex = Mutex()
    @Volatile
    private var contextInitialized = false
    @Volatile
    private var nativeLibStatus: Boolean? = null

    private var activeLm: CactusLM? = null
    private var activeLmSlug: String? = null
    private var activeLmCtxSize: Int = 2048

    private var activeStt: CactusSTT? = null
    private var activeSttSlug: String? = null

    fun isArm64Abi(): Boolean =
        Build.SUPPORTED_ABIS.any { it.contains("arm64", ignoreCase = true) }

    fun isAvailable(context: Context? = null): Boolean {
        if (!isArm64Abi()) return false
        val cached = nativeLibStatus
        if (cached != null) {
            if (cached && context != null) ensureInitialized(context)
            return cached
        }
        val ok = try {
            System.loadLibrary("cactus")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: Throwable) {
            false
        }
        nativeLibStatus = ok
        if (ok && context != null) {
            ensureInitialized(context)
        }
        return ok
    }

    fun ensureInitialized(context: Context): Boolean {
        if (!isArm64Abi()) return false
        return try {
            CactusConfig.isTelemetryEnabled = false
            if (!contextInitialized) {
                CactusContextInitializer.initialize(context.applicationContext)
                contextInitialized = true
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Cactus SDK init skipped: ${t.message}")
            false
        }
    }

    fun getActiveLoadedSlug(): String = activeLmSlug.orEmpty()

    fun getSdkModelsRootDir(context: Context): File =
        File(context.applicationContext.filesDir, "models")

    fun getSdkModelDir(context: Context, sdkSlug: String): File =
        File(getSdkModelsRootDir(context), sdkSlug)

    fun isSdkModelOnDisk(context: Context, sdkSlug: String): Boolean {
        val dir = getSdkModelDir(context, sdkSlug)
        if (!dir.exists() || !dir.isDirectory) return false
        val files = dir.listFiles().orEmpty()
        return files.isNotEmpty()
    }

    suspend fun resolveDownloadUrl(spec: CactusModelSpec): String? = withContext(Dispatchers.IO) {
        if (isAvailable()) {
            val remote = runCatching {
                when (spec.capability) {
                    CactusModelCapability.STT ->
                        CactusSTT().getVoiceModels().firstOrNull { it.slug.equals(spec.sdkSlug, ignoreCase = true) }?.download_url
                    else ->
                        CactusLM().getModels().firstOrNull { it.slug.equals(spec.sdkSlug, ignoreCase = true) }?.download_url
                }
            }.getOrNull()
            if (!remote.isNullOrBlank()) return@withContext remote
        }
        spec.directZipUrl.takeIf { it.isNotBlank() }
    }

    suspend fun prewarmModelIfReady(context: Context, modelId: String) = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        if (!isAvailable(appCtx)) return@withContext
        val cfg = CactusNativeRuntime.loadConfig(appCtx)
        val requested = CactusModelCatalog.findById(modelId)
            ?: CactusModelCatalog.findById(cfg.activeLlmModelId)
            ?: return@withContext
        if (!requested.isInProcessSdkModel ||
            (requested.capability != CactusModelCapability.LLM && requested.capability != CactusModelCapability.TOOL_CALLING)
        ) {
            return@withContext
        }
        if (!isSdkModelOnDisk(appCtx, requested.sdkSlug)) return@withContext
        engineMutex.withLock {
            runCatching {
                ensureLmLoadedLocked(appCtx, requested.sdkSlug, cfg.maxContextTokens.coerceIn(1024, 4096))
            }.onFailure { err ->
                Log.w(TAG, "Background prewarm skipped (${requested.sdkSlug}): ${err.message}")
            }
        }
    }

    suspend fun unloadAll() = engineMutex.withLock {
        runCatching { activeLm?.unload() }
        activeLm = null
        activeLmSlug = null
        activeStt = null
        activeSttSlug = null
    }

    suspend fun generateChatOrToolCompletion(
        context: Context,
        spec: CactusModelSpec,
        systemInstruction: String,
        conversationMessages: List<Pair<String, String>>,
        userPrompt: String,
        enablePhoneTools: Boolean,
        temperature: Float,
        maxContextTokens: Int,
        maxOutputTokens: Int = 768,
        onPartialUpdate: (String) -> Unit
    ): SdkChatOutcome = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        if (!isAvailable(appCtx)) {
            return@withContext SdkChatOutcome.Failure("Cactus arm64 in-process native library is unavailable on this device.")
        }
        if (!isSdkModelOnDisk(appCtx, spec.sdkSlug)) {
            return@withContext SdkChatOutcome.Failure("Model '${spec.displayName}' abhi download nahi hai.")
        }

        val startMs = System.currentTimeMillis()
        val isQwen3 = spec.sdkSlug.startsWith("qwen3", ignoreCase = true)
        val allowThinking = isQwen3 && shouldEnableQwenThinking(userPrompt)
        val effectiveMaxTokens = if (maxOutputTokens != 768) {
            maxOutputTokens
        } else if (isQwen3 && !allowThinking) {
            384
        } else {
            768
        }

        engineMutex.withLock {
            try {
                val lm = ensureLmLoadedLocked(appCtx, spec.sdkSlug, maxContextTokens.coerceIn(1024, 4096))
                // Clear stale KV prefix state between turns (~1ms, keeps weights in RAM) so prior turns never pollute generation.
                runCatching { lm.reset() }
                val cleanUserTurn = formatUserTurnPrompt(
                    userPrompt = userPrompt,
                    isQwen3 = isQwen3,
                    allowThinking = allowThinking
                )
                val chatMessages = buildList {
                    val effectiveSystem = systemInstruction.trim().take(700)
                    if (effectiveSystem.isNotBlank()) {
                        add(ChatMessage(content = effectiveSystem, role = "system"))
                    }
                    conversationMessages.takeLast(4).forEach { (role, content) ->
                        val clean = stripThinkingMarkup(content).take(400)
                        if (clean.isNotBlank()) {
                            add(ChatMessage(content = clean, role = if (role.equals("user", true)) "user" else "assistant"))
                        }
                    }
                    add(ChatMessage(content = cleanUserTurn, role = "user"))
                }

                // Only attach phone tool JSON schemas when prompt signals device/app intent;
                // keeping tools empty for casual chat avoids 400+ schema tokens and KV-cache resets.
                val sdkTools = selectRelevantSdkPhoneTools(
                    userPrompt = userPrompt,
                    enableTools = enablePhoneTools && spec.supportsToolCalling
                )
                val accRaw = StringBuilder()
                var lastEmitted = ""
                val res = lm.generateCompletion(
                    messages = chatMessages,
                    params = CactusCompletionParams(
                        model = spec.sdkSlug,
                        temperature = temperature.toDouble().coerceIn(0.05, 1.2),
                        maxTokens = effectiveMaxTokens,
                        stopSequences = DEFAULT_STOP_SEQUENCES,
                        tools = sdkTools,
                        mode = InferenceMode.LOCAL
                    ),
                    onToken = { token, _ ->
                        accRaw.append(token)
                        val visible = stripThinkingMarkup(accRaw.toString())
                        if (visible.isNotBlank() && visible != lastEmitted) {
                            lastEmitted = visible
                            onPartialUpdate(visible)
                        }
                    }
                )

                val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                if (res == null || !res.success) {
                    val err = res?.response?.takeIf { it.isNotBlank() } ?: "In-process completion returned empty result."
                    SdkChatOutcome.Failure(err)
                } else {
                    val cleanText = stripThinkingMarkup(res.response.orEmpty().ifBlank { accRaw.toString() })
                    val mappedTools = res.toolCalls.orEmpty().mapIndexed { idx, tc: ToolCall ->
                        val argsObj = JSONObject()
                        tc.arguments.forEach { (k, v) -> argsObj.put(k, parseTypedScalar(v)) }
                        CactusToolCallRequest(
                            id = "cactus_sdk_${idx}_${tc.name}",
                            name = tc.name,
                            argumentsJson = argsObj.toString()
                        )
                    }

                    SdkChatOutcome.Success(
                        text = cleanText,
                        toolCalls = mappedTools,
                        promptTokens = res.prefillTokens ?: 0,
                        outputTokens = res.decodeTokens ?: 0,
                        latencyMs = elapsed,
                        tps = res.tokensPerSecond ?: 0.0
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Cactus SDK chat failure (${spec.sdkSlug}): ${t.message}", t)
                SdkChatOutcome.Failure(t.message ?: "Cactus in-process inference failed.")
            }
        }
    }

    suspend fun analyzeImage(
        context: Context,
        spec: CactusModelSpec,
        systemInstruction: String,
        userPrompt: String,
        imageBase64: String,
        maxTokens: Int = 512
    ): Result<String> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        if (!isAvailable(appCtx)) {
            return@withContext Result.failure(IllegalStateException("Cactus arm64 in-process native library unavailable."))
        }
        if (!isSdkModelOnDisk(appCtx, spec.sdkSlug)) {
            return@withContext Result.failure(IllegalStateException("Vision model '${spec.displayName}' download nahi hai."))
        }

        val tmpFile = File(appCtx.cacheDir, "cactus_sdk_vlm_${System.currentTimeMillis()}.jpg")
        try {
            tmpFile.writeBytes(Base64.decode(imageBase64, Base64.DEFAULT))
            engineMutex.withLock {
                val lm = ensureLmLoadedLocked(appCtx, spec.sdkSlug, 2048)
                val messages = buildList {
                    if (systemInstruction.isNotBlank()) {
                        add(ChatMessage(content = systemInstruction.take(1500), role = "system"))
                    }
                    add(
                        ChatMessage(
                            content = userPrompt.take(2500),
                            role = "user",
                            images = listOf(tmpFile.absolutePath)
                        )
                    )
                }
                val res = lm.generateCompletion(
                    messages = messages,
                    params = CactusCompletionParams(
                        model = spec.sdkSlug,
                        temperature = 0.2,
                        maxTokens = maxTokens,
                        mode = InferenceMode.LOCAL
                    )
                )
                val text = stripThinkingMarkup(res?.response.orEmpty())
                if (res?.success == true && text.isNotBlank()) {
                    Result.success(text)
                } else {
                    Result.failure(IllegalStateException(text.ifBlank { "Cactus SDK vision returned empty output." }))
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Cactus SDK vision failed: ${t.message}", t)
            Result.failure(t)
        } finally {
            runCatching { tmpFile.delete() }
        }
    }

    suspend fun transcribeAudio(
        context: Context,
        spec: CactusModelSpec,
        wavFile: File
    ): Result<String> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        if (!isAvailable(appCtx)) {
            return@withContext Result.failure(IllegalStateException("Cactus arm64 in-process native library unavailable."))
        }
        if (!isSdkModelOnDisk(appCtx, spec.sdkSlug)) {
            return@withContext Result.failure(IllegalStateException("STT model '${spec.displayName}' download nahi hai."))
        }
        engineMutex.withLock {
            try {
                // Free heavy LLM context if needed before Whisper load on tight RAM devices
                val stt = if (activeStt != null && activeSttSlug == spec.sdkSlug) {
                    activeStt!!
                } else {
                    CactusSTT().also {
                        it.initializeModel(CactusInitParams(model = spec.sdkSlug))
                        activeStt = it
                        activeSttSlug = spec.sdkSlug
                    }
                }
                val res = stt.transcribe(
                    filePath = wavFile.absolutePath,
                    params = CactusTranscriptionParams(
                        model = spec.sdkSlug,
                        maxTokens = 512
                    ),
                    mode = TranscriptionMode.LOCAL
                )
                val clean = res?.text
                    ?.replace(Regex("<\\|[^>]+\\|>"), "")
                    ?.trim()
                    .orEmpty()
                if (res?.success == true && clean.isNotBlank()) {
                    Result.success(clean)
                } else {
                    Result.failure(IllegalStateException(res?.errorMessage ?: "Speech detected nahi hui ya STT fail hua."))
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Cactus SDK STT failed: ${t.message}", t)
                Result.failure(t)
            }
        }
    }

    suspend fun generateEmbedding(
        context: Context,
        spec: CactusModelSpec,
        text: String
    ): FloatArray? = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        if (!isAvailable(appCtx) || !isSdkModelOnDisk(appCtx, spec.sdkSlug)) return@withContext null
        engineMutex.withLock {
            try {
                val lm = ensureLmLoadedLocked(appCtx, spec.sdkSlug, 1024)
                val res = lm.generateEmbedding(text = text.take(1200), modelName = spec.sdkSlug)
                if (res?.success == true && res.embeddings.isNotEmpty()) {
                    FloatArray(res.embeddings.size) { i -> res.embeddings[i].toFloat() }
                } else {
                    null
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Cactus SDK embed failed (${spec.sdkSlug}): ${t.message}")
                null
            }
        }
    }

    private suspend fun ensureLmLoadedLocked(
        appCtx: Context,
        sdkSlug: String,
        contextSize: Int
    ): CactusLM {
        val cur = activeLm
        if (cur != null && activeLmSlug == sdkSlug && activeLmCtxSize == contextSize && cur.isLoaded()) {
            return cur
        }
        runCatching { cur?.unload() }
        activeLm = null
        activeLmSlug = null

        val fresh = CactusLM(enableToolFiltering = false)
        fresh.initializeModel(
            CactusInitParams(
                model = sdkSlug,
                contextSize = contextSize
            )
        )
        activeLm = fresh
        activeLmSlug = sdkSlug
        activeLmCtxSize = contextSize
        return fresh
    }

    private fun buildSdkPhoneTools(): List<CactusTool> = listOf(
        createTool(
            name = "set_flashlight",
            description = "Turn Android flashlight ON or OFF.",
            parameters = mapOf("enabled" to ToolParameter(type = "boolean", description = "true for ON, false for OFF", required = true))
        ),
        createTool(
            name = "set_volume",
            description = "Set media volume percentage from 0 to 100.",
            parameters = mapOf("percent" to ToolParameter(type = "integer", description = "Target volume 0-100", required = true))
        ),
        createTool(
            name = "get_device_telemetry",
            description = "Read battery level, charging state, free RAM, and current time.",
            parameters = emptyMap()
        ),
        createTool(
            name = "set_timer",
            description = "Start a countdown timer in minutes.",
            parameters = mapOf("minutes" to ToolParameter(type = "integer", description = "Minutes duration", required = true))
        ),
        createTool(
            name = "launch_app",
            description = "Open an installed Android app such as WhatsApp, YouTube, Camera, Settings.",
            parameters = mapOf("app_name" to ToolParameter(type = "string", description = "App name to launch", required = true))
        ),
        createTool(
            name = "invoke_mcp_telemetry",
            description = "Read embedded Android OS telemetry.",
            parameters = emptyMap()
        )
    )

    internal fun formatUserTurnPrompt(
        userPrompt: String,
        isQwen3: Boolean,
        allowThinking: Boolean
    ): String {
        val clean = userPrompt.trim()
        if (!isQwen3 || clean.isEmpty()) return clean
        val hasSwitch = clean.contains("/no_think", ignoreCase = true) || clean.contains("/think", ignoreCase = true)
        if (hasSwitch) return clean
        return if (allowThinking) clean else "$clean /no_think"
    }

    internal fun shouldEnableQwenThinking(prompt: String): Boolean {
        val clean = prompt.trim()
        if (clean.length < 18) return false
        val lower = clean.lowercase()
        return listOf(
            "/think", "step by step", "solve", "calculate", "equation", "integral",
            "derivative", "prove", "proof", "debug", "algorithm", "write code",
            "kotlin", "python", "java", "javascript", "compare", "analyze", "reasoning"
        ).any { lower.contains(it) }
    }

    internal fun selectRelevantSdkPhoneTools(userPrompt: String, enableTools: Boolean): List<CactusTool> {
        if (!enableTools) return emptyList()
        val lower = userPrompt.trim().lowercase()
        if (lower.isBlank()) return emptyList()

        val allTools = buildSdkPhoneTools().associateBy { it.function.name }
        val selected = mutableListOf<CactusTool>()

        if (listOf("flash", "torch", "light", "roshni", "batti").any { lower.contains(it) }) {
            allTools["set_flashlight"]?.let(selected::add)
        }
        if (listOf("volume", "sound", "awaz", "awaaz", "mute", "speaker").any { lower.contains(it) }) {
            allTools["set_volume"]?.let(selected::add)
        }
        if (listOf("battery", "charging", "ram", "telemetry", "device info", "device status", "phone status", "cpu", "time kya").any { lower.contains(it) }) {
            allTools["get_device_telemetry"]?.let(selected::add)
        }
        if (listOf("timer", "countdown", "minute", "min ", "mins").any { lower.contains(it) } &&
            listOf("set", "start", "laga", "timer", "countdown", "remind").any { lower.contains(it) }
        ) {
            allTools["set_timer"]?.let(selected::add)
        }
        if (APP_LAUNCH_INTENT_REGEX.containsMatchIn(lower)) {
            allTools["launch_app"]?.let(selected::add)
        }
        return selected.distinctBy { it.function.name }
    }

    internal fun stripThinkingMarkup(raw: String): String {
        val withoutClosedThink = raw.replace(THINK_BLOCK_REGEX, "")
        val openThinkIdx = withoutClosedThink.indexOf("<think>", ignoreCase = true)
        val trimmedThink = if (openThinkIdx >= 0) {
            withoutClosedThink.substring(0, openThinkIdx)
        } else {
            withoutClosedThink
        }
        return trimmedThink
            .replace(CONTROL_TOKEN_REGEX, "")
            .replace(TRAILING_PARTIAL_TAG_REGEX, "")
            .trim()
    }

    private fun parseTypedScalar(value: String): Any {
        val trimmed = value.trim()
        if (trimmed.equals("true", ignoreCase = true)) return true
        if (trimmed.equals("false", ignoreCase = true)) return false
        trimmed.toIntOrNull()?.let { return it }
        trimmed.toDoubleOrNull()?.let { return it }
        return value
    }

    sealed class SdkChatOutcome {
        data class Success(
            val text: String,
            val toolCalls: List<CactusToolCallRequest>,
            val promptTokens: Int,
            val outputTokens: Int,
            val latencyMs: Long,
            val tps: Double
        ) : SdkChatOutcome()

        data class Failure(val reason: String) : SdkChatOutcome()
    }
}
