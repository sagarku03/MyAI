package com.jarvisai.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisGlassBorder
import com.example.ui.theme.JarvisGlassSurface
import com.example.ui.theme.JarvisNeonCyan
import com.example.ui.theme.JarvisNeonGreen
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet
import com.example.ui.theme.JarvisVioletBright
import com.example.ui.theme.StatusListeningEmerald
import com.example.ui.theme.StatusThinkingAmber
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.ui.components.JarvisAIOrb
import com.jarvisai.assistant.ui.components.JarvisGlassPanel
import com.jarvisai.assistant.ui.components.JarvisMultiWavePerformanceChart
import com.jarvisai.assistant.ui.components.JarvisSparklineWave
import com.jarvisai.assistant.ui.components.JarvisSquircleQuickAction
import com.jarvisai.assistant.ui.components.JarvisStatusBadge

@OptIn(ExperimentalLayoutApi::class)
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
    onStatusPreviewSelect: (AssistantStatus?) -> Unit,
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
    agenticStepLogs: List<com.jarvisai.assistant.service.AgenticStepLog> = emptyList(),
    isAgenticLoopRunning: Boolean = false,
    detectedBadges: List<com.jarvisai.assistant.service.JarvisAccessibilityService.SetOfMarkBadge> = emptyList(),
    overlayHudState: com.jarvisai.assistant.service.OverlayHudState = com.jarvisai.assistant.service.OverlayHudState(),
    activeReplyables: List<com.jarvisai.assistant.service.CachedNotificationReplyAction> = emptyList(),
    activeFormSession: com.jarvisai.assistant.service.FormFillSessionState? = null,
    lastScratchpadResult: com.jarvisai.assistant.service.CrossAppScratchpadResult? = null,
    isRecordingDemonstration: Boolean = false,
    recordedDemonstrationSteps: List<com.jarvisai.assistant.model.WorkflowAction> = emptyList(),
    pendingHighRiskGate: com.jarvisai.assistant.service.HighRiskGateRequest? = null,
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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. WEALTHWAVE & WALLET INSPIRED TOP HEADER
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Brand Mark + App Name (Image 2 WealthWave style)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFEC4899),
                                        JarvisViolet,
                                        JarvisNeonCyan
                                    )
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    Column {
                        Text(
                            text = settings.assistantName,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${settings.language.displayName} • ${settings.voicePersona} Voice",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextSecondary,
                                fontSize = 10.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (isWakeWordActive) JarvisNeonGreen.copy(alpha = 0.16f) else Color(0xFF1E293B))
                                    .border(1.dp, if (isWakeWordActive) JarvisNeonGreen else Color(0xFF334155), RoundedCornerShape(999.dp))
                                    .clickable { onToggleWakeWord(!isWakeWordActive) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("wake_word_toggle_badge")
                            ) {
                                Text(
                                    text = if (isWakeWordActive) "Wake Word ON" else "Wake Word OFF",
                                    color = if (isWakeWordActive) JarvisNeonGreen else Color.LightGray,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Right Status Pill + Avatar Orb
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    JarvisStatusBadge(status = status)

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(JarvisViolet, JarvisElectricBlue)
                                )
                            )
                            .border(1.5.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                            .clickable(onClick = onNavigateToGeneralSettings),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = settings.assistantName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // 2. POLY ULTIMATE STYLE API KEY BANNER (IF MISSING)
        if (!hasApiKey) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF3B1D5A),
                                    Color(0xFF1F1638),
                                    Color(0xFF141829)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFFF472B6), JarvisVioletBright, JarvisNeonCyan)
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clickable(onClick = onNavigateToApiSettings)
                        .padding(18.dp)
                        .testTag("missing_api_key_banner")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = Color(0xFFF472B6),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Activate Gemini Neural Key",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFFF9A8D4),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = "Unlock real-time Bidi Live Voice, Screen Agent & Neural Chat",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Poly Ultimate pastel pink-purple pill button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFC084FC), Color(0xFFF472B6))
                                    )
                                )
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = "Setup Key",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF12091C),
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // 3. MONEX PEEK-A-BOO NEON LIME CARD + WEALTHWAVE FROSTED GLASS HERO CARD
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("arc_reactor_card")
            ) {
                // Top Peek-a-Boo Neon Lime-Green Card (Directly from Image 5 Monex!)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .align(Alignment.CenterHorizontally)
                        .height(38.dp)
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    JarvisNeonGreen,
                                    Color(0xFF6EE7B7),
                                    JarvisNeonCyan
                                )
                            )
                        )
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "JARVIS CORE // ${settings.voiceModel.removePrefix("models/").take(18).uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF050A0E),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        )
                        Text(
                            text = if (hasApiKey) "KEYSTORE VERIFIED" else "STANDBY MODE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF050A0E),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        )
                    }
                }

                // Main Frosted Glass Hero Card (Directly from Image 2 WealthWave!)
                JarvisGlassPanel(
                    accentColor = JarvisNeonCyan,
                    cornerRadius = 26.dp,
                    contentPadding = PaddingValues(20.dp),
                    modifier = Modifier.offset(y = (-6).dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Top Greeting Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Hello, Commander",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = status.hudCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisNeonCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }

                        // Center Split: Left Huge Metric Readout + Right AI Orb & Sparkline Wave
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1.1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Neural Core Status:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextSecondary
                                )
                                Text(
                                    text = status.label.uppercase(),
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.5).sp
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "+99.8%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = JarvisNeonGreen,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "Bidi Audio Ready",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JarvisTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Right: Interactive JarvisAIOrb + Glowing Bezier Sparkline
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                JarvisSparklineWave(
                                    points = listOf(0.22f, 0.38f, 0.32f, 0.56f, 0.48f, 0.78f, 0.92f),
                                    lineColor = JarvisNeonCyan,
                                    modifier = Modifier
                                        .width(68.dp)
                                        .height(44.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clickable(onClick = onNavigateToVoice),
                                    contentAlignment = Alignment.Center
                                ) {
                                    JarvisAIOrb(
                                        status = status,
                                        audioLevel = audioLevel,
                                        modifier = Modifier
                                            .size(80.dp)
                                            .testTag("home_arc_reactor")
                                    )
                                }
                            }
                        }

                        // Interactive Status Mode Pills (Preserving status_chip_* testTags)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AssistantStatus.entries.forEach { mode ->
                                val isSelected = status == mode
                                val chipColor = when (mode) {
                                    AssistantStatus.IDLE -> JarvisNeonCyan
                                    AssistantStatus.LISTENING -> JarvisNeonGreen
                                    AssistantStatus.THINKING -> StatusThinkingAmber
                                    AssistantStatus.SPEAKING -> JarvisVioletBright
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            if (isSelected) chipColor.copy(alpha = 0.20f)
                                            else Color(0xFF151B2D)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) chipColor else Color.White.copy(alpha = 0.10f),
                                            shape = RoundedCornerShape(999.dp)
                                        )
                                        .clickable { onStatusPreviewSelect(mode) }
                                        .padding(vertical = 7.dp)
                                        .testTag("status_chip_${mode.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) chipColor else JarvisTextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. WEALTHWAVE 4 GLOWING SQUIRCLE QUICK-ACTION BUTTONS ROW (Image 2: Send | Receive | Pay | Invest)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                JarvisSquircleQuickAction(
                    label = "Live Voice",
                    icon = Icons.Default.Mic,
                    accentColor = Color(0xFFF472B6), // Pink/Magenta squircle (WealthWave "Send")
                    onClick = onNavigateToVoice,
                    testTag = "btn_voice_assistant"
                )
                JarvisSquircleQuickAction(
                    label = "Neural Chat",
                    icon = Icons.AutoMirrored.Filled.Chat,
                    accentColor = StatusListeningEmerald, // Emerald/Teal squircle (WealthWave "Receive")
                    onClick = onNavigateToChat,
                    testTag = "btn_text_chat"
                )
                JarvisSquircleQuickAction(
                    label = "Memory Vault",
                    icon = Icons.Default.Memory,
                    accentColor = JarvisVioletBright, // Purple/Blue squircle (WealthWave "Pay")
                    onClick = onNavigateToMemory,
                    testTag = "btn_memory_center"
                )
                JarvisSquircleQuickAction(
                    label = "Phone Matrix",
                    icon = Icons.Default.Build,
                    accentColor = JarvisNeonCyan, // Cyan/Blue squircle (WealthWave "Invest")
                    onClick = onNavigateToControls,
                    testTag = "btn_phone_controls"
                )
            }
        }

        // 5. WEALTHWAVE "PORTFOLIO BREAKDOWN" + WALLET "SPENDING" SIDE-BY-SIDE CARDS (Image 2 & Image 4)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Neural Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Card: Memory Vault + Segmented Multi-Color Pill Bar (Image 4) + Sparkline (Image 2)
                    JarvisGlassPanel(
                        modifier = Modifier.weight(1f),
                        accentColor = JarvisNeonGreen,
                        cornerRadius = 22.dp,
                        contentPadding = PaddingValues(16.dp),
                        onClick = onNavigateToMemory
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Memories",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextSecondary
                                )
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = JarvisNeonGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "$memoriesCount",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = if (settings.memoryEnabled) "+Auto Recall" else "Paused",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JarvisNeonGreen,
                                        fontSize = 11.sp
                                    )
                                }

                                JarvisSparklineWave(
                                    points = listOf(0.20f, 0.35f, 0.30f, 0.62f, 0.54f, 0.85f),
                                    lineColor = JarvisNeonGreen,
                                    modifier = Modifier
                                        .width(56.dp)
                                        .height(28.dp),
                                    strokeWidth = 2.dp
                                )
                            }

                            // Image 4 "Spending" Segmented Multi-Color Pill Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(0.38f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(JarvisViolet)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(0.24f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFFF43F5E))
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(0.16f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(StatusListeningEmerald)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(0.14f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(StatusThinkingAmber)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(0.08f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(JarvisNeonCyan)
                                )
                            }
                        }
                    }

                    // Right Card: Sessions & Models + Overlapping Badges (Image 4) + Sparkline (Image 2)
                    JarvisGlassPanel(
                        modifier = Modifier.weight(1f),
                        accentColor = JarvisVioletBright,
                        cornerRadius = 22.dp,
                        contentPadding = PaddingValues(16.dp),
                        onClick = onNavigateToChat
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Conversations",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextSecondary
                                )
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = JarvisVioletBright,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "$sessionsCount",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = settings.language.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JarvisVioletBright,
                                        fontSize = 11.sp
                                    )
                                }

                                JarvisSparklineWave(
                                    points = listOf(0.25f, 0.48f, 0.36f, 0.72f, 0.64f, 0.90f),
                                    lineColor = JarvisVioletBright,
                                    modifier = Modifier
                                        .width(56.dp)
                                        .height(28.dp),
                                    strokeWidth = 2.dp
                                )
                            }

                            // Image 4 "Cash Back" style mini pill badges
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                MiniModelPill("G3", JarvisViolet)
                                MiniModelPill("LIVE", JarvisNeonCyan)
                                MiniModelPill("TTS", StatusListeningEmerald)
                                MiniModelPill(" torch:${if (isFlashlightOn) "ON" else "OFF"}", StatusThinkingAmber)
                            }
                        }
                    }
                }
            }
        }

        // 6. IMAGE 4 ASYMMETRIC BENTO ROW (2 Stacked Mini Square Buttons on Left + 2 Bento Cards on Right)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: 2 Stacked Mini Square Icon Buttons (btn_api_settings & btn_general_settings)
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF151B2D))
                            .border(1.dp, JarvisNeonCyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .clickable(onClick = onNavigateToApiSettings)
                            .testTag("btn_api_settings"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "API Settings",
                            tint = JarvisNeonCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF151B2D))
                            .border(1.dp, JarvisVioletBright.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .clickable(onClick = onNavigateToGeneralSettings)
                            .testTag("btn_general_settings"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "System Settings",
                            tint = JarvisVioletBright,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Middle Bento Card: Live Voice & Screen Agent
                JarvisGlassPanel(
                    modifier = Modifier
                        .weight(1f)
                        .height(118.dp),
                    accentColor = JarvisNeonCyan,
                    cornerRadius = 22.dp,
                    contentPadding = PaddingValues(14.dp),
                    onClick = onNavigateToVoice
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(JarvisNeonCyan.copy(alpha = 0.16f))
                                .border(1.dp, JarvisNeonCyan.copy(alpha = 0.4f), RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = JarvisNeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Live Voice & Agent",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Real-time Call + Screen",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Right Bento Card: All Services / API & Models
                JarvisGlassPanel(
                    modifier = Modifier
                        .weight(1f)
                        .height(118.dp),
                    accentColor = JarvisViolet,
                    cornerRadius = 22.dp,
                    contentPadding = PaddingValues(14.dp),
                    onClick = onNavigateToApiSettings
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(JarvisViolet.copy(alpha = 0.18f))
                                .border(1.dp, JarvisVioletBright.copy(alpha = 0.4f), RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = JarvisVioletBright,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "AI Models & Key",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = settings.textModel.removePrefix("models/"),
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 6.5. NEURAL AI TOOLS ROW (Vision, Docs, Routines, Alerts, Memory Timeline)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Neural AI Instruments",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    JarvisGlassPanel(
                        modifier = Modifier
                            .weight(1f)
                            .height(104.dp),
                        accentColor = JarvisNeonCyan,
                        cornerRadius = 20.dp,
                        contentPadding = PaddingValues(12.dp),
                        onClick = onNavigateToVision
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = JarvisNeonCyan, modifier = Modifier.size(22.dp))
                            Column {
                                Text("AI Vision", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Camera & Photo", style = MaterialTheme.typography.labelSmall, color = JarvisTextSecondary, fontSize = 9.sp)
                            }
                        }
                    }

                    JarvisGlassPanel(
                        modifier = Modifier
                            .weight(1f)
                            .height(104.dp),
                        accentColor = JarvisVioletBright,
                        cornerRadius = 20.dp,
                        contentPadding = PaddingValues(12.dp),
                        onClick = onNavigateToDocument
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = JarvisVioletBright, modifier = Modifier.size(22.dp))
                            Column {
                                Text("AI Docs", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("PDF & Text Audit", style = MaterialTheme.typography.labelSmall, color = JarvisTextSecondary, fontSize = 9.sp)
                            }
                        }
                    }

                    JarvisGlassPanel(
                        modifier = Modifier
                            .weight(1f)
                            .height(104.dp),
                        accentColor = JarvisNeonGreen,
                        cornerRadius = 20.dp,
                        contentPadding = PaddingValues(12.dp),
                        onClick = onNavigateToWorkflow
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(Icons.Default.AutoMode, contentDescription = null, tint = JarvisNeonGreen, modifier = Modifier.size(22.dp))
                            Column {
                                Text("Routines", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Workflows", style = MaterialTheme.typography.labelSmall, color = JarvisTextSecondary, fontSize = 9.sp)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    JarvisGlassPanel(
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp),
                        accentColor = Color(0xFFF59E0B),
                        cornerRadius = 18.dp,
                        contentPadding = PaddingValues(12.dp),
                        onClick = onNavigateToNotifications
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(24.dp))
                            Column {
                                Text("Alerts Briefing", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Notification AI Digest", style = MaterialTheme.typography.labelSmall, color = JarvisTextSecondary, fontSize = 9.sp)
                            }
                        }
                    }

                    JarvisGlassPanel(
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp),
                        accentColor = Color(0xFFA855F7),
                        cornerRadius = 18.dp,
                        contentPadding = PaddingValues(12.dp),
                        onClick = onNavigateToMemoryReview
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(24.dp))
                            Column {
                                Text("Memory Audit", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Timeline & Export", style = MaterialTheme.typography.labelSmall, color = JarvisTextSecondary, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }

        // 6.8. TOP 10 AGENTIC AI MOBILE CONTROLLER CARD
        item {
            com.jarvisai.assistant.ui.components.JarvisAgenticCommandCenterCard(
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

        // 7. WEALTHWAVE "PERFORMANCE TRENDS" MULTI-WAVE CHART CARD (Directly from Image 2!)
        item {
            JarvisGlassPanel(
                accentColor = JarvisElectricBlue,
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Neural Performance Trends",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Last 7 days ⌄",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisTextSecondary
                        )
                    }

                    JarvisMultiWavePerformanceChart()
                }
            }
        }

        // 8. WEALTHWAVE & MONEX "RECENT ACTIVITY" SUBSYSTEM LIST (Directly from Image 2 & Image 5!)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active Subsystems",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "All Online",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisNeonCyan
                    )
                }

                SubsystemActivityRow(
                    title = "Gemini Bidi Live Voice",
                    subtitle = settings.voiceModel.removePrefix("models/"),
                    rightPrimary = "${settings.voicePersona} (${settings.voiceGender.displayName.substringBefore(" ")})",
                    rightSecondary = "16kHz PCM In • 24kHz Out",
                    icon = Icons.Default.Mic,
                    accent = JarvisNeonCyan,
                    onClick = onNavigateToVoice
                )

                SubsystemActivityRow(
                    title = "Neural Text & Multimodal",
                    subtitle = settings.textModel.removePrefix("models/"),
                    rightPrimary = "$sessionsCount Sessions",
                    rightSecondary = settings.language.displayName,
                    icon = Icons.AutoMirrored.Filled.Chat,
                    accent = JarvisVioletBright,
                    onClick = onNavigateToChat
                )

                SubsystemActivityRow(
                    title = "Hardware & Accessibility Matrix",
                    subtitle = "Torch, Alarms, Dialer & Screen Agent",
                    rightPrimary = if (isFlashlightOn) "Torch ON" else "Ready",
                    rightSecondary = "Native Intents",
                    icon = Icons.Default.FlashlightOn,
                    accent = StatusListeningEmerald,
                    onClick = onNavigateToControls
                )
            }
        }
    }
}

@Composable
private fun MiniModelPill(
    label: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.22f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SubsystemActivityRow(
    title: String,
    subtitle: String,
    rightPrimary: String,
    rightSecondary: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF121728).copy(alpha = 0.90f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.16f))
                    .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(21.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = rightPrimary,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = rightSecondary,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontSize = 10.sp
            )
        }
    }
}
