package com.jarvisai.assistant.theme

import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color as ComposeColor
import com.jarvisai.assistant.terminal.LocalTerminalManager
import com.termux.terminal.TerminalColors
import com.termux.terminal.TextStyle
import java.util.Properties

/**
 * Dynamic Terminal Color Scheme Engine (`ThemeTerminal`).
 * Injects active theme colors into `com.termux.terminal.TerminalColors.COLOR_SCHEME`,
 * repaints running PTY sessions in-place, and increments [version] so Compose terminal
 * surfaces immediately redraw in the new colors.
 */
object ThemeTerminal {

    data class TerminalPalette(
        val name: String,
        val background: String,
        val foreground: String,
        val cursor: String,
        val ansiColors: List<String> // 16 standard + bright ANSI hex colors
    )

    val AGENTRA_CYBERPUNK = TerminalPalette(
        name = "Agentra Cyberpunk",
        background = "#0D1117",
        foreground = "#E6EDF3",
        cursor = "#D97757",
        ansiColors = listOf(
            "#161B22", // 0: black
            "#F85149", // 1: red
            "#3FB950", // 2: green
            "#D29922", // 3: yellow
            "#58A6FF", // 4: blue
            "#BC8CFF", // 5: magenta
            "#39C5CF", // 6: cyan
            "#B1BAC4", // 7: white
            "#6E7681", // 8: bright black
            "#FFA198", // 9: bright red
            "#56D364", // 10: bright green
            "#E3B341", // 11: bright yellow
            "#79C0FF", // 12: bright blue
            "#D2A8FF", // 13: bright magenta
            "#56D4DD", // 14: bright cyan
            "#F0F6FC"  // 15: bright white
        )
    )

    // Backwards-compatible alias for any callers referencing JARVIS_CYBERPUNK
    val JARVIS_CYBERPUNK = AGENTRA_CYBERPUNK

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

    val ANTIGEM_DARK = TerminalPalette(
        name = "Agentra Dark",
        background = "#0B0F17",
        foreground = "#E2E8F0",
        cursor = "#D97757",
        ansiColors = listOf(
            "#151B26", "#F87171", "#4ADE80", "#FBBF24",
            "#60A5FA", "#C084FC", "#22D3EE", "#CBD5E1",
            "#475569", "#FCA5A5", "#86EFAC", "#FDE68A",
            "#93C5FD", "#D8B4FE", "#67E8F9", "#F8FAFC"
        )
    )

    val ALL_PALETTES = listOf(AGENTRA_CYBERPUNK, ANTIGEM_DARK, DRACULA, MONOKAI)

    @Volatile
    var currentPalette: TerminalPalette = AGENTRA_CYBERPUNK
        private set

    var version by mutableIntStateOf(0)
        private set

    val backgroundColor: ComposeColor
        get() = parseComposeColor(currentPalette.background, ComposeColor(0xFF0D1117))

    val panelColor: ComposeColor
        get() = parseComposeColor(currentPalette.ansiColors.getOrElse(0) { "#161B22" }, ComposeColor(0xFF161B22))

    val textColor: ComposeColor
        get() = parseComposeColor(currentPalette.foreground, ComposeColor(0xFFE6EDF3))

    val accentColor: ComposeColor
        get() = parseComposeColor(currentPalette.cursor, ComposeColor(0xFFD97757))

    val dangerColor: ComposeColor
        get() = parseComposeColor(currentPalette.ansiColors.getOrElse(1) { "#F85149" }, ComposeColor(0xFFF85149))

    val successColor: ComposeColor
        get() = parseComposeColor(currentPalette.ansiColors.getOrElse(2) { "#3FB950" }, ComposeColor(0xFF3FB950))

    private fun parseComposeColor(hex: String, fallback: ComposeColor): ComposeColor {
        return runCatching { ComposeColor(Color.parseColor(hex)) }.getOrDefault(fallback)
    }

    fun applyPalette(palette: TerminalPalette = currentPalette) {
        currentPalette = palette
        runCatching {
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
        version++
    }

    fun applyCustomColors(
        backgroundColor: Int,
        foregroundColor: Int,
        cursorColor: Int
    ) {
        runCatching {
            TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND] = backgroundColor
            TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_FOREGROUND] = foregroundColor
            TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_CURSOR] = cursorColor
            reloadActiveSessions()
        }
        version++
    }

    fun reloadActiveSessions() {
        runCatching {
            LocalTerminalManager.sessions.value.toList().forEach { ptySession ->
                runCatching {
                    val emulator = ptySession.terminalSession.emulator
                    if (emulator != null) {
                        emulator.mColors.reset()
                        ptySession.onTextChanged(ptySession.terminalSession)
                    }
                }
            }
        }
        version++
    }

    fun getBackgroundColorInt(): Int {
        return runCatching { Color.parseColor(currentPalette.background) }
            .getOrDefault(Color.parseColor("#0D1117"))
    }
}
