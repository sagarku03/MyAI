package com.jarvisai.assistant.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jarvisai.assistant.data.local.JarvisDatabase
import com.jarvisai.assistant.data.local.NotificationLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android NotificationListenerService for JARVIS AI Notification Assistant.
 * Captures notifications while strictly respecting privacy exclusions.
 */
class JarvisNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "JarvisNotifService"

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        fun isNotificationAccessGranted(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val myComponent = ComponentName(context, JarvisNotificationListenerService::class.java).flattenToString()
            return enabledListeners.contains(myComponent)
        }

        fun getNotificationSettingsIntent(): Intent {
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: JarvisDatabase

    override fun onCreate() {
        super.onCreate()
        database = JarvisDatabase.getInstance(applicationContext)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isServiceConnected.value = true
        Log.i(TAG, "Notification listener connected.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isServiceConnected.value = false
        Log.i(TAG, "Notification listener disconnected.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        // Skip JARVIS own notifications or ongoing system bars
        if (packageName == applicationContext.packageName) return
        if (sbn.isOngoing) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty().trim()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty().trim()

        if (title.isEmpty() && text.isEmpty()) return

        serviceScope.launch {
            try {
                val pm = packageManager
                val appLabel = try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    packageName
                }

                val logEntity = NotificationLogEntity(
                    packageName = packageName,
                    appName = appLabel,
                    title = title,
                    text = text,
                    timestamp = sbn.postTime
                )
                database.jarvisDao().insertNotificationLog(logEntity)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record notification: ${e.message}")
            }
        }
    }
}
