# 🌌 JARVIS AI 3.0: COMPLETE GROUND-UP UI/UX REDESIGN SPECIFICATION
## "Neural Spatial OS" — Clean Luxury Cyber-Minimalism, Ergonomic Architecture & Centralized Themes System

> **Document Type:** Production-Grade UI/UX Redesign Specification & Architecture Blueprint  
> **Status:** Final Improved Master Plan (Post-Review & Appearance System Integration)  
> **Language:** Hinglish (Technical, Clear & Practical)  
> **Target Framework:** Android (Jetpack Compose, Material 3, Dynamic Theme, Hardware-Accelerated Canvas)  
> **Core Directives:** No Overcrowding, No Complicated Radial Docks, No Excessive Neon/Glass Gimmicks. 100% Functional, Clean, Ergonomic, One-Hand Friendly.  
> **Safety & Integrity:** Zero code modifications in this turn. All existing backend engines, ViewModels, and services 100% preserved.

---

## 📑 TABLE OF CONTENTS
1. [Design Philosophy: Clean Cyber-Minimalism vs Purana Gimmick UI](#1-design-philosophy-clean-cyber-minimalism)
2. [Dedicated Appearance & Centralized Themes System](#2-dedicated-appearance--centralized-themes-system)
   - 2.1 [Reference Palettes & Theme Presets (Ocean Blue, Royal Purple, Aqua Mint)](#21-reference-palettes--theme-presets)
   - 2.2 [Display Modes: Light, Dark & System Default](#22-display-modes-light-dark--system-default)
   - 2.3 [Global Cascading Theme Architecture (How Every Component Adapts)](#23-global-cascading-theme-architecture)
   - 2.4 [Settings Live Preview & Instant Apply Mechanism](#24-settings-live-preview--instant-apply-mechanism)
   - 2.5 [Persistent Storage via Jetpack DataStore](#25-persistent-storage-via-jetpack-datastore)
   - 2.6 [Material 3 Dynamic Color (Android 12+ Monet) Coexistence](#26-material-3-dynamic-color-android-12-monet-coexistence)
   - 2.7 [Centralized Compose Token Architecture (`JarvisTheme`)](#27-centralized-compose-token-architecture-jarvistheme)
3. [Visual Identity, Surface Physics & Typography Standards](#3-visual-identity-surface-physics--typography-standards)
4. [Navigation Architecture: The Ergonomic Floating Capsule Dock](#4-navigation-architecture-the-ergonomic-floating-capsule-dock)
5. [Screen 1: Home Dashboard (Minimalist Spatial Cockpit)](#5-screen-1-home-dashboard-minimalist-spatial-cockpit)
6. [Screen 2: Live Voice Companion Call Studio](#6-screen-2-live-voice-companion-call-studio)
7. [Screen 3: Neural Chat Studio & Multimodal Ingestion](#7-screen-3-neural-chat-studio--multimodal-ingestion)
8. [Screen 4: Autonomous CUA Operator Deck (10 AI Engines & Device Control)](#8-screen-4-autonomous-cua-operator-deck)
9. [Screen 5: 5-Tier Synaptic Memory Vault](#9-screen-5-5-tier-synaptic-memory-vault)
10. [Screen 6: Agent Multi-Studio Hub (Termux, Watchman, Driving, Macros)](#10-screen-6-agent-multi-studio-hub)
11. [Screen 7: Safe Safari Web Reader & DOM Engine](#11-screen-7-safe-safari-web-reader--dom-engine)
12. [Screen 8: Unified Settings Hub, Keystore Vault & Persona Studio](#12-screen-8-unified-settings-hub--persona-studio)
13. [One-Hand Reachability & Ergonomic Layout Zones](#13-one-hand-reachability--ergonomic-layout-zones)
14. [Existing Backend & ViewModel Accurate Mapping Table](#14-existing-backend--viewmodel-accurate-mapping-table)
15. [Performance, 120Hz Smoothness & Accessibility Compliance](#15-performance-120hz-smoothness--accessibility)
16. [Phase-by-Phase Replacement & Safe Migration Roadmap](#16-phase-by-phase-replacement--safe-migration-roadmap)

---

## 1. Design Philosophy: Clean Cyber-Minimalism

### 1.1 Purane Design Ki Kamiyan & Review Findings
1. **Overcrowded Home Screen:** Purane home screen par ek saath bada Orb, multiple command boxes, 6 horizontal chips, 4 bento cards, control strips, aur continuous logs ek hi viewport mein thuns diye gaye the. User confuse ho jata tha ki kahan tap karein.
2. **Complicated Radial Navigation:** Curved circular docks ya radial wheels dekhne mein sci-fi lagte hain, lekin real thumb interaction mein mis-taps aur gesture conflicts paida karte hain.
3. **Excessive Glassmorphism & Neon Glare:** Bahut zyada blur, continuous 4-color animated neon borders, aur glowing rainbow backgrounds text legibility ko kharab karte hain aur OLED battery drain karte hain.
4. **Scattered Feature Architecture:** 14 alag-alag screen destinations users ke mental model ko tod dete the.

### 1.2 Naye "Neural Spatial OS" Ka Solution
- **Calm When Idle, Alive When Active:** Jab assistant idle ho, screen bilkul shaant, clean, aur elegant dikhai dega. Jab user baat karega ya AI koi action lega, tabhi dynamic micro-animations trigger hongi.
- **4 Unified Spatial Zones:** Pura Jarvis ecosystem **4 streamlined core zones** mein divide hoga:
  1. **Zone 1: Cockpit (Home)** — Assistant Status, Single Omni-Command Bar, Contextual Glance Cards.
  2. **Zone 2: Dialogue** — Unified Live Voice Call Stage & AI Text Chat Studio.
  3. **Zone 3: Operator** — Autonomous CUA Controls, Screen Perception, Web DOM Agent, Macros & Termux.
  4. **Zone 4: Knowledge** — 5-Tier Memory Vault, Timeline, Document AI, Settings & Keystore Vault.
- **Satin Glass Surfaces:** Heavy 30px blur ke bajaye refined **Satin Surfaces** (`0.85 - 0.90` opacity and crisp 0.75dp subtle borders) use honge jo ultra-premium look dete hain bina readability ya framerate ko hurt kiye.

---

## 2. Dedicated Appearance & Centralized Themes System

User ki requirement ke mutabiq app mein ek **Centralized Appearance & Themes Engine** integrate kiya gaya hai. Is engine ke tahat app ke kisi bhi screen ya component mein hardcoded colors nahi honge, balki saara UI ek dynamic reactive theme state se drive hoga.

### 2.1 Reference Palettes & Theme Presets

Attached reference images (@uiuxaadi design specifications) ke verified color tokens ke hisab se **3 Distinct Luxury Theme Presets** define kiye gaye hain. Har preset ka Light Mode aur Dark Mode dono fully mapped hain:

---

#### 🌊 PRESET 1: OCEAN BLUE (Ref: Image 2)
Deep maritime elegance with crisp arctic blue accents. Professional, calm and authoritative.

| Token Role | Light Mode Hex | Dark Mode Hex | Usage in UI |
| :--- | :--- | :--- | :--- |
| **Canvas Background** | `#F0F7FF` (Icy Blue) | `#070D18` (Deep Abyss) | Main scaffold canvas, zero-glare backdrop |
| **Surface (Cards/Sheets)**| `#DBEAFE` (Soft Marine) | `#0F1A2E` (Satin Navy) | Bento cards, dialogue bubbles, dialog modals |
| **Surface Elevated** | `#BFDBFE` (Pale Azure) | `#172642` (Elevated Navy) | Search bars, input textfields, active rows |
| **Secondary / Container**| `#60A5FA` (Sky Azure) | `#2563EB` (Cobalt Royal) | Inactive pills, secondary badges, borders |
| **Accent / CTA** | `#2563EB` (Electric Blue) | `#38BDF8` (Bright Cyan) | Primary action pills, voice waveforms, sliders |
| **Primary Brand** | `#0B1F3B` (Midnight Navy)| `#60A5FA` (Luminous Sky) | Brand title, active icon fill, glowing core |
| **Text Primary** | `#0B1F3B` (Navy Black) | `#F8FAFC` (Pure White) | 100% crisp typography, headings, chat text |
| **Text Secondary** | `#1E3A8A` (Deep Slate) | `#94A3B8` (Cool Silver) | Subtitles, timestamps, telemetry labels |
| **Border Hairline** | `#93C5FD` (20% Opacity) | `#1E293B` (Hairline Navy) | Specular 1px card borders & divider lines |

---

#### 🔮 PRESET 2: ROYAL PURPLE (Ref: Image 3)
Ultraviolet cyberpunk luxury with royal amethyst accents. Futuristic, visionary and intimate.

| Token Role | Light Mode Hex | Dark Mode Hex | Usage in UI |
| :--- | :--- | :--- | :--- |
| **Canvas Background** | `#FAF9FF` (Lavender Ice) | `#080711` (Obsidian Void) | Pure dark OLED / clean lavender backdrop |
| **Surface (Cards/Sheets)**| `#EDE9FE` (Soft Orchid) | `#121024` (Satin Amethyst) | Bento cards, dialogue bubbles, dialog modals |
| **Surface Elevated** | `#DDD6FE` (Pale Lilac) | `#1C1836` (Elevated Velvet) | Search bars, input textfields, active rows |
| **Secondary / Container**| `#C7B3FF` (Pastel Violet)| `#5B21B6` (Deep Violet) | Inactive pills, secondary badges, borders |
| **Accent / CTA** | `#8B5CF6` (Ultraviolet) | `#A855F7` (Electric Orchid)| Primary action pills, voice waveforms, sliders |
| **Primary Brand** | `#5B21B6` (Royal Amethyst)| `#C084FC` (Luminous Orchid)| Brand title, active icon fill, glowing core |
| **Text Primary** | `#3B0764` (Deep Plum) | `#F8FAFC` (Pure White) | 100% crisp typography, headings, chat text |
| **Text Secondary** | `#6B21A8` (Muted Purple)| `#CBD5E1` (Cool Silver) | Subtitles, timestamps, telemetry labels |
| **Border Hairline** | `#C4B5FD` (20% Opacity) | `#2E2254` (Hairline Plum) | Specular 1px card borders & divider lines |

---

#### 🍃 PRESET 3: AQUA MINT / CYBER TEAL (Ref: Image 1 - Verified)
Verified directly from the 3rd reference card showing `Surface: #D6FFFF` (`srgb(214,255,255)`) and `Background: #ECFEFE` (`srgb(236,254,254)`). Fresh, energetic, futuristic biocentric intelligence.

| Token Role | Light Mode Hex | Dark Mode Hex | Usage in UI |
| :--- | :--- | :--- | :--- |
| **Canvas Background** | `#ECFEFE` (Ref Sampled) | `#040D0D` (Obsidian Teal) | Fresh biocentric canvas / deep dark matrix |
| **Surface (Cards/Sheets)**| `#D6FFFF` (Ref Sampled) | `#0A1C1C` (Satin Pine) | Bento cards, dialogue bubbles, dialog modals |
| **Surface Elevated** | `#CCFBF1` (Soft Seafoam) | `#112E2E` (Elevated Aqua) | Search bars, input textfields, active rows |
| **Secondary / Container**| `#5EEAD4` (Teal Aqua) | `#0F766E` (Deep Teal) | Inactive pills, secondary badges, borders |
| **Accent / CTA** | `#0D9488` (Vibrant Mint) | `#14B8A6` (Electric Emerald)| Primary action pills, voice waveforms, sliders |
| **Primary Brand** | `#042F2E` (Pine Shadow) | `#2DD4BF` (Luminous Mint) | Brand title, active icon fill, glowing core |
| **Text Primary** | `#042F2E` (Forest Black) | `#F8FAFC` (Pure White) | 100% crisp typography, headings, chat text |
| **Text Secondary** | `#115E59` (Slate Teal) | `#94A3B8` (Cool Silver) | Subtitles, timestamps, telemetry labels |
| **Border Hairline** | `#99F6E4` (20% Opacity) | `#134E4A` (Hairline Pine) | Specular 1px card borders & divider lines |

---

### 2.2 Display Modes: Light, Dark & System Default
Settings ke andar user ko **3 Mode Toggles** milte hain:

1. **☀️ Light Mode:** Selected palette ka Light Mode palette apply hota hai (Ultra-clean pastel cards, deep legible typography).
2. **🌙 Dark Mode:** Selected palette ka Dark Mode palette apply hota hai (Zero-power OLED true black, luminous accents, eye-friendly night comfort).
3. **⚙️ System Default:** Android OS ki system-wide dark theme state (`isSystemInDarkTheme()`) ko autonomously follow karta hai. Din mein automatically Light Mode aur raat ko Dark Mode par seamlessly switch hota hai bina app restart ke.

---

### 2.3 Global Cascading Theme Architecture
Theme change hone par poora app ek synchronized flow mein transform hota hai. Kisi bhi screen par purana color residue nahi bachta:

```
[ User Selects Theme in Settings ]
               │
               ▼
   [ DataStore Preference Updated ]
               │
               ▼
[ StateFlow<JarvisThemeState> Emits ]
               │
               ▼
[ CompositionLocalProvider(LocalJarvisColors provides dynamicColors) ]
  ├── 1. Background Canvas: Scaffold background & ambient mesh adapt
  ├── 2. Surfaces & Bento Cards: All containers recalculate elevation & satin fills
  ├── 3. Typography & Text: All Headings, Body & Captions swap high-contrast tokens
  ├── 4. Action Buttons & Pills: Gradients re-render with active primary & accent
  ├── 5. Chat Bubbles: User bubble gets active theme gradient; AI card gets theme surface
  ├── 6. Floating Navigation Dock: Active indicator morphs into new theme accent
  ├── 7. Holographic Quantum Core: Audio-reactive particle rings shift to new brand color
  └── 8. Modals, Sheets & Dialogs: Biometric Gate, Termux, and Session drawers update
```

---

### 2.4 Settings Live Preview & Instant Apply Mechanism
User ko andhere mein settings change karne ki zaroorat nahi hogi. Settings screen mein ek **Interactive Live Theme Preview Card** display hota hai:

```
+-------------------------------------------------------------+
|  🎨 LIVE THEME PREVIEW STAGE                                |
|  +-------------------------------------------------------+  |
|  | [ (•) JARVIS ONLINE ]                     10:45 AM    |  |
|  | User: "Check my daily schedule"                       |  |
|  | [User Bubble rendered in selected theme gradient]     |  |
|  |                                                       |  |
|  | AI: "You have 2 meetings today at 2:30 PM."           |  |
|  | [AI Card rendered in selected theme surface fill]     |  |
|  |                                                       |  |
|  | Bottom Pill: [ (•) Active ] [ Tab 2 ] [ Tab 3 ]       |  |
|  +-------------------------------------------------------+  |
|                                                             |
|  PALETTE PRESETS:                                           |
|  (•) Ocean Blue     ( ) Royal Purple     ( ) Aqua Mint      |
|                                                             |
|  DISPLAY MODE:                                              |
|  [ ☀️ Light ]          [ 🌙 Dark ]        [ ⚙️ System Auto ]|
+-------------------------------------------------------------+
```

- **Instant Apply (Zero Recreate):** Theme change hone par Activity restart (`recreate()`) nahi hoti. Jetpack Compose ki dynamic state recomposition instant (0ms delay) visual update provide karti hai.

---

### 2.5 Persistent Storage via Jetpack DataStore
Theme preferences Android Keystore / Jetpack DataStore Preferences (`jarvis_user_settings.preferences_pb`) mein securely persist hoti hain:
- `KEY_THEME_PALETTE`: `"OCEAN_BLUE"` | `"ROYAL_PURPLE"` | `"AQUA_MINT"`
- `KEY_THEME_MODE`: `"LIGHT"` | `"DARK"` | `"SYSTEM"`
- `KEY_DYNAMIC_COLOR_ENABLED`: `Boolean` (Android 12+ wallpaper Monet palette opt-in)

App kill hone ya phone reboot hone par splash screen ke dauran hi cold-start par saved theme load hoti hai, jisse koi **"white/black flash"** nahi aata.

---

### 2.6 Material 3 Dynamic Color (Android 12+ Monet) Coexistence
- Agar user Android 12+ (API 31+) device par hai aur unhe apna device wallpaper color system chahiye, toh wo **"Match Device Wallpaper (Material You)"** toggle on kar sakte hain.
- Toggle off hone par hamare teeno curated **Luxury Presets (Ocean Blue, Royal Purple, Aqua Mint)** priority lete hain jo specially Jarvis ke sci-fi glass layout ke liye tune kiye gaye hain.

---

### 2.7 Centralized Compose Token Architecture (`JarvisTheme`)
Har screen composable mein direct `Color(0xFF...)` hardcode karna strictly prohibited hai. Saare UI components sirf semantic tokens use karenge:

```kotlin
// Centralized Contract for All Screens
@Composable
fun JarvisTheme(
    palette: ThemePalette = ThemePalette.ROYAL_PURPLE,
    mode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    
    val colors = remember(palette, isDark) {
        getThemeColors(palette, isDark)
    }

    CompositionLocalProvider(
        LocalJarvisColors provides colors
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterial3ColorScheme(isDark),
            typography = JarvisTypography,
            content = content
        )
    }
}
```

---

## 3. Visual Identity, Surface Physics & Typography Standards

### 3.1 Surface Refraction & Specular Borders
Satin Glass cards par 3-tone gradient stroke lensed appearance provide karta hai:
- **Top Edge:** `colors.borderHairline.copy(alpha = 0.25f)` (Light refraction from top)
- **Mid Body:** `colors.surfaceElevated.copy(alpha = 0.60f)` (Solid satin body)
- **Bottom Edge:** `colors.borderHairline.copy(alpha = 0.08f)` (Soft occlusion)

### 3.2 Typography Rules (Zero Colored Body Text)
- Titles hamesha **100% Pure Optical White** (Dark Mode) ya **High-Contrast Deep Navy/Plum** (Light Mode) mein honge.
- Body text kabhi bhi bright purple ya cyan mein nahi hoga — readable text hamesha **WCAG AAA Compliant (Contrast Ratio > 7:1)** rahega.

---

## 4. Navigation Architecture: The Ergonomic Floating Capsule Dock

### 4.1 Complicated Radial Dock Ko Replace Kyun Kiya?
Radial wheel navigation ek hath se chalate waqt thumb ke natural arc mein mis-taps deti hai. Iske bajaye **"Ergonomic Floating Capsule Dock"** introduce kiya gaya hai:

```
+---------------------------------------------------------------------------------+
|                       FLOATING ERGONOMIC CAPSULE DOCK                           |
|                                                                                 |
|   +-------------------------------------------------------------------------+   |
|   |   [ 🏠 Cockpit ]     [ 💬 Dialogue ]     [ 🎯 Operator ]     [ 🧠 Brain ]|   |
|   +-------------------------------------------------------------------------+   |
|                                                                                 |
+---------------------------------------------------------------------------------+
```

### 4.2 4-Pill Navigation Specs:
1. **Dock Position:** Screen ke bottom par `12.dp` padding ke sath float karta hai, with `.navigationBarsPadding()` taaki Android gesture bar se na takraye.
2. **Touch Targets:** Har item ka minimum hit target `56.dp x 48.dp` hai (Accessible & comfortable).
3. **Selected State:** Selected tab smoothly expand hota hai into a solid theme-accent pill with white icon aur bold label, jabki unselected tabs subtle monochrome icons rehte hain.
4. **Haptic Feedback:** Tab switch hone par subtle tactile haptic click feel hota hai.

---

## 5. Screen 1: Home Dashboard (Minimalist Spatial Cockpit)

### 5.1 Layout Overview
Home screen ab shant aur executive hai. 3 clearly defined vertical blocks hain:
1. **Top Zone (20% Height):** Ambient Header & Status Island.
2. **Center Zone (45% Height):** Calm Interactive Quantum Core & Instant Action Launchers.
3. **Lower Zone (35% Height):** Smart Contextual Glance Deck (Only shows active tasks / next actions).

### 5.2 Phone-Sized ASCII Wireframe (Home Screen)

```
+-----------------------------------------------------------+
| [ ⚡ JARVIS OS ]          [ 🟢 ONLINE ]       [ 🔔 ] [ ⚙️ ] | <- Dynamic Status Island
+-----------------------------------------------------------+
|                                                           |
|                                                           |
|             +-------------------------------+             |
|             |                               |             |
|             |        ((((   🟣   ))))        |             | <- Minimalist Quantum Pulse
|             |                               |             |    (Breathes calmly on idle,
|             |      "Ready for commands"     |             |     blooms on voice input)
|             |                               |             |
|             +-------------------------------+             |
|                                                           |
|   +---------------------------------------------------+   |
|   | 🔍  Ask Jarvis, search phone, or command...   |🎙️|   | <- Omni-Command Input Bar
|   +---------------------------------------------------+   |
|                                                           |
|   QUICK DISPATCH ACTIONS (2 Large Ergonomic Buttons):     |
|   +---------------------------+ +---------------------+   |
|   |  🎙️ START LIVE CALL       | |  💬 AI TEXT STUDIO  |   |
|   |  Face-to-face voice & cam | |  Chat, vision, docs |   |
|   |  [ Gradient: Theme Accent]| |  [ Satin Glass Card]|   |
|   +---------------------------+ +---------------------+   |
|                                                           |
|   CONTEXTUAL SMART GLANCE (Shows what matters right now):  |
|   +---------------------------------------------------+   |
|   | ⚡ ACTIVE SYSTEM STATUS                           |   |
|   | • Call Screening: Active & guarding unwanted calls|   |
|   | • Next Reminder: Team Architecture Sync (2:30 PM) |   |
|   | • Memory Vault: 1,420 nodes linked & consolidated |   |
|   +---------------------------------------------------+   |
|                                                           |
|   +---------------------------------------------------+   |
|   |  [ 🏠 Cockpit ]   [ 💬 Dialogue ] [ 🎯 Operator ] [ 🧠 Brain ] | <- Floating Dock
|   +---------------------------------------------------+   |
+-----------------------------------------------------------+
```

---

## 6. Screen 2: Live Voice Companion Call Studio

### 6.1 Call Stage Philosophy
Voice assistant ko boring waveform list ke bajaye **Apple FaceTime / Personal Call Experience** jaisa banaya gaya hai.

### 6.2 Phone-Sized ASCII Wireframe (Live Voice Studio)

```
+-----------------------------------------------------------+
| [ ‹ Back ]       🎙️ JARVIS LIVE CALL       [ 🟢 18ms HD ] |
+-----------------------------------------------------------+
|                                                           |
|   PERSONA & MOOD SELECTOR (Horizontal Satin Bar):         |
|   [ 💖 Girlfriend/Flirty ] [ 👔 Professional ] [ 😈 18+ ] |
|                                                           |
|   +---------------------------------------------------+   |
|   |                                                   |   |
|   |             🌟 CALL STAGE & COMPANION             |   |
|   |                                                   |   |
|   |                   (((( 💖 ))))                    |   |
|   |               Status: Speaking (Nova)             |   |
|   |                                                   |   |
|   |         Waveform:  ||||||||||||||||||||           |   |
|   |                                                   |   |
|   |   "Maine aapka calendar check kiya. 2:30 baje     |   |
|   |    meeting hai, presentation slides ready hain!"  |   |
|   |                                                   |   |
|   +---------------------------------------------------+   |
|                                                           |
|   SCREEN PERCEPTION TILE (When Sharing Enabled):          |
|   +---------------------------------------------------+   |
|   | [ 📸 Screen Live: 2 FPS ]  [ 🎯 Touch Agent: Standby ] |
|   +---------------------------------------------------+   |
|                                                           |
|   LIVE TRANSCRIPT SNIPPET (Scrollable):                   |
|   User: "Jarvis, mere liye Uber cab book kar de"          |
|   AI:   "Jarvis searching cabs near your location..."     |
|                                                           |
|   FACETIME ROUNDED ACTION DOCK:                           |
|   +---------------------------------------------------+   |
|   |   ( 🎤 Mute )   ( 🖐️ Interrupt )   ( 🛑 End Call )   |   |
|   +---------------------------------------------------+   |
|                                                           |
+-----------------------------------------------------------+
```

---

## 7. Screen 3: Neural Chat Studio & Multimodal Ingestion

### 7.1 Layout Overview
Text chat ko clean **iMessage Pro Studio** standard par layenge. Clean legible bubbles aur rich embedded widgets.

### 7.2 Phone-Sized ASCII Wireframe (Chat Studio)

```
+-----------------------------------------------------------+
| [ ‹ Cockpit ]   💬 NEURAL STUDIO   [ 🗂️ History ] [ ➕ New]|
+-----------------------------------------------------------+
| Model: Gemini 2.5 Flash   •   Language: [ Hinglish ▾ ]    |
+-----------------------------------------------------------+
|                                                           |
| [User Bubble - Dynamic Theme Gradient]           10:14 AM |
| "Mera electricity bill check kar aur remind kar"          |
|                                                           |
| [AI Message - Clean Satin Glass Card]            10:14 AM |
| +-------------------------------------------------------+ |
| | 🔮 JARVIS NEURAL CORE                                 | |
| | Electricity Bill Details:                             | |
| | • Amount Due: ₹2,450.00                               | |
| | • Due Date: 12th November 2026                        | |
| |                                                       | |
| | [ ⚡ Pay via GPay ]   [ ⏰ Set Alarm ]   [ 💾 Save ]    | |
| +-------------------------------------------------------+ |
|                                                           |
| [AI Thinking Indicator: "Syncing reminder with SQLite..."]|
|                                                           |
| +-------------------------------------------------------+ |
| | QUICK ATTACH STRIP:                                   | |
| | [ 📸 Camera ]  [ 🖼️ Gallery ]  [ 📄 PDF ]  [ 🌐 Link ] | |
| +-------------------------------------------------------+ |
|                                                           |
| +---------------------------------------------------+---+ |
| | Type message, prompt, or paste text...            | 🚀| |
| +---------------------------------------------------+---+ |
|                                                           |
|   +---------------------------------------------------+   |
|   |  [ 🏠 Cockpit ]   [ 💬 Dialogue ] [ 🎯 Operator ] [ 🧠 Brain ] |
|   +---------------------------------------------------+   |
+-----------------------------------------------------------+
```

---

## 8. Screen 4: Autonomous CUA Operator Deck (10 AI Engines & Device Control)

### 8.1 Operator Deck Architecture
Ye screen Jarvis ke core **10 Autonomous Mobile Agent Features** ka live control center hai.

### 8.2 Phone-Sized ASCII Wireframe (Operator Deck)

```
+-----------------------------------------------------------+
| [ ‹ Cockpit ]   🎯 CUA OPERATOR DECK    [ 🛑 EMERGENCY STOP ]|
+-----------------------------------------------------------+
|                                                           |
|   GOAL DISPATCHER (Execute Any Android Task):             |
|   +---------------------------------------------------+   |
|   | "Open Swiggy, search Biryani, add to cart"    | 🚀|   |
|   +---------------------------------------------------+   |
|                                                           |
|   AUTONOMOUS ENGINE STEP RADAR:                           |
|   +---------------------------------------------------+   |
|   | 🟢 ACTIVE EXECUTION: Step 2 of 4                  |   |
|   | [DONE] 1. Launched package: in.swiggy.android     |   |
|   | [LIVE] 2. Set-of-Marks: Found Node 'Search' (#4)  |   |
|   | [WAIT] 3. Typing 'Chicken Dum Biryani'            |   |
|   | [WAIT] 4. Add First Match Item to Cart            |   |
|   |                                                   |   |
|   | [ ↩️ Voice Undo Step ]       [ ⏸️ Pause Execution ] |   |
|   +---------------------------------------------------+   |
|                                                           |
|   OPERATOR CAPABILITY TILES (2x2 Clean Grid):             |
|   +---------------------------+ +---------------------+   |
|   | 📱 Set-of-Marks Radar     | | 📝 Form Auto-Filler |   |
|   | Live visual screen scan   | | Instant form fill   |   |
|   +---------------------------+ +---------------------+   |
|   +---------------------------+ +---------------------+   |
|   | 📹 Teachable Macro        | | 🛡️ Biometric Gate   |   |
|   | Record touch gesture demo | | High-risk protection|   |
|   +---------------------------+ +---------------------+   |
|                                                           |
|   PHONE HARDWARE QUICK SWITCHES:                          |
|   [ 🔦 Flashlight ]  [ 📶 Wi-Fi ]  [ 🔊 Sound ]  [ 📱 Apps ]|
|                                                           |
|   +---------------------------------------------------+   |
|   |  [ 🏠 Cockpit ]   [ 💬 Dialogue ] [ 🎯 Operator ] [ 🧠 Brain ] |
|   +---------------------------------------------------+   |
+-----------------------------------------------------------+
```

---

## 9. Screen 5: 5-Tier Synaptic Memory Vault

### 9.1 Memory Vault Concept & Layout
5-Tier memory architecture (Short-Term, Entity Stack, Working Task Plan, Episodic Timeline, Semantic Knowledge Triples) organized with dedicated tabs.

### 9.2 Phone-Sized ASCII Wireframe (Memory Vault)

```
+-----------------------------------------------------------+
| [ ‹ Cockpit ]     🧠 SYNAPTIC MEMORY VAULT    [ ⚡ Consolidate ]|
+-----------------------------------------------------------+
| [ 🔍 Search 1,420 memories, facts, preferences...        ] |
+-----------------------------------------------------------+
| TIER SELECTOR:                                            |
| [ • Facts ]  [ Entities ]  [ Active Task ]  [ Timeline ]  [ Triples ]|
+-----------------------------------------------------------+
|                                                           |
|   PINNED KNOWLEDGE CARD:                                  |
|   +---------------------------------------------------+   |
|   | 📌 CORE USER IDENTITY (Importance: 1.0)           |   |
|   | "User prefers vegetarian food recommendations and  |   |
|   |  likes to communicate in Hinglish language."      |   |
|   | Category: [ Preferences ]   Saved: Nov 05, 2026   |   |
|   | [ ✏️ Edit ]               [ 📌 Unpin ]  [ 🗑️ Delete ]|   |
|   +---------------------------------------------------+   |
|                                                           |
|   SEMANTIC KNOWLEDGE GRAPH NODE:                          |
|   +---------------------------------------------------+   |
|   | 🌐 (User) --[ works_as ]--> (Software Engineer)   |   |
|   | Confidence: 99% • Extracted from Session #4       |   |
|   +---------------------------------------------------+   |
|                                                           |
|   ACTIONS:                                                |
|   [ 🧹 Purge Expired ]  [ 📤 Export Data ]  [ ➕ Add Memory ]|
|                                                           |
|   +---------------------------------------------------+   |
|   |  [ 🏠 Cockpit ]   [ 💬 Dialogue ] [ 🎯 Operator ] [ 🧠 Brain ] |
|   +---------------------------------------------------+   |
+-----------------------------------------------------------+
```

---

## 10. Screen 6: Agent Multi-Studio Hub (Sub-Systems)

### 10.1 Multi-Studio Layout Overview
Termux, Watchman Security, Driving Cockpit, Macros, aur Voice Notes segmented cleanly:

```
+-----------------------------------------------------------+
| [ ‹ Operator ]        🎯 AGENT STUDIO HUB                 |
+-----------------------------------------------------------+
| [ • Termux ] [ Watchman ] [ Driving ] [ Macros ] [ Voice Notes ]|
+-----------------------------------------------------------+
|                                                           |
|   TERMUX LINUX CLI CONSOLE (When Termux Tab Selected):    |
|   +---------------------------------------------------+   |
|   | $ uname -a                                        |   |
|   | Linux jarvis-terminal 5.15.0-android-aarch64       |   |
|   | $ uptime                                          |   |
|   | up 4 hours, load average: 0.12, 0.08, 0.05        |   |
|   |                                                   |   |
|   | [ Type shell command or bash script...      ] [ ↵]|   |
|   +---------------------------------------------------+   |
|   QUICK ACTIONS: [ 📊 top ]  [ 💾 df -h ]  [ 🌐 ifconfig ] |
|                                                           |
+-----------------------------------------------------------+
```

---

## 11. Screen 7: Safe Safari Web Reader & DOM Engine

### 11.1 Crash-Free Native Web Reader
Chromium multi-process crashes ko completely eliminate karke **Native OkHttp + DOM Synthesis Engine** ke sath layout:

```
+-----------------------------------------------------------+
| [ ‹ Back ]       🌐 SAFARI WEB READER         [ 🔗 Chrome]|
+-----------------------------------------------------------+
| [ 🔍 https://en.wikipedia.org/wiki/Artificial_intelligence]|
+-----------------------------------------------------------+
| BOOKMARKS: [ Google ]  [ Wikipedia ]  [ GitHub ]  [ Gemini]|
+-----------------------------------------------------------+
|                                                           |
|   ARTICLE READER VIEW:                                    |
|   +---------------------------------------------------+   |
|   | ◆ Artificial Intelligence (AI) Overview           |   |
|   |                                                   |   |
|   | Artificial intelligence is intelligence           |   |
|   | demonstrated by machines, as opposed to the       |   |
|   | natural intelligence displayed by animals.        |   |
|   +---------------------------------------------------+   |
|                                                           |
|   BOTTOM AI UTILITY DOCK:                                 |
|   +---------------------------------------------------+   |
|   | [ ✨ AI Page Summary ]    [ 🔊 Listen / Read Aloud ]|   |
|   +---------------------------------------------------+   |
|                                                           |
+-----------------------------------------------------------+
```

---

## 12. Screen 8: Unified Settings Hub, Keystore Vault & Persona Studio

### 12.1 All-in-One Grouped Settings Architecture
Settings ko 6 logical **Apple iOS Style Grouped Cards** mein structure kiya gaya hai:

```
+-----------------------------------------------------------+
| [ ‹ Cockpit ]         ⚙️ SETTINGS HUB         [ Save All ]|
+-----------------------------------------------------------+
|                                                           |
|   +---------------------------------------------------+   |
|   | 🔮 JARVIS CORE: Gemini 2.5 • All Systems Ready    |   |
|   +---------------------------------------------------+   |
|                                                           |
|   [ 🔍 Search settings, models, API keys...             ] |
|                                                           |
|   SECTION 1: APPEARANCE & THEMES ENGINE                   |
|   ├── Live Theme Preview Stage ................... [ Open›|
|   ├── Theme Preset ............ [ Ocean Blue / Purple / Mint›]|
|   └── Display Mode .............. [ Light / Dark / Auto ›]|
|                                                           |
|   SECTION 2: AI ENGINE & ENCRYPTED KEYSTORE               |
|   ├── Gemini API Key (Encrypted in Keystore) ........ [ ›]|
|   └── Active Model Selection (Flash/Pro) ............ [ ›]|
|                                                           |
|   SECTION 3: VOICE & SPEECH SYNTHESIS                     |
|   ├── Offline Wake Word ("Hey Jarvis") ......... [ SWITCH]|
|   └── Voice Persona (Nova / Female) ................. [ ›]|
|                                                           |
|   SECTION 4: PERSONALITY & COMPANION EMOTION              |
|   ├── Personality Mode (Flirty / Intimate 18+ / Pro) [ ›]|
|   └── Proactive Companion & Ambient Talk ....... [ SWITCH]|
|                                                           |
|   SECTION 5: AUTONOMOUS AGENT & SECURITY                  |
|   ├── Accessibility Service Status ......... [ 🟢 Active ]|
|   └── Require Fingerprint for High-Risk Actions . [ SWITCH]|
|                                                           |
|   SECTION 6: DATA STORAGE & MEMORY MANAGEMENT             |
|   ├── Auto-Inject Memories in Prompts .......... [ SWITCH]|
|   └── Wipe Memory Vault / Reset Chats .......... [ Red › ]|
|                                                           |
+-----------------------------------------------------------+
```

---

## 13. One-Hand Reachability & Ergonomic Layout Zones

Phone screen ko vertically **3 Ergonomic Zones** mein divide kiya gaya hai:

```
+-------------------------------------------+
| ZONE 1: DISPLAY & TELEMETRY (Top 25%)     | <- Eye Glance Only (Status, Battery, Time)
| (No interactive buttons requiring reach)  |
+-------------------------------------------+
| ZONE 2: CONTEXTUAL VIEWPORT (Middle 45%)  | <- Scrollable Content, Cards, Chat Logs
| (Natural viewing & reading zone)          |
+-------------------------------------------+
| ZONE 3: PRIMARY INTERACTION (Bottom 30%)  | <- 100% ONE-HAND THUMB REACHABLE
| (Omni-Bar, Dispatch Buttons, Navigation)  |    (Zero thumb strain)
+-------------------------------------------+
```

1. **Thumb Zone Rule:** Saare primary action triggers (`Send`, `Mute`, `Interrupt`, `Start Call`, `Navigation Tabs`, `Search Input`) bottom 30% area mein anchor honge.
2. **Minimum Touch Size:** Har button ka click area minimum `48.dp x 48.dp` hai to eliminate mis-clicks.

---

## 14. Existing Backend & ViewModel Accurate Mapping Table

Redesign ke dauran existing backend logic, Room databases, aur ViewModel methods ka exact mapping plan:

| Existing ViewModel Flow / Method | Purana Screen Location | Naya Spatial OS Location | Functionality Status |
| :--- | :--- | :--- | :--- |
| `startLiveVoiceSession()`, `stopLiveVoiceSession()` | `LiveVoiceScreen.kt` | `LiveVoiceScreen.kt` (FaceTime Call Stage) | 100% Preserved |
| `toggleLiveVoiceMute()`, `interruptLiveVoice()` | `LiveVoiceScreen.kt` | Bottom Rounded Call Action Dock | 100% Preserved |
| `updatePersonalityMode(mode)` | `GeneralSettingsScreen.kt` | Quick Persona Selector Bar in Voice Stage | 100% Preserved |
| `sendChatMessage(prompt)` | `TextChatScreen.kt` | Omni-Command Bar & Chat Studio | 100% Preserved |
| `runAutonomousAgentGoal(goal)` | `JarvisAgenticCommandCenterCard` | `OperatorDeckScreen.kt` (Goal Dispatcher) | 100% Preserved |
| `scanSetOfMarkBadges()`, `executeVisionSetOfMarkTap()`| `JarvisAgenticCommandCenterCard` | Live Set-of-Marks Radar Tile | 100% Preserved |
| `confirmHighRiskAgentAction()` | Basic Dialog | Holographic Biometric Security Gate | 100% Preserved |
| `saveApprovedMemory()`, `allMemories` | `MemoryCenterScreen.kt` | 5-Tier Synaptic Vault (Tier 1-5 tabs) | 100% Preserved |
| `saveApiKey(key)` | `ApiSettingsScreen.kt` | Keystore Encrypted Row in Settings Hub | 100% Preserved |
| `NativeWebReaderEngine.fetchAndParseUrl()` | `BrowserScreen.kt` | Safe Safari Reader View | 100% Preserved |
| `JarvisTermuxBridge.executeCommand()` | Sub-tab in `AgentHubScreen.kt` | Terminal Studio Card in Operator Hub | 100% Preserved |
| `JarvisWatchmanManager` | Sub-tab in `AgentHubScreen.kt` | Security Camera Trap in Operator Hub | 100% Preserved |

---

## 15. Performance, 120Hz Smoothness & Accessibility Compliance

1. **60fps/120Hz Smooth Scrolling:**
   - Expensive runtime `RenderEffect.createBlurEffect()` ko avoid kiya gaya hai kyunki low-end devices par ye frame drops karta hai.
   - Uske bajaye pre-calculated semi-transparent solid layers (`#111625` with alpha `0.90f`) aur hardware-accelerated linear gradients use honge.
2. **Recomposition Optimization:**
   - Har list item `key` parameter ke sath render hoga (`items(messages, key = { it.id })`).
   - Derived states (`derivedStateOf`) aur memoized lambdas use honge taaki audio level change hone par poori screen recompose na ho, sirf Quantum Pulse canvas redraw ho.
3. **Accessibility (A11y):**
   - Sabhi icons ke paas meaningful `contentDescription` hoga.
   - Text contrast ratio pure white on dark base canvas par **18:1** hai (WCAG AAA standard surpass karta hai).

---

## 16. Phase-by-Phase Replacement & Safe Migration Roadmap

Is master plan ko code mein implement karne ka structured 5-phase roadmap:

```
[ PHASE 1: Centralized Appearance & Themes Engine ]
  ├── Create `JarvisThemeColors.kt` with Ocean Blue, Royal Purple, and Aqua Mint tokens
  ├── Implement `JarvisTheme.kt` with Light/Dark/System dynamic support & DataStore sync
  └── Test compilation with `compile_applet`

[ PHASE 2: Home Dashboard "Spatial Cockpit" Rewrite ]
  ├── Create clean `QuantumPulseCore.kt` (calm idle, responsive voice audio)
  ├── Rewrite `HomeDashboardScreen.kt` with clean 3-zone layout (Omni-bar + Glance)
  └── Verify with `compile_applet` and Robolectric unit tests

[ PHASE 3: Dialogue Studio (FaceTime Live Voice & Neural Chat) ]
  ├── Rebuild `LiveVoiceScreen.kt` with clean FaceTime rounded call stage
  ├── Rebuild `TextChatScreen.kt` with actionable rich widgets & session drawer
  └── Verify microphone, audio streaming, and transcripts

[ PHASE 4: Autonomous CUA Operator Deck & 5-Tier Memory ]
  ├── Rebuild `OperatorDeckScreen.kt` with Set-of-Marks radar & Step tree
  ├── Rebuild `MemoryCenterScreen.kt` with clean Tier tabs (1 to 5)
  └── Verify all 10 CUA engine actions & biometric gatekeeper

[ PHASE 5: Settings Hub & Sub-System Polish ]
  ├── Rebuild `GeneralSettingsScreen.kt` into clean 6-group iOS cards with Theme Selector & Preview
  └── Full system regression pass: `gradle :app:testDebugUnitTest`
```

---

> **Final Confirmation:** Ye updated specification document user ki sabhi guidelines (No clutter, No radial gimmicks, No neon glare, Clean 1-hand ergonomics, Dedicated Appearance & Themes System with Ocean Blue, Royal Purple, Aqua Mint presets, 100% functionality preservation) ko perfectly address karta hai. Ready for approval!
