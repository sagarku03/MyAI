package com.jarvisai.assistant.terminal

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    object Stopping : LocalServerStatus()
    data class Stopped(val exitCode: Int? = null) : LocalServerStatus()
    data class Error(val message: String) : LocalServerStatus()
}

object LocalServerManager {

    private const val TAG = "LocalServerManager"
    private const val BRIDGE_ASSET = "agy_ide_bridge.xz"
    private const val BRIDGE_BINARY_NAME = "agy_ide_bridge"

    private val _status = MutableStateFlow<LocalServerStatus>(LocalServerStatus.Idle)
    val status: StateFlow<LocalServerStatus> = _status.asStateFlow()

    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var supervisorProcess: Process? = null
    private var healthCheckJob: Job? = null

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

    suspend fun startServer(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        if (_status.value is LocalServerStatus.Running) return@withContext Result.success(Unit)

        _status.value = LocalServerStatus.Starting
        val binDir = LocalEnvironmentManager.getBinDir(context)
        val bridgeBinary = File(binDir, BRIDGE_BINARY_NAME)

        // 1. Unpack compressed binary if missing or updated
        if (!bridgeBinary.exists() || bridgeBinary.length() < 1000) {
            try {
                unpackBridgeBinary(context, binDir)
            } catch (e: Exception) {
                Log.e(TAG, "Failed unpacking Go bridge binary: ${e.message}", e)
                _status.value = LocalServerStatus.Error("Failed to unpack Go bridge asset: ${e.message}")
                return@withContext Result.failure(e)
            }
        }

        // 2. Launch Go Supervisor Daemon
        val port = getBridgePort(context)
        val homeDir = LocalEnvironmentManager.getHomeDir(context).absolutePath
        val prefixDir = LocalEnvironmentManager.getPrefixDir(context).absolutePath
        val tmpDir = LocalEnvironmentManager.getTmpDir(context).absolutePath

        val cmd = listOf(
            bridgeBinary.absolutePath,
            "--port", port.toString(),
            "--bind", "127.0.0.1",
            "--dir", homeDir
        )

        try {
            val pb = ProcessBuilder(cmd)
            pb.directory(File(homeDir))
            val env = pb.environment()
            env["TERM"] = "xterm-256color"
            env["HOME"] = homeDir
            env["PREFIX"] = prefixDir
            env["TMPDIR"] = tmpDir
            env["PATH"] = "$prefixDir/bin:$prefixDir/bin/applets:/system/bin:/system/xbin"

            supervisorProcess = pb.start()

            // Monitor process termination in background
            serverScope.launch {
                val exitCode = supervisorProcess?.waitFor()
                Log.w(TAG, "Go bridge daemon exited with code: $exitCode")
                _status.value = LocalServerStatus.Stopped(exitCode)
                healthCheckJob?.cancel()
            }

            // 3. Poll /health until server responds
            val started = waitForHealthOk(context, timeoutSeconds = 15)
            if (started) {
                _status.value = LocalServerStatus.Running(port)
                startHealthWatchdog(context)
                Result.success(Unit)
            } else {
                supervisorProcess?.destroyForcibly()
                _status.value = LocalServerStatus.Error("Go bridge daemon failed to answer health check within 15s")
                Result.failure(IllegalStateException("Bridge health check timeout"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed launching Go bridge daemon", e)
            _status.value = LocalServerStatus.Error("Bridge launch error: ${e.message}")
            Result.failure(e)
        }
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
