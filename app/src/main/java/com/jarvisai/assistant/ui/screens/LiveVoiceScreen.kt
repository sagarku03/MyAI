package com.jarvisai.assistant.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CancelPresentation
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.CompanionEmotionMood
import com.jarvisai.assistant.model.GeminiModelOption
import com.jarvisai.assistant.model.LiveVoiceUiStateStatus
import com.jarvisai.assistant.model.PersonalityMode
import com.jarvisai.assistant.model.SupportedGeminiModels
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.service.GeminiLiveVoiceManager
import com.jarvisai.assistant.service.LiveTranscriptItem
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
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
    onTriggerProactiveCompanionNow: () -> Unit = {},
    availableVoiceModels: List<GeminiModelOption> = SupportedGeminiModels.liveVoiceModels,
    onSelectVoiceModel: (String) -> Unit = {},
    onToggleFullDuplexVoice: (Boolean) -> Unit = {}
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

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
    var showPersonalityMenu by remember { mutableStateOf(false) }
    var showVoiceModelMenu by remember { mutableStateOf(false) }
    var showTranscriptSheet by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMicPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    val handleStartCall = {
        val liveMicGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        hasMicPermission = liveMicGranted
        if (!hasApiKey) {
            onNavigateToApiSettings()
        } else if (liveMicGranted) {
            permissionDeniedNotice = false
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

    val submitLiveText = {
        val clean = livePromptInput.trim()
        if (clean.isNotEmpty() && hasApiKey) {
            onSendLiveText(clean)
            livePromptInput = ""
        } else if (!hasApiKey) {
            onNavigateToApiSettings()
        }
    }

    val voiceModelsList = remember(availableVoiceModels) {
        if (availableVoiceModels.isNotEmpty()) availableVoiceModels else SupportedGeminiModels.liveVoiceModels
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF04050D),
                        Color(0xFF090819),
                        Color(0xFF04050D)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
            .testTag("live_voice_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 580.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. TOP BAR: [Personality v] [Voice Model v] [(-)]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Personality Dropdown Pill
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        onClick = { showPersonalityMenu = true },
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF100B22),
                        border = BorderStroke(1.2.dp, Color(0xFF8B5CF6)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("live_voice_personality_dropdown")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Personality",
                                tint = Color(0xFFA855F7),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Personality",
                                color = Color.White,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showPersonalityMenu,
                        onDismissRequest = { showPersonalityMenu = false },
                        modifier = Modifier
                            .widthIn(min = 240.dp, max = 310.dp)
                            .background(Color(0xFF111528))
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "✨ Speak First (${companionEmotionMood.displayLabel})",
                                    color = Color(0xFFF472B6),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            },
                            onClick = {
                                showPersonalityMenu = false
                                onTriggerProactiveCompanionNow()
                            }
                        )
                        HorizontalDivider(color = Color(0xFF1E293B))
                        PersonalityMode.entries.forEach { mode ->
                            val selected = settings.personalityMode == mode
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = mode.displayName,
                                        color = if (selected) Color.White else Color(0xFFCBD5E1),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                trailingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color(0xFFA855F7),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    showPersonalityMenu = false
                                    onSelectPersonalityMode(mode)
                                }
                            )
                        }
                    }
                }

                // Voice Model Dropdown Pill
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        onClick = { showVoiceModelMenu = true },
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF071320),
                        border = BorderStroke(1.2.dp, Color(0xFF06B6D4)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("live_voice_model_dropdown")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Voice Model",
                                tint = Color(0xFF22D3EE),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Voice Model",
                                color = Color.White,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showVoiceModelMenu,
                        onDismissRequest = { showVoiceModelMenu = false },
                        modifier = Modifier
                            .widthIn(min = 250.dp, max = 320.dp)
                            .background(Color(0xFF111528))
                    ) {
                        Text(
                            text = "LIVE VOICE MODEL",
                            color = Color(0xFF22D3EE),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                        voiceModelsList.forEach { model ->
                            val selected = settings.voiceModel.equals(model.id, ignoreCase = true)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = model.displayName,
                                        color = if (selected) Color.White else Color(0xFFCBD5E1),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                trailingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color(0xFF22D3EE),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    showVoiceModelMenu = false
                                    onSelectVoiceModel(model.id)
                                }
                            )
                        }
                        HorizontalDivider(color = Color(0xFF1E293B))
                        Text(
                            text = "LANGUAGE",
                            color = Color(0xFFA855F7),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                        SupportedLanguage.entries.forEach { lang ->
                            val selected = settings.language == lang
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = lang.displayName,
                                        color = if (selected) Color.White else Color(0xFFCBD5E1),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.5.sp
                                    )
                                },
                                trailingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color(0xFFA855F7),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    showVoiceModelMenu = false
                                    onSelectLanguage(lang)
                                }
                            )
                        }
                        HorizontalDivider(color = Color(0xFF1E293B))
                        Text(
                            text = "DUPLEX AUDIO ENGINE",
                            color = Color(0xFF34D399),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = if (settings.fullDuplexVoiceEnabled) {
                                            "Full-Duplex Phone Call (ON)"
                                        } else {
                                            "Full-Duplex Phone Call (OFF)"
                                        },
                                        color = if (settings.fullDuplexVoiceEnabled) Color(0xFF34D399) else Color(0xFFCBD5E1),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp
                                    )
                                    Text(
                                        text = if (settings.fullDuplexVoiceEnabled) {
                                            "Hardware AEC • Instant Voice Barge-In • Affective Tone"
                                        } else {
                                            "Echo-Guard Safe Mode (Wait for AI to finish)"
                                        },
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            trailingIcon = if (settings.fullDuplexVoiceEnabled) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Full-Duplex Active",
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            onClick = {
                                showVoiceModelMenu = false
                                onToggleFullDuplexVoice(!settings.fullDuplexVoiceEnabled)
                            }
                        )
                        HorizontalDivider(color = Color(0xFF1E293B))
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Configure Gemini API Key",
                                    color = Color(0xFFFBBF24),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = {
                                showVoiceModelMenu = false
                                onNavigateToApiSettings()
                            }
                        )
                    }
                }

                // Minimize / Back Circular Button (-)
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = Color(0xFF100B22),
                    border = BorderStroke(1.2.dp, Color(0xFF8B5CF6)),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("live_voice_back_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Minimize Live Voice",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Optional Missing API Key Pill (Only if key is not configured)
            if (!hasApiKey) {
                Surface(
                    onClick = onNavigateToApiSettings,
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF2B1F09),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .defaultMinSize(minHeight = 40.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Gemini API Key Required • Tap to Set Up",
                            color = Color(0xFFFDE68A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 2. CENTER HERO STAGE: Glowing Neon Mic Orb + Headline + Subtitle + Waveform
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                NeonMicVoiceOrb(
                    isSessionActive = isSessionActive,
                    isMicMuted = isMicMuted,
                    status = status,
                    audioLevel = audioLevel,
                    onTapOrb = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (!isSessionActive) {
                            handleStartCall()
                        } else if (status == AssistantStatus.SPEAKING && !isMicMuted) {
                            onInterrupt()
                        } else {
                            onToggleMute()
                        }
                    },
                    onHoldOrb = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!isSessionActive) {
                            handleStartCall()
                        } else if (isMicMuted) {
                            onToggleMute()
                        }
                    },
                    modifier = Modifier
                        .testTag("live_voice_arc_reactor")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = when {
                        !isSessionActive -> "Tap to Speak"
                        isMicMuted -> "Mic Muted"
                        else -> liveUiStatus.displayLabel
                    },
                    color = if (isSessionActive && isMicMuted) Color(0xFFFDE68A) else Color.White,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .testTag("live_voice_status_label")
                        .clickable {
                            if (!isSessionActive) {
                                handleStartCall()
                            } else {
                                onToggleMute()
                            }
                        }
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isSessionActive) {
                        "${settings.personalityMode.displayName} • ${settings.language.displayName} • $connectionLabel"
                    } else if (settings.fullDuplexVoiceEnabled) {
                        "Full-Duplex Phone Call • Speak anytime to interrupt"
                    } else {
                        "Or hold for continuous voice"
                    },
                    color = Color(0xFF9CA3AF),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    onClick = { onToggleFullDuplexVoice(!settings.fullDuplexVoiceEnabled) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (settings.fullDuplexVoiceEnabled) Color(0xFF072320) else Color(0xFF161329),
                    border = BorderStroke(
                        1.dp,
                        if (settings.fullDuplexVoiceEnabled) Color(0xFF10B981).copy(alpha = 0.7f) else Color(0xFF64748B).copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("live_voice_duplex_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (settings.fullDuplexVoiceEnabled) Color(0xFF34D399) else Color(0xFF94A3B8)
                                )
                        )
                        Text(
                            text = if (settings.fullDuplexVoiceEnabled) {
                                "FULL-DUPLEX • AEC • BARGE-IN"
                            } else {
                                "ECHO-GUARD SAFE MODE"
                            },
                            color = if (settings.fullDuplexVoiceEnabled) Color(0xFF6EE7B7) else Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                    }
                }

                if (permissionDeniedNotice) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Microphone permission required — allow in Permissions",
                        color = Color(0xFFF87171),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                } else if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage,
                        color = Color(0xFFF87171),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (!memorySavedNotification.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = memorySavedNotification,
                        color = Color(0xFF34D399),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Centered Purple-Cyan Glowing Waveform
                NeonPurpleCyanWaveform(
                    audioLevel = audioLevel,
                    isActive = isSessionActive && !isMicMuted,
                    status = status,
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .height(66.dp)
                )
            }

            // 3. ACTION BUTTONS ROW: [ Mute ]  [ End ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute / Unmute Neon Purple Pill
                val muteBrush = if (isMicMuted) {
                    Brush.horizontalGradient(listOf(Color(0xFF5A370A), Color(0xFF78450B)))
                } else {
                    Brush.horizontalGradient(listOf(Color(0xFF170E2E), Color(0xFF281652)))
                }
                val muteBorder = if (isMicMuted) Color(0xFFF59E0B) else Color(0xFF8B5CF6)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(muteBrush)
                        .border(1.3.dp, muteBorder, RoundedCornerShape(999.dp))
                        .semantics {
                            role = Role.Button
                            contentDescription = when {
                                !isSessionActive -> "Start Voice Microphone"
                                isMicMuted -> "Unmute Microphone"
                                else -> "Mute Microphone"
                            }
                        }
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (isSessionActive) {
                                onToggleMute()
                            } else {
                                handleStartCall()
                            }
                        }
                        .testTag(if (isSessionActive) "btn_mute_live_voice" else "btn_start_live_voice"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isMicMuted) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = if (isMicMuted) "Unmute Microphone" else "Mute Microphone",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (isMicMuted) "Unmute" else "Mute",
                            color = Color.White,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // End Call Vivid Crimson Pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFFEF4444), Color(0xFFDC2626), Color(0xFFB91C1C))
                            )
                        )
                        .border(1.2.dp, Color(0xFFFCA5A5).copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                        .clickable {
                            if (isSessionActive) {
                                onStopSession()
                            } else {
                                onBack()
                            }
                        }
                        .testTag("btn_end_live_voice"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "End",
                            color = Color.White,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. BOTTOM COMPOSER PILL: [ 💬 ]  Type a message...  [ ➤ ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF0C0F1F))
                    .border(
                        width = 1.2.dp,
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF7C3AED), Color(0xFF6D28D9))
                        ),
                        shape = RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { showTranscriptSheet = true }
                        .testTag("live_voice_transcript_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "Live Transcripts & Screen Share",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(22.dp)
                    )
                    if (transcripts.isNotEmpty() || isScreenSharing) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 10.dp, end = 10.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isScreenSharing) Color(0xFF10B981) else Color(0xFF22D3EE))
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (livePromptInput.isEmpty()) {
                        Text(
                            text = "Type a message...",
                            color = Color(0xFF64748B),
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = livePromptInput,
                        onValueChange = { livePromptInput = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(Color(0xFF22D3EE)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submitLiveText() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("live_text_input")
                    )
                }

                val canSend = livePromptInput.isNotBlank() && hasApiKey
                IconButton(
                    onClick = { submitLiveText() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .testTag("btn_send_live_text")
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) {
                                    Brush.linearGradient(
                                        listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
                                    )
                                } else {
                                    Brush.linearGradient(
                                        listOf(Color(0xFF5B21B6), Color(0xFF4C1D95))
                                    )
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }

    // Full-Screen Customized Live Conversation & Screen Share Studio (behind [💬])
    if (showTranscriptSheet) {
        val visibleTranscripts = remember(transcripts) {
            transcripts.filterNot { item ->
                item.speaker.equals("SYSTEM", ignoreCase = true) && (
                    item.text.startsWith("[SESSION_CONNECTED]") ||
                        item.text.startsWith("Microphone ") ||
                        item.text.startsWith("Live Voice call ") ||
                        item.text.startsWith("👁️") ||
                        item.text.startsWith("Audio mode switched") ||
                        item.text.startsWith("Persona switched")
                    )
            }
        }
        val transcriptListState = rememberLazyListState()
        LaunchedEffect(visibleTranscripts.size, visibleTranscripts.lastOrNull()?.text?.length) {
            if (visibleTranscripts.isNotEmpty()) {
                runCatching { transcriptListState.animateScrollToItem(visibleTranscripts.size - 1) }
            }
        }

        Dialog(
            onDismissRequest = { showTranscriptSheet = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF04050D),
                                Color(0xFF0A091D),
                                Color(0xFF04050D)
                            )
                        )
                    )
                    .systemBarsPadding()
                    .imePadding()
                    .testTag("live_transcript_fullscreen_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp)
                        .align(Alignment.TopCenter)
                ) {
                    // 1. Sleek Full-Screen Top Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                onClick = { showTranscriptSheet = false },
                                shape = CircleShape,
                                color = Color(0xFF100E24),
                                border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("btn_close_transcript_fullscreen")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Close Live Conversation",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Live Conversation",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    !isSessionActive -> Color(0xFF64748B)
                                                    isMicMuted -> Color(0xFFF59E0B)
                                                    status == AssistantStatus.SPEAKING -> Color(0xFF22D3EE)
                                                    else -> Color(0xFF10B981)
                                                }
                                            )
                                    )
                                    Text(
                                        text = buildString {
                                            append(settings.personalityMode.displayName)
                                            append(" • ")
                                            append(
                                                when {
                                                    !isSessionActive -> "Standby"
                                                    isMicMuted -> "Mic Muted"
                                                    else -> liveUiStatus.displayLabel
                                                }
                                            )
                                        },
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = {
                                    if (isScreenSharing) {
                                        onStopScreenShare()
                                    } else {
                                        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                                        if (mpm != null) {
                                            screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(999.dp),
                                color = if (isScreenSharing) Color(0xFF7F1D1D) else Color(0xFF0A1D2E),
                                border = BorderStroke(
                                    1.1.dp,
                                    if (isScreenSharing) Color(0xFFF87171) else Color(0xFF22D3EE)
                                ),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("btn_toggle_screen_share")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isScreenSharing) Icons.Default.CancelPresentation else Icons.Default.PresentToAll,
                                        contentDescription = if (isScreenSharing) "Stop Screen Share" else "Share Screen",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isScreenSharing) "Stop ($screenFramesSent)" else "Share",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    val logDump = buildString {
                                        appendLine("=== AGENTRA LIVE RUNTIME LOGS ===")
                                        appendLine("SessionActive=$isSessionActive, Muted=$isMicMuted, Status=${liveUiStatus.name}, ScreenShare=$isScreenSharing, FramesSent=$screenFramesSent")
                                        appendLine("LiveScreenSummary=${GeminiLiveVoiceManager.latestLiveScreenContextSummary}")
                                        if (!errorMessage.isNullOrBlank()) appendLine("Error=$errorMessage")
                                        transcripts.forEach { item ->
                                            appendLine("[${item.speaker}] ${item.text}")
                                        }
                                    }
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("Agentra Logs", logDump))
                                    Toast.makeText(context, "Conversation logs copied!", Toast.LENGTH_SHORT).show()
                                },
                                shape = CircleShape,
                                color = Color(0xFF141B30),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("btn_copy_live_logs")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Conversation Logs",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E293B).copy(alpha = 0.7f))

                    // 2. Full-Height Live Conversation Feed (Clean, zero quick-command clutter)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (visibleTranscripts.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    Color(0xFF7C3AED).copy(alpha = 0.25f),
                                                    Color(0xFF06B6D4).copy(alpha = 0.22f)
                                                )
                                            )
                                        )
                                        .border(1.2.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = Color(0xFF22D3EE),
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                                Text(
                                    text = "Ready for Live Voice",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Aap jo bolenge aur Agentra ka jawab real-time mein yahan cleanly dikhega.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                state = transcriptListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(visibleTranscripts, key = { it.id }) { item ->
                                    val isUser = item.speaker.equals("USER", ignoreCase = true) ||
                                        item.speaker.equals("You", ignoreCase = true)
                                    val isSystem = item.speaker.equals("SYSTEM", ignoreCase = true)

                                    if (isSystem) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = Color(0xFF10182B),
                                                border = BorderStroke(1.dp, Color(0xFF22D3EE).copy(alpha = 0.25f))
                                            ) {
                                                Text(
                                                    text = item.text,
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                        ) {
                                            val bubbleShape = if (isUser) {
                                                RoundedCornerShape(
                                                    topStart = 20.dp,
                                                    topEnd = 20.dp,
                                                    bottomStart = 20.dp,
                                                    bottomEnd = 6.dp
                                                )
                                            } else {
                                                RoundedCornerShape(
                                                    topStart = 20.dp,
                                                    topEnd = 20.dp,
                                                    bottomStart = 6.dp,
                                                    bottomEnd = 20.dp
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .widthIn(max = 320.dp)
                                                    .clip(bubbleShape)
                                                    .background(
                                                        if (isUser) {
                                                            Brush.linearGradient(
                                                                listOf(Color(0xFF7C3AED), Color(0xFF5B21B6))
                                                            )
                                                        } else {
                                                            Brush.linearGradient(
                                                                listOf(Color(0xFF11182C), Color(0xFF0D1426))
                                                            )
                                                        }
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isUser) Color.White.copy(alpha = 0.22f)
                                                        else Color(0xFF22D3EE).copy(alpha = 0.35f),
                                                        bubbleShape
                                                    )
                                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                    ) {
                                                        if (!isUser) {
                                                            Icon(
                                                                imageVector = Icons.Default.AutoAwesome,
                                                                contentDescription = null,
                                                                tint = Color(0xFF22D3EE),
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                        }
                                                        Text(
                                                            text = if (isUser) "YOU" else "AGENTRA",
                                                            color = if (isUser) Color(0xFFE9D5FF) else Color(0xFF22D3EE),
                                                            fontSize = 10.5.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            letterSpacing = 0.8.sp
                                                        )
                                                    }
                                                    Text(
                                                        text = item.text,
                                                        color = Color.White,
                                                        fontSize = 14.5.sp,
                                                        lineHeight = 20.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Bottom Composer & Quick Mic Pill inside Full-Screen Transcript View
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(58.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF0C1022))
                            .border(
                                width = 1.2.dp,
                                brush = Brush.horizontalGradient(
                                    listOf(Color(0xFF7C3AED), Color(0xFF06B6D4))
                                ),
                                shape = RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (isSessionActive) onToggleMute() else handleStartCall()
                            },
                            shape = CircleShape,
                            color = when {
                                !isSessionActive -> Color(0xFF1D1B3A)
                                isMicMuted -> Color(0xFF5A370A)
                                else -> Color(0xFF0E2A38)
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    !isSessionActive -> Color(0xFF8B5CF6)
                                    isMicMuted -> Color(0xFFF59E0B)
                                    else -> Color(0xFF22D3EE)
                                }
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_transcript_mic_toggle")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isSessionActive && isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = if (isSessionActive && isMicMuted) "Unmute Microphone" else "Toggle Microphone",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (livePromptInput.isEmpty()) {
                                Text(
                                    text = "Type a message...",
                                    color = Color(0xFF64748B),
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = livePromptInput,
                                onValueChange = { livePromptInput = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 15.sp
                                ),
                                cursorBrush = SolidColor(Color(0xFF22D3EE)),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { submitLiveText() }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        val canSendInTranscript = livePromptInput.isNotBlank() && hasApiKey
                        IconButton(
                            onClick = { submitLiveText() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (canSendInTranscript) {
                                            Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)))
                                        } else {
                                            Brush.linearGradient(listOf(Color(0xFF4C1D95), Color(0xFF3B0764)))
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Message",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NeonMicVoiceOrb(
    isSessionActive: Boolean,
    isMicMuted: Boolean,
    status: AssistantStatus,
    audioLevel: Float,
    onTapOrb: () -> Unit,
    onHoldOrb: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnTapOrb by rememberUpdatedState(onTapOrb)
    val currentOnHoldOrb by rememberUpdatedState(onHoldOrb)
    val transition = rememberInfiniteTransition(label = "neon_orb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )
    val levelBoost by animateFloatAsState(
        targetValue = if (isSessionActive && !isMicMuted) audioLevel.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 90),
        label = "orb_audio"
    )

    val ringBrush = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFC026D3).copy(alpha = 0.72f),
                Color(0xFF8B5CF6).copy(alpha = 0.65f),
                Color(0xFF06B6D4).copy(alpha = 0.72f),
                Color(0xFF38BDF8).copy(alpha = 0.85f)
            )
        )
    }
    val rimHighlightBrush = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFF5D0FE).copy(alpha = 0.92f),
                Color.White.copy(alpha = 0.85f),
                Color(0xFF67E8F9).copy(alpha = 0.95f)
            )
        )
    }
    val ringSpecs = remember { listOf(0.86f to 1.2f, 0.77f to 1.8f, 0.68f to 2.4f) }
    val coreColors = remember {
        listOf(
            Color(0xFFD946EF),
            Color(0xFF7C3AED),
            Color(0xFF2563EB),
            Color(0xFF06B6D4)
        )
    }

    Box(
        modifier = modifier
            .size(230.dp)
            .semantics {
                role = Role.Button
                contentDescription = when {
                    !isSessionActive -> "Tap to Speak"
                    isMicMuted -> "Mic Muted, Tap to Unmute"
                    else -> "Active Voice Orb, Tap to Mute or Interrupt"
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnTapOrb() },
                    onLongPress = { currentOnHoldOrb() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = size.minDimension / 2f
            val coreR = maxR * (0.52f + pulse * 0.02f + levelBoost * 0.05f)

            // Ambient purple + cyan nebula behind orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF9333EA).copy(alpha = if (isSessionActive) 0.44f else 0.30f),
                        Color(0xFF2563EB).copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = Offset(cx - maxR * 0.14f, cy),
                    radius = maxR * 0.98f
                ),
                radius = maxR,
                center = Offset(cx, cy)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF06B6D4).copy(alpha = if (isSessionActive) 0.38f else 0.24f),
                        Color.Transparent
                    ),
                    center = Offset(cx + maxR * 0.18f, cy),
                    radius = maxR * 0.90f
                ),
                radius = maxR,
                center = Offset(cx, cy)
            )

            // Outer Concentric Tech Rings
            ringSpecs.forEachIndexed { idx, (ratio, strokeDp) ->
                val ringR = maxR * (ratio + pulse * 0.014f * (idx + 1))
                drawCircle(
                    brush = ringBrush,
                    radius = ringR,
                    center = Offset(cx, cy),
                    style = Stroke(width = strokeDp.dp.toPx()),
                    alpha = (0.30f + idx * 0.18f + levelBoost * 0.25f).coerceAtMost(0.95f)
                )
            }

            // Radiant Gradient Inner Core Sphere
            drawCircle(
                brush = Brush.linearGradient(
                    colors = coreColors,
                    start = Offset(cx - coreR, cy - coreR * 0.4f),
                    end = Offset(cx + coreR, cy + coreR * 0.5f)
                ),
                radius = coreR,
                center = Offset(cx, cy)
            )

            // Glossy inner rim highlight
            drawCircle(
                brush = rimHighlightBrush,
                radius = coreR,
                center = Offset(cx, cy),
                style = Stroke(width = 3.2.dp.toPx())
            )
        }

        val micTint = when {
            isMicMuted -> Color(0xFFFDE68A)
            status == AssistantStatus.SPEAKING -> Color(0xFFE0F2FE)
            else -> Color.White
        }
        Icon(
            imageVector = if (isMicMuted && isSessionActive) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = if (isSessionActive) "Voice Active" else "Tap to Speak",
            tint = micTint,
            modifier = Modifier.size(56.dp)
        )
    }
}

@Composable
private fun NeonPurpleCyanWaveform(
    audioLevel: Float,
    isActive: Boolean,
    status: AssistantStatus,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "neon_wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )
    val smoothLevel by animateFloatAsState(
        targetValue = if (isActive) audioLevel.coerceIn(0.08f, 1f) else 0f,
        animationSpec = tween(durationMillis = 85),
        label = "wave_level"
    )

    // Symmetrical bar profile matching mockup (dots at edges, peaking cleanly in middle)
    val barWeights = remember {
        listOf(
            0.05f, 0.05f, 0.06f, 0.14f, 0.09f, 0.24f, 0.16f, 0.34f,
            0.22f, 0.46f, 0.32f, 0.62f, 0.42f, 0.88f, 0.64f, 0.98f,
            0.70f, 0.92f, 0.54f, 0.82f, 0.48f, 0.64f, 0.38f, 0.48f,
            0.26f, 0.34f, 0.18f, 0.22f, 0.12f, 0.07f, 0.05f, 0.05f
        )
    }
    val barColors = remember(barWeights) {
        val count = barWeights.size
        List(count) { idx ->
            val progress = idx.toFloat() / (count - 1).coerceAtLeast(1)
            val base = lerpNeonColor(
                start = Color(0xFF9333EA),
                mid = Color(0xFF7C3AED),
                end = Color(0xFF22D3EE),
                fraction = progress
            )
            base.copy(alpha = if (barWeights[idx] < 0.08f) 0.55f else 0.95f)
        }
    }

    Canvas(modifier = modifier) {
        val barCount = barWeights.size
        val slotWidth = size.width / barCount
        val barWidth = (slotWidth * 0.46f).coerceIn(3.dp.toPx(), 6.dp.toPx())
        val centerY = size.height / 2f
        val maxHalfHeight = size.height * 0.46f

        // Soft purple-cyan center glow behind waveform
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF8B5CF6).copy(alpha = if (isActive) 0.28f else 0.16f),
                    Color(0xFF06B6D4).copy(alpha = if (isActive) 0.18f else 0.10f),
                    Color.Transparent
                ),
                center = Offset(size.width / 2f, centerY),
                radius = size.width * 0.38f
            ),
            radius = size.width * 0.38f,
            center = Offset(size.width / 2f, centerY)
        )

        val cornerR = CornerRadius(barWidth / 2f, barWidth / 2f)
        val statusScale = if (status == AssistantStatus.SPEAKING) 1.0f else 0.88f
        for (idx in 0 until barCount) {
            val baseWeight = barWeights[idx]
            val waveMotion = if (isActive) {
                val ripple = 0.5f + 0.5f * sin(phase * 1.8f + idx * 0.55f)
                (baseWeight * (0.35f + smoothLevel * 0.65f * ripple) * statusScale).coerceIn(0.06f, 1f)
            } else {
                val gentleBreath = 0.86f + 0.14f * sin(phase + idx * 0.45f)
                (baseWeight * gentleBreath).coerceIn(0.05f, 0.96f)
            }

            val barHeight = (maxHalfHeight * 2f * waveMotion).coerceAtLeast(barWidth)
            val left = idx * slotWidth + (slotWidth - barWidth) / 2f
            val top = centerY - barHeight / 2f

            drawRoundRect(
                color = barColors[idx],
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerR,
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
        }
    }
}

private fun lerpNeonColor(start: Color, mid: Color, end: Color, fraction: Float): Color {
    return if (fraction < 0.45f) {
        val t = (fraction / 0.45f).coerceIn(0f, 1f)
        Color(
            red = start.red + (mid.red - start.red) * t,
            green = start.green + (mid.green - start.green) * t,
            blue = start.blue + (mid.blue - start.blue) * t,
            alpha = 1f
        )
    } else {
        val t = ((fraction - 0.45f) / 0.55f).coerceIn(0f, 1f)
        Color(
            red = mid.red + (end.red - mid.red) * t,
            green = mid.green + (end.green - mid.green) * t,
            blue = mid.blue + (end.blue - mid.blue) * t,
            alpha = 1f
        )
    }
}
