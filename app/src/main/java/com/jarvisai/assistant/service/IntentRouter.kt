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
 */
object IntentRouter {

    private val ALLOWLISTED_ACTIONS = setOf(
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

    fun isActionAllowlisted(actionName: String): Boolean = actionName in ALLOWLISTED_ACTIONS

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

        // 0. Feature 10: Instant Voice Undo / Rollback & High-Risk Safety Gate Confirmation
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

        // 1. WhatsApp Messaging Commands (Section 10.1 & 11.2)
        val whatsAppParsed = extractWhatsAppCommand(normalized, lower)
        if (whatsAppParsed != null) {
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

        // 2. Bluetooth ON/OFF (Section 3.1 & 11.2)
        // Examples: "Bluetooth on karo", "Bluetooth band karo", "Turn on Bluetooth", "Turn off Bluetooth"
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

        // 3. Wi-Fi ON/OFF (Section 3.1 & 11.2)
        // Examples: "Wi-Fi on karo", "Wi-Fi band karo", "Turn on Wi-Fi", "Turn off Wi-Fi"
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

        // 4. Hotspot ON/OFF (Section 3.1 & 11.2)
        // Examples: "Hotspot chalu karo", "Hotspot band karo", "Turn on Hotspot", "Turn off Hotspot"
        if (lower.contains("hotspot") || lower.contains("tethering") || lower.contains("हॉटस्पॉट")) {
            val turnOn = hasTurnOnVerb(lower)
            val turnOff = hasTurnOffVerb(lower)
            return RoutedCommandIntent(
                category = IntentCategory.SYSTEM_CONTROL,
                actionName = "HOTSPOT_CONTROL",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                targetAppOrSetting = "Hotspot",
                enableState = if (turnOff) false else true
            )
        }

        // 5. Mobile Data ON/OFF (Section 3.1)
        // Examples: "Mobile data on karo", "Mobile data off karo", "Turn on mobile data", "Data band karo"
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

        // 6. Brightness Control (Section 3.1)
        // Examples: "Brightness badhao", "Brightness 80% karo", "Set brightness to 50%", "Brightness kam karo"
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

        // 7. Volume Control (Section 3.1 & 11.2)
        // Examples: "Volume full karo", "Volume 70% karo", "Volume badhao", "Volume kam karo", "Mute karo"
        if (lower.contains("volume") || lower.contains("आवाज") || lower.contains("sound") || lower == "mute" || lower == "unmute" || lower == "mute karo") {
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

        // 8. Flashlight / Torch Control
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

        // 9. Screen Scrolling (Section 5)
        if (lower.contains("scroll") || lower.contains("स्क्रॉल")) {
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

        // 10. Text Input / Voice Typing ("type hello world", "likho hello", "input field mein hello type karo")
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

        // 11. Screen Vision / Read Screen ("read screen", "screen par kya hai", "what is on my screen")
        if (lower.contains("what is on my screen") || lower.contains("what's on my screen") ||
            lower.contains("read screen") || lower.contains("screen par kya hai") ||
            lower.contains("screen padho")
        ) {
            return RoutedCommandIntent(
                category = IntentCategory.SCREEN_VISION,
                actionName = "READ_SCREEN",
                rawUtterance = clean,
                normalizedUtterance = normalized
            )
        }

        // 12. Personality Switching ("switch to friendly mode", "jarvis mode on karo", "female companion mode chalu karo")
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

        // 12A. Feature 3: Floating Cyber-HUD Overlay ("floating hud on karo", "floating bubble off karo")
        if (lower.contains("floating hud") || lower.contains("floating bubble") || lower.contains("cyber hud") || lower.contains("overlay hud")) {
            val turnOff = hasTurnOffVerb(lower)
            return RoutedCommandIntent(
                category = IntentCategory.AGENTIC_GOAL,
                actionName = "FLOATING_HUD_CONTROL",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                enableState = !turnOff
            )
        }

        // 12B. Feature 4: Background Notification Inline Reply ("notification reply to Aman: Main raste me hoon", "Rahul ke notification ka reply de do ki...")
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

        // 12C. Feature 5: Conversational Form-Filling Agent ("is screen ka form bharwao", "fill form on screen", "auto fill form")
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

        // 12D. Feature 6: Cross-App Data Bridge & Screen Translator ("screen ko hindi me translate karo", "translate screen", "cross app bridge")
        if ((lower.contains("screen") && (lower.contains("translate") || lower.contains("summary") || lower.contains("summarize") || lower.contains("anuvad"))) ||
            lower.contains("cross app bridge")
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

        // 12E. Feature 7: Teachable "Watch Me & Learn" Macro Recorder ("learning mode on karo", "watch me and learn", "dekho main kya kar raha hoon aur seekho")
        if (lower.contains("learning mode") || lower.contains("watch me and learn") ||
            (lower.contains("dekho") && lower.contains("seekho")) || lower.contains("save learned skill")
        ) {
            val isSave = lower.contains("save") || lower.contains("stop")
            val skillName = normalized.substringAfterLast("as ").substringAfterLast("naam se ").trim()
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

        // 12F. Feature 8: Hands-Free Speakerphone Call, System Alarm & Timer
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

        // 12G. Feature 9: Deep-Link "Zero-Click" Teleport ("navigation lagao", "navigate to...", "Google Maps me Home navigate karo", "youtube shorts kholo")
        if (lower.contains("navigation lagao") || lower.contains("navigate to") || lower.contains("navigate karo") || lower.contains("raste ka navigation")) {
            val dest = normalized
                .replace(Regex("(?i)(google\\s+maps\\s+(?:me|mein|par|on)|navigate\\s+karo|navigate\\s+to|navigation\\s+lagao)"), "")
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
        if (lower.contains("youtube shorts")) {
            return RoutedCommandIntent(
                category = IntentCategory.AGENTIC_GOAL,
                actionName = "DEEP_LINK_TELEPORT",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                targetAppOrSetting = "YOUTUBE_SHORTS",
                targetElementOrContact = "Shorts"
            )
        }

        // 12H. Feature 2: Vision Set-of-Mark Coordinate Tap ("tap badge 2", "badge 1 tap karo", "set of mark tap", "top right icon par tap karo")
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

        // 12I. Feature 1: Multi-Step Autonomous ReAct Goal ("khol ke ... check karo", "YouTube kholo aur ... search karo aur ... play karo")
        val multiStepConnectorCount = Regex("\\b(aur|and|phir|then)\\b").findAll(lower).count()
        if (lower.startsWith("autonomous goal") || lower.startsWith("agent goal") ||
            multiStepConnectorCount >= 2 ||
            ((lower.contains("khol ke") || lower.contains("open")) &&
                (lower.contains("check karo") || lower.contains("order") || lower.contains("followers") || lower.contains("status padh")))
        ) {
            return RoutedCommandIntent(
                category = IntentCategory.AGENTIC_GOAL,
                actionName = "AGENTIC_REACT_GOAL",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                textOrMessageContent = normalized
            )
        }

        // 13. App Launch ("YouTube open karo", "Open YouTube", "Open WhatsApp", "Open Chrome", "Settings kholo")
        if (lower.contains("open") || lower.contains("kholo") || lower.contains("khol") ||
            lower.contains("launch") || lower.contains("start") || lower.contains("खोलो")
        ) {
            return RoutedCommandIntent(
                category = IntentCategory.APP_LAUNCH,
                actionName = "OPEN_INSTALLED_APP",
                rawUtterance = clean,
                normalizedUtterance = normalized,
                targetAppOrSetting = normalized
            )
        }

        return RoutedCommandIntent(
            category = IntentCategory.GENERAL_QUERY,
            actionName = "GENERAL_AI_QUERY",
            rawUtterance = clean,
            normalizedUtterance = normalized
        )
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
        return "" to ""
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
