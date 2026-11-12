package com.jarvisai.assistant.security

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Jarvis Device Administrator Receiver (`BIND_DEVICE_ADMIN`).
 * Protects the user's Termux Linux rootfs, coding workspaces, and AI databases from accidental
 * icon-drag uninstallation. While active, Android OS blocks package removal until the user
 * explicitly deactivates the administrator.
 */
class JarvisDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Jarvis AI Device Administrator protection ENABLED.")
        Toast.makeText(context, "Jarvis Anti-Uninstall Shield Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling Jarvis Device Admin allows the app and its multi-gigabyte Termux Linux workspace to be uninstalled."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Jarvis AI Device Administrator protection DISABLED.")
    }

    companion object {
        private const val TAG = "JarvisDeviceAdmin"

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, JarvisDeviceAdminReceiver::class.java)
        }

        fun isAdminActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                ?: return false
            return runCatching { dpm.isAdminActive(getComponentName(context)) }.getOrDefault(false)
        }

        fun createActivationIntent(context: Context): Intent {
            return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Enabling Jarvis Device Administrator prevents accidental uninstallation of your Termux Linux rootfs, SDK toolchains, and local Git repositories."
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
                if (dpm.isAdminActive(getComponentName(context))) {
                    dpm.removeActiveAdmin(getComponentName(context))
                    Log.i(TAG, "Removed active Jarvis Device Admin.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to deactivate Device Admin: ${e.message}", e)
            }
        }
    }
}
