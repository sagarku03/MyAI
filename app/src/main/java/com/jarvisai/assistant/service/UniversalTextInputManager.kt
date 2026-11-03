package com.jarvisai.assistant.service

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

data class TextInputOutcome(
    val executed: Boolean,
    val textSet: String,
    val targetFieldLabel: String,
    val actionType: String, // TYPE, APPEND, REPLACE, CLEAR, SUBMIT
    val errorCode: String? = null,
    val message: String,
    val spokenHinglishSummary: String
)

/**
 * Universal text input engine for JARVIS AI.
 * Discovers accessible input nodes across Android apps (WhatsApp, Chrome, Telegram, Notes, Search bars),
 * manages focusing, text replacement/appending, and IME submission.
 */
class UniversalTextInputManager(
    private val context: Context,
    private val accessibilityBridge: AccessibilityControllerBridge = JarvisAccessibilityService
) {
    companion object {
        private const val TAG = "UniversalTextInputManager"
    }

    /**
     * Types text into the target or focused input field.
     */
    fun enterText(
        text: String,
        targetLabel: String? = null,
        append: Boolean = false,
        submitAfter: Boolean = false
    ): TextInputOutcome {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            return TextInputOutcome(
                executed = false,
                textSet = "",
                targetFieldLabel = targetLabel.orEmpty(),
                actionType = "TYPE",
                errorCode = "EMPTY_TEXT",
                message = "Text parameter cannot be empty.",
                spokenHinglishSummary = "Likhne ke liye text provide nahi kiya gaya."
            )
        }

        // 1. If target label provided, try focusing it first
        if (!targetLabel.isNullOrBlank()) {
            accessibilityBridge.focusInputField(context, targetLabel)
        }

        // 2. Perform text entry
        val result = accessibilityBridge.typeText(
            context = context,
            text = cleanText,
            replaceExisting = !append,
            submitAfter = submitAfter
        )

        return TextInputOutcome(
            executed = result.executed,
            textSet = cleanText,
            targetFieldLabel = targetLabel.orEmpty(),
            actionType = if (append) "APPEND" else "REPLACE",
            errorCode = result.errorCode,
            message = result.message,
            spokenHinglishSummary = result.spokenHinglishSummary
        )
    }

    /**
     * Clears text in the active input field.
     */
    fun clearActiveField(targetLabel: String? = null): TextInputOutcome {
        if (!targetLabel.isNullOrBlank()) {
            accessibilityBridge.focusInputField(context, targetLabel)
        }
        val result = accessibilityBridge.clearText(context)
        return TextInputOutcome(
            executed = result.executed,
            textSet = "",
            targetFieldLabel = targetLabel.orEmpty(),
            actionType = "CLEAR",
            errorCode = result.errorCode,
            message = result.message,
            spokenHinglishSummary = result.spokenHinglishSummary
        )
    }

    /**
     * Focuses an input field.
     */
    fun focusField(targetLabel: String?): TextInputOutcome {
        val result = accessibilityBridge.focusInputField(context, targetLabel)
        return TextInputOutcome(
            executed = result.executed,
            textSet = "",
            targetFieldLabel = targetLabel.orEmpty(),
            actionType = "FOCUS",
            errorCode = result.errorCode,
            message = result.message,
            spokenHinglishSummary = result.spokenHinglishSummary
        )
    }

    /**
     * Submits the active form / search query.
     */
    fun submitActiveInput(): TextInputOutcome {
        val result = accessibilityBridge.submitInput(context)
        return TextInputOutcome(
            executed = result.executed,
            textSet = "",
            targetFieldLabel = "",
            actionType = "SUBMIT",
            errorCode = result.errorCode,
            message = result.message,
            spokenHinglishSummary = result.spokenHinglishSummary
        )
    }
}
