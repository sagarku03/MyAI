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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
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
    onClearMemories: () -> Unit
) {
    var nameInput by remember(settings.assistantName) { mutableStateOf(settings.assistantName) }
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
                            subtitle = "Ask before placing phone calls or modifying critical settings",
                            checked = settings.confirmConsequentialActions,
                            onCheckedChange = onUpdateConfirmConsequential,
                            accentColor = StatusThinkingAmber,
                            testTag = "switch_settings_confirm_actions"
                        )

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

            // 5. DANGER ZONE STORAGE PURGE
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
