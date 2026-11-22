package com.jarvisai.assistant.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.data.remote.JarvisHubConfig
import com.jarvisai.assistant.data.run.RunEntry
import com.jarvisai.assistant.data.run.RunManager
import com.jarvisai.assistant.data.run.RunStatus
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunScreen(
    onBack: () -> Unit,
    onOpenPreviewUrl: (String) -> Unit
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    LaunchedEffect(Unit) { RunManager.loadCustomTemplates(context) }

    val runs by RunManager.runs.collectAsState()
    val customTemplates by RunManager.customTemplates.collectAsState()
    val projectsRoot = remember { LocalEnvironmentManager.getIsolatedProjectsDir(context) }
    val projectDirs = remember(projectsRoot) {
        val list = projectsRoot.listFiles { f -> f.isDirectory }?.sortedBy { it.name.lowercase() }.orEmpty()
        listOf(projectsRoot) + list
    }
    var selectedDir by remember { mutableStateOf(projectDirs.firstOrNull() ?: projectsRoot) }
    var showAddCommandDialog by remember { mutableStateOf(false) }

    val allTemplates = remember(customTemplates) {
        RunManager.builtInTemplates() + customTemplates
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = Color(0xFF0A0F1A),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Live App Runner", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "Web Preview Port :${JarvisHubConfig.ISOLATED_WEB_PREVIEW_PORT} (Isolated)",
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("run_screen_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddCommandDialog = true }, modifier = Modifier.size(48.dp).testTag("run_add_cmd_btn")) {
                        Icon(Icons.Default.Add, contentDescription = "Add custom run command", tint = Color(0xFF38BDF8))
                    }
                    IconButton(onClick = { RunManager.clearFinished() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear finished runs", tint = Color(0xFF94A3B8))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0E1525))
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Working Directory", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        projectDirs.forEach { dir ->
                            val label = if (dir == projectsRoot) "projects/" else dir.name
                            FilterChip(
                                selected = selectedDir.absolutePath == dir.absolutePath,
                                onClick = { selectedDir = dir },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            item {
                Text("Quick Dev Servers & Commands", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            items(allTemplates, key = { it.id }) { tpl ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF111827),
                    border = BorderStroke(1.dp, Color(0xFF1F2937)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tpl.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = tpl.command,
                                color = Color(0xFF94A3B8),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                RunManager.startRun(
                                    context = context,
                                    name = tpl.name,
                                    command = tpl.command,
                                    workingDir = selectedDir,
                                    defaultPreviewUrl = tpl.previewUrl
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("run_launch_${tpl.id}")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = Color(0xFF04101D), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Run", color = Color(0xFF04101D), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (runs.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("Active & Recent Runs (${runs.size})", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                items(runs, key = { it.id }) { run ->
                    RunProcessCard(
                        run = run,
                        onStop = { RunManager.stopRun(context, run.id) },
                        onRestart = {
                            RunManager.stopRun(context, run.id)
                            RunManager.startRun(context, run.name, run.command, File(run.cwd), run.detectedUrl)
                        },
                        onCopyLogs = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText(run.name, run.outputLines.joinToString("\n")))
                            Toast.makeText(context, "Run logs copied", Toast.LENGTH_SHORT).show()
                        },
                        onOpenUrl = { url -> onOpenPreviewUrl(url) }
                    )
                }
            }
        }
    }

    if (showAddCommandDialog) {
        var name by remember { mutableStateOf("") }
        var cmd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCommandDialog = false },
            containerColor = Color(0xFF111827),
            title = { Text("Add Custom Run Command", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Label") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cmd,
                        onValueChange = { cmd = it },
                        label = { Text("Shell Command") },
                        placeholder = { Text("e.g. python3 app.py") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (cmd.isNotBlank()) {
                            RunManager.addCustomTemplate(context, name.ifBlank { cmd.take(24) }, cmd)
                            showAddCommandDialog = false
                        }
                    },
                    enabled = cmd.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                ) {
                    Text("Save", color = Color(0xFF04101D), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCommandDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

@Composable
private fun RunProcessCard(
    run: RunEntry,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onCopyLogs: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val statusColor = when (run.status) {
        RunStatus.RUNNING -> Color(0xFF10B981)
        RunStatus.SUCCEEDED -> Color(0xFF38BDF8)
        RunStatus.FAILED -> Color(0xFFF87171)
        RunStatus.STOPPED -> Color(0xFF94A3B8)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF111827),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("run_entry_${run.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(run.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${run.status.name}${run.exitCode?.let { " (exit $it)" }.orEmpty()}",
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = onCopyLogs, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy output", tint = Color(0xFF94A3B8), modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onRestart, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Restart command", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                }
                if (run.status == RunStatus.RUNNING) {
                    IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop process", tint = Color(0xFFF87171), modifier = Modifier.size(18.dp))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 180.dp)
                    .background(Color(0xFF050810), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                run.outputLines.takeLast(14).forEach { line ->
                    Text(
                        text = line,
                        color = Color(0xFFCBD5E1),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            run.detectedUrl?.let { url ->
                OutlinedButton(
                    onClick = { onOpenUrl(url) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open Preview ($url)", color = Color(0xFF38BDF8), fontSize = 12.sp)
                }
            }
        }
    }
}
