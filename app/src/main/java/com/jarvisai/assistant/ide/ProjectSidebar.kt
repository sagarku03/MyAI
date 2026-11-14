package com.jarvisai.assistant.ide

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val IGNORED_HEAVY_DIRS = setOf(
    "node_modules", ".git", ".gradle", ".cache", "__pycache__", "build", ".idea"
)

private const val MAX_ENTRIES_PER_DIR = 250

@Composable
fun ProjectSidebar(
    rootDirectory: File,
    onFileClick: (File) -> Unit,
    onSelectProjectRoot: (File) -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var rootNodes by remember(rootDirectory.absolutePath) { mutableStateOf<List<FileNode>>(emptyList()) }
    var isLoading by remember(rootDirectory.absolutePath) { mutableStateOf(true) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    var showNewFilePrompt by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newFileError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(rootDirectory.absolutePath, refreshNonce) {
        isLoading = true
        rootNodes = withContext(Dispatchers.IO) {
            listImmediateChildren(rootDirectory)
        }
        isLoading = false
    }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(276.dp)
            .testTag("ide_project_sidebar"),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Color(0xFF1E3A5F)),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rootDirectory.name.ifBlank { "WORKSPACE" }.uppercase(),
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = rootDirectory.absolutePath,
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            rootDirectory.parentFile?.let { parent ->
                                if (parent.canRead()) onSelectProjectRoot(parent)
                            }
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("sidebar_up_button")
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Parent Folder",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { showNewFilePrompt = true },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("sidebar_new_file_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.NoteAdd,
                            contentDescription = "New File",
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { refreshNonce++ },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("sidebar_refresh_button")
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh Explorer",
                            tint = Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("sidebar_close_button")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close Sidebar",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = Color(0xFF38BDF8),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                rootNodes.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "This folder is empty",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(onClick = { showNewFilePrompt = true }) {
                                Text("Create New File", color = Color(0xFF38BDF8), fontSize = 12.sp)
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(rootNodes, key = { it.path }) { node ->
                            LazyFileNodeItem(
                                node = node,
                                depth = 0,
                                onFileClick = onFileClick,
                                onSetAsRoot = onSelectProjectRoot
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewFilePrompt) {
        AlertDialog(
            onDismissRequest = { showNewFilePrompt = false },
            containerColor = Color(0xFF111C35),
            title = {
                Text("New File", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = {
                            newFileName = it
                            newFileError = null
                        },
                        placeholder = { Text("e.g. main.py or index.html", color = Color(0xFF64748B)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155),
                            cursorColor = Color(0xFF38BDF8)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (newFileError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(newFileError ?: "", color = Color(0xFFF87171), fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newFileName.trim().replace("/", "_")
                        if (trimmed.isBlank()) {
                            newFileError = "Enter a valid file name"
                            return@Button
                        }
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    if (!rootDirectory.exists()) rootDirectory.mkdirs()
                                    val target = File(rootDirectory, trimmed)
                                    if (!target.exists()) target.createNewFile()
                                    target
                                }
                            }
                            result.onSuccess { created ->
                                showNewFilePrompt = false
                                newFileName = ""
                                refreshNonce++
                                onFileClick(created)
                            }.onFailure { err ->
                                newFileError = err.message ?: "Failed to create file"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("Create", color = Color(0xFF070B14), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFilePrompt = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

@Composable
private fun LazyFileNodeItem(
    node: FileNode,
    depth: Int,
    onFileClick: (File) -> Unit,
    onSetAsRoot: (File) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isExpanded by remember(node.path) { mutableStateOf(false) }
    var isLoadingChildren by remember(node.path) { mutableStateOf(false) }
    var children by remember(node.path) { mutableStateOf<List<FileNode>>(emptyList()) }
    var hasLoadedChildren by remember(node.path) { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
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
                .padding(
                    start = ((depth * 14).coerceAtMost(84) + 6).dp,
                    end = 4.dp,
                    top = 7.dp,
                    bottom = 7.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (node.isDirectory) {
                    if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder
                } else {
                    Icons.Default.Description
                },
                contentDescription = null,
                tint = if (node.isDirectory) Color(0xFFFCD34D) else Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = node.name,
                color = if (node.isDirectory) Color.White else Color(0xFFCBD5E1),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (node.isDirectory) {
                Text(
                    text = "Open",
                    color = Color(0xFF38BDF8),
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onSetAsRoot(node.file) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        if (node.isDirectory && isExpanded) {
            if (isLoadingChildren) {
                Text(
                    text = "Loading...",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = (((depth + 1) * 14).coerceAtMost(84) + 12).dp, bottom = 4.dp)
                )
            } else {
                children.forEach { child ->
                    LazyFileNodeItem(
                        node = child,
                        depth = depth + 1,
                        onFileClick = onFileClick,
                        onSetAsRoot = onSetAsRoot
                    )
                }
            }
        }
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
        // Prevent infinite recursion when a symlink points back to an ancestor or itself
        parentCanon == childCanon || parentCanon.startsWith("$childCanon/")
    }.getOrDefault(false)
}
