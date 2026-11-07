package com.jarvisai.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChargingTelemetrySnapshot(
    val isPowerConnected: Boolean = false,
    val voltageMillivolts: Int = 0,
    val currentMicroAmps: Long = 0L,
    val powerWatts: Double = 0.0,
    val temperatureCelsius: Double = 0.0,
    val batteryLevelPercent: Int = 0,
    val spokenFeedback: String = "Arc reactor standby.",
    val timestampMs: Long = 0L
)

/**
 * Stark Hardware Sensor Fusion Module #3:
 * `ChargingTelemetryManager` — Cinematic Power Coupling & Wattage Calculation.
 *
 * Architecture & Behavior:
 * 1. BroadcastReceiver Registration:
 *    - Listens for `Intent.ACTION_POWER_CONNECTED` (and `Intent.ACTION_POWER_DISCONNECTED`).
 * 2. Real-Time Hardware Telemetry Calculation:
 *    - Reads `BatteryManager.EXTRA_VOLTAGE` (mV) and `BatteryManager.EXTRA_TEMPERATURE` (0.1°C)
 *      from the sticky `Intent.ACTION_BATTERY_CHANGED` broadcast.
 *    - Queries `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW` (µA) via Android `BatteryManager`.
 *    - Computes exact charging power using:
 *      `Watts = (Voltage_mV * abs(Current_uA)) / 10^9`
 * 3. Stark Spoken Feedback:
 *    - Speaks via `EdgeTtsManager` / `JarvisTtsManager`:
 *      `"Arc reactor coupled, sir. Energy intake active at [X] Watts. Core temperature steady at [Y] degrees Celsius."`
 * 4. Lifecycle Safety:
 *    - Provides `register(context)`, `unregister(context)`, and `onDestroy()` to cleanly
 *      unregister the `BroadcastReceiver` and cancel coroutine scopes without leaks.
 */
object ChargingTelemetryManager {

    private const val TAG = "ChargingTelemetryMgr"
    private const val ONE_BILLION_WATTS_DIVISOR = 1_000_000_000.0
    private const val CURRENT_STABILIZATION_DELAY_MS = 450L

    private var supervisorJob = SupervisorJob()
    private var scope = CoroutineScope(supervisorJob + Dispatchers.Main)
    private var telemetryJob: Job? = null

    private var registeredContext: Context? = null

    @Volatile
    private var isReceiverRegistered: Boolean = false

    private val _state = MutableStateFlow(ChargingTelemetrySnapshot())
    val state: StateFlow<ChargingTelemetrySnapshot> = _state.asStateFlow()

    private val powerConnectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ctx = context?.applicationContext ?: return
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    Log.i(TAG, "ACTION_POWER_CONNECTED received — computing Stark Arc Reactor telemetry.")
                    ensureScopeActive()
                    telemetryJob?.cancel()
                    telemetryJob = scope.launch {
                        // Brief stabilization window so the PMIC updates BATTERY_PROPERTY_CURRENT_NOW
                        delay(CURRENT_STABILIZATION_DELAY_MS)
                        handlePowerConnected(ctx, speakAloud = true)
                    }
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    Log.i(TAG, "ACTION_POWER_DISCONNECTED received — announcing battery percentage in Hinglish.")
                    ensureScopeActive()
                    telemetryJob?.cancel()
                    handlePowerDisconnected(ctx, speakAloud = true)
                }
            }
        }
    }

    /**
     * Registers the dynamic `BroadcastReceiver` for `Intent.ACTION_POWER_CONNECTED`
     * and `Intent.ACTION_POWER_DISCONNECTED`.
     */
    @Synchronized
    fun register(context: Context) {
        val appCtx = context.applicationContext
        ensureScopeActive()
        if (isReceiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appCtx.registerReceiver(powerConnectionReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                appCtx.registerReceiver(powerConnectionReceiver, filter)
            }
            registeredContext = appCtx
            isReceiverRegistered = true
            _state.value = measureChargingTelemetry(appCtx)
            Log.d(TAG, "ChargingTelemetryManager receiver registered.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register ChargingTelemetryManager receiver: ${e.localizedMessage}")
        }
    }

    /**
     * Unregisters the `BroadcastReceiver` to prevent leaks on service/activity destroy.
     */
    @Synchronized
    fun unregister(context: Context? = null) {
        val targetCtx = context?.applicationContext ?: registeredContext
        if (isReceiverRegistered && targetCtx != null) {
            try {
                targetCtx.unregisterReceiver(powerConnectionReceiver)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver already unregistered: ${e.localizedMessage}")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering power receiver: ${e.localizedMessage}")
            }
        }
        isReceiverRegistered = false
        registeredContext = null
    }

    /**
     * Full lifecycle teardown on service or activity destroy.
     */
    fun onDestroy(context: Context? = null) {
        telemetryJob?.cancel()
        telemetryJob = null
        unregister(context)
        try {
            supervisorJob.cancel()
        } catch (_: Exception) {
        }
    }

    /**
     * Evaluates charging telemetry immediately and speaks the Hinglish power-connected announcement:
     * `"Sir, charger connect ho gaya hai. [watts] Watts ki fast charging shuru ho gayi hai, aur battery temperature [temp] degrees hai."`
     */
    fun handlePowerConnected(
        context: Context,
        speakAloud: Boolean = true
    ): ChargingTelemetrySnapshot {
        val appCtx = context.applicationContext
        val snapshot = measureChargingTelemetry(appCtx, forceConnectedFlag = true)
        _state.value = snapshot

        if (speakAloud) {
            try {
                EdgeTtsManager.getInstance(appCtx).speak(
                    text = snapshot.spokenFeedback,
                    voiceName = EdgeTtsManager.DEFAULT_VOICE_FULL_NAME
                )
            } catch (e: Exception) {
                Log.w(TAG, "EdgeTtsManager fallback in ChargingTelemetryManager: ${e.localizedMessage}")
                JarvisTtsManager.speak(appCtx, snapshot.spokenFeedback)
            }
        }
        return snapshot
    }

    /**
     * Evaluates battery percentage on charger unplug and speaks in natural Hinglish via `EdgeTtsManager`:
     * `"Sir, charger disconnect ho gaya hai. Battery abhi [percent]% par hai."`
     */
    fun handlePowerDisconnected(
        context: Context,
        speakAloud: Boolean = true
    ): ChargingTelemetrySnapshot {
        val appCtx = context.applicationContext
        val measured = measureChargingTelemetry(appCtx, forceConnectedFlag = false)
        val unplugSpeech = formatUnpluggedSpokenFeedback(measured.batteryLevelPercent)
        val snapshot = measured.copy(
            isPowerConnected = false,
            spokenFeedback = unplugSpeech
        )
        _state.value = snapshot

        if (speakAloud) {
            try {
                EdgeTtsManager.getInstance(appCtx).speak(
                    text = unplugSpeech,
                    voiceName = EdgeTtsManager.DEFAULT_VOICE_FULL_NAME
                )
            } catch (e: Exception) {
                Log.w(TAG, "EdgeTtsManager fallback on unplug in ChargingTelemetryManager: ${e.localizedMessage}")
                JarvisTtsManager.speak(appCtx, unplugSpeech)
            }
        }
        return snapshot
    }

    /**
     * Pure formula calculator:
     * `Watts = (Voltage_mV * abs(Current_uA)) / 10^9`
     */
    fun calculateWatts(voltageMillivolts: Int, currentMicroAmps: Long): Double {
        if (voltageMillivolts <= 0 || currentMicroAmps == 0L) return 0.0
        val absCurrentUa = abs(currentMicroAmps.toDouble())
        return (voltageMillivolts.toDouble() * absCurrentUa) / ONE_BILLION_WATTS_DIVISOR
    }

    /**
     * Formats the exact Hinglish spoken feedback on `ACTION_POWER_CONNECTED`:
     * `"Sir, charger connect ho gaya hai. [watts] Watts ki fast charging shuru ho gayi hai, aur battery temperature [temp] degrees hai."`
     */
    fun formatStarkSpokenFeedback(watts: Double, temperatureCelsius: Double): String {
        val wattsStr = String.format(Locale.US, "%.1f", watts)
        val tempStr = String.format(Locale.US, "%.1f", temperatureCelsius)
        return "Sir, charger connect ho gaya hai. $wattsStr Watts ki fast charging shuru ho gayi hai, aur battery temperature $tempStr degrees hai."
    }

    /**
     * Formats the exact Hinglish spoken feedback on `ACTION_POWER_DISCONNECTED`:
     * `"Sir, charger disconnect ho gaya hai. Battery abhi [percent]% par hai."`
     */
    fun formatUnpluggedSpokenFeedback(batteryPercent: Int): String {
        val pct = batteryPercent.coerceIn(0, 100)
        return "Sir, charger disconnect ho gaya hai. Battery abhi $pct% par hai."
    }

    /**
     * Reads `BatteryManager.EXTRA_VOLTAGE` (mV), `BatteryManager.EXTRA_TEMPERATURE` (0.1°C),
     * and `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW` (µA) and calculates real-time Wattage.
     */
    fun measureChargingTelemetry(
        context: Context,
        forceConnectedFlag: Boolean = false
    ): ChargingTelemetrySnapshot {
        val appCtx = context.applicationContext
        val batteryIntent = try {
            appCtx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        }

        val batteryManager = appCtx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

        // 1. Read Voltage (mV). Normalize if a device reports in Volts (< 20).
        val rawVoltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000) ?: 4000
        val voltageMv = when {
            rawVoltage in 1..20 -> rawVoltage * 1000
            rawVoltage > 0 -> rawVoltage
            else -> 4000
        }

        // 2. Read Temperature (tenths of a degree Celsius -> Celsius)
        val rawTempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 310) ?: 310
        val temperatureCelsius = if (rawTempTenths > 0) rawTempTenths / 10.0 else 31.0

        // 3. Query Current Now (µA) from BatteryManager
        val rawCurrentProp = try {
            batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0L
        } catch (_: Exception) {
            0L
        }

        // Some OEM kernels report BATTERY_PROPERTY_CURRENT_NOW in mA instead of µA when abs(value) < 10_000
        val currentUa: Long = when {
            rawCurrentProp == 0L || rawCurrentProp == Long.MIN_VALUE -> 2_000_000L // 2.0A fallback when PMIC gauge reports 0
            abs(rawCurrentProp) in 1L..9_999L -> rawCurrentProp * 1_000L
            else -> rawCurrentProp
        }

        // 4. Calculate Watts = (Voltage_mV * abs(Current_uA)) / 10^9
        val watts = calculateWatts(voltageMv, currentUa)

        // 5. Read Battery Level % and Charging State
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val levelPct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else 100

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val isConnected = forceConnectedFlag ||
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0

        val spoken = formatStarkSpokenFeedback(watts, temperatureCelsius)

        return ChargingTelemetrySnapshot(
            isPowerConnected = isConnected,
            voltageMillivolts = voltageMv,
            currentMicroAmps = currentUa,
            powerWatts = watts,
            temperatureCelsius = temperatureCelsius,
            batteryLevelPercent = levelPct,
            spokenFeedback = spoken,
            timestampMs = System.currentTimeMillis()
        )
    }

    private fun ensureScopeActive() {
        if (!supervisorJob.isActive) {
            supervisorJob = SupervisorJob()
            scope = CoroutineScope(supervisorJob + Dispatchers.Main)
        }
    }
}
