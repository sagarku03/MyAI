package com.jarvisai.assistant.antigravity

import android.content.Context
import com.jarvisai.assistant.data.local.ChatMessageEntity
import com.jarvisai.assistant.data.local.ChatSessionEntity
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import java.io.File

/**
 * Core engine for Original Google Antigravity 2.0 + Antigravity CLI (`agy`) workflows:
 * - Persistent `.agents/rules/global_rules.md` & workspace `.agents/rules.md`
 * - `SKILL.md` workspace skill discovery
 * - Mode-aware Antigravity system instruction builder (Planning/Fast, Autonomy Policy, Workspace Mode)
 * - `@` Mention autocomplete & context injection (`@file:`, `@folder:`, `@conversation:`, `@browser`)
 * - Structured Artifact parser (`task.md`, `implementation_plan.md`, `walkthrough.md`, unified diffs)
 */
object AntigravityWorkspaceEngine {

    private const val MAX_FILE_INJECT_CHARS = 6000
    private const val MAX_DIR_ENTRIES = 35

    fun getGlobalRulesFile(context: Context): File {
        val home = LocalEnvironmentManager.getHomeDir(context)
        val dir = File(home, ".agents/rules").apply { runCatching { mkdirs() } }
        return File(dir, "global_rules.md")
    }

    fun getWorkspaceRulesFile(context: Context, workspacePath: String?): File {
        val base = workspacePath?.takeIf { it.isNotBlank() }?.let { File(it) }
            ?.takeIf { it.exists() && it.isDirectory }
            ?: LocalEnvironmentManager.getHomeDir(context)
        val dir = File(base, ".agents").apply { runCatching { mkdirs() } }
        return File(dir, "rules.md")
    }

    fun loadGlobalRules(context: Context): String {
        val file = getGlobalRulesFile(context)
        return runCatching { if (file.exists()) file.readText() else "" }.getOrDefault("")
    }

    fun saveGlobalRules(context: Context, content: String): Boolean {
        return runCatching {
            val file = getGlobalRulesFile(context)
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        }.getOrDefault(false)
    }

    fun loadWorkspaceRules(context: Context, workspacePath: String?): String {
        val file = getWorkspaceRulesFile(context, workspacePath)
        return runCatching { if (file.exists()) file.readText() else "" }.getOrDefault("")
    }

    fun saveWorkspaceRules(context: Context, workspacePath: String?, content: String): Boolean {
        return runCatching {
            val file = getWorkspaceRulesFile(context, workspacePath)
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        }.getOrDefault(false)
    }

    fun discoverWorkspaceSkills(context: Context, workspacePath: String?): List<AntigravitySkillEntry> {
        val roots = buildList {
            workspacePath?.takeIf { it.isNotBlank() }?.let { add(File(it, ".agents/skills")) }
            add(File(LocalEnvironmentManager.getHomeDir(context), ".agents/skills"))
        }.distinctBy { it.absolutePath }

        val discovered = mutableListOf<AntigravitySkillEntry>()
        for (root in roots) {
            if (!root.exists() || !root.isDirectory) continue
            root.listFiles()?.forEach { child ->
                val skillFile = when {
                    child.isDirectory -> File(child, "SKILL.md")
                    child.name.endsWith(".md", ignoreCase = true) -> child
                    else -> null
                }
                if (skillFile != null && skillFile.exists() && skillFile.isFile) {
                    val text = runCatching { skillFile.readText() }.getOrDefault("")
                    val name = Regex("""(?m)^name:\s*(.+)$""").find(text)?.groupValues?.getOrNull(1)
                        ?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")
                        ?: child.nameWithoutExtension
                    val desc = Regex("""(?m)^description:\s*(.+)$""").find(text)?.groupValues?.getOrNull(1)
                        ?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")
                        ?: text.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("---") && !it.startsWith("#") }
                            ?.take(110)
                        ?: "Custom Antigravity Agent Skill"
                    val slug = name.lowercase().replace(Regex("[^a-z0-9_-]+"), "-")
                    discovered.add(
                        AntigravitySkillEntry(
                            name = name,
                            description = desc,
                            filePath = skillFile.absolutePath,
                            slashTrigger = "/workflow $slug"
                        )
                    )
                }
            }
        }
        return discovered.distinctBy { it.name.lowercase() }
    }

    /**
     * Builds the official Antigravity 2.0 behavioral & artifact formatting system directive.
     */
    fun buildAntigravitySystemDirective(
        context: Context,
        agentMode: AntigravityAgentMode,
        autonomyPolicy: AntigravityAutonomyPolicy,
        workspaceMode: AntigravityWorkspaceMode,
        workspacePath: String?
    ): String = buildString {
        val effectivePlanning = agentMode == AntigravityAgentMode.PLANNING ||
            autonomyPolicy == AntigravityAutonomyPolicy.PLAN

        appendLine("[ANTIGRAVITY 2.0 AGENT HARNESS]")
        appendLine("- Agent Execution Mode: ${if (effectivePlanning) "PLANNING" else "FAST"}")
        appendLine("- CLI Autonomy Policy (`agy`): ${autonomyPolicy.cliFlag} (${autonomyPolicy.description})")
        appendLine("- Workspace Mode: ${workspaceMode.label}")
        if (!workspacePath.isNullOrBlank()) {
            appendLine("- Active Workspace Directory: $workspacePath")
        }

        if (effectivePlanning) {
            appendLine()
            appendLine("[PLANNING MODE PROTOCOL]")
            appendLine("When the user requests a multi-step build, feature, refactor, or architecture change:")
            appendLine("1. Start with side-effect-free analysis. Do NOT silently mutate files before presenting a reviewable plan unless the user explicitly says 'Proceed' or 'Approved'.")
            appendLine("2. Emit structured Antigravity Artifacts using exact fenced markdown headings so the client renders native Artifact cards:")
            appendLine("   - For checklists, use a fenced block tagged ```task.md containing `- [ ]`, `- [/]`, or `- [x]` items.")
            appendLine("   - For plans awaiting approval, use a fenced block tagged ```implementation_plan.md with Goal, Proposed Changes, and Verification Plan.")
            appendLine("   - After completing execution, summarize results inside a fenced block tagged ```walkthrough.md.")
            appendLine("   - For code changes, include unified git diffs in ```diff blocks where helpful.")
        } else {
            appendLine()
            appendLine("[FAST MODE PROTOCOL]")
            appendLine("Execute localized tasks directly and concisely. When making code edits or multi-step runs, you may still emit ```task.md, ```diff, or ```walkthrough.md artifact blocks for transparent review.")
        }

        if (workspaceMode == AntigravityWorkspaceMode.KNOWLEDGE_WORK) {
            appendLine()
            appendLine("[KNOWLEDGE WORK MODE]")
            appendLine("Prioritize deep research, structured documents, crisp executive summaries, tables, and clear synthesis over raw terminal/IDE verbosity.")
        }

        val globalRules = loadGlobalRules(context).trim()
        val workspaceRules = loadWorkspaceRules(context, workspacePath).trim()
        if (globalRules.isNotBlank() || workspaceRules.isNotBlank()) {
            appendLine()
            appendLine("[ACTIVE .agents/rules]")
            if (globalRules.isNotBlank()) {
                appendLine("### Global Rules")
                appendLine(globalRules.take(2500))
            }
            if (workspaceRules.isNotBlank()) {
                appendLine("### Workspace Rules")
                appendLine(workspaceRules.take(2500))
            }
        }
    }

    /**
     * Generates live `@` mention suggestions for files, folders, conversations, and browser.
     */
    fun buildMentionSuggestions(
        context: Context,
        currentInput: String,
        workspacePath: String?,
        sessions: List<ChatSessionEntity>
    ): List<MentionSuggestion> {
        val lastAt = currentInput.lastIndexOf('@')
        if (lastAt < 0) return emptyList()
        if (lastAt > 0 && !currentInput[lastAt - 1].isWhitespace()) return emptyList()
        val rawQuery = currentInput.substring(lastAt + 1)
        if (rawQuery.contains(' ') || rawQuery.contains('\n')) return emptyList()
        val query = rawQuery.lowercase()

        val suggestions = mutableListOf<MentionSuggestion>()

        // Always offer @browser
        if ("browser".contains(query) || query.isEmpty()) {
            suggestions.add(
                MentionSuggestion(
                    id = "mention_browser",
                    kind = MentionKind.BROWSER,
                    displayTitle = "@browser",
                    subtitle = "Attach active Chrome / Browser Subagent page context",
                    insertToken = "@browser "
                )
            )
        }

        // Workspace files & folders
        val rootDir = workspacePath?.takeIf { it.isNotBlank() }?.let { File(it) }
            ?.takeIf { it.exists() && it.isDirectory }
            ?: LocalEnvironmentManager.getHomeDir(context)

        val entries = runCatching {
            rootDir.listFiles()
                ?.filterNot { it.name.startsWith(".") && it.name != ".agents" }
                ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                .orEmpty()
        }.getOrDefault(emptyList())

        val cleanQuery = query.removePrefix("file:").removePrefix("folder:").removePrefix("conversation:")

        entries.take(MAX_DIR_ENTRIES).forEach { file ->
            val relName = file.name
            val matches = cleanQuery.isEmpty() ||
                relName.lowercase().contains(cleanQuery) ||
                (file.isDirectory && "folder".startsWith(query)) ||
                (!file.isDirectory && "file".startsWith(query))
            if (matches) {
                if (file.isDirectory) {
                    suggestions.add(
                        MentionSuggestion(
                            id = "folder_${file.absolutePath}",
                            kind = MentionKind.FOLDER,
                            displayTitle = relName,
                            subtitle = "Folder • ${file.listFiles()?.size ?: 0} items",
                            insertToken = "@folder:$relName "
                        )
                    )
                } else {
                    suggestions.add(
                        MentionSuggestion(
                            id = "file_${file.absolutePath}",
                            kind = MentionKind.FILE,
                            displayTitle = relName,
                            subtitle = "File • ${formatCompactSize(file.length())}",
                            insertToken = "@file:$relName "
                        )
                    )
                }
            }
        }

        // Recent conversations (@conversation:)
        sessions.take(8).forEach { session ->
            val title = session.title.ifBlank { "Session #${session.id}" }
            val slug = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(28).ifBlank { "${session.id}" }
            if (cleanQuery.isEmpty() || title.lowercase().contains(cleanQuery) || "conversation".startsWith(query)) {
                suggestions.add(
                    MentionSuggestion(
                        id = "conv_${session.id}",
                        kind = MentionKind.CONVERSATION,
                        displayTitle = title,
                        subtitle = "Conversation • ${session.folder}",
                        insertToken = "@conversation:$slug "
                    )
                )
            }
        }

        return suggestions.take(14)
    }

    /**
     * Replaces the active trailing `@query` token with the selected mention token.
     */
    fun applyMentionSelection(currentInput: String, suggestion: MentionSuggestion): String {
        val lastAt = currentInput.lastIndexOf('@')
        if (lastAt < 0) return currentInput + suggestion.insertToken
        return currentInput.substring(0, lastAt) + suggestion.insertToken
    }

    /**
     * Resolves `@file:`, `@folder:`, `@conversation:`, and `@browser` mentions in a prompt
     * into real grounded context blocks appended for Antigravity.
     */
    fun resolveMentionsIntoPrompt(
        context: Context,
        rawPrompt: String,
        workspacePath: String?,
        sessions: List<ChatSessionEntity>
    ): String {
        if (!rawPrompt.contains('@')) return rawPrompt
        val rootDir = workspacePath?.takeIf { it.isNotBlank() }?.let { File(it) }
            ?.takeIf { it.exists() && it.isDirectory }
            ?: LocalEnvironmentManager.getHomeDir(context)

        val blocks = mutableListOf<String>()
        val tokenRegex = Regex("""@(file:[^\s]+|folder:[^\s]+|conversation:[^\s]+|browser)\b""")

        tokenRegex.findAll(rawPrompt).forEach { match ->
            val token = match.groupValues[1]
            when {
                token.startsWith("file:") -> {
                    val rel = token.removePrefix("file:").trim().removePrefix("/")
                    val target = resolveSafeChild(rootDir, rel)
                    if (target != null && target.exists() && target.isFile) {
                        val content = runCatching { target.readText().take(MAX_FILE_INJECT_CHARS) }.getOrNull()
                        if (!content.isNullOrBlank()) {
                            blocks.add("### Mentioned File: `$rel`\n```\n$content\n```")
                        }
                    }
                }
                token.startsWith("folder:") -> {
                    val rel = token.removePrefix("folder:").trim().removePrefix("/")
                    val target = resolveSafeChild(rootDir, rel)
                    if (target != null && target.exists() && target.isDirectory) {
                        val listing = target.listFiles()
                            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                            ?.take(40)
                            ?.joinToString("\n") { child ->
                                if (child.isDirectory) "- [dir] ${child.name}/" else "- ${child.name} (${formatCompactSize(child.length())})"
                            }.orEmpty()
                        if (listing.isNotBlank()) {
                            blocks.add("### Mentioned Folder: `$rel/`\n$listing")
                        }
                    }
                }
                token.startsWith("conversation:") -> {
                    val slug = token.removePrefix("conversation:").lowercase()
                    val matched = sessions.firstOrNull { s ->
                        val sSlug = s.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                        sSlug.contains(slug) || s.id.toString() == slug
                    }
                    if (matched != null) {
                        blocks.add("### Mentioned Conversation: `${matched.title}` (Folder: ${matched.folder}, ID: ${matched.id})")
                    }
                }
                token == "browser" -> {
                    blocks.add("### Mentioned Context: `@browser`\nUser requested referencing the active in-app Browser / Browser Subagent page context. Use browser tools if live DOM inspection or verification is needed.")
                }
            }
        }

        if (blocks.isEmpty()) return rawPrompt
        return buildString {
            appendLine(rawPrompt)
            appendLine()
            appendLine("[ATTACHED @MENTION CONTEXT]")
            blocks.forEach { block ->
                appendLine(block)
                appendLine()
            }
        }.trim()
    }

    /**
     * Parses structured Antigravity 2.0 Artifacts from a chat message:
     * - ```task.md ... ``` (or markdown checklist with >= 2 checklist lines)
     * - ```implementation_plan.md ... ``` (or structured "# Implementation Plan" section)
     * - ```walkthrough.md ... ``` (or structured "# Walkthrough" section)
     * - ```diff ... ``` unified git diffs
     */
    fun extractArtifactsFromMessage(
        message: ChatMessageEntity,
        approvedIds: Set<String> = emptySet(),
        changesRequestedIds: Set<String> = emptySet()
    ): List<AntigravityArtifact> {
        if (!message.role.equals("model", ignoreCase = true) && !message.role.equals("assistant", ignoreCase = true)) {
            return emptyList()
        }
        val content = message.content
        if (content.isBlank()) return emptyList()

        val found = mutableListOf<AntigravityArtifact>()
        val fenceRegex = Regex("```([a-zA-Z0-9._+-]*)\\n([\\s\\S]*?)```")
        var idx = 0

        fenceRegex.findAll(content).forEach { match ->
            val tag = match.groupValues[1].trim().lowercase()
            val body = match.groupValues[2].trim()
            if (body.isBlank()) return@forEach

            val id = "ag_art_${message.id}_${idx++}"
            val resolvedStatus = when {
                id in approvedIds -> AntigravityArtifactStatus.APPROVED
                id in changesRequestedIds -> AntigravityArtifactStatus.CHANGES_REQUESTED
                else -> null
            }

            when {
                tag == "task.md" || tag == "task" || tag == "tasks" -> {
                    val items = parseChecklist(body)
                    found.add(
                        AntigravityArtifact(
                            id = id,
                            messageId = message.id,
                            type = AntigravityArtifactType.TASK_LIST,
                            title = extractHeadingOrDefault(body, "Task List (task.md)"),
                            content = body,
                            language = "markdown",
                            checklistItems = items,
                            status = resolvedStatus ?: AntigravityArtifactStatus.VERIFIED,
                            timestampMs = message.timestamp
                        )
                    )
                }
                tag == "implementation_plan.md" || tag == "plan" || tag == "implementation_plan" -> {
                    found.add(
                        AntigravityArtifact(
                            id = id,
                            messageId = message.id,
                            type = AntigravityArtifactType.IMPLEMENTATION_PLAN,
                            title = extractHeadingOrDefault(body, "Implementation Plan"),
                            content = body,
                            language = "markdown",
                            checklistItems = parseChecklist(body),
                            status = resolvedStatus ?: AntigravityArtifactStatus.PENDING_REVIEW,
                            timestampMs = message.timestamp
                        )
                    )
                }
                tag == "walkthrough.md" || tag == "walkthrough" -> {
                    found.add(
                        AntigravityArtifact(
                            id = id,
                            messageId = message.id,
                            type = AntigravityArtifactType.WALKTHROUGH,
                            title = extractHeadingOrDefault(body, "Walkthrough"),
                            content = body,
                            language = "markdown",
                            status = resolvedStatus ?: AntigravityArtifactStatus.VERIFIED,
                            timestampMs = message.timestamp
                        )
                    )
                }
                tag == "diff" || tag == "patch" -> {
                    found.add(
                        AntigravityArtifact(
                            id = id,
                            messageId = message.id,
                            type = AntigravityArtifactType.CODE_DIFF,
                            title = extractDiffTitle(body),
                            content = body,
                            language = "diff",
                            status = resolvedStatus ?: AntigravityArtifactStatus.PENDING_REVIEW,
                            timestampMs = message.timestamp
                        )
                    )
                }
                body.length >= 60 -> {
                    found.add(
                        AntigravityArtifact(
                            id = id,
                            messageId = message.id,
                            type = AntigravityArtifactType.CODE_SNIPPET,
                            title = "${tag.ifBlank { "Code" }.uppercase()} Artifact #${idx}",
                            content = body,
                            language = tag.ifBlank { "text" },
                            status = AntigravityArtifactStatus.VERIFIED,
                            timestampMs = message.timestamp
                        )
                    )
                }
            }
        }

        // Heuristic fallback: detect unfenced markdown Implementation Plan / Task Checklist when in Antigravity replies
        if (found.none { it.type == AntigravityArtifactType.IMPLEMENTATION_PLAN }) {
            val looksLikePlan = content.contains("## Proposed Changes", ignoreCase = true) ||
                content.contains("# Implementation Plan", ignoreCase = true) ||
                content.contains("## Implementation Plan", ignoreCase = true)
            if (looksLikePlan) {
                val id = "ag_plan_${message.id}"
                found.add(
                    0,
                    AntigravityArtifact(
                        id = id,
                        messageId = message.id,
                        type = AntigravityArtifactType.IMPLEMENTATION_PLAN,
                        title = extractHeadingOrDefault(content, "Implementation Plan"),
                        content = content.take(3500),
                        checklistItems = parseChecklist(content),
                        status = when {
                            id in approvedIds -> AntigravityArtifactStatus.APPROVED
                            id in changesRequestedIds -> AntigravityArtifactStatus.CHANGES_REQUESTED
                            else -> AntigravityArtifactStatus.PENDING_REVIEW
                        },
                        timestampMs = message.timestamp
                    )
                )
            }
        }

        if (found.none { it.type == AntigravityArtifactType.TASK_LIST }) {
            val checklist = parseChecklist(content)
            if (checklist.size >= 2) {
                val id = "ag_tasks_${message.id}"
                found.add(
                    AntigravityArtifact(
                        id = id,
                        messageId = message.id,
                        type = AntigravityArtifactType.TASK_LIST,
                        title = "Task Checklist",
                        content = checklist.joinToString("\n") { item ->
                            val marker = when (item.state) {
                                TaskItemState.COMPLETED -> "[x]"
                                TaskItemState.IN_PROGRESS -> "[/]"
                                TaskItemState.PENDING -> "[ ]"
                            }
                            "- $marker ${item.text}"
                        },
                        checklistItems = checklist,
                        status = AntigravityArtifactStatus.VERIFIED,
                        timestampMs = message.timestamp
                    )
                )
            }
        }

        return found
    }

    fun parseChecklist(markdown: String): List<TaskChecklistItem> {
        val regex = Regex("""^\s*[-*]\s+\[([ xX/])]\s+(.+)$""", RegexOption.MULTILINE)
        return regex.findAll(markdown).map { match ->
            val state = when (match.groupValues[1].lowercase()) {
                "x" -> TaskItemState.COMPLETED
                "/" -> TaskItemState.IN_PROGRESS
                else -> TaskItemState.PENDING
            }
            TaskChecklistItem(
                text = match.groupValues[2].trim(),
                state = state
            )
        }.toList()
    }

    private fun extractHeadingOrDefault(markdown: String, fallback: String): String {
        val heading = Regex("""(?m)^#{1,3}\s+(.+)$""").find(markdown)?.groupValues?.getOrNull(1)?.trim()
        return heading?.take(64)?.ifBlank { fallback } ?: fallback
    }

    private fun extractDiffTitle(diffBody: String): String {
        val fileLine = diffBody.lineSequence().firstOrNull { it.startsWith("+++ b/") || it.startsWith("--- a/") }
        return fileLine?.substringAfter("b/")?.substringAfter("a/")?.trim()?.ifBlank { "Code Diff" } ?: "Code Diff"
    }

    private fun resolveSafeChild(rootDir: File, relativePath: String): File? {
        return runCatching {
            val rootCanonical = rootDir.canonicalFile
            val candidate = File(rootCanonical, relativePath).canonicalFile
            if (candidate.path.startsWith(rootCanonical.path)) candidate else null
        }.getOrNull()
    }

    private fun formatCompactSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> String.format("%.1fMB", bytes / (1024f * 1024f))
    }

    const val ANTIGRAVITY_GUIDE_PROMPT =
        "Provide a concise, well-structured Google Antigravity 2.0 & Antigravity CLI (`agy`) quick guide for this workspace: explain Planning vs Fast mode, the 4 CLI autonomy policies (`default`, `accept-edits`, `auto`, `plan`), structured Artifacts (`task.md`, `implementation_plan.md`, `walkthrough.md`), `@` mentions (`@file`, `@folder`, `@conversation`, `@browser`), `.agents/rules`, and key slash commands (`/plan`, `/browser`, `/resume`, `/rewind`, `/workflow`, `/logout`)."
}
