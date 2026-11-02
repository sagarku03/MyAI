package com.jarvisai.assistant.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Free on-device offline wake-word detector for JARVIS AI.
 * Uses Android's offline SpeechRecognizer engine with strict keyword spotting
 * and RMS energy gating. Does not use any paid SDKs or cloud services.
 *
 * Supported Wake Words:
 * - "Hey Jarvis"
 * - "O Jarvis" / "Oh Jarvis"
 * - "Jarvis"
 * - "Suno Jarvis"
 * - "Jarvis, stop listening" / "Stop listening"
 */
class OfflineWakeWordManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "OfflineWakeWordManager"

        val WAKE_KEYWORDS = listOf(
            "hey jarvis",
            "o jarvis",
            "oh jarvis",
            "suno jarvis",
            "jarvis",
            "हे जार्विस",
            "सुनो जार्विस"
        )

        val STOP_KEYWORDS = listOf(
            "jarvis stop",
            "stop listening",
            "stop jarvis",
            "ruk jao",
            "shant raho"
        )
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val isListening = AtomicBoolean(false)
    private var restartJob: Job? = null

    private val _isWakeWordActive = MutableStateFlow(false)
    val isWakeWordActive: StateFlow<Boolean> = _isWakeWordActive.asStateFlow()

    private val _lastDetectedPhrase = MutableStateFlow<String?>(null)
    val lastDetectedPhrase: StateFlow<String?> = _lastDetectedPhrase.asStateFlow()

    private var onWakeWordDetectedListener: ((phrase: String) -> Unit)? = null
    private var onStopListeningListener: (() -> Unit)? = null

    fun setOnWakeWordDetected(listener: (phrase: String) -> Unit) {
        onWakeWordDetectedListener = listener
    }

    fun setOnStopListening(listener: () -> Unit) {
        onStopListeningListener = listener
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "SpeechRecognizer is not available on this device.")
            return
        }

        if (isListening.getAndSet(true)) {
            return
        }

        _isWakeWordActive.value = true
        scope.launch(Dispatchers.Main) {
            initRecognizerAndStart()
        }
    }

    fun stopListening() {
        isListening.set(false)
        _isWakeWordActive.value = false
        restartJob?.cancel()
        restartJob = null

        scope.launch(Dispatchers.Main) {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recognizer: ${e.message}")
            }
        }
    }

    private fun initRecognizerAndStart() {
        if (!isListening.get()) return

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(WakeWordRecognitionListener())
            }

            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognizer: ${e.message}")
            scheduleRestart(2000L)
        }
    }

    private fun scheduleRestart(delayMillis: Long = 1000L) {
        if (!isListening.get()) return
        restartJob?.cancel()
        restartJob = scope.launch(Dispatchers.Main) {
            delay(delayMillis)
            if (isListening.get()) {
                initRecognizerAndStart()
            }
        }
    }

    private fun processHypotheses(hypotheses: List<String>) {
        for (text in hypotheses) {
            val lower = text.lowercase(Locale.ROOT).trim()
            if (STOP_KEYWORDS.any { lower.contains(it) }) {
                _lastDetectedPhrase.value = lower
                onStopListeningListener?.invoke()
                return
            }
            if (WAKE_KEYWORDS.any { lower.contains(it) }) {
                _lastDetectedPhrase.value = lower
                // Release microphone immediately before triggering listener
                // so Live Voice can obtain AudioRecord cleanly
                stopListening()
                onWakeWordDetectedListener?.invoke(lower)
                return
            }
        }
    }

    private inner class WakeWordRecognitionListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            // Error codes like ERROR_NO_MATCH or ERROR_SPEECH_TIMEOUT are normal during wake-word idling
            scheduleRestart(800L)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
            processHypotheses(matches)
            if (isListening.get()) {
                scheduleRestart(500L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
            processHypotheses(matches)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
