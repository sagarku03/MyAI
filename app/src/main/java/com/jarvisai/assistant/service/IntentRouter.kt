package com.jarvisai.assistant.service

import java.util.Locale

/**
 * Structured intent categories for JARVIS AI Command Understanding (Section 11.1).
 */
enum class IntentCategory {
    APP_LAUNCH,
    SYSTEM_CONTROL,
    ACCESSIBILITY_ACTION,
    SCREEN_VISION,
    TEXT_INPUT,
    WHATSAPP_ACTION,
    LIVE_CONVERSATION,
    MEMORY_ACTION,
    PERSONALITY_ACTION,
    AGENTIC_GOAL,
    GENERAL_QUERY
}

/**
 * Structured command payload produced by [IntentRouter] and consumed by allowlisted handlers (Section 11.3).
 */
data class RoutedCommandIntent(
    val category: IntentCategory,
    val actionName: String,
    val rawUtterance: String,
    val normalizedUtterance: String,
    val targetAppOrSetting: String = "",
    val targetElementOrContact: String = "",
    val textOrMessageContent: String = "",
    val enableState: Boolean? = null,
    val numericValue: Int? = null,
    val direction: String = "",
    val distance: String = "MEDIUM",
    val allowlisted: Boolean = true
)

/**
 * Multilingual Command Understanding & Intent Router supporting English, Hindi (हिंदी), and Hinglish (Section 11).
 *
 * Architecture (`AbuZar-Ansarii/Jarvis` PrivateAgent):
 * 1. Keeps 0ms instant hardware fast-paths for Flashlight ON/OFF, Volume set, Bluetooth/Wi-Fi/Hotspot/Data/Brightness toggles, and Media play/pause/next/previous.
 * 2. Keeps deterministic specialized system actions (Safety Gatekeeper, Undo, Screen Vision, HUD, Inline Notification Reply, Alarm/Timer).
 * 3. Routes all other action-oriented utterances ("kholo", "open", "bhejo", "send", "search", "chalao", "play", "order", "book", "like", "scroll", "type", "fill", "setting badlo", etc.)
 *    directly to `IntentCategory.AGENTIC_GOAL` with `actionName = "EXECUTE_AUTONOMOUS_OPERATOR"`, eliminating brittle regex traps.
 * 4. Routes pure conversational / knowledge queries ("What is quantum computing?", "Kavita sunao", "Tell me a joke") to `IntentCategory.GENERAL_QUERY`.
 */
object IntentRouter {

    private val ALLOWLISTED_ACTIONS = setOf(
        "EXECUTE_AUTONOMOUS_OPERATOR",
        "OPEN_YOUTUBE",
        "YOUTUBE_SEARCH",
        "OPEN_WHATSAPP",
        "SEND_WHATSAPP_MESSAGE",
        "OPEN_CHROME",
        "CHROME_SEARCH",
        "OPEN_SETTINGS",
        "OPEN_INSTALLED_APP",
        "BLUETOOTH_CONTROL",
        "WIFI_CONTROL",
        "HOTSPOT_CONTROL",
        "MOBILE_DATA_CONTROL",
        "VOLUME_CONTROL",
        "BRIGHTNESS_CONTROL",
        "FLASHLIGHT_CONTROL",
        "SCROLL_SCREEN",
        "CLICK_ELEMENT",
        "SELECT_ORDINAL_ELEMENT",
        "TYPE_TEXT",
        "CLEAR_TEXT",
        "NAVIGATE_BACK",
        "NAVIGATE_HOME",
        "READ_SCREEN",
        "BATTERY_STATUS",
        "MEDIA_PLAYBACK",
        "PERSONALITY_SWITCH",
        "MEMORY_SAVE",
        "LIVE_VOICE_CONTROL",
        "AGENTIC_REACT_GOAL",
        "VISION_SET_OF_MARK_TAP",
        "FLOATING_HUD_CONTROL",
        "NOTIFICATION_INLINE_REPLY",
        "CONVERSATIONAL_FORM_FILL",
        "CROSS_APP_DATA_BRIDGE",
        "TEACHABLE_MACRO_RECORD",
        "HANDS_FREE_SPEAKER_CALL",
        "SET_SYSTEM_ALARM",
        "SET_SYSTEM_TIMER",
        "DEEP_LINK_TELEPORT",
        "SAFETY_GATE_CONFIRM",
        "SAFETY_GATE_CANCEL",
        "VOICE_UNDO_ROLLBACK",
        "GENERAL_AI_QUERY"
    )

    private val ACTION_VERB_REGEX = Regex(
        "(?i)\\b(" +
            "kholo|khol|kholiye|kholna|open|launch|start|" +
            "bhejo|bhej|bhejiye|send|forward|" +
            "search|dhundho|dhoondho|find|lookup|" +
            "chalao|chala|chalaiye|play|bajao|baja|" +
            "order|book|buy|purchase|add\\s+to\\s+cart|checkout|" +
            "like|subscribe|follow|unfollow|comment|share|repost|" +
            "scroll|swipe|drag|" +
            "type|likho|likh|enter|input|" +
            "fill|bharo|bhar|bhariye|" +
            "setting\\s+badlo|settings\\s+badlo|change\\s+setting|change\\s+settings|badlo|" +
            "click|tap|press|dabao|select|choose|" +
            "post|upload|download|install|uninstall|delete|remove|clear|" +
            "check\\s+karo|message\\s+karo|call\\s+karo|call\\s+lagao|navigate" +
            ")\\b"
    )

    private val DEVANAGARI_ACTION_VERBS = listOf(
        "खोलो", "खोल", "ओपन", "भेजो", "सर्च", "ढूंढो", "चलाओ", "बजाओ", "प्ले",
        "ऑर्डर", "बुक", "लाइक", "सब्सक्राइब", "स्क्रॉल", "टाइप", "लिखो", "भरो",
        "सेटिंग बदलो", "क्लिक", "टैप", "दबाओ"
    )

    private val PURE_KNOWLEDGE_PREFIX_REGEX = Regex(
        "(?i)^(" +
            "what\\s+is|what\\s+are|who\\s+is|who\\s+was|why\\s+is|why\\s+do|how\\s+does|how\\s+do\\s+you\\s+explain|" +
            "when\\s+was|where\\s+is|explain|define|meaning\\s+of|difference\\s+between|" +
            "tell\\s+me\\s+a\\s+joke|tell\\s+me\\s+a\\s+story|tell\\s+me\\s+about|" +
            "write\\s+a\\s+poem|write\\s+an\\s+essay|write\\s+a\\s+story|write\\s+code|" +
            "kavita\\s+sunao|kahani\\s+sunao|joke\\s+sunao|chutkula\\s+sunao|shayari\\s+sunao" +
            ")\\b"
    )

    private val PURE_KNOWLEDGE_CONTAINS = listOf(
        "kavita sunao", "kahani sunao", "joke sunao", "chutkula sunao", "shayari sunao",
        "tell me a joke", "write a poem", "what is quantum", "kya hota hai", "kaun hai", "kaun tha"
    )

    private val COMPOUND_GOAL_CONNECTOR_REGEX = Regex(
        "(?i)\\b(aur|and|or|phir|fir|then|uske\\s+baad|after\\s+that|karke|kar\\s+ke)\\b"
    )

    fun isActionAllowlisted(actionName: String): Boolean = actionName in ALLOWLISTED_ACTIONS

    /**
     * Returns true if [rawInput] is a pure conversational, creative, or factual knowledge question
     * rather than a device/screen action.
     */
    fun isPureKnowledgeQuery(rawInput: String): Boolean {
        val lower = rawInput.trim()
            .replace(Regex("(?i)^(?:hey\\s+|ok\\s+|hello\\s+|hi\\s+|suno\\s+|sun\\s+|o\\s+)?jarvis[,!.:\\s]*"), "")
            .trim()
            .lowercase(Locale.US)
        if (lower.isEmpty()) return false
        if (lower.contains("screen") || lower.contains("battery")) return false
        if (PURE_KNOWLEDGE_PREFIX_REGEX.containsMatchIn(lower)) return true
        if (PURE_KNOWLEDGE_CONTAINS.any { lower.contains(it) }) return true
        return false
    }

    /**
     * Returns true if [rawInput] expresses an actionable device or screen operator goal.
     */
    fun isActionOrientedUtterance(rawInput: String): Boolean {
        val lower = rawInput.trim()
            .replace(Regex("(?i)^(?:hey\\s+|ok\\s+|hello\\s+|hi\\s+|suno\\s+|sun\\s+|o\\s+)?jarvis[,!.:\\s]*"), "")
            .trim()
            .lowercase(Locale.US)
        if (lower.isEmpty() || isPureKnowledgeQuery(lower)) return false
        if (ACTION_VERB_REGEX.containsMatchIn(lower)) return true
        if (DEVANAGARI_ACTION_VERBS.any { lower.contains(it) }) return true
        return false
    }

    /**
     * Classifies a spoken or typed command in English, Hindi, or Hinglish into a structured [RoutedCommandIntent].
     */
    fun classifyCommand(rawInput: String): RoutedCommandIntent {
        val clean = rawInput.trim()
        val normalized = clean
            .replace(Regex("(?i)^(?:hey\\s+|ok\\s+|hello\\s+|hi\\s+|suno\\s+|sun\\s+|o\\s+)?jarvis[,!.:\\s]*"), "")
            .replace(Regex("^(?:हे\\s+|ओ\\s+|सुनो\\s+)?जार्विस[,!.:\\s]*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val lower = normalized.lowercase(Locale.US)

        // =====================================================================
        // 0. Instant Safety Gatekeeper & Voice Undo / Rollback
        // =====================================================================
        if (lower == "undo" || lower == "undo karo" || lower == "ruko" || lower == "stop agent" ||
            lower == "jarvis ruko" || lower.contains("ruko wapas") || lower.contains("ek step piche") || lower.contains("rollback")
        ) {
            return RoutedCommandIntent(
                category = IntentCategory.AGENTIC_GOAL,
                actionName = "VOICE_UNDO_ROLLBACK",
                rawUtterance = clean,
                normalizedUtterance = normalized
            )
        }
        if (AgenticSafetyGatekeeper.pendingHighRiskGate.value != null) {
            if (lower.contains("confirm") || lower == "haan" || lower == "haan click kar do") {
                return RoutedCommandIntent(
                    category = IntentCategory.AGENTIC_GOAL,
                    actionName = "SAFETY_GATE_CONFIRM",
                    rawUtterance = clean,
                    normalizedUtterance = normalized
                )
            }
            if (lower.contains("cancel") || lower == "nahi" || lower.contains("mat karo") || lower == "abort") {
                return RoutedCommandIntent(
                    category = IntentCategory.AGENTIC_GOAL,
                    actionName = "SAFETY_GATE_CANCEL",
                    rawUtterance = clean,
                    normalizedUtterance = normalized
                )
            }
        }

        // =====================================================================
        // 1. 0ms INSTANT HARDWARE TOGGLES (Flashlight, Volume, BT, Wi-Fi, Hotspot, Data, Brightness, Media)
        // =====================================================================

        // 1A. Flashlight / Torch ON/OFF
        if (lower.contains("flashlight") || lower.contains("torch") || lower.contains("टॉर्च") || lower.contains("फ्लैशलाइट")) {
            val turnOn = hasTurnOnVerb(lower)
            val turnOff = hasTurnOffVerb(lower)
            if (turnOn || turnOff) {
                return RoutedCommandIntent(
                    category = IntentCategory.SYSTEM_CONTROL,
                    actionName = "FLASHLIGHT_CONTROL",
                    rawUtterance = clean,
                    normalizedUtterance = normalized,
                    targetAppOrSetting = "Flashlight",
                    enableState = turnOn && !turnOff
                )
            }
        }

        // 1B. Volume Set / Up / Down / Mute / Full
        if (lower.contains("volume") || lower.contains("आवाज") || lower.contains("sound") ||
            lower == "mute" || lower == "unmute" || lower == "mute karo"
        ) {
            val pctMatch = Regex("(\\d{1,3})\\s*(?:%|percent|प्रतिशत)").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Regex("(?:to|par|pe)\\s+(\\d{1,3})\\b").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Regex("\\b(\\d{1,3})\\b").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val dir = when {
                pctMatch != null -> "$pctMatch%"
                lower.contains("full") || lower.contains("max") || lower.contains("poora") || lower.contains("pura") || lower.contains("100") -> "full"
                lower.contains("unmute") -> "unmute"
                lower.contains("mute") || lower.contains("silent") || lower.contains("chup") || lower.contains("म्यूट") -> "mute"
                lower.contains("up") || lower.contains("badha") || lower.contains("increase") || lower.contains("tez") || lower.contains("बढ़ा") -> "up"
                lower.contains("down") || lower.contains("kam") || lower.contains("decrease") || lower.contains("dheeme") || lower.contains("कम") -> "down"
                else -> "up"
            }
            return RoutedCommandIntent(
                category = IntentCategory.SYSTEM_CONTROL,
                actionName = "VOLUME_CONTROL",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                targetAppOrSetting = "Volume",
                numericValue = pctMatch,
                direction = dir
            )
        }

        // 1C. Bluetooth ON/OFF
        if (lower.contains("bluetooth") || lower.contains("ब्लूटूथ")) {
            val turnOn = hasTurnOnVerb(lower)
            val turnOff = hasTurnOffVerb(lower)
            if (turnOn || turnOff) {
                return RoutedCommandIntent(
                    category = IntentCategory.SYSTEM_CONTROL,
                    actionName = "BLUETOOTH_CONTROL",
                    rawUtterance = clean,
                    normalizedUtterance = normalized,
                    targetAppOrSetting = "Bluetooth",
                    enableState = turnOn && !turnOff
                )
            }
        }

        // 1D. Wi-Fi ON/OFF
        if (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("वाईफाई") || lower.contains("वाई-फाई")) {
            val turnOn = hasTurnOnVerb(lower)
            val turnOff = hasTurnOffVerb(lower)
            if (turnOn || turnOff) {
                return RoutedCommandIntent(
                    category = IntentCategory.SYSTEM_CONTROL,
                    actionName = "WIFI_CONTROL",
                    rawUtterance = clean,
                    normalizedUtterance = normalized,
                    targetAppOrSetting = "Wi-Fi",
                    enableState = turnOn && !turnOff
                )
            }
        }

        // 1E. Hotspot ON/OFF
        if (lower.contains("hotspot") || lower.contains("tethering") || lower.contains("हॉटस्पॉट")) {
            val turnOff = hasTurnOffVerb(lower)
            return RoutedCommandIntent(
                category = IntentCategory.SYSTEM_CONTROL,
                actionName = "HOTSPOT_CONTROL",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                targetAppOrSetting = "Hotspot",
                enableState = !turnOff
            )
        }

        // 1F. Mobile Data ON/OFF
        if (lower.contains("mobile data") || lower.contains("cellular data") || lower.contains("मोबाइल डेटा") ||
            (lower.contains("data") && (lower.contains("on") || lower.contains("off") || lower.contains("chalu") || lower.contains("band")))
        ) {
            val turnOff = hasTurnOffVerb(lower)
            return RoutedCommandIntent(
                category = IntentCategory.SYSTEM_CONTROL,
                actionName = "MOBILE_DATA_CONTROL",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                targetAppOrSetting = "Mobile Data",
                enableState = !turnOff
            )
        }

        // 1G. Brightness Control
        if (lower.contains("brightness") || lower.contains("ब्राइटनेस") || lower.contains("roshni")) {
            val pctMatch = Regex("(\\d{1,3})\\s*(?:%|percent|प्रतिशत)?").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val dir = when {
                pctMatch != null -> "$pctMatch%"
                lower.contains("full") || lower.contains("max") || lower.contains("poora") -> "full"
                lower.contains("up") || lower.contains("badha") || lower.contains("increase") || lower.contains("zyada") -> "up"
                lower.contains("down") || lower.contains("kam") || lower.contains("decrease") || lower.contains("dim") || lower.contains("low") -> "down"
                else -> ""
            }
            return RoutedCommandIntent(
                category = IntentCategory.SYSTEM_CONTROL,
                actionName = "BRIGHTNESS_CONTROL",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                targetAppOrSetting = "Brightness",
                numericValue = pctMatch,
                direction = dir
            )
        }

        // 1H. Instant Media Play / Pause / Next / Previous Toggle (only when not targeting a specific app like YouTube/Spotify)
        if (isInstantMediaPlaybackToggle(lower)) {
            val mediaDir = when {
                lower.contains("next") || lower.contains("agla") || lower.contains("skip") || lower.contains("अगला") -> "next"
                lower.contains("previous") || lower.contains("pichhla") || lower.contains("prev") || lower.contains("पिछला") -> "previous"
                lower.contains("pause") || lower.contains("roko") || lower.contains("rok") || lower.contains("stop") || lower.contains("रोक") -> "pause"
                else -> "play"
            }
            return RoutedCommandIntent(
                category = IntentCategory.SYSTEM_CONTROL,
                actionName = "MEDIA_PLAYBACK",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                targetAppOrSetting = "Media",
                direction = mediaDir
            )
        }

        // =====================================================================
        // 2. Specialized Deterministic Fast-Paths (WhatsApp, Screen Vision, HUD, Forms, etc.)
        // =====================================================================

        // 2A. WhatsApp Direct Message Fast-Path
        val whatsAppParsed = extractWhatsAppCommand(normalized, lower)
        if (whatsAppParsed != null && whatsAppParsed.first.isNotBlank()) {
            return RoutedCommandIntent(
                category = IntentCategory.WHATSAPP_ACTION,
                actionName = "SEND_WHATSAPP_MESSAGE",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                targetAppOrSetting = "WhatsApp",
                targetElementOrContact = whatsAppParsed.first,
                textOrMessageContent = whatsAppParsed.second
            )
        }

        // 2B. Pure Single-Step Screen Scrolling ("fast scroll down", "upar scroll karo")
        if ((lower.contains("scroll") || lower.contains("स्क्रॉल")) && !COMPOUND_GOAL_CONNECTOR_REGEX.containsMatchIn(lower)) {
            val dir = when {
                lower.contains("up") || lower.contains("upar") || lower.contains("ऊपर") -> "up"
                lower.contains("left") || lower.contains("baayein") || lower.contains("बाएं") -> "left"
                lower.contains("right") || lower.contains("daayein") || lower.contains("दाएं") -> "right"
                else -> "down"
            }
            val dist = when {
                lower.contains("short") || lower.contains("little") || lower.contains("thoda") -> "SHORT"
                lower.contains("long") || lower.contains("full") || lower.contains("zyada") || lower.contains("fast") -> "LONG"
                else -> "MEDIUM"
            }
            return RoutedCommandIntent(
                category = IntentCategory.ACCESSIBILITY_ACTION,
                actionName = "SCROLL_SCREEN",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                direction = dir,
                distance = dist
            )
        }

        // 2C. Pure Single-Step Voice Typing ("type hello world", "likho hello")
        if (!COMPOUND_GOAL_CONNECTOR_REGEX.containsMatchIn(lower)) {
            val typedText = extractVoiceTypingText(normalized)
            if (!typedText.isNullOrBlank()) {
                return RoutedCommandIntent(
                    category = IntentCategory.TEXT_INPUT,
                    actionName = "TYPE_TEXT",
                    rawUtterance = clean,
                    normalizedUtterance = normalized,
                    textOrMessageContent = typedText
                )
            }
        }

        // 2D. Screen Vision / Read Screen ("read screen", "screen par kya hai", "screen me kya dikh raha hai")
        if (lower.contains("what is on my screen") || lower.contains("what's on my screen") || lower.contains("whats on my screen") ||
            lower.contains("read screen") || lower.contains("read my screen") || lower.contains("inspect screen") ||
            lower.contains("screen par kya hai") || lower.contains("screen me kya hai") || lower.contains("screen mein kya hai") ||
            lower.contains("screen pe kya hai") || lower.contains("screen par kya dikh") || lower.contains("screen me kya dikh") ||
            lower.contains("kya dikh raha hai screen") || lower.contains("screen dekho") || lower.contains("meri screen dekho") ||
            lower.contains("live screen dekho") || lower.contains("screen padho") || lower.contains("screen batao") ||
            lower.contains("screen par kya chal raha") || lower.contains("screen me kya chal raha")
        ) {
            return RoutedCommandIntent(
                category = IntentCategory.SCREEN_VISION,
                actionName = "READ_SCREEN",
                rawUtterance = clean,
                normalizedUtterance = normalized
            )
        }

        // 2E. Personality Switching
        if (lower.contains("personality") || lower.contains("companion mode") ||
            (lower.contains("mode") && (lower.contains("professional") || lower.contains("friendly") || lower.contains("developer") || lower.contains("casual") || lower.contains("concise")))
        ) {
            return RoutedCommandIntent(
                category = IntentCategory.PERSONALITY_ACTION,
                actionName = "PERSONALITY_SWITCH",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                targetAppOrSetting = normalized
            )
        }

        // 2F. Floating Cyber-HUD Overlay
        if (lower.contains("floating hud") || lower.contains("folding hud") || lower.contains("folding hd") ||
            lower.contains("floating hd") || lower.contains("floating bubble") || lower.contains("cyber hud") ||
            lower.contains("overlay hud") || Regex("\\b(hud|folding\\s+hd)\\s+(on|off|chalu|band)\\b").containsMatchIn(lower)
        ) {
            val turnOff = hasTurnOffVerb(lower)
            return RoutedCommandIntent(
                category = IntentCategory.AGENTIC_GOAL,
                actionName = "FLOATING_HUD_CONTROL",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                enableState = !turnOff
            )
        }

        // 2G. Background Notification Inline Reply
        if (lower.contains("notification reply") || lower.contains("background reply") || lower.contains("inline reply") ||
            (lower.contains("notification") && lower.contains("reply"))
        ) {
            val senderMatch = Regex("(?i)(?:to|for)\\s+([a-zA-Z0-9 ]{2,20}?)(?:\\s*[:,-]|\\s+saying|\\s+ki)\\s*(.+)$").find(normalized)
                ?: Regex("(?i)^([a-zA-Z0-9 ]{2,20}?)\\s+(?:ke|ka|ko)\\s+(?:background\\s+|notification\\s+)?(?:ka\\s+)?reply\\s+(?:de\\s+do|bhej\\s+do|karo|bhejo)\\s*(?:ki\\s*)?(.+)$").find(normalized)
            val sender = senderMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val msg = senderMatch?.groupValues?.getOrNull(2)?.trim().orEmpty().ifBlank {
                normalized.substringAfter("ki ", normalized.substringAfter(":", "")).trim()
            }
            return RoutedCommandIntent(
                category = IntentCategory.AGENTIC_GOAL,
                actionName = "NOTIFICATION_INLINE_REPLY",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                targetElementOrContact = sender,
                textOrMessageContent = msg
            )
        }

        // 2H. Conversational Form-Filling Agent
        val specificFieldFillMatch = Regex(
            "(?i)\\b(name|naam|full\\s+name|age|umar|email|mail|phone|mobile|number|city|address)\\s+(?:wale\\s+|wala\\s+|box\\s+|field\\s+)?(?:me|mein|in|par)\\s+(.+?)\\s+(?:saal\\s+)?(?:fill\\s+kar\\s+do|fill\\s+karo|fill|bhar\\s+do|bharo|likh\\s+do|daal\\s+do)$"
        ).find(normalized)
        if (specificFieldFillMatch != null) {
            val fieldName = specificFieldFillMatch.groupValues[1].trim()
            val rawVal = specificFieldFillMatch.groupValues[2]
                .replace(Regex("(?i)\\b(saal|years|year)\\b"), "")
                .trim()
            return RoutedCommandIntent(
                category = IntentCategory.AGENTIC_GOAL,
                actionName = "CONVERSATIONAL_FORM_FILL",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                targetElementOrContact = fieldName,
                textOrMessageContent = rawVal
            )
        }
        if ((lower.contains("form") && (lower.contains("fill") || lower.contains("bhar") || lower.contains("auto"))) ||
            lower.contains("form bharwao")
        ) {
            return RoutedCommandIntent(
                category = IntentCategory.AGENTIC_GOAL,
                actionName = "CONVERSATIONAL_FORM_FILL",
                rawUtterance = clean,
                normalizedUtterance = normalized
            )
        }

        // 2I. Cross-App Data Bridge & Screen Translator
        if ((lower.contains("screen") && (lower.contains("translate") || lower.contains("summary") || lower.contains("summarize") || lower.contains("anuvad") || lower.contains("hindi me"))) ||
            lower.contains("cross app bridge") || lower.contains("screen translate")
        ) {
            val lang = when {
                lower.contains("english") -> "English"
                lower.contains("hinglish") -> "Hinglish"
                else -> "Hindi"
            }
            return RoutedCommandIntent(
                category = IntentCategory.AGENTIC_GOAL,
                actionName = "CROSS_APP_DATA_BRIDGE",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                targetAppOrSetting = lang
            )
        }

        // 2J. Teachable "Watch Me & Learn" Macro Recorder
        if (lower.contains("learning mode") || lower.contains("watch me and learn") ||
            (lower.contains("dekho") && lower.contains("seekho")) || lower.contains("save learned skill") ||
            lower.contains("naam se save") || (lower.contains("isko") && lower.contains("save") && lower.contains("kar"))
        ) {
            val isSave = lower.contains("save") || lower.contains("stop")
            val naamSeMatch = Regex("(?i)(?:isko|is\\s+skill\\s+ko|as)\\s+(.+?)\\s+naam\\s+se\\s+save").find(normalized)
                ?: Regex("(?i)(?:save\\s+learned\\s+skill\\s+as|save\\s+as|naam\\s+se\\s+save\\s+karo|naam\\s+se\\s+save\\s+kar\\s+do)\\s*(.+)$").find(normalized)
            val skillName = (naamSeMatch?.groupValues?.getOrNull(1)?.trim()
                ?: normalized.substringAfterLast("as ")
                    .substringBefore(" naam se")
                    .replace(Regex("(?i)^(jarvis\\s*,?\\s*)?(isko|is\\s+ko)\\s+"), "")
                    .replace(Regex("(?i)\\s*naam\\s+se\\s+save.*$"), "")
                    .trim())
                .ifBlank { "Custom Voice Skill" }
            return RoutedCommandIntent(
                category = IntentCategory.AGENTIC_GOAL,
                actionName = "TEACHABLE_MACRO_RECORD",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                enableState = !isSave,
                targetElementOrContact = skillName,
                textOrMessageContent = skillName
            )
        }

        // 2K. Hands-Free Speakerphone Call, System Alarm & Timer
        if (lower.contains("speaker par call") || lower.contains("speakerphone call") || (lower.contains("call") && lower.contains("speaker"))) {
            val target = normalized
                .replace(Regex("(?i)(speaker\\s+par|speakerphone|on\\s+speaker|call\\s+lagao|call\\s+karo|call|ko)"), "")
                .trim()
            return RoutedCommandIntent(
                category = IntentCategory.AGENTIC_GOAL,
                actionName = "HANDS_FREE_SPEAKER_CALL",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                targetElementOrContact = target
            )
        }
        if (lower.contains("alarm") && (lower.contains("set") || lower.contains("laga") || lower.contains("baje"))) {
            val hour = Regex("(\\d{1,2})").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 7
            return RoutedCommandIntent(
                category = IntentCategory.AGENTIC_GOAL,
                actionName = "SET_SYSTEM_ALARM",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                numericValue = hour
            )
        }
        if (lower.contains("timer") && (lower.contains("set") || lower.contains("laga") || lower.contains("chalu") || lower.contains("start"))) {
            val num = Regex("(\\d{1,4})").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 5
            val seconds = if (lower.contains("sec")) num else num * 60
            return RoutedCommandIntent(
                category = IntentCategory.AGENTIC_GOAL,
                actionName = "SET_SYSTEM_TIMER",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                numericValue = seconds
            )
        }

        // 2L. Deep-Link "Zero-Click" Teleport (Maps Navigation)
        if (lower.contains("navigation lagao") || lower.contains("navigate to") || lower.contains("navigate karo") ||
            lower.contains("raste ka navigation") || lower.contains("navigation on karo") || lower.contains("ka rasta dikhao")
        ) {
            val dest = normalized
                .replace(
                    Regex("(?i)\\b(jarvis|google\\s+maps\\s+(?:me|mein|par|on)|raste\\s+ka\\s+navigation\\s+on\\s+karo|raste\\s+ka\\s+navigation|navigation\\s+on\\s+karo|navigation\\s+lagao|navigate\\s+karo|navigate\\s+to|ka\\s+rasta\\s+dikhao|ke\\s+liye|ka\\s+rasta|on\\s+karo|chalo|please)\\b"),
                    " "
                )
                .replace(Regex("\\s+"), " ")
                .trim()
            return RoutedCommandIntent(
                category = IntentCategory.AGENTIC_GOAL,
                actionName = "DEEP_LINK_TELEPORT",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                targetAppOrSetting = "MAPS_NAVIGATION",
                targetElementOrContact = dest.ifBlank { "Home" }
            )
        }

        // 2M. Vision Set-of-Mark Coordinate Tap
        if (lower.contains("set of mark") || lower.contains("tap badge") ||
            (lower.contains("badge") && (lower.contains("tap") || lower.contains("click") || lower.contains("dabao"))) ||
            lower.contains("unlabeled icon") ||
            (lower.contains("icon") && (lower.contains("top right") || lower.contains("bottom right") || lower.contains("upar right")))
        ) {
            return RoutedCommandIntent(
                category = IntentCategory.AGENTIC_GOAL,
                actionName = "VISION_SET_OF_MARK_TAP",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                targetElementOrContact = normalized
            )
        }

        // 2N. Simple Single-App Launch ("YouTube open karo", "Open WhatsApp", "Chrome open karo", "Settings open karo")
        if (isSimpleSingleAppLaunch(normalized, lower)) {
            return RoutedCommandIntent(
                category = IntentCategory.APP_LAUNCH,
                actionName = "OPEN_INSTALLED_APP",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                targetAppOrSetting = normalized
            )
        }

        // =====================================================================
        // 3. UNIVERSAL AUTONOMOUS OPERATOR ROUTING (No Brittle Regex Trap!)
        // Any action-oriented utterance ("kholo", "open", "bhejo", "send", "search",
        // "chalao", "play", "order", "book", "like", "scroll", "type", "fill", "setting badlo", etc.)
        // that did not match an instant 0ms hardware toggle is routed to EXECUTE_AUTONOMOUS_OPERATOR.
        // =====================================================================
        if (isActionOrientedUtterance(normalized)) {
            return RoutedCommandIntent(
                category = IntentCategory.AGENTIC_GOAL,
                actionName = "EXECUTE_AUTONOMOUS_OPERATOR",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                textOrMessageContent = normalized
            )
        }

        return RoutedCommandIntent(
            category = IntentCategory.GENERAL_QUERY,
            actionName = "GENERAL_AI_QUERY",
            rawUtterance = clean,
            normalizedUtterance = normalized
        )
    }

    private fun isInstantMediaPlaybackToggle(lower: String): Boolean {
        val hasMediaNoun = lower.contains("music") || lower.contains("gaana") ||
            lower.contains("song") || lower.contains("gana") ||
            lower.contains("गाने") || lower.contains("गाना")
        val isStandaloneControl = lower in setOf(
            "pause", "resume", "next song", "previous song", "pause music", "play music",
            "stop music", "gaana roko", "gaana bajao", "gaana chalao", "agla gaana", "pichhla gaana"
        )
        if (!hasMediaNoun && !isStandaloneControl) return false

        // Do not intercept if the user specifies an app (YouTube, Spotify) or a compound goal
        if (lower.contains("youtube") || lower.contains("spotify") || lower.contains("search") ||
            COMPOUND_GOAL_CONNECTOR_REGEX.containsMatchIn(lower)
        ) {
            return false
        }
        val wordCount = lower.split(" ").count { it.isNotBlank() }
        return wordCount <= 4
    }

    private fun isSimpleSingleAppLaunch(normalized: String, lower: String): Boolean {
        if (COMPOUND_GOAL_CONNECTOR_REGEX.containsMatchIn(lower)) return false
        val secondaryActionWords = listOf(
            "search", "play", "chalao", "bajao", "video", "channel", "song", "message", "msg",
            "bhejo", "send", "order", "book", "like", "subscribe", "comment", "scroll", "type",
            "fill", "bharo", "setting badlo", "click", "tap", "first", "pehla", "latest"
        )
        if (secondaryActionWords.any { lower.contains(it) }) return false

        val singleAppPatterns = listOf(
            Regex("(?i)^([a-zA-Z0-9\\u0900-\\u097F ._-]{2,28}?)\\s+(?:app\\s+)?(?:ko\\s+)?(?:kholo|khol\\s+do|khol|open\\s+karo|open\\s+kar\\s+do|open|launch\\s+karo|खोलो|ओपन\\s+करो)$"),
            Regex("(?i)^(?:open|launch|kholo|खोलो)\\s+([a-zA-Z0-9\\u0900-\\u097F ._-]{2,28}?)(?:\\s+(?:app|application|karo))?$")
        )
        return singleAppPatterns.any { it.matches(normalized.trim(' ', '.', '!', '?')) }
    }

    private fun hasTurnOnVerb(lower: String): Boolean {
        val verbs = listOf("on", "enable", "turn on", "switch on", "chalu", "start", "kholo", "jala", "ऑन", "चालू", "खोलो")
        return verbs.any { lower.contains(it) }
    }

    private fun hasTurnOffVerb(lower: String): Boolean {
        val verbs = listOf("off", "disable", "turn off", "switch off", "band", "stop", "bujha", "ऑफ", "बंद")
        return verbs.any { lower.contains(it) }
    }

    /**
     * Extracts `(recipientNameOrNumber, messageContent)` from English, Hindi, and Hinglish WhatsApp commands.
     */
    internal fun extractWhatsAppCommand(normalized: String, lower: String): Pair<String, String>? {
        if (!lower.contains("whatsapp") && !lower.contains("व्हाट्सएप") && !lower.contains("व्हाट्सऐप")) {
            return null
        }

        // If user only says "Open WhatsApp" / "WhatsApp kholo" without "message" or "bhejo" or "type", let APP_LAUNCH handle it
        val hasMessageIntent = lower.contains("message") || lower.contains("msg") ||
            lower.contains("bhejo") || lower.contains("bhej") || lower.contains("send") ||
            lower.contains("type") || lower.contains("likho") || lower.contains("bol") ||
            lower.contains("कहो") || lower.contains("भेजो") || lower.contains("मैसेज")
        if (!hasMessageIntent) return null

        val patterns = listOf(
            // "Rahul ko WhatsApp par hello message bhejo" / "Rahul ko WhatsApp pe I am coming bhejo"
            Regex("(?i)^([a-zA-Z0-9+\\u0900-\\u097F ._]{2,30}?)\\s+ko\\s+whatsapp\\s+(?:par|pe|mein|on)\\s+(.+?)\\s*(?:message\\s+bhejo|msg\\s+bhejo|bhejo|bhej\\s+do|send\\s+karo)$"),
            // "WhatsApp par Rahul ko message bhejo hello" / "WhatsApp par Rahul ko hello bhejo"
            Regex("(?i)^whatsapp\\s+(?:par|pe|mein|on)\\s+([a-zA-Z0-9+\\u0900-\\u097F ._]{2,30}?)\\s+ko\\s+(?:message\\s+bhejo|msg\\s+bhejo|bhejo)\\s*[:,-]?\\s*(.*)$"),
            // "WhatsApp par Rahul ko hello message bhejo"
            Regex("(?i)^whatsapp\\s+(?:par|pe|mein|on)\\s+([a-zA-Z0-9+\\u0900-\\u097F ._]{2,30}?)\\s+ko\\s+(.+?)\\s+(?:message\\s+bhejo|msg\\s+bhejo|bhejo|send\\s+karo)$"),
            // "Send WhatsApp message to Rahul saying hello" / "Send a WhatsApp message to Rahul: hello"
            Regex("(?i)^(?:send|write)\\s+(?:a\\s+)?whatsapp\\s+(?:message|msg)\\s+to\\s+([a-zA-Z0-9+\\u0900-\\u097F ._]{2,30}?)(?:\\s+(?:saying|that|:|-)|:)\\s*(.+)$"),
            // "Send WhatsApp message to Rahul" (without message body yet)
            Regex("(?i)^(?:send|write)\\s+(?:a\\s+)?whatsapp\\s+(?:message|msg)\\s+to\\s+([a-zA-Z0-9+\\u0900-\\u097F ._]{2,30})$"),
            // "WhatsApp open karo aur message type karo <msg>" / "WhatsApp open karo aur <msg> type karo"
            Regex("(?i)^whatsapp\\s+(?:open\\s+karo|kholo|open)\\s+(?:aur|and)\\s+(?:message\\s+)?(?:type\\s+karo|likho|bhejo)\\s*(.*)$")
        )

        for ((idx, regex) in patterns.withIndex()) {
            val match = regex.find(normalized)
            if (match != null) {
                return when (idx) {
                    4 -> {
                        val recipient = match.groupValues[1].trim(' ', '"', '\'')
                        recipient to ""
                    }
                    5 -> {
                        val msg = match.groupValues[1].trim(' ', '"', '\'')
                        "" to msg
                    }
                    else -> {
                        val recipient = match.groupValues[1].trim(' ', '"', '\'')
                        val rawMsg = match.groupValues.getOrNull(2)?.trim(' ', '"', '\'').orEmpty()
                        val cleanedMsg = if (rawMsg.equals("message", ignoreCase = true) || rawMsg.equals("msg", ignoreCase = true)) {
                            ""
                        } else {
                            rawMsg.replace(Regex("(?i)\\s+message$"), "").trim()
                        }
                        recipient to cleanedMsg
                    }
                }
            }
        }

        // Generic fallback when "WhatsApp" + "ko" is present
        val koMatch = Regex("(?i)(?:whatsapp\\s+(?:par|pe)\\s+)?([a-zA-Z0-9+\\u0900-\\u097F]{2,25})\\s+ko\\b").find(normalized)
        if (koMatch != null) {
            val recipient = koMatch.groupValues[1].trim()
            if (!recipient.equals("whatsapp", ignoreCase = true) && !recipient.equals("message", ignoreCase = true)) {
                return recipient to ""
            }
        }
        return null
    }

    internal fun extractVoiceTypingText(normalized: String): String? {
        val patterns = listOf(
            Regex("(?i)^(?:type|write|enter)\\s+text\\s+(.+)$"),
            Regex("(?i)^(?:type|likho)\\s+[\"']?(.+?)[\"']?$"),
            Regex("(?i)^input\\s+field\\s+mein\\s+(.+?)\\s+(?:type\\s+karo|likho)$")
        )
        for (regex in patterns) {
            val match = regex.find(normalized)
            if (match != null) {
                val content = match.groupValues[1].trim()
                if (content.isNotBlank()) return content
            }
        }
        return null
    }
}
