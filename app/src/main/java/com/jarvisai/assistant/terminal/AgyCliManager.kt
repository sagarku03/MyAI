package com.jarvisai.assistant.terminal

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class AgyCliInfo(
    val installed: Boolean,
    val path: String,
    val version: String,
    val latestRemoteVersion: String? = null
)

data class AgyCliJob(
    val kind: String,
    val running: Boolean,
    val exitCode: Int? = null,
    val log: String = ""
)

/**
 * Manages the Google Antigravity CLI (`agy`) lifecycle on the device:
 * - Locates and syncs `~/.local/bin/agy` and internal `filesDir/bin/agy`.
 * - Runs `agy --version` (filtering out Android pre-init stderr log lines) with mtime caching.
 * - Installs `agy` via Google's official installer (`https://antigravity.google/cli/install.sh`)
 *   and verified native manifest fallback (`manifests/android_arm64.json` + SHA-512 check).
 * - Updates `agy` via `agy update` (plus verified manifest fallback), detects version upgrades,
 *   and automatically restarts the local Antigravity Hub server so the new `agy` is used immediately.
 */
object AgyCliManager {
    private const val TAG = "AgyCliManager"

    const val OFFICIAL_INSTALL_SCRIPT_URL = "https://antigravity.google/cli/install.sh"
    const val OFFICIAL_UPDATER_BASE_URL = "https://antigravity-cli-auto-updater-974169037036.us-central1.run.app"
    const val OFFICIAL_ANDROID_ARM64_MANIFEST_URL = "$OFFICIAL_UPDATER_BASE_URL/manifests/android_arm64.json"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .dns(com.jarvisai.assistant.ui.updater.AppUpdateManager.sharedResilientDns)
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private val _cliState = MutableStateFlow<AgyCliInfo?>(null)
    val cliState: StateFlow<AgyCliInfo?> = _cliState.asStateFlow()

    private val _jobState = MutableStateFlow<AgyCliJob?>(null)
    val jobState: StateFlow<AgyCliJob?> = _jobState.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _updatedTo = MutableStateFlow<String?>(null)
    val updatedTo: StateFlow<String?> = _updatedTo.asStateFlow()

    private val versionRegex = Regex("""^v?\d+(\.\d+)+\S*$""")
    private val embeddedVersionRegex = Regex("""\b(1\.\d+\.\d+(?:-[0-9A-Za-z._-]+)?)\b""")
    private val updatedLineRegex = Regex("""^Updated agy (\S+) -> (\S+)\.$""", RegexOption.MULTILINE)

    @Volatile
    private var cachedVerPath: String = ""
    @Volatile
    private var cachedVerMtime: Long = -1L
    @Volatile
    private var cachedVerValue: String = ""

    private val jobLock = Any()
    private val logBuilder = StringBuilder()

    fun isAgyStartupLog(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("ERROR: logging before google.Init") ||
            trimmed.contains("Initialized Android local timezone")
    }

    fun cleanAgyLog(raw: String): String {
        if (!raw.contains("logging before google.Init") && !raw.contains("Initialized Android local timezone")) {
            return raw
        }
        return raw.lines()
            .filterNot { isAgyStartupLog(it) }
            .joinToString("\n")
    }

    fun parseVersionFromOutput(raw: String): String {
        val cleaned = cleanAgyLog(raw)
        val exactLine = cleaned.lineSequence()
            .map { it.trim() }
            .firstOrNull { versionRegex.matches(it) }
        if (!exactLine.isNullOrBlank()) {
            return exactLine.removePrefix("v")
        }
        val embedded = embeddedVersionRegex.find(cleaned)?.groupValues?.getOrNull(1)
        return embedded?.removePrefix("v").orEmpty()
    }

    /**
     * Returns the canonical installer path (`~/.local/bin/agy`) inside the active home directory.
     */
    fun getInstallerAgyFile(context: Context): File {
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        val localBin = File(homeDir, ".local/bin").apply { runCatching { mkdirs() } }
        return File(localBin, "agy")
    }

    /**
     * Synchronizes `~/.local/bin/agy` and internal `filesDir/bin/agy` so whichever binary is newer
     * is available at both locations with `0755` permissions.
     */
    fun syncAndResolveAgyBinary(context: Context): File? {
        val localBinAgy = getInstallerAgyFile(context)
        val internalBinDir = File(context.filesDir, "bin").apply { runCatching { mkdirs() } }
        val internalAgy = File(internalBinDir, "agy")
        val prefixBinAgy = File(LocalEnvironmentManager.getBinDir(context), "agy")

        val externalCandidates = listOf(
            File("/data/user/0/com.termux/files/home/.local/bin/agy"),
            File("/data/data/com.termux/files/home/.local/bin/agy"),
            File("/sdcard/Download/${com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_SDCARD_FOLDER}/agy"),
            File("/sdcard/Download/agy")
        )

        // Find all valid existing binaries (> 1 KB)
        val validExisting = buildList {
            if (localBinAgy.exists() && localBinAgy.isFile && localBinAgy.length() > 1000) add(localBinAgy)
            if (internalAgy.exists() && internalAgy.isFile && internalAgy.length() > 1000) add(internalAgy)
            if (prefixBinAgy.exists() && prefixBinAgy.isFile && prefixBinAgy.length() > 1000) add(prefixBinAgy)
            externalCandidates.forEach { ext ->
                if (ext.exists() && ext.isFile && ext.length() > 1000 && ext.canRead()) add(ext)
            }
        }

        if (validExisting.isEmpty()) {
            return null
        }

        // Pick the newest binary by lastModified timestamp
        val newest = validExisting.maxByOrNull { it.lastModified() } ?: validExisting.first()

        // Sync newest binary into both ~/.local/bin/agy and filesDir/bin/agy
        listOf(localBinAgy, internalAgy).forEach { target ->
            runCatching {
                target.parentFile?.mkdirs()
                if (newest.absolutePath != target.absolutePath) {
                    val needsCopy = !target.exists() ||
                        target.length() != newest.length() ||
                        newest.lastModified() > target.lastModified()
                    if (needsCopy) {
                        val tmp = File(target.parentFile, "${target.name}.sync.tmp")
                        newest.copyTo(tmp, overwrite = true)
                        ensureExecutablePermissions(tmp)
                        if (target.exists()) target.delete()
                        tmp.renameTo(target)
                    }
                }
                ensureExecutablePermissions(target)
            }
        }

        return when {
            localBinAgy.exists() && localBinAgy.length() > 1000 -> localBinAgy
            internalAgy.exists() && internalAgy.length() > 1000 -> internalAgy
            else -> newest
        }
    }

    private fun ensureExecutablePermissions(file: File) {
        runCatching {
            file.setReadable(true, false)
            file.setExecutable(true, false)
            Os.chmod(file.absolutePath, 493) // 0755
        }
    }

    fun invalidateVersionCache() {
        cachedVerPath = ""
        cachedVerMtime = -1L
        cachedVerValue = ""
    }

    /**
     * Runs `agy --version` (cached by file path + lastModified) and returns the clean version string (e.g. `1.5.10`).
     */
    suspend fun readAgyVersion(context: Context, binaryFile: File? = null): String = withContext(Dispatchers.IO) {
        val resolved = binaryFile ?: syncAndResolveAgyBinary(context) ?: return@withContext ""
        if (!resolved.exists() || !resolved.isFile || resolved.length() <= 1000) return@withContext ""

        val absPath = resolved.absolutePath
        val mtime = resolved.lastModified()
        if (cachedVerPath == absPath && cachedVerMtime == mtime && cachedVerValue.isNotBlank()) {
            return@withContext cachedVerValue
        }

        ensureExecutablePermissions(resolved)

        //Also check if internalAgy is executable if resolved is in home/.local/bin
        val internalAgy = File(File(context.filesDir, "bin"), "agy")
        val execCandidates = listOfNotNull(
            resolved,
            internalAgy.takeIf { it.exists() && it.absolutePath != resolved.absolutePath }
        )

        for (candidate in execCandidates) {
            val ver = runCatching {
                ensureExecutablePermissions(candidate)
                val pb = ProcessBuilder(candidate.absolutePath, "--version")
                pb.directory(LocalEnvironmentManager.getHomeDir(context))
                LocalEnvironmentManager.applyIsolatedToolEnvironment(context, pb.environment())
                val proc = pb.start()
                val stdout = proc.inputStream.bufferedReader().use { it.readText() }
                val stderr = proc.errorStream.bufferedReader().use { it.readText() }
                proc.waitFor(8, TimeUnit.SECONDS)
                parseVersionFromOutput(stdout).ifBlank { parseVersionFromOutput(stderr) }
            }.getOrDefault("")

            if (ver.isNotBlank()) {
                cachedVerPath = absPath
                cachedVerMtime = mtime
                cachedVerValue = ver
                return@withContext ver
            }
        }

        // Fallback: scan binary or companion version metadata if process execution is restricted
        val sidecarVerFile = File(resolved.parentFile, ".agy_version")
        if (sidecarVerFile.exists()) {
            val v = runCatching { sidecarVerFile.readText().trim() }.getOrDefault("")
            if (v.isNotBlank()) {
                cachedVerPath = absPath
                cachedVerMtime = mtime
                cachedVerValue = v
                return@withContext v
            }
        }

        ""
    }

    fun refreshAgyCli(context: Context) {
        val appCtx = context.applicationContext
        scope.launch {
            _checking.value = true
            try {
                val resolved = syncAndResolveAgyBinary(appCtx)
                val defaultPath = getInstallerAgyFile(appCtx).absolutePath
                if (resolved != null && resolved.exists()) {
                    val ver = readAgyVersion(appCtx, resolved)
                    _cliState.value = AgyCliInfo(
                        installed = true,
                        path = resolved.absolutePath,
                        version = ver
                    )
                } else {
                    _cliState.value = AgyCliInfo(
                        installed = false,
                        path = defaultPath,
                        version = ""
                    )
                }
                _error.value = null
                LocalServerManager.refreshDiagnostics(appCtx)
            } catch (e: Exception) {
                Log.w(TAG, "refreshAgyCli error: ${e.message}")
                _error.value = "Failed to inspect agy CLI: ${e.message}"
            } finally {
                _checking.value = false
            }
        }
    }

    /**
     * Returns a snapshot JSON matching AntiGem's `GET /api/agy/cli` response schema.
     */
    suspend fun getCliStatusSnapshotJson(context: Context): JSONObject = withContext(Dispatchers.IO) {
        val resolved = syncAndResolveAgyBinary(context)
        val installed = resolved != null && resolved.exists()
        val path = resolved?.absolutePath ?: getInstallerAgyFile(context).absolutePath
        val version = if (installed) readAgyVersion(context, resolved) else ""
        _cliState.value = AgyCliInfo(installed = installed, path = path, version = version)

        JSONObject().apply {
            put("success", true)
            put("installed", installed)
            put("path", path)
            put("version", version)
            _jobState.value?.let { j ->
                put(
                    "job",
                    JSONObject().apply {
                        put("kind", j.kind)
                        put("running", j.running)
                        if (j.exitCode != null) put("exit_code", j.exitCode)
                        put("log", j.log)
                    }
                )
            }
        }
    }

    private fun appendJobLog(kind: String, line: String, running: Boolean = true, exitCode: Int? = null) {
        synchronized(jobLock) {
            val cleaned = cleanAgyLog(line)
            if (cleaned.isNotBlank()) {
                if (logBuilder.isNotEmpty() && !logBuilder.endsWith("\n")) {
                    logBuilder.append('\n')
                }
                logBuilder.append(cleaned.trimEnd())
                if (logBuilder.length > 64 * 1024) {
                    val tail = logBuilder.substring(logBuilder.length - 32 * 1024)
                    logBuilder.setLength(0)
                    logBuilder.append(tail)
                }
            }
            _jobState.value = AgyCliJob(
                kind = kind,
                running = running,
                exitCode = exitCode,
                log = logBuilder.toString()
            )
        }
    }

    /**
     * Installs `agy` into `~/.local/bin/agy` (and syncs to `filesDir/bin/agy`) using Google's official
     * installer (`https://antigravity.google/cli/install.sh`) with a verified native manifest fallback.
     */
    fun installAgy(context: Context, onFinished: (Boolean) -> Unit = {}) {
        val appCtx = context.applicationContext
        synchronized(jobLock) {
            val current = _jobState.value
            if (current != null && current.running) {
                _error.value = "An agy ${current.kind} is already running"
                return
            }
            logBuilder.setLength(0)
            _error.value = null
            _updatedTo.value = null
            _jobState.value = AgyCliJob(kind = "install", running = true, exitCode = null, log = "")
        }

        scope.launch {
            var exitCode = 1
            try {
                val targetFile = getInstallerAgyFile(appCtx)
                val targetDir = targetFile.parentFile ?: File(LocalEnvironmentManager.getHomeDir(appCtx), ".local/bin").apply { mkdirs() }

                // First try the verified native manifest installer (works on all Android devices even without external Termux)
                // while emitting the exact official installer step logs, or shell install.sh when bash+curl are present.
                val installedVersion = installOrUpdateViaOfficialManifest(
                    context = appCtx,
                    targetFile = targetFile,
                    kind = "install",
                    currentVersion = ""
                )

                if (installedVersion.isNotBlank() && targetFile.exists() && targetFile.length() > 1000) {
                    exitCode = 0
                } else {
                    // Fallback: run official install.sh directly in shell
                    appendJobLog("install", "⟳ Running official installer script (antigravity.google/cli/install.sh)...")
                    val shell = LocalEnvironmentManager.getDefaultShell(appCtx)
                    val cmd = "curl -fsSL $OFFICIAL_INSTALL_SCRIPT_URL | sh -s -- --dir \"${targetDir.absolutePath}\""
                    val pb = ProcessBuilder(shell, "-c", cmd)
                    pb.directory(LocalEnvironmentManager.getHomeDir(appCtx))
                    LocalEnvironmentManager.applyIsolatedToolEnvironment(appCtx, pb.environment())
                    pb.redirectErrorStream(true)
                    val proc = pb.start()
                    proc.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line -> appendJobLog("install", line) }
                    }
                    exitCode = proc.waitFor()
                }

                invalidateVersionCache()
                val synced = syncAndResolveAgyBinary(appCtx)
                val finalVer = readAgyVersion(appCtx, synced)
                val installedOk = exitCode == 0 && synced != null && synced.exists()

                if (installedOk) {
                    val verLabel = finalVer.ifBlank { "latest" }
                    appendJobLog("install", "✓ Installed agy $verLabel to ${synced!!.absolutePath}", running = false, exitCode = 0)
                    _cliState.value = AgyCliInfo(
                        installed = true,
                        path = synced.absolutePath,
                        version = finalVer
                    )
                    if (finalVer.isNotBlank()) {
                        _updatedTo.value = finalVer
                    }
                    LocalServerManager.restartAgyHubAfterCliUpdate(appCtx, finalVer)
                    showMainToast(appCtx, "Installed agy $verLabel. Starting Antigravity server…")
                } else {
                    appendJobLog("install", "✗ Installation exited with code $exitCode", running = false, exitCode = exitCode)
                }
                onFinished(installedOk)
            } catch (e: Exception) {
                Log.e(TAG, "installAgy failed", e)
                appendJobLog("install", "✗ Error: ${e.message}", running = false, exitCode = 1)
                _error.value = "Install failed: ${e.message}"
                onFinished(false)
            }
        }
    }

    /**
     * Runs `agy update` (and verifies against Google's official Android ARM64 release manifest).
     * When a newer `agy` version is installed, automatically restarts the local Antigravity Hub server.
     */
    fun updateAgy(context: Context, onFinished: (Boolean) -> Unit = {}) {
        val appCtx = context.applicationContext
        synchronized(jobLock) {
            val current = _jobState.value
            if (current != null && current.running) {
                _error.value = "An agy ${current.kind} is already running"
                return
            }
            logBuilder.setLength(0)
            _error.value = null
            _updatedTo.value = null
            _jobState.value = AgyCliJob(kind = "update", running = true, exitCode = null, log = "")
        }

        scope.launch {
            var exitCode = 0
            try {
                val existingBin = syncAndResolveAgyBinary(appCtx)
                val targetFile = existingBin ?: getInstallerAgyFile(appCtx)
                val beforeVersion = if (targetFile.exists()) readAgyVersion(appCtx, targetFile) else ""

                // 1. If binary exists, try running native `agy update` first and stream its live output
                var nativeUpdateSucceeded = false
                if (targetFile.exists() && targetFile.length() > 1000) {
                    runCatching {
                        ensureExecutablePermissions(targetFile)
                        val pb = ProcessBuilder(targetFile.absolutePath, "update")
                        pb.directory(LocalEnvironmentManager.getHomeDir(appCtx))
                        val env = pb.environment()
                        LocalEnvironmentManager.applyIsolatedToolEnvironment(appCtx, env)
                        env.remove("AGY_NO_UPDATE_PROMPT")
                        pb.redirectErrorStream(true)
                        val proc = pb.start()
                        proc.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                val cleaned = cleanAgyLog(line).trim()
                                if (cleaned.isNotBlank()) {
                                    appendJobLog("update", cleaned)
                                }
                            }
                        }
                        val code = proc.waitFor()
                        if (code == 0) {
                            nativeUpdateSucceeded = true
                        }
                    }.onFailure { err ->
                        Log.d(TAG, "Direct `agy update` subprocess notice: ${err.message}")
                    }
                }

                invalidateVersionCache()
                var afterVersion = if (targetFile.exists()) readAgyVersion(appCtx, targetFile) else ""

                // 2. Check Google's official Android ARM64 release manifest to ensure we have the newest build
                // (or perform the update if `agy update` could not self-overwrite on Android SELinux)
                val manifestResult = runCatching {
                    fetchOfficialManifest()
                }.getOrNull()

                val remoteVersion = manifestResult?.optString("version", "")?.trim().orEmpty()
                val needsManifestDownload = !nativeUpdateSucceeded ||
                    afterVersion.isBlank() ||
                    (remoteVersion.isNotBlank() && compareSemanticVersions(remoteVersion, afterVersion) > 0)

                if (needsManifestDownload && manifestResult != null) {
                    val downloadedVer = installOrUpdateViaOfficialManifest(
                        context = appCtx,
                        targetFile = getInstallerAgyFile(appCtx),
                        kind = "update",
                        currentVersion = beforeVersion,
                        preFetchedManifest = manifestResult
                    )
                    if (downloadedVer.isNotBlank()) {
                        afterVersion = downloadedVer
                        exitCode = 0
                    } else if (!nativeUpdateSucceeded) {
                        exitCode = 1
                    }
                } else if (nativeUpdateSucceeded && logBuilder.isEmpty()) {
                    val verText = afterVersion.ifBlank { beforeVersion.ifBlank { "latest" } }
                    appendJobLog("update", "⟳ Checking for updates... (current version $verText)")
                    appendJobLog("update", "✓ agy $verText is the latest version.")
                }

                invalidateVersionCache()
                val synced = syncAndResolveAgyBinary(appCtx)
                if (afterVersion.isBlank() && synced != null) {
                    afterVersion = readAgyVersion(appCtx, synced)
                }

                if (exitCode == 0 && synced != null && synced.exists()) {
                    when {
                        afterVersion.isNotBlank() && beforeVersion.isNotBlank() && afterVersion != beforeVersion -> {
                            appendJobLog(
                                "update",
                                "Updated agy $beforeVersion -> $afterVersion.\nThe running hub keeps using $beforeVersion until the server restarts.",
                                running = false,
                                exitCode = 0
                            )
                            _updatedTo.value = afterVersion
                            _cliState.value = AgyCliInfo(
                                installed = true,
                                path = synced.absolutePath,
                                version = afterVersion,
                                latestRemoteVersion = remoteVersion.ifBlank { afterVersion }
                            )
                            LocalServerManager.restartAgyHubAfterCliUpdate(appCtx, afterVersion)
                            showMainToast(appCtx, "Updated to agy $afterVersion. The server restarted to use it.")
                        }
                        afterVersion.isNotBlank() && beforeVersion.isBlank() -> {
                            appendJobLog(
                                "update",
                                "Updated agy -> $afterVersion.",
                                running = false,
                                exitCode = 0
                            )
                            _updatedTo.value = afterVersion
                            _cliState.value = AgyCliInfo(
                                installed = true,
                                path = synced.absolutePath,
                                version = afterVersion,
                                latestRemoteVersion = remoteVersion.ifBlank { afterVersion }
                            )
                            LocalServerManager.restartAgyHubAfterCliUpdate(appCtx, afterVersion)
                            showMainToast(appCtx, "Updated to agy $afterVersion. The server restarted to use it.")
                        }
                        afterVersion.isNotBlank() -> {
                            if (!logBuilder.toString().contains("latest version") && !logBuilder.toString().contains("Update successful")) {
                                appendJobLog("update", "✓ agy $afterVersion is the latest version.")
                            }
                            appendJobLog("update", "", running = false, exitCode = 0)
                            _cliState.value = AgyCliInfo(
                                installed = true,
                                path = synced.absolutePath,
                                version = afterVersion,
                                latestRemoteVersion = remoteVersion.ifBlank { afterVersion }
                            )
                            LocalServerManager.refreshDiagnostics(appCtx)
                        }
                        else -> {
                            appendJobLog("update", "", running = false, exitCode = 0)
                            LocalServerManager.refreshDiagnostics(appCtx)
                        }
                    }
                    onFinished(true)
                } else {
                    appendJobLog("update", "✗ Update finished with exit code $exitCode", running = false, exitCode = exitCode)
                    onFinished(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "updateAgy failed", e)
                appendJobLog("update", "✗ Update error: ${e.message}", running = false, exitCode = 1)
                _error.value = "Update failed: ${e.message}"
                onFinished(false)
            }
        }
    }

    private fun fetchOfficialManifest(): JSONObject {
        val req = Request.Builder()
            .url(OFFICIAL_ANDROID_ARM64_MANIFEST_URL)
            .header("User-Agent", "Antigravity-CLI-Updater/1.5")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Release server returned HTTP ${resp.code}")
            }
            val body = resp.body?.string().orEmpty()
            return JSONObject(body)
        }
    }

    /**
     * Downloads, SHA-512 verifies, and extracts the official `android_arm64` Antigravity CLI release
     * from Google's release server (`antigravity-cli-auto-updater`), writing the exact progress format
     * shown in AntiGem's Antigravity CLI screen.
     */
    private suspend fun installOrUpdateViaOfficialManifest(
        context: Context,
        targetFile: File,
        kind: String,
        currentVersion: String,
        preFetchedManifest: JSONObject? = null
    ): String = withContext(Dispatchers.IO) {
        val curVerDisplay = currentVersion.ifBlank { "none" }
        if (kind == "update") {
            if (!logBuilder.toString().contains("Checking for updates")) {
                appendJobLog(kind, "⟳ Checking for updates... (current version $curVerDisplay)")
            }
        } else {
            appendJobLog(kind, "⟳ Detecting system environment... (android_arm64)")
            appendJobLog(kind, "⟳ Checking for updates...")
        }

        val manifest = preFetchedManifest ?: fetchOfficialManifest()
        val version = manifest.optString("version", "").trim()
        val downloadUrl = manifest.optString("url", "").trim()
        val expectedSha512 = manifest.optString("sha512", "").trim().lowercase()

        if (version.isBlank() || downloadUrl.isBlank() || expectedSha512.isBlank()) {
            throw IllegalStateException("Malformed release manifest from $OFFICIAL_ANDROID_ARM64_MANIFEST_URL")
        }

        if (kind == "update" && currentVersion.isNotBlank() && compareSemanticVersions(version, currentVersion) <= 0 &&
            targetFile.exists() && targetFile.length() > 1000
        ) {
            appendJobLog(kind, "✓ agy $currentVersion is already up to date.")
            return@withContext currentVersion
        }

        appendJobLog(kind, "✓ Found new version $version.")
        appendJobLog(kind, "⟳ Downloading update...")

        val stagingDir = File(context.cacheDir, "antigravity_staging").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val archiveFile = File(stagingDir, if (downloadUrl.contains(".tar.gz")) "agy.tar.gz" else "agy")

        val req = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "Antigravity-CLI-Updater/$version")
            .get()
            .build()

        val digest = MessageDigest.getInstance("SHA-512")
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Failed downloading release package: HTTP ${resp.code}")
            }
            val body = resp.body ?: throw IllegalStateException("Empty release payload")
            body.byteStream().use { input ->
                FileOutputStream(archiveFile).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        digest.update(buf, 0, read)
                    }
                }
            }
        }

        val actualSha512 = digest.digest().joinToString("") { "%02x".format(it) }.lowercase()
        if (actualSha512 != expectedSha512) {
            archiveFile.delete()
            throw SecurityException("SHA-512 checksum mismatch for agy $version")
        }

        appendJobLog(kind, "⟳ Extracting files...")
        val extractedBin = File(stagingDir, "antigravity_bin")
        if (archiveFile.name.endsWith(".tar.gz")) {
            FileInputStream(archiveFile).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    GzipCompressorInputStream(bis).use { gzis ->
                        TarArchiveInputStream(gzis).use { tis ->
                            var entry = tis.nextEntry
                            var found = false
                            while (entry != null) {
                                if (!entry.isDirectory && (entry.name.endsWith("antigravity") || entry.name.endsWith("agy") || entry.size > 5_000_000L)) {
                                    FileOutputStream(extractedBin).use { out ->
                                        tis.copyTo(out)
                                    }
                                    found = true
                                    break
                                }
                                entry = tis.nextEntry
                            }
                            if (!found) {
                                throw IllegalStateException("Binary entry 'antigravity' not found inside release archive")
                            }
                        }
                    }
                }
            }
        } else {
            archiveFile.copyTo(extractedBin, overwrite = true)
        }

        appendJobLog(kind, "✓ Verification successful.")
        appendJobLog(kind, "⟳ Installing update...")

        val destinations = listOf(
            targetFile,
            getInstallerAgyFile(context),
            File(File(context.filesDir, "bin").apply { mkdirs() }, "agy"),
            File(LocalEnvironmentManager.getBinDir(context), "agy")
        ).distinctBy { it.absolutePath }

        destinations.forEach { dest ->
            runCatching {
                dest.parentFile?.mkdirs()
                val tmp = File(dest.parentFile, "${dest.name}.new")
                extractedBin.copyTo(tmp, overwrite = true)
                ensureExecutablePermissions(tmp)
                if (dest.exists()) dest.delete()
                tmp.renameTo(dest)
                ensureExecutablePermissions(dest)
                File(dest.parentFile, ".agy_version").writeText(version)
            }
        }

        // Run `agy install --dir ~/.local/bin` non-fatally just like Google's official install.sh
        runCatching {
            val installerBin = getInstallerAgyFile(context)
            val pb = ProcessBuilder(installerBin.absolutePath, "install", "--dir", installerBin.parentFile!!.absolutePath)
            pb.directory(LocalEnvironmentManager.getHomeDir(context))
            LocalEnvironmentManager.applyIsolatedToolEnvironment(context, pb.environment())
            val p = pb.start()
            p.waitFor(5, TimeUnit.SECONDS)
        }

        runCatching { stagingDir.deleteRecursively() }
        appendJobLog(kind, "✓ Update successful! Please restart agy.")
        version
    }

    fun compareSemanticVersions(a: String, b: String): Int {
        val cleanA = a.trim().removePrefix("v")
        val cleanB = b.trim().removePrefix("v")
        if (cleanA == cleanB) return 0
        val partsA = cleanA.split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
        val partsB = cleanB.split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val x = partsA.getOrElse(i) { 0 }
            val y = partsB.getOrElse(i) { 0 }
            if (x != y) return if (x > y) 1 else -1
        }
        return 0
    }

    private fun showMainToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            runCatching {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
