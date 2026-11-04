package com.jarvisai.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.jarvisai.assistant.model.PersonalityMode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.ui.platform.LocalContext
import com.jarvisai.assistant.driving.JarvisDrivingModeManager
import com.jarvisai.assistant.telephony.JarvisTelephonyManager
import com.jarvisai.assistant.termux.JarvisTermuxBridge
import com.jarvisai.assistant.wallpaper.JarvisWallpaperService
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisElevatedSurface
import com.example.ui.theme.JarvisGlassBorder
import com.example.ui.theme.JarvisGlassSurface
import com.example.ui.theme.JarvisNeonCyan
import com.example.ui.theme.JarvisNeonGreen
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisThemeMode
import com.example.ui.theme.JarvisViolet
import com.example.ui.theme.JarvisVioletBright
import com.example.ui.theme.StatusListeningEmerald
import com.example.ui.theme.StatusThinkingAmber
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.CompanionModeState
import com.jarvisai.assistant.model.SupportedGeminiModels
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.model.VoiceGender
import com.jarvisai.assistant.ui.components.JarvisAIOrb
import com.jarvisai.assistant.ui.components.JarvisGlassPanel
import com.jarvisai.assistant.ui.components.JarvisModernDialog
import com.jarvisai.assistant.ui.components.JarvisPremiumTopBar
import com.jarvisai.assistant.ui.components.JarvisPrimaryActionButton
import com.jarvisai.assistant.ui.components.JarvisSecondaryActionButton
import com.jarvisai.assistant.ui.components.JarvisSettingsTile
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeneralSettingsScreen(
    settings: UserSettings,
    status: AssistantStatus,
    sessionsCount: Int,
    memoriesCount: Int,
    isTtsSpeaking: Boolean = false,
    ttsErrorMessage: String? = null,
    onBack: () -> Unit,
    onUpdateAssistantName: (String) -> Unit,
    onUpdateLanguage: (SupportedLanguage) -> Unit,
    onUpdateVoiceGender: (VoiceGender) -> Unit = {},
    onUpdateVoicePersona: (String) -> Unit,
    onPreviewVoice: (VoiceGender, String) -> Unit = { _, _ -> },
    onStopVoicePreview: () -> Unit = {},
    onUpdateVoiceSpeed: (Float) -> Unit,
    onUpdateVoicePitch: (Float) -> Unit,
    onUpdateAutoSpeakChat: (Boolean) -> Unit = {},
    onUpdateFloatingOverlay: (Boolean) -> Unit = {},
    onUpdateThemeMode: (JarvisThemeMode) -> Unit,
    onUpdateMemoryEnabled: (Boolean) -> Unit,
    onUpdateAutoInjectMemories: (Boolean) -> Unit,
    onUpdateConfirmConsequential: (Boolean) -> Unit,
    onUpdatePrivacyRedact: (Boolean) -> Unit,
    onClearChatHistory: () -> Unit,
    onClearMemories: () -> Unit,
    onUpdatePersonalityMode: (PersonalityMode) -> Unit = {},
    onActivateFemaleVoiceCompanion: () -> Unit = {},
    onDeactivateFemaleVoiceCompanion: () -> Unit = {},
    companionModeState: CompanionModeState = if (settings.personalityMode == PersonalityMode.FEMALE_COMPANION) CompanionModeState.ACTIVE else CompanionModeState.INACTIVE,
    companionModeStatusMessage: String? = null,
    onUpdateWakeWordEnabled: (Boolean) -> Unit = {},
    wakeWordState: com.jarvisai.assistant.service.WakeWordState = if (settings.wakeWordEnabled) com.jarvisai.assistant.service.WakeWordState.LISTENING else com.jarvisai.assistant.service.WakeWordState.DISABLED,
    wakeWordErrorMessage: String? = null,
    onUpdateRequireBiometric: (Boolean) -> Unit = {},
    onUpdateMaxAutonomousSteps: (Int) -> Unit = {},
    companionEmotionMood: com.jarvisai.assistant.model.CompanionEmotionMood = com.jarvisai.assistant.model.CompanionEmotionMood.COMPOSED,
    onUpdateCustomPersonalityPrompt: (String) -> Unit = {},
    onUpdateProactiveCompanionEnabled: (Boolean) -> Unit = {},
    onUpdateAmbientRoomTalkEnabled: (Boolean) -> Unit = {},
    onTriggerProactiveCompanionNow: () -> Unit = {}
) {
    var nameInput by remember(settings.assistantName) { mutableStateOf(settings.assistantName) }
    var customPersonaInput by remember(settings.customPersonalityPrompt) { mutableStateOf(settings.customPersonalityPrompt) }
    var showClearChatsConfirm by remember { mutableStateOf(false) }
    var showClearMemoriesConfirm by remember { mutableStateOf(false) }

    val availableVoicesForGender = remember(settings.voiceGender) {
        SupportedGeminiModels.voiceOptions.filter { it.gender == settings.voiceGender }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            JarvisPremiumTopBar(
                title = "Settings",
                subtitle = "${settings.assistantName} • ${settings.voicePersona} • ${settings.language.displayName}",
                status = status,
                onBackClick = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. IMAGE 5 ("MONEX SETTINGS — LEONARD LOPEZ") CENTERED AVATAR HEADER + DUAL PLAN CARDS
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Centered Circular AI Avatar with Neon Ring
                    Box(
                        modifier = Modifier
                            .size(82.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF151C2E))
                            .border(
                                2.dp,
                                Brush.linearGradient(listOf(JarvisNeonGreen, JarvisNeonCyan, JarvisViolet)),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        JarvisAIOrb(
                            status = status,
                            modifier = Modifier.size(68.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = settings.assistantName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "@${settings.assistantName.lowercase(Locale.US)}.neural.core",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextSecondary
                        )
                    }

                    // Inline Name Editor Capsule
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Assistant Call-Sign") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF131828),
                                unfocusedContainerColor = Color(0xFF101422),
                                focusedBorderColor = JarvisNeonGreen,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.14f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_assistant_name_input")
                        )

                        JarvisPrimaryActionButton(
                            text = "Save",
                            enabled = nameInput.isNotBlank() && nameInput != settings.assistantName,
                            accentColor = JarvisNeonGreen,
                            onClick = { onUpdateAssistantName(nameInput) },
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("btn_save_assistant_name")
                        )
                    }

                    // Image 5 Monex Dual Side-by-Side Feature Cards ("Standard / Your plan" & "Referrals")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left Card: Active Language Card with Neon Lime Star Circle (Image 5 exact layout!)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF161A26))
                                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(JarvisNeonGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFF060A0E),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = settings.language.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "Active language",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JarvisTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Right Card: Active Voice Persona Card with Lightning Graphic (Image 5 exact layout!)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF161A26))
                                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(JarvisViolet.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = JarvisNeonCyan,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = settings.voicePersona,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = settings.voiceGender.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JarvisTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // Language Selector Pills (Preserving settings_lang_* testTags)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF111522))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SupportedLanguage.entries.forEach { lang ->
                            val selected = settings.language == lang
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (selected) Color(0xFF232B44) else Color.Transparent
                                    )
                                    .border(
                                        width = if (selected) 1.dp else 0.dp,
                                        color = if (selected) JarvisNeonGreen else Color.Transparent,
                                        shape = RoundedCornerShape(999.dp)
                                    )
                                    .clickable { onUpdateLanguage(lang) }
                                    .padding(vertical = 8.dp)
                                    .testTag("settings_lang_${lang.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lang.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) Color.White else JarvisTextSecondary,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 1.5. 1-CLICK FEMALE VOICE COMPANION MODE HERO CARD
            item {
                val isTransitioning = companionModeState == CompanionModeState.ACTIVATING ||
                    companionModeState == CompanionModeState.DEACTIVATING
                val isCompanionActive = companionModeState == CompanionModeState.ACTIVE ||
                    settings.personalityMode == PersonalityMode.FEMALE_COMPANION ||
                    settings.personalityMode == PersonalityMode.GIRLFRIEND
                val badgeColor = when (companionModeState) {
                    CompanionModeState.ACTIVE -> JarvisNeonGreen
                    CompanionModeState.ACTIVATING, CompanionModeState.DEACTIVATING -> StatusThinkingAmber
                    CompanionModeState.ERROR -> AlertCrimson
                    CompanionModeState.INACTIVE -> if (isCompanionActive) JarvisNeonGreen else JarvisTextSecondary
                }
                val badgeLabel = when {
                    companionModeState != CompanionModeState.INACTIVE -> companionModeState.badgeText
                    isCompanionActive -> CompanionModeState.ACTIVE.badgeText
                    else -> CompanionModeState.INACTIVE.badgeText
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF4A154B),
                                    Color(0xFF2E1045),
                                    Color(0xFF0F172A)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.horizontalGradient(listOf(Color(0xFFF472B6), Color(0xFFC084FC), JarvisNeonCyan)),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFF472B6), modifier = Modifier.size(20.dp))
                                Text(
                                    text = "Female Voice Companion Mode",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFFF9A8D4),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(badgeColor.copy(alpha = 0.2f))
                                    .border(1.dp, badgeColor, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                    .testTag("companion_mode_status_badge")
                            ) {
                                Text(
                                    text = badgeLabel,
                                    color = badgeColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = "Switches JARVIS to a warm, emotionally expressive female companion (Kore / Aoede voice) speaking natural Hinglish with real human feelings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE2E8F0),
                            lineHeight = 18.sp
                        )

                        if (!companionModeStatusMessage.isNullOrBlank()) {
                            Text(
                                text = companionModeStatusMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                modifier = Modifier.testTag("companion_mode_status_message")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            if (isTransitioning) {
                                                listOf(Color(0xFF64748B), Color(0xFF475569))
                                            } else {
                                                listOf(Color(0xFFC084FC), Color(0xFFF472B6))
                                            }
                                        )
                                    )
                                    .clickable(enabled = !isTransitioning) {
                                        onActivateFemaleVoiceCompanion()
                                    }
                                    .testTag("btn_activate_female_companion"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFF1E1035), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when (companionModeState) {
                                            CompanionModeState.ACTIVATING -> "Activating Companion Mode..."
                                            CompanionModeState.DEACTIVATING -> "Deactivating Companion Mode..."
                                            else -> "1-Click Activate Companion Mode"
                                        },
                                        color = Color(0xFF1E1035),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            if (isCompanionActive) {
                                Box(
                                    modifier = Modifier
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF1E293B))
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                                        .clickable(enabled = !isTransitioning) {
                                            onDeactivateFemaleVoiceCompanion()
                                        }
                                        .padding(horizontal = 14.dp)
                                        .testTag("btn_deactivate_female_companion"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Deactivate",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 1.6. DEEP PERSONALITY MODES, HUMAN FEELINGS & PROACTIVE / AMBIENT ROOM CONTROLS
            item {
                JarvisGlassPanel(
                    accentColor = JarvisVioletBright,
                    cornerRadius = 24.dp,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Psychology, contentDescription = null, tint = JarvisVioletBright, modifier = Modifier.size(20.dp))
                                Column {
                                    Text(
                                        text = "AI Personality Persona & Feelings",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "Auto-switches voice & full Hinglish emotional roleplay",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JarvisTextSecondary
                                    )
                                }
                            }

                            // Live Mood Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xFF1E1B4B))
                                    .border(1.dp, JarvisVioletBright.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .testTag("live_companion_mood_badge")
                            ) {
                                Text(
                                    text = "${companionEmotionMood.emoji} ${companionEmotionMood.displayLabel}",
                                    color = Color(0xFFF9A8D4),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Active Persona Highlight Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF11172A))
                                .border(1.dp, JarvisNeonCyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                                .testTag("active_persona_summary_card")
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = settings.personalityMode.displayName,
                                        color = JarvisNeonCyan,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Voice: ${settings.voicePersona} (${settings.voiceGender.displayName.substringBefore(" ")})",
                                        color = JarvisNeonGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = settings.personalityMode.subtitle,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PersonalityMode.entries.forEach { mode ->
                                val isSelected = settings.personalityMode == mode
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) JarvisViolet.copy(alpha = 0.38f) else Color(0xFF151B2D))
                                        .border(
                                            1.dp,
                                            if (isSelected) JarvisNeonCyan else Color(0xFF334155),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onUpdatePersonalityMode(mode) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .testTag("personality_mode_${mode.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.displayName,
                                        color = if (isSelected) JarvisNeonCyan else Color.LightGray,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        if (settings.personalityMode == PersonalityMode.CUSTOM) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = customPersonaInput,
                                    onValueChange = { customPersonaInput = it },
                                    label = { Text("Custom Persona Instructions (Hinglish/English)") },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("input_custom_personality_prompt")
                                )
                                JarvisPrimaryActionButton(
                                    text = "Apply",
                                    accentColor = JarvisNeonCyan,
                                    onClick = { onUpdateCustomPersonalityPrompt(customPersonaInput) },
                                    modifier = Modifier.height(50.dp)
                                )
                            }
                        }

                        // Proactive Screen & Silence Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF121829))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Proactive Screen & Silence Talk",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Bina bole khud baat shuru karega (Reels, YouTube, WhatsApp ya chup rehne par)",
                                    color = JarvisTextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            Switch(
                                checked = settings.proactiveCompanionEnabled,
                                onCheckedChange = onUpdateProactiveCompanionEnabled,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = JarvisNeonGreen
                                ),
                                modifier = Modifier.testTag("switch_proactive_companion")
                            )
                        }

                        // Ambient Room 3rd-Person Multi-Speaker Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF121829))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Ambient Room 3rd-Person Mode",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Jab aap kisi aur insaan se baat kar rahe hon, toh sun kar dost/partner ki tarah beech mein bole",
                                    color = JarvisTextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            Switch(
                                checked = settings.ambientRoomTalkEnabled,
                                onCheckedChange = onUpdateAmbientRoomTalkEnabled,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = JarvisNeonCyan
                                ),
                                modifier = Modifier.testTag("switch_ambient_room_talk")
                            )
                        }

                        // 1-Click Test Proactive Speech Button
                        JarvisSecondaryActionButton(
                            text = "✨ Test Proactive Talk Now (${settings.personalityMode.displayName})",
                            onClick = onTriggerProactiveCompanionNow,
                            accentColor = JarvisNeonCyan,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_trigger_proactive_now")
                        )
                    }
                }
            }

            // 1.7. OFFLINE SMART WAKE WORD SETTINGS
            item {
                val isWakeWordTrulyOn = settings.wakeWordEnabled &&
                    wakeWordState != com.jarvisai.assistant.service.WakeWordState.DISABLED &&
                    wakeWordState != com.jarvisai.assistant.service.WakeWordState.ERROR
                val wakeBadgeColor = when (wakeWordState) {
                    com.jarvisai.assistant.service.WakeWordState.LISTENING,
                    com.jarvisai.assistant.service.WakeWordState.WAKE_WORD_DETECTED,
                    com.jarvisai.assistant.service.WakeWordState.PROCESSING -> JarvisNeonGreen
                    com.jarvisai.assistant.service.WakeWordState.INITIALIZING -> StatusThinkingAmber
                    com.jarvisai.assistant.service.WakeWordState.ERROR -> AlertCrimson
                    com.jarvisai.assistant.service.WakeWordState.DISABLED -> JarvisTextSecondary
                }
                JarvisGlassPanel(
                    accentColor = JarvisNeonGreen,
                    cornerRadius = 24.dp,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = JarvisNeonGreen, modifier = Modifier.size(20.dp))
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Offline Wake Word",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(wakeBadgeColor.copy(alpha = 0.18f))
                                                .border(1.dp, wakeBadgeColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                .testTag("wake_word_state_badge")
                                        ) {
                                            Text(
                                                text = wakeWordState.badgeText,
                                                color = wakeBadgeColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = "On-device keyword spotter (${wakeWordState.displayLabel})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JarvisTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Switch(
                                checked = isWakeWordTrulyOn,
                                onCheckedChange = { onUpdateWakeWordEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = JarvisNeonGreen,
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color(0xFF1E293B)
                                ),
                                modifier = Modifier.testTag("switch_settings_wake_word")
                            )
                        }

                        if (!wakeWordErrorMessage.isNullOrBlank()) {
                            Text(
                                text = wakeWordErrorMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = AlertCrimson,
                                modifier = Modifier.testTag("wake_word_error_message")
                            )
                        }

                        Text(
                            text = "Supports: \"Hey Jarvis\", \"O Jarvis\", \"Jarvis\", \"Hey Jarvis, listen\", \"Suno Jarvis\", and \"Jarvis, stop listening\". Operates offline without external cloud processing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // 2. IMAGE 8 ("FULL CONTROL" BUTTON SETTINGS INSPECTOR) GEMINI VOICE STUDIO
            item {
                JarvisGlassPanel(
                    accentColor = JarvisVioletBright,
                    cornerRadius = 24.dp,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = JarvisVioletBright,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Gemini Voice Studio",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        // Image 8 "Alignment" Segmented Bar for Male / Female Voice Gender!
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Voice Gender",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextSecondary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF0E1220))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                VoiceGender.entries.forEach { gender ->
                                    val isSelected = settings.voiceGender == gender
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSelected) Color(0xFF4F46E5)
                                                else Color.Transparent
                                            )
                                            .clickable { onUpdateVoiceGender(gender) }
                                            .padding(vertical = 10.dp)
                                            .testTag("card_voice_gender_${gender.name.lowercase()}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = gender.displayName,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isSelected) Color.White else JarvisTextSecondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Active Voice Sample Banner
                        if (isTtsSpeaking) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(StatusListeningEmerald.copy(alpha = 0.16f))
                                    .border(1.dp, StatusListeningEmerald, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Playing Hinglish Voice Sample...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = StatusListeningEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AlertCrimson)
                                        .clickable(onClick = onStopVoicePreview)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .testTag("btn_stop_voice_preview")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "STOP",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        if (!ttsErrorMessage.isNullOrBlank()) {
                            Text(
                                text = ttsErrorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = AlertCrimson
                            )
                        }

                        // Apple Arcade "Your Games" Style Voice Persona Rows
                        availableVoicesForGender.forEach { voiceOpt ->
                            val isSelected = settings.voicePersona.equals(voiceOpt.id, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) Color(0xFF1D2540)
                                        else Color(0xFF121626)
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) JarvisNeonCyan else Color.White.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { onUpdateVoicePersona(voiceOpt.id) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("voice_card_${voiceOpt.id.lowercase()}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSelected) JarvisNeonCyan.copy(alpha = 0.22f)
                                                else Color(0xFF1C2236)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            tint = if (isSelected) JarvisNeonCyan else JarvisTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = voiceOpt.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = voiceOpt.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = JarvisTextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Apple Arcade Dark Pill "Play" / "Sample" Button
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color(0xFF262D47))
                                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                                        .clickable {
                                            onUpdateVoicePersona(voiceOpt.id)
                                            onPreviewVoice(voiceOpt.gender, voiceOpt.id)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .testTag("btn_preview_voice_${voiceOpt.id.lowercase()}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Play",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Acoustic Sliders
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF101422))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Speech Rate", style = MaterialTheme.typography.labelMedium, color = Color.White)
                                Text(
                                    text = String.format(Locale.US, "%.2fx", settings.voiceSpeed),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = JarvisNeonCyan,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Slider(
                                value = settings.voiceSpeed,
                                onValueChange = onUpdateVoiceSpeed,
                                valueRange = 0.5f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = JarvisNeonCyan,
                                    activeTrackColor = JarvisNeonCyan
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Voice Pitch", style = MaterialTheme.typography.labelMedium, color = Color.White)
                                Text(
                                    text = String.format(Locale.US, "%.2fx", settings.voicePitch),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = JarvisVioletBright,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Slider(
                                value = settings.voicePitch,
                                onValueChange = onUpdateVoicePitch,
                                valueRange = 0.5f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = JarvisVioletBright,
                                    activeTrackColor = JarvisVioletBright
                                )
                            )
                        }

                        JarvisSettingsTile(
                            title = "Auto-Speak Text Chat Replies",
                            subtitle = "Automatically read normal text chat responses aloud using TTS",
                            checked = settings.autoSpeakChatReplies,
                            onCheckedChange = onUpdateAutoSpeakChat,
                            accentColor = JarvisVioletBright,
                            testTag = "switch_settings_auto_speak_chat"
                        )

                        JarvisSettingsTile(
                            title = "Floating Call Bubble Overlay",
                            subtitle = "Keep floating JARVIS control widget active when using other apps",
                            checked = settings.floatingOverlayEnabled,
                            onCheckedChange = onUpdateFloatingOverlay,
                            accentColor = JarvisNeonCyan,
                            testTag = "switch_settings_floating_overlay"
                        )
                    }
                }
            }

            // 3. VISUAL THEME SWATCH MATRIX
            item {
                JarvisGlassPanel(
                    accentColor = JarvisElectricBlue,
                    cornerRadius = 24.dp,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = JarvisElectricBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Theme Atmosphere",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            JarvisThemeMode.entries.forEach { mode ->
                                val selected = settings.themeMode == mode
                                val swatchColor = when (mode) {
                                    JarvisThemeMode.ARC_CYAN -> JarvisViolet
                                    JarvisThemeMode.DEEP_COBALT -> JarvisElectricBlue
                                    JarvisThemeMode.STEALTH_DARK -> JarvisNeonCyan
                                    JarvisThemeMode.HIGH_CONTRAST -> JarvisNeonGreen
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (selected) swatchColor.copy(alpha = 0.20f)
                                            else Color(0xFF121626)
                                        )
                                        .border(
                                            width = if (selected) 1.5.dp else 1.dp,
                                            color = if (selected) swatchColor else Color.White.copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable { onUpdateThemeMode(mode) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                        .testTag("theme_mode_${mode.name.lowercase()}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(swatchColor)
                                        )
                                        Column {
                                            Text(
                                                text = mode.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = mode.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = JarvisTextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. IMAGE 5 MONEX GROUPED LIST FOR MEMORY & PRIVACY SAFEGUARDS
            item {
                JarvisGlassPanel(
                    accentColor = StatusListeningEmerald,
                    cornerRadius = 24.dp,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = StatusListeningEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Privacy & Safeguards",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        JarvisSettingsTile(
                            title = "Enable Long-Term Memory",
                            subtitle = "Allow JARVIS to store and recall user-approved facts",
                            checked = settings.memoryEnabled,
                            onCheckedChange = onUpdateMemoryEnabled,
                            accentColor = JarvisNeonCyan,
                            testTag = "switch_settings_memory_enabled"
                        )

                        JarvisSettingsTile(
                            title = "Auto-Inject Relevant Memories",
                            subtitle = "Include pinned & matching memories in AI prompts automatically",
                            checked = settings.autoInjectMemories,
                            onCheckedChange = onUpdateAutoInjectMemories,
                            accentColor = JarvisVioletBright,
                            testTag = "switch_settings_auto_inject_memories"
                        )

                        JarvisSettingsTile(
                            title = "Confirm Consequential Phone Actions",
                            subtitle = "Ask before placing phone calls, sending messages, or modifying critical settings",
                            checked = settings.confirmConsequentialActions,
                            onCheckedChange = onUpdateConfirmConsequential,
                            accentColor = StatusThinkingAmber,
                            testTag = "switch_settings_confirm_actions"
                        )

                        JarvisSettingsTile(
                            title = "Require Biometric / Device Lock for High-Risk Actions",
                            subtitle = "Verify fingerprint, face, or device PIN before approving sensitive actions",
                            checked = settings.requireBiometricForHighRisk,
                            onCheckedChange = onUpdateRequireBiometric,
                            accentColor = AlertCrimson,
                            testTag = "switch_settings_biometric_gate"
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF101422))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Max Autonomous Steps Per Turn",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${settings.maxAutonomousSteps} steps",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = JarvisNeonCyan,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "Safety cap on consecutive tool actions before JARVIS pauses for your input (1–100)",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextSecondary,
                                fontSize = 11.sp
                            )
                            Slider(
                                value = settings.maxAutonomousSteps.toFloat(),
                                onValueChange = { onUpdateMaxAutonomousSteps(it.toInt().coerceIn(1, 100)) },
                                valueRange = 1f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = JarvisNeonCyan,
                                    activeTrackColor = JarvisNeonCyan
                                ),
                                modifier = Modifier.testTag("slider_max_autonomous_steps")
                            )
                        }

                        JarvisSettingsTile(
                            title = "Redact Sensitive Telemetry Logs",
                            subtitle = "Mask API keys and personal identifiers in diagnostic logs",
                            checked = settings.privacyRedactTelemetry,
                            onCheckedChange = onUpdatePrivacyRedact,
                            accentColor = StatusListeningEmerald,
                            testTag = "switch_settings_privacy_redact"
                        )
                    }
                }
            }

            // 5. ANDROID SYSTEM ROLES & SUPERCHARGED HARDWARE INTEGRATION (MAYA SUITE)
            item {
                val context = LocalContext.current
                val termuxInstalled = remember { JarvisTermuxBridge(context).isTermuxInstalled() }
                val telephonyManager = remember { JarvisTelephonyManager(context) }
                val hasPhonePermission = remember { telephonyManager.hasPhoneStatePermission() }

                JarvisGlassPanel(
                    accentColor = JarvisNeonCyan,
                    cornerRadius = 24.dp,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = JarvisNeonCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "System Roles & OS Integration",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        // 1. Android Default Digital Assistant
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, JarvisNeonCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Default System Assistant",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Long-press Power button, swipe corner, or use earphone button to wake JARVIS instantly.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = JarvisTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                JarvisPrimaryActionButton(
                                    text = "Set as Default Assistant",
                                    icon = Icons.Default.CheckCircle,
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        }
                                    },
                                    accentColor = JarvisNeonCyan,
                                    modifier = Modifier.testTag("btn_set_default_assistant")
                                )
                            }
                        }

                        // 2. Interactive Sci-Fi Live Wallpaper
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, JarvisVioletBright.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Arc Reactor Live Wallpaper",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Set glowing Arc Reactor HUD as your home & lock screen wallpaper. Double-tap to open JARVIS.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextSecondary,
                                    fontSize = 11.sp
                                )
                                JarvisSecondaryActionButton(
                                    text = "Set Live Wallpaper",
                                    icon = Icons.Default.Palette,
                                    accentColor = JarvisVioletBright,
                                    onClick = {
                                        try {
                                            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                                putExtra(
                                                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                                    ComponentName(context, JarvisWallpaperService::class.java)
                                                )
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            val intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        }
                                    },
                                    modifier = Modifier.testTag("btn_set_live_wallpaper")
                                )
                            }
                        }

                        // 3. Telephony & Hands-Free Calling Status
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF131B2E))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = JarvisNeonGreen, modifier = Modifier.size(18.dp))
                                Column {
                                    Text("Incoming Call Announcement", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Auto-announces caller and allows 'Utha lo' voice answer", color = JarvisTextSecondary, fontSize = 10.sp)
                                }
                            }
                            Text(
                                text = if (hasPhonePermission) "Active" else "Permission Req",
                                color = if (hasPhonePermission) JarvisNeonGreen else StatusThinkingAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // 4. Termux Linux Bridge Status
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF131B2E))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Terminal, contentDescription = null, tint = JarvisElectricBlue, modifier = Modifier.size(18.dp))
                                Column {
                                    Text("Termux Command Bridge", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Executes Linux bash & Python scripts via voice", color = JarvisTextSecondary, fontSize = 10.sp)
                                }
                            }
                            Text(
                                text = if (termuxInstalled) "Installed" else "Not Installed",
                                color = if (termuxInstalled) JarvisNeonGreen else JarvisTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // 6. DANGER ZONE STORAGE PURGE
            item {
                JarvisGlassPanel(
                    accentColor = AlertCrimson,
                    cornerRadius = 24.dp,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Storage Vault ($sessionsCount Chats • $memoriesCount Memories)",
                            style = MaterialTheme.typography.titleSmall,
                            color = AlertCrimson,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            JarvisSecondaryActionButton(
                                text = "Clear Chats ($sessionsCount)",
                                icon = Icons.Default.DeleteForever,
                                accentColor = AlertCrimson,
                                onClick = { showClearChatsConfirm = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_settings_clear_chats")
                            )

                            JarvisSecondaryActionButton(
                                text = "Clear Vault ($memoriesCount)",
                                icon = Icons.Default.DeleteForever,
                                accentColor = AlertCrimson,
                                onClick = { showClearMemoriesConfirm = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_settings_clear_memories")
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearChatsConfirm) {
        JarvisModernDialog(
            title = "Purge All Chat History?",
            message = "All $sessionsCount saved chat sessions and message transcripts will be permanently deleted.",
            confirmLabel = "Purge Chats",
            isDestructive = true,
            onConfirm = {
                onClearChatHistory()
                showClearChatsConfirm = false
            },
            onDismiss = { showClearChatsConfirm = false }
        )
    }

    if (showClearMemoriesConfirm) {
        JarvisModernDialog(
            title = "Purge All Neural Memories?",
            message = "All $memoriesCount saved long-term memories will be permanently deleted.",
            confirmLabel = "Purge Memories",
            isDestructive = true,
            onConfirm = {
                onClearMemories()
                showClearMemoriesConfirm = false
            },
            onDismiss = { showClearMemoriesConfirm = false }
        )
    }
}
