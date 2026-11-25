package com.jarvisai.assistant.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.LocalTerminalBridge
import com.jarvisai.assistant.ui.files.FileExplorerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val WorkspaceModalSurface = Color(0xFF141923)
private val WorkspaceModalCard = Color(0xFF1B2230)
private val WorkspaceModalBorder = Color(0xFF283244)
private val WorkspaceAccent = Color(0xFF60A5FA)
private val WorkspaceAccentSolid = Color(0xFF3B82F6)

private val HIDDEN_SYSTEM_DIRS = setOf(
    "projects", "artifacts", "cache", "tmp", "temp", "bin", "lib", "etc", "var", "usr"
)

/**
 * Discovers user workspaces from both Linux home (`~`) and `~/projects`, deduplicated by canonical path,
 * plus any active custom workspace folder outside those roots.
 */
fun discoverAvailableWorkspaces(
    context: Context,
    activeWorkspacePath: String? = null
): List<File> {
    val homeDir = runCatching { LocalEnvironmentManager.getHomeDir(context) }.getOrNull()
    val projectsDir = runCatching { LocalEnvironmentManager.getIsolatedProjectsDir(context) }.getOrNull()

    val found = linkedMapOf<String, File>()

    fun addDir(dir: File?) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        val key = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
        if (!found.containsKey(key)) {
            found[key] = dir
        }
    }

    // Active custom workspace first if it's not home/projects root itself
    activeWorkspacePath?.takeIf { it.isNotBlank() }?.let { activePath ->
        val f = File(activePath)
        val isRoot = activePath == homeDir?.absolutePath || activePath == projectsDir?.absolutePath
        if (!isRoot && f.exists() && f.isDirectory) {
            addDir(f)
        }
    }

    // Direct project folders inside ~/projects
    projectsDir?.listFiles { f ->
        f.isDirectory && !f.name.startsWith(".") && f.name.lowercase() != "artifacts"
    }?.sortedByDescending { it.lastModified() }?.forEach { addDir(it) }

    // User-created workspace folders directly inside ~ (matching AntiGem's ~/Test, ~/new app, etc.)
    homeDir?.listFiles { f ->
        f.isDirectory &&
            !f.name.startsWith(".") &&
            f.name.lowercase() !in HIDDEN_SYSTEM_DIRS
    }?.sortedByDescending { it.lastModified() }?.forEach { addDir(it) }

    return found.values.toList()
}

/**
 * AntiGem-Style "Select Workspace" Modal Dialog.
 * - Lists General (No Workspace) + all discovered workspaces with path, Active badge, and checkmark
 * - Includes Refresh button, "Use Git Repository..." clone flow, and "Browse or Open Other Folder..."
 */
@Composable
fun WorkspaceSelectorModal(
    workspaces: List<File>,
    activeWorkspacePath: String?,
    isGeneralActive: Boolean,
    onRefresh: () -> Unit,
    onSelectGeneral: () -> Unit,
    onSelectWorkspace: (File) -> Unit,
    onBrowseOtherFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    var showGitCloneDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 580.dp)
                .testTag("workspace_selector_modal"),
            shape = RoundedCornerShape(22.dp),
            color = WorkspaceModalSurface,
            border = BorderStroke(1.dp, WorkspaceModalBorder),
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(WorkspaceAccent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderSpecial,
                                contentDescription = null,
                                tint = WorkspaceAccent,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Select Workspace",
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${workspaces.size} available workspace${if (workspaces.size == 1) "" else "s"}",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("workspace_modal_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh workspaces",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("workspace_modal_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Workspace list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 320.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 1: General (No Workspace)
                    item(key = "ws_general") {
                        WorkspaceRowCard(
                            title = "General (No Workspace)",
                            subtitle = "Chat without attaching to a directory",
                            isSelected = isGeneralActive,
                            isGeneral = true,
                            onClick = {
                                onSelectGeneral()
                                onDismiss()
                            }
                        )
                    }

                    // Existing Workspaces
                    items(workspaces, key = { it.absolutePath }) { dir ->
                        val isSelected = !isGeneralActive && activeWorkspacePath == dir.absolutePath
                        WorkspaceRowCard(
                            title = dir.name.ifBlank { "Workspace" },
                            subtitle = dir.absolutePath,
                            isSelected = isSelected,
                            isGeneral = false,
                            onClick = {
                                onSelectWorkspace(dir)
                                onDismiss()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Use Git Repository...
                OutlinedButton(
                    onClick = { showGitCloneDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("workspace_modal_git_clone_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, WorkspaceAccent.copy(alpha = 0.55f))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudDownload,
                        contentDescription = null,
                        tint = WorkspaceAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Use Git Repository...",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WorkspaceAccent
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Browse or Open Other Folder...
                Button(
                    onClick = {
                        onDismiss()
                        onBrowseOtherFolder()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("workspace_modal_browse_folder_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WorkspaceAccentSolid,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Browse or Open Other Folder...",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showGitCloneDialog) {
        GitRepoCloneWorkspaceDialog(
            onCloned = { clonedDir ->
                showGitCloneDialog = false
                onRefresh()
                onSelectWorkspace(clonedDir)
                onDismiss()
            },
            onDismiss = { showGitCloneDialog = false }
        )
    }
}

@Composable
private fun WorkspaceRowCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    isGeneral: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) WorkspaceAccent.copy(alpha = 0.13f) else WorkspaceModalCard,
        border = BorderStroke(
            width = if (isSelected) 1.4.dp else 0.8.dp,
            color = if (isSelected) WorkspaceAccent.copy(alpha = 0.7f) else WorkspaceModalBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(if (isGeneral) CircleShape else RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) WorkspaceAccent.copy(alpha = 0.22f)
                        else Color(0xFF232C3D)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGeneral) Icons.Outlined.Public else Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (isSelected) WorkspaceAccent else Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 13.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = WorkspaceAccent.copy(alpha = 0.22f)
                        ) {
                            Text(
                                text = "Active",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = WorkspaceAccent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color(0xFF8B98AB),
                    fontFamily = if (isGeneral) FontFamily.SansSerif else FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(WorkspaceAccentSolid),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GitRepoCloneWorkspaceDialog(
    onCloned: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var repoUrl by remember { mutableStateOf("") }
    var folderName by remember { mutableStateOf("") }
    var isCloning by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isCloning) onDismiss() },
        containerColor = WorkspaceModalSurface,
        title = {
            Text(
                text = "Clone Git Repository",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter a public Git repository URL to clone into your workspace.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.5.sp
                )
                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = {
                        repoUrl = it
                        if (folderName.isBlank()) {
                            val inferred = it.trim().removeSuffix("/").substringAfterLast('/').removeSuffix(".git")
                            if (inferred.isNotBlank() && !inferred.contains(':')) {
                                folderName = inferred
                            }
                        }
                    },
                    label = { Text("Repository URL", fontSize = 12.sp) },
                    placeholder = { Text("https://github.com/user/repo", fontSize = 12.sp) },
                    singleLine = true,
                    enabled = !isCloning,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = WorkspaceAccent,
                        unfocusedBorderColor = WorkspaceModalBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name (optional)", fontSize = 12.sp) },
                    singleLine = true,
                    enabled = !isCloning,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = WorkspaceAccent,
                        unfocusedBorderColor = WorkspaceModalBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg!!,
                        color = Color(0xFFF87171),
                        fontSize = 11.5.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isCloning && repoUrl.trim().isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = WorkspaceAccentSolid),
                onClick = {
                    val url = repoUrl.trim()
                    val derivedName = folderName.trim().ifBlank {
                        url.removeSuffix("/").substringAfterLast('/').removeSuffix(".git").ifBlank { "repo_${System.currentTimeMillis() % 1000}" }
                    }.replace(Regex("[^a-zA-Z0-9._-]"), "-")
                    isCloning = true
                    errorMsg = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            val projectsDir = LocalEnvironmentManager.getIsolatedProjectsDir(context).apply { mkdirs() }
                            val destDir = File(projectsDir, derivedName)
                            val safeUrl = url.replace("'", "")
                            val cmd = "git clone --depth 1 '$safeUrl' '${destDir.absolutePath}'"
                            val execRes = LocalTerminalBridge.instance.executeCommand(
                                command = cmd,
                                timeoutMs = 120_000L
                            )
                            if (destDir.exists() && destDir.isDirectory && (destDir.listFiles()?.isNotEmpty() == true)) {
                                Result.success(destDir)
                            } else {
                                Result.failure(IllegalStateException(execRes.output.take(240).ifBlank { "Git clone failed" }))
                            }
                        }
                        isCloning = false
                        result.onSuccess { cloned ->
                            Toast.makeText(context, "Cloned ${cloned.name}", Toast.LENGTH_SHORT).show()
                            onCloned(cloned)
                        }.onFailure { err ->
                            errorMsg = err.message ?: "Clone failed"
                        }
                    }
                }
            ) {
                if (isCloning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cloning...")
                } else {
                    Text("Clone")
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isCloning,
                onClick = onDismiss
            ) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}

/**
 * Window-level host for both the Select Workspace Modal and the full-screen Project Folder Browser,
 * kept outside JarvisApp's Scaffold lambda to keep bytecode lean.
 */
@Composable
fun WorkspaceSelectorOverlaysHost(
    showWorkspaceModal: Boolean,
    showFolderBrowser: Boolean,
    workspaces: List<File>,
    activeWorkspacePath: String?,
    isGeneralActive: Boolean,
    onRefreshWorkspaces: () -> Unit,
    onSelectGeneral: () -> Unit,
    onSelectWorkspace: (File) -> Unit,
    onOpenFolderBrowser: () -> Unit,
    onOpenFileInIde: (File) -> Unit,
    onDismissWorkspaceModal: () -> Unit,
    onDismissFolderBrowser: () -> Unit
) {
    val context = LocalContext.current

    if (showWorkspaceModal) {
        WorkspaceSelectorModal(
            workspaces = workspaces,
            activeWorkspacePath = activeWorkspacePath,
            isGeneralActive = isGeneralActive,
            onRefresh = onRefreshWorkspaces,
            onSelectGeneral = {
                onSelectGeneral()
                onDismissWorkspaceModal()
            },
            onSelectWorkspace = { dir ->
                onSelectWorkspace(dir)
                onDismissWorkspaceModal()
            },
            onBrowseOtherFolder = {
                onDismissWorkspaceModal()
                onOpenFolderBrowser()
            },
            onDismiss = onDismissWorkspaceModal
        )
    }

    if (showFolderBrowser) {
        Dialog(
            onDismissRequest = onDismissFolderBrowser,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0A0D12)
            ) {
                FileExplorerScreen(
                    initialDir = runCatching { LocalEnvironmentManager.getHomeDir(context) }.getOrNull(),
                    isActive = true,
                    isProjectPickerMode = true,
                    onSelectAsProject = { dir ->
                        onSelectWorkspace(dir)
                        onDismissFolderBrowser()
                    },
                    onBack = onDismissFolderBrowser,
                    onOpenFileInIde = { file ->
                        onDismissFolderBrowser()
                        onOpenFileInIde(file)
                    }
                )
            }
        }
    }
}

/**
 * Subtle AntiGem-style centered chat date/time separator (e.g., "Wed, Nov 25, 16:56").
 */
@Composable
fun ChatTimestampSeparator(
    timestampMs: Long,
    modifier: Modifier = Modifier
) {
    val formatted = remember(timestampMs) {
        runCatching {
            SimpleDateFormat("EEE, MMM d, HH:mm", Locale.getDefault()).format(Date(timestampMs))
        }.getOrDefault("")
    }
    if (formatted.isBlank()) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatted,
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

