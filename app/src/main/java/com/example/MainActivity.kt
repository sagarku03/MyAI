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
import androidx.lifecycle.lifecycleScope
import com.jarvisai.assistant.service.LiveVoiceSessionBridge
import com.jarvisai.assistant.service.OfflineWakeWordManager
import com.jarvisai.assistant.ui.JarvisApp
import com.jarvisai.assistant.viewmodel.JarvisViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"

    private val jarvisViewModel: JarvisViewModel by viewModels {
        JarvisViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvisai.assistant.util.IndiaTimeUtils.enforceProcessIndiaTimeZone()
        super.onCreate(savedInstanceState)

        configureLockScreenWakeup(intent)
        enableEdgeToEdge()

        setContent {
            JarvisApp(viewModel = jarvisViewModel)
        }

        // --- STEP 2: NON-BLOCKING BACKGROUND SERVICE INITIALIZATIONS ---
        safeInitializeServices()

        // --- STEP 3: SAFE RUNTIME PERMISSIONS REQUEST ---
        requestAppPermissions()
    }

    private fun safeInitializeServices() {
        com.jarvisai.assistant.ui.voice.AgyVoiceBubble.registerBridgeHooks()
        com.jarvisai.assistant.voice.agy.AgyVoicePreferences.init(applicationContext)
        com.jarvisai.assistant.voice.agy.VoiceCallLauncher.publishShortcut(applicationContext)

        runCatching {
            stopService(Intent(this, com.jarvisai.assistant.service.JarvisOverlayHudService::class.java))
            com.jarvisai.assistant.service.JarvisOverlayHudService.setOverlayEnabled(applicationContext, false)
        }

        runCatching {
            com.jarvisai.assistant.service.TaskExecutor.emergencyHaltAndReset()
        }.onFailure { Log.w(TAG, "TaskExecutor reset error: ${it.message}") }

        lifecycleScope.launch(Dispatchers.Default) {
            runCatching {
                com.jarvisai.assistant.terminal.LocalEnvironmentManager.migrateLegacyJarvisFolders(applicationContext)
            }.onFailure { Log.w(TAG, "Legacy folder migration warning: ${it.message}") }

            runCatching {
                com.jarvisai.assistant.mcp.server.AndroidLocalBridgeServer.instance.start(applicationContext)
            }.onFailure { Log.w(TAG, "AndroidLocalBridgeServer start error: ${it.message}") }

            runCatching {
                com.jarvisai.assistant.agent.CommandsPermissionManager.load(applicationContext)
                com.jarvisai.assistant.mcp.McpServersManager.refreshAll(applicationContext)
                com.jarvisai.assistant.skills.SkillsAndPluginsManager.refreshSkills(applicationContext)
                com.jarvisai.assistant.skills.SkillsAndPluginsManager.refreshInstalledPlugins(applicationContext)
            }.onFailure { Log.w(TAG, "Agentra MCP/Skills/Commands warmup error: ${it.message}") }

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
                com.jarvisai.assistant.service.TermuxService.start(applicationContext)
                Log.i(TAG, "TermuxService foreground notification & daemon supervisor started.")
            }.onFailure { Log.w(TAG, "Failed to start TermuxService: ${it.message}") }

            runCatching {
                com.jarvisai.assistant.terminal.LocalServerManager.autoStartOnAppLaunch(applicationContext)
            }.onFailure { Log.w(TAG, "LocalServerManager autoStart error: ${it.message}") }
        }
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
            val termuxPerm = "com.termux.permission.RUN_COMMAND"
            val isTermuxPermDeclared = runCatching {
                packageManager.getPermissionInfo(termuxPerm, 0)
                true
            }.getOrDefault(false)
            if (isTermuxPermDeclared && checkSelfPermission(termuxPerm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                perms.add(termuxPerm)
            }
            if (perms.isNotEmpty()) {
                requestPermissions(perms.toTypedArray(), 200)
            }
        }
    }

    fun openTermuxPermissionSettings(context: Context = this) {
        Companion.openTermuxPermissionSettings(context)
    }

    companion object {
        fun openTermuxPermissionSettings(context: Context) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }

        fun openAllFilesAccessSettings(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } else {
                    openTermuxPermissionSettings(context)
                }
            } catch (_: Exception) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val fallback = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(fallback)
                    }
                } catch (_: Exception) {}
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

            if (incomingIntent?.getBooleanExtra(com.jarvisai.assistant.voice.agy.VoiceCallLauncher.EXTRA_START_CALL, false) == true) {
                incomingIntent.removeExtra(com.jarvisai.assistant.voice.agy.VoiceCallLauncher.EXTRA_START_CALL)
                com.jarvisai.assistant.voice.agy.VoiceCallLauncher.requestStart()
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
        com.jarvisai.assistant.voice.agy.VoiceCallLauncher.setAppVisible(true)
    }

    override fun onStop() {
        super.onStop()
        LiveVoiceSessionBridge.setAppInBackground(true)
        com.jarvisai.assistant.voice.agy.VoiceCallLauncher.setAppVisible(false)
    }

    override fun onDestroy() {
        runCatching { com.jarvisai.assistant.mcp.server.AndroidLocalBridgeServer.instance.stop() }
        runCatching { com.jarvisai.assistant.service.TaskExecutor.emergencyHaltAndReset() }
        super.onDestroy()
    }
}
