package com.jarvisai.assistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.antigravity.AntigravityAgentMode
import com.jarvisai.assistant.antigravity.AntigravityAutonomyPolicy
import com.jarvisai.assistant.data.remote.AntigravityModelInfo
import com.jarvisai.assistant.ui.antigravity.AntigravityPalette

/**
 * Adaptive Chat Composer:
 * - When `isAntigravityActive = true`: renders the EXACT Original Google Antigravity IDE Composer
 *   (neutral matte #222222 14dp card, "Ask anything, @ to mention, / for workflows", single bottom row:
 *   [+]  [⌃ Planning]  [⌃ Model]      [🎙] [↑])
 * - Otherwise: renders the standard AntiGem-style floating composer for Gemini API & Cactus Local.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisUnifiedCommandBar(
    promptText: String,
    onPromptChange: (String) -> Unit,
    activeModelName: String,
    activeModelId: String,
    thinkingLevelLabel: String,
    isThinkingEnabled: Boolean,
    isAntigravityLoggedIn: Boolean,
    antigravityTier: String,
    antigravityModels: List<AntigravityModelInfo>,
    isRefreshingAntigravityModels: Boolean,
    onOpenAntigravityLogin: () -> Unit,
    onRefreshAntigravityModels: () -> Unit,
    onOpenThinkingSelector: () -> Unit,
    onOpenCactusManager: () -> Unit = {},
    onModelSelect: (modelId: String, displayName: String) -> Unit,
    isLiveVoiceActive: Boolean,
    isMicMuted: Boolean,
    audioLevel: Float = 0f,
    isDictating: Boolean = false,
    dictationAudioLevel: Float = 0f,
    hasAttachments: Boolean = false,
    isGenerating: Boolean = false,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit = {},
    onMicClick: () -> Unit,
    onDictateToggleClick: () -> Unit = onMicClick,
    onVoiceStudioClick: () -> Unit = onMicClick,
    isAgyVoiceCallActive: Boolean = false,
    onAgyVoiceCallClick: (() -> Unit)? = null,
    onToolsClick: () -> Unit,
    isAntigravityActive: Boolean = false,
    antigravityAgentMode: AntigravityAgentMode = AntigravityAgentMode.PLANNING,
    antigravityAutonomyPolicy: AntigravityAutonomyPolicy = AntigravityAutonomyPolicy.DEFAULT,
    onToggleAntigravityAgentMode: () -> Unit = {},
    onSelectAntigravityAgentMode: (AntigravityAgentMode) -> Unit = { onToggleAntigravityAgentMode() },
    onCycleAntigravityAutonomyPolicy: () -> Unit = {},
    onSelectAntigravityAutonomyPolicy: (AntigravityAutonomyPolicy) -> Unit = { onCycleAntigravityAutonomyPolicy() },
    onInsertAtMention: () -> Unit = {},
    onInsertSlashCommand: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showModelMenu by remember { mutableStateOf(false) }

    val micPulseAlpha = if (isDictating) {
        val infiniteTransition = rememberInfiniteTransition(label = "MicPulse")
        val animated by infiniteTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(750, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Alpha"
        )
        animated
    } else {
        1f
    }

    val activeLevel = if (isDictating) dictationAudioLevel else audioLevel
    val dynamicWaveBoost = (activeLevel.coerceIn(0f, 1f) * 0.35f)
    val canSend = promptText.isNotBlank() || hasAttachments

    val compactModelLabel = remember(activeModelName) {
        formatCompactModelTitle(activeModelName)
    }
    val modelPillText = remember(compactModelLabel, thinkingLevelLabel, isThinkingEnabled) {
        if (isThinkingEnabled && !thinkingLevelLabel.equals("Off", ignoreCase = true)) {
            "$compactModelLabel ($thinkingLevelLabel)"
        } else {
            compactModelLabel
        }
    }
    val activeQuotaPercent = remember(activeModelId, antigravityModels) {
        resolveActiveModelQuotaPercent(activeModelId, antigravityModels)
    }
    val quotaColor = when {
        activeQuotaPercent == null -> Color(0xFF10B981)
        activeQuotaPercent <= 15 -> Color(0xFFEF4444)
        activeQuotaPercent <= 40 -> Color(0xFFF59E0B)
        else -> Color(0xFF22C55E)
    }

    val composerTargetName = remember(compactModelLabel, activeModelId) {
        when {
            activeModelId.startsWith("cactus:", ignoreCase = true) -> "Cactus Offline"
            compactModelLabel.contains("gemini", ignoreCase = true) -> "Gemini"
            compactModelLabel.contains("claude", ignoreCase = true) ||
                compactModelLabel.contains("opus", ignoreCase = true) ||
                compactModelLabel.contains("sonnet", ignoreCase = true) -> "Claude"
            compactModelLabel.contains("opencode", ignoreCase = true) -> "OpenCode"
            compactModelLabel.contains("codex", ignoreCase = true) -> "Codex"
            else -> "Agentra"
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("jarvis_unified_command_bar"),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF14171E),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isDictating -> Color(0xFF00E5FF).copy(alpha = (micPulseAlpha * 0.85f).coerceIn(0.4f, 0.9f))
                isLiveVoiceActive -> Color(0xFF00E5FF).copy(alpha = 0.65f)
                else -> Color(0xFF232834)
            }
        ),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp)
        ) {
            // Row 1: Clean Auto-Expanding Prompt Field
            OutlinedTextField(
                value = promptText,
                onValueChange = onPromptChange,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                placeholder = {
                    Text(
                        text = when {
                            isDictating -> "Listening... speak in any language"
                            isLiveVoiceActive -> if (isMicMuted) "Live Voice Muted • Type or unmute..." else "Listening to speech... (▁▂▄▆▄▂▁)"
                            else -> "Reply to $composerTargetName..."
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDictating || isLiveVoiceActive) {
                            Color(0xFF00E5FF).copy(alpha = 0.85f)
                        } else {
                            Color(0xFF8B949E)
                        }
                    )
                },
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("command_bar_text_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = Color(0xFF60A5FA)
                )
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Row 2: [+] [✦ Model (Thinking) 100% ▾]   [🎧] [⫼ Dictate] [🎙️ / ➤ / ■]
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = onToolsClick,
                            shape = CircleShape,
                            color = Color(0xFF1D212A),
                            border = BorderStroke(1.dp, Color(0xFF2B313E)),
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("command_bar_tools_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Attach photos, videos, or files",
                                    tint = Color(0xFFD1D5DB),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Box(modifier = Modifier.weight(1f, fill = false)) {
                            Surface(
                                onClick = { showModelMenu = true },
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF1A1E27),
                                border = BorderStroke(1.dp, Color(0xFF282E3B)),
                                modifier = Modifier.testTag("topbar_model_selector_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFF60A5FA),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = modelPillText,
                                        color = Color.White,
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (activeQuotaPercent != null) {
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = quotaColor.copy(alpha = 0.22f)
                                        ) {
                                            Text(
                                                text = "$activeQuotaPercent%",
                                                color = quotaColor,
                                                fontFamily = FontFamily.SansSerif,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Select Model",
                                        tint = Color(0xFF9CA3AF),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            ModelSelectorDropdown(
                                expanded = showModelMenu,
                                onDismissRequest = { showModelMenu = false },
                                thinkingLevelLabel = thinkingLevelLabel,
                                isThinkingEnabled = isThinkingEnabled,
                                isAntigravityLoggedIn = isAntigravityLoggedIn,
                                antigravityTier = antigravityTier,
                                antigravityModels = antigravityModels,
                                isRefreshingAntigravityModels = isRefreshingAntigravityModels,
                                onOpenAntigravityLogin = onOpenAntigravityLogin,
                                onRefreshAntigravityModels = onRefreshAntigravityModels,
                                onOpenThinkingSelector = onOpenThinkingSelector,
                                onOpenCactusManager = onOpenCactusManager,
                                onModelSelect = onModelSelect
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val useAgyVoiceCall = isAntigravityActive && onAgyVoiceCallClick != null
                        Surface(
                            onClick = {
                                if (useAgyVoiceCall) {
                                    onAgyVoiceCallClick?.invoke()
                                } else {
                                    onVoiceStudioClick()
                                }
                            },
                            shape = CircleShape,
                            color = if (useAgyVoiceCall && isAgyVoiceCallActive) {
                                Color(0xFF1D4ED8).copy(alpha = 0.25f)
                            } else {
                                Color(0xFF1D212A)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (useAgyVoiceCall && isAgyVoiceCallActive) Color(0xFF3B82F6) else Color(0xFF2B313E)
                            ),
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("command_bar_voice_studio_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Headset,
                                    contentDescription = when {
                                        useAgyVoiceCall && isAgyVoiceCallActive -> "Open active Antigravity voice call"
                                        useAgyVoiceCall -> "Start Antigravity voice call"
                                        else -> "Open Live Voice Studio"
                                    },
                                    tint = if (useAgyVoiceCall && isAgyVoiceCallActive) Color(0xFF60A5FA) else Color(0xFFD1D5DB),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = onDictateToggleClick,
                            shape = CircleShape,
                            color = if (isDictating) {
                                Color(0xFF00E5FF).copy(alpha = (micPulseAlpha * 0.25f + dynamicWaveBoost).coerceAtMost(0.65f))
                            } else {
                                Color(0xFF1D212A)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isDictating) Color(0xFF00E5FF) else Color(0xFF2B313E)
                            ),
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("command_bar_dictate_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isDictating) Icons.Default.Stop else Icons.Default.GraphicEq,
                                    contentDescription = if (isDictating) "Stop Voice Dictation" else "Start Voice Dictation into Text Box",
                                    tint = if (isDictating) Color(0xFF00E5FF) else Color(0xFFD1D5DB),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        when {
                            isGenerating -> {
                                Surface(
                                    onClick = onStopClick,
                                    shape = CircleShape,
                                    color = Color(0xFFEF4444),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("command_bar_stop_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = "Stop Generation",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            canSend -> {
                                Surface(
                                    onClick = onSendClick,
                                    shape = CircleShape,
                                    color = Color.White,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("command_bar_send_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send Prompt",
                                            tint = Color(0xFF0B0E14),
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                            else -> {
                                Surface(
                                    onClick = onMicClick,
                                    shape = CircleShape,
                                    color = if (isLiveVoiceActive) Color(0xFF00E5FF) else Color.White,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("command_bar_mic_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isLiveVoiceActive) Icons.Default.Stop else Icons.Default.Mic,
                                            contentDescription = if (isLiveVoiceActive) "Stop Live Voice" else "Start Live Voice",
                                            tint = Color(0xFF0B0E14),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Exact Original Google Antigravity IDE Composer Card:
 * Neutral matte #222222 box (14dp corners, 1dp #353535 border):
 * Row 1: "Ask anything, @ to mention, / for workflows"
 * Row 2: [+]  [⌃ Planning]  [⌃ Gemini 3.8 Flash]          [🎙] [↑]
 */
@Composable
private fun AntigravityOriginalComposerCard(
    promptText: String,
    onPromptChange: (String) -> Unit,
    modelPillText: String,
    activeQuotaPercent: Int?,
    quotaColor: Color,
    thinkingLevelLabel: String,
    isThinkingEnabled: Boolean,
    isAntigravityLoggedIn: Boolean,
    antigravityTier: String,
    antigravityModels: List<AntigravityModelInfo>,
    isRefreshingAntigravityModels: Boolean,
    onOpenAntigravityLogin: () -> Unit,
    onRefreshAntigravityModels: () -> Unit,
    onOpenThinkingSelector: () -> Unit,
    onOpenCactusManager: () -> Unit,
    onModelSelect: (modelId: String, displayName: String) -> Unit,
    isLiveVoiceActive: Boolean,
    isMicMuted: Boolean,
    isDictating: Boolean,
    micPulseAlpha: Float,
    canSend: Boolean,
    isGenerating: Boolean,
    agentMode: AntigravityAgentMode,
    autonomyPolicy: AntigravityAutonomyPolicy,
    onSelectAgentMode: (AntigravityAgentMode) -> Unit,
    onSelectAutonomyPolicy: (AntigravityAutonomyPolicy) -> Unit,
    onInsertAtMention: () -> Unit,
    onInsertSlashCommand: () -> Unit,
    onToolsClick: () -> Unit,
    onDictateToggleClick: () -> Unit,
    onVoiceStudioClick: () -> Unit,
    isAgyVoiceCallActive: Boolean = false,
    onAgyVoiceCallClick: (() -> Unit)? = null,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showModelMenu by remember { mutableStateOf(false) }
    var showModeMenu by remember { mutableStateOf(false) }
    var showPlusMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AntigravityPalette.Canvas)
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp)
            .testTag("jarvis_unified_command_bar")
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = AntigravityPalette.SurfaceCard,
            border = BorderStroke(
                width = 1.dp,
                color = when {
                    isDictating -> AntigravityPalette.ElectricBlue.copy(alpha = micPulseAlpha)
                    promptText.isNotEmpty() -> AntigravityPalette.BorderAccent
                    else -> AntigravityPalette.BorderHairline
                }
            ),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 8.dp)
            ) {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = onPromptChange,
                    textStyle = TextStyle(
                        color = AntigravityPalette.TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    placeholder = {
                        Text(
                            text = when {
                                isDictating -> "Listening... speak now"
                                isLiveVoiceActive -> if (isMicMuted) "Live voice muted..." else "Listening to speech..."
                                else -> "Ask anything, @ to mention, / for workflows"
                            },
                            fontSize = 14.sp,
                            color = if (isDictating) AntigravityPalette.ElectricBlue else AntigravityPalette.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("command_bar_text_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = AntigravityPalette.TextPrimary,
                        unfocusedTextColor = AntigravityPalette.TextPrimary,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = AntigravityPalette.ElectricBlue
                    )
                )

                // Exact Original Antigravity Inline Bottom Toolbar: [+]  [⌃ Planning]  [⌃ Model]      [🎙] [↑]
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // [+] Context / Attach Menu (Photos, Files, @ Mention, / Workflow)
                            Box {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showPlusMenu = true }
                                        .testTag("command_bar_tools_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add context or attachment",
                                        tint = AntigravityPalette.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showPlusMenu,
                                    onDismissRequest = { showPlusMenu = false },
                                    modifier = Modifier
                                        .background(AntigravityPalette.SurfaceElevated)
                                        .width(210.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Attach files or media...", color = AntigravityPalette.TextPrimary, fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.Add, null, tint = AntigravityPalette.TextSecondary, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            showPlusMenu = false
                                            onToolsClick()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Mention @file / @folder", color = AntigravityPalette.TextPrimary, fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.AlternateEmail, null, tint = AntigravityPalette.ElectricBlue, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            showPlusMenu = false
                                            onInsertAtMention()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Workflow / commands", color = AntigravityPalette.TextPrimary, fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.Terminal, null, tint = AntigravityPalette.TextSecondary, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            showPlusMenu = false
                                            onInsertSlashCommand()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Live Voice Studio", color = AntigravityPalette.TextPrimary, fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.Headset, null, tint = AntigravityPalette.TextSecondary, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            showPlusMenu = false
                                            onVoiceStudioClick()
                                        }
                                    )
                                }
                            }

                            // [⌃ Planning / Fast] Mode + `agy` Autonomy Policy Selector
                            Box {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showModeMenu = true }
                                        .padding(horizontal = 6.dp, vertical = 5.dp)
                                        .testTag("ag_composer_mode_toggle"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Select conversation mode",
                                        tint = AntigravityPalette.TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = agentMode.label,
                                        color = AntigravityPalette.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }

                                DropdownMenu(
                                    expanded = showModeMenu,
                                    onDismissRequest = { showModeMenu = false },
                                    modifier = Modifier
                                        .background(AntigravityPalette.SurfaceElevated)
                                        .width(245.dp)
                                ) {
                                    Text(
                                        text = "CONVERSATION MODE",
                                        color = AntigravityPalette.TextMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                    AntigravityAgentMode.entries.forEach { mode ->
                                        val selected = mode == agentMode
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = mode.label,
                                                        color = if (selected) AntigravityPalette.TextPrimary else AntigravityPalette.TextSecondary,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                                    )
                                                    Text(
                                                        text = mode.description,
                                                        color = AntigravityPalette.TextMuted,
                                                        fontSize = 10.5.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (mode == AntigravityAgentMode.PLANNING) Icons.Default.Route else Icons.Default.Bolt,
                                                    contentDescription = null,
                                                    tint = if (selected) AntigravityPalette.ElectricBlue else AntigravityPalette.TextMuted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            trailingIcon = if (selected) {
                                                { Icon(Icons.Default.Check, null, tint = AntigravityPalette.ElectricBlue, modifier = Modifier.size(15.dp)) }
                                            } else null,
                                            onClick = {
                                                showModeMenu = false
                                                onSelectAgentMode(mode)
                                            }
                                        )
                                    }
                                    HorizontalDivider(color = AntigravityPalette.BorderHairline, modifier = Modifier.padding(vertical = 4.dp))
                                    Text(
                                        text = "AGY CLI AUTONOMY POLICY",
                                        color = AntigravityPalette.TextMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                    AntigravityAutonomyPolicy.entries.forEach { policy ->
                                        val selected = policy == autonomyPolicy
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = policy.cliFlag,
                                                        color = if (selected) AntigravityPalette.TextPrimary else AntigravityPalette.TextSecondary,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                                    )
                                                    Text(
                                                        text = policy.description,
                                                        color = AntigravityPalette.TextMuted,
                                                        fontSize = 10.5.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            },
                                            trailingIcon = if (selected) {
                                                { Icon(Icons.Default.Check, null, tint = AntigravityPalette.ElectricBlue, modifier = Modifier.size(15.dp)) }
                                            } else null,
                                            onClick = {
                                                showModeMenu = false
                                                onSelectAutonomyPolicy(policy)
                                            }
                                        )
                                    }
                                }
                            }

                            // [⌃ Model Name] Selector
                            Box(modifier = Modifier.weight(1f, fill = false)) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showModelMenu = true }
                                        .padding(horizontal = 6.dp, vertical = 5.dp)
                                        .testTag("topbar_model_selector_pill"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Select Model",
                                        tint = AntigravityPalette.TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = modelPillText,
                                        color = AntigravityPalette.TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (activeQuotaPercent != null) {
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(quotaColor)
                                        )
                                    }
                                }

                                ModelSelectorDropdown(
                                    expanded = showModelMenu,
                                    onDismissRequest = { showModelMenu = false },
                                    thinkingLevelLabel = thinkingLevelLabel,
                                    isThinkingEnabled = isThinkingEnabled,
                                    isAntigravityLoggedIn = isAntigravityLoggedIn,
                                    antigravityTier = antigravityTier,
                                    antigravityModels = antigravityModels,
                                    isRefreshingAntigravityModels = isRefreshingAntigravityModels,
                                    onOpenAntigravityLogin = onOpenAntigravityLogin,
                                    onRefreshAntigravityModels = onRefreshAntigravityModels,
                                    onOpenThinkingSelector = onOpenThinkingSelector,
                                    onOpenCactusManager = onOpenCactusManager,
                                    onModelSelect = onModelSelect
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Right cluster: [🎧] Agy Voice Call + [🎙] subtle voice + [↑ / ■] circular Antigravity send/stop button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (onAgyVoiceCallClick != null) {
                                Surface(
                                    onClick = onAgyVoiceCallClick,
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isAgyVoiceCallActive) {
                                        AntigravityPalette.ElectricBlue.copy(alpha = 0.22f)
                                    } else {
                                        Color.Transparent
                                    },
                                    modifier = Modifier
                                        .size(30.dp)
                                        .testTag("command_bar_agy_voice_call_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Headset,
                                            contentDescription = if (isAgyVoiceCallActive) {
                                                "Return to Antigravity voice call"
                                            } else {
                                                "Start Antigravity voice call"
                                            },
                                            tint = if (isAgyVoiceCallActive) {
                                                AntigravityPalette.ElectricBlue
                                            } else {
                                                AntigravityPalette.TextSecondary
                                            },
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onDictateToggleClick)
                                    .testTag("command_bar_dictate_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isDictating) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = if (isDictating) "Stop Voice Dictation" else "Voice Dictation",
                                    tint = if (isDictating) AntigravityPalette.ElectricBlue else AntigravityPalette.TextSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            when {
                                isGenerating -> {
                                    Surface(
                                        onClick = onStopClick,
                                        shape = CircleShape,
                                        color = AntigravityPalette.TextPrimary,
                                        modifier = Modifier
                                            .size(30.dp)
                                            .testTag("command_bar_stop_button")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Stop,
                                                contentDescription = "Stop Generation",
                                                tint = AntigravityPalette.Canvas,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                canSend -> {
                                    Surface(
                                        onClick = onSendClick,
                                        shape = CircleShape,
                                        color = AntigravityPalette.TextPrimary,
                                        modifier = Modifier
                                            .size(30.dp)
                                            .testTag("command_bar_send_button")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = "Send Prompt",
                                                tint = AntigravityPalette.Canvas,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    Surface(
                                        onClick = {},
                                        enabled = false,
                                        shape = CircleShape,
                                        color = AntigravityPalette.SurfaceElevated,
                                        modifier = Modifier
                                            .size(30.dp)
                                            .testTag("command_bar_send_disabled")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = "Type a message to send",
                                                tint = AntigravityPalette.TextMuted,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
