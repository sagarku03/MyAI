package com.jarvisai.assistant.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.data.local.ChatSessionEntity
import java.io.File
import kotlin.math.max

private enum class DrawerViewTab {
    FOLDERS,
    TOOLS
}

private data class DrawerFolderGroup(
    val name: String,
    val sessions: List<ChatSessionEntity>,
    val isGeneral: Boolean,
    val isActive: Boolean
)

/**
 * AntiGem-Style Folder-Grouped Navigation Drawer for Jarvis AI.
 * - Conversations are grouped inside persistent folders (General, custom/project folders)
 * - Selecting or creating a folder locks "New Chat" to that folder even across new chats & restarts
 * - Shows top 5 chats per folder with "Show N more v" inline expansion, relative timestamps, and 3-dot menus
 * - Bottom bar provides 1-tap Settings, Theme, Tools & Workspaces Grid (all Jarvis modules), and Account status
 */
@Composable
fun JarvisNavigationDrawer(
    sessions: List<ChatSessionEntity>,
    currentSessionId: Long?,
    activeFolder: String = "General",
    folders: List<String> = listOf("General"),
    workspaces: List<File>,
    isAntigravityLoggedIn: Boolean = false,
    antigravityUserEmail: String = "",
    onSessionClick: (Long) -> Unit,
    onDeleteSessionClick: (Long) -> Unit = {},
    onRenameSession: (Long, String) -> Unit = { _, _ -> },
    onMoveSessionToFolder: (Long, String) -> Unit = { _, _ -> },
    onTogglePinSession: (Long, Boolean) -> Unit = { _, _ -> },
    onNewChatClick: () -> Unit,
    onNewChatInFolder: (String) -> Unit = { onNewChatClick() },
    onSelectFolder: (String) -> Unit = {},
    onCreateFolder: (name: String, createWorkspaceDir: Boolean) -> Unit = { _, _ -> },
    onRenameFolder: (oldName: String, newName: String) -> Unit = { _, _ -> },
    onDeleteFolder: (name: String, deleteChats: Boolean) -> Unit = { _, _ -> },
    onWorkspaceClick: (File) -> Unit,
    onNavigateDestination: (String) -> Unit,
    onCloseDrawer: () -> Unit
) {
    var activeTab by remember { mutableStateOf(DrawerViewTab.FOLDERS) }
    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var filterOnlyActiveFolder by remember { mutableStateOf(false) }

    // Track collapsed folders & "Show more" expanded folders by lowercase folder name
    val collapsedFolders = remember { mutableStateMapOf<String, Boolean>() }
    val expandedPreviewFolders = remember { mutableStateMapOf<String, Boolean>() }

    // Dialog states
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var renameFolderTarget by remember { mutableStateOf<String?>(null) }
    var deleteFolderTarget by remember { mutableStateOf<String?>(null) }
    var renameSessionTarget by remember { mutableStateOf<ChatSessionEntity?>(null) }
    var moveSessionTarget by remember { mutableStateOf<ChatSessionEntity?>(null) }

    val normalizedActiveFolder = remember(activeFolder) {
        activeFolder.trim().ifBlank { "General" }
    }

    val uniqueCleanSessions = remember(sessions, searchQuery) {
        val unique = sessions
            .distinctBy { it.id }
            .filterNot { it.title.matches(Regex("^Session #\\d+$")) && it.createdAt == it.updatedAt }
        if (searchQuery.isBlank()) {
            unique
        } else {
            val q = searchQuery.trim()
            unique.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.folder.contains(q, ignoreCase = true)
            }
        }
    }

    val folderGroups = remember(uniqueCleanSessions, folders, normalizedActiveFolder, filterOnlyActiveFolder, searchQuery) {
        val groupedByLower = linkedMapOf<String, MutableList<ChatSessionEntity>>()
        val displayNameByLower = linkedMapOf<String, String>()

        fun registerFolder(rawName: String) {
            val clean = rawName.trim().ifBlank { "General" }
            val key = clean.lowercase()
            if (!displayNameByLower.containsKey(key)) {
                displayNameByLower[key] = if (key == "general") "General" else clean
                groupedByLower[key] = mutableListOf()
            }
        }

        registerFolder("General")
        folders.forEach { registerFolder(it) }
        registerFolder(normalizedActiveFolder)

        uniqueCleanSessions.forEach { session ->
            val f = session.folder.trim().ifBlank { "General" }
            val key = f.lowercase()
            registerFolder(f)
            groupedByLower.getOrPut(key) { mutableListOf() }.add(session)
        }

        val activeKey = normalizedActiveFolder.lowercase()
        val orderedKeys = buildList {
            if (filterOnlyActiveFolder && searchQuery.isBlank()) {
                add(activeKey)
            } else {
                add("general")
                if (activeKey != "general" && groupedByLower.containsKey(activeKey)) {
                    add(activeKey)
                }
                groupedByLower.keys
                    .filter { it != "general" && it != activeKey }
                    .sorted()
                    .forEach { add(it) }
            }
        }.distinct()

        orderedKeys.mapNotNull { key ->
            val list = groupedByLower[key] ?: return@mapNotNull null
            if (searchQuery.isNotBlank() && list.isEmpty()) return@mapNotNull null
            val name = displayNameByLower[key] ?: "General"
            DrawerFolderGroup(
                name = name,
                sessions = list.sortedWith(
                    compareByDescending<ChatSessionEntity> { it.isPinned }
                        .thenByDescending { it.updatedAt }
                ),
                isGeneral = key == "general",
                isActive = key == activeKey
            )
        }
    }

    val allFolderNames = remember(folders, sessions, normalizedActiveFolder) {
        val seen = linkedMapOf<String, String>()
        fun addName(raw: String) {
            val clean = raw.trim().ifBlank { "General" }
            val k = clean.lowercase()
            if (!seen.containsKey(k)) seen[k] = if (k == "general") "General" else clean
        }
        addName("General")
        folders.forEach { addName(it) }
        sessions.forEach { addName(it.folder) }
        addName(normalizedActiveFolder)
        seen.values.toList()
    }

    ModalDrawerSheet(
        drawerContainerColor = Color(0xFF0E131D),
        drawerContentColor = Color.White,
        modifier = Modifier
            .width(326.dp)
            .testTag("jarvis_navigation_drawer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // --- 1. ANTIGEM-STYLE TOP HEADER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Agentra",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (activeTab == DrawerViewTab.FOLDERS) {
                            "Folder: $normalizedActiveFolder"
                        } else {
                            "Tools & Workspaces"
                        },
                        fontSize = 11.sp,
                        color = Color(0xFF60A5FA),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    HeaderIconAction(
                        icon = Icons.Default.Search,
                        contentDescription = "Search conversations",
                        isActive = isSearchVisible && activeTab == DrawerViewTab.FOLDERS,
                        onClick = {
                            activeTab = DrawerViewTab.FOLDERS
                            isSearchVisible = !isSearchVisible
                            if (!isSearchVisible) searchQuery = ""
                        }
                    )
                    HeaderIconAction(
                        icon = Icons.Default.Folder,
                        contentDescription = "Folder conversations view",
                        isActive = activeTab == DrawerViewTab.FOLDERS,
                        testTag = "drawer_folder_filter_button",
                        onClick = {
                            if (activeTab != DrawerViewTab.FOLDERS) {
                                activeTab = DrawerViewTab.FOLDERS
                            } else {
                                filterOnlyActiveFolder = !filterOnlyActiveFolder
                            }
                        }
                    )
                    HeaderIconAction(
                        icon = Icons.Default.Inventory2,
                        contentDescription = "Generated Artifacts",
                        isActive = false,
                        onClick = {
                            onNavigateDestination("artifacts")
                            onCloseDrawer()
                        }
                    )
                    HeaderIconAction(
                        icon = Icons.Default.PowerSettingsNew,
                        contentDescription = "Linux Engine & Agent Hub",
                        isActive = false,
                        tint = Color(0xFFF87171),
                        onClick = {
                            onNavigateDestination("agent_hub")
                            onCloseDrawer()
                        }
                    )
                }
            }

            // --- 2. ACTIVE FOLDER + CREATE FOLDER + NEW CHAT CONTROLS ---
            if (activeTab == DrawerViewTab.FOLDERS) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // + New Chat in Active Folder (Primary)
                    Button(
                        onClick = {
                            onNewChatClick()
                            onCloseDrawer()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("drawer_new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "New Chat • $normalizedActiveFolder",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // + Create Folder
                    Surface(
                        onClick = { showCreateFolderDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF172033),
                        border = BorderStroke(1.dp, Color(0xFF60A5FA).copy(alpha = 0.45f)),
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("drawer_new_folder_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = "Create New Folder",
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Folder",
                                color = Color(0xFFBFDBFE),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Filter badge notice if user toggled active-folder-only filter
                AnimatedVisibility(visible = filterOnlyActiveFolder) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Showing only: $normalizedActiveFolder",
                            fontSize = 11.sp,
                            color = Color(0xFF60A5FA),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Show All",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { filterOnlyActiveFolder = false }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Expandable Search Bar
                AnimatedVisibility(visible = isSearchVisible) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search folders & chats...",
                                fontSize = 12.5.sp,
                                color = Color(0xFF64748B)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search history",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF171E2C),
                            unfocusedContainerColor = Color(0xFF171E2C),
                            focusedBorderColor = Color(0xFF38BDF8).copy(alpha = 0.5f),
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("drawer_history_search_input")
                    )
                }
            }

            HorizontalDivider(
                color = Color(0xFF1E293B).copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )

            // --- 3. MAIN BODY (ANIMATED BETWEEN FOLDER CONVERSATIONS & TOOLS HUB) ---
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        fadeIn(tween(180)) togetherWith fadeOut(tween(180))
                    },
                    label = "DrawerBodyTab"
                ) { tab ->
                    when (tab) {
                        DrawerViewTab.FOLDERS -> {
                            if (folderGroups.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = if (searchQuery.isBlank()) "No conversations yet" else "No matching conversations",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    folderGroups.forEach { group ->
                                        val key = group.name.lowercase()
                                        val isCollapsed = (collapsedFolders[key] == true) && searchQuery.isBlank()
                                        val showAllInFolder = (expandedPreviewFolders[key] == true) || searchQuery.isNotBlank()
                                        val visibleLimit = if (showAllInFolder) group.sessions.size else 5
                                        val visibleSessions = if (isCollapsed) emptyList() else group.sessions.take(visibleLimit)
                                        val hiddenCount = max(0, group.sessions.size - visibleLimit)

                                        item(key = "folder_header_$key") {
                                            FolderGroupHeaderRow(
                                                group = group,
                                                isCollapsed = isCollapsed,
                                                onSelectAndToggle = {
                                                    onSelectFolder(group.name)
                                                    if (isCollapsed) {
                                                        collapsedFolders[key] = false
                                                    }
                                                },
                                                onToggleCollapse = {
                                                    collapsedFolders[key] = !isCollapsed
                                                },
                                                onNewChatHere = {
                                                    onNewChatInFolder(group.name)
                                                    onCloseDrawer()
                                                },
                                                onRenameFolder = {
                                                    renameFolderTarget = group.name
                                                },
                                                onDeleteFolder = {
                                                    deleteFolderTarget = group.name
                                                }
                                            )
                                        }

                                        if (!isCollapsed) {
                                            if (group.sessions.isEmpty()) {
                                                item(key = "folder_empty_$key") {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(start = 34.dp, end = 12.dp, top = 2.dp, bottom = 6.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable {
                                                                onNewChatInFolder(group.name)
                                                                onCloseDrawer()
                                                            }
                                                            .padding(vertical = 6.dp, horizontal = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Add,
                                                            contentDescription = null,
                                                            tint = Color(0xFF60A5FA),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "Start first chat in ${group.name}",
                                                            fontSize = 12.sp,
                                                            color = Color(0xFF94A3B8)
                                                        )
                                                    }
                                                }
                                            } else {
                                                items(
                                                    items = visibleSessions,
                                                    key = { "session_${key}_${it.id}" }
                                                ) { session ->
                                                    FolderSessionRow(
                                                        session = session,
                                                        isSelected = session.id == currentSessionId,
                                                        onClick = {
                                                            onSessionClick(session.id)
                                                            onCloseDrawer()
                                                        },
                                                        onMoveToFolder = { moveSessionTarget = session },
                                                        onRename = { renameSessionTarget = session },
                                                        onTogglePin = { onTogglePinSession(session.id, !session.isPinned) },
                                                        onDelete = { onDeleteSessionClick(session.id) }
                                                    )
                                                }

                                                if (group.sessions.size > 5 && searchQuery.isBlank()) {
                                                    item(key = "folder_more_$key") {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(start = 24.dp, end = 12.dp, top = 2.dp, bottom = 4.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .clickable {
                                                                    expandedPreviewFolders[key] = !showAllInFolder
                                                                }
                                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = if (showAllInFolder) "Show less" else "Show $hiddenCount more",
                                                                fontSize = 12.5.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                color = Color(0xFF93C5FD)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Icon(
                                                                imageVector = if (showAllInFolder) {
                                                                    Icons.Default.KeyboardArrowUp
                                                                } else {
                                                                    Icons.Default.KeyboardArrowDown
                                                                },
                                                                contentDescription = null,
                                                                tint = Color(0xFF93C5FD),
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        DrawerViewTab.TOOLS -> {
                            JarvisToolsDrawerBody(
                                workspaces = workspaces,
                                isAntigravityLoggedIn = isAntigravityLoggedIn,
                                antigravityUserEmail = antigravityUserEmail,
                                onBackToFolders = { activeTab = DrawerViewTab.FOLDERS },
                                onWorkspaceClick = onWorkspaceClick,
                                onNavigateDestination = onNavigateDestination,
                                onCloseDrawer = onCloseDrawer
                            )
                        }
                    }
                }
            }

            // --- 4. ANTIGEM-STYLE BOTTOM FOOTER BAR ---
            HorizontalDivider(color = Color(0xFF1E293B))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B0F17))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            onNavigateDestination("settings")
                            onCloseDrawer()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("drawer_footer_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            onNavigateDestination("themes")
                            onCloseDrawer()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("drawer_footer_theme")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Appearance & Theme",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Surface(
                        onClick = {
                            activeTab = if (activeTab == DrawerViewTab.TOOLS) {
                                DrawerViewTab.FOLDERS
                            } else {
                                DrawerViewTab.TOOLS
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (activeTab == DrawerViewTab.TOOLS) {
                            Color(0xFF2563EB).copy(alpha = 0.25f)
                        } else {
                            Color.Transparent
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("drawer_tools_toggle_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = "Agentra Tools & Workspaces",
                                tint = if (activeTab == DrawerViewTab.TOOLS) Color(0xFF60A5FA) else Color(0xFFCBD5E1),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Account Pill (Right)
                val displayAccount = remember(isAntigravityLoggedIn, antigravityUserEmail) {
                    when {
                        isAntigravityLoggedIn && antigravityUserEmail.isNotBlank() ->
                            antigravityUserEmail.substringBefore("@").take(14)
                        isAntigravityLoggedIn -> "Antigravity"
                        else -> "Sign In"
                    }
                }
                Surface(
                    onClick = {
                        onNavigateDestination("antigravity_login")
                        onCloseDrawer()
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF161E2E),
                    border = BorderStroke(
                        1.dp,
                        if (isAntigravityLoggedIn) Color(0xFF10B981).copy(alpha = 0.35f)
                        else Color(0xFF334155)
                    ),
                    modifier = Modifier.testTag("drawer_footer_account_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E3A8A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = displayAccount.firstOrNull()?.uppercase() ?: "J",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isAntigravityLoggedIn) Color(0xFF10B981) else Color(0xFF64748B)
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = displayAccount,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    // --- DIALOGS ---
    if (showCreateFolderDialog) {
        CreateChatFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onCreate = { name, createWs ->
                showCreateFolderDialog = false
                onCreateFolder(name, createWs)
                activeTab = DrawerViewTab.FOLDERS
                onCloseDrawer()
            }
        )
    }

    renameFolderTarget?.let { oldFolder ->
        RenameTextDialog(
            title = "Rename Folder",
            initialValue = oldFolder,
            label = "Folder name",
            onDismiss = { renameFolderTarget = null },
            onConfirm = { newName ->
                renameFolderTarget = null
                onRenameFolder(oldFolder, newName)
            }
        )
    }

    deleteFolderTarget?.let { folderToDelete ->
        AlertDialog(
            onDismissRequest = { deleteFolderTarget = null },
            containerColor = Color(0xFF111827),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCBD5E1),
            title = { Text("Delete \"$folderToDelete\"?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Choose whether to move conversations in \"$folderToDelete\" back to General or permanently delete them.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteFolderTarget = null
                        onDeleteFolder(folderToDelete, false)
                    }
                ) {
                    Text("Move Chats to General", color = Color(0xFF38BDF8))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            deleteFolderTarget = null
                            onDeleteFolder(folderToDelete, true)
                        }
                    ) {
                        Text("Delete All", color = Color(0xFFF87171))
                    }
                    TextButton(onClick = { deleteFolderTarget = null }) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                }
            }
        )
    }

    renameSessionTarget?.let { session ->
        RenameTextDialog(
            title = "Rename Conversation",
            initialValue = session.title,
            label = "Title",
            onDismiss = { renameSessionTarget = null },
            onConfirm = { newTitle ->
                renameSessionTarget = null
                onRenameSession(session.id, newTitle)
            }
        )
    }

    moveSessionTarget?.let { session ->
        MoveSessionFolderDialog(
            session = session,
            folders = allFolderNames,
            onDismiss = { moveSessionTarget = null },
            onMove = { target ->
                moveSessionTarget = null
                onMoveSessionToFolder(session.id, target)
            }
        )
    }
}

@Composable
private fun HeaderIconAction(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    tint: Color = Color(0xFFCBD5E1),
    testTag: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isActive) Color(0xFF2563EB).copy(alpha = 0.28f) else Color.Transparent,
        modifier = Modifier
            .size(38.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isActive) Color(0xFF60A5FA) else tint,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun FolderGroupHeaderRow(
    group: DrawerFolderGroup,
    isCollapsed: Boolean,
    onSelectAndToggle: () -> Unit,
    onToggleCollapse: () -> Unit,
    onNewChatHere: () -> Unit,
    onRenameFolder: () -> Unit,
    onDeleteFolder: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (group.isActive) Color(0xFF17253B).copy(alpha = 0.72f) else Color.Transparent
            )
            .clickable(onClick = onSelectAndToggle)
            .padding(start = 8.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (group.isGeneral) Icons.Default.Public else Icons.Default.Folder,
            contentDescription = group.name,
            tint = if (group.isActive) Color(0xFF60A5FA) else Color(0xFF94A3B8),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = group.name,
            fontSize = 13.5.sp,
            fontWeight = if (group.isActive) FontWeight.Bold else FontWeight.SemiBold,
            color = if (group.isActive) Color.White else Color(0xFFE2E8F0),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(6.dp))

        // Session count pill (like "98", "8", "4")
        Surface(
            shape = CircleShape,
            color = Color(0xFF1E293B)
        ) {
            Text(
                text = "${group.sessions.size}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
            )
        }

        if (group.isActive) {
            Spacer(modifier = Modifier.width(5.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF0EA5E9).copy(alpha = 0.2f)
            ) {
                Text(
                    text = "ACTIVE",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8),
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Quick + New Chat inside this folder
        IconButton(
            onClick = onNewChatHere,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New chat in ${group.name}",
                tint = Color(0xFF60A5FA),
                modifier = Modifier.size(16.dp)
            )
        }

        // Folder Overflow Menu (for non-General or Set Active)
        if (!group.isGeneral) {
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Folder options",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(15.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color(0xFF1E293B))
                ) {
                    DropdownMenuItem(
                        text = { Text("New chat here", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Add, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(17.dp)) },
                        onClick = {
                            showMenu = false
                            onNewChatHere()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename folder", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(17.dp)) },
                        onClick = {
                            showMenu = false
                            onRenameFolder()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete folder", color = Color(0xFFF87171), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFF87171), modifier = Modifier.size(17.dp)) },
                        onClick = {
                            showMenu = false
                            onDeleteFolder()
                        }
                    )
                }
            }
        }

        // Collapse / Expand chevron
        IconButton(
            onClick = onToggleCollapse,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = if (isCollapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isCollapsed) "Expand folder" else "Collapse folder",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun FolderSessionRow(
    session: ChatSessionEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onMoveToFolder: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val relativeAge = remember(session.updatedAt) { formatRelativeAge(session.updatedAt) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF1E293B) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 2.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSelected) Icons.AutoMirrored.Filled.Chat else Icons.AutoMirrored.Outlined.Chat,
            contentDescription = null,
            tint = if (isSelected) Color(0xFF38BDF8) else Color(0xFF64748B),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = session.title.ifBlank { "Conversation #${session.id}" },
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) Color.White else Color(0xFFE2E8F0),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (session.isPinned) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = "Pinned",
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(11.dp)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = relativeAge,
            fontSize = 11.5.sp,
            color = if (isSelected) Color(0xFF93C5FD) else Color(0xFF64748B),
            maxLines = 1
        )
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Conversation options",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(15.dp)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(Color(0xFF1E293B))
            ) {
                DropdownMenuItem(
                    text = { Text("Move to Folder...", color = Color.White, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null, tint = Color(0xFF60A5FA), modifier = Modifier.size(17.dp)) },
                    onClick = {
                        showMenu = false
                        onMoveToFolder()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Rename", color = Color.White, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(17.dp)) },
                    onClick = {
                        showMenu = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (session.isPinned) "Unpin" else "Pin to top", color = Color.White, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.PushPin, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(17.dp)) },
                    onClick = {
                        showMenu = false
                        onTogglePin()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color(0xFFF87171), fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFF87171), modifier = Modifier.size(17.dp)) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun JarvisToolsDrawerBody(
    workspaces: List<File>,
    isAntigravityLoggedIn: Boolean,
    antigravityUserEmail: String,
    onBackToFolders: () -> Unit,
    onWorkspaceClick: (File) -> Unit,
    onNavigateDestination: (String) -> Unit,
    onCloseDrawer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Quick return pill
        Surface(
            onClick = onBackToFolders,
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF17253B),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Back to Folder Conversations",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        DrawerSectionTitle("DEVELOPER TOOLS")
        DrawerRoundedSectionBox {
            DrawerItem(Icons.Default.Code, "📝 Sora Code Editor", Color(0xFF38BDF8), "drawer_item_ide") {
                onNavigateDestination("ide"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.Language, "🌐 Chrome Web Browser", Color(0xFF00E5FF), "drawer_item_browser") {
                onNavigateDestination("browser"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.Terminal, "💻 Embedded Linux Terminal", Color(0xFF00E5FF), "drawer_item_terminal") {
                onNavigateDestination("terminal"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.Build, "📦 Linux Core Engine (352MB)", Color(0xFF10B981), "drawer_item_rootfs") {
                onNavigateDestination("rootfs_installer"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.Description, "📄 Generated Artifacts Shelf", Color(0xFF00E5FF), "drawer_item_artifacts") {
                onNavigateDestination("artifacts"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.Hub, "🤖 Multi-Agent Hub (Claude / OpenCode)", Color(0xFFF472B6), "drawer_item_agent_hub") {
                onNavigateDestination("agent_hub"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.DeveloperMode, "📲 Wireless ADB & God-Mode", Color(0xFF10B981), "drawer_item_adb") {
                onNavigateDestination("adb_connect"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.Cloud, "🖥️ Connect to Desktop PC", Color(0xFF00FFCC), "drawer_item_connect_pc") {
                onNavigateDestination("connect_pc"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.Extension, "🧩 Plugins & Chrome Extensions", Color(0xFFA78BFA), "drawer_item_plugins") {
                onNavigateDestination("plugins"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.Mic, "🎙️ Full Live Voice Studio", Color(0xFF00E5FF), "drawer_item_voice") {
                onNavigateDestination("voice"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.Terminal, "🐚 Termux Bridge & Permissions", Color(0xFF10B981), "drawer_item_termux_setup") {
                onNavigateDestination("termux_setup"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.Security, "🔐 Permissions", Color(0xFF38BDF8), "drawer_item_permissions") {
                onNavigateDestination("permissions"); onCloseDrawer()
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        DrawerSectionTitle("MULTIMODAL & AI FEATURES")
        DrawerRoundedSectionBox {
            DrawerItem(Icons.Default.CameraAlt, "📸 Camera & Vision Analysis", Color(0xFF38BDF8), "drawer_item_vision") {
                onNavigateDestination("vision"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.Description, "📄 Document & PDF Reader", Color(0xFFA78BFA), "drawer_item_document") {
                onNavigateDestination("document"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.Calculate, "🧮 Optical Math & Code Solver", Color(0xFFF59E0B), "drawer_item_optical_solver") {
                onNavigateDestination("optical_solver"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.Translate, "🌐 Polyglot Live Translator", Color(0xFF00E5FF), "drawer_item_polyglot") {
                onNavigateDestination("polyglot_diplomat"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.BatteryChargingFull, "🔋 Arc Reactor Telemetry", Color(0xFF10B981), "drawer_item_charging_telemetry") {
                onNavigateDestination("charging_telemetry"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.AutoMode, "⚡ Autonomous Workflows & Macros", Color(0xFFF472B6), "drawer_item_workflow") {
                onNavigateDestination("workflow"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.Notifications, "🔔 Smart Notifications Assistant", Color(0xFF38BDF8), "drawer_item_notifications") {
                onNavigateDestination("notifications"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.Dashboard, "🎛️ Command Deck Dashboard", Color(0xFF00E5FF), "drawer_item_dashboard_deck") {
                onNavigateDestination("dashboard_deck"); onCloseDrawer()
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        DrawerSectionTitle("SECURITY & CONTROLS")
        DrawerRoundedSectionBox {
            DrawerItem(Icons.Default.PictureInPictureAlt, "🫧 Floating ChatHead Bubble", Color(0xFF00E5FF), "drawer_item_floating_bubble") {
                onNavigateDestination("floating_bubble"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.Security, "🛡️ Space & Safety Shield", Color(0xFF00E5FF), "drawer_item_manage_space") {
                onNavigateDestination("manage_space"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.Memory, "🧠 5-Tier Memory Center", Color(0xFF38BDF8), "drawer_item_memory") {
                onNavigateDestination("memory"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.Build, "🎛️ Device & Hardware Controls", Color(0xFF94A3B8), "drawer_item_controls") {
                onNavigateDestination("controls"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.AutoAwesome, "🧙 Setup & Onboarding Wizard", Color(0xFFF59E0B), "drawer_item_onboarding") {
                onNavigateDestination("onboarding"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.SystemUpdate, "🔄 Check for App Updates", Color(0xFF10B981), "drawer_item_check_updates") {
                onNavigateDestination("check_updates"); onCloseDrawer()
            }
            DrawerItem(Icons.Default.Settings, "⚙️ General & API Settings", Color(0xFF94A3B8), "drawer_item_settings") {
                onNavigateDestination("settings"); onCloseDrawer()
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun CreateChatFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, createWorkspaceDir: Boolean) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    var createWorkspaceDir by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFCBD5E1),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = null,
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Folder", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "New chats stay inside this selected folder until you switch folders.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name (e.g. Short Reels, assistant)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedLabelColor = Color(0xFF38BDF8),
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { createWorkspaceDir = !createWorkspaceDir }
                        .padding(vertical = 2.dp)
                ) {
                    Checkbox(
                        checked = createWorkspaceDir,
                        onCheckedChange = { createWorkspaceDir = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF38BDF8),
                            uncheckedColor = Color(0xFF64748B)
                        )
                    )
                    Text(
                        text = "Also create coding workspace directory",
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = folderName.trim()
                    if (clean.isNotBlank()) onCreate(clean, createWorkspaceDir)
                },
                enabled = folderName.trim().isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text("Create & Select", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}

@Composable
private fun RenameTextDialog(
    title: String,
    initialValue: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textValue by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        titleContentColor = Color.White,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                label = { Text(label) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedLabelColor = Color(0xFF38BDF8),
                    unfocusedLabelColor = Color(0xFF94A3B8)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = textValue.trim()
                    if (clean.isNotBlank()) onConfirm(clean)
                },
                enabled = textValue.trim().isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text("Save", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}

@Composable
private fun MoveSessionFolderDialog(
    session: ChatSessionEntity,
    folders: List<String>,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit
) {
    var newFolderInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFCBD5E1),
        title = { Text("Move to Folder", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = session.title.ifBlank { "Conversation" },
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                folders.forEach { folderName ->
                    val isCurrent = session.folder.equals(folderName, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCurrent) Color(0xFF1E293B) else Color.Transparent)
                            .clickable { onMove(folderName) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (folderName.equals("General", ignoreCase = true)) {
                                Icons.Default.Public
                            } else {
                                Icons.Default.Folder
                            },
                            contentDescription = null,
                            tint = if (isCurrent) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = folderName,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Current folder",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = newFolderInput,
                        onValueChange = { newFolderInput = it },
                        placeholder = { Text("Or new folder...", fontSize = 12.sp, color = Color(0xFF64748B)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            val clean = newFolderInput.trim()
                            if (clean.isNotBlank()) onMove(clean)
                        },
                        enabled = newFolderInput.trim().isNotBlank()
                    ) {
                        Text("Move", color = Color(0xFF38BDF8), fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF94A3B8))
            }
        }
    )
}

private fun formatRelativeAge(timestampMs: Long): String {
    val diffMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val minutes = diffMs / 60_000L
    val hours = diffMs / 3_600_000L
    val days = diffMs / 86_400_000L
    return when {
        minutes < 1L -> "now"
        minutes < 60L -> "${minutes}m"
        hours < 24L -> "${hours}h"
        days < 30L -> "${days}d"
        else -> "${days / 30}mo"
    }
}

@Composable
private fun DrawerSectionTitle(title: String) {
    Text(
        text = title,
        color = Color(0xFF94A3B8),
        fontSize = 10.5.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
    )
}

@Composable
private fun DrawerRoundedSectionBox(
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF111827),
        border = BorderStroke(1.dp, Color(0xFF1F2A3D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    title: String,
    tint: Color,
    testTag: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF172033),
        border = BorderStroke(0.8.dp, Color(0xFF25324B)),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
