package com.jarvisai.assistant.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jarvisai.assistant.terminal.LocalServerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 4: Real-time LSP, Go IDE Bridge & Antigravity Hub Daemon Log Streamer (`DaemonLogsDialog`).
 * Supports auto-scroll, error/warning syntax coloring, status header, and log buffer clearing.
 */
object IdeDaemonLogBuffer {
    private const val MAX_LINES = 500
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _logs = MutableStateFlow<List<String>>(
        listOf("[${timeFormat.format(Date())}] [INFO] Jarvis Mobile IDE Daemon & LSP Log Buffer initialized.")
    )
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun append(level: String, message: String) {
        val stamp = timeFormat.format(Date())
        val formatted = "[$stamp] [$level] $message"
        val updated = (_logs.value + formatted).takeLast(MAX_LINES)
        _logs.value = updated
    }

    fun clear() {
        _logs.value = emptyList()
    }
}

@Composable
fun DaemonLogsDialog(
    onDismiss: () -> Unit
) {
    val logs by IdeDaemonLogBuffer.logs.collectAsState()
    val serverStatus by LocalServerManager.status.collectAsState()
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size, autoScroll) {
        if (autoScroll && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.80f)
                .border(1.dp, Color(0xFF1E3A5F), RoundedCornerShape(16.dp))
                .testTag("daemon_logs_dialog"),
            color = Color(0xFF070B14),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111C35))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "IDE DAEMON & LSP STREAM LOGS",
                            color = Color(0xFF00E5FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Server state: ${serverStatus::class.simpleName ?: "Active"} • ${logs.size} lines",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Checkbox(
                                checked = autoScroll,
                                onCheckedChange = { autoScroll = it }
                            )
                            Text("Auto-scroll", color = Color(0xFFE2E8F0), fontSize = 11.sp)
                        }

                        IconButton(
                            onClick = { IdeDaemonLogBuffer.clear() },
                            modifier = Modifier.testTag("daemon_logs_clear_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear Logs", tint = Color(0xFFFCD34D))
                        }

                        Spacer(modifier = Modifier.width(2.dp))

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("daemon_logs_close_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Logs", tint = Color(0xFF94A3B8))
                        }
                    }
                }

                // Log viewport
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(logs) { _, line ->
                        val lineColor = when {
                            line.contains("[ERROR]", ignoreCase = true) || line.contains("Exception", ignoreCase = true) -> Color(0xFFF87171)
                            line.contains("[WARN]", ignoreCase = true) -> Color(0xFFFCD34D)
                            line.contains("[INFO]", ignoreCase = true) -> Color(0xFF38BDF8)
                            else -> Color(0xFFE2E8F0)
                        }
                        Text(
                            text = line,
                            color = lineColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
