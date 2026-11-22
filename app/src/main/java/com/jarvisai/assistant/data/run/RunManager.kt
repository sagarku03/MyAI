package com.jarvisai.assistant.data.run

import android.content.Context
import android.os.PowerManager
import com.jarvisai.assistant.data.remote.JarvisHubConfig
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class RunStatus { RUNNING, SUCCEEDED, FAILED, STOPPED }

data class RunCommandTemplate(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val command: String,
    val category: String = "Dev Server",
    val previewUrl: String? = null
)

data class RunEntry(
    val id: String,
    val name: String,
    val command: String,
    val cwd: String,
    val status: RunStatus,
    val exitCode: Int? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val detectedUrl: String? = null,
    val outputLines: List<String> = emptyList()
)

/**
 * Live App Run & Dev Server Manager for Agentra.
 * Runs project dev servers (Python, Node, Vite, FastAPI, static preview on preserved port 2239)
 * inside Agentra's isolated Linux environment with live log streaming, stop/restart, and WakeLock.
 */
object RunManager {

    private const val MAX_OUTPUT_LINES = 400
    private val URL_REGEX = Regex("""https?://(127\.0\.0\.1|localhost|0\.0\.0\.0):(\d+)(/[^\s"'<>]*)?""")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeProcesses = ConcurrentHashMap<String, Process>()
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _runs = MutableStateFlow<List<RunEntry>>(emptyList())
    val runs: StateFlow<List<RunEntry>> = _runs.asStateFlow()

    private val _customTemplates = MutableStateFlow<List<RunCommandTemplate>>(emptyList())
    val customTemplates: StateFlow<List<RunCommandTemplate>> = _customTemplates.asStateFlow()

    @Volatile
    private var wakeLock: PowerManager.WakeLock? = null

    fun builtInTemplates(): List<RunCommandTemplate> {
        val port = JarvisHubConfig.ISOLATED_WEB_PREVIEW_PORT
        return listOf(
            RunCommandTemplate(
                id = "tpl_py_http",
                name = "Python Static Server (:$port)",
                command = "python3 -m http.server $port --bind 127.0.0.1",
                category = "Web Preview",
                previewUrl = "http://127.0.0.1:$port"
            ),
            RunCommandTemplate(
                id = "tpl_vite",
                name = "Vite Dev Server (:$port)",
                command = "npm run dev -- --host 127.0.0.1 --port $port",
                category = "Node / Web",
                previewUrl = "http://127.0.0.1:$port"
            ),
            RunCommandTemplate(
                id = "tpl_fastapi",
                name = "FastAPI / Uvicorn (:$port)",
                command = "uvicorn main:app --host 127.0.0.1 --port $port --reload",
                category = "Python API",
                previewUrl = "http://127.0.0.1:$port"
            ),
            RunCommandTemplate(
                id = "tpl_node_start",
                name = "npm start (PORT=$port)",
                command = "PORT=$port npm start",
                category = "Node / Web",
                previewUrl = "http://127.0.0.1:$port"
            ),
            RunCommandTemplate(
                id = "tpl_pytest",
                name = "Run Pytest Suite",
                command = "pytest -v",
                category = "Test"
            )
        )
    }

    fun loadCustomTemplates(context: Context) {
        val file = File(context.filesDir, "agentra_run_templates.json")
        if (!file.exists()) return
        val parsed = runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                RunCommandTemplate(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString().take(8) },
                    name = o.optString("name", "Custom Run"),
                    command = o.optString("command"),
                    category = o.optString("category", "Custom"),
                    previewUrl = o.optString("previewUrl").takeIf { it.isNotBlank() }
                )
            }
        }.getOrDefault(emptyList())
        _customTemplates.value = parsed
    }

    fun addCustomTemplate(context: Context, name: String, command: String, previewUrl: String? = null) {
        val next = _customTemplates.value + RunCommandTemplate(
            name = name.trim().ifBlank { "Custom Command" },
            command = command.trim(),
            category = "Custom",
            previewUrl = previewUrl?.trim()?.takeIf { it.isNotBlank() }
        )
        saveCustomTemplates(context, next)
    }

    fun removeCustomTemplate(context: Context, id: String) {
        saveCustomTemplates(context, _customTemplates.value.filterNot { it.id == id })
    }

    private fun saveCustomTemplates(context: Context, list: List<RunCommandTemplate>) {
        _customTemplates.value = list
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("command", t.command)
                    .put("category", t.category)
                    .put("previewUrl", t.previewUrl.orEmpty())
            )
        }
        runCatching { File(context.filesDir, "agentra_run_templates.json").writeText(arr.toString(2)) }
    }

    fun startRun(
        context: Context,
        name: String,
        command: String,
        workingDir: File,
        defaultPreviewUrl: String? = null
    ): String {
        val appCtx = context.applicationContext
        val id = UUID.randomUUID().toString().take(8)
        val entry = RunEntry(
            id = id,
            name = name,
            command = command,
            cwd = workingDir.absolutePath,
            status = RunStatus.RUNNING,
            detectedUrl = defaultPreviewUrl,
            outputLines = listOf("$ $command", "[cwd: ${workingDir.absolutePath}]")
        )
        _runs.update { listOf(entry) + it.take(19) }
        updateWakeLock(appCtx)

        val job = scope.launch {
            try {
                val shell = LocalEnvironmentManager.getDefaultShell(appCtx)
                val envMap = linkedMapOf<String, String>()
                LocalEnvironmentManager.applyIsolatedToolEnvironment(appCtx, envMap)
                envMap["PORT"] = JarvisHubConfig.ISOLATED_WEB_PREVIEW_PORT.toString()

                val proc = ProcessBuilder(shell, "-c", command)
                    .directory(workingDir.apply { mkdirs() })
                    .redirectErrorStream(true)
                    .apply { environment().putAll(envMap) }
                    .start()

                activeProcesses[id] = proc
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                while (true) {
                    val line = reader.readLine() ?: break
                    val urlMatch = URL_REGEX.find(line)?.value?.replace("0.0.0.0", "127.0.0.1")
                    appendOutput(id, line, urlMatch)
                }

                val code = proc.waitFor()
                activeProcesses.remove(id)
                _runs.update { list ->
                    list.map { item ->
                        if (item.id == id && item.status == RunStatus.RUNNING) {
                            item.copy(
                                status = if (code == 0) RunStatus.SUCCEEDED else RunStatus.FAILED,
                                exitCode = code,
                                finishedAt = System.currentTimeMillis()
                            )
                        } else {
                            item
                        }
                    }
                }
            } catch (e: Exception) {
                activeProcesses.remove(id)
                appendOutput(id, "[error] ${e.message ?: "Process interrupted"}", null)
                _runs.update { list ->
                    list.map { item ->
                        if (item.id == id && item.status == RunStatus.RUNNING) {
                            item.copy(status = RunStatus.FAILED, exitCode = -1, finishedAt = System.currentTimeMillis())
                        } else {
                            item
                        }
                    }
                }
            } finally {
                activeJobs.remove(id)
                updateWakeLock(appCtx)
            }
        }
        activeJobs[id] = job
        return id
    }

    fun stopRun(context: Context, id: String) {
        activeProcesses.remove(id)?.let { proc ->
            runCatching { proc.destroy() }
            runCatching { proc.destroyForcibly() }
        }
        activeJobs.remove(id)?.cancel()
        _runs.update { list ->
            list.map { item ->
                if (item.id == id) {
                    item.copy(
                        status = RunStatus.STOPPED,
                        finishedAt = System.currentTimeMillis(),
                        outputLines = (item.outputLines + "[stopped by user]").takeLast(MAX_OUTPUT_LINES)
                    )
                } else {
                    item
                }
            }
        }
        updateWakeLock(context.applicationContext)
    }

    fun clearFinished() {
        _runs.update { list -> list.filter { it.status == RunStatus.RUNNING } }
    }

    private fun appendOutput(id: String, line: String, detectedUrl: String?) {
        _runs.update { list ->
            list.map { item ->
                if (item.id == id) {
                    item.copy(
                        detectedUrl = detectedUrl ?: item.detectedUrl,
                        outputLines = (item.outputLines + line).takeLast(MAX_OUTPUT_LINES)
                    )
                } else {
                    item
                }
            }
        }
    }

    @Synchronized
    private fun updateWakeLock(context: Context) {
        val anyRunning = _runs.value.any { it.status == RunStatus.RUNNING }
        if (anyRunning) {
            if (wakeLock == null) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Agentra:RunManager")
            }
            if (wakeLock?.isHeld == false) {
                runCatching { wakeLock?.acquire(30 * 60 * 1000L) }
            }
        } else {
            if (wakeLock?.isHeld == true) {
                runCatching { wakeLock?.release() }
            }
        }
    }
}
