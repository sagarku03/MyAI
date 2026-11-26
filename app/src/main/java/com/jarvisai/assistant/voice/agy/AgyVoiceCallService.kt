package com.jarvisai.assistant.voice.agy

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps Antigravity voice calls running with microphone access while backgrounded or screen-off,
 * displaying an ongoing call notification with Mute / End actions.
 */
class AgyVoiceCallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val started = runCatching {
            val n = notification(AgyVoiceCall.state.value)
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        }
        if (started.isFailure) {
            Log.w("AgyVoiceCall", "call service could not go foreground: ${started.exceptionOrNull()}")
        }
        when (intent?.action) {
            ACTION_MUTE -> AgyVoiceCall.toggleMute()
            ACTION_END -> AgyVoiceCall.end()
        }
        lastStartId = startId
        if (started.isFailure) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (wakeLock == null) {
            runCatching {
                wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "agentra:agy-voice-call")
                    .apply {
                        setReferenceCounted(false)
                        acquire(4 * 60 * 60 * 1000L)
                    }
            }
        }
        job?.cancel()
        job = scope.launch {
            AgyVoiceCall.state
                .map { Triple(it.phase, it.muted, it.agentSpeaking) to it }
                .distinctUntilChanged { a, b -> a.first == b.first }
                .collect { (_, st) ->
                    if (!st.active || st.phase == AgyVoiceCall.Phase.FAILED) {
                        stopSelf(lastStartId)
                        return@collect
                    }
                    runCatching {
                        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(st))
                    }
                }
        }
        VoiceCallTileService.refresh(this)
        runCatching { AgyVoiceBubbleBridge.attach(this) }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Antigravity voice calls", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while an Antigravity voice call is active"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun notification(st: AgyVoiceCall.State): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        fun action(a: String, code: Int) =
            PendingIntent.getService(this, code, Intent(this, AgyVoiceCallService::class.java).setAction(a), flags)
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags
        )
        val text = when (st.phase) {
            AgyVoiceCall.Phase.CONNECTING -> "Connecting…"
            AgyVoiceCall.Phase.RECONNECTING -> "Reconnecting…"
            AgyVoiceCall.Phase.ENDING -> "Ending…"
            else -> when {
                st.muted -> "Muted"
                st.agentSpeaking -> "${st.voice} is speaking"
                else -> "Listening…"
            }
        }
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_voice_call)
            .setContentTitle("${st.title.ifBlank { "Antigravity" }} · Voice call")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(open)
        if (st.connectedAt > 0) {
            b.setUsesChronometer(true)
            b.setWhen(System.currentTimeMillis() - (SystemClock.elapsedRealtime() - st.connectedAt))
        } else {
            b.setShowWhen(false)
        }
        b.addAction(0, if (st.muted) "Unmute" else "Mute", action(ACTION_MUTE, 1))
        b.addAction(0, "End", action(ACTION_END, 2))
        return b.build()
    }

    override fun onDestroy() {
        job?.cancel()
        VoiceCallTileService.refresh(this)
        scope.cancel()
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "agentra_agy_voice_call"
        private const val NOTIF_ID = 7731
        const val ACTION_MUTE = "ai.agentra.agyvoice.MUTE"
        const val ACTION_END = "ai.agentra.agyvoice.END"

        fun start(context: Context) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
            ContextCompat.startForegroundService(context, Intent(context, AgyVoiceCallService::class.java))
        }
    }
}

/** Light hook so service starts the UI bubble without creating a package cycle. */
object AgyVoiceBubbleBridge {
    @Volatile var attachBubble: ((Context) -> Unit)? = null
    @Volatile var openPanelNextCall: (() -> Unit)? = null

    fun attach(context: Context) {
        attachBubble?.invoke(context)
    }

    fun openPanelForNextCall() {
        openPanelNextCall?.invoke()
    }
}
