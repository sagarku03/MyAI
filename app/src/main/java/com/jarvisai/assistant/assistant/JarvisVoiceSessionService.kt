package com.jarvisai.assistant.assistant

import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import com.jarvisai.assistant.service.JarvisOverlayHudService
import com.jarvisai.assistant.service.LiveVoiceForegroundService

class JarvisVoiceSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return JarvisVoiceSession(this)
    }

    class JarvisVoiceSession(context: Context) : VoiceInteractionSession(context) {

        override fun onCreateContentView(): View {
            // Native Keyguard-Compatible Floating Arc Reactor Voice HUD View
            val rootLayout = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val cardContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(48, 36, 48, 48)
                val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xEE110D28.toInt()) // Royal Purple Glass
                    cornerRadius = 64f
                    setStroke(3, 0xFF8A5CF6.toInt()) // Purple Accent Border
                }
                background = bgDrawable
                val lp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM
                    setMargins(32, 0, 32, 64)
                }
                layoutParams = lp
            }

            // Status Title
            val titleText = TextView(context).apply {
                text = "⚡ JARVIS LIVE VOICE ASSISTANT"
                textSize = 14f
                setTextColor(0xFF38BDF8.toInt()) // Cyan
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 16)
            }

            // Subtitle status
            val subtitleText = TextView(context).apply {
                text = "Namaste sir, main sun raha hoon. Command dijiye..."
                textSize = 12f
                setTextColor(0xFFE2E8F0.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 24)
            }

            // Dismiss Button
            val closeButton = TextView(context).apply {
                text = "Close Assistant ✕"
                textSize = 11f
                setTextColor(0xFF94A3B8.toInt())
                gravity = Gravity.CENTER
                setPadding(24, 12, 24, 12)
                setOnClickListener {
                    hide()
                }
            }

            cardContainer.addView(titleText)
            cardContainer.addView(subtitleText)
            cardContainer.addView(closeButton)
            rootLayout.addView(cardContainer)

            return rootLayout
        }

        override fun onShow(args: Bundle?, showFlags: Int) {
            super.onShow(args, showFlags)
            try {
                // Ensure LiveVoiceForegroundService is awake to stream WebSocket audio
                LiveVoiceForegroundService.wakeSessionIfNeeded(context)

                // If unlocked and overlay permission is granted, also pop the full interactive HUD
                if (JarvisOverlayHudService.canDrawOverlays(context)) {
                    val hudIntent = android.content.Intent(context, JarvisOverlayHudService::class.java).apply {
                        action = JarvisOverlayHudService.ACTION_SHOW_HUD
                    }
                    context.startService(hudIntent)
                }
            } catch (_: Exception) {
            }
            // Do NOT call hide() immediately! Allow the native window to remain visible
            // while the user speaks. The session auto-hides when the user taps outside or presses close.
        }
    }
}
