package com.jarvisai.assistant.ide

data class EditorThemeColors(
    val background: Int,
    val text: Int,
    val panel: Int,
    val line: Int,
    val selection: Int,
    val border: Int,
    val muted: Int,
    val accent: Int,
    val keyword: Int,
    val function: Int,
    val string: Int,
    val comment: Int,
    val punctuation: Int,
    val annotation: Int,
    val tag: Int,
    val attribute: Int
) {
    companion object {
        val VS_DARK = EditorThemeColors(
            background = 0xFF1E1E1E.toInt(),
            text = 0xFFD4D4D4.toInt(),
            panel = 0xFF252526.toInt(),
            line = 0xFF2D2D2D.toInt(),
            selection = 0xFF264F78.toInt(),
            border = 0xFF3E3E42.toInt(),
            muted = 0xFF858585.toInt(),
            accent = 0xFF007ACC.toInt(),
            keyword = 0xFF569CD6.toInt(),
            function = 0xFFDCDCAA.toInt(),
            string = 0xFFCE9178.toInt(),
            comment = 0xFF6A9955.toInt(),
            punctuation = 0xFFD4D4D4.toInt(),
            annotation = 0xFF4EC9B0.toInt(),
            tag = 0xFF569CD6.toInt(),
            attribute = 0xFF9CDCFE.toInt()
        )

        val CYBERPUNK_JARVIS = EditorThemeColors(
            background = 0xFF0A0F1D.toInt(),
            text = 0xFFE2E8F0.toInt(),
            panel = 0xFF111C35.toInt(),
            line = 0xFF1E293B.toInt(),
            selection = 0xFF1E3A8A.toInt(),
            border = 0xFF334155.toInt(),
            muted = 0xFF64748B.toInt(),
            accent = 0xFF06B6D4.toInt(),
            keyword = 0xFF38BDF8.toInt(),
            function = 0xFFFCD34D.toInt(),
            string = 0xFF34D399.toInt(),
            comment = 0xFF64748B.toInt(),
            punctuation = 0xFF94A3B8.toInt(),
            annotation = 0xFFA78BFA.toInt(),
            tag = 0xFF38BDF8.toInt(),
            attribute = 0xFF67E8F9.toInt()
        )
    }
}
