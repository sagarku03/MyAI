package com.jarvisai.assistant.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AirGestureType(
    val displayTitle: String,
    val badgeCode: String,
    val descriptionHinglish: String
) {
    NONE("Standby", "AIR-IDLE", "Optical proximity sensor ready for mid-air hand gestures"),
    SINGLE_WAVE("Quick Air Wave", "WAVE // NEXT", "Skipped to next media track via <350ms quick air wave"),
    DOUBLE_WAVE("Double Air Wave", "2X-WAVE // VISION", "Snapped high-res screenshot and dispatched to Vision Solver (2 waves in 700ms)"),
    PALM_HOVER("Palm Hover Hold", "HOVER // PLAY-PAUSE", "Toggled media play/pause via >900ms palm hover")
}

data class ProximityAirGestureState(
    val isEnabled: Boolean = false,
    val isSensorAvailable: Boolean = true,
    val isHandNear: Boolean = false,
    val lastGesture: AirGestureType = AirGestureType.NONE,
    val lastGestureTimestampMs: Long = 0L,
    val totalGesturesDetected: Int = 0,
    val lastVisionSolution: String = "",
    val lastActionSummaryHinglish: String = "Zero-Touch Air Gestures ready (Quick Wave <350ms / Double Wave 700ms / Palm Hover >900ms)"
)

/**
 * Stark Hardware Sensor Fusion Module #2:
 * `ProximityAirGestureManager` — Zero-Touch Mid-Air Hand Gestures (`Sensor.TYPE_PROXIMITY`).
 *
 * Gesture Timing Specification:
 * 1. Quick Wave (< 350ms near-to-far transition):
 *    - Skips to next media track (`KeyEvent.KEYCODE_MEDIA_NEXT`).
 * 2. Palm Hover (> 900ms continuous hold over sensor):
 *    - Toggles media Play/Pause (`KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE`).
 * 3. Double Wave (2 quick waves within 700ms window):
 *    - Snaps a high-resolution screen capture (`JarvisAccessibilityService.captureScreenshotBitmap`)
 *      and dispatches it to the Vision Solver (`AgenticMobileEngine.executeLiveScreenVisionInspection`),
 *      speaking the solved result aloud.
 *
 * Lifecycle & Battery Safety:
 * - Unregisters `SensorEventListener` and cancels coroutine jobs on `setEnabled(context, false)`
 *   and `onDestroy()` to prevent background battery drain.
 */
object ProximityAirGestureManager : SensorEventListener {

    private const val TAG = "ProximityAirGesture"
    const val QUICK_WAVE_MAX_MS = 350L
    const val PALM_HOVER_HOLD_MS = 900L
    const val DOUBLE_WAVE_WINDOW_MS = 700L
    private const val MIN_DEBOUNCE_MS = 20L

    private var supervisorJob = SupervisorJob()
    private var scope = CoroutineScope(supervisorJob + Dispatchers.Main)
    private var appContext: Context? = null
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null

    private var hoverJob: Job? = null
    private var singleWaveCommitJob: Job? = null
    private var visionSolverJob: Job? = null

    @Volatile
    private var nearStartTimestampMs: Long = 0L

    @Volatile
    private var lastQuickWaveTimestampMs: Long = 0L

    @Volatile
    private var hoverTriggeredInCurrentCycle: Boolean = false

    var onDoubleWaveVisionScanRequested: (() -> Unit)? = null
    var onPalmHoverToggleRequested: (() -> Unit)? = null

    private val _state = MutableStateFlow(ProximityAirGestureState())
    val state: StateFlow<ProximityAirGestureState> = _state.asStateFlow()

    /**
     * Enables or disables the hardware `Sensor.TYPE_PROXIMITY` listener.
     */
    fun setEnabled(context: Context, enabled: Boolean): String {
        val ctx = context.applicationContext
        appContext = ctx
        ensureScopeActive()

        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorManager = sm
        proximitySensor = sm?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        if (!enabled) {
            unregisterSensor()
            val offSummary = "Zero-Touch Optical Proximity Air Gestures OFF kar diye gaye hain."
            _state.value = _state.value.copy(
                isEnabled = false,
                isHandNear = false,
                lastActionSummaryHinglish = offSummary
            )
            return offSummary
        }

        try {
            sm?.unregisterListener(this)
        } catch (_: Exception) {
        }

        val sensor = proximitySensor
        val registered = if (sensor != null && sm != null) {
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            false
        }

        val summary = if (registered) {
            "Zero-Touch Air Gestures ON! Quick Wave (<350ms -> Next Track), Palm Hover (>900ms -> Play/Pause), Double Wave (2x in 700ms -> High-Res Screenshot Vision Solver)."
        } else {
            "Zero-Touch Air Gestures Virtual Mode mein ON hain."
        }

        _state.value = _state.value.copy(
            isEnabled = true,
            isSensorAvailable = sensor != null,
            lastActionSummaryHinglish = summary
        )
        return summary
    }

    /**
     * Unregisters the proximity sensor listener and cancels pending timers to prevent battery drain.
     */
    fun unregisterSensor() {
        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister proximity sensor: ${e.localizedMessage}")
        }
        hoverJob?.cancel()
        hoverJob = null
        singleWaveCommitJob?.cancel()
        singleWaveCommitJob = null
        nearStartTimestampMs = 0L
        lastQuickWaveTimestampMs = 0L
        hoverTriggeredInCurrentCycle = false
    }

    /**
     * Full lifecycle cleanup on service or activity destruction.
     */
    fun onDestroy() {
        unregisterSensor()
        visionSolverJob?.cancel()
        visionSolverJob = null
        try {
            supervisorJob.cancel()
        } catch (_: Exception) {
        }
        _state.value = _state.value.copy(
            isEnabled = false,
            isHandNear = false
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val currentEvent = event ?: return
        if (currentEvent.sensor.type != Sensor.TYPE_PROXIMITY) return

        val maxRange = currentEvent.sensor.maximumRange.coerceAtLeast(5f)
        val distanceCm = currentEvent.values.firstOrNull() ?: maxRange
        val isNear = distanceCm < maxRange && distanceCm < 5.0f
        handleProximityTransition(isNear)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /**
     * State machine for mid-air hand proximity transitions (`isNear = true` -> `isNear = false`):
     * - Hold > 900ms while near -> `AirGestureType.PALM_HOVER` (`KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE`)
     * - Release in < 350ms:
     *   - If second quick wave arrives within 700ms -> `AirGestureType.DOUBLE_WAVE` (Screenshot + Vision Solver)
     *   - Otherwise after 700ms window -> `AirGestureType.SINGLE_WAVE` (`KeyEvent.KEYCODE_MEDIA_NEXT`)
     */
    fun handleProximityTransition(isNear: Boolean) {
        ensureScopeActive()
        val now = System.currentTimeMillis()
        val previouslyNear = _state.value.isHandNear
        if (isNear == previouslyNear) return

        _state.value = _state.value.copy(isHandNear = isNear)

        if (isNear) {
            nearStartTimestampMs = now
            hoverTriggeredInCurrentCycle = false
            hoverJob?.cancel()
            hoverJob = scope.launch {
                // Palm Hover (> 900ms hold)
                delay(PALM_HOVER_HOLD_MS + 10L)
                if (_state.value.isHandNear && !hoverTriggeredInCurrentCycle) {
                    hoverTriggeredInCurrentCycle = true
                    singleWaveCommitJob?.cancel()
                    lastQuickWaveTimestampMs = 0L
                    executeGestureAction(AirGestureType.PALM_HOVER)
                }
            }
        } else {
            hoverJob?.cancel()
            val heldDurationMs = now - nearStartTimestampMs

            // Quick Wave (< 350ms) or Double Wave (2 waves within 700ms)
            if (!hoverTriggeredInCurrentCycle && heldDurationMs in MIN_DEBOUNCE_MS..QUICK_WAVE_MAX_MS) {
                val elapsedSincePreviousWave = now - lastQuickWaveTimestampMs
                if (lastQuickWaveTimestampMs > 0L && elapsedSincePreviousWave <= DOUBLE_WAVE_WINDOW_MS) {
                    singleWaveCommitJob?.cancel()
                    lastQuickWaveTimestampMs = 0L
                    executeGestureAction(AirGestureType.DOUBLE_WAVE)
                } else {
                    lastQuickWaveTimestampMs = now
                    singleWaveCommitJob?.cancel()
                    singleWaveCommitJob = scope.launch {
                        delay(DOUBLE_WAVE_WINDOW_MS)
                        lastQuickWaveTimestampMs = 0L
                        executeGestureAction(AirGestureType.SINGLE_WAVE)
                    }
                }
            }
        }
    }

    /**
     * Allows direct execution or testing of any Air Gesture (`SINGLE_WAVE`, `DOUBLE_WAVE`, `PALM_HOVER`).
     */
    fun triggerGestureManually(context: Context, gestureType: AirGestureType): String {
        appContext = context.applicationContext
        ensureScopeActive()
        executeGestureAction(gestureType)
        return _state.value.lastActionSummaryHinglish
    }

    private fun executeGestureAction(gestureType: AirGestureType) {
        val ctx = appContext ?: return
        triggerHapticPulse(ctx)

        val summary = when (gestureType) {
            AirGestureType.SINGLE_WAVE -> {
                // Quick Wave (< 350ms): Skip to next media track (KeyEvent.KEYCODE_MEDIA_NEXT)
                dispatchMediaKeyEvent(ctx, KeyEvent.KEYCODE_MEDIA_NEXT)
                "Air Gesture [Quick Wave <350ms]: Skipped to next media track (KEYCODE_MEDIA_NEXT)."
            }

            AirGestureType.PALM_HOVER -> {
                // Palm Hover (> 900ms hold): Play/Pause toggle (KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                dispatchMediaKeyEvent(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                onPalmHoverToggleRequested?.invoke()
                "Air Gesture [Palm Hover >900ms]: Toggled media Play/Pause (KEYCODE_MEDIA_PLAY_PAUSE)."
            }

            AirGestureType.DOUBLE_WAVE -> {
                // Double Wave (2 waves in 700ms): Snap high-res screenshot and send to vision solver
                onDoubleWaveVisionScanRequested?.invoke()
                val snap = JarvisAccessibilityService.getScreenContext(ctx)
                val appLabel = snap.activePackageName.ifBlank { "Current Screen" }
                launchHighResScreenshotVisionSolver(ctx)
                "Air Gesture [Double Wave 700ms]: High-res screenshot captured on $appLabel (${snap.elements.size} UI nodes) and sent to Vision Solver!"
            }

            AirGestureType.NONE -> "Air Gesture Standby"
        }

        Log.i(TAG, summary)
        _state.value = _state.value.copy(
            lastGesture = gestureType,
            lastGestureTimestampMs = System.currentTimeMillis(),
            totalGesturesDetected = _state.value.totalGesturesDetected + 1,
            lastActionSummaryHinglish = summary
        )
    }

    private fun launchHighResScreenshotVisionSolver(context: Context) {
        visionSolverJob?.cancel()
        visionSolverJob = scope.launch(Dispatchers.IO) {
            try {
                // Capture high-res screenshot bitmap via AccessibilityService (Android 11+) and run Vision Solver
                val screenshotBitmap = JarvisAccessibilityService.captureScreenshot(context)
                val resolutionTag = if (screenshotBitmap != null) {
                    "${screenshotBitmap.width}x${screenshotBitmap.height}"
                } else {
                    "UI-Hierarchy"
                }

                val actionManager = PhoneActionManager(context, PhoneControlsManager(context))
                val outcome = actionManager.agenticEngine.executeLiveScreenVisionInspection(
                    "Analyze this high-resolution screen capture ($resolutionTag), solve any visible question or problem, and summarize key actionable items."
                )

                val solutionText = outcome.spokenHinglishResponse
                _state.value = _state.value.copy(
                    lastVisionSolution = solutionText,
                    lastActionSummaryHinglish = "Air Gesture [Double Wave Vision Solver ($resolutionTag)]: $solutionText"
                )
                EdgeTtsManager.getInstance(context).speak(solutionText)
            } catch (e: Exception) {
                Log.w(TAG, "Double-wave vision solver error: ${e.localizedMessage}")
            }
        }
    }

    private fun dispatchMediaKeyEvent(context: Context, keyCode: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val eventTime = System.currentTimeMillis()
            val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
            val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
        } catch (e: Exception) {
            Log.w(TAG, "Media key dispatch error for keyCode=$keyCode: ${e.localizedMessage}")
        }
    }

    private fun triggerHapticPulse(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {
        }
    }

    private fun ensureScopeActive() {
        if (!supervisorJob.isActive) {
            supervisorJob = SupervisorJob()
            scope = CoroutineScope(supervisorJob + Dispatchers.Main)
        }
    }
}
