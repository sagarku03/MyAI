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
        if (!isTermuxInstalled()) {
            return TermuxCommandResult(
                success = false,
                stdout = "",
                stderr = "Termux application is not installed on this device.",
                exitCode = -1,
                errorMessage = "TERMUX_NOT_INSTALLED"
            )
        }

        if (!hasRunCommandPermission()) {
            return TermuxCommandResult(
                success = false,
                stdout = "",
                stderr = "com.termux.permission.RUN_COMMAND permission is not granted. Please grant it in app settings.",
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

    companion object {
        private const val TAG = "JarvisTermuxBridge"
        const val TERMUX_PACKAGE = "com.termux"
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
