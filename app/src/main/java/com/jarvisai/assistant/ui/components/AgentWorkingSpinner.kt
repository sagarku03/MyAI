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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    primaryColor: Color = Color(0xFF5B8DEF),
    secondaryColor: Color = Color(0xFF94A3B8),
    accentColor: Color = Color(0xFF34D399)
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

                // Restrained radial pulse indicator
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.14f * radarPulse),
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
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF15171C)
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

private val ANTIGEM_SPINNER_GLYPHS = listOf("·", "✢", "✳", "✶", "✻", "✽", "✻", "✶", "✳", "✢")

private val ANTIGEM_WORKING_VERBS = listOf(
    "Ideating", "Sussing", "Unfurling", "Schlepping", "Tinkering",
    "Germinating", "Deliberating", "Crunching", "Determining", "Enchanting",
    "Cerebrating", "Hatching", "Synthesizing", "Mulling", "Pondering",
    "Percolating", "Spelunking", "Transmuting", "Unraveling", "Brewing",
    "Crafting", "Forging", "Spinning", "Weaving", "Working"
)

/**
 * AntiGem-style compact working spinner row:
 * Animated rotating 8-point sparkle glyph (`✻`) + typewriter-decoding whimsical status verb (`Ideating...`).
 */
@Composable
fun AntiGemWorkingSpinner(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF60A5FA),
    compacting: Boolean = false
) {
    var glyphIndex by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var currentVerb by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(ANTIGEM_WORKING_VERBS.random())
    }
    var displayedText by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf("$currentVerb...")
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(120L)
            glyphIndex = (glyphIndex + 1) % ANTIGEM_SPINNER_GLYPHS.size
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        var changes = 0
        while (true) {
            kotlinx.coroutines.delay(
                when (changes) {
                    0 -> 2000L
                    1 -> 3000L
                    else -> 5000L
                }
            )
            changes++
            currentVerb = ANTIGEM_WORKING_VERBS.random()
        }
    }

    androidx.compose.runtime.LaunchedEffect(currentVerb, compacting) {
        val target = if (compacting) "Compacting..." else "$currentVerb..."
        var cursor = 0
        while (cursor - 3 < target.length) {
            val sb = StringBuilder(displayedText.take(target.length).padEnd(target.length))
            for (behind in 0..3) {
                val k = cursor - behind
                if (k < 0 || k >= target.length) continue
                val want = target[k]
                sb.setCharAt(
                    k,
                    when {
                        want == ' ' -> ' '
                        behind == 3 -> want
                        behind == 0 -> '▌'
                        else -> charArrayOf('.', '_', want).random()
                    }
                )
            }
            displayedText = sb.toString().trimEnd()
            cursor++
            kotlinx.coroutines.delay(40L)
        }
        displayedText = target
    }

    Row(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .testTag("antigem_working_spinner"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = ANTIGEM_SPINNER_GLYPHS[glyphIndex],
            color = color,
            fontSize = 19.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(22.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = displayedText,
            color = Color(0xFFD1D5DB),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif
        )
    }
}

