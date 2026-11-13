package com.jarvisai.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.jarvisai.assistant.terminal.LocalServerManager
import com.jarvisai.assistant.terminal.LocalTerminalManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

open class TermuxService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var currentSessionCount: Int = 0
    private val supervisorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var hubSupervisorJob: Job? = null

    companion object {
        private const val TAG = "TermuxService"
        private const val CHANNEL_ID = "jarvis_termux_service_channel"
        private const val NOTIFICATION_ID = 2026

        const val ACTION_WAKE_LOCK = "com.jarvisai.assistant.service_wake_lock"
        const val ACTION_WAKE_UNLOCK = "com.jarvisai.assistant.service_wake_unlock"
        const val ACTION_STOP_SERVICE = "com.jarvisai.assistant.service_stop"

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
        runStartForeground()
        actionAcquireWakeLock()
        startHubSupervisorLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE, "com.termux.service_stop" -> {
                Log.d(TAG, "ACTION_STOP_SERVICE received: shutting down background supervisor")
                actionStopService()
                return START_NOT_STICKY
            }
            ACTION_WAKE_LOCK, "com.termux.service_wake_lock" -> actionAcquireWakeLock()
            ACTION_WAKE_UNLOCK, "com.termux.service_wake_unlock" -> actionReleaseWakeLock()
            else -> {
                runStartForeground()
                if (wakeLock == null) {
                    actionAcquireWakeLock()
                }
                if (hubSupervisorJob?.isActive != true) {
                    startHubSupervisorLoop()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hubSupervisorJob?.cancel()
        supervisorScope.cancel()
        actionReleaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    private fun startHubSupervisorLoop() {
        hubSupervisorJob?.cancel()
        hubSupervisorJob = supervisorScope.launch {
            while (isActive) {
                try {
                    if (!LocalServerManager.isAgyHubReachable()) {
                        Log.i(TAG, "Supervisor detected Antigravity Hub offline on port 2235; resurrecting daemon...")
                        LocalServerManager.ensureAgyHubAvailable(applicationContext)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Hub supervisor check notice: ${e.message}")
                }
                delay(15_000L)
            }
        }
    }

    private fun runStartForeground() {
        setupNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Jarvis AI Background Engine"
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                description = "Background daemon supervision and terminal services"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val wakeLockHeld = wakeLock != null
        val sessionText = "$currentSessionCount " + if (currentSessionCount == 1) "session" else "sessions"
        val notificationText = if (wakeLockHeld) "$sessionText (wake lock active)" else sessionText

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Exit/Stop action
        val exitIntent = Intent(this, TermuxService::class.java).setAction(ACTION_STOP_SERVICE)
        val exitPendingIntent = PendingIntent.getService(
            this, 1, exitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // WakeLock toggle action
        val wakeAction = if (wakeLockHeld) ACTION_WAKE_UNLOCK else ACTION_WAKE_LOCK
        val toggleWakeLockIntent = Intent(this, TermuxService::class.java).setAction(wakeAction)
        val wakePendingIntent = PendingIntent.getService(
            this, 2, toggleWakeLockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val wakeActionTitle = if (wakeLockHeld) "Release wakelock" else "Acquire wakelock"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis AI")
            .setContentText(notificationText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Exit", exitPendingIntent)
            .addAction(android.R.drawable.ic_lock_lock, wakeActionTitle, wakePendingIntent)
            .build()
    }

    private fun actionAcquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JarvisAI:TermuxServiceWakeLock")
            wakeLock?.acquire(24 * 60 * 60 * 1000L)
            updateNotification()
        }
    }

    private fun actionReleaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        updateNotification()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun actionStopService() {
        hubSupervisorJob?.cancel()
        actionReleaseWakeLock()
        runCatching { LocalServerManager.stopServer() }
        stopSelf()
    }
}
