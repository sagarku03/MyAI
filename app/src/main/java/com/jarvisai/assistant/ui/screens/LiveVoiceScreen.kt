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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisElevatedSurface
import com.example.ui.theme.JarvisGlassBorder
import com.example.ui.theme.JarvisNeonCyan
import com.example.ui.theme.JarvisNeonGreen
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet
import com.example.ui.theme.JarvisVioletBright
import com.example.ui.theme.StatusListeningEmerald
import com.example.ui.theme.StatusThinkingAmber
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.LiveVoiceUiStateStatus
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.service.JarvisAccessibilityService
import com.jarvisai.assistant.service.LiveTranscriptItem
import com.jarvisai.assistant.service.LiveVoiceForegroundService
import com.jarvisai.assistant.ui.components.JarvisAIOrb
import com.jarvisai.assistant.ui.components.JarvisAudioWaveform
import com.jarvisai.assistant.ui.components.JarvisGlassPanel
import com.jarvisai.assistant.ui.components.JarvisGradientTitleWithLaserFlare
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
    companionEmotionMood: com.jarvisai.assistant.model.CompanionEmotionMood = com.jarvisai.assistant.model.CompanionEmotionMood.COMPOSED,
    onSelectPersonalityMode: (com.jarvisai.assistant.model.PersonalityMode) -> Unit = {},
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
        // 1. Top Navigation Header
        JarvisPremiumTopBar(
            title = "Live Voice",
            subtitle = "${settings.personalityMode.displayName} • ${settings.voicePersona} (${settings.voiceGender.displayName.substringBefore(" ")})",
            status = status,
            onBackClick = onBack
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 2. IMAGE 8 "FULL CONTROL" GRADIENT HEADLINE + LASER FLARE LINE
            item {
                JarvisGradientTitleWithLaserFlare(
                    leadingWord = "Full",
                    gradientWord = "Live Control",
                    subtitle = "Bidirectional WebSocket Voice & Autonomous Screen Agent",
                    fontSize = 24.sp
                )
            }

            // 3. MONEX-INSPIRED RECESSED SEGMENTED LANGUAGE PILL BAR (Image 5 Analytics tab bar)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF101524))
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
                                    if (selected) Color(0xFF222B45)
                                    else Color.Transparent
                                )
                                .border(
                                    width = if (selected) 1.dp else 0.dp,
                                    color = if (selected) JarvisNeonCyan.copy(alpha = 0.6f) else Color.Transparent,
                                    shape = RoundedCornerShape(999.dp)
                                )
                                .clickable { onSelectLanguage(lang) }
                                .padding(vertical = 8.dp)
                                .testTag("voice_lang_${lang.name.lowercase()}"),
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

            // 3.5. LIVING HUMAN PERSONA, FEELINGS MOOD & PROACTIVE SPEECH BAR
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF111729))
                        .border(1.dp, JarvisVioletBright.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${settings.personalityMode.displayName} • ${companionEmotionMood.emoji} ${companionEmotionMood.displayLabel}",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = buildString {
                                    append("Voice: ${settings.voicePersona}")
                                    if (settings.proactiveCompanionEnabled) append(" • Proactive Screen-Talk ON")
                                    if (settings.ambientRoomTalkEnabled) append(" • 3rd-Person Room Mode ON")
                                },
                                color = JarvisTextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(JarvisViolet.copy(alpha = 0.35f))
                                .border(1.dp, JarvisNeonCyan, RoundedCornerShape(10.dp))
                                .clickable { onTriggerProactiveCompanionNow() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("voice_btn_proactive_speak"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✨ Speak First",
                                color = JarvisNeonCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val quickModes = listOf(
                            com.jarvisai.assistant.model.PersonalityMode.GIRLFRIEND,
                            com.jarvisai.assistant.model.PersonalityMode.FRIENDLY,
                            com.jarvisai.assistant.model.PersonalityMode.SAVAGE_ROAST,
                            com.jarvisai.assistant.model.PersonalityMode.BOYFRIEND_PARTNER,
                            com.jarvisai.assistant.model.PersonalityMode.CARING_FAMILY,
                            com.jarvisai.assistant.model.PersonalityMode.PROFESSIONAL
                        )
                        quickModes.forEach { mode ->
                            val isSelected = settings.personalityMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (isSelected) JarvisViolet.copy(alpha = 0.45f) else Color(0xFF172036))
                                    .border(
                                        1.dp,
                                        if (isSelected) JarvisNeonCyan else Color.White.copy(alpha = 0.14f),
                                        RoundedCornerShape(999.dp)
                                    )
                                    .clickable { onSelectPersonalityMode(mode) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                    .testTag("voice_persona_chip_${mode.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.displayName,
                                    color = if (isSelected) Color.White else JarvisTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 4. MISSING API KEY BANNER (IF APPLICABLE)
            if (!hasApiKey) {
                item {
                    JarvisGlassPanel(
                        accentColor = StatusThinkingAmber,
                        contentPadding = PaddingValues(16.dp),
                        onClick = onNavigateToApiSettings
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
                                    tint = StatusThinkingAmber
                                )
                                Column {
                                    Text(
                                        text = "API Key Required for Live Voice",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = StatusThinkingAmber,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Tap to configure your Gemini API key in Hardware Vault",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JarvisTextSecondary
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = StatusThinkingAmber
                            )
                        }
                    }
                }
            }

            // 5. IMAGE 6 "SYSTEM UPDATE" ROYAL SAPPHIRE GLASS CALL STAGE CARD
            item {
                val energyPercent = if (isSessionActive) {
                    ((0.15f + audioLevel * 0.85f).coerceIn(0.15f, 1f) * 100f)
                } else {
                    15.65f
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            // Radial royal blue backlight halo (Image 6 reference!)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF1D4ED8).copy(alpha = 0.42f),
                                        JarvisNeonCyan.copy(alpha = 0.14f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                                    radius = size.width * 0.72f
                                ),
                                radius = size.width * 0.72f
                            )
                        }
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0A193B).copy(alpha = 0.94f),
                                    Color(0xFF061026).copy(alpha = 0.96f),
                                    Color(0xFF040A1A).copy(alpha = 0.98f)
                                )
                            )
                        )
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    JarvisNeonCyan.copy(alpha = 0.85f),
                                    Color(0xFF3B82F6).copy(alpha = 0.65f),
                                    JarvisNeonCyan.copy(alpha = 0.75f)
                                )
                            ),
                            shape = RoundedCornerShape(32.dp)
                        )
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Top small telemetry line (Image 6 "Secioemnol of oratiers" position)
                        Text(
                            text = connectionLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = JarvisTextSecondary,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )

                        // Central Luminous JarvisAIOrb
                        Box(
                            modifier = Modifier
                                .size(156.dp)
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
                                    .size(148.dp)
                                    .testTag("live_voice_arc_reactor")
                            )
                        }

                        // Bold Centered Headline (Image 6 "System Update" position)
                        Text(
                            text = liveUiStatus.displayLabel,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("live_voice_status_label")
                        )

                        // Subtitle (Image 6 secondary line)
                        Text(
                            text = liveUiStatus.hinglishSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextSecondary,
                            textAlign = TextAlign.Center
                        )

                        // Real-time Audio Waveform
                        JarvisAudioWaveform(
                            audioLevel = audioLevel,
                            isActive = isSessionActive && !isMicMuted,
                            status = status
                        )

                        // Image 6 Signature Thick Pill Gradient Progress / Energy Bar
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xFF040917))
                                    .border(
                                        1.dp,
                                        Color(0xFF253B6E),
                                        RoundedCornerShape(999.dp)
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth((energyPercent / 100f).coerceIn(0.15f, 1f))
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    JarvisNeonCyan,
                                                    Color(0xFF3B82F6),
                                                    Color(0xFF6366F1)
                                                )
                                            )
                                        )
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isSessionActive) "Live Stream" else "Ready State",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = String.format( java.util.Locale.US, "%.2f%%", energyPercent),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Image 6 Pure White Pill CTA Button ("Start Live Voice Call") OR Active Call Controls
                        if (!isSessionActive) {
                            Button(
                                onClick = handleStartCall,
                                enabled = hasApiKey,
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF050814),
                                    disabledContainerColor = Color.White.copy(alpha = 0.25f),
                                    disabledContentColor = JarvisTextMuted
                                ),
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("btn_start_live_voice")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Start Live Voice Call",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        } else {
                            // Active Call Control Row: Mute | Interrupt | End Call
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Mute / Unmute Pill
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            if (isMicMuted) StatusThinkingAmber.copy(alpha = 0.24f)
                                            else Color(0xFF132347)
                                        )
                                        .border(
                                            1.dp,
                                            if (isMicMuted) StatusThinkingAmber else JarvisNeonCyan.copy(alpha = 0.5f),
                                            RoundedCornerShape(999.dp)
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
                                            tint = if (isMicMuted) StatusThinkingAmber else Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (isMicMuted) "Unmute" else "Mute",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Interrupt Pill
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color(0xFF132347))
                                        .border(1.dp, JarvisVioletBright.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
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
                                            tint = JarvisVioletBright,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Text(
                                            text = "Interrupt",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // End Call Crimson Pill
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(AlertCrimson)
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

            // 6. SCREEN SHARE & ACCESSIBILITY AGENT BENTO CARDS (Apple Arcade Library Row Style)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Screen Share Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF13192B))
                            .border(
                                1.dp,
                                if (isScreenSharing) JarvisNeonGreen.copy(alpha = 0.6f)
                                else Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp)
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
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isScreenSharing) JarvisNeonGreen.copy(alpha = 0.18f)
                                        else JarvisNeonCyan.copy(alpha = 0.16f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isScreenSharing) Icons.Default.CancelPresentation else Icons.Default.PresentToAll,
                                    contentDescription = null,
                                    tint = if (isScreenSharing) JarvisNeonGreen else JarvisNeonCyan,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = if (isScreenSharing) "Live Screen Vision Active" else "Live Screen Vision",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isScreenSharing) "Streaming 1 FPS ($screenFramesSent frames sent)"
                                    else "Let JARVIS see your screen in real time",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisTextSecondary
                                )
                            }
                        }

                        if (!isScreenSharing) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xFF232C46))
                                    .border(1.dp, JarvisNeonCyan.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                                    .clickable {
                                        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                                        if (mpm != null) {
                                            screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                                    .testTag("btn_start_screen_share")
                            ) {
                                Text(
                                    text = "Share",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = JarvisNeonCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(AlertCrimson.copy(alpha = 0.2f))
                                    .border(1.dp, AlertCrimson, RoundedCornerShape(999.dp))
                                    .clickable(onClick = onStopScreenShare)
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                                    .testTag("btn_stop_screen_share")
                            ) {
                                Text(
                                    text = "Stop",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AlertCrimson,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Accessibility Agent Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF13192B))
                            .border(
                                1.dp,
                                if (isAccessibilityEnabled) StatusListeningEmerald.copy(alpha = 0.5f)
                                else StatusThinkingAmber.copy(alpha = 0.45f),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp)
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
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isAccessibilityEnabled) StatusListeningEmerald.copy(alpha = 0.16f)
                                        else StatusThinkingAmber.copy(alpha = 0.16f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isAccessibilityEnabled) Icons.Default.CheckCircle else Icons.Default.TouchApp,
                                    contentDescription = null,
                                    tint = if (isAccessibilityEnabled) StatusListeningEmerald else StatusThinkingAmber,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = when {
                                        isAccessibilityEnabled -> "Autonomous Touch Agent Online"
                                        isAccessibilityMalfunctioning -> "Touch Agent Needs Quick Reset"
                                        else -> "Enable Screen Touch Agent"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when {
                                        isAccessibilityEnabled -> "Can click, scroll, type & control apps"
                                        isAccessibilityMalfunctioning -> "Settings mein JARVIS AI ko ek baar OFF karke wapas ON karein"
                                        else -> "Required for hands-free UI clicks & scrolling"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisTextSecondary
                                )
                            }
                        }

                        if (!isAccessibilityEnabled) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(StatusThinkingAmber.copy(alpha = 0.2f))
                                    .border(1.dp, StatusThinkingAmber, RoundedCornerShape(999.dp))
                                    .clickable {
                                        JarvisAccessibilityService.openAccessibilitySettings(context)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                                    .testTag("btn_open_accessibility_settings")
                            ) {
                                Text(
                                    text = if (isAccessibilityMalfunctioning) "Reset" else "Enable",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = StatusThinkingAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Floating Overlay Permission Row (if enabled in settings but permission not granted)
                    if (settings.floatingOverlayEnabled && !hasOverlayPermission) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF13192B))
                                .border(1.dp, JarvisVioletBright.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
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
                                    tint = JarvisVioletBright
                                )
                                Column {
                                    Text(
                                        text = "Floating Call Bubble",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Allow overlay to control call over other apps",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JarvisTextSecondary
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(JarvisViolet.copy(alpha = 0.25f))
                                    .border(1.dp, JarvisVioletBright, RoundedCornerShape(999.dp))
                                    .clickable {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                        context.startActivity(intent)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                                    .testTag("btn_grant_overlay_permission")
                            ) {
                                Text(
                                    text = "Allow",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = JarvisVioletBright,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 7. ERROR / PERMISSION / MEMORY BANNERS
            if (!errorMessage.isNullOrBlank() || permissionDeniedNotice || !memorySavedNotification.isNullOrBlank()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (permissionDeniedNotice) {
                            Text(
                                text = "Microphone permission is required for Live Voice.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AlertCrimson
                            )
                        }
                        if (!errorMessage.isNullOrBlank()) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = AlertCrimson
                            )
                        }
                        if (!memorySavedNotification.isNullOrBlank()) {
                            Text(
                                text = memorySavedNotification,
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisNeonGreen
                            )
                        }
                    }
                }
            }

            // 8. QUICK VOICE / AGENT COMMAND PILLS
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
                    quickCommands.forEach { cmd ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFF141B2D))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                                .clickable { onExecuteQuickPhoneCommand(cmd) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = cmd,
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisNeonCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 9. COLLAPSIBLE LIVE TRANSCRIPT DRAWER + IN-CALL TEXT COMPOSER
            item {
                JarvisGlassPanel(
                    accentColor = JarvisViolet,
                    cornerRadius = 22.dp,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isTranscriptExpanded = !isTranscriptExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Live Conversation Transcript (${transcripts.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (isTranscriptExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = JarvisNeonCyan
                            )
                        }

                        AnimatedVisibility(visible = isTranscriptExpanded || transcripts.isNotEmpty()) {
                            if (transcripts.isEmpty()) {
                                Text(
                                    text = "Spoken words and JARVIS replies will stream here in real time.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextMuted
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
                                                        if (isUser) JarvisViolet.copy(alpha = 0.28f)
                                                        else Color(0xFF162038)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isUser) JarvisVioletBright.copy(alpha = 0.5f)
                                                        else JarvisNeonCyan.copy(alpha = 0.35f),
                                                        RoundedCornerShape(16.dp)
                                                    )
                                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Column {
                                                    Text(
                                                        text = item.speaker,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isUser) JarvisVioletBright else JarvisNeonCyan,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 9.sp
                                                    )
                                                    Text(
                                                        text = item.text,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // In-Call Text Capsule
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
                                        color = JarvisTextMuted
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(999.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF101524),
                                    unfocusedContainerColor = Color(0xFF0D111E),
                                    focusedBorderColor = JarvisNeonCyan,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.14f)
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
                                        if (livePromptInput.isNotBlank() && hasApiKey) JarvisNeonCyan
                                        else JarvisElevatedSurface
                                    )
                                    .testTag("btn_send_live_text")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Live Prompt",
                                    tint = if (livePromptInput.isNotBlank() && hasApiKey) Color(0xFF040914) else JarvisTextMuted,
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
