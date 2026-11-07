package com.jarvisai.assistant.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.JarvisThemeMode
import com.example.ui.theme.ThemeDisplayMode
import com.example.ui.theme.ThemePalette
import com.jarvisai.assistant.model.PersonalityMode
import com.jarvisai.assistant.model.SupportedGeminiModels
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.model.VoiceGender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jarvis_user_preferences"
)

class UserPreferencesRepository(private val context: Context) {

    suspend fun getCurrentSettings(): UserSettings = userSettingsFlow.first()

    val userSettingsFlow: Flow<UserSettings> = context.jarvisDataStore.data.map { prefs ->
        val languageName = prefs[Keys.LANGUAGE] ?: SupportedLanguage.HINGLISH.name
        val language = SupportedLanguage.entries.find { it.name == languageName }
            ?: SupportedLanguage.HINGLISH

        val themeName = prefs[Keys.THEME_MODE] ?: JarvisThemeMode.ROYAL_PURPLE.name
        val themeMode = JarvisThemeMode.entries.find { it.name == themeName }
            ?: JarvisThemeMode.ROYAL_PURPLE

        val paletteName = prefs[Keys.THEME_PALETTE]
        val themePalette = if (paletteName != null) {
            ThemePalette.entries.find { it.name == paletteName } ?: themeMode.palette
        } else {
            themeMode.palette
        }

        val displayModeName = prefs[Keys.THEME_DISPLAY_MODE]
        val themeDisplayMode = if (displayModeName != null) {
            ThemeDisplayMode.entries.find { it.name == displayModeName } ?: themeMode.displayMode
        } else {
            themeMode.displayMode
        }

        val storedTextModel = prefs[Keys.TEXT_MODEL]
        val resolvedTextModel = when {
            storedTextModel.isNullOrBlank() -> SupportedGeminiModels.DEFAULT_TEXT_MODEL
            storedTextModel.contains("2.5") || storedTextModel.contains("3-flash") || storedTextModel.contains("3.8") || storedTextModel.contains("3.1") -> SupportedGeminiModels.DEFAULT_TEXT_MODEL
            else -> storedTextModel
        }

        val storedVoiceModel = prefs[Keys.VOICE_MODEL]
        val resolvedVoiceModel = when {
            storedVoiceModel.isNullOrBlank() -> SupportedGeminiModels.DEFAULT_VOICE_MODEL
            storedVoiceModel == "auto-live-voice" -> SupportedGeminiModels.DEFAULT_VOICE_MODEL
            storedVoiceModel.contains("2.5") || storedVoiceModel.contains("3-flash") -> SupportedGeminiModels.DEFAULT_VOICE_MODEL
            else -> storedVoiceModel
        }

        val storedPersona = prefs[Keys.VOICE_PERSONA] ?: "Puck"
        val storedGenderName = prefs[Keys.PREFERRED_TTS_VOICE_GENDER] ?: prefs[Keys.VOICE_GENDER]
        val matchedVoiceOption = SupportedGeminiModels.voiceOptions.find {
            it.id.equals(storedPersona, ignoreCase = true)
        }
        val resolvedGender = when {
            storedGenderName != null -> VoiceGender.entries.find { it.name == storedGenderName }
                ?: matchedVoiceOption?.gender
                ?: VoiceGender.MALE
            matchedVoiceOption != null -> matchedVoiceOption.gender
            else -> VoiceGender.MALE
        }
        val resolvedPersona = when {
            resolvedGender == VoiceGender.FEMALE && (storedPersona.equals("Aoede", ignoreCase = true) ||
                storedPersona.equals("Kore", ignoreCase = true) ||
                storedPersona.equals("Leda", ignoreCase = true) ||
                storedPersona.equals("Zephyr", ignoreCase = true)) -> storedPersona
            resolvedGender == VoiceGender.FEMALE -> "Aoede"
            resolvedGender == VoiceGender.MALE && (storedPersona.equals("Puck", ignoreCase = true) ||
                storedPersona.equals("Charon", ignoreCase = true) ||
                storedPersona.equals("Fenrir", ignoreCase = true) ||
                storedPersona.equals("Orus", ignoreCase = true)) -> storedPersona
            else -> "Puck"
        }

        val personalityModeName = prefs[Keys.PERSONALITY_MODE] ?: PersonalityMode.PROFESSIONAL.name
        val personalityMode = PersonalityMode.entries.find { it.name == personalityModeName }
            ?: PersonalityMode.PROFESSIONAL

        UserSettings(
            assistantName = prefs[Keys.ASSISTANT_NAME]?.ifBlank { "JARVIS" } ?: "JARVIS",
            language = language,
            textModel = resolvedTextModel,
            voiceModel = resolvedVoiceModel,
            voiceGender = resolvedGender,
            preferredTtsVoiceGender = resolvedGender,
            preferredTtsVoiceName = resolvedPersona,
            voicePersona = resolvedPersona,
            voiceSpeed = prefs[Keys.VOICE_SPEED] ?: 1.0f,
            voicePitch = prefs[Keys.VOICE_PITCH] ?: 1.0f,
            autoSpeakChatReplies = prefs[Keys.AUTO_SPEAK_CHAT] ?: false,
            floatingOverlayEnabled = prefs[Keys.FLOATING_OVERLAY_ENABLED] ?: true,
            themeMode = themeMode,
            themePalette = themePalette,
            themeDisplayMode = themeDisplayMode,
            memoryEnabled = prefs[Keys.MEMORY_ENABLED] ?: true,
            autoInjectMemories = prefs[Keys.AUTO_INJECT_MEMORIES] ?: true,
            confirmConsequentialActions = false,
            privacyRedactTelemetry = prefs[Keys.PRIVACY_REDACT] ?: true,
            personalityMode = personalityMode,
            customPersonalityPrompt = prefs[Keys.CUSTOM_PERSONALITY_PROMPT] ?: "",
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
            wakeWordSensitivity = prefs[Keys.WAKE_WORD_SENSITIVITY] ?: "MEDIUM",
            autoMemorySuggestionsEnabled = prefs[Keys.AUTO_MEMORY_SUGGESTIONS] ?: true,
            autoMemoryRetrievalEnabled = prefs[Keys.AUTO_MEMORY_RETRIEVAL] ?: true,
            memoryExpirationDays = prefs[Keys.MEMORY_EXPIRATION_DAYS] ?: 30,
            notificationAssistantEnabled = prefs[Keys.NOTIFICATION_ASSISTANT_ENABLED] ?: false,
            notificationExcludedPackages = prefs[Keys.NOTIFICATION_EXCLUDED_PACKAGES] ?: emptySet(),
            requireBiometricForHighRisk = prefs[Keys.REQUIRE_BIOMETRIC_HIGH_RISK] ?: false,
            maxAutonomousSteps = (prefs[Keys.MAX_AUTONOMOUS_STEPS] ?: 8).coerceIn(1, 100),
            proactiveCompanionEnabled = prefs[Keys.PROACTIVE_COMPANION_ENABLED] ?: true,
            ambientRoomTalkEnabled = prefs[Keys.AMBIENT_ROOM_TALK_ENABLED] ?: true,
            proactiveIntervalSeconds = (prefs[Keys.PROACTIVE_INTERVAL_SECONDS] ?: 28).coerceIn(15, 180)
        )
    }

    suspend fun updateAssistantName(name: String) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.ASSISTANT_NAME] = name.trim().ifEmpty { "JARVIS" }
        }
    }

    suspend fun updateLanguage(language: SupportedLanguage) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language.name
        }
    }

    suspend fun updateTextModel(modelId: String) {
        val clean = modelId.trim().removePrefix("models/").replace(" ", "")
        if (clean.isNotEmpty()) {
            context.jarvisDataStore.edit { prefs ->
                prefs[Keys.TEXT_MODEL] = clean
            }
        }
    }

    suspend fun updateVoiceModel(modelId: String) {
        val clean = modelId.trim().removePrefix("models/").replace(" ", "")
        if (clean.isNotEmpty()) {
            context.jarvisDataStore.edit { prefs ->
                prefs[Keys.VOICE_MODEL] = clean
            }
        }
    }

    suspend fun updateVoiceGender(gender: VoiceGender) {
        val targetPersona = if (gender == VoiceGender.FEMALE) "Aoede" else "Puck"
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.VOICE_GENDER] = gender.name
            prefs[Keys.PREFERRED_TTS_VOICE_GENDER] = gender.name
            prefs[Keys.PREFERRED_TTS_VOICE_NAME] = targetPersona
            prefs[Keys.VOICE_PERSONA] = targetPersona
        }
    }

    suspend fun updatePreferredTtsVoiceGender(
        gender: VoiceGender,
        voiceName: String = if (gender == VoiceGender.FEMALE) "Aoede" else "Puck"
    ) {
        val targetPersona = if (voiceName.isNotBlank() && !voiceName.contains("Neural", ignoreCase = true)) {
            voiceName
        } else {
            if (gender == VoiceGender.FEMALE) "Aoede" else "Puck"
        }
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.VOICE_GENDER] = gender.name
            prefs[Keys.PREFERRED_TTS_VOICE_GENDER] = gender.name
            prefs[Keys.PREFERRED_TTS_VOICE_NAME] = targetPersona
            prefs[Keys.VOICE_PERSONA] = targetPersona
        }
    }

    suspend fun updateVoicePersona(persona: String) {
        val matched = SupportedGeminiModels.voiceOptions.find { it.id.equals(persona, ignoreCase = true) }
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.VOICE_PERSONA] = persona
            prefs[Keys.PREFERRED_TTS_VOICE_NAME] = persona
            if (matched != null) {
                prefs[Keys.VOICE_GENDER] = matched.gender.name
                prefs[Keys.PREFERRED_TTS_VOICE_GENDER] = matched.gender.name
            }
        }
    }

    suspend fun updateVoiceSpeed(speed: Float) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.VOICE_SPEED] = speed.coerceIn(0.5f, 2.0f)
        }
    }

    suspend fun updateVoicePitch(pitch: Float) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.VOICE_PITCH] = pitch.coerceIn(0.5f, 2.0f)
        }
    }

    suspend fun updateAutoSpeakChatReplies(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.AUTO_SPEAK_CHAT] = enabled
        }
    }

    suspend fun updateFloatingOverlayEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.FLOATING_OVERLAY_ENABLED] = enabled
        }
    }

    suspend fun updateThemeMode(themeMode: JarvisThemeMode) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = themeMode.name
            prefs[Keys.THEME_PALETTE] = themeMode.palette.name
            prefs[Keys.THEME_DISPLAY_MODE] = themeMode.displayMode.name
        }
    }

    suspend fun updateThemePalette(palette: ThemePalette) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.THEME_PALETTE] = palette.name
            val currentModeName = prefs[Keys.THEME_DISPLAY_MODE] ?: ThemeDisplayMode.DARK.name
            val displayMode = ThemeDisplayMode.entries.find { it.name == currentModeName } ?: ThemeDisplayMode.DARK
            val resolvedThemeMode = resolveThemeMode(palette, displayMode)
            prefs[Keys.THEME_MODE] = resolvedThemeMode.name
        }
    }

    suspend fun updateThemeDisplayMode(displayMode: ThemeDisplayMode) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.THEME_DISPLAY_MODE] = displayMode.name
            val currentPaletteName = prefs[Keys.THEME_PALETTE] ?: ThemePalette.ROYAL_PURPLE.name
            val palette = ThemePalette.entries.find { it.name == currentPaletteName } ?: ThemePalette.ROYAL_PURPLE
            val resolvedThemeMode = resolveThemeMode(palette, displayMode)
            prefs[Keys.THEME_MODE] = resolvedThemeMode.name
        }
    }

    private fun resolveThemeMode(palette: ThemePalette, displayMode: ThemeDisplayMode): JarvisThemeMode {
        return when (displayMode) {
            ThemeDisplayMode.SYSTEM -> JarvisThemeMode.SYSTEM_DEFAULT
            ThemeDisplayMode.LIGHT -> when (palette) {
                ThemePalette.OCEAN_BLUE -> JarvisThemeMode.OCEAN_BLUE_LIGHT
                ThemePalette.ROYAL_PURPLE -> JarvisThemeMode.ROYAL_PURPLE_LIGHT
                ThemePalette.AQUA_MINT -> JarvisThemeMode.AQUA_MINT_LIGHT
            }
            ThemeDisplayMode.DARK -> when (palette) {
                ThemePalette.OCEAN_BLUE -> JarvisThemeMode.OCEAN_BLUE_DARK
                ThemePalette.ROYAL_PURPLE -> JarvisThemeMode.ROYAL_PURPLE_DARK
                ThemePalette.AQUA_MINT -> JarvisThemeMode.AQUA_MINT_DARK
            }
        }
    }

    suspend fun updateMemoryEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.MEMORY_ENABLED] = enabled
        }
    }

    suspend fun updateAutoInjectMemories(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.AUTO_INJECT_MEMORIES] = enabled
        }
    }

    suspend fun updateConfirmConsequentialActions(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.CONFIRM_CONSEQUENTIAL] = enabled
        }
    }

    suspend fun updatePrivacyRedactTelemetry(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.PRIVACY_REDACT] = enabled
        }
    }

    suspend fun updatePersonalityMode(mode: PersonalityMode) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.PERSONALITY_MODE] = mode.name
            mode.preferredGender?.let { gender ->
                prefs[Keys.VOICE_GENDER] = gender.name
                prefs[Keys.PREFERRED_TTS_VOICE_GENDER] = gender.name
                prefs[Keys.PREFERRED_TTS_VOICE_NAME] =
                    if (gender == VoiceGender.FEMALE) "en-GB-SoniaNeural" else "en-GB-RyanNeural"
                val targetPersona = mode.preferredVoicePersona ?: gender.defaultGeminiPersona
                prefs[Keys.VOICE_PERSONA] = targetPersona
            }
        }
    }

    suspend fun updateCustomPersonalityPrompt(prompt: String) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_PERSONALITY_PROMPT] = prompt.trim()
        }
    }

    suspend fun updateWakeWordEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.WAKE_WORD_ENABLED] = enabled
        }
    }

    suspend fun updateWakeWordSensitivity(sensitivity: String) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.WAKE_WORD_SENSITIVITY] = sensitivity
        }
    }

    suspend fun updateAutoMemorySuggestions(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.AUTO_MEMORY_SUGGESTIONS] = enabled
        }
    }

    suspend fun updateAutoMemoryRetrieval(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.AUTO_MEMORY_RETRIEVAL] = enabled
        }
    }

    suspend fun updateMemoryExpirationDays(days: Int) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.MEMORY_EXPIRATION_DAYS] = days
        }
    }

    suspend fun updateNotificationAssistantEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.NOTIFICATION_ASSISTANT_ENABLED] = enabled
        }
    }

    suspend fun updateNotificationExcludedPackages(packages: Set<String>) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.NOTIFICATION_EXCLUDED_PACKAGES] = packages
        }
    }

    suspend fun updateRequireBiometricForHighRisk(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.REQUIRE_BIOMETRIC_HIGH_RISK] = enabled
        }
    }

    suspend fun updateMaxAutonomousSteps(steps: Int) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.MAX_AUTONOMOUS_STEPS] = steps.coerceIn(1, 100)
        }
    }

    suspend fun updateProactiveCompanionEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.PROACTIVE_COMPANION_ENABLED] = enabled
        }
    }

    suspend fun updateAmbientRoomTalkEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.AMBIENT_ROOM_TALK_ENABLED] = enabled
        }
    }

    suspend fun updateProactiveIntervalSeconds(seconds: Int) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.PROACTIVE_INTERVAL_SECONDS] = seconds.coerceIn(15, 180)
        }
    }

    private object Keys {
        val ASSISTANT_NAME = stringPreferencesKey("assistant_name")
        val LANGUAGE = stringPreferencesKey("language_preference")
        val TEXT_MODEL = stringPreferencesKey("text_model")
        val VOICE_MODEL = stringPreferencesKey("voice_model")
        val VOICE_GENDER = stringPreferencesKey("voice_gender")
        val PREFERRED_TTS_VOICE_GENDER = stringPreferencesKey("preferred_tts_voice_gender")
        val PREFERRED_TTS_VOICE_NAME = stringPreferencesKey("preferred_tts_voice_name")
        val VOICE_PERSONA = stringPreferencesKey("voice_persona")
        val VOICE_SPEED = floatPreferencesKey("voice_speed")
        val VOICE_PITCH = floatPreferencesKey("voice_pitch")
        val AUTO_SPEAK_CHAT = booleanPreferencesKey("auto_speak_chat_replies")
        val FLOATING_OVERLAY_ENABLED = booleanPreferencesKey("floating_overlay_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val THEME_DISPLAY_MODE = stringPreferencesKey("theme_display_mode")
        val MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
        val AUTO_INJECT_MEMORIES = booleanPreferencesKey("auto_inject_memories")
        val CONFIRM_CONSEQUENTIAL = booleanPreferencesKey("confirm_consequential")
        val PRIVACY_REDACT = booleanPreferencesKey("privacy_redact")
        val PERSONALITY_MODE = stringPreferencesKey("personality_mode")
        val CUSTOM_PERSONALITY_PROMPT = stringPreferencesKey("custom_personality_prompt")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val WAKE_WORD_SENSITIVITY = stringPreferencesKey("wake_word_sensitivity")
        val AUTO_MEMORY_SUGGESTIONS = booleanPreferencesKey("auto_memory_suggestions")
        val AUTO_MEMORY_RETRIEVAL = booleanPreferencesKey("auto_memory_retrieval")
        val MEMORY_EXPIRATION_DAYS = intPreferencesKey("memory_expiration_days")
        val NOTIFICATION_ASSISTANT_ENABLED = booleanPreferencesKey("notification_assistant_enabled")
        val NOTIFICATION_EXCLUDED_PACKAGES = stringSetPreferencesKey("notification_excluded_packages")
        val REQUIRE_BIOMETRIC_HIGH_RISK = booleanPreferencesKey("require_biometric_for_high_risk")
        val MAX_AUTONOMOUS_STEPS = intPreferencesKey("max_autonomous_steps")
        val PROACTIVE_COMPANION_ENABLED = booleanPreferencesKey("proactive_companion_enabled")
        val AMBIENT_ROOM_TALK_ENABLED = booleanPreferencesKey("ambient_room_talk_enabled")
        val PROACTIVE_INTERVAL_SECONDS = intPreferencesKey("proactive_interval_seconds")
    }
}
