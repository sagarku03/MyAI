# 🔮 JARVIS AI — Apple iOS "Liquid Glass" + Royal Purple UI/UX Master Plan & Deep Code Audit Report

> **🎨 Design Theme:** **Apple iOS 26 "Liquid Glass" (Translucent Specular Glassmorphism)**  
> **💜 Primary Brand Color:** **Royal Electric Purple (`#8B5CF6` → `#A855F7` → `#C084FC`)**  
> **🔤 Typography Style:** **High-Impact Bold iOS Hierarchy (`ExtraBold` / `Bold` / `SemiBold`)**  
> **📱 Architecture Goal:** **Scattered 14-Screen Clutter ➔ Clean 5-Tab iOS Floating Glass Dock + Unified A-to-Z Settings Hub**  
> **🛠️ Build Status:** **Planning & Deep Audit Only** *(Aapke instruction ke mutabik abhi koi code change nahi kiya gaya hai)*

---

## 📊 Executive Visual Summary (Ek Nazar Mein Pura Plan)

| 🎯 Design Pillar | ❌ Abhi Ka UI (Current Problem) | ✅ Naya iOS Purple Liquid Glass UI (Proposed Solution) |
| :--- | :--- | :--- |
| **🎨 Color Palette** | **7+ Clashing Colors Ek Sath:** Neon Lime Green (`#A3FF12`), Cyan (`#22D3EE`), Hot Pink (`#EC4899`), Blue (`#3B82F6`), Amber (`#F59E0B`) ek hi screen par | **60-30-10 Royal Purple System:** Deep Amethyst Obsidian (`#070510`) + Frosted Violet Glass (`#17112B`) + Electric Royal Purple (`#8B5CF6` / `#A855F7`) |
| **🪟 Surface & Cards** | **7 Alag-Alag Templates Mix:** `WealthWave`, `Monex`, `Poly Ultimate`, `Apple Arcade`, `SHRUHH` sab ek file (`HudComponents.kt`) me khichdi bane hue hain | **Pure Apple iOS 26 Liquid Glass:** Continuous `28.dp` squircle corners, top-left specular rim light (`1.dp` white-to-purple gradient border), real translucent depth |
| **🔤 Typography** | **5 Conflicting Fonts & Thin Text:** `SpaceGrotesk`, `PlusJakartaSans`, `Orbitron`, `Rajdhani`, `ShareTechMono` + `FontWeight.Normal` (patla text) | **2-Font Bold Hierarchy:** `Plus Jakarta Sans` (`ExtraBold` / `Bold` / `SemiBold`) + `JetBrains Mono Bold` (sirf numbers/metrics ke liye) |
| **🔘 Buttons** | **3 Ulte-Seedhe Button Styles:** Neon Green button (`#A3FF12`), Cyan outline button, aur 3-color gradient button aapas me takra rahe hain | **Unified iOS Pill Button System:** `999.dp` Liquid Purple Pill CTA + Specular Frosted Secondary Pill + `48.dp` Circular Glass Icon Orbs |
| **🧭 Navigation & Screens** | **14 Bikhere Hue Screens:** 5 bottom tabs + 9 screens (`API_SETTINGS`, `VISION`, `WORKFLOW`, `MEMORY`, etc.) Home screen par kachre ki tarah pade hain | **5-Tab Floating Glass Dock + Unified Settings:** Home, Chat & Vision, Live Orb, Agent Hub, aur **1 Complete Settings Screen** jisme A-to-Z sari settings group hongi |

---

# 🔍 PART 1: Deep Frontend UI Code Audit (Abhi Ke Code Mein Kya-Kya Galat Hai?)

Maine aapke pure Frontend UI codebase (`Color.kt`, `Theme.kt`, `Type.kt`, `HudComponents.kt`, `JarvisApp.kt`, `HomeDashboardScreen.kt`, `GeneralSettingsScreen.kt`, `ApiSettingsScreen.kt`, `AgentHubScreen.kt`, `LiveVoiceScreen.kt`) ko line-by-line deeply check kiya. Aap bilkul sahi bol rahe ho — **abhi ka UI isliye bekar aur bikhra hua lag raha hai kyunki code me ye 5 bade structural blunders hain:**

---

### 🚨 Blunder #1: "Rainbow Color Chaos" — Ek Hi Screen Par 7 Alag-Alag Neon Colors!
* **File Checked:** `app/src/main/java/com/example/ui/theme/Color.kt` (Lines 1–69) & `HudComponents.kt` (Lines 60–360)
* **Code Mein Kya Galat Hai:**
  1. `Color.kt` me koi single Primary Brand Color follow nahi ho raha hai:
     * `JarvisCyan = Color(0xFF22D3EE)` (Neon Cyan)
     * `JarvisBlue = Color(0xFF3B82F6)` (Electric Blue)
     * `JarvisNeonGreen = Color(0xFFA3FF12)` (Radioactive Lime Green!)
     * `JarvisPink = Color(0xFFEC4899)` (Hot Pink)
     * `JarvisAmber = Color(0xFFF59E0B)` (Orange/Yellow)
     * `JarvisGreen = Color(0xFF10B981)` (Emerald Green)
  2. `Theme.kt` (Line 23–28) me `primary = JarvisCyan`, `secondary = JarvisViolet`, aur `tertiary = JarvisPink` set hai, lekin `HudComponents.kt` (Line 319) me **Primary Action Button (`JarvisPrimaryActionButton`) ka default color `JarvisNeonGreen (#A3FF12)` (parrot green)** rakha hua hai!
  3. `HomeDashboardScreen.kt` (Line 338–357) me `"Monex Peek-a-Boo Strip"` hai jo `JarvisGreen` aur `JarvisCyan` ka gradient banati hai, uske niche Cyan card hai, uske niche Pink-Blue button hai, aur uske niche Lime Green button hai.
* **Result:** Aankhon ko sukun dene ke bajay pura app ek "Neon Circus" jaisa lagta hai jahan koi bhi color ek-dusre se match nahi karta.

---

### 🚨 Blunder #2: 7 Alag-Alag Design Templates Ki Khichdi (`HudComponents.kt`)
* **File Checked:** `app/src/main/java/com/jarvisai/assistant/ui/components/HudComponents.kt` (1,839 Lines!)
* **Code Mein Kya Galat Hai:**
  Is ek file ke comments aur components dekhoge to pata chalega ki isme **7 alag-alag apps ke UI styles Zabardasti ghusaye gaye hain**:
  1. **Line 231:** `"WealthWave Credit Card Style"` (3-color loud gradient card)
  2. **Line 306:** `"System Update & Full Control Style"` (Neon Lime Green `#A3FF12` pill buttons)
  3. **Line 368:** `"Monex Peek-a-Boo Top Accent Card"` (Green-Cyan top strip card)
  4. **Line 422:** `"Apple Arcade Overlapping Banner"`
  5. **Line 463:** `"Poly Ultimate Bento Grid Tile"`
  6. **Line 524:** `"SHRUHH Directory Search Input"`
* **Result:** Har card ka corner radius alag hai (`14.dp`, `16.dp`, `20.dp`, `24.dp`, `26.dp`, `28.dp`), har card ka border alag hai, aur har card ka shadow/background alag hai. Isiliye pura UI "bikhra pada hua" (fragmented) dikhta hai.

---

### 🚨 Blunder #3: 5 Conflicting Fonts + Weak/Thin Typography (`Type.kt`)
* **File Checked:** `app/src/main/java/com/example/ui/theme/Type.kt` (Lines 1–135)
* **Code Mein Kya Galat Hai:**
  1. **5 Alag Fonts Ek Sath Load Ho Rahe Hain:**
     * `SpaceGroteskFamily`
     * `PlusJakartaSansFamily`
     * `OrbitronFamily` (Purana 1990s sci-fi boxy font)
     * `RajdhaniFamily`
     * `ShareTechMonoFamily`
  2. **Body Aur Labels Patle (`FontWeight.Normal`) Hain:**
     * `bodyLarge` (Line 99) aur `bodyMedium` (Line 107) me `FontWeight.Normal` use hua hai. Dark glass background ke upar `Normal` weight ka font dhundhla (washed-out) lagta hai.
     * `HudComponents.kt` me kai jagah `9.sp`, `10.sp`, `11.sp` ke micro-labels hain jo mobile screen par padhne me bilkul achhe nahi lagte.
* **Result:** Apple iOS jaisa **Bold, Crisp, Punchy look** bilkul nahi aata.

---

### 🚨 Blunder #4: Buttons Mein Koi Standard Hierarchy Nahi Hai
* **File Checked:** `HudComponents.kt` (Lines 184–364) & `GeneralSettingsScreen.kt`
* **Code Mein Kya Galat Hai:**
  * Kahin par `JarvisGradientButton` (`16.dp` corner radius) use hua hai, kahin par `JarvisPrimaryActionButton` (`999.dp` pill with Neon Lime Green `#A3FF12`), kahin par `JarvisSecondaryActionButton` (Cyan border), aur kahin par Compose ka default `Button()` ya `OutlinedButton()` bina kisi styling ke daal diya gaya hai.
  * Buttons me na koi **iOS Specular Top Highlight** hai, na **Spring Scale Press Animation (`0.96f` bounce)** hai, aur na hi proper **52.dp–56.dp** standard height hai.

---

### 🚨 Blunder #5: 14 Bikhere Hue Screens & Settings Do Tukdon Mein (`JarvisApp.kt`)
* **File Checked:** `app/src/main/java/com/jarvisai/assistant/ui/JarvisApp.kt` (Lines 47–62 & 254–565)
* **Code Mein Kya Galat Hai:**
  1. `JarvisDestination` enum me **14 alag-alag screens** hain:
     `HOME`, `BROWSER`, `VOICE`, `AGENT_HUB`, `CHAT`, `MEMORY`, `CONTROLS`, `API_SETTINGS`, `GENERAL_SETTINGS`, `VISION`, `DOCUMENT`, `WORKFLOW`, `NOTIFICATIONS`, `MEMORY_REVIEW`.
  2. Bottom Navigation Bar me sirf 5 tabs hain (`HOME`, `BROWSER`, `VOICE`, `AGENT_HUB`, `GENERAL_SETTINGS`). Baaki ke **9 screens** ko access karne ke liye user ko `HomeDashboardScreen.kt` (jo 1,296 lines ka lamba kachra scroll ban chuka hai) me upar-niche dhundhna padta hai!
  3. **Sabse Badi Galti — Settings Do Alag Screens Mein Bati Hui Hai:**
     * **API Keys, Cloud Models, Custom Endpoint, Backup/Export** ➔ `ApiSettingsScreen.kt` me alag pade hain!
     * **Voice, Persona, Wake Word, Permissions, Security** ➔ `GeneralSettingsScreen.kt` (1,715 lines ki mega-scroll file!) me pade hain, jahan saare 15+ cards ek hi lambe page par bina kisi clean sub-page ya iOS grouped structure ke khule pade hain.

---

# 💜 PART 2: Royal Purple + Apple iOS 26 "Liquid Glass" Color System

Professional UI/UX design aur Apple Human Interface Guidelines (iOS 26 Liquid Glass) ke hisab se hum **60-30-10 Color Rule** lagayenge jahan aapka **Primary Color = Royal Electric Purple** hoga, aur koi bhi bekar Neon Green (`#A3FF12`) ya random Cyan/Orange screen ko ganda nahi karega.

---

### 🎨 1. The 60-30-10 Royal Purple Color Formula

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  60% BASE CANVAS (Deep Obsidian-Amethyst)                                    │
│  ███ #070510 (Primary Background)    ███ #0E0A1F (Elevated Depth Canvas)     │
│  -> Purple-tinted ultra-dark base taaki glass cards ke piche richness dikhe  │
├──────────────────────────────────────────────────────────────────────────────┤
│  30% LIQUID GLASS SURFACES (Translucent Frosted Violet Glass)                │
│  ▓▓▓ #18112E @ 68% Alpha (Regular Liquid Glass Card)                         │
│  ▓▓▓ #231842 @ 78% Alpha (Elevated / Interactive Glass Card & Sheet)         │
│  ▓▓▓ #FFFFFF @ 28% -> #A855F7 @ 40% (1.dp Specular Rim Border Highlight)     │
├──────────────────────────────────────────────────────────────────────────────┤
│  10% PRIMARY ROYAL PURPLE ACCENTS & GLOW                                     │
│  ▒▒▒ #8B5CF6 (Primary Royal Purple)  ▒▒▒ #A855F7 (Electric Orchid Glow)      │
│  ▒▒▒ #C084FC (Soft Lavender Edge)    ▒▒▒ #6D28D9 (Deep Pressed Purple)       │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

### 🎨 2. Complete Master Color Palette Table (`Color.kt` Replacement Blueprint)

| Color Role (UI Mein Kahan Use Hoga) | Token Name | HEX Code | Visual Swatch & Vibe |
| :--- | :--- | :---: | :--- |
| **🌌 Main App Background** | `PurpleVoidBg` | `#070510` | `🟣⬛` Deep Obsidian with subtle Royal Amethyst undertone |
| **🌌 Secondary Mesh Background** | `PurpleCosmosBg` | `#0F0A24` | `🟣🌑` Top ambient radial glow ke liye dark velvet purple |
| **🪟 Liquid Glass Card (Standard)** | `LiquidGlassSurface` | `#18112E` *(68% Alpha)* | `🔮✨` Frosted iOS dark purple glass surface |
| **🪟 Liquid Glass Card (Elevated/Popup)** | `LiquidGlassElevated` | `#241945` *(82% Alpha)* | `🔮💎` Dialogs, Bottom Sheets, aur Floating Tab Dock ke liye |
| **🪟 Liquid Glass Input / Pill Fill** | `LiquidGlassPill` | `#2E2059` *(55% Alpha)* | `🔮🫧` Search bars, TextFields, Segmented Controls ke liye |
| **💜 Primary Brand Purple (Main CTA)** | `RoyalPurplePrimary` | `#8B5CF6` | `🟣⚡` Apple-grade vibrant Royal Purple (Buttons, Active Tabs, Toggles) |
| **💜 Secondary Electric Purple** | `ElectricOrchid` | `#A855F7` | `🟣🔥` Gradients, Live Voice Orb, Active Glows |
| **💜 Tertiary Soft Lavender** | `SoftLavenderGlow` | `#C084FC` | `🟣🌸` Icons, Badges, Specular Rim Highlights, Chip Text |
| **💜 Deep Royal Press State** | `DeepAmethystPressed` | `#6D28D9` | `🟣🛡️` Button press state & gradient end stop |
| **💖 Romantic / Companion Accent** | `VelvetRoseAccent` | `#E879F9` | `🩷💜` Fuchsia-Lavender (Purple family ka hi romantic shade — alag nahi dikhega!) |
| **✨ Specular Top-Left Border** | `GlassRimLightTop` | `#FFFFFF` *(32% Alpha)* | `⚪✨` Apple Liquid Glass ka real secret: upar-left chamakne wali patli line |
| **✨ Specular Bottom-Right Border** | `GlassRimLightBottom` | `#8B5CF6` *(22% Alpha)* | `🟣✨` Niche-right purple refraction edge |
| **🔤 Primary Text (Headings & Body)** | `TextPureWhite` | `#F8F7FF` | `⚪❄️` Crisp Lavender-Tinted Pure White (100% readable) |
| **🔤 Secondary Text (Subtitles)** | `TextLavenderMuted` | `#C4B5FD` *(78% Alpha)* | `🟣📄` Soft Silver-Lavender for descriptions & subtitles |
| **🔤 Tertiary / Caption Text** | `TextVioletDim` | `#8B7VA8` / `#9382B8` | `🟣📝` Metadata, timestamps, hints |
| **🟢 Semantic Status: Online / Ready** | `StatusEmerald` | `#34D399` | `🟢` *Sirf چھوٹے status dot ya "Connected" badge ke liye (pure card par nahi!)* |
| **🟡 Semantic Status: Warning** | `StatusAmber` | `#FBBF24` | `🟡` *Sirf permission/warning alert badge ke liye* |
| **🔴 Semantic Status: Danger / Stop** | `StatusRoseRed` | `#FB7185` | `🔴` *Sirf Kill-Switch, Mic Mute, ya Delete action ke liye* |

> **💡 Golden Rule (Kyun Ye Palette Premium Dikhata Hai):**  
> Abhi ke app me har card alag color ka hai (ek card Hara, ek Neela, ek Peela). Naye UI me **90% UI Sirf Shades of Obsidian, Frosted Violet Glass, Crisp White, aur Royal Purple (`#8B5CF6` - `#C084FC`)** me hoga. Green/Yellow/Red sirf `8.dp` ke status dots ya warning alerts me dikhenge!

---

# 🪟 PART 3: Apple iOS 26 "Liquid Glass" Design System (Kaise Banega Real iOS Look?)

Web research aur Apple WWDC iOS 26 "Liquid Glass" specifications ke hisab se, koi भी UI sirf `alpha = 0.5f` lagane se "Liquid Glass" nahi banta. Real Apple Liquid Glass me **4 Visual Layers** hoti hain jo hum `HudComponents.kt` (naye naam: `LiquidGlassComponents.kt`) me banayenge:

```
   ╭─────────────────────────────────────────────────────────────╮  <-- 1. Top-Left Specular Rim Light
   │ ░░░░░░░░░░░░░░ Subtle Top White Sheen (12% Alpha) ░░░░░░░░░ │      (White 32% -> Purple 35% -> Transparent)
   │                                                             │
   │   🟣  [Bold Icon Orb]   Title in ExtraBold 18.sp            │  <-- 2. Translucent Purple Glass Fill
   │                         Subtitle in SemiBold Lavender       │      (#18112E @ 68% + Radial Purple Tint)
   │                                                             │
   │   ╭─────────────────────────────────────────────────────╮   │  <-- 3. Continuous Squircle Curvature
   │   │       ⚡ Primary Royal Purple Pill Button           │   │      (28.dp Outer Card, 999.dp Inner Pill)
   │   ╰─────────────────────────────────────────────────────╯   │
   ╰─────────────────────────────────────────────────────────────╯  <-- 4. Ambient Purple Drop Glow Shadow
```

### ✨ Liquid Glass Ki 4 Technical Khasiyat (Jo Hum Code Mein Lagayenge):
1. **Continuous Corner Geometry (Squircle Look):**
   * Abhi ke app me `14.dp`, `16.dp`, `20.dp` ke random corners hain.
   * Naye UI me strict **3-Radius System** hoga:
     * **Main Glass Cards & Dialogs:** `RoundedCornerShape(28.dp)`
     * **Inner Nested Tiles / Setting Rows Group:** `RoundedCornerShape(20.dp)`
     * **All Buttons, Chips, Search Bars & Dock:** `RoundedCornerShape(percent = 50)` (**Pure Pill Shape**)
2. **Diagonal Specular Rim Lighting (Chamakdar Glass Kinara):**
   * Har card ke charo taraf flat border nahi hoga.
   * Hum `Brush.linearGradient(colors = listOf(Color.White.copy(alpha = 0.35f), Color(0xFFA855F7).copy(alpha = 0.40f), Color.White.copy(alpha = 0.05f)))` ka `1.dp` border lagayenge, jisse card bilkul iPhone ke iOS 26 Control Center ke kaanch (glass) jaisa chamkega.
3. **Inner Top Lensing Highlight (Kaanch Ki Upar Wali Chamak):**
   * Har card ke andar top `36.dp` hisse me `Color.White.copy(alpha = 0.06f)` se `Color.Transparent` ka vertical sheen hoga.
4. **Ambient Purple Backlight Mesh (Piche Ka Glow):**
   * Screen ke piche (`PurpleVoidBg #070510`) top-center aur bottom-right me halka sa **Royal Purple Radial Blur Glow (`#8B5CF6` at 18% alpha)** hoga. Jab user scroll karega aur glass cards us glow ke upar se guzrenge, to real translucent refraction dikhega!

---

# 🔤 PART 4: Bold Typography System (Patle Fonts Khatam — Full Bold iOS Look!)

Aapne bilkul sahi pakda: **abhi typography bilkul weak aur patli hai, aur 5 alag-alag fonts (`Orbitron`, `Rajdhani`, `ShareTechMono`, etc.) aapas me lad rahe hain.**

### ✅ Naya 2-Font Bold Rule:
1. **Primary UI & Display Font:** **`Plus Jakarta Sans`** (Modern geometric Apple SF-Pro vibe) — **Sirf `ExtraBold (W800)`, `Bold (W700)`, aur `SemiBold (W600)` weights!**
2. **Numeric / Telemetry / Code Font:** **`Space Grotesk Bold`** (Sirf numbers, latency `ms`, battery `%`, aur timer ke liye).
3. **`Orbitron`, `Rajdhani`, aur `FontWeight.Normal` (W400) ko hum 100% HATA denge!**

### 📏 Complete Bold Typography Scale (`Type.kt` Blueprint)

| Text Style | Kahan Use Hoga | Font Weight | Size (`sp`) | Line Height | Letter Spacing | Color |
| :--- | :--- | :---: | :---: | :---: | :---: | :--- |
| **`displayLarge`** | Screen Hero Titles, Big Metrics | **ExtraBold (`W800`)** | `34.sp` | `40.sp` | `-0.8.sp` | `#F8F7FF` (Pure White) |
| **`headlineLarge`** | Screen Top Header ("Settings", "Agent Hub") | **ExtraBold (`W800`)** | `28.sp` | `34.sp` | `-0.5.sp` | `#F8F7FF` (Pure White) |
| **`headlineMedium`** | Section Headers & Modal Popup Titles | **Bold (`W700`)** | `22.sp` | `28.sp` | `-0.3.sp` | `#F8F7FF` (Pure White) |
| **`titleLarge`** | Glass Card Main Title | **Bold (`W700`)** | `18.sp` | `24.sp` | `-0.2.sp` | `#F8F7FF` (Pure White) |
| **`titleMedium`** | Settings Row Title / Button Text | **Bold (`W700`)** | `16.sp` | `22.sp` | `0.sp` | `#F8F7FF` (Pure White) |
| **`bodyLarge`** | Chat Messages & Main Descriptions | **SemiBold (`W600`)** | `15.sp` | `22.sp` | `0.1.sp` | `#EDE9FE` (Crisp Lavender White) |
| **`bodyMedium`** | Card Subtitles & Setting Descriptions | **Medium-Bold (`W500/W600`)** | `13.5.sp` | `19.sp` | `0.1.sp` | `#C4B5FD` (Soft Lavender) |
| **`labelLarge`** | Pill Buttons & Segmented Tabs | **ExtraBold (`W800`)** | `14.sp` | `18.sp` | `0.4.sp` | `#FFFFFF` |
| **`labelMedium`** | Status Badges & Category Pills | **Bold (`W700`)** | `12.sp` | `16.sp` | `0.6.sp` | `#E9D5FF` |

> **🔥 Kya Farak Padega:**  
> Har Card ka Title **`18.sp Bold`**, har Screen Header **`28.sp ExtraBold`**, har Button **`14.sp ExtraBold`**, aur har Setting Row **`16.sp Bold`** me dikhega. Koi bhi text patla, dabu, ya chota (`9.sp`) nahi rahega!

---

# 🔘 PART 5: Buttons, Cards, Pop-ups & Controls (Har UI Element Ka Exact Design)

Abhi `HudComponents.kt` me jo bikhre hue components hain, unko replace karke hum **6 Standardized Apple iOS Liquid Glass Components** banayenge jo pure app me har jagah uniform rahenge:

---

### 1️⃣ Buttons System (3 Clean Levels — No More Neon Green!)

```
┌─────────────────────────────────────────────────────────────────────┐
│  1. PRIMARY LIQUID PURPLE PILL BUTTON (Main CTA)                    │
│  ╭───────────────────────────────────────────────────────────────╮  │
│  │        ⚡  START AUTONOMOUS TASK  (ExtraBold 15.sp)           │  │
│  ╰───────────────────────────────────────────────────────────────╯  │
│  • Shape: Pure Pill (999.dp) | Height: 54.dp                        │
│  • Fill: Linear Gradient (#9333EA -> #7C3AED -> #A855F7)            │
│  • Border: 1.dp Top Specular White (#FFFFFF @ 40% Alpha)            │
│  • Text: Pure White (#FFFFFF) ExtraBold + Press Bounce (0.96x)      │
├─────────────────────────────────────────────────────────────────────┤
│  2. SECONDARY FROSTED GLASS PILL BUTTON                             │
│  ╭───────────────────────────────────────────────────────────────╮  │
│  │        ⚙️  Configure Model Settings  (Bold 14.sp)             │  │
│  ╰───────────────────────────────────────────────────────────────╯  │
│  • Shape: Pure Pill (999.dp) | Height: 50.dp                        │
│  • Fill: Frosted Purple Glass (#261B47 @ 70% Alpha)                 │
│  • Border: 1.dp Lavender Specular Rim (#C084FC @ 45% Alpha)         │
│  • Text: Soft Lavender White (#F3E8FF) Bold                         │
├─────────────────────────────────────────────────────────────────────┤
│  3. DANGER / STOP GLASS PILL BUTTON (Sirf Stop/Delete Ke Liye)      │
│  ╭───────────────────────────────────────────────────────────────╮  │
│  │        🛑  Emergency Kill Switch  (Bold 14.sp)                │  │
│  ╰───────────────────────────────────────────────────────────────╯  │
│  • Fill: Translucent Rose Glass (#4C1127 @ 75% Alpha)               │
│  • Border: 1.dp Rose Rim (#FB7185 @ 55% Alpha)                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

### 2️⃣ Cards System (Apple iOS Grouped Glass Cards)
1. **`LiquidGlassHeroCard` (`28.dp` Squircle):**
   * Top card on each screen (jaise Home par **Jarvis Purple Liquid Orb Card**, Settings par **AI System Health & Profile Card**).
   * Piche subtle radial purple spotlight glow + `1.2.dp` specular border.
2. **`LiquidGlassGroupCard` (`24.dp` Squircle — iOS Settings Group Style):**
   * Ek alag card har choti setting ke liye banane ke bajay (jisse screen par 20 bikhre hue boxes ban jate hain), hum **Apple iOS Settings ki tarah Grouped Glass Containers** banayenge:
   * Ek `LiquidGlassGroupCard` ke andar 3–5 clean rows hongi, jinke beech me `0.5.dp` ka subtle lavender glass divider (`#FFFFFF @ 8%`) hoga!
3. **`LiquidGlassBentoTile` (`24.dp` Squircle):**
   * Quick Actions (2x2 Grid) ke liye clean square-ish glass tiles, jinke top-left me `44.dp` ka glowing purple squircle icon container hoga aur niche **Bold `16.sp`** title.

---

### 3️⃣ Pop-ups, Modals & Bottom Sheets (Apple iOS Frosted Sheet Style)
Abhi ke app me pop-ups ya to gayab hain ya inline cards me khul jate hain. Naye UI me **3 Standard iOS Glass Pop-ups** honge:
1. **iOS Frosted Modal Bottom Sheet (`LiquidGlassBottomSheet`):**
   * Upar `36.dp x 5.dp` ka Frosted Pill Drag Handle.
   * Corner Radius: `topStart = 32.dp, topEnd = 32.dp`.
   * Background: `#1E1438` @ `92%` alpha + `1.dp` top white-purple specular rim.
   * **Kahan Use Hoga:** Voice Selector, AI Model Picker, Companion Mode Selector, Workflow Step Editor, aur Memory Inspector.
2. **iOS Center Glass Alert Popup (`LiquidGlassDialog`):**
   * `28.dp` squircle center modal with blurred dark purple backdrop scrim (`#05030A @ 75%`).
   * **Kahan Use Hoga:** High-Risk Action Biometric Approval Gate, Delete Confirmation, API Key Verification Result.
3. **Floating Dynamic Island Toast Banner (Top Glass Pill):**
   * Jab koi setting save ho ya background agent action le, to upar iPhone ke **Dynamic Island** jaisa ek glowing purple-black pill capsule slide hokar aayega!

---

# 🧭 PART 6: Complete Professional App Structure (Kaunsa Screen Kahan Hona Chahiye?)

Abhi aapke app me **14 screens** bina kisi dimag ke bikhre hue hain (`API_SETTINGS` alag, `GENERAL_SETTINGS` alag, `CONTROLS` alag, `WORKFLOW` alag, `VISION` alag).  
Web research (ChatGPT iOS, Claude iOS, Perplexity iOS, Apple Intelligence UI) ke hisab se ek **World-Class Autonomous AI App ka structure 5 Main Pillars** par chalta hai:

---

### 🗺️ New Unified Information Architecture Diagram

```
                        ╔═══════════════════════════════════════╗
                        ║   🔮 FLOATING iOS LIQUID GLASS DOCK   ║
                        ║         (5 Main Bottom Tabs)          ║
                        ╚═══════════════════╦═══════════════════╝
                                            │
       ┌──────────────────┬─────────────────┼─────────────────┬──────────────────┐
       ▼                  ▼                 ▼                 ▼                  ▼
┌─────────────┐   ┌──────────────┐   ┌─────────────┐   ┌──────────────┐   ┌─────────────┐
│  1. 🏠 HOME │   │  2. 💬 CHAT  │   │ 3. 🎙️ LIVE  │   │ 4. 🤖 AGENT  │   │ 5. ⚙️ SETUP │
│  Command    │   │  & Multimod  │   │  Voice Orb  │   │  Control Hub │   │  All-in-One │
│  Center     │   │  Workspace   │   │  (Center)   │   │  & Browser   │   │  Settings   │
└──────┬──────┘   └──────┬───────┘   └──────┬──────┘   └──────┬───────┘   └──────┬──────┘
       │                 │                  │                 │                  │
  • Live Orb        • AI Chat          • 3D Purple       • Autonomous       • 🔑 API & Models
    Status Hero     • 📸 Live Vision     Liquid Orb        Task Runner      • 🗣️ Voice & Persona
  • Quick Voice       Camera Mode      • Real-Time       • 🌐 WebView       • 💕 Companion Hub
    & Text Bar      • 📄 Document        Waveform          Agent Browser    • 🎧 Wake Word & Mic
  • 2x2 Bento         & PDF Reader     • Emotion Mood    • ⚡ Workflows      • 🛡️ Permissions &
    Quick Launch    • 🧠 Context         Pill Switcher     & Macro Builder    Device Control
  • Active Task       Memory Drawer    • Barge-In &      • 🔔 Smart Notif   • 🧠 Memory & Vault
    Dynamic Card                         Transcript        Auto-Reply       • 🎨 Appearance
```

---

### 📱 Screen-by-Screen Detailed Breakdown (Har Screen Mein Kya Hoga?)

#### 🏠 TAB 1: HOME SCREEN (Clean Command Center — 1,296 Lines Ka Kachra Khatam!)
Home screen par ab 20 cards ka lamba scroll nahi hoga. Sirf **4 Clean, Bold Sections** honge jo ek hi nazar me samajh aayenge:
1. **Top Floating Glass Header:**
   * Left: **"JARVIS"** (`28.sp ExtraBold`) + Active Persona Badge Pill (e.g., `💜 Commander` ya `💘 Romantic`).
   * Right: **System Ready Dot** (`🟢 Online`) + Quick Notification Bell Orb (`48.dp` glass circle).
2. **Centerpiece: Liquid Purple AI Core Card (`28.dp` Hero Glass Card):**
   * Beech me glowing **Royal Purple Liquid Orb** (jo tap karne par sidha Live Voice start karega).
   * Niche **1-Tap Quick Command Pill Bar** (`"Ask anything or give a phone command..."` + Mic + Camera icon).
3. **2x2 Bento Liquid Glass Grid (4 Primary Capabilities):**
   * **Tile 1 (`📸 Vision AI`):** Live Camera & Screen Analysis (opens Vision Sheet/Mode).
   * **Tile 2 (`🤖 Phone Control`):** Run Autonomous Multi-App Task (opens Agent Task Sheet).
   * **Tile 3 (`⚡ Workflows`):** 1-Tap Saved Routines & Scheduled Automations.
   * **Tile 4 (`🧠 Memory Vault`):** What Jarvis Remembers About You (`5-Tier Memory`).
4. **Live Activity / Proactive Card:**
   * Agar koi background agent task chal raha hai ya smart notification intercept hua hai, to wo yahan ek sleek iOS Live Activity card me dikhega.

---

#### 💬 TAB 2: CHAT & MULTIMODAL STUDIO (`Chat`, `Vision`, `Document` Ek Sath!)
Abhi `CHAT`, `VISION`, aur `DOCUMENT` 3 alag-alag bikhre hue screens hain. Real professional apps (jaise ChatGPT / Claude iOS) me ye teeno **ek hi Unified Multimodal Workspace** me hote hain:
* **Top Segmented Glass Pill Bar:** `[ 💬 Chat ]  [ 📸 Live Vision ]  [ 📄 Doc Reader ]`
* **Chat Bubbles:**
  * User Bubble: **Royal Purple Gradient Pill Card (`#8B5CF6` -> `#6D28D9`)** with bold white text.
  * AI Bubble: **Frosted Dark Violet Glass Card (`#18112E`)** with specular rim border + Copy/Speak/Action buttons.
* **Bottom Floating iOS Input Capsule:**
  * Left `+` Glass Button (Attach Photo / PDF / Screen Capture) + Pill Input + Right Purple Voice/Send Button.

---

#### 🎙️ TAB 3: LIVE VOICE SCREEN (Center Dock Button — Full-Screen iOS Siri/Voice Experience)
* **Full-Screen Ambient Purple Liquid Glass Experience:**
  * Beech me bada **3D Multi-Ring Royal Purple & Electric Orchid Orb** jo aapke bolne aur AI ke bolne par liquid wave ki tarah dhadkega.
  * Top Glass Pill: Active Model (`Gemini 2.5 Flash Live`) + Latency (`ms`) + Current Emotion/Persona Chip.
  * Center Live Caption Glass Box: Bold `20.sp` real-time transcript taaki saaf dikhe ki aapki aawaj kya suni gayi aur AI kya bol raha hai.
  * Bottom Floating Control Dock (`4 Glass Orbs`):
    1. **🎙️ Mic Mute/Unmute (`56.dp` Glass Orb)**
    2. **📷 Turn On Camera Vision (`56.dp` Glass Orb)**
    3. **🎭 Switch Persona / Mood (`56.dp` Glass Orb — opens iOS Bottom Sheet)**
    4. **🔴 End Live Session (`64.dp` Rose-Red Glass Pill)**

---

#### 🤖 TAB 4: AGENT HUB (`Agent`, `Browser`, `Workflows`, `Controls`, `Notifications` Ek Sath!)
Abhi phone control ke features 5 alag screens me bikhre pade hain. Ab `AgentHubScreen` me upar ek **iOS Liquid Glass Segmented Bar** hoga jisme 4 clean tabs honge:
1. **`🎯 Live Agent`:** Autonomous Goal Runner + Live Execution Step Timeline + Visual Annotated Screen Preview + Biometric Approval Gate.
2. **`🌐 AI Browser`:** WebView Autonomous Agent (`BrowserScreen` seamlessly integrated).
3. **`⚡ Workflows`:** Multi-App Macro Recorder, Cron Scheduler & Saved Routines (`WorkflowBuilderScreen` integrated).
4. **`🎛️ Device & Notifs`:** Quick Device Toggles (Wi-Fi, Torch, Volume, DND) + Smart Notification Auto-Reply Rules (`DeviceControlsScreen` + `NotificationCenterScreen` integrated).

---

#### ⚙️ TAB 5: UNIFIED SETTINGS HUB (Sabse Bada Sudhar — Sare Settings Ek Jagah!)
Aapne special bola tha: ***"matlab settings screen me sare ke sare settings ho aur aise hi or kya kya section hona chahiye aap sahi-sahi batao"***.

Abhi aapke app me `ApiSettingsScreen` aur `GeneralSettingsScreen` alag-alag hain, aur `GeneralSettingsScreen` me 1,715 lines ka lamba bina-structure ka kachra hai.  
Naye design me **Apple iPhone Settings App** ki tarah ek **Unified Settings Hub** hoga jisme **7 Clean Grouped Sections** honge, aur har section par tap karne par uska clean **iOS Liquid Glass Detail Page / Bottom Sheet** khulega!

```
╔══════════════════════════════════════════════════════════════════════════╗
║  ⚙️  SETTINGS                                            [Save Changes]  ║
╠══════════════════════════════════════════════════════════════════════════╣
║  ╭────────────────────────────────────────────────────────────────────╮  ║
║  │  🔮 JARVIS AI PRO CORE                          🟢 All Systems Go  │  ║
║  │  Active Model: Gemini 2.5 Flash • Voice: Aoede • Mode: Commander   │  ║
║  ╰────────────────────────────────────────────────────────────────────╯  ║
║                                                                          ║
║  🔑 SECTION 1: AI ENGINE & API KEYS (Pehle Ye Alag Screen Par Tha!)      ║
║  ╭────────────────────────────────────────────────────────────────────╮  ║
║  │  🟣 Gemini API Keys & Multi-Key Rotation              3 Keys   ›   │  ║
║  │  ────────────────────────────────────────────────────────────────  │  ║
║  │  🧠 Default Chat, Live Voice & Vision Models        2.5 Flash  ›   │  ║
║  │  ────────────────────────────────────────────────────────────────  │  ║
║  │  🌐 Custom OpenAI / Ollama / Local LLM Endpoint          Off   ›   │  ║
║  ╰────────────────────────────────────────────────────────────────────╯  ║
║                                                                          ║
║  🗣️ SECTION 2: VOICE, AUDIO & WAKE WORD                                 ║
║  ╭────────────────────────────────────────────────────────────────────╮  ║
║  │  🎙️ AI Voice Studio (Voice Name, Speed, Pitch)         Aoede   ›   │  ║
║  │  ────────────────────────────────────────────────────────────────  │  ║
║  │  👂 "Hey Jarvis" Wake Word & Sensitivity            Always On  ›   │  ║
║  │  ────────────────────────────────────────────────────────────────  │  ║
║  │  🔊 Audio Duplex, Echo Cancellation & Barge-In        Active   ›   │  ║
║  ╰────────────────────────────────────────────────────────────────────╯  ║
║                                                                          ║
║  💕 SECTION 3: PERSONALITY & COMPANION MODES                             ║
║  ╭────────────────────────────────────────────────────────────────────╮  ║
║  │  🎭 Active Persona Mode (Commander / Companion / Custom)       ›   │  ║
║  │  ────────────────────────────────────────────────────────────────  │  ║
║  │  💘 Romantic & 18+ Adult Intimate Mode Controls     Unlocked   ›   │  ║
║  │  ────────────────────────────────────────────────────────────────  │  ║
║  │  💬 Proactive Check-Ins & Emotional Tone Settings     High     ›   │  ║
║  ╰────────────────────────────────────────────────────────────────────╯  ║
║                                                                          ║
║  🤖 SECTION 4: AUTONOMOUS AGENT & SYSTEM PERMISSIONS                     ║
║  ╭────────────────────────────────────────────────────────────────────╮  ║
║  │  ♿ Accessibility Service & Screen Control           Enabled   ›   │  ║
║  │  ────────────────────────────────────────────────────────────────  │  ║
║  │  🖥️ Floating Overlay Bubble & Background Service      Active   ›   │  ║
║  │  ────────────────────────────────────────────────────────────────  │  ║
║  │  ⚡ Self-Healing Loop, ReAct Steps & Vision Speed   Fast Mode  ›   │  ║
║  ╰────────────────────────────────────────────────────────────────────╯  ║
║                                                                          ║
║  🛡️ SECTION 5: SECURITY, BIOMETRICS & PRIVACY VAULT                     ║
║  ╭────────────────────────────────────────────────────────────────────╮  ║
║  │  🔐 Biometric Gate for Payments & High-Risk Actions   Required ›   │  ║
║  │  ────────────────────────────────────────────────────────────────  │  ║
║  │  🕵️ Incognito Screen Filter (Hide Banking/OTP Apps)   Active   ›   │  ║
║  │  ────────────────────────────────────────────────────────────────  │  ║
║  │  🔑 Encrypted Credential Vault (Auto-Sign-In)        Manage    ›   │  ║
║  ╰────────────────────────────────────────────────────────────────────╯  ║
║                                                                          ║
║  🧠 SECTION 6: MEMORY, BACKUP & DATA                                     ║
║  ╭────────────────────────────────────────────────────────────────────╮  ║
║  │  🧬 5-Tier Memory Manager & Fact Review               View     ›   │  ║
║  │  ────────────────────────────────────────────────────────────────  │  ║
║  │  ☁️ Export / Import Full Encrypted Backup (.json)     Backup   ›   │  ║
║  │  ────────────────────────────────────────────────────────────────  │  ║
║  │  🗑️ Clear Chat History / Reset Memory Vault           Reset    ›   │  ║
║  ╰────────────────────────────────────────────────────────────────────╯  ║
╚══════════════════════════════════════════════════════════════════════════╝
```

### 🌟 Is Unified Settings Structure Ka Sabse Bada Fayda:
1. **Koi Setting Gum Nahi Hogi:** Top par ek **iOS Frosted Search Bar (`🔍 Search any setting: API Key, Wake Word, Voice...`)** hoga. User jaise hi `"API"` ya `"Voice"` type karega, turant wahi setting samne aa jayegi!
2. **`ApiSettingsScreen` aur `GeneralSettingsScreen` Merge Ho Jayenge:** User ko kabhi confuse nahi hona padega ki API key kahan dalu aur Voice kahan badlu — **Sab kuch `Settings` tab ke andar milega!**
3. **Clean Sub-Sheets:** Har group row par click karne par ek smooth **Apple iOS Frosted Glass Sheet** khulegi jahan user aram se settings change karke save kar sakega — ek hi page par 1,700 lines scroll karne ki bimari hamesha ke liye khatam!

---

# 🛠️ PART 7: File-by-File Implementation Blueprint (Jab Aap "Build Karo" Bologe Tab Hum Kya Karenge)

Jab aap is report ko approve karoge, tab hum bina kisi existing feature/backend logic ko tode niche diye gaye **8 Frontend UI Files** ko upgrade karenge:

| # | File Path | Kya Change Hoga (Exact Action) |
| :-: | :--- | :--- |
| **1** | `app/src/main/java/com/example/ui/theme/Color.kt` | Sare clashing Neon Green (`#A3FF12`), random Cyan/Orange colors hata kar **60-30-10 Royal Purple + Liquid Glass Palette** (`PurpleVoidBg`, `LiquidGlassSurface`, `RoyalPurplePrimary #8B5CF6`, `ElectricOrchid #A855F7`, `SoftLavenderGlow #C084FC`) set karenge. |
| **2** | `app/src/main/java/com/example/ui/theme/Type.kt` | 5 conflicting fonts (`Orbitron`, `Rajdhani`, etc.) hata kar **2-Font Bold Hierarchy** (`Plus Jakarta Sans ExtraBold/Bold/SemiBold` + `Space Grotesk Bold`) lagayenge. |
| **3** | `app/src/main/java/com/example/ui/theme/Theme.kt` | Material 3 `DarkColorScheme` ko 100% **Royal Purple + Obsidian Dark Glass** tokens par bind karenge. |
| **4** | `app/src/main/java/com/jarvisai/assistant/ui/components/HudComponents.kt` | 7 mixed templates (`WealthWave`, `Monex`, etc.) ko clean **Apple iOS 26 Liquid Glass Design System** (`28.dp` squircle cards, `1.dp` diagonal specular rim borders, `999.dp` Royal Purple pill buttons, iOS Segmented Controls, iOS Grouped Settings Rows, aur iOS Bottom Sheets) se replace karenge. |
| **5** | `app/src/main/java/com/jarvisai/assistant/ui/JarvisApp.kt` | **Floating iOS Liquid Glass Bottom Dock** (5 clean tabs: `Home`, `Chat`, `Live Orb`, `Agent Hub`, `Settings`) + Ambient Purple Mesh Backdrop lagayenge. |
| **6** | `app/src/main/java/com/jarvisai/assistant/ui/screens/HomeDashboardScreen.kt` | 1,296-line cluttered scroll ko clean **Apple iOS Command Center** (Top Header + Center Purple Liquid Orb Hero + 2x2 Bento Quick Launch Grid + Live Activity Card) me redesign karenge. |
| **7** | `app/src/main/java/com/jarvisai/assistant/ui/screens/GeneralSettingsScreen.kt` & `ApiSettingsScreen.kt` | Dono settings screens ko merge karke **1 Unified Apple iOS Grouped Settings Hub** banayenge jisme Search Bar + 6 Categorized Glass Groups + Sub-Sheets honge (API Keys, Models, Voice, Wake Word, Persona/18+ Companion, Permissions, Security, aur Memory sab ek jagah!). |
| **8** | `ChatScreen.kt`, `LiveVoiceScreen.kt`, `AgentHubScreen.kt` | Unified **Chat + Vision + Doc Segmented Header**, **Agent + Browser + Workflows + Controls Segmented Hub**, aur **3D Royal Purple Liquid Voice Orb** apply karenge. |

---

## ✅ Final Verification Checklist (100% Feature Safety Guarantee)
* 🔒 **Zero Feature Loss:** Aapke sare 10 Autonomous Agentic Features, Accessibility Action Engine, Fast Vision Mode, 5-Tier Memory, Wake Word, aur Romantic/18+ Companion Mode ka **ek bhi backend function touch ya delete nahi hoga**.
* 💜 **100% Royal Purple + Apple iOS Liquid Glass:** Koi bhi Neon Lime Green ya bikhra hua card nahi bachega.
* 🔤 **100% Bold Typography:** Har heading, card title, button, aur setting row crisp aur bold dikhegi.
* ⚙️ **100% Unified Settings:** Sare ke sare settings ek hi clean iOS Settings screen ke andar milenge.
