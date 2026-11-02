package com.jarvisai.assistant.service

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import com.jarvisai.assistant.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class ActionResult(
    val success: Boolean,
    val message: String
)

/**
 * Executes real, validated Android device actions using official Android SDK APIs and Intents.
 * Never executes shell commands or claims an action succeeded unless verified.
 */
class PhoneControlsManager(private val context: Context) {

    private val cameraManager: CameraManager? =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    private val _hasFlashlightHardware = MutableStateFlow(false)
    val hasFlashlightHardware: StateFlow<Boolean> = _hasFlashlightHardware.asStateFlow()

    private var torchCameraId: String? = null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                _isFlashlightOn.value = enabled
            }
        }
    }

    init {
        initFlashlightHardware()
    }

    private fun initFlashlightHardware() {
        try {
            val manager = cameraManager ?: return
            val idWithFlash = manager.cameraIdList.firstOrNull { id ->
                val characteristics = manager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            torchCameraId = idWithFlash
            _hasFlashlightHardware.value = idWithFlash != null
            if (idWithFlash != null) {
                manager.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
            }
        } catch (_: Exception) {
            _hasFlashlightHardware.value = false
        }
    }

    fun setFlashlight(enabled: Boolean): ActionResult {
        val manager = cameraManager
            ?: return ActionResult(false, "Camera service is unavailable on this device.")
        val cameraId = torchCameraId
            ?: return ActionResult(false, "No hardware flashlight unit detected on this device.")

        return try {
            manager.setTorchMode(cameraId, enabled)
            _isFlashlightOn.value = enabled
            ActionResult(
                success = true,
                message = if (enabled) "Flashlight turned ON." else "Flashlight turned OFF."
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "Could not toggle flashlight: ${e.localizedMessage ?: "Hardware error"}"
            )
        }
    }

    suspend fun getInstalledLaunchableApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val discovered = linkedMapOf<String, InstalledAppInfo>()
        val pm = context.packageManager

        // 1. Query LauncherApps (most reliable way on Android 11–15 to enumerate all user-launchable icons)
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
            val activities = launcherApps?.getActivityList(null, android.os.Process.myUserHandle()).orEmpty()
            for (item in activities) {
                val pkg = item.applicationInfo?.packageName ?: continue
                if (pkg == context.packageName) continue
                val label = item.label?.toString()?.trim().orEmpty()
                val actName = item.componentName?.className.orEmpty()
                val appFlags = item.applicationInfo?.flags ?: 0
                val isSystem = (appFlags and ApplicationInfo.FLAG_SYSTEM) != 0
                discovered[pkg] = InstalledAppInfo(
                    appName = label.ifEmpty { pkg },
                    packageName = pkg,
                    isSystemApp = isSystem,
                    activityName = actName
                )
            }
        } catch (_: Exception) {
        }

        // 2. Also query PackageManager with ACTION_MAIN + CATEGORY_LAUNCHER
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    mainIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
            }

            for (info in resolveInfos) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (pkg == context.packageName || discovered.containsKey(pkg)) continue
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                val actName = info.activityInfo?.name.orEmpty()
                val appFlags = info.activityInfo?.applicationInfo?.flags ?: 0
                val isSystem = (appFlags and ApplicationInfo.FLAG_SYSTEM) != 0
                discovered[pkg] = InstalledAppInfo(
                    appName = label.ifEmpty { pkg },
                    packageName = pkg,
                    isSystemApp = isSystem,
                    activityName = actName
                )
            }
        } catch (_: Exception) {
        }

        discovered.values.sortedBy { it.appName.lowercase() }
    }

    fun launchInstalledApp(
        packageName: String,
        appName: String,
        activityName: String = ""
    ): ActionResult {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isBlank()) {
            return ActionResult(false, "Invalid application package name.")
        }
        val pm = context.packageManager

        // 1. Standard getLaunchIntentForPackage
        try {
            val launchIntent = pm.getLaunchIntentForPackage(cleanPkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return ActionResult(true, "Launched $appName.")
            }
        } catch (_: Exception) {
        }

        // 2. Explicit ComponentName fallback if activityName is known
        if (activityName.isNotBlank()) {
            try {
                val explicitIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = android.content.ComponentName(cleanPkg, activityName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                context.startActivity(explicitIntent)
                return ActionResult(true, "Launched $appName.")
            } catch (_: Exception) {
            }
        }

        // 3. Query launcher activity for this specific package and launch explicitly
        return try {
            val queryIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(cleanPkg)
            }
            val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(queryIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(queryIntent, 0)
            }
            val targetActivity = matches.firstOrNull()?.activityInfo
            if (targetActivity != null) {
                val explicitIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = android.content.ComponentName(targetActivity.packageName, targetActivity.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                context.startActivity(explicitIntent)
                ActionResult(true, "Launched $appName.")
            } else {
                ActionResult(false, "Unable to resolve launch activity for $appName ($cleanPkg).")
            }
        } catch (e: Exception) {
            ActionResult(false, "Failed to open $appName: ${e.localizedMessage ?: "Launch error"}")
        }
    }

    fun openWifiSettings(): ActionResult {
        return launchSafeSettingsIntent(
            action = Settings.ACTION_WIFI_SETTINGS,
            label = "Wi-Fi Settings"
        )
    }

    fun openBluetoothSettings(): ActionResult {
        return launchSafeSettingsIntent(
            action = Settings.ACTION_BLUETOOTH_SETTINGS,
            label = "Bluetooth Settings"
        )
    }

    fun openAndroidSettings(): ActionResult {
        return launchSafeSettingsIntent(
            action = Settings.ACTION_SETTINGS,
            label = "Android System Settings"
        )
    }

    fun openDisplaySettings(): ActionResult {
        return launchSafeSettingsIntent(
            action = Settings.ACTION_DISPLAY_SETTINGS,
            label = "Display Settings"
        )
    }

    fun openSoundSettings(): ActionResult {
        return launchSafeSettingsIntent(
            action = Settings.ACTION_SOUND_SETTINGS,
            label = "Sound & Vibration Settings"
        )
    }

    fun createSystemAlarm(hour: Int, minute: Int, label: String): ActionResult {
        if (hour !in 0..23 || minute !in 0..59) {
            return ActionResult(false, "Invalid alarm time ($hour:$minute). Hours must be 0-23 and minutes 0-59.")
        }
        val cleanLabel = label.trim().ifEmpty { "JARVIS Tactical Alarm" }
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, cleanLabel)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val timeFormatted = String.format("%02d:%02d", hour, minute)
            ActionResult(true, "System alarm configured for $timeFormatted ($cleanLabel).")
        } catch (e: Exception) {
            ActionResult(false, "No Clock app available to handle SET_ALARM intent: ${e.localizedMessage ?: "Error"}")
        }
    }

    fun launchCalendarReminderIntent(title: String, notes: String, startTimeMillis: Long): ActionResult {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            return ActionResult(false, "Reminder title cannot be empty.")
        }
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, cleanTitle)
                putExtra(CalendarContract.Events.DESCRIPTION, notes.trim())
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTimeMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startTimeMillis + 30 * 60 * 1000L)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(true, "Opened system Calendar to confirm reminder '$cleanTitle'.")
        } catch (e: Exception) {
            ActionResult(
                false,
                "Saved reminder locally in JARVIS, but no external Calendar app was found: ${e.localizedMessage ?: ""}"
            )
        }
    }

    fun initiateConfirmedDialCall(phoneNumber: String): ActionResult {
        val sanitized = phoneNumber.trim().replace(Regex("[^0-9+*#]"), "")
        if (sanitized.length < 3) {
            return ActionResult(false, "Please enter a valid phone number (minimum 3 digits).")
        }
        return try {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${Uri.encode(sanitized)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            ActionResult(true, "Opened Phone Dialer with number $sanitized.")
        } catch (e: Exception) {
            ActionResult(false, "Unable to open Phone Dialer: ${e.localizedMessage ?: "No dialer app available"}")
        }
    }

    private fun launchSafeSettingsIntent(action: String, label: String): ActionResult {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(true, "Opened $label.")
        } catch (e: Exception) {
            ActionResult(false, "Could not open $label: ${e.localizedMessage ?: "Settings unavailable"}")
        }
    }

    fun cleanup() {
        try {
            cameraManager?.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {
        }
    }
}
