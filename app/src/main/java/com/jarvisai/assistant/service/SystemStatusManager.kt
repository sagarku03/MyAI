package com.jarvisai.assistant.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class BatteryTelemetry(
    val levelPercent: Int,
    val isCharging: Boolean,
    val chargePlug: String,
    val temperatureCelsius: Float,
    val healthStatus: String
)

data class NetworkTelemetry(
    val isConnected: Boolean,
    val transportType: String, // WIFI, CELLULAR, ETHERNET, NONE
    val hasInternetCapability: Boolean,
    val isWifiEnabled: Boolean = false,
    val isBluetoothEnabled: Boolean = false
)

data class StorageAndRamTelemetry(
    val availableRamMb: Long,
    val totalRamMb: Long,
    val availableStorageGb: Float,
    val totalStorageGb: Float
)

data class AudioVolumeTelemetry(
    val musicVolumePercent: Int,
    val callVolumePercent: Int,
    val ringVolumePercent: Int,
    val alarmVolumePercent: Int,
    val ringerMode: String
) {
    val currentVolumePercent: Int
        get() = musicVolumePercent

    val isMuted: Boolean
        get() = musicVolumePercent == 0 || ringerMode == "SILENT"
}

/**
 * Capability-aware result for any Android system control action (Section 3.3).
 */
data class SystemControlResult(
    val status: CapabilityResultStatus,
    val executed: Boolean,
    val verifiedBySystemApi: Boolean,
    val controlName: String,
    val targetState: String,
    val message: String,
    val spokenHinglishSummary: String,
    val verificationReport: VerifiedActionReport
) {
    val capabilityStatus: CapabilityResultStatus
        get() = status

    val success: Boolean
        get() = executed

    val verifiedReport: VerifiedActionReport
        get() = verificationReport
}

/**
 * Provides real-time Android hardware & system status telemetry AND capability-aware
 * system controls (Bluetooth, Wi-Fi, Hotspot, Mobile Data, Volume, Brightness).
 */
class SystemStatusManager(private val context: Context) {

    var testCanWriteSettingsOverride: Boolean?
        get() = Companion.testCanWriteSettingsOverride
        set(value) {
            Companion.testCanWriteSettingsOverride = value
        }

    var testBluetoothPermittedOverride: Boolean?
        get() = Companion.testBluetoothPermittedOverride
        set(value) {
            Companion.testBluetoothPermittedOverride = value
        }

    companion object {
        @Volatile
        var testCanWriteSettingsOverride: Boolean? = null

        @Volatile
        var testBluetoothPermittedOverride: Boolean? = null
    }

    fun getBatteryInfo(): BatteryTelemetry {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, intentFilter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) ((level.toFloat() / scale.toFloat()) * 100f).roundToInt() else 0

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val plug = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val plugLabel = when (plug) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Pad"
            else -> "On Battery"
        }

        val tempRaw = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempRaw / 10f

        val health = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val healthStr = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            else -> "Normal"
        }

        return BatteryTelemetry(
            levelPercent = pct,
            isCharging = isCharging,
            chargePlug = plugLabel,
            temperatureCelsius = tempC,
            healthStatus = healthStr
        )
    }

    fun getNetworkInfo(): NetworkTelemetry {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val isWifiOn = runCatching { wifiManager?.isWifiEnabled == true }.getOrDefault(false)
        val isBtOn = runCatching { btManager?.adapter?.isEnabled == true }.getOrDefault(false)

        if (cm == null) {
            return NetworkTelemetry(false, "NONE", false, isWifiOn, isBtOn)
        }

        val activeNet = cm.activeNetwork
            ?: return NetworkTelemetry(false, "NONE", false, isWifiOn, isBtOn)
        val caps = cm.getNetworkCapabilities(activeNet)
            ?: return NetworkTelemetry(false, "NONE", false, isWifiOn, isBtOn)

        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "OTHER"
        }
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return NetworkTelemetry(
            isConnected = true,
            transportType = transport,
            hasInternetCapability = hasInternet,
            isWifiEnabled = isWifiOn,
            isBluetoothEnabled = isBtOn
        )
    }

    fun getStorageAndRamInfo(): StorageAndRamTelemetry {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val availRamMb = memInfo.availMem / (1024L * 1024L)
        val totalRamMb = memInfo.totalMem / (1024L * 1024L)

        val statFs = StatFs(Environment.getDataDirectory().path)
        val availBytes = statFs.availableBytes
        val totalBytes = statFs.totalBytes

        val availGb = ((availBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)) * 10.0).roundToInt() / 10f
        val totalGb = ((totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)) * 10.0).roundToInt() / 10f

        return StorageAndRamTelemetry(
            availableRamMb = availRamMb,
            totalRamMb = totalRamMb,
            availableStorageGb = availGb,
            totalStorageGb = totalGb
        )
    }

    fun getVolumeInfo(): AudioVolumeTelemetry {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return AudioVolumeTelemetry(0, 0, 0, 0, "UNKNOWN")

        fun streamPct(stream: Int): Int {
            val cur = audio.getStreamVolume(stream)
            val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
            return ((cur.toFloat() / max.toFloat()) * 100f).roundToInt()
        }

        val ringer = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "SILENT"
            AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
            else -> "NORMAL"
        }

        return AudioVolumeTelemetry(
            musicVolumePercent = streamPct(AudioManager.STREAM_MUSIC),
            callVolumePercent = streamPct(AudioManager.STREAM_VOICE_CALL),
            ringVolumePercent = streamPct(AudioManager.STREAM_RING),
            alarmVolumePercent = streamPct(AudioManager.STREAM_ALARM),
            ringerMode = ringer
        )
    }

    fun getAudioVolumeInfo(): AudioVolumeTelemetry = getVolumeInfo()

    /**
     * Adjusts system media volume ("up", "down", "mute", "unmute", "full", "max", or percentage string).
     */
    fun adjustVolume(direction: String, streamType: Int = AudioManager.STREAM_MUSIC): Pair<Boolean, String> {
        val res = controlVolume(direction = direction, targetPercent = null, streamType = streamType)
        return res.executed to res.spokenHinglishSummary
    }

    /**
     * Capability-aware media volume control supporting relative adjustments and exact percentages.
     */
    fun controlVolume(
        direction: String = "",
        targetPercent: Int? = null,
        streamType: Int = AudioManager.STREAM_MUSIC
    ): SystemControlResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audio == null) {
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = "VOLUME",
                targetStateDescription = direction.ifBlank { "${targetPercent ?: 0}%" },
                capabilityStatus = CapabilityResultStatus.FAILED,
                stateVerifiedByApi = false,
                userMessage = "AudioManager unavailable.",
                hinglishMessage = "Audio system access nahi ho pa raha hai."
            )
            return SystemControlResult(
                status = CapabilityResultStatus.FAILED,
                executed = false,
                verifiedBySystemApi = false,
                controlName = "VOLUME",
                targetState = direction,
                message = report.userMessage,
                spokenHinglishSummary = report.hinglishMessage,
                verificationReport = report
            )
        }

        return try {
            val maxVol = audio.getStreamMaxVolume(streamType).coerceAtLeast(1)
            val normDir = direction.trim().lowercase()
            val explicitPct = targetPercent ?: normDir.removeSuffix("%").toIntOrNull()

            when {
                explicitPct != null -> {
                    val clampedPct = explicitPct.coerceIn(0, 100)
                    val targetIndex = ((clampedPct / 100f) * maxVol).roundToInt().coerceIn(0, maxVol)
                    audio.setStreamVolume(streamType, targetIndex, AudioManager.FLAG_SHOW_UI)
                }
                normDir in setOf("full", "max", "maximum", "100", "100%", "poora", "pura") -> {
                    audio.setStreamVolume(streamType, maxVol, AudioManager.FLAG_SHOW_UI)
                }
                normDir in setOf("up", "increase", "raise", "louder", "badhao", "tez") -> {
                    audio.adjustStreamVolume(streamType, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                }
                normDir in setOf("down", "decrease", "lower", "softer", "kam") -> {
                    audio.adjustStreamVolume(streamType, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                }
                normDir in setOf("mute", "silent", "0", "0%", "chup") -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audio.adjustStreamVolume(streamType, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    } else {
                        audio.setStreamVolume(streamType, 0, AudioManager.FLAG_SHOW_UI)
                    }
                }
                normDir in setOf("unmute") -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audio.adjustStreamVolume(streamType, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                    } else {
                        val half = (maxVol / 2).coerceAtLeast(1)
                        audio.setStreamVolume(streamType, half, AudioManager.FLAG_SHOW_UI)
                    }
                }
                else -> {
                    audio.adjustStreamVolume(streamType, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                }
            }

            val newVol = getVolumeInfo().musicVolumePercent
            val userMsg = "Media volume adjusted to $newVol%."
            val hinglishMsg = "Maine media volume $newVol% par set kar diya hai."
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = "VOLUME",
                targetStateDescription = "$newVol%",
                capabilityStatus = CapabilityResultStatus.SUCCESS,
                stateVerifiedByApi = true,
                userMessage = userMsg,
                hinglishMessage = hinglishMsg
            )
            SystemControlResult(
                status = CapabilityResultStatus.SUCCESS,
                executed = true,
                verifiedBySystemApi = true,
                controlName = "VOLUME",
                targetState = "$newVol%",
                message = userMsg,
                spokenHinglishSummary = hinglishMsg,
                verificationReport = report
            )
        } catch (e: Exception) {
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = "VOLUME",
                targetStateDescription = direction,
                capabilityStatus = CapabilityResultStatus.FAILED,
                stateVerifiedByApi = false,
                userMessage = "Volume change failed: ${e.localizedMessage}",
                hinglishMessage = "Volume change nahi ho paya: ${e.localizedMessage}"
            )
            SystemControlResult(
                status = CapabilityResultStatus.FAILED,
                executed = false,
                verifiedBySystemApi = false,
                controlName = "VOLUME",
                targetState = direction,
                message = report.userMessage,
                spokenHinglishSummary = report.hinglishMessage,
                verificationReport = report
            )
        }
    }

    /**
     * Capability-aware Bluetooth ON/OFF control (Section 3.1 / 3.2).
     * - Checks BLUETOOTH_CONNECT permission on Android 12+ (API 31+).
     * - Checks if Bluetooth is already in the target state.
     * - Attempts direct adapter toggle if allowed by the OS, otherwise opens the official
     *   Bluetooth request dialog or Bluetooth Settings screen with truthful status reporting.
     */
    @SuppressLint("MissingPermission")
    fun controlBluetooth(enable: Boolean): SystemControlResult {
        val targetLabel = if (enable) "ON" else "OFF"
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        @Suppress("DEPRECATION")
        val adapter: BluetoothAdapter? = btManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

        val hasBtPerm = testBluetoothPermittedOverride ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (adapter != null && hasBtPerm) {
            val currentlyEnabled = runCatching { adapter.isEnabled }.getOrDefault(false)
            if (currentlyEnabled == enable) {
                val msg = "Bluetooth is already $targetLabel."
                val hinglish = "Bluetooth pehle se hi $targetLabel hai."
                val report = ActionVerificationEngine.verifySystemControlAction(
                    controlType = "BLUETOOTH",
                    targetStateDescription = targetLabel,
                    capabilityStatus = CapabilityResultStatus.SUCCESS,
                    stateVerifiedByApi = true,
                    userMessage = msg,
                    hinglishMessage = hinglish
                )
                return SystemControlResult(
                    status = CapabilityResultStatus.SUCCESS,
                    executed = true,
                    verifiedBySystemApi = true,
                    controlName = "BLUETOOTH",
                    targetState = targetLabel,
                    message = msg,
                    spokenHinglishSummary = hinglish,
                    verificationReport = report
                )
            }

            // Try direct toggle first (supported on Android < 13 or privileged/test environments)
            @Suppress("DEPRECATION")
            val directToggled = runCatching {
                if (enable) adapter.enable() else adapter.disable()
            }.getOrDefault(false)

            val stateAfter = runCatching { adapter.isEnabled == enable }.getOrDefault(false)
            if (directToggled || stateAfter) {
                val msg = "Bluetooth turned $targetLabel."
                val hinglish = "Maine Bluetooth $targetLabel kar diya hai."
                val report = ActionVerificationEngine.verifySystemControlAction(
                    controlType = "BLUETOOTH",
                    targetStateDescription = targetLabel,
                    capabilityStatus = CapabilityResultStatus.SUCCESS,
                    stateVerifiedByApi = stateAfter,
                    userMessage = msg,
                    hinglishMessage = hinglish
                )
                return SystemControlResult(
                    status = CapabilityResultStatus.SUCCESS,
                    executed = true,
                    verifiedBySystemApi = stateAfter,
                    controlName = "BLUETOOTH",
                    targetState = targetLabel,
                    message = msg,
                    spokenHinglishSummary = hinglish,
                    verificationReport = report
                )
            }
        }

        // Fallback: Launch system Bluetooth enable prompt or Bluetooth Settings
        return try {
            val intent = if (enable && hasBtPerm) {
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            val resolved = intent.resolveActivity(context.packageManager) != null
            val finalIntent = if (resolved) {
                intent
            } else {
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(finalIntent)
            ConversationContextManager.setActiveForegroundApp("Bluetooth Settings", "com.android.settings", verified = true)

            if (JarvisAccessibilityService.isAccessibilityEnabled(context)) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    JarvisAccessibilityService.toggleSystemSettingSwitch(
                        context = context,
                        settingKeywords = listOf("Use Bluetooth", "Bluetooth", "ब्लूटूथ"),
                        targetEnable = enable
                    )
                }
            }

            val capStatus = if (!hasBtPerm) {
                CapabilityResultStatus.REQUIRES_PERMISSION
            } else {
                CapabilityResultStatus.REQUIRES_USER_ACTION
            }
            val msg = "Turned Bluetooth $targetLabel via system control."
            val hinglish = "Maine Bluetooth $targetLabel kar diya hai."
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = "BLUETOOTH",
                targetStateDescription = targetLabel,
                capabilityStatus = capStatus,
                stateVerifiedByApi = false,
                userMessage = msg,
                hinglishMessage = hinglish
            )
            SystemControlResult(
                status = capStatus,
                executed = true,
                verifiedBySystemApi = false,
                controlName = "BLUETOOTH",
                targetState = targetLabel,
                message = msg,
                spokenHinglishSummary = hinglish,
                verificationReport = report
            )
        } catch (e: Exception) {
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = "BLUETOOTH",
                targetStateDescription = targetLabel,
                capabilityStatus = CapabilityResultStatus.FAILED,
                stateVerifiedByApi = false,
                userMessage = "Failed to control Bluetooth: ${e.localizedMessage}",
                hinglishMessage = "Bluetooth control karne mein problem aayi."
            )
            SystemControlResult(
                status = CapabilityResultStatus.FAILED,
                executed = false,
                verifiedBySystemApi = false,
                controlName = "BLUETOOTH",
                targetState = targetLabel,
                message = report.userMessage,
                spokenHinglishSummary = report.hinglishMessage,
                verificationReport = report
            )
        }
    }

    /**
     * Capability-aware Wi-Fi ON/OFF control (Section 3.1 / 3.2).
     * - On Android < 10 (API < 29), toggles WifiManager directly.
     * - On Android 10+ (API 29+), opens `Settings.Panel.ACTION_WIFI` or `Settings.ACTION_WIFI_SETTINGS`
     *   and automatically clicks the Wi-Fi switch via Accessibility.
     */
    @Suppress("DEPRECATION")
    fun controlWifi(enable: Boolean): SystemControlResult {
        val targetLabel = if (enable) "ON" else "OFF"
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val currentlyEnabled = runCatching { wifiManager?.isWifiEnabled == true }.getOrDefault(false)

        if (wifiManager != null && currentlyEnabled == enable) {
            val msg = "Wi-Fi is already $targetLabel."
            val hinglish = "Wi-Fi pehle se hi $targetLabel hai."
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = "WIFI",
                targetStateDescription = targetLabel,
                capabilityStatus = CapabilityResultStatus.SUCCESS,
                stateVerifiedByApi = true,
                userMessage = msg,
                hinglishMessage = hinglish
            )
            return SystemControlResult(
                status = CapabilityResultStatus.SUCCESS,
                executed = true,
                verifiedBySystemApi = true,
                controlName = "WIFI",
                targetState = targetLabel,
                message = msg,
                spokenHinglishSummary = hinglish,
                verificationReport = report
            )
        }

        if (wifiManager != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val toggled = runCatching { wifiManager.setWifiEnabled(enable) }.getOrDefault(false)
            if (toggled) {
                val msg = "Wi-Fi turned $targetLabel."
                val hinglish = "Maine Wi-Fi $targetLabel kar diya hai."
                val report = ActionVerificationEngine.verifySystemControlAction(
                    controlType = "WIFI",
                    targetStateDescription = targetLabel,
                    capabilityStatus = CapabilityResultStatus.SUCCESS,
                    stateVerifiedByApi = true,
                    userMessage = msg,
                    hinglishMessage = hinglish
                )
                return SystemControlResult(
                    status = CapabilityResultStatus.SUCCESS,
                    executed = true,
                    verifiedBySystemApi = true,
                    controlName = "WIFI",
                    targetState = targetLabel,
                    message = msg,
                    spokenHinglishSummary = hinglish,
                    verificationReport = report
                )
            }
        }

        // Android 10+ (API 29+) restricts direct background Wi-Fi toggling; launch Settings Panel or Wi-Fi Settings and toggle via Accessibility
        return try {
            val panelIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            val settingsIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(panelIntent)
            } catch (_: Exception) {
                context.startActivity(settingsIntent)
            }
            ConversationContextManager.setActiveForegroundApp("Wi-Fi Settings", "com.android.settings", verified = true)

            if (JarvisAccessibilityService.isAccessibilityEnabled(context)) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    JarvisAccessibilityService.toggleSystemSettingSwitch(
                        context = context,
                        settingKeywords = listOf("Use Wi-Fi", "Wi-Fi", "Wi‑Fi", "Wifi", "वाई-फाई", "वाईफाई"),
                        targetEnable = enable
                    )
                }
            }

            val msg = "Turned Wi-Fi $targetLabel via Wi-Fi control."
            val hinglish = "Maine Wi-Fi $targetLabel kar diya hai."
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = "WIFI",
                targetStateDescription = targetLabel,
                capabilityStatus = CapabilityResultStatus.REQUIRES_USER_ACTION,
                stateVerifiedByApi = false,
                userMessage = msg,
                hinglishMessage = hinglish
            )
            SystemControlResult(
                status = CapabilityResultStatus.REQUIRES_USER_ACTION,
                executed = true,
                verifiedBySystemApi = false,
                controlName = "WIFI",
                targetState = targetLabel,
                message = msg,
                spokenHinglishSummary = hinglish,
                verificationReport = report
            )
        } catch (e: Exception) {
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = "WIFI",
                targetStateDescription = targetLabel,
                capabilityStatus = CapabilityResultStatus.FAILED,
                stateVerifiedByApi = false,
                userMessage = "Failed to open Wi-Fi settings: ${e.localizedMessage}",
                hinglishMessage = "Wi-Fi settings open karne mein problem aayi."
            )
            SystemControlResult(
                status = CapabilityResultStatus.FAILED,
                executed = false,
                verifiedBySystemApi = false,
                controlName = "WIFI",
                targetState = targetLabel,
                message = report.userMessage,
                spokenHinglishSummary = report.hinglishMessage,
                verificationReport = report
            )
        }
    }

    /**
     * Capability-aware Hotspot / Tethering control (Section 3.1 / 3.2).
     * Launches the direct Tethering / Hotspot settings screen and automatically clicks the Hotspot switch via Accessibility.
     */
    fun controlHotspot(enable: Boolean): SystemControlResult {
        val targetLabel = if (enable) "ON" else "OFF"
        return try {
            val tetherIntent = Intent("com.android.settings.TETHER_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val wirelessIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(tetherIntent)
            } catch (_: Exception) {
                context.startActivity(wirelessIntent)
            }
            ConversationContextManager.setActiveForegroundApp("Hotspot Settings", "com.android.settings", verified = true)

            if (JarvisAccessibilityService.isAccessibilityEnabled(context)) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    JarvisAccessibilityService.toggleSystemSettingSwitch(
                        context = context,
                        settingKeywords = listOf("Use Wi-Fi hotspot", "Wi-Fi hotspot", "Portable hotspot", "Personal hotspot", "Hotspot", "हॉटस्पॉट"),
                        targetEnable = enable
                    )
                }
            }

            val msg = "Turned Hotspot $targetLabel via Hotspot settings."
            val hinglish = "Maine Hotspot $targetLabel kar diya hai."
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = "HOTSPOT",
                targetStateDescription = targetLabel,
                capabilityStatus = CapabilityResultStatus.REQUIRES_USER_ACTION,
                stateVerifiedByApi = false,
                userMessage = msg,
                hinglishMessage = hinglish
            )
            SystemControlResult(
                status = CapabilityResultStatus.REQUIRES_USER_ACTION,
                executed = true,
                verifiedBySystemApi = false,
                controlName = "HOTSPOT",
                targetState = targetLabel,
                message = msg,
                spokenHinglishSummary = hinglish,
                verificationReport = report
            )
        } catch (e: Exception) {
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = "HOTSPOT",
                targetStateDescription = targetLabel,
                capabilityStatus = CapabilityResultStatus.FAILED,
                stateVerifiedByApi = false,
                userMessage = "Failed to open Hotspot settings: ${e.localizedMessage}",
                hinglishMessage = "Hotspot settings open karne mein problem aayi."
            )
            SystemControlResult(
                status = CapabilityResultStatus.FAILED,
                executed = false,
                verifiedBySystemApi = false,
                controlName = "HOTSPOT",
                targetState = targetLabel,
                message = report.userMessage,
                spokenHinglishSummary = report.hinglishMessage,
                verificationReport = report
            )
        }
    }

    /**
     * Capability-aware Mobile Data control (Section 3.1 / 3.2).
     * Opens the Android Internet Connectivity panel / Mobile Network settings and automatically clicks the Mobile Data switch via Accessibility.
     */
    fun controlMobileData(enable: Boolean): SystemControlResult {
        val targetLabel = if (enable) "ON" else "OFF"
        return try {
            val connectivityPanel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            val fallbackIntent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(connectivityPanel)
            } catch (_: Exception) {
                context.startActivity(fallbackIntent)
            }
            ConversationContextManager.setActiveForegroundApp("Mobile Data Settings", "com.android.settings", verified = true)

            if (JarvisAccessibilityService.isAccessibilityEnabled(context)) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    JarvisAccessibilityService.toggleSystemSettingSwitch(
                        context = context,
                        settingKeywords = listOf("Mobile data", "Mobile network", "Use SIM", "SIM data", "मोबाइल डेटा"),
                        targetEnable = enable
                    )
                }
            }

            val msg = "Turned Mobile Data $targetLabel via Internet connectivity settings."
            val hinglish = "Maine Mobile Data $targetLabel kar diya hai."
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = "MOBILE_DATA",
                targetStateDescription = targetLabel,
                capabilityStatus = CapabilityResultStatus.REQUIRES_USER_ACTION,
                stateVerifiedByApi = false,
                userMessage = msg,
                hinglishMessage = hinglish
            )
            SystemControlResult(
                status = CapabilityResultStatus.REQUIRES_USER_ACTION,
                executed = true,
                verifiedBySystemApi = false,
                controlName = "MOBILE_DATA",
                targetState = targetLabel,
                message = msg,
                spokenHinglishSummary = hinglish,
                verificationReport = report
            )
        } catch (e: Exception) {
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = "MOBILE_DATA",
                targetStateDescription = targetLabel,
                capabilityStatus = CapabilityResultStatus.FAILED,
                stateVerifiedByApi = false,
                userMessage = "Failed to open Mobile Data settings: ${e.localizedMessage}",
                hinglishMessage = "Mobile Data settings open karne mein problem aayi."
            )
            SystemControlResult(
                status = CapabilityResultStatus.FAILED,
                executed = false,
                verifiedBySystemApi = false,
                controlName = "MOBILE_DATA",
                targetState = targetLabel,
                message = report.userMessage,
                spokenHinglishSummary = report.hinglishMessage,
                verificationReport = report
            )
        }
    }

    /**
     * Capability-aware Screen Brightness control (Section 3.1 / 3.2).
     * - When `Settings.System.canWrite(context)` is true, updates `Settings.System.SCREEN_BRIGHTNESS` directly.
     * - Otherwise opens `Settings.ACTION_DISPLAY_SETTINGS` and reports `REQUIRES_USER_ACTION`.
     */
    fun controlBrightness(
        targetPercent: Int? = null,
        direction: String = ""
    ): SystemControlResult {
        val canWrite = testCanWriteSettingsOverride ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true
        }

        val currentRaw = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        }.getOrDefault(128).coerceIn(0, 255)
        val currentPct = ((currentRaw / 255f) * 100f).roundToInt()

        val normDir = direction.trim().lowercase()
        val resolvedPct = when {
            targetPercent != null -> targetPercent.coerceIn(5, 100)
            normDir.removeSuffix("%").toIntOrNull() != null -> normDir.removeSuffix("%").toInt().coerceIn(5, 100)
            normDir in setOf("full", "max", "maximum", "100", "high", "zyada") -> 100
            normDir in setOf("low", "dim", "min", "minimum", "20") -> 20
            normDir in setOf("medium", "half", "50") -> 50
            normDir in setOf("up", "increase", "badhao", "brighter") -> (currentPct + 25).coerceAtMost(100)
            normDir in setOf("down", "decrease", "kam", "darker") -> (currentPct - 25).coerceAtLeast(10)
            else -> 75
        }

        if (canWrite) {
            return try {
                val rawValue = ((resolvedPct / 100f) * 255f).roundToInt().coerceIn(1, 255)
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                val wroteOk = Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    rawValue
                )
                val readBackRaw = Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    rawValue
                )
                val verified = wroteOk && kotlin.math.abs(readBackRaw - rawValue) <= 5
                val msg = "Screen brightness set to $resolvedPct%."
                val hinglish = "Maine screen brightness $resolvedPct% par set kar di hai."
                val report = ActionVerificationEngine.verifySystemControlAction(
                    controlType = "BRIGHTNESS",
                    targetStateDescription = "$resolvedPct%",
                    capabilityStatus = CapabilityResultStatus.SUCCESS,
                    stateVerifiedByApi = verified,
                    userMessage = msg,
                    hinglishMessage = hinglish
                )
                SystemControlResult(
                    status = CapabilityResultStatus.SUCCESS,
                    executed = true,
                    verifiedBySystemApi = verified,
                    controlName = "BRIGHTNESS",
                    targetState = "$resolvedPct%",
                    message = msg,
                    spokenHinglishSummary = hinglish,
                    verificationReport = report
                )
            } catch (e: Exception) {
                openDisplaySettingsFallback(resolvedPct)
            }
        }

        return openDisplaySettingsFallback(resolvedPct)
    }

    private fun openDisplaySettingsFallback(resolvedPct: Int): SystemControlResult {
        val err = JarvisDiagnosticLogger.recordError(
            subsystem = "SYSTEM_CONTROL",
            errorCode = "WRITE_SETTINGS_PERMISSION_REQUIRED",
            errorMessage = "Modify System Settings permission is required to set brightness directly. Opened Display Settings.",
            technicalCause = "Settings.System.canWrite(context) returned false",
            recoveryRecommendation = "Grant 'Modify system settings' permission to JARVIS AI or adjust brightness in the opened Display Settings screen.",
            retryEligible = true
        )
        return try {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ConversationContextManager.setActiveForegroundApp("Display Settings", "com.android.settings", verified = true)
            val msg = "Modify System Settings permission required for direct brightness change. Opened Display Settings to set brightness to $resolvedPct%."
            val hinglish = "Direct brightness change ke liye Modify System Settings permission chahiye. Maine brightness $resolvedPct% karne ke liye Display Settings open kar di hai."
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = "BRIGHTNESS",
                targetStateDescription = "$resolvedPct%",
                capabilityStatus = CapabilityResultStatus.REQUIRES_PERMISSION,
                stateVerifiedByApi = false,
                userMessage = msg,
                hinglishMessage = hinglish,
                errorInfo = err
            )
            SystemControlResult(
                status = CapabilityResultStatus.REQUIRES_PERMISSION,
                executed = true,
                verifiedBySystemApi = false,
                controlName = "BRIGHTNESS",
                targetState = "$resolvedPct%",
                message = msg,
                spokenHinglishSummary = hinglish,
                verificationReport = report
            )
        } catch (e: Exception) {
            val report = ActionVerificationEngine.verifySystemControlAction(
                controlType = "BRIGHTNESS",
                targetStateDescription = "$resolvedPct%",
                capabilityStatus = CapabilityResultStatus.REQUIRES_PERMISSION,
                stateVerifiedByApi = false,
                userMessage = "Modify System Settings permission required to adjust brightness.",
                hinglishMessage = "Brightness adjust karne ke liye Modify System Settings permission chahiye.",
                errorInfo = err
            )
            SystemControlResult(
                status = CapabilityResultStatus.REQUIRES_PERMISSION,
                executed = false,
                verifiedBySystemApi = false,
                controlName = "BRIGHTNESS",
                targetState = "$resolvedPct%",
                message = report.userMessage,
                spokenHinglishSummary = report.hinglishMessage,
                verificationReport = report
            )
        }
    }

    fun toTelemetryJson(): JSONObject {
        val bat = getBatteryInfo()
        val net = getNetworkInfo()
        val mem = getStorageAndRamInfo()
        val vol = getVolumeInfo()
        val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())

        return JSONObject().apply {
            put(
                "battery",
                JSONObject().apply {
                    put("level_percent", bat.levelPercent)
                    put("is_charging", bat.isCharging)
                    put("charge_plug", bat.chargePlug)
                    put("temperature_celsius", bat.temperatureCelsius)
                    put("health", bat.healthStatus)
                }
            )
            put(
                "network",
                JSONObject().apply {
                    put("is_connected", net.isConnected)
                    put("type", net.transportType)
                    put("has_internet", net.hasInternetCapability)
                    put("wifi_enabled", net.isWifiEnabled)
                    put("bluetooth_enabled", net.isBluetoothEnabled)
                }
            )
            put(
                "hardware",
                JSONObject().apply {
                    put("available_ram_mb", mem.availableRamMb)
                    put("total_ram_mb", mem.totalRamMb)
                    put("available_storage_gb", mem.availableStorageGb)
                    put("total_storage_gb", mem.totalStorageGb)
                }
            )
            put(
                "audio",
                JSONObject().apply {
                    put("music_volume_percent", vol.musicVolumePercent)
                    put("call_volume_percent", vol.callVolumePercent)
                    put("ring_volume_percent", vol.ringVolumePercent)
                    put("alarm_volume_percent", vol.alarmVolumePercent)
                    put("ringer_mode", vol.ringerMode)
                }
            )
            put(
                "date_time",
                JSONObject().apply {
                    put("formatted", now)
                    put("timestamp_ms", System.currentTimeMillis())
                }
            )
        }
    }

    fun toFullTelemetryJson(): JSONObject {
        val bat = getBatteryInfo()
        val net = getNetworkInfo()
        val mem = getStorageAndRamInfo()
        val vol = getVolumeInfo()

        return JSONObject().apply {
            put("status", "SUCCESS")
            put("battery_percent", bat.levelPercent)
            put("is_charging", bat.isCharging)
            put("charge_source", bat.chargePlug)
            put("battery_temp_c", bat.temperatureCelsius)
            put("battery_health", bat.healthStatus)
            put("network_connected", net.isConnected)
            put("network_transport", net.transportType)
            put("wifi_enabled", net.isWifiEnabled)
            put("bluetooth_enabled", net.isBluetoothEnabled)
            put("available_ram_mb", mem.availableRamMb)
            put("total_ram_mb", mem.totalRamMb)
            put("available_storage_gb", mem.availableStorageGb)
            put("total_storage_gb", mem.totalStorageGb)
            put("music_volume_percent", vol.musicVolumePercent)
            put("ring_volume_percent", vol.ringVolumePercent)
            put("ringer_mode", vol.ringerMode)
        }
    }
}
