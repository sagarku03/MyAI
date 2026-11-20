# AntiGem vs JARVIS AI: In-Built Termux, Dual App Icon & `bootrapz_2026-11-19.zip` Complete Technical Audit (Hinglish)

Yeh document **Santoshkurmi/antigem** repo, uske **`bootrapz_2026-11-19.zip`** (~382 MiB / 400 MB) archive, aur aapke **JARVIS AI (`com.jarvisai.assistant`)** app ka end-to-end deep code-level audit aur comparison hai.

---

## 1. Quick Summary (Asli Raaz Kya Hai?)

1. **"1 App Install Karne Par 2 Apps Kaise Bante Hain?"**  
   Yeh **2 alag APKs nahi** hain — yeh **1 hi APK** hai jo Android ke **`<activity-alias>` + alag `taskAffinity` + `PackageManager.setComponentEnabledSetting()`** trick use karta hai. User ke choice par launcher me ek second standalone **Termux / AntiTerm** icon enable/disable ho jata hai, jo background me same `filesDir` aur `TermuxService` share karta hai.
2. **"Bina External Termux & Bina PRoot Ke In-Built Termux Kaise Chalta Hai?"**  
   AntiGem **PRoot use hi nahi karta** (normal flow me). Iske piche **4 core tricks** hain:
   - **`targetSdk = 28` (Android 9)** -> Android 10+ ka SELinux W^X block bypass ho jata hai, isliye app apne `filesDir/usr/bin` ke binaries direct `execve()` kar sakti hai.
   - **10-Character `applicationId` Trick (`"ai.antigem"` = 10 bytes == `"com.termux"` = 10 bytes)** -> Termux ke `.deb` packages me `/data/data/com.termux` compiled hota hai. AntiGem **`TermuxRelocation.kt`** se har ELF binary, shared `.so`, script aur symlink ko **byte-for-byte in-place patch** karke `com.termux` ko `ai.antigem` kar deta hai — bina ELF offsets tode!
   - **APT / DPKG Auto-Relocation Hooks** -> Jab user baad me `pkg install` ya `apt install` chalata hai, `/etc/apt/apt.conf.d/99antigem-relocate` aur `/usr/bin/dpkg` wrapper `.deb` unpack hone se pehle hi `com.termux` ko `ai.antigem` me rewrite kar dete hain.
   - **Custom Compiled `am.apk` (`:termux-am`) + Bundled Minimal `glibc` + Built-in `Termux:API` Dispatcher** -> Android intent commands (`termux-open`, `termux-wake-lock`, `termux-setup-storage`, Toast/Clipboard/TTS) direct AntiGem ke package name par fire hote hain.
3. **`bootrapz_2026-11-19.zip` Kya Hai & Kya Aap Ise Modify Karke JARVIS AI Me Daal Sakte Ho?**  
   - Yeh vanilla Termux bootstrap **nahi** hai — yeh AntiGem ke `scripts/backup_bootstrap.sh` se banaya gaya ek **full pre-installed Linux + Dev + AI CLI snapshot** hai (158 `dpkg` packages, Clang/LLVM, Node 26, pnpm, Python 3.14, Git, GitHub CLI `gh`, Neovim, Zsh, Glibc, plus `agy` 191 MB CLI).
   - **Warning:** Is zip ke saare binaries & scripts already **`ai.antigem` paths** par patched hain, jabki aapka **JARVIS AI** `com.jarvisai.assistant` (22 chars) aur **PRoot (`com.termux` bind-mount)** use karta hai. Direct drop karne par paths clash honge — **lekin** neeche Section 5 & 6 me dono exact tareeke diye gaye hain jinse aap is zip ko 100% cleanly JARVIS ke liye customize kar sakte hain.

---

## 2. "1 App Install -> 2 Apps" Feature Kaise Kaam Karta Hai?

Android me kisi ek APK ke paas ek se zyada Home Screen / App Drawer icons ho sakte hain agar Manifest me multiple `MAIN` + `LAUNCHER` intent-filters hon. AntiGem isko clean tareeke se **`<activity-alias>`** se karta hai:

### A. Manifest Architecture (`app/src/main/AndroidManifest.xml`)
1. **`MainActivity`** -> Default main launcher icon (`android:taskAffinity="${applicationId}.main"`).
2. **`TerminalActivity`** -> Isme **koi `LAUNCHER` intent-filter nahi** hai (warna bina setting ke hamesha 2 icons dikhenge). Isme `android:taskAffinity="${applicationId}.terminal"` aur `android:launchMode="singleTask"` diya gaya hai, taaki jab standalone terminal khule to Android Recents screen me woh ek **bilkul alag app card** ki tarah dikhe!
3. **Do `<activity-alias>` entries** (dono `TerminalActivity` ko target karte hain, aur dono `android:enabled="false"` se start hote hain):
   - **`.AntiTermAlias`**: Label `AntiTerm`, app ka main icon (`@mipmap/ic_launcher`).
   - **`.ClassicTermAlias`**: Label `Termux`, classic black Termux icon (`@mipmap/ic_termux_launcher` + `@drawable/ic_termux_foreground` + `@drawable/ic_termux_background`).

### B. Runtime Toggle (`TerminalLauncherManager.kt` + Onboarding)
- First launch par `TerminalLauncherOnboardingDialog` user se puchta hai ki kya aapko app drawer me alag **Termux / AntiTerm** icon chahiye (aur Settings > Appearance me bhi toggle hai).
- Toggle karne par `TerminalLauncherManager.applyLauncherSetting(context, enabled, style)` Android ke `PackageManager` ko call karta hai:
  - `pm.setComponentEnabledSetting(componentName, COMPONENT_ENABLED_STATE_ENABLED / DISABLED, PackageManager.DONT_KILL_APP)`
- Android launcher turant 2nd app icon dikha deta hai (ya chhupa deta hai) **bina app restart kiye**!
- Dono icons same app UID, same `filesDir` (`/data/data/.../files/usr` aur `home`), aur same `TermuxService` share karte hain — isliye external Termux app ki zero zaroorat hoti hai aur koi `Permission denied` / UID mismatch nahi hota.

### C. Bonus: Debug vs Release Side-by-Side
AntiGem `app/build.gradle.kts` me `androidComponents` use karke `debug` build ka `applicationId` `"ai.antidev"` (10 chars) aur `release` ka `"ai.antigem"` (10 chars) rakhta hai, aur local ports bhi alag rakhta hai (`1236/1237/8767` vs `1238/1239/8768`).

---

## 3. In-Built Termux Bina PRoot Ke Kaise Chalta Hai? (AntiGem Engine Breakdown)

Termux ke official `.deb` packages me `/data/data/com.termux/files/usr` hardcoded hota hai (ELF dynamic linker paths, OpenSSL `openssl.cnf`, `apt`/`dpkg` db paths, `git-core`, shebangs `#!/data/data/com.termux/files/usr/bin/bash`, symlinks). Agar koi aur app (`com.example` ya `com.jarvisai.assistant`) unhe direct chalaye, to woh fail ho jaate hain.

AntiGem ne isko **5 layers** me solve kiya hai:

### Layer 1: `targetSdk = 28`
- Android 10 (API 29+) me SELinux app ke writable `filesDir` se binaries execute (`execve`) karne se rokta hai (`W^X` policy).
- `targetSdk = 28` rakhne se Android 11/12/13/14/15 sabhi phones par app apne `filesDir/usr/bin/*` ELF binaries ko **natively direct execute** kar sakti hai (JARVIS AI me bhi `targetSdk = 28` already set hai).

### Layer 2: 10-Byte Exact Package Name + `TermuxRelocation.kt`
- `"com.termux"` ki length exactly **10 ASCII bytes** hai (`c-o-m-.-t-e-r-m-u-x`).
- `"ai.antigem"` ki length bhi **10 ASCII bytes** hai (`a-i-.-a-n-t-i-g-e-m`), aur debug `"ai.antidev"` bhi **10 ASCII bytes** hai!
- Jab ZIP extract hota hai, `TermuxRelocation.relocateTree()` har file, ELF binary, `.so` library, config, script aur symlink me `com.termux` dhundh kar **same-length `ai.antigem`** se overwrite karta hai (`RandomAccessFile` in-place write):
  - Binary files ka size aur ELF section/symbol offsets **1 byte bhi change nahi hote**, isliye binary corrupt nahi hoti!
  - `usr/` me saare occurrences rewrite hote hain; `home/` me sirf `/com.termux` (`pathsOnly = true`) rewrite hota hai.
  - `.apk`, `.jar`, `.zip`, `.aar`, `.whl`, `.dex`, `.jmod` aur `.git/objects` skip kiye jaate hain; aur `com.termux.termuxam` / `com.termux.x11.` Java package names preserve kiye jaate hain.

### Layer 3: Future `pkg install` / `apt install` Hook (`99antigem-relocate` + `dpkg` wrapper)
Agar user baad me `pkg install python` ya `apt install ffmpeg` kare, to internet se aane wale `.deb` packages wapas `com.termux` paths laayenge. Isliye AntiGem install ke baad 3 cheezein setup karta hai:
1. **`usr/etc/apt/apt.conf.d/99antigem-relocate`**:
   ```conf
   DPkg::Pre-Install-Pkgs { "/data/data/ai.antigem/files/usr/libexec/antigem/relocate-debs --apt"; };
   ```
   APT har `.deb` ko install karne se pehle `relocate-debs` ko feed karta hai.
2. **`usr/bin/dpkg` Wrapper (`dpkg-divert` ke saath)**:
   Real `dpkg` ko `usr/bin/dpkg.distrib` par move (`dpkg-divert`) kiya jata hai. Wrapper agar user manually `dpkg -i file.deb` ya `apt install ./file.deb` chalaye to uski ek copy ko rewrite karke real `dpkg.distrib` ko deta hai.
3. **Fast Native Relocator (`agy_ide_bridge relocate-debs`)**:
   Go binary `agy_ide_bridge` ke andar built-in fast subcommand (`antigem-relocate-debs/1`) hai jo `.deb` (`ar` + `tar.xz`/`tar.gz`/`tar.zst`) ko ek pass me parallel rewrite kar deta hai (agar bridge binary na ho to pure shell/`sed` fallback chalta hai).

### Layer 4: Custom `am.apk` (`:termux-am`) + Built-in `Termux:API`
- Termux me `termux-open`, `termux-setup-storage`, `termux-wake-lock`, `termux-reload-settings` sab `usr/bin/am` ke through `app_process` se **`usr/libexec/termux-am/am.apk`** chalate hain.
- Upstream `am.apk` intents ko `com.termux` package par bhejta hai — jise Android reject kar deta hai!
- AntiGem me ek Gradle module **`termux-am-build/build.gradle.kts`** (`targetSdk = 27`, `termux/TermuxAm` submodule se) hai jo `BuildConfig.TERMUX_PACKAGE_NAME = "ai.antigem"` ke saath custom `am.apk` compile karke `assets/bin/am.apk` me bundle karta hai.
- Install/startup par `LocalEnvironmentManager` is `assets/bin/am.apk` ko `usr/libexec/termux-am/am.apk` par copy karke **`chmod 0400`/`0444` (read-only)** karta hai (Android 14+ par writable DEX/APK load karne par `SecurityException` aata hai).
- Saath hi **`usr/libexec/termux-api`** dispatcher (`dpkg-divert` ke saath) aur **`TermuxApiReceiver`** app ke andar hi `Toast`, `Vibrate`, `Clipboard`, `Torch`, `TextToSpeech`, `Notification`, `Volume`, `BatteryStatus` handle kar lete hain.

### Layer 5: Terminal UI, PTY & Minimal Glibc
- **Terminal PTY & Renderer**: `implementation("com.github.termux.termux-app:terminal-emulator:v0.118.1")` (`libtermux.so` JNI PTY) + `com.termux.view.TerminalView` source files.
- **Minimal Glibc (`assets/glibc-min-arm64.tar.xz` -> 1.24 MB -> unpacks to `usr/glibc/` 4.05 MB)**:
  Isme `ld-linux-aarch64.so.1`, `libc.so.6`, `libm.so.6`, `libpthread.so.0`, `libresolv.so.2`, `libnss_dns.so.2`, `libnss_files.so.2`, `libdl.so.2`, `librt.so.1` aur `glibc/etc/nsswitch.conf` + `resolv.conf` + `hosts` hote hain. Isse glibc-compiled binaries (`claude`, `agy`, standard Linux tools) direct phone par chal sakte hain (`unset LD_PRELOAD` ke saath, taaki Bionic `libtermux-exec` crash na kare).

---

## 4. `bootrapz_2026-11-19.zip` Ka Complete Deep Audit

Yeh archive `https://github.com/Santoshkurmi/antigem/releases/download/rootfs-v2/bootrapz_2026-11-19.zip` par hosted hai aur AntiGem ke **`scripts/backup_bootstrap.sh`** script se ek live device se pack kiya gaya hai.

### A. Size & Entry Breakdown
| Metric | Value |
|---|---|
| **ZIP File Size (Download)** | **400,054,861 bytes (~381.52 MiB / 400 MB)** |
| **Uncompressed Size on Disk** | **1,175.04 MiB (~1.15 GiB)** |
| **Total Entries** | **26,152** (`22,478` regular files, `1,847` dirs, `1,827` symlinks) |
| **`usr/` Breakdown** | 25,161 entries — **969.30 MiB** uncomp (311.01 MiB comp) |
| **`home/` Breakdown** | 991 entries — **205.74 MiB** uncomp (65.08 MiB comp) |

### B. Folder-by-Folder Size (`usr/` & `home/`)
- `usr/lib`: **447.64 MiB** (`libLLVM.so` 127.41 MB, `libclang-cpp.so` 60.06 MB, `libclang.so` 33.66 MB, `libicudata.so.79.1` 32.01 MB, `libtree-sitter-sql.so` 12.75 MB, sanitizer static `.a` libs)
- `usr/bin`: **305.76 MiB** (746 binaries/scripts: `llvm-exegesis` 53.10 MB, `pnpm` 52.23 MB, `node` 48.65 MB, `gh` 40.34 MB, `clangd` 17.80 MB, `agy_ide_bridge` 13.75 MB, `clang-tidy` 12.67 MB, `tree-sitter` 8.29 MB, `python 3.14`, `nvim`, `zsh`, `bash`, `git`, `ssh`)
- `usr/include`: **103.90 MiB** (Clang/LLVM/NDK C++ headers)
- `usr/share`: **74.39 MiB** (Neovim runtime, terminfo, locales, docs, GIR files)
- `usr/libexec`: **26.85 MiB** (`git-core`, `termux-exec` test binaries, `termux-am/am.apk`, `antigem/relocate-debs`, `termux-api`)
- `usr/var`: **5.69 MiB** (`dpkg/status` & `apt` metadata — **158 installed packages**)
- `usr/glibc`: **4.05 MiB** (Pre-extracted minimal glibc)
- `usr/etc`: **1.02 MiB** (`profile`, `apt/sources.list`, `apt.conf.d/99antigem-relocate`, TLS certs)
- `home/`: **205.74 MiB**
  - `home/.local/bin/agy` = **190.93 MiB** (Antigravity CLI binary, Bionic `linker64`)
  - `home/.gemini/antigravity-cli/bin/webm_encoder` = **12.06 MiB**
  - `home/.zsh/` (838 files — zsh plugins/completions)
  - `home/.bashrc`, `home/.zshrc`, `home/.profile`, `home/.termux/`

### C. Important Internal Verification Facts (Jo Code Check Se Nikle)
1. **Har Binary & Script Already `ai.antigem` Par Patched Hai!**
   - `usr/bin/bash`: **13× `ai.antigem`**, **0× `com.termux`**
   - `usr/bin/node`: **22× `ai.antigem`**, **0× `com.termux`**
   - `usr/lib/libtermux-exec-ld-preload.so`: **7× `ai.antigem`**, **0× `com.termux`**
   - `usr/bin/am`, `usr/bin/pkg`, `usr/bin/termux-setup-storage`, `usr/etc/profile`: Sab `#!/data/data/ai.antigem/files/usr/bin/...` use karte hain.
   - `home/.bashrc` & `home/.profile`: `/data/user/0/ai.antigem/files/...` hardcode karte hain aur `PS1` me `gemini` likha hai + `alias proj='cd .../home/projects'`.
   - `home/.termux/shell`: Default shell `/data/data/ai.antigem/files/usr/bin/zsh` set hai.
2. **APT Mirror:** `usr/etc/apt/sources.list` me `deb https://mirrors.aliyun.com/termux/termux-main stable main` set hai.
3. **`am.apk` Twist:** Zip ke andar `usr/libexec/termux-am/am.apk` abhi bhi `com.termux` wala hai (kyunki `.apk` relocate skip hota hai), kyunki AntiGem app launch par apne `assets/bin/am.apk` se overwrite kar deti hai.
4. **AntiGem-Specific Leftovers / Cruft / Bloat Jo Zip Me Pade Hain:**
   - `home/.antigem/`, `home/.antigem_bridge/csrf_token.txt` (`m64dezpmb8z1`), `home/.antigem_bridge/memory/state.json`
   - `home/.gemini/antigravity-cli/log/*.log`, `crashes/`, `daemon/hub_1237.json` (AntiGem ka port `1237` — JARVIS ka `2235` hai)
   - `usr/bin/agy_ide_bridge` (13.75 MB — JARVIS pure-Kotlin HTTP server use karta hai, is Go binary ki zaroorat nahi jabki relocate-debs ko chor kar)
   - `usr/print-args-binary*`, `usr/print-args-*.sh*` (debug test files)
   - `usr/libexec/installed-tests/termux-exec/...` (~2 MB test binaries)
   - Agar C/C++ compiler (`clang`/`llvm`) zip ke size ko chhota rakhna hai to sirf `llvm-exegesis` (53 MB) + `.a` sanitizer libs hata kar hi ~75 MB uncompressed (~25 MB download) bachaya ja sakta hai, aur `agy` hata kar ~60 MB download kam ho jata hai.

---

## 5. JARVIS AI vs AntiGem: Head-to-Head Comparison

| Feature / Layer | AntiGem (`ai.antigem`) | Aapka JARVIS AI (`com.jarvisai.assistant`) | Kya Gap / Problem Hai? |
|---|---|---|---|
| **`applicationId` Length** | **`ai.antigem` (10 bytes)** & debug `ai.antidev` (10 bytes) | **`com.jarvisai.assistant` (22 bytes)** | 22 bytes != 10 bytes (`com.termux`), isliye JARVIS me **in-place ELF binary patching impossible** hai. |
| **`targetSdk`** | `28` | `28` (Already good!) | Dono me Android 10+ W^X bypass active hai. |
| **Linux Execution Engine** | **Native Direct + Byte Relocation** (Zero PRoot overhead!) | **PRoot (`PRootRuntime.kt`)** bind-mounting `/data/data/com.termux` -> `filesDir`, + Direct Shell fallback | PRoot `com.termux` paths ke liye bind karta hai, `ai.antigem` paths ke liye **nahi**! |
| **Default Bootstrap Zip** | Custom `bootrapz_2026-11-19.zip` (382 MB, `usr/` + `home/`, 158 pkgs, `ai.antigem` paths) | Official Termux `bootstrap-aarch64.zip` (2024.10.04, bare minimal, ~30 MB, `com.termux` paths) | Official zip me Node, Python, Git, Clang, Zsh kuch nahi aata; `bootrapz_2026-11-19.zip` direct daloge to `ai.antigem` paths fail honge! |
| **Dual App Icon (2nd Launcher)** | `TerminalActivity` (`taskAffinity="${applicationId}.terminal"`) + `.AntiTermAlias` & `.ClassicTermAlias` + `TerminalLauncherManager` + Onboarding | `TermuxActivity` exists, **PAR** `taskAffinity="${applicationId}.main"` (same as MainActivity!) aur **KOI `<activity-alias>` / toggle / `ic_termux_launcher` icon nahi hai** | App drawer me 2nd Termux/JarvisTerm icon nahi dikhta aur Recents me alag app jaisa behave nahi karta. |
| **Custom `am.apk` (`:termux-am`)** | Built from source with `ai.antigem`, copied to `usr/libexec/termux-am/am.apk` (`chmod 0400`) | **Missing!** (`am.apk` abhi bhi `com.termux` par intents bhejta hai) | `termux-open`, `termux-wake-lock`, `termux-setup-storage` Android package mismatch se fail honge. |
| **`Termux:API` Dispatcher** | `TermuxApiReceiver` + `usr/libexec/termux-api` (`dpkg-divert`) | Manifest me `TermuxApiReceiver` hai, par `usr/libexec/termux-api` + `am.apk` wiring incomplete hai | `termux-toast`, `termux-clipboard-set` etc. smoothly tabhi chalenge jab custom `am.apk` + wrapper hoga. |
| **Minimal `glibc` (`usr/glibc`)** | `assets/glibc-min-arm64.tar.xz` (1.24 MB) auto-extracted with `nsswitch.conf` & DNS | **Not bundled** in `app/src/main/assets` | Standard glibc Linux binaries (`claude` official binary, etc.) bina Ubuntu PRoot ke direct nahi chalenge. |
| **Terminal UI** | `termux-terminal-emulator:v0.118.1` + `TerminalView` | Same `termux-terminal-emulator:v0.118.1` + `LocalTerminalDialog` | PTY layer already solid hai; bas `TerminalView` & `TermuxActivity` UX polish/alias chahiye. |

---

## 6. JARVIS AI Ko AntiGem Jaisa Kaise Banayein? (Do Exact Options)

Aapke paas **2 raaste** hain. Dhyan se decide karein ki aapko kaunsa chahiye:

### Option A (RECOMMENDED): `com.jarvisai.assistant` Rakho + Upgraded PRoot + Custom `com.termux` Zip + Dual Launcher
*Agar aap existing app update karna chahte hain aur `google-services.json` / Firebase Auth / Play signing / existing users nahi todna chahte, to yeh **best & safest** raasta hai.*

Isme kya-kya karna hoga:
1. **Dual App Icon (`1 APK = 2 Apps` Feature)**:
   - `AndroidManifest.xml` me `TermuxActivity` ka `taskAffinity` badal kar **`${applicationId}.terminal`** karein.
   - `res/mipmap-anydpi-v26/ic_termux_launcher.xml` + foreground/background icons add karein.
   - Do `<activity-alias>` add karein (`JarvisTermAlias` aur `ClassicTermAlias`, default `enabled="false"`), dono `targetActivity="com.jarvisai.assistant.terminal.TermuxActivity"`.
   - `TerminalLauncherManager.kt` + Settings toggle + Onboarding dialog banayein jo `PackageManager.setComponentEnabledSetting(..., DONT_KILL_APP)` se 2nd icon ON/OFF kare.
2. **Custom `am.apk` (`:termux-am` subproject)**:
   - `termux/TermuxAm` submodule / module add karein with `buildConfigField("String", "TERMUX_PACKAGE_NAME", "\"com.jarvisai.assistant\"")` (`targetSdk = 27`).
   - Build output ko `assets/bin/am.apk` me pack karein aur `LocalEnvironmentManager` me `usr/libexec/termux-am/am.apk` par copy karke `chmod 0444` set karein.
   - `usr/libexec/termux-api` dispatcher script bhi auto-write karein.
3. **Bundled `glibc-min-arm64.tar.xz`**:
   - `assets/glibc-min-arm64.tar.xz` (~1.2 MB) add karein aur `LocalEnvironmentManager.fixFilePermissions()` / `configureEnvironmentFiles()` me extract + `nsswitch.conf`/`resolv.conf` link karein.
4. **Zip Compatibility Fix (Most Important!)**:
   - **Recommended:** `bootrapz_2026-11-19.zip` ko pehle `ai.antigem` -> `com.termux` reverse-patch aur clean karke apna **JARVIS Custom Zip** banayein (Section 7 dekhein!). Kyunki `"ai.antigem"` aur `"com.termux"` **dono exact 10 bytes** hain, yeh 100% cleanly reverse ho jata hai aur PRoot `/data/data/com.termux` ke saath makkhan chalta hai + future `pkg install` bhi bina kisi hook ke kaam karta hai!
   - *(Extra Safety Backup in `PRootRuntime.kt`)*: `buildTermuxProotArgv` me `/data/data/com.termux` ke saath `/data/data/ai.antigem` -> `sandboxAppRoot` bind-mount bhi fallback me add kar sakte hain taaki galti se koi unpatched `ai.antigem` zip dale to bhi crash na ho (par clean `com.termux` zip hi primary hona chahiye).

---

### Option B (TRUE 100% PROOT-LESS ANTIGEM ARCHITECTURE): 10-Char `applicationId`
*Agar aapko bilkul AntiGem jaisa **zero-PRoot native speed** chahiye (PRoot ka ~10-20% ptrace syscall overhead bhi khatam karna hai):*

1. **Breaking Requirement:** `app/build.gradle.kts` me `applicationId` ko exact **10 ASCII characters** ka banana padega (jaise `"com.termux"` = 10 chars, `"ai.antigem"` = 10 chars -> e.g. **`"ai.jarvis0"`** ya **`"in.jarvisx"`**).
   - **Warning:** `applicationId` badalne se Android ise ek **bilkul naya app** maanega (purane `com.jarvisai.assistant` ke upar update nahi hoga, aur Firebase `google-services.json` + OAuth SHA-1 re-register karna padega).
2. **`TermuxRelocation.kt` port karein**: `TERMUX_PACKAGE = "com.termux"` -> `targetPackage` (`ai.jarvisx`, 10 chars) byte-for-byte ELF/script/symlink rewrite engine.
3. **APT/DPKG Hooks**: `usr/etc/apt/apt.conf.d/99jarvis-relocate` + `usr/bin/dpkg` wrapper (`dpkg-divert`) + `usr/libexec/jarvis/relocate-debs` add karein taaki naye `pkg install` packages auto-patch hon.
4. **Custom `:termux-am` (`am.apk`) + `glibc-min-arm64.tar.xz` + `<activity-alias>`** same Option A ki tarah add karein.

---

## 7. `bootrapz_2026-11-19.zip` Ko Apne App Ke Hisab Se Modify Kaise Karein? (Step-by-Step Guide)

Haan, aap **`bootrapz_2026-11-19.zip` ko 100% modify karke apna custom ZIP bana sakte hain**! Niche exact safe recipe hai (chahe aap Linux/WSL PC par karein ya Termux ke andar):

### Step 1: Zip Extract Karein Symlinks & Permissions Preserve Karke
Kabhi bhi Windows Explorer ya normal GUI se extract/repack mat karna (symlinks toot jayenge!). Linux/macOS/WSL/Termux me:
```bash
mkdir -p jarvis_rootfs && cd jarvis_rootfs
unzip -q ../bootrapz_2026-11-19.zip
```

### Step 2: `ai.antigem` -> `com.termux` Reverse-Relocate Karein (Option A / PRoot ke liye)
*Note: Agar aap Option B (10-char ID jaise `ai.jarvis0`) le rahe hain to `b"com.termux"` ki jagah `b"ai.jarvis0"` daalein!*  
Kyunki `"ai.antigem"` (10 bytes) == `"com.termux"` (10 bytes), yeh Python script har file, ELF binary aur symlink ko safe reverse-patch karega:
```python
import os

OLD = b"ai.antigem"
OLD_ALT = b"ai.antidev"
NEW = b"com.termux" # Option A (PRoot) ke liye b"com.termux"; Option B me exact 10-char ID
SKIP_EXT = {".apk", ".jar", ".zip", ".aar", ".whl", ".dex", ".jmod"}

for root, dirs, files in os.walk(".", topdown=False):
    for name in files + dirs:
        p = os.path.join(root, name)
        if os.path.islink(p):
            target = os.readlink(p)
            if "ai.antigem" in target or "ai.antidev" in target:
                new_t = target.replace("ai.antigem", NEW.decode()).replace("ai.antidev", NEW.decode())
                os.unlink(p)
                os.symlink(new_t, p)
            continue
        if os.path.isfile(p):
            if os.path.splitext(name)[1].lower() in SKIP_EXT:
                continue
            with open(p, "rb") as f:
                data = f.read()
            if OLD in data or OLD_ALT in data:
                mode = os.stat(p).st_mode
                patched = data.replace(OLD, NEW).replace(OLD_ALT, NEW)
                with open(p, "wb") as f:
                    f.write(patched)
                os.chmod(p, mode)
```

### Step 3: AntiGem Hooks, Branding & Bloat Clean Karein
```bash
# 1. AntiGem ke custom apt/dpkg hooks & debug files hatayein
rm -rf usr/etc/apt/apt.conf.d/99antigem-relocate
rm -rf usr/libexec/antigem
rm -f usr/print-args-*
rm -rf usr/libexec/installed-tests/termux-exec

# Agar dpkg.distrib hai to original dpkg restore karein (Option A ke liye)
if [ -f usr/bin/dpkg.distrib ]; then
    mv -f usr/bin/dpkg.distrib usr/bin/dpkg
fi

# 2. AntiGem runtime/bridge state & old logs clean karein
rm -rf home/.antigem home/.antigem_bridge
rm -rf home/.gemini/antigravity-cli/log/* home/.gemini/antigravity-cli/crashes/* home/.gemini/antigravity-cli/daemon/*

# 3. (Optional Size Optimization: ~130 MB uncomp / ~40 MB ZIP bachane ke liye)
rm -f usr/bin/agy_ide_bridge        # JARVIS apna native Kotlin bridge use karta hai
rm -f usr/bin/llvm-exegesis         # 53 MB benchmark tool (coding me zaroorat nahi)
rm -f usr/lib/clang/*/lib/linux/libclang_rt.*an*.a  # Static sanitizer archives (~18 MB)
```

### Step 4: JARVIS AI Customization (`bashrc`, `zshrc`, Workspace, Mirror)
1. **`home/.bashrc` aur `home/.profile`** update karein:
   - `PS1` prompt `gemini` se **`jarvis`** karein.
   - `alias proj='cd $HOME/jarvis-workspace/projects'` set karein (`JarvisHubConfig.ISOLATED_WORKSPACE_DIR_NAME`).
   - `export JARVIS_MCP_URL="http://127.0.0.1:2235"` / `2236` env add karein.
2. **`usr/etc/apt/sources.list`**:
   Agar India/Global fast CDN chahiye to `deb https://packages.termux.dev/apt/termux-main stable main` kar dein (abhi `mirrors.aliyun.com` hai).
3. **`home/.termux/shell`**:
   Agar default bash chahiye to `usr/bin/bash`, zsh chahiye to `usr/bin/zsh` rakhein (path `/data/data/com.termux/files/usr/bin/...` ya aapke 10-char ID ke hisab se).
4. **Symlinks & Permissions Check**:
   Saare `usr/bin/*` aur `home/.local/bin/*` par `chmod +x` verify kar lein.

### Step 5: Symlinks Preserve Karke Re-Zip & Verify Karein
**CRITICAL:** `zip` me **`-y` flag** lagana mandatory hai, warna saare Linux symlinks broken copy files ban jayenge!
```bash
zip -r -y -6 ../jarvis_bootrapz_v1.zip usr home
cd ..
unzip -t jarvis_bootrapz_v1.zip | tail -n 5
```
Ab is `jarvis_bootrapz_v1.zip` ko apne GitHub Releases (e.g. `Santoshkurmi/jarvis` ya aapke repo release) par upload karein aur **`LocalEnvironmentManager.kt`** me `HARDCODED_ROOTFS_URL` aur `findLocalBootstrapArchive()` ke filename ko update kar dein.

---

## 8. Final Action Checklist (JARVIS AI Upgrade Roadmap)

1. [ ] **Decide Architecture**: Option A (`com.jarvisai.assistant` + PRoot + reverse-patched custom ZIP — Zero breaking change) vs Option B (10-char `applicationId` + `TermuxRelocation` — Zero PRoot).
2. [ ] **Dual App Icon**: `AndroidManifest.xml` me `TermuxActivity` ko alag `taskAffinity="${applicationId}.terminal"` dein + `.JarvisTermAlias` / `.ClassicTermAlias` (`<activity-alias>`) + `TerminalLauncherManager.kt` + `ic_termux_launcher` icon add karein.
3. [ ] **Build Custom `:termux-am` (`am.apk`)**: `TERMUX_PACKAGE_NAME` ko JARVIS ke `applicationId` par compile karke `assets/bin/am.apk` me daalein aur `chmod 0444` se extract karein.
4. [ ] **Bundle Minimal Glibc**: `assets/glibc-min-arm64.tar.xz` (~1.2 MB) add karein taaki glibc binaries natively chalein.
5. [ ] **Customize & Host `bootrapz` ZIP**: `bootrapz_2026-11-19.zip` me se `ai.antigem` -> `com.termux` (Option A) ya 10-char ID (Option B) patch karein, `.antigem_bridge` / old logs / `llvm-exegesis` clean karein, `.bashrc` me `jarvis-workspace` daalein, `zip -r -y` se pack karke GitHub Release par daalein, aur `LocalEnvironmentManager.kt` ka `HARDCODED_ROOTFS_URL` point karein.
