package com.jarvisai.assistant.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.jarvisai.assistant.service.JarvisOverlayHudService
import com.jarvisai.assistant.service.LiveVoiceForegroundService

class JarvisVoiceSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return JarvisVoiceSession(this)
    }

    class JarvisVoiceSession(context: Context) : VoiceInteractionSession(context) {

        override fun onShow(args: Bundle?, showFlags: Int) {
            super.onShow(args, showFlags)
            try {
                // Wake up live voice service and trigger Cyber-HUD safely
                LiveVoiceForegroundService.wakeSessionIfNeeded(context)

                if (JarvisOverlayHudService.canDrawOverlays(context)) {
                    val hudIntent = Intent(context, JarvisOverlayHudService::class.java).apply {
                        action = JarvisOverlayHudService.ACTION_SHOW_HUD
                    }
                    context.startService(hudIntent)
                }
            } catch (e: Exception) {
                // Fallback to launching overlay HUD
            }
            hide()
        }
    }
}
