package com.jarvisai.assistant.service

/**
 * Intelligent Linguistic Turn-Taking Engine
 * Eliminates premature cut-offs when the user pauses mid-sentence ("aur...", "lekin...", "and...")
 * while committing instantly (~200ms) when a grammatically complete command is detected.
 */
object LinguisticTurnTakingEngine {

    private val TRAILING_CONJUNCTIONS = setOf(
        "aur", "par", "lekin", "magar", "ya", "kyunki", "matlab", "toh", "to", "bhi", "phir",
        "and", "or", "but", "because", "so", "like", "uh", "um", "ah", "err", "wait", "actually"
    )

    private val RAPID_GREETING_TOKENS = setOf(
        "hello", "hi", "hey", "jarvis", "namaste", "sun", "sunna", "haan", "ha", "theek", "ok", "okay"
    )

    private val SENTENCE_TERMINATORS = setOf(
        "karo", "kardo", "kijiye", "karna", "batao", "bataiye", "dikhana", "dikhaye",
        "hai", "hain", "tha", "the", "thi", "please", "now", "today", "tomorrow",
        "kholo", "bhejo", "chalu", "band", "sunao", "lagao",
        "hello", "hi", "hey", "jarvis", "namaste", "sun", "sunna", "haan", "ha", "theek", "ok", "okay"
    )

    data class TurnDecision(
        val isThoughtComplete: Boolean,
        val recommendedSilenceWaitMs: Long,
        val reason: String
    )

    fun evaluateTurn(transcript: String, silenceDurationMs: Long): TurnDecision {
        val cleaned = transcript.trim().lowercase()
        if (cleaned.isEmpty()) {
            return TurnDecision(
                isThoughtComplete = false,
                recommendedSilenceWaitMs = 600L,
                reason = "Empty transcript"
            )
        }

        val words = cleaned
            .replace(Regex("[^a-zA-Z0-9\\u0900-\\u097F\\s?।.]"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        val lastWord = words.lastOrNull()?.trimEnd('.', ',', '?', '!', '।') ?: ""

        // 1. Check if user is mid-thought (trailing conjunction or hesitation filler)
        if (lastWord in TRAILING_CONJUNCTIONS || cleaned.endsWith("...")) {
            val requiredWait = 900L
            return TurnDecision(
                isThoughtComplete = silenceDurationMs >= requiredWait,
                recommendedSilenceWaitMs = requiredWait,
                reason = "Trailing conjunction/hesitation ('$lastWord') — holding turn for 900ms grace period"
            )
        }

        // 2. Single-word conversational greetings / wake acknowledgments commit in <200ms (180ms)
        if (words.size == 1 && lastWord in RAPID_GREETING_TOKENS) {
            val ultraFastWait = 180L
            return TurnDecision(
                isThoughtComplete = silenceDurationMs >= ultraFastWait,
                recommendedSilenceWaitMs = ultraFastWait,
                reason = "Single-word greeting/acknowledgment ('$lastWord') — ultra-fast 180ms turn commit"
            )
        }

        // 3. Check if user completed a definitive Hindi/Hinglish/English command or question
        if (lastWord in SENTENCE_TERMINATORS ||
            cleaned.endsWith("?") ||
            cleaned.endsWith("।") ||
            cleaned.endsWith(".")
        ) {
            val fastWait = 200L
            return TurnDecision(
                isThoughtComplete = silenceDurationMs >= fastWait,
                recommendedSilenceWaitMs = fastWait,
                reason = "Definitive sentence terminator ('$lastWord') — fast 200ms turn commit"
            )
        }

        // 4. Default balanced silence window
        val standardWait = 450L
        return TurnDecision(
            isThoughtComplete = silenceDurationMs >= standardWait,
            recommendedSilenceWaitMs = standardWait,
            reason = "Standard linguistic turn boundary (450ms)"
        )
    }
}
