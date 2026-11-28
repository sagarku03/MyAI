package com.jarvisai.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.RoyalPurplePrimary
import com.example.ui.theme.SoftLavenderGlow
import com.example.ui.theme.TextPureWhite
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.ui.JarvisDestination

// ============================================================================
// APPLE iOS 26 "LIQUID GLASS" MULTI-PALETTE ADAPTIVE DESIGN SYSTEM
// ============================================================================

/**
 * 1. JarvisPremiumBackground
 * Matte neutral canvas background aligned to active dark/light palette.
 */
@Composable
fun JarvisPremiumBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = JarvisTheme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        content()
    }
}

/**
 * 2. JarvisGlassPanel (Matte Hairline Container Card)
 */
@Composable
fun JarvisGlassPanel(
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    cornerRadius: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val themeColors = JarvisTheme.colors
    val effectiveBorder = if (accentColor == Color.Unspecified || accentColor == RoyalPurplePrimary) {
        themeColors.cardBorder
    } else {
        accentColor.copy(alpha = 0.35f)
    }
    val shape = RoundedCornerShape(cornerRadius)

    val panelModifier = modifier
        .clip(shape)
        .background(themeColors.cardSurface)
        .border(
            BorderStroke(width = 1.dp, color = effectiveBorder),
            shape = shape
        )
        .let { base ->
            if (onClick != null) base.clickable(onClick = onClick) else base
        }
        .padding(contentPadding)

    Column(modifier = panelModifier, content = content)
}

/**
 * 3. JarvisFloatingCard
 */
@Composable
fun JarvisFloatingCard(
    modifier: Modifier = Modifier,
    glowColor: Color = Color.Unspecified,
    cornerRadius: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    JarvisGlassPanel(
        modifier = modifier,
        accentColor = glowColor,
        cornerRadius = cornerRadius,
        contentPadding = contentPadding,
        onClick = onClick,
        content = content
    )
}

/**
 * 4. LiquidGlassGroupCard (iOS 26 Grouped Frosted Card with Vibrant Section Header)
 */
@Composable
fun LiquidGlassGroupCard(
    modifier: Modifier = Modifier,
    sectionTitle: String? = null,
    sectionHeader: String? = null,
    sectionBadge: String? = null,
    accentColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit
) {
    val themeColors = JarvisTheme.colors
    val effectiveAccent = if (accentColor == Color.Unspecified || accentColor == RoyalPurplePrimary) {
        themeColors.accent
    } else {
        accentColor
    }
    val resolvedHeader = sectionHeader ?: sectionTitle

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!resolvedHeader.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = resolvedHeader,
                    style = MaterialTheme.typography.labelMedium,
                    color = themeColors.textSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                if (!sectionBadge.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(effectiveAccent.copy(alpha = 0.12f))
                            .border(1.dp, effectiveAccent.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = sectionBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = effectiveAccent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        val shape = RoundedCornerShape(12.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(themeColors.cardSurface)
                .border(
                    width = 1.dp,
                    color = themeColors.cardBorder,
                    shape = shape
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            content = content
        )
    }
}

/**
 * 5. LiquidGlassSettingsRow (Clean Matte Settings Row)
 */
@Composable
fun LiquidGlassSettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueText: String? = null,
    valueColor: Color = Color.Unspecified,
    iconTint: Color = Color.Unspecified,
    showDivider: Boolean = true,
    testTag: String? = null
) {
    val themeColors = JarvisTheme.colors
    val effectiveIconTint = if (iconTint == Color.Unspecified || iconTint == RoyalPurplePrimary) {
        themeColors.accent
    } else {
        iconTint
    }
    val effectiveValueColor = if (valueColor == Color.Unspecified || valueColor == SoftLavenderGlow) {
        themeColors.accent
    } else {
        valueColor
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .let { if (testTag != null) it.testTag(testTag) else it },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(themeColors.surfaceElevated)
                        .border(
                            width = 1.dp,
                            color = themeColors.border,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = effectiveIconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = themeColors.textPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = themeColors.textSecondary,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!valueText.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(themeColors.surfacePill)
                            .border(1.dp, themeColors.border, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = valueText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (valueColor == Color.Unspecified) themeColors.textSecondary else effectiveValueColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = themeColors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 60.dp, end = 12.dp),
                thickness = 1.dp,
                color = themeColors.divider
            )
        }
    }
}

/**
 * 6. LiquidGlassSegmentedBar (Matte Segmented Control)
 */
@Composable
fun <T> LiquidGlassSegmentedBar(
    items: List<T>,
    selectedItem: T,
    labelProvider: (T) -> String = { it.toString() },
    itemLabel: ((T) -> String)? = null,
    iconProvider: ((T) -> ImageVector)? = null,
    onSelect: (T) -> Unit = {},
    onItemSelected: ((T) -> Unit)? = null,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null
) {
    val themeColors = JarvisTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(themeColors.surfaceElevated)
            .border(
                width = 1.dp,
                color = themeColors.border,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val selected = item == selectedItem
            val label = itemLabel?.invoke(item) ?: labelProvider(item)
            val icon = iconProvider?.invoke(item)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) themeColors.accent else Color.Transparent)
                    .clickable {
                        onItemSelected?.invoke(item) ?: onSelect(item)
                    }
                    .padding(vertical = 8.dp, horizontal = 8.dp)
                    .let {
                        if (testTagPrefix != null) {
                            it.testTag("${testTagPrefix}_${item.toString().lowercase()}")
                        } else {
                            it
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) themeColors.onPrimary else themeColors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) themeColors.onPrimary else themeColors.textSecondary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 7. LiquidGlassBottomSheet (Matte Modal Bottom Sheet)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassBottomSheet(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit = {},
    onDismissRequest: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val themeColors = JarvisTheme.colors
    val resolvedDismiss = onDismissRequest ?: onDismiss
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = resolvedDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = if (themeColors.isDark) themeColors.surfaceElevated else themeColors.cardSurface,
        contentColor = themeColors.textPrimary,
        scrimColor = Color.Black.copy(alpha = 0.60f),
        dragHandle = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(themeColors.textMuted.copy(alpha = 0.45f))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = themeColors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = themeColors.textSecondary,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
            HorizontalDivider(
                thickness = 0.8.dp,
                color = themeColors.divider
            )
            content()
        }
    }
}

/**
 * 8. JarvisGradientButton (Primary Flat Action CTA)
 */
@Composable
fun JarvisGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    gradientColors: List<Color> = emptyList()
) {
    val themeColors = JarvisTheme.colors
    val fillColor = gradientColors.firstOrNull() ?: themeColors.accent
    val shape = RoundedCornerShape(10.dp)
    val alpha = if (enabled) 1f else 0.40f

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(fillColor.copy(alpha = alpha))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = themeColors.onPrimary.copy(alpha = if (enabled) 1f else 0.7f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = themeColors.onPrimary.copy(alpha = if (enabled) 1f else 0.7f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 9. JarvisPrimaryActionButton & JarvisSecondaryActionButton
 */
@Composable
fun JarvisPrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accentColor: Color = Color.Unspecified
) {
    val themeColors = JarvisTheme.colors
    val effectiveAccent = if (accentColor == Color.Unspecified || accentColor == RoyalPurplePrimary) {
        themeColors.accent
    } else {
        accentColor
    }
    val shape = RoundedCornerShape(10.dp)

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = effectiveAccent,
            contentColor = themeColors.onPrimary,
            disabledContainerColor = effectiveAccent.copy(alpha = 0.28f),
            disabledContentColor = themeColors.textMuted
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = themeColors.onPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = themeColors.onPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun JarvisSecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accentColor: Color = Color.Unspecified
) {
    val themeColors = JarvisTheme.colors
    val effectiveAccent = if (accentColor == Color.Unspecified || accentColor == SoftLavenderGlow) {
        themeColors.accent
    } else {
        accentColor
    }

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (effectiveAccent == themeColors.statusRoseRed) {
                effectiveAccent.copy(alpha = if (enabled) 0.50f else 0.20f)
            } else {
                themeColors.border
            }
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = themeColors.surfaceElevated,
            contentColor = themeColors.textPrimary
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = effectiveAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (effectiveAccent == themeColors.statusRoseRed) themeColors.statusRoseRed else themeColors.textPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 10. JarvisIconControl (Matte Icon Button)
 */
@Composable
fun JarvisIconControl(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    activeColor: Color = Color.Unspecified,
    isActive: Boolean = false,
    size: Dp = 44.dp
) {
    val themeColors = JarvisTheme.colors
    val effectiveActiveColor = if (activeColor == Color.Unspecified || activeColor == RoyalPurplePrimary) {
        themeColors.accent
    } else {
        activeColor
    }
    val effectiveTint = if (tint == Color.Unspecified || tint == TextPureWhite) {
        themeColors.textPrimary
    } else {
        tint
    }
    val shape = CircleShape

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (isActive) effectiveActiveColor else themeColors.surfaceElevated)
            .border(
                width = 1.dp,
                color = if (isActive) effectiveActiveColor else themeColors.border,
                shape = shape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) themeColors.onPrimary else effectiveTint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 11. JarvisPremiumTopBar (Docked Matte Header Bar)
 */
@Composable
fun JarvisPremiumTopBar(
    title: String,
    subtitle: String,
    status: AssistantStatus,
    onBackClick: (() -> Unit)? = null,
    trailingActions: (@Composable RowScope.() -> Unit)? = null
) {
    val themeColors = JarvisTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(themeColors.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (onBackClick != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(themeColors.surfaceElevated)
                            .border(
                                width = 1.dp,
                                color = themeColors.border,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable(onClick = onBackClick)
                            .testTag("hud_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate Back",
                            tint = themeColors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = themeColors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = themeColors.textSecondary,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (trailingActions != null) {
                    trailingActions()
                }
                JarvisStatusBadge(status = status)
            }
        }
        HorizontalDivider(thickness = 1.dp, color = themeColors.divider)
    }
}

/**
 * 12. JarvisStatusBadge (Clean State-Aware Status Chip)
 */
@Composable
fun JarvisStatusBadge(
    status: AssistantStatus,
    modifier: Modifier = Modifier
) {
    val themeColors = JarvisTheme.colors
    val statusColor by animateColorAsState(
        targetValue = when (status) {
            AssistantStatus.IDLE -> themeColors.statusEmerald
            AssistantStatus.LISTENING -> themeColors.statusEmerald
            AssistantStatus.THINKING -> themeColors.statusAmber
            AssistantStatus.SPEAKING -> themeColors.accent
        },
        label = "statusBadgeColor"
    )

    val dotAlpha = if (status != AssistantStatus.IDLE) {
        val infiniteTransition = rememberInfiniteTransition(label = "statusDotPulse")
        val anim by infiniteTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dotAlpha"
        )
        anim
    } else {
        1f
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(themeColors.surfaceElevated)
            .border(
                width = 1.dp,
                color = themeColors.border,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("assistant_status_pill")
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = dotAlpha))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            color = themeColors.textSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 13. JarvisModernBottomNavigation (Docked Hairline Navigation Bar)
 */
@Composable
fun JarvisModernBottomNavigation(
    destinations: List<JarvisDestination>,
    currentDestination: JarvisDestination,
    onNavigate: (JarvisDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = JarvisTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(themeColors.surface)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(thickness = 1.dp, color = themeColors.divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEach { dest ->
                val selected = currentDestination == dest ||
                    (dest == JarvisDestination.GENERAL_SETTINGS && (currentDestination == JarvisDestination.API_SETTINGS || currentDestination == JarvisDestination.MEMORY || currentDestination == JarvisDestination.MEMORY_REVIEW)) ||
                    (dest == JarvisDestination.CHAT && (currentDestination == JarvisDestination.VISION || currentDestination == JarvisDestination.DOCUMENT)) ||
                    (dest == JarvisDestination.AGENT_HUB && (currentDestination == JarvisDestination.BROWSER || currentDestination == JarvisDestination.WORKFLOW || currentDestination == JarvisDestination.CONTROLS || currentDestination == JarvisDestination.NOTIFICATIONS))

                Box(
                    modifier = Modifier
                        .weight(if (selected) 1.35f else 0.92f)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) themeColors.accent.copy(alpha = 0.14f)
                            else Color.Transparent
                        )
                        .clickable { onNavigate(dest) }
                        .padding(vertical = 8.dp, horizontal = 8.dp)
                        .testTag("bottom_nav_${dest.route}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = dest.icon,
                            contentDescription = dest.label,
                            tint = if (selected) themeColors.accent else themeColors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        if (selected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = dest.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = themeColors.accent,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 14. JarvisSparklineWave
 * Smooth glowing Bezier curve using active theme tokens.
 */
@Composable
fun JarvisSparklineWave(
    points: List<Float>,
    lineColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.5.dp
) {
    val themeColors = JarvisTheme.colors
    val effectiveLineColor = if (lineColor == Color.Unspecified || lineColor == SoftLavenderGlow) {
        themeColors.accent
    } else {
        lineColor
    }

    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val stepX = w / (points.size - 1).coerceAtLeast(1)

        val coords = points.mapIndexed { idx, normY ->
            Offset(
                x = idx * stepX,
                y = h - (normY.coerceIn(0.08f, 0.92f) * h)
            )
        }

        val strokePath = Path().apply {
            moveTo(coords.first().x, coords.first().y)
            for (i in 0 until coords.size - 1) {
                val p0 = coords[i]
                val p1 = coords[i + 1]
                val controlX1 = (p0.x + p1.x) / 2f
                cubicTo(controlX1, p0.y, controlX1, p1.y, p1.x, p1.y)
            }
        }

        val fillPath = Path().apply {
            addPath(strokePath)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    effectiveLineColor.copy(alpha = 0.36f),
                    effectiveLineColor.copy(alpha = 0.08f),
                    Color.Transparent
                )
            )
        )

        drawPath(
            path = strokePath,
            color = effectiveLineColor.copy(alpha = 0.38f),
            style = Stroke(width = strokeWidth.toPx() * 2.4f, cap = StrokeCap.Round)
        )

        drawPath(
            path = strokePath,
            color = effectiveLineColor,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * 15. JarvisMultiWavePerformanceChart
 * Harmonized multi-wave telemetry chart adapting to the active palette.
 */
@Composable
fun JarvisMultiWavePerformanceChart(
    modifier: Modifier = Modifier
) {
    val themeColors = JarvisTheme.colors
    val wavePrimary = listOf(0.18f, 0.42f, 0.36f, 0.68f, 0.58f, 0.84f, 0.76f)
    val waveSecondary = listOf(0.14f, 0.28f, 0.52f, 0.34f, 0.88f, 0.62f, 0.72f)
    val waveTertiary = listOf(0.12f, 0.34f, 0.24f, 0.54f, 0.48f, 0.78f, 0.88f)
    val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                for (i in 0..3) {
                    val y = h * (i / 3f)
                    drawLine(
                        color = themeColors.divider,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                }

                fun drawSmoothWave(data: List<Float>, color: Color) {
                    val stepX = w / (data.size - 1)
                    val pts = data.mapIndexed { idx, v ->
                        Offset(idx * stepX, h - (v * h * 0.88f) - h * 0.06f)
                    }
                    val path = Path().apply {
                        moveTo(pts.first().x, pts.first().y)
                        for (i in 0 until pts.size - 1) {
                            val p0 = pts[i]
                            val p1 = pts[i + 1]
                            val cx = (p0.x + p1.x) / 2f
                            cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                        }
                    }
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(
                        path = fill,
                        brush = Brush.verticalGradient(
                            listOf(color.copy(alpha = 0.18f), Color.Transparent)
                        )
                    )
                    drawPath(
                        path = path,
                        color = color.copy(alpha = 0.32f),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                drawSmoothWave(waveSecondary, themeColors.secondary)
                drawSmoothWave(waveTertiary, themeColors.accent)
                drawSmoothWave(wavePrimary, themeColors.primary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            days.forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall,
                    color = themeColors.textSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 16. JarvisSquircleQuickAction (Matte Action Tile)
 */
@Composable
fun JarvisSquircleQuickAction(
    label: String,
    icon: ImageVector,
    accentColor: Color = Color.Unspecified,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val themeColors = JarvisTheme.colors
    val effectiveAccent = if (accentColor == Color.Unspecified || accentColor == RoyalPurplePrimary) {
        themeColors.accent
    } else {
        accentColor
    }

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(themeColors.surfaceElevated)
                .border(
                    width = 1.dp,
                    color = themeColors.border,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = effectiveAccent,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = themeColors.textPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 17. JarvisGradientTitleWithLaserFlare (Clean Emphasized Section Title)
 */
@Composable
fun JarvisGradientTitleWithLaserFlare(
    leadingWord: String,
    gradientWord: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 26.sp
) {
    val themeColors = JarvisTheme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = themeColors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = fontSize
                    )
                ) {
                    append("$leadingWord ")
                }
                withStyle(
                    SpanStyle(
                        color = themeColors.accent,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = fontSize
                    )
                ) {
                    append(gradientWord)
                }
            }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = themeColors.textSecondary,
            fontWeight = FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(0.45f),
            thickness = 1.dp,
            color = themeColors.divider
        )
    }
}

/**
 * 18. JarvisAudioWaveform (Adaptive Theme Liquid Waveform)
 */
@Composable
fun JarvisAudioWaveform(
    audioLevel: Float,
    isActive: Boolean,
    status: AssistantStatus,
    modifier: Modifier = Modifier
) {
    val themeColors = JarvisTheme.colors
    val primaryColor = when (status) {
        AssistantStatus.LISTENING -> themeColors.statusEmerald
        AssistantStatus.THINKING -> themeColors.statusAmber
        AssistantStatus.SPEAKING -> themeColors.accent
        AssistantStatus.IDLE -> themeColors.primary
    }
    val secondaryColor = when (status) {
        AssistantStatus.LISTENING -> themeColors.accent
        AssistantStatus.THINKING -> themeColors.primary
        AssistantStatus.SPEAKING -> themeColors.primary
        AssistantStatus.IDLE -> themeColors.secondary
    }

    val multipliers = listOf(
        0.25f, 0.42f, 0.64f, 0.85f, 0.58f, 0.95f, 1.0f, 0.78f,
        0.92f, 0.66f, 0.88f, 0.52f, 0.74f, 0.40f, 0.24f
    )

    Row(
        modifier = modifier
            .height(38.dp)
            .fillMaxWidth(0.84f),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        multipliers.forEachIndexed { index, factor ->
            val targetFraction = if (isActive) {
                (0.18f + (audioLevel * factor * 0.82f)).coerceIn(0.16f, 1f)
            } else {
                0.16f + (factor * 0.08f)
            }
            val animatedFraction by animateFloatAsState(
                targetValue = targetFraction,
                animationSpec = tween(durationMillis = 90),
                label = "waveBar_$index"
            )
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight(animatedFraction)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = if (isActive) 0.98f else 0.45f),
                                secondaryColor.copy(alpha = if (isActive) 0.88f else 0.30f)
                            )
                        )
                    )
            )
        }
    }
}

/**
 * 19. JarvisFeatureTile (Bento Liquid Glass Tile)
 */
@Composable
fun JarvisFeatureTile(
    title: String,
    subtitle: String,
    badge: String,
    icon: ImageVector,
    accentColor: Color = Color.Unspecified,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isHero: Boolean = false
) {
    val themeColors = JarvisTheme.colors
    val effectiveAccent = if (accentColor == Color.Unspecified || accentColor == RoyalPurplePrimary) {
        themeColors.accent
    } else {
        accentColor
    }
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (isHero) themeColors.surfaceElevated else themeColors.cardSurface)
            .border(
                width = 1.dp,
                color = themeColors.border,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(themeColors.surfaceElevated)
                        .border(
                            1.dp,
                            themeColors.border,
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = effectiveAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = themeColors.textSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(themeColors.surfacePill)
                        .border(1.dp, themeColors.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = themeColors.textPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = themeColors.textSecondary,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = themeColors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 20. JarvisSectionHeader
 */
@Composable
fun JarvisSectionHeader(
    title: String,
    codeBadge: String,
    icon: ImageVector? = null,
    accentColor: Color = Color.Unspecified
) {
    val themeColors = JarvisTheme.colors
    val effectiveAccent = if (accentColor == Color.Unspecified || accentColor == SoftLavenderGlow) {
        themeColors.accent
    } else {
        accentColor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = effectiveAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = themeColors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (codeBadge.isNotBlank()) {
            Text(
                text = codeBadge,
                style = MaterialTheme.typography.labelSmall,
                color = themeColors.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(themeColors.surfacePill)
                    .border(1.dp, themeColors.border, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * 21. JarvisSearchBar (iOS Frosted Liquid Glass Search Pill)
 */
@Composable
fun JarvisSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val themeColors = JarvisTheme.colors

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = themeColors.textMuted,
                fontWeight = FontWeight.Normal
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = themeColors.textSecondary
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear Search",
                        tint = themeColors.textSecondary
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = themeColors.inputBackground,
            unfocusedContainerColor = themeColors.surface,
            focusedBorderColor = themeColors.primary,
            unfocusedBorderColor = themeColors.border,
            focusedTextColor = themeColors.textPrimary,
            unfocusedTextColor = themeColors.textPrimary
        ),
        modifier = modifier.let {
            if (testTag != null) it.testTag(testTag) else it
        }
    )
}

/**
 * 22. JarvisSettingsTile (iOS Liquid Glass Toggle Row)
 */
@Composable
fun JarvisSettingsTile(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    icon: ImageVector? = null,
    accentColor: Color = Color.Unspecified
) {
    val themeColors = JarvisTheme.colors
    val effectiveAccent = if (accentColor == Color.Unspecified || accentColor == RoyalPurplePrimary) {
        themeColors.accent
    } else {
        accentColor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(themeColors.cardSurface)
            .border(
                1.dp,
                if (checked) effectiveAccent.copy(alpha = 0.55f) else themeColors.border,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(effectiveAccent.copy(alpha = 0.12f))
                        .border(1.dp, effectiveAccent.copy(alpha = 0.30f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = effectiveAccent,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = themeColors.textPrimary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = themeColors.textSecondary,
                    fontWeight = FontWeight.Normal
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = themeColors.onPrimary,
                checkedTrackColor = effectiveAccent,
                uncheckedThumbColor = themeColors.textSecondary,
                uncheckedTrackColor = themeColors.surfacePill,
                uncheckedBorderColor = themeColors.border
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

/**
 * 23. JarvisAnimatedIndicator
 */
@Composable
fun JarvisAnimatedIndicator(
    label: String,
    accentColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    val themeColors = JarvisTheme.colors
    val effectiveAccent = if (accentColor == Color.Unspecified || accentColor == SoftLavenderGlow) {
        themeColors.accent
    } else {
        accentColor
    }

    val infiniteTransition = rememberInfiniteTransition(label = "indicatorPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(themeColors.surfaceElevated)
            .border(1.dp, themeColors.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(effectiveAccent.copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = themeColors.textSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 24. JarvisModernDialog (Apple iOS 26 Frosted Center Alert Dialog)
 */
@Composable
fun JarvisModernDialog(
    title: String,
    message: String = "",
    description: String = "",
    confirmLabel: String = "",
    confirmText: String = "",
    dismissText: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false
) {
    val themeColors = JarvisTheme.colors
    val resolvedMessage = message.ifBlank { description }
    val resolvedConfirm = confirmLabel.ifBlank { confirmText.ifBlank { "Confirm" } }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = if (themeColors.isDark) themeColors.surfaceElevated else themeColors.cardSurface,
        titleContentColor = themeColors.textPrimary,
        textContentColor = themeColors.textSecondary,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = themeColors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = resolvedMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = themeColors.textSecondary,
                fontWeight = FontWeight.Normal
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) themeColors.statusRoseRed else themeColors.accent,
                    contentColor = themeColors.onPrimary
                ),
                modifier = Modifier.testTag("confirm_dialog_accept")
            ) {
                Text(
                    text = resolvedConfirm,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, themeColors.border),
                modifier = Modifier.testTag("confirm_dialog_cancel")
            ) {
                Text(
                    text = dismissText,
                    style = MaterialTheme.typography.labelLarge,
                    color = themeColors.textSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}

// ============================================================================
// PRESERVED COMPONENT ALIASES
// ============================================================================

@Composable
fun HudBackgroundBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    JarvisPremiumBackground(modifier = modifier, content = content)
}

@Composable
fun HudCard(
    modifier: Modifier = Modifier,
    glowColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    JarvisGlassPanel(
        modifier = modifier,
        accentColor = glowColor,
        onClick = onClick,
        content = content
    )
}

@Composable
fun HudTopBar(
    title: String,
    subtitle: String,
    status: AssistantStatus,
    onBackClick: (() -> Unit)? = null
) {
    JarvisPremiumTopBar(
        title = title,
        subtitle = subtitle,
        status = status,
        onBackClick = onBackClick
    )
}

@Composable
fun AssistantStatusPill(
    status: AssistantStatus,
    modifier: Modifier = Modifier
) {
    JarvisStatusBadge(status = status, modifier = modifier)
}

@Composable
fun HudSectionHeader(
    title: String,
    codeBadge: String,
    icon: ImageVector? = null
) {
    JarvisSectionHeader(title = title, codeBadge = codeBadge, icon = icon)
}

@Composable
fun HudConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false
) {
    JarvisModernDialog(
        title = title,
        message = message,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        isDestructive = isDestructive
    )
}

@Composable
fun SafetyConfirmationModal(
    confirmation: com.jarvisai.assistant.model.PendingSafetyConfirmation,
    requireBiometric: Boolean,
    authErrorMessage: String? = null,
    onApprove: () -> Unit,
    onCancel: () -> Unit
) {
    val themeColors = JarvisTheme.colors

    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(16.dp),
        containerColor = if (themeColors.isDark) themeColors.surfaceElevated else themeColors.cardSurface,
        titleContentColor = themeColors.textPrimary,
        textContentColor = themeColors.textSecondary,
        modifier = Modifier.testTag("safety_confirmation_modal"),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(themeColors.statusRoseRed.copy(alpha = 0.14f))
                        .border(1.dp, themeColors.statusRoseRed.copy(alpha = 0.50f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${confirmation.riskLevel} Risk",
                        style = MaterialTheme.typography.labelSmall,
                        color = themeColors.statusRoseRed,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = confirmation.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = themeColors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = confirmation.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = themeColors.textPrimary,
                    fontWeight = FontWeight.Normal
                )
                if (confirmation.argumentsSummary.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(themeColors.codeBackground)
                            .border(1.dp, themeColors.border, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = confirmation.argumentsSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = themeColors.codeText,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
                Text(
                    text = if (requireBiometric) {
                        "Biometric or Device Lock authentication is required to approve this action."
                    } else {
                        "You can also say 'Yes / Haan' to approve or 'No / Cancel' to abort."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = themeColors.textSecondary,
                    fontWeight = FontWeight.Normal
                )
                if (!authErrorMessage.isNullOrBlank()) {
                    Text(
                        text = authErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = themeColors.statusRoseRed,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApprove,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeColors.accent,
                    contentColor = themeColors.onPrimary
                ),
                modifier = Modifier.testTag("btn_safety_confirm_approve")
            ) {
                Text(
                    text = if (requireBiometric) "Authenticate & Approve" else "Approve Action",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, themeColors.statusRoseRed.copy(alpha = 0.50f)),
                modifier = Modifier.testTag("btn_safety_confirm_cancel")
            ) {
                Text(
                    text = "Cancel Action",
                    style = MaterialTheme.typography.labelLarge,
                    color = themeColors.statusRoseRed,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}

@Composable
fun JarvisEmergencyStopBanner(
    onEmergencyStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = JarvisTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (themeColors.isDark) {
                    themeColors.statusRoseRed.copy(alpha = 0.14f)
                } else {
                    themeColors.statusRoseRed.copy(alpha = 0.08f)
                }
            )
            .border(1.dp, themeColors.statusRoseRed.copy(alpha = 0.50f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Voice / agent engine active",
                style = MaterialTheme.typography.labelSmall,
                color = themeColors.statusRoseRed,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Tap Stop to halt all running voice and agent tasks",
                style = MaterialTheme.typography.bodySmall,
                color = themeColors.textPrimary,
                fontWeight = FontWeight.Normal
            )
        }
        Button(
            onClick = onEmergencyStop,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = themeColors.statusRoseRed,
                contentColor = Color.White
            ),
            modifier = Modifier.testTag("btn_emergency_stop")
        ) {
            Text(
                text = "Stop",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
