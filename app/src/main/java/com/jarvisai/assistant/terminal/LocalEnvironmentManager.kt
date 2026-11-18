package com.jarvisai.assistant.terminal

import android.content.Context
import android.os.Environment
import android.system.Os
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile as CommonsZipFile
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

sealed class RootfsInstallProgress {
    object Idle : RootfsInstallProgress()
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val fraction: Float
    ) : RootfsInstallProgress() {
        val speed: String
            get() = "${LocalEnvironmentManager.formatFileSize(bytesDownloaded)} / ${LocalEnvironmentManager.formatFileSize(totalBytes)}"
    }
    data class Extracting(
        val currentFile: String,
        val filesExtracted: Int,
        val totalEstimate: Int,
        val fraction: Float
    ) : RootfsInstallProgress()
    data class Configuring(val statusMessage: String) : RootfsInstallProgress() {
        val status: String get() = statusMessage
    }
    data class Success(val message: String, val diagnostics: EnvironmentDiagnostics) : RootfsInstallProgress()
    data class Error(val errorMessage: String) : RootfsInstallProgress() {
        val error: String get() = errorMessage
    }
}

data class EnvironmentDiagnostics(
    val isInstalled: Boolean,
    val defaultShell: String,
    val hasBash: Boolean,
    val hasNode: Boolean,
    val hasPython: Boolean,
    val hasGit: Boolean,
    val hasNvim: Boolean,
    val diskUsageFormatted: String,
    val prefixPath: String,
    val hasPRoot: Boolean = false,
    val hasAptPkg: Boolean = false,
    val hasAdb: Boolean = false,
    val prootBinaryPath: String? = null,
    val runtimeMode: LinuxRuntimeMode = LinuxRuntimeMode.PROOT_TERMUX,
    val isProotVirtualActive: Boolean = false,
    val hasUbuntuRootfs: Boolean = false
)

object LocalEnvironmentManager {
    private const val TAG = "LocalEnvManager"
    private const val HARDCODED_ROOTFS_URL =
        "https://github.com/termux/termux-packages/releases/download/bootstrap-2024.10.04-r1%2Bapt-android-7/bootstrap-aarch64.zip"

    // Persistent application-level scope immune to UI composition dismissal
    private val installerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeInstallJob: Job? = null

    private val _installProgress = MutableStateFlow<RootfsInstallProgress>(RootfsInstallProgress.Idle)
    val installProgress: StateFlow<RootfsInstallProgress> = _installProgress.asStateFlow()

    private val _liveExtractionLogs = MutableStateFlow<List<String>>(emptyList())
    val liveExtractionLogs: StateFlow<List<String>> = _liveExtractionLogs.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .dns(com.jarvisai.assistant.ui.updater.AppUpdateManager.sharedResilientDns)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private const val PREFS_ENV_CACHE = "jarvis_local_env_cache"
    private const val KEY_CACHED_ROOTFS_BYTES = "cached_rootfs_bytes"

    @Volatile
    private var cachedPrefixSize: Long = -1L

    private fun getCachedDiskUsage(context: Context): Long {
        val sp = context.getSharedPreferences(PREFS_ENV_CACHE, Context.MODE_PRIVATE)
        return sp.getLong(KEY_CACHED_ROOTFS_BYTES, -1L)
    }

    private fun saveCachedDiskUsage(context: Context, bytes: Long) {
        val sp = context.getSharedPreferences(PREFS_ENV_CACHE, Context.MODE_PRIVATE)
        sp.edit().putLong(KEY_CACHED_ROOTFS_BYTES, bytes).apply()
    }

    fun isTermuxFlavor(context: Context? = null): Boolean {
        return com.example.BuildConfig.IS_TERMUX_FLAVOR ||
            com.example.BuildConfig.IS_NATIVE_TERMUX_PATH ||
            context?.packageName == "com.termux"
    }

    fun getPrefixDir(context: Context): File {
        val path = if (isTermuxFlavor(context)) {
            "/data/data/com.termux/files/usr"
        } else {
            "${context.filesDir.absolutePath}/usr"
        }
        return File(path).apply { runCatching { mkdirs() } }
    }

    fun getBinDir(context: Context): File = File(getPrefixDir(context), "bin").apply { runCatching { mkdirs() } }

    fun getHomeDir(context: Context): File {
        val path = if (isTermuxFlavor(context)) {
            "/data/data/com.termux/files/home"
        } else {
            "${context.filesDir.absolutePath}/home"
        }
        return File(path).apply { runCatching { mkdirs() } }
    }

    fun getTmpDir(context: Context): File = File(getPrefixDir(context), "tmp").apply { runCatching { mkdirs() } }

    /**
     * Returns Jarvis AI's isolated workspace root (`$HOME/jarvis-workspace`) so Jarvis files
     * never mix with AntiGem's default `$HOME/projects` folder.
     */
    fun getIsolatedWorkspaceDir(context: Context): File {
        return File(getHomeDir(context), com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_WORKSPACE_DIR_NAME).apply {
            runCatching { mkdirs() }
        }
    }

    /**
     * Returns Jarvis AI's isolated projects directory (`$HOME/jarvis-workspace/projects`).
     */
    fun getIsolatedProjectsDir(context: Context): File {
        return File(getIsolatedWorkspaceDir(context), "projects").apply {
            runCatching { mkdirs() }
        }
    }

    fun getHubBaseUrl(): String {
        return com.jarvisai.assistant.data.pc.PcConnect.getActiveHubBaseUrl()
            ?: com.jarvisai.assistant.data.remote.JarvisHubConfig.getLanguageServiceUrl()
    }

    fun isEnvironmentInstalled(context: Context): Boolean {
        if (isTermuxFlavor(context)) {
            val termuxBash = File("/data/data/com.termux/files/usr/bin/bash")
            val termuxSh = File("/data/data/com.termux/files/usr/bin/sh")
            if (termuxBash.exists() || termuxSh.exists()) {
                return true
            }
        }
        val bash = File(getBinDir(context), "bash")
        val sh = File(getBinDir(context), "sh")
        return (bash.exists() && bash.canExecute()) || (sh.exists() && sh.canExecute())
    }

    fun hasPrefixShell(context: Context): Boolean {
        val binDir = getBinDir(context)
        val bash = File(binDir, "bash")
        val sh = File(binDir, "sh")
        return (bash.exists() && bash.canExecute()) || (sh.exists() && sh.canExecute())
    }

    fun hasExtractedFiles(context: Context): Boolean {
        val binDir = getBinDir(context)
        val bash = File(binDir, "bash")
        return bash.exists() || (binDir.list()?.isNotEmpty() == true)
    }

    fun getDefaultShell(context: Context): String {
        val bash = File(getBinDir(context), "bash")
        if (bash.exists() && bash.canExecute()) return bash.absolutePath
        val sh = File(getBinDir(context), "sh")
        if (sh.exists() && sh.canExecute()) return sh.absolutePath
        return "/system/bin/sh"
    }

    /**
     * Resolves a guaranteed writable working directory for subprocess execution.
     * On standard (non-Termux UID) builds, automatically remaps inaccessible `/data/data/com.termux/files/...`
     * paths to the app's local Linux environment (`context.filesDir/home/...`) so `chdir` never fails with EACCES (error=13).
     */
    fun resolveWritableWorkingDir(context: Context, requestedDir: String?): File {
        val localHome = getHomeDir(context)
        val isolatedProjects = getIsolatedProjectsDir(context)
        val raw = requestedDir?.trim().orEmpty()

        val mappedPath = when {
            raw.isBlank() -> isolatedProjects.absolutePath
            raw.startsWith("/data/data/com.termux/files/home") && !isTermuxFlavor(context) -> {
                val probe = File(raw)
                if (probe.exists() && probe.isDirectory && probe.canWrite()) {
                    raw
                } else {
                    localHome.absolutePath + raw.removePrefix("/data/data/com.termux/files/home")
                }
            }
            raw.startsWith("/data/data/com.termux/files/usr") && !isTermuxFlavor(context) -> {
                val probe = File(raw)
                if (probe.exists() && probe.isDirectory && probe.canWrite()) {
                    raw
                } else {
                    getPrefixDir(context).absolutePath + raw.removePrefix("/data/data/com.termux/files/usr")
                }
            }
            else -> raw
        }

        val candidate = File(mappedPath)
        runCatching {
            if (!candidate.exists()) candidate.mkdirs()
        }
        if (candidate.exists() && candidate.isDirectory && candidate.canWrite()) {
            return candidate
        }

        runCatching {
            if (!isolatedProjects.exists()) isolatedProjects.mkdirs()
        }
        if (isolatedProjects.exists() && isolatedProjects.isDirectory && isolatedProjects.canWrite()) {
            return isolatedProjects
        }

        runCatching {
            if (!localHome.exists()) localHome.mkdirs()
        }
        if (localHome.exists() && localHome.isDirectory && localHome.canWrite()) {
            return localHome
        }
        return context.filesDir
    }

    /**
     * Returns ordered shell binary candidates, falling back safely to `/system/bin/sh` if rootfs bash is blocked by SELinux W^X.
     */
    fun getShellCandidates(context: Context): List<String> {
        val binDir = getBinDir(context)
        return listOf(
            File(binDir, "bash").absolutePath,
            File(binDir, "sh").absolutePath,
            "/system/bin/sh"
        ).filter { path ->
            val f = File(path)
            f.exists() && (f.canExecute() || path.startsWith(context.filesDir.absolutePath))
        }.ifEmpty { listOf("/system/bin/sh") }.distinct()
    }

    /**
     * Locates an installed CLI binary (`claude`, `opencode`, `codex`, `node`, `adb`, etc.)
     * inside the isolated Jarvis sandbox, Ubuntu RootFS, or truly executable Termux path.
     */
    fun findCliBinary(context: Context, binaryName: String): File? {
        val homeDir = getHomeDir(context)
        val binDir = getBinDir(context)
        val ubuntuRootfs = PRootRuntime.getUbuntuRootfsDir(context)
        val candidates = buildList {
            add(File(context.filesDir, "bin/$binaryName"))
            add(File(binDir, binaryName))
            add(File(homeDir, ".local/bin/$binaryName"))
            add(File(homeDir, ".npm-global/bin/$binaryName"))
            if (PRootRuntime.isUbuntuRootfsInstalled(context)) {
                add(File(ubuntuRootfs, "usr/local/bin/$binaryName"))
                add(File(ubuntuRootfs, "usr/bin/$binaryName"))
            }
            if (isTermuxFlavor(context)) {
                add(File("/data/data/com.termux/files/usr/bin/$binaryName"))
                add(File("/data/data/com.termux/files/home/.local/bin/$binaryName"))
                add(File("/data/data/com.termux/files/home/.npm-global/bin/$binaryName"))
            }
        }
        for (candidate in candidates) {
            if (candidate.exists() && candidate.isFile) {
                if (!candidate.canExecute() && candidate.absolutePath.startsWith(context.filesDir.absolutePath)) {
                    runCatching {
                        candidate.setExecutable(true, false)
                        Os.chmod(candidate.absolutePath, 493)
                    }
                }
                if (candidate.canExecute()) return candidate
            }
        }
        return null
    }

    fun findLocalBootstrapArchive(): File? {
        return runCatching {
            val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val jarvisDlDir = File(dlDir, com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_SDCARD_FOLDER)
            val candidates = listOf(
                File(jarvisDlDir, "bootrapz_2026-10-04.zip"),
                File(jarvisDlDir, "bootstrap-aarch64.zip"),
                File("/sdcard/Download/JarvisAI/bootrapz_2026-10-04.zip"),
                File("/sdcard/Download/JarvisAI/bootstrap-aarch64.zip"),
                File("/sdcard/Download/bootrapz_2026-10-04.zip"),
                File(dlDir, "bootrapz_2026-10-04.zip"),
                File("/sdcard/Download/bootstrap-aarch64.zip")
            )
            for (f in candidates) {
                if (f.exists() && f.canRead() && f.length() > 50 * 1024 * 1024) {
                    return@runCatching f
                }
            }
            null
        }.getOrNull()
    }

    /**
     * Ultra-fast diagnostic check (<2ms). Never blocks the UI thread with directory traversals.
     */
    fun getDiagnostics(context: Context): EnvironmentDiagnostics {
        val binDir = getBinDir(context)
        val prefix = getPrefixDir(context)

        val bash = File(binDir, "bash").let { it.exists() && it.canExecute() }
        val node = File(binDir, "node").let { it.exists() && it.canExecute() }
        val python = (File(binDir, "python").let { it.exists() && it.canExecute() }) ||
            (File(binDir, "python3").let { it.exists() && it.canExecute() })
        val git = File(binDir, "git").let { it.exists() && it.canExecute() }
        val nvim = File(binDir, "nvim").let { it.exists() && it.canExecute() }
        val aptPkg = File(binDir, "pkg").exists() || File(binDir, "apt").exists() || File(binDir, "dpkg").exists()
        val adbInstalled = File(binDir, "adb").let { it.exists() && it.canExecute() } ||
            File(prefix, "opt/android-sdk/platform-tools/adb").let { it.exists() && it.canExecute() }
        val prootBin = PRootRuntime.resolveProotBinary(context)
        val runtimeMode = PRootRuntime.getRuntimeMode(context)
        val hasUbuntu = PRootRuntime.isUbuntuRootfsInstalled(context)
        val prootActive = PRootRuntime.shouldUsePRoot(context)

        val isInstalled = isEnvironmentInstalled(context) || (prootActive && hasUbuntu)

        var sizeBytes = 0L
        if (isInstalled || hasExtractedFiles(context)) {
            if (cachedPrefixSize > 0L) {
                sizeBytes = cachedPrefixSize
            } else {
                val stored = getCachedDiskUsage(context)
                if (stored > 0L) {
                    cachedPrefixSize = stored
                    sizeBytes = stored
                } else {
                    // Default to standard known RootFS size (2.1 GB) immediately to avoid freeze.
                    // Background job will measure the exact byte count asynchronously.
                    sizeBytes = 2_150_000_000L
                    cachedPrefixSize = sizeBytes
                    val appCtx = context.applicationContext
                    installerScope.launch {
                        val exact = getDirectorySize(prefix)
                        if (exact > 0L) {
                            cachedPrefixSize = exact
                            saveCachedDiskUsage(appCtx, exact)
                        }
                    }
                }
            }
        }

        return EnvironmentDiagnostics(
            isInstalled = isInstalled,
            defaultShell = getDefaultShell(context),
            hasBash = bash,
            hasNode = node,
            hasPython = python,
            hasGit = git,
            hasNvim = nvim,
            diskUsageFormatted = formatFileSize(sizeBytes),
            prefixPath = prefix.absolutePath,
            hasPRoot = prootBin != null,
            hasAptPkg = aptPkg,
            hasAdb = adbInstalled,
            prootBinaryPath = prootBin?.absolutePath,
            runtimeMode = runtimeMode,
            isProotVirtualActive = prootActive,
            hasUbuntuRootfs = hasUbuntu
        )
    }

    fun invalidateSizeCache(context: Context? = null) {
        cachedPrefixSize = -1L
        if (context != null) {
            saveCachedDiskUsage(context, -1L)
        }
    }

    /**
     * Launch installation in persistent background installerScope.
     * Continues running seamlessly even if Dialog leaves UI composition.
     */
    fun startInstall(context: Context, zipFile: File? = null): Job {
        activeInstallJob?.cancel()
        val appContext = context.applicationContext
        return installerScope.launch {
            installBootstrapInternal(appContext, zipFile)
        }.also { activeInstallJob = it }
    }

    /**
     * Instant 0.5s permission repair for existing files.
     */
    suspend fun repairPermissions(context: Context): Result<EnvironmentDiagnostics> = withContext(Dispatchers.IO) {
        val prefix = getPrefixDir(context)
        appendLog("Repairing permissions on ${prefix.absolutePath}...")
        fixFilePermissions(prefix)
        val diag = getDiagnostics(context)
        _installProgress.value = RootfsInstallProgress.Success(
            message = "Permissions repaired! Environment is active.",
            diagnostics = diag
        )
        appendLog("Repair Complete! Bash=${diag.hasBash}, Node=${diag.hasNode}, Python=${diag.hasPython}")
        Result.success(diag)
    }

    private suspend fun installBootstrapInternal(context: Context, zipFile: File? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val prefix = getPrefixDir(context)
            val home = getHomeDir(context)
            val tmp = getTmpDir(context)

            prefix.mkdirs()
            home.mkdirs()
            tmp.mkdirs()

            _liveExtractionLogs.value = listOf("Initializing directories...", "Target prefix: ${prefix.absolutePath}")

            val targetZip = zipFile ?: run {
                val local = findLocalBootstrapArchive()
                if (local != null) {
                    appendLog("Found local offline archive: ${local.absolutePath} (${formatFileSize(local.length())})")
                    local
                } else {
                    downloadBootstrapZip(context)
                }
            }

            appendLog("Extracting archive: ${targetZip.name}...")
            extractZipPreservingPermissions(targetZip, prefix)

            _installProgress.value = RootfsInstallProgress.Configuring("Fixing file permissions and executable flags...")
            appendLog("Setting executable permissions (chmod 755) & DNS config...")
            fixFilePermissions(prefix)
            ensureDnsAndHostsConfigured(prefix)

            // Option A: Provision PRoot runtime cleanly if missing (non-fatal when offline)
            if (!isTermuxFlavor(context) && PRootRuntime.resolveProotBinary(context) == null) {
                _installProgress.value = RootfsInstallProgress.Configuring("Setting up PRoot virtualization engine...")
                appendLog("Checking PRoot hybrid runtime...")
                runCatching {
                    PRootInstaller.installProotRuntime(context) { line -> appendLog(line) }
                }.onFailure { err ->
                    appendLog("PRoot offline notice (Direct Shell fallback active): ${err.message}")
                }
            }

            val exactSize = getDirectorySize(prefix)
            cachedPrefixSize = exactSize
            saveCachedDiskUsage(context, exactSize)

            val diag = getDiagnostics(context)
            _installProgress.value = RootfsInstallProgress.Success(
                message = "Termux Linux environment installed successfully!",
                diagnostics = diag
            )
            appendLog("Setup Complete! Verified: Bash=${diag.hasBash}, Node=${diag.hasNode}, Python=${diag.hasPython}, Git=${diag.hasGit}, PRoot=${diag.hasPRoot}")
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) {
                appendLog("Installation cancelled.")
                throw e
            }
            Log.e(TAG, "Bootstrap installation failed", e)
            _installProgress.value = RootfsInstallProgress.Error("Installation failed: ${e.message}")
            appendLog("ERROR: ${e.message}")
            Result.failure(e)
        }
    }

    private fun appendLog(msg: String) {
        val current = _liveExtractionLogs.value.toMutableList()
        current.add(msg)
        if (current.size > 200) current.removeAt(0)
        _liveExtractionLogs.value = current
    }

    private suspend fun downloadBootstrapZip(context: Context): File = withContext(Dispatchers.IO) {
        val destFile = File(context.cacheDir, "bootstrap-download.zip")
        if (destFile.exists() && destFile.length() > 50 * 1024 * 1024) return@withContext destFile

        appendLog("Connecting to GitHub release server...")
        val request = Request.Builder().url(HARDCODED_ROOTFS_URL).build()
        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Failed to download rootfs: HTTP ${response.code}")
        }

        val body = response.body ?: throw Exception("Empty response body")
        val totalLength = body.contentLength()
        appendLog("Downloading bootstrap archive (${formatFileSize(totalLength)})...")

        _installProgress.value = RootfsInstallProgress.Downloading(0L, totalLength, 0f)

        body.byteStream().use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(64 * 1024)
                var bytesCopied = 0L
                var read: Int
                var lastProgressUpdate = 0L

                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesCopied += read

                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 300) {
                        lastProgressUpdate = now
                        val fraction = if (totalLength > 0) bytesCopied.toFloat() / totalLength else 0f
                        _installProgress.value = RootfsInstallProgress.Downloading(bytesCopied, totalLength, fraction)
                    }
                }
            }
        }
        destFile
    }

    @Suppress("DEPRECATION")
    internal suspend fun extractZipPreservingPermissions(zipFile: File, destDir: File) = withContext(Dispatchers.IO) {
        val commonsZip = CommonsZipFile(zipFile)
        val entries = commonsZip.entries
        var count = 0
        val totalEstimate = 25600
        var lastLogTime = 0L
        var symlinksTxtContent: String? = null
        val canonicalDest = destDir.canonicalFile
        val canonicalSandboxRoot = (destDir.parentFile ?: destDir).canonicalFile

        while (entries.hasMoreElements()) {
            val entry = entries.nextElement() as ZipArchiveEntry
            count++
            val normalizedEntry = entry.name.removePrefix("./").trimStart('/')
            if (normalizedEntry.isBlank() || normalizedEntry.contains("..")) continue

            if (!entry.isDirectory && (normalizedEntry == "SYMLINKS.txt" || normalizedEntry == "usr/SYMLINKS.txt")) {
                symlinksTxtContent = commonsZip.getInputStream(entry).bufferedReader().use { it.readText() }
                continue
            }

            val isHomeEntry = normalizedEntry == "home" || normalizedEntry.startsWith("home/")
            val rawName = if (isHomeEntry) normalizedEntry else normalizedEntry.removePrefix("usr/")
            if (rawName.isBlank()) continue

            val outputFile = if (isHomeEntry) {
                File(canonicalSandboxRoot, rawName)
            } else {
                File(canonicalDest, rawName)
            }

            // Zip-slip guard
            val allowedRoot = if (isHomeEntry) canonicalSandboxRoot else canonicalDest
            if (!outputFile.canonicalPath.startsWith(allowedRoot.canonicalPath)) continue

            if (entry.isUnixSymlink) {
                val linkTarget = commonsZip.getUnixSymlink(entry)
                PRootRuntime.createSymlinkCompat(linkTarget, outputFile)
            } else if (entry.isDirectory) {
                outputFile.mkdirs()
            } else {
                outputFile.parentFile?.mkdirs()
                commonsZip.getInputStream(entry).use { inStream ->
                    outputFile.outputStream().use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                val mode = entry.unixMode
                if (mode and 0b001_000_000 != 0 || rawName.startsWith("bin/") || rawName.contains(".local/bin/") || rawName.contains(".npm-global/bin/")) {
                    PRootRuntime.ensureExecutable(outputFile)
                }
            }

            val now = System.currentTimeMillis()
            if (now - lastLogTime > 250) {
                lastLogTime = now
                val frac = (count.toFloat() / totalEstimate).coerceIn(0f, 0.95f)
                _installProgress.value = RootfsInstallProgress.Extracting(
                    currentFile = rawName,
                    filesExtracted = count,
                    totalEstimate = totalEstimate,
                    fraction = frac
                )
                appendLog("Unpacking [$count]: $rawName")
            }
        }
        commonsZip.close()

        // Apply Termux bootstrap SYMLINKS.txt AFTER all target binaries & shared libraries exist
        if (!symlinksTxtContent.isNullOrBlank()) {
            val links = PRootRuntime.parseTermuxSymlinksTxt(symlinksTxtContent!!)
            appendLog("Applying ${links.size} bootstrap symlinks from SYMLINKS.txt...")
            links.forEach { (target, relLink) ->
                val cleanRel = relLink.removePrefix("./").trimStart('/').removePrefix("usr/")
                if (cleanRel.isNotBlank() && !cleanRel.contains("..")) {
                    val linkFile = File(canonicalDest, cleanRel)
                    if (linkFile.canonicalPath.startsWith(canonicalDest.canonicalPath)) {
                        PRootRuntime.createSymlinkCompat(target, linkFile)
                    }
                }
            }
        }
    }

    fun fixFilePermissions(prefixDir: File) {
        val homeDir = File(prefixDir.parentFile, "home")
        val targetDirs = listOf(
            File(prefixDir, "bin"),
            File(prefixDir, "libexec"),
            File(prefixDir, "libexec/git-core"),
            File(prefixDir, "lib/node_modules/npm/bin"),
            File(prefixDir, "home/.local/bin"),
            File(homeDir, ".local/bin"),
            File(homeDir, ".npm-global/bin")
        )

        for (dir in targetDirs) {
            if (!dir.exists()) continue
            dir.walkTopDown().forEach { file ->
                if (file.isFile && !Files.isSymbolicLink(file.toPath())) {
                    PRootRuntime.ensureExecutable(file)
                }
            }
        }
        ensureGitAndTlsConfigured(prefixDir, homeDir)
        ensureDnsAndHostsConfigured(prefixDir)
        ensureNodeAndNpmWrappers(prefixDir, homeDir)
    }

    /**
     * Generates portable `#!/system/bin/sh` wrappers for `npm`, `npx`, and installed global
     * Node CLIs (`claude`, `opencode`, `codex`) so they execute without `exit 126` (EACCES)
     * in BOTH PRoot mode and Direct Native Sandbox mode—even when external `com.termux`
     * owns `/data/data/com.termux` (`0700`) on the device.
     */
    fun ensureNodeAndNpmWrappers(prefixDir: File, homeDir: File) {
        runCatching {
            val hostPrefixPath = prefixDir.absolutePath
            val nodeBin = File(prefixDir, "bin/node")
            if (!nodeBin.exists()) return@runCatching
            PRootRuntime.ensureExecutable(nodeBin)

            val tlsDir = File(prefixDir, "etc/tls").apply { mkdirs() }
            val opensslCnf = File(tlsDir, "openssl.cnf")
            if (!opensslCnf.exists() || opensslCnf.length() == 0L) {
                opensslCnf.writeText("openssl_conf = openssl_init\n\n[openssl_init]\n")
            }
            val certPem = File(tlsDir, "cert.pem")

            val localBinDir = File(homeDir, ".local/bin").apply { mkdirs() }
            val npmGlobalBinDir = File(homeDir, ".npm-global/bin").apply { mkdirs() }

            val envExports = buildString {
                appendLine("export OPENSSL_CONF=\"$hostPrefixPath/etc/tls/openssl.cnf\"")
                appendLine("export OPENSSL_MODULES=\"$hostPrefixPath/lib/ossl-modules\"")
                if (certPem.exists()) {
                    appendLine("export SSL_CERT_FILE=\"$hostPrefixPath/etc/tls/cert.pem\"")
                    appendLine("export CURL_CA_BUNDLE=\"$hostPrefixPath/etc/tls/cert.pem\"")
                    appendLine("export NODE_EXTRA_CA_CERTS=\"$hostPrefixPath/etc/tls/cert.pem\"")
                }
                appendLine("export LD_LIBRARY_PATH=\"$hostPrefixPath/lib:\${LD_LIBRARY_PATH:-}\"")
            }

            val npmCliJs = File(prefixDir, "lib/node_modules/npm/bin/npm-cli.js")
            if (npmCliJs.exists()) {
                PRootRuntime.ensureExecutable(npmCliJs)
                val npmWrapperScript = buildString {
                    appendLine("#!/system/bin/sh")
                    append(envExports)
                    appendLine("if [ -x \"$hostPrefixPath/bin/node\" ]; then")
                    appendLine("  exec \"$hostPrefixPath/bin/node\" \"$hostPrefixPath/lib/node_modules/npm/bin/npm-cli.js\" \"\$@\"")
                    appendLine("else")
                    appendLine("  exec \"${PRootRuntime.VIRTUAL_TERMUX_PREFIX}/bin/node\" \"${PRootRuntime.VIRTUAL_TERMUX_PREFIX}/lib/node_modules/npm/bin/npm-cli.js\" \"\$@\"")
                    appendLine("fi")
                }
                val localNpm = File(localBinDir, "npm")
                localNpm.writeText(npmWrapperScript)
                PRootRuntime.ensureExecutable(localNpm)
            }

            val npxCliJs = File(prefixDir, "lib/node_modules/npm/bin/npx-cli.js")
            if (npxCliJs.exists()) {
                PRootRuntime.ensureExecutable(npxCliJs)
                val npxWrapperScript = buildString {
                    appendLine("#!/system/bin/sh")
                    append(envExports)
                    appendLine("if [ -x \"$hostPrefixPath/bin/node\" ]; then")
                    appendLine("  exec \"$hostPrefixPath/bin/node\" \"$hostPrefixPath/lib/node_modules/npm/bin/npx-cli.js\" \"\$@\"")
                    appendLine("else")
                    appendLine("  exec \"${PRootRuntime.VIRTUAL_TERMUX_PREFIX}/bin/node\" \"${PRootRuntime.VIRTUAL_TERMUX_PREFIX}/lib/node_modules/npm/bin/npx-cli.js\" \"\$@\"")
                    appendLine("fi")
                }
                val localNpx = File(localBinDir, "npx")
                localNpx.writeText(npxWrapperScript)
                PRootRuntime.ensureExecutable(localNpx)
            }

            // Also wrap any installed global npm CLI scripts (claude, opencode, codex)
            listOf("claude", "opencode", "codex").forEach { cliName ->
                val npmBinFile = File(npmGlobalBinDir, cliName)
                if (npmBinFile.exists()) {
                    val resolvedTarget = runCatching { npmBinFile.canonicalFile }.getOrDefault(npmBinFile)
                    if (resolvedTarget.exists() && resolvedTarget.isFile) {
                        PRootRuntime.ensureExecutable(resolvedTarget)
                        val targetHostPath = resolvedTarget.absolutePath
                        val targetGuestPath = PRootRuntime.mapHostToTermuxGuestPath(targetHostPath, prefixDir, homeDir)
                        val cliWrapper = buildString {
                            appendLine("#!/system/bin/sh")
                            append(envExports)
                            appendLine("if [ -x \"$hostPrefixPath/bin/node\" ] && [ -f \"$targetHostPath\" ]; then")
                            appendLine("  exec \"$hostPrefixPath/bin/node\" \"$targetHostPath\" \"\$@\"")
                            appendLine("else")
                            appendLine("  exec \"${PRootRuntime.VIRTUAL_TERMUX_PREFIX}/bin/node\" \"$targetGuestPath\" \"\$@\"")
                            appendLine("fi")
                        }
                        val localCli = File(localBinDir, cliName)
                        localCli.writeText(cliWrapper)
                        PRootRuntime.ensureExecutable(localCli)
                    }
                }
            }
        }
    }

    fun ensureDnsAndHostsConfigured(prefixDir: File) {
        runCatching {
            val etcDir = File(prefixDir, "etc").apply { mkdirs() }
            val resolvFile = File(etcDir, "resolv.conf")
            if (!resolvFile.exists() || resolvFile.length() == 0L) {
                resolvFile.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\nnameserver 1.0.0.1\n")
            }
            val hostsFile = File(etcDir, "hosts")
            if (!hostsFile.exists() || hostsFile.length() == 0L) {
                hostsFile.writeText("127.0.0.1 localhost\n::1 localhost\n")
            }
        }
    }

    fun ensureGitAndTlsConfigured(prefixDir: File, homeDir: File) {
        runCatching {
            homeDir.mkdirs()
            val tlsDir = File(prefixDir, "etc/tls").apply { mkdirs() }
            val opensslCnf = File(tlsDir, "openssl.cnf")
            if (!opensslCnf.exists() || opensslCnf.length() == 0L) {
                opensslCnf.writeText("openssl_conf = openssl_init\n\n[openssl_init]\n")
            }
            val certFile = File(tlsDir, "cert.pem")
            val gitConfigFile = File(homeDir, ".gitconfig")
            val existing = if (gitConfigFile.exists()) gitConfigFile.readText() else ""
            val sb = StringBuilder(existing)
            if (certFile.exists() && !existing.contains("sslCAInfo")) {
                if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append('\n')
                sb.appendLine("[http]")
                sb.appendLine("\tsslCAInfo = ${certFile.absolutePath}")
            }
            if (!existing.contains("directory = *")) {
                if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append('\n')
                sb.appendLine("[safe]")
                sb.appendLine("\tdirectory = *")
            }
            val updated = sb.toString()
            if (updated != existing && updated.isNotBlank()) {
                gitConfigFile.writeText(updated)
            }

            // Ensure prefix/etc/gitconfig exists in sandbox for tools that read $PREFIX/etc/gitconfig
            val etcDir = File(prefixDir, "etc").apply { mkdirs() }
            val prefixGitConfig = File(etcDir, "gitconfig")
            if (!prefixGitConfig.exists() && certFile.exists()) {
                prefixGitConfig.writeText("[http]\n\tsslCAInfo = ${certFile.absolutePath}\n[safe]\n\tdirectory = *\n")
            }
        }
    }

    fun applyIsolatedToolEnvironment(context: Context, env: MutableMap<String, String>) {
        val prefixDir = getPrefixDir(context)
        val prefix = prefixDir.absolutePath
        val homeDir = getHomeDir(context)
        val home = homeDir.absolutePath
        val tmp = getTmpDir(context).absolutePath

        ensureGitAndTlsConfigured(prefixDir, homeDir)
        ensureDnsAndHostsConfigured(prefixDir)
        ensureNodeAndNpmWrappers(prefixDir, homeDir)
        runCatching {
            ensureMcpStdioBridgeScript(context)
            ensureJarvisMcpCliScript(context)
        }

        val appBin = File(context.filesDir, "bin").apply { mkdirs() }.absolutePath
        env["TZ"] = com.jarvisai.assistant.util.IndiaTimeUtils.IST_ZONE_ID
        env["TERM"] = "xterm-256color"
        env["COLORTERM"] = "truecolor"
        env["HOME"] = home
        env["PREFIX"] = prefix
        env["TMPDIR"] = tmp
        env["LD_LIBRARY_PATH"] = "$prefix/lib:$home/.local/lib"
        env["PATH"] = "$appBin:$home/.local/bin:$home/.npm-global/bin:$prefix/bin:$prefix/bin/applets:/system/bin:/system/xbin"
        env["npm_config_prefix"] = "$home/.npm-global"

        // CRITICAL: Override OpenSSL 3 compile-time /data/data/com.termux/files/usr/etc/tls/openssl.cnf
        // so Node/Python/Git/curl never fail with EACCES (exit 13) when external com.termux is installed!
        val opensslCnf = File(prefixDir, "etc/tls/openssl.cnf")
        if (opensslCnf.exists()) {
            env["OPENSSL_CONF"] = opensslCnf.absolutePath
        }
        val osslModules = File(prefixDir, "lib/ossl-modules")
        if (osslModules.exists()) {
            env["OPENSSL_MODULES"] = osslModules.absolutePath
        }

        val certFile = File(prefixDir, "etc/tls/cert.pem")
        if (certFile.exists()) {
            env["SSL_CERT_FILE"] = certFile.absolutePath
            env["CURL_CA_BUNDLE"] = certFile.absolutePath
            env["GIT_SSL_CAINFO"] = certFile.absolutePath
            env["NODE_EXTRA_CA_CERTS"] = certFile.absolutePath
        }

        // Override Termux compile-time /data/data/com.termux/files/usr hardcoded Git paths
        env["GIT_CONFIG_NOSYSTEM"] = "1"
        env["GIT_CONFIG_GLOBAL"] = File(homeDir, ".gitconfig").absolutePath
        val gitExecDir = File(prefixDir, "libexec/git-core")
        if (gitExecDir.exists()) {
            env["GIT_EXEC_PATH"] = gitExecDir.absolutePath
        }
        val gitTemplateDir = File(prefixDir, "share/git-core/templates")
        if (gitTemplateDir.exists()) {
            env["GIT_TEMPLATE_DIR"] = gitTemplateDir.absolutePath
        }
    }

    /**
     * Provisions an ultra-reliable POSIX shell stdio-to-HTTP MCP bridge script (`jarvis-mcp-stdio`)
     * that works with `curl` or `/system/bin/toybox nc` so any stdio MCP client (including local `agy --hub`)
     * can communicate with JarvisEmbeddedMcp (:2236 / :2237) with zero external npm/node dependencies.
     */
    fun ensureMcpStdioBridgeScript(context: Context): File {
        val prefixBin = File(getPrefixDir(context), "bin").absolutePath
        val scriptContent = """
            #!/system/bin/sh
            ENDPOINT="${'$'}{1:-http://127.0.0.1:2236/mcp}"
            PORT=${'$'}(printf "%s" "${'$'}ENDPOINT" | sed -E 's|https?://[^:/]+:([0-9]+).*|\1|')
            [ -z "${'$'}PORT" ] && PORT="2236"
            PATH_PART=${'$'}(printf "%s" "${'$'}ENDPOINT" | sed -E 's|https?://[^/]+(/.*)|\1|')
            [ -z "${'$'}PATH_PART" ] && PATH_PART="/mcp"
            export PATH="$prefixBin:/data/data/com.termux/files/usr/bin:/system/bin:/system/xbin:${'$'}{PATH:-}"

            post_json() {
              payload="${'$'}1"
              if command -v curl >/dev/null 2>&1; then
                curl -s -S --max-time 25 -X POST -H "Content-Type: application/json" --data-raw "${'$'}payload" "${'$'}ENDPOINT" 2>/dev/null
              elif [ -x /system/bin/toybox ]; then
                len=${'$'}(printf "%s" "${'$'}payload" | wc -c | tr -d ' ')
                printf "POST %s HTTP/1.0\r\nHost: 127.0.0.1:%s\r\nContent-Type: application/json\r\nContent-Length: %s\r\nConnection: close\r\n\r\n%s" "${'$'}PATH_PART" "${'$'}PORT" "${'$'}len" "${'$'}payload" | /system/bin/toybox nc -w 20 127.0.0.1 "${'$'}PORT" 2>/dev/null | sed '1,/^\r*${'$'}/d'
              elif command -v nc >/dev/null 2>&1; then
                len=${'$'}(printf "%s" "${'$'}payload" | wc -c | tr -d ' ')
                printf "POST %s HTTP/1.0\r\nHost: 127.0.0.1:%s\r\nContent-Type: application/json\r\nContent-Length: %s\r\nConnection: close\r\n\r\n%s" "${'$'}PATH_PART" "${'$'}PORT" "${'$'}len" "${'$'}payload" | nc -w 20 127.0.0.1 "${'$'}PORT" 2>/dev/null | sed '1,/^\r*${'$'}/d'
              fi
            }

            while IFS= read -r line || [ -n "${'$'}line" ]; do
              trimmed=${'$'}(printf "%s" "${'$'}line" | tr -d '\r')
              [ -z "${'$'}trimmed" ] && continue
              resp=${'$'}(post_json "${'$'}trimmed")
              if [ -n "${'$'}resp" ]; then
                printf "%s\n" "${'$'}resp"
              fi
            done
        """.trimIndent() + "\n"

        val targets = listOf(
            File(File(context.filesDir, "bin").apply { mkdirs() }, "jarvis-mcp-stdio"),
            File(File(getHomeDir(context), ".local/bin").apply { mkdirs() }, "jarvis-mcp-stdio"),
            File(getBinDir(context), "jarvis-mcp-stdio")
        )
        var primary = targets.first()
        targets.forEach { f ->
            runCatching {
                f.parentFile?.mkdirs()
                if (!f.exists() || f.readText() != scriptContent) {
                    f.writeText(scriptContent)
                }
                f.setExecutable(true, false)
                f.setReadable(true, false)
                runCatching { Os.chmod(f.absolutePath, 493) }
                if (f.exists() && f.canExecute()) primary = f
            }
        }
        return primary
    }

    /**
     * Provisions the `jarvis-mcp` CLI utility in `$PATH` so Antigravity / terminal sessions can
     * inspect active MCP servers, invoke any MCP tool, or create new MCP servers, plugins, and skills directly.
     */
    fun ensureJarvisMcpCliScript(context: Context): File {
        val prefixBin = File(getPrefixDir(context), "bin").absolutePath
        val cliContent = """
            #!/system/bin/sh
            ENDPOINT="http://127.0.0.1:2236/mcp"
            export PATH="$prefixBin:/data/data/com.termux/files/usr/bin:/system/bin:/system/xbin:${'$'}{PATH:-}"

            rpc_call() {
              tool_name="${'$'}1"
              tool_args="${'$'}{2:-{}}"
              req="{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"${'$'}tool_name\",\"arguments\":${'$'}tool_args}}"
              if command -v curl >/dev/null 2>&1; then
                curl -s -S --max-time 25 -X POST -H "Content-Type: application/json" --data-raw "${'$'}req" "${'$'}ENDPOINT"
              elif [ -x /system/bin/toybox ]; then
                len=${'$'}(printf "%s" "${'$'}req" | wc -c | tr -d ' ')
                printf "POST /mcp HTTP/1.0\r\nHost: 127.0.0.1:2236\r\nContent-Type: application/json\r\nContent-Length: %s\r\nConnection: close\r\n\r\n%s" "${'$'}len" "${'$'}req" | /system/bin/toybox nc -w 20 127.0.0.1 2236 | sed '1,/^\r*${'$'}/d'
              fi
              printf "\n"
            }

            cmd="${'$'}{1:-status}"
            case "${'$'}cmd" in
              status|list|list-servers)
                rpc_call "mcp_list_servers" "{}"
                ;;
              call)
                srv="${'$'}2"
                tl="${'$'}3"
                args="${'$'}{4:-{}}"
                rpc_call "mcp_call_tool" "{\"server_name\":\"${'$'}srv\",\"tool_name\":\"${'$'}tl\",\"arguments\":${'$'}args}"
                ;;
              create-server)
                rpc_call "mcp_create_server" "${'$'}{2:-{}}"
                ;;
              remove-server)
                rpc_call "mcp_remove_server" "{\"server_name\":\"${'$'}2\"}"
                ;;
              skills|list-skills)
                rpc_call "skill_list_all" "{}"
                ;;
              create-skill)
                rpc_call "skill_create_or_update" "${'$'}{2:-{}}"
                ;;
              plugins|list-plugins)
                rpc_call "plugin_list_all" "{}"
                ;;
              create-plugin)
                rpc_call "plugin_create_or_update" "${'$'}{2:-{}}"
                ;;
              *)
                echo "Jarvis MCP & Ecosystem CLI"
                echo "Usage:"
                echo "  jarvis-mcp status                                      # List all active MCP servers & tools"
                echo "  jarvis-mcp call <server> <tool> '<json_args>'          # Call any tool on an MCP server"
                echo "  jarvis-mcp create-server '<json_spec>'                 # Create & hot-reload a new MCP server"
                echo "  jarvis-mcp skills                                      # List all active Agent Skills"
                echo "  jarvis-mcp create-skill '<json_spec>'                  # Create & hot-reload a SKILL.md"
                echo "  jarvis-mcp plugins                                     # List all Plugins & Browser Extensions"
                echo "  jarvis-mcp create-plugin '<json_spec>'                 # Create & hot-reload a Plugin/Extension"
                ;;
            esac
        """.trimIndent() + "\n"

        val targets = listOf(
            File(File(context.filesDir, "bin").apply { mkdirs() }, "jarvis-mcp"),
            File(File(getHomeDir(context), ".local/bin").apply { mkdirs() }, "jarvis-mcp"),
            File(getBinDir(context), "jarvis-mcp")
        )
        var primary = targets.first()
        targets.forEach { f ->
            runCatching {
                f.parentFile?.mkdirs()
                if (!f.exists() || f.readText() != cliContent) {
                    f.writeText(cliContent)
                }
                f.setExecutable(true, false)
                f.setReadable(true, false)
                runCatching { Os.chmod(f.absolutePath, 493) }
                if (f.exists() && f.canExecute()) primary = f
            }
        }
        return primary
    }

    private fun getDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
        return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }
}
