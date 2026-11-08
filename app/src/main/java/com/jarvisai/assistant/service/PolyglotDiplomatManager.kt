package com.jarvisai.assistant.service

import com.jarvisai.assistant.data.network.GeminiApiService
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class TranslationUtterance(
    val id: String = UUID.randomUUID().toString(),
    val speakerRole: SpeakerRole,
    val sourceLanguage: String,
    val targetLanguage: String,
    val originalText: String,
    val translatedText: String,
    val phoneticGuide: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

enum class SpeakerRole {
    USER_PRIMARY,      // Bottom pane (Hindi / Hinglish / English)
    FOREIGN_DIPLOMAT   // Top inverted pane (Spanish, Japanese, French, Arabic, German, Mandarin, etc.)
}

data class PolyglotLanguage(
    val code: String,
    val name: String,
    val flag: String,
    val nativeLabel: String
)

class PolyglotDiplomatManager(
    private val geminiApiService: GeminiApiService,
    private val ttsManager: JarvisTtsManager
) {
    val supportedLanguages = listOf(
        PolyglotLanguage("hi-IN", "Hindi / Hinglish", "🇮🇳", "हिंदी / Hinglish"),
        PolyglotLanguage("en-US", "English", "🇺🇸", "English"),
        PolyglotLanguage("es-ES", "Spanish", "🇪🇸", "Español"),
        PolyglotLanguage("fr-FR", "French", "🇫🇷", "Français"),
        PolyglotLanguage("ja-JP", "Japanese", "🇯🇵", "日本語"),
        PolyglotLanguage("de-DE", "German", "🇩🇪", "Deutsch"),
        PolyglotLanguage("zh-CN", "Mandarin Chinese", "🇨🇳", "中文"),
        PolyglotLanguage("ar-AE", "Arabic", "🇦🇪", "العربية"),
        PolyglotLanguage("ru-RU", "Russian", "🇷🇺", "Русский"),
        PolyglotLanguage("ko-KR", "Korean", "🇰🇷", "한국어"),
        PolyglotLanguage("pt-BR", "Portuguese", "🇧🇷", "Português"),
        PolyglotLanguage("it-IT", "Italian", "🇮🇹", "Italiano"),
        PolyglotLanguage("tr-TR", "Turkish", "🇹🇷", "Türkçe"),
        PolyglotLanguage("th-TH", "Thai", "🇹🇭", "ไทย")
    )

    private val _userLanguage = MutableStateFlow(supportedLanguages[0]) // Hindi / Hinglish
    val userLanguage: StateFlow<PolyglotLanguage> = _userLanguage.asStateFlow()

    private val _foreignLanguage = MutableStateFlow(supportedLanguages[4]) // Japanese default
    val foreignLanguage: StateFlow<PolyglotLanguage> = _foreignLanguage.asStateFlow()

    private val _utterances = MutableStateFlow<List<TranslationUtterance>>(emptyList())
    val utterances: StateFlow<List<TranslationUtterance>> = _utterances.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    private val _activeListeningRole = MutableStateFlow<SpeakerRole?>(null)
    val activeListeningRole: StateFlow<SpeakerRole?> = _activeListeningRole.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setForeignLanguage(lang: PolyglotLanguage) {
        _foreignLanguage.value = lang
    }

    fun setUserLanguage(lang: PolyglotLanguage) {
        _userLanguage.value = lang
    }

    fun swapLanguages() {
        val currentTop = _foreignLanguage.value
        val currentBottom = _userLanguage.value
        _foreignLanguage.value = currentBottom
        _userLanguage.value = currentTop
    }

    fun setActiveListeningRole(role: SpeakerRole?) {
        _activeListeningRole.value = role
    }

    fun clearHistory() {
        _utterances.value = emptyList()
        _errorMessage.value = null
    }

    fun replaySpeech(text: String) {
        if (text.isNotBlank()) {
            ttsManager.speak(text)
        }
    }

    suspend fun translateAndSpeak(
        spokenText: String,
        role: SpeakerRole,
        apiKey: String,
        routerModelId: String = "gemini-3.5-flash-lite"
    ): Result<TranslationUtterance> = withContext(Dispatchers.IO) {
        if (spokenText.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Empty speech"))
        }
        if (apiKey.isBlank()) {
            val err = "Gemini API Key missing hai. Settings > AI Models & API Key me apni free key set karein."
            _errorMessage.value = err
            return@withContext Result.failure(IllegalStateException(err))
        }

        _isTranslating.value = true
        _errorMessage.value = null
        try {
            val sourceLang = if (role == SpeakerRole.USER_PRIMARY) _userLanguage.value else _foreignLanguage.value
            val targetLang = if (role == SpeakerRole.USER_PRIMARY) _foreignLanguage.value else _userLanguage.value

            val prompt = """
                You are JARVIS Polyglot Simultaneous Interpreter.
                Translate the following utterance from ${sourceLang.name} into natural, polite, native-sounding ${targetLang.name}.
                Input Utterance: "$spokenText"
                
                Respond strictly in this 2-line format (no markdown fences):
                TRANSLATION: <exact translated text in ${targetLang.name} script>
                PHONETIC: <romanized pronunciation guide so anyone can read it aloud>
            """.trimIndent()

            val rawResponse = geminiApiService.generateQuickResponse(
                apiKey = apiKey,
                model = routerModelId,
                prompt = prompt
            )

            val translated = Regex("TRANSLATION:\\s*(.+)", RegexOption.IGNORE_CASE)
                .find(rawResponse)?.groupValues?.get(1)?.trim()
                ?: rawResponse.lines().firstOrNull()?.trim().orEmpty()

            val phonetic = Regex("PHONETIC:\\s*(.+)", RegexOption.IGNORE_CASE)
                .find(rawResponse)?.groupValues?.get(1)?.trim().orEmpty()

            val utterance = TranslationUtterance(
                speakerRole = role,
                sourceLanguage = sourceLang.name,
                targetLanguage = targetLang.name,
                originalText = spokenText,
                translatedText = translated.ifBlank { rawResponse },
                phoneticGuide = phonetic
            )

            _utterances.value = _utterances.value + utterance
            ttsManager.speak(utterance.translatedText)

            Result.success(utterance)
        } catch (e: Exception) {
            val err = e.localizedMessage ?: "Translation failed."
            _errorMessage.value = err
            Result.failure(e)
        } finally {
            _isTranslating.value = false
        }
    }
}
