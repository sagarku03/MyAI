package com.jarvisai.assistant.data.preferences

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Controls the Dual App Icon feature ("1 APK = 2 Launcher Icons") via manifest `<activity-alias>` entries:
 * - [STYLE_AGENTRA_TERM]: "Agentra Term" icon
 * - [STYLE_TERMUX]: Classic black `>_` Termux icon ("Termux")
 */
object TerminalLauncherManager {
    const val STYLE_AGENTRA_TERM = "AGENTRA_TERM"
    const val STYLE_TERMUX = "TERMUX"

    private const val TAG = "TerminalLauncherMgr"
    private const val PREFS_NAME = "agentra_launcher_prefs"
    private const val KEY_PROMPT_SHOWN = "terminal_launcher_prompt_shown"

    fun getAgentraTermComponent(context: Context): ComponentName =
        ComponentName(context.packageName, "com.jarvisai.assistant.AgentraTermAlias")

    fun getClassicTermComponent(context: Context): ComponentName =
        ComponentName(context.packageName, "com.jarvisai.assistant.ClassicTermAlias")

    fun isPromptShown(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PROMPT_SHOWN, false)

    fun markPromptShown(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PROMPT_SHOWN, true)
            .apply()
    }

    fun isLauncherEnabled(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val agentraState = pm.getComponentEnabledSetting(getAgentraTermComponent(context))
            val classicState = pm.getComponentEnabledSetting(getClassicTermComponent(context))
            agentraState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                classicState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } catch (_: Exception) {
            false
        }
    }

    fun getLauncherStyle(context: Context): String {
        return try {
            val pm = context.packageManager
            val classicState = pm.getComponentEnabledSetting(getClassicTermComponent(context))
            if (classicState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                STYLE_TERMUX
            } else {
                STYLE_AGENTRA_TERM
            }
        } catch (_: Exception) {
            STYLE_AGENTRA_TERM
        }
    }

    fun applyLauncherSetting(context: Context, enabled: Boolean, style: String) {
        val pm = context.packageManager
        val agentraTerm = getAgentraTermComponent(context)
        val classic = getClassicTermComponent(context)

        try {
            if (!enabled) {
                setComponentSafe(pm, agentraTerm, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
                setComponentSafe(pm, classic, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
                Log.d(TAG, "Standalone Terminal Launcher icon disabled")
            } else if (style == STYLE_TERMUX) {
                setComponentSafe(pm, agentraTerm, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
                setComponentSafe(pm, classic, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
                Log.d(TAG, "Enabled Classic Termux launcher alias (Termux)")
            } else {
                setComponentSafe(pm, classic, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
                setComponentSafe(pm, agentraTerm, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
                Log.d(TAG, "Enabled Agentra Term launcher alias (Agentra Term)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed updating launcher alias settings: ${e.message}", e)
        }
    }

    private fun setComponentSafe(pm: PackageManager, component: ComponentName, state: Int) {
        try {
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        } catch (e: Exception) {
            Log.w(TAG, "Component ${component.shortClassName} state update failed: ${e.message}")
        }
    }
}
