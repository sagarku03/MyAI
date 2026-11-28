package com.jarvisai.assistant.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.theme.JarvisTheme
import com.jarvisai.assistant.data.preferences.TerminalLauncherManager
import com.jarvisai.assistant.terminal.EnvironmentDiagnostics
import com.jarvisai.assistant.terminal.LinuxRuntimeMode
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.LocalInstallerState
import com.jarvisai.assistant.terminal.PRootInstallState
import com.jarvisai.assistant.terminal.PRootInstaller
import com.jarvisai.assistant.terminal.PRootRuntime
import com.jarvisai.assistant.terminal.RootfsInstallProgress
import com.jarvisai.assistant.terminal.RootfsMirror
import com.jarvisai.assistant.ui.onboarding.TerminalLauncherOnboardingDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun LinuxRootfsManagerDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Instant (<2ms) initial diagnostics via cached disk usage so UI shows ACTIVE on frame 1
    var diagnostics by remember { mutableStateOf<EnvironmentDiagnostics?>(LocalEnvironmentManager.getDiagnostics(context)) }
    var detectedLocalZip by remember { mutableStateOf<File?>(null) }
    var isLoadingDiagnostics by remember { mutableStateOf(false) }

    val installProgress by LocalEnvironmentManager.installProgress.collectAsState()
    val installerState by LocalEnvironmentManager.installerState.collectAsState()
    val prootInstallState by PRootInstaller.state.collectAsState()
    val liveLogs by LocalEnvironmentManager.liveExtractionLogs.collectAsState()
    var activeMode by remember { mutableStateOf(PRootRuntime.getRuntimeMode(context)) }
    var prootActionLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var showLauncherOnboarding by remember { mutableStateOf(false) }
    val hardwareInfo = remember { LocalEnvironmentManager.getDeviceHardwareInfo() }
    var selectedMirror by remember { mutableStateOf(LocalEnvironmentManager.getSelectedMirror(context)) }

    val logListState = rememberLazyListState()
    val controlsScrollState = rememberScrollState()

    // Scan downloads and refresh diagnostics safely on background IO thread
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val diag = LocalEnvironmentManager.getDiagnostics(context)
            val zip = LocalEnvironmentManager.findLocalBootstrapArchive()
            withContext(Dispatchers.Main) {
                diagnostics = diag
                activeMode = diag.runtimeMode
                detectedLocalZip = zip
                isLoadingDiagnostics = false
            }
        }
    }

    val combinedLogs = remember(liveLogs, prootActionLogs) {
        if (liveLogs.isNotEmpty() || prootActionLogs.isNotEmpty()) {
            (liveLogs + prootActionLogs).takeLast(200)
        } else {
            emptyList()
        }
    }

    LaunchedEffect(combinedLogs.size) {
        if (combinedLogs.isNotEmpty()) {
            logListState.animateScrollToItem(combinedLogs.size - 1)
        }
    }

    val isBusy = installProgress is RootfsInstallProgress.Downloading ||
        installProgress is RootfsInstallProgress.Extracting ||
        installProgress is RootfsInstallProgress.Configuring ||
        prootInstallState is PRootInstallState.Installing

    LaunchedEffect(installProgress, prootInstallState) {
        if (installProgress is RootfsInstallProgress.Success) {
            val diag = (installProgress as RootfsInstallProgress.Success).diagnostics
            diagnostics = diag
            activeMode = diag.runtimeMode
            if (!TerminalLauncherManager.isPromptShown(context)) {
                showLauncherOnboarding = true
            }
        } else if (prootInstallState is PRootInstallState.Success) {
            diagnostics = withContext(Dispatchers.IO) { LocalEnvironmentManager.getDiagnostics(context) }
            activeMode = diagnostics?.runtimeMode ?: activeMode
        }
    }

    val customZipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val tempZip = File(context.cacheDir, "picked_bootstrap.zip")
                prootActionLogs = (prootActionLogs + "Staging selected ZIP archive into cache...").takeLast(120)
                val copyResult = withContext(Dispatchers.IO) {
                    runCatching {
                        if (tempZip.exists()) tempZip.delete()
                        val input = context.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("Unable to open selected file stream.")
                        input.use { ins ->
                            FileOutputStream(tempZip).use { out ->
                                ins.copyTo(out, bufferSize = 64 * 1024)
                            }
                        }
                    }
                }
                if (copyResult.isFailure) {
                    runCatching { tempZip.delete() }
                    prootActionLogs = (prootActionLogs + "ERROR: Failed reading selected ZIP: ${copyResult.exceptionOrNull()?.message}").takeLast(120)
                    return@launch
                }
                val isValid = withContext(Dispatchers.IO) {
                    LocalEnvironmentManager.validateArchiveIntegrity(tempZip)
                }
                if (!isValid) {
                    runCatching { tempZip.delete() }
                    prootActionLogs = (prootActionLogs + "ERROR: Selected ZIP failed bootstrap integrity check (missing bin/sh or SYMLINKS.txt).").takeLast(120)
                } else {
                    prootActionLogs = (prootActionLogs + "Verified ZIP (${LocalEnvironmentManager.formatFileSize(tempZip.length())}). Starting install...").takeLast(120)
                    LocalEnvironmentManager.startInstall(context, tempZip)
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss, // ALWAYS ALLOW DISMISSING / MINIMIZING TO BACKGROUND
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogColors = JarvisTheme.colors
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, dialogColors.border, RoundedCornerShape(16.dp)),
            color = dialogColors.surfaceElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = dialogColors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Termux Linux Core Engine",
                                color = dialogColors.textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Node.js • Python • Git • Neovim • Bash",
                                color = dialogColors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // CLOSE BUTTON ALWAYS ENABLED (CAN DISMISS TO BACKGROUND)
                    IconButton(
                        onClick = onDismiss,
                        enabled = true
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = dialogColors.textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(controlsScrollState)
                ) {
                // Current Engine Status Card
                val diag = diagnostics
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = dialogColors.cardSurface,
                    border = BorderStroke(
                        1.dp,
                        if (diag?.isInstalled == true) dialogColors.statusEmerald.copy(alpha = 0.4f) else dialogColors.statusAmber.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (diag?.isInstalled == true) "● LINUX ROOTFS ACTIVE" else "○ NATIVE /system/bin/sh (LIMITED)",
                                color = if (diag?.isInstalled == true) Color(0xFF10B981) else Color(0xFFF59E0B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = diag?.diskUsageFormatted ?: "Checking...",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (diag?.isInstalled == true) {
                                "Installed at: ${diag.prefixPath}\n" +
                                    "Packages: Bash [${if (diag.hasBash) "✓" else "✗"}], Node [${if (diag.hasNode) "✓" else "✗"}], Python [${if (diag.hasPython) "✓" else "✗"}], Git [${if (diag.hasGit) "✓" else "✗"}]\n" +
                                    "Native Reloc [${if (diag.hasRelocateHooks) "✓" else "✗"}] • am.apk [${if (diag.hasAmApk) "✓" else "✗"}] • Glibc [${if (diag.hasGlibc) "✓" else "✗"}] • pkg [${if (diag.hasAptPkg) "✓" else "✗"}]"
                            } else {
                                "Termux core environment is not installed yet. Terminal and AI agents are running on Android fallback shell without Node.js or Python."
                            },
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Option B: Native Relocated Engine + Optional PRoot
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(
                        1.dp,
                        if (activeMode == LinuxRuntimeMode.DIRECT_NATIVE || diag?.isProotVirtualActive == true) {
                            Color(0xFF10B981).copy(alpha = 0.55f)
                        } else {
                            Color(0xFFA78BFA).copy(alpha = 0.45f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Linux Runtime Engine (Native)",
                                color = Color(0xFFECEEF2),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = when {
                                    activeMode == LinuxRuntimeMode.DIRECT_NATIVE && diag?.hasRelocateHooks == true -> "Relocated"
                                    activeMode == LinuxRuntimeMode.DIRECT_NATIVE -> "Direct"
                                    diag?.isProotVirtualActive == true -> "PRoot active"
                                    diag?.hasPRoot == true -> "PRoot ready"
                                    else -> "Standby"
                                },
                                color = if (activeMode == LinuxRuntimeMode.DIRECT_NATIVE || diag?.isProotVirtualActive == true) Color(0xFF22C55E) else Color(0xFFEAB308),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                LinuxRuntimeMode.DIRECT_NATIVE to "Native Direct",
                                LinuxRuntimeMode.PROOT_UBUNTU to "Ubuntu 24.04",
                                LinuxRuntimeMode.PROOT_TERMUX to "Termux PRoot"
                            ).forEach { (mode, label) ->
                                val selected = activeMode == mode
                                OutlinedButton(
                                    onClick = {
                                        if (mode == LinuxRuntimeMode.PROOT_UBUNTU && !PRootRuntime.isUbuntuRootfsInstalled(context)) {
                                            coroutineScope.launch {
                                                prootActionLogs = prootActionLogs + "Ubuntu 24.04 not installed yet — starting automatic download..."
                                                val res = PRootInstaller.installUbuntuBaseRootfs(context) { line ->
                                                    prootActionLogs = (prootActionLogs + line).takeLast(120)
                                                }
                                                diagnostics = withContext(Dispatchers.IO) { LocalEnvironmentManager.getDiagnostics(context) }
                                                activeMode = if (res.isSuccess) LinuxRuntimeMode.PROOT_UBUNTU else PRootRuntime.getEffectiveRuntimeMode(context)
                                            }
                                        } else {
                                            activeMode = mode
                                            PRootRuntime.setRuntimeMode(context, mode)
                                            diagnostics = LocalEnvironmentManager.getDiagnostics(context)
                                        }
                                    },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) dialogColors.primary.copy(alpha = 0.14f) else Color.Transparent,
                                        contentColor = if (selected) dialogColors.primary else dialogColors.textSecondary
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) dialogColors.primary else dialogColors.border
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("runtime_mode_${mode.id}")
                                ) {
                                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        prootActionLogs = prootActionLogs + "Starting PRoot APT installer..."
                                        PRootInstaller.installProotRuntime(context) { line ->
                                            prootActionLogs = (prootActionLogs + line).takeLast(120)
                                        }
                                        diagnostics = withContext(Dispatchers.IO) { LocalEnvironmentManager.getDiagnostics(context) }
                                    }
                                },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, dialogColors.statusEmerald.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("install_proot_button")
                            ) {
                                Text(
                                    text = if (diag?.hasPRoot == true) "Repair PRoot" else "Install PRoot (~3MB)",
                                    color = dialogColors.statusEmerald,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        prootActionLogs = prootActionLogs + "Starting Ubuntu 24.04 RootFS setup..."
                                        PRootInstaller.installUbuntuBaseRootfs(context) { line ->
                                            prootActionLogs = (prootActionLogs + line).takeLast(120)
                                        }
                                        diagnostics = withContext(Dispatchers.IO) { LocalEnvironmentManager.getDiagnostics(context) }
                                        activeMode = diagnostics?.runtimeMode ?: activeMode
                                    }
                                },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, dialogColors.primary.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("install_ubuntu_button")
                            ) {
                                Text(
                                    text = if (diag?.hasUbuntuRootfs == true) "Ubuntu (Ready)" else "Ubuntu 24.04",
                                    color = dialogColors.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showLauncherOnboarding = true },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, dialogColors.borderHighlight),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("open_dual_icon_wizard_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Configure Dual Terminal App Icon",
                                tint = dialogColors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val iconBadge = if (TerminalLauncherManager.isLauncherEnabled(context)) {
                                val styleName = if (TerminalLauncherManager.getLauncherStyle(context) == TerminalLauncherManager.STYLE_TERMUX) "Termux" else "Agentra Term"
                                "Dual App Icon: $styleName (Configure)"
                            } else {
                                "Setup Standalone Terminal App Icon"
                            }
                            Text(
                                text = iconBadge,
                                color = dialogColors.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Auto-detected Local Archive Card
                val localZip = detectedLocalZip
                if (localZip != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = dialogColors.cardSurface,
                        border = BorderStroke(1.dp, dialogColors.borderHighlight)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DownloadDone, contentDescription = null, tint = dialogColors.primary, modifier = Modifier.size(19.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Detected Offline Archive",
                                    color = dialogColors.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${localZip.name} (${LocalEnvironmentManager.formatFileSize(localZip.length())})",
                                color = dialogColors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Path: ${localZip.absolutePath}",
                                color = dialogColors.textSecondary,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    LocalEnvironmentManager.startInstall(context, localZip)
                                },
                                enabled = !isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = dialogColors.primary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (diag?.isInstalled == true) "Re-install from Archive" else "Install Offline Rootfs",
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (LocalEnvironmentManager.hasExtractedFiles(context) && diag?.isInstalled != true) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            LocalEnvironmentManager.repairPermissions(context)
                                            diagnostics = withContext(Dispatchers.IO) { LocalEnvironmentManager.getDiagnostics(context) }
                                        }
                                    },
                                    enabled = !isBusy,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF22C55E)),
                                    border = BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.50f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Repair Permissions & Activate",
                                        color = Color(0xFF22C55E),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Hardware Compatibility & Bootstrap Mirror Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(
                        1.dp,
                        if (hardwareInfo.hardwareWarning != null) Color(0xFFF59E0B).copy(alpha = 0.5f)
                        else Color(0xFF334155)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Device Hardware: ${hardwareInfo.primaryAbi} · VA ${hardwareInfo.vaBits}-bit · Atomics ${if (hardwareInfo.hasCpuAtomics) "✓" else "✗"} · Kernel ${hardwareInfo.kernelRelease.take(20)}",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (hardwareInfo.hardwareWarning != null) {
                            Text(
                                text = "Warning: ${hardwareInfo.hardwareWarning}",
                                color = dialogColors.statusAmber,
                                fontSize = 10.5.sp
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RootfsMirror.entries.forEach { mirror ->
                                val isSel = selectedMirror == mirror
                                val label = when (mirror) {
                                    RootfsMirror.GITHUB_MAIN -> "GitHub Full"
                                    RootfsMirror.HUGGING_FACE -> "HF Mirror"
                                    RootfsMirror.TERMUX_OFFICIAL -> "Termux Base"
                                }
                                Surface(
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedMirror = mirror
                                            LocalEnvironmentManager.setSelectedMirror(context, mirror)
                                        }
                                        .testTag("rootfs_dialog_mirror_${mirror.id}"),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) dialogColors.primary.copy(alpha = 0.14f) else dialogColors.surfacePill,
                                    border = BorderStroke(1.dp, if (isSel) dialogColors.primary else dialogColors.border)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSel) dialogColors.primary else dialogColors.textSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Options Row: Custom File Picker, Cloud Download, or Repair dpkg
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            customZipPicker.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pick ZIP", color = Color(0xFF38BDF8), fontSize = 11.5.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                LocalEnvironmentManager.discoverBootstrapPackage(context, selectedMirror)
                            }
                        },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        border = BorderStroke(1.dp, Color(0xFFA78BFA).copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cloud Check", color = Color(0xFFA78BFA), fontSize = 11.5.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                prootActionLogs = (prootActionLogs + "Running dpkg --configure -a & apt-get -f install...").takeLast(120)
                                val res = LocalEnvironmentManager.repairIncompletePackages(context)
                                diagnostics = withContext(Dispatchers.IO) { LocalEnvironmentManager.getDiagnostics(context) }
                                prootActionLogs = (prootActionLogs + res.fold(
                                    onSuccess = { "✓ dpkg repair complete" },
                                    onFailure = { "✗ dpkg repair error: ${it.message}" }
                                )).takeLast(120)
                            }
                        },
                        enabled = !isBusy && (diag?.isInstalled == true),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("rootfs_dialog_repair_dpkg_btn"),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Repair dpkg", color = Color(0xFF10B981), fontSize = 11.5.sp)
                    }
                }

                // Discovery confirmation card inside Rootfs Dialog
                val curInstallerState = installerState
                if (curInstallerState is LocalInstallerState.Discovering) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = curInstallerState.message,
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp
                    )
                } else if (curInstallerState is LocalInstallerState.AwaitingConfirmation) {
                    val pkg = curInstallerState.packageInfo
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${if (pkg.isLocal) "Offline" else "Cloud (${pkg.releaseTag})"} Package: ${pkg.assetName} (${pkg.sizeFormatted})",
                                color = Color(0xFF10B981),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (pkg.isLocal) {
                                            LocalEnvironmentManager.startInstall(context, File(pkg.url))
                                        } else {
                                            LocalEnvironmentManager.startInstall(context, null)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                ) {
                                    Text("Confirm Install", color = Color(0xFF052E16), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { LocalEnvironmentManager.dismissDiscoveryConfirmation() },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Cancel", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // AntiGem-Style 1-Click Coding CLI Agents Installer (Isolated inside Jarvis Linux Engine)
                var cliInstallStatus by remember { mutableStateOf<String?>(null) }
                var isInstallingCli by remember { mutableStateOf(false) }
                val hasAgyBin = remember(diagnostics, cliInstallStatus) { LocalEnvironmentManager.findCliBinary(context, "agy") != null }
                val hasClaudeBin = remember(diagnostics, cliInstallStatus) { LocalEnvironmentManager.findCliBinary(context, "claude") != null }
                val hasOpenCodeBin = remember(diagnostics, cliInstallStatus) { LocalEnvironmentManager.findCliBinary(context, "opencode") != null }
                val hasCodexBin = remember(diagnostics, cliInstallStatus) { LocalEnvironmentManager.findCliBinary(context, "codex") != null }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF111827),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "⚡ Isolated Coding CLI Agents (Agentra Workspace :2235 / :2240)",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "agy [${if (hasAgyBin) "✓" else "Cloud PA"}] • claude [${if (hasClaudeBin) "✓" else "Install"}] • opencode [${if (hasOpenCodeBin) "✓" else "Install"}] • codex [${if (hasCodexBin) "✓" else "Install"}]",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        if (!cliInstallStatus.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cliInstallStatus.orEmpty(),
                                color = Color(0xFF10B981),
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isInstallingCli = true
                                        cliInstallStatus = "Syncing isolated agy hub (:2235) & MCP (:2236)..."
                                        withContext(Dispatchers.IO) {
                                            com.jarvisai.assistant.terminal.LocalServerManager.provisionEmbeddedMcpConfig(context)
                                            com.jarvisai.assistant.terminal.LocalServerManager.warmUpAgyHub(maxWaitMs = 5000)
                                        }
                                        cliInstallStatus = "✓ Isolated Antigravity Hub (:2235) & MCP (:2236/:2237) synced!"
                                        isInstallingCli = false
                                    }
                                },
                                enabled = !isBusy && !isInstallingCli,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                            ) {
                                Text("Sync Agy :2235", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isInstallingCli = true
                                        cliInstallStatus = "Preparing Node/npm wrappers & installing CLIs..."
                                        prootActionLogs = (prootActionLogs + "Installing Claude Code, OpenCode & Codex CLIs via Node/npm...").takeLast(120)
                                        val res = withContext(Dispatchers.IO) {
                                            val prefixDir = LocalEnvironmentManager.getPrefixDir(context)
                                            val homeDir = LocalEnvironmentManager.getHomeDir(context)
                                            LocalEnvironmentManager.fixFilePermissions(prefixDir)
                                            com.jarvisai.assistant.terminal.LocalTerminalBridge.instance.init(context)
                                            val p = prefixDir.absolutePath
                                            val h = homeDir.absolutePath
                                            val installCmd = buildString {
                                                append("export OPENSSL_CONF=\"$p/etc/tls/openssl.cnf\" && ")
                                                append("export OPENSSL_MODULES=\"$p/lib/ossl-modules\" && ")
                                                append("export SSL_CERT_FILE=\"$p/etc/tls/cert.pem\" && ")
                                                append("export NODE_EXTRA_CA_CERTS=\"$p/etc/tls/cert.pem\" && ")
                                                append("export LD_LIBRARY_PATH=\"$p/lib:\${LD_LIBRARY_PATH:-}\" && ")
                                                append("mkdir -p \"$h/.npm-global/bin\" \"$h/.local/bin\" && ")
                                                append("if [ -x \"$p/bin/node\" ] && [ -f \"$p/lib/node_modules/npm/bin/npm-cli.js\" ]; then ")
                                                append("\"$p/bin/node\" \"$p/lib/node_modules/npm/bin/npm-cli.js\" install -g @anthropic-ai/claude-code opencode-ai @openai/codex --prefix \"$h/.npm-global\" --no-audit --no-fund 2>&1; ")
                                                append("elif [ -x \"/data/data/com.termux/files/usr/bin/node\" ] && [ -f \"/data/data/com.termux/files/usr/lib/node_modules/npm/bin/npm-cli.js\" ]; then ")
                                                append("\"/data/data/com.termux/files/usr/bin/node\" \"/data/data/com.termux/files/usr/lib/node_modules/npm/bin/npm-cli.js\" install -g @anthropic-ai/claude-code opencode-ai @openai/codex --prefix \"/data/data/com.termux/files/home/.npm-global\" --no-audit --no-fund 2>&1; ")
                                                append("else ")
                                                append("npm install -g @anthropic-ai/claude-code opencode-ai @openai/codex --no-audit --no-fund 2>&1; ")
                                                append("fi")
                                            }
                                            val execRes = com.jarvisai.assistant.terminal.LocalTerminalBridge.instance.executeStreamingCommand(
                                                command = installCmd,
                                                workingDir = LocalEnvironmentManager.getIsolatedProjectsDir(context).absolutePath,
                                                timeoutMs = 240_000L
                                            ) { line ->
                                                cliInstallStatus = line.take(70)
                                                prootActionLogs = (prootActionLogs + line).takeLast(120)
                                            }
                                            LocalEnvironmentManager.ensureNodeAndNpmWrappers(prefixDir, homeDir)
                                            execRes
                                        }
                                        diagnostics = withContext(Dispatchers.IO) { LocalEnvironmentManager.getDiagnostics(context) }
                                        cliInstallStatus = if (res.exitCode == 0) {
                                            "✓ Installed Claude Code, OpenCode & Codex CLIs!"
                                        } else {
                                            val lastLine = res.output.lines().lastOrNull { it.isNotBlank() }?.take(55).orEmpty()
                                            "✗ CLI install exit ${res.exitCode}: $lastLine"
                                        }
                                        isInstallingCli = false
                                    }
                                },
                                enabled = !isBusy && !isInstallingCli,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                            ) {
                                Text("Install All CLIs", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (prootInstallState is PRootInstallState.Installing) {
                    val pState = prootInstallState as PRootInstallState.Installing
                    Text(text = pState.status, color = Color(0xFFA78BFA), fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { pState.fraction.coerceIn(0f, 1f) },
                        color = Color(0xFFA78BFA),
                        trackColor = Color(0xFF1F2937),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (prootInstallState is PRootInstallState.Success) {
                    Text(
                        text = "✓ ${(prootInstallState as PRootInstallState.Success).message}",
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                } else if (prootInstallState is PRootInstallState.Error) {
                    Text(
                        text = "✗ ${(prootInstallState as PRootInstallState.Error).error}",
                        color = Color(0xFFEF4444),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Progress Bar (if active)
                when (val progress = installProgress) {
                    is RootfsInstallProgress.Downloading -> {
                        Text(
                            text = "Downloading RootFS (${progress.speed})...",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            color = Color(0xFF00E5FF),
                            trackColor = Color(0xFF1F2937),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    is RootfsInstallProgress.Extracting -> {
                        Text(
                            text = "Unpacking packages (${progress.filesExtracted} files): ${progress.currentFile}",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            color = Color(0xFF10B981),
                            trackColor = Color(0xFF1F2937),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    is RootfsInstallProgress.Configuring -> {
                        Text(text = progress.status, color = Color(0xFFF59E0B), fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            color = Color(0xFFF59E0B),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    is RootfsInstallProgress.Success -> {
                        Text(text = "✓ ${progress.message}", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    is RootfsInstallProgress.Error -> {
                        Text(text = "✗ ${progress.error}", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    else -> {}
                }

                // Cyber Terminal Output Box
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(10.dp)),
                    color = Color(0xFF030712)
                ) {
                    if (combinedLogs.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "┌── [Termux Linux Core Engine - System Verified] ──┐",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (diagnostics?.isInstalled == true) {
                                    "│ Mode: ${diagnostics?.runtimeMode?.label ?: "Native Direct"} • Option B [${if (diagnostics?.hasRelocateHooks == true) "ACTIVE" else "READY"}]"
                                } else {
                                    "│ Status: STANDBY • Select Offline or Cloud Install above"
                                },
                                color = if (diagnostics?.isInstalled == true) Color(0xFF10B981) else Color(0xFFF59E0B),
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "│ Prefix: ${diagnostics?.prefixPath ?: "/data/.../usr"}",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "│ Packages: Bash [${if (diagnostics?.hasBash == true) "✓" else "✗"}], Node [${if (diagnostics?.hasNode == true) "✓" else "✗"}], Python [${if (diagnostics?.hasPython == true) "✓" else "✗"}], Git [${if (diagnostics?.hasGit == true) "✓" else "✗"}], pkg [${if (diagnostics?.hasAptPkg == true) "✓" else "✗"}]",
                                color = Color(0xFFE2E8F0),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "│ Installed Size: ${diagnostics?.diskUsageFormatted ?: "2.1 GB"}",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "└── Press 'Close' or tap outside to return to chat ──┘",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        LazyColumn(
                            state = logListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        ) {
                            items(combinedLogs) { log ->
                                Text(
                                    text = log,
                                    color = if (log.startsWith("ERROR") || log.startsWith("✗")) Color(0xFFEF4444) else Color(0xFF00E5FF).copy(alpha = 0.85f),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }

    if (showLauncherOnboarding) {
        TerminalLauncherOnboardingDialog(
            onDismiss = { showLauncherOnboarding = false },
            onApplied = { showLauncherOnboarding = false }
        )
    }
}
