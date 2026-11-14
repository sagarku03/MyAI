package com.jarvisai.assistant.model

enum class ThinkingLevel(
    val label: String,
    val tokens: Int,
    val effortKey: String,
    val description: String
) {
    OFF("Off", 0, "none", "0 tokens • Direct response (Fastest)"),
    LOW("Low", 1024, "low", "1,024 tokens • Quick edits & daily coding"),
    MEDIUM("Med", 4096, "medium", "4,096 tokens • Balanced reasoning (Default)"),
    HIGH("High", 8192, "high", "8,192 tokens • Deep architecture & debugging"),
    EXTRA("Extra", 16384, "xhigh", "16,384 tokens • Max multi-step deliberation")
}

data class ThinkingPreference(
    val level: ThinkingLevel = ThinkingLevel.MEDIUM,
    val customTokens: Int = level.tokens
) {
    val activeTokens: Int
        get() = level.tokens

    val isEnabled: Boolean
        get() = level != ThinkingLevel.OFF && activeTokens > 0

    val displaySummary: String
        get() = when (level) {
            ThinkingLevel.OFF -> "Thinking: Off"
            ThinkingLevel.LOW -> "Thinking: Low (1K)"
            ThinkingLevel.MEDIUM -> "Thinking: Med (4K)"
            ThinkingLevel.HIGH -> "Thinking: High (8K)"
            ThinkingLevel.EXTRA -> "Thinking: Extra (16K)"
        }
}
