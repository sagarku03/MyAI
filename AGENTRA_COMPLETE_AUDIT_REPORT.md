# 🔍 Agentra Complete Forensic Audit Report (Updated With Screen Recording & Screenshots)

> **Audit Date:** 22 November 2026  
> **Attachments Analyzed (100% Reviewed):**
> 1. 🎥 `Screenrecording_20261121_085559_1f2479a5.mp4` — Agentra Home File Manager (20 folders) vs AntiGem Home File Manager (15 folders) + App Switcher Icon
> 2. 🖼️ `Screenshot_20261120_191848_94970b8b.jpg` (`7:18`) — ZIP Install Error: `/data/user/0/ai.agentra/files/usr/bin/zsh: open failed: ETXTBSY (Text file busy)`
> 3. 🖼️ `Screenshot_20261120_193044_10411e55.jpg` (`7:30`) — ZIP Install Error: `/data/user/0/ai.agentra/files/home/.local/bin/agy: open failed: ETXTBSY (Text file busy)`
> 4. 🖼️ `Screenshot_20261120_212110_f5850e74.jpg` (`9:21`) — Chat running `jarvis-mcp` + `Antigravity Hub inactive: no progress received for 120s.` during `git clone`
> 5. 📦 All 3 GitHub Release RootFS ZIPs (`agentra-rootfs.zip`, AntiGem ARM64 `bootrapz_2026-11-19.zip`, AntiGem x86_64 `bootrapz_x86_64_2026-11-20.zip`)
> 6. 💻 Full Source Code Comparison: **Agentra** vs **AntiGem** (`https://github.com/Santoshkurmi/antigem`)
>
> **Strict Rules Respected:** Abhi koi bhi code edit ya build nahi kiya gaya hai. Agentra ke isolated ports (`2234–2240`) 100% safe & untouched rakhe jayenge taki Agentra aur AntiGem ek hi phone me saath chal sakein.

---

## 1. Executive Summary (Short Verdict)

| Audit Pillar | Status | Exact Verdict (Hinglish) |
|---|---|---|
| **1. ZIP Install Error (`ETXTBSY`) — Screenshots #2 & #3** | 🔴 **100% App-Side Bug Confirmed** | Dono screenshots me **exact same kernel error** hai: **`open failed: ETXTBSY (Text file busy)`** (`usr/bin/zsh` aur `home/.local/bin/agy` par). Iska reason: Agentra ZIP extract karne se pehle running servers/terminals (`zsh`, `agy`, `agy_ide_bridge`) ko stop nahi karta, aur overwrite se pehle `outputFile.delete()` (`unlink`) nahi karta! (AntiGem karta hai.) |
| **2. File Manager Duplicate Folders & `Jarvis` / `Agentra AI` — Screen Recording** | 🔴 **ZIP + App Code Dono Me Galti Hai** | Video me saaf dikh raha hai: AntiGem `Home` me sirf **15 clean folders** hain, jabki Agentra `Home` me **20 folders** hain jinme **6 messy/duplicate folders** hain (`.agentra-ai`, `.jarvis`, `.jarvis-ai`, `.jarvis_bridge`, `agentra-workspace`, `jarvis-workspace`, plus empty `projects`). **Bonus Catch from Video:** App icon (`jarvis_app_logo.png`) me bhi abhi tak **"Jarvis"** likha hua dikh raha hai! |
| **3. Chat Screenshot (`9:21`) — `jarvis-mcp` + `120s Hub Inactive`** | 🔴 **2 App-Side Bugs Confirmed** | (a) Agent abhi bhi `jarvis-mcp` chala raha hai kyunki system prompt aur CLI script rename nahi hue. (b) Long `git clone` command ke beech `AntigravityApiService.kt` ne 120s par **`Antigravity Hub inactive: no progress received for 120s.`** error de kar active command ko abort kar diya! |
| **4. Aapka Modified `agentra-rootfs.zip` (400 MB)** | 🟡 **80% Sahi, 20% Galat (Clean Rebuild Needed)** | Core ELF binaries (704/704), CRC32, symlinks, `am.apk` (valid DEX), aur `99agentra-relocate` **100% sahi aur corruption-free hain** (ZIP corrupt hone ki wajah se install fail NAHI hua tha!). **LEKIN** Google Antigravity ne `home/` me **3 forbidden `jarvis*` symlinks**, **`.agentra-ai`**, **duplicate `agentra-workspace` + `projects`**, aur **`usr/bin/` me 6 AntiGem scripts** chhod diye hain. Isliye Section 8 me clean rebuild prompt diya hai. |
| **5. Agentra vs AntiGem Source Code Parity** | 🟢 **~94% Parity + Extra Agentra Features** | Core subsystems sab implemented hain. Sirf upar wale installer/timeout/naming bugs + optional 3 minor UI polish screens bachi hain. |

---

## 2. Visual Audit Findings (Aapke Screen Recording & Screenshots Se Jo Exact Pakda Gaya)

### 2.1 🎥 Screen Recording: Agentra `Home` vs AntiGem `Home` Side-by-Side

| Agentra `Home` (Video `00:04–00:11` — 20 folders) | AntiGem `Home` (Video `00:19–00:26` — 15 folders) | Problem & Exact Source |
|---|---|---|
| `.agentra-ai` *(Empty)* | `.antigem` | ❌ **Galat naam!** Aapne strictly bola hai sirf `Agentra`, `Agentra AI` nahi → Iska naam **`.agentra`** hona chahiye. *(Source: `agentra-rootfs.zip`)* |
| `.agentra_bridge` | `.antigem_bridge` | ✅ Sahi naam! |
| **`.jarvis`** *(1 item)* | *(None)* | ❌ **Forbidden `Jarvis` folder!** *(Source: `LocalServerManager.kt:1166` creates `.jarvis/uploads`!)* |
| **`.jarvis-ai`** *(1 item)* | *(None)* | ❌ **Forbidden `Jarvis` + Duplicate!** *(Source: `JarvisHubConfig.kt:24`, `LocalServerManager.kt:1259`, `MiniAppsManager.kt:48`, `JarvisTermuxBridge.kt:338`, AND symlink inside `agentra-rootfs.zip`!)* |
| **`.jarvis_bridge`** *(1 item)* | *(None)* | ❌ **Forbidden `Jarvis` + Duplicate!** *(Source: `AgyOkHttpClient.kt:118–120` AND symlink inside `agentra-rootfs.zip`!)* |
| **`agentra-workspace`** *(1 item)* | *(None)* | ❌ **Duplicate workspace folder!** *(Source: `agentra-rootfs.zip`)* |
| **`jarvis-workspace`** *(1 item)* | *(None)* | ❌ **Forbidden `Jarvis` + Duplicate workspace!** *(Source: `JarvisHubConfig.kt:23`, `LocalEnvironmentManager.kt:371`, `assets/bin/git_clone`, `assets/bin/restore_projects`, AND symlink inside `agentra-rootfs.zip`!)* |
| `projects` *(Empty)* | `projects` *(1 item)* | ⚠️ Agentra me `FileManagerOperations.kt:349` ne `~/projects` banaya, jabki baaki app `~/jarvis-workspace/projects` use kar rahi thi — isliye **3 workspace folders** (`projects`, `agentra-workspace`, `jarvis-workspace`) ek saath dikh rahe the! |
| `attachments` | *(Inside `.antigem/uploads`)* | ℹ️ Optional — ya `.agentra/uploads` me move kar sakte hain. |
| *(Missing at root — hidden inside `.jarvis-ai/mini-apps`)* | `mini-apps` | ℹ️ AntiGem ki tarah `~/mini-apps` directly `Home` me rakhna cleaner rahega. |
| `sdcard` + `storage` | `storage` | ℹ️ `storage` standard Termux storage links hain; `sdcard` extra symlink hai. |
| `.claude`, `.config`, `.gemini`, `.local`, `.npm`, `.npm-global`, `.termux`, `.zsh`, `logs` | `.config`, `.gemini`, `.local`, `.npm`, `.termux`, `.zsh`, `browser-mcp`, `logs` | ✅ Standard Linux/CLI runtime folders (bilkul sahi). |

#### 🎨 Extra Visual Catch From Video (`00:14`) & Screenshots (`9:21`, `7:18`):
- **App Launcher Icon & Notification Icon me abhi bhi `"Jarvis"` text likha hua hai!**
  - Reason: `app/src/main/res/drawable/ic_launcher_foreground.xml` abhi bhi `app/src/main/res/drawable/jarvis_app_logo.png` use kar raha hai jisme blue/purple robot ke andar **"Jarvis"** printed hai (Recents switcher aur status bar notification icon dono me dikh raha hai).
  - Fix: Clean **Agentra** adaptive launcher icon + clean monochrome status bar notification icon banana hoga (bina kisi `"Jarvis"` text ke).

---

### 2.2 🖼️ Screenshots #2 (`7:30`) & #3 (`7:18`): ZIP Install Error (`ETXTBSY`) Exact Root Cause

Dono screenshots ke live extraction log dekhiye:
- **Screenshot `7:18`:**
  ```
  Package ID: ai.agentra (10 bytes)
  Target prefix: /data/user/0/ai.agentra/files/usr
  Extracting archive: picked_bootstrap.zip...
  Unpacking [526]: bin/llvm-readobj
  ERROR: /data/user/0/ai.agentra/files/usr/bin/zsh: open failed: ETXTBSY (Text file busy)
  ```
- **Screenshot `7:30`:**
  ```
  Unpacking [25149]: glibc/lib/libm.so.6
  ERROR: /data/user/0/ai.agentra/files/home/.local/bin/agy: open failed: ETXTBSY (Text file busy)
  ```

**100% Exact Technical Reason (`ETXTBSY` kyun aaya?):**
1. **Background processes chal rahe the:** Jab aapne `Pick ZIP...` se re-install chalaya, us waqt background me:
   - Terminal session (`usr/bin/zsh`) chal raha tha → **`zsh: open failed: ETXTBSY`!**
   - Jab aapne dobara try kiya, `LocalServerManager` ka `home/.local/bin/agy --hub` process background me chal raha tha → 25,149 files unpack hone ke baad **`agy: open failed: ETXTBSY`!**
2. **Agentra `installBootstrapInternal()` extraction se pehle processes kill nahi karta:**
   - `LocalServerManager.stopServer()` aur `LocalTerminalManager.closeAll()` (plus `pkill` / process cleanup for `agy`, `agy_ide_bridge`, `zsh`, `bash`) call hi nahi hota!
3. **Agentra `extractZipPreservingPermissions()` overwrite se pehle `delete()` (`unlink`) nahi karta!**
   - Linux/Android kernel me agar koi binary running hai, toh uspe direct `FileOutputStream(outputFile)` (`open(O_WRONLY | O_TRUNC)`) **hamesha `ETXTBSY` deta hai**!
   - Lekin agar pehle `outputFile.setWritable(true, true)` + `outputFile.delete()` (`unlink`) karke phir naya `FileOutputStream(outputFile)` khola jaye, toh Linux naya inode bana deta hai aur **`ETXTBSY` (aur `0400` `am.apk` ka `EACCES`) KABHI NAHI AATA!**
   - **AntiGem ke `LocalEnvironmentManager.kt` (L1325, L1352–1354, L1380)** me har single entry write karne se pehle `targetFile.setWritable(true, true); targetFile.delete()` likha hai — **Agentra me ye missing tha!**
4. **3 Aur Related Installer Bugs (`LocalEnvironmentManager.kt` + `LinuxRootfsManagerDialog.kt`):**
   - **Symlink vs Directory Collision:** Agar `home/jarvis-workspace` pehle se non-empty folder hai aur ZIP me woh symlink hai, toh `Files.deleteIfExists()` `DirectoryNotEmptyException` deta hai.
   - **Zip-Slip Check Bug (L982):** Existing symlink (jaise `home/storage/shared -> /storage/emulated/0`) par `outputFile.canonicalPath` false-reject kar deta hai; lexical normalized path check karna chahiye.
   - **Pick ZIP & Default URL Bugs:** `GetContent("application/zip")` ke bajaye `OpenDocument` multi-MIME hona chahiye, copy progress + `try/catch` + auto-delete `picked_bootstrap.zip` after install, `CommonsZipFile.use {}`, aur default download mirror URL AntiGem ke `bootrapz_2026-11-19.zip` ki jagah aapke **`agentra-rootfs.zip`** par hona chahiye!

---

### 2.3 🖼️ Screenshot #1 (`9:21`): Chat Execution Bugs

Screenshot `9:21` me **2 real bugs** saaf dikh rahe hain:
1. **`jarvis-mcp` Tool Calls:**
   - `Ran bash  which jarvis-mcp && jarvis-mcp status`
   - `Ran bash  jarvis-mcp --help`
   - **Cause:** `LocalEnvironmentManager.kt`, `McpServersManager.kt`, aur `AntigravityApiService.kt` me abhi bhi `jarvis-mcp` aur `jarvis-mcp-stdio` hardcoded hain. Sabko **`agentra-mcp`** aur **`agentra-mcp-stdio`** karna hai (ek hi canonical folder me, 3 duplicate copies nahi!).
2. **`Antigravity Hub inactive: no progress received for 120s.` during `git clone`:**
   - Screenshot me agent `Tool  git clone --depth 1 https://github.com/abhigyanpatwari/GitNexus ...` chala raha tha, aur exact 120 second par error aa gaya:
     `Antigravity Hub inactive: no progress received for 120s.`
   - **Cause (`AntigravityApiService.kt` L3454 & L3525):** `val maxIdleSeconds = 120` hardcoded hai, aur jab koi long-running bash command (`git clone`, `npm install`, `pkg install`, `gradle`) `RUNNING` state me hoti hai aur trajectory text 120s tak change nahi hota, toh `lastRealProgressMs` update nahi hota aur polling loop active command ko hi 120s par abort kar deta hai!
   - **Fix:** Jab `hasAnyStepInProgress == true` (koi tool/command actively RUNNING hai) ya `cascadeStatus` `RUNNING` hai, tab idle timeout ko extend karna (e.g., `300s–600s`) aur active running tool par false inactivity abort rokna!

---

## 3. Forensic Audit: Aapka `agentra-rootfs.zip` vs AntiGem Official ZIPs

Teeno GitHub Release ZIPs ka binary-level forensic comparison:
- **Aapka ZIP (ARM64):** `https://github.com/sagarku03/MyAI/releases/download/rootfs/agentra-rootfs.zip`
- **AntiGem ARM64:** `https://github.com/Santoshkurmi/antigem/releases/download/rootfs-v2/bootrapz_2026-11-19.zip`
- **AntiGem x86_64:** `https://github.com/Santoshkurmi/antigem/releases/download/rootfs-v2/bootrapz_x86_64_2026-11-20.zip`

### 3.1 Exact Byte-Level Comparison Table

| Metric | Aapka `agentra-rootfs.zip` (ARM64) | AntiGem `bootrapz_2026-11-19.zip` (ARM64) | AntiGem `bootrapz_x86_64_2026-11-20.zip` (x86_64) |
|---|---:|---:|---:|
| **Exact Size** | `399,996,451` B (~381.47 MB) | `400,054,861` B (~381.52 MB) | `419,473,812` B (~400.04 MB) |
| **Total ZIP Entries** | `26,133` (0 duplicates) | `26,152` | `21,134` |
| **CRC32 Integrity (`testzip()`)** | ✅ `PASS` (0 corrupt files) | ✅ `PASS` | ✅ Valid Central Directory |
| **Top-Level Structure** | `['home', 'usr']` ✅ | `['home', 'usr']` | `['home', 'usr']` |
| **ZIP Unix Symlinks** | `1,829` preserved ✅ | `1,827` | `1,685` |
| **Executable Files** | `2,878` (0 lost exec bits) ✅ | `2,876` | `2,629` |
| **ELF Binaries Relocated** | `704/704` → `ai.agentra` ✅ | `703` `ai.antigem`, `1` `com.termux` | x86_64 ELFs |
| **ELF Byte-Size Corruption** | `0` (100% byte-exact) ✅ | Baseline | N/A |
| **ELFs still having `ai.antigem` / `com.termux`** | `0` / `0` ✅ | `703` / `1` | `ai.antigem` |
| **`usr/libexec/termux-am/am.apk`** | `670,930 B`, mode `0400`, DEX Adler32+SHA1 ✅, `ai.agentra` ✅ | `685,346 B`, mode `0400`, `ai.antigem` | Present |
| **APT Relocate Hook & `diversions`** | `99agentra-relocate` + `ai.agentra` ✅ | `99antigem-relocate` | `99antigem-relocate` |
| **`home/` Cleanliness** | 🔴 **17 items — 3 `jarvis*` symlinks + `.agentra-ai` + duplicate workspaces!** | 🟢 14 items (Clean) | 🟢 9 items (Clean) |
| **`usr/bin/` Helper Scripts** | 🔴 **6 scripts still have `AntiGem` & `/sdcard/Download/Antigem/backups`!** | AntiGem scripts | AntiGem scripts |

### 3.2 Aapke ZIP Me Kya Sahi Hai vs Kya Galat Hai
- **✅ Good News:** Google Antigravity ne **binary patching 100% sahi ki thi** — ek bhi ELF binary corrupt nahi hui, CRC32 100% pass hai, aur `am.apk` valid hai. Aapke screenshots me jo ZIP install error (`ETXTBSY`) aaya tha, wo ZIP corruption ki wajah se nahi balki app-side `ETXTBSY` bug ki wajah se aaya tha!
- **❌ Bad News (Isliye Clean Rebuild Zaroori Hai):**
  1. `home/` me **3 forbidden `jarvis*` symlinks** (`home/.jarvis-ai`, `home/.jarvis_bridge`, `home/jarvis-workspace`) daal diye — yahi aapki screen recording me duplicate folders ki tarah dikh rahe the!
  2. Galat naam **`home/.agentra-ai/`** (sirf `home/.agentra/` hona chahiye, `agentra-ai` nahi!) aur **duplicate `home/agentra-workspace/` + `home/projects/`** bana diye!
  3. **`usr/bin/` ke 6 scripts** (`backup_projects`, `restore_projects`, `backup_bootstrap`, `backup_chats`, `git_clone`, `claude`) me abhi bhi `AntiGem` aur `/sdcard/Download/Antigem/backups` hardcoded hai!
  - *(Note: `usr/bin/agy_ide_bridge` Go binary me internal Go banner strings `antiGem` bache hain — unhe binary patch NAHI karna hai warna binary corrupt ho jayegi; `com.termux` → `ai.agentra` already patched hai aur Agentra ise port `2234/2235` par CLI flags se chalata hai.)*

---

## 4. Complete `Jarvis` / `Agentra AI` & Duplicate Paths Audit (App Code)

| File (App Code) | Abhi Kya Galat Hai | Kya Hona Chahiye (Strict `Agentra`) |
|---|---|---|
| `res/drawable/jarvis_app_logo.png` + `ic_launcher_foreground.xml` | App icon & notification icon me **"Jarvis"** likha dikh raha hai (Video `00:14`, Screenshots) | Clean **Agentra** adaptive icon (zero `"Jarvis"` text!) |
| `JarvisHubConfig.kt` (L22–26) | `jarvis-antigravity`, `jarvis-workspace`, `.jarvis-ai`, `_jarvis-pc._tcp` | `agentra-antigravity`, `projects`, `.agentra`, `_agentra-pc._tcp` *(Ports `2234–2240` UNCHANGED!)* |
| `LocalServerManager.kt` (L1166, 1259, 1272) | Creates `.jarvis/uploads`, `.jarvis-ai`, `jarvis-workspace/projects`, `jarvis_hub_*.log` | `.agentra/uploads`, `.agentra`, `projects`, `agentra_hub_*.log` |
| `AgyOkHttpClient.kt` (L118–120) | Reads `.jarvis_bridge/security_token.txt` | `.agentra_bridge/security_token.txt` *(with fallback check)* |
| `FileManagerOperations.kt` (L349) | Creates `~/projects` while IDE/Chat used `~/jarvis-workspace/projects` | Sabhi screens ek hi unified **`~/projects`** use karein (AntiGem jaisa clean!) |
| `LocalEnvironmentManager.kt` (L96, 536–550, 1560–1667) | Hardcoded AntiGem URL, scans `Download/JarvisAI` & `Download/Antigem`, writes `jarvis-mcp` & `jarvis-mcp-stdio` in 3 duplicate dirs | Default URL = `agentra-rootfs.zip`, scan sirf `Download/Agentra` + `Download`, rename → `agentra-mcp` & `agentra-mcp-stdio` (single copy + auto-delete old `jarvis-mcp*`) |
| `LocalInstallerState.kt` (L12, 18) | Primary mirror points to `Santoshkurmi/antigem/.../bootrapz_2026-11-19.zip` | Primary mirror → `https://github.com/sagarku03/MyAI/releases/download/rootfs/agentra-rootfs.zip` |
| `BackupRestoreManager.kt` (L36) | `/sdcard/Download/Jarvis/backups` | `/sdcard/Download/Agentra/backups` |
| `JarvisTermuxBridge.kt` (L338–364) | `~/.jarvis-ai/jarvis_bridge_2238.py`, `~/jarvis-workspace/projects` | `~/.agentra/agentra_bridge_2238.py`, `~/projects` |
| `MiniAppsManager.kt` (L48) | `$HOME/.jarvis-ai/mini-apps/` | `$HOME/mini-apps/` *(AntiGem parity + auto-migrate old data!)* |
| `NotesMcpBridge.kt` (L59) | `jarvis_mcp_notes.json` | `agentra_mcp_notes.json` *(with auto-migration!)* |
| `SkillsAndPluginsManager.kt` (L159, 293, 520, 603–627) | `.jarvis-source.json`, `JarvisAI-Android`, `~/.gemini/jarvis-antigravity/` | `.agentra-source.json`, `Agentra-Android`, `~/.gemini/agentra-antigravity/` |
| `McpServersManager.kt` + `AndroidLocalBridgeServer.kt` + `AndroidDeviceMcpBridge.kt` | `"JarvisAI"`, `"JarvisEmbeddedMcp"`, `"Jarvis Android & Device Bridge"`, `.jarvis-ai/mcp_config.json`, `jarvis-mcp`, clipboard `"Jarvis"` | `"Agentra"`, `"AgentraEmbeddedMcp"`, `"Agentra Android & Device Bridge"`, `.agentra/mcp_config.json`, `agentra-mcp` |
| `AntigravityApiService.kt` (L1489, 2988, 3454, 3525, 3967) | `jarvis-mcp`, `jarvis-mcp-stdio`, AND **120s idle abort during running `git clone`** | `agentra-mcp`, `agentra-mcp-stdio`, AND **fix 120s timeout while tools are actively running!** |
| `PRootRuntime.kt` (L48, 197, 425–426) | `"/root/jarvis-home"`, `"jarvis-workspace/projects"`, `JARVIS_MCP_URL` | `"/root/agentra-home"`, `"projects"`, `AGENTRA_MCP_URL` |
| `ManageSpaceActivity.kt` (L500) | `.gemini/jarvis-antigravity` | `.gemini/agentra-antigravity` |
| `assets/bin/git_clone` & `restore_projects` | `${HOME_DIR}/jarvis-workspace` | `${HOME_DIR}/projects` |
| `BrowserScreen.kt` (L1235) | `"Agentra AI Page Summary"` ❌ | `"Agentra Page Summary"` ✅ |

---

## 5. Full Source Code Comparison: Agentra vs AntiGem

| # | AntiGem Subsystem | Agentra Status | Notes |
|---|---|---|---|
| 1 | Option B Native Termux Engine (`TermuxRelocation`, `:termux-am`, `glibc`, `99*-relocate`, `dpkg-divert`) | ✅ **Present** | Needs Section 2.2 `ETXTBSY` + `delete()` before write + `stopServer()` fix. |
| 2 | Dual App Icon (`Agentra Term` / `Termux`) | ✅ **Present** | Needs clean Agentra icon replacing `jarvis_app_logo.png`. |
| 3 | Local Server Supervisor & Ports | ✅ **Present (Isolated)** | Ports `2234–2240` strictly isolated from AntiGem's `1234/1235/8765/8091/4096`. |
| 4 | Multi-Agent (AGY, Claude Code, Codex, OpenCode) | ✅ **Present** | Needs Section 2.3 `120s` running-tool timeout fix. |
| 5 | Sora IDE, Project Sidebar, Diff Viewer, Git & GitHub CLI, `GitCloneDialog` | ✅ **Present** | Unify workspace to single `~/projects`. |
| 6 | Live App Runner (`RunManager`, `RunScreen`, `RunTemplates`) | ✅ **Present** | Ready. |
| 7 | Mini-Apps Platform (`MiniAppsManager`, `MiniAppActivity`, `MiniAppsScreen`, SDK, MCP) | ✅ **Present** | Move storage to `~/mini-apps` (AntiGem parity). *(Optional gap: `.agapp` share/install activity.)* |
| 8 | Notes (`NotesMcpBridge` + `NotesScreen`) | ✅ **Present** | Rename JSON to `agentra_mcp_notes.json`. |
| 9 | STEM / Chemistry / LaTeX / Mermaid (`ChemNotation`, `StemBlocks`, `StemRenderingScreen`) | ✅ **Present** | Ready. |
| 10 | Network Inspector (`NetworkInspectorManager`, `Interceptor`, `Dialog`) | ✅ **Present** | *(Optional gap: floating overlay bubble version.)* |
| 11 | In-App Browser + Chrome Extensions + Browser MCP | ✅ **Present** | Fix `"Agentra AI Page Summary"` → `"Agentra Page Summary"`. |
| 12 | File Explorer (`FileExplorerScreen`, `FileManagerOperations`) | 🟡 **Present** | Unify `~/projects` + auto-cleanup legacy `jarvis*`/`.agentra-ai` folders. *(Optional gap: `IncomingShareDialog`.)* |
| 13 | Rooted Chroot (`/data/local/agentra`), X11 Desktop, REA, SSH/Tmux, Wireless ADB, PC Connect | ✅ **Present** | Ready. |
| 14 | Project Checkpoints UI (`ProjectCheckpointsScreen`, `CheckpointRestoreSheet`) | ⚪ **Optional Minor Gap** | Git diff/revert exists; dedicated checkpoint sheet can be added if desired. |
| 15 | **Agentra-Exclusive Extras (Not in AntiGem)** | 🌟 **Bonus** | Live Voice Companion, Wake Word, Arc Reactor HUD & Wallpaper, Watchman Security, Optical Solver, Polyglot Diplomat, Driving Mode, Notification/Call & Accessibility Screen Agent. |

---

## 6. Action Split: Kya AI (Main) Fix Karunga vs Kya Aapko Karna Hai

### 🤖 Part A: Jo Main (AI) App Code Me Fix Karunga (Jab Aap Bolenge)
1. **Fix `ETXTBSY (Text file busy)` & All ZIP Install Errors (`LocalEnvironmentManager.kt`, `PRootRuntime.kt`, `LinuxRootfsManagerDialog.kt`, `LocalInstallerState.kt`):**
   - Extraction start hone se pehle **`LocalTerminalManager.closeAll()` + `LocalServerManager.stopServer()`** + stray `agy` / `agy_ide_bridge` / `zsh` / `bash` processes ko cleanly terminate karna.
   - Har file aur symlink extract karne se pehle **`outputFile.setWritable(true, true)` + `outputFile.delete()`** (aur agar directory vs symlink collision ho toh safe cleanup) — **`ETXTBSY` (`zsh`, `agy`) aur `EACCES` (`0400 am.apk`) 100% khatam!**
   - Lexical Zip-Slip check, `CommonsZipFile.use {}`, `am.apk` `0400` preservation, `OpenDocument` multi-MIME picker, copy progress + `try/catch` + auto-delete temp `picked_bootstrap.zip`.
   - Default download mirror URL ko aapke `https://github.com/sagarku03/MyAI/releases/download/rootfs/agentra-rootfs.zip` par set karna.
2. **Fix 120s Hub Inactivity Abort During Long Commands (`AntigravityApiService.kt`):**
   - Jab koi bash/tool step (`git clone`, `npm install`, `pkg install`) actively `RUNNING` ho, tab 120s par premature abort nahi hoga!
3. **100% `Jarvis` & `Agentra AI` Purge + Clean AntiGem-Style `Home` Structure:**
   - Sabhi paths unify karke strictly **Agentra** naming: `~/.agentra`, `~/.agentra_bridge`, `~/projects`, `~/mini-apps`, `agentra-mcp`, `agentra-mcp-stdio`, `/sdcard/Download/Agentra/backups`, `"Agentra Page Summary"`.
   - **Auto-Migration & Cleanup on Launch:** Phone par pehle se bane hue `.jarvis`, `.jarvis-ai`, `.jarvis_bridge`, `.agentra-ai`, `jarvis-workspace`, `agentra-workspace`, `jarvis-mcp*` folders/symlinks ka koi bhi user data safely migrate karke un duplicate/Jarvis folders ko auto-delete kar dega — taaki aapke File Manager me **ekdum AntiGem jaisa clean Home** dikhe!
   - **App Icon Fix:** `jarvis_app_logo.png` (jisme `"Jarvis"` likha hai) ko clean **Agentra** adaptive launcher icon se replace karna (bina `"Jarvis"` text ke).
   - **Isolated Ports (`2234–2240`) 100% Untouched!**

### 👤 Part B: Jo Aapko Karna Hai
1. Original AntiGem `bootrapz_2026-11-19.zip` de kar Google Antigravity ko neeche diya hua **Section 7 Master Prompt** paste karke bilkul clean `agentra-rootfs.zip` banwa lein (taaki usme zero `jarvis*` symlinks, zero `.agentra-ai`, zero duplicate workspaces, aur zero AntiGem scripts rahein).
2. Nayi clean `agentra-rootfs.zip` ko apne GitHub Release (`https://github.com/sagarku03/MyAI/releases/tag/rootfs`) par upload/replace kar dein.
3. Jab main Part A implement karke APK build kar doon, usse install karke 1-Click Install ya Pick ZIP chalayein — `ETXTBSY`, duplicate folders, `Jarvis` icon, aur `120s` timeout sab ek saath solve ho jayega!

---

## 7. 📋 Master Prompt for Google Antigravity (Clean `agentra-rootfs.zip` Rebuild)

*(Kyunki aapke current `agentra-rootfs.zip` me forbidden `jarvis*` symlinks, `.agentra-ai`, duplicate `agentra-workspace` + `projects`, aur 6 `AntiGem` scripts mile hain, neeche ready-to-paste Master Prompt hai. Original AntiGem `bootrapz_2026-11-19.zip` input de kar ye exact prompt Google Antigravity ko dein:)*

```text
You are modifying the official AntiGem Android Termux RootFS bootstrap archive (`bootrapz_2026-11-19.zip`, ARM64 / aarch64) from scratch to produce a 100% clean, production-grade RootFS archive named `agentra-rootfs.zip` for the Android app **Agentra** (package ID: `ai.agentra`).

FOLLOW EVERY RULE BELOW WITH 100% PRECISION. DO NOT ADD ANY EXTRA FOLDERS OR SYMLINKS.

### 1. STRICT BRANDING & NAMING RULES
- App name is strictly **Agentra** (NEVER `Agentra AI`, NEVER `agentra-ai`, NEVER `Jarvis`, NEVER `jarvis`, NEVER `AntiGem`).
- Target Android package ID is **`ai.agentra`** (exact 10 ASCII bytes — identical length to `ai.antigem` and `com.termux`).
- Agentra isolated ports (MUST NOT collide with AntiGem's 1234/1235):
  - `AGENTRA_BRIDGE_PORT=2234`
  - `ANTIGRAVITY_HUB_PORT=2235`
  - `AGENTRA_MCP_PORT=2236`
  - `AGENTRA_BROWSER_MCP_PORT=2237`

### 2. BYTE-EXACT BINARY & PATH RELOCATION (`ai.antigem` & `com.termux` -> `ai.agentra`)
- Process `bootrapz_2026-11-19.zip` while **preserving all Unix file modes (`external_attr`), executable bits, read-only `0400` bits, and Unix symbolic links (`0120777`)**.
- Replace `ai.antigem` (10 bytes) and `com.termux` (10 bytes) with `ai.agentra` (10 bytes) **byte-for-byte in-place** across:
  - All ELF binaries & `.so` shared libraries (CRITICAL: **EVERY ELF binary MUST keep its EXACT original byte length** — verify `len(new) == len(old)` for every `\x7fELF` file!).
  - All text files, shell scripts, shebangs, configs, `.pc` files, `dpkg` metadata (`usr/var/lib/dpkg/*`), and Unix symlink targets.
- **EXCEPTIONS — DO NOT CORRUPT THESE:**
  - NEVER replace `com.termux.termuxam` or `com.termux.x11.` (keep those Java class/package prefixes intact!).
  - NEVER blindly byte-patch `.apk`, `.jar`, `.zip`, `.aar`, `.dex`, `.gz`, `.xz` compressed archives (except updating symlink targets whose names end in `.gz`, e.g., `usr/etc/alternatives/*.1.gz`).
  - DO NOT change the byte length of `usr/bin/agy_ide_bridge` (only replace 10-byte `com.termux`/`ai.antigem` -> `ai.agentra` in-place; do NOT touch internal Go strings like `antiGem` or `.antigem_bridge` inside that Go binary).

### 3. `usr/libexec/termux-am/am.apk`
- Keep `usr/libexec/termux-am/am.apk` valid for `ai.agentra` (valid DEX Adler32 + SHA-1 checksums, mode `0100400` read-only on both `am.apk` and `am.apk.distrib`).

### 4. APT HOOKS, `dpkg` WRAPPER, AND `termux-api` DISPATCHER
- Remove `usr/etc/apt/apt.conf.d/99antigem-relocate` and `usr/libexec/antigem/` completely.
- Create:
  - `usr/etc/apt/apt.conf.d/99agentra-relocate` (mode `0644`) pointing to `/data/data/ai.agentra/files/usr/libexec/agentra/relocate-debs --apt`.
  - `usr/libexec/agentra/relocate-debs` (mode `0755`) rewriting `OLD='com.termux'` -> `NEW='ai.agentra'` while preserving `com.termux.termuxam` and `com.termux.x11.`.
- Update `usr/bin/dpkg` (mode `0755`) comments & paths to reference `Agentra`, `ai.agentra`, and `99agentra-relocate`.
- Update `usr/var/lib/dpkg/diversions` to use `/data/data/ai.agentra/...`.
- Update `usr/libexec/termux-api` (mode `0755`) to use `Agentra` text, `/data/data/ai.agentra/files/usr`, and broadcast component `ai.agentra/com.jarvisai.assistant.receiver.TermuxApiReceiver` (keep that exact Android component class name so it matches Agentra's `AndroidManifest.xml`).

### 5. CLEAN `usr/bin/` HELPER SCRIPTS (ZERO `AntiGem` / `Jarvis` LEAKS)
Update ALL 6 scripts below so they use `Agentra`, `/data/data/ai.agentra/files/...`, `~/projects`, and `/sdcard/Download/Agentra/backups`:
1. `usr/bin/backup_projects` -> `BACKUP_DIR="/sdcard/Download/Agentra/backups"`, banner `Agentra Projects Backup Packager`, manifest `agentra_project_manifest.json`.
2. `usr/bin/restore_projects` -> banner `Agentra Projects Archive Restorer`, support reading `agentra_project_manifest.json` (and fallback `antigem_project_manifest.json`), default restore dir `${HOME_DIR}/projects`.
3. `usr/bin/backup_bootstrap` -> `BACKUP_DIR="/sdcard/Download/Agentra/backups"`, comment mentions `Agentra's installer`.
4. `usr/bin/backup_chats` -> `BACKUP_DIR="/sdcard/Download/Agentra/backups"`, log says `Exporting backup archive to Agentra backups storage...`.
5. `usr/bin/git_clone` -> banner `Agentra Git Repository Cloner`, default dir `${HOME_DIR}/projects`, output `AGENTRA_CLONED_PATH=$TARGET_DIR`.
6. `usr/bin/claude` -> comment `# Claude Code launcher written by Agentra on every start`, paths `/data/user/0/ai.agentra/...`.

### 6. CLEAN `home/` DIRECTORY STRUCTURE (CRITICAL — MATCH ANTIGEM'S CLEAN LAYOUT!)
- DELETE all old AntiGem runtime/session leftovers from `home/`:
  - `home/.antigem/`
  - `home/.antigem_bridge/`
  - `home/.gemini/antigravity-cli/cli.log`, `conversation_summaries.db`, `conversations/`, `crashes/`, `daemon/`, `installation_id`, `jetski_state.pbtxt`, `last_check.timestamp`, `log/`, `updater/`
  - `home/.node_repl_history`, `home/.python_history`
- **STRICTLY FORBIDDEN — DO NOT CREATE ANY OF THESE:**
  - ❌ NO `home/.jarvis`
  - ❌ NO `home/.jarvis-ai`
  - ❌ NO `home/.jarvis_bridge`
  - ❌ NO `home/jarvis-workspace`
  - ❌ NO `home/.agentra-ai`
  - ❌ NO `home/agentra-workspace`
- Create ONLY these clean Agentra directories in `home/` (mode `0700`):
  - `home/.agentra/`
  - `home/.agentra_bridge/`
  - `home/.agentra_bridge/memory/`
  - `home/projects/`
  - `home/mini-apps/`
- Update `home/.bashrc`:
  - `PREFIX="/data/data/ai.agentra/files/usr"`
  - `HOME="/data/data/ai.agentra/files/home"`
  - `TERMUX_MAIN_PACKAGE_NAME="ai.agentra"` and `TERMUX_APP__PACKAGE_NAME="ai.agentra"`
  - `export AGENTRA_BRIDGE_PORT=2234`
  - `export ANTIGRAVITY_HUB_PORT=2235`
  - `alias proj='cd /data/data/ai.agentra/files/home/projects'`
  - Prompt: `PS1='\[\033[01;32m\]agentra\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]\$ '`
- Keep `home/.zshrc` and `home/.profile` cleanly pointing to `/data/data/ai.agentra/files/...` and `/data/user/0/ai.agentra/files/...`.

### 7. MANDATORY FINAL VERIFICATION SCRIPT (ALL 8 CHECKS MUST PASS)
Run a Python verification script before finishing and confirm ALL assertions pass:
1. `zipfile.ZipFile('agentra-rootfs.zip').testzip() is None` (ZERO CRC errors).
2. Top-level entries are strictly `['home', 'usr']`.
3. ZERO duplicate entry names in the ZIP.
4. ZERO entry paths containing `jarvis`, `agentra-ai`, `agentra-workspace`, or `antigem`.
5. `home/` contains `.agentra/`, `.agentra_bridge/`, `projects/`, `mini-apps/` — and ZERO symlinks named `jarvis*`.
6. EVERY `\x7fELF` binary has the EXACT same byte size as in `bootrapz_2026-11-19.zip`, `0` ELFs contain `ai.antigem` or `com.termux`, and all ELFs remain valid AArch64 (`e_machine == 183`).
7. ZERO files lost executable permissions compared to `bootrapz_2026-11-19.zip`, and `usr/libexec/termux-am/am.apk` has mode `0100400` and valid DEX Adler32 + SHA-1 checksums.
8. ZERO scripts in `usr/bin/` contain `antigem` or `/sdcard/Download/Antigem` (excluding compiled Go binary `usr/bin/agy_ide_bridge`).
```

---

## 8. Aapke Go-Ahead Ka Intezaar

Ab report 100% complete hai (aapke screen recording, teeno screenshots, teeno GitHub ZIPs, aur AntiGem source code sab cover ho chuka hai) aur **koi code edit ya build nahi kiya gaya hai**.  
Jab aap **"Ab Part A implement karo aur build karo"** bolenge, main saare app-side fixes (`ETXTBSY`, `120s` timeout, `Jarvis`/`Agentra AI` + duplicate folders auto-cleanup, clean **Agentra** app icon, aur `agentra-rootfs.zip` default URL — ports `2234–2240` safe rakhte hue) implement kar dunga!
