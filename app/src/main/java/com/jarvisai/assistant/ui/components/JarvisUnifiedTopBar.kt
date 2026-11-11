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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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

data class ModelDropdownOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String = "FREE",
    val badgeColor: Color = Color(0xFF10B981)
)

/**
 * AntiGem-Grade Minimalist Unified Top Bar.
 * Unites Drawer toggle, Categorized 2026 Model Selector Pill, Live status dot,
 * and 1-tap Terminal / IDE / HUD shortcuts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisUnifiedTopBar(
    activeModelName: String = "Gemini 3.8 Flash",
    thinkingLevelLabel: String = "Med",
    onOpenThinkingSelector: () -> Unit = {},
    isAgentBusy: Boolean = false,
    isVoiceActive: Boolean = false,
    onMenuClick: () -> Unit,
    onModelSelect: (modelId: String, displayName: String) -> Unit,
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
                    .testTag("topbar_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Navigation Drawer",
                    tint = Color.White
                )
            }

            // Center: Minimalist Model Selector Pill + Thinking Level Quick Pill
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF111827),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.28f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showModelMenu = true }
                            .testTag("topbar_model_selector_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusDotColor.copy(alpha = pulseAlpha))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeModelName,
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(2.dp))
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
                    modifier = Modifier
                        .background(Color(0xFF111827))
                        .width(310.dp)
                ) {
                    // --- SECTION 1: GOOGLE AI STUDIO 2026 FRONTIER LINEUP ---
                    ModelDropdownSectionHeader("GOOGLE AI STUDIO 2026 FRONTIER")

                    ModelMenuItem(
                        title = "⚡ Gemini 3.8 Flash",
                        subtitle = "Autonomous Agents & Software Eng • Recommended",
                        badge = "FLAGSHIP",
                        badgeColor = Color(0xFF00E5FF),
                        onClick = {
                            onModelSelect("gemini-3.8-flash", "Gemini 3.8 Flash")
                            showModelMenu = false
                        }
                    )

                    ModelMenuItem(
                        title = "👑 Gemini 4 Argon",
                        subtitle = "1M Context Window • Deep Multi-Step Reasoning",
                        badge = "FRONTIER",
                        badgeColor = Color(0xFFA78BFA),
                        onClick = {
                            onModelSelect("gemini-4-argon", "Gemini 4 Argon")
                            showModelMenu = false
                        }
                    )

                    ModelMenuItem(
                        title = "🚀 Gemini 3.5 Flash-Lite",
                        subtitle = "Sub-150ms High Throughput • Lowest Latency",
                        badge = "FAST $0",
                        badgeColor = Color(0xFF10B981),
                        onClick = {
                            onModelSelect("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite")
                            showModelMenu = false
                        }
                    )

                    ModelMenuItem(
                        title = "🎙️ Gemini 3.8 Live Voice",
                        subtitle = "Sub-500ms Conversational Audio • Zero Delay",
                        badge = "LIVE BIDI",
                        badgeColor = Color(0xFF00E5FF),
                        onClick = {
                            onModelSelect("gemini-3.8-live", "Gemini 3.8 Live")
                            showModelMenu = false
                        }
                    )

                    ModelMenuItem(
                        title = "🧠 Gemini 3.8 Live Extended Thinking",
                        subtitle = "Voice Agent with Background Reasoning Tokens",
                        badge = "THINKING",
                        badgeColor = Color(0xFFF59E0B),
                        onClick = {
                            onModelSelect("gemini-3.8-live-extended-thinking", "Gemini 3.8 Extended")
                            showModelMenu = false
                        }
                    )

                    HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 4.dp))

                    // --- SECTION 2: GOOGLE DEEPMIND ANTIGRAVITY ENGINE ---
                    ModelDropdownSectionHeader("GOOGLE DEEPMIND ANTIGRAVITY ENGINE")

                    ModelMenuItem(
                        title = "☁️ Gemini 3.8 Flash (Thinking)",
                        subtitle = "Frontier Reasoning with Low / Med / High Budgets",
                        badge = "DEFAULT",
                        badgeColor = Color(0xFF00E5FF),
                        onClick = {
                            onModelSelect("agy:gemini-3.8-flash", "Gemini 3.8 Flash (Thinking)")
                            showModelMenu = false
                        }
                    )

                    ModelMenuItem(
                        title = "⚡ Gemini 4 Argon (Thinking)",
                        subtitle = "Next-Gen Deep Reasoning (Pro Tier)",
                        badge = "PRO",
                        badgeColor = Color(0xFFA78BFA),
                        onClick = {
                            onModelSelect("agy:gemini-4-argon", "Gemini 4 Argon (Thinking)")
                            showModelMenu = false
                        }
                    )

                    ModelMenuItem(
                        title = "🧠 Gemini 4 Carbon (Thinking)",
                        subtitle = "Flagship 1M Context Frontier Reasoning (Ultra Tier)",
                        badge = "ULTRA",
                        badgeColor = Color(0xFFF59E0B),
                        onClick = {
                            onModelSelect("agy:gemini-4-carbon", "Gemini 4 Carbon (Thinking)")
                            showModelMenu = false
                        }
                    )

                    ModelMenuItem(
                        title = "☁️ Claude Sonnet 5.5 (Thinking)",
                        subtitle = "Anthropic Frontier Thinking via DeepMind Antigravity",
                        badge = "PRO TIER",
                        badgeColor = Color(0xFF38BDF8),
                        onClick = {
                            onModelSelect("agy:claude-sonnet-5.5", "Claude Sonnet 5.5 (Thinking)")
                            showModelMenu = false
                        }
                    )

                    HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 4.dp))

                    // --- SECTION 3: LOCAL TERMINAL AGENTS ---
                    ModelDropdownSectionHeader("LOCAL TERMINAL AGENTS")

                    ModelMenuItem(
                        title = "🤖 Claude Code CLI Supervisor",
                        subtitle = "Autonomous Terminal Coding with Permission Gates",
                        badge = "TERMINAL",
                        badgeColor = Color(0xFFF472B6),
                        onClick = {
                            onModelSelect("claude-code-cli", "Claude Code")
                            showModelMenu = false
                        }
                    )

                    ModelMenuItem(
                        title = "🌐 OpenCode Multi-Provider Router",
                        subtitle = "Ollama, DeepSeek, Local & Cloud Fallback",
                        badge = "ROUTER",
                        badgeColor = Color(0xFF64748B),
                        onClick = {
                            onModelSelect("opencode-router", "OpenCode")
                            showModelMenu = false
                        }
                    )
                }
                }

                // Thinking Level Quick Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOpenThinkingSelector() }
                        .testTag("topbar_thinking_selector_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Select Thinking Level",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = thinkingLevelLabel,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Right: 1-Tap Quick Action Icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Sora IDE Quick Action
                IconButton(
                    onClick = onIdeClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("topbar_ide_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Open Sora IDE",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Terminal Quick Action
                IconButton(
                    onClick = onTerminalClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("topbar_terminal_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Open Terminal",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Picture-in-Picture / Overlay HUD Action
                IconButton(
                    onClick = onOverlayHudClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("topbar_hud_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPictureAlt,
                        contentDescription = "Toggle Floating HUD",
                        tint = if (isAgentBusy) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelDropdownSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF64748B),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@Composable
private fun ModelMenuItem(
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColor.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    )
}
