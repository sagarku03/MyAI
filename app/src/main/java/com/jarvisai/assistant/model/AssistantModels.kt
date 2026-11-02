package com.jarvisai.assistant.model

import com.example.ui.theme.JarvisThemeMode

enum class AssistantStatus(val label: String, val hudCode: String, val description: String) {
    IDLE("Idle", "SYS-IDLE // STANDBY", "All neural subsystems online and awaiting command input."),
    LISTENING("Listening", "AUD-IN // CAPTURING", "Active acoustic array receiving real-time voice stream."),
    THINKING("Thinking", "NEURAL // PROCESSING", "Synthesizing context, memories, and Gemini inference."),
    SPEAKING("Speaking", "AUD-OUT // TRANSMITTING", "Streaming real-time synthesized audio response.")
}

enum class SupportedLanguage(val displayName: String, val code: String, val systemPromptDirective: String) {
    ENGLISH(
        displayName = "English",
        code = "en-US",
        systemPromptDirective = "Respond clearly and naturally in English, maintaining a sharp, intelligent, helpful JARVIS HUD assistant tone."
    ),
    HINDI(
        displayName = "Hindi (हिंदी)",
        code = "hi-IN",
        systemPromptDirective = "Respond naturally in Hindi (Devanagari script: हिंदी), maintaining a respectful, intelligent, futuristic JARVIS assistant persona. Understand English, Hindi, or Hinglish inputs seamlessly."
    ),
    HINGLISH(
        displayName = "Hinglish",
        code = "en-IN",
        systemPromptDirective = "Respond naturally in conversational Hinglish (a natural blend of Hindi and English written in Latin/Roman script, just like modern Indian tech speakers talk), maintaining an intelligent, witty, helpful JARVIS persona."
    )
}

enum class MemoryCategory(val label: String, val iconName: String) {
    PERSONAL("Personal", "person"),
    PREFERENCES("Preferences", "tune"),
    WORK("Work & Study", "work"),
    PROJECTS("Projects", "code"),
    IMPORTANT("Important", "star"),
    GENERAL("General", "memory")
}

data class GeminiModelOption(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val isLiveVoiceModel: Boolean = false,
    val supportsBidiWebSocket: Boolean = false
)

object SupportedGeminiModels {
    const val DEFAULT_TEXT_MODEL = "gemini-3-flash-preview"
    const val DEFAULT_VOICE_MODEL = "auto-live-voice"

    val defaultTextModels = listOf(
        GeminiModelOption(
            id = "gemini-3-flash-preview",
            displayName = "Gemini 3 Flash",
            subtitle = "Latest next-gen high-speed multimodal Flash model (Recommended)"
        ),
        GeminiModelOption(
            id = "gemini-3.8-flash",
            displayName = "Gemini 3.8 Flash",
            subtitle = "Latest Gemini 3.8 Flash model with auto-fallback support"
        ),
        GeminiModelOption(
            id = "gemini-3.1-pro-preview",
            displayName = "Gemini 3.1 Pro",
            subtitle = "Advanced reasoning, coding, and deep analytical synthesis"
        ),
        GeminiModelOption(
            id = "gemini-2.5-flash-lite",
            displayName = "Gemini 2.5 Flash Lite",
            subtitle = "Ultra-low latency lightweight model"
        ),
        GeminiModelOption(
            id = "gemini-2.0-flash",
            displayName = "Gemini 2.0 Flash",
            subtitle = "High-throughput general conversation & multilingual tasks"
        )
    )

    val defaultVoiceModels = listOf(
        GeminiModelOption(
            id = "auto-live-voice",
            displayName = "Auto-Detect Live Voice (ListModels + Acoustic Fallback)",
            subtitle = "Automatically discovers Bidi WebSocket model via ListModels or uses Continuous Neural Voice",
            isLiveVoiceModel = true,
            supportsBidiWebSocket = true
        ),
        GeminiModelOption(
            id = "gemini-2.5-flash-native-audio-preview-09-2025",
            displayName = "Gemini 2.5 Flash Native Audio (Bidi Live)",
            subtitle = "Native bidirectional audio streaming over WebSocket",
            isLiveVoiceModel = true,
            supportsBidiWebSocket = true
        ),
        GeminiModelOption(
            id = "gemini-2.0-flash-live-001",
            displayName = "Gemini 2.0 Flash Live 001 (Bidi Live)",
            subtitle = "Bidirectional WebSocket live voice session",
            isLiveVoiceModel = true,
            supportsBidiWebSocket = true
        )
    )

    val voicePersonas = listOf(
        "Puck" to "Crisp, articulate tactical HUD voice",
        "Charon" to "Deep, calm analytical command voice",
        "Kore" to "Warm, clear intelligent assistant voice",
        "Fenrir" to "Resonant, authoritative operational voice",
        "Aoede" to "Expressive, natural conversational voice"
    )
}

data class UserSettings(
    val assistantName: String = "JARVIS",
    val language: SupportedLanguage = SupportedLanguage.ENGLISH,
    val textModel: String = SupportedGeminiModels.DEFAULT_TEXT_MODEL,
    val voiceModel: String = SupportedGeminiModels.DEFAULT_VOICE_MODEL,
    val voicePersona: String = "Puck",
    val voiceSpeed: Float = 1.0f,
    val voicePitch: Float = 1.0f,
    val themeMode: JarvisThemeMode = JarvisThemeMode.ARC_CYAN,
    val memoryEnabled: Boolean = true,
    val autoInjectMemories: Boolean = true,
    val confirmConsequentialActions: Boolean = true,
    val privacyRedactTelemetry: Boolean = true
)

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean
)

data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
    val latencyMs: Long = 0L,
    val modelTested: String = "",
    val discoveredTextModels: List<GeminiModelOption> = emptyList(),
    val discoveredVoiceModels: List<GeminiModelOption> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
