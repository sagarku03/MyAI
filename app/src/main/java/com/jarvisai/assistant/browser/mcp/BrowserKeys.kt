package com.jarvisai.assistant.browser.mcp

import android.view.KeyEvent

/**
 * Physical and virtual key lookup table translating key names and chord strings
 * (e.g., `Ctrl+A`, `Shift+Tab`, `Enter`, `ArrowDown`) into Chrome DevTools Protocol (CDP)
 * `Input.dispatchKeyEvent` payloads and Android `KeyEvent` keycodes.
 */
object BrowserKeys {

    const val MODIFIER_ALT = 1
    const val MODIFIER_CTRL = 2
    const val MODIFIER_META = 4
    const val MODIFIER_SHIFT = 8

    data class KeyDef(
        val key: String,
        val code: String,
        val keyCode: Int,
        val text: String? = null,
        val androidCode: Int = KeyEvent.KEYCODE_UNKNOWN,
        val modifierBit: Int = 0
    )

    data class ResolvedKeyChord(
        val keyDef: KeyDef,
        val modifiers: Int
    )

    private val SPECIAL_KEYS: Map<String, KeyDef> = mapOf(
        "enter" to KeyDef("Enter", "Enter", 13, "\r", KeyEvent.KEYCODE_ENTER),
        "return" to KeyDef("Enter", "Enter", 13, "\r", KeyEvent.KEYCODE_ENTER),
        "tab" to KeyDef("Tab", "Tab", 9, null, KeyEvent.KEYCODE_TAB),
        "backspace" to KeyDef("Backspace", "Backspace", 8, null, KeyEvent.KEYCODE_DEL),
        "delete" to KeyDef("Delete", "Delete", 46, null, KeyEvent.KEYCODE_FORWARD_DEL),
        "escape" to KeyDef("Escape", "Escape", 27, null, KeyEvent.KEYCODE_ESCAPE),
        "esc" to KeyDef("Escape", "Escape", 27, null, KeyEvent.KEYCODE_ESCAPE),
        "space" to KeyDef(" ", "Space", 32, " ", KeyEvent.KEYCODE_SPACE),
        " " to KeyDef(" ", "Space", 32, " ", KeyEvent.KEYCODE_SPACE),
        "arrowup" to KeyDef("ArrowUp", "ArrowUp", 38, null, KeyEvent.KEYCODE_DPAD_UP),
        "up" to KeyDef("ArrowUp", "ArrowUp", 38, null, KeyEvent.KEYCODE_DPAD_UP),
        "arrowdown" to KeyDef("ArrowDown", "ArrowDown", 40, null, KeyEvent.KEYCODE_DPAD_DOWN),
        "down" to KeyDef("ArrowDown", "ArrowDown", 40, null, KeyEvent.KEYCODE_DPAD_DOWN),
        "arrowleft" to KeyDef("ArrowLeft", "ArrowLeft", 37, null, KeyEvent.KEYCODE_DPAD_LEFT),
        "left" to KeyDef("ArrowLeft", "ArrowLeft", 37, null, KeyEvent.KEYCODE_DPAD_LEFT),
        "arrowright" to KeyDef("ArrowRight", "ArrowRight", 39, null, KeyEvent.KEYCODE_DPAD_RIGHT),
        "right" to KeyDef("ArrowRight", "ArrowRight", 39, null, KeyEvent.KEYCODE_DPAD_RIGHT),
        "home" to KeyDef("Home", "Home", 36, null, KeyEvent.KEYCODE_MOVE_HOME),
        "end" to KeyDef("End", "End", 35, null, KeyEvent.KEYCODE_MOVE_END),
        "pageup" to KeyDef("PageUp", "PageUp", 33, null, KeyEvent.KEYCODE_PAGE_UP),
        "pagedown" to KeyDef("PageDown", "PageDown", 34, null, KeyEvent.KEYCODE_PAGE_DOWN),
        "insert" to KeyDef("Insert", "Insert", 45, null, KeyEvent.KEYCODE_INSERT),
        "shift" to KeyDef("Shift", "ShiftLeft", 16, null, KeyEvent.KEYCODE_SHIFT_LEFT, MODIFIER_SHIFT),
        "control" to KeyDef("Control", "ControlLeft", 17, null, KeyEvent.KEYCODE_CTRL_LEFT, MODIFIER_CTRL),
        "ctrl" to KeyDef("Control", "ControlLeft", 17, null, KeyEvent.KEYCODE_CTRL_LEFT, MODIFIER_CTRL),
        "alt" to KeyDef("Alt", "AltLeft", 18, null, KeyEvent.KEYCODE_ALT_LEFT, MODIFIER_ALT),
        "meta" to KeyDef("Meta", "MetaLeft", 91, null, KeyEvent.KEYCODE_META_LEFT, MODIFIER_META),
        "cmd" to KeyDef("Meta", "MetaLeft", 91, null, KeyEvent.KEYCODE_META_LEFT, MODIFIER_META),
        "command" to KeyDef("Meta", "MetaLeft", 91, null, KeyEvent.KEYCODE_META_LEFT, MODIFIER_META)
    )

    private data class ShiftedSymbol(
        val code: String,
        val keyCode: Int,
        val androidCode: Int,
        val requiresShift: Boolean
    )

    private val PUNCTUATION_MAP: Map<Char, ShiftedSymbol> = mapOf(
        '-' to ShiftedSymbol("Minus", 189, KeyEvent.KEYCODE_MINUS, false),
        '_' to ShiftedSymbol("Minus", 189, KeyEvent.KEYCODE_MINUS, true),
        '=' to ShiftedSymbol("Equal", 187, KeyEvent.KEYCODE_EQUALS, false),
        '+' to ShiftedSymbol("Equal", 187, KeyEvent.KEYCODE_EQUALS, true),
        '[' to ShiftedSymbol("BracketLeft", 219, KeyEvent.KEYCODE_LEFT_BRACKET, false),
        '{' to ShiftedSymbol("BracketLeft", 219, KeyEvent.KEYCODE_LEFT_BRACKET, true),
        ']' to ShiftedSymbol("BracketRight", 221, KeyEvent.KEYCODE_RIGHT_BRACKET, false),
        '}' to ShiftedSymbol("BracketRight", 221, KeyEvent.KEYCODE_RIGHT_BRACKET, true),
        '\\' to ShiftedSymbol("Backslash", 220, KeyEvent.KEYCODE_BACKSLASH, false),
        '|' to ShiftedSymbol("Backslash", 220, KeyEvent.KEYCODE_BACKSLASH, true),
        ';' to ShiftedSymbol("Semicolon", 186, KeyEvent.KEYCODE_SEMICOLON, false),
        ':' to ShiftedSymbol("Semicolon", 186, KeyEvent.KEYCODE_SEMICOLON, true),
        '\'' to ShiftedSymbol("Quote", 222, KeyEvent.KEYCODE_APOSTROPHE, false),
        '"' to ShiftedSymbol("Quote", 222, KeyEvent.KEYCODE_APOSTROPHE, true),
        ',' to ShiftedSymbol("Comma", 188, KeyEvent.KEYCODE_COMMA, false),
        '<' to ShiftedSymbol("Comma", 188, KeyEvent.KEYCODE_COMMA, true),
        '.' to ShiftedSymbol("Period", 190, KeyEvent.KEYCODE_PERIOD, false),
        '>' to ShiftedSymbol("Period", 190, KeyEvent.KEYCODE_PERIOD, true),
        '/' to ShiftedSymbol("Slash", 191, KeyEvent.KEYCODE_SLASH, false),
        '?' to ShiftedSymbol("Slash", 191, KeyEvent.KEYCODE_SLASH, true),
        '`' to ShiftedSymbol("Backquote", 192, KeyEvent.KEYCODE_GRAVE, false),
        '~' to ShiftedSymbol("Backquote", 192, KeyEvent.KEYCODE_GRAVE, true),
        '!' to ShiftedSymbol("Digit1", 49, KeyEvent.KEYCODE_1, true),
        '@' to ShiftedSymbol("Digit2", 50, KeyEvent.KEYCODE_2, true),
        '#' to ShiftedSymbol("Digit3", 51, KeyEvent.KEYCODE_3, true),
        '$' to ShiftedSymbol("Digit4", 52, KeyEvent.KEYCODE_4, true),
        '%' to ShiftedSymbol("Digit5", 53, KeyEvent.KEYCODE_5, true),
        '^' to ShiftedSymbol("Digit6", 54, KeyEvent.KEYCODE_6, true),
        '&' to ShiftedSymbol("Digit7", 55, KeyEvent.KEYCODE_7, true),
        '*' to ShiftedSymbol("Digit8", 56, KeyEvent.KEYCODE_8, true),
        '(' to ShiftedSymbol("Digit9", 57, KeyEvent.KEYCODE_9, true),
        ')' to ShiftedSymbol("Digit0", 48, KeyEvent.KEYCODE_0, true)
    )

    /**
     * Resolves a key name or key combination (e.g. `"Enter"`, `"Ctrl+a"`, `"Shift+Tab"`)
     * into its [KeyDef] and combined CDP modifier bitmask.
     */
    fun resolveChord(rawSpec: String): ResolvedKeyChord {
        val trimmed = rawSpec.trim()
        if (trimmed.isEmpty()) {
            return ResolvedKeyChord(SPECIAL_KEYS.getValue("enter"), 0)
        }
        val tokens = if (trimmed.length > 1 && trimmed.contains("+")) {
            trimmed.split("+").filter { it.isNotEmpty() }
        } else {
            listOf(trimmed)
        }

        var modifiers = 0
        var primaryToken = tokens.last()

        for (i in 0 until tokens.size - 1) {
            when (tokens[i].lowercase()) {
                "alt", "option" -> modifiers = modifiers or MODIFIER_ALT
                "ctrl", "control" -> modifiers = modifiers or MODIFIER_CTRL
                "meta", "cmd", "command", "super" -> modifiers = modifiers or MODIFIER_META
                "shift" -> modifiers = modifiers or MODIFIER_SHIFT
            }
        }

        val special = SPECIAL_KEYS[primaryToken.lowercase()]
        if (special != null) {
            return ResolvedKeyChord(special, modifiers or special.modifierBit)
        }

        // Function keys F1..F12
        if (primaryToken.matches(Regex("(?i)^F([1-9]|1[0-2])$"))) {
            val fnIndex = primaryToken.drop(1).toInt()
            val keyName = "F$fnIndex"
            return ResolvedKeyChord(
                KeyDef(
                    key = keyName,
                    code = keyName,
                    keyCode = 111 + fnIndex,
                    text = null,
                    androidCode = KeyEvent.KEYCODE_F1 + (fnIndex - 1)
                ),
                modifiers
            )
        }

        if (primaryToken.length == 1) {
            val ch = primaryToken[0]
            when {
                ch in 'a'..'z' -> {
                    val upper = ch.uppercaseChar()
                    val offset = upper - 'A'
                    val keyStr = if ((modifiers and MODIFIER_SHIFT) != 0) upper.toString() else ch.toString()
                    val textStr = if ((modifiers and (MODIFIER_CTRL or MODIFIER_ALT or MODIFIER_META)) != 0) null else keyStr
                    return ResolvedKeyChord(
                        KeyDef(
                            key = keyStr,
                            code = "Key$upper",
                            keyCode = 65 + offset,
                            text = textStr,
                            androidCode = KeyEvent.KEYCODE_A + offset
                        ),
                        modifiers
                    )
                }
                ch in 'A'..'Z' -> {
                    val offset = ch - 'A'
                    val combinedMods = modifiers or MODIFIER_SHIFT
                    val textStr = if ((combinedMods and (MODIFIER_CTRL or MODIFIER_ALT or MODIFIER_META)) != 0) null else ch.toString()
                    return ResolvedKeyChord(
                        KeyDef(
                            key = ch.toString(),
                            code = "Key$ch",
                            keyCode = 65 + offset,
                            text = textStr,
                            androidCode = KeyEvent.KEYCODE_A + offset
                        ),
                        combinedMods
                    )
                }
                ch in '0'..'9' -> {
                    val offset = ch - '0'
                    return ResolvedKeyChord(
                        KeyDef(
                            key = ch.toString(),
                            code = "Digit$ch",
                            keyCode = 48 + offset,
                            text = if ((modifiers and (MODIFIER_CTRL or MODIFIER_ALT or MODIFIER_META)) != 0) null else ch.toString(),
                            androidCode = KeyEvent.KEYCODE_0 + offset
                        ),
                        modifiers
                    )
                }
                PUNCTUATION_MAP.containsKey(ch) -> {
                    val punct = PUNCTUATION_MAP.getValue(ch)
                    val combinedMods = if (punct.requiresShift) (modifiers or MODIFIER_SHIFT) else modifiers
                    return ResolvedKeyChord(
                        KeyDef(
                            key = ch.toString(),
                            code = punct.code,
                            keyCode = punct.keyCode,
                            text = ch.toString(),
                            androidCode = punct.androidCode
                        ),
                        combinedMods
                    )
                }
            }
        }

        // Fallback for any other single/multi-char key string
        return ResolvedKeyChord(
            KeyDef(
                key = primaryToken,
                code = primaryToken,
                keyCode = primaryToken.firstOrNull()?.code ?: 0,
                text = if (primaryToken.length == 1) primaryToken else null
            ),
            modifiers
        )
    }

    /**
     * Convenience lookup for single key definitions.
     */
    fun lookup(key: String): KeyDef = resolveChord(key).keyDef
}
