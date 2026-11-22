package com.jarvisai.assistant.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.data.miniapps.MiniAppInfo
import com.jarvisai.assistant.data.miniapps.MiniAppsManager
import com.jarvisai.assistant.ui.miniapps.MiniAppActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniAppsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    LaunchedEffect(Unit) { MiniAppsManager.refresh(context) }

    val apps by MiniAppsManager.apps.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

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
                        Text("Agentra Mini-Apps", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("~/mini-apps · Sandboxed WebView SDK", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("miniapps_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { MiniAppsManager.refresh(context) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh mini-apps", tint = Color(0xFF38BDF8))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0E1525))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = Color(0xFF38BDF8),
                contentColor = Color(0xFF04101D),
                modifier = Modifier.testTag("miniapps_create_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create starter Mini-App")
            }
        }
    ) { innerPadding ->
        if (apps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Apps, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(48.dp))
                    Text("No Mini-Apps installed yet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Create a starter Mini-App here or ask an Agentra agent to build one via /mini_app_developer MCP tools.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.5.sp
                    )
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        modifier = Modifier.testTag("miniapps_empty_create_btn")
                    ) {
                        Text("Create Starter Mini-App", color = Color(0xFF04101D), fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(apps, key = { it.slug }) { app ->
                    MiniAppCard(
                        app = app,
                        onLaunch = {
                            context.startActivity(MiniAppActivity.createIntent(context, app.slug))
                        },
                        onClearData = {
                            MiniAppsManager.clearAppData(context, app)
                            Toast.makeText(context, "Cleared storage for ${app.name}", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = {
                            MiniAppsManager.deleteApp(context, app)
                            Toast.makeText(context, "Deleted ${app.name}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = Color(0xFF111827),
            title = { Text("Create Starter Mini-App", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("App Name") },
                        placeholder = { Text("e.g. Pomodoro Timer") },
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
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Description") },
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
                        val res = MiniAppsManager.createStarterApp(
                            context = context,
                            rawName = name,
                            description = desc.ifBlank { "Sandboxed Agentra Mini-App" }
                        )
                        res.fold(
                            onSuccess = { created ->
                                showCreateDialog = false
                                Toast.makeText(context, "Created ${created.name}", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { err ->
                                Toast.makeText(context, err.message ?: "Failed", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                ) {
                    Text("Create", color = Color(0xFF04101D), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

@Composable
private fun MiniAppCard(
    app: MiniAppInfo,
    onLaunch: () -> Unit,
    onClearData: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF111827),
        border = BorderStroke(1.dp, Color(0xFF1F2937)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("miniapp_card_${app.slug}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.iconEmoji, fontSize = 24.sp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = "${app.id} · v${app.version}",
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Mini-App", tint = Color(0xFFF87171), modifier = Modifier.size(18.dp))
                }
            }

            if (app.description.isNotBlank()) {
                Text(app.description, color = Color(0xFFCBD5E1), fontSize = 12.5.sp)
            }

            if (app.warnings.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(15.dp))
                    Text(app.warnings.joinToString("; "), color = Color(0xFFF59E0B), fontSize = 11.5.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onLaunch,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("miniapp_launch_${app.slug}")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF04101D), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Launch", color = Color(0xFF04101D), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
                OutlinedButton(
                    onClick = onClearData,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear Data", color = Color(0xFF94A3B8), fontSize = 12.5.sp)
                }
            }
        }
    }
}
