package com.jarvisai.assistant.ide

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jarvisai.assistant.data.ide.RunConfigManager
import com.jarvisai.assistant.data.ide.RunConfiguration
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.WorkspaceSyncManager
import com.jarvisai.assistant.ui.components.LocalTerminalDialog
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
}

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg", "ico")

@Composable
fun IdeScreen(
    initialFile: File? = null,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var currentProjectDir by remember { mutableStateOf(LocalEnvironmentManager.getHomeDir(context)) }

    var openTabs by remember { mutableStateOf<List<OpenTab>>(emptyList()) }
    var activeTabPath by remember { mutableStateOf<String?>(null) }
    var activeImageAsset by remember { mutableStateOf<File?>(null) }
    var showSidebar by remember { mutableStateOf(false) }
    var showGitView by remember { mutableStateOf(false) }
    var showTerminalDialog by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showDaemonLogsDialog by remember { mutableStateOf(false) }
    var activeExecutionRequest by remember { mutableStateOf<IdeExecutionRequest?>(null) }

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
            val content = try { file.readText() } catch (_: Exception) { "" }
            val tab = OpenTab(file.absolutePath, file.name, content)
            openTabs = openTabs + tab
            activeTabPath = file.absolutePath
            editorViewInstance?.setFile(file, content)
            syncActiveEditorRegistry(tab.path, tab.title, tab.content, tab.isDirty)
        }
        showSidebar = false
    }

    LaunchedEffect(initialFile, editorViewInstance) {
        if (initialFile != null && editorViewInstance != null) {
            openFile(initialFile)
        }
    }

    val syncTimestamp by WorkspaceSyncManager.syncTimestamp.collectAsState()

    LaunchedEffect(syncTimestamp) {
        val path = activeTabPath ?: return@LaunchedEffect
        val tab = openTabs.firstOrNull { it.path == path } ?: return@LaunchedEffect
        if (!tab.isDirty) {
            val file = File(path)
            if (file.exists()) {
                val diskContent = runCatching { file.readText() }.getOrDefault("")
                if (diskContent.isNotEmpty() && diskContent != tab.content) {
                    tab.content = diskContent
                    editorViewInstance?.setFile(file, diskContent)
                    syncActiveEditorRegistry(tab.path, tab.title, diskContent, false)
                }
            }
        }
    }

    fun saveActiveFile() {
        val path = activeTabPath ?: return
        val tab = openTabs.firstOrNull { it.path == path } ?: return
        val currentContent = editorViewInstance?.getText() ?: tab.content
        try {
            File(path).writeText(currentContent)
            tab.content = currentContent
            tab.isDirty = false
            syncActiveEditorRegistry(tab.path, tab.title, currentContent, false)
            IdeDaemonLogBuffer.append("INFO", "Saved file ${tab.path}")
            Toast.makeText(context, "Saved: ${tab.title}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            IdeDaemonLogBuffer.append("ERROR", "Failed saving ${tab.path}: ${e.message}")
            Toast.makeText(context, "Failed saving: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun launchRunConfiguration() {
        saveActiveFile()
        val activeFile = activeTabPath?.let { File(it) }
        val projectRoot = activeFile?.parentFile ?: currentProjectDir
        val configs = RunConfigManager.loadConfigurations(projectRoot.absolutePath)
        val chosen: RunConfiguration = configs.firstOrNull { it.isDefault } ?: configs.first()
        IdeDaemonLogBuffer.append("INFO", "Launching configuration '${chosen.name}' in ${projectRoot.absolutePath}")
        activeExecutionRequest = IdeExecutionRequest(
            title = activeFile?.name ?: chosen.name,
            projectPath = projectRoot.absolutePath,
            activeFile = activeFile,
            configuration = chosen
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // --- Top Action Toolbar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111C35))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showSidebar = !showSidebar }) {
                Icon(Icons.Default.Menu, contentDescription = "Sidebar", tint = Color(0xFF38BDF8))
            }
            IconButton(onClick = { showNewProjectDialog = true }) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "New Project", tint = Color(0xFF00E5FF))
            }
            IconButton(onClick = { showGitView = !showGitView }) {
                Icon(Icons.Default.Source, contentDescription = "Git", tint = if (showGitView) Color(0xFF38BDF8) else Color.White)
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = { launchRunConfiguration() }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Run Project or File", tint = Color(0xFF34D399))
            }
            IconButton(onClick = { editorViewInstance?.undo() }) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = Color.LightGray)
            }
            IconButton(onClick = { editorViewInstance?.redo() }) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = Color.LightGray)
            }
            IconButton(onClick = { saveActiveFile() }) {
                Icon(Icons.Default.Save, contentDescription = "Save", tint = Color(0xFF38BDF8))
            }
            IconButton(onClick = { showDaemonLogsDialog = true }) {
                Icon(Icons.Default.BugReport, contentDescription = "Daemon Logs", tint = Color(0xFFFCD34D))
            }
            IconButton(onClick = { showTerminalDialog = true }) {
                Icon(Icons.Default.Terminal, contentDescription = "Terminal", tint = Color(0xFF34D399))
            }
        }

        // --- File Tabs Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F172A))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            openTabs.forEach { tab ->
                val isSelected = tab.path == activeTabPath && activeImageAsset == null
                InputChip(
                    selected = isSelected,
                    onClick = {
                        activeImageAsset = null
                        activeTabPath = tab.path
                        editorViewInstance?.setFile(File(tab.path), tab.content)
                    },
                    label = { Text(tab.title + if (tab.isDirty) " *" else "", fontSize = 12.sp, color = if (isSelected) Color(0xFF38BDF8) else Color.White) },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                openTabs = openTabs.filter { it.path != tab.path }
                                if (activeTabPath == tab.path) {
                                    activeTabPath = openTabs.firstOrNull()?.path
                                    activeTabPath?.let { p ->
                                        openTabs.firstOrNull { it.path == p }?.let { editorViewInstance?.setFile(File(it.path), it.content) }
                                    }
                                }
                            },
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
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
                            editorViewInstance = this
                            onContentChanged = { text ->
                                openTabs.firstOrNull { it.path == activeTabPath }?.let { tab ->
                                    tab.content = text
                                    tab.isDirty = true
                                    syncActiveEditorRegistry(tab.path, tab.title, text, true)
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Slide-over Project File Tree Sidebar
            if (showSidebar) {
                ProjectSidebar(
                    rootDirectory = currentProjectDir,
                    onFileClick = { openFile(it) },
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            // Slide-over Git Source Control Panel
            if (showGitView) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(280.dp)
                        .align(Alignment.CenterEnd)
                ) {
                    GitSourceControlView(projectDir = currentProjectDir)
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
