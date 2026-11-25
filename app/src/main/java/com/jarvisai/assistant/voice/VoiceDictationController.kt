package com.jarvisai.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.jarvisai.assistant.service.LiveVoiceForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Continuous, multilingual Speech-to-Text dictation controller for the chat composer box.
 *
 * - Tap middle button to start: captures voice via Android SpeechRecognizer with automatic
 *   multilingual language detection & switching (API 33+/34+ with graceful fallback),
 *   streaming live partial + finalized transcripts directly into the composer input box.
 * - Stays active across natural pauses until the user taps the middle button again to stop
 *   or taps Send.
 */
class VoiceDictationController(
    private val context: Context,
    private val onTranscriptUpdate: (String) -> Unit,
    private val onErrorMessage: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private val cactusRecorder = com.jarvisai.assistant.cactus.CactusAudioRecorder(context)
    private val cactusScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )
    private var isUsingCactusOfflineStt = false

    private val _isDictating = MutableStateFlow(false)
    val isDictating: StateFlow<Boolean> = _isDictating.asStateFlow()

    private val _dictationAudioLevel = MutableStateFlow(0f)
    val dictationAudioLevel: StateFlow<Float> = _dictationAudioLevel.asStateFlow()

    private var prefixText: String = ""
    private val committedSegments = mutableListOf<String>()
    private var currentPartial: String = ""
    private var allowLanguageDetectionExtras = true
    private var consecutiveTransientErrors = 0

    private val restartRunnable = Runnable {
        if (_isDictating.value) {
            beginListeningInternal()
        }
    }

    fun startDictation(existingPromptText: String) {
        if (_isDictating.value) return

        val cactusCfg = com.jarvisai.assistant.cactus.CactusNativeRuntime.config.value
        val cactusStatus = com.jarvisai.assistant.cactus.CactusNativeRuntime.status.value
        val preferCactusOffline = cactusCfg.offlineSttEnabled ||
            (!SpeechRecognizer.isRecognitionAvailable(context) && (cactusStatus.isServerRunning || cactusStatus.binaryInstalled))

        if (preferCactusOffline) {
            LiveVoiceForegroundService.pauseMicrophoneForExternalTool()
            prefixText = existingPromptText.trimEnd()
            committedSegments.clear()
            currentPartial = ""
            isUsingCactusOfflineStt = true
            _isDictating.value = true
            _dictationAudioLevel.value = 0f

            val started = cactusRecorder.startRecording { amp ->
                _dictationAudioLevel.value = amp
            }
            if (!started) {
                isUsingCactusOfflineStt = false
                _isDictating.value = false
                LiveVoiceForegroundService.resumeMicrophoneAfterExternalTool()
                onErrorMessage("Cactus Offline STT mic start nahi ho paya. Microphone permission check karein.")
            }
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onErrorMessage("Speech recognition is not available on this device.")
            return
        }

        // Pause background wake-word/live-voice mic capture so SpeechRecognizer gets clean audio focus
        LiveVoiceForegroundService.pauseMicrophoneForExternalTool()

        prefixText = existingPromptText.trimEnd()
        committedSegments.clear()
        currentPartial = ""
        consecutiveTransientErrors = 0
        isUsingCactusOfflineStt = false
        _isDictating.value = true
        _dictationAudioLevel.value = 0f

        mainHandler.removeCallbacks(restartRunnable)
        beginListeningInternal()
    }

    fun stopDictation(commitPendingPartial: Boolean = true) {
        if (isUsingCactusOfflineStt) {
            isUsingCactusOfflineStt = false
            _isDictating.value = false
            _dictationAudioLevel.value = 0f
            val wavFile = cactusRecorder.stopAndWriteWav()
            LiveVoiceForegroundService.resumeMicrophoneAfterExternalTool()

            if (commitPendingPartial && wavFile != null) {
                cactusScope.launch {
                    val res = com.jarvisai.assistant.cactus.CactusAiService.transcribeAudioOffline(context, wavFile)
                    runCatching { wavFile.delete() }
                    mainHandler.post {
                        res.onSuccess { transcript ->
                            if (transcript.isNotBlank()) {
                                committedSegments.add(transcript.trim())
                                onTranscriptUpdate(buildFullTranscript())
                            }
                        }.onFailure { err ->
                            onErrorMessage(err.message ?: "Cactus Offline STT failed.")
                        }
                    }
                }
            } else {
                runCatching { wavFile?.delete() }
            }
            return
        }

        if (!_isDictating.value && speechRecognizer == null) return
        mainHandler.removeCallbacks(restartRunnable)
        _isDictating.value = false
        _dictationAudioLevel.value = 0f

        if (commitPendingPartial && currentPartial.isNotBlank()) {
            committedSegments.add(currentPartial.trim())
            currentPartial = ""
            onTranscriptUpdate(buildFullTranscript())
        } else {
            currentPartial = ""
        }

        destroyRecognizerOnMain()
        LiveVoiceForegroundService.resumeMicrophoneAfterExternalTool()
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        isUsingCactusOfflineStt = false
        cactusRecorder.cleanup()
        _isDictating.value = false
        _dictationAudioLevel.value = 0f
        destroyRecognizerOnMain()
        LiveVoiceForegroundService.resumeMicrophoneAfterExternalTool()
    }

    private fun destroyRecognizerOnMain() {
        val cleanup = {
            runCatching {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            }
            speechRecognizer = null
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cleanup()
        } else {
            mainHandler.post(cleanup)
        }
    }

    private fun beginListeningInternal() {
        if (!_isDictating.value) return
        val runStart = {
            if (_isDictating.value) {
                try {
                    if (speechRecognizer == null) {
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                            setRecognitionListener(createListener())
                        }
                    }
                    val intent = buildRecognizerIntent(useAutoLanguageDetection = allowLanguageDetectionExtras)
                    speechRecognizer?.startListening(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed starting SpeechRecognizer: ${e.message}")
                    if (allowLanguageDetectionExtras) {
                        allowLanguageDetectionExtras = false
                        scheduleRestart(150L)
                    } else {
                        stopDictation(commitPendingPartial = true)
                        onErrorMessage("Mic dictation start nahi ho paya: ${e.message ?: "Unknown error"}")
                    }
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runStart()
        } else {
            mainHandler.post(runStart)
        }
    }

    private fun buildRecognizerIntent(useAutoLanguageDetection: Boolean): Intent {
        val defaultLangTag = Locale.getDefault().toLanguageTag().ifBlank { "en-IN" }
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, defaultLangTag)

            if (useAutoLanguageDetection && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                    arrayListOf("en-IN", "hi-IN", "en-US", "bn-IN", "mr-IN", "ta-IN", "te-IN", "gu-IN", "pa-IN", "ur-IN")
                )
            }
            if (useAutoLanguageDetection && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                putExtra(
                    RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,
                    RecognizerIntent.LANGUAGE_SWITCH_HIGH_PRECISION
                )
            }
        }
    }

    private fun createListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            consecutiveTransientErrors = 0
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            if (_isDictating.value) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _dictationAudioLevel.value = normalized
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _dictationAudioLevel.value = 0f
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!_isDictating.value) return
            val spoken = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotBlank() && spoken != currentPartial) {
                currentPartial = spoken
                onTranscriptUpdate(buildFullTranscript())
            }
        }

        override fun onResults(results: Bundle?) {
            if (!_isDictating.value) return
            consecutiveTransientErrors = 0
            val spoken = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotBlank()) {
                committedSegments.add(spoken)
                currentPartial = ""
                onTranscriptUpdate(buildFullTranscript())
            } else if (currentPartial.isNotBlank()) {
                committedSegments.add(currentPartial.trim())
                currentPartial = ""
                onTranscriptUpdate(buildFullTranscript())
            }
            // Continue listening seamlessly until user explicitly taps Stop
            scheduleRestart(120L)
        }

        override fun onError(error: Int) {
            if (!_isDictating.value) return
            _dictationAudioLevel.value = 0f

            // If device recognizer rejected Android 13/14 language detection flags, immediately retry without them
            if (allowLanguageDetectionExtras && (
                    error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                        error == SpeechRecognizer.ERROR_CLIENT
                    )
            ) {
                Log.w(TAG, "Language detection flags unsupported (error=$error); falling back to standard recognizer.")
                allowLanguageDetectionExtras = false
                destroyRecognizerOnMain()
                scheduleRestart(150L)
                return
            }

            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Commit any partial speech heard before timeout, then keep listening until user stops
                    if (currentPartial.isNotBlank()) {
                        committedSegments.add(currentPartial.trim())
                        currentPartial = ""
                        onTranscriptUpdate(buildFullTranscript())
                    }
                    scheduleRestart(180L)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> {
                    consecutiveTransientErrors++
                    if (consecutiveTransientErrors <= 3) {
                        destroyRecognizerOnMain()
                        scheduleRestart(250L)
                    } else {
                        stopDictation(commitPendingPartial = true)
                        onErrorMessage("Voice mic busy hai. Dobara tap karke try karein.")
                    }
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    stopDictation(commitPendingPartial = true)
                    onErrorMessage("Microphone permission required for voice dictation.")
                }
                SpeechRecognizer.ERROR_AUDIO -> {
                    stopDictation(commitPendingPartial = true)
                    onErrorMessage("Microphone audio capture error.")
                }
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                    consecutiveTransientErrors++
                    if (consecutiveTransientErrors <= 2) {
                        scheduleRestart(400L)
                    } else {
                        stopDictation(commitPendingPartial = true)
                        onErrorMessage("Network error during speech transcription.")
                    }
                }
                else -> {
                    consecutiveTransientErrors++
                    if (consecutiveTransientErrors <= 2) {
                        scheduleRestart(250L)
                    } else {
                        stopDictation(commitPendingPartial = true)
                        onErrorMessage("Voice transcription stopped (code $error).")
                    }
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun scheduleRestart(delayMs: Long) {
        mainHandler.removeCallbacks(restartRunnable)
        if (_isDictating.value) {
            mainHandler.postDelayed(restartRunnable, delayMs)
        }
    }

    private fun buildFullTranscript(): String {
        val parts = mutableListOf<String>()
        if (prefixText.isNotBlank()) parts.add(prefixText)
        if (committedSegments.isNotEmpty()) parts.add(committedSegments.joinToString(" "))
        if (currentPartial.isNotBlank()) parts.add(currentPartial.trim())
        return parts.joinToString(" ").trim()
    }

    companion object {
        private const val TAG = "VoiceDictation"
    }
}
