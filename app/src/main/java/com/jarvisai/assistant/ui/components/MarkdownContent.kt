package com.jarvisai.assistant.ui.components

import android.graphics.Color as AndroidColor
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.model.ChoiceResponseItem
import com.jarvisai.assistant.model.ToolCall
import ru.noties.jlatexmath.JLatexMathDrawable
import kotlin.math.max

/**
 * Global LRU cache holding up to 150 pre-parsed [JLatexMathDrawable] instances.
 * Prevents re-parsing LaTeX ASTs during 120 FPSLazyColumn scrolling and eliminates Cumulative Layout Shift (CLS).
 */
object LatexDrawableCache {
    private val cache = LruCache<Int, JLatexMathDrawable>(150)

    fun getOrCreate(
        rawLatex: String,
        textSizePx: Float,
        argbColor: Int
    ): JLatexMathDrawable? {
        val clean = stripLatexDelimiters(rawLatex)
        if (clean.isBlank()) return null
        val key = (clean.hashCode() * 31 + textSizePx.toInt()) * 31 + argbColor
        synchronized(cache) {
            cache.get(key)?.let { return it }
        }
        return try {
            val drawable = JLatexMathDrawable.builder(clean)
                .textSize(textSizePx)
                .color(argbColor)
                .background(AndroidColor.TRANSPARENT)
                .padding(4)
                .align(JLatexMathDrawable.ALIGN_LEFT)
                .build()
            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
            synchronized(cache) {
                cache.put(key, drawable)
            }
            drawable
        } catch (_: Throwable) {
            null
        }
    }

    fun stripLatexDelimiters(raw: String): String {
        var s = raw.trim()
        when {
            s.startsWith("$$") && s.endsWith("$$") && s.length >= 4 -> s = s.substring(2, s.length - 2)
            s.startsWith("\\[") && s.endsWith("\\]") && s.length >= 4 -> s = s.substring(2, s.length - 2)
            s.startsWith("\\(") && s.endsWith("\\)") && s.length >= 4 -> s = s.substring(2, s.length - 2)
            s.startsWith("$") && s.endsWith("$") && s.length >= 2 -> s = s.substring(1, s.length - 1)
        }
        return s.trim()
    }
}

/**
 * Renders a native LaTeX equation directly onto an Android [Canvas] using [JLatexMathDrawable]
 * with zero WebViews and pre-computed intrinsic dimensions (zero CLS).
 */
@Composable
fun NativeMathView(
    latex: String,
    isDisplay: Boolean = true,
    textColor: Color = Color(0xFFF59E0B), // Amber Gold math formula accent
    fontSize: TextUnit = if (isDisplay) 17.sp else 14.5.sp,
    onCopy: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textSizePx = with(density) { fontSize.toPx() }
    val argb = textColor.toArgb()

    val drawable = remember(latex, textSizePx, argb) {
        LatexDrawableCache.getOrCreate(latex, textSizePx, argb)
    }

    if (drawable == null) {
        // Fallback syntax-highlighted raw LaTeX expression if expression has unbalanced braces during live streaming
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF111827),
            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f)),
            modifier = modifier.padding(vertical = 2.dp)
        ) {
            Text(
                text = LatexDrawableCache.stripLatexDelimiters(latex),
                color = textColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        return
    }

    val widthDp: Dp = with(density) { max(drawable.intrinsicWidth, 1).toDp() }
    val heightDp: Dp = with(density) { max(drawable.intrinsicHeight, 1).toDp() }

    if (isDisplay) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .testTag("native_math_display_card"),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0B0F19),
            border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111827))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Functions,
                            contentDescription = "LaTeX Formula",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LATEX CANVAS ENGINE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF38BDF8)
                        )
                    }
                    if (onCopy != null) {
                        IconButton(
                            onClick = { onCopy(LatexDrawableCache.stripLatexDelimiters(latex)) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy LaTeX source",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Canvas(modifier = Modifier.size(width = widthDp, height = heightDp)) {
                        drawIntoCanvas { canvas ->
                            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
                            drawable.draw(canvas.nativeCanvas)
                        }
                    }
                }
            }
        }
    } else {
        Canvas(modifier = modifier.size(width = widthDp, height = heightDp)) {
            drawIntoCanvas { canvas ->
                drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
                drawable.draw(canvas.nativeCanvas)
            }
        }
    }
}

private sealed class MarkdownSegment {
    data class TextParagraph(val text: String) : MarkdownSegment()
    data class DisplayMath(val latex: String) : MarkdownSegment()
    data class FencedCode(val language: String, val code: String) : MarkdownSegment()
    data class Heading(val level: Int, val text: String) : MarkdownSegment()
    data class BulletItem(val indent: Int, val marker: String, val text: String) : MarkdownSegment()
    data class BlockQuote(val text: String) : MarkdownSegment()
    data object DividerRule : MarkdownSegment()
}

private val INLINE_MATH_REGEX = Regex("""(?<!\$)\$(?!\$)([^\$\n]+?)(?<!\$)\$(?!\$)|\\\((.+?)\\\)""")
private val BOLD_ITALIC_CODE_REGEX = Regex("""(`[^`]+`|\*\*[^*]+\*\*|\*[^*]+\*|~~[^~]+~~)""")

/**
 * High-performance Scientific Markdown & Native Canvas LaTeX Renderer (`MarkdownContent`).
 * Supports:
 * - Display LaTeX blocks (`$$ ... $$` and `\[ ... \]`) rendered via [NativeMathView]
 * - Inline LaTeX equations (`$ ... $` and `\( ... \)`) embedded via [InlineTextContent] with pre-computed [Placeholder] dimensions
 * - Mermaid diagrams (` ```mermaid `) routed to [MermaidDiagramView]
 * - Unified Git diffs (` ```diff `) routed to [UnifiedDiffViewer]
 * - Syntax-highlighted code blocks (` ```lang `) routed to [CodeBlock]
 * - Interactive tool calls & multiple-choice questionnaires
 */
@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color(0xFFF3F4F6),
    mathColor: Color = Color(0xFFF59E0B),
    fontSize: TextUnit = 15.sp,
    fillMaxWidth: Boolean = true,
    toolCalls: List<ToolCall> = emptyList(),
    onApproveTool: ((ToolCall) -> Unit)? = null,
    onRejectTool: ((ToolCall) -> Unit)? = null,
    onSubmitQuestionnaire: ((ToolCall, List<ChoiceResponseItem>) -> Unit)? = null,
    onSkipQuestionnaire: ((ToolCall) -> Unit)? = null,
    onCopy: ((String) -> Unit)? = null
) {
    val segments = remember(content) { parseMarkdownSegments(content) }

    Column(
        modifier = if (fillMaxWidth) modifier.fillMaxWidth() else modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (toolCalls.isNotEmpty()) {
            toolCalls.forEach { tool ->
                if (tool.questionnaire != null && onSubmitQuestionnaire != null && onSkipQuestionnaire != null) {
                    ChoiceQuestionnaireCard(
                        toolCall = tool,
                        onSubmit = onSubmitQuestionnaire,
                        onSkip = onSkipQuestionnaire
                    )
                } else {
                    AgentToolCallCard(toolCall = tool)
                }
            }
        }

        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.DisplayMath -> {
                    NativeMathView(
                        latex = segment.latex,
                        isDisplay = true,
                        textColor = mathColor,
                        onCopy = onCopy
                    )
                }

                is MarkdownSegment.FencedCode -> {
                    val lowerLang = segment.language.lowercase().trim()
                    val isDiff = lowerLang in listOf("diff", "patch") ||
                        segment.code.startsWith("diff --git") ||
                        (segment.code.contains("\n@@ ") && segment.code.contains("\n--- "))

                    when {
                        lowerLang == "mermaid" -> {
                            MermaidDiagramView(code = segment.code)
                        }
                        lowerLang in listOf("math", "latex", "tex") -> {
                            NativeMathView(
                                latex = segment.code,
                                isDisplay = true,
                                textColor = mathColor,
                                onCopy = onCopy
                            )
                        }
                        isDiff -> {
                            UnifiedDiffViewer(rawDiff = segment.code)
                        }
                        else -> {
                            CodeBlock(
                                code = segment.code,
                                language = segment.language.ifBlank { "CODE" }
                            )
                        }
                    }
                }

                is MarkdownSegment.Heading -> {
                    val headingSize = when (segment.level) {
                        1 -> 20.sp
                        2 -> 18.sp
                        3 -> 16.5.sp
                        else -> 15.5.sp
                    }
                    val headingColor = when (segment.level) {
                        1 -> Color(0xFF38BDF8)
                        2 -> Color(0xFFA5B4FC)
                        else -> textColor
                    }
                    InlineMathRichText(
                        rawText = segment.text,
                        textColor = headingColor,
                        mathColor = mathColor,
                        fontSize = headingSize,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                is MarkdownSegment.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (segment.indent * 12).dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = segment.marker,
                            color = Color(0xFF38BDF8),
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            InlineMathRichText(
                                rawText = segment.text,
                                textColor = textColor,
                                mathColor = mathColor,
                                fontSize = fontSize
                            )
                        }
                    }
                }

                is MarkdownSegment.BlockQuote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF111827).copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(22.dp)
                                .background(Color(0xFF6366F1), RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        InlineMathRichText(
                            rawText = segment.text,
                            textColor = textColor.copy(alpha = 0.9f),
                            mathColor = mathColor,
                            fontSize = fontSize
                        )
                    }
                }

                is MarkdownSegment.DividerRule -> {
                    HorizontalDivider(
                        color = Color(0xFF1F2937),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                is MarkdownSegment.TextParagraph -> {
                    InlineMathRichText(
                        rawText = segment.text,
                        textColor = textColor,
                        mathColor = mathColor,
                        fontSize = fontSize
                    )
                }
            }
        }

        FileLinkRow(text = content)
    }
}

@Composable
private fun InlineMathRichText(
    rawText: String,
    textColor: Color,
    mathColor: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal
) {
    val density = LocalDensity.current
    val textSizePx = with(density) { fontSize.toPx() }
    val mathArgb = mathColor.toArgb()

    val (annotatedString, inlineContentMap) = remember(rawText, textColor, mathColor, fontSize, fontWeight) {
        val inlineMap = mutableMapOf<String, InlineTextContent>()
        var mathIndex = 0

        val annotated = buildAnnotatedString {
            var cursor = 0
            for (match in INLINE_MATH_REGEX.findAll(rawText)) {
                if (match.range.first > cursor) {
                    appendStyledMarkdownSpan(
                        text = rawText.substring(cursor, match.range.first),
                        baseColor = textColor
                    )
                }
                val latexExpr = (match.groupValues[1].ifEmpty { match.groupValues[2] }).trim()
                val drawable = LatexDrawableCache.getOrCreate(latexExpr, textSizePx * 1.05f, mathArgb)
                if (drawable != null) {
                    val id = "inline_math_${mathIndex++}"
                    val widthSp = with(density) { max(drawable.intrinsicWidth, 4).toSp() }
                    val heightSp = with(density) { max(drawable.intrinsicHeight, 4).toSp() }
                    appendInlineContent(id, "[$latexExpr]")
                    inlineMap[id] = InlineTextContent(
                        placeholder = Placeholder(
                            width = widthSp,
                            height = heightSp,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                        )
                    ) {
                        NativeMathView(
                            latex = latexExpr,
                            isDisplay = false,
                            textColor = mathColor,
                            fontSize = fontSize
                        )
                    }
                } else {
                    withStyle(
                        SpanStyle(
                            color = mathColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append(latexExpr)
                    }
                }
                cursor = match.range.last + 1
            }
            if (cursor < rawText.length) {
                appendStyledMarkdownSpan(
                    text = rawText.substring(cursor),
                    baseColor = textColor
                )
            }
        }
        annotated to inlineMap
    }

    Text(
        text = annotatedString,
        inlineContent = inlineContentMap,
        color = textColor,
        fontSize = fontSize,
        fontWeight = fontWeight,
        lineHeight = (fontSize.value * 1.42f).sp,
        style = MaterialTheme.typography.bodyLarge
    )
}

private fun AnnotatedString.Builder.appendStyledMarkdownSpan(
    text: String,
    baseColor: Color
) {
    var cursor = 0
    for (match in BOLD_ITALIC_CODE_REGEX.findAll(text)) {
        if (match.range.first > cursor) {
            append(text.substring(cursor, match.range.first))
        }
        val token = match.value
        when {
            token.startsWith("`") && token.endsWith("`") && token.length >= 2 -> {
                withStyle(
                    SpanStyle(
                        color = Color(0xFF38BDF8),
                        background = Color(0xFF111827),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    append(" " + token.substring(1, token.length - 1) + " ")
                }
            }
            token.startsWith("**") && token.endsWith("**") && token.length >= 4 -> {
                withStyle(SpanStyle(color = baseColor, fontWeight = FontWeight.ExtraBold)) {
                    append(token.substring(2, token.length - 2))
                }
            }
            token.startsWith("*") && token.endsWith("*") && token.length >= 2 -> {
                withStyle(SpanStyle(color = baseColor, fontStyle = FontStyle.Italic)) {
                    append(token.substring(1, token.length - 1))
                }
            }
            token.startsWith("~~") && token.endsWith("~~") && token.length >= 4 -> {
                withStyle(SpanStyle(color = baseColor.copy(alpha = 0.65f), textDecoration = TextDecoration.LineThrough)) {
                    append(token.substring(2, token.length - 2))
                }
            }
            else -> append(token)
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        append(text.substring(cursor))
    }
}

private fun parseMarkdownSegments(raw: String): List<MarkdownSegment> {
    if (raw.isBlank()) return emptyList()
    val result = mutableListOf<MarkdownSegment>()
    val lines = raw.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // 1. Fenced code block ```lang
        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim()
            val codeBuf = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                if (codeBuf.isNotEmpty()) codeBuf.append('\n')
                codeBuf.append(lines[i])
                i++
            }
            result.add(MarkdownSegment.FencedCode(language = lang, code = codeBuf.toString()))
            i++
            continue
        }

        // 2. Display Math block $$ ... $$ or \[ ... \]
        if (trimmed.startsWith("$$") || trimmed.startsWith("\\[")) {
            val endDelimiter = if (trimmed.startsWith("$$")) "$$" else "\\]"
            val startDelimiter = if (trimmed.startsWith("$$")) "$$" else "\\["
            if (trimmed.length > startDelimiter.length + endDelimiter.length && trimmed.endsWith(endDelimiter)) {
                result.add(MarkdownSegment.DisplayMath(trimmed))
                i++
                continue
            }
            val mathBuf = StringBuilder()
            val firstLineAfterDelim = trimmed.removePrefix(startDelimiter).trim()
            if (firstLineAfterDelim.isNotEmpty()) {
                mathBuf.append(firstLineAfterDelim)
            }
            i++
            while (i < lines.size) {
                val cur = lines[i].trim()
                if (cur.endsWith(endDelimiter)) {
                    val beforeEnd = cur.removeSuffix(endDelimiter).trim()
                    if (beforeEnd.isNotEmpty()) {
                        if (mathBuf.isNotEmpty()) mathBuf.append('\n')
                        mathBuf.append(beforeEnd)
                    }
                    break
                } else {
                    if (mathBuf.isNotEmpty()) mathBuf.append('\n')
                    mathBuf.append(lines[i])
                }
                i++
            }
            result.add(MarkdownSegment.DisplayMath(mathBuf.toString()))
            i++
            continue
        }

        // 3. Horizontal Rule
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            result.add(MarkdownSegment.DividerRule)
            i++
            continue
        }

        // 4. Headings (#, ##, ###, ####)
        val headingMatch = Regex("""^(#{1,6})\s+(.+)$""").find(trimmed)
        if (headingMatch != null) {
            result.add(
                MarkdownSegment.Heading(
                    level = headingMatch.groupValues[1].length,
                    text = headingMatch.groupValues[2]
                )
            )
            i++
            continue
        }

        // 5. Blockquote (> ...)
        if (trimmed.startsWith("> ") || trimmed == ">") {
            result.add(MarkdownSegment.BlockQuote(trimmed.removePrefix(">").trim()))
            i++
            continue
        }

        // 6. Bullet or numbered list item
        val bulletMatch = Regex("""^(\s*)([-*+]|\d+\.)\s+(.+)$""").find(line)
        if (bulletMatch != null) {
            val indentSpaces = bulletMatch.groupValues[1].length / 2
            val markerRaw = bulletMatch.groupValues[2]
            val marker = if (markerRaw in listOf("-", "*", "+")) "•" else markerRaw
            result.add(
                MarkdownSegment.BulletItem(
                    indent = indentSpaces,
                    marker = marker,
                    text = bulletMatch.groupValues[3]
                )
            )
            i++
            continue
        }

        // 7. Blank line
        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // 8. Standard paragraph (accumulate consecutive plain lines)
        val paraBuf = StringBuilder(line)
        i++
        while (i < lines.size) {
            val next = lines[i]
            val nextTrimmed = next.trim()
            if (nextTrimmed.isEmpty() ||
                nextTrimmed.startsWith("```") ||
                nextTrimmed.startsWith("$$") ||
                nextTrimmed.startsWith("\\[") ||
                nextTrimmed.startsWith("#") ||
                nextTrimmed.startsWith("> ") ||
                next.matches(Regex("""^\s*([-*+]|\d+\.)\s+.+$"""))
            ) {
                break
            }
            paraBuf.append('\n').append(next)
            i++
        }
        result.add(MarkdownSegment.TextParagraph(paraBuf.toString()))
    }

    return result
}
