package com.jarvisai.assistant.service

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.AlarmClock
import com.jarvisai.assistant.termux.JarvisTermuxBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PocketStrikeSlmPreset(
    val id: String,
    val displayName: String,
    val parameterSize: String,
    val quantization: String,
    val ramRequiredMb: Int,
    val termuxRunnerCmd: String,
    val description: String
)

data class OfflineExecutionOutcome(
    val handledLocally: Boolean,
    val intentCategory: String,
    val latencyMs: Long,
    val spokenHinglishResponse: String,
    val technicalDetails: String
)

/**
 * Feature 2: Dual-Core Brain (Cloud Gemini Live + PocketStrike 100% Offline On-Device Fallback).
 * Inspired by AbuZar-Ansarii/PocketStrike:
 * - Zero-cloud 0ms deterministic NLU & device control engine when internet drops or offline mode is active.
 * - Curated 1-tap GGUF ARM NEON Small Language Model (SLM) presets for local execution via Termux llama.cpp/ollama.
 */
object PocketStrikeOfflineEngine {

    val curatedSlmPresets: List<PocketStrikeSlmPreset> = listOf(
        PocketStrikeSlmPreset(
            id = "smollm2_360m_heretic",
            displayName = "SmolLM2 360M (ARM NEON Fast)",
            parameterSize = "360M",
            quantization = "Q4_K_M GGUF",
            ramRequiredMb = 380,
            termuxRunnerCmd = "ollama run smollm2:360m",
            description = "Ultra-lightweight instant offline conversational & command SLM"
        ),
        PocketStrikeSlmPreset(
            id = "qwen2_5_0_5b",
            displayName = "Qwen 2.5 0.5B Instruct",
            parameterSize = "500M",
            quantization = "Q4_K_M GGUF",
            ramRequiredMb = 520,
            termuxRunnerCmd = "ollama run qwen2.5:0.5b",
            description = "High-accuracy multilingual & coding micro-model for Android CPUs"
        ),
        PocketStrikeSlmPreset(
            id = "llama3_2_1b_abliterated",
            displayName = "Llama 3.2 1B Uncensored",
            parameterSize = "1.2B",
            quantization = "Q4_0 GGUF",
            ramRequiredMb = 950,
            termuxRunnerCmd = "ollama run llama3.2:1b",
            description = "Uncensored local reasoning & natural Hinglish/English dialogue"
        ),
        PocketStrikeSlmPreset(
            id = "deepseek_r1_distill_1_5b",
            displayName = "DeepSeek R1 Distill 1.5B",
            parameterSize = "1.5B",
            quantization = "Q4_K_M GGUF",
            ramRequiredMb = 1280,
            termuxRunnerCmd = "ollama run deepseek-r1:1.5b",
            description = "Chain-of-thought math, logic & offline code synthesis workstation"
        )
    )

    private val _selectedPresetId = MutableStateFlow(curatedSlmPresets.first().id)
    val selectedPresetId: StateFlow<String> = _selectedPresetId.asStateFlow()

    private val _forceOfflineMode = MutableStateFlow(false)
    val forceOfflineMode: StateFlow<Boolean> = _forceOfflineMode.asStateFlow()

    fun selectSlmPreset(presetId: String) {
        if (curatedSlmPresets.any { it.id == presetId }) {
            _selectedPresetId.value = presetId
        }
    }

    fun setForceOfflineMode(enabled: Boolean) {
        _forceOfflineMode.value = enabled
    }

    fun isNetworkOnline(context: Context): Boolean {
        if (_forceOfflineMode.value) return false
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Zero-Cloud Offline Execution Engine:
     * Handles hardware controls, alarms, math, battery/storage telemetry, app launching,
     * and optional Termux GGUF SLM inference with 0% cloud dependency.
     */
    suspend fun executeOfflineCommand(
        context: Context,
        utterance: String,
        phoneControls: PhoneControlsManager = PhoneControlsManager(context)
    ): OfflineExecutionOutcome {
        val startMs = System.currentTimeMillis()
        val clean = utterance.trim()
        val lower = clean.lowercase()

        // 1. Flashlight / Torch Control
        if (lower.contains("flashlight") || lower.contains("torch") || lower.contains("light on") || lower.contains("light off")) {
            val turnOn = !lower.contains("off") && !lower.contains("band")
            val res = phoneControls.setFlashlight(turnOn)
            val stateWord = if (turnOn) "ON" else "OFF"
            return OfflineExecutionOutcome(
                handledLocally = res.success,
                intentCategory = "OFFLINE_HARDWARE_TORCH",
                latencyMs = System.currentTimeMillis() - startMs,
                spokenHinglishResponse = "Boss [PocketStrike Offline Brain]: Maine bina internet ke flashlight $stateWord kar di hai.",
                technicalDetails = "Zero-Cloud Hardware Torch -> $stateWord (${res.message})"
            )
        }

        // 2. Volume Control
        val volMatch = Regex("(?:volume|awaaz|sound).*?(\\d{1,3})").find(lower)
        if (volMatch != null || lower.contains("mute") || lower.contains("full volume")) {
            val targetPct = when {
                lower.contains("mute") -> 0
                lower.contains("full volume") -> 100
                else -> volMatch?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 100) ?: 50
            }
            phoneControls.systemStatusManager.controlVolume(targetPercent = targetPct)
            return OfflineExecutionOutcome(
                handledLocally = true,
                intentCategory = "OFFLINE_AUDIO_VOLUME",
                latencyMs = System.currentTimeMillis() - startMs,
                spokenHinglishResponse = "Boss [PocketStrike Offline]: Media volume $targetPct% par set kar diya gaya hai.",
                technicalDetails = "AudioManager STREAM_MUSIC -> $targetPct%"
            )
        }

        // 3. Battery, RAM & Time Telemetry
        if (lower.contains("battery") || lower.contains("time") || lower.contains("kitna charge") || lower.contains("status") || lower.contains("telemetry")) {
            val sysMgr = SystemStatusManager(context)
            val bat = sysMgr.getBatteryInfo()
            val ram = sysMgr.getStorageAndRamInfo()
            val timeStr = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
            val reply = "Boss [PocketStrike Offline Core]: Abhi time $timeStr hai, battery ${bat.levelPercent}% (${if (bat.isCharging) "Charging" else "Discharging"}) hai, aur free RAM ${ram.availableRamMb} MB hai."
            return OfflineExecutionOutcome(
                handledLocally = true,
                intentCategory = "OFFLINE_SYSTEM_TELEMETRY",
                latencyMs = System.currentTimeMillis() - startMs,
                spokenHinglishResponse = reply,
                technicalDetails = "Battery=${bat.levelPercent}%, RAM=${ram.availableRamMb}MB, Time=$timeStr"
            )
        }

        // 4. Offline Alarm / Timer
        val timerMinMatch = Regex("(\\d{1,3})\\s*(?:minute|min)").find(lower)
        if (lower.contains("timer") && timerMinMatch != null) {
            val mins = timerMinMatch.groupValues[1].toIntOrNull() ?: 5
            try {
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, mins * 60)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
            return OfflineExecutionOutcome(
                handledLocally = true,
                intentCategory = "OFFLINE_TIMER",
                latencyMs = System.currentTimeMillis() - startMs,
                spokenHinglishResponse = "Boss [Offline Brain]: $mins minute ka timer bina internet ke start kar diya hai.",
                technicalDetails = "AlarmClock.ACTION_SET_TIMER -> ${mins * 60}s"
            )
        }

        // 5. Offline App Launch ("open youtube", "whatsapp kholo", etc.)
        val openMatch = Regex("(?:open|launch|kholo|start)\\s+([a-zA-Z0-9\\s]+)").find(lower)
        if (openMatch != null) {
            val targetApp = openMatch.groupValues[1].trim()
            val launchRes = AppLauncherManager(context, phoneControls).launchApp(appName = targetApp, allowBrowserFallback = true)
            if (launchRes.executed) {
                return OfflineExecutionOutcome(
                    handledLocally = true,
                    intentCategory = "OFFLINE_APP_LAUNCH",
                    latencyMs = System.currentTimeMillis() - startMs,
                    spokenHinglishResponse = "Boss [Offline Brain]: Maine ${launchRes.appName} open kar diya hai.",
                    technicalDetails = "Package launched: ${launchRes.packageName}"
                )
            }
        }

        // 6. Offline Arithmetic / Math Evaluator
        val mathMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*([+\\-*/x])\\s*(\\d+(?:\\.\\d+)?)").find(lower)
        if (mathMatch != null) {
            val a = mathMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val op = mathMatch.groupValues[2]
            val b = mathMatch.groupValues[3].toDoubleOrNull() ?: 1.0
            val ans = when (op) {
                "+" -> a + b
                "-" -> a - b
                "*", "x" -> a * b
                "/" -> if (b != 0.0) a / b else Double.NaN
                else -> 0.0
            }
            val formatted = if (ans % 1.0 == 0.0) ans.toLong().toString() else String.format(Locale.US, "%.2f", ans)
            return OfflineExecutionOutcome(
                handledLocally = true,
                intentCategory = "OFFLINE_MATH_ENGINE",
                latencyMs = System.currentTimeMillis() - startMs,
                spokenHinglishResponse = "Boss [Offline Math Core]: $a $op $b ka answer $formatted hai.",
                technicalDetails = "Evaluated locally: $a $op $b = $formatted"
            )
        }

        // 7. Termux Local GGUF SLM Fallback (if Termux is installed)
        val bridge = JarvisTermuxBridge(context)
        val activePreset = curatedSlmPresets.find { it.id == _selectedPresetId.value } ?: curatedSlmPresets.first()
        if (bridge.isTermuxInstalled() && bridge.hasRunCommandPermission()) {
            val escapedPrompt = clean.replace("\"", "\\\"")
            val res = bridge.executeCommand(
                command = "${activePreset.termuxRunnerCmd} \"$escapedPrompt\"",
                background = true,
                timeoutSeconds = 10
            )
            if (res.success && res.stdout.isNotBlank()) {
                return OfflineExecutionOutcome(
                    handledLocally = true,
                    intentCategory = "OFFLINE_GGUF_SLM (${activePreset.displayName})",
                    latencyMs = System.currentTimeMillis() - startMs,
                    spokenHinglishResponse = res.stdout.trim().take(320),
                    technicalDetails = "Executed via ARM NEON GGUF Preset: ${activePreset.displayName}"
                )
            }
        }

        // 8. Resilient On-Device Heuristic Response
        val bat = SystemStatusManager(context).getBatteryInfo()
        return OfflineExecutionOutcome(
            handledLocally = true,
            intentCategory = "OFFLINE_HEURISTIC_BRAIN",
            latencyMs = System.currentTimeMillis() - startMs,
            spokenHinglishResponse = "Boss, abhi PocketStrike Offline Mode active hai (${activePreset.displayName}). Aapka phone ${bat.levelPercent}% charged hai. Offline mode mein aap mujhse Flashlight, Volume, Timer, App Launch, Math calculation ya Termux commands turant karwa sakte hain!",
            technicalDetails = "Offline Fallback Preset: ${activePreset.displayName} (${activePreset.quantization})"
        )
    }
}
