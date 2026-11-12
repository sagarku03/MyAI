package com.jarvisai.assistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Multi-ring futuristic radar pulse & particle spinner (`AgentWorkingSpinner`)
 * indicating active autonomous agent tool execution.
 */
@Composable
fun AgentWorkingSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    label: String? = null,
    primaryColor: Color = Color(0xFF38BDF8),
    secondaryColor: Color = Color(0xFF6366F1),
    accentColor: Color = Color(0xFF10B981)
) {
    val transition = rememberInfiniteTransition(label = "agent_working_spinner")

    val outerAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_ring_rotation"
    )

    val innerAngle by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_ring_rotation"
    )

    val radarPulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_radar_pulse"
    )

    val spinnerCanvas: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(size)
                .testTag("agent_working_spinner_canvas"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(size)) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val maxRadius = this.size.minDimension / 2f
                val strokeWidth = (maxRadius * 0.14f).coerceAtLeast(2f)

                // Subtle radial aura
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.28f * radarPulse),
                            secondaryColor.copy(alpha = 0.10f * radarPulse),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxRadius
                    ),
                    radius = maxRadius,
                    center = center
                )

                // Outer segmented radar arc
                val outerRadius = maxRadius * 0.86f
                drawArc(
                    color = primaryColor,
                    startAngle = outerAngle,
                    sweepAngle = 210f,
                    useCenter = false,
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                    size = Size(outerRadius * 2f, outerRadius * 2f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Counter-rotating inner indigo arc
                val innerRadius = maxRadius * 0.56f
                drawArc(
                    color = secondaryColor,
                    startAngle = innerAngle,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                    size = Size(innerRadius * 2f, innerRadius * 2f),
                    style = Stroke(width = strokeWidth * 0.85f, cap = StrokeCap.Round)
                )

                // Orbiting particle nodes
                for (i in 0 until 3) {
                    val rad = Math.toRadians((outerAngle + i * 120f).toDouble())
                    val px = center.x + (outerRadius * cos(rad)).toFloat()
                    val py = center.y + (outerRadius * sin(rad)).toFloat()
                    drawCircle(
                        color = accentColor,
                        radius = strokeWidth * 0.7f,
                        center = Offset(px, py)
                    )
                }

                // Pulsing quantum core dot
                drawCircle(
                    color = primaryColor.copy(alpha = radarPulse),
                    radius = maxRadius * 0.22f * radarPulse,
                    center = center
                )
            }
        }
    }

    if (label.isNullOrBlank()) {
        Box(modifier = modifier) {
            spinnerCanvas()
        }
    } else {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFF111827).copy(alpha = 0.9f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                spinnerCanvas()
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    color = primaryColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
