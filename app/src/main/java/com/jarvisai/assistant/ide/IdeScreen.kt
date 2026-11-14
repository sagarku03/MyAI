package com.jarvisai.assistant.ide

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jarvisai.assistant.data.ide.RunConfigManager
import com.jarvisai.assistant.data.ide.RunConfiguration
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.WorkspaceSyncManager
import com.jarvisai.assistant.ui.components.LocalTerminalDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object ActiveCodeEditorRegistry {
    @Volatile
    var activeFilePath: String? = null
    @Volatile
    var activeFileTitle: String? = null
    @Volatile
    var activeContent: String = ""
    @Volatile
    var isDirty: Boolean = false
    @Volatile
    var currentProjectPath: String? = null
}

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg", "ico")

@Composable
fun IdeScreen(
    initialFile: File? = null,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentProjectDir by remember {
        mutableStateOf(
            runCatching { LocalEnvironmentManager.getHomeDir(context) }
                .getOrElse { context.filesDir }
        )
    }

    var openTabs by remember { mutableStateOf<List<OpenTab>>(emptyList()) }
    var activeTabPath by remember { mutableStateOf<String?>(null) }
    var activeImageAsset by remember { mutableStateOf<File?>(null) }
    var showSidebar by remember { mutableStateOf(false) }
    var showGitView by remember { mutableStateOf(false) }
    var showTerminalDialog by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showDaemonLogsDialog by remember { mutableStateOf(false) }
    var activeExecutionRequest by remember { mutableStateOf<IdeExecutionRequest?>(null) }

    var newFileNameInput by remember { mutableStateOf("") }
    var editorViewInstance by remember { mutableStateOf<CodeEditorView?>(null) }

    fun syncActiveEditorRegistry(path: String?, title: String?, content: String, dirty: Boolean) {
        ActiveCodeEditorRegistry.activeFilePath = path
        ActiveCodeEditorRegistry.activeFileTitle = title
        ActiveCodeEditorRegistry.activeContent = content
        ActiveCodeEditorRegistry.isDirty = dirty
    }

    fun isBinaryFile(file: File): Boolean {
        return runCatching {
            val bytes = file.inputStream().use { stream ->
                val buf = ByteArray(512)
                val read = stream.read(buf)
                if (read <= 0) ByteArray(0) else buf.copyOf(read)
            }
            bytes.any { it == 0.toByte() }
        }.getOrDefault(false)
    }

    fun openFile(file: File) {
        runCatching {
            if (!file.exists() || file.isDirectory) return
            val ext = file.extension.lowercase()
            if (ext in IMAGE_EXTENSIONS) {
                activeImageAsset = file
                showSidebar = false
                return
            }
            if (isBinaryFile(file)) {
                Toast.makeText(context, "Binary file (${file.name}) cannot be edited as plain text", Toast.LENGTH_SHORT).show()
                return
            }

            activeImageAsset = null
            val existing = openTabs.firstOrNull { it.path == file.absolutePath }
            if (existing != null) {
                activeTabPath = file.absolutePath
                editorViewInstance?.setFile(file, existing.content)
                syncActiveEditorRegistry(existing.path, existing.title, existing.content, existing.isDirty)
            } else {
                val content = runCatching { file.readText() }.getOrDefault("")
                val tab = OpenTab(file.absolutePath, file.name, content)
                openTabs = openTabs + tab
                activeTabPath = file.absolutePath
                editorViewInstance?.setFile(file, content)
                syncActiveEditorRegistry(tab.path, tab.title, tab.content, tab.isDirty)
            }
            showSidebar = false
        }.onFailure { err ->
            IdeDaemonLogBuffer.append("ERROR", "Failed opening ${file.name}: ${err.message}")
            Toast.makeText(context, "Could not open file: ${err.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val onEditorTextUpdated by rememberUpdatedState<(String) -> Unit> { text ->
        val currentPath = activeTabPath ?: return@rememberUpdatedState
        val idx = openTabs.indexOfFirst { it.path == currentPath }
        if (idx >= 0) {
            val existing = openTabs[idx]
            val updatedList = openTabs.toMutableList()
            updatedList[idx] = existing.copy(content = text, isDirty = true)
            openTabs = updatedList
            syncActiveEditorRegistry(existing.path, existing.title, text, true)
        }
    }

    LaunchedEffect(initialFile, editorViewInstance) {
        if (initialFile != null && editorViewInstance != null) {
            openFile(initialFile)
        }
    }

    LaunchedEffect(currentProjectDir) {
        ActiveCodeEditorRegistry.currentProjectPath = currentProjectDir.absolutePath
        WorkspaceSyncManager.updateWorkspaceRoot(currentProjectDir.absolutePath)
    }

    val pendingOpenFile by WorkspaceSyncManager.pendingOpenFile.collectAsState()
    LaunchedEffect(pendingOpenFile, editorViewInstance) {
        val targetPath = pendingOpenFile ?: return@LaunchedEffect
        val targetFile = File(targetPath)
        if (targetFile.exists() && targetFile.isFile) {
            openFile(targetFile)
        }
        WorkspaceSyncManager.clearPendingOpenFile()
    }

    val syncTimestamp by WorkspaceSyncManager.syncTimestamp.collectAsState()

    LaunchedEffect(syncTimestamp) {
        val path = activeTabPath ?: return@LaunchedEffect
        val tab = openTabs.firstOrNull { it.path == path } ?: return@LaunchedEffect
        if (!tab.isDirty) {
            val file = File(path)
            if (file.exists()) {
                val diskContent = withContext(Dispatchers.IO) {
                    runCatching { file.readText() }.getOrDefault("")
                }
                if (diskContent.isNotEmpty() && diskContent != tab.content) {
                    val idx = openTabs.indexOfFirst { it.path == path }
                    if (idx >= 0) {
                        val updatedList = openTabs.toMutableList()
                        updatedList[idx] = tab.copy(content = diskContent, isDirty = false)
                        openTabs = updatedList
                    }
                    editorViewInstance?.setFile(file, diskContent)
                    syncActiveEditorRegistry(tab.path, tab.title, diskContent, false)
                }
            }
        }
    }

    fun saveActiveFile(showEmptyWarning: Boolean = true) {
        val path = activeTabPath
        val tab = path?.let { p -> openTabs.firstOrNull { it.path == p } }
        if (path == null || tab == null) {
            if (showEmptyWarning) {
                Toast.makeText(context, "Open or create a file first to save", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val currentContent = editorViewInstance?.getText() ?: tab.content
        runCatching {
            val file = File(path)
            file.parentFile?.let { if (!it.exists()) it.mkdirs() }
            file.writeText(currentContent)
            val idx = openTabs.indexOfFirst { it.path == path }
            if (idx >= 0) {
                val updatedList = openTabs.toMutableList()
                updatedList[idx] = tab.copy(content = currentContent, isDirty = false)
                openTabs = updatedList
            }
            syncActiveEditorRegistry(tab.path, tab.title, currentContent, false)
            IdeDaemonLogBuffer.append("INFO", "Saved file ${tab.path}")
            Toast.makeText(context, "Saved: ${tab.title}", Toast.LENGTH_SHORT).show()
        }.onFailure { err ->
            IdeDaemonLogBuffer.append("ERROR", "Failed saving ${tab.path}: ${err.message}")
            Toast.makeText(context, "Failed saving: ${err.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun launchRunConfiguration() {
        runCatching {
            if (activeTabPath != null) {
                saveActiveFile(showEmptyWarning = false)
            }
            val activeFile = activeTabPath?.let { File(it) }
            val projectRoot = activeFile?.parentFile ?: currentProjectDir
            val configs = RunConfigManager.loadConfigurations(projectRoot.absolutePath)
            val chosen: RunConfiguration = configs.firstOrNull { it.isDefault }
                ?: configs.firstOrNull()
                ?: RunConfiguration(
                    name = "Workspace Shell",
                    type = "terminal",
                    command = "ls -la",
                    cwd = projectRoot.absolutePath,
                    isDefault = true
                )
            IdeDaemonLogBuffer.append("INFO", "Launching configuration '${chosen.name}' in ${projectRoot.absolutePath}")
            showSidebar = false
            showGitView = false
            activeExecutionRequest = IdeExecutionRequest(
                title = activeFile?.name ?: chosen.name,
                projectPath = projectRoot.absolutePath,
                activeFile = activeFile,
                configuration = chosen
            )
        }.onFailure { err ->
            IdeDaemonLogBuffer.append("ERROR", "Launch run config failed: ${err.message}")
            Toast.makeText(context, "Unable to run: ${err.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // System back closes overlays inside IDE before exiting IDE screen
    val hasInternalOverlay = activeExecutionRequest != null ||
        showGitView ||
        showSidebar ||
        activeImageAsset != null ||
        showDaemonLogsDialog ||
        showNewProjectDialog ||
        showNewFileDialog ||
        showTerminalDialog

    BackHandler(enabled = hasInternalOverlay) {
        when {
            showTerminalDialog -> showTerminalDialog = false
            showDaemonLogsDialog -> showDaemonLogsDialog = false
            showNewProjectDialog -> showNewProjectDialog = false
            showNewFileDialog -> showNewFileDialog = false
            activeExecutionRequest != null -> activeExecutionRequest = null
            showGitView -> showGitView = false
            showSidebar -> showSidebar = false
            activeImageAsset != null -> activeImageAsset = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            editorViewInstance?.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .imePadding()
            .testTag("ide_screen_root")
    ) {
        // --- Top Action Toolbar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111C35))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    showGitView = false
                    showSidebar = !showSidebar
                },
                modifier = Modifier.testTag("ide_toolbar_sidebar")
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Project Files Sidebar",
                    tint = if (showSidebar) Color(0xFF00E5FF) else Color(0xFF38BDF8)
                )
            }
            IconButton(
                onClick = { showNewProjectDialog = true },
                modifier = Modifier.testTag("ide_toolbar_new_project")
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "New Project", tint = Color(0xFF00E5FF))
            }
            IconButton(
                onClick = { showNewFileDialog = true },
                modifier = Modifier.testTag("ide_toolbar_new_file")
            ) {
                Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "New File", tint = Color(0xFFA78BFA))
            }
            IconButton(
                onClick = {
                    showSidebar = false
                    showGitView = !showGitView
                },
                modifier = Modifier.testTag("ide_toolbar_git")
            ) {
                Icon(
                    Icons.Default.Source,
                    contentDescription = "Source Control",
                    tint = if (showGitView) Color(0xFF38BDF8) else Color.White
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = { launchRunConfiguration() },
                modifier = Modifier.testTag("ide_toolbar_run")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Run Project or File", tint = Color(0xFF34D399))
            }
            IconButton(
                onClick = {
                    val didUndo = editorViewInstance?.undo() ?: false
                    if (!didUndo) {
                        Toast.makeText(context, "Nothing to undo", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.testTag("ide_toolbar_undo")
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = Color.LightGray)
            }
            IconButton(
                onClick = {
                    val didRedo = editorViewInstance?.redo() ?: false
                    if (!didRedo) {
                        Toast.makeText(context, "Nothing to redo", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.testTag("ide_toolbar_redo")
            ) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = Color.LightGray)
            }
            IconButton(
                onClick = { saveActiveFile(showEmptyWarning = true) },
                modifier = Modifier.testTag("ide_toolbar_save")
            ) {
                Icon(Icons.Default.Save, contentDescription = "Save File", tint = Color(0xFF38BDF8))
            }
            IconButton(
                onClick = { showDaemonLogsDialog = true },
                modifier = Modifier.testTag("ide_toolbar_logs")
            ) {
                Icon(Icons.Default.BugReport, contentDescription = "Daemon Logs", tint = Color(0xFFFCD34D))
            }
            IconButton(
                onClick = { showTerminalDialog = true },
                modifier = Modifier.testTag("ide_toolbar_terminal")
            ) {
                Icon(Icons.Default.Terminal, contentDescription = "Open Terminal", tint = Color(0xFF34D399))
            }
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.testTag("ide_toolbar_close")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close IDE", tint = Color(0xFF94A3B8))
            }
        }

        // --- Workspace Breadcrumb & Open File Tabs ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F172A))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = { showSidebar = true },
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.padding(end = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFFCD34D), modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = currentProjectDir.name.ifBlank { "workspace" },
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            openTabs.forEach { tab ->
                val isSelected = tab.path == activeTabPath && activeImageAsset == null
                InputChip(
                    selected = isSelected,
                    onClick = {
                        activeImageAsset = null
                        activeTabPath = tab.path
                        editorViewInstance?.setFile(File(tab.path), tab.content)
                        syncActiveEditorRegistry(tab.path, tab.title, tab.content, tab.isDirty)
                    },
                    label = {
                        Text(
                            text = tab.title + if (tab.isDirty) " *" else "",
                            fontSize = 12.sp,
                            color = if (isSelected) Color(0xFF38BDF8) else Color.White
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val remaining = openTabs.filter { it.path != tab.path }
                                openTabs = remaining
                                if (activeTabPath == tab.path) {
                                    val next = remaining.firstOrNull()
                                    activeTabPath = next?.path
                                    if (next != null) {
                                        editorViewInstance?.setFile(File(next.path), next.content)
                                        syncActiveEditorRegistry(next.path, next.title, next.content, next.isDirty)
                                    } else {
                                        editorViewInstance?.setText("")
                                        syncActiveEditorRegistry(null, null, "", false)
                                    }
                                }
                            },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Tab", tint = Color.Gray, modifier = Modifier.size(13.dp))
                        }
                    },
                    colors = InputChipDefaults.inputChipColors(
                        containerColor = if (isSelected) Color(0xFF1E293B) else Color.Transparent
                    ),
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }

        // --- Workspace Body ---
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val imageFile = activeImageAsset
            if (imageFile != null) {
                ImageAssetViewer(
                    file = imageFile,
                    onClose = { activeImageAsset = null },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        CodeEditorView(ctx).apply {
                            onContentChanged = { text -> onEditorTextUpdated(text) }
                        }
                    },
                    update = { view ->
                        if (editorViewInstance !== view) {
                            editorViewInstance = view
                        }
                        view.onContentChanged = { text -> onEditorTextUpdated(text) }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Empty State Launcher Overlay when no file tab is open
                if (openTabs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0A0F1D).copy(alpha = 0.94f))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.widthIn(max = 360.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "Jarvis Mobile Code IDE",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Create a project, open a workspace file, or launch a live terminal session.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.5.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                IdeEmptyActionCard(
                                    icon = Icons.Default.CreateNewFolder,
                                    label = "New Project",
                                    accent = Color(0xFF00E5FF),
                                    onClick = { showNewProjectDialog = true },
                                    modifier = Modifier.weight(1f)
                                )
                                IdeEmptyActionCard(
                                    icon = Icons.AutoMirrored.Filled.NoteAdd,
                                    label = "New File",
                                    accent = Color(0xFFA78BFA),
                                    onClick = { showNewFileDialog = true },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                IdeEmptyActionCard(
                                    icon = Icons.Default.FolderOpen,
                                    label = "Browse Files",
                                    accent = Color(0xFFFCD34D),
                                    onClick = { showSidebar = true },
                                    modifier = Modifier.weight(1f)
                                )
                                IdeEmptyActionCard(
                                    icon = Icons.Default.Terminal,
                                    label = "Terminal",
                                    accent = Color(0xFF34D399),
                                    onClick = { showTerminalDialog = true },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Dismiss Scrim when Sidebar or Git Drawer is open
            if (showSidebar || showGitView) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable {
                            showSidebar = false
                            showGitView = false
                        }
                )
            }

            // Slide-over Project File Tree Sidebar
            if (showSidebar) {
                ProjectSidebar(
                    rootDirectory = currentProjectDir,
                    onFileClick = { openFile(it) },
                    onSelectProjectRoot = { newRoot -> currentProjectDir = newRoot },
                    onClose = { showSidebar = false },
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            // Slide-over Git Source Control Panel
            if (showGitView) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(290.dp)
                        .align(Alignment.CenterEnd)
                ) {
                    GitSourceControlView(
                        projectDir = currentProjectDir,
                        onClose = { showGitView = false }
                    )
                }
            }

            // Bottom Execution Runner Sheet
            val execReq = activeExecutionRequest
            if (execReq != null) {
                IdeExecutionRunnerSheet(
                    request = execReq,
                    onClose = { activeExecutionRequest = null },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            containerColor = Color(0xFF111C35),
            title = {
                Text("Create File in ${currentProjectDir.name}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                OutlinedTextField(
                    value = newFileNameInput,
                    onValueChange = { newFileNameInput = it },
                    placeholder = { Text("e.g. script.py or index.html", color = Color(0xFF64748B)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF334155),
                        cursorColor = Color(0xFF00E5FF)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sanitized = newFileNameInput.trim().replace("/", "_")
                        if (sanitized.isBlank()) return@Button
                        scope.launch {
                            val createdRes = withContext(Dispatchers.IO) {
                                runCatching {
                                    if (!currentProjectDir.exists()) currentProjectDir.mkdirs()
                                    val target = File(currentProjectDir, sanitized)
                                    if (!target.exists()) target.createNewFile()
                                    target
                                }
                            }
                            createdRes.onSuccess { target ->
                                newFileNameInput = ""
                                showNewFileDialog = false
                                openFile(target)
                            }.onFailure { err ->
                                Toast.makeText(context, "Failed: ${err.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = newFileNameInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("Create & Open", color = Color(0xFF070B14), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    if (showNewProjectDialog) {
        NewProjectDialog(
            workspaceRoot = currentProjectDir,
            onProjectCreated = { projectDir, entryFile ->
                currentProjectDir = projectDir
                showNewProjectDialog = false
                openFile(entryFile)
            },
            onDismiss = { showNewProjectDialog = false }
        )
    }

    if (showDaemonLogsDialog) {
        DaemonLogsDialog(onDismiss = { showDaemonLogsDialog = false })
    }

    if (showTerminalDialog) {
        LocalTerminalDialog(onDismiss = { showTerminalDialog = false })
    }
}

@Composable
private fun IdeEmptyActionCard(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = Color(0xFF111C35),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(72.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
