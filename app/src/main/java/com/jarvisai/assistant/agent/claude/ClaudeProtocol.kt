package com.jarvisai.assistant.agent.claude

import org.json.JSONArray
import org.json.JSONObject

/**
 * Complete Protocol DTOs and JSON Serializers for Claude Code CLI streaming events,
 * subagent hierarchies, thinking budgets, rate limits, and interactive tool approvals.
 */
data class ClaudeCliStatus(
    val installed: Boolean = false,
    val running: Boolean = false,
    val version: String? = null,
    val authenticated: Boolean = false,
    val email: String? = null,
    val organizationName: String? = null,
    val planType: String? = null,
    val activeModel: String = "claude-sonnet-4-5-20250929"
) {
    companion object {
        fun fromJson(json: JSONObject): ClaudeCliStatus {
            return ClaudeCliStatus(
                installed = json.optBoolean("installed", false),
                running = json.optBoolean("running", false),
                version = json.optString("version").takeIf { it.isNotBlank() },
                authenticated = json.optBoolean("authenticated", false),
                email = json.optString("email").takeIf { it.isNotBlank() },
                organizationName = json.optString("organizationName").takeIf { it.isNotBlank() },
                planType = json.optString("planType").takeIf { it.isNotBlank() },
                activeModel = json.optString("activeModel", "claude-sonnet-4-5-20250929")
            )
        }
    }
}

data class ClaudeUsageInfo(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cacheCreationInputTokens: Int = 0,
    val cacheReadInputTokens: Int = 0,
    val totalCostUsd: Double = 0.0,
    val durationMs: Long = 0L
) {
    val totalTokens: Int
        get() = inputTokens + outputTokens + cacheCreationInputTokens + cacheReadInputTokens

    companion object {
        fun fromJson(json: JSONObject?): ClaudeUsageInfo {
            if (json == null) return ClaudeUsageInfo()
            return ClaudeUsageInfo(
                inputTokens = json.optInt("input_tokens", json.optInt("inputTokens", 0)),
                outputTokens = json.optInt("output_tokens", json.optInt("outputTokens", 0)),
                cacheCreationInputTokens = json.optInt(
                    "cache_creation_input_tokens",
                    json.optInt("cacheCreationInputTokens", 0)
                ),
                cacheReadInputTokens = json.optInt(
                    "cache_read_input_tokens",
                    json.optInt("cacheReadInputTokens", 0)
                ),
                totalCostUsd = json.optDouble("total_cost_usd", json.optDouble("costUsd", 0.0)),
                durationMs = json.optLong("duration_ms", json.optLong("durationMs", 0L))
            )
        }
    }
}

sealed class ClaudeLoginState {
    object LoggedOut : ClaudeLoginState()
    object Checking : ClaudeLoginState()
    data class AwaitingOAuthCallback(val authUrl: String, val userCode: String? = null) : ClaudeLoginState()
    data class LoggedIn(
        val email: String,
        val organization: String? = null,
        val plan: String = "Claude Max / Pro"
    ) : ClaudeLoginState()
    data class Error(val message: String) : ClaudeLoginState()
}

data class ClaudeRateLimits(
    val fiveHourWindowRemainingFraction: Double = 1.0,
    val sevenDayWindowRemainingFraction: Double = 1.0,
    val resetAtEpochSeconds: Long = 0L,
    val isThrottled: Boolean = false
) {
    companion object {
        fun fromJson(json: JSONObject?): ClaudeRateLimits {
            if (json == null) return ClaudeRateLimits()
            return ClaudeRateLimits(
                fiveHourWindowRemainingFraction = json.optDouble("fiveHourRemaining", 1.0),
                sevenDayWindowRemainingFraction = json.optDouble("sevenDayRemaining", 1.0),
                resetAtEpochSeconds = json.optLong("resetAt", 0L),
                isThrottled = json.optBoolean("isThrottled", false)
            )
        }
    }
}

data class ClaudeSubagentRun(
    val subagentId: String,
    val parentToolUseId: String? = null,
    val description: String,
    val prompt: String = "",
    val status: String = "running", // "running", "completed", "failed"
    val outputPreview: String = "",
    val startedAtMs: Long = System.currentTimeMillis()
)

data class ClaudeControlRequest(
    val sessionId: String,
    val action: String, // "interrupt", "approve_tool", "deny_tool", "set_thinking_budget", "set_model"
    val toolCallId: String? = null,
    val allowAlways: Boolean = false,
    val thinkingBudgetTokens: Int? = null,
    val modelId: String? = null
) {
    fun toJsonString(): String {
        return JSONObject().apply {
            put("sessionId", sessionId)
            put("action", action)
            toolCallId?.let { put("toolCallId", it) }
            put("allowAlways", allowAlways)
            thinkingBudgetTokens?.let { put("thinkingBudgetTokens", it) }
            modelId?.let { put("modelId", it) }
        }.toString()
    }
}

/**
 * Parses NDJSON or WebSocket frame payloads emitted by `claude --output-format stream-json`.
 */
object ClaudeProtocolParser {

    sealed class ParsedFrame {
        data class TextChunk(val text: String) : ParsedFrame()
        data class ThinkingChunk(val thinking: String) : ParsedFrame()
        data class ToolUseStart(
            val toolCallId: String,
            val toolName: String,
            val inputSummary: String,
            val rawInputJson: String
        ) : ParsedFrame()
        data class ToolUseResult(
            val toolCallId: String,
            val output: String,
            val isError: Boolean
        ) : ParsedFrame()
        data class PermissionRequired(
            val toolCallId: String,
            val toolName: String,
            val commandOrInput: String,
            val description: String
        ) : ParsedFrame()
        data class SubagentUpdate(val subagent: ClaudeSubagentRun) : ParsedFrame()
        data class TurnResult(
            val fullText: String,
            val usage: ClaudeUsageInfo,
            val sessionId: String?
        ) : ParsedFrame()
        data class ErrorFrame(val error: String) : ParsedFrame()
    }

    fun parseJsonFrame(rawJsonLine: String): List<ParsedFrame> {
        val frames = mutableListOf<ParsedFrame>()
        val trimmed = rawJsonLine.trim()
        if (!trimmed.startsWith("{")) return frames

        try {
            val root = JSONObject(trimmed)
            when (root.optString("type")) {
                "text_delta" -> {
                    val delta = root.optString("delta")
                    if (delta.isNotEmpty()) frames.add(ParsedFrame.TextChunk(delta))
                }
                "thought_delta", "thinking_delta" -> {
                    val thought = root.optString("thought", root.optString("thinking"))
                    if (thought.isNotEmpty()) frames.add(ParsedFrame.ThinkingChunk(thought))
                }
                "approval_required", "control_request" -> {
                    frames.add(
                        ParsedFrame.PermissionRequired(
                            toolCallId = root.optString("toolCallId", root.optString("id", "tool_req")),
                            toolName = root.optString("toolName", "Bash"),
                            commandOrInput = root.optString("input", root.optString("command", "")),
                            description = root.optString("description", "Requires user confirmation")
                        )
                    )
                }
                "subagent_progress" -> {
                    frames.add(
                        ParsedFrame.SubagentUpdate(
                            ClaudeSubagentRun(
                                subagentId = root.optString("subagentId", "subagent"),
                                description = root.optString("role", "Task Subagent"),
                                status = root.optString("status", "running")
                            )
                        )
                    )
                }
                "assistant" -> {
                    val msgObj = root.optJSONObject("message")
                    val contentArr = msgObj?.optJSONArray("content") ?: JSONArray()
                    for (i in 0 until contentArr.length()) {
                        val block = contentArr.getJSONObject(i)
                        when (block.optString("type")) {
                            "text" -> {
                                val text = block.optString("text")
                                if (text.isNotEmpty()) frames.add(ParsedFrame.TextChunk(text))
                            }
                            "thinking" -> {
                                val thinking = block.optString("thinking")
                                if (thinking.isNotEmpty()) frames.add(ParsedFrame.ThinkingChunk(thinking))
                            }
                            "tool_use" -> {
                                val id = block.optString("id", "tool_$i")
                                val name = block.optString("name", "Tool")
                                val inputObj = block.optJSONObject("input")
                                val summary = inputObj?.optString("command")
                                    ?: inputObj?.optString("file_path")
                                    ?: inputObj?.optString("pattern")
                                    ?: inputObj?.toString()?.take(120)
                                    ?: name
                                frames.add(
                                    ParsedFrame.ToolUseStart(
                                        toolCallId = id,
                                        toolName = name,
                                        inputSummary = summary,
                                        rawInputJson = inputObj?.toString() ?: "{}"
                                    )
                                )
                            }
                        }
                    }
                }
                "user" -> {
                    val msgObj = root.optJSONObject("message")
                    val contentArr = msgObj?.optJSONArray("content") ?: JSONArray()
                    for (i in 0 until contentArr.length()) {
                        val block = contentArr.getJSONObject(i)
                        if (block.optString("type") == "tool_result") {
                            val toolUseId = block.optString("tool_use_id", "")
                            val contentStr = block.optString("content", "")
                            val isErr = block.optBoolean("is_error", false)
                            frames.add(ParsedFrame.ToolUseResult(toolUseId, contentStr, isErr))
                        }
                    }
                }
                "result", "complete" -> {
                    val resultText = root.optString("result", root.optString("fullResponse", ""))
                    val usage = ClaudeUsageInfo.fromJson(root.optJSONObject("usage") ?: root)
                    val sessionId = root.optString("session_id").takeIf { it.isNotBlank() }
                    frames.add(ParsedFrame.TurnResult(resultText, usage, sessionId))
                }
                "error" -> {
                    frames.add(ParsedFrame.ErrorFrame(root.optString("message", "Claude execution error")))
                }
            }
        } catch (_: Exception) {
        }
        return frames
    }
}
