package com.jarvisai.assistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
    var isExpanded by remember { mutableStateOf(isStreaming) }
    var hasUserManuallyToggled by remember { mutableStateOf(false) }

    LaunchedEffect(isStreaming) {
        if (isStreaming && !hasUserManuallyToggled) {
            isExpanded = true
        } else if (!isStreaming && !hasUserManuallyToggled) {
            isExpanded = false
        }
    }

    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "thought_chevron_rotation"
    )

    val previewText = remember(thoughtText) {
        thoughtText.trim()
            .lines()
            .map { it.trim().removePrefix("#").removePrefix("*").removePrefix("-").trim() }
            .firstOrNull { it.isNotBlank() } ?: "DeepMind Reasoning"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    hasUserManuallyToggled = true
                    isExpanded = !isExpanded
                }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = Color(0xFF64748B),
                modifier = Modifier
                    .size(16.dp)
                    .rotate(rotation)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Outlined.Psychology,
                contentDescription = "Thinking",
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            if (isStreaming) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E5FF))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Thinking...",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF00E5FF),
                    fontFamily = FontFamily.Monospace
                )
            } else {
                val durationLabel = if (durationMs != null && durationMs > 0) "Thought for ${durationMs / 1000}s" else "Thought process"
                Text(
                    text = durationLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8),
                    fontFamily = FontFamily.Monospace
                )
            }

            if (!isExpanded && previewText.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "— $previewText",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, top = 4.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A0F1D))
                    .padding(10.dp)
            ) {
                Text(
                    text = thoughtText,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFFCBD5E1),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
