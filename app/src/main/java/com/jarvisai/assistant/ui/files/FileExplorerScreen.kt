package com.jarvisai.assistant.ui.files

import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.ide.ActiveCodeEditorRegistry
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.WorkspaceSyncManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileExplorerScreen(
    initialDir: File? = null,
    isActive: Boolean = true,
    onBack: () -> Unit,
    onOpenFileInIde: (File) -> Unit,
    onOpenFolderInIde: ((File) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val homeDir = remember {
        LocalEnvironmentManager.getHomeDir(context).also {
            FileManagerOperations.ensureHomeStorageShortcuts(context)
        }
    }

    val navStack = remember {
        val startDir = when {
            initialDir != null && initialDir.exists() -> initialDir
            homeDir.exists() -> homeDir
            else -> Environment.getExternalStorageDirectory() ?: context.filesDir
        }
        val rootTitle = if (startDir.absolutePath == homeDir.absolutePath) "Home" else startDir.name.ifBlank { "Storage" }
        mutableStateListOf(NavSegment(displayName = rootTitle, directory = startDir))
    }

    val currentSegment = navStack.last()
    val currentDirectory = currentSegment.directory

    var refreshTrigger by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    var showHiddenFiles by remember { mutableStateOf(FileManagerOperations.getShowHiddenPreference(context)) }
    var isGridView by remember { mutableStateOf(FileManagerOperations.getGridModePreference(context)) }
    var sortField by remember { mutableStateOf(FileSortField.NAME) }
    var sortOrder by remember { mutableStateOf(FileSortOrder.ASCENDING) }

    // Search State
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchAllSubfolders by remember { mutableStateOf(false) }
    var subfolderSearchResults by remember { mutableStateOf<List<File>>(emptyList()) }
    var isSearchingSubfolders by remember { mutableStateOf(false) }

    // Multi-Select & Clipboard State
    val selectedPaths = remember { mutableStateListOf<String>() }
    val isMultiSelectMode = selectedPaths.isNotEmpty()
    var clipboardState by remember { mutableStateOf<FileClipboardState?>(null) }

    // Sheets & Dialogs State
    var showPlacesSheet by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showSortAndViewSheet by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var createDialogIsFolder by remember { mutableStateOf<Boolean?>(null) }
    var actionSheetFile by remember { mutableStateOf<File?>(null) }
    var detailsTargetFile by remember { mutableStateOf<File?>(null) }
    var renameTargetFile by remember { mutableStateOf<File?>(null) }
    var deleteConfirmFiles by remember { mutableStateOf<List<File>?>(null) }
    var openWithTargetFile by remember { mutableStateOf<File?>(null) }
    var builtInImageViewerFile by remember { mutableStateOf<File?>(null) }

    val syncTimestamp by WorkspaceSyncManager.syncTimestamp.collectAsState()
    LaunchedEffect(syncTimestamp) {
        refreshTrigger++
    }

    // Subfolder recursive search
    LaunchedEffect(isSearchActive, searchQuery, searchAllSubfolders, currentDirectory, showHiddenFiles, refreshTrigger) {
        if (isSearchActive && searchAllSubfolders && searchQuery.isNotBlank()) {
            isSearchingSubfolders = true
            subfolderSearchResults = FileManagerOperations.searchInSubfolders(
                rootDir = currentDirectory,
                query = searchQuery,
                showHidden = showHiddenFiles
            )
            isSearchingSubfolders = false
        } else {
            subfolderSearchResults = emptyList()
            isSearchingSubfolders = false
        }
    }

    val displayedFiles = remember(
        currentDirectory,
        showHiddenFiles,
        sortField,
        sortOrder,
        isSearchActive,
        searchQuery,
        searchAllSubfolders,
        subfolderSearchResults,
        refreshTrigger
    ) {
        val baseList = if (isSearchActive && searchAllSubfolders && searchQuery.isNotBlank()) {
            subfolderSearchResults
        } else {
            val children = FileManagerOperations.listDirectoryChildren(currentDirectory, showHiddenFiles)
            if (isSearchActive && searchQuery.isNotBlank()) {
                children.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
            } else {
                children
            }
        }

        val comparator = Comparator<File> { a, b ->
            val aDir = FileManagerOperations.isEffectiveDirectory(a)
            val bDir = FileManagerOperations.isEffectiveDirectory(b)
            if (aDir != bDir) {
                if (aDir) -1 else 1
            } else {
                val rawCmp = when (sortField) {
                    FileSortField.NAME -> a.name.lowercase(Locale.ROOT).compareTo(b.name.lowercase(Locale.ROOT))
                    FileSortField.DATE -> a.lastModified().compareTo(b.lastModified())
                    FileSortField.SIZE -> a.length().compareTo(b.length())
                    FileSortField.TYPE -> a.extension.lowercase(Locale.ROOT).compareTo(b.extension.lowercase(Locale.ROOT))
                        .takeIf { it != 0 } ?: a.name.lowercase(Locale.ROOT).compareTo(b.name.lowercase(Locale.ROOT))
                }
                if (sortOrder == FileSortOrder.ASCENDING) rawCmp else -rawCmp
            }
        }
        baseList.sortedWith(comparator)
    }

    val footerSummary = remember(displayedFiles) {
        var folderCount = 0
        var fileCount = 0
        var totalFileBytes = 0L
        for (f in displayedFiles) {
            if (FileManagerOperations.isEffectiveDirectory(f)) {
                folderCount++
            } else {
                fileCount++
                totalFileBytes += f.length()
            }
        }
        val fLabel = if (folderCount == 1) "1 folder" else "$folderCount folders"
        val fileLabel = if (fileCount == 1) "1 file" else "$fileCount files"
        if (totalFileBytes > 0L) {
            "$fLabel • $fileLabel • ${FileManagerOperations.formatBytes(totalFileBytes)}"
        } else {
            "$fLabel • $fileLabel"
        }
    }

    fun navigateIntoDirectory(file: File, customLabel: String? = null) {
        selectedPaths.clear()
        val label = customLabel ?: file.name.ifBlank { file.absolutePath }
        navStack.add(NavSegment(displayName = label, directory = file))
        FileManagerOperations.recordRecentDir(context, file)
    }

    fun switchRootPlace(place: PlaceEntry) {
        selectedPaths.clear()
        isSearchActive = false
        searchQuery = ""
        val extRoot = Environment.getExternalStorageDirectory()
        navStack.clear()
        when (place.kind) {
            PlaceKind.HOME -> {
                navStack.add(NavSegment("Home", homeDir))
            }
            PlaceKind.INTERNAL_STORAGE -> {
                navStack.add(NavSegment("Internal storage", place.directory))
            }
            PlaceKind.DOWNLOADS, PlaceKind.DOCUMENTS, PlaceKind.CAMERA,
            PlaceKind.PICTURES, PlaceKind.MOVIES, PlaceKind.MUSIC -> {
                if (extRoot != null && place.directory.absolutePath.startsWith(extRoot.absolutePath)) {
                    navStack.add(NavSegment("Internal storage", extRoot))
                    navStack.add(NavSegment(place.directory.name, place.directory))
                } else {
                    navStack.add(NavSegment(place.title, place.directory))
                }
                FileManagerOperations.recordRecentDir(context, place.directory)
            }
            else -> {
                if (place.directory.absolutePath.startsWith(homeDir.absolutePath) &&
                    place.directory.absolutePath != homeDir.absolutePath
                ) {
                    navStack.add(NavSegment("Home", homeDir))
                    val relParts = place.directory.absolutePath
                        .removePrefix(homeDir.absolutePath)
                        .trimStart('/')
                        .split('/')
                        .filter { it.isNotBlank() }
                    var cursor = homeDir
                    for (part in relParts) {
                        cursor = File(cursor, part)
                        navStack.add(NavSegment(part, cursor))
                    }
                } else {
                    navStack.add(NavSegment(place.title, place.directory))
                }
                FileManagerOperations.recordRecentDir(context, place.directory)
            }
        }
    }

    fun handleBackNavigation() {
        when {
            isMultiSelectMode -> selectedPaths.clear()
            isSearchActive -> {
                isSearchActive = false
                searchQuery = ""
            }
            navStack.size > 1 -> {
                navStack.removeAt(navStack.lastIndex)
            }
            currentDirectory.absolutePath != homeDir.absolutePath && homeDir.exists() -> {
                navStack.clear()
                navStack.add(NavSegment("Home", homeDir))
            }
            else -> onBack()
        }
    }

    BackHandler(enabled = isActive) {
        handleBackNavigation()
    }

    fun triggerManualRefresh() {
        scope.launch {
            isRefreshing = true
            refreshTrigger++
            delay(280)
            isRefreshing = false
        }
    }

    fun handleFileClick(file: File) {
        if (isMultiSelectMode) {
            val path = file.absolutePath
            if (path in selectedPaths) selectedPaths.remove(path) else selectedPaths.add(path)
            return
        }
        if (FileManagerOperations.isEffectiveDirectory(file)) {
            navigateIntoDirectory(file)
            return
        }
        val ext = file.extension.lowercase(Locale.ROOT)
        val savedHandler = FileManagerOperations.getAlwaysOpenHandler(context, ext)
        if (ext in FileManagerOperations.IMAGE_EXTENSIONS) {
            when (savedHandler) {
                "builtin" -> builtInImageViewerFile = file
                "external" -> FileManagerOperations.openFileExternal(context, file)
                else -> openWithTargetFile = file
            }
        } else if (ext in FileManagerOperations.VIDEO_EXTENSIONS ||
            ext in FileManagerOperations.AUDIO_EXTENSIONS ||
            ext == "apk" || ext == "pdf"
        ) {
            openWithTargetFile = file
        } else if (ext in FileManagerOperations.ARCHIVE_EXTENSIONS) {
            actionSheetFile = file
        } else {
            onOpenFileInIde(file)
        }
    }

    fun openDirectoryInIde(dir: File) {
        val resolved = FileManagerOperations.resolveDirectoryTarget(dir)
        ActiveCodeEditorRegistry.currentProjectPath = resolved.absolutePath
        if (onOpenFolderInIde != null) {
            onOpenFolderInIde(resolved)
        } else {
            val firstFile = resolved.listFiles()?.firstOrNull { it.isFile }
                ?: File(resolved, "README.md").apply { if (!exists()) runCatching { writeText("# ${resolved.name}\n") } }
            onOpenFileInIde(firstFile)
        }
    }

    val breadcrumbListState = rememberLazyListState()
    LaunchedEffect(navStack.size) {
        if (navStack.isNotEmpty()) {
            runCatching { breadcrumbListState.animateScrollToItem(navStack.lastIndex) }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("file_explorer_screen"),
        containerColor = AntiGemBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateSheet = true },
                containerColor = AntiGemAccent,
                contentColor = Color(0xFF0B0C10),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .size(56.dp)
                    .testTag("file_explorer_fab_create")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create file, folder, or project",
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(AntiGemBg)
        ) {
            // =================================================================
            // 1. TOP BAR (Normal / Search / Multi-Select)
            // =================================================================
            if (isMultiSelectMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedPaths.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection", tint = AntiGemTextPrimary)
                        }
                        Text(
                            text = "${selectedPaths.size} selected",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AntiGemTextPrimary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            selectedPaths.clear()
                            selectedPaths.addAll(displayedFiles.map { it.absolutePath })
                        }) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = "Select all", tint = AntiGemTextPrimary)
                        }
                        IconButton(onClick = {
                            val files = displayedFiles.filter { it.absolutePath in selectedPaths }
                            clipboardState = FileClipboardState(files, FileClipboardMode.COPY)
                            selectedPaths.clear()
                            Toast.makeText(context, "${files.size} item(s) copied", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy selected", tint = AntiGemTextPrimary)
                        }
                        IconButton(onClick = {
                            val files = displayedFiles.filter { it.absolutePath in selectedPaths }
                            clipboardState = FileClipboardState(files, FileClipboardMode.CUT)
                            selectedPaths.clear()
                            Toast.makeText(context, "${files.size} item(s) cut", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Outlined.ContentCut, contentDescription = "Cut selected", tint = AntiGemTextPrimary)
                        }
                        IconButton(onClick = {
                            val files = displayedFiles.filter { it.absolutePath in selectedPaths }
                            val zipName = if (files.size == 1) "${files.first().name}.zip" else "archive_${System.currentTimeMillis() / 1000}.zip"
                            val outZip = File(FileManagerOperations.resolveDirectoryTarget(currentDirectory), zipName)
                            selectedPaths.clear()
                            scope.launch {
                                isRefreshing = true
                                FileManagerOperations.compressToZip(files, outZip).onSuccess {
                                    Toast.makeText(context, "Created ${it.name}", Toast.LENGTH_SHORT).show()
                                    refreshTrigger++
                                }.onFailure {
                                    Toast.makeText(context, "ZIP failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                                isRefreshing = false
                            }
                        }) {
                            Icon(Icons.Outlined.Archive, contentDescription = "Compress selected", tint = AntiGemTextPrimary)
                        }
                        IconButton(onClick = {
                            val files = displayedFiles.filter { it.absolutePath in selectedPaths }
                            deleteConfirmFiles = files
                        }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete selected", tint = AntiGemDanger)
                        }
                    }
                }
            } else if (isSearchActive) {
                // Inline Search Bar + Scope Chips (01:15 in AntiGem video)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color(0xFF181B26),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    isSearchActive = false
                                    searchQuery = ""
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Exit search",
                                    tint = AntiGemTextPrimary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search in ${currentSegment.displayName}",
                                        fontSize = 15.sp,
                                        color = AntiGemTextSecondary
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = AntiGemTextPrimary, fontSize = 15.sp),
                                    cursorBrush = SolidColor(AntiGemAccent),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear query",
                                        tint = AntiGemTextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchScopeChip(
                            label = "This folder",
                            selected = !searchAllSubfolders,
                            onClick = { searchAllSubfolders = false }
                        )
                        SearchScopeChip(
                            label = "All subfolders",
                            selected = searchAllSubfolders,
                            leadingSearchIcon = true,
                            onClick = { searchAllSubfolders = true }
                        )
                    }
                }
            } else {
                // Standard AntiGem Top Bar (00:30 in video)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = { handleBackNavigation() },
                            modifier = Modifier.testTag("file_explorer_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = AntiGemTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = currentSegment.displayName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = AntiGemTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { isSearchActive = true },
                            modifier = Modifier.testTag("file_explorer_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search files",
                                tint = AntiGemTextPrimary
                            )
                        }
                        IconButton(
                            onClick = { createDialogIsFolder = true },
                            modifier = Modifier.testTag("file_explorer_new_folder_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CreateNewFolder,
                                contentDescription = "New folder",
                                tint = AntiGemTextPrimary
                            )
                        }
                        IconButton(
                            onClick = {
                                isGridView = !isGridView
                                FileManagerOperations.setGridModePreference(context, isGridView)
                            },
                            modifier = Modifier.testTag("file_explorer_grid_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                                contentDescription = "Toggle grid or list view",
                                tint = AntiGemTextPrimary
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.testTag("file_explorer_overflow_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = AntiGemTextPrimary
                                )
                            }

                            // Top-right 3-dot menu matching 01:27 in AntiGem video
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                                modifier = Modifier
                                    .background(Color(0xFF191C26))
                                    .width(236.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("New folder", color = AntiGemTextPrimary, fontSize = 14.5.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, tint = AntiGemTextPrimary)
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        createDialogIsFolder = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("New file", color = AntiGemTextPrimary, fontSize = 14.5.sp) },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, contentDescription = null, tint = AntiGemTextPrimary)
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        createDialogIsFolder = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Select all", color = AntiGemTextPrimary, fontSize = 14.5.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.CheckBox, contentDescription = null, tint = AntiGemTextPrimary)
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        selectedPaths.clear()
                                        selectedPaths.addAll(displayedFiles.map { it.absolutePath })
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Show hidden files", color = AntiGemTextPrimary, fontSize = 14.5.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Visibility, contentDescription = null, tint = AntiGemTextPrimary)
                                    },
                                    trailingIcon = {
                                        Checkbox(
                                            checked = showHiddenFiles,
                                            onCheckedChange = null,
                                            colors = CheckboxDefaults.colors(checkedColor = AntiGemAccent)
                                        )
                                    },
                                    onClick = {
                                        showHiddenFiles = !showHiddenFiles
                                        FileManagerOperations.setShowHiddenPreference(context, showHiddenFiles)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort & view...", color = AntiGemTextPrimary, fontSize = 14.5.sp) },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, tint = AntiGemTextPrimary)
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showSortAndViewSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Refresh", color = AntiGemTextPrimary, fontSize = 14.5.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = AntiGemTextPrimary)
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        triggerManualRefresh()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Open this folder in IDE", color = AntiGemTextPrimary, fontSize = 14.5.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Code, contentDescription = null, tint = AntiGemTextPrimary)
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        openDirectoryInIde(currentDirectory)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy folder path", color = AntiGemTextPrimary, fontSize = 14.5.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = AntiGemTextPrimary)
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        FileManagerOperations.copyPathToClipboard(context, currentDirectory.absolutePath)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Folder details", color = AntiGemTextPrimary, fontSize = 14.5.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Info, contentDescription = null, tint = AntiGemTextPrimary)
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        detailsTargetFile = currentDirectory
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // =================================================================
            // 2. PLACES BUTTON & BREADCRUMB BAR (00:30, 00:54 in AntiGem video)
            // =================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF171922),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showPlacesSheet = true }
                        .testTag("file_explorer_places_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Dns,
                            contentDescription = "Open Places",
                            tint = AntiGemAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF171922),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    LazyRow(
                        state = breadcrumbListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(navStack) { index, segment ->
                            val isLast = index == navStack.lastIndex
                            val isFirst = index == 0

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (isLast) Color(0xFF25293A) else Color.Transparent,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(9.dp))
                                        .clickable {
                                            if (!isLast) {
                                                while (navStack.size > index + 1) {
                                                    navStack.removeAt(navStack.lastIndex)
                                                }
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isFirst) {
                                            Icon(
                                                imageVector = if (segment.displayName == "Home") Icons.Outlined.Home else Icons.Outlined.Smartphone,
                                                contentDescription = null,
                                                tint = if (isLast) AntiGemTextPrimary else AntiGemTextSecondary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = segment.displayName,
                                            fontSize = 13.sp,
                                            fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (isLast) AntiGemTextPrimary else AntiGemTextSecondary,
                                            maxLines = 1
                                        )
                                    }
                                }

                                if (!isLast) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = Color(0xFF565D70),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // =================================================================
            // 3. CLIPBOARD / COPY-TO / MOVE-TO BANNER (when active)
            // =================================================================
            AnimatedVisibility(visible = clipboardState != null) {
                clipboardState?.let { clip ->
                    val actionVerb = when (clip.mode) {
                        FileClipboardMode.COPY -> "Paste here"
                        FileClipboardMode.CUT -> "Move here"
                        FileClipboardMode.COPY_TO -> "Copy here"
                        FileClipboardMode.MOVE_TO -> "Move here"
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E2538),
                        border = BorderStroke(1.dp, AntiGemAccent.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentPaste,
                                    contentDescription = null,
                                    tint = AntiGemAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "${clip.files.size} item(s): ${clip.files.first().name}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AntiGemTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { clipboardState = null }) {
                                    Text("Cancel", color = AntiGemTextSecondary, fontSize = 13.sp)
                                }
                                Button(
                                    onClick = {
                                        val isMove = clip.mode == FileClipboardMode.CUT || clip.mode == FileClipboardMode.MOVE_TO
                                        scope.launch {
                                            isRefreshing = true
                                            FileManagerOperations.copyOrMoveFiles(
                                                sources = clip.files,
                                                destinationDir = currentDirectory,
                                                isCut = isMove
                                            ).onSuccess { count ->
                                                Toast.makeText(
                                                    context,
                                                    if (isMove) "Moved $count item(s)" else "Copied $count item(s)",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                clipboardState = null
                                                refreshTrigger++
                                            }.onFailure {
                                                Toast.makeText(context, "Operation failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                            isRefreshing = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AntiGemAccent),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(actionVerb, color = Color(0xFF0B0C10), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // =================================================================
            // 4. MAIN FILE LIST / GRID WITH PULL-TO-REFRESH
            // =================================================================
            PullToRefreshBox(
                isRefreshing = isRefreshing || isSearchingSubfolders,
                onRefresh = { triggerManualRefresh() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (displayedFiles.isEmpty() && !isSearchingSubfolders) {
                    // Empty folder view matching 01:55 in AntiGem video
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(AntiGemIconBoxBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = AntiGemAccent,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isSearchActive && searchQuery.isNotBlank()) "No matching files" else "This folder is empty",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = AntiGemTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isSearchActive && searchQuery.isNotBlank()) {
                                "Try another search term or switch to All subfolders."
                            } else {
                                "Tap + to create a file or folder."
                            },
                            fontSize = 13.5.sp,
                            color = AntiGemTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        if (isGridView) {
                            val rows = displayedFiles.chunked(3)
                            items(rows, key = { row -> row.joinToString("|") { it.absolutePath } }) { rowFiles ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    for (i in 0 until 3) {
                                        val file = rowFiles.getOrNull(i)
                                        if (file != null) {
                                            AntiGemFileGridCard(
                                                file = file,
                                                showHidden = showHiddenFiles,
                                                isSelected = file.absolutePath in selectedPaths,
                                                modifier = Modifier.weight(1f),
                                                onClick = { handleFileClick(file) },
                                                onLongClick = {
                                                    val path = file.absolutePath
                                                    if (path in selectedPaths) selectedPaths.remove(path)
                                                    else selectedPaths.add(path)
                                                }
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        } else {
                            items(displayedFiles, key = { it.absolutePath }) { file ->
                                AntiGemFileListRow(
                                    file = file,
                                    showHidden = showHiddenFiles,
                                    isSelected = file.absolutePath in selectedPaths,
                                    onClick = { handleFileClick(file) },
                                    onLongClick = {
                                        val path = file.absolutePath
                                        if (path in selectedPaths) selectedPaths.remove(path)
                                        else selectedPaths.add(path)
                                    },
                                    onMoreClick = { actionSheetFile = file }
                                )
                            }
                        }

                        // Footer Summary matching 00:35, 01:11, 01:38 in AntiGem video
                        item(key = "footer_summary") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = footerSummary,
                                    fontSize = 12.sp,
                                    color = Color(0xFF687082)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // 5. BOTTOM SHEETS & DIALOGS
    // =========================================================================
    if (showPlacesSheet) {
        val recentDirs = remember(refreshTrigger, navStack.size) {
            FileManagerOperations.getRecentDirs(context)
        }
        val places = remember(recentDirs, refreshTrigger) {
            FileManagerOperations.buildPlacesList(context, recentDirs)
        }
        PlacesBottomSheet(
            places = places,
            currentDirectory = currentDirectory,
            onSelectPlace = { place ->
                showPlacesSheet = false
                switchRootPlace(place)
            },
            onDismiss = { showPlacesSheet = false }
        )
    }

    if (showCreateSheet) {
        CreateItemBottomSheet(
            onCreateFolder = { createDialogIsFolder = true },
            onCreateFile = { createDialogIsFolder = false },
            onCreateProjectFromTemplate = { showTemplateDialog = true },
            onDismiss = { showCreateSheet = false }
        )
    }

    if (showTemplateDialog) {
        ProjectFromTemplateDialog(
            onConfirmCreate = { template, folderName, openIde ->
                showTemplateDialog = false
                scope.launch {
                    FileManagerOperations.createProjectFromTemplate(currentDirectory, folderName, template)
                        .onSuccess { createdDir ->
                            Toast.makeText(context, "Created ${createdDir.name}", Toast.LENGTH_SHORT).show()
                            refreshTrigger++
                            if (openIde) {
                                openDirectoryInIde(createdDir)
                            } else {
                                navigateIntoDirectory(createdDir)
                            }
                        }
                        .onFailure {
                            Toast.makeText(context, "Failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                }
            },
            onDismiss = { showTemplateDialog = false }
        )
    }

    createDialogIsFolder?.let { isFolder ->
        var inputName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { createDialogIsFolder = null },
            containerColor = AntiGemSheetBg,
            title = {
                Text(
                    text = if (isFolder) "New folder" else "New file",
                    color = AntiGemTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    placeholder = {
                        Text(
                            text = if (isFolder) "Folder name" else "e.g. main.py, index.js, notes.md",
                            color = AntiGemTextSecondary
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AntiGemAccent,
                        unfocusedBorderColor = Color(0xFF2D3348),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = inputName.trim()
                        if (trimmed.isNotEmpty()) {
                            val targetParent = FileManagerOperations.resolveDirectoryTarget(currentDirectory)
                            val target = File(targetParent, trimmed)
                            val ok = runCatching {
                                if (isFolder) target.mkdirs() else {
                                    target.parentFile?.mkdirs()
                                    target.createNewFile()
                                }
                            }.getOrDefault(false)
                            if (ok || target.exists()) {
                                refreshTrigger++
                                createDialogIsFolder = null
                                if (!isFolder) {
                                    onOpenFileInIde(target)
                                }
                            } else {
                                Toast.makeText(context, "Could not create $trimmed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AntiGemAccent)
                ) {
                    Text("Create", color = Color(0xFF0B0C10), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { createDialogIsFolder = null }) {
                    Text("Cancel", color = AntiGemTextSecondary)
                }
            }
        )
    }

    actionSheetFile?.let { file ->
        val isDir = FileManagerOperations.isEffectiveDirectory(file)
        val isArchive = !isDir && file.extension.lowercase(Locale.ROOT) in FileManagerOperations.ARCHIVE_EXTENSIONS
        ItemActionBottomSheet(
            file = file,
            showHidden = showHiddenFiles,
            onOpen = { handleFileClick(file) },
            onCopy = {
                clipboardState = FileClipboardState(listOf(file), FileClipboardMode.COPY)
                Toast.makeText(context, "Copied ${file.name}", Toast.LENGTH_SHORT).show()
            },
            onCut = {
                clipboardState = FileClipboardState(listOf(file), FileClipboardMode.CUT)
                Toast.makeText(context, "Cut ${file.name}", Toast.LENGTH_SHORT).show()
            },
            onRename = { renameTargetFile = file },
            onDelete = { deleteConfirmFiles = listOf(file) },
            onOpenInIde = {
                if (isDir) openDirectoryInIde(file) else onOpenFileInIde(file)
            },
            onCompressToZip = {
                val outZip = File(FileManagerOperations.resolveDirectoryTarget(currentDirectory), "${file.name}.zip")
                scope.launch {
                    isRefreshing = true
                    FileManagerOperations.compressToZip(listOf(file), outZip)
                        .onSuccess {
                            Toast.makeText(context, "Saved ${it.name}", Toast.LENGTH_SHORT).show()
                            refreshTrigger++
                        }
                        .onFailure {
                            Toast.makeText(context, "Compress failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    isRefreshing = false
                }
            },
            onExtractArchive = if (isArchive) {
                {
                    scope.launch {
                        isRefreshing = true
                        FileManagerOperations.extractArchive(file, currentDirectory)
                            .onSuccess { count ->
                                Toast.makeText(context, "Extracted $count entries", Toast.LENGTH_SHORT).show()
                                refreshTrigger++
                            }
                            .onFailure {
                                Toast.makeText(context, "Extract failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        isRefreshing = false
                    }
                }
            } else null,
            onCopyTo = {
                clipboardState = FileClipboardState(listOf(file), FileClipboardMode.COPY_TO)
                Toast.makeText(context, "Navigate to destination folder and tap 'Copy here'", Toast.LENGTH_SHORT).show()
            },
            onMoveTo = {
                clipboardState = FileClipboardState(listOf(file), FileClipboardMode.MOVE_TO)
                Toast.makeText(context, "Navigate to destination folder and tap 'Move here'", Toast.LENGTH_SHORT).show()
            },
            onDuplicate = {
                scope.launch {
                    FileManagerOperations.duplicateItem(file)
                        .onSuccess {
                            Toast.makeText(context, "Duplicated as ${it.name}", Toast.LENGTH_SHORT).show()
                            refreshTrigger++
                        }
                        .onFailure {
                            Toast.makeText(context, "Duplicate failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                }
            },
            onCopyPath = {
                FileManagerOperations.copyPathToClipboard(context, file.absolutePath)
            },
            onDetails = { detailsTargetFile = file },
            onDismiss = { actionSheetFile = null }
        )
    }

    renameTargetFile?.let { file ->
        var newName by remember(file) { mutableStateOf(file.name) }
        AlertDialog(
            onDismissRequest = { renameTargetFile = null },
            containerColor = AntiGemSheetBg,
            title = { Text("Rename", color = AntiGemTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AntiGemAccent,
                        unfocusedBorderColor = Color(0xFF2D3348),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newName.trim()
                        if (trimmed.isNotEmpty() && trimmed != file.name) {
                            val dest = File(file.parentFile, trimmed)
                            if (file.renameTo(dest)) {
                                refreshTrigger++
                            } else {
                                Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                        renameTargetFile = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AntiGemAccent)
                ) {
                    Text("Save", color = Color(0xFF0B0C10), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetFile = null }) {
                    Text("Cancel", color = AntiGemTextSecondary)
                }
            }
        )
    }

    deleteConfirmFiles?.let { filesToDelete ->
        AlertDialog(
            onDismissRequest = { deleteConfirmFiles = null },
            containerColor = AntiGemSheetBg,
            title = {
                Text(
                    text = if (filesToDelete.size == 1) "Delete ${filesToDelete.first().name}?" else "Delete ${filesToDelete.size} items?",
                    color = AntiGemTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This action will permanently remove the selected item(s) from storage.",
                    color = AntiGemTextSecondary,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            for (f in filesToDelete) {
                                runCatching { f.deleteRecursively() }
                            }
                            selectedPaths.clear()
                            deleteConfirmFiles = null
                            refreshTrigger++
                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AntiGemDanger)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmFiles = null }) {
                    Text("Cancel", color = AntiGemTextSecondary)
                }
            }
        )
    }

    detailsTargetFile?.let { file ->
        FolderOrFileDetailsSheet(
            file = file,
            onCopyPath = { path -> FileManagerOperations.copyPathToClipboard(context, path) },
            onDismiss = { detailsTargetFile = null }
        )
    }

    openWithTargetFile?.let { file ->
        val ext = file.extension.lowercase(Locale.ROOT)
        OpenWithBottomSheet(
            file = file,
            onViewBuiltIn = { rememberChoice ->
                if (rememberChoice) FileManagerOperations.setAlwaysOpenHandler(context, ext, "builtin")
                builtInImageViewerFile = file
            },
            onOpenExternal = { rememberChoice ->
                if (rememberChoice) FileManagerOperations.setAlwaysOpenHandler(context, ext, "external")
                FileManagerOperations.openFileExternal(context, file)
            },
            onShare = {
                FileManagerOperations.shareFileExternal(context, file)
            },
            onDismiss = { openWithTargetFile = null }
        )
    }

    builtInImageViewerFile?.let { file ->
        BuiltInImageViewerDialog(
            file = file,
            onShare = { FileManagerOperations.shareFileExternal(context, file) },
            onSaveToDownloads = {
                scope.launch {
                    FileManagerOperations.saveFileToDownloads(context, file)
                        .onSuccess { saved ->
                            Toast.makeText(context, "Saved to Downloads/${saved.name}", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            Toast.makeText(context, "Save failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                }
            },
            onDismiss = { builtInImageViewerFile = null }
        )
    }

    if (showSortAndViewSheet) {
        SortAndViewBottomSheet(
            sortField = sortField,
            sortOrder = sortOrder,
            isGridView = isGridView,
            showHiddenFiles = showHiddenFiles,
            onSelectSortField = { sortField = it },
            onSelectSortOrder = { sortOrder = it },
            onToggleGridView = {
                isGridView = it
                FileManagerOperations.setGridModePreference(context, it)
            },
            onToggleShowHidden = {
                showHiddenFiles = it
                FileManagerOperations.setShowHiddenPreference(context, it)
            },
            onDismiss = { showSortAndViewSheet = false }
        )
    }
}

@Composable
private fun SearchScopeChip(
    label: String,
    selected: Boolean,
    leadingSearchIcon: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color(0xFF252D44) else Color(0xFF171922),
        border = BorderStroke(1.dp, if (selected) AntiGemAccent.copy(alpha = 0.45f) else Color(0xFF252938)),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingSearchIcon) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (selected) AntiGemAccent else AntiGemTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) AntiGemTextPrimary else AntiGemTextSecondary
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AntiGemFileListRow(
    file: File,
    showHidden: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val isDir = remember(file) { FileManagerOperations.isEffectiveDirectory(file) }
    val isProjectDir = remember(file, isDir) { isDir && FileManagerOperations.isProjectDirectory(file) }
    val subtitle = remember(file, isDir, showHidden) {
        val dateStr = FileManagerOperations.formatTimestamp(file.lastModified())
        if (isDir) {
            "${FileManagerOperations.getDirectoryItemCountLabel(file, showHidden)} • $dateStr"
        } else {
            "${FileManagerOperations.formatBytes(file.length())} • $dateStr"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) Color(0xFF1F273B) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            FileOrFolderLeadingThumb(
                file = file,
                isDirectory = isDir,
                isProjectDir = isProjectDir,
                sizeDp = 44
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(AntiGemAccent.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color(0xFF0B0C10),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = AntiGemTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = AntiGemTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "File options",
                tint = AntiGemTextSecondary,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AntiGemFileGridCard(
    file: File,
    showHidden: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isDir = remember(file) { FileManagerOperations.isEffectiveDirectory(file) }
    val isProjectDir = remember(file, isDir) { isDir && FileManagerOperations.isProjectDirectory(file) }
    val subLabel = remember(file, isDir, showHidden) {
        if (isDir) FileManagerOperations.getDirectoryItemCountLabel(file, showHidden)
        else FileManagerOperations.formatBytes(file.length())
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color(0xFF232C44) else Color(0xFF151822),
        border = if (isSelected) BorderStroke(1.dp, AntiGemAccent) else null,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FileOrFolderLeadingThumb(
                file = file,
                isDirectory = isDir,
                isProjectDir = isProjectDir,
                sizeDp = 48
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = file.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = AntiGemTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subLabel,
                fontSize = 11.sp,
                color = AntiGemTextSecondary,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}
