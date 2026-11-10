package com.jarvisai.assistant.ui.components

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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
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
 * - Draggable on screen edges (retains Y position ratio).
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
    var isExpanded by remember { mutableStateOf(false) }
    var isVertical by remember { mutableStateOf(true) }

    var offsetYRatio by remember { mutableFloatStateOf(0.45f) }
    var isAnchorRight by remember { mutableStateOf(true) }

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
        val parentHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(300f)
        val currentYPx = (parentHeightPx * offsetYRatio).coerceIn(100f, (parentHeightPx - 200f).coerceAtLeast(100f))

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = if (isAnchorRight) (constraints.maxWidth - (if (isExpanded) 72.dp.roundToPx() else 44.dp.roundToPx())) else 0,
                        y = currentYPx.roundToInt()
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newY = currentYPx + dragAmount.y
                        offsetYRatio = (newY / parentHeightPx).coerceIn(0.1f, 0.85f)
                        if (change.position.x < constraints.maxWidth / 3) {
                            isAnchorRight = false
                        } else if (change.position.x > (constraints.maxWidth * 2) / 3) {
                            isAnchorRight = true
                        }
                    }
                }
        ) {
            Surface(
                shape = if (isAnchorRight) RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                else RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
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
                                        // Double tap -> bounce back to previous screen
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onNavigateBack()
                                    } else {
                                        // Single tap -> expand switcher dock
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

                            // Collapse Arrow
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
