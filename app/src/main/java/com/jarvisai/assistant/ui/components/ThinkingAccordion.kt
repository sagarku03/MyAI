package com.jarvisai.assistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ThinkingAccordion(
    thoughtText: String,
    durationMs: Long? = null,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(isStreaming && thoughtText.isNotBlank()) }
    var userManuallyToggled by remember { mutableStateOf(false) }

    LaunchedEffect(isStreaming, thoughtText.isNotBlank()) {
        if (isStreaming && thoughtText.isNotBlank() && !userManuallyToggled) {
            isExpanded = true
        } else if (!isStreaming) {
            isExpanded = false
            userManuallyToggled = false
        }
    }

    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "thought_chevron_rotation"
    )

    val pulseAlpha = if (isStreaming) {
        val transition = rememberInfiniteTransition(label = "thought_pulse")
        val anim by transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(750, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "thought_pulse_alpha"
        )
        anim
    } else {
        1f
    }

    val previewText = remember(thoughtText) {
        thoughtText.trim()
            .lines()
            .map { line ->
                line.trim()
                    .trimStart('#', '*', '-', '_', '>', ' ')
                    .replace("**", "")
                    .replace("__", "")
                    .trimEnd('*', '_', ' ')
                    .trim()
            }
            .firstOrNull { it.isNotBlank() } ?: ""
    }

    val durationLabel = remember(durationMs, isStreaming) {
        if (isStreaming) {
            "Thinking..."
        } else {
            formatAntiGemThoughtDuration(durationMs)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    userManuallyToggled = true
                    isExpanded = !isExpanded
                }
                .padding(horizontal = 2.dp, vertical = 5.dp)
                .testTag("thinking_accordion_row"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse thought" else "Expand thought",
                tint = Color(0xFF6B7280),
                modifier = Modifier
                    .size(14.dp)
                    .rotate(rotation)
            )

            Spacer(modifier = Modifier.width(5.dp))

            Icon(
                imageVector = Icons.Outlined.Psychology,
                contentDescription = "Thinking",
                tint = Color(0xFFA78BFA).copy(alpha = pulseAlpha),
                modifier = Modifier.size(15.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = durationLabel,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isStreaming) Color(0xFFC4B5FD) else Color(0xFFE5E7EB),
                fontFamily = FontFamily.SansSerif,
                maxLines = 1
            )

            if (!isExpanded && previewText.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = previewText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.SansSerif,
                    color = Color(0xFF6B7280),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 3.dp, bottom = 6.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0D1117),
                border = BorderStroke(0.6.dp, Color(0xFF1F2937))
            ) {
                Text(
                    text = thoughtText.trim(),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFF9CA3AF),
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}

private fun formatAntiGemThoughtDuration(durationMs: Long?): String {
    if (durationMs == null || durationMs <= 0L) return "Thought"
    val totalSec = durationMs / 1000L
    return when {
        totalSec >= 60L -> {
            val mins = totalSec / 60L
            val secs = totalSec % 60L
            if (secs > 0L) "Thought for ${mins}m ${secs}s" else "Thought for ${mins}m"
        }
        totalSec >= 1L -> "Thought for ${totalSec}s"
        else -> "Thought for <1s"
    }
}
