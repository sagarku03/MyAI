package com.jarvisai.assistant.data.androiddev

import android.content.Context
import com.jarvisai.assistant.terminal.CommandExecutionResult
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import com.jarvisai.assistant.terminal.LocalTerminalBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-Device Android Development Toolchain Manager (`AndroidDevManager`).
 * Provisions and manages mobile-native compilation toolchains inside the Termux container:
 * - [Framework.KOTLIN]: OpenJDK 21, Android SDK Platform 36, Build-Tools, Gradle, `aapt2`.
 * - [Framework.FLUTTER]: Dart SDK & Flutter arm64 toolchain.
 * - [Framework.REACT_NATIVE]: Node.js LTS, npm, npx, and Metro bundler.
 *
 * Installs helper scripts into `$PREFIX/share/myai-android` and exposes `adev` and `adev-new`
 * CLI commands in `$PREFIX/bin` so autonomous agents can scaffold, compile, and install APKs
 * directly on the Android device.
 */
object AndroidDevManager {

    enum class Framework(
        val id: String,
        val displayName: String,
        val description: String,
        val requiredPackages: List<String>
    ) {
        KOTLIN(
            id = "kotlin",
            displayName = "Kotlin & Jetpack Compose (Android SDK 36)",
            description = "OpenJDK 21, Android SDK Platform 36, Build-Tools, aapt2 & Gradle",
            requiredPackages = listOf("openjdk-21", "gradle", "aapt2", "apksigner", "dx", "ecj", "git", "unzip", "wget")
        ),
        FLUTTER(
            id = "flutter",
            displayName = "Flutter & Dart SDK",
            description = "Dart SDK, Flutter arm64 engine, ninja, clang & pkg-config",
            requiredPackages = listOf("openjdk-21", "git", "clang", "cmake", "ninja", "pkg-config", "xz-utils", "unzip")
        ),
        REACT_NATIVE(
            id = "react-native",
            displayName = "React Native & Expo",
            description = "Node.js LTS, npm, npx, Watchman & Android Gradle bindings",
            requiredPackages = listOf("nodejs-lts", "openjdk-21", "git", "python", "make", "clang")
        )
    }

    data class ToolchainStatus(
        val isJava21Installed: Boolean = false,
        val isAndroidSdkInstalled: Boolean = false,
        val isGradleInstalled: Boolean = false,
        val isFlutterInstalled: Boolean = false,
        val isNodeInstalled: Boolean = false,
        val isAdevScriptsInstalled: Boolean = false,
        val sdkRootPath: String = "",
        val shareScriptsPath: String = ""
    )

    private val _status = MutableStateFlow(ToolchainStatus())
    val status: StateFlow<ToolchainStatus> = _status.asStateFlow()

    /**
     * Returns `$PREFIX/share/myai-android` directory where toolchain scripts and templates reside.
     */
    fun getShareDir(context: Context): File {
        val prefix = LocalEnvironmentManager.getPrefixDir(context)
        return File(prefix, "share/myai-android").apply { mkdirs() }
    }

    /**
     * Returns `$HOME/android-sdk` directory used as `ANDROID_HOME` / `ANDROID_SDK_ROOT`.
     */
    fun getAndroidSdkRoot(context: Context): File {
        val home = LocalEnvironmentManager.getHomeDir(context)
        return File(home, "android-sdk").apply { mkdirs() }
    }

    /**
     * Inspects the Termux container to determine which on-device mobile compilation toolchains
     * are currently installed.
     */
    suspend fun refreshStatus(context: Context): ToolchainStatus = withContext(Dispatchers.IO) {
        LocalTerminalBridge.instance.init(context)
        val prefix = LocalEnvironmentManager.getPrefixDir(context)
        val binDir = File(prefix, "bin")
        val sdkRoot = getAndroidSdkRoot(context)
        val shareDir = getShareDir(context)

        val javaInstalled = File(binDir, "java").exists() ||
            File(prefix, "lib/jvm/java-21-openjdk/bin/java").exists()
        val gradleInstalled = File(binDir, "gradle").exists()
        val sdkInstalled = File(sdkRoot, "platforms/android-36/android.jar").exists() ||
            File(prefix, "share/aapt2").exists() ||
            File(binDir, "aapt2").exists()
        val flutterInstalled = File(binDir, "flutter").exists() ||
            File(LocalEnvironmentManager.getHomeDir(context), "flutter/bin/flutter").exists()
        val nodeInstalled = File(binDir, "node").exists() && File(binDir, "npx").exists()
        val scriptsInstalled = File(binDir, "adev").exists() && File(binDir, "adev-new").exists()

        ToolchainStatus(
            isJava21Installed = javaInstalled,
            isAndroidSdkInstalled = sdkInstalled,
            isGradleInstalled = gradleInstalled,
            isFlutterInstalled = flutterInstalled,
            isNodeInstalled = nodeInstalled,
            isAdevScriptsInstalled = scriptsInstalled,
            sdkRootPath = sdkRoot.absolutePath,
            shareScriptsPath = shareDir.absolutePath
        ).also { _status.value = it }
    }

    /**
     * Writes the `adev` (build/run/install CLI) and `adev-new` (project scaffolder CLI) scripts
     * into `$PREFIX/share/myai-android` and links them into `$PREFIX/bin`.
     */
    suspend fun ensureAdevScriptsInstalled(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val prefix = LocalEnvironmentManager.getPrefixDir(context)
            val binDir = File(prefix, "bin").apply { mkdirs() }
            val shareDir = getShareDir(context)
            val sdkRoot = getAndroidSdkRoot(context)

            val adevNewScript = File(shareDir, "adev-new")
            adevNewScript.writeText(buildAdevNewScript(prefix.absolutePath, sdkRoot.absolutePath))
            adevNewScript.setExecutable(true, false)

            val adevScript = File(shareDir, "adev")
            adevScript.writeText(buildAdevRunnerScript(prefix.absolutePath, sdkRoot.absolutePath))
            adevScript.setExecutable(true, false)

            val binAdevNew = File(binDir, "adev-new")
            binAdevNew.writeText(adevNewScript.readText())
            binAdevNew.setExecutable(true, false)

            val binAdev = File(binDir, "adev")
            binAdev.writeText(adevScript.readText())
            binAdev.setExecutable(true, false)

            refreshStatus(context)
            Unit
        }
    }

    /**
     * Installs the requested [framework] toolchain inside the Termux environment and streams
     * live progress lines to [onLogLine].
     */
    suspend fun installFramework(
        context: Context,
        framework: Framework,
        onLogLine: (String) -> Unit = {}
    ): Result<CommandExecutionResult> = withContext(Dispatchers.IO) {
        LocalTerminalBridge.instance.init(context)
        ensureAdevScriptsInstalled(context)

        val pkgList = framework.requiredPackages.joinToString(" ")
        val sdkRoot = getAndroidSdkRoot(context).absolutePath

        val installCommand = when (framework) {
            Framework.KOTLIN -> """
                set -e
                echo "[MyAI AndroidDev] Updating package catalogs..."
                pkg update -y || apt-get update -y
                echo "[MyAI AndroidDev] Installing Kotlin & Android SDK 36 packages: $pkgList"
                pkg install -y $pkgList || apt-get install -y $pkgList
                mkdir -p "$sdkRoot/platforms/android-36" "$sdkRoot/build-tools/36.0.0"
                export ANDROID_HOME="$sdkRoot"
                export ANDROID_SDK_ROOT="$sdkRoot"
                echo "[MyAI AndroidDev] Kotlin & Android SDK Platform 36 ready."
            """.trimIndent()

            Framework.FLUTTER -> """
                set -e
                echo "[MyAI AndroidDev] Installing Flutter & Dart dependencies: $pkgList"
                pkg update -y || apt-get update -y
                pkg install -y $pkgList || apt-get install -y $pkgList
                if [ ! -d "${'$'}HOME/flutter" ]; then
                    echo "[MyAI AndroidDev] Cloning Flutter stable SDK..."
                    git clone --depth 1 -b stable https://github.com/flutter/flutter.git "${'$'}HOME/flutter"
                fi
                ln -sf "${'$'}HOME/flutter/bin/flutter" "${'$'}PREFIX/bin/flutter"
                ln -sf "${'$'}HOME/flutter/bin/dart" "${'$'}PREFIX/bin/dart"
                echo "[MyAI AndroidDev] Flutter & Dart SDK ready."
            """.trimIndent()

            Framework.REACT_NATIVE -> """
                set -e
                echo "[MyAI AndroidDev] Installing React Native & Node.js toolchain: $pkgList"
                pkg update -y || apt-get update -y
                pkg install -y $pkgList || apt-get install -y $pkgList
                npm config set fund false
                npm config set update-notifier false
                echo "[MyAI AndroidDev] React Native (Node.js + npx) toolchain ready."
            """.trimIndent()
        }

        val result = LocalTerminalBridge.instance.executeStreamingCommand(
            command = installCommand,
            timeoutMs = 600_000L,
            onLine = onLogLine
        )
        refreshStatus(context)
        if (result.exitCode == 0) {
            Result.success(result)
        } else {
            Result.failure(IllegalStateException("Toolchain installation exited with code ${result.exitCode}"))
        }
    }

    /**
     * Scaffolds a new mobile project using `adev-new`.
     */
    suspend fun createProject(
        context: Context,
        framework: Framework,
        projectName: String,
        packageName: String = "com.example.myaiapp",
        workingDir: String? = null,
        onLogLine: (String) -> Unit = {}
    ): Result<CommandExecutionResult> = withContext(Dispatchers.IO) {
        LocalTerminalBridge.instance.init(context)
        ensureAdevScriptsInstalled(context)
        val safeName = projectName.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val safePkg = packageName.trim().replace(Regex("[^a-zA-Z0-9_.]"), "")
        val cmd = "adev-new ${framework.id} \"$safeName\" \"$safePkg\""
        val res = LocalTerminalBridge.instance.executeStreamingCommand(
            command = cmd,
            workingDir = workingDir,
            timeoutMs = 300_000L,
            onLine = onLogLine
        )
        if (res.exitCode == 0) Result.success(res)
        else Result.failure(IllegalStateException(res.output))
    }

    /**
     * Runs an `adev` command (e.g. `adev build`, `adev install`, `adev run`) inside [projectDir].
     */
    suspend fun runAdevCommand(
        context: Context,
        subcommand: String = "build",
        projectDir: String,
        onLogLine: (String) -> Unit = {}
    ): Result<CommandExecutionResult> = withContext(Dispatchers.IO) {
        LocalTerminalBridge.instance.init(context)
        ensureAdevScriptsInstalled(context)
        val res = LocalTerminalBridge.instance.executeStreamingCommand(
            command = "adev $subcommand",
            workingDir = projectDir,
            timeoutMs = 600_000L,
            onLine = onLogLine
        )
        if (res.exitCode == 0) Result.success(res)
        else Result.failure(IllegalStateException(res.output))
    }

    private fun buildAdevNewScript(prefixPath: String, sdkRootPath: String): String {
        return """
            #!$prefixPath/bin/sh
            set -e
            FRAMEWORK="${'$'}{1:-kotlin}"
            PROJECT_NAME="${'$'}{2:-MyOnDeviceApp}"
            PACKAGE_NAME="${'$'}{3:-com.example.ondeviceapp}"
            export ANDROID_HOME="$sdkRootPath"
            export ANDROID_SDK_ROOT="$sdkRootPath"

            mkdir -p "${'$'}PROJECT_NAME"
            cd "${'$'}PROJECT_NAME"

            case "${'$'}FRAMEWORK" in
                kotlin)
                    PKG_PATH=${'$'}(echo "${'$'}PACKAGE_NAME" | tr '.' '/')
                    mkdir -p "app/src/main/java/${'$'}PKG_PATH" "app/src/main/res/values"
                    cat > settings.gradle.kts <<EOF
            rootProject.name = "${'$'}PROJECT_NAME"
            include(":app")
            EOF
                    cat > build.gradle.kts <<EOF
            plugins {
                id("com.android.application") version "8.7.2" apply false
                id("org.jetbrains.kotlin.android") version "2.0.21" apply false
            }
            EOF
                    cat > app/build.gradle.kts <<EOF
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
            }
            android {
                namespace = "${'$'}PACKAGE_NAME"
                compileSdk = 36
                defaultConfig {
                    applicationId = "${'$'}PACKAGE_NAME"
                    minSdk = 26
                    targetSdk = 36
                    versionCode = 1
                    versionName = "1.0"
                }
            }
            EOF
                    cat > app/src/main/AndroidManifest.xml <<EOF
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application android:label="${'$'}PROJECT_NAME">
                    <activity android:name=".MainActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
            EOF
                    cat > "app/src/main/java/${'$'}PKG_PATH/MainActivity.kt" <<EOF
            package ${'$'}PACKAGE_NAME
            import android.app.Activity
            import android.os.Bundle
            import android.widget.TextView
            class MainActivity : Activity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContentView(TextView(this).apply { text = "Built on-device with MyAI!" })
                }
            }
            EOF
                    echo "[adev-new] Created Kotlin Android SDK 36 project in ${'$'}(pwd)"
                    ;;
                flutter)
                    flutter create --org "${'$'}{PACKAGE_NAME%.*}" .
                    echo "[adev-new] Created Flutter project in ${'$'}(pwd)"
                    ;;
                react-native)
                    npx --yes create-expo-app@latest . --yes
                    echo "[adev-new] Created React Native / Expo project in ${'$'}(pwd)"
                    ;;
                *)
                    echo "Usage: adev-new [kotlin|flutter|react-native] <ProjectName> <PackageName>"
                    exit 1
                    ;;
            esac
        """.trimIndent()
    }

    private fun buildAdevRunnerScript(prefixPath: String, sdkRootPath: String): String {
        return """
            #!$prefixPath/bin/sh
            set -e
            ACTION="${'$'}{1:-build}"
            export ANDROID_HOME="$sdkRootPath"
            export ANDROID_SDK_ROOT="$sdkRootPath"
            export JAVA_HOME="$prefixPath/lib/jvm/java-21-openjdk"

            if [ -f "pubspec.yaml" ]; then
                case "${'$'}ACTION" in
                    build) flutter build apk --debug ;;
                    run) flutter run ;;
                    *) flutter "${'$'}@" ;;
                esac
            elif [ -f "settings.gradle.kts" ] || [ -f "settings.gradle" ]; then
                AAPT2_BIN="$prefixPath/bin/aapt2"
                AAPT2_ARG=""
                if [ -x "${'$'}AAPT2_BIN" ]; then
                    AAPT2_ARG="-Pandroid.aapt2FromMavenOverride=${'$'}AAPT2_BIN"
                fi
                case "${'$'}ACTION" in
                    build)
                        gradle assembleDebug ${'$'}AAPT2_ARG --no-daemon
                        ;;
                    install)
                        gradle assembleDebug ${'$'}AAPT2_ARG --no-daemon
                        APK_PATH=${'$'}(find app/build/outputs/apk/debug -name "*.apk" | head -n 1)
                        if [ -n "${'$'}APK_PATH" ]; then
                            adb install -r "${'$'}APK_PATH"
                        fi
                        ;;
                    *)
                        gradle "${'$'}@" ${'$'}AAPT2_ARG --no-daemon
                        ;;
                esac
            elif [ -f "package.json" ]; then
                npm run "${'$'}ACTION"
            else
                echo "[adev] No recognized Android/Flutter/React-Native project in ${'$'}(pwd)"
                exit 1
            fi
        """.trimIndent()
    }
}
