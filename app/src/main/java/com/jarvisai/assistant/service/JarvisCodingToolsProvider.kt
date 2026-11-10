package com.jarvisai.assistant.service

import android.content.Context
import com.jarvisai.assistant.core.SuperJarvisOrchestrator
import com.jarvisai.assistant.ide.ActiveCodeEditorRegistry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Declares and executes Gemini 2.0 Flash Multimodal Live Function Declarations
 * for Developer and Coding Agent operations.
 */
object JarvisCodingToolsProvider {

    const val TOOL_DELEGATE_TO_CODING_AGENT = "delegate_to_coding_agent"
    const val TOOL_EXECUTE_TERMINAL_COMMAND = "execute_terminal_command"
    const val TOOL_GET_ACTIVE_CODE_EDITOR_CONTENT = "get_active_code_editor_content"
    const val TOOL_INSPECT_GIT_STATUS = "inspect_git_status"

    /**
     * Returns the Gemini 2.0 Live Function Declarations schema for coding superpowers.
     */
    fun getCodingToolDeclarations(): JSONArray {
        val toolsArray = JSONArray()

        // Tool 1: delegate_to_coding_agent
        toolsArray.put(JSONObject().apply {
            put("name", TOOL_DELEGATE_TO_CODING_AGENT)
            put("description", "Dispatches a complex coding task, repository refactoring, bug fixing, or test run to a background autonomous agent (Claude Code CLI or OpenCode).")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("task_prompt", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Clear, actionable coding instruction (e.g. 'Fix failing Jest tests and git commit').")
                    })
                    put("project_path", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Target repository directory in Termux (default: /data/data/com.termux/files/home/projects).")
                    })
                    put("agent_type", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Agent model engine: 'claude' (Claude Code CLI) or 'opencode' (Multi-model router).")
                    })
                })
                put("required", JSONArray().apply {
                    put("task_prompt")
                })
            })
        })

        // Tool 2: execute_terminal_command
        toolsArray.put(JSONObject().apply {
            put("name", TOOL_EXECUTE_TERMINAL_COMMAND)
            put("description", "Executes a shell command inside the embedded native Termux Linux PTY bash environment.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("command", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The bash shell command to execute (e.g. 'git status', 'pnpm build', 'ls -la').")
                    })
                    put("working_dir", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Directory to execute command in (default: /data/data/com.termux/files/home).")
                    })
                    put("timeout_sec", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Optional timeout in seconds (default: 30).")
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

        return when (functionName) {
            TOOL_DELEGATE_TO_CODING_AGENT -> {
                val prompt = args.optString("task_prompt", args.optString("task", ""))
                val path = args.optString("project_path", args.optString("repo_path", "/data/data/com.termux/files/home/projects"))
                val agentType = args.optString("agent_type", args.optString("model_provider", "claude"))

                val task = orchestrator.delegateCodingTask(prompt, path, agentType)
                ToolExecutionOutcome(
                    callId = callId,
                    toolName = functionName,
                    executed = true,
                    status = "SUCCESS",
                    message = "Dispatched task ${task.id} to $agentType in $path",
                    spokenHinglishSummary = "Sir, maine $agentType coding agent ko task delegate kar diya hai. Background execution start ho chuka hai.",
                    responseJson = JSONObject().apply {
                        put("task_id", task.id)
                        put("agent_type", agentType)
                        put("status", "RUNNING")
                    }
                )
            }

            TOOL_EXECUTE_TERMINAL_COMMAND -> {
                val cmd = args.optString("command", "")
                val dir = args.optString("working_dir", "/data/data/com.termux/files/home")

                val output = orchestrator.executeTerminalCommand(cmd, dir)
                ToolExecutionOutcome(
                    callId = callId,
                    toolName = functionName,
                    executed = true,
                    status = "SUCCESS",
                    message = output,
                    spokenHinglishSummary = "Command execute ho gaya hai, sir. Output ready hai.",
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
                    "Abhi Sora Code Editor mein koi file open nahi hai, sir."
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
                val repoPath = args.optString("repo_path", "/data/data/com.termux/files/home/projects")
                val gitOutput = orchestrator.executeTerminalCommand("git status -s && git log -n 3 --oneline", repoPath)
                ToolExecutionOutcome(
                    callId = callId,
                    toolName = functionName,
                    executed = true,
                    status = "SUCCESS",
                    message = gitOutput,
                    spokenHinglishSummary = "Git status fetch kar liya hai, sir.",
                    responseJson = JSONObject().apply {
                        put("repo_path", repoPath)
                        put("git_status", gitOutput)
                    }
                )
            }

            else -> null
        }
    }
}
