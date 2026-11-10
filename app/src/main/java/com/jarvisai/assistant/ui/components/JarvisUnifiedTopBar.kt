package com.jarvisai.assistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * AntiGem-Grade Minimalist Unified Top Bar.
 * Unites Drawer toggle, Model Selector Pill, Live status dot, and 1-tap Terminal / IDE / HUD shortcuts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisUnifiedTopBar(
    activeModelName: String = "Gemini 2.0 Live",
    isAgentBusy: Boolean = false,
    isVoiceActive: Boolean = false,
    onMenuClick: () -> Unit,
    onModelSelect: (String) -> Unit,
    onTerminalClick: () -> Unit,
    onIdeClick: () -> Unit,
    onOverlayHudClick: () -> Unit
) {
    var showModelMenu by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "StatusDotPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Alpha"
    )

    val statusDotColor = when {
        isAgentBusy -> Color(0xFFF59E0B) // Amber (Agent Busy)
        isVoiceActive -> Color(0xFF00E5FF) // Neon Cyan (Listening / Voice Active)
        else -> Color(0xFF10B981) // Emerald Green (Online)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0A0E17),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Hamburger Menu Button [☰]
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("top_bar_drawer_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Navigation Drawer",
                    tint = Color.White
                )
            }

            // Center: Model Selector Pill [ Gemini 2.0 Live ▾ ]
            Box {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { showModelMenu = true }
                        .testTag("top_bar_model_selector_pill"),
                    color = Color(0xFF111827),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.28f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusDotColor.copy(alpha = pulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = activeModelName,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Model",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showModelMenu,
                    onDismissRequest = { showModelMenu = false },
                    modifier = Modifier.background(Color(0xFF111827))
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "⚡ Gemini 2.0 Flash Live Voice",
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        },
                        onClick = {
                            onModelSelect("Gemini 2.0 Live")
                            showModelMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "🤖 Claude Code CLI Supervisor",
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        },
                        onClick = {
                            onModelSelect("Claude Code")
                            showModelMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "🌐 OpenCode Multi-Provider Router",
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        },
                        onClick = {
                            onModelSelect("OpenCode")
                            showModelMenu = false
                        }
                    )
                }
            }

            // Right: 1-Tap Quick Action Icons [💻 Terminal] [📝 Sora IDE] [⚡ Arc HUD]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onTerminalClick,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("top_bar_terminal_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Open Embedded Linux Terminal",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onIdeClick,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("top_bar_ide_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Open Sora Code IDE",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onOverlayHudClick,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("top_bar_hud_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPictureAlt,
                        contentDescription = "Toggle Floating Arc Reactor HUD",
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
