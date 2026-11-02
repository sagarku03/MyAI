package com.jarvisai.assistant.viewmodel

import android.content.Context
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
import com.jarvisai.assistant.model.MemoryCategory
import com.jarvisai.assistant.model.SupportedGeminiModels
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.security.SecureApiKeyManager
import com.jarvisai.assistant.service.ActionResult
import com.jarvisai.assistant.service.GeminiLiveVoiceManager
import com.jarvisai.assistant.service.LiveTranscriptItem
import com.jarvisai.assistant.service.PhoneControlsManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    private val phoneControlsManager: PhoneControlsManager
) : ViewModel() {

    // --- USER SETTINGS ---
    val userSettings: StateFlow<UserSettings> = preferencesRepository.userSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    // --- GLOBAL ASSISTANT STATUS ---
    private val _chatAssistantStatus = MutableStateFlow(AssistantStatus.IDLE)
    private val _manualPreviewStatus = MutableStateFlow<AssistantStatus?>(null)

    val assistantStatus: StateFlow<AssistantStatus> = combine(
        liveVoiceManager.isSessionActive,
        liveVoiceManager.status,
        _chatAssistantStatus,
        _manualPreviewStatus
    ) { isVoiceActive, voiceStatus, chatStatus, manualPreview ->
        when {
            manualPreview != null -> manualPreview
            isVoiceActive -> voiceStatus
            chatStatus != AssistantStatus.IDLE -> chatStatus
            else -> AssistantStatus.IDLE
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssistantStatus.IDLE)

    fun setPreviewStatus(status: AssistantStatus?) {
        _manualPreviewStatus.value = status
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

    // --- LIVE VOICE STATE ---
    val isLiveVoiceActive: StateFlow<Boolean> = liveVoiceManager.isSessionActive
    val isMicMuted: StateFlow<Boolean> = liveVoiceManager.isMicMuted
    val liveAudioLevel: StateFlow<Float> = liveVoiceManager.audioLevel
    val liveConnectionLabel: StateFlow<String> = liveVoiceManager.connectionStateLabel
    val liveVoiceError: StateFlow<String?> = liveVoiceManager.errorMessage
    val liveTranscripts: StateFlow<List<LiveTranscriptItem>> = liveVoiceManager.transcripts

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
        refreshInstalledApps()
        // If an API key is already saved, automatically discover supported models via ListModels
        if (secureApiKeyManager.hasValidApiKey()) {
            syncSupportedModelsFromApi(secureApiKeyManager.getApiKey(), silent = true)
        }
    }

    // =========================================================================
    // 1. TEXT CHAT OPERATIONS
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

    fun sendChatMessage(userPrompt: String, speakReplyAloud: Boolean = false) {
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
            val isFirstMessageInSession: Boolean

            if (sessionId == null) {
                val initialTitle = cleanPrompt.take(36).ifBlank { "Tactical Session" }
                sessionId = repository.createNewChatSession(
                    title = initialTitle,
                    languageCode = settings.language.name
                )
                _currentSessionId.value = sessionId
                isFirstMessageInSession = true
            } else {
                val existingMsgs = repository.getMessagesForSessionOnce(sessionId)
                isFirstMessageInSession = existingMsgs.isEmpty()
                if (isFirstMessageInSession) {
                    repository.updateSessionTitleAndTimestamp(
                        sessionId = sessionId,
                        newTitle = cleanPrompt.take(36)
                    )
                }
            }

            repository.insertMessage(
                ChatMessageEntity(
                    sessionId = sessionId,
                    role = "user",
                    content = cleanPrompt
                )
            )

            val history = repository.getMessagesForSessionOnce(sessionId)
            val relevantMemories = if (settings.memoryEnabled && settings.autoInjectMemories) {
                repository.retrieveRelevantMemories(cleanPrompt)
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
                        _chatAssistantStatus.value = AssistantStatus.SPEAKING
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
                repository.insertMessage(
                    ChatMessageEntity(
                        sessionId = sessionId,
                        role = "model",
                        content = completedText,
                        isError = false,
                        modelUsed = actualModelUsed,
                        memoriesUsedCount = relevantMemories.size
                    )
                )
                if (speakReplyAloud) {
                    liveVoiceManager.speakWithDeviceTts(
                        text = completedText,
                        language = settings.language,
                        speed = settings.voiceSpeed,
                        pitch = settings.voicePitch
                    )
                }
            } else {
                val err = errorText ?: "Failed to receive response from Gemini."
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

    fun speakMessageAloud(text: String) {
        val settings = userSettings.value
        liveVoiceManager.speakWithDeviceTts(
            text = text,
            language = settings.language,
            speed = settings.voiceSpeed,
            pitch = settings.voicePitch
        )
    }

    // =========================================================================
    // 2. LIVE VOICE OPERATIONS
    // =========================================================================

    fun startLiveVoiceSession() {
        viewModelScope.launch {
            val settings = userSettings.value
            val memories = if (settings.memoryEnabled && settings.autoInjectMemories) {
                repository.retrieveRelevantMemories("", maxCount = 6)
            } else {
                emptyList()
            }
            liveVoiceManager.startLiveSession(
                apiKey = secureApiKeyManager.getApiKey(),
                voiceModel = settings.voiceModel,
                textModel = settings.textModel,
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
            repository.saveApprovedMemory(
                title = title,
                content = content,
                category = category,
                tags = tags,
                isPinned = isPinned
            )
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
            appName = app.appName
        )
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
                    message = "ListModels synced: ${catalog.textModels.size} text models & ${catalog.bidiVoiceModels.size - 1} Bidi live audio models discovered. Active model: ${catalog.bestTextModelId ?: userSettings.value.textModel}",
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

    fun updateVoicePersona(persona: String) {
        viewModelScope.launch { preferencesRepository.updateVoicePersona(persona) }
    }

    fun updateVoiceSpeed(speed: Float) {
        viewModelScope.launch { preferencesRepository.updateVoiceSpeed(speed) }
    }

    fun updateVoicePitch(pitch: Float) {
        viewModelScope.launch { preferencesRepository.updateVoicePitch(pitch) }
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
            return JarvisViewModel(
                repository = repo,
                preferencesRepository = prefs,
                secureApiKeyManager = keyManager,
                geminiApiService = apiService,
                liveVoiceManager = liveVoice,
                phoneControlsManager = phoneControls
            ) as T
        }
    }
}
