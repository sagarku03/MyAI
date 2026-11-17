package com.jarvisai.assistant.ui.screens.browser

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jarvisai.assistant.browser.BrowserDataStore
import com.jarvisai.assistant.extension.BrowserExtension
import com.jarvisai.assistant.extension.BrowserExtensionManager
import com.jarvisai.assistant.extension.BrowserExtensionRuntimeBridge
import kotlinx.coroutines.launch
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

@Composable
fun JarvisExtensionsQuickSheet(
    extensions: List<BrowserExtension>,
    onDismiss: () -> Unit,
    onLaunchExtension: (BrowserExtension) -> Unit,
    onOpenManage: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenWebStore: () -> Unit
) {
    var isGrid by remember { mutableStateOf(false) }

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
                    IconButton(onClick = { isGrid = !isGrid }) {
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
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onLaunchExtension(ext) }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ExtensionBadgeIcon(ext, sizeDp = 42)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = ext.name,
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
                                    text = ext.name,
                                    color = JarvisBrowserColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (ext.uiEntryPage.isNotBlank()) {
                                    Text(
                                        text = "Tap to open extension",
                                        color = JarvisBrowserColors.TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Text(
                                text = "MV${ext.manifestVersion}",
                                color = JarvisBrowserColors.Coral,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
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
    onShowDetails: (BrowserExtension) -> Unit
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
                            .clickable { actionTargetExtId = ext.id }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExtensionBadgeIcon(ext, sizeDp = 36)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = ext.name,
                            color = JarvisBrowserColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (ext.isEnabled) "On" else "Off",
                            color = if (ext.isEnabled) JarvisBrowserColors.TextPrimary else JarvisBrowserColors.TextSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = "Extension options",
                            tint = JarvisBrowserColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
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
                        Text("Details", color = JarvisBrowserColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = JarvisBrowserColors.TextSecondary)
                    }

                    if (actionExt.isEnabled && actionExt.uiEntryPage.isNotBlank()) {
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
    var isInstalling by remember { mutableStateOf(false) }
    var showStoreDialog by remember { mutableStateOf(false) }
    var storeInput by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val archivePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isInstalling = true
            statusMessage = "Installing extension archive..."
            scope.launch {
                val res = BrowserExtensionManager.installFromUri(context, uri)
                isInstalling = false
                statusMessage = res.fold(
                    onSuccess = { "Installed ${it.name} v${it.version}" },
                    onFailure = { "Install failed: ${it.message}" }
                )
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                        if (ext.isEnabled && ext.uiEntryPage.isNotBlank()) {
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
                        statusMessage = "Downloading CRX from Chrome Web Store..."
                        scope.launch {
                            val res = BrowserExtensionManager.installFromChromeWebStore(context, query)
                            isInstalling = false
                            statusMessage = res.fold(
                                onSuccess = { "Installed ${it.name} v${it.version}" },
                                onFailure = { "Web Store install failed: ${it.message}" }
                            )
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
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ExtensionPopupSheet(
    extension: BrowserExtension,
    activeTabId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val entryPage = extension.uiEntryPage
    val numericId = remember(activeTabId) { BrowserExtensionRuntimeBridge.numericIdFor(activeTabId) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(onClick = onDismiss)
            .testTag("extension_popup_sheet")
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(480.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(JarvisBrowserColors.Surface)
                .border(1.dp, JarvisBrowserColors.CardBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(enabled = false, onClick = {})
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JarvisBrowserColors.SurfaceElevated)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExtensionBadgeIcon(extension, sizeDp = 28)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = extension.name,
                    color = JarvisBrowserColors.TextPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close Popup", tint = JarvisBrowserColors.TextSecondary)
                }
            }

            if (entryPage.isNotBlank()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            popupWebView = this
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
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
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    return BrowserExtensionRuntimeBridge.interceptExtensionRequest(context, request, numericId)
                                        ?: super.shouldInterceptRequest(view, request)
                                }
                            }
                            loadUrl("${extension.extensionOrigin()}/$entryPage")
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            } else {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${extension.name} (v${extension.version})",
                        color = JarvisBrowserColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = extension.description.ifBlank { "Content script & background worker active on matching pages." },
                        color = JarvisBrowserColors.TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
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
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisBrowserColors.Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Info, contentDescription = null, tint = JarvisBrowserColors.Coral)
                Text(extension.name, color = JarvisBrowserColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Version: ${extension.version} (MV${extension.manifestVersion})", color = JarvisBrowserColors.TextPrimary, fontSize = 13.sp)
                Text("ID: ${extension.id}", color = JarvisBrowserColors.TextSecondary, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
                Text("Source: ${extension.sourceType}", color = JarvisBrowserColors.TextSecondary, fontSize = 12.sp)
                if (extension.description.isNotBlank()) {
                    Text(extension.description, color = JarvisBrowserColors.TextPrimary, fontSize = 12.5.sp)
                }
                if (extension.permissions.isNotEmpty()) {
                    Text("Permissions: ${extension.permissions.joinToString(", ")}", color = JarvisBrowserColors.Gold, fontSize = 12.sp)
                }
                if (extension.contentScripts.isNotEmpty()) {
                    val matches = extension.contentScripts.flatMap { it.matches }.distinct()
                    Text("Content Scripts (${extension.contentScripts.size}): ${matches.joinToString(", ")}", color = JarvisBrowserColors.SkyBlue, fontSize = 11.5.sp)
                }
                if (extension.uiEntryPage.isNotBlank()) {
                    Text("UI Entry: ${extension.uiEntryPage}", color = JarvisBrowserColors.GreenSecure, fontSize = 12.sp)
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
