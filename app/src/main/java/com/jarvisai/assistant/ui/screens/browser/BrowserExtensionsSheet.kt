package com.jarvisai.assistant.ui.screens.browser

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.WebChromeClient
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import com.jarvisai.assistant.extension.ExtensionPageInjector
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
            .background(if (bmp == null) QuettaColors.Coral.copy(alpha = 0.2f) else QuettaColors.SurfaceElevated),
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
                color = QuettaColors.Coral,
                fontSize = (sizeDp * 0.36f).sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun QuettaExtensionsQuickSheet(
    extensions: List<BrowserExtension>,
    onDismiss: () -> Unit,
    onOpenExtensionPopup: (BrowserExtension) -> Unit,
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
                .background(QuettaColors.Surface)
                .border(1.dp, QuettaColors.CardBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(enabled = false, onClick = {})
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Sheet Handle + Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Extension, contentDescription = null, tint = QuettaColors.Coral, modifier = Modifier.size(20.dp))
                    Text("Extensions", color = QuettaColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isGrid = !isGrid }) {
                        Icon(
                            imageVector = if (isGrid) Icons.Default.FormatListBulleted else Icons.Default.Apps,
                            contentDescription = "Toggle Grid/List",
                            tint = QuettaColors.TextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = QuettaColors.TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val enabledExts = remember(extensions) { extensions.filter { it.isEnabled } }
            if (enabledExts.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No active extensions", color = QuettaColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Install from Chrome Web Store or .crx / .zip / .user.js",
                        color = QuettaColors.TextSecondary,
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
                                .clickable { onOpenExtensionPopup(ext) }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ExtensionBadgeIcon(ext, sizeDp = 42)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = ext.name,
                                color = QuettaColors.TextPrimary,
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
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(enabledExts, key = { it.id }) { ext ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(QuettaColors.SurfaceElevated)
                                .clickable { onOpenExtensionPopup(ext) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExtensionBadgeIcon(ext, sizeDp = 34)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ext.name,
                                    color = QuettaColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (ext.popupFile.isNotBlank()) "Tap to open popup" else "v${ext.version} • Active",
                                    color = QuettaColors.TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                            Text(
                                text = "MV${ext.manifestVersion}",
                                color = QuettaColors.Coral,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer Bar: Manage Extensions | Chrome Web Store | Settings Gear (Developer)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(QuettaColors.SurfaceElevated)
                        .clickable(onClick = onOpenManage)
                        .padding(vertical = 12.dp)
                        .testTag("extensions_manage_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Manage Extensions", color = QuettaColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(QuettaColors.CoralSoft)
                        .border(1.dp, QuettaColors.Coral.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenWebStore)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .testTag("extensions_webstore_btn"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Default.Storefront, contentDescription = "Chrome Web Store", tint = QuettaColors.Coral, modifier = Modifier.size(16.dp))
                    Text("Store", color = QuettaColors.Coral, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onOpenDeveloper,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(QuettaColors.SurfaceElevated)
                        .testTag("extensions_dev_gear_btn")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Developer Extensions", tint = QuettaColors.TextPrimary)
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
    val filtered = remember(extensions, search) {
        if (search.isBlank()) extensions else {
            val q = search.trim().lowercase()
            extensions.filter { it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(QuettaColors.BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("manage_extensions_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = QuettaColors.TextPrimary)
            }
            Text("Manage Extensions", color = QuettaColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${extensions.size} installed", color = QuettaColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp))
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search extensions...", color = QuettaColors.TextSecondary, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = QuettaColors.TextSecondary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = QuettaColors.Coral,
                unfocusedBorderColor = QuettaColors.CardBorder,
                focusedContainerColor = QuettaColors.Surface,
                unfocusedContainerColor = QuettaColors.Surface,
                focusedTextColor = QuettaColors.TextPrimary,
                unfocusedTextColor = QuettaColors.TextPrimary
            )
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered, key = { it.id }) { ext ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(QuettaColors.Surface)
                        .border(1.dp, QuettaColors.CardBorder, RoundedCornerShape(16.dp))
                        .clickable {
                            if (ext.isEnabled && ext.popupFile.isNotBlank()) {
                                onOpenPopup(ext)
                            } else {
                                onShowDetails(ext)
                            }
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExtensionBadgeIcon(ext, sizeDp = 40)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ext.name, color = QuettaColors.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("v${ext.version} • ${ext.sourceType}", color = QuettaColors.TextSecondary, fontSize = 11.5.sp)
                        if (ext.description.isNotBlank()) {
                            Text(ext.description, color = QuettaColors.TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = ext.isEnabled,
                        onCheckedChange = { BrowserExtensionManager.toggleExtension(ext.id, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = QuettaColors.Coral)
                    )
                }
            }
        }

        // Bottom link "Developer options..." matching `01:44`
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(QuettaColors.Surface)
                .border(1.dp, QuettaColors.CardBorder)
                .clickable(onClick = onOpenDeveloperOptions)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("manage_extensions_developer_btn"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Code, contentDescription = null, tint = QuettaColors.Coral)
                Text("Developer options...", color = QuettaColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("CRX / ZIP / UserScript", color = QuettaColors.TextSecondary, fontSize = 12.sp)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(QuettaColors.BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("developer_extensions_screen")
    ) {
        // Header with Developer mode switch (`01:48`)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = QuettaColors.TextPrimary)
            }
            Text("Extensions", color = QuettaColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Developer mode", color = QuettaColors.TextSecondary, fontSize = 12.sp)
                Switch(
                    checked = prefs.developerModeExtensions,
                    onCheckedChange = { enabled ->
                        BrowserDataStore.updatePreferences { it.copy(developerModeExtensions = enabled) }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = QuettaColors.Coral)
                )
            }
        }

        // Action Row: +(from .zip/.crx/.user.js) | Chrome Store | Update
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1.4f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(QuettaColors.SurfaceElevated)
                    .border(1.dp, QuettaColors.CardBorder, RoundedCornerShape(12.dp))
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
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .testTag("ext_install_archive_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.FolderZip, contentDescription = null, tint = QuettaColors.Coral, modifier = Modifier.size(16.dp))
                Text("+ (from .zip/.crx/.user.js)", color = QuettaColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(QuettaColors.SurfaceElevated)
                    .border(1.dp, QuettaColors.CardBorder, RoundedCornerShape(12.dp))
                    .clickable { showStoreDialog = true }
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .testTag("ext_install_webstore_id_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = QuettaColors.GreenSecure, modifier = Modifier.size(15.dp))
                Text("Web Store", color = QuettaColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(QuettaColors.SurfaceElevated)
                    .border(1.dp, QuettaColors.CardBorder, RoundedCornerShape(12.dp))
                    .clickable {
                        isInstalling = true
                        scope.launch {
                            val count = BrowserExtensionManager.updateWebStoreExtensions(context)
                            isInstalling = false
                            statusMessage = if (count > 0) "Updated $count extension(s)" else "All extensions up to date"
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = QuettaColors.SkyBlue, modifier = Modifier.size(15.dp))
                Text("Update", color = QuettaColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (isInstalling || statusMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(QuettaColors.CoralSoft)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isInstalling) {
                    CircularProgressIndicator(color = QuettaColors.Coral, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                Text(
                    text = statusMessage ?: "",
                    color = QuettaColors.TextPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(extensions, key = { it.id }) { ext ->
                val workerActive = BrowserExtensionManager.isWorkerRunning(ext.id)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(QuettaColors.Surface)
                        .border(1.dp, QuettaColors.CardBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExtensionBadgeIcon(ext, sizeDp = 42)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${ext.name}  ${ext.version}", color = QuettaColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("ID: ${ext.id}", color = QuettaColors.TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Switch(
                            checked = ext.isEnabled,
                            onCheckedChange = { BrowserExtensionManager.toggleExtension(ext.id, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = QuettaColors.Coral)
                        )
                    }

                    if (ext.description.isNotBlank()) {
                        Text(ext.description, color = QuettaColors.TextSecondary, fontSize = 12.5.sp)
                    }

                    if (prefs.developerModeExtensions || ext.serviceWorkerFile.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(QuettaColors.BgDark)
                                .clickable { onInspectWorker(ext) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = QuettaColors.SkyBlue, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Inspect views: service worker (${if (workerActive) "Active" else "Inactive"})",
                                color = QuettaColors.SkyBlue,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (ext.isEnabled && ext.popupFile.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(QuettaColors.CoralSoft)
                                    .clickable { onOpenPopup(ext) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text("Popup", color = QuettaColors.Coral, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(QuettaColors.SurfaceElevated)
                                .clickable { onShowDetails(ext) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text("Details", color = QuettaColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (ext.sourceType != "bundled") {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x22EF4444))
                                    .clickable {
                                        BrowserExtensionManager.removeExtension(ext.id)
                                        statusMessage = "Removed ${ext.name}"
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = QuettaColors.Coral, modifier = Modifier.size(14.dp))
                                    Text("Remove", color = QuettaColors.Coral, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
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
            containerColor = QuettaColors.Surface,
            title = { Text("Install from Chrome Web Store", color = QuettaColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste Chrome Web Store URL or 32-char Extension ID (e.g. cjpalhdlnbpafiamejdnhcphjbkeiagm):",
                        color = QuettaColors.TextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = storeInput,
                        onValueChange = { storeInput = it },
                        placeholder = { Text("https://chromewebstore.google.com/detail/...", color = QuettaColors.TextSecondary, fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = QuettaColors.Coral,
                            unfocusedBorderColor = QuettaColors.CardBorder,
                            focusedTextColor = QuettaColors.TextPrimary,
                            unfocusedTextColor = QuettaColors.TextPrimary
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
                    Text("Install", color = QuettaColors.Coral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStoreDialog = false }) {
                    Text("Cancel", color = QuettaColors.TextSecondary)
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
    activeTabUrl: String,
    activeTabTitle: String,
    onEvalInActiveTab: (String) -> Unit,
    onOpenNewTab: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val popupFile = remember(extension) {
        File(extension.dirPath, extension.popupFile.removePrefix("/"))
    }

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
                .height(440.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(QuettaColors.Surface)
                .border(1.dp, QuettaColors.CardBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(enabled = false, onClick = {})
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(QuettaColors.SurfaceElevated)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExtensionBadgeIcon(extension, sizeDp = 28)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = extension.name,
                    color = QuettaColors.TextPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close Popup", tint = QuettaColors.TextSecondary)
                }
            }

            if (popupFile.exists()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                @Suppress("DEPRECATION")
                                allowFileAccessFromFileURLs = true
                                @Suppress("DEPRECATION")
                                allowUniversalAccessFromFileURLs = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }
                            addJavascriptInterface(
                                ExtensionPageInjector.PopupExtensionBridge(
                                    context = context,
                                    extension = extension,
                                    activeTabId = activeTabId,
                                    activeTabUrl = activeTabUrl,
                                    activeTabTitle = activeTabTitle,
                                    onEvalActiveTab = onEvalInActiveTab,
                                    onOpenNewTab = onOpenNewTab
                                ),
                                "__jarvis_popup_bridge"
                            )
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    view?.evaluateJavascript(
                                        ExtensionPageInjector.buildPopupChromeBridgeJs(extension),
                                        null
                                    )
                                }
                            }
                            loadUrl("file://${popupFile.absolutePath}")
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
                        color = QuettaColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = extension.description.ifBlank { "Background/Content Script extension active on matching pages." },
                        color = QuettaColors.TextSecondary,
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
        containerColor = QuettaColors.Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = QuettaColors.SkyBlue)
                Text("Worker Inspector • ${extension.name}", color = QuettaColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Script: ${extension.serviceWorkerFile}", color = QuettaColors.TextSecondary, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(QuettaColors.BgDark)
                        .border(1.dp, QuettaColors.CardBorder, RoundedCornerShape(10.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (logs.isEmpty()) "No console logs yet. Execute JS below or reload page." else logs.joinToString("\n"),
                        color = QuettaColors.GreenSecure,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                OutlinedTextField(
                    value = jsCommand,
                    onValueChange = { jsCommand = it },
                    placeholder = { Text("chrome.tabs.query({}, console.log) or JS...", color = QuettaColors.TextSecondary, fontSize = 11.5.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = QuettaColors.SkyBlue,
                        unfocusedBorderColor = QuettaColors.CardBorder,
                        focusedTextColor = QuettaColors.TextPrimary,
                        unfocusedTextColor = QuettaColors.TextPrimary
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
                Text("Evaluate", color = QuettaColors.Coral, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = QuettaColors.TextSecondary)
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
        containerColor = QuettaColors.Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Info, contentDescription = null, tint = QuettaColors.Coral)
                Text(extension.name, color = QuettaColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Version: ${extension.version} (MV${extension.manifestVersion})", color = QuettaColors.TextPrimary, fontSize = 13.sp)
                Text("ID: ${extension.id}", color = QuettaColors.TextSecondary, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
                Text("Source: ${extension.sourceType}", color = QuettaColors.TextSecondary, fontSize = 12.sp)
                if (extension.description.isNotBlank()) {
                    Text(extension.description, color = QuettaColors.TextPrimary, fontSize = 12.5.sp)
                }
                if (extension.permissions.isNotEmpty()) {
                    Text("Permissions: ${extension.permissions.joinToString(", ")}", color = QuettaColors.Gold, fontSize = 12.sp)
                }
                if (extension.contentScripts.isNotEmpty()) {
                    val matches = extension.contentScripts.flatMap { it.matches }.distinct()
                    Text("Content Scripts (${extension.contentScripts.size}): ${matches.joinToString(", ")}", color = QuettaColors.SkyBlue, fontSize = 11.5.sp)
                }
                if (extension.popupFile.isNotBlank()) {
                    Text("Popup UI: ${extension.popupFile}", color = QuettaColors.GreenSecure, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = QuettaColors.Coral, fontWeight = FontWeight.Bold)
            }
        }
    )
}
