package com.jarvisai.assistant.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.LocalTerminalManager
import com.jarvisai.assistant.terminal.RootfsInstallProgress
import com.jarvisai.assistant.ui.components.AgentWorkingSpinner
import com.jarvisai.assistant.ui.components.bouncyOverscroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenLocalToolsInstaller(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installProgress by LocalEnvironmentManager.installProgress.collectAsState()
    val extractionLogs by LocalEnvironmentManager.liveExtractionLogs.collectAsState()

    var installedMap by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var runningBatch by remember { mutableStateOf(false) }
    var currentToolTitle by remember { mutableStateOf<String?>(null) }
    val terminalLines = remember { mutableStateListOf<String>() }

    fun refreshStates() {
        installedMap = DEFAULT_LOCAL_TOOLS.associate {
            it.id to LocalToolProbe.isBinaryInstalled(context, it.binaryName)
        }
    }

    LaunchedEffect(installProgress) {
        refreshStates()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Full-Screen Toolchain Provisioner",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFFF8FAFC),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Automated Bootstrap, SYMLINKS.txt & CLI Agent Setup",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF38BDF8)
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("fullscreen_installer_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFF8FAFC)
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (!runningBatch) {
                                runningBatch = true
                                scope.launch {
                                    terminalLines.add("=== Starting Full Autonomous Toolchain Setup ===")
                                    for (spec in DEFAULT_LOCAL_TOOLS) {
                                        currentToolTitle = spec.title
                                        terminalLines.add("$ ${spec.packageCommand}")
                                        val output = withContext(Dispatchers.IO) {
                                            com.jarvisai.assistant.terminal.LocalTerminalBridge.instance.init(context)
                                            com.jarvisai.assistant.terminal.LocalTerminalBridge.instance.executeCommand(
                                                spec.packageCommand,
                                                timeoutMs = 240_000L
                                            ).output
                                        }
                                        output.lines().filter { it.isNotBlank() }.takeLast(6).forEach {
                                            terminalLines.add(it)
                                        }
                                        refreshStates()
                                    }
                                    currentToolTitle = null
                                    runningBatch = false
                                    terminalLines.add("=== Toolchain Provisioning Complete ===")
                                }
                            }
                        },
                        enabled = !runningBatch,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .heightIn(min = 48.dp)
                            .testTag("install_all_tools_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color(0xFF052E16)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Install All", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0F19))
            )
        },
        containerColor = Color(0xFF0B0F19)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RootfsBootstrapCard(
                installProgress = installProgress,
                onInstallBootstrap = {
                    LocalEnvironmentManager.startInstall(context)
                }
            )

            if (runningBatch || installProgress is RootfsInstallProgress.Downloading || installProgress is RootfsInstallProgress.Extracting) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF111827),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AgentWorkingSpinner(size = 32.dp)
                        Column {
                            Text(
                                text = currentToolTitle?.let { "Installing $it..." } ?: "Provisioning Termux Environment...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFFF8FAFC),
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                text = "Streaming live stdout/stderr from PTY bridge",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .bouncyOverscroll(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(DEFAULT_LOCAL_TOOLS, key = { it.id }) { spec ->
                    val installed = installedMap[spec.id] == true
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF111827),
                        border = BorderStroke(
                            1.dp,
                            if (installed) Color(0xFF10B981).copy(alpha = 0.45f) else Color(0xFF1E293B)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = spec.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = Color(0xFFF8FAFC),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Text(
                                    text = spec.description,
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = spec.packageCommand,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF38BDF8),
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            if (installed) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Installed",
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Ready",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = Color(0xFF10B981),
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            currentToolTitle = spec.title
                                            terminalLines.add("$ ${spec.packageCommand}")
                                            val out = withContext(Dispatchers.IO) {
                                                com.jarvisai.assistant.terminal.LocalTerminalBridge.instance.init(context)
                                                com.jarvisai.assistant.terminal.LocalTerminalBridge.instance.executeCommand(
                                                    spec.packageCommand,
                                                    timeoutMs = 180_000L
                                                ).output
                                            }
                                            terminalLines.add(out.takeLast(500))
                                            currentToolTitle = null
                                            refreshStates()
                                        }
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Install")
                                }
                            }
                        }
                    }
                }
            }

            // Live Terminal Log Output Box
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF050811),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "LIVE INSTALLER CONSOLE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF10B981),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val allLogs = (extractionLogs.takeLast(12) + terminalLines.takeLast(12)).takeLast(14)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(allLogs) { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFFE2E8F0),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
