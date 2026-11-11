package com.example

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.jarvisai.assistant.service.LiveVoiceSessionBridge
import com.jarvisai.assistant.service.OfflineWakeWordManager
import com.jarvisai.assistant.ui.JarvisApp
import com.jarvisai.assistant.viewmodel.JarvisViewModel
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"

    private val jarvisViewModel: JarvisViewModel by viewModels {
        JarvisViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // --- STEP 1: GLOBAL UNCAUGHT EXCEPTION CRASH SHIELD ---
        installGlobalCrashShield()

        super.onCreate(savedInstanceState)

        // --- STEP 2: SAFE SERVICE INITIALIZATIONS ---
        safeInitializeServices()

        configureLockScreenWakeup(intent)
        enableEdgeToEdge()

        // --- STEP 3: SAFE RUNTIME PERMISSIONS REQUEST ---
        requestAppPermissions()

        setContent {
            JarvisApp(viewModel = jarvisViewModel)
        }
    }

    private fun installGlobalCrashShield() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "FATAL CRASH DETECTED on thread ${thread.name}: ${throwable.message}", throwable)
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()

                // Save crash log to internal storage and external Download folder
                val logText = "JARVIS CRASH LOG [${System.currentTimeMillis()}]\nThread: ${thread.name}\n\n$stackTrace"
                runCatching {
                    val crashFile = File(applicationContext.filesDir, "jarvis_last_crash.log")
                    crashFile.writeText(logText)
                }
                runCatching {
                    val publicLog = File("/sdcard/Download/jarvis_crash.log")
                    publicLog.writeText(logText)
                }
            } catch (_: Throwable) {}

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun safeInitializeServices() {
        runCatching {
            com.jarvisai.assistant.service.TaskExecutor.emergencyHaltAndReset()
        }.onFailure { Log.w(TAG, "TaskExecutor reset error: ${it.message}") }

        runCatching {
            com.jarvisai.assistant.mcp.server.AndroidLocalBridgeServer.instance.start(applicationContext)
        }.onFailure { Log.w(TAG, "AndroidLocalBridgeServer start error: ${it.message}") }

        runCatching {
            com.jarvisai.assistant.terminal.LocalTerminalBridge.instance.init(applicationContext)
        }.onFailure { Log.w(TAG, "LocalTerminalBridge init error: ${it.message}") }

        runCatching {
            com.jarvisai.assistant.agent.AgentOrchestrator.init(applicationContext)
        }.onFailure { Log.w(TAG, "AgentOrchestrator init error: ${it.message}") }

        runCatching {
            com.jarvisai.assistant.extension.BrowserExtensionManager.init(applicationContext)
        }.onFailure { Log.w(TAG, "BrowserExtensionManager init error: ${it.message}") }

        runCatching {
            com.jarvisai.assistant.core.SuperJarvisOrchestrator.getInstance(applicationContext)
        }.onFailure { Log.w(TAG, "SuperJarvisOrchestrator init error: ${it.message}") }

        runCatching {
            com.jarvisai.assistant.terminal.LocalServerManager.autoStartOnAppLaunch(applicationContext)
        }.onFailure { Log.w(TAG, "LocalServerManager autoStart error: ${it.message}") }
    }

    private fun requestAppPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val perms = mutableListOf<String>()
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                perms.add(android.Manifest.permission.RECORD_AUDIO)
            }
            if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                perms.add(android.Manifest.permission.READ_CONTACTS)
            }
            if (checkSelfPermission(android.Manifest.permission.CALL_PHONE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                perms.add(android.Manifest.permission.CALL_PHONE)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    perms.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    perms.add(android.Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (perms.isNotEmpty()) {
                requestPermissions(perms.toTypedArray(), 200)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureLockScreenWakeup(intent)
    }

    private fun configureLockScreenWakeup(incomingIntent: Intent?) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
            }

            if (incomingIntent?.getBooleanExtra(OfflineWakeWordManager.EXTRA_WAKE_WORD_TRIGGERED, false) == true) {
                incomingIntent.removeExtra(OfflineWakeWordManager.EXTRA_WAKE_WORD_TRIGGERED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    km?.requestDismissKeyguard(this, null)
                }
                jarvisViewModel.handleWakeWordActivityLaunch()
            }

            if (incomingIntent?.action == Intent.ACTION_SEND) {
                val sharedText = incomingIntent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
                val sharedSubject = incomingIntent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
                @Suppress("DEPRECATION")
                val sharedUri = incomingIntent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)?.toString().orEmpty()
                if (sharedText.isNotBlank()) {
                    com.jarvisai.assistant.service.WorkingMemoryBlackboard.putScratchpadVariable("shared_dropzone_text", sharedText)
                }
                if (sharedSubject.isNotBlank()) {
                    com.jarvisai.assistant.service.WorkingMemoryBlackboard.putScratchpadVariable("shared_dropzone_subject", sharedSubject)
                }
                if (sharedUri.isNotBlank()) {
                    com.jarvisai.assistant.service.WorkingMemoryBlackboard.putScratchpadVariable("shared_dropzone_uri", sharedUri)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onStart() {
        super.onStart()
        LiveVoiceSessionBridge.setAppInBackground(false)
    }

    override fun onStop() {
        super.onStop()
        LiveVoiceSessionBridge.setAppInBackground(true)
    }

    override fun onDestroy() {
        runCatching { com.jarvisai.assistant.mcp.server.AndroidLocalBridgeServer.instance.stop() }
        runCatching { com.jarvisai.assistant.service.TaskExecutor.emergencyHaltAndReset() }
        super.onDestroy()
    }
}
