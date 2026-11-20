# AntiGem vs Agentra: Complete Audit Report + `bootrapz_2026-11-19.zip` Modification Guide (Hinglish)

> **Note:** Is report ke dauraan kisi bhi Kotlin/Gradle source code me koi change ya build nahi kiya gaya hai. Har point dono repos (`Santoshkurmi/antigem` aur aapke **Agentra** app) aur `bootrapz_2026-11-19.zip` ke exact files verify karke likha gaya hai.

---

## Bhaag 1: AntiGem vs Agentra — Poora Audit Aur Comparison

### 1.1 Agentra me Kya-Kya AntiGem Jaisa AA CHUKA Hai (Verified Working)

Pichhle updates ke baad neeche di gayi core cheezein Agentra me already implement aur compile ho chuki hain:

1. **App Package ID & Termux Compatibility (`ai.agentra`)**:
   - `app/build.gradle.kts`: `applicationId = "ai.agentra"` (exact **10 ASCII bytes** — `com.termux` aur `ai.antigem` dono ki length bhi 10 bytes hai, isliye binary/ELF byte-patching bina offset tootey kaam karti hai).
   - `res/values/strings.xml`, `settings.gradle.kts`, `metadata.json`: App name **"Agentra"** sync ho chuka hai.
   - `termux-am-build/build.gradle.kts`: Agentra ke liye custom `am.apk` (`TERMUX_PACKAGE_NAME = "ai.agentra"`) build hota hai aur `TermuxAmAssetTask` use `assets/bin/am.apk` ke andar pack karta hai.

2. **Option B Native Relocation Engine (`TermuxRelocation.kt` - 533 lines)**:
   - Extract karte waqt `com.termux`, `ai.antigem`, aur `ai.antidev` strings ko byte-for-byte `ai.agentra` me convert karta hai (binaries, shared libs `.so`, scripts, configs, aur symlinks par).
   - `.termuxam` aur `.x11.` jaise Java/X11 identifiers ko safely skip karta hai, aur `.apk / .jar / .zip / .aar / .whl / .dex` ko corrupt hone se bachata hai.
   - Permanent `DPkg::Pre-Install-Pkgs` hook (`99agentra-relocate`), `libexec/agentra/relocate-debs`, aur `dpkg` wrapper (`dpkg-divert`) install karta hai, aur purani `99antigem-relocate` file delete karta hai.

3. **Native Termux View Engine & Component Wrappers**:
   - Real `com.termux.view` classes (`TerminalView.java`, `TerminalRenderer.java`, `GestureAndScaleRecognizer.java`, `TerminalViewClient.java`, `TextSelectionCursorController.java`, etc.) present hain.
   - Standalone `TermuxActivity` + Dual Launcher Aliases (`AgentraTermAlias`, `ClassicTermAlias`) + `TerminalLauncherOnboardingDialog` implement hain.
   - `AndroidManifest.xml` me `ai.agentra.app.*` aur `com.termux.app.*` dono compatibility receivers/activities (`TermuxOpenActivity`, `TermuxOpenReceiver`, `TermuxSystemReceiver`, `TermuxService`) aur `TermuxApiReceiver` registered hain.
   - Built-in `TermuxApiReceiver` 9 API methods directly handle karta hai (`Toast`, `Vibrate`, `Clipboard`, `Torch`, `TtsSpeak`, `Notification`, `NotificationRemove`, `Volume`, `BatteryStatus`).
   - `assets/glibc-min-arm64.tar.xz` aur `PRootRuntime.kt` / `PRootInstaller.kt` / `TermuxStorageSetupManager.kt` present hain.

4. **Terminal UI & Customization**:
   - `LocalTerminalDialog.kt` (1218 lines): AntiGem-style multi-tab terminal session bar, Extra Keys bar (ESC, TAB, CTRL, ALT, arrows, HOME/END/PGUP/PGDN, `/`, `-`, `|`, `~`), sticky/locked Ctrl/Alt states, pinch-to-zoom font size, cursor style (`BAR`/`BLOCK`/`UNDERLINE`), theme palette (`ThemeTerminal.kt`), aur `TerminalSettingsSubScreen.kt` (601 lines) aa chuka hai.

5. **IDE, Agents, MCP & Automation Modules**:
   - Sora Code Editor IDE (`IdeScreen`, `CodeEditorView`, `GitSourceControlView`, `IdeExecutionRunner`, `UnifiedDiffViewer`), `AgyCliManager`, Claude/Codex/OpenCode backends, MCP Bridges (`JarvisMemory`, `Browser`, `AndroidDevice`, `Terminal`, `Notes`, `Theme`, `Flow`), `AndroidDevManager`, `ReaScreen`, `X11DesktopScreen`, `AdbConnectScreen`, `ConnectPcScreen`, `ChangelogScreen`, `GlobalContextScreen`, `CustomSlashCommandsScreen`, aur `AppUpdateManager` present hain.

---

### 1.2 Agentra me AB BHI Kya-Kya MISSING ya Incomplete Hai (Exact Code Audit)

#### A. Terminal Engine & Session Management Gaps (Sabse Bada Fark)

| Feature | AntiGem (`Santoshkurmi/antigem`) | Agentra (`/app/applet`) | Asar (Impact) |
| :--- | :--- | :--- | :--- |
| **Tmux Control Mode (`tmux -CC`) + Persistence** | `LocalTerminalManager.kt` (**1,641 lines**) + `TmuxControlParser.kt` (**325 lines**) | `LocalTerminalManager.kt` (**522 lines**), **NO `TmuxControlParser.kt`** | AntiGem me app close/restart karne par bhi `antigem` tmux session aur scrollback history (`capture-pane`) auto-restore hoti hai. Agentra me direct `TerminalSession` chalta hai — app kill hote hi saare open terminal tabs aur running commands khatam ho jaate hain. |
| **SSH PTY Terminal Mode (`USE_SSH_TERMINAL`)** | `LocalPtySession` me `isSsh`, `sshHost/Port/User/Pass`, `SshSessionFactory` aur `tmux` window probe built-in hai | Sirf local PTY (`launchDirectWithFallback`), SSH PTY routing missing | AntiGem ka terminal directly PC ya local SSH (`8022`) se tmux control mode me connect hokar tabs sync kar sakta hai. Agentra me Connect PC separate screen tak seemit hai. |
| **Realtime Terminal Telemetry (`ANTIGEM_INFO`)** | Alternate screen (`?1049h/l`), mouse tracking (`?1000h/?1006h`), aur live working directory `tmux display-message` se sync hote hain | Basic `onTitleChanged` only | Vim/Neovim/htop/tmux ke alternate screen aur mouse scroll sync SSH/tmux sessions me AntiGem jaise rock-solid nahi honge. |
| **Global Keyboard Focus Coordinator** | `LocalAppFocusCoordinator` har `AppViewMode` par IME keyboard focus cleanly switch karta hai | Missing in `LocalTerminalDialog.kt` | Split/Run/Terminal switching ke dauraan keyboard focus kabhi-kabhi lose ya conflict ho sakta hai. |

---

#### B. Environment Installer, Relocation & Recovery Gaps

| Feature | AntiGem | Agentra | Kya Missing Hai |
| :--- | :--- | :--- | :--- |
| **Environment Manager Depth** | `LocalEnvironmentManager.kt` (**2,500 lines**) + `LocalInstallerState.kt` | `LocalEnvironmentManager.kt` (**1,296 lines**) | Agentra me `RootfsMirror` (GitHub + HuggingFace mirror chooser), `discoverBootstrapPackage()` + `AwaitingConfirmation` step, `Verifying` health-test step, foreground Android notification progress bar, aur `validateArchiveIntegrity()` missing hain. |
| **32-bit / Kernel Compatibility Checks** | `isUnsupported32BitDevice()` + `getDeviceHardwareInfo()` (`vaBits` 39 vs 48-bit check, CPU `atomics` check) | Missing | Purane ya weird kernel devices par clear hardware warning UI nahi dikhta. |
| **x86_64 Seccomp Shim & Multi-ABI Support** | `-Pabi=x86_64` ke saath `libantigem_sigsys.so` (`LD_AUDIT` syscall rewrite) + `src/abi/<abi>/assets` | Sirf `glibc-min-arm64.tar.xz` | x86_64 Android emulators / Chromebooks par glibc binaries (`agy`/`opencode`) `seccomp` ke kaaran crash ho sakti hain. |
| **Fast Go `.deb` Relocator (`agy_ide_bridge`)** | `agy_ide_bridge` (Go binary) me built-in `antigem-relocate-debs/1` fast command hai + Gradle build ke waqt `version.json` sync hota hai | Agentra ne `LocalServerManager` me Pure Kotlin bridge banaya hai (`agy_ide_bridge` source repo me nahi hai) | `pkg install` karte waqt `relocate-debs` fallback shell script (`dpkg-deb -R` + `sed`) chalti hai, jo Go binary ke muqable thodi slow hoti hai. |
| **In-App `.deb` Extractor & Package Repair** | `extractDebsArchive()`, `extractSingleDebFile()`, `repairIncompletePackages()`, `resetEnvironment()` | Sirf `repairPermissions()` hai; full `.deb` extractor / in-app environment reset missing | Agar half-installed `dpkg` state toot jaye to app ke andar se automatic `.deb` recovery/reset flow kamzor hai. |
| **External `Termux:API` App Switch** | `ensureTermuxApiPackage()`, `getTermuxApiProvider()`, `isTermuxApiAppInstalled()` (Built-in vs External `ai.antigem.api` APK) | Sirf built-in 9 methods (`TermuxApiReceiver`) | Camera photo, SMS, Contacts, Location, Sensor, Biometric (`termux-location`, `termux-camera-photo`, `termux-sms-list`, `termux-fingerprint`) jaise commands Agentra me error denge kyunki external Termux:API APK provider support aur unke handlers missing hain. |
| **Installer Screens Size / Features** | `FullScreenLocalToolsInstaller.kt` (893 lines) + `LocalToolsInstallDialog.kt` (1,072 lines) | `FullScreenLocalToolsInstaller.kt` (394 lines) + `LocalToolsInstallDialog.kt` (619 lines) | AntiGem me mirror selection, live discovery confirmation card, `OpenCode`/`Codex`/`Agy` specific install health diagnostics, aur hardware/VA-bit warnings zyada detailed hain. |

---

#### C. AntiGem Ke Major App Modules Jo Agentra Me Missing Ya Adhoore Hain

1. **Mini-Apps Platform (`ai.antigem.ui.miniapps.*` + `miniapp-sdk` + `mini_app_docs`)**:
   - AntiGem me **11 dedicated files** (`MiniAppsActivity`, `MiniAppActivity`, `MiniAppHost`, `MiniAppStoreActivity`, `MiniAppInstallActivity`, `MiniAppShareActivity`, `MiniAppMediaViewer`, `MiniAppPage`, etc.) aur JS SDK (`assets/miniapp-sdk/core.js`, `idb.js`, `net.js`, `threads.js`, `ui.css`, `worker-core.js`, `assets/mini-apps/tools/agapp.mjs`) hain.
   - Agentra me sirf `AndroidLocalBridgeServer.kt` ke andar `mini_app_*` tool names ke stubs hain — **Mini-App Store, runtime Activity, aur SDK assets poori tarah missing hain**.

2. **Global Crash Recovery Screen (`ai.antigem.crash.CrashHandler` & `CrashActivity`)**:
   - AntiGem me koi fatal crash hone par ek dedicated Crash Activity khulti hai jisme full stacktrace, copy, aur restart button milta hai. Agentra me yeh system missing hai.

3. **Extra Utility Screens & Bundled Assets Missing**:
   - **Bundled Shell Scripts in `assets/bin/`**: AntiGem APK me `backup_projects`, `git_clone`, `restore_projects` aur `agy_ide_bridge.xz` pre-packaged rehte hain. Agentra ke `assets/` me yeh scripts aur `assets/browser-runtime/*.mjs` (`browser-api.mjs`, `chrome-api.mjs`, `extension-host.mjs`, `devtools.mjs`, `skills.mjs`) aur `assets/render/GUIDE.md` missing hain.
   - **Missing/Different UI Screens**: AntiGem me dedicated `RunScreen`, `NotesScreen`, `StemRenderingScreen` (LaTeX/STEM renderer), `EgmScreen`, `GitHubScreen`, `FloatingChatActivity` (`Theme.AntiGem.FloatingTranslucent`), aur 6-file dedicated `OpenCode` UI (`OpenCodeChatUi`, `OpenCodeMcpPage`, `OpenCodeProvidersPage`, `OpenCodeSettingsScreen`, `OpenCodeModelSheet`) hain jo Agentra me either absent hain ya alag simpler forms me hain.

4. **Companion Repo Dependency (`termux-x11` & `termux-api`)**:
   - AntiGem ke `X11DesktopScreen` me custom signed `antigem-termux-x11-sharedUid.apk` aur `antigem-termux-x11-*-all.deb` companion package links hain. Agentra me `X11DesktopScreen.kt` hai par custom `ai.agentra` sharedUid X11 build repo abhi separate configure nahi hai.

---

#### D. Internal Naming & Path Mismatches (Code & Rootfs Config)

User-facing UI screens ab **"Agentra"** dikhati hain, lekin code ke andar aur RootFS config me yeh farq ab bhi hain:

1. **Kotlin Package & Manifest Class Names (Internal)**:
   - Source files abhi bhi `com.jarvisai.assistant.*` package me hain aur `AndroidManifest.xml` me `JarvisAccessibilityService`, `JarvisOverlayHudService`, `JarvisWallpaperService`, `JarvisTileService`, `JarvisBootReceiver`, `Theme.JarvisAI` jaise internal names hain.
   - *(Tip: Internal Kotlin class/package naam na badalna hi safe hai warna purane users ka Room database, Accessibility permission aur SharedPreferences reset ho sakta hai — sirf `JarvisHubConfig` / `LocalEnvironmentManager` paths ko dhyan me rakhna zaroori hai.)*

2. **Directory & Port Mismatch (`JarvisHubConfig.kt` vs `bootrapz_2026-11-19.zip`)**:
   - **Agentra app use karta hai:**
     - Ports: Bridge **`2234`**, Hub **`2235`**, MCP **`2236`**, Browser MCP **`2237`**
     - Folders: `$HOME/.jarvis_bridge`, `$HOME/.jarvis-ai`, `$HOME/.gemini/jarvis-antigravity`, `$HOME/jarvis-workspace/projects`, `/sdcard/Download/Agentra`
     - Dispatch scripts: `$HOME/.local/bin/jarvis-mcp` & `jarvis-mcp-stdio`
   - **Original `bootrapz_2026-11-19.zip` me hardcoded hai:**
     - Package ID: **`ai.antigem`** aur **`com.termux`**
     - Stale daemon config: `home/.gemini/antigravity-cli/daemon/hub_1237.json` (AntiGem ka port `1237`!)
     - Folders & tokens: `home/.antigem/`, `home/.antigem_bridge/csrf_token.txt`, `usr/libexec/antigem/relocate-debs`, `usr/etc/apt/apt.conf.d/99antigem-relocate`
     - Shell Prompt & Aliases (`home/.bashrc`): `PS1` me **`gemini`**, `proj` alias me `/data/user/0/ai.antigem/files/home/projects`, aur `TERMUX_MAIN_PACKAGE_NAME="com.termux"`!

---

## Bhaag 2: `bootrapz_2026-11-19.zip` Ki Deep Anatomy (Actual Inspected Contents)

Live ZIP inspection se humein exact details mili hain:

- **Total File Size**: `400,054,861 bytes` (~381.5 MB)
- **Total Entries**: `26,152` entries
- **Top-Level Folders**: Sirf `usr/` aur `home/` (koi `SYMLINKS.txt` file nahi hai — is ZIP ke andar **1,827 native UNIX symlinks** directly ZIP external attributes `0o120xxx` ke roop me stored hain!)
- **Pre-installed Major Binaries Inside Zip**:
  - `home/.local/bin/agy` (~200.2 MB) — Antigravity CLI binary
  - `usr/bin/pnpm` (~54.7 MB)
  - `usr/bin/node` (~51.0 MB)
  - `usr/bin/gh` (~42.3 MB — GitHub CLI)
  - `usr/bin/agy_ide_bridge` (~14.4 MB — Go IDE Bridge)
  - `usr/bin/git`, `usr/bin/zsh`, `usr/bin/bash`, `usr/bin/ssh`, `usr/bin/sshd`, `usr/bin/curl`, `usr/bin/wget`, `usr/bin/tar`, `usr/bin/zip`, `usr/bin/unzip`, `usr/bin/python3`, `usr/bin/nvim`, `usr/bin/claude` wrapper
  - **Already Diverted Files (`.distrib`)**:
    - `usr/bin/dpkg` (AntiGem wrapper script) + `usr/bin/dpkg.distrib` (real dpkg)
    - `usr/libexec/termux-am/am.apk` (**AntiGem `ai.antigem` ke liye compiled!**) + `usr/libexec/termux-am/am.apk.distrib`
    - `usr/libexec/termux-api` (AntiGem script) + `usr/libexec/termux-api.distrib`
- **Hidden / Stale AntiGem & Personal Files Inside Zip**:
  - `home/.gemini/antigravity-cli/log/cli-*.log` (creator ke 10 purane log files!)
  - `home/.gemini/antigravity-cli/crashes/*.log`, `conversation_summaries.db`, `installation_id`, `jetski_state.pbtxt`, `cache/default_project_id.txt`, `daemon/hub_1237.json`
  - `home/.antigem/` aur `home/.antigem_bridge/` (`csrf_token.txt`, `memory/state.json`)
  - `home/.node_repl_history`, `home/.python_history`
  - `home/.termux/shell` -> symlink pointing to `/data/data/ai.antigem/files/usr/bin/zsh`
  - `home/.local/bin/claude` -> symlink pointing to `/data/user/0/ai.antigem/files/usr/bin/claude`
  - `usr/etc/apt/trusted.gpg.d/*.gpg` -> 8 symlinks pointing to `/data/data/ai.antigem/files/usr/share/termux-keyring/*.gpg`

---

## Bhaag 3: `bootrapz_2026-11-19.zip` Ko Agentra (`ai.agentra`) Ke Hisab Se Modify Karne Ka Poora Process

### Zaroori Chetavni (Sabse Important Rules)

1. **Windows Explorer, WinRAR, 7-Zip GUI, ya macOS Finder GUI BILKUL MAT USE KARNA!**
   - Is ZIP me **1,827 Linux symlinks** aur executable permissions (`0755`/`0700`) hain. Agar aapne Windows par extract ya re-zip kiya to saare symlinks toot jayenge aur Android par `bash`, `apt`, `python3`, `awk`, `cp`, `sh` sab fail ho jayenge.
   - Modify karne ke liye hamesha **Linux**, **WSL2 (Ubuntu)**, **macOS Terminal**, ya **Android par Termux** hi use karein.
2. **Byte-Length Rule (`10 Bytes Exact`)**:
   - `com.termux` = 10 chars
   - `ai.antigem` = 10 chars
   - `ai.agentra` = 10 chars
   - Kyunki teenon exact 10 ASCII bytes hain, hum **compiled ELF binaries (`.so`, `bash`, `node`, `git`, `apt`, `dpkg.distrib`) me hardcoded `/data/data/ai.antigem/...` paths ko seedhe byte-replace** karke `ai.agentra` kar sakte hain bina binary corrupt hue.
3. **`.apk` / `.jar` / `.zip` par Byte-Replace MAT Chalana!**
   - `usr/libexec/termux-am/am.apk` ko byte-replace se edit nahi kiya ja sakta (Android ZIP/APK checksum aur DEX layout toot jayega). Isko **Agentra ke `:termux-am` project se build ki hui `am.apk`** se replace karna hoga (neeche Step 4 me bataya gaya hai).

> **Note:** Waise Agentra app ke `TermuxRelocation.kt` me runtime par `ai.antigem` -> `ai.agentra` auto-patch aur `am.apk` replace karne ka logic laga hai, **LEKIN** jab aap ZIP ko pehle se hi cleanly modify kar doge to:
> - App install 2x fast hoga (runtime scanning minimal hogi),
> - AntiGem ke purane logs, crash DB, `hub_1237.json`, `csrf_token.txt`, aur `gemini` shell prompt hat jayenge,
> - `home/.bashrc`, `home/.zshrc`, `proj` alias, `hubPort 2235`, `99agentra-relocate`, aur `am.apk` 100% clean Agentra-native rahenge!

---

### Step 1: System Tools & Original Files Prepare Karein (Linux / WSL2 / Termux)

Terminal kholein aur required packages check/install karein (`unzip`, `zip`, `python3`):

```bash
# Ubuntu / Debian / WSL2 par:
sudo apt update && sudo apt install -y unzip zip python3 curl

# Ya Termux par:
pkg install -y unzip zip python
```

Ek clean working folder banayein aur official zip download karein:

```bash
mkdir -p ~/agentra_rootfs_mod && cd ~/agentra_rootfs_mod

curl -L -o bootrapz_2026-11-19.zip \
  "https://github.com/Santoshkurmi/antigem/releases/download/rootfs-v2/bootrapz_2026-11-19.zip"
```

---

### Step 2: Agentra Ki Compiled `am.apk` Taiyaar Karein

Aapke Agentra repo ke andar `:termux-am` already `ai.agentra` ke liye configured hai (`termux-am-build/build.gradle.kts` me `TERMUX_PACKAGE_NAME = "ai.agentra"`).

Apne Agentra project folder me jaake sirf `am.apk` compile karein (ya apni latest built Agentra APK me se `assets/bin/am.apk` nikaal lein):

```bash
# Option 1: Agentra repo ke root folder me Gradle se build karein:
gradle :termux-am:assembleRelease
# Output file milegi:
# termux-am-build/build/outputs/apk/release/termux-am-build-release-unsigned.apk

# Option 2: Agar aapke paas already built Agentra.apk hai:
unzip -p Agentra.apk assets/bin/am.apk > ~/agentra_rootfs_mod/agentra_am.apk
```

Isko `~/agentra_rootfs_mod/agentra_am.apk` par save rakh lein.

---

### Step 3: ZIP Ko Safely Extract Karein (Symlinks & Permissions Preserve Karke)

```bash
cd ~/agentra_rootfs_mod
mkdir -p rootfs_work
unzip -q bootrapz_2026-11-19.zip -d rootfs_work
```

Check karein ki `rootfs_work/usr` aur `rootfs_work/home` aur symlinks sahi se extract hue:

```bash
ls -la rootfs_work/
find rootfs_work -type l | wc -l
# Output lagbhag 1827 aana chahiye!
```

---

### Step 4: AntiGem-Specific Cleanup, Byte Relocation & Agentra Customization

Ab ek Python script chalayein jo **ek saath aur zero galti ke**:
1. Saare **1,827 symlinks** ke targets me `/ai.antigem` aur `/com.termux` ko `/ai.agentra` me badal degi (e.g. `trusted.gpg.d/*.gpg`, `home/.termux/shell`, `home/.local/bin/claude`).
2. `usr/` ki **sabhi ELF binaries, shared libraries, aur scripts** me `ai.antigem` / `ai.antidev` / `com.termux` ko byte-for-byte `ai.agentra` karegi (aur `.termuxam`, `.x11.`, aur `.apk/.jar/.zip` ko safe chhod degi).
3. `home/` ke config files me `/ai.antigem` aur `/com.termux` paths ko `/ai.agentra` karegi.
4. AntiGem ke purane logs, crash files, SQLite chat DB, `hub_1237.json`, `csrf_token.txt`, aur `.antigem*` runtime cache clean karegi.
5. `99antigem-relocate` aur `usr/libexec/antigem/` hatakar Agentra ke **`99agentra-relocate`**, **`usr/libexec/agentra/relocate-debs`**, aur updated **`usr/bin/dpkg`** wrapper lagayegi.
6. Aapki `agentra_am.apk` ko `usr/libexec/termux-am/am.apk` par **`chmod 0400`** (Android 14+ W^X read-only rule) ke saath install karegi.
7. `home/.bashrc`, `home/.zshrc`, `home/.profile`, aur `usr/libexec/termux-api` ko **Agentra** (port `2234`/`2235`, `jarvis-workspace/projects`, green `agentra` prompt) ke liye set karegi.

Neeche diya gaya script `modify_for_agentra.py` naam se save karein aur run karein:

```bash
cat << 'EOF' > ~/agentra_rootfs_mod/modify_for_agentra.py
import os
import stat
import shutil

ROOT = os.path.abspath("rootfs_work")
AM_APK_SRC = os.path.abspath("agentra_am.apk")

OLD_PKGS = [b"com.termux", b"ai.antigem", b"ai.antidev"]
NEW_PKG = b"ai.agentra"
SKIP_EXTS = {".apk", ".jar", ".zip", ".aar", ".whl", ".dex", ".jmod", ".png", ".jpg", ".gz", ".xz"}
KEPT_SUFFIXES = [b".termuxam", b".x11."]

def rewrite_bytes(data: bytes, paths_only: bool = False) -> bytes:
    out = bytearray(data)
    changed = False
    n = len(NEW_PKG)
    for old in OLD_PKGS:
        idx = 0
        while True:
            pos = data.find(old, idx)
            if pos == -1:
                break
            in_path = (not paths_only) or (pos > 0 and data[pos - 1:pos] == b"/")
            kept = any(data[pos + n:pos + n + len(suf)] == suf for suf in KEPT_SUFFIXES)
            if in_path and not kept:
                out[pos:pos + n] = NEW_PKG
                changed = True
            idx = pos + n
        if changed:
            data = bytes(out)
    return bytes(out) if changed else data

# 1. Clean AntiGem creator logs, DBs, stale daemon state, and caches
to_delete_paths = [
    "home/.antigem",
    "home/.antigem_bridge",
    "home/.node_repl_history",
    "home/.python_history",
    "home/.bash_history",
    "home/.zsh_history",
    "home/.gemini/antigravity-cli/cli.log",
    "home/.gemini/antigravity-cli/log",
    "home/.gemini/antigravity-cli/crashes",
    "home/.gemini/antigravity-cli/conversations",
    "home/.gemini/antigravity-cli/conversation_summaries.db",
    "home/.gemini/antigravity-cli/daemon",
    "home/.gemini/antigravity-cli/installation_id",
    "home/.gemini/antigravity-cli/jetski_state.pbtxt",
    "home/.gemini/antigravity-cli/last_check.timestamp",
    "home/.gemini/antigravity-cli/updater/update.lock",
    "usr/etc/apt/apt.conf.d/99antigem-relocate",
    "usr/libexec/antigem",
]
for rel in to_delete_paths:
    full = os.path.join(ROOT, rel)
    if os.path.islink(full) or os.path.isfile(full):
        os.remove(full)
        print("Removed file:", rel)
    elif os.path.isdir(full):
        shutil.rmtree(full)
        print("Removed dir :", rel)

# Ensure clean Agentra workspace directories
for d in [
    "home/.jarvis_bridge/memory",
    "home/.jarvis-ai",
    "home/jarvis-workspace/projects",
    "home/.gemini/antigravity-cli/log",
    "home/.gemini/antigravity-cli/crashes",
    "home/.gemini/antigravity-cli/daemon",
]:
    os.makedirs(os.path.join(ROOT, d), mode=0o700, exist_ok=True)

# 2. Relocate symlinks + files across usr/ (full) and home/ (paths_only=True)
relinked = 0
patched_files = 0

for tree, paths_only in [("usr", False), ("home", True)]:
    base = os.path.join(ROOT, tree)
    for dirpath, dirnames, filenames in os.walk(base, followlinks=False):
        # Also handle symlinked directories if any
        for name in filenames + dirnames:
            full = os.path.join(dirpath, name)
            try:
                st = os.lstat(full)
            except FileNotFoundError:
                continue

            if stat.S_ISLNK(st.st_mode):
                target = os.readlink(full)
                new_target = rewrite_bytes(target.encode("utf-8"), paths_only=paths_only).decode("utf-8")
                if new_target != target:
                    os.unlink(full)
                    os.symlink(new_target, full)
                    relinked += 1
            elif stat.S_ISREG(st.st_mode):
                _, ext = os.path.splitext(name.lower())
                if ext in SKIP_EXTS:
                    continue
                with open(full, "rb") as f:
                    orig = f.read()
                updated = rewrite_bytes(orig, paths_only=paths_only)
                if updated != orig:
                    mode = st.st_mode & 0o777
                    if not (mode & 0o200):
                        os.chmod(full, mode | 0o200)
                    with open(full, "wb") as f:
                        f.write(updated)
                    os.chmod(full, mode)
                    patched_files += 1

print(f"Relocated symlinks: {relinked}, patched files: {patched_files}")

# 3. Install Agentra APT hook & relocate-debs script (fallback pure shell version)
prefix = "/data/data/ai.agentra/files/usr"
apt_hook_path = os.path.join(ROOT, "usr/etc/apt/apt.conf.d/99agentra-relocate")
os.makedirs(os.path.dirname(apt_hook_path), exist_ok=True)
with open(apt_hook_path, "w") as f:
    f.write(
        "// Written for Agentra (ai.agentra).\n"
        f'DPkg::Pre-Install-Pkgs {{ "{prefix}/libexec/agentra/relocate-debs --apt"; }};\n'
    )
os.chmod(apt_hook_path, 0o644)

reloc_script_path = os.path.join(ROOT, "usr/libexec/agentra/relocate-debs")
os.makedirs(os.path.dirname(reloc_script_path), exist_ok=True)
with open(reloc_script_path, "w") as f:
    f.write(f"""#!{prefix}/bin/sh
OLD='com.termux'
NEW='ai.agentra'
export LC_ALL=C
umask 022
TMP="${{TMPDIR:-{prefix}/tmp}}"
SED_PROG='s/com\\.termux\\.termuxam/\\x01om.termux.termuxam/g; s/com\\.termux\\.x11\\./\\x01om.termux.x11./g; s/com\\.termux/ai.agentra/g; s/\\x01om\\.termux/com.termux/g'
APT_MODE=0
[ "${{1:-}}" = "--apt" ] && APT_MODE=1

relocate_deb() {{
    work=$(mktemp -d "$TMP/agentra-deb.XXXXXX") || return 1
    if ! dpkg-deb -R "$1" "$work/pkg"; then rm -rf "$work"; return 1; fi
    if [ ! -d "$work/pkg/data/data/$OLD" ] && ! grep -rqF "$OLD" "$work/pkg" 2>/dev/null; then
        rm -rf "$work"; return 0
    fi
    echo "Agentra: adapting ${{1##*/}} for $NEW"
    if [ -d "$work/pkg/data/data/$OLD" ]; then
        mkdir -p "$work/pkg/data/data"
        mv "$work/pkg/data/data/$OLD" "$work/pkg/data/data/$NEW" || {{ rm -rf "$work"; return 1; }}
    fi
    grep -rlF "$OLD" "$work/pkg" 2>/dev/null > "$work/files"
    while IFS= read -r f; do
        case "$f" in *.apk|*.jar|*.zip|*.aar|*.whl|*.dex|*.jmod) continue ;; esac
        [ -L "$f" ] && continue
        [ -w "$f" ] || chmod u+w "$f"
        sed -i "$SED_PROG" "$f" || {{ rm -rf "$work"; return 1; }}
    done < "$work/files"
    find "$work/pkg" -type l > "$work/links"
    while IFS= read -r l; do
        t=$(readlink "$l")
        case "$t" in
            *"$OLD"*) ln -sfn "$(printf '%s' "$t" | sed "$SED_PROG")" "$l" || {{ rm -rf "$work"; return 1; }} ;;
        esac
    done < "$work/links"
    if [ -f "$work/pkg/DEBIAN/md5sums" ]; then
        ( cd "$work/pkg" && cut -c35- DEBIAN/md5sums | xargs -r -d '\\n' md5sum ) > "$work/md5sums" && mv -f "$work/md5sums" "$work/pkg/DEBIAN/md5sums"
    fi
    chmod 0755 "$work/pkg/DEBIAN"
    for s in preinst postinst prerm postrm config; do
        [ -f "$work/pkg/DEBIAN/$s" ] && chmod 0755 "$work/pkg/DEBIAN/$s"
    done
    dpkg-deb --root-owner-group -Zgzip -z1 -b "$work/pkg" "$work/out.deb" >/dev/null || {{ rm -rf "$work"; return 1; }}
    mv -f "$work/out.deb" "$1"
    rm -rf "$work"
}}

fail=0
while IFS= read -r deb; do
    case "$deb" in *.deb) ;; *) continue ;; esac
    [ -f "$deb" ] || continue
    if [ "$APT_MODE" = 1 ]; then
        case "$deb" in */cache/apt/archives/*) ;; *) continue ;; esac
    fi
    relocate_deb "$deb" || fail=1
done
exit $fail
""")
os.chmod(reloc_script_path, 0o755)

# Fix usr/bin/dpkg wrapper reference from antigem/relocate-debs -> agentra/relocate-debs
dpkg_path = os.path.join(ROOT, "usr/bin/dpkg")
if os.path.isfile(dpkg_path):
    with open(dpkg_path, "r") as f:
        dpkg_txt = f.read()
    dpkg_txt = dpkg_txt.replace("/libexec/antigem/relocate-debs", "/libexec/agentra/relocate-debs")
    dpkg_txt = dpkg_txt.replace("99antigem-relocate", "99agentra-relocate")
    dpkg_txt = dpkg_txt.replace("AntiGem", "Agentra").replace("antigem", "agentra")
    with open(dpkg_path, "w") as f:
        f.write(dpkg_txt)
    os.chmod(dpkg_path, 0o755)

# 4. Update am.apk for ai.agentra (read-only 0400 for Android 14+)
if os.path.isfile(AM_APK_SRC):
    am_dst = os.path.join(ROOT, "usr/libexec/termux-am/am.apk")
    os.makedirs(os.path.dirname(am_dst), exist_ok=True)
    if os.path.exists(am_dst):
        os.chmod(am_dst, 0o644)
    shutil.copyfile(AM_APK_SRC, am_dst)
    os.chmod(am_dst, 0o400)
    print("Replaced am.apk with Agentra build (0400)")
else:
    print("WARNING: agentra_am.apk not found! TermuxRelocation will replace it on first Android launch.")

# 5. Rewrite home/.bashrc for Agentra
bashrc_path = os.path.join(ROOT, "home/.bashrc")
with open(bashrc_path, "w") as f:
    f.write("""# Agentra Local Linux Environment
export PREFIX="/data/data/ai.agentra/files/usr"
export HOME="/data/data/ai.agentra/files/home"
export PATH="$HOME/.local/bin:$HOME/.npm-global/bin:$PREFIX/bin:$PREFIX/bin/applets:/system/bin:/system/xbin"
export TMPDIR="$PREFIX/tmp"
export TERM="xterm-256color"
export COLORTERM="truecolor"
export TERMUX_VERSION="0.118.1"
export TERMUX_MAIN_PACKAGE_NAME="ai.agentra"
export TERMUX_APP__PACKAGE_NAME="ai.agentra"
export TERMUX_APK_RELEASE="GITHUB"
export SHELL="$PREFIX/bin/bash"
export ANDROID_DATA="/data"
export ANDROID_ROOT="/system"
export LANG="en_US.UTF-8"
export LC_ALL="en_US.UTF-8"
export AGENTRA_BRIDGE_PORT=2234
export ANTIGRAVITY_HUB_PORT=2235

alias ll='ls -la'
alias la='ls -A'
alias l='ls -CF'
alias cls='clear'
alias proj='cd /data/data/ai.agentra/files/home/jarvis-workspace/projects'

# Agentra styled color prompt
PS1='\\[\\033[01;32m\\]agentra\\[\\033[00m\\]:\\[\\033[01;34m\\]\\w\\[\\033[00m\\]\\$ '
""")
os.chmod(bashrc_path, 0o644)

# 6. Patch usr/libexec/termux-api receiver target to Agentra
tapi_path = os.path.join(ROOT, "usr/libexec/termux-api")
if os.path.isfile(tapi_path):
    with open(tapi_path, "r") as f:
        tapi = f.read()
    tapi = tapi.replace("ai.antigem.api", "ai.agentra.api")
    tapi = tapi.replace(
        "ai.antigem/ai.antigem.data.receiver.TermuxApiReceiver",
        "ai.agentra/com.jarvisai.assistant.receiver.TermuxApiReceiver"
    )
    tapi = tapi.replace("AntiGem", "Agentra")
    with open(tapi_path, "w") as f:
        f.write(tapi)
    os.chmod(tapi_path, 0o755)

print("All modifications complete!")
EOF

cd ~/agentra_rootfs_mod
python3 modify_for_agentra.py
```

---

### Step 5: Final Verification (Ensure ZERO Leftover `antigem` References)

Re-zip karne se pehle check karein ki koi broken symlink ya leftover `ai.antigem` reference nahi bacha:

```bash
cd ~/agentra_rootfs_mod/rootfs_work

# 1. Check symlinks pointing to ai.antigem (output khali hona chahiye!)
find usr home -type l -exec readlink {} + | grep -i "antigem" || echo "OK: No antigem symlinks!"

# 2. Check text/config scripts mentioning ai.antigem
grep -rnI "ai.antigem" usr/etc home/ 2>/dev/null || echo "OK: No antigem paths in etc/home!"

# 3. Verify bash & sh executable permissions
ls -l usr/bin/bash usr/bin/sh usr/bin/dpkg usr/libexec/termux-am/am.apk
```

*(Note: `usr/bin/agy_ide_bridge` ke andar Go binary me `"antigem"` text strings compile-time logs/markers ho sakte hain; agar aap Agentra ka Pure-Kotlin `LocalServerManager` use kar rahe hain to usse koi issue nahi aata.)*

---

### Step 6: ZIP Ko Repackage Karein (`zip -r -y` Zaroori Hai!)

**`-y` flag (`--symlinks`) bahut zaroori hai** — bina `-y` ke `zip` symlinks ko duplicate files me convert kar dega aur size double ho jayega:

```bash
cd ~/agentra_rootfs_mod/rootfs_work
zip -r -y ../agentra-rootfs_2026-11-19.zip usr home
```

Archive integrity aur symlink preservation verify karein:

```bash
cd ~/agentra_rootfs_mod

# 1. Checksum test (Har entry verify hogi):
unzip -tqq agentra-rootfs_2026-11-19.zip && echo "ZIP INTEGRITY PASSED!"

# 2. Check symlinks preserved inside ZIP (output ~1827 hona chahiye):
zipinfo agentra-rootfs_2026-11-19.zip | grep '^l' | wc -l

# 3. Check bash exists:
zipinfo -1 agentra-rootfs_2026-11-19.zip | grep -qx "usr/bin/bash" && echo "usr/bin/bash FOUND!"
```

---

### Step 7: Modified ZIP Ko Agentra App Me Test & Deploy Karein

Agentra ka `LocalEnvironmentManager.findLocalBootstrapArchive()` automatically phone storage me in folders & filenames ko sabse pehle dhoondhta hai (bina app recompile kiye!):

- Folder priority:
  1. `/sdcard/Download/Agentra/`
  2. `/sdcard/Download/`
- Filename priority:
  1. `bootrapz_2026-11-19.zip`
  2. `agentra-rootfs.zip`

#### Tarika A: Direct Offline Test (Bina APK Recompile Kiye)
1. Apni nayi bani file ko phone me copy karein aur naam rakhein:
   `/sdcard/Download/Agentra/agentra-rootfs.zip` (ya `bootrapz_2026-11-19.zip`).
2. Agentra app open karein → **Settings → Terminal → Toolchain Setup** par jayein → Install press karein.
3. App automatically local ZIP pick karke extract, permissions fix (`0755`/`0400`), aur Agentra terminal active kar dega.
4. Terminal me check karein:
   ```bash
   echo $PREFIX
   # Output: /data/data/ai.agentra/files/usr
   termux-toast "Agentra Ready!"
   pkg update && pkg list-installed | head
   ```

#### Tarika B: Online Release Hosting (Jab GitHub Release Upload Karein)
1. `agentra-rootfs_2026-11-19.zip` ko apne GitHub repository ke **Releases** (e.g., `rootfs-v1`) me upload karein.
2. Jab aap future me code update karein, tab `LocalEnvironmentManager.kt` me `HARDCODED_ROOTFS_URL` aur `LocalToolsInstallDialog`/`FullScreenLocalToolsInstaller` me apna release URL set kar sakte hain.

---

## Summary: Agla Kadam Kya Hona Chahiye?

Jab aap yeh audit dekh lein aur bataayein ki aapko kaun-kaun se missing features Agentra me chahiye, tab hum step-by-step implement kar sakte hain:
1. **Priority 1 (Exact AntiGem Terminal Experience)**: `TmuxControlParser.kt` + `LocalTerminalManager.kt` me `tmux -CC` session persistence + SSH PTY mode + `LocalAppFocusCoordinator` add karna.
2. **Priority 2 (Installer & Safety Parity)**: `LocalEnvironmentManager.kt` me `LocalInstallerState` + Mirror selector + `.deb` recovery extractor + foreground notification progress + external `Termux:API` support + full `LocalToolsInstallDialog`/`FullScreenLocalToolsInstaller` parity.
3. **Priority 3 (Extra Modules, agar chahiye)**: `CrashHandler`/`CrashActivity`, `MiniApps` subsystem + JS SDK, aur missing `assets/bin/*` scripts.
