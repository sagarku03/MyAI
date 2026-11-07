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
    SINGLE_WAVE("Single Air Wave", "WAVE // NEXT", "Skipped media track / acknowledged alert via mid-air wave"),
    DOUBLE_WAVE("Double Air Wave", "2X-WAVE // VISION", "Triggered instant live screen vision scan via double air wave"),
    PALM_HOVER("Palm Hover Hold", "HOVER // TOGGLE", "Toggled media play/pause & voice state via 1s palm hover")
}

data class ProximityAirGestureState(
    val isEnabled: Boolean = false,
    val isSensorAvailable: Boolean = true,
    val isHandNear: Boolean = false,
    val lastGesture: AirGestureType = AirGestureType.NONE,
    val lastGestureTimestampMs: Long = 0L,
    val totalGesturesDetected: Int = 0,
    val lastActionSummaryHinglish: String = "Zero-Touch Air Gestures ready (Wave / Double Wave / Palm Hover)"
)

/**
 * AbuZar-Ansarii/Jarvis (PrivateAgent) Zero-Touch Optical Proximity Air Gesture Engine:
 * Uses Android's hardware `Sensor.TYPE_PROXIMITY` to detect mid-air hand gestures without touching the screen:
 * 1. Quick Wave (< 400ms near -> far): Skip music track / dismiss alert
 * 2. Double Wave (2 waves within 750ms): Instant Live Screen Vision Capture & Analysis
 * 3. Palm Hover (Hold over sensor >= 900ms): Play/Pause media or toggle listening
 */
object ProximityAirGestureManager : SensorEventListener {
    private const val TAG = "ProximityAirGesture"
    private const val HOVER_THRESHOLD_MS = 900L
    private const val DOUBLE_WAVE_WINDOW_MS = 750L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var appContext: Context? = null
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var hoverJob: Job? = null
    private var singleWaveCommitJob: Job? = null

    private var nearStartTimestampMs: Long = 0L
    private var lastWaveTimestampMs: Long = 0L
    private var hoverTriggeredInCurrentCycle: Boolean = false

    var onDoubleWaveVisionScanRequested: (() -> Unit)? = null
    var onPalmHoverToggleRequested: (() -> Unit)? = null

    private val _state = MutableStateFlow(ProximityAirGestureState())
    val state: StateFlow<ProximityAirGestureState> = _state.asStateFlow()

    fun setEnabled(context: Context, enabled: Boolean): String {
        appContext = context.applicationContext
        val sm = appContext?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorManager = sm
        proximitySensor = sm?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        if (!enabled) {
            try {
                sm?.unregisterListener(this)
            } catch (_: Exception) {
            }
            hoverJob?.cancel()
            singleWaveCommitJob?.cancel()
            _state.value = _state.value.copy(
                isEnabled = false,
                isHandNear = false,
                lastActionSummaryHinglish = "Zero-Touch Optical Proximity Air Gestures OFF kar diye gaye hain."
            )
            return _state.value.lastActionSummaryHinglish
        }

        val sensor = proximitySensor
        val registered = if (sensor != null && sm != null) {
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            false
        }

        val summary = if (registered) {
            "Zero-Touch Air Gestures ON ho gaye hain! Sensor ke upar Single Wave (Next Track), Double Wave (Live Screen Vision Scan), ya Palm Hover (Play/Pause) karein."
        } else {
            "Zero-Touch Air Gestures Virtual Mode mein ON hain (Simulation buttons active)."
        }

        _state.value = _state.value.copy(
            isEnabled = true,
            isSensorAvailable = sensor != null,
            lastActionSummaryHinglish = summary
        )
        return summary
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_PROXIMITY) return
        val maxRange = event.sensor.maximumRange.coerceAtLeast(5f)
        val distanceCm = event.values.firstOrNull() ?: maxRange
        val isNear = distanceCm < maxRange && distanceCm < 5.0f
        handleProximityTransition(isNear)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun handleProximityTransition(isNear: Boolean) {
        val now = System.currentTimeMillis()
        val prevNear = _state.value.isHandNear
        if (isNear == prevNear) return

        _state.value = _state.value.copy(isHandNear = isNear)

        if (isNear) {
            nearStartTimestampMs = now
            hoverTriggeredInCurrentCycle = false
            hoverJob?.cancel()
            hoverJob = scope.launch {
                delay(HOVER_THRESHOLD_MS)
                if (_state.value.isHandNear && !hoverTriggeredInCurrentCycle) {
                    hoverTriggeredInCurrentCycle = true
                    singleWaveCommitJob?.cancel()
                    executeGestureAction(AirGestureType.PALM_HOVER)
                }
            }
        } else {
            hoverJob?.cancel()
            val heldDuration = now - nearStartTimestampMs
            if (!hoverTriggeredInCurrentCycle && heldDuration in 30..850) {
                val timeSinceLastWave = now - lastWaveTimestampMs
                if (timeSinceLastWave <= DOUBLE_WAVE_WINDOW_MS) {
                    singleWaveCommitJob?.cancel()
                    lastWaveTimestampMs = 0L
                    executeGestureAction(AirGestureType.DOUBLE_WAVE)
                } else {
                    lastWaveTimestampMs = now
                    singleWaveCommitJob?.cancel()
                    singleWaveCommitJob = scope.launch {
                        delay(DOUBLE_WAVE_WINDOW_MS)
                        executeGestureAction(AirGestureType.SINGLE_WAVE)
                    }
                }
            }
        }
    }

    /**
     * Allows direct execution or testing of any Air Gesture (Single Wave, Double Wave, Palm Hover).
     */
    fun triggerGestureManually(context: Context, gestureType: AirGestureType): String {
        appContext = context.applicationContext
        executeGestureAction(gestureType)
        return _state.value.lastActionSummaryHinglish
    }

    private fun executeGestureAction(gestureType: AirGestureType) {
        val ctx = appContext ?: return
        triggerHapticPulse(ctx)

        val summary = when (gestureType) {
            AirGestureType.SINGLE_WAVE -> {
                dispatchMediaKeyEvent(ctx, KeyEvent.KEYCODE_MEDIA_NEXT)
                "Air Gesture [Single Wave]: Media Next Track / Alert Dismissed!"
            }
            AirGestureType.DOUBLE_WAVE -> {
                onDoubleWaveVisionScanRequested?.invoke()
                val snap = JarvisAccessibilityService.getScreenContext(ctx)
                val appLabel = snap.activePackageName.ifBlank { "Current Screen" }
                "Air Gesture [Double Wave]: Instant Live Screen Vision Scan triggered on $appLabel (${snap.elements.size} UI nodes captured)!"
            }
            AirGestureType.PALM_HOVER -> {
                dispatchMediaKeyEvent(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                onPalmHoverToggleRequested?.invoke()
                "Air Gesture [Palm Hover 1s]: Media Play/Pause & Voice Toggle executed!"
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

    private fun dispatchMediaKeyEvent(context: Context, keyCode: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (e: Exception) {
            Log.w(TAG, "Media key dispatch error: ${e.message}")
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
}
