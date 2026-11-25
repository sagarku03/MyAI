package com.jarvisai.assistant.ui.antigravity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.antigravity.AntigravityArtifact
import com.jarvisai.assistant.antigravity.AntigravityArtifactStatus
import com.jarvisai.assistant.antigravity.AntigravityArtifactType
import com.jarvisai.assistant.antigravity.TaskItemState
import com.jarvisai.assistant.model.ToolCall
import com.jarvisai.assistant.model.ToolCallStatus
import com.jarvisai.assistant.ui.components.AgentToolCallCard

/**
 * Antigravity 2.0 Collapsible Grouped Tool & Execution Progress Card.
 * Summarizes multiple tool actions into one crisp expandable accordion so the chat stream stays clean.
 */
@Composable
fun AntigravityProgressGroupCard(
    toolCalls: List<ToolCall>,
    isLiveStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (toolCalls.isEmpty()) return

    var expanded by remember(toolCalls.size, isLiveStreaming) {
        mutableStateOf(isLiveStreaming || toolCalls.any { it.status == ToolCallStatus.PENDING_APPROVAL })
    }

    val runningCount = toolCalls.count { it.status == ToolCallStatus.RUNNING }
    val pendingCount = toolCalls.count { it.status == ToolCallStatus.PENDING_APPROVAL }
    val errorCount = toolCalls.count { it.status == ToolCallStatus.FAILED }
    val doneCount = toolCalls.count { it.status == ToolCallStatus.SUCCESS }

    val fileOps = toolCalls.count {
        val n = it.name.lowercase()
        n.contains("file") || n.contains("read") || n.contains("write") || n.contains("edit") || n.contains("search")
    }
    val cmdOps = toolCalls.count {
        val n = it.name.lowercase()
        n.contains("cmd") || n.contains("command") || n.contains("bash") || n.contains("shell") || n.contains("terminal")
    }

    val summaryLabel = buildList {
        if (fileOps > 0) add("$fileOps file op${if (fileOps > 1) "s" else ""}")
        if (cmdOps > 0) add("$cmdOps command${if (cmdOps > 1) "s" else ""}")
        val other = (toolCalls.size - fileOps - cmdOps).coerceAtLeast(0)
        if (other > 0) add("$other tool action${if (other > 1) "s" else ""}")
    }.joinToString(" • ").ifBlank { "${toolCalls.size} agent step(s)" }

    val statusColor = when {
        pendingCount > 0 -> AntigravityPalette.ReviewAmber
        errorCount > 0 -> AntigravityPalette.DangerRed
        runningCount > 0 || isLiveStreaming -> AntigravityPalette.ElectricBlue
        else -> AntigravityPalette.VerifiedGreen
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("ag_progress_group_card"),
        shape = RoundedCornerShape(10.dp),
        color = AntigravityPalette.SurfaceCard,
        border = BorderStroke(1.dp, AntigravityPalette.BorderHairline)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (runningCount > 0 || isLiveStreaming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = statusColor,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = summaryLabel,
                            color = AntigravityPalette.TextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                pendingCount > 0 -> "$pendingCount awaiting your approval"
                                runningCount > 0 -> "$runningCount running • $doneCount completed"
                                errorCount > 0 -> "$doneCount completed • $errorCount failed"
                                else -> "All $doneCount step${if (doneCount == 1) "" else "s"} verified"
                            },
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse steps" else "Expand steps",
                    tint = AntigravityPalette.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    toolCalls.forEach { tc ->
                        AgentToolCallCard(toolCall = tc)
                    }
                }
            }
        }
    }
}

/**
 * Antigravity 2.0 Inline Structured Artifact Card:
 * Renders `task.md`, `implementation_plan.md` (with Approve & Proceed + Inline Comment),
 * `walkthrough.md`, and `changes.diff` natively in the chat stream.
 */
@Composable
fun AntigravityArtifactInlineCard(
    artifact: AntigravityArtifact,
    onApprovePlan: (AntigravityArtifact) -> Unit,
    onSubmitInlineComment: (AntigravityArtifact, String) -> Unit,
    onOpenInIde: (AntigravityArtifact) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember(artifact.id) {
        mutableStateOf(
            artifact.type == AntigravityArtifactType.TASK_LIST ||
                artifact.type == AntigravityArtifactType.IMPLEMENTATION_PLAN
        )
    }
    var showCommentBox by remember(artifact.id) { mutableStateOf(false) }
    var commentDraft by remember(artifact.id) { mutableStateOf("") }

    val accent = when (artifact.type) {
        AntigravityArtifactType.TASK_LIST -> AntigravityPalette.ElectricBlue
        AntigravityArtifactType.IMPLEMENTATION_PLAN -> when (artifact.status) {
            AntigravityArtifactStatus.APPROVED -> AntigravityPalette.VerifiedGreen
            AntigravityArtifactStatus.CHANGES_REQUESTED -> AntigravityPalette.ReviewAmber
            else -> AntigravityPalette.IndigoAccent
        }
        AntigravityArtifactType.WALKTHROUGH -> AntigravityPalette.VerifiedGreen
        AntigravityArtifactType.CODE_DIFF -> AntigravityPalette.ReviewAmber
        AntigravityArtifactType.CODE_SNIPPET -> AntigravityPalette.TextSecondary
    }

    val icon = when (artifact.type) {
        AntigravityArtifactType.TASK_LIST -> Icons.Default.TaskAlt
        AntigravityArtifactType.IMPLEMENTATION_PLAN -> Icons.AutoMirrored.Filled.Assignment
        AntigravityArtifactType.WALKTHROUGH -> Icons.Default.Verified
        AntigravityArtifactType.CODE_DIFF -> Icons.Default.Difference
        AntigravityArtifactType.CODE_SNIPPET -> Icons.Default.Code
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .testTag("ag_artifact_card_${artifact.type.name.lowercase()}"),
        shape = RoundedCornerShape(10.dp),
        color = AntigravityPalette.SurfaceCard,
        border = BorderStroke(1.dp, AntigravityPalette.BorderHairline)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Card Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = artifact.type.badgeLabel,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = accent.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = artifact.type.badgeLabel,
                                    color = accent,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = artifact.type.fileName,
                                color = AntigravityPalette.TextMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = artifact.title,
                            color = AntigravityPalette.TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle Artifact Details",
                    tint = AntigravityPalette.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Body
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                ) {
                    // Task Checklist Renderer
                    if (artifact.checklistItems.isNotEmpty()) {
                        val done = artifact.checklistItems.count { it.state == TaskItemState.COMPLETED }
                        val total = artifact.checklistItems.size
                        val progress = if (total > 0) done.toFloat() / total.toFloat() else 0f

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$done / $total tasks completed",
                                color = AntigravityPalette.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                color = accent,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = accent,
                            trackColor = AntigravityPalette.BorderHairline
                        )
                        Spacer(modifier = Modifier.height(9.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            artifact.checklistItems.take(12).forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    when (item.state) {
                                        TaskItemState.COMPLETED -> Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Done",
                                            tint = AntigravityPalette.VerifiedGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        TaskItemState.IN_PROGRESS -> Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(AntigravityPalette.ElectricBlue.copy(alpha = 0.25f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(AntigravityPalette.ElectricBlue)
                                            )
                                        }
                                        TaskItemState.PENDING -> Icon(
                                            imageVector = Icons.Default.RadioButtonUnchecked,
                                            contentDescription = "Pending",
                                            tint = AntigravityPalette.TextMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = item.text,
                                        color = if (item.state == TaskItemState.COMPLETED) {
                                            AntigravityPalette.TextSecondary
                                        } else {
                                            AntigravityPalette.TextPrimary
                                        },
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    } else if (artifact.type == AntigravityArtifactType.CODE_DIFF) {
                        // Compact syntax-colored diff preview
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AntigravityPalette.Canvas,
                            border = BorderStroke(1.dp, AntigravityPalette.BorderHairline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                artifact.content.lines().take(16).forEach { line ->
                                    val lineColor = when {
                                        line.startsWith("+") && !line.startsWith("+++") -> AntigravityPalette.VerifiedGreen
                                        line.startsWith("-") && !line.startsWith("---") -> AntigravityPalette.DangerRed
                                        line.startsWith("@@") -> AntigravityPalette.ElectricBlue
                                        else -> AntigravityPalette.TextSecondary
                                    }
                                    Text(
                                        text = line,
                                        color = lineColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } else {
                        // Structured Markdown summary preview (Plan / Walkthrough)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AntigravityPalette.Canvas,
                            border = BorderStroke(1.dp, AntigravityPalette.BorderHairline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = artifact.content.take(700) + if (artifact.content.length > 700) "\n..." else "",
                                color = AntigravityPalette.TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // Implementation Plan Review Controls (Approve & Proceed + Inline Comment)
                    if (artifact.type == AntigravityArtifactType.IMPLEMENTATION_PLAN) {
                        Spacer(modifier = Modifier.height(10.dp))
                        if (artifact.status == AntigravityArtifactStatus.APPROVED) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Approved",
                                    tint = AntigravityPalette.VerifiedGreen,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Plan Approved — Execution Authorized",
                                    color = AntigravityPalette.VerifiedGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onApprovePlan(artifact) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AntigravityPalette.ActionBlue,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("ag_approve_plan_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Proceed", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { showCommentBox = !showCommentBox },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, AntigravityPalette.BorderAccent),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("ag_comment_plan_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Comment,
                                        contentDescription = null,
                                        tint = AntigravityPalette.TextPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Comment",
                                        color = AntigravityPalette.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = showCommentBox) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = commentDraft,
                                    onValueChange = { commentDraft = it },
                                    placeholder = {
                                        Text(
                                            text = "Add inline review feedback for this plan...",
                                            fontSize = 12.sp,
                                            color = AntigravityPalette.TextMuted
                                        )
                                    },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AntigravityPalette.ElectricBlue,
                                        unfocusedBorderColor = AntigravityPalette.BorderHairline,
                                        focusedTextColor = AntigravityPalette.TextPrimary,
                                        unfocusedTextColor = AntigravityPalette.TextPrimary
                                    )
                                )
                                Surface(
                                    onClick = {
                                        if (commentDraft.isNotBlank()) {
                                            val text = commentDraft.trim()
                                            commentDraft = ""
                                            showCommentBox = false
                                            onSubmitInlineComment(artifact, text)
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = AntigravityPalette.ElectricBlue,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send Plan Feedback",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (artifact.type == AntigravityArtifactType.CODE_DIFF) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { onOpenInIde(artifact) },
                                shape = RoundedCornerShape(9.dp),
                                border = BorderStroke(1.dp, AntigravityPalette.BorderHairline)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = AntigravityPalette.ElectricBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Open in IDE", color = AntigravityPalette.TextPrimary, fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
