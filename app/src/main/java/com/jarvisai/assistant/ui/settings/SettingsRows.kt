package com.jarvisai.assistant.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.ui.components.bouncyOverscroll

/**
 * Shared AntiGem Settings palette tokens harmonized cleanly with Jarvis Obsidian Dark theme.
 */
object AgySettingsColors {
    val BgCanvas = Color(0xFF0D0E12)
    val CardBg = Color(0xFF15171C)
    val CardBgElevated = Color(0xFF1D2027)
    val CardBorder = Color(0xFF2A2E38)
    val Terracotta = Color(0xFFD97757)
    val GeminiBlue = Color(0xFF5B8DEF)
    val GeminiPurple = Color(0xFF8B74EB)
    val QuotaGreen = Color(0xFF22C55E)
    val Warning = Color(0xFFEAB308)
    val AccentTeal = Color(0xFF14B8A6)
    val AccentPink = Color(0xFFEF4444)
    val AccentIndigo = Color(0xFF5B8DEF)
    val Info = Color(0xFF5B8DEF)
    val Danger = Color(0xFFEF4444)
    val TextPrimary = Color(0xFFECEEF2)
    val TextSecondary = Color(0xFF98A0AE)
}

/**
 * One row in an AntiGem-style grouped Settings card:
 * 32dp rounded-9dp tinted icon badge, 15sp title, optional colored status dot + value, right chevron.
 */
class SettingsRowItem(
    val icon: ImageVector,
    val tint: Color,
    val title: String,
    val value: String? = null,
    val valueColor: Color? = null,
    val testTagId: String = title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_'),
    val onClick: () -> Unit
)

@Composable
fun SettingsRowList(
    groups: List<List<SettingsRowItem>>,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    cardBg: Color = AgySettingsColors.CardBg,
    header: (@Composable () -> Unit)? = null
) {
    val nonEmpty = groups.filter { it.isNotEmpty() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .bouncyOverscroll()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        header?.invoke()
        nonEmpty.forEachIndexed { index, group ->
            if (index > 0 || header != null) Spacer(Modifier.height(14.dp))
            SettingsRowGroup(group, cardBg)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SettingsRowGroup(
    items: List<SettingsRowItem>,
    cardBg: Color = AgySettingsColors.CardBg
) {
    val themeColors = com.example.ui.theme.JarvisTheme.colors
    val resolvedCardBg = if (cardBg == AgySettingsColors.CardBg) themeColors.cardSurface else cardBg
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = resolvedCardBg,
        border = BorderStroke(1.dp, themeColors.cardBorder)
    ) {
        Column {
            items.forEachIndexed { i, item ->
                if (i > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 60.dp),
                        thickness = 1.dp,
                        color = themeColors.divider
                    )
                }
                SettingsRowView(item)
            }
        }
    }
}

@Composable
private fun SettingsRowView(item: SettingsRowItem) {
    val themeColors = com.example.ui.theme.JarvisTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = item.onClick)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
            .testTag("settings_row_${item.testTagId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(themeColors.surfaceElevated)
                .border(1.dp, themeColors.border, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = item.tint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = themeColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!item.value.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            if (item.valueColor != null) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(item.valueColor)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = item.value,
                fontSize = 12.sp,
                color = themeColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open ${item.title}",
            tint = themeColors.textMuted,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(20.dp)
        )
    }
}
