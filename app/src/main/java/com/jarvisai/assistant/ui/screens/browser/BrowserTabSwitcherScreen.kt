package com.jarvisai.assistant.ui.screens.browser

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.browser.BrowserAdBlockEngine
import com.jarvisai.assistant.browser.BrowserDataStore
import com.jarvisai.assistant.browser.TabGroupRecord

@Composable
fun BrowserTabSwitcherScreen(
    tabs: List<QuettaTab>,
    activeTabId: String,
    tabGroups: List<TabGroupRecord>,
    initialMode: TabSwitcherMode,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: (isPrivate: Boolean, groupId: String?) -> Unit,
    onCloseAllInMode: (isPrivate: Boolean) -> Unit,
    onCreateGroup: (title: String, tabIds: List<String>) -> Unit,
    onAssignTabToGroup: (tabId: String, groupId: String?) -> Unit,
    onDeleteGroup: (groupId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var overflowExpanded by remember { mutableStateOf(false) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var newGroupTitle by remember { mutableStateOf("") }

    val normalTabs = remember(tabs) { tabs.filter { !it.isPrivate } }
    val privateTabs = remember(tabs) { tabs.filter { it.isPrivate } }

    val visibleTabs = remember(tabs, mode, searchQuery, selectedGroupId) {
        val base = when (mode) {
            TabSwitcherMode.NORMAL -> tabs.filter { !it.isPrivate }
            TabSwitcherMode.PRIVATE -> tabs.filter { it.isPrivate }
            TabSwitcherMode.GROUPS -> {
                if (selectedGroupId != null) {
                    tabs.filter { it.groupId == selectedGroupId }
                } else {
                    emptyList()
                }
            }
        }
        if (searchQuery.isBlank()) base else {
            val q = searchQuery.trim().lowercase()
            base.filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
        }
    }

    val activeAccent = if (mode == TabSwitcherMode.PRIVATE) QuettaColors.PrivateAccent else QuettaColors.Coral

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (mode == TabSwitcherMode.PRIVATE) Color(0xFF0E0C14) else QuettaColors.BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("browser_tab_switcher")
    ) {
        // Top Header matching Quetta Tab Switcher (`00:35`)
        if (mode == TabSwitcherMode.GROUPS && selectedGroupId != null) {
            val currentGroup = tabGroups.firstOrNull { it.id == selectedGroupId }
            val groupTabsCount = tabs.count { it.groupId == selectedGroupId }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedGroupId = null }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Groups",
                        tint = QuettaColors.TextPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentGroup?.title ?: "Tab Group",
                        color = QuettaColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$groupTabsCount tabs",
                        color = QuettaColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = { onNewTab(false, selectedGroupId) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add tab to group", tint = QuettaColors.Coral)
                }
                IconButton(onClick = {
                    val gid = selectedGroupId
                    selectedGroupId = null
                    if (gid != null) onDeleteGroup(gid)
                }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Ungroup tabs", tint = QuettaColors.TextSecondary)
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { searchOpen = !searchOpen }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search tabs",
                        tint = if (searchOpen) activeAccent else QuettaColors.TextPrimary
                    )
                }

                // 3-Mode Selector Pill: Normal [N] | Private | Groups
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(QuettaColors.Surface)
                        .border(1.dp, QuettaColors.CardBorder, RoundedCornerShape(999.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Normal Tabs Pill with Tab Count Box
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (mode == TabSwitcherMode.NORMAL) QuettaColors.SurfaceElevated else Color.Transparent)
                            .clickable {
                                mode = TabSwitcherMode.NORMAL
                                selectedGroupId = null
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("tab_mode_normal"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .border(
                                    1.6.dp,
                                    if (mode == TabSwitcherMode.NORMAL) QuettaColors.TextPrimary else QuettaColors.TextSecondary,
                                    RoundedCornerShape(5.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = normalTabs.size.toString(),
                                color = if (mode == TabSwitcherMode.NORMAL) QuettaColors.TextPrimary else QuettaColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Private / Incognito Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (mode == TabSwitcherMode.PRIVATE) QuettaColors.PrivateAccent.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable {
                                mode = TabSwitcherMode.PRIVATE
                                selectedGroupId = null
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("tab_mode_private"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "Private Tabs",
                            tint = if (mode == TabSwitcherMode.PRIVATE) QuettaColors.PrivateAccent else QuettaColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Tab Groups Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (mode == TabSwitcherMode.GROUPS) QuettaColors.Coral.copy(alpha = 0.22f) else Color.Transparent)
                            .clickable { mode = TabSwitcherMode.GROUPS }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("tab_mode_groups"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Tab Groups",
                            tint = if (mode == TabSwitcherMode.GROUPS) QuettaColors.Coral else QuettaColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Overflow Menu
                Box {
                    IconButton(onClick = { overflowExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Tab options", tint = QuettaColors.TextPrimary)
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                        containerColor = QuettaColors.SurfaceElevated
                    ) {
                        DropdownMenuItem(
                            text = { Text("New Tab", color = QuettaColors.TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Add, null, tint = QuettaColors.Coral) },
                            onClick = {
                                overflowExpanded = false
                                onNewTab(mode == TabSwitcherMode.PRIVATE, null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Create Tab Group", color = QuettaColors.TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, null, tint = QuettaColors.SkyBlue) },
                            onClick = {
                                overflowExpanded = false
                                showCreateGroupDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (mode == TabSwitcherMode.PRIVATE) "Close All Private Tabs" else "Close All Tabs",
                                    color = QuettaColors.Coral
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, null, tint = QuettaColors.Coral) },
                            onClick = {
                                overflowExpanded = false
                                onCloseAllInMode(mode == TabSwitcherMode.PRIVATE)
                            }
                        )
                    }
                }
            }
        }

        if (searchOpen) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search open tabs...", color = QuettaColors.TextSecondary, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = activeAccent,
                    unfocusedBorderColor = QuettaColors.CardBorder,
                    focusedContainerColor = QuettaColors.Surface,
                    unfocusedContainerColor = QuettaColors.Surface,
                    focusedTextColor = QuettaColors.TextPrimary,
                    unfocusedTextColor = QuettaColors.TextPrimary
                )
            )
        }

        // Grid Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (mode == TabSwitcherMode.GROUPS && selectedGroupId == null) {
                if (tabGroups.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = QuettaColors.TextSecondary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Tab Groups Yet",
                            color = QuettaColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Group related research, shopping, or AI tabs cleanly together.",
                            color = QuettaColors.TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(QuettaColors.Coral)
                                .clickable { showCreateGroupDialog = true }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text("Create Group", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(tabGroups, key = { it.id }) { group ->
                            val groupTabs = tabs.filter { it.groupId == group.id }
                            TabGroupGridCard(
                                group = group,
                                groupTabs = groupTabs,
                                onOpen = { selectedGroupId = group.id },
                                onDelete = { onDeleteGroup(group.id) }
                            )
                        }
                    }
                }
            } else if (visibleTabs.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (mode == TabSwitcherMode.PRIVATE) Icons.Default.VisibilityOff else Icons.Default.Language,
                        contentDescription = null,
                        tint = if (mode == TabSwitcherMode.PRIVATE) QuettaColors.PrivateAccent else QuettaColors.TextSecondary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (mode == TabSwitcherMode.PRIVATE) "Private Browsing" else "No Matching Tabs",
                        color = QuettaColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (mode == TabSwitcherMode.PRIVATE) {
                            "Pages viewed in private tabs aren't saved to history."
                        } else {
                            "Tap + New Tab below to open a page."
                        },
                        color = QuettaColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(visibleTabs, key = { it.id }) { tab ->
                        TabThumbnailCard(
                            tab = tab,
                            isActive = tab.id == activeTabId,
                            tabGroups = tabGroups,
                            onSelect = { onSelectTab(tab.id) },
                            onClose = { onCloseTab(tab.id) },
                            onAssignGroup = { gid -> onAssignTabToGroup(tab.id, gid) }
                        )
                    }
                }
            }
        }

        // Bottom Bar of Tab Switcher: [+ New Tab] and [Done]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(QuettaColors.Surface)
                .border(1.dp, QuettaColors.CardBorder)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(activeAccent)
                    .clickable {
                        onNewTab(
                            mode == TabSwitcherMode.PRIVATE,
                            if (mode == TabSwitcherMode.GROUPS) selectedGroupId else null
                        )
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .testTag("tab_switcher_new_tab_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(
                    text = if (mode == TabSwitcherMode.PRIVATE) "Private Tab" else "New Tab",
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(QuettaColors.SurfaceElevated)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .testTag("tab_switcher_done_btn")
            ) {
                Text(
                    text = "Done",
                    color = QuettaColors.TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            containerColor = QuettaColors.Surface,
            title = { Text("Create Tab Group", color = QuettaColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newGroupTitle,
                    onValueChange = { newGroupTitle = it },
                    placeholder = { Text("e.g. Shopping, Research, Work", color = QuettaColors.TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = QuettaColors.Coral,
                        unfocusedBorderColor = QuettaColors.CardBorder,
                        focusedTextColor = QuettaColors.TextPrimary,
                        unfocusedTextColor = QuettaColors.TextPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val label = newGroupTitle.trim().ifBlank { "Group ${tabGroups.size + 1}" }
                    onCreateGroup(label, listOf(activeTabId))
                    newGroupTitle = ""
                    showCreateGroupDialog = false
                    mode = TabSwitcherMode.GROUPS
                }) {
                    Text("Create", color = QuettaColors.Coral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancel", color = QuettaColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun TabThumbnailCard(
    tab: QuettaTab,
    isActive: Boolean,
    tabGroups: List<TabGroupRecord>,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onAssignGroup: (String?) -> Unit
) {
    var showGroupMenu by remember { mutableStateOf(false) }
    val borderColor = when {
        isActive && tab.isPrivate -> QuettaColors.PrivateAccent
        isActive -> QuettaColors.Coral
        else -> QuettaColors.CardBorder
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.74f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (tab.isPrivate) QuettaColors.PrivateSurface else QuettaColors.Surface)
            .border(if (isActive) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
    ) {
        // Card Title Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (tab.isPrivate) Color(0xFF231B33) else QuettaColors.SurfaceElevated)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (tab.isPrivate) QuettaColors.PrivateAccent else QuettaColors.Coral.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (tab.isHomePage) "J" else BrowserAdBlockEngine.extractHost(tab.url).take(1).uppercase().ifBlank { "W" },
                    color = if (tab.isPrivate) Color.White else QuettaColors.Coral,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (tab.isHomePage) "Start Page" else tab.title.ifBlank { BrowserAdBlockEngine.extractHost(tab.url) },
                color = QuettaColors.TextPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (tabGroups.isNotEmpty() && !tab.isPrivate) {
                Box {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Assign to group",
                        tint = if (tab.groupId != null) QuettaColors.Coral else QuettaColors.TextSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { showGroupMenu = true }
                    )
                    DropdownMenu(
                        expanded = showGroupMenu,
                        onDismissRequest = { showGroupMenu = false },
                        containerColor = QuettaColors.SurfaceElevated
                    ) {
                        tabGroups.forEach { grp ->
                            DropdownMenuItem(
                                text = { Text("Add to ${grp.title}", color = QuettaColors.TextPrimary, fontSize = 13.sp) },
                                onClick = {
                                    onAssignGroup(grp.id)
                                    showGroupMenu = false
                                }
                            )
                        }
                        if (tab.groupId != null) {
                            DropdownMenuItem(
                                text = { Text("Remove from group", color = QuettaColors.Coral, fontSize = 13.sp) },
                                onClick = {
                                    onAssignGroup(null)
                                    showGroupMenu = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close tab",
                tint = QuettaColors.TextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onClose)
            )
        }

        // Card Preview Thumbnail
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(if (tab.isPrivate) Color(0xFF130E1D) else QuettaColors.BgDark),
            contentAlignment = Alignment.Center
        ) {
            if (!tab.isHomePage && tab.thumbnail != null) {
                Image(
                    bitmap = tab.thumbnail.asImageBitmap(),
                    contentDescription = tab.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = if (tab.isHomePage) "JARVIS" else BrowserAdBlockEngine.extractHost(tab.url).ifBlank { "PAGE" },
                        color = if (tab.isPrivate) QuettaColors.PrivateAccent else QuettaColors.Coral,
                        fontSize = if (tab.isHomePage) 18.sp else 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (tab.isHomePage) "Speed Dial & Search" else tab.url,
                        color = QuettaColors.TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TabGroupGridCard(
    group: TabGroupRecord,
    groupTabs: List<QuettaTab>,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.84f)
            .clip(RoundedCornerShape(16.dp))
            .background(QuettaColors.Surface)
            .border(1.dp, Color(group.colorHex).copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = group.title,
                color = QuettaColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete group",
                tint = QuettaColors.TextSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onDelete)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2x2 collage preview matching video `00:53`
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (rowIdx in 0..1) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (colIdx in 0..1) {
                        val tabIdx = rowIdx * 2 + colIdx
                        val previewTab = groupTabs.getOrNull(tabIdx)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(QuettaColors.BgDark)
                                .border(0.5.dp, QuettaColors.CardBorder, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (previewTab?.thumbnail != null && !previewTab.isHomePage) {
                                Image(
                                    bitmap = previewTab.thumbnail.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (previewTab != null) {
                                Text(
                                    text = previewTab.title.take(8),
                                    color = QuettaColors.TextSecondary,
                                    fontSize = 9.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${groupTabs.size} tabs • ${BrowserDataStore.formatRelativeTime(group.updatedAt)}",
            color = QuettaColors.TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
