package com.jarvisai.assistant.ui.settings

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.LocalTerminalManager
import com.jarvisai.assistant.terminal.RootfsInstallProgress
import com.jarvisai.assistant.ui.components.bouncyOverscroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class LocalToolSpec(
    val id: String,
    val title: String,
    val binaryName: String,
    val packageCommand: String,
    val category: String,
    val description: String,
    val estimatedSizeMb: Int
)

val DEFAULT_LOCAL_TOOLS = listOf(
    LocalToolSpec(
        id = "nodejs",
        title = "Node.js 22 LTS + npm",
        binaryName = "node",
        packageCommand = "pkg install -y nodejs-lts",
        category = "Runtime",
        description = "JavaScript/TypeScript runtime required for Claude Code, Codex, and MCP servers.",
        estimatedSizeMb = 48
    ),
    LocalToolSpec(
        id = "python",
        title = "Python 3.12 + pip",
        binaryName = "python3",
        packageCommand = "pkg install -y python",
        category = "Runtime",
        description = "Scientific computing, FastAPI backends, SymPy, and AI data pipelines.",
        estimatedSizeMb = 65
    ),
    LocalToolSpec(
        id = "git_gh",
        title = "Git & GitHub CLI (gh)",
        binaryName = "git",
        packageCommand = "pkg install -y git gh openssh",
        category = "VCS",
        description = "Source control, SSH key management, and autonomous PR workflows.",
        estimatedSizeMb = 34
    ),
    LocalToolSpec(
        id = "claude_code",
        title = "Claude Code CLI (@anthropic-ai/claude-code)",
        binaryName = "claude",
        packageCommand = "npm install -g @anthropic-ai/claude-code",
        category = "AI Agent",
        description = "Official Anthropic terminal coding agent with MCP & subagent tool routing.",
        estimatedSizeMb = 28
    ),
    LocalToolSpec(
        id = "openai_codex",
        title = "OpenAI Codex CLI (@openai/codex)",
        binaryName = "codex",
        packageCommand = "npm install -g @openai/codex",
        category = "AI Agent",
        description = "Autonomous workspace diff generator and sandbox command runner.",
        estimatedSizeMb = 22
    ),
    LocalToolSpec(
        id = "opencode",
        title = "OpenCode CLI (opencode-ai)",
        binaryName = "opencode",
        packageCommand = "npm install -g opencode-ai",
        category = "AI Agent",
        description = "Multi-provider local & cloud coding agent with HTTP daemon mode.",
        estimatedSizeMb = 25
    ),
    LocalToolSpec(
        id = "clang_cmake",
        title = "Clang / LLVM + CMake + Ninja",
        binaryName = "clang",
        packageCommand = "pkg install -y clang cmake ninja make pkg-config",
        category = "Native Toolchain",
        description = "C/C++ compiler suite and native build tools for NDK and Python wheels.",
        estimatedSizeMb = 120
    ),
    LocalToolSpec(
        id = "ripgrep_fd",
        title = "Ripgrep (rg) + fd + jq",
        binaryName = "rg",
        packageCommand = "pkg install -y ripgrep fd jq tmux neovim",
        category = "Search & Editor",
        description = "Ultra-fast AST/regex search utilities used by AI agents for codebase indexing.",
        estimatedSizeMb = 18
    )
)

object LocalToolProbe {
    fun isBinaryInstalled(context: Context, binaryName: String): Boolean {
        val binDir = LocalEnvironmentManager.getBinDir(context)
        val candidate = File(binDir, binaryName)
        if (candidate.exists() && candidate.canExecute()) return true
        val termuxCandidate = File("/data/data/com.termux/files/usr/bin/$binaryName")
        return termuxCandidate.exists() && termuxCandidate.canExecute()
    }
}

@Composable
fun LocalToolsInstallDialog(
    onDismiss: () -> Unit,
    onExpandFullScreen: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installProgress by LocalEnvironmentManager.installProgress.collectAsState()
    val logs by LocalEnvironmentManager.liveExtractionLogs.collectAsState()

    var installedMap by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var activeToolId by remember { mutableStateOf<String?>(null) }
    val consoleOutput = remember { mutableStateListOf<String>() }

    fun refreshInstalledStates() {
        val updated = DEFAULT_LOCAL_TOOLS.associate { spec ->
            spec.id to LocalToolProbe.isBinaryInstalled(context, spec.binaryName)
        }
        installedMap = updated
    }

    LaunchedEffect(installProgress) {
        refreshInstalledStates()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 700.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF0B0F19),
            border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.4f)),
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF6366F1).copy(alpha = 0.16f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = "Local Tools Manager",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Local CLI & Agent Toolchain",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color(0xFFF8FAFC),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Prefix: ${LocalEnvironmentManager.getPrefixDir(context).absolutePath}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF94A3B8),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onExpandFullScreen,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("local_tools_expand_fullscreen")
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInFull,
                                contentDescription = "Open Full Screen Installer",
                                tint = Color(0xFF38BDF8)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("local_tools_close_dialog")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Local Tools Dialog",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // Bootstrap Rootfs Banner
                RootfsBootstrapCard(
                    installProgress = installProgress,
                    onInstallBootstrap = {
                        LocalEnvironmentManager.startInstall(context)
                    }
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .bouncyOverscroll(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(DEFAULT_LOCAL_TOOLS, key = { it.id }) { spec ->
                        val isInstalled = installedMap[spec.id] == true
                        val isInstalling = activeToolId == spec.id

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF111827),
                            border = BorderStroke(
                                1.dp,
                                if (isInstalled) Color(0xFF10B981).copy(alpha = 0.4f)
                                else Color(0xFF1E293B)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = spec.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = Color(0xFFF8FAFC),
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF6366F1).copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = spec.category,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = Color(0xFF818CF8),
                                                        fontSize = 10.sp
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = spec.description,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "$ ${spec.packageCommand} • ~${spec.estimatedSizeMb} MB",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color(0xFF38BDF8),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (isInstalling) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(28.dp),
                                            color = Color(0xFF38BDF8),
                                            strokeWidth = 2.5.dp
                                        )
                                    } else if (isInstalled) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Installed",
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            OutlinedButton(
                                                onClick = {
                                                    activeToolId = spec.id
                                                    scope.launch {
                                                        consoleOutput.add("> Updating ${spec.title}...")
                                                        val res = withContext(Dispatchers.IO) {
                                                            com.jarvisai.assistant.terminal.LocalTerminalBridge.instance.init(context)
                                                            com.jarvisai.assistant.terminal.LocalTerminalBridge.instance.executeCommand(
                                                                spec.packageCommand,
                                                                timeoutMs = 180_000L
                                                            ).output
                                                        }
                                                        consoleOutput.add(res.takeLast(400))
                                                        activeToolId = null
                                                        refreshInstalledStates()
                                                    }
                                                },
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("reinstall_tool_${spec.id}"),
                                                border = BorderStroke(1.dp, Color(0xFF334155))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Update Tool",
                                                    tint = Color(0xFF94A3B8),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                activeToolId = spec.id
                                                scope.launch {
                                                    consoleOutput.add("$ ${spec.packageCommand}")
                                                    val res = withContext(Dispatchers.IO) {
                                                        com.jarvisai.assistant.terminal.LocalTerminalBridge.instance.init(context)
                                                        com.jarvisai.assistant.terminal.LocalTerminalBridge.instance.executeCommand(
                                                            spec.packageCommand,
                                                            timeoutMs = 180_000L
                                                        ).output
                                                    }
                                                    consoleOutput.add(res.ifBlank { "Completed installation for ${spec.title}" }.takeLast(400))
                                                    activeToolId = null
                                                    refreshInstalledStates()
                                                }
                                            },
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("install_tool_${spec.id}"),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF6366F1),
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDownload,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Install", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = consoleOutput.isNotEmpty() || logs.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF050811),
                        border = BorderStroke(1.dp, Color(0xFF1E293B))
                    ) {
                        val combined = (logs.takeLast(6) + consoleOutput.takeLast(6)).takeLast(8)
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            combined.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF10B981),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RootfsBootstrapCard(
    installProgress: RootfsInstallProgress,
    onInstallBootstrap: () -> Unit
) {
    val context = LocalContext.current
    val diagnostics = remember(installProgress) {
        LocalEnvironmentManager.getDiagnostics(context)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E1B4B).copy(alpha = 0.55f),
        border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = if (diagnostics.isInstalled) "Termux ARM64 Rootfs Active" else "Termux ARM64 Bootstrap Required",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFFF8FAFC),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Disk Usage: ${diagnostics.diskUsageFormatted} • Shell: ${diagnostics.defaultShell}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF94A3B8)
                            )
                        )
                    }
                }

                Button(
                    onClick = onInstallBootstrap,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("bootstrap_install_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (diagnostics.isInstalled) Color(0xFF1E293B) else Color(0xFF10B981),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (diagnostics.isInstalled) "Repair Rootfs" else "Bootstrap Now",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            when (installProgress) {
                is RootfsInstallProgress.Downloading -> {
                    LinearProgressIndicator(
                        progress = { installProgress.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF38BDF8),
                        trackColor = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Downloading bootstrap archive: ${installProgress.speed}",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8))
                    )
                }
                is RootfsInstallProgress.Extracting -> {
                    LinearProgressIndicator(
                        progress = { installProgress.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFFF59E0B),
                        trackColor = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Extracting SYMLINKS.txt & binaries (${installProgress.filesExtracted}/${installProgress.totalEstimate}): ${installProgress.currentFile}",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFF59E0B)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                is RootfsInstallProgress.Configuring -> {
                    Text(
                        text = installProgress.statusMessage,
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8))
                    )
                }
                is RootfsInstallProgress.Error -> {
                    Text(
                        text = "Error: ${installProgress.errorMessage}",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFEF4444))
                    )
                }
                else -> {}
            }
        }
    }
}
