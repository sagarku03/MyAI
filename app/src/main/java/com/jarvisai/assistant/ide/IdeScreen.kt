package com.jarvisai.assistant.ide

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jarvisai.assistant.data.ide.RunConfigManager
import com.jarvisai.assistant.data.ide.RunConfiguration
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.WorkspaceSyncManager
import com.jarvisai.assistant.ui.components.LocalTerminalDialog
import com.jarvisai.assistant.ui.files.FileExplorerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _projectPathFlow = MutableStateFlow<String?>(null)
    val projectPathFlow: StateFlow<String?> = _projectPathFlow.asStateFlow()

    var currentProjectPath: String?
        get() = _projectPathFlow.value
        set(value) {
            _projectPathFlow.value = value
        }
}

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg", "ico")
private val CODING_ACCESSORY_SYMBOLS = listOf(
    "Tab", "{", "}", "(", ")", "[", "]", "=", "\"", "'", ";", ":", "<", ">", "/", "_", "$"
)

private val IdeCanvasBg = Color(0xFF0D0E13)
private val IdeTopBarBg = Color(0xFF11131A)
private val IdeCardBg = Color(0xFF171A24)
private val IdeBorder = Color(0xFF222634)
private val IdeAccent = Color(0xFF8AB4F8)
private val IdeLavender = Color(0xFFC4B5FD)
private val IdeGreen = Color(0xFF22C55E)
private val IdeTextPrimary = Color(0xFFE6EDF3)
private val IdeTextSecondary = Color(0xFF8B949E)

private data class DiffViewerRequest(
    val filePath: String,
    val rawDiff: String,
    val isStaged: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdeScreen(
    initialFile: File? = null,
    isActive: Boolean = true,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentProjectDir by remember {
        mutableStateOf(
            ActiveCodeEditorRegistry.currentProjectPath
                ?.let { File(it) }
                ?.takeIf { it.exists() && it.isDirectory }
                ?: runCatching { LocalEnvironmentManager.getIsolatedProjectsDir(context) }
                    .getOrElse { context.filesDir }
        )
    }

    var openTabs by remember { mutableStateOf<List<OpenTab>>(emptyList()) }
    var activeTabPath by remember { mutableStateOf<String?>(null) }
    var activeImageAsset by remember { mutableStateOf<File?>(null) }
    var activeDiffRequest by remember { mutableStateOf<DiffViewerRequest?>(null) }

    var showSidebar by remember { mutableStateOf(false) }
    var showProjectPicker by remember { mutableStateOf(false) }
    var showTerminalDialog by remember { mutableStateOf(false) }
    var terminalTargetDir by remember { mutableStateOf<String?>(null) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showDaemonLogsDialog by remember { mutableStateOf(false) }
    var showAndroidDevDialog by remember { mutableStateOf(false) }
    var activeExecutionRequest by remember { mutableStateOf<IdeExecutionRequest?>(null) }

    var showRunMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var wordWrapEnabled by remember { mutableStateOf(false) }
    var fontSizeSp by remember { mutableFloatStateOf(13.5f) }
    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }

    var editorViewInstance by remember { mutableStateOf<CodeEditorView?>(null) }

    fun syncActiveEditorRegistry(path: String?, title: String?, content: String, dirty: Boolean) {
        ActiveCodeEditorRegistry.activeFilePath = path
        ActiveCodeEditorRegistry.activeFileTitle = title
        ActiveCodeEditorRegistry.activeContent = content
        ActiveCodeEditorRegistry.isDirty = dirty
    }

    fun flushActiveTabBuffer() {
        val path = activeTabPath ?: return
        val liveText = editorViewInstance?.getText() ?: return
        val idx = openTabs.indexOfFirst { it.path == path }
        if (idx >= 0) {
            val tab = openTabs[idx]
            if (tab.content != liveText) {
                tab.content = liveText
                if (!tab.isDirty) {
                    val updatedList = openTabs.toMutableList()
                    updatedList[idx] = tab.copy(content = liveText, isDirty = true)
                    openTabs = updatedList
                }
                syncActiveEditorRegistry(tab.path, tab.title, liveText, true)
            }
        }
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
                flushActiveTabBuffer()
                activeDiffRequest = null
                activeImageAsset = file
                showSidebar = false
                return
            }
            if (isBinaryFile(file)) {
                Toast.makeText(context, "Binary file (${file.name}) cannot be edited as plain text", Toast.LENGTH_SHORT).show()
                return
            }

            flushActiveTabBuffer()
            activeDiffRequest = null
            activeImageAsset = null
            val existing = openTabs.firstOrNull { it.path == file.absolutePath }
            if (existing != null) {
                activeTabPath = file.absolutePath
                editorViewInstance?.setFile(file, existing.content, isAlreadyDirty = existing.isDirty)
                syncActiveEditorRegistry(existing.path, existing.title, existing.content, existing.isDirty)
            } else {
                val content = runCatching { file.readText() }.getOrDefault("")
                val tab = OpenTab(file.absolutePath, file.name, content)
                openTabs = openTabs + tab
                activeTabPath = file.absolutePath
                editorViewInstance?.setFile(file, content, isAlreadyDirty = false)
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
            existing.content = text
            if (!existing.isDirty) {
                val updatedList = openTabs.toMutableList()
                updatedList[idx] = existing.copy(content = text, isDirty = true)
                openTabs = updatedList
            }
            syncActiveEditorRegistry(existing.path, existing.title, text, true)
        }
    }

    LaunchedEffect(editorViewInstance) {
        val view = editorViewInstance ?: return@LaunchedEffect
        view.setWordWrap(wordWrapEnabled)
        view.setFontSizeSp(fontSizeSp)
        val path = activeTabPath ?: return@LaunchedEffect
        val tab = openTabs.firstOrNull { it.path == path } ?: return@LaunchedEffect
        view.setFile(File(tab.path), tab.content, isAlreadyDirty = tab.isDirty)
        syncActiveEditorRegistry(tab.path, tab.title, tab.content, tab.isDirty)
    }

    LaunchedEffect(initialFile, editorViewInstance) {
        if (initialFile != null && editorViewInstance != null) {
            openFile(initialFile)
        }
    }

    LaunchedEffect(currentProjectDir) {
        WorkspaceSyncManager.updateWorkspaceRoot(currentProjectDir.absolutePath)
        if (ActiveCodeEditorRegistry.currentProjectPath != currentProjectDir.absolutePath) {
            ActiveCodeEditorRegistry.currentProjectPath = currentProjectDir.absolutePath
        }
    }

    // Respect external project selection via ActiveCodeEditorRegistry & WorkspaceSyncManager
    val registryProjectPath by ActiveCodeEditorRegistry.projectPathFlow.collectAsState()
    val syncedWorkspacePath by WorkspaceSyncManager.currentWorkspacePath.collectAsState()
    LaunchedEffect(registryProjectPath, syncedWorkspacePath) {
        val target = registryProjectPath?.takeIf { it.isNotBlank() }
            ?: syncedWorkspacePath?.takeIf { it.isNotBlank() }
        if (!target.isNullOrBlank() && target != currentProjectDir.absolutePath) {
            val f = File(target)
            if (f.exists() && f.isDirectory) {
                currentProjectDir = f
            }
        }
    }

    val pendingOpenFile by WorkspaceSyncManager.pendingOpenFile.collectAsState()
    LaunchedEffect(pendingOpenFile, editorViewInstance) {
        val targetPath = pendingOpenFile ?: return@LaunchedEffect
        if (editorViewInstance == null) return@LaunchedEffect
        val targetFile = File(targetPath)
        if (targetFile.exists() && targetFile.isFile) {
            val rootPrefix = currentProjectDir.absolutePath.trimEnd('/') + "/"
            if (!targetFile.absolutePath.startsWith(rootPrefix)) {
                targetFile.parentFile?.takeIf { it.exists() && it.isDirectory }?.let { parent ->
                    currentProjectDir = parent
                    ActiveCodeEditorRegistry.currentProjectPath = parent.absolutePath
                }
            }
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
                    editorViewInstance?.setFile(file, diskContent, isAlreadyDirty = false)
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
            Toast.makeText(context, "Saved ${tab.title}", Toast.LENGTH_SHORT).show()
        }.onFailure { err ->
            IdeDaemonLogBuffer.append("ERROR", "Failed saving ${tab.path}: ${err.message}")
            Toast.makeText(context, "Failed saving: ${err.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun launchRunConfiguration(specificConfig: RunConfiguration? = null) {
        runCatching {
            flushActiveTabBuffer()
            if (activeTabPath != null) {
                saveActiveFile(showEmptyWarning = false)
            }
            val activeFile = activeTabPath?.let { File(it) }
            val projectRoot = currentProjectDir
            val chosen = specificConfig ?: run {
                val configs = RunConfigManager.loadConfigurations(projectRoot.absolutePath)
                configs.firstOrNull { it.isDefault }
                    ?: configs.firstOrNull()
                    ?: RunConfiguration(
                        name = "Workspace Shell",
                        type = "terminal",
                        command = "ls -la",
                        cwd = projectRoot.absolutePath,
                        isDefault = true
                    )
            }
            IdeDaemonLogBuffer.append("INFO", "Launching '${chosen.name}' in ${projectRoot.absolutePath}")
            showSidebar = false
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

    fun launchActiveFile() {
        val activeFile = activeTabPath?.let { File(it) }
        if (activeFile == null || !activeFile.exists()) {
            Toast.makeText(context, "Open a file first to run", Toast.LENGTH_SHORT).show()
            return
        }
        val ext = activeFile.extension.lowercase()
        val isHtml = ext == "html" || ext == "htm"
        val cfg = if (isHtml) {
            RunConfiguration(
                name = "Preview ${activeFile.name}",
                type = "webview",
                command = "python3 -m http.server ${com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_WEB_PREVIEW_PORT}",
                file = activeFile.name,
                cwd = activeFile.parentFile?.absolutePath ?: currentProjectDir.absolutePath
            )
        } else {
            RunConfiguration(
                name = "Run ${activeFile.name}",
                type = "terminal",
                command = RunConfigManager.inferCommandForFile(activeFile),
                file = activeFile.name,
                cwd = activeFile.parentFile?.absolutePath ?: currentProjectDir.absolutePath
            )
        }
        launchRunConfiguration(cfg)
    }

    val activeTab = openTabs.firstOrNull { it.path == activeTabPath }
    val isAnyDirty = activeTab?.isDirty == true
    val isImeVisible = WindowInsets.isImeVisible

    val hasInternalOverlay = activeExecutionRequest != null ||
        showProjectPicker ||
        showSidebar ||
        activeDiffRequest != null ||
        activeImageAsset != null ||
        showFindBar ||
        showAndroidDevDialog ||
        showDaemonLogsDialog ||
        showNewProjectDialog ||
        showTerminalDialog

    BackHandler(enabled = isActive && hasInternalOverlay) {
        when {
            showAndroidDevDialog -> showAndroidDevDialog = false
            showTerminalDialog -> showTerminalDialog = false
            showDaemonLogsDialog -> showDaemonLogsDialog = false
            showNewProjectDialog -> showNewProjectDialog = false
            showFindBar -> {
                showFindBar = false
                findQuery = ""
                editorViewInstance?.stopSearch()
            }
            activeExecutionRequest != null -> activeExecutionRequest = null
            showProjectPicker -> showProjectPicker = false
            showSidebar -> showSidebar = false
            activeDiffRequest != null -> activeDiffRequest = null
            activeImageAsset != null -> activeImageAsset = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            editorViewInstance?.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IdeCanvasBg)
            .imePadding()
            .testTag("ide_screen_root")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // =================================================================
            // 1. STREAMLINED ANTIGEM TOP BAR (52dp, minimal & high contrast)
            // =================================================================
            Surface(
                color = IdeTopBarBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showSidebar = !showSidebar },
                        modifier = Modifier.testTag("ide_toolbar_sidebar")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Toggle Project Sidebar",
                            tint = if (showSidebar) IdeAccent else IdeTextPrimary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showSidebar = true }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentProjectDir.name.ifBlank { "workspace" },
                            color = IdeTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Save icon (only prominent when a file is open)
                    if (activeTab != null) {
                        IconButton(
                            onClick = { saveActiveFile(showEmptyWarning = true) },
                            modifier = Modifier.testTag("ide_toolbar_save")
                        ) {
                            Box {
                                Icon(
                                    imageVector = Icons.Outlined.Save,
                                    contentDescription = "Save File",
                                    tint = if (isAnyDirty) IdeAccent else IdeTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                if (isAnyDirty) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .align(Alignment.TopEnd)
                                            .clip(CircleShape)
                                            .background(IdeGreen)
                                    )
                                }
                            }
                        }
                    }

                    // AntiGem Green Split Run Pill [ ▶ | ▾ ]
                    Box {
                        Surface(
                            color = Color(0xFF15291E),
                            border = BorderStroke(1.dp, IdeGreen.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { launchRunConfiguration() }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                        .testTag("ide_toolbar_run"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Run Project",
                                        tint = IdeGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Run",
                                        color = IdeGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(16.dp)
                                        .background(IdeGreen.copy(alpha = 0.25f))
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { showRunMenu = true }
                                        .padding(horizontal = 5.dp, vertical = 5.dp)
                                        .testTag("ide_toolbar_run_dropdown"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Run & Build Options",
                                        tint = IdeGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = showRunMenu,
                            onDismissRequest = { showRunMenu = false },
                            modifier = Modifier
                                .background(Color(0xFF1A1E2B))
                                .widthIn(min = 220.dp)
                        ) {
                            val configs = remember(currentProjectDir.absolutePath, showRunMenu) {
                                RunConfigManager.loadConfigurations(currentProjectDir.absolutePath)
                            }
                            configs.forEach { cfg ->
                                DropdownMenuItem(
                                    text = { Text(cfg.name, color = IdeTextPrimary, fontSize = 13.5.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = IdeGreen, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        showRunMenu = false
                                        launchRunConfiguration(cfg)
                                    }
                                )
                            }
                            if (activeTab != null) {
                                DropdownMenuItem(
                                    text = { Text("Run Current File (${activeTab.title})", color = IdeTextPrimary, fontSize = 13.5.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        showRunMenu = false
                                        launchActiveFile()
                                    }
                                )
                            }
                            HorizontalDivider(color = IdeBorder)
                            DropdownMenuItem(
                                text = { Text("Android SDK 36 & App Builder", color = IdeTextPrimary, fontSize = 13.5.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Android, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showRunMenu = false
                                    showAndroidDevDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open Terminal", color = IdeTextPrimary, fontSize = 13.5.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Terminal, contentDescription = null, tint = IdeAccent, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showRunMenu = false
                                    terminalTargetDir = currentProjectDir.absolutePath
                                    showTerminalDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Daemon & LSP Logs", color = IdeTextPrimary, fontSize = 13.5.sp) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.BugReport, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showRunMenu = false
                                    showDaemonLogsDialog = true
                                }
                            )
                            HorizontalDivider(color = IdeBorder)
                            DropdownMenuItem(
                                text = { Text("Browse Project...", color = IdeTextPrimary, fontSize = 13.5.sp) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = IdeLavender, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showRunMenu = false
                                    showProjectPicker = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("New Project...", color = IdeTextPrimary, fontSize = 13.5.sp) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, tint = IdeAccent, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showRunMenu = false
                                    showNewProjectDialog = true
                                }
                            )
                        }
                    }

                    // Overflow Menu (Undo, Redo, Find, Word Wrap, Font Size, Close IDE)
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.testTag("ide_toolbar_overflow")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Editor Menu",
                                tint = IdeTextPrimary
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            modifier = Modifier
                                .background(Color(0xFF1A1E2B))
                                .widthIn(min = 200.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Find in File", color = IdeTextPrimary, fontSize = 13.5.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = IdeTextPrimary, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showFindBar = !showFindBar
                                    if (!showFindBar) {
                                        findQuery = ""
                                        editorViewInstance?.stopSearch()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Undo", color = IdeTextPrimary, fontSize = 13.5.sp) },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = IdeTextPrimary, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    if (editorViewInstance?.undo() != true) {
                                        Toast.makeText(context, "Nothing to undo", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Redo", color = IdeTextPrimary, fontSize = 13.5.sp) },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null, tint = IdeTextPrimary, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    if (editorViewInstance?.redo() != true) {
                                        Toast.makeText(context, "Nothing to redo", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Word Wrap", color = IdeTextPrimary, fontSize = 13.5.sp) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.WrapText, contentDescription = null, tint = IdeTextPrimary, modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    Checkbox(
                                        checked = wordWrapEnabled,
                                        onCheckedChange = null,
                                        colors = CheckboxDefaults.colors(checkedColor = IdeAccent)
                                    )
                                },
                                onClick = {
                                    wordWrapEnabled = !wordWrapEnabled
                                    editorViewInstance?.setWordWrap(wordWrapEnabled)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Font Size + (${fontSizeSp.toInt()}sp)", color = IdeTextPrimary, fontSize = 13.5.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.ZoomIn, contentDescription = null, tint = IdeTextPrimary, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    fontSizeSp = (fontSizeSp + 1.5f).coerceAtMost(24f)
                                    editorViewInstance?.setFontSizeSp(fontSizeSp)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Font Size -", color = IdeTextPrimary, fontSize = 13.5.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.ZoomOut, contentDescription = null, tint = IdeTextPrimary, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    fontSizeSp = (fontSizeSp - 1.5f).coerceAtLeast(10f)
                                    editorViewInstance?.setFontSizeSp(fontSizeSp)
                                }
                            )
                            HorizontalDivider(color = IdeBorder)
                            DropdownMenuItem(
                                text = { Text("Exit Code Editor", color = Color(0xFFF87171), fontSize = 13.5.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onNavigateBack()
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = IdeBorder, thickness = 1.dp)

            // =================================================================
            // 2. CLEAN FILE TABS STRIP (Only visible when tabs are open)
            // =================================================================
            if (openTabs.isNotEmpty() && activeDiffRequest == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(IdeTopBarBg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                            .fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        openTabs.forEach { tab ->
                            val isSelected = tab.path == activeTabPath && activeImageAsset == null
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .background(if (isSelected) IdeCanvasBg else Color.Transparent)
                                    .clickable {
                                        flushActiveTabBuffer()
                                        activeImageAsset = null
                                        activeDiffRequest = null
                                        activeTabPath = tab.path
                                        editorViewInstance?.setFile(File(tab.path), tab.content, isAlreadyDirty = tab.isDirty)
                                        syncActiveEditorRegistry(tab.path, tab.title, tab.content, tab.isDirty)
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(if (isSelected) IdeAccent else Color.Transparent)
                                )
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 10.dp, end = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CodeTreeFileBadge(fileName = tab.title)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = tab.title,
                                        color = if (isSelected) IdeTextPrimary else IdeTextSecondary,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                    if (tab.isDirty) {
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(IdeAccent)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                flushActiveTabBuffer()
                                                val remaining = openTabs.filter { it.path != tab.path }
                                                openTabs = remaining
                                                if (activeTabPath == tab.path) {
                                                    val next = remaining.lastOrNull()
                                                    activeTabPath = next?.path
                                                    if (next != null) {
                                                        editorViewInstance?.setFile(File(next.path), next.content, isAlreadyDirty = next.isDirty)
                                                        syncActiveEditorRegistry(next.path, next.title, next.content, next.isDirty)
                                                    } else {
                                                        editorViewInstance?.setText("")
                                                        syncActiveEditorRegistry(null, null, "", false)
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close ${tab.title}",
                                            tint = IdeTextSecondary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Subtle inline Undo / Redo / Find micro actions on right of tab bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        IconButton(
                            onClick = { editorViewInstance?.undo() },
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("ide_toolbar_undo")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = IdeTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = { editorViewInstance?.redo() },
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("ide_toolbar_redo")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                tint = IdeTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = IdeBorder, thickness = 1.dp)
            }

            // =================================================================
            // 3. IN-FILE FIND BAR (Collapsible)
            // =================================================================
            AnimatedVisibility(visible = showFindBar && openTabs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(IdeCardBg)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = IdeTextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (findQuery.isEmpty()) {
                            Text("Find in file...", color = IdeTextSecondary, fontSize = 13.sp)
                        }
                        BasicTextField(
                            value = findQuery,
                            onValueChange = { q ->
                                findQuery = q
                                editorViewInstance?.searchQuery(q)
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = IdeTextPrimary, fontSize = 13.sp),
                            cursorBrush = SolidColor(IdeAccent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    IconButton(
                        onClick = { editorViewInstance?.gotoPreviousMatch() },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous Match", tint = IdeTextPrimary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { editorViewInstance?.gotoNextMatch() },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next Match", tint = IdeTextPrimary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            showFindBar = false
                            findQuery = ""
                            editorViewInstance?.stopSearch()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Find", tint = IdeTextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
                HorizontalDivider(color = IdeBorder, thickness = 1.dp)
            }

            // =================================================================
            // 4. WORKSPACE BODY (Sora CodeEditor / Image Viewer / Diff Viewer / Idle)
            // =================================================================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(IdeCanvasBg)
            ) {
                val diffReq = activeDiffRequest
                val imageFile = activeImageAsset

                // Keep Sora CodeEditorView always mounted so image/diff previews never destroy editor state
                AndroidView(
                    factory = { ctx ->
                        CodeEditorView(ctx).apply {
                            onContentChanged = { text -> onEditorTextUpdated(text) }
                            setWordWrap(wordWrapEnabled)
                            setFontSizeSp(fontSizeSp)
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

                // Clean AntiGem Idle Overlay when no file is open
                if (openTabs.isEmpty() && diffReq == null && imageFile == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(IdeCanvasBg)
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.widthIn(max = 320.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(IdeCardBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    tint = IdeTextSecondary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Select a file from the sidebar to start editing",
                                color = IdeTextSecondary,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.height(22.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IdeQuickPill(
                                    icon = Icons.Outlined.FolderOpen,
                                    label = "Explorer",
                                    accent = IdeAccent,
                                    onClick = { showSidebar = true }
                                )
                                IdeQuickPill(
                                    icon = Icons.Outlined.Search,
                                    label = "Browse...",
                                    accent = IdeLavender,
                                    onClick = { showProjectPicker = true }
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IdeQuickPill(
                                    icon = Icons.Outlined.CreateNewFolder,
                                    label = "New Project",
                                    accent = IdeTextPrimary,
                                    onClick = { showNewProjectDialog = true }
                                )
                                IdeQuickPill(
                                    icon = Icons.Default.Android,
                                    label = "SDK 36 Builder",
                                    accent = Color(0xFF10B981),
                                    onClick = { showAndroidDevDialog = true }
                                )
                            }
                        }
                    }
                }

                if (imageFile != null && diffReq == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(IdeCanvasBg)
                    ) {
                        ImageAssetViewer(
                            file = imageFile,
                            onClose = { activeImageAsset = null },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                if (diffReq != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(IdeCanvasBg)
                    ) {
                        UnifiedDiffViewer(
                            filePath = diffReq.filePath,
                            rawDiff = diffReq.rawDiff,
                            isStaged = diffReq.isStaged,
                            onOpenWorkingCopy = {
                                val target = File(diffReq.filePath)
                                activeDiffRequest = null
                                if (target.exists() && target.isFile) {
                                    openFile(target)
                                }
                            },
                            onClose = { activeDiffRequest = null },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Scrim for slide-over sidebar
                if (showSidebar) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.52f))
                            .clickable { showSidebar = false }
                    )
                }

                // AntiGem Slide-over Explorer + Git + Project Switcher Sidebar
                if (showSidebar) {
                    ProjectSidebar(
                        rootDirectory = currentProjectDir,
                        activeFilePath = activeTabPath,
                        onFileClick = { openFile(it) },
                        onSelectProjectRoot = { newRoot ->
                            currentProjectDir = newRoot
                        },
                        onBrowseProject = {
                            showSidebar = false
                            showProjectPicker = true
                        },
                        onNewProject = {
                            showSidebar = false
                            showNewProjectDialog = true
                        },
                        onOpenTerminalInDir = { folder ->
                            showSidebar = false
                            terminalTargetDir = folder.absolutePath
                            showTerminalDialog = true
                        },
                        onOpenDiff = { path, rawDiff, isStaged ->
                            showSidebar = false
                            activeImageAsset = null
                            activeDiffRequest = DiffViewerRequest(path, rawDiff, isStaged)
                        },
                        onFileMutated = { oldFile, newFile ->
                            flushActiveTabBuffer()
                            val oldAbs = oldFile.absolutePath
                            if (newFile == null) {
                                // Deleted file or directory
                                val remaining = openTabs.filterNot {
                                    it.path == oldAbs || it.path.startsWith("$oldAbs/")
                                }
                                openTabs = remaining
                                if (activeTabPath == oldAbs || activeTabPath?.startsWith("$oldAbs/") == true) {
                                    val next = remaining.lastOrNull()
                                    activeTabPath = next?.path
                                    if (next != null) {
                                        editorViewInstance?.setFile(File(next.path), next.content, isAlreadyDirty = next.isDirty)
                                        syncActiveEditorRegistry(next.path, next.title, next.content, next.isDirty)
                                    } else {
                                        editorViewInstance?.setText("")
                                        syncActiveEditorRegistry(null, null, "", false)
                                    }
                                }
                            } else {
                                // Renamed file
                                val updatedTabs = openTabs.map { t ->
                                    if (t.path == oldAbs) {
                                        t.copy(path = newFile.absolutePath, title = newFile.name)
                                    } else if (t.path.startsWith("$oldAbs/")) {
                                        val rel = t.path.removePrefix(oldAbs)
                                        t.copy(path = newFile.absolutePath + rel)
                                    } else {
                                        t
                                    }
                                }
                                openTabs = updatedTabs
                                if (activeTabPath == oldAbs) {
                                    activeTabPath = newFile.absolutePath
                                    syncActiveEditorRegistry(newFile.absolutePath, newFile.name, activeTab?.content ?: "", activeTab?.isDirty ?: false)
                                }
                            }
                        },
                        onClose = { showSidebar = false },
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }
            }

            // =================================================================
            // 5. CODING QUICK-SYMBOL ACCESSORY BAR (Shown when keyboard is up)
            // =================================================================
            if (isImeVisible && openTabs.isNotEmpty() && activeImageAsset == null && activeDiffRequest == null) {
                HorizontalDivider(color = IdeBorder, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(IdeTopBarBg)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CODING_ACCESSORY_SYMBOLS.forEach { sym ->
                        Surface(
                            onClick = { editorViewInstance?.insertSymbol(sym) },
                            color = IdeCardBg,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, IdeBorder)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = sym,
                                    color = IdeTextPrimary,
                                    fontSize = 12.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 6. FULL-SCREEN "OPEN A PROJECT OR FILE" PICKER (AntiGem 00:49)
        // =====================================================================
        if (showProjectPicker) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(IdeCanvasBg)
            ) {
                FileExplorerScreen(
                    initialDir = currentProjectDir,
                    isActive = true,
                    isProjectPickerMode = true,
                    onSelectAsProject = { chosenDir ->
                        currentProjectDir = chosenDir
                        showProjectPicker = false
                        showSidebar = true
                    },
                    onBack = { showProjectPicker = false },
                    onOpenFileInIde = { chosenFile ->
                        chosenFile.parentFile?.let { currentProjectDir = it }
                        showProjectPicker = false
                        openFile(chosenFile)
                    },
                    onOpenFolderInIde = { chosenDir ->
                        currentProjectDir = chosenDir
                        showProjectPicker = false
                        showSidebar = true
                    }
                )
            }
        }

        // =====================================================================
        // 7. EXECUTION RUNNER / LIVE WEB PREVIEW SHEET
        // =====================================================================
        val execReq = activeExecutionRequest
        if (execReq != null) {
            IdeExecutionRunnerSheet(
                request = execReq,
                onClose = { activeExecutionRequest = null },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
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
        LocalTerminalDialog(
            initialWorkingDir = terminalTargetDir ?: currentProjectDir.absolutePath,
            onDismiss = {
                showTerminalDialog = false
                terminalTargetDir = null
            }
        )
    }

    if (showAndroidDevDialog) {
        AndroidSdkBuilderDialog(
            currentProjectDir = currentProjectDir,
            onProjectCreated = { newDir ->
                currentProjectDir = newDir
                showAndroidDevDialog = false
                showSidebar = true
            },
            onDismiss = { showAndroidDevDialog = false }
        )
    }
}

@Composable
private fun IdeQuickPill(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = IdeCardBg,
        border = BorderStroke(1.dp, IdeBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                color = IdeTextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AndroidSdkBuilderDialog(
    currentProjectDir: File,
    onProjectCreated: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by com.jarvisai.assistant.data.androiddev.AndroidDevManager.status.collectAsState()
    var isWorking by remember { mutableStateOf(false) }
    var buildLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var appNameInput by remember { mutableStateOf("MyAgentraApp") }
    var pkgNameInput by remember { mutableStateOf("com.agentra.mobileapp") }
    var selectedFramework by remember {
        mutableStateOf(com.jarvisai.assistant.data.androiddev.AndroidDevManager.Framework.KOTLIN)
    }

    LaunchedEffect(Unit) {
        com.jarvisai.assistant.data.androiddev.AndroidDevManager.refreshStatus(context)
    }

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        containerColor = IdeCardBg,
        titleContentColor = IdeTextPrimary,
        textContentColor = IdeTextPrimary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Android, contentDescription = null, tint = Color(0xFF10B981))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("On-Device Android SDK 36 & App Builder", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "Isolated SDK: ~/jarvis-android-sdk",
                        color = IdeAccent,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = IdeTopBarBg,
                    border = BorderStroke(1.dp, IdeBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Java 21 [${if (status.isJava21Installed) "✓" else "✗"}] • SDK 36 [${if (status.isAndroidSdkInstalled) "✓" else "✗"}] • Gradle [${if (status.isGradleInstalled) "✓" else "✗"}]",
                            color = IdeTextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Flutter [${if (status.isFlutterInstalled) "✓" else "✗"}] • Node/RN [${if (status.isNodeInstalled) "✓" else "✗"}] • adev CLI [${if (status.isAdevScriptsInstalled) "✓" else "✗"}]",
                            color = IdeTextSecondary,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    com.jarvisai.assistant.data.androiddev.AndroidDevManager.Framework.entries.forEach { fw ->
                        val selected = selectedFramework == fw
                        FilterChip(
                            selected = selected,
                            onClick = { selectedFramework = fw },
                            label = {
                                Text(
                                    text = when (fw) {
                                        com.jarvisai.assistant.data.androiddev.AndroidDevManager.Framework.KOTLIN -> "Kotlin SDK 36"
                                        com.jarvisai.assistant.data.androiddev.AndroidDevManager.Framework.FLUTTER -> "Flutter"
                                        com.jarvisai.assistant.data.androiddev.AndroidDevManager.Framework.REACT_NATIVE -> "React Native"
                                    },
                                    fontSize = 10.5.sp
                                )
                            }
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isWorking = true
                            buildLogs = listOf("Installing ${selectedFramework.displayName}...")
                            com.jarvisai.assistant.data.androiddev.AndroidDevManager.installFramework(
                                context = context,
                                framework = selectedFramework
                            ) { line ->
                                buildLogs = (buildLogs + line).takeLast(12)
                            }
                            isWorking = false
                        }
                    },
                    enabled = !isWorking,
                    border = BorderStroke(1.dp, IdeAccent.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚡ Install / Verify ${selectedFramework.id.uppercase()} Toolchain",
                        color = IdeAccent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = appNameInput,
                    onValueChange = { appNameInput = it },
                    label = { Text("New Mobile App Name", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pkgNameInput,
                    onValueChange = { pkgNameInput = it },
                    label = { Text("Package Name", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                isWorking = true
                                val projectsRoot = LocalEnvironmentManager.getIsolatedProjectsDir(context)
                                buildLogs = listOf("Scaffolding $appNameInput in ${projectsRoot.absolutePath}...")
                                val res = com.jarvisai.assistant.data.androiddev.AndroidDevManager.createProject(
                                    context = context,
                                    framework = selectedFramework,
                                    projectName = appNameInput,
                                    packageName = pkgNameInput,
                                    workingDir = projectsRoot.absolutePath
                                ) { line ->
                                    buildLogs = (buildLogs + line).takeLast(12)
                                }
                                isWorking = false
                                if (res.isSuccess) {
                                    val newDir = File(projectsRoot, appNameInput.trim())
                                    if (newDir.exists()) onProjectCreated(newDir)
                                }
                            }
                        },
                        enabled = !isWorking && appNameInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Scaffold App", color = Color(0xFF060A12), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isWorking = true
                                buildLogs = listOf("Running `adev build` in ${currentProjectDir.name}...")
                                com.jarvisai.assistant.data.androiddev.AndroidDevManager.runAdevCommand(
                                    context = context,
                                    subcommand = "build",
                                    projectDir = currentProjectDir.absolutePath
                                ) { line ->
                                    buildLogs = (buildLogs + line).takeLast(12)
                                }
                                isWorking = false
                            }
                        },
                        enabled = !isWorking,
                        colors = ButtonDefaults.buttonColors(containerColor = IdeAccent),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Build APK", color = Color(0xFF060A12), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (buildLogs.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = IdeCanvasBg,
                        border = BorderStroke(1.dp, IdeBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            buildLogs.forEach { logLine ->
                                Text(
                                    text = logLine,
                                    color = Color(0xFF6EE7B7),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isWorking) {
                Text("Close", color = IdeTextSecondary)
            }
        }
    )
}
