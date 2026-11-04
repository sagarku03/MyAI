package com.jarvisai.assistant.assistant

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import com.example.MainActivity
import com.jarvisai.assistant.service.JarvisOverlayHudService
import com.jarvisai.assistant.service.LiveVoiceForegroundService
import com.jarvisai.assistant.service.OfflineWakeWordManager

class JarvisAssistActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                km?.requestDismissKeyguard(this, null)
            }

            LiveVoiceForegroundService.wakeSessionIfNeeded(this)

            if (JarvisOverlayHudService.canDrawOverlays(this)) {
                val hudIntent = Intent(this, JarvisOverlayHudService::class.java).apply {
                    action = JarvisOverlayHudService.ACTION_SHOW_HUD
                }
                startService(hudIntent)
            } else {
                val openMain = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(OfflineWakeWordManager.EXTRA_WAKE_WORD_TRIGGERED, true)
                }
                startActivity(openMain)
            }
        } catch (_: Exception) {
        }
        finish()
    }
}
