package com.jarvisai.assistant.driving

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jarvisai.assistant.service.JarvisTtsManager

class JarvisDrivingOffReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action == ACTION_DRIVING_OFF) {
            JarvisDrivingModeManager.isDrivingModeActive = false
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(JarvisDrivingModeManager.DRIVING_NOTIFICATION_ID)
                JarvisTtsManager.speak(context, "Driving mode deactivated.")
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val ACTION_DRIVING_OFF = "com.jarvisai.assistant.DRIVING_OFF"
    }
}
