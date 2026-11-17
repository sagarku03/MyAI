package com.jarvisai.assistant.ide

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jarvisai.assistant.terminal.LocalTerminalBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val GitBg = Color(0xFF11131A)
private val GitCardBg = Color(0xFF171A24)
private val GitBorder = Color(0xFF232738)
private val GitAccent = Color(0xFF8AB4F8)
private val GitLavender = Color(0xFFC4B5FD)
private val GitGreen = Color(0xFF34D399)
private val GitYellow = Color(0xFFFBBF24)
private val GitRed = Color(0xFFF87171)
private val GitTextPrimary = Color(0xFFE6EDF3)
private val GitTextSecondary = Color(0xFF8B949E)

@Composable
fun GitSourceControlView(
    projectDir: File,
    embeddedInSidebar: Boolean = false,
    onOpenDiff: ((filePath: String, rawDiff: String, isStaged: Boolean) -> Unit)? = null,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var gitStatus by remember(projectDir.absolutePath) { mutableStateOf(GitStatusResponse()) }
    var commitMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isRepoInitialized by remember(projectDir.absolutePath) { mutableStateOf(true) }
    var statusError by remember { mutableStateOf<String?>(null) }
    var showGitConfigDialog by remember { mutableStateOf(false) }
    var gitUserName by remember { mutableStateOf("") }
    var gitUserEmail by remember { mutableStateOf("") }
    var gitRemoteUrl by remember { mutableStateOf("") }

    fun shellQuote(path: String): String = "'${path.replace("'", "'\\''")}'"

    fun parseGitStatusLine(rawLine: String): Pair<String, String> {
        // Handle both standard porcelain ("XY path") and leading-trimmed first line ("M path")
        if (rawLine.length >= 3 && rawLine[2] == ' ') {
            val badge = rawLine.take(2).trim().ifBlank { "M" }
            val path = rawLine.drop(3).trim()
            return badge to path
        }
        val match = Regex("^([?!MADRCU]{1,2})\\s+(.+)$").find(rawLine.trimStart())
        if (match != null) {
            return match.groupValues[1] to match.groupValues[2].trim()
        }
        val clean = rawLine.trim()
        val spaceIdx = clean.indexOfFirst { it.isWhitespace() }
        return if (spaceIdx > 0) {
            clean.substring(0, spaceIdx) to clean.substring(spaceIdx).trim()
        } else {
            "M" to clean
        }
    }

    fun refreshStatus() {
        coroutineScope.launch {
            isLoading = true
            statusError = null
            runCatching {
                LocalTerminalBridge.instance.init(context.applicationContext)
                val workDir = projectDir.absolutePath
                val statusRes = withContext(Dispatchers.IO) {
                    LocalTerminalBridge.instance.executeCommand(
                        command = "git status -s",
                        timeoutMs = 15_000L,
                        workingDir = workDir
                    )
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
                    statusRes.exitCode != 0 -> {
                        statusError = statusRes.output.lines().firstOrNull()?.take(140)
                            ?.ifBlank { "git status failed (exit ${statusRes.exitCode})" }
                            ?: "git status failed (exit ${statusRes.exitCode})"
                        gitStatus = GitStatusResponse()
                    }
                    else -> {
                        isRepoInitialized = true
                        val branchRes = withContext(Dispatchers.IO) {
                            runCatching {
                                LocalTerminalBridge.instance.executeCommand(
                                    command = "git rev-parse --abbrev-ref HEAD",
                                    timeoutMs = 10_000L,
                                    workingDir = workDir
                                )
                            }.getOrNull()
                        }
                        val branchName = branchRes?.output?.trim()?.takeIf {
                            branchRes.exitCode == 0 && it.isNotBlank() && !it.contains("fatal", ignoreCase = true)
                        } ?: "main"
                        val lines = statusRes.output.lines().filter { it.isNotBlank() }
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

    fun openGitConfig() {
        coroutineScope.launch {
            val workDir = projectDir.absolutePath
            withContext(Dispatchers.IO) {
                LocalTerminalBridge.instance.init(context.applicationContext)
                gitUserName = LocalTerminalBridge.instance
                    .executeCommand("git config user.name", timeoutMs = 5_000L, workingDir = workDir)
                    .let { if (it.exitCode == 0) it.output.trim() else "" }
                gitUserEmail = LocalTerminalBridge.instance
                    .executeCommand("git config user.email", timeoutMs = 5_000L, workingDir = workDir)
                    .let { if (it.exitCode == 0) it.output.trim() else "" }
                gitRemoteUrl = LocalTerminalBridge.instance
                    .executeCommand("git remote get-url origin", timeoutMs = 5_000L, workingDir = workDir)
                    .let { res ->
                        if (res.exitCode == 0) res.output.trim().takeUnless { it.contains("fatal", ignoreCase = true) } ?: ""
                        else ""
                    }
            }
            showGitConfigDialog = true
        }
    }

    fun inspectDiff(changeLine: String) {
        val (_, parsedPath) = parseGitStatusLine(changeLine)
        val rawPath = parsedPath.substringAfter("-> ").trim().removeSurrounding("\"")
        if (rawPath.isBlank()) return
        coroutineScope.launch {
            isLoading = true
            val workDir = projectDir.absolutePath
            val diffText = withContext(Dispatchers.IO) {
                LocalTerminalBridge.instance.init(context.applicationContext)
                val quotedFile = shellQuote(rawPath)
                val headDiff = LocalTerminalBridge.instance.executeCommand(
                    command = "git diff HEAD -- $quotedFile",
                    timeoutMs = 10_000L,
                    workingDir = workDir
                )
                val validHeadDiff = headDiff.exitCode == 0 &&
                    headDiff.output.isNotBlank() &&
                    !headDiff.output.contains("fatal:", ignoreCase = true)

                if (validHeadDiff) {
                    headDiff.output
                } else {
                    val cachedDiff = LocalTerminalBridge.instance.executeCommand(
                        command = "git diff --cached -- $quotedFile",
                        timeoutMs = 10_000L,
                        workingDir = workDir
                    )
                    if (cachedDiff.exitCode == 0 && cachedDiff.output.isNotBlank()) {
                        cachedDiff.output
                    } else {
                        // Untracked or initial unborn-HEAD file preview as unified addition diff
                        val f = File(projectDir, rawPath)
                        if (f.exists() && f.isFile && f.length() < 120_000L) {
                            val contentLines = runCatching { f.readLines() }.getOrDefault(emptyList())
                            buildString {
                                appendLine("--- /dev/null")
                                appendLine("+++ b/$rawPath")
                                appendLine("@@ -0,0 +1,${contentLines.size} @@")
                                contentLines.forEach { appendLine("+$it") }
                            }
                        } else {
                            ""
                        }
                    }
                }
            }
            isLoading = false
            if (onOpenDiff != null) {
                onOpenDiff(File(projectDir, rawPath).absolutePath, diffText, false)
            } else {
                Toast.makeText(context, "Opened diff for $rawPath", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(projectDir.absolutePath) {
        refreshStatus()
    }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(GitBg)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("ide_git_panel")
        ) {
            // Header row matching AntiGem 01:56
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Git Control",
                        color = GitTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isRepoInitialized && gitStatus.branch.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = GitAccent.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = gitStatus.branch,
                                color = GitAccent,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { openGitConfig() },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("git_settings_button")
                    ) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Git Settings",
                            tint = GitTextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(
                        onClick = { refreshStatus() },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("git_refresh_button")
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh Git Status",
                            tint = GitTextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    if (!embeddedInSidebar) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("git_close_button")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close Git Panel",
                                tint = GitTextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = GitLavender,
                    trackColor = GitCardBg
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (statusError != null) {
                Surface(
                    color = Color(0xFF7F1D1D).copy(alpha = 0.30f),
                    border = BorderStroke(1.dp, GitRed.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusError ?: "",
                        color = Color(0xFFFCA5A5),
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (!isRepoInitialized && statusError == null) {
                // Clean AntiGem "Not a Git Repository" empty card (01:56)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(GitCardBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccountTree,
                                contentDescription = null,
                                tint = GitTextSecondary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Not a Git Repository",
                            color = GitTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Initialize a Git repository to track file changes, view diffs, and commit.",
                            color = GitTextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isLoading = true
                                    runCatching {
                                        LocalTerminalBridge.instance.init(context.applicationContext)
                                        withContext(Dispatchers.IO) {
                                            LocalTerminalBridge.instance.executeCommand(
                                                command = "git init",
                                                timeoutMs = 15_000L,
                                                workingDir = projectDir.absolutePath
                                            )
                                        }
                                    }.onFailure { err ->
                                        statusError = err.message
                                    }
                                    isLoading = false
                                    refreshStatus()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GitLavender,
                                contentColor = Color(0xFF0D0E13)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("git_init_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Initialize Git Repository", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Quick Sync Actions Row (Pull / Push)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                statusError = null
                                val res = withContext(Dispatchers.IO) {
                                    LocalTerminalBridge.instance.init(context.applicationContext)
                                    LocalTerminalBridge.instance.executeCommand(
                                        command = "git pull",
                                        timeoutMs = 25_000L,
                                        workingDir = projectDir.absolutePath
                                    )
                                }
                                isLoading = false
                                if (res.exitCode != 0) {
                                    statusError = res.output.lines().lastOrNull()?.take(140)
                                        ?.ifBlank { "Git pull failed (exit ${res.exitCode})" }
                                        ?: "Git pull failed (exit ${res.exitCode})"
                                } else {
                                    Toast.makeText(context, "Git pull completed", Toast.LENGTH_SHORT).show()
                                }
                                refreshStatus()
                            }
                        },
                        enabled = !isLoading,
                        border = BorderStroke(1.dp, GitBorder),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = GitAccent, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pull", color = GitTextPrimary, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                statusError = null
                                val res = withContext(Dispatchers.IO) {
                                    LocalTerminalBridge.instance.init(context.applicationContext)
                                    LocalTerminalBridge.instance.executeCommand(
                                        command = "git push",
                                        timeoutMs = 25_000L,
                                        workingDir = projectDir.absolutePath
                                    )
                                }
                                isLoading = false
                                if (res.exitCode != 0) {
                                    statusError = res.output.lines().lastOrNull()?.take(140)
                                        ?.ifBlank { "Git push failed (exit ${res.exitCode})" }
                                        ?: "Git push failed (exit ${res.exitCode})"
                                } else {
                                    Toast.makeText(context, "Git push completed", Toast.LENGTH_SHORT).show()
                                }
                                refreshStatus()
                            }
                        },
                        enabled = !isLoading,
                        border = BorderStroke(1.dp, GitBorder),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = GitGreen, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Push", color = GitTextPrimary, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    placeholder = { Text("Commit message...", fontSize = 12.5.sp, color = GitTextSecondary) },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = GitCardBg,
                        unfocusedContainerColor = GitCardBg,
                        focusedTextColor = GitTextPrimary,
                        unfocusedTextColor = GitTextPrimary,
                        focusedBorderColor = GitAccent,
                        unfocusedBorderColor = GitBorder,
                        cursorColor = GitAccent
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
                                            command = "git add -A && git commit -m ${shellQuote(msg)}",
                                            timeoutMs = 20_000L,
                                            workingDir = projectDir.absolutePath
                                        )
                                    }
                                    if (res.exitCode != 0) {
                                        statusError = res.output.lines().lastOrNull()?.take(140)
                                            ?.ifBlank { "Commit failed (exit ${res.exitCode})" }
                                            ?: "Commit failed (exit ${res.exitCode})"
                                    } else {
                                        commitMessage = ""
                                        Toast.makeText(context, "Committed changes", Toast.LENGTH_SHORT).show()
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GitLavender,
                        contentColor = Color(0xFF0D0E13)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Commit All Changes", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "CHANGES (${gitStatus.unstagedFiles.size})",
                    color = GitTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (gitStatus.unstagedFiles.isEmpty() && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 20.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text("Working tree clean", color = GitTextSecondary, fontSize = 12.5.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(gitStatus.unstagedFiles) { rawLine ->
                            val (statusBadge, filePath) = parseGitStatusLine(rawLine)
                            val badgeColor = when {
                                statusBadge.contains("?") || statusBadge.contains("A") -> GitGreen
                                statusBadge.contains("D") -> GitRed
                                else -> GitYellow
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { inspectDiff(rawLine) }
                                    .padding(horizontal = 6.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = badgeColor.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = statusBadge,
                                        color = badgeColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = filePath,
                                    color = GitTextPrimary,
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (embeddedInSidebar) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
    } else {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = GitBg,
            border = BorderStroke(1.dp, GitBorder),
            shadowElevation = 12.dp
        ) {
            content()
        }
    }

    if (showGitConfigDialog) {
        Dialog(onDismissRequest = { showGitConfigDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF171A24),
                border = BorderStroke(1.dp, GitBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Git Configuration", color = GitTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = gitUserName,
                        onValueChange = { gitUserName = it },
                        label = { Text("user.name", fontSize = 11.5.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GitTextPrimary,
                            unfocusedTextColor = GitTextPrimary,
                            focusedBorderColor = GitAccent,
                            unfocusedBorderColor = GitBorder
                        )
                    )
                    OutlinedTextField(
                        value = gitUserEmail,
                        onValueChange = { gitUserEmail = it },
                        label = { Text("user.email", fontSize = 11.5.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GitTextPrimary,
                            unfocusedTextColor = GitTextPrimary,
                            focusedBorderColor = GitAccent,
                            unfocusedBorderColor = GitBorder
                        )
                    )
                    OutlinedTextField(
                        value = gitRemoteUrl,
                        onValueChange = { gitRemoteUrl = it },
                        label = { Text("origin URL", fontSize = 11.5.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GitTextPrimary,
                            unfocusedTextColor = GitTextPrimary,
                            focusedBorderColor = GitAccent,
                            unfocusedBorderColor = GitBorder
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showGitConfigDialog = false }) {
                            Text("Cancel", color = GitTextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val workDir = projectDir.absolutePath
                                    withContext(Dispatchers.IO) {
                                        LocalTerminalBridge.instance.init(context.applicationContext)
                                        if (gitUserName.isNotBlank()) {
                                            LocalTerminalBridge.instance.executeCommand(
                                                command = "git config user.name ${shellQuote(gitUserName.trim())}",
                                                timeoutMs = 5_000L,
                                                workingDir = workDir
                                            )
                                        }
                                        if (gitUserEmail.isNotBlank()) {
                                            LocalTerminalBridge.instance.executeCommand(
                                                command = "git config user.email ${shellQuote(gitUserEmail.trim())}",
                                                timeoutMs = 5_000L,
                                                workingDir = workDir
                                            )
                                        }
                                        if (gitRemoteUrl.isNotBlank()) {
                                            val setRes = LocalTerminalBridge.instance.executeCommand(
                                                command = "(git remote set-url origin ${shellQuote(gitRemoteUrl.trim())} || git remote add origin ${shellQuote(gitRemoteUrl.trim())})",
                                                timeoutMs = 5_000L,
                                                workingDir = workDir
                                            )
                                            IdeDaemonLogBuffer.append("INFO", "Updated git remote origin (${setRes.exitCode})")
                                        }
                                    }
                                    showGitConfigDialog = false
                                    Toast.makeText(context, "Git config saved", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GitLavender, contentColor = Color(0xFF0D0E13))
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
