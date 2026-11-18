package com.jarvisai.assistant.terminal

import android.content.Context
import android.util.Log
import com.jarvisai.assistant.agent.core.AgyOkHttpClient
import com.jarvisai.assistant.util.IndiaTimeUtils
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
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
    val agyVersion: String = "",
    val agyHubReachable: Boolean = false,
    val mcpConfigured: Boolean = false,
    val linuxCoreInstalled: Boolean = false,
    val externalTermuxActive: Boolean = false,
    val lastNotice: String = ""
)

/**
 * Native Jarvis AI IDE Bridge & Antigravity Hub Supervisor.
 * Runs an embedded pure-Kotlin HTTP IDE bridge on port 2234 and supervises
 * the isolated Antigravity Hub daemon (`agy --hub`) on port 2235.
 */
object LocalServerManager {

    private const val TAG = "LocalServerManager"
    private const val LEGACY_BRIDGE_BINARY_NAME = "agy_ide_bridge"

    private val _status = MutableStateFlow<LocalServerStatus>(LocalServerStatus.Idle)
    val status: StateFlow<LocalServerStatus> = _status.asStateFlow()

    private val _agyDiagnostics = MutableStateFlow(AgyDaemonDiagnostics())
    val agyDiagnostics: StateFlow<AgyDaemonDiagnostics> = _agyDiagnostics.asStateFlow()

    private val _serverLogs = MutableStateFlow<List<String>>(emptyList())
    val serverLogs: StateFlow<List<String>> = _serverLogs.asStateFlow()

    private val logLock = Any()
    private val hubLifecycleMutex = Mutex()
    private const val MAX_SERVER_LOG_LINES = 800
    @Volatile
    private var activeLogFile: File? = null
    @Volatile
    private var currentHubState: String = "idle"
    @Volatile
    private var currentCsrfToken: String = ""
    @Volatile
    private var lastHubVerifiedLine: String = ""
    @Volatile
    private var lastVerifiedBinaryStamp: String = ""

    private val ANSI_ESCAPE_REGEX = Regex("""\u001B\[[0-9;]*[A-Za-z]""")
    private val EXTERNAL_BRAND_REGEX = Regex("""\bantigem\b""", RegexOption.IGNORE_CASE)

    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var agyHubProcess: Process? = null
    private var healthCheckJob: Job? = null
    private var hubMonitorJob: Job? = null
    @Volatile
    private var cachedAppContext: Context? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private val GLOG_PREINIT_TZ_REGEX = Regex("""ERROR:\s+logging before google\.Init:\s+I\d+\s+\d{2}:\d{2}:\d{2}\.\d+\s+\d+\s+[^\]]+\]\s*(.*)""")
    private const val PREFS_SERVER_SECURITY = "jarvis_server_prefs"
    private const val KEY_VERIFIED_AGY_STAMP = "verified_agy_binary_stamp"

    private fun nowLogTimestamp(): String = IndiaTimeUtils.nowLogTimestamp()

    private fun sanitizeLogLine(rawLine: String): String {
        val noAnsi = ANSI_ESCAPE_REGEX.replace(rawLine, "")
        val cleanedBrand = EXTERNAL_BRAND_REGEX.replace(noAnsi, "Jarvis AI")
        val glogMatch = GLOG_PREINIT_TZ_REGEX.find(cleanedBrand)
        return if (glogMatch != null) {
            val detail = glogMatch.groupValues[1].trim()
            val prefix = cleanedBrand.substring(0, glogMatch.range.first)
            "$prefix$detail"
        } else {
            cleanedBrand
        }
    }

    fun appendServerLog(rawLine: String) {
        val cleanLine = sanitizeLogLine(rawLine)
        synchronized(logLock) {
            _serverLogs.value = (_serverLogs.value + cleanLine).takeLast(MAX_SERVER_LOG_LINES)
            runCatching {
                activeLogFile?.appendText(cleanLine + "\n")
            }
        }
    }

    fun clearServerLogs() {
        synchronized(logLock) {
            _serverLogs.value = emptyList()
        }
    }

    fun getServerLogTranscript(): String {
        return _serverLogs.value.joinToString("\n")
    }

    fun resolveOrGenerateSecurityToken(context: Context): String {
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        val bridgeDir = File(homeDir, ".jarvis_bridge").apply { mkdirs() }
        val tokenFile = File(bridgeDir, "security_token.txt")
        if (tokenFile.exists() && tokenFile.canRead()) {
            val existing = runCatching { tokenFile.readText().trim() }.getOrDefault("")
            if (existing.length == 12 && existing.all { it.isLetterOrDigit() }) {
                return existing.lowercase()
            }
        }
        val defaultToken = "nfan7m2r00wy"
        runCatching { tokenFile.writeText(defaultToken) }
        return defaultToken
    }

    fun buildFramedHeader(token: String): String {
        val clean = token.trim().lowercase().take(12).padEnd(12, '0')
        return "x-ag${clean}_9qx"
    }

    private fun bytesMatchAt(buf: ByteArray, pos: Int, target: ByteArray): Boolean {
        var k = 0
        while (k < target.size) {
            if (buf[pos + k] != target[k]) return false
            k++
        }
        return true
    }

    /**
     * Read-first verification and conditional in-place patching of the 20-byte CSRF security header
     * inside the AGY binary (or companion `agy.va39`) without changing file size.
     * Persists verified file metadata (path + size + lastModified + header) in SharedPreferences
     * so cold launches skip the 100MB scan (<1ms instead of ~15s) until the binary actually updates.
     */
    fun patchAgyBinaryHeader(
        binaryFile: File,
        securityToken: String,
        context: Context? = cachedAppContext
    ): Pair<String, Double> {
        val t0 = System.nanoTime()
        val framedHeader = buildFramedHeader(securityToken)
        AgyOkHttpClient.setActiveFramedHeader(framedHeader)

        val targetFile = if (binaryFile.length() < 10L * 1024L * 1024L) {
            val companion = File(binaryFile.parentFile, "agy.va39")
            if (companion.exists() && companion.length() >= 10L * 1024L * 1024L) companion else binaryFile
        } else {
            binaryFile
        }

        if (!targetFile.exists() || !targetFile.isFile || targetFile.length() < 20L) {
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
            return framedHeader to elapsedMs
        }

        val prefs = (context ?: cachedAppContext)?.getSharedPreferences(PREFS_SERVER_SECURITY, Context.MODE_PRIVATE)
        if (lastVerifiedBinaryStamp.isBlank() && prefs != null) {
            lastVerifiedBinaryStamp = prefs.getString(KEY_VERIFIED_AGY_STAMP, "").orEmpty()
        }

        val currentStamp = "${targetFile.absolutePath}|${targetFile.length()}|${targetFile.lastModified()}|$framedHeader"
        if (currentStamp == lastVerifiedBinaryStamp) {
            runCatching {
                if (!targetFile.canExecute()) {
                    targetFile.setExecutable(true, false)
                    android.system.Os.chmod(targetFile.absolutePath, 493)
                }
            }
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
            return framedHeader to elapsedMs
        }

        val newBytes = framedHeader.toByteArray(Charsets.US_ASCII)
        val defaultBytes = "x-codeium-csrf-token".toByteArray(Charsets.US_ASCII)
        val prefixBytes = "x-ag".toByteArray(Charsets.US_ASCII)
        val suffixBytes = "_9qx".toByteArray(Charsets.US_ASCII)
        val xByte = 'x'.code.toByte()

        val writeOffsets = mutableListOf<Long>()
        runCatching {
            java.io.RandomAccessFile(targetFile, "r").use { raf ->
                val fileLen = raf.length()
                val chunkSize = 2 * 1024 * 1024
                val overlap = 32
                val buf = ByteArray(chunkSize + overlap)
                var offset = 0L

                while (offset < fileLen) {
                    raf.seek(offset)
                    val toRead = minOf(buf.size.toLong(), fileLen - offset).toInt()
                    val read = raf.read(buf, 0, toRead)
                    if (read < 20) break

                    var i = 0
                    val limit = read - 20
                    while (i <= limit) {
                        if (buf[i] != xByte) {
                            i++
                            continue
                        }
                        val nextByte = buf[i + 1]
                        val isDefault = nextByte == defaultBytes[1] &&
                            buf[i + 2] == defaultBytes[2] &&
                            bytesMatchAt(buf, i, defaultBytes)

                        val isFramed = !isDefault &&
                            nextByte == prefixBytes[1] &&
                            buf[i + 2] == prefixBytes[2] &&
                            buf[i + 3] == prefixBytes[3] &&
                            buf[i + 16] == suffixBytes[0] &&
                            buf[i + 17] == suffixBytes[1] &&
                            buf[i + 18] == suffixBytes[2] &&
                            buf[i + 19] == suffixBytes[3]

                        if (isDefault || isFramed) {
                            if (!bytesMatchAt(buf, i, newBytes)) {
                                writeOffsets.add(offset + i)
                            }
                            i += 20
                        } else {
                            i++
                        }
                    }
                    if (toRead < buf.size) break
                    offset += chunkSize
                }
            }
        }

        var patchSucceeded = writeOffsets.isEmpty()
        if (writeOffsets.isNotEmpty()) {
            val distinctOffsets = writeOffsets.distinct()
            for (attempt in 0 until 3) {
                try {
                    java.io.RandomAccessFile(targetFile, "rw").use { raf ->
                        for (pos in distinctOffsets) {
                            raf.seek(pos)
                            raf.write(newBytes)
                        }
                    }
                    patchSucceeded = true
                    break
                } catch (e: Exception) {
                    val isBusy = e.message?.contains("Text file busy", ignoreCase = true) == true ||
                        e.message?.contains("ETXTBSY", ignoreCase = true) == true
                    if (!isBusy || attempt == 2) {
                        Log.w(TAG, "patchAgyBinaryHeader write notice: ${e.message}")
                        break
                    }
                    runCatching { agyHubProcess?.destroyForcibly() }
                    agyHubProcess = null
                    Thread.sleep(150L * (attempt + 1))
                }
            }
            if (patchSucceeded) {
                appendServerLog("${nowLogTimestamp()} [Security] Patched ${distinctOffsets.size} header offset(s) in ${targetFile.name} -> $framedHeader")
            }
        }

        if (patchSucceeded) {
            val verifiedStamp = "${targetFile.absolutePath}|${targetFile.length()}|${targetFile.lastModified()}|$framedHeader"
            lastVerifiedBinaryStamp = verifiedStamp
            prefs?.edit()?.putString(KEY_VERIFIED_AGY_STAMP, verifiedStamp)?.apply()
        }

        runCatching {
            targetFile.setExecutable(true, false)
            android.system.Os.chmod(targetFile.absolutePath, 493)
        }

        val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
        return framedHeader to elapsedMs
    }

    private fun logHubStateTransition(newState: String, errorMsg: String = "") {
        val oldState = currentHubState
        if (oldState == newState && errorMsg.isEmpty()) return
        currentHubState = newState
        if (newState == "stopped" || newState == "idle" || newState == "error") {
            currentCsrfToken = ""
        }
        val hasCsrf = currentCsrfToken.isNotBlank()
        appendServerLog("${nowLogTimestamp()} [Hub State Update] Transition: '$oldState' -> '$newState' (csrf: $hasCsrf, err: '$errorMsg')")
    }

    private fun probeHubTcp(hubPort: Int, logFailure: Boolean = true, logSuccess: Boolean = false): Boolean {
        val t0 = System.nanoTime()
        return try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("127.0.0.1", hubPort), 400)
            }
            val elapsedUs = (System.nanoTime() - t0) / 1000.0
            if (logSuccess) {
                val formatted = if (elapsedUs >= 1000.0) {
                    String.format(java.util.Locale.US, "%.3fms", elapsedUs / 1000.0)
                } else {
                    String.format(java.util.Locale.US, "%.3fµs", elapsedUs)
                }
                appendServerLog("${nowLogTimestamp()} [Hub Probe] 127.0.0.1:$hubPort -> TCP Connected OK in $formatted | Assumed state: ONLINE")
            }
            true
        } catch (e: Exception) {
            val elapsedUs = (System.nanoTime() - t0) / 1000.0
            if (logFailure) {
                val formatted = if (elapsedUs >= 1000.0) {
                    String.format(java.util.Locale.US, "%.3fms", elapsedUs / 1000.0)
                } else {
                    String.format(java.util.Locale.US, "%.3fµs", elapsedUs)
                }
                appendServerLog("${nowLogTimestamp()} [Hub Probe] 127.0.0.1:$hubPort -> Not connected (dial tcp 127.0.0.1:$hubPort: connect: connection refused in $formatted) | Assumed state: offline")
            }
            false
        }
    }

    private fun fetchCsrfTokenWithLog(hubPort: Int): String {
        return try {
            val url = "http://127.0.0.1:$hubPort/"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "JarvisAI-Android-Supervisor")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val headerToken = resp.header("x-codeium-csrf-token")?.trim().orEmpty()
                if (headerToken.isNotBlank()) {
                    appendServerLog("${nowLogTimestamp()} [FetchCsrfToken] Found token in header: $headerToken")
                    return headerToken
                }
                val body = resp.body?.string().orEmpty()
                val jsonRegex = Regex(""""csrfToken"\s*:\s*"([^"]+)"""")
                val match = jsonRegex.find(body)
                if (match != null) {
                    val token = match.groupValues[1].trim()
                    if (token.isNotBlank()) {
                        appendServerLog("${nowLogTimestamp()} [FetchCsrfToken] Extracted token via JSON pattern: $token")
                        return token
                    }
                }
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun verifyRealHubAuthAndLog(hubPort: Int) {
        try {
            val url = "http://127.0.0.1:$hubPort/exa.language_server_pb.LanguageServerService/GetAuthStatus"
            val req = Request.Builder()
                .url(url)
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            AgyOkHttpClient.client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errSummary = "HTTP ${resp.code}"
                    if (errSummary != lastHubVerifiedLine) {
                        lastHubVerifiedLine = errSummary
                        appendServerLog("${nowLogTimestamp()} [Hub Verify] GetAuthStatus returned HTTP ${resp.code}")
                    }
                    return
                }
                val json = JSONObject(body)
                val auth = json.optJSONObject("authResult") ?: json.optJSONObject("auth_result")
                val valid = auth?.optBoolean("hasValidAuth", false)
                    ?: auth?.optBoolean("has_valid_auth", false)
                    ?: false
                val appAccountEmail = cachedAppContext?.let { ctx ->
                    runCatching {
                        com.jarvisai.assistant.data.preferences.AuthPreferences.getInstance(ctx).getUserEmail()
                    }.getOrDefault("")
                }.orEmpty()
                val rpcEmail = auth?.optString("email").orEmpty().ifBlank { appAccountEmail }
                val summary = if (valid) {
                    "Authenticated (${rpcEmail.ifBlank { "OAuth active" }})"
                } else if (appAccountEmail.isNotBlank()) {
                    "Local daemon unsigned; CloudCode OAuth ready ($appAccountEmail)"
                } else {
                    "Unauthenticated (not-signed-in)"
                }
                if (summary != lastHubVerifiedLine) {
                    lastHubVerifiedLine = summary
                    appendServerLog("${nowLogTimestamp()} [Hub Verify] Real AGY RPC GetAuthStatus -> $summary")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "verifyRealHubAuthAndLog notice: ${e.message}")
        }
    }

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
        val agyVer = if (agyBin != null && agyBin.exists()) {
            runCatching { AgyCliManager.readAgyVersion(ctx, agyBin) }.getOrDefault("")
        } else ""
        val hubReachable = isAgyHubReachable()
        val mcpFile = File(LocalEnvironmentManager.getHomeDir(ctx), ".gemini/${com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_APP_DATA_DIR}/mcp_config.json")
        val linuxInstalled = LocalEnvironmentManager.isEnvironmentInstalled(ctx)
        val termuxBridge = com.jarvisai.assistant.termux.JarvisTermuxBridge(ctx)
        val extTermuxOk = termuxBridge.isTermuxInstalled() && termuxBridge.hasRunCommandPermission()
        val diag = AgyDaemonDiagnostics(
            hubPort = getHubPort(ctx),
            agyBinaryFound = agyBin != null && agyBin.exists(),
            agyBinaryPath = agyBin?.absolutePath,
            agyVersion = agyVer,
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
            val bridgePort = context?.let { getBridgePort(it) } ?: 2234
            context?.let { JarvisIdeBridgeServer.instance.start(it.applicationContext, bridgePort) }
            if (_status.value !is LocalServerStatus.Running) {
                _status.value = LocalServerStatus.Running(bridgePort)
            }
            return@withContext true
        }
        val ctx = context ?: cachedAppContext ?: return@withContext false
        hubLifecycleMutex.withLock {
            if (isAgyHubReachable()) {
                val bridgePort = getBridgePort(ctx)
                JarvisIdeBridgeServer.instance.start(ctx.applicationContext, bridgePort)
                if (_status.value !is LocalServerStatus.Running) {
                    _status.value = LocalServerStatus.Running(bridgePort)
                }
                return@withLock true
            }
            provisionEmbeddedMcpConfig(ctx)
            val agyBin = resolveAgyBinary(ctx)
            ensureAgyHubDaemonRunningLocked(ctx, agyBin)
            val iterations = (maxWaitMs / 500L).toInt().coerceIn(4, 24)
            repeat(iterations) {
                if (isAgyHubReachable()) {
                    val port = getBridgePort(ctx)
                    JarvisIdeBridgeServer.instance.start(ctx.applicationContext, port)
                    _status.value = LocalServerStatus.Running(port)
                    refreshDiagnostics(ctx)
                    return@withLock true
                }
                delay(500)
            }
            refreshDiagnostics(ctx)
            isAgyHubReachable()
        }
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
            val localBinAgy = File(LocalEnvironmentManager.getHomeDir(context), ".local/bin/agy")
            runCatching {
                localBinAgy.parentFile?.mkdirs()
                sourceFile.copyTo(localBinAgy, overwrite = true)
                localBinAgy.setExecutable(true, false)
                localBinAgy.setReadable(true, false)
                android.system.Os.chmod(localBinAgy.absolutePath, 493)
            }
            bootstrapAuthTokens(context)
            provisionEmbeddedMcpConfig(context)
            _agyDiagnostics.value = _agyDiagnostics.value.copy(
                agyBinaryFound = true,
                agyBinaryPath = localBinAgy.absolutePath,
                lastNotice = "Imported agy binary (${internalAgy.length() / 1024} KB)"
            )
            localBinAgy
        }
    }

    internal fun resolveAgyBinary(context: Context): File? {
        val binDir = File(context.filesDir, "bin").apply { mkdirs() }
        val internalAgy = File(binDir, "agy")
        val localBinAgy = File(LocalEnvironmentManager.getHomeDir(context), ".local/bin/agy")

        // 0. If ~/.local/bin/agy exists and is newer than internalAgy (e.g., after `agy update` or `install.sh`), sync it!
        if (localBinAgy.exists() && localBinAgy.isFile && localBinAgy.length() > 1000) {
            runCatching {
                localBinAgy.setExecutable(true, false)
                localBinAgy.setReadable(true, false)
                android.system.Os.chmod(localBinAgy.absolutePath, 493)
                if (!internalAgy.exists() || internalAgy.length() != localBinAgy.length() || localBinAgy.lastModified() > internalAgy.lastModified()) {
                    localBinAgy.copyTo(internalAgy, overwrite = true)
                    internalAgy.setExecutable(true, false)
                    internalAgy.setReadable(true, false)
                    android.system.Os.chmod(internalAgy.absolutePath, 493)
                }
            }
            bootstrapAuthTokens(context)
            return localBinAgy
        }

        // 1. Check private app storage and native library dir
        val nativeLibAgy = File(context.applicationInfo.nativeLibraryDir, "libagy.so")
        if (internalAgy.exists() && internalAgy.isFile && internalAgy.length() > 1000) {
            runCatching {
                internalAgy.setExecutable(true, false)
                android.system.Os.chmod(internalAgy.absolutePath, 493)
                if (!localBinAgy.exists()) {
                    localBinAgy.parentFile?.mkdirs()
                    internalAgy.copyTo(localBinAgy, overwrite = true)
                    localBinAgy.setExecutable(true, false)
                    android.system.Os.chmod(localBinAgy.absolutePath, 493)
                }
            }
            bootstrapAuthTokens(context)
            return if (localBinAgy.exists() && localBinAgy.length() > 1000) localBinAgy else internalAgy
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
        val isolatedFolder = com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_SDCARD_FOLDER
        val externalCandidates = listOf(
            File(downloadsDir, "$isolatedFolder/agy"),
            File(downloadsDir, "$isolatedFolder/agy-linux-arm64"),
            File("/sdcard/Download/$isolatedFolder/agy"),
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
                    localBinAgy.parentFile?.mkdirs()
                    runCatching {
                        internalAgy.copyTo(localBinAgy, overwrite = true)
                        localBinAgy.setExecutable(true, false)
                        android.system.Os.chmod(localBinAgy.absolutePath, 493)
                    }
                    Log.i(TAG, "Successfully copied standalone agy binary from ${ext.absolutePath} to ${localBinAgy.absolutePath} (${internalAgy.length()} bytes)")
                    bootstrapAuthTokens(context)
                    return if (localBinAgy.exists()) localBinAgy else internalAgy
                } catch (e: Exception) {
                    Log.w(TAG, "Failed copying agy from ${ext.absolutePath}: ${e.message}")
                    if (ext.canExecute()) return ext
                }
            }
        }

        // 4. Fallback scan Termux paths and copy into Jarvis ~/.local/bin/agy if readable
        val termuxCandidates = listOf(
            File("/data/data/com.termux/files/home/.local/bin/agy"),
            File("/data/data/com.termux/files/usr/bin/agy"),
            File("/data/user/0/com.termux/files/home/.local/bin/agy")
        )
        for (candidate in termuxCandidates) {
            if (candidate.exists() && candidate.isFile && candidate.length() > 1000) {
                runCatching {
                    localBinAgy.parentFile?.mkdirs()
                    candidate.copyTo(localBinAgy, overwrite = true)
                    localBinAgy.setExecutable(true, false)
                    android.system.Os.chmod(localBinAgy.absolutePath, 493)
                    return localBinAgy
                }
                return candidate
            }
        }
        return null
    }

    /**
     * Provisions Jarvis's built-in Embedded MCP Server (isolated ports 2236 & 2237) plus user-selected catalog servers
     * into ~/.gemini/jarvis-antigravity/mcp_config.json so the local agy daemon natively sees Android OS,
     * Browser CDP, Terminal PTY, 5-Tier Memory, and Google Flow tools without touching AntiGem's ~/.gemini/ config.
     */
    fun provisionEmbeddedMcpConfig(context: Context) {
        try {
            val appCtx = context.applicationContext
            com.jarvisai.assistant.mcp.server.AndroidLocalBridgeServer.instance.start(appCtx)
            val stdioBridgePath = runCatching {
                LocalEnvironmentManager.ensureMcpStdioBridgeScript(appCtx).absolutePath
            }.getOrDefault("")
            runCatching { LocalEnvironmentManager.ensureJarvisMcpCliScript(appCtx) }

            val prefs = appCtx.getSharedPreferences("jarvis_mcp_catalog", Context.MODE_PRIVATE)
            val enabledCatalogIds = mutableSetOf<String>()
            val catalog = com.jarvisai.assistant.mcp.McpServersManager.CASCADE_CATALOG
            catalog.forEach { item ->
                val defaultOn = item.id == "jarvis-android-mcp" || item.id == "filesystem" || item.id == "browser-cdp"
                if (prefs.getBoolean("enabled_${item.id}", defaultOn)) {
                    enabledCatalogIds.add(item.id)
                }
            }

            val mcpUrl = com.jarvisai.assistant.data.remote.JarvisHubConfig.getMcpUrl() // http://127.0.0.1:2236/mcp
            val browserMcpUrl = com.jarvisai.assistant.data.remote.JarvisHubConfig.getBrowserMcpUrl() // http://127.0.0.1:2237/mcp/browser
            val fsMcpUrl = "http://127.0.0.1:${com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_MCP_PORT}/filesystem"

            // Load existing specs first so user/agent-created custom MCP servers are NEVER wiped out!
            val mergedSpecs = com.jarvisai.assistant.mcp.McpServersManager.loadConfigSpecs(appCtx)

            fun buildLocalSpec(name: String, url: String, existingDisabled: Boolean = false) =
                com.jarvisai.assistant.mcp.McpServerSpec(
                    serverName = name,
                    command = if (stdioBridgePath.isNotBlank()) "/system/bin/sh" else "",
                    args = if (stdioBridgePath.isNotBlank()) listOf(stdioBridgePath, url) else emptyList(),
                    serverUrl = url,
                    disabled = existingDisabled
                )

            mergedSpecs["jarvis-android-mcp"] = buildLocalSpec(
                "jarvis-android-mcp",
                mcpUrl,
                mergedSpecs["jarvis-android-mcp"]?.disabled ?: false
            )
            mergedSpecs["jarvis-browser-mcp"] = buildLocalSpec(
                "jarvis-browser-mcp",
                browserMcpUrl,
                mergedSpecs["jarvis-browser-mcp"]?.disabled ?: false
            )
            val existingFs = mergedSpecs["filesystem"]
            if (existingFs == null || existingFs.command == "npx" || existingFs.serverUrl.contains(":2236/filesystem")) {
                mergedSpecs["filesystem"] = buildLocalSpec(
                    "filesystem",
                    fsMcpUrl,
                    existingFs?.disabled ?: false
                )
            }

            val homePath = LocalEnvironmentManager.getIsolatedWorkspaceDir(appCtx).absolutePath
            catalog
                .filter { it.id in enabledCatalogIds && it.id != "jarvis-android-mcp" && it.id != "browser-cdp" && it.id != "filesystem" }
                .forEach { entry ->
                    if (!mergedSpecs.containsKey(entry.id)) {
                        val argsList = entry.args.map { arg ->
                            if (arg == "/data/data/com.termux/files/home" || arg == "/data/user/0/com.jarvisai") homePath else arg
                        }
                        mergedSpecs[entry.id] = com.jarvisai.assistant.mcp.McpServerSpec(
                            serverName = entry.id,
                            command = if (entry.isRemote) "" else entry.command,
                            args = if (entry.isRemote) emptyList() else argsList,
                            env = entry.env.mapValues { (k, defaultVal) -> prefs.getString("env_$k", defaultVal).orEmpty() },
                            serverUrl = if (entry.isRemote) entry.serverUrl else "",
                            disabled = false
                        )
                    }
                }

            com.jarvisai.assistant.mcp.McpServersManager.writeSpecsToDisk(appCtx, mergedSpecs)
            com.jarvisai.assistant.mcp.McpServersManager.ensureServersLoadedAndProbed(appCtx)
            Log.i(TAG, "Provisioned Isolated Antigravity MCP config (${mergedSpecs.size} servers, custom servers preserved).")
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
            val jarvisDownloadGemini = File(File(downloadDir, com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_SDCARD_FOLDER), ".gemini")
            val sdcardGemini = File(downloadDir, ".gemini")

            // Candidate source paths (checks Jarvis-isolated locations first, then read-only fallback)
            val sourceTokenCandidates = listOf(
                File(isolatedGeminiDir, "jetski-standalone-oauth-token"),
                File(jarvisDownloadGemini, "jetski-standalone-oauth-token"),
                File("/data/data/com.termux/files/home/.gemini/$dataDirName/jetski-standalone-oauth-token"),
                File(sdcardGemini, "jetski-standalone-oauth-token"),
                File(geminiDir, "jetski-standalone-oauth-token")
            )

            val sourceSettingsCandidates = listOf(
                File(isolatedGeminiDir, "settings.json"),
                File(jarvisDownloadGemini, "settings.json"),
                File("/data/data/com.termux/files/home/.gemini/$dataDirName/settings.json"),
                File(geminiDir, "settings.json")
            )

            val validTokenSource = sourceTokenCandidates.firstOrNull { it.exists() && it.isFile && it.length() > 20 }
            val validSettingsSource = sourceSettingsCandidates.firstOrNull { it.exists() && it.isFile && it.length() > 5 }

            // Target directories strictly isolated to Jarvis AI (never overwrites external Termux root ~/.gemini)
            val targetDirs = listOf(
                geminiDir,
                isolatedGeminiDir,
                File("/data/data/com.termux/files/home/.gemini/$dataDirName"),
                File("/data/user/0/com.termux/files/home/.gemini/$dataDirName")
            )

            val authPrefs = com.jarvisai.assistant.data.preferences.AuthPreferences.getInstance(context)
            val hasLiveOAuth = authPrefs.getAccessToken().isNotBlank() || authPrefs.getRefreshToken().isNotBlank()
            if (hasLiveOAuth) {
                syncAuthPreferencesToAgyDirs(context)
            } else if (validTokenSource != null) {
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
                Log.w(TAG, "No disk jetski-standalone-oauth-token or AuthPreferences session found yet.")
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
                restartServer(context)
            } catch (e: Exception) {
                Log.w(TAG, "restartAgyHubForAuth notice: ${e.message}")
            }
        }
    }

    /**
     * Restarts the local Antigravity Hub daemon (`agy --hub`) and IDE supervisor after `agy` is
     * installed or updated via `AgyCliManager` so the new binary version is active immediately.
     */
    fun restartAgyHubAfterCliUpdate(context: Context, updatedVersion: String = "") {
        val appCtx = context.applicationContext
        serverScope.launch {
            try {
                syncAuthPreferencesToAgyDirs(appCtx)
                provisionEmbeddedMcpConfig(appCtx)
                stopServerInternal()
                delay(350)
                startServer(appCtx, forceRestart = true)
                ensureAgyHubAvailable(appCtx)
                val diag = refreshDiagnostics(appCtx)
                if (updatedVersion.isNotBlank()) {
                    _agyDiagnostics.value = diag.copy(
                        agyVersion = updatedVersion,
                        lastNotice = "Updated to agy $updatedVersion. The server restarted to use it."
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "restartAgyHubAfterCliUpdate notice: ${e.message}")
            }
        }
    }

    fun ensureAgyHubDaemonRunning(context: Context, agyBin: File? = null) {
        ensureAgyHubDaemonRunningLocked(context, agyBin)
    }

    private fun ensureAgyHubDaemonRunningLocked(context: Context, agyBin: File? = null) {
        bootstrapAuthTokens(context)
        provisionEmbeddedMcpConfig(context)
        val hubPort = getHubPort(context)
        val dataDirName = com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_APP_DATA_DIR
        val mcpUrl = com.jarvisai.assistant.data.remote.JarvisHubConfig.getMcpUrl()

        if (probeHubTcp(hubPort, logFailure = false, logSuccess = true)) {
            val tok = fetchCsrfTokenWithLog(hubPort)
            if (tok.isNotBlank()) {
                currentCsrfToken = tok
            }
            verifyRealHubAuthAndLog(hubPort)
            appendServerLog(" [✓] AGY Hub is online and listening on http://127.0.0.1:$hubPort")
            logHubStateTransition("online")
            appendServerLog("${nowLogTimestamp()} [Hub Monitor] ✅ AGY Hub detected ONLINE on http://127.0.0.1:$hubPort (CSRF token present: ${currentCsrfToken.isNotBlank()})")
            _agyDiagnostics.value = _agyDiagnostics.value.copy(
                agyHubReachable = true,
                lastNotice = "Antigravity Hub active on 127.0.0.1:$hubPort"
            )
            return
        }

        logHubStateTransition("starting")

        val resolvedBin = agyBin ?: resolveAgyBinary(context)
        val homeDir = LocalEnvironmentManager.getHomeDir(context).apply { mkdirs() }
        val prefixDir = LocalEnvironmentManager.getPrefixDir(context).apply { mkdirs() }
        val binDir = File(context.filesDir, "bin").apply { mkdirs() }
        val workspaceDir = com.jarvisai.assistant.ide.ActiveCodeEditorRegistry.currentProjectPath
            ?.takeIf { it.isNotBlank() && File(it).exists() }
            ?: LocalEnvironmentManager.getIsolatedProjectsDir(context).absolutePath

        if (resolvedBin != null && resolvedBin.exists()) {
            try {
                runCatching {
                    resolvedBin.setExecutable(true, false)
                    android.system.Os.chmod(resolvedBin.absolutePath, 493)
                }

                // In-place patch AGY binary with 20-byte framed security header (read-only check first)
                val secToken = resolveOrGenerateSecurityToken(context)
                val (patchedHeader, elapsedMs) = patchAgyBinaryHeader(resolvedBin, secToken, context)
                val formattedMs = String.format(java.util.Locale.US, "%.3fms", elapsedMs)
                appendServerLog("${nowLogTimestamp()} [Security] Verified AGY binary (${resolvedBin.absolutePath}) in $formattedMs -> Active Header: $patchedHeader")

                // Re-check port before spawning in case an instance came up during verification
                if (probeHubTcp(hubPort, logFailure = false, logSuccess = true)) {
                    val tok = fetchCsrfTokenWithLog(hubPort)
                    if (tok.isNotBlank()) currentCsrfToken = tok
                    verifyRealHubAuthAndLog(hubPort)
                    appendServerLog(" [✓] AGY Hub is online and listening on http://127.0.0.1:$hubPort")
                    logHubStateTransition("online")
                    appendServerLog("${nowLogTimestamp()} [Hub Monitor] ✅ AGY Hub detected ONLINE on http://127.0.0.1:$hubPort (CSRF token present: ${currentCsrfToken.isNotBlank()})")
                    _agyDiagnostics.value = _agyDiagnostics.value.copy(
                        agyHubReachable = true,
                        lastNotice = "Antigravity Hub active on 127.0.0.1:$hubPort"
                    )
                    return
                }

                val uploadsDir = File(homeDir, ".jarvis/uploads").apply { mkdirs() }
                val pb = ProcessBuilder(
                    resolvedBin.absolutePath,
                    "--hub",
                    "--hub-port=$hubPort",
                    "--app_data_dir=$dataDirName",
                    "--add-dir=$workspaceDir",
                    "--add-dir=${uploadsDir.absolutePath}"
                )
                pb.directory(File(workspaceDir))
                val env = pb.environment()
                LocalEnvironmentManager.applyIsolatedToolEnvironment(context, env)
                env["TZ"] = IndiaTimeUtils.IST_ZONE_ID
                env["XDG_CONFIG_HOME"] = File(homeDir, ".config/$dataDirName").apply { mkdirs() }.absolutePath
                env["XDG_DATA_HOME"] = File(homeDir, ".local/share/$dataDirName").apply { mkdirs() }.absolutePath
                env["LD_LIBRARY_PATH"] = "${prefixDir.absolutePath}/lib:${homeDir.absolutePath}/.local/lib:/data/data/com.termux/files/usr/lib"
                env["AGY_ENABLE_HUB"] = "1"
                env["ANTIGRAVITY_VSCODE_HOST"] = "1"
                env["ANTIGRAVITY_AUTH_SUCCESS_APP"] = "vscode"
                env["ANTIGRAVITY_APP_DATA_DIR"] = dataDirName
                env["JARVIS_MCP_URL"] = mcpUrl
                env["ANTIGRAVITY_MCP_URL"] = mcpUrl
                env["WORKSPACE_DIR"] = workspaceDir
                env["CI"] = "1"
                env["AGY_NO_UPDATE_PROMPT"] = "1"
                env["GODEBUG"] = "http2client=0"
                env["PATH"] = "${binDir.absolutePath}:${homeDir.absolutePath}/.local/bin:${prefixDir.absolutePath}/bin:${prefixDir.absolutePath}/bin/applets:/system/bin:/system/xbin"

                pb.redirectErrorStream(true)
                var proc: Process? = null
                var startErr: Exception? = null
                repeat(3) { attempt ->
                    try {
                        proc = pb.start()
                        startErr = null
                        return@repeat
                    } catch (ex: Exception) {
                        startErr = ex
                        val isBusy = ex.message?.contains("Text file busy", ignoreCase = true) == true ||
                            ex.message?.contains("ETXTBSY", ignoreCase = true) == true
                        if (!isBusy || attempt == 2) throw ex
                        runCatching { agyHubProcess?.destroyForcibly() }
                        Thread.sleep(160L * (attempt + 1))
                    }
                }
                val startedProc = proc ?: throw (startErr ?: IllegalStateException("Failed to launch agy"))
                agyHubProcess = startedProc
                _agyDiagnostics.value = _agyDiagnostics.value.copy(
                    agyBinaryFound = true,
                    agyBinaryPath = resolvedBin.absolutePath,
                    lastNotice = "Starting isolated agy --hub on port $hubPort..."
                )
                serverScope.launch {
                    try {
                        startedProc.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                Log.d("AgyHubDaemon", line)
                                appendServerLog("[agy-hub] $line")
                                com.jarvisai.assistant.ide.IdeDaemonLogBuffer.append("AGY", line)
                            }
                        }
                    } catch (_: Exception) {}
                }
                Log.i(TAG, "Started isolated Antigravity Hub daemon (${resolvedBin.absolutePath}) on 127.0.0.1:$hubPort")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Internal ProcessBuilder start attempt: ${e.message}")
                appendServerLog("${nowLogTimestamp()} [Hub Error] ProcessBuilder start failed: ${e.message}")
                _agyDiagnostics.value = _agyDiagnostics.value.copy(
                    lastNotice = "ProcessBuilder start failed: ${e.message}"
                )
            }
        } else {
            val expectedPath = File(homeDir, ".local/bin/agy").absolutePath
            appendServerLog(" [!] agy is not installed at $expectedPath yet: auto-installing official Antigravity CLI...")
            _agyDiagnostics.value = _agyDiagnostics.value.copy(
                agyBinaryFound = false,
                agyBinaryPath = expectedPath,
                lastNotice = "Auto-installing Antigravity CLI (agy) into ~/.local/bin/agy..."
            )
            // Automatically install official Antigravity CLI in background if not yet present!
            if (AgyCliManager.jobState.value?.running != true) {
                AgyCliManager.installAgy(context)
            }
        }

        // Strategy 2: Isolated Termux Bridge fallback (Port 2238 or RUN_COMMAND)
        val termuxBridge = com.jarvisai.assistant.termux.JarvisTermuxBridge(context)
        if (termuxBridge.isLocalBridgeDaemonActive() || (termuxBridge.isTermuxInstalled() && termuxBridge.hasRunCommandPermission())) {
            serverScope.launch {
                val launchScript = """
                    if ! pgrep -f 'hub-port=$hubPort' >/dev/null 2>&1; then
                        termux-wake-lock 2>/dev/null || true
                        mkdir -p /data/data/com.termux/files/home/.gemini/$dataDirName /data/data/com.termux/files/home/.jarvis-ai /data/data/com.termux/files/home/jarvis-workspace/projects
                        [ -f /sdcard/Download/JarvisAI/.gemini/jetski-standalone-oauth-token ] && cp -f /sdcard/Download/JarvisAI/.gemini/jetski-standalone-oauth-token /data/data/com.termux/files/home/.gemini/$dataDirName/jetski-standalone-oauth-token
                        [ -f /data/data/com.termux/files/home/.gemini/jetski-standalone-oauth-token ] && [ ! -f /data/data/com.termux/files/home/.gemini/$dataDirName/jetski-standalone-oauth-token ] && cp -f /data/data/com.termux/files/home/.gemini/jetski-standalone-oauth-token /data/data/com.termux/files/home/.gemini/$dataDirName/jetski-standalone-oauth-token
                        export TZ=${IndiaTimeUtils.IST_ZONE_ID}
                        export AGY_ENABLE_HUB=1
                        export ANTIGRAVITY_VSCODE_HOST=1
                        export ANTIGRAVITY_AUTH_SUCCESS_APP=vscode
                        export ANTIGRAVITY_APP_DATA_DIR=$dataDirName
                        export JARVIS_MCP_URL=$mcpUrl
                        export CI=1
                        export AGY_NO_UPDATE_PROMPT=1
                        export GODEBUG=http2client=0
                        cd /data/data/com.termux/files/home/jarvis-workspace/projects
                        nohup /data/data/com.termux/files/home/.local/bin/agy --hub --hub-port=$hubPort --app_data_dir=$dataDirName < /dev/null >> /data/data/com.termux/files/home/.jarvis-ai/jarvis_hub_$hubPort.log 2>&1 &
                    fi
                """.trimIndent()
                termuxBridge.executeCommand(launchScript, background = true, timeoutSeconds = 5)
                Log.i(TAG, "Dispatched isolated Antigravity Hub launch on port $hubPort via Termux bridge.")
            }
        }
    }

    private fun startContinuousHubMonitor(context: Context) {
        hubMonitorJob?.cancel()
        hubMonitorJob = serverScope.launch {
            val hubPort = getHubPort(context)
            val bridgePort = getBridgePort(context)
            var consecutiveProbes = 0
            while (isActive) {
                val intervalMs = if (currentHubState == "online" && currentCsrfToken.isNotBlank()) 15_000L else 1_000L
                delay(intervalMs)
                if (_status.value is LocalServerStatus.Stopping || _status.value is LocalServerStatus.Stopped) {
                    break
                }
                consecutiveProbes++
                val isOnline = probeHubTcp(
                    hubPort = hubPort,
                    logFailure = currentHubState != "online" && consecutiveProbes <= 4,
                    logSuccess = currentHubState != "online" || consecutiveProbes % 4 == 0
                )
                if (isOnline) {
                    var tokenAcquired = false
                    if (currentCsrfToken.isBlank()) {
                        val tok = fetchCsrfTokenWithLog(hubPort)
                        if (tok.isNotBlank()) {
                            currentCsrfToken = tok
                            tokenAcquired = true
                        }
                    }
                    if (currentHubState != "online" || tokenAcquired) {
                        verifyRealHubAuthAndLog(hubPort)
                        appendServerLog(" [✓] AGY Hub is online and listening on http://127.0.0.1:$hubPort")
                        logHubStateTransition("online")
                        appendServerLog("${nowLogTimestamp()} [Hub Monitor] ✅ AGY Hub detected ONLINE on http://127.0.0.1:$hubPort (CSRF token present: ${currentCsrfToken.isNotBlank()})")
                        _status.value = LocalServerStatus.Running(bridgePort)
                        refreshDiagnostics(context)
                    } else if (_status.value !is LocalServerStatus.Running) {
                        _status.value = LocalServerStatus.Running(bridgePort)
                    }
                } else {
                    if (currentHubState == "online") {
                        currentCsrfToken = ""
                        lastHubVerifiedLine = ""
                        logHubStateTransition("stopped")
                        appendServerLog("${nowLogTimestamp()} [Hub Monitor] ⚠️ AGY Hub port $hubPort became unreachable -> marked STOPPED")
                    }
                }
            }
        }
    }

    private fun cleanupLegacyExternalBridgeBinaries(context: Context) {
        val candidates = listOf(
            File(LocalEnvironmentManager.getBinDir(context), LEGACY_BRIDGE_BINARY_NAME),
            File(LocalEnvironmentManager.getHomeDir(context), "bin/$LEGACY_BRIDGE_BINARY_NAME")
        )
        candidates.forEach { f ->
            runCatching {
                if (f.exists()) {
                    f.delete()
                    Log.i(TAG, "Removed legacy third-party bridge binary: ${f.absolutePath}")
                }
            }
        }
    }

    suspend fun startServer(context: Context, forceRestart: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        cachedAppContext = appCtx

        hubLifecycleMutex.withLock {
            if (!forceRestart && _status.value is LocalServerStatus.Running && isAgyHubReachable() && JarvisIdeBridgeServer.instance.isActive()) {
                return@withLock Result.success(Unit)
            }

            _status.value = LocalServerStatus.Starting
            currentHubState = "idle"
            currentCsrfToken = ""
            lastHubVerifiedLine = ""
            IndiaTimeUtils.enforceProcessIndiaTimeZone()

            val port = getBridgePort(appCtx)
            val hubPort = getHubPort(appCtx)
            val homeDirFile = LocalEnvironmentManager.getHomeDir(appCtx).apply { mkdirs() }
            val projectsDir = File(homeDirFile, "projects").apply { mkdirs() }.absolutePath
            val logsDir = File(homeDirFile, "logs").apply { mkdirs() }
            val fileStamp = IndiaTimeUtils.nowFileStamp()
            val logFile = File(logsDir, "jarvis-ide-bridge_$fileStamp.log")
            activeLogFile = logFile

            cleanupLegacyExternalBridgeBinaries(appCtx)

            val agyBin = resolveAgyBinary(appCtx)
            val agyBinDisplay = agyBin?.absolutePath ?: File(homeDirFile, ".local/bin/agy").absolutePath
            val secToken = resolveOrGenerateSecurityToken(appCtx)
            val framedHeader = buildFramedHeader(secToken)
            com.jarvisai.assistant.agent.core.AgyOkHttpClient.setActiveFramedHeader(framedHeader)
            val localTimeStr = IndiaTimeUtils.nowBannerTimestamp()

            synchronized(logLock) {
                _serverLogs.value = emptyList()
            }

            appendServerLog("============================================================")
            appendServerLog("  ⚡ Jarvis AI IDE Server & AGY Hub Supervisor")
            appendServerLog("============================================================")
            appendServerLog("  • IDE Server Port:  http://127.0.0.1:$port")
            appendServerLog("  • Projects Dir:     $projectsDir")
            appendServerLog("  • Target Hub Port:  $hubPort")
            appendServerLog("  • AGY Binary:       $agyBinDisplay")
            appendServerLog("  • Security Token:   $secToken")
            appendServerLog("  • Framed Header:    $framedHeader")
            appendServerLog("  • India Time (IST): $localTimeStr")
            appendServerLog("  • Log File:         ${logFile.absolutePath}")
            appendServerLog("============================================================")
            appendServerLog("")

            // Start native pure-Kotlin Jarvis AI Bridge on port 2234 & Embedded MCP Server on ports 2236/2237
            JarvisIdeBridgeServer.instance.start(appCtx, port)
            com.jarvisai.assistant.mcp.server.AndroidLocalBridgeServer.instance.start(appCtx)
            appendServerLog(" 🚀 Jarvis AI IDE Server running at http://127.0.0.1:$port")
            appendServerLog("")
            appendServerLog(" ⚡ Auto-starting isolated Antigravity Hub on port $hubPort...")

            // Launch AGY Hub Daemon & Read-First In-Place Security Patcher
            ensureAgyHubDaemonRunningLocked(appCtx, agyBin)

            // Wait up to 4.5s for AGY Hub to come online on port 2235 before starting periodic monitor
            repeat(9) {
                if (_status.value is LocalServerStatus.Stopping || _status.value is LocalServerStatus.Stopped) {
                    return@withLock Result.success(Unit)
                }
                if (isAgyHubReachable()) {
                    if (currentCsrfToken.isBlank()) {
                        val tok = fetchCsrfTokenWithLog(hubPort)
                        if (tok.isNotBlank()) currentCsrfToken = tok
                    }
                    if (currentHubState != "online") {
                        verifyRealHubAuthAndLog(hubPort)
                        appendServerLog(" [✓] AGY Hub is online and listening on http://127.0.0.1:$hubPort")
                        logHubStateTransition("online")
                        appendServerLog("${nowLogTimestamp()} [Hub Monitor] ✅ AGY Hub detected ONLINE on http://127.0.0.1:$hubPort (CSRF token present: ${currentCsrfToken.isNotBlank()})")
                        val langUrl = com.jarvisai.assistant.data.remote.JarvisHubConfig.getLanguageServiceUrl(appCtx)
                        serverScope.launch {
                            com.jarvisai.assistant.data.remote.AntigravityApiService().preWarmLocalHubCatalog(langUrl)
                        }
                    }
                    _status.value = LocalServerStatus.Running(port)
                    startContinuousHubMonitor(appCtx)
                    startHealthWatchdog(appCtx)
                    refreshDiagnostics(appCtx)
                    return@withLock Result.success(Unit)
                }
                delay(500)
            }

            _status.value = LocalServerStatus.Running(port)
            startContinuousHubMonitor(appCtx)
            startHealthWatchdog(appCtx)
            refreshDiagnostics(appCtx)
            Result.success(Unit)
        }
    }

    private fun stopServerInternal() {
        _status.value = LocalServerStatus.Stopping
        healthCheckJob?.cancel()
        hubMonitorJob?.cancel()
        runCatching {
            agyHubProcess?.destroyForcibly()
        }
        agyHubProcess = null
        cachedAppContext?.let { ctx ->
            val hubPort = getHubPort(ctx)
            runCatching {
                ProcessBuilder("/system/bin/sh", "-c", "pkill -f 'hub-port=$hubPort' 2>/dev/null || true")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor()
            }
        }
        runCatching {
            JarvisIdeBridgeServer.instance.stop()
        }
        com.jarvisai.assistant.agent.core.AgyOkHttpClient.clearCsrfCache()
        com.jarvisai.assistant.data.remote.AntigravityApiService.invalidateHubInspectionCache()
        lastHubVerifiedLine = ""
        logHubStateTransition("stopped")
        appendServerLog("${nowLogTimestamp()} [Supervisor] Jarvis AI Local Server & AGY Hub stopped.")
        _agyDiagnostics.value = _agyDiagnostics.value.copy(
            agyHubReachable = false,
            lastNotice = "Antigravity Hub is stopped."
        )
        _status.value = LocalServerStatus.Stopped(0)
    }

    fun stopServer() {
        serverScope.launch {
            hubLifecycleMutex.withLock {
                stopServerInternal()
            }
        }
    }

    fun restartServer(context: Context? = cachedAppContext) {
        val appCtx = (context ?: cachedAppContext)?.applicationContext ?: return
        cachedAppContext = appCtx
        serverScope.launch {
            hubLifecycleMutex.withLock {
                _status.value = LocalServerStatus.Starting
                stopServerInternal()
                delay(250)
            }
            startServer(appCtx, forceRestart = true)
            refreshDiagnostics(appCtx)
        }
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
                delay(15000)
                if (_status.value is LocalServerStatus.Stopping || _status.value is LocalServerStatus.Stopped) {
                    break
                }
                try {
                    httpClient.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful && _status.value is LocalServerStatus.Running) {
                            Log.w(TAG, "Health check returned HTTP ${resp.code}")
                        }
                    }
                } catch (e: Exception) {
                    if (_status.value is LocalServerStatus.Running) {
                        Log.w(TAG, "Health watchdog reconnecting bridge: ${e.message}")
                        JarvisIdeBridgeServer.instance.start(context.applicationContext, getBridgePort(context))
                    }
                }
            }
        }
    }
}

