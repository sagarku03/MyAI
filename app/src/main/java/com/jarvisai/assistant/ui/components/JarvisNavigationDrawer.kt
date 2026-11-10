package com.jarvisai.assistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.data.local.ChatSessionEntity
import java.io.File

/**
 * AntiGem-Grade Master Navigation Drawer.
 * Neatly groups Google Antigravity Account Card, Active Workspaces, Developer Tools,
 * Enterprise Security & Controls, and Searchable Chat/Voice Conversation History.
 */
@Composable
fun JarvisNavigationDrawer(
    sessions: List<ChatSessionEntity>,
    currentSessionId: Long?,
    workspaces: List<File>,
    isAntigravityLoggedIn: Boolean = false,
    antigravityUserEmail: String = "",
    onSessionClick: (Long) -> Unit,
    onNewChatClick: () -> Unit,
    onWorkspaceClick: (File) -> Unit,
    onNavigateDestination: (String) -> Unit,
    onCloseDrawer: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredSessions = remember(sessions, searchQuery) {
        if (searchQuery.isBlank()) {
            sessions
        } else {
            sessions.filter { it.title.contains(searchQuery.trim(), ignoreCase = true) }
        }
    }

    ModalDrawerSheet(
        drawerContainerColor = Color(0xFF111827),
        drawerContentColor = Color.White,
        modifier = Modifier
            .width(320.dp)
            .testTag("jarvis_navigation_drawer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Arc Reactor Logo + Version
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoMode,
                    contentDescription = "Arc Reactor Core",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "JARVIS AI ULTRA",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "v4.0.0 Ultra • AntiGem OS",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Action: New Chat Button
            Button(
                onClick = {
                    onNewChatClick()
                    onCloseDrawer()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_new_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color(0xFF0A0E17),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New Conversation",
                    color = Color(0xFF0A0E17),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Antigravity Google Account Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onNavigateDestination("antigravity_login")
                        onCloseDrawer()
                    }
                    .padding(vertical = 4.dp)
                    .testTag("drawer_antigravity_account_card"),
                color = Color(0xFF1F2937),
                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAntigravityLoggedIn && antigravityUserEmail.isNotBlank()) {
                                antigravityUserEmail
                            } else {
                                "Google Antigravity"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isAntigravityLoggedIn) {
                                "Pro Tier Active • Tap to manage"
                            } else {
                                "Tap to connect Google account"
                            },
                            fontSize = 10.sp,
                            color = if (isAntigravityLoggedIn) Color(0xFF10B981) else Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- SECTION 1: ACTIVE WORKSPACES ($HOME/projects) ---
            DrawerSectionTitle("ACTIVE WORKSPACES (\$HOME/projects)")
            DrawerItem(
                icon = Icons.Default.Folder,
                title = "📁 Workspace Files Explorer",
                tint = Color(0xFFF59E0B),
                testTag = "drawer_item_files"
            ) {
                onNavigateDestination("files")
                onCloseDrawer()
            }
            if (workspaces.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onNavigateDestination("ide")
                            onCloseDrawer()
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "~/projects (Open in Sora IDE)",
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1)
                    )
                }
            } else {
                workspaces.take(6).forEach { dir ->
                    DrawerItem(
                        icon = Icons.Default.Folder,
                        title = "${dir.name} (Open in IDE)",
                        tint = Color(0xFFF59E0B),
                        onClick = {
                            onWorkspaceClick(dir)
                            onCloseDrawer()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF1F2937))
            Spacer(modifier = Modifier.height(14.dp))

            // --- SECTION 2: DEVELOPER TOOLS ---
            DrawerSectionTitle("DEVELOPER TOOLS")
            DrawerItem(
                icon = Icons.Default.Code,
                title = "📝 Sora Code Editor",
                tint = Color(0xFF38BDF8),
                testTag = "drawer_item_ide"
            ) {
                onNavigateDestination("ide")
                onCloseDrawer()
            }
            DrawerItem(
                icon = Icons.Default.Terminal,
                title = "💻 Embedded Linux Terminal",
                tint = Color(0xFF00E5FF),
                testTag = "drawer_item_terminal"
            ) {
                onNavigateDestination("terminal")
                onCloseDrawer()
            }
            DrawerItem(
                icon = Icons.Default.Inventory2,
                title = "📦 Linux Core Engine (352MB)",
                tint = Color(0xFF10B981),
                testTag = "drawer_item_rootfs"
            ) {
                onNavigateDestination("rootfs_installer")
                onCloseDrawer()
            }
            DrawerItem(
                icon = Icons.Default.Description,
                title = "📄 Generated Artifacts Shelf",
                tint = Color(0xFF00E5FF),
                testTag = "drawer_item_artifacts"
            ) {
                onNavigateDestination("artifacts")
                onCloseDrawer()
            }
            DrawerItem(
                icon = Icons.Default.Hub,
                title = "🤖 Multi-Agent Hub (Claude / OpenCode)",
                tint = Color(0xFFF472B6),
                testTag = "drawer_item_agent_hub"
            ) {
                onNavigateDestination("agent_hub")
                onCloseDrawer()
            }
            DrawerItem(
                icon = Icons.Default.DeveloperMode,
                title = "📲 Wireless ADB & God-Mode",
                tint = Color(0xFF10B981),
                testTag = "drawer_item_adb"
            ) {
                onNavigateDestination("adb_connect")
                onCloseDrawer()
            }
            DrawerItem(
                icon = Icons.Default.Extension,
                title = "🧩 Plugins & Chrome Extensions",
                tint = Color(0xFFA78BFA),
                testTag = "drawer_item_plugins"
            ) {
                onNavigateDestination("plugins")
                onCloseDrawer()
            }
            DrawerItem(
                icon = Icons.Default.Mic,
                title = "🎙️ Full Live Voice Studio",
                tint = Color(0xFF00E5FF),
                testTag = "drawer_item_voice"
            ) {
                onNavigateDestination("voice")
                onCloseDrawer()
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF1F2937))
            Spacer(modifier = Modifier.height(14.dp))

            // --- SECTION 3: ENTERPRISE SECURITY & CONTROLS ---
            DrawerSectionTitle("SECURITY & CONTROLS")
            DrawerItem(
                icon = Icons.Default.PictureInPictureAlt,
                title = "🫧 Floating ChatHead Bubble",
                tint = Color(0xFF00E5FF),
                testTag = "drawer_item_floating_bubble"
            ) {
                onNavigateDestination("floating_bubble")
                onCloseDrawer()
            }
            DrawerItem(
                icon = Icons.Default.Security,
                title = "🛡️ Space & Safety Shield",
                tint = Color(0xFF00E5FF),
                testTag = "drawer_item_manage_space"
            ) {
                onNavigateDestination("manage_space")
                onCloseDrawer()
            }
            DrawerItem(
                icon = Icons.Default.Memory,
                title = "🧠 5-Tier Memory Center",
                tint = Color(0xFF38BDF8),
                testTag = "drawer_item_memory"
            ) {
                onNavigateDestination("memory")
                onCloseDrawer()
            }
            DrawerItem(
                icon = Icons.Default.Build,
                title = "🎛️ Device & Hardware Controls",
                tint = Color(0xFF94A3B8),
                testTag = "drawer_item_controls"
            ) {
                onNavigateDestination("controls")
                onCloseDrawer()
            }
            DrawerItem(
                icon = Icons.Default.AutoAwesome,
                title = "🧙 Setup & Onboarding Wizard",
                tint = Color(0xFFF59E0B),
                testTag = "drawer_item_onboarding"
            ) {
                onNavigateDestination("onboarding")
                onCloseDrawer()
            }
            DrawerItem(
                icon = Icons.Default.SystemUpdate,
                title = "🔄 Check for App Updates",
                tint = Color(0xFF10B981),
                testTag = "drawer_item_check_updates"
            ) {
                onNavigateDestination("check_updates")
                onCloseDrawer()
            }
            DrawerItem(
                icon = Icons.Default.Settings,
                title = "⚙️ General & API Settings",
                tint = Color(0xFF94A3B8),
                testTag = "drawer_item_settings"
            ) {
                onNavigateDestination("settings")
                onCloseDrawer()
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF1F2937))
            Spacer(modifier = Modifier.height(14.dp))

            // --- SECTION 4: SEARCHABLE CHAT & VOICE HISTORY ---
            DrawerSectionTitle("CONVERSATION HISTORY")
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search conversations...",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search history",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1F2937),
                    unfocusedContainerColor = Color(0xFF1F2937),
                    focusedBorderColor = Color(0xFF00E5FF).copy(alpha = 0.4f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .testTag("drawer_history_search_input")
            )

            if (filteredSessions.isEmpty()) {
                Text(
                    text = if (searchQuery.isBlank()) "No conversations yet" else "No matching chats",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            } else {
                filteredSessions.take(20).forEach { session ->
                    val isSelected = session.id == currentSessionId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF1F2937) else Color.Transparent)
                            .clickable {
                                onSessionClick(session.id)
                                onCloseDrawer()
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = if (isSelected) Color(0xFF00E5FF) else Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = session.title.ifBlank { "Conversation #${session.id}" },
                            fontSize = 12.sp,
                            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerSectionTitle(title: String) {
    Text(
        text = title,
        color = Color(0xFF64748B),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
    )
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    title: String,
    tint: Color,
    testTag: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, color = Color(0xFFE2E8F0), fontSize = 13.sp)
    }
}
