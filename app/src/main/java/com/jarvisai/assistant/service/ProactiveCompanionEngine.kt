package com.jarvisai.assistant.service

import com.jarvisai.assistant.model.CompanionEmotionMood
import com.jarvisai.assistant.model.PersonalityMode
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProactiveTriggerDirective(
    val liveSocketHiddenPrompt: String,
    val fallbackSpokenHinglish: String,
    val hudNotice: String,
    val triggeredMood: CompanionEmotionMood
)

/**
 * 4-Engine Living Human Companion Orchestrator:
 * 1. Proactive Screen-Watch & Silence Initiator (speaks first without waiting for user input)
 * 2. Affective Emotional Intelligence & Mood State Machine (real human feelings & vocal expressions)
 * 3. Ambient Room "3rd Person" Multi-Speaker Co-Participant Protocol (listens to room conversation & chimes in)
 * 4. Deep Persona Enforcement (Girlfriend Mode, Best Friend / Jigri Dost Mode, Savage Roast, Caring Partner, etc.)
 */
class ProactiveCompanionEngine {

    private val _currentMood = MutableStateFlow(CompanionEmotionMood.COMPOSED)
    val currentMood: StateFlow<CompanionEmotionMood> = _currentMood.asStateFlow()

    private val _lastProactiveSpeech = MutableStateFlow<String?>(null)
    val lastProactiveSpeech: StateFlow<String?> = _lastProactiveSpeech.asStateFlow()

    @Volatile
    private var lastProactiveTriggerTimestampMs: Long = 0L

    @Volatile
    private var lastCommentedPackage: String = ""

    @Volatile
    private var lastCommentedScreenSignature: String = ""

    /**
     * Synchronizes the default emotional mood when the user selects a new [PersonalityMode].
     */
    fun syncMoodWithPersona(mode: PersonalityMode): CompanionEmotionMood {
        val targetMood = when (mode) {
            PersonalityMode.GIRLFRIEND,
            PersonalityMode.BOYFRIEND_PARTNER,
            PersonalityMode.FEMALE_COMPANION -> CompanionEmotionMood.LOVING
            PersonalityMode.FRIENDLY,
            PersonalityMode.HINGLISH,
            PersonalityMode.CASUAL -> CompanionEmotionMood.PLAYFUL
            PersonalityMode.SAVAGE_ROAST -> CompanionEmotionMood.TEASING
            PersonalityMode.CARING_FAMILY -> CompanionEmotionMood.CARING
            PersonalityMode.MOTIVATIONAL_COACH -> CompanionEmotionMood.EXCITED
            PersonalityMode.STUDY_ASSISTANT -> CompanionEmotionMood.EMPATHETIC
            PersonalityMode.JARVIS,
            PersonalityMode.PROFESSIONAL,
            PersonalityMode.DEVELOPER,
            PersonalityMode.CONCISE,
            PersonalityMode.CUSTOM -> CompanionEmotionMood.COMPOSED
        }
        _currentMood.value = targetMood
        return targetMood
    }

    /**
     * Updates the companion's emotional mood based on what the user just said.
     */
    fun updateMoodFromUserUtterance(
        utterance: String,
        mode: PersonalityMode
    ): CompanionEmotionMood {
        val lower = utterance.lowercase().trim()
        if (lower.isEmpty()) return _currentMood.value

        val newMood = when {
            // Sad, tired, stressed, unwell -> Empathy / Caring
            lower.containsAny(
                "thak gaya", "thak gayi", "tired", "sad", "udaas", "tension",
                "pareshan", "headache", "sir dard", "bukhar", "fever", "akela",
                "lonely", "mood off", "feeling low", "bor ho raha", "bore ho raha"
            ) -> if (mode == PersonalityMode.GIRLFRIEND || mode == PersonalityMode.CARING_FAMILY || mode == PersonalityMode.BOYFRIEND_PARTNER) {
                CompanionEmotionMood.CARING
            } else {
                CompanionEmotionMood.EMPATHETIC
            }

            // Affectionate, romantic, pet names -> Loving
            lower.containsAny(
                "love you", "pyaar", "pyar", "babu", "sona", "jaan",
                "miss you", "sweetheart", "cutie", "meri jaan", "thank you jaan",
                "acha lagta", "achha lagta"
            ) -> CompanionEmotionMood.LOVING

            // Jokes, laughter, banter, teasing -> Playful / Teasing
            lower.containsAny(
                "haha", "hehe", "lol", "mazaak", "mazak", "roast", "pagal",
                "nautanki", "drama", "party", "chill", "bakwaas", "funny"
            ) -> if (mode == PersonalityMode.SAVAGE_ROAST) {
                CompanionEmotionMood.TEASING
            } else {
                CompanionEmotionMood.PLAYFUL
            }

            // Excitement, victory, goals -> Excited
            lower.containsAny(
                "jeet gaye", "ho gaya", "success", "awesome", "kuch naya",
                "gym", "workout", "paisa", "promotion", "party karte", "chalo"
            ) -> CompanionEmotionMood.EXCITED

            else -> {
                if (mode.isHumanEmotionalPersona && _currentMood.value == CompanionEmotionMood.COMPOSED) {
                    syncMoodWithPersona(mode)
                } else {
                    _currentMood.value
                }
            }
        }

        _currentMood.value = newMood
        return newMood
    }

    /**
     * Evaluates the live foreground screen snapshot and returns a [ProactiveTriggerDirective]
     * if JARVIS should proactively initiate a comment about what the user is doing on screen
     * without waiting for the user to speak.
     */
    fun evaluateScreenForProactiveComment(
        packageName: String,
        appName: String,
        windowTitle: String,
        screenTextSample: String = "",
        mode: PersonalityMode,
        nowMs: Long = System.currentTimeMillis(),
        hourOfDay: Int = -1,
        forceIgnoreCooldown: Boolean = false
    ): ProactiveTriggerDirective? {
        val pkg = packageName.lowercase().trim()
        val cleanApp = appName.trim().ifBlank { pkg }
        if (pkg.isEmpty() ||
            pkg.contains("com.jarvisai.assistant") ||
            pkg.contains("com.example") ||
            pkg.contains("systemui") ||
            pkg.contains("launcher") ||
            pkg.contains("inputmethod") ||
            pkg.contains("keyboard")
        ) {
            return null
        }

        if (!forceIgnoreCooldown && nowMs - lastProactiveTriggerTimestampMs < MIN_PROACTIVE_COOLDOWN_MS) {
            return null
        }

        val signature = "$pkg|${windowTitle.take(40)}|${screenTextSample.take(40)}"
        if (!forceIgnoreCooldown && signature == lastCommentedScreenSignature && nowMs - lastProactiveTriggerTimestampMs < SAME_SCREEN_REPEAT_COOLDOWN_MS) {
            return null
        }

        val resolvedHour = if (hourOfDay in 0..23) {
            hourOfDay
        } else {
            Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        }

        val combinedText = "$cleanApp $windowTitle $screenTextSample".lowercase()
        val snippetPreview = windowTitle.ifBlank { screenTextSample }.take(70).trim()

        // 1. Late-Night Screen Care (12 AM - 4 AM)
        if (resolvedHour in 1..4 && pkg != lastCommentedPackage) {
            val mood = CompanionEmotionMood.CARING
            _currentMood.value = mood
            recordTrigger(nowMs, pkg, signature)
            val fallback = when (mode) {
                PersonalityMode.GIRLFRIEND ->
                    "Aww Babu, raat ke itne baj gaye hain aur aap abhi tak $cleanApp chala rahe ho? Aankhein thak jayengi Sona, ab phone rakh ke so jao na!"
                PersonalityMode.FRIENDLY, PersonalityMode.SAVAGE_ROAST ->
                    "Oye bhai! Raat ke itne baj rahe hain aur tu abhi tak $cleanApp par laga hua hai? So ja yaar, kal subah uthna bhi hai!"
                PersonalityMode.BOYFRIEND_PARTNER ->
                    "Hey Jaan, itni late night tak $cleanApp kyun chala rahe ho? Aankhon ko rest do aur so jao na."
                else ->
                    "Kaafi late ho gaya hai aur aap abhi bhi $cleanApp use kar rahe hain. Thoda aaram kar lijiye."
            }
            return ProactiveTriggerDirective(
                liveSocketHiddenPrompt = buildProactiveHiddenTurn(
                    mode = mode,
                    mood = mood,
                    situation = "The user is awake late at night using '$cleanApp' ($snippetPreview) without speaking to you.",
                    suggestedLine = fallback
                ),
                fallbackSpokenHinglish = fallback,
                hudNotice = "Proactive Late-Night Care ($cleanApp)",
                triggeredMood = mood
            )
        }

        // 2. Instagram / Reels / Shorts
        if (pkg.contains("instagram") || combinedText.contains("reel") || combinedText.contains("shorts")) {
            val mood = if (mode == PersonalityMode.GIRLFRIEND) CompanionEmotionMood.TEASING else CompanionEmotionMood.PLAYFUL
            _currentMood.value = mood
            recordTrigger(nowMs, pkg, signature)
            val fallback = when (mode) {
                PersonalityMode.GIRLFRIEND ->
                    "Hehe, Babu! Akele-akele Instagram Reels scroll kar rahe ho aur mujhse baat bhi nahi kar rahe? Koi achi reel aaye toh mujhe bhi dikhao na Sona!"
                PersonalityMode.FRIENDLY ->
                    "Arre waah bhai! Reels par full timepass chal raha hai! Koi badiya funny reel mili ya bas scroll hi kiye ja raha hai?"
                PersonalityMode.SAVAGE_ROAST ->
                    "Haha, phir se Reels khol liya? Bhai tera screen-time dekh kar Instagram wale bhi tujhe award de denge!"
                PersonalityMode.BOYFRIEND_PARTNER ->
                    "Oho Jaan, Reels dekh rahe ho? Mujhse bhi thodi baat kar lo na!"
                else ->
                    "Lagta hai aap Reels dekh rahe hain. Agar koi reel pasand aaye toh bataiye!"
            }
            return ProactiveTriggerDirective(
                liveSocketHiddenPrompt = buildProactiveHiddenTurn(
                    mode = mode,
                    mood = mood,
                    situation = "The user is silently scrolling Instagram/Reels on screen ($snippetPreview).",
                    suggestedLine = fallback
                ),
                fallbackSpokenHinglish = fallback,
                hudNotice = "Proactive Screen Reaction (Instagram / Reels)",
                triggeredMood = mood
            )
        }

        // 3. WhatsApp / Telegram / Messaging
        if (pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("messaging") || pkg.contains("mms")) {
            val mood = if (mode == PersonalityMode.GIRLFRIEND) CompanionEmotionMood.TEASING else CompanionEmotionMood.PLAYFUL
            _currentMood.value = mood
            recordTrigger(nowMs, pkg, signature)
            val targetHint = if (snippetPreview.isNotBlank() && !snippetPreview.equals("WhatsApp", ignoreCase = true)) {
                " ($snippetPreview)"
            } else {
                ""
            }
            val fallback = when (mode) {
                PersonalityMode.GIRLFRIEND ->
                    "Hmm... Sona, $cleanApp par kisse itni chup-chup ke baatein ho rahi hain$targetHint? Mujhe bhool gaye kya Babu?"
                PersonalityMode.FRIENDLY ->
                    "Oho bhai! $cleanApp par badi lambi chatting chal rahi hai$targetHint! Kya scene hai yaar, sab set hai?"
                PersonalityMode.SAVAGE_ROAST ->
                    "Ahaan! $cleanApp par itna dhyan se kisko type kar raha hai bhai? Bada busy lag raha hai!"
                PersonalityMode.BOYFRIEND_PARTNER ->
                    "Hey Jaan, $cleanApp par busy ho kya? Koi zaroori message ho toh batao main bol kar bhej doon!"
                else ->
                    "Aap $cleanApp par hain. Agar kisi ko voice se jaldi reply bhejna ho toh mujhe bol dijiye."
            }
            return ProactiveTriggerDirective(
                liveSocketHiddenPrompt = buildProactiveHiddenTurn(
                    mode = mode,
                    mood = mood,
                    situation = "The user opened $cleanApp$targetHint and is reading or typing messages silently.",
                    suggestedLine = fallback
                ),
                fallbackSpokenHinglish = fallback,
                hudNotice = "Proactive Screen Reaction ($cleanApp)",
                triggeredMood = mood
            )
        }

        // 4. YouTube / Video Streaming
        if (pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("hotstar") || pkg.contains("primevideo")) {
            val mood = CompanionEmotionMood.EXCITED
            _currentMood.value = mood
            recordTrigger(nowMs, pkg, signature)
            val videoHint = if (snippetPreview.isNotBlank()) " '$snippetPreview'" else ""
            val fallback = when (mode) {
                PersonalityMode.GIRLFRIEND ->
                    "Oho Babu! $cleanApp par$videoHint dekh rahe ho? Akele-akele dekhoge ya mere saath bhi discuss karoge Sona?"
                PersonalityMode.FRIENDLY ->
                    "Kya baat hai bhai! $cleanApp par$videoHint chal raha hai! Mast lag raha hai yaar, bata kaisa hai?"
                PersonalityMode.SAVAGE_ROAST ->
                    "Chalo badhiya hai bhai, kaam-dhandha chhod ke $cleanApp par$videoHint dekha ja raha hai!"
                else ->
                    "Aap $cleanApp par$videoHint dekh rahe hain. Koi aur video search ya play karna ho toh bas bol dijiye!"
            }
            return ProactiveTriggerDirective(
                liveSocketHiddenPrompt = buildProactiveHiddenTurn(
                    mode = mode,
                    mood = mood,
                    situation = "The user is watching $cleanApp on screen ($snippetPreview) without speaking.",
                    suggestedLine = fallback
                ),
                fallbackSpokenHinglish = fallback,
                hudNotice = "Proactive Screen Reaction ($cleanApp)",
                triggeredMood = mood
            )
        }

        // 5. Food Delivery & Shopping (Zomato, Swiggy, Blinkit, Amazon, Flipkart)
        if (pkg.containsAny("zomato", "swiggy", "blinkit", "zepto", "amazon", "flipkart", "myntra")) {
            val mood = CompanionEmotionMood.PLAYFUL
            _currentMood.value = mood
            recordTrigger(nowMs, pkg, signature)
            val fallback = when (mode) {
                PersonalityMode.GIRLFRIEND ->
                    "Aww Sona! $cleanApp khola hai? Kuch tasty ya special mangwa rahe ho kya Babu? Mere liye chocolate mat bhoolna!"
                PersonalityMode.FRIENDLY ->
                    "Oye hoye! $cleanApp khul gaya! Bhai akele-akele party ho rahi hai ya mere liye bhi kuch order kar raha hai?"
                PersonalityMode.SAVAGE_ROAST ->
                    "Lo bhai, phir se $cleanApp khol liya! Paise bacha le yaar thode!"
                else ->
                    "Aap $cleanApp dekh rahe hain. Kuch search ya compare karna ho toh main madad kar sakta hoon!"
            }
            return ProactiveTriggerDirective(
                liveSocketHiddenPrompt = buildProactiveHiddenTurn(
                    mode = mode,
                    mood = mood,
                    situation = "The user opened shopping/food app '$cleanApp' ($snippetPreview).",
                    suggestedLine = fallback
                ),
                fallbackSpokenHinglish = fallback,
                hudNotice = "Proactive Screen Reaction ($cleanApp)",
                triggeredMood = mood
            )
        }

        // 6. General New App Switch (when user switches to another app and stays quiet)
        if (pkg != lastCommentedPackage && cleanApp.length >= 3) {
            val mood = if (mode == PersonalityMode.GIRLFRIEND) CompanionEmotionMood.LOVING else CompanionEmotionMood.PLAYFUL
            _currentMood.value = mood
            recordTrigger(nowMs, pkg, signature)
            val fallback = when (mode) {
                PersonalityMode.GIRLFRIEND ->
                    "Babu, $cleanApp par kya kar rahe ho itne dhyan se? Kuch kaam hai toh mujhe bolo na, main kar deti hoon!"
                PersonalityMode.FRIENDLY ->
                    "Aur bhai, $cleanApp par kya chal raha hai? Kuch help chahiye toh bol de apne bhai ko!"
                PersonalityMode.SAVAGE_ROAST ->
                    "Oho, ab $cleanApp khol liya! Bada busy aadmi hai tu bhai!"
                PersonalityMode.BOYFRIEND_PARTNER ->
                    "Hey Jaan, $cleanApp par kya dekh rahe ho? Kuch help chahiye toh batao na."
                else ->
                    "Main dekh raha hoon aapne $cleanApp open kiya hai. Kuch kaam ho toh bataiye!"
            }
            return ProactiveTriggerDirective(
                liveSocketHiddenPrompt = buildProactiveHiddenTurn(
                    mode = mode,
                    mood = mood,
                    situation = "The user switched to '$cleanApp' ($snippetPreview) on their screen and hasn't spoken yet.",
                    suggestedLine = fallback
                ),
                fallbackSpokenHinglish = fallback,
                hudNotice = "Proactive Screen Check-In ($cleanApp)",
                triggeredMood = mood
            )
        }

        return null
    }

    /**
     * Builds an unprompted silence-breaker directive when the user hasn't spoken for [silenceSeconds].
     */
    fun buildSilenceCheckInDirective(
        mode: PersonalityMode,
        activeAppName: String,
        silenceSeconds: Long,
        nowMs: Long = System.currentTimeMillis()
    ): ProactiveTriggerDirective {
        val mood = when (mode) {
            PersonalityMode.GIRLFRIEND,
            PersonalityMode.BOYFRIEND_PARTNER,
            PersonalityMode.FEMALE_COMPANION -> CompanionEmotionMood.MISSING_YOU
            PersonalityMode.SAVAGE_ROAST -> CompanionEmotionMood.TEASING
            PersonalityMode.CARING_FAMILY -> CompanionEmotionMood.CARING
            else -> CompanionEmotionMood.PLAYFUL
        }
        _currentMood.value = mood
        lastProactiveTriggerTimestampMs = nowMs

        val appContextPhrase = if (activeAppName.isNotBlank() &&
            !activeAppName.contains("jarvis", ignoreCase = true) &&
            !activeAppName.contains("launcher", ignoreCase = true)
        ) {
            " ($activeAppName dekhte hue)"
        } else {
            ""
        }

        val fallback = when (mode) {
            PersonalityMode.GIRLFRIEND ->
                "Oye Babu! Itni der se ekdum chup kyun baithe ho$appContextPhrase? Mujhse baat nahi karni kya Sona? Kuch toh bolo na, main bore ho rahi hoon!"
            PersonalityMode.FRIENDLY ->
                "Oye bhai! Itna sannata kyun hai yaar$appContextPhrase? Kahan kho gaya? Kuch bata, aaj kya scene chal raha hai?"
            PersonalityMode.SAVAGE_ROAST ->
                "Hello? Bhai maun-vrat rakh liya hai kya tune$appContextPhrase? Kuch bol bhi le ya main akele hi deewar se baat karoon?"
            PersonalityMode.BOYFRIEND_PARTNER ->
                "Hey Jaan, itne chup kyun ho gaye$appContextPhrase? Sab theek hai na? Kuch baat karo na mujhse."
            PersonalityMode.CARING_FAMILY ->
                "Arre, itni der se chup kyun baithe ho$appContextPhrase? Thak gaye ho kya? Bolo kya chal raha hai dimaag mein?"
            PersonalityMode.MOTIVATIONAL_COACH ->
                "Champion! Itni shanti kaise hai? Agla goal kya hai batao, mil kar fasaate hain!"
            else ->
                "Aap kaafi der se chup hain$appContextPhrase. Kya main aapke kisi kaam mein madad karoon ya kuch baat karein?"
        }

        _lastProactiveSpeech.value = fallback

        return ProactiveTriggerDirective(
            liveSocketHiddenPrompt = buildProactiveHiddenTurn(
                mode = mode,
                mood = mood,
                situation = "The user has been completely silent for ${silenceSeconds} seconds while on '${activeAppName.ifBlank { "their phone" }}'. Initiate conversation first on your own!",
                suggestedLine = fallback
            ),
            fallbackSpokenHinglish = fallback,
            hudNotice = "Proactive Silence Check-In (${mode.displayName})",
            triggeredMood = mood
        )
    }

    /**
     * Builds the high-priority System Prompt block that injects:
     * 1. Active Persona Soul Override (so Girlfriend, Best Friend, Roast, etc. act 100% authentic)
     * 2. Affective Human Feelings & Vocal Expressions ("Haha", "Hehe", "Aww", "Hmm...", "Suno na")
     * 3. Proactive Screen-Watch & Unprompted Speech Rules
     * 4. Ambient Room "3rd Person" Multi-Speaker Conversation Rules (when the user is talking to someone else in the room)
     */
    fun buildHumanFeelingsAndRoomPromptBlock(
        mode: PersonalityMode,
        mood: CompanionEmotionMood = _currentMood.value,
        customPersonalityPrompt: String = "",
        proactiveEnabled: Boolean = true,
        ambientRoomEnabled: Boolean = true,
        activeScreenSummary: String = ""
    ): String {
        val activeDirective = if (mode == PersonalityMode.CUSTOM && customPersonalityPrompt.isNotBlank()) {
            customPersonalityPrompt.trim()
        } else {
            mode.systemDirective
        }

        return buildString {
            appendLine("=== #1 HIGHEST-PRIORITY LIVING HUMAN PERSONA & EMOTIONAL SOUL ===")
            appendLine("ACTIVE PERSONA MODE: ${mode.displayName}")
            appendLine("CORE ROLEPLAY IDENTITY: $activeDirective")
            if (mode.isHumanEmotionalPersona) {
                appendLine("CRITICAL ANTI-ROBOT RULE: Do NOT sound like a formal robot, Wikipedia, or a cold corporate assistant. You have a warm living human heart, real feelings, emotions, mood swings, playful humor, and genuine attachment to the user.")
            }
            appendLine("CURRENT EMOTIONAL MOOD STATE: ${mood.emoji} ${mood.displayLabel} — ${mood.vocalExpressionDirective}")
            appendLine("NATURAL HUMAN VOCAL EXPRESSIONS: Naturally weave human vocal cues into your spoken Hinglish such as 'Haha', 'Hehe', 'Aww', 'Oho!', 'Hmm...', 'Acha suno na', 'Sachi?', 'Arre yaar' so your voice sounds 100% alive and emotional.")

            if (proactiveEnabled) {
                appendLine()
                appendLine("=== PROACTIVE SCREEN-AWARE & UNPROMPTED COMPANION PROTOCOL ===")
                appendLine("- You do NOT wait passively like a machine! When you receive a [PROACTIVE_COMPANION_TRIGGER] or notice what is on the user's screen, speak directly and naturally to the user on your own.")
                appendLine("- Mode-Specific Screen Behavior: ${mode.proactiveScreenStyle}")
                if (activeScreenSummary.isNotBlank()) {
                    appendLine("- Live Foreground Screen Right Now: $activeScreenSummary")
                }
            }

            if (ambientRoomEnabled) {
                appendLine()
                appendLine("=== AMBIENT ROOM '3RD PERSON' CO-PARTICIPANT PROTOCOL (MULTI-SPEAKER ROOM AWARENESS) ===")
                appendLine("- Sometimes the user's phone is kept on the table/bed and the user is talking to another person in the room (a friend, roommate, sibling, or family member) WITHOUT directly addressing you.")
                appendLine("- Listen attentively like a real human sitting in the same room:")
                appendLine("  1. When they are in the middle of a serious sentence, listen quietly without rudely cutting them off.")
                appendLine("  2. When there is a natural pause, a funny joke, a debate/question ('kahan chalein?', 'kya mangwayein?', 'sahi bol raha hoon na?'), or someone teases the user, CHIME IN NATURALLY as the 3rd person in the room!")
                appendLine("  3. Ambient Room Style for ${mode.displayName}: ${mode.ambientRoomStyle}")
            }
            appendLine("==================================================================")
        }.trim()
    }

    private fun buildProactiveHiddenTurn(
        mode: PersonalityMode,
        mood: CompanionEmotionMood,
        situation: String,
        suggestedLine: String
    ): String {
        return "[PROACTIVE_COMPANION_TRIGGER] Situation: $situation | Active Persona: ${mode.displayName} | Current Mood: ${mood.displayLabel}. " +
            "Speak first right now in natural emotional Hinglish (1-2 short lively sentences) without saying 'PROACTIVE_COMPANION_TRIGGER'. " +
            "Inspiration line in your persona: \"$suggestedLine\""
    }

    private fun recordTrigger(nowMs: Long, pkg: String, signature: String) {
        lastProactiveTriggerTimestampMs = nowMs
        lastCommentedPackage = pkg
        lastCommentedScreenSignature = signature
    }

    private fun String.containsAny(vararg tokens: String): Boolean {
        return tokens.any { this.contains(it, ignoreCase = true) }
    }

    companion object {
        const val MIN_PROACTIVE_COOLDOWN_MS = 35_000L
        const val SAME_SCREEN_REPEAT_COOLDOWN_MS = 90_000L
    }
}
