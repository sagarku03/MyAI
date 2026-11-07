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
    private val _shortTermTurnsFlow = MutableStateFlow<List<ShortTermTurn>>(emptyList())
    val shortTermTurnsFlow: StateFlow<List<ShortTermTurn>> = _shortTermTurnsFlow.asStateFlow()

    private val _entityFocusStackFlow = MutableStateFlow(com.jarvisai.assistant.model.ActiveEntityFocusStack())
    val entityFocusStackFlow: StateFlow<com.jarvisai.assistant.model.ActiveEntityFocusStack> = _entityFocusStackFlow.asStateFlow()

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
            lower.contains("quickstep")
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
        // Ignore transient Jarvis overlay or SystemUI (volume/status bar) if another target app is already tracked
        if ((observedPkg == "com.jarvisai.assistant" ||
                observedPkg == "com.example" ||
                observedPkg == "com.android.systemui") &&
            _activeAppContext.value.activePackageName.isNotBlank() &&
            !wasTargetAppExitedByBack
        ) {
            return
        }

        val cur = _activeAppContext.value
        val resolvedName = resolveReadableAppName(observedPkg, snapshot.windowTitle)
        val pkgChanged = !cur.activePackageName.equals(observedPkg, ignoreCase = true)

        if (!isLauncherOrSystemHomePackage(observedPkg)) {
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
        val lower = cleaned.lowercase(Locale.US)
        if (cleaned.length in 3..80) {
            if (lower.startsWith("search for ")) {
                updateSearchQuery(cleaned.substring("search for ".length).trim())
            } else if (lower.startsWith("search ")) {
                updateSearchQuery(cleaned.substring("search ".length).trim())
            }
        }

        // Extract focused App Name if mentioned
        val appMentions = listOf(
            "youtube" to "YouTube",
            "whatsapp" to "WhatsApp",
            "instagram" to "Instagram",
            "spotify" to "Spotify",
            "chrome" to "Chrome",
            "gmail" to "Gmail",
            "maps" to "Google Maps",
            "telegram" to "Telegram",
            "amazon" to "Amazon",
            "flipkart" to "Flipkart",
            "swiggy" to "Swiggy",
            "zomato" to "Zomato",
            "settings" to "Settings"
        )
        appMentions.firstOrNull { lower.contains(it.first) }?.let { (_, canonicalApp) ->
            updateFocusedApp(canonicalApp)
        }

        // Extract focused Person Name if mentioned in call/message/name contexts
        val personPatterns = listOf(
            Regex("(?i)\\b([A-Za-z]{2,18})\\s+ko\\s+(?:whatsapp|call|message|msg|bhej|bol|phone|text)"),
            Regex("(?i)\\b([A-Za-z]{2,18})\\s+ko\\b"),
            Regex("(?i)(?:mera\\s+bhai|my\\s+brother|mera\\s+dost|my\\s+friend|papa|mummy)\\s+([A-Za-z]{2,18})\\b"),
            Regex("(?i)(?:call|message|msg|text)\\s+(?:to\\s+)?([A-Za-z]{2,18})\\b")
        )
        val stopWords = setOf(
            "jarvis", "whatsapp", "youtube", "instagram", "chrome", "spotify", "google",
            "please", "kholo", "open", "karo", "usko", "unhe", "usse", "woh", "wahan", "him", "her",
            "par", "pe", "mein", "me", "se", "ko", "ka", "ki", "ke", "bhejo", "isko", "ise", "kis", "kisi"
        )
        for (pat in personPatterns) {
            val candidate = pat.find(cleaned)?.groupValues?.getOrNull(1)?.trim()
            if (!candidate.isNullOrBlank() && candidate.lowercase(Locale.US) !in stopWords) {
                updateFocusedPerson(candidate)
                break
            }
        }
    }

    fun getActiveEntityFocusStack(): com.jarvisai.assistant.model.ActiveEntityFocusStack {
        return _entityFocusStackFlow.value
    }

    fun updateFocusedPerson(personName: String) {
        val clean = personName.trim()
        if (clean.isEmpty()) return
        _entityFocusStackFlow.value = _entityFocusStackFlow.value.copy(lastMentionedPerson = clean)
    }

    fun updateFocusedApp(appName: String) {
        val clean = appName.trim()
        if (clean.isEmpty()) return
        _entityFocusStackFlow.value = _entityFocusStackFlow.value.copy(lastMentionedApp = clean)
    }

    fun updateFocusedQueryOrItem(queryOrItem: String) {
        val clean = queryOrItem.trim()
        if (clean.isEmpty()) return
        _entityFocusStackFlow.value = _entityFocusStackFlow.value.copy(lastMentionedQueryOrItem = clean)
    }

    /**
     * Tier 1 STM Pronoun & Anaphora Resolver for Hindi, Hinglish, and English:
     * Resolves "usko", "unhe", "usse", "usey", "him", "her" -> lastMentionedPerson
     * Resolves "wahan", "us app mein", "usme", "there" -> lastMentionedApp / activeAppName
     * Resolves "woh", "isko", "ise", "ye wala", "that" -> lastMentionedQueryOrItem / lastSearchQuery
     */
    fun resolvePronounsAndAnaphora(utterance: String): String {
        val clean = utterance.trim()
        if (clean.isEmpty()) return clean

        val focus = _entityFocusStackFlow.value
        val activeApp = _activeAppContext.value.activeAppName.ifBlank { focus.lastMentionedApp }
        val activeQuery = focus.lastMentionedQueryOrItem.ifBlank { lastSearchQuery }
        val resolutions = mutableListOf<String>()

        val lower = clean.lowercase(Locale.US)
        if (focus.lastMentionedPerson.isNotBlank() && !clean.contains(focus.lastMentionedPerson, ignoreCase = true)) {
            val hasPersonPronoun = Regex("\\b(usko|unhe|unko|usse|usey|usase|him|her)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(lower)
            if (hasPersonPronoun) {
                resolutions.add("Person=${focus.lastMentionedPerson}")
            }
        }

        if (activeApp.isNotBlank() && !clean.contains(activeApp, ignoreCase = true)) {
            val hasAppPronoun = Regex("\\b(wahan|waha|us\\s+app\\s+mein|us\\s+app\\s+me|usme|usmein|in\\s+that\\s+app|there)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(lower)
            if (hasAppPronoun) {
                resolutions.add("App=$activeApp")
            }
        }

        if (activeQuery.isNotBlank() && !clean.contains(activeQuery, ignoreCase = true)) {
            val hasItemPronoun = Regex("\\b(woh\\s+wala|wo\\s+wala|isko|ise|isey|ye\\s+wala|that\\s+one)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(lower)
            if (hasItemPronoun) {
                resolutions.add("Topic=$activeQuery")
            }
        }

        return if (resolutions.isEmpty()) {
            clean
        } else {
            "$clean [STM Context: ${resolutions.joinToString(", ")}]"
        }
    }

    /**
     * Directly substitutes Hindi/Hinglish/English pronouns ("usko", "unhe", "usse", "wahan", "him", "her")
     * inside an executable command string so PhoneActionManager can execute follow-up commands like
     * "Usko call karo" -> "Rahul ko call karo" or "Usko WhatsApp par Hello bhejo" -> "Rahul ko WhatsApp par Hello bhejo".
     */
    fun rewritePronounsInCommand(utterance: String): String {
        var rewritten = utterance.trim()
        if (rewritten.isEmpty()) return rewritten

        val focus = _entityFocusStackFlow.value
        val activeApp = _activeAppContext.value.activeAppName.ifBlank { focus.lastMentionedApp }

        if (focus.lastMentionedPerson.isNotBlank() && !rewritten.contains(focus.lastMentionedPerson, ignoreCase = true)) {
            val person = focus.lastMentionedPerson
            rewritten = rewritten
                .replace(Regex("(?i)\\b(usko|unhe|unko|usse|usey|usase)\\b"), "$person ko")
                .replace(Regex("(?i)\\b(call|message|msg|text|whatsapp)\\s+(him|her)\\b")) { m ->
                    "${m.groupValues[1]} $person"
                }
        }

        if (activeApp.isNotBlank() && !rewritten.contains(activeApp, ignoreCase = true)) {
            rewritten = rewritten.replace(
                Regex("(?i)\\b(wahan|waha|us\\s+app\\s+mein|us\\s+app\\s+me|usme|usmein)\\b"),
                "$activeApp par"
            )
        }

        return rewritten.replace(Regex("\\s+"), " ").trim()
    }

    private fun trimTurns() {
        val evicted = mutableListOf<ShortTermTurn>()
        while (shortTermTurns.size > MAX_SHORT_TERM_TURNS) {
            evicted.add(shortTermTurns.removeAt(0))
        }
        if (evicted.isNotEmpty()) {
            val snippet = evicted.joinToString(" | ") { "${it.role}: ${it.text.take(40)}" }
            val prevSummary = _entityFocusStackFlow.value.rollingSessionSummary
            val combined = if (prevSummary.isBlank()) snippet else "$prevSummary | $snippet"
            _entityFocusStackFlow.value = _entityFocusStackFlow.value.copy(
                rollingSessionSummary = combined.takeLast(320)
            )
        }
        _shortTermTurnsFlow.value = shortTermTurns.toList()
        if (lastSearchQuery.isNotBlank() && _entityFocusStackFlow.value.lastMentionedQueryOrItem != lastSearchQuery) {
            _entityFocusStackFlow.value = _entityFocusStackFlow.value.copy(lastMentionedQueryOrItem = lastSearchQuery)
        }
    }

    fun getRecentTurns(limit: Int = 10): List<ShortTermTurn> {
        val snapshot = shortTermTurns.toList()
        return if (snapshot.size <= limit) snapshot else snapshot.takeLast(limit)
    }

    fun clearShortTermMemory() {
        shortTermTurns.clear()
        _shortTermTurnsFlow.value = emptyList()
        _entityFocusStackFlow.value = com.jarvisai.assistant.model.ActiveEntityFocusStack()
    }

    /**
     * Builds a compact, structured context block injected into Gemini Live setup/reconnection
     * and text chat so the model always knows the active foreground app, screen state, and
     * recent short-term conversation history.
     */
    fun buildSessionContextPromptBlock(): String {
        val cur = _activeAppContext.value
        val focus = _entityFocusStackFlow.value
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
        if (focus.lastMentionedPerson.isNotBlank() || focus.lastMentionedApp.isNotBlank() || focus.lastMentionedQueryOrItem.isNotBlank()) {
            sb.appendLine("- Active Entity Focus (Pronoun Resolution): Person='${focus.lastMentionedPerson.ifBlank { "none" }}', App='${focus.lastMentionedApp.ifBlank { "none" }}', Topic='${focus.lastMentionedQueryOrItem.ifBlank { "none" }}'")
        }
        if (focus.rollingSessionSummary.isNotBlank()) {
            sb.appendLine("- Rolling Session Summary: ${focus.rollingSessionSummary}")
        }
        lastSuccessfulAction?.let { last ->
            sb.appendLine("- Last Verified Action: ${last.actionName} on ${last.targetAppName} -> ${last.hinglishReport}")
        }
        val wmBlock = WorkingMemoryBlackboard.buildWorkingMemoryPromptBlock()
        if (wmBlock.isNotBlank()) {
            sb.appendLine(wmBlock)
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
        _shortTermTurnsFlow.value = emptyList()
        _entityFocusStackFlow.value = com.jarvisai.assistant.model.ActiveEntityFocusStack()
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
