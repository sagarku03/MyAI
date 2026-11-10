package com.jarvisai.assistant.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun ProjectSidebar(
    rootDirectory: File,
    onFileClick: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    var fileTree by remember(rootDirectory) { mutableStateOf(buildFileTree(rootDirectory)) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(260.dp)
            .background(Color(0xFF0F172A))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = rootDirectory.name.uppercase(),
                color = Color(0xFF38BDF8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { fileTree = buildFileTree(rootDirectory) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.LightGray)
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(fileTree) { node ->
                FileNodeItem(node = node, depth = 0, onFileClick = onFileClick)
            }
        }
    }
}

@Composable
private fun FileNodeItem(
    node: FileNode,
    depth: Int,
    onFileClick: (File) -> Unit
) {
    var isExpanded by remember { mutableStateOf(node.isExpanded) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (node.isDirectory) {
                        isExpanded = !isExpanded
                        node.isExpanded = isExpanded
                    } else {
                        onFileClick(node.file)
                    }
                }
                .padding(vertical = 4.dp, horizontal = (depth * 12).dp),
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
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = node.name,
                color = if (node.isDirectory) Color.White else Color(0xFFCBD5E1),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (node.isDirectory && isExpanded) {
            node.children.forEach { child ->
                FileNodeItem(node = child, depth = depth + 1, onFileClick = onFileClick)
            }
        }
    }
}

private fun buildFileTree(dir: File): List<FileNode> {
    if (!dir.exists() || !dir.isDirectory) return emptyList()
    val files = dir.listFiles() ?: return emptyList()

    return files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        .map { file ->
            FileNode(
                file = file,
                children = if (file.isDirectory) buildFileTree(file) else emptyList()
            )
        }
}
