package com.jarvisai.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.ui.theme.ArcCyanBright
import com.example.ui.theme.IceBlue
import com.example.ui.theme.StatusListeningEmerald
import com.example.ui.theme.StatusSpeakingAzure
import com.example.ui.theme.StatusThinkingAmber
import com.jarvisai.assistant.model.AssistantStatus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Original futuristic animated Arc Reactor HUD core.
 * Reacts dynamically to [AssistantStatus] (Idle, Listening, Thinking, Speaking)
 * and real-time microphone/playback [audioLevel].
 */
@Composable
fun ArcReactorView(
    status: AssistantStatus,
    audioLevel: Float = 0f,
    modifier: Modifier = Modifier
) {
    val primaryThemeColor = MaterialTheme.colorScheme.primary

    val targetCoreColor by animateColorAsState(
        targetValue = when (status) {
            AssistantStatus.IDLE -> primaryThemeColor
            AssistantStatus.LISTENING -> StatusListeningEmerald
            AssistantStatus.THINKING -> StatusThinkingAmber
            AssistantStatus.SPEAKING -> StatusSpeakingAzure
        },
        animationSpec = tween(durationMillis = 400),
        label = "coreColor"
    )

    val smoothedAudio by animateFloatAsState(
        targetValue = audioLevel.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 90),
        label = "smoothedAudio"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "arcReactorInfinite")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (status) {
                    AssistantStatus.THINKING -> 4500
                    AssistantStatus.SPEAKING -> 7000
                    AssistantStatus.LISTENING -> 9000
                    AssistantStatus.IDLE -> 16000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (status) {
                    AssistantStatus.THINKING -> 3200
                    AssistantStatus.SPEAKING -> 5500
                    AssistantStatus.LISTENING -> 7500
                    AssistantStatus.IDLE -> 12000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRotation"
    )

    val breathPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (status) {
                    AssistantStatus.THINKING -> 650
                    AssistantStatus.SPEAKING -> 500
                    AssistantStatus.LISTENING -> 900
                    AssistantStatus.IDLE -> 2200
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathPulse"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = min(size.width, size.height)
            val maxRadius = diameter / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val dynamicBoost = 1f + (smoothedAudio * 0.22f)

            // 1. Ambient Outer Plasma Halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        targetCoreColor.copy(alpha = 0.36f * breathPulse),
                        primaryThemeColor.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius * 0.98f * dynamicBoost
                ),
                radius = maxRadius * 0.98f * dynamicBoost,
                center = center
            )

            // 2. Outer Degree Telemetry Tick Ring
            val tickRingRadius = maxRadius * 0.90f
            for (deg in 0 until 360 step 6) {
                val angleRad = (deg * PI / 180.0).toFloat()
                val isMajor = deg % 30 == 0
                val innerR = if (isMajor) tickRingRadius * 0.91f else tickRingRadius * 0.95f
                val start = Offset(
                    x = center.x + innerR * cos(angleRad),
                    y = center.y + innerR * sin(angleRad)
                )
                val end = Offset(
                    x = center.x + tickRingRadius * cos(angleRad),
                    y = center.y + tickRingRadius * sin(angleRad)
                )
                drawLine(
                    color = if (isMajor) targetCoreColor.copy(alpha = 0.85f)
                    else primaryThemeColor.copy(alpha = 0.35f),
                    start = start,
                    end = end,
                    strokeWidth = if (isMajor) diameter * 0.012f else diameter * 0.005f,
                    cap = StrokeCap.Round
                )
            }

            // 3. Rotating Segmented Outer Containment Ring
            val outerRingRadius = maxRadius * 0.78f
            rotate(degrees = outerRotation, pivot = center) {
                for (i in 0 until 6) {
                    drawArc(
                        color = targetCoreColor.copy(alpha = 0.88f),
                        startAngle = i * 60f + 8f,
                        sweepAngle = 44f,
                        useCenter = false,
                        topLeft = Offset(center.x - outerRingRadius, center.y - outerRingRadius),
                        size = Size(outerRingRadius * 2f, outerRingRadius * 2f),
                        style = Stroke(width = diameter * 0.028f, cap = StrokeCap.Butt)
                    )
                }
            }

            // 4. Radial Reactor Induction Coils (10 segments around reactor ring)
            val coilRadius = maxRadius * 0.65f
            drawCircle(
                color = primaryThemeColor.copy(alpha = 0.25f),
                radius = coilRadius,
                center = center,
                style = Stroke(width = diameter * 0.055f)
            )
            for (coil in 0 until 10) {
                drawArc(
                    color = IceBlue.copy(alpha = 0.92f),
                    startAngle = coil * 36f - 6f,
                    sweepAngle = 12f,
                    useCenter = false,
                    topLeft = Offset(center.x - coilRadius, center.y - coilRadius),
                    size = Size(coilRadius * 2f, coilRadius * 2f),
                    style = Stroke(width = diameter * 0.068f, cap = StrokeCap.Butt)
                )
            }

            // 5. Counter-Rotating Harmonic Telemetry Ring
            val midRingRadius = maxRadius * 0.50f
            rotate(degrees = innerRotation, pivot = center) {
                for (j in 0 until 4) {
                    drawArc(
                        color = ArcCyanBright.copy(alpha = 0.8f),
                        startAngle = j * 90f + 12f,
                        sweepAngle = 66f,
                        useCenter = false,
                        topLeft = Offset(center.x - midRingRadius, center.y - midRingRadius),
                        size = Size(midRingRadius * 2f, midRingRadius * 2f),
                        style = Stroke(width = diameter * 0.018f, cap = StrokeCap.Round)
                    )
                }

                // Inner Geometric Hex Lattice
                val hexPath = Path()
                val hexR = maxRadius * 0.38f
                for (k in 0..6) {
                    val a = (k * 60.0 * PI / 180.0).toFloat()
                    val px = center.x + hexR * cos(a)
                    val py = center.y + hexR * sin(a)
                    if (k == 0) hexPath.moveTo(px, py) else hexPath.lineTo(px, py)
                }
                drawPath(
                    path = hexPath,
                    color = targetCoreColor.copy(alpha = 0.65f),
                    style = Stroke(width = diameter * 0.01f)
                )
            }

            // 6. Central Crystalline Energy Chamber
            val coreRadius = maxRadius * 0.26f * breathPulse * dynamicBoost
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        IceBlue,
                        targetCoreColor,
                        targetCoreColor.copy(alpha = 0.1f)
                    ),
                    center = center,
                    radius = coreRadius * 1.35f
                ),
                radius = coreRadius,
                center = center
            )

            // Crisp Inner Core Ring
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = coreRadius * 0.55f,
                center = center,
                style = Stroke(width = diameter * 0.012f)
            )
        }
    }
}
