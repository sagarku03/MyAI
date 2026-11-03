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

        // 1. WhatsApp Messaging Commands (Section 10.1 & 11.2)
        // Examples:
        // - "Jarvis, WhatsApp par Rahul ko message bhejo"
        // - "Rahul ko WhatsApp par hello message bhejo"
        // - "WhatsApp open karo aur message type karo hello"
        // - "Send WhatsApp message to Rahul saying hello"
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
