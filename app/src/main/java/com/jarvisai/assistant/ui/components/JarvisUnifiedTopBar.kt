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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.data.remote.AntigravityModelInfo

data class ModelDropdownOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String = "FREE",
    val badgeColor: Color = Color(0xFF10B981)
)

/**
 * AntiGem-Grade Minimalist Unified Top Bar.
 * Dynamically populates the Antigravity section ONLY with models supported by
 * the user's logged-in Google Antigravity account/subscription tier, plus live
 * Low/Med/High/Extra Thinking toggle and Terminal / IDE / HUD shortcuts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisUnifiedTopBar(
    activeModelName: String = "⚡ Google AI Studio • Gemini 3.8 Flash",
    thinkingLevelLabel: String = "Med",
    isThinkingEnabled: Boolean = true,
    isAntigravityLoggedIn: Boolean = false,
    antigravityTier: String = "free",
    antigravityModels: List<AntigravityModelInfo> = emptyList(),
    isRefreshingAntigravityModels: Boolean = false,
    onOpenAntigravityLogin: () -> Unit = {},
    onRefreshAntigravityModels: () -> Unit = {},
    onOpenThinkingSelector: () -> Unit = {},
    isAgentBusy: Boolean = false,
    isVoiceActive: Boolean = false,
    showNewChatAction: Boolean = false,
    onNewChatClick: (() -> Unit)? = null,
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
        isAgentBusy -> Color(0xFFF59E0B)
        isVoiceActive -> Color(0xFF00E5FF)
        else -> Color(0xFF10B981)
    }

    val tierBadgeLabel = remember(antigravityTier) {
        formatAntigravityTierBadge(antigravityTier)
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(modifier = Modifier.weight(1f, fill = false)) {
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                            .width(330.dp)
                            .heightIn(max = 520.dp)
                    ) {
                        // --- SECTION 1: GOOGLE DEEPMIND ANTIGRAVITY (ACCOUNT-ENTITLED MODELS ONLY) ---
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "☁️ ANTIGRAVITY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E5FF)
                                )
                                if (isAntigravityLoggedIn) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFA78BFA).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = tierBadgeLabel,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFA78BFA),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            if (isAntigravityLoggedIn) {
                                if (isRefreshingAntigravityModels) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF00E5FF)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh Antigravity Models",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .clickable { onRefreshAntigravityModels() }
                                            .testTag("dropdown_refresh_agy_models")
                                    )
                                }
                            }
                        }

                        if (!isAntigravityLoggedIn) {
                            ModelMenuItem(
                                title = "🔐 Sign in with Google Antigravity",
                                subtitle = "Sign in to load only the models supported by your Gmail / Pro / Ultra account",
                                badge = "LOGIN",
                                badgeColor = Color(0xFF00E5FF),
                                onClick = {
                                    showModelMenu = false
                                    onOpenAntigravityLogin()
                                }
                            )
                        } else if (antigravityModels.isEmpty()) {
                            ModelMenuItem(
                                title = if (isRefreshingAntigravityModels) {
                                    "⏳ Loading your Antigravity models..."
                                } else {
                                    "🔄 Sync Account Models"
                                },
                                subtitle = "Tap to fetch supported models & quota for your account",
                                badge = "SYNC",
                                badgeColor = Color(0xFFF59E0B),
                                onClick = {
                                    onRefreshAntigravityModels()
                                }
                            )
                        } else {
                            antigravityModels.forEach { agyModel ->
                                val quotaPct = agyModel.quotaPercent
                                val quotaColor = when {
                                    quotaPct <= 15 -> Color(0xFFEF4444)
                                    quotaPct <= 40 -> Color(0xFFF59E0B)
                                    else -> Color(0xFF10B981)
                                }
                                val emoji = when {
                                    agyModel.id.contains("orchestrator", true) -> "🕸️"
                                    agyModel.id.contains("carbon", true) -> "🧠"
                                    agyModel.id.contains("argon", true) -> "⚡"
                                    agyModel.id.contains("hydrogen", true) -> "🚀"
                                    agyModel.id.contains("claude", true) || agyModel.id.contains("opus", true) || agyModel.id.contains("sonnet", true) -> "🎭"
                                    agyModel.id.contains("oss", true) -> "🧩"
                                    else -> "☁️"
                                }
                                val thinkingHint = if (agyModel.supportsThinking) {
                                    "Thinking ($thinkingLevelLabel)"
                                } else {
                                    "Direct Mode"
                                }
                                val tagExtra = agyModel.tagTitle.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()
                                val badgeText = if (agyModel.isRecommended) "REC • $quotaPct%" else "$quotaPct%"
                                val cleanDisplay = agyModel.displayName.ifBlank { agyModel.id }

                                ModelMenuItem(
                                    title = "$emoji $cleanDisplay",
                                    subtitle = "$thinkingHint • Quota $quotaPct%$tagExtra",
                                    badge = badgeText,
                                    badgeColor = quotaColor,
                                    onClick = {
                                        onModelSelect("agy:${agyModel.id}", "☁️ Antigravity • $cleanDisplay")
                                        showModelMenu = false
                                    }
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 4.dp))

                        // --- SECTION 2: GOOGLE AI STUDIO • FREE DEVELOPER API ---
                        ModelDropdownSectionHeader("⚡ GOOGLE AI STUDIO • DEVELOPER API")

                        ModelMenuItem(
                            title = "🚀 Gemini 4 Hydrogen",
                            subtitle = "Gemini 4 Flagship High-Efficiency • Low/Med/High/xHigh Thinking",
                            badge = "NEW FREE",
                            badgeColor = Color(0xFF10B981),
                            onClick = {
                                onModelSelect("gemini-4-hydrogen", "⚡ Google AI Studio • Gemini 4 Hydrogen")
                                showModelMenu = false
                            }
                        )

                        ModelMenuItem(
                            title = "⚡ Gemini 4 Argon",
                            subtitle = "Gemini 4 Deep Reasoning & Coding • 1M Context (Paid Tier)",
                            badge = "GEMINI 4",
                            badgeColor = Color(0xFFA78BFA),
                            onClick = {
                                onModelSelect("gemini-4-argon", "⚡ Google AI Studio • Gemini 4 Argon")
                                showModelMenu = false
                            }
                        )

                        ModelMenuItem(
                            title = "⚡ Gemini 3.8 Flash",
                            subtitle = "Flagship Multimodal • Supports Off/Low/Med/High Thinking",
                            badge = "DEFAULT",
                            badgeColor = Color(0xFF00E5FF),
                            onClick = {
                                onModelSelect("gemini-3.8-flash", "⚡ Google AI Studio • Gemini 3.8 Flash")
                                showModelMenu = false
                            }
                        )

                        ModelMenuItem(
                            title = "🚀 Gemini 3.5 Flash Lite",
                            subtitle = "Ultra-Low Latency Intent Router • 30 RPM • Free Tier",
                            badge = "FAST $0",
                            badgeColor = Color(0xFF10B981),
                            onClick = {
                                onModelSelect("gemini-3.5-flash-lite", "⚡ Google AI Studio • Gemini 3.5 Lite")
                                showModelMenu = false
                            }
                        )

                        ModelMenuItem(
                            title = "🛡️ Gemini 3.1 Flash Lite",
                            subtitle = "Stable High-Throughput Workhorse • 1M Context",
                            badge = "STABLE",
                            badgeColor = Color(0xFF38BDF8),
                            onClick = {
                                onModelSelect("gemini-3.1-flash-lite", "⚡ Google AI Studio • Gemini 3.1 Lite")
                                showModelMenu = false
                            }
                        )

                        ModelMenuItem(
                            title = "👑 Gemini 2.5 Pro",
                            subtitle = "2M Context Window • Deep Codebase Analysis",
                            badge = "2M CTX",
                            badgeColor = Color(0xFFA78BFA),
                            onClick = {
                                onModelSelect("gemini-2.5-pro", "⚡ Google AI Studio • Gemini 2.5 Pro")
                                showModelMenu = false
                            }
                        )

                        ModelMenuItem(
                            title = "🎙️ Gemini Live Voice",
                            subtitle = "Sub-500ms Native Audio Preview • Zero Delay",
                            badge = "LIVE BIDI",
                            badgeColor = Color(0xFF00E5FF),
                            onClick = {
                                onModelSelect("gemini-3.8-live", "⚡ Google AI Studio • Live Voice")
                                showModelMenu = false
                            }
                        )

                        HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 4.dp))

                        // --- SECTION 3: IN-APP & TERMINAL CODING AGENTS (ISOLATED FROM ANTIGEM) ---
                        ModelDropdownSectionHeader("IN-APP & TERMINAL CODING AGENTS (ISOLATED)")

                        ModelMenuItem(
                            title = "⚡ Antigravity Autonomous Agent (:2235)",
                            subtitle = "Isolated Cascade Hub (:2235) + MCP (:2236/:2237) + Sora IDE",
                            badge = "PORT 2235",
                            badgeColor = Color(0xFF00E5FF),
                            onClick = {
                                onModelSelect("antigravity-agent", "☁️ Antigravity Agent")
                                showModelMenu = false
                            }
                        )

                        ModelMenuItem(
                            title = "🎭 Claude Code CLI Agent",
                            subtitle = "Anthropic Claude Code Stream-JSON Agent in ~/jarvis-workspace",
                            badge = "CLAUDE CLI",
                            badgeColor = Color(0xFFA78BFA),
                            onClick = {
                                onModelSelect("claude-code-cli", "🎭 Claude Code Agent")
                                showModelMenu = false
                            }
                        )

                        ModelMenuItem(
                            title = "🌐 OpenCode Multi-Provider Agent (:2240)",
                            subtitle = "Isolated OpenCode Server (:2240) • Ollama, DeepSeek & Local",
                            badge = "PORT 2240",
                            badgeColor = Color(0xFF10B981),
                            onClick = {
                                onModelSelect("opencode-router", "🌐 OpenCode (:2240)")
                                showModelMenu = false
                            }
                        )

                        ModelMenuItem(
                            title = "🧩 OpenAI Codex CLI Agent",
                            subtitle = "Terminal Autonomous Coding Agent in ~/jarvis-workspace",
                            badge = "CODEX CLI",
                            badgeColor = Color(0xFFF59E0B),
                            onClick = {
                                onModelSelect("codex-cli-agent", "🧩 Codex CLI Agent")
                                showModelMenu = false
                            }
                        )
                    }
                }

                // Thinking Level Quick Pill
                val pillBorderColor = if (isThinkingEnabled) Color(0xFF00E5FF).copy(alpha = 0.45f) else Color(0xFF334155)
                val pillTint = when (thinkingLevelLabel.lowercase()) {
                    "off" -> Color(0xFF64748B)
                    "low" -> Color(0xFF10B981)
                    "high" -> Color(0xFFA78BFA)
                    "extra" -> Color(0xFFF59E0B)
                    else -> Color(0xFF00E5FF)
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, pillBorderColor),
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOpenThinkingSelector() }
                        .testTag("topbar_thinking_selector_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Select Thinking Level",
                            tint = pillTint,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = thinkingLevelLabel,
                            color = if (isThinkingEnabled) Color.White else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (showNewChatAction && onNewChatClick != null) {
                    IconButton(
                        onClick = onNewChatClick,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("topbar_new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddComment,
                            contentDescription = "Start New Chat",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
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

fun formatAntigravityTierBadge(rawTier: String): String {
    val t = rawTier.trim().lowercase()
    return when {
        t.contains("ultra") -> "AI ULTRA"
        t.contains("pro") -> "AI PRO"
        t.contains("plus") -> "AI PLUS"
        t.contains("enterprise") || t.contains("standard") -> "ENTERPRISE"
        t.isBlank() || t.contains("free") -> "FREE TIER"
        else -> rawTier.uppercase()
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
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
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
