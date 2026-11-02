# JARVIS AI — Pre-Redesign Codebase Audit & Function Preservation Report

**Application Name:** JARVIS AI  
**Application ID:** `com.jarvisai.assistant`  
**Audit Status:** Complete (100% of Kotlin source files, Gradle configurations, and `AndroidManifest.xml` inspected)  
**Source Code Modifications:** **None** (0 source files modified)

---

## 1. Executive Architecture & Navigation Inventory

### 1.1 Entry Point & Navigation Architecture
* **Entry Point:** `app/src/main/java/com/example/MainActivity.kt`
  * Calls `enableEdgeToEdge()`, initializes `JarvisViewModel` via `viewModel()`, applies `MyApplicationTheme(themeMode = settings.themeMode)`, and renders `JarvisApp(viewModel = jarvisViewModel)`.
* **Navigation Host & Shell:** `app/src/main/java/com/jarvisai/assistant/ui/JarvisApp.kt`
  * Uses state-driven navigation (`var currentDestination by rememberSaveable { mutableStateOf(JarvisDestination.HOME) }`).
  * Every non-home screen registers an `androidx.activity.compose.BackHandler` that returns to `JarvisDestination.HOME`.
  * Persistent bottom `NavigationBar` exposes 5 primary tabs (`HOME`, `CHAT`, `VOICE`, `MEMORY`, `GENERAL_SETTINGS`), while `CONTROLS` and `API_SETTINGS` are accessed via Home Dashboard modules or contextual banners.

### 1.2 All Navigation Routes & Destinations (`JarvisDestination`)

| Destination Enum | Route ID | Label | Bottom Nav TestTag | Target Screen Composable | File Responsible | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `JarvisDestination.HOME` | `"home"` | `"HUD"` | `bottom_nav_home` | `HomeDashboardScreen` | `ui/screens/HomeDashboardScreen.kt` | Working |
| `JarvisDestination.CHAT` | `"chat"` | `"Chat"` | `bottom_nav_chat` | `TextChatScreen` | `ui/screens/TextChatScreen.kt` | Working |
| `JarvisDestination.VOICE` | `"voice"` | `"Live Voice"` | `bottom_nav_voice` | `LiveVoiceScreen` | `ui/screens/LiveVoiceScreen.kt` | Working |
| `JarvisDestination.MEMORY` | `"memory"` | `"Memory"` | `bottom_nav_memory` | `MemoryCenterScreen` | `ui/screens/MemoryCenterScreen.kt` | Working |
| `JarvisDestination.CONTROLS` | `"controls"` | `"Controls"` | *(Via Home `btn_phone_controls`)* | `PhoneControlsScreen` | `ui/screens/PhoneControlsScreen.kt` | Working |
| `JarvisDestination.API_SETTINGS` | `"api_settings"` | `"API"` | *(Via Home `btn_api_settings` & banners)* | `ApiSettingsScreen` | `ui/screens/ApiSettingsScreen.kt` | Working |
| `JarvisDestination.GENERAL_SETTINGS` | `"general_settings"` | `"Settings"` | `bottom_nav_general_settings` | `GeneralSettingsScreen` | `ui/screens/GeneralSettingsScreen.kt` | Working |

---

## 2. Detailed Screen, Component, Button & Action Inventory

### 2.1 Screen 1: Home Dashboard (`HomeDashboardScreen.kt`)
* **File:** `app/src/main/java/com/jarvisai/assistant/ui/screens/HomeDashboardScreen.kt`
* **Responsibilities:** Displays animated Arc Reactor HUD, assistant status (`IDLE`, `LISTENING`, `THINKING`, `SPEAKING`), telemetry summary (Memory Count, Active Language, Keystore status, Active Text & Voice Models), missing API key warning banner, and 6 primary navigation modules.
* **Dependencies:** `JarvisViewModel`, `ArcReactorView`, `HudComponents` (`HudTopBar`, `AssistantStatusPill`, `HudFrameCard`, `HudMetricChip`, `DashboardModuleButton`).

| Component / Element | TestTag | Existing Functionality / Action | Connected ViewModel / Callback | Navigation Destination | Working Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Top HUD Bar + Status Pill | `assistant_status_pill` | Displays `<AssistantName> AI`, subtitle, and live status badge (`IDLE`, `LISTENING`, `THINKING`, `SPEAKING`) | `status` (`JarvisViewModel.assistantStatus`) | None | Working |
| Arc Reactor Core | `arc_reactor_card`, `home_arc_reactor` | Multi-ring animated Canvas HUD reactor scaling with `audioLevel` & `status` | `status`, `audioLevel` (`liveVoiceState.audioLevel`) | None | Working |
| Status Filter Chips (4) | `status_chip_idle`, `status_chip_listening`, `status_chip_thinking`, `status_chip_speaking` | Previews reactor animation states when Live Voice session is idle | `onSelectStatusPreview` -> `viewModel.setPreviewStatus(status)` | None | Working |
| Missing API Key Warning Card | `missing_api_key_banner` | Shown when `!hasApiKey`; clicking navigates to API Settings | `onNavigateToApiSettings` | `JarvisDestination.API_SETTINGS` | Working |
| Voice Assistant Module Card | `btn_voice_assistant` | Navigates to Live Voice Assistant screen | `onNavigateToVoice` | `JarvisDestination.VOICE` | Working |
| Text Chat Module Card | `btn_text_chat` | Navigates to Text Chat screen | `onNavigateToChat` | `JarvisDestination.CHAT` | Working |
| Memory Center Module Card | `btn_memory_center` | Navigates to Memory Center screen | `onNavigateToMemory` | `JarvisDestination.MEMORY` | Working |
| Phone Controls Module Card | `btn_phone_controls` | Navigates to Phone Controls screen | `onNavigateToControls` | `JarvisDestination.CONTROLS` | Working |
| API Settings Module Card | `btn_api_settings` | Navigates to API Settings screen | `onNavigateToApiSettings` | `JarvisDestination.API_SETTINGS` | Working |
| General Settings Module Card | `btn_general_settings` | Navigates to General Settings screen | `onNavigateToGeneralSettings` | `JarvisDestination.GENERAL_SETTINGS` | Working |

---

### 2.2 Screen 2: Text Chat (`TextChatScreen.kt`)
* **File:** `app/src/main/java/com/jarvisai/assistant/ui/screens/TextChatScreen.kt`
* **Responsibilities:** Multi-session Gemini text chat with real-time SSE streaming (`streamGenerateContent`) and automatic standard REST fallback (`generateContent`), Hinglish/Hindi/English language switching, Room persistence, natural-language phone command execution (`PhoneActionManager`), auto-memory extraction (`MemoryRepository`), manual memory saving dialog, message copy, failed request retry, and session history management.
* **Dependencies:** `JarvisViewModel`, `GeminiApiService`, `JarvisRepository`, `MemoryRepository`, `PhoneActionManager`, `HudComponents`.

| Component / Element | TestTag | Existing Functionality / Action | Connected ViewModel / Callback | Navigation Destination | Working Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Top Bar Back Button | `hud_back_button` | Navigates back to Home Dashboard | `onBack` | `JarvisDestination.HOME` | Working |
| Language Filter Chips (3) | `chat_lang_hinglish`, `chat_lang_hindi`, `chat_lang_english` | Switches active language (`HINGLISH`, `HINDI`, `ENGLISH`) | `onSelectLanguage` -> `viewModel.updateLanguage(lang)` | None | Working |
| New Chat Button | `btn_new_chat` | Creates a new `ChatSessionEntity` in Room and selects it | `onStartNewChat` -> `viewModel.startNewChat()` | None | Working |
| Sessions Drawer Toggle | `btn_toggle_sessions` | Toggles collapsible `HudFrameCard` listing past chat sessions | Local state `showHistorySheet = !showHistorySheet` | None | Working |
| Session Item Row & Delete | `delete_session_${session.id}` | Selects session (`onSelectSession`) or deletes session from Room (`onDeleteSession`) | `viewModel.selectChatSession(id)`, `viewModel.deleteChatSession(id)` | None | Working |
| Clear All Chat History Button | `btn_clear_chat_history` | Opens `HudConfirmationDialog` -> purges all chat sessions & messages | `onClearAllHistory` -> `viewModel.clearAllChatHistory()` | None | Working |
| Memory Saved Notification Banner | *(AnimatedVisibility banner)* | Shows auto-saved or manually saved memory confirmation with close (`X`) button | `chatUiState.memorySavedNotification`, `viewModel.dismissMemorySavedNotification()` | None | Working |
| Missing API Key Banner | *(Conditional banner)* | Prompts user to configure Gemini API key if missing | `onNavigateToApiSettings` | `JarvisDestination.API_SETTINGS` | Working |
| Empty Chat Quick Prompts (3) | *(OutlinedButtons in `EmptyChatPlaceholder`)* | Sends starter prompt in selected language (Hinglish/Hindi/English) | `onSendMessage(prompt)` -> `viewModel.sendChatMessage(prompt)` | None | Working |
| Message Copy Action | `copy_msg_${message.id}` | Copies message content to Android system `ClipboardManager` + Toast | `copyTextToClipboard(context, message.content)` | None | Working |
| Save Message to Memory Action | `save_memory_msg_${message.id}` | Opens `SaveChatToMemoryDialog` to edit title/content/category/tags/pin and save to Room | `onSaveMessageToMemory` -> `viewModel.saveApprovedMemory(...)` | None | Working |
| Per-Message Retry Action | `retry_msg_${message.id}` | Retries failed user/assistant message request | `onRetryFailed` -> `viewModel.retryLastFailedMessage()` | None | Working |
| Error Banner Retry Button | `btn_retry_chat` | Shown when `chatUiState.errorMessage != null`; retries last failed message | `onRetryFailed` -> `viewModel.retryLastFailedMessage()` | None | Working |
| Streaming Partial Bubble | *(LazyColumn item)* | Renders live streaming chunks (`streamingPartialResponse + " ▍"`) | `chatUiState.isStreaming`, `chatUiState.streamingPartialResponse` | None | Working |
| Chat Text Input Field | `chat_input_field` | Multiline text input (`maxLines = 4`) for typing prompt | Local `inputText` state | None | Working |
| Send Message IconButton | `btn_send_message` | Sends typed message when non-blank and not currently loading | `onSendMessage(inputText)` -> `viewModel.sendChatMessage(text)` | None | Working |

---

### 2.3 Screen 3: Live Voice Assistant (`LiveVoiceScreen.kt`)
* **File:** `app/src/main/java/com/jarvisai/assistant/ui/screens/LiveVoiceScreen.kt`
* **Responsibilities:** Real-time bidirectional Gemini Live WebSocket audio call (`BidiGenerateContent`), PCM 16kHz mic capture, PCM 24kHz low-latency `AudioTrack` output, live screen sharing (`MediaProjection` 1 FPS JPEG capture), autonomous Live Agent tool execution (40+ tools via `LiveAgentToolExecutor` & `JarvisAccessibilityService`), foreground service + floating overlay popup (`LiveVoiceForegroundService`), voice barge-in/interruption, and in-call text prompt injection.
* **Dependencies:** `JarvisViewModel`, `GeminiLiveVoiceManager`, `LiveVoiceForegroundService`, `LiveAgentToolExecutor`, `JarvisAccessibilityService`, `PhoneActionManager`.

| Component / Element | TestTag | Existing Functionality / Action | Connected ViewModel / Callback | Navigation Destination | Working Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Top Bar Back Button | `hud_back_button` | Navigates back to Home Dashboard | `onBack` | `JarvisDestination.HOME` | Working |
| Language Filter Chips (3) | `voice_lang_hinglish`, `voice_lang_hindi`, `voice_lang_english` | Switches active language for Live Voice & reconnects/updates system prompt | `onSelectLanguage` -> `viewModel.updateLanguage(lang)` | None | Working |
| Missing API Key Warning | *(Conditional `HudFrameCard`)* | Shown if `!hasApiKey`; navigates to API Settings | `onNavigateToApiSettings` | `JarvisDestination.API_SETTINGS` | Working |
| Overlay Permission Banner | `btn_grant_overlay_permission` | Shown when `settings.floatingOverlayEnabled && !Settings.canDrawOverlays(context)`; opens system overlay settings | Launches `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` | Android System Settings | Working |
| Accessibility Service Status Bar | `accessibility_service_status_bar`, `btn_open_accessibility_settings` | Shows whether `JarvisAccessibilityService` is `ACTIVE` or `OFF` for full screen click/scroll/type automation; button opens Android Accessibility Settings | `JarvisAccessibilityService.isServiceConnected()`, `JarvisAccessibilityService.openAccessibilitySettings(context)` | Android Accessibility Settings | Working |
| Central Live Arc Reactor | `live_voice_arc_reactor` | Animated reactor reacting to real-time mic RMS and AI playback `audioLevel` | `liveVoiceState.status`, `liveVoiceState.audioLevel` | None | Working |
| Live Status Label & Spectrum | `live_voice_status_label` | Shows current status (`LISTENING`, `THINKING`, `SPEAKING`, `IDLE`), `LiveAudioSpectrumStrip`, and 3 `LiveStatusIndicatorChip`s | `liveVoiceState` | None | Working |
| Screen Share Control Bar | `screen_share_control_bar`, `btn_start_screen_share`, `btn_stop_screen_share` | Visible when `liveVoiceState.isSessionActive`. Starts `MediaProjectionManager.createScreenCaptureIntent()` -> streams 1 FPS screen frames to Gemini Live; or stops screen sharing | `viewModel.startLiveScreenSharing(resultCode, data)`, `viewModel.stopLiveScreenSharing()` | System MediaProjection Dialog | Working |
| Quick Voice Command Chips (4) | *(Horizontal scroll `AssistChip`s)* | `"JARVIS, YouTube kholo"`, `"JARVIS, YouTube par search karo latest technology videos"`, `"JARVIS, Chrome kholo"`, `"JARVIS, Settings kholo"` | Sends via `onSendLiveText` if session active, or `onExecuteQuickPhoneCommand` if idle | None | Working |
| Live Transcript Log | *(LazyColumn inside `HudFrameCard`)* | Displays real-time `[USER]`, `[JARVIS]`, and `[SYSTEM]` transcript items + partial user/AI transcripts | `liveVoiceState.transcriptHistory`, `lastUserTranscript`, `lastAiTranscript` | None | Working |
| Live Text Prompt Input & Send | `live_text_input`, `btn_send_live_text` | Visible when `isSessionActive`; sends text turn into active Gemini Live WebSocket session or executes phone action | `onSendLiveText` -> `viewModel.sendLiveVoiceTextPrompt(text)` | None | Working |
| Start Live Voice Session Button | `btn_start_live_voice` | Shown when `!isSessionActive`. Verifies/requests `RECORD_AUDIO` (and `POST_NOTIFICATIONS` on Android 13+) then starts `LiveVoiceForegroundService` and WebSocket session | `onStartSession` -> `viewModel.startLiveVoiceSession()` | None | Working |
| Mute / Unmute Mic Button | `btn_mute_live_voice` | Shown when `isSessionActive`. Toggles microphone mute state | `onToggleMute` -> `viewModel.toggleLiveVoiceMute()` | None | Working |
| Interrupt (Barge-In) Button | `btn_interrupt_live_voice` | Shown when `isSessionActive`. Immediately flushes `AudioTrack` playback and cancels current AI audio turn | `onInterrupt` -> `viewModel.interruptLiveVoice()` | None | Working |
| End Live Session Button | `btn_end_live_voice` | Shown when `isSessionActive`. Stops WebSocket session, stops screen sharing, and stops `LiveVoiceForegroundService` | `onStopSession` -> `viewModel.stopLiveVoiceSession()` | None | Working |

---

### 2.4 Screen 4: Memory Center (`MemoryCenterScreen.kt`)
* **File:** `app/src/main/java/com/jarvisai/assistant/ui/screens/MemoryCenterScreen.kt`
* **Responsibilities:** Full CRUD management of persistent assistant memories stored in Room (`MemoryItemEntity`), keyword search, category filtering (`PERSONAL`, `PREFERENCES`, `WORK`, `PROJECTS`, `IMPORTANT`, `GENERAL`), pinning, global memory retrieval toggle, and bulk purge.
* **Dependencies:** `JarvisViewModel`, `MemoryRepository`, `JarvisDao`, `HudComponents`.

| Component / Element | TestTag | Existing Functionality / Action | Connected ViewModel / Callback | Navigation Destination | Working Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Top Bar Back Button | `hud_back_button` | Navigates back to Home Dashboard | `onBack` | `JarvisDestination.HOME` | Working |
| Add Memory FAB | `fab_add_memory` | Opens `MemoryEditorDialog` to create a new memory item | Local state `showCreateDialog = true` | None | Working |
| Memory Retrieval Switch | `switch_memory_center_enabled` | Toggles `settings.memoryEnabled` in DataStore | `onToggleMemoryEnabled` -> `viewModel.updateMemoryEnabled(enabled)` | None | Working |
| Search Memories Input | `memory_search_input` | Filters memories by title, content, or tags in real time | `onSearchQueryChange` -> `viewModel.setMemorySearchQuery(query)` | None | Working |
| Clear All Memories Button | `btn_clear_all_memories` | Opens `HudConfirmationDialog` -> deletes all memories from Room | `onClearAllMemories` -> `viewModel.clearAllMemories()` | None | Working |
| Category Filter Chips (7) | `memory_cat_all`, `memory_cat_personal`, `memory_cat_preferences`, `memory_cat_work`, `memory_cat_projects`, `memory_cat_important`, `memory_cat_general` | Filters memory list by `MemoryCategory` or shows all | `onSelectCategory` -> `viewModel.setSelectedMemoryCategory(cat)` | None | Working |
| Memory Card Pin IconButton | *(Inside `memory_card_${memory.id}`)* | Toggles `isPinned` on the `MemoryItem` in Room | `onTogglePin` -> `viewModel.toggleMemoryPinned(memory)` | None | Working |
| Memory Card Edit IconButton | `edit_memory_${memory.id}` | Opens `MemoryEditorDialog` pre-populated with the memory's fields | Local state `editingMemory = memory` -> `viewModel.updateMemory(...)` | None | Working |
| Memory Card Delete IconButton | `delete_memory_${memory.id}` | Deletes the specific memory item from Room | `onDeleteMemory` -> `viewModel.deleteMemory(memory.id)` | None | Working |
| Memory Editor Dialog & Save | `memory_dialog_title`, `memory_dialog_content`, `memory_dialog_tags`, `memory_dialog_save_btn` | Validates non-blank title/content and inserts or updates memory in Room | `onSaveMemory` (`viewModel.saveApprovedMemory`) / `onUpdateMemory` (`viewModel.updateMemory`) | None | Working |

---

### 2.5 Screen 5: Phone Controls (`PhoneControlsScreen.kt`)
* **File:** `app/src/main/java/com/jarvisai/assistant/ui/screens/PhoneControlsScreen.kt`
* **Responsibilities:** Hardware & system intent controls (Flashlight via `CameraManager`, Wi-Fi/Bluetooth/System/Display/Sound settings), system alarm creation (`AlarmClock.ACTION_SET_ALARM`), local + Calendar-synced reminders (`ReminderEntity`), safety-confirmed phone call initiation (`Intent.ACTION_DIAL`), and searchable installed app launcher (`PackageManager`).
* **Dependencies:** `JarvisViewModel`, `PhoneControlsManager`, `JarvisRepository`, `HudComponents`.

| Component / Element | TestTag | Existing Functionality / Action | Connected ViewModel / Callback | Navigation Destination | Working Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Top Bar Back Button | `hud_back_button` | Navigates back to Home Dashboard | `onBack` | `JarvisDestination.HOME` | Working |
| Feedback Banner & Dismiss | `phone_control_feedback_banner` | Shows action execution status message with dismiss (`X`) button | `uiState.feedbackMessage`, `viewModel.clearPhoneControlFeedback()` | None | Working |
| Torch / Flashlight Switch | `switch_flashlight` | Toggles rear camera torch via `CameraManager.setTorchMode` | `onToggleFlashlight` -> `viewModel.toggleFlashlight(enabled)` | None | Working |
| Wi-Fi Settings Button | `btn_open_wifi` | Opens Android Wi-Fi settings (`Settings.ACTION_WIFI_SETTINGS`) | `onOpenWifi` -> `viewModel.openWifiSettings()` | Android Wi-Fi Settings | Working |
| Bluetooth Settings Button | `btn_open_bluetooth` | Opens Android Bluetooth settings (`Settings.ACTION_BLUETOOTH_SETTINGS`) | `onOpenBluetooth` -> `viewModel.openBluetoothSettings()` | Android Bluetooth Settings | Working |
| System Settings Button | `btn_open_android_settings` | Opens main Android Settings (`Settings.ACTION_SETTINGS`) | `onOpenSettings` -> `viewModel.openAndroidSettings()` | Android Settings | Working |
| Display Settings Button | *(OutlinedButton)* | Opens Display settings (`Settings.ACTION_DISPLAY_SETTINGS`) | `onOpenDisplaySettings` -> `viewModel.openDisplaySettings()` | Android Display Settings | Working |
| Sound Settings Button | *(OutlinedButton)* | Opens Sound settings (`Settings.ACTION_SOUND_SETTINGS`) | `onOpenSoundSettings` -> `viewModel.openSoundSettings()` | Android Sound Settings | Working |
| Create Alarm Inputs & Button | `alarm_hour_input`, `alarm_minute_input`, `alarm_label_input`, `btn_create_alarm` | Validates hour (0–23) & minute (0–59); optionally shows `HudConfirmationDialog` if `confirmConsequentialActions` is enabled; launches `AlarmClock.ACTION_SET_ALARM` | `onCreateAlarm` -> `viewModel.createAlarm(hour, minute, label)` | System Clock App | Working |
| Tactical Reminder Inputs & Button | `reminder_title_input`, `btn_create_reminder` | Saves reminder in Room (`reminders` table) and optionally opens Calendar event intent (`CalendarContract.Events.CONTENT_URI`) | `onCreateReminder` -> `viewModel.createReminder(...)` | Optional Calendar App | Working |
| Reminder Complete & Delete | *(Checkbox & Delete IconButton)* | Toggles `isCompleted` or deletes reminder from Room | `viewModel.toggleReminderCompleted(reminder)`, `viewModel.deleteReminder(id)` | None | Working |
| Phone Call Input & Dial Button | `call_phone_input`, `btn_initiate_call` | Always triggers `HudConfirmationDialog` before launching `Intent.ACTION_DIAL` (`tel:`) | `onInitiateConfirmedCall` -> `viewModel.initiateConfirmedCall(phone)` | System Phone Dialer | Working |
| Installed Apps Search & Refresh | `installed_apps_search` | Filters launchable apps and refreshes list from `PackageManager` | `viewModel.setAppSearchQuery(query)`, `viewModel.refreshInstalledApps()` | None | Working |
| Launch Installed App Button | *(Per-row `OPEN` OutlinedButton)* | Optionally confirms if `confirmConsequentialActions` is enabled, then launches app via `packageManager.getLaunchIntentForPackage` | `onLaunchApp` -> `viewModel.launchApp(packageName, label)` | Target Installed App | Working |

---

### 2.6 Screen 6: API Settings (`ApiSettingsScreen.kt`)
* **File:** `app/src/main/java/com/jarvisai/assistant/ui/screens/ApiSettingsScreen.kt`
* **Responsibilities:** Manages hardware-backed AES-256-GCM API key encryption (`SecureApiKeyManager`), live connection testing (`GeminiApiService.testApiKeyConnection`), dynamic model synchronization from Google's `v1beta/models` endpoint (`syncSupportedModelsFromApi`), and selection or custom entry of both **Text Chat Models** and **Live Voice Chat Models**.
* **Dependencies:** `JarvisViewModel`, `SecureApiKeyManager`, `GeminiApiService`, `UserPreferencesRepository`, `HudComponents`.

| Component / Element | TestTag | Existing Functionality / Action | Connected ViewModel / Callback | Navigation Destination | Working Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Top Bar Back Button | `hud_back_button` | Navigates back to Home Dashboard | `onBack` | `JarvisDestination.HOME` | Working |
| API Key Input Field | `api_key_input_field` | Input for Gemini API key with password masking toggle | Local `apiKeyInput` state | None | Working |
| Toggle Key Visibility Button | `btn_toggle_key_visibility` | Toggles password masking on `api_key_input_field` | Local `showApiKey = !showApiKey` | None | Working |
| Save Key Button | `btn_save_api_key` | Encrypts and saves Gemini API key into Android Keystore + SharedPreferences | `onSaveApiKey` -> `viewModel.saveApiKey(apiKeyInput)` | None | Working |
| Delete Key Button | `btn_delete_api_key` | Opens `HudConfirmationDialog` -> deletes stored key from Keystore & preferences | `onDeleteApiKey` -> `viewModel.deleteApiKey()` | None | Working |
| Test Connection Button | `btn_test_connection` | Tests typed or saved API key against Gemini API and displays latency & model | `onTestConnection` -> `viewModel.testGeminiConnection(candidateKey)` | None | Working |
| Sync Models from API Button | `btn_sync_list_models` | Queries `GET /v1beta/models` to dynamically populate text and `bidiGenerateContent` voice models | `onSyncModels` -> `viewModel.syncSupportedModelsFromApi(candidateKey)` | None | Working |
| Connection Result Box | `connection_test_result_box` | Displays success/failure banner from connection test or model sync | `apiUiState.testResultMessage`, `apiUiState.testSuccess` | None | Working |
| Custom Text Model Input & Apply | `custom_text_model_input` | Allows entering any custom text model ID and applying it | `onSelectTextModel` -> `viewModel.updateTextModel(modelId)` | None | Working |
| Text Chat Model Radio List | `text_model_${model.id}` | Selects active Gemini model for Text Chat (`gemini-2.5-flash`, `gemini-2.5-pro`, `gemini-2.5-flash-lite`, `gemini-2.0-flash`, or synced models) | `onSelectTextModel` -> `viewModel.updateTextModel(model.id)` | None | Working |
| Custom Voice Model Input & Apply | `custom_voice_model_input` | Allows entering any custom Live Voice `bidiGenerateContent` model ID | `onSelectVoiceModel` -> `viewModel.updateVoiceModel(modelId)` | None | Working |
| Live Voice Model Radio List | `voice_model_${model.id}` | Selects active Gemini Live WebSocket model (`gemini-2.5-flash-native-audio-preview-12-2025`, `gemini-2.5-flash-native-audio-preview-09-2025`, `gemini-2.0-flash-live-001`, etc.) | `onSelectVoiceModel` -> `viewModel.updateVoiceModel(model.id)` | None | Working |

---

### 2.7 Screen 7: General Settings (`GeneralSettingsScreen.kt`)
* **File:** `app/src/main/java/com/jarvisai/assistant/ui/screens/GeneralSettingsScreen.kt`
* **Responsibilities:** Configures Gemini Live Voice gender (`MALE`/`FEMALE`), 8 prebuilt Gemini voice personas (`Puck`, `Charon`, `Fenrir`, `Orus`, `Aoede`, `Kore`, `Leda`, `Zephyr`) with live audio sample preview, Floating Call Overlay toggle, speech speed & pitch sliders, assistant custom name, default language, 4 HUD visual themes (`ARC_CYAN`, `DEEP_COBALT`, `STEALTH_DARK`, `HIGH_CONTRAST`), memory/privacy toggles, and local database purge actions.
* **Dependencies:** `JarvisViewModel`, `UserPreferencesRepository`, `JarvisTtsManager`, `GeminiLiveVoiceManager`, `HudComponents`.

| Component / Element | TestTag | Existing Functionality / Action | Connected ViewModel / Callback | Navigation Destination | Working Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Top Bar Back Button | `hud_back_button` | Navigates back to Home Dashboard | `onBack` | `JarvisDestination.HOME` | Working |
| Male / Female Voice Cards | `card_voice_gender_male`, `card_voice_gender_female` | Selects voice gender, auto-selects default persona (`Puck` or `Aoede`), updates active Live Voice session if running, and plays sample preview | `onUpdateVoiceGender(gender)` (`viewModel.updateVoiceGender`) + `onPreviewVoice` (`viewModel.previewVoiceSelection`) | None | Working |
| Stop Voice Preview Button | `btn_stop_voice_preview` | Shown when `isSpeaking` is true; stops ongoing TTS voice preview | `onStopVoicePreview` -> `viewModel.stopSpeaking()` | None | Working |
| Voice Persona Cards (8) & Preview Button | `voice_card_${voiceOpt.id.lowercase()}`, `btn_preview_voice_${voiceOpt.id.lowercase()}` | Selects specific Gemini prebuilt voice (`Puck`, `Charon`, `Fenrir`, `Orus`, `Aoede`, `Kore`, `Leda`, `Zephyr`) and plays a sample voice preview | `onUpdateVoicePersona(id, gender)` (`viewModel.updateVoicePersona`), `onPreviewVoice` (`viewModel.previewVoiceSelection`) | None | Working |
| Floating Call Popup Switch | `switch_settings_floating_overlay` | Enables/disables floating HUD call widget when user leaves app during an active Live Voice call | `onUpdateFloatingOverlay` -> `viewModel.updateFloatingOverlayEnabled(enabled)` | None | Working |
| Speech Rate Slider | *(Slider 0.6x–1.5x)* | Updates voice playback speed multiplier in DataStore and `JarvisTtsManager` | `onUpdateVoiceSpeed` -> `viewModel.updateVoiceSpeed(speed)` | None | Working |
| Voice Pitch Slider | *(Slider 0.6x–1.5x)* | Updates voice pitch multiplier in DataStore and `JarvisTtsManager` | `onUpdateVoicePitch` -> `viewModel.updateVoicePitch(pitch)` | None | Working |
| Assistant Name Input & Save | `settings_assistant_name_input`, `btn_save_assistant_name` | Updates custom assistant name (default `"JARVIS"`) across HUD & prompts | `onUpdateAssistantName` -> `viewModel.updateAssistantName(name)` | None | Working |
| Language Chips (3) | `settings_lang_hinglish`, `settings_lang_hindi`, `settings_lang_english` | Updates assistant language (`HINGLISH`, `HINDI`, `ENGLISH`) | `onUpdateLanguage` -> `viewModel.updateLanguage(lang)` | None | Working |
| HUD Theme Mode Radio Rows (4) | `theme_mode_arc_cyan`, `theme_mode_deep_cobalt`, `theme_mode_stealth_dark`, `theme_mode_high_contrast` | Switches `JarvisThemeMode` dynamically in `MyApplicationTheme` | `onUpdateThemeMode` -> `viewModel.updateThemeMode(mode)` | None | Working |
| Enable Long-Term Memory Switch | `switch_settings_memory_enabled` | Toggles long-term memory feature | `onUpdateMemoryEnabled` -> `viewModel.updateMemoryEnabled(enabled)` | None | Working |
| Auto-Inject Memories Switch | `switch_settings_auto_inject_memories` | Toggles automatic memory retrieval & prompt injection into chats | `onUpdateAutoInjectMemories` -> `viewModel.updateAutoInjectMemories(enabled)` | None | Working |
| Confirm Consequential Actions Switch | `switch_settings_confirm_actions` | Toggles safety confirmation dialogs before alarms/app launches | `onUpdateConfirmActions` -> `viewModel.updateConfirmConsequentialActions(enabled)` | None | Working |
| Redact Telemetry Switch | `switch_settings_privacy_redact` | Toggles sensitive telemetry redaction in logs | `onUpdatePrivacyRedact` -> `viewModel.updatePrivacyRedactTelemetry(enabled)` | None | Working |
| Purge All Chat Logs Button | `btn_settings_clear_chats` | Opens `HudConfirmationDialog` -> deletes all chat sessions & messages | `onClearAllChats` -> `viewModel.clearAllChatHistory()` | None | Working |
| Wipe All Saved Memories Button | `btn_settings_clear_memories` | Opens `HudConfirmationDialog` -> deletes all saved memories | `onClearAllMemories` -> `viewModel.clearAllMemories()` | None | Working |
| `onUpdateAutoSpeakChat` parameter | *(Passed to `GeneralSettingsScreen`, not rendered as a toggle)* | Intentionally disabled (`UserPreferencesRepository` forces `autoSpeakChatReplies = false` so normal text chat never speaks aloud unless Live Voice is used) | `viewModel.updateAutoSpeakChatReplies` | None | Intentionally Disabled / Preserved Signature |

---

## 3. ViewModels, Business Logic & State Management (`JarvisViewModel.kt`)

* **File:** `app/src/main/java/com/jarvisai/assistant/viewmodel/JarvisViewModel.kt`
* `JarvisViewModel` (`AndroidViewModel`) is the central ViewModel coordinating all 7 screens and services:

### 3.1 Core Collaborators Initialized in `JarvisViewModel`
* `database = JarvisDatabase.getInstance(application)` (`jarvisDao`)
* `preferencesRepository = UserPreferencesRepository(application)`
* `secureApiKeyManager = SecureApiKeyManager(application)`
* `geminiApiService = GeminiApiService()`
* `jarvisRepository = JarvisRepository(dao, geminiApiService)`
* `memoryRepository = MemoryRepository(dao)`
* `ttsManager = JarvisTtsManager(application)`
* `phoneControlsManager = PhoneControlsManager(application)`
* `phoneActionManager = PhoneActionManager(application)`
* `liveVoiceManager = GeminiLiveVoiceManager(context = application, scope = viewModelScope)`

### 3.2 Exposed `StateFlow`s
* `settings: StateFlow<AssistantSettings>` (backed by `UserPreferencesRepository.settingsFlow`)
* `chatSessions: StateFlow<List<ChatSessionSummary>>`
* `currentMessages: StateFlow<List<ChatMessage>>`
* `allMemories: StateFlow<List<MemoryItem>>`
* `filteredMemories: StateFlow<List<MemoryItem>>` (combines `allMemories`, `_memorySearchQuery`, `_selectedMemoryCategory`)
* `reminders: StateFlow<List<ReminderItem>>`
* `chatUiState: StateFlow<ChatUiState>` (loading, streaming, partial response, error, memory notification)
* `apiUiState: StateFlow<ApiSettingsUiState>` (hasKey, maskedKey, encryptionActive, testResult, availableTextModels, availableVoiceModels)
* `phoneControlsUiState: StateFlow<PhoneControlsUiState>` (flashlight state, filtered installed apps, feedback banner)
* `liveVoiceState: StateFlow<LiveVoiceSessionState>` (from `GeminiLiveVoiceManager.state`)
* `isSpeaking: StateFlow<Boolean>` (from `JarvisTtsManager.isSpeaking`)
* `assistantStatus: StateFlow<AssistantStatus>` (combines `liveVoiceState`, `chatUiState`, `isSpeaking`, and `_manualStatusOverride`)

### 3.3 Complete List of Public Functions on `JarvisViewModel`
* **Status & Chat:** `setPreviewStatus`, `selectChatSession`, `startNewChat`, `deleteChatSession`, `clearAllChatHistory`, `sendChatMessage`, `retryLastFailedMessage`, `speakMessageText`, `stopSpeaking`, `dismissMemorySavedNotification`.
* **Memory Center:** `setMemorySearchQuery`, `setSelectedMemoryCategory`, `saveApprovedMemory`, `updateMemory`, `toggleMemoryPinned`, `deleteMemory`, `clearAllMemories`.
* **Live Voice & Screen Share:** `startLiveVoiceSession`, `stopLiveVoiceSession`, `startLiveScreenSharing`, `stopLiveScreenSharing`, `toggleLiveVoiceMute`, `interruptLiveVoice`, `sendLiveVoiceTextPrompt`, `executeQuickPhoneCommand`.
* **Phone Controls:** `toggleFlashlight`, `setAppSearchQuery`, `refreshInstalledApps`, `launchApp`, `createAlarm`, `createReminder`, `toggleReminderCompleted`, `deleteReminder`, `initiateConfirmedCall`, `openWifiSettings`, `openBluetoothSettings`, `openAndroidSettings`, `openDisplaySettings`, `openSoundSettings`, `clearPhoneControlFeedback`.
* **API & General Settings:** `saveApiKey`, `deleteApiKey`, `testGeminiConnection`, `syncSupportedModelsFromApi`, `updateTextModel`, `updateVoiceModel`, `updateAssistantName`, `updateLanguage`, `updateThemeMode`, `updateVoiceGender`, `updateVoicePersona`, `previewVoiceSelection`, `updateFloatingOverlayEnabled`, `updateVoiceSpeed`, `updateVoicePitch`, `updateAutoSpeakChatReplies`, `updateMemoryEnabled`, `updateAutoInjectMemories`, `updateConfirmConsequentialActions`, `updatePrivacyRedactTelemetry`.

---

## 4. Gemini API, Live Voice, Audio Pipeline & Live Agent Integrations

### 4.1 Gemini REST & SSE Streaming (`GeminiApiService.kt`)
* **File:** `app/src/main/java/com/jarvisai/assistant/data/network/GeminiApiService.kt`
* **Endpoints:** `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent` and `:streamGenerateContent?alt=sse`, plus `GET https://generativelanguage.googleapis.com/v1beta/models`.
* **Resilience & Fallbacks:**
  * Normalizes deprecated or preview model aliases via `normalizeTextModel` (`DEFAULT_TEXT_MODEL = "gemini-2.5-flash"`) and `normalizeVoiceModel` (`DEFAULT_VOICE_MODEL = "gemini-2.5-flash-native-audio-preview-12-2025"`).
  * Automatically retries across fallback text models (`FALLBACK_TEXT_MODELS`) if a model returns HTTP 404 or unsupported model error.
  * `streamChatCompletion` streams real-time SSE chunks and automatically falls back to `generateChatCompletion` if streaming fails before emitting text.
  * Dynamically constructs system instructions supporting Hinglish/Hindi/English, custom assistant name, and injected relevant memories from `MemoryRepository`.

### 4.2 Gemini Live Bidirectional WebSocket & Audio Pipeline (`GeminiLiveVoiceManager.kt`)
* **File:** `app/src/main/java/com/jarvisai/assistant/service/GeminiLiveVoiceManager.kt`
* **WebSocket Endpoint:** `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key={apiKey}`
* **Model Fallback Chain (`LIVE_MODEL_FALLBACK_CHAIN`):**
  1. `gemini-2.5-flash-native-audio-preview-12-2025`
  2. `gemini-2.5-flash-native-audio-preview-09-2025`
  3. `gemini-2.0-flash-live-001`
  4. `gemini-live-2.5-flash-preview`
  5. `gemini-2.0-flash-exp`
* **Voice Input (Microphone Capture):**
  * Captures 16kHz, 16-bit mono PCM (`AudioFormat.ENCODING_PCM_16BIT`) via `AudioRecord` (`MediaRecorder.AudioSource.VOICE_COMMUNICATION` with fallback to `MIC`).
  * Applies hardware `AcousticEchoCanceler`, `NoiseSuppressor`, and `AutomaticGainControl` when available.
  * Streams 40ms PCM chunks (`1280` bytes) Base64-encoded inside `realtimeInput.mediaChunks` (`audio/pcm;rate=16000`) with automatic fallback to `realtimeInput.audio`.
  * Includes client-side Voice Activity Detection (VAD) and silence timeout (`VAD_SILENCE_TURN_COMPLETE_MS = 1100L`) alongside server VAD.
* **Voice Output (Real-Time Audio Playback & TTS Fallback):**
  * Decodes incoming 24kHz PCM audio chunks from `serverContent.modelTurn.parts[].inlineData` and writes them immediately to a low-latency `AudioTrack` (`SAMPLE_RATE_OUTPUT = 24000`) on a dedicated playback coroutine channel.
  * Supports instant user barge-in (`interruptAiPlayback()`), flushing `AudioTrack` when the user speaks or presses Interrupt.
  * Falls back to `JarvisTtsManager` (Android `TextToSpeech` with Male/Female voice selection) for voice previews or text-only fallback turns.
* **Live Screen Sharing (`MediaProjection`):**
  * Uses `VirtualDisplay` + `ImageReader` (`PixelFormat.RGBA_8888`) scaled to max dimension `768px` JPEG (`quality = 72`) at `1000ms` intervals (1 FPS), streaming frames to Gemini Live via `realtimeInput.mediaChunks` (`image/jpeg`).

### 4.3 Live Agent Tool Calling & Android Device Interaction
* **Files:**
  * `app/src/main/java/com/jarvisai/assistant/service/LiveAgentToolExecutor.kt`
  * `app/src/main/java/com/jarvisai/assistant/service/PhoneActionManager.kt`
  * `app/src/main/java/com/jarvisai/assistant/service/JarvisAccessibilityService.kt`
  * `app/src/main/java/com/jarvisai/assistant/service/LiveVoiceForegroundService.kt`
* **40+ Declared Gemini Live Tools (`buildToolDeclarationsJson()`):**
  * **App & Intent Tools:** `open_app`, `open_youtube`, `search_youtube`, `open_and_play_youtube`, `open_whatsapp`, `send_whatsapp_message`, `open_chrome`, `open_google_search`, `open_url`, `open_camera`, `open_maps`, `open_gmail`, `open_instagram`, `open_telegram`, `open_spotify`, `open_calculator`, `open_calendar`, `open_clock`, `open_files`, `open_play_store`, `open_contacts`, `call_contact`, `dial_number`, `send_sms`, `set_alarm`, `create_timer`, `open_settings_section`, `toggle_flashlight`, `list_installed_apps`.
  * **UI & Accessibility Automation Tools:** `click_on_text`, `click_element`, `tap_coordinates`, `long_press_coordinates`, `type_text`, `clear_text`, `scroll_screen`, `swipe_screen`, `navigate_back`, `navigate_home`, `open_recent_apps`, `open_notifications`, `open_quick_settings`, `read_screen_text`, `get_screen_elements`, `find_element_on_screen`, `wait_seconds`.
* **Accessibility Service (`JarvisAccessibilityService.kt`):**
  * Inspects active window hierarchy (`rootInActiveWindow`), extracts visible text and interactive nodes with screen bounds, clicks elements by text/contentDescription/viewId, types text via `ACTION_SET_TEXT` (with clipboard paste fallback), performs `GestureDescription` taps/long-presses/swipes/scrolls, and executes global actions (`GLOBAL_ACTION_BACK`, `GLOBAL_ACTION_HOME`, `GLOBAL_ACTION_RECENTS`, `GLOBAL_ACTION_NOTIFICATIONS`, `GLOBAL_ACTION_QUICK_SETTINGS`).
* **Foreground Service & Floating Overlay (`LiveVoiceForegroundService.kt`):**
  * Runs with `foregroundServiceType="microphone|mediaProjection"`, acquires partial `WakeLock`, displays persistent notification with Mute & End Call actions, and renders a draggable floating HUD overlay (`TYPE_APPLICATION_OVERLAY`) when the user backgrounds JARVIS AI during a live call.

---

## 5. Permissions, Database, Storage & Reusable UI Components

### 5.1 Declared Permissions & Services (`AndroidManifest.xml`)
* **Install-Time / System Permissions:** `INTERNET`, `ACCESS_NETWORK_STATE`, `VIBRATE`, `WAKE_LOCK`, `MODIFY_AUDIO_SETTINGS`, `FLASHLIGHT`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `SYSTEM_ALERT_WINDOW`, `QUERY_ALL_PACKAGES`, `com.android.alarm.permission.SET_ALARM`.
* **Runtime-Requested Dangerous Permissions:** `RECORD_AUDIO`, `POST_NOTIFICATIONS` (requested in `LiveVoiceScreen.kt` before starting Live Voice), `CAMERA`, `READ_CONTACTS`, `CALL_PHONE`.
* **Registered Services:**
  1. `com.jarvisai.assistant.service.LiveVoiceForegroundService` (`microphone|mediaProjection`)
  2. `com.jarvisai.assistant.service.JarvisAccessibilityService` (`BIND_ACCESSIBILITY_SERVICE` with `@xml/jarvis_accessibility_service_config`)

### 5.2 Local Storage & Encryption
* **Room Database (`JarvisDatabase.kt`, `Entities.kt`, `JarvisDao.kt`):**
  * Database name: `jarvis_assistant_db` (version 1).
  * Entities: `ChatSessionEntity` (`chat_sessions`), `ChatMessageEntity` (`chat_messages`), `MemoryItemEntity` (`memory_items`), `ReminderEntity` (`reminders`).
* **DataStore Preferences (`UserPreferencesRepository.kt`):**
  * File name: `jarvis_user_preferences`.
  * Stores: `assistant_name`, `selected_language`, `text_model`, `voice_model`, `theme_mode`, `voice_gender`, `voice_persona`, `floating_overlay_enabled`, `voice_speed`, `voice_pitch`, `auto_speak_chat`, `memory_enabled`, `auto_inject_memories`, `confirm_consequential_actions`, `privacy_redact_telemetry`, `dynamic_text_models_csv`, `dynamic_voice_models_csv`.
* **Hardware-Backed API Key Security (`SecureApiKeyManager.kt`):**
  * Encrypts Gemini API key using Android Keystore `AES/GCM/NoPadding` (`256-bit` key alias `jarvis_gemini_api_key_master_alias`) and stores Base64 IV + ciphertext in `jarvis_secure_vault_prefs`.

### 5.3 Reusable UI & Theme Components
* `app/src/main/java/com/jarvisai/assistant/ui/components/ArcReactorView.kt`:
  * `ArcReactorView`: Multi-layered animated Canvas Arc Reactor (`outerRotate`, `innerRotate`, `corePulse`, status color interpolation, audio-reactive glow).
* `app/src/main/java/com/jarvisai/assistant/ui/components/HudComponents.kt`:
  * `HudBackgroundContainer`: Radial glow + tactical grid canvas background.
  * `HudFrameCard`: Clipped corner-bracket HUD container card with glowing border and header bar.
  * `HudTopBar`: Standardized screen top bar with optional `hud_back_button` and trailing status/actions.
  * `AssistantStatusPill`: Status indicator pill (`IDLE`, `LISTENING`, `THINKING`, `SPEAKING`).
  * `DashboardModuleButton`: Clickable HUD module card with icon badge and chevron.
  * `HudMetricChip`: Compact telemetry metric badge.
  * `HudConfirmationDialog`: Reusable safety confirmation modal dialog (`hud_confirm_button`, `hud_cancel_button`).
* `app/src/main/java/com/example/ui/theme/`:
  * `Color.kt`, `Theme.kt` (`MyApplicationTheme` supporting `ARC_CYAN`, `DEEP_COBALT`, `STEALTH_DARK`, `HIGH_CONTRAST`), `Type.kt` (`Orbitron`, `Rajdhani`, `ShareTechMono`, `SpaceGrotesk` font families).

---

## 6. Files That Would Need UI-Only Changes in a Future Redesign

During a UI-only redesign, **only the presentation/UI layer files below should be modified**, while keeping all ViewModel signatures, `testTag` identifiers, callbacks, navigation routes, and service/data layers untouched:

1. **Theme & Styling Layer (UI-only):**
   * `app/src/main/java/com/example/ui/theme/Color.kt`
   * `app/src/main/java/com/example/ui/theme/Theme.kt`
   * `app/src/main/java/com/example/ui/theme/Type.kt`
2. **Reusable HUD & Reactor Visual Components (UI-only, preserve parameters & `testTag`s):**
   * `app/src/main/java/com/jarvisai/assistant/ui/components/ArcReactorView.kt`
   * `app/src/main/java/com/jarvisai/assistant/ui/components/HudComponents.kt`
3. **Navigation Shell & Screen Composables (UI-only, preserve all callbacks, state bindings, permission launchers & `testTag`s):**
   * `app/src/main/java/com/jarvisai/assistant/ui/JarvisApp.kt`
   * `app/src/main/java/com/jarvisai/assistant/ui/screens/HomeDashboardScreen.kt`
   * `app/src/main/java/com/jarvisai/assistant/ui/screens/TextChatScreen.kt`
   * `app/src/main/java/com/jarvisai/assistant/ui/screens/LiveVoiceScreen.kt`
   * `app/src/main/java/com/jarvisai/assistant/ui/screens/MemoryCenterScreen.kt`
   * `app/src/main/java/com/jarvisai/assistant/ui/screens/PhoneControlsScreen.kt`
   * `app/src/main/java/com/jarvisai/assistant/ui/screens/ApiSettingsScreen.kt`
   * `app/src/main/java/com/jarvisai/assistant/ui/screens/GeneralSettingsScreen.kt`

---

## 7. Critical Preservation Checklist for Redesign

- [x] Do not delete any existing function in `JarvisViewModel` or any Service/Repository.
- [x] Do not remove any existing screen (all 7 `JarvisDestination` screens must remain).
- [x] Do not remove any navigation route (`HOME`, `CHAT`, `VOICE`, `MEMORY`, `CONTROLS`, `API_SETTINGS`, `GENERAL_SETTINGS`).
- [x] Do not replace real functionality with placeholder UI.
- [x] Do not disconnect buttons from their existing `onClick` / ViewModel actions.
- [x] Do not modify business logic, Gemini REST/SSE API integration, or Gemini Live WebSocket audio streaming.
- [x] Do not modify Live Agent tool declarations or execution (`LiveAgentToolExecutor`, `PhoneActionManager`).
- [x] Do not modify `JarvisAccessibilityService` or `LiveVoiceForegroundService`.
- [x] Do not modify Room database entities/DAOs, DataStore preferences, or Android Keystore encryption.
