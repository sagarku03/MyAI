package com.jarvisai.assistant.terminal

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.util.concurrent.TimeUnit

sealed class LocalServerStatus {
    object Idle : LocalServerStatus()
    object Starting : LocalServerStatus()
    data class Running(val port: Int, val startTimeMs: Long = System.currentTimeMillis()) : LocalServerStatus()
    object FallbackPTY : LocalServerStatus()
    object Stopping : LocalServerStatus()
    data class Stopped(val exitCode: Int? = null) : LocalServerStatus()
    data class Error(val message: String) : LocalServerStatus()
}

/**
 * Robust Go IDE Server & Multi-Agent Daemon Supervisor.
 * Implements a 4-Tier Zero-Crash binary discovery strategy:
 * 1. Checks $PREFIX/bin/agy_ide_bridge.
 * 2. Checks /sdcard/agy_ide_bridge or $HOME/bin/agy_ide_bridge.
 * 3. Unpacks assets/bin/agy_ide_bridge.xz if packaged.
 * 4. Gracefully falls back to LocalServerStatus.FallbackPTY if binary is absent,
 *    allowing AgentOrchestrator and LocalTerminalBridge to execute tasks via native PTY bash.
 */
object LocalServerManager {

    private const val TAG = "LocalServerManager"
    private const val BRIDGE_ASSET = "agy_ide_bridge.xz"
    private const val BRIDGE_BINARY_NAME = "agy_ide_bridge"

    private val _status = MutableStateFlow<LocalServerStatus>(LocalServerStatus.Idle)
    val status: StateFlow<LocalServerStatus> = _status.asStateFlow()

    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var supervisorProcess: Process? = null
    private var agyHubProcess: Process? = null
    private var healthCheckJob: Job? = null
    @Volatile
    private var cachedAppContext: Context? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    fun getBridgePort(context: Context): Int {
        return com.jarvisai.assistant.data.remote.JarvisHubConfig.getBridgePort(context)
    }

    fun getBridgeUrl(context: Context): String {
        return "http://127.0.0.1:${getBridgePort(context)}"
    }

    fun getHubPort(context: Context? = cachedAppContext): Int {
        return com.jarvisai.assistant.data.remote.JarvisHubConfig.getHubPort(context)
    }

    fun getHubUrl(context: Context? = cachedAppContext): String {
        return "http://127.0.0.1:${getHubPort(context)}"
    }

    /**
     * Automatically starts the Go IDE bridge and the isolated Antigravity Hub daemon (port 2235)
     * in the background when the app launches.
     */
    fun autoStartOnAppLaunch(context: Context) {
        cachedAppContext = context.applicationContext
        serverScope.launch {
            startServer(context.applicationContext)
        }
    }

    suspend fun warmUpAgyHub(maxWaitMs: Long = 10000, context: Context? = cachedAppContext): Boolean {
        return ensureAgyHubAvailable(context)
    }

    fun hasTermuxCommandPermission(context: Context? = cachedAppContext): Boolean {
        // Return true so API layer never blocks localhost HTTP connections
        return true
    }

    /**
     * Ensures the local isolated Antigravity Hub at 127.0.0.1:2235 is reachable, starting it if needed.
     */
    suspend fun ensureAgyHubAvailable(context: Context? = cachedAppContext): Boolean = withContext(Dispatchers.IO) {
        if (isAgyHubReachable()) return@withContext true
        val ctx = context ?: cachedAppContext ?: return@withContext false
        val agyBin = resolveAgyBinary(ctx)
        ensureAgyHubDaemonRunning(ctx, agyBin)
        // Wait up to 10 seconds (20 iterations x 500ms) for isolated hub port to finish binding
        repeat(20) {
            if (isAgyHubReachable()) return@withContext true
            delay(500)
        }
        isAgyHubReachable()
    }

    fun isAgyHubReachable(): Boolean {
        return try {
            val hubPort = getHubPort(cachedAppContext)
            val req = Request.Builder().url("http://127.0.0.1:$hubPort/").get().build()
            httpClient.newCall(req).execute().use { resp ->
                resp.code in 200..499
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveAgyBinary(context: Context): File? {
        val binDir = File(context.filesDir, "bin").apply { mkdirs() }
        val internalAgy = File(binDir, "agy")

        // 1. If already installed in private app storage, ensure executable and return
        if (internalAgy.exists() && internalAgy.isFile && internalAgy.length() > 1000) {
            runCatching { internalAgy.setExecutable(true, false) }
            bootstrapAuthTokens(context)
            return internalAgy
        }

        // 2. Check public Downloads folder (/sdcard/Download/agy) and copy to internal storage
        val sdcardAgy = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "agy")
        if (sdcardAgy.exists() && sdcardAgy.isFile && sdcardAgy.length() > 1000) {
            try {
                sdcardAgy.copyTo(internalAgy, overwrite = true)
                internalAgy.setExecutable(true, false)
                Log.i(TAG, "Successfully copied standalone agy binary to ${internalAgy.absolutePath} (${internalAgy.length()} bytes)")
                bootstrapAuthTokens(context)
                return internalAgy
            } catch (e: Exception) {
                Log.w(TAG, "Failed copying agy from downloads: ${e.message}")
            }
        }

        // 3. Fallback scan Termux paths
        val candidates = listOf(
            File("/data/data/com.termux/files/home/.local/bin/agy"),
            File("/data/user/0/com.termux/files/home/.local/bin/agy"),
            File(LocalEnvironmentManager.getHomeDir(context), ".local/bin/agy"),
            File(LocalEnvironmentManager.getBinDir(context), "agy")
        )
        for (candidate in candidates) {
            if (candidate.exists() && candidate.isFile && candidate.length() > 1000) {
                return candidate
            }
        }
        return null
    }

    private fun bootstrapAuthTokens(context: Context) {
        try {
            val dataDirName = com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_APP_DATA_DIR // "jarvis-antigravity"
            val homeDir = File(context.filesDir, "home").apply { mkdirs() }
            val geminiDir = File(homeDir, ".gemini").apply { mkdirs() }
            val isolatedGeminiDir = File(geminiDir, dataDirName).apply { mkdirs() }

            val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val sdcardGemini = File(downloadDir, ".gemini")

            // Candidate source paths where the user or Termux may have placed valid tokens
            val sourceTokenCandidates = listOf(
                File(sdcardGemini, "jetski-standalone-oauth-token"),
                File("/data/data/com.termux/files/home/.gemini/jetski-standalone-oauth-token"),
                File("/data/user/0/com.termux/files/home/.gemini/jetski-standalone-oauth-token"),
                File(geminiDir, "jetski-standalone-oauth-token")
            )

            val sourceSettingsCandidates = listOf(
                File(sdcardGemini, "settings.json"),
                File("/data/data/com.termux/files/home/.gemini/settings.json"),
                File("/data/user/0/com.termux/files/home/.gemini/settings.json"),
                File(geminiDir, "settings.json")
            )

            val validTokenSource = sourceTokenCandidates.firstOrNull { it.exists() && it.isFile && it.length() > 20 }
            val validSettingsSource = sourceSettingsCandidates.firstOrNull { it.exists() && it.isFile && it.length() > 5 }

            // Target directories that MUST contain the auth token and settings
            val targetDirs = listOf(
                geminiDir,
                isolatedGeminiDir,
                File("/data/data/com.termux/files/home/.gemini"),
                File("/data/data/com.termux/files/home/.gemini/$dataDirName"),
                File("/data/user/0/com.termux/files/home/.gemini"),
                File("/data/user/0/com.termux/files/home/.gemini/$dataDirName")
            )

            if (validTokenSource != null) {
                targetDirs.forEach { dir ->
                    try {
                        if (!dir.exists()) dir.mkdirs()
                        val destToken = File(dir, "jetski-standalone-oauth-token")
                        if (!destToken.exists() || destToken.length() != validTokenSource.length()) {
                            validTokenSource.copyTo(destToken, overwrite = true)
                            runCatching { destToken.setReadable(true, false) }
                            Log.i(TAG, "Bootstrapped OAuth token to ${destToken.absolutePath}")
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Notice copying token to ${dir.absolutePath}: ${e.message}")
                    }
                }
            } else {
                Log.w(TAG, "No disk jetski-standalone-oauth-token found in candidates; syncing from AuthPreferences if signed in.")
                syncAuthPreferencesToAgyDirs(context)
            }

            if (validSettingsSource != null) {
                targetDirs.forEach { dir ->
                    try {
                        if (!dir.exists()) dir.mkdirs()
                        val destSettings = File(dir, "settings.json")
                        if (!destSettings.exists()) {
                            validSettingsSource.copyTo(destSettings, overwrite = true)
                            runCatching { destSettings.setReadable(true, false) }
                            Log.i(TAG, "Bootstrapped settings.json to ${destSettings.absolutePath}")
                        }
                    } catch (_: Exception) {}
                }
            } else {
                listOf(geminiDir, isolatedGeminiDir).forEach { dir ->
                    runCatching {
                        val settingsFile = File(dir, "settings.json")
                        if (!settingsFile.exists()) {
                            settingsFile.writeText("{}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Token bootstrap exception: ${e.message}")
        }
    }

    /**
     * Bridges Jarvis AI's in-app Google PKCE OAuth session (AuthPreferences) onto disk inside
     * the isolated jarvis-antigravity directory so the local agy daemon can read it.
     */
    fun syncAuthPreferencesToAgyDirs(context: Context) {
        try {
            val authPrefs = com.jarvisai.assistant.data.preferences.AuthPreferences.getInstance(context)
            val accessToken = authPrefs.getAccessToken()
            val refreshToken = authPrefs.getRefreshToken()
            if (accessToken.isBlank() && refreshToken.isBlank()) return

            val dataDirName = com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_APP_DATA_DIR
            val homeDir = File(context.filesDir, "home").apply { mkdirs() }
            val geminiDir = File(homeDir, ".gemini").apply { mkdirs() }
            val isolatedGeminiDir = File(geminiDir, dataDirName).apply { mkdirs() }

            val tokenPayload = org.json.JSONObject().apply {
                if (accessToken.isNotBlank()) put("access_token", accessToken)
                if (refreshToken.isNotBlank()) put("refresh_token", refreshToken)
                put("token_type", "Bearer")
                put("client_id", com.jarvisai.assistant.data.remote.GoogleOAuthManager.CLIENT_ID)
                put("client_secret", com.jarvisai.assistant.data.remote.GoogleOAuthManager.CLIENT_SECRET)
                val email = authPrefs.getUserEmail()
                if (email.isNotBlank()) put("email", email)
            }.toString(2)

            listOf(geminiDir, isolatedGeminiDir).forEach { dir ->
                runCatching {
                    if (!dir.exists()) dir.mkdirs()
                    val destToken = File(dir, "jetski-standalone-oauth-token")
                    destToken.writeText(tokenPayload)
                    destToken.setReadable(true, false)
                    val settingsFile = File(dir, "settings.json")
                    if (!settingsFile.exists()) {
                        settingsFile.writeText("{}")
                    }
                    Log.i(TAG, "Synced AuthPreferences OAuth token to ${destToken.absolutePath}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "syncAuthPreferencesToAgyDirs notice: ${e.message}")
        }
    }

    /**
     * Restarts Jarvis's internally-managed agy hub daemon so freshly signed-in OAuth credentials
     * take effect immediately on port 2235.
     */
    fun restartAgyHubForAuth(context: Context) {
        serverScope.launch {
            try {
                syncAuthPreferencesToAgyDirs(context)
                agyHubProcess?.let { proc ->
                    if (proc.isAlive) {
                        Log.i(TAG, "Restarting internal Antigravity Hub daemon to pick up refreshed OAuth session...")
                        proc.destroyForcibly()
                    }
                }
                agyHubProcess = null
                delay(400)
                ensureAgyHubAvailable(context)
            } catch (e: Exception) {
                Log.w(TAG, "restartAgyHubForAuth notice: ${e.message}")
            }
        }
    }

    fun ensureAgyHubDaemonRunning(context: Context, agyBin: File? = null) {
        bootstrapAuthTokens(context)
        val hubPort = getHubPort(context)
        val dataDirName = com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_APP_DATA_DIR

        if (isAgyHubReachable()) {
            Log.i(TAG, "Antigravity Hub already active on 127.0.0.1:$hubPort")
            return
        }

        val resolvedBin = agyBin ?: resolveAgyBinary(context)

        // Strategy 1: Direct Internal Execution via targetSdk 28 (AntiGem Parity)
        if (resolvedBin != null && resolvedBin.exists()) {
            try {
                resolvedBin.setExecutable(true, false)
                val homeDir = File(context.filesDir, "home").apply { mkdirs() }
                val tmpDir = File(context.filesDir, "tmp").apply { mkdirs() }
                val binDir = File(context.filesDir, "bin").apply { mkdirs() }

                val pb = ProcessBuilder(
                    resolvedBin.absolutePath,
                    "--hub",
                    "--hub-port=$hubPort",
                    "--app_data_dir=$dataDirName"
                )
                pb.directory(homeDir)
                val env = pb.environment()
                env["HOME"] = homeDir.absolutePath
                env["PREFIX"] = "${context.filesDir.absolutePath}/usr"
                env["TMPDIR"] = tmpDir.absolutePath
                env["AGY_ENABLE_HUB"] = "1"
                env["ANTIGRAVITY_VSCODE_HOST"] = "1"
                env["ANTIGRAVITY_AUTH_SUCCESS_APP"] = "vscode"
                env["ANTIGRAVITY_APP_DATA_DIR"] = dataDirName
                env["CI"] = "1"
                env["AGY_NO_UPDATE_PROMPT"] = "1"
                env["PATH"] = "${binDir.absolutePath}:/system/bin:/system/xbin"

                pb.redirectErrorStream(true)
                val proc = pb.start()
                agyHubProcess = proc
                serverScope.launch {
                    try {
                        proc.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                Log.d("AgyHubDaemon", line)
                            }
                        }
                    } catch (_: Exception) {}
                }
                Log.i(TAG, "Started self-contained Antigravity Hub daemon (pid alive) on port 127.0.0.1:$hubPort")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Internal ProcessBuilder start attempt: ${e.message}")
            }
        }

        // Strategy 2: Termux RUN_COMMAND Bridge fallback
        val termuxBridge = com.jarvisai.assistant.termux.JarvisTermuxBridge(context)
        if (termuxBridge.isTermuxInstalled() && termuxBridge.hasRunCommandPermission()) {
            serverScope.launch {
                val launchScript = """
                    if ! pgrep -f 'hub-port=$hubPort' >/dev/null 2>&1; then
                        termux-wake-lock 2>/dev/null || true
                        mkdir -p /data/data/com.termux/files/home/.gemini/$dataDirName
                        [ -f /data/data/com.termux/files/home/.gemini/jetski-standalone-oauth-token ] && cp -f /data/data/com.termux/files/home/.gemini/jetski-standalone-oauth-token /data/data/com.termux/files/home/.gemini/$dataDirName/jetski-standalone-oauth-token
                        [ -f /sdcard/Download/.gemini/jetski-standalone-oauth-token ] && cp -f /sdcard/Download/.gemini/jetski-standalone-oauth-token /data/data/com.termux/files/home/.gemini/$dataDirName/jetski-standalone-oauth-token
                        [ -f /sdcard/Download/.gemini/jetski-standalone-oauth-token ] && cp -f /sdcard/Download/.gemini/jetski-standalone-oauth-token /data/data/com.termux/files/home/.gemini/jetski-standalone-oauth-token
                        [ -f /sdcard/Download/.gemini/settings.json ] && cp -f /sdcard/Download/.gemini/settings.json /data/data/com.termux/files/home/.gemini/$dataDirName/settings.json
                        export AGY_ENABLE_HUB=1
                        export ANTIGRAVITY_VSCODE_HOST=1
                        export ANTIGRAVITY_AUTH_SUCCESS_APP=vscode
                        export ANTIGRAVITY_APP_DATA_DIR=$dataDirName
                        export CI=1
                        export AGY_NO_UPDATE_PROMPT=1
                        nohup /data/data/com.termux/files/home/.local/bin/agy --hub --hub-port=$hubPort --app_data_dir=$dataDirName < /dev/null >> /data/data/com.termux/files/home/.gemini/jarvis_hub_$hubPort.log 2>&1 &
                    fi
                """.trimIndent()
                termuxBridge.executeCommand(launchScript, background = true, timeoutSeconds = 5)
                Log.i(TAG, "Dispatched Antigravity Hub launch on port $hubPort via Termux bridge.")
            }
        }
    }

    suspend fun startServer(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        cachedAppContext = context.applicationContext

        // Always ensure Antigravity Hub is started (via Termux Bridge or local binary)
        val agyBin = resolveAgyBinary(context)
        ensureAgyHubDaemonRunning(context, agyBin)

        if (_status.value is LocalServerStatus.Running) return@withContext Result.success(Unit)

        _status.value = LocalServerStatus.Starting
        val binDir = LocalEnvironmentManager.getBinDir(context)
        val bridgeBinary = resolveBridgeBinary(context, binDir)

        if (bridgeBinary == null || !bridgeBinary.exists() || !bridgeBinary.canExecute()) {
            Log.w(TAG, "No executable agy_ide_bridge binary found. Activating FallbackPTY mode.")
            _status.value = LocalServerStatus.FallbackPTY
            return@withContext Result.success(Unit)
        }

        val port = getBridgePort(context)
        val hubPort = getHubPort(context)
        val homeDir = LocalEnvironmentManager.getHomeDir(context).absolutePath
        val prefixDir = LocalEnvironmentManager.getPrefixDir(context).absolutePath
        val tmpDir = LocalEnvironmentManager.getTmpDir(context).absolutePath

        val cmd = mutableListOf(
            bridgeBinary.absolutePath,
            "-f",
            "--port", port.toString(),
            "--bind", "127.0.0.1",
            "--dir", homeDir,
            "--hub-port", hubPort.toString()
        )
        resolveAgyBinary(context)?.let { agyBin ->
            cmd.addAll(listOf("--bin", agyBin.absolutePath))
        }

        try {
            val pb = ProcessBuilder(cmd)
            pb.directory(File(homeDir))
            val env = pb.environment()
            env["TERM"] = "xterm-256color"
            env["HOME"] = homeDir
            env["PREFIX"] = prefixDir
            env["TMPDIR"] = tmpDir
            env["LD_LIBRARY_PATH"] = "$prefixDir/lib"
            env["PATH"] = "$homeDir/.local/bin:$prefixDir/bin:$prefixDir/bin/applets:/system/bin:/system/xbin"

            supervisorProcess = pb.start()

            serverScope.launch {
                val exitCode = supervisorProcess?.waitFor()
                Log.w(TAG, "Go bridge daemon exited with code: $exitCode")
                _status.value = LocalServerStatus.Stopped(exitCode)
                healthCheckJob?.cancel()
            }

            val isHealthy = waitForHealthOk(context, timeoutSeconds = 6)
            if (isHealthy) {
                _status.value = LocalServerStatus.Running(port)
                startHealthWatchdog(context)
                Log.i(TAG, "Go bridge daemon running on port $port")
                Result.success(Unit)
            } else {
                Log.w(TAG, "Daemon launched but health check timed out. Switching to FallbackPTY.")
                _status.value = LocalServerStatus.FallbackPTY
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting Go bridge daemon: ${e.message}. Falling back to PTY.", e)
            _status.value = LocalServerStatus.FallbackPTY
            Result.success(Unit)
        }
    }

    /**
     * Resolves the Go bridge binary across 4 tiers without throwing unhandled exceptions.
     */
    private fun resolveBridgeBinary(context: Context, targetBinDir: File): File? {
        if (!targetBinDir.exists()) {
            targetBinDir.mkdirs()
        }

        // Tier 1: Check $PREFIX/bin/agy_ide_bridge
        val standardBin = File(targetBinDir, BRIDGE_BINARY_NAME)
        if (standardBin.exists() && standardBin.canExecute() && standardBin.length() > 1000) {
            return standardBin
        }

        // Tier 2: Check device storage (/sdcard/agy_ide_bridge or $HOME/bin/agy_ide_bridge)
        val homeBin = File(LocalEnvironmentManager.getHomeDir(context), "bin/$BRIDGE_BINARY_NAME")
        val sdcardBin = File("/sdcard", BRIDGE_BINARY_NAME)
        val externalCandidate = listOf(homeBin, sdcardBin).firstOrNull { it.exists() && it.length() > 1000 }
        if (externalCandidate != null) {
            try {
                externalCandidate.copyTo(standardBin, overwrite = true)
                standardBin.setExecutable(true, false)
                standardBin.setReadable(true, false)
                return standardBin
            } catch (e: Exception) {
                Log.w(TAG, "Could not copy binary from ${externalCandidate.absolutePath}: ${e.message}")
            }
        }

        // Tier 3: Unpack from assets/bin/agy_ide_bridge.xz if present
        try {
            val hasAsset = context.assets.list("bin")?.contains(BRIDGE_ASSET) == true
            if (hasAsset) {
                unpackBridgeBinary(context, targetBinDir)
                if (standardBin.exists() && standardBin.canExecute()) {
                    return standardBin
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Asset $BRIDGE_ASSET not present or unpack skipped: ${e.message}")
        }

        // Tier 4: Return null so caller transitions cleanly to LocalServerStatus.FallbackPTY
        return if (standardBin.exists() && standardBin.canExecute()) standardBin else null
    }

    fun stopServer() {
        _status.value = LocalServerStatus.Stopping
        healthCheckJob?.cancel()
        supervisorProcess?.destroyForcibly()
        supervisorProcess = null
        _status.value = LocalServerStatus.Stopped()
    }

    private fun unpackBridgeBinary(context: Context, targetBinDir: File) {
        val targetFile = File(targetBinDir, BRIDGE_BINARY_NAME)
        val tempFile = File(targetBinDir, "$BRIDGE_BINARY_NAME.tmp")

        context.assets.open("bin/$BRIDGE_ASSET").use { rawInput ->
            XZInputStream(BufferedInputStream(rawInput, 64 * 1024)).use { xzInput ->
                tempFile.outputStream().use { fileOut ->
                    xzInput.copyTo(fileOut)
                }
            }
        }

        tempFile.setExecutable(true, false)
        tempFile.setReadable(true, false)
        if (targetFile.exists()) targetFile.delete()
        tempFile.renameTo(targetFile)
        Log.i(TAG, "Unpacked $BRIDGE_BINARY_NAME (${targetFile.length()} bytes) successfully.")
    }

    private suspend fun waitForHealthOk(context: Context, timeoutSeconds: Int): Boolean {
        val healthUrl = "${getBridgeUrl(context)}/health"
        val request = Request.Builder().url(healthUrl).build()

        repeat(timeoutSeconds * 2) {
            try {
                httpClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) return true
                }
            } catch (_: Exception) {}
            delay(500)
        }
        return false
    }

    private fun startHealthWatchdog(context: Context) {
        healthCheckJob?.cancel()
        healthCheckJob = serverScope.launch {
            val request = Request.Builder().url("${getBridgeUrl(context)}/health").build()
            while (isActive) {
                delay(10000)
                try {
                    httpClient.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful && _status.value is LocalServerStatus.Running) {
                            Log.w(TAG, "Health check failed: HTTP ${resp.code}")
                        }
                    }
                } catch (e: Exception) {
                    if (_status.value is LocalServerStatus.Running) {
                        Log.w(TAG, "Health watchdog disconnected: ${e.message}")
                    }
                }
            }
        }
    }
}
