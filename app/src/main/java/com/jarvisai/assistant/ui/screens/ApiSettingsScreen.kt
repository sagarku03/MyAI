package com.jarvisai.assistant.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.StatusListeningEmerald
import com.example.ui.theme.StatusSpeakingAzure
import com.example.ui.theme.StatusThinkingAmber
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.ConnectionTestResult
import com.jarvisai.assistant.model.GeminiModelOption
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.ui.components.HudCard
import com.jarvisai.assistant.ui.components.HudConfirmationDialog
import com.jarvisai.assistant.ui.components.HudSectionHeader
import com.jarvisai.assistant.ui.components.HudTopBar

@Composable
fun ApiSettingsScreen(
    settings: UserSettings,
    status: AssistantStatus,
    currentApiKey: String,
    maskedApiKey: String,
    isKeystoreBacked: Boolean,
    isTestingConnection: Boolean,
    connectionTestResult: ConnectionTestResult?,
    availableTextModels: List<GeminiModelOption>,
    availableVoiceModels: List<GeminiModelOption>,
    onBack: () -> Unit,
    onSaveOrUpdateKey: (String) -> Unit,
    onDeleteKey: () -> Unit,
    onTestConnection: (String) -> Unit,
    onSyncModelsFromApi: (String) -> Unit,
    onSelectTextModel: (String) -> Unit,
    onSelectVoiceModel: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    var keyInput by rememberSaveable { mutableStateOf(currentApiKey) }
    var isKeyVisible by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var customModelInput by rememberSaveable(settings.textModel) { mutableStateOf(settings.textModel) }

    LaunchedEffect(currentApiKey) {
        if (keyInput.isBlank() && currentApiKey.isNotBlank()) {
            keyInput = currentApiKey
        }
    }

    val hasSavedKey = currentApiKey.isNotBlank()

    Column(modifier = Modifier.fillMaxSize()) {
        HudTopBar(
            title = "GEMINI API & MODELS",
            subtitle = "ANDROID KEYSTORE AES-256-GCM VAULT",
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
            // 1. KEYSTORE ENCRYPTION & ACTUAL CONNECTION STATUS
            item {
                HudCard(
                    glowColor = if (hasSavedKey) StatusListeningEmerald else StatusThinkingAmber,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (hasSavedKey) StatusListeningEmerald else StatusThinkingAmber
                            )
                            Column {
                                Text(
                                    text = if (hasSavedKey) "VAULT STATUS: KEY STORED" else "VAULT STATUS: AWAITING API KEY",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (hasSavedKey) StatusListeningEmerald else StatusThinkingAmber
                                )
                                Text(
                                    text = if (isKeystoreBacked) {
                                        "Hardware-backed AndroidKeyStore AES-256-GCM encryption active"
                                    } else {
                                        "Local encrypted vault active"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "STORED KEY SIGNATURE: $maskedApiKey",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "ACTIVE TEXT MODEL: ${settings.textModel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = StatusSpeakingAzure
                    )
                }
            }

            // 2. API KEY INPUT, SHOW/HIDE, SAVE, UPDATE, DELETE, TEST CONNECTION & LISTMODELS SYNC
            item {
                HudSectionHeader(
                    title = "Gemini API Key Management",
                    codeBadge = "DIRECT UPLINK",
                    icon = Icons.Default.Key
                )
            }

            item {
                HudCard(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Gemini API Key (AIza...)") },
                        placeholder = { Text("Enter your Google AI Studio Gemini API Key") },
                        singleLine = true,
                        visualTransformation = if (isKeyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { isKeyVisible = !isKeyVisible },
                                modifier = Modifier.testTag("btn_toggle_key_visibility")
                            ) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isKeyVisible) "Hide API Key" else "Show API Key",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_input_field")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSaveOrUpdateKey(keyInput) },
                            enabled = keyInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_save_api_key")
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasSavedKey) "UPDATE KEY" else "SAVE KEY",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        if (hasSavedKey) {
                            OutlinedButton(
                                onClick = { showDeleteConfirm = true },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag("btn_delete_api_key")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete API Key",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("DELETE", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Test Connection Button
                        OutlinedButton(
                            onClick = { onTestConnection(keyInput) },
                            enabled = !isTestingConnection && (keyInput.isNotBlank() || hasSavedKey),
                            border = BorderStroke(1.dp, StatusSpeakingAzure),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_test_connection")
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(
                                    color = StatusSpeakingAzure,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("TESTING...", color = StatusSpeakingAzure, style = MaterialTheme.typography.labelLarge)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.NetworkCheck,
                                    contentDescription = null,
                                    tint = StatusSpeakingAzure,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "TEST CONNECTION",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = StatusSpeakingAzure
                                )
                            }
                        }

                        // Sync Supported Models via ListModels
                        OutlinedButton(
                            onClick = { onSyncModelsFromApi(keyInput) },
                            enabled = !isTestingConnection && (keyInput.isNotBlank() || hasSavedKey),
                            border = BorderStroke(1.dp, StatusListeningEmerald),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_sync_list_models")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = StatusListeningEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SYNC MODELS",
                                style = MaterialTheme.typography.labelLarge,
                                color = StatusListeningEmerald
                            )
                        }
                    }

                    // Actual Connection Test Result Output
                    if (connectionTestResult != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val resultColor = if (connectionTestResult.success) StatusListeningEmerald else AlertCrimson
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(resultColor.copy(alpha = 0.14f))
                                .border(1.dp, resultColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(10.dp)
                                .testTag("connection_test_result_box"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (connectionTestResult.success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = resultColor
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (connectionTestResult.success) "CONNECTION VERIFIED" else "CONNECTION FAILED",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = resultColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = connectionTestResult.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 3. TEXT MODEL SELECTION (WITH DYNAMIC ListModels & CUSTOM INPUT)
            item {
                HudSectionHeader(
                    title = "Text Chat Model Selection (${availableTextModels.size})",
                    codeBadge = "LISTMODELS + AUTO-HEAL",
                    icon = Icons.Default.Psychology
                )
            }

            item {
                HudCard(modifier = Modifier.fillMaxWidth()) {
                    // Custom Model ID Override Box
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customModelInput,
                            onValueChange = { customModelInput = it },
                            label = { Text("Active / Custom Model ID") },
                            placeholder = { Text("e.g. gemini-3-flash-preview or gemini-3.8-flash") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("custom_text_model_input")
                        )
                        Button(
                            onClick = { onSelectTextModel(customModelInput) },
                            enabled = customModelInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.Black
                            )
                        ) {
                            Text("APPLY", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    availableTextModels.take(12).forEach { model ->
                        val isSelected = settings.textModel == model.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    customModelInput = model.id
                                    onSelectTextModel(model.id)
                                }
                                .padding(vertical = 8.dp, horizontal = 6.dp)
                                .testTag("text_model_${model.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    customModelInput = model.id
                                    onSelectTextModel(model.id)
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${model.displayName} (${model.id})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = model.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        HudConfirmationDialog(
            title = "DELETE STORED API KEY?",
            message = "This will permanently delete your encrypted Gemini API key from the Android Keystore vault.",
            confirmLabel = "DELETE KEY",
            isDestructive = true,
            onConfirm = {
                keyInput = ""
                onDeleteKey()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
