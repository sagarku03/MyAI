package com.jarvisai.assistant.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ui.theme.JarvisThemeMode
import com.jarvisai.assistant.data.local.ChatMessageEntity
import com.jarvisai.assistant.data.local.ChatSessionEntity
import com.jarvisai.assistant.data.local.JarvisDatabase
import com.jarvisai.assistant.data.local.MemoryEntity
import com.jarvisai.assistant.data.local.ReminderEntity
import com.jarvisai.assistant.data.network.GeminiApiService
import com.jarvisai.assistant.data.preferences.UserPreferencesRepository
import com.jarvisai.assistant.data.repository.JarvisRepository
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.ConnectionTestResult
import com.jarvisai.assistant.model.GeminiModelOption
import com.jarvisai.assistant.model.InstalledAppInfo
import com.jarvisai.assistant.model.LiveVoiceUiStateStatus
import com.jarvisai.assistant.model.MemoryCategory
import com.jarvisai.assistant.model.SupportedGeminiModels
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.model.VoiceGender
import com.jarvisai.assistant.security.SecureApiKeyManager
import com.jarvisai.assistant.service.ActionResult
import com.jarvisai.assistant.service.GeminiLiveVoiceManager
import com.jarvisai.assistant.service.JarvisTtsManager
import com.jarvisai.assistant.service.LiveTranscriptItem
import com.jarvisai.assistant.service.LiveVoiceSessionBridge
import com.jarvisai.assistant.service.PhoneActionManager
import com.jarvisai.assistant.service.PhoneControlsManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class JarvisViewModel(
    private val repository: JarvisRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val secureApiKeyManager: SecureApiKeyManager,
    private val geminiApiService: GeminiApiService,
    private val liveVoiceManager: GeminiLiveVoiceManager,
    private val phoneControlsManager: PhoneControlsManager,
    private val phoneActionManager: PhoneActionManager,
    private val ttsManager: JarvisTtsManager
) : ViewModel() {

    // --- USER SETTINGS (Eagerly loaded so persisted voice & model are ready immediately) ---
    val userSettings: StateFlow<UserSettings> = preferencesRepository.userSettingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    // --- GLOBAL ASSISTANT STATUS ---
    private val _chatAssistantStatus = MutableStateFlow(AssistantStatus.IDLE)
    private val _manualPreviewStatus = MutableStateFlow<AssistantStatus?>(null)

    val assistantStatus: StateFlow<AssistantStatus> = combine(
        liveVoiceManager.isSessionActive,
        liveVoiceManager.status,
        _chatAssistantStatus,
        ttsManager.isSpeaking,
        _manualPreviewStatus
    ) { isVoiceActive, voiceStatus, chatStatus, isTtsSpeaking, manualPreview ->
        when {
            manualPreview != null -> manualPreview
            isVoiceActive -> voiceStatus
            isTtsSpeaking -> AssistantStatus.SPEAKING
            chatStatus != AssistantStatus.IDLE -> chatStatus
            else -> AssistantStatus.IDLE
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssistantStatus.IDLE)

    fun setPreviewStatus(status: AssistantStatus?) {
        _manualPreviewStatus.value = status
    }

    // --- TTS STATE (REAL GEMINI VOICE PREVIEW IN SETTINGS) ---
    val isTtsSpeaking: StateFlow<Boolean> = ttsManager.isSpeaking
    val ttsErrorMessage: StateFlow<String?> = ttsManager.voiceStatusError

    // --- MEMORY SAVED CONFIRMATION BANNER ---
    private val _memorySavedNotification = MutableStateFlow<String?>(null)
    val memorySavedNotification: StateFlow<String?> = _memorySavedNotification.asStateFlow()

    fun dismissMemorySavedNotification() {
        _memorySavedNotification.value = null
    }

    // --- DYNAMICALLY DISCOVERED MODELS (ListModels) ---
    private val _availableTextModels = MutableStateFlow(SupportedGeminiModels.defaultTextModels)
    val availableTextModels: StateFlow<List<GeminiModelOption>> = _availableTextModels.asStateFlow()

    private val _availableVoiceModels = MutableStateFlow(SupportedGeminiModels.defaultVoiceModels)
    val availableVoiceModels: StateFlow<List<GeminiModelOption>> = _availableVoiceModels.asStateFlow()

    // --- CHAT STATE ---
    val chatSessions: StateFlow<List<ChatSessionEntity>> = repository.allChatSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    val currentMessages: StateFlow<List<ChatMessageEntity>> = _currentSessionId
        .flatMapLatest { sessionId ->
            if (sessionId == null) flowOf(emptyList())
            else repository.getMessagesForSession(sessionId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _streamingPartialResponse = MutableStateFlow<String?>(null)
    val streamingPartialResponse: StateFlow<String?> = _streamingPartialResponse.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private val _lastFailedPrompt = MutableStateFlow<String?>(null)
    val lastFailedPrompt: StateFlow<String?> = _lastFailedPrompt.asStateFlow()

    private var activeChatJob: Job? = null

    // --- LIVE VOICE & SCREEN SHARE STATE ---
    val isLiveVoiceActive: StateFlow<Boolean> = liveVoiceManager.isSessionActive
    val isMicMuted: StateFlow<Boolean> = liveVoiceManager.isMicMuted
    val liveUiStatus: StateFlow<LiveVoiceUiStateStatus> = liveVoiceManager.liveUiStatus
    val liveAudioLevel: StateFlow<Float> = liveVoiceManager.audioLevel
    val liveConnectionLabel: StateFlow<String> = liveVoiceManager.connectionStateLabel
    val liveVoiceError: StateFlow<String?> = liveVoiceManager.errorMessage
    val liveTranscripts: StateFlow<List<LiveTranscriptItem>> = liveVoiceManager.transcripts
    val isScreenSharing: StateFlow<Boolean> = liveVoiceManager.isScreenSharing
    val screenFramesSent: StateFlow<Long> = liveVoiceManager.screenFramesSent

    // --- MEMORY CENTER STATE ---
    val allMemories: StateFlow<List<MemoryEntity>> = repository.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _memorySearchQuery = MutableStateFlow("")
    val memorySearchQuery: StateFlow<String> = _memorySearchQuery.asStateFlow()

    private val _selectedMemoryCategory = MutableStateFlow<MemoryCategory?>(null)
    val selectedMemoryCategory: StateFlow<MemoryCategory?> = _selectedMemoryCategory.asStateFlow()

    val filteredMemories: StateFlow<List<MemoryEntity>> = combine(
        allMemories,
        _memorySearchQuery,
        _selectedMemoryCategory
    ) { memories, query, category ->
        memories.filter { memory ->
            val matchesCategory = category == null || memory.category == category.name
            val q = query.trim().lowercase()
            val matchesSearch = q.isEmpty() ||
                memory.title.lowercase().contains(q) ||
                memory.content.lowercase().contains(q) ||
                memory.tags.lowercase().contains(q) ||
                memory.category.lowercase().contains(q)
            matchesCategory && matchesSearch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- PHONE CONTROLS STATE ---
    val isFlashlightOn: StateFlow<Boolean> = phoneControlsManager.isFlashlightOn
    val hasFlashlightHardware: StateFlow<Boolean> = phoneControlsManager.hasFlashlightHardware

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _appSearchQuery = MutableStateFlow("")
    val appSearchQuery: StateFlow<String> = _appSearchQuery.asStateFlow()

    val filteredInstalledApps: StateFlow<List<InstalledAppInfo>> = combine(
        _installedApps,
        _appSearchQuery
    ) { apps, query ->
        val q = query.trim().lowercase()
        if (q.isEmpty()) apps
        else apps.filter {
            it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<ReminderEntity>> = repository.allReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _phoneControlFeedback = MutableStateFlow<ActionResult?>(null)
    val phoneControlFeedback: StateFlow<ActionResult?> = _phoneControlFeedback.asStateFlow()

    // --- API SETTINGS STATE ---
    val apiKeyFlow: StateFlow<String> = secureApiKeyManager.apiKeyFlow
    val isUsingKeystoreEncryption: StateFlow<Boolean> = secureApiKeyManager.isUsingKeystoreEncryption

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<ConnectionTestResult?>(null)
    val connectionTestResult: StateFlow<ConnectionTestResult?> = _connectionTestResult.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allChatSessions.collect { sessions ->
                if (_currentSessionId.value == null && sessions.isNotEmpty()) {
                    _currentSessionId.value = sessions.first().id
                }
            }
        }
        viewModelScope.launch {
            userSettings.collect { settings ->
                LiveVoiceSessionBridge.setFloatingOverlayEnabled(settings.floatingOverlayEnabled)
            }
        }

        // Handle spoken utterances from Live Voice for Automatic Memory Detection & Phone Control
        liveVoiceManager.onUserUtteranceListener = { spokenText ->
            handleLiveVoiceUserUtterance(spokenText)
        }

        refreshInstalledApps()
        if (secureApiKeyManager.hasValidApiKey()) {
            syncSupportedModelsFromApi(secureApiKeyManager.getApiKey(), silent = true)
        }
    }

    private fun handleLiveVoiceUserUtterance(utterance: String) {
        viewModelScope.launch {
            val settings = userSettings.value
            // 1. Automatic memory detection during Live Voice
            if (settings.memoryEnabled) {
                val memResult = repository.detectAndSaveAutomaticMemories(utterance)
                if (memResult.saved) {
                    _memorySavedNotification.value = memResult.confirmationMessage
                    liveVoiceManager.appendSystemNotice(memResult.confirmationMessage)
                }
            }

            // 2. Spoken Phone Control action during Live Voice ("JARVIS, YouTube kholo", etc.)
            val actionOutcome = phoneActionManager.detectAndExecuteCommand(utterance)
            if (actionOutcome.isPhoneCommand) {
                liveVoiceManager.appendSystemNotice(actionOutcome.spokenHinglishResponse)
                _phoneControlFeedback.value = ActionResult(
                    success = actionOutcome.executedSuccessfully,
                    message = actionOutcome.spokenHinglishResponse
                )
            }
        }
    }

    // =========================================================================
    // 1. TEXT CHAT OPERATIONS (TEXT ONLY — NO AUTOMATIC VOICE / TTS)
    // =========================================================================

    fun selectChatSession(sessionId: Long) {
        _currentSessionId.value = sessionId
        _chatError.value = null
    }

    fun startNewChat() {
        viewModelScope.launch {
            val lang = userSettings.value.language.name
            val newId = repository.createNewChatSession(
                title = "Session #${(System.currentTimeMillis() % 10000)}",
                languageCode = lang
            )
            _currentSessionId.value = newId
            _chatError.value = null
            _lastFailedPrompt.value = null
        }
    }

    fun deleteChatSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteChatSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                val remaining = chatSessions.value.filter { it.id != sessionId }
                _currentSessionId.value = remaining.firstOrNull()?.id
            }
        }
    }

    fun clearAllChatHistory() {
        viewModelScope.launch {
            repository.clearAllChatHistory()
            _currentSessionId.value = null
            _chatError.value = null
            _lastFailedPrompt.value = null
        }
    }

    /**
     * Sends a user message in Normal Text Chat:
     * 1. Automatically detects and persists user memory commands ("My name is Sagar. Remember this.") in Room.
     * 2. Checks if the message is a direct phone control command ("JARVIS, YouTube kholo") and executes it.
     * 3. Retrieves all core/relevant memories from Room and injects them into the Gemini request.
     * 4. Returns TEXT ONLY in Normal Chat (never triggers automatic speech or TTS).
     */
    fun sendChatMessage(userPrompt: String, speakReplyAloud: Boolean? = null) {
        val cleanPrompt = userPrompt.trim()
        if (cleanPrompt.isEmpty() || _isChatLoading.value) return

        activeChatJob?.cancel()
        activeChatJob = viewModelScope.launch {
            _isChatLoading.value = true
            _chatError.value = null
            _lastFailedPrompt.value = null
            _streamingPartialResponse.value = null
            _chatAssistantStatus.value = AssistantStatus.THINKING

            val settings = userSettings.value

            var sessionId = _currentSessionId.value
            if (sessionId == null) {
                val initialTitle = cleanPrompt.take(36).ifBlank { "Tactical Session" }
                sessionId = repository.createNewChatSession(
                    title = initialTitle,
                    languageCode = settings.language.name
                )
                _currentSessionId.value = sessionId
            } else {
                val existingMsgs = repository.getMessagesForSessionOnce(sessionId)
                if (existingMsgs.isEmpty()) {
                    repository.updateSessionTitleAndTimestamp(
                        sessionId = sessionId,
                        newTitle = cleanPrompt.take(36)
                    )
                }
            }

            // Insert user message
            repository.insertMessage(
                ChatMessageEntity(
                    sessionId = sessionId,
                    role = "user",
                    content = cleanPrompt
                )
            )

            // 1. Check Automatic Memory Detection first and persist to Room
            var autoMemoryResultMessage: String? = null
            if (settings.memoryEnabled) {
                val autoSaveResult = repository.detectAndSaveAutomaticMemories(cleanPrompt)
                if (autoSaveResult.saved) {
                    autoMemoryResultMessage = autoSaveResult.confirmationMessage
                    _memorySavedNotification.value = autoSaveResult.confirmationMessage
                }
            }

            // 2. Check if user issued a direct Phone Control command ("JARVIS, YouTube kholo", etc.)
            val phoneCommandOutcome = phoneActionManager.detectAndExecuteCommand(cleanPrompt)
            if (phoneCommandOutcome.isPhoneCommand) {
                val replyText = phoneCommandOutcome.spokenHinglishResponse
                repository.insertMessage(
                    ChatMessageEntity(
                        sessionId = sessionId,
                        role = "model",
                        content = replyText,
                        isError = !phoneCommandOutcome.executedSuccessfully,
                        modelUsed = "PhoneActionManager"
                    )
                )
                _isChatLoading.value = false
                _chatAssistantStatus.value = AssistantStatus.IDLE
                return@launch
            }

            // 3. Retrieve relevant + core personal memories from Room
            val history = repository.getMessagesForSessionOnce(sessionId)
            val relevantMemories = if (settings.memoryEnabled && settings.autoInjectMemories) {
                repository.retrieveRelevantMemories(cleanPrompt, maxCount = 10)
            } else {
                emptyList()
            }

            val apiKey = secureApiKeyManager.getApiKey()
            var finalReply: String? = null
            var actualModelUsed: String = settings.textModel
            var errorText: String? = null

            geminiApiService.streamChatResponse(
                apiKey = apiKey,
                modelId = settings.textModel,
                assistantName = settings.assistantName,
                language = settings.language,
                conversationHistory = history,
                userPrompt = cleanPrompt,
                relevantMemories = relevantMemories
            ).collect { chunk ->
                when (chunk) {
                    is GeminiApiService.StreamChunkResult.Partial -> {
                        _chatAssistantStatus.value = AssistantStatus.THINKING
                        _streamingPartialResponse.value = chunk.accumulatedText
                    }
                    is GeminiApiService.StreamChunkResult.ModelAutoUpdated -> {
                        actualModelUsed = chunk.newModelId
                        preferencesRepository.updateTextModel(chunk.newModelId)
                    }
                    is GeminiApiService.StreamChunkResult.Completed -> {
                        finalReply = chunk.fullText
                        actualModelUsed = chunk.modelUsed
                        _streamingPartialResponse.value = null
                    }
                    is GeminiApiService.StreamChunkResult.Error -> {
                        errorText = chunk.errorMessage
                        _streamingPartialResponse.value = null
                    }
                }
            }

            val completedText = finalReply
            if (!completedText.isNullOrBlank()) {
                val displayReply = if (autoMemoryResultMessage != null &&
                    !completedText.contains("Memory saved", ignoreCase = true)
                ) {
                    "✓ $autoMemoryResultMessage\n\n$completedText"
                } else {
                    completedText
                }

                repository.insertMessage(
                    ChatMessageEntity(
                        sessionId = sessionId,
                        role = "model",
                        content = displayReply,
                        isError = false,
                        modelUsed = actualModelUsed,
                        memoriesUsedCount = relevantMemories.size
                    )
                )
            } else if (autoMemoryResultMessage != null) {
                val fallbackConfirm = "$autoMemoryResultMessage Ji, maine yeh baat apni local memory mein save kar li hai."
                repository.insertMessage(
                    ChatMessageEntity(
                        sessionId = sessionId,
                        role = "model",
                        content = fallbackConfirm,
                        isError = false,
                        modelUsed = "LocalMemoryCore",
                        memoriesUsedCount = relevantMemories.size
                    )
                )
            } else {
                val err = errorText ?: "Connection mein problem aa rahi hai. Gemini se response nahi mila."
                _chatError.value = err
                _lastFailedPrompt.value = cleanPrompt
                repository.insertMessage(
                    ChatMessageEntity(
                        sessionId = sessionId,
                        role = "model",
                        content = err,
                        isError = true,
                        modelUsed = actualModelUsed
                    )
                )
            }

            _isChatLoading.value = false
            _chatAssistantStatus.value = AssistantStatus.IDLE
        }
    }

    fun retryLastFailedMessage() {
        val failedPrompt = _lastFailedPrompt.value
        val sessionId = _currentSessionId.value
        if (failedPrompt.isNullOrBlank()) return

        viewModelScope.launch {
            if (sessionId != null) {
                val msgs = repository.getMessagesForSessionOnce(sessionId)
                val lastMsg = msgs.lastOrNull()
                if (lastMsg != null && lastMsg.isError) {
                    repository.deleteMessageById(lastMsg.id)
                    val secondLast = msgs.getOrNull(msgs.size - 2)
                    if (secondLast != null && secondLast.role == "user" && secondLast.content == failedPrompt) {
                        repository.deleteMessageById(secondLast.id)
                    }
                }
            }
            sendChatMessage(failedPrompt)
        }
    }

    fun stopSpeaking() {
        ttsManager.stopSpeaking()
    }

    fun previewVoiceSelection(
        gender: VoiceGender = userSettings.value.voiceGender,
        persona: String = userSettings.value.voicePersona
    ) {
        val settings = userSettings.value
        ttsManager.previewVoice(
            voiceGender = gender,
            voicePersona = persona,
            language = settings.language,
            speed = settings.voiceSpeed,
            pitch = settings.voicePitch
        )
    }

    // =========================================================================
    // 2. LIVE VOICE & SCREEN STREAMING OPERATIONS
    // =========================================================================

    fun startLiveVoiceSession() {
        viewModelScope.launch {
            ttsManager.stopSpeaking()
            // Read the persisted DataStore settings directly so we never race with default values
            val settings = preferencesRepository.userSettingsFlow.first()
            val memories = if (settings.memoryEnabled && settings.autoInjectMemories) {
                repository.retrieveRelevantMemories("", maxCount = 10)
            } else {
                emptyList()
            }
            liveVoiceManager.startLiveSession(
                apiKey = secureApiKeyManager.getApiKey(),
                voiceModel = settings.voiceModel,
                textModel = settings.textModel,
                voiceGender = settings.voiceGender,
                voicePersona = settings.voicePersona,
                assistantName = settings.assistantName,
                language = settings.language,
                voiceSpeed = settings.voiceSpeed,
                voicePitch = settings.voicePitch,
                memories = memories
            )
        }
    }

    fun stopLiveVoiceSession() {
        liveVoiceManager.stopLiveSession()
    }

    fun toggleLiveVoiceMute() {
        liveVoiceManager.toggleMuteMicrophone()
    }

    fun interruptLiveVoice() {
        liveVoiceManager.interruptAssistantPlayback()
    }

    fun sendLiveVoiceTextPrompt(prompt: String) {
        liveVoiceManager.sendLiveTextTurn(prompt)
    }

    fun clearLiveVoiceTranscripts() {
        liveVoiceManager.clearTranscripts()
    }

    fun startLiveScreenSharing(resultCode: Int, data: Intent) {
        liveVoiceManager.startScreenSharing(resultCode, data)
    }

    fun stopLiveScreenSharing() {
        liveVoiceManager.stopScreenSharing(notifyHud = true)
    }

    fun onScreenSharePermissionDenied() {
        liveVoiceManager.appendSystemNotice("Screen sharing permission deny kar di gayi.")
    }

    // =========================================================================
    // 3. MEMORY CENTER OPERATIONS
    // =========================================================================

    fun setMemorySearchQuery(query: String) {
        _memorySearchQuery.value = query
    }

    fun setSelectedMemoryCategory(category: MemoryCategory?) {
        _selectedMemoryCategory.value = category
    }

    fun saveApprovedMemory(
        title: String,
        content: String,
        category: MemoryCategory,
        tags: String = "",
        isPinned: Boolean = false
    ) {
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            val rowId = repository.saveApprovedMemory(
                title = title,
                content = content,
                category = category,
                tags = tags,
                isPinned = isPinned
            )
            if (rowId > 0L) {
                _memorySavedNotification.value = "Memory saved successfully. (Memory successfully save ho gayi.)"
            }
        }
    }

    fun updateMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            repository.updateMemory(memory)
        }
    }

    fun toggleMemoryPinned(memory: MemoryEntity) {
        viewModelScope.launch {
            repository.updateMemory(memory.copy(isPinned = !memory.isPinned))
        }
    }

    fun deleteMemory(memoryId: Long) {
        viewModelScope.launch {
            repository.deleteMemoryById(memoryId)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            repository.clearAllMemories()
        }
    }

    // =========================================================================
    // 4. PHONE CONTROLS OPERATIONS
    // =========================================================================

    fun refreshInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = phoneControlsManager.getInstalledLaunchableApps()
        }
    }

    fun setAppSearchQuery(query: String) {
        _appSearchQuery.value = query
    }

    fun toggleFlashlight(enabled: Boolean) {
        _phoneControlFeedback.value = phoneControlsManager.setFlashlight(enabled)
    }

    fun launchApp(app: InstalledAppInfo) {
        _phoneControlFeedback.value = phoneControlsManager.launchInstalledApp(
            packageName = app.packageName,
            appName = app.appName,
            activityName = app.activityName
        )
    }

    fun executeVoicePhoneCommand(commandText: String) {
        viewModelScope.launch {
            val outcome = phoneActionManager.detectAndExecuteCommand(commandText)
            if (outcome.isPhoneCommand) {
                _phoneControlFeedback.value = ActionResult(
                    success = outcome.executedSuccessfully,
                    message = outcome.spokenHinglishResponse
                )
            }
        }
    }

    fun openWifiSettings() {
        _phoneControlFeedback.value = phoneControlsManager.openWifiSettings()
    }

    fun openBluetoothSettings() {
        _phoneControlFeedback.value = phoneControlsManager.openBluetoothSettings()
    }

    fun openAndroidSettings() {
        _phoneControlFeedback.value = phoneControlsManager.openAndroidSettings()
    }

    fun openDisplaySettings() {
        _phoneControlFeedback.value = phoneControlsManager.openDisplaySettings()
    }

    fun openSoundSettings() {
        _phoneControlFeedback.value = phoneControlsManager.openSoundSettings()
    }

    fun createAlarm(hour: Int, minute: Int, label: String) {
        _phoneControlFeedback.value = phoneControlsManager.createSystemAlarm(hour, minute, label)
    }

    fun createReminder(
        title: String,
        notes: String,
        scheduledTimeMillis: Long,
        alsoOpenSystemCalendar: Boolean
    ) {
        if (title.isBlank()) {
            _phoneControlFeedback.value = ActionResult(false, "Please enter a reminder title.")
            return
        }
        viewModelScope.launch {
            repository.insertReminder(
                title = title,
                notes = notes,
                scheduledTimeMillis = scheduledTimeMillis
            )
            if (alsoOpenSystemCalendar) {
                _phoneControlFeedback.value = phoneControlsManager.launchCalendarReminderIntent(
                    title = title,
                    notes = notes,
                    startTimeMillis = scheduledTimeMillis
                )
            } else {
                _phoneControlFeedback.value = ActionResult(
                    true,
                    "Reminder '${title.trim()}' saved to local JARVIS tactical log."
                )
            }
        }
    }

    fun toggleReminderCompleted(reminder: ReminderEntity) {
        viewModelScope.launch {
            repository.toggleReminderCompleted(reminder)
        }
    }

    fun deleteReminder(reminderId: Long) {
        viewModelScope.launch {
            repository.deleteReminder(reminderId)
        }
    }

    fun initiateConfirmedCall(phoneNumber: String) {
        _phoneControlFeedback.value = phoneControlsManager.initiateConfirmedDialCall(phoneNumber)
    }

    fun clearPhoneControlFeedback() {
        _phoneControlFeedback.value = null
    }

    // =========================================================================
    // 5. API SETTINGS & GENERAL SETTINGS OPERATIONS
    // =========================================================================

    fun saveApiKey(rawKey: String) {
        val saved = secureApiKeyManager.saveApiKey(rawKey)
        if (saved) {
            _connectionTestResult.value = ConnectionTestResult(
                success = true,
                message = "API key encrypted with Android Keystore (AES-256-GCM). Discovering supported models via ListModels..."
            )
            syncSupportedModelsFromApi(rawKey.trim(), silent = false)
        }
    }

    fun deleteApiKey() {
        secureApiKeyManager.deleteApiKey()
        _availableTextModels.value = SupportedGeminiModels.defaultTextModels
        _availableVoiceModels.value = SupportedGeminiModels.defaultVoiceModels
        _connectionTestResult.value = ConnectionTestResult(
            success = false,
            message = "Stored API key purged from Android Keystore vault."
        )
    }

    fun getMaskedApiKey(): String = secureApiKeyManager.getMaskedApiKey()

    fun hasConfiguredApiKey(): Boolean = secureApiKeyManager.hasValidApiKey()

    fun syncSupportedModelsFromApi(overrideKey: String? = null, silent: Boolean = false) {
        val keyToUse = overrideKey?.takeIf { it.isNotBlank() } ?: secureApiKeyManager.getApiKey()
        if (keyToUse.isBlank()) return

        viewModelScope.launch {
            if (!silent) _isTestingConnection.value = true
            val catalog = geminiApiService.fetchAvailableModels(keyToUse)
            if (catalog.textModels.isNotEmpty()) {
                _availableTextModels.value = catalog.textModels
                val currentText = userSettings.value.textModel
                val isCurrentInCatalog = catalog.textModels.any { it.id == currentText }
                if (!isCurrentInCatalog && !catalog.bestTextModelId.isNullOrBlank()) {
                    preferencesRepository.updateTextModel(catalog.bestTextModelId)
                }
            }
            if (catalog.bidiVoiceModels.isNotEmpty()) {
                _availableVoiceModels.value = catalog.bidiVoiceModels
            }
            if (!silent) {
                _connectionTestResult.value = ConnectionTestResult(
                    success = true,
                    message = "ListModels synced: ${catalog.textModels.size} text models discovered. Active model: ${catalog.bestTextModelId ?: userSettings.value.textModel}",
                    modelTested = catalog.bestTextModelId.orEmpty(),
                    discoveredTextModels = catalog.textModels,
                    discoveredVoiceModels = catalog.bidiVoiceModels
                )
                _isTestingConnection.value = false
            }
        }
    }

    fun testGeminiConnection(overrideKey: String? = null) {
        val keyToTest = overrideKey?.takeIf { it.isNotBlank() } ?: secureApiKeyManager.getApiKey()
        viewModelScope.launch {
            _isTestingConnection.value = true
            val preferredModel = userSettings.value.textModel
            val result = geminiApiService.testConnection(
                apiKey = keyToTest,
                preferredModelId = preferredModel
            )
            if (result.discoveredTextModels.isNotEmpty()) {
                _availableTextModels.value = result.discoveredTextModels
            }
            if (result.discoveredVoiceModels.isNotEmpty()) {
                _availableVoiceModels.value = result.discoveredVoiceModels
            }
            if (result.success && result.modelTested.isNotBlank() && result.modelTested != preferredModel) {
                preferencesRepository.updateTextModel(result.modelTested)
            }
            _connectionTestResult.value = result
            _isTestingConnection.value = false
        }
    }

    fun updateAssistantName(name: String) {
        viewModelScope.launch { preferencesRepository.updateAssistantName(name) }
    }

    fun updateLanguage(language: SupportedLanguage) {
        viewModelScope.launch { preferencesRepository.updateLanguage(language) }
    }

    fun updateTextModel(modelId: String) {
        viewModelScope.launch { preferencesRepository.updateTextModel(modelId) }
    }

    fun updateVoiceModel(modelId: String) {
        viewModelScope.launch { preferencesRepository.updateVoiceModel(modelId) }
    }

    fun updateVoiceGender(gender: VoiceGender) {
        viewModelScope.launch {
            preferencesRepository.updateVoiceGender(gender)
            val updated = preferencesRepository.userSettingsFlow.first()
            liveVoiceManager.updateVoiceAndReconnectIfActive(updated.voiceGender, updated.voicePersona)
        }
    }

    fun updateVoicePersona(persona: String) {
        viewModelScope.launch {
            preferencesRepository.updateVoicePersona(persona)
            val updated = preferencesRepository.userSettingsFlow.first()
            liveVoiceManager.updateVoiceAndReconnectIfActive(updated.voiceGender, updated.voicePersona)
        }
    }

    fun updateVoiceSpeed(speed: Float) {
        viewModelScope.launch { preferencesRepository.updateVoiceSpeed(speed) }
    }

    fun updateVoicePitch(pitch: Float) {
        viewModelScope.launch { preferencesRepository.updateVoicePitch(pitch) }
    }

    fun updateAutoSpeakChatReplies(enabled: Boolean) {
        viewModelScope.launch {
            ttsManager.stopSpeaking()
            preferencesRepository.updateAutoSpeakChatReplies(false)
        }
    }

    fun updateFloatingOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateFloatingOverlayEnabled(enabled) }
    }

    fun updateThemeMode(themeMode: JarvisThemeMode) {
        viewModelScope.launch { preferencesRepository.updateThemeMode(themeMode) }
    }

    fun updateMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateMemoryEnabled(enabled) }
    }

    fun updateAutoInjectMemories(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateAutoInjectMemories(enabled) }
    }

    fun updateConfirmConsequentialActions(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateConfirmConsequentialActions(enabled) }
    }

    fun updatePrivacyRedactTelemetry(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updatePrivacyRedactTelemetry(enabled) }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
        liveVoiceManager.release()
        phoneControlsManager.cleanup()
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = JarvisDatabase.getInstance(appContext)
            val repo = JarvisRepository(db.jarvisDao())
            val prefs = UserPreferencesRepository(appContext)
            val keyManager = SecureApiKeyManager(appContext)
            val apiService = GeminiApiService()
            val liveVoice = GeminiLiveVoiceManager(appContext, apiService)
            val phoneControls = PhoneControlsManager(appContext)
            val phoneActions = PhoneActionManager(appContext, phoneControls)
            val tts = JarvisTtsManager(appContext, apiService, keyManager)
            return JarvisViewModel(
                repository = repo,
                preferencesRepository = prefs,
                secureApiKeyManager = keyManager,
                geminiApiService = apiService,
                liveVoiceManager = liveVoice,
                phoneControlsManager = phoneControls,
                phoneActionManager = phoneActions,
                ttsManager = tts
            ) as T
        }
    }
}
