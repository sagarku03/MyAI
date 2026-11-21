package com.jarvisai.assistant.ui.components

import android.util.Log
import androidx.compose.runtime.compositionLocalOf

enum class AppViewMode {
    CHAT,
    IDE,
    TERMINAL,
    BROWSER
}

/**
 * Coordinates unified focus and keyboard (IME) handoff across persistent main screens:
 * CHAT, IDE, TERMINAL, and BROWSER.
 */
class AppFocusCoordinator {
    private val tag = "AppFocusCoordinator"
    private val focusRequesters = mutableMapOf<AppViewMode, () -> Unit>()
    private val clearFocusRequesters = mutableMapOf<AppViewMode, () -> Unit>()

    fun registerScreen(
        mode: AppViewMode,
        onRequestFocus: () -> Unit,
        onClearFocus: () -> Unit
    ) {
        focusRequesters[mode] = onRequestFocus
        clearFocusRequesters[mode] = onClearFocus
    }

    fun unregisterScreen(mode: AppViewMode) {
        focusRequesters.remove(mode)
        clearFocusRequesters.remove(mode)
    }

    fun onScreenLeaving(leavingMode: AppViewMode, isKeyboardOpen: Boolean) {
        Log.d(tag, "Screen $leavingMode leaving, isKeyboardOpen=$isKeyboardOpen")
        if (!isKeyboardOpen) {
            clearFocusRequesters[leavingMode]?.invoke()
        }
    }

    fun onScreenEntering(
        enteringMode: AppViewMode,
        isKeyboardOpen: Boolean
    ) {
        Log.d(tag, "Screen $enteringMode entering, isKeyboardOpen=$isKeyboardOpen")
        if (isKeyboardOpen) {
            focusRequesters[enteringMode]?.invoke()
        } else {
            clearFocusRequesters[enteringMode]?.invoke()
        }
    }
}

val LocalAppFocusCoordinator = compositionLocalOf { AppFocusCoordinator() }
