package com.jarvisai.assistant.terminal

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class CommandExecutionResult(
    val exitCode: Int,
    val output: String,
    val durationMs: Long
)

class LocalTerminalBridge private constructor() {

    companion object {
        val instance: LocalTerminalBridge by lazy { LocalTerminalBridge() }
    }

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getAppContext(): Context? = appContext

    fun getActiveHomePath(): String {
        val ctx = appContext
        return if (ctx != null) {
            LocalEnvironmentManager.getHomeDir(ctx).absolutePath
        } else {
            "/data/user/0/com.jarvisai.assistant/files/home"
        }
    }

    private fun buildProcessForShell(
        context: Context,
        shell: String,
        command: String,
        targetDir: File,
        prefix: String,
        tmp: String
    ): ProcessBuilder {
        val processBuilder = ProcessBuilder(shell, "-c", command)
        processBuilder.directory(targetDir)
        val env = processBuilder.environment()
        LocalEnvironmentManager.applyIsolatedToolEnvironment(context, env)
        env["PWD"] = targetDir.absolutePath
        return processBuilder
    }

    private fun startProcessWithShellFallback(
        context: Context,
        command: String,
        targetDir: File,
        prefix: String,
        tmp: String,
        forceDirectShell: Boolean = false
    ): Process {
        // Option A: Execute via PRoot Virtual Bind-Mount first when provisioned, keeping direct fallback
        if (!forceDirectShell && PRootRuntime.shouldUsePRoot(context)) {
            val prootInv = runCatching {
                PRootRuntime.buildActiveInvocation(
                    context = context,
                    command = command,
                    workingDir = targetDir.absolutePath,
                    loginShell = false
                )
            }.getOrNull()
            if (prootInv != null) {
                try {
                    val pb = ProcessBuilder(listOf(prootInv.executablePath) + prootInv.argv)
                    pb.directory(prootInv.hostWorkingDir)
                    val env = pb.environment()
                    env.clear()
                    env.putAll(prootInv.hostEnv)
                    return pb.start()
                } catch (e: Exception) {
                    android.util.Log.w(
                        "LocalTerminalBridge",
                        "PRoot execution fallback to native shell: ${e.message}"
                    )
                }
            }
        }

        val candidates = LocalEnvironmentManager.getShellCandidates(context)
        var lastError: Exception? = null
        for (shell in candidates) {
            try {
                if (shell != "/system/bin/sh" && shell.startsWith(context.filesDir.absolutePath)) {
                    val shellFile = File(shell)
                    if (!shellFile.canExecute()) {
                        runCatching {
                            shellFile.setExecutable(true, false)
                            android.system.Os.chmod(shell, 493)
                        }
                    }
                }
                return buildProcessForShell(context, shell, command, targetDir, prefix, tmp).start()
            } catch (e: Exception) {
                lastError = e
                val msg = e.message.orEmpty()
                val isPermissionIssue = msg.contains("Permission denied", ignoreCase = true) ||
                    msg.contains("error=13")
                if (isPermissionIssue && shell != "/system/bin/sh" && shell.startsWith(context.filesDir.absolutePath)) {
                    val repaired = runCatching {
                        File(shell).setExecutable(true, false)
                        android.system.Os.chmod(shell, 493)
                        buildProcessForShell(context, shell, command, targetDir, prefix, tmp).start()
                    }.getOrNull()
                    if (repaired != null) return repaired
                }
            }
        }
        throw lastError ?: IllegalStateException("No executable shell found for command execution.")
    }

    /**
     * Synchronous command execution with timeout and automatic direct-shell retry on PRoot exit 126/127.
     */
    suspend fun executeCommand(
        command: String,
        timeoutMs: Long = 60000,
        workingDir: String? = null
    ): CommandExecutionResult = withContext(Dispatchers.IO) {
        val first = executeCommandInternal(command, timeoutMs, workingDir, forceDirectShell = false)
        if ((first.exitCode == 126 || first.exitCode == 127 || first.exitCode == 13) &&
            appContext?.let { PRootRuntime.shouldUsePRoot(it) } == true
        ) {
            android.util.Log.w(
                "LocalTerminalBridge",
                "PRoot returned exit ${first.exitCode}; retrying command via Direct Sandbox Shell"
            )
            return@withContext executeCommandInternal(command, timeoutMs, workingDir, forceDirectShell = true)
        }
        first
    }

    private suspend fun executeCommandInternal(
        command: String,
        timeoutMs: Long,
        workingDir: String?,
        forceDirectShell: Boolean
    ): CommandExecutionResult = withContext(Dispatchers.IO) {
        val context = appContext ?: throw IllegalStateException("LocalTerminalBridge not initialized with Context")
        val startTime = System.currentTimeMillis()

        val prefix = LocalEnvironmentManager.getPrefixDir(context).absolutePath
        val targetDir = LocalEnvironmentManager.resolveWritableWorkingDir(context, workingDir)
        val tmp = LocalEnvironmentManager.getTmpDir(context).absolutePath

        val process = startProcessWithShellFallback(context, command, targetDir, prefix, tmp, forceDirectShell)
        val reader = process.inputStream.bufferedReader()
        val errReader = process.errorStream.bufferedReader()

        val output = StringBuilder()
        val outJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                reader.forEachLine {
                    synchronized(output) {
                        output.appendLine(it)
                    }
                }
            } catch (_: Exception) {}
        }
        val errJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                errReader.forEachLine {
                    synchronized(output) {
                        output.appendLine(it)
                    }
                }
            } catch (_: Exception) {}
        }

        val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!completed) {
            process.destroyForcibly()
            outJob.cancel()
            errJob.cancel()
            return@withContext CommandExecutionResult(
                -1,
                output.toString() + "\n[Command Timed Out]",
                System.currentTimeMillis() - startTime
            )
        }

        outJob.join()
        errJob.join()
        CommandExecutionResult(
            process.exitValue(),
            output.toString().trim(),
            System.currentTimeMillis() - startTime
        )
    }

    /**
     * Real-time streaming command execution with automatic direct-shell retry on PRoot exit 126/127.
     * Invokes [onLine] immediately as each line of stdout or stderr arrives.
     */
    suspend fun executeStreamingCommand(
        command: String,
        workingDir: String? = null,
        timeoutMs: Long = 180000,
        onLine: (line: String) -> Unit
    ): CommandExecutionResult = withContext(Dispatchers.IO) {
        val first = executeStreamingCommandInternal(command, workingDir, timeoutMs, forceDirectShell = false, onLine = onLine)
        if ((first.exitCode == 126 || first.exitCode == 127 || first.exitCode == 13) &&
            appContext?.let { PRootRuntime.shouldUsePRoot(it) } == true
        ) {
            onLine("[PRoot fallback -> Direct Sandbox Shell...]")
            return@withContext executeStreamingCommandInternal(command, workingDir, timeoutMs, forceDirectShell = true, onLine = onLine)
        }
        first
    }

    private suspend fun executeStreamingCommandInternal(
        command: String,
        workingDir: String?,
        timeoutMs: Long,
        forceDirectShell: Boolean,
        onLine: (line: String) -> Unit
    ): CommandExecutionResult = withContext(Dispatchers.IO) {
        val context = appContext ?: throw IllegalStateException("LocalTerminalBridge not initialized with Context")
        val startTime = System.currentTimeMillis()

        val prefix = LocalEnvironmentManager.getPrefixDir(context).absolutePath
        val targetDir = LocalEnvironmentManager.resolveWritableWorkingDir(context, workingDir)
        val tmp = LocalEnvironmentManager.getTmpDir(context).absolutePath

        val process = startProcessWithShellFallback(context, command, targetDir, prefix, tmp, forceDirectShell)
        val reader = process.inputStream.bufferedReader()
        val errReader = process.errorStream.bufferedReader()

        val fullOutput = StringBuilder()

        val outJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                reader.forEachLine { line ->
                    synchronized(fullOutput) { fullOutput.appendLine(line) }
                    onLine(line)
                }
            } catch (_: Exception) {}
        }

        val errJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                errReader.forEachLine { line ->
                    synchronized(fullOutput) { fullOutput.appendLine(line) }
                    onLine("[ERR] $line")
                }
            } catch (_: Exception) {}
        }

        val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!completed) {
            process.destroyForcibly()
            outJob.cancel()
            errJob.cancel()
            val timeoutMsg = "[Process Timed Out after ${timeoutMs / 1000}s]"
            onLine(timeoutMsg)
            return@withContext CommandExecutionResult(-1, fullOutput.toString() + "\n" + timeoutMsg, System.currentTimeMillis() - startTime)
        }

        outJob.join()
        errJob.join()

        CommandExecutionResult(
            process.exitValue(),
            fullOutput.toString().trim(),
            System.currentTimeMillis() - startTime
        )
    }

    fun listActiveSessionsJson(): JSONArray {
        val array = JSONArray()
        LocalTerminalManager.sessions.value.forEach { s ->
            val obj = JSONObject().apply {
                put("id", s.id)
                put("title", s.name)
                put("workingDirectory", s.workingDirectory)
                put("isExited", s.isExited.value)
                put("cols", s.ptyCols)
                put("rows", s.ptyRows)
            }
            array.put(obj)
        }
        return array
    }
}
