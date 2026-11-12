package com.jarvisai.assistant.ide

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Phase 4: Compose Multi-Language Syntax Highlighting `VisualTransformation` (`CodeSyntaxHighlighter`).
 * Highlights keywords, string literals, comments, numbers, annotations, HTML/XML tags,
 * and function invocations inside Jetpack Compose code blocks, editors, and diffs.
 */
class CodeSyntaxVisualTransformation(
    private val fileName: String = "code.kt",
    private val theme: EditorThemeColors = EditorThemeColors.CYBERPUNK_JARVIS
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            text = CodeSyntaxHighlighter.highlight(text.text, fileName, theme),
            offsetMapping = OffsetMapping.Identity
        )
    }
}

object CodeSyntaxHighlighter {

    private val commentRegex = Regex("(//.*$|#.*$|/\\*[\\s\\S]*?\\*/)", setOf(RegexOption.MULTILINE))
    private val stringRegex = Regex("(\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`(?:\\\\.|[^`\\\\])*`)")
    private val annotationRegex = Regex("@[A-Za-z_][A-Za-z0-9_.]*")
    private val tagRegex = Regex("</?[A-Za-z][A-Za-z0-9\\-:]*\\b|/?>")
    private val numberRegex = Regex("\\b(?:0x[0-9a-fA-F]+|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFdDlL]?)\\b")
    private val functionRegex = Regex("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?=\\()")
    private val wordRegex = Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b")

    fun highlight(
        rawCode: String,
        fileName: String = "snippet.kt",
        theme: EditorThemeColors = EditorThemeColors.CYBERPUNK_JARVIS
    ): AnnotatedString {
        if (rawCode.isEmpty()) return AnnotatedString("")

        val keywordStyle = SpanStyle(color = Color(theme.keyword or 0xFF000000.toInt()), fontWeight = FontWeight.SemiBold)
        val stringStyle = SpanStyle(color = Color(theme.string or 0xFF000000.toInt()))
        val commentStyle = SpanStyle(color = Color(theme.comment or 0xFF000000.toInt()), fontStyle = FontStyle.Italic)
        val functionStyle = SpanStyle(color = Color(theme.function or 0xFF000000.toInt()))
        val numberStyle = SpanStyle(color = Color(0xFFF472B6))
        val annotationStyle = SpanStyle(color = Color(theme.annotation or 0xFF000000.toInt()))
        val tagStyle = SpanStyle(color = Color(theme.tag or 0xFF000000.toInt()), fontWeight = FontWeight.SemiBold)

        val ext = fileName.substringAfterLast('.', "").lowercase()
        val isMarkup = ext in setOf("html", "htm", "xml", "svg", "jsx", "tsx", "vue")

        return buildAnnotatedString {
            append(rawCode)

            // 1. Numbers
            numberRegex.findAll(rawCode).forEach { m ->
                addStyle(numberStyle, m.range.first, m.range.last + 1)
            }

            // 2. Function calls
            functionRegex.findAll(rawCode).forEach { m ->
                val group = m.groups[1]
                if (group != null) {
                    addStyle(functionStyle, group.range.first, group.range.last + 1)
                }
            }

            // 3. Keywords & capitalized types
            wordRegex.findAll(rawCode).forEach { m ->
                val w = m.value
                if (w in UniversalCodeAnalyzer.UNIVERSAL_KEYWORDS) {
                    addStyle(keywordStyle, m.range.first, m.range.last + 1)
                } else if (w.first().isUpperCase() && w.length > 1) {
                    addStyle(annotationStyle, m.range.first, m.range.last + 1)
                }
            }

            // 4. Markup tags
            if (isMarkup) {
                tagRegex.findAll(rawCode).forEach { m ->
                    addStyle(tagStyle, m.range.first, m.range.last + 1)
                }
            }

            // 5. Annotations (@Composable, etc.)
            annotationRegex.findAll(rawCode).forEach { m ->
                addStyle(annotationStyle, m.range.first, m.range.last + 1)
            }

            // 6. Strings override inner keywords
            stringRegex.findAll(rawCode).forEach { m ->
                addStyle(stringStyle, m.range.first, m.range.last + 1)
            }

            // 7. Comments override everything inside their span
            commentRegex.findAll(rawCode).forEach { m ->
                addStyle(commentStyle, m.range.first, m.range.last + 1)
            }
        }
    }
}
