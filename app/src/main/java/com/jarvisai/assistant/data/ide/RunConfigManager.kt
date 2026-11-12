package com.jarvisai.assistant.data.ide

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Phase 4: Run Configuration Schema & `.ide/launch.json` Manager (`RunConfigManager`).
 * Supports custom build/run commands, environment variables, working directories,
 * launch targets ("terminal" or "webview"), and automatic project-type heuristics
 * with strict error boundaries against malformed or corrupted JSON files.
 */
data class RunConfiguration(
    val name: String,
    val type: String = "terminal", // "terminal" or "webview"
    val command: String? = null,
    val file: String? = null,
    val cwd: String? = null,
    val env: Map<String, String>? = null,
    val args: List<String>? = null,
    val isDefault: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("type", type)
        command?.let { put("command", it) }
        file?.let { put("file", it) }
        cwd?.let { put("cwd", it) }
        if (!env.isNullOrEmpty()) {
            val envObj = JSONObject()
            env.forEach { (k, v) -> envObj.put(k, v) }
            put("env", envObj)
        }
        if (!args.isNullOrEmpty()) {
            put("args", JSONArray(args))
        }
        put("isDefault", isDefault)
    }

    companion object {
        fun fromJson(obj: JSONObject): RunConfiguration {
            val envMap = mutableMapOf<String, String>()
            obj.optJSONObject("env")?.let { envObj ->
                val keys = envObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    envMap[key] = envObj.optString(key, "")
                }
            }
            val argsList = mutableListOf<String>()
            obj.optJSONArray("args")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val arg = arr.optString(i)
                    if (arg.isNotEmpty()) argsList.add(arg)
                }
            }

            return RunConfiguration(
                name = obj.optString("name", "Run Project").ifBlank { "Run Project" },
                type = obj.optString("type", "terminal").ifBlank { "terminal" },
                command = obj.optString("command").takeIf { it.isNotBlank() },
                file = obj.optString("file").takeIf { it.isNotBlank() },
                cwd = obj.optString("cwd").takeIf { it.isNotBlank() },
                env = envMap.takeIf { it.isNotEmpty() },
                args = argsList.takeIf { it.isNotEmpty() },
                isDefault = obj.optBoolean("isDefault", false)
            )
        }
    }
}

data class LaunchConfigFile(
    val version: String = "1.0",
    val configurations: List<RunConfiguration> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", version)
        val arr = JSONArray()
        configurations.forEach { arr.put(it.toJson()) }
        put("configurations", arr)
    }

    companion object {
        fun fromJson(obj: JSONObject): LaunchConfigFile {
            val version = obj.optString("version", "1.0")
            val arr = obj.optJSONArray("configurations") ?: JSONArray()
            val list = buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { item ->
                        runCatching { add(RunConfiguration.fromJson(item)) }
                    }
                }
            }
            return LaunchConfigFile(version = version, configurations = list)
        }
    }
}

object RunConfigManager {

    private const val IDE_FOLDER = ".ide"
    private const val LAUNCH_FILE = "launch.json"

    /**
     * Reads `.ide/launch.json` for [projectPath]. If absent or corrupted, falls back safely
     * to auto-detected project configurations without throwing or crashing.
     */
    fun loadConfigurations(projectPath: String): List<RunConfiguration> {
        val projectDir = File(projectPath)
        if (!projectDir.exists() || !projectDir.isDirectory) {
            return listOf(defaultShellConfiguration(projectPath))
        }

        val launchFile = File(File(projectDir, IDE_FOLDER), LAUNCH_FILE)
        if (launchFile.exists() && launchFile.isFile && launchFile.canRead()) {
            try {
                val raw = launchFile.readText()
                if (raw.isNotBlank()) {
                    val parsed = LaunchConfigFile.fromJson(JSONObject(raw))
                    if (parsed.configurations.isNotEmpty()) {
                        return parsed.configurations
                    }
                }
            } catch (_: Exception) {
                // Corrupted launch.json boundary: fall back to heuristic auto-detection
            }
        }

        return detectConfigurations(projectDir)
    }

    /**
     * Persists [configs] into `<projectPath>/.ide/launch.json`.
     */
    fun saveConfigurations(projectPath: String, configs: List<RunConfiguration>): Boolean {
        return try {
            val projectDir = File(projectPath)
            val ideDir = File(projectDir, IDE_FOLDER).apply { if (!exists()) mkdirs() }
            val launchFile = File(ideDir, LAUNCH_FILE)
            val payload = LaunchConfigFile(version = "1.0", configurations = configs)
            launchFile.writeText(payload.toJson().toString(2))
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Resolves the effective shell command for a given [RunConfiguration] and optional [activeFile].
     */
    fun buildExecutionCommand(config: RunConfiguration, projectPath: String, activeFile: File? = null): String {
        val workDir = config.cwd?.takeIf { it.isNotBlank() } ?: projectPath
        val envPrefix = config.env?.entries
            ?.joinToString(" ") { (k, v) -> "$k='${v.replace("'", "'\\''")}'" }
            ?.let { "$it " }
            ?: ""
        val argsSuffix = config.args?.takeIf { it.isNotEmpty() }
            ?.joinToString(" ") { "'${it.replace("'", "'\\''")}'" }
            ?.let { " $it" }
            ?: ""

        val baseCmd = when {
            !config.command.isNullOrBlank() -> config.command
            !config.file.isNullOrBlank() -> inferCommandForFile(File(workDir, config.file))
            activeFile != null -> inferCommandForFile(activeFile)
            else -> "ls -la"
        }

        return "cd '${workDir.replace("'", "'\\''")}' && ${envPrefix}${baseCmd}${argsSuffix}"
    }

    /**
     * Auto-detection heuristics for Node.js, Python, Rust, Go, C/C++, Web, and Shell projects.
     */
    fun detectConfigurations(projectDir: File): List<RunConfiguration> {
        val configs = mutableListOf<RunConfiguration>()
        val path = projectDir.absolutePath

        val packageJson = File(projectDir, "package.json")
        val mainPy = File(projectDir, "main.py")
        val appPy = File(projectDir, "app.py")
        val cargoToml = File(projectDir, "Cargo.toml")
        val goMod = File(projectDir, "go.mod")
        val indexHtml = File(projectDir, "index.html")
        val mainCpp = File(projectDir, "main.cpp")

        if (packageJson.exists()) {
            configs.add(
                RunConfiguration(
                    name = "npm start (Node.js)",
                    type = "terminal",
                    command = "npm start",
                    cwd = path,
                    isDefault = configs.isEmpty()
                )
            )
            if (File(projectDir, "index.js").exists()) {
                configs.add(
                    RunConfiguration(
                        name = "node index.js",
                        type = "terminal",
                        command = "node index.js",
                        file = "index.js",
                        cwd = path
                    )
                )
            }
        }

        if (mainPy.exists() || appPy.exists()) {
            val targetPy = if (mainPy.exists()) "main.py" else "app.py"
            configs.add(
                RunConfiguration(
                    name = "Python ($targetPy)",
                    type = "terminal",
                    command = "python3 $targetPy",
                    file = targetPy,
                    cwd = path,
                    isDefault = configs.isEmpty()
                )
            )
        }

        if (cargoToml.exists()) {
            configs.add(
                RunConfiguration(
                    name = "Cargo Run (Rust)",
                    type = "terminal",
                    command = "cargo run",
                    cwd = path,
                    isDefault = configs.isEmpty()
                )
            )
        }

        if (goMod.exists() || File(projectDir, "main.go").exists()) {
            configs.add(
                RunConfiguration(
                    name = "Go Run",
                    type = "terminal",
                    command = "go run .",
                    cwd = path,
                    isDefault = configs.isEmpty()
                )
            )
        }

        if (mainCpp.exists()) {
            configs.add(
                RunConfiguration(
                    name = "Build & Run C++ (g++)",
                    type = "terminal",
                    command = "g++ -O2 -std=c++17 main.cpp -o app_bin && ./app_bin",
                    file = "main.cpp",
                    cwd = path,
                    isDefault = configs.isEmpty()
                )
            )
        }

        if (indexHtml.exists()) {
            configs.add(
                RunConfiguration(
                    name = "Live Web Preview (index.html)",
                    type = "webview",
                    command = "python3 -m http.server 4321",
                    file = "index.html",
                    cwd = path,
                    isDefault = configs.isEmpty()
                )
            )
        }

        if (configs.isEmpty()) {
            configs.add(defaultShellConfiguration(path))
        }

        return configs
    }

    fun inferCommandForFile(file: File): String {
        val name = file.name
        return when (file.extension.lowercase()) {
            "py" -> "python3 '$name'"
            "js", "mjs", "cjs" -> "node '$name'"
            "ts" -> "npx tsx '$name'"
            "sh", "bash" -> "bash '$name'"
            "go" -> "go run '$name'"
            "rs" -> "rustc '$name' -o /tmp/rust_out && /tmp/rust_out"
            "cpp", "cc", "cxx" -> "g++ -O2 '$name' -o /tmp/cpp_out && /tmp/cpp_out"
            "c" -> "gcc -O2 '$name' -o /tmp/c_out && /tmp/c_out"
            "kt" -> "kotlinc '$name' -include-runtime -d /tmp/kt_out.jar && java -jar /tmp/kt_out.jar"
            "java" -> "java '$name'"
            "php" -> "php '$name'"
            "rb" -> "ruby '$name'"
            else -> "cat '$name'"
        }
    }

    private fun defaultShellConfiguration(projectPath: String): RunConfiguration {
        return RunConfiguration(
            name = "Interactive Workspace Shell",
            type = "terminal",
            command = "pwd && ls -la",
            cwd = projectPath,
            isDefault = true
        )
    }
}
