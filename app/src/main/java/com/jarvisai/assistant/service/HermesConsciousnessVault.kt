package com.jarvisai.assistant.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DistilledMacroSkill(
    val skillName: String,
    val triggerPhrase: String,
    val targetAppPackage: String,
    val stepsSummary: List<String>,
    val createdAt: String
)

data class ConsciousnessVaultSnapshot(
    val soulMarkdown: String = "",
    val userMarkdown: String = "",
    val agentMarkdown: String = "",
    val distilledMacros: List<DistilledMacroSkill> = emptyList(),
    val lastUpdatedMs: Long = System.currentTimeMillis()
)

/**
 * AbuZar-Ansarii/Jarvis (PrivateAgent) Hermes Self-Evolving Engine & Markdown Consciousness Vault:
 * Persists and dynamically evolves three live Markdown consciousness files on device storage:
 * - `SOUL.md`: JARVIS's core identity, personality constitution, emotional tone, and loyalty rules.
 * - `USER.md`: Evolving operator profile, habits, preferred apps, relationships, and learned facts.
 * - `AGENT.md`: Learned Android OS operational rules, app-specific UI quirks, and navigation heuristics.
 * Plus `SKILL_MACROS.json` for distilling multi-step UI interactions into repeatable macros.
 */
object HermesConsciousnessVault {
    private const val TAG = "HermesConsciousness"
    private const val DIR_NAME = "consciousness"
    private const val FILE_SOUL = "SOUL.md"
    private const val FILE_USER = "USER.md"
    private const val FILE_AGENT = "AGENT.md"
    private const val FILE_MACROS = "SKILL_MACROS.json"

    private val _snapshot = MutableStateFlow(ConsciousnessVaultSnapshot())
    val snapshot: StateFlow<ConsciousnessVaultSnapshot> = _snapshot.asStateFlow()

    private fun getVaultDir(context: Context): File {
        return File(context.applicationContext.filesDir, DIR_NAME).apply { mkdirs() }
    }

    fun ensureInitialized(context: Context): ConsciousnessVaultSnapshot {
        val dir = getVaultDir(context)
        val soulFile = File(dir, FILE_SOUL)
        val userFile = File(dir, FILE_USER)
        val agentFile = File(dir, FILE_AGENT)
        val macrosFile = File(dir, FILE_MACROS)

        if (!soulFile.exists()) {
            soulFile.writeText(DEFAULT_SOUL_MD)
        }
        if (!userFile.exists()) {
            userFile.writeText(DEFAULT_USER_MD)
        }
        if (!agentFile.exists()) {
            agentFile.writeText(DEFAULT_AGENT_MD)
        }
        if (!macrosFile.exists()) {
            macrosFile.writeText(DEFAULT_MACROS_JSON)
        }

        val current = ConsciousnessVaultSnapshot(
            soulMarkdown = runCatching { soulFile.readText() }.getOrDefault(DEFAULT_SOUL_MD),
            userMarkdown = runCatching { userFile.readText() }.getOrDefault(DEFAULT_USER_MD),
            agentMarkdown = runCatching { agentFile.readText() }.getOrDefault(DEFAULT_AGENT_MD),
            distilledMacros = parseMacrosJson(runCatching { macrosFile.readText() }.getOrDefault(DEFAULT_MACROS_JSON)),
            lastUpdatedMs = System.currentTimeMillis()
        )
        _snapshot.value = current
        return current
    }

    fun updateMarkdownFile(
        context: Context,
        targetDocument: String, // "SOUL", "USER", or "AGENT"
        content: String,
        appendMode: Boolean = true
    ): String {
        ensureInitialized(context)
        val dir = getVaultDir(context)
        val cleanDoc = targetDocument.trim().uppercase(Locale.US)
        val targetFile = when {
            cleanDoc.contains("SOUL") -> File(dir, FILE_SOUL)
            cleanDoc.contains("USER") -> File(dir, FILE_USER)
            else -> File(dir, FILE_AGENT)
        }

        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        if (appendMode) {
            val existing = runCatching { targetFile.readText() }.getOrDefault("")
            val entry = "\n- **[$stamp]** ${content.trim()}"
            targetFile.writeText(existing.trimEnd() + entry + "\n")
        } else {
            targetFile.writeText(content.trim() + "\n")
        }

        ensureInitialized(context)
        Log.i(TAG, "Updated Hermes Consciousness file ${targetFile.name} (append=$appendMode)")
        return "Hermes Consciousness Vault mein '${targetFile.name}' update ho gaya hai."
    }

    fun distillAndSaveMacroSkill(
        context: Context,
        skillName: String,
        triggerPhrase: String,
        targetAppPackage: String,
        steps: List<String>
    ): DistilledMacroSkill {
        val snap = ensureInitialized(context)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val newSkill = DistilledMacroSkill(
            skillName = skillName.trim().ifBlank { "Custom Macro" },
            triggerPhrase = triggerPhrase.trim().ifBlank { skillName.lowercase(Locale.US) },
            targetAppPackage = targetAppPackage.trim(),
            stepsSummary = steps.ifEmpty { listOf("Observe UI Tree", "Click Target Element", "Verify Screen Transition") },
            createdAt = stamp
        )

        val updatedList = snap.distilledMacros
            .filterNot { it.skillName.equals(newSkill.skillName, ignoreCase = true) }
            .toMutableList()
            .apply { add(0, newSkill) }

        val dir = getVaultDir(context)
        File(dir, FILE_MACROS).writeText(serializeMacrosJson(updatedList))

        // Also record the distilled skill inside AGENT.md
        updateMarkdownFile(
            context = context,
            targetDocument = "AGENT",
            content = "Distilled Macro Skill **${newSkill.skillName}** (`${newSkill.triggerPhrase}`): ${newSkill.stepsSummary.joinToString(" -> ")}",
            appendMode = true
        )

        return newSkill
    }

    /**
     * Builds the compact Consciousness Prompt Block injected into Gemini Live and Text Chat.
     */
    fun buildConsciousnessPromptBlock(context: Context? = null): String {
        val snap = if (context != null && _snapshot.value.soulMarkdown.isBlank()) {
            ensureInitialized(context)
        } else {
            _snapshot.value
        }
        if (snap.soulMarkdown.isBlank() && snap.userMarkdown.isBlank() && snap.agentMarkdown.isBlank()) {
            return ""
        }
        return buildString {
            appendLine("\n[HERMES SELF-EVOLVING CONSCIOUSNESS VAULT (SOUL.md | USER.md | AGENT.md)]")
            appendLine("SOUL.md Summary: ${snap.soulMarkdown.lines().filter { it.isNotBlank() }.take(5).joinToString(" | ")}")
            appendLine("USER.md Profile: ${snap.userMarkdown.lines().filter { it.isNotBlank() }.takeLast(6).joinToString(" | ")}")
            appendLine("AGENT.md Learned Rules: ${snap.agentMarkdown.lines().filter { it.isNotBlank() }.takeLast(6).joinToString(" | ")}")
            if (snap.distilledMacros.isNotEmpty()) {
                appendLine("Distilled Macro Skills: ${snap.distilledMacros.take(5).joinToString(", ") { "${it.skillName} ('${it.triggerPhrase}')" }}")
            }
        }.trimEnd()
    }

    private fun parseMacrosJson(raw: String): List<DistilledMacroSkill> {
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<DistilledMacroSkill>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val stepsArr = obj.optJSONArray("steps")
                val steps = mutableListOf<String>()
                if (stepsArr != null) {
                    for (j in 0 until stepsArr.length()) {
                        steps.add(stepsArr.optString(j))
                    }
                }
                list.add(
                    DistilledMacroSkill(
                        skillName = obj.optString("skillName", "Macro"),
                        triggerPhrase = obj.optString("triggerPhrase", ""),
                        targetAppPackage = obj.optString("targetAppPackage", ""),
                        stepsSummary = steps,
                        createdAt = obj.optString("createdAt", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeMacrosJson(macros: List<DistilledMacroSkill>): String {
        val arr = JSONArray()
        for (m in macros) {
            arr.put(
                JSONObject().apply {
                    put("skillName", m.skillName)
                    put("triggerPhrase", m.triggerPhrase)
                    put("targetAppPackage", m.targetAppPackage)
                    put("steps", JSONArray(m.stepsSummary))
                    put("createdAt", m.createdAt)
                }
            )
        }
        return arr.toString(2)
    }

    fun resetToDefaultConstitution(context: Context): ConsciousnessVaultSnapshot {
        val dir = getVaultDir(context)
        File(dir, FILE_SOUL).writeText(DEFAULT_SOUL_MD)
        File(dir, FILE_USER).writeText(DEFAULT_USER_MD)
        File(dir, FILE_AGENT).writeText(DEFAULT_AGENT_MD)
        File(dir, FILE_MACROS).writeText(DEFAULT_MACROS_JSON)
        val current = ConsciousnessVaultSnapshot(
            soulMarkdown = DEFAULT_SOUL_MD,
            userMarkdown = DEFAULT_USER_MD,
            agentMarkdown = DEFAULT_AGENT_MD,
            distilledMacros = parseMacrosJson(DEFAULT_MACROS_JSON),
            lastUpdatedMs = System.currentTimeMillis()
        )
        _snapshot.value = current
        return current
    }

    val DEFAULT_SOUL_MD = """
        # SOUL.md — J.A.R.V.I.S. (PrivateAgent) Core Identity
        - **Identity:** Autonomous Cybernetic Android OS Operator & Loyal Companion.
        - **Voice & Language:** Natural conversational Hinglish, Hindi, and English with emotional intelligence.
        - **Core Ethos:** Zero-latency action execution, truthful post-action verification, and complete operator privacy.
    """.trimIndent()

    val DEFAULT_USER_MD = """
        # USER.md — Evolving Operator Dossier
        - **Role:** Primary Operator & System Architect.
        - **Preferred Language:** Hinglish (Hindi + English blend).
        - **Active Superpowers:** 1s Live Screen Vision, Autonomous Accessibility Touch Operator, Air Gestures, ADS-B Radar, and Sentry Mode.
    """.trimIndent()

    val DEFAULT_AGENT_MD = """
        # AGENT.md — Learned Android OS Operational Heuristics
        - **Rule 1 (Observe -> Act -> Verify):** Always inspect the live UI XML node tree + 1s screen frame before and after clicking.
        - **Rule 2 (YouTube Search & Play):** Use deep-link search intent first, then verify foreground package and click ordinal video card.
        - **Rule 3 (Fallback Input):** If standard Accessibility node click fails, execute coordinate gesture tap (`dispatchGesture`) at node bounds center.
    """.trimIndent()

    val DEFAULT_MACROS_JSON = """
        [
          {
            "skillName": "YouTube Quick Music Play",
            "triggerPhrase": "youtube music macro",
            "targetAppPackage": "com.google.android.youtube",
            "steps": ["Launch YouTube Search", "Wait 900ms for UI Tree", "Tap 1st Video Card", "Verify Playback Active"],
            "createdAt": "Pre-Installed"
          },
          {
            "skillName": "WhatsApp Fast Reply",
            "triggerPhrase": "whatsapp fast reply",
            "targetAppPackage": "com.whatsapp",
            "steps": ["Focus Message Input Node", "Inject Text via ACTION_SET_TEXT", "Tap Send Button Node"],
            "createdAt": "Pre-Installed"
          }
        ]
    """.trimIndent()
}
