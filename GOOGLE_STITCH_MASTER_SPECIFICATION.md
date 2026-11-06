# GOOGLE STITCH MASTER SPECIFICATION
## Comprehensive Codebase Audit & System Handoff Document for Frontend Re-Architecture

---

### Document Information
- **Document Title:** Jarvis AI Operating Assistant – Google Stitch Master Functional Specification
- **Target Audience:** Google Stitch Design Team, Senior UI/UX Architects, System Engineers
- **Scope:** Complete Functional Audit, Architecture Mapping, AI & Device Integrations, and Design Handoff
- **Mode:** Read-Only Audit & Specification (Zero Code Modification / Pure Functional Handoff)
- **Status:** Approved for Next-Generation Frontend Architecture

---

## 1. Application Overview

### 1.1 Purpose and Core Mission
**Jarvis AI** is an advanced, native Android AI Operating Assistant and autonomous agent. Engineered on modern Android runtime environments (targeting Android 15/16, API level 36 with backward compatibility to Android 7.0, API level 24), Jarvis bridges high-intelligence Large Language Models (LLMs) with deep native device capabilities. 

Unlike conventional chatbots or conversational wrappers, Jarvis operates as an **Autonomous Device-Agent and Personal Cognitive Companion**:
1. **Multimodal Perception:** Real-time awareness across screen vision (MediaProjection streaming), voice capture (low-latency 16 kHz PCM streaming), and environmental sensors (telephony, driving movement, notifications).
2. **Autonomous Computer Use Agent (CUA):** Hands-free physical device execution via Android Accessibility APIs, Set-of-Marks visual grounding, coordinate clicking, gesture dispatching, and cross-application automation.
3. **5-Tier Cognitive Memory Engine:** Persistent episodic, semantic, working, relational, and contextual memory architecture backed by a local relational database (Room SQLite).
4. **Adaptive Multimodal Live Streaming:** Bidirectional WebSocket integration with Gemini Multimodal Live API supporting sub-second audio turnarounds, voice barge-in (interruption), and real-time screen frame interpretation.
5. **System-Level Guardian & Automator:** Intelligent call screening, hands-free driving detection, anti-theft watchman monitoring, Termux command execution, lockscreen wallpapers, and system-wide floating overlay controls.

### 1.2 Target Audience & Usage Contexts
- **Productivity Power Users & Developers:** Users requiring autonomous device execution (e.g., "Summarize this PDF, open WhatsApp, find Rahul, and send him the bullet points").
- **Hands-Free & In-Transit Users:** Drivers, cyclists, and commuters using hands-free wake word activation ("Hey Jarvis") with offline Silero VAD (Voice Activity Detection), driving telemetry, and real-time live voice dialogues.
- **Privacy & Security Conscious Users:** Individuals who demand strict zero-telemetry call screening, local audio buffering, and explicit security confirmation gates for sensitive actions (payments, SMS deletion, system settings changes).
- **Personalized Companionship:** Users seeking emotionally resonant, contextual, and customizable personas ranging from a sharp British Butler (Tactical Jarvis) to empathetic, humorous, or professional companions supporting natural Hindi, English, and Hinglish.

---

## 2. Project Architecture

### 2.1 High-Level Architectural Pattern
The application follows a decoupled **Clean Architecture + MVVM (Model-View-ViewModel)** paradigm with reactive unidirectional data flows (UDF):

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PRESENTATION LAYER                              │
│       Jetpack Compose UI (Targeted for Google Stitch Redesign)         │
│  StateFlow Observers • User Action Dispatchers • Accessibility Bridge   │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ Dispatches Actions / Observes States
┌──────────────────────────────────▼─────────────────────────────────────┐
│                         VIEWMODEL LAYER                                │
│                       JarvisViewModel.kt                               │
│     Cognitive Orchestrator • Session Management • UI State Machine      │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ Invokes Use Cases & Services
┌──────────────────────────────────▼─────────────────────────────────────┐
│                          DOMAIN LAYER                                  │
│   • ToolRegistry (37+ Schemas)       • AgenticMobileEngine (10 Engines)│
│   • SileroVadManager (Voice VAD)     • OfflineWakeWordManager (Porcupine)│
│   • JarvisDrivingModeManager         • JarvisWatchmanManager           │
│   • DocumentProcessor (PDF/OCR)      • MemoryTierClassifier            │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ Reads / Writes / Streams
┌──────────────────────────────────▼─────────────────────────────────────┐
│                           DATA LAYER                                   │
│  • GeminiApiService (REST SSE)       • GeminiLiveVoiceManager (WS)     │
│  • JarvisDatabase (8 Room Entities)  • UserSettingsDataStore           │
│  • Android System Services (Telephony, Notification, Accessibility)    │
└────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Core Component Boundaries & Communication
1. **UI Layer (Google Stitch Target):** Purely declarative, stateless, and reactive. Observes Kotlin `StateFlow` from `JarvisViewModel` and dispatches strongly-typed actions. The presentation layer contains zero direct business logic, direct SQLite queries, or unmanaged coroutine scopes.
2. **ViewModel Layer (`JarvisViewModel.kt`):** Acts as the central mediator. It exposes:
   - `uiState: StateFlow<JarvisUiState>`
   - `liveVoiceUiState: StateFlow<LiveVoiceUiState>`
   - `drivingUiState: StateFlow<DrivingModeUiState>`
   - `watchmanUiState: StateFlow<WatchmanUiState>`
   - `memoryStats: StateFlow<MemoryStatistics>`
   - `chatSessions: StateFlow<List<ChatSessionEntity>>`
   - `activeTools: StateFlow<List<ToolExecutionState>>`
3. **Service-Bridge Bus:** Android background services (`LiveVoiceForegroundService`, `JarvisAccessibilityService`, `JarvisNotificationListenerService`, `JarvisOverlayHudService`) communicate with the ViewModel through Kotlin `SharedFlow` events, bound service binders, and singleton event broadcasters.
4. **Repository & Data Access Layer:** Database persistence is managed via Room DAOs (`ChatDao`, `MemoryDao`, `ReminderDao`, `WorkflowDao`). Settings and preferences are persisted through `DataStore<Preferences>`.

---

## 3. Technology Stack

### 3.1 Platform & Runtime
- **Operating System:** Android (Minimum SDK: 24 / Android 7.0 Nougat, Target & Compile SDK: 36 / Android 16 Baklava preview).
- **Core Language:** 100% Kotlin with strict null-safety and Coroutine structured concurrency.
- **Dependency Injection:** Lightweight constructor injection and singleton service locators.
- **Build System:** Gradle (Kotlin DSL `build.gradle.kts`) with Version Catalog (`libs.versions.toml`) and Android Gradle Plugin (AGP).

### 3.2 Key Libraries & Frameworks
| Category | Framework / Library | Role & Purpose |
| :--- | :--- | :--- |
| **UI & Graphics** | Jetpack Compose (M3) | Declarative UI framework, Canvas drawing, dynamic layout composition |
| **Architecture** | AndroidX ViewModel & Lifecycle | Lifecycle-aware StateFlow hosting and lifecycle management |
| **Networking** | OkHttp 4.12.0 | Bidirectional WebSockets, HTTP/2 streaming, SSE (Server-Sent Events) |
| **Database** | Room 2.6.1 (with KSP) | SQLite abstraction for 8 entities with reactive Flow queries |
| **Preferences** | Jetpack DataStore Preferences | Asynchronous key-value storage for settings and theme state |
| **Audio & Media** | Android AudioRecord & AudioTrack | 16 kHz 16-bit mono PCM capture and low-latency audio playback |
| **Screen Perception** | Android MediaProjection | Virtual display screen capture yielding JPEG/RGBA frame streams |
| **Voice Detection** | Silero VAD (ONNX Runtime / Local) | Energy and neural Voice Activity Detection with 30ms windowing |
| **Wake Word** | Picovoice Porcupine / Acoustic Regex | Low-power offline wake-word triggers ("Hey Jarvis", "Jarvis") |
| **Serialization** | Kotlinx Serialization | Type-safe JSON serialization for tool calls and API payloads |
| **Document Parsing** | Android PdfRenderer & OCR APIs | Native PDF rasterization and text extraction |
| **Unit Testing** | Robolectric 4.12.2 + JUnit 4 | Local JVM Android simulation and unit testing suite |

---

## 4. Complete Feature Specification

The application features 15 distinct functional modules. Each is documented below with its functional purpose, user interactions, inputs, outputs, error conditions, and dependencies.

### 4.1 Core Conversational AI & Multi-Turn Chat
- **Purpose:** Full multi-turn conversational AI with context retention, markdown rendering, code block formatting, and dynamic role playing.
- **User Actions:** Enter text via keyboard; attach images, documents, or screenshots; send message; edit past queries; copy responses; regenerate replies; delete or branch sessions.
- **Inputs:** Text strings, image URIs (JPEG, PNG, WEBP), document URIs (PDF, TXT), audio attachments.
- **Outputs:** Streaming token updates, formatted markdown, rendered code blocks, structured tool invocation status cards.
- **Dependencies:** `GeminiApiService`, `ChatDao`, `UserSettingsDataStore`.
- **States & Recovery:**
  - *Loading:* Token streaming shimmer/typing pulse.
  - *Empty:* Suggested quick-action prompts, persona introduction.
  - *Error:* Rate limit (HTTP 429) backoff banner with retry action; network timeout notification.

### 4.2 Real-Time Multimodal Live Voice (Bidirectional WebSocket)
- **Purpose:** Natural, human-like voice conversation with sub-500ms latency, automatic barge-in (interruption detection), and real-time screen/camera feed ingestion.
- **User Actions:** Tap to start live voice session; mute microphone; toggle video/screen stream; interrupt Jarvis while speaking; end session.
- **Inputs:** 16 kHz 16-bit mono PCM microphone audio stream, MediaProjection video frames.
- **Outputs:** 24 kHz PCM audio playback stream via `AudioTrack`, real-time visualizer waveform amplitude stream (0.0 to 1.0), live subtitle transcriptions.
- **Dependencies:** `GeminiLiveVoiceManager`, `LiveVoiceForegroundService`, `AudioRecord`, `AudioTrack`.
- **States & Recovery:**
  - *Connecting:* Pulsing indicator, WebSocket handshake.
  - *Active Speaking:* Waveform visualization synchronized to incoming audio amplitude.
  - *Active Listening:* Waveform visualization reacting to user's mic input.
  - *Barge-In Triggered:* Immediate audio track buffer flush and cancellation of queued audio packets.
  - *Error:* Reconnection loop with backoff; fallback to REST voice synthesis if WebSocket disconnects.

### 4.3 10-Engine Autonomous Mobile Agent (Computer Use Agent - CUA)
- **Purpose:** Enables Jarvis to see the screen and execute multi-step physical device actions autonomously across any third-party Android application.
- **The 10 CUA Engines:**
  1. *Perception Engine:* Captures screen pixels via MediaProjection or Accessibility node hierarchy trees.
  2. *Set-of-Marks (SoM) Grounding Engine:* Overlays numbered visual bounding tags onto interactive screen elements for deterministic targeting.
  3. *Coordinate Transformer:* Translates normalized 1000x1000 AI model coordinates into exact physical display pixel bounds.
  4. *Gesture Dispatch Engine:* Executes taps, double taps, long presses, linear swipes, and curved scroll gestures via `GestureDescription`.
  5. *Text Injection Engine:* Types strings into input fields via Accessibility bundle arguments or clipboard pasting fallback.
  6. *App Navigation Engine:* Launches applications via explicit Android package intents and returns via system back/home gestures.
  7. *Verification & Self-Correction Engine:* Takes post-action screenshots, compares UI state against goal criteria, and retries alternate strategies if an action fails.
  8. *Safety & Gatekeeper Engine:* Intercepts sensitive operations (e.g., payments, banking apps, privacy settings, account deletion) and requires explicit biometric/user confirmation.
  9. *Action Sequence Planner (ReAct):* Decomposes high-level instructions (e.g., "Order my usual pizza on Domino's") into a DAG (Directed Acyclic Graph) of micro-actions.
  10. *Execution Telemetry & HUD Engine:* Displays execution progress, current step description, target bounding highlights, and emergency stop button.
- **Inputs:** Natural language goals (e.g., "Open YouTube, search for Kotlin tutorials, and play the first video").
- **Outputs:** Real-time visual highlight tags, step-by-step HUD progress logs, completed/failed status callback.
- **Dependencies:** `JarvisAccessibilityService`, `AgenticMobileEngine`, `MediaProjection`.
- **States & Recovery:**
  - *Execution:* Interactive HUD overlay with "Cancel Action" button.
  - *Error / Stuck:* If target node is unclickable or absent after 3 attempts, Jarvis pauses, captures the screen, informs the user, and asks for guidance.

### 4.4 5-Tier Memory Architecture
- **Purpose:** Emulates human cognitive memory across five distinct operational layers to ensure lifelong personalization and zero context loss.
- **The 5 Memory Tiers:**
  1. *Tier 1: Working Memory (Active Task Buffer):* Manages short-term scratchpad state for ongoing multi-step workflows. Cleared upon task completion.
  2. *Tier 2: Episodic Memory (Events & Logs):* Temporal records of past interactions, completed user requests, and system events with timestamps.
  3. *Tier 3: Semantic Memory (Knowledge Triples):* Structured knowledge graphs representing factual information (Subject-Predicate-Object triples, e.g., `[User] -> [Prefers] -> [Dark Roast Coffee]`).
  4. *Tier 4: Relational & Social Memory:* Stores user relationships, key dates (birthdays, anniversaries), emotional profiles, and contact associations.
  5. *Tier 5: Core Identity Memory:* Immutable user preferences, emergency contacts, name, home/work addresses, and strict privacy constraints.
- **User Actions:** View stored memories; search knowledge graph; manually edit or delete memories; filter by category; wipe working memory.
- **Inputs:** Automatic extraction from conversational interactions; manual user input.
- **Outputs:** Filtered memory cards, knowledge relationship graph visualization, category counts.
- **Dependencies:** `JarvisDatabase`, `MemoryDao`, `MemoryTierClassifier`.

### 4.5 Intelligent Call Screening & Telephony Assistant
- **Purpose:** Automatically answers unknown or spam calls, engages callers in a conversational screening dialogue using Gemini AI, transcribes the caller's purpose in real time, and alerts the user with Accept/Reject options.
- **User Actions:** Enable/disable screening; customize greeting prompt; view live incoming call transcription; tap "Take Call", "Reject Call", or "Ask for Details".
- **Inputs:** Android Telecom connection audio, caller ID, real-time caller speech.
- **Outputs:** Real-time call transcription card, spam score evaluation (0-100%), caller intent summary.
- **Dependencies:** `JarvisTelephonyManager`, `RoleManager.ROLE_CALL_SCREENING`, `TelecomManager`.

### 4.6 Smart Notifications & Notification Listener
- **Purpose:** Monitors incoming system notifications from messaging apps (WhatsApp, Telegram, SMS, Email), filters low-priority spam, summarizes important messages, and enables voice/one-tap quick replies.
- **User Actions:** View aggregated notification feed; toggle auto-summarize; dictate reply; dismiss notifications.
- **Inputs:** `StatusBarNotification` payloads (title, text, package name, actions).
- **Outputs:** Structured notification feed, grouped conversation summaries, AI-drafted reply options.
- **Dependencies:** `JarvisNotificationListenerService`, `NotificationLogDao`.

### 4.7 Driving Mode & Auto Telemetry
- **Purpose:** Provides a low-distraction, voice-first automotive dashboard that automatically activates upon Bluetooth vehicle connection or GPS high-speed detection.
- **User Actions:** Start navigation; dictate destination; play music; listen to unread messages; trigger emergency call.
- **Inputs:** Device GPS speed, Bluetooth connected device profiles, accelerometer motion.
- **Outputs:** High-contrast, glanceable telemetry cards (speedometer, ETA, media player, compass).
- **Dependencies:** `JarvisDrivingModeManager`, `LocationManager`, `BluetoothAdapter`.

### 4.8 Watchman & Device Security Guardian
- **Purpose:** Anti-theft and device surveillance mode. Activates camera and sensors when the device is picked up, an incorrect lock PIN is entered, or unauthorized movement is detected.
- **User Actions:** Arm/disarm Watchman; set sensitivity threshold; view captured intruder snapshots; configure emergency alert contacts.
- **Inputs:** Front/back camera feeds, accelerometer sensor events, battery charge disconnect events.
- **Outputs:** Intruder snapshot logs, silent location broadcast via SMS/Email, high-decibel alarm.
- **Dependencies:** `JarvisWatchmanManager`, `CameraManager`, `SensorManager`.

### 4.9 Document & Multimodal Vision Processor
- **Purpose:** Ingests documents (PDFs, TXT, DOCX), extracts text and visual charts, executes OCR on scanned receipts or paper documents, and provides instant Q&A.
- **User Actions:** Pick document file; take photo of physical paper; search within document; export summary.
- **Inputs:** Document URIs, camera bitmap streams.
- **Outputs:** Extracted text segments, multi-page previews, tabular data representations, conversational answers.
- **Dependencies:** `DocumentProcessor`, Android `PdfRenderer`.

### 4.10 Developer & Termux Power Bridge
- **Purpose:** Enables advanced users to execute local Linux commands, shell scripts, Python scripts, or Git commands via the Termux Android app.
- **User Actions:** Run terminal command; view stdout/stderr output; save executable workflow scripts.
- **Inputs:** Shell script strings, environment arguments.
- **Outputs:** Real-time terminal output stream, exit codes, execution duration.
- **Dependencies:** `JarvisTermuxBridge`, `com.termux` intent receiver.

### 4.11 Floating Overlay HUD & Quick Assistant
- **Purpose:** System-wide floating bubble/dock that persists across any Android app, providing immediate access to voice commands, screen analysis, and quick tools.
- **User Actions:** Drag bubble position; tap to expand HUD dock; trigger instant screen perception; collapse bubble.
- **Inputs:** Touch drag coordinates, screen content beneath overlay.
- **Outputs:** Compact floating UI pill, expanded tool tray, mini visualizer.
- **Dependencies:** `JarvisOverlayHudService`, `Settings.canDrawOverlays()`.

### 4.12 Voice Notes & Instant Transcription
- **Purpose:** Quick-capture audio memo recorder with local Whisper/Gemini transcription, automatic title generation, action-item extraction, and calendar sync.
- **User Actions:** Tap to record; pause/resume; tag notes; export transcription to notes or reminders.
- **Inputs:** Microphone audio files.
- **Outputs:** Transcribed text, extracted tasks/bullets, audio waveform playback.
- **Dependencies:** `JarvisVoiceNoteManager`, `AudioRecord`.

### 4.13 Dynamic AI Wallpaper & Ambient Display
- **Purpose:** Live Android wallpaper engine displaying an animated AI core (Arc Reactor / Organic Orb) that reacts dynamically to battery level, time of day, and Jarvis listening states.
- **User Actions:** Set as home/lock screen wallpaper; customize core style; toggle battery reaction.
- **Inputs:** Battery broadcast events, time ticks, system theme events.
- **Outputs:** Hardware-accelerated OpenGL / Canvas wallpaper surface.
- **Dependencies:** `JarvisWallpaperService`, `WallpaperManager`.

### 4.14 Reminders & Autonomous Workflow Automations
- **Purpose:** Natural language scheduling and multi-step scheduled routines (e.g., "Every morning at 8 AM, read the weather, unread emails, and top news headlines").
- **User Actions:** Create reminder; build visual workflow step sequence; toggle active automations.
- **Inputs:** Time triggers, intent triggers, geofencing triggers.
- **Outputs:** Scheduled AlarmManager notifications, automated task execution chains.
- **Dependencies:** `ReminderDao`, `WorkflowDao`, `AlarmManager`.

### 4.15 Personality, Voice & Appearance Customization
- **Purpose:** Comprehensive personalization hub allowing users to configure the companion's persona, vocal timbre, speech rate, language dialect, visual theme palette, and display mode.
- **User Actions:** Select personality mode; test voice sample; toggle language; select theme preset; switch between Light/Dark/System Default modes.
- **Inputs:** User preferences selections.
- **Outputs:** Immediate app-wide styling updates, altered system prompt directives, updated voice synthesis configs.
- **Dependencies:** `UserSettingsDataStore`, `SupportedGeminiModels`.

---

## 5. AI and Agentic Capabilities

### 5.1 AI Models & Multi-Tier Model Architecture
The app natively supports dynamic switching across Google's frontier Gemini model family, configured with auto-discovery and intelligent fallback:
- **Flagship Multimodal Agent Models:**
  - `gemini-2.5-flash` (Primary default for fast, highly capable multi-turn reasoning and tool orchestration)
  - `gemini-2.5-pro` (Deep reasoning, long document synthesis, complex code analysis)
  - `gemini-2.0-flash-exp` (Ultra-low latency conversational interaction)
- **Real-Time Multimodal Live Voice Models:**
  - `gemini-2.0-flash-exp` (WebSocket Bidi Live API endpoint with 24 kHz audio output and continuous video streaming)
- **Local On-Device Models (Offline Fallback):**
  - Silero VAD (Neural voice activity detection)
  - Acoustic Wake Word Engine (Porcupine / PocketSphinx offline keyword spotting)

### 5.2 Tool Registry (Comprehensive 37+ Tool Schemas)
Every tool registered in `ToolRegistry.kt` is exposed to Gemini as a structured function declaration with strict JSON schemas:

| # | Tool Identifier | Risk Level | Description & Arguments |
| :---: | :--- | :---: | :--- |
| 1 | `open_app` | `LOW_RISK` | Opens any installed app by package name or common app name (`app_name`). |
| 2 | `click_screen` | `LOW_RISK` | Clicks at exact coordinates `x, y` or element label on the active screen. |
| 3 | `type_text` | `LOW_RISK` | Types `text` into the currently focused input field. |
| 4 | `scroll_screen` | `LOW_RISK` | Scrolls screen in specified `direction` (`UP`, `DOWN`, `LEFT`, `RIGHT`) by `distance`. |
| 5 | `press_back` | `READ_ONLY` | Performs the Android system back button action. |
| 6 | `press_home` | `READ_ONLY` | Performs the Android system home button action. |
| 7 | `take_screenshot` | `READ_ONLY` | Captures the active screen bitmap and returns image metadata for visual inspection. |
| 8 | `read_screen_content` | `READ_ONLY` | Inspects and returns the full accessibility text/node hierarchy of the current foreground screen. |
| 9 | `make_phone_call` | `SENSITIVE` | Dials a phone number `phone_number` or contact name `contact_name`. |
| 10 | `send_sms` | `SENSITIVE` | Sends an SMS message with `body` to `recipient`. Confirmation required if high risk. |
| 11 | `set_device_volume` | `LOW_RISK` | Adjusts volume level `level` (0-100) for stream `stream_type` (`MEDIA`, `RING`, `ALARM`). |
| 12 | `set_screen_brightness` | `LOW_RISK` | Sets display brightness `brightness` (0-255). |
| 13 | `toggle_flashlight` | `LOW_RISK` | Turns device flashlight `enabled` (`true`/`false`). |
| 14 | `toggle_wifi` | `LOW_RISK` | Opens Wi-Fi settings or toggles Wi-Fi state. |
| 15 | `toggle_bluetooth` | `LOW_RISK` | Toggles Bluetooth radio state or opens pairing dialog. |
| 16 | `set_alarm` | `LOW_RISK` | Sets system alarm for `hour`, `minute`, with optional `message` and `days`. |
| 17 | `set_reminder` | `LOW_RISK` | Saves an internal reminder with `title`, `due_timestamp`, `priority`. |
| 18 | `create_calendar_event` | `LOW_RISK` | Schedules an event with `title`, `start_time`, `end_time`, `location`. |
| 19 | `search_contacts` | `READ_ONLY` | Searches address book by `query` and returns matching names and numbers. |
| 20 | `read_notifications` | `READ_ONLY` | Returns recent unread notifications with filters for app package or time window. |
| 21 | `reply_notification` | `SENSITIVE` | Dispatches inline quick reply text `reply_text` to active notification `notification_id`. |
| 22 | `store_memory` | `LOW_RISK` | Saves long-term memory triple or episodic fact (`subject`, `predicate`, `object`, `category`). |
| 23 | `recall_memories` | `READ_ONLY` | Performs semantic search across 5 memory tiers using `query`. |
| 24 | `delete_memory` | `SENSITIVE` | Removes a specific memory record by `memory_id`. |
| 25 | `execute_termux_command`| `SENSITIVE` | Executes shell command `command` inside Termux environment. |
| 26 | `start_call_screening` | `LOW_RISK` | Arms telephony call screener for incoming calls. |
| 27 | `arm_watchman_mode` | `LOW_RISK` | Activates anti-theft surveillance with specified `sensor_sensitivity`. |
| 28 | `activate_driving_mode`| `LOW_RISK` | Launches automotive driving UI with telemetry hooks. |
| 29 | `scan_document` | `READ_ONLY` | Initiates camera document scanning and OCR pipeline. |
| 30 | `search_web` | `READ_ONLY` | Performs live web search using Google Search grounding for fresh real-time facts. |
| 31 | `query_device_telemetry`| `READ_ONLY` | Retrieves battery %, charging status, thermal state, RAM usage, storage free. |
| 32 | `play_music` | `LOW_RISK` | Dispatches media playback command `query` to default music player (Spotify, YT Music). |
| 33 | `pause_music` | `LOW_RISK` | Pauses active media playback. |
| 34 | `navigate_to` | `LOW_RISK` | Launches Google Maps turn-by-turn navigation to `destination_address`. |
| 35 | `take_photo` | `LOW_RISK` | Captures snapshot using front or rear camera without launching camera app. |
| 36 | `execute_workflow` | `SENSITIVE` | Runs a multi-step custom sequence of automated actions by `workflow_id`. |
| 37 | `confirm_sensitive_action`| `SENSITIVE` | Prompts user with biometric/PIN gate to confirm execution of gated operations. |

### 5.3 Agentic Autonomous Execution Loop (ReAct)
The autonomous mobile agent executes according to the **Observe -> Orient -> Plan -> Act -> Verify (OOPAV)** cognitive cycle:
```
┌────────────────────────────────────────────────────────┐
│                   USER HIGH-LEVEL GOAL                 │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│ 1. OBSERVE & ORIENT                                    │
│    • Capture Screen Frame (MediaProjection)            │
│    • Dump Accessibility Hierarchy Trees                │
│    • Ground Interactive Targets with Numbered Tags     │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│ 2. REASON & PLAN                                       │
│    • Gemini 2.5 Flash analyzes current screen vs goal   │
│    • Select next tool call (e.g. click_screen(tag=4))  │
│    • Determine if confirmation gate is required        │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│ 3. ACT                                                 │
│    • AccessibilityService executes physical gesture     │
│    • Coordinates mapped to exact display pixels        │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│ 4. VERIFY & SELF-CORRECT                               │
│    • Wait 500ms for UI transition                      │
│    • Capture verification screenshot                   │
│    • Check if target state achieved; retry if failed   │
└────────────────────────────────────────────────────────┘
```

### 5.4 Personas & Personality Modes
The system prompt dynamically adapts across 15 distinct personality profiles:
1. **Tactical Jarvis:** The quintessential British butler—crisp, ultra-efficient, polite, and technically formidable.
2. **Companion (Warm & Caring):** Empathetic, supportive, observant of user stress, offering uplifting encouragement.
3. **Dost (Casual Bro):** Informal Hindi/English colloquial friend ("Bhai", "Yaar"), witty, relaxed, highly relatable.
4. **Savage Roast:** Sharp, sarcastic, playful teasing, witty comebacks (safe and humorous).
5. **Professional Executive:** Concise, formal, corporate-grade summaries, meeting minutes, zero fluff.
6. **Code Mentor:** Deep developer context, architectural tips, clean code explanations, debugging insights.
7. **Romantic Companion:** Sweet, poetic, emotionally attuned, caring.
8. **18+ Girlfriend Mode:** Playful, affectionate, personalized intimate conversational tone.
9. **Fitness Coach:** Energetic, motivational, tracking water intake, workout reps, and diet discipline.
10. **Study Partner:** Socratic method, quiz generator, flashcard drills, memory hooks.
11. **Philosopher:** Deep existential reflections, stoic wisdom, multi-perspective debates.
12. **Storyteller:** Rich narrative generation, dramatic pacing, character voices.
13. **Cyberpunk AI:** Glitchy, hacker aesthetics, rebellious techno-futurist tone.
14. **Silent Operator:** Extreme minimalism—responds in under 10 words or pure tool executions.
15. **Custom Persona:** User-defined persona prompt injected directly into the system instruction.

---

## 6. Device and Android Integration

### 6.1 Android System Services & APIs
The app integrates with core Android operating system components via specialized services and permission bridges:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ANDROID OS FRAMEWORK                            │
├────────────────────┬────────────────────┬──────────────────────────────┤
│ Accessibility      │ MediaProjection    │ Telecom Framework            │
│ Service            │ Manager            │ & In-Call Service            │
├────────────────────┼────────────────────┼──────────────────────────────┤
│ Notification       │ AudioRecord &      │ Telecom Role Manager         │
│ Listener Service   │ AudioTrack         │ (Call Screening)             │
├────────────────────┼────────────────────┼──────────────────────────────┤
│ System Alert       │ AlarmManager &     │ SensorManager &              │
│ Window (Overlay)   │ JobScheduler       │ CameraManager (Watchman)     │
└────────────────────┴────────────────────┴──────────────────────────────┘
```

### 6.2 Foreground Services & System Lifecycle
1. **`LiveVoiceForegroundService`:**
   - Holds a high-priority foreground service with `microphone` and `mediaProjection` foreground service types.
   - Maintains a persistent status bar notification with quick mute and disconnect actions.
   - Acquires `WAKE_LOCK` to ensure continuous audio streaming when the screen is turned off.
2. **`JarvisAccessibilityService`:**
   - Declared with `accessibilityEventTypes="typeAllMask"` and `accessibilityFeedbackType="feedbackGeneric"`.
   - Flags: `flagDefault`, `flagRetrieveInteractiveWindows`, `flagIncludeNotImportantViews`, `flagReportViewIds`.
   - Dispatches gestures via `dispatchGesture(GestureDescription, GestureResultCallback, Handler)`.
3. **`JarvisNotificationListenerService`:**
   - Extends `NotificationListenerService` to intercept active notifications and dispatch WearableExtender/RemoteInput quick replies.
4. **`JarvisOverlayHudService`:**
   - Renders a floating window using `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`.
   - Features touch drag listeners with spring physics to dock against display edges.

---

## 7. Functional Requirements

### 7.1 User Actions & Triggers
- **Voice Initiation:** Hardware wake word ("Hey Jarvis"), floating HUD tap, quick settings tile, or dedicated in-app microphone action.
- **Text & Multimodal Queries:** Rich keyboard input with multi-line auto-expansion, image attachment picker, file picker, and camera trigger.
- **Workflow & Routine Triggers:** Manual one-tap execute, geofence enter/exit, specific time of day alarm, or notification condition.
- **Interruption (Barge-In):** User speaks while Jarvis is rendering audio playback; the system detects speech onset within 200ms and immediately silences playback.

### 7.2 UI State Machine Model
For any dynamic feature, the UI state machine conforms to the following states:

```
         ┌─────────────────────────┐
         │         IDLE            │
         └────────────┬────────────┘
                      │ User Action / Event
         ┌────────────▼────────────┐
         │        LOADING          │◄────────────────┐
         │ (Shimmer/Waveform/Pulse)│                 │ Retry
         └──────┬───────────┬──────┘                 │
                │           │                        │
     Success    │           │ Failure                │
┌───────────────▼──┐     ┌──▼────────────────────┐   │
│     SUCCESS      │     │        ERROR          │───┘
│  (Data / Result) │     │ (Banner / Actionable) │
└──────────────────┘     └───────────────────────┘
```

### 7.3 Error Handling & Recovery Strategies
- **Network Loss:** Offline visual indicator; local cache serving for memories and reminders; offline voice queueing.
- **API Rate Limiting (HTTP 429):** Automatic exponential backoff (1s, 2s, 4s, 8s) with visual cooldown countdown.
- **Permission Denials:** Non-intrusive permission rationale banner with direct "Open System Settings" deeplink.
- **Accessibility Service Interruption:** When the Android OS kills or restarts the accessibility service, the app detects disconnection, updates the status badge, and provides a one-tap re-enable dialog.

---

## 8. User Workflows

### 8.1 Hands-Free Autonomous Screen Action
```
User: "Hey Jarvis, open WhatsApp and send Rahul a message saying I will be 15 minutes late"
  │
  ▼
Offline Wake Word Engine triggers -> Broadcast to JarvisViewModel
  │
  ▼
Gemini 2.5 Flash analyzes intent -> Returns tool call: open_app("WhatsApp")
  │
  ▼
Accessibility Service launches WhatsApp -> Wait 600ms for UI render
  │
  ▼
Perception Engine captures screen -> Detects search bar or chat list
  │
  ▼
Tool call: type_text("Rahul") -> Click contact -> Type message
  │
  ▼
Sensitivity Check: Low-risk SMS/Chat -> Requires confirmation? 
If user configured confirmation: Display quick overlay: "Send message to Rahul?" [Send] [Cancel]
  │
  ▼
Execute click_screen(Send Button) -> Success callback -> Voice confirmation: "Message sent to Rahul, sir."
```

### 8.2 Real-Time Multimodal Screen Consultation
```
User starts Live Voice session -> Enables Screen Share
  │
  ▼
MediaProjection captures display at 1-2 FPS JPEG compression
  │
  ▼
Frames streamed over WebSocket alongside 16 kHz PCM microphone audio
  │
  ▼
User: "Jarvis, look at this graph on my screen. What was the net margin in Q3?"
  │
  ▼
Gemini Live API inspects incoming video frame + audio token simultaneously
  │
  ▼
Synthesizes 24 kHz audio response -> Streams directly to user headset within 450ms
```

### 8.3 Autonomous Call Screening
```
Incoming call from unknown number (+91 98765 43210)
  │
  ▼
Jarvis Telephony Manager intercepts call -> Mutes device ringer
  │
  ▼
Answers call silently in background -> Plays synthesized greeting:
"Hello, I am Jarvis, the AI assistant for this device. The owner is currently busy. Please state your name and purpose."
  │
  ▼
Caller speaks -> Streamed to STT -> Gemini evaluates intent and spam score
  │
  ▼
Screen displays real-time live transcript with action buttons:
[Take Call] [Reject Call] [Ask Caller to Hold] [Mark as Spam]
```

---

## 9. Backend and API Mapping

### 9.1 Gemini REST API Endpoints
- **Base URL:** `https://generativelanguage.googleapis.com/v1beta`
- **Models Endpoint:**
  - `GET /v1beta/models?key={API_KEY}` – Retrieves available models for dynamic feature availability.
- **Chat & Content Generation:**
  - `POST /v1beta/models/{model}:generateContent?key={API_KEY}` – Unary request.
  - `POST /v1beta/models/{model}:streamGenerateContent?alt=sse&key={API_KEY}` – Server-Sent Events (SSE) streaming request.
- **Request Headers:**
  - `Content-Type: application/json`
  - `x-goog-api-client: gl-kotlin/1.0.0 fire-android/1.0.0`
- **Payload Schema:**
  - `contents`: Array of role (`user`, `model`) and parts (`text`, `inline_data`).
  - `tools`: Array of function declarations matching `ToolRegistry.kt`.
  - `generationConfig`: `temperature` (0.7), `topP` (0.95), `maxOutputTokens` (2048), `responseMimeType`.
  - `systemInstruction`: Dynamic role directive injected with user preferences and persona instructions.

### 9.2 Gemini Multimodal Live API (Bidirectional WebSocket)
- **WebSocket URL:** `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key={API_KEY}`
- **Protocol Flow:**
  1. *Handshake & Setup Message (JSON):*
     ```json
     {
       "setup": {
         "model": "models/gemini-2.0-flash-exp",
         "generationConfig": {
           "responseModalities": ["AUDIO"],
           "speechConfig": {
             "voiceConfig": {
               "prebuiltVoiceConfig": {
                 "voiceName": "Puck"
               }
             }
           }
         }
       }
     }
     ```
  2. *Real-Time Audio Ingestion (Client to Server):*
     - Base64-encoded chunks of 16 kHz 16-bit linear PCM audio sent every 100ms.
  3. *Real-Time Video/Screen Ingestion (Client to Server):*
     - JPEG compressed screen frames sent every 500ms to 1000ms.
  4. *Real-Time Audio Playback (Server to Client):*
     - Server streams 24 kHz PCM audio packets rendered directly through an active `AudioTrack`.
  5. *Interruption Signal:*
     - When client-side VAD detects speech during playback, client sends clientContent cancel event and flushes the `AudioTrack` buffer.

---

## 10. Data Models and Storage

### 10.1 Room Database Entities (`JarvisDatabase.kt`)
The local SQLite database includes 8 relational entities:

#### 1. `chat_sessions`
- `sessionId` (String, Primary Key)
- `title` (String) – Auto-generated title
- `createdAt` (Long) – Timestamp
- `updatedAt` (Long) – Last message timestamp
- `pinned` (Boolean) – User pinned status
- `personalityMode` (String) – Associated persona

#### 2. `chat_messages`
- `messageId` (String, Primary Key)
- `sessionId` (String, Foreign Key -> `chat_sessions.sessionId` ON DELETE CASCADE)
- `sender` (String) – `USER` or `JARVIS`
- `content` (String) – Markdown text
- `timestamp` (Long)
- `attachmentsJson` (String?) – Serialized list of file/image URIs
- `toolCallsJson` (String?) – Executed tools and results

#### 3. `memories`
- `id` (Long, Primary Key AutoGenerate)
- `subject` (String)
- `predicate` (String)
- `objectValue` (String)
- `category` (String) – `PERSONAL_PREFERENCE`, `FACT`, `RELATIONSHIP`, `HEALTH`, `WORK`, `EMERGENCY`
- `tier` (Int) – Memory tier (1 to 5)
- `importance` (Int) – `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- `confidence` (Float) – 0.0 to 1.0
- `createdAt` (Long)
- `lastAccessedAt` (Long)

#### 4. `working_memory_tasks`
- `taskId` (String, Primary Key)
- `goalDescription` (String)
- `currentStep` (Int)
- `totalSteps` (Int)
- `status` (String) – `PENDING`, `IN_PROGRESS`, `PAUSED`, `COMPLETED`, `FAILED`
- `scratchpadJson` (String) – Intermediate key-value state

#### 5. `episodic_memories`
- `eventId` (String, Primary Key)
- `summary` (String)
- `location` (String?)
- `timestamp` (Long)
- `emotionalValence` (Float) – -1.0 to 1.0

#### 6. `semantic_triples`
- `tripleId` (String, Primary Key)
- `entityA` (String)
- `relation` (String)
- `entityB` (String)
- `weight` (Float)

#### 7. `reminders`
- `reminderId` (Long, Primary Key AutoGenerate)
- `title` (String)
- `dueTimestamp` (Long)
- `isCompleted` (Boolean)
- `priority` (String) – `LOW`, `NORMAL`, `HIGH`, `URGENT`
- `repeatInterval` (String?)

#### 8. `workflows`
- `workflowId` (String, Primary Key)
- `title` (String)
- `description` (String)
- `triggerType` (String) – `MANUAL`, `SCHEDULED`, `BLUETOOTH_CONNECT`, `NOTIFICATION`
- `stepsJson` (String) – Serialized DAG of action steps
- `isEnabled` (Boolean)

#### 9. `notification_logs`
- `id` (Long, Primary Key AutoGenerate)
- `packageName` (String)
- `sender` (String)
- `messageContent` (String)
- `summary` (String?)
- `timestamp` (Long)
- `wasReplied` (Boolean)

### 10.2 Jetpack DataStore Preferences Keys
- `user_api_key`: Encrypted Gemini API key (fallback if not supplied by build environment).
- `selected_theme_palette`: Selected visual palette (`OCEAN_BLUE`, `ROYAL_PURPLE`, `AQUA_MINT`).
- `selected_dark_mode_option`: Display mode (`LIGHT`, `DARK`, `SYSTEM_DEFAULT`).
- `personality_mode`: Current persona identifier (`TACTICAL_JARVIS`, `DOST`, `ROMANTIC`, etc.).
- `gemini_voice_option`: Selected voice persona (`Puck`, `Charon`, `Kore`, `Fenrir`, `Aoede`, etc.).
- `wake_word_enabled`: Boolean flag for offline keyword listening.
- `haptic_feedback_enabled`: Boolean flag for touch and action vibration.
- `barge_in_sensitivity`: Low, Medium, High sensitivity threshold.
- `driving_mode_auto_launch`: Auto-launch on Bluetooth vehicle connection.
- `watchman_sensitivity`: Motion sensitivity threshold for security guardian.

---

## 11. Permissions and System Dependencies

### 11.1 Android Manifest Permissions Audit
The application requests the following permissions, categorized by security level:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        INSTALL-TIME PERMISSIONS                        │
├────────────────────────────────────────────────────────────────────────┤
│ android.permission.INTERNET                                            │
│ android.permission.ACCESS_NETWORK_STATE                                │
│ android.permission.VIBRATE                                             │
│ android.permission.WAKE_LOCK                                           │
│ android.permission.FOREGROUND_SERVICE                                  │
│ android.permission.FOREGROUND_SERVICE_MICROPHONE                       │
│ android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION                 │
│ android.permission.RECEIVE_BOOT_COMPLETED                              │
│ android.permission.QUERY_ALL_PACKAGES                                  │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│                      DANGEROUS RUNTIME PERMISSIONS                     │
├────────────────────────────────────────────────────────────────────────┤
│ android.permission.RECORD_AUDIO (Voice streaming, live mic)            │
│ android.permission.CAMERA (Watchman security, document scanning)       │
│ android.permission.READ_CONTACTS (Address book query tool)              │
│ android.permission.CALL_PHONE (Direct telephony dialing)               │
│ android.permission.SEND_SMS (Autonomous text messaging)                │
│ android.permission.READ_SMS (SMS verification, context reading)        │
│ android.permission.ACCESS_FINE_LOCATION (Navigation & Driving Mode)    │
│ android.permission.ACCESS_COARSE_LOCATION (Weather context)            │
│ android.permission.POST_NOTIFICATIONS (Android 13+ background alerts)  │
│ android.permission.BLUETOOTH_CONNECT (Driving Mode vehicle detection)  │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│                        SPECIAL APP ACCESS GRANTS                       │
├────────────────────────────────────────────────────────────────────────┤
│ BIND_ACCESSIBILITY_SERVICE (Autonomous Screen Touch & Gesture CUA)     │
│ BIND_NOTIFICATION_LISTENER_SERVICE (System Notification Mirroring)     │
│ SYSTEM_ALERT_WINDOW (Floating HUD Overlay Bubble)                      │
│ MANAGE_EXTERNAL_STORAGE (Optional power file tool)                     │
│ Telecom Call Screening Role (Spam and Call Filtering)                  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Theme and Personalization Requirements

### 12.1 The Three Curated Color Palettes
The user has approved three distinct, modern color palettes. Stitch must support dynamic switching across all three:

#### 1. Ocean Blue Palette
- **Primary:** `#0B1F3B` (Deep Midnight Navy)
- **Accent:** `#2563EB` (Vibrant Tech Cobalt Blue)
- **Secondary:** `#60A5FA` (Soft Sky Blue)
- **Surface:** `#DBEAFE` (Subtle Ice Blue)
- **Background:** `#F0F7FF` (Crisp Light Air Blue)
- **Dark Mode Variants:**
  - Background: `#060D17`
  - Surface: `#0D1E36`
  - Primary Accent: `#3B82F6`

#### 2. Royal Purple Palette
- **Primary:** `#5B21B6` (Deep Majestic Purple)
- **Accent:** `#8B5CF6` (Vibrant Violet Electric Accent)
- **Secondary:** `#C7B3FF` (Soft Lavender Pastel)
- **Surface:** `#EDE9FE` (Subtle Lilac Mist)
- **Background:** `#FAF9FF` (Clean Pearl White)
- **Dark Mode Variants:**
  - Background: `#0E071A`
  - Surface: `#1D1233`
  - Primary Accent: `#A78BFA`

#### 3. Aqua / Mint Palette
- **Primary:** `#004D40` (Deep Forest Teal)
- **Accent:** `#00BFA5` (Vibrant Mint Emerald)
- **Secondary:** `#80CBC4` (Soft Seafoam)
- **Surface:** `#E0F2F1` (Subtle Mint Mist)
- **Background:** `#F2FCFB` (Ultra-Clean Frost Aqua)
- **Dark Mode Variants:**
  - Background: `#041412`
  - Surface: `#082622`
  - Primary Accent: `#1DE9B6`

### 12.2 Display Mode Support
- **Light Mode:** High contrast, bright surfaces, clean shadows.
- **Dark Mode:** Deep OLED-friendly blacks/dark tones, illuminated glowing accents, reduced eye strain.
- **System Default:** Automatically tracks Android OS day/night mode transitions.

### 12.3 Voice Personas & Languages
- **8 Prebuilt Gemini Voice Personas:** `Puck` (Energetic, natural male), `Charon` (Deep, calm male), `Kore` (Warm, soothing female), `Fenrir` (Authoritative male), `Aoede` (Expressive, lyrical female), `Zephyr` (Gentle female), `Leda` (Confident female), `Orpheus` (Melodic male).
- **Supported Dialects:** Bilingual Hinglish (natural fusion of Hindi and English colloquial phrasing), Pure Standard Hindi, International English.

---

## 13. Implementation Status and Functional Gaps

### 13.1 Fully Implemented Capabilities (100% Verified in Code)
- Real-Time Gemini Live Audio/Video WebSocket Bidi connection (`GeminiLiveVoiceManager.kt`).
- Multi-Turn SSE streaming chat with dynamic system instructions (`GeminiApiService.kt`).
- All 37 Tool calling schemas and execution dispatchers (`ToolRegistry.kt`).
- 10-Engine Autonomous Mobile Agent (CUA) via Accessibility gestures (`AgenticMobileEngine.kt`, `JarvisAccessibilityService.kt`).
- 5-Tier Memory Room database with DAOs and persistence (`JarvisDatabase.kt`, `MemoryDao.kt`).
- Telephony Call Screening listener and in-call broadcast receiver (`JarvisTelephonyManager.kt`).
- Notification Listener and RemoteInput reply dispatcher (`JarvisNotificationListenerService.kt`).
- Driving mode motion and Bluetooth triggers (`JarvisDrivingModeManager.kt`).
- Watchman anti-theft camera/accelerometer monitoring (`JarvisWatchmanManager.kt`).
- Voice Note recorder and PCM audio exporter (`JarvisVoiceNoteManager.kt`).
- Dynamic Live Wallpaper service (`JarvisWallpaperService.kt`).
- Termux command IPC bridge (`JarvisTermuxBridge.kt`).
- Floating HUD Overlay Service with drag bounds (`JarvisOverlayHudService.kt`).

### 13.2 Functional Gaps (Features Present in Backend but Constrained by Current UI)
- **Visual Memory Explorer:** While the 5 memory tiers are stored in SQLite, the user currently lacks an intuitive graph or card-based UI to search, edit, inspect, and prune memory triples.
- **Agentic Step-by-Step HUD:** The CUA autonomous execution engine functions in the background, but users need a clean, non-intrusive floating progress bar and highlight preview to see what Jarvis is doing on their screen.
- **Call Screening Live Dashboard:** The telephony manager intercepts calls, but a dedicated, clean incoming call screening card with live transcription needs to be surfaced in the UI.
- **Workflow Automation Builder:** The `workflows` table supports multi-step DAG actions, but there is currently no visual routine builder for users to compose routines without typing JSON.
- **Appearance & Theme Switcher:** The color palettes and theme state exist in DataStore, but require an elegant settings UI with live previews and one-tap switching.

---

## 14. Technical Constraints and Limitations

1. **Android Background Limits:** Android 14+ restricts background service launches. All continuous audio recording and screen capturing MUST run inside an active foreground service with appropriate foreground service type flags (`microphone`, `mediaProjection`).
2. **MediaProjection Token Lifecycle:** Screen projection requires an initial system dialog consent. The returned `Intent` token cannot be cached across device reboots and must be requested once per user session.
3. **Accessibility Service Resilience:** If the user or battery saver disables Accessibility, the agent cannot execute physical taps or reads. The UI must detect this state gracefully and guide the user.
4. **Touch Target Accessibility:** All interactive elements must strictly adhere to Android accessibility standards with a minimum touch target size of 48dp x 48dp.
5. **No Emulators/ADB in Execution Environment:** All testing is performed via local JVM Robolectric tests and incremental builds.
6. **API Key Security:** API keys must never be hardcoded into source files and are injected via build-time BuildConfig or secure DataStore settings.

---

## 15. Google Stitch Design Instructions

### 15.1 Architectural Mandate for Google Stitch
> **IMPORTANT DIRECTIVE FOR GOOGLE STITCH:**
> You have **complete, unrestricted creative freedom** over the frontend user experience. You are **NOT** bound to any existing screen count, navigation structure, tab layout, or visual styling.
> 
> You are encouraged to imagine a **brand-new, next-generation UI architecture** that matches the power of an autonomous AI operating system. 
> 
> **What You Decide Freely:**
> - How many screens or surfaces exist.
> - Whether the interface uses a floating dock, bottom sheets, full-screen canvas, radial controls, split panels, or dynamic spatial cards.
> - How features are grouped, nested, or elevated.
> - The visual identity, typography scale, icon styling, motion language, micro-interactions, and sound design cues.
> - How the three curated color palettes (Ocean Blue, Royal Purple, Aqua Mint) are applied across surfaces, cards, buttons, visualizers, and typography.
>
> **The ONLY Mandatory Requirement:**
> - **100% Preservation of Actual Application Functionality.** Every single capability listed in Section 4, 5, and 6 MUST be accessible, operable, and clearly integrated into your new design.

### 15.2 Functional Integration Checklist for Stitch
When designing the new interface, ensure your screens and components provide affordances for:
- [ ] Instant voice initiation with dynamic waveform / orb visualizer reacting to audio levels.
- [ ] Multimodal chat with streaming text, markdown formatting, code copy, and media attachments.
- [ ] Real-time screen perception indicator (showing user when Jarvis is watching the screen).
- [ ] Autonomous mobile control HUD with real-time step status and an emergency "Stop Action" button.
- [ ] Interactive 5-Tier Memory viewer (view, search, add, delete, and filter memories).
- [ ] Live Call Screening incoming card with real-time transcription and Accept/Reject triggers.
- [ ] Smart Notification aggregation feed with AI summaries and one-tap quick reply drafting.
- [ ] Driving Mode low-distraction dashboard with speedometer, music controls, and navigation.
- [ ] Watchman security control panel with arm/disarm toggle and intruder snapshot gallery.
- [ ] Document / PDF / OCR scanning and conversational query viewer.
- [ ] Routine / Workflow automation creator.
- [ ] Comprehensive Appearance & Settings panel (3 theme palettes, Light/Dark/System Default, 15 personality modes, 8 voice options).

---

## 16. Final Functional Acceptance Criteria

To achieve sign-off and verify complete functionality preservation, the newly designed frontend must fulfill the following acceptance criteria:

1. **AI Chat & Streaming:** User can initiate a chat session, type queries, attach images, and receive real-time streaming SSE tokens without truncation.
2. **Live Voice Bidi Session:** User can tap to enter live voice mode, speak naturally, hear 24 kHz synthesized voice responses, interrupt the assistant mid-sentence (barge-in), and view live audio waveforms.
3. **Autonomous Computer Use (CUA):** User can request a cross-app task (e.g., "Open YouTube and search for Lo-Fi music"), see the agentic HUD overlay activate, witness the accessibility gesture execute, and receive completion confirmation.
4. **Memory Retention:** Facts taught to Jarvis (e.g., "My sister's name is Priya") are stored in the Room database under Semantic/Relational memory and accurately recalled in subsequent queries.
5. **Call Screening & System Telephony:** Incoming calls trigger the screening evaluator, displaying live caller transcriptions and response actions.
6. **Notification Management:** Active system notifications are displayed, summarized, and replyable via voice or tap.
7. **Theming & Display Modes:** User can switch between Ocean Blue, Royal Purple, and Aqua Mint palettes, as well as Light, Dark, and System Default modes, with immediate app-wide visual updates that persist across app restarts.
8. **Permissions & System Resilience:** Missing permissions or inactive accessibility services present clear, actionable prompts that guide the user to system settings without crashing the app.
9. **Build & Test Integrity:** The application compiles cleanly with zero syntax errors, and all unit/Robolectric test suites pass with 100% success.

---
*End of Master Specification Document.*
*Prepared for Google Stitch Design Handoff.*
