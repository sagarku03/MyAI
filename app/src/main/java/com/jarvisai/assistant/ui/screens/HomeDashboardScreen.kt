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
        AssistantStatus.IDLE -> "Ready for commands, terminal tasks, or voice control."
        AssistantStatus.LISTENING -> "Listening..."
        AssistantStatus.THINKING -> "Processing request and context..."
        AssistantStatus.SPEAKING -> "Responding..."
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_dashboard_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ====================================================================
        // ZONE 1: WORKSTATION STATUS BAR
        // ====================================================================
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(themeColors.surface)
                            .border(
                                width = 1.dp,
                                color = themeColors.border,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable(onClick = onNavigateToApiSettings)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("home_dynamic_island"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.jarvis_app_logo),
                            contentDescription = "Agentra Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (hasApiKey) themeColors.statusEmerald else themeColors.statusAmber)
                        )
                        Text(
                            text = settings.assistantName.ifBlank { "Agentra" },
                            style = MaterialTheme.typography.labelLarge,
                            color = themeColors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusDotColor.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = status.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusDotColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Right Actions: Wake Word Toggle, Notifications, Settings
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isWakeWordActive) themeColors.statusEmerald.copy(alpha = 0.14f)
                                    else themeColors.surface
                                )
                                .border(
                                    1.dp,
                                    if (isWakeWordActive) themeColors.statusEmerald.copy(alpha = 0.50f)
                                    else themeColors.border,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = if (isWakeWordActive) themeColors.statusEmerald else themeColors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Wake",
                                style = MaterialTheme.typography.labelSmall,
                                color = themeColors.textPrimary,
                                fontWeight = FontWeight.Medium
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
                                    .size(width = 40.dp, height = 24.dp)
                                    .testTag("home_wake_word_switch")
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(themeColors.surface)
                                .border(1.dp, themeColors.border, RoundedCornerShape(8.dp))
                                .clickable(onClick = onNavigateToNotifications)
                                .testTag("home_btn_notifications"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = themeColors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(themeColors.surface)
                                .border(1.dp, themeColors.border, RoundedCornerShape(8.dp))
                                .clickable(onClick = onNavigateToGeneralSettings)
                                .testTag("home_btn_general_settings"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = themeColors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        if (!hasApiKey) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(themeColors.surfaceElevated)
                        .border(1.dp, themeColors.statusAmber.copy(alpha = 0.50f), RoundedCornerShape(12.dp))
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
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(themeColors.statusAmber.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = themeColors.statusAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Configure Gemini API Key",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = themeColors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Add a key in Settings to enable cloud voice and agent tasks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = themeColors.textSecondary,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(themeColors.accent)
                                .clickable(onClick = onNavigateToApiSettings)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("home_btn_api_settings")
                        ) {
                            Text(
                                text = "Setup",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
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
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (selected) themeColors.accent.copy(alpha = 0.16f)
                                    else themeColors.surface
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) themeColors.accent else themeColors.border,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    com.jarvisai.assistant.ui.components.ProceduralHudModeState.setMode(mode)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("hud_mode_${mode.name.lowercase()}")
                        ) {
                            Text(
                                text = mode.hudBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) themeColors.accent else themeColors.textSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${status.hudCode} // ${activeHudMode.hudBadge}",
                        style = MaterialTheme.typography.labelSmall,
                        color = themeColors.textMuted,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = assistantHinglishGreeting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = themeColors.textSecondary,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // Omni-Command Input
                OutlinedTextField(
                    value = quickCommandText,
                    onValueChange = { quickCommandText = it },
                    placeholder = {
                        Text(
                            text = "Run a command or task...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = themeColors.textMuted
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = themeColors.textPrimary,
                        unfocusedTextColor = themeColors.textPrimary,
                        focusedContainerColor = themeColors.surfaceElevated,
                        unfocusedContainerColor = themeColors.surface,
                        focusedBorderColor = themeColors.accent,
                        unfocusedBorderColor = themeColors.border,
                        cursorColor = themeColors.accent
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = themeColors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
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

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
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

                // Primary Actions: Live Voice + Text Studio
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(themeColors.accent)
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
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Live Voice",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Hands-free stream",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(themeColors.surface)
                            .border(
                                width = 1.dp,
                                color = themeColors.border,
                                shape = RoundedCornerShape(12.dp)
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
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = themeColors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Text Chat",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = themeColors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Code, files & vision",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = themeColors.textSecondary
                                )
                            }
                        }
                    }
                }

                // State Preview Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple(AssistantStatus.IDLE, "Idle", themeColors.accent),
                        Triple(AssistantStatus.LISTENING, "Listen", themeColors.statusEmerald),
                        Triple(AssistantStatus.THINKING, "Think", themeColors.statusAmber),
                        Triple(AssistantStatus.SPEAKING, "Speak", themeColors.primary)
                    ).forEach { (st, label, chipColor) ->
                        val selected = status == st
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) chipColor.copy(alpha = 0.16f)
                                    else themeColors.surface
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) chipColor else themeColors.border,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onStatusPreviewSelect(st) }
                                .padding(vertical = 8.dp)
                                .testTag("status_chip_${st.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) chipColor else themeColors.textSecondary,
                                fontWeight = FontWeight.Medium
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Capabilities",
                    style = MaterialTheme.typography.labelMedium,
                    color = themeColors.textMuted,
                    fontWeight = FontWeight.SemiBold,
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

                // Feature Tiles: Optical Solver, Polyglot Diplomat, Charging Telemetry
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CockpitQuickTile(
                        title = "Optical Solver",
                        subtitle = "Math, code & receipt math",
                        icon = Icons.Default.Calculate,
                        accentColor = themeColors.accent,
                        onClick = onNavigateToOpticalSolver,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_btn_optical_solver")
                    )
                    CockpitQuickTile(
                        title = "Live Translator",
                        subtitle = "Two-way voice translation",
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CockpitQuickTile(
                        title = "Charging Telemetry",
                        subtitle = "Live watts, mV & thermal stats",
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(themeColors.cardSurface)
                        .border(1.dp, themeColors.cardBorder, RoundedCornerShape(12.dp))
                        .clickable { showFullAgenticTools = !showFullAgenticTools }
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(themeColors.surfaceElevated)
                                    .border(1.dp, themeColors.border, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = themeColors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Autonomous Screen Engines",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = themeColors.textPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isAgenticLoopRunning) themeColors.statusEmerald.copy(alpha = 0.16f)
                                                else themeColors.surfaceElevated
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isAgenticLoopRunning) "Running" else "Standby",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isAgenticLoopRunning) themeColors.statusEmerald else themeColors.textMuted,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                Text(
                                    text = "Set-of-Mark vision, form auto-fill & cross-app bridge",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = themeColors.textSecondary
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
            .clip(RoundedCornerShape(12.dp))
            .background(themeColors.cardSurface)
            .border(
                width = 1.dp,
                color = themeColors.cardBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(themeColors.surfaceElevated)
                    .border(1.dp, themeColors.border, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = themeColors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = themeColors.textSecondary
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
            .clip(RoundedCornerShape(8.dp))
            .background(themeColors.surface)
            .border(1.dp, themeColors.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            fontWeight = FontWeight.Medium
        )
    }
}
