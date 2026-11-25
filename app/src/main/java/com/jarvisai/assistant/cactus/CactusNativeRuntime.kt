package com.jarvisai.assistant.cactus

import android.app.ActivityManager
import android.content.Context
import android.os.Build
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
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Native runtime manager, Hugging Face model downloader, and local OpenAI-compatible
 * server supervisor (`cactus serve` + `cactus run` + JNI probe) for Cactus Compute.
 */
object CactusNativeRuntime {
    private const val TAG = "CactusNativeRuntime"
    private const val PREFS_NAME = "agentra_cactus_runtime_prefs"
    private const val MAX_LOGS = 200

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
        val candidates = mutableListOf(
            getLocalBinaryFile(appCtx),
            File(LocalEnvironmentManager.getPrefixDir(appCtx), "bin/cactus"),
            File(LocalEnvironmentManager.getHomeDir(appCtx), ".local/bin/cactus"),
            File("/data/data/com.termux/files/usr/bin/cactus")
        )
        return candidates.firstOrNull { f -> f.exists() && f.isFile && f.length() > 10_000L }
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
            binFile != null -> "Cactus Native Binary ready (${binFile.name}). Tap 'Start Local Server' or select a downloaded model."
            jniReady -> "Cactus Native JNI library detected."
            else -> "Install Cactus ARM64 engine or connect an external `cactus serve` endpoint."
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
     * Downloads the official prebuilt `android-arm64` Cactus native runner binary from Hugging Face
     * into `filesDir/cactus/bin/cactus` and marks it executable (`chmod 755`).
     */
    suspend fun installCactusNativeBinary(context: Context): Result<File> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        if (_status.value.isInstallingBinary) {
            return@withContext Result.failure(IllegalStateException("Cactus binary install already running."))
        }
        _status.value = _status.value.copy(
            isInstallingBinary = true,
            binaryDownloadProgress = 0.02f,
            lastStatusMessage = "Downloading official Cactus ARM64 native engine..."
        )
        appendLog("[INSTALL] Fetching ${CactusModelCatalog.ANDROID_ARM64_BINARY_URL}")

        try {
            val targetFile = getLocalBinaryFile(appCtx)
            val tmpFile = File(targetFile.parentFile, "cactus.download.tmp")
            if (tmpFile.exists()) tmpFile.delete()

            val req = Request.Builder()
                .url(CactusModelCatalog.ANDROID_ARM64_BINARY_URL)
                .header("User-Agent", "Agentra-Android/4.0")
                .build()

            downloadClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Download failed (HTTP ${resp.code}). You can also install via Linux Terminal: `pip install cactus-compute`.")
                }
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
            }

            if (tmpFile.length() < 50_000L) {
                tmpFile.delete()
                throw IllegalStateException("Downloaded Cactus binary appears incomplete. Try again on Wi-Fi.")
            }
            if (targetFile.exists()) targetFile.delete()
            tmpFile.renameTo(targetFile)
            targetFile.setExecutable(true, false)
            targetFile.setReadable(true, false)

            appendLog("[INSTALL] Installed Cactus binary (${targetFile.length() / 1024} KB) at ${targetFile.absolutePath}")
            _status.value = _status.value.copy(
                isInstallingBinary = false,
                binaryDownloadProgress = 1f,
                binaryInstalled = true,
                binaryPath = targetFile.absolutePath,
                lastStatusMessage = "Cactus ARM64 Engine installed (${targetFile.length() / (1024 * 1024)} MB)."
            )
            refreshStatus(appCtx)
            Result.success(targetFile)
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
     * Downloads real quantized model files from Hugging Face `Cactus-Compute/<repo>` into app storage.
     */
    fun startModelDownload(context: Context, spec: CactusModelSpec) {
        val appCtx = context.applicationContext
        if (downloadJobs[spec.id]?.isActive == true) return

        val job = runtimeScope.launch {
            updateModelState(spec.id) {
                it.copy(isDownloading = true, downloadProgress = 0.01f, lastError = null)
            }
            appendLog("[MODEL] Querying Hugging Face tree for ${spec.huggingFaceRepo}...")

            try {
                val filesToDownload = fetchHuggingFaceRepoFiles(spec.huggingFaceRepo)
                if (filesToDownload.isEmpty()) {
                    throw IllegalStateException("No downloadable model files found in ${spec.huggingFaceRepo}.")
                }

                val targetDir = getModelDir(appCtx, spec).apply { mkdirs() }
                val totalBytes = filesToDownload.sumOf { it.second }.coerceAtLeast(1L)
                var cumulativeDownloaded = 0L

                for ((relPath, expectedSize) in filesToDownload) {
                    if (!isActive) throw CancellationException("Download cancelled")
                    val outFile = File(targetDir, relPath)
                    outFile.parentFile?.mkdirs()

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
                    val fileUrl = "https://huggingface.co/${spec.huggingFaceRepo}/resolve/main/$encodedPath"
                    val tmpFile = File(outFile.parentFile, "${outFile.name}.part")
                    val req = Request.Builder()
                        .url(fileUrl)
                        .header("User-Agent", "Agentra-Android/4.0")
                        .build()

                    downloadClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            throw IllegalStateException("Failed downloading $relPath (HTTP ${resp.code})")
                        }
                        val body = resp.body ?: throw IllegalStateException("Empty body for $relPath")
                        val buf = ByteArray(64 * 1024)
                        var fileBytes = 0L
                        body.byteStream().use { input ->
                            FileOutputStream(tmpFile).use { out ->
                                while (true) {
                                    if (!isActive) throw CancellationException("Download cancelled")
                                    val read = input.read(buf)
                                    if (read <= 0) break
                                    out.write(buf, 0, read)
                                    fileBytes += read
                                    val nowTotal = cumulativeDownloaded + fileBytes
                                    updateModelState(spec.id) {
                                        it.copy(
                                            downloadedBytes = nowTotal,
                                            totalBytes = maxOf(totalBytes, nowTotal),
                                            downloadProgress = (nowTotal.toFloat() / maxOf(totalBytes, nowTotal).toFloat()).coerceIn(0.02f, 0.99f)
                                        )
                                    }
                                }
                            }
                        }
                        cumulativeDownloaded += fileBytes
                    }

                    if (outFile.exists()) outFile.delete()
                    tmpFile.renameTo(outFile)
                }

                // Write ready marker
                File(targetDir, ".cactus_ready").writeText("repo=${spec.huggingFaceRepo}\nts=${System.currentTimeMillis()}\n")
                val diskBytes = calculateDirectorySize(targetDir)
                updateModelState(spec.id) {
                    it.copy(
                        isDownloading = false,
                        isDownloaded = true,
                        downloadProgress = 1f,
                        localDirPath = targetDir.absolutePath,
                        sizeOnDiskBytes = diskBytes,
                        lastError = null
                    )
                }
                appendLog("[MODEL] Downloaded ${spec.displayName} (${diskBytes / (1024 * 1024)} MB)")
                refreshStatus(appCtx)
            } catch (ce: CancellationException) {
                appendLog("[MODEL] Cancelled download for ${spec.displayName}")
                updateModelState(spec.id) {
                    it.copy(isDownloading = false, downloadProgress = 0f, lastError = "Download cancelled")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model download failed for ${spec.id}", e)
                appendLog("[ERROR] ${spec.displayName} download failed: ${e.message}")
                updateModelState(spec.id) {
                    it.copy(isDownloading = false, lastError = e.message ?: "Download failed")
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
            it.copy(isDownloaded = false, isDownloading = false, downloadProgress = 0f, sizeOnDiskBytes = 0L, lastError = null)
        }
        appendLog("[MODEL] Deleted ${spec.displayName} to free storage.")
        refreshStatus(context)
    }

    /**
     * Starts the supervised local `cactus serve` OpenAI-compatible HTTP server on the configured loopback port.
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
                // Maybe an external server (Termux / PC) is already running on the configured URL!
                val probe = probeServerEndpoint(cfg.serverBaseUrl)
                if (probe.first) {
                    refreshStatus(appCtx)
                    return@withContext Result.success("Connected to external Cactus server at ${cfg.serverBaseUrl}")
                }
                val err = "Cactus engine binary not installed yet. Tap 'Install Cactus Engine' first."
                _status.value = _status.value.copy(lastStatusMessage = err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            stopLocalServerLocked()

            val port = extractPort(cfg.serverBaseUrl, defaultPort = 8090)
            val localModelDir = getModelDir(appCtx, chosenSpec)
            val modelArg = if (isModelDirectoryPopulated(localModelDir, calculateDirectorySize(localModelDir))) {
                localModelDir.absolutePath
            } else {
                chosenSpec.huggingFaceRepo
            }

            val cmd = mutableListOf(
                binFile.absolutePath,
                "serve",
                modelArg,
                "--port",
                port.toString()
            )
            if (cfg.computeBackend != CactusComputeBackend.AUTO) {
                cmd.addAll(listOf("--backend", cfg.computeBackend.cliFlag))
            }

            appendLog("[SERVER] Starting: ${cmd.joinToString(" ")}")
            _status.value = _status.value.copy(lastStatusMessage = "Starting Cactus server on :$port (${chosenSpec.displayName})...")

            try {
                val pb = ProcessBuilder(cmd)
                    .directory(getCactusRootDir(appCtx))
                    .redirectErrorStream(true)
                val env = pb.environment()
                env["HOME"] = getCactusRootDir(appCtx).absolutePath
                env["CACTUS_HOME"] = getCactusRootDir(appCtx).absolutePath
                env["TMPDIR"] = appCtx.cacheDir.absolutePath
                val libDir = File(getCactusRootDir(appCtx), "lib").absolutePath
                val existingLd = env["LD_LIBRARY_PATH"].orEmpty()
                env["LD_LIBRARY_PATH"] = listOf(libDir, appCtx.applicationInfo.nativeLibraryDir, existingLd)
                    .filter { it.isNotBlank() }
                    .joinToString(":")

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

                // Wait up to 8 seconds for loopback OpenAI API to become reachable
                val targetUrl = "http://127.0.0.1:$port"
                var online = false
                for (attempt in 0 until 16) {
                    delay(500L)
                    if (!proc.isAlive) {
                        val code = runCatching { proc.exitValue() }.getOrDefault(-1)
                        throw IllegalStateException("Cactus process exited (code $code). Check Cactus logs or run `cactus serve` in Linux Terminal.")
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
     * Executes a single one-shot native `cactus run` CLI command (great for on-demand VLM `--image`
     * or STT `--audio` or offline chat when the background HTTP daemon is not running).
     */
    suspend fun executeNativeCli(
        context: Context,
        args: List<String>,
        timeoutSeconds: Long = 60L
    ): Result<String> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val binFile = resolveCactusBinaryPath(appCtx)
            ?: return@withContext Result.failure(IllegalStateException("Cactus binary not installed."))

        try {
            val fullCmd = listOf(binFile.absolutePath) + args
            appendLog("[CLI] ${fullCmd.joinToString(" ")}")
            val pb = ProcessBuilder(fullCmd)
                .directory(getCactusRootDir(appCtx))
                .redirectErrorStream(true)
            pb.environment()["HOME"] = getCactusRootDir(appCtx).absolutePath
            pb.environment()["CACTUS_HOME"] = getCactusRootDir(appCtx).absolutePath
            pb.environment()["TMPDIR"] = appCtx.cacheDir.absolutePath

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

    private fun fetchHuggingFaceRepoFiles(repoId: String): List<Pair<String, Long>> {
        val apiUrl = "https://huggingface.co/api/models/$repoId/tree/main?recursive=true"
        val req = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", "Agentra-Android/4.0")
            .build()
        downloadClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Hugging Face API returned HTTP ${resp.code} for $repoId")
            }
            val arr = JSONArray(resp.body?.string().orEmpty())
            val results = mutableListOf<Pair<String, Long>>()
            val allowedExt = setOf("gguf", "bin", "cactus", "safetensors", "json", "txt", "model", "tiktoken", "vocab", "merges")
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                if (item.optString("type") != "file") continue
                val path = item.optString("path").trim()
                if (path.isBlank() || path.startsWith(".")) continue
                val ext = path.substringAfterLast('.', "").lowercase()
                if (ext in allowedExt || path.endsWith("config", ignoreCase = true)) {
                    val size = item.optLong("size", item.optJSONObject("lfs")?.optLong("size", 0L) ?: 0L)
                    results.add(path to size)
                }
            }
            return results
        }
    }

    private fun updateModelState(modelId: String, transform: (CactusModelInstallState) -> CactusModelInstallState) {
        val next = _modelStates.value.toMutableMap()
        val cur = next[modelId] ?: CactusModelInstallState(modelId = modelId)
        next[modelId] = transform(cur)
        _modelStates.value = next
    }

    private fun isModelDirectoryPopulated(dir: File, diskBytes: Long): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        if (File(dir, ".cactus_ready").exists() && diskBytes > 100_000L) return true
        return diskBytes > 5L * 1024L * 1024L
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
