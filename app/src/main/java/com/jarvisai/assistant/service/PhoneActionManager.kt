package com.jarvisai.assistant.service

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.jarvisai.assistant.model.InstalledAppInfo

data class VoiceCommandActionOutcome(
    val isPhoneCommand: Boolean,
    val executedSuccessfully: Boolean,
    val spokenHinglishResponse: String,
    val actionTitle: String = ""
)

/**
 * Parses and executes spoken/typed phone control commands for JARVIS AI
 * in Hinglish, Hindi, and English during Live Voice and Text Chat.
 *
 * Supported voice commands:
 * - "JARVIS, YouTube kholo" / "Open YouTube"
 * - "JARVIS, YouTube par search karo latest technology videos" / "YouTube kholo aur <query> search karo"
 * - "JARVIS, Chrome kholo" / "Open Chrome"
 * - "JARVIS, Settings kholo" / "Wi-Fi kholo" / "Bluetooth kholo"
 * - "JARVIS, Flashlight on karo" / "Torch band karo"
 * - "JARVIS, <Installed App> kholo"
 *
 * Never claims an action succeeded unless the Android Intent or hardware call actually succeeded.
 */
class PhoneActionManager(
    private val context: Context,
    private val phoneControlsManager: PhoneControlsManager
) {

    /**
     * Checks if [transcript] contains an actionable phone control command and executes it immediately.
     */
    suspend fun detectAndExecuteCommand(transcript: String): VoiceCommandActionOutcome {
        val clean = transcript.trim()
        if (clean.isEmpty()) {
            return VoiceCommandActionOutcome(false, false, "")
        }

        val normalized = clean
            .replace(Regex("(?i)^(?:hey\\s+|ok\\s+|hello\\s+|hi\\s+|sun\\s+)?jarvis[,!.:\\s]*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val lower = normalized.lowercase()

        // 1. YouTube Search Command:
        // Examples:
        // "YouTube par search karo latest technology videos"
        // "YouTube kholo aur latest technology videos search karo"
        // "Search latest technology videos on YouTube"
        // "YouTube pe Arijit Singh songs search karo"
        val ytSearchQuery = extractYouTubeSearchQuery(normalized)
        if (!ytSearchQuery.isNullOrBlank()) {
            return openYouTubeSearch(ytSearchQuery)
        }

        // 2. Open YouTube Command:
        // Examples: "YouTube kholo", "YouTube open karo", "Open YouTube", "यूट्यूब खोलो"
        if (isOpenAppMatch(lower, listOf("youtube", "you tube", "यूट्यूब"))) {
            return openYouTubeApp()
        }

        // 3. Google / Chrome Search Command:
        val googleSearchQuery = extractChromeSearchQuery(normalized)
        if (!googleSearchQuery.isNullOrBlank()) {
            return openChromeSearch(googleSearchQuery)
        }

        // 4. Open Chrome Command:
        // Examples: "Chrome kholo", "Chrome open karo", "Open Chrome", "क्रोम खोलो"
        if (isOpenAppMatch(lower, listOf("chrome", "google chrome", "browser", "क्रोम"))) {
            return openChromeApp()
        }

        // 5. Open Wi-Fi / Bluetooth / Android Settings:
        if (isOpenAppMatch(lower, listOf("wifi", "wi-fi", "वाईफाई", "वाई-फाई"))) {
            val res = phoneControlsManager.openWifiSettings()
            return VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = res.success,
                spokenHinglishResponse = if (res.success) {
                    "Main Wi-Fi settings open kar raha hoon."
                } else {
                    "Wi-Fi settings open karne mein problem aayi: ${res.message}"
                },
                actionTitle = "Wi-Fi Settings"
            )
        }

        if (isOpenAppMatch(lower, listOf("bluetooth", "ब्लूटूथ"))) {
            val res = phoneControlsManager.openBluetoothSettings()
            return VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = res.success,
                spokenHinglishResponse = if (res.success) {
                    "Main Bluetooth settings open kar raha hoon."
                } else {
                    "Bluetooth settings open karne mein problem aayi: ${res.message}"
                },
                actionTitle = "Bluetooth Settings"
            )
        }

        if (isOpenAppMatch(lower, listOf("settings", "setting", "phone settings", "system settings", "सेटिंग्स", "सेटिंग"))) {
            return openAndroidSettings()
        }

        // 6. Flashlight / Torch ON or OFF:
        if (lower.contains("flashlight") || lower.contains("torch") || lower.contains("टॉर्च") || lower.contains("फ्लैशलाइट")) {
            val turnOn = lower.contains("on") || lower.contains("jala") || lower.contains("chalu") ||
                lower.contains("start") || lower.contains("kholo") || lower.contains("ऑन") || lower.contains("जला")
            val turnOff = lower.contains("off") || lower.contains("band") || lower.contains("stop") ||
                lower.contains("बुझा") || lower.contains("बंद")
            if (turnOn || turnOff) {
                val enable = turnOn && !turnOff
                val res = phoneControlsManager.setFlashlight(enable)
                return VoiceCommandActionOutcome(
                    isPhoneCommand = true,
                    executedSuccessfully = res.success,
                    spokenHinglishResponse = if (res.success) {
                        if (enable) "Maine flashlight ON kar di hai." else "Maine flashlight OFF kar di hai."
                    } else {
                        "Flashlight control nahi ho payi: ${res.message}"
                    },
                    actionTitle = if (enable) "Flashlight ON" else "Flashlight OFF"
                )
            }
        }

        // 7. Accessibility Screen Scroll ("scroll down", "scroll up", "neeche scroll karo", "upar scroll karo")
        val scrollDirection = extractScrollDirection(lower)
        if (scrollDirection != null) {
            val res = JarvisAccessibilityService.scrollScreen(context, scrollDirection)
            return VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = res.executed,
                spokenHinglishResponse = res.spokenHinglishSummary,
                actionTitle = "Scroll ${scrollDirection.uppercase()}"
            )
        }

        // 8. Accessibility Global Navigation ("go back", "back jao", "peeche jao", "go home", "home screen par jao")
        if (isNavigateBackCommand(lower)) {
            val res = JarvisAccessibilityService.navigateBack(context)
            return VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = res.executed,
                spokenHinglishResponse = res.spokenHinglishSummary,
                actionTitle = "Navigate Back"
            )
        }
        if (isNavigateHomeCommand(lower)) {
            val res = JarvisAccessibilityService.navigateHome(context)
            return VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = res.executed,
                spokenHinglishResponse = res.spokenHinglishSummary,
                actionTitle = "Navigate Home"
            )
        }

        // 9. Accessibility Element Click ("click Search", "Search button par click karo", "tap Subscribe")
        val clickTarget = extractAccessibilityClickTarget(normalized)
        if (!clickTarget.isNullOrBlank()) {
            val res = JarvisAccessibilityService.clickElement(
                context = context,
                targetText = clickTarget,
                contentDescription = clickTarget,
                viewId = null
            )
            return VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = res.executed,
                spokenHinglishResponse = res.spokenHinglishSummary,
                actionTitle = "Click: $clickTarget"
            )
        }

        // 10. Battery Status Check
        if (lower.contains("battery") || lower.contains("बैटरी")) {
            if (lower.contains("status") || lower.contains("kitni") || lower.contains("level") ||
                lower.contains("check") || lower.contains("percentage") || lower.contains("%") ||
                lower.contains("charge") || lower.contains("kitna")
            ) {
                val battery = phoneControlsManager.systemStatusManager.getBatteryInfo()
                val pct = battery.levelPercent
                val isCharging = battery.isCharging
                val spoken = "Aapke device ki battery $pct% hai${if (isCharging) " aur phone charging par laga hai." else "."}"
                return VoiceCommandActionOutcome(
                    isPhoneCommand = true,
                    executedSuccessfully = true,
                    spokenHinglishResponse = spoken,
                    actionTitle = "Battery: $pct%"
                )
            }
        }

        // 11. System Volume Control ("volume badhao", "volume kam karo", "mute karo")
        if (lower.contains("volume") || lower.contains("आवाज") || lower.contains("sound")) {
            val isUp = lower.contains("up") || lower.contains("badha") || lower.contains("increase") || lower.contains("tez") || lower.contains("बढ़ा")
            val isDown = lower.contains("down") || lower.contains("kam") || lower.contains("decrease") || lower.contains("dheeme") || lower.contains("कम")
            val isMute = lower.contains("mute") || lower.contains("chup") || lower.contains("silent") || lower.contains("म्यूट")
            val isUnmute = lower.contains("unmute")

            val direction = when {
                isUp -> "up"
                isDown -> "down"
                isMute -> "mute"
                isUnmute -> "unmute"
                else -> "panel"
            }
            val (success, msg) = phoneControlsManager.systemStatusManager.adjustVolume(direction)
            return VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = success,
                spokenHinglishResponse = msg,
                actionTitle = "Volume: $direction"
            )
        }

        // 12. Media Playback Controls ("gaana roko", "pause music", "next song", "gaana chalao")
        if (lower.contains("music") || lower.contains("gaana") || lower.contains("song") || lower.contains("gana") || lower.contains("गाने") || lower.contains("गाना")) {
            val isPause = lower.contains("pause") || lower.contains("roko") || lower.contains("rok") || lower.contains("stop") || lower.contains("रोक")
            val isPlay = lower.contains("play") || lower.contains("chalao") || lower.contains("bajao") || lower.contains("resume") || lower.contains("चला") || lower.contains("बजा")
            val isNext = lower.contains("next") || lower.contains("agla") || lower.contains("skip") || lower.contains("अगला")
            val isPrev = lower.contains("previous") || lower.contains("pichhla") || lower.contains("prev") || lower.contains("पिछला")

            val action = when {
                isNext -> "next"
                isPrev -> "previous"
                isPause -> "pause"
                isPlay -> "play"
                else -> "toggle"
            }
            val res = phoneControlsManager.mediaControllerHelper.executeMediaAction(action)
            return VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = res.executed,
                spokenHinglishResponse = res.spokenHinglishSummary,
                actionTitle = "Media: $action"
            )
        }

        // 13. Generic "<App Name> kholo" / "Open <App Name>"
        val genericAppTarget = extractGenericAppNameToOpen(normalized)
        if (!genericAppTarget.isNullOrBlank()) {
            return openInstalledAppByName(genericAppTarget)
        }

        return VoiceCommandActionOutcome(false, false, "")
    }

    private fun extractScrollDirection(lowerInput: String): String? {
        if (!lowerInput.contains("scroll") && !lowerInput.contains("स्क्रॉल")) return null
        return when {
            lowerInput.contains("up") || lowerInput.contains("upar") || lowerInput.contains("backward") || lowerInput.contains("ऊपर") -> "up"
            lowerInput.contains("down") || lowerInput.contains("neeche") || lowerInput.contains("niche") || lowerInput.contains("forward") || lowerInput.contains("नीचे") -> "down"
            else -> "down"
        }
    }

    private fun isNavigateBackCommand(lowerInput: String): Boolean {
        val exactBackPhrases = setOf(
            "go back", "navigate back", "back jao", "peeche jao", "piche jao",
            "back button dabao", "press back", "वापस जाओ", "पीछे जाओ"
        )
        return lowerInput in exactBackPhrases
    }

    private fun isNavigateHomeCommand(lowerInput: String): Boolean {
        val exactHomePhrases = setOf(
            "go home", "navigate home", "go to home screen", "home screen par jao",
            "home jao", "press home", "होम स्क्रीन पर जाओ"
        )
        return lowerInput in exactHomePhrases
    }

    private fun extractAccessibilityClickTarget(input: String): String? {
        val cleaned = input.trim(' ', '.', '!', '?')
        val patterns = listOf(
            // "click on Search button" / "click Search" / "tap on Subscribe"
            Regex("(?i)^(?:please\\s+)?(?:click|tap|press)\\s+(?:on\\s+)?(?:the\\s+)?(.+?)(?:\\s+(?:button|icon|option|tab|link))?$"),
            // "Search button par click karo" / "Search pe click karo" / "Subscribe button dabao"
            Regex("(?i)^(.+?)(?:\\s+(?:button|icon|option|tab))?\\s+(?:par|pe|ko)?\\s*(?:click\\s+karo|click\\s+kar|tap\\s+karo|press\\s+karo|dabao|दबाओ|क्लिक\\s+करो)$")
        )
        for (regex in patterns) {
            val match = regex.find(cleaned)
            if (match != null) {
                val target = match.groupValues[1]
                    .replace(Regex("(?i)\\b(?:button|icon|option|the)\\b"), "")
                    .trim(' ', '"', '\'')
                if (target.isNotBlank() && target.length <= 40) {
                    return target
                }
            }
        }
        return null
    }

    fun openYouTubeApp(): VoiceCommandActionOutcome {
        val youtubePkg = "com.google.android.youtube"
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(youtubePkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                Log.d(TAG, "Opened YouTube native app ($youtubePkg)")
                VoiceCommandActionOutcome(
                    isPhoneCommand = true,
                    executedSuccessfully = true,
                    spokenHinglishResponse = "Main YouTube open kar raha hoon.",
                    actionTitle = "Open YouTube"
                )
            } else {
                // Fallback to YouTube web intent if app is not installed on device
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                VoiceCommandActionOutcome(
                    isPhoneCommand = true,
                    executedSuccessfully = true,
                    spokenHinglishResponse = "YouTube app installed nahi mila, isliye main browser mein YouTube open kar raha hoon.",
                    actionTitle = "Open YouTube (Web)"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open YouTube: ${e.localizedMessage}")
            VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = false,
                spokenHinglishResponse = "YouTube open karne mein error aaya: ${e.localizedMessage ?: "App not available"}",
                actionTitle = "Open YouTube Failed"
            )
        }
    }

    fun openYouTubeSearch(rawQuery: String): VoiceCommandActionOutcome {
        val cleanQuery = rawQuery.trim()
        if (cleanQuery.isEmpty()) return openYouTubeApp()

        val youtubePkg = "com.google.android.youtube"
        // 1. Try native YouTube ACTION_SEARCH intent first
        try {
            val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage(youtubePkg)
                putExtra(SearchManager.QUERY, cleanQuery)
                putExtra("query", cleanQuery)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (searchIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(searchIntent)
                Log.d(TAG, "Launched YouTube ACTION_SEARCH for query='$cleanQuery'")
                return VoiceCommandActionOutcome(
                    isPhoneCommand = true,
                    executedSuccessfully = true,
                    spokenHinglishResponse = "Okay, YouTube khol raha hoon aur $cleanQuery search kar raha hoon.",
                    actionTitle = "YouTube Search: $cleanQuery"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "YouTube ACTION_SEARCH failed, trying VIEW URI fallback: ${e.localizedMessage}")
        }

        // 2. Fallback to YouTube search URL (opens directly in YouTube app via App Links or browser)
        return try {
            val encoded = Uri.encode(cleanQuery)
            val uri = Uri.parse("https://www.youtube.com/results?search_query=$encoded")
            val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
            Log.d(TAG, "Launched YouTube search URL for query='$cleanQuery'")
            VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = true,
                spokenHinglishResponse = "Okay, YouTube khol raha hoon aur $cleanQuery search kar raha hoon.",
                actionTitle = "YouTube Search: $cleanQuery"
            )
        } catch (e: Exception) {
            VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = false,
                spokenHinglishResponse = "YouTube par $cleanQuery search karne mein problem aayi.",
                actionTitle = "YouTube Search Failed"
            )
        }
    }

    fun openChromeApp(): VoiceCommandActionOutcome {
        val chromePkg = "com.android.chrome"
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(chromePkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                VoiceCommandActionOutcome(
                    isPhoneCommand = true,
                    executedSuccessfully = true,
                    spokenHinglishResponse = "Main Chrome browser open kar raha hoon.",
                    actionTitle = "Open Chrome"
                )
            } else {
                val fallbackBrowser = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackBrowser)
                VoiceCommandActionOutcome(
                    isPhoneCommand = true,
                    executedSuccessfully = true,
                    spokenHinglishResponse = "Main browser open kar raha hoon.",
                    actionTitle = "Open Browser"
                )
            }
        } catch (e: Exception) {
            VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = false,
                spokenHinglishResponse = "Chrome browser open karne mein problem aayi.",
                actionTitle = "Open Chrome Failed"
            )
        }
    }

    private fun openChromeSearch(query: String): VoiceCommandActionOutcome {
        val cleanQuery = query.trim()
        return try {
            val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(cleanQuery)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = true,
                spokenHinglishResponse = "Okay, Chrome mein $cleanQuery search kar raha hoon.",
                actionTitle = "Google Search: $cleanQuery"
            )
        } catch (e: Exception) {
            VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = false,
                spokenHinglishResponse = "Browser search open karne mein problem aayi.",
                actionTitle = "Search Failed"
            )
        }
    }

    fun openAndroidSettings(): VoiceCommandActionOutcome {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = true,
                spokenHinglishResponse = "Main Android Settings open kar raha hoon.",
                actionTitle = "Open Android Settings"
            )
        } catch (e: Exception) {
            VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = false,
                spokenHinglishResponse = "Settings open karne mein error aaya.",
                actionTitle = "Open Settings Failed"
            )
        }
    }

    suspend fun openInstalledAppByName(targetName: String): VoiceCommandActionOutcome {
        val cleanTarget = targetName.trim()
        val lowerTarget = cleanTarget.lowercase()
            .replace(Regex("\\b(app|application|ko|please|zara|mera|meri)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (lowerTarget.isBlank()) {
            return VoiceCommandActionOutcome(false, false, "")
        }

        // Special handling for Google Play Store ("play store", "google play", "google play store", "playstore")
        if (lowerTarget in setOf("play store", "playstore", "google play", "google play store", "प्ले स्टोर")) {
            val playStoreRes = phoneControlsManager.launchInstalledApp("com.android.vending", "Google Play Store")
            if (playStoreRes.success) {
                return VoiceCommandActionOutcome(
                    isPhoneCommand = true,
                    executedSuccessfully = true,
                    spokenHinglishResponse = "Main Google Play Store open kar raha hoon.",
                    actionTitle = "Open Google Play Store"
                )
            }
            return try {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(marketIntent)
                VoiceCommandActionOutcome(
                    isPhoneCommand = true,
                    executedSuccessfully = true,
                    spokenHinglishResponse = "Main Google Play Store open kar raha hoon.",
                    actionTitle = "Open Google Play Store"
                )
            } catch (_: Exception) {
                VoiceCommandActionOutcome(
                    isPhoneCommand = true,
                    executedSuccessfully = false,
                    spokenHinglishResponse = "Aapke phone mein Google Play Store open nahi ho paya.",
                    actionTitle = "Play Store Not Available"
                )
            }
        }

        // Known popular apps map with multiple package candidates for OEM/Google devices
        val knownPackages: Map<String, Pair<List<String>, String>> = mapOf(
            "whatsapp" to (listOf("com.whatsapp", "com.whatsapp.w4b") to "WhatsApp"),
            "व्हाट्सएप" to (listOf("com.whatsapp", "com.whatsapp.w4b") to "WhatsApp"),
            "spotify" to (listOf("com.spotify.music") to "Spotify"),
            "instagram" to (listOf("com.instagram.android", "com.instagram.lite") to "Instagram"),
            "इंस्टाग्राम" to (listOf("com.instagram.android", "com.instagram.lite") to "Instagram"),
            "gmail" to (listOf("com.google.android.gm") to "Gmail"),
            "जीमेल" to (listOf("com.google.android.gm") to "Gmail"),
            "maps" to (listOf("com.google.android.apps.maps") to "Google Maps"),
            "google maps" to (listOf("com.google.android.apps.maps") to "Google Maps"),
            "मैप्स" to (listOf("com.google.android.apps.maps") to "Google Maps"),
            "calculator" to (listOf("com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator") to "Calculator"),
            "कैलकुलेटर" to (listOf("com.google.android.calculator", "com.android.calculator2") to "Calculator"),
            "calendar" to (listOf("com.google.android.calendar", "com.android.calendar", "com.samsung.android.calendar") to "Calendar"),
            "clock" to (listOf("com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage") to "Clock"),
            "alarm" to (listOf("com.google.android.deskclock", "com.android.deskclock") to "Clock"),
            "photos" to (listOf("com.google.android.apps.photos", "com.android.gallery3d", "com.sec.android.gallery3d") to "Photos"),
            "google photos" to (listOf("com.google.android.apps.photos") to "Google Photos"),
            "gallery" to (listOf("com.google.android.apps.photos", "com.sec.android.gallery3d", "com.miui.gallery") to "Gallery"),
            "contacts" to (listOf("com.google.android.contacts", "com.android.contacts") to "Contacts"),
            "phone" to (listOf("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer") to "Phone"),
            "dialer" to (listOf("com.google.android.dialer", "com.android.dialer") to "Dialer"),
            "messages" to (listOf("com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging") to "Messages"),
            "files" to (listOf("com.google.android.apps.nbu.files", "com.android.documentsui") to "Files")
        )

        if (lowerTarget == "camera" || lowerTarget == "कैमरा") {
            return try {
                val camIntent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(camIntent)
                VoiceCommandActionOutcome(
                    isPhoneCommand = true,
                    executedSuccessfully = true,
                    spokenHinglishResponse = "Main Camera open kar raha hoon.",
                    actionTitle = "Open Camera"
                )
            } catch (_: Exception) {
                VoiceCommandActionOutcome(false, false, "")
            }
        }

        knownPackages[lowerTarget]?.let { (pkgCandidates, label) ->
            for (pkg in pkgCandidates) {
                val res = phoneControlsManager.launchInstalledApp(pkg, label)
                if (res.success) {
                    return VoiceCommandActionOutcome(
                        isPhoneCommand = true,
                        executedSuccessfully = true,
                        spokenHinglishResponse = "Main $label open kar raha hoon.",
                        actionTitle = "Open $label"
                    )
                }
            }
        }

        val installedApps = phoneControlsManager.getInstalledLaunchableApps()
        val normalizedTargetNoSpaces = lowerTarget.replace(Regex("[^a-z0-9]"), "")
        val targetTokens = lowerTarget.split(" ").filter { it.length >= 2 }

        val matchedApp = installedApps.firstOrNull {
            it.appName.equals(lowerTarget, ignoreCase = true) ||
                it.appName.equals(cleanTarget, ignoreCase = true)
        } ?: installedApps.firstOrNull {
            val appNoSpaces = it.appName.lowercase().replace(Regex("[^a-z0-9]"), "")
            appNoSpaces.isNotEmpty() && (appNoSpaces == normalizedTargetNoSpaces ||
                appNoSpaces.contains(normalizedTargetNoSpaces) ||
                (normalizedTargetNoSpaces.length >= 4 && normalizedTargetNoSpaces.contains(appNoSpaces)))
        } ?: installedApps.firstOrNull { app ->
            val appLower = app.appName.lowercase()
            val pkgLower = app.packageName.lowercase()
            appLower.contains(lowerTarget) ||
                lowerTarget.contains(appLower) ||
                (targetTokens.isNotEmpty() && targetTokens.all { tok -> appLower.contains(tok) || pkgLower.contains(tok) })
        }

        val targetAppToLaunch = matchedApp ?: InstalledAppInfo(
            appName = cleanTarget,
            packageName = "",
            isSystemApp = false
        )

        val res = phoneControlsManager.launchInstalledApp(
            packageName = targetAppToLaunch.packageName,
            appName = targetAppToLaunch.appName,
            activityName = targetAppToLaunch.activityName,
            allowBrowserFallback = true
        )

        if (res.success) {
            val spoken = if (res.message.contains("Chrome")) {
                "Installed app could not be launched. Opening the website in Chrome."
            } else {
                "Main ${targetAppToLaunch.appName} open kar raha hoon."
            }
            return VoiceCommandActionOutcome(
                isPhoneCommand = true,
                executedSuccessfully = true,
                spokenHinglishResponse = spoken,
                actionTitle = if (res.message.contains("Chrome")) "Open in Chrome: ${targetAppToLaunch.appName}" else "Open ${targetAppToLaunch.appName}"
            )
        }

        return VoiceCommandActionOutcome(
            isPhoneCommand = true,
            executedSuccessfully = false,
            spokenHinglishResponse = "App not found.",
            actionTitle = "App Not Found: $cleanTarget"
        )
    }

    internal fun extractYouTubeSearchQuery(input: String): String? {
        val patterns = listOf(
            // "YouTube par search karo latest technology videos" / "YouTube pe search karo X"
            Regex("(?i)youtube\\s+(?:par|pe|mein|on|in)?\\s*search\\s+(?:karo|kar|kariye|for)?\\s+(.+)$"),
            // "YouTube kholo aur latest technology videos search karo"
            Regex("(?i)youtube\\s+(?:kholo|open\\s+karo|open)\\s+(?:aur|and)\\s+(.+?)\\s+search\\s+(?:karo|kar|kariye)$"),
            // "YouTube par latest technology videos search karo" / "YouTube pe X lagao / chalao"
            Regex("(?i)youtube\\s+(?:par|pe|mein|on)\\s+(.+?)\\s+(?:search\\s+karo|search\\s+kar|search|lagao|chalao|play\\s+karo)$"),
            // "Search latest technology videos on YouTube"
            Regex("(?i)search\\s+(?:for\\s+)?(.+?)\\s+on\\s+youtube$"),
            // "Play <query> on YouTube"
            Regex("(?i)play\\s+(.+?)\\s+on\\s+youtube$")
        )

        for (regex in patterns) {
            val match = regex.find(input)
            if (match != null) {
                val query = match.groupValues[1]
                    .trim(' ', '.', ',', '!', '?', '"', '\'')
                if (query.isNotBlank()) return query
            }
        }
        return null
    }

    private fun extractChromeSearchQuery(input: String): String? {
        val patterns = listOf(
            Regex("(?i)(?:chrome|google)\\s+(?:par|pe|mein|on)\\s+search\\s+(?:karo|kar)?\\s+(.+)$"),
            Regex("(?i)(?:chrome|google)\\s+(?:par|pe|mein|on)\\s+(.+?)\\s+search\\s+(?:karo|kar)$"),
            Regex("(?i)search\\s+(.+?)\\s+on\\s+(?:google|chrome)$")
        )
        for (regex in patterns) {
            val match = regex.find(input)
            if (match != null) {
                val query = match.groupValues[1].trim(' ', '.', ',', '!', '?')
                if (query.isNotBlank()) return query
            }
        }
        return null
    }

    private fun isOpenAppMatch(lowerInput: String, appKeywords: List<String>): Boolean {
        val openVerbs = listOf("kholo", "khol", "open", "chalu", "start", "launch", "खोलो", "खोल", "ओपन")
        val hasOpenVerb = openVerbs.any { lowerInput.contains(it) }
        val hasAppKeyword = appKeywords.any { lowerInput.contains(it) }
        val isSearchCommand = lowerInput.contains("search") || lowerInput.contains("सर्च")
        return hasOpenVerb && hasAppKeyword && !isSearchCommand
    }

    private fun extractGenericAppNameToOpen(input: String): String? {
        val cleanedInput = input.trim(' ', '.', '!', '?')
            .replace(Regex("(?i)^(?:please|plz|zara|bhai|yaar)\\s+"), "")
            .replace(Regex("(?i)\\s+(?:please|plz|yaar|na|bro)$"), "")
            .trim()

        val patterns = listOf(
            // "<App Name> kholo" / "<App Name> ko open karo" / "<App Name> chalu kar do"
            Regex("(?i)^([a-zA-Z0-9\\u0900-\\u097F ._-]{2,35}?)\\s+(?:app\\s+)?(?:ko\\s+)?(?:kholo|khol\\s+do|khol\\s+de|kholiye|khol|open\\s+karo|open\\s+kar\\s+do|open\\s+kariye|open|chalu\\s+karo|chalu\\s+kar\\s+do|start\\s+karo|launch\\s+karo|खोलो|खोल\\s+दो|ओपन\\s+करो)$"),
            // "Open <App Name>" / "Launch <App Name>" / "Kholo <App Name>"
            Regex("(?i)^(?:open|launch|start|kholo|खोलो)\\s+([a-zA-Z0-9\\u0900-\\u097F ._-]{2,35}?)(?:\\s+(?:app|application|ko|karo))?$")
        )
        val ignoredTargets = setOf("jarvis", "memory", "chat", "voice", "live voice", "mic", "microphone", "screen", "screen share")
        for (regex in patterns) {
            val match = regex.find(cleanedInput)
            if (match != null) {
                val candidate = match.groupValues[1]
                    .replace(Regex("(?i)\\b(?:app|application|ko)\\b"), "")
                    .trim()
                if (candidate.isNotBlank() && candidate.lowercase() !in ignoredTargets) {
                    return candidate
                }
            }
        }
        return null
    }

    companion object {
        private const val TAG = "PhoneActionManager"
    }
}
