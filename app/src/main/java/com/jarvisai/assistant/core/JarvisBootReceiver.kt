package com.jarvisai.assistant.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jarvisai.assistant.service.JarvisOverlayHudService
import com.jarvisai.assistant.service.LiveVoiceForegroundService

class JarvisBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action
        Log.d(TAG, "Boot event received: $action. Auto-restoring JARVIS AI background engines.")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                try {
                    LiveVoiceForegroundService.startServiceIfNeeded(context)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed auto-starting LiveVoiceForegroundService on boot: ${e.message}")
                }

                try {
                    if (JarvisOverlayHudService.canDrawOverlays(context)) {
                        val hudIntent = Intent(context, JarvisOverlayHudService::class.java).apply {
                            this.action = JarvisOverlayHudService.ACTION_SHOW_HUD
                        }
                        context.startService(hudIntent)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed auto-starting JarvisOverlayHudService on boot: ${e.message}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "JarvisBootReceiver"
    }
}
