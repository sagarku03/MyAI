package com.jarvisai.assistant.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Explicit state machine for the JARVIS Wake Word Engine (Section 8.3).
 */
enum class WakeWordState(val displayLabel: String, val badgeText: String) {
    DISABLED("Disabled", "OFF // STANDBY"),
    INITIALIZING("Initializing...", "INITIALIZING"),
    LISTENING("Listening for 'Hey Jarvis' (Online/Offline • Screen-Off Ready)", "ACTIVE // LISTENING"),
    WAKE_WORD_DETECTED("Wake Word Detected!", "WAKE DETECTED"),
    PROCESSING("Processing Command...", "PROCESSING"),
    ERROR("Wake Word Error", "ERROR // CHECK PERMISSIONS")
}

/**
 * 24/7 Persistent & Screen-Off Wake Word Engine for "Hey Jarvis", "Jarvis", "Suno Jarvis",
 * "हे जार्विस", "सुनो जार्विस", and "Jarvis, stop listening".
 *
 * Owned by [LiveVoiceForegroundService] via a persistent process-scoped singleton so swiping
 * the app from Android Recents (`ViewModel.onCleared()`) never kills the wake-word listener.
 */
class OfflineWakeWordManager(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    private val context: Context = context.applicationContext

    companion object {
        private const val TAG = "OfflineWakeWord"
        private const val RESTART_DELAY_MS = 650L
        private const val SAMPLE_RATE_HZ = 16000
        private const val FRAME_SAMPLES = 640 // 40ms frame at 16kHz
        private const val VAD_VOICE_DB_THRESHOLD = -36.0
        const val EXTRA_WAKE_WORD_TRIGGERED = "com.jarvisai.assistant.extra.WAKE_WORD_TRIGGERED"
        const val WAKE_WORD_WELCOME_GREETING = "Bataiye sir, main aapki kya madad kar sakta hoon?"

        val WAKE_WORDS = listOf(
            "hey jarvis listen",
            "hey jarvis",
            "o jarvis",
            "ok jarvis",
            "okay jarvis",
            "hi jarvis",
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

        @Volatile
        var testNetworkConnectedOverride: Boolean? = null

        @Volatile
        private var sharedInstance: OfflineWakeWordManager? = null

        fun getInstance(context: Context): OfflineWakeWordManager {
            return sharedInstance ?: synchronized(this) {
                sharedInstance ?: OfflineWakeWordManager(
                    context = context.applicationContext,
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                ).also { sharedInstance = it }
            }
        }
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

    var testNetworkConnectedOverride: Boolean?
        get() = Companion.testNetworkConnectedOverride
        set(value) {
            Companion.testNetworkConnectedOverride = value
        }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var restartJob: Job? = null
    private var audioRecordVadJob: Job? = null

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var isStage2RecognizerRunning = false

    @Volatile
    private var preferOfflineMode = true

    @Volatile
    private var consecutiveRecognizerFailures = 0

    @Volatile
    private var lastAcousticTriggerTimestamp = 0L

    @Volatile
    private var followUpCommandWindowUntilMs = 0L

    @Volatile
    private var isPausedForSession = false

    private var partialWakeLock: PowerManager.WakeLock? = null

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

    fun isNetworkConnected(): Boolean {
        testNetworkConnectedOverride?.let { return it }
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val net = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(net) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.isConnected == true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens a short follow-up command window (default 15s) after an offline wake word trigger
     * so the user can say "Hey Jarvis" -> wait for greeting -> say "Torch on karo" without repeating "Jarvis".
     */
    fun openOfflineFollowUpCommandWindow(durationMs: Long = 15_000L) {
        followUpCommandWindowUntilMs = System.currentTimeMillis() + durationMs
    }

    fun isOfflineFollowUpWindowActive(): Boolean {
        return System.currentTimeMillis() < followUpCommandWindowUntilMs
    }

    fun isDeviceScreenInteractive(): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isInteractive ?: true
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Starts wake-word detection after verifying microphone permission.
     * Supports both Stage-1 Continuous AudioRecord Offline VAD/Acoustic detection and Stage-2
     * Offline-First SpeechRecognizer verification.
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
            LiveVoiceSessionBridge.setWakeWordListening(false)
            return false
        }

        // 2. If test explicitly overrides recognition availability to false, honor it
        if (testRecognitionAvailableOverride == false && !isSpeechRecognizerSupported()) {
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
            LiveVoiceSessionBridge.setWakeWordListening(false)
            return false
        }

        isPausedForSession = false
        _isWakeWordActive.value = true
        _wakeWordState.value = WakeWordState.LISTENING
        acquirePartialWakeLock()

        // Keep Foreground Service alive for 24/7 background + screen-off wake word detection
        LiveVoiceSessionBridge.setWakeWordListening(true)
        if (testPermissionGrantedOverride == null) {
            LiveVoiceForegroundService.startServiceIfNeeded(context)
        }

        // Start Stage-1 continuous AudioRecord VAD + Offline Acoustic monitor
        startContinuousAudioRecordMonitor()
        return true
    }

    /**
     * Stops wake-word listening, cancels pending restarts, releases microphone & WakeLock resources,
     * and transitions state to [WakeWordState.DISABLED].
     */
    fun stopListening() {
        isPausedForSession = false
        _isWakeWordActive.value = false
        _wakeWordState.value = WakeWordState.DISABLED
        LiveVoiceSessionBridge.setWakeWordListening(false)
        restartJob?.cancel()
        restartJob = null
        stopContinuousAudioRecordMonitor()
        releasePartialWakeLock()
        mainHandler.post {
            releaseRecognizerInternal()
        }
        if (testPermissionGrantedOverride == null && !LiveVoiceSessionBridge.isSessionActive.value) {
            LiveVoiceForegroundService.stopServiceIfRunning(context)
        }
    }

    /**
     * Pauses the wake-word microphone during an active Live Voice session or command capture
     * so there is no microphone conflict, then resumes once the session ends.
     */
    fun pauseForActiveSession() {
        isPausedForSession = true
        restartJob?.cancel()
        restartJob = null
        stopContinuousAudioRecordMonitor()
        if (Looper.myLooper() == Looper.getMainLooper()) {
            releaseRecognizerInternal()
        } else {
            mainHandler.post {
                releaseRecognizerInternal()
            }
        }
        if (_isWakeWordActive.value) {
            _wakeWordState.value = WakeWordState.PROCESSING
        }
    }

    fun resumeAfterSessionIfEnabled() {
        isPausedForSession = false
        if (_isWakeWordActive.value && hasMicrophonePermission() && !LiveVoiceSessionBridge.isSessionActive.value) {
            _wakeWordState.value = WakeWordState.LISTENING
            acquirePartialWakeLock()
            LiveVoiceSessionBridge.setWakeWordListening(true)
            if (testPermissionGrantedOverride == null) {
                LiveVoiceForegroundService.startServiceIfNeeded(context)
            }
            startContinuousAudioRecordMonitor()
        }
    }

    private fun acquirePartialWakeLock() {
        if (testPermissionGrantedOverride != null) return
        try {
            if (partialWakeLock?.isHeld == true) return
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            partialWakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "JARVIS:WakeWordEngineWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to acquire WakeWord PARTIAL_WAKE_LOCK: ${e.message}")
        }
    }

    private fun releasePartialWakeLock() {
        try {
            if (partialWakeLock?.isHeld == true) {
                partialWakeLock?.release()
            }
        } catch (_: Exception) {
        } finally {
            partialWakeLock = null
        }
    }

    /**
     * Prompts the user to whitelist JARVIS from Android Doze / Battery Optimizations so the
     * 24/7 wake-word microphone loop is never killed when the screen is off.
     */
    @SuppressLint("BatteryLife")
    fun requestBatteryOptimizationExemptionIfNeeded(activityOrAppContext: Context = context) {
        if (testPermissionGrantedOverride != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
                if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activityOrAppContext.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to launch battery optimization exemption dialog: ${e.message}")
            }
        }
    }

    /**
     * Wakes up the phone screen if locked/off and brings JARVIS (`MainActivity`) to the foreground
     * over the lock screen to start the Live Voice Call immediately.
     */
    fun wakeDeviceScreenAndBringJarvisToFront() {
        if (testPermissionGrantedOverride != null) return
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            @Suppress("DEPRECATION")
            val screenWakeLock = pm?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "JARVIS:ScreenWakeupLock"
            )
            screenWakeLock?.setReferenceCounted(false)
            screenWakeLock?.acquire(10_000L)

            val wakeIntent = Intent(context.applicationContext, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                putExtra(EXTRA_WAKE_WORD_TRIGGERED, true)
            }
            context.applicationContext.startActivity(wakeIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed waking screen on wake word: ${e.message}")
        }
    }

    /**
     * Continuous 24/7 Screen-On & Screen-Off Wake Word Monitor:
     * - Prioritizes continuous on-device `SpeechRecognizer` (`EXTRA_PREFER_OFFLINE = true` with automatic
     *   online fallback) even when the phone screen is OFF (`PARTIAL_WAKE_LOCK` held) so exact phrases
     *   `["hey jarvis", "jarvis", "suno jarvis", "हे जार्विस", "सुनो जार्विस"]` are recognized deterministically
     *   without relying on fake peak-counting heuristics.
     * - Maintains a 16kHz `AudioRecord` human-voice VAD gate that immediately hands off to `SpeechRecognizer`
     *   whenever voice activity is detected after recognizer idle periods.
     */
    @SuppressLint("MissingPermission")
    private fun startContinuousAudioRecordMonitor() {
        // In unit/Robolectric tests with overrides, use direct recognizer path
        if (testPermissionGrantedOverride != null || testRecognitionAvailableOverride != null) {
            mainHandler.post { initAndStartRecognizer() }
            return
        }

        // If SpeechRecognizer is supported, start continuous recognition immediately (works both Screen-ON & Screen-OFF with PARTIAL_WAKE_LOCK)
        if (isSpeechRecognizerSupported() && consecutiveRecognizerFailures < 5) {
            mainHandler.post { initAndStartRecognizer() }
            return
        }

        audioRecordVadJob?.cancel()
        audioRecordVadJob = scope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) {
                mainHandler.post { initAndStartRecognizer() }
                return@launch
            }

            while (isActive && _isWakeWordActive.value && !isPausedForSession && !LiveVoiceSessionBridge.isSessionActive.value && _wakeWordState.value == WakeWordState.LISTENING) {
                if (isStage2RecognizerRunning) {
                    delay(120L)
                    continue
                }

                var recorder: AudioRecord? = null
                try {
                    recorder = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        SAMPLE_RATE_HZ,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        maxOf(minBuf * 2, FRAME_SAMPLES * 8)
                    )
                    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                        recorder.release()
                        recorder = null
                        mainHandler.post { initAndStartRecognizer() }
                        delay(1500L)
                        continue
                    }

                    audioRecord = recorder
                    recorder.startRecording()
                    Log.d(TAG, "16kHz PCM AudioRecord VAD gate active (waiting for voice to hand off to SpeechRecognizer)")

                    val frameBuffer = ShortArray(FRAME_SAMPLES)
                    var consecutiveSpeechFrames = 0
                    var adaptiveNoiseFloorDb = -48.0

                    while (isActive && _isWakeWordActive.value && !isPausedForSession && !LiveVoiceSessionBridge.isSessionActive.value && !isStage2RecognizerRunning) {
                        val read = recorder.read(frameBuffer, 0, FRAME_SAMPLES)
                        if (read <= 0) {
                            delay(25L)
                            continue
                        }

                        val rmsDb = calculateRmsDb(frameBuffer, read)
                        val zcr = calculateZeroCrossingRate(frameBuffer, read)

                        if (rmsDb < adaptiveNoiseFloorDb + 6.0) {
                            adaptiveNoiseFloorDb = (adaptiveNoiseFloorDb * 0.96) + (rmsDb * 0.04)
                        }
                        val dynamicThresholdDb = maxOf(VAD_VOICE_DB_THRESHOLD, adaptiveNoiseFloorDb + 9.5)
                        val isHumanVoiceFrame = rmsDb >= dynamicThresholdDb && zcr in 0.02..0.48

                        if (isHumanVoiceFrame) {
                            consecutiveSpeechFrames++
                            // Hand off to SpeechRecognizer regardless of screen state (screen ON or OFF)
                            if (consecutiveSpeechFrames >= 2 && isSpeechRecognizerSupported()) {
                                Log.d(TAG, "16kHz VAD detected speech (rms=${rmsDb.toInt()}dB). Starting SpeechRecognizer for exact wake phrase verification.")
                                consecutiveRecognizerFailures = 0
                                isStage2RecognizerRunning = true
                                releaseAudioRecordInternal()
                                mainHandler.post { initAndStartRecognizer() }
                                break
                            }
                        } else {
                            consecutiveSpeechFrames = 0
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "AudioRecord VAD monitor error: ${e.message}")
                    delay(1000L)
                } finally {
                    releaseAudioRecordInternal()
                }
            }
        }
    }

    private fun stopContinuousAudioRecordMonitor() {
        audioRecordVadJob?.cancel()
        audioRecordVadJob = null
        releaseAudioRecordInternal()
    }

    private fun releaseAudioRecordInternal() {
        val rec = audioRecord ?: return
        audioRecord = null
        try {
            if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                rec.stop()
            }
        } catch (_: Exception) {
        }
        try {
            rec.release()
        } catch (_: Exception) {
        }
    }

    internal fun calculateRmsDb(buffer: ShortArray, length: Int): Double {
        if (length <= 0) return -90.0
        var sumSquares = 0.0
        for (i in 0 until length) {
            val normalized = buffer[i] / 32768.0
            sumSquares += normalized * normalized
        }
        val rms = sqrt(sumSquares / length).coerceAtLeast(1e-5)
        return 20.0 * log10(rms)
    }

    internal fun calculateZeroCrossingRate(buffer: ShortArray, length: Int): Double {
        if (length <= 1) return 0.0
        var crossings = 0
        for (i in 1 until length) {
            if ((buffer[i - 1] >= 0 && buffer[i] < 0) || (buffer[i - 1] < 0 && buffer[i] >= 0)) {
                crossings++
            }
        }
        return crossings.toDouble() / (length - 1).toDouble()
    }

    internal fun isJarvisAcousticCadence(
        durationMs: Int,
        syllablePeakCount: Int,
        sibilantTailFrames: Int
    ): Boolean {
        return durationMs in 400..1650 &&
            syllablePeakCount in 2..4 &&
            sibilantTailFrames >= 1
    }

    /**
     * Evaluates a sequence of 40ms 16kHz PCM frames using Stage-1B Acoustic Syllabic & Sibilant
     * envelope matching ("Jar-vis" / "Hey Jar-vis").
     */
    internal fun evaluateAcousticWakePattern(frames: List<ShortArray>): Boolean {
        var syllablePeakCount = 0
        var wasInPeak = false
        var sibilantTailFrames = 0
        var burstTotalSpeechFrames = 0
        val dynamicThresholdDb = VAD_VOICE_DB_THRESHOLD

        for (frame in frames) {
            if (frame.isEmpty()) continue
            val rmsDb = calculateRmsDb(frame, frame.size)
            val zcr = calculateZeroCrossingRate(frame, frame.size)
            val isHumanVoiceFrame = rmsDb >= dynamicThresholdDb && zcr in 0.02..0.48

            if (isHumanVoiceFrame) {
                burstTotalSpeechFrames++
                val isStrongVowelPeak = rmsDb >= (dynamicThresholdDb + 4.5) && zcr in 0.03..0.24
                if (isStrongVowelPeak && !wasInPeak) {
                    syllablePeakCount++
                    wasInPeak = true
                } else if (rmsDb < dynamicThresholdDb + 2.0) {
                    wasInPeak = false
                }

                if (zcr in 0.17..0.46 && syllablePeakCount >= 1) {
                    sibilantTailFrames++
                }
            } else {
                wasInPeak = false
            }
        }

        val durationMs = burstTotalSpeechFrames * 40
        return isJarvisAcousticCadence(durationMs, syllablePeakCount, sibilantTailFrames)
    }

    private fun releaseRecognizerInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        isStage2RecognizerRunning = false
    }

    private fun initAndStartRecognizer() {
        if (!_isWakeWordActive.value || isPausedForSession || LiveVoiceSessionBridge.isSessionActive.value) return
        if (!hasMicrophonePermission()) {
            _isWakeWordActive.value = false
            _wakeWordState.value = WakeWordState.ERROR
            return
        }

        // Ensure Stage-1 AudioRecord has released the mic before Stage-2 SpeechRecognizer starts
        releaseAudioRecordInternal()
        isStage2RecognizerRunning = true

        try {
            if (speechRecognizer == null) {
                if (!isSpeechRecognizerSupported()) {
                    Log.w(TAG, "SpeechRecognizer not available on this device; staying in Stage-1 AudioRecord mode")
                    isStage2RecognizerRunning = false
                    return
                }
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            if (_isWakeWordActive.value && !isPausedForSession && !LiveVoiceSessionBridge.isSessionActive.value) {
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
                                LiveVoiceSessionBridge.setWakeWordListening(false)
                                return
                            }

                            // If offline language pack is missing, toggle preferOfflineMode so online works seamlessly
                            if (preferOfflineMode && (
                                    error == SpeechRecognizer.ERROR_NETWORK ||
                                        error == SpeechRecognizer.ERROR_CLIENT ||
                                        error == SpeechRecognizer.ERROR_SERVER ||
                                        error == 12 || // ERROR_LANGUAGE_NOT_SUPPORTED
                                        error == 13    // ERROR_LANGUAGE_UNAVAILABLE
                                )
                            ) {
                                preferOfflineMode = false
                            } else {
                                consecutiveRecognizerFailures++
                            }

                            releaseRecognizerInternal()
                            if (_isWakeWordActive.value && !isPausedForSession && !LiveVoiceSessionBridge.isSessionActive.value) {
                                scheduleRestart()
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            consecutiveRecognizerFailures = 0
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            processHypotheses(matches)
                            releaseRecognizerInternal()
                            if (_isWakeWordActive.value && !isPausedForSession && !LiveVoiceSessionBridge.isSessionActive.value && _wakeWordState.value == WakeWordState.LISTENING) {
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

            val useOffline = preferOfflineMode || !isNetworkConnected()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, useOffline)
                }
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Wake word recognizer start error: ${e.message}")
            isStage2RecognizerRunning = false
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
            Log.d(TAG, "Speech hypothesis received: '$clean' (norm: '$norm')")

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

                // Release both AudioRecord and SpeechRecognizer mic immediately before handing off
                stopContinuousAudioRecordMonitor()
                mainHandler.post {
                    releaseRecognizerInternal()
                }

                // Wake device screen & bring JARVIS over lock screen if screen was off
                wakeDeviceScreenAndBringJarvisToFront()

                _wakeWordState.value = WakeWordState.PROCESSING
                if (!inlineCommand.isNullOrBlank()) {
                    followUpCommandWindowUntilMs = 0L
                    onInlineCommandDetectedListener?.invoke(inlineCommand)
                } else if (!isNetworkConnected()) {
                    // Open 15s offline follow-up command window so user can speak command next without repeating "Jarvis"
                    openOfflineFollowUpCommandWindow(15_000L)
                }
                val listener = onWakeWordDetectedListener
                if (listener != null) {
                    listener.invoke(clean)
                } else {
                    if (testPermissionGrantedOverride == null) {
                        JarvisTtsManager.speak(context, WAKE_WORD_WELCOME_GREETING)
                    }
                    LiveVoiceSessionBridge.triggerWakeSession()
                }
                return
            }

            // If we are inside the 15-second Offline Follow-Up Command Window after "Hey Jarvis",
            // treat any meaningful spoken command directly as an offline phone command!
            if (isOfflineFollowUpWindowActive() && norm.length >= 3) {
                followUpCommandWindowUntilMs = 0L
                _lastDetectedPhrase.value = clean
                _wakeWordState.value = WakeWordState.PROCESSING
                Log.i(TAG, "Offline follow-up command detected after wake word: '$clean'")
                onInlineCommandDetectedListener?.invoke(clean)
                if (_isWakeWordActive.value) {
                    _wakeWordState.value = WakeWordState.LISTENING
                }
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
                Regex("(?i)^.*?(?:hey\\s+jarvis\\s+listen|hey\\s+jarvis|okay\\s+jarvis|ok\\s+jarvis|o\\s+jarvis|hi\\s+jarvis|suno\\s+jarvis|hello\\s+jarvis|jarvis|हे\\s+जार्विस|सुनो\\s+जार्विस|जार्विस)[,!.:\\s]*"),
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
        if (isPausedForSession || LiveVoiceSessionBridge.isSessionActive.value) return
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(RESTART_DELAY_MS)
            if (_isWakeWordActive.value && !isPausedForSession && !LiveVoiceSessionBridge.isSessionActive.value && _wakeWordState.value == WakeWordState.LISTENING) {
                startContinuousAudioRecordMonitor()
            }
        }
    }
}
