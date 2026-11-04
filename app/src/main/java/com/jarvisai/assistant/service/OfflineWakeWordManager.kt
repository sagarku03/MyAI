package com.jarvisai.assistant.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Explicit state machine for the JARVIS Wake Word Engine (Section 8.3).
 */
enum class WakeWordState(val displayLabel: String, val badgeText: String) {
    DISABLED("Disabled", "OFF // STANDBY"),
    INITIALIZING("Initializing...", "INITIALIZING"),
    LISTENING("Listening for 'Hey Jarvis'", "ACTIVE // LISTENING"),
    WAKE_WORD_DETECTED("Wake Word Detected!", "WAKE DETECTED"),
    PROCESSING("Processing Command...", "PROCESSING"),
    ERROR("Wake Word Error", "ERROR // CHECK PERMISSIONS")
}

/**
 * Wake Word Engine for "Hey Jarvis", "Jarvis", "Hey Jarvis, listen", "Suno Jarvis",
 * and "Jarvis, stop listening" (Section 8).
 *
 * Implements:
 * - Strict 6-state machine ([WakeWordState]).
 * - Pre-flight `RECORD_AUDIO` permission and `SpeechRecognizer` availability verification.
 * - Clean microphone handoff before starting Live Voice or command execution (preventing mic conflict).
 * - Inline command extraction when user speaks a wake word + command in one breath
 *   (e.g., "Hey Jarvis open YouTube" or "Jarvis Bluetooth on karo").
 */
class OfflineWakeWordManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "OfflineWakeWord"
        private const val RESTART_DELAY_MS = 2500L

        val WAKE_WORDS = listOf(
            "hey jarvis listen",
            "hey jarvis",
            "o jarvis",
            "ok jarvis",
            "suno jarvis",
            "hello jarvis",
            "jarvis",
            "जार्विस",
            "हे जार्विस",
            "सुनो जार्विस"
        )

        val STOP_PHRASES = listOf(
            "jarvis stop listening",
            "jarvis stop",
            "stop jarvis",
            "jarvis ruk jao",
            "jarvis chup ho jao",
            "जार्विस रुक जाओ"
        )

        @Volatile
        var testPermissionGrantedOverride: Boolean? = null

        @Volatile
        var testRecognitionAvailableOverride: Boolean? = null
    }

    var testPermissionGrantedOverride: Boolean?
        get() = Companion.testPermissionGrantedOverride
        set(value) {
            Companion.testPermissionGrantedOverride = value
        }

    var testRecognitionAvailableOverride: Boolean?
        get() = Companion.testRecognitionAvailableOverride
        set(value) {
            Companion.testRecognitionAvailableOverride = value
        }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var restartJob: Job? = null

    private val _wakeWordState = MutableStateFlow(WakeWordState.DISABLED)
    val wakeWordState: StateFlow<WakeWordState> = _wakeWordState.asStateFlow()

    private val _isWakeWordActive = MutableStateFlow(false)
    val isWakeWordActive: StateFlow<Boolean> = _isWakeWordActive.asStateFlow()

    private val _lastDetectedPhrase = MutableStateFlow<String?>(null)
    val lastDetectedPhrase: StateFlow<String?> = _lastDetectedPhrase.asStateFlow()

    private val _wakeWordError = MutableStateFlow<JarvisSubsystemError?>(null)
    val wakeWordError: StateFlow<JarvisSubsystemError?> = _wakeWordError.asStateFlow()

    private var onWakeWordDetectedListener: ((String) -> Unit)? = null
    private var onInlineCommandDetectedListener: ((String) -> Unit)? = null
    private var onStopListeningListener: (() -> Unit)? = null

    fun setOnWakeWordDetected(listener: (String) -> Unit) {
        onWakeWordDetectedListener = listener
    }

    fun setOnInlineCommandDetected(listener: (String) -> Unit) {
        onInlineCommandDetectedListener = listener
    }

    fun setOnStopListening(listener: () -> Unit) {
        onStopListeningListener = listener
    }

    fun hasMicrophonePermission(): Boolean {
        testPermissionGrantedOverride?.let { return it }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isSpeechRecognizerSupported(): Boolean {
        testRecognitionAvailableOverride?.let { return it }
        return runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
    }

    /**
     * Starts wake-word detection after verifying microphone permission and recognizer availability.
     * Returns `true` if startup succeeded (or entered `LISTENING`), or `false` if blocked by missing permission/error.
     */
    fun startListening(): Boolean {
        if (_wakeWordState.value == WakeWordState.LISTENING && _isWakeWordActive.value) {
            return true
        }

        _wakeWordState.value = WakeWordState.INITIALIZING
        _wakeWordError.value = null

        // 1. Verify RECORD_AUDIO permission before claiming Wake Word is ON
        if (!hasMicrophonePermission()) {
            val err = JarvisDiagnosticLogger.recordError(
                subsystem = "WAKE_WORD",
                errorCode = "RECORD_AUDIO_PERMISSION_DENIED",
                errorMessage = "Microphone permission is required for Wake Word detection.",
                technicalCause = "Manifest.permission.RECORD_AUDIO is not granted",
                recoveryRecommendation = "Grant Microphone permission to JARVIS AI in Android Settings.",
                retryEligible = true
            )
            _wakeWordError.value = err
            _isWakeWordActive.value = false
            _wakeWordState.value = WakeWordState.ERROR
            return false
        }

        // 2. Verify SpeechRecognizer availability
        if (!isSpeechRecognizerSupported()) {
            val err = JarvisDiagnosticLogger.recordError(
                subsystem = "WAKE_WORD",
                errorCode = "SPEECH_RECOGNIZER_UNAVAILABLE",
                errorMessage = "On-device speech recognition service is unavailable on this device.",
                technicalCause = "SpeechRecognizer.isRecognitionAvailable(context) returned false",
                recoveryRecommendation = "Ensure Google Speech Services / Android System Intelligence is enabled.",
                retryEligible = false
            )
            _wakeWordError.value = err
            _isWakeWordActive.value = false
            _wakeWordState.value = WakeWordState.ERROR
            return false
        }

        _isWakeWordActive.value = true
        _wakeWordState.value = WakeWordState.LISTENING
        mainHandler.post {
            initAndStartRecognizer()
        }
        return true
    }

    /**
     * Stops wake-word listening, cancels pending restarts, releases microphone resources,
     * and transitions state to [WakeWordState.DISABLED].
     */
    fun stopListening() {
        _isWakeWordActive.value = false
        _wakeWordState.value = WakeWordState.DISABLED
        restartJob?.cancel()
        restartJob = null
        mainHandler.post {
            releaseRecognizerInternal()
        }
    }

    /**
     * Pauses the wake-word microphone during an active Live Voice session or command capture
     * so there is no microphone conflict, then resumes once the session ends.
     */
    fun pauseForActiveSession() {
        restartJob?.cancel()
        restartJob = null
        mainHandler.post {
            releaseRecognizerInternal()
        }
        if (_isWakeWordActive.value) {
            _wakeWordState.value = WakeWordState.PROCESSING
        }
    }

    fun resumeAfterSessionIfEnabled() {
        if (_isWakeWordActive.value && hasMicrophonePermission()) {
            _wakeWordState.value = WakeWordState.LISTENING
            scheduleRestart()
        }
    }

    private fun releaseRecognizerInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
    }

    private fun initAndStartRecognizer() {
        if (!_isWakeWordActive.value) return
        if (!hasMicrophonePermission()) {
            _isWakeWordActive.value = false
            _wakeWordState.value = WakeWordState.ERROR
            return
        }

        try {
            if (speechRecognizer == null) {
                if (!isSpeechRecognizerSupported()) {
                    Log.w(TAG, "SpeechRecognizer not available on this device")
                    _isWakeWordActive.value = false
                    _wakeWordState.value = WakeWordState.ERROR
                    return
                }
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            if (_isWakeWordActive.value) {
                                _wakeWordState.value = WakeWordState.LISTENING
                            }
                        }

                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}

                        override fun onError(error: Int) {
                            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                                val err = JarvisDiagnosticLogger.recordError(
                                    subsystem = "WAKE_WORD",
                                    errorCode = "ERROR_INSUFFICIENT_PERMISSIONS",
                                    errorMessage = "Microphone permission revoked while Wake Word was active.",
                                    technicalCause = "SpeechRecognizer error=$error",
                                    recoveryRecommendation = "Grant Microphone permission to re-enable Wake Word.",
                                    retryEligible = true
                                )
                                _wakeWordError.value = err
                                _isWakeWordActive.value = false
                                _wakeWordState.value = WakeWordState.ERROR
                                return
                            }
                            releaseRecognizerInternal()
                            if (_isWakeWordActive.value) {
                                scheduleRestart()
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            processHypotheses(matches)
                            releaseRecognizerInternal()
                            if (_isWakeWordActive.value && _wakeWordState.value == WakeWordState.LISTENING) {
                                scheduleRestart()
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            processHypotheses(matches)
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Wake word start error: ${e.message}")
            if (_isWakeWordActive.value) {
                scheduleRestart()
            }
        }
    }

    /**
     * Evaluates recognition hypotheses for stop phrases, wake words ("Hey Jarvis", "Jarvis",
     * "Hey Jarvis, listen"), and optional trailing inline commands (e.g., "Hey Jarvis open YouTube").
     * Visible as internal for deterministic Robolectric testing (TEST 11, TEST 12, TEST 13, TEST 18).
     */
    internal fun processHypotheses(matches: List<String>?) {
        if (matches.isNullOrEmpty()) return
        for (raw in matches) {
            val clean = raw.trim()
            val norm = clean.lowercase()
                .replace(Regex("[,!.:?;]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            if (STOP_PHRASES.any { norm.contains(it) }) {
                _lastDetectedPhrase.value = clean
                _wakeWordState.value = WakeWordState.PROCESSING
                onStopListeningListener?.invoke()
                if (_isWakeWordActive.value) {
                    _wakeWordState.value = WakeWordState.LISTENING
                }
                return
            }

            val matchedWake = WAKE_WORDS.firstOrNull { norm.contains(it) }
            if (matchedWake != null) {
                _lastDetectedPhrase.value = clean
                _wakeWordState.value = WakeWordState.WAKE_WORD_DETECTED
                Log.i(TAG, "Wake word detected: phrase='$clean' matched='$matchedWake'")

                // Extract optional inline command spoken after the wake word
                val inlineCommand = extractInlineCommandAfterWakeWord(clean)

                // Release wake-word recognizer mic before handing off to command / Live Voice
                mainHandler.post {
                    releaseRecognizerInternal()
                }

                _wakeWordState.value = WakeWordState.PROCESSING
                if (!inlineCommand.isNullOrBlank()) {
                    onInlineCommandDetectedListener?.invoke(inlineCommand)
                }
                onWakeWordDetectedListener?.invoke(clean)
                return
            }
        }
    }

    fun matchesWakeWord(utterance: String): Boolean {
        val norm = utterance.trim().lowercase()
            .replace(Regex("[,!.:?;]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return WAKE_WORDS.any { norm.contains(it) }
    }

    fun simulateRecognizedUtteranceForTest(utterance: String): Boolean {
        val prevPhrase = _lastDetectedPhrase.value
        processHypotheses(listOf(utterance))
        return _lastDetectedPhrase.value == utterance.trim() || _lastDetectedPhrase.value != prevPhrase
    }

    internal fun extractInlineCommandAfterWakeWord(utterance: String): String? {
        val cleaned = utterance.trim()
        val afterWake = cleaned
            .replace(
                Regex("(?i)^.*?(?:hey\\s+jarvis\\s+listen|hey\\s+jarvis|ok\\s+jarvis|o\\s+jarvis|suno\\s+jarvis|hello\\s+jarvis|jarvis|हे\\s+जार्विस|सुनो\\s+जार्विस|जार्विस)[,!.:\\s]*"),
                ""
            )
            .trim()
        if (afterWake.isBlank()) return null
        val lower = afterWake.lowercase()
        if (lower in setOf("listen", "sun", "suno", "batao", "hello", "hi")) {
            return null
        }
        return afterWake
    }

    private fun scheduleRestart() {
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(RESTART_DELAY_MS)
            if (_isWakeWordActive.value && _wakeWordState.value == WakeWordState.LISTENING) {
                mainHandler.post {
                    try {
                        speechRecognizer?.cancel()
                    } catch (_: Exception) {
                    }
                    initAndStartRecognizer()
                }
            }
        }
    }
}
