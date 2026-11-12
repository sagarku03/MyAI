package com.jarvisai.assistant.browser.mcp

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrowserAgentIndicator(
    isAgentActive: Boolean,
    actionSummary: String = "Antigravity automating page...",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "agent_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    AnimatedVisibility(
        visible = isAgentActive,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF070B14).copy(alpha = 0.90f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF)),
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E5FF).copy(alpha = dotAlpha))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = actionSummary,
                    fontSize = 11.5.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun BrowserAgentCursorOverlay(
    isAgentActive: Boolean,
    cursorPosition: Pair<Float, Float>?,
    modifier: Modifier = Modifier
) {
    if (!isAgentActive || cursorPosition == null) return

    val targetX by animateFloatAsState(
        targetValue = cursorPosition.first,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cursor_x"
    )
    val targetY by animateFloatAsState(
        targetValue = cursorPosition.second,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cursor_y"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "cursor_ripple")
    val rippleRadius by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 42f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_radius"
    )
    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_alpha"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(
            x = targetX.coerceIn(0f, size.width),
            y = targetY.coerceIn(0f, size.height)
        )
        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = rippleAlpha),
            radius = rippleRadius,
            center = center,
            style = Stroke(width = 3f)
        )
        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = 0.35f),
            radius = 18f,
            center = center
        )
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = 7f,
            center = center
        )
    }
}
