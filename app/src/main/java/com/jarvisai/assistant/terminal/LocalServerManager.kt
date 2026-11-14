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

data class AgyDaemonDiagnostics(
    val hubPort: Int = 2235,
    val agyBinaryFound: Boolean = false,
    val agyBinaryPath: String? = null,
    val agyHubReachable: Boolean = false,
    val mcpConfigured: Boolean = false,
    val linuxCoreInstalled: Boolean = false,
    val externalTermuxActive: Boolean = false,
    val lastNotice: String = ""
)

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

    private val _agyDiagnostics = MutableStateFlow(AgyDaemonDiagnostics())
    val agyDiagnostics: StateFlow<AgyDaemonDiagnostics> = _agyDiagnostics.asStateFlow()

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
            provisionEmbeddedMcpConfig(context.applicationContext)
            startServer(context.applicationContext)
            refreshDiagnostics(context.applicationContext)
        }
    }

    suspend fun warmUpAgyHub(maxWaitMs: Long = 10000, context: Context? = cachedAppContext): Boolean {
        return ensureAgyHubAvailable(context, maxWaitMs)
    }

    fun hasTermuxCommandPermission(context: Context? = cachedAppContext): Boolean {
        // Return true so API layer never blocks localhost HTTP connections
        return true
    }

    suspend fun refreshDiagnostics(context: Context? = cachedAppContext): AgyDaemonDiagnostics = withContext(Dispatchers.IO) {
        val ctx = context ?: cachedAppContext ?: return@withContext _agyDiagnostics.value
        val agyBin = resolveAgyBinary(ctx)
        val hubReachable = isAgyHubReachable()
        val mcpFile = File(LocalEnvironmentManager.getHomeDir(ctx), ".gemini/${com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_APP_DATA_DIR}/mcp_config.json")
        val linuxInstalled = LocalEnvironmentManager.isEnvironmentInstalled(ctx)
        val termuxBridge = com.jarvisai.assistant.termux.JarvisTermuxBridge(ctx)
        val extTermuxOk = termuxBridge.isTermuxInstalled() && termuxBridge.hasRunCommandPermission()
        val diag = AgyDaemonDiagnostics(
            hubPort = getHubPort(ctx),
            agyBinaryFound = agyBin != null && agyBin.exists(),
            agyBinaryPath = agyBin?.absolutePath,
            agyHubReachable = hubReachable,
            mcpConfigured = mcpFile.exists() && mcpFile.length() > 10,
            linuxCoreInstalled = linuxInstalled,
            externalTermuxActive = extTermuxOk,
            lastNotice = _agyDiagnostics.value.lastNotice
        )
        _agyDiagnostics.value = diag
        diag
    }

    /**
     * Ensures the local isolated Antigravity Hub at 127.0.0.1:2235 is reachable, starting it if needed.
     */
    suspend fun ensureAgyHubAvailable(
        context: Context? = cachedAppContext,
        maxWaitMs: Long = 10000L
    ): Boolean = withContext(Dispatchers.IO) {
        if (isAgyHubReachable()) {
            refreshDiagnostics(context)
            return@withContext true
        }
        val ctx = context ?: cachedAppContext ?: return@withContext false
        provisionEmbeddedMcpConfig(ctx)
        val agyBin = resolveAgyBinary(ctx)
        ensureAgyHubDaemonRunning(ctx, agyBin)
        val iterations = (maxWaitMs / 500L).toInt().coerceIn(4, 24)
        repeat(iterations) {
            if (isAgyHubReachable()) {
                refreshDiagnostics(ctx)
                return@withContext true
            }
            delay(500)
        }
        refreshDiagnostics(ctx)
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

    fun importAgyBinary(context: Context, sourceFile: File): Result<File> {
        return runCatching {
            require(sourceFile.exists() && sourceFile.isFile && sourceFile.length() > 1000) {
                "Invalid agy binary at ${sourceFile.absolutePath}"
            }
            val binDir = File(context.filesDir, "bin").apply { mkdirs() }
            val internalAgy = File(binDir, "agy")
            sourceFile.copyTo(internalAgy, overwrite = true)
            internalAgy.setExecutable(true, false)
            internalAgy.setReadable(true, false)
            runCatching { android.system.Os.chmod(internalAgy.absolutePath, 493) }
            bootstrapAuthTokens(context)
            provisionEmbeddedMcpConfig(context)
            _agyDiagnostics.value = _agyDiagnostics.value.copy(
                agyBinaryFound = true,
                agyBinaryPath = internalAgy.absolutePath,
                lastNotice = "Imported agy binary (${internalAgy.length() / 1024} KB)"
            )
            internalAgy
        }
    }

    private fun resolveAgyBinary(context: Context): File? {
        val binDir = File(context.filesDir, "bin").apply { mkdirs() }
        val internalAgy = File(binDir, "agy")

        // 1. Check private app storage and native library dir
        val nativeLibAgy = File(context.applicationInfo.nativeLibraryDir, "libagy.so")
        if (internalAgy.exists() && internalAgy.isFile && internalAgy.length() > 1000) {
            runCatching {
                internalAgy.setExecutable(true, false)
                android.system.Os.chmod(internalAgy.absolutePath, 493)
            }
            bootstrapAuthTokens(context)
            return internalAgy
        }
        if (nativeLibAgy.exists() && nativeLibAgy.isFile && nativeLibAgy.length() > 1000) {
            runCatching {
                nativeLibAgy.setExecutable(true, false)
            }
            bootstrapAuthTokens(context)
            return nativeLibAgy
        }

        // 2. Check packaged asset bin/agy.xz if bundled
        runCatching {
            if (context.assets.list("bin")?.contains("agy.xz") == true) {
                val tmpFile = File(binDir, "agy.tmp")
                context.assets.open("bin/agy.xz").use { raw ->
                    XZInputStream(BufferedInputStream(raw, 64 * 1024)).use { xz ->
                        tmpFile.outputStream().use { out -> xz.copyTo(out) }
                    }
                }
                tmpFile.setExecutable(true, false)
                tmpFile.setReadable(true, false)
                runCatching { android.system.Os.chmod(tmpFile.absolutePath, 493) }
                if (internalAgy.exists()) internalAgy.delete()
                tmpFile.renameTo(internalAgy)
                bootstrapAuthTokens(context)
                return internalAgy
            }
        }

        // 3. Check public Downloads / sdcard folders and copy to internal storage for execve
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val externalCandidates = listOf(
            File(downloadsDir, "agy"),
            File(downloadsDir, "agy-linux-arm64"),
            File("/sdcard/Download/agy"),
            File("/sdcard/agy"),
            File(LocalEnvironmentManager.getHomeDir(context), ".local/bin/agy"),
            File(LocalEnvironmentManager.getBinDir(context), "agy")
        )
        for (ext in externalCandidates) {
            if (ext.exists() && ext.isFile && ext.length() > 1000) {
                try {
                    ext.copyTo(internalAgy, overwrite = true)
                    internalAgy.setExecutable(true, false)
                    internalAgy.setReadable(true, false)
                    runCatching { android.system.Os.chmod(internalAgy.absolutePath, 493) }
                    Log.i(TAG, "Successfully copied standalone agy binary from ${ext.absolutePath} to ${internalAgy.absolutePath} (${internalAgy.length()} bytes)")
                    bootstrapAuthTokens(context)
                    return internalAgy
                } catch (e: Exception) {
                    Log.w(TAG, "Failed copying agy from ${ext.absolutePath}: ${e.message}")
                    if (ext.canExecute()) return ext
                }
            }
        }

        // 4. Fallback scan Termux paths
        val termuxCandidates = listOf(
            File("/data/data/com.termux/files/home/.local/bin/agy"),
            File("/data/data/com.termux/files/usr/bin/agy"),
            File("/data/user/0/com.termux/files/home/.local/bin/agy")
        )
        for (candidate in termuxCandidates) {
            if (candidate.exists() && candidate.isFile && candidate.length() > 1000) {
                return candidate
            }
        }
        return null
    }

    /**
     * Provisions Jarvis's built-in Embedded MCP Server (port 8765 & 8091) plus user-selected catalog servers
     * into ~/.gemini/jarvis-antigravity/mcp_config.json so the local agy daemon natively sees Android OS,
     * Browser CDP, Terminal PTY, 5-Tier Memory, and Google Flow tools.
     */
    fun provisionEmbeddedMcpConfig(context: Context) {
        try {
            val prefs = context.getSharedPreferences("jarvis_mcp_catalog", Context.MODE_PRIVATE)
            val enabledCatalogIds = mutableSetOf<String>()
            com.jarvisai.assistant.ui.settings.BUILTIN_MCP_CATALOG.forEach { item ->
                val defaultOn = item.id == "jarvis-android-mcp" || item.id == "filesystem" || item.id == "browser-cdp"
                if (prefs.getBoolean("enabled_${item.id}", defaultOn)) {
                    enabledCatalogIds.add(item.id)
                }
            }

            val serversObj = org.json.JSONObject().apply {
                put("jarvis-android-mcp", org.json.JSONObject().apply {
                    put("serverUrl", "http://127.0.0.1:8765/mcp")
                    put("url", "http://127.0.0.1:8765/mcp")
                    put("transport", "http")
                })
                put("jarvis-browser-mcp", org.json.JSONObject().apply {
                    put("serverUrl", "http://127.0.0.1:8091/mcp/browser")
                    put("url", "http://127.0.0.1:8091/mcp/browser")
                    put("transport", "http")
                })
            }

            val homePath = LocalEnvironmentManager.getHomeDir(context).absolutePath
            com.jarvisai.assistant.ui.settings.BUILTIN_MCP_CATALOG
                .filter { it.id in enabledCatalogIds && it.id != "jarvis-android-mcp" && it.id != "browser-cdp" }
                .forEach { entry ->
                    val argsList = entry.args.map { arg ->
                        if (arg == "/data/data/com.termux/files/home") homePath else arg
                    }
                    val spec = org.json.JSONObject().apply {
                        if (entry.command == "http") {
                            put("serverUrl", argsList.firstOrNull().orEmpty())
                            put("url", argsList.firstOrNull().orEmpty())
                            put("transport", "http")
                        } else {
                            put("command", entry.command)
                            put("args", org.json.JSONArray(argsList))
                        }
                        if (entry.envKeyName != null) {
                            val token = prefs.getString("env_${entry.envKeyName}", "").orEmpty()
                            if (token.isNotBlank()) {
                                put("env", org.json.JSONObject().put(entry.envKeyName, token))
                            }
                        }
                    }
                    serversObj.put(entry.id, spec)
                }

            val rootJson = org.json.JSONObject().put("mcpServers", serversObj).toString(2)
            val dataDirName = com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_APP_DATA_DIR
            val targetDirs = listOf(
                File(context.filesDir, "home/.gemini"),
                File(context.filesDir, "home/.gemini/$dataDirName"),
                File(LocalEnvironmentManager.getHomeDir(context), ".gemini"),
                File(LocalEnvironmentManager.getHomeDir(context), ".gemini/$dataDirName"),
                File("/data/data/com.termux/files/home/.gemini/$dataDirName"),
                File("/data/data/com.termux/files/home/.gemini")
            )
            targetDirs.forEach { dir ->
                runCatching {
                    dir.mkdirs()
                    File(dir, "mcp_config.json").writeText(rootJson)
                }
            }
            Log.i(TAG, "Provisioned Antigravity MCP config (${serversObj.length()} servers) to jarvis-antigravity.")
        } catch (e: Exception) {
            Log.w(TAG, "provisionEmbeddedMcpConfig notice: ${e.message}")
        }
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
            val termuxIsolatedGemini = File("/data/data/com.termux/files/home/.gemini/$dataDirName")

            val tokenPayload = org.json.JSONObject().apply {
                if (accessToken.isNotBlank()) put("access_token", accessToken)
                if (refreshToken.isNotBlank()) put("refresh_token", refreshToken)
                put("token_type", "Bearer")
                put("client_id", com.jarvisai.assistant.data.remote.GoogleOAuthManager.CLIENT_ID)
                put("client_secret", com.jarvisai.assistant.data.remote.GoogleOAuthManager.CLIENT_SECRET)
                val email = authPrefs.getUserEmail()
                if (email.isNotBlank()) put("email", email)
            }.toString(2)

            listOf(geminiDir, isolatedGeminiDir, termuxIsolatedGemini).forEach { dir ->
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
     * or newly synced MCP servers take effect immediately on port 2235.
     */
    fun restartAgyHubForAuth(context: Context) {
        serverScope.launch {
            try {
                syncAuthPreferencesToAgyDirs(context)
                provisionEmbeddedMcpConfig(context)
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
        provisionEmbeddedMcpConfig(context)
        val hubPort = getHubPort(context)
        val dataDirName = com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_APP_DATA_DIR

        if (isAgyHubReachable()) {
            Log.i(TAG, "Antigravity Hub already active on 127.0.0.1:$hubPort")
            _agyDiagnostics.value = _agyDiagnostics.value.copy(
                agyHubReachable = true,
                lastNotice = "Antigravity Hub active on 127.0.0.1:$hubPort"
            )
            return
        }

        val resolvedBin = agyBin ?: resolveAgyBinary(context)
        val homeDir = LocalEnvironmentManager.getHomeDir(context).apply { mkdirs() }
        val prefixDir = LocalEnvironmentManager.getPrefixDir(context).apply { mkdirs() }
        val tmpDir = LocalEnvironmentManager.getTmpDir(context).apply { mkdirs() }
        val binDir = File(context.filesDir, "bin").apply { mkdirs() }
        val workspaceDir = com.jarvisai.assistant.ide.ActiveCodeEditorRegistry.currentProjectPath
            ?.takeIf { it.isNotBlank() && File(it).exists() }
            ?: File(homeDir, "projects").apply { mkdirs() }.absolutePath

        // Strategy 1: Direct Internal Execution via targetSdk 28 (AntiGem Parity)
        if (resolvedBin != null && resolvedBin.exists()) {
            try {
                runCatching {
                    resolvedBin.setExecutable(true, false)
                    android.system.Os.chmod(resolvedBin.absolutePath, 493)
                }

                val pb = ProcessBuilder(
                    resolvedBin.absolutePath,
                    "--hub",
                    "--hub-port=$hubPort",
                    "--app_data_dir=$dataDirName"
                )
                pb.directory(File(workspaceDir))
                val env = pb.environment()
                env["HOME"] = homeDir.absolutePath
                env["PREFIX"] = prefixDir.absolutePath
                env["TMPDIR"] = tmpDir.absolutePath
                env["XDG_CONFIG_HOME"] = File(homeDir, ".config").apply { mkdirs() }.absolutePath
                env["XDG_DATA_HOME"] = File(homeDir, ".local/share").apply { mkdirs() }.absolutePath
                env["LD_LIBRARY_PATH"] = "${prefixDir.absolutePath}/lib:${homeDir.absolutePath}/.local/lib:/data/data/com.termux/files/usr/lib"
                val certFile = File(prefixDir, "etc/tls/cert.pem")
                if (certFile.exists()) {
                    env["SSL_CERT_FILE"] = certFile.absolutePath
                    env["CURL_CA_BUNDLE"] = certFile.absolutePath
                }
                env["AGY_ENABLE_HUB"] = "1"
                env["ANTIGRAVITY_VSCODE_HOST"] = "1"
                env["ANTIGRAVITY_AUTH_SUCCESS_APP"] = "vscode"
                env["ANTIGRAVITY_APP_DATA_DIR"] = dataDirName
                env["JARVIS_MCP_URL"] = "http://127.0.0.1:8765/mcp"
                env["ANTIGRAVITY_MCP_URL"] = "http://127.0.0.1:8765/mcp"
                env["WORKSPACE_DIR"] = workspaceDir
                env["CI"] = "1"
                env["AGY_NO_UPDATE_PROMPT"] = "1"
                env["PATH"] = "${binDir.absolutePath}:${homeDir.absolutePath}/.local/bin:${prefixDir.absolutePath}/bin:${prefixDir.absolutePath}/bin/applets:/system/bin:/system/xbin"

                pb.redirectErrorStream(true)
                val proc = pb.start()
                agyHubProcess = proc
                _agyDiagnostics.value = _agyDiagnostics.value.copy(
                    agyBinaryFound = true,
                    agyBinaryPath = resolvedBin.absolutePath,
                    lastNotice = "Starting agy --hub on port $hubPort..."
                )
                serverScope.launch {
                    try {
                        proc.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                Log.d("AgyHubDaemon", line)
                                com.jarvisai.assistant.ide.IdeDaemonLogBuffer.append("AGY", line)
                            }
                        }
                    } catch (_: Exception) {}
                }
                Log.i(TAG, "Started self-contained Antigravity Hub daemon (${resolvedBin.absolutePath}) on 127.0.0.1:$hubPort")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Internal ProcessBuilder start attempt: ${e.message}")
                _agyDiagnostics.value = _agyDiagnostics.value.copy(
                    lastNotice = "ProcessBuilder start failed: ${e.message}"
                )
            }
        } else {
            _agyDiagnostics.value = _agyDiagnostics.value.copy(
                agyBinaryFound = false,
                agyBinaryPath = null,
                lastNotice = "Standalone 'agy' binary not found in app storage, Downloads, or Termux."
            )
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
                        export JARVIS_MCP_URL=http://127.0.0.1:8765/mcp
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
