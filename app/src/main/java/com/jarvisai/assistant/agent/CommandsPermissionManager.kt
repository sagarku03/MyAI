package com.jarvisai.assistant.agent

import android.content.Context
import com.jarvisai.assistant.data.remote.JarvisHubConfig
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ArtifactReviewMode(val wireValue: String, val label: String) {
    TURBO("ARTIFACT_REVIEW_MODE_TURBO", "Auto (Turbo)"),
    ALWAYS("ARTIFACT_REVIEW_MODE_ALWAYS", "Always Ask");

    companion object {
        fun fromWire(value: String?): ArtifactReviewMode {
            return if (value?.contains("TURBO", ignoreCase = true) == true) TURBO else ALWAYS
        }
    }
}

enum class CommandExecutionPolicy(val wireValue: String, val label: String) {
    EAGER("AGENT_EXECUTION_POLICY_EAGER", "Auto-run"),
    AUTO("AGENT_EXECUTION_POLICY_AUTO", "Smart safety"),
    OFF("AGENT_EXECUTION_POLICY_OFF", "Ask first");

    companion object {
        fun fromWire(value: String?): CommandExecutionPolicy {
            return when {
                value?.contains("EAGER", ignoreCase = true) == true -> EAGER
                value?.contains("AUTO", ignoreCase = true) == true -> AUTO
                else -> OFF
            }
        }
    }
}

enum class FileAccessPolicy(val wireValue: String, val label: String) {
    ALLOW("AGENT_SETTING_POLICY_ALLOW", "Allow All"),
    ASK("AGENT_SETTING_POLICY_ASK", "Ask First");

    companion object {
        fun fromWire(value: String?): FileAccessPolicy {
            return if (value?.contains("ALLOW", ignoreCase = true) == true) ALLOW else ASK
        }
    }
}

enum class SecurityPreset {
    DEFAULT,
    FULL_MACHINE,
    TURBO,
    CUSTOM
}

data class PermissionGrantRule(
    val action: String,
    val pattern: String,
    val decision: String // ALLOW, DENY, ASK
) {
    val rawRule: String get() = "${action.trim().lowercase()}(${pattern.trim()})"

    companion object {
        fun parse(raw: String, decision: String): PermissionGrantRule {
            val open = raw.indexOf('(')
            val close = raw.lastIndexOf(')')
            return if (open > 0 && close > open) {
                PermissionGrantRule(
                    action = raw.substring(0, open).trim().lowercase(),
                    pattern = raw.substring(open + 1, close).trim(),
                    decision = decision.uppercase()
                )
            } else {
                PermissionGrantRule(action = "bash", pattern = raw.trim(), decision = decision.uppercase())
            }
        }
    }
}

data class SecurityPolicyState(
    val artifactReviewMode: ArtifactReviewMode = ArtifactReviewMode.TURBO,
    val commandExecutionPolicy: CommandExecutionPolicy = CommandExecutionPolicy.AUTO,
    val nonWorkspaceFileAccess: FileAccessPolicy = FileAccessPolicy.ALLOW,
    val enableTerminalSandbox: Boolean = false,
    val allowRules: List<String> = listOf("bash(git status)", "bash(ls *)", "read(*)"),
    val denyRules: List<String> = listOf("bash(rm -rf /*)"),
    val askRules: List<String> = emptyList()
) {
    val activePreset: SecurityPreset
        get() = when {
            commandExecutionPolicy == CommandExecutionPolicy.OFF && nonWorkspaceFileAccess == FileAccessPolicy.ASK -> SecurityPreset.DEFAULT
            commandExecutionPolicy == CommandExecutionPolicy.OFF && nonWorkspaceFileAccess == FileAccessPolicy.ALLOW -> SecurityPreset.FULL_MACHINE
            commandExecutionPolicy == CommandExecutionPolicy.EAGER && nonWorkspaceFileAccess == FileAccessPolicy.ALLOW -> SecurityPreset.TURBO
            else -> SecurityPreset.CUSTOM
        }

    val allRules: List<PermissionGrantRule>
        get() = buildList {
            allowRules.forEach { add(PermissionGrantRule.parse(it, "ALLOW")) }
            denyRules.forEach { add(PermissionGrantRule.parse(it, "DENY")) }
            askRules.forEach { add(PermissionGrantRule.parse(it, "ASK")) }
        }
}

/**
 * Persists and enforces Antigravity Commands & Permissions policies across chat,
 * shell tools, and workspace edits. Mirrors state to both SharedPreferences and
 * `~/.gemini/jarvis-antigravity/security_settings.json`.
 */
object CommandsPermissionManager {

    private const val PREFS_NAME = "jarvis_security_policies"
    private const val KEY_ARTIFACT_MODE = "artifact_review_mode"
    private const val KEY_EXEC_POLICY = "command_exec_policy"
    private const val KEY_FILE_ACCESS = "file_access_policy"
    private const val KEY_SANDBOX = "terminal_sandbox"
    private const val KEY_ALLOW_RULES = "allow_rules_json"
    private const val KEY_DENY_RULES = "deny_rules_json"
    private const val KEY_ASK_RULES = "ask_rules_json"

    private val _state = MutableStateFlow(SecurityPolicyState())
    val state: StateFlow<SecurityPolicyState> = _state.asStateFlow()

    @Volatile
    private var loaded = false

    fun load(context: Context): SecurityPolicyState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val loadedState = SecurityPolicyState(
            artifactReviewMode = ArtifactReviewMode.fromWire(prefs.getString(KEY_ARTIFACT_MODE, ArtifactReviewMode.TURBO.wireValue)),
            commandExecutionPolicy = CommandExecutionPolicy.fromWire(prefs.getString(KEY_EXEC_POLICY, CommandExecutionPolicy.EAGER.wireValue)),
            nonWorkspaceFileAccess = FileAccessPolicy.fromWire(prefs.getString(KEY_FILE_ACCESS, FileAccessPolicy.ALLOW.wireValue)),
            enableTerminalSandbox = prefs.getBoolean(KEY_SANDBOX, false),
            allowRules = decodeStringList(prefs.getString(KEY_ALLOW_RULES, null))
                ?: listOf("bash(git status)", "bash(ls *)", "read(*)"),
            denyRules = decodeStringList(prefs.getString(KEY_DENY_RULES, null))
                ?: listOf("bash(rm -rf /*)"),
            askRules = decodeStringList(prefs.getString(KEY_ASK_RULES, null))
                ?: emptyList()
        )
        _state.value = loadedState
        loaded = true
        return loadedState
    }

    private fun ensureLoaded(context: Context): SecurityPolicyState {
        if (!loaded) return load(context)
        return _state.value
    }

    fun applyPreset(context: Context, preset: SecurityPreset) {
        val cur = ensureLoaded(context)
        val next = when (preset) {
            SecurityPreset.DEFAULT -> cur.copy(
                commandExecutionPolicy = CommandExecutionPolicy.OFF,
                nonWorkspaceFileAccess = FileAccessPolicy.ASK
            )
            SecurityPreset.FULL_MACHINE -> cur.copy(
                commandExecutionPolicy = CommandExecutionPolicy.OFF,
                nonWorkspaceFileAccess = FileAccessPolicy.ALLOW
            )
            SecurityPreset.TURBO -> cur.copy(
                commandExecutionPolicy = CommandExecutionPolicy.EAGER,
                nonWorkspaceFileAccess = FileAccessPolicy.ALLOW,
                artifactReviewMode = ArtifactReviewMode.TURBO
            )
            SecurityPreset.CUSTOM -> cur.copy(
                commandExecutionPolicy = CommandExecutionPolicy.AUTO
            )
        }
        save(context, next)
    }

    fun setArtifactReviewMode(context: Context, mode: ArtifactReviewMode) {
        save(context, ensureLoaded(context).copy(artifactReviewMode = mode))
    }

    fun setCommandExecutionPolicy(context: Context, policy: CommandExecutionPolicy) {
        save(context, ensureLoaded(context).copy(commandExecutionPolicy = policy))
    }

    fun setNonWorkspaceFileAccess(context: Context, policy: FileAccessPolicy) {
        save(context, ensureLoaded(context).copy(nonWorkspaceFileAccess = policy))
    }

    fun setTerminalSandbox(context: Context, enabled: Boolean) {
        save(context, ensureLoaded(context).copy(enableTerminalSandbox = enabled))
    }

    fun addOrUpdateRule(context: Context, action: String, pattern: String, decision: String) {
        val cleanAction = action.trim().lowercase().ifBlank { "bash" }
        val cleanPattern = pattern.trim()
        if (cleanPattern.isBlank()) return
        val ruleStr = "$cleanAction($cleanPattern)"
        val cur = ensureLoaded(context)
        val allow = cur.allowRules.filterNot { it.equals(ruleStr, ignoreCase = true) }.toMutableList()
        val deny = cur.denyRules.filterNot { it.equals(ruleStr, ignoreCase = true) }.toMutableList()
        val ask = cur.askRules.filterNot { it.equals(ruleStr, ignoreCase = true) }.toMutableList()
        when (decision.uppercase()) {
            "ALLOW" -> allow.add(ruleStr)
            "DENY" -> deny.add(ruleStr)
            "ASK" -> ask.add(ruleStr)
            else -> allow.add(ruleStr)
        }
        save(context, cur.copy(allowRules = allow, denyRules = deny, askRules = ask))
    }

    fun removeRule(context: Context, rawRule: String) {
        val cur = ensureLoaded(context)
        save(
            context,
            cur.copy(
                allowRules = cur.allowRules.filterNot { it.equals(rawRule, ignoreCase = true) },
                denyRules = cur.denyRules.filterNot { it.equals(rawRule, ignoreCase = true) },
                askRules = cur.askRules.filterNot { it.equals(rawRule, ignoreCase = true) }
            )
        )
    }

    /**
     * Evaluates whether a shell command is ALLOWED, DENIED, or needs ASK approval under current policy.
     */
    fun evaluateShellCommand(context: Context, command: String): String {
        val s = ensureLoaded(context)
        val trimmed = command.trim()

        // Explicit deny rules always win first
        for (raw in s.denyRules) {
            val rule = PermissionGrantRule.parse(raw, "DENY")
            if (rule.action in listOf("bash", "shell", "command", "*") && matchesWildcard(trimmed, rule.pattern)) {
                return "DENY"
            }
        }
        // Explicit ask rules
        for (raw in s.askRules) {
            val rule = PermissionGrantRule.parse(raw, "ASK")
            if (rule.action in listOf("bash", "shell", "command", "*") && matchesWildcard(trimmed, rule.pattern)) {
                return "ASK"
            }
        }
        // Explicit allow rules
        for (raw in s.allowRules) {
            val rule = PermissionGrantRule.parse(raw, "ALLOW")
            if (rule.action in listOf("bash", "shell", "command", "*") && matchesWildcard(trimmed, rule.pattern)) {
                return "ALLOW"
            }
        }

        // High-risk guard for OFF / AUTO modes
        val dangerousCmd = isHighRiskCommand(trimmed)
        return when (s.commandExecutionPolicy) {
            CommandExecutionPolicy.EAGER -> "ALLOW"
            CommandExecutionPolicy.AUTO -> if (dangerousCmd) "ASK" else "ALLOW"
            CommandExecutionPolicy.OFF -> "ASK"
        }
    }

    private fun isHighRiskCommand(cmd: String): Boolean {
        val lower = cmd.lowercase()
        val dangerousPrefixes = listOf(
            "rm -rf", "rm -fr", "mkfs", "dd if=", "chmod -r 777",
            "curl ", "wget ", "su ", "sudo ", "reboot", "shutdown",
            "git push --force", "git reset --hard"
        )
        return dangerousPrefixes.any { lower.contains(it) }
    }

    private fun matchesWildcard(input: String, pattern: String): Boolean {
        if (pattern == "*" || pattern.equals(input, ignoreCase = true)) return true
        val regexStr = buildString {
            append("^")
            for (ch in pattern) {
                when (ch) {
                    '*' -> append(".*")
                    '?' -> append(".")
                    '.', '+', '^', '$', '(', ')', '[', ']', '{', '}', '|', '\\' -> append('\\').append(ch)
                    else -> append(ch)
                }
            }
            append("$")
        }
        return runCatching { Regex(regexStr, RegexOption.IGNORE_CASE).matches(input) }.getOrDefault(false)
    }

    private fun save(context: Context, next: SecurityPolicyState) {
        _state.value = next
        loaded = true
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ARTIFACT_MODE, next.artifactReviewMode.wireValue)
            .putString(KEY_EXEC_POLICY, next.commandExecutionPolicy.wireValue)
            .putString(KEY_FILE_ACCESS, next.nonWorkspaceFileAccess.wireValue)
            .putBoolean(KEY_SANDBOX, next.enableTerminalSandbox)
            .putString(KEY_ALLOW_RULES, JSONArray(next.allowRules).toString())
            .putString(KEY_DENY_RULES, JSONArray(next.denyRules).toString())
            .putString(KEY_ASK_RULES, JSONArray(next.askRules).toString())
            .apply()

        runCatching {
            val home = LocalEnvironmentManager.getHomeDir(context)
            val cfgDir = File(home, ".gemini/${JarvisHubConfig.ISOLATED_APP_DATA_DIR}").apply { mkdirs() }
            val json = JSONObject().apply {
                put("artifactReviewMode", next.artifactReviewMode.wireValue)
                put("autoExecutionPolicy", next.commandExecutionPolicy.wireValue)
                put("nonWorkspaceFileAccessPolicy", next.nonWorkspaceFileAccess.wireValue)
                put("enableTerminalSandbox", next.enableTerminalSandbox)
                put("globalPermissionGrants", JSONObject().apply {
                    put("allow", JSONArray(next.allowRules))
                    put("deny", JSONArray(next.denyRules))
                    put("ask", JSONArray(next.askRules))
                })
            }
            File(cfgDir, "security_settings.json").writeText(json.toString(2))
        }
    }

    private fun decodeStringList(raw: String?): List<String>? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
        }.getOrNull()
    }
}
