package com.jarvisai.assistant.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CancelPresentation
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.AccentChampagneGold
import com.example.ui.theme.AccentMintEmerald
import com.example.ui.theme.AccentRosePink
import com.example.ui.theme.AccentSkyCyan
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.LiquidGlassElevated
import com.example.ui.theme.LiquidGlassSurface
import com.example.ui.theme.RoyalIndigoAccent
import com.example.ui.theme.RoyalPurplePrimary
import com.example.ui.theme.SoftLavenderGlow
import com.example.ui.theme.TextLavenderMuted
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.CompanionEmotionMood
import com.jarvisai.assistant.model.LiveVoiceUiStateStatus
import com.jarvisai.assistant.model.PersonalityMode
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.service.JarvisAccessibilityService
import com.jarvisai.assistant.service.LiveTranscriptItem
import com.jarvisai.assistant.service.LiveVoiceForegroundService
import com.jarvisai.assistant.ui.components.JarvisAIOrb
import com.jarvisai.assistant.ui.components.JarvisAudioWaveform
import com.jarvisai.assistant.ui.components.JarvisPremiumTopBar

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
    isScreenSharing: Boolean = false,
    screenFramesSent: Long = 0L,
    memorySavedNotification: String? = null,
    onBack: () -> Unit,
    onSelectLanguage: (SupportedLanguage) -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onToggleMute: () -> Unit,
    onInterrupt: () -> Unit,
    onSendLiveText: (String) -> Unit,
    onExecuteQuickPhoneCommand: (String) -> Unit = {},
    onStartScreenShare: (Int, Intent) -> Unit = { _, _ -> },
    onStopScreenShare: () -> Unit = {},
    onScreenShareDenied: () -> Unit = {},
    onNavigateToApiSettings: () -> Unit,
    companionEmotionMood: CompanionEmotionMood = CompanionEmotionMood.COMPOSED,
    onSelectPersonalityMode: (PersonalityMode) -> Unit = {},
    onTriggerProactiveCompanionNow: () -> Unit = {}
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
    val a11yConnectedFlow by JarvisAccessibilityService.isConnectedFlow.collectAsState()
    var isAccessibilityEnabled by remember(a11yConnectedFlow) {
        mutableStateOf(a11yConnectedFlow || JarvisAccessibilityService.isAccessibilityEnabled(context))
    }
    var isAccessibilityMalfunctioning by remember(a11yConnectedFlow) {
        mutableStateOf(!a11yConnectedFlow && JarvisAccessibilityService.isAccessibilityMalfunctioning(context))
    }
    var permissionDeniedNotice by remember { mutableStateOf(false) }
    var livePromptInput by rememberSaveable { mutableStateOf("") }
    var isTranscriptExpanded by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMicPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                hasOverlayPermission = LiveVoiceForegroundService.canDrawOverlays(context)
                isAccessibilityEnabled = JarvisAccessibilityService.isConnectedFlow.value ||
                    JarvisAccessibilityService.isAccessibilityEnabled(context)
                isAccessibilityMalfunctioning = !isAccessibilityEnabled &&
                    JarvisAccessibilityService.isAccessibilityMalfunctioning(context)
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

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            onStartScreenShare(result.resultCode, data)
        } else {
            onScreenShareDenied()
        }
    }

    val transcriptListState = rememberLazyListState()
    LaunchedEffect(transcripts.size) {
        if (transcripts.isNotEmpty()) {
            transcriptListState.animateScrollToItem(transcripts.size - 1)
        }
    }

    val handleStartCall = {
        if (hasMicPermission) {
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
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. APPLE iOS 26 FLOATING CALL HEADER
        JarvisPremiumTopBar(
            title = "Live Voice & Companion",
            subtitle = "${settings.personalityMode.displayName} • ${settings.voicePersona}",
            status = status,
            onBackClick = onBack
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 2. BILINGUAL LANGUAGE & PERSONA STUDIO DECK
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF281B48).copy(alpha = 0.94f),
                                    Color(0xFF1A213C).copy(alpha = 0.94f)
                                )
                            )
                        )
                        .border(
                            width = 1.2.dp,
                            brush = Brush.horizontalGradient(
                                listOf(
                                    AccentRosePink,
                                    RoyalPurplePrimary,
                                    AccentSkyCyan
                                )
                            ),
                            shape = RoundedCornerShape(26.dp)
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Top Row: Active Persona + Mood Badge + Speak First Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(AccentRosePink, RoyalPurplePrimary)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = companionEmotionMood.emoji,
                                    fontSize = 20.sp
                                )
                            }
                            Column {
                                Text(
                                    text = settings.personalityMode.displayName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "${companionEmotionMood.displayLabel} • ${settings.voicePersona}",
                                    color = AccentSkyCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(AccentRosePink, RoyalPurplePrimary)
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                                .clickable { onTriggerProactiveCompanionNow() }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("voice_btn_proactive_speak"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✨ Speak First",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Horizontal Persona Switcher Cards
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PersonalityMode.entries.forEach { mode ->
                            val isSelected = settings.personalityMode == mode
                            val isRomanticOrAdult = mode == PersonalityMode.ADULT_INTIMATE_18_PLUS ||
                                mode == PersonalityMode.ROMANTIC_GIRLFRIEND ||
                                mode == PersonalityMode.GIRLFRIEND
                            val chipAccent = if (isRomanticOrAdult) AccentRosePink else RoyalPurplePrimary
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSelected) {
                                            Brush.horizontalGradient(
                                                listOf(chipAccent, RoyalIndigoAccent)
                                            )
                                        } else {
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFF121629), Color(0xFF121629))
                                            )
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable { onSelectPersonalityMode(mode) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                                    .testTag("voice_persona_chip_${mode.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.displayName,
                                    color = if (isSelected) Color.White else TextLavenderMuted,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    // Segmented Language Pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0E1222))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SupportedLanguage.entries.forEach { lang ->
                            val selected = settings.language == lang
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) {
                                            Brush.horizontalGradient(
                                                listOf(AccentSkyCyan, RoyalPurplePrimary)
                                            )
                                        } else {
                                            Brush.horizontalGradient(
                                                listOf(Color.Transparent, Color.Transparent)
                                            )
                                        }
                                    )
                                    .clickable { onSelectLanguage(lang) }
                                    .padding(vertical = 8.dp)
                                    .testTag("voice_lang_${lang.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lang.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) Color.White else TextLavenderMuted,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }

            // 3. MISSING API KEY BANNER (IF APPLICABLE)
            if (!hasApiKey) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(AccentChampagneGold.copy(alpha = 0.20f))
                            .border(1.2.dp, AccentChampagneGold, RoundedCornerShape(22.dp))
                            .clickable(onClick = onNavigateToApiSettings)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = AccentChampagneGold
                                )
                                Column {
                                    Text(
                                        text = "API Key Required for Live Voice",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "Tap to configure your Gemini API key in Settings Vault",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFFDE68A),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = AccentChampagneGold
                            )
                        }
                    }
                }
            }

            // 4. APPLE iOS 26 FACETIME / SIRI MULTI-COLOR CALL STAGE
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF311B61),
                                    Color(0xFF14213D),
                                    Color(0xFF4A154B)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(900f, 900f)
                            )
                        )
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.55f),
                                    RoyalPurplePrimary,
                                    AccentSkyCyan,
                                    AccentRosePink
                                )
                            ),
                            shape = RoundedCornerShape(32.dp)
                        )
                        .padding(22.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Top Connection & Duplex Badge Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .border(1.dp, AccentSkyCyan.copy(alpha = 0.65f), RoundedCornerShape(999.dp))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = connectionLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentSkyCyan,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (isSessionActive) AccentMintEmerald.copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.12f)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (isSessionActive) "● LIVE DUPLEX" else "STANDBY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSessionActive) AccentMintEmerald else Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        // Compact Luminous Siri / AI Sphere + Live Status
                        Box(
                            modifier = Modifier
                                .size(136.dp)
                                .clickable {
                                    if (!isSessionActive && hasApiKey) {
                                        handleStartCall()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            JarvisAIOrb(
                                status = status,
                                audioLevel = audioLevel,
                                modifier = Modifier
                                    .size(132.dp)
                                    .testTag("live_voice_arc_reactor")
                            )
                        }

                        Text(
                            text = liveUiStatus.displayLabel,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("live_voice_status_label")
                        )

                        Text(
                            text = liveUiStatus.hinglishSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        // Real-time Multi-Color Audio Waveform
                        JarvisAudioWaveform(
                            audioLevel = audioLevel,
                            isActive = isSessionActive && !isMicMuted,
                            status = status
                        )

                        // iOS FaceTime Style Call Action Dock
                        if (!isSessionActive) {
                            Button(
                                onClick = handleStartCall,
                                enabled = hasApiKey,
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentRosePink,
                                    contentColor = Color.White,
                                    disabledContainerColor = Color(0xFF282E4A),
                                    disabledContentColor = TextLavenderMuted
                                ),
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 15.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .border(
                                        1.2.dp,
                                        Color.White.copy(alpha = 0.55f),
                                        RoundedCornerShape(22.dp)
                                    )
                                    .testTag("btn_start_live_voice")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Start Live Voice Call",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Mute Pill
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            if (isMicMuted) AccentChampagneGold
                                            else Color.Black.copy(alpha = 0.38f)
                                        )
                                        .border(
                                            1.dp,
                                            if (isMicMuted) Color.White else AccentSkyCyan,
                                            RoundedCornerShape(18.dp)
                                        )
                                        .clickable(onClick = onToggleMute)
                                        .testTag("btn_mute_live_voice"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                            contentDescription = "Mute",
                                            tint = if (isMicMuted) Color(0xFF1A1002) else AccentSkyCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (isMicMuted) "Unmute" else "Mute",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isMicMuted) Color(0xFF1A1002) else Color.White,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                // Interrupt Pill
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color.Black.copy(alpha = 0.38f))
                                        .border(1.dp, RoyalPurplePrimary, RoundedCornerShape(18.dp))
                                        .clickable(onClick = onInterrupt)
                                        .testTag("btn_interrupt_live_voice"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PanTool,
                                            contentDescription = "Interrupt",
                                            tint = SoftLavenderGlow,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Text(
                                            text = "Interrupt",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                // End Call Crimson Pill
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(AlertCrimson)
                                        .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                                        .clickable(onClick = onStopSession)
                                        .testTag("btn_end_live_voice"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CallEnd,
                                            contentDescription = "End Call",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "End",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. MULTI-COLOR BENTO CARDS FOR SCREEN SHARE & TOUCH AGENT
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Screen Share Bento Card (Sky Cyan / Mint Emerald)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF0C4A6E).copy(alpha = 0.78f),
                                        Color(0xFF151B30).copy(alpha = 0.94f)
                                    )
                                )
                            )
                            .border(
                                1.2.dp,
                                if (isScreenSharing) AccentMintEmerald else AccentSkyCyan.copy(alpha = 0.65f),
                                RoundedCornerShape(22.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("screen_share_control_bar"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isScreenSharing) AccentMintEmerald else AccentSkyCyan),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isScreenSharing) Icons.Default.CancelPresentation else Icons.Default.PresentToAll,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = if (isScreenSharing) "Live Screen Vision Active" else "Live Screen Vision",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (isScreenSharing) "Streaming 1 FPS ($screenFramesSent frames sent)"
                                    else "Let JARVIS see your screen in real time",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.80f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (!isScreenSharing) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(AccentSkyCyan)
                                    .clickable {
                                        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                                        if (mpm != null) {
                                            screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .testTag("btn_start_screen_share")
                            ) {
                                Text(
                                    text = "Share",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF04101E),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(AlertCrimson)
                                    .clickable(onClick = onStopScreenShare)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .testTag("btn_stop_screen_share")
                            ) {
                                Text(
                                    text = "Stop",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    // Accessibility Touch Agent Bento Card (Mint Emerald / Amber Gold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        if (isAccessibilityEnabled) Color(0xFF065F46).copy(alpha = 0.78f)
                                        else Color(0xFF78350F).copy(alpha = 0.78f),
                                        Color(0xFF151B30).copy(alpha = 0.94f)
                                    )
                                )
                            )
                            .border(
                                1.2.dp,
                                if (isAccessibilityEnabled) AccentMintEmerald else AccentChampagneGold,
                                RoundedCornerShape(22.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("accessibility_service_status_bar"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isAccessibilityEnabled) AccentMintEmerald else AccentChampagneGold
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isAccessibilityEnabled) Icons.Default.CheckCircle else Icons.Default.TouchApp,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = when {
                                        isAccessibilityEnabled -> "Autonomous Touch Agent Online"
                                        isAccessibilityMalfunctioning -> "Touch Agent Needs Quick Reset"
                                        else -> "Enable Screen Touch Agent"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = when {
                                        isAccessibilityEnabled -> "Can click, scroll, type & control apps"
                                        isAccessibilityMalfunctioning -> "Settings mein JARVIS AI ko ek baar OFF karke wapas ON karein"
                                        else -> "Required for hands-free UI clicks & scrolling"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.80f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (!isAccessibilityEnabled) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(AccentChampagneGold)
                                    .clickable {
                                        JarvisAccessibilityService.openAccessibilitySettings(context)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .testTag("btn_open_accessibility_settings")
                            ) {
                                Text(
                                    text = if (isAccessibilityMalfunctioning) "Reset" else "Enable",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF1A1002),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    // Floating Overlay Permission Row
                    if (settings.floatingOverlayEnabled && !hasOverlayPermission) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF1E1B4B))
                                .border(1.dp, RoyalPurplePrimary, RoundedCornerShape(22.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = null,
                                    tint = SoftLavenderGlow
                                )
                                Column {
                                    Text(
                                        text = "Floating Call Bubble",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "Allow overlay to control call over other apps",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextLavenderMuted
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(RoyalPurplePrimary)
                                    .clickable {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                        context.startActivity(intent)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .testTag("btn_grant_overlay_permission")
                            ) {
                                Text(
                                    text = "Allow",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }

            // 6. ERROR / PERMISSION / MEMORY BANNERS
            if (!errorMessage.isNullOrBlank() || permissionDeniedNotice || !memorySavedNotification.isNullOrBlank()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (permissionDeniedNotice) {
                            Text(
                                text = "Microphone permission is required for Live Voice.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AlertCrimson,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (!errorMessage.isNullOrBlank()) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = AlertCrimson,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (!memorySavedNotification.isNullOrBlank()) {
                            Text(
                                text = memorySavedNotification,
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentMintEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 7. QUICK VOICE / AGENT COMMAND PILLS
            item {
                val quickCommands = listOf(
                    "Flashlight ON karo",
                    "Flashlight OFF karo",
                    "YouTube kholo",
                    "WhatsApp open karo",
                    "Wi-Fi settings kholo",
                    "Subah 7 baje ka alarm lagao"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickCommands.forEachIndexed { idx, cmd ->
                        val chipColors = listOf(RoyalPurplePrimary, AccentSkyCyan, AccentRosePink, AccentMintEmerald, AccentChampagneGold)
                        val accent = chipColors[idx % chipColors.size]
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(accent.copy(alpha = 0.18f))
                                .border(1.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                                .clickable { onExecuteQuickPhoneCommand(cmd) }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = cmd,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            // 8. LIVE CONVERSATION TRANSCRIPT & SILENT PROMPT STUDIO
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF191F38).copy(alpha = 0.94f))
                        .border(
                            1.2.dp,
                            Brush.horizontalGradient(
                                listOf(RoyalPurplePrimary, AccentSkyCyan)
                            ),
                            RoundedCornerShape(26.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isTranscriptExpanded = !isTranscriptExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(RoyalPurplePrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                Text(
                                    text = "Live Transcripts (${transcripts.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Icon(
                                imageVector = if (isTranscriptExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = AccentSkyCyan
                            )
                        }

                        AnimatedVisibility(visible = isTranscriptExpanded || transcripts.isNotEmpty()) {
                            if (transcripts.isEmpty()) {
                                Text(
                                    text = "Spoken words and JARVIS replies will stream here in real time.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextLavenderMuted
                                )
                            } else {
                                LazyColumn(
                                    state = transcriptListState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 180.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(transcripts, key = { it.id }) { item ->
                                        val isUser = item.speaker.equals("You", ignoreCase = true)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .widthIn(max = 280.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(
                                                        if (isUser) RoyalPurplePrimary
                                                        else Color(0xFF10162B)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isUser) Color.White.copy(alpha = 0.45f)
                                                        else AccentSkyCyan.copy(alpha = 0.55f),
                                                        RoundedCornerShape(16.dp)
                                                    )
                                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Column {
                                                    Text(
                                                        text = item.speaker,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isUser) Color.White else AccentSkyCyan,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 9.5.sp
                                                    )
                                                    Text(
                                                        text = item.text,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // In-Call Silent Text Capsule
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = livePromptInput,
                                onValueChange = { livePromptInput = it },
                                placeholder = {
                                    Text(
                                        text = "Type a silent prompt into Live Call...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextLavenderMuted
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(999.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF0E1222),
                                    unfocusedContainerColor = Color(0xFF0E1222),
                                    focusedBorderColor = AccentSkyCyan,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
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
                                enabled = livePromptInput.isNotBlank() && hasApiKey,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (livePromptInput.isNotBlank() && hasApiKey) RoyalPurplePrimary
                                        else Color(0xFF232844)
                                    )
                                    .testTag("btn_send_live_text")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Live Prompt",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
