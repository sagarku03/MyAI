# JARVIS AI — Next-Gen Powerhouse Assistant Expansion Plan

Transform JARVIS AI from a single-command voice assistant into a proactive, multi-step personal operating system capable of chaining complex phone actions from a single voice phrase, translating or summarizing any screen in Hindi/Hinglish/English, reacting automatically to device state changes, and delivering personalized voice briefings.

## User Review & Critical Decisions

> [!IMPORTANT]
> Since the clarification questions were skipped, this plan uses the recommended defaults below so you get the highest-impact upgrades immediately while keeping all 12 existing screens, routes, and voice features intact.

- **Selected Powerhouse Feature Suite (Recommended Default)**:
  1. **Multi-Step Voice Routines & Preset Macros** (*"Jarvis, Good Morning mode chalu karo"*, *"Driving mode on karo"*, *"Study focus mode"*, *"Goodnight mode"*).
  2. **Live Screen Translation, Summarization & Smart Clipboard Assistant** (*"Jarvis, screen ko Hindi me translate karo"*, *"Is page ka summary batao"*, and 1-tap smart clipboard actions).
  3. **Proactive Device Event Triggers** (spoken/visual alerts & automated actions when Battery drops below 20% or Charger connects).
  4. **Voice Daily Briefing Engine** (*"Jarvis, aaj ki briefing do"* — combines live battery/network status, saved memories, active reminders, and pending tasks into a spoken summary).
- **UI Placement (Recommended Default)**:
  - **Compact Power-Action Strip on the Home Dashboard** for 1-tap Daily Briefing, Screen Translate, and Quick Macros, plus **Preset Routine Templates & Multi-Step Builder** inside the existing Workflow & Automation hub (preserving all 12 navigation routes).

---

## 1. Overview & Core Concept

- **What It Does**:
  - Lets users trigger **multi-step phone routines** by voice or a single tap (e.g., setting volume, toggling system controls, speaking a status briefing, and launching a target app in sequence).
  - Reads the active foreground screen via Accessibility or clipboard text and performs **instant Hindi / Hinglish / English translation and bullet-point summarization**.
  - Monitors **real-time battery & charging state changes** to proactively assist the user (e.g., offering to lower brightness and turn off torch when battery is low).
  - Delivers an **executive Daily Briefing** in natural Hinglish or English covering device health, saved personal memories, and active workflows.
- **Target Audience / Persona**: Power users who want a true Iron-Man-style JARVIS companion that understands English, Hindi, and Hinglish and automates multi-step phone tasks hands-free.
- **Key Value**: Eliminates repetitive manual taps across apps by combining Accessibility screen intelligence, Android system controls, local persistence, and Gemini reasoning into seamless 1-command workflows.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **1-Command Voice Macro Execution**: User says *"Jarvis, Driving Mode chalu karo"* (or taps the **Driving Mode** chip on the Home Dashboard) $\rightarrow$ JARVIS sets media volume to 80%, checks Bluetooth state, announces battery level, and launches Google Maps/Navigation with a live step-by-step progress card.
  2. **On-Screen Live Translation & Summary**: User is reading an article or message in Chrome/WhatsApp and says *"Jarvis, screen ko Hindi me translate karo"* or *"Screen summarize karo"* (or taps **Translate Screen** on the dashboard) $\rightarrow$ JARVIS captures the visible screen text (or clipboard content), translates/summarizes it, speaks the result aloud, and displays a copyable card in chat.
  3. **Executive Daily Briefing**: User taps **Daily Briefing** on the Home Dashboard or says *"Jarvis, give me my daily briefing"* $\rightarrow$ JARVIS synthesizes current time greeting, battery & temperature health, connectivity state, top saved memories, and active workflows into a crisp spoken and visual briefing.
  4. **Proactive Battery & Charging Assistant**: When battery drops below 20% unplugged, JARVIS displays a non-intrusive HUD alert with a 1-tap **Power Saver Routine** button (dimming brightness & disabling torch).
- **Visual Identity & Theme**:
  - **Aesthetic Direction**: Futuristic Tactical Cyber-HUD matching the existing JARVIS dark-glass aesthetic.
  - **Color Palette & Mood**: Deep obsidian-slate surfaces (`#0B1120`, `#1E293B`), electric cyan (`JarvisSky` `#38BDF8`) for active intelligence, neon emerald (`JarvisGreen` `#22C55E`) for verified execution steps, and deep violet (`JarvisPurpleDark`) accents.
  - **Typography & Hierarchy**: Bold geometric headers with high-contrast monospace telemetry badges (`11.sp`–`12.sp`) for routine execution steps and status indicators.
  - **Component Styling & Layout**: Semi-transparent glassmorphic cards with `1.dp` cyan/emerald glowing borders, horizontal quick-action pills with minimum `48.dp` touch targets, and step-by-step execution badges.
- **Interactive Feedback & Motion**:
  - Smooth `AnimatedVisibility` expansion for multi-step macro execution logs showing each step's real-time verification status (`VERIFIED`, `REQUIRES_PERMISSION`, `COMPLETED`).

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Deterministic Local Execution First + Gemini Intelligence Where Needed**
  - *Chosen Approach*: Multi-step system macros and daily device telemetry run 100% locally and instantaneously using the verified Android System Control Engine, while screen translation and smart summarization use Gemini with a structured local fallback when offline.
  - *Why*: Ensures zero latency and 100% reliability for phone hardware controls even without internet or API quota, while unlocking rich natural-language translation when online.
  - *Alternatives Considered*: Routing every macro step through LLM tool-calling, which adds network latency and consumes free-tier API quota unnecessarily.
- **Decision 2: Preset Smart Routines + Custom Multi-Action JSON Chaining in Existing Database**
  - *Chosen Approach*: Store multi-step routine definitions using the existing `WorkflowEntity` (`actionsJson` array) and seed 4 flagship preset routines (*Good Morning*, *Driving Mode*, *Study Focus*, *Power Saver*) if none exist.
  - *Why*: Zero destructive database changes, preserves all existing user data and Room migrations (`v1..v4`), and immediately populates the Automation hub with ready-to-use routines.

---

## 4. Technical Architecture & Data Strategy

- **Architecture & Component Diagram**:

```
┌───────────────────────────────────────────────────────────────────────────┐
│                        JARVIS AI UI LAYER (Compose)                       │
│  ┌─────────────────────────┐  ┌────────────────────┐  ┌────────────────┐  │
│  │   HomeDashboardScreen   │  │   WorkflowScreen   │  │ TextChatScreen │  │
│  │ • Daily Briefing Pill   │  │ • Preset Templates │  │ • Translation  │  │
│  │ • Quick Macro Chips     │  │ • Multi-Step Log   │  │   & Summary    │  │
│  │ • Clipboard & Translate │  │ • Proactive Rules  │  │   Result Cards │  │
│  └────────────┬────────────┘  └─────────┬──────────┘  └───────┬────────┘  │
└───────────────┼─────────────────────────┼─────────────────────┼───────────┘
                │                         │                     │
                ▼                         ▼                     ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                      VIEWMODEL & ORCHESTRATION LAYER                      │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │                           JarvisViewModel                           │  │
│  │ • executeVoiceRoutine()          • generateDailyBriefing()          │  │
│  │ • translateOrSummarizeScreen()   • handleClipboardSmartAction()     │  │
│  └────────────┬─────────────────────────┬─────────────────────┬────────┘  │
└───────────────┼─────────────────────────┼─────────────────────┼───────────┘
                │                         │                     │
                ▼                         ▼                     ▼
┌───────────────────────────┐ ┌───────────────────────┐ ┌───────────────────┐
│   IntentRouter & Action   │ │ Screen & Clipboard    │ │ Workflow & Memory │
│   Verification Engine     │ │ Intelligence Engine   │ │   Repositories    │
│ • VOICE_ROUTINE routing   │ │ • Accessibility Read  │ │ • Preset Macros   │
│ • SCREEN_TRANSLATE route  │ │ • Clipboard Inspector │ │ • Sequential Step │
│ • DAILY_BRIEFING route    │ │ • Hindi/Hinglish/Eng  │ │   Runner + Logs   │
└───────────────────────────┘ └───────────────────────┘ └───────────────────┘
```

- **Data Model & State**:
  - **Multi-Step Routine Model**: Uses `WorkflowEntity` with a JSON array of `WorkflowAction` steps (`SET_VOLUME`, `SET_BRIGHTNESS`, `BLUETOOTH_CONTROL`, `FLASHLIGHT`, `OPEN_APP`, `SPEAK_BRIEFING`, `SCROLL_SCREEN`) so a single routine can execute 2–6 sequential verified actions.
  - **Screen & Clipboard Intelligence State**: Captures visible text from `JarvisAccessibilityService.captureScreenSnapshot()` (or falls back to `ClipboardManager.primaryClip`) and produces structured translation/summary output in the user's selected language (Hindi, Hinglish, or English).
- **Interactive Component & State Mapping**:
  - **Home Dashboard Power Bar**:
    - *Daily Briefing Chip* (`home_daily_briefing_chip`): Generates and speaks a live status + memory + workflow briefing and logs it to the assistant feed.
    - *Translate Screen Chip* (`home_translate_screen_chip`): Reads the last active screen or clipboard text, translates it into Hindi/Hinglish, and opens the result in Chat.
    - *Quick Routine Chips* (*Good Morning*, *Driving Mode*, *Power Saver*): Immediately runs the multi-step macro and updates the status banner with step verification results.
  - **Workflow Screen Preset Loader**:
    - One-tap **"Load Smart Presets"** button (`workflow_load_presets_button`) adds the 4 flagship multi-step routines so users can run or customize them immediately.
  - **Voice Command Integration (`IntentRouter` + `PhoneActionManager`)**:
    - Recognizes natural English, Hindi, and Hinglish commands for routines (*"Good morning mode chalu karo"*, *"Driving mode on karo"*, *"Power saver mode"*), daily briefing (*"Aaj ki briefing do"*, *"Daily briefing"*), and screen translation/summary (*"Screen ko Hindi me translate karo"*, *"Clipboard summarize karo"*).
