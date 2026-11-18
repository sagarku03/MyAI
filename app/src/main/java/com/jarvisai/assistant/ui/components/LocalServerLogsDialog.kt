package com.jarvisai.assistant.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jarvisai.assistant.terminal.LocalServerManager
import com.jarvisai.assistant.terminal.LocalServerStatus

private val AgyBlue = Color(0xFF3B82F6)
private val StatusGreen = Color(0xFF22C55E)
private val StatusWarning = Color(0xFFF59E0B)
private val StatusDanger = Color(0xFFEF4444)
private val StatusMuted = Color(0xFF6B7280)

/**
 * Jarvis AI `Local Server Logs` modal dialog with live supervisor log stream,
 * Copy button, Start (▶) / Stop (■) server control button, and syntax-colored terminal output.
 */
@Composable
fun LocalServerLogsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val status by LocalServerManager.status.collectAsState()
    val logs by LocalServerManager.serverLogs.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            runCatching {
                listState.animateScrollToItem((logs.size - 1).coerceAtLeast(0))
            }
        }
    }

    val dotColor = when (status) {
        is LocalServerStatus.Running, is LocalServerStatus.FallbackPTY -> StatusGreen
        is LocalServerStatus.Starting, is LocalServerStatus.Stopping -> StatusWarning
        is LocalServerStatus.Error -> StatusDanger
        is LocalServerStatus.Stopped, is LocalServerStatus.Idle -> StatusMuted
    }

    val statusText = when (val s = status) {
        is LocalServerStatus.Running, is LocalServerStatus.FallbackPTY -> "Running"
        is LocalServerStatus.Starting -> "Starting..."
        is LocalServerStatus.Stopping -> "Stopping..."
        is LocalServerStatus.Stopped -> "Stopped"
        is LocalServerStatus.Error -> "Error: ${s.message.take(25)}"
        is LocalServerStatus.Idle -> "Idle"
    }

    val isRunningOrStarting = status is LocalServerStatus.Running ||
        status is LocalServerStatus.FallbackPTY ||
        status is LocalServerStatus.Starting

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(18.dp))
                .testTag("local_server_logs_dialog"),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF13161C),
            border = BorderStroke(1.dp, Color(0xFF262B36)),
            tonalElevation = 0.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Column {
                            Text(
                                text = "Local Server Logs",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE5E7EB)
                            )
                            Text(
                                text = statusText,
                                fontSize = 11.sp,
                                color = dotColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Copy Button
                        IconButton(
                            onClick = {
                                val text = LocalServerManager.getServerLogTranscript()
                                if (text.isBlank()) {
                                    Toast.makeText(context, "The log is empty", Toast.LENGTH_SHORT).show()
                                } else {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Server log", text))
                                    Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("local_server_logs_copy_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy log",
                                tint = Color(0xFFE5E7EB),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Start Button (only while the server is not running)
                        if (!isRunningOrStarting) {
                            IconButton(
                                onClick = { LocalServerManager.restartServer(context) },
                                enabled = status !is LocalServerStatus.Stopping,
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("local_server_logs_start_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Start Server",
                                    tint = if (status is LocalServerStatus.Stopping) Color.Gray else Color(0xFF3B82F6),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Stop Button (when running or starting)
                        if (isRunningOrStarting) {
                            IconButton(
                                onClick = { LocalServerManager.stopServer() },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("local_server_logs_stop_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop Server",
                                    tint = StatusDanger,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Close Dialog Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("local_server_logs_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFFE5E7EB),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Terminal Console Area
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp)),
                    color = Color(0xFF0A0C10),
                    border = BorderStroke(1.dp, Color(0xFF222630))
                ) {
                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No bridge process running.\nTap ▶ above to start the local server.",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = StatusMuted,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    } else {
                        SelectionContainer {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                itemsIndexed(logs) { _, line ->
                                    StyledSupervisorLogLine(line)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StyledSupervisorLogLine(line: String) {
    val annotated = buildAnnotatedString {
        when {
            line.startsWith("====") -> {
                withStyle(SpanStyle(color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)) {
                    append(line)
                }
            }
            line.contains("IDE Server & AGY Hub Supervisor") || line.contains("Go IDE Server & AGY Hub Supervisor") -> {
                withStyle(SpanStyle(color = StatusGreen, fontWeight = FontWeight.Bold)) {
                    append(line)
                }
            }
            line.contains("India Time (IST):") -> {
                val parts = line.split(":", limit = 2)
                withStyle(SpanStyle(color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)) {
                    append(parts[0] + ":")
                }
                if (parts.size > 1) {
                    withStyle(SpanStyle(color = Color(0xFFE5E7EB))) {
                        append(parts[1])
                    }
                }
            }
            line.contains("• Security Token:") -> {
                val parts = line.split(":", limit = 2)
                withStyle(SpanStyle(color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)) {
                    append(parts[0] + ":")
                }
                if (parts.size > 1) {
                    withStyle(SpanStyle(color = Color(0xFFE5E7EB))) {
                        append(parts[1])
                    }
                }
            }
            line.contains("• Framed Header:") -> {
                val parts = line.split(":", limit = 2)
                withStyle(SpanStyle(color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)) {
                    append(parts[0] + ":")
                }
                if (parts.size > 1) {
                    withStyle(SpanStyle(color = Color(0xFFE5E7EB))) {
                        append(parts[1])
                    }
                }
            }
            line.trimStart().startsWith("🚀") || line.contains("IDE Server running at") -> {
                withStyle(SpanStyle(color = StatusGreen, fontWeight = FontWeight.SemiBold)) {
                    append(line)
                }
            }
            line.trimStart().startsWith("⚡ Force flag") || line.trimStart().startsWith("[!]") -> {
                withStyle(SpanStyle(color = StatusWarning, fontWeight = FontWeight.SemiBold)) {
                    append(line)
                }
            }
            line.contains("[Hub Probe]") && line.contains("Not connected") -> {
                withStyle(SpanStyle(color = Color(0xFF6B7280))) {
                    append(line)
                }
            }
            line.contains("[Hub Probe]") && line.contains("ONLINE") -> {
                withStyle(SpanStyle(color = StatusGreen)) {
                    append(line)
                }
            }
            line.contains("[Hub State Update]") -> {
                val idx = line.indexOf("[Hub State Update]")
                withStyle(SpanStyle(color = Color(0xFFE5E7EB))) {
                    append(line.substring(0, idx))
                }
                withStyle(SpanStyle(color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)) {
                    append("[Hub State Update]")
                }
                withStyle(SpanStyle(color = Color(0xFFE5E7EB))) {
                    append(line.substring(idx + "[Hub State Update]".length))
                }
            }
            line.contains("[Security]") -> {
                val idx = line.indexOf("[Security]")
                withStyle(SpanStyle(color = Color(0xFFE5E7EB))) {
                    append(line.substring(0, idx))
                }
                withStyle(SpanStyle(color = StatusGreen, fontWeight = FontWeight.Bold)) {
                    append("[Security]")
                }
                withStyle(SpanStyle(color = Color(0xFFE5E7EB))) {
                    append(line.substring(idx + "[Security]".length))
                }
            }
            line.contains("[Hub Monitor]") || line.contains("[Hub Verify]") || line.contains("[Jarvis Bridge]") -> {
                val tag = when {
                    line.contains("[Hub Monitor]") -> "[Hub Monitor]"
                    line.contains("[Hub Verify]") -> "[Hub Verify]"
                    else -> "[Jarvis Bridge]"
                }
                val idx = line.indexOf(tag)
                withStyle(SpanStyle(color = Color(0xFFE5E7EB))) {
                    append(line.substring(0, idx))
                }
                val tagColor = if (line.contains("⚠️")) StatusWarning else StatusGreen
                withStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.Bold)) {
                    append(tag)
                }
                withStyle(SpanStyle(color = Color(0xFFE5E7EB))) {
                    append(line.substring(idx + tag.length))
                }
            }
            line.trimStart().startsWith("[✓]") -> {
                withStyle(SpanStyle(color = StatusGreen, fontWeight = FontWeight.Bold)) {
                    append(" [✓]")
                }
                withStyle(SpanStyle(color = Color(0xFFE5E7EB))) {
                    append(line.trimStart().removePrefix("[✓]"))
                }
            }
            line.startsWith("[agy-hub]") -> {
                withStyle(SpanStyle(color = Color(0xFF6B7280))) {
                    append("[agy-hub]")
                }
                val rest = line.removePrefix("[agy-hub]")
                val restColor = if (rest.contains("➜")) Color(0xFF00E5FF) else Color(0xFFE5E7EB)
                withStyle(SpanStyle(color = restColor)) {
                    append(rest)
                }
            }
            line.startsWith("[agy-cli]") -> {
                withStyle(SpanStyle(color = Color(0xFF3B82F6), fontWeight = FontWeight.SemiBold)) {
                    append("[agy-cli]")
                }
                withStyle(SpanStyle(color = Color(0xFFE5E7EB))) {
                    append(line.removePrefix("[agy-cli]"))
                }
            }
            line.contains("[Hub Error]") || line.contains("[Security Fatal]") -> {
                withStyle(SpanStyle(color = StatusDanger, fontWeight = FontWeight.SemiBold)) {
                    append(line)
                }
            }
            else -> {
                withStyle(SpanStyle(color = Color(0xFFE5E7EB))) {
                    append(line)
                }
            }
        }
    }

    Text(
        text = annotated,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.5.sp,
        lineHeight = 15.5.sp
    )
}

/**
 * Round Antigravity agent mark (`🚀` in a blue circle) with an optional status dot badge at bottom-end.
 */
@Composable
fun AntigravityAgentMark(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    statusColor: Color? = null
) {
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(AgyBlue.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.RocketLaunch,
                contentDescription = null,
                tint = AgyBlue,
                modifier = Modifier.size(size * 0.52f)
            )
        }
        if (statusColor != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.34f)
                    .clip(CircleShape)
                    .background(Color(0xFF171A21))
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
        }
    }
}

/**
 * Jarvis AI `AGENTS` dropdown popup anchored to the top-bar status dot.
 * Shows the Antigravity agent status (`Starting...`, `Ready`, or `Offline` with `Start` pill),
 * user account & plan detail, `Server logs >`, and `Restart server`.
 */
@Composable
fun AgentStatusPopupMenu(
    expanded: Boolean,
    serverStatus: LocalServerStatus,
    userEmail: String = "",
    userTier: String = "",
    agyVersion: String = "",
    onDismiss: () -> Unit,
    onStartOrRestartServer: () -> Unit,
    onOpenServerLogs: () -> Unit
) {
    val isStarting = serverStatus is LocalServerStatus.Starting
    val isStopping = serverStatus is LocalServerStatus.Stopping
    val isRunning = serverStatus is LocalServerStatus.Running || serverStatus is LocalServerStatus.FallbackPTY
    val isOffline = serverStatus is LocalServerStatus.Stopped || serverStatus is LocalServerStatus.Idle
    val isError = serverStatus is LocalServerStatus.Error

    val stateLabel = when {
        isStarting -> "Starting..."
        isStopping -> "Stopping..."
        isRunning -> "Ready"
        isError -> "Error"
        else -> "Offline"
    }

    val stateColor = when {
        isStarting || isStopping -> StatusWarning
        isRunning -> StatusGreen
        isError -> StatusDanger
        else -> StatusMuted
    }

    val detailText = when {
        isStarting -> "The Antigravity hub is starting."
        isStopping -> "Stopping the local server..."
        isRunning -> {
            val tierClean = userTier.ifBlank { "Google AI Pro" }
            val parts = buildList {
                if (userEmail.isNotBlank()) add(userEmail)
                if (tierClean.isNotBlank()) add(tierClean)
                else if (agyVersion.isNotBlank()) add("agy $agyVersion")
            }
            parts.joinToString(" · ").ifBlank { "Antigravity Hub online" }
        }
        isError -> (serverStatus as? LocalServerStatus.Error)?.message ?: "Local server error."
        else -> "The local server is not running."
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = Color(0xFF171B22),
        border = BorderStroke(1.dp, Color(0xFF282E3A)),
        shadowElevation = 10.dp,
        modifier = Modifier
            .width(305.dp)
            .testTag("agent_status_popup_menu")
    ) {
        Text(
            text = "AGENTS",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = Color(0xFF9CA3AF),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AntigravityAgentMark(size = 34.dp, statusColor = stateColor)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Antigravity",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stateLabel,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = stateColor,
                        maxLines = 1
                    )
                }
                Text(
                    text = detailText,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    color = Color(0xFF9CA3AF),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isOffline || isError) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    onClick = {
                        onDismiss()
                        onStartOrRestartServer()
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = AgyBlue.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, AgyBlue.copy(alpha = 0.4f)),
                    modifier = Modifier.testTag("agent_status_start_button")
                ) {
                    Text(
                        text = if (isError) "Retry" else "Start",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AgyBlue,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp),
            color = Color(0xFF252A35)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onDismiss()
                    onOpenServerLogs()
                }
                .padding(horizontal = 16.dp, vertical = 11.dp)
                .testTag("agent_status_server_logs_row"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Terminal,
                contentDescription = null,
                tint = Color(0xFFD1D5DB),
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Server logs",
                fontSize = 13.sp,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF6B7280),
                modifier = Modifier.size(18.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onDismiss()
                    onStartOrRestartServer()
                }
                .padding(horizontal = 16.dp, vertical = 11.dp)
                .testTag("agent_status_restart_server_row"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.RestartAlt,
                contentDescription = null,
                tint = Color(0xFFD1D5DB),
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Restart server",
                fontSize = 13.sp,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Displayed on the empty chat screen while the background Antigravity engine is starting up (`Starting` state).
 */
@Composable
fun EngineWarmingUpView(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFF6366F1).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.SmartToy,
                contentDescription = null,
                tint = Color(0xFF818CF8),
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Waking up Antigravity...",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "The background AI engine is starting up. Your chats and workspace will be ready in a moment.",
            fontSize = 13.5.sp,
            color = Color(0xFF9CA3AF),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

/**
 * Displayed on the empty chat screen when the local server is stopped (`Stopped` state).
 */
@Composable
fun ServerStoppedPromptCard(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.PowerSettingsNew,
                contentDescription = null,
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Antigravity is stopped",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "The local AI engine is currently stopped. Tap the status dot in the top header to start, restart, or stop the server and inspect live logs.",
            fontSize = 13.5.sp,
            color = Color(0xFF9CA3AF),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}
