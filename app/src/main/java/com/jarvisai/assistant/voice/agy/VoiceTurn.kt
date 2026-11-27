package com.jarvisai.assistant.voice.agy

/**
 * Parses Antigravity voice-call handoff messages stored inside the cascade chat:
 * `<voice_instructions>...</voice_instructions>` + `<talker_agent_context>` transcript lines.
 * Lets Agentra chat bubbles display cleanly what you said instead of dumping raw XML context.
 */
object VoiceTurn {
    const val TAG = "voice_turn"
    private const val TALKER_TAG_PREFIX = "voice_talker:"

    data class Parsed(
        val spoken: String,
        val voiceLine: String?
    )

    private val lineRe = Regex("""^\s*(?:\[[^\]]*]\s*)?(user|talker):\s*(?:"(.*)"|(.*))\s*$""")

    fun isVoiceTurn(text: String, tags: List<String> = emptyList()): Boolean {
        if (tags.contains(TAG)) return true
        val t = text.trimStart()
        return t.startsWith("<voice_instructions>") || t.startsWith("<talker_agent_context>")
    }

    fun parse(text: String, tags: List<String> = emptyList()): Parsed? {
        if (!isVoiceTurn(text, tags)) return null
        val taggedVoiceLine = tags.firstOrNull { it.startsWith(TALKER_TAG_PREFIX) }
            ?.removePrefix(TALKER_TAG_PREFIX)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val context = text.substringAfter("<talker_agent_context>", "")
            .substringBefore("</talker_agent_context>")
        data class L(val user: Boolean, val timed: Boolean, val text: String)
        val lines = context.lines().mapNotNull { raw ->
            val m = lineRe.find(raw) ?: return@mapNotNull null
            val body = (m.groupValues[2].ifEmpty { m.groupValues[3] }).trim()
            L(
                user = m.groupValues[1] == "user",
                timed = raw.trimStart().startsWith("["),
                text = unescape(body).trim().removeSuffix("-").trim()
            )
        }
        var end = lines.size
        while (end > 0 && !lines[end - 1].user) end--
        var start = end
        while (start > 0 && lines[start - 1].user) start--
        val run = lines.subList(start, end).filter { it.text.isNotEmpty() }
        val picked = if (run.any { it.timed }) run.filter { it.timed } else run
        val spoken = picked.joinToString(" ") { it.text }.trim()

        val inferredTalkerLine = if (taggedVoiceLine != null) {
            taggedVoiceLine
        } else {
            val trailingTalker = lines.subList(end, lines.size)
                .filter { !it.user && it.text.isNotEmpty() }
            val talkerRun = if (trailingTalker.isNotEmpty()) {
                trailingTalker
            } else {
                lines.lastOrNull { !it.user && it.text.isNotEmpty() }?.let { listOf(it) }.orEmpty()
            }
            talkerRun.joinToString(" ") { it.text }.trim().takeIf { it.isNotEmpty() }
        }

        val fallbackSpoken = if (spoken.isBlank() && context.isBlank()) {
            text.substringAfter("</voice_instructions>", "")
                .trim()
        } else spoken

        return Parsed(fallbackSpoken, inferredTalkerLine)
    }

    fun displayText(text: String, tags: List<String> = emptyList()): String {
        val p = parse(text, tags) ?: return text
        return p.spoken.ifBlank { p.voiceLine ?: "Voice request" }
    }

    private fun unescape(s: String): String =
        s.replace("\\\"", "\"").replace("\\n", " ").replace("\\\\", "\\")
}
