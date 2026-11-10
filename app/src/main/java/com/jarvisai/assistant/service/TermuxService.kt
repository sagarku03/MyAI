package com.jarvisai.assistant.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.jarvisai.assistant.MainActivity
import com.jarvisai.assistant.terminal.LocalTerminalBridge

open class TermuxService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val CHANNEL_ID = "jarvis_terminal_service"
        private const val NOTIFICATION_ID = 1337

        const val ACTION_WAKE_LOCK = "com.termux.service_wake_lock"
        const val ACTION_WAKE_UNLOCK = "com.termux.service_wake_unlock"
        const val ACTION_STOP_SERVICE = "com.termux.service_stop"

        fun start(context: Context) {
            val intent = Intent(context, TermuxService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TermuxService::class.java).setAction(ACTION_STOP_SERVICE)
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        LocalTerminalBridge.instance.init(applicationContext)
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_WAKE_LOCK -> acquireWakeLock()
            ACTION_WAKE_UNLOCK -> releaseWakeLock()
            ACTION_STOP_SERVICE -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JarvisAI:TerminalWakeLock")
            wakeLock?.acquire(10 * 60 * 1000L /* 10 minutes max per lock */)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jarvis Terminal Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps Linux shell and AI daemons active" }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis Linux Subsystem Active")
            .setContentText("PTY shell and terminal engine running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
