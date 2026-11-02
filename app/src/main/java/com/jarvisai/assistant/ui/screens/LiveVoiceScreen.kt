package com.jarvisai.assistant.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PictureInPictureAlt
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.StatusListeningEmerald
import com.example.ui.theme.StatusSpeakingAzure
import com.example.ui.theme.StatusThinkingAmber
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.LiveVoiceUiStateStatus
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.service.LiveTranscriptItem
import com.jarvisai.assistant.service.LiveVoiceForegroundService
import com.jarvisai.assistant.ui.components.ArcReactorView
import com.jarvisai.assistant.ui.components.HudCard
import com.jarvisai.assistant.ui.components.HudTopBar

@Composable
fun LiveVoiceScreen(
    settings: UserSettings,
    status: AssistantStatus,
    liveUiStatus: LiveVoiceUiStateStatus = LiveVoiceUiStateStatus.STANDBY,
    hasApiKey: Boolean,
    isSessionActive: Boolean,
    isMicMuted: Boolean,
    audioLevel: Float,
    connectionLabel: String,
    errorMessage: String?,
    transcripts: List<LiveTranscriptItem>,
    memorySavedNotification: String? = null,
    onBack: () -> Unit,
    onSelectLanguage: (SupportedLanguage) -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onToggleMute: () -> Unit,
    onInterrupt: () -> Unit,
    onSendLiveText: (String) -> Unit,
    onExecuteQuickPhoneCommand: (String) -> Unit = {},
    onNavigateToApiSettings: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasOverlayPermission by remember {
        mutableStateOf(LiveVoiceForegroundService.canDrawOverlays(context))
    }
    var permissionDeniedNotice by remember { mutableStateOf(false) }
    var livePromptInput by rememberSaveable { mutableStateOf("") }

    // Refresh permission states when returning from Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMicPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                hasOverlayPermission = LiveVoiceForegroundService.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onStartSession()
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        permissionDeniedNotice = !granted
        if (granted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onStartSession()
            }
        }
    }

    val transcriptListState = rememberLazyListState()
    LaunchedEffect(transcripts.size) {
        if (transcripts.isNotEmpty()) {
            transcriptListState.animateScrollToItem(transcripts.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HudTopBar(
            title = "GEMINI LIVE VOICE",
            subtitle = "${liveUiStatus.displayLabel} // ${settings.voiceGender.displayName} (${settings.voicePersona})",
            status = status,
            onBackClick = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Language Mode Bar (Hinglish / Hindi / English)
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
                            text = "Live Voice ke liye Gemini API Key required hai. API Settings mein key enter karne ke liye tap karein.",
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
                        text = "Microphone Error... (Microphone permission required hai.)",
                        style = MaterialTheme.typography.labelLarge,
                        color = AlertCrimson
                    )
                    Text(
                        text = "Live Voice mein aapki aawaz sunne ke liye RECORD_AUDIO permission allow karein.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Floating HUD Popup Permission Card (Problem 6)
            if (settings.floatingOverlayEnabled && !hasOverlayPermission) {
                HudCard(
                    glowColor = StatusSpeakingAzure,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = null,
                                tint = StatusSpeakingAzure,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "FLOATING CALL POPUP PERMISSION",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = StatusSpeakingAzure,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Home button dabane ya YouTube kholne par floating call popup dikhane ke liye Overlay permission allow karein.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    try {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        ).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                    }
                                }
                            },
                            border = BorderStroke(1.dp, StatusSpeakingAzure),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("btn_grant_overlay_permission")
                        ) {
                            Text(
                                text = "ALLOW",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusSpeakingAzure
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = !memorySavedNotification.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusListeningEmerald.copy(alpha = 0.16f))
                        .border(1.dp, StatusListeningEmerald, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StatusListeningEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = memorySavedNotification.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusListeningEmerald,
                        fontWeight = FontWeight.Bold
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

            // Central Live Arc Reactor & Live Status Banner
            HudCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(150.dp),
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

                    Spacer(modifier = Modifier.height(6.dp))

                    // Prominent Real-Time Status Indicator (Problem 1 Requirement)
                    val statusBadgeColor = when (liveUiStatus) {
                        LiveVoiceUiStateStatus.CONNECTING -> StatusThinkingAmber
                        LiveVoiceUiStateStatus.LISTENING -> StatusListeningEmerald
                        LiveVoiceUiStateStatus.USER_SPEAKING -> StatusListeningEmerald
                        LiveVoiceUiStateStatus.PROCESSING -> StatusThinkingAmber
                        LiveVoiceUiStateStatus.AI_SPEAKING -> StatusSpeakingAzure
                        LiveVoiceUiStateStatus.MIC_ERROR,
                        LiveVoiceUiStateStatus.CONNECTION_ERROR -> AlertCrimson
                        LiveVoiceUiStateStatus.STANDBY -> MaterialTheme.colorScheme.primary
                    }

                    Text(
                        text = liveUiStatus.displayLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = statusBadgeColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("live_voice_status_label")
                    )
                    Text(
                        text = liveUiStatus.hinglishSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Real-time Acoustic Spectrum Visualizer
                    LiveAudioSpectrumStrip(
                        audioLevel = audioLevel,
                        isActive = isSessionActive && !isMicMuted,
                        status = status
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Status Indicator Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        LiveStatusIndicatorChip(
                            label = "Listening...",
                            active = isSessionActive && (liveUiStatus == LiveVoiceUiStateStatus.LISTENING || liveUiStatus == LiveVoiceUiStateStatus.USER_SPEAKING),
                            activeColor = StatusListeningEmerald
                        )
                        LiveStatusIndicatorChip(
                            label = "Processing...",
                            active = isSessionActive && (liveUiStatus == LiveVoiceUiStateStatus.PROCESSING || liveUiStatus == LiveVoiceUiStateStatus.CONNECTING),
                            activeColor = StatusThinkingAmber
                        )
                        LiveStatusIndicatorChip(
                            label = "AI Speaking...",
                            active = isSessionActive && liveUiStatus == LiveVoiceUiStateStatus.AI_SPEAKING,
                            activeColor = StatusSpeakingAzure
                        )
                    }
                }
            }

            // Quick Spoken / Tap Phone Control Commands Bar (Problem 7)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val quickVoiceCommands = listOf(
                    "JARVIS, YouTube kholo",
                    "JARVIS, YouTube par search karo latest technology videos",
                    "JARVIS, Chrome kholo",
                    "JARVIS, Settings kholo"
                )
                quickVoiceCommands.forEach { cmd ->
                    OutlinedButton(
                        onClick = {
                            if (isSessionActive) {
                                onSendLiveText(cmd)
                            } else {
                                onExecuteQuickPhoneCommand(cmd)
                            }
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = cmd.removePrefix("JARVIS, "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Live Session Transcript Log (User Spoken Transcript + JARVIS Spoken Transcript)
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
                            text = "LIVE VOICE TRANSCRIPT (USER + JARVIS)",
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
                                "Ji, main sun raha hoon... Aap Hindi, Hinglish ya English mein boliye (jaise: 'JARVIS, YouTube kholo')."
                            } else {
                                "START LIVE VOICE SESSION dabayein aur phone call ki tarah seedhe JARVIS se baat karein."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = transcriptListState,
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

            // Live Text Prompt Input inside Live Voice
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
                                "Boliye ya yahan type karein (e.g. YouTube kholo)...",
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
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onStartSession()
                                }
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
            .height(26.dp)
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
