package com.jarvisai.assistant.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.ImageFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.AudioManager
import android.media.ImageReader
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.jarvisai.assistant.model.ProceduralHudMode
import com.jarvisai.assistant.ui.components.ProceduralHudModeState
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class AccelerometerSentryState(
    val isArmed: Boolean = false,
    val isSettling: Boolean = false,
    val isCalibrated: Boolean = false,
    val baselineX: Float = 0f,
    val baselineY: Float = 0f,
    val baselineZ: Float = 0f,
    val lastDeltaMs2: Float = 0f,
    val breachCount: Int = 0,
    val lastBreachTimestampMs: Long = 0L,
    val lastCapturedPhotoPath: String? = null,
    val lastGpsCoordinates: String = "",
    val lastBatteryPercent: Int = 0,
    val lastTelegramDispatched: Boolean = false,
    val statusSummary: String = "Sentry Watchdog Standby (Disarmed)"
)

/**
 * Stark Hardware Sensor Fusion Module #1:
 * `AccelerometerSentryManager` — Anti-Theft Table Watchdog (`Sensor.TYPE_ACCELEROMETER`).
 *
 * Architecture & Behavior:
 * 1. Arming & 3-Second Table Settling Window:
 *    - When armed, waits 3,000ms (`SETTLING_DELAY_MS = 3000L`) for the device to rest flat on a table.
 *    - Captures the baseline gravity vector `(x0, y0, z0)`.
 * 2. Euclidean Vector Delta Trigger:
 *    - Computes `delta = sqrt((x - x0)^2 + (y - y0)^2 + (z - z0)^2)`.
 *    - Triggers breach protocol immediately when `delta > 1.8f` (`1.8 m/s²`).
 * 3. Autonomous Breach Response:
 *    a) Silent front-camera JPEG capture via Android Camera2 API.
 *    b) Booms commanding vocal security alert:
 *       `"Security violation detected. Spatial coordinates and facial telemetry logged."`
 *    c) Dispatches intruder photo + battery % + GPS coordinates to the user's Telegram Bot
 *       via `ClawbotTelegramGateway.dispatchIntruderAlert(...)`.
 * 4. Lifecycle Safety:
 *    - Unregisters `SensorEventListener` and cancels coroutine jobs on `disarm()` / `onDestroy()`
 *      to guarantee zero background battery drain when disarmed.
 */
object AccelerometerSentryManager : SensorEventListener {

    private const val TAG = "AccelerometerSentryMgr"
    const val SETTLING_DELAY_MS = 3_000L
    const val BREACH_DELTA_THRESHOLD_MS2 = 1.8f
    private const val BREACH_COOLDOWN_MS = 6_000L
    const val VOCAL_SECURITY_ALERT =
        "Security violation detected. Spatial coordinates and facial telemetry logged."

    private var supervisorJob = SupervisorJob()
    private var scope = CoroutineScope(supervisorJob + Dispatchers.Main)
    private var settleCountdownJob: Job? = null
    private var breachExecutionJob: Job? = null

    private var appContext: Context? = null
    private var sensorManager: SensorManager? = null
    private var accelerometerSensor: Sensor? = null

    @Volatile
    private var armedTimestampMs: Long = 0L

    @Volatile
    private var x0: Float = 0f

    @Volatile
    private var y0: Float = 0f

    @Volatile
    private var z0: Float = 0f

    @Volatile
    private var baselineRecorded: Boolean = false

    private val _state = MutableStateFlow(AccelerometerSentryState())
    val state: StateFlow<AccelerometerSentryState> = _state.asStateFlow()

    /**
     * Arms or disarms the Anti-Theft Table Watchdog.
     */
    fun setArmed(context: Context, armed: Boolean): String {
        return if (armed) {
            arm(context)
        } else {
            disarm()
        }
    }

    /**
     * Arms the accelerometer watchdog, starts the 3-second table-settling window,
     * and registers the `Sensor.TYPE_ACCELEROMETER` listener.
     */
    fun arm(context: Context): String {
        val ctx = context.applicationContext
        appContext = ctx
        ensureScopeActive()

        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorManager = sm
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometerSensor = sensor

        try {
            sm?.unregisterListener(this)
        } catch (_: Exception) {
        }

        baselineRecorded = false
        x0 = 0f
        y0 = 0f
        z0 = 0f
        armedTimestampMs = System.currentTimeMillis()

        val registered = if (sm != null && sensor != null) {
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            false
        }

        ProceduralHudModeState.setMode(ProceduralHudMode.SENTRY_MATRIX)

        val initialStatus = if (registered) {
            "Sentry Mode ARMED: Waiting 3 seconds for phone to settle on table before locking baseline gravity vector (x0, y0, z0)."
        } else {
            "Sentry Mode ARMED (Virtual Sensor Mode): Waiting 3 seconds for baseline lock."
        }

        _state.value = _state.value.copy(
            isArmed = true,
            isSettling = true,
            isCalibrated = false,
            lastDeltaMs2 = 0f,
            statusSummary = initialStatus
        )

        settleCountdownJob?.cancel()
        settleCountdownJob = scope.launch {
            delay(SETTLING_DELAY_MS)
            if (_state.value.isArmed && !baselineRecorded) {
                // If no sensor event has arrived yet (e.g., on headless JVM/emulator), lock standard gravity (0, 0, 9.81)
                x0 = 0f
                y0 = 0f
                z0 = SensorManager.STANDARD_GRAVITY
                baselineRecorded = true
                _state.value = _state.value.copy(
                    isSettling = false,
                    isCalibrated = true,
                    baselineX = x0,
                    baselineY = y0,
                    baselineZ = z0,
                    statusSummary = "Sentry Watchdog LOCKED on baseline (0.00, 0.00, 9.81) m/s². Threshold > 1.8 m/s²."
                )
            }
        }

        return initialStatus
    }

    /**
     * Disarms the watchdog and unregisters the accelerometer listener to prevent battery drain.
     */
    fun disarm(): String {
        settleCountdownJob?.cancel()
        settleCountdownJob = null
        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering accelerometer listener: ${e.localizedMessage}")
        }
        baselineRecorded = false
        ProceduralHudModeState.setMode(ProceduralHudMode.ARC_DEFAULT)

        val summary = "Sentry Mode Disarmed. Accelerometer sensor unregistered."
        _state.value = _state.value.copy(
            isArmed = false,
            isSettling = false,
            isCalibrated = false,
            statusSummary = summary
        )
        return summary
    }

    /**
     * Clean lifecycle teardown to prevent sensor and coroutine leaks on service/activity destroy.
     */
    fun onDestroy() {
        disarm()
        breachExecutionJob?.cancel()
        breachExecutionJob = null
        try {
            supervisorJob.cancel()
        } catch (_: Exception) {
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val currentEvent = event ?: return
        if (!_state.value.isArmed || currentEvent.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = currentEvent.values.getOrElse(0) { 0f }
        val y = currentEvent.values.getOrElse(1) { 0f }
        val z = currentEvent.values.getOrElse(2) { 0f }
        onAccelerometerSample(x, y, z)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /**
     * Processes an accelerometer sample `(x, y, z)` in m/s².
     * - Ignores samples during the initial 3-second settling window.
     * - Records `(x0, y0, z0)` on the first post-settle sample.
     * - Triggers the breach response when `sqrt((x-x0)^2 + (y-y0)^2 + (z-z0)^2) > 1.8f`.
     */
    fun onAccelerometerSample(x: Float, y: Float, z: Float) {
        if (!_state.value.isArmed) return
        val now = System.currentTimeMillis()

        // 1. Wait 3 seconds for phone to settle on table
        if (now - armedTimestampMs < SETTLING_DELAY_MS) {
            return
        }

        // 2. Record baseline gravity vector (x0, y0, z0)
        if (!baselineRecorded) {
            x0 = x
            y0 = y
            z0 = z
            baselineRecorded = true
            settleCountdownJob?.cancel()
            _state.value = _state.value.copy(
                isSettling = false,
                isCalibrated = true,
                baselineX = x0,
                baselineY = y0,
                baselineZ = z0,
                statusSummary = String.format(
                    Locale.US,
                    "Sentry Watchdog LOCKED on baseline (%.2f, %.2f, %.2f) m/s². Threshold > %.1f m/s².",
                    x0,
                    y0,
                    z0,
                    BREACH_DELTA_THRESHOLD_MS2
                )
            )
            Log.i(TAG, "Baseline gravity vector locked: x0=$x0, y0=$y0, z0=$z0")
            return
        }

        // 3. Calculate Euclidean motion delta: sqrt((x-x0)^2 + (y-y0)^2 + (z-z0)^2)
        val dx = x - x0
        val dy = y - y0
        val dz = z - z0
        val delta = sqrt(dx * dx + dy * dy + dz * dz)

        _state.value = _state.value.copy(lastDeltaMs2 = delta)

        // 4. Trigger breach protocol if delta > 1.8 m/s^2
        if (delta > BREACH_DELTA_THRESHOLD_MS2 && (now - _state.value.lastBreachTimestampMs) >= BREACH_COOLDOWN_MS) {
            val ctx = appContext ?: return
            triggerSecurityBreach(ctx, delta)
        }
    }

    /**
     * Executes the 3-stage Stark Sentry Breach Response:
     * a) Silent front-camera capture via Camera2 API.
     * b) Boom commanding vocal security alert:
     *    "Security violation detected. Spatial coordinates and facial telemetry logged."
     * c) Dispatch intruder photo + battery % + GPS coordinates to user's Telegram Bot via `ClawbotTelegramGateway`.
     */
    fun triggerSecurityBreach(context: Context, deltaMs2: Float = 2.4f) {
        val ctx = context.applicationContext
        appContext = ctx
        ensureScopeActive()

        val now = System.currentTimeMillis()
        ProceduralHudModeState.setMode(ProceduralHudMode.RED_ALERT)

        val updatedBreachCount = _state.value.breachCount + 1
        _state.value = _state.value.copy(
            breachCount = updatedBreachCount,
            lastDeltaMs2 = deltaMs2,
            lastBreachTimestampMs = now,
            statusSummary = String.format(
                Locale.US,
                "🚨 SECURITY VIOLATION DETECTED (Δa = %.2f m/s² > %.1f m/s²)! Logging facial telemetry & spatial coordinates...",
                deltaMs2,
                BREACH_DELTA_THRESHOLD_MS2
            )
        )

        // Stage B: Boom commanding vocal security alert immediately
        boomVocalSecurityAlert(ctx)

        // Stage A & C: Capture silent front-camera photo + read battery % & GPS + dispatch to Telegram
        breachExecutionJob?.cancel()
        breachExecutionJob = scope.launch(Dispatchers.IO) {
            val batteryPct = readCurrentBatteryPercent(ctx)
            val (lat, lon, locLabel) = AdsBAirspaceRadarManager.resolveOperatorCoordinates(ctx)
            val coordsText = String.format(Locale.US, "%.5f, %.5f (%s)", lat, lon, locLabel)

            val photoFile = captureSilentFrontCameraPhoto(ctx)

            val telegramSent = ClawbotTelegramGateway.dispatchIntruderAlert(
                context = ctx,
                photoFile = photoFile,
                batteryPercent = batteryPct,
                latitude = lat,
                longitude = lon,
                locationLabel = locLabel,
                deltaAccelerationMs2 = deltaMs2
            )

            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    lastCapturedPhotoPath = photoFile?.absolutePath,
                    lastGpsCoordinates = coordsText,
                    lastBatteryPercent = batteryPct,
                    lastTelegramDispatched = telegramSent,
                    statusSummary = buildString {
                        append("🚨 Security Violation Logged (Δa=")
                        append(String.format(Locale.US, "%.2f m/s²", deltaMs2))
                        append(") | Battery: ").append(batteryPct).append("%")
                        append(" | GPS: ").append(coordsText)
                        if (photoFile != null) append(" | Photo: ").append(photoFile.name)
                        if (telegramSent) append(" | Dispatched to Telegram Bot")
                    }
                )
            }
        }
    }

    private fun boomVocalSecurityAlert(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let { am ->
                val maxMusic = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val targetVol = (maxMusic * 0.90f).toInt().coerceAtLeast(1)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
            }
            ToneGenerator(AudioManager.STREAM_ALARM, 95)
                .startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 550)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to boost alarm volume/tone: ${e.localizedMessage}")
        }

        try {
            EdgeTtsManager.getInstance(context).speak(
                text = VOCAL_SECURITY_ALERT,
                voiceName = EdgeTtsManager.DEFAULT_VOICE_FULL_NAME
            )
        } catch (e: Exception) {
            Log.w(TAG, "EdgeTtsManager vocal alert fallback: ${e.localizedMessage}")
            JarvisTtsManager.speak(context, VOCAL_SECURITY_ALERT)
        }
    }

    /**
     * Captures a silent front-facing JPEG snapshot using Android's Camera2 `ImageReader` surface
     * without requiring a visible preview window.
     */
    suspend fun captureSilentFrontCameraPhoto(context: Context): File? =
        withTimeoutOrNull(4_500L) {
            suspendCancellableCoroutine { continuation ->
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                if (cameraManager == null) {
                    if (continuation.isActive) continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                val handlerThread = HandlerThread("StarkSentryCameraThread").apply { start() }
                val backgroundHandler = Handler(handlerThread.looper)
                var imageReader: ImageReader? = null
                var openedCamera: CameraDevice? = null
                var resumed = false

                fun cleanupAndResume(file: File?) {
                    synchronized(handlerThread) {
                        if (resumed) return
                        resumed = true
                    }
                    try {
                        openedCamera?.close()
                    } catch (_: Exception) {
                    }
                    try {
                        imageReader?.close()
                    } catch (_: Exception) {
                    }
                    try {
                        handlerThread.quitSafely()
                    } catch (_: Exception) {
                    }
                    if (continuation.isActive) {
                        continuation.resume(file)
                    }
                }

                try {
                    val frontCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                        val chars = cameraManager.getCameraCharacteristics(id)
                        chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
                    } ?: cameraManager.cameraIdList.firstOrNull()

                    if (frontCameraId == null) {
                        cleanupAndResume(null)
                        return@suspendCancellableCoroutine
                    }

                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val outputDir = File(context.filesDir, "sentry_snapshots").apply {
                        if (!exists()) mkdirs()
                    }
                    val targetFile = File(outputDir, "sentry_intruder_$timestamp.jpg")

                    val reader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2)
                    imageReader = reader

                    reader.setOnImageAvailableListener({ ir ->
                        try {
                            val image = ir.acquireLatestImage()
                            if (image != null) {
                                val buffer = image.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                image.close()
                                FileOutputStream(targetFile).use { fos ->
                                    fos.write(bytes)
                                    fos.flush()
                                }
                                cleanupAndResume(targetFile)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving front camera JPEG: ${e.localizedMessage}")
                            cleanupAndResume(null)
                        }
                    }, backgroundHandler)

                    cameraManager.openCamera(
                        frontCameraId,
                        object : CameraDevice.StateCallback() {
                            override fun onOpened(camera: CameraDevice) {
                                openedCamera = camera
                                try {
                                    @Suppress("DEPRECATION")
                                    camera.createCaptureSession(
                                        listOf(reader.surface),
                                        object : CameraCaptureSession.StateCallback() {
                                            override fun onConfigured(session: CameraCaptureSession) {
                                                try {
                                                    val captureRequest = camera.createCaptureRequest(
                                                        CameraDevice.TEMPLATE_STILL_CAPTURE
                                                    ).apply {
                                                        addTarget(reader.surface)
                                                        set(
                                                            CaptureRequest.CONTROL_AF_MODE,
                                                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                                        )
                                                        set(
                                                            CaptureRequest.CONTROL_AE_MODE,
                                                            CaptureRequest.CONTROL_AE_MODE_ON
                                                        )
                                                    }.build()
                                                    session.capture(captureRequest, null, backgroundHandler)
                                                } catch (e: Exception) {
                                                    cleanupAndResume(null)
                                                }
                                            }

                                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                                cleanupAndResume(null)
                                            }
                                        },
                                        backgroundHandler
                                    )
                                } catch (e: Exception) {
                                    cleanupAndResume(null)
                                }
                            }

                            override fun onDisconnected(camera: CameraDevice) {
                                cleanupAndResume(null)
                            }

                            override fun onError(camera: CameraDevice, error: Int) {
                                cleanupAndResume(null)
                            }
                        },
                        backgroundHandler
                    )
                } catch (se: SecurityException) {
                    Log.w(TAG, "Camera permission not granted for silent sentry capture: ${se.localizedMessage}")
                    cleanupAndResume(null)
                } catch (e: Exception) {
                    Log.w(TAG, "Silent front camera capture failed: ${e.localizedMessage}")
                    cleanupAndResume(null)
                }

                continuation.invokeOnCancellation {
                    cleanupAndResume(null)
                }
            }
        }

    private fun readCurrentBatteryPercent(context: Context): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val prop = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (prop in 1..100) {
                prop
            } else {
                val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else 100
            }
        } catch (_: Exception) {
            100
        }
    }

    private fun ensureScopeActive() {
        if (!supervisorJob.isActive) {
            supervisorJob = SupervisorJob()
            scope = CoroutineScope(supervisorJob + Dispatchers.Main)
        }
    }
}
