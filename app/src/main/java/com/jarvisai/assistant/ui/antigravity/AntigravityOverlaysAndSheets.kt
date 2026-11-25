package com.jarvisai.assistant.ui.antigravity

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.agent.AgentTask
import com.jarvisai.assistant.antigravity.AntigravityAgentMode
import com.jarvisai.assistant.antigravity.AntigravityArtifact
import com.jarvisai.assistant.antigravity.AntigravityArtifactStatus
import com.jarvisai.assistant.antigravity.AntigravityAutonomyPolicy
import com.jarvisai.assistant.antigravity.AntigravityInboxFilter
import com.jarvisai.assistant.antigravity.AntigravitySkillEntry
import com.jarvisai.assistant.antigravity.AntigravityWorkspaceEngine
import com.jarvisai.assistant.antigravity.MentionKind
import com.jarvisai.assistant.antigravity.MentionSuggestion
import com.jarvisai.assistant.data.local.ChatSessionEntity

/**
 * Floating `@` Mentions Autocomplete Popup above the composer (`@file:`, `@folder:`, `@conversation:`, `@browser`).
 */
@Composable
fun MentionAutocompleteOverlay(
    suggestions: List<MentionSuggestion>,
    isVisible: Boolean,
    onSelect: (MentionSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible && suggestions.isNotEmpty(),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier.testTag("ag_mention_overlay")
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .heightIn(max = 220.dp),
            shape = RoundedCornerShape(14.dp),
            color = AntigravityPalette.SurfaceCard,
            border = BorderStroke(1.dp, AntigravityPalette.ElectricBlue.copy(alpha = 0.45f)),
            tonalElevation = 8.dp
        ) {
            LazyColumn(modifier = Modifier.padding(6.dp)) {
                items(suggestions, key = { it.id }) { item ->
                    val (icon, tint) = when (item.kind) {
                        MentionKind.FILE -> Icons.Default.InsertDriveFile to AntigravityPalette.ElectricBlue
                        MentionKind.FOLDER -> Icons.Default.Folder to AntigravityPalette.ReviewAmber
                        MentionKind.CONVERSATION -> Icons.AutoMirrored.Filled.Chat to AntigravityPalette.IndigoAccent
                        MentionKind.BROWSER -> Icons.Default.Public to AntigravityPalette.VerifiedGreen
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(item) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = item.kind.label,
                            tint = tint,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.displayTitle,
                                color = AntigravityPalette.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.subtitle,
                                color = AntigravityPalette.TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = tint.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = item.kind.label,
                                color = tint,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Antigravity 2.0 Mission Control Inbox & Agent Manager Bottom Sheet.
 * Aggregates pending plan reviews, running subagents, active threads, and archived conversations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntigravityInboxSheet(
    sessions: List<ChatSessionEntity>,
    archivedSessions: List<ChatSessionEntity>,
    subagentTasks: List<AgentTask>,
    pendingPlanArtifacts: List<AntigravityArtifact>,
    currentSessionId: Long?,
    onSelectSession: (Long) -> Unit,
    onApprovePlan: (AntigravityArtifact) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedFilter by remember { mutableStateOf(AntigravityInboxFilter.NEEDS_REVIEW) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AntigravityPalette.Canvas,
        contentColor = AntigravityPalette.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("ag_inbox_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        tint = AntigravityPalette.ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Antigravity Inbox & Manager",
                            color = AntigravityPalette.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Asynchronous agent threads & decision queue",
                            color = AntigravityPalette.TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Inbox", tint = AntigravityPalette.TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AntigravityInboxFilter.entries.forEach { filter ->
                    val selected = filter == selectedFilter
                    Surface(
                        onClick = { selectedFilter = filter },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) AntigravityPalette.ElectricBlue.copy(alpha = 0.2f) else AntigravityPalette.SurfaceCard,
                        border = BorderStroke(
                            1.dp,
                            if (selected) AntigravityPalette.ElectricBlue else AntigravityPalette.BorderHairline
                        )
                    ) {
                        Text(
                            text = filter.label,
                            color = if (selected) AntigravityPalette.ElectricBlue else AntigravityPalette.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val runningTasks = subagentTasks.filter { it.status.name == "RUNNING" || it.status.name == "PENDING" }
            val reviewPlans = pendingPlanArtifacts.filter { it.status == AntigravityArtifactStatus.PENDING_REVIEW }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 430.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (selectedFilter) {
                    AntigravityInboxFilter.NEEDS_REVIEW -> {
                        if (reviewPlans.isEmpty() && runningTasks.isEmpty()) {
                            item {
                                InboxAllCaughtUpState("All caught up — no pending plans or approvals")
                            }
                        } else {
                            items(reviewPlans, key = { it.id }) { plan ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = AntigravityPalette.SurfaceCard,
                                    border = BorderStroke(1.dp, AntigravityPalette.ReviewAmber.copy(alpha = 0.45f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Assignment,
                                                contentDescription = null,
                                                tint = AntigravityPalette.ReviewAmber,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = plan.title,
                                                    color = AntigravityPalette.TextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Implementation Plan awaiting your decision",
                                                    color = AntigravityPalette.ReviewAmber,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                onApprovePlan(plan)
                                                onDismiss()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = AntigravityPalette.ElectricBlue),
                                            shape = RoundedCornerShape(9.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Proceed", fontSize = 11.5.sp)
                                        }
                                    }
                                }
                            }
                            items(runningTasks, key = { it.id }) { task ->
                                SubagentInboxCard(task)
                            }
                        }
                    }

                    AntigravityInboxFilter.ACTIVE -> {
                        if (runningTasks.isEmpty() && sessions.isEmpty()) {
                            item { InboxAllCaughtUpState("No active agent runs right now") }
                        } else {
                            items(runningTasks, key = { it.id }) { task ->
                                SubagentInboxCard(task)
                            }
                            items(sessions.take(10), key = { it.id }) { session ->
                                SessionInboxRow(
                                    session = session,
                                    isCurrent = session.id == currentSessionId,
                                    onClick = {
                                        onSelectSession(session.id)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }

                    AntigravityInboxFilter.ALL -> {
                        if (sessions.isEmpty()) {
                            item { InboxAllCaughtUpState("No conversations yet") }
                        } else {
                            items(sessions, key = { it.id }) { session ->
                                SessionInboxRow(
                                    session = session,
                                    isCurrent = session.id == currentSessionId,
                                    onClick = {
                                        onSelectSession(session.id)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }

                    AntigravityInboxFilter.ARCHIVED -> {
                        if (archivedSessions.isEmpty()) {
                            item { InboxAllCaughtUpState("No archived threads") }
                        } else {
                            items(archivedSessions, key = { it.id }) { session ->
                                SessionInboxRow(
                                    session = session,
                                    isCurrent = session.id == currentSessionId,
                                    onClick = {
                                        onSelectSession(session.id)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SubagentInboxCard(task: AgentTask) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AntigravityPalette.SurfaceCard,
        border = BorderStroke(1.dp, AntigravityPalette.ElectricBlue.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${task.agentType.uppercase()} SUBAGENT",
                    color = AntigravityPalette.ElectricBlue,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Step ${task.progressStep}/${task.totalSteps}",
                    color = AntigravityPalette.TextSecondary,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = task.prompt,
                color = AntigravityPalette.TextPrimary,
                fontSize = 12.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SessionInboxRow(
    session: ChatSessionEntity,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isCurrent) AntigravityPalette.SurfaceElevated else AntigravityPalette.SurfaceCard,
        border = BorderStroke(
            1.dp,
            if (isCurrent) AntigravityPalette.ElectricBlue.copy(alpha = 0.55f) else AntigravityPalette.BorderHairline
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isCurrent) AntigravityPalette.VerifiedGreen else AntigravityPalette.TextMuted)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title.ifBlank { "Untitled Mission" },
                        color = AntigravityPalette.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Folder: ${session.folder}",
                        color = AntigravityPalette.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            if (isCurrent) {
                Text(
                    text = "ACTIVE",
                    color = AntigravityPalette.VerifiedGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun InboxAllCaughtUpState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = AntigravityPalette.VerifiedGreen,
            modifier = Modifier.size(34.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            color = AntigravityPalette.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Antigravity 2.0 Rules (`.agents/rules`), Skills (`SKILL.md`) & Policy Config Bottom Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntigravityRulesAndSkillsSheet(
    workspacePath: String?,
    agentMode: AntigravityAgentMode,
    autonomyPolicy: AntigravityAutonomyPolicy,
    onSelectAgentMode: (AntigravityAgentMode) -> Unit,
    onSelectAutonomyPolicy: (AntigravityAutonomyPolicy) -> Unit,
    onTriggerSkillWorkflow: (AntigravitySkillEntry) -> Unit,
    onRulesUpdated: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var globalRulesText by remember {
        mutableStateOf(AntigravityWorkspaceEngine.loadGlobalRules(context))
    }
    var workspaceRulesText by remember(workspacePath) {
        mutableStateOf(AntigravityWorkspaceEngine.loadWorkspaceRules(context, workspacePath))
    }
    val discoveredSkills = remember(workspacePath) {
        AntigravityWorkspaceEngine.discoverWorkspaceSkills(context, workspacePath)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AntigravityPalette.Canvas,
        contentColor = AntigravityPalette.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("ag_rules_skills_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Gavel,
                        contentDescription = null,
                        tint = AntigravityPalette.ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Antigravity Rules, Skills & Policy",
                            color = AntigravityPalette.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = ".agents/rules • SKILL.md • agy execution modes",
                            color = AntigravityPalette.TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = AntigravityPalette.TextSecondary)
                }
            }

            // Agent Mode Selector
            Text("Agent Mode", color = AntigravityPalette.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AntigravityAgentMode.entries.forEach { mode ->
                    val active = mode == agentMode
                    Surface(
                        onClick = { onSelectAgentMode(mode) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) AntigravityPalette.ElectricBlue.copy(alpha = 0.18f) else AntigravityPalette.SurfaceCard,
                        border = BorderStroke(1.dp, if (active) AntigravityPalette.ElectricBlue else AntigravityPalette.BorderHairline),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(mode.label, color = AntigravityPalette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(mode.description, color = AntigravityPalette.TextSecondary, fontSize = 10.5.sp, lineHeight = 14.sp, maxLines = 2)
                        }
                    }
                }
            }

            // CLI Autonomy Policy Selector
            Text("Antigravity CLI (`agy`) Autonomy Policy", color = AntigravityPalette.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                AntigravityAutonomyPolicy.entries.forEach { policy ->
                    val active = policy == autonomyPolicy
                    Surface(
                        onClick = { onSelectAutonomyPolicy(policy) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) AntigravityPalette.IndigoAccent.copy(alpha = 0.2f) else AntigravityPalette.SurfaceCard,
                        border = BorderStroke(1.dp, if (active) AntigravityPalette.IndigoAccent else AntigravityPalette.BorderHairline)
                    ) {
                        Text(
                            text = policy.cliFlag,
                            color = if (active) AntigravityPalette.IndigoAccent else AntigravityPalette.TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                        )
                    }
                }
            }
            Text(
                text = autonomyPolicy.description,
                color = AntigravityPalette.TextMuted,
                fontSize = 11.sp
            )

            // Global Rules (.agents/rules/global_rules.md)
            Text("Global Rules (~/.agents/rules/global_rules.md)", color = AntigravityPalette.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = globalRulesText,
                onValueChange = { globalRulesText = it },
                placeholder = {
                    Text(
                        text = "e.g., Always reply in Hinglish, write clean Kotlin Compose, run tests before finishing...",
                        fontSize = 12.sp,
                        color = AntigravityPalette.TextMuted
                    )
                },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AntigravityPalette.ElectricBlue,
                    unfocusedBorderColor = AntigravityPalette.BorderHairline,
                    focusedTextColor = AntigravityPalette.TextPrimary,
                    unfocusedTextColor = AntigravityPalette.TextPrimary
                )
            )

            // Workspace Rules (.agents/rules.md)
            Text("Workspace Rules (.agents/rules.md)", color = AntigravityPalette.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = workspaceRulesText,
                onValueChange = { workspaceRulesText = it },
                placeholder = {
                    Text(
                        text = "Project-specific architecture constraints, lint rules, or style guides...",
                        fontSize = 12.sp,
                        color = AntigravityPalette.TextMuted
                    )
                },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AntigravityPalette.ElectricBlue,
                    unfocusedBorderColor = AntigravityPalette.BorderHairline,
                    focusedTextColor = AntigravityPalette.TextPrimary,
                    unfocusedTextColor = AntigravityPalette.TextPrimary
                )
            )

            Button(
                onClick = {
                    AntigravityWorkspaceEngine.saveGlobalRules(context, globalRulesText)
                    AntigravityWorkspaceEngine.saveWorkspaceRules(context, workspacePath, workspaceRulesText)
                    onRulesUpdated()
                    Toast.makeText(context, "Antigravity .agents/rules saved!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AntigravityPalette.ElectricBlue),
                shape = RoundedCornerShape(11.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ag_save_rules_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save .agents/rules", fontWeight = FontWeight.Bold)
            }

            // Discovered SKILL.md skills
            Text(
                text = "Workspace Agent Skills (.agents/skills/*/SKILL.md) • ${discoveredSkills.size}",
                color = AntigravityPalette.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (discoveredSkills.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AntigravityPalette.SurfaceCard,
                    border = BorderStroke(1.dp, AntigravityPalette.BorderHairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No custom SKILL.md files found yet in ~/.agents/skills/. Add any folder with a SKILL.md file to trigger it via /workflow.",
                        color = AntigravityPalette.TextMuted,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                discoveredSkills.forEach { skill ->
                    Surface(
                        onClick = {
                            onTriggerSkillWorkflow(skill)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(11.dp),
                        color = AntigravityPalette.SurfaceCard,
                        border = BorderStroke(1.dp, AntigravityPalette.BorderHairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = AntigravityPalette.IndigoAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(9.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(skill.name, color = AntigravityPalette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(skill.description, color = AntigravityPalette.TextSecondary, fontSize = 11.sp, maxLines = 2)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
