package com.jarvisai.assistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Floating Unified Command Bar.
 * Unites [ + Tools ], Multi-line auto-expanding text input, [ 🎙️ Mic ] with cyan pulse, and [ ➤ Send ].
 */
@Composable
fun JarvisUnifiedCommandBar(
    promptText: String,
    onPromptChange: (String) -> Unit,
    isLiveVoiceActive: Boolean,
    isMicMuted: Boolean,
    audioLevel: Float = 0f,
    hasAttachments: Boolean = false,
    onSendClick: () -> Unit,
    onMicClick: () -> Unit,
    onToolsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "MicPulse")
    val micPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Alpha"
    )

    val dynamicWaveBoost = (audioLevel.coerceIn(0f, 1f) * 0.35f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("jarvis_unified_command_bar"),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF111827),
        border = BorderStroke(
            width = 1.dp,
            color = if (isLiveVoiceActive) Color(0xFF00E5FF).copy(alpha = 0.6f)
            else Color(0xFF00E5FF).copy(alpha = 0.25f)
        ),
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: [ + ] Photos, Videos & Files Attachment Trigger
            IconButton(
                onClick = onToolsClick,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("command_bar_tools_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Attach photos, videos, or files",
                    tint = Color(0xFF00E5FF)
                )
            }

            // Center: Multi-line Auto-Expanding Input Field
            OutlinedTextField(
                value = promptText,
                onValueChange = onPromptChange,
                placeholder = {
                    Text(
                        text = if (isLiveVoiceActive) {
                            if (isMicMuted) "Live Voice Muted • Type or unmute..."
                            else "Listening to speech... (▁▂▄▆▄▂▁)"
                        } else {
                            "Message Jarvis or type /command..."
                        },
                        fontSize = 13.sp,
                        color = if (isLiveVoiceActive) Color(0xFF00E5FF).copy(alpha = 0.8f) else Color(0xFF64748B)
                    )
                },
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .testTag("command_bar_text_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF00E5FF)
                )
            )

            // Right 1: [ 🎙️ ] Live Voice Mic Button with Pulsing Cyan Aura
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isLiveVoiceActive) {
                            Color(0xFF00E5FF).copy(alpha = (micPulseAlpha * 0.28f + dynamicWaveBoost).coerceAtMost(0.65f))
                        } else {
                            Color.Transparent
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("command_bar_mic_button")
                ) {
                    Icon(
                        imageVector = if (isLiveVoiceActive) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isLiveVoiceActive) "Stop Live Voice" else "Start Live Voice",
                        tint = if (isLiveVoiceActive) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                    )
                }
            }

            // Right 2: [ ➤ ] Send Action Button
            val canSend = promptText.isNotBlank() || hasAttachments
            IconButton(
                onClick = onSendClick,
                enabled = canSend,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("command_bar_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Prompt",
                    tint = if (canSend) Color(0xFF00E5FF) else Color(0xFF475569)
                )
            }
        }
    }
}
