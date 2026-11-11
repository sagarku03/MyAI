package com.jarvisai.assistant.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.jarvisai.assistant.terminal.EnvironmentDiagnostics
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.RootfsInstallProgress
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

    // ASYNC STATE: Initialized as null so Compose renders INSTANTLY (0 freeze, 0 ANR)
    var diagnostics by remember { mutableStateOf<EnvironmentDiagnostics?>(null) }
    var detectedLocalZip by remember { mutableStateOf<File?>(null) }
    var isLoadingDiagnostics by remember { mutableStateOf(true) }

    val installProgress by LocalEnvironmentManager.installProgress.collectAsState()
    val liveLogs by LocalEnvironmentManager.liveExtractionLogs.collectAsState()

    val logListState = rememberLazyListState()

    // Load diagnostics and scan downloads safely on background IO thread
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val diag = LocalEnvironmentManager.getDiagnostics(context)
            val zip = LocalEnvironmentManager.findLocalBootstrapArchive()
            withContext(Dispatchers.Main) {
                diagnostics = diag
                detectedLocalZip = zip
                isLoadingDiagnostics = false
            }
        }
    }

    LaunchedEffect(liveLogs.size) {
        if (liveLogs.isNotEmpty()) {
            logListState.animateScrollToItem(liveLogs.size - 1)
        }
    }

    val isBusy = installProgress is RootfsInstallProgress.Downloading ||
        installProgress is RootfsInstallProgress.Extracting ||
        installProgress is RootfsInstallProgress.Configuring

    LaunchedEffect(installProgress) {
        if (installProgress is RootfsInstallProgress.Success) {
            diagnostics = (installProgress as RootfsInstallProgress.Success).diagnostics
        }
    }

    val customZipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val tempZip = File(context.cacheDir, "picked_bootstrap.zip")
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempZip).use { output -> input.copyTo(output) }
                    }
                }
                LocalEnvironmentManager.startInstall(context, tempZip)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss, // ALWAYS ALLOW DISMISSING / MINIMIZING TO BACKGROUND
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
            color = Color(0xFF0A0E17)
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
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Termux Linux Core Engine",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Node.js • Python • Git • Neovim • Bash",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // CLOSE BUTTON ALWAYS ENABLED (CAN DISMISS TO BACKGROUND)
                    IconButton(
                        onClick = onDismiss,
                        enabled = true
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Current Engine Status Card
                val diag = diagnostics
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF111827),
                    border = BorderStroke(
                        1.dp,
                        if (diag?.isInstalled == true) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFF59E0B).copy(alpha = 0.4f)
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
                                "Installed at: ${diag.prefixPath}\nAvailable: Bash [✓], Node [${if (diag.hasNode) "✓" else "✗"}], Python [${if (diag.hasPython) "✓" else "✗"}], Git [${if (diag.hasGit) "✓" else "✗"}]"
                            } else {
                                "Termux core environment is not installed yet. Terminal and AI agents are running on Android fallback shell without Node.js or Python."
                            },
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Auto-detected Local Archive Card
                val localZip = detectedLocalZip
                if (localZip != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DownloadDone, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Detected Offline Archive",
                                    color = Color(0xFF00E5FF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${localZip.name} (${LocalEnvironmentManager.formatFileSize(localZip.length())})",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Path: ${localZip.absolutePath}",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    LocalEnvironmentManager.startInstall(context, localZip)
                                },
                                enabled = !isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF0A0E17))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (diag?.isInstalled == true) "Re-install from Offline Archive" else "⚡ 1-Click Install Offline Rootfs",
                                    color = Color(0xFF0A0E17),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Instant 0.5s Permission Repair for existing files
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
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981)),
                                    border = BorderStroke(1.dp, Color(0xFF10B981)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "⚡ Repair Permissions & Activate (Instant)",
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Options Row: Custom File Picker or Cloud Download
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { customZipPicker.launch("application/zip") },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick ZIP...", color = Color(0xFF38BDF8), fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            LocalEnvironmentManager.startInstall(context, null)
                        },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color(0xFFA78BFA).copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download (Cloud)", color = Color(0xFFA78BFA), fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

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
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(10.dp)),
                    color = Color(0xFF030712)
                ) {
                    LazyColumn(
                        state = logListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    ) {
                        items(liveLogs) { log ->
                            Text(
                                text = log,
                                color = if (log.startsWith("ERROR")) Color(0xFFEF4444) else Color(0xFF00E5FF).copy(alpha = 0.8f),
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
