package com.jarvisai.assistant.assistant

import android.content.Intent
import android.speech.RecognitionService

class JarvisRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // Recognition callback handled via Gemini Live Voice engine or Android SpeechRecognizer
    }

    override fun onCancel(listener: Callback?) {
    }

    override fun onStopListening(listener: Callback?) {
    }
}
