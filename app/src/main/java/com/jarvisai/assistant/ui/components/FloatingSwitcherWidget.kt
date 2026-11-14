package com.jarvisai.assistant.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val JarvisDockBg = Color(0xFF0A0F1D).copy(alpha = 0.94f)
private val JarvisCyan = Color(0xFF00E5FF)
private val JarvisViolet = Color(0xFF8B5CF6)
private val JarvisBorder = Color(0xFF1E3A5F)

data class SwitcherDestination(
    val id: String,
    val label: String,
    val icon: ImageVector
)

val DefaultSwitcherItems = listOf(
    SwitcherDestination("home", "Chat", Icons.AutoMirrored.Filled.Chat),
    SwitcherDestination("ide", "Sora IDE", Icons.Default.Code),
    SwitcherDestination("terminal", "Terminal", Icons.Default.Terminal),
    SwitcherDestination("files", "Files", Icons.Default.Folder),
    SwitcherDestination("browser", "Browser", Icons.Default.Search)
)

/**
 * Futuristic Jarvis Cyberpunk Edge Dock.
 * - Smoothly draggable anywhere vertically & horizontally, with magnetic left/right edge snapping.
 * - Uses measured IntSize via onSizeChanged so the dock never clips off the screen edge.
 * - Collapsed state: Glowing Arc Reactor edge pill.
 * - Tap to expand: Reveals 1-tap navigation rail to Chat, IDE, Terminal, Files, Browser.
 * - Double tap on collapsed pill: Bounces back to the previously active screen.
 * - Long-press: Flips between Vertical and Horizontal orientation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingSwitcherWidget(
    activeScreen: String,
    onNavigate: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    items: List<SwitcherDestination> = DefaultSwitcherItems
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var isExpanded by remember { mutableStateOf(false) }
    var isVertical by remember { mutableStateOf(true) }

    var offsetYRatio by remember { mutableFloatStateOf(0.45f) }
    var isAnchorRight by remember { mutableStateOf(true) }

    var isDragging by remember { mutableStateOf(false) }
    var dragXPx by remember { mutableFloatStateOf(0f) }
    var dragYPx by remember { mutableFloatStateOf(0f) }
    var widgetSize by remember { mutableStateOf(IntSize.Zero) }

    var lastTapTime by remember { mutableLongStateOf(0L) }

    // Auto-collapse after 7 seconds of inactivity when expanded
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            delay(7000L)
            isExpanded = false
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(999f)
            .testTag("floating_switcher_widget")
    ) {
        val parentWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(100f)
        val parentHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(200f)
        val fallbackWidthPx = with(density) { (if (isExpanded) 72.dp else 56.dp).toPx() }
        val fallbackHeightPx = with(density) { (if (isExpanded) 240.dp else 52.dp).toPx() }
        val measuredWidthPx = if (widgetSize.width > 0) widgetSize.width.toFloat() else fallbackWidthPx
        val measuredHeightPx = if (widgetSize.height > 0) widgetSize.height.toFloat() else fallbackHeightPx

        val minYPx = with(density) { 12.dp.toPx() }
        val maxXPx = (parentWidthPx - measuredWidthPx).coerceAtLeast(0f)
        val maxYPx = (parentHeightPx - measuredHeightPx - minYPx).coerceAtLeast(minYPx)

        val anchoredX = if (isAnchorRight) maxXPx else 0f
        val anchoredY = (parentHeightPx * offsetYRatio).coerceIn(minYPx, maxYPx)

        val targetOffset = if (isDragging) {
            IntOffset(
                x = dragXPx.coerceIn(0f, maxXPx).roundToInt(),
                y = dragYPx.coerceIn(minYPx, maxYPx).roundToInt()
            )
        } else {
            IntOffset(
                x = anchoredX.roundToInt(),
                y = anchoredY.roundToInt()
            )
        }

        val animatedOffset by animateIntOffsetAsState(
            targetValue = targetOffset,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = if (isDragging) 10_000f else Spring.StiffnessMediumLow
            ),
            label = "floating_switcher_offset"
        )

        val currentAnchoredXState = rememberUpdatedStateValue(anchoredX)
        val currentAnchoredYState = rememberUpdatedStateValue(anchoredY)
        val currentMaxXPxState = rememberUpdatedStateValue(maxXPx)
        val currentMaxYPxState = rememberUpdatedStateValue(maxYPx)
        val currentMinYPxState = rememberUpdatedStateValue(minYPx)
        val currentParentHeightState = rememberUpdatedStateValue(parentHeightPx)
        val currentMeasuredWidthState = rememberUpdatedStateValue(measuredWidthPx)

        Box(
            modifier = Modifier
                .offset { if (isDragging) targetOffset else animatedOffset }
                .onSizeChanged { widgetSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragXPx = currentAnchoredXState.value
                            dragYPx = currentAnchoredYState.value
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragXPx = (dragXPx + dragAmount.x).coerceIn(0f, currentMaxXPxState.value)
                            dragYPx = (dragYPx + dragAmount.y).coerceIn(
                                currentMinYPxState.value,
                                currentMaxYPxState.value
                            )
                        },
                        onDragEnd = {
                            val centerX = dragXPx + (currentMeasuredWidthState.value / 2f)
                            val halfParent = (currentMaxXPxState.value + currentMeasuredWidthState.value) / 2f
                            isAnchorRight = centerX >= halfParent
                            offsetYRatio = (dragYPx / currentParentHeightState.value).coerceIn(0.02f, 0.92f)
                            isDragging = false
                        },
                        onDragCancel = {
                            offsetYRatio = (dragYPx / currentParentHeightState.value).coerceIn(0.02f, 0.92f)
                            isDragging = false
                        }
                    )
                }
        ) {
            val dockShape = when {
                isDragging -> RoundedCornerShape(20.dp)
                isAnchorRight -> RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                else -> RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
            }

            Surface(
                shape = dockShape,
                color = JarvisDockBg,
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(listOf(JarvisCyan.copy(alpha = 0.6f), JarvisViolet.copy(alpha = 0.4f)))
                ),
                shadowElevation = 12.dp
            ) {
                if (!isExpanded) {
                    // --- COLLAPSED ARC REACTOR PILL ---
                    val currentDest = items.find { it.id == activeScreen } ?: items.first()
                    Row(
                        modifier = Modifier
                            .combinedClickable(
                                onClick = {
                                    val now = System.currentTimeMillis()
                                    if (now - lastTapTime < 350L) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onNavigateBack()
                                    } else {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        isExpanded = true
                                    }
                                    lastTapTime = now
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isVertical = !isVertical
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isAnchorRight) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(JarvisCyan.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(currentDest.icon, contentDescription = currentDest.label, tint = JarvisCyan, modifier = Modifier.size(17.dp))
                        }

                        if (isAnchorRight) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                        }
                    }
                } else {
                    // --- EXPANDED CYBER RAIL ---
                    if (isVertical) {
                        Column(
                            modifier = Modifier.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items.forEach { dest ->
                                val isSelected = dest.id == activeScreen
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onNavigate(dest.id)
                                        isExpanded = false
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) JarvisCyan.copy(alpha = 0.25f) else Color.Transparent)
                                ) {
                                    Icon(
                                        dest.icon,
                                        contentDescription = dest.label,
                                        tint = if (isSelected) JarvisCyan else Color.LightGray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            HorizontalDivider(
                                color = JarvisBorder,
                                thickness = 0.5.dp,
                                modifier = Modifier
                                    .width(32.dp)
                                    .padding(vertical = 2.dp)
                            )

                            IconButton(
                                onClick = { isExpanded = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    if (isAnchorRight) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                                    contentDescription = "Collapse",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items.forEach { dest ->
                                val isSelected = dest.id == activeScreen
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onNavigate(dest.id)
                                        isExpanded = false
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) JarvisCyan.copy(alpha = 0.25f) else Color.Transparent)
                                ) {
                                    Icon(
                                        dest.icon,
                                        contentDescription = dest.label,
                                        tint = if (isSelected) JarvisCyan else Color.LightGray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { isExpanded = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    if (isAnchorRight) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                                    contentDescription = "Collapse",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> rememberUpdatedStateValue(value: T): androidx.compose.runtime.State<T> {
    return androidx.compose.runtime.rememberUpdatedState(value)
}
