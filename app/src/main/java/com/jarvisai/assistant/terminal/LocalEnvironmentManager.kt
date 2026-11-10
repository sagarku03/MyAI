package com.jarvisai.assistant.terminal

import android.content.Context
import android.os.Build
import android.system.Os
import android.system.OsConstants
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.zip.ZipFile as CommonsZipFile
import java.io.*
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

object LocalEnvironmentManager {

    private const val TAG = "LocalEnvironmentManager"
    const val HARDCODED_ROOTFS_URL = "https://github.com/Santoshkurmi/antigem/releases/download/rootfs-v1/bootrapz_2026-10-04.zip"
    const val HARDCODED_ROOTFS_SIZE_BYTES = 350 * 1024 * 1024L

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _installerState = MutableStateFlow<LocalInstallerState>(LocalInstallerState.Idle)
    val installerState: StateFlow<LocalInstallerState> = _installerState.asStateFlow()

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
        val sh = File(getBinDir(context), "sh")
        val bash = File(getBinDir(context), "bash")
        return (sh.exists() && sh.canExecute()) || (bash.exists() && bash.canExecute())
    }

    fun getDefaultShell(context: Context): String {
        val bash = File(getBinDir(context), "bash")
        if (bash.exists() && bash.canExecute()) return bash.absolutePath
        val sh = File(getBinDir(context), "sh")
        if (sh.exists() && sh.canExecute()) return sh.absolutePath
        return "/system/bin/sh"
    }

    suspend fun installBootstrap(context: Context, zipFile: File? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val prefix = getPrefixDir(context)
            val home = getHomeDir(context)
            val tmp = getTmpDir(context)

            prefix.mkdirs()
            home.mkdirs()
            tmp.mkdirs()

            val targetZip = zipFile ?: downloadBootstrapZip(context)
            _installerState.value = LocalInstallerState.Extracting(0, 10000, 0.1f, "Unpacking rootfs...")
            extractZipWithSymlinks(targetZip, prefix)

            _installerState.value = LocalInstallerState.Configuring("Setting executable permissions...", 0.9f)
            fixFilePermissions(prefix)

            _installerState.value = LocalInstallerState.Success(
                message = "Termux Linux environment installed successfully!",
                prefixPath = prefix.absolutePath,
                totalDiskUsageFormatted = formatFileSize(getDirectorySize(prefix))
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Bootstrap installation failed", e)
            _installerState.value = LocalInstallerState.Error("Installation failed: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun downloadBootstrapZip(context: Context): File = withContext(Dispatchers.IO) {
        val destFile = File(context.cacheDir, "bootstrap.zip")
        if (destFile.exists() && destFile.length() > 10 * 1024 * 1024) return@withContext destFile

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
                        _installerState.value = LocalInstallerState.Downloading(
                            bytesDownloaded = totalRead,
                            totalBytes = totalBytes,
                            progressFraction = frac,
                            speedText = "${formatFileSize(totalRead)} / ${formatFileSize(totalBytes)}",
                            currentPackageName = "Termux Core Rootfs"
                        )
                    }
                }
            }
        }
        destFile
    }

    @Suppress("DEPRECATION")
    private fun extractZipWithSymlinks(zipFile: File, targetDir: File) {
        val commonsZip = CommonsZipFile(zipFile)
        val entries = commonsZip.entries
        var count = 0

        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val destFile = File(targetDir, entry.name)

            if (entry.isUnixSymlink) {
                val targetPath = commonsZip.getUnixSymlink(entry)
                destFile.parentFile?.mkdirs()
                try {
                    destFile.delete()
                    Os.symlink(targetPath, destFile.absolutePath)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create symlink: ${destFile.name} -> $targetPath")
                }
            } else if (entry.isDirectory) {
                destFile.mkdirs()
            } else {
                destFile.parentFile?.mkdirs()
                commonsZip.getInputStream(entry).use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                if (entry.name.startsWith("bin/") || entry.name.contains("/bin/")) {
                    destFile.setExecutable(true, false)
                    destFile.setReadable(true, false)
                }
            }
            count++
            if (count % 250 == 0) {
                val frac = (0.1f + (count.coerceAtMost(10000) / 10000f) * 0.75f).coerceIn(0.1f, 0.85f)
                _installerState.value = LocalInstallerState.Extracting(
                    extractedFilesCount = count,
                    totalFilesEstimate = 10000,
                    progressFraction = frac,
                    currentFileName = entry.name
                )
            }
        }
        commonsZip.close()
    }

    private fun fixFilePermissions(dir: File) {
        dir.walkTopDown().forEach { file ->
            if (file.isDirectory || file.parentFile?.name == "bin" || file.parentFile?.name == "lib") {
                try {
                    Os.chmod(file.absolutePath, 493) // 0755 in octal = 493 decimal
                } catch (_: Exception) {}
            }
        }
    }

    private fun getDirectorySize(dir: File): Long {
        var size = 0L
        dir.walkTopDown().forEach { if (it.isFile) size += it.length() }
        return size
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
        return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }
}
