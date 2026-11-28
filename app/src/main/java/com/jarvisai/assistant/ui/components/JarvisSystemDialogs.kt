package com.jarvisai.assistant.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JarvisTheme
import com.jarvisai.assistant.adb.AdbConnectManager
import com.jarvisai.assistant.termux.JarvisTermuxBridge
import com.jarvisai.assistant.ui.updater.ApkUpdateResult
import com.jarvisai.assistant.ui.updater.AppReleaseInfo
import com.jarvisai.assistant.ui.updater.AppUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun GitHubReleaseUpdateDialog(
    release: AppReleaseInfo,
    updateManager: AppUpdateManager,
    isDownloadingUpdate: Boolean,
    updateDownloadProgress: Int,
    updateDownloadedMbText: String,
    updateErrorMessage: String?,
    pendingInstallApkFile: File?,
    onSetDownloading: (Boolean) -> Unit,
    onSetProgress: (Int, String) -> Unit,
    onSetError: (String?) -> Unit,
    onSetPendingApk: (File?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val themeColors = JarvisTheme.colors

    AlertDialog(
        onDismissRequest = {
            if (!isDownloadingUpdate) {
                onDismiss()
            }
        },
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Update Available: ${release.newVersion}",
                color = themeColors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            val updateItems = remember(release) {
                release.changelogItems.ifEmpty {
                    release.releaseNotes.lines()
                        .map { it.trim().removePrefix("• ").removePrefix("- ").trim() }
                        .filter { it.isNotBlank() }
                }
            }
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Current: ${release.currentVersion} -> New: ${release.newVersion}",
                    color = themeColors.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "What's New in ${release.newVersion}:",
                    color = themeColors.textPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = themeColors.cardSurface,
                    border = BorderStroke(1.dp, themeColors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        updateItems.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    color = themeColors.primary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 1.dp, end = 6.dp)
                                )
                                Text(
                                    text = item,
                                    color = themeColors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
                if (!release.compareUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(release.compareUrl))
                                )
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("update_view_github_diff")
                    ) {
                        Text(
                            text = "View GitHub Diff ↗",
                            color = themeColors.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                if (isDownloadingUpdate) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (updateDownloadProgress >= 0) {
                        LinearProgressIndicator(
                            progress = { updateDownloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = themeColors.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Downloading: $updateDownloadProgress% ${if (updateDownloadedMbText.isNotBlank()) "($updateDownloadedMbText)" else ""}".trim(),
                            color = themeColors.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = themeColors.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Downloading APK... $updateDownloadedMbText".trim(),
                            color = themeColors.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (!updateErrorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF450A0A).copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = updateErrorMessage,
                                color = Color(0xFFFCA5A5),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(
                                onClick = {
                                    val fallbackUrl = release.downloadUrl.ifBlank { release.releasePageUrl }
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl))
                                        )
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "Open Download in Browser ↗",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (pendingInstallApkFile != null && pendingInstallApkFile.exists()) {
                Button(
                    onClick = {
                        runCatching {
                            updateManager.installApk(context, pendingInstallApkFile)
                            onSetPendingApk(null)
                            onDismiss()
                        }.onFailure { err ->
                            onSetError(err.message ?: "Installer launch failed.")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Install APK Now", color = Color(0xFF06281E), fontWeight = FontWeight.Bold)
                }
            } else if (release.downloadUrl.isNotBlank() || release.assetApiUrl.isNotBlank()) {
                Button(
                    enabled = !isDownloadingUpdate,
                    onClick = {
                        onSetError(null)
                        onSetProgress(0, "")
                        onSetDownloading(true)
                        coroutineScope.launch {
                            val res = updateManager.downloadAndInstallApk(
                                context = context,
                                downloadUrl = release.downloadUrl.ifBlank { release.assetApiUrl },
                                releasePageUrl = release.releasePageUrl,
                                fallbackAssetApiUrl = release.assetApiUrl,
                                onProgress = { p ->
                                    val dlMb = String.format(Locale.US, "%.1f MB", p.downloadedBytes / (1024f * 1024f))
                                    val mbText = if (p.totalBytes > 0) {
                                        val totalMb = String.format(Locale.US, "%.1f MB", p.totalBytes / (1024f * 1024f))
                                        "$dlMb / $totalMb"
                                    } else dlMb
                                    onSetProgress(p.percent, mbText)
                                }
                            )
                            onSetDownloading(false)
                            if (res.isFailure) {
                                val msg = res.exceptionOrNull()?.message ?: "Download failed"
                                onSetError(msg)
                                Toast.makeText(context, "Update error: $msg", Toast.LENGTH_LONG).show()
                            } else {
                                when (val outcome = res.getOrThrow()) {
                                    is ApkUpdateResult.LaunchedInstaller -> {
                                        onSetPendingApk(null)
                                        onDismiss()
                                    }
                                    is ApkUpdateResult.NeedsUnknownSourcesPermission -> {
                                        onSetPendingApk(outcome.apkFile)
                                        onSetError("'Allow from this source' ON karke app par wapas aayein — install apne aap start ho jayega.")
                                        Toast.makeText(
                                            context,
                                            "Enable 'Allow from this source' to install update.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    is ApkUpdateResult.SignatureConflict -> {
                                        onSetPendingApk(null)
                                        onSetError(outcome.message)
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary)
                ) {
                    Text(
                        if (isDownloadingUpdate) "Downloading..." else "Update Now",
                        color = themeColors.onPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(release.releasePageUrl))
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary)
                ) {
                    Text("Open Releases", color = themeColors.onPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDownloadingUpdate
            ) {
                Text("Later", color = themeColors.textSecondary)
            }
        },
        containerColor = themeColors.surfaceElevated
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerBottomSheet(
    onPickPhotos: () -> Unit,
    onPickVideos: () -> Unit,
    onPickFiles: () -> Unit,
    onDismiss: () -> Unit
) {
    val themeColors = JarvisTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = themeColors.surfaceElevated,
        contentColor = themeColors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Attach Media & Files",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = themeColors.textPrimary
            )
            Spacer(modifier = Modifier.height(14.dp))
            AttachmentSheetRow("Photos", "Pick 1–5 photos from gallery (Zero-permission Photo Picker)") {
                onDismiss()
                onPickPhotos()
            }
            AttachmentSheetRow("Videos", "Pick videos to analyze frames or video stream") {
                onDismiss()
                onPickVideos()
            }
            AttachmentSheetRow("Files & Documents", "PDF, Code, TXT, JSON, Markdown, ZIP & more") {
                onDismiss()
                onPickFiles()
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun AttachmentSheetRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val themeColors = JarvisTheme.colors
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = themeColors.cardSurface,
        border = BorderStroke(1.dp, themeColors.border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = themeColors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                Text(text = subtitle, color = themeColors.textSecondary, fontSize = 11.5.sp)
            }
        }
    }
}

@Composable
fun TermuxBridgeSetupDialog(
    refreshKey: Int,
    isLinuxRootfsInstalled: Boolean,
    onRefreshDiagnostics: () -> Unit,
    onRequestRunCommandPermission: () -> Unit,
    onOpenWirelessAdb: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val termuxBridge = remember(refreshKey) { JarvisTermuxBridge(context) }
    var diag by remember(refreshKey) { mutableStateOf(termuxBridge.getDiagnostics()) }
    val adbState by AdbConnectManager.deviceState.collectAsState()

    LaunchedEffect(refreshKey) {
        AdbConnectManager.init(context)
        AdbConnectManager.checkAdbInstalled()
        withContext(Dispatchers.IO) {
            val updated = termuxBridge.getDiagnostics()
            withContext(Dispatchers.Main) {
                diag = updated
            }
        }
    }

    val themeColors = JarvisTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = themeColors.surfaceElevated,
        titleContentColor = themeColors.textPrimary,
        textContentColor = themeColors.textSecondary,
        title = {
            Text(
                text = "Termux Bridge & Android 16 Setup",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "1) Built-in Agentra Linux Engine: " +
                        if (isLinuxRootfsInstalled) "Active (0 permissions needed)"
                        else "Pending setup (tap Linux Core Engine in Drawer)",
                    fontSize = 12.sp,
                    color = if (isLinuxRootfsInstalled) themeColors.statusEmerald else themeColors.statusAmber
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "2) Android 16 Termux Isolated Bridge (:2238): " +
                        if (diag.isLocalBridgeActive) "Connected (Full Command & File Access, Isolated Agentra Runtime)"
                        else "1-Tap Setup Available Below",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (diag.isLocalBridgeActive) themeColors.statusEmerald else themeColors.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "3) All Files Access (Storage): " +
                        if (diag.hasAllFilesAccess) "Granted" else "Not Granted",
                    fontSize = 12.sp,
                    color = if (diag.hasAllFilesAccess) themeColors.statusEmerald else themeColors.statusAmber
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "4) External Termux App: " +
                        when {
                            !diag.isTermuxInstalled -> "Not installed"
                            diag.isPlayStoreTermux -> "Play Store Build (Blocks External)"
                            diag.hasRunCommandPermission -> "Installed + RUN_COMMAND Granted"
                            else -> "Installed (Android 16 hides Settings toggle)"
                        },
                    fontSize = 12.sp,
                    color = when {
                        !diag.isTermuxInstalled -> themeColors.statusRoseRed
                        diag.isPlayStoreTermux -> themeColors.statusAmber
                        else -> themeColors.statusEmerald
                    }
                )
                if (diag.blockingReasonSummary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = themeColors.cardSurface,
                        border = BorderStroke(
                            1.dp,
                            if (diag.hasRunCommandPermission || diag.isLocalBridgeActive) themeColors.statusEmerald.copy(alpha = 0.45f)
                            else themeColors.borderHighlight
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = diag.blockingReasonSummary,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = themeColors.textPrimary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                "Android 16 Termux Bridge Command",
                                JarvisTermuxBridge.buildAndroid16BridgeCommand(context)
                            )
                        )
                        Toast.makeText(
                            context,
                            "Command Copied! Ab Termux me Paste karke Enter dabayein — bina kisi Settings toggle ke Termux connect ho jayega!",
                            Toast.LENGTH_LONG
                        ).show()
                        runCatching {
                            val launch = context.packageManager.getLaunchIntentForPackage(
                                JarvisTermuxBridge.TERMUX_PACKAGE
                            )
                            if (launch != null) context.startActivity(launch)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeColors.statusEmerald),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("termux_copy_config_button")
                ) {
                    Text(
                        "1-Tap Android 16 Termux Bridge (Copy & Open)",
                        color = themeColors.onPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                if (!diag.hasAllFilesAccess) {
                    Button(
                        onClick = { com.example.MainActivity.openAllFilesAccessSettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("termux_all_files_access_button")
                    ) {
                        Text(
                            "Grant All Files Access (Allow Files)",
                            color = themeColors.onPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (!diag.isTermuxInstalled || diag.isPlayStoreTermux || !diag.isPermissionDeclaredByTermux) {
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/"))
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColors.statusAmber),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("termux_fdroid_download_button")
                    ) {
                        Text(
                            "Download Official F-Droid Termux APK",
                            color = themeColors.onPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (adbState.isConnected && !diag.hasRunCommandPermission) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val res = AdbConnectManager.unblockAndEnableAccessibility(context)
                                onRefreshDiagnostics()
                                Toast.makeText(
                                    context,
                                    res.getOrElse { it.message ?: "ADB grant failed" },
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColors.statusEmerald),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("termux_adb_auto_grant_button")
                    ) {
                        Text(
                            "1-Tap Auto-Grant via Connected ADB",
                            color = themeColors.onPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                OutlinedButton(
                    onClick = onRequestRunCommandPermission,
                    border = BorderStroke(1.dp, themeColors.primary.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("termux_request_permission_button")
                ) {
                    Text("Request RUN_COMMAND Popup", color = themeColors.primary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        Toast.makeText(
                            context,
                            "App Info screen (jahan Uninstall button hai) par top-right (⋮) -> 'Allow restricted settings' dabayein.",
                            Toast.LENGTH_LONG
                        ).show()
                        com.example.MainActivity.openTermuxPermissionSettings(context)
                    },
                    border = BorderStroke(1.dp, themeColors.borderHighlight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("termux_open_settings_button")
                ) {
                    Text("Open App Info (⋮ Allow Restricted)", color = themeColors.primary, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onOpenWirelessAdb,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("termux_open_wireless_adb_button")
                ) {
                    Text(
                        text = if (adbState.isConnected) "Wireless ADB Connected • Open Panel" else "Setup Wireless ADB (No PC Auto-Grant)",
                        color = if (adbState.isConnected) themeColors.statusEmerald else themeColors.textSecondary,
                        fontSize = 11.5.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = themeColors.primary)
            }
        }
    )
}
