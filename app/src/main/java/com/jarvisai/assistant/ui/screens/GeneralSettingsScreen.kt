package com.jarvisai.assistant.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JarvisThemeMode
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.SupportedGeminiModels
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.UserSettings
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
    onBack: () -> Unit,
    onUpdateAssistantName: (String) -> Unit,
    onUpdateLanguage: (SupportedLanguage) -> Unit,
    onUpdateVoicePersona: (String) -> Unit,
    onUpdateVoiceSpeed: (Float) -> Unit,
    onUpdateVoicePitch: (Float) -> Unit,
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
            title = "GENERAL HUD SETTINGS",
            subtitle = "IDENTITY, VOICE, THEME & PRIVACY",
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
            // 1. ASSISTANT NAME & LANGUAGE PREFERENCE
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
                                Toast.makeText(context, "Assistant name updated", Toast.LENGTH_SHORT).show()
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

            // 2. VOICE PREFERENCES
            item {
                HudSectionHeader(
                    title = "Voice Synthesis Preferences",
                    codeBadge = "ACOUSTIC PROFILE",
                    icon = Icons.Default.RecordVoiceOver
                )
            }

            item {
                HudCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "GEMINI LIVE VOICE PERSONA:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    SupportedGeminiModels.voicePersonas.forEach { (personaName, desc) ->
                        val isSelected = settings.voicePersona == personaName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onUpdateVoicePersona(personaName) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onUpdateVoicePersona(personaName) }
                            )
                            Column {
                                Text(
                                    text = personaName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

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
                        title = "Enable Memory System",
                        subtitle = "Allow JARVIS to store and access user-approved memories in local Room DB",
                        checked = settings.memoryEnabled,
                        onCheckedChange = onUpdateMemoryEnabled,
                        testTag = "switch_settings_memory_enabled"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SettingsSwitchRow(
                        title = "Retrieve Relevant Memories in Gemini Context",
                        subtitle = "Automatically include matching user-approved memories in chat & voice prompts",
                        checked = settings.autoInjectMemories,
                        onCheckedChange = onUpdateAutoInjectMemories,
                        testTag = "switch_settings_auto_inject_memories"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SettingsSwitchRow(
                        title = "Confirm Consequential Phone Actions",
                        subtitle = "Always ask operator confirmation before calls, system alarms, and app launches",
                        checked = settings.confirmConsequentialActions,
                        onCheckedChange = onUpdateConfirmConsequential,
                        testTag = "switch_settings_confirm_actions"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SettingsSwitchRow(
                        title = "Privacy Telemetry Redaction",
                        subtitle = "Keep all diagnostic logs strictly local and mask sensitive tokens in UI",
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
            message = "Permanently delete all $memoriesCount user-approved memories from the Memory Center?",
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
