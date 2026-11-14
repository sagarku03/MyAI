package com.jarvisai.assistant.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.model.ToolCall
import com.jarvisai.assistant.model.ToolCallStatus

@Composable
fun AgentToolCallCard(
    toolCall: ToolCall,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(toolCall.status == ToolCallStatus.RUNNING) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val isRunning = toolCall.status == ToolCallStatus.RUNNING
    val isFailed = toolCall.status == ToolCallStatus.FAILED

    LaunchedEffect(isRunning) {
        if (isRunning) {
            isExpanded = true
        }
    }

    val statusColor = when {
        isRunning -> Color(0xFF00E5FF) // Arc Cyan
        isFailed -> Color(0xFFEF4444) // Rose Red
        else -> Color(0xFF10B981) // Emerald Green
    }

    val infiniteTransition = rememberInfiniteTransition(label = "tool_pulse")
    val runningAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tool_pulse_alpha"
    )

    val normalizedTool = toolCall.name.lowercase().trim()
    val actionLabel = when (normalizedTool) {
        "bash", "run_command", "shell", "terminal_exec" -> if (isRunning) "Running command" else "Ran command"
        "command_status" -> if (isRunning) "Checking command" else "Command status"
        "read_file", "view_file", "view_file_outline", "view_code_item" -> if (isRunning) "Reading file" else "Read file"
        "write_to_file", "write_file" -> if (isRunning) "Writing file" else "Wrote file"
        "edit_file", "replace_file_content", "multi_replace_file_content", "code_action" -> if (isRunning) "Editing file" else "Edited file"
        "list_dir", "list_directory", "find_by_name" -> if (isRunning) "Listing files" else "Listed files"
        "grep_search", "search" -> if (isRunning) "Searching code" else "Searched code"
        "web_search", "read_url_content" -> if (isRunning) "Searching web" else "Searched web"
        "task_boundary", "checkpoint" -> "Task step"
        else -> normalizedTool.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(180),
        label = "tool_chevron"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0C1322),
        border = BorderStroke(
            width = if (isRunning) 1.dp else 0.8.dp,
            color = if (isRunning) statusColor.copy(alpha = 0.65f) else Color(0xFF1E293B)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Collapse step" else "Expand step",
                    tint = Color(0xFF64748B),
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(rotation)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = if (isRunning) runningAlpha else 1f))
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = actionLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isRunning) Color(0xFF00E5FF) else Color.White,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = toolCall.command.ifBlank { toolCall.args },
                    fontSize = 11.5.sp,
                    color = Color(0xFF94A3B8),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (toolCall.durationMs > 0L) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (toolCall.durationMs >= 1000L) {
                            String.format(java.util.Locale.US, "%.1fs", toolCall.durationMs / 1000.0)
                        } else {
                            "${toolCall.durationMs}ms"
                        },
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (toolCall.exitCode != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (toolCall.exitCode == 0) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "exit ${toolCall.exitCode}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (toolCall.exitCode == 0) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded || isRunning,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF060A12),
                        border = BorderStroke(0.5.dp, Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isRunning) "LIVE TERMINAL / AGENT OUTPUT" else "OUTPUT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRunning) Color(0xFF00E5FF) else Color(0xFF64748B),
                                    fontFamily = FontFamily.Monospace
                                )
                                if (toolCall.output.isNotBlank() || toolCall.command.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            val clipContent = buildString {
                                                if (toolCall.command.isNotBlank()) appendLine("$ ${toolCall.command}")
                                                if (toolCall.output.isNotBlank()) append(toolCall.output)
                                            }.trim()
                                            clipboardManager.setText(AnnotatedString(clipContent))
                                            Toast.makeText(context, "Copied step output", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy step output",
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }

                            if (toolCall.command.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "$ ${toolCall.command}",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = if (isExpanded) 6 else 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = toolCall.output.ifBlank { if (isRunning) "Executing live on device..." else "(no output)" },
                                color = when {
                                    isFailed -> Color(0xFFFCA5A5)
                                    toolCall.output.contains("\n+") || toolCall.output.startsWith("+") -> Color(0xFF6EE7B7)
                                    else -> Color(0xFF38BDF8)
                                },
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = if (isExpanded) 50 else 8,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
