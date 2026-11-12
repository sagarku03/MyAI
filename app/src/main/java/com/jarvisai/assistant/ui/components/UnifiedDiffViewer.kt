package com.jarvisai.assistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class DiffLineType {
    object Addition : DiffLineType()
    object Deletion : DiffLineType()
    object Header : DiffLineType()
    object Context : DiffLineType()
}

data class DiffLine(
    val type: DiffLineType,
    val text: String
)

@Composable
fun UnifiedDiffViewer(
    rawDiff: String,
    modifier: Modifier = Modifier
) {
    val lines = remember(rawDiff) {
        rawDiff.lines().map { line ->
            when {
                line.startsWith("+++") || line.startsWith("---") -> DiffLine(DiffLineType.Header, line)
                line.startsWith("@@") -> DiffLine(DiffLineType.Header, line)
                line.startsWith("+") -> DiffLine(DiffLineType.Addition, line)
                line.startsWith("-") -> DiffLine(DiffLineType.Deletion, line)
                else -> DiffLine(DiffLineType.Context, line)
            }
        }
    }

    val additions = remember(lines) { lines.count { it.type == DiffLineType.Addition } }
    val deletions = remember(lines) { lines.count { it.type == DiffLineType.Deletion } }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF070B14),
        border = BorderStroke(0.8.dp, Color(0xFF1E293B))
    ) {
        Column {
            // Diff Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FILE DIFF",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    fontFamily = FontFamily.Monospace
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("+$additions", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("-$deletions", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }

            // Diff Lines List
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                lines.forEach { line ->
                    val bg = when (line.type) {
                        DiffLineType.Addition -> Color(0xFF10B981).copy(alpha = 0.15f)
                        DiffLineType.Deletion -> Color(0xFFEF4444).copy(alpha = 0.15f)
                        DiffLineType.Header -> Color(0xFF00E5FF).copy(alpha = 0.10f)
                        DiffLineType.Context -> Color.Transparent
                    }
                    val textColor = when (line.type) {
                        DiffLineType.Addition -> Color(0xFF6EE7B7)
                        DiffLineType.Deletion -> Color(0xFFFCA5A5)
                        DiffLineType.Header -> Color(0xFF38BDF8)
                        DiffLineType.Context -> Color(0xFFCBD5E1)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .padding(horizontal = 8.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = line.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            color = textColor,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
