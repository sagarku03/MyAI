package com.jarvisai.assistant.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Jarvis Cyber HUD Real-Time Agent Execution Card.
 * Displays live progress steps, active tool badges, and collapsible terminal logs with clipboard copy.
 */
@Composable
fun AgentExecutionCard(
    agentName: String,
    command: String,
    output: String,
    status: String = "SUCCESS",
    stepInfo: String = "Step 1/1",
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val isRunning = status.equals("RUNNING", ignoreCase = true)
    val isWaiting = status.contains("APPROVAL", ignoreCase = true)

    val statusColor = when {
        isRunning -> Color(0xFF00E5FF) // Arc Cyan
        isWaiting -> Color(0xFFF59E0B) // Amber
        status.equals("SUCCESS", ignoreCase = true) || status.equals("COMPLETED", ignoreCase = true) -> Color(0xFF10B981) // Emerald Green
        else -> Color(0xFFEF4444) // Rose Red
    }

    // Breathing pulse animation when running
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("agent_execution_card"),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF111827),
        border = BorderStroke(1.dp, if (isRunning) statusColor.copy(alpha = pulseAlpha) else Color(0xFF1F2937))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Expandable trigger
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) statusColor.copy(alpha = pulseAlpha) else statusColor)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = agentName,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 12.5.sp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = status.uppercase(),
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stepInfo,
                        fontSize = 11.5.sp,
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Command / Prompt summary line
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = command,
                color = Color(0xFFE2E8F0),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = if (isExpanded) 10 else 2
            )

            // Collapsible Output Logs
            AnimatedVisibility(visible = isExpanded || isRunning) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF070B14),
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
                                    text = "LIVE OUTPUT LOG",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    fontFamily = FontFamily.Monospace
                                )
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(output))
                                        Toast.makeText(context, "Copied log to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = output.ifBlank { "Awaiting command execution output..." },
                                color = if (status.contains("FAIL", ignoreCase = true)) Color(0xFFFCA5A5) else Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = if (isExpanded) 50 else 6
                            )
                        }
                    }
                }
            }
        }
    }
}
