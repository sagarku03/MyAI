package com.jarvisai.assistant.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.window.Dialog
import com.jarvisai.assistant.data.remote.AgyAuthService
import com.jarvisai.assistant.data.remote.JarvisHubConfig
import com.jarvisai.assistant.terminal.LocalServerManager
import kotlinx.coroutines.launch

/**
 * Standalone Antigravity Daemon Authentication & Live Diagnostics Dialog for Jarvis AI's isolated hub (Port 2235).
 * Ensures Jarvis AI maintains its own dedicated Google OAuth session in ~/.gemini/jarvis-antigravity,
 * surfaces `agy` binary + MCP + Linux Core readiness, and offers 1-tap Hub restart/scan actions.
 */
@Composable
fun AntigravityAuthDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authService = remember { AgyAuthService(context) }
    val diagnostics by LocalServerManager.agyDiagnostics.collectAsState()

    var isChecking by remember { mutableStateOf(true) }
    var isWorkingAction by remember { mutableStateOf(false) }
    var authStatus by remember { mutableStateOf<AgyAuthService.AuthStatus?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun refreshStatus() {
        scope.launch {
            isChecking = true
            statusMessage = null
            LocalServerManager.refreshDiagnostics(context)
            authService.checkAuthStatus()
                .onSuccess { status ->
                    authStatus = status
                }
                .onFailure { err ->
                    statusMessage = "Hub check: ${err.message}"
                }
            isChecking = false
        }
    }

    LaunchedEffect(Unit) {
        refreshStatus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("antigravity_isolated_auth_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF111827),
            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E5FF).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "Isolated Antigravity Hub",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Jarvis Isolated Antigravity Hub",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Port ${diagnostics.hubPort} • Data Dir: ~/.gemini/${JarvisHubConfig.ISOLATED_APP_DATA_DIR}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00E5FF)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Live Daemon & Engine Diagnostics Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("agy_daemon_diagnostics_card"),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DiagnosticStatusRow(
                            label = "Local Hub Daemon (Port ${diagnostics.hubPort})",
                            ok = diagnostics.agyHubReachable,
                            detail = if (diagnostics.agyHubReachable) "Cascade RPC Active" else "Offline (CloudCode PA fallback ready)"
                        )
                        DiagnosticStatusRow(
                            label = "Native `agy` Binary",
                            ok = diagnostics.agyBinaryFound,
                            detail = diagnostics.agyBinaryPath ?: "Place arm64 'agy' in /sdcard/Download/ or Termux ~/.local/bin/"
                        )
                        DiagnosticStatusRow(
                            label = "Embedded MCP Config (:8765 / :8091)",
                            ok = diagnostics.mcpConfigured,
                            detail = if (diagnostics.mcpConfigured) "jarvis-android-mcp & browser-mcp synced" else "Pending sync"
                        )
                        DiagnosticStatusRow(
                            label = "Built-in Linux Core Engine",
                            ok = diagnostics.linuxCoreInstalled,
                            detail = if (diagnostics.linuxCoreInstalled) "PTY & Toolchain Ready" else "Setup Pending"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isChecking || isWorkingAction) {
                    CircularProgressIndicator(
                        color = Color(0xFF00E5FF),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (authStatus?.isLoggedIn == true) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1F2937),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Authenticated Account",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = authStatus?.name?.takeIf { it.isNotBlank() } ?: "Authenticated Account",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = authStatus?.email?.takeIf { it.isNotBlank() } ?: "Isolated Session Active",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                authService.startLogin()
                                    .onSuccess { authUrl ->
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
                                        }
                                    }
                                    .onFailure { err ->
                                        Toast.makeText(
                                            context,
                                            "Daemon login error: ${err.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("agy_sign_in_hub_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Login,
                            contentDescription = "Sign in to Isolated Hub",
                            tint = Color(0xFF0A0E17),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sign in to Isolated Hub (Port ${diagnostics.hubPort})",
                            color = Color(0xFF0A0E17),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isWorkingAction = true
                                LocalServerManager.provisionEmbeddedMcpConfig(context)
                                LocalServerManager.restartAgyHubForAuth(context)
                                kotlinx.coroutines.delay(1500)
                                val updated = LocalServerManager.refreshDiagnostics(context)
                                statusMessage = if (updated.agyHubReachable) {
                                    "Antigravity Hub restarted on Port ${updated.hubPort}."
                                } else {
                                    updated.lastNotice.ifBlank { "Hub restarted (awaiting `agy` binary)." }
                                }
                                isWorkingAction = false
                            }
                        },
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("agy_restart_hub_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Restart Hub & Sync MCP",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restart Hub", color = Color(0xFF00E5FF), fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isWorkingAction = true
                                LocalServerManager.provisionEmbeddedMcpConfig(context)
                                val reachable = LocalServerManager.ensureAgyHubAvailable(context, maxWaitMs = 5000L)
                                val updated = LocalServerManager.refreshDiagnostics(context)
                                statusMessage = when {
                                    reachable -> "Found `agy` & Hub is live on Port ${updated.hubPort}."
                                    updated.agyBinaryFound -> "Found `agy` at ${updated.agyBinaryPath}."
                                    else -> "No `agy` binary found yet in Download or Termux."
                                }
                                isWorkingAction = false
                            }
                        },
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("agy_scan_binary_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Scan & Start agy Binary",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan `agy`", color = Color(0xFF38BDF8), fontSize = 12.sp)
                    }
                }

                val noticeToShow = statusMessage ?: diagnostics.lastNotice.takeIf { it.isNotBlank() }
                noticeToShow?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        fontSize = 11.sp,
                        color = Color(0xFFF59E0B)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { refreshStatus() },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("agy_refresh_status_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Status",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Check Hub Status", color = Color(0xFF00E5FF), fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("agy_dialog_close_button")
                    ) {
                        Text(text = "Close", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticStatusRow(
    label: String,
    ok: Boolean,
    detail: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = if (ok) "Ready" else "Pending",
            tint = if (ok) Color(0xFF10B981) else Color(0xFFF59E0B),
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = detail,
                color = Color(0xFF94A3B8),
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
