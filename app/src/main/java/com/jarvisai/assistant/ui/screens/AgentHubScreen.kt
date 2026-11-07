package com.jarvisai.assistant.ui.screens

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentChampagneGold
import com.example.ui.theme.AccentMintEmerald
import com.example.ui.theme.AccentRosePink
import com.example.ui.theme.AccentSkyCyan
import com.example.ui.theme.RoyalIndigoAccent
import com.example.ui.theme.RoyalPurplePrimary
import com.example.ui.theme.TextLavenderMuted
import com.jarvisai.assistant.driving.JarvisDrivingModeManager
import com.jarvisai.assistant.service.ChronosFocusGuardian
import com.jarvisai.assistant.service.ClawbotTelegramGateway
import com.jarvisai.assistant.service.CyberSecuritySentry
import com.jarvisai.assistant.service.GhostPilotGestureEngine
import com.jarvisai.assistant.service.JarvisTtsManager
import com.jarvisai.assistant.service.McpClientBridge
import com.jarvisai.assistant.service.NeuralImageStudioManager
import com.jarvisai.assistant.service.PocketStrikeOfflineEngine
import com.jarvisai.assistant.service.PrometheusToolSynthesizer
import com.jarvisai.assistant.service.SwarmParallelCoordinator
import com.jarvisai.assistant.termux.JarvisTermuxBridge
import com.jarvisai.assistant.ui.components.JarvisGlassPanel
import com.jarvisai.assistant.voicenote.JarvisVoiceNoteManager
import com.jarvisai.assistant.wallpaper.JarvisWallpaperService
import com.jarvisai.assistant.watchman.JarvisWatchmanManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AgentHubTab(val label: String) {
    TERMINAL("Termux Console"),
    SECURITY("Watchman & Cyber"),
    DRIVING("Driving Mode"),
    MACROS("Prometheus & Swarm"),
    VOICE_NOTES("Voice Notes"),
    WALLPAPER("Neural Art & Brain")
}

@Composable
fun AgentHubScreen(
    onBack: () -> Unit,
    onRunMacro: (name: String, query: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(AgentHubTab.TERMINAL) }

    var termuxCommand by remember { mutableStateOf("uname -a") }
    var termuxLogs by remember { mutableStateOf(listOf("JARVIS Cyber Terminal Initialized.", "Bridge connected to Termux service.")) }
    var isTermuxExecuting by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("jarvis_watchman_prefs", Context.MODE_PRIVATE) }
    var isWatchmanArmed by remember { mutableStateOf(prefs.getBoolean("watchman_armed", true)) }
    var telegramBotToken by remember { mutableStateOf(prefs.getString("tg_bot_token", "") ?: "") }
    var telegramChatId by remember { mutableStateOf(prefs.getString("tg_chat_id", "") ?: "") }
    var watchmanStatusMessage by remember { mutableStateOf<String?>(null) }
    var isCapturingWatchman by remember { mutableStateOf(false) }

    var isDrivingModeActive by remember { mutableStateOf(JarvisDrivingModeManager.isDrivingModeActive) }
    var autoReadMessages by remember { mutableStateOf(true) }

    var simulatedVoiceNotes by remember {
        mutableStateOf(
            listOf(
                "WhatsApp: Rahul Verma -> 'Bhai meeting 5 baje shift ho gayi hai, slides ready kar lo' [Detected: Hindi -> Transcribed]",
                "WhatsApp: Elena Costa -> 'Hola amigo, podemos revisar el informe manana?' [Detected: Spanish -> Translation: Hi friend, can we review report tomorrow?]"
            )
        )
    }
    var voiceNoteReplyText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. APPLE iOS 26 FLOATING CAPSULE HEADER
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF231D42).copy(alpha = 0.92f),
                            Color(0xFF162038).copy(alpha = 0.94f)
                        )
                    )
                )
                .border(
                    1.2.dp,
                    Brush.horizontalGradient(
                        listOf(RoyalPurplePrimary, AccentSkyCyan, AccentChampagneGold)
                    ),
                    RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(RoyalPurplePrimary, RoyalIndigoAccent)
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(13.dp))
                            .clickable(onClick = onBack)
                            .testTag("agent_hub_back_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(AccentChampagneGold, AccentRosePink)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Agent Studio Hub",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "PrivateAgent 3D Bust • Air Gestures • ADS-B • SOUL.md",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentSkyCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(AccentMintEmerald.copy(alpha = 0.22f))
                        .border(1.dp, AccentMintEmerald, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "ONLINE",
                        color = AccentMintEmerald,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // 2. MULTI-COLOR BENTO PILL TABS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AgentHubTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                val tabColor = when (tab) {
                    AgentHubTab.TERMINAL -> AccentSkyCyan
                    AgentHubTab.SECURITY -> AccentRosePink
                    AgentHubTab.DRIVING -> AccentMintEmerald
                    AgentHubTab.MACROS -> AccentChampagneGold
                    AgentHubTab.VOICE_NOTES -> RoyalPurplePrimary
                    AgentHubTab.WALLPAPER -> RoyalIndigoAccent
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) {
                                Brush.horizontalGradient(listOf(tabColor, RoyalPurplePrimary))
                            } else {
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF191F36), Color(0xFF191F36))
                                )
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color.White.copy(alpha = 0.55f) else tabColor.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                        .testTag("tab_${tab.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) Color.White else TextLavenderMuted,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // 3. TAB CONTENT
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            when (selectedTab) {
                AgentHubTab.TERMINAL -> {
                    TermuxConsoleTab(
                        termuxCommand = termuxCommand,
                        onCommandChange = { termuxCommand = it },
                        logs = termuxLogs,
                        isExecuting = isTermuxExecuting,
                        onClearLogs = { termuxLogs = emptyList() },
                        onExecuteCommand = { cmd ->
                            isTermuxExecuting = true
                            termuxLogs = termuxLogs + "> $cmd"
                            coroutineScope.launch {
                                val bridge = JarvisTermuxBridge(context)
                                val result = withContext(Dispatchers.IO) {
                                    bridge.executeCommand(cmd, background = true, timeoutSeconds = 15)
                                }
                                val out = if (result.success) {
                                    result.stdout.ifBlank { "[Executed with Exit Code 0]" }
                                } else {
                                    "Error (${result.exitCode}): ${result.stderr.ifBlank { result.errorMessage }}"
                                }
                                termuxLogs = termuxLogs + out
                                isTermuxExecuting = false
                            }
                        }
                    )
                }

                AgentHubTab.SECURITY -> {
                    WatchmanSecurityTab(
                        isArmed = isWatchmanArmed,
                        onToggleArmed = {
                            isWatchmanArmed = it
                            prefs.edit().putBoolean("watchman_armed", it).apply()
                        },
                        telegramBotToken = telegramBotToken,
                        onTokenChange = { telegramBotToken = it },
                        telegramChatId = telegramChatId,
                        onChatIdChange = { telegramChatId = it },
                        onSaveCredentials = {
                            prefs.edit()
                                .putString("tg_bot_token", telegramBotToken)
                                .putString("tg_chat_id", telegramChatId)
                                .apply()
                            watchmanStatusMessage = "Telegram security credentials saved."
                        },
                        statusMessage = watchmanStatusMessage,
                        isCapturing = isCapturingWatchman,
                        onCaptureTestSnapshot = {
                            isCapturingWatchman = true
                            coroutineScope.launch {
                                val watchman = JarvisWatchmanManager(context)
                                val result = watchman.captureIntruderSnapshot(
                                    telegramBotToken = telegramBotToken.ifBlank { null },
                                    telegramChatId = telegramChatId.ifBlank { null },
                                    caption = "JARVIS Manual Security Probe: Front camera intruder test"
                                )
                                watchmanStatusMessage = result.message
                                isCapturingWatchman = false
                            }
                        }
                    )
                }

                AgentHubTab.DRIVING -> {
                    DrivingCockpitTab(
                        isDrivingActive = isDrivingModeActive,
                        autoReadMessages = autoReadMessages,
                        onToggleDriving = { active ->
                            isDrivingModeActive = active
                            if (active) {
                                JarvisDrivingModeManager.activateDrivingMode(context)
                            } else {
                                JarvisDrivingModeManager.deactivateDrivingMode(context)
                            }
                        },
                        onToggleAutoRead = { autoReadMessages = it }
                    )
                }

                AgentHubTab.MACROS -> {
                    BuiltinMacrosTab(
                        onRunMacro = onRunMacro
                    )
                }

                AgentHubTab.VOICE_NOTES -> {
                    VoiceNoteCenterTab(
                        voiceNotes = simulatedVoiceNotes,
                        replyText = voiceNoteReplyText,
                        onReplyTextChange = { voiceNoteReplyText = it },
                        onSendReply = {
                            if (voiceNoteReplyText.isNotBlank()) {
                                coroutineScope.launch {
                                    val success = JarvisVoiceNoteManager.replyToVoiceNote(
                                        context = context,
                                        chatName = "WhatsApp",
                                        replyText = voiceNoteReplyText
                                    )
                                    if (success) {
                                        simulatedVoiceNotes = simulatedVoiceNotes + "You -> WhatsApp: $voiceNoteReplyText"
                                        voiceNoteReplyText = ""
                                    }
                                }
                            }
                        },
                        onSimulateIncoming = {
                            val newNote = "WhatsApp: Priya Sharma -> 'Kal subah 9 baje office pohanch jaana' [Detected: Hindi -> English: Be at office by 9 AM tomorrow]"
                            simulatedVoiceNotes = listOf(newNote) + simulatedVoiceNotes
                            JarvisVoiceNoteManager.recordVoiceNoteDetected(
                                senderName = "Priya Sharma",
                                chatName = "WhatsApp",
                                detectedLanguage = "Hindi",
                                meaning = "Kal subah 9 baje office pohanch jaana"
                            )
                            JarvisTtsManager.speak(context, "New voice note from Priya Sharma: Kal subah 9 baje office pohanch jaana")
                        }
                    )
                }

                AgentHubTab.WALLPAPER -> {
                    WallpaperAndSystemTab()
                }
            }
        }
    }
}

@Composable
private fun TermuxConsoleTab(
    termuxCommand: String,
    onCommandChange: (String) -> Unit,
    logs: List<String>,
    isExecuting: Boolean,
    onClearLogs: () -> Unit,
    onExecuteCommand: (String) -> Unit
) {
    val quickChips = listOf("uname -a", "df -h", "ip a", "uptime", "whoami", "ls -la")

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickChips.forEach { chip ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(AccentSkyCyan.copy(alpha = 0.16f))
                        .border(1.dp, AccentSkyCyan.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                        .clickable {
                            onCommandChange(chip)
                            onExecuteCommand(chip)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = chip,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = termuxCommand,
                onValueChange = onCommandChange,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("termux_command_input"),
                placeholder = { Text("Enter Termux bash command...", fontSize = 11.sp, color = TextLavenderMuted) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onExecuteCommand(termuxCommand) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentSkyCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedContainerColor = Color(0xFF101528),
                    unfocusedContainerColor = Color(0xFF101528)
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(listOf(RoyalPurplePrimary, AccentSkyCyan))
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .clickable(enabled = !isExecuting) { onExecuteCommand(termuxCommand) }
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .testTag("termux_execute_btn"),
                contentAlignment = Alignment.Center
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "EXEC",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }

        JarvisGlassPanel(
            accentColor = AccentSkyCyan,
            cornerRadius = 24.dp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(AccentMintEmerald))
                        Text(
                            text = "TERMUX BASH CONSOLE",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AccentSkyCyan,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Text(
                        text = "CLEAR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentRosePink,
                        modifier = Modifier.clickable { onClearLogs() }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (log.startsWith(">")) AccentSkyCyan else Color.White,
                            fontWeight = if (log.startsWith(">")) FontWeight.ExtraBold else FontWeight.Medium,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchmanSecurityTab(
    isArmed: Boolean,
    onToggleArmed: (Boolean) -> Unit,
    telegramBotToken: String,
    onTokenChange: (String) -> Unit,
    telegramChatId: String,
    onChatIdChange: (String) -> Unit,
    onSaveCredentials: () -> Unit,
    statusMessage: String?,
    isCapturing: Boolean,
    onCaptureTestSnapshot: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isClawbotRunning by ClawbotTelegramGateway.isDaemonRunning.collectAsState()
    val clawbotLogs by ClawbotTelegramGateway.commandLogs.collectAsState()
    val cyberReport by CyberSecuritySentry.latestAuditReport.collectAsState()
    val chronosReport by ChronosFocusGuardian.lastReport.collectAsState()
    var clawbotPollSummary by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            JarvisGlassPanel(
                accentColor = if (isArmed) AccentRosePink else AccentSkyCyan,
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isArmed) AccentRosePink else AccentSkyCyan
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isArmed) Icons.Default.Lock else Icons.Default.Security,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }

                        Column {
                            Text(
                                text = if (isArmed) "WATCHMAN ARMED" else "WATCHMAN STANDBY",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = if (isArmed) "Silent photo capture on wrong PIN" else "Protection is currently paused",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextLavenderMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Switch(
                        checked = isArmed,
                        onCheckedChange = onToggleArmed,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentRosePink
                        ),
                        modifier = Modifier.testTag("watchman_arm_switch")
                    )
                }
            }
        }

        item {
            JarvisGlassPanel(
                accentColor = RoyalPurplePrimary,
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Telegram Intrusion Alert & Clawbot 24/7 Gateway",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Dispatches intruder snapshots AND listens for inbound Telegram commands (/status, /photo, /otp, /locate, /ring, /speak, /termux).",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLavenderMuted
                    )

                    OutlinedTextField(
                        value = telegramBotToken,
                        onValueChange = onTokenChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Telegram Bot Token") },
                        placeholder = { Text("e.g. 123456789:AAHfkjdsf...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RoyalPurplePrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color(0xFF0E1324),
                            unfocusedContainerColor = Color(0xFF0E1324),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )

                    OutlinedTextField(
                        value = telegramChatId,
                        onValueChange = onChatIdChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Telegram Chat ID") },
                        placeholder = { Text("e.g. 987654321") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RoyalPurplePrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color(0xFF0E1324),
                            unfocusedContainerColor = Color(0xFF0E1324),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(RoyalPurplePrimary)
                                .clickable { onSaveCredentials() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Save Credentials", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.horizontalGradient(listOf(AccentRosePink, AccentChampagneGold))
                                )
                                .clickable(enabled = !isCapturing) { onCaptureTestSnapshot() }
                                .padding(vertical = 12.dp)
                                .testTag("watchman_test_snapshot_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCapturing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Trigger Test Photo", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isClawbotRunning) "CLAWBOT 24/7 GATEWAY: ACTIVE" else "CLAWBOT 24/7 REMOTE GATEWAY",
                                color = if (isClawbotRunning) AccentMintEmerald else Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.5.sp
                            )
                            Text(
                                text = "Last Cmd: ${clawbotLogs.firstOrNull()?.command ?: "NONE"}",
                                color = TextLavenderMuted,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isClawbotRunning,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    ClawbotTelegramGateway.startGatewayDaemon(context, telegramBotToken, telegramChatId)
                                } else {
                                    ClawbotTelegramGateway.stopGatewayDaemon()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentMintEmerald
                            ),
                            modifier = Modifier.testTag("clawbot_gateway_switch")
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AccentSkyCyan.copy(alpha = 0.22f))
                            .border(1.dp, AccentSkyCyan, RoundedCornerShape(14.dp))
                            .clickable {
                                scope.launch {
                                    clawbotPollSummary = ClawbotTelegramGateway.executeInboundRemoteCommand(
                                        context = context,
                                        rawCommand = "/status",
                                        botToken = telegramBotToken.ifBlank { null },
                                        chatId = telegramChatId.ifBlank { null }
                                    )
                                }
                            }
                            .padding(vertical = 10.dp)
                            .testTag("clawbot_poll_now_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Test Clawbot Remote /status Command Now", color = AccentSkyCyan, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                    }

                    if (!clawbotPollSummary.isNullOrBlank()) {
                        Text(text = clawbotPollSummary!!, color = AccentMintEmerald, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    if (!statusMessage.isNullOrBlank()) {
                        Text(
                            text = statusMessage,
                            color = AccentSkyCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Feature 7: Cyber Security Sentry & Network Inspector
        item {
            JarvisGlassPanel(
                accentColor = AccentSkyCyan,
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cyber Security Sentry & LAN Auditor",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Audits Local IPv4, VPN Tunnel, DNS, ARP LAN Peers & High-Risk App Permissions",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextLavenderMuted
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentSkyCyan)
                                .clickable {
                                    scope.launch {
                                        val rep = CyberSecuritySentry.runFullCyberSecurityAudit(context)
                                        JarvisTtsManager.speak(context, rep.spokenHinglishSummary)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("cyber_sentry_scan_btn")
                        ) {
                            Text("SCAN", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                        }
                    }

                    cyberReport?.let { rep ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF0E1324))
                                .border(1.dp, AccentSkyCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Security Score: ${rep.securityScorePercent}% | Transport: ${rep.networkTransport}", color = AccentMintEmerald, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                Text("Local IPv4: ${rep.localIpv4Address} | VPN Active: ${rep.isVpnTunnelActive} | ARP Peers: ${rep.discoveredLanPeersCount}", color = Color.White, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
                                Text("Sensitive Permission Apps: ${rep.highRiskAppsCount} (${rep.highRiskApps.take(4).joinToString(", ") { it.appName }})", color = AccentChampagneGold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Feature 6: Project Chronos Focus & Eye-Strain Guardian
        item {
            JarvisGlassPanel(
                accentColor = AccentChampagneGold,
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Project Chronos — Digital Wellbeing Guardian", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            Text("20-20-20 Eye-Strain & Late-Night Doom-Scroll Monitor", color = TextLavenderMuted, fontSize = 11.5.sp)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentChampagneGold)
                                .clickable {
                                    val st = ChronosFocusGuardian.evaluateScreenFatigueAndWellness(context)
                                    JarvisTtsManager.speak(context, st.spokenHinglishIntervention)
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                                .testTag("chronos_audit_btn")
                        ) {
                            Text("AUDIT", color = Color(0xFF101528), fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                        }
                    }
                    chronosReport?.let { st ->
                        Text(
                            text = "Session: ${st.continuousSessionMinutes}m | Fatigue: ${st.fatigueLevel} | ${st.spokenHinglishIntervention}",
                            color = AccentChampagneGold,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrivingCockpitTab(
    isDrivingActive: Boolean,
    autoReadMessages: Boolean,
    onToggleDriving: (Boolean) -> Unit,
    onToggleAutoRead: (Boolean) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        JarvisGlassPanel(
            accentColor = if (isDrivingActive) AccentMintEmerald else AccentSkyCyan,
            cornerRadius = 24.dp,
            contentPadding = PaddingValues(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDrivingActive) AccentMintEmerald else AccentSkyCyan),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }

                    Column {
                        Text(
                            text = if (isDrivingActive) "DRIVING COCKPIT ACTIVE" else "DRIVING MODE OFF",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = if (isDrivingActive) "Voice-only hands-free mode engaged" else "Auto-activates when connected to Car Bluetooth",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextLavenderMuted
                        )
                    }
                }

                Switch(
                    checked = isDrivingActive,
                    onCheckedChange = onToggleDriving,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentMintEmerald
                    ),
                    modifier = Modifier.testTag("driving_mode_switch")
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            JarvisGlassPanel(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp),
                accentColor = AccentSkyCyan,
                cornerRadius = 22.dp,
                onClick = {
                    try {
                        val mapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=gas+station"))
                        mapsIntent.setPackage("com.google.android.apps.maps")
                        context.startActivity(mapsIntent)
                    } catch (_: Exception) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=gas+station")))
                    }
                }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentSkyCyan),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("Google Maps", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        Text("Gas & Navigation", color = TextLavenderMuted, fontSize = 11.sp)
                    }
                }
            }

            JarvisGlassPanel(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp),
                accentColor = RoyalPurplePrimary,
                cornerRadius = 22.dp,
                onClick = {
                    JarvisTtsManager.speak(context, "Hands-free driving mode is ready. Say 'Hey Jarvis' to dictate calls or messages.")
                }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(RoyalPurplePrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("Voice Assistant", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        Text("Hands-Free Talk", color = TextLavenderMuted, fontSize = 11.sp)
                    }
                }
            }
        }

        JarvisGlassPanel(
            accentColor = AccentMintEmerald,
            cornerRadius = 22.dp,
            contentPadding = PaddingValues(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Read Incoming Messages", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Text("Speaks WhatsApp and SMS notifications aloud", color = TextLavenderMuted, fontSize = 11.5.sp)
                }
                Switch(
                    checked = autoReadMessages,
                    onCheckedChange = onToggleAutoRead,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentMintEmerald)
                )
            }
        }
    }
}

@Composable
private fun BuiltinMacrosTab(
    onRunMacro: (name: String, query: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hotTools by PrometheusToolSynthesizer.hotLoadedTools.collectAsState()
    val swarmBundle by SwarmParallelCoordinator.lastSwarmBundle.collectAsState()

    var newToolName by remember { mutableStateOf("morning_briefing_tool") }
    var newToolPipeline by remember { mutableStateOf("Open YouTube and search Arijit Singh songs") }
    var prometheusStatus by remember { mutableStateOf<String?>(null) }

    val macros = listOf(
        Triple("YouTube Desi Song Auto-Play", "YouTube open karo aur pehla desi gaana play karo", "Arijit Singh"),
        Triple("ChatGPT Image Generator", "ChatGPT app me AI image prompt generate aur download karo", "Cyberpunk Stark City"),
        Triple("Gemini Multimodal Audit", "Gemini app me document ya image analyse karo", "Summarize screen"),
        Triple("WhatsApp Voice Note", "WhatsApp me hands-free voice note dispatch karo", "Quick reply")
    )
    val cardAccents = listOf(AccentRosePink, RoyalPurplePrimary, AccentSkyCyan, AccentMintEmerald)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Feature 1: Project Prometheus Dynamic Hot-Tool Synthesizer
        item {
            JarvisGlassPanel(
                accentColor = AccentChampagneGold,
                cornerRadius = 22.dp,
                contentPadding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Project Prometheus — Dynamic Tool Synthesizer", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Text("Synthesizes, AST-verifies & hot-loads new tools into memory without app restart.", color = TextLavenderMuted, fontSize = 11.5.sp)

                    OutlinedTextField(
                        value = newToolName,
                        onValueChange = { newToolName = it },
                        modifier = Modifier.fillMaxWidth().testTag("prometheus_tool_name_input"),
                        label = { Text("Tool Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentChampagneGold,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color(0xFF0E1324),
                            unfocusedContainerColor = Color(0xFF0E1324),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = newToolPipeline,
                        onValueChange = { newToolPipeline = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Goal Instruction") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentChampagneGold,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color(0xFF0E1324),
                            unfocusedContainerColor = Color(0xFF0E1324),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentChampagneGold)
                                .clickable {
                                    val res = PrometheusToolSynthesizer.synthesizeAndHotLoadTool(
                                        context = context,
                                        rawNameOrTrigger = newToolName,
                                        goalInstruction = newToolPipeline
                                    )
                                    prometheusStatus = res.spokenHinglishSummary
                                }
                                .padding(vertical = 10.dp)
                                .testTag("prometheus_synthesize_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Hot-Load Tool (${hotTools.size})", color = Color(0xFF101528), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }

                    if (!prometheusStatus.isNullOrBlank()) {
                        Text(text = prometheusStatus!!, color = AccentMintEmerald, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Feature 9 & 10: Swarm Multi-Agent Parallel Execution & Ghost Pilot CUA
        item {
            JarvisGlassPanel(
                accentColor = AccentSkyCyan,
                cornerRadius = 22.dp,
                contentPadding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Swarm Parallel Sub-Agents & Ghost Pilot CUA", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Text("Spawns 3 concurrent Coroutine Sub-Agents (Alpha/Beta/Gamma) or dispatches sub-pixel micro-gestures.", color = TextLavenderMuted, fontSize = 11.5.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentSkyCyan)
                                .clickable {
                                    scope.launch {
                                        val bundle = SwarmParallelCoordinator.executeParallelSwarmMission(context, "Full System & UI Intelligence Sweep")
                                        JarvisTtsManager.speak(context, bundle.synthesizedHinglishBriefing)
                                    }
                                }
                                .padding(vertical = 10.dp)
                                .testTag("swarm_execute_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Launch 3-Drone Swarm", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(RoyalPurplePrimary)
                                .clickable {
                                    GhostPilotGestureEngine.executeSubPixelGesture(
                                        context = context,
                                        gestureType = "BEZIER_DRAG",
                                        startXPct = 0.5f,
                                        startYPct = 0.7f,
                                        endXPct = 0.5f,
                                        endYPct = 0.3f
                                    )
                                }
                                .padding(vertical = 10.dp)
                                .testTag("ghost_pilot_test_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Ghost Pilot Bezier Swipe", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                        }
                    }

                    swarmBundle?.let { bundle ->
                        Text(
                            text = "Swarm (${bundle.totalWallClockMs}ms): " + bundle.droneReports.joinToString(" | ") { "${it.droneId}: ${it.findings}" },
                            color = AccentSkyCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        items(macros.size) { index ->
            val (title, desc, query) = macros[index]
            val accent = cardAccents[index % cardAccents.size]
            JarvisGlassPanel(
                accentColor = accent,
                cornerRadius = 22.dp,
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                                .clip(RoundedCornerShape(13.dp))
                                .background(accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoMode, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = desc, color = TextLavenderMuted, fontSize = 11.5.sp)
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(accent)
                            .clickable { onRunMacro(title, query) }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                            .testTag("run_macro_${title.take(6).lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("RUN", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceNoteCenterTab(
    voiceNotes: List<String>,
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    onSendReply: () -> Unit,
    onSimulateIncoming: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Detected Voice Notes & Transcripts", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(RoyalPurplePrimary)
                    .clickable { onSimulateIncoming() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+ Test Voice Note", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        JarvisGlassPanel(
            accentColor = RoyalPurplePrimary,
            cornerRadius = 24.dp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(voiceNotes) { note ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF12172C))
                            .border(1.dp, RoyalPurplePrimary.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = note,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = replyText,
                onValueChange = onReplyTextChange,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("voice_note_reply_input"),
                placeholder = { Text("Reply to voice note...", fontSize = 11.sp, color = TextLavenderMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RoyalPurplePrimary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedContainerColor = Color(0xFF101528),
                    unfocusedContainerColor = Color(0xFF101528),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(RoyalPurplePrimary)
                    .clickable { onSendReply() }
                    .padding(horizontal = 16.dp, vertical = 15.dp)
                    .testTag("voice_note_send_reply_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun WallpaperAndSystemTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val generatedGallery by NeuralImageStudioManager.generatedGallery.collectAsState()
    var isGeneratingArt by remember { mutableStateOf(false) }
    var imagePrompt by remember { mutableStateOf("Futuristic Stark Hologram Core in Cyberpunk Mumbai") }
    var selectedAspectRatio by remember { mutableStateOf("9:16") }

    val selectedPresetId by PocketStrikeOfflineEngine.selectedPresetId.collectAsState()
    val activeOfflinePreset = remember(selectedPresetId) {
        PocketStrikeOfflineEngine.curatedSlmPresets.find { it.id == selectedPresetId }
            ?: PocketStrikeOfflineEngine.curatedSlmPresets.first()
    }
    var offlineTestOutput by remember { mutableStateOf<String?>(null) }
    var mcpTestOutput by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Feature 8: In-App AI Neural Image Generator Studio
        item {
            JarvisGlassPanel(
                accentColor = AccentRosePink,
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AI Neural Image & Wallpaper Studio", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text("Generates custom AI / procedural holographic art with aspect ratio selection & 1-tap wallpaper apply.", color = TextLavenderMuted, fontSize = 11.5.sp)

                    OutlinedTextField(
                        value = imagePrompt,
                        onValueChange = { imagePrompt = it },
                        modifier = Modifier.fillMaxWidth().testTag("neural_image_prompt_input"),
                        label = { Text("Image Prompt") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentRosePink,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color(0xFF0E1324),
                            unfocusedContainerColor = Color(0xFF0E1324),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("1:1", "16:9", "9:16", "4:3").forEach { ratio ->
                            val selected = selectedAspectRatio == ratio
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) AccentRosePink else Color(0xFF151C33))
                                    .clickable { selectedAspectRatio = ratio }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(ratio, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.horizontalGradient(listOf(AccentRosePink, RoyalPurplePrimary)))
                                .clickable(enabled = !isGeneratingArt) {
                                    isGeneratingArt = true
                                    scope.launch {
                                        val art = NeuralImageStudioManager.generateNeuralImage(
                                            context = context,
                                            prompt = imagePrompt,
                                            aspectRatio = selectedAspectRatio
                                        )
                                        isGeneratingArt = false
                                        JarvisTtsManager.speak(context, "Boss, '${art.prompt}' ka neural artwork generate ho gaya hai.")
                                    }
                                }
                                .padding(vertical = 12.dp)
                                .testTag("neural_image_generate_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (isGeneratingArt) "Generating..." else "Generate Artwork", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(AccentMintEmerald)
                                .clickable(enabled = !isGeneratingArt) {
                                    isGeneratingArt = true
                                    scope.launch {
                                        val art = NeuralImageStudioManager.generateNeuralImage(
                                            context = context,
                                            prompt = imagePrompt,
                                            aspectRatio = selectedAspectRatio
                                        )
                                        NeuralImageStudioManager.setArtworkAsDeviceWallpaper(context, art)
                                        isGeneratingArt = false
                                        JarvisTtsManager.speak(context, "Boss, naya neural artwork home screen wallpaper par set kar diya gaya hai.")
                                    }
                                }
                                .padding(vertical = 12.dp)
                                .testTag("neural_image_set_wallpaper_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Generate & Set Wallpaper", color = Color(0xFF0E1324), fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                        }
                    }

                    generatedGallery.firstOrNull()?.bitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Generated Neural Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, AccentRosePink, RoundedCornerShape(16.dp))
                        )
                    }
                }
            }
        }

        // Feature 2 & 4: PocketStrike Dual-Core Offline Brain & MCP Server Bridge
        item {
            JarvisGlassPanel(
                accentColor = AccentMintEmerald,
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PocketStrike Offline Brain & MCP Server Bridge", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text("Active Local GGUF Preset: ${activeOfflinePreset.displayName} (${activeOfflinePreset.quantization}, ${activeOfflinePreset.ramRequiredMb}MB RAM)", color = AccentMintEmerald, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentMintEmerald)
                                .clickable {
                                    scope.launch {
                                        val res = PocketStrikeOfflineEngine.executeOfflineCommand(
                                            context = context,
                                            utterance = "battery status report"
                                        )
                                        offlineTestOutput = res.spokenHinglishResponse
                                    }
                                }
                                .padding(vertical = 10.dp)
                                .testTag("pocketstrike_offline_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Test Offline Brain", color = Color(0xFF0E1324), fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentSkyCyan)
                                .clickable {
                                    scope.launch {
                                        val res = McpClientBridge.invokeMcpTool(
                                            context = context,
                                            serverIdOrToolName = "mcp_android_os_telemetry",
                                            toolName = "get_device_telemetry"
                                        )
                                        mcpTestOutput = res.spokenHinglishSummary
                                    }
                                }
                                .padding(vertical = 10.dp)
                                .testTag("mcp_probe_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Query MCP Server", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                        }
                    }

                    if (!offlineTestOutput.isNullOrBlank()) {
                        Text(text = offlineTestOutput!!, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    if (!mcpTestOutput.isNullOrBlank()) {
                        Text(text = mcpTestOutput!!, color = AccentSkyCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        item {
            JarvisGlassPanel(
                accentColor = AccentSkyCyan,
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(AccentSkyCyan),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                        }

                        Column {
                            Text("Liquid Aurora Live Wallpaper", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            Text("Interactive glowing particle field on phone home screen", color = TextLavenderMuted, fontSize = 12.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(listOf(AccentSkyCyan, RoyalIndigoAccent))
                            )
                            .clickable {
                                try {
                                    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                        putExtra(
                                            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                            ComponentName(context, JarvisWallpaperService::class.java)
                                        )
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                                    context.startActivity(fallbackIntent)
                                }
                            }
                            .padding(vertical = 13.dp)
                            .testTag("apply_live_wallpaper_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Apply Live Wallpaper to Home Screen", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                    }
                }
            }
        }

        item {
            JarvisGlassPanel(
                accentColor = RoyalPurplePrimary,
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(RoyalPurplePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = Color.White)
                        }

                        Column {
                            Text("Default Digital Assistant", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            Text("Hold power button or swipe from corner to summon JARVIS anywhere", color = TextLavenderMuted, fontSize = 12.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(listOf(RoyalPurplePrimary, AccentRosePink))
                            )
                            .clickable {
                                try {
                                    val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val intent = Intent(Settings.ACTION_SETTINGS)
                                    context.startActivity(intent)
                                }
                            }
                            .padding(vertical = 13.dp)
                            .testTag("set_default_assistant_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Set JARVIS as Default Android Assistant", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}
