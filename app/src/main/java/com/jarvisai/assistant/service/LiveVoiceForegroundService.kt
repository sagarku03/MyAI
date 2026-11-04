package com.jarvisai.assistant.service

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
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Shared singleton bridge between [GeminiLiveVoiceManager], [MainActivity], and [LiveVoiceForegroundService]
 * so the Foreground Service and Floating Overlay Widget stay synchronized in real time with the active
 * Live Voice call state.
 */
object LiveVoiceSessionBridge {
    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _isWakeWordListening = MutableStateFlow(false)
    val isWakeWordListening: StateFlow<Boolean> = _isWakeWordListening.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _liveStatusLabel = MutableStateFlow("Listening...")
    val liveStatusLabel: StateFlow<String> = _liveStatusLabel.asStateFlow()

    private val _isAppInBackground = MutableStateFlow(false)
    val isAppInBackground: StateFlow<Boolean> = _isAppInBackground.asStateFlow()

    private val _floatingOverlayEnabled = MutableStateFlow(true)
    val floatingOverlayEnabled: StateFlow<Boolean> = _floatingOverlayEnabled.asStateFlow()

    private val _isScreenSharingActive = MutableStateFlow(false)
    val isScreenSharingActive: StateFlow<Boolean> = _isScreenSharingActive.asStateFlow()

    @Volatile
    var onToggleMuteCallback: (() -> Unit)? = null

    @Volatile
    var onEndSessionCallback: (() -> Unit)? = null

    @Volatile
    var onEmergencyStopCallback: (() -> Unit)? = null

    @Volatile
    var onWakeSessionCallback: (() -> Unit)? = null

    fun triggerEmergencyStop() {
        onEmergencyStopCallback?.invoke() ?: onEndSessionCallback?.invoke()
    }

    fun triggerWakeSession() {
        onWakeSessionCallback?.invoke()
    }

    fun updateSessionState(active: Boolean, muted: Boolean, statusLabel: String) {
        _isSessionActive.value = active
        _isMicMuted.value = muted
        _liveStatusLabel.value = statusLabel
    }

    fun setWakeWordListening(listening: Boolean) {
        _isWakeWordListening.value = listening
    }

    fun setScreenSharingActive(active: Boolean) {
        _isScreenSharingActive.value = active
    }

    fun setAppInBackground(inBackground: Boolean) {
        _isAppInBackground.value = inBackground
    }

    fun setFloatingOverlayEnabled(enabled: Boolean) {
        _floatingOverlayEnabled.value = enabled
    }
}

/**
 * Android Foreground Service + Floating HUD Overlay for JARVIS Live Voice & 24/7 Screen-Off Wake Word.
 *
 * Ensures that when a Live Voice session OR Wake Word Engine is active and the user presses Home,
 * switches to another app, or locks the phone screen:
 * 1. The microphone capture, Offline/Online Wake Word Engine, & Gemini Live WebSocket stream remain alive.
 * 2. A PARTIAL_WAKE_LOCK keeps the audio processing thread active even when the screen is off.
 * 3. A persistent Foreground Service notification is displayed.
 * 4. If overlay permission (`SYSTEM_ALERT_WINDOW`) is granted and a Live Voice call is active,
 *    a draggable JARVIS HUD Floating Popup appears over other apps.
 */
class LiveVoiceForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateObserverJob: Job? = null

    @Volatile
    private var hasPromotedToForeground = false

    @Volatile
    private var hasHandledStartCommand = false

    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var windowManager: WindowManager? = null
    private var overlayRootView: View? = null
    private var statusTextView: TextView? = null
    private var muteButtonView: Button? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        createNotificationChannel()
        acquirePartialWakeLockIfNeeded()
        // Immediately satisfy Android's startForegroundService() contract in onCreate()
        promoteToForegroundSafely(includeProjection = false)
    }

    private fun acquirePartialWakeLockIfNeeded() {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return
            wakeLock = pm.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK,
                "JARVIS:ForegroundVoiceWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to acquire PARTIAL_WAKE_LOCK: ${e.localizedMessage}")
        }
    }

    private fun releasePartialWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        } finally {
            wakeLock = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val includeProjection = intent?.action == ACTION_ELEVATE_MEDIA_PROJECTION ||
            LiveVoiceSessionBridge.isScreenSharingActive.value

        // Always call startForeground() first before any potential stopSelf() branch
        promoteToForegroundSafely(includeProjection = includeProjection)
        acquirePartialWakeLockIfNeeded()
        hasHandledStartCommand = true
        isPendingForegroundStart = false

        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                LiveVoiceSessionBridge.setScreenSharingActive(false)
                stopGracefully()
                return START_NOT_STICKY
            }
            ACTION_END_CALL_FROM_NOTIFICATION -> {
                LiveVoiceSessionBridge.setScreenSharingActive(false)
                LiveVoiceSessionBridge.onEndSessionCallback?.invoke()
                if (!LiveVoiceSessionBridge.isWakeWordListening.value) {
                    stopGracefully()
                    return START_NOT_STICKY
                }
            }
            ACTION_EMERGENCY_STOP -> {
                LiveVoiceSessionBridge.setScreenSharingActive(false)
                LiveVoiceSessionBridge.triggerEmergencyStop()
                if (!LiveVoiceSessionBridge.isWakeWordListening.value) {
                    stopGracefully()
                    return START_NOT_STICKY
                }
            }
            ACTION_TOGGLE_MUTE_FROM_NOTIFICATION -> {
                LiveVoiceSessionBridge.onToggleMuteCallback?.invoke()
            }
            ACTION_ELEVATE_MEDIA_PROJECTION -> {
                LiveVoiceSessionBridge.setScreenSharingActive(true)
            }
            ACTION_DOWNGRADE_MEDIA_PROJECTION -> {
                LiveVoiceSessionBridge.setScreenSharingActive(false)
            }
            ACTION_WAKE_SESSION -> {
                LiveVoiceSessionBridge.onWakeSessionCallback?.invoke()
                if (!LiveVoiceSessionBridge.isSessionActive.value && !LiveVoiceSessionBridge.isWakeWordListening.value) {
                    stopGracefully()
                    return START_NOT_STICKY
                }
            }
        }

        if (!LiveVoiceSessionBridge.isSessionActive.value && !LiveVoiceSessionBridge.isWakeWordListening.value) {
            stopGracefully()
            return START_NOT_STICKY
        }

        if (stateObserverJob == null || stateObserverJob?.isActive != true) {
            observeBridgeState()
        }
        updateOverlayVisibility()
        return START_STICKY
    }

    private fun promoteToForegroundSafely(includeProjection: Boolean) {
        createNotificationChannel()
        val notification = buildForegroundNotification(
            statusText = LiveVoiceSessionBridge.liveStatusLabel.value,
            isMuted = LiveVoiceSessionBridge.isMicMuted.value,
            isLiveSessionActive = LiveVoiceSessionBridge.isSessionActive.value,
            isWakeWordListening = LiveVoiceSessionBridge.isWakeWordListening.value
        )
        val hasMicPerm = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val fgType = when {
                    includeProjection && hasMicPerm ->
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    hasMicPerm ->
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    else -> 0
                }
                if (fgType != 0) {
                    startForeground(NOTIFICATION_ID, notification, fgType)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            hasPromotedToForeground = true
        } catch (e: Exception) {
            Log.w(TAG, "Primary startForeground failed (${e.localizedMessage}), trying fallback type")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                hasPromotedToForeground = true
            } catch (fallbackEx: Exception) {
                Log.w(TAG, "Fallback startForeground failed (${fallbackEx.localizedMessage}), trying untyped")
                try {
                    startForeground(NOTIFICATION_ID, notification)
                    hasPromotedToForeground = true
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun stopGracefully() {
        if (!hasPromotedToForeground) {
            promoteToForegroundSafely(includeProjection = false)
        }
        removeFloatingOverlay()
        releasePartialWakeLock()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        try {
            stopSelf()
        } catch (_: Exception) {
        }
        isServiceRunning = false
        isPendingForegroundStart = false
    }

    private fun observeBridgeState() {
        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            combine(
                LiveVoiceSessionBridge.isSessionActive,
                LiveVoiceSessionBridge.isWakeWordListening,
                LiveVoiceSessionBridge.isMicMuted,
                LiveVoiceSessionBridge.liveStatusLabel,
                LiveVoiceSessionBridge.isAppInBackground
            ) { active, wakeListening, muted, statusLabel, inBackground ->
                OverlaySnapshot(
                    active = active,
                    wakeWordListening = wakeListening,
                    muted = muted,
                    statusLabel = statusLabel,
                    inBackground = inBackground,
                    overlayEnabled = LiveVoiceSessionBridge.floatingOverlayEnabled.value
                )
            }.collect { snap ->
                if (!snap.active && !snap.wakeWordListening) {
                    if (hasHandledStartCommand) {
                        stopGracefully()
                    }
                    return@collect
                }

                acquirePartialWakeLockIfNeeded()

                // Update persistent notification
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.notify(
                    NOTIFICATION_ID,
                    buildForegroundNotification(
                        statusText = snap.statusLabel,
                        isMuted = snap.muted,
                        isLiveSessionActive = snap.active,
                        isWakeWordListening = snap.wakeWordListening
                    )
                )

                // Show floating overlay when app is in background (and permission is granted)
                if (snap.inBackground && snap.overlayEnabled && canDrawOverlays(this@LiveVoiceForegroundService)) {
                    showOrUpdateFloatingOverlay(snap.statusLabel, snap.muted)
                } else {
                    removeFloatingOverlay()
                }
            }
        }
    }

    private fun updateOverlayVisibility() {
        val active = LiveVoiceSessionBridge.isSessionActive.value
        val inBg = LiveVoiceSessionBridge.isAppInBackground.value
        val enabled = LiveVoiceSessionBridge.floatingOverlayEnabled.value
        if (active && inBg && enabled && canDrawOverlays(this)) {
            showOrUpdateFloatingOverlay(
                statusText = LiveVoiceSessionBridge.liveStatusLabel.value,
                isMuted = LiveVoiceSessionBridge.isMicMuted.value
            )
        } else {
            removeFloatingOverlay()
        }
    }

    private fun showOrUpdateFloatingOverlay(statusText: String, isMuted: Boolean) {
        if (overlayRootView != null) {
            statusTextView?.text = "JARVIS LIVE • $statusText"
            muteButtonView?.text = if (isMuted) "Unmute" else "Mute"
            muteButtonView?.setTextColor(if (isMuted) Color.parseColor("#FFB300") else Color.parseColor("#00E5FF"))
            return
        }

        if (!canDrawOverlays(this)) return
        val wm = windowManager ?: return

        try {
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val windowLayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dpToPx(16)
                y = dpToPx(110)
            }

            val cardDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#EE071320"))
                setStroke(dpToPx(2), Color.parseColor("#00E5FF"))
            }

            val rootContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = cardDrawable
                setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))
            }

            // Top row: Glowing Arc Reactor icon + Live Voice status indicator
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val arcIconBadge = View(this).apply {
                val circle = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#00E5FF"))
                    setStroke(dpToPx(2), Color.parseColor("#80D8FF"))
                }
                background = circle
                this.layoutParams = LinearLayout.LayoutParams(dpToPx(12), dpToPx(12)).apply {
                    marginEnd = dpToPx(8)
                }
            }

            val titleTv = TextView(this).apply {
                text = "JARVIS LIVE • $statusText"
                setTextColor(Color.parseColor("#00E5FF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                typeface = Typeface.DEFAULT_BOLD
            }
            statusTextView = titleTv

            headerRow.addView(arcIconBadge)
            headerRow.addView(titleTv)
            rootContainer.addView(headerRow)

            // Bottom row: Mute/Unmute button, End Call button, Return to JARVIS button
            val actionsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(8), 0, 0)
            }

            val muteBtn = createHudOverlayButton(
                label = if (isMuted) "Unmute" else "Mute",
                textColor = if (isMuted) Color.parseColor("#FFB300") else Color.parseColor("#00E5FF"),
                strokeColor = Color.parseColor("#00E5FF")
            ) {
                LiveVoiceSessionBridge.onToggleMuteCallback?.invoke()
            }
            muteButtonView = muteBtn

            val endBtn = createHudOverlayButton(
                label = "STOP",
                textColor = Color.WHITE,
                strokeColor = Color.parseColor("#FF1744"),
                fillColor = Color.parseColor("#CCFF1744")
            ) {
                LiveVoiceSessionBridge.triggerEmergencyStop()
                stopGracefully()
            }

            val returnBtn = createHudOverlayButton(
                label = "Open JARVIS",
                textColor = Color.parseColor("#00E676"),
                strokeColor = Color.parseColor("#00E676")
            ) {
                val openIntent = Intent(this@LiveVoiceForegroundService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(openIntent)
            }

            actionsRow.addView(muteBtn)
            actionsRow.addView(endBtn)
            actionsRow.addView(returnBtn)
            rootContainer.addView(actionsRow)

            // Enable smooth drag-to-move anywhere on screen
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            rootContainer.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = windowLayoutParams.x
                        initialY = windowLayoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        windowLayoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        windowLayoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            wm.updateViewLayout(rootContainer, windowLayoutParams)
                        } catch (_: Exception) {
                        }
                        true
                    }
                    else -> false
                }
            }

            wm.addView(rootContainer, windowLayoutParams)
            overlayRootView = rootContainer
        } catch (e: Exception) {
            Log.e(TAG, "Error showing floating overlay: ${e.localizedMessage}")
        }
    }

    private fun createHudOverlayButton(
        label: String,
        textColor: Int,
        strokeColor: Int,
        fillColor: Int = Color.parseColor("#2200E5FF"),
        onClick: () -> Unit
    ): Button {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(8).toFloat()
            setColor(fillColor)
            setStroke(dpToPx(1), strokeColor)
        }
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            background = bg
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dpToPx(10), dpToPx(5), dpToPx(10), dpToPx(5))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(6)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun removeFloatingOverlay() {
        val view = overlayRootView ?: return
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
        } finally {
            overlayRootView = null
            statusTextView = null
            muteButtonView = null
        }
    }

    private fun buildForegroundNotification(
        statusText: String,
        isMuted: Boolean,
        isLiveSessionActive: Boolean = LiveVoiceSessionBridge.isSessionActive.value,
        isWakeWordListening: Boolean = LiveVoiceSessionBridge.isWakeWordListening.value
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(this, LiveVoiceForegroundService::class.java).apply {
            action = ACTION_TOGGLE_MUTE_FROM_NOTIFICATION
        }
        val pendingMute = PendingIntent.getService(
            this,
            101,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endIntent = Intent(this, LiveVoiceForegroundService::class.java).apply {
            action = ACTION_END_CALL_FROM_NOTIFICATION
        }
        val pendingEnd = PendingIntent.getService(
            this,
            102,
            endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val emergencyStopIntent = Intent(this, LiveVoiceForegroundService::class.java).apply {
            action = ACTION_EMERGENCY_STOP
        }
        val pendingEmergencyStop = PendingIntent.getService(
            this,
            103,
            emergencyStopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isLiveSessionActive) {
            "JARVIS AI Live Voice Active"
        } else if (isWakeWordListening) {
            "JARVIS Wake Word Active (Online/Offline)"
        } else {
            "JARVIS AI Voice Service"
        }

        val body = if (isLiveSessionActive) {
            "$statusText • Tap to return to JARVIS"
        } else {
            "Say 'Hey Jarvis' anytime • Works even with screen off"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setCategory(if (isLiveSessionActive) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingOpen)

        if (isLiveSessionActive) {
            builder.addAction(
                android.R.drawable.ic_lock_silent_mode_off,
                if (isMuted) "Unmute Mic" else "Mute Mic",
                pendingMute
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "End Call",
                pendingEnd
            )
        }
        builder.addAction(
            android.R.drawable.ic_delete,
            "EMERGENCY STOP",
            pendingEmergencyStop
        )
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Live Voice Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps JARVIS AI Live Voice and Wake Word active when you switch apps or lock screen."
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        isServiceRunning = false
        isPendingForegroundStart = false
        stateObserverJob?.cancel()
        removeFloatingOverlay()
        releasePartialWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private data class OverlaySnapshot(
        val active: Boolean,
        val wakeWordListening: Boolean,
        val muted: Boolean,
        val statusLabel: String,
        val inBackground: Boolean,
        val overlayEnabled: Boolean
    )

    companion object {
        private const val TAG = "LiveVoiceFgService"
        private const val CHANNEL_ID = "jarvis_live_voice_channel"
        private const val NOTIFICATION_ID = 4201

        @Volatile
        var isServiceRunning: Boolean = false

        @Volatile
        var isPendingForegroundStart: Boolean = false

        const val ACTION_START_SERVICE = "com.jarvisai.assistant.action.START_LIVE_VOICE_FG"
        const val ACTION_STOP_SERVICE = "com.jarvisai.assistant.action.STOP_LIVE_VOICE_FG"
        const val ACTION_TOGGLE_MUTE_FROM_NOTIFICATION = "com.jarvisai.assistant.action.TOGGLE_MUTE"
        const val ACTION_END_CALL_FROM_NOTIFICATION = "com.jarvisai.assistant.action.END_CALL"
        const val ACTION_EMERGENCY_STOP = "com.jarvisai.assistant.action.EMERGENCY_STOP"
        const val ACTION_ELEVATE_MEDIA_PROJECTION = "com.jarvisai.assistant.action.ELEVATE_MEDIA_PROJECTION"
        const val ACTION_DOWNGRADE_MEDIA_PROJECTION = "com.jarvisai.assistant.action.DOWNGRADE_MEDIA_PROJECTION"
        const val ACTION_WAKE_SESSION = "com.jarvisai.assistant.action.WAKE_SESSION"

        fun canDrawOverlays(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        fun startServiceIfNeeded(context: Context) {
            if (!LiveVoiceSessionBridge.isSessionActive.value && !LiveVoiceSessionBridge.isWakeWordListening.value) {
                return
            }
            try {
                val intent = Intent(context.applicationContext, LiveVoiceForegroundService::class.java).apply {
                    action = ACTION_START_SERVICE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    isPendingForegroundStart = true
                    context.applicationContext.startForegroundService(intent)
                } else {
                    context.applicationContext.startService(intent)
                }
            } catch (e: Exception) {
                isPendingForegroundStart = false
                Log.w(TAG, "Unable to start LiveVoiceForegroundService: ${e.localizedMessage}")
            }
        }

        fun wakeSessionIfNeeded(context: Context) {
            try {
                LiveVoiceSessionBridge.onWakeSessionCallback?.invoke()
                if (LiveVoiceSessionBridge.isSessionActive.value || LiveVoiceSessionBridge.isWakeWordListening.value) {
                    startServiceIfNeeded(context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to wake LiveVoiceSession: ${e.localizedMessage}")
            }
        }

        fun elevateForMediaProjection(context: Context) {
            try {
                LiveVoiceSessionBridge.setScreenSharingActive(true)
                if (!LiveVoiceSessionBridge.isSessionActive.value && !LiveVoiceSessionBridge.isWakeWordListening.value && !isServiceRunning) {
                    return
                }
                val intent = Intent(context.applicationContext, LiveVoiceForegroundService::class.java).apply {
                    action = ACTION_ELEVATE_MEDIA_PROJECTION
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    isPendingForegroundStart = true
                    context.applicationContext.startForegroundService(intent)
                } else {
                    context.applicationContext.startService(intent)
                }
            } catch (e: Exception) {
                isPendingForegroundStart = false
                Log.w(TAG, "Unable to elevate LiveVoiceForegroundService for MediaProjection: ${e.localizedMessage}")
            }
        }

        fun downgradeFromMediaProjection(context: Context) {
            try {
                LiveVoiceSessionBridge.setScreenSharingActive(false)
                if (isServiceRunning && !isPendingForegroundStart) {
                    val intent = Intent(context.applicationContext, LiveVoiceForegroundService::class.java).apply {
                        action = ACTION_DOWNGRADE_MEDIA_PROJECTION
                    }
                    context.applicationContext.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun stopServiceIfRunning(context: Context) {
            try {
                LiveVoiceSessionBridge.setScreenSharingActive(false)
                if (LiveVoiceSessionBridge.isWakeWordListening.value && !LiveVoiceSessionBridge.isSessionActive.value) {
                    // Keep foreground service running for 24/7 Screen-Off Wake Word listening
                    return
                }
                if (isServiceRunning && !isPendingForegroundStart) {
                    val intent = Intent(context.applicationContext, LiveVoiceForegroundService::class.java).apply {
                        action = ACTION_STOP_SERVICE
                    }
                    context.applicationContext.stopService(intent)
                }
            } catch (_: Exception) {
            }
        }
    }
}
