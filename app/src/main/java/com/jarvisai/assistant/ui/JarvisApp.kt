package com.jarvisai.assistant.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.JarvisHudTheme
import com.jarvisai.assistant.ui.components.JarvisModernBottomNavigation
import com.jarvisai.assistant.ui.components.JarvisPremiumBackground
import com.jarvisai.assistant.ui.screens.ApiSettingsScreen
import com.jarvisai.assistant.ui.screens.DocumentAssistantScreen
import com.jarvisai.assistant.ui.screens.GeneralSettingsScreen
import com.jarvisai.assistant.ui.screens.HomeDashboardScreen
import com.jarvisai.assistant.ui.screens.LiveVoiceScreen
import com.jarvisai.assistant.ui.screens.MemoryCenterScreen
import com.jarvisai.assistant.ui.screens.MemoryReviewScreen
import com.jarvisai.assistant.ui.screens.NotificationAssistantScreen
import com.jarvisai.assistant.ui.screens.PhoneControlsScreen
import com.jarvisai.assistant.ui.screens.TextChatScreen
import com.jarvisai.assistant.ui.screens.VisionScreen
import com.jarvisai.assistant.ui.screens.WorkflowScreen
import com.jarvisai.assistant.viewmodel.JarvisViewModel

enum class JarvisDestination(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Default.Home),
    CHAT("chat", "Chat", Icons.AutoMirrored.Filled.Chat),
    VOICE("voice", "Live Voice", Icons.Default.Mic),
    MEMORY("memory", "Memory", Icons.Default.Memory),
    CONTROLS("controls", "Controls", Icons.Default.Build),
    API_SETTINGS("api_settings", "API", Icons.Default.Build),
    GENERAL_SETTINGS("general_settings", "Settings", Icons.Default.Settings),
    VISION("vision", "Vision", Icons.Default.CameraAlt),
    DOCUMENT("document", "Docs", Icons.Default.Description),
    WORKFLOW("workflow", "Workflow", Icons.Default.AutoMode),
    NOTIFICATIONS("notifications", "Alerts", Icons.Default.Notifications),
    MEMORY_REVIEW("memory_review", "Timeline", Icons.Default.History)
}

@Composable
fun JarvisApp(viewModel: JarvisViewModel) {
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val status by viewModel.assistantStatus.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKeyFlow.collectAsStateWithLifecycle()
    val isKeystoreBacked by viewModel.isUsingKeystoreEncryption.collectAsStateWithLifecycle()

    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsStateWithLifecycle()
    val ttsErrorMessage by viewModel.ttsErrorMessage.collectAsStateWithLifecycle()
    val memorySavedNotification by viewModel.memorySavedNotification.collectAsStateWithLifecycle()

    val sessions by viewModel.chatSessions.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val currentMessages by viewModel.currentMessages.collectAsStateWithLifecycle()
    val streamingPartial by viewModel.streamingPartialResponse.collectAsStateWithLifecycle()
    val isChatLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val lastFailedPrompt by viewModel.lastFailedPrompt.collectAsStateWithLifecycle()

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
    val pendingSafetyConfirmation by viewModel.pendingSafetyConfirmation.collectAsStateWithLifecycle()
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

    val context = androidx.compose.ui.platform.LocalContext.current
    var biometricAuthError by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }

    val credentialFallbackLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            biometricAuthError = null
            viewModel.resolveSafetyConfirmation(true)
        } else {
            biometricAuthError = "Device credential verification was cancelled."
        }
    }

    var currentDestination by rememberSaveable { mutableStateOf(JarvisDestination.HOME) }

    val bottomNavDestinations = listOf(
        JarvisDestination.HOME,
        JarvisDestination.CHAT,
        JarvisDestination.VOICE,
        JarvisDestination.MEMORY,
        JarvisDestination.GENERAL_SETTINGS
    )

    JarvisHudTheme(themeMode = settings.themeMode) {
        JarvisPremiumBackground {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    JarvisModernBottomNavigation(
                        destinations = bottomNavDestinations,
                        currentDestination = currentDestination,
                        onNavigate = { currentDestination = it }
                    )
                }
            ) { innerPadding ->
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isAutonomousTaskOrVoiceActive) {
                        com.jarvisai.assistant.ui.components.JarvisEmergencyStopBanner(
                            onEmergencyStop = { viewModel.triggerEmergencyStop() }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        AnimatedContent(
                            targetState = currentDestination,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)) togetherWith
                                    fadeOut(animationSpec = tween(180))
                            },
                            label = "jarvisScreenTransition"
                        ) { destination ->
                            when (destination) {
                                JarvisDestination.HOME -> {
                                    HomeDashboardScreen(
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
                                        onNavigateToVoice = { currentDestination = JarvisDestination.VOICE },
                                        onNavigateToChat = { currentDestination = JarvisDestination.CHAT },
                                        onNavigateToMemory = { currentDestination = JarvisDestination.MEMORY },
                                        onNavigateToControls = { currentDestination = JarvisDestination.CONTROLS },
                                        onNavigateToApiSettings = { currentDestination = JarvisDestination.API_SETTINGS },
                                        onNavigateToGeneralSettings = { currentDestination = JarvisDestination.GENERAL_SETTINGS },
                                        onNavigateToVision = { currentDestination = JarvisDestination.VISION },
                                        onNavigateToDocument = { currentDestination = JarvisDestination.DOCUMENT },
                                        onNavigateToWorkflow = { currentDestination = JarvisDestination.WORKFLOW },
                                        onNavigateToNotifications = { currentDestination = JarvisDestination.NOTIFICATIONS },
                                        onNavigateToMemoryReview = { currentDestination = JarvisDestination.MEMORY_REVIEW },
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

                                JarvisDestination.CHAT -> {
                                    TextChatScreen(
                                        settings = settings,
                                        status = status,
                                        hasApiKey = apiKey.isNotBlank(),
                                        sessions = sessions,
                                        currentSessionId = currentSessionId,
                                        messages = currentMessages,
                                        streamingPartialResponse = streamingPartial,
                                        isLoading = isChatLoading,
                                        chatError = chatError,
                                        lastFailedPrompt = lastFailedPrompt,
                                        memorySavedNotification = memorySavedNotification,
                                        onDismissMemoryNotification = { viewModel.dismissMemorySavedNotification() },
                                        onBack = { currentDestination = JarvisDestination.HOME },
                                        onSelectLanguage = { viewModel.updateLanguage(it) },
                                        onSendMessage = { viewModel.sendChatMessage(it) },
                                        onRetryFailed = { viewModel.retryLastFailedMessage() },
                                        onNewChat = { viewModel.startNewChat() },
                                        onSelectSession = { viewModel.selectChatSession(it) },
                                        onDeleteSession = { viewModel.deleteChatSession(it) },
                                        onClearAllHistory = { viewModel.clearAllChatHistory() },
                                        onSaveApprovedMemory = { title, content, cat ->
                                            viewModel.saveApprovedMemory(title, content, cat)
                                        },
                                        onNavigateToApiSettings = { currentDestination = JarvisDestination.API_SETTINGS }
                                    )
                                }

                                JarvisDestination.VOICE -> {
                                    LiveVoiceScreen(
                                        settings = settings,
                                        status = status,
                                        liveUiStatus = liveUiStatus,
                                        hasApiKey = apiKey.isNotBlank(),
                                        isSessionActive = isLiveVoiceActive,
                                        isMicMuted = isMicMuted,
                                        audioLevel = liveAudioLevel,
                                        connectionLabel = liveConnectionLabel,
                                        errorMessage = liveVoiceError,
                                        transcripts = liveTranscripts,
                                        isScreenSharing = isScreenSharing,
                                        screenFramesSent = screenFramesSent,
                                        memorySavedNotification = memorySavedNotification,
                                        onBack = { currentDestination = JarvisDestination.HOME },
                                        onSelectLanguage = { viewModel.updateLanguage(it) },
                                        onStartSession = { viewModel.startLiveVoiceSession() },
                                        onStopSession = { viewModel.stopLiveVoiceSession() },
                                        onToggleMute = { viewModel.toggleLiveVoiceMute() },
                                        onInterrupt = { viewModel.interruptLiveVoice() },
                                        onSendLiveText = { viewModel.sendLiveVoiceTextPrompt(it) },
                                        onExecuteQuickPhoneCommand = { viewModel.executeVoicePhoneCommand(it) },
                                        onStartScreenShare = { code, data ->
                                            viewModel.startLiveScreenSharing(code, data)
                                        },
                                        onStopScreenShare = { viewModel.stopLiveScreenSharing() },
                                        onScreenShareDenied = { viewModel.onScreenSharePermissionDenied() },
                                        onNavigateToApiSettings = { currentDestination = JarvisDestination.API_SETTINGS }
                                    )
                                }

                                JarvisDestination.MEMORY -> {
                                    MemoryCenterScreen(
                                        settings = settings,
                                        status = status,
                                        memories = filteredMemories,
                                        totalMemoriesCount = allMemories.size,
                                        searchQuery = memorySearchQuery,
                                        selectedCategory = selectedMemoryCategory,
                                        onBack = { currentDestination = JarvisDestination.HOME },
                                        onSearchQueryChange = { viewModel.setMemorySearchQuery(it) },
                                        onSelectCategory = { viewModel.setSelectedMemoryCategory(it) },
                                        onToggleMemoryEnabled = { viewModel.updateMemoryEnabled(it) },
                                        onSaveMemory = { title, content, cat, tags, pinned ->
                                            viewModel.saveApprovedMemory(title, content, cat, tags, pinned)
                                        },
                                        onUpdateMemory = { viewModel.updateMemory(it) },
                                        onTogglePinMemory = { viewModel.toggleMemoryPinned(it) },
                                        onDeleteMemory = { viewModel.deleteMemory(it) },
                                        onClearAllMemories = { viewModel.clearAllMemories() }
                                    )
                                }

                                JarvisDestination.CONTROLS -> {
                                    PhoneControlsScreen(
                                        settings = settings,
                                        status = status,
                                        isFlashlightOn = isFlashlightOn,
                                        hasFlashlightHardware = hasFlashlightHardware,
                                        installedApps = filteredInstalledApps,
                                        appSearchQuery = appSearchQuery,
                                        reminders = reminders,
                                        actionFeedback = phoneControlFeedback,
                                        onBack = { currentDestination = JarvisDestination.HOME },
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
                                }

                                JarvisDestination.API_SETTINGS -> {
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
                                        onBack = { currentDestination = JarvisDestination.HOME },
                                        onSaveOrUpdateKey = { viewModel.saveApiKey(it) },
                                        onDeleteKey = { viewModel.deleteApiKey() },
                                        onTestConnection = { viewModel.testGeminiConnection(it) },
                                        onSyncModelsFromApi = { viewModel.syncSupportedModelsFromApi(it) },
                                        onSelectTextModel = { viewModel.updateTextModel(it) },
                                        onSelectVoiceModel = { viewModel.updateVoiceModel(it) }
                                    )
                                }

                                JarvisDestination.GENERAL_SETTINGS -> {
                                    val companionModeState by viewModel.companionModeState.collectAsStateWithLifecycle()
                                    val companionModeStatusMessage by viewModel.companionModeStatusMessage.collectAsStateWithLifecycle()
                                    val wakeWordState by viewModel.wakeWordState.collectAsStateWithLifecycle()
                                    val wakeWordError by viewModel.wakeWordError.collectAsStateWithLifecycle()
                                    GeneralSettingsScreen(
                                        settings = settings,
                                        status = status,
                                        sessionsCount = sessions.size,
                                        memoriesCount = allMemories.size,
                                        isTtsSpeaking = isTtsSpeaking,
                                        ttsErrorMessage = ttsErrorMessage,
                                        onBack = { currentDestination = JarvisDestination.HOME },
                                        onUpdateAssistantName = { viewModel.updateAssistantName(it) },
                                        onUpdateLanguage = { viewModel.updateLanguage(it) },
                                        onUpdateVoiceGender = { viewModel.updateVoiceGender(it) },
                                        onUpdateVoicePersona = { viewModel.updateVoicePersona(it) },
                                        onPreviewVoice = { gender, persona ->
                                            viewModel.previewVoiceSelection(gender, persona)
                                        },
                                        onStopVoicePreview = { viewModel.stopSpeaking() },
                                        onUpdateVoiceSpeed = { viewModel.updateVoiceSpeed(it) },
                                        onUpdateVoicePitch = { viewModel.updateVoicePitch(it) },
                                        onUpdateAutoSpeakChat = { viewModel.updateAutoSpeakChatReplies(it) },
                                        onUpdateFloatingOverlay = { viewModel.updateFloatingOverlayEnabled(it) },
                                        onUpdateThemeMode = { viewModel.updateThemeMode(it) },
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
                                        onUpdateMaxAutonomousSteps = { viewModel.updateMaxAutonomousSteps(it) }
                                    )
                                }

                                JarvisDestination.VISION -> {
                                    VisionScreen(
                                        onBack = { currentDestination = JarvisDestination.HOME },
                                        onAnalyzeImage = { b64, mime, prompt ->
                                            viewModel.analyzeVisionImage(b64, mime, prompt)
                                        },
                                        onSpeakText = { viewModel.speakText(it) },
                                        isAnalyzing = isVisionAnalyzing,
                                        analysisResult = visionAnalysisResult,
                                        errorMessage = visionError
                                    )
                                }

                                JarvisDestination.DOCUMENT -> {
                                    DocumentAssistantScreen(
                                        onBack = { currentDestination = JarvisDestination.HOME },
                                        onAnalyzeDocument = { text, name, prompt ->
                                            viewModel.analyzeDocument(text, name, prompt)
                                        },
                                        onSpeakText = { viewModel.speakText(it) },
                                        isAnalyzing = isDocumentAnalyzing,
                                        analysisResult = documentAnalysisResult,
                                        errorMessage = documentError
                                    )
                                }

                                JarvisDestination.WORKFLOW -> {
                                    WorkflowScreen(
                                        onBack = { currentDestination = JarvisDestination.HOME },
                                        workflows = allWorkflows,
                                        executionLogs = workflowExecutionLogs,
                                        onSaveWorkflow = { viewModel.saveWorkflow(it) },
                                        onDeleteWorkflow = { viewModel.deleteWorkflow(it) },
                                        onToggleWorkflow = { wf, en -> viewModel.toggleWorkflow(wf, en) },
                                        onRunWorkflow = { viewModel.runWorkflow(it) }
                                    )
                                }

                                JarvisDestination.NOTIFICATIONS -> {
                                    NotificationAssistantScreen(
                                        onBack = { currentDestination = JarvisDestination.HOME },
                                        notifications = notificationLogs,
                                        isPermissionGranted = isNotificationAccessGranted,
                                        onOpenSettings = { viewModel.openNotificationSettings() },
                                        onSummarizeNotifications = { viewModel.summarizeNotifications() },
                                        onSpeakSummary = { viewModel.speakText(it) },
                                        isSummarizing = isSummarizingNotifications,
                                        aiSummary = notificationSummary
                                    )
                                }

                                JarvisDestination.MEMORY_REVIEW -> {
                                    MemoryReviewScreen(
                                        onBack = { currentDestination = JarvisDestination.HOME },
                                        memories = allMemories,
                                        onUpdateImportance = { id, imp -> viewModel.updateMemoryImportance(id, imp) },
                                        onDeleteMemory = { viewModel.deleteMemory(it) },
                                        onPurgeExpired = { viewModel.purgeExpiredMemories() },
                                        onExportText = { viewModel.exportMemoriesAsText(it) },
                                        onExportJson = { viewModel.exportMemoriesAsJson(it) }
                                    )
                                }
                            }
                        }

                        val activeConfirm = pendingSafetyConfirmation
                        if (activeConfirm != null) {
                            com.jarvisai.assistant.ui.components.SafetyConfirmationModal(
                                confirmation = activeConfirm,
                                requireBiometric = settings.requireBiometricForHighRisk,
                                authErrorMessage = biometricAuthError,
                                onApprove = {
                                    if (settings.requireBiometricForHighRisk) {
                                        com.jarvisai.assistant.security.BiometricAuthHelper.authenticateHighRiskAction(
                                            context = context,
                                            title = activeConfirm.title,
                                            subtitle = activeConfirm.description,
                                            onLaunchCredentialIntent = { intent ->
                                                credentialFallbackLauncher.launch(intent)
                                            },
                                            onResult = { authResult ->
                                                when (authResult) {
                                                    is com.jarvisai.assistant.security.BiometricAuthResult.Authenticated -> {
                                                        biometricAuthError = null
                                                        viewModel.resolveSafetyConfirmation(true)
                                                    }
                                                    is com.jarvisai.assistant.security.BiometricAuthResult.Failed -> {
                                                        biometricAuthError = authResult.reason
                                                    }
                                                    is com.jarvisai.assistant.security.BiometricAuthResult.Cancelled -> {
                                                        biometricAuthError = "Authentication cancelled."
                                                    }
                                                }
                                            }
                                        )
                                    } else {
                                        biometricAuthError = null
                                        viewModel.resolveSafetyConfirmation(true)
                                    }
                                },
                                onCancel = {
                                    biometricAuthError = null
                                    viewModel.resolveSafetyConfirmation(false)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

