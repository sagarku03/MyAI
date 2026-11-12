package com.jarvisai.assistant.ide

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.FrameLayout
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import java.io.File

/**
 * Production Sora CodeEditor wrapper with dynamic 15+ language syntax highlighting,
 * TextMate grammar resolution + JavaLanguage C-family fallback, Cyberpunk Jarvis dark theme,
 * line numbering, word wrap, undo/redo, and programmatic text sync with ActiveCodeEditorRegistry.
 */
class CodeEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var onContentChanged: ((String) -> Unit)? = null
    private var isSettingContentProgrammatically = false
    private var currentFilePath: String? = null
    private var currentLanguageScope: String = "source.java"

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
                val updated = editor.text.toString()
                ActiveCodeEditorRegistry.activeContent = updated
                ActiveCodeEditorRegistry.isDirty = true
                onContentChanged?.invoke(updated)
            }
        }
    }

    fun setFile(file: File, content: String) {
        currentFilePath = file.absolutePath
        ActiveCodeEditorRegistry.activeFilePath = file.absolutePath
        ActiveCodeEditorRegistry.activeFileTitle = file.name
        ActiveCodeEditorRegistry.activeContent = content
        ActiveCodeEditorRegistry.isDirty = false
        setText(content)
        applySyntaxHighlighting(file.name)
    }

    fun setText(content: String) {
        isSettingContentProgrammatically = true
        editor.setText(content)
        isSettingContentProgrammatically = false
    }

    fun getText(): String = editor.text.toString()

    fun getDetectedLanguageScope(): String = currentLanguageScope

    fun setWordWrap(enabled: Boolean) {
        editor.isWordwrap = enabled
    }

    fun undo() {
        if (editor.canUndo()) editor.undo()
    }

    fun redo() {
        if (editor.canRedo()) editor.redo()
    }

    /**
     * Resolves and applies syntax grammar for 15+ developer languages:
     * Kotlin, Java, Python, JavaScript, TypeScript, Go, Rust, HTML, CSS,
     * JSON, Shell/Bash, C, C++, XML, YAML, and Markdown.
     */
    fun applySyntaxHighlighting(fileName: String) {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val scopeName = resolveTextMateScope(ext)
        currentLanguageScope = scopeName

        // First attempt TextMate grammar if registered in GrammarRegistry
        try {
            val tmLanguage = TextMateLanguage.create(scopeName, true)
            editor.setEditorLanguage(tmLanguage)
            try {
                val tmScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                editor.colorScheme = tmScheme
            } catch (_: Exception) {
                editor.colorScheme = buildColorScheme(EditorThemeColors.CYBERPUNK_JARVIS)
            }
            return
        } catch (_: Throwable) {
            // Grammar bundle not registered in TextMate registry; use built-in language lexer
        }

        // Use UniversalCodeLanguage async incremental tokenizer for 20+ languages
        try {
            editor.setEditorLanguage(UniversalCodeLanguage(ext))
        } catch (_: Exception) {
            try {
                editor.setEditorLanguage(JavaLanguage())
            } catch (_: Exception) {
                editor.setEditorLanguage(EmptyLanguage())
            }
        }
        editor.colorScheme = buildColorScheme(EditorThemeColors.CYBERPUNK_JARVIS)
    }

    private fun resolveTextMateScope(ext: String): String {
        return when (ext) {
            "kt", "kts" -> "source.kotlin"
            "java" -> "source.java"
            "py" -> "source.python"
            "js", "mjs", "cjs" -> "source.js"
            "jsx" -> "source.js.jsx"
            "ts" -> "source.ts"
            "tsx" -> "source.tsx"
            "go" -> "source.go"
            "rs" -> "source.rust"
            "html", "htm" -> "text.html.basic"
            "css" -> "source.css"
            "json" -> "source.json"
            "sh", "bash", "zsh" -> "source.shell"
            "c", "h" -> "source.c"
            "cpp", "cc", "cxx", "hpp" -> "source.cpp"
            "xml", "svg" -> "text.xml"
            "yaml", "yml" -> "source.yaml"
            "md", "markdown" -> "text.html.markdown"
            else -> "source.java"
        }
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
