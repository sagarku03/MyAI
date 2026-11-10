package com.jarvisai.assistant.security

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Jarvis Device Admin Receiver
 * Protects Jarvis AI from accidental uninstallation or rogue process termination.
 * When active, Android requires explicit deactivation in System Settings before uninstalling,
 * ensuring compiled Linux environments, toolchains, and uncommitted Git repos are preserved.
 */
class JarvisDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Jarvis AI Enterprise Device Admin Protection ENABLED.")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Jarvis AI Enterprise Device Admin Protection DISABLED.")
    }

    companion object {
        private const val TAG = "JarvisDeviceAdmin"

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, JarvisDeviceAdminReceiver::class.java)
        }

        fun isAdminActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
            return dpm.isAdminActive(getComponentName(context))
        }

        fun createActivationIntent(context: Context): Intent {
            return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Enabling Jarvis Device Admin protects your embedded Linux environment, Git repositories, and AI cognitive memories from accidental deletion or uninstallation."
                )
            }
        }

        fun createDeviceAdminSettingsIntent(): Intent {
            return Intent("android.settings.DEVICE_ADMIN_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        fun deactivateAdmin(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
            try {
                dpm.removeActiveAdmin(getComponentName(context))
                Log.i(TAG, "Successfully removed Jarvis active Device Admin status.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove Device Admin: ${e.message}", e)
            }
        }
    }
}
