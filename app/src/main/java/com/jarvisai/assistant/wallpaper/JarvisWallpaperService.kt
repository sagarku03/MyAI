package com.jarvisai.assistant.wallpaper

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.jarvisai.assistant.assistant.JarvisAssistActivity
import kotlin.math.cos
import kotlin.math.sin

class JarvisWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return JarvisWallpaperEngine()
    }

    inner class JarvisWallpaperEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private var isVisible = false
        private var lastTapTime: Long = 0
        private var rotationAngle = 0f

        private val cyanPaint = Paint().apply {
            color = Color.parseColor("#00E5FF")
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }

        private val corePaint = Paint().apply {
            color = Color.parseColor("#00E5FF")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val darkBluePaint = Paint().apply {
            color = Color.parseColor("#050B14")
            style = Paint.Style.FILL
        }

        private val textPaint = Paint().apply {
            color = Color.parseColor("#80D8FF")
            textSize = 36f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        private val drawRunnable = object : Runnable {
            override fun run() {
                drawFrame()
                if (isVisible) {
                    handler.postDelayed(this, 33) // ~30 fps when visible
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            this.isVisible = visible
            if (visible) {
                handler.post(drawRunnable)
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            this.isVisible = false
            handler.removeCallbacks(drawRunnable)
        }

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
            if (event?.action == MotionEvent.ACTION_DOWN) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastTapTime < 400) {
                    // Double tap detected: awaken JARVIS assistant!
                    wakeJarvisFromWallpaper()
                }
                lastTapTime = now
            }
        }

        private fun wakeJarvisFromWallpaper() {
            try {
                val intent = Intent(applicationContext, JarvisAssistActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                applicationContext.startActivity(intent)
            } catch (_: Exception) {
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    val width = canvas.width.toFloat()
                    val height = canvas.height.toFloat()
                    val centerX = width / 2f
                    val centerY = height / 2f

                    // Background
                    canvas.drawRect(0f, 0f, width, height, darkBluePaint)

                    // Arc Reactor Outer Ring
                    rotationAngle = (rotationAngle + 1.2f) % 360f
                    val outerRadius = width.coerceAtMost(height) * 0.28f

                    cyanPaint.strokeWidth = 6f
                    cyanPaint.alpha = 180
                    canvas.drawCircle(centerX, centerY, outerRadius, cyanPaint)

                    // Rotating segmented arcs
                    cyanPaint.strokeWidth = 8f
                    cyanPaint.alpha = 240
                    for (i in 0 until 6) {
                        val angle = rotationAngle + i * 60f
                        val rad = Math.toRadians(angle.toDouble())
                        val x = centerX + outerRadius * cos(rad).toFloat()
                        val y = centerY + outerRadius * sin(rad).toFloat()
                        canvas.drawCircle(x, y, 12f, cyanPaint)
                    }

                    // Inner Pulsing Core
                    val pulse = (sin(Math.toRadians(rotationAngle.toDouble() * 3)) * 8f).toFloat()
                    val innerRadius = outerRadius * 0.45f + pulse
                    corePaint.alpha = 220
                    canvas.drawCircle(centerX, centerY, innerRadius, corePaint)

                    // Glow circle
                    cyanPaint.strokeWidth = 3f
                    cyanPaint.alpha = 120
                    canvas.drawCircle(centerX, centerY, outerRadius * 0.72f, cyanPaint)

                    // Text HUD
                    canvas.drawText("JARVIS // NEURAL CORE ONLINE", centerX, centerY + outerRadius + 80f, textPaint)
                    canvas.drawText("Double-tap to awaken assistant", centerX, centerY + outerRadius + 130f, textPaint)
                }
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}
