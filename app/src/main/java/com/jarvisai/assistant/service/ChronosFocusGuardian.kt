package com.jarvisai.assistant.service

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.jarvisai.assistant.model.ProceduralHudMode
import com.jarvisai.assistant.ui.components.ProceduralHudModeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

data class ChronosWellnessReport(
    val continuousSessionMinutes: Int,
    val isLateNightHour: Boolean,
    val currentHour24: Int,
    val activePackage: String,
    val isSocialOrReelsApp: Boolean,
    val fatigueLevel: String, // OPTIMAL, MODERATE_EYE_STRAIN, HIGH_FATIGUE_LATE_NIGHT
    val overdriveModeEnabled: Boolean,
    val spokenHinglishIntervention: String
)

/**
 * Feature 6: Project Chronos Mobile — Proactive Screen Fatigue, Late-Night Doom-Scroll & Focus Guardian.
 * Inspired by AbuZar-Ansarii/Ultron Project Chronos:
 * Monitors continuous screen time, late-night social/reels scrolling, developer/study fatigue,
 * and engages Focus Overdrive or 20-20-20 Eye-Break timers.
 */
object ChronosFocusGuardian {

    private var sessionStartTimestampMs: Long = System.currentTimeMillis()

    private val _overdriveModeActive = MutableStateFlow(false)
    val overdriveModeActive: StateFlow<Boolean> = _overdriveModeActive.asStateFlow()

    private val _lastReport = MutableStateFlow<ChronosWellnessReport?>(null)
    val lastReport: StateFlow<ChronosWellnessReport?> = _lastReport.asStateFlow()

    private val doomScrollPackages = listOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.zhiliaoapp.musically",
        "com.snapchat.android",
        "com.facebook.katana",
        "com.twitter.android"
    )

    fun toggleFocusOverdriveMode(context: Context, enabled: Boolean): String {
        _overdriveModeActive.value = enabled
        ProceduralHudModeState.setMode(
            if (enabled) ProceduralHudMode.CHRONOS_GUARDIAN else ProceduralHudMode.ARC_DEFAULT
        )
        val msg = if (enabled) {
            "Boss, Project Chronos Focus Overdrive Mode engage ho gaya hai. Distractions monitor kiye ja rahe hain aur 20-20-20 eye protection active hai."
        } else {
            "Boss, Project Chronos Focus Overdrive Mode standby par set kar diya gaya hai."
        }
        JarvisTtsManager.speak(context, msg)
        return msg
    }

    fun evaluateScreenFatigueAndWellness(context: Context): ChronosWellnessReport {
        ProceduralHudModeState.setMode(ProceduralHudMode.CHRONOS_GUARDIAN)
        val cal = Calendar.getInstance()
        val hour24 = cal.get(Calendar.HOUR_OF_DAY)
        val isLateNight = hour24 >= 23 || hour24 in 0..4

        val elapsedSessionMins = ((System.currentTimeMillis() - sessionStartTimestampMs) / 60_000L)
            .toInt()
            .coerceAtLeast(1)

        // Query UsageStatsManager if permission is granted, otherwise use live session + Accessibility package
        val activePkg = JarvisAccessibilityService.getScreenContext(context).activePackageName
            .ifBlank { queryTopUsagePackage(context) }
            .ifBlank { context.packageName }

        val isReelsOrSocial = doomScrollPackages.any { activePkg.contains(it, ignoreCase = true) }

        val (fatigueLevel, spokenHinglish) = when {
            isLateNight && isReelsOrSocial -> {
                "HIGH_FATIGUE_LATE_NIGHT" to
                    "Boss, raat ke $hour24 baj rahe hain aur aap social media/reels ($activePkg) dekh rahe hain. Aankhon aur neend ke liye abhi screen lock karna best rahega. Kya main relaxing sleep mode laga du?"
            }
            isLateNight -> {
                "LATE_NIGHT_WORK_ALERT" to
                    "Boss, late night ($hour24:00 hrs) ho chuka hai. Aap pichle $elapsedSessionMins minutes se active hain. Apni sehat ka dhyan rakhein aur jaldi rest lein!"
            }
            elapsedSessionMins >= 45 -> {
                "MODERATE_EYE_STRAIN" to
                    "Boss, Project Chronos ne notice kiya hai ki aap lagatar $elapsedSessionMins minutes se screen dekh rahe hain. 20-20-20 rule ke mutabik 5 minute ka eye break le lijiye!"
            }
            _overdriveModeActive.value && isReelsOrSocial -> {
                "DISTRACTION_DETECTED" to
                    "Alert Boss! Focus Overdrive Mode on hai aur aap $activePkg par aa gaye hain. Chaliye wapas apne main goal par focus karte hain!"
            }
            else -> {
                "OPTIMAL" to
                    "Boss, Project Chronos Bio-Rhythm check complete: Active session $elapsedSessionMins min, focus state OPTIMAL hai."
            }
        }

        val report = ChronosWellnessReport(
            continuousSessionMinutes = elapsedSessionMins,
            isLateNightHour = isLateNight,
            currentHour24 = hour24,
            activePackage = activePkg,
            isSocialOrReelsApp = isReelsOrSocial,
            fatigueLevel = fatigueLevel,
            overdriveModeEnabled = _overdriveModeActive.value,
            spokenHinglishIntervention = spokenHinglish
        )
        _lastReport.value = report
        return report
    }

    fun startEyeBreakTimer(context: Context, minutes: Int = 5): String {
        val safeMins = minutes.coerceIn(1, 60)
        try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, safeMins * 60)
                putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS Chronos Eye Recovery Break")
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
        val msg = "Boss, Project Chronos ne $safeMins minute ka Eye Recovery Break timer start kar diya hai. Aankhein band karke relax kijiye."
        JarvisTtsManager.speak(context, msg)
        return msg
    }

    private fun queryTopUsagePackage(context: Context): String {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return ""
            val end = System.currentTimeMillis()
            val start = end - 1000L * 60L * 30L
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            stats?.maxByOrNull { it.lastTimeUsed }?.packageName.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
}
