package com.jarvisai.assistant.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phase 4: Unified Git Diff Parser & Interactive Viewer (`UnifiedDiffViewer`) for the IDE subsystem.
 * Parses unified git diff hunks (`@@ -old,count +new,count @@`), computes old/new line numbers,
 * renders additions (`+`) in emerald green, deletions (`-`) in rose red, and hunk headers in slate blue.
 */
enum class DiffLineKind {
    ADDITION,
    DELETION,
    CONTEXT,
    HUNK_HEADER,
    META
}

data class ParsedDiffLine(
    val kind: DiffLineKind,
    val rawText: String,
    val content: String,
    val oldLineNumber: Int? = null,
    val newLineNumber: Int? = null
)

object UnifiedDiffEngine {

    private val hunkHeaderRegex = Regex("^@@\\s+-(\\d+)(?:,\\d+)?\\s+\\+(\\d+)(?:,\\d+)?\\s+@@(.*)$")

    fun parse(rawDiff: String): List<ParsedDiffLine> {
        if (rawDiff.isBlank()) return emptyList()
        val lines = rawDiff.lines()
        val result = ArrayList<ParsedDiffLine>(lines.size)

        var currentOldLine = 1
        var currentNewLine = 1

        for (line in lines) {
            when {
                line.startsWith("@@") -> {
                    val match = hunkHeaderRegex.find(line)
                    if (match != null) {
                        currentOldLine = match.groupValues[1].toIntOrNull() ?: 1
                        currentNewLine = match.groupValues[2].toIntOrNull() ?: 1
                    }
                    result.add(
                        ParsedDiffLine(
                            kind = DiffLineKind.HUNK_HEADER,
                            rawText = line,
                            content = line
                        )
                    )
                }
                line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff --git") || line.startsWith("index ") -> {
                    result.add(
                        ParsedDiffLine(
                            kind = DiffLineKind.META,
                            rawText = line,
                            content = line
                        )
                    )
                }
                line.startsWith("+") -> {
                    result.add(
                        ParsedDiffLine(
                            kind = DiffLineKind.ADDITION,
                            rawText = line,
                            content = line.drop(1),
                            oldLineNumber = null,
                            newLineNumber = currentNewLine++
                        )
                    )
                }
                line.startsWith("-") -> {
                    result.add(
                        ParsedDiffLine(
                            kind = DiffLineKind.DELETION,
                            rawText = line,
                            content = line.drop(1),
                            oldLineNumber = currentOldLine++,
                            newLineNumber = null
                        )
                    )
                }
                else -> {
                    val clean = if (line.startsWith(" ")) line.drop(1) else line
                    result.add(
                        ParsedDiffLine(
                            kind = DiffLineKind.CONTEXT,
                            rawText = line,
                            content = clean,
                            oldLineNumber = currentOldLine++,
                            newLineNumber = currentNewLine++
                        )
                    )
                }
            }
        }
        return result
    }
}

@Composable
fun UnifiedDiffViewer(
    filePath: String,
    rawDiff: String,
    isStaged: Boolean = false,
    onOpenWorkingCopy: (() -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val parsedLines = remember(rawDiff) { UnifiedDiffEngine.parse(rawDiff) }
    val additionsCount = remember(parsedLines) { parsedLines.count { it.kind == DiffLineKind.ADDITION } }
    val deletionsCount = remember(parsedLines) { parsedLines.count { it.kind == DiffLineKind.DELETION } }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("ide_unified_diff_viewer"),
        color = Color(0xFF0A0F1D)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111C35))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = filePath.substringAfterLast('/'),
                            color = Color(0xFFF0F6FC),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = if (isStaged) Color(0xFF238636).copy(alpha = 0.25f) else Color(0xFFD29922).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (isStaged) "STAGED" else "WORKING TREE",
                                color = if (isStaged) Color(0xFF3FB950) else Color(0xFFFCD34D),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "+$additionsCount additions",
                            color = Color(0xFF34D399),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "-$deletionsCount deletions",
                            color = Color(0xFFF87171),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onOpenWorkingCopy != null) {
                        TextButton(onClick = onOpenWorkingCopy) {
                            Text("Open File", color = Color(0xFF38BDF8), fontSize = 12.sp)
                        }
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("diff_viewer_close_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Diff Viewer", tint = Color(0xFF94A3B8))
                    }
                }
            }

            if (parsedLines.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No diff changes to display for $filePath",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 6.dp)
                ) {
                    itemsIndexed(parsedLines) { _, line ->
                        val bgColor = when (line.kind) {
                            DiffLineKind.ADDITION -> Color(0xFF064E3B).copy(alpha = 0.42f)
                            DiffLineKind.DELETION -> Color(0xFF7F1D1D).copy(alpha = 0.42f)
                            DiffLineKind.HUNK_HEADER -> Color(0xFF1E3A8A).copy(alpha = 0.38f)
                            DiffLineKind.META -> Color(0xFF0F172A)
                            DiffLineKind.CONTEXT -> Color.Transparent
                        }
                        val prefixChar = when (line.kind) {
                            DiffLineKind.ADDITION -> "+"
                            DiffLineKind.DELETION -> "-"
                            else -> " "
                        }
                        val textColor = when (line.kind) {
                            DiffLineKind.ADDITION -> Color(0xFF6EE7B7)
                            DiffLineKind.DELETION -> Color(0xFFFCA5A5)
                            DiffLineKind.HUNK_HEADER -> Color(0xFF60A5FA)
                            DiffLineKind.META -> Color(0xFF64748B)
                            DiffLineKind.CONTEXT -> Color(0xFFE2E8F0)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bgColor)
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Old line number column
                            Text(
                                text = line.oldLineNumber?.toString()?.padStart(4, ' ') ?: "    ",
                                color = Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // New line number column
                            Text(
                                text = line.newLineNumber?.toString()?.padStart(4, ' ') ?: "    ",
                                color = Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Sign & code line
                            Text(
                                text = if (line.kind == DiffLineKind.HUNK_HEADER || line.kind == DiffLineKind.META) {
                                    line.content
                                } else {
                                    "$prefixChar ${line.content}"
                                },
                                color = textColor,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
