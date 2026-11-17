package com.jarvisai.assistant.ui.screens.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.browser.BrowserAdBlockEngine
import com.jarvisai.assistant.browser.BrowserDataStore
import com.jarvisai.assistant.browser.BrowserSearchEngine
import com.jarvisai.assistant.browser.HistoryEntry
import com.jarvisai.assistant.browser.SpeedDialItem

@Composable
fun JarvisStartPage(
    isPrivate: Boolean,
    speedDials: List<SpeedDialItem>,
    onExitBrowser: () -> Unit,
    onClickSpeedDial: (SpeedDialItem) -> Unit,
    onDeleteSpeedDial: (String) -> Unit,
    onAddSpeedDialClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editMode by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isPrivate) Color(0xFF0E0C14) else JarvisBrowserColors.BgDark)
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top minimal header: Back to Jarvis pill + Speed Dial Edit Pencil
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(JarvisBrowserColors.Surface)
                    .clickable(onClick = onExitBrowser)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("browser_exit_home_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Jarvis", tint = JarvisBrowserColors.TextSecondary, modifier = Modifier.size(16.dp))
                Text("Jarvis", color = JarvisBrowserColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            IconButton(onClick = { editMode = !editMode }) {
                Icon(
                    imageVector = if (editMode) Icons.Default.Done else Icons.Default.Edit,
                    contentDescription = "Edit shortcuts",
                    tint = if (editMode) JarvisBrowserColors.Coral else JarvisBrowserColors.TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(44.dp))

        // Jarvis-style signature brand wordmark (`00:00`, `02:10`)
        if (isPrivate) {
            Icon(
                imageVector = Icons.Default.VisibilityOff,
                contentDescription = "Private Browsing",
                tint = JarvisBrowserColors.PrivateAccent,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = if (isPrivate) "JARVIS PRIVATE" else "JARVIS",
            color = if (isPrivate) JarvisBrowserColors.PrivateAccent else JarvisBrowserColors.Coral,
            fontSize = if (isPrivate) 24.sp else 32.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp
        )
        if (isPrivate) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "History & cookies won't be saved after closing",
                color = JarvisBrowserColors.TextSecondary,
                fontSize = 11.5.sp
            )
        }

        Spacer(modifier = Modifier.height(34.dp))

        // 4-Column Speed Dial Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(speedDials, key = { it.id }) { dial ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (editMode) onDeleteSpeedDial(dial.id) else onClickSpeedDial(dial)
                            }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(JarvisBrowserColors.Surface)
                                .border(1.dp, JarvisBrowserColors.CardBorder, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dial.badgeLetter,
                                color = Color(dial.accentHex),
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = dial.title,
                            color = JarvisBrowserColors.TextPrimary,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (editMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(JarvisBrowserColors.Coral)
                                .clickable { onDeleteSpeedDial(dial.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove shortcut", tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }

            // + Add Tile
            item(key = "add_speed_dial_tile") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onAddSpeedDialClick)
                        .padding(vertical = 4.dp)
                        .testTag("speed_dial_add_btn"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(JarvisBrowserColors.Surface)
                            .border(1.dp, JarvisBrowserColors.CardBorder, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Shortcut", tint = JarvisBrowserColors.TextSecondary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Add", color = JarvisBrowserColors.TextSecondary, fontSize = 11.5.sp)
                }
            }
        }
    }
}

@Composable
fun JarvisBottomOmniboxDock(
    activeTab: JarvisTab,
    tabCount: Int,
    isLoading: Boolean,
    loadProgress: Int,
    groupTabs: List<JarvisTab>,
    onHomeClick: () -> Unit,
    onOmniboxTap: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    onSelectGroupTab: (String) -> Unit,
    onAddTabToGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (activeTab.isPrivate) Color(0xFF14101D) else JarvisBrowserColors.Surface)
    ) {
        // Thin Coral Loading Bar right above Omnibox (`00:18`)
        if (isLoading || loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = { (loadProgress.coerceIn(4, 100)) / 100f },
                modifier = Modifier.fillMaxWidth().height(2.5.dp),
                color = if (activeTab.isPrivate) JarvisBrowserColors.PrivateAccent else JarvisBrowserColors.Coral,
                trackColor = JarvisBrowserColors.Surface
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(0.75.dp).background(JarvisBrowserColors.CardBorder))
        }

        // Tab Group Quick Strip right above omnibox (`00:43–00:48`)
        if (groupTabs.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JarvisBrowserColors.BgDark)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                groupTabs.forEach { gTab ->
                    val selected = gTab.id == activeTab.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) JarvisBrowserColors.CoralSoft else JarvisBrowserColors.Surface)
                            .border(
                                1.dp,
                                if (selected) JarvisBrowserColors.Coral else JarvisBrowserColors.CardBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectGroupTab(gTab.id) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (gTab.isHomePage) "Start" else gTab.title.take(14),
                            color = if (selected) JarvisBrowserColors.Coral else JarvisBrowserColors.TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
                IconButton(onClick = onAddTabToGroup, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Add tab to group", tint = JarvisBrowserColors.Coral, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Bottom Jarvis Bar: [ Home | Search/Domain Pill | [N] Tabs | Menu ]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onHomeClick,
                modifier = Modifier.size(42.dp).testTag("browser_home_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Start Page",
                    tint = JarvisBrowserColors.TextPrimary,
                    modifier = Modifier.size(21.dp)
                )
            }

            // Address Pill
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(JarvisBrowserColors.SurfaceElevated)
                    .border(
                        1.dp,
                        if (activeTab.isPrivate) JarvisBrowserColors.PrivateAccent.copy(alpha = 0.5f) else JarvisBrowserColors.CardBorder,
                        RoundedCornerShape(999.dp)
                    )
                    .clickable(onClick = onOmniboxTap)
                    .padding(horizontal = 12.dp)
                    .testTag("browser_url_input"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (activeTab.isPrivate) Icons.Default.VisibilityOff else Icons.Default.Shield,
                    contentDescription = "Privacy Shield",
                    tint = if (activeTab.isPrivate) JarvisBrowserColors.PrivateAccent else if (activeTab.isHomePage) JarvisBrowserColors.TextSecondary else JarvisBrowserColors.GreenSecure,
                    modifier = Modifier.size(16.dp)
                )
                val displayText = when {
                    activeTab.isHomePage -> "Search or type URL"
                    activeTab.isExtensionPage -> activeTab.title.ifBlank { "Extension" }
                    else -> BrowserAdBlockEngine.extractHost(activeTab.url).ifBlank { activeTab.url }
                }
                Text(
                    text = displayText,
                    color = if (activeTab.isHomePage) JarvisBrowserColors.TextSecondary else JarvisBrowserColors.TextPrimary,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Tab Counter Button `[ N ]`
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onTabsClick)
                    .testTag("browser_tabs_btn"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(21.dp)
                        .border(
                            1.8.dp,
                            if (activeTab.isPrivate) JarvisBrowserColors.PrivateAccent else JarvisBrowserColors.TextPrimary,
                            RoundedCornerShape(5.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabCount.toString(),
                        color = if (activeTab.isPrivate) JarvisBrowserColors.PrivateAccent else JarvisBrowserColors.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Jarvis Menu Button
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(42.dp).testTag("browser_menu_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Browser Menu",
                    tint = JarvisBrowserColors.TextPrimary,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

@Composable
fun JarvisFocusedOmniboxOverlay(
    initialQuery: String,
    searchEngine: BrowserSearchEngine,
    history: List<HistoryEntry>,
    speedDials: List<SpeedDialItem>,
    onSelectSearchEngine: (BrowserSearchEngine) -> Unit,
    onSubmitUrlOrSearch: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var engineMenuOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val suggestions = remember(query, history, speedDials) {
        val q = query.trim().lowercase()
        val fromHistory = history.filter {
            q.isEmpty() || it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
        }.take(8)
        if (fromHistory.isNotEmpty() || q.isNotEmpty()) {
            fromHistory
        } else {
            speedDials.filter { !it.url.startsWith("jarvis://") }.map {
                HistoryEntry(id = it.id, title = it.title, url = it.url, domain = BrowserAdBlockEngine.extractHost(it.url))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBrowserColors.BgDark.copy(alpha = 0.96f))
            .statusBarsPadding()
            .clickable(onClick = onDismiss)
            .testTag("browser_omnibox_overlay")
    ) {
        // Suggestions list filling the upper area above the bottom input (`00:05–00:16`)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(enabled = false, onClick = {}),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(suggestions, key = { it.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSubmitUrlOrSearch(item.url) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = JarvisBrowserColors.TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, color = JarvisBrowserColors.TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.url, color = JarvisBrowserColors.Coral, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { query = item.url }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.NorthWest, contentDescription = "Fill into search bar", tint = JarvisBrowserColors.TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Bottom Search Bar with Search Engine Selector Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(JarvisBrowserColors.Surface)
                .border(1.dp, JarvisBrowserColors.CardBorder)
                .clickable(enabled = false, onClick = {})
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(searchEngine.badgeColorHex))
                        .clickable { engineMenuOpen = true }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("omnibox_engine_selector"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(searchEngine.shortBadge, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }

                DropdownMenu(
                    expanded = engineMenuOpen,
                    onDismissRequest = { engineMenuOpen = false },
                    containerColor = JarvisBrowserColors.SurfaceElevated
                ) {
                    BrowserSearchEngine.entries.forEach { eng ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        modifier = Modifier.size(22.dp).clip(CircleShape).background(Color(eng.badgeColorHex)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(eng.shortBadge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(eng.displayName, color = JarvisBrowserColors.TextPrimary, fontSize = 13.5.sp)
                                }
                            },
                            trailingIcon = {
                                if (eng == searchEngine) {
                                    Icon(Icons.Default.Check, null, tint = JarvisBrowserColors.Coral, modifier = Modifier.size(16.dp))
                                }
                            },
                            onClick = {
                                onSelectSearchEngine(eng)
                                engineMenuOpen = false
                            }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(JarvisBrowserColors.SurfaceElevated)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text("Search with ${searchEngine.displayName} or type URL", color = JarvisBrowserColors.TextSecondary, fontSize = 13.sp, maxLines = 1)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = JarvisBrowserColors.TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(JarvisBrowserColors.Coral),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        if (query.isNotBlank()) onSubmitUrlOrSearch(query)
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )
            }

            if (query.isNotEmpty()) {
                IconButton(onClick = { query = "" }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Clear, "Clear", tint = JarvisBrowserColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(JarvisBrowserColors.Coral)
                    .clickable {
                        if (query.isNotBlank()) onSubmitUrlOrSearch(query) else onDismiss()
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
                    .testTag("browser_go_btn")
            ) {
                Text(if (query.isNotBlank()) "Go" else "Cancel", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ChromeWebStoreInstallBanner(
    extensionId: String,
    isAlreadyInstalled: Boolean,
    isInstalling: Boolean,
    onInstallClick: () -> Unit,
    onOpenExtensions: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(JarvisBrowserColors.SurfaceElevated)
            .border(1.dp, JarvisBrowserColors.Coral.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .testTag("webstore_install_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Extension, null, tint = JarvisBrowserColors.Coral, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isAlreadyInstalled) "Extension Installed" else "Chrome Extension Detected",
                color = JarvisBrowserColors.TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "ID: ${extensionId.take(12)}...",
                color = JarvisBrowserColors.TextSecondary,
                fontSize = 11.sp
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (isAlreadyInstalled) JarvisBrowserColors.GreenSecure else JarvisBrowserColors.Coral)
                .clickable(enabled = !isInstalling) {
                    if (isAlreadyInstalled) onOpenExtensions() else onInstallClick()
                }
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text(
                text = when {
                    isInstalling -> "Installing..."
                    isAlreadyInstalled -> "Manage"
                    else -> "Add to Jarvis"
                },
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun FindInPageBar(
    query: String,
    matchSummary: String,
    onQueryChange: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(JarvisBrowserColors.SurfaceElevated)
            .border(1.dp, JarvisBrowserColors.CardBorder)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(JarvisBrowserColors.BgDark)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (query.isEmpty()) {
                Text("Find in page...", color = JarvisBrowserColors.TextSecondary, fontSize = 13.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = JarvisBrowserColors.TextPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(JarvisBrowserColors.Coral),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (matchSummary.isNotEmpty()) {
            Text(matchSummary, color = JarvisBrowserColors.TextSecondary, fontSize = 11.5.sp)
        }
        IconButton(onClick = onPrev, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, "Previous match", tint = JarvisBrowserColors.TextPrimary)
        }
        IconButton(onClick = onNext, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, "Next match", tint = JarvisBrowserColors.TextPrimary)
        }
        IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.Close, "Close find", tint = JarvisBrowserColors.TextPrimary)
        }
    }
}

@Composable
fun OpenInExternalAppDialog(
    targetUrl: String,
    onStay: () -> Unit,
    onOpenApp: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onStay,
        containerColor = JarvisBrowserColors.Surface,
        title = { Text("Open This Page in App?", color = JarvisBrowserColors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("This link wants to open an external application:", color = JarvisBrowserColors.TextSecondary, fontSize = 13.sp)
                Text(targetUrl.take(120), color = JarvisBrowserColors.Coral, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenApp, modifier = Modifier.testTag("browser_open_external_btn")) {
                Text("Open in App", color = JarvisBrowserColors.Coral, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onStay) {
                Text("Stay in Browser", color = JarvisBrowserColors.TextPrimary)
            }
        }
    )
}

@Composable
fun AddSpeedDialDialog(
    defaultTitle: String,
    defaultUrl: String,
    onConfirm: (title: String, url: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(defaultTitle) { mutableStateOf(defaultTitle) }
    var url by remember(defaultUrl) { mutableStateOf(defaultUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisBrowserColors.Surface,
        title = { Text("Add to Speed Dial", color = JarvisBrowserColors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title", color = JarvisBrowserColors.TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisBrowserColors.Coral,
                        unfocusedBorderColor = JarvisBrowserColors.CardBorder,
                        focusedTextColor = JarvisBrowserColors.TextPrimary,
                        unfocusedTextColor = JarvisBrowserColors.TextPrimary
                    )
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Website URL", color = JarvisBrowserColors.TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisBrowserColors.Coral,
                        unfocusedBorderColor = JarvisBrowserColors.CardBorder,
                        focusedTextColor = JarvisBrowserColors.TextPrimary,
                        unfocusedTextColor = JarvisBrowserColors.TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (url.isNotBlank()) onConfirm(title.trim(), url.trim())
            }) {
                Text("Save", color = JarvisBrowserColors.Coral, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = JarvisBrowserColors.TextSecondary)
            }
        }
    )
}
