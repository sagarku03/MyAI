package com.jarvisai.assistant.cactus

/**
 * Core data models, capability taxonomy, and curated Hugging Face catalog
 * for the Cactus Compute (cactus-compute/cactus) on-device AI engine.
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
    val supportsToolCalling: Boolean = false,
    val supportsVision: Boolean = false,
    val parameterLabel: String,
    val quantizationLabel: String,
    val estimatedDownloadMb: Int,
    val recommendedRamGb: Int,
    val description: String,
    val defaultBits: Int = 4
)

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
    val maxContextTokens: Int = 4096,
    val temperature: Float = 0.6f
)

data class CactusEngineStatus(
    val binaryInstalled: Boolean = false,
    val binaryPath: String = "",
    val nativeJniAvailable: Boolean = false,
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
    const val DEFAULT_LLM_ID = "cactus:needle3"
    const val DEFAULT_VLM_ID = "cactus:mirage"
    const val DEFAULT_STT_ID = "cactus:whistle"
    const val DEFAULT_EMBEDDING_ID = "cactus:needle3"

    const val RUNTIME_HF_REPO = "Cactus-Compute/cactus"
    const val DEFAULT_RELEASE_VERSION = "4.0.0"

    fun buildBinaryUrl(releaseVersion: String = DEFAULT_RELEASE_VERSION, targetFolder: String = "android-arm64"): String =
        "https://huggingface.co/$RUNTIME_HF_REPO/resolve/main/releases/$releaseVersion/$targetFolder/cactus"

    val allModels: List<CactusModelSpec> = listOf(
        // --- OFFICIAL CACTUS v4 NATIVE `.cact` MODELS (RUN DIRECTLY ON ANDROID BINARY!) ---
        CactusModelSpec(
            id = "cactus:needle3",
            slug = "needle3",
            displayName = "Cactus Needle3 (.cact Native)",
            huggingFaceRepo = "Cactus-Compute/needle3",
            fallbackHuggingFaceRepos = listOf(
                "Cactus-Compute/needle2",
                "Cactus-Compute/needle"
            ),
            capability = CactusModelCapability.TOOL_CALLING,
            isNativeCactModel = true,
            supportsToolCalling = true,
            parameterLabel = "29 MB",
            quantizationLabel = "NATIVE .cact",
            estimatedDownloadMb = 29,
            recommendedRamGb = 2,
            description = "Official Cactus v4 native `.cact` engine model! Runs directly on Android ARM64 for instant offline tool calling, structured extraction & screen actions."
        ),
        CactusModelSpec(
            id = "cactus:whistle",
            slug = "whistle",
            displayName = "Cactus Whistle (.cact Native STT)",
            huggingFaceRepo = "Cactus-Compute/whistle",
            fallbackHuggingFaceRepos = emptyList(),
            capability = CactusModelCapability.STT,
            isNativeCactModel = true,
            parameterLabel = "17 MB",
            quantizationLabel = "NATIVE .cact",
            estimatedDownloadMb = 17,
            recommendedRamGb = 2,
            description = "Official Cactus v4 native `.cact` offline speech-to-text (7 languages, word timestamps, live streaming). Can load alongside Needle3!"
        ),
        CactusModelSpec(
            id = "cactus:mirage",
            slug = "mirage",
            displayName = "Cactus Mirage (.cact Native VLM)",
            huggingFaceRepo = "Cactus-Compute/mirage",
            fallbackHuggingFaceRepos = emptyList(),
            capability = CactusModelCapability.VLM,
            isNativeCactModel = true,
            supportsVision = true,
            parameterLabel = "22 MB",
            quantizationLabel = "NATIVE .cact",
            estimatedDownloadMb = 22,
            recommendedRamGb = 2,
            description = "Official Cactus v4 `.cact` 22 MB ultra-fast on-device vision, OCR & structured visual extraction model."
        ),

        // --- EXTERNAL SERVER / MLX / GENERAL CHAT WEIGHTS (FOR OPENAI BRIDGE OR `cactus-mlx`) ---
        CactusModelSpec(
            id = "cactus:qwen3-0.6b",
            slug = "qwen3-0.6b",
            displayName = "Qwen3 0.6B (External Server)",
            huggingFaceRepo = "Cactus-Compute/Qwen3-0.6B",
            fallbackHuggingFaceRepos = listOf(
                "Cactus-Compute/Qwen3.5-0.8B",
                "Cactus-Compute/LFM2-350M"
            ),
            capability = CactusModelCapability.LLM,
            isNativeCactModel = false,
            supportsToolCalling = true,
            parameterLabel = "0.6B",
            quantizationLabel = "MLX / Server",
            estimatedDownloadMb = 390,
            recommendedRamGb = 4,
            description = "General chat weights for external OpenAI-compatible Cactus/llama server (not a v4 `.cact` binary file)."
        ),
        CactusModelSpec(
            id = "cactus:qwen3-1.7b",
            slug = "qwen3-1.7b",
            displayName = "Qwen3 1.7B (External Server)",
            huggingFaceRepo = "Cactus-Compute/Qwen3-1.7B",
            fallbackHuggingFaceRepos = listOf(
                "Cactus-Compute/LFM2.5-1.2B-Thinking",
                "Cactus-Compute/Qwen3.5-0.8B"
            ),
            capability = CactusModelCapability.LLM,
            isNativeCactModel = false,
            supportsToolCalling = true,
            parameterLabel = "1.7B",
            quantizationLabel = "MLX / Server",
            estimatedDownloadMb = 1050,
            recommendedRamGb = 6,
            description = "Strong coding & reasoning weights for external OpenAI-compatible server (not a v4 `.cact` binary file)."
        ),
        CactusModelSpec(
            id = "cactus:gemma-4-e2b",
            slug = "gemma-4-e2b",
            displayName = "Gemma 4 E2B (External Server)",
            huggingFaceRepo = "Cactus-Compute/gemma-4-E2B-it",
            fallbackHuggingFaceRepos = listOf(
                "Cactus-Compute/gemma-4-e2b-it-hybrid",
                "Cactus-Compute/gemma-4-e2b-it-hybrid-GGUF"
            ),
            capability = CactusModelCapability.LLM,
            isNativeCactModel = false,
            supportsToolCalling = true,
            supportsVision = true,
            parameterLabel = "2B",
            quantizationLabel = "GGUF / Server",
            estimatedDownloadMb = 1520,
            recommendedRamGb = 8,
            description = "Google Gemma 4 Edge weights for external OpenAI-compatible server (not a v4 `.cact` binary file)."
        ),
        CactusModelSpec(
            id = "cactus:functiongemma-270m",
            slug = "functiongemma-270m",
            displayName = "FunctionGemma 270M (External)",
            huggingFaceRepo = "Cactus-Compute/functiongemma-270m-it",
            fallbackHuggingFaceRepos = listOf(
                "Cactus-Compute/gemma-3-270m-it",
                "Cactus-Compute/needle3"
            ),
            capability = CactusModelCapability.TOOL_CALLING,
            isNativeCactModel = false,
            supportsToolCalling = true,
            parameterLabel = "270M",
            quantizationLabel = "MLX / Server",
            estimatedDownloadMb = 185,
            recommendedRamGb = 3,
            description = "Function-calling weights for external OpenAI-compatible server (use Needle3 `.cact` for native Android binary)."
        ),
        CactusModelSpec(
            id = "cactus:lfm2.5-vl-450m",
            slug = "lfm2.5-vl-450m",
            displayName = "LFM2.5-VL 450M (External VLM)",
            huggingFaceRepo = "Cactus-Compute/LFM2.5-VL-450M",
            fallbackHuggingFaceRepos = listOf(
                "Cactus-Compute/LFM2-VL-450M",
                "Cactus-Compute/mirage"
            ),
            capability = CactusModelCapability.VLM,
            isNativeCactModel = false,
            supportsVision = true,
            supportsToolCalling = true,
            parameterLabel = "450M",
            quantizationLabel = "MLX / Server",
            estimatedDownloadMb = 380,
            recommendedRamGb = 4,
            description = "GUI vision weights for external OpenAI-compatible VLM server."
        ),
        CactusModelSpec(
            id = "cactus:whisper-small",
            slug = "whisper-small",
            displayName = "Whisper Small (External STT)",
            huggingFaceRepo = "Cactus-Compute/whisper-small",
            fallbackHuggingFaceRepos = listOf(
                "Cactus-Compute/whisper-base",
                "Cactus-Compute/whisper-tiny"
            ),
            capability = CactusModelCapability.STT,
            isNativeCactModel = false,
            parameterLabel = "244M",
            quantizationLabel = "MLX / Server",
            estimatedDownloadMb = 245,
            recommendedRamGb = 4,
            description = "Multilingual Whisper weights for external OpenAI STT server (use Whistle `.cact` for native Android binary)."
        ),
        CactusModelSpec(
            id = "cactus:qwen3-embedding-0.6b",
            slug = "qwen3-embedding-0.6b",
            displayName = "Qwen3 Embedding 0.6B (External)",
            huggingFaceRepo = "Cactus-Compute/Qwen3-Embedding-0.6B",
            fallbackHuggingFaceRepos = listOf(
                "Cactus-Compute/needle3"
            ),
            capability = CactusModelCapability.EMBEDDING,
            isNativeCactModel = false,
            parameterLabel = "0.6B",
            quantizationLabel = "MLX / Server",
            estimatedDownloadMb = 380,
            recommendedRamGb = 4,
            description = "Semantic vector embeddings for external OpenAI `/v1/embeddings` server."
        )
    )

    fun findById(modelId: String): CactusModelSpec? {
        val clean = modelId.trim()
        return allModels.firstOrNull {
            it.id.equals(clean, ignoreCase = true) ||
                it.slug.equals(clean.removePrefix("cactus:"), ignoreCase = true) ||
                it.huggingFaceRepo.equals(clean, ignoreCase = true)
        }
    }

    fun nativeCactModels(): List<CactusModelSpec> =
        allModels.filter { it.isNativeCactModel }

    fun chatCapableModels(): List<CactusModelSpec> =
        allModels.filter {
            it.capability == CactusModelCapability.LLM || it.capability == CactusModelCapability.TOOL_CALLING
        }

    fun visionCapableModels(): List<CactusModelSpec> =
        allModels.filter { it.capability == CactusModelCapability.VLM || it.supportsVision }

    fun sttModels(): List<CactusModelSpec> =
        allModels.filter { it.capability == CactusModelCapability.STT }

    fun embeddingModels(): List<CactusModelSpec> =
        allModels.filter { it.capability == CactusModelCapability.EMBEDDING || it.id == "cactus:needle3" }
}
