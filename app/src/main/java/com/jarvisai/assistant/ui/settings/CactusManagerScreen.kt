package com.jarvisai.assistant.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.cactus.CactusComputeBackend
import com.jarvisai.assistant.cactus.CactusModelCapability
import com.jarvisai.assistant.cactus.CactusModelCatalog
import com.jarvisai.assistant.cactus.CactusModelInstallState
import com.jarvisai.assistant.cactus.CactusModelRuntime
import com.jarvisai.assistant.cactus.CactusModelSpec
import com.jarvisai.assistant.cactus.CactusNativeRuntime
import com.jarvisai.assistant.cactus.CactusVectorMemoryIndex
import com.jarvisai.assistant.data.local.JarvisDatabase
import kotlinx.coroutines.launch

private val CactusEmerald = Color(0xFF10B981)
private val CactusCyan = Color(0xFF00E5FF)
private val CactusAmber = Color(0xFFF59E0B)
private val CactusViolet = Color(0xFFA78BFA)
private val CactusCardBg = Color(0xFF121722)
private val CactusBorder = Color(0xFF222B3C)

/**
 * Full-featured Cactus Compute (cactus-compute/cactus) On-Device AI Manager Screen.
 * Lets users install the native ARM64 Cactus runner, configure/start `cactus serve`,
 * download/delete quantized Hugging Face LLM/VLM/STT/Embedding models, choose CPU/GPU/NPU
 * backend, enable Hybrid Cloud Fallback, and re-index Agentra's 5-Tier Memory Vectors.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CactusManagerScreen(
    onBack: () -> Unit,
    onSelectCactusChatModel: (modelId: String, displayName: String) -> Unit = { _, _ -> }
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val config by CactusNativeRuntime.config.collectAsState()
    val status by CactusNativeRuntime.status.collectAsState()
    val modelStates by CactusNativeRuntime.modelStates.collectAsState()
    val logs by CactusNativeRuntime.engineLogs.collectAsState()

    var selectedFilter by remember { mutableStateOf<CactusModelCapability?>(null) }
    var endpointInput by remember(config.serverBaseUrl) { mutableStateOf(config.serverBaseUrl) }
    var hfTokenInput by remember(config.huggingFaceToken) { mutableStateOf(config.huggingFaceToken) }
    var isStartingServer by remember { mutableStateOf(false) }
    var isOneTapSettingUp by remember { mutableStateOf(false) }
    var isReindexingMemories by remember { mutableStateOf(false) }
    var reindexStatusText by remember { mutableStateOf<String?>(null) }
    var showLogs by remember { mutableStateOf(false) }

    val hasNativeNeedleDownloaded = modelStates["cactus:needle3"]?.isDownloaded == true

    LaunchedEffect(Unit) {
        CactusNativeRuntime.initialize(context)
        CactusNativeRuntime.refreshStatus(context)
    }

    val filteredModels = remember(selectedFilter) {
        if (selectedFilter == null) {
            CactusModelCatalog.allModels
        } else if (selectedFilter == CactusModelCapability.LLM) {
            CactusModelCatalog.chatCapableModels()
        } else {
            CactusModelCatalog.allModels.filter { it.capability == selectedFilter }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("cactus_manager_screen"),
        containerColor = AgySettingsColors.BgCanvas,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Cactus On-Device AI",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "cactus-compute/cactus • Offline LLM, VLM, STT & RAG",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("cactus_manager_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                CactusNativeRuntime.refreshStatus(context)
                                Toast.makeText(context, "Cactus status refreshed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("cactus_refresh_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Cactus Engine",
                            tint = CactusEmerald
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AgySettingsColors.BgCanvas)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. HERO ENGINE STATUS & SERVER CONTROL CARD
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF0A221D),
                                    Color(0xFF0E1624),
                                    Color(0xFF0A1B2A)
                                )
                            )
                        )
                        .border(
                            1.2.dp,
                            if (status.isServerRunning) CactusEmerald.copy(alpha = 0.7f) else CactusBorder,
                            RoundedCornerShape(22.dp)
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(CactusEmerald.copy(alpha = 0.18f))
                                        .border(1.dp, CactusEmerald.copy(alpha = 0.45f), RoundedCornerShape(13.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = CactusEmerald,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Cactus Compute Engine",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = when {
                                            status.sdkSupported && status.isServerRunning ->
                                                "IN-APP SDK + v4 SERVER ONLINE"
                                            status.sdkSupported ->
                                                "IN-APP JNI SDK READY (ZERO SERVER NEEDED)"
                                            status.isServerRunning ->
                                                "ONLINE • ${status.serverEndpoint}"
                                            status.binaryInstalled ->
                                                "v4 BINARY READY • STANDBY"
                                            else ->
                                                "SETUP REQUIRED • DOWNLOAD MODEL BELOW"
                                        },
                                        color = if (status.isServerRunning || status.sdkSupported) CactusEmerald else CactusAmber,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (status.isServerRunning) CactusEmerald.copy(alpha = 0.2f) else Color(0xFF1E293B)
                            ) {
                                Text(
                                    text = status.deviceAbi.ifBlank { "arm64" },
                                    color = if (status.isServerRunning) CactusEmerald else Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Hardware & Storage Telemetry Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TelemetryChip(
                                label = "Free RAM",
                                value = "${status.availableRamMb} MB",
                                accent = CactusCyan,
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryChip(
                                label = "Models Disk",
                                value = "${status.totalStorageUsedMb} MB",
                                accent = CactusEmerald,
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryChip(
                                label = "RAG Vectors",
                                value = "${status.indexedMemoryVectorsCount}",
                                accent = CactusViolet,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text(
                            text = status.lastStatusMessage,
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )

                        if (status.isInstallingBinary) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    progress = { status.binaryDownloadProgress.coerceIn(0.02f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(99.dp)),
                                    color = CactusEmerald,
                                    trackColor = Color(0xFF1E293B)
                                )
                                Text(
                                    text = "Installing Cactus ARM64 binary (${(status.binaryDownloadProgress * 100).toInt()}%)...",
                                    color = CactusEmerald,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Primary Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!status.binaryInstalled) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val res = CactusNativeRuntime.installCactusNativeBinary(context)
                                            Toast.makeText(
                                                context,
                                                res.fold(
                                                    onSuccess = { "Cactus ARM64 Engine installed!" },
                                                    onFailure = { it.message ?: "Install failed" }
                                                ),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    enabled = !status.isInstallingBinary,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CactusEmerald,
                                        contentColor = Color(0xFF04130D)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("cactus_install_binary_btn")
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Install Engine", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            if (status.isServerRunning) {
                                                CactusNativeRuntime.stopLocalServer(context)
                                            } else {
                                                isStartingServer = true
                                                val res = CactusNativeRuntime.startLocalServer(context)
                                                isStartingServer = false
                                                if (res.isFailure) {
                                                    showLogs = true
                                                }
                                                Toast.makeText(
                                                    context,
                                                    res.fold(
                                                        onSuccess = { it },
                                                        onFailure = { it.message ?: "Start failed" }
                                                    ),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    },
                                    enabled = !isStartingServer,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (status.isServerRunning) Color(0xFFEF4444) else CactusEmerald,
                                        contentColor = if (status.isServerRunning) Color.White else Color(0xFF04130D)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("cactus_toggle_server_btn")
                                ) {
                                    if (isStartingServer) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = Color(0xFF04130D)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Starting...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    } else {
                                        Icon(
                                            imageVector = if (status.isServerRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (status.isServerRunning) "Stop Server" else "Start Local Server",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { showLogs = !showLogs },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, CactusBorder),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("cactus_toggle_logs_btn")
                            ) {
                                Icon(Icons.Default.Terminal, contentDescription = null, tint = CactusCyan, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Logs", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // One-Tap Native .cact Setup (when binary is ready but Needle3 .cact is not downloaded yet)
                        if (status.binaryInstalled && !status.isServerRunning && !hasNativeNeedleDownloaded) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isOneTapSettingUp = true
                                        val res = CactusNativeRuntime.downloadNeedle3AndStartServer(context)
                                        isOneTapSettingUp = false
                                        if (res.isSuccess) {
                                            onSelectCactusChatModel("cactus:needle3", "🌵 Cactus • Needle3 (.cact Native)")
                                        } else {
                                            showLogs = true
                                        }
                                        Toast.makeText(
                                            context,
                                            res.fold(
                                                onSuccess = { it },
                                                onFailure = { it.message ?: "Setup failed" }
                                            ),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                enabled = !isOneTapSettingUp && !isStartingServer,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CactusCyan,
                                    contentColor = Color(0xFF04131D)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("cactus_onetap_needle3_btn")
                            ) {
                                if (isOneTapSettingUp) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF04131D)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Downloading Needle3 (.cact) & Starting...",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.5.sp
                                    )
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "⚡ Download Needle3 (.cact • 29 MB) & Start Server",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Expandable Engine Logs (Scrollable, full-line readability + Copy/Clear)
                        AnimatedVisibility(visible = showLogs) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF070A10),
                                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 90.dp, max = 260.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Engine Logs (${logs.size})",
                                            color = CactusCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text(
                                                text = "Copy",
                                                color = CactusEmerald,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clickable {
                                                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                        cm?.setPrimaryClip(
                                                            android.content.ClipData.newPlainText(
                                                                "Cactus Logs",
                                                                logs.joinToString("\n")
                                                            )
                                                        )
                                                        Toast.makeText(context, "Cactus logs copied", Toast.LENGTH_SHORT).show()
                                                    }
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                            Text(
                                                text = "Clear",
                                                color = Color(0xFFF87171),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clickable { CactusNativeRuntime.clearLogs() }
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        if (logs.isEmpty()) {
                                            Text(
                                                text = "No Cactus logs yet. Tap Start Local Server to inspect output.",
                                                color = Color(0xFF64748B),
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        } else {
                                            logs.takeLast(60).forEach { line ->
                                                Text(
                                                    text = line,
                                                    color = when {
                                                        line.contains("ERROR", ignoreCase = true) ||
                                                            line.contains("exited", ignoreCase = true) ||
                                                            line.contains("failed", ignoreCase = true) -> Color(0xFFFCA5A5)
                                                        line.startsWith("[SERVER]") || line.startsWith("[PROBE]") -> CactusCyan
                                                        else -> Color(0xFFCBD5E1)
                                                    },
                                                    fontSize = 10.5.sp,
                                                    lineHeight = 14.sp,
                                                    fontFamily = FontFamily.Monospace
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

            // 2. SERVER ENDPOINT & HARDWARE ACCELERATION CARD
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CactusCardBg,
                    border = BorderStroke(1.dp, CactusBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Hardware & Server Bridge",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        // Compute Backend Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CactusComputeBackend.entries.forEach { backend ->
                                val active = config.computeBackend == backend
                                Surface(
                                    onClick = {
                                        CactusNativeRuntime.updateConfig(context) { it.copy(computeBackend = backend) }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (active) CactusEmerald.copy(alpha = 0.2f) else Color(0xFF1A2232),
                                    border = BorderStroke(
                                        1.dp,
                                        if (active) CactusEmerald else CactusBorder
                                    ),
                                    modifier = Modifier.testTag("cactus_backend_${backend.id}")
                                ) {
                                    Text(
                                        text = backend.label,
                                        color = if (active) CactusEmerald else Color(0xFFCBD5E1),
                                        fontSize = 11.5.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        // Endpoint Config (for Termux/PRoot/PC `cactus serve`)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = endpointInput,
                                onValueChange = { endpointInput = it },
                                label = { Text("Cactus Server URL", fontSize = 12.sp) },
                                placeholder = { Text("http://127.0.0.1:8090") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CactusEmerald,
                                    unfocusedBorderColor = CactusBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF0A0F18),
                                    unfocusedContainerColor = Color(0xFF0A0F18)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cactus_endpoint_input")
                            )
                            Button(
                                onClick = {
                                    CactusNativeRuntime.updateConfig(context) {
                                        it.copy(serverBaseUrl = endpointInput.trim().removeSuffix("/"))
                                    }
                                    scope.launch {
                                        val refreshed = CactusNativeRuntime.refreshStatus(context)
                                        Toast.makeText(
                                            context,
                                            if (refreshed.isServerRunning) "Cactus Server ONLINE!" else "Saved endpoint (server currently offline)",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CactusCyan,
                                    contentColor = Color(0xFF04131D)
                                ),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("cactus_probe_endpoint_btn")
                            ) {
                                Text("Probe", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                            }
                        }

                        // Optional Hugging Face Access Token (only needed for gated repos like Gemma 4)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = hfTokenInput,
                                onValueChange = { hfTokenInput = it },
                                label = { Text("Optional HF Token (for gated models)", fontSize = 11.5.sp) },
                                placeholder = { Text("hf_... (optional)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CactusViolet,
                                    unfocusedBorderColor = CactusBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF0A0F18),
                                    unfocusedContainerColor = Color(0xFF0A0F18)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cactus_hf_token_input")
                            )
                            OutlinedButton(
                                onClick = {
                                    CactusNativeRuntime.updateConfig(context) {
                                        it.copy(huggingFaceToken = hfTokenInput.trim())
                                    }
                                    Toast.makeText(
                                        context,
                                        if (hfTokenInput.isBlank()) "HF Token cleared" else "HF Token saved for gated downloads",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, CactusViolet.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("cactus_save_hf_token_btn")
                            ) {
                                Text("Save", color = CactusViolet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 3. AGENTRA INTEGRATION TOGGLES (HYBRID FALLBACK, OFFLINE STT, VECTOR RAG, PRIVATE VISION/DOCS)
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CactusCardBg,
                    border = BorderStroke(1.dp, CactusBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Agentra Subsystem Integrations",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        CactusToggleRow(
                            title = "⚡ Fast Cactus Screen Control (3-Tier Hybrid)",
                            subtitle = "Run Android taps, scrolling & live screen vision in ~100-500ms on Cactus (Tier 1 UI-Tree + Tier 2 Local VLM), with auto Cloud Gemini fallback only if stuck.",
                            checked = config.fastCactusScreenControlEnabled,
                            onCheckedChange = { enabled ->
                                CactusNativeRuntime.updateConfig(context) { it.copy(fastCactusScreenControlEnabled = enabled) }
                            },
                            tag = "cactus_toggle_fast_screen_control"
                        )

                        CactusToggleRow(
                            title = "Hybrid Cloud Auto-Fallback",
                            subtitle = "If local Cactus engine isn't running, hand off smoothly to Gemini/Antigravity when online.",
                            checked = config.hybridCloudFallbackEnabled,
                            onCheckedChange = { enabled ->
                                CactusNativeRuntime.updateConfig(context) { it.copy(hybridCloudFallbackEnabled = enabled) }
                            },
                            tag = "cactus_toggle_hybrid"
                        )

                        CactusToggleRow(
                            title = "Offline Cactus Voice STT (Whistle / Whisper)",
                            subtitle = "Use 100% offline mic transcription via Whistle (.cact) or Whisper JNI SDK without Google Speech API.",
                            checked = config.offlineSttEnabled,
                            onCheckedChange = { enabled ->
                                CactusNativeRuntime.updateConfig(context) { it.copy(offlineSttEnabled = enabled) }
                            },
                            tag = "cactus_toggle_stt"
                        )

                        CactusToggleRow(
                            title = "5-Tier Memory Semantic Vector RAG",
                            subtitle = "Boost Agentra memory recall using in-process Cactus embeddings cosine similarity.",
                            checked = config.memoryVectorRagEnabled,
                            onCheckedChange = { enabled ->
                                CactusNativeRuntime.updateConfig(context) { it.copy(memoryVectorRagEnabled = enabled) }
                            },
                            tag = "cactus_toggle_rag"
                        )

                        CactusToggleRow(
                            title = "Private Offline Vision & Optical Solver Default",
                            subtitle = "Default Vision & Optical Solver screens to on-device LFM 2 VL / Cactus Vision SDK.",
                            checked = config.privateOfflineVisionDefault,
                            onCheckedChange = { enabled ->
                                CactusNativeRuntime.updateConfig(context) { it.copy(privateOfflineVisionDefault = enabled) }
                            },
                            tag = "cactus_toggle_vision_default"
                        )

                        CactusToggleRow(
                            title = "Private Offline Document Reader Default",
                            subtitle = "Default PDF/TXT Document Assistant to local Cactus RAG without cloud upload.",
                            checked = config.privateOfflineDocsDefault,
                            onCheckedChange = { enabled ->
                                CactusNativeRuntime.updateConfig(context) { it.copy(privateOfflineDocsDefault = enabled) }
                            },
                            tag = "cactus_toggle_docs_default"
                        )

                        // Re-index 5-Tier Memories with Cactus Vectors button
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isReindexingMemories = true
                                    reindexStatusText = "Indexing Agentra 5-Tier Memories with Cactus Embeddings..."
                                    val dao = JarvisDatabase.getInstance(context).jarvisDao()
                                    val memories = dao.getAllMemoriesOnce()
                                    val (indexed, total) = CactusVectorMemoryIndex.reindexAllMemories(context, memories) { done, tot ->
                                        reindexStatusText = "Embedding memory $done / $tot..."
                                    }
                                    isReindexingMemories = false
                                    reindexStatusText = if (total == 0) {
                                        "No memories stored yet."
                                    } else if (indexed > 0) {
                                        "Indexed $indexed / $total memories into Cactus Vector Store!"
                                    } else {
                                        "Embedding model nahi mila. Niche catalog me 'Qwen3 Embedding 0.6B' ya 'Qwen3 0.6B' download karein (ya external OpenAI `/v1/embeddings` connect karein)."
                                    }
                                }
                            },
                            enabled = !isReindexingMemories,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, CactusViolet.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("cactus_reindex_memories_btn")
                        ) {
                            if (isReindexingMemories) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = CactusViolet
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = if (isReindexingMemories) "Indexing Vectors..." else "Re-index 5-Tier Memory Vectors (${status.indexedMemoryVectorsCount})",
                                color = CactusViolet,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }

                        if (!reindexStatusText.isNullOrBlank()) {
                            Text(
                                text = reindexStatusText.orEmpty(),
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }

            // 4. MODEL CATALOG FILTER TABS
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Cactus Hugging Face Model Catalog",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val tabs = listOf(
                            null to "All (${CactusModelCatalog.allModels.size})",
                            CactusModelCapability.LLM to "Chat & Tools",
                            CactusModelCapability.VLM to "Vision (VLM)",
                            CactusModelCapability.STT to "Voice (STT)",
                            CactusModelCapability.EMBEDDING to "Embeddings"
                        )
                        tabs.forEach { (cap, title) ->
                            val selected = selectedFilter == cap
                            Surface(
                                onClick = { selectedFilter = cap },
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) CactusEmerald else Color(0xFF1A2232),
                                border = BorderStroke(1.dp, if (selected) CactusEmerald else CactusBorder)
                            ) {
                                Text(
                                    text = title,
                                    color = if (selected) Color(0xFF04130D) else Color(0xFFCBD5E1),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 5. MODEL CARDS
            items(filteredModels, key = { it.id }) { spec ->
                val state = modelStates[spec.id] ?: CactusModelInstallState(modelId = spec.id)
                val isActiveSlot = when (spec.capability) {
                    CactusModelCapability.LLM, CactusModelCapability.TOOL_CALLING -> config.activeLlmModelId == spec.id
                    CactusModelCapability.VLM -> config.activeVlmModelId == spec.id
                    CactusModelCapability.STT -> config.activeSttModelId == spec.id
                    CactusModelCapability.EMBEDDING -> config.activeEmbeddingModelId == spec.id
                }

                CactusModelCard(
                    spec = spec,
                    state = state,
                    isActiveSlot = isActiveSlot,
                    onDownload = { CactusNativeRuntime.startModelDownload(context, spec) },
                    onCancelDownload = { CactusNativeRuntime.cancelModelDownload(spec.id) },
                    onDelete = { scope.launch { CactusNativeRuntime.deleteModel(context, spec) } },
                    onActivate = {
                        CactusNativeRuntime.updateConfig(context) { cur ->
                            when (spec.capability) {
                                CactusModelCapability.LLM, CactusModelCapability.TOOL_CALLING ->
                                    cur.copy(activeLlmModelId = spec.id)
                                CactusModelCapability.VLM ->
                                    cur.copy(activeVlmModelId = spec.id)
                                CactusModelCapability.STT ->
                                    cur.copy(activeSttModelId = spec.id)
                                CactusModelCapability.EMBEDDING ->
                                    cur.copy(activeEmbeddingModelId = spec.id)
                            }
                        }
                        if (spec.capability == CactusModelCapability.LLM || spec.capability == CactusModelCapability.TOOL_CALLING) {
                            onSelectCactusChatModel(spec.id, "🌵 Cactus • ${spec.displayName}")
                        }
                        Toast.makeText(context, "Active Cactus ${spec.capability.badge}: ${spec.displayName}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun TelemetryChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0A101A),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = label, color = Color(0xFF94A3B8), fontSize = 10.sp)
            Text(text = value, color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CactusToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0B1019))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(text = title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(text = subtitle, color = Color(0xFF94A3B8), fontSize = 11.sp, lineHeight = 15.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF04130D),
                checkedTrackColor = CactusEmerald
            )
        )
    }
}

@Composable
private fun CactusModelCard(
    spec: CactusModelSpec,
    state: CactusModelInstallState,
    isActiveSlot: Boolean,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit,
    onActivate: () -> Unit
) {
    val accent = when (spec.capability) {
        CactusModelCapability.LLM -> CactusEmerald
        CactusModelCapability.TOOL_CALLING -> CactusCyan
        CactusModelCapability.VLM -> Color(0xFFF472B6)
        CactusModelCapability.STT -> CactusAmber
        CactusModelCapability.EMBEDDING -> CactusViolet
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CactusCardBg,
        border = BorderStroke(
            width = if (isActiveSlot) 1.5.dp else 1.dp,
            color = if (isActiveSlot) accent else CactusBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cactus_model_card_${spec.slug}")
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = spec.displayName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${spec.huggingFaceRepo} • ${spec.parameterLabel} • ${spec.quantizationLabel}",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accent.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = spec.capability.badge,
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = spec.description,
                color = Color(0xFFCBD5E1),
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val runtimeBadgeColor = when (spec.runtime) {
                    CactusModelRuntime.IN_PROCESS_SDK -> CactusEmerald
                    CactusModelRuntime.NATIVE_V4_CACT -> CactusCyan
                    CactusModelRuntime.EXTERNAL_OPENAI -> Color(0xFF94A3B8)
                }
                val runtimeChipBg = when (spec.runtime) {
                    CactusModelRuntime.IN_PROCESS_SDK -> CactusEmerald.copy(alpha = 0.2f)
                    CactusModelRuntime.NATIVE_V4_CACT -> CactusCyan.copy(alpha = 0.18f)
                    CactusModelRuntime.EXTERNAL_OPENAI -> Color(0xFF1E293B)
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = runtimeChipBg
                ) {
                    Text(
                        text = spec.runtime.badgeLabel,
                        color = runtimeBadgeColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = "~${spec.estimatedDownloadMb} MB",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = "•", color = Color(0xFF475569))
                Text(
                    text = "${spec.recommendedRamGb}GB+ RAM",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                if (spec.supportsToolCalling) {
                    Text(text = "•", color = Color(0xFF475569))
                    Text(text = "Tools", color = CactusCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                if (state.isDownloaded && state.sizeOnDiskBytes > 0L) {
                    Text(text = "•", color = Color(0xFF475569))
                    Text(
                        text = "${state.sizeOnDiskBytes / (1024L * 1024L)} MB saved",
                        color = CactusEmerald,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (state.isDownloading) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { state.downloadProgress.coerceIn(0.01f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(99.dp)),
                        color = accent,
                        trackColor = Color(0xFF1E293B)
                    )
                    val dlMb = String.format(java.util.Locale.US, "%.1f", state.downloadedBytes.toDouble() / (1024.0 * 1024.0))
                    val totMb = if (state.totalBytes > 0L) {
                        " / " + String.format(java.util.Locale.US, "%.1f MB", state.totalBytes.toDouble() / (1024.0 * 1024.0))
                    } else {
                        " MB"
                    }
                    val fileTag = state.currentFileName.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()
                    Text(
                        text = "Downloading ${(state.downloadProgress * 100).toInt()}% ($dlMb$totMb)$fileTag",
                        color = accent,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!state.lastError.isNullOrBlank()) {
                Text(
                    text = state.lastError.orEmpty(),
                    color = Color(0xFFFCA5A5),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.isDownloading) {
                    OutlinedButton(
                        onClick = onCancelDownload,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancel", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (state.isDownloaded) {
                    Button(
                        onClick = onActivate,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isActiveSlot) accent.copy(alpha = 0.22f) else accent,
                            contentColor = if (isActiveSlot) accent else Color(0xFF04130D)
                        ),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isActiveSlot) "Active Model" else "Use Model",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Model",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = onDownload,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = accent
                        ),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download (~${spec.estimatedDownloadMb} MB)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onActivate,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isActiveSlot) accent else CactusBorder),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text(
                            text = if (isActiveSlot) "Selected" else "Select",
                            color = if (isActiveSlot) accent else Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
