package com.jarvisai.assistant.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.JarvisThemeMode
import com.jarvisai.assistant.model.SupportedGeminiModels
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.model.VoiceGender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jarvis_user_preferences"
)

class UserPreferencesRepository(private val context: Context) {

    val userSettingsFlow: Flow<UserSettings> = context.jarvisDataStore.data.map { prefs ->
        val languageName = prefs[Keys.LANGUAGE] ?: SupportedLanguage.HINGLISH.name
        val language = SupportedLanguage.entries.find { it.name == languageName }
            ?: SupportedLanguage.HINGLISH

        val themeName = prefs[Keys.THEME_MODE] ?: JarvisThemeMode.ARC_CYAN.name
        val themeMode = JarvisThemeMode.entries.find { it.name == themeName }
            ?: JarvisThemeMode.ARC_CYAN

        val storedTextModel = prefs[Keys.TEXT_MODEL]
        val resolvedTextModel = when {
            storedTextModel.isNullOrBlank() -> SupportedGeminiModels.DEFAULT_TEXT_MODEL
            storedTextModel == "gemini-2.5-flash" -> SupportedGeminiModels.DEFAULT_TEXT_MODEL
            else -> storedTextModel
        }

        val storedVoiceModel = prefs[Keys.VOICE_MODEL]
        val resolvedVoiceModel = when {
            storedVoiceModel.isNullOrBlank() -> SupportedGeminiModels.DEFAULT_VOICE_MODEL
            storedVoiceModel == "gemini-2.0-flash-exp" -> SupportedGeminiModels.DEFAULT_VOICE_MODEL
            else -> storedVoiceModel
        }

        val storedPersona = prefs[Keys.VOICE_PERSONA] ?: "Puck"
        val storedGenderName = prefs[Keys.VOICE_GENDER]
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
        val resolvedPersona = SupportedGeminiModels.findVoiceOption(storedPersona, resolvedGender).id

        UserSettings(
            assistantName = prefs[Keys.ASSISTANT_NAME]?.ifBlank { "JARVIS" } ?: "JARVIS",
            language = language,
            textModel = resolvedTextModel,
            voiceModel = resolvedVoiceModel,
            voiceGender = resolvedGender,
            voicePersona = resolvedPersona,
            voiceSpeed = prefs[Keys.VOICE_SPEED] ?: 1.0f,
            voicePitch = prefs[Keys.VOICE_PITCH] ?: 1.0f,
            autoSpeakChatReplies = false,
            floatingOverlayEnabled = prefs[Keys.FLOATING_OVERLAY_ENABLED] ?: true,
            themeMode = themeMode,
            memoryEnabled = prefs[Keys.MEMORY_ENABLED] ?: true,
            autoInjectMemories = prefs[Keys.AUTO_INJECT_MEMORIES] ?: true,
            confirmConsequentialActions = prefs[Keys.CONFIRM_CONSEQUENTIAL] ?: true,
            privacyRedactTelemetry = prefs[Keys.PRIVACY_REDACT] ?: true
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
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.VOICE_GENDER] = gender.name
            val currentPersona = prefs[Keys.VOICE_PERSONA] ?: "Puck"
            val matchingOption = SupportedGeminiModels.voiceOptions.find {
                it.id.equals(currentPersona, ignoreCase = true) && it.gender == gender
            }
            if (matchingOption == null) {
                prefs[Keys.VOICE_PERSONA] = gender.defaultGeminiPersona
            }
        }
    }

    suspend fun updateVoicePersona(persona: String) {
        val matched = SupportedGeminiModels.voiceOptions.find { it.id.equals(persona, ignoreCase = true) }
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.VOICE_PERSONA] = persona
            if (matched != null) {
                prefs[Keys.VOICE_GENDER] = matched.gender.name
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

    private object Keys {
        val ASSISTANT_NAME = stringPreferencesKey("assistant_name")
        val LANGUAGE = stringPreferencesKey("language_preference")
        val TEXT_MODEL = stringPreferencesKey("text_model")
        val VOICE_MODEL = stringPreferencesKey("voice_model")
        val VOICE_GENDER = stringPreferencesKey("voice_gender")
        val VOICE_PERSONA = stringPreferencesKey("voice_persona")
        val VOICE_SPEED = floatPreferencesKey("voice_speed")
        val VOICE_PITCH = floatPreferencesKey("voice_pitch")
        val AUTO_SPEAK_CHAT = booleanPreferencesKey("auto_speak_chat_replies")
        val FLOATING_OVERLAY_ENABLED = booleanPreferencesKey("floating_overlay_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
        val AUTO_INJECT_MEMORIES = booleanPreferencesKey("auto_inject_memories")
        val CONFIRM_CONSEQUENTIAL = booleanPreferencesKey("confirm_consequential")
        val PRIVACY_REDACT = booleanPreferencesKey("privacy_redact")
    }
}
