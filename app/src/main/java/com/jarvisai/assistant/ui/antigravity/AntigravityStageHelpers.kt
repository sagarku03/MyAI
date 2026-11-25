package com.jarvisai.assistant.ui.antigravity

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.agent.AgentTask
import com.jarvisai.assistant.antigravity.AntigravityAgentMode
import com.jarvisai.assistant.antigravity.AntigravityArtifact
import com.jarvisai.assistant.antigravity.AntigravityArtifactType
import com.jarvisai.assistant.antigravity.AntigravityAutonomyPolicy
import com.jarvisai.assistant.antigravity.AntigravitySkillEntry
import com.jarvisai.assistant.antigravity.AntigravityWorkspaceMode
import com.jarvisai.assistant.data.local.ChatSessionEntity
import com.jarvisai.assistant.model.ChoiceResponseItem
import com.jarvisai.assistant.model.ToolCall
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.WorkspaceSyncManager
import com.jarvisai.assistant.ui.components.AgentToolCallCard
import com.jarvisai.assistant.ui.components.ChoiceQuestionnaireCard
import com.jarvisai.assistant.ui.drawer.ArtifactsDrawerContent
import com.jarvisai.assistant.ui.drawer.GeneratedArtifact
import com.jarvisai.assistant.ui.screens.parseToolCallsFromJson
import java.io.File

/**
 * Extracted top banners (Antigravity 2.0 Mission Control Header Bar + Linux RootFS Pending Banner)
 * to keep JarvisApp's Scaffold bytecode lean and modular.
 */
@Composable
fun AntigravityStageTopBanners(
    activeScreen: String,
    isAntigravityActive: Boolean,
    workspaceMode: AntigravityWorkspaceMode,
    inboxPendingCount: Int,
    artifactCount: Int,
    rulesCount: Int,
    canRewind: Boolean,
    isLinuxRootfsInstalled: Boolean,
    isSetupBannerDismissed: Boolean,
    onToggleWorkspaceMode: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenArtifacts: () -> Unit,
    onOpenRulesAndSkills: () -> Unit,
    onRewindLastTurn: () -> Unit,
    onOpenRootfsInstaller: () -> Unit,
    onDismissSetupBanner: () -> Unit
) {
    if (activeScreen == "home" && isAntigravityActive) {
        AntigravityMissionHeaderBar(
            workspaceMode = workspaceMode,
            inboxPendingCount = inboxPendingCount,
            artifactCount = artifactCount,
            rulesCount = rulesCount,
            canRewind = canRewind,
            onToggleWorkspaceMode = onToggleWorkspaceMode,
            onOpenInbox = onOpenInbox,
            onOpenArtifacts = onOpenArtifacts,
            onOpenRulesAndSkills = onOpenRulesAndSkills,
            onRewindLastTurn = onRewindLastTurn
        )
    }

    if (activeScreen == "home" && !isLinuxRootfsInstalled && !isSetupBannerDismissed) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenRootfsInstaller),
            color = Color(0xFF1F1E12),
            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Linux Core Setup Pending (Python, Node, Git)",
                        color = Color(0xFFF59E0B),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚡ Setup Now",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismissSetupBanner,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss setup banner",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Extracted persisted tool calls + Antigravity 2.0 inline structured artifacts block.
 */
@Composable
fun AntigravityMessageToolsAndArtifacts(
    isUser: Boolean,
    isAntigravityActive: Boolean,
    toolCallsJson: String?,
    messageArtifacts: List<AntigravityArtifact>,
    onSubmitQuestionnaire: (ToolCall, List<ChoiceResponseItem>) -> Unit,
    onSkipQuestionnaire: (ToolCall) -> Unit,
    onApprovePlan: (AntigravityArtifact) -> Unit,
    onSubmitInlineComment: (AntigravityArtifact, String) -> Unit,
    onOpenArtifactFileInIde: (File) -> Unit
) {
    if (isUser) return
    val context = LocalContext.current

    if (!toolCallsJson.isNullOrBlank()) {
        val persistedTools = remember(toolCallsJson) {
            parseToolCallsFromJson(toolCallsJson)
        }
        val questionnaireTools = persistedTools.filter { it.questionnaire != null }
        val actionTools = persistedTools.filter { it.questionnaire == null }

        if (isAntigravityActive && actionTools.isNotEmpty()) {
            AntigravityProgressGroupCard(
                toolCalls = actionTools,
                isLiveStreaming = false
            )
        } else {
            actionTools.forEach { tool ->
                AgentToolCallCard(toolCall = tool)
            }
        }
        questionnaireTools.forEach { tool ->
            ChoiceQuestionnaireCard(
                toolCall = tool,
                onSubmit = onSubmitQuestionnaire,
                onSkip = onSkipQuestionnaire
            )
        }
    }

    if (isAntigravityActive && messageArtifacts.isNotEmpty()) {
        messageArtifacts
            .filter { it.type != AntigravityArtifactType.CODE_SNIPPET }
            .forEach { art ->
                AntigravityArtifactInlineCard(
                    artifact = art,
                    onApprovePlan = onApprovePlan,
                    onSubmitInlineComment = onSubmitInlineComment,
                    onOpenInIde = { targetArt ->
                        val artifactsDir = File(LocalEnvironmentManager.getHomeDir(context), "projects/artifacts")
                        if (!artifactsDir.exists()) artifactsDir.mkdirs()
                        val targetFile = File(artifactsDir, "${targetArt.id}_${targetArt.type.fileName}")
                        runCatching { targetFile.writeText(targetArt.content) }
                        WorkspaceSyncManager.requestOpenFileInIde(targetFile.absolutePath)
                        onOpenArtifactFileInIde(targetFile)
                    }
                )
            }
    }
}

/**
 * Extracted live streaming tool calls block (grouped accordion for Antigravity 2.0).
 */
@Composable
fun AntigravityLiveToolCallsSection(
    isAntigravityActive: Boolean,
    isChatLoading: Boolean,
    toolCalls: List<ToolCall>,
    onSubmitQuestionnaire: (ToolCall, List<ChoiceResponseItem>) -> Unit,
    onSkipQuestionnaire: (ToolCall) -> Unit
) {
    val liveQuestionTools = toolCalls.filter { it.questionnaire != null }
    val liveActionTools = toolCalls.filter { it.questionnaire == null }
    if (isAntigravityActive && liveActionTools.isNotEmpty()) {
        AntigravityProgressGroupCard(
            toolCalls = liveActionTools,
            isLiveStreaming = isChatLoading
        )
    } else {
        liveActionTools.forEach { tool ->
            AgentToolCallCard(toolCall = tool)
        }
    }
    liveQuestionTools.forEach { tool ->
        ChoiceQuestionnaireCard(
            toolCall = tool,
            onSubmit = onSubmitQuestionnaire,
            onSkip = onSkipQuestionnaire
        )
    }
}

/**
 * Extracted Artifacts Drawer + Antigravity Inbox Sheet + Antigravity Rules/Skills Sheet host.
 */
@Composable
fun AntigravityStageOverlaysHost(
    showArtifactsDrawer: Boolean,
    showInboxSheet: Boolean,
    showRulesSheet: Boolean,
    generatedArtifacts: List<GeneratedArtifact>,
    structuredArtifacts: List<AntigravityArtifact>,
    pendingPlanArtifacts: List<AntigravityArtifact>,
    sessions: List<ChatSessionEntity>,
    archivedSessions: List<ChatSessionEntity>,
    subagentTasks: List<AgentTask>,
    currentSessionId: Long?,
    activeWorkspacePath: String,
    agentMode: AntigravityAgentMode,
    autonomyPolicy: AntigravityAutonomyPolicy,
    onSelectSession: (Long) -> Unit,
    onSelectAgentMode: (AntigravityAgentMode) -> Unit,
    onSelectAutonomyPolicy: (AntigravityAutonomyPolicy) -> Unit,
    onApprovePlan: (AntigravityArtifact) -> Unit,
    onOpenArtifactFileInIde: (File) -> Unit,
    onTriggerSkillWorkflow: (AntigravitySkillEntry) -> Unit,
    onRulesUpdated: () -> Unit,
    onCloseArtifactsDrawer: () -> Unit,
    onDismissInbox: () -> Unit,
    onDismissRules: () -> Unit
) {
    val context = LocalContext.current

    if (showArtifactsDrawer) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                AnimatedVisibility(
                    visible = showArtifactsDrawer,
                    enter = slideInHorizontally { it },
                    exit = slideOutHorizontally { it }
                ) {
                    ArtifactsDrawerContent(
                        artifacts = generatedArtifacts,
                        structuredArtifacts = structuredArtifacts,
                        onApprovePlan = onApprovePlan,
                        onOpenInIde = { artifact ->
                            val artifactsDir = File(LocalEnvironmentManager.getHomeDir(context), "projects/artifacts")
                            if (!artifactsDir.exists()) artifactsDir.mkdirs()
                            val ext = when (artifact.fileType.lowercase()) {
                                "kotlin", "kt" -> "kt"
                                "python", "py" -> "py"
                                "javascript", "js" -> "js"
                                "typescript", "ts" -> "ts"
                                "html" -> "html"
                                "json" -> "json"
                                "sh", "bash" -> "sh"
                                "diff", "patch" -> "diff"
                                "markdown", "md" -> "md"
                                else -> "txt"
                            }
                            val targetFile = File(artifactsDir, "${artifact.id}.$ext")
                            runCatching { targetFile.writeText(artifact.content) }
                            WorkspaceSyncManager.requestOpenFileInIde(targetFile.absolutePath)
                            onCloseArtifactsDrawer()
                            onOpenArtifactFileInIde(targetFile)
                        },
                        onClose = onCloseArtifactsDrawer
                    )
                }
            }
        }
    }

    if (showInboxSheet) {
        AntigravityInboxSheet(
            sessions = sessions,
            archivedSessions = archivedSessions,
            subagentTasks = subagentTasks,
            pendingPlanArtifacts = pendingPlanArtifacts,
            currentSessionId = currentSessionId,
            onSelectSession = onSelectSession,
            onApprovePlan = onApprovePlan,
            onDismiss = onDismissInbox
        )
    }

    if (showRulesSheet) {
        AntigravityRulesAndSkillsSheet(
            workspacePath = activeWorkspacePath,
            agentMode = agentMode,
            autonomyPolicy = autonomyPolicy,
            onSelectAgentMode = onSelectAgentMode,
            onSelectAutonomyPolicy = onSelectAutonomyPolicy,
            onTriggerSkillWorkflow = onTriggerSkillWorkflow,
            onRulesUpdated = onRulesUpdated,
            onDismiss = onDismissRules
        )
    }
}
