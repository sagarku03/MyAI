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

private val MARKDOWN_LINK_FILE_URI_REGEX = Regex("""\[[^\]]+]\(file:///([^)\s]+)\)""")
private val FILE_URI_REGEX = Regex("""file:///([^\s\)\]`"'>]+)""")

fun extractFileLinks(text: String): List<String> {
    val withoutInlineMarkdownLinks = MARKDOWN_LINK_FILE_URI_REGEX.replace(text, " ")
    return FILE_URI_REGEX.findAll(withoutInlineMarkdownLinks)
        .map { "/" + it.groupValues[1] }
        .distinct()
        .take(4)
        .toList()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileLinkRow(
    text: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val filePaths = remember(text) { extractFileLinks(text) }
    var inspectedFile by remember { mutableStateOf<File?>(null) }

    if (filePaths.isEmpty()) return

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        filePaths.forEach { rawPath ->
            val file = remember(rawPath) { File(rawPath) }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(0.7.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
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
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = file.name.ifBlank { rawPath },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF38BDF8),
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    inspectedFile?.let { f ->
        FileInspectorDialog(
            file = f,
            onDismiss = { inspectedFile = null }
        )
    }
}

@Composable
fun FileInspectorDialog(
    file: File,
    onDismiss: () -> Unit
) {
    val fileContent = remember(file) {
        runCatching { file.readText().take(24_000) }.getOrDefault("Unable to read file: ${file.absolutePath}")
    }
    val ext = remember(file) { file.extension.ifBlank { "txt" } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0B132B),
        title = {
            Text(
                text = file.name,
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
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF38BDF8))
            }
        }
    )
}
