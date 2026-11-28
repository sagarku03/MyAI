package com.jarvisai.assistant.cactus

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Dual-engine Cactus Compute supervisor:
 * 1. Manages official `com.cactuscompute:cactus:1.4.3-beta` in-process SDK model lifecycle (`filesDir/models/<sdkSlug>`).
 * 2. Manages Cactus v4 ARM64 CLI/HTTP runner (`needle3.cact`, `whistle.cact`) and external OpenAI endpoints.
 */
object CactusNativeRuntime {
    private const val TAG = "CactusNativeRuntime"
    private const val PREFS_NAME = "agentra_cactus_runtime_prefs"
    private const val MAX_LOGS = 200

    private val WEIGHT_EXTENSIONS = setOf("cact", "gguf", "safetensors", "bin", "cactus", "onnx", "weights")
    private val ARCHIVE_EXTENSIONS = setOf("zip")
    private val METADATA_EXTENSIONS = setOf("json", "txt", "model", "tiktoken", "vocab", "merges")

    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serverMutex = Mutex()
    private val downloadJobs = ConcurrentHashMap<String, Job>()

    private var serverProcess: Process? = null
    private var logReaderJob: Job? = null
    @Volatile
    private var cachedCliCaps: Pair<String, CactusBinaryCliCaps>? = null
    @Volatile
    private var lastAutoLaunchFailureMs: Long = 0L
    @Volatile
    private var lastAutoLaunchFailureModelId: String = ""

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(800, TimeUnit.MILLISECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    private val _config = MutableStateFlow(CactusRuntimeConfig())
    val config: StateFlow<CactusRuntimeConfig> = _config.asStateFlow()

    private val _status = MutableStateFlow(CactusEngineStatus())
    val status: StateFlow<CactusEngineStatus> = _status.asStateFlow()

    private val _modelStates = MutableStateFlow<Map<String, CactusModelInstallState>>(emptyMap())
    val modelStates: StateFlow<Map<String, CactusModelInstallState>> = _modelStates.asStateFlow()

    private val _engineLogs = MutableStateFlow<List<String>>(emptyList())
    val engineLogs: StateFlow<List<String>> = _engineLogs.asStateFlow()

    fun getCactusRootDir(context: Context): File =
        File(context.applicationContext.filesDir, "cactus").apply { mkdirs() }

    fun getBinDir(context: Context): File =
        File(getCactusRootDir(context), "bin").apply { mkdirs() }

    fun getModelsDir(context: Context): File =
        File(getCactusRootDir(context), "models").apply { mkdirs() }

    fun getModelDir(context: Context, spec: CactusModelSpec): File =
        if (spec.isInProcessSdkModel) {
            CactusInProcessEngine.getSdkModelDir(context.applicationContext, spec.sdkSlug)
        } else {
            File(getModelsDir(context), spec.slug)
        }

    fun getLocalBinaryFile(context: Context): File =
        File(getBinDir(context), "cactus")

    fun appendLog(line: String) {
        val clean = line.trim()
        if (clean.isEmpty()) return
        _engineLogs.value = (_engineLogs.value + clean).takeLast(MAX_LOGS)
    }

    fun clearLogs() {
        _engineLogs.value = emptyList()
    }

    fun updateIndexedVectorsCount(count: Int) {
        _status.value = _status.value.copy(indexedMemoryVectorsCount = count)
    }

    fun initialize(context: Context) {
        val appCtx = context.applicationContext
        CactusInProcessEngine.ensureInitialized(appCtx)
        CactusVectorMemoryIndex.initialize(appCtx)
        loadConfig(appCtx)
        runtimeScope.launch {
            refreshStatus(appCtx)
        }
    }

    fun loadConfig(context: Context): CactusRuntimeConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val backendId = prefs.getString("compute_backend", CactusComputeBackend.AUTO.id) ?: CactusComputeBackend.AUTO.id
        val backend = CactusComputeBackend.entries.firstOrNull { it.id == backendId } ?: CactusComputeBackend.AUTO
        val loaded = CactusRuntimeConfig(
            serverBaseUrl = prefs.getString("server_base_url", "http://127.0.0.1:8090")
                ?.trim()?.removeSuffix("/")?.ifBlank { "http://127.0.0.1:8090" } ?: "http://127.0.0.1:8090",
            activeLlmModelId = normalizeSavedModelId(prefs.getString("active_llm_model", CactusModelCatalog.DEFAULT_LLM_ID), CactusModelCatalog.DEFAULT_LLM_ID),
            activeVlmModelId = normalizeSavedModelId(prefs.getString("active_vlm_model", CactusModelCatalog.DEFAULT_VLM_ID), CactusModelCatalog.DEFAULT_VLM_ID),
            activeSttModelId = normalizeSavedModelId(prefs.getString("active_stt_model", CactusModelCatalog.DEFAULT_STT_ID), CactusModelCatalog.DEFAULT_STT_ID),
            activeEmbeddingModelId = normalizeSavedModelId(prefs.getString("active_embedding_model", CactusModelCatalog.DEFAULT_EMBEDDING_ID), CactusModelCatalog.DEFAULT_EMBEDDING_ID),
            computeBackend = backend,
            hybridCloudFallbackEnabled = prefs.getBoolean("hybrid_cloud_fallback", true),
            offlineSttEnabled = prefs.getBoolean("offline_stt_enabled", false),
            memoryVectorRagEnabled = prefs.getBoolean("memory_vector_rag", true),
            privateOfflineVisionDefault = prefs.getBoolean("private_offline_vision_default", false),
            privateOfflineDocsDefault = prefs.getBoolean("private_offline_docs_default", false),
            fastCactusScreenControlEnabled = prefs.getBoolean("fast_cactus_screen_control", true),
            huggingFaceToken = prefs.getString("hf_token", "")?.trim().orEmpty(),
            maxContextTokens = prefs.getInt("max_context_tokens", 2048),
            temperature = prefs.getFloat("temperature", 0.6f)
        )
        _config.value = loaded
        return loaded
    }

    private fun normalizeSavedModelId(raw: String?, fallback: String): String {
        val clean = raw?.trim().orEmpty()
        if (clean.isBlank()) return fallback
        return CactusModelCatalog.findById(clean)?.id ?: fallback
    }

    fun updateConfig(context: Context, transform: (CactusRuntimeConfig) -> CactusRuntimeConfig): CactusRuntimeConfig {
        val updated = transform(_config.value)
        _config.value = updated
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("server_base_url", updated.serverBaseUrl.trim().removeSuffix("/"))
            .putString("active_llm_model", updated.activeLlmModelId)
            .putString("active_vlm_model", updated.activeVlmModelId)
            .putString("active_stt_model", updated.activeSttModelId)
            .putString("active_embedding_model", updated.activeEmbeddingModelId)
            .putString("compute_backend", updated.computeBackend.id)
            .putBoolean("hybrid_cloud_fallback", updated.hybridCloudFallbackEnabled)
            .putBoolean("offline_stt_enabled", updated.offlineSttEnabled)
            .putBoolean("memory_vector_rag", updated.memoryVectorRagEnabled)
            .putBoolean("private_offline_vision_default", updated.privateOfflineVisionDefault)
            .putBoolean("private_offline_docs_default", updated.privateOfflineDocsDefault)
            .putBoolean("fast_cactus_screen_control", updated.fastCactusScreenControlEnabled)
            .putString("hf_token", updated.huggingFaceToken.trim())
            .putInt("max_context_tokens", updated.maxContextTokens)
            .putFloat("temperature", updated.temperature)
            .apply()
        return updated
    }

    /**
     * Returns true ONLY when genuine general/JSON/VLM local inference is ready:
     * - Either an `IN_PROCESS_SDK` chat/VLM model is downloaded on a supported `arm64-v8a` device, OR
     * - An OpenAI-compatible Cactus/MLX server endpoint is online.
     * Deliberately excludes bare `needle3.cact` tool-routers so Android screen automation is never hijacked.
     */
    fun isFastLocalInferenceReady(context: Context): Boolean {
        val appCtx = context.applicationContext
        if (CactusInProcessEngine.isAvailable(appCtx)) {
            val hasSdkChatOrVlm = CactusModelCatalog.inProcessSdkModels().any { spec ->
                (spec.capability == CactusModelCapability.LLM ||
                    spec.capability == CactusModelCapability.TOOL_CALLING ||
                    spec.capability == CactusModelCapability.VLM) &&
                    isModelOnDisk(appCtx, spec)
            }
            if (hasSdkChatOrVlm) return true
        }
        val st = _status.value
        if (st.isServerRunning && st.loadedServerModel.isNotBlank() &&
            !st.loadedServerModel.contains("needle", ignoreCase = true)
        ) {
            return true
        }
        val cfg = _config.value
        return probeServerEndpoint(cfg.serverBaseUrl).let {
            it.isOnline && it.apiStyle == CactusServerApiStyle.OPENAI
        }
    }

    /**
     * Returns true ONLY when an actual offline STT backend is ready to transcribe speech
     * (`whistle.cact` + binary, OR an in-process Whisper SDK model on arm64, OR an OpenAI server).
     */
    fun isOfflineSttReady(context: Context): Boolean {
        val appCtx = context.applicationContext
        if (resolveCactusBinaryPath(appCtx) != null && findDownloadedWhistleCact(appCtx) != null) {
            return true
        }
        if (CactusInProcessEngine.isAvailable(appCtx)) {
            val hasWhisper = CactusModelCatalog.sttModels().any { spec ->
                spec.isInProcessSdkModel && isModelOnDisk(appCtx, spec)
            }
            if (hasWhisper) return true
        }
        return _status.value.isServerRunning &&
            probeServerEndpoint(_config.value.serverBaseUrl).apiStyle == CactusServerApiStyle.OPENAI
    }

    fun findAnyDownloadedSdkModel(
        context: Context,
        capabilities: Set<CactusModelCapability>
    ): CactusModelSpec? {
        val appCtx = context.applicationContext
        if (!CactusInProcessEngine.isAvailable(appCtx)) return null
        return CactusModelCatalog.inProcessSdkModels().firstOrNull { spec ->
            spec.capability in capabilities && isModelOnDisk(appCtx, spec)
        }
    }

    fun isModelOnDisk(context: Context, spec: CactusModelSpec): Boolean {
        val dir = getModelDir(context.applicationContext, spec)
        if (!dir.exists() || !dir.isDirectory) return false
        val diskBytes = calculateDirectorySize(dir)
        return isModelDirectoryPopulated(dir, diskBytes)
    }

    fun resolveCactusBinaryPath(context: Context): File? {
        val appCtx = context.applicationContext
        val candidates = listOf(
            getLocalBinaryFile(appCtx),
            File(LocalEnvironmentManager.getPrefixDir(appCtx), "bin/cactus"),
            File(LocalEnvironmentManager.getHomeDir(appCtx), ".local/bin/cactus"),
            File("/data/data/com.termux/files/usr/bin/cactus")
        )
        return candidates.firstOrNull { f -> f.exists() && f.isFile && f.length() > 10_000L }
    }

    fun resolveModelArtifactPath(context: Context, spec: CactusModelSpec): String {
        val dir = getModelDir(context.applicationContext, spec)
        if (!dir.exists() || !dir.isDirectory) return spec.huggingFaceRepo

        val cactFiles = dir.walkTopDown().filter {
            it.isFile && it.extension.equals("cact", ignoreCase = true) && it.length() > 50_000L
        }.toList()
        if (cactFiles.isNotEmpty()) {
            val preferred = cactFiles.minByOrNull { f ->
                val name = f.name.lowercase()
                val score = when {
                    name.contains("int4") || name.contains("q4") || name.contains("cq4") -> 0
                    name.contains("cq2") || name.contains("2bit") -> 1
                    name.contains("int8") || name.contains("q8") -> 2
                    else -> 3
                }
                score * 1_000_000_000L + (f.length() / 1024L)
            }
            if (preferred != null) return preferred.absolutePath
        }

        val ggufFiles = dir.walkTopDown().filter {
            it.isFile && it.extension.equals("gguf", ignoreCase = true) && it.length() > 50_000L
        }.toList()
        if (ggufFiles.isNotEmpty()) {
            return (ggufFiles.minByOrNull { it.length() } ?: ggufFiles.first()).absolutePath
        }

        val configFile = dir.walkTopDown()
            .maxDepth(3)
            .firstOrNull { it.isFile && it.name.equals("config.json", ignoreCase = true) }
        if (configFile?.parentFile != null && configFile.parentFile != dir) {
            return configFile.parentFile!!.absolutePath
        }

        val diskBytes = calculateDirectorySize(dir)
        return if (isModelDirectoryPopulated(dir, diskBytes)) dir.absolutePath else spec.huggingFaceRepo
    }

    fun findLocalCactFile(context: Context, spec: CactusModelSpec): File? {
        if (spec.runtime != CactusModelRuntime.NATIVE_V4_CACT && !spec.isNativeCactModel) return null
        val dir = getModelDir(context.applicationContext, spec)
        if (!dir.exists() || !dir.isDirectory) return null
        val cactFiles = dir.walkTopDown().filter {
            it.isFile && it.extension.equals("cact", ignoreCase = true) && it.length() > 50_000L
        }.toList()
        if (cactFiles.isEmpty()) return null
        return cactFiles.minByOrNull { f ->
            val name = f.name.lowercase()
            val score = when {
                name.contains("int4") || name.contains("q4") || name.contains("cq4") -> 0
                name.contains("cq2") || name.contains("2bit") -> 1
                name.contains("int8") || name.contains("q8") -> 2
                else -> 3
            }
            score * 1_000_000_000L + (f.length() / 1024L)
        }
    }

    fun findAnyDownloadedNativeCactTextModel(context: Context): Pair<CactusModelSpec, File>? {
        val appCtx = context.applicationContext
        for (spec in CactusModelCatalog.nativeCactModels()) {
            if (spec.capability == CactusModelCapability.TOOL_CALLING || spec.capability == CactusModelCapability.LLM) {
                val f = findLocalCactFile(appCtx, spec)
                if (f != null) return spec to f
            }
        }
        return null
    }

    fun findDownloadedWhistleCact(context: Context): File? {
        val spec = CactusModelCatalog.findById("cactus:whistle") ?: return null
        return findLocalCactFile(context.applicationContext, spec)
    }

    fun shouldSkipAutoServerStart(modelId: String): Boolean {
        val elapsed = System.currentTimeMillis() - lastAutoLaunchFailureMs
        return lastAutoLaunchFailureModelId == modelId && elapsed in 0L..20_000L
    }

    fun getBinaryCliCapabilities(context: Context, forceRefresh: Boolean = false): CactusBinaryCliCaps {
        val appCtx = context.applicationContext
        val binFile = resolveCactusBinaryPath(appCtx) ?: return CactusBinaryCliCaps()
        val cacheKey = "${binFile.absolutePath}:${binFile.length()}:${binFile.lastModified()}"
        val cached = cachedCliCaps
        if (!forceRefresh && cached != null && cached.first == cacheKey) {
            return cached.second
        }

        ensureExecutable(binFile)
        val helpText = runCatching {
            val pb = ProcessBuilder(listOf(binFile.absolutePath, "--help"))
                .directory(getCactusRootDir(appCtx))
                .redirectErrorStream(true)
            configureCactusEnvironment(appCtx, pb.environment())
            val proc = pb.start()
            val sb = StringBuilder()
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var lines = 0
            while (lines < 80) {
                val l = reader.readLine() ?: break
                sb.appendLine(l)
                lines++
            }
            proc.waitFor(3, TimeUnit.SECONDS)
            runCatching { proc.destroy() }
            sb.toString().trim()
        }.getOrDefault("")

        val lower = helpText.lowercase()
        val caps = if (helpText.isBlank()) {
            CactusBinaryCliCaps(probed = true)
        } else {
            CactusBinaryCliCaps(
                probed = true,
                hasDoubleDashServe = lower.contains("--serve"),
                hasDoubleDashModel = lower.contains("--model"),
                hasServeSubcommand = Regex("(?m)^\\s*serve\\b").containsMatchIn(lower) ||
                    lower.contains("cactus serve"),
                hasRunSubcommand = Regex("(?m)^\\s*run\\b").containsMatchIn(lower) ||
                    lower.contains("cactus run"),
                hasDoubleDashPrompt = lower.contains("--prompt") || Regex("\\s-p\\b").containsMatchIn(lower),
                hasDoubleDashTools = lower.contains("--tools"),
                hasDoubleDashSystem = lower.contains("--system"),
                hasDoubleDashThreads = lower.contains("--threads"),
                hasDoubleDashImage = lower.contains("--image"),
                hasDoubleDashAudio = lower.contains("--audio"),
                rawHelpSnippet = helpText.lines().take(28).joinToString("\n")
            )
        }
        cachedCliCaps = cacheKey to caps
        if (helpText.isNotBlank()) {
            appendLog("[PROBE] Binary CLI: --model=${caps.hasDoubleDashModel}, --serve=${caps.hasDoubleDashServe}, --tools=${caps.hasDoubleDashTools}, --prompt=${caps.hasDoubleDashPrompt}, --audio=${caps.hasDoubleDashAudio}")
        }
        return caps
    }

    fun isNativeJniLibraryPresent(context: Context): Boolean {
        if (CactusInProcessEngine.isAvailable(context)) return true
        val nativeDir = File(context.applicationContext.applicationInfo.nativeLibraryDir)
        val localLibDir = File(getCactusRootDir(context), "lib")
        val names = listOf("libcactus.so", "libcactus_engine.so")
        return names.any { name ->
            File(nativeDir, name).exists() || File(localLibDir, name).exists()
        }
    }

    suspend fun refreshStatus(context: Context): CactusEngineStatus = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val cfg = _config.value
        val binFile = resolveCactusBinaryPath(appCtx)
        val sdkSupported = CactusInProcessEngine.isAvailable(appCtx)
        val jniReady = isNativeJniLibraryPresent(appCtx)
        val serverProbe = probeServerEndpoint(cfg.serverBaseUrl)
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val availRamMb = queryAvailableRamMb(appCtx)

        migrateLegacySdkModelFoldersIfNeeded(appCtx)

        val currentMap = _modelStates.value.toMutableMap()
        var totalStorageBytes = 0L

        for (spec in CactusModelCatalog.allModels) {
            val dir = getModelDir(appCtx, spec)
            val diskBytes = calculateDirectorySize(dir)
            totalStorageBytes += diskBytes
            val existing = currentMap[spec.id]
            val downloaded = isModelDirectoryPopulated(dir, diskBytes)
            currentMap[spec.id] = (existing ?: CactusModelInstallState(modelId = spec.id)).copy(
                isDownloaded = downloaded,
                localDirPath = dir.absolutePath,
                sizeOnDiskBytes = diskBytes
            )
        }
        _modelStates.value = currentMap

        val downloadedSdkCount = CactusModelCatalog.inProcessSdkModels().count { currentMap[it.id]?.isDownloaded == true }
        val statusMsg = when {
            sdkSupported && downloadedSdkCount > 0 && serverProbe.isOnline ->
                "In-Process JNI SDK READY ($downloadedSdkCount model(s)) + Cactus v4 Server ONLINE at ${cfg.serverBaseUrl}"
            sdkSupported && downloadedSdkCount > 0 ->
                "Cactus In-Process JNI SDK READY ($downloadedSdkCount offline model(s) downloaded — zero server needed!)"
            serverProbe.isOnline -> {
                val styleTag = if (serverProbe.apiStyle == CactusServerApiStyle.CACTUS_NATIVE) "Native v4" else "OpenAI API"
                val modelSuffix = if (serverProbe.modelName.isNotBlank()) " • ${serverProbe.modelName}" else ""
                "Cactus Local Server ONLINE at ${cfg.serverBaseUrl} ($styleTag$modelSuffix)"
            }
            sdkSupported ->
                "Cactus In-Process SDK (arm64 libcactus.so) READY! Download Qwen3 / Gemma / LFM2-VL below — runs 100% offline in-app."
            binFile != null ->
                "Cactus v4 Native Binary ready (${binFile.name}). Download Needle3/Whistle .cact or start local server."
            else ->
                "Download Qwen3 / LFM2-VL below for In-Process Offline AI, or install Cactus v4 binary for $abi."
        }

        val updated = _status.value.copy(
            binaryInstalled = binFile != null,
            binaryPath = binFile?.absolutePath.orEmpty(),
            nativeJniAvailable = jniReady,
            sdkSupported = sdkSupported,
            sdkLoadedModel = CactusInProcessEngine.getActiveLoadedSlug(),
            isServerRunning = serverProbe.isOnline,
            serverEndpoint = cfg.serverBaseUrl,
            loadedServerModel = serverProbe.modelName.ifBlank { _status.value.loadedServerModel },
            deviceAbi = abi,
            availableRamMb = availRamMb,
            totalStorageUsedMb = totalStorageBytes / (1024L * 1024L),
            indexedMemoryVectorsCount = CactusVectorMemoryIndex.getIndexedCount(),
            lastStatusMessage = statusMsg
        )
        _status.value = updated
        updated
    }

    private fun migrateLegacySdkModelFoldersIfNeeded(appCtx: Context) {
        val legacyRoot = getModelsDir(appCtx)
        if (!legacyRoot.exists()) return
        for (spec in CactusModelCatalog.inProcessSdkModels()) {
            val targetDir = getModelDir(appCtx, spec)
            if (isModelDirectoryPopulated(targetDir, calculateDirectorySize(targetDir))) continue
            val legacyCandidates = (listOf(spec.slug) + spec.aliases.map { it.removePrefix("cactus:") }).distinct()
            for (legacyName in legacyCandidates) {
                val oldDir = File(legacyRoot, legacyName)
                val oldBytes = calculateDirectorySize(oldDir)
                if (oldDir.exists() && isModelDirectoryPopulated(oldDir, oldBytes)) {
                    runCatching {
                        targetDir.parentFile?.mkdirs()
                        if (!oldDir.renameTo(targetDir)) {
                            oldDir.copyRecursively(targetDir, overwrite = true)
                            oldDir.deleteRecursively()
                        }
                        flattenSingleSubdirIfNeeded(targetDir)
                        appendLog("[MIGRATE] Moved ${spec.displayName} weights to SDK directory ${targetDir.name}")
                    }
                    break
                }
            }
        }
    }

    suspend fun installCactusNativeBinary(context: Context): Result<File> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        if (_status.value.isInstallingBinary) {
            return@withContext Result.failure(IllegalStateException("Cactus binary install already running."))
        }
        val targetFolder = resolveAndroidTargetFolder()
        _status.value = _status.value.copy(
            isInstallingBinary = true,
            binaryDownloadProgress = 0.02f,
            lastStatusMessage = "Resolving latest Cactus $targetFolder release..."
        )

        try {
            val releaseVersions = resolveCandidateReleaseVersions()
            var downloadedFile: File? = null
            var lastErrorMsg = ""

            for (version in releaseVersions) {
                val url = CactusModelCatalog.buildBinaryUrl(releaseVersion = version, targetFolder = targetFolder)
                appendLog("[INSTALL] Fetching $url")
                _status.value = _status.value.copy(
                    lastStatusMessage = "Downloading Cactus v$version ($targetFolder)..."
                )

                val targetFile = getLocalBinaryFile(appCtx)
                val tmpFile = File(targetFile.parentFile, "cactus.download.tmp")
                if (tmpFile.exists()) tmpFile.delete()

                val req = buildHfRequest(url)
                val attemptOk = try {
                    downloadClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            lastErrorMsg = "HTTP ${resp.code} for $targetFolder v$version"
                            appendLog("[INSTALL] $lastErrorMsg")
                            false
                        } else {
                            val body = resp.body ?: throw IllegalStateException("Empty response body.")
                            val total = body.contentLength().coerceAtLeast(1L)
                            var copied = 0L
                            val buf = ByteArray(32 * 1024)
                            body.byteStream().use { input ->
                                FileOutputStream(tmpFile).use { out ->
                                    while (true) {
                                        val read = input.read(buf)
                                        if (read <= 0) break
                                        out.write(buf, 0, read)
                                        copied += read
                                        val frac = (copied.toFloat() / total.toFloat()).coerceIn(0.02f, 0.99f)
                                        _status.value = _status.value.copy(binaryDownloadProgress = frac)
                                    }
                                }
                            }
                            if (tmpFile.length() < 50_000L) {
                                tmpFile.delete()
                                lastErrorMsg = "Downloaded binary too small (${tmpFile.length()} B)"
                                false
                            } else {
                                if (targetFile.exists()) targetFile.delete()
                                tmpFile.renameTo(targetFile)
                                ensureExecutable(targetFile)
                                downloadedFile = targetFile
                                true
                            }
                        }
                    }
                } catch (e: Exception) {
                    runCatching { tmpFile.delete() }
                    lastErrorMsg = e.message ?: "Network error"
                    appendLog("[INSTALL] Error on v$version: $lastErrorMsg")
                    false
                }

                if (attemptOk && downloadedFile != null) break
            }

            val installed = downloadedFile
                ?: throw IllegalStateException(
                    "Cactus binary download failed ($lastErrorMsg). Internet check karein."
                )

            appendLog("[INSTALL] Installed Cactus binary (${installed.length() / 1024} KB) at ${installed.absolutePath}")
            _status.value = _status.value.copy(
                isInstallingBinary = false,
                binaryDownloadProgress = 1f,
                binaryInstalled = true,
                binaryPath = installed.absolutePath,
                lastStatusMessage = "Cactus Engine installed (${formatSizeMb(installed.length())})."
            )
            refreshStatus(appCtx)
            Result.success(installed)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install Cactus binary", e)
            appendLog("[ERROR] Binary install failed: ${e.message}")
            _status.value = _status.value.copy(
                isInstallingBinary = false,
                binaryDownloadProgress = 0f,
                lastStatusMessage = "Install failed: ${e.message}"
            )
            Result.failure(e)
        }
    }

    /**
     * Downloads models cleanly for EITHER:
     * - [CactusModelRuntime.IN_PROCESS_SDK] (official `1.4.3-beta` zip into `filesDir/models/<sdkSlug>`)
     * - [CactusModelRuntime.NATIVE_V4_CACT] or [CactusModelRuntime.EXTERNAL_OPENAI] (HF repo into `filesDir/cactus/models/<slug>`)
     */
    fun startModelDownload(context: Context, spec: CactusModelSpec) {
        val appCtx = context.applicationContext
        if (downloadJobs[spec.id]?.isActive == true) return

        val job = runtimeScope.launch {
            updateModelState(spec.id) {
                it.copy(
                    isDownloading = true,
                    downloadProgress = 0.01f,
                    currentFileName = "Preparing download...",
                    lastError = null
                )
            }

            val targetDir = getModelDir(appCtx, spec).apply { mkdirs() }

            try {
                if (spec.isInProcessSdkModel) {
                    val sdkUrl = CactusInProcessEngine.resolveDownloadUrl(spec)
                    if (!sdkUrl.isNullOrBlank()) {
                        appendLog("[MODEL] Downloading SDK bundle for ${spec.displayName} (${spec.sdkSlug})")
                        val zipOk = downloadSingleArchiveWithProgress(spec, sdkUrl, targetDir, "${spec.sdkSlug}.zip")
                        if (zipOk) {
                            File(targetDir, ".cactus_ready").writeText("sdk=${spec.sdkSlug}\nts=${System.currentTimeMillis()}\n")
                            markDownloadCompleted(appCtx, spec, targetDir, "Cactus Official SDK")
                            return@launch
                        }
                    }
                }

                val candidateRepos = (listOf(spec.huggingFaceRepo) + spec.fallbackHuggingFaceRepos).distinct()
                var resolvedRepo: String? = null
                var filesToDownload: List<Pair<String, Long>> = emptyList()
                var lastProbeErr: String? = null

                for (repo in candidateRepos) {
                    if (!isActive) throw CancellationException("Download cancelled")
                    appendLog("[MODEL] Querying Hugging Face tree for $repo...")
                    try {
                        val files = fetchHuggingFaceRepoFiles(repo)
                        if (files.isNotEmpty()) {
                            resolvedRepo = repo
                            filesToDownload = selectOptimalMobileFiles(files)
                            break
                        } else {
                            lastProbeErr = "No compatible model weights found in $repo."
                        }
                    } catch (probeEx: Exception) {
                        lastProbeErr = probeEx.message
                        appendLog("[MODEL] Skipping $repo: ${probeEx.message}")
                    }
                }

                val activeRepo = resolvedRepo
                    ?: throw IllegalStateException(lastProbeErr ?: "Could not access ${spec.huggingFaceRepo} on Hugging Face.")

                if (filesToDownload.isEmpty()) {
                    throw IllegalStateException("No downloadable model files found in $activeRepo.")
                }

                appendLog("[MODEL] Downloading ${filesToDownload.size} file(s) from $activeRepo")
                val totalBytes = filesToDownload.sumOf { it.second }.coerceAtLeast(1L)
                var cumulativeDownloaded = 0L

                for ((relPath, expectedSize) in filesToDownload) {
                    if (!isActive) throw CancellationException("Download cancelled")
                    val outFile = File(targetDir, relPath)
                    outFile.parentFile?.mkdirs()

                    updateModelState(spec.id) { it.copy(currentFileName = outFile.name) }

                    if (outFile.exists() && expectedSize > 0L && outFile.length() == expectedSize) {
                        cumulativeDownloaded += expectedSize
                        updateModelState(spec.id) {
                            it.copy(
                                downloadedBytes = cumulativeDownloaded,
                                totalBytes = totalBytes,
                                downloadProgress = (cumulativeDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0.02f, 0.99f)
                            )
                        }
                        continue
                    }

                    val encodedPath = relPath.split("/").joinToString("/") { seg ->
                        java.net.URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
                    }
                    val fileUrl = "https://huggingface.co/$activeRepo/resolve/main/$encodedPath"
                    val fileBytes = downloadUrlToFile(
                        spec = spec,
                        url = fileUrl,
                        outFile = outFile,
                        baseDownloaded = cumulativeDownloaded,
                        knownTotalBytes = totalBytes
                    )
                    cumulativeDownloaded += fileBytes

                    if (outFile.extension.equals("zip", ignoreCase = true)) {
                        updateModelState(spec.id) { it.copy(currentFileName = "Extracting ${outFile.name}...") }
                        extractZipSafely(outFile, targetDir)
                        flattenSingleSubdirIfNeeded(targetDir)
                        runCatching { outFile.delete() }
                    }
                }

                File(targetDir, ".cactus_ready").writeText("repo=$activeRepo\nts=${System.currentTimeMillis()}\n")
                markDownloadCompleted(appCtx, spec, targetDir, activeRepo)
            } catch (ce: CancellationException) {
                cleanupPartFiles(targetDir)
                appendLog("[MODEL] Cancelled download for ${spec.displayName}")
                updateModelState(spec.id) {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = 0f,
                        currentFileName = "",
                        lastError = "Download cancelled"
                    )
                }
            } catch (e: Exception) {
                cleanupPartFiles(targetDir)
                Log.e(TAG, "Model download failed for ${spec.id}", e)
                appendLog("[ERROR] ${spec.displayName} download failed: ${e.message}")
                updateModelState(spec.id) {
                    it.copy(
                        isDownloading = false,
                        currentFileName = "",
                        lastError = e.message ?: "Download failed"
                    )
                }
            } finally {
                downloadJobs.remove(spec.id)
            }
        }
        downloadJobs[spec.id] = job
    }

    private suspend fun downloadSingleArchiveWithProgress(
        spec: CactusModelSpec,
        url: String,
        targetDir: File,
        archiveName: String
    ): Boolean {
        val archiveFile = File(targetDir, archiveName)
        return try {
            downloadUrlToFile(
                spec = spec,
                url = url,
                outFile = archiveFile,
                baseDownloaded = 0L,
                knownTotalBytes = spec.estimatedDownloadMb.toLong().coerceAtLeast(1L) * 1024L * 1024L
            )
            updateModelState(spec.id) { it.copy(currentFileName = "Extracting $archiveName...") }
            extractZipSafely(archiveFile, targetDir)
            flattenSingleSubdirIfNeeded(targetDir)
            runCatching { archiveFile.delete() }
            isModelDirectoryPopulated(targetDir, calculateDirectorySize(targetDir))
        } catch (ce: CancellationException) {
            runCatching { archiveFile.delete() }
            throw ce
        } catch (e: Exception) {
            runCatching { archiveFile.delete() }
            appendLog("[MODEL] Primary CDN fallback (${spec.sdkSlug}): ${e.message}")
            false
        }
    }

    private suspend fun downloadUrlToFile(
        spec: CactusModelSpec,
        url: String,
        outFile: File,
        baseDownloaded: Long,
        knownTotalBytes: Long
    ): Long = withContext(Dispatchers.IO) {
        outFile.parentFile?.mkdirs()
        val tmpFile = File(outFile.parentFile, "${outFile.name}.part")
        val req = buildHfRequest(url)
        try {
            downloadClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException(formatHfHttpError(resp.code, "${spec.displayName} ($url)"))
                }
                val body = resp.body ?: throw IllegalStateException("Empty body for ${outFile.name}")
                val contentLen = body.contentLength().takeIf { it > 0L } ?: knownTotalBytes
                val totalDenom = maxOf(knownTotalBytes, baseDownloaded + contentLen).coerceAtLeast(1L)
                val buf = ByteArray(64 * 1024)
                var fileBytes = 0L
                var lastUiPushMs = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tmpFile).use { out ->
                        while (true) {
                            if (!isActive) {
                                throw CancellationException("Download cancelled")
                            }
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                            fileBytes += read
                            val nowMs = System.currentTimeMillis()
                            if (nowMs - lastUiPushMs >= 120L) {
                                lastUiPushMs = nowMs
                                val nowTotal = baseDownloaded + fileBytes
                                val denom = maxOf(totalDenom, nowTotal).coerceAtLeast(1L)
                                updateModelState(spec.id) {
                                    it.copy(
                                        currentFileName = outFile.name,
                                        downloadedBytes = nowTotal,
                                        totalBytes = denom,
                                        downloadProgress = (nowTotal.toFloat() / denom.toFloat()).coerceIn(0.02f, 0.99f)
                                    )
                                }
                            }
                        }
                    }
                }
                if (outFile.exists()) outFile.delete()
                tmpFile.renameTo(outFile)
                fileBytes
            }
        } catch (e: Exception) {
            runCatching { tmpFile.delete() }
            throw e
        }
    }

    private suspend fun markDownloadCompleted(
        appCtx: Context,
        spec: CactusModelSpec,
        targetDir: File,
        sourceLabel: String
    ) {
        val diskBytes = calculateDirectorySize(targetDir)
        updateModelState(spec.id) {
            it.copy(
                isDownloading = false,
                isDownloaded = true,
                downloadProgress = 1f,
                downloadedBytes = diskBytes,
                totalBytes = diskBytes,
                currentFileName = "",
                localDirPath = targetDir.absolutePath,
                sizeOnDiskBytes = diskBytes,
                lastError = null
            )
        }
        appendLog("[MODEL] Downloaded ${spec.displayName} from $sourceLabel (${formatSizeMb(diskBytes)})")
        refreshStatus(appCtx)
    }

    fun cancelModelDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)
    }

    suspend fun deleteModel(context: Context, spec: CactusModelSpec) = withContext(Dispatchers.IO) {
        cancelModelDownload(spec.id)
        if (spec.isInProcessSdkModel && CactusInProcessEngine.getActiveLoadedSlug() == spec.sdkSlug) {
            CactusInProcessEngine.unloadAll()
        }
        val dir = getModelDir(context, spec)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        val legacyDir = File(getModelsDir(context), spec.slug)
        if (legacyDir.exists() && legacyDir != dir) {
            legacyDir.deleteRecursively()
        }
        updateModelState(spec.id) {
            it.copy(
                isDownloaded = false,
                isDownloading = false,
                downloadProgress = 0f,
                downloadedBytes = 0L,
                totalBytes = 0L,
                currentFileName = "",
                sizeOnDiskBytes = 0L,
                lastError = null
            )
        }
        refreshStatus(context)
    }

    suspend fun downloadNeedle3AndStartServer(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val needleSpec = CactusModelCatalog.findById("cactus:needle3")
            ?: return@withContext Result.failure(IllegalStateException("Needle3 spec not found."))

        if (resolveCactusBinaryPath(appCtx) == null) {
            val binRes = installCactusNativeBinary(appCtx)
            if (binRes.isFailure) {
                return@withContext Result.failure(binRes.exceptionOrNull() ?: IllegalStateException("Cactus binary install failed."))
            }
        }

        if (findLocalCactFile(appCtx, needleSpec) == null) {
            startModelDownload(appCtx, needleSpec)
            val job = downloadJobs[needleSpec.id]
            job?.join()
            if (findLocalCactFile(appCtx, needleSpec) == null) {
                val err = _modelStates.value[needleSpec.id]?.lastError
                    ?: "Needle3 (.cact) download incomplete. Retry karein."
                return@withContext Result.failure(IllegalStateException(err))
            }
        }

        startLocalServer(appCtx, needleSpec)
    }

    internal fun prepareRuntimeToolsAndSystemFiles(appCtx: Context): Pair<File, File> {
        val rtDir = File(getCactusRootDir(appCtx), "runtime").apply { mkdirs() }
        val toolsFile = File(rtDir, "tools.json")
        val systemFile = File(rtDir, "system.txt")

        val toolsJson = """
            [
              {
                "name": "set_flashlight",
                "description": "Turn the Android device flashlight or torch ON or OFF.",
                "parameters": {
                  "type": "object",
                  "properties": {
                    "enabled": { "type": "boolean", "description": "true for ON, false for OFF" }
                  },
                  "required": ["enabled"]
                }
              },
              {
                "name": "set_volume",
                "description": "Set device media volume percentage from 0 to 100.",
                "parameters": {
                  "type": "object",
                  "properties": {
                    "percent": { "type": "integer", "description": "Target media volume 0-100" }
                  },
                  "required": ["percent"]
                }
              },
              {
                "name": "get_device_telemetry",
                "description": "Read device battery level, charging state, free RAM and current India IST time.",
                "parameters": { "type": "object", "properties": {} }
              },
              {
                "name": "set_timer",
                "description": "Start a countdown timer in minutes on Android.",
                "parameters": {
                  "type": "object",
                  "properties": {
                    "minutes": { "type": "integer", "description": "Duration in minutes" }
                  },
                  "required": ["minutes"]
                }
              },
              {
                "name": "launch_app",
                "description": "Open an installed Android application by name like WhatsApp, YouTube, Settings or Camera.",
                "parameters": {
                  "type": "object",
                  "properties": {
                    "app_name": { "type": "string", "description": "Android app name to launch" }
                  },
                  "required": ["app_name"]
                }
              }
            ]
        """.trimIndent()
        runCatching { toolsFile.writeText(toolsJson) }

        val systemText = "You are Agentra, an ultra-fast on-device Android assistant running locally on Cactus Compute. Device: ${Build.MODEL}. Locale: India (IST). Respond clearly and concisely in Hinglish or English."
        runCatching { systemFile.writeText(systemText) }

        return toolsFile to systemFile
    }

    suspend fun startLocalServer(
        context: Context,
        modelSpec: CactusModelSpec? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        serverMutex.withLock {
            val appCtx = context.applicationContext
            val cfg = loadConfig(appCtx)

            val existingProbe = probeServerEndpoint(cfg.serverBaseUrl)
            if (existingProbe.isOnline) {
                val styleTag = if (existingProbe.apiStyle == CactusServerApiStyle.CACTUS_NATIVE) "Native v4" else "OpenAI API"
                _status.value = _status.value.copy(
                    isServerRunning = true,
                    serverEndpoint = cfg.serverBaseUrl,
                    loadedServerModel = existingProbe.modelName,
                    lastStatusMessage = "Cactus Server ONLINE at ${cfg.serverBaseUrl} ($styleTag)"
                )
                return@withContext Result.success("Cactus Server already ONLINE at ${cfg.serverBaseUrl}")
            }

            stopLocalServerLocked()

            val binFile = resolveCactusBinaryPath(appCtx)
            if (binFile == null) {
                val err = if (CactusInProcessEngine.isAvailable(appCtx)) {
                    "In-Process SDK models run directly bina server ke! Agar Needle3 (.cact) server bhi chahiye to upar 'Install Engine' tap karein."
                } else {
                    "Cactus binary install nahi hai. Upar 'Install Engine' par tap karein."
                }
                _status.value = _status.value.copy(lastStatusMessage = err)
                return@withContext Result.failure(IllegalStateException(err))
            }
            ensureExecutable(binFile)

            var chosenSpec = modelSpec ?: CactusModelCatalog.findById(cfg.activeLlmModelId)
                ?: CactusModelCatalog.findById("cactus:needle3")
                ?: CactusModelCatalog.allModels.first()

            val port = extractPort(cfg.serverBaseUrl, defaultPort = 8090)
            val caps = getBinaryCliCapabilities(appCtx)
            var modelArg = resolveModelArtifactPath(appCtx, chosenSpec)
            val isV4CactOnlyBinary = caps.hasDoubleDashServe && caps.hasDoubleDashModel && !caps.hasServeSubcommand

            if (isV4CactOnlyBinary) {
                val cactFile = findLocalCactFile(appCtx, chosenSpec)
                if (cactFile != null) {
                    modelArg = cactFile.absolutePath
                } else {
                    val fallbackCact = findAnyDownloadedNativeCactTextModel(appCtx)
                    if (fallbackCact != null) {
                        chosenSpec = fallbackCact.first
                        modelArg = fallbackCact.second.absolutePath
                        appendLog("[SERVER] '${modelSpec?.displayName ?: cfg.activeLlmModelId}' runs directly in-app via JNI SDK; booting v4 server with ${chosenSpec.displayName}")
                    } else {
                        val hasSdkDownloaded = findAnyDownloadedSdkModel(
                            appCtx,
                            setOf(CactusModelCapability.LLM, CactusModelCapability.TOOL_CALLING)
                        ) != null
                        val err = if (hasSdkDownloaded) {
                            "Aapka selected model In-Process JNI SDK se bina server ke directly chalta hai! v4 HTTP server sirf Needle3 (.cact) ke liye hota hai."
                        } else {
                            "Cactus v4 Server sirf `.cact` files load karta hai. 'Download Needle3 (.cact) & Start' tap karein ya Qwen3 In-Process model download karein."
                        }
                        appendLog("[SERVER] $err")
                        _status.value = _status.value.copy(isServerRunning = false, lastStatusMessage = err)
                        lastAutoLaunchFailureMs = System.currentTimeMillis()
                        lastAutoLaunchFailureModelId = chosenSpec.id
                        return@withContext Result.failure(IllegalStateException(err))
                    }
                }
            } else if (!File(modelArg).exists()) {
                val err = "Model '${chosenSpec.displayName}' download nahi hai. Catalog se pehle Download karein."
                appendLog("[SERVER] $err (path=$modelArg)")
                _status.value = _status.value.copy(isServerRunning = false, lastStatusMessage = err)
                lastAutoLaunchFailureMs = System.currentTimeMillis()
                lastAutoLaunchFailureModelId = chosenSpec.id
                return@withContext Result.failure(IllegalStateException(err))
            }

            val extraFlags = mutableListOf<String>()
            if (cfg.computeBackend == CactusComputeBackend.CPU_NEON && caps.hasDoubleDashThreads) {
                extraFlags.addAll(listOf("--threads", "4"))
            }

            // Always keep `--tools` on Needle3 server attempts (Needle3 without `--tools` returns empty calls on every turn!)
            val candidateCommands = buildList {
                if (caps.hasDoubleDashServe && caps.hasDoubleDashModel) {
                    val whistleCact = findDownloadedWhistleCact(appCtx)
                    val (toolsFile, systemFile) = prepareRuntimeToolsAndSystemFiles(appCtx)

                    val withWhistleCmd = mutableListOf(binFile.absolutePath, "--model", modelArg)
                    if (whistleCact != null && whistleCact.absolutePath != modelArg) {
                        withWhistleCmd.addAll(listOf("--model", whistleCact.absolutePath))
                    }
                    if (caps.hasDoubleDashTools && toolsFile.exists()) {
                        withWhistleCmd.addAll(listOf("--tools", toolsFile.absolutePath))
                    }
                    if (caps.hasDoubleDashSystem && systemFile.exists()) {
                        withWhistleCmd.addAll(listOf("--system", systemFile.absolutePath))
                    }
                    withWhistleCmd.addAll(listOf("--serve", "--port", port.toString()))
                    withWhistleCmd.addAll(extraFlags)
                    add(withWhistleCmd)

                    // Attempt 2: Drop co-loaded whistle if needed, STILL keeping --tools!
                    val needleOnlyCmd = mutableListOf(binFile.absolutePath, "--model", modelArg)
                    if (caps.hasDoubleDashTools && toolsFile.exists()) {
                        needleOnlyCmd.addAll(listOf("--tools", toolsFile.absolutePath))
                    }
                    if (caps.hasDoubleDashSystem && systemFile.exists()) {
                        needleOnlyCmd.addAll(listOf("--system", systemFile.absolutePath))
                    }
                    needleOnlyCmd.addAll(listOf("--serve", "--port", port.toString()))
                    needleOnlyCmd.addAll(extraFlags)
                    add(needleOnlyCmd)
                } else if (caps.hasDoubleDashServe) {
                    add(listOf(binFile.absolutePath, modelArg, "--serve", "--port", port.toString()) + extraFlags)
                } else {
                    add(listOf(binFile.absolutePath, "serve", modelArg, "--port", port.toString()))
                }
            }.distinct()

            var lastFailure: Result<String>? = null
            for ((idx, cmd) in candidateCommands.withIndex()) {
                val attempt = launchServerProcessLocked(appCtx, cmd, port, chosenSpec, modelArg)
                if (attempt.isSuccess) {
                    lastAutoLaunchFailureMs = 0L
                    lastAutoLaunchFailureModelId = ""
                    return@withContext attempt
                }
                lastFailure = attempt
                val errMsg = attempt.exceptionOrNull()?.message.orEmpty()
                if (errMsg.contains("unreadable", ignoreCase = true) ||
                    errMsg.contains("cannot load", ignoreCase = true) ||
                    idx == candidateCommands.lastIndex
                ) {
                    break
                }
                appendLog("[SERVER] Retrying single-model Cactus v4 flags (${idx + 2}/${candidateCommands.size})...")
                stopLocalServerLocked()
            }

            lastAutoLaunchFailureMs = System.currentTimeMillis()
            lastAutoLaunchFailureModelId = chosenSpec.id
            lastFailure ?: Result.failure(IllegalStateException("Failed to start Cactus local server."))
        }
    }

    private suspend fun launchServerProcessLocked(
        appCtx: Context,
        cmd: List<String>,
        port: Int,
        chosenSpec: CactusModelSpec,
        modelArg: String
    ): Result<String> {
        appendLog("[SERVER] Starting: ${cmd.joinToString(" ")}")
        _status.value = _status.value.copy(lastStatusMessage = "Starting Cactus server on :$port (${chosenSpec.displayName})...")

        val startupLines = java.util.Collections.synchronizedList(mutableListOf<String>())

        return try {
            val pb = ProcessBuilder(cmd)
                .directory(getCactusRootDir(appCtx))
                .redirectErrorStream(true)
            configureCactusEnvironment(appCtx, pb.environment())

            val proc = pb.start()
            serverProcess = proc
            logReaderJob = runtimeScope.launch {
                try {
                    BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            if (startupLines.size < 60) {
                                startupLines.add(line)
                            }
                            appendLog("[cactus] $line")
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    if (serverProcess === proc) {
                        _status.value = _status.value.copy(isServerRunning = false)
                    }
                }
            }

            val targetUrl = "http://127.0.0.1:$port"
            var probeResult = CactusServerProbeResult()
            for (attempt in 0 until 18) {
                delay(350L)
                if (!proc.isAlive) {
                    delay(120L)
                    val code = runCatching { proc.exitValue() }.getOrDefault(-1)
                    val summary = extractStartupErrorSummary(startupLines, modelArg, chosenSpec, code)
                    throw IllegalStateException(summary)
                }
                val probe = probeServerEndpoint(targetUrl)
                if (probe.isOnline) {
                    probeResult = probe
                    break
                }
            }

            if (probeResult.isOnline) {
                val styleTag = if (probeResult.apiStyle == CactusServerApiStyle.CACTUS_NATIVE) "Native v4" else "OpenAI API"
                // Preserve user's chosen SDK LLM if they selected Qwen3/Gemma — do not overwrite activeLlmModelId with needle3 unexpectedly!
                val currentActiveSpec = CactusModelCatalog.findById(_config.value.activeLlmModelId)
                val shouldKeepActive = currentActiveSpec != null && currentActiveSpec.isInProcessSdkModel && isModelOnDisk(appCtx, currentActiveSpec)
                if (!shouldKeepActive) {
                    updateConfig(appCtx) { it.copy(serverBaseUrl = targetUrl, activeLlmModelId = chosenSpec.id) }
                } else {
                    updateConfig(appCtx) { it.copy(serverBaseUrl = targetUrl) }
                }
                _status.value = _status.value.copy(
                    isServerRunning = true,
                    serverEndpoint = targetUrl,
                    loadedServerModel = probeResult.modelName.ifBlank { chosenSpec.displayName },
                    lastStatusMessage = "Cactus Server ONLINE ($targetUrl • $styleTag • ${chosenSpec.displayName})"
                )
                Result.success("Cactus Server ONLINE at $targetUrl (${chosenSpec.displayName})")
            } else if (proc.isAlive) {
                val msg = "Cactus process running on :$port (${chosenSpec.displayName} loading). Wait 5s & tap Probe."
                _status.value = _status.value.copy(
                    isServerRunning = true,
                    serverEndpoint = targetUrl,
                    loadedServerModel = chosenSpec.displayName,
                    lastStatusMessage = msg
                )
                Result.success(msg)
            } else {
                val code = runCatching { proc.exitValue() }.getOrDefault(-1)
                throw IllegalStateException(extractStartupErrorSummary(startupLines, modelArg, chosenSpec, code))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting Cactus server", e)
            stopLocalServerLocked()
            val err = e.message ?: "Failed starting Cactus local server"
            _status.value = _status.value.copy(isServerRunning = false, lastStatusMessage = err)
            Result.failure(e)
        }
    }

    private fun extractStartupErrorSummary(
        startupLines: List<String>,
        modelArg: String,
        spec: CactusModelSpec,
        exitCode: Int
    ): String {
        val snapshot = synchronized(startupLines) { startupLines.toList() }
        val nonHelpErrors = snapshot.filter { line ->
            val l = line.trim().lowercase()
            l.isNotBlank() &&
                !l.startsWith("--") &&
                !l.startsWith("-h") &&
                !l.contains("worker threads") &&
                !l.contains("benchmark mode") &&
                !l.contains("refuse a turn")
        }
        val bestErrorLine = nonHelpErrors.firstOrNull { line ->
            val l = line.lowercase()
            l.contains("error") || l.contains("fail") || l.contains("cannot") ||
                l.contains("invalid") || l.contains("unsupported") || l.contains("magic") ||
                l.contains("cact") || l.contains("missing") || l.contains("usage")
        } ?: nonHelpErrors.firstOrNull().orEmpty()

        val isDirNotCact = File(modelArg).isDirectory
        val formatHint = if (isDirNotCact && !modelArg.endsWith(".cact", ignoreCase = true)) {
            " Note: Cactus v4 server sirf `.cact` files (Needle3 / Whistle) load karta hai."
        } else {
            ""
        }
        return if (bestErrorLine.isNotBlank()) {
            "Cactus exited (code $exitCode): ${bestErrorLine.take(180)}$formatHint"
        } else {
            "Cactus server exited (code $exitCode). Logs open karke details dekhein.$formatHint"
        }
    }

    suspend fun stopLocalServer(context: Context) = withContext(Dispatchers.IO) {
        serverMutex.withLock {
            stopLocalServerLocked()
            appendLog("[SERVER] Cactus local server stopped.")
            refreshStatus(context)
        }
    }

    private fun stopLocalServerLocked() {
        runCatching { logReaderJob?.cancel() }
        logReaderJob = null
        runCatching { serverProcess?.destroy() }
        serverProcess = null
    }

    suspend fun executeNativeCli(
        context: Context,
        args: List<String>,
        timeoutSeconds: Long = 60L,
        stdinInput: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val binFile = resolveCactusBinaryPath(appCtx)
            ?: return@withContext Result.failure(IllegalStateException("Cactus binary not installed."))
        ensureExecutable(binFile)

        try {
            val fullCmd = listOf(binFile.absolutePath) + args
            appendLog("[CLI] ${fullCmd.joinToString(" ").take(220)}")
            val pb = ProcessBuilder(fullCmd)
                .directory(getCactusRootDir(appCtx))
                .redirectErrorStream(true)
            configureCactusEnvironment(appCtx, pb.environment())

            val proc = pb.start()
            if (stdinInput != null) {
                runCatching {
                    proc.outputStream.bufferedWriter().use { writer ->
                        writer.write(stdinInput)
                        if (!stdinInput.endsWith("\n")) writer.newLine()
                        writer.flush()
                    }
                }
            } else {
                runCatching { proc.outputStream.close() }
            }

            val output = StringBuilder()
            val readerJob = launch {
                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        output.appendLine(line)
                    }
                }
            }
            val finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                readerJob.cancel()
                return@withContext Result.failure(IllegalStateException("Cactus CLI timed out after ${timeoutSeconds}s."))
            }
            readerJob.join()
            val text = output.toString().trim()
            if (proc.exitValue() == 0 && text.isNotBlank() && !looksLikeCliHelpDump(text)) {
                Result.success(text)
            } else {
                val shortErr = text.lines().firstOrNull { it.isNotBlank() }?.take(180)
                    ?: "Cactus CLI exited with code ${proc.exitValue()}"
                Result.failure(IllegalStateException(shortErr))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun looksLikeCliHelpDump(text: String): Boolean {
        val lower = text.lowercase()
        return (lower.contains("--serve") && lower.contains("--threads")) ||
            lower.contains("--fail-input-overflow") ||
            (lower.startsWith("usage:") && lower.contains("cactus"))
    }

    /**
     * Probes the configured Cactus HTTP server accurately without false positives:
     * - `GET /v1/models` (200 JSON) or `GET /health` (200) -> [CactusServerApiStyle.OPENAI]
     * - `POST /complete {}` returning verified `cactus --serve` JSON error (`POST /complete needs a JSON body...`) -> [CactusServerApiStyle.CACTUS_NATIVE]
     */
    fun probeServerEndpoint(baseUrl: String): CactusServerProbeResult {
        val clean = baseUrl.trim().removeSuffix("/").ifBlank { "http://127.0.0.1:8090" }

        // 1. OpenAI `/v1/models` (strictly 2xx + JSON payload)
        try {
            val req = Request.Builder().url("$clean/v1/models").get().build()
            probeClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val parsedObj = runCatching { JSONObject(body) }.getOrNull()
                    if (parsedObj != null && (parsedObj.has("data") || parsedObj.has("object") || parsedObj.has("models"))) {
                        val modelName = parsedObj.optJSONArray("data")?.optJSONObject(0)?.optString("id").orEmpty()
                        return CactusServerProbeResult(
                            isOnline = true,
                            modelName = modelName,
                            apiStyle = CactusServerApiStyle.OPENAI
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 2. OpenAI/MLX `/health` (strictly 2xx)
        try {
            val req = Request.Builder().url("$clean/health").get().build()
            probeClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val modelName = runCatching { JSONObject(body).optString("model").orEmpty() }.getOrDefault("")
                    return CactusServerProbeResult(
                        isOnline = true,
                        modelName = modelName.ifBlank { _status.value.loadedServerModel },
                        apiStyle = CactusServerApiStyle.OPENAI
                    )
                }
            }
        } catch (_: Exception) {
        }

        // 3. Exact Cactus v4 `--serve` handshake (`POST /complete` with `{}` returns 400 JSON mentioning `POST /complete` & `input`)
        try {
            val req = Request.Builder()
                .url("$clean/complete")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            probeClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val looksLikeNativeComplete = (resp.code == 400 || resp.isSuccessful) &&
                    (body.contains("POST /complete", ignoreCase = true) ||
                        body.contains("input field", ignoreCase = true) ||
                        body.contains("\"function_calls\"") ||
                        body.contains("\"type\":\"call\""))
                if (looksLikeNativeComplete) {
                    return CactusServerProbeResult(
                        isOnline = true,
                        modelName = _status.value.loadedServerModel.ifBlank { "Needle3 (.cact Native)" },
                        apiStyle = CactusServerApiStyle.CACTUS_NATIVE
                    )
                }
            }
        } catch (_: Exception) {
        }

        return CactusServerProbeResult()
    }

    fun isModelDownloaded(modelId: String): Boolean {
        val spec = CactusModelCatalog.findById(modelId)
        val exact = _modelStates.value[spec?.id ?: modelId]?.isDownloaded
        if (exact != null) return exact
        return false
    }

    private fun buildHfRequest(url: String): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Agentra-Android/4.0")
        val token = _config.value.huggingFaceToken.trim()
        if (token.isNotBlank() && url.contains("huggingface.co", ignoreCase = true)) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder.build()
    }

    private fun formatHfHttpError(code: Int, target: String): String {
        return when (code) {
            401, 403 -> "HTTP $code for $target — Repo gated/restricted ho sakta hai. Optional HF Token (`hf_...`) dalein."
            404 -> "HTTP 404 — $target abhi available nahi hai."
            429 -> "HTTP 429 rate limit. Kuch seconds baad retry karein."
            else -> "HTTP $code for $target"
        }
    }

    private fun resolveAndroidTargetFolder(): String {
        val abis = Build.SUPPORTED_ABIS.map { it.lowercase() }
        return when {
            abis.any { it.contains("arm64") } -> "android-arm64"
            abis.any { it.contains("riscv64") } -> "android-riscv64"
            abis.any { it.contains("armeabi") || it.contains("armv7") } -> "android-armv7"
            else -> "android-arm64"
        }
    }

    private fun resolveCandidateReleaseVersions(): List<String> {
        val discovered = mutableListOf<String>()
        runCatching {
            val req = buildHfRequest("https://huggingface.co/api/models/${CactusModelCatalog.RUNTIME_HF_REPO}/tree/main/releases")
            downloadClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val arr = JSONArray(resp.body?.string().orEmpty())
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        if (item.optString("type") == "directory") {
                            val rel = item.optString("path").substringAfterLast('/').trim()
                            if (rel.isNotBlank()) discovered.add(rel)
                        }
                    }
                }
            }
        }
        val sorted = discovered.sortedDescending().toMutableList()
        if (CactusModelCatalog.DEFAULT_RELEASE_VERSION !in sorted) {
            sorted.add(CactusModelCatalog.DEFAULT_RELEASE_VERSION)
        }
        return sorted.distinct()
    }

    private fun fetchHuggingFaceRepoFiles(repoId: String): List<Pair<String, Long>> {
        val apiUrl = "https://huggingface.co/api/models/$repoId/tree/main?recursive=true"
        val req = buildHfRequest(apiUrl)
        downloadClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException(formatHfHttpError(resp.code, repoId))
            }
            val arr = JSONArray(resp.body?.string().orEmpty())
            val results = mutableListOf<Pair<String, Long>>()
            val allAllowed = WEIGHT_EXTENSIONS + ARCHIVE_EXTENSIONS + METADATA_EXTENSIONS
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                if (item.optString("type") != "file") continue
                val path = item.optString("path").trim()
                if (path.isBlank() || path.startsWith(".")) continue
                val fileName = path.substringAfterLast('/')
                if (fileName.equals("README.md", ignoreCase = true) || fileName.equals("LICENSE", ignoreCase = true)) continue
                val ext = fileName.substringAfterLast('.', "").lowercase()
                if (ext in allAllowed || fileName.endsWith("config", ignoreCase = true)) {
                    val size = item.optLong("size", item.optJSONObject("lfs")?.optLong("size", 0L) ?: 0L)
                    results.add(path to size)
                }
            }
            return results
        }
    }

    private fun selectOptimalMobileFiles(files: List<Pair<String, Long>>): List<Pair<String, Long>> {
        val cactFiles = files.filter { it.first.substringAfterLast('.', "").equals("cact", ignoreCase = true) }
        val ggufFiles = files.filter { it.first.substringAfterLast('.', "").equals("gguf", ignoreCase = true) }
        val zipFiles = files.filter { it.first.substringAfterLast('.', "").equals("zip", ignoreCase = true) }

        val metadataFiles = files.filter { (path, size) ->
            val ext = path.substringAfterLast('.', "").lowercase()
            ext in METADATA_EXTENSIONS && size < 30L * 1024L * 1024L
        }

        if (cactFiles.isNotEmpty()) {
            val bestCact = cactFiles.minByOrNull { (path, size) ->
                val l = path.lowercase()
                val score = when {
                    l.contains("int4") || l.contains("q4") || l.contains("cq4") -> 0
                    l.contains("cq2") || l.contains("2bit") -> 1
                    l.contains("int8") || l.contains("q8") -> 2
                    else -> 3
                }
                score * 1_000_000_000L + (size / 1024L)
            }
            if (bestCact != null) return (listOf(bestCact) + metadataFiles).distinctBy { it.first }
        }

        if (ggufFiles.isNotEmpty()) {
            val bestGguf = ggufFiles.minByOrNull { (path, size) ->
                val l = path.lowercase()
                val score = when {
                    l.contains("q4_k_m") || l.contains("q4_0") || l.contains("int4") -> 0
                    l.contains("q5") || l.contains("q6") -> 1
                    else -> 2
                }
                score * 1_000_000_000L + (size / 1024L)
            }
            if (bestGguf != null) return (listOf(bestGguf) + metadataFiles).distinctBy { it.first }
        }

        if (zipFiles.isNotEmpty()) {
            val bestZip = zipFiles.minByOrNull { (path, size) ->
                val l = path.lowercase()
                val score = when {
                    l.contains("int4") || l.contains("q4") || l.contains("android") -> 0
                    else -> 1
                }
                score * 1_000_000_000L + (size / 1024L)
            }
            if (bestZip != null) return (listOf(bestZip) + metadataFiles).distinctBy { it.first }
        }

        return files
    }

    private fun extractZipSafely(zipFile: File, destDir: File) {
        val destCanonical = destDir.canonicalPath
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            val buf = ByteArray(32 * 1024)
            while (true) {
                val entry = zis.nextEntry ?: break
                val outFile = File(destDir, entry.name)
                if (!outFile.canonicalPath.startsWith(destCanonical + File.separator) && outFile.canonicalPath != destCanonical) {
                    zis.closeEntry()
                    continue
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        while (true) {
                            val n = zis.read(buf)
                            if (n <= 0) break
                            fos.write(buf, 0, n)
                        }
                    }
                }
                zis.closeEntry()
            }
        }
    }

    /**
     * Flattens single nested directories created inside `.zip` archives so `config.json` and
     * model tensors always sit directly under `filesDir/models/<sdkSlug>/` as `CactusLM` requires.
     */
    internal fun flattenSingleSubdirIfNeeded(targetDir: File) {
        val visible = targetDir.listFiles { f -> !f.name.startsWith(".") }.orEmpty()
        if (visible.size == 1 && visible[0].isDirectory) {
            val nested = visible[0]
            nested.listFiles().orEmpty().forEach { child ->
                val dest = File(targetDir, child.name)
                if (!dest.exists()) {
                    if (!child.renameTo(dest)) {
                        child.copyRecursively(dest, overwrite = true)
                        child.deleteRecursively()
                    }
                }
            }
            runCatching { nested.deleteRecursively() }
        }
    }

    private fun cleanupPartFiles(dir: File) {
        if (!dir.exists()) return
        runCatching {
            dir.walkTopDown().forEach { f ->
                if (f.isFile && f.name.endsWith(".part")) {
                    f.delete()
                }
            }
        }
    }

    private fun ensureExecutable(file: File) {
        runCatching {
            file.setReadable(true, false)
            file.setExecutable(true, false)
            Os.chmod(file.absolutePath, 493)
        }
    }

    private fun configureCactusEnvironment(appCtx: Context, env: MutableMap<String, String>) {
        runCatching { LocalEnvironmentManager.applyIsolatedToolEnvironment(appCtx, env) }
        env["HOME"] = getCactusRootDir(appCtx).absolutePath
        env["CACTUS_HOME"] = getCactusRootDir(appCtx).absolutePath
        env["TMPDIR"] = appCtx.cacheDir.absolutePath
        val libDir = File(getCactusRootDir(appCtx), "lib").absolutePath
        val existingLd = env["LD_LIBRARY_PATH"].orEmpty()
        env["LD_LIBRARY_PATH"] = listOf(libDir, appCtx.applicationInfo.nativeLibraryDir, existingLd)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(":")
    }

    private fun updateModelState(modelId: String, transform: (CactusModelInstallState) -> CactusModelInstallState) {
        val next = _modelStates.value.toMutableMap()
        val cur = next[modelId] ?: CactusModelInstallState(modelId = modelId)
        next[modelId] = transform(cur)
        _modelStates.value = next
    }

    private fun isModelDirectoryPopulated(dir: File, diskBytes: Long): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        val hasWeightFile = dir.walkTopDown().any { f ->
            f.isFile &&
                !f.name.endsWith(".part") &&
                f.extension.lowercase() in WEIGHT_EXTENSIONS &&
                f.length() > 100_000L
        }
        if (hasWeightFile) return true
        if (File(dir, ".cactus_ready").exists() && diskBytes > 500_000L) return true
        // SDK `.zip` bundles expand into `config.json` + tensor shards without `.cact`/`.gguf` extensions
        return File(dir, "config.json").exists() && diskBytes > 500_000L
    }

    private fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var sum = 0L
        dir.walkTopDown().forEach { f ->
            if (f.isFile && !f.name.endsWith(".part")) {
                sum += f.length()
            }
        }
        return sum
    }

    private fun formatSizeMb(bytes: Long): String {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format(java.util.Locale.US, "%.1f MB", mb)
    }

    private fun queryAvailableRamMb(context: Context): Long {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            mi.availMem / (1024L * 1024L)
        } catch (_: Exception) {
            0L
        }
    }

    private fun extractPort(url: String, defaultPort: Int): Int {
        return try {
            val p = URI(url).port
            if (p in 1024..65535) p else defaultPort
        } catch (_: Exception) {
            defaultPort
        }
    }
}
