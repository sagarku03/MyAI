package com.jarvisai.assistant.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput

class AdbPairingService : Service() {

    companion object {
        const val ACTION_START = "com.jarvisai.assistant.adb.START_PAIRING"
        const val ACTION_STOP = "com.jarvisai.assistant.adb.STOP_PAIRING"
        const val ACTION_CODE = "com.jarvisai.assistant.adb.PAIRING_CODE"
        const val KEY_PAIRING_CODE = "pairing_code"
        const val CHANNEL_ID = "jarvis_adb_pairing"
        const val NOTIFICATION_ID = 4310

        fun start(context: Context) {
            val intent = Intent(context, AdbPairingService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AdbPairingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AdbConnectManager.init(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildPairingNotification())

        when (intent?.action) {
            ACTION_STOP -> {
                AdbConnectManager.stopPairing()
                stopSelf()
            }
            ACTION_CODE -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val code = results?.getCharSequence(KEY_PAIRING_CODE)?.toString()
                if (!code.isNullOrBlank()) {
                    AdbConnectManager.submitPairingCode(code.trim())
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        @Suppress("DEPRECATION")
        stopForeground(true)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wireless ADB Pairing",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Displays Wireless Debugging pairing code input box" }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildPairingNotification(): Notification {
        val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE)
            .setLabel("Enter 6-digit code")
            .build()

        val replyIntent = Intent(this, AdbPairingService::class.java).setAction(ACTION_CODE)
        val replyPendingIntent = PendingIntent.getService(
            this,
            1,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_add,
            "Pair Now",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val stopIntent = Intent(this, AdbPairingService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wireless Debugging Detected")
            .setContentText("Type the 6-digit pairing code from Developer Options below")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .addAction(replyAction)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
}
