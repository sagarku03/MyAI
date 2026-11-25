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
            .putString("hf_token", updated.huggingFaceToken.trim())
            .putInt("max_context_tokens", updated.maxContextTokens)
            .putFloat("temperature", updated.temperature)
            .apply()
        return updated
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
     * Resolves the best local model artifact path (prefers a `.cact` or `.gguf` file inside the
     * downloaded directory, otherwise the directory itself, otherwise the Hugging Face repo ID).
     */
    fun resolveModelArtifactPath(context: Context, spec: CactusModelSpec): String {
        val dir = getModelDir(context.applicationContext, spec)
        if (!dir.exists() || !dir.isDirectory) return spec.huggingFaceRepo

        val cactFile = dir.walkTopDown().firstOrNull {
            it.isFile && it.extension.equals("cact", ignoreCase = true) && it.length() > 50_000L
        }
        if (cactFile != null) return cactFile.absolutePath

        val ggufFile = dir.walkTopDown().firstOrNull {
            it.isFile && it.extension.equals("gguf", ignoreCase = true) && it.length() > 50_000L
        }
        if (ggufFile != null) return ggufFile.absolutePath

        val diskBytes = calculateDirectorySize(dir)
        return if (isModelDirectoryPopulated(dir, diskBytes)) dir.absolutePath else spec.huggingFaceRepo
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
            serverProbe.first -> "Cactus Local Server ONLINE at ${cfg.serverBaseUrl}" +
                (if (serverProbe.second.isNotBlank()) " (${serverProbe.second})" else "")
            binFile != null -> "Cactus Native Binary ready (${binFile.name}). Tap 'Start Local Server' or download a model."
            jniReady -> "Cactus Native JNI library detected."
            else -> "Tap 'Install Engine' to download the official Cactus binary for $abi, or connect an external `cactus serve` endpoint."
        }

        val updated = _status.value.copy(
            binaryInstalled = binFile != null,
            binaryPath = binFile?.absolutePath.orEmpty(),
            nativeJniAvailable = jniReady,
            isServerRunning = serverProbe.first,
            serverEndpoint = cfg.serverBaseUrl,
            loadedServerModel = serverProbe.second.ifBlank { _status.value.loadedServerModel },
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
     * Starts the supervised local `cactus serve` OpenAI-compatible HTTP server on the configured loopback port.
     * Automatically retries without `--backend` if the installed binary version doesn't accept that flag.
     */
    suspend fun startLocalServer(
        context: Context,
        modelSpec: CactusModelSpec? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        serverMutex.withLock {
            val appCtx = context.applicationContext
            val cfg = _config.value
            val chosenSpec = modelSpec ?: CactusModelCatalog.findById(cfg.activeLlmModelId)
                ?: CactusModelCatalog.allModels.first()

            val binFile = resolveCactusBinaryPath(appCtx)
            if (binFile == null) {
                val probe = probeServerEndpoint(cfg.serverBaseUrl)
                if (probe.first) {
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
            val modelArg = resolveModelArtifactPath(appCtx, chosenSpec)
            val baseCmd = mutableListOf(binFile.absolutePath, "serve", modelArg, "--port", port.toString())
            val cmdWithBackend = if (cfg.computeBackend != CactusComputeBackend.AUTO) {
                (baseCmd + listOf("--backend", cfg.computeBackend.cliFlag))
            } else {
                baseCmd
            }

            val firstAttempt = launchServerProcessLocked(appCtx, cmdWithBackend, port, chosenSpec)
            if (firstAttempt.isSuccess) return@withContext firstAttempt

            // If `--backend` caused an immediate CLI flag rejection, retry cleanly without `--backend`
            if (cfg.computeBackend != CactusComputeBackend.AUTO) {
                appendLog("[SERVER] Retrying `cactus serve` without --backend flag...")
                stopLocalServerLocked()
                return@withContext launchServerProcessLocked(appCtx, baseCmd, port, chosenSpec)
            }
            firstAttempt
        }
    }

    private suspend fun launchServerProcessLocked(
        appCtx: Context,
        cmd: List<String>,
        port: Int,
        chosenSpec: CactusModelSpec
    ): Result<String> {
        appendLog("[SERVER] Starting: ${cmd.joinToString(" ")}")
        _status.value = _status.value.copy(lastStatusMessage = "Starting Cactus server on :$port (${chosenSpec.displayName})...")

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
            var online = false
            for (attempt in 0 until 16) {
                delay(500L)
                if (!proc.isAlive) {
                    val code = runCatching { proc.exitValue() }.getOrDefault(-1)
                    throw IllegalStateException(
                        "Cactus server exited (code $code). Open Logs to inspect details, or run `cactus serve` in Linux Terminal."
                    )
                }
                val probe = probeServerEndpoint(targetUrl)
                if (probe.first) {
                    online = true
                    break
                }
            }

            if (online) {
                updateConfig(appCtx) { it.copy(serverBaseUrl = targetUrl, activeLlmModelId = chosenSpec.id) }
                _status.value = _status.value.copy(
                    isServerRunning = true,
                    serverEndpoint = targetUrl,
                    loadedServerModel = chosenSpec.displayName,
                    lastStatusMessage = "Cactus Server ONLINE ($targetUrl • ${chosenSpec.displayName})"
                )
                Result.success("Cactus Server ONLINE at $targetUrl")
            } else {
                val msg = "Cactus process started on :$port (model loading in background). Tap Probe in a few seconds."
                _status.value = _status.value.copy(lastStatusMessage = msg)
                Result.success(msg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting Cactus server", e)
            stopLocalServerLocked()
            val err = e.message ?: "Failed starting Cactus local server"
            _status.value = _status.value.copy(isServerRunning = false, lastStatusMessage = err)
            Result.failure(e)
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
     * Executes a single one-shot native `cactus` CLI command (great for on-demand VLM `--image`,
     * STT `--audio` / `transcribe`, or offline chat when the background HTTP daemon is not running).
     */
    suspend fun executeNativeCli(
        context: Context,
        args: List<String>,
        timeoutSeconds: Long = 60L
    ): Result<String> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val binFile = resolveCactusBinaryPath(appCtx)
            ?: return@withContext Result.failure(IllegalStateException("Cactus binary not installed."))
        ensureExecutable(binFile)

        try {
            val fullCmd = listOf(binFile.absolutePath) + args
            appendLog("[CLI] ${fullCmd.joinToString(" ")}")
            val pb = ProcessBuilder(fullCmd)
                .directory(getCactusRootDir(appCtx))
                .redirectErrorStream(true)
            configureCactusEnvironment(appCtx, pb.environment())

            val proc = pb.start()
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
            if (proc.exitValue() == 0 && text.isNotBlank()) {
                Result.success(text)
            } else {
                Result.failure(IllegalStateException(text.ifBlank { "Cactus CLI exited with code ${proc.exitValue()}" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Probes `/v1/models` and `/health` on the configured Cactus HTTP server.
     * Returns Pair(isOnline, firstModelIdOrEmpty).
     */
    fun probeServerEndpoint(baseUrl: String): Pair<Boolean, String> {
        val clean = baseUrl.trim().removeSuffix("/").ifBlank { "http://127.0.0.1:8090" }
        val endpoints = listOf("$clean/v1/models", "$clean/health")
        for (url in endpoints) {
            try {
                val req = Request.Builder().url(url).get().build()
                probeClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val modelName = runCatching {
                            val obj = org.json.JSONObject(body)
                            obj.optJSONArray("data")?.optJSONObject(0)?.optString("id").orEmpty()
                        }.getOrDefault("")
                        return true to modelName
                    }
                }
            } catch (_: Exception) {
            }
        }
        return false to ""
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
     * Keeps mobile downloads compact: if a Cactus repo ships a pre-quantized `.cact`, `.gguf`, or `.zip`
     * bundle alongside huge raw `checkpoints/` safetensors files, download the compact bundle + JSON/tokenizer
     * metadata and skip redundant multi-GB raw training checkpoints.
     */
    private fun selectOptimalMobileFiles(files: List<Pair<String, Long>>): List<Pair<String, Long>> {
        val hasCompactBundle = files.any { (path, _) ->
            val ext = path.substringAfterLast('.', "").lowercase()
            ext == "cact" || ext == "gguf" || ext == "zip"
        }
        if (!hasCompactBundle) return files

        val filtered = files.filter { (path, _) ->
            val lower = path.lowercase()
            val ext = lower.substringAfterLast('.', "")
            val isRawCheckpoint = lower.startsWith("checkpoints/") || lower.startsWith("original/")
            !(isRawCheckpoint && ext == "safetensors")
        }
        return filtered.ifEmpty { files }
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
