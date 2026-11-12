package com.jarvisai.assistant.theme

import android.graphics.Color
import com.jarvisai.assistant.terminal.LocalTerminalManager
import com.termux.terminal.TerminalColors
import com.termux.terminal.TextStyle
import java.util.Properties

/**
 * Dynamic Terminal Color Scheme Engine (`ThemeTerminal`).
 * Injects active theme colors (background, foreground, cursor, and 16 ANSI colors)
 * into `com.termux.terminal.TerminalColors.COLOR_SCHEME` and repaints active PTY sessions
 * without requiring a shell restart.
 */
object ThemeTerminal {

    data class TerminalPalette(
        val name: String,
        val background: String,
        val foreground: String,
        val cursor: String,
        val ansiColors: List<String> // 16 standard + bright ANSI hex colors
    )

    val JARVIS_CYBERPUNK = TerminalPalette(
        name = "Jarvis Cyberpunk",
        background = "#070B14",
        foreground = "#E2E8F0",
        cursor = "#00E5FF",
        ansiColors = listOf(
            "#0F172A", // 0: black
            "#FB7185", // 1: red
            "#34D399", // 2: green
            "#FBBF24", // 3: yellow
            "#38BDF8", // 4: blue
            "#C084FC", // 5: magenta
            "#00E5FF", // 6: cyan
            "#CBD5E1", // 7: white
            "#475569", // 8: bright black
            "#FDA4AF", // 9: bright red
            "#6EE7B7", // 10: bright green
            "#FDE68A", // 11: bright yellow
            "#7DD3FC", // 12: bright blue
            "#D8B4FE", // 13: bright magenta
            "#67E8F9", // 14: bright cyan
            "#F8FAFC"  // 15: bright white
        )
    )

    val DRACULA = TerminalPalette(
        name = "Dracula",
        background = "#282A36",
        foreground = "#F8F8F2",
        cursor = "#FF79C6",
        ansiColors = listOf(
            "#21222C", "#FF5555", "#50FA7B", "#F1FA8C",
            "#BD93F9", "#FF79C6", "#8BE9FD", "#F8F8F2",
            "#6272A4", "#FF6E6E", "#69FF94", "#FFFFA5",
            "#D6ACFF", "#FF92DF", "#A4FFFF", "#FFFFFF"
        )
    )

    val MONOKAI = TerminalPalette(
        name = "Monokai Pro",
        background = "#1E1F1C",
        foreground = "#F8F8F2",
        cursor = "#FD971F",
        ansiColors = listOf(
            "#272822", "#F92672", "#A6E22E", "#F4BF75",
            "#66D9EF", "#AE81FF", "#A1EFE4", "#F8F8F2",
            "#75715E", "#F92672", "#A6E22E", "#F4BF75",
            "#66D9EF", "#AE81FF", "#A1EFE4", "#F9F8F5"
        )
    )

    val ALL_PALETTES = listOf(JARVIS_CYBERPUNK, DRACULA, MONOKAI)

    @Volatile
    var currentPalette: TerminalPalette = JARVIS_CYBERPUNK
        private set

    fun applyPalette(palette: TerminalPalette = currentPalette) {
        currentPalette = palette
        val props = Properties().apply {
            setProperty("background", palette.background)
            setProperty("foreground", palette.foreground)
            setProperty("cursor", palette.cursor)
            palette.ansiColors.forEachIndexed { index, hex ->
                setProperty("color$index", hex)
            }
        }
        TerminalColors.COLOR_SCHEME.updateWith(props)
        reloadActiveSessions()
    }

    fun applyCustomColors(
        backgroundColor: Int,
        foregroundColor: Int,
        cursorColor: Int
    ) {
        TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND] = backgroundColor
        TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_FOREGROUND] = foregroundColor
        TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_CURSOR] = cursorColor
        reloadActiveSessions()
    }

    fun reloadActiveSessions() {
        LocalTerminalManager.sessions.value.forEach { ptySession ->
            val emulator = ptySession.terminalSession.emulator
            if (emulator != null) {
                emulator.mColors.reset()
                ptySession.onTextChanged(ptySession.terminalSession)
            }
        }
    }

    fun getBackgroundColorInt(): Int {
        return runCatching { Color.parseColor(currentPalette.background) }
            .getOrDefault(Color.parseColor("#070B14"))
    }
}
