package com.jarvisai.assistant.model

enum class ThinkingLevel(val label: String, val tokens: Int, val description: String) {
    OFF("Off", 0, "0 tokens • Direct response (Fastest)"),
    LOW("Low", 1024, "1,024 tokens • Quick logic & code"),
    MEDIUM("Med", 4096, "4,096 tokens • Standard reasoning (Default)"),
    HIGH("High", 8192, "8,192 tokens • Deep multi-step thinking")
}

data class ThinkingPreference(
    val level: ThinkingLevel = ThinkingLevel.MEDIUM,
    val customTokens: Int = 4096
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
        }
}
