package com.jarvisai.assistant.service

import android.content.Context
import com.jarvisai.assistant.core.SuperJarvisOrchestrator
import com.jarvisai.assistant.ide.ActiveCodeEditorRegistry
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.WorkspaceSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Declares and executes Gemini Live Function Declarations
 * for Antigravity In-App Coding Agent, GitHub Cloning, Terminal & Sora IDE operations.
 */
object JarvisCodingToolsProvider {

    const val TOOL_DELEGATE_TO_CODING_AGENT = "delegate_to_coding_agent"
    const val TOOL_EXECUTE_TERMINAL_COMMAND = "execute_terminal_command"
    const val TOOL_GET_ACTIVE_CODE_EDITOR_CONTENT = "get_active_code_editor_content"
    const val TOOL_INSPECT_GIT_STATUS = "inspect_git_status"

    /**
     * Hook registered by JarvisViewModel so Live Voice coding requests open a fresh
     * Antigravity chat session inside the app, generate code directly via Antigravity,
     * announce completion in Live Voice, and open the generated file in Sora IDE.
     */
    @Volatile
    var onDispatchToAntigravityChat: ((taskPrompt: String, projectPath: String) -> Unit)? = null

    private val downloadHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Returns Live Function Declarations schema for coding & workspace operations.
     */
    fun getCodingToolDeclarations(): JSONArray {
        val toolsArray = JSONArray()

        // Tool 1: delegate_to_coding_agent -> Routes to In-App Antigravity Agent
        toolsArray.put(JSONObject().apply {
            put("name", TOOL_DELEGATE_TO_CODING_AGENT)
            put(
                "description",
                "Dispatches ANY coding, web page (HTML/CSS/JS), app building, script creation, or bug-fixing task directly to the in-app Antigravity AI Agent. Creates a fresh Antigravity chat message, writes generated files to the local workspace, announces in voice when ready, and opens the file/IDE screen. NEVER use Accessibility or screen automation for coding."
            )
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("task_prompt", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Clear, complete coding instruction (e.g. 'Create a vibrant interactive Diwali festival HTML page with animated diyas and fireworks').")
                    })
                    put("project_path", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional workspace project directory path.")
                    })
                    put("agent_type", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Use 'antigravity' (default in-app Antigravity Agent) or 'opencode'.")
                    })
                })
                put("required", JSONArray().apply {
                    put("task_prompt")
                })
            })
        })

        // Tool 2: execute_terminal_command -> Executes local shell commands or directly clones GitHub repos
        toolsArray.put(JSONObject().apply {
            put("name", TOOL_EXECUTE_TERMINAL_COMMAND)
            put(
                "description",
                "Executes a shell command or git clone (including GitHub repository installation like 'git clone https://github.com/user/repo') directly inside the app's local writable workspace without Accessibility Service."
            )
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("command", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Shell command or git clone command to run (e.g. 'git clone https://github.com/sagarku03/MyAI', 'ls -la', 'python3 script.py').")
                    })
                    put("working_dir", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional local workspace directory path.")
                    })
                    put("timeout_sec", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Optional timeout in seconds (default 60).")
                    })
                })
                put("required", JSONArray().apply {
                    put("command")
                })
            })
        })

        // Tool 3: get_active_code_editor_content
        toolsArray.put(JSONObject().apply {
            put("name", TOOL_GET_ACTIVE_CODE_EDITOR_CONTENT)
            put("description", "Returns the file path, title, dirty state, and source code currently open in the embedded Sora Code Editor IDE.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            })
        })

        // Tool 4: inspect_git_status
        toolsArray.put(JSONObject().apply {
            put("name", TOOL_INSPECT_GIT_STATUS)
            put("description", "Inspects current git branches, modified files, uncommitted changes, and recent commit history.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("repo_path", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Path to git repository directory.")
                    })
                })
                put("required", JSONArray().apply {
                    put("repo_path")
                })
            })
        })

        return toolsArray
    }

    /**
     * Executes the coding tool call and produces verified ToolExecutionOutcome.
     */
    suspend fun handleCodingToolCall(
        context: Context,
        functionName: String,
        callId: String,
        args: JSONObject
    ): ToolExecutionOutcome? {
        val orchestrator = SuperJarvisOrchestrator.getInstance(context)
        val defaultHome = LocalEnvironmentManager.getHomeDir(context).absolutePath
        val defaultProjects = "$defaultHome/projects"

        return when (functionName) {
            TOOL_DELEGATE_TO_CODING_AGENT -> {
                val prompt = args.optString("task_prompt", args.optString("task", "")).trim()
                val rawPath = args.optString("project_path", args.optString("repo_path", "")).trim()
                val path = LocalEnvironmentManager
                    .resolveWritableWorkingDir(context, rawPath.ifBlank { defaultProjects }).absolutePath

                // Prefer Antigravity Agent always unless user explicitly has opencode binary
                val requestedType = args.optString("agent_type", args.optString("model_provider", "antigravity")).trim()
                val agentType = if (requestedType.equals("opencode", ignoreCase = true) &&
                    LocalEnvironmentManager.findCliBinary(context, "opencode") != null
                ) {
                    "opencode"
                } else {
                    "antigravity"
                }

                if (prompt.isBlank()) {
                    return ToolExecutionOutcome(
                        callId = callId,
                        toolName = functionName,
                        executed = false,
                        status = "ERROR",
                        errorCode = "MISSING_PROMPT",
                        message = "Coding task description missing.",
                        spokenHinglishSummary = "Sir, coding ke liye kya banana hai wo clear nahi mila.",
                        responseJson = JSONObject().put("error", "MISSING_PROMPT")
                    )
                }

                val chatDispatcher = onDispatchToAntigravityChat
                if (agentType == "antigravity" && chatDispatcher != null) {
                    chatDispatcher.invoke(prompt, path)
                    ToolExecutionOutcome(
                        callId = callId,
                        toolName = functionName,
                        executed = true,
                        status = "SUCCESS",
                        message = "Dispatched '$prompt' to in-app Antigravity Agent (New Chat + IDE) in $path",
                        spokenHinglishSummary = "Maine Antigravity agent ko naye chat mein ye coding task de diya hai. Jaise hi code ready hoga main aapko bata dunga aur IDE/file open kar dunga.",
                        responseJson = JSONObject().apply {
                            put("agent_type", "antigravity")
                            put("status", "DISPATCHED_TO_ANTIGRAVITY_CHAT")
                            put("workspace", path)
                        }
                    )
                } else {
                    val task = orchestrator.delegateCodingTask(prompt, path, agentType)
                    ToolExecutionOutcome(
                        callId = callId,
                        toolName = functionName,
                        executed = true,
                        status = "SUCCESS",
                        message = "Dispatched task ${task.id} to $agentType in $path",
                        spokenHinglishSummary = "Maine Antigravity coding engine par task start kar diya hai. Ready hote hi file workspace aur IDE mein open ho jayegi.",
                        responseJson = JSONObject().apply {
                            put("task_id", task.id)
                            put("agent_type", agentType)
                            put("status", "RUNNING")
                        }
                    )
                }
            }

            TOOL_EXECUTE_TERMINAL_COMMAND -> {
                val cmd = args.optString("command", "").trim()
                val rawDir = args.optString("working_dir", "").trim()
                val isGitCloneCmd = Regex("(?i)\\bgit\\s+clone\\b").containsMatchIn(cmd) ||
                    cmd.contains("github.com/", ignoreCase = true)
                val targetBaseDir = rawDir.ifBlank { if (isGitCloneCmd) defaultProjects else defaultHome }
                val dir = LocalEnvironmentManager
                    .resolveWritableWorkingDir(context, targetBaseDir).absolutePath

                // Fast & guaranteed pure-Kotlin GitHub repository installer if `git clone` is requested
                if (isGitCloneCmd) {
                    val hasGitBinary = LocalEnvironmentManager.findCliBinary(context, "git") != null
                    if (hasGitBinary) {
                        val shellOut = runCatching { orchestrator.executeTerminalCommand(cmd, dir) }.getOrNull()
                        if (shellOut != null &&
                            !shellOut.contains("not found", ignoreCase = true) &&
                            !shellOut.contains("Permission denied", ignoreCase = true) &&
                            !shellOut.contains("fatal:", ignoreCase = true)
                        ) {
                            WorkspaceSyncManager.notifyExternalChange(dir)
                            return ToolExecutionOutcome(
                                callId = callId,
                                toolName = functionName,
                                executed = true,
                                status = "SUCCESS",
                                message = shellOut,
                                spokenHinglishSummary = "Git repository clone ho chuka hai $dir mein.",
                                responseJson = JSONObject().apply {
                                    put("command", cmd)
                                    put("working_dir", dir)
                                    put("output", shellOut)
                                }
                            )
                        }
                    }

                    val githubClone = cloneGithubRepoDirect(cmd, dir)
                    if (githubClone.first) {
                        return ToolExecutionOutcome(
                            callId = callId,
                            toolName = functionName,
                            executed = true,
                            status = "SUCCESS",
                            message = githubClone.second,
                            spokenHinglishSummary = githubClone.second,
                            responseJson = JSONObject().apply {
                                put("command", cmd)
                                put("working_dir", dir)
                                put("output", githubClone.second)
                            }
                        )
                    } else {
                        return ToolExecutionOutcome(
                            callId = callId,
                            toolName = functionName,
                            executed = false,
                            status = "ERROR",
                            errorCode = "GIT_CLONE_FAILED",
                            message = githubClone.second,
                            spokenHinglishSummary = githubClone.second,
                            responseJson = JSONObject().apply {
                                put("command", cmd)
                                put("error", githubClone.second)
                            }
                        )
                    }
                }

                val output = orchestrator.executeTerminalCommand(cmd, dir)
                ToolExecutionOutcome(
                    callId = callId,
                    toolName = functionName,
                    executed = true,
                    status = "SUCCESS",
                    message = output,
                    spokenHinglishSummary = "Command execute ho gaya hai. Output ready hai.",
                    responseJson = JSONObject().apply {
                        put("command", cmd)
                        put("working_dir", dir)
                        put("output", output)
                    }
                )
            }

            TOOL_GET_ACTIVE_CODE_EDITOR_CONTENT -> {
                val activePath = ActiveCodeEditorRegistry.activeFilePath.orEmpty()
                val activeTitle = ActiveCodeEditorRegistry.activeFileTitle.orEmpty()
                val content = ActiveCodeEditorRegistry.activeContent
                val isDirty = ActiveCodeEditorRegistry.isDirty

                val summary = if (activePath.isNotBlank()) {
                    "Sora IDE mein $activeTitle file open hai (${content.lines().size} lines)."
                } else {
                    "Abhi Sora Code Editor mein koi file open nahi hai."
                }

                ToolExecutionOutcome(
                    callId = callId,
                    toolName = functionName,
                    executed = true,
                    status = "SUCCESS",
                    message = summary,
                    spokenHinglishSummary = summary,
                    responseJson = JSONObject().apply {
                        put("file_path", activePath)
                        put("file_title", activeTitle)
                        put("is_dirty", isDirty)
                        put("line_count", if (content.isEmpty()) 0 else content.lines().size)
                        put("content", content)
                    }
                )
            }

            TOOL_INSPECT_GIT_STATUS -> {
                val rawRepo = args.optString("repo_path", "").trim()
                val repoPath = LocalEnvironmentManager
                    .resolveWritableWorkingDir(context, rawRepo.ifBlank { defaultProjects }).absolutePath
                val gitOutput = orchestrator.executeTerminalCommand("git status -s && git log -n 3 --oneline", repoPath)
                ToolExecutionOutcome(
                    callId = callId,
                    toolName = functionName,
                    executed = true,
                    status = "SUCCESS",
                    message = gitOutput,
                    spokenHinglishSummary = "Git status fetch kar liya hai.",
                    responseJson = JSONObject().apply {
                        put("repo_path", repoPath)
                        put("git_status", gitOutput)
                    }
                )
            }

            else -> null
        }
    }

    /**
     * Pure-Kotlin fallback for `git clone https://github.com/<owner>/<repo>` when `git` binary
     * is not present on Android. Downloads the archive ZIP from GitHub and extracts it into
     * `baseDir/<repo>`, then notifies WorkspaceSyncManager & opens the entry file in IDE.
     */
    private suspend fun cloneGithubRepoDirect(rawCommandOrUrl: String, baseDir: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val match = Regex("https?://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)")
                .find(rawCommandOrUrl)
                ?: return@withContext false to "Valid GitHub repository URL nahi mila. Kripya 'https://github.com/username/repo' format dein."

            val owner = match.groupValues[1]
            val repo = match.groupValues[2].removeSuffix(".git")
            val destRoot = File(baseDir, repo).apply { mkdirs() }

            val candidateBranches = listOf("main", "master")
            var downloadedStreamBytes: ByteArray? = null
            var lastHttpErr = ""

            for (branch in candidateBranches) {
                val zipUrl = "https://github.com/$owner/$repo/archive/refs/heads/$branch.zip"
                runCatching {
                    val req = Request.Builder().url(zipUrl).get().build()
                    downloadHttpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            downloadedStreamBytes = resp.body?.bytes()
                        } else {
                            lastHttpErr = "HTTP ${resp.code}"
                        }
                    }
                }.onFailure { e ->
                    lastHttpErr = e.localizedMessage ?: "Network error"
                }
                if (downloadedStreamBytes != null) break
            }

            val bytes = downloadedStreamBytes
                ?: return@withContext false to "GitHub repo '$owner/$repo' download nahi ho paya ($lastHttpErr). Check karein ki repo public hai aur internet chal raha hai."

            var extractedCount = 0
            var firstInterestingFile: File? = null
            runCatching {
                val destRootCanonical = destRoot.canonicalFile.path.let {
                    if (it.endsWith(File.separator)) it else it + File.separator
                }
                ZipInputStream(bytes.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val rawEntryName = entry.name
                        // GitHub zip puts everything under "<repo>-<branch>/"
                        val strippedRel = rawEntryName.substringAfter('/', missingDelimiterValue = rawEntryName)
                            .trimStart('/', '\\')
                        if (strippedRel.isNotBlank() && !entry.isDirectory) {
                            val outFile = File(destRoot, strippedRel)
                            val canonicalOut = outFile.canonicalPath
                            if (!canonicalOut.startsWith(destRootCanonical)) {
                                throw SecurityException("Blocked unsafe zip entry: $rawEntryName")
                            }
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { out ->
                                zis.copyTo(out)
                            }
                            extractedCount++
                            val lowerName = outFile.name.lowercase()
                            if (firstInterestingFile == null ||
                                lowerName == "readme.md" ||
                                lowerName == "index.html" ||
                                lowerName == "package.json"
                            ) {
                                firstInterestingFile = outFile
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }.onFailure { e ->
                return@withContext false to "Repository extract karte waqt error aaya: ${e.localizedMessage}"
            }

            WorkspaceSyncManager.notifyExternalChange(destRoot.absolutePath)
            firstInterestingFile?.let { WorkspaceSyncManager.requestOpenFileInIde(it.absolutePath) }

            true to "✓ GitHub repository '$owner/$repo' install ho gaya ($extractedCount files saved to ${destRoot.absolutePath})! File Explorer aur IDE mein open kar diya hai."
        }
}
