package com.jarvisai.assistant.ide

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula

class CodeEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var onContentChanged: ((String) -> Unit)? = null
    private var isSettingContentProgrammatically = false

    val editor = CodeEditor(context).apply {
        typefaceText = Typeface.MONOSPACE
        typefaceLineNumber = Typeface.MONOSPACE
        setTextSize(14f)
        colorScheme = buildColorScheme(EditorThemeColors.CYBERPUNK_JARVIS)
        isLineNumberEnabled = true
        setPinLineNumber(true)
        isWordwrap = false
        isEditable = true
    }

    init {
        addView(editor, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        editor.subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
            if (!isSettingContentProgrammatically) {
                onContentChanged?.invoke(editor.text.toString())
            }
        }
    }

    fun setText(content: String) {
        isSettingContentProgrammatically = true
        editor.setText(content)
        isSettingContentProgrammatically = false
    }

    fun getText(): String = editor.text.toString()

    fun setWordWrap(enabled: Boolean) {
        editor.isWordwrap = enabled
    }

    fun undo() {
        if (editor.canUndo()) editor.undo()
    }

    fun redo() {
        if (editor.canRedo()) editor.redo()
    }

    private fun buildColorScheme(t: EditorThemeColors): EditorColorScheme {
        val scheme = SchemeDarcula()
        val bg = t.background or 0xFF000000.toInt()

        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, bg)
        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, bg)
        scheme.setColor(EditorColorScheme.LINE_NUMBER, t.muted)
        scheme.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, t.text)
        scheme.setColor(EditorColorScheme.LINE_DIVIDER, t.line)
        scheme.setColor(EditorColorScheme.CURRENT_LINE, t.panel)
        scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, t.selection)
        scheme.setColor(EditorColorScheme.SELECTION_HANDLE, t.accent)
        scheme.setColor(EditorColorScheme.SELECTION_INSERT, t.accent)
        scheme.setColor(EditorColorScheme.TEXT_NORMAL, t.text)
        scheme.setColor(EditorColorScheme.IDENTIFIER_NAME, t.text)
        scheme.setColor(EditorColorScheme.KEYWORD, t.keyword)
        scheme.setColor(EditorColorScheme.LITERAL, t.string)
        scheme.setColor(EditorColorScheme.COMMENT, t.comment)
        scheme.setColor(EditorColorScheme.OPERATOR, t.punctuation)
        scheme.setColor(EditorColorScheme.FUNCTION_NAME, t.function)
        scheme.setColor(EditorColorScheme.ANNOTATION, t.annotation)
        scheme.setColor(EditorColorScheme.HTML_TAG, t.tag)
        scheme.setColor(EditorColorScheme.ATTRIBUTE_NAME, t.attribute)
        return scheme
    }
}
