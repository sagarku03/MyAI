package com.jarvisai.assistant.ui.screens.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jarvisai.assistant.browser.BrowserAdBlockEngine
import com.jarvisai.assistant.browser.BrowserDataStore
import com.jarvisai.assistant.browser.BrowserSearchEngine
import com.jarvisai.assistant.browser.NativeWebPageSnapshot
import com.jarvisai.assistant.browser.PlaylistMediaItem

@Composable
fun JarvisBottomMenuSheet(
    activeTab: JarvisTab,
    isBookmarked: Boolean,
    onDismiss: () -> Unit,
    onOpenLibrary: (LibraryTabType) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onFindInPage: () -> Unit,
    onShowReaderMode: () -> Unit,
    onTranslatePage: () -> Unit,
    onAddToSpeedDial: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleDevTools: () -> Unit,
    onSummarizePage: () -> Unit,
    onReadAloud: () -> Unit,
    onCopyEmailMask: (String) -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onReload: () -> Unit,
    onTogglePrivateTab: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val prefs by BrowserDataStore.preferences.collectAsState()
    val domain = remember(activeTab.url, activeTab.isHomePage) {
        if (activeTab.isHomePage) "Start Page" else BrowserAdBlockEngine.extractHost(activeTab.url).ifBlank { "Web Page" }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(onClick = onDismiss)
            .testTag("browser_menu_sheet")
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(JarvisBrowserColors.BgDark)
                .border(1.dp, JarvisBrowserColors.CardBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(enabled = false, onClick = {})
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Top 4 Quick Library Tiles: Favorites | History | Downloads | Playlist
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MenuQuickTile(Icons.Default.Favorite, "Favorites", JarvisBrowserColors.Coral, Modifier.weight(1f)) {
                    onOpenLibrary(LibraryTabType.FAVORITES)
                }
                MenuQuickTile(Icons.Default.History, "History", JarvisBrowserColors.SkyBlue, Modifier.weight(1f)) {
                    onOpenLibrary(LibraryTabType.HISTORY)
                }
                MenuQuickTile(Icons.Default.Download, "Downloads", JarvisBrowserColors.GreenSecure, Modifier.weight(1f)) {
                    onOpenLibrary(LibraryTabType.DOWNLOADS)
                }
                MenuQuickTile(Icons.Default.PlaylistPlay, "Playlist", JarvisBrowserColors.Gold, Modifier.weight(1f)) {
                    onOpenLibrary(LibraryTabType.PLAYLIST)
                }
            }

            // 2. Jarvis AI Smart Actions Row (Cleanly placed in Menu instead of cluttering viewport)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(JarvisBrowserColors.CoralSoft)
                        .border(1.dp, JarvisBrowserColors.Coral.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .clickable { onDismiss(); onSummarizePage() }
                        .padding(vertical = 11.dp, horizontal = 10.dp)
                        .testTag("browser_ai_summarize_btn"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = JarvisBrowserColors.Coral, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("AI Summary", color = JarvisBrowserColors.TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(JarvisBrowserColors.Surface)
                        .border(1.dp, JarvisBrowserColors.CardBorder, RoundedCornerShape(14.dp))
                        .clickable { onDismiss(); onReadAloud() }
                        .padding(vertical = 11.dp, horizontal = 10.dp)
                        .testTag("browser_read_aloud_btn"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = JarvisBrowserColors.SkyBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Read Aloud", color = JarvisBrowserColors.TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 3. Privacy, Email Mask, Settings, Extensions Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(JarvisBrowserColors.Surface)
            ) {
                MenuActionRow(
                    icon = Icons.Default.Shield,
                    iconTint = if (prefs.adBlockEnabled) JarvisBrowserColors.GreenSecure else JarvisBrowserColors.TextSecondary,
                    title = "Privacy Shield (${activeTab.blockedCount} blocked)",
                    trailingText = if (prefs.adBlockEnabled) "ON" else "OFF",
                    onClick = { BrowserDataStore.updatePreferences { it.copy(adBlockEnabled = !it.adBlockEnabled) } }
                )
                HorizontalDivider(color = JarvisBrowserColors.CardBorder, thickness = 0.5.dp)
                MenuActionRow(
                    icon = Icons.Default.MailOutline,
                    iconTint = JarvisBrowserColors.PrivateAccent,
                    title = "Email Mask",
                    trailingText = "Copy",
                    onClick = {
                        clipboard.setText(AnnotatedString(prefs.emailMaskAlias))
                        onCopyEmailMask(prefs.emailMaskAlias)
                    }
                )
                HorizontalDivider(color = JarvisBrowserColors.CardBorder, thickness = 0.5.dp)
                MenuActionRow(
                    icon = Icons.Default.Extension,
                    iconTint = JarvisBrowserColors.Coral,
                    title = "Extensions",
                    trailingText = "Manage",
                    onClick = onOpenExtensions
                )
                HorizontalDivider(color = JarvisBrowserColors.CardBorder, thickness = 0.5.dp)
                MenuActionRow(
                    icon = Icons.Default.Settings,
                    iconTint = JarvisBrowserColors.TextPrimary,
                    title = "Browser Settings",
                    onClick = onOpenSettings
                )
            }

            // 4. Current Domain Actions Card (`00:25`, `01:15`)
            if (!activeTab.isHomePage) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(JarvisBrowserColors.Surface)
                ) {
                    // Domain header with bookmark & share
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = domain,
                            color = JarvisBrowserColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = onToggleBookmark, modifier = Modifier.size(34.dp)) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Bookmark Page",
                                tint = if (isBookmarked) JarvisBrowserColors.Coral else JarvisBrowserColors.TextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                runCatching {
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, activeTab.title)
                                        putExtra(Intent.EXTRA_TEXT, activeTab.url)
                                    }
                                    context.startActivity(Intent.createChooser(share, "Share page"))
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = JarvisBrowserColors.TextPrimary, modifier = Modifier.size(18.dp))
                        }
                    }

                    HorizontalDivider(color = JarvisBrowserColors.CardBorder, thickness = 0.5.dp)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) {
                            MenuActionRow(Icons.Default.Translate, JarvisBrowserColors.SkyBlue, "Translate", onClick = onTranslatePage)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            MenuActionRow(Icons.Default.MenuBook, JarvisBrowserColors.Gold, "Reader", onClick = onShowReaderMode)
                        }
                    }
                    HorizontalDivider(color = JarvisBrowserColors.CardBorder, thickness = 0.5.dp)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) {
                            MenuActionRow(Icons.Default.FindInPage, JarvisBrowserColors.TextPrimary, "Find in Page", onClick = onFindInPage)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            MenuActionRow(
                                icon = if (activeTab.desktopMode) Icons.Default.PhoneAndroid else Icons.Default.DesktopWindows,
                                iconTint = if (activeTab.desktopMode) JarvisBrowserColors.Coral else JarvisBrowserColors.TextPrimary,
                                title = if (activeTab.desktopMode) "Mobile Site" else "Desktop Site",
                                onClick = onToggleDesktopMode,
                                modifier = Modifier.testTag("browser_desktop_mode_btn")
                            )
                        }
                    }
                    HorizontalDivider(color = JarvisBrowserColors.CardBorder, thickness = 0.5.dp)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) {
                            MenuActionRow(Icons.Default.PlaylistAdd, JarvisBrowserColors.GreenSecure, "Watch Later", onClick = onAddToPlaylist)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            MenuActionRow(Icons.Default.Add, JarvisBrowserColors.Coral, "Speed Dial", onClick = onAddToSpeedDial)
                        }
                    }
                    HorizontalDivider(color = JarvisBrowserColors.CardBorder, thickness = 0.5.dp)
                    MenuActionRow(Icons.Default.DeveloperMode, JarvisBrowserColors.SkyBlue, "Developer Tools (Eruda Console)", onClick = onToggleDevTools)
                }
            }

            // 5. Bottom Navigation Footer: ‹ Back | › Forward | 🧩 Extensions | ⟳ Reload | Privacy | × Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(JarvisBrowserColors.Surface)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onGoBack(); onDismiss() }, enabled = activeTab.canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (activeTab.canGoBack) JarvisBrowserColors.TextPrimary else JarvisBrowserColors.TextSecondary.copy(alpha = 0.4f))
                }
                IconButton(onClick = { onGoForward(); onDismiss() }, enabled = activeTab.canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward", tint = if (activeTab.canGoForward) JarvisBrowserColors.TextPrimary else JarvisBrowserColors.TextSecondary.copy(alpha = 0.4f))
                }
                IconButton(onClick = onOpenExtensions, modifier = Modifier.testTag("menu_footer_extensions_btn")) {
                    Icon(Icons.Default.Extension, "Extensions", tint = JarvisBrowserColors.Coral)
                }
                IconButton(onClick = { onReload(); onDismiss() }) {
                    Icon(Icons.Default.Refresh, "Reload", tint = JarvisBrowserColors.TextPrimary)
                }
                IconButton(onClick = { onTogglePrivateTab(); onDismiss() }) {
                    Icon(Icons.Default.VisibilityOff, "Private Mode", tint = if (activeTab.isPrivate) JarvisBrowserColors.PrivateAccent else JarvisBrowserColors.TextPrimary)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close Menu", tint = JarvisBrowserColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun MenuQuickTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(JarvisBrowserColors.Surface)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Text(text = label, color = JarvisBrowserColors.TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MenuActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    trailingText: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = title, color = JarvisBrowserColors.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!trailingText.isNullOrBlank()) {
            Text(text = trailingText, color = JarvisBrowserColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun JarvisLibraryScreen(
    initialTab: LibraryTabType,
    activeTab: JarvisTab,
    onOpenUrl: (String) -> Unit,
    onSaveOfflinePage: () -> Unit,
    onPlayMediaItem: (PlaylistMediaItem) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var currentTab by remember(initialTab) { mutableStateOf(initialTab) }
    var search by remember { mutableStateOf("") }
    var currentFolder by remember { mutableStateOf<String?>(null) }

    val bookmarks by BrowserDataStore.bookmarks.collectAsState()
    val folders by BrowserDataStore.folders.collectAsState()
    val history by BrowserDataStore.history.collectAsState()
    val downloads by BrowserDataStore.downloads.collectAsState()
    val playlist by BrowserDataStore.playlist.collectAsState()

    LaunchedEffect(currentTab) {
        if (currentTab == LibraryTabType.DOWNLOADS) {
            BrowserDataStore.refreshDownloadsFromStorage(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBrowserColors.BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("browser_library_sheet")
    ) {
        // Top 4-Tab Pill Row matching `01:19 - 01:33`
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(JarvisBrowserColors.Surface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LibraryTabType.entries.forEach { tab ->
                    val selected = tab == currentTab
                    val icon = when (tab) {
                        LibraryTabType.FAVORITES -> Icons.Default.Favorite
                        LibraryTabType.HISTORY -> Icons.Default.History
                        LibraryTabType.DOWNLOADS -> Icons.Default.Download
                        LibraryTabType.PLAYLIST -> Icons.Default.PlaylistPlay
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) JarvisBrowserColors.Coral else Color.Transparent)
                            .clickable {
                                currentTab = tab
                                currentFolder = null
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.label,
                            tint = if (selected) Color.White else JarvisBrowserColors.TextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close Library", tint = JarvisBrowserColors.TextPrimary)
            }
        }

        // Search Box
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search ${currentTab.label.lowercase()}...", color = JarvisBrowserColors.TextSecondary, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = JarvisBrowserColors.TextSecondary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisBrowserColors.Coral,
                unfocusedBorderColor = JarvisBrowserColors.CardBorder,
                focusedContainerColor = JarvisBrowserColors.Surface,
                unfocusedContainerColor = JarvisBrowserColors.Surface,
                focusedTextColor = JarvisBrowserColors.TextPrimary,
                unfocusedTextColor = JarvisBrowserColors.TextPrimary
            )
        )

        val q = search.trim().lowercase()

        when (currentTab) {
            LibraryTabType.FAVORITES -> {
                if (currentFolder != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { currentFolder = null }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = JarvisBrowserColors.Coral, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(currentFolder!!, color = JarvisBrowserColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                val visibleBookmarks = bookmarks.filter {
                    val matchesFolder = currentFolder == null || it.folder == currentFolder
                    val matchesQ = q.isEmpty() || it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
                    matchesFolder && matchesQ
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentFolder == null && q.isEmpty()) {
                        items(folders) { folderName ->
                            val count = bookmarks.count { it.folder == folderName }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(JarvisBrowserColors.Surface)
                                    .clickable { currentFolder = folderName }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, null, tint = JarvisBrowserColors.Gold, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(folderName, color = JarvisBrowserColors.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("$count", color = JarvisBrowserColors.TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                    items(visibleBookmarks, key = { it.id }) { bm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(JarvisBrowserColors.Surface)
                                .clickable { onOpenUrl(bm.url) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Bookmark, null, tint = JarvisBrowserColors.Coral, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bm.title, color = JarvisBrowserColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(bm.url, color = JarvisBrowserColors.TextSecondary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { BrowserDataStore.removeBookmark(bm.id) }) {
                                Icon(Icons.Default.Delete, "Delete bookmark", tint = JarvisBrowserColors.TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            LibraryTabType.HISTORY -> {
                val filteredHistory = history.filter {
                    q.isEmpty() || it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
                }
                val grouped = filteredHistory.groupBy { BrowserDataStore.formatHistoryDayLabel(it.visitedAt) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${filteredHistory.size} entries", color = JarvisBrowserColors.TextSecondary, fontSize = 12.sp)
                    if (history.isNotEmpty()) {
                        Text(
                            text = "Clear History",
                            color = JarvisBrowserColors.Coral,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { BrowserDataStore.clearHistory() }
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    grouped.forEach { (dayLabel, itemsInDay) ->
                        item(key = "hdr_$dayLabel") {
                            Text(dayLabel, color = JarvisBrowserColors.Coral, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        items(itemsInDay, key = { it.id }) { h ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(JarvisBrowserColors.Surface)
                                    .clickable { onOpenUrl(h.url) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(h.title, color = JarvisBrowserColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(h.domain.ifBlank { h.url }, color = JarvisBrowserColors.TextSecondary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { BrowserDataStore.removeHistoryItem(h.id) }) {
                                    Icon(Icons.Default.Close, "Remove entry", tint = JarvisBrowserColors.TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            LibraryTabType.DOWNLOADS -> {
                if (!activeTab.isHomePage) {
                    val host = BrowserAdBlockEngine.extractHost(activeTab.url).ifBlank { "page" }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(JarvisBrowserColors.CoralSoft)
                            .border(1.dp, JarvisBrowserColors.Coral.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .clickable(onClick = onSaveOfflinePage)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Download, null, tint = JarvisBrowserColors.Coral, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save $host as offline page", color = JarvisBrowserColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                val filteredDownloads = downloads.filter {
                    q.isEmpty() || it.fileName.lowercase().contains(q) || it.url.lowercase().contains(q)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Folder: Download/${BrowserDataStore.AGENTRA_DOWNLOADS_FOLDER}",
                        color = JarvisBrowserColors.TextSecondary,
                        fontSize = 11.5.sp
                    )
                    Text(
                        text = "${filteredDownloads.size} item${if (filteredDownloads.size == 1) "" else "s"}",
                        color = JarvisBrowserColors.Coral,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (filteredDownloads.isEmpty()) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Download, null, tint = JarvisBrowserColors.GreenSecure, modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No Downloads Yet", color = JarvisBrowserColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Downloaded files save automatically to Download/${BrowserDataStore.AGENTRA_DOWNLOADS_FOLDER} and appear here.",
                            color = JarvisBrowserColors.TextSecondary,
                            fontSize = 12.5.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredDownloads, key = { it.id }) { dl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(JarvisBrowserColors.Surface)
                                    .clickable {
                                        if (dl.isOfflinePage && dl.localPath.isNotBlank()) {
                                            onOpenUrl("file://${dl.localPath}")
                                        } else if (!BrowserDataStore.openDownloadEntry(context, dl) && dl.url.startsWith("http")) {
                                            onOpenUrl(dl.url)
                                        }
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Download, null, tint = JarvisBrowserColors.GreenSecure, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(dl.fileName, color = JarvisBrowserColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(dl.sizeLabel.ifBlank { dl.mimeType }, color = JarvisBrowserColors.TextSecondary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { BrowserDataStore.removeDownloadEntry(dl.id) }) {
                                    Icon(Icons.Default.Delete, "Delete download", tint = JarvisBrowserColors.TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            LibraryTabType.PLAYLIST -> {
                val filteredPlaylist = playlist.filter {
                    q.isEmpty() || it.title.lowercase().contains(q) || it.domain.lowercase().contains(q)
                }
                if (filteredPlaylist.isEmpty()) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.PlaylistPlay, null, tint = JarvisBrowserColors.Gold, modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Watch Later Playlist Empty", color = JarvisBrowserColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Use Menu -> Watch Later on any YouTube or media page to queue videos.", color = JarvisBrowserColors.TextSecondary, fontSize = 12.5.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredPlaylist, key = { it.id }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(JarvisBrowserColors.Surface)
                                    .clickable { onPlayMediaItem(item) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(JarvisBrowserColors.CoralSoft),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, tint = JarvisBrowserColors.Coral, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, color = JarvisBrowserColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(item.domain, color = JarvisBrowserColors.TextSecondary, fontSize = 11.5.sp)
                                }
                                IconButton(onClick = { BrowserDataStore.removeFromPlaylist(item.id) }) {
                                    Icon(Icons.Default.Delete, "Remove from playlist", tint = JarvisBrowserColors.TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BrowserSettingsScreen(
    onBack: () -> Unit,
    onOpenDeveloperExtensions: () -> Unit
) {
    val prefs by BrowserDataStore.preferences.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBrowserColors.BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("browser_settings_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = JarvisBrowserColors.TextPrimary)
            }
            Text("Browser Settings", color = JarvisBrowserColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Search Engine", color = JarvisBrowserColors.Coral, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(JarvisBrowserColors.Surface)
            ) {
                BrowserSearchEngine.entries.forEachIndexed { idx, engine ->
                    val selected = engine == prefs.searchEngine
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { BrowserDataStore.updatePreferences { it.copy(searchEngine = engine) } }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(engine.badgeColorHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(engine.shortBadge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(engine.displayName, color = JarvisBrowserColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (selected) {
                            Text("Active", color = JarvisBrowserColors.Coral, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (idx < BrowserSearchEngine.entries.lastIndex) {
                        HorizontalDivider(color = JarvisBrowserColors.CardBorder, thickness = 0.5.dp)
                    }
                }
            }

            Text("Privacy & Safety", color = JarvisBrowserColors.Coral, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(JarvisBrowserColors.Surface)
            ) {
                SettingsToggleRow(
                    title = "Ad & Tracker Blocker (Privacy Shield)",
                    subtitle = "Blocks known advertising & analytics trackers",
                    checked = prefs.adBlockEnabled
                ) { v -> BrowserDataStore.updatePreferences { it.copy(adBlockEnabled = v) } }
                HorizontalDivider(color = JarvisBrowserColors.CardBorder, thickness = 0.5.dp)
                SettingsToggleRow(
                    title = "Ask before opening in external apps",
                    subtitle = "Prevents sudden app redirects without confirmation",
                    checked = prefs.askBeforeOpeningApp
                ) { v -> BrowserDataStore.updatePreferences { it.copy(askBeforeOpeningApp = v) } }
                HorizontalDivider(color = JarvisBrowserColors.CardBorder, thickness = 0.5.dp)
                SettingsToggleRow(
                    title = "Force Dark Pages",
                    subtitle = "Apply algorithmic dark theme on light sites",
                    checked = prefs.forceDarkPages
                ) { v -> BrowserDataStore.updatePreferences { it.copy(forceDarkPages = v) } }
                HorizontalDivider(color = JarvisBrowserColors.CardBorder, thickness = 0.5.dp)
                SettingsToggleRow(
                    title = "Desktop Site by Default",
                    subtitle = "Open new tabs in full 1280px Desktop Mode",
                    checked = prefs.desktopModeDefault
                ) { v -> BrowserDataStore.updatePreferences { it.copy(desktopModeDefault = v) } }
                HorizontalDivider(color = JarvisBrowserColors.CardBorder, thickness = 0.5.dp)
                SettingsToggleRow(
                    title = "Background & Lockscreen Playback",
                    subtitle = "Keep audio playing across screens, background & screen-off with media notification",
                    checked = prefs.backgroundMediaPlayback
                ) { v -> BrowserDataStore.updatePreferences { it.copy(backgroundMediaPlayback = v) } }
                HorizontalDivider(color = JarvisBrowserColors.CardBorder, thickness = 0.5.dp)
                SettingsToggleRow(
                    title = "Enable JavaScript",
                    subtitle = "Required for modern websites and extensions",
                    checked = prefs.javaScriptEnabled
                ) { v -> BrowserDataStore.updatePreferences { it.copy(javaScriptEnabled = v) } }
            }

            Text("Extensions", color = JarvisBrowserColors.Coral, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(JarvisBrowserColors.Surface)
                    .clickable(onClick = onOpenDeveloperExtensions)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Developer Extensions & Store Installer", color = JarvisBrowserColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = JarvisBrowserColors.Coral, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = JarvisBrowserColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = JarvisBrowserColors.TextSecondary, fontSize = 11.5.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = JarvisBrowserColors.Coral)
        )
    }
}

@Composable
fun ReaderModeOverlay(
    title: String,
    url: String,
    domText: String,
    snapshot: NativeWebPageSnapshot?,
    onReadAloud: (String) -> Unit,
    onClose: () -> Unit
) {
    val body = remember(domText, snapshot) {
        snapshot?.paragraphs?.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
            ?: domText.ifBlank { "No readable article text extracted for this page yet." }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF161618))
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("browser_reader_mode")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(JarvisBrowserColors.Surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.MenuBook, null, tint = JarvisBrowserColors.Gold, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reader View", color = JarvisBrowserColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { onReadAloud(body.take(800)) }) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, "Read Aloud", tint = JarvisBrowserColors.SkyBlue)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Exit Reader", tint = JarvisBrowserColors.TextPrimary)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title.ifBlank { "Article" }, color = JarvisBrowserColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp)
            Text(BrowserAdBlockEngine.extractHost(url), color = JarvisBrowserColors.Coral, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(color = JarvisBrowserColors.CardBorder)
            Text(body, color = Color(0xFFE4E4E7), fontSize = 16.sp, lineHeight = 26.sp)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlaylistMediaPlayerOverlay(
    item: PlaylistMediaItem,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(JarvisBrowserColors.Surface).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close player", tint = JarvisBrowserColors.TextPrimary)
            }
            Text(item.title, color = JarvisBrowserColors.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        com.jarvisai.assistant.browser.BrowserIdentity.applyTo(this, ctx, desktop = false)
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                            if (request == null) return
                            val drm = request.resources.filter {
                                it == android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
                            }
                            if (drm.isNotEmpty()) {
                                runCatching { request.grant(drm.toTypedArray()) }
                            } else {
                                runCatching { request.deny() }
                            }
                        }
                    }
                    webViewClient = WebViewClient()
                    loadUrl(item.url)
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
}

@Composable
fun TranslateLanguageDialog(
    currentUrl: String,
    onSelectLanguage: (langCode: String) -> Unit,
    onDismiss: () -> Unit
) {
    val languages = listOf(
        "hi" to "Hindi (हिन्दी)",
        "en" to "English",
        "es" to "Spanish (Español)",
        "fr" to "French (Français)",
        "de" to "German (Deutsch)",
        "ja" to "Japanese (日本語)",
        "ar" to "Arabic (العربية)"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisBrowserColors.Surface,
        title = { Text("Translate Page", color = JarvisBrowserColors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                languages.forEach { (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectLanguage(code) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name, color = JarvisBrowserColors.TextPrimary, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = JarvisBrowserColors.TextSecondary)
            }
        }
    )
}
