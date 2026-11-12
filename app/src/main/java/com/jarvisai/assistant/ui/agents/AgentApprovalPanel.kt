package com.jarvisai.assistant.ui.agents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.jarvisai.assistant.agent.AgentApprovalRequest

/**
 * Phase 3: Polymorphic tool execution approval card supporting AGY, CLAUDE, OPENCODE, and CODEX.
 */
@Composable
fun AgentApprovalPanel(
    request: AgentApprovalRequest,
    onApprove: (String, String?) -> Unit,
    onReject: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var feedback by remember(request.requestId) { mutableStateOf("") }
    val riskColor = when (request.riskLevel.uppercase()) {
        "HIGH" -> Color(0xFFF85149)
        "LOW" -> Color(0xFF3FB950)
        else -> Color(0xFFD29922)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, riskColor.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .testTag("agent_approval_panel_${request.requestId}"),
        color = Color(0xFF161B22),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${request.agentKind.displayName.uppercase()} • TOOL AUTHORIZATION",
                    color = Color(request.agentKind.badgeColorHex),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "RISK: ${request.riskLevel.uppercase()}",
                    color = riskColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = request.toolName,
                color = Color(0xFFF0F6FC),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (request.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = request.description,
                    color = Color(0xFF8B949E),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1117), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF30363D), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = request.commandOrInput,
                    color = Color(0xFFE6EDF3),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                if (request.workingDirectory.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "cwd: ${request.workingDirectory}",
                        color = Color(0xFF8B949E),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = feedback,
                onValueChange = { feedback = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("agent_approval_feedback_input"),
                placeholder = { Text("Optional instruction or denial reason...", fontSize = 12.sp) },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onReject(request.requestId, feedback.takeIf { it.isNotBlank() }) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("agent_reject_button")
                ) {
                    Text("Deny", color = Color(0xFFF85149), fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onApprove(request.requestId, feedback.takeIf { it.isNotBlank() }) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("agent_approve_button")
                ) {
                    Text("Approve & Run", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
