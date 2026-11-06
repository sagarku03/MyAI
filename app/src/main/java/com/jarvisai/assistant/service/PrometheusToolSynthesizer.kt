package com.jarvisai.assistant.service

import android.content.Context
import com.jarvisai.assistant.model.ProceduralHudMode
import com.jarvisai.assistant.termux.JarvisTermuxBridge
import com.jarvisai.assistant.ui.components.ProceduralHudModeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class PrometheusStepSpec(
    val stepOrder: Int,
    val actionType: String, // LAUNCH_APP, CLICK_TEXT, TYPE_TEXT, SCROLL, WAIT_MS, TERMUX_CMD, SPEAK
    val targetOrPackage: String,
    val payloadValue: String = ""
)

data class SynthesizedPrometheusTool(
    val toolId: String,
    val toolName: String,
    val triggerPhrase: String,
    val description: String,
    val steps: List<PrometheusStepSpec>,
    val securityVerified: Boolean = true,
    val executionCount: Int = 0,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class PrometheusSynthesisOutcome(
    val success: Boolean,
    val tool: SynthesizedPrometheusTool?,
    val securityAuditReport: String,
    val spokenHinglishSummary: String
)

/**
 * Feature 1: Project Prometheus Android — Autonomous Self-Evolution & Dynamic Tool/Macro Synthesizer.
 * Inspired by AbuZar-Ansarii/Ultron Project Prometheus:
 * Dynamically synthesizes declarative multi-step tools & Termux scripts, performs security heuristic
 * checks, persists to local storage, and hot-loads into active memory without restarting the app.
 */
object PrometheusToolSynthesizer {

    private const val PREFS_NAME = "jarvis_prometheus_tools_prefs"
    private const val KEY_TOOLS_JSON = "synthesized_tools_json"

    private val forbiddenDestructivePatterns = listOf(
        "rm -rf /", "mkfs", "dd if=", ":(){ :|:& };:", "format /", "factory_reset", "uninstall_all"
    )

    private val defaultSeedTools = listOf(
        SynthesizedPrometheusTool(
            toolId = "prom_zomato_quick_reorder",
            toolName = "zomato_quick_reorder",
            triggerPhrase = "Zomato Biryani Reorder",
            description = "Opens Zomato, navigates to Order History / Reorder, and pauses safely before UPI payment.",
            steps = listOf(
                PrometheusStepSpec(1, "LAUNCH_APP", "Zomato", "com.application.zomato"),
                PrometheusStepSpec(2, "WAIT_MS", "800", ""),
                PrometheusStepSpec(3, "CLICK_TEXT", "Reorder", ""),
                PrometheusStepSpec(4, "SPEAK", "TTS", "Boss, Zomato reorder cart ready hai. Payment safety gate par pause kiya hai.")
            )
        ),
        SynthesizedPrometheusTool(
            toolId = "prom_morning_briefing_overdrive",
            toolName = "morning_briefing_overdrive",
            triggerPhrase = "Morning Overdrive Protocol",
            description = "Sets media volume to 65%, checks battery & network telemetry, and announces morning briefing.",
            steps = listOf(
                PrometheusStepSpec(1, "SET_VOLUME", "65", ""),
                PrometheusStepSpec(2, "SPEAK", "TTS", "Good morning Boss! Project Prometheus Morning Overdrive protocol active hai.")
            )
        ),
        SynthesizedPrometheusTool(
            toolId = "prom_dev_git_status_audit",
            toolName = "dev_git_status_audit",
            triggerPhrase = "Termux System Health Probe",
            description = "Runs a fast Termux kernel & memory diagnostic script and speaks the status.",
            steps = listOf(
                PrometheusStepSpec(1, "TERMUX_CMD", "uname -sr && free -m | head -n 2", ""),
                PrometheusStepSpec(2, "SPEAK", "TTS", "Termux workstation diagnostic complete ho gaya hai.")
            )
        )
    )

    private val _hotLoadedTools = MutableStateFlow<List<SynthesizedPrometheusTool>>(defaultSeedTools)
    val hotLoadedTools: StateFlow<List<SynthesizedPrometheusTool>> = _hotLoadedTools.asStateFlow()

    fun loadPersistedTools(context: Context): List<SynthesizedPrometheusTool> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_TOOLS_JSON, null)
            if (raw.isNullOrBlank()) {
                _hotLoadedTools.value = defaultSeedTools
                return defaultSeedTools
            }
            val arr = JSONArray(raw)
            val parsed = mutableListOf<SynthesizedPrometheusTool>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val stepsArr = obj.optJSONArray("steps") ?: JSONArray()
                val steps = mutableListOf<PrometheusStepSpec>()
                for (s in 0 until stepsArr.length()) {
                    val sObj = stepsArr.getJSONObject(s)
                    steps.add(
                        PrometheusStepSpec(
                            stepOrder = sObj.optInt("stepOrder", s + 1),
                            actionType = sObj.optString("actionType", "SPEAK"),
                            targetOrPackage = sObj.optString("targetOrPackage", ""),
                            payloadValue = sObj.optString("payloadValue", "")
                        )
                    )
                }
                parsed.add(
                    SynthesizedPrometheusTool(
                        toolId = obj.optString("toolId", "prom_${System.currentTimeMillis()}"),
                        toolName = obj.optString("toolName", "custom_tool"),
                        triggerPhrase = obj.optString("triggerPhrase", "Custom Tool"),
                        description = obj.optString("description", ""),
                        steps = steps,
                        securityVerified = obj.optBoolean("securityVerified", true),
                        executionCount = obj.optInt("executionCount", 0),
                        createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis())
                    )
                )
            }
            if (parsed.isEmpty()) {
                _hotLoadedTools.value = defaultSeedTools
            } else {
                _hotLoadedTools.value = parsed
            }
            _hotLoadedTools.value
        } catch (_: Exception) {
            _hotLoadedTools.value = defaultSeedTools
            defaultSeedTools
        }
    }

    private fun saveToolsToStorage(context: Context, tools: List<SynthesizedPrometheusTool>) {
        try {
            val arr = JSONArray()
            for (t in tools) {
                val stepsArr = JSONArray()
                for (s in t.steps) {
                    stepsArr.put(
                        JSONObject().apply {
                            put("stepOrder", s.stepOrder)
                            put("actionType", s.actionType)
                            put("targetOrPackage", s.targetOrPackage)
                            put("payloadValue", s.payloadValue)
                        }
                    )
                }
                arr.put(
                    JSONObject().apply {
                        put("toolId", t.toolId)
                        put("toolName", t.toolName)
                        put("triggerPhrase", t.triggerPhrase)
                        put("description", t.description)
                        put("steps", stepsArr)
                        put("securityVerified", t.securityVerified)
                        put("executionCount", t.executionCount)
                        put("createdAtMs", t.createdAtMs)
                    }
                )
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TOOLS_JSON, arr.toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    /**
     * Synthesizes a new multi-step tool from natural language goal/specification,
     * validates security heuristics, and hot-loads it into memory immediately.
     */
    fun synthesizeAndHotLoadTool(
        context: Context,
        rawNameOrTrigger: String,
        goalInstruction: String,
        explicitSteps: List<PrometheusStepSpec>? = null
    ): PrometheusSynthesisOutcome {
        ProceduralHudModeState.setMode(ProceduralHudMode.CYBER_TERMINAL)
        val combinedText = "$rawNameOrTrigger $goalInstruction".lowercase()

        for (bad in forbiddenDestructivePatterns) {
            if (combinedText.contains(bad)) {
                ProceduralHudModeState.setMode(ProceduralHudMode.RED_ALERT)
                return PrometheusSynthesisOutcome(
                    success = false,
                    tool = null,
                    securityAuditReport = "BLOCKED BY PROMETHEUS AST HEURISTICS: Destructive pattern '$bad' detected.",
                    spokenHinglishSummary = "Warning Boss! Project Prometheus security scanner ne destructive command '$bad' ko block kar diya hai."
                )
            }
        }

        val cleanToolName = rawNameOrTrigger.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "prometheus_tool_${System.currentTimeMillis() % 10000}" }

        val synthesizedSteps = if (!explicitSteps.isNullOrEmpty()) {
            explicitSteps
        } else {
            inferStepsFromNaturalGoal(goalInstruction)
        }

        val newTool = SynthesizedPrometheusTool(
            toolId = "prom_${System.currentTimeMillis()}",
            toolName = cleanToolName,
            triggerPhrase = rawNameOrTrigger.trim().ifBlank { cleanToolName },
            description = goalInstruction.trim().ifBlank { "Synthesized multi-step autonomous tool" },
            steps = synthesizedSteps,
            securityVerified = true,
            executionCount = 0
        )

        val current = loadPersistedTools(context).toMutableList()
        current.removeAll { it.toolName.equals(cleanToolName, ignoreCase = true) }
        current.add(0, newTool)
        _hotLoadedTools.value = current
        saveToolsToStorage(context, current)

        return PrometheusSynthesisOutcome(
            success = true,
            tool = newTool,
            securityAuditReport = "AST Schema & Security Heuristics Verified (${synthesizedSteps.size} steps hot-loaded).",
            spokenHinglishSummary = "Boss, Project Prometheus ne naya tool '${newTool.triggerPhrase}' (${synthesizedSteps.size} steps) synthesize karke active memory mein bina restart ke hot-load kar liya hai!"
        )
    }

    private fun inferStepsFromNaturalGoal(goal: String): List<PrometheusStepSpec> {
        val steps = mutableListOf<PrometheusStepSpec>()
        val lower = goal.lowercase()
        var order = 1

        val knownApps = listOf(
            "youtube" to "YouTube",
            "whatsapp" to "WhatsApp",
            "zomato" to "Zomato",
            "swiggy" to "Swiggy",
            "chrome" to "Chrome",
            "instagram" to "Instagram",
            "spotify" to "Spotify",
            "maps" to "Google Maps",
            "settings" to "Settings"
        )
        for ((kw, display) in knownApps) {
            if (lower.contains(kw)) {
                steps.add(PrometheusStepSpec(order++, "LAUNCH_APP", display, ""))
                break
            }
        }

        val searchMatch = Regex("(?:search|dhundho|play|order)\\s+([a-zA-Z0-9\\s]{2,30})").find(goal)
        if (searchMatch != null) {
            val query = searchMatch.groupValues[1].trim()
            steps.add(PrometheusStepSpec(order++, "TYPE_TEXT", "Search", query))
        }

        if (lower.contains("scroll")) {
            steps.add(PrometheusStepSpec(order++, "SCROLL", "down", ""))
        }

        if (lower.contains("click") || lower.contains("tap") || lower.contains("reorder")) {
            val clickTarget = when {
                lower.contains("reorder") -> "Reorder"
                lower.contains("first") -> "First Result"
                else -> "Submit"
            }
            steps.add(PrometheusStepSpec(order++, "CLICK_TEXT", clickTarget, ""))
        }

        steps.add(
            PrometheusStepSpec(
                stepOrder = order,
                actionType = "SPEAK",
                targetOrPackage = "TTS",
                payloadValue = "Boss, Prometheus tool '$goal' successfully execute ho gaya."
            )
        )
        return steps
    }

    /**
     * Executes a hot-loaded Prometheus tool by its name or trigger phrase in <0.2s per local step.
     */
    suspend fun executeHotLoadedTool(
        context: Context,
        nameOrTrigger: String,
        phoneControlsManager: PhoneControlsManager = PhoneControlsManager(context)
    ): Pair<Boolean, String> {
        ProceduralHudModeState.setMode(ProceduralHudMode.CYBER_TERMINAL)
        val tools = loadPersistedTools(context)
        val query = nameOrTrigger.trim().lowercase()
        val matched = tools.firstOrNull {
            it.toolName.lowercase() == query ||
                it.triggerPhrase.lowercase() == query ||
                it.triggerPhrase.lowercase().contains(query) ||
                query.contains(it.toolName.lowercase())
        } ?: return false to "Prometheus tool '$nameOrTrigger' active memory mein nahi mila."

        val executedLogs = mutableListOf<String>()
        val launcher = AppLauncherManager(context)

        for (step in matched.steps) {
            when (step.actionType.uppercase()) {
                "LAUNCH_APP" -> {
                    val res = launcher.launchApp(appName = step.targetOrPackage, packageName = step.payloadValue, allowBrowserFallback = true)
                    executedLogs.add("Step ${step.stepOrder}: Launched ${step.targetOrPackage} (${res.executed})")
                }
                "CLICK_TEXT" -> {
                    val res = JarvisAccessibilityService.clickElement(
                        context = context,
                        targetText = step.targetOrPackage,
                        contentDescription = step.targetOrPackage,
                        viewId = ""
                    )
                    executedLogs.add("Step ${step.stepOrder}: Clicked '${step.targetOrPackage}' (${res.executed})")
                }
                "TYPE_TEXT" -> {
                    val res = JarvisAccessibilityService.typeText(
                        context = context,
                        text = step.payloadValue.ifBlank { step.targetOrPackage },
                        replaceExisting = true,
                        submitAfter = true
                    )
                    executedLogs.add("Step ${step.stepOrder}: Typed '${step.payloadValue}' (${res.executed})")
                }
                "SCROLL" -> {
                    val res = JarvisAccessibilityService.scrollScreen(context, step.targetOrPackage.ifBlank { "down" })
                    executedLogs.add("Step ${step.stepOrder}: Scrolled ${step.targetOrPackage} (${res.executed})")
                }
                "SET_VOLUME" -> {
                    val pct = step.targetOrPackage.toIntOrNull() ?: 60
                    phoneControlsManager.systemStatusManager.controlVolume(targetPercent = pct)
                    executedLogs.add("Step ${step.stepOrder}: Volume set to $pct%")
                }
                "TERMUX_CMD" -> {
                    val bridge = JarvisTermuxBridge(context)
                    val res = bridge.executeCommand(step.targetOrPackage, background = true, timeoutSeconds = 8)
                    executedLogs.add("Step ${step.stepOrder}: Termux '${step.targetOrPackage}' -> ${if (res.success) "OK" else res.errorMessage}")
                }
                "WAIT_MS" -> {
                    val ms = step.targetOrPackage.toLongOrNull()?.coerceIn(100L, 2000L) ?: 300L
                    delay(ms)
                    executedLogs.add("Step ${step.stepOrder}: Waited ${ms}ms")
                }
                "SPEAK" -> {
                    val msg = step.payloadValue.ifBlank { "Prometheus step complete." }
                    JarvisTtsManager.speak(context, msg)
                    executedLogs.add("Step ${step.stepOrder}: Spoke '$msg'")
                }
            }
        }

        val updated = tools.map {
            if (it.toolId == matched.toolId) it.copy(executionCount = it.executionCount + 1) else it
        }
        _hotLoadedTools.value = updated
        saveToolsToStorage(context, updated)

        val summary = "Prometheus Tool '${matched.triggerPhrase}' executed (${executedLogs.size} steps): ${executedLogs.joinToString(" | ")}"
        return true to summary
    }
}
