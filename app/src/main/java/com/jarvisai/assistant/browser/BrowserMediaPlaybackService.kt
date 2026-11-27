package com.jarvisai.assistant.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * Foreground media playback service for the Agentra in-app browser.
 * Keeps WebView audio/video playback alive across screen switches, Home button minimization,
 * and screen-off / lockscreen states, while publishing a rich Android MediaStyle notification
 * with Play/Pause, Previous, Next, and Close controls backed by a native [MediaSession].
 */
class BrowserMediaPlaybackService : Service() {

    companion object {
        private const val TAG = "BrowserMediaService"
        private const val CHANNEL_ID = "agentra_browser_media"
        private const val NOTIFICATION_ID = 4401

        const val ACTION_UPDATE_STATE = "ai.agentra.browser.media.UPDATE_STATE"
        const val ACTION_PLAY_PAUSE = "ai.agentra.browser.media.PLAY_PAUSE"
        const val ACTION_PLAY = "ai.agentra.browser.media.PLAY"
        const val ACTION_PAUSE = "ai.agentra.browser.media.PAUSE"
        const val ACTION_PREV = "ai.agentra.browser.media.PREV"
        const val ACTION_NEXT = "ai.agentra.browser.media.NEXT"
        const val ACTION_STOP = "ai.agentra.browser.media.STOP"

        const val EXTRA_TAB_ID = "extra_tab_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_ARTWORK_URL = "extra_artwork_url"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        const val EXTRA_POSITION_MS = "extra_position_ms"
        const val EXTRA_OPEN_BROWSER_TAB_ID = "extra_open_browser_tab_id"

        private const val PAUSED_IDLE_TIMEOUT_MS = 10L * 60L * 1000L

        @Volatile
        private var isRunning: Boolean = false

        @Volatile
        private var activeInstance: BrowserMediaPlaybackService? = null

        fun update(context: Context, state: BrowserMediaPlaybackState) {
            if (!state.hasActiveMedia) {
                if (isRunning) stop(context)
                return
            }
            // Update the live service instance directly in-process so resuming from notification
            // while on the Home screen / lockscreen never hits Android 14–16 background FGS restrictions.
            val live = activeInstance
            if (live != null && isRunning) {
                live.postStateUpdate(state)
                return
            }

            val appCtx = context.applicationContext
            val intent = Intent(appCtx, BrowserMediaPlaybackService::class.java).apply {
                action = ACTION_UPDATE_STATE
                putExtra(EXTRA_TAB_ID, state.tabId)
                putExtra(EXTRA_TITLE, state.title)
                putExtra(EXTRA_ARTIST, state.artist)
                putExtra(EXTRA_ARTWORK_URL, state.artworkUrl)
                putExtra(EXTRA_IS_PLAYING, state.isPlaying)
                putExtra(EXTRA_DURATION_MS, state.durationMs)
                putExtra(EXTRA_POSITION_MS, state.positionMs)
            }
            try {
                if (state.isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appCtx.startForegroundService(intent)
                } else {
                    appCtx.startService(intent)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Unable to dispatch media state to service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val live = activeInstance
            if (live != null) {
                live.mainHandler.post { live.stopAndDismiss() }
                return
            }
            val appCtx = context.applicationContext
            runCatching {
                appCtx.stopService(Intent(appCtx, BrowserMediaPlaybackService::class.java))
            }
            isRunning = false
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var isForegroundNow: Boolean = false
    private var noisyReceiverRegistered: Boolean = false

    private var currentTabId: String = ""
    private var currentTitle: String = "Agentra Browser"
    private var currentArtist: String = "Media Playback"
    private var currentArtworkUrl: String = ""
    private var currentArtworkBitmap: Bitmap? = null
    private var currentIsPlaying: Boolean = false
    private var currentDurationMs: Long = -1L
    private var currentPositionMs: Long = 0L

    private val pausedIdleTimeoutRunnable = Runnable {
        if (!currentIsPlaying) {
            detachForegroundKeepNotification()
        }
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && currentIsPlaying) {
                BrowserSessionManager.instance.dispatchMediaControl(currentTabId, "pause")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        activeInstance = this
        ensureNotificationChannel()
        initMediaSession()
        registerNoisyReceiver()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun postStateUpdate(state: BrowserMediaPlaybackState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyMediaState(
                tabId = state.tabId,
                title = state.title,
                artist = state.artist,
                artworkUrl = state.artworkUrl,
                isPlaying = state.isPlaying,
                durationMs = state.durationMs,
                positionMs = state.positionMs
            )
        } else {
            mainHandler.post {
                if (isRunning) {
                    applyMediaState(
                        tabId = state.tabId,
                        title = state.title,
                        artist = state.artist,
                        artworkUrl = state.artworkUrl,
                        isPlaying = state.isPlaying,
                        durationMs = state.durationMs,
                        positionMs = state.positionMs
                    )
                }
            }
        }
    }

    private fun applyMediaState(
        tabId: String,
        title: String?,
        artist: String?,
        artworkUrl: String,
        isPlaying: Boolean,
        durationMs: Long,
        positionMs: Long
    ) {
        if (tabId.isNotBlank()) currentTabId = tabId
        if (!title.isNullOrBlank()) currentTitle = title
        if (!artist.isNullOrBlank()) currentArtist = artist
        currentIsPlaying = isPlaying
        currentDurationMs = durationMs
        currentPositionMs = positionMs.coerceAtLeast(0L)

        if (currentIsPlaying) {
            mainHandler.removeCallbacks(pausedIdleTimeoutRunnable)
        } else {
            mainHandler.removeCallbacks(pausedIdleTimeoutRunnable)
            mainHandler.postDelayed(pausedIdleTimeoutRunnable, PAUSED_IDLE_TIMEOUT_MS)
        }

        updatePowerLocks(currentIsPlaying)
        updateMediaSessionState()
        publishNotification()

        if (artworkUrl.isNotBlank() && artworkUrl != currentArtworkUrl) {
            currentArtworkUrl = artworkUrl
            fetchArtworkAsync(artworkUrl)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                if (!currentIsPlaying) {
                    updatePowerLocks(true)
                    mainHandler.removeCallbacks(pausedIdleTimeoutRunnable)
                }
                BrowserSessionManager.instance.dispatchMediaControl(currentTabId, "toggle")
            }
            ACTION_PLAY -> {
                updatePowerLocks(true)
                mainHandler.removeCallbacks(pausedIdleTimeoutRunnable)
                BrowserSessionManager.instance.dispatchMediaControl(currentTabId, "play")
            }
            ACTION_PAUSE -> {
                BrowserSessionManager.instance.dispatchMediaControl(currentTabId, "pause")
            }
            ACTION_PREV -> {
                BrowserSessionManager.instance.dispatchMediaControl(currentTabId, "prev")
            }
            ACTION_NEXT -> {
                BrowserSessionManager.instance.dispatchMediaControl(currentTabId, "next")
            }
            ACTION_STOP -> {
                BrowserSessionManager.instance.dispatchMediaControl(currentTabId, "stop")
                stopAndDismiss()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_STATE, null -> {
                applyMediaState(
                    tabId = intent?.getStringExtra(EXTRA_TAB_ID).orEmpty(),
                    title = intent?.getStringExtra(EXTRA_TITLE),
                    artist = intent?.getStringExtra(EXTRA_ARTIST),
                    artworkUrl = intent?.getStringExtra(EXTRA_ARTWORK_URL).orEmpty(),
                    isPlaying = intent?.getBooleanExtra(EXTRA_IS_PLAYING, currentIsPlaying) ?: currentIsPlaying,
                    durationMs = intent?.getLongExtra(EXTRA_DURATION_MS, currentDurationMs) ?: currentDurationMs,
                    positionMs = intent?.getLongExtra(EXTRA_POSITION_MS, currentPositionMs) ?: currentPositionMs
                )
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep audio running if actively playing in background; otherwise clean up
        if (!currentIsPlaying) {
            stopAndDismiss()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        if (activeInstance === this) activeInstance = null
        mainHandler.removeCallbacksAndMessages(null)
        unregisterNoisyReceiver()
        updatePowerLocks(false)
        runCatching {
            mediaSession?.isActive = false
            mediaSession?.release()
        }
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun stopAndDismiss() {
        currentIsPlaying = false
        mainHandler.removeCallbacks(pausedIdleTimeoutRunnable)
        updatePowerLocks(false)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        isForegroundNow = false
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        }
        stopSelf()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Browser Media Playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Controls for background audio and video playback in Agentra Browser"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(null, null)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun initMediaSession() {
        runCatching {
            mediaSession = MediaSession(this, "AgentraBrowserMediaSession").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        updatePowerLocks(true)
                        mainHandler.removeCallbacks(pausedIdleTimeoutRunnable)
                        BrowserSessionManager.instance.dispatchMediaControl(currentTabId, "play")
                    }

                    override fun onPause() {
                        BrowserSessionManager.instance.dispatchMediaControl(currentTabId, "pause")
                    }

                    override fun onSkipToNext() {
                        BrowserSessionManager.instance.dispatchMediaControl(currentTabId, "next")
                    }

                    override fun onSkipToPrevious() {
                        BrowserSessionManager.instance.dispatchMediaControl(currentTabId, "prev")
                    }

                    override fun onSeekTo(pos: Long) {
                        BrowserSessionManager.instance.dispatchMediaControl(currentTabId, "seek", pos)
                    }

                    override fun onStop() {
                        BrowserSessionManager.instance.dispatchMediaControl(currentTabId, "stop")
                        stopAndDismiss()
                    }
                })
                isActive = true
            }
        }.onFailure { Log.w(TAG, "MediaSession init error: ${it.message}") }
    }

    private fun updateMediaSessionState() {
        val session = mediaSession ?: return
        runCatching {
            val actions = PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_STOP

            val playbackState = PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (currentIsPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    currentPositionMs.coerceAtLeast(0L),
                    if (currentIsPlaying) 1.0f else 0.0f,
                    SystemClock.elapsedRealtime()
                )
                .build()
            session.setPlaybackState(playbackState)

            val metaBuilder = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, currentArtist)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, currentTitle)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, currentArtist)
            if (currentDurationMs > 0L) {
                metaBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, currentDurationMs)
            }
            currentArtworkBitmap?.let { bmp ->
                metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bmp)
                metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, bmp)
            }
            session.setMetadata(metaBuilder.build())
            session.isActive = true
        }
    }

    private fun detachForegroundKeepNotification() {
        if (!isForegroundNow) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
        }
        isForegroundNow = false
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        runCatching { nm?.notify(NOTIFICATION_ID, buildMediaNotification()) }
    }

    private fun publishNotification() {
        val notification = buildMediaNotification()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        // Keep foreground status active across play AND short pauses so Android 14–16 never
        // freezes the background WebView or blocks resuming playback from the notification shade.
        if (currentIsPlaying || !isForegroundNow) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isForegroundNow = true
            }.onFailure { err ->
                Log.w(TAG, "startForeground(mediaPlayback) fallback: ${err.message}")
                runCatching {
                    startForeground(NOTIFICATION_ID, notification)
                    isForegroundNow = true
                }.onFailure {
                    nm?.notify(NOTIFICATION_ID, notification)
                }
            }
        } else {
            runCatching { nm?.notify(NOTIFICATION_ID, notification) }
        }
    }

    private fun buildMediaNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_OPEN_BROWSER_TAB_ID, currentTabId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            4400,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevAction = buildServiceAction(
            requestCode = 4401,
            action = ACTION_PREV,
            iconRes = android.R.drawable.ic_media_previous,
            label = "Previous"
        )
        val playPauseAction = buildServiceAction(
            requestCode = 4402,
            action = ACTION_PLAY_PAUSE,
            iconRes = if (currentIsPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            label = if (currentIsPlaying) "Pause" else "Play"
        )
        val nextAction = buildServiceAction(
            requestCode = 4403,
            action = ACTION_NEXT,
            iconRes = android.R.drawable.ic_media_next,
            label = "Next"
        )
        val closeAction = buildServiceAction(
            requestCode = 4404,
            action = ACTION_STOP,
            iconRes = android.R.drawable.ic_menu_close_clear_cancel,
            label = "Stop"
        )
        val deletePendingIntent = PendingIntent.getService(
            this,
            4405,
            Intent(this, BrowserMediaPlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mediaStyle = Notification.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
        mediaSession?.sessionToken?.let { token ->
            mediaStyle.setMediaSession(token)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(currentIsPlaying)
            .setShowWhen(false)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(closeAction)
            .setStyle(mediaStyle)

        currentArtworkBitmap?.let { bmp ->
            builder.setLargeIcon(bmp)
        }

        return builder.build()
    }

    private fun buildServiceAction(
        requestCode: Int,
        action: String,
        iconRes: Int,
        label: String
    ): Notification.Action {
        val intent = Intent(this, BrowserMediaPlaybackService::class.java).apply {
            this.action = action
        }
        val pi = PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(
            Icon.createWithResource(this, iconRes),
            label,
            pi
        ).build()
    }

    private fun fetchArtworkAsync(artworkUrl: String) {
        serviceScope.launch {
            val bmp = runCatching {
                val conn = (URL(artworkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    instanceFollowRedirects = true
                }
                conn.inputStream.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }?.let { decoded ->
                    val maxDim = 512
                    if (decoded.width > maxDim || decoded.height > maxDim) {
                        val scale = maxDim.toFloat() / maxOf(decoded.width, decoded.height).toFloat()
                        val scaled = Bitmap.createScaledBitmap(
                            decoded,
                            (decoded.width * scale).toInt().coerceAtLeast(1),
                            (decoded.height * scale).toInt().coerceAtLeast(1),
                            true
                        )
                        if (scaled !== decoded) decoded.recycle()
                        scaled
                    } else {
                        decoded
                    }
                }
            }.getOrNull()

            if (bmp != null && artworkUrl == currentArtworkUrl && isRunning) {
                mainHandler.post {
                    if (artworkUrl == currentArtworkUrl && isRunning) {
                        currentArtworkBitmap = bmp
                        updateMediaSessionState()
                        publishNotification()
                    }
                }
            }
        }
    }

    private fun updatePowerLocks(shouldHold: Boolean) {
        runCatching {
            if (shouldHold) {
                if (wakeLock == null) {
                    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                    wakeLock = pm?.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "Agentra:BrowserMediaWakeLock"
                    )?.apply { setReferenceCounted(false) }
                }
                if (wakeLock?.isHeld == false) {
                    // Bounded 4-hour safety timeout, renewed on updates and released immediately on pause/stop
                    wakeLock?.acquire(4L * 60L * 60L * 1000L)
                }

                if (wifiLock == null) {
                    val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                    } else {
                        @Suppress("DEPRECATION")
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF
                    }
                    wifiLock = wm?.createWifiLock(lockMode, "Agentra:BrowserMediaWifiLock")?.apply {
                        setReferenceCounted(false)
                    }
                }
                if (wifiLock?.isHeld == false) {
                    wifiLock?.acquire()
                }
            } else {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
                if (wifiLock?.isHeld == true) {
                    wifiLock?.release()
                }
            }
        }
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        runCatching {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(becomingNoisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(becomingNoisyReceiver, filter)
            }
            noisyReceiverRegistered = true
        }
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        runCatching { unregisterReceiver(becomingNoisyReceiver) }
        noisyReceiverRegistered = false
    }
}
