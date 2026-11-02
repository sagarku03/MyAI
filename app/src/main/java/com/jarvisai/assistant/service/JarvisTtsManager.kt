package com.jarvisai.assistant.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.jarvisai.assistant.model.SupportedGeminiModels
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.VoiceGender
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dedicated Android TextToSpeech manager for JARVIS AI.
 *
 * Responsibilities:
 * - Automatically speaks every normal chat AI reply aloud (unless muted/disabled).
 * - Never speaks the user's own messages.
 * - Supports Male and Female voice selection by matching installed Android TTS voices
 *   (`hi-IN`, `en-IN`, `en-US`) and tuning voice pitch/timbre characteristics per persona.
 * - Supports Hindi, Hinglish, and English pronunciation naturally by stripping Markdown
 *   formatting symbols before speech synthesis.
 * - Provides a Stop Speaking control and prevents overlapping audio playback.
 */
class JarvisTtsManager(context: Context) {

    private val appContext = context.applicationContext

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _voiceStatusError = MutableStateFlow<String?>(null)
    val voiceStatusError: StateFlow<String?> = _voiceStatusError.asStateFlow()

    init {
        initEngine()
    }

    private fun initEngine() {
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    _voiceStatusError.value = null
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                        }

                        override fun onStop(utteranceId: String?, interrupted: Boolean) {
                            _isSpeaking.value = false
                        }
                    })
                } else {
                    isInitialized = false
                    _voiceStatusError.value = "Device Text-to-Speech engine initialize nahi ho paya."
                    Log.e(TAG, "TTS initialization failed with status=$status")
                }
            }
        } catch (e: Exception) {
            isInitialized = false
            _voiceStatusError.value = "TTS engine error: ${e.localizedMessage ?: "Unavailable"}"
        }
    }

    /**
     * Speaks an AI response or voice preview aloud using the selected Male/Female voice and persona.
     * Automatically flushes any ongoing speech to prevent overlapping audio.
     */
    fun speak(
        rawText: String,
        language: SupportedLanguage,
        voiceGender: VoiceGender,
        voicePersona: String,
        speed: Float = 1.0f,
        pitch: Float = 1.0f
    ): Boolean {
        val cleanText = sanitizeTextForSpeech(rawText)
        if (cleanText.isBlank()) return false

        val engine = tts
        if (!isInitialized || engine == null) {
            _voiceStatusError.value = "Voice engine abhi ready nahi hai. Kripya ek second baad try karein."
            initEngine()
            return false
        }

        return try {
            engine.stop()

            val targetLocale = resolveBestLocale(rawText, language)
            val langResult = engine.setLanguage(targetLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.forLanguageTag("en-IN"))
            }

            // Select matching Male or Female Android TTS Voice if available on device
            val selectedAndroidVoice = findMatchingAndroidVoice(engine, targetLocale, voiceGender, voicePersona)
            if (selectedAndroidVoice != null) {
                engine.voice = selectedAndroidVoice
            }

            // Apply natural persona pitch & speed so Male and Female voices are clearly distinct
            val personaBasePitch = when (voicePersona.lowercase()) {
                "charon" -> 0.84f
                "fenrir" -> 0.88f
                "puck" -> 0.95f
                "kore" -> 1.12f
                "aoede" -> 1.18f
                else -> if (voiceGender == VoiceGender.FEMALE) 1.14f else 0.92f
            }
            val finalPitch = (personaBasePitch * pitch).coerceIn(0.6f, 1.8f)
            val finalSpeed = speed.coerceIn(0.6f, 1.8f)

            engine.setPitch(finalPitch)
            engine.setSpeechRate(finalSpeed)

            val utteranceId = "jarvis_tts_${UUID.randomUUID()}"
            _isSpeaking.value = true
            _voiceStatusError.value = null
            val speakResult = engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (speakResult == TextToSpeech.ERROR) {
                _isSpeaking.value = false
                _voiceStatusError.value = "Selected voice par audio play karne mein problem aayi."
                false
            } else {
                true
            }
        } catch (e: Exception) {
            _isSpeaking.value = false
            _voiceStatusError.value = "Voice playback error: ${e.localizedMessage ?: "Unknown"}"
            false
        }
    }

    fun previewVoice(
        voiceGender: VoiceGender,
        voicePersona: String,
        language: SupportedLanguage,
        speed: Float,
        pitch: Float
    ): Boolean {
        val option = SupportedGeminiModels.findVoiceOption(voicePersona, voiceGender)
        return speak(
            rawText = option.previewSampleHinglish,
            language = language,
            voiceGender = option.gender,
            voicePersona = option.id,
            speed = speed,
            pitch = pitch
        )
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        } finally {
            _isSpeaking.value = false
        }
    }

    private fun resolveBestLocale(text: String, language: SupportedLanguage): Locale {
        val containsDevanagari = text.any { it in '\u0900'..'\u097F' }
        return when {
            containsDevanagari || language == SupportedLanguage.HINDI -> Locale.forLanguageTag("hi-IN")
            language == SupportedLanguage.HINGLISH -> Locale.forLanguageTag("en-IN")
            else -> Locale.US
        }
    }

    private fun findMatchingAndroidVoice(
        engine: TextToSpeech,
        targetLocale: Locale,
        gender: VoiceGender,
        persona: String
    ): Voice? {
        val availableVoices = try {
            engine.voices?.filterNotNull().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        if (availableVoices.isEmpty()) return null

        val targetLang = targetLocale.language.lowercase()
        val targetCountry = targetLocale.country.lowercase()

        // Prefer Indian / Hindi / US voices that are installed locally (not requiring network or already available)
        val localeCandidates = availableVoices.filter { v ->
            val vLang = v.locale?.language?.lowercase().orEmpty()
            val vCountry = v.locale?.country?.lowercase().orEmpty()
            (vLang == targetLang && vCountry == targetCountry) ||
                (vLang == "hi" && vCountry == "in") ||
                (vLang == "en" && vCountry == "in") ||
                (vLang == targetLang)
        }.ifEmpty { availableVoices }

        val genderKeywords = if (gender == VoiceGender.FEMALE) {
            listOf("female", "#female", "wavenet-a", "wavenet-d", "Standard-A", "Standard-D", "sfg", "tpf", "hia", "hid", "ena", "end")
        } else {
            listOf("male", "#male", "wavenet-b", "wavenet-c", "Standard-B", "Standard-C", "sfm", "tpm", "hib", "hic", "enb", "enc")
        }

        val oppositeKeywords = if (gender == VoiceGender.FEMALE) {
            listOf("#male", "_male", "-male")
        } else {
            listOf("#female", "_female", "-female")
        }

        val exactLocaleAndGender = localeCandidates.firstOrNull { v ->
            val nameLower = v.name.lowercase()
            val featuresLower = v.features?.joinToString(" ")?.lowercase().orEmpty()
            val combined = "$nameLower $featuresLower"
            v.locale?.language?.equals(targetLang, ignoreCase = true) == true &&
                genderKeywords.any { combined.contains(it.lowercase()) } &&
                oppositeKeywords.none { combined.contains(it.lowercase()) }
        }
        if (exactLocaleAndGender != null) return exactLocaleAndGender

        val anyGenderMatch = localeCandidates.firstOrNull { v ->
            val combined = "${v.name.lowercase()} ${v.features?.joinToString(" ")?.lowercase().orEmpty()}"
            genderKeywords.any { combined.contains(it.lowercase()) } &&
                oppositeKeywords.none { combined.contains(it.lowercase()) }
        }
        if (anyGenderMatch != null) return anyGenderMatch

        // Deterministic fallback among locale voices so different personas pick distinct voices if multiple exist
        val sortedLocaleVoices = localeCandidates
            .filter { v -> v.locale?.language?.equals(targetLang, ignoreCase = true) == true }
            .sortedBy { it.name }
        if (sortedLocaleVoices.size >= 2) {
            return if (gender == VoiceGender.FEMALE) {
                sortedLocaleVoices.first()
            } else {
                sortedLocaleVoices.last()
            }
        }
        return sortedLocaleVoices.firstOrNull()
    }

    private fun sanitizeTextForSpeech(raw: String): String {
        return raw
            .replace(Regex("```[\\s\\S]*?```"), " code block ")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("[*_#~>|]+"), " ")
            .replace(Regex("\\[(.*?)]\\(.*?\\)"), "$1")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        isInitialized = false
        _isSpeaking.value = false
    }

    companion object {
        private const val TAG = "JarvisTtsManager"
    }
}
