package com.jarvisai.assistant.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.theme.ThemeTerminal

@Composable
fun ThemeQuickPanel(
    modifier: Modifier = Modifier
) {
    var selectedName by remember { mutableStateOf(ThemeTerminal.currentPalette.name) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF111827),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThemeTerminal.ALL_PALETTES.forEach { palette ->
                val isSelected = selectedName == palette.name
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        ThemeTerminal.applyPalette(palette)
                        selectedName = palette.name
                    },
                    label = {
                        Text(
                            text = palette.name,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("quick_theme_chip_${palette.name}"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF6366F1).copy(alpha = 0.25f),
                        selectedLabelColor = Color(0xFF38BDF8),
                        containerColor = Color(0xFF0B0F19),
                        labelColor = Color(0xFF94A3B8)
                    )
                )
            }
        }
    }
}
