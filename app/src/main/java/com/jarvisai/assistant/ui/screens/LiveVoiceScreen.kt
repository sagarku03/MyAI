package com.jarvisai.assistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.StatusListeningEmerald
import com.example.ui.theme.StatusSpeakingAzure
import com.example.ui.theme.StatusThinkingAmber
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.service.LiveTranscriptItem
import com.jarvisai.assistant.ui.components.ArcReactorView
import com.jarvisai.assistant.ui.components.HudCard
import com.jarvisai.assistant.ui.components.HudTopBar

@Composable
fun LiveVoiceScreen(
    settings: UserSettings,
    status: AssistantStatus,
    hasApiKey: Boolean,
    isSessionActive: Boolean,
    isMicMuted: Boolean,
    audioLevel: Float,
    connectionLabel: String,
    errorMessage: String?,
    transcripts: List<LiveTranscriptItem>,
    onBack: () -> Unit,
    onSelectLanguage: (SupportedLanguage) -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onToggleMute: () -> Unit,
    onInterrupt: () -> Unit,
    onSendLiveText: (String) -> Unit,
    onNavigateToApiSettings: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedNotice by remember { mutableStateOf(false) }
    var livePromptInput by rememberSaveable { mutableStateOf("") }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        permissionDeniedNotice = !granted
        if (granted) {
            onStartSession()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HudTopBar(
            title = "GEMINI LIVE VOICE",
            subtitle = "$connectionLabel // ${settings.voicePersona.uppercase()}",
            status = status,
            onBackClick = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Language Mode Bar (English / Hindi / Hinglish)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VOICE LANG:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SupportedLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = settings.language == lang,
                        onClick = { onSelectLanguage(lang) },
                        label = { Text(lang.displayName, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("voice_lang_${lang.name.lowercase()}")
                    )
                }
            }

            if (!hasApiKey) {
                HudCard(
                    glowColor = StatusThinkingAmber,
                    onClick = onNavigateToApiSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = StatusThinkingAmber
                        )
                        Text(
                            text = "Gemini API Key required for Live Voice WebSocket streaming. Tap to configure.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusThinkingAmber
                        )
                    }
                }
            }

            if (permissionDeniedNotice && !hasMicPermission) {
                HudCard(
                    glowColor = AlertCrimson,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "MICROPHONE PERMISSION REQUIRED",
                        style = MaterialTheme.typography.labelLarge,
                        color = AlertCrimson
                    )
                    Text(
                        text = "Live Voice requires RECORD_AUDIO permission to stream 16kHz PCM audio from your microphone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                HudCard(
                    glowColor = AlertCrimson,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = AlertCrimson
                    )
                }
            }

            // Central Live Arc Reactor & Acoustic Spectrum Bars
            HudCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(175.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ArcReactorView(
                            status = status,
                            audioLevel = audioLevel,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("live_voice_arc_reactor")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Real-time Acoustic Spectrum Visualizer
                    LiveAudioSpectrumStrip(
                        audioLevel = audioLevel,
                        isActive = isSessionActive && !isMicMuted,
                        status = status
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Status Indicator Row: Listening, Thinking, Speaking
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        LiveStatusIndicatorChip(
                            label = "LISTENING",
                            active = isSessionActive && status == AssistantStatus.LISTENING,
                            activeColor = StatusListeningEmerald
                        )
                        LiveStatusIndicatorChip(
                            label = "THINKING",
                            active = isSessionActive && status == AssistantStatus.THINKING,
                            activeColor = StatusThinkingAmber
                        )
                        LiveStatusIndicatorChip(
                            label = "SPEAKING",
                            active = isSessionActive && status == AssistantStatus.SPEAKING,
                            activeColor = StatusSpeakingAzure
                        )
                    }
                }
            }

            // Live Session Transcript Log
            HudCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE AUDIO TELEMETRY & TRANSCRIPT",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "PCM 16kHz IN / 24kHz OUT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (transcripts.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isSessionActive) {
                                "Listening for your voice... Speak naturally in ${settings.language.displayName}."
                            } else {
                                "Tap START LIVE SESSION below to establish real-time bidirectional Gemini audio."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(transcripts, key = { it.id }) { item ->
                            val speakerColor = when (item.speaker) {
                                "USER" -> StatusListeningEmerald
                                "JARVIS" -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "[${item.speaker}] ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = speakerColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Live Text Prompt Input (when session is active, user can also send text to hear spoken live response)
            if (isSessionActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = livePromptInput,
                        onValueChange = { livePromptInput = it },
                        placeholder = {
                            Text(
                                "Or type a live prompt for spoken response...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("live_text_input")
                    )
                    IconButton(
                        onClick = {
                            if (livePromptInput.isNotBlank()) {
                                onSendLiveText(livePromptInput)
                                livePromptInput = ""
                            }
                        },
                        modifier = Modifier.testTag("btn_send_live_text")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send to Live Voice",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Primary Session Controls: Start/End Session, Mute Mic, Interrupt
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isSessionActive) {
                    Button(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                hasMicPermission = true
                                onStartSession()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusListeningEmerald,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_start_live_voice")
                    ) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "START LIVE VOICE SESSION",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // Mute / Unmute Button
                    OutlinedButton(
                        onClick = onToggleMute,
                        border = BorderStroke(
                            1.dp,
                            if (isMicMuted) StatusThinkingAmber else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("btn_mute_live_voice")
                    ) {
                        Icon(
                            imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Toggle Microphone Mute",
                            tint = if (isMicMuted) StatusThinkingAmber else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isMicMuted) "UNMUTE" else "MUTE",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isMicMuted) StatusThinkingAmber else MaterialTheme.colorScheme.primary
                        )
                    }

                    // Interrupt Button
                    OutlinedButton(
                        onClick = onInterrupt,
                        border = BorderStroke(1.dp, StatusSpeakingAzure),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("btn_interrupt_live_voice")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PanTool,
                            contentDescription = "Interrupt AI Audio",
                            tint = StatusSpeakingAzure,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "INTERRUPT",
                            style = MaterialTheme.typography.labelLarge,
                            color = StatusSpeakingAzure
                        )
                    }

                    // End Session Button
                    Button(
                        onClick = onStopSession,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlertCrimson,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("btn_end_live_voice")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Live Session"
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "END",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveAudioSpectrumStrip(
    audioLevel: Float,
    isActive: Boolean,
    status: AssistantStatus
) {
    val barColor = when (status) {
        AssistantStatus.LISTENING -> StatusListeningEmerald
        AssistantStatus.THINKING -> StatusThinkingAmber
        AssistantStatus.SPEAKING -> StatusSpeakingAzure
        AssistantStatus.IDLE -> MaterialTheme.colorScheme.primary
    }
    val multipliers = listOf(0.35f, 0.55f, 0.8f, 1.0f, 0.7f, 0.95f, 0.6f, 0.85f, 0.45f, 0.75f, 0.5f, 0.3f)

    Row(
        modifier = Modifier
            .height(28.dp)
            .fillMaxWidth(0.75f),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        multipliers.forEach { factor ->
            val targetFraction = if (isActive) {
                (0.18f + (audioLevel * factor * 0.82f)).coerceIn(0.15f, 1f)
            } else {
                0.15f
            }
            val animatedFraction by animateFloatAsState(targetValue = targetFraction, label = "barHeight")
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight(animatedFraction)
                    .clip(RoundedCornerShape(50))
                    .background(barColor.copy(alpha = if (isActive) 0.9f else 0.3f))
            )
        }
    }
}

@Composable
private fun LiveStatusIndicatorChip(
    label: String,
    active: Boolean,
    activeColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) activeColor.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                1.dp,
                if (active) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(50)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
