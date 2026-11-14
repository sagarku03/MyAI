package com.jarvisai.assistant.ide

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.terminal.LocalTerminalBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun GitSourceControlView(
    projectDir: File,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var gitStatus by remember { mutableStateOf(GitStatusResponse()) }
    var commitMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isRepoInitialized by remember { mutableStateOf(true) }
    var statusError by remember { mutableStateOf<String?>(null) }

    fun shellQuote(path: String): String = "'${path.replace("'", "'\\''")}'"

    fun refreshStatus() {
        coroutineScope.launch {
            isLoading = true
            statusError = null
            runCatching {
                LocalTerminalBridge.instance.init(context.applicationContext)
                val quotedDir = shellQuote(projectDir.absolutePath)
                val statusRes = withContext(Dispatchers.IO) {
                    LocalTerminalBridge.instance.executeCommand("cd $quotedDir && git status -s", timeoutMs = 15_000L)
                }
                val outputLower = statusRes.output.lowercase()
                when {
                    outputLower.contains("not a git repository") -> {
                        isRepoInitialized = false
                        gitStatus = GitStatusResponse(branch = "uninitialized", unstagedFiles = emptyList())
                    }
                    outputLower.contains("git: not found") ||
                        outputLower.contains("command not found") ||
                        outputLower.contains("cannot link executable") -> {
                        isRepoInitialized = false
                        statusError = "Git CLI unavailable. Install or repair Linux Core in Settings/Terminal."
                        gitStatus = GitStatusResponse(branch = "unavailable", unstagedFiles = emptyList())
                    }
                    statusRes.exitCode != 0 && statusRes.output.isNotBlank() -> {
                        statusError = statusRes.output.lines().firstOrNull()?.take(120)
                        gitStatus = GitStatusResponse()
                    }
                    else -> {
                        isRepoInitialized = true
                        val branchRes = withContext(Dispatchers.IO) {
                            runCatching {
                                LocalTerminalBridge.instance.executeCommand(
                                    "cd $quotedDir && git rev-parse --abbrev-ref HEAD",
                                    timeoutMs = 10_000L
                                )
                            }.getOrNull()
                        }
                        val branchName = branchRes?.output?.trim()?.takeIf {
                            branchRes.exitCode == 0 && it.isNotBlank() && !it.contains("fatal", ignoreCase = true)
                        } ?: "main"
                        val lines = statusRes.output.lines().filter { it.isNotBlank() }.map { it.trim() }
                        gitStatus = GitStatusResponse(branch = branchName, unstagedFiles = lines)
                    }
                }
            }.onFailure { err ->
                statusError = err.localizedMessage ?: "Failed to query git status"
                IdeDaemonLogBuffer.append("ERROR", "Git status error: ${err.message}")
            }
            isLoading = false
        }
    }

    LaunchedEffect(projectDir.absolutePath) {
        refreshStatus()
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("ide_git_panel"),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Color(0xFF1E3A5F)),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SOURCE CONTROL", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${projectDir.name} (${gitStatus.branch})",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { refreshStatus() },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("git_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Git Status", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("git_close_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Source Control", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF38BDF8),
                    trackColor = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (statusError != null) {
                Surface(
                    color = Color(0xFF7F1D1D).copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusError ?: "",
                        color = Color(0xFFFCA5A5),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (!isRepoInitialized && statusError == null) {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "No Git repository found in '${projectDir.name}'.",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isLoading = true
                                    runCatching {
                                        LocalTerminalBridge.instance.init(context.applicationContext)
                                        withContext(Dispatchers.IO) {
                                            LocalTerminalBridge.instance.executeCommand(
                                                "cd ${shellQuote(projectDir.absolutePath)} && git init",
                                                timeoutMs = 15_000L
                                            )
                                        }
                                    }.onFailure { err ->
                                        statusError = err.message
                                    }
                                    isLoading = false
                                    refreshStatus()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Initialize Repository (git init)", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    placeholder = { Text("Commit message", fontSize = 12.sp, color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E293B),
                        unfocusedContainerColor = Color(0xFF1E293B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155),
                        cursorColor = Color(0xFF38BDF8)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val msg = commitMessage.trim()
                        if (msg.isNotEmpty() && !isLoading) {
                            coroutineScope.launch {
                                isLoading = true
                                statusError = null
                                runCatching {
                                    LocalTerminalBridge.instance.init(context.applicationContext)
                                    val res = withContext(Dispatchers.IO) {
                                        LocalTerminalBridge.instance.executeCommand(
                                            "cd ${shellQuote(projectDir.absolutePath)} && git add -A && git commit -m ${shellQuote(msg)}",
                                            timeoutMs = 20_000L
                                        )
                                    }
                                    if (res.exitCode != 0 && res.output.isNotBlank()) {
                                        statusError = res.output.lines().lastOrNull()?.take(120)
                                    } else {
                                        commitMessage = ""
                                    }
                                }.onFailure { err ->
                                    statusError = err.message ?: "Commit failed"
                                }
                                isLoading = false
                                refreshStatus()
                            }
                        }
                    },
                    enabled = commitMessage.isNotBlank() && !isLoading && isRepoInitialized,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Commit All Changes", fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("CHANGES (${gitStatus.unstagedFiles.size})", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

                if (gitStatus.unstagedFiles.isEmpty() && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text("Working tree clean", color = Color(0xFF64748B), fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 4.dp)) {
                        items(gitStatus.unstagedFiles) { change ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(change.take(2), color = Color(0xFFFCD34D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(change.drop(2).trim(), color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
