package com.jarvisai.assistant.ui.components

import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jarvisai.assistant.data.github.GitHubManager
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun GitCloneDialog(
    initialUrl: String = "",
    onCloned: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var repoUrl by remember { mutableStateOf(initialUrl) }
    var folderName by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var clonedDir by remember { mutableStateOf<File?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val logs = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    val activity = remember(context) {
        var c = context
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        c as? Activity
    }

    DisposableEffect(isRunning) {
        val window = activity?.window
        if (isRunning) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
    }

    val urlValid = repoUrl.trim().startsWith("https://") || repoUrl.trim().startsWith("git@")

    Dialog(
        onDismissRequest = { if (!isRunning) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0A0F1A),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clone Git Repository", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (!isRunning) onDismiss() }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close clone dialog", tint = Color(0xFF94A3B8))
                    }
                }

                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it },
                    enabled = !isRunning,
                    label = { Text("Repository URL (HTTPS)") },
                    placeholder = { Text("https://github.com/owner/repo.git") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("git_clone_url_input")
                )

                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    enabled = !isRunning,
                    label = { Text("Folder name (optional)") },
                    placeholder = { Text("Defaults to repository name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("git_clone_folder_input")
                )

                if (logs.isNotEmpty()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp, max = 200.dp)
                            .background(Color(0xFF050810), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(logs) { line ->
                            Text(
                                text = line,
                                color = Color(0xFFCBD5E1),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                errorMsg?.let { err ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                        Text(err, color = Color(0xFFF87171), fontSize = 12.sp)
                    }
                }

                clonedDir?.let { dir ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                        Text("Cloned to ${dir.name}", color = Color(0xFF10B981), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isRunning,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }

                    if (clonedDir != null) {
                        Button(
                            onClick = { onCloned(clonedDir!!) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("git_clone_open_btn")
                        ) {
                            Text("Open Project", color = Color(0xFF04101D), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                isRunning = true
                                errorMsg = null
                                clonedDir = null
                                logs.clear()
                                scope.launch {
                                    val res = GitHubManager.cloneRepository(context, repoUrl, folderName) { line ->
                                        logs.add(line)
                                    }
                                    isRunning = false
                                    res.fold(
                                        onSuccess = { clonedDir = it },
                                        onFailure = { errorMsg = it.message ?: "Clone failed" }
                                    )
                                }
                            },
                            enabled = !isRunning && urlValid,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("git_clone_start_btn")
                        ) {
                            if (isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF04101D)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Cloning...", color = Color(0xFF04101D))
                            } else {
                                Text("Clone", color = Color(0xFF04101D), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
