package com.jarvisai.assistant.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
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
    val BgCanvas = Color(0xFF0A0D14)
    val CardBg = Color(0xFF121722)
    val CardBgElevated = Color(0xFF171E2E)
    val CardBorder = Color(0xFF1E293B)
    val Terracotta = Color(0xFFD97757)
    val GeminiBlue = Color(0xFF4C8DF6)
    val GeminiPurple = Color(0xFFA855F7)
    val QuotaGreen = Color(0xFF10B981)
    val Warning = Color(0xFFF59E0B)
    val AccentTeal = Color(0xFF14B8A6)
    val AccentPink = Color(0xFFEC4899)
    val AccentIndigo = Color(0xFF6366F1)
    val Info = Color(0xFF38BDF8)
    val Danger = Color(0xFFEF4444)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
        border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
    ) {
        Column {
            items.forEachIndexed { i, item ->
                if (i > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 60.dp),
                        thickness = 0.5.dp,
                        color = Color.White.copy(alpha = 0.06f)
                    )
                }
                SettingsRowView(item)
            }
        }
    }
}

@Composable
private fun SettingsRowView(item: SettingsRowItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = item.onClick)
            .padding(start = 14.dp, end = 10.dp, top = 11.dp, bottom = 11.dp)
            .testTag("settings_row_${item.testTagId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(item.tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = item.tint,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = item.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = AgySettingsColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!item.value.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            if (item.valueColor != null) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(item.valueColor)
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = item.value,
                fontSize = 12.5.sp,
                color = AgySettingsColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open ${item.title}",
            tint = AgySettingsColors.TextSecondary.copy(alpha = 0.6f),
            modifier = Modifier
                .padding(start = 2.dp)
                .size(20.dp)
        )
    }
}
