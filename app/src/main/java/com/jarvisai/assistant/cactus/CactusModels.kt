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
    val capability: CactusModelCapability,
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
    data class Error(val message: String, val canFallbackToCloud: Boolean = false) : CactusStreamEvent()
}

object CactusModelCatalog {
    const val DEFAULT_LLM_ID = "cactus:qwen3-0.6b"
    const val DEFAULT_VLM_ID = "cactus:mirage"
    const val DEFAULT_STT_ID = "cactus:whistle"
    const val DEFAULT_EMBEDDING_ID = "cactus:qwen3-embedding-0.6b"

    const val ANDROID_ARM64_BINARY_URL =
        "https://huggingface.co/Cactus-Compute/cactus/resolve/main/releases/4.0.0/android-arm64/cactus"

    val allModels: List<CactusModelSpec> = listOf(
        // --- LLM & TOOL CALLING ---
        CactusModelSpec(
            id = "cactus:qwen3-0.6b",
            slug = "qwen3-0.6b",
            displayName = "Qwen3 0.6B (Fast Edge)",
            huggingFaceRepo = "Cactus-Compute/Qwen3-0.6B-int4",
            capability = CactusModelCapability.LLM,
            supportsToolCalling = true,
            parameterLabel = "0.6B",
            quantizationLabel = "INT4 CQ",
            estimatedDownloadMb = 390,
            recommendedRamGb = 4,
            description = "Ultra-fast everyday Hinglish/English offline chat & phone tool calls on 4GB+ RAM devices."
        ),
        CactusModelSpec(
            id = "cactus:qwen3-1.7b",
            slug = "qwen3-1.7b",
            displayName = "Qwen3 1.7B (Smart Reasoning)",
            huggingFaceRepo = "Cactus-Compute/Qwen3-1.7B-int4",
            capability = CactusModelCapability.LLM,
            supportsToolCalling = true,
            parameterLabel = "1.7B",
            quantizationLabel = "INT4 CQ",
            estimatedDownloadMb = 1050,
            recommendedRamGb = 6,
            description = "Strong offline coding, reasoning, document Q&A & agentic tool calling."
        ),
        CactusModelSpec(
            id = "cactus:gemma-4-e2b",
            slug = "gemma-4-e2b",
            displayName = "Gemma 4 E2B (Multimodal Edge)",
            huggingFaceRepo = "Cactus-Compute/gemma-4-E2B-it",
            capability = CactusModelCapability.LLM,
            supportsToolCalling = true,
            supportsVision = true,
            parameterLabel = "2B",
            quantizationLabel = "INT4 Hybrid",
            estimatedDownloadMb = 1520,
            recommendedRamGb = 8,
            description = "Google Gemma 4 Edge model optimized by Cactus for offline chat, vision & reasoning."
        ),
        CactusModelSpec(
            id = "cactus:functiongemma-270m",
            slug = "functiongemma-270m",
            displayName = "FunctionGemma 270M (Tool Router)",
            huggingFaceRepo = "Cactus-Compute/functiongemma-270m-it",
            capability = CactusModelCapability.TOOL_CALLING,
            supportsToolCalling = true,
            parameterLabel = "270M",
            quantizationLabel = "INT4 CQ",
            estimatedDownloadMb = 185,
            recommendedRamGb = 3,
            description = "Instant sub-150ms on-device phone control & MCP function calling router."
        ),
        CactusModelSpec(
            id = "cactus:needle3",
            slug = "needle3",
            displayName = "Cactus Needle3 (Micro Agent)",
            huggingFaceRepo = "Cactus-Compute/needle3",
            capability = CactusModelCapability.TOOL_CALLING,
            supportsToolCalling = true,
            parameterLabel = "121M",
            quantizationLabel = "INT4 CQ",
            estimatedDownloadMb = 95,
            recommendedRamGb = 2,
            description = "Cactus native micro-agent engineered specifically for deterministic offline tool calls."
        ),

        // --- VISION (VLM) ---
        CactusModelSpec(
            id = "cactus:mirage",
            slug = "mirage",
            displayName = "Cactus Mirage VLM",
            huggingFaceRepo = "Cactus-Compute/mirage",
            capability = CactusModelCapability.VLM,
            supportsVision = true,
            parameterLabel = "1.5B",
            quantizationLabel = "INT4 CQ",
            estimatedDownloadMb = 860,
            recommendedRamGb = 6,
            description = "100% private offline image analysis, bill/receipt OCR, math & code screenshot reader."
        ),

        // --- SPEECH-TO-TEXT (STT) ---
        CactusModelSpec(
            id = "cactus:whistle",
            slug = "whistle",
            displayName = "Cactus Whistle (Fast STT)",
            huggingFaceRepo = "Cactus-Compute/whistle",
            capability = CactusModelCapability.STT,
            parameterLabel = "120M",
            quantizationLabel = "INT8",
            estimatedDownloadMb = 125,
            recommendedRamGb = 3,
            description = "Ultra-low-latency offline speech transcription for mic dictation & wake-word follow-ups."
        ),
        CactusModelSpec(
            id = "cactus:whisper-small",
            slug = "whisper-small",
            displayName = "Whisper Small (Multilingual STT)",
            huggingFaceRepo = "Cactus-Compute/whisper-small",
            capability = CactusModelCapability.STT,
            parameterLabel = "244M",
            quantizationLabel = "INT8",
            estimatedDownloadMb = 245,
            recommendedRamGb = 4,
            description = "High-accuracy offline Hindi, Hinglish & English voice dictation without Google Speech API."
        ),

        // --- EMBEDDINGS (LOCAL RAG) ---
        CactusModelSpec(
            id = "cactus:qwen3-embedding-0.6b",
            slug = "qwen3-embedding-0.6b",
            displayName = "Qwen3 Embedding 0.6B (Memory RAG)",
            huggingFaceRepo = "Cactus-Compute/Qwen3-Embedding-0.6B",
            capability = CactusModelCapability.EMBEDDING,
            parameterLabel = "0.6B",
            quantizationLabel = "INT4",
            estimatedDownloadMb = 380,
            recommendedRamGb = 4,
            description = "Powers semantic vector search in Agentra's 5-Tier Memory Center & Private Document RAG."
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

    fun chatCapableModels(): List<CactusModelSpec> =
        allModels.filter {
            it.capability == CactusModelCapability.LLM || it.capability == CactusModelCapability.TOOL_CALLING
        }

    fun visionCapableModels(): List<CactusModelSpec> =
        allModels.filter { it.capability == CactusModelCapability.VLM || it.supportsVision }

    fun sttModels(): List<CactusModelSpec> =
        allModels.filter { it.capability == CactusModelCapability.STT }

    fun embeddingModels(): List<CactusModelSpec> =
        allModels.filter { it.capability == CactusModelCapability.EMBEDDING }
}
