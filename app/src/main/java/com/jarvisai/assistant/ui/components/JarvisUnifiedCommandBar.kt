package com.jarvisai.assistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.data.remote.AntigravityModelInfo

/**
 * AntiGem-Style Two-Row Floating Composer Card.
 * Row 1: Clean borderless prompt input ("Reply to Gemini...")
 * Row 2: [ + ]  [ ✦ Gemini 3.8 Flash (High) [100%] ▾ ]    [ 🎧 ] [ ⫼ Dictate ] [ 🎙️ / ➤ / ■ ]
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
    onToolsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showModelMenu by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "MicPulse")
    val micPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Alpha"
    )

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
    val composerTargetName = remember(compactModelLabel) {
        when {
            compactModelLabel.contains("gemini", ignoreCase = true) -> "Gemini"
            compactModelLabel.contains("claude", ignoreCase = true) ||
                compactModelLabel.contains("opus", ignoreCase = true) ||
                compactModelLabel.contains("sonnet", ignoreCase = true) -> "Claude"
            compactModelLabel.contains("opencode", ignoreCase = true) -> "OpenCode"
            compactModelLabel.contains("codex", ignoreCase = true) -> "Codex"
            else -> "Jarvis"
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
            // Row 1: Clean Auto-Expanding Prompt Field (Dictation writes directly here live)
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
                    // Left cluster: [+] + [✦ Model (Thinking) 100% ▾]
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
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (activeQuotaPercent != null) {
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = quotaColor.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "$activeQuotaPercent%",
                                                color = quotaColor,
                                                fontSize = 10.5.sp,
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
                                onModelSelect = onModelSelect
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Right cluster: [🎧] [⫼ Dictate] [🎙️ / ➤ / ■]
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left of 3 buttons: Headphones -> Open Full Live Voice Studio
                        Surface(
                            onClick = onVoiceStudioClick,
                            shape = CircleShape,
                            color = Color(0xFF1D212A),
                            border = BorderStroke(1.dp, Color(0xFF2B313E)),
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("command_bar_voice_studio_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Headset,
                                    contentDescription = "Open Live Voice Studio",
                                    tint = Color(0xFFD1D5DB),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // Middle of 3 buttons: Voice Dictation Toggle -> transcribes spoken language live into text box
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

                        // Rightmost White Circle: Stop (Red) while generating -> Send (White) when text/attachments -> Live Voice Mic (White) when idle
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
