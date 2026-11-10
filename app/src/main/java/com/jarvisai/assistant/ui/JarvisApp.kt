package com.jarvisai.assistant.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.JarvisHudTheme
import com.jarvisai.assistant.agent.ActiveCodingAgent
import com.jarvisai.assistant.core.SuperJarvisOrchestrator
import com.jarvisai.assistant.ide.IdeScreen
import com.jarvisai.assistant.security.ManageSpaceActivity
import com.jarvisai.assistant.service.JarvisOverlayHudService
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.ui.components.AgentExecutionCard
import com.jarvisai.assistant.ui.components.AntigravityLoginDialog
import com.jarvisai.assistant.ui.components.ArcReactorView
import com.jarvisai.assistant.ui.components.JarvisEmergencyStopBanner
import com.jarvisai.assistant.ui.components.JarvisNavigationDrawer
import com.jarvisai.assistant.ui.components.JarvisUnifiedCommandBar
import com.jarvisai.assistant.ui.components.JarvisUnifiedTopBar
import com.jarvisai.assistant.ui.components.LocalTerminalDialog
import com.jarvisai.assistant.ui.screens.AdbConnectScreen
import com.jarvisai.assistant.ui.screens.AgentHubScreen
import com.jarvisai.assistant.ui.screens.ApiSettingsScreen
import com.jarvisai.assistant.ui.screens.ArcReactorChargingScreen
import com.jarvisai.assistant.ui.screens.BrowserScreen
import com.jarvisai.assistant.ui.screens.DocumentAssistantScreen
import com.jarvisai.assistant.ui.screens.GeneralSettingsScreen
import com.jarvisai.assistant.ui.screens.HomeDashboardScreen
import com.jarvisai.assistant.ui.screens.LiveVoiceScreen
import com.jarvisai.assistant.ui.screens.MemoryCenterScreen
import com.jarvisai.assistant.ui.screens.MemoryReviewScreen
import com.jarvisai.assistant.ui.screens.NotificationAssistantScreen
import com.jarvisai.assistant.ui.screens.OpticalSolverScreen
import com.jarvisai.assistant.ui.screens.PhoneControlsScreen
import com.jarvisai.assistant.ui.screens.PluginsManagementScreen
import com.jarvisai.assistant.ui.screens.PolyglotDiplomatScreen
import com.jarvisai.assistant.ui.screens.VisionScreen
import com.jarvisai.assistant.ui.screens.WorkflowScreen
import com.jarvisai.assistant.viewmodel.JarvisViewModel
import kotlinx.coroutines.launch
import java.io.File

enum class JarvisMasterTab(
    val title: String,
    val icon: ImageVector,
    val destination: JarvisDestination
) {
    VOICE_HUB("Jarvis Voice", Icons.Default.Mic, JarvisDestination.VOICE),
    SORA_IDE("Sora IDE", Icons.Default.Code, JarvisDestination.IDE),
    TERMINAL("Terminal", Icons.Default.Terminal, JarvisDestination.HOME),
    AGENT_SUPERVISOR("Agents", Icons.Default.Psychology, JarvisDestination.AGENT_HUB),
    BROWSER_FLOW("Flow & Ext", Icons.Default.Extension, JarvisDestination.PLUGINS)
}

enum class JarvisDestination(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Default.Home),
    BROWSER("browser", "Browser", Icons.Default.Search),
    VOICE("voice", "Live Voice", Icons.Default.Mic),
    AGENT_HUB("agent_hub", "Hub", Icons.Default.Terminal),
    CHAT("chat", "Chat", Icons.AutoMirrored.Filled.Chat),
    MEMORY("memory", "Memory", Icons.Default.Memory),
    CONTROLS("controls", "Controls", Icons.Default.Build),
    API_SETTINGS("api_settings", "API", Icons.Default.Build),
    GENERAL_SETTINGS("general_settings", "Settings", Icons.Default.Settings),
    VISION("vision", "Vision", Icons.Default.CameraAlt),
    DOCUMENT("document", "Docs", Icons.Default.Description),
    WORKFLOW("workflow", "Workflow", Icons.Default.AutoMode),
    NOTIFICATIONS("notifications", "Alerts", Icons.Default.Notifications),
    MEMORY_REVIEW("memory_review", "Timeline", Icons.Default.History),
    OPTICAL_SOLVER("optical_solver", "Optical Solver", Icons.Default.Calculate),
    POLYGLOT_DIPLOMAT("polyglot_diplomat", "Translator", Icons.Default.Translate),
    CHARGING_TELEMETRY("charging_telemetry", "Arc Dock", Icons.Default.BatteryChargingFull),
    IDE("ide", "IDE", Icons.Default.Code),
    ADB_CONNECT("adb_connect", "ADB", Icons.Default.DeveloperMode),
    PLUGINS("plugins", "Plugins", Icons.Default.Extension)
}

/**
 * Unified AntiGem-Grade Master Architecture for Jarvis AI Ultra.
 * Eliminates dual-navigation bars; uses a single clean Top Bar, Modal Navigation Drawer,
 * central Conversational & Voice Stage with inline Arc Reactor & Collapsible Agent Execution Cards,
 * Google Antigravity PKCE OAuth & CloudCode PA streaming, and a Unified Floating Command Bar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JarvisApp(viewModel: JarvisViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val orchestrator = remember { SuperJarvisOrchestrator.getInstance(context) }
    val subagentTasks by orchestrator.agentOrchestrator.tasks.collectAsStateWithLifecycle()
    val activeIntent by orchestrator.activeVoiceIntent.collectAsStateWithLifecycle()

    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val status by viewModel.assistantStatus.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKeyFlow.collectAsStateWithLifecycle()
    val isKeystoreBacked by viewModel.isUsingKeystoreEncryption.collectAsStateWithLifecycle()

    val isAntigravityLoggedIn by viewModel.isAntigravityLoggedIn.collectAsStateWithLifecycle()
    val antigravityUserEmail by viewModel.antigravityUserEmail.collectAsStateWithLifecycle()
    val antigravityTier by viewModel.antigravityTier.collectAsStateWithLifecycle()
    val antigravityAuthUrl by viewModel.antigravityAuthUrl.collectAsStateWithLifecycle()
    val isOAuthServerActive by viewModel.isOAuthServerActive.collectAsStateWithLifecycle()

    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsStateWithLifecycle()
    val ttsErrorMessage by viewModel.ttsErrorMessage.collectAsStateWithLifecycle()
    val memorySavedNotification by viewModel.memorySavedNotification.collectAsStateWithLifecycle()

    val sessions by viewModel.chatSessions.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val currentMessages by viewModel.currentMessages.collectAsStateWithLifecycle()
    val streamingPartial by viewModel.streamingPartialResponse.collectAsStateWithLifecycle()
    val isChatLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()

    val isLiveVoiceActive by viewModel.isLiveVoiceActive.collectAsStateWithLifecycle()
    val isMicMuted by viewModel.isMicMuted.collectAsStateWithLifecycle()
    val liveUiStatus by viewModel.liveUiStatus.collectAsStateWithLifecycle()
    val liveAudioLevel by viewModel.liveAudioLevel.collectAsStateWithLifecycle()
    val liveConnectionLabel by viewModel.liveConnectionLabel.collectAsStateWithLifecycle()
    val liveVoiceError by viewModel.liveVoiceError.collectAsStateWithLifecycle()
    val liveTranscripts by viewModel.liveTranscripts.collectAsStateWithLifecycle()
    val isScreenSharing by viewModel.isScreenSharing.collectAsStateWithLifecycle()
    val screenFramesSent by viewModel.screenFramesSent.collectAsStateWithLifecycle()

    val allMemories by viewModel.allMemories.collectAsStateWithLifecycle()
    val filteredMemories by viewModel.filteredMemories.collectAsStateWithLifecycle()
    val memorySearchQuery by viewModel.memorySearchQuery.collectAsStateWithLifecycle()
    val selectedMemoryCategory by viewModel.selectedMemoryCategory.collectAsStateWithLifecycle()
    val selectedCognitiveTab by viewModel.selectedCognitiveTab.collectAsStateWithLifecycle()
    val stmTurns by viewModel.stmTurns.collectAsStateWithLifecycle()
    val entityFocusStack by viewModel.entityFocusStack.collectAsStateWithLifecycle()
    val workingMemoryPlan by viewModel.workingMemoryPlan.collectAsStateWithLifecycle()
    val episodicEvents by viewModel.episodicEvents.collectAsStateWithLifecycle()
    val semanticTriples by viewModel.semanticTriples.collectAsStateWithLifecycle()

    val isFlashlightOn by viewModel.isFlashlightOn.collectAsStateWithLifecycle()
    val hasFlashlightHardware by viewModel.hasFlashlightHardware.collectAsStateWithLifecycle()
    val filteredInstalledApps by viewModel.filteredInstalledApps.collectAsStateWithLifecycle()
    val appSearchQuery by viewModel.appSearchQuery.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val phoneControlFeedback by viewModel.phoneControlFeedback.collectAsStateWithLifecycle()

    val isTestingConnection by viewModel.isTestingConnection.collectAsStateWithLifecycle()
    val connectionTestResult by viewModel.connectionTestResult.collectAsStateWithLifecycle()

    val isWakeWordActive by viewModel.isWakeWordActive.collectAsStateWithLifecycle()
    val visionAnalysisResult by viewModel.visionAnalysisResult.collectAsStateWithLifecycle()
    val isVisionAnalyzing by viewModel.isVisionAnalyzing.collectAsStateWithLifecycle()
    val visionError by viewModel.visionError.collectAsStateWithLifecycle()
    val documentAnalysisResult by viewModel.documentAnalysisResult.collectAsStateWithLifecycle()
    val isDocumentAnalyzing by viewModel.isDocumentAnalyzing.collectAsStateWithLifecycle()
    val documentError by viewModel.documentError.collectAsStateWithLifecycle()
    val notificationLogs by viewModel.notificationLogs.collectAsStateWithLifecycle()
    val isNotificationAccessGranted by viewModel.isNotificationAccessGranted.collectAsStateWithLifecycle()
    val notificationSummary by viewModel.notificationSummary.collectAsStateWithLifecycle()
    val isSummarizingNotifications by viewModel.isSummarizingNotifications.collectAsStateWithLifecycle()
    val allWorkflows by viewModel.allWorkflows.collectAsStateWithLifecycle()
    val workflowExecutionLogs by viewModel.workflowExecutionLogs.collectAsStateWithLifecycle()
    val isAutonomousTaskOrVoiceActive by viewModel.isAutonomousTaskOrVoiceActive.collectAsStateWithLifecycle()

    val agenticStepLogs by viewModel.agenticStepLogs.collectAsStateWithLifecycle()
    val isAgenticLoopRunning by viewModel.isAgenticLoopRunning.collectAsStateWithLifecycle()
    val detectedBadges by viewModel.detectedSetOfMarkBadges.collectAsStateWithLifecycle()
    val overlayHudState by viewModel.overlayHudState.collectAsStateWithLifecycle()
    val activeReplyables by viewModel.activeReplyables.collectAsStateWithLifecycle()
    val activeFormSession by viewModel.activeFormSession.collectAsStateWithLifecycle()
    val lastScratchpadResult by viewModel.lastScratchpadResult.collectAsStateWithLifecycle()
    val isRecordingDemonstration by viewModel.isRecordingDemonstration.collectAsStateWithLifecycle()
    val recordedDemonstrationSteps by viewModel.recordedDemonstrationSteps.collectAsStateWithLifecycle()
    val pendingHighRiskGate by viewModel.pendingHighRiskGate.collectAsStateWithLifecycle()

    var activeScreen by rememberSaveable { mutableStateOf("home") }
    var activeModelName by rememberSaveable { mutableStateOf("Gemini 2.0 Live") }
    var promptInput by rememberSaveable { mutableStateOf("") }
    var showTerminalDialog by rememberSaveable { mutableStateOf(false) }
    var showAntigravityLoginDialog by rememberSaveable { mutableStateOf(false) }
    var showToolsSheet by remember { mutableStateOf(false) }

    val workspaces = remember {
        val projectsDir = File(LocalEnvironmentManager.getHomeDir(context), "projects")
        if (!projectsDir.exists()) projectsDir.mkdirs()
        projectsDir.listFiles { f -> f.isDirectory }?.toList() ?: emptyList()
    }

    if (activeScreen != "home") {
        BackHandler {
            activeScreen = "home"
        }
    }

    JarvisHudTheme(themeMode = settings.themeMode) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                JarvisNavigationDrawer(
                    sessions = sessions,
                    currentSessionId = currentSessionId,
                    workspaces = workspaces,
                    isAntigravityLoggedIn = isAntigravityLoggedIn,
                    antigravityUserEmail = antigravityUserEmail,
                    onSessionClick = { id ->
                        viewModel.selectChatSession(id)
                        activeScreen = "home"
                    },
                    onNewChatClick = {
                        viewModel.startNewChat()
                        activeScreen = "home"
                    },
                    onWorkspaceClick = {
                        activeScreen = "ide"
                    },
                    onNavigateDestination = { dest ->
                        when (dest) {
                            "antigravity_login" -> {
                                showAntigravityLoginDialog = true
                            }
                            "manage_space" -> {
                                context.startActivity(Intent(context, ManageSpaceActivity::class.java))
                            }
                            "terminal" -> {
                                showTerminalDialog = true
                            }
                            else -> {
                                activeScreen = dest
                            }
                        }
                    },
                    onCloseDrawer = {
                        coroutineScope.launch { drawerState.close() }
                    }
                )
            }
        ) {
            Scaffold(
                containerColor = Color(0xFF0A0E17),
                topBar = {
                    JarvisUnifiedTopBar(
                        activeModelName = activeModelName,
                        isAgentBusy = isChatLoading || isAgenticLoopRunning || subagentTasks.any { it.status.name == "RUNNING" },
                        isVoiceActive = isLiveVoiceActive,
                        onMenuClick = {
                            coroutineScope.launch { drawerState.open() }
                        },
                        onModelSelect = { selected ->
                            activeModelName = selected
                            when {
                                selected.contains("Antigravity", ignoreCase = true) -> {
                                    val modelId = if (selected.contains("Claude", ignoreCase = true)) {
                                        "claude-3-5-sonnet"
                                    } else {
                                        "gemini-2.5-flash-thinking"
                                    }
                                    viewModel.updateTextModel(modelId)
                                    orchestrator.agentOrchestrator.selectAgent(ActiveCodingAgent.GEMINI_DIRECT)
                                }
                                selected.contains("Claude", ignoreCase = true) ->
                                    orchestrator.agentOrchestrator.selectAgent(ActiveCodingAgent.CLAUDE_CODE)
                                selected.contains("OpenCode", ignoreCase = true) ->
                                    orchestrator.agentOrchestrator.selectAgent(ActiveCodingAgent.OPEN_CODE)
                                else ->
                                    orchestrator.agentOrchestrator.selectAgent(ActiveCodingAgent.GEMINI_DIRECT)
                            }
                        },
                        onTerminalClick = { showTerminalDialog = true },
                        onIdeClick = {
                            activeScreen = if (activeScreen == "ide") "home" else "ide"
                        },
                        onOverlayHudClick = {
                            if (Settings.canDrawOverlays(context)) {
                                JarvisOverlayHudService.toggleHud(context)
                            } else {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        }
                    )
                },
                bottomBar = {
                    if (activeScreen == "home") {
                        JarvisUnifiedCommandBar(
                            promptText = promptInput,
                            onPromptChange = { promptInput = it },
                            isLiveVoiceActive = isLiveVoiceActive,
                            isMicMuted = isMicMuted,
                            audioLevel = liveAudioLevel,
                            onSendClick = {
                                val cleanPrompt = promptInput.trim()
                                if (cleanPrompt.isNotBlank()) {
                                    promptInput = ""
                                    if (cleanPrompt.startsWith("/bash ") || cleanPrompt.startsWith("/sh ")) {
                                        val cmd = cleanPrompt.removePrefix("/bash ").removePrefix("/sh ")
                                        coroutineScope.launch {
                                            orchestrator.executeTerminalCommand(cmd)
                                        }
                                    } else if (activeModelName == "Claude Code" || activeModelName == "OpenCode") {
                                        val agentKey = if (activeModelName == "OpenCode") "opencode" else "claude"
                                        coroutineScope.launch {
                                            orchestrator.delegateCodingTask(
                                                prompt = cleanPrompt,
                                                agentType = agentKey
                                            )
                                        }
                                        viewModel.sendChatMessage(cleanPrompt)
                                    } else if (isLiveVoiceActive) {
                                        viewModel.sendLiveVoiceTextPrompt(cleanPrompt)
                                    } else {
                                        viewModel.sendChatMessage(cleanPrompt)
                                    }
                                }
                            },
                            onMicClick = {
                                if (isLiveVoiceActive) {
                                    viewModel.stopLiveVoiceSession()
                                } else {
                                    viewModel.startLiveVoiceSession()
                                }
                            },
                            onToolsClick = {
                                showToolsSheet = true
                            }
                        )
                    }
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(Color(0xFF0A0E17))
                ) {
                    if (isAutonomousTaskOrVoiceActive) {
                        JarvisEmergencyStopBanner(
                            onEmergencyStop = { viewModel.triggerEmergencyStop() }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        when (activeScreen) {
                            "home" -> {
                                val listState = rememberLazyListState()

                                if (currentMessages.isEmpty() && subagentTasks.isEmpty() && agenticStepLogs.isEmpty() && liveTranscripts.isEmpty()) {
                                    // Central Stage Idle View: Pulsing Arc Reactor + Live Audio Waveform + 1-Tap Quick Actions
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        ArcReactorView(
                                            status = status,
                                            audioLevel = liveAudioLevel,
                                            size = 168.dp
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        Text(
                                            text = if (isLiveVoiceActive) {
                                                "Jarvis Live Voice Streaming ($liveConnectionLabel)"
                                            } else {
                                                "Jarvis AI Ultra Online"
                                            },
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = if (isLiveVoiceActive) {
                                                "Waveform Active • Speak naturally in English, Hindi, or Hinglish"
                                            } else {
                                                "Speak naturally, delegate to Claude Code / OpenCode, or launch Sora IDE & Terminal."
                                            },
                                            color = Color(0xFF94A3B8),
                                            fontSize = 12.5.sp,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(24.dp))

                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            QuickStageChip("☁️ Antigravity OAuth") { showAntigravityLoginDialog = true }
                                            QuickStageChip("💻 Linux Terminal") { showTerminalDialog = true }
                                            QuickStageChip("📝 Sora Code IDE") { activeScreen = "ide" }
                                            QuickStageChip("🤖 Multi-Agent Hub") { activeScreen = "agent_hub" }
                                            QuickStageChip("📲 Wireless ADB") { activeScreen = "adb_connect" }
                                            QuickStageChip("🧩 Google Flow & Ext") { activeScreen = "plugins" }
                                            QuickStageChip("🎛️ Command Deck") { activeScreen = "dashboard_deck" }
                                        }
                                    }
                                } else {
                                    // Active Conversational Stream + Collapsible Subagent Execution Cards
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        // Collapsible Subagent Execution Cards (Claude Code / OpenCode / Termux PTY)
                                        items(subagentTasks, key = { "task_${it.id}" }) { task ->
                                            AgentExecutionCard(
                                                agentName = "${task.agentType.uppercase()} SUBAGENT",
                                                command = task.prompt,
                                                output = task.outputSummary.ifBlank { "Working in ${task.workingDirectory}..." },
                                                status = task.status.name,
                                                stepInfo = "Step ${task.progressStep}/${task.totalSteps}"
                                            )
                                        }

                                        // Autonomous Screen Operator Execution Cards (collapsed to prevent log spam)
                                        if (agenticStepLogs.isNotEmpty()) {
                                            item(key = "screen_operator_card") {
                                                AgentExecutionCard(
                                                    agentName = "ANDROID SCREEN OPERATOR",
                                                    command = "Autonomous UI Perception & Action Loop ($activeIntent)",
                                                    output = agenticStepLogs.joinToString("\n"),
                                                    status = if (isAgenticLoopRunning) "RUNNING" else "SUCCESS",
                                                    stepInfo = "Step ${agenticStepLogs.size}"
                                                )
                                            }
                                        }

                                        // Live Voice Transcripts when Live Voice session is active
                                        items(liveTranscripts, key = { "live_${it.id}" }) { transcript ->
                                            val isUser = transcript.speaker.equals("You", ignoreCase = true) ||
                                                transcript.speaker.equals("USER", ignoreCase = true)
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isUser) Color(0xFF1F2937) else Color(0xFF111827),
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isUser) Color(0xFF38BDF8).copy(alpha = 0.35f)
                                                        else Color(0xFF00E5FF).copy(alpha = 0.3f)
                                                    )
                                                ) {
                                                    Text(
                                                        text = transcript.text,
                                                        color = Color.White,
                                                        fontSize = 13.5.sp,
                                                        modifier = Modifier.padding(12.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Persistent Chat Conversation Messages
                                        items(currentMessages, key = { "msg_${it.id}" }) { message ->
                                            val isUser = message.role.equals("user", ignoreCase = true)
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isUser) Color(0xFF1F2937) else Color(0xFF111827),
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isUser) Color(0xFF38BDF8).copy(alpha = 0.3f)
                                                        else Color(0xFF00E5FF).copy(alpha = 0.22f)
                                                    )
                                                ) {
                                                    Text(
                                                        text = message.content,
                                                        color = Color.White,
                                                        fontSize = 13.5.sp,
                                                        modifier = Modifier.padding(12.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Streaming Partial Response (including DeepMind Thinking Stream)
                                        val partialText = streamingPartial.orEmpty()
                                        if (partialText.isNotBlank()) {
                                            item(key = "streaming_partial") {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFF111827),
                                                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = partialText,
                                                        color = Color.White,
                                                        fontSize = 13.5.sp,
                                                        modifier = Modifier.padding(12.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            "ide" -> IdeScreen(onNavigateBack = { activeScreen = "home" })

                            "adb_connect" -> AdbConnectScreen(onNavigateBack = { activeScreen = "home" })

                            "plugins" -> PluginsManagementScreen(onNavigateBack = { activeScreen = "home" })

                            "agent_hub" -> AgentHubScreen(
                                onBack = { activeScreen = "home" },
                                onRunMacro = { name, q ->
                                    viewModel.runAutonomousAgentGoal("$name: $q")
                                    activeScreen = "home"
                                }
                            )

                            "voice" -> {
                                val companionEmotionMood by viewModel.companionEmotionMood.collectAsStateWithLifecycle()
                                LiveVoiceScreen(
                                    settings = settings,
                                    status = status,
                                    liveUiStatus = liveUiStatus,
                                    hasApiKey = apiKey.isNotBlank(),
                                    isSessionActive = isLiveVoiceActive,
                                    isMicMuted = isMicMuted,
                                    audioLevel = liveAudioLevel,
                                    connectionLabel = if (activeIntent != "Idle") "$liveConnectionLabel • $activeIntent" else liveConnectionLabel,
                                    errorMessage = liveVoiceError,
                                    transcripts = liveTranscripts,
                                    isScreenSharing = isScreenSharing,
                                    screenFramesSent = screenFramesSent,
                                    memorySavedNotification = memorySavedNotification,
                                    onBack = { activeScreen = "home" },
                                    onSelectLanguage = { viewModel.updateLanguage(it) },
                                    onStartSession = { viewModel.startLiveVoiceSession() },
                                    onStopSession = { viewModel.stopLiveVoiceSession() },
                                    onToggleMute = { viewModel.toggleLiveVoiceMute() },
                                    onInterrupt = { viewModel.interruptLiveVoice() },
                                    onSendLiveText = { viewModel.sendLiveVoiceTextPrompt(it) },
                                    onExecuteQuickPhoneCommand = { viewModel.executeVoicePhoneCommand(it) },
                                    onStartScreenShare = { code, data -> viewModel.startLiveScreenSharing(code, data) },
                                    onStopScreenShare = { viewModel.stopLiveScreenSharing() },
                                    onScreenShareDenied = { viewModel.onScreenSharePermissionDenied() },
                                    onNavigateToApiSettings = { activeScreen = "api_settings" },
                                    companionEmotionMood = companionEmotionMood,
                                    onSelectPersonalityMode = { viewModel.updatePersonalityMode(it) },
                                    onTriggerProactiveCompanionNow = { viewModel.triggerProactiveCompanionNow() }
                                )
                            }

                            "memory" -> MemoryCenterScreen(
                                settings = settings,
                                status = status,
                                memories = filteredMemories,
                                totalMemoriesCount = allMemories.size,
                                searchQuery = memorySearchQuery,
                                selectedCategory = selectedMemoryCategory,
                                onBack = { activeScreen = "home" },
                                onSearchQueryChange = { viewModel.setMemorySearchQuery(it) },
                                onSelectCategory = { viewModel.setSelectedMemoryCategory(it) },
                                onToggleMemoryEnabled = { viewModel.updateMemoryEnabled(it) },
                                onSaveMemory = { title, content, cat, tags, pinned ->
                                    viewModel.saveApprovedMemory(title, content, cat, tags, pinned)
                                },
                                onUpdateMemory = { viewModel.updateMemory(it) },
                                onTogglePinMemory = { viewModel.toggleMemoryPinned(it) },
                                onDeleteMemory = { viewModel.deleteMemory(it) },
                                onClearAllMemories = { viewModel.clearAllMemories() },
                                selectedCognitiveTab = selectedCognitiveTab,
                                onSelectCognitiveTab = { viewModel.setSelectedCognitiveTab(it) },
                                stmTurns = stmTurns,
                                entityFocusStack = entityFocusStack,
                                workingMemoryPlan = workingMemoryPlan,
                                episodicEvents = episodicEvents,
                                semanticTriples = semanticTriples,
                                onClearShortTermMemory = { viewModel.clearShortTermMemory() },
                                onAddWorkingMemoryStep = { title, param -> viewModel.startOrAddWorkingMemoryStep(title, param) },
                                onCompleteWorkingMemoryStep = { viewModel.completeWorkingMemoryStep(it) },
                                onPutWorkingScratchpad = { k, v -> viewModel.putWorkingMemoryScratchpad(k, v) },
                                onRemoveWorkingScratchpad = { viewModel.removeWorkingMemoryScratchpad(it) },
                                onClearWorkingMemory = { viewModel.clearWorkingMemory() },
                                onAddEpisodicEvent = { sum, type, apps, valScore ->
                                    viewModel.addManualEpisodicEvent(sum, type, apps, valScore)
                                },
                                onDeleteEpisodicEvent = { viewModel.deleteEpisodicEvent(it) },
                                onClearEpisodicEvents = { viewModel.clearAllEpisodicEvents() },
                                onSaveSemanticTriple = { sub, pred, obj, cat ->
                                    viewModel.saveSemanticTriple(sub, pred, obj, cat)
                                },
                                onDeleteSemanticTriple = { viewModel.deleteSemanticTriple(it) },
                                onClearSemanticTriples = { viewModel.clearAllSemanticTriples() },
                                onConsolidateMemories = { viewModel.triggerMemoryConsolidation() }
                            )

                            "controls" -> PhoneControlsScreen(
                                settings = settings,
                                status = status,
                                isFlashlightOn = isFlashlightOn,
                                hasFlashlightHardware = hasFlashlightHardware,
                                installedApps = filteredInstalledApps,
                                appSearchQuery = appSearchQuery,
                                reminders = reminders,
                                actionFeedback = phoneControlFeedback,
                                onBack = { activeScreen = "home" },
                                onDismissFeedback = { viewModel.clearPhoneControlFeedback() },
                                onToggleFlashlight = { viewModel.toggleFlashlight(it) },
                                onOpenWifiSettings = { viewModel.openWifiSettings() },
                                onOpenBluetoothSettings = { viewModel.openBluetoothSettings() },
                                onOpenAndroidSettings = { viewModel.openAndroidSettings() },
                                onOpenDisplaySettings = { viewModel.openDisplaySettings() },
                                onOpenSoundSettings = { viewModel.openSoundSettings() },
                                onCreateAlarm = { h, m, label -> viewModel.createAlarm(h, m, label) },
                                onCreateReminder = { title, notes, time, cal ->
                                    viewModel.createReminder(title, notes, time, cal)
                                },
                                onToggleReminderCompleted = { viewModel.toggleReminderCompleted(it) },
                                onDeleteReminder = { viewModel.deleteReminder(it) },
                                onInitiateConfirmedCall = { viewModel.initiateConfirmedCall(it) },
                                onAppSearchQueryChange = { viewModel.setAppSearchQuery(it) },
                                onRefreshApps = { viewModel.refreshInstalledApps() },
                                onLaunchApp = { viewModel.launchApp(it) }
                            )

                            "api_settings" -> {
                                val availableTextModels by viewModel.availableTextModels.collectAsStateWithLifecycle()
                                val availableVoiceModels by viewModel.availableVoiceModels.collectAsStateWithLifecycle()
                                ApiSettingsScreen(
                                    settings = settings,
                                    status = status,
                                    currentApiKey = apiKey,
                                    maskedApiKey = viewModel.getMaskedApiKey(),
                                    isKeystoreBacked = isKeystoreBacked,
                                    isTestingConnection = isTestingConnection,
                                    connectionTestResult = connectionTestResult,
                                    availableTextModels = availableTextModels,
                                    availableVoiceModels = availableVoiceModels,
                                    onBack = { activeScreen = "home" },
                                    onSaveOrUpdateKey = { viewModel.saveApiKey(it) },
                                    onDeleteKey = { viewModel.deleteApiKey() },
                                    onTestConnection = { viewModel.testGeminiConnection(it) },
                                    onSyncModelsFromApi = { viewModel.syncSupportedModelsFromApi(it) },
                                    onSelectTextModel = { viewModel.updateTextModel(it) },
                                    onSelectVoiceModel = { viewModel.updateVoiceModel(it) },
                                    onSelectScreenOperatorModel = { viewModel.updateScreenOperatorModel(it) },
                                    onSelectRouterModel = { viewModel.updateRouterModel(it) }
                                )
                            }

                            "settings" -> {
                                val companionModeState by viewModel.companionModeState.collectAsStateWithLifecycle()
                                val companionModeStatusMessage by viewModel.companionModeStatusMessage.collectAsStateWithLifecycle()
                                val companionEmotionMood by viewModel.companionEmotionMood.collectAsStateWithLifecycle()
                                val wakeWordState by viewModel.wakeWordState.collectAsStateWithLifecycle()
                                val wakeWordError by viewModel.wakeWordError.collectAsStateWithLifecycle()
                                val availableTextModels by viewModel.availableTextModels.collectAsStateWithLifecycle()
                                val availableVoiceModels by viewModel.availableVoiceModels.collectAsStateWithLifecycle()
                                GeneralSettingsScreen(
                                    settings = settings,
                                    status = status,
                                    sessionsCount = sessions.size,
                                    memoriesCount = allMemories.size,
                                    isTtsSpeaking = isTtsSpeaking,
                                    ttsErrorMessage = ttsErrorMessage,
                                    onBack = { activeScreen = "home" },
                                    onUpdateAssistantName = { viewModel.updateAssistantName(it) },
                                    onUpdateLanguage = { viewModel.updateLanguage(it) },
                                    onUpdateVoiceGender = { viewModel.updateVoiceGender(it) },
                                    onUpdateVoicePersona = { viewModel.updateVoicePersona(it) },
                                    onPreviewVoice = { gender, persona -> viewModel.previewVoiceSelection(gender, persona) },
                                    onStopVoicePreview = { viewModel.stopSpeaking() },
                                    onUpdateVoiceSpeed = { viewModel.updateVoiceSpeed(it) },
                                    onUpdateVoicePitch = { viewModel.updateVoicePitch(it) },
                                    onUpdateAutoSpeakChat = { viewModel.updateAutoSpeakChatReplies(it) },
                                    onUpdateFloatingOverlay = { viewModel.updateFloatingOverlayEnabled(it) },
                                    onUpdateThemeMode = { viewModel.updateThemeMode(it) },
                                    onUpdateThemePalette = { viewModel.updateThemePalette(it) },
                                    onUpdateThemeDisplayMode = { viewModel.updateThemeDisplayMode(it) },
                                    onUpdateMemoryEnabled = { viewModel.updateMemoryEnabled(it) },
                                    onUpdateAutoInjectMemories = { viewModel.updateAutoInjectMemories(it) },
                                    onUpdateConfirmConsequential = { viewModel.updateConfirmConsequentialActions(it) },
                                    onUpdatePrivacyRedact = { viewModel.updatePrivacyRedactTelemetry(it) },
                                    onClearChatHistory = { viewModel.clearAllChatHistory() },
                                    onClearMemories = { viewModel.clearAllMemories() },
                                    onUpdatePersonalityMode = { viewModel.updatePersonalityMode(it) },
                                    onActivateFemaleVoiceCompanion = { viewModel.enableFemaleVoiceCompanionMode() },
                                    onDeactivateFemaleVoiceCompanion = { viewModel.deactivateFemaleVoiceCompanionMode() },
                                    companionModeState = companionModeState,
                                    companionModeStatusMessage = companionModeStatusMessage,
                                    onUpdateWakeWordEnabled = { viewModel.toggleWakeWord(it) },
                                    wakeWordState = wakeWordState,
                                    wakeWordErrorMessage = wakeWordError?.userFriendlyMessage,
                                    onUpdateRequireBiometric = { viewModel.updateRequireBiometricForHighRisk(it) },
                                    onUpdateMaxAutonomousSteps = { viewModel.updateMaxAutonomousSteps(it) },
                                    companionEmotionMood = companionEmotionMood,
                                    onUpdateCustomPersonalityPrompt = { viewModel.updateCustomPersonalityPrompt(it) },
                                    onUpdateProactiveCompanionEnabled = { viewModel.updateProactiveCompanionEnabled(it) },
                                    onUpdateAmbientRoomTalkEnabled = { viewModel.updateAmbientRoomTalkEnabled(it) },
                                    onTriggerProactiveCompanionNow = { viewModel.triggerProactiveCompanionNow() },
                                    currentApiKey = apiKey,
                                    maskedApiKey = viewModel.getMaskedApiKey(),
                                    isKeystoreBacked = isKeystoreBacked,
                                    isTestingConnection = isTestingConnection,
                                    connectionTestResult = connectionTestResult,
                                    availableTextModels = availableTextModels,
                                    availableVoiceModels = availableVoiceModels,
                                    onSaveOrUpdateApiKey = { viewModel.saveApiKey(it) },
                                    onDeleteApiKey = { viewModel.deleteApiKey() },
                                    onTestApiConnection = { viewModel.testGeminiConnection(it) },
                                    onSyncModelsFromApi = { viewModel.syncSupportedModelsFromApi(it) },
                                    onSelectTextModel = { viewModel.updateTextModel(it) },
                                    onSelectVoiceModel = { viewModel.updateVoiceModel(it) },
                                    onSelectScreenOperatorModel = { viewModel.updateScreenOperatorModel(it) },
                                    onSelectRouterModel = { viewModel.updateRouterModel(it) },
                                    onOpenFullApiVault = { activeScreen = "api_settings" },
                                    onNavigateToMemory = { activeScreen = "memory" },
                                    onNavigateToMemoryReview = { activeScreen = "memory_review" },
                                    onNavigateToControls = { activeScreen = "controls" },
                                    onNavigateToWorkflow = { activeScreen = "workflow" },
                                    onNavigateToNotifications = { activeScreen = "notifications" }
                                )
                            }

                            "vision" -> VisionScreen(
                                onBack = { activeScreen = "home" },
                                onAnalyzeImage = { b64, mime, prompt -> viewModel.analyzeVisionImage(b64, mime, prompt) },
                                onSpeakText = { viewModel.speakText(it) },
                                isAnalyzing = isVisionAnalyzing,
                                analysisResult = visionAnalysisResult,
                                errorMessage = visionError
                            )

                            "document" -> DocumentAssistantScreen(
                                onBack = { activeScreen = "home" },
                                onAnalyzeDocument = { text, name, prompt -> viewModel.analyzeDocument(text, name, prompt) },
                                onSpeakText = { viewModel.speakText(it) },
                                isAnalyzing = isDocumentAnalyzing,
                                analysisResult = documentAnalysisResult,
                                errorMessage = documentError
                            )

                            "workflow" -> WorkflowScreen(
                                onBack = { activeScreen = "home" },
                                workflows = allWorkflows,
                                executionLogs = workflowExecutionLogs,
                                onSaveWorkflow = { viewModel.saveWorkflow(it) },
                                onDeleteWorkflow = { viewModel.deleteWorkflow(it) },
                                onToggleWorkflow = { wf, en -> viewModel.toggleWorkflow(wf, en) },
                                onRunWorkflow = { viewModel.runWorkflow(it) }
                            )

                            "notifications" -> NotificationAssistantScreen(
                                onBack = { activeScreen = "home" },
                                notifications = notificationLogs,
                                isPermissionGranted = isNotificationAccessGranted,
                                onOpenSettings = { viewModel.openNotificationSettings() },
                                onSummarizeNotifications = { viewModel.summarizeNotifications() },
                                onSpeakSummary = { viewModel.speakText(it) },
                                isSummarizing = isSummarizingNotifications,
                                aiSummary = notificationSummary
                            )

                            "memory_review" -> MemoryReviewScreen(
                                onBack = { activeScreen = "home" },
                                memories = allMemories,
                                onUpdateImportance = { id, imp -> viewModel.updateMemoryImportance(id, imp) },
                                onDeleteMemory = { viewModel.deleteMemory(it) },
                                onPurgeExpired = { viewModel.purgeExpiredMemories() },
                                onExportText = { viewModel.exportMemoriesAsText(it) },
                                onExportJson = { viewModel.exportMemoriesAsJson(it) }
                            )

                            "browser" -> BrowserScreen(
                                onBack = { activeScreen = "home" },
                                onSummarizeContent = { viewModel.summarizeWebContent(it) }
                            )

                            "optical_solver" -> OpticalSolverScreen(
                                solverManager = viewModel.opticalSolverManager,
                                apiKey = apiKey,
                                modelId = settings.screenOperatorModel,
                                onSpeakSolution = { viewModel.speakText(it) },
                                onBack = { activeScreen = "home" }
                            )

                            "polyglot_diplomat" -> PolyglotDiplomatScreen(
                                diplomatManager = viewModel.polyglotDiplomatManager,
                                apiKey = apiKey,
                                routerModelId = settings.routerModel,
                                onBack = { activeScreen = "home" }
                            )

                            "charging_telemetry" -> ArcReactorChargingScreen(
                                onSpeakTelemetry = { viewModel.speakText(it) },
                                onBack = { activeScreen = "home" }
                            )

                            "dashboard_deck" -> HomeDashboardScreen(
                                settings = settings,
                                status = status,
                                audioLevel = liveAudioLevel,
                                hasApiKey = apiKey.isNotBlank(),
                                memoriesCount = allMemories.size,
                                sessionsCount = sessions.size,
                                isFlashlightOn = isFlashlightOn,
                                isWakeWordActive = isWakeWordActive,
                                onToggleWakeWord = { viewModel.toggleWakeWord(it) },
                                onStatusPreviewSelect = { viewModel.setPreviewStatus(it) },
                                onNavigateToVoice = { activeScreen = "voice" },
                                onNavigateToChat = { activeScreen = "home" },
                                onNavigateToMemory = { activeScreen = "memory" },
                                onNavigateToControls = { activeScreen = "controls" },
                                onNavigateToApiSettings = { activeScreen = "api_settings" },
                                onNavigateToGeneralSettings = { activeScreen = "settings" },
                                onNavigateToVision = { activeScreen = "vision" },
                                onNavigateToDocument = { activeScreen = "document" },
                                onNavigateToWorkflow = { activeScreen = "workflow" },
                                onNavigateToNotifications = { activeScreen = "notifications" },
                                onNavigateToMemoryReview = { activeScreen = "memory_review" },
                                onNavigateToBrowser = { activeScreen = "browser" },
                                onNavigateToAgentHub = { activeScreen = "agent_hub" },
                                onNavigateToOpticalSolver = { activeScreen = "optical_solver" },
                                onNavigateToPolyglotDiplomat = { activeScreen = "polyglot_diplomat" },
                                onNavigateToChargingTelemetry = { activeScreen = "charging_telemetry" },
                                agenticStepLogs = agenticStepLogs,
                                isAgenticLoopRunning = isAgenticLoopRunning,
                                detectedBadges = detectedBadges,
                                overlayHudState = overlayHudState,
                                activeReplyables = activeReplyables,
                                activeFormSession = activeFormSession,
                                lastScratchpadResult = lastScratchpadResult,
                                isRecordingDemonstration = isRecordingDemonstration,
                                recordedDemonstrationSteps = recordedDemonstrationSteps,
                                pendingHighRiskGate = pendingHighRiskGate,
                                onRunAutonomousGoal = { viewModel.runAutonomousAgentGoal(it) },
                                onScanSetOfMarkBadges = { viewModel.scanSetOfMarkBadges() },
                                onTapSetOfMarkBadge = { viewModel.executeVisionSetOfMarkTap(badgeId = it) },
                                onToggleOverlayHud = { viewModel.toggleFloatingCyberHud(it) },
                                onSendInlineReply = { sender, msg -> viewModel.sendBackgroundNotificationReply(sender, msg) },
                                onAutoFillScreenForm = { viewModel.scanAndAutoFillScreenForm() },
                                onRunCrossAppBridge = { lang, app -> viewModel.executeCrossAppDataBridge(lang, app) },
                                onStartTeachableRecording = { viewModel.startTeachableSkillRecording() },
                                onStopAndSaveTeachableSkill = { viewModel.stopAndSaveTeachableSkill(it) },
                                onSpeakerCall = { viewModel.triggerHandsFreeSpeakerCall(it) },
                                onSetTimerSeconds = { viewModel.triggerVoiceAlarmOrTimer(isTimer = true, hourOrSeconds = it) },
                                onZeroClickTeleport = { type, q -> viewModel.executeZeroClickTeleport(type, q) },
                                onConfirmHighRiskGate = { viewModel.confirmHighRiskAgentAction() },
                                onCancelHighRiskGate = { viewModel.cancelHighRiskAgentAction() },
                                onVoiceUndoRollback = { viewModel.triggerAgenticVoiceUndo() }
                            )
                        }

                        // 1-Tap Embedded Linux Terminal Modal
                        if (showTerminalDialog) {
                            LocalTerminalDialog(
                                onDismiss = { showTerminalDialog = false }
                            )
                        }

                        // 1-Tap Google Antigravity PKCE OAuth & Account Modal
                        if (showAntigravityLoginDialog) {
                            AntigravityLoginDialog(
                                isLoggedIn = isAntigravityLoggedIn,
                                userEmail = antigravityUserEmail,
                                tier = antigravityTier,
                                isServerListening = isOAuthServerActive,
                                authUrl = antigravityAuthUrl,
                                onStartLogin = { viewModel.startAntigravitySignIn() },
                                onSubmitManualCode = { code -> viewModel.handleAntigravityCode(code) },
                                onLogout = { viewModel.logoutAntigravity() },
                                onDismiss = { showAntigravityLoginDialog = false }
                            )
                        }

                        // [ + ] Tools & Multimodal Attachments Bottom Sheet
                        if (showToolsSheet) {
                            ModalBottomSheet(
                                onDismissRequest = { showToolsSheet = false },
                                containerColor = Color(0xFF111827),
                                contentColor = Color.White
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        text = "JARVIS MULTIMODAL TOOLS & MACROS",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF00E5FF)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    ToolSheetItem("☁️ Google Antigravity OAuth", "Sign in for unmetered Gemini 2.5 Thinking & Claude 3.5 Sonnet") {
                                        showToolsSheet = false
                                        showAntigravityLoginDialog = true
                                    }
                                    ToolSheetItem("📸 Camera & Vision Analysis", "Inspect photos, UI screens, or hardware") {
                                        showToolsSheet = false
                                        activeScreen = "vision"
                                    }
                                    ToolSheetItem("📄 Document & PDF Reader", "Summarize and query local files") {
                                        showToolsSheet = false
                                        activeScreen = "document"
                                    }
                                    ToolSheetItem("🧮 Optical Math & Code Solver", "Solve equations and circuits via camera") {
                                        showToolsSheet = false
                                        activeScreen = "optical_solver"
                                    }
                                    ToolSheetItem("🌐 Polyglot Live Translator", "Real-time bilingual voice interpreter") {
                                        showToolsSheet = false
                                        activeScreen = "polyglot_diplomat"
                                    }
                                    ToolSheetItem("🔋 Arc Reactor Telemetry", "System battery & thermal diagnostics") {
                                        showToolsSheet = false
                                        activeScreen = "charging_telemetry"
                                    }
                                    ToolSheetItem("⚡ Autonomous Workflows & Macros", "Configure multi-step device automation") {
                                        showToolsSheet = false
                                        activeScreen = "workflow"
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStageChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF111827),
        border = BorderStroke(1.dp, Color(0xFF1F2937)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = Color(0xFFE2E8F0),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ToolSheetItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF1F2937),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, color = Color(0xFF94A3B8), fontSize = 11.5.sp)
            }
        }
    }
}
