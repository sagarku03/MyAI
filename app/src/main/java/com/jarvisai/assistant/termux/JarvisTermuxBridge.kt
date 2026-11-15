package com.jarvisai.assistant.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

class JarvisTermuxBridge(private val context: Context) {

    private val idCounter = AtomicInteger(1000)

    fun isTermuxInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(TERMUX_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun hasRunCommandPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            "com.termux.permission.RUN_COMMAND"
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun executeCommand(
        command: String,
        background: Boolean = true,
        timeoutSeconds: Long = 15
    ): TermuxCommandResult {
        val localLinuxReady = com.jarvisai.assistant.terminal.LocalEnvironmentManager.isEnvironmentInstalled(context)
        if (!isTermuxInstalled() || !hasRunCommandPermission()) {
            // Seamlessly run inside Built-in Jarvis Linux Core if available (0 Android permissions required)
            if (!isTermuxInstalled() || localLinuxReady) {
                return try {
                    com.jarvisai.assistant.terminal.LocalTerminalBridge.instance.init(context.applicationContext)
                    val localResult = com.jarvisai.assistant.terminal.LocalTerminalBridge.instance.executeCommand(
                        command = command,
                        timeoutMs = timeoutSeconds * 1000L
                    )
                    TermuxCommandResult(
                        success = localResult.exitCode == 0,
                        stdout = localResult.output,
                        stderr = if (localResult.exitCode != 0) localResult.output else "",
                        exitCode = localResult.exitCode,
                        errorMessage = if (localResult.exitCode != 0) "EXIT_${localResult.exitCode}" else null
                    )
                } catch (e: Exception) {
                    val reason = if (!isTermuxInstalled()) {
                        "Termux app is not installed and built-in shell failed: ${e.message}"
                    } else {
                        "Termux RUN_COMMAND permission not granted and built-in shell failed: ${e.message}"
                    }
                    TermuxCommandResult(
                        success = false,
                        stdout = "",
                        stderr = reason,
                        exitCode = -2,
                        errorMessage = if (!isTermuxInstalled()) "TERMUX_NOT_INSTALLED" else "PERMISSION_DENIED"
                    )
                }
            }
            return TermuxCommandResult(
                success = false,
                stdout = "",
                stderr = "com.termux.permission.RUN_COMMAND permission is not granted and built-in Jarvis Linux Core is not installed yet.",
                exitCode = -2,
                errorMessage = "PERMISSION_DENIED"
            )
        }

        val execId = idCounter.incrementAndGet()
        val deferred = CompletableDeferred<TermuxCommandResult>()
        JarvisTermuxResultReceiver.pendingRequests[execId] = deferred

        try {
            val resultIntent = Intent(context, JarvisTermuxResultReceiver::class.java).apply {
                putExtra("jarvis_exec_id", execId)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, execId, resultIntent, flags)

            val intent = Intent(TERMUX_RUN_COMMAND_ACTION).apply {
                setClassName(TERMUX_PACKAGE, TERMUX_SERVICE_CLASS)
                putExtra(EXTRA_RUN_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/bash")
                putExtra(EXTRA_RUN_COMMAND_ARGUMENTS, arrayOf("-c", command))
                putExtra(EXTRA_RUN_COMMAND_WORKDIR, "/data/data/com.termux/files/home")
                putExtra(EXTRA_RUN_COMMAND_BACKGROUND, background)
                putExtra(EXTRA_RUN_COMMAND_SESSION_ACTION, "0")
                putExtra(EXTRA_PENDING_INTENT, pendingIntent)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }

            val result = withTimeoutOrNull(timeoutSeconds * 1000) {
                deferred.await()
            }

            return result ?: TermuxCommandResult(
                success = false,
                stdout = "",
                stderr = "Command execution timed out after ${timeoutSeconds}s in Termux.",
                exitCode = -3,
                errorMessage = "TIMEOUT"
            )
        } catch (se: SecurityException) {
            JarvisTermuxResultReceiver.pendingRequests.remove(execId)
            Log.e(TAG, "SecurityException executing Termux command", se)
            return TermuxCommandResult(
                success = false,
                stdout = "",
                stderr = "Termux blocked external execution. Please run in Termux: ${TERMUX_ALLOW_EXTERNAL_CMD}",
                exitCode = -2,
                errorMessage = "TERMUX_EXTERNAL_APPS_DISABLED"
            )
        } catch (e: Exception) {
            JarvisTermuxResultReceiver.pendingRequests.remove(execId)
            Log.e(TAG, "Error executing Termux command", e)
            return TermuxCommandResult(
                success = false,
                stdout = "",
                stderr = e.localizedMessage ?: "Unknown execution error",
                exitCode = -4,
                errorMessage = e.message
            )
        }
    }

    private fun getPackageInfoCompat(packageName: String): android.content.pm.PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getInstallerPackageCompat(packageName: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(packageName).installingPackageName.orEmpty()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(packageName).orEmpty()
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun getDiagnostics(): TermuxBridgeDiagnostics {
        val termuxInfo = getPackageInfoCompat(TERMUX_PACKAGE)
        val installed = termuxInfo != null
        val permGranted = hasRunCommandPermission()
        val pkg = context.packageName
        val isPermissionDeclaredByTermux = runCatching {
            context.packageManager.getPermissionInfo(TERMUX_RUN_COMMAND_PERMISSION, 0)
            true
        }.getOrDefault(false)
        val termuxInstaller = if (installed) getInstallerPackageCompat(TERMUX_PACKAGE) else ""
        val isPlayStoreTermux = termuxInstaller == "com.android.vending" || termuxInstaller == "com.google.android.feedback"
        val jarvisInfo = getPackageInfoCompat(pkg)
        val wasJarvisInstalledBeforeTermux = if (jarvisInfo != null && termuxInfo != null) {
            jarvisInfo.firstInstallTime < termuxInfo.firstInstallTime
        } else {
            false
        }
        val blockingReason = when {
            permGranted -> "External Termux RUN_COMMAND permission active hai."
            !installed -> "External com.termux installed nahi hai — Built-in Jarvis Linux Core bina kisi permission ke chal raha hai."
            !isPermissionDeclaredByTermux -> "Aapka installed Termux RUN_COMMAND permission expose nahi kar raha (Play Store / old build). F-Droid ya GitHub wala Termux install karein."
            isPlayStoreTermux -> "Play Store wala Termux deprecated hai aur external RUN_COMMAND block karta hai. F-Droid ya GitHub Release wala Termux install karein."
            wasJarvisInstalledBeforeTermux -> "Android Bug Detected: Jarvis pehle install hua tha aur Termux baad me! Isi wajah se Settings > Additional permissions me toggle nahi dikh raha. Niche 1-Tap Wireless ADB se auto-grant karein ya Jarvis APK bina uninstall kiye dubara install (update) karein."
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> "Android 13+ Restricted Settings lock: Sideloaded apps ke liye Settings me Additional Permissions hidden/disabled rehti hai jab tak App Info (⋮ -> Allow restricted settings) ya Wireless ADB se unlock na ho."
            else -> "Settings > App Info > Permissions > Additional permissions me toggle enable karein ya niche Wireless ADB se 1-tap unlock karein."
        }
        return TermuxBridgeDiagnostics(
            isTermuxInstalled = installed,
            hasRunCommandPermission = permGranted,
            isPermissionDeclaredByTermux = isPermissionDeclaredByTermux,
            isPlayStoreTermux = isPlayStoreTermux,
            wasJarvisInstalledBeforeTermux = wasJarvisInstalledBeforeTermux,
            blockingReasonSummary = blockingReason,
            adbGrantCommand = "adb shell appops set $pkg ACCESS_RESTRICTED_SETTINGS allow && adb shell pm grant $pkg $TERMUX_RUN_COMMAND_PERMISSION",
            termuxPropertiesCommand = TERMUX_ALLOW_EXTERNAL_CMD
        )
    }

    data class TermuxBridgeDiagnostics(
        val isTermuxInstalled: Boolean,
        val hasRunCommandPermission: Boolean,
        val isPermissionDeclaredByTermux: Boolean = false,
        val isPlayStoreTermux: Boolean = false,
        val wasJarvisInstalledBeforeTermux: Boolean = false,
        val blockingReasonSummary: String = "",
        val adbGrantCommand: String,
        val termuxPropertiesCommand: String
    )

    companion object {
        private const val TAG = "JarvisTermuxBridge"
        const val TERMUX_PACKAGE = "com.termux"
        const val TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
        const val TERMUX_ALLOW_EXTERNAL_CMD =
            "mkdir -p ~/.termux && grep -qxF 'allow-external-apps = true' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps = true' >> ~/.termux/termux.properties && termux-reload-settings"
        private const val TERMUX_SERVICE_CLASS = "com.termux.app.RunCommandService"
        private const val TERMUX_RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
        private const val EXTRA_RUN_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        private const val EXTRA_RUN_COMMAND_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val EXTRA_RUN_COMMAND_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        private const val EXTRA_RUN_COMMAND_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        private const val EXTRA_RUN_COMMAND_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
        private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
    }
}
