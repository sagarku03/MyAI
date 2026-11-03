package com.jarvisai.assistant.service

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class ToolExecutionOutcome(
    val callId: String,
    val toolName: String,
    val executed: Boolean,
    val status: String, // "SUCCESS", "ERROR", "CONFIRMATION_REQUIRED"
    val errorCode: String? = null,
    val message: String,
    val spokenHinglishSummary: String,
    val responseJson: JSONObject
)

/**
 * Executes Gemini Live Agent tool calls on Android and produces verified, truthful
 * structured tool responses (`BidiGenerateContentToolResponse`).
 *
 * Supported tools:
 * - `launch_application`
 * - `get_screen_context`
 * - `click_accessible_element`
 * - `scroll_screen`
 * - `navigate_back`
 * - `navigate_home`
 * - `request_sensitive_action_confirmation`
 */
class LiveAgentToolExecutor(
    private val context: Context,
    private val phoneControlsManager: PhoneControlsManager = PhoneControlsManager(context),
    private val accessibilityBridge: AccessibilityControllerBridge = JarvisAccessibilityService
) {

    @Volatile
    var lastExecutedToolTimestampMs: Long = 0L
        private set

    suspend fun executeToolCall(
        functionName: String,
        callId: String,
        args: JSONObject?
    ): ToolExecutionOutcome {
        val safeArgs = args ?: JSONObject()
        val cleanName = functionName.trim()
        Log.d(TAG, "Executing Live Agent tool call: name=$cleanName, id=$callId, args=$safeArgs")

        return try {
            val outcome = when (cleanName) {
                TOOL_LAUNCH_APPLICATION -> executeLaunchApplication(callId, safeArgs)
                TOOL_GET_SCREEN_CONTEXT -> executeGetScreenContext(callId)
                TOOL_CLICK_ACCESSIBLE_ELEMENT -> executeClickAccessibleElement(callId, safeArgs)
                TOOL_SCROLL_SCREEN -> executeScrollScreen(callId, safeArgs)
                TOOL_NAVIGATE_BACK -> executeNavigateBack(callId)
                TOOL_NAVIGATE_HOME -> executeNavigateHome(callId)
                TOOL_SENSITIVE_ACTION_CONFIRMATION -> executeSensitiveActionCheck(callId, safeArgs)
                TOOL_DIRECTIONAL_GESTURE -> executeDirectionalGesture(callId, safeArgs)
                TOOL_TYPE_TEXT -> executeTypeText(callId, safeArgs)
                TOOL_FOCUS_INPUT -> executeFocusInput(callId, safeArgs)
                TOOL_CLEAR_TEXT -> executeClearText(callId)
                TOOL_GLOBAL_ACTION -> executeGlobalAction(callId, safeArgs)
                TOOL_READ_SCREEN_TEXT -> executeReadScreenText(callId)
                TOOL_GET_SYSTEM_TELEMETRY -> executeGetSystemTelemetry(callId)
                TOOL_ADJUST_SYSTEM_VOLUME -> executeAdjustSystemVolume(callId, safeArgs)
                TOOL_CONTROL_MEDIA_PLAYBACK -> executeControlMediaPlayback(callId, safeArgs)
                TOOL_SEND_COMMUNICATION -> executeSendCommunication(callId, safeArgs)
                TOOL_OPEN_PHONE_DIALER -> executeOpenPhoneDialer(callId, safeArgs)
                TOOL_OPEN_SPOTIFY_MUSIC -> executeOpenSpotify(callId, safeArgs)
                else -> buildErrorOutcome(
                    callId = callId,
                    toolName = cleanName.ifEmpty { "unknown_tool" },
                    errorCode = "MALFORMED_TOOL_CALL",
                    message = "Unsupported or unknown tool '$cleanName'.",
                    spokenHinglishSummary = "Yeh action tool ($cleanName) supported nahi hai."
                )
            }
            if (outcome.executed) {
                lastExecutedToolTimestampMs = System.currentTimeMillis()
            }
            outcome
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution exception for $cleanName: ${e.localizedMessage}", e)
            buildErrorOutcome(
                callId = callId,
                toolName = cleanName,
                errorCode = "TOOL_EXECUTION_EXCEPTION",
                message = "Tool execution failed with exception: ${e.localizedMessage ?: "Unexpected error"}",
                spokenHinglishSummary = "Action execute karte waqt error aaya: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    /**
     * Launches an installed Android application or system settings panel and verifies execution.
     */
    private suspend fun executeLaunchApplication(
        callId: String,
        args: JSONObject
    ): ToolExecutionOutcome {
        val rawAppName = args.optString("app_name", "").trim()
        val rawPackageName = args.optString("package_name", "").trim()
        val searchQuery = args.optString("search_query", "").trim()
        val allowBrowserFallback = args.optBoolean("allow_browser_fallback", true)

        if (rawAppName.isEmpty() && rawPackageName.isEmpty()) {
            return buildErrorOutcome(
                callId = callId,
                toolName = TOOL_LAUNCH_APPLICATION,
                errorCode = "MALFORMED_TOOL_CALL",
                message = "Missing required parameter 'app_name' or 'package_name'.",
                spokenHinglishSummary = "Kaunsa app open karna hai uska naam nahi mila."
            )
        }

        val normalizedName = rawAppName.lowercase()
            .replace(Regex("\\b(app|application|ko|please|zara)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // 1. Check if user asked for Android System Settings / Wi-Fi / Bluetooth / Accessibility Settings
        if (normalizedName in setOf("settings", "setting", "phone settings", "system settings", "android settings", "सेटिंग्स", "सेटिंग") ||
            rawPackageName == "com.android.settings"
        ) {
            return launchSystemSettingsIntent(
                callId = callId,
                action = Settings.ACTION_SETTINGS,
                displayLabel = "Android Settings"
            )
        }
        if (normalizedName in setOf("wifi", "wi-fi", "wifi settings", "wi-fi settings", "वाईफाई")) {
            return launchSystemSettingsIntent(
                callId = callId,
                action = Settings.ACTION_WIFI_SETTINGS,
                displayLabel = "Wi-Fi Settings"
            )
        }
        if (normalizedName in setOf("bluetooth", "bluetooth settings", "ब्लूटूथ")) {
            return launchSystemSettingsIntent(
                callId = callId,
                action = Settings.ACTION_BLUETOOTH_SETTINGS,
                displayLabel = "Bluetooth Settings"
            )
        }
        if (normalizedName in setOf("accessibility", "accessibility settings")) {
            return launchSystemSettingsIntent(
                callId = callId,
                action = Settings.ACTION_ACCESSIBILITY_SETTINGS,
                displayLabel = "Accessibility Settings"
            )
        }

        // 2. Special handling for YouTube (with search query support & browser fallback)
        if (normalizedName in setOf("youtube", "you tube", "yt", "यूट्यूब") ||
            rawPackageName.equals(YOUTUBE_PACKAGE, ignoreCase = true)
        ) {
            return launchYouTubeWithVerification(
                callId = callId,
                searchQuery = searchQuery,
                allowBrowserFallback = allowBrowserFallback
            )
        }

        // 3. Special handling for Chrome (with optional search query)
        if (normalizedName in setOf("chrome", "google chrome", "क्रोम") ||
            rawPackageName.equals(CHROME_PACKAGE, ignoreCase = true)
        ) {
            return launchChromeWithVerification(
                callId = callId,
                searchQuery = searchQuery,
                allowBrowserFallback = allowBrowserFallback
            )
        }

        // 4. Camera intent
        if (normalizedName in setOf("camera", "कैमरा")) {
            return try {
                val camIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(camIntent)
                val payload = JSONObject().apply {
                    put("status", "SUCCESS")
                    put("executed", true)
                    put("app_name", "Camera")
                    put("package_name", "system.camera")
                    put("fallback_used", false)
                    put("message", "Launched Android Camera.")
                }
                ToolExecutionOutcome(
                    callId = callId,
                    toolName = TOOL_LAUNCH_APPLICATION,
                    executed = true,
                    status = "SUCCESS",
                    errorCode = null,
                    message = "Launched Android Camera.",
                    spokenHinglishSummary = "Maine Camera open kar diya hai.",
                    responseJson = payload
                )
            } catch (e: Exception) {
                buildErrorOutcome(
                    callId = callId,
                    toolName = TOOL_LAUNCH_APPLICATION,
                    errorCode = "LAUNCH_FAILED",
                    message = "Failed to launch Camera: ${e.localizedMessage ?: "Camera unavailable"}",
                    spokenHinglishSummary = "Camera open karne mein problem aayi."
                )
            }
        }

        // 5. Resolve package candidates from known app catalog or explicit package_name
        val candidatePackages = mutableListOf<String>()
        var displayAppName = rawAppName.ifEmpty { rawPackageName }

        if (rawPackageName.isNotBlank()) {
            candidatePackages.add(rawPackageName)
        }
        KNOWN_APP_PACKAGES[normalizedName]?.let { (pkgs, friendlyName) ->
            displayAppName = friendlyName
            pkgs.forEach { pkg ->
                if (pkg !in candidatePackages) candidatePackages.add(pkg)
            }
        }

        for (pkg in candidatePackages) {
            val launchResult = tryLaunchPackage(pkg, displayAppName)
            if (launchResult != null && launchResult.executed) {
                return launchResult.copy(callId = callId)
            }
        }

        // 6. Search all installed launchable apps via PackageManager & LauncherApps
        val installedApps = phoneControlsManager.getInstalledLaunchableApps()
        val normalizedNoSpaces = normalizedName.replace(Regex("[^a-z0-9]"), "")
        val tokens = normalizedName.split(" ").filter { it.length >= 2 }

        val matchedInstalled = installedApps.firstOrNull { app ->
            (rawPackageName.isNotBlank() && app.packageName.equals(rawPackageName, ignoreCase = true)) ||
                app.appName.equals(normalizedName, ignoreCase = true) ||
                app.appName.equals(rawAppName, ignoreCase = true)
        } ?: installedApps.firstOrNull { app ->
            val appNoSpaces = app.appName.lowercase().replace(Regex("[^a-z0-9]"), "")
            appNoSpaces.isNotEmpty() && normalizedNoSpaces.isNotEmpty() && (
                appNoSpaces == normalizedNoSpaces ||
                    appNoSpaces.contains(normalizedNoSpaces) ||
                    (normalizedNoSpaces.length >= 4 && normalizedNoSpaces.contains(appNoSpaces))
                )
        } ?: installedApps.firstOrNull { app ->
            val appLower = app.appName.lowercase()
            val pkgLower = app.packageName.lowercase()
            (normalizedName.isNotEmpty() && (appLower.contains(normalizedName) || normalizedName.contains(appLower))) ||
                (tokens.isNotEmpty() && tokens.all { tok -> appLower.contains(tok) || pkgLower.contains(tok) })
        }

        if (matchedInstalled != null) {
            val res = phoneControlsManager.launchInstalledApp(
                packageName = matchedInstalled.packageName,
                appName = matchedInstalled.appName,
                activityName = matchedInstalled.activityName
            )
            if (res.success) {
                val payload = JSONObject().apply {
                    put("status", "SUCCESS")
                    put("executed", true)
                    put("app_name", matchedInstalled.appName)
                    put("package_name", matchedInstalled.packageName)
                    put("fallback_used", false)
                    put("message", "Successfully launched ${matchedInstalled.appName} (${matchedInstalled.packageName}).")
                }
                return ToolExecutionOutcome(
                    callId = callId,
                    toolName = TOOL_LAUNCH_APPLICATION,
                    executed = true,
                    status = "SUCCESS",
                    errorCode = null,
                    message = "Successfully launched ${matchedInstalled.appName}.",
                    spokenHinglishSummary = "Maine ${matchedInstalled.appName} open kar diya hai.",
                    responseJson = payload
                )
            } else {
                return buildErrorOutcome(
                    callId = callId,
                    toolName = TOOL_LAUNCH_APPLICATION,
                    errorCode = "LAUNCH_INTENT_UNAVAILABLE",
                    message = "App '${matchedInstalled.appName}' (${matchedInstalled.packageName}) is installed, but its launch activity could not be started: ${res.message}",
                    spokenHinglishSummary = "${matchedInstalled.appName} open karne mein error aaya: ${res.message}"
                )
            }
        }

        // 7. Check if any candidate package is installed on device but lacks a launcher activity
        val pm = context.packageManager
        val installedWithoutLauncher = candidatePackages.firstOrNull { isPackageInstalled(pm, it) }
        if (installedWithoutLauncher != null) {
            return buildErrorOutcome(
                callId = callId,
                toolName = TOOL_LAUNCH_APPLICATION,
                errorCode = "LAUNCH_INTENT_UNAVAILABLE",
                message = "Application '$displayAppName' ($installedWithoutLauncher) is installed on this device, but no launchable Main/Launcher activity is available.",
                spokenHinglishSummary = "'$displayAppName' phone mein installed hai lekin direct launch intent available nahi hai."
            )
        }

        // 8. Truthfully report that the requested application is NOT installed
        val targetLabel = rawAppName.ifEmpty { rawPackageName }
        return buildErrorOutcome(
            callId = callId,
            toolName = TOOL_LAUNCH_APPLICATION,
            errorCode = "APP_NOT_INSTALLED",
            message = "Application '$targetLabel' is not installed on this Android device.",
            spokenHinglishSummary = "Aapke phone mein '$targetLabel' app installed nahi hai."
        )
    }

    private fun launchYouTubeWithVerification(
        callId: String,
        searchQuery: String,
        allowBrowserFallback: Boolean
    ): ToolExecutionOutcome {
        val pm = context.packageManager
        val isYouTubeInstalled = isPackageInstalled(pm, YOUTUBE_PACKAGE) ||
            pm.getLaunchIntentForPackage(YOUTUBE_PACKAGE) != null

        if (searchQuery.isNotBlank()) {
            if (isYouTubeInstalled) {
                try {
                    val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                        setPackage(YOUTUBE_PACKAGE)
                        putExtra(SearchManager.QUERY, searchQuery)
                        putExtra("query", searchQuery)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(searchIntent)
                    val payload = JSONObject().apply {
                        put("status", "SUCCESS")
                        put("executed", true)
                        put("app_name", "YouTube")
                        put("package_name", YOUTUBE_PACKAGE)
                        put("search_query", searchQuery)
                        put("fallback_used", false)
                        put("message", "Launched YouTube app and searched for '$searchQuery'.")
                    }
                    return ToolExecutionOutcome(
                        callId = callId,
                        toolName = TOOL_LAUNCH_APPLICATION,
                        executed = true,
                        status = "SUCCESS",
                        errorCode = null,
                        message = "Launched YouTube app and searched for '$searchQuery'.",
                        spokenHinglishSummary = "Maine YouTube open kar ke '$searchQuery' search kar diya hai.",
                        responseJson = payload
                    )
                } catch (_: Exception) {
                    // Fall through to URL intent
                }
            }

            if (allowBrowserFallback || isYouTubeInstalled) {
                return try {
                    val encoded = Uri.encode(searchQuery)
                    val viewIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.youtube.com/results?search_query=$encoded")
                    ).apply {
                        if (isYouTubeInstalled) {
                            setPackage(YOUTUBE_PACKAGE)
                        }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(viewIntent)
                    val payload = JSONObject().apply {
                        put("status", "SUCCESS")
                        put("executed", true)
                        put("app_name", "YouTube")
                        put("package_name", if (isYouTubeInstalled) YOUTUBE_PACKAGE else "browser")
                        put("search_query", searchQuery)
                        put("fallback_used", !isYouTubeInstalled)
                        put(
                            "message",
                            if (isYouTubeInstalled) {
                                "Opened YouTube search for '$searchQuery'."
                            } else {
                                "YouTube app is not installed; opened YouTube search for '$searchQuery' in the web browser."
                            }
                        )
                    }
                    ToolExecutionOutcome(
                        callId = callId,
                        toolName = TOOL_LAUNCH_APPLICATION,
                        executed = true,
                        status = "SUCCESS",
                        errorCode = null,
                        message = payload.optString("message"),
                        spokenHinglishSummary = if (isYouTubeInstalled) {
                            "Maine YouTube par '$searchQuery' search kar diya hai."
                        } else {
                            "YouTube app installed nahi mila, isliye maine browser mein YouTube par '$searchQuery' open kar diya hai."
                        },
                        responseJson = payload
                    )
                } catch (e: Exception) {
                    buildErrorOutcome(
                        callId = callId,
                        toolName = TOOL_LAUNCH_APPLICATION,
                        errorCode = "LAUNCH_FAILED",
                        message = "Failed to open YouTube search for '$searchQuery': ${e.localizedMessage}",
                        spokenHinglishSummary = "YouTube par '$searchQuery' open karne mein error aaya."
                    )
                }
            }
        }

        // Standard "Open YouTube" without search query
        val nativeOutcome = tryLaunchPackage(YOUTUBE_PACKAGE, "YouTube")
        if (nativeOutcome != null && nativeOutcome.executed) {
            return nativeOutcome.copy(callId = callId)
        }

        if (allowBrowserFallback) {
            return try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                val payload = JSONObject().apply {
                    put("status", "SUCCESS")
                    put("executed", true)
                    put("app_name", "YouTube")
                    put("package_name", YOUTUBE_PACKAGE)
                    put("app_installed", false)
                    put("fallback_used", true)
                    put("fallback_type", "browser")
                    put("message", "YouTube native app is not installed on this device; opened https://m.youtube.com in the browser fallback.")
                }
                ToolExecutionOutcome(
                    callId = callId,
                    toolName = TOOL_LAUNCH_APPLICATION,
                    executed = true,
                    status = "SUCCESS",
                    errorCode = null,
                    message = "YouTube native app is not installed; opened YouTube in browser fallback.",
                    spokenHinglishSummary = "YouTube app phone mein installed nahi mila, isliye maine browser mein YouTube open kar diya hai.",
                    responseJson = payload
                )
            } catch (e: Exception) {
                buildErrorOutcome(
                    callId = callId,
                    toolName = TOOL_LAUNCH_APPLICATION,
                    errorCode = "APP_NOT_INSTALLED",
                    message = "YouTube app ($YOUTUBE_PACKAGE) is not installed and browser fallback failed: ${e.localizedMessage ?: "No browser available"}",
                    spokenHinglishSummary = "YouTube app installed nahi hai aur browser fallback bhi open nahi ho paya."
                )
            }
        }

        return buildErrorOutcome(
            callId = callId,
            toolName = TOOL_LAUNCH_APPLICATION,
            errorCode = "APP_NOT_INSTALLED",
            message = "YouTube app ($YOUTUBE_PACKAGE) is not installed on this device. A browser fallback (https://m.youtube.com) is available if requested.",
            spokenHinglishSummary = "Aapke phone mein YouTube app installed nahi hai. Agar aap chahein toh main browser mein YouTube open kar sakta hoon."
        )
    }

    private fun launchChromeWithVerification(
        callId: String,
        searchQuery: String,
        allowBrowserFallback: Boolean
    ): ToolExecutionOutcome {
        if (searchQuery.isNotBlank()) {
            return try {
                val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(searchQuery)}")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    if (isPackageInstalled(context.packageManager, CHROME_PACKAGE)) {
                        setPackage(CHROME_PACKAGE)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                val payload = JSONObject().apply {
                    put("status", "SUCCESS")
                    put("executed", true)
                    put("app_name", "Chrome")
                    put("package_name", CHROME_PACKAGE)
                    put("search_query", searchQuery)
                    put("message", "Opened Google Chrome and searched for '$searchQuery'.")
                }
                ToolExecutionOutcome(
                    callId = callId,
                    toolName = TOOL_LAUNCH_APPLICATION,
                    executed = true,
                    status = "SUCCESS",
                    errorCode = null,
                    message = "Opened Google Chrome and searched for '$searchQuery'.",
                    spokenHinglishSummary = "Maine Chrome mein '$searchQuery' search kar diya hai.",
                    responseJson = payload
                )
            } catch (e: Exception) {
                buildErrorOutcome(
                    callId = callId,
                    toolName = TOOL_LAUNCH_APPLICATION,
                    errorCode = "LAUNCH_FAILED",
                    message = "Failed to search '$searchQuery' in Chrome: ${e.localizedMessage}",
                    spokenHinglishSummary = "Chrome mein search open karne mein problem aayi."
                )
            }
        }

        val nativeChrome = tryLaunchPackage(CHROME_PACKAGE, "Chrome")
        if (nativeChrome != null && nativeChrome.executed) {
            return nativeChrome.copy(callId = callId)
        }

        if (allowBrowserFallback) {
            // Check if any browser handles https://www.google.com
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val canResolveBrowser = browserIntent.resolveActivity(context.packageManager) != null
            if (canResolveBrowser) {
                return try {
                    context.startActivity(browserIntent)
                    val payload = JSONObject().apply {
                        put("status", "SUCCESS")
                        put("executed", true)
                        put("app_name", "Browser")
                        put("package_name", CHROME_PACKAGE)
                        put("fallback_used", true)
                        put("message", "Chrome package not directly found; opened default Android browser.")
                    }
                    ToolExecutionOutcome(
                        callId = callId,
                        toolName = TOOL_LAUNCH_APPLICATION,
                        executed = true,
                        status = "SUCCESS",
                        errorCode = null,
                        message = "Opened default web browser.",
                        spokenHinglishSummary = "Maine web browser open kar diya hai.",
                        responseJson = payload
                    )
                } catch (_: Exception) {
                    buildErrorOutcome(
                        callId = callId,
                        toolName = TOOL_LAUNCH_APPLICATION,
                        errorCode = "APP_NOT_INSTALLED",
                        message = "Google Chrome ($CHROME_PACKAGE) is not installed on this device.",
                        spokenHinglishSummary = "Aapke phone mein Google Chrome installed nahi mila."
                    )
                }
            }
        }

        return buildErrorOutcome(
            callId = callId,
            toolName = TOOL_LAUNCH_APPLICATION,
            errorCode = "APP_NOT_INSTALLED",
            message = "Google Chrome ($CHROME_PACKAGE) is not installed on this device.",
            spokenHinglishSummary = "Aapke phone mein Google Chrome installed nahi hai."
        )
    }

    private fun tryLaunchPackage(packageName: String, displayAppName: String): ToolExecutionOutcome? {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isBlank()) return null

        val res = phoneControlsManager.launchInstalledApp(
            packageName = cleanPkg,
            appName = displayAppName,
            allowBrowserFallback = false
        )
        if (!res.success) return null

        val payload = JSONObject().apply {
            put("status", "SUCCESS")
            put("executed", true)
            put("app_name", displayAppName)
            put("package_name", cleanPkg)
            put("fallback_used", false)
            put("message", "Successfully launched $displayAppName ($cleanPkg).")
        }
        return ToolExecutionOutcome(
            callId = "",
            toolName = TOOL_LAUNCH_APPLICATION,
            executed = true,
            status = "SUCCESS",
            errorCode = null,
            message = "Successfully launched $displayAppName ($cleanPkg).",
            spokenHinglishSummary = "Maine $displayAppName open kar diya hai.",
            responseJson = payload
        )
    }

    private fun launchSystemSettingsIntent(
        callId: String,
        action: String,
        displayLabel: String
    ): ToolExecutionOutcome {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val payload = JSONObject().apply {
                put("status", "SUCCESS")
                put("executed", true)
                put("app_name", displayLabel)
                put("package_name", "com.android.settings")
                put("intent_action", action)
                put("message", "Successfully opened $displayLabel.")
            }
            ToolExecutionOutcome(
                callId = callId,
                toolName = TOOL_LAUNCH_APPLICATION,
                executed = true,
                status = "SUCCESS",
                errorCode = null,
                message = "Successfully opened $displayLabel.",
                spokenHinglishSummary = "Maine $displayLabel open kar diya hai.",
                responseJson = payload
            )
        } catch (e: Exception) {
            buildErrorOutcome(
                callId = callId,
                toolName = TOOL_LAUNCH_APPLICATION,
                errorCode = "LAUNCH_FAILED",
                message = "Could not open $displayLabel: ${e.localizedMessage ?: "Settings unavailable"}",
                spokenHinglishSummary = "$displayLabel open karne mein problem aayi."
            )
        }
    }

    private fun executeGetScreenContext(callId: String): ToolExecutionOutcome {
        val snapshot = accessibilityBridge.getScreenContext(context)
        val json = snapshot.toJson()
        val executedOk = snapshot.serviceEnabled && snapshot.errorCode == null
        val spoken = if (executedOk) {
            "Current screen (${snapshot.activePackageName.ifEmpty { "active app" }}) par ${snapshot.elements.size} UI elements dikh rahe hain."
        } else if (!snapshot.serviceEnabled) {
            "JARVIS Accessibility Service abhi disabled hai. Kripya Android Accessibility Settings mein 'JARVIS AI Live Screen Agent' on karein."
        } else {
            snapshot.message
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = TOOL_GET_SCREEN_CONTEXT,
            executed = executedOk,
            status = if (executedOk) "SUCCESS" else "ERROR",
            errorCode = snapshot.errorCode,
            message = snapshot.message,
            spokenHinglishSummary = spoken,
            responseJson = json
        )
    }

    private fun executeClickAccessibleElement(
        callId: String,
        args: JSONObject
    ): ToolExecutionOutcome {
        val targetText = args.optString("target_text", "").trim()
        val contentDescription = args.optString("content_description", "").trim()
        val viewId = args.optString("view_id", "").trim()
        val userConfirmed = args.optBoolean("user_confirmed", false)

        val combinedTarget = "$targetText $contentDescription $viewId".trim()
        if (!userConfirmed && isSensitiveActionLabel(combinedTarget)) {
            val payload = JSONObject().apply {
                put("status", "CONFIRMATION_REQUIRED")
                put("executed", false)
                put("requires_confirmation", true)
                put("error_code", "SENSITIVE_ACTION_CONFIRMATION_REQUIRED")
                put("target_element", combinedTarget)
                put(
                    "message",
                    "Clicking '$combinedTarget' is a sensitive or irreversible action (payment, message send, deletion, or security change). Ask the user for explicit voice confirmation first, then call click_accessible_element with user_confirmed=true."
                )
            }
            return ToolExecutionOutcome(
                callId = callId,
                toolName = TOOL_CLICK_ACCESSIBLE_ELEMENT,
                executed = false,
                status = "CONFIRMATION_REQUIRED",
                errorCode = "SENSITIVE_ACTION_CONFIRMATION_REQUIRED",
                message = payload.optString("message"),
                spokenHinglishSummary = "Yeh ek sensitive action ('$combinedTarget') hai. Kya aap confirm karte hain ki main is par click karoon?",
                responseJson = payload
            )
        }

        val result = accessibilityBridge.clickElement(
            context = context,
            targetText = targetText,
            contentDescription = contentDescription,
            viewId = viewId
        )
        return ToolExecutionOutcome(
            callId = callId,
            toolName = TOOL_CLICK_ACCESSIBLE_ELEMENT,
            executed = result.executed,
            status = if (result.executed) "SUCCESS" else "ERROR",
            errorCode = result.errorCode,
            message = result.message,
            spokenHinglishSummary = result.spokenHinglishSummary,
            responseJson = result.toJson()
        )
    }

    private fun executeScrollScreen(
        callId: String,
        args: JSONObject
    ): ToolExecutionOutcome {
        val direction = args.optString("direction", "down").trim().ifEmpty { "down" }
        val result = accessibilityBridge.scrollScreen(context, direction)
        return ToolExecutionOutcome(
            callId = callId,
            toolName = TOOL_SCROLL_SCREEN,
            executed = result.executed,
            status = if (result.executed) "SUCCESS" else "ERROR",
            errorCode = result.errorCode,
            message = result.message,
            spokenHinglishSummary = result.spokenHinglishSummary,
            responseJson = result.toJson()
        )
    }

    private fun executeNavigateBack(callId: String): ToolExecutionOutcome {
        val result = accessibilityBridge.navigateBack(context)
        return ToolExecutionOutcome(
            callId = callId,
            toolName = TOOL_NAVIGATE_BACK,
            executed = result.executed,
            status = if (result.executed) "SUCCESS" else "ERROR",
            errorCode = result.errorCode,
            message = result.message,
            spokenHinglishSummary = result.spokenHinglishSummary,
            responseJson = result.toJson()
        )
    }

    private fun executeNavigateHome(callId: String): ToolExecutionOutcome {
        val result = accessibilityBridge.navigateHome(context)
        return ToolExecutionOutcome(
            callId = callId,
            toolName = TOOL_NAVIGATE_HOME,
            executed = result.executed,
            status = if (result.executed) "SUCCESS" else "ERROR",
            errorCode = result.errorCode,
            message = result.message,
            spokenHinglishSummary = result.spokenHinglishSummary,
            responseJson = result.toJson()
        )
    }

    private fun executeDirectionalGesture(callId: String, args: JSONObject): ToolExecutionOutcome {
        val direction = args.optString("direction", "down").trim().ifEmpty { "down" }
        val gestureType = args.optString("type", "scroll").trim().lowercase()
        val result = if (gestureType == "swipe") {
            accessibilityBridge.swipeScreen(context, direction)
        } else {
            accessibilityBridge.scrollScreen(context, direction)
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = TOOL_DIRECTIONAL_GESTURE,
            executed = result.executed,
            status = if (result.executed) "SUCCESS" else "ERROR",
            errorCode = result.errorCode,
            message = result.message,
            spokenHinglishSummary = result.spokenHinglishSummary,
            responseJson = result.toJson()
        )
    }

    private fun executeTypeText(callId: String, args: JSONObject): ToolExecutionOutcome {
        val text = args.optString("text", "")
        val replace = args.optBoolean("replace_existing", false)
        val submit = args.optBoolean("submit_after", false)
        val result = accessibilityBridge.typeText(context, text, replace, submit)
        return ToolExecutionOutcome(
            callId = callId,
            toolName = TOOL_TYPE_TEXT,
            executed = result.executed,
            status = if (result.executed) "SUCCESS" else "ERROR",
            errorCode = result.errorCode,
            message = result.message,
            spokenHinglishSummary = result.spokenHinglishSummary,
            responseJson = result.toJson()
        )
    }

    private fun executeFocusInput(callId: String, args: JSONObject): ToolExecutionOutcome {
        val targetLabel = args.optString("target_label", "").takeIf { it.isNotBlank() }
        val result = accessibilityBridge.focusInputField(context, targetLabel)
        return ToolExecutionOutcome(
            callId = callId,
            toolName = TOOL_FOCUS_INPUT,
            executed = result.executed,
            status = if (result.executed) "SUCCESS" else "ERROR",
            errorCode = result.errorCode,
            message = result.message,
            spokenHinglishSummary = result.spokenHinglishSummary,
            responseJson = result.toJson()
        )
    }

    private fun executeClearText(callId: String): ToolExecutionOutcome {
        val result = accessibilityBridge.clearText(context)
        return ToolExecutionOutcome(
            callId = callId,
            toolName = TOOL_CLEAR_TEXT,
            executed = result.executed,
            status = if (result.executed) "SUCCESS" else "ERROR",
            errorCode = result.errorCode,
            message = result.message,
            spokenHinglishSummary = result.spokenHinglishSummary,
            responseJson = result.toJson()
        )
    }

    private fun executeGlobalAction(callId: String, args: JSONObject): ToolExecutionOutcome {
        val action = args.optString("action", "back").trim().lowercase()
        val result = when (action) {
            "back" -> accessibilityBridge.navigateBack(context)
            "home" -> accessibilityBridge.navigateHome(context)
            "recents", "recent_apps" -> accessibilityBridge.openRecents(context)
            "notifications" -> accessibilityBridge.openNotifications(context)
            "quick_settings" -> accessibilityBridge.openQuickSettings(context)
            else -> accessibilityBridge.navigateBack(context)
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = TOOL_GLOBAL_ACTION,
            executed = result.executed,
            status = if (result.executed) "SUCCESS" else "ERROR",
            errorCode = result.errorCode,
            message = result.message,
            spokenHinglishSummary = result.spokenHinglishSummary,
            responseJson = result.toJson()
        )
    }

    private fun executeReadScreenText(callId: String): ToolExecutionOutcome {
        val text = accessibilityBridge.readScreenText(context)
        val payload = JSONObject().apply {
            put("status", "SUCCESS")
            put("text", text)
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = TOOL_READ_SCREEN_TEXT,
            executed = true,
            status = "SUCCESS",
            errorCode = null,
            message = "Read screen text successfully",
            spokenHinglishSummary = "Maine screen par dikhne wala text read kar liya hai.",
            responseJson = payload
        )
    }

    /**
     * Enforces Section 7: Action Safety & Confirmation for sensitive or irreversible actions.
     */
    private fun executeSensitiveActionCheck(
        callId: String,
        args: JSONObject
    ): ToolExecutionOutcome {
        val actionType = args.optString("action_type", "").trim()
        val targetDescription = args.optString("target_description", "").trim()
        val userConfirmed = args.optBoolean("user_confirmed", false)

        if (actionType.isEmpty() && targetDescription.isEmpty()) {
            return buildErrorOutcome(
                callId = callId,
                toolName = TOOL_SENSITIVE_ACTION_CONFIRMATION,
                errorCode = "MALFORMED_TOOL_CALL",
                message = "Missing action_type or target_description for sensitive action check.",
                spokenHinglishSummary = "Sensitive action ki details missing hain."
            )
        }

        if (!userConfirmed) {
            val payload = JSONObject().apply {
                put("status", "CONFIRMATION_REQUIRED")
                put("executed", false)
                put("requires_confirmation", true)
                put("error_code", "SENSITIVE_ACTION_CONFIRMATION_REQUIRED")
                put("action_type", actionType)
                put("target_description", targetDescription)
                put(
                    "message",
                    "Sensitive action '$actionType' ($targetDescription) requires explicit user confirmation before execution. Ask the user clearly if they want to proceed."
                )
            }
            return ToolExecutionOutcome(
                callId = callId,
                toolName = TOOL_SENSITIVE_ACTION_CONFIRMATION,
                executed = false,
                status = "CONFIRMATION_REQUIRED",
                errorCode = "SENSITIVE_ACTION_CONFIRMATION_REQUIRED",
                message = payload.optString("message"),
                spokenHinglishSummary = "Yeh ek sensitive action hai ($targetDescription). Kya aap confirm karte hain ki main aage badhoon?",
                responseJson = payload
            )
        }

        val payload = JSONObject().apply {
            put("status", "SUCCESS")
            put("executed", true)
            put("user_confirmed", true)
            put("action_type", actionType)
            put("target_description", targetDescription)
            put("message", "User explicitly confirmed sensitive action '$actionType' ($targetDescription).")
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = TOOL_SENSITIVE_ACTION_CONFIRMATION,
            executed = true,
            status = "SUCCESS",
            errorCode = null,
            message = "User confirmed '$actionType' ($targetDescription).",
            spokenHinglishSummary = "Confirmation mil gaya hai.",
            responseJson = payload
        )
    }

    private fun isSensitiveActionLabel(text: String): Boolean {
        val lower = text.lowercase()
        val sensitivePatterns = listOf(
            "pay now", "send money", "make payment", "place order", "buy now", "checkout",
            "confirm payment", "transfer money", "upi pin", "delete account", "delete all",
            "factory reset", "erase data", "uninstall", "post publicly", "publish post",
            "send message", "send email", "disable lock", "change password"
        )
        return sensitivePatterns.any { lower.contains(it) }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun buildErrorOutcome(
        callId: String,
        toolName: String,
        errorCode: String,
        message: String,
        spokenHinglishSummary: String
    ): ToolExecutionOutcome {
        val payload = JSONObject().apply {
            put("status", "ERROR")
            put("executed", false)
            put("error_code", errorCode)
            put("message", message)
            put("spoken_summary", spokenHinglishSummary)
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = toolName,
            executed = false,
            status = "ERROR",
            errorCode = errorCode,
            message = message,
            spokenHinglishSummary = spokenHinglishSummary,
            responseJson = payload
        )
    }

    private fun executeGetSystemTelemetry(callId: String): ToolExecutionOutcome {
        val telemetry = phoneControlsManager.systemStatusManager.toTelemetryJson()
        val batteryObj = telemetry.optJSONObject("battery")
        val batteryPct = batteryObj?.optInt("level_percent", 100) ?: 100
        val isCharging = batteryObj?.optBoolean("is_charging", false) ?: false
        val networkType = telemetry.optJSONObject("network")?.optString("type", "UNKNOWN") ?: "UNKNOWN"

        val spoken = "Aapke device ki battery $batteryPct percent hai${if (isCharging) " aur charging par hai" else ""}. Network $networkType connected hai."

        val responseJson = JSONObject().apply {
            put("status", "SUCCESS")
            put("telemetry", telemetry)
            put("summary", "Battery: $batteryPct%, Network: $networkType")
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = TOOL_GET_SYSTEM_TELEMETRY,
            executed = true,
            status = "SUCCESS",
            message = "System telemetry retrieved successfully.",
            spokenHinglishSummary = spoken,
            responseJson = responseJson
        )
    }

    private fun executeAdjustSystemVolume(callId: String, args: JSONObject): ToolExecutionOutcome {
        val direction = args.optString("direction", "SHOW_PANEL")
        val (success, message) = phoneControlsManager.systemStatusManager.adjustVolume(direction)
        val audioInfo = phoneControlsManager.systemStatusManager.getAudioVolumeInfo()

        val responseJson = JSONObject().apply {
            put("status", if (success) "SUCCESS" else "ERROR")
            put("direction", direction)
            put("volume_percent", audioInfo.currentVolumePercent)
            put("is_muted", audioInfo.isMuted)
            put("message", message)
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = TOOL_ADJUST_SYSTEM_VOLUME,
            executed = success,
            status = if (success) "SUCCESS" else "ERROR",
            message = message,
            spokenHinglishSummary = message,
            responseJson = responseJson
        )
    }

    private fun executeControlMediaPlayback(callId: String, args: JSONObject): ToolExecutionOutcome {
        val command = args.optString("command", "PLAY_PAUSE")
        val res = phoneControlsManager.mediaControllerHelper.executeMediaAction(command)
        val responseJson = JSONObject().apply {
            put("status", if (res.executed) "SUCCESS" else "ERROR")
            put("command", command)
            put("message", res.message)
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = TOOL_CONTROL_MEDIA_PLAYBACK,
            executed = res.executed,
            status = if (res.executed) "SUCCESS" else "ERROR",
            message = res.message,
            spokenHinglishSummary = res.spokenHinglishSummary,
            responseJson = responseJson
        )
    }

    private fun executeSendCommunication(callId: String, args: JSONObject): ToolExecutionOutcome {
        val platform = args.optString("platform", "WHATSAPP").uppercase()
        val recipient = args.optString("recipient", "").trim()
        val message = args.optString("message", "").trim()
        val subject = args.optString("subject", "").trim()
        val userConfirmed = args.optBoolean("user_confirmed", false)

        val res = when (platform) {
            "WHATSAPP" -> phoneControlsManager.communicationHelper.handleWhatsAppMessage(
                recipientNumberOrName = recipient,
                messageText = message,
                userConfirmed = userConfirmed
            )
            "TELEGRAM" -> phoneControlsManager.communicationHelper.handleTelegramMessage(
                messageText = message,
                userConfirmed = userConfirmed
            )
            "GMAIL", "EMAIL" -> phoneControlsManager.communicationHelper.handleEmailCompose(
                recipientEmail = recipient,
                subject = subject,
                body = message,
                userConfirmed = userConfirmed
            )
            "SMS" -> phoneControlsManager.communicationHelper.handleSmsCompose(
                phoneNumber = recipient,
                messageBody = message,
                userConfirmed = userConfirmed
            )
            else -> phoneControlsManager.communicationHelper.handleWhatsAppMessage(
                recipientNumberOrName = recipient,
                messageText = message,
                userConfirmed = userConfirmed
            )
        }

        val status = if (res.requiresConfirmation) "CONFIRMATION_REQUIRED" else if (res.executed) "SUCCESS" else "ERROR"
        val responseJson = JSONObject().apply {
            put("status", status)
            put("platform", platform)
            put("recipient", recipient)
            put("requires_confirmation", res.requiresConfirmation)
            put("message", res.message)
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = TOOL_SEND_COMMUNICATION,
            executed = res.executed,
            status = status,
            errorCode = if (res.requiresConfirmation) "CONFIRMATION_REQUIRED" else if (!res.executed) "FAILED" else null,
            message = res.message,
            spokenHinglishSummary = res.spokenHinglishSummary,
            responseJson = responseJson
        )
    }

    private fun executeOpenPhoneDialer(callId: String, args: JSONObject): ToolExecutionOutcome {
        val phoneNumber = args.optString("phone_number", "").trim()
        val res = phoneControlsManager.communicationHelper.handlePhoneDial(phoneNumber)
        val responseJson = JSONObject().apply {
            put("status", if (res.executed) "SUCCESS" else "ERROR")
            put("phone_number", phoneNumber)
            put("message", res.message)
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = TOOL_OPEN_PHONE_DIALER,
            executed = res.executed,
            status = if (res.executed) "SUCCESS" else "ERROR",
            message = res.message,
            spokenHinglishSummary = res.spokenHinglishSummary,
            responseJson = responseJson
        )
    }

    private fun executeOpenSpotify(callId: String, args: JSONObject): ToolExecutionOutcome {
        val query = args.optString("search_query", args.optString("query", "")).trim()
        val res = phoneControlsManager.mediaControllerHelper.openSpotifySearch(query)
        val responseJson = JSONObject().apply {
            put("status", if (res.executed) "SUCCESS" else "ERROR")
            put("query", query)
            put("message", res.message)
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = TOOL_OPEN_SPOTIFY_MUSIC,
            executed = res.executed,
            status = if (res.executed) "SUCCESS" else "ERROR",
            message = res.message,
            spokenHinglishSummary = res.spokenHinglishSummary,
            responseJson = responseJson
        )
    }

    companion object {
        private const val TAG = "LiveAgentToolExecutor"

        const val TOOL_LAUNCH_APPLICATION = ToolRegistry.TOOL_LAUNCH_APPLICATION
        const val TOOL_GET_SCREEN_CONTEXT = ToolRegistry.TOOL_GET_SCREEN_CONTEXT
        const val TOOL_CLICK_ACCESSIBLE_ELEMENT = ToolRegistry.TOOL_CLICK_ACCESSIBLE_ELEMENT
        const val TOOL_SCROLL_SCREEN = ToolRegistry.TOOL_SCROLL_SCREEN
        const val TOOL_NAVIGATE_BACK = ToolRegistry.TOOL_NAVIGATE_BACK
        const val TOOL_NAVIGATE_HOME = ToolRegistry.TOOL_NAVIGATE_HOME
        const val TOOL_SENSITIVE_ACTION_CONFIRMATION = ToolRegistry.TOOL_SENSITIVE_ACTION_CONFIRMATION
        const val TOOL_DIRECTIONAL_GESTURE = ToolRegistry.TOOL_DIRECTIONAL_GESTURE
        const val TOOL_TYPE_TEXT = ToolRegistry.TOOL_TYPE_TEXT
        const val TOOL_FOCUS_INPUT = ToolRegistry.TOOL_FOCUS_INPUT
        const val TOOL_CLEAR_TEXT = ToolRegistry.TOOL_CLEAR_TEXT
        const val TOOL_GLOBAL_ACTION = ToolRegistry.TOOL_GLOBAL_ACTION
        const val TOOL_READ_SCREEN_TEXT = ToolRegistry.TOOL_READ_SCREEN_TEXT
        const val TOOL_GET_SYSTEM_TELEMETRY = ToolRegistry.TOOL_GET_SYSTEM_TELEMETRY
        const val TOOL_ADJUST_SYSTEM_VOLUME = ToolRegistry.TOOL_ADJUST_SYSTEM_VOLUME
        const val TOOL_CONTROL_MEDIA_PLAYBACK = ToolRegistry.TOOL_CONTROL_MEDIA_PLAYBACK
        const val TOOL_SEND_COMMUNICATION = ToolRegistry.TOOL_SEND_COMMUNICATION
        const val TOOL_OPEN_PHONE_DIALER = ToolRegistry.TOOL_OPEN_PHONE_DIALER
        const val TOOL_OPEN_SPOTIFY_MUSIC = ToolRegistry.TOOL_OPEN_SPOTIFY_MUSIC

        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        const val CHROME_PACKAGE = "com.android.chrome"

        private val KNOWN_APP_PACKAGES: Map<String, Pair<List<String>, String>> = mapOf(
            "whatsapp" to (listOf("com.whatsapp", "com.whatsapp.w4b") to "WhatsApp"),
            "व्हाट्सएप" to (listOf("com.whatsapp", "com.whatsapp.w4b") to "WhatsApp"),
            "calculator" to (listOf("com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator") to "Calculator"),
            "कैलकुलेटर" to (listOf("com.google.android.calculator", "com.android.calculator2") to "Calculator"),
            "play store" to (listOf("com.android.vending") to "Google Play Store"),
            "google play" to (listOf("com.android.vending") to "Google Play Store"),
            "playstore" to (listOf("com.android.vending") to "Google Play Store"),
            "spotify" to (listOf("com.spotify.music") to "Spotify"),
            "instagram" to (listOf("com.instagram.android", "com.instagram.lite") to "Instagram"),
            "gmail" to (listOf("com.google.android.gm") to "Gmail"),
            "maps" to (listOf("com.google.android.apps.maps") to "Google Maps"),
            "google maps" to (listOf("com.google.android.apps.maps") to "Google Maps"),
            "calendar" to (listOf("com.google.android.calendar", "com.android.calendar", "com.samsung.android.calendar") to "Calendar"),
            "clock" to (listOf("com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage") to "Clock"),
            "photos" to (listOf("com.google.android.apps.photos", "com.android.gallery3d", "com.sec.android.gallery3d") to "Photos"),
            "gallery" to (listOf("com.google.android.apps.photos", "com.sec.android.gallery3d", "com.miui.gallery") to "Gallery"),
            "contacts" to (listOf("com.google.android.contacts", "com.android.contacts") to "Contacts"),
            "phone" to (listOf("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer") to "Phone"),
            "messages" to (listOf("com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging") to "Messages"),
            "files" to (listOf("com.google.android.apps.nbu.files", "com.android.documentsui") to "Files")
        )

        /**
         * Builds the official `tools` JSON array (`[{"functionDeclarations": [...]}]`)
         * for `BidiGenerateContentSetup`.
         */
        fun buildToolsSetupJsonArray(): JSONArray {
            return JSONArray().put(
                JSONObject().put("functionDeclarations", ToolRegistry.buildDeclarationsJsonArray())
            )
        }

        private fun legacyFunctionDeclarations(): JSONArray {
            val declarations = JSONArray().apply {
                // 1. launch_application
                put(
                    JSONObject().apply {
                        put("name", TOOL_LAUNCH_APPLICATION)
                        put(
                            "description",
                            "Launches an installed Android application (such as YouTube, Chrome, Settings, Calculator, WhatsApp, Spotify, Gmail, Maps, or any installed app) on the user's phone, with optional search query support."
                        )
                        put(
                            "parameters",
                            JSONObject().apply {
                                put("type", "OBJECT")
                                put(
                                    "properties",
                                    JSONObject().apply {
                                        put(
                                            "app_name",
                                            JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Display name of the Android app to open, e.g. 'YouTube', 'Chrome', 'Settings', 'Calculator', 'WhatsApp'.")
                                            }
                                        )
                                        put(
                                            "package_name",
                                            JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Optional Android package name if known, e.g. 'com.google.android.youtube' or 'com.android.chrome'.")
                                            }
                                        )
                                        put(
                                            "search_query",
                                            JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Optional search query when the user asks to open YouTube or Chrome and search for a specific topic.")
                                            }
                                        )
                                    }
                                )
                                put("required", JSONArray().put("app_name"))
                            }
                        )
                    }
                )

                // 2. get_screen_context
                put(
                    JSONObject().apply {
                        put("name", TOOL_GET_SCREEN_CONTEXT)
                        put(
                            "description",
                            "Inspects the current Android screen via the user-enabled JARVIS Accessibility Service and returns the active app package name and visible UI elements (buttons, text, labels, view IDs, and clickable/scrollable states)."
                        )
                        put(
                            "parameters",
                            JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject())
                            }
                        )
                    }
                )

                // 3. click_accessible_element
                put(
                    JSONObject().apply {
                        put("name", TOOL_CLICK_ACCESSIBLE_ELEMENT)
                        put(
                            "description",
                            "Clicks a visible button or UI element on the current Android screen using its visible text, content description, or view ID via the Android Accessibility Service."
                        )
                        put(
                            "parameters",
                            JSONObject().apply {
                                put("type", "OBJECT")
                                put(
                                    "properties",
                                    JSONObject().apply {
                                        put(
                                            "target_text",
                                            JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Visible text label of the button or UI element to click, e.g. 'Search', 'Subscribe', 'Library', 'OK'.")
                                            }
                                        )
                                        put(
                                            "content_description",
                                            JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Optional accessibility contentDescription of the icon/button to click, e.g. 'Search', 'Back', 'More options'.")
                                            }
                                        )
                                        put(
                                            "view_id",
                                            JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Optional Android view resource ID to click if known.")
                                            }
                                        )
                                        put(
                                            "user_confirmed",
                                            JSONObject().apply {
                                                put("type", "BOOLEAN")
                                                put("description", "Set to true ONLY if the user has explicitly confirmed executing a sensitive or irreversible action.")
                                            }
                                        )
                                    }
                                )
                            }
                        )
                    }
                )

                // 4. scroll_screen
                put(
                    JSONObject().apply {
                        put("name", TOOL_SCROLL_SCREEN)
                        put(
                            "description",
                            "Scrolls the current scrollable list or page on the Android screen up or down using the Android Accessibility Service."
                        )
                        put(
                            "parameters",
                            JSONObject().apply {
                                put("type", "OBJECT")
                                put(
                                    "properties",
                                    JSONObject().apply {
                                        put(
                                            "direction",
                                            JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Direction to scroll: 'down' (forward) or 'up' (backward).")
                                            }
                                        )
                                    }
                                )
                                put("required", JSONArray().put("direction"))
                            }
                        )
                    }
                )

                // 5. navigate_back
                put(
                    JSONObject().apply {
                        put("name", TOOL_NAVIGATE_BACK)
                        put(
                            "description",
                            "Performs the Android global Back action to return to the previous screen."
                        )
                        put(
                            "parameters",
                            JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject())
                            }
                        )
                    }
                )

                // 6. navigate_home
                put(
                    JSONObject().apply {
                        put("name", TOOL_NAVIGATE_HOME)
                        put(
                            "description",
                            "Performs the Android global Home action to return to the phone's Home screen."
                        )
                        put(
                            "parameters",
                            JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject())
                            }
                        )
                    }
                )

                // 7. request_sensitive_action_confirmation
                put(
                    JSONObject().apply {
                        put("name", TOOL_SENSITIVE_ACTION_CONFIRMATION)
                        put(
                            "description",
                            "Verifies user confirmation before executing any sensitive or irreversible action (sending messages/emails, payments/purchases, deleting user data, posting publicly, or changing security settings)."
                        )
                        put(
                            "parameters",
                            JSONObject().apply {
                                put("type", "OBJECT")
                                put(
                                    "properties",
                                    JSONObject().apply {
                                        put(
                                            "action_type",
                                            JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Category of sensitive action: 'SEND_MESSAGE', 'PAYMENT', 'DELETE_DATA', 'PUBLIC_POST', 'SECURITY_SETTING'.")
                                            }
                                        )
                                        put(
                                            "target_description",
                                            JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Clear description of the sensitive action to confirm with the user.")
                                            }
                                        )
                                        put(
                                            "user_confirmed",
                                            JSONObject().apply {
                                                put("type", "BOOLEAN")
                                                put("description", "True only after the user explicitly confirmed this specific sensitive action.")
                                            }
                                        )
                                    }
                                )
                                put(
                                    "required",
                                    JSONArray().put("action_type").put("target_description")
                                )
                            }
                        )
                    }
                )

                // 8. directional_gesture (Scroll / Swipe in any direction, Tap, Long Press)
                put(
                    JSONObject().apply {
                        put("name", TOOL_DIRECTIONAL_GESTURE)
                        put(
                            "description",
                            "Performs directional screen gestures via AccessibilityService: 'scroll' or 'swipe' in direction 'up', 'down', 'left', or 'right', or precision 'tap' or 'long_press' at specified coordinates."
                        )
                        put(
                            "parameters",
                            JSONObject().apply {
                                put("type", "OBJECT")
                                put(
                                    "properties",
                                    JSONObject().apply {
                                        put(
                                            "action",
                                            JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Gesture action: 'scroll', 'swipe', 'tap', or 'long_press'.")
                                            }
                                        )
                                        put(
                                            "direction",
                                            JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Direction for scroll/swipe: 'up', 'down', 'left', or 'right'.")
                                            }
                                        )
                                        put(
                                            "duration_ms",
                                            JSONObject().apply {
                                                put("type", "INTEGER")
                                                put("description", "Optional gesture duration in ms (default 300).")
                                            }
                                        )
                                    }
                                )
                            }
                        )
                    }
                )

                // 9. type_text (Universal voice typing)
                put(
                    JSONObject().apply {
                        put("name", TOOL_TYPE_TEXT)
                        put(
                            "description",
                            "Types text into the active or targeted input field using Android Accessibility ACTION_SET_TEXT and clipboard fallback."
                        )
                        put(
                            "parameters",
                            JSONObject().apply {
                                put("type", "OBJECT")
                                put(
                                    "properties",
                                    JSONObject().apply {
                                        put(
                                            "text",
                                            JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The text to input into the field.")
                                            }
                                        )
                                        put(
                                            "target_label",
                                            JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Optional label, hint, or description of the target input field (e.g. 'Search', 'Comment').")
                                            }
                                        )
                                        put(
                                            "append",
                                            JSONObject().apply {
                                                put("type", "BOOLEAN")
                                                put("description", "If true, appends text to existing content; if false, replaces content.")
                                            }
                                        )
                                        put(
                                            "submit",
                                            JSONObject().apply {
                                                put("type", "BOOLEAN")
                                                put("description", "If true, clicks Search/Send/Enter after entering text.")
                                            }
                                        )
                                    }
                                )
                                put("required", JSONArray().put("text"))
                            }
                        )
                    }
                )

                // 10. focus_input
                put(
                    JSONObject().apply {
                        put("name", TOOL_FOCUS_INPUT)
                        put(
                            "description",
                            "Focuses an editable input field by hint, label, or viewId."
                        )
                        put(
                            "parameters",
                            JSONObject().apply {
                                put("type", "OBJECT")
                                put(
                                    "properties",
                                    JSONObject().apply {
                                        put(
                                            "target_label",
                                            JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Optional label or hint of the field to focus.")
                                            }
                                        )
                                    }
                                )
                            }
                        )
                    }
                )

                // 11. clear_text
                put(
                    JSONObject().apply {
                        put("name", TOOL_CLEAR_TEXT)
                        put(
                            "description",
                            "Clears text from the active or specified input field."
                        )
                        put(
                            "parameters",
                            JSONObject().apply {
                                put("type", "OBJECT")
                                put(
                                    "properties",
                                    JSONObject().apply {
                                        put(
                                            "target_label",
                                            JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Optional label or hint of the field to clear.")
                                            }
                                        )
                                    }
                                )
                            }
                        )
                    }
                )

                // 12. global_action
                put(
                    JSONObject().apply {
                        put("name", TOOL_GLOBAL_ACTION)
                        put(
                            "description",
                            "Performs Android system global action: 'back', 'home', 'recents', 'notifications', 'quick_settings', 'power_dialog', or 'lock_screen'."
                        )
                        put(
                            "parameters",
                            JSONObject().apply {
                                put("type", "OBJECT")
                                put(
                                    "properties",
                                    JSONObject().apply {
                                        put(
                                            "action",
                                            JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "System action name.")
                                            }
                                        )
                                    }
                                )
                                put("required", JSONArray().put("action"))
                            }
                        )
                    }
                )

                // 13. read_screen_text
                put(
                    JSONObject().apply {
                        put("name", TOOL_READ_SCREEN_TEXT)
                        put(
                            "description",
                            "Reads and returns all visible text hierarchy from the current Android screen."
                        )
                        put(
                            "parameters",
                            JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject())
                            }
                        )
                    }
                )
            }

            return JSONArray().put(
                JSONObject().put("functionDeclarations", declarations)
            )
        }

        /**
         * Builds the official Gemini Live `toolResponse` message (`BidiGenerateContentToolResponse`)
         * to send back over the WebSocket after executing one or more tool calls.
         */
        fun buildToolResponseWebSocketMessage(outcomes: List<ToolExecutionOutcome>): JSONObject {
            val responsesArray = JSONArray()
            for (outcome in outcomes) {
                responsesArray.put(
                    JSONObject().apply {
                        if (outcome.callId.isNotBlank()) {
                            put("id", outcome.callId)
                        }
                        put("name", outcome.toolName)
                        put("response", outcome.responseJson)
                    }
                )
            }
            return JSONObject().apply {
                put(
                    "toolResponse",
                    JSONObject().apply {
                        put("functionResponses", responsesArray)
                    }
                )
            }
        }
    }
}
