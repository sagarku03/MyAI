package com.jarvisai.assistant.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.StatusListeningEmerald
import com.example.ui.theme.StatusSpeakingAzure
import com.example.ui.theme.StatusThinkingAmber
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.ui.components.ArcReactorView
import com.jarvisai.assistant.ui.components.HudCard
import com.jarvisai.assistant.ui.components.HudSectionHeader
import com.jarvisai.assistant.ui.components.HudTopBar

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
    onStatusPreviewSelect: (AssistantStatus?) -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToControls: () -> Unit,
    onNavigateToApiSettings: () -> Unit,
    onNavigateToGeneralSettings: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HudTopBar(
            title = "${settings.assistantName} AI",
            subtitle = "MARK VII CYBERNETIC HUD // ${settings.language.displayName.uppercase()}",
            status = status
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. CENTRAL ANIMATED ARC REACTOR & TELEMETRY
            item {
                HudCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("arc_reactor_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CORE REACTOR TELEMETRY",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (hasApiKey) "UPLINK: READY" else "UPLINK: KEY REQUIRED",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hasApiKey) StatusListeningEmerald else StatusThinkingAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ArcReactorView(
                            status = status,
                            audioLevel = audioLevel,
                            modifier = Modifier
                                .size(210.dp)
                                .testTag("home_arc_reactor")
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Assistant Status Readout
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = status.hudCode,
                            style = MaterialTheme.typography.titleLarge,
                            color = when (status) {
                                AssistantStatus.IDLE -> MaterialTheme.colorScheme.primary
                                AssistantStatus.LISTENING -> StatusListeningEmerald
                                AssistantStatus.THINKING -> StatusThinkingAmber
                                AssistantStatus.SPEAKING -> StatusSpeakingAzure
                            },
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = status.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Assistant Status Indicators (Idle, Listening, Thinking, Speaking)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistantStatus.entries.forEach { entry ->
                            val isSelected = status == entry
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected && entry != AssistantStatus.IDLE) {
                                        onStatusPreviewSelect(null)
                                    } else {
                                        onStatusPreviewSelect(entry)
                                    }
                                },
                                label = {
                                    Text(
                                        text = entry.label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("status_chip_${entry.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // 2. API KEY QUICK ALERT IF NOT CONFIGURED
            if (!hasApiKey) {
                item {
                    HudCard(
                        glowColor = StatusThinkingAmber,
                        onClick = onNavigateToApiSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("missing_api_key_banner")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Configure Gemini API Key",
                                tint = StatusThinkingAmber,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "GEMINI API KEY REQUIRED",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = StatusThinkingAmber
                                )
                                Text(
                                    text = "Tap to securely store your Gemini API Key in Android Keystore for Text Chat and Live Voice.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 3. SIX PRIMARY COMMAND MODULES
            item {
                HudSectionHeader(
                    title = "Tactical Command Modules",
                    codeBadge = "SYS-06 // ACTIVE",
                    icon = Icons.Default.Psychology
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Row 1: Voice Assistant & Text Chat
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DashboardModuleButton(
                            title = "Voice Assistant",
                            subtitle = "Gemini Live Audio",
                            badge = "REAL-TIME",
                            icon = Icons.Default.Mic,
                            accentColor = StatusListeningEmerald,
                            testTag = "btn_voice_assistant",
                            onClick = onNavigateToVoice,
                            modifier = Modifier.weight(1f)
                        )
                        DashboardModuleButton(
                            title = "Text Chat",
                            subtitle = "$sessionsCount Sessions Saved",
                            badge = settings.textModel.removePrefix("gemini-").uppercase(),
                            icon = Icons.AutoMirrored.Filled.Chat,
                            accentColor = MaterialTheme.colorScheme.primary,
                            testTag = "btn_text_chat",
                            onClick = onNavigateToChat,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 2: Memory Center & Phone Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DashboardModuleButton(
                            title = "Memory Center",
                            subtitle = "$memoriesCount Approved Memories",
                            badge = if (settings.memoryEnabled) "ENABLED" else "PAUSED",
                            icon = Icons.Default.Memory,
                            accentColor = StatusSpeakingAzure,
                            testTag = "btn_memory_center",
                            onClick = onNavigateToMemory,
                            modifier = Modifier.weight(1f)
                        )
                        DashboardModuleButton(
                            title = "Phone Controls",
                            subtitle = if (isFlashlightOn) "Torch: ON • System" else "Apps, Torch & Calls",
                            badge = "ANDROID OS",
                            icon = if (isFlashlightOn) Icons.Default.FlashlightOn else Icons.Default.Build,
                            accentColor = StatusThinkingAmber,
                            testTag = "btn_phone_controls",
                            onClick = onNavigateToControls,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 3: API Settings & General Settings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DashboardModuleButton(
                            title = "API Settings",
                            subtitle = if (hasApiKey) "Keystore Encrypted" else "Add Gemini Key",
                            badge = "KEYSTORE",
                            icon = Icons.Default.VerifiedUser,
                            accentColor = if (hasApiKey) MaterialTheme.colorScheme.primary else StatusThinkingAmber,
                            testTag = "btn_api_settings",
                            onClick = onNavigateToApiSettings,
                            modifier = Modifier.weight(1f)
                        )
                        DashboardModuleButton(
                            title = "General Settings",
                            subtitle = "${settings.assistantName} • ${settings.language.displayName}",
                            badge = "HUD CONFIG",
                            icon = Icons.Default.Settings,
                            accentColor = MaterialTheme.colorScheme.secondary,
                            testTag = "btn_general_settings",
                            onClick = onNavigateToGeneralSettings,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DashboardModuleButton(
    title: String,
    subtitle: String,
    badge: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HudCard(
        glowColor = accentColor,
        onClick = onClick,
        modifier = modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f))
                    .border(BorderStroke(1.dp, accentColor.copy(alpha = 0.65f)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
