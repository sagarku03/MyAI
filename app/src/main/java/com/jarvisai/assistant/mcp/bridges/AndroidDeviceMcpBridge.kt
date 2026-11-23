package com.jarvisai.assistant.mcp.bridges

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.jarvisai.assistant.adb.AdbConnectManager
import com.jarvisai.assistant.security.SecureApiKeyManager
import com.jarvisai.assistant.service.AutomationAccessibilityService
import com.jarvisai.assistant.service.JarvisAccessibilityService
import com.jarvisai.assistant.service.LiveAgentToolExecutor
import com.jarvisai.assistant.service.ToolRegistry
import com.jarvisai.assistant.terminal.LocalTerminalBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Full-capability Android Device MCP Bridge giving the Chat Agent (`agy` MCP & AntigravityToolSuite)
 * exact parity with Gemini Live Voice (`LiveAgentToolExecutor`, `JarvisAccessibilityService`,
 * Set-of-Mark visual screenshots, autonomous multi-step screen operator, hardware controls,
 * messaging, telephony, alarms, intents, file sharing, and automatic Wireless ADB fallback).
 */
object AndroidDeviceMcpBridge {

    private const val TAG = "AndroidDeviceMcpBridge"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var lastAdbAutoEnableAttemptMs: Long = 0L

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun resolveContext(): Context? =
        appContext ?: JarvisAccessibilityService.instance?.applicationContext
            ?: AutomationAccessibilityService.instance?.applicationContext

    private fun createLiveExecutor(ctx: Context): LiveAgentToolExecutor {
        val keyManager = SecureApiKeyManager(ctx)
        return LiveAgentToolExecutor(
            context = ctx,
            geminiApiKeyProvider = {
                runCatching { keyManager.getApiKey().trim() }.getOrDefault("")
            }
        )
    }

    fun listTools(): JSONArray = JSONArray().apply {
        put(toolSpec(
            "android_execute_goal",
            "Runs Agentra's full autonomous multi-step mobile screen pilot (same engine as Gemini Live Voice) to complete end-to-end tasks across any Android app (e.g. post video/reel to Instagram, play YouTube video, send WhatsApp message, toggle settings).",
            listOf("goal"),
            "goal" to "string",
            "max_steps" to "integer"
        ))
        put(toolSpec(
            "android_get_ui_tree",
            "Reads live on-screen UI hierarchy, active app package, and numbered Set-of-Mark clickable element badges [1], [2], ... with exact coordinates.",
            emptyList()
        ))
        put(toolSpec(
            "android_inspect_screen",
            "Full Gemini-Live-style visual + accessibility screen inspection returning active package, visible text, clickable Set-of-Mark badges, and optional screenshot.",
            emptyList(),
            "include_screenshot" to "boolean"
        ))
        put(toolSpec(
            "android_take_screenshot",
            "Captures the live Android phone display as a Base64 JPEG image (attached as visual image block) with optional Set-of-Mark numbered clickable badges.",
            emptyList(),
            "include_som_badges" to "boolean"
        ))
        put(toolSpec(
            "android_click_element",
            "Clicks an on-screen UI element by Set-of-Mark badge_id, visible text, content_description, or view_id.",
            emptyList(),
            "badge_id" to "integer",
            "text" to "string",
            "content_description" to "string",
            "view_id" to "string"
        ))
        put(toolSpec(
            "android_tap",
            "Taps exact screen coordinates (x, y). Supports optional long_press (duration_ms) or double_tap. Uses Wireless ADB fallback if Accessibility is off.",
            listOf("x", "y"),
            "x" to "integer",
            "y" to "integer",
            "long_press" to "boolean",
            "double_tap" to "boolean",
            "duration_ms" to "integer"
        ))
        put(toolSpec(
            "android_swipe",
            "Performs a directional swipe (UP, DOWN, LEFT, RIGHT) or custom coordinate drag (start_x, start_y, end_x, end_y, duration_ms).",
            emptyList(),
            "direction" to "string",
            "start_x" to "integer",
            "start_y" to "integer",
            "end_x" to "integer",
            "end_y" to "integer",
            "duration_ms" to "integer"
        ))
        put(toolSpec(
            "android_scroll",
            "Performs scroll gesture (UP, DOWN, LEFT, RIGHT).",
            listOf("direction"),
            "direction" to "string"
        ))
        put(toolSpec(
            "android_focus_input",
            "Focuses an editable input, search, or caption box on screen by its hint/label.",
            emptyList(),
            "target_label" to "string"
        ))
        put(toolSpec(
            "android_type_text",
            "Types text into the currently focused field or a named target_field, with optional replace_existing and submit/Enter.",
            listOf("text"),
            "text" to "string",
            "target_field" to "string",
            "replace_existing" to "boolean",
            "submit" to "boolean"
        ))
        put(toolSpec(
            "android_clear_text",
            "Clears text in the currently focused input field.",
            emptyList()
        ))
        put(toolSpec(
            "android_press_key",
            "Presses system or hardware action: BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, POWER_DIALOG, LOCK_SCREEN, ENTER, VOLUME_UP, VOLUME_DOWN, MEDIA_PLAY_PAUSE.",
            listOf("key"),
            "key" to "string"
        ))
        put(toolSpec(
            "android_launch_app",
            "Launches any installed Android app by appName or packageName, with optional in-app searchQuery. Works even without Accessibility.",
            emptyList(),
            "appName" to "string",
            "packageName" to "string",
            "searchQuery" to "string"
        ))
        put(toolSpec(
            "android_control_hardware",
            "Controls Android hardware & device settings: wifi, bluetooth, flashlight/torch, volume, brightness, hotspot, mobile_data, dnd, airplane_mode.",
            listOf("setting"),
            "setting" to "string",
            "state" to "string",
            "level" to "integer"
        ))
        put(toolSpec(
            "android_send_message",
            "Sends a message via WhatsApp, Telegram, or SMS (platform: WHATSAPP, TELEGRAM, SMS).",
            listOf("recipient", "message"),
            "platform" to "string",
            "recipient" to "string",
            "message" to "string",
            "auto_send" to "boolean"
        ))
        put(toolSpec(
            "android_dial_phone",
            "Opens phone dialer or places a call by phone_number or contact_name.",
            emptyList(),
            "phone_number" to "string",
            "contact_name" to "string"
        ))
        put(toolSpec(
            "android_set_alarm_or_timer",
            "Sets an Android alarm (hour, minute, label) or countdown timer (duration_seconds, label).",
            listOf("mode"),
            "mode" to "string",
            "hour" to "integer",
            "minute" to "integer",
            "duration_seconds" to "integer",
            "label" to "string"
        ))
        put(toolSpec(
            "android_media_control",
            "Controls active media playback (action: play, pause, toggle, next, previous, stop).",
            listOf("action"),
            "action" to "string"
        ))
        put(toolSpec(
            "android_get_system_status",
            "Returns live Android telemetry: battery, charging, network, foreground app, Accessibility connection status, Wireless ADB status, and screen resolution.",
            emptyList()
        ))
        put(toolSpec(
            "android_deep_link",
            "Opens any URL, app deep link, or Android Intent URI directly (e.g. instagram://, vnd.youtube:, geo:, tel:, settings).",
            listOf("uri"),
            "uri" to "string",
            "package_name" to "string"
        ))
        put(toolSpec(
            "android_share_file",
            "Shares a local video, image, or document file directly to a target Android app (e.g. com.instagram.android, com.whatsapp) or system share sheet.",
            listOf("file_path"),
            "file_path" to "string",
            "package_name" to "string",
            "caption" to "string",
            "mime_type" to "string"
        ))
        put(toolSpec("android_get_clipboard", "Retrieves current text copied on Android clipboard.", emptyList()))
        put(toolSpec("android_set_clipboard", "Copies string text onto Android clipboard.", listOf("text"), "text" to "string"))
        put(toolSpec(
            "android_reply_notification",
            "Replies directly to an active messaging notification.",
            listOf("reply_text"),
            "sender" to "string",
            "app_name" to "string",
            "reply_text" to "string"
        ))
        put(toolSpec("android_auto_fill_otp", "Reads the latest SMS/notification OTP and injects it into the active screen input.", emptyList()))
        put(toolSpec("android_stop_task", "Stops any currently running autonomous mobile task or undoes the last action.", emptyList()))
        put(toolSpec(
            "android_open_accessibility_settings",
            "Auto-enables Agentra Accessibility Service if Wireless ADB is connected, or opens Android Accessibility Settings so the user can toggle Agentra ON.",
            emptyList()
        ))
    }

    suspend fun callTool(name: String, args: JSONObject): JSONObject {
        val ctx = resolveContext()
            ?: return JSONObject().put("error", "Agentra Android context is not initialized yet.")

        return when (name) {
            // =========================================================================
            // 1. NON-ACCESSIBILITY / HYBRID LIVE-AGENT TOOLS (ALWAYS WORK!)
            // =========================================================================
            "android_launch_app" -> {
                val appName = args.optString("appName", "")
                    .ifBlank { args.optString("app_name", "") }
                    .ifBlank { args.optString("target", "") }
                val pkgName = args.optString("packageName", "")
                    .ifBlank { args.optString("package_name", "") }
                val searchQuery = args.optString("searchQuery", "")
                    .ifBlank { args.optString("search_query", "") }
                val liveArgs = JSONObject().apply {
                    if (appName.isNotBlank()) put("app_name", appName)
                    if (pkgName.isNotBlank()) put("package_name", pkgName)
                    if (searchQuery.isNotBlank()) put("search_query", searchQuery)
                }
                val outcome = createLiveExecutor(ctx).executeToolCall(
                    ToolRegistry.TOOL_LAUNCH_APPLICATION,
                    "mcp-${UUID.randomUUID()}",
                    liveArgs
                )
                outcomeToMcpJson(outcome).put("launched", outcome.executed)
            }

            "android_control_hardware" -> {
                val setting = args.optString("setting", "")
                    .ifBlank { args.optString("target", "") }
                val state = args.optString("state", "")
                    .ifBlank { args.optString("value", "") }
                val liveArgs = JSONObject(args.toString()).apply {
                    if (setting.isNotBlank()) put("setting", setting)
                    if (state.isNotBlank()) put("state", state)
                }
                val outcome = createLiveExecutor(ctx).executeToolCall(
                    ToolRegistry.TOOL_CONTROL_DEVICE_HARDWARE,
                    "mcp-${UUID.randomUUID()}",
                    liveArgs
                )
                outcomeToMcpJson(outcome)
            }

            "android_send_message" -> {
                val liveArgs = JSONObject(args.toString()).apply {
                    if (!has("platform") || optString("platform").isBlank()) put("platform", "WHATSAPP")
                }
                val outcome = createLiveExecutor(ctx).executeToolCall(
                    ToolRegistry.TOOL_SEND_COMMUNICATION_MESSAGE,
                    "mcp-${UUID.randomUUID()}",
                    liveArgs
                )
                outcomeToMcpJson(outcome)
            }

            "android_dial_phone" -> {
                val outcome = createLiveExecutor(ctx).executeToolCall(
                    "open_phone_dialer",
                    "mcp-${UUID.randomUUID()}",
                    args
                )
                outcomeToMcpJson(outcome)
            }

            "android_set_alarm_or_timer" -> {
                val outcome = createLiveExecutor(ctx).executeToolCall(
                    ToolRegistry.TOOL_SET_ALARM_OR_TIMER,
                    "mcp-${UUID.randomUUID()}",
                    args
                )
                outcomeToMcpJson(outcome)
            }

            "android_media_control" -> {
                val outcome = createLiveExecutor(ctx).executeToolCall(
                    "control_media_playback",
                    "mcp-${UUID.randomUUID()}",
                    args
                )
                outcomeToMcpJson(outcome)
            }

            "android_get_system_status" -> {
                val outcome = createLiveExecutor(ctx).executeToolCall(
                    "get_system_telemetry",
                    "mcp-${UUID.randomUUID()}",
                    JSONObject()
                )
                val dm = ctx.resources.displayMetrics
                val a11yRunning = JarvisAccessibilityService.instance != null || AutomationAccessibilityService.instance != null
                val a11yEnabled = JarvisAccessibilityService.isAccessibilityEnabled(ctx)
                val a11yMalfunctioning = JarvisAccessibilityService.isAccessibilityMalfunctioning(ctx)
                val adbState = AdbConnectManager.deviceState.value
                outcomeToMcpJson(outcome).apply {
                    put("accessibility_running", a11yRunning)
                    put("accessibility_enabled", a11yEnabled)
                    put("accessibility_malfunctioning", a11yMalfunctioning)
                    put("adb_connected", adbState.isConnected)
                    put("adb_serial", adbState.deviceSerial ?: "")
                    put("screen_width", dm.widthPixels)
                    put("screen_height", dm.heightPixels)
                }
            }

            "android_deep_link" -> executeDeepLink(ctx, args)

            "android_share_file" -> executeShareFile(ctx, args)

            "android_get_clipboard" -> withContext(Dispatchers.Main) {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val text = runCatching { cm?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty() }.getOrDefault("")
                JSONObject().put("clipboard", text).put("status", "SUCCESS")
            }

            "android_set_clipboard" -> withContext(Dispatchers.Main) {
                val text = args.optString("text", "")
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("Agentra", text))
                JSONObject().put("copied", true).put("status", "SUCCESS")
            }

            "android_reply_notification" -> {
                val outcome = createLiveExecutor(ctx).executeToolCall(
                    ToolRegistry.TOOL_REPLY_TO_NOTIFICATION,
                    "mcp-${UUID.randomUUID()}",
                    args
                )
                outcomeToMcpJson(outcome)
            }

            "android_auto_fill_otp" -> {
                val outcome = createLiveExecutor(ctx).executeToolCall(
                    ToolRegistry.TOOL_AUTO_FILL_OTP,
                    "mcp-${UUID.randomUUID()}",
                    args
                )
                outcomeToMcpJson(outcome)
            }

            "android_stop_task" -> {
                val outcome = createLiveExecutor(ctx).executeToolCall(
                    ToolRegistry.TOOL_STOP_OR_UNDO_ACTION,
                    "mcp-${UUID.randomUUID()}",
                    args
                )
                outcomeToMcpJson(outcome)
            }

            "android_open_accessibility_settings" -> executeOpenOrEnableAccessibility(ctx)

            // =========================================================================
            // 2. AUTONOMOUS SCREEN PILOT & ACCESSIBILITY / ADB HYBRID TOOLS
            // =========================================================================
            "android_execute_goal" -> {
                ensureAccessibilityOrTryAdbAutoEnable(ctx)
                val goal = args.optString("goal", "").ifBlank { args.optString("instruction", "") }.trim()
                if (goal.isBlank()) {
                    return JSONObject().put("error", "Missing 'goal' parameter.")
                }
                if (!hasAccessibilityControl(ctx)) {
                    return buildAccessibilityUnavailableError(ctx, "android_execute_goal")
                }
                val executor = createLiveExecutor(ctx).apply {
                    val customSteps = args.optInt("max_steps", 25).coerceIn(3, 50)
                    maxAutonomousStepsProvider = { customSteps }
                }
                val liveArgs = JSONObject().put("goal", goal)
                val outcome = executor.executeToolCall(
                    ToolRegistry.TOOL_EXECUTE_SCREEN_OPERATOR_GOAL,
                    "mcp-${UUID.randomUUID()}",
                    liveArgs
                )
                outcomeToMcpJson(outcome)
            }

            "android_get_ui_tree", "android_inspect_screen" -> {
                ensureAccessibilityOrTryAdbAutoEnable(ctx)
                if (hasAccessibilityControl(ctx)) {
                    val snap = JarvisAccessibilityService.getScreenContext(ctx)
                    val badges = JarvisAccessibilityService.extractSetOfMarkBadges(ctx)
                    val badgesJson = JSONArray()
                    badges.take(60).forEach { b ->
                        badgesJson.put(
                            JSONObject()
                                .put("badge_id", b.badgeId)
                                .put("label", b.label)
                                .put("x", b.centerX)
                                .put("y", b.centerY)
                                .put("bounds", b.bounds)
                                .put("class", b.className)
                        )
                    }
                    val res = JSONObject().apply {
                        put("status", "SUCCESS")
                        put("engine", "accessibility")
                        put("active_package", snap.activePackageName)
                        put("window_title", snap.windowTitle)
                        put("ui_hierarchy", snap.toJson())
                        put("clickable_badges", badgesJson)
                    }
                    if (args.optBoolean("include_screenshot", false)) {
                        val shot = captureRealScreenshot(ctx, includeSomBadges = true)
                        if (shot.has("image_base64")) {
                            res.put("image_base64", shot.getString("image_base64"))
                            res.put("screen_path", shot.optString("screen_path"))
                        }
                    }
                    res
                } else if (isAdbAvailable()) {
                    executeAdbUiDump()
                } else {
                    buildAccessibilityUnavailableError(ctx, name)
                }
            }

            "android_take_screenshot" -> {
                ensureAccessibilityOrTryAdbAutoEnable(ctx)
                val includeSom = args.optBoolean("include_som_badges", true)
                captureRealScreenshot(ctx, includeSomBadges = includeSom)
            }

            "android_click_element" -> {
                ensureAccessibilityOrTryAdbAutoEnable(ctx)
                if (!hasAccessibilityControl(ctx)) {
                    return buildAccessibilityUnavailableError(ctx, name)
                }
                val badgeId = args.optInt("badge_id", -1)
                if (badgeId > 0) {
                    val outcome = createLiveExecutor(ctx).executeToolCall(
                        ToolRegistry.TOOL_TAP_SET_OF_MARK_BADGE,
                        "mcp-${UUID.randomUUID()}",
                        JSONObject().put("badge_id", badgeId)
                    )
                    return outcomeToMcpJson(outcome)
                }
                val text = args.optString("text", "").ifBlank { args.optString("target", "") }.ifBlank { null }
                val desc = args.optString("content_description", "").ifBlank { null }
                val viewId = args.optString("view_id", "").ifBlank { null }
                val op = JarvisAccessibilityService.clickElement(ctx, text, desc, viewId)
                op.toJson().apply {
                    if (!op.executed) put("error", op.message)
                }
            }

            "android_tap" -> {
                ensureAccessibilityOrTryAdbAutoEnable(ctx)
                val x = args.optInt("x", -1)
                val y = args.optInt("y", -1)
                if (x < 0 || y < 0) return JSONObject().put("error", "Valid integer x and y coordinates are required.")
                val longPress = args.optBoolean("long_press", false)
                val doubleTap = args.optBoolean("double_tap", false)
                val durationMs = args.optLong("duration_ms", 800L).coerceIn(200L, 4000L)

                if (hasAccessibilityControl(ctx)) {
                    val op = when {
                        longPress -> JarvisAccessibilityService.longPressCoordinates(ctx, x.toFloat(), y.toFloat(), durationMs)
                        doubleTap -> {
                            JarvisAccessibilityService.tapCoordinates(ctx, x.toFloat(), y.toFloat())
                            delay(110L)
                            JarvisAccessibilityService.tapCoordinates(ctx, x.toFloat(), y.toFloat())
                        }
                        else -> JarvisAccessibilityService.tapCoordinates(ctx, x.toFloat(), y.toFloat())
                    }
                    op.toJson().put("tapped", op.executed).put("x", x).put("y", y).apply {
                        if (!op.executed) put("error", op.message)
                    }
                } else if (isAdbAvailable()) {
                    val cmd = when {
                        longPress -> "adb shell input swipe $x $y $x $y $durationMs"
                        doubleTap -> "adb shell input tap $x $y && sleep 0.1 && adb shell input tap $x $y"
                        else -> "adb shell input tap $x $y"
                    }
                    runAdbAction(cmd, "tapped", JSONObject().put("x", x).put("y", y))
                } else {
                    buildAccessibilityUnavailableError(ctx, name)
                }
            }

            "android_swipe" -> {
                ensureAccessibilityOrTryAdbAutoEnable(ctx)
                val hasCustomCoords = args.has("start_x") && args.has("start_y") && args.has("end_x") && args.has("end_y")
                val direction = args.optString("direction", "").uppercase()
                val durationMs = args.optLong("duration_ms", 320L).coerceIn(80L, 3000L)

                if (hasCustomCoords) {
                    val sx = args.optInt("start_x", 0)
                    val sy = args.optInt("start_y", 0)
                    val ex = args.optInt("end_x", 0)
                    val ey = args.optInt("end_y", 0)
                    val svc = JarvisAccessibilityService.instance
                    if (svc != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val ok = dispatchCustomSwipe(svc, sx.toFloat(), sy.toFloat(), ex.toFloat(), ey.toFloat(), durationMs)
                        JSONObject().put("swiped", ok).put("engine", "accessibility")
                            .apply { if (!ok) put("error", "Swipe gesture failed to dispatch.") }
                    } else if (isAdbAvailable()) {
                        runAdbAction("adb shell input swipe $sx $sy $ex $ey $durationMs", "swiped")
                    } else {
                        buildAccessibilityUnavailableError(ctx, name)
                    }
                } else {
                    val dir = direction.ifBlank { "UP" }
                    if (hasAccessibilityControl(ctx)) {
                        val op = JarvisAccessibilityService.swipeScreen(ctx, dir.lowercase())
                        op.toJson().put("swiped", op.executed).apply {
                            if (!op.executed) put("error", op.message)
                        }
                    } else if (isAdbAvailable()) {
                        val (sx, sy, ex, ey) = directionToCoords(ctx, dir)
                        runAdbAction("adb shell input swipe $sx $sy $ex $ey $durationMs", "swiped")
                    } else {
                        buildAccessibilityUnavailableError(ctx, name)
                    }
                }
            }

            "android_scroll" -> {
                ensureAccessibilityOrTryAdbAutoEnable(ctx)
                val direction = args.optString("direction", "DOWN").uppercase()
                if (hasAccessibilityControl(ctx)) {
                    val op = JarvisAccessibilityService.scrollScreen(ctx, direction.lowercase())
                    op.toJson().put("scrolled", op.executed).apply {
                        if (!op.executed) put("error", op.message)
                    }
                } else if (isAdbAvailable()) {
                    val (sx, sy, ex, ey) = directionToCoords(ctx, direction)
                    runAdbAction("adb shell input swipe $sx $sy $ex $ey 350", "scrolled")
                } else {
                    buildAccessibilityUnavailableError(ctx, name)
                }
            }

            "android_focus_input" -> {
                ensureAccessibilityOrTryAdbAutoEnable(ctx)
                if (!hasAccessibilityControl(ctx)) {
                    return buildAccessibilityUnavailableError(ctx, name)
                }
                val label = args.optString("target_label", "").ifBlank { null }
                val op = JarvisAccessibilityService.focusInputField(ctx, label)
                op.toJson().apply { if (!op.executed) put("error", op.message) }
            }

            "android_type_text" -> {
                ensureAccessibilityOrTryAdbAutoEnable(ctx)
                val text = args.optString("text", "")
                val targetField = args.optString("target_field", "").trim()
                val replaceExisting = args.optBoolean("replace_existing", false)
                val submit = args.optBoolean("submit", false)

                if (hasAccessibilityControl(ctx)) {
                    if (targetField.isNotBlank()) {
                        JarvisAccessibilityService.focusInputField(ctx, targetField)
                        delay(180L)
                    }
                    val op = JarvisAccessibilityService.typeText(ctx, text, replaceExisting = replaceExisting, submitAfter = submit)
                    op.toJson().put("typed", op.executed).apply {
                        if (!op.executed) put("error", op.message)
                    }
                } else if (isAdbAvailable()) {
                    val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace(" ", "%s")
                    val cmd = buildString {
                        append("adb shell input text \"$escaped\"")
                        if (submit) append(" && adb shell input keyevent 66")
                    }
                    runAdbAction(cmd, "typed")
                } else {
                    buildAccessibilityUnavailableError(ctx, name)
                }
            }

            "android_clear_text" -> {
                ensureAccessibilityOrTryAdbAutoEnable(ctx)
                if (!hasAccessibilityControl(ctx)) {
                    return buildAccessibilityUnavailableError(ctx, name)
                }
                val op = JarvisAccessibilityService.clearText(ctx)
                op.toJson().apply { if (!op.executed) put("error", op.message) }
            }

            "android_press_key" -> {
                ensureAccessibilityOrTryAdbAutoEnable(ctx)
                val key = args.optString("key", "BACK").trim().uppercase()
                if (hasAccessibilityControl(ctx)) {
                    val svc = JarvisAccessibilityService.instance
                    val ok = when (key) {
                        "BACK" -> JarvisAccessibilityService.navigateBack(ctx).executed
                        "HOME" -> JarvisAccessibilityService.navigateHome(ctx).executed
                        "RECENTS" -> JarvisAccessibilityService.openRecents(ctx).executed
                        "NOTIFICATIONS" -> JarvisAccessibilityService.openNotifications(ctx).executed
                        "QUICK_SETTINGS" -> JarvisAccessibilityService.openQuickSettings(ctx).executed
                        "POWER_DIALOG" -> svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG) == true
                        "LOCK_SCREEN" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                            svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) == true
                        "ENTER" -> JarvisAccessibilityService.submitInput(ctx).executed
                        "VOLUME_UP", "VOLUME_DOWN", "MEDIA_PLAY_PAUSE" -> {
                            val live = createLiveExecutor(ctx)
                            val res = when (key) {
                                "VOLUME_UP" -> live.executeToolCall("adjust_system_volume", "mcp-vup", JSONObject().put("direction", "up"))
                                "VOLUME_DOWN" -> live.executeToolCall("adjust_system_volume", "mcp-vdown", JSONObject().put("direction", "down"))
                                else -> live.executeToolCall("control_media_playback", "mcp-play", JSONObject().put("action", "toggle"))
                            }
                            res.executed
                        }
                        else -> svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) == true
                    }
                    JSONObject().put("pressed", key).put("success", ok).put("engine", "accessibility")
                        .apply { if (!ok) put("error", "Failed to press key $key") }
                } else if (isAdbAvailable()) {
                    val keyCode = when (key) {
                        "BACK" -> "4"
                        "HOME" -> "3"
                        "RECENTS" -> "187"
                        "ENTER" -> "66"
                        "VOLUME_UP" -> "24"
                        "VOLUME_DOWN" -> "25"
                        "MEDIA_PLAY_PAUSE" -> "85"
                        "POWER_DIALOG", "LOCK_SCREEN" -> "26"
                        "NOTIFICATIONS" -> return runAdbAction("adb shell cmd statusbar expand-notifications", "success", JSONObject().put("pressed", key))
                        "QUICK_SETTINGS" -> return runAdbAction("adb shell cmd statusbar expand-settings", "success", JSONObject().put("pressed", key))
                        else -> "4"
                    }
                    runAdbAction("adb shell input keyevent $keyCode", "success", JSONObject().put("pressed", key))
                } else {
                    buildAccessibilityUnavailableError(ctx, name)
                }
            }

            else -> JSONObject().put("error", "Unknown android tool: $name")
        }
    }

    // =========================================================================
    // HELPERS: SCREENSHOTS, DEEP LINKS, FILE SHARING, ADB FALLBACK
    // =========================================================================

    private fun hasAccessibilityControl(ctx: Context): Boolean =
        JarvisAccessibilityService.instance != null ||
            AutomationAccessibilityService.instance != null ||
            JarvisAccessibilityService.isAccessibilityEnabled(ctx)

    private fun isAdbAvailable(): Boolean = AdbConnectManager.deviceState.value.isConnected

    private suspend fun ensureAccessibilityOrTryAdbAutoEnable(ctx: Context) {
        if (JarvisAccessibilityService.instance != null) return
        val now = System.currentTimeMillis()
        if (isAdbAvailable() && (now - lastAdbAutoEnableAttemptMs) > 30_000L) {
            lastAdbAutoEnableAttemptMs = now
            runCatching {
                AdbConnectManager.unblockAndEnableAccessibility(ctx)
                delay(450L)
            }
        }
    }

    private suspend fun executeOpenOrEnableAccessibility(ctx: Context): JSONObject {
        if (isAdbAvailable()) {
            val adbRes = AdbConnectManager.unblockAndEnableAccessibility(ctx)
            delay(500L)
            if (JarvisAccessibilityService.instance != null) {
                return JSONObject()
                    .put("status", "SUCCESS")
                    .put("enabled_via_adb", true)
                    .put("message", adbRes.getOrDefault("Agentra Accessibility Service enabled via local ADB."))
            }
        }
        val opened = JarvisAccessibilityService.openAccessibilitySettings(ctx)
        return JSONObject().apply {
            put("status", if (opened) "OPENED_SETTINGS" else "ERROR")
            put("opened_settings", opened)
            put(
                "message",
                if (opened) {
                    "Opened Android Accessibility Settings. Ask the user to enable 'Agentra' (if already ON, toggle OFF then ON once)."
                } else {
                    "Could not open Android Accessibility Settings."
                }
            )
        }
    }

    private suspend fun captureRealScreenshot(ctx: Context, includeSomBadges: Boolean): JSONObject {
        var rawBitmap: Bitmap? = null
        var source = "accessibility"

        if (JarvisAccessibilityService.instance != null) {
            rawBitmap = runCatching {
                JarvisAccessibilityService.captureScreenshotSuspend(allowCachedWithinInterval = false)
            }.getOrNull()
        }
        if (rawBitmap == null && AutomationAccessibilityService.instance != null) {
            rawBitmap = runCatching {
                AutomationAccessibilityService.instance?.captureScreenshot()
            }.getOrNull()
        }
        if (rawBitmap == null && isAdbAvailable()) {
            source = "adb_screencap"
            rawBitmap = captureScreenshotViaAdb(ctx)
        }

        if (rawBitmap == null) {
            return buildAccessibilityUnavailableError(ctx, "android_take_screenshot")
        }

        val dm = ctx.resources.displayMetrics
        val badges = if (JarvisAccessibilityService.instance != null) {
            runCatching { JarvisAccessibilityService.extractSetOfMarkBadges(ctx) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val scaledBitmap = scaleBitmapMaxWidth(rawBitmap, maxWidth = 720)
        val finalBitmap = if (includeSomBadges && badges.isNotEmpty()) {
            runCatching {
                JarvisAccessibilityService.drawSetOfMarkBadgesOnBitmap(
                    scaledBitmap,
                    dm.widthPixels.coerceAtLeast(1),
                    dm.heightPixels.coerceAtLeast(1)
                )
            }.getOrDefault(scaledBitmap)
        } else {
            scaledBitmap
        }

        val baos = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
        val jpegBytes = baos.toByteArray()
        val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

        val outFile = File(ctx.cacheDir, "agentra_screen_latest.jpg")
        runCatching { outFile.writeBytes(jpegBytes) }

        val badgesArr = JSONArray()
        badges.take(45).forEach { b ->
            badgesArr.put(
                JSONObject()
                    .put("badge_id", b.badgeId)
                    .put("label", b.label)
                    .put("x", b.centerX)
                    .put("y", b.centerY)
            )
        }

        return JSONObject().apply {
            put("status", "CAPTURED")
            put("engine", source)
            put("width", finalBitmap.width)
            put("height", finalBitmap.height)
            put("screen_width", dm.widthPixels)
            put("screen_height", dm.heightPixels)
            put("screen_path", outFile.absolutePath)
            put("badges", badgesArr)
            put("image_base64", b64)
        }
    }

    private suspend fun captureScreenshotViaAdb(ctx: Context): Bitmap? = withContext(Dispatchers.IO) {
        val tmpFile = File(ctx.cacheDir, "adb_cap_${System.currentTimeMillis()}.png")
        try {
            LocalTerminalBridge.instance.init(ctx)
            val res = LocalTerminalBridge.instance.executeCommand(
                "adb exec-out screencap -p > \"${tmpFile.absolutePath}\"",
                timeoutMs = 15_000L
            )
            if (res.exitCode == 0 && tmpFile.exists() && tmpFile.length() > 1000L) {
                BitmapFactory.decodeFile(tmpFile.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "ADB screencap failed: ${e.message}")
            null
        } finally {
            runCatching { tmpFile.delete() }
        }
    }

    private suspend fun executeAdbUiDump(): JSONObject = withContext(Dispatchers.IO) {
        val res = runCatching {
            LocalTerminalBridge.instance.executeCommand(
                "adb shell uiautomator dump /sdcard/agentra_ui.xml >/dev/null 2>&1 && adb shell cat /sdcard/agentra_ui.xml",
                timeoutMs = 15_000L
            )
        }.getOrNull()
        if (res != null && res.exitCode == 0 && res.output.contains("<hierarchy")) {
            JSONObject()
                .put("status", "SUCCESS")
                .put("engine", "adb_uiautomator")
                .put("ui_xml", res.output.take(14_000))
        } else {
            JSONObject().put("error", "ADB UI hierarchy dump failed: ${res?.output?.take(200) ?: "no output"}")
        }
    }

    private suspend fun runAdbAction(
        command: String,
        successKey: String,
        extra: JSONObject = JSONObject()
    ): JSONObject = withContext(Dispatchers.IO) {
        val res = runCatching {
            LocalTerminalBridge.instance.executeCommand(command, timeoutMs = 15_000L)
        }.getOrNull()
        val ok = res != null && res.exitCode == 0
        JSONObject(extra.toString()).apply {
            put(successKey, ok)
            put("engine", "adb_shell")
            if (!ok) put("error", "ADB command failed: ${res?.output?.take(200) ?: "unknown error"}")
        }
    }

    private fun executeDeepLink(ctx: Context, args: JSONObject): JSONObject {
        val rawUri = args.optString("uri", "").trim()
        val pkg = args.optString("package_name", "").trim()
        if (rawUri.isBlank()) return JSONObject().put("error", "Missing 'uri' parameter.")
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rawUri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (pkg.isNotBlank()) setPackage(pkg)
            }
            ctx.startActivity(intent)
            JSONObject().put("status", "SUCCESS").put("opened_uri", rawUri).put("package_name", pkg)
        } catch (e: Exception) {
            JSONObject().put("error", "Failed to open deep link '$rawUri': ${e.message}")
        }
    }

    private fun executeShareFile(ctx: Context, args: JSONObject): JSONObject {
        val rawPath = args.optString("file_path", "").trim()
        val pkg = args.optString("package_name", "").trim()
        val caption = args.optString("caption", "").trim()
        val explicitMime = args.optString("mime_type", "").trim()
        if (rawPath.isBlank()) return JSONObject().put("error", "Missing 'file_path' parameter.")
        val file = File(rawPath)
        if (!file.exists()) return JSONObject().put("error", "File not found: $rawPath")

        return try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val ext = file.extension.lowercase()
            val mime = explicitMime.ifBlank {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                    ?: when (ext) {
                        "mp4", "mov", "mkv", "webm" -> "video/*"
                        "jpg", "jpeg", "png", "webp" -> "image/*"
                        else -> "*/*"
                    }
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                if (pkg.isNotBlank()) setPackage(pkg)
            }
            val launchIntent = if (pkg.isNotBlank()) {
                shareIntent
            } else {
                Intent.createChooser(shareIntent, "Share via Agentra").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(launchIntent)
            JSONObject()
                .put("status", "SUCCESS")
                .put("shared_file", file.absolutePath)
                .put("mime_type", mime)
                .put("package_name", pkg)
        } catch (e: Exception) {
            JSONObject().put("error", "Failed to share file '$rawPath': ${e.message}")
        }
    }

    private fun dispatchCustomSwipe(
        service: AccessibilityService,
        sx: Float,
        sy: Float,
        ex: Float,
        ey: Float,
        durationMs: Long
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply {
            moveTo(sx, sy)
            lineTo(ex, ey)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceIn(50L, 4000L))
        return runCatching {
            service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        }.getOrDefault(false)
    }

    private fun directionToCoords(ctx: Context, dir: String): IntArray {
        val dm = ctx.resources.displayMetrics
        val w = dm.widthPixels.coerceAtLeast(720)
        val h = dm.heightPixels.coerceAtLeast(1280)
        return when (dir.uppercase()) {
            "DOWN" -> intArrayOf(w / 2, (h * 0.72f).toInt(), w / 2, (h * 0.25f).toInt())
            "UP" -> intArrayOf(w / 2, (h * 0.28f).toInt(), w / 2, (h * 0.75f).toInt())
            "LEFT" -> intArrayOf((w * 0.80f).toInt(), h / 2, (w * 0.20f).toInt(), h / 2)
            "RIGHT" -> intArrayOf((w * 0.20f).toInt(), h / 2, (w * 0.80f).toInt(), h / 2)
            else -> intArrayOf(w / 2, (h * 0.72f).toInt(), w / 2, (h * 0.25f).toInt())
        }
    }

    private fun scaleBitmapMaxWidth(source: Bitmap, maxWidth: Int): Bitmap {
        if (source.width <= maxWidth || source.width <= 0) return source
        val ratio = maxWidth.toFloat() / source.width.toFloat()
        val targetH = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, maxWidth, targetH, true)
    }

    private fun outcomeToMcpJson(outcome: com.jarvisai.assistant.service.ToolExecutionOutcome): JSONObject {
        val base = runCatching { JSONObject(outcome.responseJson.toString()) }.getOrElse { JSONObject() }
        base.put("status", outcome.status)
        base.put("executed", outcome.executed)
        base.put("message", outcome.message)
        base.put("spoken_summary", outcome.spokenHinglishSummary)
        if (!outcome.executed && !base.has("error")) {
            base.put("error", outcome.message.ifBlank { outcome.errorCode ?: "Tool execution failed" })
        }
        return base
    }

    private fun buildAccessibilityUnavailableError(ctx: Context, toolName: String): JSONObject {
        val malfunctioning = JarvisAccessibilityService.isAccessibilityMalfunctioning(ctx)
        val adbConnected = isAdbAvailable()
        val msg = if (malfunctioning) {
            "Agentra Accessibility Service is enabled in Android Settings but currently disconnected (malfunctioning after update/battery kill). Toggle Agentra OFF and ON once in Accessibility Settings, or call android_open_accessibility_settings."
        } else {
            "Agentra Accessibility Service is not enabled and Wireless ADB is disconnected. Call android_open_accessibility_settings so the user can enable Agentra Accessibility for full screen control."
        }
        return JSONObject()
            .put("error", msg)
            .put("tool", toolName)
            .put("accessibility_running", false)
            .put("accessibility_malfunctioning", malfunctioning)
            .put("adb_connected", adbConnected)
            .put("action_hint", "Call android_open_accessibility_settings to open Accessibility Settings directly, or use non-screen tools like android_launch_app, android_control_hardware, android_share_file, android_deep_link.")
    }

    private fun toolSpec(
        name: String,
        desc: String,
        required: List<String>,
        vararg props: Pair<String, String>
    ): JSONObject = JSONObject().apply {
        put("name", name)
        put("description", desc)
        val pObj = JSONObject()
        props.forEach { (pName, pType) ->
            pObj.put(pName, JSONObject().apply { put("type", pType) })
        }
        put("inputSchema", JSONObject().apply {
            put("type", "object")
            put("properties", pObj)
            put("required", JSONArray(required))
        })
    }
}
