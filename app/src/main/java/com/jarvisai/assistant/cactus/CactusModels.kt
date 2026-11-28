package com.jarvisai.assistant.cactus

/**
 * Execution backend for a cataloged Cactus model:
 * - [IN_PROCESS_SDK]: Runs natively inside Agentra via `com.cactuscompute:cactus:1.4.3-beta` (`libcactus.so`).
 * - [NATIVE_V4_CACT]: Runs on the Cactus v4 CLI/HTTP binary (`needle3.cact`, `whistle.cact`).
 * - [EXTERNAL_OPENAI]: Requires a user-hosted OpenAI-compatible server (`cactus-mlx server` / PC bridge).
 */
enum class CactusModelRuntime(val badgeLabel: String) {
    IN_PROCESS_SDK("IN-PROCESS JNI"),
    NATIVE_V4_CACT("NATIVE .cact"),
    EXTERNAL_OPENAI("EXTERNAL SERVER")
}

/**
 * Core data models, capability taxonomy, and verified catalog
 * for Agentra's Dual Cactus Compute Engine (`com.cactuscompute:cactus` SDK + v4 CLI runtime).
 */
enum class CactusModelCapability(val label: String, val badge: String) {
    LLM("Chat & Reasoning", "LLM"),
    TOOL_CALLING("Agentic Tools", "TOOLS"),
    VLM("Vision & OCR", "VLM"),
    STT("Speech-to-Text", "STT"),
    EMBEDDING("Vector RAG", "EMBED")
}

enum class CactusComputeBackend(val id: String, val label: String, val cliFlag: String) {
    AUTO("auto", "Auto (Best)", "auto"),
    CPU_NEON("cpu", "CPU NEON", "cpu"),
    GPU_VULKAN("vulkan", "GPU Vulkan", "vulkan"),
    NPU_HEXAGON("npu", "NPU / DSP", "npu")
}

data class CactusModelSpec(
    val id: String,
    val slug: String,
    val displayName: String,
    val huggingFaceRepo: String,
    val fallbackHuggingFaceRepos: List<String> = emptyList(),
    val capability: CactusModelCapability,
    val isNativeCactModel: Boolean = false,
    val runtime: CactusModelRuntime = if (isNativeCactModel) CactusModelRuntime.NATIVE_V4_CACT else CactusModelRuntime.IN_PROCESS_SDK,
    val sdkSlug: String = slug,
    val directZipUrl: String = "",
    val supportsToolCalling: Boolean = false,
    val supportsVision: Boolean = false,
    val parameterLabel: String,
    val quantizationLabel: String,
    val estimatedDownloadMb: Int,
    val recommendedRamGb: Int,
    val description: String,
    val defaultBits: Int = 4,
    val aliases: List<String> = emptyList()
) {
    val isInProcessSdkModel: Boolean
        get() = runtime == CactusModelRuntime.IN_PROCESS_SDK

    val isExternalServerOnly: Boolean
        get() = runtime == CactusModelRuntime.EXTERNAL_OPENAI
}

data class CactusModelInstallState(
    val modelId: String,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val currentFileName: String = "",
    val localDirPath: String = "",
    val sizeOnDiskBytes: Long = 0L,
    val lastError: String? = null
)

data class CactusRuntimeConfig(
    val serverBaseUrl: String = "http://127.0.0.1:8090",
    val activeLlmModelId: String = CactusModelCatalog.DEFAULT_LLM_ID,
    val activeVlmModelId: String = CactusModelCatalog.DEFAULT_VLM_ID,
    val activeSttModelId: String = CactusModelCatalog.DEFAULT_STT_ID,
    val activeEmbeddingModelId: String = CactusModelCatalog.DEFAULT_EMBEDDING_ID,
    val computeBackend: CactusComputeBackend = CactusComputeBackend.AUTO,
    val hybridCloudFallbackEnabled: Boolean = true,
    val offlineSttEnabled: Boolean = false,
    val memoryVectorRagEnabled: Boolean = true,
    val privateOfflineVisionDefault: Boolean = false,
    val privateOfflineDocsDefault: Boolean = false,
    val fastCactusScreenControlEnabled: Boolean = true,
    val huggingFaceToken: String = "",
    val maxContextTokens: Int = 2048,
    val temperature: Float = 0.6f
)

data class CactusEngineStatus(
    val binaryInstalled: Boolean = false,
    val binaryPath: String = "",
    val nativeJniAvailable: Boolean = false,
    val sdkSupported: Boolean = false,
    val sdkLoadedModel: String = "",
    val isInstallingBinary: Boolean = false,
    val binaryDownloadProgress: Float = 0f,
    val isServerRunning: Boolean = false,
    val serverEndpoint: String = "http://127.0.0.1:8090",
    val loadedServerModel: String = "",
    val deviceAbi: String = "",
    val availableRamMb: Long = 0L,
    val totalStorageUsedMb: Long = 0L,
    val indexedMemoryVectorsCount: Int = 0,
    val lastStatusMessage: String = "Cactus On-Device Engine ready for setup."
)

data class CactusToolCallRequest(
    val id: String,
    val name: String,
    val argumentsJson: String
)

enum class CactusServerApiStyle {
    NONE,
    OPENAI,
    CACTUS_NATIVE
}

data class CactusServerProbeResult(
    val isOnline: Boolean = false,
    val modelName: String = "",
    val apiStyle: CactusServerApiStyle = CactusServerApiStyle.NONE
)

data class CactusBinaryCliCaps(
    val probed: Boolean = false,
    val hasDoubleDashServe: Boolean = true,
    val hasDoubleDashModel: Boolean = true,
    val hasServeSubcommand: Boolean = false,
    val hasRunSubcommand: Boolean = false,
    val hasDoubleDashPrompt: Boolean = true,
    val hasDoubleDashTools: Boolean = true,
    val hasDoubleDashSystem: Boolean = true,
    val hasDoubleDashThreads: Boolean = true,
    val hasDoubleDashImage: Boolean = false,
    val hasDoubleDashAudio: Boolean = true,
    val rawHelpSnippet: String = ""
)

sealed class CactusStreamEvent {
    data class Status(val message: String) : CactusStreamEvent()
    data class Token(val accumulatedText: String, val delta: String) : CactusStreamEvent()
    data class ToolExecuted(
        val toolCallId: String,
        val toolName: String,
        val argumentsSummary: String,
        val resultSummary: String,
        val succeeded: Boolean
    ) : CactusStreamEvent()
    data class Completed(
        val fullText: String,
        val modelUsed: String,
        val latencyMs: Long,
        val promptTokens: Int = 0,
        val outputTokens: Int = 0
    ) : CactusStreamEvent()
    data class HandoffToCloud(
        val offlineFriendlyReply: String,
        val modelUsed: String = "cactus:needle3",
        val latencyMs: Long = 0L,
        val decodeTps: Double = 0.0,
        val confidence: Double = 0.0,
        val canFallbackToCloud: Boolean = true
    ) : CactusStreamEvent()
    data class Error(val message: String, val canFallbackToCloud: Boolean = false) : CactusStreamEvent()
}

object CactusModelCatalog {
    const val DEFAULT_LLM_ID = "cactus:qwen3-0.6b"
    const val DEFAULT_VLM_ID = "cactus:lfm2.5-vl-450m"
    const val DEFAULT_STT_ID = "cactus:whistle"
    const val DEFAULT_EMBEDDING_ID = "cactus:qwen3-embedding-0.6b"

    const val RUNTIME_HF_REPO = "Cactus-Compute/cactus"
    const val DEFAULT_RELEASE_VERSION = "4.1.4"
    private const val SUPABASE_MODELS_CDN = "https://vlqqczxwyaodtcdmdmlw.supabase.co/storage/v1/object/public/cactus-models"
    private const val SUPABASE_VOICE_CDN = "https://vlqqczxwyaodtcdmdmlw.supabase.co/storage/v1/object/public/voice-models"

    fun buildBinaryUrl(releaseVersion: String = DEFAULT_RELEASE_VERSION, targetFolder: String = "android-arm64"): String =
        "https://huggingface.co/$RUNTIME_HF_REPO/resolve/main/releases/$releaseVersion/$targetFolder/cactus"

    val allModels: List<CactusModelSpec> = listOf(
        // --- 1. OFFICIAL IN-PROCESS ANDROID SDK MODELS (RUN DIRECTLY IN-APP VIA libcactus.so — ZERO SERVER!) ---
        CactusModelSpec(
            id = "cactus:qwen3-0.6b",
            slug = "qwen3-0.6",
            displayName = "Qwen3 0.6B (In-App Chat + Tools)",
            huggingFaceRepo = "Cactus-Compute/Qwen3-0.6B",
            fallbackHuggingFaceRepos = listOf("Cactus-Compute/LFM2-350M"),
            capability = CactusModelCapability.LLM,
            isNativeCactModel = false,
            runtime = CactusModelRuntime.IN_PROCESS_SDK,
            sdkSlug = "qwen3-0.6",
            directZipUrl = "$SUPABASE_MODELS_CDN/qwen3-0.6.zip",
            supportsToolCalling = true,
            parameterLabel = "0.6B",
            quantizationLabel = "JNI INT8/4",
            estimatedDownloadMb = 394,
            recommendedRamGb = 4,
            description = "Full offline chat, reasoning, doc Q&A & local phone tool calling — runs directly in-process via Cactus Android SDK (zero server needed).",
            aliases = listOf("qwen3-0.6b", "cactus:qwen3-0.6")
        ),
        CactusModelSpec(
            id = "cactus:gemma3-270m",
            slug = "gemma3-270m",
            displayName = "Gemma 3 270M (Ultra-Light Chat)",
            huggingFaceRepo = "Cactus-Compute/gemma-3-270m-it",
            capability = CactusModelCapability.LLM,
            isNativeCactModel = false,
            runtime = CactusModelRuntime.IN_PROCESS_SDK,
            sdkSlug = "gemma3-270m",
            directZipUrl = "$SUPABASE_MODELS_CDN/gemma3-270m.zip",
            supportsToolCalling = false,
            parameterLabel = "270M",
            quantizationLabel = "JNI INT8",
            estimatedDownloadMb = 172,
            recommendedRamGb = 3,
            description = "Ultra-compact offline conversational & summarization model running directly in-process inside Agentra."
        ),
        CactusModelSpec(
            id = "cactus:qwen3-1.7b",
            slug = "qwen3-1.7",
            displayName = "Qwen3 1.7B (Pro Offline LLM)",
            huggingFaceRepo = "Cactus-Compute/Qwen3-1.7B",
            capability = CactusModelCapability.LLM,
            isNativeCactModel = false,
            runtime = CactusModelRuntime.IN_PROCESS_SDK,
            sdkSlug = "qwen3-1.7",
            directZipUrl = "$SUPABASE_MODELS_CDN/qwen3-1.7.zip",
            supportsToolCalling = true,
            parameterLabel = "1.7B",
            quantizationLabel = "JNI INT8",
            estimatedDownloadMb = 1161,
            recommendedRamGb = 6,
            description = "Strong coding, Hinglish chat & tool-calling model running directly inside Agentra via Cactus JNI SDK.",
            aliases = listOf("qwen3-1.7b", "cactus:qwen3-1.7")
        ),
        CactusModelSpec(
            id = "cactus:gemma3-1b",
            slug = "gemma3-1b",
            displayName = "Gemma 3 1B (Best Hindi/Hinglish)",
            huggingFaceRepo = "Cactus-Compute/gemma3-1b",
            capability = CactusModelCapability.LLM,
            isNativeCactModel = false,
            runtime = CactusModelRuntime.IN_PROCESS_SDK,
            sdkSlug = "gemma3-1b",
            directZipUrl = "$SUPABASE_MODELS_CDN/gemma3-1b.zip",
            supportsToolCalling = false,
            parameterLabel = "1B",
            quantizationLabel = "JNI FP16",
            estimatedDownloadMb = 612,
            recommendedRamGb = 4,
            description = "Fast offline Google Gemma 3 1B — strong Hindi/Hinglish understanding with zero <think> delay."
        ),
        CactusModelSpec(
            id = "cactus:lfm2-1.2b-tool",
            slug = "lfm2-1.2b-tool",
            displayName = "LFM 2 1.2B Tool (Fast Agent)",
            huggingFaceRepo = "Cactus-Compute/LFM2-1.2B-Tool",
            capability = CactusModelCapability.LLM,
            isNativeCactModel = false,
            runtime = CactusModelRuntime.IN_PROCESS_SDK,
            sdkSlug = "lfm2-1.2b-tool",
            directZipUrl = "$SUPABASE_MODELS_CDN/lfm2-1.2b-tool.zip",
            supportsToolCalling = true,
            parameterLabel = "1.2B",
            quantizationLabel = "JNI INT8",
            estimatedDownloadMb = 729,
            recommendedRamGb = 4,
            description = "Liquid AI 1.2B fast chat & structured tool calling model — great for fast screen JSON & local actions."
        ),
        CactusModelSpec(
            id = "cactus:functiongemma-270m",
            slug = "functiongemma-270m",
            displayName = "FunctionGemma 3 270M (In-App Tools)",
            huggingFaceRepo = "Cactus-Compute/functiongemma-270m-it",
            fallbackHuggingFaceRepos = listOf("Cactus-Compute/gemma-3-270m-it"),
            capability = CactusModelCapability.TOOL_CALLING,
            isNativeCactModel = false,
            runtime = CactusModelRuntime.IN_PROCESS_SDK,
            sdkSlug = "functiongemma-270m",
            directZipUrl = "$SUPABASE_MODELS_CDN/functiongemma-270m-it.zip",
            supportsToolCalling = true,
            parameterLabel = "270M",
            quantizationLabel = "JNI INT8",
            estimatedDownloadMb = 182,
            recommendedRamGb = 3,
            description = "Compact on-device function-calling model running directly inside Agentra via Cactus JNI SDK."
        ),
        CactusModelSpec(
            id = "cactus:lfm2.5-vl-450m",
            slug = "lfm2-vl-450m",
            displayName = "LFM 2 VL 450M (Offline Vision)",
            huggingFaceRepo = "Cactus-Compute/LFM2.5-VL-450M",
            fallbackHuggingFaceRepos = listOf("Cactus-Compute/LFM2-VL-450M"),
            capability = CactusModelCapability.VLM,
            isNativeCactModel = false,
            runtime = CactusModelRuntime.IN_PROCESS_SDK,
            sdkSlug = "lfm2-vl-450m",
            directZipUrl = "$SUPABASE_MODELS_CDN/lfm2-vl-450m.zip",
            supportsVision = true,
            parameterLabel = "450M",
            quantizationLabel = "JNI VLM",
            estimatedDownloadMb = 420,
            recommendedRamGb = 4,
            description = "Real offline Vision & Optical Solver model running natively in-process on Android arm64 (supports photos & screenshots!).",
            aliases = listOf("lfm2.5-vl-450m", "cactus:lfm2-vl-450m")
        ),
        CactusModelSpec(
            id = "cactus:whisper-tiny",
            slug = "whisper-tiny",
            displayName = "Whisper Tiny (In-App STT)",
            huggingFaceRepo = "Cactus-Compute/whisper-tiny",
            capability = CactusModelCapability.STT,
            isNativeCactModel = false,
            runtime = CactusModelRuntime.IN_PROCESS_SDK,
            sdkSlug = "whisper-tiny",
            directZipUrl = "$SUPABASE_VOICE_CDN/whisper-tiny.zip",
            parameterLabel = "39M",
            quantizationLabel = "JNI STT",
            estimatedDownloadMb = 30,
            recommendedRamGb = 2,
            description = "Ultra-light 30 MB multilingual offline Whisper STT running directly inside Agentra via CactusSTT."
        ),
        CactusModelSpec(
            id = "cactus:whisper-small",
            slug = "whisper-small",
            displayName = "Whisper Small (Multilingual STT)",
            huggingFaceRepo = "Cactus-Compute/whisper-small",
            fallbackHuggingFaceRepos = listOf("Cactus-Compute/whisper-base", "Cactus-Compute/whisper-tiny"),
            capability = CactusModelCapability.STT,
            isNativeCactModel = false,
            runtime = CactusModelRuntime.IN_PROCESS_SDK,
            sdkSlug = "whisper-small",
            directZipUrl = "$SUPABASE_VOICE_CDN/whisper-small.zip",
            parameterLabel = "244M",
            quantizationLabel = "JNI STT",
            estimatedDownloadMb = 192,
            recommendedRamGb = 4,
            description = "High-accuracy multilingual offline speech recognition (Hindi/Hinglish/English) via CactusSTT JNI."
        ),
        CactusModelSpec(
            id = "cactus:qwen3-embedding-0.6b",
            slug = "qwen3-0.6-embed",
            displayName = "Qwen3 Embedding 0.6B (Vector RAG)",
            huggingFaceRepo = "Cactus-Compute/Qwen3-Embedding-0.6B",
            capability = CactusModelCapability.EMBEDDING,
            isNativeCactModel = false,
            runtime = CactusModelRuntime.IN_PROCESS_SDK,
            sdkSlug = "qwen3-0.6-embed",
            directZipUrl = "$SUPABASE_MODELS_CDN/qwen3-0.6-embed.zip",
            parameterLabel = "0.6B",
            quantizationLabel = "JNI EMBED",
            estimatedDownloadMb = 394,
            recommendedRamGb = 4,
            description = "Local semantic vector embeddings running in-process via Cactus JNI (`cactus_embed`) for Memory RAG & Docs.",
            aliases = listOf("qwen3-embedding-0.6b", "cactus:qwen3-0.6-embed")
        ),

        // --- 2. CACTUS v4 NATIVE `.cact` ULTRA-LIGHTWEIGHT BINARIES ---
        CactusModelSpec(
            id = "cactus:needle3",
            slug = "needle3",
            displayName = "Cactus Needle3 (.cact Tool Router)",
            huggingFaceRepo = "Cactus-Compute/needle3",
            fallbackHuggingFaceRepos = listOf("Cactus-Compute/needle2", "Cactus-Compute/needle"),
            capability = CactusModelCapability.TOOL_CALLING,
            isNativeCactModel = true,
            runtime = CactusModelRuntime.NATIVE_V4_CACT,
            sdkSlug = "needle3",
            supportsToolCalling = true,
            parameterLabel = "34 MB",
            quantizationLabel = "NATIVE .cact",
            estimatedDownloadMb = 34,
            recommendedRamGb = 2,
            description = "Cactus v4 `.cact` ultra-fast local phone-command router (flashlight, volume, timer, apps). Not a general chat LLM."
        ),
        CactusModelSpec(
            id = "cactus:whistle",
            slug = "whistle",
            displayName = "Cactus Whistle (.cact 17MB STT)",
            huggingFaceRepo = "Cactus-Compute/whistle",
            capability = CactusModelCapability.STT,
            isNativeCactModel = true,
            runtime = CactusModelRuntime.NATIVE_V4_CACT,
            sdkSlug = "whistle",
            parameterLabel = "17 MB",
            quantizationLabel = "NATIVE .cact",
            estimatedDownloadMb = 17,
            recommendedRamGb = 2,
            description = "Official Cactus v4 `.cact` 17 MB offline speech-to-text (en/de/fr/es/it/nl/pl) via native binary."
        ),

        // --- 3. EXTERNAL SERVER / SPECIALIZED WEIGHTS ---
        CactusModelSpec(
            id = "cactus:gemma-4-e2b",
            slug = "gemma-4-e2b",
            displayName = "Gemma 4 E2B (External Server)",
            huggingFaceRepo = "Cactus-Compute/gemma-4-E2B-it",
            fallbackHuggingFaceRepos = listOf("Cactus-Compute/gemma-4-e2b-it-hybrid-GGUF"),
            capability = CactusModelCapability.LLM,
            isNativeCactModel = false,
            runtime = CactusModelRuntime.EXTERNAL_OPENAI,
            sdkSlug = "gemma-4-e2b",
            supportsToolCalling = true,
            supportsVision = true,
            parameterLabel = "2B",
            quantizationLabel = "EXTERNAL",
            estimatedDownloadMb = 1520,
            recommendedRamGb = 8,
            description = "Requires an external OpenAI-compatible Cactus/llama/MLX server. For in-app offline chat use Qwen3 0.6B or Gemma 3."
        ),
        CactusModelSpec(
            id = "cactus:mirage",
            slug = "mirage",
            displayName = "Cactus Mirage (.cact • External/C-ABI)",
            huggingFaceRepo = "Cactus-Compute/mirage",
            capability = CactusModelCapability.VLM,
            isNativeCactModel = true,
            runtime = CactusModelRuntime.EXTERNAL_OPENAI,
            sdkSlug = "mirage",
            supportsVision = true,
            parameterLabel = "21 MB",
            quantizationLabel = "C-ABI .cact",
            estimatedDownloadMb = 21,
            recommendedRamGb = 2,
            description = "Cactus v4 `.cact` vision weight requires C ABI (`cactus_describe`) or external server — CLI binary cannot run vision. Use LFM 2 VL 450M in-app!"
        )
    )

    fun findById(modelId: String): CactusModelSpec? {
        val clean = modelId.trim()
        return allModels.firstOrNull { spec ->
            spec.id.equals(clean, ignoreCase = true) ||
                spec.slug.equals(clean.removePrefix("cactus:"), ignoreCase = true) ||
                spec.sdkSlug.equals(clean.removePrefix("cactus:"), ignoreCase = true) ||
                spec.huggingFaceRepo.equals(clean, ignoreCase = true) ||
                spec.aliases.any { alias ->
                    alias.equals(clean, ignoreCase = true) ||
                        alias.equals(clean.removePrefix("cactus:"), ignoreCase = true)
                }
        }
    }

    fun inProcessSdkModels(): List<CactusModelSpec> =
        allModels.filter { it.runtime == CactusModelRuntime.IN_PROCESS_SDK }

    fun nativeCactModels(): List<CactusModelSpec> =
        allModels.filter { it.runtime == CactusModelRuntime.NATIVE_V4_CACT }

    fun chatCapableModels(): List<CactusModelSpec> =
        allModels.filter {
            it.capability == CactusModelCapability.LLM || it.capability == CactusModelCapability.TOOL_CALLING
        }

    fun visionCapableModels(): List<CactusModelSpec> =
        allModels.filter { it.capability == CactusModelCapability.VLM || it.supportsVision }

    fun sttModels(): List<CactusModelSpec> =
        allModels.filter { it.capability == CactusModelCapability.STT }

    fun embeddingModels(): List<CactusModelSpec> =
        allModels.filter { it.capability == CactusModelCapability.EMBEDDING || it.id == "cactus:qwen3-0.6b" }
}
