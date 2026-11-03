package com.jarvisai.assistant.service

import android.content.Context
import com.jarvisai.assistant.model.InstalledAppInfo

data class AppLaunchOutcome(
    val executed: Boolean,
    val appName: String,
    val packageName: String,
    val isPwaOrWeb: Boolean = false,
    val errorCode: String? = null,
    val message: String,
    val spokenHinglishSummary: String
)

/**
 * Dedicated App Launcher and Installed Application manager for JARVIS AI.
 * Handles launchable packages, WebAPKs, native launch intents, and exact status reporting.
 */
class AppLauncherManager(
    private val context: Context,
    private val phoneControlsManager: PhoneControlsManager = PhoneControlsManager(context)
) {

    suspend fun getInstalledApps(): List<InstalledAppInfo> {
        return phoneControlsManager.getInstalledLaunchableApps()
    }

    suspend fun findAppByName(name: String): InstalledAppInfo? {
        val clean = name.trim().lowercase()
        val all = getInstalledApps()
        return all.firstOrNull { it.appName.equals(clean, ignoreCase = true) }
            ?: all.firstOrNull { it.appName.lowercase().contains(clean) }
    }

    suspend fun launchApp(
        appName: String,
        packageName: String = "",
        allowBrowserFallback: Boolean = false
    ): AppLaunchOutcome {
        val cleanApp = appName.trim()
        val cleanPkg = packageName.trim()

        if (cleanApp.isBlank() && cleanPkg.isBlank()) {
            return AppLaunchOutcome(
                executed = false,
                appName = "",
                packageName = "",
                errorCode = "MALFORMED_TOOL_CALL",
                message = "Missing app_name or package_name.",
                spokenHinglishSummary = "App ka naam ya package provide nahi kiya gaya."
            )
        }

        // Try direct launch via PhoneControlsManager
        val result = phoneControlsManager.launchInstalledApp(
            packageName = cleanPkg,
            appName = cleanApp,
            allowBrowserFallback = allowBrowserFallback
        )

        return if (result.success) {
            val isWeb = cleanPkg.startsWith("http") || result.message.contains("website", ignoreCase = true)
            AppLaunchOutcome(
                executed = true,
                appName = cleanApp,
                packageName = cleanPkg,
                isPwaOrWeb = isWeb,
                message = result.message,
                spokenHinglishSummary = "Maine $cleanApp open kar diya hai."
            )
        } else {
            AppLaunchOutcome(
                executed = false,
                appName = cleanApp,
                packageName = cleanPkg,
                errorCode = "APP_NOT_INSTALLED",
                message = "Application '$cleanApp' is not installed on this Android device.",
                spokenHinglishSummary = "Aapke phone mein '$cleanApp' app installed nahi hai."
            )
        }
    }
}
