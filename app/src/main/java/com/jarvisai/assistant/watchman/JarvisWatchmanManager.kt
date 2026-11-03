package com.jarvisai.assistant.watchman

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
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

data class WatchmanCaptureResult(
    val success: Boolean,
    val photoPath: String? = null,
    val telegramDispatched: Boolean = false,
    val message: String
)

class JarvisWatchmanManager(private val context: Context) {

    private val httpClient = OkHttpClient()

    suspend fun captureIntruderSnapshot(
        telegramBotToken: String? = null,
        telegramChatId: String? = null,
        caption: String = "JARVIS Watchman Alert: Stranger detected tampering with phone"
    ): WatchmanCaptureResult = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val photoFile = try {
            captureFrontCameraSnapshotInternal(timestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Failed front camera capture: ${e.message}")
            null
        }

        val photoPath = photoFile?.absolutePath
        var telegramSent = false

        if (photoFile != null && !telegramBotToken.isNullOrBlank() && !telegramChatId.isNullOrBlank()) {
            telegramSent = sendPhotoToTelegram(photoFile, telegramBotToken, telegramChatId, "$caption at $timestamp")
        }

        postWatchmanNotification(photoFile, timestamp)

        WatchmanCaptureResult(
            success = photoFile != null,
            photoPath = photoPath,
            telegramDispatched = telegramSent,
            message = if (photoFile != null) {
                "Intruder photo captured successfully at $timestamp." + (if (telegramSent) " Dispatched to Telegram." else " Saved locally.")
            } else {
                "Unable to access front camera silently."
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
            .setContentTitle("JARVIS Watchman Security Alert")
            .setContentText("Intruder snapshot captured at $timestamp")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap))
        }

        notificationManager.notify(WATCHMAN_NOTIFICATION_ID, builder.build())
    }

    companion object {
        private const val TAG = "JarvisWatchman"
        const val WATCHMAN_NOTIFICATION_ID = 8821
    }
}
