package com.jarvisai.assistant.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AccentChampagneGold
import com.example.ui.theme.AccentMintEmerald
import com.example.ui.theme.AccentRosePink
import com.example.ui.theme.AccentSkyCyan
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.ElectricOrchid
import com.example.ui.theme.RoyalIndigoAccent
import com.example.ui.theme.RoyalPurplePrimary
import com.example.ui.theme.SoftLavenderGlow
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusRoseRed
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.ProceduralHudMode
import com.jarvisai.assistant.service.AdsBAirspaceRadarManager
import com.jarvisai.assistant.service.AdsBRadarSnapshot
import com.jarvisai.assistant.service.GyroscopeOrientation3D
import com.jarvisai.assistant.service.Hologram3DEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Global reactive state controller for Ultron & AbuZar-Ansarii/Jarvis (PrivateAgent) 60 FPS 3D Holographic HUD modes.
 * Tools (Sentry, 3D Iron Man Bust, ADS-B Airspace Radar, Termux, Search, Ghost Pilot, Red Alert) and UI selectors can switch this at runtime.
 */
object ProceduralHudModeState {
    private val _currentMode = MutableStateFlow(ProceduralHudMode.ARC_DEFAULT)
    val currentMode: StateFlow<ProceduralHudMode> = _currentMode.asStateFlow()

    fun setMode(mode: ProceduralHudMode) {
        _currentMode.value = mode
    }
}

private fun DrawScope.drawProceduralModeOverlay(
    hudMode: ProceduralHudMode,
    center: Offset,
    radius: Float,
    primaryRotation: Float,
    secondaryRotation: Float,
    wavePhase: Float,
    gyro: GyroscopeOrientation3D = GyroscopeOrientation3D(),
    radarSnapshot: AdsBRadarSnapshot = AdsBRadarSnapshot(),
    auraColor: Color = AccentSkyCyan
) {
    // Subtle 3D Gyroscope Parallax Ring (responds to real phone tilt in all modes)
    if (gyro.isSensorActive && (gyro.pitchDeg != 0f || gyro.rollDeg != 0f)) {
        val tiltOffset = Offset(
            center.x + (gyro.rollDeg / 45f) * radius * 0.22f,
            center.y + (gyro.pitchDeg / 45f) * radius * 0.22f
        )
        drawCircle(
            color = auraColor.copy(alpha = 0.42f),
            radius = radius * 0.88f,
            center = tiltOffset,
            style = Stroke(width = 1.6.dp.toPx())
        )
    }

    when (hudMode) {
        ProceduralHudMode.ARC_DEFAULT -> Unit
        ProceduralHudMode.IRON_MAN_BUST_3D -> {
            // AbuZar-Ansarii/Jarvis (PrivateAgent) Translucent 3D Iron Man Suit Bust Hologram
            val orbitYaw = sin(wavePhase * 2f * PI.toFloat()) * 22f
            val pitch = gyro.pitchDeg * 0.65f
            val roll = gyro.rollDeg * 0.65f
            val scaleR = radius * 0.84f

            // Holographic Horizontal Laser Scanline
            val scanY = center.y + ((wavePhase * 2f - 1f) * scaleR * 0.85f)
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, auraColor.copy(alpha = 0.85f), Color.White, auraColor.copy(alpha = 0.85f), Color.Transparent)
                ),
                start = Offset(center.x - scaleR * 0.85f, scanY),
                end = Offset(center.x + scaleR * 0.85f, scanY),
                strokeWidth = 2.dp.toPx()
            )

            // Render 3D Wireframe Mesh (Helmet, Glowing Visor Eyes, Shoulders & Chest Arc Reactor)
            for (edge in Hologram3DEngine.mark85BustWireframeEdges) {
                val (p1, depth1) = Hologram3DEngine.project3DPoint(edge.start, center, scaleR, pitch, roll, orbitYaw)
                val (p2, depth2) = Hologram3DEngine.project3DPoint(edge.end, center, scaleR, pitch, roll, orbitYaw)
                val avgDepth = ((depth1 + depth2) * 0.5f).coerceIn(0.6f, 1.5f)
                val edgeColor = when (edge.layerGroup) {
                    1 -> Color.White.copy(alpha = 0.95f) // Glowing Eyes & Chest Arc Core
                    2 -> auraColor.copy(alpha = (0.65f * avgDepth).coerceIn(0.25f, 0.95f))
                    else -> AccentSkyCyan.copy(alpha = (0.78f * avgDepth).coerceIn(0.30f, 0.98f))
                }
                val strokeW = if (edge.layerGroup == 1) 2.8.dp.toPx() else 1.7.dp.toPx()
                drawLine(
                    color = edgeColor,
                    start = p1,
                    end = p2,
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }
        }
        ProceduralHudMode.ADSB_AIRSPACE_RADAR -> {
            // AbuZar-Ansarii/Jarvis (PrivateAgent) 360° Live ADS-B Overhead Aircraft Radar
            for (ring in 1..3) {
                drawCircle(
                    color = AccentMintEmerald.copy(alpha = 0.35f),
                    radius = radius * (ring * 0.30f),
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
            rotate(degrees = primaryRotation * 2.5f, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            AccentMintEmerald.copy(alpha = 0.08f),
                            AccentMintEmerald.copy(alpha = 0.72f)
                        ),
                        center = center
                    ),
                    startAngle = 0f,
                    sweepAngle = 75f,
                    useCenter = true,
                    topLeft = Offset(center.x - radius * 0.92f, center.y - radius * 0.92f),
                    size = androidx.compose.ui.geometry.Size(radius * 1.84f, radius * 1.84f)
                )
            }
            val maxR = radarSnapshot.radiusKm.coerceAtLeast(50).toFloat()
            radarSnapshot.aircraftList.take(12).forEach { flight ->
                val normDist = (flight.distanceKm.toFloat() / maxR).coerceIn(0.12f, 0.88f)
                val radBearing = (flight.bearingDeg - 90f) * (PI.toFloat() / 180f)
                val bx = center.x + cos(radBearing) * radius * normDist
                val by = center.y + sin(radBearing) * radius * normDist
                drawCircle(
                    color = AccentChampagneGold,
                    radius = 4.2.dp.toPx(),
                    center = Offset(bx, by)
                )
                drawCircle(
                    color = AccentMintEmerald.copy(alpha = 0.55f),
                    radius = 8.dp.toPx(),
                    center = Offset(bx, by),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
        }
        ProceduralHudMode.SENTRY_MATRIX -> {
            // 360° Radar Sweep & Target Reticle Crosshairs
            rotate(degrees = primaryRotation * 2.2f, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            AccentMintEmerald.copy(alpha = 0.05f),
                            AccentMintEmerald.copy(alpha = 0.55f),
                            AccentSkyCyan.copy(alpha = 0.85f)
                        ),
                        center = center
                    ),
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = Offset(center.x - radius * 0.92f, center.y - radius * 0.92f),
                    size = androidx.compose.ui.geometry.Size(radius * 1.84f, radius * 1.84f)
                )
            }
            val crossLen = radius * 0.96f
            drawLine(
                color = AccentMintEmerald.copy(alpha = 0.65f),
                start = Offset(center.x - crossLen, center.y),
                end = Offset(center.x + crossLen, center.y),
                strokeWidth = 1.2.dp.toPx()
            )
            drawLine(
                color = AccentMintEmerald.copy(alpha = 0.65f),
                start = Offset(center.x, center.y - crossLen),
                end = Offset(center.x, center.y + crossLen),
                strokeWidth = 1.2.dp.toPx()
            )
        }
        ProceduralHudMode.CYBER_TERMINAL -> {
            // Segmented Prometheus Code Telemetry Ticks
            val tickCount = 24
            rotate(degrees = -secondaryRotation * 1.5f, pivot = center) {
                for (i in 0 until tickCount) {
                    val angleRad = (i * (360f / tickCount)) * (PI.toFloat() / 180f)
                    val innerR = radius * (if (i % 2 == 0) 0.80f else 0.85f)
                    val outerR = radius * 0.96f
                    drawLine(
                        color = if (i % 3 == 0) AccentSkyCyan else AccentMintEmerald.copy(alpha = 0.75f),
                        start = Offset(center.x + cos(angleRad) * innerR, center.y + sin(angleRad) * innerR),
                        end = Offset(center.x + cos(angleRad) * outerR, center.y + sin(angleRad) * outerR),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        ProceduralHudMode.QUANTUM_SEARCH -> {
            // Multi-Wave Sonar Concentric Ripples
            for (ring in 0..2) {
                val p = (wavePhase + ring * 0.33f) % 1f
                val r = radius * (0.45f + p * 0.55f)
                drawCircle(
                    color = AccentSkyCyan.copy(alpha = (1f - p) * 0.75f),
                    radius = r,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        ProceduralHudMode.CHRONOS_GUARDIAN -> {
            // Clockwork 12-Hour Dial & Golden Bio-Rhythm Ring
            for (h in 0 until 12) {
                val angleRad = (h * 30f) * (PI.toFloat() / 180f)
                val innerR = radius * 0.82f
                val outerR = radius * 0.94f
                drawLine(
                    color = AccentChampagneGold.copy(alpha = 0.85f),
                    start = Offset(center.x + cos(angleRad) * innerR, center.y + sin(angleRad) * innerR),
                    end = Offset(center.x + cos(angleRad) * outerR, center.y + sin(angleRad) * outerR),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            rotate(degrees = primaryRotation * 3f, pivot = center) {
                drawLine(
                    color = AccentChampagneGold,
                    start = center,
                    end = Offset(center.x, center.y - radius * 0.72f),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        ProceduralHudMode.GHOST_PILOT -> {
            // Sub-Pixel Coordinate Vector Diamond & Bezier Lock
            val dRadius = radius * 0.90f
            rotate(degrees = primaryRotation, pivot = center) {
                val diamond = Path().apply {
                    moveTo(center.x, center.y - dRadius)
                    lineTo(center.x + dRadius, center.y)
                    lineTo(center.x, center.y + dRadius)
                    lineTo(center.x - dRadius, center.y)
                    close()
                }
                drawPath(
                    path = diamond,
                    color = ElectricOrchid.copy(alpha = 0.80f),
                    style = Stroke(width = 1.8.dp.toPx())
                )
            }
        }
        ProceduralHudMode.RED_ALERT -> {
            // Defcon-1 Crimson Octagonal Lockdown Shield
            val octR = radius * 0.92f
            rotate(degrees = secondaryRotation * 2f, pivot = center) {
                val octagon = Path().apply {
                    for (i in 0 until 8) {
                        val a = (i * 45f) * (PI.toFloat() / 180f)
                        val px = center.x + cos(a) * octR
                        val py = center.y + sin(a) * octR
                        if (i == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(
                    path = octagon,
                    color = AlertCrimson.copy(alpha = 0.90f),
                    style = Stroke(width = 2.6.dp.toPx())
                )
            }
        }
    }
}

/**
 * Apple iOS 26 "Liquid Glass" Multi-Tone 3D AI Core Orb + AbuZar-Ansarii/Jarvis Dual 3D Hologram HUD Overlays.
 */
@Composable
fun JarvisAIOrb(
    status: AssistantStatus,
    audioLevel: Float = 0f,
    size: Dp = 200.dp,
    hudMode: ProceduralHudMode? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        Hologram3DEngine.startTracking(context)
        onDispose { }
    }
    val observedHudMode by ProceduralHudModeState.currentMode.collectAsState()
    val gyroState by Hologram3DEngine.orientation.collectAsState()
    val radarState by AdsBAirspaceRadarManager.radarSnapshot.collectAsState()
    val activeHudMode = hudMode ?: observedHudMode
    val infiniteTransition = rememberInfiniteTransition(label = "liquidPurpleMultiToneOrb")

    val primaryRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "primaryRotation"
    )

    val secondaryRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "secondaryRotation"
    )

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )

    val liquidPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liquidPhase"
    )

    val (corePrimary, coreSecondary, outerAccent) = when {
        activeHudMode == ProceduralHudMode.RED_ALERT -> Triple(AlertCrimson, StatusAmber, AccentRosePink)
        activeHudMode == ProceduralHudMode.SENTRY_MATRIX -> Triple(AccentMintEmerald, AccentSkyCyan, RoyalPurplePrimary)
        activeHudMode == ProceduralHudMode.CYBER_TERMINAL -> Triple(AccentSkyCyan, AccentMintEmerald, RoyalIndigoAccent)
        activeHudMode == ProceduralHudMode.CHRONOS_GUARDIAN -> Triple(AccentChampagneGold, RoyalPurplePrimary, AccentSkyCyan)
        status == AssistantStatus.IDLE -> Triple(RoyalPurplePrimary, AccentSkyCyan, AccentRosePink)
        status == AssistantStatus.LISTENING -> Triple(RoyalPurplePrimary, StatusEmerald, AccentSkyCyan)
        status == AssistantStatus.THINKING -> Triple(ElectricOrchid, AccentRosePink, StatusAmber)
        else -> Triple(RoyalPurplePrimary, AccentSkyCyan, SoftLavenderGlow)
    }

    val dynamicBoost = (audioLevel.coerceIn(0f, 1f) * 0.24f)
    val effectiveScale = (breathingScale + dynamicBoost).coerceAtMost(1.26f)

    Box(
        modifier = modifier
            .size(size)
            .testTag("arc_reactor_orb"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxRadius = (this.size.minDimension / 2f)

            // 1. Outer Multi-Tone Ambient Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        corePrimary.copy(alpha = 0.42f),
                        coreSecondary.copy(alpha = 0.22f),
                        outerAccent.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius * effectiveScale
                ),
                radius = maxRadius * effectiveScale,
                center = center
            )

            // 2. Outer Specular Liquid Glass Ring
            rotate(degrees = primaryRotation, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.65f),
                            corePrimary.copy(alpha = 0.85f),
                            coreSecondary.copy(alpha = 0.85f),
                            outerAccent.copy(alpha = 0.70f),
                            Color.White.copy(alpha = 0.65f)
                        ),
                        center = center
                    ),
                    radius = maxRadius * 0.82f,
                    center = center,
                    style = Stroke(width = 2.4.dp.toPx())
                )

                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            coreSecondary.copy(alpha = 0.90f),
                            Color.White.copy(alpha = 0.95f),
                            corePrimary.copy(alpha = 0.90f),
                            Color.Transparent
                        ),
                        center = center
                    ),
                    startAngle = 15f,
                    sweepAngle = 135f,
                    useCenter = false,
                    topLeft = Offset(center.x - maxRadius * 0.73f, center.y - maxRadius * 0.73f),
                    size = androidx.compose.ui.geometry.Size(maxRadius * 1.46f, maxRadius * 1.46f),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 3. Counter-Rotating Harmonic Wave Ring
            rotate(degrees = secondaryRotation, pivot = center) {
                val waveRadius = maxRadius * 0.63f * (0.96f + dynamicBoost * 0.4f)
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            outerAccent.copy(alpha = 0.15f),
                            coreSecondary.copy(alpha = 0.75f),
                            Color.White.copy(alpha = 0.60f),
                            corePrimary.copy(alpha = 0.75f),
                            outerAccent.copy(alpha = 0.15f)
                        ),
                        center = center
                    ),
                    radius = waveRadius,
                    center = center,
                    style = Stroke(width = 2.2.dp.toPx())
                )
            }

            // 4. Inner 3D Translucent Liquid Glass Sphere
            val sphereRadius = maxRadius * 0.50f * effectiveScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.96f),
                        SoftLavenderGlow.copy(alpha = 0.92f),
                        corePrimary.copy(alpha = 0.90f),
                        coreSecondary.copy(alpha = 0.78f),
                        RoyalIndigoAccent.copy(alpha = 0.85f)
                    ),
                    center = Offset(
                        center.x - sphereRadius * 0.22f + cos(liquidPhase) * sphereRadius * 0.08f,
                        center.y - sphereRadius * 0.22f + sin(liquidPhase) * sphereRadius * 0.08f
                    ),
                    radius = sphereRadius * 1.30f
                ),
                radius = sphereRadius,
                center = center
            )

            // 5. Secondary Internal Plasma Lobe
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        outerAccent.copy(alpha = 0.50f),
                        coreSecondary.copy(alpha = 0.30f),
                        Color.Transparent
                    ),
                    center = Offset(
                        center.x + sphereRadius * 0.28f * sin(liquidPhase),
                        center.y + sphereRadius * 0.28f * cos(liquidPhase)
                    ),
                    radius = sphereRadius * 0.78f
                ),
                radius = sphereRadius * 0.75f,
                center = center
            )

            // 6. Specular Glass Lensing Highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.62f),
                        Color.White.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - sphereRadius * 0.34f, center.y - sphereRadius * 0.34f),
                    radius = sphereRadius * 0.58f
                ),
                radius = sphereRadius * 0.54f,
                center = Offset(center.x - sphereRadius * 0.26f, center.y - sphereRadius * 0.26f)
            )

            // 7. Outer Sphere Specular Rim Border
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.80f),
                        corePrimary.copy(alpha = 0.65f),
                        coreSecondary.copy(alpha = 0.65f)
                    ),
                    start = Offset(center.x - sphereRadius, center.y - sphereRadius),
                    end = Offset(center.x + sphereRadius, center.y + sphereRadius)
                ),
                radius = sphereRadius,
                center = center,
                style = Stroke(width = 1.6.dp.toPx())
            )

            // 8. Ultron & AbuZar-Ansarii/Jarvis 60 FPS 3D Hologram HUD Overlay
            drawProceduralModeOverlay(
                hudMode = activeHudMode,
                center = center,
                radius = maxRadius,
                primaryRotation = primaryRotation,
                secondaryRotation = secondaryRotation,
                wavePhase = (liquidPhase / (2f * PI).toFloat()).coerceIn(0f, 1f),
                gyro = gyroState,
                radarSnapshot = radarState,
                auraColor = coreSecondary
            )
        }
    }
}

@Composable
fun ArcReactorView(
    status: AssistantStatus,
    audioLevel: Float = 0f,
    size: Dp = 200.dp,
    hudMode: ProceduralHudMode? = null,
    modifier: Modifier = Modifier
) {
    QuantumPulseCore(
        status = status,
        audioLevel = audioLevel,
        size = size,
        hudMode = hudMode,
        modifier = modifier
    )
}

/**
 * QuantumPulseCore: Clean Luxury Cyber-Minimalism pulse core + Ultron 60 FPS Procedural HUD Overlays.
 */
@Composable
fun QuantumPulseCore(
    status: AssistantStatus,
    audioLevel: Float = 0f,
    modifier: Modifier = Modifier,
    size: Dp = 190.dp,
    hudMode: ProceduralHudMode? = null,
    onClick: (() -> Unit)? = null
) {
    val themeColors = com.example.ui.theme.JarvisTheme.colors
    val observedHudMode by ProceduralHudModeState.currentMode.collectAsState()
    val activeHudMode = hudMode ?: observedHudMode

    val gyroState by Hologram3DEngine.orientation.collectAsState()
    val radarState by AdsBAirspaceRadarManager.radarSnapshot.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "quantumPulseCore")

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )

    val haloRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "haloRotation"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val (primaryHalo, secondaryHalo, _) = when {
        activeHudMode == ProceduralHudMode.RED_ALERT -> Triple(AlertCrimson, StatusAmber, AlertCrimson)
        activeHudMode == ProceduralHudMode.SENTRY_MATRIX -> Triple(AccentMintEmerald, AccentSkyCyan, AccentMintEmerald)
        activeHudMode == ProceduralHudMode.CYBER_TERMINAL -> Triple(AccentSkyCyan, AccentMintEmerald, AccentSkyCyan)
        activeHudMode == ProceduralHudMode.CHRONOS_GUARDIAN -> Triple(AccentChampagneGold, themeColors.accent, AccentChampagneGold)
        status == AssistantStatus.IDLE -> Triple(themeColors.accent, themeColors.secondary, themeColors.accent)
        status == AssistantStatus.LISTENING -> Triple(themeColors.statusEmerald, themeColors.accent, themeColors.statusEmerald)
        status == AssistantStatus.THINKING -> Triple(themeColors.statusAmber, themeColors.accent, themeColors.statusAmber)
        else -> Triple(themeColors.accent, themeColors.primary, themeColors.accent)
    }

    val audioBoost = (audioLevel.coerceIn(0f, 1f) * 0.18f)
    val effectiveScale = breathingScale + audioBoost

    Box(
        modifier = modifier
            .size(size)
            .testTag("home_quantum_pulse_core")
            .testTag("arc_reactor_orb")
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = (this.size.width / 2f) * 0.72f * effectiveScale

            // 1. Outer Ambient Glow Halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryHalo.copy(alpha = if (themeColors.isDark) 0.32f else 0.18f),
                        secondaryHalo.copy(alpha = if (themeColors.isDark) 0.14f else 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.35f
                ),
                radius = baseRadius * 1.35f,
                center = center
            )

            // 2. Expanding Concentric Sound Wave Halo
            val waveRadius = baseRadius * (0.85f + 0.35f * wavePhase)
            val waveAlpha = (1f - wavePhase) * (if (themeColors.isDark) 0.40f else 0.25f)
            drawCircle(
                color = primaryHalo.copy(alpha = waveAlpha),
                radius = waveRadius,
                center = center,
                style = Stroke(width = 1.4.dp.toPx())
            )

            // 3. Middle Refraction Ring with Specular Edge
            rotate(haloRotation, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            primaryHalo.copy(alpha = 0.85f),
                            secondaryHalo.copy(alpha = 0.30f),
                            Color.White.copy(alpha = 0.75f),
                            primaryHalo.copy(alpha = 0.85f)
                        ),
                        center = center
                    ),
                    radius = baseRadius * 0.95f,
                    center = center,
                    style = Stroke(width = 2.0.dp.toPx())
                )
            }

            // 4. Satin Glass Sphere Body
            val sphereBrush = if (themeColors.isDark) {
                Brush.radialGradient(
                    colors = listOf(
                        themeColors.surfaceElevated.copy(alpha = 0.95f),
                        themeColors.surface.copy(alpha = 0.90f),
                        primaryHalo.copy(alpha = 0.45f)
                    ),
                    center = Offset(center.x - baseRadius * 0.25f, center.y - baseRadius * 0.25f),
                    radius = baseRadius * 0.85f
                )
            } else {
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.98f),
                        themeColors.surface.copy(alpha = 0.95f),
                        primaryHalo.copy(alpha = 0.20f)
                    ),
                    center = Offset(center.x - baseRadius * 0.25f, center.y - baseRadius * 0.25f),
                    radius = baseRadius * 0.85f
                )
            }
            drawCircle(
                brush = sphereBrush,
                radius = baseRadius * 0.78f,
                center = center
            )

            // 5. Specular Top Lensing Sheen
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (themeColors.isDark) 0.65f else 0.85f),
                        Color.White.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - baseRadius * 0.28f, center.y - baseRadius * 0.28f),
                    radius = baseRadius * 0.45f
                ),
                radius = baseRadius * 0.45f,
                center = Offset(center.x - baseRadius * 0.22f, center.y - baseRadius * 0.22f)
            )

            // 6. Inner Glowing Micro Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        primaryHalo,
                        primaryHalo.copy(alpha = 0.40f)
                    ),
                    center = center,
                    radius = baseRadius * 0.28f
                ),
                radius = baseRadius * 0.25f,
                center = center
            )

            // 7. Hairline Outer Specular Border
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.70f),
                        primaryHalo.copy(alpha = 0.50f),
                        Color.White.copy(alpha = 0.20f)
                    ),
                    start = Offset(center.x - baseRadius, center.y - baseRadius),
                    end = Offset(center.x + baseRadius, center.y + baseRadius)
                ),
                radius = baseRadius * 0.78f,
                center = center,
                style = Stroke(width = 1.2.dp.toPx())
            )

            // 8. Ultron & AbuZar-Ansarii/Jarvis 60 FPS 3D Hologram HUD Overlay
            drawProceduralModeOverlay(
                hudMode = activeHudMode,
                center = center,
                radius = baseRadius * 1.15f,
                primaryRotation = haloRotation,
                secondaryRotation = 360f - haloRotation,
                wavePhase = wavePhase,
                gyro = gyroState,
                radarSnapshot = radarState,
                auraColor = primaryHalo
            )
        }
    }
}


