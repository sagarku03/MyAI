package com.jarvisai.assistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentChampagneGold
import com.example.ui.theme.AccentMintEmerald
import com.example.ui.theme.AccentRosePink
import com.example.ui.theme.AccentSkyCyan
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.RoyalPurplePrimary
import com.example.ui.theme.TextLavenderMuted
import com.jarvisai.assistant.model.ProceduralHudMode
import com.jarvisai.assistant.service.AdsBAirspaceRadarManager
import com.jarvisai.assistant.service.AirGestureType
import com.jarvisai.assistant.service.HermesConsciousnessVault
import com.jarvisai.assistant.service.Hologram3DEngine
import com.jarvisai.assistant.service.OllamaLocalLlmBridge
import com.jarvisai.assistant.service.ProximityAirGestureManager
import com.jarvisai.assistant.watchman.JarvisWatchmanManager
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Interactive Command Deck for the 7 AbuZar-Ansarii/Jarvis (PrivateAgent) Fused Subsystems:
 * 1. Dual 3D Hologram HUD Switcher (3D Gyro Arc Reactor, 3D Translucent Iron Man Bust, 360° ADS-B Radar)
 * 2. Zero-Touch Optical Proximity Air Gestures (Wave, Double-Wave Vision, Palm Hover)
 * 3. Armed Accelerometer Anti-Theft Sentry Mode (Motion Watchdog + Vocal Deterrence + Front Camera2)
 * 4. Real-Time OpenSky ADS-B Overhead Aircraft Radar
 * 5. Hermes Self-Evolving Consciousness Vault (SOUL.md, USER.md, AGENT.md & Distilled Macros)
 * 6. Native Ollama Local LLM (127.0.0.1:11434) & OpenAI-Compatible Bridge
 */
@Composable
fun AbuZarPrivateAgentCommandDeck(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        Hologram3DEngine.startTracking(context)
        HermesConsciousnessVault.ensureInitialized(context)
        OllamaLocalLlmBridge.loadSavedConfig(context)
    }

    val activeHudMode by ProceduralHudModeState.currentMode.collectAsState()
    val gyro by Hologram3DEngine.orientation.collectAsState()
    val airGestureState by ProximityAirGestureManager.state.collectAsState()
    val sentryState by JarvisWatchmanManager.sentryState.collectAsState()
    val radarSnapshot by AdsBAirspaceRadarManager.radarSnapshot.collectAsState()
    val consciousnessSnap by HermesConsciousnessVault.snapshot.collectAsState()
    val ollamaConfig by OllamaLocalLlmBridge.config.collectAsState()

    var isRadarScanning by remember { mutableStateOf(false) }
    var showConsciousnessVault by remember { mutableStateOf(false) }
    var selectedDocTab by remember { mutableStateOf("SOUL") }
    var editableMarkdown by remember(consciousnessSnap, selectedDocTab) {
        mutableStateOf(
            when (selectedDocTab) {
                "SOUL" -> consciousnessSnap.soulMarkdown
                "USER" -> consciousnessSnap.userMarkdown
                else -> consciousnessSnap.agentMarkdown
            }
        )
    }
    var showOllamaPanel by remember { mutableStateOf(false) }
    var ollamaEndpointInput by remember(ollamaConfig.endpointUrl) { mutableStateOf(ollamaConfig.endpointUrl) }
    var ollamaModelInput by remember(ollamaConfig.modelName) { mutableStateOf(ollamaConfig.modelName) }
    var ollamaTestPrompt by remember { mutableStateOf("Jarvis, report local system status in Hinglish") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0E172A).copy(alpha = 0.96f),
                        Color(0xFF1E1B4B).copy(alpha = 0.96f)
                    )
                )
            )
            .border(
                width = 1.3.dp,
                brush = Brush.horizontalGradient(
                    listOf(AccentSkyCyan, RoyalPurplePrimary, AccentMintEmerald)
                ),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(16.dp)
            .testTag("abuzar_private_agent_command_deck"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // HEADER: PrivateAgent Fusion Status + Gyroscope 3D Telemetry
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🦾 PRIVATE-AGENT 3D & SENSOR SUITE",
                    style = MaterialTheme.typography.titleSmall,
                    color = AccentSkyCyan,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "3D Bust Hologram • Air Gestures • ADS-B Radar • Sentry • SOUL.md",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextLavenderMuted,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, AccentMintEmerald.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = String.format(
                        Locale.US,
                        "3D GYRO %.0f°/%.0f°",
                        gyro.pitchDeg,
                        gyro.rollDeg
                    ),
                    fontSize = 10.sp,
                    color = AccentMintEmerald,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // 1. 3D HOLOGRAM HUD MODE SWITCHER (ARC CORE ↔ 3D IRON MAN BUST ↔ ADS-B RADAR ↔ SENTRY)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val featuredModes = listOf(
                ProceduralHudMode.ARC_DEFAULT to "⚛️ 3D Arc Core",
                ProceduralHudMode.IRON_MAN_BUST_3D to "🦾 3D Iron Man Bust",
                ProceduralHudMode.ADSB_AIRSPACE_RADAR to "✈️ ADS-B Radar",
                ProceduralHudMode.SENTRY_MATRIX to "🛡️ Sentry Matrix",
                ProceduralHudMode.GHOST_PILOT to "🎯 Ghost Pilot",
                ProceduralHudMode.RED_ALERT to "🚨 Red Alert"
            )
            featuredModes.forEach { (mode, label) ->
                val selected = activeHudMode == mode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected) Brush.horizontalGradient(listOf(AccentSkyCyan, RoyalPurplePrimary))
                            else Brush.horizontalGradient(listOf(Color(0xFF141B30), Color(0xFF141B30)))
                        )
                        .border(
                            1.dp,
                            if (selected) Color.White else AccentSkyCyan.copy(alpha = 0.35f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { ProceduralHudModeState.setMode(mode) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("hud_mode_chip_${mode.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (selected) Color.White else TextLavenderMuted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // 2. ZERO-TOUCH PROXIMITY AIR GESTURES & ARMED ACCELEROMETER SENTRY MODE ROW
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Air Gestures Toggle Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (airGestureState.isEnabled) AccentMintEmerald.copy(alpha = 0.22f)
                        else Color(0xFF141B30)
                    )
                    .border(
                        1.dp,
                        if (airGestureState.isEnabled) AccentMintEmerald else AccentSkyCyan.copy(alpha = 0.4f),
                        RoundedCornerShape(18.dp)
                    )
                    .clickable {
                        ProximityAirGestureManager.setEnabled(context, !airGestureState.isEnabled)
                    }
                    .padding(12.dp)
                    .testTag("btn_toggle_air_gestures")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (airGestureState.isEnabled) "🖐️ Air Gestures: ON" else "🖐️ Air Gestures: OFF",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Wave • 2x Vision • Hover (${airGestureState.totalGesturesDetected})",
                        color = if (airGestureState.isEnabled) AccentMintEmerald else TextLavenderMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Armed Accelerometer Sentry Mode Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (sentryState.isArmed) AlertCrimson.copy(alpha = 0.28f)
                        else Color(0xFF141B30)
                    )
                    .border(
                        1.dp,
                        if (sentryState.isArmed) AlertCrimson else AccentChampagneGold.copy(alpha = 0.5f),
                        RoundedCornerShape(18.dp)
                    )
                    .clickable {
                        JarvisWatchmanManager.setSentryModeArmed(context, !sentryState.isArmed)
                    }
                    .padding(12.dp)
                    .testTag("btn_toggle_sentry_mode")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (sentryState.isArmed) "🚨 Sentry: ARMED" else "🛡️ Desk Sentry: OFF",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (sentryState.isArmed) "Motion -> Photo + Siren" else "Tap to arm anti-theft",
                        color = if (sentryState.isArmed) AccentChampagneGold else TextLavenderMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Quick Air Gesture Trigger Strip (when Air Gestures enabled)
        AnimatedVisibility(visible = airGestureState.isEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        AirGestureType.SINGLE_WAVE to "👋 Wave (Next)",
                        AirGestureType.DOUBLE_WAVE to "📸 2x Wave (Vision)",
                        AirGestureType.PALM_HOVER to "✋ Hover (Play/Pause)"
                    ).forEach { (gType, label) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .border(1.dp, AccentSkyCyan.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .clickable {
                                    ProximityAirGestureManager.triggerGestureManually(context, gType)
                                }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = AccentSkyCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = airGestureState.lastActionSummaryHinglish,
                    color = AccentMintEmerald,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 3. REAL-TIME ADS-B OVERHEAD AIRCRAFT RADAR ACTION BAR
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0A1224))
                .border(1.dp, AccentSkyCyan.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "✈️ ADS-B Overhead Aircraft Telemetry (${radarSnapshot.aircraftList.size} Flights)",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = radarSnapshot.spokenHinglishBriefing,
                        color = TextLavenderMuted,
                        fontSize = 10.5.sp,
                        maxLines = 2
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(AccentSkyCyan)
                        .clickable(enabled = !isRadarScanning) {
                            isRadarScanning = true
                            scope.launch {
                                AdsBAirspaceRadarManager.scanOverheadAirspace(context, 150)
                                isRadarScanning = false
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .testTag("btn_scan_adsb_radar")
                ) {
                    Text(
                        text = if (isRadarScanning) "Scanning..." else "Scan Airspace",
                        color = Color(0xFF04101E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            if (radarSnapshot.aircraftList.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    radarSnapshot.aircraftList.take(6).forEach { flight ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF13203B))
                                .border(1.dp, AccentMintEmerald.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Column {
                                Text(
                                    text = "✈️ ${flight.callsign} • ${String.format(Locale.US, "%.1f", flight.distanceKm)} km",
                                    color = AccentChampagneGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "${flight.altitudeFeet} ft • ${flight.speedKmh} km/h • ${flight.originCountry}",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 9.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. HERMES CONSCIOUSNESS VAULT (SOUL.md | USER.md | AGENT.md) & LOCAL OLLAMA BRIDGE BUTTONS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(RoyalPurplePrimary.copy(alpha = 0.25f))
                    .border(1.dp, RoyalPurplePrimary, RoundedCornerShape(14.dp))
                    .clickable { showConsciousnessVault = !showConsciousnessVault }
                    .padding(vertical = 10.dp)
                    .testTag("btn_toggle_consciousness_vault"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🧠 SOUL / USER / AGENT.md (${consciousnessSnap.distilledMacros.size})",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AccentRosePink.copy(alpha = 0.22f))
                    .border(1.dp, AccentRosePink, RoundedCornerShape(14.dp))
                    .clickable { showOllamaPanel = !showOllamaPanel }
                    .padding(vertical = 10.dp)
                    .testTag("btn_toggle_ollama_bridge"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (ollamaConfig.isOnline) "🦙 Ollama: ONLINE" else "🦙 Local Ollama (:11434)",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // EXPANDABLE HERMES MARKDOWN CONSCIOUSNESS VAULT EDITOR
        AnimatedVisibility(visible = showConsciousnessVault) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0B0F1E))
                    .border(1.dp, RoyalPurplePrimary.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("SOUL", "USER", "AGENT").forEach { tab ->
                        val active = selectedDocTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) RoyalPurplePrimary else Color(0xFF171E36))
                                .clickable {
                                    selectedDocTab = tab
                                    editableMarkdown = when (tab) {
                                        "SOUL" -> consciousnessSnap.soulMarkdown
                                        "USER" -> consciousnessSnap.userMarkdown
                                        else -> consciousnessSnap.agentMarkdown
                                    }
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$tab.md",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = editableMarkdown,
                    onValueChange = { editableMarkdown = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        fontSize = 11.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentSkyCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Distilled Macros: ${consciousnessSnap.distilledMacros.joinToString { it.skillName }}",
                        color = AccentMintEmerald,
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(AccentMintEmerald)
                            .clickable {
                                HermesConsciousnessVault.updateMarkdownFile(
                                    context = context,
                                    targetDocument = selectedDocTab,
                                    content = editableMarkdown,
                                    appendMode = false
                                )
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Save $selectedDocTab.md",
                            color = Color(0xFF04101E),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        // EXPANDABLE NATIVE OLLAMA LOCAL LLM BRIDGE PANEL
        AnimatedVisibility(visible = showOllamaPanel) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0B0F1E))
                    .border(1.dp, AccentRosePink.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🦙 Native Ollama Local Server & OpenAI Bridge",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = ollamaEndpointInput,
                        onValueChange = { ollamaEndpointInput = it },
                        label = { Text("Ollama URL", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1.3f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = ollamaModelInput,
                        onValueChange = { ollamaModelInput = it },
                        label = { Text("Model Tag", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentSkyCyan)
                            .clickable {
                                OllamaLocalLlmBridge.saveConfig(context, ollamaEndpointInput, ollamaModelInput)
                                scope.launch { OllamaLocalLlmBridge.probeOllamaServer(context) }
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Save & Probe :11434",
                            color = Color(0xFF04101E),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(RoyalPurplePrimary)
                            .clickable {
                                OllamaLocalLlmBridge.saveConfig(context, ollamaEndpointInput, ollamaModelInput)
                                scope.launch {
                                    OllamaLocalLlmBridge.chatWithLocalOrOpenAiLlm(context, ollamaTestPrompt)
                                }
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Test Local Inference",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Text(
                    text = ollamaConfig.lastResponsePreview,
                    color = AccentMintEmerald,
                    fontSize = 10.5.sp
                )
            }
        }
    }
}
