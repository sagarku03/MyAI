package com.jarvisai.assistant.watchman

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.AudioManager
import android.media.ImageReader
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvisai.assistant.model.ProceduralHudMode
import com.jarvisai.assistant.service.AdsBAirspaceRadarManager
import com.jarvisai.assistant.ui.components.ProceduralHudModeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.sqrt

data class WatchmanCaptureResult(
    val success: Boolean,
    val photoPath: String? = null,
    val telegramDispatched: Boolean = false,
    val gpsLocationUrl: String = "",
    val message: String
)

data class SentryModeWatchdogState(
    val isArmed: Boolean = false,
    val isCalibrated: Boolean = false,
    val breachCount: Int = 0,
    val lastDeltaAcceleration: Float = 0f,
    val lastBreachTimestampMs: Long = 0L,
    val lastPhotoPath: String? = null,
    val statusHinglish: String = "Sentry Mode Disarmed (Standby)"
)

class JarvisWatchmanManager(private val context: Context) {

    private val httpClient = OkHttpClient()

    suspend fun captureIntruderSnapshot(
        telegramBotToken: String? = null,
        telegramChatId: String? = null,
        caption: String = "JARVIS Watchman Alert: Stranger detected tampering with phone"
    ): WatchmanCaptureResult = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val (lat, lon, locLabel) = AdsBAirspaceRadarManager.resolveOperatorCoordinates(context)
        val mapsUrl = "https://maps.google.com/?q=$lat,$lon"

        val photoFile = try {
            captureFrontCameraSnapshotInternal(timestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Failed front camera capture: ${e.message}")
            null
        }

        val photoPath = photoFile?.absolutePath
        var telegramSent = false
        val fullCaption = "$caption at $timestamp\n📍 GPS ($locLabel): $mapsUrl"

        if (photoFile != null && !telegramBotToken.isNullOrBlank() && !telegramChatId.isNullOrBlank()) {
            telegramSent = sendPhotoToTelegram(photoFile, telegramBotToken, telegramChatId, fullCaption)
        }

        postWatchmanNotification(photoFile, timestamp)

        WatchmanCaptureResult(
            success = photoFile != null,
            photoPath = photoPath,
            telegramDispatched = telegramSent,
            gpsLocationUrl = mapsUrl,
            message = if (photoFile != null) {
                "Intruder photo + GPS ($locLabel) captured at $timestamp." + (if (telegramSent) " Dispatched to Telegram." else " Saved locally.")
            } else {
                "Sentry alert logged with GPS ($locLabel) at $timestamp."
            }
        )
    }

    private suspend fun captureFrontCameraSnapshotInternal(timestamp: String): File? =
        suspendCancellableCoroutine { continuation ->
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cameraManager == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            try {
                var frontCameraId: String? = null
                for (id in cameraManager.cameraIdList) {
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        frontCameraId = id
                        break
                    }
                }

                val targetId = frontCameraId ?: cameraManager.cameraIdList.firstOrNull()
                if (targetId == null) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                val handlerThread = HandlerThread("WatchmanCamera").apply { start() }
                val backgroundHandler = Handler(handlerThread.looper)

                val imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2)
                val snapshotDir = File(context.filesDir, "watchman_snapshots").apply { mkdirs() }
                val targetFile = File(snapshotDir, "intruder_$timestamp.jpg")

                imageReader.setOnImageAvailableListener({ reader ->
                    try {
                        val image = reader.acquireLatestImage()
                        if (image != null) {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            FileOutputStream(targetFile).use { it.write(bytes) }
                            image.close()
                            handlerThread.quitSafely()
                            if (continuation.isActive) {
                                continuation.resume(targetFile)
                            }
                        }
                    } catch (e: Exception) {
                        handlerThread.quitSafely()
                        if (continuation.isActive) continuation.resume(null)
                    }
                }, backgroundHandler)

                try {
                    cameraManager.openCamera(targetId, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            try {
                                @Suppress("DEPRECATION")
                                camera.createCaptureSession(
                                    listOf(imageReader.surface),
                                    object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                                        override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                                            try {
                                                val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                                    addTarget(imageReader.surface)
                                                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                                }
                                                session.capture(captureBuilder.build(), null, backgroundHandler)
                                            } catch (e: Exception) {
                                                camera.close()
                                                handlerThread.quitSafely()
                                                if (continuation.isActive) continuation.resume(null)
                                            }
                                        }

                                        override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                                            camera.close()
                                            handlerThread.quitSafely()
                                            if (continuation.isActive) continuation.resume(null)
                                        }
                                    },
                                    backgroundHandler
                                )
                            } catch (e: Exception) {
                                camera.close()
                                handlerThread.quitSafely()
                                if (continuation.isActive) continuation.resume(null)
                            }
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            camera.close()
                            handlerThread.quitSafely()
                            if (continuation.isActive) continuation.resume(null)
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            camera.close()
                            handlerThread.quitSafely()
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }, backgroundHandler)
                } catch (e: SecurityException) {
                    handlerThread.quitSafely()
                    if (continuation.isActive) continuation.resume(null)
                }
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private fun sendPhotoToTelegram(
        photoFile: File,
        botToken: String,
        chatId: String,
        caption: String
    ): Boolean {
        return try {
            val url = "https://api.telegram.org/bot$botToken/sendPhoto"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("caption", caption)
                .addFormDataPart(
                    "photo",
                    photoFile.name,
                    photoFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder().url(url).post(requestBody).build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Telegram upload failed: ${e.message}")
            false
        }
    }

    private fun postWatchmanNotification(photoFile: File?, timestamp: String) {
        val channelId = "jarvis_watchman_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "JARVIS Watchman Security",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val bitmap: Bitmap? = photoFile?.let { BitmapFactory.decodeFile(it.absolutePath) }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("JARVIS Sentry / Watchman Security Alert")
            .setContentText("Intruder snapshot & GPS captured at $timestamp")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap))
        }

        notificationManager.notify(WATCHMAN_NOTIFICATION_ID, builder.build())
    }

    companion object : SensorEventListener {
        private const val TAG = "JarvisWatchman"
        const val WATCHMAN_NOTIFICATION_ID = 8821
        private const val MOTION_THRESHOLD_MS2 = 2.2f
        private const val BREACH_COOLDOWN_MS = 6500L

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var appContext: Context? = null
        private var sensorManager: SensorManager? = null
        private var accelSensor: Sensor? = null
        private var deterrenceTts: TextToSpeech? = null

        private var armedAtMs: Long = 0L
        private var baselineX: Float = 0f
        private var baselineY: Float = 0f
        private var baselineZ: Float = 0f
        private var isBaselineCaptured = false

        private val _sentryState = MutableStateFlow(SentryModeWatchdogState())
        val sentryState: StateFlow<SentryModeWatchdogState> = _sentryState.asStateFlow()

        /**
         * AbuZar-Ansarii/Jarvis (PrivateAgent) Armed Accelerometer Anti-Theft Watchdog ("Sentry Mode"):
         * Monitors `Sensor.TYPE_ACCELEROMETER` for unauthorized physical movement/pickup on a desk.
         * When triggered: fires vocal deterrence warning + captures front Camera2 photo + sends GPS & photo to Telegram.
         */
        fun setSentryModeArmed(context: Context, armed: Boolean): String {
            val ctx = context.applicationContext
            appContext = ctx
            val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            sensorManager = sm
            accelSensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

            if (!armed) {
                try {
                    sm?.unregisterListener(this)
                } catch (_: Exception) {
                }
                isBaselineCaptured = false
                ProceduralHudModeState.setMode(ProceduralHudMode.ARC_DEFAULT)
                val msg = "Sentry Mode Disarmed. Accelerometer motion watchdog ab standby par hai."
                _sentryState.value = _sentryState.value.copy(
                    isArmed = false,
                    isCalibrated = false,
                    statusHinglish = msg
                )
                return msg
            }

            if (deterrenceTts == null) {
                deterrenceTts = TextToSpeech(ctx) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        deterrenceTts?.language = Locale.US
                    }
                }
            }

            armedAtMs = System.currentTimeMillis()
            isBaselineCaptured = false
            val sensor = accelSensor
            if (sensor != null && sm != null) {
                sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            }
            ProceduralHudModeState.setMode(ProceduralHudMode.SENTRY_MATRIX)

            val msg = "Sentry Mode ARMED! Phone ko table par rakhein — 2 seconds mein accelerometer lock ho jayega. Koi bhi phone chhuega toh Front Camera photo + Vocal Siren + Telegram GPS alert jayega."
            _sentryState.value = _sentryState.value.copy(
                isArmed = true,
                isCalibrated = false,
                statusHinglish = msg
            )
            return msg
        }

        override fun onSensorChanged(event: SensorEvent?) {
            event ?: return
            if (!_sentryState.value.isArmed || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

            val now = System.currentTimeMillis()
            // Allow 2.0 seconds settling window after arming
            if (now - armedAtMs < 2000L) return

            val ax = event.values.getOrElse(0) { 0f }
            val ay = event.values.getOrElse(1) { 0f }
            val az = event.values.getOrElse(2) { 0f }

            if (!isBaselineCaptured) {
                baselineX = ax
                baselineY = ay
                baselineZ = az
                isBaselineCaptured = true
                _sentryState.value = _sentryState.value.copy(
                    isCalibrated = true,
                    statusHinglish = "Sentry Mode ACTIVE & LOCKED (Monitoring desk accelerometer Δg)"
                )
                return
            }

            val dx = ax - baselineX
            val dy = ay - baselineY
            val dz = az - baselineZ
            val delta = sqrt(dx * dx + dy * dy + dz * dz)

            if (delta >= MOTION_THRESHOLD_MS2 && (now - _sentryState.value.lastBreachTimestampMs) > BREACH_COOLDOWN_MS) {
                val ctx = appContext ?: return
                triggerSentryBreachProtocol(ctx, delta)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        fun triggerSentryBreachProtocol(context: Context, deltaAccel: Float = 3.5f) {
            val now = System.currentTimeMillis()
            ProceduralHudModeState.setMode(ProceduralHudMode.RED_ALERT)

            // 1. Audible Siren Tone + Vocal Deterrence Warning
            try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.let {
                    val maxVol = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    it.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.85f).toInt().coerceAtLeast(1), 0)
                }
                ToneGenerator(AudioManager.STREAM_ALARM, 95).startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
                deterrenceTts?.speak(
                    "Warning! Unauthorized physical handling detected. Biometric snapshot and GPS coordinates transmitted to the operator.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "sentry_deterrence_$now"
                )
            } catch (e: Exception) {
                Log.w(TAG, "Sentry vocal deterrence error: ${e.message}")
            }

            _sentryState.value = _sentryState.value.copy(
                breachCount = _sentryState.value.breachCount + 1,
                lastDeltaAcceleration = deltaAccel,
                lastBreachTimestampMs = now,
                statusHinglish = "🚨 SENTRY BREACH DETECTED (Δa=${String.format(Locale.US, "%.1f", deltaAccel)} m/s²)! Capturing intruder photo & GPS..."
            )

            // 2. Silent Front Camera2 Capture + Telegram Dispatch
            scope.launch {
                val prefs = context.getSharedPreferences("jarvis_watchman_prefs", Context.MODE_PRIVATE)
                val token = prefs.getString("tg_bot_token", null)
                val chatId = prefs.getString("tg_chat_id", null)
                val res = JarvisWatchmanManager(context).captureIntruderSnapshot(
                    telegramBotToken = token,
                    telegramChatId = chatId,
                    caption = "🚨 JARVIS SENTRY MODE BREACH (Motion Δ=${String.format(Locale.US, "%.1f", deltaAccel)} m/s²)"
                )
                _sentryState.value = _sentryState.value.copy(
                    lastPhotoPath = res.photoPath,
                    statusHinglish = "🚨 SENTRY BREACH LOGGED: ${res.message}"
                )
            }
        }
    }
}
