package com.jarvisai.assistant.ui.antigravity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Undo
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
import com.jarvisai.assistant.data.local.ChatSessionEntity
import com.jarvisai.assistant.terminal.LocalServerStatus
import java.io.File

/**
 * Authentic Original Google Antigravity IDE / Agent Panel neutral dark matte color tokens.
 * Pure neutral charcoal/graphite surfaces — zero neon cyan or blue-tinted backdrop.
 */
object AntigravityPalette {
    val Canvas = Color(0xFF181818)
    val SurfaceCard = Color(0xFF222222)
    val SurfaceElevated = Color(0xFF2A2A2A)
    val SurfaceSubtle = Color(0xFF1C1C1C)
    val UserPromptBg = Color(0xFF252525)
    val BorderHairline = Color(0xFF2F2F2F)
    val BorderAccent = Color(0xFF424242)

    val ElectricBlue = Color(0xFF8AB4F8)     // Understated Google Blue
    val ActionBlue = Color(0xFF3B82F6)       // Clean Proceed button blue
    val IndigoAccent = Color(0xFFA8C7FA)
    val VerifiedGreen = Color(0xFF4ADE80)
    val ReviewAmber = Color(0xFFFBBF24)
    val DangerRed = Color(0xFFF87171)

    val TextPrimary = Color(0xFFE5E5E5)
    val TextSecondary = Color(0xFF9E9E9E)
    val TextMuted = Color(0xFF707070)
}

/**
 * Authentic Single-Row Original Google Antigravity Agent Panel Top Bar:
 * [☰]  [Thread Title]  [home ⌄]        [+]  [⏱]  [⋮]
 */
@Composable
fun AntigravityPanelTopBar(
    chatTitle: String,
    activeChatFolder: String,
    activeWorkspaceName: String?,
    customChatFolders: List<String>,
    workspaces: List<File>,
    workspaceMode: AntigravityWorkspaceMode,
    inboxPendingCount: Int,
    artifactCount: Int,
    rulesCount: Int,
    canRewind: Boolean,
    serverStatus: LocalServerStatus,
    onMenuClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenArtifacts: () -> Unit,
    onOpenRulesAndSkills: () -> Unit,
    onToggleWorkspaceMode: () -> Unit,
    onRewindLastTurn: () -> Unit,
    onSelectFolder: (String) -> Unit,
    onSelectWorkspace: (File) -> Unit,
    onOpenFolderManager: () -> Unit,
    onOpenServerLogs: () -> Unit,
    onRestartServer: () -> Unit,
    onIdeClick: () -> Unit,
    onTerminalClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showWorkspaceMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val projectLabel = remember(activeWorkspaceName, activeChatFolder) {
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

    val displayTitle = remember(chatTitle) {
        val clean = chatTitle.trim()
        if (clean.isBlank() || clean.equals("New Chat", ignoreCase = true)) {
            "New conversation"
        } else {
            clean
        }
    }

    val serverDotColor = when (serverStatus) {
        is LocalServerStatus.Error -> AntigravityPalette.DangerRed
        is LocalServerStatus.Stopped, is LocalServerStatus.Idle -> AntigravityPalette.TextMuted
        is LocalServerStatus.Starting, is LocalServerStatus.Stopping -> AntigravityPalette.ReviewAmber
        else -> AntigravityPalette.VerifiedGreen
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AntigravityPalette.Canvas)
            .testTag("antigravity_panel_top_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Hamburger + Thread Title + Subtle Workspace Selector
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
                        contentDescription = "Open Sidebar",
                        tint = AntigravityPalette.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                ) {
                    Text(
                        text = displayTitle,
                        color = AntigravityPalette.TextPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showWorkspaceMenu = true }
                                .padding(vertical = 1.dp)
                                .testTag("topbar_project_selector_pill"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(serverDotColor)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "$projectLabel · ${workspaceMode.shortLabel}",
                                color = AntigravityPalette.TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 165.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Switch Workspace",
                                tint = AntigravityPalette.TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showWorkspaceMenu,
                            onDismissRequest = { showWorkspaceMenu = false },
                            modifier = Modifier
                                .background(AntigravityPalette.SurfaceCard)
                                .width(240.dp)
                                .heightIn(max = 400.dp)
                        ) {
                            Text(
                                text = "WORKSPACES & FOLDERS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AntigravityPalette.TextMuted,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                            distinctFolders.forEach { folder ->
                                val isSelected = folder.equals(activeChatFolder, ignoreCase = true) && activeWorkspaceName.isNullOrBlank()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = folder,
                                            color = if (isSelected) AntigravityPalette.TextPrimary else AntigravityPalette.TextSecondary,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (folder.equals("General", ignoreCase = true)) Icons.Default.Public else Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = if (isSelected) AntigravityPalette.ElectricBlue else AntigravityPalette.TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    trailingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = AntigravityPalette.ElectricBlue,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        showWorkspaceMenu = false
                                        onSelectFolder(folder)
                                    }
                                )
                            }
                            if (workspaces.isNotEmpty()) {
                                HorizontalDivider(color = AntigravityPalette.BorderHairline, modifier = Modifier.padding(vertical = 4.dp))
                                workspaces.forEach { dir ->
                                    val isWsSelected = activeWorkspaceName == dir.name
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = dir.name,
                                                color = if (isWsSelected) AntigravityPalette.TextPrimary else AntigravityPalette.TextSecondary,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Code,
                                                contentDescription = null,
                                                tint = if (isWsSelected) AntigravityPalette.ElectricBlue else AntigravityPalette.TextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        trailingIcon = if (isWsSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = AntigravityPalette.ElectricBlue,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        } else null,
                                        onClick = {
                                            showWorkspaceMenu = false
                                            onSelectWorkspace(dir)
                                        }
                                    )
                                }
                            }
                            HorizontalDivider(color = AntigravityPalette.BorderHairline, modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text("Manage folders...", color = AntigravityPalette.ElectricBlue, fontSize = 12.5.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CreateNewFolder,
                                        contentDescription = null,
                                        tint = AntigravityPalette.ElectricBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    showWorkspaceMenu = false
                                    onOpenFolderManager()
                                }
                            )
                        }
                    }
                }
            }

            // Right: Exact Original Antigravity Panel Toolbar Icons: [+]  [⏱ History/Inbox]  [⋮ More]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                IconButton(
                    onClick = onNewChatClick,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("topbar_new_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New conversation",
                        tint = AntigravityPalette.TextSecondary,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = onOpenInbox,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("ag_inbox_chip")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Conversation History & Agent Inbox",
                            tint = if (inboxPendingCount > 0) AntigravityPalette.ElectricBlue else AntigravityPalette.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (inboxPendingCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 10.dp, end = 10.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(AntigravityPalette.ReviewAmber)
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("ag_panel_more_menu")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Antigravity Panel Actions",
                            tint = AntigravityPalette.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        modifier = Modifier
                            .background(AntigravityPalette.SurfaceCard)
                            .width(230.dp)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (artifactCount > 0) "Artifacts ($artifactCount)" else "Artifacts",
                                    color = AntigravityPalette.TextPrimary,
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = AntigravityPalette.TextSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onOpenArtifacts()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (rulesCount > 0) "Rules & Skills ($rulesCount)" else "Rules & Skills",
                                    color = AntigravityPalette.TextPrimary,
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Gavel,
                                    contentDescription = null,
                                    tint = AntigravityPalette.TextSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onOpenRulesAndSkills()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Mode: ${workspaceMode.shortLabel} (Switch)",
                                    color = AntigravityPalette.TextPrimary,
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (workspaceMode == AntigravityWorkspaceMode.SOFTWARE_ENGINEERING) Icons.Default.Code else Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = AntigravityPalette.TextSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onToggleWorkspaceMode()
                            }
                        )
                        if (canRewind) {
                            DropdownMenuItem(
                                text = { Text("Rewind last turn (/rewind)", color = AntigravityPalette.TextPrimary, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Undo,
                                        contentDescription = null,
                                        tint = AntigravityPalette.TextSecondary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onRewindLastTurn()
                                }
                            )
                        }
                        HorizontalDivider(color = AntigravityPalette.BorderHairline, modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text("Open Editor (IDE)", color = AntigravityPalette.TextSecondary, fontSize = 12.5.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Code, null, tint = AntigravityPalette.TextMuted, modifier = Modifier.size(16.dp))
                            },
                            onClick = {
                                showMoreMenu = false
                                onIdeClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Open Terminal", color = AntigravityPalette.TextSecondary, fontSize = 12.5.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Terminal, null, tint = AntigravityPalette.TextMuted, modifier = Modifier.size(16.dp))
                            },
                            onClick = {
                                showMoreMenu = false
                                onTerminalClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Bridge Logs / Restart", color = AntigravityPalette.TextSecondary, fontSize = 12.5.sp) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(serverDotColor)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onOpenServerLogs()
                            }
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = AntigravityPalette.BorderHairline, thickness = 0.75.dp)
    }
}

/**
 * Authentic Original Google Antigravity Empty Conversation Panel:
 * Clean neutral #181818 surface, understated Antigravity mark, calm starter rows,
 * and quick access to recent conversations.
 */
@Composable
fun AntigravityIdleMissionView(
    agentMode: AntigravityAgentMode,
    autonomyPolicy: AntigravityAutonomyPolicy,
    workspaceMode: AntigravityWorkspaceMode,
    workspaceName: String,
    recentSessions: List<ChatSessionEntity> = emptyList(),
    onSelectSession: (Long) -> Unit = {},
    onQuickAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        // Minimal monochrome Antigravity A-caret geometric badge
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = AntigravityPalette.SurfaceCard,
            border = BorderStroke(1.dp, AntigravityPalette.BorderHairline),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "A",
                    color = AntigravityPalette.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = if (workspaceMode == AntigravityWorkspaceMode.SOFTWARE_ENGINEERING) {
                "What would you like to build?"
            } else {
                "What would you like to explore?"
            },
            color = AntigravityPalette.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "$workspaceName · ${agentMode.label} · agy ${autonomyPolicy.cliFlag}",
            color = AntigravityPalette.TextMuted,
            fontSize = 12.5.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Clean understated Antigravity monochrome starter rows
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AntigravityStarterRow(
                icon = Icons.AutoMirrored.Filled.Assignment,
                title = "Plan a feature or refactor",
                hint = "/plan",
                onClick = { onQuickAction("/plan ") },
                testTag = "ag_starter_plan"
            )
            AntigravityStarterRow(
                icon = Icons.Default.AlternateEmail,
                title = "Inspect workspace files",
                hint = "@file",
                onClick = { onQuickAction("@") },
                testTag = "ag_starter_mention"
            )
            AntigravityStarterRow(
                icon = Icons.Default.Public,
                title = "Verify live UI in browser",
                hint = "/browser",
                onClick = { onQuickAction("/browser ") },
                testTag = "ag_starter_browser"
            )
            AntigravityStarterRow(
                icon = Icons.Default.Terminal,
                title = "Antigravity CLI & workflow guide",
                hint = "/antigravity-guide",
                onClick = { onQuickAction("/antigravity-guide") },
                testTag = "ag_starter_guide"
            )
        }

        val recent = remember(recentSessions) {
            recentSessions
                .filter { it.title.isNotBlank() && !it.title.equals("New Chat", ignoreCase = true) }
                .take(3)
        }
        if (recent.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Recent conversations",
                color = AntigravityPalette.TextMuted,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                recent.forEach { session ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectSession(session.id) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = AntigravityPalette.TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = session.title,
                            color = AntigravityPalette.TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = AntigravityPalette.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AntigravityStarterRow(
    icon: ImageVector,
    title: String,
    hint: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = AntigravityPalette.SurfaceSubtle,
        border = BorderStroke(1.dp, AntigravityPalette.BorderHairline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = AntigravityPalette.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(11.dp))
            Text(
                text = title,
                color = AntigravityPalette.TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = hint,
                color = AntigravityPalette.TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
    }
}
