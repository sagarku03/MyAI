package com.jarvisai.assistant.ide

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.ui.files.FileManagerOperations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private val SidebarBg = Color(0xFF11131A)
private val SidebarCardBg = Color(0xFF171A24)
private val SidebarActiveCardBg = Color(0xFF1F263A)
private val SidebarDivider = Color(0xFF222634)
private val SidebarAccent = Color(0xFF8AB4F8)
private val SidebarLavender = Color(0xFFC4B5FD)
private val SidebarTextPrimary = Color(0xFFE6EDF3)
private val SidebarTextSecondary = Color(0xFF8B949E)
private val SidebarDanger = Color(0xFFF87171)

private val IGNORED_HEAVY_DIRS = setOf(
    "node_modules", ".git", ".gradle", ".cache", "__pycache__", "build", ".idea"
)
private const val MAX_ENTRIES_PER_DIR = 250
private const val PREF_RECENT_PROJECTS = "jarvis_ide_recent_projects"
private const val KEY_RECENT_PATHS = "recent_project_paths"

private enum class SidebarMode {
    EXPLORER,
    GIT
}

private data class CreateTargetRequest(
    val parentDir: File,
    val isFolder: Boolean
)

@Composable
fun ProjectSidebar(
    rootDirectory: File,
    activeFilePath: String? = null,
    onFileClick: (File) -> Unit,
    onSelectProjectRoot: (File) -> Unit = {},
    onBrowseProject: () -> Unit = {},
    onNewProject: () -> Unit = {},
    onOpenTerminalInDir: (File) -> Unit = {},
    onOpenDiff: ((filePath: String, rawDiff: String, isStaged: Boolean) -> Unit)? = null,
    onFileMutated: (oldFile: File, newFile: File?) -> Unit = { _, _ -> },
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sidebarMode by remember { mutableStateOf(SidebarMode.EXPLORER) }
    var isProjectSwitcherExpanded by remember { mutableStateOf(false) }

    var rootNodes by remember(rootDirectory.absolutePath) { mutableStateOf<List<FileNode>>(emptyList()) }
    var isLoading by remember(rootDirectory.absolutePath) { mutableStateOf(true) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    var collapseAllNonce by remember { mutableIntStateOf(0) }
    var lastCreatedParentPath by remember { mutableStateOf<String?>(null) }

    var recentProjects by remember { mutableStateOf<List<File>>(emptyList()) }
    var createRequest by remember { mutableStateOf<CreateTargetRequest?>(null) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var detailsTarget by remember { mutableStateOf<File?>(null) }

    // Record current root in recent projects & scan discovered projects
    LaunchedEffect(rootDirectory.absolutePath, refreshNonce) {
        withContext(Dispatchers.IO) {
            recordRecentProject(context, rootDirectory)
            recentProjects = loadRecentAndDiscoveredProjects(context, rootDirectory)
        }
    }

    LaunchedEffect(rootDirectory.absolutePath, refreshNonce) {
        isLoading = true
        rootNodes = withContext(Dispatchers.IO) {
            listImmediateChildren(rootDirectory)
        }
        isLoading = false
    }

    val homeDirPath = remember {
        runCatching { LocalEnvironmentManager.getHomeDir(context).absolutePath }.getOrDefault("")
    }

    fun formatCompactPath(dir: File): String {
        val abs = dir.absolutePath
        return if (homeDirPath.isNotBlank() && abs.startsWith(homeDirPath)) {
            "~" + abs.removePrefix(homeDirPath).ifEmpty { "/" }
        } else {
            abs
        }
    }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.84f)
            .widthIn(max = 324.dp)
            .testTag("ide_project_sidebar"),
        color = SidebarBg,
        shadowElevation = 16.dp
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // =============================================================
                // 1. PROJECT SWITCHER HEADER (AntiGem 00:43, 00:48)
                // =============================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isProjectSwitcherExpanded = !isProjectSwitcherExpanded }
                        .padding(start = 12.dp, end = 6.dp, top = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SidebarActiveCardBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = SidebarAccent,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rootDirectory.name.ifBlank { "workspace" },
                            color = SidebarTextPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatCompactPath(rootDirectory),
                            color = SidebarTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { isProjectSwitcherExpanded = !isProjectSwitcherExpanded },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isProjectSwitcherExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Switch Project",
                            tint = SidebarTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("sidebar_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Sidebar",
                            tint = SidebarTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // =============================================================
                // 2. EXPANDABLE "Available Projects" DRAWER MENU (AntiGem 00:48, 02:02)
                // =============================================================
                AnimatedVisibility(visible = isProjectSwitcherExpanded) {
                    Surface(
                        color = SidebarCardBg,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, SidebarDivider),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Available Projects",
                                color = SidebarTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )

                            // Browse / File Manager...
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        isProjectSwitcherExpanded = false
                                        onBrowseProject()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 9.dp)
                                    .testTag("sidebar_browse_project_button"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FolderOpen,
                                    contentDescription = null,
                                    tint = SidebarLavender,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Browse / File Manager...",
                                    color = SidebarTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // + New Project
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        isProjectSwitcherExpanded = false
                                        onNewProject()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 9.dp)
                                    .testTag("sidebar_new_project_button"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CreateNewFolder,
                                    contentDescription = null,
                                    tint = SidebarAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "New Project...",
                                    color = SidebarTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (recentProjects.isNotEmpty()) {
                                HorizontalDivider(
                                    color = SidebarDivider,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                ) {
                                    recentProjects.take(6).forEach { proj ->
                                        val isCurrent = proj.absolutePath == rootDirectory.absolutePath
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isCurrent) SidebarActiveCardBg else Color.Transparent)
                                                .clickable {
                                                    isProjectSwitcherExpanded = false
                                                    if (!isCurrent) {
                                                        onSelectProjectRoot(proj)
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Folder,
                                                contentDescription = null,
                                                tint = if (isCurrent) SidebarAccent else Color(0xFFFBBF24),
                                                modifier = Modifier.size(17.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = proj.name.ifBlank { "workspace" },
                                                    color = if (isCurrent) SidebarAccent else SidebarTextPrimary,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = formatCompactPath(proj),
                                                    color = SidebarTextSecondary,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (!isCurrent) {
                                                IconButton(
                                                    onClick = {
                                                        removeRecentProject(context, proj)
                                                        recentProjects = recentProjects.filter { it.absolutePath != proj.absolutePath }
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Remove from recent",
                                                        tint = SidebarTextSecondary,
                                                        modifier = Modifier.size(14.dp)
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

                // =============================================================
                // 3. SEGMENTED SWITCHER: Explorer | Git (AntiGem 00:43, 01:56)
                // =============================================================
                Surface(
                    color = SidebarCardBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SidebarModeTab(
                            label = "Explorer",
                            icon = Icons.Outlined.Folder,
                            selected = sidebarMode == SidebarMode.EXPLORER,
                            onClick = { sidebarMode = SidebarMode.EXPLORER },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sidebar_tab_explorer")
                        )
                        SidebarModeTab(
                            label = "Git",
                            icon = Icons.Outlined.AccountTree,
                            selected = sidebarMode == SidebarMode.GIT,
                            onClick = { sidebarMode = SidebarMode.GIT },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sidebar_tab_git")
                        )
                    }
                }

                HorizontalDivider(color = SidebarDivider, thickness = 1.dp)

                // =============================================================
                // 4. EXPLORER OR EMBEDDED GIT BODY
                // =============================================================
                if (sidebarMode == SidebarMode.GIT) {
                    GitSourceControlView(
                        projectDir = rootDirectory,
                        embeddedInSidebar = true,
                        onOpenDiff = onOpenDiff,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    // FILES sub-header + 4 action buttons (New File, New Folder, Refresh, Collapse All)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "FILES",
                            color = SidebarTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { createRequest = CreateTargetRequest(rootDirectory, isFolder = false) },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("sidebar_new_file_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.NoteAdd,
                                    contentDescription = "New File",
                                    tint = SidebarTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { createRequest = CreateTargetRequest(rootDirectory, isFolder = true) },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("sidebar_new_folder_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CreateNewFolder,
                                    contentDescription = "New Folder",
                                    tint = SidebarTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { refreshNonce++ },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("sidebar_refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Explorer",
                                    tint = SidebarTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { collapseAllNonce++ },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("sidebar_collapse_all_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UnfoldLess,
                                    contentDescription = "Collapse All",
                                    tint = SidebarTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    when {
                        isLoading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    color = SidebarLavender,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        rootNodes.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderOpen,
                                        contentDescription = null,
                                        tint = SidebarTextSecondary,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No files in this folder",
                                        color = SidebarTextSecondary,
                                        fontSize = 12.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = { createRequest = CreateTargetRequest(rootDirectory, isFolder = false) },
                                        border = BorderStroke(1.dp, SidebarDivider)
                                    ) {
                                        Text("Create File", color = SidebarAccent, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                items(rootNodes, key = { it.path }) { node ->
                                    LazyFileNodeItem(
                                        node = node,
                                        depth = 0,
                                        activeFilePath = activeFilePath,
                                        refreshNonce = refreshNonce,
                                        collapseAllNonce = collapseAllNonce,
                                        autoExpandPath = lastCreatedParentPath,
                                        onFileClick = onFileClick,
                                        onSetAsRoot = onSelectProjectRoot,
                                        onRequestCreate = { parentDir, isFolder ->
                                            createRequest = CreateTargetRequest(parentDir, isFolder)
                                        },
                                        onOpenTerminal = onOpenTerminalInDir,
                                        onRename = { renameTarget = it },
                                        onShare = { FileManagerOperations.shareFileExternal(context, it) },
                                        onDetails = { detailsTarget = it },
                                        onDelete = { deleteTarget = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Crisp 1dp right divider
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(SidebarDivider)
            )
        }
    }

    // =========================================================================
    // CENTERED-ICON ANTIGEM DIALOGS (Create File/Folder, Rename, Delete, Details)
    // =========================================================================
    createRequest?.let { req ->
        var nameInput by remember(req) { mutableStateOf("") }
        var errorMsg by remember(req) { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { createRequest = null }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SidebarCardBg,
                border = BorderStroke(1.dp, SidebarDivider),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SidebarLavender.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (req.isFolder) Icons.Outlined.CreateNewFolder else Icons.AutoMirrored.Outlined.NoteAdd,
                            contentDescription = null,
                            tint = SidebarLavender,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (req.isFolder) "Create Folder" else "Create File",
                        color = SidebarTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "In ${req.parentDir.name.ifBlank { "workspace" }}",
                        color = SidebarTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            errorMsg = null
                        },
                        placeholder = {
                            Text(
                                text = if (req.isFolder) "e.g. components" else "e.g. main.py or index.html",
                                color = SidebarTextSecondary
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SidebarTextPrimary,
                            unfocusedTextColor = SidebarTextPrimary,
                            focusedBorderColor = SidebarLavender,
                            unfocusedBorderColor = SidebarDivider,
                            cursorColor = SidebarLavender
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorMsg != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(errorMsg ?: "", color = SidebarDanger, fontSize = 11.5.sp)
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { createRequest = null },
                            border = BorderStroke(1.dp, SidebarDivider),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = SidebarTextSecondary)
                        }
                        Button(
                            onClick = {
                                val cleaned = nameInput.trim().trimStart('/')
                                if (cleaned.isBlank()) {
                                    errorMsg = "Enter a valid name"
                                    return@Button
                                }
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            if (!req.parentDir.exists()) req.parentDir.mkdirs()
                                            val target = File(req.parentDir, cleaned)
                                            if (req.isFolder) {
                                                target.mkdirs()
                                            } else {
                                                target.parentFile?.mkdirs()
                                                if (!target.exists()) target.createNewFile()
                                            }
                                            target
                                        }
                                    }
                                    result.onSuccess { created ->
                                        lastCreatedParentPath = req.parentDir.absolutePath
                                        createRequest = null
                                        refreshNonce++
                                        if (!req.isFolder) {
                                            onFileClick(created)
                                        }
                                    }.onFailure { err ->
                                        errorMsg = err.message ?: "Creation failed"
                                    }
                                }
                            },
                            enabled = nameInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SidebarLavender,
                                contentColor = Color(0xFF0D0E13)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Create", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        var newName by remember(target) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = SidebarCardBg,
            title = { Text("Rename", color = SidebarTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SidebarTextPrimary,
                        unfocusedTextColor = SidebarTextPrimary,
                        focusedBorderColor = SidebarLavender,
                        unfocusedBorderColor = SidebarDivider
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newName.trim()
                        if (trimmed.isNotEmpty() && trimmed != target.name) {
                            val dest = File(target.parentFile, trimmed)
                            if (target.renameTo(dest)) {
                                onFileMutated(target, dest)
                                refreshNonce++
                            } else {
                                Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                        renameTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SidebarLavender,
                        contentColor = Color(0xFF0D0E13)
                    )
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", color = SidebarTextSecondary)
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = SidebarCardBg,
            title = { Text("Delete ${target.name}?", color = SidebarTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = if (target.isDirectory) {
                        "This folder and all of its contents will be permanently deleted."
                    } else {
                        "This file will be permanently deleted from workspace storage."
                    },
                    color = SidebarTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { runCatching { target.deleteRecursively() } }
                            onFileMutated(target, null)
                            deleteTarget = null
                            refreshNonce++
                            Toast.makeText(context, "Deleted ${target.name}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SidebarDanger, contentColor = Color.White)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = SidebarTextSecondary)
                }
            }
        )
    }

    detailsTarget?.let { target ->
        val sizeText = remember(target) {
            if (target.isDirectory) {
                FileManagerOperations.getDirectoryItemCountLabel(target, showHidden = true)
            } else {
                FileManagerOperations.formatBytes(target.length())
            }
        }
        val modifiedText = remember(target) { FileManagerOperations.formatTimestamp(target.lastModified()) }
        AlertDialog(
            onDismissRequest = { detailsTarget = null },
            containerColor = SidebarCardBg,
            title = { Text(target.name, color = SidebarTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SidebarDetailRow("Type", if (target.isDirectory) "Directory" else target.extension.uppercase(Locale.ROOT).ifBlank { "File" })
                    SidebarDetailRow(if (target.isDirectory) "Contents" else "Size", sizeText)
                    SidebarDetailRow("Modified", modifiedText)
                    SidebarDetailRow("Path", target.absolutePath)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    FileManagerOperations.copyPathToClipboard(context, target.absolutePath)
                    detailsTarget = null
                }) {
                    Text("Copy Path", color = SidebarAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { detailsTarget = null }) {
                    Text("Close", color = SidebarTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SidebarDetailRow(label: String, value: String) {
    Column {
        Text(label, color = SidebarTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = SidebarTextPrimary, fontSize = 12.5.sp)
    }
}

@Composable
private fun SidebarModeTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) SidebarActiveCardBg else Color.Transparent,
        shape = RoundedCornerShape(9.dp),
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) SidebarAccent else SidebarTextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (selected) SidebarTextPrimary else SidebarTextSecondary,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun LazyFileNodeItem(
    node: FileNode,
    depth: Int,
    activeFilePath: String?,
    refreshNonce: Int,
    collapseAllNonce: Int,
    autoExpandPath: String?,
    onFileClick: (File) -> Unit,
    onSetAsRoot: (File) -> Unit,
    onRequestCreate: (parentDir: File, isFolder: Boolean) -> Unit,
    onOpenTerminal: (File) -> Unit,
    onRename: (File) -> Unit,
    onShare: (File) -> Unit,
    onDetails: (File) -> Unit,
    onDelete: (File) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isExpanded by remember(node.path) { mutableStateOf(false) }
    var isLoadingChildren by remember(node.path) { mutableStateOf(false) }
    var children by remember(node.path) { mutableStateOf<List<FileNode>>(emptyList()) }
    var hasLoadedChildren by remember(node.path) { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(collapseAllNonce) {
        if (collapseAllNonce > 0) {
            isExpanded = false
        }
    }

    LaunchedEffect(autoExpandPath) {
        if (node.isDirectory && autoExpandPath == node.path) {
            isExpanded = true
        }
    }

    LaunchedEffect(refreshNonce, isExpanded) {
        if (node.isDirectory) {
            if (isExpanded) {
                if (!hasLoadedChildren) isLoadingChildren = true
                children = withContext(Dispatchers.IO) {
                    listImmediateChildren(node.file)
                }
                hasLoadedChildren = true
                isLoadingChildren = false
            } else if (refreshNonce > 0) {
                hasLoadedChildren = false
                children = emptyList()
            }
        }
    }

    val isActiveFile = !node.isDirectory && node.path == activeFilePath
    val visualDepth = depth.coerceAtMost(7)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isActiveFile) SidebarActiveCardBg else Color.Transparent)
                .clickable {
                    if (node.isDirectory) {
                        val nextExpanded = !isExpanded
                        isExpanded = nextExpanded
                        if (nextExpanded && !hasLoadedChildren && !isLoadingChildren) {
                            isLoadingChildren = true
                            scope.launch {
                                val loaded = withContext(Dispatchers.IO) {
                                    listImmediateChildren(node.file)
                                }
                                children = loaded
                                hasLoadedChildren = true
                                isLoadingChildren = false
                            }
                        }
                    } else {
                        onFileClick(node.file)
                    }
                }
                .padding(end = 2.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indent vertical guides matching AntiGem tree (00:58-01:10)
            if (visualDepth > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                repeat(visualDepth) {
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height(22.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(SidebarDivider)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(6.dp))
            }

            if (node.isDirectory) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = SidebarTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(17.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(18.dp))
                CodeTreeFileBadge(fileName = node.name)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = node.name,
                color = when {
                    isActiveFile -> SidebarAccent
                    node.isDirectory -> SidebarTextPrimary
                    else -> Color(0xFFCBD5E1)
                },
                fontSize = 13.sp,
                fontWeight = if (isActiveFile || node.isDirectory) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Item actions",
                        tint = SidebarTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(Color(0xFF1A1E2B))
                ) {
                    if (node.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("New File", color = SidebarTextPrimary, fontSize = 13.5.sp) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null, tint = SidebarTextPrimary, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                menuExpanded = false
                                onRequestCreate(node.file, false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("New Folder", color = SidebarTextPrimary, fontSize = 13.5.sp) },
                            leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, tint = SidebarTextPrimary, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                menuExpanded = false
                                onRequestCreate(node.file, true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Terminal", color = SidebarTextPrimary, fontSize = 13.5.sp) },
                            leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp)) },
                            onClick = {
                                menuExpanded = false
                                onOpenTerminal(node.file)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Open as Root", color = SidebarTextPrimary, fontSize = 13.5.sp) },
                            leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = SidebarAccent, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                menuExpanded = false
                                onSetAsRoot(node.file)
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Open", color = SidebarTextPrimary, fontSize = 13.5.sp) },
                            leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null, tint = SidebarAccent, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                menuExpanded = false
                                onFileClick(node.file)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share", color = SidebarTextPrimary, fontSize = 13.5.sp) },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null, tint = SidebarTextPrimary, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                menuExpanded = false
                                onShare(node.file)
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text("Rename", color = SidebarTextPrimary, fontSize = 13.5.sp) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = SidebarTextPrimary, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onRename(node.file)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Details", color = SidebarTextPrimary, fontSize = 13.5.sp) },
                        leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null, tint = SidebarTextPrimary, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onDetails(node.file)
                        }
                    )
                    HorizontalDivider(color = SidebarDivider)
                    DropdownMenuItem(
                        text = { Text("Delete", color = SidebarDanger, fontSize = 13.5.sp) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = SidebarDanger, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onDelete(node.file)
                        }
                    )
                }
            }
        }

        if (node.isDirectory && isExpanded) {
            if (isLoadingChildren) {
                Text(
                    text = "Loading...",
                    color = SidebarTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = ((visualDepth + 1) * 14 + 26).dp, bottom = 4.dp)
                )
            } else {
                children.forEach { child ->
                    LazyFileNodeItem(
                        node = child,
                        depth = depth + 1,
                        activeFilePath = activeFilePath,
                        refreshNonce = refreshNonce,
                        collapseAllNonce = collapseAllNonce,
                        autoExpandPath = autoExpandPath,
                        onFileClick = onFileClick,
                        onSetAsRoot = onSetAsRoot,
                        onRequestCreate = onRequestCreate,
                        onOpenTerminal = onOpenTerminal,
                        onRename = onRename,
                        onShare = onShare,
                        onDetails = onDetails,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

@Composable
internal fun CodeTreeFileBadge(fileName: String) {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val (label, tint) = when {
        fileName.equals(".gitignore", ignoreCase = true) -> "G" to Color(0xFF94A3B8)
        ext in setOf("kt", "kts") -> "K" to Color(0xFFC084FC)
        ext == "java" -> "J" to Color(0xFFFB923C)
        ext == "py" -> "Py" to Color(0xFF38BDF8)
        ext in setOf("js", "mjs", "cjs") -> "JS" to Color(0xFFFACC15)
        ext in setOf("ts", "tsx") -> "TS" to Color(0xFF60A5FA)
        ext in setOf("html", "htm") -> "<>" to Color(0xFFF87171)
        ext in setOf("css", "scss") -> "#" to Color(0xFF38BDF8)
        ext == "json" -> "{}" to Color(0xFFFBBF24)
        ext == "md" -> "M" to Color(0xFF34D399)
        ext in setOf("sh", "bash", "zsh") -> ">_" to Color(0xFF34D399)
        ext in setOf("c", "cpp", "cc", "h", "hpp") -> "C+" to Color(0xFF60A5FA)
        ext in setOf("xml", "yaml", "yml", "toml", "properties") -> "⚙" to Color(0xFF94A3B8)
        ext in setOf("png", "jpg", "jpeg", "webp", "gif", "svg") -> "Img" to Color(0xFFF472B6)
        else -> "•" to Color(0xFF94A3B8)
    }

    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = tint,
            fontSize = if (label.length > 1) 8.5.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

private fun listImmediateChildren(dir: File): List<FileNode> {
    return runCatching {
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return@runCatching emptyList()
        val files = dir.listFiles() ?: return@runCatching emptyList()
        files
            .asSequence()
            .filter { file ->
                if (!file.isDirectory) true
                else file.name !in IGNORED_HEAVY_DIRS && !isSymlinkLoop(dir, file)
            }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .take(MAX_ENTRIES_PER_DIR)
            .map { file ->
                FileNode(
                    file = file,
                    name = file.name,
                    isDirectory = file.isDirectory,
                    path = file.absolutePath,
                    isExpanded = false,
                    children = emptyList()
                )
            }
            .toList()
    }.getOrDefault(emptyList())
}

private fun isSymlinkLoop(parent: File, child: File): Boolean {
    return runCatching {
        val parentCanon = parent.canonicalPath
        val childCanon = child.canonicalPath
        parentCanon == childCanon || parentCanon.startsWith("$childCanon/")
    }.getOrDefault(false)
}

private fun recordRecentProject(context: Context, dir: File) {
    if (!dir.exists() || !dir.isDirectory) return
    val prefs = context.getSharedPreferences(PREF_RECENT_PROJECTS, Context.MODE_PRIVATE)
    val existing = prefs.getString(KEY_RECENT_PATHS, "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
    val updated = (listOf(dir.absolutePath) + existing.filter { it != dir.absolutePath }).take(10)
    prefs.edit().putString(KEY_RECENT_PATHS, updated.joinToString("|")).apply()
}

private fun removeRecentProject(context: Context, dir: File) {
    val prefs = context.getSharedPreferences(PREF_RECENT_PROJECTS, Context.MODE_PRIVATE)
    val existing = prefs.getString(KEY_RECENT_PATHS, "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
    val updated = existing.filter { it != dir.absolutePath }
    prefs.edit().putString(KEY_RECENT_PATHS, updated.joinToString("|")).apply()
}

private fun loadRecentAndDiscoveredProjects(context: Context, activeRoot: File): List<File> {
    val prefs = context.getSharedPreferences(PREF_RECENT_PROJECTS, Context.MODE_PRIVATE)
    val recent = prefs.getString(KEY_RECENT_PATHS, "")?.split("|")
        ?.map { File(it) }
        ?.filter { it.exists() && it.isDirectory }
        ?: emptyList()

    val discovered = mutableListOf<File>()
    runCatching {
        val projectsDir = LocalEnvironmentManager.getIsolatedProjectsDir(context)
        if (projectsDir.exists() && projectsDir.isDirectory) {
            discovered += projectsDir
            projectsDir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.sortedByDescending { it.lastModified() }
                ?.let { discovered.addAll(it) }
        }
        val home = LocalEnvironmentManager.getHomeDir(context)
        if (home.exists() && home.isDirectory) discovered += home
    }

    val seen = LinkedHashMap<String, File>()
    seen[activeRoot.absolutePath] = activeRoot
    for (d in recent) seen.putIfAbsent(d.absolutePath, d)
    for (d in discovered) seen.putIfAbsent(d.absolutePath, d)
    return seen.values.toList()
}
