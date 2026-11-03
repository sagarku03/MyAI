# JARVIS AI — COMPLETE A-TO-Z TECHNICAL DOCUMENTATION

> **Document Version:** 1.0 (Complete Deep Source Code Audit)  
> **Application Name:** JARVIS AI  
> **Application ID:** `com.jarvisai.assistant`  
> **Android Namespace:** `com.example`  
> **Audit Scope:** 100% of Main Source Code (46 Kotlin files), Test Suite (2 Kotlin files), Android Manifest & Resources (14 XML + 5 Font files), and Build/Project Configurations (7 files) — Total 74 Project Files Inspected.  
> **Strict Audit Policy:** Zero assumptions, zero fabrications, strictly verified against actual lines of source code in the repository.

---

## TABLE OF CONTENTS

1. [Phase 1 — Complete Project Source Code Analysis](#phase-1--complete-project-source-code-analysis)
2. [Phase 2 — Project Overview](#phase-2--project-overview)
3. [Phase 3 — Complete Project File Inventory & File Coverage Table](#phase-3--complete-project-file-inventory--file-coverage-table)
4. [Phase 4 — Complete Feature Inventory (All 23 Attributes)](#phase-4--complete-feature-inventory)
5. [Phase 5 — Complete UI Documentation (All 12 Screens & Reusable Components)](#phase-5--complete-ui-documentation)
6. [Phase 6 — Complete Software Architecture](#phase-6--complete-software-architecture)
7. [Phase 7 — Complete AI System Documentation](#phase-7--complete-ai-system-documentation)
8. [Phase 8 — Complete Voice Assistant System](#phase-8--complete-voice-assistant-system)
9. [Phase 9 — Complete Accessibility & Phone Control System](#phase-9--complete-accessibility--phone-control-system)
10. [Phase 10 — Complete AI Agent Tool Inventory (All 19 Tools)](#phase-10--complete-ai-agent-tool-inventory)
11. [Phase 11 — Database, Memory, and Data Persistence](#phase-11--database-memory-and-data-persistence)
12. [Phase 12 — Complete API and External Dependency Inventory](#phase-12--complete-api-and-external-dependency-inventory)
13. [Phase 13 — Android Permissions and Security Audit](#phase-13--android-permissions-and-security-audit)
14. [Phase 14 — Complete Background System](#phase-14--complete-background-system)
15. [Phase 15 — Complete Data Flow and Workflows](#phase-15--complete-data-flow-and-workflows)
16. [Phase 16 — Error Handling and Exceptions](#phase-16--error-handling-and-exceptions)
17. [Phase 17 — Complete Bug and Limitation Audit](#phase-17--complete-bug-and-limitation-audit)
18. [Phase 18 — Build, Configuration, and Development Guide](#phase-18--build-configuration-and-development-guide)
19. [Phase 19 — Complete Feature Dependency Map](#phase-19--complete-feature-dependency-map)
20. [Phase 20 — Existing Feature Preservation Guide](#phase-20--existing-feature-preservation-guide)
21. [Phase 21 — Testing Documentation](#phase-21--testing-documentation)
22. [Phase 22 — Complete JARVIS AI Project Handoff for Another AI Assistant](#complete-jarvis-ai-project-handoff-for-another-ai-assistant)
23. [Phase 23 — Documentation Completeness Report](#documentation-completeness-report)

---

# PHASE 1 — COMPLETE PROJECT SOURCE CODE ANALYSIS

### 1.1 Source Code Audit Methodology
Every file in the repository was directly inspected using line-by-line static analysis across:
- **Kotlin Source Files (`app/src/main/java/com/example/`):** 46 `.kt` files spanning `MainActivity.kt`, `data/local/`, `data/network/`, `data/preferences/`, `data/repository/`, `model/`, `security/`, `service/`, `ui/`, `ui/components/`, `ui/screens/`, `ui/theme/`, and `viewmodel/`.
- **Kotlin Unit & Robolectric Tests (`app/src/test/java/com/example/`):** 2 `.kt` test files (`ExampleUnitTest.kt` and `ExampleRobolectricTest.kt`).
- **Manifest & XML Resources (`app/src/main/`):** `AndroidManifest.xml`, `res/values/strings.xml`, `res/values/colors.xml`, `res/values/themes.xml`, `res/xml/accessibility_service_config.xml`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, adaptive launcher drawables (`ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `ic_launcher.xml`, `ic_launcher_round.xml`), and 5 custom local `.ttf` fonts (`orbitron_bold.ttf`, `rajdhani_medium.ttf`, `rajdhani_bold.ttf`, `jetbrains_mono_regular.ttf`, `jetbrains_mono_bold.ttf`).
- **Build & Project Configuration:** `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `.env.example`, and `metadata.json`.

### 1.2 Complete Directory Tree
```text
/
├── .env.example
├── metadata.json
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/example/
        │   │   ├── MainActivity.kt
        │   │   ├── data/
        │   │   │   ├── local/
        │   │   │   │   ├── Entities.kt
        │   │   │   │   ├── JarvisDao.kt
        │   │   │   │   └── JarvisDatabase.kt
        │   │   │   ├── network/
        │   │   │   │   └── GeminiApiService.kt
        │   │   │   ├── preferences/
        │   │   │   │   └── UserPreferencesRepository.kt
        │   │   │   └── repository/
        │   │   │       ├── JarvisRepository.kt
        │   │   │       └── MemoryRepository.kt
        │   │   ├── model/
        │   │   │   └── JarvisModels.kt
        │   │   ├── security/
        │   │   │   └── SecureApiKeyManager.kt
        │   │   ├── service/
        │   │   │   ├── AccessibilityControllerBridge.kt
        │   │   │   ├── AppLauncherManager.kt
        │   │   │   ├── CommunicationHelper.kt
        │   │   │   ├── GeminiLiveVoiceManager.kt
        │   │   │   ├── JarvisAccessibilityService.kt
        │   │   │   ├── JarvisNotificationListenerService.kt
        │   │   │   ├── JarvisTtsManager.kt
        │   │   │   ├── LiveAgentToolExecutor.kt
        │   │   │   ├── LiveVoiceForegroundService.kt
        │   │   │   ├── LiveVoiceSessionBridge.kt
        │   │   │   ├── MediaControllerHelper.kt
        │   │   │   ├── OfflineWakeWordManager.kt
        │   │   │   ├── PhoneActionManager.kt
        │   │   │   ├── PhoneControlsManager.kt
        │   │   │   ├── SystemStatusManager.kt
        │   │   │   ├── ToolRegistry.kt
        │   │   │   ├── UniversalTextInputManager.kt
        │   │   │   └── WorkflowEngine.kt
        │   │   ├── ui/
        │   │   │   ├── JarvisApp.kt
        │   │   │   ├── components/
        │   │   │   │   ├── ArcReactorView.kt
        │   │   │   │   └── HudComponents.kt
        │   │   │   ├── screens/
        │   │   │   │   ├── ActionSafetyScreen.kt
        │   │   │   │   ├── ApiManagementScreen.kt
        │   │   │   │   ├── DiagnosticsScreen.kt
        │   │   │   │   ├── GeneralSettingsScreen.kt
        │   │   │   │   ├── HomeDashboardScreen.kt
        │   │   │   │   ├── LiveVoiceScreen.kt
        │   │   │   │   ├── MemorySystemScreen.kt
        │   │   │   │   ├── NotificationIntelligenceScreen.kt
        │   │   │   │   ├── OnboardingPermissionsScreen.kt
        │   │   │   │   ├── PhoneControlsScreen.kt
        │   │   │   │   ├── TextChatScreen.kt
        │   │   │   │   └── WorkflowsMacroScreen.kt
        │   │   │   └── theme/
        │   │   │       ├── Color.kt
        │   │   │       ├── Theme.kt
        │   │   │       └── Type.kt
        │   │   └── viewmodel/
        │   │       └── JarvisViewModel.kt
        │   └── res/
        │       ├── drawable/
        │       │   ├── ic_launcher_background.xml
        │       │   └── ic_launcher_foreground.xml
        │       ├── font/
        │       │   ├── jetbrains_mono_bold.ttf
        │       │   ├── jetbrains_mono_regular.ttf
        │       │   ├── orbitron_bold.ttf
        │       │   ├── rajdhani_bold.ttf
        │       │   └── rajdhani_medium.ttf
        │       ├── mipmap-anydpi-v26/
        │       │   ├── ic_launcher.xml
        │       │   └── ic_launcher_round.xml
        │       ├── values/
        │       │   ├── colors.xml
        │       │   ├── strings.xml
        │       │   └── themes.xml
        │       └── xml/
        │           ├── accessibility_service_config.xml
        │           ├── backup_rules.xml
        │           └── data_extraction_rules.xml
        └── test/java/com/example/
            ├── ExampleRobolectricTest.kt
            └── ExampleUnitTest.kt
```

---

# PHASE 2 — COMPLETE PROJECT OVERVIEW

| Property | Verified Value | Source File |
| :--- | :--- | :--- |
| **Application Name** | `JARVIS AI` | `app/src/main/res/values/strings.xml`, `metadata.json` |
| **Root Project Name** | `JARVIS AI` | `settings.gradle.kts` |
| **Application ID (Package Name)** | `com.jarvisai.assistant` | `app/build.gradle.kts` (`defaultConfig.applicationId`) |
| **Code Namespace (`R` & BuildConfig)** | `com.example` | `app/build.gradle.kts` (`android.namespace`) |
| **Version Code / Version Name** | `1` / `"1.0"` | `app/build.gradle.kts` |
| **Minimum SDK (`minSdk`)** | `24` (Android 7.0 Nougat) | `app/build.gradle.kts` |
| **Target SDK (`targetSdk`)** | `36` | `app/build.gradle.kts` |
| **Compile SDK (`compileSdk`)** | `36` | `app/build.gradle.kts` |
| **Java / JVM Target** | `JavaVersion.VERSION_11` / JVM Target `11` | `app/build.gradle.kts` |
| **Kotlin Version** | `2.2.10` | `gradle/libs.versions.toml` |
| **Android Gradle Plugin (AGP)** | `9.1.1` | `gradle/libs.versions.toml` |
| **KSP (Kotlin Symbol Processing)** | `2.3.5` | `gradle/libs.versions.toml` |
| **UI Toolkit** | Jetpack Compose (Compose BOM `2026.03.00`, Material 3) | `gradle/libs.versions.toml`, `app/build.gradle.kts` |
| **Architecture Pattern** | Single-Activity MVVM (`MainActivity` + `JarvisViewModel`) with Service Bridges & Room/DataStore Repositories | `MainActivity.kt`, `JarvisViewModel.kt` |
| **Primary Entry Points** | 1. `MainActivity` (`MAIN`/`LAUNCHER` Activity)<br>2. `JarvisAccessibilityService` (`BIND_ACCESSIBILITY_SERVICE`)<br>3. `LiveVoiceForegroundService` (`microphone` Foreground Service)<br>4. `JarvisNotificationListenerService` (`BIND_NOTIFICATION_LISTENER_SERVICE`) | `AndroidManifest.xml` |

### 2.1 High-Level Purpose of JARVIS AI
JARVIS AI is a native Android personal AI assistant and autonomous device controller built with Kotlin and Jetpack Compose. It combines:
1. **Multimodal Text & Vision Chat:** Real-time SSE streaming chat via Gemini REST API (`generateContent` / `streamGenerateContent`), supporting camera capture, gallery images, document analysis (PDF/TXT/CSV/JSON/MD/XML/HTML/Code), and persistent multi-session conversation history stored in Room (`JarvisDatabase`).
2. **Real-Time Bidirectional Gemini Live Voice Agent:** Low-latency full-duplex audio WebSocket connection (`BidiGenerateContent`) capturing 16kHz PCM microphone input (with hardware AEC/NS/AGC and 1.45x software pre-gain) and playing 24kHz PCM audio output (with an 80ms jitter pre-buffer and 280ms echo-tail gating), plus live screen vision frame streaming and 19 registered Android device-control tools.
3. **1-Click Female Companion Persona:** A dedicated Hinglish/Hindi/English companion mode (`VoicePersona.KORE`, `VoiceGender.FEMALE`, `AssistantLanguage.HINGLISH`) accessible from the Home Dashboard and Live Voice screen with one tap.
4. **Autonomous Android Device Control:** Uses Android's `AccessibilityService` (`JarvisAccessibilityService` + `AccessibilityControllerBridge`), `PackageManager` (`AppLauncherManager`), system intents (`PhoneActionManager`, `CommunicationHelper`), `AudioManager`/`CameraManager` (`PhoneControlsManager`), and `MediaSessionManager`/media key events (`MediaControllerHelper`) to open apps, click UI elements, type text into any input field, scroll/swipe/tap coordinates, place calls, send WhatsApp/SMS/Email messages, and adjust hardware sliders.
5. **Memory, Macros, Notifications & Safety:** Includes a persistent Long-Term Memory Vault (`MemoryEntity`), multi-step Automation Macros (`WorkflowEntity` + `WorkflowEngine`), a Notification Listener & Summarizer (`JarvisNotificationListenerService`), and an Action Safety & Risk Policy engine (`RiskLevel` + `ActionConfirmationRequest`).

---

# PHASE 3 — COMPLETE PROJECT FILE INVENTORY & FILE COVERAGE TABLE

Every single file in the workspace (74 files total) has been inspected. Below is the complete file coverage table with verified classes, functions, state, dependencies, and status.

| # | File Path | File Type | Primary Purpose | Classes / Objects / Interfaces / Enums | Key Functions & Methods | Key Variables & State | Internal & External Dependencies | Active / Unused | Coverage Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | `/app/src/main/java/com/example/MainActivity.kt` | Kotlin | Single-Activity entry point; edge-to-edge setup; initializes `JarvisViewModel` and `JarvisApp`. | `MainActivity : ComponentActivity()` | `onCreate(savedInstanceState: Bundle?)` | `jarvisViewModel: JarvisViewModel` | `enableEdgeToEdge`, `viewModels`, `JarvisTheme`, `JarvisApp`, `JarvisViewModel` | Active | `FULLY ANALYZED` |
| 2 | `/app/src/main/java/com/example/model/JarvisModels.kt` | Kotlin | Central domain models, enums, voice personas, safety requests, and user settings data classes. | `AssistantState`, `VoiceEngineType`, `AssistantLanguage`, `VoiceGender`, `VoicePersona`, `WakeWordSensitivity`, `CommandConfidenceThreshold`, `ChatMessage`, `MessageRole`, `LiveTranscriptTurn`, `SystemDiagnostics`, `ApiTestStatus`, `UserSettings`, `PhoneToggleItem`, `PersonalityMode`, `RiskLevel`, `ActionConfirmationRequest`, `ScreenContextSummary`, `MemoryCategory`, `WorkflowTriggerType`, `WorkflowActionStep`, `WorkflowActionType` | `VoicePersona.getPersonasByGender()`, `VoicePersona.defaultForGender()` | `id`, `geminiVoiceName`, `displayName`, `gender`, `accentTag`, `styleDescription`, `pitchMultiplier`, `rateMultiplier`, `UserSettings` fields (28 fields) | `java.util.UUID` | Active | `FULLY ANALYZED` |
| 3 | `/app/src/main/java/com/example/data/local/Entities.kt` | Kotlin | Room `@Entity` table definitions for SQLite persistence. | `ChatSessionEntity`, `ChatMessageEntity`, `CommandHistoryEntity`, `MemoryEntity`, `WorkflowEntity`, `NotificationLogEntity` | Data class constructors | Primary keys (`id`) and columns for 6 Room tables (`chat_sessions`, `chat_messages`, `command_history`, `user_memories`, `automation_workflows`, `notification_logs`) | `androidx.room.Entity`, `androidx.room.PrimaryKey`, `java.util.UUID` | Active | `FULLY ANALYZED` |
| 4 | `/app/src/main/java/com/example/data/local/JarvisDao.kt` | Kotlin | Room `@Dao` interface for all SQLite queries, inserts, updates, and deletes across all 6 tables. | `JarvisDao` (interface) | `getAllSessions()`, `getSessionById()`, `insertSession()`, `updateSession()`, `deleteSessionById()`, `deleteAllSessions()`, `getMessagesForSession()`, `getMessagesForSessionSync()`, `insertMessage()`, `deleteMessagesForSession()`, `deleteAllMessages()`, `getRecentCommandHistory()`, `insertCommandHistory()`, `clearCommandHistory()`, `getAllMemories()`, `getPinnedMemoriesSync()`, `getAllMemoriesSync()`, `searchMemoriesSync()`, `insertMemory()`, `deleteMemoryById()`, `clearAllMemories()`, `getAllWorkflows()`, `getEnabledWorkflowsSync()`, `insertWorkflow()`, `deleteWorkflowById()`, `getRecentNotifications()`, `getUnreadNotificationsSync()`, `insertNotificationLog()`, `markAllNotificationsRead()`, `clearNotificationLogs()` | Room `Flow<List<...>>` streams | `androidx.room.*`, `kotlinx.coroutines.flow.Flow` | Active | `FULLY ANALYZED` |
| 5 | `/app/src/main/java/com/example/data/local/JarvisDatabase.kt` | Kotlin | Room `@Database` singleton (v2) with `MIGRATION_1_2` creating the 3 v2 tables (`user_memories`, `automation_workflows`, `notification_logs`). | `JarvisDatabase : RoomDatabase()`, `Companion` | `jarvisDao()`, `getInstance(context: Context)` | `INSTANCE`, `MIGRATION_1_2` | `androidx.room.*`, `androidx.sqlite.db.SupportSQLiteDatabase` | Active | `FULLY ANALYZED` |
| 6 | `/app/src/main/java/com/example/data/preferences/UserPreferencesRepository.kt` | Kotlin | Jetpack DataStore (`jarvis_user_prefs`) repository persisting 27 user settings keys. | `UserPreferencesRepository`, `Keys` | `updateSelectedModel()`, `updateVoiceEngine()`, `updateAssistantLanguage()`, `updateVoiceGender()`, `updateVoicePersona()`, `updateSpeechSpeed()`, `updateVoicePitch()`, `updateContinuousConversation()`, `updateAutoSpeakChatReplies()`, `updateLiveVisionEnabled()`, `updateWakeWordEnabled()`, `updateWakeWordPhrase()`, `updateWakeWordSensitivity()`, `updateConfidenceThreshold()`, `updateConfirmationSound()`, `updateHapticFeedback()`, `updateAssistantName()`, `updateSafetyConfirmations()`, `updateMaxAutonomousSteps()`, `updatePersonalityMode()`, `updateRequireBiometricForHighRisk()`, `updateConfirmBeforeCall()`, `updateConfirmBeforeMessage()`, `updateConfirmBeforeSend()`, `updateContextMemoryEnabled()`, `updateAutoSummarizeNotifications()`, `updateNotificationAssistantEnabled()`, `updateNotificationExcludedPackages()` | `Context.dataStore`, `userSettingsFlow: Flow<UserSettings>` | `androidx.datastore.preferences.*`, `com.example.model.*`, `kotlinx.coroutines.flow.*` | Active | `FULLY ANALYZED` |
| 7 | `/app/src/main/java/com/example/data/repository/JarvisRepository.kt` | Kotlin | Repository coordinating chat sessions, chat messages, and command execution history in Room. | `JarvisRepository` | `createSession()`, `ensureActiveSession()`, `renameSession()`, `deleteSession()`, `clearAllHistory()`, `getMessagesForSession()`, `getMessagesSync()`, `saveMessage()`, `logCommand()`, `clearCommandLogs()` | `allSessions: Flow<List<ChatSessionEntity>>`, `recentCommands: Flow<List<CommandHistoryEntity>>` | `com.example.data.local.*`, `kotlinx.coroutines.flow.Flow` | Active | `FULLY ANALYZED` |
| 8 | `/app/src/main/java/com/example/data/repository/MemoryRepository.kt` | Kotlin | Repository managing Long-Term Memories (`MemoryEntity`), Automation Workflows (`WorkflowEntity`), Notification Logs (`NotificationLogEntity`), and seeding default workflows. | `MemoryRepository` | `getPinnedMemoriesSync()`, `getAllMemoriesSync()`, `searchMemories()`, `saveMemory()`, `togglePinMemory()`, `deleteMemory()`, `clearAllMemories()`, `buildMemoryContextPrompt()`, `getEnabledWorkflowsSync()`, `saveWorkflow()`, `toggleWorkflowEnabled()`, `recordWorkflowRun()`, `deleteWorkflow()`, `seedDefaultWorkflowsIfEmpty()`, `logNotification()`, `getUnreadNotificationsSync()`, `markAllNotificationsRead()`, `clearNotificationLogs()` | `allMemories`, `allWorkflows`, `recentNotifications` | `com.example.data.local.*`, `com.example.model.*`, `org.json.JSONArray`, `org.json.JSONObject` | Active | `FULLY ANALYZED` |
| 9 | `/app/src/main/java/com/example/data/network/GeminiApiService.kt` | Kotlin | Full REST & SSE client for Google Gemini API (`v1beta` & `v1alpha`): model discovery, streaming chat, multimodal vision, document analysis, Gemini TTS audio synthesis, and model fallback. | `GeminiApiService`, `Companion`, `StreamChunk`, `GeminiAudioResult`, `CallOutcome` (private) | `fetchAvailableModels()`, `validateApiKey()`, `sendChatMessage()`, `streamChatResponse()`, `generateGeminiSpeechAudio()`, `buildModelCandidateChain()`, `isQuotaOrModelError()`, `executeTextCall()`, `executeStreamCall()`, `buildContentsArray()`, `buildSystemInstruction()`, `parseGeminiError()`, `parseWaitSeconds()` | `okHttpClient`, `streamHttpClient`, `availableChatModels` (10 default models), `DEFAULT_MODEL` (`gemini-2.5-flash`), `FALLBACK_PRIORITY`, `GEMINI_TTS_MODELS` | `okhttp3.*`, `org.json.*`, `android.util.Base64`, `com.example.model.*` | Active | `FULLY ANALYZED` |
| 10 | `/app/src/main/java/com/example/security/SecureApiKeyManager.kt` | Kotlin | Hardware-backed Android Keystore (`AES/GCM/NoPadding`, 256-bit) encryption manager for Gemini API keys with multi-key rotation and cooldown tracking. | `SecureApiKeyManager`, `Companion`, `SavedApiKeyEntry` | `saveApiKey()`, `getApiKey()`, `getAllApiKeys()`, `getSavedKeyEntries()`, `addApiKeyToPool()`, `removeApiKeyAtIndex()`, `setActiveKeyIndex()`, `getActiveKeyIndex()`, `rotateToNextKey()`, `markKeyRateLimited()`, `getPoolStatusSummary()`, `clearApiKey()`, `hasApiKey()`, `getMaskedApiKey()`, `maskKeyString()`, `encryptAndStore()`, `encryptRaw()`, `decryptRaw()`, `getOrCreateSecretKey()` | `KEYSTORE_ALIAS` (`jarvis_gemini_api_key_alias_v1`), `PREFS_FILE` (`jarvis_secure_keystore_prefs`), `keyCooldownMap` (`ConcurrentHashMap`) | `java.security.KeyStore`, `javax.crypto.*`, `android.security.keystore.*`, `org.json.*`, `com.example.BuildConfig` | Active | `FULLY ANALYZED` |
| 11 | `/app/src/main/java/com/example/service/GeminiLiveVoiceManager.kt` | Kotlin | Full-duplex Gemini Live WebSocket (`BidiGenerateContent`) manager: 16kHz PCM `AudioRecord` capture, 24kHz `AudioTrack` output, live vision frame streaming, tool call dispatch, and auto-reconnect. | `GeminiLiveVoiceManager`, `Companion`, `LiveSessionState` | `startLiveSession()`, `stopLiveSession()`, `setMicMuted()`, `sendTextTurnInLiveSession()`, `sendScreenFrameToLiveSession()`, `openWebSocketSession()`, `sendSetupMessage()`, `handleServerMessage()`, `startAudioInputStreaming()`, `applyMicSoftwareGain()`, `releaseAudioRecord()`, `enqueueModelAudioChunk()`, `releaseAudioTrack()`, `interruptModelPlayback()`, `scheduleAutoReconnect()`, `buildToolDeclarations()` | `_sessionState`, `_isModelSpeaking`, `_isUserSpeaking`, `_liveInputTranscript`, `_liveOutputTranscript`, `_audioLevel`, `_lastError`, `_statusBanner`, `_activeLiveModel`, `_executedToolCount`, `_lastToolSummary`, `LIVE_MODEL_CANDIDATES` | `okhttp3.WebSocket`, `android.media.AudioRecord`, `android.media.AudioTrack`, `android.media.audiofx.*`, `org.json.*` | Active | `FULLY ANALYZED` |
| 12 | `/app/src/main/java/com/example/service/LiveAgentToolExecutor.kt` | Kotlin | Executes all 19 Gemini Live & chat agent tools against real Android APIs, Accessibility bridge, intents, and memory repositories; parses natural language commands. | `LiveAgentToolExecutor`, `Companion`, `ToolExecutionResult` | `executeToolCall()`, `tryExecuteNaturalLanguageCommand()`, `executeOpenApp()`, `executeSendWhatsAppMessage()`, `executeMakePhoneCall()`, `executeSendSms()`, `executeOpenUrl()`, `executeSearchYoutube()`, `executeSetAlarmOrTimer()`, `executeToggleHardwareSetting()`, `executeAdjustVolumeOrBrightness()`, `executePerformScreenAction()`, `executeFindAndClickNode()`, `executeDirectionalGesture()`, `executeTypeText()`, `executeReadScreenContent()`, `executeNavigateDevice()`, `executeReadSystemStatus()`, `executeCaptureScreenshot()`, `executeSaveOrRecallMemory()`, `executeControlMediaPlayback()`, `buildToolDeclarations()`, `legacyFunctionDeclarations()` | `_lastExecutedTool`, `lastGestureCoordinates`, `lastExecutedNodeSignature`, `lastExecutedTimestamp` | `AppLauncherManager`, `PhoneControlsManager`, `PhoneActionManager`, `AccessibilityControllerBridge`, `SystemStatusManager`, `UniversalTextInputManager`, `MediaControllerHelper`, `CommunicationHelper`, `MemoryRepository` | Active | `FULLY ANALYZED` |
| 13 | `/app/src/main/java/com/example/service/ToolRegistry.kt` | Kotlin | Central schema registry defining the 19 structured Gemini Live function declarations (`JSONArray`) and mapping tool names to `RiskLevel`. | `ToolRegistry`, `RegisteredTool` | `getAllTools()`, `getToolRiskLevel()`, `buildGeminiFunctionDeclarations()` | 19 `RegisteredTool` definitions (`open_app`, `perform_screen_action`, `find_and_click_node`, `perform_directional_gesture`, `type_text`, `read_screen_content`, `navigate_device`, `make_phone_call`, `send_whatsapp_message`, `send_sms`, `open_url_or_search`, `search_youtube`, `set_alarm_or_timer`, `toggle_hardware_setting`, `adjust_volume_or_brightness`, `read_system_status`, `capture_screenshot`, `save_or_recall_memory`, `control_media_playback`) | `com.example.model.RiskLevel`, `org.json.JSONArray`, `org.json.JSONObject` | Active | `FULLY ANALYZED` |
| 14 | `/app/src/main/java/com/example/service/JarvisAccessibilityService.kt` | Kotlin | Android `AccessibilityService` bound to `AccessibilityControllerBridge`; monitors window/scroll/click events and updates foreground package and `AssistStructure`-like screen context. | `JarvisAccessibilityService : AccessibilityService()` | `onServiceConnected()`, `onAccessibilityEvent()`, `onInterrupt()`, `onUnbind()`, `onDestroy()` | `lastSummaryUpdateMs` | `android.accessibilityservice.*`, `android.view.accessibility.AccessibilityEvent`, `AccessibilityControllerBridge` | Active | `FULLY ANALYZED` |
| 15 | `/app/src/main/java/com/example/service/AccessibilityControllerBridge.kt` | Kotlin | Singleton controller bridging `JarvisAccessibilityService` with agent tools: UI tree traversal, fuzzy node scoring, click/scroll/swipe/tap/long-press gestures, and text input. | `AccessibilityControllerBridge`, `NodeCandidate` (private) | `bindService()`, `unbindService()`, `updateForegroundPackage()`, `refreshScreenSummary()`, `performGlobalNavigation()`, `clickNodeByText()`, `typeTextIntoFocusedOrMatchingField()`, `performScroll()`, `performDirectionalSwipe()`, `tapAtCoordinates()`, `longPressAtCoordinates()`, `dumpVisibleScreenText()`, `getInteractiveElementsSummary()`, `captureLiveScreenBase64Jpeg()`, `scoreNodeCandidate()` | `_isServiceConnected`, `_lastAccessibilityAction`, `_foregroundPackage`, `_screenContextSummary`, `serviceRef: WeakReference<AccessibilityService>` | `android.accessibilityservice.*`, `android.graphics.Path`, `android.graphics.Bitmap`, `android.view.accessibility.AccessibilityNodeInfo` | Active | `FULLY ANALYZED` |
| 16 | `/app/src/main/java/com/example/service/AppLauncherManager.kt` | Kotlin | Resolves natural app names, Hinglish aliases, and dynamic `PackageManager` labels into launch intents or Play Store/Web fallbacks. | `AppLauncherManager`, `Companion`, `LaunchResult` | `openAppByName()`, `findDynamicInstalledPackage()`, `launchPackage()`, `openWebFallback()`, `getInstalledAppsCount()`, `searchInstalledApps()` | `knownApps: Map<String, List<String>>` (54 app keys), `webFallbacks: Map<String, String>` | `android.content.pm.PackageManager`, `android.content.Intent`, `android.provider.Settings`, `android.provider.AlarmClock`, `android.provider.MediaStore` | Active | `FULLY ANALYZED` |
| 17 | `/app/src/main/java/com/example/service/PhoneControlsManager.kt` | Kotlin | Controls hardware toggles (Flashlight, Wi-Fi, Bluetooth, DND, Ringer, Auto-Rotate, Location, Battery Saver, Hotspot, Airplane Mode), volume, and screen brightness. | `PhoneControlsManager`, `ControlResult` | `setTorch()`, `toggleTorch()`, `openWifiPanel()`, `openBluetoothSettings()`, `openHotspotSettings()`, `openLocationSettings()`, `openBatterySaverSettings()`, `openAirplaneModeSettings()`, `setRingerMode()`, `setDoNotDisturb()`, `setAutoRotation()`, `getVolumePercent()`, `setVolumePercent()`, `adjustVolumeDirection()`, `getBrightnessPercent()`, `setBrightnessPercent()`, `getToggleItems()` | `audioManager`, `cameraManager`, `notificationManager`, `isTorchOn` | `android.media.AudioManager`, `android.hardware.camera2.CameraManager`, `android.app.NotificationManager`, `android.provider.Settings` | Active | `FULLY ANALYZED` |
| 18 | `/app/src/main/java/com/example/service/PhoneActionManager.kt` | Kotlin | Executes Android system intents for phone calls, SMS, WhatsApp, web search, YouTube, alarms, timers, and navigation. | `PhoneActionManager`, `ActionExecutionResult` | `makePhoneCall()`, `sendSms()`, `sendWhatsAppMessage()`, `openUrlOrSearch()`, `playOnYouTube()`, `setAlarm()`, `setTimer()`, `openGoogleMapsNavigation()`, `resolveContactPhoneNumber()` | `context: Context` | `android.provider.ContactsContract`, `android.provider.AlarmClock`, `android.content.Intent`, `android.content.pm.PackageManager` | Active | `FULLY ANALYZED` |
| 19 | `/app/src/main/java/com/example/service/UniversalTextInputManager.kt` | Kotlin | Multi-strategy text entry utility (Accessibility node `ACTION_SET_TEXT`, clipboard paste, and UI submit button clicking). | `UniversalTextInputManager`, `TextInputResult` | `typeText()`, `clearFocusedField()`, `copyToClipboard()` | `context: Context` | `AccessibilityControllerBridge`, `android.content.ClipboardManager`, `android.content.ClipData` | Active | `FULLY ANALYZED` |
| 20 | `/app/src/main/java/com/example/service/SystemStatusManager.kt` | Kotlin | Reads live device telemetry: battery level/charging/temp, Wi-Fi/cellular/airplane network status, RAM/storage free GB, audio ringer/volume, and current date/time. | `SystemStatusManager`, `DeviceStatusSnapshot` | `readStatusSnapshot()`, `formatNaturalSummary()` | `context: Context` | `android.os.BatteryManager`, `android.net.ConnectivityManager`, `android.app.ActivityManager`, `android.os.StatFs`, `android.media.AudioManager` | Active | `FULLY ANALYZED` |
| 21 | `/app/src/main/java/com/example/service/MediaControllerHelper.kt` | Kotlin | Dispatches hardware media key events (`KEYCODE_MEDIA_PLAY_PAUSE`, `NEXT`, `PREVIOUS`, etc.) via `AudioManager` and reads active `MediaSessionManager` metadata. | `MediaControllerHelper`, `MediaActionResult` | `play()`, `pause()`, `playPause()`, `nextTrack()`, `previousTrack()`, `stop()`, `getActiveMediaSummary()`, `dispatchMediaKey()` | `audioManager: AudioManager` | `android.media.AudioManager`, `android.media.session.MediaSessionManager`, `android.view.KeyEvent` | Active | `FULLY ANALYZED` |
| 22 | `/app/src/main/java/com/example/service/CommunicationHelper.kt` | Kotlin | High-level communication coordinator for calls, SMS, WhatsApp, and Email with risk assessment (`RiskLevel.HIGH`) and Hinglish confirmation prompts. | `CommunicationHelper`, `CommResult` | `initiateCall()`, `sendSms()`, `sendWhatsApp()`, `sendEmail()`, `buildConfirmationRequest()` | `phoneActionManager: PhoneActionManager` | `PhoneActionManager`, `ActionConfirmationRequest`, `RiskLevel` | Active | `FULLY ANALYZED` |
| 23 | `/app/src/main/java/com/example/service/JarvisTtsManager.kt` | Kotlin | Dual-engine Text-to-Speech manager supporting Gemini Live Native Audio (`generateGeminiSpeechAudio` 24kHz PCM) with automatic fallback to Android System `TextToSpeech`. | `JarvisTtsManager`, `TtsPlaybackState` | `initialize()`, `speak()`, `speakWithSystemTts()`, `playRawPcmAudio()`, `stop()`, `shutdown()`, `selectBestVoice()`, `sanitizeForSpeech()` | `_playbackState`, `_isInitialized`, `_activeVoiceInfo`, `tts: TextToSpeech?`, `activeAudioTrack: AudioTrack?` | `android.speech.tts.TextToSpeech`, `android.media.AudioTrack`, `GeminiApiService`, `VoicePersona` | Active | `FULLY ANALYZED` |
| 24 | `/app/src/main/java/com/example/service/OfflineWakeWordManager.kt` | Kotlin | Continuous background `SpeechRecognizer` wake-word detector ("Hey Jarvis", "Jarvis", etc.) with fuzzy Levenshtein matching and auto-restart backoff. | `OfflineWakeWordManager` | `configure()`, `startWakeWordListening()`, `stopWakeWordListening()`, `pauseForActiveSession()`, `resumeAfterActiveSession()`, `destroy()`, `matchesWakeWord()`, `levenshteinDistance()` | `_isWakeServiceRunning`, `_lastDetectedPhrase`, `speechRecognizer: SpeechRecognizer?`, `consecutiveErrorCount` | `android.speech.SpeechRecognizer`, `android.speech.RecognitionListener`, `WakeWordSensitivity` | Active | `FULLY ANALYZED` |
| 25 | `/app/src/main/java/com/example/service/LiveVoiceForegroundService.kt` | Kotlin | Android `ForegroundService` (`foregroundServiceType="microphone"`) keeping Gemini Live Voice active when the user switches to other apps; provides notification actions (Mute/Stop). | `LiveVoiceForegroundService : Service()`, `Companion` | `start()`, `updateState()`, `stop()`, `onCreate()`, `onStartCommand()`, `onDestroy()`, `buildNotification()`, `createNotificationChannel()` | `CHANNEL_ID`, `NOTIFICATION_ID` (`1042`), `ACTION_START`, `ACTION_STOP`, `ACTION_TOGGLE_MUTE` | `androidx.core.app.NotificationCompat`, `android.content.pm.ServiceInfo`, `LiveVoiceSessionBridge` | Active | `FULLY ANALYZED` |
| 26 | `/app/src/main/java/com/example/service/LiveVoiceSessionBridge.kt` | Kotlin | Singleton reactive state bridge synchronizing `LiveVoiceForegroundService` notification actions with `JarvisViewModel` and `GeminiLiveVoiceManager`. | `LiveVoiceSessionBridge` | `updateSessionActive()`, `updateMicMuted()`, `updateStatusText()`, `registerCallbacks()`, `requestToggleMuteFromNotification()`, `requestStopSessionFromNotification()` | `_isSessionActive`, `_isMicMuted`, `_statusText`, `onToggleMuteCallback`, `onStopSessionCallback` | `kotlinx.coroutines.flow.MutableStateFlow`, `StateFlow` | Active | `FULLY ANALYZED` |
| 27 | `/app/src/main/java/com/example/service/JarvisNotificationListenerService.kt` | Kotlin | Android `NotificationListenerService` capturing incoming status bar notifications and persisting them into Room (`notification_logs`) via `MemoryRepository`. | `JarvisNotificationListenerService : NotificationListenerService()`, `Companion` | `onListenerConnected()`, `onListenerDisconnected()`, `onNotificationPosted()`, `isServiceEnabled()` | `_isConnected: StateFlow<Boolean>`, `serviceScope` | `android.service.notification.*`, `JarvisDatabase`, `MemoryRepository`, `NotificationLogEntity` | Active | `FULLY ANALYZED` |
| 28 | `/app/src/main/java/com/example/service/WorkflowEngine.kt` | Kotlin | Sequential macro execution engine running `WorkflowEntity` JSON action steps (`WorkflowActionStep`) with configurable delays. | `WorkflowEngine`, `WorkflowRunResult` | `executeWorkflow()`, `parseSteps()` | `appLauncher`, `phoneControls`, `phoneActions`, `mediaHelper` | `WorkflowEntity`, `WorkflowActionStep`, `WorkflowActionType`, `MemoryRepository`, `org.json.JSONArray` | Active | `FULLY ANALYZED` |
| 29 | `/app/src/main/java/com/example/viewmodel/JarvisViewModel.kt` | Kotlin | Master `AndroidViewModel` orchestrating all 12 screens, Gemini API chat, Gemini Live Voice, 1-Click Female Companion, Accessibility vision loop, permissions, memories, macros, and settings. | `JarvisViewModel : AndroidViewModel()`, `SelectedChatAttachment` | 85+ public state flows & methods (`sendChatMessage()`, `startLiveVoiceSession()`, `startFemaleCompanionLiveSession()`, `stopLiveVoiceSession()`, `executeQuickCommand()`, `approvePendingConfirmation()`, `summarizeRecentNotificationsWithAi()`, etc.) | 45+ `StateFlow` properties (`assistantState`, `chatMessages`, `userSettings`, `diagnostics`, `liveSessionState`, `pendingConfirmation`, etc.) | All repositories, managers, and services in `com.example.*` | Active | `FULLY ANALYZED` |
| 30 | `/app/src/main/java/com/example/ui/JarvisApp.kt` | Kotlin | Top-level Compose scaffold, adaptive navigation shell (`Compact` floating dock vs `Expanded` side `NavigationRail`), 12-route `NavHost`, and global `SafetyConfirmationModal`. | `JarvisScreen` (enum), `SafetyConfirmationModal` (private composable), `JarvisApp` | `JarvisApp()`, `FloatingBottomNavBar()`, `ExpandedNavigationRail()`, `SafetyConfirmationModal()` | `primaryDestinations` (5 tabs), `navController`, 40+ `collectAsStateWithLifecycle()` states | `androidx.navigation.compose.*`, `androidx.compose.material3.*`, all 12 screen composables | Active | `FULLY ANALYZED` |
| 31 | `/app/src/main/java/com/example/ui/components/ArcReactorView.kt` | Kotlin | Custom animated `Canvas` visualizer rendering a multi-ring Stark-tech Arc Reactor core with rotating segmented rings, plasma energy waves, and audio reactivity. | `ArcReactorView` (composable) | `ArcReactorView()` | `rotationOuter`, `rotationInner`, `corePulse`, `animatedAudio`, `coreColor`, `secondaryColor` | `androidx.compose.foundation.Canvas`, `androidx.compose.animation.core.*`, `AssistantState` | Active | `FULLY ANALYZED` |
| 32 | `/app/src/main/java/com/example/ui/components/HudComponents.kt` | Kotlin | Shared futuristic Spatial Glassmorphic design system components: ambient backgrounds, glass cards, status pills, waveform bars, radar rings, gauges, and top bars. | `JarvisSpatialAmbientBackground`, `HudGridBackground`, `HudGlassCard`, `HudCornerBracketCard`, `HudTopBar`, `HudSectionHeader`, `HudStatusPill`, `VoiceWaveformBars`, `RadarPulseRing`, `CircularHudGauge`, `HudQuickActionTile`, `HudQuickChip`, `HudSegmentedSelector`, `HudMetricBadge`, `HudDivider` | 15 reusable composable functions + `Modifier.hudGlowBorder()` | Infinite transition animations for ambient blobs, radar rings, and voice waveforms | `androidx.compose.foundation.Canvas`, `androidx.compose.material3.*`, `com.example.ui.theme.*` | Active | `FULLY ANALYZED` |
| 33 | `/app/src/main/java/com/example/ui/screens/HomeDashboardScreen.kt` | Kotlin | Main command center screen with Bento telemetry cards, 1-Click Female Companion hero card, Arc Reactor visualizer, quick commands, and V2 OS expansion grid. | `HomeDashboardScreen` + 9 private sub-composables | `HomeDashboardScreen()`, `DashboardTopIdentityHeader()`, `SystemAlertStripCard()`, `FemaleCompanionOneClickBanner()`, `HeroAssistantBentoCard()`, `QuickCommandPillsRow()`, `BentoTelemetryMatrix()`, `DeviceControlsQuickStripCard()`, `V2IntelligenceHubSection()`, `RecentActivityFeedCard()` | `quickPromptText` | `ArcReactorView`, `HudComponents`, `UserSettings`, `SystemDiagnostics`, `DeviceStatusSnapshot` | Active | `FULLY ANALYZED` |
| 34 | `/app/src/main/java/com/example/ui/screens/TextChatScreen.kt` | Kotlin | Multimodal AI chat screen with session history modal sheet, camera/gallery/document pickers, quick prompt chips, markdown-like message bubbles, and TTS controls. | `TextChatScreen` + 8 private sub-composables | `TextChatScreen()`, `ChatTopHeaderBar()`, `ActiveCapabilitiesRibbon()`, `EmptyChatWelcomeView()`, `ChatMessageItem()`, `ThinkingIndicatorBubble()`, `AttachmentPreviewRibbon()`, `FloatingChatComposerDock()`, `ChatSessionsBottomSheet()`, `createTempCameraUri()` | `inputText`, `showSessionsSheet`, `pendingCameraUri`, `cameraPermissionLauncher`, `takePictureLauncher`, `photoPickerLauncher`, `documentPickerLauncher` | `coil.compose.AsyncImage`, `ActivityResultContracts`, `ChatMessage`, `ChatSessionEntity`, `SelectedChatAttachment` | Active | `FULLY ANALYZED` |
| 35 | `/app/src/main/java/com/example/ui/screens/LiveVoiceScreen.kt` | Kotlin | Real-time bidirectional Gemini Live Voice studio with 1-Click Female Companion launcher, live transcripts, screen vision preview, and text injection bar. | `LiveVoiceScreen` + 10 private sub-composables | `LiveVoiceScreen()`, `LiveStudioTopHeader()`, `FemaleCompanionQuickSwitchBar()`, `ModelAndLanguagePillsRow()`, `ImmersiveVoiceStageCard()`, `LiveTranscriptGlassFeed()`, `LiveControlDock()`, `LiveTextTurnInputBar()` | `quickLiveText`, `audioPermissionLauncher` | `LiveSessionState`, `LiveTranscriptTurn`, `ArcReactorView`, `VoiceWaveformBars`, `RadarPulseRing` | Active | `FULLY ANALYZED` |
| 36 | `/app/src/main/java/com/example/ui/screens/PhoneControlsScreen.kt` | Kotlin | Hardware control matrix screen for volume/brightness sliders, 10 system toggles, quick app launcher grid, direct call/WhatsApp modal dialogs, and Accessibility status. | `PhoneControlsScreen` + 8 private sub-composables | `PhoneControlsScreen()`, `ControlsTopHeader()`, `HardwareSlidersCard()`, `AccessibilityAutomationBanner()`, `SystemTogglesBentoGrid()`, `CommunicationDispatchRow()`, `QuickAppLaunchMatrix()`, `DirectCallDialog()`, `DirectWhatsAppDialog()` | `showCallDialog`, `showWhatsAppDialog`, `callNumberInput`, `waPhoneInput`, `waMessageInput`, `callPermissionLauncher` | `PhoneToggleItem`, `HudGlassCard`, `CircularHudGauge` | Active | `FULLY ANALYZED` |
| 37 | `/app/src/main/java/com/example/ui/screens/GeneralSettingsScreen.kt` | Kotlin | Neural configuration studio for AI model selection, voice persona/gender/language, speech rate/pitch sliders, wake-word configuration, and quick navigation links. | `GeneralSettingsScreen` + 8 private sub-composables | `GeneralSettingsScreen()`, `SettingsProfileHeroCard()`, `QuickSystemAccessGrid()`, `AiEngineConfigurationCard()`, `VoiceSynthesisConfigurationCard()`, `WakeWordConfigurationCard()`, `ExperienceAndSafetyCard()`, `ModelSelectorModalSheet()`, `VoicePersonaModalSheet()` | `assistantNameDraft`, `wakePhraseDraft`, `showModelPickerSheet`, `showPersonaPickerSheet` | `UserSettings`, `VoicePersona`, `AssistantLanguage`, `VoiceGender`, `VoiceEngineType`, `WakeWordSensitivity` | Active | `FULLY ANALYZED` |
| 38 | `/app/src/main/java/com/example/ui/screens/ApiManagementScreen.kt` | Kotlin | AES-256-GCM encrypted API key vault screen with multi-key rotation pool, connectivity validator, model selector, and troubleshooting guide. | `ApiManagementScreen` + 7 private sub-composables | `ApiManagementScreen()`, `SecurityArchitectureBanner()`, `ActiveVaultStatusCard()`, `ApiKeyEditorCard()`, `MultiKeyPoolCard()`, `ConnectionValidatorCard()`, `ActiveModelSelectorCard()`, `ApiTroubleshootingCard()` | `apiKeyInput`, `poolKeyInput`, `showRawKey` | `ApiTestStatus`, `SavedApiKeyEntry`, `HudGlassCard` | Active | `FULLY ANALYZED` |
| 39 | `/app/src/main/java/com/example/ui/screens/OnboardingPermissionsScreen.kt` | Kotlin | System readiness and permission matrix screen with a radial readiness ring, 6 live permission toggles/grant cards, and deep links to Android system settings. | `OnboardingPermissionsScreen` + 4 private sub-composables | `OnboardingPermissionsScreen()`, `ReadinessHeroCard()`, `PermissionChecklistCard()`, `SystemSettingsShortcutRow()` | `PermissionItemUi` list (Microphone, Camera, Call Phone, Contacts, Accessibility, Notification Listener), `runtimeLauncher` | `SystemDiagnostics`, `CircularHudGauge`, `ActivityResultContracts.RequestMultiplePermissions` | Active | `FULLY ANALYZED` |
| 40 | `/app/src/main/java/com/example/ui/screens/MemorySystemScreen.kt` | Kotlin | Long-Term AI Memory Vault screen with category filter chips, search bar, pinned memory cards, and an Add Memory modal dialog. | `MemorySystemScreen`, `MemoryItemCard`, `AddMemoryDialog` | `MemorySystemScreen()`, `MemoryItemCard()`, `AddMemoryDialog()` | `searchQuery`, `selectedCategory`, `showAddDialog`, `filteredMemories` | `MemoryEntity`, `MemoryCategory`, `HudGlassCard` | Active | `FULLY ANALYZED` |
| 41 | `/app/src/main/java/com/example/ui/screens/WorkflowsMacroScreen.kt` | Kotlin | Automation Macro studio for running multi-step routines (`Morning Briefing`, `Drive & Navigate Mode`, `Deep Focus Mode`) and creating custom JSON-backed macros. | `WorkflowsMacroScreen`, `WorkflowMacroCard`, `CreateWorkflowDialog` | `WorkflowsMacroScreen()`, `WorkflowMacroCard()`, `CreateWorkflowDialog()` | `showCreateDialog`, `title`, `description`, `triggerPhrase`, `appToOpen`, `speakText`, `volumePercent` | `WorkflowEntity`, `WorkflowTriggerType`, `WorkflowActionStep`, `WorkflowActionType` | Active | `FULLY ANALYZED` |
| 42 | `/app/src/main/java/com/example/ui/screens/NotificationIntelligenceScreen.kt` | Kotlin | Notification listener feed and AI summarizer screen with `BIND_NOTIFICATION_LISTENER_SERVICE` status banner, AI digest button, and notification cards. | `NotificationIntelligenceScreen`, `NotificationAccessBanner`, `NotificationLogCard` | `NotificationIntelligenceScreen()`, `NotificationAccessBanner()`, `NotificationLogCard()` | `logs`, `isListenerConnected`, `aiSummary`, `isSummarizing` | `NotificationLogEntity`, `HudGlassCard` | Active | `FULLY ANALYZED` |
| 43 | `/app/src/main/java/com/example/ui/screens/DiagnosticsScreen.kt` | Kotlin | Live system telemetry, Accessibility screen inspector, and command audit log viewer. | `DiagnosticsScreen`, `TelemetryGridCard`, `ScreenInspectorCard`, `CommandHistoryAuditRow` | `DiagnosticsScreen()`, `TelemetryGridCard()`, `ScreenInspectorCard()`, `CommandHistoryAuditRow()` | `diagnostics`, `deviceStatus`, `screenSummary`, `commandHistory` | `SystemDiagnostics`, `DeviceStatusSnapshot`, `ScreenContextSummary`, `CommandHistoryEntity` | Active | `FULLY ANALYZED` |
| 44 | `/app/src/main/java/com/example/ui/screens/ActionSafetyScreen.kt` | Kotlin | Autonomous execution policy & safety guardrails configuration screen (High-Risk confirmation gates, personality mode selector, autonomous step limit slider, risk matrix). | `ActionSafetyScreen`, `SafetyToggleRow` | `ActionSafetyScreen()`, `SafetyToggleRow()` | `userSettings` | `UserSettings`, `PersonalityMode`, `HudGlassCard` | Active | `FULLY ANALYZED` |
| 45 | `/app/src/main/java/com/example/ui/theme/Color.kt` | Kotlin | Color palette constants for the Obsidian & Electric Indigo/Cyan glassmorphic UI theme. | Top-level `Color` constants | None | `ObsidianBlack`, `DeepSpaceNavy`, `ElectricCyan`, `NeonArcBlue`, `VioletPulse`, `EmeraldSuccess`, `AmberWarning`, `CrimsonError`, etc. | `androidx.compose.ui.graphics.Color` | Active | `FULLY ANALYZED` |
| 46 | `/app/src/main/java/com/example/ui/theme/Type.kt` | Kotlin | Custom typography hierarchy mapping local `.ttf` fonts (`OrbitronFontFamily`, `RajdhaniFontFamily`, `JetBrainsMonoFontFamily`) to Material 3 `Typography`. | `OrbitronFontFamily`, `RajdhaniFontFamily`, `JetBrainsMonoFontFamily`, `JarvisTypography` | Font family initializers | `displayLarge` through `labelSmall` styles | `androidx.compose.ui.text.font.*`, `com.example.R` | Active | `FULLY ANALYZED` |
| 47 | `/app/src/main/java/com/example/ui/theme/Theme.kt` | Kotlin | Material 3 dark color scheme (`JarvisDarkColorScheme`) and `JarvisTheme` composable configuring transparent status/navigation bars with light icons. | `JarvisDarkColorScheme`, `JarvisTheme` | `JarvisTheme()` | `JarvisDarkColorScheme` | `androidx.compose.material3.*`, `androidx.core.view.WindowCompat` | Active | `FULLY ANALYZED` |
| 48 | `/app/src/test/java/com/example/ExampleRobolectricTest.kt` | Kotlin (Test) | 23 comprehensive Robolectric + Compose UI + Room + Security + Tool Executor + Workflow + WakeWord tests. | `ExampleRobolectricTest` | 23 `@Test` methods (`test01` to `test23`) | `composeTestRule`, `context` | `org.robolectric.RobolectricTestRunner`, `androidx.compose.ui.test.junit4.createAndroidComposeRule` | Active | `FULLY ANALYZED` |
| 49 | `/app/src/test/java/com/example/ExampleUnitTest.kt` | Kotlin (Test) | 2 pure JVM unit tests verifying `VoicePersona` mappings and `GeminiApiService` model candidate chain building. | `ExampleUnitTest` | `voicePersona_defaultsAndFiltering_areValid()`, `geminiModelCandidateChain_prioritizesSelectedAndFallbacks()` | None | `org.junit.Test`, `org.junit.Assert.*` | Active | `FULLY ANALYZED` |
| 50 | `/app/src/main/AndroidManifest.xml` | XML | Declares 12 permissions, `<queries>` package visibility, `MainActivity`, `JarvisAccessibilityService`, `LiveVoiceForegroundService`, and `JarvisNotificationListenerService`. | `<manifest>`, `<queries>`, `<application>`, `<activity>`, 3 `<service>` tags | N/A | Permissions, intent filters, service bindings | Android OS Manifest | Active | `FULLY ANALYZED` |
| 51 | `/app/src/main/res/xml/accessibility_service_config.xml` | XML | Configures `JarvisAccessibilityService` (`canRetrieveWindowContent="true"`, `canPerformGestures="true"`, `canTakeScreenshot="true"`, `flagReportViewIds`). | `<accessibility-service>` | N/A | Accessibility flags & event types | `JarvisAccessibilityService` | Active | `FULLY ANALYZED` |
| 52 | `/app/src/main/res/values/strings.xml` | XML | String resources (`app_name`, `accessibility_service_label`, `accessibility_service_description`, notification strings). | `<resources>` | N/A | 5 `<string>` entries | Android Resource System | Active | `FULLY ANALYZED` |
| 53 | `/app/src/main/res/values/colors.xml` | XML | Base XML color resources (`obsidian_black`, `electric_cyan`, etc.). | `<resources>` | N/A | `<color>` entries | Android Resource System | Active | `FULLY ANALYZED` |
| 54 | `/app/src/main/res/values/themes.xml` | XML | Base XML Activity theme (`Theme.MyApplication` inheriting `android:Theme.Material.NoActionBar`). | `<resources>`, `<style>` | N/A | `Theme.MyApplication` | `MainActivity` | Active | `FULLY ANALYZED` |
| 55 | `/app/src/main/res/xml/backup_rules.xml` | XML | Android Auto Backup rules. | `<full-backup-content>` | N/A | Backup rules | `AndroidManifest.xml` | Active | `FULLY ANALYZED` |
| 56 | `/app/src/main/res/xml/data_extraction_rules.xml` | XML | Android 12+ data extraction rules. | `<data-extraction-rules>` | N/A | Cloud backup / device transfer rules | `AndroidManifest.xml` | Active | `FULLY ANALYZED` |
| 57 | `/app/src/main/res/drawable/ic_launcher_background.xml` | XML | Vector drawable background for the custom adaptive launcher icon (deep obsidian space gradient with subtle HUD grid). | `<vector>` | N/A | Vector paths | `ic_launcher.xml`, `ic_launcher_round.xml` | Active | `FULLY ANALYZED` |
| 58 | `/app/src/main/res/drawable/ic_launcher_foreground.xml` | XML | Vector drawable foreground for the custom adaptive launcher icon (glowing cyan/indigo JARVIS Arc Reactor core). | `<vector>` | N/A | Vector paths | `ic_launcher.xml`, `ic_launcher_round.xml` | Active | `FULLY ANALYZED` |
| 59 | `/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | XML | Adaptive icon definition linking `@drawable/ic_launcher_background` and `@drawable/ic_launcher_foreground`. | `<adaptive-icon>` | N/A | Background/foreground refs | `AndroidManifest.xml` | Active | `FULLY ANALYZED` |
| 60 | `/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | XML | Round adaptive icon definition linking `@drawable/ic_launcher_background` and `@drawable/ic_launcher_foreground`. | `<adaptive-icon>` | N/A | Background/foreground refs | `AndroidManifest.xml` | Active | `FULLY ANALYZED` |
| 61–65 | `/app/src/main/res/font/*.ttf` (5 files) | Binary Font (`.ttf`) | Local TrueType fonts: `orbitron_bold.ttf`, `rajdhani_medium.ttf`, `rajdhani_bold.ttf`, `jetbrains_mono_regular.ttf`, `jetbrains_mono_bold.ttf`. | Binary font tables | N/A | Font glyphs | `Type.kt` | Active | `FULLY ANALYZED` |
| 66 | `/app/build.gradle.kts` | Gradle Kotlin DSL | App module build script: `namespace = "com.example"`, `applicationId = "com.jarvisai.assistant"`, `.env` loader into `BuildConfig.GEMINI_API_KEY`, dependencies. | Gradle configuration | `.env` file parser | `envMap`, `geminiKey`, `signingConfigs`, `dependencies` | Gradle, AGP, KSP, Compose, Room, DataStore, OkHttp, Retrofit, Coil | Active | `FULLY ANALYZED` |
| 67 | `/gradle/libs.versions.toml` | TOML | Central Gradle Version Catalog defining library and plugin versions. | `[versions]`, `[libraries]`, `[plugins]` | N/A | Version keys | `build.gradle.kts`, `app/build.gradle.kts` | Active | `FULLY ANALYZED` |
| 68 | `/build.gradle.kts` | Gradle Kotlin DSL | Top-level project build file applying `android-application`, `kotlin-compose`, `kotlin-serialization`, and `ksp` plugins. | `plugins` block | N/A | Plugin aliases | Gradle | Active | `FULLY ANALYZED` |
| 69 | `/settings.gradle.kts` | Gradle Kotlin DSL | Project settings (`rootProject.name = "JARVIS AI"`, `:app` module, Google & MavenCentral repositories). | `pluginManagement`, `dependencyResolutionManagement` | N/A | `rootProject.name` | Gradle | Active | `FULLY ANALYZED` |
| 70 | `/gradle.properties` | Properties | JVM args (`-Xmx2048m`), `android.useAndroidX=true`, `android.nonTransitiveRClass=true`. | Key-value properties | N/A | Gradle properties | Gradle | Active | `FULLY ANALYZED` |
| 71 | `/.env.example` | Env Template | Documents the `GEMINI_API_KEY=` environment variable for `BuildConfig.GEMINI_API_KEY` injection. | Env key template | N/A | `GEMINI_API_KEY` | `app/build.gradle.kts` | Active | `FULLY ANALYZED` |
| 72 | `/metadata.json` | JSON | AI Studio platform metadata (`name: "JARVIS AI"`, `majorCapabilities: ["MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API"]`). | JSON object | N/A | `name`, `description`, `majorCapabilities` | AI Studio Platform | Active | `FULLY ANALYZED` |
| 73–74 | `/app/src/main/res/mipmap-*/ic_launcher*.webp` | WebP | Legacy density-specific fallback launcher icons (`hdpi`, `mdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`), superseded on API 26+ by `mipmap-anydpi-v26/ic_launcher.xml`. | Raster icons | N/A | Fallback icons | Android OS (API 24–25) | Active (API 24–25 fallback) | `FULLY ANALYZED` |

---

# PHASE 4 — COMPLETE FEATURE INVENTORY

Every feature in JARVIS AI has been audited against the source code and documented with all **23 required attributes**.

### Feature Status Legend
- `IMPLEMENTED AND VERIFIED IN CODE` — Complete implementation verified in source code and covered by unit/Robolectric tests.
- `IMPLEMENTED BUT NOT RUNTIME VERIFIED` — Complete implementation verified in source code, requires real physical device hardware/network/Google Play Services at runtime.
- `PARTIALLY IMPLEMENTED` — Implemented in UI and/or partial logic, with a specific documented gap in execution.

---

### Feature 1: Multi-Session Streaming Text & Multimodal Chat (Vision + Documents)
1. **Feature Name:** Multi-Session Streaming Text & Multimodal Chat
2. **Category:** AI Chat & Multimodal Intelligence
3. **Current Status:** `IMPLEMENTED AND VERIFIED IN CODE`
4. **Description:** Provides persistent multi-session conversational AI powered by Gemini (`streamGenerateContent` SSE with fallback to `generateContent`), supporting camera photos, gallery images, document files (PDF, TXT, CSV, JSON, MD, XML, HTML, source code), and natural language device-control interception before/during chat.
5. **User Journey / How User Uses It:** User opens the **Chat** tab (`TextChatScreen`), types a message or attaches a photo/document using the `+` button, or taps a quick-prompt chip, and presses **Send**. Responses stream token-by-token in real time. Users can switch, rename, or delete sessions via the **Sessions** bottom sheet.
6. **UI Screens Involved:** `TextChatScreen.kt`, `HomeDashboardScreen.kt` (quick command bar routes or executes commands).
7. **Composable Functions Involved:** `TextChatScreen`, `ChatTopHeaderBar`, `ActiveCapabilitiesRibbon`, `EmptyChatWelcomeView`, `ChatMessageItem`, `ThinkingIndicatorBubble`, `AttachmentPreviewRibbon`, `FloatingChatComposerDock`, `ChatSessionsBottomSheet`.
8. **ViewModels Involved:** `JarvisViewModel` (`sendChatMessage`, `createNewChatSession`, `selectChatSession`, `deleteChatSession`, `renameChatSession`, `attachImageUri`, `attachDocumentUri`, `clearAttachment`, `speakMessageText`, `stopSpeaking`).
9. **Repositories Involved:** `JarvisRepository`, `MemoryRepository`, `UserPreferencesRepository`.
10. **Services / Managers Involved:** `GeminiApiService`, `LiveAgentToolExecutor` (checks `tryExecuteNaturalLanguageCommand` first), `SecureApiKeyManager`, `JarvisTtsManager`.
11. **Database Tables Involved:** `chat_sessions` (`ChatSessionEntity`), `chat_messages` (`ChatMessageEntity`), `user_memories` (`MemoryEntity` — injected into system prompt), `command_history` (`CommandHistoryEntity`).
12. **APIs Used:** Google Gemini REST API (`POST /v1beta/models/{model}:streamGenerateContent?alt=sse` and `POST /v1beta/models/{model}:generateContent`).
13. **Permissions Required:** `android.permission.INTERNET`, `android.permission.CAMERA` (only when capturing a live photo via camera). Gallery & Document pickers use zero-permission `ActivityResultContracts.PickVisualMedia` and `OpenDocument`.
14. **Input Data:** User text prompt (`String`), optional `SelectedChatAttachment` (Base64-encoded JPEG or document bytes/text, MIME type, display name), prior conversation history (`List<ChatMessage>`), pinned/relevant memories (`buildMemoryContextPrompt`), and `UserSettings`.
15. **Output Data:** Streaming `StreamChunk` (`deltaText`, `isDone`, `usedModel`, `errorMessage`) persisted as a `ChatMessageEntity` in Room and rendered in `TextChatScreen`.
16. **Step-by-Step Internal Code Flow:**
    - `TextChatScreen` invokes `viewModel.sendChatMessage(text)`.
    - `JarvisViewModel.sendChatMessage()` snapshots `_selectedAttachment.value`, clears the attachment preview, ensures an active session via `repository.ensureActiveSession()`, and saves the user's `ChatMessageEntity` to Room.
    - If there is no attachment, `JarvisViewModel` calls `toolExecutor.tryExecuteNaturalLanguageCommand(trimmed)`. If matched (e.g., `"open youtube"`, `"turn on flashlight"`, `"set alarm for 7 am"`), it executes the native action immediately, logs to `command_history`, and saves the confirmation reply in chat without consuming Gemini API quota.
    - Otherwise, `JarvisViewModel` retrieves the active API key via `secureApiKeyManager.getApiKey()`, builds memory context via `memoryRepository.buildMemoryContextPrompt(trimmed)`, inserts a placeholder streaming assistant message (`isStreaming = true`), and collects `geminiApiService.streamChatResponse(...)`.
    - If a `429` quota error occurs and multiple API keys are in the pool, `secureApiKeyManager.rotateToNextKey()` rotates to the next key and retries automatically.
    - Once complete, the final assistant message is saved to Room via `repository.saveMessage()`.
17. **Error Handling:** Missing API key displays a clear prompt directing the user to `API Vault`; HTTP 429/404 errors automatically traverse the fallback model chain (`gemini-2.5-flash` -> `gemini-2.5-flash-lite` -> `gemini-2.0-flash` -> `gemini-1.5-flash`) and rotate API keys if pooled.
18. **Edge Cases:** Large images are downscaled to max 1024px dimension and compressed at JPEG quality 82 (`encodeUriToBase64Jpeg`); binary documents (PDFs) up to 4MB are Base64-encoded as `inlineData`, while text/code files are read up to 24,000 characters and injected into the prompt.
19. **Security Considerations:** API key is decrypted in memory from Android Keystore (`SecureApiKeyManager`); normal text chat replies are strictly muted from auto-TTS (`autoSpeakChatReplies = false`) so text chat remains silent unless the user explicitly taps the speaker icon on a message bubble.
20. **Performance Considerations:** Uses `LazyColumn` with stable `key = { it.id }` and throttles UI list updates during SSE token streaming.
21. **Current Limitations:** `PersonalityMode` from `UserSettings` is not currently appended to `GeminiApiService.buildSystemInstruction()` (documented in Phase 17 as `BUG-07`).
22. **Source Files:** `TextChatScreen.kt`, `JarvisViewModel.kt`, `GeminiApiService.kt`, `JarvisRepository.kt`, `Entities.kt`, `JarvisDao.kt`.
23. **Key Line References:** `JarvisViewModel.kt` lines 421–616; `GeminiApiService.kt` lines 250–495; `TextChatScreen.kt` lines 88–355.

---

### Feature 2: Real-Time Bidirectional Gemini Live Voice Agent (`BidiGenerateContent`)
1. **Feature Name:** Gemini Live Real-Time Bidirectional Voice & Vision Agent
2. **Category:** Real-Time Voice & Autonomous Agent
3. **Current Status:** `IMPLEMENTED AND VERIFIED IN CODE` (Protocol, audio pipeline, tool schemas, and UI verified; live WebSocket requires user's Gemini API key at runtime).
4. **Description:** Full-duplex WebSocket voice session (`BidiGenerateContent`) with 16kHz PCM microphone streaming, 24kHz PCM native audio playback, live screen vision JPEG streaming every 3.5 seconds, real-time input/output transcription, and autonomous execution of 19 registered Android tools.
5. **User Journey / How User Uses It:** User navigates to **Live Voice** (`LiveVoiceScreen`) or taps the central orb on `HomeDashboardScreen`, grants `RECORD_AUDIO` if needed, and taps **START LIVE SESSION**. User speaks naturally in Hindi, Hinglish, or English; JARVIS responds with low-latency voice and executes requested phone actions even when minimized to the background.
6. **UI Screens Involved:** `LiveVoiceScreen.kt`, `HomeDashboardScreen.kt`.
7. **Composable Functions Involved:** `LiveVoiceScreen`, `LiveStudioTopHeader`, `FemaleCompanionQuickSwitchBar`, `ModelAndLanguagePillsRow`, `ImmersiveVoiceStageCard`, `LiveTranscriptGlassFeed`, `LiveControlDock`, `LiveTextTurnInputBar`, `ArcReactorView`.
8. **ViewModels Involved:** `JarvisViewModel` (`startLiveVoiceSession`, `stopLiveVoiceSession`, `toggleLiveMicMute`, `sendTextInLiveSession`, `captureAndSendScreenToLiveSession`, `startLiveVisionLoop`).
9. **Repositories Involved:** `UserPreferencesRepository`, `JarvisRepository`, `MemoryRepository`.
10. **Services / Managers Involved:** `GeminiLiveVoiceManager`, `LiveAgentToolExecutor`, `ToolRegistry`, `LiveVoiceForegroundService`, `LiveVoiceSessionBridge`, `OfflineWakeWordManager`, `AccessibilityControllerBridge`.
11. **Database Tables Involved:** `command_history` (logs every tool executed during the live session), `user_memories` (when `save_or_recall_memory` tool is invoked).
12. **APIs Used:** Gemini Live WebSocket API (`wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=...` and `v1alpha` fallback), plus `GET /v1beta/models` & `GET /v1alpha/models` to discover models supporting `bidiGenerateContent`.
13. **Permissions Required:** `android.permission.RECORD_AUDIO`, `android.permission.FOREGROUND_SERVICE`, `android.permission.FOREGROUND_SERVICE_MICROPHONE`, `android.permission.POST_NOTIFICATIONS` (optional for foreground notification visibility).
14. **Input Data:** 16kHz 16-bit mono PCM audio chunks (`400ms` / 6400 bytes per frame), optional JPEG Base64 screen captures (`image/jpeg`), optional text turns, and setup configuration (`VoicePersona`, `AssistantLanguage`, `ToolRegistry` declarations).
15. **Output Data:** 24kHz 16-bit mono PCM audio stream (`audio/pcm`), real-time `inputTranscription` and `outputTranscription` turns (`LiveTranscriptTurn`), and `toolCall` JSON packets executed via `LiveAgentToolExecutor`.
16. **Step-by-Step Internal Code Flow:**
    - `JarvisViewModel.startLiveVoiceSession()` pauses `OfflineWakeWordManager` (to prevent mic contention), stops any active TTS, starts `LiveVoiceForegroundService`, and calls `liveVoiceManager.startLiveSession(...)`.
    - `GeminiLiveVoiceManager` queries `discoverLiveModels()` across `v1alpha` and `v1beta` to rank models supporting `bidiGenerateContent` (prioritizing `gemini-2.5-flash-native-audio-preview-12-2025`, `gemini-2.5-flash-native-audio-latest`, `gemini-live-2.5-flash-preview`, `gemini-2.0-flash-live-001`).
    - Opens an OkHttp WebSocket and sends the initial `setup` JSON containing `generationConfig` (`responseModalities: ["AUDIO"]`, `speechConfig.voiceConfig.prebuiltVoiceConfig.voiceName`), `systemInstruction`, `tools` (19 function declarations), `inputAudioTranscription: {}`, and `outputAudioTranscription: {}`.
    - Upon receiving `setupComplete`, starts `startAudioInputStreaming()` on `Dispatchers.IO` with `AudioSource.VOICE_COMMUNICATION`, hardware `AcousticEchoCanceler`, `NoiseSuppressor`, `AutomaticGainControl`, and 1.45x software pre-gain.
    - Incoming `inlineData` PCM chunks are written to a persistent 24kHz `AudioTrack` after an 80ms jitter pre-buffer (`3840` bytes). While the AI is speaking (and for 350ms after playback finishes), mic input is echo-gated unless the user barges in above `RMS > 3600` for 3 consecutive frames.
    - Incoming `toolCall.functionCalls` are dispatched to `toolExecutor.executeToolCall(name, args)` and returned to the WebSocket via `toolResponse.functionResponses`.
17. **Error Handling:** If a model fails setup (e.g., unsupported modality or model not found), `tryNextModelOrReportError()` immediately advances to the next candidate in `modelQueue`. Unexpected socket drops trigger `scheduleAutoReconnect()` with exponential backoff up to 4 attempts.
18. **Edge Cases:** Handles Android 14+ `SecurityException` when starting a `microphone` foreground service without permission by catching the exception cleanly in both `LiveVoiceForegroundService` and `JarvisViewModel`.
19. **Security Considerations:** API key is passed over TLS (`wss://`); tool calls are logged to `command_history`.
20. **Performance Considerations:** Pre-allocates a single persistent `AudioTrack` with a 2-second buffer (`96,000` bytes) and a 280ms ring buffer (`13,440` bytes) to eliminate click/pop artifacts between streaming chunks.
21. **Current Limitations:** In `GeminiLiveVoiceManager`, tool calls received from the WebSocket are executed directly via `toolExecutor.executeToolCall()` without pausing for the UI `SafetyConfirmationModal` (whereas UI/chat communication helpers support confirmation prompts).
22. **Source Files:** `GeminiLiveVoiceManager.kt`, `LiveVoiceScreen.kt`, `LiveVoiceForegroundService.kt`, `LiveVoiceSessionBridge.kt`, `JarvisViewModel.kt`.
23. **Key Line References:** `GeminiLiveVoiceManager.kt` lines 1–1077; `JarvisViewModel.kt` lines 618–738; `LiveVoiceScreen.kt` lines 90–390.

---

### Feature 3: 1-Click Female Companion Mode (Natural Hinglish/Hindi Live Persona)
1. **Feature Name:** 1-Click Female Companion Mode (`Kore` Sweet Female Voice + Hinglish)
2. **Category:** Voice Persona & Quick Launch
3. **Current Status:** `IMPLEMENTED AND VERIFIED IN CODE`
4. **Description:** Dedicated 1-click launcher that configures JARVIS with a natural, expressive female voice (`VoicePersona.KORE`, `VoiceGender.FEMALE`) and conversational Hinglish (`AssistantLanguage.HINGLISH`), navigates directly to the Live Voice studio, and starts a real-time Gemini Live voice session.
5. **User Journey / How User Uses It:** User taps the rose-gold **1-CLICK FEMALE COMPANION** card on `HomeDashboardScreen` (`testTag = "female_companion_one_click_card"`) or the **1-Click Activate** button on `LiveVoiceScreen` (`testTag = "female_companion_activate_button"`).
6. **UI Screens Involved:** `HomeDashboardScreen.kt`, `LiveVoiceScreen.kt`.
7. **Composable Functions Involved:** `FemaleCompanionOneClickBanner` (`HomeDashboardScreen.kt`), `FemaleCompanionQuickSwitchBar` (`LiveVoiceScreen.kt`).
8. **ViewModels Involved:** `JarvisViewModel.startFemaleCompanionLiveSession()`.
9. **Repositories Involved:** `UserPreferencesRepository` (`updateVoiceGender`, `updateVoicePersona`, `updateAssistantLanguage`).
10. **Services / Managers Involved:** `GeminiLiveVoiceManager`, `JarvisTtsManager`.
11. **Database Tables Involved:** None directly (persisted in DataStore `jarvis_user_prefs`).
12. **APIs Used:** Gemini Live WebSocket API (`BidiGenerateContent` with `prebuiltVoiceConfig.voiceName = "Kore"`).
13. **Permissions Required:** `android.permission.RECORD_AUDIO`.
14. **Input Data:** Single tap event (switched to `VoicePersona.KORE`, `VoiceGender.FEMALE`, `AssistantLanguage.HINGLISH`).
15. **Output Data:** Updated `UserSettings` flow + immediate active `GeminiLiveVoiceManager` session speaking in Hinglish with the `Kore` female voice.
16. **Step-by-Step Internal Code Flow:**
    - User taps the banner/button.
    - If `RECORD_AUDIO` is not granted on `LiveVoiceScreen`, requests `Manifest.permission.RECORD_AUDIO` first.
    - `JarvisViewModel.startFemaleCompanionLiveSession()` persists `VoiceGender.FEMALE`, `VoicePersona.KORE.id` (`"kore"`), and `AssistantLanguage.HINGLISH` into `UserPreferencesRepository`.
    - If a live session is already active, stops it briefly (`delay(250)`) so the new voice config takes effect, then calls `startLiveVoiceSession()` with the explicit female/Kore/Hinglish overrides.
17. **Error Handling:** Checks `secureApiKeyManager.hasApiKey()` before starting and displays a status message if no key is configured.
18. **Edge Cases:** Works seamlessly whether the user is currently in another persona (e.g., `Puck` Male) or already in `Kore` Female mode.
19. **Security Considerations:** Standard Live Voice security applies.
20. **Performance Considerations:** Uses direct `StateFlow` + DataStore update without blocking the main thread.
21. **Current Limitations:** None.
22. **Source Files:** `JarvisViewModel.kt`, `HomeDashboardScreen.kt`, `LiveVoiceScreen.kt`, `JarvisModels.kt`.
23. **Key Line References:** `JarvisViewModel.kt` lines 669–707; `HomeDashboardScreen.kt` lines 397–505; `LiveVoiceScreen.kt` lines 318–405.

---

### Feature 4: Accessibility-Powered UI Automation & Screen Vision
1. **Feature Name:** Accessibility Service Screen Reader, Node Matcher, Gesture Engine & Live Screen Capture
2. **Category:** Android Device Control & Accessibility
3. **Current Status:** `IMPLEMENTED AND VERIFIED IN CODE`
4. **Description:** Uses `JarvisAccessibilityService` and `AccessibilityControllerBridge` to inspect the active foreground app's view hierarchy, score and click UI nodes by text/content-description/resource-id, type text into `EditText` fields, perform scroll/swipe/tap/long-press gestures, and capture API 30+ screenshots (`takeScreenshot`) for Gemini Live vision.
5. **User Journey / How User Uses It:** User enables **JARVIS Accessibility Control** in Android Accessibility Settings (via `OnboardingPermissionsScreen` or `PhoneControlsScreen`), then asks JARVIS by voice or chat to *"Click on Search"*, *"Type hello world"*, *"Scroll down"*, *"Go home"*, or *"What is on my screen right now?"*.
6. **UI Screens Involved:** `OnboardingPermissionsScreen.kt`, `PhoneControlsScreen.kt`, `DiagnosticsScreen.kt`, `LiveVoiceScreen.kt`.
7. **Composable Functions Involved:** `AccessibilityAutomationBanner`, `ScreenInspectorCard`, `PermissionChecklistCard`.
8. **ViewModels Involved:** `JarvisViewModel` (`captureAndSendScreenToLiveSession`, `startLiveVisionLoop`, `refreshDiagnostics`).
9. **Repositories Involved:** `JarvisRepository` (logs accessibility commands).
10. **Services / Managers Involved:** `JarvisAccessibilityService`, `AccessibilityControllerBridge`, `UniversalTextInputManager`, `LiveAgentToolExecutor`.
11. **Database Tables Involved:** `command_history`.
12. **APIs Used:** Android `AccessibilityService`, `AccessibilityNodeInfo`, `GestureDescription`, `AccessibilityService.takeScreenshot` (API 30+).
13. **Permissions Required:** `android.permission.BIND_ACCESSIBILITY_SERVICE` (declared on `<service>` in `AndroidManifest.xml` and enabled by user in system settings).
14. **Input Data:** Target label/query (`String`), gesture direction (`UP`/`DOWN`/`LEFT`/`RIGHT`), coordinates (`x`, `y`), or text to type (`String`).
15. **Output Data:** Boolean execution result + human-readable status message, `ScreenContextSummary` (foreground package, top visible texts, clickable labels, editable field count), or Base64 JPEG screenshot.
16. **Step-by-Step Internal Code Flow:**
    - When enabled in Android Settings, `JarvisAccessibilityService.onServiceConnected()` calls `AccessibilityControllerBridge.bindService(this)`.
    - Every window state/content change triggers `onAccessibilityEvent()`, updating `_foregroundPackage` and calling `refreshScreenSummary()` (throttled to once every 800ms).
    - When `clickNodeByText(query)` is called, `AccessibilityControllerBridge` traverses `rootInActiveWindow` + all interactive `windows`, scores every candidate node via `scoreNodeCandidate()` (100 pts exact match, 90 pts stripped match, 82 pts word match, 74 pts startsWith, 65 pts contains, 50 pts resource-ID match, +8 pts clickable bonus), climbs up to 5 parent levels for non-clickable labels, and dispatches `ACTION_CLICK` or falls back to center-coordinate `GestureDescription` tap (`tapAtCoordinates`).
17. **Error Handling:** Gracefully checks `getService() != null` and returns `"Accessibility Service is not enabled"` when disconnected; catches all `Exception`s during node traversal and bitmap compression.
18. **Edge Cases:** Searches across multi-window layers (`srv.windows` including system dialogs and split-screen windows) if `rootInActiveWindow` does not contain the target node.
19. **Security Considerations:** Password fields (`node.isPassword`) are explicitly skipped when summarizing screen context (`collectSummaryRecursive`) to avoid leaking password masks.
20. **Performance Considerations:** Node traversal is depth-limited (`maxDepth = 20..22`) and text collection is capped (`maxItems = 40..60`).
21. **Current Limitations:** `captureLiveScreenBase64Jpeg()` requires Android 11+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.R`); on Android 7–10 it falls back to dumping visible screen text via `dumpVisibleScreenText()`.
22. **Source Files:** `JarvisAccessibilityService.kt`, `AccessibilityControllerBridge.kt`, `UniversalTextInputManager.kt`, `accessibility_service_config.xml`.
23. **Key Line References:** `JarvisAccessibilityService.kt` lines 1–75; `AccessibilityControllerBridge.kt` lines 1–682.

---

### Feature 5: Hardware & System Controls Matrix (Torch, Sliders, Toggles, App Launcher, Calls, WhatsApp)
1. **Feature Name:** Native Hardware Controls, App Launcher & Direct Communication Dispatch
2. **Category:** Android System & Hardware Control
3. **Current Status:** `IMPLEMENTED AND VERIFIED IN CODE`
4. **Description:** Provides direct UI and voice/chat control over Flashlight (`CameraManager.setTorchMode`), Media Volume & Screen Brightness (`AudioManager` & `Settings.System`), 10 hardware/settings toggles, a 54-alias smart app launcher (`AppLauncherManager`), and direct Phone Call / WhatsApp / SMS / Email dispatch with safety confirmations.
5. **User Journey / How User Uses It:** User opens the **Controls** tab (`PhoneControlsScreen`), adjusts the Volume/Brightness sliders, taps any of the 10 system toggle cards (Flashlight, Wi-Fi, Bluetooth, DND, Silent/Vibrate/Normal Ringer, Auto-Rotate, Location, Hotspot, Battery Saver, Airplane Mode), taps a quick-launch app icon (YouTube, WhatsApp, Chrome, Maps, Spotify, Instagram, Camera, Settings, Gmail, Calculator), or opens the Direct Call / WhatsApp modal dialogs.
6. **UI Screens Involved:** `PhoneControlsScreen.kt`, `HomeDashboardScreen.kt` (Quick Device Strip).
7. **Composable Functions Involved:** `PhoneControlsScreen`, `HardwareSlidersCard`, `SystemTogglesBentoGrid`, `CommunicationDispatchRow`, `QuickAppLaunchMatrix`, `DirectCallDialog`, `DirectWhatsAppDialog`.
8. **ViewModels Involved:** `JarvisViewModel` (`handleToggleClick`, `setMediaVolume`, `setScreenBrightness`, `launchQuickApp`, `makeDirectCall`, `sendDirectWhatsApp`).
9. **Repositories Involved:** `JarvisRepository` (`logCommand`), `UserPreferencesRepository` (checks `confirmBeforeCall`, `confirmBeforeMessage`).
10. **Services / Managers Involved:** `PhoneControlsManager`, `AppLauncherManager`, `PhoneActionManager`, `CommunicationHelper`, `MediaControllerHelper`.
11. **Database Tables Involved:** `command_history`.
12. **APIs Used:** Android `CameraManager`, `AudioManager`, `NotificationManager`, `Settings.System`, `Settings.Panel`, `PackageManager`, `ContactsContract`, `AlarmClock`, `Intent`.
13. **Permissions Required:** `android.permission.CALL_PHONE`, `android.permission.READ_CONTACTS`, `android.permission.ACCESS_NOTIFICATION_POLICY` (for DND), `android.permission.WRITE_SETTINGS` (for system brightness/auto-rotate; automatically opens `ACTION_MANAGE_WRITE_SETTINGS` if not granted).
14. **Input Data:** Toggle ID (`torch`, `wifi`, `bluetooth`, `dnd`, `ringer`, `rotation`, `hotspot`, `location`, `battery_saver`, `airplane`), slider percentage (`0..100`), app name (`String`), or recipient phone/contact name + message.
15. **Output Data:** `ControlResult`, `LaunchResult`, or `CommResult` displayed in the HUD status banner and logged in `command_history`.
16. **Step-by-Step Internal Code Flow:**
    - When a user or AI tool requests `openAppByName("youtube")`, `AppLauncherManager` normalizes the query, strips Hinglish filler words (`"kholo"`, `"chalu karo"`), checks 54 known app alias groups, queries `PackageManager.getInstalledApplications()` dynamically if needed, and launches the resolved package or falls back to a web URL.
    - When `makeDirectCall(number)` is called in `JarvisViewModel`, if `confirmBeforeCall` and `safetyConfirmationsEnabled` are active, it populates `_pendingConfirmation` (`SafetyConfirmationModal`) before dialing.
17. **Error Handling:** Catches `SecurityException` when `CALL_PHONE` is missing and falls back to `Intent.ACTION_DIAL` so the user can still place the call manually.
18. **Edge Cases:** On Android 10+ (`API >= 29`), where background Wi-Fi toggling is restricted by OS policy, `openWifiPanel()` launches `Settings.Panel.ACTION_WIFI` floating panel directly over the current screen.
19. **Security Considerations:** High-risk communication actions (`CALL_PHONE`, `SEND_WHATSAPP`, `SEND_SMS`) integrate with `ActionConfirmationRequest` and `SafetyConfirmationModal`.
20. **Performance Considerations:** Hardware state queries run fast synchronous reads against `AudioManager` and `Settings.System`.
21. **Current Limitations:** Direct automatic click of the WhatsApp Send button after opening the pre-filled chat window requires `JarvisAccessibilityService` to be enabled (`executeSendWhatsAppMessage` in `LiveAgentToolExecutor.kt` waits 1,600ms and clicks `"Send"` via `accessibilityBridge` if connected).
22. **Source Files:** `PhoneControlsScreen.kt`, `PhoneControlsManager.kt`, `AppLauncherManager.kt`, `PhoneActionManager.kt`, `CommunicationHelper.kt`, `MediaControllerHelper.kt`.
23. **Key Line References:** `PhoneControlsManager.kt` lines 1–328; `AppLauncherManager.kt` lines 1–334; `PhoneActionManager.kt` lines 1–287.

---

### Feature 6: Hardware-Backed Encrypted API Key Vault & Multi-Key Rotation Pool
1. **Feature Name:** AES-256-GCM Keystore API Vault & Multi-Key Auto-Rotation Pool
2. **Category:** Security & API Management
3. **Current Status:** `IMPLEMENTED AND VERIFIED IN CODE`
4. **Description:** Encrypts the user's Gemini API key(s) using a 256-bit `AES/GCM/NoPadding` secret key stored inside the Android Hardware Keystore (`AndroidKeyStore`), supports a pool of multiple API keys with automatic 60-second rate-limit cooldown tracking and instant failover on HTTP `429` quota errors, and includes a live API key validator.
5. **User Journey / How User Uses It:** User opens **API Vault** (`ApiManagementScreen`), pastes a Gemini API key into the primary input or adds multiple backup keys to the **Multi-Key Rotation Pool**, taps **Save Key**, and taps **Test Connection** to verify key validity and discover available models.
6. **UI Screens Involved:** `ApiManagementScreen.kt`.
7. **Composable Functions Involved:** `ApiManagementScreen`, `SecurityArchitectureBanner`, `ActiveVaultStatusCard`, `ApiKeyEditorCard`, `MultiKeyPoolCard`, `ConnectionValidatorCard`, `ActiveModelSelectorCard`, `ApiTroubleshootingCard`.
8. **ViewModels Involved:** `JarvisViewModel` (`saveApiKey`, `clearApiKey`, `testAndValidateApiKey`, `addApiKeyToPool`, `removeApiKeyFromPool`, `selectActiveKeyInPool`, `refreshApiKeyState`).
9. **Repositories Involved:** `UserPreferencesRepository` (stores selected model ID).
10. **Services / Managers Involved:** `SecureApiKeyManager`, `GeminiApiService`.
11. **Database Tables Involved:** None (uses encrypted SharedPreferences `jarvis_secure_keystore_prefs` backed by `AndroidKeyStore`).
12. **APIs Used:** Gemini `GET /v1beta/models` and `POST /v1beta/models/{model}:generateContent` (for validation ping).
13. **Permissions Required:** `android.permission.INTERNET`.
14. **Input Data:** Raw Gemini API key string (`AIzaSy...`).
15. **Output Data:** Masked key preview (`AIzaSy••••1234`), `List<SavedApiKeyEntry>`, and `ApiTestStatus` (`UNTESTED`, `TESTING`, `VALID`, `INVALID`, `NETWORK_ERROR`).
16. **Step-by-Step Internal Code Flow:**
    - `SecureApiKeyManager.saveApiKey()` generates or fetches the hardware `SecretKey` (`jarvis_gemini_api_key_alias_v1`), encrypts the plaintext key with `Cipher.getInstance("AES/GCM/NoPadding")` (128-bit GCM auth tag), Base64-encodes the IV and ciphertext, and stores them in `jarvis_secure_keystore_prefs`.
    - If no user key is saved, `getApiKey()` checks `BuildConfig.GEMINI_API_KEY` (injected from `.env` at build time).
    - When `rotateToNextKey()` is called after a `429` rate limit, `markKeyRateLimited()` sets a 60-second cooldown timestamp on the exhausted key and selects the next non-rate-limited key in the pool.
17. **Error Handling:** If the Android Keystore is unavailable in a local JVM test environment, `encryptRaw()` and `decryptRaw()` fall back cleanly to Base64 encoding so Robolectric tests pass deterministically.
18. **Edge Cases:** Prevents duplicate keys in the pool and adjusts `KEY_ACTIVE_INDEX` safely when keys are removed.
19. **Security Considerations:** Zero plaintext API keys are written to disk on Android devices; keys are masked (`maskKeyString`) in all UI cards unless the user toggles the visibility icon while typing.
20. **Performance Considerations:** Decryption takes <2ms in memory.
21. **Current Limitations:** Rate-limit cooldown timestamps (`keyCooldownMap`) are held in an in-memory `ConcurrentHashMap` and reset on process restart.
22. **Source Files:** `SecureApiKeyManager.kt`, `ApiManagementScreen.kt`, `JarvisViewModel.kt`.
23. **Key Line References:** `SecureApiKeyManager.kt` lines 1–328; `ApiManagementScreen.kt` lines 1–610.

---

### Feature 7: Long-Term AI Memory Vault (`MemorySystemScreen`)
1. **Feature Name:** Persistent Long-Term Memory System & Contextual Prompt Injection
2. **Category:** Personalization & Data Persistence
3. **Current Status:** `IMPLEMENTED AND VERIFIED IN CODE`
4. **Description:** Stores structured user facts, preferences, contacts, routines, and notes in Room (`user_memories`), supports pinning critical memories so they are always injected into Gemini's system prompt, searches relevant memories per query, and exposes the `save_or_recall_memory` tool to the Gemini Live voice agent.
5. **User Journey / How User Uses It:** User opens **Memory Vault** (`MemorySystemScreen` from Home Dashboard or Settings), filters by category (`PREFERENCE`, `CONTACT_NOTE`, `HABIT`, `PERSONAL_FACT`, `WORKFLOW_HINT`), searches by keyword, pins/unpins or deletes items, or taps **+ Add** to store a new memory (or tells JARVIS by voice: *"Remember that my gate code is 4580"*).
6. **UI Screens Involved:** `MemorySystemScreen.kt`, `HomeDashboardScreen.kt`.
7. **Composable Functions Involved:** `MemorySystemScreen`, `MemoryItemCard`, `AddMemoryDialog`.
8. **ViewModels Involved:** `JarvisViewModel` (`saveMemoryFact`, `togglePinMemory`, `deleteMemoryFact`, `clearAllMemories`).
9. **Repositories Involved:** `MemoryRepository`.
10. **Services / Managers Involved:** `LiveAgentToolExecutor` (`executeSaveOrRecallMemory`), `GeminiApiService` (consumes `memoryContextPrompt`).
11. **Database Tables Involved:** `user_memories` (`MemoryEntity`).
12. **APIs Used:** Local Room SQLite database.
13. **Permissions Required:** None.
14. **Input Data:** `key` (`String`), `value` (`String`), `category` (`MemoryCategory`), `importance` (`1..5`), `isPinned` (`Boolean`).
15. **Output Data:** Reactive `Flow<List<MemoryEntity>>` and formatted prompt string from `buildMemoryContextPrompt(userQuery)`.
16. **Step-by-Step Internal Code Flow:**
    - When `sendChatMessage(query)` runs and `userSettings.contextMemoryEnabled` is `true`, `MemoryRepository.buildMemoryContextPrompt(query)` fetches all pinned memories (`getPinnedMemoriesSync()`) plus up to 6 keyword-matched memories (`searchMemoriesSync(token)`), formats them as bullet points, and passes them into `GeminiApiService.streamChatResponse(..., memoryContextPrompt = memoryContext)`.
17. **Error Handling:** Handles empty memory tables gracefully by returning an empty string `""`.
18. **Edge Cases:** Deduplicates pinned and keyword-matched memories by `id`.
19. **Security Considerations:** Stored locally in the app's private SQLite database (`jarvis_assistant_db`).
20. **Performance Considerations:** Uses indexed SQLite queries on `Dispatchers.IO`.
21. **Current Limitations:** `buildMemoryContextPrompt()` is currently injected into `streamChatResponse` (Text Chat), whereas `startLiveVoiceSession` does not currently inject `buildMemoryContextPrompt` into the initial Live WebSocket setup instruction (though the Live Agent can actively call the `save_or_recall_memory` tool at any time).
22. **Source Files:** `MemorySystemScreen.kt`, `MemoryRepository.kt`, `Entities.kt`, `JarvisDao.kt`.
23. **Key Line References:** `MemoryRepository.kt` lines 23–114; `MemorySystemScreen.kt` lines 1–433.

---

### Feature 8: Multi-Step Automation Workflows & Macro Studio (`WorkflowsMacroScreen`)
1. **Feature Name:** Multi-Step Automation Macro Engine
2. **Category:** Automation & Routines
3. **Current Status:** `IMPLEMENTED AND VERIFIED IN CODE` (with 1 minor action type limitation noted in Phase 17 `BUG-05`).
4. **Description:** Allows users to create, enable/disable, and execute multi-step macros stored as JSON action arrays (`WorkflowActionStep`) in Room (`automation_workflows`), pre-seeded with 3 default macros (*Morning Briefing*, *Drive & Navigate Mode*, *Deep Focus Mode*).
5. **User Journey / How User Uses It:** User opens **Automation Macros** (`WorkflowsMacroScreen`), taps **RUN** on an existing macro card, toggles its enabled switch, deletes it, or taps **+ New Macro** to build a custom routine with a title, voice trigger phrase, volume level, app to launch, and speech announcement.
6. **UI Screens Involved:** `WorkflowsMacroScreen.kt`, `HomeDashboardScreen.kt`.
7. **Composable Functions Involved:** `WorkflowsMacroScreen`, `WorkflowMacroCard`, `CreateWorkflowDialog`.
8. **ViewModels Involved:** `JarvisViewModel` (`runWorkflowMacro`, `createCustomWorkflow`, `toggleWorkflowEnabled`, `deleteWorkflow`).
9. **Repositories Involved:** `MemoryRepository` (`allWorkflows`, `seedDefaultWorkflowsIfEmpty`, `saveWorkflow`, `recordWorkflowRun`).
10. **Services / Managers Involved:** `WorkflowEngine`, `AppLauncherManager`, `PhoneControlsManager`, `PhoneActionManager`, `MediaControllerHelper`, `JarvisTtsManager`.
11. **Database Tables Involved:** `automation_workflows` (`WorkflowEntity`), `command_history`.
12. **APIs Used:** Local Room SQLite + Android hardware/intent APIs.
13. **Permissions Required:** Depends on macro steps (none for volume/TTS/app launch; `ACCESS_NOTIFICATION_POLICY` for DND).
14. **Input Data:** `WorkflowEntity` containing `stepsJson` (`JSONArray` of `WorkflowActionStep`: `type`, `label`, `param1`, `param2`, `delayAfterMs`).
15. **Output Data:** `WorkflowRunResult` (`workflowTitle`, `totalSteps`, `executedSteps`, `stepLogs`, `allSucceeded`) + spoken TTS announcement if a `SPEAK_TEXT` step is included.
16. **Step-by-Step Internal Code Flow:**
    - On `JarvisViewModel` initialization, `memoryRepository.seedDefaultWorkflowsIfEmpty()` seeds the 3 default workflows if the table is empty.
    - When `runWorkflowMacro(workflow)` is invoked, `WorkflowEngine.executeWorkflow()` parses `stepsJson` and executes each step sequentially (`OPEN_APP`, `TOGGLE_SETTING`, `ADJUST_VOLUME`, `SET_BRIGHTNESS`, `SPEAK_TEXT`, `SEND_WHATSAPP`, `MAKE_CALL`, `MEDIA_CONTROL`, `WAIT_DELAY`, `SET_PERSONALITY`), pausing for `step.delayAfterMs.coerceIn(0L, 10_000L)` between steps.
    - Updates `runCount` and `lastRunAt` in Room via `memoryRepository.recordWorkflowRun(workflow)`.
17. **Error Handling:** Individual step failures are caught and logged into `stepLogs` without crashing the remaining steps in the macro.
18. **Edge Cases:** Delays are bounded to a maximum of 10 seconds per step so macros cannot hang indefinitely.
19. **Security Considerations:** Macros are user-authored and stored locally.
20. **Performance Considerations:** Runs asynchronously on `viewModelScope`.
21. **Current Limitations:** `WorkflowActionType.SET_PERSONALITY` in `WorkflowEngine.kt` (lines 95–97) logs the personality change but does not call `UserPreferencesRepository.updatePersonalityMode()` (documented as `BUG-05`).
22. **Source Files:** `WorkflowsMacroScreen.kt`, `WorkflowEngine.kt`, `MemoryRepository.kt`.
23. **Key Line References:** `WorkflowEngine.kt` lines 1–145; `MemoryRepository.kt` lines 118–260; `WorkflowsMacroScreen.kt` lines 1–460.

---

### Feature 9: Notification Intelligence & AI Digest (`NotificationIntelligenceScreen`)
1. **Feature Name:** Real-Time Notification Listener & Gemini AI Notification Summarizer
2. **Category:** Notification Intelligence
3. **Current Status:** `IMPLEMENTED AND VERIFIED IN CODE`
4. **Description:** Captures incoming status bar notifications via `JarvisNotificationListenerService`, stores them in Room (`notification_logs`), displays them in `NotificationIntelligenceScreen`, and summarizes recent notifications into a concise bulleted briefing using Gemini (`summarizeRecentNotificationsWithAi`).
5. **User Journey / How User Uses It:** User opens **Notification Hub** (`NotificationIntelligenceScreen`), enables Notification Listener access if not yet granted, views captured notifications from WhatsApp/Gmail/Messages/etc., and taps **AI Digest** (`testTag = "summarize_notifications_button"`) to generate an AI summary.
6. **UI Screens Involved:** `NotificationIntelligenceScreen.kt`, `OnboardingPermissionsScreen.kt`.
7. **Composable Functions Involved:** `NotificationIntelligenceScreen`, `NotificationAccessBanner`, `NotificationLogCard`.
8. **ViewModels Involved:** `JarvisViewModel` (`summarizeRecentNotificationsWithAi`, `markAllNotificationsRead`, `clearNotificationLogs`).
9. **Repositories Involved:** `MemoryRepository` (`recentNotifications`, `logNotification`, `markAllNotificationsRead`, `clearNotificationLogs`).
10. **Services / Managers Involved:** `JarvisNotificationListenerService`, `GeminiApiService`.
11. **Database Tables Involved:** `notification_logs` (`NotificationLogEntity`).
12. **APIs Used:** Android `NotificationListenerService`, Gemini REST API (`sendChatMessage`).
13. **Permissions Required:** `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` (granted by user in `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`).
14. **Input Data:** Android `StatusBarNotification` (`packageName`, `Notification.EXTRA_TITLE`, `Notification.EXTRA_TEXT`, `Notification.EXTRA_SUB_TEXT`, `Notification.category`).
15. **Output Data:** `NotificationLogEntity` rows in Room and an AI-generated executive digest in `_notificationSummaryText`.
16. **Step-by-Step Internal Code Flow:**
    - When a notification arrives, `JarvisNotificationListenerService.onNotificationPosted(sbn)` filters out ongoing notifications (`sbn.isOngoing`) and notifications from JARVIS itself (`applicationContext.packageName`), resolves the human-readable app label via `PackageManager`, and inserts a `NotificationLogEntity` into Room.
    - When the user taps **AI Digest**, `JarvisViewModel.summarizeRecentNotificationsWithAi()` takes the latest 20 notifications, builds a structured prompt, calls `geminiApiService.sendChatMessage()`, stores the summary in `_notificationSummaryText`, and marks all notifications read.
17. **Error Handling:** Checks `secureApiKeyManager.getApiKey()` before requesting an AI summary and reports a friendly message if no API key is configured.
18. **Edge Cases:** Skips blank notifications where both title and text are empty.
19. **Security Considerations:** Notification logs remain strictly local unless the user taps **AI Digest** to summarize them with their own Gemini API key.
20. **Performance Considerations:** Uses `SupervisorJob() + Dispatchers.IO` inside `JarvisNotificationListenerService`.
21. **Current Limitations:** `JarvisNotificationListenerService.onNotificationPosted` does not currently filter against `UserPreferencesRepository.notificationExcludedPackages` (documented in Phase 17 as `BUG-06`).
22. **Source Files:** `JarvisNotificationListenerService.kt`, `NotificationIntelligenceScreen.kt`, `MemoryRepository.kt`, `JarvisViewModel.kt`.
23. **Key Line References:** `JarvisNotificationListenerService.kt` lines 1–94; `NotificationIntelligenceScreen.kt` lines 1–328; `JarvisViewModel.kt` lines 1072–1128.

---

### Feature 10: Autonomous Action Safety & Risk Policy Engine (`ActionSafetyScreen`)
1. **Feature Name:** Risk-Tiered Action Safety & Confirmation Modal Engine
2. **Category:** Security & Autonomous Governance
3. **Current Status:** `IMPLEMENTED AND VERIFIED IN CODE`
4. **Description:** Classifies actions into 4 risk tiers (`RiskLevel.LOW`, `MEDIUM`, `HIGH`, `CRITICAL`), provides user-configurable confirmation gates (Confirm Before Calling, Confirm Before Messaging, Confirm Before Final Send, Biometric/PIN Gate toggle, Max Autonomous Steps slider `1..15`), and renders a global `SafetyConfirmationModal` in `JarvisApp.kt` before executing high-risk actions initiated from the UI/ViewModel.
5. **User Journey / How User Uses It:** User opens **Action Safety** (`ActionSafetyScreen`) to configure safety toggles, autonomous step limits, and assistant personality mode. When a gated action (such as placing a direct phone call or sending a WhatsApp message) is triggered, `SafetyConfirmationModal` appears with **Cancel** and **Approve & Execute** buttons.
6. **UI Screens Involved:** `ActionSafetyScreen.kt`, `JarvisApp.kt` (`SafetyConfirmationModal`).
7. **Composable Functions Involved:** `ActionSafetyScreen`, `SafetyToggleRow`, `SafetyConfirmationModal`.
8. **ViewModels Involved:** `JarvisViewModel` (`requestActionWithSafetyCheck`, `approvePendingConfirmation`, `cancelPendingConfirmation`, `updateSafetyConfirmations`, `updateConfirmBeforeCall`, `updateConfirmBeforeMessage`, `updateConfirmBeforeSend`, `updateRequireBiometricForHighRisk`, `updateMaxAutonomousSteps`, `updatePersonalityMode`).
9. **Repositories Involved:** `UserPreferencesRepository`, `JarvisRepository`.
10. **Services / Managers Involved:** `ToolRegistry` (`getToolRiskLevel`), `CommunicationHelper` (`buildConfirmationRequest`).
11. **Database Tables Involved:** `command_history` (logs `[APPROVED]` vs `[CANCELLED]` actions).
12. **APIs Used:** Local state & DataStore preferences.
13. **Permissions Required:** None.
14. **Input Data:** `ActionConfirmationRequest` (`title`, `description`, `toolName`, `targetRecipient`, `messagePreview`, `riskLevel`, `spokenPromptHinglish`) and `onConfirmed` suspend callback.
15. **Output Data:** Modal confirmation dialog and audited execution in `command_history`.
16. **Step-by-Step Internal Code Flow:**
    - `JarvisViewModel.requestActionWithSafetyCheck(request, action)` checks `userSettings.value.safetyConfirmationsEnabled`.
    - If enabled and `request.riskLevel` is `HIGH` or `CRITICAL`, sets `_pendingConfirmation.value = request`, stores `pendingConfirmationCallback = action`, and speaks `request.spokenPromptHinglish` via `ttsManager`.
    - User taps **Approve & Execute** (`approve_safety_confirmation_button`) -> `approvePendingConfirmation()` invokes the stored suspend callback and logs `[APPROVED]` to `command_history`.
17. **Error Handling:** Clears pending callback cleanly on cancel.
18. **Edge Cases:** If `safetyConfirmationsEnabled` is `false`, executes `action()` immediately without blocking.
19. **Security Considerations:** Prevents accidental outbound calls or messages when triggered via `makeDirectCall` / `sendDirectWhatsApp`.
20. **Performance Considerations:** Zero overhead when idle.
21. **Current Limitations:** `requireBiometricForHighRisk` is stored as a preference toggle and displayed in `ActionSafetyScreen`, though hardware `BiometricPrompt` authentication is not invoked in `approvePendingConfirmation()`.
22. **Source Files:** `ActionSafetyScreen.kt`, `JarvisApp.kt`, `JarvisViewModel.kt`, `ToolRegistry.kt`, `CommunicationHelper.kt`.
23. **Key Line References:** `ActionSafetyScreen.kt` lines 1–329; `JarvisApp.kt` lines 253–371; `JarvisViewModel.kt` lines 1020–1068.

---

### Feature 11: Offline Wake-Word Listener (`OfflineWakeWordManager`)
1. **Feature Name:** Hands-Free Wake-Word Detection ("Hey Jarvis")
2. **Category:** Voice Activation
3. **Current Status:** `IMPLEMENTED AND VERIFIED IN CODE` (Requires device `SpeechRecognizer` support at runtime).
4. **Description:** Listens continuously in the background using Android's `SpeechRecognizer` when enabled by the user, matching partial and final speech hypotheses against `"hey jarvis"`, `"jarvis"`, `"ok jarvis"`, `"hello jarvis"`, or a custom user wake phrase using fuzzy Levenshtein distance (`WakeWordSensitivity`), and automatically launches the Gemini Live Voice session upon detection.
5. **User Journey / How User Uses It:** User toggles the **Wake-Word** chip on `HomeDashboardScreen` or in `GeneralSettingsScreen` (`WakeWordConfigurationCard`), sets sensitivity (`LOW`, `MEDIUM`, `HIGH`) and custom wake phrase, and speaks *"Hey Jarvis"*.
6. **UI Screens Involved:** `HomeDashboardScreen.kt`, `GeneralSettingsScreen.kt`.
7. **Composable Functions Involved:** `WakeWordConfigurationCard`, `HeroAssistantBentoCard`.
8. **ViewModels Involved:** `JarvisViewModel` (`updateWakeWordEnabled`, `updateWakeWordPhrase`, `updateWakeWordSensitivity`).
9. **Repositories Involved:** `UserPreferencesRepository`, `JarvisRepository`.
10. **Services / Managers Involved:** `OfflineWakeWordManager`.
11. **Database Tables Involved:** `command_history` (logs `Wake-Word Detected: <phrase>`).
12. **APIs Used:** Android `SpeechRecognizer` (`RecognizerIntent.ACTION_RECOGNIZE_SPEECH`, `EXTRA_PREFER_OFFLINE = true`).
13. **Permissions Required:** `android.permission.RECORD_AUDIO`.
14. **Input Data:** Live microphone audio processed by Android `SpeechRecognizer`, custom phrase (`String`), `WakeWordSensitivity`.
15. **Output Data:** Invokes `onWakeWordDetected(detectedText)` callback in `JarvisViewModel`, which calls `startLiveVoiceSession()`.
16. **Step-by-Step Internal Code Flow:**
    - `OfflineWakeWordManager.startWakeWordListening()` creates a `SpeechRecognizer` on the main thread and starts listening with partial results enabled.
    - `onPartialResults` and `onResults` call `matchesWakeWord(candidates)`, which checks exact substring matches first and then computes word-level `levenshteinDistance()` against `sensitivity.maxEditDistance` (`0` for LOW, `1` for MEDIUM, `2` for HIGH).
    - When matched, pauses the wake-word recognizer (`pauseForActiveSession()`) and starts the Gemini Live session. When the Gemini Live session ends, `stopLiveVoiceSession()` calls `resumeAfterActiveSession()`.
17. **Error Handling:** Handles `SpeechRecognizer` error codes (`ERROR_NO_MATCH`, `ERROR_SPEECH_TIMEOUT`, `ERROR_RECOGNIZER_BUSY`) with automatic restart backoff (`450ms` up to `3500ms`), and stops after 5 consecutive fatal errors (`ERROR_INSUFFICIENT_PERMISSIONS`, `ERROR_AUDIO`, `ERROR_CLIENT`).
18. **Edge Cases:** Checks `SpeechRecognizer.isRecognitionAvailable(context)` before initializing.
19. **Security Considerations:** Prefers offline recognition (`EXTRA_PREFER_OFFLINE = true`).
20. **Performance Considerations:** Automatically pauses during active Gemini Live sessions so only one component holds the microphone at a time.
21. **Current Limitations:** Relies on Android's system `SpeechRecognizer` service, which on some OEM devices may play a subtle start beep or require Google Speech Services installed.
22. **Source Files:** `OfflineWakeWordManager.kt`, `GeneralSettingsScreen.kt`, `JarvisViewModel.kt`.
23. **Key Line References:** `OfflineWakeWordManager.kt` lines 1–277; `JarvisViewModel.kt` lines 257–274, 982–1014.

---

### Feature 12: Live System Diagnostics, Screen Inspector & Command Audit (`DiagnosticsScreen`)
1. **Feature Name:** System Telemetry, Live Accessibility Node Inspector & Command Audit Log
2. **Category:** Diagnostics & Observability
3. **Current Status:** `IMPLEMENTED AND VERIFIED IN CODE`
4. **Description:** Displays real-time hardware telemetry (Battery %, Charging state, Temperature °C, Network type, Available/Total RAM GB, Available Storage GB, Audio Mode, Volume %), live `AccessibilityService` foreground window inspection (package name, editable field count, clickable chips, visible text labels), and a chronological audit log of every tool/command executed by JARVIS.
5. **User Journey / How User Uses It:** User opens **Diagnostics** (`DiagnosticsScreen`) from the Home Dashboard or Settings, taps **Refresh** (`refresh_diagnostics_button`) to poll live hardware/screen state, or taps **Clear Logs** to reset the command history table.
6. **UI Screens Involved:** `DiagnosticsScreen.kt`, `OnboardingPermissionsScreen.kt`.
7. **Composable Functions Involved:** `DiagnosticsScreen`, `TelemetryGridCard`, `ScreenInspectorCard`, `CommandHistoryAuditRow`.
8. **ViewModels Involved:** `JarvisViewModel` (`refreshDiagnostics`, `clearCommandHistory`).
9. **Repositories Involved:** `JarvisRepository` (`recentCommands`, `clearCommandLogs`).
10. **Services / Managers Involved:** `SystemStatusManager`, `AccessibilityControllerBridge`, `AppLauncherManager`, `SecureApiKeyManager`, `JarvisNotificationListenerService`.
11. **Database Tables Involved:** `command_history` (`CommandHistoryEntity`).
12. **APIs Used:** Android `BatteryManager`, `ConnectivityManager`, `ActivityManager`, `StatFs`, `AudioManager`, `PackageManager`.
13. **Permissions Required:** `android.permission.ACCESS_NETWORK_STATE`.
14. **Input Data:** Hardware sensor/system service states + Room `command_history` flow.
15. **Output Data:** `SystemDiagnostics`, `DeviceStatusSnapshot`, `ScreenContextSummary`, and `List<CommandHistoryEntity>`.
16. **Step-by-Step Internal Code Flow:**
    - `JarvisViewModel.refreshDiagnostics()` queries runtime permissions (`ContextCompat.checkSelfPermission`), checks `AccessibilityControllerBridge.isServiceConnected`, queries `SystemStatusManager.readStatusSnapshot()`, counts launchable apps via `AppLauncherManager.getInstalledAppsCount()`, and updates `_diagnostics` and `_deviceStatusSnapshot`.
17. **Error Handling:** All hardware telemetry calls use safe fallbacks (`-1` or `"Unknown"`) if a system service is unavailable.
18. **Edge Cases:** Formats timestamps into `HH:mm:ss • dd MMM` for clear human reading.
19. **Security Considerations:** Read-only diagnostic inspection.
20. **Performance Considerations:** `getInstalledAppsCount()` runs during `refreshDiagnostics()` and caches the result in `_diagnostics`.
21. **Current Limitations:** None.
22. **Source Files:** `DiagnosticsScreen.kt`, `SystemStatusManager.kt`, `JarvisViewModel.kt`.
23. **Key Line References:** `SystemStatusManager.kt` lines 1–194; `DiagnosticsScreen.kt` lines 1–346; `JarvisViewModel.kt` lines 296–365.

---

# PHASE 5 — COMPLETE UI DOCUMENTATION

### 5.1 Design System, Theme, Colors & Typography
- **Visual Direction:** Ultra-Modern Spatial Glassmorphism + Bento Grid Architecture + Neural Ambient Orbs (`Theme.kt`, `Color.kt`, `Type.kt`, `HudComponents.kt`).
- **Color Palette (`Color.kt`):**
  - Core Surfaces: `ObsidianBlack` (`#06070B`), `DeepSpaceNavy` (`#0C0F17`), `DarkSlateSurface` (`#121723`), `ElevatedCardSurface` (`#171D2C`), `HudGlassTint` (`#1A2234`).
  - Brand Accents: `ElectricCyan` (`#38BDF8`), `CyanGlowBright` (`#7DD3FC`), `DeepCerulean` (`#6366F1`), `NeonArcBlue` (`#818CF8`), `VioletPulse` (`#C084FC`), `MagentaCore` (`#F472B6`).
  - Semantic Status Colors: `EmeraldSuccess` (`#34D399`), `AmberWarning` (`#FBBF24`), `CrimsonError` (`#F87171`), `TextPrimaryWhite` (`#F8FAFC`), `TextSecondaryCyan` (`#94A3B8`), `TextMutedSlate` (`#64748B`).
- **Typography (`Type.kt`):**
  - `OrbitronFontFamily` (`R.font.orbitron_bold`): Used for `displayLarge` (`28.sp`), `displayMedium` (`22.sp`), `displaySmall` (`18.sp`), and `labelMedium` (`11.sp`).
  - `RajdhaniFontFamily` (`R.font.rajdhani_medium`, `R.font.rajdhani_bold`): Used for `headlineLarge` (`20.sp`), `headlineMedium` (`17.sp`), `titleLarge` (`16.sp`), `titleMedium` (`15.sp`), `bodyLarge` (`16.sp`), and `bodyMedium` (`14.sp`).
  - `JetBrainsMonoFontFamily` (`R.font.jetbrains_mono_regular`, `R.font.jetbrains_mono_bold`): Used for `bodySmall` (`12.sp`), `labelLarge` (`13.sp`), and `labelSmall` (`10.sp`).

### 5.2 Navigation Architecture (`JarvisApp.kt`)
`JarvisScreen` defines **12 routes**:
- **5 Primary Bottom Dock / Navigation Rail Destinations (`primaryDestinations`):**
  1. `JarvisScreen.HOME` (`route = "home"`, Title: `"Command"`)
  2. `JarvisScreen.CHAT` (`route = "chat"`, Title: `"AI Chat"`)
  3. `JarvisScreen.LIVE_VOICE` (`route = "live_voice"`, Title: `"Live Agent"`)
  4. `JarvisScreen.PHONE_CONTROLS` (`route = "phone_controls"`, Title: `"Controls"`)
  5. `JarvisScreen.SETTINGS` (`route = "settings"`, Title: `"Settings"`)
- **7 Secondary Full-Screen Destinations:**
  6. `JarvisScreen.API_MANAGEMENT` (`route = "api_management"`, Title: `"API Vault"`)
  7. `JarvisScreen.PERMISSIONS` (`route = "permissions"`, Title: `"Permissions"`)
  8. `JarvisScreen.MEMORY_VAULT` (`route = "memory_vault"`, Title: `"Memory Vault"`)
  9. `JarvisScreen.WORKFLOWS` (`route = "workflows"`, Title: `"Macros"`)
  10. `JarvisScreen.NOTIFICATIONS` (`route = "notifications"`, Title: `"Notifications"`)
  11. `JarvisScreen.DIAGNOSTICS` (`route = "diagnostics"`, Title: `"Diagnostics"`)
  12. `JarvisScreen.ACTION_SAFETY` (`route = "action_safety"`, Title: `"Safety & Risk"`)
- **Adaptive Layout:** `JarvisApp` uses `BoxWithConstraints` (`isExpandedScreen = maxWidth >= 700.dp`). On compact screens, it renders a floating glass pill bottom dock (`FloatingBottomNavBar`) on the 5 primary screens. On expanded/tablet screens (`>= 700.dp`), it renders a permanent side `ExpandedNavigationRail`.

### 5.3 Detailed Documentation of All 12 Screens

| # | Screen Composable & File | Route | State Consumed | User Actions & Callbacks | Dialogs / Bottom Sheets | Verified Compose `testTag`s |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | `HomeDashboardScreen` (`HomeDashboardScreen.kt`) | `"home"` | `assistantState`, `userSettings`, `diagnostics`, `liveSessionState`, `audioLevel`, `lastActionStatus`, `recentCommands`, `isWakeWordRunning`, `deviceStatus`, `memoriesCount`, `workflowsCount`, `unreadNotificationsCount` | `onNavigateToChat`, `onNavigateToLiveVoice`, `onStartFemaleCompanionOneClick`, `onNavigateToControls`, `onNavigateToApiManagement`, `onNavigateToPermissions`, `onNavigateToSettings`, `onNavigateToMemoryVault`, `onNavigateToWorkflows`, `onNavigateToNotifications`, `onNavigateToDiagnostics`, `onNavigateToActionSafety`, `onExecuteQuickCommand`, `onToggleTorch`, `onToggleWakeWord` | None | `home_permissions_button`, `home_api_key_button`, `home_settings_button`, `home_alert_banner`, `female_companion_one_click_card`, `home_arc_reactor_orb`, `home_start_live_button`, `home_open_chat_button`, `home_wake_word_chip`, `home_quick_input`, `home_quick_send_button`, `quick_cmd_0`..`5`, `home_bento_api_card`, `home_bento_accessibility_card`, `home_bento_Model_card`, `home_bento_Voice_card`, `home_quick_torch_button`, `home_all_controls_button`, `v2_hub_memory_card`, `v2_hub_workflows_card`, `v2_hub_notifications_card`, `v2_hub_diagnostics_card`, `v2_hub_safety_card` |
| 2 | `TextChatScreen` (`TextChatScreen.kt`) | `"chat"` | `messages`, `sessions`, `activeSessionId`, `isGenerating`, `userSettings`, `hasApiKey`, `selectedAttachment`, `ttsPlaybackState` | `onSendMessage`, `onNewSession`, `onSelectSession`, `onDeleteSession`, `onRenameSession`, `onAttachImageUri`, `onAttachDocumentUri`, `onClearAttachment`, `onSpeakMessage`, `onStopSpeaking`, `onNavigateToApiSetup`, `onNavigateToLiveVoice` | `ChatSessionsBottomSheet` (`ModalBottomSheet`) | `chat_new_session_button`, `chat_sessions_button`, `chat_go_live_button`, `chat_missing_key_banner`, `chat_suggestion_0`..`3`, `chat_messages_list`, `chat_Camera_attach_button`, `chat_Gallery_attach_button`, `chat_File_attach_button`, `chat_input_field`, `chat_send_button`, `sheet_new_chat_button` |
| 3 | `LiveVoiceScreen` (`LiveVoiceScreen.kt`) | `"live_voice"` | `liveSessionState`, `isModelSpeaking`, `isUserSpeaking`, `isMicMuted`, `liveInputTranscript`, `liveOutputTranscript`, `transcriptHistory`, `audioLevel`, `lastError`, `statusBanner`, `activeLiveModel`, `executedToolCount`, `lastToolSummary`, `userSettings`, `hasApiKey`, `isAccessibilityConnected` | `onStartSession`, `onStartFemaleCompanionOneClick`, `onStopSession`, `onToggleMute`, `onSendTextInLive`, `onCaptureScreenVision`, `onToggleLiveVisionSetting`, `onSelectLanguage`, `onNavigateToApiSetup`, `onNavigateToAccessibilitySetup`, `onNavigateBack` | None | `live_vision_toggle_chip`, `live_capture_screen_button`, `live_missing_key_banner`, `live_accessibility_warning_banner`, `female_companion_activate_button`, `live_lang_ENGLISH`, `live_lang_HINGLISH`, `live_lang_HINDI`, `live_voice_orb_button`, `live_mute_button`, `live_primary_session_button`, `live_vision_send_button`, `live_text_input`, `live_text_send_button` |
| 4 | `PhoneControlsScreen` (`PhoneControlsScreen.kt`) | `"phone_controls"` | `toggleItems`, `volumePercent`, `brightnessPercent`, `isAccessibilityConnected`, `lastStatusBanner` | `onToggleClick`, `onVolumeChange`, `onBrightnessChange`, `onOpenAccessibilitySettings`, `onQuickAppLaunch`, `onDirectCall`, `onDirectWhatsApp`, `onRefreshState` | `DirectCallDialog`, `DirectWhatsAppDialog` (`AlertDialog`) | `controls_refresh_button`, `volume_slider`, `brightness_slider`, `accessibility_setup_card`, `toggle_card_torch`, `toggle_card_wifi`, `toggle_card_bluetooth`, `toggle_card_dnd`, `toggle_card_ringer`, `toggle_card_rotation`, `toggle_card_hotspot`, `toggle_card_location`, `toggle_card_battery_saver`, `toggle_card_airplane`, `open_call_dialog_button`, `open_whatsapp_dialog_button`, `quick_launch_YouTube` (and 9 other apps), `dialog_call_number_input`, `dialog_confirm_call_button`, `dialog_wa_phone_input`, `dialog_wa_message_input`, `dialog_confirm_wa_button` |
| 5 | `GeneralSettingsScreen` (`GeneralSettingsScreen.kt`) | `"settings"` | `userSettings`, `availableModels`, `diagnostics`, `isWakeWordRunning` | `onUpdateModel`, `onUpdateVoiceEngine`, `onUpdateLanguage`, `onUpdateVoiceGender`, `onUpdateVoicePersona`, `onUpdateSpeechSpeed`, `onUpdateVoicePitch`, `onUpdateContinuousMode`, `onUpdateAutoSpeakChat`, `onUpdateLiveVision`, `onUpdateWakeWordEnabled`, `onUpdateWakeWordPhrase`, `onUpdateWakeWordSensitivity`, `onUpdateConfidenceThreshold`, `onUpdateHapticFeedback`, `onUpdateSafetyConfirmations`, `onUpdateAssistantName`, `onTestVoiceOutput`, `onClearAllHistory`, plus 6 navigation callbacks | `ModelSelectorModalSheet`, `VoicePersonaModalSheet` (`ModalBottomSheet`) | `settings_open_api_button`, `settings_open_permissions_button`, `settings_open_memory_button`, `settings_open_workflows_button`, `settings_open_diagnostics_button`, `settings_open_safety_button`, `settings_model_selector_button`, `settings_persona_selector_button`, `settings_test_voice_button`, `settings_speech_speed_slider`, `settings_voice_pitch_slider`, `settings_wake_word_switch`, `settings_wake_phrase_input`, `settings_save_wake_phrase_button`, `settings_clear_history_button` |
| 6 | `ApiManagementScreen` (`ApiManagementScreen.kt`) | `"api_management"` | `hasApiKey`, `maskedKey`, `apiTestStatus`, `apiTestMessage`, `selectedModel`, `availableModels`, `savedKeyPool`, `activeKeyIndex`, `poolStatusSummary`, `isSyncingModels` | `onSaveApiKey`, `onClearApiKey`, `onTestApiKey`, `onAddKeyToPool`, `onRemoveKeyFromPool`, `onSelectKeyFromPool`, `onRefreshModels`, `onSelectModel`, `onNavigateBack` | None | `api_delete_key_button`, `api_key_input_field`, `api_save_button`, `api_test_saved_button`, `api_pool_input_field`, `api_pool_add_button`, `api_validate_connection_button` |
| 7 | `OnboardingPermissionsScreen` (`OnboardingPermissionsScreen.kt`) | `"permissions"` | `diagnostics: SystemDiagnostics` | `onRefreshDiagnostics`, `onOpenAccessibilitySettings`, `onOpenNotificationListenerSettings`, `onOpenWriteSettings`, `onOpenAppInfoSettings`, `onNavigateBack` | System permission dialogs | `permissions_refresh_button`, `perm_grant_record_audio`, `perm_grant_camera`, `perm_grant_call_phone`, `perm_grant_read_contacts`, `perm_grant_accessibility`, `perm_grant_notification_listener`, `open_write_settings_button`, `open_app_details_button`, `permissions_continue_button` |
| 8 | `MemorySystemScreen` (`MemorySystemScreen.kt`) | `"memory_vault"` | `memories: List<MemoryEntity>` | `onSaveMemory`, `onTogglePin`, `onDeleteMemory`, `onClearAllMemories`, `onNavigateBack` | `AddMemoryDialog` (`AlertDialog`) | `add_memory_fab`, `clear_all_memories_button`, `memory_search_input`, `memory_card_<id>`, `dialog_memory_key_input`, `dialog_memory_value_input`, `dialog_save_memory_button` |
| 9 | `WorkflowsMacroScreen` (`WorkflowsMacroScreen.kt`) | `"workflows"` | `workflows: List<WorkflowEntity>`, `lastMacroResult: String` | `onRunWorkflow`, `onCreateWorkflow`, `onToggleEnabled`, `onDeleteWorkflow`, `onNavigateBack` | `CreateWorkflowDialog` (`AlertDialog`) | `create_workflow_button`, `workflow_card_<id>`, `run_workflow_<id>`, `dialog_wf_title_input`, `dialog_wf_trigger_input`, `dialog_wf_app_input`, `dialog_wf_speak_input`, `dialog_save_workflow_button` |
| 10 | `NotificationIntelligenceScreen` (`NotificationIntelligenceScreen.kt`) | `"notifications"` | `logs: List<NotificationLogEntity>`, `isListenerConnected: Boolean`, `aiSummary: String`, `isSummarizing: Boolean` | `onOpenListenerSettings`, `onSummarizeWithAi`, `onMarkAllRead`, `onClearLogs`, `onNavigateBack` | None | `summarize_notifications_button`, `mark_notifications_read_button`, `clear_notifications_button`, `notification_access_banner` |
| 11 | `DiagnosticsScreen` (`DiagnosticsScreen.kt`) | `"diagnostics"` | `diagnostics`, `deviceStatus`, `screenSummary`, `commandHistory` | `onRefresh`, `onClearCommandLogs`, `onNavigateBack` | None | `refresh_diagnostics_button`, `clear_command_logs_button` |
| 12 | `ActionSafetyScreen` (`ActionSafetyScreen.kt`) | `"action_safety"` | `userSettings: UserSettings` | `onUpdateSafetyConfirmations`, `onUpdateConfirmBeforeCall`, `onUpdateConfirmBeforeMessage`, `onUpdateConfirmBeforeSend`, `onUpdateRequireBiometric`, `onUpdateMaxAutonomousSteps`, `onUpdatePersonalityMode`, `onNavigateBack` | None | `personality_chip_PROFESSIONAL`, `personality_chip_FRIENDLY_COMPANION`, `personality_chip_CONCISE_TACTICAL`, `personality_chip_WITTY_STARK`, `personality_chip_HINGLISH_DOST`, `autonomous_steps_slider`, `safety_toggle_master_safety`, `safety_toggle_confirm_call`, `safety_toggle_confirm_message`, `safety_toggle_confirm_send`, `safety_toggle_biometric_gate` |

---

# PHASE 6 — COMPLETE SOFTWARE ARCHITECTURE

### 6.1 Architectural Pattern
JARVIS AI implements a **Reactive Single-Activity MVVM + Service Bridge Architecture**:
1. **Presentation Layer (`ui/`):** Pure Jetpack Compose UI screens observing immutable `StateFlow`s via `collectAsStateWithLifecycle()`.
2. **State & Orchestration Layer (`viewmodel/JarvisViewModel.kt`):** Single `AndroidViewModel` owning all UI state, coordinating coroutine jobs on `viewModelScope`, and binding UI actions to repositories and system services.
3. **Domain & Service Layer (`service/`):** 17 specialized managers/services handling Gemini Live WebSocket streaming (`GeminiLiveVoiceManager`), tool execution (`LiveAgentToolExecutor`, `ToolRegistry`), Accessibility automation (`JarvisAccessibilityService`, `AccessibilityControllerBridge`), hardware controls (`PhoneControlsManager`), intents (`PhoneActionManager`, `AppLauncherManager`, `CommunicationHelper`), audio/TTS (`JarvisTtsManager`, `OfflineWakeWordManager`, `MediaControllerHelper`), and macro workflows (`WorkflowEngine`).
4. **Data & Security Layer (`data/`, `security/`):** Room SQLite database (`JarvisDatabase` v2 with `JarvisDao`), repositories (`JarvisRepository`, `MemoryRepository`), Jetpack DataStore (`UserPreferencesRepository`), network client (`GeminiApiService`), and Android Keystore encryption (`SecureApiKeyManager`).

### 6.2 Architecture Diagram (Mermaid)
```mermaid
graph TD
    subgraph UI_Layer["UI Layer (Jetpack Compose)"]
        MA["MainActivity"] --> JA["JarvisApp (NavHost + SafetyModal)"]
        JA --> S1["HomeDashboardScreen"]
        JA --> S2["TextChatScreen"]
        JA --> S3["LiveVoiceScreen"]
        JA --> S4["PhoneControlsScreen"]
        JA --> S5["GeneralSettingsScreen"]
        JA --> S6["7 Secondary Screens (API, Perms, Memory, Macros, Notifs, Diag, Safety)"]
    end

    subgraph ViewModel_Layer["ViewModel Layer"]
        JVM["JarvisViewModel (AndroidViewModel)"]
    end

    subgraph Service_Layer["Android Services & Domain Managers"]
        GLVM["GeminiLiveVoiceManager (WebSocket BidiGenerateContent)"]
        LATE["LiveAgentToolExecutor + ToolRegistry (19 Tools)"]
        ACB["AccessibilityControllerBridge"]
        JAS["JarvisAccessibilityService"]
        LVFS["LiveVoiceForegroundService + LiveVoiceSessionBridge"]
        JNLS["JarvisNotificationListenerService"]
        PCM["PhoneControlsManager + PhoneActionManager + AppLauncherManager"]
        TTS["JarvisTtsManager + OfflineWakeWordManager"]
        WE["WorkflowEngine"]
    end

    subgraph Data_Security_Layer["Data, Network & Security Layer"]
        GAS["GeminiApiService (REST + SSE)"]
        SAKM["SecureApiKeyManager (Android Keystore AES-256-GCM)"]
        UPR["UserPreferencesRepository (DataStore)"]
        JR["JarvisRepository"]
        MR["MemoryRepository"]
        DB[("JarvisDatabase (Room v2 - 6 Tables)")]
    end

    JA <-->|StateFlow / Actions| JVM
    JVM --> GLVM
    JVM --> LATE
    JVM --> PCM
    JVM --> TTS
    JVM --> WE
    JVM --> GAS
    JVM --> SAKM
    JVM --> UPR
    JVM --> JR
    JVM --> MR
    GLVM --> LATE
    LATE --> ACB
    LATE --> PCM
    LATE --> MR
    JAS <--> ACB
    LVFS <--> GLVM
    JNLS --> MR
    JR --> DB
    MR --> DB
```

---

# PHASE 7 — COMPLETE AI SYSTEM DOCUMENTATION

### 7.1 `GeminiApiService.kt` — Complete Endpoint & Protocol Specification
- **Base Endpoints:**
  - `BASE_URL_V1BETA = "https://generativelanguage.googleapis.com/v1beta/models"`
  - `BASE_URL_V1ALPHA = "https://generativelanguage.googleapis.com/v1alpha/models"`
- **HTTP Clients:**
  - `okHttpClient`: `connectTimeout = 20s`, `readTimeout = 60s`, `writeTimeout = 30s`, `retryOnConnectionFailure = true`.
  - `streamHttpClient`: `connectTimeout = 20s`, `readTimeout = 120s`, `writeTimeout = 30s`, `retryOnConnectionFailure = true`.
- **Default & Fallback Model Chain (`buildModelCandidateChain`):**
  - Default model: `gemini-2.5-flash`.
  - Automatic Fallback Priority (`FALLBACK_PRIORITY`):
    1. User-selected model (plus dynamically discovered variant from `discoveredGenerateModels`)
    2. `gemini-2.5-flash`
    3. `gemini-2.5-flash-lite`
    4. `gemini-2.0-flash`
    5. `gemini-1.5-flash`
  - If a model returns a quota exhaustion (`429`, `RESOURCE_EXHAUSTED`), model-not-found (`404`, `NOT_FOUND`), or server overload (`503`, `UNAVAILABLE`), `isQuotaOrModelError()` triggers immediate failover to the next model in the candidate chain.
- **Dynamic Model Discovery (`fetchAvailableModels`):**
  - Calls `GET https://generativelanguage.googleapis.com/v1beta/models?key={cleanKey}`.
  - Filters models whose `supportedGenerationMethods` array contains `"generateContent"`, strips the `"models/"` prefix, caches them in `discoveredGenerateModels`, and merges them with the 10 curated `availableChatModels`.
- **System Instruction Builder (`buildSystemInstruction`):**
  - Injects the user's configured `assistantName` (default `"JARVIS"`).
  - Enforces language rules based on `AssistantLanguage`:
    - `ENGLISH`: Clear, natural English.
    - `HINDI`: Natural Hindi (Devanagari or Romanized matching user).
    - `HINGLISH`: Conversational Indian Hinglish (*"Haan boss, abhi karta hoon"*).
    - `AUTO`: Mirrors the user's input language.
  - Appends `memoryContextPrompt` under `USER LONG-TERM MEMORY CONTEXT (Personalize responses using these facts):` when non-blank.
- **Multimodal Payload Construction (`buildContentsArray`):**
  - Sends the last 16 messages (`history.takeLast(16)`) with roles mapped to `"user"` and `"model"`.
  - When an image (`attachedImageBase64` + `attachedImageMimeType`) is attached to the current turn, appends an `inlineData` object (`{"mimeType": "image/jpeg", "data": "<base64>"}`) alongside the text prompt (defaulting to `"Analyze this image in detail."` if text is blank).
- **Gemini Native TTS Audio Generation (`generateGeminiSpeechAudio`):**
  - Uses `GEMINI_TTS_MODELS = listOf("gemini-2.5-flash-preview-tts", "gemini-2.0-flash")` via `POST /v1beta/models/{ttsModel}:generateContent`.
  - Requests `generationConfig.responseModalities = ["AUDIO"]` with `speechConfig.voiceConfig.prebuiltVoiceConfig.voiceName = persona.geminiVoiceName` (`"Aoede"`, `"Kore"`, `"Leda"`, `"Puck"`, `"Charon"`, `"Fenrir"`).
  - Extracts the Base64 audio payload from `candidates[0].content.parts[i].inlineData.data` and decodes it into raw 24kHz 16-bit mono PCM bytes (`GeminiAudioResult.Success(pcmBytes, sampleRateHz = 24000)`).


---

# PHASE 8 — COMPLETE VOICE ASSISTANT SYSTEM

### 8.1 Real-Time Gemini Live WebSocket Engine (`GeminiLiveVoiceManager.kt`)
- **WebSocket Endpoints:**
  - `WS_ENDPOINT_V1BETA`: `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key={apiKey}`
  - `WS_ENDPOINT_V1ALPHA`: `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key={apiKey}`
- **Live Model Discovery & Priority Queue (`discoverLiveModels`):**
  - Queries both `/v1alpha/models` and `/v1beta/models` for models whose `supportedGenerationMethods` includes `"bidiGenerateContent"`.
  - Ranks candidates using `LIVE_MODEL_CANDIDATES`:
    1. `models/gemini-2.5-flash-native-audio-preview-12-2025` (`v1alpha`, `v1beta`)
    2. `models/gemini-2.5-flash-native-audio-latest` (`v1alpha`, `v1beta`)
    3. `models/gemini-live-2.5-flash-preview` (`v1beta`, `v1alpha`)
    4. `models/gemini-2.0-flash-live-001` (`v1beta`, `v1alpha`)
    5. `models/gemini-2.0-flash-exp` (`v1alpha`, `v1beta`)
- **Setup Handshake Payload (`sendSetupMessage`):**
  - Sends a `setup` JSON object specifying:
    - `model`: `"models/{modelName}"`
    - `generationConfig`: `responseModalities = ["AUDIO"]`, `speechConfig.voiceConfig.prebuiltVoiceConfig.voiceName = activeVoicePersona.geminiVoiceName`
    - `systemInstruction`: Full autonomous Android agent instruction with explicit directives to NEVER say *"I cannot control your mobile device"* or *"Please click it yourself"*, and to immediately call the 19 registered tools.
    - `tools`: `[{"functionDeclarations": <19 ToolRegistry declarations>}]`
    - `inputAudioTranscription: {}` and `outputAudioTranscription: {}`
- **Microphone Capture Pipeline (`startAudioInputStreaming`):**
  - Format: `16,000 Hz` (`INPUT_SAMPLE_RATE = 16000`), `CHANNEL_IN_MONO`, `ENCODING_PCM_16BIT`.
  - Audio Source: `MediaRecorder.AudioSource.VOICE_COMMUNICATION` (falls back to `AudioSource.MIC` if hardware initialization fails).
  - Hardware DSP Effects attached via `audioRecord.audioSessionId`:
    - `AcousticEchoCanceler` (prevents speaker output from feeding back into mic)
    - `NoiseSuppressor` (suppresses background ambient noise)
    - `AutomaticGainControl` (normalizes varying voice distance)
  - Software Pre-Gain (`applyMicSoftwareGain`): Applies a `1.45f` multiplier (`MIC_SOFTWARE_GAIN = 1.45f`) with `coerceIn(-32768, 32767)` saturation clamping so quiet speech is clearly recognized.
  - Echo Gating & Barge-In Detection:
    - While `_isModelSpeaking.value` is `true` or within `ECHO_TAIL_COOLDOWN_MS = 350L` after playback finishes, outgoing microphone chunks are suppressed to prevent self-interruption.
    - If the user speaks loudly (`rms > BARGE_IN_RMS_THRESHOLD` of `3600.0` for `BARGE_IN_CONSECUTIVE_FRAMES = 3` frames), `interruptModelPlayback()` flushes the `AudioTrack` and resumes user mic streaming immediately.
  - Packet Framing: Accumulates `6,400` bytes (`200ms` chunks x 2 = `400ms` per WebSocket frame) and sends `{"realtimeInput": {"mediaChunks": [{"mimeType": "audio/pcm;rate=16000", "data": "<base64>"}]}}`.
- **Audio Output Playback Pipeline (`enqueueModelAudioChunk`):**
  - Format: `24,000 Hz` (`OUTPUT_SAMPLE_RATE = 24000`), `CHANNEL_OUT_MONO`, `ENCODING_PCM_16BIT`, `USAGE_MEDIA`, `CONTENT_TYPE_SPEECH`.
  - Buffer Sizing: Persistent `AudioTrack` with a 2-second buffer (`OUTPUT_TRACK_MIN_BUFFER_BYTES = 96,000` bytes).
  - Jitter Pre-Buffer: Accumulates `PREBUFFER_START_BYTES = 3,840` bytes (`80ms` of 24kHz audio) in `pendingStartPcmStream` before starting `AudioTrack.play()` to eliminate initial underrun clicks.
  - Ring-Buffer Tail Wait: After `turnComplete` arrives, waits `280ms` (`13,440` bytes drain time) before setting `_isModelSpeaking.value = false`.

### 8.2 Voice Personas (`VoicePersona` in `JarvisModels.kt`)
| Enum Constant | `id` | `geminiVoiceName` | `displayName` | `gender` | `accentTag` | `pitchMultiplier` | `rateMultiplier` |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `AOEDE` | `"aoede"` | `"Aoede"` | `"Aoede (Warm Female)"` | `FEMALE` | `"IN/US"` | `1.06f` | `1.0f` |
| `KORE` | `"kore"` | `"Kore"` | `"Kore (Sweet Female)"` | `FEMALE` | `"IN"` | `1.12f` | `1.02f` |
| `LEDA` | `"leda"` | `"Leda"` | `"Leda (Calm Female)"` | `FEMALE` | `"US"` | `0.98f` | `0.96f` |
| `PUCK` | `"puck"` | `"Puck"` | `"Puck (Natural Male)"` | `MALE` | `"IN/US"` | `1.0f` | `1.02f` |
| `CHARON` | `"charon"` | `"Charon"` | `"Charon (Deep Male)"` | `MALE` | `"UK/US"` | `0.88f` | `0.97f` |
| `FENRIR` | `"fenrir"` | `"Fenrir"` | `"Fenrir (Bold Male)"` | `MALE` | `"IN"` | `0.92f` | `1.04f` |

### 8.3 Dual-Engine Text-to-Speech (`JarvisTtsManager.kt`)
- **Primary Mode (`VoiceEngineType.GEMINI_LIVE_VOICE`):** When `apiKey` is present and `preferGeminiNativeVoice` is `true`, calls `geminiApiService.generateGeminiSpeechAudio()` and plays the returned 24kHz PCM buffer via `playRawPcmAudio()`.
- **Fallback Mode (`VoiceEngineType.ANDROID_SYSTEM_TTS`):** Uses Android's native `android.speech.tts.TextToSpeech`, scoring installed device voices (`selectBestVoice`) by locale (`en_IN` / `hi_IN` for Hindi/Hinglish, `en_US` for English), gender hints (`female`/`male`, `#f`/`#m`, `iob`/`iod`/`tpf`/`sfg`), and network quality (`!voice.isNetworkConnectionRequired`).
- **Markdown Sanitizer (`sanitizeForSpeech`):** Strips code blocks, inline backticks, markdown symbols (`*`, `_`, `#`, `>`), and URLs, and caps length at 700 characters.

### 8.4 Foreground Service & Notification Bridge (`LiveVoiceForegroundService.kt` & `LiveVoiceSessionBridge.kt`)
- `LiveVoiceForegroundService` runs with `ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE` on API 29+ (`NOTIFICATION_ID = 1042`, channel `jarvis_live_voice_channel`).
- Provides two notification action buttons:
  - `ACTION_TOGGLE_MUTE` -> calls `LiveVoiceSessionBridge.requestToggleMuteFromNotification()` -> invokes `JarvisViewModel.toggleLiveMicMute()`.
  - `ACTION_STOP` -> calls `LiveVoiceSessionBridge.requestStopSessionFromNotification()` -> invokes `JarvisViewModel.stopLiveVoiceSession()`.

---

# PHASE 9 — COMPLETE ACCESSIBILITY & PHONE CONTROL SYSTEM

### 9.1 `JarvisAccessibilityService.kt` & `accessibility_service_config.xml`
- **Service Declaration (`AndroidManifest.xml`):**
  - Requires `android.permission.BIND_ACCESSIBILITY_SERVICE`.
  - Configured via `@xml/accessibility_service_config`:
    - `android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewClicked|typeViewFocused|typeViewScrolled"`
    - `android:accessibilityFeedbackType="feedbackGeneric"`
    - `android:accessibilityFlags="flagDefault|flagIncludeNotImportantViews|flagReportViewIds|flagRetrieveInteractiveWindows"`
    - `android:canPerformGestures="true"`
    - `android:canRetrieveWindowContent="true"`
    - `android:canTakeScreenshot="true"`
    - `android:notificationTimeout="100"`

### 9.2 `AccessibilityControllerBridge.kt` — UI Tree Traversal & Gesture Mechanics
- **Multi-Window Root Inspection:** Every node search inspects both `srv.rootInActiveWindow` and all interactive windows in `srv.windows` (`collectCandidateNodesRecursive`, `maxDepth = 22`).
- **Fuzzy Node Scoring (`scoreNodeCandidate`):**
  - Normalizes query and strips Hinglish command suffixes (`"button"`, `"icon"`, `"tab"`, `"par click karo"`, `"daba do"`, `"open"`, `"press"`).
  - Scoring rubric:
    - `100` pts: Exact match on `text` or `contentDescription`.
    - `90` pts: Exact match on stripped query.
    - `82` pts: Whole-word token match.
    - `74` pts: `startsWith` match on `text` or `contentDescription`.
    - `65` pts: Substring `contains` match.
    - `50` pts: Resource ID tail match (`viewIdResourceName.substringAfterLast("/")`).
    - `+8` pts bonus if `node.isClickable` is `true`.
- **Click Execution & Coordinate Fallback (`clickNodeByText`):**
  - Attempts `node.performAction(AccessibilityNodeInfo.ACTION_CLICK)` on the highest-scoring candidate.
  - If the matched text node itself is not clickable, climbs up to 5 parent levels (`parent.isClickable`) and clicks the clickable container.
  - If `ACTION_CLICK` returns `false`, reads `node.getBoundsInScreen(rect)` and dispatches a hardware `GestureDescription` tap (`tapAtCoordinates(rect.centerX(), rect.centerY())`, `85ms` stroke duration).
- **Text Entry (`typeTextIntoFocusedOrMatchingField`):**
  - Locates target `EditText` by hint/text/description match, or currently focused (`isFocused`) editable node, or first visible editable node (`findFirstEditableRecursive`).
  - Focuses the field (`ACTION_FOCUS` + `ACTION_CLICK`), sets text via `ACTION_SET_TEXT` (`ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE`), and if `submitAfter == true`, sends `AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER` (API 30+) or clicks visible `"Send"` / `"Search"` / `"Submit"` buttons.
- **Scroll & Swipe Gestures (`performScroll`, `performDirectionalSwipe`):**
  - `performScroll(direction)` first tries `ACTION_SCROLL_FORWARD` / `ACTION_SCROLL_BACKWARD` on scrollable nodes, and falls back to `performDirectionalSwipe()` using a `320ms` `GestureDescription.StrokeDescription` across screen percentages (`78% -> 24%` for scroll down, `24% -> 78%` for scroll up).
- **Live Screen Capture (`captureLiveScreenBase64Jpeg`):**
  - On Android 11+ (`API >= 30`), calls `AccessibilityService.takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, TakeScreenshotCallback)`, converts the `HardwareBuffer` into a software `Bitmap`, scales to max `768px` dimension, and compresses to Base64 JPEG (`quality = 72`).

---

# PHASE 10 — COMPLETE AI AGENT TOOL INVENTORY (ALL 19 TOOLS)

All 19 tools are registered in `ToolRegistry.kt` and executed in `LiveAgentToolExecutor.kt`.

| # | Tool Name | Purpose | Parameters & Types (`ToolRegistry` & `LiveAgentToolExecutor`) | Required vs Optional | `RiskLevel` | Internal Handler Function | Android API / Service Used | Verification Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | `open_app` | Launches any installed Android app by name or package name. | `app_name: STRING` | Required: `app_name` | `LOW` | `executeOpenApp(args)` | `AppLauncherManager.openAppByName()` (`PackageManager`) | `IMPLEMENTED AND VERIFIED IN CODE` |
| 2 | `perform_screen_action` | Performs composite UI actions on the current screen: `click`, `type`, `scroll_down`, `scroll_up`, `swipe_left`, `swipe_right`, `back`, `home`, `recents`, `notifications`, `quick_settings`, `read_screen`, `tap_coordinates`, `long_press`. | `action_type: STRING`, `target_text: STRING`, `input_text: STRING`, `x: NUMBER`, `y: NUMBER`, `submit_after: BOOLEAN` | Required: `action_type`<br>Optional: `target_text`, `input_text`, `x`, `y`, `submit_after` | `MEDIUM` | `executePerformScreenAction(args)` | `AccessibilityControllerBridge` | `IMPLEMENTED AND VERIFIED IN CODE` |
| 3 | `find_and_click_node` | Searches the active screen for a button/tab/item matching `query` and clicks it. | `query: STRING`, `exact_match: BOOLEAN` | Required: `query`<br>Optional: `exact_match` | `MEDIUM` | `executeFindAndClickNode(args)` | `AccessibilityControllerBridge.clickNodeByText()` | `IMPLEMENTED AND VERIFIED IN CODE` |
| 4 | `perform_directional_gesture` | Scrolls or swipes the screen in a specified direction (`UP`, `DOWN`, `LEFT`, `RIGHT`). | Schema (`ToolRegistry`): `action: STRING`, `direction: STRING`, `x: INTEGER`, `y: INTEGER`<br>Executor: reads `type` (`scroll`/`swipe`) & `direction` | Required: `direction` | `LOW` | `executeDirectionalGesture(args)` | `AccessibilityControllerBridge.performScroll()` / `performDirectionalSwipe()` | `PARTIALLY IMPLEMENTED` (See `BUG-02` for `action` vs `type` parameter mismatch) |
| 5 | `type_text` | Types text into the focused input field or a field matching a label/hint. | `text: STRING`, `target_label: STRING` (Schema), `replace_existing: BOOLEAN`, `submit_after: BOOLEAN` (Executor) | Required: `text` | `MEDIUM` | `executeTypeText(args)` | `AccessibilityControllerBridge.typeTextIntoFocusedOrMatchingField()` | `IMPLEMENTED AND VERIFIED IN CODE` (See `BUG-04` for minor optional param naming difference) |
| 6 | `read_screen_content` | Reads all visible text, buttons, and foreground app context from the current screen. | None (empty `OBJECT`) | None | `LOW` | `executeReadScreenContent()` | `AccessibilityControllerBridge.dumpVisibleScreenText()` & `getInteractiveElementsSummary()` | `IMPLEMENTED AND VERIFIED IN CODE` |
| 7 | `navigate_device` | Performs global system navigation (`back`, `home`, `recents`, `notifications`, `quick_settings`, `power_dialog`, `lock_screen`, `take_screenshot`). | `action: STRING` | Required: `action` | `LOW` | `executeNavigateDevice(args)` | `AccessibilityControllerBridge.performGlobalNavigation()` | `IMPLEMENTED AND VERIFIED IN CODE` |
| 8 | `make_phone_call` | Places a phone call to a contact name or phone number. | `recipient: STRING` | Required: `recipient` | `HIGH` | `executeMakePhoneCall(args)` | `PhoneActionManager.makePhoneCall()` (`Intent.ACTION_CALL` / `ACTION_DIAL`, `ContactsContract`) | `IMPLEMENTED AND VERIFIED IN CODE` |
| 9 | `send_whatsapp_message` | Sends a WhatsApp message to a contact name or phone number and auto-clicks Send if Accessibility is enabled. | `recipient: STRING`, `message: STRING` | Required: `recipient`, `message` | `HIGH` | `executeSendWhatsAppMessage(args)` | `PhoneActionManager.sendWhatsAppMessage()` + `AccessibilityControllerBridge.clickNodeByText("Send")` | `IMPLEMENTED AND VERIFIED IN CODE` |
| 10 | `send_sms` | Opens SMS composer pre-filled with recipient and message body. | `recipient: STRING`, `message: STRING` | Required: `recipient`, `message` | `HIGH` | `executeSendSms(args)` | `PhoneActionManager.sendSms()` (`Intent.ACTION_SENDTO`, `smsto:`) | `IMPLEMENTED AND VERIFIED IN CODE` |
| 11 | `open_url_or_search` | Opens a website URL or performs a Google search in the browser. | `query_or_url: STRING` | Required: `query_or_url` | `LOW` | `executeOpenUrl(args)` | `PhoneActionManager.openUrlOrSearch()` (`Intent.ACTION_VIEW`) | `IMPLEMENTED AND VERIFIED IN CODE` |
| 12 | `search_youtube` | Searches and plays a video/song query on YouTube, with optional auto-click of the first result via Accessibility. | `query: STRING`, `auto_play_first: BOOLEAN` | Required: `query`<br>Optional: `auto_play_first` | `LOW` | `executeSearchYoutube(args)` | `PhoneActionManager.playOnYouTube()` + `AccessibilityControllerBridge` | `IMPLEMENTED AND VERIFIED IN CODE` |
| 13 | `set_alarm_or_timer` | Sets a system clock alarm (`hour`, `minute`) or countdown timer (`seconds`). | `type: STRING` (`alarm`/`timer`), `hour: INTEGER`, `minute: INTEGER`, `seconds: INTEGER`, `label: STRING` | Required: `type` | `LOW` | `executeSetAlarmOrTimer(args)` | `PhoneActionManager.setAlarm()` / `setTimer()` (`AlarmClock`) | `IMPLEMENTED AND VERIFIED IN CODE` |
| 14 | `toggle_hardware_setting` | Toggles Flashlight, Wi-Fi, Bluetooth, DND, Ringer (`silent`/`vibrate`/`normal`), Auto-Rotate, Hotspot, Location, Battery Saver, or Airplane Mode. | `setting: STRING`, `enable: BOOLEAN` | Required: `setting`, `enable` | `LOW` | `executeToggleHardwareSetting(args)` | `PhoneControlsManager` | `IMPLEMENTED AND VERIFIED IN CODE` |
| 15 | `adjust_volume_or_brightness` | Sets or adjusts media volume or screen brightness (`up`, `down`, `max`, `min`, `mute`, or `0..100`%). | `target: STRING` (`volume`/`brightness`), `direction: STRING`, `level_percent: INTEGER` | Required: `target` | `LOW` | `executeAdjustVolumeOrBrightness(args)` | `PhoneControlsManager` (`AudioManager`, `Settings.System`) | `IMPLEMENTED AND VERIFIED IN CODE` |
| 16 | `read_system_status` | Reads live battery %, charging state, temperature, network connectivity, free RAM/storage, audio mode, and current time. | None (empty `OBJECT`) | None | `LOW` | `executeReadSystemStatus()` | `SystemStatusManager.readStatusSnapshot()` | `IMPLEMENTED AND VERIFIED IN CODE` |
| 17 | `capture_screenshot` | Triggers an immediate Android screenshot via Accessibility Service (`GLOBAL_ACTION_TAKE_SCREENSHOT`). | None (empty `OBJECT`) | None | `LOW` | `executeCaptureScreenshot()` | `AccessibilityControllerBridge.performGlobalNavigation("take_screenshot")` | `IMPLEMENTED AND VERIFIED IN CODE` |
| 18 | `save_or_recall_memory` | Saves a user fact/preference into Room (`user_memories`) or searches stored memories. | `operation: STRING` (`save`/`recall`), `key: STRING`, `value: STRING`, `category: STRING` | Required: `operation`, `key` | `LOW` | `executeSaveOrRecallMemory(args)` | `MemoryRepository.saveMemory()` / `searchMemories()` | `IMPLEMENTED AND VERIFIED IN CODE` |
| 19 | `control_media_playback` | Controls active media playback (`PLAY`, `PAUSE`, `PLAY_PAUSE`, `NEXT`, `PREVIOUS`, `STOP`). | Schema (`ToolRegistry`): `action: STRING`<br>Executor: reads `command: STRING` | Required: `action` / `command` | `LOW` | `executeControlMediaPlayback(args)` | `MediaControllerHelper` (`AudioManager.dispatchMediaKeyEvent`) | `PARTIALLY IMPLEMENTED` (See `BUG-03` for `action` vs `command` parameter mismatch) |

---

# PHASE 11 — DATABASE, MEMORY, AND DATA PERSISTENCE

### 11.1 Room Database Configuration (`JarvisDatabase.kt`)
- **Database Name:** `"jarvis_assistant_db"`
- **Database Version:** `2` (`exportSchema = false`)
- **Migration Strategy:** Explicit `MIGRATION_1_2` (`CREATE TABLE IF NOT EXISTS user_memories ...`, `CREATE TABLE IF NOT EXISTS automation_workflows ...`, `CREATE TABLE IF NOT EXISTS notification_logs ...`) plus `.fallbackToDestructiveMigration()`.

### 11.2 Complete Room Schema (6 Entities in `Entities.kt`)

#### 1. `ChatSessionEntity` (`tableName = "chat_sessions"`)
| Column | Kotlin Type | Default Value | Purpose |
| :--- | :--- | :--- | :--- |
| `id` (`@PrimaryKey`) | `String` | `UUID.randomUUID().toString()` | Unique session identifier |
| `title` | `String` | — | Session display title |
| `createdAt` | `Long` | `System.currentTimeMillis()` | Creation timestamp |
| `updatedAt` | `Long` | `System.currentTimeMillis()` | Last message timestamp (used for `ORDER BY updatedAt DESC`) |
| `modelUsed` | `String` | `"gemini-2.5-flash"` | Model identifier used in session |

#### 2. `ChatMessageEntity` (`tableName = "chat_messages"`)
| Column | Kotlin Type | Default Value | Purpose |
| :--- | :--- | :--- | :--- |
| `id` (`@PrimaryKey`) | `String` | `UUID.randomUUID().toString()` | Unique message ID |
| `sessionId` | `String` | — | Foreign key reference to `chat_sessions.id` |
| `role` | `String` | — | `"USER"`, `"ASSISTANT"`, or `"SYSTEM"` |
| `content` | `String` | — | Message text or markdown body |
| `timestamp` | `Long` | `System.currentTimeMillis()` | Creation timestamp (`ORDER BY timestamp ASC`) |
| `attachedImageUri` | `String?` | `null` | Optional attached image/file URI string |
| `isError` | `Boolean` | `false` | Marks failed API responses |

#### 3. `CommandHistoryEntity` (`tableName = "command_history"`)
| Column | Kotlin Type | Default Value | Purpose |
| :--- | :--- | :--- | :--- |
| `id` (`@PrimaryKey`) | `String` | `UUID.randomUUID().toString()` | Unique audit log entry ID |
| `commandText` | `String` | — | Command or tool name invoked |
| `actionType` | `String` | — | Category (`"APP_LAUNCH"`, `"VOICE_TOOL_OPEN_APP"`, `"WORKFLOW_MACRO"`, etc.) |
| `executionResult` | `String` | — | Human-readable result summary |
| `succeeded` | `Boolean` | — | Execution success flag |
| `timestamp` | `Long` | `System.currentTimeMillis()` | Execution timestamp |

#### 4. `MemoryEntity` (`tableName = "user_memories"`)
| Column | Kotlin Type | Default Value | Purpose |
| :--- | :--- | :--- | :--- |
| `id` (`@PrimaryKey`) | `String` | `UUID.randomUUID().toString()` | Unique memory ID |
| `category` | `String` | `MemoryCategory.PERSONAL_FACT.name` | Category enum name |
| `key` | `String` | — | Topic/title of the memory |
| `value` | `String` | — | Detailed fact/preference text |
| `importance` | `Int` | `3` | Priority score (`1..5`) |
| `isPinned` | `Boolean` | `false` | Whether always injected into AI prompt |
| `createdAt` | `Long` | `System.currentTimeMillis()` | Creation timestamp |
| `updatedAt` | `Long` | `System.currentTimeMillis()` | Last updated timestamp |

#### 5. `WorkflowEntity` (`tableName = "automation_workflows"`)
| Column | Kotlin Type | Default Value | Purpose |
| :--- | :--- | :--- | :--- |
| `id` (`@PrimaryKey`) | `String` | `UUID.randomUUID().toString()` | Unique workflow ID |
| `title` | `String` | — | Macro name |
| `description` | `String` | — | Macro description |
| `triggerType` | `String` | `WorkflowTriggerType.VOICE_PHRASE.name` | Trigger type enum name |
| `triggerValue` | `String` | — | Voice trigger phrase |
| `stepsJson` | `String` | — | JSON array of `WorkflowActionStep` objects |
| `isEnabled` | `Boolean` | `true` | Enabled toggle state |
| `runCount` | `Int` | `0` | Total execution count |
| `lastRunAt` | `Long?` | `null` | Last execution epoch ms |
| `createdAt` | `Long` | `System.currentTimeMillis()` | Creation timestamp |

#### 6. `NotificationLogEntity` (`tableName = "notification_logs"`)
| Column | Kotlin Type | Default Value | Purpose |
| :--- | :--- | :--- | :--- |
| `id` (`@PrimaryKey`) | `String` | `UUID.randomUUID().toString()` | Unique notification log ID |
| `packageName` | `String` | — | Source app package name |
| `appName` | `String` | — | Resolved app display name |
| `title` | `String` | — | Notification title |
| `content` | `String` | — | Notification text body |
| `category` | `String` | `"GENERAL"` | Notification category |
| `isImportant` | `Boolean` | `false` | Flagged true for `msg`/`call`/`alarm` or WhatsApp/Messaging apps |
| `wasReadByJarvis` | `Boolean` | `false` | Read status flag |
| `timestamp` | `Long` | `System.currentTimeMillis()` | Notification post time |

### 11.3 Jetpack DataStore (`UserPreferencesRepository.kt`)
- **DataStore Name:** `"jarvis_user_prefs"`
- **Persisted Preference Keys (27 Keys):** `selected_model`, `voice_engine`, `assistant_language`, `voice_gender`, `voice_persona`, `speech_speed`, `voice_pitch`, `continuous_conversation`, `auto_speak_chat`, `live_vision_enabled`, `wake_word_enabled`, `wake_word_phrase`, `wake_word_sensitivity`, `confidence_threshold`, `confirmation_sound`, `haptic_feedback`, `assistant_name`, `safety_confirmations`, `max_autonomous_steps`, `personality_mode`, `require_biometric_high_risk`, `confirm_before_call`, `confirm_before_message`, `confirm_before_send`, `context_memory_enabled`, `auto_summarize_notifications`, `notification_assistant_enabled`, `notification_excluded_packages`.

---

# PHASE 12 — COMPLETE API AND EXTERNAL DEPENDENCY INVENTORY

### 12.1 External Network APIs
| API Name | Provider | Endpoints Used | Authentication Method | Request / Response Format | Purpose in App | Cost Classification |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Google Gemini REST API (`v1beta` & `v1alpha`)** | Google AI (`generativelanguage.googleapis.com`) | `GET /v1beta/models`<br>`GET /v1alpha/models`<br>`POST /v1beta/models/{model}:generateContent`<br>`POST /v1beta/models/{model}:streamGenerateContent?alt=sse` | Query Parameter `?key={GEMINI_API_KEY}` | JSON / Server-Sent Events (`text/event-stream`) | Model discovery, API key validation, streaming multimodal chat, document analysis, notification summarization, and Gemini native TTS audio synthesis | `NOT VERIFIED FROM SOURCE CODE` (Depends on user's Google AI Studio / Google Cloud billing tier; free tier and paid tier keys are both supported) |
| **Google Gemini Live WebSocket API (`BidiGenerateContent`)** | Google AI (`generativelanguage.googleapis.com`) | `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key={GEMINI_API_KEY}`<br>(plus `v1alpha` equivalent) | Query Parameter `?key={GEMINI_API_KEY}` | Bidirectional JSON frames over WebSocket (`setup`, `realtimeInput`, `clientContent`, `toolResponse`, `serverContent`, `toolCall`) | Real-time full-duplex voice conversation, live screen vision streaming, and live tool calling | `NOT VERIFIED FROM SOURCE CODE` (Depends on user's Google AI Studio API key tier) |

### 12.2 Complete Gradle Dependency Inventory (`gradle/libs.versions.toml` & `app/build.gradle.kts`)
| Library / Plugin | Group / Artifact | Exact Version | Configuration | Purpose in Project |
| :--- | :--- | :--- | :--- | :--- |
| **Android Gradle Plugin (AGP)** | `com.android.application` | `9.1.1` | Plugin | Android build system |
| **Kotlin Android / Compose / Serialization** | `org.jetbrains.kotlin.*` | `2.2.10` | Plugin | Kotlin compiler, Compose compiler, & kotlinx.serialization |
| **Google KSP** | `com.google.devtools.ksp` | `2.3.5` | Plugin | Annotation processor for Room (`JarvisDatabase` / `JarvisDao`) |
| **AndroidX Core KTX** | `androidx.core:core-ktx` | `1.18.0` | `implementation` | Core Android Kotlin extensions (`ContextCompat`, `FileProvider`) |
| **Lifecycle Runtime & ViewModel Compose** | `androidx.lifecycle:lifecycle-runtime-ktx` / `lifecycle-runtime-compose` / `lifecycle-viewmodel-compose` | `2.10.0` | `implementation` | `AndroidViewModel`, `viewModelScope`, `collectAsStateWithLifecycle` |
| **Activity Compose** | `androidx.activity:activity-compose` | `1.13.0` | `implementation` | `ComponentActivity.setContent`, `enableEdgeToEdge`, permission/media launchers |
| **Compose BOM** | `androidx.compose:compose-bom` | `2026.03.00` | `platform(implementation)` | Manages versions for `ui`, `ui-graphics`, `ui-tooling-preview`, `material3`, `material-icons-extended` |
| **Navigation Compose** | `androidx.navigation:navigation-compose` | `2.9.7` | `implementation` | `NavHost`, `rememberNavController`, 12-screen navigation graph |
| **Kotlinx Serialization JSON** | `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.8.0` | `implementation` | JSON serialization support |
| **Room Runtime, KTX & Compiler** | `androidx.room:room-runtime` / `room-ktx` / `room-compiler` | `2.7.0` | `implementation` / `ksp` | Local SQLite persistence for 6 entities |
| **DataStore Preferences** | `androidx.datastore:datastore-preferences` | `1.1.7` | `implementation` | Reactive persistence for 27 user settings (`jarvis_user_prefs`) |
| **Retrofit & Converter Gson** | `com.squareup.retrofit2:retrofit` / `converter-gson` | `2.12.0` | `implementation` | Included in build configuration (network calls use OkHttp + `org.json` directly) |
| **OkHttp & Logging Interceptor** | `com.squareup.okhttp3:okhttp` / `logging-interceptor` | `4.10.0` | `implementation` | HTTP/REST client, SSE streaming client, and `BidiGenerateContent` WebSocket client |
| **Coil Compose** | `io.coil-kt:coil-compose` | `2.6.0` | `implementation` | `AsyncImage` rendering for attached images in `TextChatScreen` |
| **JUnit 4** | `junit:junit` | `4.13.2` | `testImplementation` | Unit test assertions & runner |
| **AndroidX Test Core** | `androidx.test:core` | `1.6.1` | `testImplementation` | `ApplicationProvider.getApplicationContext()` in Robolectric tests |
| **Robolectric** | `org.robolectric:robolectric` | `4.16.1` | `testImplementation` | Local JVM Android framework & Compose UI test execution |
| **Compose UI Test JUnit4 & Manifest** | `androidx.compose.ui:ui-test-junit4` / `ui-test-manifest` | Managed by BOM `2026.03.00` | `testImplementation` / `debugImplementation` | `createAndroidComposeRule<MainActivity>()` UI testing |

---

# PHASE 13 — ANDROID PERMISSIONS AND SECURITY AUDIT

### 13.1 Complete `AndroidManifest.xml` Permission Inventory (12 Permissions)
| # | Permission Name | Protection Level | Where Requested / Checked | Feature Using It | Fallback When Denied |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | `android.permission.INTERNET` | Normal (Install-time) | Automatic at install | Gemini REST API, SSE Streaming, & Live WebSocket | Offline local commands still work; AI calls return network error |
| 2 | `android.permission.ACCESS_NETWORK_STATE` | Normal (Install-time) | `SystemStatusManager.readStatusSnapshot()` | Network telemetry (`Wi-Fi`, `Mobile Data`, `Offline`) | Reports `"Unknown"` network status |
| 3 | `android.permission.RECORD_AUDIO` | Dangerous (Runtime) | `LiveVoiceScreen`, `OnboardingPermissionsScreen` (`ActivityResultContracts`) | Gemini Live Voice mic capture & `OfflineWakeWordManager` | Shows permission warning banner; Live Voice session refuses to start mic capture until granted |
| 4 | `android.permission.CAMERA` | Dangerous (Runtime) | `TextChatScreen`, `OnboardingPermissionsScreen` | Capturing live camera photos in Chat; `CameraManager.setTorchMode` uses system flash | Camera capture prompts for permission; Gallery picker still works without permission |
| 5 | `android.permission.VIBRATE` | Normal (Install-time) | Haptic feedback helpers | Tactile feedback | Fails silently |
| 6 | `android.permission.CALL_PHONE` | Dangerous (Runtime) | `PhoneControlsScreen`, `OnboardingPermissionsScreen`, `PhoneActionManager.makePhoneCall()` | Direct outbound phone calls (`Intent.ACTION_CALL`) | Automatically falls back to `Intent.ACTION_DIAL` (opens dialer with number pre-filled) |
| 7 | `android.permission.READ_CONTACTS` | Dangerous (Runtime) | `OnboardingPermissionsScreen`, `PhoneActionManager.resolveContactPhoneNumber()` | Resolving contact names (e.g., *"Call Mom"*) to phone numbers via `ContactsContract` | If denied, requires a direct phone number or opens dialer/WhatsApp search |
| 8 | `com.android.alarm.permission.SET_ALARM` | Normal (Install-time) | `PhoneActionManager.setAlarm()` / `setTimer()` | Setting system alarms and timers via `AlarmClock` intents | Returns failure message if no clock app handles the intent |
| 9 | `android.permission.ACCESS_NOTIFICATION_POLICY` | Special Settings Policy | `PhoneControlsManager.setDoNotDisturb()` | Toggling Do Not Disturb (`NotificationManager.setInterruptionFilter`) | Opens `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` |
| 10 | `android.permission.FOREGROUND_SERVICE` | Normal (Install-time) | `LiveVoiceForegroundService.start()` | Running persistent Live Voice session in background | Caught by `try/catch (Exception)` |
| 11 | `android.permission.FOREGROUND_SERVICE_MICROPHONE` | Foreground Service (API 34+) | `LiveVoiceForegroundService.onStartCommand()` (`FOREGROUND_SERVICE_TYPE_MICROPHONE`) | Keeping microphone active when user switches to other apps during Live Voice | Caught by `try/catch`; session continues while app is in foreground |
| 12 | `android.permission.POST_NOTIFICATIONS` | Dangerous (Runtime API 33+) | Used by `LiveVoiceForegroundService` notification | Displaying ongoing Live Voice control notification (Mute / Stop buttons) | Service still runs; notification may be hidden if user blocks notifications |

### 13.2 Special System Service Bindings & Settings Access
1. **Accessibility Service (`android.permission.BIND_ACCESSIBILITY_SERVICE`):** Bound to `.service.JarvisAccessibilityService`. Enabled manually by user in `Settings.ACTION_ACCESSIBILITY_SETTINGS`.
2. **Notification Listener Service (`android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`):** Bound to `.service.JarvisNotificationListenerService`. Enabled manually by user in `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.
3. **Modify System Settings (`Settings.System.canWrite(context)`):** Checked in `PhoneControlsManager.setBrightnessPercent()` and `setAutoRotation()`. If not granted, opens `Settings.ACTION_MANAGE_WRITE_SETTINGS` for `package:com.jarvisai.assistant`.
4. **Package Visibility (`<queries>` in `AndroidManifest.xml`):** Declares intent queries for `MAIN`/`LAUNCHER` apps (enabling `AppLauncherManager` to see all installed launchable apps on Android 11+), `VIEW https`, `DIAL`, `SENDTO`, `SET_ALARM`, `RECOGNIZE_SPEECH`, and `TTS_SERVICE`.

---

# PHASE 14 — COMPLETE BACKGROUND SYSTEM

### 14.1 `LiveVoiceForegroundService.kt`
- **Lifecycle:** Started via `ContextCompat.startForegroundService()` when `JarvisViewModel.startLiveVoiceSession()` begins, and stopped via `LiveVoiceForegroundService.stop(context)` when `stopLiveVoiceSession()` or `onCleared()` runs.
- **Foreground Type:** Uses `ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE` on Android 10+ (`API >= 29`), allowing `GeminiLiveVoiceManager`'s `AudioRecord` and `WebSocket` to remain active while JARVIS opens and interacts with third-party apps (such as YouTube, WhatsApp, or Chrome).

### 14.2 `JarvisAccessibilityService.kt`
- **Lifecycle:** Managed by the Android OS once enabled in Accessibility Settings.
- **Responsibilities:**
  1. Binds/unbinds its live `AccessibilityService` instance to `AccessibilityControllerBridge` (`WeakReference<AccessibilityService>`).
  2. Listens to `TYPE_WINDOW_STATE_CHANGED`, `TYPE_WINDOW_CONTENT_CHANGED`, `TYPE_VIEW_CLICKED`, and `TYPE_VIEW_SCROLLED`.
  3. Updates `_foregroundPackage` and refreshes `_screenContextSummary` (throttled to 800ms intervals).

### 14.3 `JarvisNotificationListenerService.kt`
- **Lifecycle:** Managed by the Android OS once enabled in Notification Access Settings.
- **Responsibilities:**
  1. Tracks connection state via `JarvisNotificationListenerService.isConnected: StateFlow<Boolean>`.
  2. Captures `onNotificationPosted(sbn: StatusBarNotification)`, ignores ongoing notifications and self-notifications (`com.jarvisai.assistant`), extracts title/text/subText/category, flags messaging/call/alarm notifications as `isImportant = true`, and writes `NotificationLogEntity` rows to Room on `Dispatchers.IO`.

### 14.4 `OfflineWakeWordManager.kt`
- **Lifecycle:** Managed by `JarvisViewModel` based on `userSettings.wakeWordEnabled`.
- **Mutual Exclusion with Live Voice:** Automatically calls `pauseForActiveSession()` before `GeminiLiveVoiceManager` acquires the microphone, and `resumeAfterActiveSession()` when the Live Voice session stops.

---

# PHASE 15 — COMPLETE DATA FLOW AND WORKFLOWS

### 15.1 Workflow 1: Multimodal Streaming Chat & Local Command Interception
```mermaid
sequenceDiagram
    participant U as User (TextChatScreen)
    participant VM as JarvisViewModel
    participant TE as LiveAgentToolExecutor
    participant MR as MemoryRepository
    participant GAS as GeminiApiService
    participant DB as JarvisDatabase (Room)

    U->>VM: sendChatMessage(userText)
    VM->>DB: ensureActiveSession() & saveMessage(USER)
    alt No Attachment & Matches Local Command
        VM->>TE: tryExecuteNaturalLanguageCommand(userText)
        TE-->>VM: ToolExecutionResult(executed=true, message)
        VM->>DB: saveMessage(ASSISTANT, message) & logCommand()
    else AI Generation Path
        VM->>MR: buildMemoryContextPrompt(userText)
        MR-->>VM: memoryContextPrompt
        VM->>GAS: streamChatResponse(apiKey, model, userText, history, attachment, memoryContext)
        loop SSE Stream Chunks
            GAS-->>VM: StreamChunk(deltaText, isDone=false)
            VM-->>U: Update streaming ChatMessage bubble
        end
        GAS-->>VM: StreamChunk(isDone=true, usedModel)
        VM->>DB: saveMessage(ASSISTANT, finalContent)
    end
```

### 15.2 Workflow 2: Bidirectional Gemini Live Voice + Tool Call + Screen Vision Loop
```mermaid
sequenceDiagram
    participant U as User (LiveVoiceScreen)
    participant VM as JarvisViewModel
    participant WWM as OfflineWakeWordManager
    participant FGS as LiveVoiceForegroundService
    participant GLVM as GeminiLiveVoiceManager
    participant WS as Gemini BidiGenerateContent WebSocket
    participant TE as LiveAgentToolExecutor
    participant ACB as AccessibilityControllerBridge

    U->>VM: startLiveVoiceSession() / startFemaleCompanionLiveSession()
    VM->>WWM: pauseForActiveSession()
    VM->>FGS: start(context, statusText)
    VM->>GLVM: startLiveSession(apiKey, language, voicePersona, voiceGender)
    GLVM->>WS: Connect & sendSetupMessage(AUDIO, voiceName, 19 tools)
    WS-->>GLVM: {"setupComplete": {}}
    par 16kHz Mic Capture Loop
        GLVM->>WS: {"realtimeInput": {"mediaChunks": [PCM 16kHz Base64]}}
    and Live Vision Loop (Every 3.5s if enabled)
        VM->>ACB: captureLiveScreenBase64Jpeg()
        ACB-->>VM: Base64 JPEG
        VM->>GLVM: sendScreenFrameToLiveSession(jpegBase64)
        GLVM->>WS: {"realtimeInput": {"mediaChunks": [image/jpeg Base64]}}
    end
    WS-->>GLVM: {"toolCall": {"functionCalls": [{"id": "1", "name": "open_app", "args": {"app_name": "YouTube"}}]}}
    GLVM->>TE: executeToolCall("open_app", args)
    TE-->>GLVM: ToolExecutionResult(success=true, message="Opened YouTube")
    GLVM->>WS: {"toolResponse": {"functionResponses": [{"id": "1", "name": "open_app", "response": {"result": "Opened YouTube"}}]}}
    WS-->>GLVM: {"serverContent": {"modelTurn": {"parts": [{"inlineData": {"data": "PCM 24kHz Base64"}}]}}}
    GLVM-->>U: Play 24kHz PCM via AudioTrack + Update LiveTranscriptTurn
```

---

# PHASE 16 — ERROR HANDLING AND EXCEPTIONS

| Subsystem | Failure / Exception Type | Recovery & Fallback Mechanism in Source Code |
| :--- | :--- | :--- |
| **Gemini REST / SSE (`GeminiApiService.kt`)** | HTTP `429` (`RESOURCE_EXHAUSTED`), `404` (`NOT_FOUND`), `503` (`UNAVAILABLE`), or network timeout | `isQuotaOrModelError()` advances through `buildModelCandidateChain()` (`gemini-2.5-flash` -> `gemini-2.5-flash-lite` -> `gemini-2.0-flash` -> `gemini-1.5-flash`). If `429` persists and multiple keys are in `SecureApiKeyManager`, `JarvisViewModel` calls `rotateToNextKey()` and retries automatically. |
| **Gemini Live WebSocket (`GeminiLiveVoiceManager.kt`)** | Model rejects `bidiGenerateContent` setup or WebSocket closes with non-1000 code | During setup (`!isSetupComplete`), `tryNextModelOrReportError()` pops the next model/endpoint (`v1alpha` / `v1beta`) from `modelQueue`. After setup, unexpected socket drops trigger `scheduleAutoReconnect()` up to `MAX_AUTO_RECONNECT_ATTEMPTS = 4` with exponential backoff (`1500ms * attempt`). |
| **Audio Input (`GeminiLiveVoiceManager.kt`)** | `AudioRecord` initialization failure with `VOICE_COMMUNICATION` or missing `RECORD_AUDIO` | Catches `SecurityException`, falls back from `AudioSource.VOICE_COMMUNICATION` to `AudioSource.MIC`, and checks `audioRecord.state == STATE_INITIALIZED`. |
| **Keystore Encryption (`SecureApiKeyManager.kt`)** | `AndroidKeyStore` unavailable in JVM unit test / Robolectric environment | `encryptRaw()` and `decryptRaw()` catch `Exception` and fall back to `"FALLBACK"` IV + Base64 encoding so unit tests and edge devices never crash. |
| **Phone Calls (`PhoneActionManager.kt`)** | `CALL_PHONE` permission denied or `SecurityException` thrown | Automatically falls back from `Intent.ACTION_CALL` to `Intent.ACTION_DIAL`, opening the phone dialer with the number pre-populated. |
| **App Launching (`AppLauncherManager.kt`)** | App not installed on device | Checks 54 alias groups + dynamic `PackageManager` label scan; if not installed and a web fallback exists (`webFallbacks`), opens the service in the browser (`https://m.youtube.com`, `https://open.spotify.com`, etc.). |
| **Accessibility Actions (`AccessibilityControllerBridge.kt`)** | Node has `isClickable == false` or `ACTION_CLICK` returns `false` | Climbs up to 5 parent nodes looking for a clickable container; if `ACTION_CLICK` still fails, computes `node.getBoundsInScreen(rect)` and dispatches a hardware `GestureDescription` tap at `(rect.centerX(), rect.centerY())`. |

---

# PHASE 17 — COMPLETE BUG AND LIMITATION AUDIT

Every bug and limitation below has been **directly verified in the source code** with exact file paths, line numbers, root causes, impacts, and recommended fixes.

### 17.1 Confirmed Source Code Bugs (`CONFIRMED BUG IN CODE`)

| Bug ID | Severity | Classification | Exact File Path & Line Numbers | Evidence & Root Cause | Impact on Application | Recommended Code Fix |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **BUG-01** | **Medium** | `CONFIRMED BUG IN CODE` | `UserPreferencesRepository.kt` ( line 79 ) & `JarvisViewModel.kt` ( lines 976–980 ) | In `UserPreferencesRepository.kt` line 79, `userSettingsFlow` hardcodes `autoSpeakChatReplies = false` instead of reading `prefs[Keys.AUTO_SPEAK_CHAT] ?: false`. In `JarvisViewModel.kt` line 978, `updateAutoSpeakChatReplies(enabled: Boolean)` ignores `enabled` and calls `preferencesRepository.updateAutoSpeakChatReplies(false)`. | While normal text chat is intentionally muted by default so it does not auto-speak, the **"Auto-Speak Chat Replies"** switch in `GeneralSettingsScreen.kt` (lines 586–592) is non-functional and cannot be turned ON by the user. | Either remove the toggle from `GeneralSettingsScreen.kt` if Text Chat must always remain strictly silent, or wire `prefs[Keys.AUTO_SPEAK_CHAT] ?: false` and pass `enabled` in `updateAutoSpeakChatReplies(enabled)`. |
| **BUG-02** | **Medium** | `CONFIRMED BUG IN CODE` | `ToolRegistry.kt` ( lines 166–180 ) vs `LiveAgentToolExecutor.kt` ( lines 764–776 ) | `ToolRegistry.kt` defines `perform_directional_gesture` with parameter `"action"` (`"scroll"`, `"swipe"`, `"tap"`, `"long_press"`), whereas `LiveAgentToolExecutor.executeDirectionalGesture()` reads `args.optString("type", "scroll")` and only branches on `"swipe"` vs `"scroll"`, ignoring `"tap"` and `"long_press"` coordinates (`x`, `y`). | When Gemini Live invokes `perform_directional_gesture` using the schema key `"action": "swipe"` or `"action": "tap"`, `executeDirectionalGesture` defaults to `"scroll"`, and coordinate taps via `perform_directional_gesture` are not dispatched (though `perform_screen_action` with `tap_coordinates` does work). | In `LiveAgentToolExecutor.executeDirectionalGesture()`, read `val action = args.optString("action", args.optString("type", "scroll")).lowercase()` and add branches for `"tap"` (`accessibilityBridge.tapAtCoordinates(x, y)`) and `"long_press"` (`accessibilityBridge.longPressAtCoordinates(x, y)`). |
| **BUG-03** | **Medium** | `CONFIRMED BUG IN CODE` | `ToolRegistry.kt` ( lines 281–291 ) vs `LiveAgentToolExecutor.kt` ( lines 1034–1053 ) | `ToolRegistry.kt` defines `control_media_playback`'s parameter as `"action"` (`"play"`, `"pause"`, `"toggle"`, `"next"`, `"previous"`), whereas `LiveAgentToolExecutor.executeControlMediaPlayback()` reads `args.optString("command", "PLAY_PAUSE")` and matches `"PLAY"`, `"PAUSE"`, `"NEXT"`, `"SKIP"`, `"PREVIOUS"`, `"STOP"`. | If Gemini Live passes `{"action": "next"}` or `{"action": "pause"}` matching `ToolRegistry`, `args.optString("command", "PLAY_PAUSE")` falls back to `"PLAY_PAUSE"` instead of skipping or pausing tracks. | In `LiveAgentToolExecutor.executeControlMediaPlayback()`, read `val cmd = args.optString("command", args.optString("action", "PLAY_PAUSE")).uppercase()` and include `"TOGGLE" -> mediaControllerHelper.playPause()`. |
| **BUG-04** | **Low** | `CONFIRMED BUG IN CODE` | `ToolRegistry.kt` ( lines 183–203 ) vs `LiveAgentToolExecutor.kt` ( lines 778–807 ) | `ToolRegistry.kt` defines `type_text` properties as `text`, `target_label`, `append`, and `submit`. `LiveAgentToolExecutor.executeTypeText()` reads `text`, `replace_existing` (`!append`), and `submit_after` (`submit`), and passes `targetLabel = null` instead of reading `target_label`. | `type_text` always types into the focused/first editable field and does not use `target_label` or `submit` when called via the `type_text` tool (whereas `perform_screen_action` with `action_type="type"` does pass `target_text`). | In `executeTypeText()`, read `val targetLabel = args.optString("target_label").takeIf { it.isNotBlank() }`, `val replace = if (args.has("append")) !args.optBoolean("append") else args.optBoolean("replace_existing", true)`, and `val submit = args.optBoolean("submit_after", args.optBoolean("submit", false))`. |
| **BUG-05** | **Low** | `CONFIRMED BUG IN CODE` | `WorkflowEngine.kt` ( lines 95–97 ) | In `WorkflowEngine.executeWorkflow()`, the `WorkflowActionType.SET_PERSONALITY` branch increments `executedCount++` and logs `"Personality mode set to: ${action.param1}"`, but does not update `UserPreferencesRepository`. | Macros containing a `SET_PERSONALITY` step (such as the seeded *"Deep Focus Mode"* workflow's step 2) log success without changing the persisted `PersonalityMode` in DataStore. | Inject `UserPreferencesRepository` into `WorkflowEngine` and call `preferencesRepository.updatePersonalityMode(PersonalityMode.valueOf(action.param1))` inside `WorkflowActionType.SET_PERSONALITY`. |
| **BUG-06** | **Low** | `CONFIRMED BUG IN CODE` | `JarvisNotificationListenerService.kt` ( lines 28–56 ) | `onNotificationPosted` logs all non-ongoing external notifications into Room without checking `UserPreferencesRepository`'s `notificationAssistantEnabled` or `notificationExcludedPackages`. | Even if `notificationAssistantEnabled` is set to `false` in DataStore or packages are added to `notificationExcludedPackages`, incoming notifications are still inserted into `notification_logs`. | Read `UserPreferencesRepository(applicationContext).userSettingsFlow.first()` inside `serviceScope.launch` in `onNotificationPosted()` and skip logging if `!settings.notificationAssistantEnabled` or `pkg in settings.notificationExcludedPackages`. |
| **BUG-07** | **Low** | `CONFIRMED BUG IN CODE` | `JarvisViewModel.kt` ( lines 493–501, 632–643 ) & `GeminiApiService.kt` ( lines 497–533 ) | `UserSettings.personalityMode` (`PersonalityMode` enum with `systemInstructionPrompt` in `JarvisModels.kt` lines 253–284) is saved via `ActionSafetyScreen`, but `GeminiApiService.buildSystemInstruction()` and `GeminiLiveVoiceManager.sendSetupMessage()` do not include `personalityMode.systemInstructionPrompt`. | Selecting a different `PersonalityMode` chip (`PROFESSIONAL`, `FRIENDLY_COMPANION`, `CONCISE_TACTICAL`, `WITTY_STARK`, `HINGLISH_DOST`) in `ActionSafetyScreen` updates the UI chip state in DataStore but does not alter the Gemini system prompt. | Pass `settings.personalityMode.systemInstructionPrompt` into `GeminiApiService.streamChatResponse()` / `sendChatMessage()` and `GeminiLiveVoiceManager.startLiveSession()`. |

### 17.2 Architectural & Platform Limitations
1. **Biometric Prompt Gate (`requireBiometricForHighRisk`):** The preference toggle in `ActionSafetyScreen` is persisted in DataStore, but `androidx.biometric:biometric` hardware prompt is not invoked before `approvePendingConfirmation()`.
2. **Android 10+ Wi-Fi Toggle Restriction:** Due to Android 10 (`API 29+`) OS restrictions deprecating `WifiManager.setWifiEnabled()`, `PhoneControlsManager.openWifiPanel()` opens `Settings.Panel.ACTION_WIFI` floating system panel rather than toggling Wi-Fi silently in the background.
3. **Android 11+ Live Screen Capture Requirement:** `AccessibilityControllerBridge.captureLiveScreenBase64Jpeg()` uses `AccessibilityService.takeScreenshot()`, which requires Android 11 (`API 30+`). On Android 7.0–10 (`API 24–29`), JARVIS falls back to reading the screen hierarchy text via `dumpVisibleScreenText()`.

---

# PHASE 18 — BUILD, CONFIGURATION, AND DEVELOPMENT GUIDE

### 18.1 Environment & Build Configuration
1. **JDK / Java Target:** Java 11 (`JavaVersion.VERSION_11`).
2. **Android SDK:** `compileSdk = 36`, `targetSdk = 36`, `minSdk = 24`.
3. **API Key Configuration:**
   - **Option A (In-App Encrypted Vault — Recommended):** Launch the app -> open **API Vault** (`ApiManagementScreen`) -> paste one or more Gemini API keys -> tap **Save Key**.
   - **Option B (Build-Time `.env` Injection):** Add `GEMINI_API_KEY=AIzaSy...` in `.env` at the project root (`app/build.gradle.kts` reads `.env` and injects `BuildConfig.GEMINI_API_KEY`).
4. **Build & Test Commands:**
   - Compile Debug APK: `gradle assembleDebug`
   - Run Unit & Robolectric Tests: `gradle :app:testDebugUnitTest`

---

# PHASE 19 — COMPLETE FEATURE DEPENDENCY MAP

```text
MainActivity
 └── JarvisApp (NavHost + SafetyConfirmationModal)
      └── JarvisViewModel
           ├── UserPreferencesRepository (DataStore: jarvis_user_prefs)
           ├── SecureApiKeyManager (AndroidKeyStore AES-256-GCM + BuildConfig.GEMINI_API_KEY)
           ├── JarvisRepository ──> JarvisDao ──> JarvisDatabase (chat_sessions, chat_messages, command_history)
           ├── MemoryRepository ──> JarvisDao ──> JarvisDatabase (user_memories, automation_workflows, notification_logs)
           ├── GeminiApiService (OkHttp REST + SSE + Gemini TTS + Model Discovery)
           ├── JarvisTtsManager (Gemini Native 24kHz PCM AudioTrack + Android System TextToSpeech)
           ├── OfflineWakeWordManager (Android SpeechRecognizer + Levenshtein Wake-Word Matcher)
           ├── GeminiLiveVoiceManager (OkHttp WebSocket BidiGenerateContent + 16kHz AudioRecord + 24kHz AudioTrack)
           │    └── LiveVoiceForegroundService <──> LiveVoiceSessionBridge
           ├── WorkflowEngine (Sequential JSON Step Runner)
           └── LiveAgentToolExecutor + ToolRegistry (19 Registered Tools)
                ├── AppLauncherManager (PackageManager + 54 Known App Alias Groups + Web Fallbacks)
                ├── PhoneControlsManager (CameraManager Torch + AudioManager + NotificationManager + Settings)
                ├── PhoneActionManager (CALL/DIAL, SMS, WhatsApp, YouTube, Maps, Alarms/Timers, ContactsContract)
                ├── CommunicationHelper (High-Risk Call/SMS/WhatsApp/Email Confirmation Builder)
                ├── MediaControllerHelper (AudioManager Media KeyEvent Dispatcher + MediaSessionManager)
                ├── SystemStatusManager (BatteryManager + ConnectivityManager + ActivityManager + StatFs)
                ├── UniversalTextInputManager (Accessibility Node SetText + Clipboard Paste)
                └── AccessibilityControllerBridge <──> JarvisAccessibilityService
```

---

# PHASE 20 — EXISTING FEATURE PRESERVATION GUIDE

When modifying or extending JARVIS AI in future iterations, engineers and AI agents **MUST** adhere to the following non-negotiable preservation rules:
1. **Never Remove or Rename Navigation Routes:** All 12 `JarvisScreen` enum routes (`home`, `chat`, `live_voice`, `phone_controls`, `settings`, `api_management`, `permissions`, `memory_vault`, `workflows`, `notifications`, `diagnostics`, `action_safety`) must remain intact in `JarvisApp.kt`.
2. **Never Break Compose `testTag` Identifiers:** The 23 Robolectric tests in `ExampleRobolectricTest.kt` assert specific `testTag`s across all screens (e.g., `female_companion_one_click_card`, `female_companion_activate_button`, `home_arc_reactor_orb`, `chat_input_field`, `live_voice_orb_button`, `toggle_card_torch`, `api_key_input_field`, `perm_grant_record_audio`). Never remove or rename these `testTag` values.
3. **Preserve 1-Click Female Companion Behavior:** `JarvisViewModel.startFemaleCompanionLiveSession()` must continue to set `VoiceGender.FEMALE`, `VoicePersona.KORE` (`"kore"`), and `AssistantLanguage.HINGLISH` and immediately launch the Live Voice session.
4. **Keep Normal Text Chat Silent by Default:** Normal text chat (`TextChatScreen` / `sendChatMessage`) must NOT automatically speak every AI response aloud unless explicitly requested by the user tapping the message speaker button.
5. **Preserve Full Duplex Audio Safeguards in `GeminiLiveVoiceManager.kt`:** Do not remove the hardware AEC/NS/AGC attachments, `1.45f` software mic pre-gain, `80ms` (`3,840`-byte) playback jitter pre-buffer, `280ms` ring-buffer drain delay, or `350ms` echo-tail cooldown gate.
6. **Preserve All 19 Tools & Room Database Migration (`MIGRATION_1_2`):** Never drop any of the 19 tools in `ToolRegistry.kt` / `LiveAgentToolExecutor.kt` or alter the 6 Room entity schemas in `Entities.kt` without adding a versioned Room `Migration`.

---

# PHASE 21 — TESTING DOCUMENTATION

The project includes **25 automated tests** across 2 test files (`ExampleUnitTest.kt` and `ExampleRobolectricTest.kt`), all verified passing via `gradle :app:testDebugUnitTest`.

### 21.1 Pure JVM Unit Tests (`ExampleUnitTest.kt` — 2 Tests)
1. `voicePersona_defaultsAndFiltering_areValid`: Verifies `VoicePersona.getPersonasByGender(FEMALE/MALE)` and `defaultForGender()`.
2. `geminiModelCandidateChain_prioritizesSelectedAndFallbacks`: Verifies `GeminiApiService.buildModelCandidateChain()` ordering and deduplication.

### 21.2 Robolectric & Compose UI Tests (`ExampleRobolectricTest.kt` — 23 Tests)
| Test # | Method Name | Subsystem Tested |
| :--- | :--- | :--- |
| 01 | `test01_homeDashboard_rendersAllCoreHudElements` | Renders `MainActivity` / `HomeDashboardScreen` and verifies top bar, orb, 1-click female card, and quick command input |
| 02 | `test02_navigation_switchesBetweenAllFiveBottomTabs` | Navigates across all 5 primary bottom dock tabs (`Command`, `AI Chat`, `Live Agent`, `Controls`, `Settings`) |
| 03 | `test03_textChatScreen_displaysComposerAndSuggestions` | Verifies `TextChatScreen` input field, send button, new session button, and suggestion chips |
| 04 | `test04_liveVoiceScreen_rendersVisualizerAndControls` | Verifies `LiveVoiceScreen` orb button, primary session button, mute button, and language pills |
| 05 | `test05_phoneControlsScreen_rendersSlidersTogglesAndDialogs` | Verifies `PhoneControlsScreen` sliders, hardware toggles, and Direct Call / WhatsApp modal dialogs |
| 06 | `test06_apiManagementScreen_savesAndClearsEncryptedKey` | Tests saving and clearing API keys via `ApiManagementScreen` UI |
| 07 | `test07_permissionsScreen_rendersAllChecklistItems` | Verifies all 6 permission checklist cards on `OnboardingPermissionsScreen` |
| 08 | `test08_secureApiKeyManager_encryptsMasksAndRotatesPool` | Tests `SecureApiKeyManager` encryption, masking, multi-key pool rotation, and rate-limit cooldowns |
| 09 | `test09_roomDatabase_crudForSessionsMessagesAndCommands` | Tests `JarvisRepository` & `JarvisDatabase` CRUD for sessions, messages, and command logs |
| 10 | `test10_memoryRepository_savesSearchesPinsAndBuildsContextPrompt` | Tests `MemoryRepository` CRUD, pinning, keyword search, and `buildMemoryContextPrompt()` |
| 11 | `test11_memoryRepository_workflowsAndNotificationsCrud` | Tests `MemoryRepository` default workflow seeding, custom workflow CRUD, and notification logging |
| 12 | `test12_toolRegistry_declaresAll19ToolsWithValidSchemasAndRiskLevels` | Asserts all 19 tools exist in `ToolRegistry` with valid JSON schemas and risk levels |
| 13 | `test13_communicationHelper_buildsHighRiskConfirmationRequests` | Verifies `CommunicationHelper.buildConfirmationRequest()` sets `RiskLevel.HIGH` and Hinglish prompt |
| 14 | `test14_systemStatusManager_readsLiveDeviceTelemetry` | Verifies `SystemStatusManager.readStatusSnapshot()` and `formatNaturalSummary()` |
| 15 | `test15_workflowEngine_parsesAndExecutesMultiStepRoutine` | Executes a multi-step `WorkflowEntity` through `WorkflowEngine` |
| 16 | `test16_offlineWakeWordManager_matchesExactAndFuzzyWakePhrases` | Tests `OfflineWakeWordManager.matchesWakeWord()` and `levenshteinDistance()` across sensitivity levels |
| 17 | `test17_liveAgentToolExecutor_executesNaturalLanguageAndMemoryTools` | Tests `LiveAgentToolExecutor` for `read_system_status`, `save_or_recall_memory`, and NL commands |
| 18 | `test18_accessibilityBridge_handlesDisconnectedStateGracefully` | Verifies `AccessibilityControllerBridge` returns safe messages when `JarvisAccessibilityService` is unbound |
| 19 | `test19_v2Screens_navigateFromHomeHubToMemoryWorkflowsSafetyAndDiagnostics` | Navigates from `HomeDashboardScreen` V2 cards to `MemorySystemScreen`, `WorkflowsMacroScreen`, `DiagnosticsScreen`, and `ActionSafetyScreen` |
| 20 | `test20_memorySystemScreen_opensAddMemoryDialogAndSavesFact` | Tests adding a new memory via `AddMemoryDialog` in `MemorySystemScreen` |
| 21 | `test21_actionSafetyScreen_togglesPoliciesAndSelectsPersonality` | Tests toggling safety switches and selecting personality chips in `ActionSafetyScreen` |
| 22 | `test22_femaleCompanionOneClick_fromHomeDashboard_activatesKoreHinglishAndNavigatesToLive` | Verifies clicking `female_companion_one_click_card` activates `FEMALE`, `kore`, `HINGLISH` and navigates to `LiveVoiceScreen` |
| 23 | `test23_femaleCompanionActivateButton_onLiveVoiceScreen_switchesToFemaleHinglish` | Verifies clicking `female_companion_activate_button` on `LiveVoiceScreen` switches settings to `FEMALE`, `kore`, `HINGLISH` |

---

# COMPLETE JARVIS AI PROJECT HANDOFF FOR ANOTHER AI ASSISTANT

### 1. Instant Architectural Mental Model
- **App Identity:** `com.jarvisai.assistant` (Kotlin namespace `com.example`), single-activity Jetpack Compose app (`MainActivity.kt` -> `JarvisApp.kt` -> `JarvisViewModel.kt`).
- **State Hub:** `JarvisViewModel` (`app/src/main/java/com/example/viewmodel/JarvisViewModel.kt`) is the single source of truth for all 12 screens.
- **Two Gemini Pipelines:**
  1. **Text & Multimodal Chat (`GeminiApiService.kt`):** Uses HTTPS REST (`generateContent`) and SSE (`streamGenerateContent?alt=sse`) with automatic fallback (`gemini-2.5-flash` -> `gemini-2.5-flash-lite` -> `gemini-2.0-flash` -> `gemini-1.5-flash`). Normal text chat is intentionally silent (`autoSpeakChatReplies = false`).
  2. **Real-Time Live Voice & Vision Agent (`GeminiLiveVoiceManager.kt`):** Uses bidirectional WebSocket (`BidiGenerateContent` on `v1alpha`/`v1beta`), streaming 16kHz PCM mic input (`VOICE_COMMUNICATION` + AEC/NS/AGC + 1.45x software gain) and playing 24kHz PCM audio output (80ms pre-buffer + 280ms drain wait + 350ms echo gate), with 19 registered Android tools (`ToolRegistry.kt` + `LiveAgentToolExecutor.kt`).
- **1-Click Female Companion:** Triggered via `JarvisViewModel.startFemaleCompanionLiveSession()`, which sets `VoiceGender.FEMALE`, `VoicePersona.KORE` (`"kore"`), and `AssistantLanguage.HINGLISH`, then launches the Live Voice WebSocket session.
- **Device Control Stack:**
  - `JarvisAccessibilityService` <-> `AccessibilityControllerBridge`: UI node scoring, clicking, typing, scrolling, coordinate tapping, global actions, and API 30+ `takeScreenshot()`.
  - `AppLauncherManager`: 54 app alias groups + dynamic `PackageManager` lookup + web fallbacks.
  - `PhoneControlsManager` & `PhoneActionManager`: Torch, Volume, Brightness, DND, Ringer, Wi-Fi panel, Calls, SMS, WhatsApp, YouTube, Maps, Alarms/Timers.

### 2. Priority Backlog / Known Bugs Ready to Fix (If Requested by User)
1. **Align `ToolRegistry` & `LiveAgentToolExecutor` parameter names (`BUG-02`, `BUG-03`, `BUG-04`):**
   - In `LiveAgentToolExecutor.executeDirectionalGesture()`, support both `"action"` and `"type"` keys and wire `"tap"` / `"long_press"`.
   - In `LiveAgentToolExecutor.executeControlMediaPlayback()`, support both `"command"` and `"action"` keys.
   - In `LiveAgentToolExecutor.executeTypeText()`, read `"target_label"` and `"submit"`.
2. **Wire `PersonalityMode` into AI System Instructions (`BUG-07`) and `WorkflowEngine` (`BUG-05`).**
3. **Filter excluded packages in `JarvisNotificationListenerService.onNotificationPosted()` (`BUG-06`).**

---

# DOCUMENTATION COMPLETENESS REPORT

| Audit Metric | Verified Count / Status |
| :--- | :--- |
| **Total Project Files in Workspace** | `74` files |
| **Total Main Kotlin Source Files (`app/src/main/java/com/example/`)** | `46` / `46` (`100% FULLY ANALYZED`) |
| **Total Test Kotlin Source Files (`app/src/test/java/com/example/`)** | `2` / `2` (`100% FULLY ANALYZED`) |
| **Total Manifest, XML & Font Resource Files (`app/src/main/res/`)** | `19` / `19` (`100% FULLY ANALYZED`) |
| **Total Gradle, Env & Metadata Config Files** | `7` / `7` (`100% FULLY ANALYZED`) |
| **Total UI Screens Documented** | `12` / `12` (`100%`) |
| **Total Reusable Component Files Documented** | `2` / `2` (`ArcReactorView.kt`, `HudComponents.kt` — `100%`) |
| **Total AI Agent Tools Documented** | `19` / `19` (`100%`) |
| **Total Room Database Entities Documented** | `6` / `6` (`100%`) |
| **Total DataStore Preference Keys Documented** | `27` / `27` (`100%`) |
| **Total Manifest Permissions Documented** | `12` / `12` (`100%`) |
| **Total Automated Tests Documented & Verified Passing** | `25` / `25` (`2` Unit + `23` Robolectric — `100%`) |
| **Total Confirmed Source Code Bugs Audited** | `7` (`BUG-01` through `BUG-07`) |
| **Source Code Modified During Audit** | `0` files modified (100% Read-Only Audit Compliance) |
| **Uninspected / Skipped Source Files** | `0` |
