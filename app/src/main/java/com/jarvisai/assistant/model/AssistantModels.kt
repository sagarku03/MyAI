package com.jarvisai.assistant.model

import com.example.ui.theme.JarvisThemeMode

enum class AssistantStatus(val label: String, val hudCode: String, val description: String) {
    IDLE("Idle", "SYS-IDLE // STANDBY", "Namaste, main JARVIS hoon. Sabhi neural subsystems online hain."),
    LISTENING("Listening", "AUD-IN // CAPTURING", "Ji, main sun raha hoon. Aapki aawaz real-time stream ho rahi hai."),
    THINKING("Thinking", "NEURAL // PROCESSING", "Ek second, main aapki request aur memories process kar raha hoon."),
    SPEAKING("Speaking", "AUD-OUT // TRANSMITTING", "JARVIS real-time audio mein jawab de raha hai.")
}

/**
 * Live Voice specific UI status indicators required for real-time call diagnostics.
 */
enum class LiveVoiceUiStateStatus(val displayLabel: String, val hinglishSubtitle: String) {
    STANDBY("Standby", "Live call start karne ke liye button dabayein."),
    CONNECTING("Connecting...", "Gemini Live server se connect ho raha hai..."),
    LISTENING("Listening...", "Ji, main sun raha hoon. Aap boliye..."),
    USER_SPEAKING("User Speaking...", "Aapki aawaz suni ja rahi hai..."),
    PROCESSING("Processing...", "Ek second, main process kar raha hoon..."),
    AI_SPEAKING("AI Speaking...", "JARVIS bol raha hai..."),
    MIC_ERROR("Microphone Error...", "Microphone permission ya hardware mein problem hai."),
    CONNECTION_ERROR("Connection Error...", "Connection mein problem aa rahi hai.")
}

enum class VoiceGender(val displayName: String, val defaultGeminiPersona: String) {
    MALE("Male Voice", "Puck"),
    FEMALE("Female Voice", "Kore")
}

data class VoiceOption(
    val id: String,
    val displayName: String,
    val gender: VoiceGender,
    val subtitle: String,
    val previewSampleHinglish: String
)

enum class SupportedLanguage(val displayName: String, val code: String, val systemPromptDirective: String) {
    HINGLISH(
        displayName = "Hinglish",
        code = "en-IN",
        systemPromptDirective = "Respond naturally in conversational Hinglish (a natural blend of Hindi and English written in Latin/Roman script, e.g., 'Namaste, main JARVIS hoon. Bataiye main aapki kya madad kar sakta hoon?'), maintaining an intelligent, respectful, helpful JARVIS persona."
    ),
    HINDI(
        displayName = "Hindi (हिंदी)",
        code = "hi-IN",
        systemPromptDirective = "Respond naturally in Hindi (Devanagari script: हिंदी) or Hinglish as appropriate, maintaining a respectful, intelligent, futuristic JARVIS assistant persona. Understand English, Hindi, or Hinglish inputs seamlessly."
    ),
    ENGLISH(
        displayName = "English",
        code = "en-US",
        systemPromptDirective = "Respond clearly and naturally in English (while understanding Hindi/Hinglish seamlessly), maintaining a sharp, intelligent, helpful JARVIS HUD assistant tone."
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
    const val DEFAULT_VOICE_MODEL = "gemini-2.5-flash-native-audio-preview-12-2025"

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
            id = "gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash",
            subtitle = "High-throughput general conversation & multilingual tasks"
        )
    )

    val defaultVoiceModels = listOf(
        GeminiModelOption(
            id = "gemini-2.5-flash-native-audio-preview-12-2025",
            displayName = "Gemini 2.5 Flash Native Audio (12-2025)",
            subtitle = "Native bidirectional audio streaming over WebSocket (Recommended)",
            isLiveVoiceModel = true,
            supportsBidiWebSocket = true
        ),
        GeminiModelOption(
            id = "gemini-2.5-flash-native-audio-preview-09-2025",
            displayName = "Gemini 2.5 Flash Native Audio (09-2025)",
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

    val voiceOptions: List<VoiceOption> = listOf(
        VoiceOption(
            id = "Puck",
            displayName = "JARVIS Tactical (Puck)",
            gender = VoiceGender.MALE,
            subtitle = "Male Voice • Crisp, smart, energetic Hinglish & English command voice",
            previewSampleHinglish = "Namaste, main JARVIS hoon. Ji, main sun raha hoon, bataiye main aapki kya madad kar sakta hoon?"
        ),
        VoiceOption(
            id = "Charon",
            displayName = "JARVIS Deep Command (Charon)",
            gender = VoiceGender.MALE,
            subtitle = "Male Voice • Calm, deep, composed analytical voice",
            previewSampleHinglish = "Namaste, main JARVIS hoon. Sabhi systems online hain aur main aapki command ke liye ready hoon."
        ),
        VoiceOption(
            id = "Fenrir",
            displayName = "JARVIS Alpha (Fenrir)",
            gender = VoiceGender.MALE,
            subtitle = "Male Voice • Bold, resonant operational tactical voice",
            previewSampleHinglish = "Namaste, main JARVIS hoon. Ek second, main aapki request process kar raha hoon."
        ),
        VoiceOption(
            id = "Orus",
            displayName = "JARVIS Sentinel (Orus)",
            gender = VoiceGender.MALE,
            subtitle = "Male Voice • Steady, authoritative executive assistant voice",
            previewSampleHinglish = "Namaste sir, main JARVIS hoon. Aapka system bilkul ready hai."
        ),
        VoiceOption(
            id = "Kore",
            displayName = "FRIDAY Clear (Kore)",
            gender = VoiceGender.FEMALE,
            subtitle = "Female Voice • Warm, natural, crystal-clear Hinglish & Hindi voice",
            previewSampleHinglish = "Namaste, main JARVIS AI hoon. Ji, main sun rahi hoon, bataiye aaj kya karna hai?"
        ),
        VoiceOption(
            id = "Aoede",
            displayName = "FRIDAY Expressive (Aoede)",
            gender = VoiceGender.FEMALE,
            subtitle = "Female Voice • Smooth, expressive conversational voice",
            previewSampleHinglish = "Namaste! Main aapki AI assistant hoon. Aap Hindi, Hinglish ya English mein baat kar sakte hain."
        ),
        VoiceOption(
            id = "Leda",
            displayName = "FRIDAY Serene (Leda)",
            gender = VoiceGender.FEMALE,
            subtitle = "Female Voice • Calm, articulate, melodic assistant voice",
            previewSampleHinglish = "Namaste, main sun rahi hoon. Bataiye main aapki kis tarah madad kar sakti hoon?"
        ),
        VoiceOption(
            id = "Zephyr",
            displayName = "FRIDAY Dynamic (Zephyr)",
            gender = VoiceGender.FEMALE,
            subtitle = "Female Voice • Bright, responsive, modern conversational voice",
            previewSampleHinglish = "Hello! Main JARVIS AI hoon. Aapki har command ke liye main taiyaar hoon."
        )
    )

    val voicePersonas = voiceOptions.map { it.id to it.subtitle }

    fun findVoiceOption(personaId: String, gender: VoiceGender): VoiceOption {
        return voiceOptions.find { it.id.equals(personaId, ignoreCase = true) && it.gender == gender }
            ?: voiceOptions.find { it.id.equals(personaId, ignoreCase = true) }
            ?: voiceOptions.firstOrNull { it.gender == gender }
            ?: voiceOptions.first()
    }
}

enum class PersonalityMode(val displayName: String, val subtitle: String, val systemDirective: String) {
    PROFESSIONAL(
        displayName = "Professional Mode",
        subtitle = "Sharp, composed, analytical executive assistant tone.",
        systemDirective = "Maintain an executive, articulate, highly intelligent, composed tone with tactical precision and objective clarity."
    ),
    FRIENDLY(
        displayName = "Friendly Mode",
        subtitle = "Warm, encouraging, enthusiastic daily companion.",
        systemDirective = "Be warm, encouraging, engaging, energetic, and highly supportive like a trusted close friend and brilliant partner."
    ),
    JARVIS(
        displayName = "Jarvis / Tactical Mode",
        subtitle = "Cybernetic HUD command officer with crisp operational telemetry.",
        systemDirective = "Speak with crisp, hyper-competent JARVIS tactical command presence. Address the user respectfully, report telemetry and action status with precision, and maintain futuristic HUD clarity."
    ),
    CASUAL(
        displayName = "Casual Mode",
        subtitle = "Relaxed, conversational, natural everyday style.",
        systemDirective = "Keep the conversation relaxed, natural, approachable, and effortless—like chatting with a tech-savvy friend without formality."
    ),
    STUDY_ASSISTANT(
        displayName = "Study Assistant Mode",
        subtitle = "Patient, pedagogical tutor breaking down complex concepts.",
        systemDirective = "Act as an expert pedagogical mentor. Break down complex subjects step-by-step, provide memorable analogies, offer practice questions, and encourage deep conceptual understanding."
    ),
    DEVELOPER(
        displayName = "Developer Mode",
        subtitle = "Code-first, architecture, debugging & systems expert.",
        systemDirective = "Adopt a software engineer & architect persona. Prioritize correct idiomatic code, explain algorithmic complexity, provide edge-case analysis, and format responses cleanly with markdown code blocks."
    ),
    CONCISE(
        displayName = "Concise Mode",
        subtitle = "Ultra-succinct, direct, bullet-points only.",
        systemDirective = "Provide ultra-concise, direct, highly dense bullet-point answers. Omit pleasantries, filler phrases, and unnecessary preambles."
    ),
    HINGLISH(
        displayName = "Hinglish Mode",
        subtitle = "Conversational, vibrant Bollywood & tech Hinglish blend.",
        systemDirective = "Speak naturally in authentic conversational Hinglish (Hindi + English written in Roman script) with great warmth, charisma, and desi tech flair."
    ),
    FEMALE_COMPANION(
        displayName = "Female Voice Companion Mode",
        subtitle = "Friendly, supportive, non-romantic conversational companion.",
        systemDirective = "Speak as FRIDAY, a warm, intelligent, supportive, and cheerful female AI companion. Maintain a caring, respectful, conversational, and uplifting demeanor without romantic simulation."
    ),
    CUSTOM(
        displayName = "Custom Personality Mode",
        subtitle = "User-customized AI persona directives.",
        systemDirective = ""
    )
}

enum class MemoryImportance(val label: String, val weight: Int) {
    LOW("Low", 1),
    NORMAL("Normal", 2),
    IMPORTANT("Important", 3),
    CRITICAL("Critical", 4);

    companion object {
        fun fromString(value: String?): MemoryImportance {
            return entries.find { it.name.equals(value, ignoreCase = true) || it.label.equals(value, ignoreCase = true) }
                ?: NORMAL
        }
    }
}

enum class WorkflowActionType(val displayName: String, val description: String) {
    OPEN_APP("Open Application", "Launch an installed app or Chrome PWA."),
    START_TIMER("Set Alarm / Timer", "Create a system clock alarm or timer."),
    SHOW_REMINDER("Create Reminder", "Schedule an Android calendar reminder."),
    SET_PERSONALITY("Switch Personality", "Change the active JARVIS personality mode."),
    SPEAK_TEXT("Voice Announcement", "Speak a message aloud using speech synthesis."),
    SEARCH_WEB("Search in Chrome", "Perform a Google search in Chrome."),
    TOGGLE_FLASHLIGHT("Toggle Flashlight", "Turn device flashlight ON or OFF."),
    CLICK_UI_ELEMENT("Click Screen Element", "Tap a button or element by label via Accessibility."),
    SCROLL_SCREEN("Scroll Screen", "Scroll current screen up, down, left, or right."),
    INPUT_TEXT("Type Into Field", "Type text into a matching or focused input field."),
    SET_VOLUME("Set Volume %", "Set device media volume to a target percentage."),
    SET_BRIGHTNESS("Set Brightness %", "Set screen brightness level.")
}

data class WorkflowAction(
    val type: WorkflowActionType,
    val title: String,
    val param1: String = "",
    val param2: String = ""
)

data class UserSettings(
    val assistantName: String = "JARVIS",
    val language: SupportedLanguage = SupportedLanguage.HINGLISH,
    val textModel: String = SupportedGeminiModels.DEFAULT_TEXT_MODEL,
    val voiceModel: String = SupportedGeminiModels.DEFAULT_VOICE_MODEL,
    val voiceGender: VoiceGender = VoiceGender.MALE,
    val voicePersona: String = "Puck",
    val voiceSpeed: Float = 1.0f,
    val voicePitch: Float = 1.0f,
    val autoSpeakChatReplies: Boolean = false,
    val floatingOverlayEnabled: Boolean = true,
    val themeMode: JarvisThemeMode = JarvisThemeMode.ARC_CYAN,
    val memoryEnabled: Boolean = true,
    val autoInjectMemories: Boolean = true,
    val confirmConsequentialActions: Boolean = true,
    val privacyRedactTelemetry: Boolean = true,
    val personalityMode: PersonalityMode = PersonalityMode.PROFESSIONAL,
    val customPersonalityPrompt: String = "",
    val wakeWordEnabled: Boolean = false,
    val wakeWordSensitivity: String = "MEDIUM",
    val autoMemorySuggestionsEnabled: Boolean = true,
    val autoMemoryRetrievalEnabled: Boolean = true,
    val memoryExpirationDays: Int = 30,
    val notificationAssistantEnabled: Boolean = false,
    val notificationExcludedPackages: Set<String> = emptySet(),
    val requireBiometricForHighRisk: Boolean = false,
    val maxAutonomousSteps: Int = 8
)

data class PendingSafetyConfirmation(
    val callId: String,
    val toolName: String,
    val title: String,
    val description: String,
    val riskLevel: String = "HIGH",
    val argumentsSummary: String = "",
    val riskBadge: String = "HIGH RISK // CONFIRMATION REQUIRED",
    val details: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
) {
    val actionTitle: String
        get() = title
}

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val activityName: String = ""
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

enum class CompanionModeState(val displayLabel: String, val badgeText: String) {
    INACTIVE("Inactive", "INACTIVE"),
    ACTIVATING("Activating...", "ACTIVATING"),
    ACTIVE("Active", "ACTIVE // KORE HINGLISH"),
    DEACTIVATING("Deactivating...", "DEACTIVATING"),
    ERROR("Error", "ERROR")
}

data class ActiveAppContextSnapshot(
    val activePackageName: String = "",
    val activeAppName: String = "",
    val activeWindowTitle: String = "",
    val previousPackageName: String = "",
    val previousAppName: String = "",
    val currentInteractionTarget: String = "",
    val currentTask: String = "",
    val isVerifiedForeground: Boolean = false,
    val wasTargetAppExitedByBack: Boolean = false,
    val updatedAtMs: Long = System.currentTimeMillis()
)

data class ShortTermTurn(
    val role: String,
    val text: String,
    val intent: String = "",
    val activeAppName: String = "",
    val timestampMs: Long = System.currentTimeMillis()
)

data class ActionVerificationRecord(
    val actionName: String,
    val targetAppName: String,
    val targetPackage: String,
    val executed: Boolean,
    val verifiedForeground: Boolean,
    val verificationUncertain: Boolean,
    val resultingPackage: String,
    val summary: String,
    val hinglishReport: String,
    val timestampMs: Long = System.currentTimeMillis()
) {
    val verificationStatus: String
        get() = when {
            verifiedForeground -> "VERIFIED"
            verificationUncertain -> "UNCERTAIN"
            else -> "FAILED"
        }

    val spokenHinglishReport: String
        get() = hinglishReport
}

