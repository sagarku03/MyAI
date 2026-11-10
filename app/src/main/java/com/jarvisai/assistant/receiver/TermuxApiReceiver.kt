package com.jarvisai.assistant.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.*
import android.hardware.camera2.CameraManager
import android.os.*
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.util.Locale

class TermuxApiReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val apiMethod = intent.getStringExtra("api_method")
            ?: intent.getStringExtra("method")
            ?: intent.action?.substringAfterLast('.')
            ?: return

        when (apiMethod.lowercase()) {
            "toast" -> {
                val text = intent.getStringExtra("text") ?: intent.getStringExtra("message") ?: return
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
            }
            "vibrate" -> {
                val duration = intent.getIntExtra("duration_ms", 500).toLong()
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(duration)
                }
            }
            "clipboard", "clipboard-set" -> {
                val text = intent.getStringExtra("text") ?: return
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Termux", text))
            }
            "clipboard-get" -> {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipText = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                resultData = clipText
            }
            "torch" -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = cm.cameraIdList.firstOrNull() ?: return
                try {
                    cm.setTorchMode(cameraId, enabled)
                } catch (_: Exception) {}
            }
            "tts", "tts-speak" -> {
                val text = intent.getStringExtra("text") ?: return
                var tts: TextToSpeech? = null
                tts = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        tts?.language = Locale.US
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "termux_tts")
                    }
                }
            }
            "notification" -> {
                val title = intent.getStringExtra("title") ?: "Termux"
                val content = intent.getStringExtra("content") ?: intent.getStringExtra("text") ?: return
                val channelId = "termux_api_notifications"
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        channelId,
                        "Termux API Notifications",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                    nm.createNotificationChannel(channel)
                }
                val notification = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setAutoCancel(true)
                    .build()
                nm.notify(intent.getIntExtra("id", 2048), notification)
            }
            "battery-status" -> {
                val batteryStatus = context.applicationContext.registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
                val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
                val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
                val temp = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
                val json = JSONObject().apply {
                    put("percentage", pct)
                    put("plugged", if (plugged != 0) "PLUGGED" else "UNPLUGGED")
                    put("temperature", temp)
                }
                resultData = json.toString()
            }
        }
    }
}
