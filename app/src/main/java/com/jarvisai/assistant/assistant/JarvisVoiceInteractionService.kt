package com.jarvisai.assistant.assistant

import android.service.voice.VoiceInteractionService

/**
 * Android System Default Digital Assistant service for JARVIS AI.
 * When the user selects JARVIS as the system assistant in Android Settings,
 * this service binds to the OS VoiceInteraction framework, allowing JARVIS
 * to be invoked via Power button long-press, corner swipe, or keyguard headset buttons.
 */
class JarvisVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
    }

    override fun onShutdown() {
        super.onShutdown()
    }
}
