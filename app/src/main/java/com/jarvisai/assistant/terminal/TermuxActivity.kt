package com.jarvisai.assistant.terminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.ui.theme.JarvisHudTheme
import com.jarvisai.assistant.data.preferences.AuthPreferences
import com.jarvisai.assistant.service.TermuxService
import com.jarvisai.assistant.theme.ThemeTerminal
import com.jarvisai.assistant.ui.components.LocalTerminalDialog

/**
 * Standalone Terminal Entrypoint Activity launched by "Agentra Term" / "Termux" launcher icons.
 */
class TermuxActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LocalTerminalBridge.instance.init(applicationContext)
        TermuxService.start(this)

        val authPrefs = AuthPreferences.getInstance(applicationContext)
        LocalTerminalManager.updatePreferences(
            cursorStyle = authPrefs.getTerminalCursorStyleSync(),
            bufferSize = authPrefs.getTerminalBufferSizeSync()
        )
        ThemeTerminal.applyPalette(ThemeTerminal.currentPalette)

        setContent {
            val savedCursor by authPrefs.terminalCursorStyle.collectAsState()
            val savedBuffer by authPrefs.terminalBufferSize.collectAsState()
            remember(savedCursor, savedBuffer) {
                LocalTerminalManager.updatePreferences(savedCursor, savedBuffer)
                true
            }
            JarvisHudTheme {
                LocalTerminalDialog(onDismiss = { finish() })
            }
        }
    }
}
