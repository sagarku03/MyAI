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
 * Dynamic multi-palette canvas with ambient backlight fields that adapt across
 * Royal Purple, Ocean Blue, and Aqua Mint in both Dark and Light display modes.
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
            .background(
                Brush.verticalGradient(
                    colors = if (colors.isDark) {
                        listOf(
                            colors.background,
                            colors.surface.copy(alpha = 0.82f),
                            colors.background
                        )
                    } else {
                        listOf(
                            colors.background,
                            colors.surfaceElevated.copy(alpha = 0.45f),
                            colors.background
                        )
                    }
                )
            )
            .drawBehind {
                // Top-Left Primary Theme Accent Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.accent.copy(alpha = if (colors.isDark) 0.28f else 0.12f),
                            colors.primary.copy(alpha = if (colors.isDark) 0.14f else 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.15f, size.height * 0.04f),
                        radius = size.width * 0.90f
                    ),
                    radius = size.width * 0.90f,
                    center = Offset(size.width * 0.15f, size.height * 0.04f)
                )

                // Top-Right Harmonious Secondary Wave
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.secondary.copy(alpha = if (colors.isDark) 0.22f else 0.10f),
                            colors.accent.copy(alpha = if (colors.isDark) 0.10f else 0.04f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.92f, size.height * 0.12f),
                        radius = size.width * 0.78f
                    ),
                    radius = size.width * 0.78f,
                    center = Offset(size.width * 0.92f, size.height * 0.12f)
                )

                // Bottom-Right Subtle Ambient Core
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.primary.copy(alpha = if (colors.isDark) 0.16f else 0.08f),
                            colors.accent.copy(alpha = if (colors.isDark) 0.10f else 0.04f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.80f, size.height * 0.90f),
                        radius = size.width * 0.75f
                    ),
                    radius = size.width * 0.75f,
                    center = Offset(size.width * 0.80f, size.height * 0.90f)
                )
            }
    ) {
        content()
    }
}

/**
 * 2. JarvisGlassPanel (Apple iOS 26 Frosted Translucent Bento Card)
 */
@Composable
fun JarvisGlassPanel(
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    cornerRadius: Dp = 28.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val themeColors = JarvisTheme.colors
    val effectiveAccent = if (accentColor == Color.Unspecified || accentColor == RoyalPurplePrimary) {
        themeColors.accent
    } else {
        accentColor
    }
    val shape = RoundedCornerShape(cornerRadius)

    val bgColors = if (themeColors.isDark) {
        listOf(
            themeColors.surface.copy(alpha = 0.88f),
            themeColors.surfaceElevated.copy(alpha = 0.94f)
        )
    } else {
        listOf(
            themeColors.cardSurface.copy(alpha = 0.96f),
            themeColors.surfaceElevated.copy(alpha = 0.65f)
        )
    }

    val panelModifier = modifier
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = bgColors,
                start = Offset(0f, 0f),
                end = Offset(900f, 900f)
            )
        )
        .drawBehind {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        themeColors.glassRimTop,
                        effectiveAccent.copy(alpha = if (themeColors.isDark) 0.08f else 0.04f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = size.height * 0.42f
                )
            )
        }
        .border(
            BorderStroke(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        themeColors.glassRimTop,
                        effectiveAccent.copy(alpha = 0.55f),
                        themeColors.glassRimBottom
                    )
                )
            ),
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
    cornerRadius: Dp = 28.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
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
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = 14.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(effectiveAccent)
                    )
                    Text(
                        text = resolvedHeader.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = themeColors.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.9.sp
                    )
                }
                if (!sectionBadge.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(effectiveAccent.copy(alpha = 0.16f))
                            .border(1.dp, effectiveAccent.copy(alpha = 0.50f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = sectionBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = effectiveAccent,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        val shape = RoundedCornerShape(26.dp)
        val bgColors = if (themeColors.isDark) {
            listOf(
                themeColors.surface.copy(alpha = 0.88f),
                themeColors.surfaceElevated.copy(alpha = 0.94f)
            )
        } else {
            listOf(
                themeColors.cardSurface.copy(alpha = 0.96f),
                themeColors.surfaceElevated.copy(alpha = 0.65f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.linearGradient(colors = bgColors))
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            themeColors.glassRimTop,
                            effectiveAccent.copy(alpha = 0.55f),
                            themeColors.glassRimBottom
                        )
                    ),
                    shape = shape
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            content = content
        )
    }
}

/**
 * 5. LiquidGlassSettingsRow (Authentic Apple iOS Row with SOLID Vibrant Gradient Squircle Icon Badge)
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
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .let { if (testTag != null) it.testTag(testTag) else it },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Solid Vibrant Gradient iOS Squircle Badge with Crisp White Icon
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    effectiveIconTint,
                                    effectiveIconTint.copy(alpha = 0.80f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = themeColors.onPrimary.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(13.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = themeColors.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = themeColors.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = themeColors.textSecondary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!valueText.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(effectiveValueColor.copy(alpha = 0.16f))
                            .border(1.dp, effectiveValueColor.copy(alpha = 0.50f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = valueText,
                            style = MaterialTheme.typography.labelSmall,
                            color = themeColors.textPrimary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(themeColors.surfacePill.copy(alpha = 0.70f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = themeColors.textMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 68.dp, end = 12.dp),
                thickness = 0.6.dp,
                color = themeColors.divider
            )
        }
    }
}

/**
 * 6. LiquidGlassSegmentedBar (Apple iOS 26 Segmented Pill Bar)
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
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (themeColors.isDark) {
                    themeColors.surface.copy(alpha = 0.94f)
                } else {
                    themeColors.surfacePill.copy(alpha = 0.85f)
                }
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        themeColors.glassRimTop,
                        themeColors.borderHighlight,
                        themeColors.glassRimBottom
                    )
                ),
                shape = RoundedCornerShape(999.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val selected = item == selectedItem
            val label = itemLabel?.invoke(item) ?: labelProvider(item)
            val icon = iconProvider?.invoke(item)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (selected) {
                            Brush.horizontalGradient(
                                colors = listOf(
                                    themeColors.accent,
                                    themeColors.primary
                                )
                            )
                        } else {
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Color.Transparent)
                            )
                        }
                    )
                    .border(
                        width = if (selected) 1.dp else 0.dp,
                        color = if (selected) themeColors.onPrimary.copy(alpha = 0.45f) else Color.Transparent,
                        shape = RoundedCornerShape(999.dp)
                    )
                    .clickable {
                        onItemSelected?.invoke(item) ?: onSelect(item)
                    }
                    .padding(vertical = 10.dp, horizontal = 6.dp)
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
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 7. LiquidGlassBottomSheet (Apple iOS 26 Frosted Smoked-Crystal Modal Sheet)
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
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = if (themeColors.isDark) themeColors.surfaceElevated else themeColors.cardSurface,
        contentColor = themeColors.textPrimary,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 42.dp, height = 5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(themeColors.textMuted.copy(alpha = 0.45f))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = themeColors.textPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = themeColors.textSecondary,
                        fontWeight = FontWeight.SemiBold
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
 * 8. JarvisGradientButton (Primary Multi-Palette Liquid Glass Pill CTA)
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
    val resolvedGradient = if (gradientColors.isEmpty()) {
        listOf(themeColors.accent, themeColors.primary)
    } else {
        gradientColors
    }
    val shape = RoundedCornerShape(999.dp)
    val alpha = if (enabled) 1f else 0.42f

    Box(
        modifier = modifier
            .height(54.dp)
            .clip(shape)
            .background(
                brush = Brush.horizontalGradient(
                    colors = resolvedGradient.map { it.copy(alpha = alpha) }
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        themeColors.onPrimary.copy(alpha = if (enabled) 0.50f else 0.15f),
                        themeColors.secondary.copy(alpha = if (enabled) 0.40f else 0.12f)
                    )
                ),
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
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
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = themeColors.onPrimary.copy(alpha = if (enabled) 1f else 0.7f),
                fontWeight = FontWeight.ExtraBold
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
    val shape = RoundedCornerShape(999.dp)

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
        border = BorderStroke(
            width = 1.dp,
            color = themeColors.onPrimary.copy(alpha = if (enabled) 0.36f else 0.10f)
        ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 13.dp),
        modifier = modifier
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = themeColors.onPrimary,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = themeColors.onPrimary,
            fontWeight = FontWeight.ExtraBold
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
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                listOf(
                    themeColors.borderHighlight,
                    effectiveAccent.copy(alpha = if (enabled) 0.65f else 0.20f)
                )
            )
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = themeColors.surfacePill.copy(alpha = 0.65f),
            contentColor = themeColors.textPrimary
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp),
        modifier = modifier
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = effectiveAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (effectiveAccent == themeColors.statusRoseRed) themeColors.statusRoseRed else themeColors.textPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 10. JarvisIconControl (Circular iOS Liquid Glass Orb Button)
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
            .background(
                if (isActive) {
                    Brush.linearGradient(
                        listOf(
                            effectiveActiveColor,
                            themeColors.primary
                        )
                    )
                } else {
                    Brush.linearGradient(
                        listOf(
                            themeColors.surfaceElevated.copy(alpha = 0.88f),
                            themeColors.surface.copy(alpha = 0.92f)
                        )
                    )
                }
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = if (isActive) {
                        listOf(themeColors.onPrimary.copy(alpha = 0.55f), effectiveActiveColor)
                    } else {
                        listOf(themeColors.glassRimTop, themeColors.borderHighlight)
                    }
                ),
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
 * 11. JarvisPremiumTopBar (Apple iOS 26 Bold Glass Header)
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    colors = if (themeColors.isDark) {
                        listOf(
                            themeColors.surfaceElevated.copy(alpha = 0.90f),
                            themeColors.surface.copy(alpha = 0.94f)
                        )
                    } else {
                        listOf(
                            themeColors.cardSurface.copy(alpha = 0.96f),
                            themeColors.surfaceElevated.copy(alpha = 0.80f)
                        )
                    }
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        themeColors.glassRimTop,
                        themeColors.borderHighlight,
                        themeColors.glassRimBottom
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        themeColors.accent,
                                        themeColors.primary
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = themeColors.onPrimary.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(13.dp)
                            )
                            .clickable(onClick = onBackClick)
                            .testTag("hud_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate Back",
                            tint = themeColors.onPrimary,
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
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = themeColors.accent,
                        fontWeight = FontWeight.Bold,
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
    }
}

/**
 * 12. JarvisStatusBadge (Apple iOS Dynamic Island Status Pill)
 */
@Composable
fun JarvisStatusBadge(
    status: AssistantStatus,
    modifier: Modifier = Modifier
) {
    val themeColors = JarvisTheme.colors
    val statusColor by animateColorAsState(
        targetValue = when (status) {
            AssistantStatus.IDLE -> themeColors.accent
            AssistantStatus.LISTENING -> themeColors.statusEmerald
            AssistantStatus.THINKING -> themeColors.statusAmber
            AssistantStatus.SPEAKING -> themeColors.primary
        },
        label = "statusBadgeColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "statusDotPulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(statusColor.copy(alpha = 0.16f))
            .border(
                width = 1.dp,
                color = statusColor.copy(alpha = 0.65f),
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 11.dp, vertical = 6.dp)
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
            color = themeColors.textPrimary,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

/**
 * 13. JarvisModernBottomNavigation
 * Apple VisionOS / iOS 26 Expandable Multi-Color Capsule Dock
 */
@Composable
fun JarvisModernBottomNavigation(
    destinations: List<JarvisDestination>,
    currentDestination: JarvisDestination,
    onNavigate: (JarvisDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = JarvisTheme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(
                    if (themeColors.isDark) {
                        themeColors.surface.copy(alpha = 0.94f)
                    } else {
                        themeColors.surface.copy(alpha = 0.96f)
                    }
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            themeColors.glassRimTop,
                            themeColors.borderHighlight,
                            themeColors.glassRimBottom
                        )
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
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
                        .weight(if (selected) 1.45f else 0.92f)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (selected) {
                                Brush.horizontalGradient(
                                    listOf(
                                        themeColors.accent,
                                        themeColors.primary
                                    )
                                )
                            } else {
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Transparent
                                    )
                                )
                            }
                        )
                        .border(
                            width = if (selected) 1.2.dp else 0.5.dp,
                            color = if (selected) themeColors.onPrimary.copy(alpha = 0.60f) else Color.Transparent,
                            shape = RoundedCornerShape(22.dp)
                        )
                        .clickable { onNavigate(dest) }
                        .padding(vertical = 10.dp, horizontal = 6.dp)
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
                            tint = if (selected) themeColors.onPrimary else themeColors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        if (selected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = dest.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = themeColors.onPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp,
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
 * 16. JarvisSquircleQuickAction (Apple iOS 26 Liquid Glass Squircle Action Tile)
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
                .size(62.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            themeColors.surfaceElevated.copy(alpha = 0.90f),
                            effectiveAccent.copy(alpha = 0.22f)
                        )
                    )
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            themeColors.glassRimTop,
                            effectiveAccent.copy(alpha = 0.75f)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = effectiveAccent,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = themeColors.textPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 17. JarvisGradientTitleWithLaserFlare
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
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = fontSize
                    )
                ) {
                    append("$leadingWord ")
                }
                withStyle(
                    SpanStyle(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                themeColors.accent,
                                themeColors.primary,
                                themeColors.secondary
                            )
                        ),
                        fontWeight = FontWeight.ExtraBold,
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
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .height(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                themeColors.primary,
                                themeColors.accent,
                                themeColors.secondary,
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                themeColors.accent,
                                themeColors.primary.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
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
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = if (isHero) {
                        listOf(
                            effectiveAccent.copy(alpha = if (themeColors.isDark) 0.28f else 0.14f),
                            themeColors.surfaceElevated.copy(alpha = 0.92f),
                            themeColors.cardSurface.copy(alpha = 0.96f)
                        )
                    } else {
                        listOf(
                            themeColors.cardSurface.copy(alpha = 0.94f),
                            themeColors.surfaceElevated.copy(alpha = 0.88f)
                        )
                    }
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        themeColors.glassRimTop,
                        effectiveAccent.copy(alpha = 0.55f),
                        themeColors.border
                    )
                ),
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
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    effectiveAccent.copy(alpha = 0.24f),
                                    effectiveAccent.copy(alpha = 0.10f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            effectiveAccent.copy(alpha = 0.55f),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = effectiveAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = effectiveAccent,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(effectiveAccent.copy(alpha = 0.16f))
                        .border(1.dp, effectiveAccent.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

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
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = themeColors.textSecondary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = effectiveAccent,
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
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = themeColors.textPrimary,
                fontWeight = FontWeight.ExtraBold
            )
        }

        if (codeBadge.isNotBlank()) {
            Text(
                text = codeBadge,
                style = MaterialTheme.typography.labelSmall,
                color = themeColors.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(themeColors.surfacePill.copy(alpha = 0.80f))
                    .border(1.dp, effectiveAccent.copy(alpha = 0.40f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
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
                fontWeight = FontWeight.SemiBold
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = themeColors.accent
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
        shape = RoundedCornerShape(999.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = themeColors.inputBackground,
            unfocusedContainerColor = themeColors.surface.copy(alpha = 0.90f),
            focusedBorderColor = themeColors.accent,
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
            .clip(RoundedCornerShape(20.dp))
            .background(themeColors.cardSurface.copy(alpha = 0.92f))
            .border(
                1.dp,
                if (checked) effectiveAccent.copy(alpha = 0.65f) else themeColors.border,
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
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
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(effectiveAccent.copy(alpha = 0.18f))
                        .border(1.dp, effectiveAccent.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = effectiveAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = themeColors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = themeColors.textSecondary,
                    fontWeight = FontWeight.SemiBold
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
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(effectiveAccent.copy(alpha = 0.16f))
            .border(1.dp, effectiveAccent.copy(alpha = 0.48f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(effectiveAccent.copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = themeColors.textPrimary,
            fontWeight = FontWeight.ExtraBold
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
    val borderAccent = if (isDestructive) themeColors.statusRoseRed else themeColors.accent

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(30.dp),
        containerColor = if (themeColors.isDark) themeColors.surfaceElevated else themeColors.cardSurface,
        titleContentColor = themeColors.textPrimary,
        textContentColor = themeColors.textSecondary,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "JARVIS SYSTEM PROTOCOL",
                    style = MaterialTheme.typography.labelSmall,
                    color = borderAccent,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = themeColors.textPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = resolvedMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = themeColors.textSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) themeColors.statusRoseRed else themeColors.accent,
                    contentColor = themeColors.onPrimary
                ),
                modifier = Modifier.testTag("confirm_dialog_accept")
            ) {
                Text(
                    text = resolvedConfirm,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, themeColors.border),
                modifier = Modifier.testTag("confirm_dialog_cancel")
            ) {
                Text(
                    text = dismissText,
                    style = MaterialTheme.typography.labelLarge,
                    color = themeColors.textSecondary,
                    fontWeight = FontWeight.Bold
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
        shape = RoundedCornerShape(30.dp),
        containerColor = if (themeColors.isDark) themeColors.surfaceElevated else themeColors.cardSurface,
        titleContentColor = themeColors.textPrimary,
        textContentColor = themeColors.textSecondary,
        modifier = Modifier.testTag("safety_confirmation_modal"),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(themeColors.statusRoseRed.copy(alpha = 0.18f))
                        .border(1.dp, themeColors.statusRoseRed, RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${confirmation.riskLevel} RISK • SAFETY GATE",
                        style = MaterialTheme.typography.labelSmall,
                        color = themeColors.statusRoseRed,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = confirmation.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = themeColors.textPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = confirmation.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = themeColors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                if (confirmation.argumentsSummary.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(themeColors.codeBackground)
                            .border(1.dp, themeColors.border, RoundedCornerShape(14.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = confirmation.argumentsSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = themeColors.codeText,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
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
                    fontWeight = FontWeight.SemiBold
                )
                if (!authErrorMessage.isNullOrBlank()) {
                    Text(
                        text = authErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = themeColors.statusRoseRed,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApprove,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeColors.accent,
                    contentColor = themeColors.onPrimary
                ),
                modifier = Modifier.testTag("btn_safety_confirm_approve")
            ) {
                Text(
                    text = if (requireBiometric) "Authenticate & Approve" else "Approve Action",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, themeColors.statusRoseRed),
                modifier = Modifier.testTag("btn_safety_confirm_cancel")
            ) {
                Text(
                    text = "Cancel Action",
                    style = MaterialTheme.typography.labelLarge,
                    color = themeColors.statusRoseRed,
                    fontWeight = FontWeight.Bold
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
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (themeColors.isDark) {
                    themeColors.statusRoseRed.copy(alpha = 0.22f)
                } else {
                    themeColors.statusRoseRed.copy(alpha = 0.14f)
                }
            )
            .border(1.2.dp, themeColors.statusRoseRed, RoundedCornerShape(999.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "AUTONOMOUS / VOICE ENGINE ACTIVE",
                style = MaterialTheme.typography.labelSmall,
                color = themeColors.statusRoseRed,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Tap STOP to halt all voice & autonomous tasks",
                style = MaterialTheme.typography.bodySmall,
                color = themeColors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Button(
            onClick = onEmergencyStop,
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = themeColors.statusRoseRed,
                contentColor = themeColors.onPrimary
            ),
            modifier = Modifier.testTag("btn_emergency_stop")
        ) {
            Text(
                text = "STOP",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
