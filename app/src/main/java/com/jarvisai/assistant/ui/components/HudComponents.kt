package com.jarvisai.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisElevatedSurface
import com.example.ui.theme.JarvisGlassBorder
import com.example.ui.theme.JarvisGlassSurface
import com.example.ui.theme.JarvisMidnightBg
import com.example.ui.theme.JarvisNavyBgSecondary
import com.example.ui.theme.JarvisNeonCyan
import com.example.ui.theme.JarvisNeonGreen
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet
import com.example.ui.theme.JarvisVioletBright
import com.example.ui.theme.StatusListeningEmerald
import com.example.ui.theme.StatusThinkingAmber
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.ui.JarvisDestination

// ============================================================================
// JARVIS PREMIUM FUTURISTIC DARK GLASSMORPHISM DESIGN SYSTEM
// Inspired directly by the 9 Reference UI Designs:
// - WealthWave (Frosted glass hero card with bezier sparklines, 4 glowing squircle buttons,
//   multi-wave Performance Trends chart, under-lit floating glass bottom dock)
// - Monex (Neon lime #A3FF12 CTA pills, peek-a-boo gradient card, centered circular dock action)
// - System Update (Royal sapphire glass card with thick cyan-indigo progress bar & white pill CTA)
// - Full Control (Purple-to-cyan gradient headline + glowing violet laser flare line)
// - Apple Arcade Library (Detached circular nav button, stacked glass category rows, pill Open buttons)
// - Poly Ultimate (Purple-pink & gold gradient cards, glowing underline tab bar)
// - SHRUHH (Frosted violet glass card with bright specular rim border)
// ============================================================================

/**
 * 1. JarvisPremiumBackground
 * Deep obsidian/midnight canvas (#06080F) with ambient radial cyan, royal blue, and violet light fields.
 */
@Composable
fun JarvisPremiumBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF05070E),
                        Color(0xFF090D1A),
                        Color(0xFF05070E)
                    )
                )
            )
            .drawBehind {
                // Top-left violet-magenta nebula aura (Poly / SHRUHH reference)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            JarvisViolet.copy(alpha = 0.16f),
                            JarvisElectricBlue.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.12f, size.height * 0.10f),
                        radius = size.width * 0.85f
                    ),
                    radius = size.width * 0.85f,
                    center = Offset(size.width * 0.12f, size.height * 0.10f)
                )

                // Center-right cyan/sapphire halo (WealthWave / System Update reference)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            JarvisNeonCyan.copy(alpha = 0.13f),
                            JarvisElectricBlue.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.34f),
                        radius = size.width * 0.80f
                    ),
                    radius = size.width * 0.80f,
                    center = Offset(size.width * 0.88f, size.height * 0.34f)
                )

                // Bottom ambient glow behind floating dock
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            JarvisNeonCyan.copy(alpha = 0.10f),
                            JarvisViolet.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.42f, size.height * 0.94f),
                        radius = size.width * 0.70f
                    ),
                    radius = size.width * 0.70f,
                    center = Offset(size.width * 0.42f, size.height * 0.94f)
                )
            }
    ) {
        content()
    }
}

/**
 * 2. JarvisGlassPanel
 * Frosted dark glass surface with specular top-left highlight border (WealthWave / SHRUHH / System Update).
 */
@Composable
fun JarvisGlassPanel(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    cornerRadius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    val panelModifier = modifier
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF1A2035).copy(alpha = 0.90f),
                    Color(0xFF101422).copy(alpha = 0.95f),
                    Color(0xFF0C101C).copy(alpha = 0.96f)
                ),
                start = Offset(0f, 0f),
                end = Offset(700f, 700f)
            )
        )
        .border(
            BorderStroke(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f),
                        accentColor.copy(alpha = 0.50f),
                        JarvisGlassBorder.copy(alpha = 0.65f),
                        accentColor.copy(alpha = 0.20f)
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
    glowColor: Color = JarvisViolet,
    cornerRadius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
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
 * 4. JarvisGradientButton
 * Pill/rounded button with luminous gradient fill and crisp specular border (SHRUHH / Poly Ultimate / Monex).
 */
@Composable
fun JarvisGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    gradientColors: List<Color> = listOf(JarvisViolet, JarvisElectricBlue, JarvisNeonCyan)
) {
    val shape = RoundedCornerShape(999.dp)
    val alpha = if (enabled) 1f else 0.42f

    Box(
        modifier = modifier
            .height(50.dp)
            .clip(shape)
            .background(
                brush = Brush.horizontalGradient(
                    colors = gradientColors.map { it.copy(alpha = alpha) }
                )
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (enabled) 0.38f else 0.12f),
                shape = shape
            )
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
                    tint = Color.White.copy(alpha = if (enabled) 1f else 0.7f),
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = if (enabled) 1f else 0.7f),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 5. JarvisPrimaryActionButton & JarvisSecondaryActionButton
 */
@Composable
fun JarvisPrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accentColor: Color = JarvisNeonGreen
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor,
            contentColor = Color(0xFF060911),
            disabledContainerColor = accentColor.copy(alpha = 0.25f),
            disabledContentColor = JarvisTextSecondary
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
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
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                listOf(
                    accentColor.copy(alpha = if (enabled) 0.75f else 0.25f),
                    accentColor.copy(alpha = if (enabled) 0.35f else 0.15f)
                )
            )
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = accentColor.copy(alpha = 0.12f),
            contentColor = accentColor
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = modifier
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = accentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 6. JarvisIconControl
 */
@Composable
fun JarvisIconControl(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = JarvisTextPrimary,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    isActive: Boolean = false,
    size: Dp = 42.dp
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                if (isActive) activeColor.copy(alpha = 0.22f)
                else Color(0xFF171C2E).copy(alpha = 0.92f)
            )
            .border(
                width = 1.dp,
                color = if (isActive) activeColor.copy(alpha = 0.80f)
                else Color.White.copy(alpha = 0.14f),
                shape = shape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) activeColor else tint,
            modifier = Modifier.size(19.dp)
        )
    }
}

/**
 * 7. JarvisPremiumTopBar
 * Clean modern header inspired by WealthWave, Monex, and Apple Arcade Library.
 */
@Composable
fun JarvisPremiumTopBar(
    title: String,
    subtitle: String,
    status: AssistantStatus,
    onBackClick: (() -> Unit)? = null,
    trailingActions: (@Composable RowScope.() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
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
                if (onBackClick != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF161B2C))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                            .clickable(onClick = onBackClick)
                            .testTag("hud_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate Back",
                            tint = JarvisTextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = JarvisTextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisTextSecondary,
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
 * 8. JarvisStatusBadge
 */
@Composable
fun JarvisStatusBadge(
    status: AssistantStatus,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = when (status) {
            AssistantStatus.IDLE -> JarvisNeonCyan
            AssistantStatus.LISTENING -> JarvisNeonGreen
            AssistantStatus.THINKING -> StatusThinkingAmber
            AssistantStatus.SPEAKING -> JarvisVioletBright
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
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF151A2C))
            .border(1.dp, statusColor.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 6.dp)
            .testTag("assistant_status_pill")
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = dotAlpha))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            color = statusColor,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 9. JarvisModernBottomNavigation
 * Combines:
 * - Image 2 (WealthWave): Translucent frosted glass pill dock with bright cyan bottom under-glow spotlight
 * - Image 5 (Monex): Prominent circular Neon Lime-Green / Cyan center action button for Live Voice
 * - Image 7 (Apple Arcade): Detached circular glass Settings orb on the right
 */
@Composable
fun JarvisModernBottomNavigation(
    destinations: List<JarvisDestination>,
    currentDestination: JarvisDestination,
    onNavigate: (JarvisDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val mainPillDestinations = destinations.filter { it != JarvisDestination.GENERAL_SETTINGS }
    val detachedDestination = destinations.find { it == JarvisDestination.GENERAL_SETTINGS }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Main Floating Frosted Glass Pill Dock (WealthWave + Monex style)
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF141B2D).copy(alpha = 0.95f),
                            Color(0xFF1B2338).copy(alpha = 0.95f),
                            Color(0xFF141B2D).copy(alpha = 0.95f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.25f),
                            JarvisNeonCyan.copy(alpha = 0.45f),
                            Color.White.copy(alpha = 0.18f)
                        )
                    ),
                    shape = RoundedCornerShape(999.dp)
                )
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            mainPillDestinations.forEach { dest ->
                val selected = currentDestination == dest
                val isCenterVoiceAction = dest == JarvisDestination.VOICE

                if (isCenterVoiceAction) {
                    // Monex-inspired prominent circular Neon Lime-Green center button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate(dest) }
                            .testTag("bottom_nav_${dest.route}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = if (selected) {
                                            listOf(JarvisNeonCyan, JarvisNeonGreen)
                                        } else {
                                            listOf(JarvisNeonGreen, Color(0xFF6EE7B7))
                                        }
                                    )
                                )
                                .border(
                                    width = 2.dp,
                                    color = Color.White.copy(alpha = 0.65f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = dest.label,
                                tint = Color(0xFF060911),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else {
                    // WealthWave-inspired tab item with glowing bottom cyan spotlight when selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .drawBehind {
                                if (selected) {
                                    // Bottom cyan spotlight glow (WealthWave reference)
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                JarvisNeonCyan.copy(alpha = 0.45f),
                                                JarvisElectricBlue.copy(alpha = 0.15f),
                                                Color.Transparent
                                            ),
                                            center = Offset(size.width * 0.5f, size.height * 0.95f),
                                            radius = size.width * 0.65f
                                        ),
                                        radius = size.width * 0.65f,
                                        center = Offset(size.width * 0.5f, size.height * 0.95f)
                                    )
                                }
                            }
                            .clickable { onNavigate(dest) }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                            .testTag("bottom_nav_${dest.route}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = dest.label,
                                tint = if (selected) JarvisNeonCyan else JarvisTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = dest.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color.White else JarvisTextMuted,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Apple Arcade Library-inspired Detached Circular Glass Button on Right
        if (detachedDestination != null) {
            val selected = currentDestination == detachedDestination
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = if (selected) {
                                listOf(JarvisViolet.copy(alpha = 0.45f), JarvisElectricBlue.copy(alpha = 0.35f))
                            } else {
                                listOf(Color(0xFF161D30).copy(alpha = 0.95f), Color(0xFF101524).copy(alpha = 0.95f))
                            }
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) JarvisNeonCyan else Color.White.copy(alpha = 0.22f),
                        shape = CircleShape
                    )
                    .clickable { onNavigate(detachedDestination) }
                    .testTag("bottom_nav_${detachedDestination.route}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = detachedDestination.icon,
                    contentDescription = detachedDestination.label,
                    tint = if (selected) JarvisNeonCyan else JarvisTextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * 10. JarvisSparklineWave (Directly from Image 2 WealthWave Hero & Breakdown Cards!)
 * Draws a smooth glowing Bezier curve with a soft vertical gradient fill underneath.
 */
@Composable
fun JarvisSparklineWave(
    points: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.5.dp
) {
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
                    lineColor.copy(alpha = 0.34f),
                    lineColor.copy(alpha = 0.06f),
                    Color.Transparent
                )
            )
        )

        // Outer soft neon glow stroke
        drawPath(
            path = strokePath,
            color = lineColor.copy(alpha = 0.35f),
            style = Stroke(width = strokeWidth.toPx() * 2.4f, cap = StrokeCap.Round)
        )

        // Crisp core stroke
        drawPath(
            path = strokePath,
            color = lineColor,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * 11. JarvisMultiWavePerformanceChart (Directly from Image 2 "Performance Trends" Card!)
 * Renders 3 overlapping smooth glowing Bezier curves (Cyan, Violet, Neon Green) over a subtle grid.
 */
@Composable
fun JarvisMultiWavePerformanceChart(
    modifier: Modifier = Modifier
) {
    val waveCyan = listOf(0.18f, 0.42f, 0.36f, 0.68f, 0.58f, 0.84f, 0.76f)
    val waveViolet = listOf(0.14f, 0.28f, 0.52f, 0.34f, 0.88f, 0.62f, 0.72f)
    val waveGreen = listOf(0.12f, 0.34f, 0.24f, 0.54f, 0.48f, 0.78f, 0.88f)
    val days = listOf("Su", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Subtle horizontal grid lines
                for (i in 0..3) {
                    val y = h * (i / 3f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.06f),
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
                            listOf(color.copy(alpha = 0.16f), Color.Transparent)
                        )
                    )
                    drawPath(
                        path = path,
                        color = color.copy(alpha = 0.3f),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                drawSmoothWave(waveViolet, JarvisVioletBright)
                drawSmoothWave(waveGreen, JarvisNeonGreen)
                drawSmoothWave(waveCyan, JarvisNeonCyan)
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
                    color = JarvisTextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * 12. JarvisSquircleQuickAction (Directly from Image 2 WealthWave 4 Squircle Action Row!)
 */
@Composable
fun JarvisSquircleQuickAction(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.28f),
                            Color(0xFF131829).copy(alpha = 0.92f)
                        )
                    )
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.85f),
                            accentColor.copy(alpha = 0.25f)
                        )
                    ),
                    shape = RoundedCornerShape(18.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(25.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = JarvisTextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 13. JarvisGradientTitleWithLaserFlare (Directly from Image 8 "Full Control Without the Complexity"!)
 */
@Composable
fun JarvisGradientTitleWithLaserFlare(
    leadingWord: String,
    gradientWord: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 26.sp
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = fontSize
                    )
                ) {
                    append("$leadingWord ")
                }
                withStyle(
                    SpanStyle(
                        brush = Brush.horizontalGradient(
                            colors = listOf(JarvisVioletBright, JarvisElectricBlue, JarvisNeonCyan)
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
            color = JarvisTextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Glowing horizontal violet-cyan laser flare line (Image 8 reference)
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
                                JarvisVioletBright,
                                Color.White,
                                JarvisNeonCyan,
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .size(width = 24.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White, JarvisVioletBright, Color.Transparent)
                        )
                    )
            )
        }
    }
}

/**
 * 14. JarvisAudioWaveform
 */
@Composable
fun JarvisAudioWaveform(
    audioLevel: Float,
    isActive: Boolean,
    status: AssistantStatus,
    modifier: Modifier = Modifier
) {
    val primaryColor = when (status) {
        AssistantStatus.LISTENING -> JarvisNeonGreen
        AssistantStatus.THINKING -> JarvisVioletBright
        AssistantStatus.SPEAKING -> JarvisNeonCyan
        AssistantStatus.IDLE -> JarvisElectricBlue
    }
    val secondaryColor = when (status) {
        AssistantStatus.LISTENING -> JarvisNeonCyan
        AssistantStatus.THINKING -> JarvisElectricBlue
        AssistantStatus.SPEAKING -> JarvisViolet
        AssistantStatus.IDLE -> JarvisViolet
    }

    val multipliers = listOf(
        0.25f, 0.42f, 0.64f, 0.85f, 0.58f, 0.95f, 1.0f, 0.78f,
        0.92f, 0.66f, 0.88f, 0.52f, 0.74f, 0.40f, 0.24f
    )

    Row(
        modifier = modifier
            .height(36.dp)
            .fillMaxWidth(0.82f),
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
                                primaryColor.copy(alpha = if (isActive) 0.95f else 0.40f),
                                secondaryColor.copy(alpha = if (isActive) 0.85f else 0.25f)
                            )
                        )
                    )
            )
        }
    }
}

/**
 * 15. JarvisFeatureTile
 */
@Composable
fun JarvisFeatureTile(
    title: String,
    subtitle: String,
    badge: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isHero: Boolean = false
) {
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = if (isHero) {
                        listOf(
                            accentColor.copy(alpha = 0.24f),
                            Color(0xFF171D30).copy(alpha = 0.94f),
                            Color(0xFF0E1320).copy(alpha = 0.96f)
                        )
                    } else {
                        listOf(
                            Color(0xFF171D30).copy(alpha = 0.92f),
                            Color(0xFF0F1320).copy(alpha = 0.96f)
                        )
                    }
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.20f),
                        accentColor.copy(alpha = 0.55f),
                        JarvisGlassBorder
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
                                    accentColor.copy(alpha = 0.28f),
                                    accentColor.copy(alpha = 0.10f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            accentColor.copy(alpha = 0.5f),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accentColor.copy(alpha = 0.14f))
                        .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
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
                        color = JarvisTextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = JarvisTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 16. JarvisSectionHeader
 */
@Composable
fun JarvisSectionHeader(
    title: String,
    codeBadge: String,
    icon: ImageVector? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
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
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = JarvisTextPrimary,
                fontWeight = FontWeight.ExtraBold
            )
        }

        if (codeBadge.isNotBlank()) {
            Text(
                text = codeBadge,
                style = MaterialTheme.typography.labelSmall,
                color = JarvisTextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF151A2C))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * 17. JarvisSearchBar (Recessed dark pill search bar from Image 3 & Image 4!)
 */
@Composable
fun JarvisSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = JarvisTextMuted
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = JarvisTextSecondary
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear Search",
                        tint = JarvisTextSecondary
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(999.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF141929),
            unfocusedContainerColor = Color(0xFF111522),
            focusedBorderColor = JarvisNeonCyan,
            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
            focusedTextColor = JarvisTextPrimary,
            unfocusedTextColor = JarvisTextPrimary
        ),
        modifier = modifier.let {
            if (testTag != null) it.testTag(testTag) else it
        }
    )
}

/**
 * 18. JarvisSettingsTile
 */
@Composable
fun JarvisSettingsTile(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    icon: ImageVector? = null,
    accentColor: Color = JarvisNeonCyan
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF131828).copy(alpha = 0.85f))
            .border(
                1.dp,
                if (checked) accentColor.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.10f),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = JarvisTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisTextSecondary
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF050811),
                checkedTrackColor = accentColor,
                uncheckedThumbColor = JarvisTextSecondary,
                uncheckedTrackColor = JarvisElevatedSurface,
                uncheckedBorderColor = JarvisGlassBorder
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

/**
 * 19. JarvisAnimatedIndicator
 */
@Composable
fun JarvisAnimatedIndicator(
    label: String,
    accentColor: Color = JarvisNeonCyan,
    modifier: Modifier = Modifier
) {
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
            .background(accentColor.copy(alpha = 0.14f))
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 20. JarvisModernDialog (Directly inspired by Image 6 "System Update" Royal Blue Glass Modal!)
 */
@Composable
fun JarvisModernDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false
) {
    val borderAccent = if (isDestructive) AlertCrimson else JarvisNeonCyan

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        containerColor = Color(0xFF0A142E),
        titleContentColor = Color.White,
        textContentColor = JarvisTextSecondary,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "JARVIS SYSTEM PROTOCOL",
                    style = MaterialTheme.typography.labelSmall,
                    color = borderAccent,
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
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
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextSecondary
                )
            }
        },
        confirmButton = {
            // Pure White Pill Button with Bold Black Text (Image 6 "Restart Now" style)
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) AlertCrimson else Color.White,
                    contentColor = if (isDestructive) Color.White else Color(0xFF050811)
                ),
                modifier = Modifier.testTag("confirm_dialog_accept")
            ) {
                Text(
                    text = confirmLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
                modifier = Modifier.testTag("confirm_dialog_cancel")
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = JarvisTextSecondary
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
    glowColor: Color = MaterialTheme.colorScheme.primary,
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
