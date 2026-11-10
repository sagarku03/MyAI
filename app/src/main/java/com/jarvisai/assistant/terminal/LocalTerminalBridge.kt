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

    suspend fun executeCommand(command: String, timeoutMs: Long = 30000): CommandExecutionResult = withContext(Dispatchers.IO) {
        val context = appContext ?: throw IllegalStateException("LocalTerminalBridge not initialized with Context")
        val startTime = System.currentTimeMillis()

        val prefix = LocalEnvironmentManager.getPrefixDir(context).absolutePath
        val home = LocalEnvironmentManager.getHomeDir(context).absolutePath
        val tmp = LocalEnvironmentManager.getTmpDir(context).absolutePath
        val shell = LocalEnvironmentManager.getDefaultShell(context)

        val processBuilder = ProcessBuilder(shell, "-c", command)
        processBuilder.directory(File(home))
        val env = processBuilder.environment()
        env["TERM"] = "xterm-256color"
        env["HOME"] = home
        env["PREFIX"] = prefix
        env["TMPDIR"] = tmp
        env["PATH"] = "$prefix/bin:$prefix/bin/applets:/system/bin:/system/xbin"

        val process = processBuilder.start()
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
