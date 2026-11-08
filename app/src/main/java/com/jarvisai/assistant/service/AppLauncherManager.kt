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

    companion object {
        // High-priority popular Indian & global Android package mappings
        val POPULAR_APP_PACKAGES = mapOf(
            "flipkart" to listOf("com.flipkart.android"),
            "amazon" to listOf("in.amazon.mShop.android.shopping", "com.amazon.mShop.android.shopping"),
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "youtube" to listOf("com.google.android.youtube"),
            "instagram" to listOf("com.instagram.android"),
            "spotify" to listOf("com.spotify.music"),
            "chrome" to listOf("com.android.chrome"),
            "settings" to listOf("com.android.settings"),
            "camera" to listOf("com.android.camera", "com.google.android.GoogleCamera"),
            "calculator" to listOf("com.google.android.calculator", "com.android.calculator2"),
            "zomato" to listOf("com.application.zomato"),
            "swiggy" to listOf("in.swiggy.android"),
            "paytm" to listOf("net.one97.paytm"),
            "phonepe" to listOf("com.phonepe.app"),
            "gpay" to listOf("com.google.android.apps.nbu.paisa.user"),
            "telegram" to listOf("org.telegram.messenger"),
            "uber" to listOf("com.ubercab"),
            "ola" to listOf("com.olacabs.customer"),
            "gmail" to listOf("com.google.android.gm"),
            "maps" to listOf("com.google.android.apps.maps")
        )
    }

    suspend fun getInstalledApps(): List<InstalledAppInfo> {
        return phoneControlsManager.getInstalledLaunchableApps()
    }

    suspend fun findAppByName(name: String): InstalledAppInfo? {
        val clean = name.trim().lowercase()
        // 1. Check known aliases first
        val knownPkgs = POPULAR_APP_PACKAGES[clean] ?: emptyList()
        val all = getInstalledApps()
        for (pkg in knownPkgs) {
            val match = all.firstOrNull { it.packageName.equals(pkg, ignoreCase = true) }
            if (match != null) return match
        }
        // 2. Exact app name match
        val exact = all.firstOrNull { it.appName.equals(clean, ignoreCase = true) }
        if (exact != null) return exact

        // 3. Substring package or app name match
        return all.firstOrNull { it.packageName.substringAfterLast('.').equals(clean, ignoreCase = true) }
            ?: all.firstOrNull { it.appName.lowercase().contains(clean) }
            ?: all.firstOrNull { it.packageName.lowercase().contains(clean) }
    }

    suspend fun launchApp(
        appName: String,
        packageName: String = "",
        allowBrowserFallback: Boolean = false
    ): AppLaunchOutcome {
        val cleanApp = appName.trim()
        var cleanPkg = packageName.trim()

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

        // If package name is not supplied, always resolve it from installed packages or known aliases first!
        if (cleanPkg.isBlank() && cleanApp.isNotBlank()) {
            val resolvedApp = findAppByName(cleanApp)
            if (resolvedApp != null) {
                cleanPkg = resolvedApp.packageName
            }
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
