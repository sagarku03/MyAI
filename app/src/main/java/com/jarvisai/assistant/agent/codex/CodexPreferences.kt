package com.jarvisai.assistant.agent.codex

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 3: Configuration and preference persistence for the OpenAI Codex CLI agent backend.
 */
data class CodexConfigState(
    val model: String = "codex-mini-latest",
    val approvalPolicy: ApprovalPolicy = ApprovalPolicy.ON_FAILURE,
    val sandboxMode: SandboxMode = SandboxMode.WORKSPACE_WRITE,
    val reasoningEffort: String = "high",
    val workingDirectory: String = ""
) {
    enum class ApprovalPolicy(val cliFlag: String, val label: String) {
        SUGGEST("suggest", "Ask on Every Edit & Command"),
        AUTO_EDIT("auto-edit", "Auto-Approve File Edits Only"),
        ON_FAILURE("on-failure", "Auto-Run Inside Sandbox"),
        FULL_AUTO("full-auto", "Full Autonomous Execution")
    }

    enum class SandboxMode(val cliValue: String, val label: String) {
        READ_ONLY("read-only", "Read-Only Inspection"),
        WORKSPACE_WRITE("workspace-write", "Workspace Write Access"),
        DANGER_FULL_ACCESS("danger-full-access", "Unrestricted System Access")
    }
}

class CodexPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<CodexConfigState> = _state.asStateFlow()

    private fun load(): CodexConfigState {
        val policyName = prefs.getString(KEY_APPROVAL_POLICY, CodexConfigState.ApprovalPolicy.ON_FAILURE.name)
            ?: CodexConfigState.ApprovalPolicy.ON_FAILURE.name
        val sandboxName = prefs.getString(KEY_SANDBOX_MODE, CodexConfigState.SandboxMode.WORKSPACE_WRITE.name)
            ?: CodexConfigState.SandboxMode.WORKSPACE_WRITE.name

        return CodexConfigState(
            model = prefs.getString(KEY_MODEL, "codex-mini-latest") ?: "codex-mini-latest",
            approvalPolicy = runCatching { CodexConfigState.ApprovalPolicy.valueOf(policyName) }
                .getOrDefault(CodexConfigState.ApprovalPolicy.ON_FAILURE),
            sandboxMode = runCatching { CodexConfigState.SandboxMode.valueOf(sandboxName) }
                .getOrDefault(CodexConfigState.SandboxMode.WORKSPACE_WRITE),
            reasoningEffort = prefs.getString(KEY_REASONING_EFFORT, "high") ?: "high",
            workingDirectory = prefs.getString(KEY_WORKING_DIR, "") ?: ""
        )
    }

    fun setModel(model: String) {
        prefs.edit().putString(KEY_MODEL, model).apply()
        _state.value = _state.value.copy(model = model)
    }

    fun setApprovalPolicy(policy: CodexConfigState.ApprovalPolicy) {
        prefs.edit().putString(KEY_APPROVAL_POLICY, policy.name).apply()
        _state.value = _state.value.copy(approvalPolicy = policy)
    }

    fun setSandboxMode(mode: CodexConfigState.SandboxMode) {
        prefs.edit().putString(KEY_SANDBOX_MODE, mode.name).apply()
        _state.value = _state.value.copy(sandboxMode = mode)
    }

    fun setReasoningEffort(effort: String) {
        prefs.edit().putString(KEY_REASONING_EFFORT, effort).apply()
        _state.value = _state.value.copy(reasoningEffort = effort)
    }

    fun setWorkingDirectory(dir: String) {
        prefs.edit().putString(KEY_WORKING_DIR, dir).apply()
        _state.value = _state.value.copy(workingDirectory = dir)
    }

    companion object {
        private const val PREFS_NAME = "jarvis_codex_preferences"
        private const val KEY_MODEL = "codex_model"
        private const val KEY_APPROVAL_POLICY = "codex_approval_policy"
        private const val KEY_SANDBOX_MODE = "codex_sandbox_mode"
        private const val KEY_REASONING_EFFORT = "codex_reasoning_effort"
        private const val KEY_WORKING_DIR = "codex_working_dir"

        val SUPPORTED_MODELS = listOf(
            "codex-mini-latest",
            "o4-mini",
            "o3",
            "gpt-4.1"
        )
    }
}
