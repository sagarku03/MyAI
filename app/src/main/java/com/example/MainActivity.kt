package com.example

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.jarvisai.assistant.service.LiveVoiceSessionBridge
import com.jarvisai.assistant.service.OfflineWakeWordManager
import com.jarvisai.assistant.ui.JarvisApp
import com.jarvisai.assistant.viewmodel.JarvisViewModel

class MainActivity : ComponentActivity() {

    private val jarvisViewModel: JarvisViewModel by viewModels {
        JarvisViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenWakeup(intent)
        enableEdgeToEdge()

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

        setContent {
            JarvisApp(viewModel = jarvisViewModel)
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    km?.requestDismissKeyguard(this, null)
                }
            }
        } catch (_: Exception) {
        }
    }

    override fun onStart() {
        super.onStart()
        LiveVoiceSessionBridge.setAppInBackground(false)
    }

    override fun onStop() {
        super.onStop()
        LiveVoiceSessionBridge.setAppInBackground(true)
    }
}
