package com.jarvisai.assistant.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.AccentMintEmerald
import com.example.ui.theme.AccentSkyCyan
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.RoyalPurplePrimary
import com.example.ui.theme.TextLavenderMuted
import com.example.ui.theme.TextPureWhite
import com.jarvisai.assistant.service.PolyglotDiplomatManager
import com.jarvisai.assistant.service.SpeakerRole
import com.jarvisai.assistant.service.TranslationUtterance
import com.jarvisai.assistant.ui.components.JarvisIconControl
import kotlin.math.sin
import kotlinx.coroutines.launch

@Composable
fun PolyglotDiplomatScreen(
    diplomatManager: PolyglotDiplomatManager,
    apiKey: String,
    routerModelId: String,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeColors = JarvisTheme.colors

    val userLanguage by diplomatManager.userLanguage.collectAsStateWithLifecycle()
    val foreignLanguage by diplomatManager.foreignLanguage.collectAsStateWithLifecycle()
    val utterances by diplomatManager.utterances.collectAsStateWithLifecycle()
    val isTranslating by diplomatManager.isTranslating.collectAsStateWithLifecycle()
    val activeListeningRole by diplomatManager.activeListeningRole.collectAsStateWithLifecycle()
    val errorMessage by diplomatManager.errorMessage.collectAsStateWithLifecycle()

    var userDraftText by rememberSaveable { mutableStateOf("") }
    var foreignDraftText by rememberSaveable { mutableStateOf("") }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
            com.jarvisai.assistant.service.LiveVoiceForegroundService.resumeMicrophoneAfterExternalTool()
        }
    }

    fun startSpeechCaptureForRole(role: SpeakerRole) {
        if (!hasAudioPermission) {
            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (activeListeningRole == role) {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            }
            diplomatManager.setActiveListeningRole(null)
            com.jarvisai.assistant.service.LiveVoiceForegroundService.resumeMicrophoneAfterExternalTool()
            return
        }

        val recognizer = speechRecognizer
        if (recognizer == null) {
            Toast.makeText(
                context,
                "Speech recognition unavailable on this device — use the text bar below.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Pause background Live Voice / Wake Word microphone capture before starting SpeechRecognizer
        com.jarvisai.assistant.service.LiveVoiceForegroundService.pauseMicrophoneForExternalTool()

        val langCode = if (role == SpeakerRole.USER_PRIMARY) userLanguage.code else foreignLanguage.code
        diplomatManager.setActiveListeningRole(role)

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                diplomatManager.setActiveListeningRole(null)
                com.jarvisai.assistant.service.LiveVoiceForegroundService.resumeMicrophoneAfterExternalTool()
            }

            override fun onError(error: Int) {
                diplomatManager.setActiveListeningRole(null)
                com.jarvisai.assistant.service.LiveVoiceForegroundService.resumeMicrophoneAfterExternalTool()
            }

            override fun onResults(results: Bundle?) {
                diplomatManager.setActiveListeningRole(null)
                com.jarvisai.assistant.service.LiveVoiceForegroundService.resumeMicrophoneAfterExternalTool()
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull()?.trim().orEmpty()
                if (spoken.isNotBlank()) {
                    scope.launch {
                        diplomatManager.translateAndSpeak(
                            spokenText = spoken,
                            role = role,
                            apiKey = apiKey,
                            routerModelId = routerModelId
                        )
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try {
            recognizer.startListening(intent)
        } catch (_: Exception) {
            diplomatManager.setActiveListeningRole(null)
            com.jarvisai.assistant.service.LiveVoiceForegroundService.resumeMicrophoneAfterExternalTool()
        }
    }

    val latestForForeigner = utterances.lastOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070812))
            .testTag("polyglot_diplomat_screen")
    ) {
        // ====================================================================
        // TOP PANE (INVERTED 180° FOR PERSON SITTING ACROSS THE TABLE)
        // ====================================================================
        Box(
            modifier = Modifier
                .weight(0.44f)
                .fillMaxWidth()
                .graphicsLayer { rotationZ = 180f }
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0E162B),
                            Color(0xFF090D1A)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("polyglot_top_inverted_pane")
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Foreign Language Selector Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    diplomatManager.supportedLanguages.forEach { lang ->
                        val isSelected = foreignLanguage.code == lang.code
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (isSelected) AccentSkyCyan.copy(alpha = 0.25f)
                                    else Color(0xFF151C33)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) AccentSkyCyan else Color.White.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(999.dp)
                                )
                                .clickable { diplomatManager.setForeignLanguage(lang) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                .testTag("polyglot_foreign_lang_${lang.code}")
                        ) {
                            Text(
                                text = "${lang.flag} ${lang.name}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) AccentSkyCyan else TextPureWhite,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }
                }

                // Foreigner's Translated Display Bubble
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF121A30))
                        .border(1.dp, AccentSkyCyan.copy(alpha = 0.38f), RoundedCornerShape(22.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (latestForForeigner != null) {
                        val displayForGuest = if (latestForForeigner.speakerRole == SpeakerRole.USER_PRIMARY) {
                            latestForForeigner.translatedText
                        } else {
                            latestForForeigner.originalText
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${foreignLanguage.flag} ${foreignLanguage.nativeLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentSkyCyan,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = displayForGuest,
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPureWhite,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (latestForForeigner.phoneticGuide.isNotBlank() &&
                                latestForForeigner.speakerRole == SpeakerRole.USER_PRIMARY
                            ) {
                                Text(
                                    text = latestForForeigner.phoneticGuide,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextLavenderMuted
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "${foreignLanguage.flag} Tap Mic or type in ${foreignLanguage.name}",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextLavenderMuted,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Foreign Speaker Controls (Input + Mic + Waveform)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = foreignDraftText,
                        onValueChange = { foreignDraftText = it },
                        placeholder = {
                            Text(
                                text = "Speak or type in ${foreignLanguage.name}...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextLavenderMuted
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPureWhite,
                            unfocusedTextColor = TextPureWhite,
                            focusedContainerColor = Color(0xFF141C34),
                            unfocusedContainerColor = Color(0xFF10172B),
                            focusedBorderColor = AccentSkyCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        ),
                        trailingIcon = {
                            if (foreignDraftText.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        val textToSend = foreignDraftText.trim()
                                        foreignDraftText = ""
                                        scope.launch {
                                            diplomatManager.translateAndSpeak(
                                                spokenText = textToSend,
                                                role = SpeakerRole.FOREIGN_DIPLOMAT,
                                                apiKey = apiKey,
                                                routerModelId = routerModelId
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Translate Foreign Text",
                                        tint = AccentSkyCyan
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("polyglot_foreign_input")
                    )

                    val isForeignListening = activeListeningRole == SpeakerRole.FOREIGN_DIPLOMAT
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                if (isForeignListening) themeColors.statusRoseRed else AccentSkyCyan
                            )
                            .border(2.dp, Color.White, CircleShape)
                            .clickable { startSpeechCaptureForRole(SpeakerRole.FOREIGN_DIPLOMAT) }
                            .testTag("polyglot_foreign_mic_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isForeignListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Foreign Speaker Mic",
                            tint = Color(0xFF050811),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // ====================================================================
        // CENTER NEON DIVIDER BAR (Back Button, Swap Languages, Live Waveform, Clear)
        // ====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            RoyalPurplePrimary.copy(alpha = 0.28f),
                            AccentSkyCyan.copy(alpha = 0.32f),
                            RoyalPurplePrimary.copy(alpha = 0.28f)
                        )
                    )
                )
                .border(1.dp, AccentSkyCyan.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                JarvisIconControl(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                    size = 38.dp,
                    modifier = Modifier.testTag("polyglot_back_btn")
                )
                Column {
                    Text(
                        text = "POLYGLOT DIPLOMAT // 2-WAY HUD",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentSkyCyan,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "${userLanguage.flag} ${userLanguage.name} ⇄ ${foreignLanguage.flag} ${foreignLanguage.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPureWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Live Waveform Indicator
            PolyglotMiniWaveform(
                isActive = isTranslating || activeListeningRole != null,
                modifier = Modifier
                    .width(68.dp)
                    .height(24.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                JarvisIconControl(
                    icon = Icons.Default.SwapVert,
                    contentDescription = "Swap Languages",
                    onClick = { diplomatManager.swapLanguages() },
                    tint = AccentMintEmerald,
                    size = 38.dp,
                    modifier = Modifier.testTag("polyglot_swap_btn")
                )
                JarvisIconControl(
                    icon = Icons.Default.DeleteSweep,
                    contentDescription = "Clear Conversation",
                    onClick = { diplomatManager.clearHistory() },
                    tint = TextLavenderMuted,
                    size = 38.dp,
                    modifier = Modifier.testTag("polyglot_clear_btn")
                )
            }
        }

        // ====================================================================
        // BOTTOM PANE (STANDARD ORIENTATION FOR USER - HINDI / HINGLISH)
        // ====================================================================
        Column(
            modifier = Modifier
                .weight(0.56f)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0A0C18),
                            Color(0xFF121528)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (!errorMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(themeColors.statusRoseRed.copy(alpha = 0.2f))
                        .border(1.dp, themeColors.statusRoseRed, RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPureWhite
                    )
                }
            }

            // Scrollable Dual Transcript History
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (utterances.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF14182B))
                                .border(1.dp, RoyalPurplePrimary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "🌐 Tabletop Split-Screen Mode Active",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPureWhite,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Place the phone on a table between you and a foreign speaker. Speak in Hindi/Hinglish below — JARVIS translates & speaks aloud in ${foreignLanguage.name} while displaying it upside-down on the top half.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextLavenderMuted
                                )
                            }
                        }
                    }
                } else {
                    items(utterances, key = { it.id }) { item ->
                        DiplomatTranscriptBubble(
                            utterance = item,
                            onReplay = { diplomatManager.replaySpeech(it) },
                            onCopy = { text ->
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cb?.setPrimaryClip(ClipData.newPlainText("Polyglot Translation", text))
                                Toast.makeText(context, "Copied translation", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // User Bottom Input & One-Tap Microphone Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = userDraftText,
                    onValueChange = { userDraftText = it },
                    placeholder = {
                        Text(
                            text = "Boliye ya type karein (${userLanguage.name})...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextLavenderMuted
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPureWhite,
                        unfocusedTextColor = TextPureWhite,
                        focusedContainerColor = Color(0xFF15192C),
                        unfocusedContainerColor = Color(0xFF111526),
                        focusedBorderColor = RoyalPurplePrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.22f)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (userDraftText.isNotBlank()) {
                                val textToSend = userDraftText.trim()
                                userDraftText = ""
                                scope.launch {
                                    diplomatManager.translateAndSpeak(
                                        spokenText = textToSend,
                                        role = SpeakerRole.USER_PRIMARY,
                                        apiKey = apiKey,
                                        routerModelId = routerModelId
                                    )
                                }
                            }
                        }
                    ),
                    trailingIcon = {
                        if (userDraftText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val textToSend = userDraftText.trim()
                                    userDraftText = ""
                                    scope.launch {
                                        diplomatManager.translateAndSpeak(
                                            spokenText = textToSend,
                                            role = SpeakerRole.USER_PRIMARY,
                                            apiKey = apiKey,
                                            routerModelId = routerModelId
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("polyglot_user_send_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Translate User Text",
                                    tint = RoyalPurplePrimary
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("polyglot_user_input")
                )

                val isUserListening = activeListeningRole == SpeakerRole.USER_PRIMARY
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                if (isUserListening) {
                                    listOf(themeColors.statusRoseRed, themeColors.statusAmber)
                                } else {
                                    listOf(RoyalPurplePrimary, AccentSkyCyan)
                                }
                            )
                        )
                        .border(2.dp, Color.White, CircleShape)
                        .clickable(enabled = !isTranslating) {
                            startSpeechCaptureForRole(SpeakerRole.USER_PRIMARY)
                        }
                        .testTag("polyglot_user_mic_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isTranslating) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(26.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isUserListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "User Mic",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiplomatTranscriptBubble(
    utterance: TranslationUtterance,
    onReplay: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    val isUser = utterance.speakerRole == SpeakerRole.USER_PRIMARY
    val accent = if (isUser) RoyalPurplePrimary else AccentSkyCyan

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isUser) Color(0xFF191632) else Color(0xFF122036)
            )
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${utterance.sourceLanguage} ➔ ${utterance.targetLanguage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.ExtraBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { onReplay(utterance.translatedText) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Replay Audio",
                            tint = accent,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(
                        onClick = { onCopy(utterance.translatedText) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Translation",
                            tint = TextLavenderMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Text(
                text = utterance.originalText,
                style = MaterialTheme.typography.bodySmall,
                color = TextLavenderMuted
            )
            Text(
                text = utterance.translatedText,
                style = MaterialTheme.typography.titleMedium,
                color = TextPureWhite,
                fontWeight = FontWeight.ExtraBold
            )
            if (utterance.phoneticGuide.isNotBlank()) {
                Text(
                    text = "🔊 ${utterance.phoneticGuide}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentMintEmerald,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PolyglotMiniWaveform(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "polyglotWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Canvas(modifier = modifier) {
        val barCount = 9
        val spacing = size.width / (barCount + 1)
        val centerY = size.height / 2f
        for (i in 0 until barCount) {
            val x = spacing * (i + 1)
            val norm = if (isActive) {
                (0.25f + 0.70f * kotlin.math.abs(sin(phase + i * 0.65f)))
            } else {
                0.20f
            }
            val halfH = (size.height * 0.45f * norm).coerceAtLeast(3f)
            drawLine(
                color = if (isActive) AccentSkyCyan else Color.White.copy(alpha = 0.30f),
                start = Offset(x, centerY - halfH),
                end = Offset(x, centerY + halfH),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
