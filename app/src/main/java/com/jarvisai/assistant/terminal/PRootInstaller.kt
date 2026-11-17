package com.jarvisai.assistant.terminal

import android.content.Context
import android.os.Build
import android.util.Log
import com.jarvisai.assistant.ui.updater.AppUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

sealed class PRootInstallState {
    object Idle : PRootInstallState()
    data class Installing(val status: String, val fraction: Float = 0f) : PRootInstallState()
    data class Success(val message: String) : PRootInstallState()
    data class Error(val error: String) : PRootInstallState()
}

data class AptPackageMeta(
    val packageName: String,
    val version: String,
    val filename: String,
    val depends: List<String>
)

/**
 * Provisions real PRoot runtime binaries & runtime shared libs (`proot`, `libtalloc`, `libandroid-shmem`)
 * from official Termux APT repositories, plus optional Ubuntu 24.04 Base RootFS archives.
 * Uses Commons Compress (`ar` + `tar.gz`) + Tukaani `xz` already included in Gradle dependencies.
 */
object PRootInstaller {
    private const val TAG = "PRootInstaller"
    private const val TERMUX_APT_BASE = "https://packages.termux.dev/apt/termux-main"
    private const val TERMUX_INTERNAL_USR_PREFIX = "data/data/com.termux/files/usr/"

    private val _state = MutableStateFlow<PRootInstallState>(PRootInstallState.Idle)
    val state: StateFlow<PRootInstallState> = _state.asStateFlow()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(AppUpdateManager.sharedResilientDns)
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun mapAbiToTermuxArch(abis: Array<String> = Build.SUPPORTED_ABIS ?: arrayOf("arm64-v8a")): String {
        for (abi in abis) {
            when (abi.lowercase()) {
                "arm64-v8a", "aarch64" -> return "aarch64"
                "armeabi-v7a", "armeabi" -> return "arm"
                "x86_64" -> return "x86_64"
                "x86", "i686" -> return "i686"
            }
        }
        return "aarch64"
    }

    fun mapAbiToUbuntuArch(abis: Array<String> = Build.SUPPORTED_ABIS ?: arrayOf("arm64-v8a")): String {
        for (abi in abis) {
            when (abi.lowercase()) {
                "arm64-v8a", "aarch64" -> return "arm64"
                "armeabi-v7a", "armeabi" -> return "armhf"
                "x86_64" -> return "amd64"
            }
        }
        return "arm64"
    }

    /**
     * Parses a Debian/APT `Packages` index into a map keyed by Package name.
     */
    fun parsePackagesIndex(rawText: String): Map<String, AptPackageMeta> {
        val result = linkedMapOf<String, AptPackageMeta>()
        var pkg = ""
        var ver = ""
        var filename = ""
        var deps = emptyList<String>()

        fun flushStanza() {
            if (pkg.isNotEmpty() && filename.isNotEmpty()) {
                result[pkg] = AptPackageMeta(pkg, ver, filename, deps)
            }
            pkg = ""
            ver = ""
            filename = ""
            deps = emptyList()
        }

        rawText.lineSequence().forEach { line ->
            if (line.isBlank()) {
                flushStanza()
                return@forEach
            }
            when {
                line.startsWith("Package:") -> pkg = line.substringAfter("Package:").trim()
                line.startsWith("Version:") -> ver = line.substringAfter("Version:").trim()
                line.startsWith("Filename:") -> filename = line.substringAfter("Filename:").trim()
                line.startsWith("Depends:") -> {
                    val rawDeps = line.substringAfter("Depends:").trim()
                    deps = rawDeps.split(",")
                        .mapNotNull { token ->
                            val primary = token.substringBefore("|").trim()
                            val cleanName = primary.substringBefore("(").trim()
                            cleanName.takeIf { it.isNotEmpty() }
                        }
                }
            }
        }
        flushStanza()
        return result
    }

    /**
     * Resolves transitive runtime dependencies for requested packages in topological install order.
     */
    fun resolveDependencyOrder(
        index: Map<String, AptPackageMeta>,
        roots: List<String>
    ): List<AptPackageMeta> {
        val ordered = mutableListOf<AptPackageMeta>()
        val visited = mutableSetOf<String>()

        fun visit(name: String) {
            if (!visited.add(name)) return
            val meta = index[name] ?: return
            meta.depends.forEach { dep ->
                // Skip non-essential virtual or system shell packages during minimal proot bootstrap
                if (dep != "bash" && dep != "termux-tools" && dep != "ndk-sysroot") {
                    visit(dep)
                }
            }
            ordered.add(meta)
        }

        roots.forEach(::visit)
        return ordered
    }

    /**
     * Extracts a Debian `.deb` (`ar` container -> `data.tar.xz` / `data.tar.gz` / `data.tar`)
     * into [targetPrefixDir], stripping the Termux `./data/data/com.termux/files/usr/` prefix.
     */
    fun extractDebIntoPrefix(debFile: File, targetPrefixDir: File): Int {
        var filesWritten = 0
        val canonicalRoot = targetPrefixDir.canonicalFile
        canonicalRoot.mkdirs()

        BufferedInputStream(FileInputStream(debFile), 64 * 1024).use { fileIn ->
            ArArchiveInputStream(fileIn).use { arIn ->
                val baseArIn: org.apache.commons.compress.archivers.ArchiveInputStream<*> = arIn
                var arEntry = baseArIn.nextEntry
                while (arEntry != null) {
                    val entryName = arEntry.name.trim()
                    if (entryName.startsWith("data.tar")) {
                        val tarStream: InputStream = when {
                            entryName.endsWith(".xz") -> XZInputStream(arIn)
                            entryName.endsWith(".gz") -> GzipCompressorInputStream(arIn)
                            else -> arIn
                        }
                        filesWritten += extractTarToDir(
                            tarInput = tarStream,
                            targetDir = canonicalRoot,
                            stripPrefixes = listOf(
                                "./$TERMUX_INTERNAL_USR_PREFIX",
                                TERMUX_INTERNAL_USR_PREFIX,
                                "./usr/",
                                "usr/"
                            )
                        )
                        break
                    }
                    arEntry = baseArIn.nextEntry
                }
            }
        }
        return filesWritten
    }

    fun extractTarArchive(archiveFile: File, destDir: File, onProgress: ((Int, String) -> Unit)? = null): Int {
        val canonicalRoot = destDir.canonicalFile
        canonicalRoot.mkdirs()
        val lowerName = archiveFile.name.lowercase()
        BufferedInputStream(FileInputStream(archiveFile), 64 * 1024).use { rawIn ->
            val uncompressed: InputStream = when {
                lowerName.endsWith(".tar.xz") || lowerName.endsWith(".txz") -> XZInputStream(rawIn)
                lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz") -> GzipCompressorInputStream(rawIn)
                else -> rawIn
            }
            return extractTarToDir(
                tarInput = uncompressed,
                targetDir = canonicalRoot,
                stripPrefixes = listOf("./"),
                onProgress = onProgress
            )
        }
    }

    private fun extractTarToDir(
        tarInput: InputStream,
        targetDir: File,
        stripPrefixes: List<String>,
        onProgress: ((Int, String) -> Unit)? = null
    ): Int {
        var count = 0
        val tarIn = TarArchiveInputStream(tarInput)
        val baseTarIn: org.apache.commons.compress.archivers.ArchiveInputStream<*> = tarIn
        var entry = baseTarIn.nextEntry as? TarArchiveEntry
        while (entry != null) {
            var relPath = entry.name
            for (prefix in stripPrefixes) {
                if (relPath.startsWith(prefix)) {
                    relPath = relPath.removePrefix(prefix)
                    break
                }
            }
            relPath = relPath.removePrefix("./").trimStart('/')

            if (relPath.isNotEmpty() && !relPath.contains("..")) {
                val outFile = File(targetDir, relPath)
                if (outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    when {
                        entry.isSymbolicLink -> {
                            PRootRuntime.createSymlinkCompat(entry.linkName, outFile)
                            count++
                        }
                        entry.isDirectory -> {
                            outFile.mkdirs()
                        }
                        else -> {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                tarIn.copyTo(out)
                            }
                            if ((entry.mode and 0b001_000_000) != 0 ||
                                relPath.startsWith("bin/") ||
                                relPath.startsWith("libexec/") ||
                                relPath.startsWith("lib/") && relPath.contains(".so")
                            ) {
                                PRootRuntime.ensureExecutable(outFile)
                            }
                            count++
                        }
                    }
                    if (count % 150 == 0) {
                        onProgress?.invoke(count, relPath)
                    }
                }
            }
            entry = baseTarIn.nextEntry as? TarArchiveEntry
        }
        return count
    }

    suspend fun installProotRuntime(
        context: Context,
        onLog: ((String) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val log = { msg: String ->
            Log.i(TAG, msg)
            onLog?.invoke(msg)
        }
        try {
            val arch = mapAbiToTermuxArch()
            val runtimeDir = PRootRuntime.getProotRuntimeDir(context)
            runtimeDir.mkdirs()
            val cacheDir = File(context.cacheDir, "proot_debs").apply { mkdirs() }

            _state.value = PRootInstallState.Installing("Fetching Termux APT index ($arch)...", 0.1f)
            log("Fetching PRoot package index ($arch)...")

            val packagesUrlGz = "$TERMUX_APT_BASE/dists/stable/main/binary-$arch/Packages.gz"
            val packagesUrlRaw = "$TERMUX_APT_BASE/dists/stable/main/binary-$arch/Packages"

            val rawIndex = runCatching {
                val req = Request.Builder().url(packagesUrlGz).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    GzipCompressorInputStream(resp.body!!.byteStream()).bufferedReader().readText()
                }
            }.getOrElse {
                val req = Request.Builder().url(packagesUrlRaw).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("APT Packages fetch failed: HTTP ${resp.code}")
                    resp.body!!.string()
                }
            }

            val index = parsePackagesIndex(rawIndex)
            val plan = resolveDependencyOrder(index, listOf("proot"))
            if (plan.isEmpty() || plan.none { it.packageName == "proot" }) {
                error("Could not locate 'proot' package in Termux APT repository ($arch)")
            }

            log("Resolved PRoot packages: ${plan.joinToString { "${it.packageName} (${it.version})" }}")

            plan.forEachIndexed { idx, pkg ->
                val frac = 0.25f + 0.65f * ((idx + 1).toFloat() / plan.size.toFloat())
                _state.value = PRootInstallState.Installing("Installing ${pkg.packageName}...", frac)
                val debUrl = "$TERMUX_APT_BASE/${pkg.filename}"
                val debFile = File(cacheDir, "${pkg.packageName}.deb")
                log("Downloading ${pkg.packageName}...")
                httpClient.newCall(Request.Builder().url(debUrl).build()).execute().use { resp ->
                    if (!resp.isSuccessful) error("Failed downloading ${pkg.packageName}: HTTP ${resp.code}")
                    resp.body!!.byteStream().use { input ->
                        FileOutputStream(debFile).use { output -> input.copyTo(output) }
                    }
                }
                val unpacked = extractDebIntoPrefix(debFile, runtimeDir)
                log("Unpacked ${pkg.packageName} ($unpacked entries)")
                runCatching { debFile.delete() }
            }

            // Ensure all binaries & shared objects in proot-runtime are executable
            listOf("bin", "libexec", "lib").forEach { sub ->
                File(runtimeDir, sub).takeIf { it.exists() }?.walkTopDown()?.forEach { f ->
                    if (f.isFile && !java.nio.file.Files.isSymbolicLink(f.toPath())) {
                        PRootRuntime.ensureExecutable(f)
                    }
                }
            }

            val prootBin = PRootRuntime.resolveProotBinary(context)
                ?: error("PRoot extraction finished but binary not found in ${runtimeDir.absolutePath}")

            // Switch runtime mode to PROOT_TERMUX automatically if previously direct
            if (PRootRuntime.getRuntimeMode(context) == LinuxRuntimeMode.DIRECT_NATIVE &&
                !LocalEnvironmentManager.isTermuxFlavor(context)
            ) {
                PRootRuntime.setRuntimeMode(context, LinuxRuntimeMode.PROOT_TERMUX)
            }

            val msg = "PRoot Runtime ready (${prootBin.length() / 1024} KB, $arch)"
            _state.value = PRootInstallState.Success(msg)
            log("✓ $msg")
            Result.success(prootBin)
        } catch (e: Exception) {
            val errMsg = e.message ?: "PRoot installation error"
            Log.e(TAG, "PRoot install failed", e)
            _state.value = PRootInstallState.Error(errMsg)
            onLog?.invoke("✗ PRoot setup note: $errMsg")
            Result.failure(e)
        }
    }

    suspend fun installUbuntuBaseRootfs(
        context: Context,
        archiveFile: File? = null,
        onLog: ((String) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val log = { msg: String ->
            Log.i(TAG, msg)
            onLog?.invoke(msg)
        }
        try {
            // Ensure PRoot itself is available first
            if (!PRootRuntime.isProotReady(context)) {
                val prootRes = installProotRuntime(context, onLog)
                if (prootRes.isFailure) return@withContext Result.failure(prootRes.exceptionOrNull()!!)
            }

            val ubuntuDir = PRootRuntime.getUbuntuRootfsDir(context)
            ubuntuDir.mkdirs()

            val targetArchive = archiveFile ?: run {
                val uArch = mapAbiToUbuntuArch()
                val url = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.2-base-$uArch.tar.gz"
                val dest = File(context.cacheDir, "ubuntu-base-24.04-$uArch.tar.gz")
                if (!dest.exists() || dest.length() < 10_000_000L) {
                    _state.value = PRootInstallState.Installing("Downloading Ubuntu 24.04 ($uArch)...", 0.25f)
                    log("Downloading Ubuntu 24.04 Base RootFS ($uArch)...")
                    httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                        if (!resp.isSuccessful) error("Ubuntu download failed: HTTP ${resp.code}")
                        val body = resp.body ?: error("Empty Ubuntu archive response")
                        val total = body.contentLength()
                        var copied = 0L
                        body.byteStream().use { input ->
                            FileOutputStream(dest).use { output ->
                                val buf = ByteArray(64 * 1024)
                                var read: Int
                                while (input.read(buf).also { read = it } != -1) {
                                    output.write(buf, 0, read)
                                    copied += read
                                    if (total > 0) {
                                        val frac = 0.1f + 0.5f * (copied.toFloat() / total)
                                        _state.value = PRootInstallState.Installing(
                                            "Downloading Ubuntu (${LocalEnvironmentManager.formatFileSize(copied)} / ${LocalEnvironmentManager.formatFileSize(total)})",
                                            frac
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                dest
            }

            _state.value = PRootInstallState.Installing("Extracting Ubuntu RootFS...", 0.7f)
            log("Extracting ${targetArchive.name} into ${ubuntuDir.absolutePath}...")
            val count = extractTarArchive(targetArchive, ubuntuDir) { extractedCount, path ->
                _state.value = PRootInstallState.Installing("Unpacking Ubuntu ($extractedCount): $path", 0.85f)
            }

            // Configure DNS & hosts inside Ubuntu rootfs
            val etc = File(ubuntuDir, "etc").apply { mkdirs() }
            File(etc, "resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
            File(etc, "hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")
            PRootRuntime.setRuntimeMode(context, LinuxRuntimeMode.PROOT_UBUNTU)

            val msg = "Ubuntu 24.04 RootFS ready ($count entries extracted)"
            _state.value = PRootInstallState.Success(msg)
            log("✓ $msg")
            Result.success(ubuntuDir)
        } catch (e: Exception) {
            val errMsg = e.message ?: "Ubuntu RootFS installation failed"
            Log.e(TAG, "Ubuntu RootFS install failed", e)
            _state.value = PRootInstallState.Error(errMsg)
            Result.failure(e)
        }
    }
}
