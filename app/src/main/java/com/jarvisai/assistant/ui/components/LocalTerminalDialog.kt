package com.jarvisai.assistant.ui.components

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jarvisai.assistant.terminal.LocalTerminalManager
import com.termux.view.TerminalView

@Composable
fun LocalTerminalDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val context = LocalContext.current
        val sessions by LocalTerminalManager.sessions.collectAsState()
        val activeSessionId by LocalTerminalManager.activeSessionId.collectAsState()

        LaunchedEffect(Unit) {
            if (sessions.isEmpty()) {
                LocalTerminalManager.createSession(context, "Bash")
            }
        }

        val activeSession = LocalTerminalManager.getActiveSession()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // --- Header & Session Tabs ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    sessions.forEach { s ->
                        val isSelected = s.id == activeSessionId
                        AssistChip(
                            onClick = { LocalTerminalManager.setActiveSession(s.id) },
                            label = { Text(s.name, color = if (isSelected) Color(0xFF38BDF8) else Color.White) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isSelected) Color(0xFF334155) else Color.Transparent
                            ),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    IconButton(onClick = { LocalTerminalManager.createSession(context, "Bash") }) {
                        Icon(Icons.Default.Add, contentDescription = "New Session", tint = Color(0xFF38BDF8))
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Terminal", tint = Color.LightGray)
                }
            }

            // --- Terminal Native Canvas View ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                if (activeSession != null) {
                    AndroidView(
                        factory = { ctx ->
                            TerminalView(ctx, null).apply {
                                attachSession(activeSession.terminalSession)
                                setTextSize(14)
                            }
                        },
                        update = { view ->
                            if (view.currentSession != activeSession.terminalSession) {
                                view.attachSession(activeSession.terminalSession)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF38BDF8))
                    }
                }
            }

            // --- Extra Keys Row ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
                    .horizontalScroll(rememberScrollState())
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val keys = listOf("ESC", "TAB", "CTRL", "ALT", "-", "/", "|", "UP", "DOWN", "LEFT", "RIGHT")
                keys.forEach { key ->
                    Button(
                        onClick = {
                            when (key) {
                                "TAB" -> activeSession?.write("\t")
                                "ESC" -> activeSession?.write("\u001B")
                                "UP" -> activeSession?.write("\u001B[A")
                                "DOWN" -> activeSession?.write("\u001B[B")
                                "RIGHT" -> activeSession?.write("\u001B[C")
                                "LEFT" -> activeSession?.write("\u001B[D")
                                else -> activeSession?.write(key)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(key, fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }
    }
}
