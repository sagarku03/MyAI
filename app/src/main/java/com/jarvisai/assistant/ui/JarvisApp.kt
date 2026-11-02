package com.jarvisai.assistant.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.JarvisHudTheme
import com.jarvisai.assistant.ui.components.HudBackgroundBox
import com.jarvisai.assistant.ui.screens.ApiSettingsScreen
import com.jarvisai.assistant.ui.screens.GeneralSettingsScreen
import com.jarvisai.assistant.ui.screens.HomeDashboardScreen
import com.jarvisai.assistant.ui.screens.LiveVoiceScreen
import com.jarvisai.assistant.ui.screens.MemoryCenterScreen
import com.jarvisai.assistant.ui.screens.PhoneControlsScreen
import com.jarvisai.assistant.ui.screens.TextChatScreen
import com.jarvisai.assistant.viewmodel.JarvisViewModel

enum class JarvisDestination(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "HUD", Icons.Default.Home),
    CHAT("chat", "Chat", Icons.AutoMirrored.Filled.Chat),
    VOICE("voice", "Live Voice", Icons.Default.Mic),
    MEMORY("memory", "Memory", Icons.Default.Memory),
    CONTROLS("controls", "Controls", Icons.Default.Build),
    API_SETTINGS("api_settings", "API", Icons.Default.Build),
    GENERAL_SETTINGS("general_settings", "Settings", Icons.Default.Settings)
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

    var currentDestination by rememberSaveable { mutableStateOf(JarvisDestination.HOME) }

    val bottomNavDestinations = listOf(
        JarvisDestination.HOME,
        JarvisDestination.CHAT,
        JarvisDestination.VOICE,
        JarvisDestination.MEMORY,
        JarvisDestination.GENERAL_SETTINGS
    )

    JarvisHudTheme(themeMode = settings.themeMode) {
        HudBackgroundBox {
            Scaffold(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                    ) {
                        bottomNavDestinations.forEach { dest ->
                            val selected = currentDestination == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentDestination = dest },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("bottom_nav_${dest.route}")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentDestination) {
                        JarvisDestination.HOME -> {
                            HomeDashboardScreen(
                                settings = settings,
                                status = status,
                                audioLevel = liveAudioLevel,
                                hasApiKey = apiKey.isNotBlank(),
                                memoriesCount = allMemories.size,
                                sessionsCount = sessions.size,
                                isFlashlightOn = isFlashlightOn,
                                onStatusPreviewSelect = { viewModel.setPreviewStatus(it) },
                                onNavigateToVoice = { currentDestination = JarvisDestination.VOICE },
                                onNavigateToChat = { currentDestination = JarvisDestination.CHAT },
                                onNavigateToMemory = { currentDestination = JarvisDestination.MEMORY },
                                onNavigateToControls = { currentDestination = JarvisDestination.CONTROLS },
                                onNavigateToApiSettings = { currentDestination = JarvisDestination.API_SETTINGS },
                                onNavigateToGeneralSettings = { currentDestination = JarvisDestination.GENERAL_SETTINGS }
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
                                onClearMemories = { viewModel.clearAllMemories() }
                            )
                        }
                    }
                }
            }
        }
    }
}
