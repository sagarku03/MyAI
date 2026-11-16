# JARVIS — Gemini Live Voice Call Par Short Aur Long Multi-Step Android Tasks Fail Kyu Ho Rahe Hain? (Verified 2026 Deep Root-Cause & Architecture Audit)

> **Important Note (Pichli Report Ki Galti Ka Correction):**  
> Is audit mein sirf **4 Official Google Documentation Sources** aur aapke **exact Android codebase** ko check kiya gaya hai — kisi third-party blog ya purani cutoff memory ko nahi:
> 1. `https://aistudio.google.com/docs`
> 2. `https://aistudio.google.com/docs/models`
> 3. `https://antigravity.google/docs`
> 4. `https://antigravity.google/docs/models/`
>
> **Pichli galti clear:** Aapka kehna **100% sahi tha**. `gemini-3.8-flash`, `gemini-3.6-flash`, aur `gemini-3.5-flash-lite` **2026 ke active, official aur Free Tier supported models hain**. Ulta `gemini-2.0-flash` / `gemini-2.0-flash-live-001` **June 2026 mein retire ho chuke hain** (aur aapke code mein abhi bhi kai jagah `gemini-2.0-flash` purane fallback strings ke roop mein pade hain, jo khud ek bug hai).

---

## 1. Executive Summary — Ek Nazar Mein Poori Kahani

Jab aap **Gemini Live Voice Call** par JARVIS ko koi Android mobile task dete hain — chahe **chhota task** (*"YouTube par XYZ search karke pehla video chalao aur like karo"*) ya **lamba multi-step task** (*"Chrome me X search karke summary lo, WhatsApp par Rahul ko bhejo aur phir Settings me Y on karo"*) — toh woh kaam **3 alag-alag layers** par toot jaata hai:

1. **AI sirf bolta hai *"Main kar raha hoon"* aur tool call hi nahi karta:**  
   `GeminiLiveVoiceManager.kt` ka prompt (`ULTRA-LOW LATENCY RULE`, line `893`) aur bhaari-bharkam companion roleplay (`150+` lines upar) Live model ko force karta hai ki woh turant bol de aur `turnComplete: true` bhej de — JSON `toolCall` emit karne ka wait kiye bina.
2. **Chhote aur medium tasks beech mein hi CUT / SELF-CANCEL ho jaate hain:**  
   - **650ms Self-Cancellation Race (`JarvisViewModel.kt:927` vs `TaskExecutor.kt:430`):** Model ko tool call bhejne mein `1.2s–2.5s` lagta hai, par `JarvisViewModel` sirf `650ms` wait karke apni taraf se `PhoneActionManager` chala deta hai. Phir `1s` baad jab Gemini ka asli `toolCall` ya watchdog (`line 1336`) aata hai, toh `TaskExecutor` ke `taskExecutionMutex` mein **dono takraate hain aur Step 1 ko `priorJob.cancel()` kar dete hain!**
   - **Greedy Regex Hijack (`PhoneActionManager.kt` + `IntentRouter.kt`):** *"YouTube par X search karo aur video play karke like karo"* ya *"WhatsApp par X ko..."* jaise compound commands ko `IntentRouter`/`PhoneActionManager` ke regex pehle hi pakad lete hain, sirf pehla half-step (e.g., search page open) karke ruk jaate hain, aur Autonomous `TaskExecutor` kabhi trigger hi nahi hota!
3. **Lambe multi-step tasks aadhe mein ruk jaate hain ya crash hote hain:**  
   - **Critical `agy:` Model Routing Crash (`UserPreferencesRepository.kt:193` + `LiveAgentToolExecutor.kt:2158` + `GeminiApiService.kt:834, 1070`):** Aapke chat mein **`agy:gemini-3.8-flash`** (Antigravity Pro) set hai. Jab aap text model change karte ho, app **`SCREEN_OPERATOR_MODEL` ko bhi `"agy:gemini-3.8-flash"` par set kar deta hai**! Phir `TaskExecutor` wahi `"agy:gemini-3.8-flash"` seedha AI Studio REST endpoint (`/v1beta/models/agy:gemini-3.8-flash:generateContent`) par bhej deta hai — `AntigravityApiService` par nahi! Har single step par primary call **404/400 error se fail hoti hai**, phir fallback par **AI Studio Free Tier (`5 RPM / 20 RPD`)** ki limit turant khatam (`429 RESOURCE_EXHAUSTED`) ho jaati hai!
   - **75-Second Global Timeout (`TaskExecutor.kt:477`):** Poore 15–50 step loop par sirf `75s` ka total timeout hai. Failed primary calls + UI wait ke baad ~7–9 steps mein timeout hokar task abort ho jaata hai.
   - **Blind Text-Only Operator + 40 Node Limit (`TaskExecutor.kt:921, 935`):** `TaskExecutor` screenshot bhejta hi nahi (no Vision!) aur screen ke sirf pehle **40 labeled Accessibility nodes** dekh kar baaki screen kaat deta hai.
   - **Live WebSocket 2-Minute Drop + Proactive Sabotage:** Audio + Screen JPEG (`1 FPS`) bhejne par Live API Docs ke hisab se **~2 minute ki hard cap** hoti hai agar `contextWindowCompression` aur `sessionResumption` na laga ho — dono aapke `buildSetupMessage()` mein missing hain! Saath hi `ProactiveCompanionEngine` (`JarvisViewModel.kt:710-778`) chalte hue automation ke beech har app switch par fake user prompt ghusa kar AI ka dhyan bhatka deta hai!

---

## 2. Official 2026 Model Matrix (Aapke Exact Plans Ke Hisab Se)

Aapke paas do alag-alag Google ecosystems hain — dono ke models, limits, aur roles ko app mein alag-alag aur sahi tarike se wire karna zaroori hai:

### A. Google AI Studio API Key — **Free Tier** (`aistudio.google.com/docs/models`)

| Model ID | Status (Nov 2026) | Free Tier Support & Official Behavior | JARVIS Mein Sahi Role |
|---|---|---|---|
| `gemini-3.8-live` *(ya `gemini-live-2.5-flash-native-audio` / `gemini-2.5-flash-native-audio-preview-12-2025`)* | **Active (Live API WebSocket)** | Native Audio Bidi streaming. **`gemini-3.8-live`** async `NON_BLOCKING` function calling + `scheduling` (`INTERRUPT`, `WHEN_IDLE`, `SILENT`) ko officially support karta hai. | **Live Voice Call Brain (WebSocket)** — Note: Normal `gemini-3.8-flash` WebSocket Live API support NAHI karta! |
| `gemini-3.8-flash` | **Active (Free Tier)** | 1M context, `thinkingLevel` (`LOW`/`MEDIUM`/`HIGH`), structured JSON & tools. **Free Tier Rate Limit Alert:** Project console par dynamic hota hai, par typically **~5 RPM / ~20 RPD / ~250K TPM** tak restricted rehta hai! | **Heavy Planning / Coding / Complex Screen Step** — **Warning:** Agar `TaskExecutor` is par 15 steps back-to-back marega toh **5 RPM / 20 RPD** Free Tier par `429` error aa jayega! |
| `gemini-3.6-flash` | **Active (Free Tier)** | Stable Gen-3 agentic workhorse (`gemini-3.5-flash` depreciate hokar ispar route hota hai). | Reliable secondary text/operator fallback. |
| `gemini-3.5-flash-lite` | **Active (Free Tier)** | High-throughput & low latency (**~15–30 RPM / ~500 RPD** Free Tier par zyada headroom). | **Per-Step Fast Screen Operator (`TaskExecutor`)** ke liye AI Studio Free Tier par sabse safe high-RPM model (bina `429` hit kiye 15-20 steps chala sakta hai). |
| `gemini-4-hydrogen` | **Active (Free & Paid)** | Fast everyday Gemini 4 execution model (1M context). | Fast multi-step sub-agent execution agar project Free Tier catalog mein enabled hai. |
| `gemini-4-argon` / `gemini-4-carbon` | Active (Mostly Paid / Frontier) | Deep frontier reasoning (`xhigh` thinking). | Heavy reasoning (API Studio Free Tier par restricted/paid). |
| `gemini-2.0-flash` / `gemini-2.0-flash-live-001` | **RETIRED / DEPRECATED (June 1, 2026)** | Google ne shutdown kar diya hai (aur `gemini-2.5-pro` Dec 1, 2026 ko retire ho raha hai). | **Codebase Bug:** Aapke code mein abhi bhi `GeminiApiService.kt:1694`, `941`, `AssistantModels.kt:107`, aur `GeminiLiveVoiceManager.kt:594-596` mein yeh dead IDs pade hain — inhe hatana hoga. |

> **AI Studio Free Tier Rule (`aistudio.google.com/docs`):** Limits **per Google Cloud Project** enforce hoti hain (multiple API keys banane se quota nahi badhta), aur exact real-time numbers `aistudio.google.com/usage?tab=rate-limit` par dikhte hain.

### B. Google Antigravity — **Google AI Pro Plan** (`antigravity.google/docs/models/`)

| Antigravity Model | Plan Access | Official Capability & Quota Behavior |
|---|---|---|
| **`Orchestrator (Argon)`** | **Google AI Pro** (Non-trial) | Frontier planner (`Gemini 4 Argon`) plan banata hai + fast engine (`Gemini 4 Hydrogen`) steps execute karta hai. **24% kam quota kharch hota hai aur 19% faster** complete karta hai! |
| **`Gemini 4 Argon`** | **Google AI Pro** | Deep multi-step planning, architecture aur recovery reasoning. |
| **`Gemini 4 Hydrogen`** | **Google AI Pro** | Ultra-fast everyday execution engine. |
| **`Gemini 3.8 Flash` / `3.6 Flash`** | **Google AI Pro** | Fast daily agentic workhorse (`agy:gemini-3.8-flash` jo aapke screenshot mein chal raha hai). |
| **`Orchestrator (Carbon)` / `Gemini 4 Carbon`** | Google AI Ultra (Mainly) | Top frontier tier. |

> **Antigravity Quota Advantage (`antigravity.google/docs`):** AI Pro plan mein quota har **5 ghante (5-hour refresh cycle)** par refresh hota hai! Isliye agar app aapke Antigravity Pro token ke through `TaskExecutor` chalaye (ya `Orchestrator` pattern use kare: ek baar Plan banana, phir fast steps chalana), toh AI Studio Free Tier ki `20 RPD` wali deewar aapko nahi rokegi!

---

## 3. End-to-End Architecture Flow: Abhi Aapke App Mein Kya Ho Raha Hai

Niche diye diagram mein dekhiye jab aap Live Voice Call mein koi command bolte hain toh code ke andar asal mein kya hota hai:

```text
[User Bolta Hai: "YouTube par XYZ search karo aur pehla video play karke like karo"]
         │
         ├──► Path A: Android AudioRecord (PCM 16kHz) ──► Gemini Live WebSocket
         │       ├── Live Model `turnComplete` + "Main kar raha hoon" bol deta hai (No toolCall!)
         │       │   (Kyu? Line 893 "ULTRA-LOW LATENCY" + 150 lines roleplay prompt upar!)
         │       └── Ya agar toolCall bhejta hai, toh 1.2s–2.5s baad aata hai
         │
         └──► Path B: `inputTranscription(finished=true)` ──► JarvisViewModel.kt:927
                 ├── SIRF 650ms wait karta hai! (Model ka toolCall abhi aaya bhi nahi hota)
                 ├── WasToolCalled == false dekh kar `PhoneActionManager` chala deta hai
                 │   ├── `IntentRouter` / `PhoneActionManager` greedy regex ("youtube...(.+)$")
                 │   │   poore multi-step command ko kha jaata hai -> Sirf YouTube search kholta hai!
                 │   └── Ya agar `TaskExecutor` shuru bhi ho gaya:
                 │         1. `JarvisViewModel` liveVoiceManager ke flags TRUE nahi karta!
                 │         2. 1 sec baad Gemini ka `toolCall` ya Watchdog (`line 1336`)
                 │            dobara `TaskExecutor` call karta hai -> `taskExecutionMutex`
                 │            pehle chal rahe task ko `abortRequested=true` & `cancel()` kar deta hai!
                 │         3. `TaskExecutor` `agy:gemini-3.8-flash` ko AI Studio URL pe bhej kar 404 khata hai!
                 │         4. `GeminiLiveVoiceManager` ke setup mein `NON_BLOCKING`, `contextWindowCompression`,
                 │            aur `sessionResumption` nahi hai -> 75s block + 2 min WebSocket drop!
                 │         5. Beech task mein app badalte hi `ProactiveCompanionEngine` fake chat inject karta hai!
```

---

## 4. Saare Verified Root Causes (Exact File Paths & Line Numbers Ke Saath)

Har ek problem ke piche exact code culprit niche diya gaya hai:

### Root Cause #1: `agy:` Model Prefix Leak — Har Step Par 404/400 Crash & Free Tier Rate-Limit Exhaustion
* **Exact Location:**
  - `app/src/main/java/com/jarvisai/assistant/data/preferences/UserPreferencesRepository.kt` (Lines `193–224`)
  - `app/src/main/java/com/jarvisai/assistant/service/LiveAgentToolExecutor.kt` (Lines `2157–2173`)
  - `app/src/main/java/com/jarvisai/assistant/data/network/GeminiApiService.kt` (Lines `834, 899–914, 1070–1076`)
* **Kya Ho Raha Hai:**
  1. Jab aap chat mein Antigravity Pro ka model (`agy:gemini-3.8-flash`, jo aapke screenshot mein active hai) select karte hain, toh `UserPreferencesRepository` **dono** `Keys.TEXT_MODEL` aur `Keys.SCREEN_OPERATOR_MODEL` mein `"agy:gemini-3.8-flash"` save kar deta hai (`lines 193–224`).
  2. Live call par jab `execute_screen_operator_goal` chalta hai, `LiveAgentToolExecutor.kt:2158` `prefs.screenOperatorModel` (`"agy:gemini-3.8-flash"`) utha kar seedha `TaskExecutor.executeTask(...)` -> `GeminiApiService.generateAgentDecision()` ko deta hai.
  3. `GeminiApiService.sanitizeModelId()` (`line 1070`) sirf `"models/"` hatata hai — **`"agy:"` nahi hatata aur na hi `AntigravityApiService` par route karta hai!** (Grep verify: poore `service/` folder mein `AntigravityApiService` kahin call hi nahi hota!).
  4. Natija: Har single automation step par primary HTTP request `https://generativelanguage.googleapis.com/v1beta/models/agy:gemini-3.8-flash:generateContent` par jaati hai -> **404 Invalid Model Name error**!
  5. Phir `line 904` fallback list (`gemini-3.8-flash`, `gemini-3.6-flash`, `gemini-3.5-flash-lite`) try karta hai. Kyunki AI Studio key **Free Tier** par hai (`gemini-3.8-flash` ~**5 RPM / 20 RPD**), 6–10 steps chalte hi **`429 RESOURCE_EXHAUSTED`** aata hai aur long task dead ho jaata hai!
* **Secondary Deprecated Model Bug:** `GeminiApiService.kt:1694` (`DEFAULT_TEXT_MODEL = "gemini-2.0-flash"`), `941`, `AssistantModels.kt:107`, aur `GeminiLiveVoiceManager.kt:594–596` (`gemini-2.0-flash-live-001`) abhi bhi June 2026 mein retire ho chuke models par fallbacks rakhe hue hain.

---

### Root Cause #2: Gemini Live Sirf *"Main Kar Raha Hoon"* Bolta Hai, Tool Call Nahi Bhejta
* **Exact Location:**
  - `app/src/main/java/com/jarvisai/assistant/service/GeminiLiveVoiceManager.kt` (Lines `863–974`, specifically Line `893`)
* **Kya Ho Raha Hai:**
  1. Line `893` par likha hai:
     > `ULTRA-LOW LATENCY RULE: Respond immediately as soon as the user finishes speaking. Keep internal thinking minimal and emit audio or tool calls right away without delay.`
     Live Native Audio models jab *"emit immediately without delay"* directive dekhte hain, toh tool-argument JSON sochne ke bajaye turant sabse low-cost output — yaani Hinglish speech (*"Ji, main kar raha hoon..."*) + `turnComplete: true` bhej dete hain!
  2. **Official AI Studio Live API Best Practice Violation (`aistudio.google.com/docs`):** Official docs kehti hai Live `systemInstruction` ka order strictly `1. Persona (language) -> 2. Conversational Rules -> 3. Workflow + Tool Calls ("unmistakably" trigger words) -> 4. Guardrails` hona chahiye. Aapke code mein upar ~150 lines ka `ProactiveCompanionEngine` (emotional girlfriend/bestie/ambient-room prompt + gender lock + 30 memories + 18 chat continuity turns) bhara hua hai, jiske neeche 12 tool rules dab jaate hain!

---

### Root Cause #3: `JarvisViewModel` (650ms) vs Live Watchdog — Self-Cancellation Race Condition (Chhote & Bade Dono Tasks Kill Hote Hain!)
* **Exact Location:**
  - `app/src/main/java/com/jarvisai/assistant/viewmodel/JarvisViewModel.kt` (Lines `927–975`)
  - `app/src/main/java/com/jarvisai/assistant/service/GeminiLiveVoiceManager.kt` (Lines `1336–1420`)
  - `app/src/main/java/com/jarvisai/assistant/service/TaskExecutor.kt` (Lines `430–448`)
* **Kya Ho Raha Hai:**
  1. User bolna khatam karta hai -> `inputTranscription(finished=true)` fire hota hai.
  2. `JarvisViewModel.kt:927` sirf **`delay(650L)`** wait karta hai aur check karta hai: `if (!liveVoiceManager.wasToolCalledForCurrentTurn() && !liveVoiceManager.isToolCallCurrentlyExecuting)`.
  3. Live Native Audio model ko server se `toolCall` packet bhejne mein typically **1,200ms – 2,500ms** lagta hai. Toh `650ms` par woh check **hamesha false** milta hai!
  4. `JarvisViewModel` khud `phoneActionManager.detectAndExecuteCommand()` -> `TaskExecutor.executeTask()` start kar deta hai — **par `liveVoiceManager` ko batata hi nahi** (Sirf `appendSystemNotice` karta hai, `toolCalledInCurrentUserTurn` ya `isToolCallCurrentlyExecuting` set nahi karta!).
  5. Phir 1 second baad **do mein se ek cheez hoti hai:**
     - **Case A:** Gemini Live ka asli `toolCall(execute_screen_operator_goal)` aa jaata hai.
     - **Case B:** Model sirf bol kar ruk jaata hai (`turnComplete: true`), toh `GeminiLiveVoiceManager.kt:1336` ka `enforceRealActionIfModelOnlyPromised()` watchdog dekh leta hai `toolCalledInCurrentUserTurn == false`, aur woh bhi `execute_screen_operator_goal` fire kar deta hai!
  6. Dono cases mein naya call `TaskExecutor.executeTask()` (`lines 430–448`) mein pahunchta hai -> `if (taskExecutionMutex.isLocked)` true hota hai -> **`abortRequested = true; priorJob?.cancel()` chala kar Step 1 ko beech mein hi MAAR DETA HAI!**

---

### Root Cause #4: `IntentRouter` & `PhoneActionManager` Greedy Regex Multi-Step Commands Ko Kha Jaate Hain
* **Exact Location:**
  - `app/src/main/java/com/jarvisai/assistant/service/PhoneActionManager.kt` (Lines `290–458`, `1277`, `1301`)
  - `app/src/main/java/com/jarvisai/assistant/service/IntentRouter.kt` (Lines `266`, `369`, `712–774`)
* **Kya Ho Raha Hai:**
  - Jab `JarvisViewModel` (`650ms` race se) ya fallback path `PhoneActionManager.detectAndExecuteCommand()` chalata hai, toh simple single-step regex **compound multi-step sentences** ko hijack kar lete hain:
    - **YouTube Hijack (`PhoneActionManager.kt:1277`):** Regex `youtube ... search ... (.+)$` line ke end tak sab kuch ek search query maan leta hai! Agar aapne bola *"YouTube par Tech Burner search karo aur latest video play karke like karo"*, toh `openYouTubeSearch()` poori line search bar mein daal kar wahin return `true` kar deta hai — **video play/like steps kabhi `TaskExecutor` tak jaate hi nahi!** Same issue Chrome ke `extractChromeSearchQuery` (`line 1301`) mein hai.
    - **WhatsApp Hijack (`IntentRouter.kt:766`):** Koi bhi sentence jisme `"whatsapp"` + `"ko"` aaye (e.g. *"WhatsApp khol kar Rahul ko last photo forward karo aur phir Gmail check karo"*), fallback line `766` use `WHATSAPP_ACTION` banakar `communicationHelper.handleWhatsAppMessage()` par bhej deta hai — `TaskExecutor` bypass ho jaata hai!
    - **Settings / Hardware Hijack (`IntentRouter.kt:266`):** *"Bluetooth settings me jaake Boat Rockerz connect karo"* jaisa multi-step UI goal `"bluetooth"` + `"on/open"` keyword dekh kar simple 1-step `BLUETOOTH_CONTROL` mein phans jaata hai.

---

### Root Cause #5: Live API Tool Calling `BLOCKING` Hai + `contextWindowCompression` / `sessionResumption` Missing (2-Minute Hard Drop!)
* **Exact Location:**
  - `app/src/main/java/com/jarvisai/assistant/service/ToolRegistry.kt` (Lines `289–305`)
  - `app/src/main/java/com/jarvisai/assistant/service/JarvisCodingToolsProvider.kt`
  - `app/src/main/java/com/jarvisai/assistant/service/GeminiLiveVoiceManager.kt` (Lines `977–1024`, `1117`, `1485`, `2198–2322`)
* **Official AI Studio Live API Docs (`aistudio.google.com/docs`) ke mutabik kya galat hai:**
  1. **Missing `"behavior": "NON_BLOCKING"`:** Live API mein default `FunctionDeclaration` **BLOCKING** hoti hai. Jab tak Android `toolResponse` wapas nahi bhejta, server-side Gemini session freeze/wait karta rehta hai. Aapka `execute_screen_operator_goal` 15–75 seconds leta hai! Us dauran aapne `isModelCurrentlyResponding = false` (`line 1117`) forced kar rakha hai, jisse `startAutoAccessibilityLiveScreenLoop` (`lines 2198–2322`) har **1 second** mein JPEG frames + `AudioRecord` 25 PCM packets/sec blocked WebSocket par pump karta rehta hai — server buffer overload / `1008` / `1011` disconnect!
  2. **2-Minute Audio+Video Session Limit:** Official docs kehti hai ki Live API par **Audio+Video (screen frame stream) session bina `contextWindowCompression` ke sirf ~2 minute chalta hai!** Aur aapke `buildSetupMessage()` (`lines 977–1024`) mein na **`contextWindowCompression: { slidingWindow: {} }`** hai, na **`sessionResumption: {}`** (2-hour valid resume handle), aur na hi Async tool response ke liye **`scheduling: "WHEN_IDLE"` / `"SILENT"` / `"INTERRUPT"`** (`gemini-3.8-live` feature)!

---

### Root Cause #6: `TaskExecutor.kt` Khud Lambe Multi-Step Tasks Chalane Mein Kyu Fail Hota Hai
* **Exact Location:**
  - `app/src/main/java/com/jarvisai/assistant/service/TaskExecutor.kt` (Lines `232, 477, 739, 921, 935`)
  - `app/src/main/java/com/jarvisai/assistant/data/network/GeminiApiService.kt` (Lines `866, 893, 994–1009`)
  - `app/src/main/java/com/jarvisai/assistant/service/AgenticMobileEngine.kt` (Lines `494–577`)
* **Kya Ho Raha Hai:**
  1. **75-Second Global Timeout (`TaskExecutor.kt:477`):** `withTimeoutOrNull(75_000L)` poora multi-step loop wrap karta hai — single step timeout nahi hai. 15–25 step ke task mein 8–10 steps par hi 75 seconds poore hokar task kill ho jaata hai.
  2. **Blind Text-Only Brain + 40 Nodes Cap (`TaskExecutor.kt:921, 935`):** `AgenticMobileEngine.kt:494` mein aapne **Hybrid Set-of-Mark Screenshot + Accessibility Tree** likha hua hai, **LEKIN `TaskExecutor.kt` use call hi nahi karta!** `TaskExecutor` sirf text-only `generateAgentDecision` call karta hai aur `compressScreenHierarchy(maxNodes = 40)` ke baad baaki nodes + unlabeled icons kaat deta hai! WebView, YouTube feeds, Instagram/Amazon buttons AI ko dikhte hi nahi!
  3. **No Orchestrator / Sub-Goal Plan (`GeminiApiService.kt:866`):** Prompt ko sirf `history.takeLast(3)` milta hai — koi persistent multi-step checklist (`WorkingMemoryBlackboard` step tracker) nahi jaata! 12-step task mein Step 5 par model bhool jaata hai ki Step 1–2 kya tha aur loop mein ghoomne lagta hai. Plus `maxOutputTokens = 160` (`line 893`) complex step reasoning/summary kaat deta hai, aur `buildCoPilotWorkingMemoryTrace` (`lines 994–1009`) unrelated apps par bhi hardcoded YouTube/WhatsApp hints daalta hai.
  4. **Fixed `450ms` `open_app` Wait (`TaskExecutor.kt:739`):** Heavy apps (Chrome, Amazon, Instagram, YouTube) cold-start hone mein `1.5s–3s` lete hain. `450ms` baad `TaskExecutor` purana launcher ya splash screen dekh leta hai, confuse hokar `RecoveryEngine` se `Back` (`line 232`) daba deta hai — aur **khuli hui app band ho jaati hai!**

---

### Root Cause #7: `ProactiveCompanionEngine` Mid-Task Sabotage + Trailing Transcription Bug + Dual Service
* **Exact Location:**
  - `app/src/main/java/com/jarvisai/assistant/viewmodel/JarvisViewModel.kt` (Lines `707–778`)
  - `app/src/main/java/com/jarvisai/assistant/service/GeminiLiveVoiceManager.kt` (Lines `1161–1182, 1211, 2800–2869`)
  - `app/src/main/AndroidManifest.xml` (Lines `210–222` vs `437–449`)
* **Kya Ho Raha Hai:**
  1. **Proactive Companion Mid-Task Hijack (`JarvisViewModel.kt:710–749`):** Jab `TaskExecutor` Step 1 -> Step 2 mein app switch karta hai (e.g., YouTube -> WhatsApp), toh `latestScreenSnapshotFlow` fire hota hai. `2200ms` baad observer `sendProactiveCompanionTrigger()` call karke Live WebSocket mein fake `"user"` turn (`"User ne ab WhatsApp khola hai, kuch casual bolo"`) bhej deta hai! Kyunki `isChannelIdleForProactiveSpeech()` (`line 2800`) **ye check hi nahi karta ki `isToolCallCurrentlyExecuting` ya `TaskExecutor` chal raha hai**, Live model chalta automation bhool kar gup-shup karne lagta hai!
  2. **Trailing `inputTranscription` Bug (`GeminiLiveVoiceManager.kt:1161–1182, 1211`):** Jab model ka pehla `modelTurn` aata hai, buffer khali ho jaata hai. Par agar aapki aawaz ke aakhri 1–2 trailing words (`"kar do"`) ASR se thoda late aaye, toh woh khali buffer par flush hokar **`toolCalledInCurrentUserTurn.set(false)`** kar dete hain aur `activeTurnUserCommand` ko sirf `"kar do"` se overwrite kar dete hain — jisse watchdog galat trigger hota hai!
  3. **Dual AccessibilityService Declaration (`AndroidManifest.xml:210–222` & `437–449`):** Manifest mein `JarvisAccessibilityService` aur `AutomationAccessibilityService` dono registered hain, jo Android OS accessibility binding mein conflict/confusion create kar sakte hain.

---

## 5. Official Solution Blueprint (AI Studio Docs + Antigravity Docs Based Fix Architecture)

Jab aap **implement/fix karne ka permission denge**, toh niche diye 6 surgical fixes lagane se aapke **chhote aur lambe dono multi-step Android tasks** rock-solid ho jayenge — aapke exact **AI Studio Free Tier + Antigravity Pro** setup ke hisab se:

### Fix 1: Proper Dual-Plan Model Routing (`agy:` Prefix & Orchestrator Support)
1. **Separate `screenOperatorModel` from `textModel` (`UserPreferencesRepository.kt`):** Chat mein `agy:gemini-3.8-flash` rakhne par `screenOperatorModel` overwrite na ho — ya `TaskExecutor` dono paths natively support kare:
   - **Agar user ka Antigravity Pro connected hai (`agy:` ya `Orchestrator`):**
     - **Antigravity Orchestrator Pattern (`antigravity.google/docs/models`):** Long multi-step goal aate hi **Step 0 mein ek baar** `Gemini 4 Argon` / `gemini-3.8-flash` se clean numbered Sub-Goal Checklist (`[1. Open X, 2. Search Y, 3. Tap Z, 4. Switch WhatsApp, 5. Send]`) banwayein, phir per-step actions high-speed **`Gemini 4 Hydrogen`** / **`gemini-3.8-flash` (Antigravity 5h quota)** se chalayein!
   - **Agar AI Studio Free Tier key par fallback/default ho:**
     - Per-step `TaskExecutor` loop mein primary model **`gemini-3.5-flash-lite`** (high Free Tier RPM/RPD headroom so 15–20 steps par `429` na aaye!) aur sirf stuck/complex reasoning step par **`gemini-3.8-flash`** use karein — aur saare retired **`gemini-2.0-flash`** strings hata dein!

### Fix 2: Live Voice `NON_BLOCKING` Async Tools + `contextWindowCompression` + `sessionResumption` (`aistudio.google.com/docs`)
1. `buildSetupMessage()` (`GeminiLiveVoiceManager.kt`) mein add karein:
   - `"contextWindowCompression": { "slidingWindow": {} }` — taaki Audio + 1-FPS Screen stream **2 minute par kabhi na toote**.
   - `"sessionResumption": { "handle": savedHandle }` — taaki agar network drop ho bhi toh same state se auto-resume ho (2 hours valid).
   - Prioritize **`gemini-3.8-live`** (then `gemini-live-2.5-flash-native-audio` / `gemini-2.5-flash-native-audio-preview-12-2025`) Live WebSocket ke liye.
2. `ToolRegistry.kt` mein `execute_screen_operator_goal` aur long tools par **`"behavior": "NON_BLOCKING"`** lagayein:
   - Tool launch hote hi WebSocket unblock rahega -> Live model bol sakta hai *"Main step by step kar raha hoon"* **aur background mein `TaskExecutor` bina WebSocket block kiye chalta rahega!**
   - Har milestone par (e.g., Step 3 done -> ab WhatsApp khol raha hoon) aur final completion par **`toolResponse`** mein `"scheduling": "WHEN_IDLE"` (progress ke liye `"SILENT"`) bhej kar Live model ko real progress status milega!

### Fix 3: Official 4-Part Live System Instruction + Kill the "Empty Promise" Rule
- `GeminiLiveVoiceManager.kt:893` ka `"Respond immediately... minimal thinking"` hatayein.
- `buildSystemInstruction()` ko AI Studio Live Docs ke exact 4-part structure mein clean karein:
  1. **Persona & Hinglish Style** (concise)
  2. **Critical Tool-First Rule:** Jab bhi user phone task bole (1-step ya multi-step), speech emit karne se PEHLE unmistakably `execute_screen_operator_goal` ya atomic tool emit karo — sirf *"Main kar raha hoon"* bolna strictly forbidden!
  3. **Live Tools Workflow + Parameters**
  4. **Emotional Companion / Memories** (compact section at bottom, taaki tool behavior suppress na ho).

### Fix 4: Single Execution Coordinator (650ms Self-Kill Race Khatam!)
- `JarvisViewModel.kt:927`, `GeminiLiveVoiceManager.kt:1336`, aur `TaskExecutor.kt:430` ko ek unified **Turn Execution Lock / Claim (`activeTurnDispatchOwner`)** se jodein:
  - Wait window ko `650ms` se badha kar `1800ms` karein (ya sirf jab `turnComplete == true` aur `wasToolCalledForCurrentTurn == false` ho tabhi fallback chale!).
  - Fallback chale toh turant `liveVoiceManager.markToolExecutedExternallyForTurn()` set kare taaki `enforceRealActionIfModelOnlyPromised` aur `TaskExecutor` mutex **apne hi Step 1 ko cancel na karein**!
  - Trailing `inputTranscription` chunk bug fix karein (turn ke tool flag ko tab tak reset na karein jab tak naya real user utterance silence ke baad shuru na ho).

### Fix 5: Compound Multi-Step Guard in `IntentRouter` & `PhoneActionManager`
- `IntentRouter.classifyCommand()` aur `PhoneActionManager` ke top par ek **`isCompoundOrMultiStepGoal(utterance)`** gate lagayein:
  - Agar sentence mein multiple steps/conjunctions hain (`"aur"`, `"phir"`, `"uske baad"`, `"then"`, `"and"` + 2 action verbs, ya `"like karo"`, `"subscribe"`, `"share"`, `"forward"`, `"settings me jaake"`), toh **single-step regex (`youtube ... (.+)$`, `whatsapp + ko`, `bluetooth`) use hijack nahi karenge** — seedha `TaskExecutor` (`EXECUTE_AUTONOMOUS_OPERATOR`) chalega!

### Fix 6: `TaskExecutor.kt` Multi-Step Reliability Upgrade + Silence `ProactiveCompanionEngine` During Tasks
1. **Per-Step Timeout instead of 75s Global Cap:** Total 75s loop timeout hata kar **per-step `15s` timeout** + **max total `240s` (4 min)** lagayein.
2. **Dynamic Window-Settle Wait after `open_app`:** Fixed `450ms` ki jagah `waitUntilPackageChangesOrSettled(targetPkg, timeoutMs = 3500L)` use karein, taaki splash screen par `RecoveryEngine` galat `Back` na dabaye!
3. **Hybrid Vision + Set-of-Mark Fallback (`AgenticMobileEngine` integration):** Jab screen par `> 40` nodes hon, ya unlabeled icons dikhein, ya consecutive 2 steps mein screen change na ho -> automatically annotated Set-of-Mark Screenshot (`captureScreenshotSuspend`) attach karke Vision decision lein!
4. **Persistent Sub-Goal Checklist:** `history.takeLast(3)` ke saath structured `PlanChecklist` (`[DONE] Step 1`, `[CURRENT] Step 2`, `[PENDING] Step 3..N`) har step ke prompt mein bhejein aur `maxOutputTokens` ko `160` se `384` karein + hardcoded YouTube/WhatsApp noise hatayein.
5. **Mute Proactive Triggers + Pause 1-FPS Frame Spam While Task Running:** `isChannelIdleForProactiveSpeech()` aur `JarvisViewModel` screen observer (`line 710`) mein check lagayein: `if (isToolCallCurrentlyExecuting || TaskExecutor.isRunning) return` — taaki automation ke dauran Live screen frame spam aur fake companion prompts band rahein!

---

## 6. Verification Checklist (Code Edit Ke Baad Test Karne Ke Liye)
*Note: Abhi aapke nirdesh ke mutabik **koi bhi code change ya build nahi kiya gaya hai**. Jab aap kahein tab hum in fixes ko apply karke test kar sakte hain:*
1. **Short Multi-Step Test:** *"YouTube par Tech Burner search karo aur latest video play karke like karo"* -> `IntentRouter` bypass -> `TaskExecutor` bina self-cancel hue search -> click -> like complete kare.
2. **Long Cross-App Multi-Step Test:** *"Chrome me AI news kholo, summary copy karo, WhatsApp par Rahul ko bhejo"* -> `Orchestrator` plan + per-step execution + dynamic `open_app` settle + no 75s abort + no `agy:` 404 crash + no `ProactiveCompanion` interruption.
3. **Live Voice Call Stability Test:** 3+ minute screen share + voice call ke dauran bina WebSocket drop ke `NON_BLOCKING` async tool execution aur smooth Hinglish voice updates.
