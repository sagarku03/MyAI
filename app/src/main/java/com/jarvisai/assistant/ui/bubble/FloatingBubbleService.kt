package com.jarvisai.assistant.ui.bubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

/**
 * System Overlay Service for Floating ChatHead Bubble.
 * Runs on top of all Android apps, allowing 1-tap quick chat expansion.
 */
class FloatingBubbleService : Service() {

    companion object {
        const val CHANNEL_ID = "jarvis_bubble_channel"
        const val NOTIFICATION_ID = 4040

        @Volatile
        var isRunning: Boolean = false

        fun start(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBubbleService::class.java))
        }

        fun toggle(context: Context): Boolean {
            return if (isRunning) {
                stop(context)
                false
            } else {
                start(context)
                true
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        setupFloatingBubble()
    }

    private fun setupFloatingBubble() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val bubbleSize = (56 * resources.displayMetrics.density).toInt()
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 200
        }

        val imageView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_dialog_info)
            setBackgroundColor(0xFF00E5FF.toInt())
        }

        imageView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(v, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = abs(event.rawX - initialTouchX)
                        val diffY = abs(event.rawY - initialTouchY)
                        if (diffX < 10 && diffY < 10) {
                            v.performClick()
                            // Click detected -> Launch main app chat
                            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                            launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            if (launchIntent != null) {
                                startActivity(launchIntent)
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })

        bubbleView = imageView
        try {
            windowManager?.addView(bubbleView, params)
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Floating Bubble",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS AI Floating Bubble Active")
            .setContentText("Tap bubble on screen to interact with agents")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        bubbleView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
    }
}
