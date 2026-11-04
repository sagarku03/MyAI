package com.jarvisai.assistant.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.jarvisai.assistant.service.JarvisTtsManager
import com.jarvisai.assistant.service.LiveVoiceForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class JarvisPhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        Log.d(TAG, "Phone state changed: $state, number: $incomingNumber")

        if (state == TelephonyManager.EXTRA_STATE_RINGING) {
            lastRingingNumber = incomingNumber
            isCurrentlyRinging = true

            val telephonyManager = JarvisTelephonyManager(context)
            val callerName = telephonyManager.resolveCallerName(incomingNumber)
            lastCallerName = callerName

            CoroutineScope(Dispatchers.Main).launch {
                val announcement = "Boss, $callerName ka call aa raha hai. Uthau ya decline kar du?"
                JarvisTtsManager.speak(context, announcement)

                try {
                    LiveVoiceForegroundService.wakeSessionIfNeeded(context)
                } catch (_: Exception) {
                }
            }
        } else if (state == TelephonyManager.EXTRA_STATE_IDLE || state == TelephonyManager.EXTRA_STATE_OFFHOOK) {
            isCurrentlyRinging = false
        }
    }

    companion object {
        private const val TAG = "JarvisPhoneReceiver"
        @Volatile
        var isCurrentlyRinging: Boolean = false
        @Volatile
        var lastRingingNumber: String? = null
        @Volatile
        var lastCallerName: String? = null
    }
}
