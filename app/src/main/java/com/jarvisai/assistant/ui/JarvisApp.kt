package com.jarvisai.assistant.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.zIndex
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.JarvisHudTheme
import com.jarvisai.assistant.agent.ActiveCodingAgent
import com.jarvisai.assistant.core.SuperJarvisOrchestrator
import com.jarvisai.assistant.ide.IdeScreen
import com.jarvisai.assistant.security.ManageSpaceActivity
import com.jarvisai.assistant.service.JarvisOverlayHudService
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.ui.bubble.FloatingBubbleService
import com.jarvisai.assistant.ui.chat.SlashCommandOverlay
import com.jarvisai.assistant.ui.claude.ClaudePendingCommand
import com.jarvisai.assistant.ui.claude.ClaudePermissionPanel
import com.jarvisai.assistant.ui.components.AgentExecutionCard
import com.jarvisai.assistant.ui.components.AntigravityLoginDialog
import com.jarvisai.assistant.ui.components.ArcReactorView
import com.jarvisai.assistant.ui.components.ChatBubbleAttachmentsSection
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.jarvisai.assistant.ui.components.FloatingSwitcherWidget
import com.jarvisai.assistant.ui.components.FullscreenImagePreviewDialog
import com.jarvisai.assistant.ui.components.JarvisNavigationDrawer
import com.jarvisai.assistant.ui.components.JarvisUnifiedCommandBar
import com.jarvisai.assistant.ui.components.JarvisUnifiedTopBar
import com.jarvisai.assistant.ui.components.LocalTerminalContent
import com.jarvisai.assistant.ui.components.LocalTerminalDialog
import com.jarvisai.assistant.ui.components.openAttachmentExternally
import com.jarvisai.assistant.ui.drawer.ArtifactsDrawerContent
import com.jarvisai.assistant.ui.drawer.GeneratedArtifact
import com.jarvisai.assistant.ui.files.FileExplorerScreen
import com.jarvisai.assistant.ui.onboarding.OnboardingWizardDialog
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
import com.jarvisai.assistant.ui.screens.PermissionsCenterScreen
import com.jarvisai.assistant.ui.screens.PhoneControlsScreen
import com.jarvisai.assistant.ui.screens.PluginsManagementScreen
import com.jarvisai.assistant.ui.screens.PolyglotDiplomatScreen
import com.jarvisai.assistant.ui.screens.VisionScreen
import com.jarvisai.assistant.ui.screens.WorkflowScreen
import com.jarvisai.assistant.ui.updater.ApkUpdateResult
import com.jarvisai.assistant.ui.updater.AppReleaseInfo
import com.jarvisai.assistant.ui.updater.AppUpdateManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.material.icons.rounded.HeadsetMic
import androidx.core.content.ContextCompat
import com.jarvisai.assistant.ui.voice.AgyVoiceCallPill
import com.jarvisai.assistant.ui.voice.AgyVoiceCallScreen
import com.jarvisai.assistant.ui.voice.AgyVoicePickerDialog
import com.jarvisai.assistant.ui.voice.AgyVoiceTranscriptSheet
import com.jarvisai.assistant.ui.voice.VoiceCallMarker
import com.jarvisai.assistant.viewmodel.JarvisViewModel
import com.jarvisai.assistant.voice.agy.AgyVoiceCall
import com.jarvisai.assistant.voice.agy.AgyVoiceChats
import com.jarvisai.assistant.voice.agy.AgyVoicePreferences
import com.jarvisai.assistant.voice.agy.VoiceCallLauncher
import com.jarvisai.assistant.voice.agy.VoiceChatRecord
import com.jarvisai.assistant.voice.agy.VoiceTurn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

enum class JarvisMasterTab(
    val title: String,
    val icon: ImageVector,
    val destination: JarvisDestination
) {
    VOICE_HUB("Agentra Voice", Icons.Default.Mic, JarvisDestination.VOICE),
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
    FILES("files", "Files", Icons.Default.Folder),
    ADB_CONNECT("adb_connect", "ADB", Icons.Default.DeveloperMode),
    PLUGINS("plugins", "Plugins", Icons.Default.Extension),
    NOTES("notes", "Notes", Icons.Default.Description),
    RUN_MANAGER("run_manager", "Runner", Icons.Default.AutoMode),
    MINI_APPS("mini_apps", "Mini-Apps", Icons.Default.Apps)
}

private sealed interface ChatTimelineItem {
    val sortTimestamp: Long
    val orderHint: Int
    val timelineKey: String

    data class PersistentMessage(
        val message: com.jarvisai.assistant.data.local.ChatMessageEntity
    ) : ChatTimelineItem {
        override val sortTimestamp: Long get() = message.timestamp
        override val orderHint: Int get() = 0
        override val timelineKey: String get() = "msg_${message.id}"
    }

    data class LiveTranscript(
        val transcript: com.jarvisai.assistant.service.LiveTranscriptItem
    ) : ChatTimelineItem {
        override val sortTimestamp: Long get() = transcript.timestamp
        override val orderHint: Int get() = 1
        override val timelineKey: String get() = "live_${transcript.id}"
    }

    data class SubagentTask(
        val task: com.jarvisai.assistant.agent.AgentTask
    ) : ChatTimelineItem {
        override val sortTimestamp: Long get() = task.createdAtMs
        override val orderHint: Int get() = 2
        override val timelineKey: String get() = "task_${task.id}"
    }

    data class VoiceCall(
        val record: VoiceChatRecord?,
        val live: Boolean,
        override val sortTimestamp: Long,
        override val orderHint: Int = -1
    ) : ChatTimelineItem {
        override val timelineKey: String =
            if (live) "agy_voice_call_live_$sortTimestamp" else "agy_voice_call_${record?.startedAtMs ?: sortTimestamp}_$sortTimestamp"
    }
}

/**
 * Unified AntiGem-Grade Master Architecture for Jarvis AI Ultra.
 * Unites TopBar, Navigation Drawer, File Explorer, Claude Permission Approvals,
 * Slash Command Autocomplete, Artifacts Right-Side Drawer, Floating ChatHead Bubble,
 * In-App GitHub Updater, Onboarding Wizard, and Unified Floating Command Bar.
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
    val pendingOrchestratorApproval by orchestrator.pendingApprovalFlow.collectAsStateWithLifecycle()
    val pendingAgentInteractiveApproval by orchestrator.agentOrchestrator.pendingInteractiveApproval.collectAsStateWithLifecycle()

    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val status by viewModel.assistantStatus.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKeyFlow.collectAsStateWithLifecycle()
    val isKeystoreBacked by viewModel.isUsingKeystoreEncryption.collectAsStateWithLifecycle()

    val isAntigravityLoggedIn by viewModel.isAntigravityLoggedIn.collectAsStateWithLifecycle()
    val antigravityUserEmail by viewModel.antigravityUserEmail.collectAsStateWithLifecycle()
    val antigravityTier by viewModel.antigravityTier.collectAsStateWithLifecycle()
    val antigravityAuthUrl by viewModel.antigravityAuthUrl.collectAsStateWithLifecycle()
    val isOAuthServerActive by viewModel.isOAuthServerActive.collectAsStateWithLifecycle()
    val antigravityModels by viewModel.antigravityModels.collectAsStateWithLifecycle()
    val isRefreshingAntigravityModels by viewModel.isRefreshingAntigravityModels.collectAsStateWithLifecycle()
    val localServerStatus by com.jarvisai.assistant.terminal.LocalServerManager.status.collectAsStateWithLifecycle()
    val agyDiagnostics by com.jarvisai.assistant.terminal.LocalServerManager.agyDiagnostics.collectAsStateWithLifecycle()
    var showLocalServerLogsDialog by rememberSaveable { mutableStateOf(false) }

    val agyVoiceCallState by AgyVoiceCall.state.collectAsStateWithLifecycle()
    val agyVoiceChatsMap by AgyVoiceChats.chats.collectAsStateWithLifecycle()
    val isAgyVoiceAgentWorking by viewModel.isAgyVoiceAgentWorking.collectAsStateWithLifecycle()
    val currentExternalCascadeId by viewModel.currentAgyVoiceCascadeId.collectAsStateWithLifecycle()
    val voiceCallPendingTrigger by VoiceCallLauncher.pending.collectAsStateWithLifecycle()

    var showAgyVoicePicker by rememberSaveable { mutableStateOf(false) }
    var startAgyCallAfterPerm by remember { mutableStateOf(false) }
    var openAgyTranscriptRecord by remember { mutableStateOf<VoiceChatRecord?>(null) }

    val startAgyVoiceCallNow: () -> Unit = {
        AgyVoicePreferences.init(context)
        val title = viewModel.chatSessions.value.firstOrNull {
            it.id == viewModel.currentSessionId.value
        }?.title?.ifBlank { "Voice call" } ?: "Voice call"
        AgyVoiceCall.start(context, title) {
            viewModel.prepareAntigravityVoiceCall()
        }
    }

    val agyMicPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && startAgyCallAfterPerm) {
            startAgyCallAfterPerm = false
            startAgyVoiceCallNow()
        } else if (!granted && startAgyCallAfterPerm) {
            startAgyCallAfterPerm = false
            Toast.makeText(context, "Microphone permission is required for voice calls.", Toast.LENGTH_SHORT).show()
        }
    }

    val triggerAgyVoiceCallFlow: () -> Unit = {
        AgyVoicePreferences.init(context)
        when {
            agyVoiceCallState.active -> AgyVoiceCall.setMinimized(false)
            !AgyVoicePreferences.picked.value -> showAgyVoicePicker = true
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> {
                startAgyCallAfterPerm = true
                agyMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> startAgyVoiceCallNow()
        }
    }

    LaunchedEffect(agyVoiceCallState.active, currentExternalCascadeId, settings.llmProvider) {
        val cid = currentExternalCascadeId?.trim().orEmpty()
        if (cid.isNotBlank() && settings.llmProvider == com.jarvisai.assistant.model.LLMProvider.ANTIGRAVITY) {
            AgyVoiceChats.refresh(cid, context)
        }
    }

    LaunchedEffect(Unit) {
        AgyVoiceCall.conversationMoved.collect { newCid ->
            viewModel.selectSessionByExternalCascadeId(newCid)
            AgyVoiceChats.refresh(newCid, context)
        }
    }

    LaunchedEffect(voiceCallPendingTrigger) {
        if (voiceCallPendingTrigger > 0L && VoiceCallLauncher.consumePending()) {
            triggerAgyVoiceCallFlow()
        }
    }

    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsStateWithLifecycle()
    val ttsErrorMessage by viewModel.ttsErrorMessage.collectAsStateWithLifecycle()
    val memorySavedNotification by viewModel.memorySavedNotification.collectAsStateWithLifecycle()

    val sessions by viewModel.chatSessions.collectAsStateWithLifecycle()
    val archivedSessions by viewModel.archivedChatSessions.collectAsStateWithLifecycle()
    val activeChatFolder by viewModel.activeChatFolder.collectAsStateWithLifecycle()
    val customChatFolders by viewModel.customChatFolders.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val currentMessages by viewModel.currentMessages.collectAsStateWithLifecycle()
    val streamingPartial by viewModel.streamingPartialResponse.collectAsStateWithLifecycle()
    val currentTurnThought by viewModel.currentTurnThought.collectAsStateWithLifecycle()
    val currentTurnToolCalls by viewModel.currentTurnToolCalls.collectAsStateWithLifecycle()
    val isChatLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()

    val isLiveVoiceActive by viewModel.isLiveVoiceActive.collectAsStateWithLifecycle()
    val isMicMuted by viewModel.isMicMuted.collectAsStateWithLifecycle()
    val liveUiStatus by viewModel.liveUiStatus.collectAsStateWithLifecycle()
    val liveAudioLevel by viewModel.liveAudioLevel.collectAsStateWithLifecycle()
    val liveConnectionLabel by viewModel.liveConnectionLabel.collectAsStateWithLifecycle()
    val liveVoiceError by viewModel.liveVoiceError.collectAsStateWithLifecycle()
    val liveTranscripts by viewModel.liveTranscripts.collectAsStateWithLifecycle()
    val archivedLiveTranscriptIds by viewModel.archivedLiveTranscriptIds.collectAsStateWithLifecycle()
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
    val isWorkflowRunning by viewModel.isWorkflowRunning.collectAsStateWithLifecycle()
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
    var previousActiveScreen by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsInitialSection by remember { mutableStateOf(com.jarvisai.assistant.ui.settings.SettingsSection.MAIN) }
    var localTerminalAgentOverride by rememberSaveable { mutableStateOf<String?>(null) }
    var activeWorkspaceName by rememberSaveable { mutableStateOf<String?>(null) }
    var activeModelName by rememberSaveable {
        mutableStateOf(
            resolveTopBarModelDisplay(
                modelId = settings.textModel,
                provider = settings.llmProvider,
                localAgentOverride = localTerminalAgentOverride,
                antigravityModels = antigravityModels
            )
        )
    }
    var promptInput by rememberSaveable { mutableStateOf("") }
    val dictationController = remember(context) {
        com.jarvisai.assistant.voice.VoiceDictationController(
            context = context,
            onTranscriptUpdate = { transcript -> promptInput = transcript },
            onErrorMessage = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
        )
    }
    val isDictating by dictationController.isDictating.collectAsStateWithLifecycle()
    val dictationAudioLevel by dictationController.dictationAudioLevel.collectAsStateWithLifecycle()
    DisposableEffect(dictationController) {
        onDispose { dictationController.release() }
    }
    val dictationMicPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            dictationController.startDictation(promptInput)
        } else {
            Toast.makeText(context, "Microphone permission required for voice typing.", Toast.LENGTH_SHORT).show()
        }
    }
    var showTerminalDialog by rememberSaveable { mutableStateOf(false) }
    var showRootfsInstallerDialog by rememberSaveable { mutableStateOf(false) }
    var isSetupBannerDismissed by rememberSaveable { mutableStateOf(false) }
    var showThinkingSelectorSheet by rememberSaveable { mutableStateOf(false) }
    val currentThinkingPreference = remember(settings.thinkingLevel) {
        com.jarvisai.assistant.model.ThinkingPreference(level = settings.thinkingLevel)
    }
    val activeModelSupportsThinking = remember(settings.textModel, antigravityModels) {
        val rawBare = settings.textModel.removePrefix("agy:").trim()
        val bare = rawBare.lowercase()
        val matchedAgy = antigravityModels.firstOrNull {
            it.id.equals(rawBare, ignoreCase = true) ||
                it.thinkingVariantIds.values.any { v -> v.equals(rawBare, ignoreCase = true) }
        }
        when {
            matchedAgy != null -> matchedAgy.supportsThinking
            bare.contains("-lite") || bare.contains("oss") -> false
            else -> true
        }
    }
    var showAntigravityLoginDialog by rememberSaveable { mutableStateOf(false) }
    var showArtifactsDrawer by rememberSaveable { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showTermuxBridgeDialog by rememberSaveable { mutableStateOf(false) }
    var pendingAttachments by remember { mutableStateOf<List<com.jarvisai.assistant.model.ChatAttachment>>(emptyList()) }
    var selectedIdeFile by remember { mutableStateOf<File?>(null) }
    val pendingIdeOpenPath by com.jarvisai.assistant.terminal.WorkspaceSyncManager.pendingOpenFile.collectAsStateWithLifecycle()
    val syncedWorkspacePath by com.jarvisai.assistant.terminal.WorkspaceSyncManager.currentWorkspacePath.collectAsStateWithLifecycle()
    val runningSessionIds by viewModel.runningSessionIds.collectAsStateWithLifecycle()
    var showWorkspaceSelectorModal by remember { mutableStateOf(false) }
    var showProjectFolderBrowser by remember { mutableStateOf(false) }
    var workspacesRefreshKey by remember { mutableIntStateOf(0) }
    var inspectingRawPayload by remember { mutableStateOf<String?>(null) }
    var inspectingTokenUsage by remember { mutableStateOf<com.jarvisai.assistant.model.TokenUsage?>(null) }

    fun appendPickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val added = uris.mapNotNull { uri ->
            com.jarvisai.assistant.data.media.AttachmentProcessor.inspectUri(context, uri)
        }
        if (added.isNotEmpty()) {
            pendingAttachments = (pendingAttachments + added).distinctBy { it.uriString }.take(8)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris -> appendPickedUris(uris) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(3)
    ) { uris -> appendPickedUris(uris) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> appendPickedUris(uris) }

    var termuxDiagnosticsRefresh by remember { mutableIntStateOf(0) }
    var lastTermuxPermLaunchMs by remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                termuxDiagnosticsRefresh++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val termuxPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        termuxDiagnosticsRefresh++
        if (granted) {
            Toast.makeText(
                context,
                "✅ Termux RUN_COMMAND permission granted!",
                Toast.LENGTH_LONG
            ).show()
        } else {
            val bridge = com.jarvisai.assistant.termux.JarvisTermuxBridge(context)
            val diag = bridge.getDiagnostics()
            if (!diag.isTermuxInstalled) {
                Toast.makeText(
                    context,
                    "Pehle Official F-Droid Termux app install karein (ya 1-Tap Android 16 Bridge use karein).",
                    Toast.LENGTH_LONG
                ).show()
                showTermuxBridgeDialog = true
            } else {
                copyTextWithToast(
                    context,
                    "Android 16 Termux Bridge Command",
                    com.jarvisai.assistant.termux.JarvisTermuxBridge.buildAndroid16BridgeCommand(context)
                )
                Toast.makeText(
                    context,
                    "Android 16 Settings me ye toggle hidden rehta hai — Bridge command copy ho gaya! Termux me Paste karke Enter dabayein.",
                    Toast.LENGTH_LONG
                ).show()
                runCatching {
                    val launch = context.packageManager.getLaunchIntentForPackage(
                        com.jarvisai.assistant.termux.JarvisTermuxBridge.TERMUX_PACKAGE
                    )
                    if (launch != null) context.startActivity(launch)
                }
            }
        }
    }

    LaunchedEffect(
        isAntigravityLoggedIn,
        antigravityModels,
        settings.llmProvider,
        settings.textModel
    ) {
        if (
            isAntigravityLoggedIn &&
            antigravityModels.isNotEmpty() &&
            settings.llmProvider == com.jarvisai.assistant.model.LLMProvider.ANTIGRAVITY &&
            settings.textModel.startsWith("agy:", ignoreCase = true)
        ) {
            val bareId = settings.textModel.removePrefix("agy:")
            val isSupported = antigravityModels.any { it.id.equals(bareId, ignoreCase = true) }
            if (!isSupported) {
                val fallback = antigravityModels.firstOrNull { it.isRecommended } ?: antigravityModels.first()
                viewModel.updateTextModel("agy:${fallback.id}")
            }
        }
    }

    LaunchedEffect(settings.textModel, settings.llmProvider, localTerminalAgentOverride, antigravityModels) {
        activeModelName = resolveTopBarModelDisplay(
            modelId = settings.textModel,
            provider = settings.llmProvider,
            localAgentOverride = localTerminalAgentOverride,
            antigravityModels = antigravityModels
        )
    }

    LaunchedEffect(Unit) {
        com.jarvisai.assistant.data.pc.PcConnect.init(context)
        com.jarvisai.assistant.browser.BrowserSessionManager.instance.init(context)
    }

    val rootfsInstallProgress by LocalEnvironmentManager.installProgress.collectAsStateWithLifecycle()
    val isLinuxRootfsInstalled = remember(showRootfsInstallerDialog, rootfsInstallProgress) {
        LocalEnvironmentManager.isEnvironmentInstalled(context)
    }

    // Persistent composition flags so once opened, heavy workspaces (IDE, Terminal, Files, Browser)
    // stay alive in composition via graphicsLayer without losing scroll/cursor/PTY state.
    var hasEverOpenedIde by rememberSaveable { mutableStateOf(false) }
    var hasEverOpenedTerminal by rememberSaveable { mutableStateOf(false) }
    var hasEverOpenedFiles by rememberSaveable { mutableStateOf(false) }
    var hasEverOpenedBrowser by rememberSaveable { mutableStateOf(false) }

    val agentBrowserBanner by com.jarvisai.assistant.browser.BrowserSessionManager.instance.agentBrowserBanner.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        com.jarvisai.assistant.browser.BrowserSessionManager.instance.openInAppBrowserRequests.collect { req ->
            hasEverOpenedBrowser = true
            if (req.switchToBrowser && activeScreen != "browser") {
                previousActiveScreen = activeScreen
                activeScreen = "browser"
            }
        }
    }

    LaunchedEffect(activeScreen) {
        when (activeScreen) {
            "ide" -> hasEverOpenedIde = true
            "terminal" -> hasEverOpenedTerminal = true
            "files" -> hasEverOpenedFiles = true
            "browser" -> hasEverOpenedBrowser = true
        }
    }

    LaunchedEffect(pendingIdeOpenPath) {
        val path = pendingIdeOpenPath ?: return@LaunchedEffect
        val targetFile = File(path)
        if (targetFile.exists() && targetFile.isFile) {
            hasEverOpenedIde = true
            selectedIdeFile = targetFile
            if (activeScreen != "ide") {
                previousActiveScreen = activeScreen
                activeScreen = "ide"
            }
        } else {
            com.jarvisai.assistant.terminal.WorkspaceSyncManager.clearPendingOpenFile()
        }
    }

    // Onboarding state persisted in SharedPreferences
    val onboardingPrefs = remember {
        context.getSharedPreferences("jarvis_onboarding_prefs", Context.MODE_PRIVATE)
    }
    var showOnboardingWizard by remember {
        mutableStateOf(false)
    }
    val authPrefs = remember {
        com.jarvisai.assistant.data.preferences.AuthPreferences.getInstance(context)
    }
    var showTerminalLauncherOnboarding by remember {
        mutableStateOf(
            !com.jarvisai.assistant.data.preferences.TerminalLauncherManager.isPromptShown(context) &&
                !authPrefs.hasSeenTerminalLauncherOnboardingSync()
        )
    }

    // Claude Interactive Permission Approval State
    var autoApproveSessionBash by rememberSaveable { mutableStateOf(false) }
    var manualPendingClaudeCmd by remember { mutableStateOf<ClaudePendingCommand?>(null) }
    val activePendingClaudeCommand: ClaudePendingCommand? = remember(
        pendingOrchestratorApproval,
        pendingAgentInteractiveApproval,
        manualPendingClaudeCmd
    ) {
        manualPendingClaudeCmd
            ?: pendingAgentInteractiveApproval
            ?: pendingOrchestratorApproval?.let { req ->
                ClaudePendingCommand(
                    id = req.approvalId,
                    command = "${req.toolName}: ${req.toolDetails}",
                    workingDir = LocalEnvironmentManager.getHomeDir(context).absolutePath,
                    riskLevel = if (req.toolDetails.contains("rm ") || req.toolDetails.contains("sudo")) "HIGH" else "MEDIUM"
                )
            }
    }

    // GitHub App Self-Updater State
    val updateManager = remember { AppUpdateManager() }
    var latestReleaseInfo by remember { mutableStateOf<AppReleaseInfo?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var isCheckingForUpdates by remember { mutableStateOf(false) }
    var updateDownloadProgress by remember { mutableIntStateOf(0) }
    var updateDownloadedMbText by remember { mutableStateOf("") }
    var updateErrorMessage by remember { mutableStateOf<String?>(null) }
    var pendingInstallApkFile by remember { mutableStateOf<File?>(null) }

    // Auto-resume APK installation as soon as user returns from "Allow from this source" Settings
    val updaterLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(updaterLifecycleOwner, pendingInstallApkFile) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val queuedApk = pendingInstallApkFile
                if (queuedApk != null && queuedApk.exists()) {
                    val canInstall = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.packageManager.canRequestPackageInstalls()
                    } else true
                    if (canInstall) {
                        pendingInstallApkFile = null
                        runCatching {
                            updateManager.installApk(context, queuedApk)
                            latestReleaseInfo = null
                            updateErrorMessage = null
                        }.onFailure { err ->
                            updateErrorMessage = err.message ?: "Could not launch package installer."
                        }
                    }
                }
            }
        }
        updaterLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { updaterLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto-check for updates on startup
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val res = updateManager.checkForUpdates()
            if (res.isSuccess) {
                val info = res.getOrNull()
                if (info != null && info.isNewer && info.downloadUrl.isNotBlank()) {
                    updateErrorMessage = null
                    latestReleaseInfo = info
                }
            }
        }
    }

    // Extract code blocks and markdown snippets from current conversation as GeneratedArtifact items
    val generatedArtifacts: List<GeneratedArtifact> = remember(currentMessages) {
        val list = mutableListOf<GeneratedArtifact>()
        val codeBlockRegex = Regex("```([a-zA-Z0-9_+-]*)\\n([\\s\\S]*?)```")
        currentMessages.filter { !it.role.equals("user", ignoreCase = true) }.forEach { msg ->
            val matches = codeBlockRegex.findAll(msg.content).toList()
            matches.forEachIndexed { idx, match ->
                val lang = match.groupValues[1].ifBlank { "code" }
                val code = match.groupValues[2].trim()
                if (code.isNotBlank()) {
                    list.add(
                        GeneratedArtifact(
                            id = "artifact_${msg.id}_$idx",
                            title = "Snippet #${idx + 1} (${lang.uppercase()})",
                            fileType = lang,
                            content = code,
                            timestamp = msg.timestamp
                        )
                    )
                }
            }
        }
        list.reversed()
    }

    val isAntigravityActive = settings.llmProvider == com.jarvisai.assistant.model.LLMProvider.ANTIGRAVITY
    var showAntigravityInboxSheet by rememberSaveable { mutableStateOf(false) }
    var showAntigravityRulesSheet by rememberSaveable { mutableStateOf(false) }
    var antigravityRulesRevision by remember { mutableIntStateOf(0) }
    var approvedPlanArtifactIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var changesRequestedArtifactIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val activeWorkspacePath = remember(activeWorkspaceName, syncedWorkspacePath) {
        syncedWorkspacePath
            ?.takeIf { it.isNotBlank() }
            ?: com.jarvisai.assistant.ide.ActiveCodeEditorRegistry.currentProjectPath
                ?.takeIf { it.isNotBlank() }
            ?: LocalEnvironmentManager.getHomeDir(context).absolutePath
    }

    val structuredArtifactsByMessage = remember(
        currentMessages,
        approvedPlanArtifactIds,
        changesRequestedArtifactIds
    ) {
        currentMessages.associate { msg ->
            msg.id to com.jarvisai.assistant.antigravity.AntigravityWorkspaceEngine.extractArtifactsFromMessage(
                message = msg,
                approvedIds = approvedPlanArtifactIds,
                changesRequestedIds = changesRequestedArtifactIds
            )
        }
    }

    val allStructuredArtifacts = remember(structuredArtifactsByMessage) {
        structuredArtifactsByMessage.values.flatten().reversed()
    }

    val pendingPlanArtifacts = remember(allStructuredArtifacts) {
        allStructuredArtifacts.filter {
            it.type == com.jarvisai.assistant.antigravity.AntigravityArtifactType.IMPLEMENTATION_PLAN &&
                it.status == com.jarvisai.assistant.antigravity.AntigravityArtifactStatus.PENDING_REVIEW
        }
    }

    val antigravityRulesAndSkillsCount = remember(activeWorkspacePath, antigravityRulesRevision) {
        val g = com.jarvisai.assistant.antigravity.AntigravityWorkspaceEngine.loadGlobalRules(context).isNotBlank()
        val w = com.jarvisai.assistant.antigravity.AntigravityWorkspaceEngine.loadWorkspaceRules(context, activeWorkspacePath).isNotBlank()
        val skills = com.jarvisai.assistant.antigravity.AntigravityWorkspaceEngine.discoverWorkspaceSkills(context, activeWorkspacePath).size
        (if (g) 1 else 0) + (if (w) 1 else 0) + skills
    }

    val mentionSuggestions = remember(promptInput, activeWorkspacePath, sessions, isAntigravityActive) {
        if (!isAntigravityActive || !promptInput.contains("@")) {
            emptyList()
        } else {
            com.jarvisai.assistant.antigravity.AntigravityWorkspaceEngine.buildMentionSuggestions(
                context = context,
                currentInput = promptInput,
                workspacePath = activeWorkspacePath,
                sessions = sessions
            )
        }
    }

    fun approveAntigravityPlan(artifact: com.jarvisai.assistant.antigravity.AntigravityArtifact) {
        approvedPlanArtifactIds = approvedPlanArtifactIds + artifact.id
        changesRequestedArtifactIds = changesRequestedArtifactIds - artifact.id
        viewModel.sendChatMessage(
            "Proceed with the approved Implementation Plan (`${artifact.title}`). Execute the steps cleanly and report a `walkthrough.md` summary when verified."
        )
    }

    fun submitAntigravityPlanComment(
        artifact: com.jarvisai.assistant.antigravity.AntigravityArtifact,
        comment: String
    ) {
        changesRequestedArtifactIds = changesRequestedArtifactIds + artifact.id
        viewModel.sendChatMessage(
            "[Inline Plan Review on `${artifact.title}`]: $comment\nPlease revise the `implementation_plan.md` artifact before proceeding."
        )
    }

    val workspaces = remember(workspacesRefreshKey, activeWorkspacePath) {
        com.jarvisai.assistant.ui.components.discoverAvailableWorkspaces(context, activeWorkspacePath)
    }

    fun toggleFloatingChatHeadBubble() {
        if (Settings.canDrawOverlays(context)) {
            val active = FloatingBubbleService.toggle(context)
            Toast.makeText(
                context,
                if (active) "Floating ChatHead Bubble enabled" else "Floating ChatHead Bubble stopped",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
        }
    }

    fun triggerUpdateCheck() {
        if (isCheckingForUpdates) return
        isCheckingForUpdates = true
        Toast.makeText(context, "Checking GitHub Releases for updates...", Toast.LENGTH_SHORT).show()
        coroutineScope.launch {
            val res = updateManager.checkForUpdates()
            isCheckingForUpdates = false
            if (res.isSuccess) {
                val info = res.getOrNull()
                when {
                    info == null -> {
                        Toast.makeText(
                            context,
                            "Already on latest version (${AppUpdateManager.CURRENT_VERSION_TAG})",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    !info.isNewer -> {
                        Toast.makeText(
                            context,
                            "Already on latest version (${info.currentVersion})",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    info.downloadUrl.isBlank() -> {
                        updateErrorMessage = "Release ${info.newVersion} mil gaya hai, par APK asset abhi upload/build ho raha hai. 1-2 minute baad dubara check karein ya Browser mein kholein."
                        latestReleaseInfo = info
                    }
                    else -> {
                        updateErrorMessage = null
                        latestReleaseInfo = info
                    }
                }
            } else {
                val errMsg = res.exceptionOrNull()?.message ?: "Network error"
                Toast.makeText(
                    context,
                    "Update check failed: $errMsg",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val appFocusCoordinator = com.jarvisai.assistant.ui.components.LocalAppFocusCoordinator.current
    var previousFocusMode by remember { mutableStateOf(com.jarvisai.assistant.ui.components.AppViewMode.CHAT) }
    LaunchedEffect(activeScreen, showTerminalDialog) {
        val nextMode = when {
            showTerminalDialog -> com.jarvisai.assistant.ui.components.AppViewMode.TERMINAL
            activeScreen == "ide" -> com.jarvisai.assistant.ui.components.AppViewMode.IDE
            activeScreen == "browser" -> com.jarvisai.assistant.ui.components.AppViewMode.BROWSER
            else -> com.jarvisai.assistant.ui.components.AppViewMode.CHAT
        }
        if (nextMode != previousFocusMode) {
            appFocusCoordinator.onScreenLeaving(previousFocusMode, isKeyboardOpen = false)
            appFocusCoordinator.onScreenEntering(nextMode, isKeyboardOpen = nextMode == com.jarvisai.assistant.ui.components.AppViewMode.TERMINAL)
            previousFocusMode = nextMode
        }
    }

    if (activeScreen != "home") {
        BackHandler {
            activeScreen = "home"
        }
    }

    JarvisHudTheme(themeMode = settings.themeMode) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = activeScreen == "home",
            drawerContent = {
                JarvisNavigationDrawer(
                    sessions = sessions,
                    archivedSessions = archivedSessions,
                    currentSessionId = currentSessionId,
                    runningSessionIds = runningSessionIds,
                    activeFolder = activeChatFolder,
                    folders = customChatFolders,
                    workspaces = workspaces,
                    isAntigravityLoggedIn = isAntigravityLoggedIn,
                    antigravityUserEmail = antigravityUserEmail,
                    onSessionClick = { id ->
                        viewModel.selectChatSession(id)
                        activeScreen = "home"
                    },
                    onDeleteSessionClick = { id ->
                        viewModel.deleteChatSession(id)
                    },
                    onRenameSession = { id, newTitle ->
                        viewModel.renameChatSession(id, newTitle)
                    },
                    onMoveSessionToFolder = { id, targetFolder ->
                        viewModel.moveSessionToFolder(id, targetFolder)
                    },
                    onTogglePinSession = { id, isPinned ->
                        viewModel.togglePinSession(id, isPinned)
                    },
                    onCopySessionId = { id ->
                        viewModel.copyConversationId(id)
                    },
                    onForkSession = { id ->
                        viewModel.forkChatSession(id) {
                            activeScreen = "home"
                        }
                    },
                    onExportSessionMarkdown = { id ->
                        viewModel.exportConversationMarkdown(id)
                    },
                    onExportSessionHtml = { id ->
                        viewModel.exportConversationHtml(id)
                    },
                    onShareSession = { id ->
                        viewModel.shareConversation(id)
                    },
                    onToggleArchiveSession = { id, isArchived ->
                        viewModel.toggleArchiveSession(id, isArchived)
                    },
                    onNewChatClick = {
                        promptInput = ""
                        pendingAttachments = emptyList()
                        viewModel.startNewChat()
                        activeScreen = "home"
                    },
                    onNewChatInFolder = { folder ->
                        promptInput = ""
                        pendingAttachments = emptyList()
                        val matchedWorkspace = workspaces.firstOrNull { it.name.equals(folder, ignoreCase = true) }
                        if (matchedWorkspace != null) {
                            activeWorkspaceName = matchedWorkspace.name
                            viewModel.selectWorkspaceDirectory(matchedWorkspace)
                        } else if (folder.equals("General", ignoreCase = true)) {
                            activeWorkspaceName = null
                            viewModel.selectWorkspaceDirectory(null)
                        } else {
                            activeWorkspaceName = null
                        }
                        viewModel.startNewChatInFolder(folder)
                        activeScreen = "home"
                    },
                    onSelectFolder = { folder ->
                        val matchedWorkspace = workspaces.firstOrNull { it.name.equals(folder, ignoreCase = true) }
                        if (matchedWorkspace != null) {
                            activeWorkspaceName = matchedWorkspace.name
                            viewModel.selectWorkspaceDirectory(matchedWorkspace)
                        } else if (folder.equals("General", ignoreCase = true)) {
                            activeWorkspaceName = null
                            viewModel.selectWorkspaceDirectory(null)
                        } else {
                            activeWorkspaceName = null
                            viewModel.selectActiveChatFolder(folder)
                        }
                    },
                    onCreateFolder = { folderName, createWorkspaceDir ->
                        promptInput = ""
                        pendingAttachments = emptyList()
                        activeWorkspaceName = if (createWorkspaceDir) folderName else null
                        viewModel.createChatFolder(
                            folderName = folderName,
                            createWorkspaceDir = createWorkspaceDir,
                            startNewChatInside = true
                        )
                        workspacesRefreshKey++
                        activeScreen = "home"
                    },
                    onRenameFolder = { oldName, newName ->
                        viewModel.renameChatFolder(oldName, newName)
                    },
                    onDeleteFolder = { folderName, deleteChats ->
                        viewModel.deleteChatFolder(folderName, deleteChats)
                    },
                    onWorkspaceClick = { dir ->
                        activeWorkspaceName = dir.name
                        viewModel.selectWorkspaceDirectory(dir)
                        workspacesRefreshKey++
                        hasEverOpenedIde = true
                        activeScreen = "ide"
                    },
                    onNavigateDestination = { dest ->
                        when (dest) {
                            "antigravity_login" -> {
                                showAntigravityLoginDialog = true
                            }
                            "artifacts" -> {
                                showArtifactsDrawer = true
                            }
                            "floating_bubble" -> {
                                toggleFloatingChatHeadBubble()
                            }
                            "onboarding" -> {
                                showOnboardingWizard = true
                            }
                            "check_updates" -> {
                                triggerUpdateCheck()
                            }
                            "manage_space" -> {
                                context.startActivity(Intent(context, ManageSpaceActivity::class.java))
                            }
                            "terminal" -> {
                                previousActiveScreen = activeScreen
                                activeScreen = "terminal"
                            }
                            "rootfs_installer" -> {
                                showRootfsInstallerDialog = true
                            }
                            "termux_setup" -> {
                                termuxDiagnosticsRefresh++
                                showTermuxBridgeDialog = true
                            }
                            "settings" -> {
                                settingsInitialSection = com.jarvisai.assistant.ui.settings.SettingsSection.MAIN
                                previousActiveScreen = activeScreen
                                activeScreen = "settings"
                            }
                            "themes" -> {
                                settingsInitialSection = com.jarvisai.assistant.ui.settings.SettingsSection.THEMES
                                previousActiveScreen = activeScreen
                                activeScreen = "settings"
                            }
                            else -> {
                                previousActiveScreen = activeScreen
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
            val topBarTitle = when (activeScreen) {
                "home" -> sessions.firstOrNull { it.id == currentSessionId }?.title?.takeIf { it.isNotBlank() } ?: "New Chat"
                "ide" -> "Sora Code IDE"
                "terminal" -> "Linux Terminal"
                "files" -> "Files"
                "browser" -> "Chrome Browser"
                "voice" -> "Live Voice"
                "memory" -> "Memory Center"
                "controls" -> "Phone Controls"
                "settings" -> "Settings"
                "jarvis_settings" -> "Agentra Voice & Memory"
                "api_settings" -> "API Vault"
                "agent_hub" -> "Multi-Agent Hub"
                "adb_connect" -> "Wireless ADB"
                "connect_pc" -> "Connect to PC"
                "permissions" -> "Permissions"
                "notes" -> "Notes & To-Do"
                "run_manager" -> "Live App Runner"
                "mini_apps" -> "Agentra Mini-Apps"
                else -> "Agentra"
            }

            Scaffold(
                containerColor = com.example.ui.theme.JarvisTheme.colors.background,
                topBar = {
                    if (activeScreen == "home") {
                        JarvisUnifiedTopBar(
                            chatTitle = topBarTitle,
                            activeChatFolder = activeChatFolder,
                            activeWorkspaceName = activeWorkspaceName,
                            customChatFolders = customChatFolders,
                            workspaces = workspaces,
                            isAgentBusy = isChatLoading || runningSessionIds.isNotEmpty() || isAgenticLoopRunning || isAutonomousTaskOrVoiceActive || subagentTasks.any { it.status.name == "RUNNING" },
                            isVoiceActive = isLiveVoiceActive,
                            onMenuClick = {
                                coroutineScope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            },
                            onNewChatClick = {
                                promptInput = ""
                                pendingAttachments = emptyList()
                                viewModel.startNewChat()
                                activeScreen = "home"
                            },
                            onSelectFolder = { folder ->
                                if (folder.equals("General", ignoreCase = true)) {
                                    activeWorkspaceName = null
                                    viewModel.selectWorkspaceDirectory(null)
                                } else {
                                    activeWorkspaceName = null
                                    viewModel.selectActiveChatFolder(folder)
                                }
                            },
                            onSelectWorkspace = { dir ->
                                activeWorkspaceName = dir.name
                                viewModel.selectWorkspaceDirectory(dir)
                                workspacesRefreshKey++
                            },
                            onOpenWorkspaceSelector = {
                                workspacesRefreshKey++
                                showWorkspaceSelectorModal = true
                            },
                            onOpenFolderManager = {
                                workspacesRefreshKey++
                                showWorkspaceSelectorModal = true
                            },
                            onTerminalClick = {
                                previousActiveScreen = activeScreen
                                activeScreen = if (activeScreen == "terminal") "home" else "terminal"
                            },
                            onIdeClick = {
                                previousActiveScreen = activeScreen
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
                            },
                            serverStatus = localServerStatus,
                            userEmail = antigravityUserEmail,
                            userTier = antigravityTier,
                            agyVersion = agyDiagnostics.agyVersion,
                            onOpenServerLogs = {
                                showLocalServerLogsDialog = true
                            },
                            onRestartServer = {
                                com.jarvisai.assistant.terminal.LocalServerManager.restartServer(context)
                            }
                        )
                    }
                },
                bottomBar = {
                    if (activeScreen == "home") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                        ) {
                            AgyVoiceCallPill(
                                agentWorking = isAgyVoiceAgentWorking && agyVoiceCallState.conversationId == currentExternalCascadeId
                            )
                            // Interactive Claude Code & Subagent Permission Panel
                            ClaudePermissionPanel(
                                pendingCommand = activePendingClaudeCommand,
                                onApprove = { cmdId, autoApprove ->
                                    if (autoApprove) autoApproveSessionBash = true
                                    val manualCmd = manualPendingClaudeCmd
                                    if (manualCmd != null && manualCmd.id == cmdId) {
                                        manualPendingClaudeCmd = null
                                        coroutineScope.launch {
                                            orchestrator.executeTerminalCommand(manualCmd.command)
                                        }
                                    } else {
                                        orchestrator.agentOrchestrator.resolvePendingApproval(true)
                                        coroutineScope.launch {
                                            orchestrator.resolveVoiceApproval("haan allow karo")
                                        }
                                    }
                                },
                                onReject = { cmdId ->
                                    if (manualPendingClaudeCmd?.id == cmdId) {
                                        manualPendingClaudeCmd = null
                                    } else {
                                        orchestrator.agentOrchestrator.resolvePendingApproval(false)
                                        coroutineScope.launch {
                                            orchestrator.resolveVoiceApproval("nahi cancel karo")
                                        }
                                    }
                                }
                            )

                            // @ Mention Context Autocomplete Overlay (Antigravity 2.0)
                            com.jarvisai.assistant.ui.antigravity.MentionAutocompleteOverlay(
                                suggestions = mentionSuggestions,
                                isVisible = isAntigravityActive && mentionSuggestions.isNotEmpty(),
                                onSelect = { suggestion ->
                                    promptInput = com.jarvisai.assistant.antigravity.AntigravityWorkspaceEngine.applyMentionSelection(
                                        currentInput = promptInput,
                                        suggestion = suggestion
                                    )
                                }
                            )

                            // Slash Command Autocomplete Overlay (Official agy CLI + Workspace Commands)
                            SlashCommandOverlay(
                                currentInput = promptInput,
                                isVisible = promptInput.startsWith("/"),
                                isAntigravityActive = isAntigravityActive,
                                onSelectCommand = { selectedCmd ->
                                    when (selectedCmd) {
                                        "/plan" -> {
                                            viewModel.updateAntigravityAgentMode(
                                                com.jarvisai.assistant.antigravity.AntigravityAgentMode.PLANNING
                                            )
                                            promptInput = "/plan "
                                        }
                                        "/fast" -> {
                                            viewModel.updateAntigravityAgentMode(
                                                com.jarvisai.assistant.antigravity.AntigravityAgentMode.FAST
                                            )
                                            promptInput = ""
                                            Toast.makeText(context, "Switched to Antigravity Fast Mode", Toast.LENGTH_SHORT).show()
                                        }
                                        "/browser" -> {
                                            promptInput = "/browser "
                                        }
                                        "/resume" -> {
                                            promptInput = ""
                                            showAntigravityInboxSheet = true
                                        }
                                        "/rewind" -> {
                                            promptInput = ""
                                            val rewound = viewModel.rewindLastTurn()
                                            Toast.makeText(
                                                context,
                                                if (rewound) "Rewound last turn & reset context" else "Nothing to rewind",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        "/workflow" -> {
                                            promptInput = ""
                                            showAntigravityRulesSheet = true
                                        }
                                        "/antigravity-guide" -> {
                                            promptInput = ""
                                            viewModel.sendChatMessage(
                                                com.jarvisai.assistant.antigravity.AntigravityWorkspaceEngine.ANTIGRAVITY_GUIDE_PROMPT
                                            )
                                        }
                                        "/logout" -> {
                                            promptInput = ""
                                            viewModel.logoutAntigravity()
                                            Toast.makeText(context, "Signed out of Antigravity session", Toast.LENGTH_SHORT).show()
                                        }
                                        "/clear" -> {
                                            promptInput = ""
                                            viewModel.startNewChat()
                                        }
                                        "/terminal" -> {
                                            promptInput = ""
                                            previousActiveScreen = activeScreen
                                            activeScreen = "terminal"
                                        }
                                        "/ide" -> {
                                            promptInput = ""
                                            previousActiveScreen = activeScreen
                                            activeScreen = "ide"
                                        }
                                        "/files" -> {
                                            promptInput = ""
                                            previousActiveScreen = activeScreen
                                            activeScreen = "files"
                                        }
                                        "/artifacts" -> {
                                            promptInput = ""
                                            showArtifactsDrawer = true
                                        }
                                        "/antigravity" -> {
                                            promptInput = ""
                                            showAntigravityLoginDialog = true
                                        }
                                        "/bubble" -> {
                                            promptInput = ""
                                            toggleFloatingChatHeadBubble()
                                        }
                                        "/skills" -> {
                                            promptInput = ""
                                            if (isAntigravityActive) {
                                                showAntigravityRulesSheet = true
                                            } else {
                                                coroutineScope.launch { drawerState.open() }
                                            }
                                        }
                                        else -> {
                                            promptInput = "$selectedCmd "
                                        }
                                    }
                                }
                            )

                            // Interactive Ask-Question Stepper Panel (docked directly above the message box)
                            val activePendingQuestionTool = remember(currentTurnToolCalls, currentMessages, isChatLoading) {
                                currentTurnToolCalls.lastOrNull {
                                    it.questionnaire != null && it.status != com.jarvisai.assistant.model.ToolCallStatus.SUCCESS
                                } ?: if (!isChatLoading) {
                                    val lastModelMsg = currentMessages.lastOrNull { it.role == "model" && !it.toolCallsJson.isNullOrBlank() }
                                    lastModelMsg?.toolCallsJson?.let { rawJson ->
                                        com.jarvisai.assistant.ui.screens.parseToolCallsFromJson(rawJson).lastOrNull {
                                            it.questionnaire != null && it.status != com.jarvisai.assistant.model.ToolCallStatus.SUCCESS
                                        }
                                    }
                                } else null
                            }
                            if (activePendingQuestionTool != null) {
                                com.jarvisai.assistant.ui.components.AskQuestionBottomPanel(
                                    toolCall = activePendingQuestionTool,
                                    onSubmit = { tCall, responses ->
                                        viewModel.submitQuestionnaireAnswers(tCall, responses)
                                    },
                                    onSkip = { tCall ->
                                        viewModel.skipQuestionnaire(tCall)
                                    }
                                )
                            }

                            // Pending Multimodal Attachments Strip (Photos, Videos, Files)
                            if (pendingAttachments.isNotEmpty()) {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp)
                                ) {
                                    items(pendingAttachments, key = { it.id }) { att ->
                                        val kindIcon = when (att.kind) {
                                            com.jarvisai.assistant.model.AttachmentKind.IMAGE -> Icons.Default.Image
                                            com.jarvisai.assistant.model.AttachmentKind.VIDEO -> Icons.Default.VideoFile
                                            com.jarvisai.assistant.model.AttachmentKind.DOCUMENT -> Icons.Default.Description
                                            com.jarvisai.assistant.model.AttachmentKind.FILE -> Icons.Default.InsertDriveFile
                                        }
                                        val kindColor = when (att.kind) {
                                            com.jarvisai.assistant.model.AttachmentKind.IMAGE -> Color(0xFF38BDF8)
                                            com.jarvisai.assistant.model.AttachmentKind.VIDEO -> Color(0xFFF472B6)
                                            com.jarvisai.assistant.model.AttachmentKind.DOCUMENT -> Color(0xFFA78BFA)
                                            com.jarvisai.assistant.model.AttachmentKind.FILE -> Color(0xFF10B981)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF1E293B),
                                            border = BorderStroke(1.dp, kindColor.copy(alpha = 0.45f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(start = 6.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (att.kind == com.jarvisai.assistant.model.AttachmentKind.IMAGE) {
                                                    AsyncImage(
                                                        model = ImageRequest.Builder(context)
                                                            .data(Uri.parse(att.uriString))
                                                            .crossfade(true)
                                                            .build(),
                                                        contentDescription = att.displayName,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(kindColor.copy(alpha = 0.16f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = kindIcon,
                                                            contentDescription = att.kind.name,
                                                            tint = kindColor,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.widthIn(max = 140.dp)) {
                                                    Text(
                                                        text = att.displayName,
                                                        color = Color.White,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = com.jarvisai.assistant.data.media.AttachmentProcessor.formatBytes(att.sizeBytes),
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        pendingAttachments = pendingAttachments.filterNot { it.id == att.id }
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Remove attachment",
                                                        tint = Color(0xFF94A3B8),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            JarvisUnifiedCommandBar(
                                promptText = promptInput,
                                onPromptChange = { promptInput = it },
                                activeModelName = activeModelName,
                                activeModelId = settings.textModel,
                                thinkingLevelLabel = currentThinkingPreference.level.label,
                                isThinkingEnabled = currentThinkingPreference.isEnabled,
                                isAntigravityLoggedIn = isAntigravityLoggedIn,
                                antigravityTier = antigravityTier,
                                antigravityModels = antigravityModels,
                                isRefreshingAntigravityModels = isRefreshingAntigravityModels,
                                onOpenAntigravityLogin = { showAntigravityLoginDialog = true },
                                onRefreshAntigravityModels = { viewModel.refreshAntigravityModels() },
                                onOpenThinkingSelector = { showThinkingSelectorSheet = true },
                                onOpenCactusManager = { activeScreen = "cactus_manager" },
                                onModelSelect = { modelId, displayName ->
                                    activeModelName = displayName
                                    when {
                                        modelId.startsWith("cactus:", ignoreCase = true) -> {
                                            localTerminalAgentOverride = null
                                            viewModel.updateLlmProvider(com.jarvisai.assistant.model.LLMProvider.CACTUS_LOCAL)
                                            viewModel.updateTextModel(modelId)
                                            com.jarvisai.assistant.cactus.CactusNativeRuntime.updateConfig(context) {
                                                it.copy(activeLlmModelId = modelId)
                                            }
                                            orchestrator.agentOrchestrator.selectAgent(ActiveCodingAgent.GEMINI_DIRECT)
                                        }
                                        modelId == "antigravity-agent" -> {
                                            localTerminalAgentOverride = null
                                            viewModel.updateLlmProvider(com.jarvisai.assistant.model.LLMProvider.ANTIGRAVITY)
                                            val recId = antigravityModels.firstOrNull { it.isRecommended }?.id ?: "orchestrator-carbon"
                                            viewModel.updateTextModel("agy:$recId")
                                            orchestrator.agentOrchestrator.selectAgent(ActiveCodingAgent.ANTIGRAVITY_GRPC)
                                        }
                                        modelId == "claude-code-cli" -> {
                                            localTerminalAgentOverride = "Claude Code"
                                            orchestrator.agentOrchestrator.selectAgent(ActiveCodingAgent.CLAUDE_CODE)
                                        }
                                        modelId == "opencode-router" -> {
                                            localTerminalAgentOverride = "OpenCode"
                                            orchestrator.agentOrchestrator.selectAgent(ActiveCodingAgent.OPEN_CODE)
                                        }
                                        modelId == "codex-cli-agent" -> {
                                            localTerminalAgentOverride = "Codex"
                                            orchestrator.agentOrchestrator.selectAgent(ActiveCodingAgent.CODEX_CLI)
                                        }
                                        modelId.startsWith("agy:") -> {
                                            localTerminalAgentOverride = null
                                            viewModel.updateLlmProvider(com.jarvisai.assistant.model.LLMProvider.ANTIGRAVITY)
                                            viewModel.updateTextModel(modelId)
                                            orchestrator.agentOrchestrator.selectAgent(ActiveCodingAgent.GEMINI_DIRECT)
                                        }
                                        modelId == "gemini-3.8-live" || modelId == "gemini-3.8-live-extended-thinking" -> {
                                            localTerminalAgentOverride = null
                                            viewModel.updateLlmProvider(com.jarvisai.assistant.model.LLMProvider.GEMINI_API)
                                            viewModel.updateVoiceModel(modelId)
                                            viewModel.updateTextModel(modelId)
                                            orchestrator.agentOrchestrator.selectAgent(ActiveCodingAgent.GEMINI_DIRECT)
                                        }
                                        else -> {
                                            localTerminalAgentOverride = null
                                            viewModel.updateLlmProvider(com.jarvisai.assistant.model.LLMProvider.GEMINI_API)
                                            viewModel.updateTextModel(modelId)
                                            orchestrator.agentOrchestrator.selectAgent(ActiveCodingAgent.GEMINI_DIRECT)
                                        }
                                    }
                                },
                                isLiveVoiceActive = isLiveVoiceActive,
                                isMicMuted = isMicMuted,
                                audioLevel = liveAudioLevel,
                                isDictating = isDictating,
                                dictationAudioLevel = dictationAudioLevel,
                                hasAttachments = pendingAttachments.isNotEmpty(),
                                isGenerating = isChatLoading || isAgenticLoopRunning || isWorkflowRunning,
                                onStopClick = { viewModel.stopChatGeneration() },
                                onSendClick = {
                                    if (isDictating) {
                                        dictationController.stopDictation(commitPendingPartial = true)
                                    }
                                    val cleanPrompt = promptInput.trim()
                                    val attachedNow = pendingAttachments
                                    if (cleanPrompt.isNotBlank() || attachedNow.isNotEmpty()) {
                                        promptInput = ""
                                        pendingAttachments = emptyList()
                                        when {
                                            attachedNow.isEmpty() && cleanPrompt == "/clear" -> viewModel.startNewChat()
                                            attachedNow.isEmpty() && cleanPrompt == "/resume" -> showAntigravityInboxSheet = true
                                            attachedNow.isEmpty() && (cleanPrompt == "/rewind" || cleanPrompt == "/undo") -> {
                                                val rewound = viewModel.rewindLastTurn()
                                                Toast.makeText(
                                                    context,
                                                    if (rewound) "Rewound last turn & reset context" else "Nothing to rewind",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            attachedNow.isEmpty() && cleanPrompt == "/workflow" -> showAntigravityRulesSheet = true
                                            attachedNow.isEmpty() && cleanPrompt == "/fast" -> {
                                                viewModel.updateAntigravityAgentMode(
                                                    com.jarvisai.assistant.antigravity.AntigravityAgentMode.FAST
                                                )
                                                Toast.makeText(context, "Switched to Antigravity Fast Mode", Toast.LENGTH_SHORT).show()
                                            }
                                            attachedNow.isEmpty() && cleanPrompt == "/antigravity-guide" -> {
                                                viewModel.sendChatMessage(
                                                    com.jarvisai.assistant.antigravity.AntigravityWorkspaceEngine.ANTIGRAVITY_GUIDE_PROMPT
                                                )
                                            }
                                            attachedNow.isEmpty() && cleanPrompt == "/logout" -> {
                                                viewModel.logoutAntigravity()
                                                Toast.makeText(context, "Signed out of Antigravity session", Toast.LENGTH_SHORT).show()
                                            }
                                            attachedNow.isEmpty() && cleanPrompt.startsWith("/plan ") -> {
                                                viewModel.updateAntigravityAgentMode(
                                                    com.jarvisai.assistant.antigravity.AntigravityAgentMode.PLANNING
                                                )
                                                val taskText = cleanPrompt.removePrefix("/plan ").trim()
                                                viewModel.sendChatMessage(
                                                    "[/plan Planning Mode] First research cleanly without mutating files, then present a structured `task.md` checklist and `implementation_plan.md` for my approval:\n\n$taskText"
                                                )
                                            }
                                            attachedNow.isEmpty() && cleanPrompt.startsWith("/browser") -> {
                                                val webTask = cleanPrompt.removePrefix("/browser").trim()
                                                if (webTask.isBlank()) {
                                                    previousActiveScreen = activeScreen
                                                    activeScreen = "browser"
                                                } else {
                                                    viewModel.sendChatMessage(
                                                        "[/browser Browser Subagent Task] Use browser inspection/automation tools to perform and verify:\n\n$webTask"
                                                    )
                                                }
                                            }
                                            attachedNow.isEmpty() && cleanPrompt == "/terminal" -> {
                                                previousActiveScreen = activeScreen
                                                activeScreen = "terminal"
                                            }
                                            attachedNow.isEmpty() && cleanPrompt == "/ide" -> {
                                                previousActiveScreen = activeScreen
                                                activeScreen = "ide"
                                            }
                                            attachedNow.isEmpty() && cleanPrompt == "/files" -> {
                                                previousActiveScreen = activeScreen
                                                activeScreen = "files"
                                            }
                                            attachedNow.isEmpty() && cleanPrompt == "/artifacts" -> showArtifactsDrawer = true
                                            attachedNow.isEmpty() && cleanPrompt == "/antigravity" -> showAntigravityLoginDialog = true
                                            attachedNow.isEmpty() && cleanPrompt == "/bubble" -> toggleFloatingChatHeadBubble()
                                            attachedNow.isEmpty() && cleanPrompt.startsWith("/git") -> {
                                                coroutineScope.launch {
                                                    orchestrator.executeTerminalCommand("git status")
                                                }
                                            }
                                            attachedNow.isEmpty() && (cleanPrompt.startsWith("/bash ") || cleanPrompt.startsWith("/sh ")) -> {
                                                val cmd = cleanPrompt.removePrefix("/bash ").removePrefix("/sh ")
                                                val isDestructive = cmd.contains("rm ") || cmd.contains("git push") || cmd.contains("npm publish")
                                                if (isDestructive && !autoApproveSessionBash) {
                                                    manualPendingClaudeCmd = ClaudePendingCommand(
                                                        id = "cmd_${System.currentTimeMillis()}",
                                                        command = cmd,
                                                        workingDir = LocalEnvironmentManager.getHomeDir(context).absolutePath,
                                                        riskLevel = if (cmd.contains("rm -rf")) "HIGH" else "MEDIUM"
                                                    )
                                                } else {
                                                    coroutineScope.launch {
                                                        orchestrator.executeTerminalCommand(cmd)
                                                    }
                                                }
                                            }
                                            activeModelName in setOf("OpenCode", "Claude Code", "Codex") -> {
                                                val delegatedType = when (activeModelName) {
                                                    "Claude Code" -> "claude"
                                                    "Codex" -> "codex"
                                                    else -> "opencode"
                                                }
                                                coroutineScope.launch {
                                                    orchestrator.delegateCodingTask(
                                                        prompt = cleanPrompt,
                                                        agentType = delegatedType
                                                    )
                                                }
                                                viewModel.sendChatMessage(cleanPrompt, attachments = attachedNow)
                                            }
                                            isLiveVoiceActive && attachedNow.isEmpty() -> {
                                                viewModel.sendLiveVoiceTextPrompt(cleanPrompt)
                                            }
                                            else -> {
                                                viewModel.sendChatMessage(cleanPrompt, attachments = attachedNow)
                                            }
                                        }
                                    }
                                },
                                onDictateToggleClick = {
                                    if (isDictating) {
                                        dictationController.stopDictation(commitPendingPartial = true)
                                    } else {
                                        if (isLiveVoiceActive) {
                                            viewModel.stopLiveVoiceSession()
                                        }
                                        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.RECORD_AUDIO
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            dictationController.startDictation(promptInput)
                                        } else {
                                            dictationMicPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                onMicClick = {
                                    if (isDictating) {
                                        dictationController.stopDictation(commitPendingPartial = true)
                                    }
                                    if (isLiveVoiceActive) {
                                        viewModel.stopLiveVoiceSession()
                                    } else {
                                        viewModel.startLiveVoiceSession()
                                    }
                                },
                                onVoiceStudioClick = {
                                    previousActiveScreen = activeScreen
                                    activeScreen = "voice"
                                },
                                isAgyVoiceCallActive = agyVoiceCallState.active,
                                onAgyVoiceCallClick = if (isAntigravityActive) triggerAgyVoiceCallFlow else null,
                                onToolsClick = {
                                    showAttachmentSheet = true
                                },
                                isAntigravityActive = isAntigravityActive,
                                antigravityAgentMode = settings.antigravityAgentMode,
                                antigravityAutonomyPolicy = settings.antigravityAutonomyPolicy,
                                onToggleAntigravityAgentMode = {
                                    val next = settings.antigravityAgentMode.toggle()
                                    viewModel.updateAntigravityAgentMode(next)
                                    Toast.makeText(context, "Antigravity: ${next.label} Mode", Toast.LENGTH_SHORT).show()
                                },
                                onSelectAntigravityAgentMode = { mode ->
                                    viewModel.updateAntigravityAgentMode(mode)
                                    Toast.makeText(context, "Antigravity: ${mode.label} Mode", Toast.LENGTH_SHORT).show()
                                },
                                onCycleAntigravityAutonomyPolicy = {
                                    val next = settings.antigravityAutonomyPolicy.next()
                                    viewModel.updateAntigravityAutonomyPolicy(next)
                                    Toast.makeText(context, "agy policy: ${next.cliFlag}", Toast.LENGTH_SHORT).show()
                                },
                                onSelectAntigravityAutonomyPolicy = { policy ->
                                    viewModel.updateAntigravityAutonomyPolicy(policy)
                                    Toast.makeText(context, "agy policy: ${policy.cliFlag}", Toast.LENGTH_SHORT).show()
                                },
                                onInsertAtMention = {
                                    promptInput = if (promptInput.isEmpty() || promptInput.endsWith(" ")) {
                                        "${promptInput}@"
                                    } else {
                                        "$promptInput @"
                                    }
                                },
                                onInsertSlashCommand = {
                                    promptInput = "/"
                                }
                            )
                        }
                    }
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .consumeWindowInsets(paddingValues)
                        .background(Color(0xFF000000))
                ) {
                    com.jarvisai.assistant.ui.antigravity.AntigravityStageTopBanners(
                        activeScreen = activeScreen,
                        isAntigravityActive = isAntigravityActive,
                        workspaceMode = settings.antigravityWorkspaceMode,
                        inboxPendingCount = pendingPlanArtifacts.size + subagentTasks.count { it.status.name == "RUNNING" },
                        artifactCount = allStructuredArtifacts.size.coerceAtLeast(generatedArtifacts.size),
                        rulesCount = antigravityRulesAndSkillsCount,
                        canRewind = currentMessages.isNotEmpty() && !isChatLoading,
                        isLinuxRootfsInstalled = isLinuxRootfsInstalled,
                        isSetupBannerDismissed = isSetupBannerDismissed,
                        onToggleWorkspaceMode = {
                            val next = settings.antigravityWorkspaceMode.toggle()
                            viewModel.updateAntigravityWorkspaceMode(next)
                            Toast.makeText(context, "Workspace Mode: ${next.label}", Toast.LENGTH_SHORT).show()
                        },
                        onOpenInbox = { showAntigravityInboxSheet = true },
                        onOpenArtifacts = { showArtifactsDrawer = true },
                        onOpenRulesAndSkills = { showAntigravityRulesSheet = true },
                        onRewindLastTurn = {
                            val rewound = viewModel.rewindLastTurn()
                            Toast.makeText(
                                context,
                                if (rewound) "Rewound last turn" else "Nothing to rewind",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onOpenRootfsInstaller = { showRootfsInstallerDialog = true },
                        onDismissSetupBanner = { isSetupBannerDismissed = true }
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        // Opaque curtain behind active screens so persistently-mounted BrowserScreen (at zIndex -1f)
                        // stays attached to the Window without ever bleeding through or intercepting stray taps.
                        if (hasEverOpenedBrowser && activeScreen != "browser") {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(0f)
                                    .background(Color(0xFF000000))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {}
                                    )
                            )
                        }

                        // 1. PERSISTENT HOME / CHAT STAGE LAYER
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(1f)
                                .graphicsLayer {
                                    alpha = if (activeScreen == "home") 1f else 0f
                                    translationX = if (activeScreen == "home") 0f else 100000f
                                }
                        ) {
                            val listState = rememberLazyListState()
                            val imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)
                            val timelineItems = remember(
                                currentMessages,
                                liveTranscripts,
                                archivedLiveTranscriptIds,
                                subagentTasks,
                                agyVoiceChatsMap,
                                currentExternalCascadeId,
                                agyVoiceCallState.active,
                                agyVoiceCallState.conversationId,
                                agyVoiceCallState.startedAtWall,
                                isAntigravityActive
                            ) {
                                buildList {
                                    currentMessages.forEach { add(ChatTimelineItem.PersistentMessage(it)) }
                                    liveTranscripts
                                        .filterNot { item ->
                                            item.id in archivedLiveTranscriptIds ||
                                                (item.speaker.equals("SYSTEM", ignoreCase = true) && (
                                                    item.text.startsWith("[SESSION_CONNECTED]") ||
                                                        item.text.startsWith("Microphone ") ||
                                                        item.text.startsWith("Live Voice call ") ||
                                                        item.text.startsWith("👁️") ||
                                                        item.text.startsWith("Audio mode switched") ||
                                                        item.text.startsWith("Persona switched")
                                                    ))
                                        }
                                        .forEach { add(ChatTimelineItem.LiveTranscript(it)) }
                                    subagentTasks.forEach { add(ChatTimelineItem.SubagentTask(it)) }
                                    val callRecords = if (isAntigravityActive && !currentExternalCascadeId.isNullOrBlank()) {
                                        agyVoiceChatsMap[currentExternalCascadeId].orEmpty()
                                    } else emptyList()
                                    callRecords.forEach { rec ->
                                        val started = rec.startedAtMs ?: return@forEach
                                        add(
                                            ChatTimelineItem.VoiceCall(
                                                record = rec,
                                                live = false,
                                                sortTimestamp = started,
                                                orderHint = -1
                                            )
                                        )
                                    }
                                    val isLiveHere = isAntigravityActive &&
                                        agyVoiceCallState.active &&
                                        !currentExternalCascadeId.isNullOrBlank() &&
                                        agyVoiceCallState.conversationId == currentExternalCascadeId
                                    if (isLiveHere && agyVoiceCallState.startedAtWall > 0L) {
                                        val alreadyListed = callRecords.any { rec ->
                                            val s = rec.startedAtMs ?: return@any false
                                            kotlin.math.abs(s - agyVoiceCallState.startedAtWall) < 15_000L
                                        }
                                        if (!alreadyListed) {
                                            add(
                                                ChatTimelineItem.VoiceCall(
                                                    record = null,
                                                    live = true,
                                                    sortTimestamp = agyVoiceCallState.startedAtWall,
                                                    orderHint = -1
                                                )
                                            )
                                        }
                                    }
                                }.sortedWith(
                                    compareBy<ChatTimelineItem> { it.sortTimestamp }
                                        .thenBy { it.orderHint }
                                )
                            }

                            LaunchedEffect(
                                timelineItems.size,
                                liveTranscripts.lastOrNull()?.lastUpdatedTimestamp,
                                liveTranscripts.sumOf { it.text.length },
                                subagentTasks.lastOrNull()?.outputSummary,
                                agenticStepLogs.size,
                                agenticStepLogs.lastOrNull(),
                                currentTurnToolCalls.size,
                                streamingPartial,
                                currentTurnThought,
                                isChatLoading,
                                imeBottomPx
                            ) {
                                val totalItems = listState.layoutInfo.totalItemsCount
                                if (totalItems > 0) {
                                    if (isLiveVoiceActive || !streamingPartial.isNullOrBlank()) {
                                        runCatching { listState.scrollToItem(totalItems - 1) }
                                    } else {
                                        runCatching { listState.animateScrollToItem(totalItems - 1) }
                                    }
                                }
                            }

                                if (timelineItems.isEmpty() && agenticStepLogs.isEmpty() && currentTurnThought.isNullOrBlank() && currentTurnToolCalls.isEmpty() && streamingPartial.isNullOrBlank() && !isChatLoading) {
                                    // AntiGem-Style Minimalist Centered Idle View
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        if (isLiveVoiceActive) {
                                            ArcReactorView(
                                                status = status,
                                                audioLevel = liveAudioLevel,
                                                size = 128.dp
                                            )
                                            Spacer(modifier = Modifier.height(18.dp))
                                            Text(
                                                text = "Agentra Live Voice ($liveConnectionLabel)",
                                                color = Color.White,
                                                fontSize = 21.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Waveform Active • Speak naturally in English, Hindi, or Hinglish",
                                                color = Color(0xFF7D8590),
                                                fontSize = 14.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        } else if (localServerStatus is com.jarvisai.assistant.terminal.LocalServerStatus.Starting) {
                                            com.jarvisai.assistant.ui.components.EngineWarmingUpView(
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else if (localServerStatus is com.jarvisai.assistant.terminal.LocalServerStatus.Stopped) {
                                            com.jarvisai.assistant.ui.components.ServerStoppedPromptCard(
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            val themeColors = com.example.ui.theme.JarvisTheme.colors
                                            Text(
                                                text = "Ready for your next task",
                                                color = themeColors.textPrimary,
                                                fontFamily = FontFamily.SansSerif,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                letterSpacing = (-0.2).sp,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Type a command, attach files with +, or open Voice Studio",
                                                color = themeColors.textSecondary,
                                                fontSize = 14.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    // Active Chronological Conversation Stream (Messages + Live Voice + Subagent Cards)
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        itemsIndexed(timelineItems, key = { _, it -> it.timelineKey }) { index, item ->
                                            val prevItem = timelineItems.getOrNull(index - 1)
                                            when (item) {
                                                is ChatTimelineItem.VoiceCall -> {
                                                    VoiceCallMarker(
                                                        startedAt = item.record?.startedAtMs ?: item.sortTimestamp,
                                                        durationMs = item.record?.durationMs,
                                                        live = item.live,
                                                        onOpen = {
                                                            if (item.live) {
                                                                AgyVoiceCall.setMinimized(false)
                                                            } else if (item.record != null) {
                                                                openAgyTranscriptRecord = item.record
                                                            }
                                                        }
                                                    )
                                                }

                                                is ChatTimelineItem.SubagentTask -> {
                                                    val task = item.task
                                                    AgentExecutionCard(
                                                        agentName = "${task.agentType.uppercase()} SUBAGENT",
                                                        command = task.prompt,
                                                        output = task.outputSummary.ifBlank { "Working in ${task.workingDirectory}..." },
                                                        status = task.status.name,
                                                        stepInfo = "Step ${task.progressStep}/${task.totalSteps}",
                                                        onDismiss = {
                                                            com.jarvisai.assistant.agent.AgentOrchestrator.dismissTask(task.id)
                                                        }
                                                    )
                                                }

                                                is ChatTimelineItem.LiveTranscript -> {
                                                    val transcript = item.transcript
                                                    val isUser = transcript.speaker.equals("You", ignoreCase = true) ||
                                                        transcript.speaker.equals("USER", ignoreCase = true)
                                                    val isSystemNotice = transcript.speaker.equals("SYSTEM", ignoreCase = true)
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 5.dp),
                                                        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                                                    ) {
                                                        if (isUser) {
                                                            Surface(
                                                                shape = RoundedCornerShape(
                                                                    topStart = 18.dp,
                                                                    topEnd = 4.dp,
                                                                    bottomStart = 18.dp,
                                                                    bottomEnd = 18.dp
                                                                ),
                                                                color = Color(0xFF1A2538),
                                                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.32f)),
                                                                modifier = Modifier
                                                                    .padding(start = 52.dp)
                                                                    .widthIn(max = 310.dp)
                                                            ) {
                                                                SelectionContainer {
                                                                    Text(
                                                                        text = transcript.text,
                                                                        color = Color.White,
                                                                        fontSize = 14.sp,
                                                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                                                    )
                                                                }
                                                            }
                                                        } else {
                                                            SelectionContainer {
                                                                Text(
                                                                    text = transcript.text,
                                                                    color = if (isSystemNotice) Color(0xFF94A3B8) else Color.White,
                                                                    fontSize = if (isSystemNotice) 12.5.sp else 14.5.sp,
                                                                    lineHeight = if (isSystemNotice) 18.sp else 22.sp,
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .padding(vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                is ChatTimelineItem.PersistentMessage -> {
                                                    val message = item.message
                                            val isUser = message.role.equals("user", ignoreCase = true)
                                            var copiedMessageFeedback by remember(message.id) { mutableStateOf(false) }
                                            LaunchedEffect(copiedMessageFeedback) {
                                                if (copiedMessageFeedback) {
                                                    delay(1500L)
                                                    copiedMessageFeedback = false
                                                }
                                            }

                                            if (!isUser && message.content.startsWith("[AI_STUDIO_SETUP_REQUIRED]")) {
                                                AiStudioInlineSetupCard(
                                                    onSaveKey = { enteredKey ->
                                                        viewModel.saveApiKey(enteredKey)
                                                        Toast.makeText(
                                                            context,
                                                            "Google AI Studio API Key saved to Keystore vault!",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        viewModel.retryLastFailedMessage()
                                                    },
                                                    onOpenFullSettings = {
                                                        activeScreen = "api_settings"
                                                    }
                                                )
                                            } else {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 6.dp),
                                                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                                                ) {
                                                    val prevTimestamp = (prevItem as? ChatTimelineItem.PersistentMessage)?.message?.timestamp
                                                    val showDateDivider = prevTimestamp == null ||
                                                        (message.timestamp - prevTimestamp) > 15 * 60 * 1000L
                                                    if (showDateDivider && message.timestamp > 0L) {
                                                        com.jarvisai.assistant.ui.components.ChatTimestampSeparator(
                                                            timestampMs = message.timestamp
                                                        )
                                                    }
                                                    val hasInterleavedThoughts = remember(message.toolCallsJson) {
                                                        message.toolCallsJson?.contains("\"__thought__\"") == true
                                                    }
                                                    if (!isUser && !hasInterleavedThoughts && !message.thoughtText.isNullOrBlank()) {
                                                        com.jarvisai.assistant.ui.components.ThinkingAccordion(
                                                            thoughtText = message.thoughtText,
                                                            durationMs = message.thoughtDurationMs,
                                                            isStreaming = false
                                                        )
                                                    }

                                                    com.jarvisai.assistant.ui.antigravity.AntigravityMessageToolsAndArtifacts(
                                                        isUser = isUser,
                                                        isAntigravityActive = isAntigravityActive,
                                                        toolCallsJson = message.toolCallsJson,
                                                        messageArtifacts = structuredArtifactsByMessage[message.id].orEmpty(),
                                                        onSubmitQuestionnaire = { tCall, responses ->
                                                            viewModel.submitQuestionnaireAnswers(tCall, responses)
                                                        },
                                                        onSkipQuestionnaire = { tCall ->
                                                            viewModel.skipQuestionnaire(tCall)
                                                        },
                                                        onApprovePlan = { approveAntigravityPlan(it) },
                                                        onSubmitInlineComment = { targetArt, comment ->
                                                            submitAntigravityPlanComment(targetArt, comment)
                                                        },
                                                        onOpenArtifactFileInIde = { targetFile ->
                                                            selectedIdeFile = targetFile
                                                            activeScreen = "ide"
                                                        }
                                                    )

                                                    if (message.role == "system_summary" || message.content.startsWith("[CONTEXT_SUMMARY]")) {
                                                        com.jarvisai.assistant.ui.components.ContextSummaryCheckpointBanner(
                                                            summaryText = message.content.removePrefix("[CONTEXT_SUMMARY]").trim()
                                                        )
                                                    } else if (isUser) {
                                                        val decodedUser = remember(message.content) {
                                                            com.jarvisai.assistant.model.ChatAttachmentCodec.decodeMessage(message.content)
                                                        }
                                                        val parsedVoiceTurn = remember(decodedUser.bodyText) {
                                                            VoiceTurn.parse(decodedUser.bodyText)
                                                        }
                                                        val displayUserBody = remember(decodedUser.bodyText, parsedVoiceTurn) {
                                                            if (parsedVoiceTurn != null) {
                                                                parsedVoiceTurn.spoken.ifBlank { parsedVoiceTurn.voiceLine ?: "Voice request" }
                                                            } else {
                                                                decodedUser.bodyText
                                                            }
                                                        }
                                                        var previewAttachment by remember {
                                                            mutableStateOf<com.jarvisai.assistant.model.PersistedChatAttachment?>(null)
                                                        }
                                                        var showFullVoicePromptDialog by remember { mutableStateOf(false) }
                                                        val copyableUserText = remember(message.content, displayUserBody, parsedVoiceTurn) {
                                                            if (parsedVoiceTurn != null) displayUserBody
                                                            else com.jarvisai.assistant.model.ChatAttachmentCodec.stripForCopy(message.content)
                                                        }

                                                        // User message: clean dark AntiGem-style bubble; tapping a Voice bubble opens the Full prompt dialog
                                                        Surface(
                                                            shape = RoundedCornerShape(
                                                                topStart = 18.dp,
                                                                topEnd = 4.dp,
                                                                bottomStart = 18.dp,
                                                                bottomEnd = 18.dp
                                                            ),
                                                            color = Color(0xFF1C2331),
                                                            border = BorderStroke(0.6.dp, Color(0xFF2B3548)),
                                                            modifier = Modifier
                                                                .padding(start = 52.dp)
                                                                .widthIn(max = 310.dp)
                                                                .then(
                                                                    if (parsedVoiceTurn != null) {
                                                                        Modifier
                                                                            .testTag("voice_turn_bubble_${message.id}")
                                                                            .clickable { showFullVoicePromptDialog = true }
                                                                    } else {
                                                                        Modifier
                                                                    }
                                                                )
                                                        ) {
                                                            Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
                                                                if (parsedVoiceTurn != null) {
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                                        modifier = Modifier.padding(bottom = 4.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Rounded.HeadsetMic,
                                                                            contentDescription = "Voice message",
                                                                            tint = Color(0xFF38BDF8),
                                                                            modifier = Modifier.size(13.dp)
                                                                        )
                                                                        Text(
                                                                            text = "Voice",
                                                                            color = Color(0xFF38BDF8),
                                                                            fontSize = 11.sp,
                                                                            fontWeight = FontWeight.SemiBold
                                                                        )
                                                                        Spacer(modifier = Modifier.width(2.dp))
                                                                        Text(
                                                                            text = "· Tap for full prompt",
                                                                            color = Color(0xFF64748B),
                                                                            fontSize = 10.sp
                                                                        )
                                                                    }
                                                                }
                                                                if (decodedUser.attachments.isNotEmpty()) {
                                                                    ChatBubbleAttachmentsSection(
                                                                        attachments = decodedUser.attachments,
                                                                        onAttachmentClick = { att ->
                                                                            if (att.kind == com.jarvisai.assistant.model.AttachmentKind.IMAGE) {
                                                                                previewAttachment = att
                                                                            } else {
                                                                                openAttachmentExternally(context, att)
                                                                            }
                                                                        }
                                                                    )
                                                                    if (displayUserBody.isNotBlank()) {
                                                                        Spacer(modifier = Modifier.height(8.dp))
                                                                    }
                                                                }
                                                                if (displayUserBody.isNotBlank()) {
                                                                    if (parsedVoiceTurn != null) {
                                                                        Text(
                                                                            text = displayUserBody,
                                                                            color = Color(0xFFF3F4F6),
                                                                            fontSize = 14.5.sp,
                                                                            lineHeight = 20.sp
                                                                        )
                                                                    } else {
                                                                        SelectionContainer {
                                                                            com.jarvisai.assistant.ui.screens.RenderMessageContentWithCodeBlocks(
                                                                                content = displayUserBody,
                                                                                textColor = Color(0xFFF3F4F6),
                                                                                fillMaxWidth = false,
                                                                                onCopy = { codeText ->
                                                                                    copyTextWithToast(context, "Code", codeText)
                                                                                }
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                                val talkerPreview = parsedVoiceTurn?.voiceLine?.takeIf { it.isNotBlank() }
                                                                if (talkerPreview != null) {
                                                                    Spacer(modifier = Modifier.height(6.dp))
                                                                    Text(
                                                                        text = "\"$talkerPreview\"",
                                                                        color = Color(0xFF94A3B8),
                                                                        fontSize = 12.sp,
                                                                        lineHeight = 16.sp,
                                                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                                        maxLines = 2,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        if (showFullVoicePromptDialog && parsedVoiceTurn != null) {
                                                            val rawPromptText = decodedUser.bodyText
                                                            val charCountFormatted = remember(rawPromptText) {
                                                                java.text.NumberFormat.getInstance().format(rawPromptText.length)
                                                            }
                                                            val approxTokensFormatted = remember(rawPromptText) {
                                                                java.text.NumberFormat.getInstance().format(((rawPromptText.length + 3) / 4).coerceAtLeast(1))
                                                            }
                                                            androidx.compose.ui.window.Dialog(
                                                                onDismissRequest = { showFullVoicePromptDialog = false },
                                                                properties = androidx.compose.ui.window.DialogProperties(
                                                                    usePlatformDefaultWidth = false
                                                                )
                                                            ) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(22.dp),
                                                                    color = Color(0xFF141923),
                                                                    border = BorderStroke(1.dp, Color(0xFF283142)),
                                                                    modifier = Modifier
                                                                        .fillMaxWidth(0.92f)
                                                                        .widthIn(max = 520.dp)
                                                                        .testTag("dialog_full_voice_prompt")
                                                                ) {
                                                                    Column(
                                                                        modifier = Modifier.padding(18.dp),
                                                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = "Full prompt",
                                                                            color = Color(0xFFF8FAFC),
                                                                            fontSize = 19.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                        Text(
                                                                            text = "$charCountFormatted characters · ~$approxTokensFormatted tokens",
                                                                            color = Color(0xFF94A3B8),
                                                                            fontSize = 12.5.sp,
                                                                            fontWeight = FontWeight.Medium
                                                                        )
                                                                        Text(
                                                                            text = "Exactly what was sent to the agent, including the parts the chat hides.",
                                                                            color = Color(0xFF94A3B8),
                                                                            fontSize = 12.5.sp,
                                                                            lineHeight = 17.sp
                                                                        )
                                                                        Surface(
                                                                            shape = RoundedCornerShape(14.dp),
                                                                            color = Color(0xFF0A0E15),
                                                                            border = BorderStroke(1.dp, Color(0xFF1F2937)),
                                                                            modifier = Modifier
                                                                                .fillMaxWidth()
                                                                                .heightIn(min = 120.dp, max = 380.dp)
                                                                        ) {
                                                                            val promptScroll = rememberScrollState()
                                                                            Box(
                                                                                modifier = Modifier
                                                                                    .verticalScroll(promptScroll)
                                                                                    .padding(12.dp)
                                                                            ) {
                                                                                SelectionContainer {
                                                                                    Text(
                                                                                        text = rawPromptText,
                                                                                        color = Color(0xFFE2E8F0),
                                                                                        fontSize = 12.sp,
                                                                                        lineHeight = 17.sp,
                                                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                        Row(
                                                                            modifier = Modifier.fillMaxWidth(),
                                                                            horizontalArrangement = Arrangement.End,
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            TextButton(
                                                                                onClick = {
                                                                                    copyTextWithToast(context, "Full prompt", rawPromptText)
                                                                                },
                                                                                modifier = Modifier
                                                                                    .heightIn(min = 48.dp)
                                                                                    .testTag("btn_copy_full_voice_prompt")
                                                                            ) {
                                                                                Text(
                                                                                    text = "Copy all",
                                                                                    color = Color(0xFF38BDF8),
                                                                                    fontWeight = FontWeight.SemiBold
                                                                                )
                                                                            }
                                                                            Spacer(modifier = Modifier.width(6.dp))
                                                                            TextButton(
                                                                                onClick = { showFullVoicePromptDialog = false },
                                                                                modifier = Modifier
                                                                                    .heightIn(min = 48.dp)
                                                                                    .testTag("btn_close_full_voice_prompt")
                                                                            ) {
                                                                                Text(
                                                                                    text = "Close",
                                                                                    color = Color(0xFFE2E8F0),
                                                                                    fontWeight = FontWeight.SemiBold
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        previewAttachment?.let { att ->
                                                            FullscreenImagePreviewDialog(
                                                                attachment = att,
                                                                onDismiss = { previewAttachment = null }
                                                            )
                                                        }
                                                    } else {
                                                        // AI message: left-aligned, completely borderless / boxless directly on OLED black background
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 2.dp, vertical = 4.dp)
                                                        ) {
                                                            SelectionContainer {
                                                                com.jarvisai.assistant.ui.screens.RenderMessageContentWithCodeBlocks(
                                                                    content = message.content,
                                                                    textColor = if (message.isError) Color(0xFFFCA5A5) else Color(0xFFE5E7EB),
                                                                    fillMaxWidth = true,
                                                                    onCopy = { codeText ->
                                                                        copyTextWithToast(context, "Code", codeText)
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }

                                                    if (!isUser && message.content.isNotBlank()) {
                                                        val usage = remember(message) {
                                                            com.jarvisai.assistant.ui.components.parseTokenUsageFromMessage(message)
                                                        }
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            com.jarvisai.assistant.ui.components.TokenUsageTelemetryPill(
                                                                usage = usage,
                                                                onViewPayload = {
                                                                    inspectingTokenUsage = usage
                                                                }
                                                            )

                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                val precedingUserPrompt = remember(timelineItems, index) {
                                                                    var prompt: String? = null
                                                                    for (i in (index - 1) downTo 0) {
                                                                        val prevMsg = (timelineItems[i] as? ChatTimelineItem.PersistentMessage)?.message
                                                                        if (prevMsg != null && prevMsg.role.equals("user", ignoreCase = true)) {
                                                                            prompt = com.jarvisai.assistant.model.ChatAttachmentCodec
                                                                                .stripForCopy(prevMsg.content)
                                                                                .trim()
                                                                            break
                                                                        }
                                                                    }
                                                                    prompt
                                                                }
                                                                if (!precedingUserPrompt.isNullOrBlank()) {
                                                                    Surface(
                                                                        shape = RoundedCornerShape(10.dp),
                                                                        color = Color(0xFF111827),
                                                                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                                                        modifier = Modifier
                                                                            .clip(RoundedCornerShape(10.dp))
                                                                            .clickable(enabled = !isChatLoading) {
                                                                                viewModel.sendChatMessage(precedingUserPrompt)
                                                                            }
                                                                            .testTag("retry_ai_response_btn_${message.id}")
                                                                    ) {
                                                                        Row(
                                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            Icon(
                                                                                imageVector = Icons.Default.Refresh,
                                                                                contentDescription = "Regenerate AI response",
                                                                                tint = Color(0xFF94A3B8),
                                                                                modifier = Modifier.size(13.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                }

                                                                Surface(
                                                                    shape = RoundedCornerShape(10.dp),
                                                                    color = if (copiedMessageFeedback) Color(0xFF064E3B).copy(alpha = 0.55f) else Color(0xFF111827),
                                                                    border = BorderStroke(
                                                                        1.dp,
                                                                        if (copiedMessageFeedback) Color(0xFF10B981).copy(alpha = 0.6f)
                                                                        else Color(0xFF1E293B)
                                                                    ),
                                                                    modifier = Modifier
                                                                        .clip(RoundedCornerShape(10.dp))
                                                                        .clickable {
                                                                            copyTextWithToast(context, "Response", message.content)
                                                                            copiedMessageFeedback = true
                                                                        }
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = if (copiedMessageFeedback) Icons.Default.Check else Icons.Default.ContentCopy,
                                                                            contentDescription = "Copy AI response",
                                                                            tint = if (copiedMessageFeedback) Color(0xFF10B981) else Color(0xFF94A3B8),
                                                                            modifier = Modifier.size(13.dp)
                                                                        )
                                                                        Spacer(modifier = Modifier.width(5.dp))
                                                                        Text(
                                                                            text = if (copiedMessageFeedback) "Copied" else "Copy",
                                                                            color = if (copiedMessageFeedback) Color(0xFF10B981) else Color(0xFFCBD5E1),
                                                                            fontSize = 11.sp,
                                                                            fontWeight = FontWeight.Medium
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                                } // end PersistentMessage
                                            } // end when (item)
                                        } // end items(timelineItems)

                                        // Autonomous Screen Operator Execution Card (stays at active bottom when running)
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

                                         // Live AntiGem-Style Chronological Activity (Thinking + Borderless Tool Rows)
                                         if (!currentTurnThought.isNullOrBlank() || currentTurnToolCalls.isNotEmpty()) {
                                             item(key = "antigravity_live_trajectory") {
                                                 val hasLiveInterleavedThoughts = currentTurnToolCalls.any { it.name == "__thought__" }
                                                 Column(
                                                     modifier = Modifier
                                                         .fillMaxWidth()
                                                         .padding(vertical = 2.dp)
                                                 ) {
                                                     if (!hasLiveInterleavedThoughts && !currentTurnThought.isNullOrBlank()) {
                                                         com.jarvisai.assistant.ui.components.ThinkingAccordion(
                                                             thoughtText = currentTurnThought ?: "",
                                                             isStreaming = isChatLoading
                                                         )
                                                     }

                                                     com.jarvisai.assistant.ui.antigravity.AntigravityLiveToolCallsSection(
                                                         isAntigravityActive = isAntigravityActive,
                                                         isChatLoading = isChatLoading,
                                                         toolCalls = currentTurnToolCalls,
                                                         onSubmitQuestionnaire = { tCall, responses ->
                                                             viewModel.submitQuestionnaireAnswers(tCall, responses)
                                                         },
                                                         onSkipQuestionnaire = { tCall ->
                                                             viewModel.skipQuestionnaire(tCall)
                                                         }
                                                     )
                                                 }
                                             }
                                         }

                                         // Streaming Partial Response: left-aligned, flat borderless markdown directly on background
                                         val partialText = streamingPartial.orEmpty()
                                         if (partialText.isNotBlank()) {
                                             item(key = "streaming_partial") {
                                                 Box(
                                                     modifier = Modifier
                                                         .fillMaxWidth()
                                                         .padding(horizontal = 2.dp, vertical = 4.dp)
                                                 ) {
                                                     SelectionContainer {
                                                         com.jarvisai.assistant.ui.screens.RenderMessageContentWithCodeBlocks(
                                                             content = partialText,
                                                             textColor = Color(0xFFE5E7EB),
                                                             fillMaxWidth = true,
                                                             onCopy = { codeText ->
                                                                 copyTextWithToast(context, "Code", codeText)
                                                             }
                                                         )
                                                     }
                                                 }
                                             }
                                         }

                                         // Rotating sparkle spinner always stays at the very bottom while agent is actively working
                                         if (isChatLoading) {
                                             item(key = "antigem_bottom_working_spinner") {
                                                 com.jarvisai.assistant.ui.components.AntiGemWorkingSpinner(
                                                     color = Color(0xFF60A5FA)
                                                 )
                                             }
                                         }
                                    }
                                }
                        }

                        // 2. PERSISTENT SORA IDE STAGE LAYER
                        if (hasEverOpenedIde || activeScreen == "ide") {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(1f)
                                    .graphicsLayer {
                                        alpha = if (activeScreen == "ide") 1f else 0f
                                        translationX = if (activeScreen == "ide") 0f else 100000f
                                    }
                            ) {
                                IdeScreen(
                                    initialFile = selectedIdeFile,
                                    isActive = activeScreen == "ide",
                                    onNavigateBack = {
                                        previousActiveScreen = activeScreen
                                        activeScreen = "home"
                                    }
                                )
                            }
                        }

                        // 3. PERSISTENT AUTHENTIC NATIVE TERMUX TERMINAL LAYER
                        if (hasEverOpenedTerminal || activeScreen == "terminal") {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(1f)
                                    .graphicsLayer {
                                        alpha = if (activeScreen == "terminal") 1f else 0f
                                        translationX = if (activeScreen == "terminal") 0f else 100000f
                                    }
                            ) {
                                LocalTerminalContent(
                                    applySystemBarInsets = false,
                                    onClose = {
                                        previousActiveScreen = activeScreen
                                        activeScreen = "home"
                                    }
                                )
                            }
                        }

                        // 4. PERSISTENT FILE EXPLORER STAGE LAYER
                        if (hasEverOpenedFiles || activeScreen == "files") {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(1f)
                                    .graphicsLayer {
                                        alpha = if (activeScreen == "files") 1f else 0f
                                        translationX = if (activeScreen == "files") 0f else 100000f
                                    }
                            ) {
                                FileExplorerScreen(
                                    isActive = activeScreen == "files",
                                    onBack = {
                                        previousActiveScreen = activeScreen
                                        activeScreen = "home"
                                    },
                                    onOpenFileInIde = { file ->
                                        selectedIdeFile = file
                                        com.jarvisai.assistant.terminal.WorkspaceSyncManager.requestOpenFileInIde(file.absolutePath)
                                        previousActiveScreen = activeScreen
                                        activeScreen = "ide"
                                    },
                                    onOpenFolderInIde = { folder ->
                                        activeWorkspaceName = folder.name
                                        viewModel.selectWorkspaceDirectory(folder)
                                        workspacesRefreshKey++
                                        hasEverOpenedIde = true
                                        previousActiveScreen = activeScreen
                                        activeScreen = "ide"
                                    }
                                )
                            }
                        }

                        // 5. PERSISTENT LIVE BROWSER STAGE LAYER (NO graphicsLayer!)
                        // Kept persistently mounted in composition via zIndex without graphicsLayer so
                        // WebView NEVER detaches from Window or shrinks when switching to Chat (zero audio
                        // stutter/pause), while hardware SurfaceTexture <video> stays 100% intact (no black screen).
                        if (hasEverOpenedBrowser || activeScreen == "browser") {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(if (activeScreen == "browser") 5f else -1f)
                            ) {
                                BrowserScreen(
                                    isActive = activeScreen == "browser",
                                    onBack = {
                                        previousActiveScreen = activeScreen
                                        activeScreen = "home"
                                    },
                                    onSummarizeContent = { viewModel.summarizeWebContent(it) }
                                )
                            }
                        }

                        // 6. AUXILIARY & SUB-SCREENS
                        if (activeScreen !in setOf("home", "ide", "terminal", "files", "browser")) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(1f)
                            ) {
                                when (activeScreen) {
                            "adb_connect" -> AdbConnectScreen(onNavigateBack = { activeScreen = "home" })

                            "connect_pc" -> com.jarvisai.assistant.ui.screens.ConnectPcScreen(onNavigateBack = { activeScreen = "home" })

                            "plugins" -> PluginsManagementScreen(onNavigateBack = { activeScreen = "home" })

                            "notes" -> com.jarvisai.assistant.ui.screens.NotesScreen(onBack = { activeScreen = "home" })

                            "mini_apps" -> com.jarvisai.assistant.ui.screens.MiniAppsScreen(onBack = { activeScreen = "home" })

                            "run_manager" -> com.jarvisai.assistant.ui.screens.RunScreen(
                                onBack = { activeScreen = "home" },
                                onOpenPreviewUrl = { url ->
                                    com.jarvisai.assistant.browser.BrowserSessionManager.instance.init(context)
                                    com.jarvisai.assistant.browser.BrowserSessionManager.instance.openUrlFromUi(
                                        rawUrl = url,
                                        switchToBrowser = true
                                    )
                                }
                            )

                            "agent_hub" -> AgentHubScreen(
                                onBack = { activeScreen = "home" },
                                onRunMacro = { name, q ->
                                    viewModel.runAutonomousAgentGoal("$name: $q")
                                    activeScreen = "home"
                                }
                            )

                            "voice" -> {
                                val companionEmotionMood by viewModel.companionEmotionMood.collectAsStateWithLifecycle()
                                val availableVoiceModels by viewModel.availableVoiceModels.collectAsStateWithLifecycle()
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
                                    onTriggerProactiveCompanionNow = { viewModel.triggerProactiveCompanionNow() },
                                    availableVoiceModels = availableVoiceModels,
                                    onSelectVoiceModel = { viewModel.updateVoiceModel(it) },
                                    onToggleFullDuplexVoice = { viewModel.updateFullDuplexVoiceEnabled(it) }
                                )
                            }

                            "permissions" -> PermissionsCenterScreen(
                                isScreenSharing = isScreenSharing,
                                screenFramesSent = screenFramesSent,
                                onStartScreenShare = { code, data -> viewModel.startLiveScreenSharing(code, data) },
                                onStopScreenShare = { viewModel.stopLiveScreenSharing() },
                                onScreenShareDenied = { viewModel.onScreenSharePermissionDenied() },
                                onOpenTermuxSetup = {
                                    termuxDiagnosticsRefresh++
                                    showTermuxBridgeDialog = true
                                },
                                onBack = { activeScreen = "home" }
                            )

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
                                com.jarvisai.assistant.ui.settings.SettingsPreferencesScreen(
                                    settings = settings,
                                    userEmail = antigravityUserEmail.takeIf { it.isNotBlank() },
                                    initialSection = settingsInitialSection,
                                    onBack = { activeScreen = "home" },
                                    onOpenJarvisGeneralSettings = { activeScreen = "jarvis_settings" },
                                    onOpenApiVault = { activeScreen = "api_settings" },
                                    onOpenOnboarding = { showOnboardingWizard = true },
                                    onUpdateAntigravityExecutionMode = { viewModel.updateAntigravityExecutionMode(it) },
                                    onUpdateBackgroundScreenTtsEnabled = { viewModel.updateBackgroundScreenTtsEnabled(it) },
                                    onSelectCactusChatModel = { modelId, displayName ->
                                        activeModelName = displayName
                                        localTerminalAgentOverride = null
                                        viewModel.updateLlmProvider(com.jarvisai.assistant.model.LLMProvider.CACTUS_LOCAL)
                                        viewModel.updateTextModel(modelId)
                                    }
                                )
                            }

                            "cactus_manager" -> {
                                com.jarvisai.assistant.ui.settings.CactusManagerScreen(
                                    onBack = { activeScreen = "home" },
                                    onSelectCactusChatModel = { modelId, displayName ->
                                        activeModelName = displayName
                                        localTerminalAgentOverride = null
                                        viewModel.updateLlmProvider(com.jarvisai.assistant.model.LLMProvider.CACTUS_LOCAL)
                                        viewModel.updateTextModel(modelId)
                                    }
                                )
                            }

                            "jarvis_settings" -> {
                                BackHandler { activeScreen = "settings" }
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
                                    onBack = { activeScreen = "settings" },
                                    onUpdateAssistantName = { viewModel.updateAssistantName(it) },
                                    onUpdateLanguage = { viewModel.updateLanguage(it) },
                                    onUpdateVoiceGender = { viewModel.updateVoiceGender(it) },
                                    onUpdateVoicePersona = { viewModel.updateVoicePersona(it) },
                                    onUpdateTtsModel = { viewModel.updateTtsModel(it) },
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
                                    onUpdateBackgroundScreenTtsEnabled = { viewModel.updateBackgroundScreenTtsEnabled(it) },
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
                                onAnalyzeImage = { b64, mime, prompt, useCactus ->
                                    viewModel.analyzeVisionImage(b64, mime, prompt, useCactus)
                                },
                                onSpeakText = { viewModel.speakText(it) },
                                isAnalyzing = isVisionAnalyzing,
                                analysisResult = visionAnalysisResult,
                                errorMessage = visionError
                            )

                            "document" -> DocumentAssistantScreen(
                                onBack = { activeScreen = "home" },
                                onAnalyzeDocument = { text, name, prompt, useCactus ->
                                    viewModel.analyzeDocument(text, name, prompt, useCactus)
                                },
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
                                onNavigateToGeneralSettings = {
                                    settingsInitialSection = com.jarvisai.assistant.ui.settings.SettingsSection.MAIN
                                    activeScreen = "settings"
                                },
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
                            }
                        }

                        // Floating 'View Live' browser banner removed per user preference (use FloatingSwitcherWidget to jump to Browser)


                        // Agentra Edge Dock / Floating Workspace Switcher (always above persistent BrowserScreen at zIndex 5f)
                        if (activeScreen != "permissions") {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(10f)
                            ) {
                                FloatingSwitcherWidget(
                                    activeScreen = activeScreen,
                                    onNavigate = { route ->
                                        if (route != activeScreen) {
                                            previousActiveScreen = activeScreen
                                            activeScreen = route
                                        }
                                    },
                                    onNavigateBack = {
                                        val target = previousActiveScreen ?: "home"
                                        previousActiveScreen = activeScreen
                                        activeScreen = target
                                    }
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(12f)
                        ) {
                            com.jarvisai.assistant.ui.antigravity.AntigravityStageOverlaysHost(
                                showArtifactsDrawer = showArtifactsDrawer,
                                showInboxSheet = showAntigravityInboxSheet,
                                showRulesSheet = showAntigravityRulesSheet,
                                generatedArtifacts = generatedArtifacts,
                                structuredArtifacts = allStructuredArtifacts,
                                pendingPlanArtifacts = pendingPlanArtifacts,
                                sessions = sessions,
                                archivedSessions = archivedSessions,
                                subagentTasks = subagentTasks,
                                currentSessionId = currentSessionId,
                                activeWorkspacePath = activeWorkspacePath,
                                agentMode = settings.antigravityAgentMode,
                                autonomyPolicy = settings.antigravityAutonomyPolicy,
                                onSelectSession = { sid ->
                                    viewModel.selectChatSession(sid)
                                    activeScreen = "home"
                                },
                                onSelectAgentMode = { viewModel.updateAntigravityAgentMode(it) },
                                onSelectAutonomyPolicy = { viewModel.updateAntigravityAutonomyPolicy(it) },
                                onApprovePlan = { approveAntigravityPlan(it) },
                                onOpenArtifactFileInIde = { targetFile ->
                                    selectedIdeFile = targetFile
                                    activeScreen = "ide"
                                },
                                onTriggerSkillWorkflow = { skill ->
                                    promptInput = "Run agent skill `${skill.name}` from `${skill.filePath}`: "
                                },
                                onRulesUpdated = { antigravityRulesRevision++ },
                                onCloseArtifactsDrawer = { showArtifactsDrawer = false },
                                onDismissInbox = { showAntigravityInboxSheet = false },
                                onDismissRules = { showAntigravityRulesSheet = false }
                            )
                        }
                    }
                }
            }

            // Window-level Modal Dialogs & Sheets (kept outside Scaffold content lambda to stay well under JVM 64KB method limit)
            com.jarvisai.assistant.ui.components.WorkspaceSelectorOverlaysHost(
                showWorkspaceModal = showWorkspaceSelectorModal,
                showFolderBrowser = showProjectFolderBrowser,
                workspaces = workspaces,
                activeWorkspacePath = activeWorkspacePath,
                isGeneralActive = activeWorkspaceName.isNullOrBlank() && activeChatFolder.equals("General", ignoreCase = true),
                onRefreshWorkspaces = { workspacesRefreshKey++ },
                onSelectGeneral = {
                    activeWorkspaceName = null
                    viewModel.selectWorkspaceDirectory(null)
                    workspacesRefreshKey++
                },
                onSelectWorkspace = { dir ->
                    activeWorkspaceName = dir.name
                    viewModel.selectWorkspaceDirectory(dir)
                    workspacesRefreshKey++
                },
                onOpenFolderBrowser = { showProjectFolderBrowser = true },
                onOpenFileInIde = { file ->
                    hasEverOpenedIde = true
                    selectedIdeFile = file
                    com.jarvisai.assistant.terminal.WorkspaceSyncManager.requestOpenFileInIde(file.absolutePath)
                    previousActiveScreen = activeScreen
                    activeScreen = "ide"
                },
                onDismissWorkspaceModal = { showWorkspaceSelectorModal = false },
                onDismissFolderBrowser = { showProjectFolderBrowser = false }
            )

            if (showTerminalDialog) {
                LocalTerminalDialog(
                    onDismiss = { showTerminalDialog = false }
                )
            }

            if (showRootfsInstallerDialog) {
                com.jarvisai.assistant.ui.components.LinuxRootfsManagerDialog(
                    onDismiss = { showRootfsInstallerDialog = false }
                )
            }

            if (showThinkingSelectorSheet) {
                com.jarvisai.assistant.ui.components.ThinkingSelectorBottomSheet(
                    currentPreference = currentThinkingPreference,
                    activeModelName = activeModelName,
                    modelSupportsThinking = activeModelSupportsThinking,
                    onPreferenceSelected = { pref ->
                        viewModel.setThinkingLevel(pref.level)
                    },
                    onDismiss = { showThinkingSelectorSheet = false }
                )
            }

            com.jarvisai.assistant.ui.components.AntigravityAuthDialogHost(
                visible = showAntigravityLoginDialog,
                viewModel = viewModel,
                selectedModelId = settings.textModel,
                currentThinkingLevel = currentThinkingPreference.level,
                onSelectModel = { modelInfo ->
                    localTerminalAgentOverride = null
                    activeModelName = "☁️ Antigravity • ${modelInfo.displayName}"
                    viewModel.updateLlmProvider(com.jarvisai.assistant.model.LLMProvider.ANTIGRAVITY)
                    viewModel.updateTextModel("agy:${modelInfo.id}")
                    orchestrator.agentOrchestrator.selectAgent(ActiveCodingAgent.GEMINI_DIRECT)
                },
                onDismiss = { showAntigravityLoginDialog = false }
            )

            inspectingTokenUsage?.let { usage ->
                com.jarvisai.assistant.ui.components.TokenUsageDetailsSheet(
                    usage = usage,
                    onInspectRawPayload = {
                        inspectingRawPayload = org.json.JSONObject().apply {
                            put("model", usage.model ?: "gemini-3.8-flash")
                            put("promptTokens", usage.promptTokens)
                            put("outputTokens", usage.outputTokens)
                            put("cachedTokens", usage.cachedTokens)
                            put("totalTokens", usage.totalTokens)
                            put("durationMs", usage.durationMs)
                        }.toString(2)
                    },
                    onDismiss = { inspectingTokenUsage = null }
                )
            }

            inspectingRawPayload?.let { payload ->
                com.jarvisai.assistant.ui.components.RawPayloadDialog(
                    payloadJson = payload,
                    onDismiss = { inspectingRawPayload = null }
                )
            }

            if (showOnboardingWizard) {
                OnboardingWizardDialog(
                    onComplete = {
                        onboardingPrefs.edit().putBoolean("onboarding_completed", true).apply()
                        showOnboardingWizard = false
                    },
                    onStartAntigravityLogin = {
                        showOnboardingWizard = false
                        showAntigravityLoginDialog = true
                    }
                )
            }

            if (!showOnboardingWizard && showTerminalLauncherOnboarding) {
                com.jarvisai.assistant.ui.onboarding.TerminalLauncherOnboardingDialog(
                    onDismiss = {
                        com.jarvisai.assistant.data.preferences.TerminalLauncherManager.markPromptShown(context)
                        authPrefs.setHasSeenTerminalLauncherOnboarding(true)
                        showTerminalLauncherOnboarding = false
                    },
                    onApplied = {
                        authPrefs.setHasSeenTerminalLauncherOnboarding(true)
                        showTerminalLauncherOnboarding = false
                    }
                )
            }

            latestReleaseInfo?.let { release ->
                if (release.isNewer) {
                    com.jarvisai.assistant.ui.components.GitHubReleaseUpdateDialog(
                        release = release,
                        updateManager = updateManager,
                        isDownloadingUpdate = isDownloadingUpdate,
                        updateDownloadProgress = updateDownloadProgress,
                        updateDownloadedMbText = updateDownloadedMbText,
                        updateErrorMessage = updateErrorMessage,
                        pendingInstallApkFile = pendingInstallApkFile,
                        onSetDownloading = { isDownloadingUpdate = it },
                        onSetProgress = { percent, mbText ->
                            updateDownloadProgress = percent
                            updateDownloadedMbText = mbText
                        },
                        onSetError = { updateErrorMessage = it },
                        onSetPendingApk = { pendingInstallApkFile = it },
                        onDismiss = {
                            latestReleaseInfo = null
                            updateErrorMessage = null
                        }
                    )
                }
            }

            if (showAttachmentSheet) {
                com.jarvisai.assistant.ui.components.AttachmentPickerBottomSheet(
                    onPickPhotos = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onPickVideos = {
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onPickFiles = {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    onDismiss = { showAttachmentSheet = false }
                )
            }

            if (showTermuxBridgeDialog) {
                com.jarvisai.assistant.ui.components.TermuxBridgeSetupDialog(
                    refreshKey = termuxDiagnosticsRefresh,
                    isLinuxRootfsInstalled = isLinuxRootfsInstalled,
                    onRefreshDiagnostics = { termuxDiagnosticsRefresh++ },
                    onRequestRunCommandPermission = {
                        lastTermuxPermLaunchMs = System.currentTimeMillis()
                        termuxPermissionLauncher.launch(
                            com.jarvisai.assistant.termux.JarvisTermuxBridge.TERMUX_RUN_COMMAND_PERMISSION
                        )
                    },
                    onOpenWirelessAdb = {
                        showTermuxBridgeDialog = false
                        previousActiveScreen = activeScreen
                        activeScreen = "adb_connect"
                    },
                    onDismiss = { showTermuxBridgeDialog = false }
                )
            }

            if (showLocalServerLogsDialog) {
                com.jarvisai.assistant.ui.components.LocalServerLogsDialog(
                    onDismiss = { showLocalServerLogsDialog = false }
                )
            }

            AgyVoiceCallScreen(
                agentWorking = isAgyVoiceAgentWorking && agyVoiceCallState.conversationId == currentExternalCascadeId,
                onOpenAgySettings = {
                    if (agyVoiceCallState.phase == AgyVoiceCall.Phase.FAILED) {
                        AgyVoiceCall.end()
                    } else {
                        AgyVoiceCall.setMinimized(true)
                    }
                    activeScreen = "settings"
                },
                onSignInAgain = {
                    AgyVoiceCall.end()
                    showAntigravityLoginDialog = true
                }
            )

            if (showAgyVoicePicker) {
                AgyVoicePickerDialog(
                    initialVoice = AgyVoicePreferences.voice.value,
                    onChoose = { voice ->
                        AgyVoicePreferences.setVoice(voice)
                        AgyVoicePreferences.setPicked()
                        showAgyVoicePicker = false
                        triggerAgyVoiceCallFlow()
                    },
                    onDismiss = { showAgyVoicePicker = false }
                )
            }

            openAgyTranscriptRecord?.let { rec ->
                AgyVoiceTranscriptSheet(
                    record = rec,
                    onDismiss = { openAgyTranscriptRecord = null }
                )
            }
        }
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

@Composable
private fun AiStudioInlineSetupCard(
    onSaveKey: (String) -> Unit,
    onOpenFullSettings: () -> Unit
) {
    val context = LocalContext.current
    val themeColors = com.example.ui.theme.JarvisTheme.colors
    var apiKeyInput by rememberSaveable { mutableStateOf("") }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = themeColors.cardSurface,
        border = BorderStroke(1.dp, themeColors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Google AI Studio Setup",
                color = themeColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Add a Gemini API key (comma or newline separated for multi-key rotation).",
                color = themeColors.textSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                placeholder = {
                    Text(
                        text = "AIzaSy... (or multiple keys separated by comma)",
                        color = themeColors.textMuted,
                        fontSize = 12.sp
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = themeColors.textPrimary,
                    unfocusedTextColor = themeColors.textPrimary,
                    focusedBorderColor = themeColors.accent,
                    unfocusedBorderColor = themeColors.border,
                    focusedContainerColor = themeColors.inputBackground,
                    unfocusedContainerColor = themeColors.surfaceElevated
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        runCatching {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://aistudio.google.com/app/apikey")
                            )
                            context.startActivity(intent)
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, themeColors.border),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Get API Key", color = themeColors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = {
                        val trimmed = apiKeyInput.trim()
                        if (trimmed.isNotBlank()) {
                            onSaveKey(trimmed)
                        } else {
                            onOpenFullSettings()
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = themeColors.accent),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save & Connect", color = themeColors.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun copyTextWithToast(context: Context, label: String, text: String) {
    if (text.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun resolveTopBarModelDisplay(
    modelId: String,
    provider: com.jarvisai.assistant.model.LLMProvider,
    localAgentOverride: String? = null,
    antigravityModels: List<com.jarvisai.assistant.data.remote.AntigravityModelInfo> = emptyList()
): String {
    if (!localAgentOverride.isNullOrBlank()) return localAgentOverride
    val clean = modelId.trim()
    val rawAgyId = clean.removePrefix("agy:")
    val bare = rawAgyId.lowercase()
    return when {
        provider == com.jarvisai.assistant.model.LLMProvider.CACTUS_LOCAL || clean.startsWith("cactus:", ignoreCase = true) -> {
            val spec = com.jarvisai.assistant.cactus.CactusModelCatalog.findById(clean)
            "Cactus • ${spec?.displayName ?: clean.removePrefix("cactus:")}"
        }
        provider == com.jarvisai.assistant.model.LLMProvider.ANTIGRAVITY || clean.startsWith("agy:", ignoreCase = true) -> {
            val matched = antigravityModels.firstOrNull {
                it.id.equals(rawAgyId, ignoreCase = true) ||
                    it.thinkingVariantIds.values.any { v -> v.equals(rawAgyId, ignoreCase = true) }
            }
            if (matched != null && matched.displayName.isNotBlank()) {
                "Antigravity • ${matched.displayName}"
            } else {
                when {
                    bare.contains("orchestrator-carbon") -> "Antigravity • Orchestrator (Carbon)"
                    bare.contains("orchestrator-argon") -> "Antigravity • Orchestrator (Argon)"
                    bare.contains("carbon") -> "Antigravity • Gemini 4 Carbon"
                    bare.contains("argon") -> "Antigravity • Gemini 4 Argon"
                    bare.contains("hydrogen") -> "Antigravity • Gemini 4 Hydrogen"
                    bare.contains("3.8") -> "Antigravity • Gemini 3.8 Flash"
                    bare.contains("3.7") -> "Antigravity • Gemini 3.7 Flash"
                    bare.contains("3.6") -> "Antigravity • Gemini 3.6 Flash"
                    bare.contains("3.1-pro") || bare.contains("3.1 pro") -> "Antigravity • Gemini 3.1 Pro"
                    bare.contains("opus-5.5") || bare.contains("opus 5.5") -> "Antigravity • Claude Opus 5.5"
                    bare.contains("sonnet-5.5") || bare.contains("sonnet 5.5") -> "Antigravity • Claude Sonnet 5.5"
                    bare.contains("sonnet-4.6") || bare.contains("sonnet 4.6") -> "Antigravity • Claude Sonnet 4.6"
                    bare.contains("claude") || bare.contains("sonnet") || bare.contains("opus") -> "Antigravity • Claude Thinking"
                    bare.contains("gpt") -> "Antigravity • GPT-OSS 120B"
                    else -> "Antigravity • $rawAgyId"
                }
            }
        }
        bare == "gemini-4-hydrogen" -> "Google AI Studio • Gemini 4 Hydrogen"
        bare == "gemini-4-argon" -> "Google AI Studio • Gemini 4 Argon"
        bare == "gemini-4-carbon" -> "Google AI Studio • Gemini 4 Carbon"
        bare == "gemini-3.8-flash" -> "Google AI Studio • Gemini 3.8 Flash"
        bare == "gemini-3.5-flash-lite" -> "Google AI Studio • Gemini 3.5 Lite"
        bare == "gemini-3.1-flash-lite" -> "Google AI Studio • Gemini 3.1 Lite"
        bare == "gemini-2.5-pro" -> "Google AI Studio • Gemini 2.5 Pro"
        bare.contains("live") -> "Google AI Studio • Live Voice"
        bare.isNotBlank() -> "Google AI Studio • $clean"
        else -> "Google AI Studio • Gemini 3.8 Flash"
    }
}


