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
    onMenuClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onSelectFolder: (String) -> Unit = {},
    onSelectWorkspace: (File) -> Unit = {},
    onOpenFolderManager: () -> Unit = {},
    onTerminalClick: () -> Unit = {},
    onIdeClick: () -> Unit = {},
    onOverlayHudClick: () -> Unit = {}
) {
    var showProjectMenu by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "StatusDotPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Alpha"
    )

    val statusDotColor = when {
        isAgentBusy -> Color(0xFFF59E0B)
        isVoiceActive -> Color(0xFF00E5FF)
        else -> Color(0xFF22C55E)
    }

    val projectPillLabel = remember(activeWorkspaceName, activeChatFolder) {
        when {
            !activeWorkspaceName.isNullOrBlank() -> activeWorkspaceName
            !activeChatFolder.equals("General", ignoreCase = true) && activeChatFolder.isNotBlank() -> activeChatFolder
            else -> "Select Project"
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
        color = Color(0xFF0A0D12),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Hamburger Menu + Chat / Screen Title
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
                        tint = Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = chatTitle.ifBlank { "New Chat" },
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // Right: [● Select Project ▾] Pill + New Chat Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF151922),
                        border = BorderStroke(1.dp, Color(0xFF232834)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showProjectMenu = true }
                            .testTag("topbar_project_selector_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusDotColor.copy(alpha = if (isAgentBusy || isVoiceActive) pulseAlpha else 1f))
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = projectPillLabel,
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 128.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Project or Folder",
                                tint = Color(0xFFD1D5DB),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

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
                        tint = Color(0xFFE5E7EB),
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}
