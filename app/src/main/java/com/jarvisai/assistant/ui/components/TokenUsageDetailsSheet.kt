package com.jarvisai.assistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.model.TokenUsage
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenUsageDetailsSheet(
    usage: TokenUsage,
    onInspectRawPayload: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Turn Token & Latency Telemetry",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0A0E17),
                border = BorderStroke(1.dp, Color(0xFF1F2937)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryRow("Model", usage.model?.replace("MODEL_PLACEHOLDER_", "") ?: "gemini-3.8-flash", Color(0xFF00E5FF))
                    TelemetryRow("Prompt / Input Tokens", usage.promptTokens.toString(), Color(0xFF38BDF8))
                    TelemetryRow("Output / Completion Tokens", usage.outputTokens.toString(), Color(0xFF10B981))
                    TelemetryRow("Cached Context Tokens", usage.cachedTokens.toString(), Color(0xFFF59E0B))
                    TelemetryRow("Total Tokens", usage.totalTokens.toString(), Color.White)
                    if (usage.durationMs > 0) {
                        TelemetryRow(
                            "Execution Latency",
                            String.format(Locale.US, "%.2f s (%d ms)", usage.durationMs / 1000.0, usage.durationMs),
                            Color(0xFF94A3B8)
                        )
                    }
                }
            }

            if (onInspectRawPayload != null) {
                Button(
                    onClick = {
                        onDismiss()
                        onInspectRawPayload()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00FFCC).copy(alpha = 0.18f),
                        contentColor = Color(0xFF00FFCC)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF00FFCC).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.DataObject, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inspect Raw Request / RPC Payload", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.5.sp, color = Color(0xFF94A3B8))
        Text(
            text = value,
            fontSize = 12.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
