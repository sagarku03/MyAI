package com.jarvisai.assistant.ui.antigravity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.antigravity.AntigravityAgentMode
import com.jarvisai.assistant.antigravity.AntigravityAutonomyPolicy
import com.jarvisai.assistant.antigravity.AntigravityWorkspaceMode

/**
 * Official Google Antigravity 2.0 Material-Agentic color tokens.
 */
object AntigravityPalette {
    val Canvas = Color(0xFF0B0E14)
    val SurfaceCard = Color(0xFF121722)
    val SurfaceElevated = Color(0xFF171E2C)
    val SurfaceSubtle = Color(0xFF0F141E)
    val BorderHairline = Color(0xFF232C3D)
    val BorderAccent = Color(0xFF3A5284)

    val ElectricBlue = Color(0xFF4F8BFF)
    val IndigoAccent = Color(0xFF818CF8)
    val VerifiedGreen = Color(0xFF22C55E)
    val ReviewAmber = Color(0xFFF59E0B)
    val DangerRed = Color(0xFFEF4444)

    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
}

/**
 * Antigravity 2.0 Mission Control Header Strip displayed directly below the Top Bar
 * whenever an Antigravity model/engine is selected.
 */
@Composable
fun AntigravityMissionHeaderBar(
    workspaceMode: AntigravityWorkspaceMode,
    inboxPendingCount: Int,
    artifactCount: Int,
    rulesCount: Int,
    canRewind: Boolean,
    onToggleWorkspaceMode: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenArtifacts: () -> Unit,
    onOpenRulesAndSkills: () -> Unit,
    onRewindLastTurn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("antigravity_mission_header_bar"),
        color = AntigravityPalette.SurfaceSubtle,
        border = BorderStroke(0.5.dp, AntigravityPalette.BorderHairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Antigravity 2.0 Identity Pill
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AntigravityPalette.ElectricBlue.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, AntigravityPalette.ElectricBlue.copy(alpha = 0.36f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AntigravityPalette.ElectricBlue,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Antigravity 2.0",
                        color = AntigravityPalette.TextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Workspace Mode Toggle: Software Engineering <-> Knowledge Work
            MissionControlChip(
                icon = if (workspaceMode == AntigravityWorkspaceMode.SOFTWARE_ENGINEERING) {
                    Icons.Default.Code
                } else {
                    Icons.AutoMirrored.Filled.MenuBook
                },
                label = workspaceMode.shortLabel,
                accentColor = AntigravityPalette.IndigoAccent,
                badgeCount = null,
                onClick = onToggleWorkspaceMode,
                testTag = "ag_workspace_mode_chip"
            )

            // Inbox / Agent Manager Queue
            MissionControlChip(
                icon = Icons.Default.Inbox,
                label = "Inbox",
                accentColor = if (inboxPendingCount > 0) AntigravityPalette.ReviewAmber else AntigravityPalette.ElectricBlue,
                badgeCount = inboxPendingCount.takeIf { it > 0 },
                onClick = onOpenInbox,
                testTag = "ag_inbox_chip"
            )

            // Artifacts Hub
            MissionControlChip(
                icon = Icons.Default.Description,
                label = "Artifacts",
                accentColor = AntigravityPalette.VerifiedGreen,
                badgeCount = artifactCount.takeIf { it > 0 },
                onClick = onOpenArtifacts,
                testTag = "ag_artifacts_chip"
            )

            // Rules & Skills Manager (.agents/rules + SKILL.md)
            MissionControlChip(
                icon = Icons.Default.Gavel,
                label = "Rules",
                accentColor = AntigravityPalette.TextSecondary,
                badgeCount = rulesCount.takeIf { it > 0 },
                onClick = onOpenRulesAndSkills,
                testTag = "ag_rules_chip"
            )

            // Quick /rewind button
            if (canRewind) {
                MissionControlChip(
                    icon = Icons.Default.Undo,
                    label = "Rewind",
                    accentColor = AntigravityPalette.TextSecondary,
                    badgeCount = null,
                    onClick = onRewindLastTurn,
                    testTag = "ag_rewind_chip"
                )
            }
        }
    }
}

@Composable
private fun MissionControlChip(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    badgeCount: Int?,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = AntigravityPalette.SurfaceCard,
        border = BorderStroke(1.dp, AntigravityPalette.BorderHairline),
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = AntigravityPalette.TextPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (badgeCount != null) {
                Spacer(modifier = Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.22f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeCount.toString(),
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

/**
 * Official Antigravity CLI (`agy`) & 2.0 Composer Controls Strip:
 * [Planning / Fast]  [agy: default / accept-edits / auto / plan]   [@ Context]  [/ Cmd]
 */
@Composable
fun AntigravityComposerModeStrip(
    agentMode: AntigravityAgentMode,
    autonomyPolicy: AntigravityAutonomyPolicy,
    onToggleAgentMode: () -> Unit,
    onCycleAutonomyPolicy: () -> Unit,
    onInsertAtMention: () -> Unit,
    onInsertSlashCommand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlanning = agentMode == AntigravityAgentMode.PLANNING
    val modeAccent = if (isPlanning) AntigravityPalette.ElectricBlue else AntigravityPalette.VerifiedGreen

    val policyColor = when (autonomyPolicy) {
        AntigravityAutonomyPolicy.DEFAULT -> AntigravityPalette.ReviewAmber
        AntigravityAutonomyPolicy.ACCEPT_EDITS -> AntigravityPalette.IndigoAccent
        AntigravityAutonomyPolicy.AUTO -> AntigravityPalette.VerifiedGreen
        AntigravityAutonomyPolicy.PLAN -> AntigravityPalette.ElectricBlue
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Planning vs Fast Mode Toggle Pill
        Surface(
            onClick = onToggleAgentMode,
            shape = RoundedCornerShape(12.dp),
            color = modeAccent.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, modeAccent.copy(alpha = 0.42f)),
            modifier = Modifier.testTag("ag_composer_mode_toggle")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isPlanning) Icons.Default.Route else Icons.Default.Bolt,
                    contentDescription = "Toggle Planning or Fast mode",
                    tint = modeAccent,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = agentMode.label,
                    color = AntigravityPalette.TextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Official `agy` CLI 4-Mode Autonomy Policy Cycler (default / accept-edits / auto / plan)
        Surface(
            onClick = onCycleAutonomyPolicy,
            shape = RoundedCornerShape(12.dp),
            color = AntigravityPalette.SurfaceElevated,
            border = BorderStroke(1.dp, policyColor.copy(alpha = 0.42f)),
            modifier = Modifier.testTag("ag_composer_policy_cycle")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Cycle CLI autonomy policy",
                    tint = policyColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "agy: ${autonomyPolicy.shortLabel}",
                    color = policyColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Quick @ Mention trigger
        Surface(
            onClick = onInsertAtMention,
            shape = RoundedCornerShape(12.dp),
            color = AntigravityPalette.SurfaceCard,
            border = BorderStroke(1.dp, AntigravityPalette.BorderHairline),
            modifier = Modifier.testTag("ag_composer_mention_chip")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AlternateEmail,
                    contentDescription = "Mention file, folder, conversation, or browser",
                    tint = AntigravityPalette.TextSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "Context",
                    color = AntigravityPalette.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Quick / Slash Commands trigger
        Surface(
            onClick = onInsertSlashCommand,
            shape = RoundedCornerShape(12.dp),
            color = AntigravityPalette.SurfaceCard,
            border = BorderStroke(1.dp, AntigravityPalette.BorderHairline),
            modifier = Modifier.testTag("ag_composer_slash_chip")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Insert slash command",
                    tint = AntigravityPalette.TextSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "/cmd",
                    color = AntigravityPalette.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Antigravity 2.0 Mission Control Idle Screen when Antigravity is selected and chat is empty.
 */
@Composable
fun AntigravityIdleMissionView(
    agentMode: AntigravityAgentMode,
    autonomyPolicy: AntigravityAutonomyPolicy,
    workspaceMode: AntigravityWorkspaceMode,
    workspaceName: String,
    onQuickAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AntigravityPalette.ElectricBlue.copy(alpha = 0.14f),
                            AntigravityPalette.IndigoAccent.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.28f),
                        radius = size.minDimension * 0.65f
                    ),
                    radius = size.minDimension * 0.65f,
                    center = Offset(size.width * 0.5f, size.height * 0.28f)
                )
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AntigravityPalette.ElectricBlue.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, AntigravityPalette.ElectricBlue.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = AntigravityPalette.ElectricBlue,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ANTIGRAVITY MISSION CONTROL • ${workspaceName.uppercase()}",
                    color = AntigravityPalette.ElectricBlue,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = if (workspaceMode == AntigravityWorkspaceMode.SOFTWARE_ENGINEERING) {
                "Plan, build & verify autonomously"
            } else {
                "Research, synthesize & draft artifacts"
            },
            color = AntigravityPalette.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.4).sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "${agentMode.label} Mode • agy policy: ${autonomyPolicy.cliFlag} • Use @file or /plan",
            color = AntigravityPalette.TextSecondary,
            fontSize = 12.5.sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MissionStarterCard(
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    accent = AntigravityPalette.ElectricBlue,
                    title = "/plan Architecture",
                    subtitle = "Draft task.md & implementation_plan.md before edits",
                    onClick = { onQuickAction("/plan ") },
                    modifier = Modifier.weight(1f),
                    testTag = "ag_starter_plan"
                )
                MissionStarterCard(
                    icon = Icons.Default.Public,
                    accent = AntigravityPalette.VerifiedGreen,
                    title = "/browser Inspect",
                    subtitle = "Verify live UI, forms, or web pages with Browser MCP",
                    onClick = { onQuickAction("/browser ") },
                    modifier = Modifier.weight(1f),
                    testTag = "ag_starter_browser"
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MissionStarterCard(
                    icon = Icons.Default.AlternateEmail,
                    accent = AntigravityPalette.IndigoAccent,
                    title = "@file Code Audit",
                    subtitle = "Attach workspace files or folders directly into context",
                    onClick = { onQuickAction("@") },
                    modifier = Modifier.weight(1f),
                    testTag = "ag_starter_mention"
                )
                MissionStarterCard(
                    icon = Icons.Default.Terminal,
                    accent = AntigravityPalette.ReviewAmber,
                    title = "/antigravity-guide",
                    subtitle = "Interactive reference for agy CLI, artifacts & workflows",
                    onClick = { onQuickAction("/antigravity-guide") },
                    modifier = Modifier.weight(1f),
                    testTag = "ag_starter_guide"
                )
            }
        }
    }
}

@Composable
private fun MissionStarterCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = AntigravityPalette.SurfaceCard,
        border = BorderStroke(1.dp, AntigravityPalette.BorderHairline),
        modifier = modifier.testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                color = AntigravityPalette.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = AntigravityPalette.TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
