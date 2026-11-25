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
        isServerError -> Color(0xFFEF4444)
        isServerStopped -> Color(0xFF6B7280)
        isServerStarting || isAgentBusy -> Color(0xFFF59E0B)
        isVoiceActive -> Color(0xFF00E5FF)
        else -> Color(0xFF22C55E)
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
        color = Color(0xFF000000),
        tonalElevation = 0.dp
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Hamburger Menu + Compact AntiGem Chat Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("topbar_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Navigation Drawer",
                            tint = Color(0xFFE5E7EB),
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    Text(
                        text = chatTitle.ifBlank { "New Chat" },
                        color = Color(0xFFF3F4F6),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                // Right: AntiGem Compact [● workspace] Pill + [💬+] New Chat Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF11151C),
                            border = BorderStroke(0.8.dp, Color(0xFF232A36)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
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
                                // Interactive green status dot INSIDE the pill just like AntiGem
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
                                            .size(7.dp)
                                            .graphicsLayer { alpha = pulseAlpha }
                                            .clip(CircleShape)
                                            .background(statusDotColor)
                                    )
                                }
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = projectPillLabel,
                                    color = Color(0xFFD1D5DB),
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 12.5.sp,
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
                            .background(Color(0xFF141821))
                            .width(260.dp)
                            .heightIn(max = 460.dp)
                    ) {
                        Text(
                            text = "CHAT FOLDERS & PROJECTS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6B7280),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )

                        distinctFolders.forEach { folder ->
                            val isSelected = folder.equals(activeChatFolder, ignoreCase = true) && activeWorkspaceName.isNullOrBlank()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = folder,
                                        color = if (isSelected) Color.White else Color(0xFFD1D5DB),
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (folder.equals("General", ignoreCase = true)) Icons.Default.Public else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (isSelected) Color(0xFF22C55E) else Color(0xFF9CA3AF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color(0xFF22C55E),
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
                            HorizontalDivider(color = Color(0xFF1F2937), modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "LOCAL WORKSPACES",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6B7280),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                            workspaces.forEach { dir ->
                                val isWsSelected = activeWorkspaceName == dir.name
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = dir.name,
                                            color = if (isWsSelected) Color.White else Color(0xFFD1D5DB),
                                            fontSize = 13.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Code,
                                            contentDescription = null,
                                            tint = if (isWsSelected) Color(0xFF60A5FA) else Color(0xFF9CA3AF),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    trailingIcon = if (isWsSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Active workspace",
                                                tint = Color(0xFF60A5FA),
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

                        HorizontalDivider(color = Color(0xFF1F2937), modifier = Modifier.padding(vertical = 4.dp))

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Manage / Create Folder",
                                    color = Color(0xFF60A5FA),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = null,
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showProjectMenu = false
                                onOpenFolderManager()
                            }
                        )

                        HorizontalDivider(color = Color(0xFF1F2937), modifier = Modifier.padding(vertical = 4.dp))

                        Text(
                            text = "QUICK WORKSPACE VIEWS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6B7280),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )

                        DropdownMenuItem(
                            text = { Text("Sora Code IDE", color = Color(0xFFE5E7EB), fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showProjectMenu = false
                                onIdeClick()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Linux Terminal", color = Color(0xFFE5E7EB), fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showProjectMenu = false
                                onTerminalClick()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Agent Server Status & Logs", color = Color(0xFF22C55E), fontSize = 13.sp) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
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
                        .size(44.dp)
                        .testTag("topbar_new_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddComment,
                        contentDescription = "Start New Chat",
                        tint = Color(0xFFD1D5DB),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            }
            HorizontalDivider(
                color = Color(0xFF141821),
                thickness = 0.6.dp
            )
        }
    }
}
