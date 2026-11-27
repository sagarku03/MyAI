package com.jarvisai.assistant.ui.screens.browser

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jarvisai.assistant.browser.BrowserDataStore
import com.jarvisai.assistant.browser.BrowserIdentity
import com.jarvisai.assistant.extension.BrowserExtension
import com.jarvisai.assistant.extension.BrowserExtensionManager
import com.jarvisai.assistant.extension.BrowserExtensionRuntimeBridge
import com.jarvisai.assistant.extension.CrxExtensionInstaller
import com.jarvisai.assistant.extension.ExtensionContextMenuItem
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

@Composable
fun ExtensionBadgeIcon(ext: BrowserExtension, sizeDp: Int = 36) {
    val bmp = remember(ext.iconPath) {
        if (ext.iconPath.isNotBlank() && File(ext.iconPath).exists()) {
            runCatching { BitmapFactory.decodeFile(ext.iconPath) }.getOrNull()
        } else null
    }
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (bmp == null) JarvisBrowserColors.Coral.copy(alpha = 0.2f) else JarvisBrowserColors.SurfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = ext.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().padding(3.dp)
            )
        } else {
            Text(
                text = ext.name.trim().take(2).uppercase().ifBlank { "EX" },
                color = JarvisBrowserColors.Coral,
                fontSize = (sizeDp * 0.36f).sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

fun summarizeExtensionPermissions(ext: BrowserExtension): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    val allHosts = (ext.hostPermissions + ext.contentScripts.flatMap { it.matches }).distinct()
    if (allHosts.isNotEmpty()) {
        val desc = if (allHosts.any { it == "<all_urls>" || it == "*://*/*" || it == "*" }) {
            "Read and change all your data on websites you visit"
        } else {
            "Read and change your data on ${allHosts.take(3).joinToString(", ")}" +
                if (allHosts.size > 3) " (+${allHosts.size - 3} more)" else ""
        }
        result.add("Site access" to desc)
    }
    val seen = mutableSetOf<String>()
    for (perm in ext.permissions) {
        if (perm.contains("://") || perm == "<all_urls>") continue
        val item = when (perm.lowercase()) {
            "tabs", "activetab" -> "Tabs & browsing" to "Read active tab URL, title, and manage browser tabs"
            "scripting", "userscripts" -> "Web page scripts" to "Modify page appearance and execute scripts on allowed sites"
            "declarativenetrequest", "declarativenetrequestwithhostaccess", "webrequest", "webrequestblocking" ->
                "Network filtering" to "Block trackers, ads, or redirect web requests"
            "storage", "unlimitedstorage" -> "Local storage" to "Save extension preferences and state on your device"
            "cookies" -> "Cookies" to "Read and update site cookies on permitted domains"
            "downloads" -> "Downloads" to "Start and monitor downloads"
            "notifications" -> "Notifications" to "Show status alerts and notifications"
            "contextmenus" -> "Context menus" to "Add quick actions to browser extension menus"
            "alarms" -> "Background schedule" to "Run background timers"
            "sidepanel" -> "Side panel UI" to "Display companion panels alongside pages"
            "offscreen" -> "Offscreen worker" to "Process background DOM or audio tasks"
            else -> perm to "Permission: $perm"
        }
        if (seen.add(item.first)) result.add(item)
    }
    if (result.isEmpty()) {
        result.add("No special permissions" to "Runs with standard sandboxed extension capabilities")
    }
    return result
}

@Composable
fun ExtensionInstallConfirmDialog(
    preview: CrxExtensionInstaller.StagedExtensionPreview,
    onConfirm: (pinToToolbar: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val ext = preview.extension
    var pinToToolbar by remember(ext.id) { mutableStateOf(true) }
    val permSummary = remember(ext.id, ext.permissions, ext.hostPermissions) {
        summarizeExtensionPermissions(ext)
    }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = JarvisBrowserColors.Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExtensionBadgeIcon(ext, sizeDp = 40)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (preview.isAlreadyInstalled) "Update \"${ext.name}\"?" else "Add \"${ext.name}\"?",
                        color = JarvisBrowserColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "v${ext.version} • MV${ext.manifestVersion} • ${preview.sourceType.uppercase()}",
                        color = JarvisBrowserColors.Coral,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (ext.description.isNotBlank()) {
                    Text(ext.description, color = JarvisBrowserColors.TextSecondary, fontSize = 12.5.sp)
                }
                Text(
                    text = "This extension can:",
                    color = JarvisBrowserColors.TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
                permSummary.forEach { (title, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(JarvisBrowserColors.BgDark)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = JarvisBrowserColors.SkyBlue, modifier = Modifier.size(16.dp))
                        Column {
                            Text(title, color = JarvisBrowserColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(desc, color = JarvisBrowserColors.TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { pinToToolbar = !pinToToolbar }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = pinToToolbar,
                        onCheckedChange = { pinToToolbar = it },
                        colors = CheckboxDefaults.colors(checkedColor = JarvisBrowserColors.Coral)
                    )
                    Text("Pin shortcut to browser toolbar", color = JarvisBrowserColors.TextPrimary, fontSize = 12.5.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pinToToolbar) },
                modifier = Modifier.testTag("ext_confirm_install_btn")
            ) {
                Text(
                    text = if (preview.isAlreadyInstalled) "Update extension" else "Add extension",
                    color = JarvisBrowserColors.Coral,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = JarvisBrowserColors.TextSecondary)
            }
        }
    )
}

@Composable
fun JarvisExtensionsQuickSheet(
    extensions: List<BrowserExtension>,
    onDismiss: () -> Unit,
    onLaunchExtension: (BrowserExtension) -> Unit,
    onOpenManage: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenWebStore: () -> Unit,
    onOpenInTab: (BrowserExtension) -> Unit = onLaunchExtension,
    onOpenDetails: (BrowserExtension) -> Unit = { onOpenManage() },
    onContextMenuClick: (ExtensionContextMenuItem) -> Unit = {}
) {
    val isGrid by BrowserExtensionManager.isQuickSheetGrid.collectAsState()
    val actionUiStates by BrowserExtensionManager.actionUiStates.collectAsState()
    val contextMenuItems by BrowserExtensionManager.contextMenuItems.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(onClick = onDismiss)
            .testTag("extensions_quick_sheet")
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(JarvisBrowserColors.Surface)
                .border(1.dp, JarvisBrowserColors.CardBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(enabled = false, onClick = {})
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Sheet Header matching 00:02 - 00:10
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Extension, contentDescription = null, tint = JarvisBrowserColors.Coral, modifier = Modifier.size(20.dp))
                    Text("Extensions", color = JarvisBrowserColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { BrowserExtensionManager.setQuickSheetGrid(!isGrid) }) {
                        Icon(
                            imageVector = if (isGrid) Icons.AutoMirrored.Filled.FormatListBulleted else Icons.Default.Apps,
                            contentDescription = "Toggle Grid/List",
                            tint = JarvisBrowserColors.TextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = JarvisBrowserColors.TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val enabledExts = remember(extensions) { extensions.filter { it.isEnabled } }
            val activeMenuItems = remember(contextMenuItems, enabledExts) {
                val enabledIds = enabledExts.map { it.id }.toSet()
                contextMenuItems.filter { it.extensionId in enabledIds }
            }
            if (activeMenuItems.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeMenuItems.forEach { item ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(JarvisBrowserColors.CoralSoft)
                                .border(1.dp, JarvisBrowserColors.Coral.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                                .clickable { onContextMenuClick(item) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(item.title, color = JarvisBrowserColors.Coral, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (enabledExts.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No active extensions", color = JarvisBrowserColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Install from Chrome Web Store or .crx / .zip / .user.js",
                        color = JarvisBrowserColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            } else if (isGrid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().height(210.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(enabledExts, key = { it.id }) { ext ->
                        val actionUi = actionUiStates[ext.id]
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onLaunchExtension(ext) }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box {
                                ExtensionBadgeIcon(ext, sizeDp = 42)
                                val badge = actionUi?.badgeText.orEmpty()
                                if (badge.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(actionUi?.badgeColorHex ?: 0xFFF25F70L))
                                            .padding(horizontal = 3.dp, vertical = 1.dp)
                                    ) {
                                        Text(badge, color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = actionUi?.title?.takeIf { it.isNotBlank() } ?: ext.name,
                                color = JarvisBrowserColors.TextPrimary,
                                fontSize = 11.sp,
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(230.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(enabledExts, key = { it.id }) { ext ->
                        val actionUi = actionUiStates[ext.id]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(JarvisBrowserColors.SurfaceElevated)
                                .clickable { onLaunchExtension(ext) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExtensionBadgeIcon(ext, sizeDp = 34)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = actionUi?.title?.takeIf { it.isNotBlank() } ?: ext.name,
                                    color = JarvisBrowserColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (ext.popupFile.isNotBlank() || ext.sidePanelPath.isNotBlank()) "Tap for popup • MV${ext.manifestVersion}" else "MV${ext.manifestVersion}",
                                    color = JarvisBrowserColors.TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            val badge = actionUi?.badgeText.orEmpty()
                            if (badge.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(actionUi?.badgeColorHex ?: 0xFFF25F70L))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(badge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            IconButton(
                                onClick = { BrowserExtensionManager.togglePinned(ext.id, !ext.pinned) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = if (ext.pinned) "Unpin" else "Pin to toolbar",
                                    tint = if (ext.pinned) JarvisBrowserColors.Coral else JarvisBrowserColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onOpenInTab(ext) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = "Open in Tab",
                                    tint = JarvisBrowserColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onOpenDetails(ext) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Extension Details",
                                    tint = JarvisBrowserColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer Bar matching 00:02 - 00:23: Manage Extensions | Store | Dev Gear
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(JarvisBrowserColors.SurfaceElevated)
                        .clickable(onClick = onOpenManage)
                        .padding(vertical = 12.dp)
                        .testTag("extensions_manage_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Manage Extensions", color = JarvisBrowserColors.Coral, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(JarvisBrowserColors.CoralSoft)
                        .border(1.dp, JarvisBrowserColors.Coral.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenWebStore)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .testTag("extensions_webstore_btn"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Default.Storefront, contentDescription = "Chrome Web Store", tint = JarvisBrowserColors.Coral, modifier = Modifier.size(16.dp))
                    Text("Store", color = JarvisBrowserColors.Coral, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onOpenDeveloper,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(JarvisBrowserColors.SurfaceElevated)
                        .testTag("extensions_dev_gear_btn")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Developer Extensions", tint = JarvisBrowserColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
fun ManageExtensionsScreen(
    extensions: List<BrowserExtension>,
    onBack: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onOpenPopup: (BrowserExtension) -> Unit,
    onShowDetails: (BrowserExtension) -> Unit,
    onOpenOptions: (BrowserExtension) -> Unit = onOpenPopup
) {
    var search by remember { mutableStateOf("") }
    var actionTargetExtId by remember { mutableStateOf<String?>(null) }

    val filtered = remember(extensions, search) {
        if (search.isBlank()) extensions else {
            val q = search.trim().lowercase()
            extensions.filter { it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
        }
    }

    val actionExt = remember(extensions, actionTargetExtId) {
        extensions.firstOrNull { it.id == actionTargetExtId }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBrowserColors.BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("manage_extensions_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with Beta Pill matching `00:25`
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = JarvisBrowserColors.TextPrimary)
                }
                Text("Manage Extensions", color = JarvisBrowserColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(JarvisBrowserColors.SurfaceElevated)
                        .border(1.dp, JarvisBrowserColors.CardBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Beta", color = JarvisBrowserColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search extensions", color = JarvisBrowserColors.TextSecondary, fontSize = 13.5.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = JarvisBrowserColors.TextSecondary) },
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

            Text(
                text = "INSTALLED",
                color = JarvisBrowserColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { ext ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(JarvisBrowserColors.Surface)
                            .clickable {
                                if (ext.isEnabled) {
                                    onOpenPopup(ext)
                                } else {
                                    actionTargetExtId = ext.id
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExtensionBadgeIcon(ext, sizeDp = 36)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ext.name,
                                color = JarvisBrowserColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (ext.isEnabled) "Tap to open extension UI" else "Disabled • Tap to enable",
                                color = JarvisBrowserColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(JarvisBrowserColors.SurfaceElevated)
                                .clickable { actionTargetExtId = ext.id }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (ext.isEnabled) "On" else "Off",
                                color = if (ext.isEnabled) JarvisBrowserColors.Coral else JarvisBrowserColors.TextSecondary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = "Extension options",
                                tint = JarvisBrowserColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Bottom "Developer options..." matching `00:27`
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JarvisBrowserColors.Surface)
                    .border(1.dp, JarvisBrowserColors.CardBorder)
                    .clickable(onClick = onOpenDeveloperOptions)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .testTag("manage_extensions_developer_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = JarvisBrowserColors.Coral)
                    Text("Developer options...", color = JarvisBrowserColors.Coral, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("CRX / ZIP / UserScript", color = JarvisBrowserColors.TextSecondary, fontSize = 11.5.sp)
            }
        }

        // Bottom Action Drawer when tapping an extension row matching `00:29 - 00:33`
        if (actionExt != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { actionTargetExtId = null }
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(JarvisBrowserColors.BgDark)
                        .border(1.dp, JarvisBrowserColors.CardBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .clickable(enabled = false, onClick = {})
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = actionExt.name.uppercase(),
                        color = JarvisBrowserColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(JarvisBrowserColors.Surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (actionExt.isEnabled) "On" else "Off", color = JarvisBrowserColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = actionExt.isEnabled,
                                onCheckedChange = { BrowserExtensionManager.toggleExtension(actionExt.id, it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = JarvisBrowserColors.Coral)
                            )
                        }
                        HorizontalDivider(color = JarvisBrowserColors.CardBorder, thickness = 0.5.dp)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Pin to Toolbar", color = JarvisBrowserColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = actionExt.pinned,
                                onCheckedChange = { BrowserExtensionManager.togglePinned(actionExt.id, it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = JarvisBrowserColors.Coral)
                            )
                        }
                        HorizontalDivider(color = JarvisBrowserColors.CardBorder, thickness = 0.5.dp)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Run in Private Space", color = JarvisBrowserColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = actionExt.allowInPrivate,
                                onCheckedChange = { BrowserExtensionManager.togglePrivateAccess(actionExt.id, it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = JarvisBrowserColors.PrivateAccent)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(JarvisBrowserColors.Surface)
                            .clickable {
                                val target = actionExt
                                actionTargetExtId = null
                                onShowDetails(target)
                            }
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null, tint = JarvisBrowserColors.TextPrimary, modifier = Modifier.size(19.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Details & Permissions", color = JarvisBrowserColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = JarvisBrowserColors.TextSecondary)
                    }

                    if (actionExt.isEnabled && actionExt.optionsPage.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(JarvisBrowserColors.Surface)
                                .clickable {
                                    val target = actionExt
                                    actionTargetExtId = null
                                    onOpenOptions(target)
                                }
                                .padding(horizontal = 16.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Settings, null, tint = JarvisBrowserColors.SkyBlue, modifier = Modifier.size(19.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Extension Options", color = JarvisBrowserColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = JarvisBrowserColors.TextSecondary)
                        }
                    }

                    if (actionExt.isEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(JarvisBrowserColors.CoralSoft)
                                .clickable {
                                    val target = actionExt
                                    actionTargetExtId = null
                                    onOpenPopup(target)
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.OpenInNew, null, tint = JarvisBrowserColors.Coral, modifier = Modifier.size(19.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Open Extension Interface", color = JarvisBrowserColors.Coral, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (actionExt.sourceType != "bundled") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(JarvisBrowserColors.Surface)
                                .clickable {
                                    BrowserExtensionManager.removeExtension(actionExt.id)
                                    actionTargetExtId = null
                                }
                                .padding(horizontal = 16.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Delete, null, tint = JarvisBrowserColors.Coral, modifier = Modifier.size(19.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Remove Extension", color = JarvisBrowserColors.Coral, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeveloperExtensionsScreen(
    extensions: List<BrowserExtension>,
    onBack: () -> Unit,
    onInspectWorker: (BrowserExtension) -> Unit,
    onShowDetails: (BrowserExtension) -> Unit,
    onOpenPopup: (BrowserExtension) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by BrowserDataStore.preferences.collectAsState()
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var lastInstalledExt by remember { mutableStateOf<BrowserExtension?>(null) }
    var isInstalling by remember { mutableStateOf(false) }
    var showStoreDialog by remember { mutableStateOf(false) }
    var storeInput by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var pendingPreview by remember { mutableStateOf<CrxExtensionInstaller.StagedExtensionPreview?>(null) }

    val archivePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isInstalling = true
            statusMessage = "Reading extension archive..."
            scope.launch {
                val res = CrxExtensionInstaller.stageFromUri(context, uri)
                isInstalling = false
                res.onSuccess { staged ->
                    pendingPreview = staged
                    statusMessage = "Review permissions for ${staged.extension.name}"
                }.onFailure { err ->
                    lastInstalledExt = null
                    statusMessage = "Install failed: ${err.message}"
                }
            }
        }
    }

    val displayedExtensions = remember(extensions, searchQuery) {
        if (searchQuery.isBlank()) extensions else {
            val q = searchQuery.trim().lowercase()
            extensions.filter {
                it.name.lowercase().contains(q) || it.id.lowercase().contains(q) || it.description.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBrowserColors.BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("developer_extensions_screen")
    ) {
        // Header matching `00:36`
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = JarvisBrowserColors.TextPrimary)
            }
            Text("Extensions", color = JarvisBrowserColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { searchOpen = !searchOpen }) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = JarvisBrowserColors.TextPrimary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Developer mode", color = JarvisBrowserColors.TextSecondary, fontSize = 12.sp)
                Switch(
                    checked = prefs.developerModeExtensions,
                    onCheckedChange = { enabled ->
                        BrowserDataStore.updatePreferences { it.copy(developerModeExtensions = enabled) }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = JarvisBrowserColors.Coral)
                )
            }
        }

        if (searchOpen) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter by name or ID...", color = JarvisBrowserColors.TextSecondary, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisBrowserColors.Coral,
                    unfocusedBorderColor = JarvisBrowserColors.CardBorder,
                    focusedTextColor = JarvisBrowserColors.TextPrimary,
                    unfocusedTextColor = JarvisBrowserColors.TextPrimary
                )
            )
        }

        // Action Row: (from .zip/.crx/.user.js) | Web Store | Update
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1.45f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(JarvisBrowserColors.SurfaceElevated)
                    .border(1.dp, JarvisBrowserColors.CardBorder, RoundedCornerShape(999.dp))
                    .clickable {
                        archivePickerLauncher.launch(
                            arrayOf(
                                "application/zip",
                                "application/x-zip-compressed",
                                "application/x-chrome-extension",
                                "application/javascript",
                                "text/javascript",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("ext_install_archive_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.FolderZip, contentDescription = null, tint = JarvisBrowserColors.Coral, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("(from .zip/.crx/.user.js)", color = JarvisBrowserColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(JarvisBrowserColors.SurfaceElevated)
                    .border(1.dp, JarvisBrowserColors.CardBorder, RoundedCornerShape(999.dp))
                    .clickable { showStoreDialog = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("ext_install_webstore_id_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = JarvisBrowserColors.GreenSecure, modifier = Modifier.size(15.dp))
                Text("Web Store", color = JarvisBrowserColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(JarvisBrowserColors.SurfaceElevated)
                    .border(1.dp, JarvisBrowserColors.CardBorder, RoundedCornerShape(999.dp))
                    .clickable {
                        isInstalling = true
                        scope.launch {
                            val count = BrowserExtensionManager.updateWebStoreExtensions(context)
                            isInstalling = false
                            statusMessage = if (count > 0) "Updated $count extension(s)" else "All extensions up to date"
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = JarvisBrowserColors.SkyBlue, modifier = Modifier.size(15.dp))
                Text("Update", color = JarvisBrowserColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (isInstalling || statusMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(JarvisBrowserColors.CoralSoft)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isInstalling) {
                    CircularProgressIndicator(color = JarvisBrowserColors.Coral, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                Text(
                    text = statusMessage ?: "",
                    color = JarvisBrowserColors.TextPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                if (!isInstalling && lastInstalledExt != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(JarvisBrowserColors.Coral)
                            .clickable { onOpenPopup(lastInstalledExt!!) }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text("Open Now", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Text(
            text = "All Extensions",
            color = JarvisBrowserColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(displayedExtensions, key = { it.id }) { ext ->
                val workerActive = BrowserExtensionManager.isWorkerRunning(ext.id)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(JarvisBrowserColors.Surface)
                        .border(1.dp, JarvisBrowserColors.CardBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (ext.isEnabled) onOpenPopup(ext)
                        }
                    ) {
                        ExtensionBadgeIcon(ext, sizeDp = 42)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${ext.name}  ${ext.version}", color = JarvisBrowserColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("ID: ${ext.id}", color = JarvisBrowserColors.TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Switch(
                            checked = ext.isEnabled,
                            onCheckedChange = { BrowserExtensionManager.toggleExtension(ext.id, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = JarvisBrowserColors.Coral)
                        )
                    }

                    if (ext.description.isNotBlank()) {
                        Text(ext.description, color = JarvisBrowserColors.TextSecondary, fontSize = 12.5.sp)
                    }

                    if (prefs.developerModeExtensions || ext.serviceWorkerFile.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(JarvisBrowserColors.BgDark)
                                .clickable { onInspectWorker(ext) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = JarvisBrowserColors.SkyBlue, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Inspect views: service worker (${if (workerActive) "Active" else "Inactive"})",
                                color = JarvisBrowserColors.SkyBlue,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(JarvisBrowserColors.SurfaceElevated)
                                .border(1.dp, JarvisBrowserColors.CardBorder, RoundedCornerShape(999.dp))
                                .clickable { onShowDetails(ext) }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text("Details", color = JarvisBrowserColors.SkyBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (ext.sourceType != "bundled") {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(JarvisBrowserColors.SurfaceElevated)
                                    .border(1.dp, JarvisBrowserColors.CardBorder, RoundedCornerShape(999.dp))
                                    .clickable {
                                        BrowserExtensionManager.removeExtension(ext.id)
                                        statusMessage = "Removed ${ext.name}"
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text("Remove", color = JarvisBrowserColors.Coral, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (ext.isEnabled) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(JarvisBrowserColors.CoralSoft)
                                    .clickable { onOpenPopup(ext) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text("Open UI", color = JarvisBrowserColors.Coral, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStoreDialog) {
        AlertDialog(
            onDismissRequest = { showStoreDialog = false },
            containerColor = JarvisBrowserColors.Surface,
            title = { Text("Install from Chrome Web Store", color = JarvisBrowserColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste Chrome Web Store URL or 32-char Extension ID:",
                        color = JarvisBrowserColors.TextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = storeInput,
                        onValueChange = { storeInput = it },
                        placeholder = { Text("https://chromewebstore.google.com/detail/...", color = JarvisBrowserColors.TextSecondary, fontSize = 12.sp) },
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
                    val query = storeInput.trim()
                    showStoreDialog = false
                    if (query.isNotEmpty()) {
                        isInstalling = true
                        statusMessage = "Fetching CRX from Chrome Web Store..."
                        scope.launch {
                            val res = CrxExtensionInstaller.stageFromChromeWebStore(context, query)
                            isInstalling = false
                            res.onSuccess { staged ->
                                pendingPreview = staged
                                statusMessage = "Review permissions for ${staged.extension.name}"
                            }.onFailure { err ->
                                lastInstalledExt = null
                                statusMessage = "Web Store install failed: ${err.message}"
                            }
                        }
                    }
                }) {
                    Text("Install", color = JarvisBrowserColors.Coral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStoreDialog = false }) {
                    Text("Cancel", color = JarvisBrowserColors.TextSecondary)
                }
            }
        )
    }

    pendingPreview?.let { preview ->
        ExtensionInstallConfirmDialog(
            preview = preview,
            onConfirm = { pinToToolbar ->
                val finalized = CrxExtensionInstaller.finalizeStagedInstall(context, preview, pinToToolbar)
                pendingPreview = null
                finalized.onSuccess { installed ->
                    lastInstalledExt = installed
                    statusMessage = "Installed ${installed.name} v${installed.version}"
                }.onFailure { err ->
                    statusMessage = "Install failed: ${err.message}"
                }
            },
            onCancel = {
                CrxExtensionInstaller.discardStagedInstall(preview)
                pendingPreview = null
                statusMessage = "Installation cancelled"
            }
        )
    }
}

private const val POPUP_VIEWPORT_RESIZE_JS =
    "try { window.dispatchEvent(new Event('resize')); } catch (_) {}"

private fun dispatchPopupViewportResize(webView: WebView?) {
    if (webView == null || !webView.isAttachedToWindow) return
    runCatching {
        webView.requestLayout()
        webView.evaluateJavascript(POPUP_VIEWPORT_RESIZE_JS, null)
    }
}

private enum class PopupJsDialogKind {
    ALERT,
    CONFIRM,
    PROMPT
}

private data class PopupJsDialogRequest(
    val kind: PopupJsDialogKind,
    val message: String,
    val defaultValue: String = "",
    val onConfirm: (String?) -> Unit,
    val onCancel: () -> Unit
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ExtensionPopupSheet(
    extension: BrowserExtension,
    activeTabId: String,
    onDismiss: () -> Unit,
    onOpenUrlInNewTab: (String) -> Unit = {},
    onOpenInFullTab: (BrowserExtension) -> Unit = {},
    onOpenDetails: (BrowserExtension) -> Unit = {},
    entryOverride: String? = null
) {
    val context = LocalContext.current
    val entryPage = entryOverride?.takeIf { it.isNotBlank() }
        ?: extension.uiEntryPage.ifBlank { "__jarvis_ext_dashboard.html" }
    val numericId = remember(activeTabId) { BrowserExtensionRuntimeBridge.numericIdFor(activeTabId) }
    val actionUiStates by BrowserExtensionManager.actionUiStates.collectAsState()
    val liveTitle = actionUiStates[extension.id]?.title?.takeIf { it.isNotBlank() } ?: extension.name
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    // Open by default at ~86% viewport height (Quetta parity) so sidepanel/popup extensions have room
    // for their full control list while keeping the sheet clearly anchored over the page.
    var isExpandedHeight by remember { mutableStateOf(true) }
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingJsDialog by remember { mutableStateOf<PopupJsDialogRequest?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val cb = pendingFileCallback
        pendingFileCallback = null
        runCatching {
            cb?.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingJsDialog?.onCancel()
            pendingJsDialog = null
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = null
            runCatching {
                popupWebView?.apply {
                    stopLoading()
                    removeAllViews()
                    destroy()
                }
            }
            popupWebView = null
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(onClick = onDismiss)
            .testTag("extension_popup_sheet")
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight
        val density = LocalDensity.current
        val sheetHeightDp = remember(containerHeight, isExpandedHeight) {
            val minBound = 220.dp.coerceAtMost(containerHeight)
            val maxAvailable = (containerHeight - 40.dp).coerceIn(minBound, containerHeight)
            val expandedHeight = (containerHeight * 0.86f).coerceIn(
                480.dp.coerceAtMost(maxAvailable),
                maxAvailable
            )
            val compactHeight = (containerHeight * 0.58f).coerceIn(
                360.dp.coerceAtMost(expandedHeight),
                expandedHeight
            )
            if (isExpandedHeight) expandedHeight else compactHeight
        }
        val webViewWidthPx = with(density) { containerWidth.roundToPx().coerceAtLeast(360) }
        val webViewHeightPx = with(density) { (sheetHeightDp - 64.dp).coerceAtLeast(200.dp).roundToPx() }

        LaunchedEffect(webViewHeightPx, popupWebView) {
            val wv = popupWebView ?: return@LaunchedEffect
            wv.post { dispatchPopupViewportResize(wv) }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeightDp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(JarvisBrowserColors.Surface)
                .border(1.dp, JarvisBrowserColors.CardBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(enabled = false, onClick = {})
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JarvisBrowserColors.SurfaceElevated)
                    .clickable { isExpandedHeight = !isExpandedHeight }
                    .padding(top = 8.dp, bottom = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.28f))
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JarvisBrowserColors.SurfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExtensionBadgeIcon(extension, sizeDp = 28)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = liveTitle,
                        color = JarvisBrowserColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "MV${extension.manifestVersion} • $entryPage",
                        color = JarvisBrowserColors.TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { popupWebView?.reload() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload Popup", tint = JarvisBrowserColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { isExpandedHeight = !isExpandedHeight }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (isExpandedHeight) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Resize Popup",
                        tint = JarvisBrowserColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = {
                        onDismiss()
                        onOpenInFullTab(extension)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open in Full Tab", tint = JarvisBrowserColors.Coral, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { onOpenDetails(extension) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Info, contentDescription = "Details", tint = JarvisBrowserColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close Popup", tint = JarvisBrowserColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        popupWebView = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setLayerType(View.LAYER_TYPE_NONE, null)
                        setBackgroundColor(AndroidColor.parseColor("#121214"))
                        isVerticalScrollBarEnabled = true
                        isHorizontalScrollBarEnabled = false
                        overScrollMode = View.OVER_SCROLL_ALWAYS
                        isFocusable = true
                        isFocusableInTouchMode = true
                        BrowserIdentity.applyTo(settings, ctx, desktop = false)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = false
                            allowContentAccess = true
                            allowFileAccessFromFileURLs = false
                            allowUniversalAccessFromFileURLs = false
                            setSupportMultipleWindows(true)
                            javaScriptCanOpenWindowsAutomatically = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            setSupportZoom(false)
                            builtInZoomControls = false
                            displayZoomControls = false
                            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                                WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, false)
                            }
                        }
                        addJavascriptInterface(
                            BrowserExtensionRuntimeBridge.ChromeJsBridge(
                                context = context,
                                ownerWebViewProvider = { popupWebView },
                                currentTabNumericIdProvider = { numericId }
                            ),
                            "__jarvis_chrome_bridge"
                        )
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                                BrowserExtensionManager.recordLog(extension.id, "[UI] ${cm?.message()}")
                                return true
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                if (filePathCallback == null) return false
                                pendingFileCallback?.onReceiveValue(null)
                                pendingFileCallback = filePathCallback
                                val acceptTypes = fileChooserParams?.acceptTypes
                                    ?.filter { it.isNotBlank() && it.contains("/") }
                                    ?.toTypedArray()
                                    ?.takeIf { it.isNotEmpty() }
                                    ?: arrayOf("*/*")
                                return runCatching {
                                    fileChooserLauncher.launch(acceptTypes)
                                    true
                                }.getOrElse {
                                    pendingFileCallback = null
                                    filePathCallback.onReceiveValue(null)
                                    false
                                }
                            }

                            override fun onPermissionRequest(request: PermissionRequest?) {
                                request ?: return
                                val allowed = request.resources.filter { res ->
                                    res == PermissionRequest.RESOURCE_AUDIO_CAPTURE ||
                                        res == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                }.toTypedArray()
                                if (allowed.isNotEmpty()) request.grant(allowed) else request.deny()
                            }

                            override fun onJsAlert(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: JsResult?
                            ): Boolean {
                                if (result == null) return false
                                pendingJsDialog?.onCancel()
                                pendingJsDialog = PopupJsDialogRequest(
                                    kind = PopupJsDialogKind.ALERT,
                                    message = message.orEmpty(),
                                    onConfirm = { result.confirm() },
                                    onCancel = { result.cancel() }
                                )
                                return true
                            }

                            override fun onJsConfirm(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: JsResult?
                            ): Boolean {
                                if (result == null) return false
                                pendingJsDialog?.onCancel()
                                pendingJsDialog = PopupJsDialogRequest(
                                    kind = PopupJsDialogKind.CONFIRM,
                                    message = message.orEmpty(),
                                    onConfirm = { result.confirm() },
                                    onCancel = { result.cancel() }
                                )
                                return true
                            }

                            override fun onJsPrompt(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                defaultValue: String?,
                                result: JsPromptResult?
                            ): Boolean {
                                if (result == null) return false
                                pendingJsDialog?.onCancel()
                                pendingJsDialog = PopupJsDialogRequest(
                                    kind = PopupJsDialogKind.PROMPT,
                                    message = message.orEmpty(),
                                    defaultValue = defaultValue.orEmpty(),
                                    onConfirm = { value -> result.confirm(value ?: defaultValue.orEmpty()) },
                                    onCancel = { result.cancel() }
                                )
                                return true
                            }

                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?
                            ): Boolean {
                                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                val tempWebView = WebView(ctx).apply {
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            v: WebView?,
                                            req: WebResourceRequest?
                                        ): Boolean {
                                            val target = req?.url?.toString().orEmpty()
                                            if (target.isNotBlank()) {
                                                onDismiss()
                                                onOpenUrlInNewTab(target)
                                            }
                                            runCatching { v?.destroy() }
                                            return true
                                        }
                                    }
                                }
                                transport.webView = tempWebView
                                resultMsg.sendToTarget()
                                return true
                            }

                            override fun onCloseWindow(window: WebView?) {
                                onDismiss()
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val uri = request?.url ?: return false
                                val parsedExt = BrowserExtensionRuntimeBridge.parseExtensionResourceUrl(uri.toString())
                                val expectedHost = android.net.Uri.parse(extension.extensionOrigin()).host
                                if (parsedExt != null && (parsedExt.first == extension.id || parsedExt.first == extension.safeHostId())) {
                                    if (uri.scheme.equals("chrome-extension", ignoreCase = true)) {
                                        view?.loadUrl("${extension.extensionOrigin()}/${parsedExt.second}")
                                        return true
                                    }
                                    return uri.host != expectedHost
                                }
                                val targetStr = uri.toString()
                                if (targetStr.startsWith("http://") || targetStr.startsWith("https://") || targetStr.startsWith("chrome-extension://")) {
                                    onDismiss()
                                    onOpenUrlInNewTab(targetStr)
                                    return true
                                }
                                return true
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                return BrowserExtensionRuntimeBridge.interceptExtensionRequest(context, request, numericId)
                                    ?: super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.post { dispatchPopupViewportResize(view) }
                                view?.postDelayed({ dispatchPopupViewportResize(view) }, 120L)
                            }
                        }
                        // Pre-measure WebView to exact sheet viewport dimensions before loadUrl so
                        // `100vh` / `height: 100%` flex containers render with non-zero scrollable bodies.
                        runCatching {
                            measure(
                                View.MeasureSpec.makeMeasureSpec(webViewWidthPx, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(webViewHeightPx, View.MeasureSpec.EXACTLY)
                            )
                            layout(0, 0, webViewWidthPx, webViewHeightPx)
                        }
                        loadUrl("${extension.extensionOrigin()}/$entryPage")
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
            )
        }
    }

    pendingJsDialog?.let { req ->
        var promptInput by remember(req) { mutableStateOf(req.defaultValue) }
        AlertDialog(
            onDismissRequest = {
                pendingJsDialog = null
                req.onCancel()
            },
            containerColor = JarvisBrowserColors.Surface,
            title = {
                Text(
                    text = liveTitle,
                    color = JarvisBrowserColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (req.message.isNotBlank()) {
                        Text(
                            text = req.message,
                            color = JarvisBrowserColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    if (req.kind == PopupJsDialogKind.PROMPT) {
                        OutlinedTextField(
                            value = promptInput,
                            onValueChange = { promptInput = it },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = JarvisBrowserColors.TextPrimary,
                                unfocusedTextColor = JarvisBrowserColors.TextPrimary,
                                focusedBorderColor = JarvisBrowserColors.Coral,
                                unfocusedBorderColor = JarvisBrowserColors.CardBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = if (req.kind == PopupJsDialogKind.PROMPT) promptInput else null
                    pendingJsDialog = null
                    req.onConfirm(value)
                }) {
                    Text("OK", color = JarvisBrowserColors.Coral, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                if (req.kind != PopupJsDialogKind.ALERT) {
                    TextButton(onClick = {
                        pendingJsDialog = null
                        req.onCancel()
                    }) {
                        Text("Cancel", color = JarvisBrowserColors.TextSecondary)
                    }
                }
            }
        )
    }
}

@Composable
fun ExtensionWorkerInspectorDialog(
    extension: BrowserExtension,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val logsTick by BrowserExtensionManager.logsTick.collectAsState()
    var jsCommand by remember { mutableStateOf("") }
    var logs by remember { mutableStateOf(BrowserExtensionManager.getWorkerLogs(extension.id)) }

    LaunchedEffect(logsTick, extension.id) {
        logs = BrowserExtensionManager.getWorkerLogs(extension.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisBrowserColors.Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = JarvisBrowserColors.SkyBlue)
                Text("Worker Inspector • ${extension.name}", color = JarvisBrowserColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Script: ${extension.serviceWorkerFile.ifBlank { "none" }}", color = JarvisBrowserColors.TextSecondary, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(JarvisBrowserColors.BgDark)
                        .border(1.dp, JarvisBrowserColors.CardBorder, RoundedCornerShape(10.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (logs.isEmpty()) "No console logs yet. Execute JS below or reload page." else logs.joinToString("\n"),
                        color = JarvisBrowserColors.GreenSecure,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                OutlinedTextField(
                    value = jsCommand,
                    onValueChange = { jsCommand = it },
                    placeholder = { Text("chrome.tabs.query({}, console.log) or JS...", color = JarvisBrowserColors.TextSecondary, fontSize = 11.5.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisBrowserColors.SkyBlue,
                        unfocusedBorderColor = JarvisBrowserColors.CardBorder,
                        focusedTextColor = JarvisBrowserColors.TextPrimary,
                        unfocusedTextColor = JarvisBrowserColors.TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cmd = jsCommand.trim()
                if (cmd.isNotEmpty()) {
                    jsCommand = ""
                    scope.launch {
                        BrowserExtensionManager.evaluateInWorker(extension.id, cmd)
                    }
                }
            }) {
                Text("Evaluate", color = JarvisBrowserColors.Coral, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = JarvisBrowserColors.TextSecondary)
            }
        }
    )
}

@Composable
fun ExtensionDetailsDialog(
    extension: BrowserExtension,
    onDismiss: () -> Unit,
    onOpenUi: (BrowserExtension) -> Unit = {},
    onOpenOptions: (BrowserExtension) -> Unit = {},
    onInspectWorker: (BrowserExtension) -> Unit = {}
) {
    val allExtensions by BrowserExtensionManager.extensions.collectAsState()
    val liveExt = allExtensions.firstOrNull { it.id == extension.id } ?: extension
    val permSummary = remember(liveExt.id, liveExt.permissions, liveExt.hostPermissions) {
        summarizeExtensionPermissions(liveExt)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisBrowserColors.Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExtensionBadgeIcon(liveExt, sizeDp = 36)
                Column(modifier = Modifier.weight(1f)) {
                    Text(liveExt.name, color = JarvisBrowserColors.TextPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("v${liveExt.version} • MV${liveExt.manifestVersion} • ${liveExt.sourceType}", color = JarvisBrowserColors.TextSecondary, fontSize = 11.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("ID: ${liveExt.id}", color = JarvisBrowserColors.TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                if (liveExt.description.isNotBlank()) {
                    Text(liveExt.description, color = JarvisBrowserColors.TextPrimary, fontSize = 12.5.sp)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(JarvisBrowserColors.BgDark)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enabled", color = JarvisBrowserColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = liveExt.isEnabled,
                            onCheckedChange = { BrowserExtensionManager.toggleExtension(liveExt.id, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = JarvisBrowserColors.Coral)
                        )
                    }
                    HorizontalDivider(color = JarvisBrowserColors.CardBorder, thickness = 0.5.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pin to toolbar", color = JarvisBrowserColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = liveExt.pinned,
                            onCheckedChange = { BrowserExtensionManager.togglePinned(liveExt.id, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = JarvisBrowserColors.Coral)
                        )
                    }
                    HorizontalDivider(color = JarvisBrowserColors.CardBorder, thickness = 0.5.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Run in Private Space", color = JarvisBrowserColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = liveExt.allowInPrivate,
                            onCheckedChange = { BrowserExtensionManager.togglePrivateAccess(liveExt.id, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = JarvisBrowserColors.PrivateAccent)
                        )
                    }
                    HorizontalDivider(color = JarvisBrowserColors.CardBorder, thickness = 0.5.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Allow user scripts", color = JarvisBrowserColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = liveExt.allowUserScripts,
                            onCheckedChange = { BrowserExtensionManager.toggleAllowUserScripts(liveExt.id, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = JarvisBrowserColors.SkyBlue)
                        )
                    }
                }

                Text("Permissions & Site Access", color = JarvisBrowserColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                permSummary.forEach { (title, desc) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(JarvisBrowserColors.BgDark)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(title, color = JarvisBrowserColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(desc, color = JarvisBrowserColors.TextSecondary, fontSize = 11.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (liveExt.isEnabled) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(JarvisBrowserColors.CoralSoft)
                                .clickable { onDismiss(); onOpenUi(liveExt) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text("Open UI", color = JarvisBrowserColors.Coral, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (liveExt.isEnabled && liveExt.optionsPage.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(JarvisBrowserColors.SurfaceElevated)
                                .clickable { onDismiss(); onOpenOptions(liveExt) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text("Options", color = JarvisBrowserColors.SkyBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (liveExt.serviceWorkerFile.isNotBlank() || liveExt.backgroundScripts.isNotEmpty() || liveExt.backgroundPage.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(JarvisBrowserColors.SurfaceElevated)
                                .clickable { onInspectWorker(liveExt) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text("Inspect Worker", color = JarvisBrowserColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(JarvisBrowserColors.SurfaceElevated)
                            .clickable { BrowserExtensionManager.reloadSingleExtension(liveExt.id) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("Reload", color = JarvisBrowserColors.GreenSecure, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (liveExt.sourceType != "bundled") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(JarvisBrowserColors.SurfaceElevated)
                                .clickable {
                                    BrowserExtensionManager.removeExtension(liveExt.id)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text("Remove", color = JarvisBrowserColors.Coral, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = JarvisBrowserColors.Coral, fontWeight = FontWeight.Bold)
            }
        }
    )
}
