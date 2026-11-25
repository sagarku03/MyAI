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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
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
 * Native runtime manager, Hugging Face model downloader, and local OpenAI-compatible
 * server supervisor (`cactus serve` + `cactus run` + JNI probe) for Cactus Compute.
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
        .readTimeout(120, TimeUnit.SECONDS)
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
        File(getModelsDir(context), spec.slug)

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

    /**
     * Loads persisted preferences and scans installed binaries, models, RAM, and server health.
     */
    fun initialize(context: Context) {
        val appCtx = context.applicationContext
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
            activeLlmModelId = prefs.getString("active_llm_model", CactusModelCatalog.DEFAULT_LLM_ID)
                ?: CactusModelCatalog.DEFAULT_LLM_ID,
            activeVlmModelId = prefs.getString("active_vlm_model", CactusModelCatalog.DEFAULT_VLM_ID)
                ?: CactusModelCatalog.DEFAULT_VLM_ID,
            activeSttModelId = prefs.getString("active_stt_model", CactusModelCatalog.DEFAULT_STT_ID)
                ?: CactusModelCatalog.DEFAULT_STT_ID,
            activeEmbeddingModelId = prefs.getString("active_embedding_model", CactusModelCatalog.DEFAULT_EMBEDDING_ID)
                ?: CactusModelCatalog.DEFAULT_EMBEDDING_ID,
            computeBackend = backend,
            hybridCloudFallbackEnabled = prefs.getBoolean("hybrid_cloud_fallback", true),
            offlineSttEnabled = prefs.getBoolean("offline_stt_enabled", false),
            memoryVectorRagEnabled = prefs.getBoolean("memory_vector_rag", true),
            privateOfflineVisionDefault = prefs.getBoolean("private_offline_vision_default", false),
            privateOfflineDocsDefault = prefs.getBoolean("private_offline_docs_default", false),
            fastCactusScreenControlEnabled = prefs.getBoolean("fast_cactus_screen_control", true),
            huggingFaceToken = prefs.getString("hf_token", "")?.trim().orEmpty(),
            maxContextTokens = prefs.getInt("max_context_tokens", 4096),
            temperature = prefs.getFloat("temperature", 0.6f)
        )
        _config.value = loaded
        return loaded
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
     * Fast non-blocking check indicating whether a local Cactus server or binary is available
     * for low-latency screen control without stalling on network probes.
     */
    fun isFastLocalInferenceReady(context: Context): Boolean {
        val st = _status.value
        if (st.isServerRunning || st.binaryInstalled) return true
        return resolveCactusBinaryPath(context.applicationContext) != null
    }

    /**
     * Resolves an executable `cactus` native binary across:
     * 1. App-private Cactus bin dir (`filesDir/cactus/bin/cactus`)
     * 2. Agentra Embedded Linux Core (`$PREFIX/bin/cactus` or `$HOME/.local/bin/cactus`)
     * 3. External Termux usr/bin (`com.termux/files/usr/bin/cactus`)
     */
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

    /**
     * Resolves the best local model artifact path:
     * 1. Compact `.cact` container (preferring int4 / q4 / cq mobile quantizations if multiple exist)
     * 2. `.gguf` file
     * 3. Inner model directory containing `config.json` (handles zip-extracted nested folders)
     * 4. Model directory itself if populated, otherwise Hugging Face repo slug.
     */
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

        // If a downloaded zip unpacked into a single nested subfolder containing config.json, point directly to it
        val configFile = dir.walkTopDown()
            .maxDepth(3)
            .firstOrNull { it.isFile && it.name.equals("config.json", ignoreCase = true) }
        if (configFile?.parentFile != null && configFile.parentFile != dir) {
            return configFile.parentFile!!.absolutePath
        }

        val diskBytes = calculateDirectorySize(dir)
        return if (isModelDirectoryPopulated(dir, diskBytes)) dir.absolutePath else spec.huggingFaceRepo
    }

    /**
     * Returns the downloaded `.cact` file inside [spec]'s directory, or null if none exists.
     */
    fun findLocalCactFile(context: Context, spec: CactusModelSpec): File? {
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

    /**
     * Finds any already-downloaded Cactus v4 native `.cact` text/tool model (e.g. `needle3.cact`).
     */
    fun findAnyDownloadedNativeCactTextModel(context: Context): Pair<CactusModelSpec, File>? {
        val appCtx = context.applicationContext
        val preferredSpecs = CactusModelCatalog.allModels.filter {
            it.isNativeCactModel &&
                (it.capability == CactusModelCapability.TOOL_CALLING || it.capability == CactusModelCapability.LLM)
        }
        for (spec in preferredSpecs) {
            val f = findLocalCactFile(appCtx, spec)
            if (f != null) return spec to f
        }
        // Fallback: scan all models in case any other downloaded model contains a `.cact`
        for (spec in CactusModelCatalog.chatCapableModels()) {
            val f = findLocalCactFile(appCtx, spec)
            if (f != null) return spec to f
        }
        return null
    }

    /**
     * Returns downloaded `whistle.cact` speech model file if present (can be co-loaded alongside
     * `needle3.cact` via repeated `--model` flag on Cactus v4).
     */
    fun findDownloadedWhistleCact(context: Context): File? {
        val spec = CactusModelCatalog.findById("cactus:whistle") ?: return null
        return findLocalCactFile(context.applicationContext, spec)
    }

    /**
     * Checks whether a quick auto-start attempt can proceed without looping repeatedly on a model
     * that just failed to start.
     */
    fun shouldSkipAutoServerStart(modelId: String): Boolean {
        val elapsed = System.currentTimeMillis() - lastAutoLaunchFailureMs
        return lastAutoLaunchFailureModelId == modelId && elapsed in 0L..20_000L
    }

    /**
     * Probes `cactus --help` once per installed binary path to detect exact CLI syntax
     * (`--model file.cact`, `--serve [--port N]`, `--tools`, `--system`, `--prompt`, `--audio`).
     */
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

    /**
     * Checks if compiled `libcactus.so` / `libcactus_engine.so` JNI library is present in app libs.
     */
    fun isNativeJniLibraryPresent(context: Context): Boolean {
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
        val jniReady = isNativeJniLibraryPresent(appCtx)
        val serverProbe = probeServerEndpoint(cfg.serverBaseUrl)
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val availRamMb = queryAvailableRamMb(appCtx)

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

        val statusMsg = when {
            serverProbe.isOnline -> {
                val styleTag = if (serverProbe.apiStyle == CactusServerApiStyle.CACTUS_NATIVE) "Native v4" else "OpenAI API"
                val modelSuffix = if (serverProbe.modelName.isNotBlank()) " • ${serverProbe.modelName}" else ""
                "Cactus Local Server ONLINE at ${cfg.serverBaseUrl} ($styleTag$modelSuffix)"
            }
            binFile != null -> "Cactus Native Binary ready (${binFile.name}). Tap 'Start Local Server' or download a model."
            jniReady -> "Cactus Native JNI library detected."
            else -> "Tap 'Install Engine' to download the official Cactus binary for $abi, or connect an external `cactus --serve` endpoint."
        }

        val updated = _status.value.copy(
            binaryInstalled = binFile != null,
            binaryPath = binFile?.absolutePath.orEmpty(),
            nativeJniAvailable = jniReady,
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

    /**
     * Downloads the official prebuilt Cactus native runner binary matching the device ABI
     * (`android-arm64`, `android-armv7`, or `android-riscv64`) from `Cactus-Compute/cactus`.
     */
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
                    "Cactus binary download failed ($lastErrorMsg). Internet check karein ya Linux Terminal me `pip install -U cactus-compute` chalayein."
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
     * Downloads real quantized model files from Hugging Face `Cactus-Compute/<repo>` (with automatic
     * fallback repos and `.cact`/`.zip`/`.gguf` support) into app storage.
     */
    fun startModelDownload(context: Context, spec: CactusModelSpec) {
        val appCtx = context.applicationContext
        if (downloadJobs[spec.id]?.isActive == true) return

        val job = runtimeScope.launch {
            updateModelState(spec.id) {
                it.copy(
                    isDownloading = true,
                    downloadProgress = 0.01f,
                    currentFileName = "Fetching repo manifest...",
                    lastError = null
                )
            }

            val candidateRepos = (listOf(spec.huggingFaceRepo) + spec.fallbackHuggingFaceRepos).distinct()
            val targetDir = getModelDir(appCtx, spec).apply { mkdirs() }

            try {
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

                    updateModelState(spec.id) {
                        it.copy(currentFileName = outFile.name)
                    }

                    // Skip already verified files on resume
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
                    val tmpFile = File(outFile.parentFile, "${outFile.name}.part")
                    val req = buildHfRequest(fileUrl)

                    try {
                        downloadClient.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) {
                                throw IllegalStateException(formatHfHttpError(resp.code, "$activeRepo/$relPath"))
                            }
                            val body = resp.body ?: throw IllegalStateException("Empty body for $relPath")
                            val buf = ByteArray(64 * 1024)
                            var fileBytes = 0L
                            var lastUiPushMs = 0L
                            body.byteStream().use { input ->
                                FileOutputStream(tmpFile).use { out ->
                                    while (true) {
                                        if (!isActive) throw CancellationException("Download cancelled")
                                        val read = input.read(buf)
                                        if (read <= 0) break
                                        out.write(buf, 0, read)
                                        fileBytes += read
                                        val nowMs = System.currentTimeMillis()
                                        if (nowMs - lastUiPushMs >= 120L) {
                                            lastUiPushMs = nowMs
                                            val nowTotal = cumulativeDownloaded + fileBytes
                                            val denom = maxOf(totalBytes, nowTotal).coerceAtLeast(1L)
                                            updateModelState(spec.id) {
                                                it.copy(
                                                    downloadedBytes = nowTotal,
                                                    totalBytes = denom,
                                                    downloadProgress = (nowTotal.toFloat() / denom.toFloat()).coerceIn(0.02f, 0.99f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            cumulativeDownloaded += fileBytes
                        }
                    } catch (e: Exception) {
                        runCatching { tmpFile.delete() }
                        throw e
                    }

                    if (outFile.exists()) outFile.delete()
                    tmpFile.renameTo(outFile)

                    // Automatically extract zipped Cactus bundles safely into targetDir
                    if (outFile.extension.equals("zip", ignoreCase = true)) {
                        updateModelState(spec.id) { it.copy(currentFileName = "Extracting ${outFile.name}...") }
                        extractZipSafely(outFile, targetDir)
                        runCatching { outFile.delete() }
                    }
                }

                // Write ready marker
                File(targetDir, ".cactus_ready").writeText("repo=$activeRepo\nts=${System.currentTimeMillis()}\n")
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
                appendLog("[MODEL] Downloaded ${spec.displayName} from $activeRepo (${formatSizeMb(diskBytes)})")
                refreshStatus(appCtx)
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

    fun cancelModelDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)
    }

    suspend fun deleteModel(context: Context, spec: CactusModelSpec) = withContext(Dispatchers.IO) {
        cancelModelDownload(spec.id)
        val dir = getModelDir(context, spec)
        if (dir.exists()) {
            dir.deleteRecursively()
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
        appendLog("[MODEL] Deleted ${spec.displayName} to free storage.")
        refreshStatus(context)
    }

    /**
     * One-tap setup helper: downloads official Cactus v4 `needle3.cact` (~29 MB), selects it as
     * active local model, and immediately boots the local Cactus v4 HTTP server.
     */
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
            // Wait for download job to finish cleanly
            val job = downloadJobs[needleSpec.id]
            job?.join()
            if (findLocalCactFile(appCtx, needleSpec) == null) {
                val err = _modelStates.value[needleSpec.id]?.lastError
                    ?: "Needle3 (.cact) download incomplete. Retry karein."
                return@withContext Result.failure(IllegalStateException(err))
            }
        }

        updateConfig(appCtx) { it.copy(activeLlmModelId = needleSpec.id) }
        startLocalServer(appCtx, needleSpec)
    }

    /**
     * Writes Agentra's local phone tool schemas (`tools.json`) and system context (`system.txt`)
     * for Cactus v4 `--tools` / `--system` flags.
     */
    private fun prepareRuntimeToolsAndSystemFiles(appCtx: Context): Pair<File, File> {
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

    /**
     * Starts the supervised local Cactus HTTP server on the configured loopback port.
     * Enforces the verified Cactus v4.0.0 `.cact` binary contract:
     * `cactus --model file.cact [--model whistle.cact] [--tools tools.json] [--system system.txt] --serve --port N`
     */
    suspend fun startLocalServer(
        context: Context,
        modelSpec: CactusModelSpec? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        serverMutex.withLock {
            val appCtx = context.applicationContext
            val cfg = _config.value
            var chosenSpec = modelSpec ?: CactusModelCatalog.findById(cfg.activeLlmModelId)
                ?: CactusModelCatalog.findById(CactusModelCatalog.DEFAULT_LLM_ID)
                ?: CactusModelCatalog.allModels.first()

            val binFile = resolveCactusBinaryPath(appCtx)
            if (binFile == null) {
                val probe = probeServerEndpoint(cfg.serverBaseUrl)
                if (probe.isOnline) {
                    refreshStatus(appCtx)
                    return@withContext Result.success("Connected to external Cactus server at ${cfg.serverBaseUrl}")
                }
                val err = "Cactus engine binary not installed yet. Tap 'Install Engine' first."
                _status.value = _status.value.copy(lastStatusMessage = err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            ensureExecutable(binFile)
            stopLocalServerLocked()

            val port = extractPort(cfg.serverBaseUrl, defaultPort = 8090)
            val caps = getBinaryCliCapabilities(appCtx)
            var modelArg = resolveModelArtifactPath(appCtx, chosenSpec)
            val isV4CactOnlyBinary = caps.hasDoubleDashServe && caps.hasDoubleDashModel && !caps.hasServeSubcommand

            // Cactus v4.0.0 Android binary ONLY accepts `.cact` files (directories fail with `cannot load ...: unreadable`).
            if (isV4CactOnlyBinary) {
                val cactFile = findLocalCactFile(appCtx, chosenSpec)
                if (cactFile != null) {
                    modelArg = cactFile.absolutePath
                } else {
                    // Auto-recover: if another native `.cact` model (like needle3.cact) IS already downloaded, use it!
                    val fallbackCact = findAnyDownloadedNativeCactTextModel(appCtx)
                    if (fallbackCact != null) {
                        chosenSpec = fallbackCact.first
                        modelArg = fallbackCact.second.absolutePath
                        appendLog("[SERVER] '${modelSpec?.displayName ?: cfg.activeLlmModelId}' has no .cact file; auto-switching to ${chosenSpec.displayName} (${fallbackCact.second.name})")
                    } else {
                        val err = "Cactus v4 Android Engine sirf `.cact` files load karta hai (`${chosenSpec.displayName}` me `.cact` file nahi hai). " +
                            "'Download Needle3 (.cact • 29 MB) & Start' button tap karein!"
                        appendLog("[SERVER] $err (checked path=$modelArg)")
                        _status.value = _status.value.copy(isServerRunning = false, lastStatusMessage = err)
                        lastAutoLaunchFailureMs = System.currentTimeMillis()
                        lastAutoLaunchFailureModelId = chosenSpec.id
                        return@withContext Result.failure(IllegalStateException(err))
                    }
                }
            } else if (!File(modelArg).exists()) {
                val err = "Model '${chosenSpec.displayName}' download nahi hai. Niche catalog se pehle Download karein."
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

            // Build verified launch commands (with optional co-loaded whistle.cact + tools.json, plus clean minimal fallback)
            val candidateCommands = buildList {
                if (caps.hasDoubleDashServe && caps.hasDoubleDashModel) {
                    val whistleCact = findDownloadedWhistleCact(appCtx)
                    val (toolsFile, systemFile) = prepareRuntimeToolsAndSystemFiles(appCtx)

                    // Attempt 1: Full Cactus v4 server with optional co-loaded Whistle STT + tools.json + system.txt
                    val fullCmd = mutableListOf(binFile.absolutePath, "--model", modelArg)
                    if (whistleCact != null && whistleCact.absolutePath != modelArg) {
                        fullCmd.addAll(listOf("--model", whistleCact.absolutePath))
                    }
                    if (caps.hasDoubleDashTools && toolsFile.exists()) {
                        fullCmd.addAll(listOf("--tools", toolsFile.absolutePath))
                    }
                    if (caps.hasDoubleDashSystem && systemFile.exists()) {
                        fullCmd.addAll(listOf("--system", systemFile.absolutePath))
                    }
                    fullCmd.addAll(listOf("--serve", "--port", port.toString()))
                    fullCmd.addAll(extraFlags)
                    add(fullCmd)

                    // Attempt 2: Clean minimal Cactus v4 server (in case tools.json schema or co-loaded model rejected)
                    add(listOf(binFile.absolutePath, "--model", modelArg, "--serve", "--port", port.toString()) + extraFlags)
                } else if (caps.hasDoubleDashServe) {
                    add(listOf(binFile.absolutePath, modelArg, "--serve", "--port", port.toString()) + extraFlags)
                    add(listOf(binFile.absolutePath, "--model", modelArg, "--serve", "--port", port.toString()) + extraFlags)
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
                // If model file itself is unreadable/corrupt, don't retry further
                if (errMsg.contains("unreadable", ignoreCase = true) ||
                    errMsg.contains("cannot load", ignoreCase = true) ||
                    idx == candidateCommands.lastIndex
                ) {
                    break
                }
                appendLog("[SERVER] Retrying minimal Cactus v4 server flags (${idx + 2}/${candidateCommands.size})...")
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
                delay(400L)
                if (!proc.isAlive) {
                    // Brief pause so log reader flushes remaining stderr lines
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
                updateConfig(appCtx) { it.copy(serverBaseUrl = targetUrl, activeLlmModelId = chosenSpec.id) }
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
            " Note: Cactus v4 binary `.cact` models (jaise Needle3 / Mirage / Whistle) sabse reliably run karta hai; `${spec.displayName}` folder format check karein."
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

    /**
     * Executes a single one-shot native `cactus` CLI command (supports optional stdin prompt piping
     * for Cactus v4.0.0 binaries that accept turns on stdin when `--prompt` is not used).
     */
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
     * Probes the configured Cactus HTTP server and identifies whether it speaks:
     * - [CactusServerApiStyle.OPENAI] (`/v1/models` or `/health` from `cactus-mlx server` / OpenAI bridge)
     * - [CactusServerApiStyle.CACTUS_NATIVE] (native `cactus --serve` binary listening on loopback)
     */
    fun probeServerEndpoint(baseUrl: String): CactusServerProbeResult {
        val clean = baseUrl.trim().removeSuffix("/").ifBlank { "http://127.0.0.1:8090" }

        // 1. Check OpenAI `/v1/models`
        try {
            val req = Request.Builder().url("$clean/v1/models").get().build()
            probeClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val modelName = runCatching {
                        val obj = org.json.JSONObject(body)
                        obj.optJSONArray("data")?.optJSONObject(0)?.optString("id").orEmpty()
                    }.getOrDefault("")
                    return CactusServerProbeResult(
                        isOnline = true,
                        modelName = modelName,
                        apiStyle = CactusServerApiStyle.OPENAI
                    )
                } else if (resp.code in 400..499) {
                    // Server IS actively listening on this port, just doesn't expose OpenAI `/v1/models` -> Native v4 `--serve`!
                    return CactusServerProbeResult(
                        isOnline = true,
                        modelName = _status.value.loadedServerModel,
                        apiStyle = CactusServerApiStyle.CACTUS_NATIVE
                    )
                }
            }
        } catch (_: Exception) {
        }

        // 2. Check `/health`
        try {
            val req = Request.Builder().url("$clean/health").get().build()
            probeClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val modelName = runCatching {
                        org.json.JSONObject(body).optString("model").orEmpty()
                    }.getOrDefault("")
                    return CactusServerProbeResult(
                        isOnline = true,
                        modelName = modelName.ifBlank { _status.value.loadedServerModel },
                        apiStyle = CactusServerApiStyle.OPENAI
                    )
                } else if (resp.code in 400..499) {
                    return CactusServerProbeResult(
                        isOnline = true,
                        modelName = _status.value.loadedServerModel,
                        apiStyle = CactusServerApiStyle.CACTUS_NATIVE
                    )
                }
            }
        } catch (_: Exception) {
        }

        // 3. Check root `/` (native Cactus v4 `--serve` returns 200 / 404 / 405 to GET `/`)
        try {
            val req = Request.Builder().url("$clean/").get().build()
            probeClient.newCall(req).execute().use { resp ->
                if (resp.code in 200..499) {
                    return CactusServerProbeResult(
                        isOnline = true,
                        modelName = _status.value.loadedServerModel,
                        apiStyle = CactusServerApiStyle.CACTUS_NATIVE
                    )
                }
            }
        } catch (_: Exception) {
        }

        return CactusServerProbeResult()
    }

    fun isModelDownloaded(modelId: String): Boolean =
        _modelStates.value[modelId]?.isDownloaded == true

    private fun buildHfRequest(url: String): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Agentra-Android/4.0")
        val token = _config.value.huggingFaceToken.trim()
        if (token.isNotBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder.build()
    }

    private fun formatHfHttpError(code: Int, target: String): String {
        return when (code) {
            401, 403 -> "Hugging Face HTTP $code for $target — Repo gated/restricted ho sakta hai. Agar Gemma/gated model hai to upar optional Hugging Face Token (`hf_...`) dalein."
            404 -> "Hugging Face HTTP 404 — $target abhi available nahi hai."
            429 -> "Hugging Face rate limit (HTTP 429). Kuch seconds baad retry karein ya free HF Token dalein."
            else -> "Hugging Face HTTP $code for $target"
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
        // Sort descending so newest release is attempted first, always keeping DEFAULT_RELEASE_VERSION fallback
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

    /**
     * Keeps mobile downloads compact:
     * - If a repo ships `.cact` files (Cactus v4 native containers), pick the single best mobile
     *   quantized `.cact` (preferring int4 / q4 / cq4 / cq2) plus small metadata files instead of
     *   downloading every quantization variant or multi-GB raw safetensors checkpoints.
     * - Same for `.gguf` or `.zip` bundles if no `.cact` is present.
     */
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
            Os.chmod(file.absolutePath, 493) // 0755
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
        return File(dir, ".cactus_ready").exists() && diskBytes > 500_000L
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
