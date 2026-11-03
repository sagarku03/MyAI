package com.jarvisai.assistant.driving

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.jarvisai.assistant.service.JarvisTtsManager

object JarvisDrivingModeManager {

    const val DRIVING_NOTIFICATION_ID = 9012
    private const val CHANNEL_ID = "jarvis_driving_channel"

    @Volatile
    var isDrivingModeActive: Boolean = false

    fun handleBluetoothConnection(context: Context, device: BluetoothDevice?, connected: Boolean) {
        if (device == null) return

        if (connected) {
            val deviceClass = try {
                device.bluetoothClass?.majorDeviceClass
            } catch (_: SecurityException) {
                null
            }

            val isCarOrAudio = deviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
                (device.name?.contains("car", ignoreCase = true) == true) ||
                (device.name?.contains("auto", ignoreCase = true) == true)

            if (isCarOrAudio && !isDrivingModeActive) {
                activateDrivingMode(context, device.name ?: "Bluetooth Car Device")
            }
        } else {
            if (isDrivingModeActive) {
                deactivateDrivingMode(context)
            }
        }
    }

    fun activateDrivingMode(context: Context, deviceName: String = "Car Audio") {
        isDrivingModeActive = true
        createNotificationChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val exitIntent = Intent(context, JarvisDrivingOffReceiver::class.java).apply {
            action = JarvisDrivingOffReceiver.ACTION_DRIVING_OFF
        }
        val exitPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            exitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("JARVIS Driving Mode Active")
            .setContentText("Connected to $deviceName. Hands-free auto-announce active.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Exit Driving Mode", exitPendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(DRIVING_NOTIFICATION_ID, notification)

        JarvisTtsManager.speak(context, "Driving mode activated. Hands-free assistant is listening.")
    }

    fun deactivateDrivingMode(context: Context) {
        isDrivingModeActive = false
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(DRIVING_NOTIFICATION_ID)
        JarvisTtsManager.speak(context, "Driving mode deactivated.")
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Driving Mode",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows active driving mode status and controls"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }
}
