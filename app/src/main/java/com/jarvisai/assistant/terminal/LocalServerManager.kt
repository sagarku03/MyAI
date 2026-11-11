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
        return if (context.packageName == "com.termux") 1234 else 1236
    }

    fun getBridgeUrl(context: Context): String {
        return "http://127.0.0.1:${getBridgePort(context)}"
    }

    /**
     * Automatically starts the Go IDE bridge and the Antigravity Hub daemon (port 1235)
     * in the background when the app launches.
     */
    fun autoStartOnAppLaunch(context: Context) {
        cachedAppContext = context.applicationContext
        serverScope.launch {
            startServer(context.applicationContext)
        }
    }

    /**
     * Ensures the local Antigravity Hub at 127.0.0.1:1235 is reachable, starting it if needed.
     */
    suspend fun ensureAgyHubAvailable(context: Context? = cachedAppContext): Boolean = withContext(Dispatchers.IO) {
        if (isAgyHubReachable()) return@withContext true
        val ctx = context ?: cachedAppContext ?: return@withContext false
        val agyBin = resolveAgyBinary(ctx)
        if (agyBin != null) {
            ensureAgyHubDaemonRunning(ctx, agyBin)
        }
        // Wait up to 3.5 seconds for port 1235 to become ready
        repeat(7) {
            if (isAgyHubReachable()) return@withContext true
            delay(500)
        }
        isAgyHubReachable()
    }

    private fun isAgyHubReachable(): Boolean {
        return try {
            val req = Request.Builder().url("http://127.0.0.1:1235/").get().build()
            httpClient.newCall(req).execute().use { resp ->
                resp.code in 200..499
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveAgyBinary(context: Context): File? {
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        val binDir = LocalEnvironmentManager.getBinDir(context)
        val candidates = listOf(
            File(homeDir, ".local/bin/agy"),
            File(binDir, "agy"),
            File(homeDir, "bin/agy")
        )
        return candidates.firstOrNull { it.exists() }?.also { bin ->
            if (!bin.canExecute()) {
                runCatching { bin.setExecutable(true, false) }
            }
        }
    }

    private fun ensureAgyHubDaemonRunning(context: Context, agyBin: File) {
        try {
            if (isAgyHubReachable()) {
                Log.i(TAG, "Antigravity Hub already active on 127.0.0.1:1235")
                return
            }
        } catch (_: Exception) {
            // Port 1235 not listening yet — proceed to spawn
        }

        try {
            val homeDir = LocalEnvironmentManager.getHomeDir(context).absolutePath
            val prefixDir = LocalEnvironmentManager.getPrefixDir(context).absolutePath
            val tmpDir = LocalEnvironmentManager.getTmpDir(context).absolutePath

            if (!agyBin.canExecute()) {
                agyBin.setExecutable(true, false)
            }

            val pb = ProcessBuilder(
                agyBin.absolutePath,
                "--hub",
                "--port", "1235"
            )
            pb.directory(File(homeDir))
            val env = pb.environment()
            env["HOME"] = homeDir
            env["PREFIX"] = prefixDir
            env["TMPDIR"] = tmpDir
            env["LD_LIBRARY_PATH"] = "$prefixDir/lib"
            env["PATH"] = "$homeDir/.local/bin:$prefixDir/bin:$prefixDir/bin/applets:/system/bin"

            agyHubProcess = pb.start()
            Log.i(TAG, "Antigravity Hub daemon started on 127.0.0.1:1235 via ${agyBin.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start agy hub daemon: ${e.message}")
        }
    }

    suspend fun startServer(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        cachedAppContext = context.applicationContext

        // Always ensure Antigravity Hub (port 1235) is started if agy binary is installed
        resolveAgyBinary(context)?.let { agyBin ->
            ensureAgyHubDaemonRunning(context, agyBin)
        }

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
        val homeDir = LocalEnvironmentManager.getHomeDir(context).absolutePath
        val prefixDir = LocalEnvironmentManager.getPrefixDir(context).absolutePath
        val tmpDir = LocalEnvironmentManager.getTmpDir(context).absolutePath

        val cmd = mutableListOf(
            bridgeBinary.absolutePath,
            "--port", port.toString(),
            "--bind", "127.0.0.1",
            "--dir", homeDir,
            "--hub-port", "1235"
        )
        resolveAgyBinary(context)?.let { agyBin ->
            cmd.addAll(listOf("--agy-bin", agyBin.absolutePath))
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
