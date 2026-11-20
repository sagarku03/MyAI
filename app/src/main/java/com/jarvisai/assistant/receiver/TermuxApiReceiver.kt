package com.jarvisai.assistant.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.R
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.Socket
import java.util.Locale

class TermuxApiReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TermuxApiReceiver"
        private const val API_NOTIFICATION_CHANNEL_ID = "termux_api_notifications"

        /**
         * Canonical Termux:API method names supported directly inside Agentra without needing
         * an external Termux:API companion app.
         */
        val BUILT_IN_METHODS = listOf(
            "BatteryStatus",
            "Clipboard",
            "Notification",
            "NotificationRemove",
            "Toast",
            "Torch",
            "TtsSpeak",
            "Vibrate",
            "Volume"
        )
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val apiMethod = intent.getStringExtra("api_method")
            ?: intent.getStringExtra("method")
            ?: intent.action?.substringAfterLast('.')
            ?: return

        when (apiMethod.lowercase(Locale.US)) {
            "toast" -> {
                val text = intent.getStringExtra("text")
                    ?: intent.getStringExtra("message")
                    ?: intent.getStringExtra("content")
                    ?: return
                val longDuration = intent.getBooleanExtra("short", true).not()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context.applicationContext,
                        text,
                        if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                    ).show()
                }
            }

            "vibrate" -> {
                val duration = intent.getIntExtra("duration_ms", intent.getIntExtra("duration", 500)).toLong().coerceIn(20L, 10_000L)
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                }
            }

            "clipboard", "clipboard-set" -> handleClipboard(context, intent, forceSet = apiMethod.equals("clipboard-set", ignoreCase = true))

            "clipboard-get" -> {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clipText = cm?.primaryClip?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                resultData = clipText
                sendSocketResponse(intent, clipText)
            }

            "torch" -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
                val cameraId = runCatching { cm.cameraIdList.firstOrNull() }.getOrNull() ?: return
                try {
                    cm.setTorchMode(cameraId, enabled)
                } catch (e: Exception) {
                    Log.w(TAG, "Torch toggle failed: ${e.message}")
                }
            }

            "tts", "tts-speak", "ttsspeak" -> {
                val text = intent.getStringExtra("text")
                    ?: intent.getStringExtra("message")
                    ?: return
                var tts: TextToSpeech? = null
                tts = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        tts?.language = Locale.US
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agentra_termux_tts")
                    }
                }
            }

            "notification" -> handleNotification(context, intent)

            "notificationremove" -> handleNotificationRemove(context, intent)

            "volume" -> handleVolume(context, intent)

            "battery-status", "batterystatus" -> handleBatteryStatus(context, intent)
        }
    }

    private fun handleClipboard(context: Context, intent: Intent, forceSet: Boolean = false) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val setMode = forceSet || intent.getBooleanExtra("set", false)
        if (setMode) {
            val text = intent.getStringExtra("text")
                ?: intent.getStringExtra("message")
                ?: intent.getStringExtra("content")
                ?: ""
            cm.setPrimaryClip(ClipData.newPlainText("Agentra", text))
        } else {
            val clipText = cm.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            resultData = clipText
            sendSocketResponse(intent, clipText)
        }
    }

    private fun handleNotification(context: Context, intent: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val id = intent.getIntExtra("id", 4242)
        val title = intent.getStringExtra("title") ?: "Agentra Terminal"
        val content = intent.getStringExtra("content") ?: intent.getStringExtra("text") ?: ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                API_NOTIFICATION_CHANNEL_ID,
                "Termux API Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, API_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.notify(id, notification)
    }

    private fun handleNotificationRemove(context: Context, intent: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val id = intent.getIntExtra("id", 4242)
        nm.cancel(id)
    }

    private fun handleVolume(context: Context, intent: Intent) {
        val streamName = intent.getStringExtra("stream")?.lowercase(Locale.US) ?: "music"
        val volume = intent.getIntExtra("volume", -1)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val streamType = when (streamName) {
            "alarm" -> AudioManager.STREAM_ALARM
            "ring" -> AudioManager.STREAM_RING
            "notification" -> AudioManager.STREAM_NOTIFICATION
            "voice_call", "call" -> AudioManager.STREAM_VOICE_CALL
            "system" -> AudioManager.STREAM_SYSTEM
            else -> AudioManager.STREAM_MUSIC
        }
        if (volume >= 0) {
            val maxVol = audioManager.getStreamMaxVolume(streamType)
            audioManager.setStreamVolume(streamType, volume.coerceIn(0, maxVol), AudioManager.FLAG_SHOW_UI)
        }
    }

    private fun handleBatteryStatus(context: Context, intent: Intent) {
        try {
            val batteryStatus = context.applicationContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val temp = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
            val json = JSONObject().apply {
                put("percentage", pct)
                put("is_charging", isCharging)
                put("plugged", if (plugged != 0) "PLUGGED" else "UNPLUGGED")
                put("temperature", temp)
            }
            val data = json.toString()
            resultData = data
            sendSocketResponse(intent, data)
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading battery status: ${e.message}")
        }
    }

    private fun sendSocketResponse(intent: Intent, data: String) {
        val socketOutput = intent.getStringExtra("socket_output")
            ?: intent.getStringExtra("output")
            ?: intent.getStringExtra("result_file")
            ?: return
        try {
            if (socketOutput.startsWith("/")) {
                val file = File(socketOutput)
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { it.write(data.toByteArray(Charsets.UTF_8)) }
            } else if (socketOutput.contains(":")) {
                val host = socketOutput.substringBefore(":")
                val port = socketOutput.substringAfter(":").toIntOrNull() ?: return
                Socket(host, port).use { sock ->
                    sock.getOutputStream().write(data.toByteArray(Charsets.UTF_8))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed writing Termux API response to $socketOutput: ${e.message}")
        }
    }
}
