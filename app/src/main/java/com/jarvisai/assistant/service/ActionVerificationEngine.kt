package com.jarvisai.assistant.service

import android.content.Context
import android.util.Log
import com.jarvisai.assistant.model.ActionVerificationRecord
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Capability-aware result statuses for Android System Control & Action Execution (Section 3.3).
 */
enum class CapabilityResultStatus {
    SUCCESS,
    PARTIAL_SUCCESS,
    REQUIRES_PERMISSION,
    REQUIRES_USER_ACTION,
    UNSUPPORTED,
    FAILED
}

/**
 * Execution status for an Android action (Section 7.2).
 */
enum class ActionExecutionStatus {
    ACTION_EXECUTED,
    ACTION_FAILED
}

/**
 * Action-specific verification status (Section 7.3).
 */
enum class ActionVerificationStatus {
    ACTION_VERIFIED,
    ACTION_EXECUTED,
    ACTION_EXECUTED_VERIFICATION_UNAVAILABLE,
    ACTION_FAILED;

    companion object {
        val VERIFIED: ActionVerificationStatus
            get() = ACTION_VERIFIED
    }
}

/**
 * Centralized subsystem error model (Section 15.1).
 */
data class JarvisSubsystemError(
    val subsystem: String,
    val errorCode: String,
    val errorMessage: String,
    val technicalCause: String,
    val recoveryRecommendation: String,
    val retryEligible: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
    val userFriendlyMessage: String
        get() = errorMessage

    fun toJson(): JSONObject = JSONObject().apply {
        put("subsystem", subsystem)
        put("error_code", errorCode)
        put("error_message", errorMessage)
        put("technical_cause", technicalCause)
        put("recovery_recommendation", recoveryRecommendation)
        put("retry_eligible", retryEligible)
        put("timestamp", timestamp)
    }
}

/**
 * Structured verification report for any system, accessibility, or app action (Section 7.2).
 */
data class VerifiedActionReport(
    val actionId: String = "act_${UUID.randomUUID().toString().take(8)}",
    val actionType: String,
    val targetApplication: String = "",
    val targetElement: String = "",
    val executionStatus: ActionExecutionStatus,
    val verificationStatus: ActionVerificationStatus,
    val capabilityStatus: CapabilityResultStatus,
    val userMessage: String,
    val hinglishMessage: String,
    val errorInfo: JarvisSubsystemError? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val timeoutMs: Long = 3500L
) {
    val executed: Boolean
        get() = executionStatus == ActionExecutionStatus.ACTION_EXECUTED

    val verified: Boolean
        get() = verificationStatus == ActionVerificationStatus.ACTION_VERIFIED ||
            verificationStatus == ActionVerificationStatus.ACTION_EXECUTED

    fun toJson(): JSONObject = JSONObject().apply {
        put("action_id", actionId)
        put("action_type", actionType)
        put("target_application", targetApplication)
        put("target_element", targetElement)
        put("execution_status", executionStatus.name)
        put("verification_status", verificationStatus.name)
        put("capability_status", capabilityStatus.name)
        put("user_message", userMessage)
        put("hinglish_message", hinglishMessage)
        put("timestamp", timestamp)
        put("timeout_ms", timeoutMs)
        errorInfo?.let { put("error_info", it.toJson()) }
    }
}

/**
 * Structured diagnostic logger with automatic secret redaction (Section 15.2).
 */
object JarvisDiagnosticLogger {
    private const val TAG = "JarvisDiagnostics"
    private const val MAX_LOG_ENTRIES = 100

    private val recentErrors = CopyOnWriteArrayList<JarvisSubsystemError>()
    private val _latestErrorFlow = MutableStateFlow<JarvisSubsystemError?>(null)
    val latestErrorFlow: StateFlow<JarvisSubsystemError?> = _latestErrorFlow.asStateFlow()

    fun logInfo(subsystem: String, message: String) {
        val safeMsg = redactSecrets(message)
        Log.i(TAG, "[$subsystem] $safeMsg")
    }

    fun logWarning(subsystem: String, message: String) {
        val safeMsg = redactSecrets(message)
        Log.w(TAG, "[$subsystem] $safeMsg")
    }

    fun recordError(
        subsystem: String,
        errorCode: String,
        errorMessage: String,
        technicalCause: String,
        recoveryRecommendation: String,
        retryEligible: Boolean = false
    ): JarvisSubsystemError {
        val error = JarvisSubsystemError(
            subsystem = subsystem,
            errorCode = errorCode,
            errorMessage = redactSecrets(errorMessage),
            technicalCause = redactSecrets(technicalCause),
            recoveryRecommendation = recoveryRecommendation,
            retryEligible = retryEligible
        )
        recentErrors.add(error)
        while (recentErrors.size > MAX_LOG_ENTRIES) {
            recentErrors.removeAt(0)
        }
        _latestErrorFlow.value = error
        Log.e(
            TAG,
            "[${error.subsystem}] code=${error.errorCode} msg='${error.errorMessage}' cause='${error.technicalCause}' recovery='${error.recoveryRecommendation}'"
        )
        return error
    }

    fun getRecentErrors(limit: Int = 25): List<JarvisSubsystemError> {
        val list = recentErrors.toList()
        return if (list.size <= limit) list else list.takeLast(limit)
    }

    fun clearErrors() {
        recentErrors.clear()
        _latestErrorFlow.value = null
    }

    internal fun redactSecrets(input: String): String {
        if (input.isBlank()) return input
        return input
            .replace(Regex("AIza[0-9A-Za-z\\-_]{20,}"), "[REDACTED_GEMINI_KEY]")
            .replace(Regex("(?i)(key|api_key|apikey|token)=([^&\\s]+)"), "$1=[REDACTED]")
    }
}

/**
 * Centralized ActionVerificationEngine (Section 7).
 *
 * Performs action-specific verification for app launches, system settings toggles,
 * volume/brightness/flashlight changes, Accessibility scrolling/clicking/typing, and WhatsApp flows.
 *
 * Eliminates false generic "I cannot verify" responses when an action was deterministically
 * executed via Android APIs, while accurately distinguishing:
 * - [ActionVerificationStatus.ACTION_VERIFIED]
 * - [ActionVerificationStatus.ACTION_EXECUTED]
 * - [ActionVerificationStatus.ACTION_EXECUTED_VERIFICATION_UNAVAILABLE]
 * - [ActionVerificationStatus.ACTION_FAILED]
 */
object ActionVerificationEngine {

    private val verificationHistory = CopyOnWriteArrayList<VerifiedActionReport>()
    private const val MAX_HISTORY = 50

    /**
     * Verifies an application launch (e.g., Open YouTube, Open WhatsApp, Open Chrome, Open Settings).
     */
    fun verifyAppLaunch(
        appName: String,
        packageName: String,
        packageResolved: Boolean,
        intentStarted: Boolean,
        accessibilityEnabled: Boolean,
        observedForegroundPackage: String? = null,
        usedBrowserFallback: Boolean = false,
        strictForegroundMatchRequired: Boolean = false
    ): VerifiedActionReport {
        val cleanName = appName.trim().ifBlank {
            ConversationContextManager.resolveReadableAppName(packageName, "App")
        }
        val cleanPkg = packageName.trim()
        val observed = observedForegroundPackage?.trim().orEmpty()

        if (!intentStarted) {
            val err = JarvisDiagnosticLogger.recordError(
                subsystem = "APP_LAUNCH",
                errorCode = if (!packageResolved) "APP_NOT_INSTALLED" else "ACTIVITY_START_FAILED",
                errorMessage = "Could not launch $cleanName.",
                technicalCause = if (!packageResolved) "Package '$cleanPkg' not found on device" else "startActivity failed for '$cleanPkg'",
                recoveryRecommendation = "Verify that $cleanName is installed on your device.",
                retryEligible = false
            )
            return recordReport(
                VerifiedActionReport(
                    actionType = "OPEN_APP",
                    targetApplication = cleanName,
                    targetElement = cleanPkg,
                    executionStatus = ActionExecutionStatus.ACTION_FAILED,
                    verificationStatus = ActionVerificationStatus.ACTION_FAILED,
                    capabilityStatus = CapabilityResultStatus.FAILED,
                    userMessage = "Failed to open $cleanName.",
                    hinglishMessage = "$cleanName open nahi ho paya.",
                    errorInfo = err
                )
            )
        }

        val matchesObservedForeground = observed.isNotBlank() && (
            observed.equals(cleanPkg, ignoreCase = true) ||
                (cleanName.equals("YouTube", ignoreCase = true) && observed.contains("youtube", ignoreCase = true)) ||
                (cleanName.equals("WhatsApp", ignoreCase = true) && observed.contains("whatsapp", ignoreCase = true)) ||
                (cleanName.equals("Chrome", ignoreCase = true) && observed.contains("chrome", ignoreCase = true)) ||
                (cleanName.equals("Spotify", ignoreCase = true) && observed.contains("spotify", ignoreCase = true)) ||
                (cleanName.equals("Instagram", ignoreCase = true) && observed.contains("instagram", ignoreCase = true)) ||
                (cleanName.contains("Settings", ignoreCase = true) && observed.contains("settings", ignoreCase = true))
            )

        val verificationStatus = when {
            matchesObservedForeground -> ActionVerificationStatus.ACTION_VERIFIED
            accessibilityEnabled && observed.isNotBlank() &&
                observed != "com.jarvisai.assistant" &&
                observed != "com.example" &&
                strictForegroundMatchRequired -> ActionVerificationStatus.ACTION_FAILED
            !accessibilityEnabled && strictForegroundMatchRequired -> ActionVerificationStatus.ACTION_EXECUTED_VERIFICATION_UNAVAILABLE
            packageResolved && !usedBrowserFallback -> ActionVerificationStatus.ACTION_VERIFIED
            else -> ActionVerificationStatus.ACTION_EXECUTED
        }

        val capabilityStatus = when {
            verificationStatus == ActionVerificationStatus.ACTION_FAILED -> CapabilityResultStatus.FAILED
            usedBrowserFallback -> CapabilityResultStatus.PARTIAL_SUCCESS
            else -> CapabilityResultStatus.SUCCESS
        }

        val hinglishMsg = when {
            verificationStatus == ActionVerificationStatus.ACTION_FAILED ->
                "$cleanName open nahi ho paya."
            usedBrowserFallback ->
                "$cleanName app phone mein installed nahi mila, isliye browser mein $cleanName open kar diya hai."
            verificationStatus == ActionVerificationStatus.ACTION_EXECUTED_VERIFICATION_UNAVAILABLE ->
                "$cleanName open karne ki koshish ki hai, lekin main verify nahi kar pa raha hoon."
            else ->
                "$cleanName open ho gaya hai."
        }

        val userMsg = when {
            verificationStatus == ActionVerificationStatus.ACTION_FAILED ->
                "Launch intent sent for $cleanName ($cleanPkg), but foreground remained '$observed'."
            usedBrowserFallback ->
                "Opened $cleanName in web browser fallback."
            matchesObservedForeground ->
                "$cleanName ($cleanPkg) launched and verified in foreground."
            else ->
                "Successfully launched $cleanName ($cleanPkg)."
        }

        return recordReport(
            VerifiedActionReport(
                actionType = "OPEN_APP",
                targetApplication = cleanName,
                targetElement = cleanPkg,
                executionStatus = if (verificationStatus == ActionVerificationStatus.ACTION_FAILED) {
                    ActionExecutionStatus.ACTION_FAILED
                } else {
                    ActionExecutionStatus.ACTION_EXECUTED
                },
                verificationStatus = verificationStatus,
                capabilityStatus = capabilityStatus,
                userMessage = userMsg,
                hinglishMessage = hinglishMsg
            )
        )
    }

    /**
     * Verifies a hardware/system control action (Bluetooth, Wi-Fi, Hotspot, Mobile Data, Volume, Brightness, Flashlight).
     */
    fun verifySystemControlAction(
        controlType: String,
        targetStateDescription: String,
        capabilityStatus: CapabilityResultStatus,
        stateVerifiedByApi: Boolean,
        userMessage: String,
        hinglishMessage: String,
        errorInfo: JarvisSubsystemError? = null
    ): VerifiedActionReport {
        val executed = capabilityStatus == CapabilityResultStatus.SUCCESS ||
            capabilityStatus == CapabilityResultStatus.PARTIAL_SUCCESS ||
            capabilityStatus == CapabilityResultStatus.REQUIRES_USER_ACTION

        val verificationStatus = when {
            !executed -> ActionVerificationStatus.ACTION_FAILED
            stateVerifiedByApi && capabilityStatus == CapabilityResultStatus.SUCCESS ->
                ActionVerificationStatus.ACTION_VERIFIED
            capabilityStatus == CapabilityResultStatus.PARTIAL_SUCCESS ||
                capabilityStatus == CapabilityResultStatus.REQUIRES_USER_ACTION ->
                ActionVerificationStatus.ACTION_EXECUTED
            else -> ActionVerificationStatus.ACTION_EXECUTED_VERIFICATION_UNAVAILABLE
        }

        return recordReport(
            VerifiedActionReport(
                actionType = controlType,
                targetApplication = "Android System",
                targetElement = targetStateDescription,
                executionStatus = if (executed) ActionExecutionStatus.ACTION_EXECUTED else ActionExecutionStatus.ACTION_FAILED,
                verificationStatus = verificationStatus,
                capabilityStatus = capabilityStatus,
                userMessage = userMessage,
                hinglishMessage = hinglishMessage,
                errorInfo = errorInfo
            )
        )
    }

    /**
     * Verifies a screen scroll operation by comparing before/after UI state snapshots.
     */
    fun verifyScrollAction(
        direction: String,
        scrollableNodeFound: Boolean,
        actionDispatched: Boolean,
        contentChanged: Boolean,
        activePackage: String
    ): VerifiedActionReport {
        val normDir = direction.trim().lowercase().ifEmpty { "down" }
        return when {
            !scrollableNodeFound && !actionDispatched -> {
                val err = JarvisDiagnosticLogger.recordError(
                    subsystem = "ACCESSIBILITY_SCROLL",
                    errorCode = "SCROLLABLE_CONTAINER_NOT_FOUND",
                    errorMessage = "Scrollable container not found.",
                    technicalCause = "No visible node with isScrollable=true or ACTION_SCROLL in window '$activePackage'",
                    recoveryRecommendation = "Open a scrollable feed, list, or page before scrolling.",
                    retryEligible = false
                )
                recordReport(
                    VerifiedActionReport(
                        actionType = "SCROLL_$normDir".uppercase(),
                        targetApplication = activePackage,
                        targetElement = normDir,
                        executionStatus = ActionExecutionStatus.ACTION_FAILED,
                        verificationStatus = ActionVerificationStatus.ACTION_FAILED,
                        capabilityStatus = CapabilityResultStatus.FAILED,
                        userMessage = "Scrollable container not found.",
                        hinglishMessage = "Is screen par koi scrollable list ya page nahi mila.",
                        errorInfo = err
                    )
                )
            }
            actionDispatched && contentChanged -> {
                recordReport(
                    VerifiedActionReport(
                        actionType = "SCROLL_$normDir".uppercase(),
                        targetApplication = activePackage,
                        targetElement = normDir,
                        executionStatus = ActionExecutionStatus.ACTION_EXECUTED,
                        verificationStatus = ActionVerificationStatus.ACTION_VERIFIED,
                        capabilityStatus = CapabilityResultStatus.SUCCESS,
                        userMessage = "Successfully scrolled $normDir.",
                        hinglishMessage = "Maine screen ko $normDir scroll kar diya hai."
                    )
                )
            }
            actionDispatched && !contentChanged -> {
                recordReport(
                    VerifiedActionReport(
                        actionType = "SCROLL_$normDir".uppercase(),
                        targetApplication = activePackage,
                        targetElement = normDir,
                        executionStatus = ActionExecutionStatus.ACTION_EXECUTED,
                        verificationStatus = ActionVerificationStatus.ACTION_EXECUTED,
                        capabilityStatus = CapabilityResultStatus.PARTIAL_SUCCESS,
                        userMessage = "Scroll action completed but the content did not change.",
                        hinglishMessage = "Maine screen ko $normDir scroll kar diya hai."
                    )
                )
            }
            else -> {
                recordReport(
                    VerifiedActionReport(
                        actionType = "SCROLL_$normDir".uppercase(),
                        targetApplication = activePackage,
                        targetElement = normDir,
                        executionStatus = ActionExecutionStatus.ACTION_FAILED,
                        verificationStatus = ActionVerificationStatus.ACTION_FAILED,
                        capabilityStatus = CapabilityResultStatus.FAILED,
                        userMessage = "Failed to scroll $normDir.",
                        hinglishMessage = "Screen $normDir scroll nahi ho payi."
                    )
                )
            }
        }
    }

    /**
     * Verifies a WhatsApp messaging action so the assistant never claims a message was sent
     * if it was only prepared in the composer (Section 10.7).
     */
    fun verifyWhatsAppAction(
        recipient: String,
        messageText: String,
        intentLaunched: Boolean,
        autoClickedSendViaAccessibility: Boolean,
        requiresConfirmation: Boolean
    ): VerifiedActionReport {
        if (requiresConfirmation) {
            return recordReport(
                VerifiedActionReport(
                    actionType = "WHATSAPP_MESSAGE",
                    targetApplication = "WhatsApp",
                    targetElement = recipient,
                    executionStatus = ActionExecutionStatus.ACTION_FAILED,
                    verificationStatus = ActionVerificationStatus.ACTION_FAILED,
                    capabilityStatus = CapabilityResultStatus.REQUIRES_USER_ACTION,
                    userMessage = "WhatsApp message to '$recipient' requires user confirmation before sending.",
                    hinglishMessage = "Kya aap WhatsApp par $recipient ko yeh message bhejna chahte hain: \"$messageText\"?"
                )
            )
        }
        if (!intentLaunched) {
            return recordReport(
                VerifiedActionReport(
                    actionType = "WHATSAPP_MESSAGE",
                    targetApplication = "WhatsApp",
                    targetElement = recipient,
                    executionStatus = ActionExecutionStatus.ACTION_FAILED,
                    verificationStatus = ActionVerificationStatus.ACTION_FAILED,
                    capabilityStatus = CapabilityResultStatus.FAILED,
                    userMessage = "Failed to open WhatsApp for '$recipient'.",
                    hinglishMessage = "WhatsApp open karne mein problem aayi."
                )
            )
        }
        return if (autoClickedSendViaAccessibility) {
            recordReport(
                VerifiedActionReport(
                    actionType = "WHATSAPP_MESSAGE",
                    targetApplication = "WhatsApp",
                    targetElement = recipient,
                    executionStatus = ActionExecutionStatus.ACTION_EXECUTED,
                    verificationStatus = ActionVerificationStatus.ACTION_VERIFIED,
                    capabilityStatus = CapabilityResultStatus.SUCCESS,
                    userMessage = "WhatsApp message sent to $recipient.",
                    hinglishMessage = "Maine WhatsApp par $recipient ko message bhej diya hai."
                )
            )
        } else {
            recordReport(
                VerifiedActionReport(
                    actionType = "WHATSAPP_MESSAGE",
                    targetApplication = "WhatsApp",
                    targetElement = recipient,
                    executionStatus = ActionExecutionStatus.ACTION_EXECUTED,
                    verificationStatus = ActionVerificationStatus.ACTION_EXECUTED,
                    capabilityStatus = CapabilityResultStatus.REQUIRES_USER_ACTION,
                    userMessage = "Opened WhatsApp with prepared message for '$recipient'. Tap Send in WhatsApp to complete.",
                    hinglishMessage = "Maine WhatsApp open kar ke $recipient ke liye message type kar diya hai. Bhejne ke liye Send dabayein."
                )
            )
        }
    }

    private fun recordReport(report: VerifiedActionReport): VerifiedActionReport {
        verificationHistory.add(report)
        while (verificationHistory.size > MAX_HISTORY) {
            verificationHistory.removeAt(0)
        }
        JarvisDiagnosticLogger.logInfo(
            "ActionVerification",
            "id=${report.actionId} type=${report.actionType} target='${report.targetApplication}' exec=${report.executionStatus} ver=${report.verificationStatus} cap=${report.capabilityStatus}"
        )
        return report
    }

    fun getLatestReport(): VerifiedActionReport? = verificationHistory.lastOrNull()

    fun getRecentReports(limit: Int = 20): List<VerifiedActionReport> {
        val snapshot = verificationHistory.toList()
        return if (snapshot.size <= limit) snapshot else snapshot.takeLast(limit)
    }

    fun clearHistory() {
        verificationHistory.clear()
    }
}
