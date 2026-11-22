package com.jarvisai.assistant.terminal

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.system.Os
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.jarvisai.assistant.data.preferences.AuthPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile as CommonsZipFile
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
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
    val runtimeMode: LinuxRuntimeMode = LinuxRuntimeMode.DIRECT_NATIVE,
    val isProotVirtualActive: Boolean = false,
    val hasUbuntuRootfs: Boolean = false,
    val hasAmApk: Boolean = false,
    val hasGlibc: Boolean = false,
    val hasRelocateHooks: Boolean = false
)

object LocalEnvironmentManager {
    private const val TAG = "LocalEnvManager"
    private const val HARDCODED_ROOTFS_URL =
        "https://github.com/sagarku03/MyAI/releases/download/rootfs/agentra-rootfs.zip"
    private const val FALLBACK_BOOTSTRAP_URL =
        "https://github.com/termux/termux-packages/releases/download/bootstrap-2024.10.04-r1%2Bapt-android-7/bootstrap-aarch64.zip"

    // Persistent application-level scope immune to UI composition dismissal
    private val installerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeInstallJob: Job? = null

    private val _installProgress = MutableStateFlow<RootfsInstallProgress>(RootfsInstallProgress.Idle)
    val installProgress: StateFlow<RootfsInstallProgress> = _installProgress.asStateFlow()

    private val _installerState = MutableStateFlow<LocalInstallerState>(LocalInstallerState.Idle)
    val installerState: StateFlow<LocalInstallerState> = _installerState.asStateFlow()

    private val _liveExtractionLogs = MutableStateFlow<List<String>>(emptyList())
    val liveExtractionLogs: StateFlow<List<String>> = _liveExtractionLogs.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .dns(com.jarvisai.assistant.ui.updater.AppUpdateManager.sharedResilientDns)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private const val PREFS_ENV_CACHE = "jarvis_local_env_cache"
    private const val KEY_CACHED_ROOTFS_BYTES = "cached_rootfs_bytes"
    private const val INSTALLER_NOTIFICATION_CHANNEL_ID = "agentra_rootfs_installer"
    private const val INSTALLER_NOTIFICATION_ID = 2241

    @Volatile
    private var cachedPrefixSize: Long = -1L

    fun getSelectedMirror(context: Context): RootfsMirror {
        return RootfsMirror.fromId(AuthPreferences.getInstance(context).getRootfsMirrorSync())
    }

    fun setSelectedMirror(context: Context, mirror: RootfsMirror) {
        AuthPreferences.getInstance(context).setRootfsMirror(mirror.id)
    }

    fun getTermuxApiProvider(context: Context): String {
        return AuthPreferences.getInstance(context).getTermuxApiProviderSync()
    }

    fun setTermuxApiProvider(context: Context, provider: String) {
        AuthPreferences.getInstance(context).setTermuxApiProvider(provider)
        ensureTermuxApiDispatcher(context)
    }

    fun termuxApiAppPackage(context: Context): String = "${context.packageName}.api"

    fun isTermuxApiAppInstalled(context: Context): Boolean {
        val pm = context.packageManager
        val candidates = listOf(termuxApiAppPackage(context), "com.termux.api")
        return candidates.any { pkg ->
            runCatching {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
        }
    }

    fun isTermuxApiPackageInstalled(context: Context): Boolean {
        val prefix = getPrefixDir(context)
        return File(prefix, "libexec/termux-api").exists() ||
            File(prefix, "libexec/termux-api.distrib").exists() ||
            File(prefix, "bin/termux-toast").exists()
    }

    suspend fun ensureTermuxApiPackage(
        context: Context,
        onLine: (String) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isEnvironmentInstalled(context)) {
            onLine("Rootfs is not installed yet. Install bootstrap first.")
            return@withContext false
        }
        val code = executeCommandLive(
            context = context,
            command = "pkg install -y termux-api || apt-get update && apt-get install -y termux-api",
            timeoutMs = 180_000L,
            onLine = onLine
        )
        ensureTermuxApiDispatcher(context)
        code == 0 || isTermuxApiPackageInstalled(context)
    }

    fun isUnsupported32BitDevice(): Boolean = Build.SUPPORTED_64_BIT_ABIS.isNullOrEmpty()

    fun getBootstrapArch(): String {
        val abis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        return when {
            abis.any { it.contains("arm64") } -> "aarch64"
            abis.any { it.contains("x86_64") } -> "x86_64"
            abis.any { it.contains("x86") } -> "i686"
            else -> "arm"
        }
    }

    fun getDeviceHardwareInfo(): DeviceHardwareInfo {
        val abis64 = Build.SUPPORTED_64_BIT_ABIS?.toList().orEmpty()
        val abis32 = Build.SUPPORTED_32_BIT_ABIS?.toList().orEmpty()
        val primary = Build.SUPPORTED_ABIS?.firstOrNull() ?: "unknown"
        val is64 = abis64.isNotEmpty()
        val kernel = runCatching { Os.uname().release }.getOrDefault("unknown")
        val cpuInfoText = runCatching { File("/proc/cpuinfo").readText() }.getOrDefault("")
        val hasAtomics = cpuInfoText.contains("atomics", ignoreCase = true) ||
            cpuInfoText.contains("lse", ignoreCase = true) ||
            !is64

        val vaBits = runCatching {
            val match = Regex("""address sizes\s*:\s*\d+\s*bits physical,\s*(\d+)\s*bits virtual""", RegexOption.IGNORE_CASE)
                .find(cpuInfoText)
            match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 48
        }.getOrDefault(48)

        val warning = when {
            !is64 -> "This 32-bit-only device (${abis32.joinToString()}) cannot run 64-bit arm64 tools (Node 22/Agy). Core shell utilities remain available."
            vaBits in 1..39 -> "39-bit virtual address space detected (VA=$vaBits). Some jemalloc/Node binaries may need PRoot fallback if segfaults occur."
            else -> null
        }

        return DeviceHardwareInfo(
            primaryAbi = primary,
            supported64Abis = abis64,
            supported32Abis = abis32,
            is64BitSupported = is64,
            vaBits = vaBits,
            hasCpuAtomics = hasAtomics,
            kernelRelease = kernel,
            hardwareWarning = warning
        )
    }

    suspend fun discoverBootstrapPackage(
        context: Context,
        mirror: RootfsMirror = getSelectedMirror(context)
    ): DiscoveredPackageInfo = withContext(Dispatchers.IO) {
        _installerState.value = LocalInstallerState.Discovering("Checking local storage & ${mirror.displayName}...")
        val arch = getBootstrapArch()
        val local = findLocalBootstrapArchive()
        if (local != null) {
            val info = DiscoveredPackageInfo(
                url = local.absolutePath,
                releaseTag = "local-offline",
                assetName = local.name,
                arch = arch,
                sizeBytes = local.length(),
                sizeFormatted = formatFileSize(local.length()),
                packageName = context.packageName,
                isLocal = true
            )
            _installerState.value = LocalInstallerState.AwaitingConfirmation(info)
            return@withContext info
        }

        var remoteSize = if (mirror == RootfsMirror.TERMUX_OFFICIAL) 35_000_000L else 400_054_861L
        runCatching {
            val req = Request.Builder().url(mirror.url).head().build()
            httpClient.newCall(req).execute().use { resp ->
                val len = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                if (len > 0L) remoteSize = len
            }
        }

        val info = DiscoveredPackageInfo(
            url = mirror.url,
            releaseTag = if (mirror == RootfsMirror.TERMUX_OFFICIAL) "bootstrap-2024.10" else "rootfs-v2",
            assetName = mirror.url.substringAfterLast('/').substringBefore('?').ifBlank { "bootstrap-$arch.zip" },
            arch = arch,
            sizeBytes = remoteSize,
            sizeFormatted = formatFileSize(remoteSize),
            packageName = context.packageName,
            isLocal = false
        )
        _installerState.value = LocalInstallerState.AwaitingConfirmation(info)
        info
    }

    fun dismissDiscoveryConfirmation() {
        if (_installerState.value is LocalInstallerState.AwaitingConfirmation) {
            _installerState.value = LocalInstallerState.Idle
        }
    }

    private fun showInstallNotification(
        context: Context,
        title: String,
        content: String,
        progress: Int = -1,
        max: Int = 100,
        ongoing: Boolean = true
    ) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    INSTALLER_NOTIFICATION_CHANNEL_ID,
                    "Agentra Linux Installer",
                    NotificationManager.IMPORTANCE_LOW
                )
                nm.createNotificationChannel(channel)
            }
            val builder = NotificationCompat.Builder(context, INSTALLER_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
                .setOnlyAlertOnce(true)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
                .setPriority(NotificationCompat.PRIORITY_LOW)
            if (progress >= 0) {
                builder.setProgress(max, progress.coerceIn(0, max), false)
            } else if (ongoing) {
                builder.setProgress(0, 0, true)
            }
            nm.notify(INSTALLER_NOTIFICATION_ID, builder.build())
        }
    }

    private fun cancelInstallNotification(context: Context) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(INSTALLER_NOTIFICATION_ID)
        }
    }

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
     * Returns Agentra's unified workspace root (`$HOME/projects`).
     * Agentra (`ai.agentra`) already has its own Android app sandbox, so `$HOME/projects`
     * is completely isolated from other apps without creating duplicate workspace folders.
     */
    fun getIsolatedWorkspaceDir(context: Context): File {
        return File(getHomeDir(context), "projects").apply {
            runCatching { mkdirs() }
        }
    }

    /**
     * Returns Agentra's unified projects directory (`$HOME/projects`).
     */
    fun getIsolatedProjectsDir(context: Context): File {
        return File(getHomeDir(context), "projects").apply {
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
            val agentraDlDir = File(dlDir, "Agentra")
            val names = listOf(
                "agentra-rootfs.zip",
                "bootrapz_2026-11-19.zip",
                "bootrapz_2026-10-04.zip",
                "bootstrap-aarch64.zip"
            )
            val dirs = listOf(
                agentraDlDir,
                File("/sdcard/Download/Agentra"),
                dlDir,
                File("/sdcard/Download")
            )
            for (d in dirs) {
                for (name in names) {
                    val f = File(d, name)
                    if (f.exists() && f.canRead() && f.length() > 20 * 1024 * 1024) {
                        return@runCatching f
                    }
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

        val hasAmApk = File(prefix, "libexec/termux-am/am.apk").exists()
        val hasGlibc = File(prefix, "glibc/lib/ld-linux-aarch64.so.1").exists()
        val hasRelocateHooks = File(prefix, "etc/apt/apt.conf.d/99agentra-relocate").exists() &&
            File(prefix, "libexec/agentra/relocate-debs").exists()

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
            hasUbuntuRootfs = hasUbuntu,
            hasAmApk = hasAmApk,
            hasGlibc = hasGlibc,
            hasRelocateHooks = hasRelocateHooks
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

    @Volatile
    private var optionBHooksSynced = false

    /**
     * Repairs file permissions + runs Option B byte relocation, apt/dpkg hooks, custom am.apk,
     * minimal glibc, and built-in Termux:API dispatcher.
     */
    suspend fun repairPermissions(context: Context): Result<EnvironmentDiagnostics> = withContext(Dispatchers.IO) {
        val prefix = getPrefixDir(context)
        val home = getHomeDir(context)
        _installerState.value = LocalInstallerState.Configuring("Repairing Option B native environment...", 0.35f)
        appendLog("Repairing Option B native environment on ${prefix.absolutePath}...")
        fixFilePermissions(prefix)
        TermuxRelocation.relocateIfNeeded(context, prefix)?.let { s ->
            appendLog("Relocated ${s.patchedFiles} files, ${s.links} links -> ${context.packageName}")
        }
        TermuxRelocation.relocateTree(home, context.packageName, pathsOnly = true)
        TermuxRelocation.ensurePackageHooks(context)
        ensureBundledHelperScripts(context)
        ensureGlibcEnvironment(context)
        ensureTermuxApiDispatcher(context)
        optionBHooksSynced = true
        val diag = getDiagnostics(context)
        _installProgress.value = RootfsInstallProgress.Success(
            message = "Option B Native Engine repaired and active (${context.packageName})!",
            diagnostics = diag
        )
        _installerState.value = LocalInstallerState.Success(
            message = "Environment repaired (${context.packageName})",
            prefixPath = diag.prefixPath,
            totalDiskUsageFormatted = diag.diskUsageFormatted
        )
        appendLog("Repair Complete! Bash=${diag.hasBash}, Node=${diag.hasNode}, Python=${diag.hasPython}, am.apk=${diag.hasAmApk}, glibc=${diag.hasGlibc}")
        Result.success(diag)
    }

    fun validateArchiveIntegrity(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 5 * 1024 * 1024) return false
        return runCatching {
            var hasCoreBinary = false
            CommonsZipFile(file).use { zf ->
                val entries = zf.entries
                var checked = 0
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    checked++
                    val n = e.name.removePrefix("./").trimStart('/')
                    if (n == "bin/bash" || n == "usr/bin/bash" || n == "bin/sh" || n == "usr/bin/sh" || n == "SYMLINKS.txt") {
                        hasCoreBinary = true
                    }
                    if (hasCoreBinary && checked > 50) break
                }
                checked > 10 && hasCoreBinary
            }
        }.getOrDefault(false)
    }

    private suspend fun installBootstrapInternal(context: Context, zipFile: File? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val prefix = getPrefixDir(context)
            val home = getHomeDir(context)
            val tmp = getTmpDir(context)

            prefix.mkdirs()
            home.mkdirs()
            tmp.mkdirs()

            _liveExtractionLogs.value = listOf(
                "Initializing Agentra Option B native workspace...",
                "Package ID: ${context.packageName} (${context.packageName.length} bytes)",
                "Target prefix: ${prefix.absolutePath}"
            )
            showInstallNotification(context, "Setting up Agentra Linux", "Stopping active sessions...", ongoing = true)

            // CRITICAL: Stop running terminal shells & local hub/bridge servers BEFORE extracting
            // so Linux kernel never rejects binary overwrites with ETXTBSY (Text file busy).
            appendLog("Stopping active terminals & local servers to prevent ETXTBSY...")
            runCatching { LocalTerminalManager.closeAll() }
            runCatching { LocalServerManager.stopServer() }
            runCatching {
                ProcessBuilder(
                    "/system/bin/sh",
                    "-c",
                    "pkill -9 agy 2>/dev/null || true; pkill -9 agy_ide_bridge 2>/dev/null || true; pkill -9 zsh 2>/dev/null || true; pkill -9 bash 2>/dev/null || true"
                ).start().waitFor(2, TimeUnit.SECONDS)
            }
            delay(300)

            val targetZip = zipFile ?: run {
                val local = findLocalBootstrapArchive()
                if (local != null) {
                    appendLog("Found local offline archive: ${local.absolutePath} (${formatFileSize(local.length())})")
                    local
                } else {
                    downloadBootstrapZip(context)
                }
            }

            if (!validateArchiveIntegrity(targetZip)) {
                if (zipFile != null) {
                    throw IllegalStateException("Selected archive (${targetZip.name}) is not a valid Agentra/Termux bootstrap ZIP.")
                }
                appendLog("Archive integrity check failed, re-downloading cleanly...")
                if (targetZip.absolutePath.startsWith(context.cacheDir.absolutePath)) {
                    targetZip.delete()
                }
                val freshZip = downloadBootstrapZip(context)
                if (!validateArchiveIntegrity(freshZip)) {
                    throw IllegalStateException("Downloaded bootstrap archive is corrupted or incomplete.")
                }
                extractZipPreservingPermissions(freshZip, prefix, context)
            } else {
                appendLog("Verified archive integrity: ${targetZip.name}")
                extractZipPreservingPermissions(targetZip, prefix, context)
            }

            _installProgress.value = RootfsInstallProgress.Configuring("Fixing executable permissions...")
            _installerState.value = LocalInstallerState.Configuring("Setting executable permissions...", 0.2f)
            showInstallNotification(context, "Setting up Agentra Linux", "Applying executable permissions...", ongoing = true)
            appendLog("Setting executable permissions (chmod 755)...")
            fixFilePermissions(prefix)

            _installProgress.value = RootfsInstallProgress.Configuring("Relocating binaries & scripts for ${context.packageName}...")
            _installerState.value = LocalInstallerState.Configuring("Relocating ELF binaries & scripts...", 0.5f)
            showInstallNotification(context, "Setting up Agentra Linux", "Relocating binaries for Agentra...", ongoing = true)
            appendLog("Running byte-for-byte Option B relocation -> ${context.packageName}...")
            val usrStats = TermuxRelocation.relocateIfNeeded(context, prefix) { scanned ->
                appendLog("Scanning & relocating files ($scanned scanned)...")
            }
            val homeStats = if (context.packageName.length == TermuxRelocation.TERMUX_PACKAGE.length) {
                TermuxRelocation.relocateTree(home, context.packageName, pathsOnly = true)
            } else {
                TermuxRelocation.Stats()
            }
            appendLog("Relocated ${usrStats?.patchedFiles ?: 0} prefix files, ${usrStats?.links ?: 0} symlinks, ${homeStats.patchedFiles} home paths.")

            _installProgress.value = RootfsInstallProgress.Configuring("Installing APT/DPKG hooks & custom am.apk...")
            _installerState.value = LocalInstallerState.Configuring("Installing APT hooks & helper tools...", 0.75f)
            appendLog("Installing permanent APT hook (99agentra-relocate), dpkg wrapper & am.apk...")
            TermuxRelocation.ensurePackageHooks(context)
            ensureBundledHelperScripts(context)

            _installProgress.value = RootfsInstallProgress.Configuring("Provisioning minimal Glibc & DNS resolver...")
            _installerState.value = LocalInstallerState.Configuring("Provisioning Glibc, DNS & Termux:API...", 0.9f)
            ensureGlibcEnvironment(context)
            ensureDnsAndHostsConfigured(prefix)
            ensureTermuxApiDispatcher(context)
            migrateLegacyJarvisFolders(context)
            optionBHooksSynced = true

            // Keep Native Direct as default execution mode unless user explicitly selects Ubuntu PRoot
            if (PRootRuntime.getRuntimeMode(context) == LinuxRuntimeMode.PROOT_TERMUX) {
                PRootRuntime.setRuntimeMode(context, LinuxRuntimeMode.DIRECT_NATIVE)
            }

            _installerState.value = LocalInstallerState.Verifying("Verifying bash & shell runtime...")
            showInstallNotification(context, "Setting up Agentra Linux", "Verifying Linux shell runtime...", ongoing = true)
            val verifyCode = runCatching {
                executeCommandLive(context, "bash --version | head -n 1", timeoutMs = 10_000L) { line ->
                    appendLog("Verify: $line")
                }
            }.getOrDefault(0)
            appendLog("Shell health check exited with code $verifyCode.")

            val exactSize = getDirectorySize(prefix)
            cachedPrefixSize = exactSize
            saveCachedDiskUsage(context, exactSize)

            val diag = getDiagnostics(context)
            _installProgress.value = RootfsInstallProgress.Success(
                message = "Agentra Native Linux environment ready (Option B · ${context.packageName})!",
                diagnostics = diag
            )
            _installerState.value = LocalInstallerState.Success(
                message = "Agentra Linux ready (${context.packageName})",
                prefixPath = diag.prefixPath,
                totalDiskUsageFormatted = diag.diskUsageFormatted
            )
            showInstallNotification(
                context = context,
                title = "Agentra Linux Ready",
                content = "Environment installed (${diag.diskUsageFormatted})",
                progress = -1,
                ongoing = false
            )
            appendLog(
                "Setup Complete! Bash=${diag.hasBash}, Node=${diag.hasNode}, Python=${diag.hasPython}, Git=${diag.hasGit}, " +
                    "Hooks=${diag.hasRelocateHooks}, am.apk=${diag.hasAmApk}, Glibc=${diag.hasGlibc}"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) {
                cancelInstallNotification(context)
                appendLog("Installation cancelled.")
                _installerState.value = LocalInstallerState.Idle
                throw e
            }
            Log.e(TAG, "Bootstrap installation failed", e)
            _installProgress.value = RootfsInstallProgress.Error("Installation failed: ${e.message}")
            _installerState.value = LocalInstallerState.Error("Installation failed: ${e.message}")
            showInstallNotification(
                context = context,
                title = "Agentra Setup Failed",
                content = e.message ?: "Unknown error",
                progress = -1,
                ongoing = false
            )
            appendLog("ERROR: ${e.message}")
            Result.failure(e)
        } finally {
            if (zipFile != null && zipFile.name == "picked_bootstrap.zip" && zipFile.absolutePath.startsWith(context.cacheDir.absolutePath)) {
                runCatching { zipFile.delete() }
            }
        }
    }

    private fun appendLog(msg: String) {
        val current = _liveExtractionLogs.value.toMutableList()
        current.add(msg)
        if (current.size > 200) current.removeAt(0)
        _liveExtractionLogs.value = current
    }

    private suspend fun downloadBootstrapZip(context: Context): File = withContext(Dispatchers.IO) {
        val mirror = getSelectedMirror(context)
        val cacheName = "bootstrap-${mirror.id.lowercase()}.zip"
        val destFile = File(context.cacheDir, cacheName)
        if (destFile.exists() && validateArchiveIntegrity(destFile)) return@withContext destFile

        val candidateUrls = listOf(mirror.url, HARDCODED_ROOTFS_URL, FALLBACK_BOOTSTRAP_URL).distinct()
        var response: okhttp3.Response? = null
        var chosenUrl = mirror.url
        for (url in candidateUrls) {
            appendLog("Connecting to mirror: ${url.substringAfter("://").substringBefore('/') }...")
            val attempt = runCatching {
                httpClient.newCall(Request.Builder().url(url).build()).execute()
            }.getOrNull()
            if (attempt != null && attempt.isSuccessful) {
                response = attempt
                chosenUrl = url
                break
            }
            attempt?.close()
        }

        if (response == null || !response.isSuccessful) {
            val code = response?.code ?: -1
            response?.close()
            throw Exception("Failed to download rootfs from mirrors (HTTP $code)")
        }

        val body = response.body ?: throw Exception("Empty response body")
        val totalLength = body.contentLength()
        val pkgName = chosenUrl.substringAfterLast('/').substringBefore('?').ifBlank { "rootfs.zip" }
        appendLog("Downloading $pkgName (${formatFileSize(totalLength)})...")

        _installProgress.value = RootfsInstallProgress.Downloading(0L, totalLength, 0f)
        _installerState.value = LocalInstallerState.Downloading(0L, totalLength, 0f, "0 B/s", pkgName)

        body.byteStream().use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(64 * 1024)
                var bytesCopied = 0L
                var lastBytesSample = 0L
                var lastSampleMs = System.currentTimeMillis()
                var read: Int
                var lastProgressUpdate = 0L

                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesCopied += read

                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 300) {
                        val elapsedSec = ((now - lastSampleMs).coerceAtLeast(1L)) / 1000.0
                        val speedBytesPerSec = ((bytesCopied - lastBytesSample) / elapsedSec).toLong().coerceAtLeast(0L)
                        val speedStr = "${formatFileSize(speedBytesPerSec)}/s"
                        lastSampleMs = now
                        lastBytesSample = bytesCopied
                        lastProgressUpdate = now
                        val fraction = if (totalLength > 0) bytesCopied.toFloat() / totalLength else 0f
                        _installProgress.value = RootfsInstallProgress.Downloading(bytesCopied, totalLength, fraction)
                        _installerState.value = LocalInstallerState.Downloading(
                            bytesDownloaded = bytesCopied,
                            totalBytes = totalLength,
                            progressFraction = fraction,
                            speedText = speedStr,
                            currentPackageName = pkgName
                        )
                        val pct = if (totalLength > 0) ((fraction * 100f).toInt()) else -1
                        showInstallNotification(
                            context = context,
                            title = "Downloading Agentra Linux",
                            content = "${formatFileSize(bytesCopied)} / ${formatFileSize(totalLength)} ($speedStr)",
                            progress = pct,
                            ongoing = true
                        )
                    }
                }
            }
        }
        destFile
    }

    @Suppress("DEPRECATION")
    internal suspend fun extractZipPreservingPermissions(
        zipFile: File,
        destDir: File,
        notifyContext: Context? = null
    ) = withContext(Dispatchers.IO) {
        var count = 0
        val totalEstimate = 26152
        var lastLogTime = 0L
        var symlinksTxtContent: String? = null
        val canonicalDest = destDir.canonicalFile
        val canonicalSandboxRoot = (destDir.parentFile ?: destDir).canonicalFile
        val allowedDestPath = canonicalDest.toPath().toAbsolutePath().normalize()
        val allowedSandboxPath = canonicalSandboxRoot.toPath().toAbsolutePath().normalize()

        CommonsZipFile(zipFile).use { commonsZip ->
            val entries = commonsZip.entries
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

                // Lexical Zip-slip guard (immune to pre-existing symlinks)
                val allowedRootPath = if (isHomeEntry) allowedSandboxPath else allowedDestPath
                val normalizedOutPath = outputFile.toPath().toAbsolutePath().normalize()
                if (!normalizedOutPath.startsWith(allowedRootPath)) continue

                if (entry.isUnixSymlink) {
                    val linkTarget = commonsZip.getUnixSymlink(entry)
                    PRootRuntime.createSymlinkCompat(linkTarget, outputFile)
                } else if (entry.isDirectory) {
                    if (Files.isSymbolicLink(normalizedOutPath) || (outputFile.exists() && !outputFile.isDirectory)) {
                        runCatching { outputFile.setWritable(true, true) }
                        runCatching { Files.deleteIfExists(normalizedOutPath) }
                    }
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    // CRITICAL ETXTBSY & EACCES FIX:
                    // Unlink any existing file, symlink, or colliding directory first so the kernel
                    // allocates a brand-new inode instead of truncating an executing binary or 0400 file.
                    if (Files.isSymbolicLink(normalizedOutPath)) {
                        runCatching { Files.deleteIfExists(normalizedOutPath) }
                    } else if (outputFile.exists()) {
                        runCatching { outputFile.setWritable(true, true) }
                        if (outputFile.isDirectory) {
                            runCatching { outputFile.deleteRecursively() }
                        } else {
                            runCatching { Files.deleteIfExists(normalizedOutPath) }
                            if (outputFile.exists()) {
                                runCatching { outputFile.delete() }
                            }
                        }
                    }

                    commonsZip.getInputStream(entry).use { inStream ->
                        FileOutputStream(outputFile).use { outStream ->
                            inStream.copyTo(outStream)
                        }
                    }

                    val isReadOnlyDexApk = rawName.endsWith("/am.apk") || rawName.endsWith("/am.apk.distrib")
                    if (isReadOnlyDexApk) {
                        // Android 14+ SAFER_DCL requires dynamically loaded DEX/APK files to be read-only (0400)
                        runCatching { Os.chmod(outputFile.absolutePath, 256) }
                    } else {
                        val mode = entry.unixMode
                        if (mode and 0b001_000_000 != 0 || rawName.startsWith("bin/") || rawName.contains(".local/bin/") || rawName.contains(".npm-global/bin/")) {
                            PRootRuntime.ensureExecutable(outputFile)
                        }
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
                    _installerState.value = LocalInstallerState.Extracting(
                        extractedFilesCount = count,
                        totalFilesEstimate = totalEstimate,
                        progressFraction = frac,
                        currentFileName = rawName
                    )
                    if (notifyContext != null) {
                        showInstallNotification(
                            context = notifyContext,
                            title = "Extracting Agentra Linux",
                            content = "$count files • $rawName",
                            progress = (frac * 100f).toInt(),
                            ongoing = true
                        )
                    }
                    appendLog("Unpacking [$count]: $rawName")
                }
            }
        }

        // Apply Termux bootstrap SYMLINKS.txt AFTER all target binaries & shared libraries exist
        if (!symlinksTxtContent.isNullOrBlank()) {
            val links = PRootRuntime.parseTermuxSymlinksTxt(symlinksTxtContent!!)
            appendLog("Applying ${links.size} bootstrap symlinks from SYMLINKS.txt...")
            links.forEach { (target, relLink) ->
                val cleanRel = relLink.removePrefix("./").trimStart('/').removePrefix("usr/")
                if (cleanRel.isNotBlank() && !cleanRel.contains("..")) {
                    val linkFile = File(canonicalDest, cleanRel)
                    val normalizedLinkPath = linkFile.toPath().toAbsolutePath().normalize()
                    if (normalizedLinkPath.startsWith(allowedDestPath)) {
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
        if (!optionBHooksSynced && File(prefixDir, "etc/apt").isDirectory) {
            optionBHooksSynced = true
            runCatching {
                TermuxRelocation.ensurePackageHooks(context)
                ensureBundledHelperScripts(context)
                ensureGlibcEnvironment(context)
                ensureTermuxApiDispatcher(context)
            }
        }
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
        env["ANDROID_DATA"] = "/data"
        env["ANDROID_ROOT"] = "/system"
        env["TERMUX_APP__PACKAGE_NAME"] = context.packageName
        env["TERMUX_MAIN_PACKAGE_NAME"] = context.packageName
        env["TERMUX_VERSION"] = "0.118.1"
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
     * Unpacks bundled `assets/glibc-min-arm64.tar.xz` into `$PREFIX/glibc` and configures
     * `nsswitch.conf`, `resolv.conf`, `hosts`, `libnss_dns.so`, and `libnss_files.so` so GNU glibc
     * binaries run cleanly alongside Bionic Termux packages on Android arm64.
     */
    fun ensureGlibcEnvironment(context: Context): Boolean {
        val prefixDir = getPrefixDir(context)
        val glibcDir = File(prefixDir, "glibc")
        val glibcLib = File(glibcDir, "lib")
        val ldLinux = File(glibcLib, "ld-linux-aarch64.so.1")

        if (!ldLinux.exists()) {
            extractGlibcMinAsset(context, prefixDir)
        }

        ensureDnsAndHostsConfigured(prefixDir)
        val etcDir = File(prefixDir, "etc").apply { mkdirs() }
        val etcResolv = File(etcDir, "resolv.conf")
        val etcHosts = File(etcDir, "hosts")

        val glibcEtc = File(glibcDir, "etc").apply { mkdirs() }
        val glibcNsswitch = File(glibcEtc, "nsswitch.conf")
        if (!glibcNsswitch.exists()) {
            glibcNsswitch.writeText("hosts: files dns\nnetworks: files\nprotocols: files\nservices: files\n")
        }

        val glibcResolv = File(glibcEtc, "resolv.conf")
        if (!glibcResolv.exists() && !Files.isSymbolicLink(glibcResolv.toPath())) {
            try {
                Os.symlink(etcResolv.absolutePath, glibcResolv.absolutePath)
            } catch (_: Exception) {
                glibcResolv.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
            }
        }

        val glibcHosts = File(glibcEtc, "hosts")
        if (!glibcHosts.exists() && !Files.isSymbolicLink(glibcHosts.toPath())) {
            try {
                Os.symlink(etcHosts.absolutePath, glibcHosts.absolutePath)
            } catch (_: Exception) {
                glibcHosts.writeText("127.0.0.1 localhost\n::1 localhost\n")
            }
        }

        if (glibcLib.exists()) {
            runCatching {
                val nssDns = File(glibcLib, "libnss_dns.so")
                if (!nssDns.exists()) Os.symlink("libnss_dns.so.2", nssDns.absolutePath)
                val nssFiles = File(glibcLib, "libnss_files.so")
                if (!nssFiles.exists()) Os.symlink("libnss_files.so.2", nssFiles.absolutePath)
            }
        }

        return ldLinux.exists()
    }

    private fun extractGlibcMinAsset(context: Context, prefixDir: File): Boolean {
        val assetName = "glibc-min-arm64.tar.xz"
        val inStream = try {
            context.assets.open(assetName)
        } catch (e: Exception) {
            appendLog("Minimal Glibc asset ($assetName) not found in APK: ${e.message}")
            return false
        }

        return try {
            val glibcDir = File(prefixDir, "glibc")
            glibcDir.mkdirs()
            val canonicalGlibc = glibcDir.canonicalFile
            var extractedFiles = 0

            XZInputStream(BufferedInputStream(inStream, 64 * 1024)).use { rawStream ->
                TarArchiveInputStream(rawStream).use { tarIn ->
                    var entry: TarArchiveEntry? = tarIn.nextEntry
                    while (entry != null) {
                        val rawName = entry.name
                        val entryName = rawName.removePrefix("glibc/").removePrefix("./glibc/").removePrefix("./")
                        if (entryName.isNotBlank() && entryName != "/" && !entryName.contains("..")) {
                            val targetFile = File(canonicalGlibc, entryName)
                            if (targetFile.canonicalPath.startsWith(canonicalGlibc.canonicalPath)) {
                                if (entry.isDirectory) {
                                    targetFile.mkdirs()
                                } else if (entry.isSymbolicLink) {
                                    PRootRuntime.createSymlinkCompat(entry.linkName, targetFile)
                                } else {
                                    targetFile.parentFile?.mkdirs()
                                    FileOutputStream(targetFile).use { fos -> tarIn.copyTo(fos) }
                                    val mode = entry.mode
                                    if (mode and 0b001_000_000 != 0 || entryName.endsWith(".so") || entryName.contains(".so.")) {
                                        PRootRuntime.ensureExecutable(targetFile)
                                    }
                                    extractedFiles++
                                }
                            }
                        }
                        entry = tarIn.nextEntry
                    }
                }
            }
            TermuxRelocation.relocateIfNeeded(context, glibcDir)
            appendLog("Unpacked bundled Glibc ($extractedFiles files) into ${glibcDir.absolutePath}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed extracting glibc-min asset: ${e.message}", e)
            appendLog("Glibc unpack warning: ${e.message}")
            false
        }
    }

    /**
     * Installs Agentra's bundled helper shell scripts (`git_clone`, `backup_projects`, `restore_projects`)
     * from `assets/bin/` into `$PREFIX/bin` and `$HOME/.local/bin`.
     */
    fun ensureBundledHelperScripts(context: Context) {
        val scripts = listOf("git_clone", "backup_projects", "restore_projects")
        val prefixBin = getBinDir(context)
        val localBin = File(getHomeDir(context), ".local/bin").apply { mkdirs() }
        val appBin = File(context.filesDir, "bin").apply { mkdirs() }
        val pkg = context.packageName

        for (name in scripts) {
            val raw = runCatching {
                context.assets.open("bin/$name").bufferedReader().use { it.readText() }
            }.getOrNull() ?: continue
            val localized = raw.replace("ai.agentra", pkg)
            listOf(File(prefixBin, name), File(localBin, name), File(appBin, name)).forEach { target ->
                runCatching {
                    TermuxRelocation.writeIfChanged(target, localized, executable = true)
                }
            }
        }
    }

    /**
     * Installs Agentra's `libexec/termux-api` dispatcher script supporting both Built-in
     * (`TermuxApiReceiver`) and External (`ai.agentra.api` / `com.termux.api`) providers.
     */
    fun ensureTermuxApiDispatcher(context: Context) {
        try {
            val prefixDir = getPrefixDir(context)
            if (!File(prefixDir, "bin").isDirectory) return
            val dispatcher = File(prefixDir, "libexec/termux-api")
            dispatcher.parentFile?.mkdirs()
            if (!TermuxRelocation.divertTermuxApi(context) && Files.isSymbolicLink(dispatcher.toPath())) {
                dispatcher.delete()
            }
            val pkg = context.packageName
            val provider = getTermuxApiProvider(context)
            val extPkg = termuxApiAppPackage(context)
            val prefix = "/data/data/$pkg/files/usr"
            val builtinMethods = com.jarvisai.assistant.receiver.TermuxApiReceiver.BUILT_IN_METHODS.joinToString("|")
            val script = """
                |#!$prefix/bin/bash
                |# Written by Agentra. Provider=$provider ($pkg / $extPkg)
                |METHOD="${'$'}1"
                |PROVIDER_MODE="$provider"
                |EXTERNAL_PKG="$extPkg"
                |DISPATCH_DISTRIB="$prefix/libexec/termux-api.distrib"
                |
                |IS_BUILTIN=0
                |case "${'$'}METHOD" in
                |    $builtinMethods|toast|vibrate|clipboard|clipboard-get|clipboard-set|torch|tts|tts-speak|notification|battery-status) IS_BUILTIN=1 ;;
                |esac
                |
                |if [ "${'$'}PROVIDER_MODE" = "external" ] || [ "${'$'}IS_BUILTIN" = "0" ]; then
                |    if [ -x "${'$'}DISPATCH_DISTRIB" ] && command -v pm >/dev/null 2>&1; then
                |        if pm path "${'$'}EXTERNAL_PKG" >/dev/null 2>&1 || pm path "com.termux.api" >/dev/null 2>&1; then
                |            exec "${'$'}DISPATCH_DISTRIB" "${'$'}@"
                |        fi
                |    fi
                |    if [ "${'$'}IS_BUILTIN" = "0" ]; then
                |        echo "termux-api: '${'$'}METHOD' requires companion Termux:API app (${'$'}EXTERNAL_PKG) or package 'pkg install termux-api'." >&2
                |        exit 1
                |    fi
                |fi
                |shift
                |
                |EXTRA_ARGS=()
                |if [ ! -t 0 ]; then
                |    INPUT=${'$'}(timeout 0.1 cat 2>/dev/null || true)
                |    if [ -n "${'$'}INPUT" ]; then
                |        EXTRA_ARGS=(--es text "${'$'}INPUT" --es message "${'$'}INPUT" --es content "${'$'}INPUT")
                |    fi
                |fi
                |
                |TMP_DIR="${'$'}{TMPDIR:-$prefix/tmp}"
                |mkdir -p "${'$'}TMP_DIR"
                |TMP_OUT="${'$'}TMP_DIR/api_out.${'$'}${'$'}"
                |
                |am broadcast --user ${'$'}(( ${'$'}(id -u) / 100000 )) \
                |    -a "$pkg.api" \
                |    -n "$pkg/com.jarvisai.assistant.receiver.TermuxApiReceiver" \
                |    --es "api_method" "${'$'}METHOD" \
                |    --es "socket_output" "${'$'}TMP_OUT" \
                |    "${'$'}{EXTRA_ARGS[@]}" \
                |    "${'$'}@" > /dev/null 2>&1
                |
                |answer=0
                |case "${'$'}METHOD" in
                |    BatteryStatus|battery-status|clipboard-get) answer=1 ;;
                |    Clipboard|clipboard) case " ${'$'}* " in *" set "*|*" --ez set true "*) ;; *) answer=1 ;; esac ;;
                |esac
                |if [ "${'$'}answer" = 1 ]; then
                |    for i in ${'$'}(seq 1 50); do
                |        if [ -e "${'$'}TMP_OUT" ]; then
                |            sleep 0.05
                |            cat "${'$'}TMP_OUT"
                |            echo ""
                |            break
                |        fi
                |        sleep 0.1
                |    done
                |fi
                |rm -f "${'$'}TMP_OUT"
                |""".trimMargin() + "\n"

            TermuxRelocation.writeIfChanged(dispatcher, script, executable = true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed ensuring termux-api dispatcher: ${e.message}")
        }
    }

    /**
     * Provisions an ultra-reliable POSIX shell stdio-to-HTTP MCP bridge script (`agentra-mcp-stdio`)
     * that works with `curl` or `/system/bin/toybox nc` so any stdio MCP client (including local `agy --hub`)
     * can communicate with AgentraEmbeddedMcp (:2236 / :2237) with zero external npm/node dependencies.
     */
    @Volatile
    private var verifiedStdioBridgePath: String? = null

    fun ensureMcpStdioBridgeScript(context: Context): File {
        verifiedStdioBridgePath?.let { cachedPath ->
            val cachedFile = File(cachedPath)
            if (cachedFile.exists() && cachedFile.canExecute() && cachedFile.length() > 100L) {
                return cachedFile
            }
        }
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

        val binDirs = listOf(
            File(context.filesDir, "bin").apply { mkdirs() },
            File(getHomeDir(context), ".local/bin").apply { mkdirs() },
            getBinDir(context)
        )
        var primary = File(binDirs.first(), "agentra-mcp-stdio")
        binDirs.forEach { dir ->
            runCatching { File(dir, "jarvis-mcp-stdio").delete() }
            val f = File(dir, "agentra-mcp-stdio")
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
        verifiedStdioBridgePath = primary.absolutePath
        return primary
    }

    /**
     * Provisions the `agentra-mcp` CLI utility in `$PATH` so Antigravity / terminal sessions can
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
                echo "Agentra MCP & Ecosystem CLI"
                echo "Usage:"
                echo "  agentra-mcp status                                     # List all active MCP servers & tools"
                echo "  agentra-mcp call <server> <tool> '<json_args>'         # Call any tool on an MCP server"
                echo "  agentra-mcp create-server '<json_spec>'                # Create & hot-reload a new MCP server"
                echo "  agentra-mcp skills                                     # List all active Agent Skills"
                echo "  agentra-mcp create-skill '<json_spec>'                 # Create & hot-reload a SKILL.md"
                echo "  agentra-mcp plugins                                    # List all Plugins & Browser Extensions"
                echo "  agentra-mcp create-plugin '<json_spec>'                # Create & hot-reload a Plugin/Extension"
                ;;
            esac
        """.trimIndent() + "\n"

        val binDirs = listOf(
            File(context.filesDir, "bin").apply { mkdirs() },
            File(getHomeDir(context), ".local/bin").apply { mkdirs() },
            getBinDir(context)
        )
        var primary = File(binDirs.first(), "agentra-mcp")
        binDirs.forEach { dir ->
            runCatching { File(dir, "jarvis-mcp").delete() }
            val f = File(dir, "agentra-mcp")
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

    /**
     * One-time safe cleanup & migration of legacy `Jarvis` / duplicate `Agentra AI` folders inside
     * the Agentra Home directory so users see only clean `~/projects`, `~/.agentra`,
     * `~/.agentra_bridge`, `~/mini-apps`, and `~/.gemini/agentra-antigravity`.
     */
    fun migrateLegacyJarvisFolders(context: Context) {
        runCatching {
            val home = getHomeDir(context)
            if (!home.exists()) return@runCatching

            val unifiedProjects = File(home, "projects").apply { mkdirs() }
            val unifiedAgentra = File(home, ".agentra").apply { mkdirs() }
            val unifiedBridge = File(home, ".agentra_bridge").apply { mkdirs() }
            val unifiedMiniApps = File(home, "mini-apps").apply { mkdirs() }
            val unifiedGeminiApp = File(home, ".gemini/agentra-antigravity")

            fun mergeDirectoryContents(src: File, dst: File) {
                if (!src.exists()) return
                if (Files.isSymbolicLink(src.toPath())) {
                    runCatching { Files.deleteIfExists(src.toPath()) }
                    return
                }
                if (!src.isDirectory) return
                dst.mkdirs()
                src.listFiles()?.forEach { child ->
                    val target = File(dst, child.name)
                    if (child.isDirectory) {
                        mergeDirectoryContents(child, target)
                    } else if (!target.exists() && !Files.isSymbolicLink(target.toPath())) {
                        runCatching {
                            if (!child.renameTo(target)) {
                                child.copyTo(target, overwrite = false)
                            }
                        }
                    }
                }
                runCatching { src.deleteRecursively() }
            }

            // 1. Consolidate legacy workspace folders -> ~/projects
            listOf(
                File(home, "jarvis-workspace/projects"),
                File(home, "jarvis-workspace"),
                File(home, "agentra-workspace/projects"),
                File(home, "agentra-workspace")
            ).forEach { oldWorkspace ->
                if (oldWorkspace.exists() || Files.isSymbolicLink(oldWorkspace.toPath())) {
                    mergeDirectoryContents(oldWorkspace, unifiedProjects)
                }
            }

            // 2. Consolidate legacy hidden mini-apps -> ~/mini-apps or ~/.agentra/mini-apps
            val oldHiddenMiniApps = File(home, ".jarvis-ai/mini-apps")
            if (oldHiddenMiniApps.exists()) {
                mergeDirectoryContents(oldHiddenMiniApps, File(unifiedAgentra, "mini-apps"))
            }

            // 3. Consolidate legacy .jarvis / .jarvis-ai / .agentra-ai -> ~/.agentra
            listOf(
                File(home, ".jarvis"),
                File(home, ".jarvis-ai"),
                File(home, ".agentra-ai")
            ).forEach { oldHidden ->
                if (oldHidden.exists() || Files.isSymbolicLink(oldHidden.toPath())) {
                    mergeDirectoryContents(oldHidden, unifiedAgentra)
                }
            }

            // 4. Consolidate legacy .jarvis_bridge -> ~/.agentra_bridge
            val oldBridge = File(home, ".jarvis_bridge")
            if (oldBridge.exists() || Files.isSymbolicLink(oldBridge.toPath())) {
                mergeDirectoryContents(oldBridge, unifiedBridge)
            }

            // 5. Consolidate legacy ~/.gemini, ~/.config, ~/.local/share jarvis-antigravity -> agentra-antigravity
            listOf(
                File(home, ".gemini/jarvis-antigravity") to unifiedGeminiApp,
                File(home, ".gemini/jarvis-antigravity-cli") to File(home, ".gemini/agentra-antigravity-cli"),
                File(home, ".config/jarvis-antigravity") to File(home, ".config/agentra-antigravity"),
                File(home, ".config/jarvis-antigravity-cli") to File(home, ".config/agentra-antigravity-cli"),
                File(home, ".local/share/jarvis-antigravity") to File(home, ".local/share/agentra-antigravity"),
                File(home, ".local/share/jarvis-antigravity-cli") to File(home, ".local/share/agentra-antigravity-cli")
            ).forEach { (oldDir, newDir) ->
                if (oldDir.exists() || Files.isSymbolicLink(oldDir.toPath())) {
                    mergeDirectoryContents(oldDir, newDir)
                }
            }

            // 6. Remove any stale jarvis-mcp* binaries in PATH
            listOf(
                File(context.filesDir, "bin"),
                File(home, ".local/bin"),
                getBinDir(context)
            ).forEach { dir ->
                runCatching { File(dir, "jarvis-mcp").delete() }
                runCatching { File(dir, "jarvis-mcp-stdio").delete() }
            }

            // 7. Ensure clean ~/mini-apps directory exists
            if (!unifiedMiniApps.exists()) {
                unifiedMiniApps.mkdirs()
            }
        }.onFailure { err ->
            Log.w(TAG, "Legacy Jarvis folder migration warning: ${err.message}")
        }
    }

    suspend fun extractDebsArchive(context: Context, archiveFile: File): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val prefixDir = getPrefixDir(context)
            val dpkgStatus = File(prefixDir, "var/lib/dpkg/status").apply { parentFile?.mkdirs() }
            val tempDir = File(getTmpDir(context), "deb_extract_${System.currentTimeMillis()}").apply { mkdirs() }
            var extractedDebCount = 0
            try {
                if (archiveFile.name.endsWith(".zip", ignoreCase = true)) {
                    CommonsZipFile(archiveFile).use { zf ->
                        val entries = zf.entries
                        while (entries.hasMoreElements()) {
                            val e = entries.nextElement()
                            if (!e.isDirectory && e.name.endsWith(".deb")) {
                                val outDeb = File(tempDir, File(e.name).name)
                                zf.getInputStream(e).use { ins -> outDeb.outputStream().use { ins.copyTo(it) } }
                                if (extractSingleDebFile(outDeb, prefixDir, dpkgStatus, context)) extractedDebCount++
                            }
                        }
                    }
                } else {
                    val baseIn: InputStream = FileInputStream(archiveFile).let { fis ->
                        when {
                            archiveFile.name.endsWith(".xz", ignoreCase = true) -> XZInputStream(BufferedInputStream(fis))
                            archiveFile.name.endsWith(".gz", ignoreCase = true) || archiveFile.name.endsWith(".tgz", ignoreCase = true) ->
                                GzipCompressorInputStream(BufferedInputStream(fis))
                            else -> BufferedInputStream(fis)
                        }
                    }
                    TarArchiveInputStream(baseIn).use { tarIn ->
                        var entry = tarIn.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name.endsWith(".deb")) {
                                val outDeb = File(tempDir, File(entry.name).name)
                                outDeb.outputStream().use { tarIn.copyTo(it) }
                                if (extractSingleDebFile(outDeb, prefixDir, dpkgStatus, context)) extractedDebCount++
                            }
                            entry = tarIn.nextEntry
                        }
                    }
                }
                TermuxRelocation.relocateIfNeeded(context, prefixDir)
                TermuxRelocation.ensurePackageHooks(context)
                invalidateSizeCache(context)
            } finally {
                tempDir.deleteRecursively()
            }
            extractedDebCount
        }
    }

    fun extractSingleDebFile(
        debFile: File,
        prefixDir: File,
        dpkgStatusFile: File,
        appContext: Context
    ): Boolean {
        return runCatching {
            val canonicalPrefix = prefixDir.canonicalFile
            var controlText = ""
            FileInputStream(debFile).use { fis ->
                ArArchiveInputStream(BufferedInputStream(fis)).use { arIn ->
                    var arEntry: ArArchiveEntry? = arIn.nextEntry
                    while (arEntry != null) {
                        val name = arEntry.name
                        if (name.startsWith("control.tar")) {
                            controlText = readControlFromArEntry(arIn, name)
                        } else if (name.startsWith("data.tar")) {
                            extractDataTarFromArEntry(arIn, name, canonicalPrefix)
                        }
                        arEntry = arIn.nextEntry
                    }
                }
            }
            if (controlText.isNotBlank()) {
                val pkgLine = controlText.lines().firstOrNull { it.startsWith("Package:") }?.substringAfter(":")?.trim()
                if (!pkgLine.isNullOrBlank()) {
                    val existing = if (dpkgStatusFile.exists()) dpkgStatusFile.readText() else ""
                    if (!existing.contains("Package: $pkgLine\n")) {
                        val statusEntry = buildString {
                            if (existing.isNotEmpty() && !existing.endsWith("\n\n")) append("\n")
                            append(controlText.trim())
                            append("\nStatus: install ok installed\n\n")
                        }
                        dpkgStatusFile.appendText(statusEntry)
                    }
                }
            }
            TermuxRelocation.relocateTree(canonicalPrefix, appContext.packageName, pathsOnly = false)
            true
        }.onFailure { err ->
            Log.w(TAG, "extractSingleDebFile failed for ${debFile.name}: ${err.message}")
        }.getOrDefault(false)
    }

    private fun readControlFromArEntry(rawArStream: InputStream, name: String): String {
        val tarStream = wrapTarCompressorStream(rawArStream, name)
        TarArchiveInputStream(tarStream).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith("control")) {
                    return tar.readBytes().toString(Charsets.UTF_8)
                }
                entry = tar.nextEntry
            }
        }
        return ""
    }

    private fun extractDataTarFromArEntry(rawArStream: InputStream, name: String, canonicalPrefix: File) {
        val tarStream = wrapTarCompressorStream(rawArStream, name)
        TarArchiveInputStream(tarStream).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                var relPath = entry.name.removePrefix("./").trimStart('/')
                listOf(
                    "data/data/com.termux/files/usr/",
                    "data/data/ai.antigem/files/usr/",
                    "data/data/ai.agentra/files/usr/",
                    "data/user/0/com.termux/files/usr/",
                    "data/user/0/ai.antigem/files/usr/",
                    "data/user/0/ai.agentra/files/usr/",
                    "usr/"
                ).forEach { prefixStrip ->
                    if (relPath.startsWith(prefixStrip)) {
                        relPath = relPath.removePrefix(prefixStrip)
                    }
                }
                if (relPath.isNotBlank() && !relPath.contains("..")) {
                    val target = File(canonicalPrefix, relPath)
                    if (target.canonicalPath.startsWith(canonicalPrefix.canonicalPath)) {
                        when {
                            entry.isDirectory -> target.mkdirs()
                            entry.isSymbolicLink -> PRootRuntime.createSymlinkCompat(entry.linkName, target)
                            else -> {
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { fos -> tar.copyTo(fos) }
                                if (entry.mode and 0b001_000_000 != 0 || relPath.startsWith("bin/") || relPath.contains(".so")) {
                                    PRootRuntime.ensureExecutable(target)
                                }
                            }
                        }
                    }
                }
                entry = tar.nextEntry
            }
        }
    }

    private fun wrapTarCompressorStream(arEntryStream: InputStream, name: String): InputStream {
        // Buffer current AR entry bytes so wrapped compressors never read past EOF of this AR member
        val data = arEntryStream.readBytes()
        val bais = BufferedInputStream(data.inputStream())
        return when {
            name.endsWith(".xz") -> XZInputStream(bais)
            name.endsWith(".gz") -> GzipCompressorInputStream(bais)
            else -> bais
        }
    }

    suspend fun repairIncompletePackages(context: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val prefix = getPrefixDir(context)
            listOf(
                "var/lib/dpkg/lock",
                "var/lib/dpkg/lock-frontend",
                "var/cache/apt/archives/lock"
            ).forEach { rel ->
                val lockFile = File(prefix, rel)
                if (lockFile.exists()) lockFile.delete()
            }
            val logs = mutableListOf<String>()
            executeCommandLive(
                context = context,
                command = "dpkg --configure -a && apt-get install -f -y",
                timeoutMs = 180_000L
            ) { line ->
                logs.add(line)
                appendLog(line)
            }
            repairPermissions(context)
            logs.joinToString("\n").ifBlank { "Package & permission repair completed." }
        }
    }

    suspend fun resetEnvironment(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            LocalTerminalManager.closeAll()
            clearAppDataAndReset(context)
            _installProgress.value = RootfsInstallProgress.Idle
            _installerState.value = LocalInstallerState.Idle
            _liveExtractionLogs.value = emptyList()
            cancelInstallNotification(context)
        }
    }

    fun clearAppDataAndReset(context: Context) {
        runCatching { getPrefixDir(context).deleteRecursively() }
        runCatching { getHomeDir(context).deleteRecursively() }
        runCatching { context.cacheDir.listFiles()?.forEach { if (it.name.startsWith("bootstrap")) it.delete() } }
        invalidateSizeCache(context)
        optionBHooksSynced = false
    }

    suspend fun executeScriptLive(
        context: Context,
        scriptContent: String,
        workingDir: File? = null,
        timeoutMs: Long = 180_000L,
        onLine: (String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val tmpScript = File(getTmpDir(context), "agentra_live_${System.currentTimeMillis()}.sh")
        tmpScript.writeText(scriptContent)
        PRootRuntime.ensureExecutable(tmpScript)
        try {
            executeCommandLive(context, "\"${tmpScript.absolutePath}\"", workingDir, timeoutMs, onLine)
        } finally {
            runCatching { tmpScript.delete() }
        }
    }

    suspend fun executeCommandLive(
        context: Context,
        command: String,
        workingDir: File? = null,
        timeoutMs: Long = 180_000L,
        onLine: (String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val shell = getDefaultShell(context)
        val cwd = workingDir ?: resolveWritableWorkingDir(context, null)
        val envMap = linkedMapOf<String, String>()
        applyIsolatedToolEnvironment(context, envMap)
        val pb = ProcessBuilder(shell, "-c", command).apply {
            directory(cwd)
            redirectErrorStream(true)
            environment().putAll(envMap)
        }
        val proc = pb.start()
        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val line = reader.readLine()
            if (line == null) break
            onLine(line)
            if (System.currentTimeMillis() > deadline) {
                proc.destroyForcibly()
                return@withContext -1
            }
        }
        proc.waitFor()
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
