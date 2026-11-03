package com.jarvisai.assistant.service

import android.util.Log
import com.jarvisai.assistant.model.ActionVerificationRecord
import com.jarvisai.assistant.model.ActiveAppContextSnapshot
import com.jarvisai.assistant.model.ShortTermTurn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

enum class ContextualFollowUpType {
    NONE,
    IN_APP_YOUTUBE_SEARCH,
    IN_APP_SPOTIFY_SEARCH,
    IN_APP_GENERIC_SEARCH,
    SELECT_ORDINAL_ITEM,
    SCROLL_ACTIVE_SCREEN,
    NAVIGATE_BACK,
    REPEAT_LAST_ACTION,
    CLARIFICATION_REQUIRED;

    companion object {
        val SEARCH_IN_ACTIVE_APP: ContextualFollowUpType
            get() = IN_APP_YOUTUBE_SEARCH
        val SEARCH_IN_ACTIVE_APP_PROMPT: ContextualFollowUpType
            get() = IN_APP_YOUTUBE_SEARCH
    }
}

data class ContextualCommandResolution(
    val type: ContextualFollowUpType,
    val activeAppName: String = "",
    val activePackageName: String = "",
    val searchQuery: String = "",
    val ordinalIndex: Int = 1,
    val ordinalCategoryHint: String = "",
    val scrollDirection: String = "down",
    val repeatToolName: String = "",
    val repeatToolArgs: Map<String, String> = emptyMap(),
    val clarificationPrompt: String = ""
) {
    val targetPackageName: String
        get() = activePackageName

    val queryOrTarget: String
        get() = searchQuery
}

data class BackNavigationOutcome(
    val previousAppName: String,
    val previousPackageName: String,
    val resultingAppName: String,
    val resultingPackageName: String,
    val targetAppExited: Boolean,
    val verificationUncertain: Boolean,
    val summaryMessage: String
)

/**
 * Three-tier memory coordinator for Short-Term Conversation Memory (A) and
 * Active Session & App Context Memory (B). Long-Term Memory (C) remains in Room (JarvisRepository).
 */
object ConversationContextManager {
    private const val TAG = "ConvContextManager"
    private const val MAX_SHORT_TERM_TURNS = 20

    private val _activeAppContext = MutableStateFlow(ActiveAppContextSnapshot())
    val activeAppContext: StateFlow<ActiveAppContextSnapshot> = _activeAppContext.asStateFlow()

    private val shortTermTurns = CopyOnWriteArrayList<ShortTermTurn>()
    private val relevantEntities = CopyOnWriteArrayList<String>()

    @Volatile
    var currentUserIntent: String = ""
        private set

    @Volatile
    var lastSearchQuery: String = ""
        private set

    @Volatile
    var pendingTask: String = ""
        private set

    @Volatile
    var currentTaskState: String = "IDLE"
        private set

    @Volatile
    var lastSuccessfulAction: ActionVerificationRecord? = null
        private set

    @Volatile
    var lastToolCallName: String = ""
        private set

    @Volatile
    var lastToolCallArgs: Map<String, String> = emptyMap()
        private set

    @Volatile
    var expectedTargetAppName: String = ""
        private set

    @Volatile
    var expectedTargetAppPackage: String = ""
        private set

    @Volatile
    var wasTargetAppExitedByBack: Boolean = false
        private set

    @Volatile
    var exitedTargetAppName: String = ""
        private set

    private val knownPackageAppNames = mapOf(
        "com.google.android.youtube" to "YouTube",
        "app.revanced.android.youtube" to "YouTube",
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "com.instagram.android" to "Instagram",
        "com.spotify.music" to "Spotify",
        "com.android.chrome" to "Chrome",
        "com.google.android.gm" to "Gmail",
        "com.google.android.apps.maps" to "Google Maps",
        "org.telegram.messenger" to "Telegram",
        "com.android.settings" to "Settings"
    )

    fun resolveReadableAppName(packageName: String, fallbackName: String = ""): String {
        val pkg = packageName.trim()
        if (pkg.isBlank()) return fallbackName
        knownPackageAppNames[pkg]?.let { return it }
        val lower = pkg.lowercase(Locale.US)
        return when {
            lower.contains("youtube") -> "YouTube"
            lower.contains("whatsapp") -> "WhatsApp"
            lower.contains("instagram") -> "Instagram"
            lower.contains("spotify") -> "Spotify"
            lower.contains("chrome") -> "Chrome"
            lower.contains("gmail") || lower == "com.google.android.gm" -> "Gmail"
            lower.contains("maps") -> "Google Maps"
            lower.contains("launcher") || lower.contains("nexuslauncher") || lower.contains("trebuchet") || lower.contains("home") -> "Home Screen"
            fallbackName.isNotBlank() -> fallbackName
            else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase(Locale.US) }
        }
    }

    fun isLauncherOrSystemHomePackage(packageName: String): Boolean {
        val lower = packageName.trim().lowercase(Locale.US)
        if (lower.isBlank()) return false
        return lower.contains("launcher") ||
            lower.contains("trebuchet") ||
            lower.contains("quickstep") ||
            lower == "com.android.systemui" ||
            lower == "com.jarvisai.assistant" ||
            lower == "com.example"
    }

    fun recordUserTurn(utterance: String, detectedIntent: String = "") {
        val cleaned = utterance.trim()
        if (cleaned.isBlank()) return
        val intent = detectedIntent.ifBlank { inferIntentFromUtterance(cleaned) }
        currentUserIntent = intent
        val activeApp = _activeAppContext.value.activeAppName
        shortTermTurns.add(
            ShortTermTurn(
                role = "USER",
                text = cleaned,
                intent = intent,
                activeAppName = activeApp
            )
        )
        trimTurns()
        extractEntitiesFromUtterance(cleaned)
        Log.d(TAG, "Recorded USER turn: intent=$intent, activeApp=$activeApp")
    }

    fun recordAssistantTurn(reply: String) {
        val cleaned = reply.trim()
        if (cleaned.isBlank()) return
        shortTermTurns.add(
            ShortTermTurn(
                role = "ASSISTANT",
                text = cleaned,
                intent = currentUserIntent,
                activeAppName = _activeAppContext.value.activeAppName
            )
        )
        trimTurns()
    }

    fun recordUserUtterance(utterance: String, detectedIntent: String = "") {
        recordUserTurn(utterance, detectedIntent)
    }

    fun recordAssistantResponse(reply: String) {
        recordAssistantTurn(reply)
    }

    fun recordLastToolExecution(toolName: String, args: Map<String, String>) {
        if (toolName.isBlank()) return
        lastToolCallName = toolName
        lastToolCallArgs = args.toMap()
    }

    fun getLastToolExecution(): Pair<String, org.json.JSONObject>? {
        val name = lastToolCallName
        if (name.isBlank()) return null
        val json = org.json.JSONObject()
        lastToolCallArgs.forEach { (k, v) ->
            val intVal = v.toIntOrNull()
            if (intVal != null && k == "ordinal_index") {
                json.put(k, intVal)
            } else {
                json.put(k, v)
            }
        }
        return name to json
    }

    fun updateSearchQuery(query: String) {
        val cleaned = query.trim()
        if (cleaned.isNotBlank()) {
            lastSearchQuery = cleaned
            if (!relevantEntities.contains(cleaned)) {
                relevantEntities.add(cleaned)
                while (relevantEntities.size > 10) {
                    relevantEntities.removeAt(0)
                }
            }
        }
    }

    fun setPendingTask(task: String, state: String = "IN_PROGRESS") {
        pendingTask = task
        currentTaskState = state
        val cur = _activeAppContext.value
        _activeAppContext.value = cur.copy(
            currentTask = task,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    /**
     * Evaluates and records an app launch attempt using the 9-Step Action Verification Pipeline.
     * Never blindly claims an app opened if verification failed or was uncertain.
     */
    fun onAppLaunchEvaluated(
        appName: String,
        packageName: String,
        intentDispatched: Boolean,
        accessibilityVerified: Boolean,
        observedForegroundPkg: String
    ): ActionVerificationRecord = onAppLaunchEvaluated(
        targetAppName = appName,
        targetPackage = packageName,
        launchedOk = intentDispatched,
        observedPackageAfter = observedForegroundPkg,
        accessibilityEnabled = accessibilityVerified
    )

    fun onAppLaunchEvaluated(
        targetAppName: String,
        targetPackage: String,
        launchedOk: Boolean,
        observedPackageAfter: String?,
        accessibilityEnabled: Boolean
    ): ActionVerificationRecord {
        val cleanName = targetAppName.trim().ifBlank {
            resolveReadableAppName(targetPackage, "App")
        }
        val cleanPkg = targetPackage.trim()
        val observed = observedPackageAfter?.trim().orEmpty()

        val verifiedForeground: Boolean
        val verificationUncertain: Boolean

        if (!launchedOk) {
            verifiedForeground = false
            verificationUncertain = false
        } else if (!accessibilityEnabled || observed.isBlank()) {
            // Intent fired, but Accessibility cannot confirm the foreground window transitions
            verifiedForeground = false
            verificationUncertain = true
        } else {
            val matchesTarget = observed.equals(cleanPkg, ignoreCase = true) ||
                (cleanName.equals("YouTube", ignoreCase = true) && observed.contains("youtube", ignoreCase = true)) ||
                (cleanName.equals("WhatsApp", ignoreCase = true) && observed.contains("whatsapp", ignoreCase = true)) ||
                (cleanName.equals("Spotify", ignoreCase = true) && observed.contains("spotify", ignoreCase = true)) ||
                (cleanName.equals("Chrome", ignoreCase = true) && observed.contains("chrome", ignoreCase = true)) ||
                (cleanName.equals("Instagram", ignoreCase = true) && observed.contains("instagram", ignoreCase = true))

            if (matchesTarget) {
                verifiedForeground = true
                verificationUncertain = false
            } else if (observed == "com.jarvisai.assistant" || observed == "com.example") {
                // Transition may still be animating out of Jarvis
                verifiedForeground = false
                verificationUncertain = true
            } else {
                verifiedForeground = false
                verificationUncertain = false
            }
        }

        val hinglishReport = when {
            verifiedForeground -> "$cleanName open ho gaya hai"
            !launchedOk -> "$cleanName open nahi ho paya"
            verificationUncertain -> "$cleanName open karne ki koshish ki hai, lekin main verify nahi kar pa raha hoon"
            else -> "$cleanName open nahi ho paya"
        }

        val summary = when {
            verifiedForeground -> "$cleanName ($cleanPkg) launched and verified in foreground."
            !launchedOk -> "Failed to launch $cleanName ($cleanPkg)."
            verificationUncertain -> "Launch intent sent for $cleanName ($cleanPkg), but foreground verification is uncertain."
            else -> "Launch intent sent for $cleanName ($cleanPkg), but foreground package remained '$observed'."
        }

        val record = ActionVerificationRecord(
            actionName = "launch_application",
            targetAppName = cleanName,
            targetPackage = cleanPkg,
            executed = launchedOk,
            verifiedForeground = verifiedForeground,
            verificationUncertain = verificationUncertain,
            resultingPackage = observed.ifBlank { if (launchedOk) cleanPkg else "" },
            summary = summary,
            hinglishReport = hinglishReport
        )

        if (launchedOk && (verifiedForeground || verificationUncertain)) {
            val prev = _activeAppContext.value
            expectedTargetAppName = cleanName
            expectedTargetAppPackage = cleanPkg
            wasTargetAppExitedByBack = false
            exitedTargetAppName = ""
            currentTaskState = "APP_OPEN"
            _activeAppContext.value = ActiveAppContextSnapshot(
                activePackageName = cleanPkg,
                activeAppName = cleanName,
                activeWindowTitle = cleanName,
                previousPackageName = prev.activePackageName,
                previousAppName = prev.activeAppName,
                currentInteractionTarget = cleanName,
                currentTask = "Interacting with $cleanName",
                isVerifiedForeground = verifiedForeground,
                updatedAtMs = System.currentTimeMillis()
            )
            lastSuccessfulAction = record
        }

        Log.i(
            TAG,
            "AppLaunchEvaluated: app=$cleanName pkg=$cleanPkg launched=$launchedOk verified=$verifiedForeground uncertain=$verificationUncertain report='$hinglishReport'"
        )
        return record
    }

    /**
     * Synchronizes active app context from real Accessibility screen observation.
     */
    fun syncWithScreenSnapshot(snapshot: ScreenContextSnapshot) {
        val observedPkg = snapshot.activePackageName.trim()
        if (observedPkg.isBlank()) return
        // Ignore transient Jarvis overlay inspection if another target app is already tracked
        if ((observedPkg == "com.jarvisai.assistant" || observedPkg == "com.example") &&
            _activeAppContext.value.activePackageName.isNotBlank() &&
            !wasTargetAppExitedByBack
        ) {
            return
        }

        val cur = _activeAppContext.value
        val resolvedName = resolveReadableAppName(observedPkg, snapshot.windowTitle)
        val pkgChanged = !cur.activePackageName.equals(observedPkg, ignoreCase = true)

        if (pkgChanged && expectedTargetAppPackage.isNotBlank() &&
            !observedPkg.equals(expectedTargetAppPackage, ignoreCase = true) &&
            isLauncherOrSystemHomePackage(observedPkg)
        ) {
            wasTargetAppExitedByBack = true
            exitedTargetAppName = expectedTargetAppName.ifBlank { cur.activeAppName }
        } else if (!isLauncherOrSystemHomePackage(observedPkg)) {
            wasTargetAppExitedByBack = false
            exitedTargetAppName = ""
            expectedTargetAppName = resolvedName
            expectedTargetAppPackage = observedPkg
        }

        _activeAppContext.value = cur.copy(
            activePackageName = observedPkg,
            activeAppName = resolvedName,
            activeWindowTitle = snapshot.windowTitle.ifBlank { resolvedName },
            previousPackageName = if (pkgChanged && cur.activePackageName.isNotBlank()) cur.activePackageName else cur.previousPackageName,
            previousAppName = if (pkgChanged && cur.activeAppName.isNotBlank()) cur.activeAppName else cur.previousAppName,
            currentInteractionTarget = resolvedName,
            isVerifiedForeground = snapshot.serviceEnabled,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    /**
     * Explicitly sets the active foreground app context (e.g., when a specific app intent is launched or verified).
     */
    fun setActiveForegroundApp(
        appName: String,
        packageName: String,
        windowTitle: String = appName,
        screenName: String = "",
        currentTask: String = "",
        verified: Boolean = true,
        taskDescription: String = ""
    ) {
        val cur = _activeAppContext.value
        val cleanName = appName.trim().ifBlank { resolveReadableAppName(packageName, "App") }
        val cleanPkg = packageName.trim()
        expectedTargetAppName = cleanName
        expectedTargetAppPackage = cleanPkg
        wasTargetAppExitedByBack = false
        exitedTargetAppName = ""
        val effectiveTitle = screenName.ifBlank { windowTitle.ifBlank { cleanName } }
        val effectiveTask = currentTask.ifBlank { taskDescription.ifBlank { "Interacting with $cleanName" } }
        _activeAppContext.value = ActiveAppContextSnapshot(
            activePackageName = cleanPkg,
            activeAppName = cleanName,
            activeWindowTitle = effectiveTitle,
            previousPackageName = cur.activePackageName,
            previousAppName = cur.activeAppName,
            currentInteractionTarget = cleanName,
            currentTask = effectiveTask,
            isVerifiedForeground = verified,
            wasTargetAppExitedByBack = false,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    fun onBackNavigationExecuted(
        resultingPackageName: String,
        resultingScreenTitle: String = ""
    ): BackNavigationOutcome = onBackNavigationExecuted(
        resultingPackage = resultingPackageName,
        resultingWindowTitle = resultingScreenTitle,
        accessibilityEnabled = true
    )

    /**
     * Handles post-Back navigation state updates.
     * Detects if Back exited the active target app (e.g., YouTube -> Home Screen).
     */
    fun onBackNavigationExecuted(
        resultingPackage: String?,
        resultingWindowTitle: String?,
        accessibilityEnabled: Boolean
    ): BackNavigationOutcome {
        val cur = _activeAppContext.value
        val prevAppName = cur.activeAppName.ifBlank { expectedTargetAppName }
        val prevPkg = cur.activePackageName.ifBlank { expectedTargetAppPackage }
        val observedPkg = resultingPackage?.trim().orEmpty()

        val targetExited: Boolean
        val resultingName: String
        val uncertain: Boolean

        if (!accessibilityEnabled || observedPkg.isBlank()) {
            // When Accessibility cannot observe the post-Back package, if we were in an app, mark as exited/uncertain
            targetExited = prevAppName.isNotBlank() && prevAppName != "Home Screen"
            resultingName = if (targetExited) "Home Screen" else prevAppName
            uncertain = !accessibilityEnabled
        } else {
            resultingName = resolveReadableAppName(observedPkg, resultingWindowTitle.orEmpty())
            val stillInSameApp = prevPkg.isNotBlank() &&
                (observedPkg.equals(prevPkg, ignoreCase = true) ||
                    resultingName.equals(prevAppName, ignoreCase = true))
            targetExited = !stillInSameApp && prevAppName.isNotBlank() && prevAppName != "Home Screen"
            uncertain = false
        }

        if (targetExited) {
            wasTargetAppExitedByBack = true
            exitedTargetAppName = prevAppName
            currentTaskState = "EXITED_TARGET_APP"
            _activeAppContext.value = cur.copy(
                activePackageName = observedPkg.ifBlank { "com.android.launcher3" },
                activeAppName = resultingName.ifBlank { "Home Screen" },
                activeWindowTitle = resultingWindowTitle?.ifBlank { resultingName } ?: resultingName,
                previousPackageName = prevPkg,
                previousAppName = prevAppName,
                currentInteractionTarget = resultingName.ifBlank { "Home Screen" },
                currentTask = "Exited $prevAppName via Back navigation",
                isVerifiedForeground = accessibilityEnabled && observedPkg.isNotBlank(),
                wasTargetAppExitedByBack = true,
                updatedAtMs = System.currentTimeMillis()
            )
        } else if (observedPkg.isNotBlank()) {
            _activeAppContext.value = cur.copy(
                activePackageName = observedPkg,
                activeAppName = resultingName,
                activeWindowTitle = resultingWindowTitle?.ifBlank { resultingName } ?: resultingName,
                isVerifiedForeground = accessibilityEnabled,
                wasTargetAppExitedByBack = false,
                updatedAtMs = System.currentTimeMillis()
            )
        }

        val summary = when {
            targetExited -> "Navigated back. $prevAppName is no longer in the foreground (now on ${resultingName.ifBlank { "Home Screen" }})."
            prevAppName.isNotBlank() -> "Navigated back inside $prevAppName."
            else -> "Navigated back."
        }

        Log.i(TAG, "BackNavigationOutcome: prev=$prevAppName, resulting=$resultingName, targetExited=$targetExited")
        return BackNavigationOutcome(
            previousAppName = prevAppName,
            previousPackageName = prevPkg,
            resultingAppName = resultingName,
            resultingPackageName = observedPkg,
            targetAppExited = targetExited,
            verificationUncertain = uncertain,
            summaryMessage = summary
        )
    }

    /**
     * Checks if the user is issuing an ambiguous screen action (e.g. "Scroll down")
     * right after exiting the previous target app via Back navigation.
     */
    fun checkAmbiguousScreenActionAfterBack(actionVerb: String = "scroll"): String? {
        val cur = _activeAppContext.value
        if (wasTargetAppExitedByBack && exitedTargetAppName.isNotBlank()) {
            return "$exitedTargetAppName ab foreground mein nahi hai. Kis screen par $actionVerb karna hai?"
        }
        if (expectedTargetAppName.isNotBlank() &&
            cur.activeAppName.isNotBlank() &&
            !cur.activeAppName.equals(expectedTargetAppName, ignoreCase = true) &&
            isLauncherOrSystemHomePackage(cur.activePackageName)
        ) {
            return "$expectedTargetAppName ab foreground mein nahi hai. Kis screen par $actionVerb karna hai?"
        }
        return null
    }

    /**
     * Resolves short follow-up conversational commands against the current active app & session context.
     */
    fun resolveContextualFollowUp(rawCommand: String): ContextualCommandResolution {
        val text = rawCommand.trim()
        val lower = text.lowercase(Locale.US)
        val cur = _activeAppContext.value
        val activeApp = cur.activeAppName
        val activePkg = cur.activePackageName

        // 1. Repeat last action: "Do the same thing again", "Repeat that", "Wahi dobara karo"
        if (lower == "do the same thing again" ||
            lower == "repeat that" ||
            lower == "do it again" ||
            lower == "repeat last action" ||
            lower.contains("do the same thing again") ||
            lower.contains("wahi dobara karo") ||
            lower.contains("dobara karo")
        ) {
            if (lastToolCallName.isNotBlank()) {
                return ContextualCommandResolution(
                    type = ContextualFollowUpType.REPEAT_LAST_ACTION,
                    activeAppName = activeApp,
                    activePackageName = activePkg,
                    repeatToolName = lastToolCallName,
                    repeatToolArgs = lastToolCallArgs
                )
            }
        }

        // 2. Scroll follow-up: "Scroll down", "Scroll up", "Neeche scroll karo"
        val isScrollDown = lower == "scroll down" || lower == "scroll" ||
            lower == "neeche scroll karo" || lower == "neeche karo" || lower == "scroll niche"
        val isScrollUp = lower == "scroll up" || lower == "upar scroll karo" || lower == "scroll upar"
        if (isScrollDown || isScrollUp) {
            val mismatchPrompt = checkAmbiguousScreenActionAfterBack("scroll")
            if (mismatchPrompt != null) {
                return ContextualCommandResolution(
                    type = ContextualFollowUpType.CLARIFICATION_REQUIRED,
                    activeAppName = activeApp,
                    activePackageName = activePkg,
                    clarificationPrompt = mismatchPrompt
                )
            }
            return ContextualCommandResolution(
                type = ContextualFollowUpType.SCROLL_ACTIVE_SCREEN,
                activeAppName = activeApp,
                activePackageName = activePkg,
                scrollDirection = if (isScrollUp) "up" else "down"
            )
        }

        // 3. Back navigation follow-up: "Go back", "Back", "Peeche jao"
        if (lower == "go back" || lower == "back" || lower == "navigate back" ||
            lower == "peeche jao" || lower == "wapas jao"
        ) {
            return ContextualCommandResolution(
                type = ContextualFollowUpType.NAVIGATE_BACK,
                activeAppName = activeApp,
                activePackageName = activePkg
            )
        }

        // 4. Ordinal item selection: "Open the first one", "Open the second video", "Play this", "Click the third result"
        val ordinalMatch = parseOrdinalSelectionCommand(lower)
        if (ordinalMatch != null) {
            val mismatchPrompt = checkAmbiguousScreenActionAfterBack("click")
            if (mismatchPrompt != null) {
                return ContextualCommandResolution(
                    type = ContextualFollowUpType.CLARIFICATION_REQUIRED,
                    activeAppName = activeApp,
                    activePackageName = activePkg,
                    clarificationPrompt = mismatchPrompt
                )
            }
            return ContextualCommandResolution(
                type = ContextualFollowUpType.SELECT_ORDINAL_ITEM,
                activeAppName = activeApp,
                activePackageName = activePkg,
                ordinalIndex = ordinalMatch.first,
                ordinalCategoryHint = ordinalMatch.second
            )
        }

        // 5. Short contextual search: "Search for a song", "Search <query>", "Search it"
        // Only intercept when user does NOT explicitly specify another engine like "on google" or "in chrome"
        if (!lower.contains("on google") && !lower.contains("in chrome") && !lower.contains("on web")) {
            val extractedQuery = extractContextualSearchQuery(text, lower)
            if (extractedQuery != null) {
                val queryToUse = extractedQuery.ifBlank { lastSearchQuery.ifBlank { "trending songs" } }
                updateSearchQuery(queryToUse)
                val isYouTubeActive = !wasTargetAppExitedByBack && (
                    activeApp.equals("YouTube", ignoreCase = true) ||
                        activePkg.contains("youtube", ignoreCase = true) ||
                        expectedTargetAppName.equals("YouTube", ignoreCase = true)
                    )
                val isSpotifyActive = !wasTargetAppExitedByBack && (
                    activeApp.equals("Spotify", ignoreCase = true) ||
                        activePkg.contains("spotify", ignoreCase = true) ||
                        expectedTargetAppName.equals("Spotify", ignoreCase = true)
                    )

                if (isYouTubeActive) {
                    return ContextualCommandResolution(
                        type = ContextualFollowUpType.IN_APP_YOUTUBE_SEARCH,
                        activeAppName = "YouTube",
                        activePackageName = activePkg.ifBlank { "com.google.android.youtube" },
                        searchQuery = queryToUse
                    )
                }
                if (isSpotifyActive) {
                    return ContextualCommandResolution(
                        type = ContextualFollowUpType.IN_APP_SPOTIFY_SEARCH,
                        activeAppName = "Spotify",
                        activePackageName = activePkg.ifBlank { "com.spotify.music" },
                        searchQuery = queryToUse
                    )
                }
                if (!wasTargetAppExitedByBack && activeApp.isNotBlank() && !isLauncherOrSystemHomePackage(activePkg)) {
                    return ContextualCommandResolution(
                        type = ContextualFollowUpType.IN_APP_GENERIC_SEARCH,
                        activeAppName = activeApp,
                        activePackageName = activePkg,
                        searchQuery = queryToUse
                    )
                }
            }
        }

        return ContextualCommandResolution(type = ContextualFollowUpType.NONE)
    }

    /**
     * Parses ordinal index (1-based) and category hint from expressions like:
     * "open the first one", "open the second video", "play the 2nd song", "play this", "pehla video chala do"
     */
    fun parseOrdinalSelectionCommand(lowerCommand: String): Pair<Int, String>? {
        val lower = lowerCommand.trim()
        if (lower == "play this" || lower == "open this" || lower == "click this" ||
            lower == "ise chala do" || lower == "ye wala open karo"
        ) {
            return 1 to "item"
        }

        val actionPrefix = lower.startsWith("open ") ||
            lower.startsWith("play ") ||
            lower.startsWith("click ") ||
            lower.startsWith("select ") ||
            lower.startsWith("tap ") ||
            lower.contains(" open karo") ||
            lower.contains(" chala do") ||
            lower.contains(" click karo")

        if (!actionPrefix) return null

        val ordinalIndex = when {
            Regex("\\b(first|1st|one|pehla|pehle|1)\\b").containsMatchIn(lower) &&
                (lower.contains("first") || lower.contains("1st") || lower.contains("the one") || lower.contains("first one") || lower.contains("pehla") || lower.contains("pehle")) -> 1
            Regex("\\b(second|2nd|doosra|dusra)\\b").containsMatchIn(lower) -> 2
            Regex("\\b(third|3rd|teesra|tisra)\\b").containsMatchIn(lower) -> 3
            Regex("\\b(fourth|4th|chautha)\\b").containsMatchIn(lower) -> 4
            Regex("\\b(fifth|5th|panchwa)\\b").containsMatchIn(lower) -> 5
            else -> null
        } ?: return null

        val categoryHint = when {
            lower.contains("video") -> "video"
            lower.contains("song") || lower.contains("music") || lower.contains("track") -> "song"
            lower.contains("result") || lower.contains("link") -> "result"
            else -> "item"
        }

        return ordinalIndex to categoryHint
    }

    private fun extractContextualSearchQuery(original: String, lower: String): String? {
        if (lower == "search it" || lower == "search that" || lower == "isse search karo") {
            return lastSearchQuery.ifBlank {
                relevantEntities.lastOrNull().orEmpty()
            }
        }
        val prefixes = listOf(
            "search for ",
            "search ",
            "find ",
            "look up ",
            "play song "
        )
        for (prefix in prefixes) {
            if (lower.startsWith(prefix)) {
                val remainder = original.substring(prefix.length).trim()
                if (remainder.isNotBlank()) {
                    val cleaned = remainder
                        .replace(Regex("^(a|an)\\s+", RegexOption.IGNORE_CASE), "")
                        .trim()
                    return cleaned.ifBlank { remainder }
                }
            }
        }
        return null
    }

    private fun inferIntentFromUtterance(text: String): String {
        val lower = text.lowercase(Locale.US)
        return when {
            lower.startsWith("open ") || lower.startsWith("launch ") -> "OPEN_APP"
            lower.startsWith("search ") || lower.contains("search for") -> "SEARCH"
            lower.contains("scroll") -> "SCROLL_SCREEN"
            lower == "go back" || lower == "back" || lower.contains("peeche") -> "NAVIGATE_BACK"
            parseOrdinalSelectionCommand(lower) != null -> "SELECT_ORDINAL_ITEM"
            lower.contains("call ") -> "PHONE_CALL"
            lower.contains("whatsapp") || lower.contains("message") -> "SEND_MESSAGE"
            else -> "CONVERSATION"
        }
    }

    private fun extractEntitiesFromUtterance(text: String) {
        val cleaned = text.trim()
        if (cleaned.length in 3..80) {
            val lower = cleaned.lowercase(Locale.US)
            if (lower.startsWith("search for ")) {
                updateSearchQuery(cleaned.substring("search for ".length).trim())
            } else if (lower.startsWith("search ")) {
                updateSearchQuery(cleaned.substring("search ".length).trim())
            }
        }
    }

    private fun trimTurns() {
        while (shortTermTurns.size > MAX_SHORT_TERM_TURNS) {
            shortTermTurns.removeAt(0)
        }
    }

    fun getRecentTurns(limit: Int = 10): List<ShortTermTurn> {
        val snapshot = shortTermTurns.toList()
        return if (snapshot.size <= limit) snapshot else snapshot.takeLast(limit)
    }

    /**
     * Builds a compact, structured context block injected into Gemini Live setup/reconnection
     * and text chat so the model always knows the active foreground app, screen state, and
     * recent short-term conversation history.
     */
    fun buildSessionContextPromptBlock(): String {
        val cur = _activeAppContext.value
        val turns = getRecentTurns(8)
        val sb = StringBuilder()
        sb.appendLine("\n[ACTIVE SESSION & APP CONTEXT MEMORY]")
        if (cur.activeAppName.isNotBlank()) {
            sb.appendLine("- Active Foreground App: ${cur.activeAppName} (package: ${cur.activePackageName}, verified=${cur.isVerifiedForeground})")
        } else {
            sb.appendLine("- Active Foreground App: None / Home Screen")
        }
        if (cur.previousAppName.isNotBlank()) {
            sb.appendLine("- Previous Foreground App: ${cur.previousAppName} (${cur.previousPackageName})")
        }
        if (wasTargetAppExitedByBack && exitedTargetAppName.isNotBlank()) {
            sb.appendLine("- Navigation Alert: User pressed Back and exited $exitedTargetAppName. $exitedTargetAppName is NO LONGER in the foreground. If user asks to scroll or click without naming an app, ask: \"$exitedTargetAppName ab foreground mein nahi hai. Kis screen par scroll karna hai?\"")
        }
        if (currentUserIntent.isNotBlank()) {
            sb.appendLine("- Current User Intent: $currentUserIntent")
        }
        if (lastSearchQuery.isNotBlank()) {
            sb.appendLine("- Last Search Query / Entity: $lastSearchQuery")
        }
        lastSuccessfulAction?.let { last ->
            sb.appendLine("- Last Verified Action: ${last.actionName} on ${last.targetAppName} -> ${last.hinglishReport}")
        }
        sb.appendLine("- Follow-Up Context Rule: If an app like YouTube or Spotify is currently active in the foreground and the user says a short follow-up like 'Search for a song', 'Search it', 'Open the first one', 'Open the second video', 'Scroll down', or 'Go back', execute the action INSIDE the active app (${cur.activeAppName.ifBlank { "current app" }}) instead of defaulting to Google web search.")
        sb.appendLine("- Action Verification Rule: Never claim an app opened or a button was clicked unless verified by the tool output. Use truthful Hinglish status like 'YouTube open ho gaya hai', 'YouTube open nahi ho paya', or 'YouTube open karne ki koshish ki hai, lekin main verify nahi kar pa raha hoon'.")

        if (turns.isNotEmpty()) {
            sb.appendLine("\n[RECENT SHORT-TERM CONVERSATION HISTORY]")
            turns.forEach { turn ->
                val appTag = if (turn.activeAppName.isNotBlank()) " [App: ${turn.activeAppName}]" else ""
                sb.appendLine("${turn.role}$appTag: ${turn.text}")
            }
        }
        return sb.toString().trimEnd()
    }

    fun resetSession() {
        resetForTest()
    }

    fun resetForTest() {
        shortTermTurns.clear()
        relevantEntities.clear()
        currentUserIntent = ""
        lastSearchQuery = ""
        pendingTask = ""
        currentTaskState = "IDLE"
        lastSuccessfulAction = null
        lastToolCallName = ""
        lastToolCallArgs = emptyMap()
        expectedTargetAppName = ""
        expectedTargetAppPackage = ""
        wasTargetAppExitedByBack = false
        exitedTargetAppName = ""
        _activeAppContext.value = ActiveAppContextSnapshot()
    }
}
