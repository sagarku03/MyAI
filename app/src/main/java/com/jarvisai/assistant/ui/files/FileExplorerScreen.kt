package com.jarvisai.assistant.ui.files

import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.WorkspaceSyncManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AntiGem-Grade Standalone File Explorer & Workspace Manager.
 * Allows browsing, searching, creating, deleting, and 1-tap opening files in Sora IDE.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    initialDir: File? = null,
    onBack: () -> Unit,
    onOpenFileInIde: (File) -> Unit
) {
    val context = LocalContext.current
    var currentDirectory by remember {
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        val defaultDir = when {
            initialDir != null && initialDir.exists() -> initialDir
            homeDir.exists() -> homeDir
            else -> Environment.getExternalStorageDirectory()
        }
        mutableStateOf(defaultDir)
    }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createIsDirectory by remember { mutableStateOf(false) }
    var selectedFileForAction by remember { mutableStateOf<File?>(null) }

    val syncTimestamp by WorkspaceSyncManager.syncTimestamp.collectAsState()

    LaunchedEffect(syncTimestamp) {
        refreshTrigger++
    }

    val fileList = remember(currentDirectory, searchQuery, refreshTrigger) {
        val files = currentDirectory.listFiles()?.toList() ?: emptyList()
        val sorted = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        if (searchQuery.isBlank()) sorted
        else sorted.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Scaffold(
        modifier = Modifier.testTag("file_explorer_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Workspace Explorer",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = currentDirectory.absolutePath,
                            fontSize = 11.sp,
                            color = Color(0xFF00E5FF),
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        val parent = currentDirectory.parentFile
                        if (parent != null && parent.canRead()) {
                            currentDirectory = parent
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        createIsDirectory = false
                        showCreateDialog = true
                    }) {
                        Icon(imageVector = Icons.Default.NoteAdd, contentDescription = "New File", tint = Color(0xFF00E5FF))
                    }
                    IconButton(onClick = {
                        createIsDirectory = true
                        showCreateDialog = true
                    }) {
                        Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = Color(0xFFF59E0B))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E17))
            )
        },
        containerColor = Color(0xFF0A0E17)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter files & folders...", fontSize = 12.sp, color = Color(0xFF64748B)) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            // File List
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (fileList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Directory is empty", color = Color(0xFF64748B), fontSize = 13.sp)
                        }
                    }
                } else {
                    items(fileList, key = { it.absolutePath }) { file ->
                        FileItemRow(
                            file = file,
                            onClick = {
                                if (file.isDirectory) {
                                    currentDirectory = file
                                } else {
                                    onOpenFileInIde(file)
                                }
                            },
                            onLongClick = { selectedFileForAction = file }
                        )
                    }
                }
            }
        }
    }

    // Create File / Folder Dialog
    if (showCreateDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if (createIsDirectory) "Create New Directory" else "Create New File", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text(if (createIsDirectory) "e.g. src" else "e.g. main.py") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            val target = File(currentDirectory, newName.trim())
                            val success = runCatching {
                                if (createIsDirectory) target.mkdirs() else target.createNewFile()
                            }.getOrDefault(false)
                            if (success) {
                                Toast.makeText(context, "Created successfully!", Toast.LENGTH_SHORT).show()
                                refreshTrigger++
                            } else {
                                Toast.makeText(context, "Failed to create", Toast.LENGTH_SHORT).show()
                            }
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("Create", color = Color(0xFF0A0E17), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF111827)
        )
    }

    // File Action Sheet (Delete / Open in IDE)
    selectedFileForAction?.let { file ->
        AlertDialog(
            onDismissRequest = { selectedFileForAction = null },
            title = { Text(file.name, color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace) },
            text = {
                Text("Select an action for this ${if (file.isDirectory) "folder" else "file"}:", color = Color(0xFF94A3B8), fontSize = 12.sp)
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!file.isDirectory) {
                        Button(
                            onClick = {
                                onOpenFileInIde(file)
                                selectedFileForAction = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                        ) {
                            Text("Open in IDE", color = Color(0xFF0A0E17), fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            file.deleteRecursively()
                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                            refreshTrigger++
                            selectedFileForAction = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedFileForAction = null }) {
                    Text("Cancel", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF111827)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileItemRow(
    file: File,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateStr = remember(file) {
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = Color(0xFF111827),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (file.isDirectory) Color(0xFFF59E0B) else Color(0xFF38BDF8),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = if (file.isDirectory) "Directory • $dateStr" else "${file.length() / 1024} KB • $dateStr",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }
            if (!file.isDirectory) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "Open in IDE",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = onLongClick,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "File Actions",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
