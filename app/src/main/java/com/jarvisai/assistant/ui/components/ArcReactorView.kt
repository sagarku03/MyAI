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
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisNeonCyan
import com.example.ui.theme.JarvisNeonGreen
import com.example.ui.theme.JarvisViolet
import com.example.ui.theme.JarvisVioletBright
import com.example.ui.theme.StatusListeningEmerald
import com.example.ui.theme.StatusSpeakingAzure
import com.example.ui.theme.StatusThinkingAmber
import com.jarvisai.assistant.model.AssistantStatus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Reconstructed Premium Futuristic AI Orb & Neural Energy Core.
 * Features a multi-layered purple-blue-cyan plasma sphere, orbital light filaments,
 * satellite energy nodes, smooth breathing motion, and real-time audio reactivity.
 */
@Composable
fun JarvisAIOrb(
    status: AssistantStatus,
    audioLevel: Float = 0f,
    modifier: Modifier = Modifier
) {
    val primaryOrbColor by animateColorAsState(
        targetValue = when (status) {
            AssistantStatus.IDLE -> JarvisNeonCyan
            AssistantStatus.LISTENING -> JarvisNeonGreen
            AssistantStatus.THINKING -> JarvisVioletBright
            AssistantStatus.SPEAKING -> JarvisElectricBlue
        },
        animationSpec = tween(durationMillis = 450),
        label = "primaryOrbColor"
    )

    val secondaryOrbColor by animateColorAsState(
        targetValue = when (status) {
            AssistantStatus.IDLE -> JarvisViolet
            AssistantStatus.LISTENING -> StatusListeningEmerald
            AssistantStatus.THINKING -> StatusThinkingAmber
            AssistantStatus.SPEAKING -> JarvisNeonCyan
        },
        animationSpec = tween(durationMillis = 450),
        label = "secondaryOrbColor"
    )

    val tertiaryOrbColor by animateColorAsState(
        targetValue = when (status) {
            AssistantStatus.IDLE -> JarvisElectricBlue
            AssistantStatus.LISTENING -> JarvisNeonCyan
            AssistantStatus.THINKING -> JarvisElectricBlue
            AssistantStatus.SPEAKING -> JarvisViolet
        },
        animationSpec = tween(durationMillis = 450),
        label = "tertiaryOrbColor"
    )

    val smoothedAudio by animateFloatAsState(
        targetValue = audioLevel.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 80),
        label = "smoothedAudio"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "jarvisAiOrbInfinite")

    val primaryOrbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (status) {
                    AssistantStatus.THINKING -> 4200
                    AssistantStatus.SPEAKING -> 6000
                    AssistantStatus.LISTENING -> 8500
                    AssistantStatus.IDLE -> 15000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "primaryOrbitAngle"
    )

    val counterOrbitAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (status) {
                    AssistantStatus.THINKING -> 3000
                    AssistantStatus.SPEAKING -> 4600
                    AssistantStatus.LISTENING -> 6800
                    AssistantStatus.IDLE -> 11500
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "counterOrbitAngle"
    )

    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (status) {
                    AssistantStatus.THINKING -> 700
                    AssistantStatus.SPEAKING -> 550
                    AssistantStatus.LISTENING -> 950
                    AssistantStatus.IDLE -> 2400
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = min(size.width, size.height)
            val maxRadius = diameter / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val audioBoost = 1f + (smoothedAudio * 0.28f)

            // 1. Deep Atmospheric Outer Nebula Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryOrbColor.copy(alpha = 0.28f * breathScale),
                        secondaryOrbColor.copy(alpha = 0.16f),
                        tertiaryOrbColor.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius * 0.98f * audioBoost
                ),
                radius = maxRadius * 0.98f * audioBoost,
                center = center
            )

            // 2. Whisper-Thin Outer Glass Halo Ring
            val outerHaloRadius = maxRadius * 0.88f
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        primaryOrbColor.copy(alpha = 0.45f),
                        secondaryOrbColor.copy(alpha = 0.12f),
                        tertiaryOrbColor.copy(alpha = 0.55f),
                        secondaryOrbColor.copy(alpha = 0.12f),
                        primaryOrbColor.copy(alpha = 0.45f)
                    ),
                    center = center
                ),
                radius = outerHaloRadius,
                center = center,
                style = Stroke(width = diameter * 0.008f)
            )

            // 3. Rotating Gradient Orbital Arcs (Outer Filament)
            val outerArcRadius = maxRadius * 0.78f
            rotate(degrees = primaryOrbitAngle, pivot = center) {
                val sweepBrush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        primaryOrbColor.copy(alpha = 0.85f),
                        secondaryOrbColor.copy(alpha = 0.95f),
                        Color.Transparent,
                        tertiaryOrbColor.copy(alpha = 0.80f),
                        Color.Transparent
                    ),
                    center = center
                )
                for (i in 0 until 3) {
                    drawArc(
                        brush = sweepBrush,
                        startAngle = i * 120f + 12f,
                        sweepAngle = 82f,
                        useCenter = false,
                        topLeft = Offset(center.x - outerArcRadius, center.y - outerArcRadius),
                        size = Size(outerArcRadius * 2f, outerArcRadius * 2f),
                        style = Stroke(width = diameter * 0.022f, cap = StrokeCap.Round)
                    )
                }

                // Glowing Orbital Satellite Nodes
                for (nodeIndex in 0 until 3) {
                    val angleRad = ((nodeIndex * 120f + 94f) * PI / 180.0).toFloat()
                    val nodeOffset = Offset(
                        x = center.x + outerArcRadius * cos(angleRad),
                        y = center.y + outerArcRadius * sin(angleRad)
                    )
                    drawCircle(
                        color = primaryOrbColor.copy(alpha = 0.4f),
                        radius = diameter * 0.028f,
                        center = nodeOffset
                    )
                    drawCircle(
                        color = Color.White,
                        radius = diameter * 0.011f,
                        center = nodeOffset
                    )
                }
            }

            // 4. Counter-Rotating Harmonic Ribbon Ring
            val midRingRadius = maxRadius * 0.63f * (0.96f + smoothedAudio * 0.08f)
            rotate(degrees = counterOrbitAngle, pivot = center) {
                for (j in 0 until 4) {
                    drawArc(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                secondaryOrbColor.copy(alpha = 0.85f),
                                primaryOrbColor.copy(alpha = 0.75f),
                                tertiaryOrbColor.copy(alpha = 0.25f)
                            )
                        ),
                        startAngle = j * 90f + 15f,
                        sweepAngle = 56f,
                        useCenter = false,
                        topLeft = Offset(center.x - midRingRadius, center.y - midRingRadius),
                        size = Size(midRingRadius * 2f, midRingRadius * 2f),
                        style = Stroke(width = diameter * 0.016f, cap = StrokeCap.Round)
                    )
                }
            }

            // 5. Audio-Reactive Radial Pulse Dots (24 harmonic nodes)
            val dotRingRadius = maxRadius * 0.51f
            val numDots = 24
            for (d in 0 until numDots) {
                val angle = (d * (360f / numDots) * PI / 180.0).toFloat()
                val harmonicWave = sin(wavePhase + d * 0.55f) * 0.5f + 0.5f
                val radialOffset = dotRingRadius * (1f + (smoothedAudio * 0.14f * harmonicWave))
                val pos = Offset(
                    x = center.x + radialOffset * cos(angle),
                    y = center.y + radialOffset * sin(angle)
                )
                val dotAlpha = 0.35f + 0.55f * harmonicWave
                drawCircle(
                    color = if (d % 2 == 0) primaryOrbColor.copy(alpha = dotAlpha)
                    else secondaryOrbColor.copy(alpha = dotAlpha),
                    radius = diameter * (0.007f + smoothedAudio * 0.006f * harmonicWave),
                    center = pos
                )
            }

            // 6. Central 3D Luminous Plasma Sphere
            val sphereRadius = maxRadius * 0.36f * breathScale * audioBoost

            // Outer plasma aura around sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryOrbColor.copy(alpha = 0.65f),
                        secondaryOrbColor.copy(alpha = 0.42f),
                        tertiaryOrbColor.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = sphereRadius * 1.45f
                ),
                radius = sphereRadius * 1.45f,
                center = center
            )

            // Main iridescent orb body
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        primaryOrbColor,
                        tertiaryOrbColor,
                        secondaryOrbColor
                    ),
                    start = Offset(center.x - sphereRadius, center.y - sphereRadius),
                    end = Offset(center.x + sphereRadius, center.y + sphereRadius)
                ),
                radius = sphereRadius,
                center = center
            )

            // Inner core specular highlight (3D glass sphere effect)
            val highlightCenter = Offset(
                x = center.x - sphereRadius * 0.26f,
                y = center.y - sphereRadius * 0.26f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.92f),
                        primaryOrbColor.copy(alpha = 0.55f),
                        Color.Transparent
                    ),
                    center = highlightCenter,
                    radius = sphereRadius * 0.85f
                ),
                radius = sphereRadius * 0.85f,
                center = center
            )

            // Crisp Glass Rim on Sphere
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        primaryOrbColor.copy(alpha = 0.45f),
                        secondaryOrbColor.copy(alpha = 0.75f)
                    ),
                    start = Offset(center.x - sphereRadius, center.y - sphereRadius),
                    end = Offset(center.x + sphereRadius, center.y + sphereRadius)
                ),
                radius = sphereRadius,
                center = center,
                style = Stroke(width = diameter * 0.012f)
            )
        }
    }
}

/**
 * Preserved entry point delegating directly to the reconstructed [JarvisAIOrb].
 */
@Composable
fun ArcReactorView(
    status: AssistantStatus,
    audioLevel: Float = 0f,
    modifier: Modifier = Modifier
) {
    JarvisAIOrb(
        status = status,
        audioLevel = audioLevel,
        modifier = modifier
    )
}
