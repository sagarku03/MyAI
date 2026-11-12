package com.jarvisai.assistant.ide

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.analysis.IncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.brackets.OnlineBracketsMatcher
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.SymbolPairMatch
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Phase 4: High-Speed Universal Code Language & Incremental Tokenizer (`UniversalCodeLanguage`)
 * for Rosemoe Sora Editor (`io.github.rosemoe.sora`).
 *
 * Provides zero-overhead syntax highlighting across 20+ programming languages
 * (Kotlin, Java, Python, JS, TS, Go, Rust, C, C++, C#, Swift, PHP, Ruby, SQL, Shell, HTML, XML, CSS, JSON, YAML)
 * without requiring multi-megabyte external TextMate grammar bundles.
 */
enum class CodeTokenType {
    NORMAL,
    KEYWORD,
    STRING,
    NUMBER,
    COMMENT,
    FUNCTION,
    ANNOTATION,
    TAG,
    ATTRIBUTE,
    OPERATOR
}

data class CodeToken(
    val startColumn: Int,
    val endColumn: Int,
    val type: CodeTokenType
)

class UniversalCodeLanguage(
    fileExtension: String = "kt"
) : EmptyLanguage() {

    private val analyzer = UniversalCodeAnalyzer(fileExtension.lowercase())
    private val bracketMatcher = OnlineBracketsMatcher(
        charArrayOf('(', ')', '[', ']', '{', '}'),
        100_000
    )

    override fun getAnalyzeManager(): AnalyzeManager = analyzer

    override fun getSymbolPairs(): SymbolPairMatch = SymbolPairMatch.DefaultSymbolPairs()

    override fun useTab(): Boolean = false

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int {
        val text = content.getLine(line).trimEnd()
        return if (text.endsWith("{") || text.endsWith(":") || text.endsWith("(") || text.endsWith("[")) {
            4
        } else {
            0
        }
    }

    fun getBracketMatcher(): OnlineBracketsMatcher = bracketMatcher

    override fun destroy() {
        analyzer.destroy()
    }
}

class UniversalCodeAnalyzer(
    private val ext: String
) : AsyncIncrementalAnalyzeManager<Int, CodeToken>() {

    companion object {
        private const val STATE_NORMAL = 0
        private const val STATE_BLOCK_COMMENT = 1
        private const val STATE_MULTILINE_STRING = 2

        val UNIVERSAL_KEYWORDS = setOf(
            // Control & branching
            "if", "else", "elif", "when", "switch", "case", "default", "for", "while", "do",
            "break", "continue", "return", "yield", "try", "catch", "finally", "throw", "throws",
            "raise", "except", "match", "defer", "select", "go",
            // Declarations & modifiers
            "fun", "function", "def", "fn", "class", "interface", "object", "struct", "enum",
            "trait", "impl", "type", "typealias", "val", "var", "let", "const", "final", "static",
            "public", "private", "protected", "internal", "open", "abstract", "override", "suspend",
            "inline", "data", "sealed", "companion", "init", "constructor", "async", "await",
            "export", "import", "from", "package", "namespace", "using", "module", "require",
            "include", "extends", "implements", "where", "in", "out", "is", "as", "typeof", "instanceof",
            // Primitives, literals & SQL
            "true", "false", "null", "nil", "None", "True", "False", "undefined", "this", "super", "self",
            "void", "int", "long", "float", "double", "boolean", "bool", "char", "byte", "short",
            "string", "String", "Int", "Long", "Float", "Double", "Boolean", "Unit", "Any", "List", "Map", "Set",
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
            "CREATE", "TABLE", "DROP", "ALTER", "JOIN", "INNER", "LEFT", "RIGHT", "ON", "GROUP", "BY", "ORDER", "LIMIT"
        )
    }

    override fun getInitialState(): Int = STATE_NORMAL

    override fun stateEquals(state: Int?, another: Int?): Boolean = state == another

    override fun tokenizeLine(
        line: CharSequence,
        state: Int,
        lineIndex: Int
    ): IncrementalAnalyzeManager.LineTokenizeResult<Int, CodeToken> {
        val tokens = ArrayList<CodeToken>()
        val length = line.length
        if (length == 0) {
            return IncrementalAnalyzeManager.LineTokenizeResult(state, tokens)
        }

        var currentState = state
        var i = 0
        val isMarkup = ext in setOf("html", "htm", "xml", "svg", "vue", "jsx", "tsx")
        val supportsHashComment = ext in setOf("py", "sh", "bash", "zsh", "rb", "yaml", "yml", "toml", "dockerfile", "conf", "env", "php")

        while (i < length) {
            // 1. Inside multi-line block comment /* ... */
            if (currentState == STATE_BLOCK_COMMENT) {
                val endIdx = indexOfPair(line, '*', '/', i)
                if (endIdx == -1) {
                    tokens.add(CodeToken(i, length, CodeTokenType.COMMENT))
                    i = length
                } else {
                    tokens.add(CodeToken(i, endIdx + 2, CodeTokenType.COMMENT))
                    i = endIdx + 2
                    currentState = STATE_NORMAL
                }
                continue
            }

            // 2. Inside multi-line triple-quoted string """ ... """
            if (currentState == STATE_MULTILINE_STRING) {
                val endIdx = indexOfTripleQuote(line, i)
                if (endIdx == -1) {
                    tokens.add(CodeToken(i, length, CodeTokenType.STRING))
                    i = length
                } else {
                    tokens.add(CodeToken(i, endIdx + 3, CodeTokenType.STRING))
                    i = endIdx + 3
                    currentState = STATE_NORMAL
                }
                continue
            }

            val ch = line[i]

            if (ch.isWhitespace()) {
                i++
                continue
            }

            // 3. Single-line comment // or --
            if (ch == '/' && i + 1 < length && line[i + 1] == '/') {
                tokens.add(CodeToken(i, length, CodeTokenType.COMMENT))
                break
            }
            if (ch == '-' && i + 1 < length && line[i + 1] == '-' && ext == "sql") {
                tokens.add(CodeToken(i, length, CodeTokenType.COMMENT))
                break
            }

            // 4. Multi-line comment start /*
            if (ch == '/' && i + 1 < length && line[i + 1] == '*') {
                val closeIdx = indexOfPair(line, '*', '/', i + 2)
                if (closeIdx == -1) {
                    tokens.add(CodeToken(i, length, CodeTokenType.COMMENT))
                    currentState = STATE_BLOCK_COMMENT
                    break
                } else {
                    tokens.add(CodeToken(i, closeIdx + 2, CodeTokenType.COMMENT))
                    i = closeIdx + 2
                    continue
                }
            }

            // 5. Hash comment #
            if (ch == '#' && supportsHashComment) {
                tokens.add(CodeToken(i, length, CodeTokenType.COMMENT))
                break
            }

            // 6. Annotations / Decorators (@Composable, #include)
            if ((ch == '@' || (ch == '#' && !supportsHashComment)) && i + 1 < length && (line[i + 1].isLetter() || line[i + 1] == '_')) {
                val start = i
                i++
                while (i < length && (line[i].isLetterOrDigit() || line[i] == '_' || line[i] == '.')) {
                    i++
                }
                tokens.add(CodeToken(start, i, CodeTokenType.ANNOTATION))
                continue
            }

            // 7. Triple-quoted string """
            if (ch == '"' && i + 2 < length && line[i + 1] == '"' && line[i + 2] == '"') {
                val closeIdx = indexOfTripleQuote(line, i + 3)
                if (closeIdx == -1) {
                    tokens.add(CodeToken(i, length, CodeTokenType.STRING))
                    currentState = STATE_MULTILINE_STRING
                    break
                } else {
                    tokens.add(CodeToken(i, closeIdx + 3, CodeTokenType.STRING))
                    i = closeIdx + 3
                    continue
                }
            }

            // 8. String literal "...", '...', `...`
            if (ch == '"' || ch == '\'' || ch == '`') {
                val quote = ch
                val start = i
                i++
                while (i < length) {
                    val c = line[i]
                    if (c == '\\') {
                        i += 2
                        continue
                    }
                    if (c == quote) {
                        i++
                        break
                    }
                    i++
                }
                tokens.add(CodeToken(start, i.coerceAtMost(length), CodeTokenType.STRING))
                continue
            }

            // 9. Markup Tags <div ...> or </div>
            if (isMarkup && ch == '<' && i + 1 < length && (line[i + 1].isLetter() || line[i + 1] == '/' || line[i + 1] == '!')) {
                val start = i
                i++
                if (i < length && line[i] == '/') i++
                while (i < length && (line[i].isLetterOrDigit() || line[i] == '-' || line[i] == ':' || line[i] == '_')) {
                    i++
                }
                tokens.add(CodeToken(start, i, CodeTokenType.TAG))
                continue
            }

            // 10. Numeric literal
            if (ch.isDigit() || (ch == '.' && i + 1 < length && line[i + 1].isDigit())) {
                val start = i
                i++
                while (i < length && (line[i].isLetterOrDigit() || line[i] == '.' || line[i] == '_')) {
                    i++
                }
                tokens.add(CodeToken(start, i, CodeTokenType.NUMBER))
                continue
            }

            // 11. Identifier, Keyword, or Function invocation
            if (ch.isLetter() || ch == '_' || ch == '$') {
                val start = i
                i++
                while (i < length && (line[i].isLetterOrDigit() || line[i] == '_' || line[i] == '$' || (isMarkup && line[i] == '-'))) {
                    i++
                }
                val word = line.subSequence(start, i).toString()
                var nextNonSpace = i
                while (nextNonSpace < length && line[nextNonSpace] == ' ') {
                    nextNonSpace++
                }

                val tokenType = when {
                    word in UNIVERSAL_KEYWORDS -> CodeTokenType.KEYWORD
                    nextNonSpace < length && line[nextNonSpace] == '(' -> CodeTokenType.FUNCTION
                    isMarkup && nextNonSpace < length && line[nextNonSpace] == '=' -> CodeTokenType.ATTRIBUTE
                    word.first().isUpperCase() -> CodeTokenType.ANNOTATION
                    else -> CodeTokenType.NORMAL
                }
                tokens.add(CodeToken(start, i, tokenType))
                continue
            }

            // 12. Operators & punctuation
            val start = i
            i++
            tokens.add(CodeToken(start, i, CodeTokenType.OPERATOR))
        }

        return IncrementalAnalyzeManager.LineTokenizeResult(currentState, tokens)
    }

    override fun generateSpansForLine(
        tokensResult: IncrementalAnalyzeManager.LineTokenizeResult<Int, CodeToken>
    ): List<Span> {
        val spans = ArrayList<Span>()
        val tokenList: List<CodeToken> = tokensResult.tokens ?: emptyList()
        if (tokenList.isEmpty()) {
            spans.add(Span.obtain(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
            return spans
        }

        var lastEnd = 0
        for (token in tokenList) {
            if (token.startColumn > lastEnd) {
                spans.add(Span.obtain(lastEnd, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
            }
            val style = when (token.type) {
                CodeTokenType.KEYWORD -> TextStyle.makeStyle(EditorColorScheme.KEYWORD, 0, true, false, false)
                CodeTokenType.STRING -> TextStyle.makeStyle(EditorColorScheme.LITERAL)
                CodeTokenType.NUMBER -> TextStyle.makeStyle(EditorColorScheme.LITERAL)
                CodeTokenType.COMMENT -> TextStyle.makeStyle(EditorColorScheme.COMMENT, 0, false, true, false)
                CodeTokenType.FUNCTION -> TextStyle.makeStyle(EditorColorScheme.FUNCTION_NAME)
                CodeTokenType.ANNOTATION -> TextStyle.makeStyle(EditorColorScheme.ANNOTATION)
                CodeTokenType.TAG -> TextStyle.makeStyle(EditorColorScheme.HTML_TAG, 0, true, false, false)
                CodeTokenType.ATTRIBUTE -> TextStyle.makeStyle(EditorColorScheme.ATTRIBUTE_NAME)
                CodeTokenType.OPERATOR -> TextStyle.makeStyle(EditorColorScheme.OPERATOR)
                CodeTokenType.NORMAL -> TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)
            }
            spans.add(Span.obtain(token.startColumn, style))
            lastEnd = token.endColumn
        }
        spans.add(Span.obtain(lastEnd, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
        return spans
    }

    override fun computeBlocks(
        text: Content?,
        delegate: CodeBlockAnalyzeDelegate?
    ): List<CodeBlock> {
        return emptyList()
    }

    private fun indexOfPair(seq: CharSequence, first: Char, second: Char, startIndex: Int): Int {
        var i = startIndex
        while (i + 1 < seq.length) {
            if (seq[i] == first && seq[i + 1] == second) return i
            i++
        }
        return -1
    }

    private fun indexOfTripleQuote(seq: CharSequence, startIndex: Int): Int {
        var i = startIndex
        while (i + 2 < seq.length) {
            if (seq[i] == '"' && seq[i + 1] == '"' && seq[i + 2] == '"') return i
            i++
        }
        return -1
    }
}
