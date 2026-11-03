package com.jarvisai.assistant.service

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceBatteryInfo(
    val levelPercent: Int,
    val isCharging: Boolean,
    val chargePlug: String, // AC, USB, WIRELESS, UNKNOWN
    val health: String
)

data class NetworkStatusInfo(
    val isConnected: Boolean,
    val isWifi: Boolean,
    val isCellular: Boolean,
    val connectionType: String
)

data class HardwareDeviceInfo(
    val deviceModel: String,
    val manufacturer: String,
    val androidVersion: String,
    val apiLevel: Int,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val totalStorageGb: Double,
    val availableStorageGb: Double
)

data class SystemAudioVolumeInfo(
    val currentVolumePercent: Int,
    val maxVolume: Int,
    val currentVolume: Int,
    val isMuted: Boolean
)

/**
 * Reads real-time device hardware, battery, audio, and network telemetry
 * using legitimate, zero-permission or normal-permission Android system services.
 */
class SystemStatusManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    fun getBatteryInfo(): DeviceBatteryInfo {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, filter)
            if (batteryStatus != null) {
                val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0

                val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

                val chargePlug = when (batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                    else -> if (isCharging) "Charging" else "Unplugged"
                }

                val health = when (batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                    else -> "Normal"
                }

                DeviceBatteryInfo(
                    levelPercent = pct,
                    isCharging = isCharging,
                    chargePlug = chargePlug,
                    health = health
                )
            } else {
                DeviceBatteryInfo(50, false, "Unknown", "Good")
            }
        } catch (_: Exception) {
            DeviceBatteryInfo(0, false, "Unknown", "Unavailable")
        }
    }

    fun getNetworkStatus(): NetworkStatusInfo {
        return try {
            val cm = connectivityManager
                ?: return NetworkStatusInfo(false, isWifi = false, isCellular = false, "Disconnected")

            val activeNetwork = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(activeNetwork)
            if (caps == null) {
                return NetworkStatusInfo(false, isWifi = false, isCellular = false, "Disconnected")
            }

            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isCell = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

            val type = when {
                isWifi -> "Wi-Fi (High Speed)"
                isCell -> "Cellular Mobile Data"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth Tethering"
                else -> "Connected"
            }

            NetworkStatusInfo(
                isConnected = hasInternet,
                isWifi = isWifi,
                isCellular = isCell,
                connectionType = type
            )
        } catch (_: Exception) {
            NetworkStatusInfo(false, isWifi = false, isCellular = false, "Unknown")
        }
    }

    fun getHardwareInfo(): HardwareDeviceInfo {
        var totalRamMb = 0L
        var availRamMb = 0L
        try {
            val mi = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(mi)
            totalRamMb = mi.totalMem / (1024 * 1024)
            availRamMb = mi.availMem / (1024 * 1024)
        } catch (_: Exception) {}

        var totalStorageGb = 0.0
        var availStorageGb = 0.0
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availBlocks = stat.availableBlocksLong
            totalStorageGb = String.format(Locale.US, "%.1f", (totalBlocks * blockSize).toDouble() / (1024 * 1024 * 1024)).toDouble()
            availStorageGb = String.format(Locale.US, "%.1f", (availBlocks * blockSize).toDouble() / (1024 * 1024 * 1024)).toDouble()
        } catch (_: Exception) {}

        return HardwareDeviceInfo(
            deviceModel = Build.MODEL,
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            totalStorageGb = totalStorageGb,
            availableStorageGb = availStorageGb
        )
    }

    fun getAudioVolumeInfo(): SystemAudioVolumeInfo {
        val am = audioManager ?: return SystemAudioVolumeInfo(50, 15, 7, false)
        val stream = AudioManager.STREAM_MUSIC
        val current = am.getStreamVolume(stream)
        val max = am.getStreamMaxVolume(stream)
        val pct = if (max > 0) (current * 100 / max) else 50
        val isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.isStreamMute(stream)
        } else {
            current == 0
        }
        return SystemAudioVolumeInfo(
            currentVolumePercent = pct,
            maxVolume = max,
            currentVolume = current,
            isMuted = isMuted
        )
    }

    fun adjustVolume(direction: String): Pair<Boolean, String> {
        val am = audioManager ?: return false to "Audio service unavailable."
        val stream = AudioManager.STREAM_MUSIC
        return try {
            when (direction.trim().lowercase()) {
                "up", "raise", "increase", "badhao" -> {
                    am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    val newVol = getAudioVolumeInfo()
                    true to "Volume increased to ${newVol.currentVolumePercent}%."
                }
                "down", "lower", "decrease", "kam karo" -> {
                    am.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    val newVol = getAudioVolumeInfo()
                    true to "Volume decreased to ${newVol.currentVolumePercent}%."
                }
                "mute", "silent" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    } else {
                        am.setStreamVolume(stream, 0, AudioManager.FLAG_SHOW_UI)
                    }
                    true to "Media audio muted."
                }
                "unmute" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                    }
                    true to "Media audio unmuted."
                }
                else -> {
                    am.adjustStreamVolume(stream, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                    true to "Volume level is currently ${getAudioVolumeInfo().currentVolumePercent}%."
                }
            }
        } catch (e: Exception) {
            false to "Failed to adjust volume: ${e.localizedMessage}"
        }
    }

    fun getCurrentDateTimeSummary(): String {
        val sdf = SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.getDefault())
        return sdf.format(Date())
    }

    fun toTelemetryJson(): JSONObject = JSONObject().apply {
        val batt = getBatteryInfo()
        val net = getNetworkStatus()
        val hw = getHardwareInfo()
        val aud = getAudioVolumeInfo()

        put("date_time", getCurrentDateTimeSummary())
        put("battery", JSONObject().apply {
            put("level_percent", batt.levelPercent)
            put("is_charging", batt.isCharging)
            put("plug_type", batt.chargePlug)
            put("health", batt.health)
        })
        put("network", JSONObject().apply {
            put("connected", net.isConnected)
            put("type", net.connectionType)
            put("is_wifi", net.isWifi)
            put("is_cellular", net.isCellular)
        })
        put("hardware", JSONObject().apply {
            put("model", hw.deviceModel)
            put("manufacturer", hw.manufacturer)
            put("android_version", hw.androidVersion)
            put("ram_mb", "${hw.availableRamMb} MB free / ${hw.totalRamMb} MB total")
            put("storage_gb", "${hw.availableStorageGb} GB free / ${hw.totalStorageGb} GB total")
        })
        put("audio", JSONObject().apply {
            put("volume_percent", aud.currentVolumePercent)
            put("is_muted", aud.isMuted)
        })
    }
}
