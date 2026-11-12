package com.jarvisai.assistant.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

private val FILE_URI_REGEX = Regex("""file:///([^\s\)\]`"'>]+)""")

fun extractFileLinks(text: String): List<String> {
    return FILE_URI_REGEX.findAll(text).map { "/" + it.groupValues[1] }.distinct().toList()
}

@Composable
fun FileLinkRow(
    text: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val filePaths = remember(text) { extractFileLinks(text) }
    var inspectedFile by remember { mutableStateOf<File?>(null) }

    if (filePaths.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        filePaths.forEach { rawPath ->
            val file = remember(rawPath) { File(rawPath) }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(0.8.dp, Color(0xFF00E5FF).copy(alpha = 0.45f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        if (file.exists() && file.isFile) {
                            inspectedFile = file
                        } else {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("File Path", rawPath))
                            Toast.makeText(context, "Copied path: $rawPath", Toast.LENGTH_SHORT).show()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = file.name.ifBlank { rawPath },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF),
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = rawPath,
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }

    inspectedFile?.let { f ->
        val fileContent = remember(f) {
            runCatching { f.readText().take(24_000) }.getOrDefault("Unable to read file: ${f.absolutePath}")
        }
        val ext = remember(f) { f.extension.ifBlank { "txt" } }
        AlertDialog(
            onDismissRequest = { inspectedFile = null },
            containerColor = Color(0xFF0B132B),
            title = {
                Text(
                    text = f.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                CodeBlock(
                    code = fileContent,
                    language = ext
                )
            },
            confirmButton = {
                TextButton(onClick = { inspectedFile = null }) {
                    Text("Close", color = Color(0xFF00E5FF))
                }
            }
        )
    }
}
