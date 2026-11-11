package com.jarvisai.assistant.terminal

import android.content.Context
import android.os.Build
import android.os.Environment
import android.system.Os
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile as CommonsZipFile
import java.io.File
import java.io.IOException
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

data class EnvironmentDiagnostics(
    val isInstalled: Boolean,
    val defaultShell: String,
    val hasBash: Boolean,
    val hasNode: Boolean,
    val hasPython: Boolean,
    val hasGit: Boolean,
    val hasNvim: Boolean,
    val diskUsageFormatted: String,
    val prefixPath: String
)

sealed class RootfsInstallProgress {
    object Idle : RootfsInstallProgress()
    data class Downloading(val bytesRead: Long, val totalBytes: Long, val fraction: Float, val speed: String) : RootfsInstallProgress()
    data class Extracting(val currentFile: String, val filesExtracted: Int, val totalEstimate: Int, val fraction: Float) : RootfsInstallProgress()
    data class Configuring(val status: String) : RootfsInstallProgress()
    data class Success(val message: String, val diagnostics: EnvironmentDiagnostics) : RootfsInstallProgress()
    data class Error(val error: String) : RootfsInstallProgress()
}

object LocalEnvironmentManager {

    private const val TAG = "LocalEnvironmentManager"
    const val HARDCODED_ROOTFS_URL = "https://github.com/Santoshkurmi/antigem/releases/download/rootfs-v1/bootrapz_2026-10-04.zip"
    const val HARDCODED_ROOTFS_SIZE_BYTES = 350 * 1024 * 1024L

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _installProgress = MutableStateFlow<RootfsInstallProgress>(RootfsInstallProgress.Idle)
    val installProgress: StateFlow<RootfsInstallProgress> = _installProgress.asStateFlow()

    private val _liveExtractionLogs = MutableStateFlow<List<String>>(emptyList())
    val liveExtractionLogs: StateFlow<List<String>> = _liveExtractionLogs.asStateFlow()

    fun getDeviceArch(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return when {
            abi.contains("arm64") || abi.contains("aarch64") -> "aarch64"
            abi.contains("armeabi") || abi.contains("arm") -> "arm"
            abi.contains("x86_64") -> "x86_64"
            abi.contains("x86") -> "i686"
            else -> "aarch64"
        }
    }

    fun getPrefixDir(context: Context): File {
        val path = if (context.packageName == "com.termux") {
            "/data/data/com.termux/files/usr"
        } else {
            "${context.filesDir.absolutePath}/usr"
        }
        return File(path).apply { mkdirs() }
    }

    fun getBinDir(context: Context): File = File(getPrefixDir(context), "bin").apply { mkdirs() }

    fun getHomeDir(context: Context): File {
        val path = if (context.packageName == "com.termux") {
            "/data/data/com.termux/files/home"
        } else {
            "${context.filesDir.absolutePath}/home"
        }
        return File(path).apply { mkdirs() }
    }

    fun getTmpDir(context: Context): File = File(getPrefixDir(context), "tmp").apply { mkdirs() }

    fun isEnvironmentInstalled(context: Context): Boolean {
        val bash = File(getBinDir(context), "bash")
        val sh = File(getBinDir(context), "sh")
        return (bash.exists() && bash.canExecute()) || (sh.exists() && sh.canExecute())
    }

    fun getDefaultShell(context: Context): String {
        val bash = File(getBinDir(context), "bash")
        if (bash.exists() && bash.canExecute()) return bash.absolutePath
        val sh = File(getBinDir(context), "sh")
        if (sh.exists() && sh.canExecute()) return sh.absolutePath
        return "/system/bin/sh"
    }

    fun findLocalBootstrapArchive(): File? {
        return runCatching {
            val candidates = listOf(
                File("/sdcard/Download/bootrapz_2026-10-04.zip"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "bootrapz_2026-10-04.zip"),
                File("/sdcard/Download/bootstrap-aarch64.zip")
            )
            for (f in candidates) {
                if (f.exists() && f.length() > 10 * 1024 * 1024) {
                    return@runCatching f
                }
            }
            val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (dlDir != null && dlDir.exists() && dlDir.isDirectory) {
                val matching = dlDir.listFiles { file ->
                    file.isFile && file.name.endsWith(".zip", ignoreCase = true) &&
                        (file.name.contains("bootrap", ignoreCase = true) || file.name.contains("bootstrap", ignoreCase = true)) &&
                        file.length() > 20 * 1024 * 1024
                }
                if (!matching.isNullOrEmpty()) return@runCatching matching.first()
            }
            null
        }.getOrNull()
    }

    // Fast in-memory cached size to prevent re-walking 25,600+ files
    @Volatile
    private var cachedPrefixSize: Long = -1L

    fun getDiagnostics(context: Context): EnvironmentDiagnostics {
        val binDir = getBinDir(context)
        val bash = File(binDir, "bash").exists()
        val node = File(binDir, "node").exists()
        val python = File(binDir, "python3").exists() || File(binDir, "python").exists()
        val git = File(binDir, "git").exists()
        val nvim = File(binDir, "nvim").exists()
        val prefix = getPrefixDir(context)

        val isInstalled = isEnvironmentInstalled(context)
        val sizeBytes = if (!isInstalled) 0L else {
            if (cachedPrefixSize > 0) cachedPrefixSize else {
                val measured = getDirectorySize(prefix)
                cachedPrefixSize = measured
                measured
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
            prefixPath = prefix.absolutePath
        )
    }

    fun invalidateSizeCache() {
        cachedPrefixSize = -1L
    }

    suspend fun installBootstrap(context: Context, zipFile: File? = null): Result<Unit> = withContext(Dispatchers.IO) {
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
            appendLog("Setting executable permissions (chmod 755)...")
            fixFilePermissions(prefix)
            invalidateSizeCache()

            val diag = getDiagnostics(context)
            _installProgress.value = RootfsInstallProgress.Success(
                message = "Termux Linux environment installed successfully!",
                diagnostics = diag
            )
            appendLog("Setup Complete! Verified: Bash=${diag.hasBash}, Node=${diag.hasNode}, Python=${diag.hasPython}, Git=${diag.hasGit}")
            Result.success(Unit)
        } catch (e: Exception) {
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
        if (!response.isSuccessful) throw IOException("Download failed with HTTP ${response.code}")

        val body = response.body ?: throw IOException("Empty response body")
        val totalBytes = body.contentLength()

        body.byteStream().use { input ->
            destFile.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var totalRead = 0L
                var lastLog = System.currentTimeMillis()

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    val now = System.currentTimeMillis()
                    if (now - lastLog > 300) {
                        lastLog = now
                        val frac = if (totalBytes > 0) totalRead.toFloat() / totalBytes else 0f
                        _installProgress.value = RootfsInstallProgress.Downloading(
                            bytesRead = totalRead,
                            totalBytes = totalBytes,
                            fraction = frac,
                            speed = "${formatFileSize(totalRead)} / ${formatFileSize(totalBytes)}"
                        )
                    }
                }
            }
        }
        destFile
    }

    @Suppress("DEPRECATION")
    private suspend fun extractZipPreservingPermissions(zipFile: File, destDir: File) = withContext(Dispatchers.IO) {
        val commonsZip = CommonsZipFile(zipFile)
        val entries = commonsZip.entries
        var count = 0
        val totalEstimate = 8500
        var lastLogTime = 0L

        while (entries.hasMoreElements()) {
            val entry = entries.nextElement() as ZipArchiveEntry
            count++
            val rawName = entry.name.removePrefix("usr/").removePrefix("./")
            if (rawName.isBlank()) continue

            val outputFile = File(destDir, rawName)

            if (entry.isUnixSymlink) {
                try {
                    val linkTarget = commonsZip.getUnixSymlink(entry)
                    if (outputFile.exists() || File(outputFile.absolutePath).exists()) {
                        outputFile.delete()
                    }
                    outputFile.parentFile?.mkdirs()
                    Os.symlink(linkTarget, outputFile.absolutePath)
                } catch (e: Exception) {
                    Log.w(TAG, "Symlink creation failed for ${outputFile.name}: ${e.message}")
                }
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
                if (mode and 0b001_000_000 != 0 || rawName.startsWith("bin/")) {
                    outputFile.setExecutable(true, false)
                    try { Os.chmod(outputFile.absolutePath, 493) } catch (_: Exception) {}
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
    }

    fun fixFilePermissions(prefixDir: File) {
        val binDir = File(prefixDir, "bin")
        binDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                file.setExecutable(true, false)
                file.setReadable(true, false)
                try { Os.chmod(file.absolutePath, 493) } catch (_: Exception) {}
            }
        }
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
