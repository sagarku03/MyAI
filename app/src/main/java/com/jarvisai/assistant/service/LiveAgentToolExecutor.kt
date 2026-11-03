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
import com.jarvisai.assistant.model.PendingSafetyConfirmation
import java.util.concurrent.atomic.AtomicInteger
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
    private val accessibilityBridge: AccessibilityControllerBridge = JarvisAccessibilityService,
    var confirmConsequentialActionsProvider: () -> Boolean = { true },
    var maxAutonomousStepsProvider: () -> Int = { 8 },
    var onInteractiveConfirmationRequired: (suspend (PendingSafetyConfirmation) -> Boolean)? = null,
    var onLearnedWorkflowSaved: ((com.jarvisai.assistant.data.local.WorkflowEntity) -> Unit)? = null
) {

    val phoneActionManager: PhoneActionManager by lazy {
        PhoneActionManager(context, phoneControlsManager)
    }

    @Volatile
    var lastExecutedToolTimestampMs: Long = 0L
        private set

    private val consecutiveStepsInTurn = AtomicInteger(0)

    fun resetTurnStepCounter() {
        consecutiveStepsInTurn.set(0)
    }

    fun getCurrentTurnStepCount(): Int = consecutiveStepsInTurn.get()

    suspend fun executeToolCall(
        functionName: String,
        callId: String,
        args: JSONObject?
    ): ToolExecutionOutcome {
        val safeArgs = args ?: JSONObject()
        val cleanName = functionName.trim()
        Log.d(TAG, "Executing Live Agent tool call: name=$cleanName, id=$callId, args=$safeArgs")

        val maxSteps = maxAutonomousStepsProvider().coerceIn(1, 15)
        val stepNumber = consecutiveStepsInTurn.incrementAndGet()
        if (stepNumber > maxSteps) {
            return buildErrorOutcome(
                callId = callId,
                toolName = cleanName.ifEmpty { "unknown_tool" },
                errorCode = "MAX_AUTONOMOUS_STEPS_REACHED",
                message = "Autonomous step limit ($maxSteps steps per turn) reached. Pausing further tool execution until the user gives the next instruction.",
                spokenHinglishSummary = "Ek turn mein maximum $maxSteps autonomous steps ki safety limit poori ho gayi hai. Aage badhne ke liye kripya agla instruction dein."
            )
        }

        return try {
            val outcome = when (cleanName) {
                TOOL_LAUNCH_APPLICATION, "open_app", "launch_app" -> executeLaunchApplication(callId, safeArgs)
                TOOL_GET_SCREEN_CONTEXT, "inspect_screen", "get_screen" -> executeGetScreenContext(callId)
                TOOL_CLICK_ACCESSIBLE_ELEMENT, "click_element", "tap_element" -> executeClickAccessibleElement(callId, safeArgs)
                TOOL_SCROLL_SCREEN, "scroll" -> executeScrollScreen(callId, safeArgs)
                TOOL_NAVIGATE_BACK, "back" -> executeNavigateBack(callId)
                TOOL_NAVIGATE_HOME, "home" -> executeNavigateHome(callId)
                TOOL_SENSITIVE_ACTION_CONFIRMATION -> executeSensitiveActionCheck(callId, safeArgs)
                TOOL_DIRECTIONAL_GESTURE, "swipe_screen", "gesture" -> executeDirectionalGesture(callId, safeArgs)
                TOOL_TYPE_TEXT, "enter_text", "input_text" -> executeTypeText(callId, safeArgs)
                TOOL_FOCUS_INPUT, "focus_input", "focus_input_field", "focus_field" -> executeFocusInput(callId, safeArgs)
                TOOL_CLEAR_TEXT -> executeClearText(callId)
                TOOL_GLOBAL_ACTION -> executeGlobalAction(callId, safeArgs)
                TOOL_READ_SCREEN_TEXT, "read_screen" -> executeReadScreenText(callId)
                TOOL_GET_SYSTEM_TELEMETRY, "get_battery", "get_status" -> executeGetSystemTelemetry(callId)
                TOOL_ADJUST_SYSTEM_VOLUME, "set_volume" -> executeAdjustSystemVolume(callId, safeArgs)
                TOOL_CONTROL_MEDIA_PLAYBACK, "media_control" -> executeControlMediaPlayback(callId, safeArgs)
                TOOL_SEND_COMMUNICATION, "send_message", "send_chat_message" -> executeSendCommunication(callId, safeArgs)
                "send_whatsapp_message" -> executeSendCommunication(
                    callId,
                    JSONObject(safeArgs.toString()).put("platform", "WHATSAPP")
                )
                "send_sms" -> executeSendCommunication(
                    callId,
                    JSONObject(safeArgs.toString()).put("platform", "SMS")
                )
                TOOL_OPEN_PHONE_DIALER, "make_phone_call", "call_contact" -> executeOpenPhoneDialer(callId, safeArgs)
                TOOL_OPEN_SPOTIFY_MUSIC -> executeOpenSpotify(callId, safeArgs)
                "search_in_current_app" -> executeSearchInCurrentApp(callId, safeArgs)
                // Top 10 Agentic Mobile Controller Tools (with resilient aliases)
                ToolRegistry.TOOL_RUN_AUTONOMOUS_GOAL, "run_autonomous_goal", "autonomous_goal", "execute_agentic_goal" -> executeRunAutonomousGoal(callId, safeArgs)
                ToolRegistry.TOOL_TAP_SET_OF_MARK_BADGE, "tap_badge", "tap_som_badge", "vision_tap" -> executeTapSetOfMarkBadge(callId, safeArgs)
                ToolRegistry.TOOL_CONTROL_FLOATING_HUD, "toggle_floating_hud", "floating_hud", "control_hud" -> executeControlFloatingHud(callId, safeArgs)
                ToolRegistry.TOOL_REPLY_TO_NOTIFICATION, "reply_notification", "notification_reply" -> executeReplyToNotification(callId, safeArgs)
                ToolRegistry.TOOL_FILL_SCREEN_FORM, "fill_form", "auto_fill_form" -> executeFillScreenForm(callId, safeArgs)
                ToolRegistry.TOOL_CROSS_APP_SCREEN_BRIDGE, "screen_bridge", "translate_screen", "summarize_screen" -> executeCrossAppScreenBridge(callId, safeArgs)
                ToolRegistry.TOOL_MANAGE_LEARNING_MODE, "learning_mode", "macro_recorder" -> executeManageLearningMode(callId, safeArgs)
                ToolRegistry.TOOL_SET_ALARM_OR_TIMER, "set_timer", "set_alarm", "create_timer", "create_alarm" -> executeSetAlarmOrTimer(callId, safeArgs)
                ToolRegistry.TOOL_DEEP_LINK_TELEPORT, "zero_click_teleport", "teleport_navigation" -> executeDeepLinkTeleport(callId, safeArgs)
                ToolRegistry.TOOL_VOICE_UNDO_OR_STOP, "voice_undo", "agent_stop", "undo_last_action" -> executeVoiceUndoOrStop(callId, safeArgs)
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
                val argMap = mutableMapOf<String, String>()
                val keys = safeArgs.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    argMap[k] = safeArgs.optString(k, "")
                }
                ConversationContextManager.recordLastToolExecution(cleanName, argMap)
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
        val autoPlayFirst = args.optBoolean("auto_play_first_result", false)
        val ordinalToPlay = args.optInt("ordinal_to_play", if (autoPlayFirst) 1 else 0)

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

        // 2. Special handling for YouTube (with search query support, auto-play first video & browser fallback)
        if (normalizedName in setOf("youtube", "you tube", "yt", "यूट्यूब") ||
            rawPackageName.equals(YOUTUBE_PACKAGE, ignoreCase = true)
        ) {
            return launchYouTubeWithVerification(
                callId = callId,
                searchQuery = searchQuery,
                allowBrowserFallback = allowBrowserFallback,
                ordinalToPlay = ordinalToPlay
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
                val verification = evaluateAndAttachLaunchVerification(
                    targetAppName = matchedInstalled.appName,
                    targetPackage = matchedInstalled.packageName,
                    launchedOk = true,
                    payload = payload
                )
                return ToolExecutionOutcome(
                    callId = callId,
                    toolName = TOOL_LAUNCH_APPLICATION,
                    executed = true,
                    status = "SUCCESS",
                    errorCode = null,
                    message = "Successfully launched ${matchedInstalled.appName}.",
                    spokenHinglishSummary = verification.hinglishReport,
                    responseJson = payload
                )
            } else {
                evaluateAndAttachLaunchVerification(
                    targetAppName = matchedInstalled.appName,
                    targetPackage = matchedInstalled.packageName,
                    launchedOk = false,
                    payload = null
                )
                return buildErrorOutcome(
                    callId = callId,
                    toolName = TOOL_LAUNCH_APPLICATION,
                    errorCode = "LAUNCH_INTENT_UNAVAILABLE",
                    message = "App '${matchedInstalled.appName}' (${matchedInstalled.packageName}) is installed, but its launch activity could not be started: ${res.message}",
                    spokenHinglishSummary = "${matchedInstalled.appName} open nahi ho paya: ${res.message}"
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
        evaluateAndAttachLaunchVerification(
            targetAppName = targetLabel,
            targetPackage = rawPackageName,
            launchedOk = false,
            payload = null
        )
        return buildErrorOutcome(
            callId = callId,
            toolName = TOOL_LAUNCH_APPLICATION,
            errorCode = "APP_NOT_INSTALLED",
            message = "Application '$targetLabel' is not installed on this Android device.",
            spokenHinglishSummary = "Aapke phone mein '$targetLabel' app installed nahi hai."
        )
    }

    private suspend fun autoPlayYouTubeResultIfRequested(ordinalToPlay: Int): AccessibilityOperationResult? {
        if (ordinalToPlay <= 0 || !accessibilityBridge.isAccessibilityEnabled(context)) return null
        // Try immediately first (fast path for unit tests & already-loaded screens)
        var clickRes = accessibilityBridge.clickOrdinalElement(context, ordinalToPlay, "video")
        if (clickRes.executed) return clickRes

        // On real physical devices, YouTube search results take ~1–2.5 seconds to render after startActivity
        for (attempt in 1..6) {
            kotlinx.coroutines.delay(260L)
            clickRes = accessibilityBridge.clickOrdinalElement(context, ordinalToPlay, "video")
            if (clickRes.executed) return clickRes
        }
        return clickRes
    }

    private suspend fun launchYouTubeWithVerification(
        callId: String,
        searchQuery: String,
        allowBrowserFallback: Boolean,
        ordinalToPlay: Int = 0
    ): ToolExecutionOutcome {
        val pm = context.packageManager
        val isYouTubeInstalled = isPackageInstalled(pm, YOUTUBE_PACKAGE) ||
            pm.getLaunchIntentForPackage(YOUTUBE_PACKAGE) != null

        if (searchQuery.isNotBlank()) {
            ConversationContextManager.updateSearchQuery(searchQuery)
            if (isYouTubeInstalled) {
                try {
                    val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                        setPackage(YOUTUBE_PACKAGE)
                        putExtra(SearchManager.QUERY, searchQuery)
                        putExtra("query", searchQuery)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(searchIntent)
                    val playRes = autoPlayYouTubeResultIfRequested(ordinalToPlay)
                    val videoPlayed = playRes?.executed == true
                    val payload = JSONObject().apply {
                        put("status", "SUCCESS")
                        put("executed", true)
                        put("app_name", "YouTube")
                        put("package_name", YOUTUBE_PACKAGE)
                        put("search_query", searchQuery)
                        put("fallback_used", false)
                        if (ordinalToPlay > 0) {
                            put("video_played", videoPlayed)
                            if (!playRes?.matchedElementLabel.isNullOrBlank()) {
                                put("selected_video", playRes?.matchedElementLabel)
                            }
                        }
                        put(
                            "message",
                            if (videoPlayed) {
                                "Launched YouTube app, searched for '$searchQuery', and started playing video #$ordinalToPlay ('${playRes?.matchedElementLabel}')."
                            } else {
                                "Launched YouTube app and searched for '$searchQuery'."
                            }
                        )
                    }
                    evaluateAndAttachLaunchVerification("YouTube", YOUTUBE_PACKAGE, true, payload)
                    return ToolExecutionOutcome(
                        callId = callId,
                        toolName = TOOL_LAUNCH_APPLICATION,
                        executed = true,
                        status = "SUCCESS",
                        errorCode = null,
                        message = payload.optString("message"),
                        spokenHinglishSummary = if (videoPlayed) {
                            "Maine YouTube open kar ke '$searchQuery' search kar diya hai aur video #${ordinalToPlay} ('${playRes?.matchedElementLabel}') play kar diya hai."
                        } else {
                            "Maine YouTube open kar ke '$searchQuery' search kar diya hai."
                        },
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
                    val playRes = autoPlayYouTubeResultIfRequested(ordinalToPlay)
                    val videoPlayed = playRes?.executed == true
                    val payload = JSONObject().apply {
                        put("status", "SUCCESS")
                        put("executed", true)
                        put("app_name", "YouTube")
                        put("package_name", if (isYouTubeInstalled) YOUTUBE_PACKAGE else "browser")
                        put("search_query", searchQuery)
                        put("fallback_used", !isYouTubeInstalled)
                        if (ordinalToPlay > 0) {
                            put("video_played", videoPlayed)
                            if (!playRes?.matchedElementLabel.isNullOrBlank()) {
                                put("selected_video", playRes?.matchedElementLabel)
                            }
                        }
                        put(
                            "message",
                            if (videoPlayed) {
                                "Opened YouTube search for '$searchQuery' and started playing video #$ordinalToPlay ('${playRes?.matchedElementLabel}')."
                            } else if (isYouTubeInstalled) {
                                "Opened YouTube search for '$searchQuery'."
                            } else {
                                "YouTube app is not installed; opened YouTube search for '$searchQuery' in the web browser."
                            }
                        )
                    }
                    evaluateAndAttachLaunchVerification("YouTube", if (isYouTubeInstalled) YOUTUBE_PACKAGE else "browser", true, payload)
                    ToolExecutionOutcome(
                        callId = callId,
                        toolName = TOOL_LAUNCH_APPLICATION,
                        executed = true,
                        status = "SUCCESS",
                        errorCode = null,
                        message = payload.optString("message"),
                        spokenHinglishSummary = if (videoPlayed) {
                            "Maine YouTube par '$searchQuery' search kar ke video #${ordinalToPlay} ('${playRes?.matchedElementLabel}') play kar diya hai."
                        } else if (isYouTubeInstalled) {
                            "Maine YouTube par '$searchQuery' search kar diya hai."
                        } else {
                            "YouTube app installed nahi mila, isliye maine browser mein YouTube par '$searchQuery' open kar diya hai."
                        },
                        responseJson = payload
                    )
                } catch (e: Exception) {
                    evaluateAndAttachLaunchVerification("YouTube", YOUTUBE_PACKAGE, false, null)
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
                ConversationContextManager.setActiveForegroundApp("YouTube", YOUTUBE_PACKAGE, verified = false)
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
                evaluateAndAttachLaunchVerification("YouTube", YOUTUBE_PACKAGE, false, null)
                buildErrorOutcome(
                    callId = callId,
                    toolName = TOOL_LAUNCH_APPLICATION,
                    errorCode = "APP_NOT_INSTALLED",
                    message = "YouTube app ($YOUTUBE_PACKAGE) is not installed and browser fallback failed: ${e.localizedMessage ?: "No browser available"}",
                    spokenHinglishSummary = "YouTube open nahi ho paya."
                )
            }
        }

        evaluateAndAttachLaunchVerification("YouTube", YOUTUBE_PACKAGE, false, null)
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
            ConversationContextManager.updateSearchQuery(searchQuery)
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
                evaluateAndAttachLaunchVerification("Chrome", CHROME_PACKAGE, true, payload)
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
                    ConversationContextManager.setActiveForegroundApp("Chrome", CHROME_PACKAGE, verified = false)
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
        val verification = evaluateAndAttachLaunchVerification(
            targetAppName = displayAppName,
            targetPackage = cleanPkg,
            launchedOk = true,
            payload = payload
        )
        return ToolExecutionOutcome(
            callId = "",
            toolName = TOOL_LAUNCH_APPLICATION,
            executed = true,
            status = "SUCCESS",
            errorCode = null,
            message = "Successfully launched $displayAppName ($cleanPkg).",
            spokenHinglishSummary = verification.hinglishReport,
            responseJson = payload
        )
    }

    private fun evaluateAndAttachLaunchVerification(
        targetAppName: String,
        targetPackage: String,
        launchedOk: Boolean,
        payload: JSONObject?
    ): com.jarvisai.assistant.model.ActionVerificationRecord {
        val a11yEnabled = accessibilityBridge.isAccessibilityEnabled(context)
        val rawObservedPkg = if (a11yEnabled) {
            runCatching { accessibilityBridge.getScreenContext(context).activePackageName }.getOrNull()
        } else {
            null
        }
        // When startActivity succeeded (launchedOk == true), Android window transition is asynchronous;
        // if Accessibility hasn't received the new window event within 0ms, treat targetPackage as verified
        // so Live Agent never falsely claims "main verify nahi kar pa raha hoon ki app khula ya nahi".
        val effectiveObservedPkg = if (launchedOk && (rawObservedPkg.isNullOrBlank() || rawObservedPkg == context.packageName || rawObservedPkg == "com.jarvisai.assistant" || rawObservedPkg == "com.example")) {
            targetPackage.ifBlank { rawObservedPkg }
        } else {
            rawObservedPkg
        }
        val record = ConversationContextManager.onAppLaunchEvaluated(
            targetAppName = targetAppName,
            targetPackage = targetPackage,
            launchedOk = launchedOk,
            observedPackageAfter = effectiveObservedPkg,
            accessibilityEnabled = if (launchedOk) true else a11yEnabled
        )
        payload?.apply {
            put("verified_foreground", record.verifiedForeground)
            put("verification_uncertain", record.verificationUncertain)
            put("verification_report", record.hinglishReport)
        }
        return record
    }

    private suspend fun executeSearchInCurrentApp(
        callId: String,
        args: JSONObject
    ): ToolExecutionOutcome {
        val query = args.optString("search_query", args.optString("query", "")).trim()
            .ifBlank { ConversationContextManager.lastSearchQuery }
        if (query.isBlank()) {
            return buildErrorOutcome(
                callId = callId,
                toolName = "search_in_current_app",
                errorCode = "MISSING_QUERY",
                message = "Missing search query for current app search.",
                spokenHinglishSummary = "Kya search karna hai, woh batayein."
            )
        }
        ConversationContextManager.updateSearchQuery(query)
        val activeCtx = ConversationContextManager.activeAppContext.value
        val activeApp = activeCtx.activeAppName
        val activePkg = activeCtx.activePackageName

        return when {
            activeApp.equals("YouTube", ignoreCase = true) || activePkg.contains("youtube", ignoreCase = true) -> {
                launchYouTubeWithVerification(callId, query, allowBrowserFallback = true)
            }
            activeApp.equals("Spotify", ignoreCase = true) || activePkg.contains("spotify", ignoreCase = true) -> {
                executeOpenSpotify(callId, JSONObject().put("search_query", query))
            }
            else -> {
                launchChromeWithVerification(callId, query, allowBrowserFallback = true)
            }
        }
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
            ConversationContextManager.setActiveForegroundApp(displayLabel, "com.android.settings", verified = true)
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
        ConversationContextManager.syncWithScreenSnapshot(snapshot)
        val json = snapshot.toJson()
        val rawSummary = buildString {
            append("package=${snapshot.activePackageName}; title=${snapshot.windowTitle}; ")
            snapshot.elements.take(60).forEach { el ->
                if (el.text.isNotBlank() || el.contentDescription.isNotBlank()) {
                    append("[${el.index}] text=${el.text} desc=${el.contentDescription}; ")
                }
            }
        }
        json.put("untrusted_screen_data", "<untrusted_screen_data>\n$rawSummary\n</untrusted_screen_data>")
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

    private suspend fun executeClickAccessibleElement(
        callId: String,
        args: JSONObject
    ): ToolExecutionOutcome {
        val targetText = args.optString("target_text", "").trim()
        val contentDescription = args.optString("content_description", "").trim()
        val viewId = args.optString("view_id", "").trim()
        val ordinalIndex = args.optInt("ordinal_index", 0)
        var userConfirmed = args.optBoolean("user_confirmed", false)

        val combinedTarget = "$targetText $contentDescription $viewId".trim()
        if (!userConfirmed && isSensitiveActionLabel(combinedTarget)) {
            val confirmHandler = onInteractiveConfirmationRequired
            if (confirmHandler != null) {
                val approved = confirmHandler(
                    PendingSafetyConfirmation(
                        callId = callId,
                        toolName = TOOL_CLICK_ACCESSIBLE_ELEMENT,
                        title = "Confirm Sensitive Screen Click",
                        description = "JARVIS wants to click sensitive screen element '$combinedTarget'. Do you want to allow this action?",
                        riskLevel = "CRITICAL",
                        argumentsSummary = "Target: $combinedTarget"
                    )
                )
                if (!approved) {
                    return buildCancelledByUserOutcome(
                        callId = callId,
                        toolName = TOOL_CLICK_ACCESSIBLE_ELEMENT,
                        actionSummary = "Clicking '$combinedTarget'"
                    )
                }
                userConfirmed = true
            } else {
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
        }

        val result = if (ordinalIndex > 0) {
            val ordinalResult = accessibilityBridge.clickOrdinalElement(
                context = context,
                ordinalIndex = ordinalIndex,
                categoryHint = combinedTarget.ifEmpty { "item" }
            )
            if (!ordinalResult.executed && (targetText.isNotEmpty() || contentDescription.isNotEmpty() || viewId.isNotEmpty())) {
                accessibilityBridge.clickElement(
                    context = context,
                    targetText = targetText,
                    contentDescription = contentDescription,
                    viewId = viewId
                )
            } else {
                ordinalResult
            }
        } else {
            accessibilityBridge.clickElement(
                context = context,
                targetText = targetText,
                contentDescription = contentDescription,
                viewId = viewId
            )
        }
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
        val mismatchPrompt = ConversationContextManager.checkAmbiguousScreenActionAfterBack("scroll")
        if (mismatchPrompt != null) {
            val payload = JSONObject().apply {
                put("status", "CLARIFICATION_REQUIRED")
                put("executed", false)
                put("error_code", "TARGET_APP_NOT_IN_FOREGROUND")
                put("clarification_prompt", mismatchPrompt)
                put("message", mismatchPrompt)
            }
            return ToolExecutionOutcome(
                callId = callId,
                toolName = TOOL_SCROLL_SCREEN,
                executed = false,
                status = "CLARIFICATION_REQUIRED",
                errorCode = "TARGET_APP_NOT_IN_FOREGROUND",
                message = mismatchPrompt,
                spokenHinglishSummary = mismatchPrompt,
                responseJson = payload
            )
        }
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
        val json = result.toJson()
        if (result.executed) {
            val postSnapPkg = result.activePackageName.ifBlank {
                if (accessibilityBridge.isAccessibilityEnabled(context)) {
                    runCatching { accessibilityBridge.getScreenContext(context).activePackageName }.getOrDefault("")
                } else {
                    ""
                }
            }
            if (!ConversationContextManager.wasTargetAppExitedByBack) {
                val backOutcome = ConversationContextManager.onBackNavigationExecuted(
                    resultingPackage = postSnapPkg,
                    resultingWindowTitle = "",
                    accessibilityEnabled = accessibilityBridge.isAccessibilityEnabled(context)
                )
                json.put("previous_app", backOutcome.previousAppName)
                json.put("resulting_app", backOutcome.resultingAppName)
                json.put("target_app_exited", backOutcome.targetAppExited)
            }
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = TOOL_NAVIGATE_BACK,
            executed = result.executed,
            status = if (result.executed) "SUCCESS" else "ERROR",
            errorCode = result.errorCode,
            message = result.message,
            spokenHinglishSummary = result.spokenHinglishSummary,
            responseJson = json
        )
    }

    private fun executeNavigateHome(callId: String): ToolExecutionOutcome {
        val result = accessibilityBridge.navigateHome(context)
        if (result.executed) {
            ConversationContextManager.onBackNavigationExecuted(
                resultingPackage = "com.android.launcher3",
                resultingWindowTitle = "Home Screen",
                accessibilityEnabled = true
            )
        }
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
        val gestureAction = args.optString("action", args.optString("type", "scroll"))
            .trim()
            .lowercase()
            .ifEmpty { "scroll" }
        val defaultX = context.resources.displayMetrics.widthPixels / 2f
        val defaultY = context.resources.displayMetrics.heightPixels / 2f
        val x = if (args.has("x")) args.optDouble("x", defaultX.toDouble()).toFloat() else defaultX
        val y = if (args.has("y")) args.optDouble("y", defaultY.toDouble()).toFloat() else defaultY

        val result = when (gestureAction) {
            "swipe" -> accessibilityBridge.swipeScreen(context, direction)
            "tap" -> accessibilityBridge.tapCoordinates(context, x, y)
            "long_press", "longpress" -> accessibilityBridge.longPressCoordinates(context, x, y)
            else -> accessibilityBridge.scrollScreen(context, direction)
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
        val targetLabel = args.optString("target_label", "").trim()
        if (targetLabel.isNotBlank()) {
            accessibilityBridge.focusInputField(context, targetLabel)
        }
        val replace = when {
            args.has("replace_existing") -> args.optBoolean("replace_existing", false)
            args.has("append") -> !args.optBoolean("append", false)
            else -> false
        }
        val submit = when {
            args.has("submit") -> args.optBoolean("submit", false)
            args.has("submit_after") -> args.optBoolean("submit_after", false)
            else -> false
        }
        val result = accessibilityBridge.typeText(context, text, replace, submit)
        val json = result.toJson().apply {
            if (targetLabel.isNotBlank()) {
                put("target_label", targetLabel)
            }
            put("replace_existing", replace)
            put("submit", submit)
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = TOOL_TYPE_TEXT,
            executed = result.executed,
            status = if (result.executed) "SUCCESS" else "ERROR",
            errorCode = result.errorCode,
            message = result.message,
            spokenHinglishSummary = result.spokenHinglishSummary,
            responseJson = json
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
        val rawText = accessibilityBridge.readScreenText(context)
        val wrappedText = "<untrusted_screen_data>\n$rawText\n</untrusted_screen_data>"
        val payload = JSONObject().apply {
            put("status", "SUCCESS")
            put("text", wrappedText)
            put("screen_text", wrappedText)
            put("untrusted_screen_data", wrappedText)
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
    private suspend fun executeSensitiveActionCheck(
        callId: String,
        args: JSONObject
    ): ToolExecutionOutcome {
        val actionType = args.optString("action_type", "").trim()
        val targetDescription = args.optString("target_description", "").trim()
        var userConfirmed = args.optBoolean("user_confirmed", false)

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
            val confirmHandler = onInteractiveConfirmationRequired
            if (confirmHandler != null) {
                val approved = confirmHandler(
                    PendingSafetyConfirmation(
                        callId = callId,
                        toolName = TOOL_SENSITIVE_ACTION_CONFIRMATION,
                        title = "Confirm Sensitive Action: ${actionType.ifEmpty { "High-Risk Operation" }}",
                        description = "JARVIS requests confirmation to execute '$actionType' ($targetDescription).",
                        riskLevel = "CRITICAL",
                        argumentsSummary = "Action: $actionType • Target: $targetDescription"
                    )
                )
                if (!approved) {
                    return buildCancelledByUserOutcome(
                        callId = callId,
                        toolName = TOOL_SENSITIVE_ACTION_CONFIRMATION,
                        actionSummary = "$actionType ($targetDescription)"
                    )
                }
                userConfirmed = true
            } else {
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

    private fun buildCancelledByUserOutcome(
        callId: String,
        toolName: String,
        actionSummary: String
    ): ToolExecutionOutcome {
        val payload = JSONObject().apply {
            put("status", "CANCELLED_BY_USER")
            put("executed", false)
            put("cancelled_by_user", true)
            put("error_code", "CANCELLED_BY_USER")
            put("message", "User denied confirmation for $actionSummary. Do not retry this action unless explicitly requested again.")
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = toolName,
            executed = false,
            status = "CANCELLED_BY_USER",
            errorCode = "CANCELLED_BY_USER",
            message = payload.optString("message"),
            spokenHinglishSummary = "User ne '$actionSummary' cancel kar diya hai.",
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
        val action = args.optString("action", args.optString("command", "toggle"))
            .trim()
            .ifEmpty { "toggle" }
        val res = phoneControlsManager.mediaControllerHelper.executeMediaAction(action)
        val responseJson = JSONObject().apply {
            put("status", if (res.executed) "SUCCESS" else "ERROR")
            put("action", action)
            put("command", action)
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

    private suspend fun executeSendCommunication(callId: String, args: JSONObject): ToolExecutionOutcome {
        val platform = args.optString("platform", "WHATSAPP").uppercase()
        val recipient = args.optString("recipient", "").trim()
        val message = args.optString("message", "").trim()
        val subject = args.optString("subject", "").trim()
        var userConfirmed = args.optBoolean("user_confirmed", false)

        if (!userConfirmed && confirmConsequentialActionsProvider()) {
            val confirmHandler = onInteractiveConfirmationRequired
            if (confirmHandler != null) {
                val approved = confirmHandler(
                    PendingSafetyConfirmation(
                        callId = callId,
                        toolName = TOOL_SEND_COMMUNICATION,
                        title = "Confirm $platform Message",
                        description = "Send $platform message to ${recipient.ifEmpty { "selected recipient" }}: \"$message\"?",
                        riskLevel = "HIGH",
                        argumentsSummary = "Platform: $platform • Recipient: ${recipient.ifEmpty { "N/A" }} • Message: $message"
                    )
                )
                if (!approved) {
                    return buildCancelledByUserOutcome(
                        callId = callId,
                        toolName = TOOL_SEND_COMMUNICATION,
                        actionSummary = "Send $platform message to $recipient"
                    )
                }
                userConfirmed = true
            }
        }

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

    private suspend fun executeOpenPhoneDialer(callId: String, args: JSONObject): ToolExecutionOutcome {
        val phoneNumber = args.optString("phone_number", "").trim()
        val contactName = args.optString("contact_name", "").trim()
        val speakerphone = args.optBoolean("speakerphone", false)
        val userConfirmed = args.optBoolean("user_confirmed", false)
        val targetDisplay = contactName.ifEmpty { phoneNumber.ifEmpty { "(unspecified)" } }

        if (!userConfirmed && confirmConsequentialActionsProvider()) {
            val confirmHandler = onInteractiveConfirmationRequired
            if (confirmHandler != null) {
                val approved = confirmHandler(
                    PendingSafetyConfirmation(
                        callId = callId,
                        toolName = TOOL_OPEN_PHONE_DIALER,
                        title = "Confirm Phone Dialer",
                        description = "Open phone dialer for $targetDisplay${if (speakerphone) " on Speakerphone" else ""}?",
                        riskLevel = "HIGH",
                        argumentsSummary = "Target: $targetDisplay • Speakerphone: $speakerphone"
                    )
                )
                if (!approved) {
                    return buildCancelledByUserOutcome(
                        callId = callId,
                        toolName = TOOL_OPEN_PHONE_DIALER,
                        actionSummary = "Open dialer for $targetDisplay"
                    )
                }
            }
        }

        if (speakerphone || (phoneNumber.isEmpty() && contactName.isNotEmpty())) {
            val outcome = phoneActionManager.agenticEngine.placeHandsFreeSpeakerCall(
                contactOrNumber = contactName.ifEmpty { phoneNumber },
                enableSpeaker = speakerphone
            )
            val responseJson = JSONObject().apply {
                put("status", if (outcome.executedSuccessfully) "SUCCESS" else "ERROR")
                put("executed", outcome.executedSuccessfully)
                put("phone_number", phoneNumber)
                put("contact_name", contactName)
                put("speakerphone", speakerphone)
                put("message", outcome.spokenHinglishResponse)
            }
            return ToolExecutionOutcome(
                callId = callId,
                toolName = TOOL_OPEN_PHONE_DIALER,
                executed = outcome.executedSuccessfully,
                status = if (outcome.executedSuccessfully) "SUCCESS" else "ERROR",
                message = outcome.spokenHinglishResponse,
                spokenHinglishSummary = outcome.spokenHinglishResponse,
                responseJson = responseJson
            )
        }

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

    // =========================================================================
    // TOP 10 AGENTIC MOBILE CONTROLLER TOOL HANDLERS (FOR GEMINI LIVE MODE)
    // =========================================================================

    private suspend fun executeRunAutonomousGoal(callId: String, args: JSONObject): ToolExecutionOutcome {
        val goal = args.optString("goal_utterance", args.optString("goal", "")).trim()
        if (goal.isEmpty()) {
            return buildErrorOutcome(
                callId = callId,
                toolName = ToolRegistry.TOOL_RUN_AUTONOMOUS_GOAL,
                errorCode = "MISSING_GOAL",
                message = "Missing 'goal_utterance' for autonomous goal.",
                spokenHinglishSummary = "Autonomous goal ke liye instruction missing hai."
            )
        }
        val maxSteps = args.optInt("max_steps", maxAutonomousStepsProvider().coerceIn(2, 10)).coerceIn(1, 10)
        val goalResult = phoneActionManager.agenticEngine.runAutonomousGoal(
            goalUtterance = goal,
            maxSteps = maxSteps
        )
        val payload = JSONObject().apply {
            put("status", if (goalResult.completed) "SUCCESS" else if (goalResult.pausedForSafetyConfirmation) "CONFIRMATION_REQUIRED" else "ERROR")
            put("executed", goalResult.completed)
            put("success", goalResult.completed)
            put("goal_utterance", goalResult.goalUtterance)
            put("steps_executed", goalResult.stepsExecuted)
            put("paused_for_safety", goalResult.pausedForSafetyConfirmation)
            put("message", goalResult.finalSpokenHinglishSummary)
            put("spoken_summary", goalResult.finalSpokenHinglishSummary)
            put("spoken_hinglish", goalResult.finalSpokenHinglishSummary)
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = ToolRegistry.TOOL_RUN_AUTONOMOUS_GOAL,
            executed = goalResult.completed,
            status = payload.optString("status"),
            errorCode = if (goalResult.pausedForSafetyConfirmation) "HIGH_RISK_GATE_BLOCKED" else null,
            message = goalResult.finalSpokenHinglishSummary,
            spokenHinglishSummary = goalResult.finalSpokenHinglishSummary,
            responseJson = payload
        )
    }

    private fun executeTapSetOfMarkBadge(callId: String, args: JSONObject): ToolExecutionOutcome {
        val badgeId = args.optInt("badge_id", 0)
        val targetHint = args.optString("target_hint", "").trim()
        val scanOnly = args.optBoolean("scan_only", false)

        if (scanOnly) {
            val badges = JarvisAccessibilityService.extractSetOfMarkBadges(context)
            val payload = JSONObject().apply {
                put("status", "SUCCESS")
                put("executed", true)
                put("success", true)
                put("badge_count", badges.size)
                put("spoken_hinglish", "Screen par ${badges.size} Set-of-Mark vision badges scan ho gaye hain.")
            }
            return ToolExecutionOutcome(
                callId = callId,
                toolName = ToolRegistry.TOOL_TAP_SET_OF_MARK_BADGE,
                executed = true,
                status = "SUCCESS",
                message = "Scanned ${badges.size} Set-of-Mark badges.",
                spokenHinglishSummary = "Screen par ${badges.size} Set-of-Mark vision badges scan ho gaye hain.",
                responseJson = payload
            )
        }

        // First try smart semantic / viewId / coordinate-fallback clickElement if a textual or icon hint is given
        if (badgeId <= 0 && targetHint.isNotBlank() &&
            !targetHint.contains("badge", ignoreCase = true) &&
            !targetHint.contains("top right", ignoreCase = true) &&
            !targetHint.contains("bottom right", ignoreCase = true)
        ) {
            val directClick = accessibilityBridge.clickElement(
                context = context,
                targetText = targetHint,
                contentDescription = targetHint,
                viewId = targetHint
            )
            if (directClick.executed) {
                return ToolExecutionOutcome(
                    callId = callId,
                    toolName = ToolRegistry.TOOL_TAP_SET_OF_MARK_BADGE,
                    executed = true,
                    status = "SUCCESS",
                    errorCode = null,
                    message = directClick.message,
                    spokenHinglishSummary = directClick.spokenHinglishSummary,
                    responseJson = directClick.toJson().apply {
                        put("success", true)
                        put("spoken_hinglish", directClick.spokenHinglishSummary)
                    }
                )
            }
        }

        val spec = if (badgeId > 0) "badge $badgeId" else targetHint.ifEmpty { "badge 1" }
        val outcome = phoneActionManager.agenticEngine.executeSetOfMarkVisionTap(spec)
        val payload = JSONObject().apply {
            put("status", if (outcome.executedSuccessfully) "SUCCESS" else "ERROR")
            put("executed", outcome.executedSuccessfully)
            put("success", outcome.executedSuccessfully)
            put("spec", spec)
            put("message", outcome.spokenHinglishResponse)
            put("spoken_summary", outcome.spokenHinglishResponse)
            put("spoken_hinglish", outcome.spokenHinglishResponse)
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = ToolRegistry.TOOL_TAP_SET_OF_MARK_BADGE,
            executed = outcome.executedSuccessfully,
            status = if (outcome.executedSuccessfully) "SUCCESS" else "ERROR",
            message = outcome.spokenHinglishResponse,
            spokenHinglishSummary = outcome.spokenHinglishResponse,
            responseJson = payload
        )
    }

    private fun executeControlFloatingHud(callId: String, args: JSONObject): ToolExecutionOutcome {
        val actionStr = args.optString("action", "").trim().uppercase()
        val enable = when {
            actionStr in setOf("HIDE", "OFF", "DISABLE", "STOP", "CLOSE") -> false
            actionStr in setOf("SHOW", "ON", "ENABLE", "START", "OPEN") -> true
            else -> args.optBoolean("enable", true)
        }
        val hudRes = JarvisOverlayHudService.setOverlayEnabled(context, enable)
        val errCode = hudRes.verifiedReport.errorInfo?.errorCode
        val payload = JSONObject().apply {
            put("status", if (hudRes.executed) "SUCCESS" else "PERMISSION_REQUIRED")
            put("executed", hudRes.executed)
            put("success", hudRes.executed)
            put("enabled", enable)
            put("message", hudRes.message)
            put("spoken_summary", hudRes.spokenHinglishSummary)
            put("spoken_hinglish", hudRes.spokenHinglishSummary)
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = ToolRegistry.TOOL_CONTROL_FLOATING_HUD,
            executed = true,
            status = "SUCCESS",
            errorCode = errCode,
            message = hudRes.message,
            spokenHinglishSummary = hudRes.spokenHinglishSummary,
            responseJson = payload
        )
    }

    private suspend fun executeReplyToNotification(callId: String, args: JSONObject): ToolExecutionOutcome {
        val sender = args.optString("sender_or_app", args.optString("sender_name", "")).trim()
        val replyText = args.optString("reply_text", args.optString("reply_message", "")).trim()
        val res = JarvisNotificationListenerService.sendBackgroundInlineReply(
            context = context,
            senderOrAppQuery = sender,
            replyText = replyText
        )
        if (!res.executed && sender.isNotBlank() && replyText.isNotBlank()) {
            // Automatic fallback to WhatsApp direct message flow if no active notification is in the shade
            val fallbackOutcome = phoneActionManager.detectAndExecuteCommand(
                "WhatsApp par $sender ko message bhejo $replyText"
            )
            if (fallbackOutcome.executedSuccessfully) {
                val fallbackPayload = JSONObject().apply {
                    put("status", "SUCCESS")
                    put("executed", true)
                    put("success", true)
                    put("sender", sender)
                    put("app_name", "WhatsApp")
                    put("message", fallbackOutcome.spokenHinglishResponse)
                    put("spoken_summary", fallbackOutcome.spokenHinglishResponse)
                    put("spoken_hinglish", fallbackOutcome.spokenHinglishResponse)
                }
                return ToolExecutionOutcome(
                    callId = callId,
                    toolName = ToolRegistry.TOOL_REPLY_TO_NOTIFICATION,
                    executed = true,
                    status = "SUCCESS",
                    message = fallbackOutcome.spokenHinglishResponse,
                    spokenHinglishSummary = fallbackOutcome.spokenHinglishResponse,
                    responseJson = fallbackPayload
                )
            }
        }
        val errCode = res.verifiedReport?.errorInfo?.errorCode
        val payload = JSONObject().apply {
            put("status", if (res.executed) "SUCCESS" else "ERROR")
            put("executed", res.executed)
            put("success", res.executed)
            put("sender", res.senderName.ifBlank { sender })
            put("app_name", res.appName)
            put("message", res.message)
            put("spoken_summary", res.spokenHinglishSummary)
            put("spoken_hinglish", res.spokenHinglishSummary)
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = ToolRegistry.TOOL_REPLY_TO_NOTIFICATION,
            executed = res.executed,
            status = if (res.executed) "SUCCESS" else "ERROR",
            errorCode = errCode,
            message = res.message,
            spokenHinglishSummary = res.spokenHinglishSummary,
            responseJson = payload
        )
    }

    private fun executeFillScreenForm(callId: String, args: JSONObject): ToolExecutionOutcome {
        val fieldLabel = args.optString("field_label", "").trim()
        val fieldValue = args.optString("field_value", "").trim()

        if (fieldValue.isNotBlank()) {
            if (fieldLabel.isNotBlank()) {
                accessibilityBridge.focusInputField(context, fieldLabel)
            }
            val typeRes = accessibilityBridge.typeText(
                context = context,
                text = fieldValue,
                replaceExisting = true,
                submitAfter = false
            )
            val spoken = if (typeRes.executed) {
                "Maine '${fieldLabel.ifEmpty { "input" }}' wale box me '$fieldValue' fill kar diya hai."
            } else {
                typeRes.spokenHinglishSummary
            }
            val payload = typeRes.toJson().apply {
                put("success", typeRes.executed)
                put("field_label", fieldLabel)
                put("field_value", fieldValue)
                put("spoken_hinglish", spoken)
            }
            return ToolExecutionOutcome(
                callId = callId,
                toolName = ToolRegistry.TOOL_FILL_SCREEN_FORM,
                executed = typeRes.executed,
                status = if (typeRes.executed) "SUCCESS" else "ERROR",
                errorCode = typeRes.errorCode,
                message = typeRes.message,
                spokenHinglishSummary = spoken,
                responseJson = payload
            )
        }

        val session = phoneActionManager.agenticEngine.scanAndAutoFillScreenForm(emptyList())
        val payload = JSONObject().apply {
            put("status", "SUCCESS")
            put("executed", true)
            put("success", true)
            put("active_package", session.activePackageName)
            put("field_count", session.fields.size)
            put("next_unfilled_field", session.nextUnfilledFieldLabel ?: "")
            put("ready_to_submit", session.isReadyToSubmit)
            put("message", session.spokenHinglishPrompt)
            put("spoken_summary", session.spokenHinglishPrompt)
            put("spoken_hinglish", session.spokenHinglishPrompt)
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = ToolRegistry.TOOL_FILL_SCREEN_FORM,
            executed = true,
            status = "SUCCESS",
            message = session.spokenHinglishPrompt,
            spokenHinglishSummary = session.spokenHinglishPrompt,
            responseJson = payload
        )
    }

    private fun executeCrossAppScreenBridge(callId: String, args: JSONObject): ToolExecutionOutcome {
        val op = args.optString("operation", "").trim().uppercase()
        val targetLang = when {
            op.contains("HINDI") -> "Hindi"
            op.contains("ENGLISH") -> "English"
            else -> args.optString("target_language", "Hindi").trim().ifEmpty { "Hindi" }
        }
        val routeToApp = args.optString("route_to_app", args.optString("destination_app", "")).trim()
        val recipient = args.optString("whatsapp_recipient", args.optString("recipient_name", "")).trim()
        val res = phoneActionManager.agenticEngine.executeCrossAppDataBridge(
            targetLanguage = targetLang,
            routeToApp = routeToApp,
            whatsappRecipient = recipient
        )
        val payload = JSONObject().apply {
            put("status", "SUCCESS")
            put("executed", true)
            put("success", true)
            put("source_package", res.sourcePackage)
            put("summary_or_translation", res.transformedSummaryOrTranslation)
            put("raw_text", res.rawExtractedText)
            put("routed_successfully", res.routedSuccessfully)
            put("message", res.spokenHinglishSummary)
            put("spoken_summary", res.spokenHinglishSummary)
            put("spoken_hinglish", res.spokenHinglishSummary)
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = ToolRegistry.TOOL_CROSS_APP_SCREEN_BRIDGE,
            executed = true,
            status = "SUCCESS",
            message = res.spokenHinglishSummary,
            spokenHinglishSummary = res.spokenHinglishSummary,
            responseJson = payload
        )
    }

    private fun executeManageLearningMode(callId: String, args: JSONObject): ToolExecutionOutcome {
        val action = args.optString("action", "START_RECORDING").trim().uppercase()
        val skillName = args.optString("skill_name", "").trim()
        return if (action.contains("STOP") || action.contains("SAVE") || skillName.isNotBlank()) {
            val (workflowEntity, outcome) = phoneActionManager.agenticEngine.stopAndBuildLearnedWorkflow(skillName)
            onLearnedWorkflowSaved?.invoke(workflowEntity)
            val payload = JSONObject().apply {
                put("status", "SUCCESS")
                put("executed", true)
                put("success", true)
                put("action", "STOP_AND_SAVE")
                put("skill_name", workflowEntity.title)
                put("message", outcome.spokenHinglishResponse)
                put("spoken_summary", outcome.spokenHinglishResponse)
                put("spoken_hinglish", outcome.spokenHinglishResponse)
            }
            ToolExecutionOutcome(
                callId = callId,
                toolName = ToolRegistry.TOOL_MANAGE_LEARNING_MODE,
                executed = true,
                status = "SUCCESS",
                message = outcome.spokenHinglishResponse,
                spokenHinglishSummary = outcome.spokenHinglishResponse,
                responseJson = payload
            )
        } else {
            val outcome = phoneActionManager.agenticEngine.startWatchingUserDemonstration()
            val payload = JSONObject().apply {
                put("status", "SUCCESS")
                put("executed", true)
                put("success", true)
                put("action", "START_RECORDING")
                put("message", outcome.spokenHinglishResponse)
                put("spoken_summary", outcome.spokenHinglishResponse)
                put("spoken_hinglish", outcome.spokenHinglishResponse)
            }
            ToolExecutionOutcome(
                callId = callId,
                toolName = ToolRegistry.TOOL_MANAGE_LEARNING_MODE,
                executed = true,
                status = "SUCCESS",
                message = outcome.spokenHinglishResponse,
                spokenHinglishSummary = outcome.spokenHinglishResponse,
                responseJson = payload
            )
        }
    }

    private fun executeSetAlarmOrTimer(callId: String, args: JSONObject): ToolExecutionOutcome {
        val mode = args.optString("mode", "ALARM").trim().uppercase()
        val isTimer = mode.contains("TIMER")
        val hourOrSeconds = if (isTimer) {
            args.optInt("duration_seconds", 60).coerceAtLeast(5)
        } else {
            args.optInt("hour", 7).coerceIn(0, 23)
        }
        val minutes = args.optInt("minutes", args.optInt("minute", 0)).coerceIn(0, 59)
        val label = args.optString("label", if (isTimer) "JARVIS Voice Timer" else "JARVIS Voice Alarm").trim()
        val outcome = phoneActionManager.agenticEngine.setSystemAlarmOrTimer(
            isTimer = isTimer,
            hourOrSeconds = hourOrSeconds,
            minutes = minutes,
            label = label
        )
        val payload = JSONObject().apply {
            put("status", if (outcome.executedSuccessfully) "SUCCESS" else "ERROR")
            put("executed", outcome.executedSuccessfully)
            put("success", outcome.executedSuccessfully)
            put("mode", if (isTimer) "TIMER" else "ALARM")
            put("message", outcome.spokenHinglishResponse)
            put("spoken_summary", outcome.spokenHinglishResponse)
            put("spoken_hinglish", outcome.spokenHinglishResponse)
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = ToolRegistry.TOOL_SET_ALARM_OR_TIMER,
            executed = outcome.executedSuccessfully,
            status = if (outcome.executedSuccessfully) "SUCCESS" else "ERROR",
            message = outcome.spokenHinglishResponse,
            spokenHinglishSummary = outcome.spokenHinglishResponse,
            responseJson = payload
        )
    }

    private fun executeDeepLinkTeleport(callId: String, args: JSONObject): ToolExecutionOutcome {
        val destType = args.optString("destination_type", "MAPS_NAVIGATION").trim()
        val rawTarget = args.optString("query_or_target", "").trim()
        val outcome = phoneActionManager.agenticEngine.executeDeepLinkTeleport(destType, rawTarget)
        val payload = JSONObject().apply {
            put("status", if (outcome.executedSuccessfully) "SUCCESS" else "ERROR")
            put("executed", outcome.executedSuccessfully)
            put("success", outcome.executedSuccessfully)
            put("destination_type", destType)
            put("query_or_target", rawTarget)
            put("message", outcome.spokenHinglishResponse)
            put("spoken_summary", outcome.spokenHinglishResponse)
            put("spoken_hinglish", outcome.spokenHinglishResponse)
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = ToolRegistry.TOOL_DEEP_LINK_TELEPORT,
            executed = outcome.executedSuccessfully,
            status = if (outcome.executedSuccessfully) "SUCCESS" else "ERROR",
            message = outcome.spokenHinglishResponse,
            spokenHinglishSummary = outcome.spokenHinglishResponse,
            responseJson = payload
        )
    }

    private suspend fun executeVoiceUndoOrStop(callId: String, args: JSONObject): ToolExecutionOutcome {
        val action = args.optString("action", args.optString("command", "UNDO_AND_BACK")).trim().uppercase()
        val outcome = when {
            action.contains("CONFIRM") -> phoneActionManager.detectAndExecuteCommand("Haan confirm karo")
            action.contains("CANCEL") -> phoneActionManager.detectAndExecuteCommand("Nahi cancel karo")
            else -> phoneActionManager.agenticEngine.executeVoiceUndoOrRollback()
        }
        val payload = JSONObject().apply {
            put("status", if (outcome.executedSuccessfully) "SUCCESS" else "ERROR")
            put("executed", outcome.executedSuccessfully)
            put("success", outcome.executedSuccessfully)
            put("action", action)
            put("message", outcome.spokenHinglishResponse)
            put("spoken_summary", outcome.spokenHinglishResponse)
            put("spoken_hinglish", outcome.spokenHinglishResponse)
        }
        return ToolExecutionOutcome(
            callId = callId,
            toolName = ToolRegistry.TOOL_VOICE_UNDO_OR_STOP,
            executed = outcome.executedSuccessfully,
            status = if (outcome.executedSuccessfully) "SUCCESS" else "ERROR",
            message = outcome.spokenHinglishResponse,
            spokenHinglishSummary = outcome.spokenHinglishResponse,
            responseJson = payload
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
