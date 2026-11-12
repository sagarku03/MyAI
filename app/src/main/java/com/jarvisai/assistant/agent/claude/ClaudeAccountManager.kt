package com.jarvisai.assistant.agent.claude

import android.content.Context
import android.util.Log
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.LocalTerminalBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Manages Claude Code CLI authentication status (`claude auth status --json`),
 * OAuth login URL generation, API key injection, and organization switching.
 */
object ClaudeAccountManager {
    private const val TAG = "ClaudeAccountManager"

    private val _loginState = MutableStateFlow<ClaudeLoginState>(ClaudeLoginState.LoggedOut)
    val loginState: StateFlow<ClaudeLoginState> = _loginState.asStateFlow()

    private val _cliStatus = MutableStateFlow(ClaudeCliStatus())
    val cliStatus: StateFlow<ClaudeCliStatus> = _cliStatus.asStateFlow()

    suspend fun refreshAuthStatus(context: Context): ClaudeLoginState = withContext(Dispatchers.IO) {
        _loginState.value = ClaudeLoginState.Checking
        try {
            val homeDir = LocalEnvironmentManager.getHomeDir(context)
            val claudeConfigFile = File(homeDir, ".claude.json")
            val claudeCredsFile = File(homeDir, ".claude/.credentials.json")

            // 1. Fast file-based check if ~/.claude.json or ~/.claude/.credentials.json exists
            if (claudeConfigFile.exists() && claudeConfigFile.canRead()) {
                val json = JSONObject(claudeConfigFile.readText())
                val accountObj = json.optJSONObject("oauthAccount")
                if (accountObj != null) {
                    val email = accountObj.optString("emailAddress", "Authenticated User")
                    val org = accountObj.optString("organizationName", "Personal")
                    val state = ClaudeLoginState.LoggedIn(email = email, organization = org)
                    _loginState.value = state
                    _cliStatus.value = _cliStatus.value.copy(
                        installed = true,
                        authenticated = true,
                        email = email,
                        organizationName = org
                    )
                    return@withContext state
                }
            }

            if (claudeCredsFile.exists() && claudeCredsFile.canRead()) {
                val creds = JSONObject(claudeCredsFile.readText())
                val oauth = creds.optJSONObject("claudeAiOauth")
                if (oauth != null && oauth.optString("accessToken").isNotBlank()) {
                    val subType = oauth.optString("subscriptionType", "Claude Pro/Max")
                    val state = ClaudeLoginState.LoggedIn(
                        email = "Claude OAuth Session",
                        organization = subType,
                        plan = subType
                    )
                    _loginState.value = state
                    _cliStatus.value = _cliStatus.value.copy(
                        installed = true,
                        authenticated = true,
                        planType = subType
                    )
                    return@withContext state
                }
            }

            // 2. Fallback to `claude auth status --json` via LocalTerminalBridge
            val res = LocalTerminalBridge.instance.executeCommand(
                command = "cd '${homeDir.absolutePath}' && (claude auth status --json 2>/dev/null || claude --version 2>/dev/null)",
                timeoutMs = 6000
            )
            val output = res.output.trim()
            if (output.startsWith("{")) {
                val json = JSONObject(output)
                val loggedIn = json.optBoolean("loggedIn", json.optBoolean("authenticated", false))
                val email = json.optString("email", "Claude User")
                val org = json.optString("organizationName", "")
                if (loggedIn) {
                    val state = ClaudeLoginState.LoggedIn(email = email, organization = org)
                    _loginState.value = state
                    _cliStatus.value = _cliStatus.value.copy(
                        installed = true,
                        authenticated = true,
                        email = email,
                        organizationName = org
                    )
                    return@withContext state
                }
            } else if (output.contains("claude", ignoreCase = true)) {
                _cliStatus.value = _cliStatus.value.copy(
                    installed = true,
                    version = output.lines().firstOrNull()
                )
            }

            _loginState.value = ClaudeLoginState.LoggedOut
            ClaudeLoginState.LoggedOut
        } catch (e: Exception) {
            Log.w(TAG, "refreshAuthStatus error: ${e.message}")
            val errState = ClaudeLoginState.Error(e.message ?: "Auth check failed")
            _loginState.value = errState
            errState
        }
    }

    suspend fun saveAnthropicApiKey(context: Context, apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val homeDir = LocalEnvironmentManager.getHomeDir(context)
            val claudeDir = File(homeDir, ".claude").apply { mkdirs() }
            val settingsFile = File(claudeDir, "settings.json")
            val existing = if (settingsFile.exists()) {
                runCatching { JSONObject(settingsFile.readText()) }.getOrDefault(JSONObject())
            } else {
                JSONObject()
            }
            val envObj = existing.optJSONObject("env") ?: JSONObject()
            envObj.put("ANTHROPIC_API_KEY", apiKey.trim())
            existing.put("env", envObj)
            settingsFile.writeText(existing.toString(2))

            val state = ClaudeLoginState.LoggedIn(
                email = "API Key (${apiKey.take(10)}...)",
                organization = "Anthropic Console",
                plan = "API Usage Tier"
            )
            _loginState.value = state
            _cliStatus.value = _cliStatus.value.copy(authenticated = true, email = state.email)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save Anthropic API key: ${e.message}", e)
            false
        }
    }

    suspend fun logout(context: Context) = withContext(Dispatchers.IO) {
        try {
            val homeDir = LocalEnvironmentManager.getHomeDir(context)
            File(homeDir, ".claude/.credentials.json").delete()
            _loginState.value = ClaudeLoginState.LoggedOut
            _cliStatus.value = _cliStatus.value.copy(authenticated = false, email = null)
        } catch (e: Exception) {
            Log.w(TAG, "Logout error: ${e.message}")
        }
    }
}
