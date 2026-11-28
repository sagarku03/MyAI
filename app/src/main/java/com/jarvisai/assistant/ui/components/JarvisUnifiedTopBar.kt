package com.jarvisai.assistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * AntiGem-Style Minimalist Top Bar:
 * [☰]  [New Chat / Active Session Title]        [● Select Project ▾]  [💬+]
 */
@Composable
fun JarvisUnifiedTopBar(
    chatTitle: String = "New Chat",
    activeChatFolder: String = "General",
    activeWorkspaceName: String? = null,
    customChatFolders: List<String> = emptyList(),
    workspaces: List<File> = emptyList(),
    isAgentBusy: Boolean = false,
    isVoiceActive: Boolean = false,
    serverStatus: com.jarvisai.assistant.terminal.LocalServerStatus = com.jarvisai.assistant.terminal.LocalServerStatus.Running(2234),
    userEmail: String = "",
    userTier: String = "",
    agyVersion: String = "",
    onOpenServerLogs: () -> Unit = {},
    onRestartServer: () -> Unit = {},
    onMenuClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onSelectFolder: (String) -> Unit = {},
    onSelectWorkspace: (File) -> Unit = {},
    onOpenWorkspaceSelector: (() -> Unit)? = null,
    onOpenFolderManager: () -> Unit = {},
    onTerminalClick: () -> Unit = {},
    onIdeClick: () -> Unit = {},
    onOverlayHudClick: () -> Unit = {}
) {
    val themeColors = com.example.ui.theme.JarvisTheme.colors
    var showProjectMenu by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }

    val isServerStarting = serverStatus is com.jarvisai.assistant.terminal.LocalServerStatus.Starting ||
        serverStatus is com.jarvisai.assistant.terminal.LocalServerStatus.Stopping
    val isServerStopped = serverStatus is com.jarvisai.assistant.terminal.LocalServerStatus.Stopped ||
        serverStatus is com.jarvisai.assistant.terminal.LocalServerStatus.Idle
    val isServerError = serverStatus is com.jarvisai.assistant.terminal.LocalServerStatus.Error

    val shouldPulseDot = isAgentBusy || isVoiceActive || isServerStarting
    val pulseAlpha = if (shouldPulseDot) {
        val infiniteTransition = rememberInfiniteTransition(label = "StatusDotPulse")
        val animated by infiniteTransition.animateFloat(
            initialValue = 0.42f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(850, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Alpha"
        )
        animated
    } else {
        1f
    }

    val statusDotColor = when {
        isServerError -> themeColors.statusRoseRed
        isServerStopped -> themeColors.textMuted
        isServerStarting || isAgentBusy -> themeColors.statusAmber
        isVoiceActive -> themeColors.accent
        else -> themeColors.statusEmerald
    }

    val projectPillLabel = remember(activeWorkspaceName, activeChatFolder) {
        when {
            !activeWorkspaceName.isNullOrBlank() -> activeWorkspaceName
            !activeChatFolder.equals("General", ignoreCase = true) && activeChatFolder.isNotBlank() -> activeChatFolder
            else -> "home"
        }
    }

    val distinctFolders = remember(customChatFolders) {
        (listOf("General") + customChatFolders)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = themeColors.surface,
        tonalElevation = 0.dp
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("topbar_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Navigation Drawer",
                            tint = themeColors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = chatTitle.ifBlank { "New Thread" },
                        color = themeColors.textPrimary,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = themeColors.surfaceElevated,
                            border = BorderStroke(1.dp, themeColors.border),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (onOpenWorkspaceSelector != null) {
                                        onOpenWorkspaceSelector()
                                    } else {
                                        showProjectMenu = true
                                    }
                                }
                                .testTag("topbar_project_selector_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .clickable { showStatusMenu = true }
                                        .testTag("topbar_agent_status_dot"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .graphicsLayer { alpha = pulseAlpha }
                                            .clip(CircleShape)
                                            .background(statusDotColor)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = projectPillLabel,
                                    color = themeColors.textSecondary,
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 104.dp)
                                )
                            }
                        }

                        AgentStatusPopupMenu(
                            expanded = showStatusMenu,
                            serverStatus = serverStatus,
                            userEmail = userEmail,
                            userTier = userTier,
                            agyVersion = agyVersion,
                            onDismiss = { showStatusMenu = false },
                            onStartOrRestartServer = onRestartServer,
                            onOpenServerLogs = onOpenServerLogs
                        )

                    DropdownMenu(
                        expanded = showProjectMenu,
                        onDismissRequest = { showProjectMenu = false },
                        modifier = Modifier
                            .background(themeColors.surfaceElevated)
                            .width(260.dp)
                            .heightIn(max = 460.dp)
                    ) {
                        Text(
                            text = "Folders & Projects",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = themeColors.textMuted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )

                        distinctFolders.forEach { folder ->
                            val isSelected = folder.equals(activeChatFolder, ignoreCase = true) && activeWorkspaceName.isNullOrBlank()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = folder,
                                        color = if (isSelected) themeColors.textPrimary else themeColors.textSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (folder.equals("General", ignoreCase = true)) Icons.Default.Public else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (isSelected) themeColors.accent else themeColors.textMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = themeColors.accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    showProjectMenu = false
                                    onSelectFolder(folder)
                                }
                            )
                        }

                        if (workspaces.isNotEmpty()) {
                            HorizontalDivider(color = themeColors.divider, modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "Local Workspaces",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = themeColors.textMuted,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                            workspaces.forEach { dir ->
                                val isWsSelected = activeWorkspaceName == dir.name
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = dir.name,
                                            color = if (isWsSelected) themeColors.textPrimary else themeColors.textSecondary,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Code,
                                            contentDescription = null,
                                            tint = if (isWsSelected) themeColors.accent else themeColors.textMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    trailingIcon = if (isWsSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Active workspace",
                                                tint = themeColors.accent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        showProjectMenu = false
                                        onSelectWorkspace(dir)
                                    }
                                )
                            }
                        }

                        HorizontalDivider(color = themeColors.divider, modifier = Modifier.padding(vertical = 4.dp))

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Manage / Create Folder",
                                    color = themeColors.accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = null,
                                    tint = themeColors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showProjectMenu = false
                                onOpenFolderManager()
                            }
                        )

                        HorizontalDivider(color = themeColors.divider, modifier = Modifier.padding(vertical = 4.dp))

                        Text(
                            text = "Workspace Views",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = themeColors.textMuted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )

                        DropdownMenuItem(
                            text = { Text("Sora Code IDE", color = themeColors.textPrimary, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    tint = themeColors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showProjectMenu = false
                                onIdeClick()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Linux Terminal", color = themeColors.textPrimary, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = themeColors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showProjectMenu = false
                                onTerminalClick()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Agent Server Status & Logs", color = themeColors.textPrimary, fontSize = 13.sp) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(statusDotColor)
                                )
                            },
                            onClick = {
                                showProjectMenu = false
                                showStatusMenu = true
                            }
                        )
                    }
                }

                IconButton(
                    onClick = onNewChatClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("topbar_new_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddComment,
                        contentDescription = "Start New Chat",
                        tint = themeColors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            }
            HorizontalDivider(
                color = themeColors.divider,
                thickness = 1.dp
            )
        }
    }
}
