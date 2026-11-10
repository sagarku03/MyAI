package com.jarvisai.assistant.agent.core

import java.util.regex.Pattern

enum class AgentRiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

data class ParsedAgentStep(
    val stepNumber: Int,
    val toolName: String,
    val target: String,
    val riskLevel: AgentRiskLevel,
    val isDestructive: Boolean,
    val summary: String
)

object AgentTrajectoryParser {

    private val TOOL_PATTERN = Pattern.compile("(?i)(view|edit|replace|write|bash|command|glob|grep|fetch|rm|git)\\s*[:\\(]\\s*([^\\)\\n]+)")
    private val DESTRUCTIVE_KEYWORDS = listOf(
        "rm -rf", "rm -r", "drop table", "git push --force", "git push -f",
        "mkfs", "dd if=", "kill -9", "truncate", "chmod 777"
    )

    /**
     * Inspects a command or CLI tool argument and determines its risk severity.
     */
    fun evaluateRisk(commandOrArgs: String): AgentRiskLevel {
        val clean = commandOrArgs.trim().lowercase()
        return when {
            DESTRUCTIVE_KEYWORDS.any { clean.contains(it) } -> AgentRiskLevel.HIGH
            clean.startsWith("git reset") || clean.startsWith("kill") || clean.contains("git rebase") -> AgentRiskLevel.MEDIUM
            else -> AgentRiskLevel.LOW
        }
    }

    /**
     * Parses streaming stdout/stderr lines from Claude Code CLI or Termux PTY,
     * identifying tool calls, files modified, step progression, and warnings.
     */
    fun parseLine(line: String, currentStep: Int): ParsedAgentStep? {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return null

        val risk = evaluateRisk(trimmed)
        val isDestructive = risk == AgentRiskLevel.HIGH

        // Match tool pattern
        val matcher = TOOL_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            val tool = matcher.group(1)?.uppercase() ?: "EXECUTE"
            val target = matcher.group(2)?.trim() ?: ""
            return ParsedAgentStep(
                stepNumber = currentStep + 1,
                toolName = tool,
                target = target,
                riskLevel = risk,
                isDestructive = isDestructive,
                summary = "Invoking $tool on $target"
            )
        }

        // Match typical CLI step prefixes
        if (trimmed.startsWith("Step ") || trimmed.startsWith("[Step ") || trimmed.startsWith("Running ") || trimmed.startsWith("Editing ")) {
            return ParsedAgentStep(
                stepNumber = currentStep + 1,
                toolName = "PROGRESS",
                target = trimmed,
                riskLevel = risk,
                isDestructive = isDestructive,
                summary = trimmed
            )
        }

        // Destructive command detected directly in line
        if (isDestructive) {
            return ParsedAgentStep(
                stepNumber = currentStep + 1,
                toolName = "SECURITY_GATE",
                target = trimmed,
                riskLevel = AgentRiskLevel.HIGH,
                isDestructive = true,
                summary = "Dangerous operation detected: $trimmed"
            )
        }

        return null
    }
}
