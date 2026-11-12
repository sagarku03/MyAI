package com.jarvisai.assistant.agent.claude

import android.content.Context
import android.util.Log
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Synchronizes Claude Code CLI settings with `~/.claude/settings.json`.
 * Manages model selection, thinking budget tokens, MCP server entries, and permission allowlists.
 */
object ClaudeConfigManager {
    private const val TAG = "ClaudeConfigManager"

    data class ClaudeSettingsState(
        val selectedModel: String = "claude-sonnet-4-5-20250929",
        val thinkingBudgetTokens: Int = 8192,
        val autoApproveSafeReads: Boolean = true,
        val autoApproveBashInSession: Boolean = false,
        val allowedTools: List<String> = listOf("Read", "Glob", "Grep", "LS")
    )

    val AVAILABLE_MODELS = listOf(
        "claude-sonnet-4-5-20250929" to "Claude Sonnet 4.5 (Coding Default)",
        "claude-opus-4-1-20250805" to "Claude Opus 4.1 (Deep Architecture)",
        "claude-3-5-haiku-latest" to "Claude Haiku 3.5 (Ultra Fast)"
    )

    private val _settings = MutableStateFlow(ClaudeSettingsState())
    val settings: StateFlow<ClaudeSettingsState> = _settings.asStateFlow()

    suspend fun loadSettings(context: Context): ClaudeSettingsState = withContext(Dispatchers.IO) {
        try {
            val homeDir = LocalEnvironmentManager.getHomeDir(context)
            val file = File(homeDir, ".claude/settings.json")
            if (!file.exists()) return@withContext _settings.value

            val root = JSONObject(file.readText())
            val model = root.optString("model", _settings.value.selectedModel)
            val envObj = root.optJSONObject("env")
            val thinkingTokens = envObj?.optString("MAX_THINKING_TOKENS")?.toIntOrNull()
                ?: _settings.value.thinkingBudgetTokens

            val permissionsObj = root.optJSONObject("permissions")
            val allowArr = permissionsObj?.optJSONArray("allow") ?: JSONArray()
            val allowList = mutableListOf<String>()
            for (i in 0 until allowArr.length()) {
                allowList.add(allowArr.getString(i))
            }

            val loaded = ClaudeSettingsState(
                selectedModel = model,
                thinkingBudgetTokens = thinkingTokens,
                allowedTools = if (allowList.isNotEmpty()) allowList else _settings.value.allowedTools
            )
            _settings.value = loaded
            loaded
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load ~/.claude/settings.json: ${e.message}")
            _settings.value
        }
    }

    suspend fun updateModelAndThinking(
        context: Context,
        modelId: String,
        thinkingBudgetTokens: Int
    ) = withContext(Dispatchers.IO) {
        val updated = _settings.value.copy(
            selectedModel = modelId,
            thinkingBudgetTokens = thinkingBudgetTokens.coerceIn(0, 64000)
        )
        _settings.value = updated
        persistToDisk(context, updated)
    }

    suspend fun addAllowedToolRule(context: Context, toolRule: String) = withContext(Dispatchers.IO) {
        val current = _settings.value
        if (toolRule !in current.allowedTools) {
            val updated = current.copy(allowedTools = current.allowedTools + toolRule)
            _settings.value = updated
            persistToDisk(context, updated)
        }
    }

    private fun persistToDisk(context: Context, state: ClaudeSettingsState) {
        try {
            val homeDir = LocalEnvironmentManager.getHomeDir(context)
            val claudeDir = File(homeDir, ".claude").apply { mkdirs() }
            val file = File(claudeDir, "settings.json")
            val root = if (file.exists()) {
                runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
            } else {
                JSONObject()
            }

            root.put("model", state.selectedModel)
            val envObj = root.optJSONObject("env") ?: JSONObject()
            envObj.put("MAX_THINKING_TOKENS", state.thinkingBudgetTokens.toString())
            root.put("env", envObj)

            val permsObj = root.optJSONObject("permissions") ?: JSONObject()
            permsObj.put("allow", JSONArray(state.allowedTools))
            root.put("permissions", permsObj)

            file.writeText(root.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save ~/.claude/settings.json: ${e.message}", e)
        }
    }
}
