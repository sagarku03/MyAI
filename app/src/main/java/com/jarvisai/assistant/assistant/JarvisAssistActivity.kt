package com.jarvisai.assistant.assistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.jarvisai.assistant.service.JarvisOverlayHudService
import com.jarvisai.assistant.service.LiveVoiceForegroundService

class JarvisAssistActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val serviceIntent = Intent(this, LiveVoiceForegroundService::class.java).apply {
                action = LiveVoiceForegroundService.ACTION_WAKE_SESSION
            }
            startForegroundService(serviceIntent)

            val hudIntent = Intent(this, JarvisOverlayHudService::class.java).apply {
                action = JarvisOverlayHudService.ACTION_SHOW_HUD
            }
            startService(hudIntent)
        } catch (_: Exception) {
        }
        finish()
    }
}
