package com.jarvisai.assistant.assistant

import android.service.voice.VoiceInteractionService
import android.util.Log
import com.jarvisai.assistant.service.LiveVoiceForegroundService
import com.jarvisai.assistant.service.LiveVoiceSessionBridge

/**
 * Android System Default Digital Assistant service for JARVIS AI.
 * When the user selects JARVIS as the system assistant in Android Settings,
 * this service binds to the OS VoiceInteraction framework, allowing JARVIS
 * to be invoked via Power button long-press, corner swipe, or keyguard headset buttons
 * even when the screen is locked and with zero idle battery drain.
 */
class JarvisVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        Log.i("JarvisVoiceInteraction", "JARVIS System Digital Assistant service is READY.")
        try {
            if (LiveVoiceSessionBridge.isWakeWordListening.value || LiveVoiceSessionBridge.isSessionActive.value) {
                LiveVoiceForegroundService.startServiceIfNeeded(applicationContext)
            }
        } catch (e: Exception) {
            Log.w("JarvisVoiceInteraction", "Unable to start foreground service from VoiceInteractionService: ${e.message}")
        }
    }

    override fun onShutdown() {
        super.onShutdown()
    }
}
