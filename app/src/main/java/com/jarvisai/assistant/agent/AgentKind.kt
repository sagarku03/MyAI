package com.jarvisai.assistant.agent

/**
 * Polymorphic Agent Runtime Identifiers across the 4-Agent orchestration engine.
 */
enum class AgentKind(
    val id: String,
    val displayName: String,
    val badgeColorHex: Long,
    val subtitle: String = displayName
) {
    AGY("agy", "Antigravity Cloud", 0xFF00E5FF, "Google Cloud Code & Local Trajectory Daemon"),
    CLAUDE("claude", "Claude Code", 0xFFD97757, "Anthropic Subagent & Thinking CLI"),
    OPENCODE("opencode", "OpenCode CLI", 0xFF10B981, "Multi-Provider Open-Source Agent"),
    CODEX("codex", "OpenAI Codex", 0xFF38BDF8, "Sandboxed Workspace Diff & Patch Engine");

    val accentColorHex: Long
        get() = badgeColorHex

    companion object {
        fun fromId(id: String?): AgentKind {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
                ?: AGY
        }
    }
}
