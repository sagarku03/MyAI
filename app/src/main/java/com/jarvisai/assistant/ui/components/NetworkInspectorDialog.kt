package com.jarvisai.assistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jarvisai.assistant.data.remote.inspector.NetworkInspectorManager
import com.jarvisai.assistant.data.remote.inspector.NetworkLogEntry

@Composable
fun NetworkInspectorDialog(
    onDismiss: () -> Unit
) {
    val logs by NetworkInspectorManager.logs.collectAsState()
    val enabled by NetworkInspectorManager.enabledFlow.collectAsState()
    val activeCalls by NetworkInspectorManager.activeCallsCount.collectAsState()
    var search by remember { mutableStateOf("") }
    var selectedEntry by remember { mutableStateOf<NetworkLogEntry?>(null) }

    val filteredLogs = remember(logs, search) {
        if (search.isBlank()) logs
        else logs.filter {
            it.url.contains(search, ignoreCase = true) ||
                it.method.contains(search, ignoreCase = true) ||
                it.serviceType.contains(search, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0A0F1A),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = "Network Inspector",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Network Inspector",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${logs.size} captured · $activeCalls active",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = enabled,
                            onCheckedChange = { NetworkInspectorManager.setCaptureEnabled(it) },
                            modifier = Modifier.testTag("network_inspector_toggle")
                        )
                        IconButton(
                            onClick = { NetworkInspectorManager.clearLogs() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("network_inspector_clear_btn")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs", tint = Color(0xFF94A3B8))
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("network_inspector_close_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                        }
                    }
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Filter URL, method, service...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("network_inspector_search_input")
                )

                if (filteredLogs.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (enabled) "Listening for Hub/API traffic..." else "Enable the switch above to record traffic",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .bouncyOverscroll(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { entry ->
                            val statusColor = when {
                                entry.error != null -> Color(0xFFEF4444)
                                entry.isConnected -> Color(0xFF38BDF8)
                                entry.isSuccess -> Color(0xFF10B981)
                                else -> Color(0xFFF59E0B)
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF111827),
                                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable {
                                        selectedEntry = if (selectedEntry?.id == entry.id) null else entry
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${entry.method} ${entry.displayName}",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = when {
                                                entry.error != null -> "ERR"
                                                entry.isConnected -> "OPEN"
                                                else -> "${entry.statusCode} · ${entry.durationMs}ms"
                                            },
                                            color = statusColor,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "${entry.formattedTime} · ${entry.serviceType} · ${entry.url}",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (selectedEntry?.id == entry.id) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = Color(0xFF1E293B)
                                        )
                                        Text(
                                            text = "URL: ${entry.url}",
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 10.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        if (entry.error != null) {
                                            Text(
                                                text = "Error: ${entry.error}",
                                                color = Color(0xFFEF4444),
                                                fontSize = 10.5.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        if (entry.requestHeaders.isNotEmpty()) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = "Req Headers: ${entry.requestHeaders.entries.joinToString { "${it.key}=${it.value}" }}",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        if (entry.responseHeaders.isNotEmpty()) {
                                            Text(
                                                text = "Res Headers: ${entry.responseHeaders.entries.joinToString { "${it.key}=${it.value}" }}",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
