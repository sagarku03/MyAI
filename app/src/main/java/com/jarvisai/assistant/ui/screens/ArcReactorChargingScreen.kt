package com.jarvisai.assistant.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.AccentMintEmerald
import com.example.ui.theme.AccentSkyCyan
import com.example.ui.theme.RoyalPurplePrimary
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusRoseRed
import com.example.ui.theme.TextLavenderMuted
import com.example.ui.theme.TextPureWhite
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.ProceduralHudMode
import com.jarvisai.assistant.service.ChargingTelemetryManager
import com.jarvisai.assistant.ui.components.ArcReactorView
import com.jarvisai.assistant.ui.components.JarvisGlassPanel
import com.jarvisai.assistant.ui.components.JarvisIconControl
import com.jarvisai.assistant.ui.components.JarvisSecondaryActionButton
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun ArcReactorChargingScreen(
    onSpeakTelemetry: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val telemetry by ChargingTelemetryManager.telemetryFlow.collectAsStateWithLifecycle()

    var lastInteractionTimestampMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isOledDimmed by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        ChargingTelemetryManager.register(context)
        ChargingTelemetryManager.refreshTelemetry(context)
        onDispose {
            // Keep global receiver active or refresh state cleanly
        }
    }

    // Periodic 2-second hardware PMIC telemetry polling + 15-second OLED burn-in ambient dimmer
    LaunchedEffect(lastInteractionTimestampMs) {
        isOledDimmed = false
        while (true) {
            delay(2000L)
            ChargingTelemetryManager.refreshTelemetry(context)
            val elapsedIdleMs = System.currentTimeMillis() - lastInteractionTimestampMs
            if (elapsedIdleMs >= 15_000L) {
                isOledDimmed = true
            }
        }
    }

    val screenAlpha by animateFloatAsState(
        targetValue = if (isOledDimmed) 0.14f else 1.0f,
        animationSpec = tween(durationMillis = 700),
        label = "oledDimmerAlpha"
    )

    val chargingStandardBadge = when {
        !telemetry.isPowerConnected -> "ARC REACTOR // STANDALONE CELL"
        telemetry.powerWatts >= 45.0 -> "⚡ SUPERVOOC / HYPERCHARGE ULTRA (${String.format(Locale.US, "%.1f", telemetry.powerWatts)}W)"
        telemetry.powerWatts >= 18.0 -> "⚡ USB-PD FAST CHARGING (${String.format(Locale.US, "%.1f", telemetry.powerWatts)}W)"
        telemetry.powerWatts > 0.0 -> "🔋 STANDARD AC/USB INTAKE (${String.format(Locale.US, "%.1f", telemetry.powerWatts)}W)"
        else -> "🔌 POWER COUPLED // STABILIZING PMIC"
    }

    val normalizedPowerLevel = (telemetry.powerWatts.toFloat() / 65f).coerceIn(0.08f, 1.0f)
    val dynamicPulseScale by animateFloatAsState(
        targetValue = if (telemetry.isPowerConnected) 1.0f + (normalizedPowerLevel * 0.14f) else 1.0f,
        animationSpec = tween(600),
        label = "wattagePulseScale"
    )

    val tempAccentColor = when {
        telemetry.temperatureCelsius >= 42.0 -> StatusRoseRed
        telemetry.temperatureCelsius >= 37.5 -> StatusAmber
        else -> AccentMintEmerald
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // Pure #000000 OLED black for zero power consumption on dark pixels
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                lastInteractionTimestampMs = System.currentTimeMillis()
                isOledDimmed = false
            }
            .alpha(screenAlpha)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .testTag("arc_reactor_charging_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. TOP TELEMETRY BANNER & BACK CONTROL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                JarvisIconControl(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = {
                        lastInteractionTimestampMs = System.currentTimeMillis()
                        onBack()
                    },
                    modifier = Modifier.testTag("charging_telemetry_back_btn")
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (telemetry.isPowerConnected) AccentMintEmerald.copy(alpha = 0.16f)
                            else AccentSkyCyan.copy(alpha = 0.14f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (telemetry.isPowerConnected) AccentMintEmerald else AccentSkyCyan,
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .testTag("charging_standard_badge")
                ) {
                    Text(
                        text = chargingStandardBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (telemetry.isPowerConnected) AccentMintEmerald else AccentSkyCyan,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp
                    )
                }

                JarvisIconControl(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Announce Telemetry",
                    tint = AccentSkyCyan,
                    onClick = {
                        lastInteractionTimestampMs = System.currentTimeMillis()
                        val announcement = if (telemetry.isPowerConnected) {
                            ChargingTelemetryManager.formatStarkSpokenFeedback(
                                telemetry.powerWatts,
                                telemetry.temperatureCelsius
                            )
                        } else {
                            ChargingTelemetryManager.formatUnpluggedSpokenFeedback(
                                telemetry.batteryLevelPercent
                            )
                        }
                        onSpeakTelemetry(announcement)
                    },
                    modifier = Modifier.testTag("charging_speak_btn")
                )
            }

            // 2. CENTER: MASSIVE ANIMATED ARC REACTOR CORE SCALED BY REAL-TIME WATTAGE
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = dynamicPulseScale
                            scaleY = dynamicPulseScale
                        }
                ) {
                    ArcReactorView(
                        status = if (telemetry.isPowerConnected) AssistantStatus.LISTENING else AssistantStatus.IDLE,
                        audioLevel = if (telemetry.isPowerConnected) normalizedPowerLevel else 0.08f,
                        size = 240.dp,
                        hudMode = ProceduralHudMode.ARC_DEFAULT
                    )

                    // Center Overlay Percentage Readout
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${telemetry.batteryLevelPercent}%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPureWhite,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f W", telemetry.powerWatts),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (telemetry.isPowerConnected) AccentMintEmerald else AccentSkyCyan,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Text(
                    text = telemetry.spokenFeedback,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLavenderMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Battery Progress Bar
                LinearProgressIndicator(
                    progress = { (telemetry.batteryLevelPercent / 100f).coerceIn(0f, 1f) },
                    color = if (telemetry.isPowerConnected) AccentMintEmerald else AccentSkyCyan,
                    trackColor = Color(0xFF15192B),
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .height(8.dp)
                        .clip(CircleShape)
                )
            }

            // 3. 4 STARK HUD METRIC CARDS (Power Watts, Voltage mV, Core Temp °C, Battery Level %)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StarkTelemetryMetricCard(
                        title = "CHARGING POWER",
                        value = String.format(Locale.US, "%.2f W", telemetry.powerWatts),
                        subtitle = "${telemetry.currentMicroAmps / 1000} mA Current",
                        icon = Icons.Default.Bolt,
                        accentColor = AccentMintEmerald,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("metric_card_watts")
                    )
                    StarkTelemetryMetricCard(
                        title = "CELL VOLTAGE",
                        value = "${telemetry.voltageMillivolts} mV",
                        subtitle = String.format(Locale.US, "%.2f V Rail", telemetry.voltageMillivolts / 1000.0),
                        icon = Icons.Default.ElectricMeter,
                        accentColor = AccentSkyCyan,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("metric_card_voltage")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StarkTelemetryMetricCard(
                        title = "CORE TEMPERATURE",
                        value = String.format(Locale.US, "%.1f °C", telemetry.temperatureCelsius),
                        subtitle = if (telemetry.temperatureCelsius < 38.0) "Thermal Nominal" else "Elevated Load",
                        icon = Icons.Default.Thermostat,
                        accentColor = tempAccentColor,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("metric_card_temp")
                    )
                    StarkTelemetryMetricCard(
                        title = "CELL CAPACITY",
                        value = "${telemetry.batteryLevelPercent}%",
                        subtitle = if (telemetry.isPowerConnected) "Coupled • Charging" else "Discharging",
                        icon = Icons.Default.BatteryChargingFull,
                        accentColor = RoyalPurplePrimary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("metric_card_capacity")
                    )
                }

                // Ambient Dock Hint & Manual Refresh
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isOledDimmed) {
                            "OLED Burn-In Protection Active • Tap anywhere to wake"
                        } else {
                            "Auto-dims after 15s idle for bedside OLED dock mode"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextLavenderMuted,
                        modifier = Modifier.weight(1f)
                    )
                    JarvisSecondaryActionButton(
                        text = "Refresh PMIC",
                        onClick = {
                            lastInteractionTimestampMs = System.currentTimeMillis()
                            ChargingTelemetryManager.refreshTelemetry(context)
                        },
                        modifier = Modifier.testTag("charging_refresh_btn")
                    )
                }
            }
        }
    }
}

@Composable
private fun StarkTelemetryMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    JarvisGlassPanel(
        modifier = modifier,
        accentColor = accentColor,
        cornerRadius = 22.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextLavenderMuted,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.5.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = TextPureWhite,
            fontWeight = FontWeight.Black
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}
