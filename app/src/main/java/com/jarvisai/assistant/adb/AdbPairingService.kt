package com.jarvisai.assistant.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat

/**
 * Foreground service (`specialUse`) that posts a persistent high-priority notification with a
 * [RemoteInput] inline reply field so the user can enter the 6-digit Wireless Debugging pairing
 * code directly from the notification shade without leaving Android Developer Options.
 */
class AdbPairingService : Service() {

    companion object {
        const val ACTION_START = "com.jarvisai.assistant.adb.START_PAIRING"
        const val ACTION_STOP = "com.jarvisai.assistant.adb.STOP_PAIRING"
        const val ACTION_CODE = "com.jarvisai.assistant.adb.PAIRING_CODE"
        const val ACTION_UPDATE_STATUS = "com.jarvisai.assistant.adb.UPDATE_STATUS"
        const val EXTRA_STATUS_TITLE = "extra_status_title"
        const val EXTRA_STATUS_TEXT = "extra_status_text"
        const val EXTRA_SHOW_INPUT = "extra_show_input"
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

        fun updateNotification(
            context: Context,
            title: String,
            text: String,
            showInput: Boolean = true
        ) {
            val intent = Intent(context, AdbPairingService::class.java).apply {
                action = ACTION_UPDATE_STATUS
                putExtra(EXTRA_STATUS_TITLE, title)
                putExtra(EXTRA_STATUS_TEXT, text)
                putExtra(EXTRA_SHOW_INPUT, showInput)
            }
            runCatching { context.startService(intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AdbPairingService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AdbConnectManager.init(this)
        createNotificationChannel()

        when (intent?.action) {
            ACTION_STOP -> {
                AdbConnectManager.stopPairing()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_CODE -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val rawCode = results?.getCharSequence(KEY_PAIRING_CODE)?.toString()?.trim().orEmpty()
                val digitsOnly = rawCode.filter { it.isDigit() }
                if (digitsOnly.isNotEmpty()) {
                    promoteToForeground(
                        buildPairingNotification(
                            title = "Pairing Wireless ADB...",
                            text = "Submitting code $digitsOnly to 127.0.0.1...",
                            showInput = false
                        )
                    )
                    AdbConnectManager.submitPairingCode(digitsOnly)
                } else {
                    promoteToForeground(
                        buildPairingNotification(
                            title = "Invalid Pairing Code",
                            text = "Please enter the 6-digit numeric pairing code from Developer Options.",
                            showInput = true
                        )
                    )
                }
                return START_NOT_STICKY
            }

            ACTION_UPDATE_STATUS -> {
                val title = intent.getStringExtra(EXTRA_STATUS_TITLE) ?: "Wireless Debugging Detected"
                val text = intent.getStringExtra(EXTRA_STATUS_TEXT)
                    ?: "Type the 6-digit pairing code from Developer Options below"
                val showInput = intent.getBooleanExtra(EXTRA_SHOW_INPUT, true)
                promoteToForeground(buildPairingNotification(title, text, showInput))
                return START_NOT_STICKY
            }

            else -> {
                promoteToForeground(
                    buildPairingNotification(
                        title = "Searching for Wireless Debugging...",
                        text = "Open Developer Options -> Wireless debugging -> Pair device with pairing code",
                        showInput = true
                    )
                )
                return START_NOT_STICKY
            }
        }
    }

    private fun promoteToForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopForegroundCompat()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wireless ADB Pairing",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Allows entering the 6-digit Wireless Debugging pairing code directly from the notification shade"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildPairingNotification(
        title: String,
        text: String,
        showInput: Boolean
    ): Notification {
        val stopIntent = Intent(this, AdbPairingService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)

        if (showInput) {
            val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE)
                .setLabel("Enter 6-digit code")
                .build()

            val replyIntent = Intent(this, AdbPairingService::class.java).setAction(ACTION_CODE)
            val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val replyPendingIntent = PendingIntent.getService(
                this,
                1,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
            )

            val replyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_input_add,
                "Enter 6-digit code",
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(false)
                .build()

            builder.addAction(replyAction)
        }

        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", stopPendingIntent)
        return builder.build()
    }
}
