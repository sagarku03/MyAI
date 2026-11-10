package com.jarvisai.assistant.ui.claude

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

data class ClaudePendingCommand(
    val id: String,
    val command: String,
    val workingDir: String = "/data/data/com.termux/files/home",
    val riskLevel: String = "MEDIUM" // LOW, MEDIUM, HIGH
)

/**
 * Interactive Approval Sheet for Claude Code CLI and Subagents.
 * Intercepts destructive/system terminal executions and requests user consent.
 */
@Composable
fun ClaudePermissionPanel(
    pendingCommand: ClaudePendingCommand?,
    onApprove: (commandId: String, autoApproveSession: Boolean) -> Unit,
    onReject: (commandId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pendingCommand == null) return

    var autoApproveForSession by remember { mutableStateOf(false) }

    val riskColor = when (pendingCommand.riskLevel.uppercase()) {
        "HIGH" -> Color(0xFFEF4444) // Red
        "LOW" -> Color(0xFF10B981) // Green
        else -> Color(0xFFF59E0B) // Amber
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("claude_permission_panel"),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF111827),
        border = BorderStroke(1.dp, riskColor.copy(alpha = 0.6f)),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = riskColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Agent Permission Request",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = riskColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "${pendingCommand.riskLevel} RISK",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = riskColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Claude Code wants to execute the following terminal command:",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Command Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0A0E17),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = pendingCommand.workingDir,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$ ${pendingCommand.command}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF38BDF8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Session Auto-Approve Checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = autoApproveForSession,
                    onCheckedChange = { autoApproveForSession = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF00E5FF),
                        checkmarkColor = Color(0xFF0A0E17),
                        uncheckedColor = Color(0xFF475569)
                    )
                )
                Text(
                    text = "Always allow bash commands for this session",
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onReject(pendingCommand.id) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reject", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onApprove(pendingCommand.id, autoApproveForSession) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color(0xFF0A0E17), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Allow Once", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0A0E17))
                }
            }
        }
    }
}
