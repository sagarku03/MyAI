package com.jarvisai.assistant.terminal.chroot

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class ChrootContainer(
    val path: String,
    val name: String,
    val mounted: Boolean = false,
    val runningProcesses: Int = 0
)

data class ChrootUiState(
    val checking: Boolean = false,
    val rootAvailable: Boolean? = null,
    val suDetails: String = "",
    val containers: List<ChrootContainer> = emptyList(),
    val busy: Boolean = false,
    val lastOutput: String = ""
)

/**
 * Isolated Rooted Linux Chroot Manager for Agentra.
 * Uses bundled `assets/chroot/chrootctl.sh` and stores Agentra containers strictly under
 * `/data/local/agentra` (isolated from AntiGem's `/data/local/antigem`).
 */
object ChrootManager {

    const val BASE_DIR = "/data/local/agentra"
    private const val CTL_ASSET = "chroot/chrootctl.sh"

    private val _state = MutableStateFlow(ChrootUiState())
    val state: StateFlow<ChrootUiState> = _state.asStateFlow()

    private fun ensureScript(context: Context): File {
        val out = File(context.applicationContext.filesDir, "agentra_chrootctl.sh")
        val content = context.assets.open(CTL_ASSET).bufferedReader().use { it.readText() }
        if (!out.exists() || out.length() != content.length.toLong()) {
            out.writeText(content)
            out.setExecutable(true, false)
        }
        return out
    }

    private fun runSu(command: String, timeoutSec: Long = 20L): Pair<Int, String> {
        return try {
            val proc = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val out = StringBuilder()
            BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    out.appendLine(line)
                }
            }
            val done = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!done) {
                proc.destroyForcibly()
                -1 to "Timed out after ${timeoutSec}s"
            } else {
                proc.exitValue() to out.toString().trim()
            }
        } catch (e: Exception) {
            -1 to (e.message ?: "su not available")
        }
    }

    suspend fun probeAndScan(context: Context) = withContext(Dispatchers.IO) {
        _state.update { it.copy(checking = true) }
        val (idCode, idOut) = runSu("id", 8L)
        val hasRoot = idCode == 0 && idOut.contains("uid=0")
        if (!hasRoot) {
            _state.update {
                it.copy(
                    checking = false,
                    rootAvailable = false,
                    suDetails = idOut.ifBlank { "Root (su) binary not found or permission denied." },
                    containers = emptyList()
                )
            }
            return@withContext
        }

        val ctl = ensureScript(context)
        val (_, listOut) = runSu("mkdir -p $BASE_DIR && ls -1 $BASE_DIR 2>/dev/null || true", 10L)
        val names = listOut.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("ls:") }
        val containers = names.map { name ->
            val path = "$BASE_DIR/$name"
            val (_, stOut) = runSu("sh '${ctl.absolutePath}' status '$path' 2>&1 || true", 10L)
            val mounted = stOut.contains("MOUNTED=1", ignoreCase = true) || stOut.contains("mounted", ignoreCase = true)
            val procs = Regex("""PROCS=(\d+)""").find(stOut)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            ChrootContainer(path = path, name = name, mounted = mounted, runningProcesses = procs)
        }

        _state.update {
            it.copy(
                checking = false,
                rootAvailable = true,
                suDetails = idOut.lines().firstOrNull().orEmpty(),
                containers = containers
            )
        }
    }

    suspend fun runAction(context: Context, action: String, containerPath: String) = withContext(Dispatchers.IO) {
        _state.update { it.copy(busy = true, lastOutput = "Running chrootctl $action on $containerPath...") }
        val ctl = ensureScript(context)
        val safePath = containerPath.replace("'", "'\"'\"'")
        val (code, out) = runSu("sh '${ctl.absolutePath}' $action '$safePath' 2>&1", 30L)
        _state.update {
            it.copy(
                busy = false,
                lastOutput = "[$action exit=$code]\n${out.ifBlank { "(no output)" }}"
            )
        }
        probeAndScan(context)
    }
}
