package com.jarvisai.assistant.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.JarvisTheme
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.model.WorkflowAction
import com.jarvisai.assistant.service.AgenticStepLog
import com.jarvisai.assistant.service.CachedNotificationReplyAction
import com.jarvisai.assistant.service.CrossAppScratchpadResult
import com.jarvisai.assistant.service.FormFillSessionState
import com.jarvisai.assistant.service.HighRiskGateRequest
import com.jarvisai.assistant.service.JarvisAccessibilityService
import com.jarvisai.assistant.service.OverlayHudState
import com.jarvisai.assistant.ui.components.JarvisAgenticCommandCenterCard
import com.jarvisai.assistant.ui.components.QuantumPulseCore

/**
 * JARVIS Spatial Cockpit Home Screen
 * Clean Luxury Cyber-Minimalism architecture according to JARVIS_UI_REDESIGN_PLAN.md.
 * 
 * Zone 1: Dynamic Status Island (top header)
 * Zone 2: Quantum Pulse Core & Omni-Command Dispatch (centerpiece & dual hero buttons)
 * Zone 3: Contextual Smart Glance & Expandable Operator Console
 */
@Composable
fun HomeDashboardScreen(
    settings: UserSettings,
    status: AssistantStatus,
    audioLevel: Float,
    hasApiKey: Boolean,
    memoriesCount: Int,
    sessionsCount: Int,
    isFlashlightOn: Boolean,
    isWakeWordActive: Boolean = false,
    onToggleWakeWord: (Boolean) -> Unit = {},
    onStatusPreviewSelect: (AssistantStatus) -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToControls: () -> Unit,
    onNavigateToApiSettings: () -> Unit,
    onNavigateToGeneralSettings: () -> Unit,
    onNavigateToVision: () -> Unit = {},
    onNavigateToDocument: () -> Unit = {},
    onNavigateToWorkflow: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToMemoryReview: () -> Unit = {},
    onNavigateToBrowser: () -> Unit = {},
    onNavigateToAgentHub: () -> Unit = {},
    onNavigateToOpticalSolver: () -> Unit = {},
    onNavigateToPolyglotDiplomat: () -> Unit = {},
    onNavigateToChargingTelemetry: () -> Unit = {},
    agenticStepLogs: List<AgenticStepLog> = emptyList(),
    isAgenticLoopRunning: Boolean = false,
    detectedBadges: List<JarvisAccessibilityService.SetOfMarkBadge> = emptyList(),
    overlayHudState: OverlayHudState = OverlayHudState(),
    activeReplyables: List<CachedNotificationReplyAction> = emptyList(),
    activeFormSession: FormFillSessionState? = null,
    lastScratchpadResult: CrossAppScratchpadResult? = null,
    isRecordingDemonstration: Boolean = false,
    recordedDemonstrationSteps: List<WorkflowAction> = emptyList(),
    pendingHighRiskGate: HighRiskGateRequest? = null,
    onRunAutonomousGoal: (String) -> Unit = {},
    onScanSetOfMarkBadges: () -> Unit = {},
    onTapSetOfMarkBadge: (Int) -> Unit = {},
    onToggleOverlayHud: (Boolean) -> Unit = {},
    onSendInlineReply: (String, String) -> Unit = { _, _ -> },
    onAutoFillScreenForm: () -> Unit = {},
    onRunCrossAppBridge: (String, String) -> Unit = { _, _ -> },
    onStartTeachableRecording: () -> Unit = {},
    onStopAndSaveTeachableSkill: (String) -> Unit = {},
    onSpeakerCall: (String) -> Unit = {},
    onSetTimerSeconds: (Int) -> Unit = {},
    onZeroClickTeleport: (String, String) -> Unit = { _, _ -> },
    onConfirmHighRiskGate: () -> Unit = {},
    onCancelHighRiskGate: () -> Unit = {},
    onVoiceUndoRollback: () -> Unit = {}
) {
    val themeColors = JarvisTheme.colors
    var quickCommandText by rememberSaveable { mutableStateOf("") }
    var showFullAgenticTools by rememberSaveable { mutableStateOf(false) }

    val statusDotColor = when (status) {
        AssistantStatus.IDLE -> themeColors.accent
        AssistantStatus.LISTENING -> themeColors.statusEmerald
        AssistantStatus.THINKING -> themeColors.statusAmber
        AssistantStatus.SPEAKING -> themeColors.primary
    }

    val assistantHinglishGreeting = when (status) {
        AssistantStatus.IDLE -> "Namaste, sir! Main ${settings.assistantName.ifBlank { "JARVIS" }} hoon. Sabhi neural subsystems online hain."
        AssistantStatus.LISTENING -> "Ji, main sun raha hoon. Aap boliye..."
        AssistantStatus.THINKING -> "Ek second, main aapki request aur memories process kar raha hoon..."
        AssistantStatus.SPEAKING -> "Transmitting response..."
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_dashboard_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ====================================================================
        // ZONE 1: DYNAMIC STATUS ISLAND (Top Spatial Bar)
        // ====================================================================
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Dynamic Island Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(themeColors.surface.copy(alpha = if (themeColors.isDark) 0.88f else 0.94f))
                            .border(
                                width = 1.2.dp,
                                brush = Brush.horizontalGradient(
                                    listOf(themeColors.glassRimTop, themeColors.borderHighlight, themeColors.glassRimBottom)
                                ),
                                shape = RoundedCornerShape(999.dp)
                            )
                            .clickable(onClick = onNavigateToApiSettings)
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                            .testTag("home_dynamic_island"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.jarvis_app_logo),
                            contentDescription = "Jarvis AI Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(7.dp))
                        )
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (hasApiKey) themeColors.statusEmerald else themeColors.statusAmber)
                        )
                        Text(
                            text = "${settings.assistantName.ifBlank { "JARVIS" }.uppercase()} OS",
                            style = MaterialTheme.typography.labelMedium,
                            color = themeColors.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(statusDotColor.copy(alpha = 0.20f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = status.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = statusDotColor,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.5.sp
                            )
                        }
                    }

                    // Right Actions: Wake Word Pill, Notifications Bell, Settings Cog
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Wake Word Compact Pill
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isWakeWordActive) themeColors.statusEmerald.copy(alpha = 0.18f)
                                    else themeColors.surface.copy(alpha = 0.70f)
                                )
                                .border(
                                    1.dp,
                                    if (isWakeWordActive) themeColors.statusEmerald.copy(alpha = 0.65f)
                                    else themeColors.border,
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = if (isWakeWordActive) themeColors.statusEmerald else themeColors.textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Wake",
                                style = MaterialTheme.typography.labelSmall,
                                color = themeColors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Switch(
                                checked = isWakeWordActive,
                                onCheckedChange = onToggleWakeWord,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = themeColors.statusEmerald,
                                    uncheckedThumbColor = themeColors.textMuted,
                                    uncheckedTrackColor = themeColors.surfaceElevated
                                ),
                                modifier = Modifier
                                    .size(width = 38.dp, height = 24.dp)
                                    .testTag("home_wake_word_switch")
                            )
                        }

                        // Notifications Icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(themeColors.surface.copy(alpha = 0.85f))
                                .border(1.dp, themeColors.border, RoundedCornerShape(13.dp))
                                .clickable(onClick = onNavigateToNotifications)
                                .testTag("home_btn_notifications"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = themeColors.textPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Settings Icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(themeColors.surface.copy(alpha = 0.85f))
                                .border(1.dp, themeColors.border, RoundedCornerShape(13.dp))
                                .clickable(onClick = onNavigateToGeneralSettings)
                                .testTag("home_btn_general_settings"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = themeColors.textPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }

        // ====================================================================
        // OPTIONAL MISSING API KEY BANNER (Clean Luxury Amber Tone)
        // ====================================================================
        if (!hasApiKey) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    themeColors.statusAmber.copy(alpha = 0.18f),
                                    themeColors.surfaceElevated.copy(alpha = 0.90f)
                                )
                            )
                        )
                        .border(1.2.dp, themeColors.statusAmber.copy(alpha = 0.65f), RoundedCornerShape(22.dp))
                        .clickable(onClick = onNavigateToGeneralSettings)
                        .padding(16.dp)
                        .testTag("missing_api_key_banner")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(themeColors.statusAmber.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = themeColors.statusAmber,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Activate Gemini Cloud Brain",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = themeColors.textPrimary,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Add your API key in Settings to unlock Voice & Agentic AI",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = themeColors.textSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(themeColors.accent)
                                .clickable(onClick = onNavigateToApiSettings)
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                                .testTag("home_btn_api_settings")
                        ) {
                            Text(
                                text = "Setup",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // ====================================================================
        // ZONE 2: SPATIAL COCKPIT CORE & OMNI-COMMAND DISPATCH
        // Calm Quantum Pulse Core, Greeting, Omni-Search & Dual Large Hero Buttons
        // ====================================================================
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Interactive Quantum Pulse Core
                val activeHudMode by com.jarvisai.assistant.ui.components.ProceduralHudModeState.currentMode.collectAsState()
                QuantumPulseCore(
                    status = status,
                    audioLevel = audioLevel,
                    size = 186.dp,
                    hudMode = activeHudMode,
                    onClick = onNavigateToVoice
                )

                // Ultron 60 FPS Procedural HUD Mode Selector Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .testTag("home_procedural_hud_strip"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.jarvisai.assistant.model.ProceduralHudMode.entries.forEach { mode ->
                        val selected = activeHudMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (selected) themeColors.accent.copy(alpha = 0.24f)
                                    else themeColors.surface.copy(alpha = 0.72f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) themeColors.accent else themeColors.border,
                                    shape = RoundedCornerShape(999.dp)
                                )
                                .clickable {
                                    com.jarvisai.assistant.ui.components.ProceduralHudModeState.setMode(mode)
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("hud_mode_${mode.name.lowercase()}")
                        ) {
                            Text(
                                text = mode.hudBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) themeColors.accent else themeColors.textSecondary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                }

                // Editorial Status & Bilingual Hinglish Greeting
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${status.hudCode} // ${activeHudMode.hudBadge}",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusDotColor,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = assistantHinglishGreeting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = themeColors.textSecondary,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // Omni-Command Search Bar
                OutlinedTextField(
                    value = quickCommandText,
                    onValueChange = { quickCommandText = it },
                    placeholder = {
                        Text(
                            text = "Ask JARVIS or type any command...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = themeColors.textMuted
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = themeColors.textPrimary,
                        unfocusedTextColor = themeColors.textPrimary,
                        focusedContainerColor = themeColors.surface.copy(alpha = 0.90f),
                        unfocusedContainerColor = themeColors.surface.copy(alpha = 0.75f),
                        focusedBorderColor = themeColors.accent,
                        unfocusedBorderColor = themeColors.border,
                        cursorColor = themeColors.accent
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = themeColors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            // Mic Button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(themeColors.surfaceElevated)
                                    .clickable { onNavigateToVoice() }
                                    .testTag("home_mic_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice Call",
                                    tint = themeColors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Send Button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(themeColors.accent)
                                    .clickable {
                                        if (quickCommandText.isNotBlank()) {
                                            onRunAutonomousGoal(quickCommandText.trim())
                                            quickCommandText = ""
                                        } else {
                                            onNavigateToVoice()
                                        }
                                    }
                                    .testTag("home_quick_command_send"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (quickCommandText.isNotBlank()) {
                                        Icons.AutoMirrored.Filled.Send
                                    } else {
                                        Icons.AutoMirrored.Filled.ArrowForward
                                    },
                                    contentDescription = "Submit",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (quickCommandText.isNotBlank()) {
                                onRunAutonomousGoal(quickCommandText.trim())
                                quickCommandText = ""
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_quick_command_input")
                )

                // 2 Large Ergonomic Quick Dispatch Hero Buttons (56dp height)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Button 1: Start Live Call (Filled Theme Accent Gradient)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(themeColors.accent, themeColors.primary)
                                )
                            )
                            .border(
                                width = 1.2.dp,
                                color = Color.White.copy(alpha = 0.50f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable(onClick = onNavigateToVoice)
                            .padding(horizontal = 12.dp)
                            .testTag("hero_btn_start_voice")
                            .testTag("home_btn_voice"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Start Live Call",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "FaceTime AI Companion",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }

                    // Button 2: AI Text Studio (Satin Translucent Surface)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(themeColors.surface.copy(alpha = 0.92f))
                            .border(
                                width = 1.2.dp,
                                color = themeColors.border,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable(onClick = onNavigateToChat)
                            .padding(horizontal = 12.dp)
                            .testTag("hero_btn_open_chat")
                            .testTag("home_btn_chat"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(themeColors.surfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    tint = themeColors.accent,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "AI Text Studio",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = themeColors.textPrimary,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Multimodal & Vision",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = themeColors.textSecondary,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }
                }

                // Compact Status Simulation Mode Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple(AssistantStatus.IDLE, "IDLE", themeColors.accent),
                        Triple(AssistantStatus.LISTENING, "LISTEN", themeColors.statusEmerald),
                        Triple(AssistantStatus.THINKING, "THINK", themeColors.statusAmber),
                        Triple(AssistantStatus.SPEAKING, "SPEAK", themeColors.primary)
                    ).forEach { (st, label, chipColor) ->
                        val selected = status == st
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) chipColor.copy(alpha = 0.22f)
                                    else themeColors.surface.copy(alpha = 0.50f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) chipColor else themeColors.border,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onStatusPreviewSelect(st) }
                                .padding(vertical = 6.dp)
                                .testTag("status_chip_${st.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) chipColor else themeColors.textSecondary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        // ====================================================================
        // ZONE 2B: ABUZAR-ANSARII/JARVIS (PRIVATE-AGENT) 3D HOLOGRAM & SENSOR SUITE
        // ====================================================================
        item {
            com.jarvisai.assistant.ui.components.AbuZarPrivateAgentCommandDeck()
        }

        // ====================================================================
        // ZONE 3: CONTEXTUAL SMART GLANCE CARD & QUICK TILES
        // ====================================================================
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Section Title
                Text(
                    text = "NEURAL CAPABILITIES",
                    style = MaterialTheme.typography.labelMedium,
                    color = themeColors.textMuted,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                // 4 Clean Glance Tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CockpitQuickTile(
                        title = "Vision AI",
                        subtitle = "Camera & OCR",
                        icon = Icons.Default.CameraAlt,
                        accentColor = themeColors.accent,
                        onClick = onNavigateToVision,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_btn_vision")
                    )
                    CockpitQuickTile(
                        title = "Memory Vault",
                        subtitle = "$memoriesCount Facts",
                        icon = Icons.Default.Memory,
                        accentColor = themeColors.statusEmerald,
                        onClick = onNavigateToMemory,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_btn_memory")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CockpitQuickTile(
                        title = "CUA Agent",
                        subtitle = "Autonomous UI",
                        icon = Icons.Default.Terminal,
                        accentColor = themeColors.primary,
                        onClick = onNavigateToAgentHub,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_btn_agent_hub")
                    )
                    CockpitQuickTile(
                        title = if (isFlashlightOn) "Torch ON" else "Device Tools",
                        subtitle = "Hardware Hub",
                        icon = Icons.Default.Build,
                        accentColor = themeColors.statusAmber,
                        onClick = onNavigateToControls,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_btn_controls")
                    )
                }

                // 3 Cyber-Tactical Feature Tiles: Optical Solver, Polyglot Diplomat, Arc Reactor Telemetry
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CockpitQuickTile(
                        title = "Optical Solver",
                        subtitle = "🧮 Math, Code & Bill Split",
                        icon = Icons.Default.Calculate,
                        accentColor = themeColors.accent,
                        onClick = onNavigateToOpticalSolver,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_btn_optical_solver")
                    )
                    CockpitQuickTile(
                        title = "Polyglot Diplomat",
                        subtitle = "🌐 2-Way Live Translator",
                        icon = Icons.Default.Translate,
                        accentColor = themeColors.statusEmerald,
                        onClick = onNavigateToPolyglotDiplomat,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_btn_polyglot_diplomat")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CockpitQuickTile(
                        title = "Arc Reactor Dock",
                        subtitle = "🔋 Live Watts, mV & Thermal HUD",
                        icon = Icons.Default.BatteryChargingFull,
                        accentColor = themeColors.primary,
                        onClick = onNavigateToChargingTelemetry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_btn_charging_telemetry")
                    )
                }

                // Horizontal Mini Action Strip (Workflows, Doc Reader, Browser, Timeline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CockpitPillAction(
                        label = "Workflows",
                        icon = Icons.Default.AutoMode,
                        onClick = onNavigateToWorkflow,
                        modifier = Modifier.testTag("home_btn_workflow")
                    )
                    CockpitPillAction(
                        label = "Doc Reader",
                        icon = Icons.Default.Description,
                        onClick = onNavigateToDocument,
                        modifier = Modifier.testTag("home_btn_document")
                    )
                    CockpitPillAction(
                        label = "Web Agent",
                        icon = Icons.Default.Public,
                        onClick = onNavigateToBrowser,
                        modifier = Modifier.testTag("home_btn_browser")
                    )
                    CockpitPillAction(
                        label = "Timeline",
                        icon = Icons.Default.History,
                        onClick = onNavigateToMemoryReview,
                        modifier = Modifier.testTag("home_btn_memory_review")
                    )
                }

                // Expandable Autonomous Console Header Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(themeColors.surface.copy(alpha = 0.92f))
                        .border(1.2.dp, themeColors.border, RoundedCornerShape(22.dp))
                        .clickable { showFullAgenticTools = !showFullAgenticTools }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(themeColors.primary.copy(alpha = 0.20f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = themeColors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "10 Autonomous CUA Engines",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = themeColors.textPrimary,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(
                                                if (isAgenticLoopRunning) themeColors.statusEmerald.copy(alpha = 0.22f)
                                                else themeColors.surfaceElevated
                                            )
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isAgenticLoopRunning) "RUNNING" else "STANDBY",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isAgenticLoopRunning) themeColors.statusEmerald else themeColors.textMuted,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                                Text(
                                    text = "Set-of-Mark Vision • Form Auto-Fill • Cross-App Bridge",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = themeColors.textSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        Icon(
                            imageVector = if (showFullAgenticTools) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = themeColors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Collapsible Full Agentic Console
                AnimatedVisibility(
                    visible = showFullAgenticTools || isAgenticLoopRunning || pendingHighRiskGate != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    JarvisAgenticCommandCenterCard(
                        agenticStepLogs = agenticStepLogs,
                        isAgenticLoopRunning = isAgenticLoopRunning,
                        detectedBadges = detectedBadges,
                        overlayHudState = overlayHudState,
                        activeReplyables = activeReplyables,
                        activeFormSession = activeFormSession,
                        lastScratchpadResult = lastScratchpadResult,
                        isRecordingDemonstration = isRecordingDemonstration,
                        recordedDemonstrationSteps = recordedDemonstrationSteps,
                        pendingHighRiskGate = pendingHighRiskGate,
                        onRunAutonomousGoal = onRunAutonomousGoal,
                        onScanSetOfMarkBadges = onScanSetOfMarkBadges,
                        onTapSetOfMarkBadge = onTapSetOfMarkBadge,
                        onToggleOverlayHud = onToggleOverlayHud,
                        onSendInlineReply = onSendInlineReply,
                        onAutoFillScreenForm = onAutoFillScreenForm,
                        onRunCrossAppBridge = onRunCrossAppBridge,
                        onStartTeachableRecording = onStartTeachableRecording,
                        onStopAndSaveTeachableSkill = onStopAndSaveTeachableSkill,
                        onSpeakerCall = onSpeakerCall,
                        onSetTimerSeconds = onSetTimerSeconds,
                        onZeroClickTeleport = onZeroClickTeleport,
                        onConfirmHighRiskGate = onConfirmHighRiskGate,
                        onCancelHighRiskGate = onCancelHighRiskGate,
                        onVoiceUndoRollback = onVoiceUndoRollback
                    )
                }
            }
        }
    }
}

@Composable
private fun CockpitQuickTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = JarvisTheme.colors

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(themeColors.surface.copy(alpha = 0.90f))
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    listOf(themeColors.glassRimTop, accentColor.copy(alpha = 0.45f), themeColors.glassRimBottom)
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.18f))
                    .border(1.dp, accentColor.copy(alpha = 0.40f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = themeColors.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = themeColors.textSecondary,
                    fontSize = 11.5.sp
                )
            }
        }
    }
}

@Composable
private fun CockpitPillAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = JarvisTheme.colors

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(themeColors.surface.copy(alpha = 0.85f))
            .border(1.dp, themeColors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = themeColors.accent,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = themeColors.textPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}
