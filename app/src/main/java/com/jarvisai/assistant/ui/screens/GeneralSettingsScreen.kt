package com.jarvisai.assistant.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.JarvisThemeMode
import com.example.ui.theme.StatusListeningEmerald
import com.example.ui.theme.StatusSpeakingAzure
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.SupportedGeminiModels
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.model.VoiceGender
import com.jarvisai.assistant.ui.components.HudCard
import com.jarvisai.assistant.ui.components.HudConfirmationDialog
import com.jarvisai.assistant.ui.components.HudSectionHeader
import com.jarvisai.assistant.ui.components.HudTopBar

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
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var nameInput by rememberSaveable(settings.assistantName) { mutableStateOf(settings.assistantName) }
    var showClearChatConfirm by rememberSaveable { mutableStateOf(false) }
    var showClearMemoriesConfirm by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        HudTopBar(
            title = "SETTINGS",
            subtitle = "GEMINI VOICE (MALE/FEMALE), LANGUAGE, THEME & PRIVACY",
            status = status,
            onBackClick = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. VOICE SETTINGS (MALE & FEMALE GEMINI VOICE SELECTION + PREVIEW)
            item {
                HudSectionHeader(
                    title = "Gemini Voice Settings (Male & Female)",
                    codeBadge = "GEMINI AUDIO 24kHz",
                    icon = Icons.Default.RecordVoiceOver
                )
            }

            item {
                HudCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "SELECT VOICE GENDER (MALE / FEMALE):",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Male Voice vs Female Voice Selection Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VoiceGender.entries.forEach { gender ->
                            val isSelected = settings.voiceGender == gender
                            val accent = if (gender == VoiceGender.MALE) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                StatusSpeakingAzure
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) accent.copy(alpha = 0.20f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) accent else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        onUpdateVoiceGender(gender)
                                    }
                                    .padding(12.dp)
                                    .testTag("card_voice_gender_${gender.name.lowercase()}")
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = gender.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = StatusListeningEmerald,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (gender == VoiceGender.MALE) {
                                            "JARVIS Tactical Male Voice"
                                        } else {
                                            "FRIDAY Clear Female Voice"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "GEMINI LIVE VOICE PERSONA CARDS:",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isTtsSpeaking) {
                                    "Playing real Gemini AI voice preview (${settings.voicePersona})..."
                                } else {
                                    "Tap PREVIEW to hear the actual Gemini prebuilt voice (24kHz PCM)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isTtsSpeaking) StatusListeningEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isTtsSpeaking) {
                            OutlinedButton(
                                onClick = onStopVoicePreview,
                                border = BorderStroke(1.dp, AlertCrimson),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("btn_stop_voice_preview")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.StopCircle,
                                    contentDescription = "Stop Preview",
                                    tint = AlertCrimson,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "STOP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AlertCrimson
                                )
                            }
                        }
                    }

                    if (!ttsErrorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = ttsErrorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = AlertCrimson
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Voice Option Cards with Voice Name, Voice Type (Male/Female), Selected Indicator & Preview Button
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SupportedGeminiModels.voiceOptions.forEach { voiceOpt ->
                            val isSelected = settings.voicePersona.equals(voiceOpt.id, ignoreCase = true)
                            val badgeColor = if (voiceOpt.gender == VoiceGender.MALE) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                StatusSpeakingAzure
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) badgeColor.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) badgeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        onUpdateVoicePersona(voiceOpt.id)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .testTag("voice_card_${voiceOpt.id.lowercase()}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onUpdateVoicePersona(voiceOpt.id) }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = voiceOpt.displayName,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (isSelected) badgeColor else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = voiceOpt.gender.displayName.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = badgeColor,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(badgeColor.copy(alpha = 0.16f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = voiceOpt.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Preview Voice Button (Real Gemini TTS Audio)
                                OutlinedButton(
                                    onClick = {
                                        onUpdateVoicePersona(voiceOpt.id)
                                        onPreviewVoice(voiceOpt.gender, voiceOpt.id)
                                    },
                                    border = BorderStroke(1.dp, badgeColor),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("btn_preview_voice_${voiceOpt.id.lowercase()}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Preview ${voiceOpt.displayName}",
                                        tint = badgeColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "PREVIEW",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    SettingsSwitchRow(
                        title = "Live Voice Floating Call Popup",
                        subtitle = "Live Voice ke dauran Home dabane ya dusra app kholne par floating HUD popup dikhayein",
                        checked = settings.floatingOverlayEnabled,
                        onCheckedChange = onUpdateFloatingOverlay,
                        testTag = "switch_settings_floating_overlay"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "SPEECH RATE: ${String.format("%.2fx", settings.voiceSpeed)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = settings.voiceSpeed,
                        onValueChange = onUpdateVoiceSpeed,
                        valueRange = 0.6f..1.8f
                    )

                    Text(
                        text = "VOICE PITCH: ${String.format("%.2fx", settings.voicePitch)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = settings.voicePitch,
                        onValueChange = onUpdateVoicePitch,
                        valueRange = 0.6f..1.6f
                    )
                }
            }

            // 2. ASSISTANT NAME & LANGUAGE PREFERENCE
            item {
                HudSectionHeader(
                    title = "Assistant Identity & Language",
                    codeBadge = "CORE PERSONA",
                    icon = Icons.Default.Badge
                )
            }

            item {
                HudCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Assistant Designation Name") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_assistant_name_input")
                        )
                        Button(
                            onClick = {
                                onUpdateAssistantName(nameInput)
                                Toast.makeText(context, "Assistant name save ho gaya", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.testTag("btn_save_assistant_name")
                        ) {
                            Text("SAVE", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DEFAULT CONVERSATION LANGUAGE:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SupportedLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = settings.language == lang,
                                onClick = { onUpdateLanguage(lang) },
                                label = { Text(lang.displayName, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("settings_lang_${lang.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // 3. THEME SETTINGS
            item {
                HudSectionHeader(
                    title = "HUD Visual Theme",
                    codeBadge = "DISPLAY MATRIX",
                    icon = Icons.Default.Palette
                )
            }

            item {
                HudCard(modifier = Modifier.fillMaxWidth()) {
                    JarvisThemeMode.entries.forEach { mode ->
                        val isSelected = settings.themeMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else Color.Transparent
                                )
                                .clickable { onUpdateThemeMode(mode) }
                                .padding(vertical = 8.dp, horizontal = 6.dp)
                                .testTag("theme_mode_${mode.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onUpdateThemeMode(mode) }
                            )
                            Column {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = mode.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 4. MEMORY & PRIVACY CONTROLS
            item {
                HudSectionHeader(
                    title = "Memory & Privacy Controls",
                    codeBadge = "ZERO-CLOUD LOCAL",
                    icon = Icons.Default.Security
                )
            }

            item {
                HudCard(modifier = Modifier.fillMaxWidth()) {
                    SettingsSwitchRow(
                        title = "Enable Persistent Memory System",
                        subtitle = "JARVIS aapki zaroori baatein (jaise Name, Favorite language) Room DB mein yaad rakhega",
                        checked = settings.memoryEnabled,
                        onCheckedChange = onUpdateMemoryEnabled,
                        testTag = "switch_settings_memory_enabled"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SettingsSwitchRow(
                        title = "Retrieve Saved Memories in Every Conversation",
                        subtitle = "Har Chat aur Live Voice session mein aapki saved memories automatically inject karein",
                        checked = settings.autoInjectMemories,
                        onCheckedChange = onUpdateAutoInjectMemories,
                        testTag = "switch_settings_auto_inject_memories"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SettingsSwitchRow(
                        title = "Confirm Consequential Phone Actions",
                        subtitle = "Phone calls aur system alarms se pehle confirmation maangein",
                        checked = settings.confirmConsequentialActions,
                        onCheckedChange = onUpdateConfirmConsequential,
                        testTag = "switch_settings_confirm_actions"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SettingsSwitchRow(
                        title = "Privacy Telemetry Redaction",
                        subtitle = "Saara data phone par local rakhein aur sensitive keys mask karein",
                        checked = settings.privacyRedactTelemetry,
                        onCheckedChange = onUpdatePrivacyRedact,
                        testTag = "switch_settings_privacy_redact"
                    )
                }
            }

            // 5. DATA MANAGEMENT (CLEAR CHAT HISTORY & CLEAR MEMORIES)
            item {
                HudSectionHeader(
                    title = "Local Storage Purge Controls",
                    codeBadge = "ROOM DB MANAGEMENT",
                    icon = Icons.Default.Memory
                )
            }

            item {
                HudCard(
                    glowColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showClearChatConfirm = true },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_settings_clear_chats")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CLEAR CHATS ($sessionsCount)",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            onClick = { showClearMemoriesConfirm = true },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_settings_clear_memories")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CLEAR MEMORIES ($memoriesCount)",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showClearChatConfirm) {
        HudConfirmationDialog(
            title = "CLEAR ALL CHAT HISTORY?",
            message = "Permanently delete all $sessionsCount conversation sessions and messages stored on this device?",
            confirmLabel = "CLEAR CHATS",
            isDestructive = true,
            onConfirm = {
                onClearChatHistory()
                Toast.makeText(context, "All chat history cleared", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showClearChatConfirm = false }
        )
    }

    if (showClearMemoriesConfirm) {
        HudConfirmationDialog(
            title = "CLEAR ALL SAVED MEMORIES?",
            message = "Permanently delete all $memoriesCount saved memories from the Memory Center?",
            confirmLabel = "CLEAR MEMORIES",
            isDestructive = true,
            onConfirm = {
                onClearMemories()
                Toast.makeText(context, "All memories cleared", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showClearMemoriesConfirm = false }
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
