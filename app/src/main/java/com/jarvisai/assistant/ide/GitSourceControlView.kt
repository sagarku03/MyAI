package com.jarvisai.assistant.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.terminal.LocalTerminalBridge
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun GitSourceControlView(
    projectDir: File,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var gitStatus by remember { mutableStateOf(GitStatusResponse()) }
    var commitMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    fun refreshStatus() {
        coroutineScope.launch {
            isLoading = true
            val res = LocalTerminalBridge.instance.executeCommand("cd '${projectDir.absolutePath}' && git status -s")
            val lines = res.output.lines().filter { it.isNotBlank() }
            val unstaged = lines.map { it.trim() }
            gitStatus = GitStatusResponse(unstagedFiles = unstaged)
            isLoading = false
        }
    }

    LaunchedEffect(projectDir) {
        refreshStatus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SOURCE CONTROL", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { refreshStatus() }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.LightGray)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = commitMessage,
            onValueChange = { commitMessage = it },
            placeholder = { Text("Commit message (Ctrl+Enter)", fontSize = 12.sp, color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (commitMessage.isNotBlank()) {
                    coroutineScope.launch {
                        LocalTerminalBridge.instance.executeCommand("cd '${projectDir.absolutePath}' && git add -A && git commit -m '${commitMessage.replace("'", "\\'")}'")
                        commitMessage = ""
                        refreshStatus()
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Commit All Changes", fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("CHANGES (${gitStatus.unstagedFiles.size})", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 4.dp)) {
            items(gitStatus.unstagedFiles) { change ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(change.take(2), color = Color(0xFFFCD34D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(change.drop(2).trim(), color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}
