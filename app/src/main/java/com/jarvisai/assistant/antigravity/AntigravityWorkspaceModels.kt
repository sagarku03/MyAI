package com.jarvisai.assistant.antigravity

/**
 * Official Google Antigravity 2.0 Agent Execution Mode:
 * - PLANNING: Side-effect-free discovery, task breakdown (`task.md`), and reviewable
 *   `implementation_plan.md` before executing code edits or commands.
 * - FAST: Direct execution for localized edits, quick commands, and immediate Q&A.
 */
enum class AntigravityAgentMode(
    val label: String,
    val badge: String,
    val description: String
) {
    PLANNING(
        label = "Planning",
        badge = "PLAN",
        description = "Researches first, drafts a structured task list & implementation plan for your approval before editing."
    ),
    FAST(
        label = "Fast",
        badge = "FAST",
        description = "Direct execution without formal planning pause — ideal for quick fixes and focused commands."
    );

    fun toggle(): AntigravityAgentMode = if (this == PLANNING) FAST else PLANNING
}

/**
 * Official Antigravity CLI (`agy`) 4 Autonomy / Permission Policies.
 * Mirrors `Shift+Tab` cycling in the official Antigravity CLI.
 */
enum class AntigravityAutonomyPolicy(
    val cliFlag: String,
    val shortLabel: String,
    val description: String
) {
    DEFAULT(
        cliFlag = "default",
        shortLabel = "default",
        description = "Ask before workspace file edits and terminal commands."
    ),
    ACCEPT_EDITS(
        cliFlag = "accept-edits",
        shortLabel = "accept-edits",
        description = "Auto-approve workspace file edits; ask before running shell commands."
    ),
    AUTO(
        cliFlag = "auto",
        shortLabel = "auto",
        description = "Auto-approve workspace edits & safe sandbox commands; confirm high-risk actions."
    ),
    PLAN(
        cliFlag = "plan",
        shortLabel = "plan",
        description = "Strict plan-first mode: outline steps & await approval before any side-effects."
    );

    fun next(): AntigravityAutonomyPolicy = when (this) {
        DEFAULT -> ACCEPT_EDITS
        ACCEPT_EDITS -> AUTO
        AUTO -> PLAN
        PLAN -> DEFAULT
    }
}

/**
 * Official Antigravity 2.0 Workspace Layout Toggle:
 * - SOFTWARE_ENGINEERING: Full coding telemetry, diffs, terminal/IDE quick links, artifact badges.
 * - KNOWLEDGE_WORK: Streamlined research, drafting, and structured document view.
 */
enum class AntigravityWorkspaceMode(
    val label: String,
    val shortLabel: String
) {
    SOFTWARE_ENGINEERING("Software Engineering", "Coding"),
    KNOWLEDGE_WORK("Knowledge Work", "Knowledge");

    fun toggle(): AntigravityWorkspaceMode =
        if (this == SOFTWARE_ENGINEERING) KNOWLEDGE_WORK else SOFTWARE_ENGINEERING
}

enum class AntigravityArtifactType(val fileName: String, val badgeLabel: String) {
    TASK_LIST("task.md", "TASK LIST"),
    IMPLEMENTATION_PLAN("implementation_plan.md", "PLAN"),
    WALKTHROUGH("walkthrough.md", "WALKTHROUGH"),
    CODE_DIFF("changes.diff", "DIFF"),
    CODE_SNIPPET("snippet", "CODE")
}

enum class AntigravityArtifactStatus {
    PENDING_REVIEW,
    APPROVED,
    CHANGES_REQUESTED,
    VERIFIED
}

data class TaskChecklistItem(
    val text: String,
    val state: TaskItemState
)

enum class TaskItemState {
    PENDING,      // [ ]
    IN_PROGRESS,  // [/]
    COMPLETED     // [x]
}

data class AntigravityArtifact(
    val id: String,
    val messageId: Long,
    val type: AntigravityArtifactType,
    val title: String,
    val content: String,
    val language: String = "markdown",
    val checklistItems: List<TaskChecklistItem> = emptyList(),
    val status: AntigravityArtifactStatus = AntigravityArtifactStatus.VERIFIED,
    val timestampMs: Long = System.currentTimeMillis()
)

enum class MentionKind(val prefix: String, val label: String) {
    FILE("@file:", "File"),
    FOLDER("@folder:", "Folder"),
    CONVERSATION("@conversation:", "Thread"),
    BROWSER("@browser", "Browser")
}

data class MentionSuggestion(
    val id: String,
    val kind: MentionKind,
    val displayTitle: String,
    val subtitle: String,
    val insertToken: String
)

data class AntigravitySkillEntry(
    val name: String,
    val description: String,
    val filePath: String,
    val slashTrigger: String
)

enum class AntigravityInboxFilter(val label: String) {
    NEEDS_REVIEW("Needs Review"),
    ACTIVE("Active"),
    ALL("All"),
    ARCHIVED("Archived")
}
