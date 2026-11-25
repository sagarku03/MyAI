package com.jarvisai.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

@Composable
fun ModelSelectorDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    thinkingLevelLabel: String,
    isThinkingEnabled: Boolean,
    isAntigravityLoggedIn: Boolean,
    antigravityTier: String,
    antigravityModels: List<AntigravityModelInfo>,
    isRefreshingAntigravityModels: Boolean,
    onOpenAntigravityLogin: () -> Unit,
    onRefreshAntigravityModels: () -> Unit,
    onOpenThinkingSelector: () -> Unit,
    onOpenCactusManager: () -> Unit = {},
    onModelSelect: (modelId: String, displayName: String) -> Unit
) {
    val cactusStatus by com.jarvisai.assistant.cactus.CactusNativeRuntime.status.collectAsState()
    val cactusModelStates by com.jarvisai.assistant.cactus.CactusNativeRuntime.modelStates.collectAsState()
    val tierBadgeLabel = remember(antigravityTier) {
        formatAntigravityTierBadge(antigravityTier)
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .background(Color(0xFF141821))
            .width(330.dp)
            .heightIn(max = 520.dp)
    ) {
        // Quick Thinking Depth Selector Trigger (moved into Model Selector menu for clean UI)
        val thinkingTint = when (thinkingLevelLabel.lowercase()) {
            "off" -> Color(0xFF64748B)
            "low" -> Color(0xFF10B981)
            "high" -> Color(0xFFA78BFA)
            "extra" -> Color(0xFFF59E0B)
            else -> Color(0xFF00E5FF)
        }
        DropdownMenuItem(
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Thinking Depth",
                            tint = thinkingTint,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Thinking Depth",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = thinkingTint.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = if (isThinkingEnabled) thinkingLevelLabel.uppercase() else "OFF",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = thinkingTint,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            },
            onClick = {
                onDismissRequest()
                onOpenThinkingSelector()
            },
            modifier = Modifier.testTag("topbar_thinking_selector_pill")
        )

        HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 4.dp))

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
                    onDismissRequest()
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
                onClick = { onRefreshAntigravityModels() }
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
                        onDismissRequest()
                    }
                )
            }
        }

        HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 4.dp))

        // --- SECTION 2: GOOGLE AI STUDIO • DEVELOPER API ---
        ModelDropdownSectionHeader("⚡ GOOGLE AI STUDIO • DEVELOPER API")

        ModelMenuItem(
            title = "🚀 Gemini 4 Hydrogen",
            subtitle = "Gemini 4 Flagship High-Efficiency • Low/Med/High/xHigh Thinking",
            badge = "NEW FREE",
            badgeColor = Color(0xFF10B981),
            onClick = {
                onModelSelect("gemini-4-hydrogen", "⚡ Google AI Studio • Gemini 4 Hydrogen")
                onDismissRequest()
            }
        )

        ModelMenuItem(
            title = "⚡ Gemini 4 Argon",
            subtitle = "Gemini 4 Deep Reasoning & Coding • 1M Context (Paid Tier)",
            badge = "GEMINI 4",
            badgeColor = Color(0xFFA78BFA),
            onClick = {
                onModelSelect("gemini-4-argon", "⚡ Google AI Studio • Gemini 4 Argon")
                onDismissRequest()
            }
        )

        ModelMenuItem(
            title = "⚡ Gemini 3.8 Flash",
            subtitle = "Flagship Multimodal • Supports Off/Low/Med/High Thinking",
            badge = "DEFAULT",
            badgeColor = Color(0xFF00E5FF),
            onClick = {
                onModelSelect("gemini-3.8-flash", "⚡ Google AI Studio • Gemini 3.8 Flash")
                onDismissRequest()
            }
        )

        ModelMenuItem(
            title = "🚀 Gemini 3.5 Flash Lite",
            subtitle = "Ultra-Low Latency Intent Router • 30 RPM • Free Tier",
            badge = "FAST $0",
            badgeColor = Color(0xFF10B981),
            onClick = {
                onModelSelect("gemini-3.5-flash-lite", "⚡ Google AI Studio • Gemini 3.5 Lite")
                onDismissRequest()
            }
        )

        ModelMenuItem(
            title = "🛡️ Gemini 3.1 Flash Lite",
            subtitle = "Stable High-Throughput Workhorse • 1M Context",
            badge = "STABLE",
            badgeColor = Color(0xFF38BDF8),
            onClick = {
                onModelSelect("gemini-3.1-flash-lite", "⚡ Google AI Studio • Gemini 3.1 Lite")
                onDismissRequest()
            }
        )

        ModelMenuItem(
            title = "👑 Gemini 2.5 Pro",
            subtitle = "2M Context Window • Deep Codebase Analysis",
            badge = "2M CTX",
            badgeColor = Color(0xFFA78BFA),
            onClick = {
                onModelSelect("gemini-2.5-pro", "⚡ Google AI Studio • Gemini 2.5 Pro")
                onDismissRequest()
            }
        )

        ModelMenuItem(
            title = "🎙️ Gemini Live Voice",
            subtitle = "Sub-500ms Native Audio Preview • Zero Delay",
            badge = "LIVE BIDI",
            badgeColor = Color(0xFF00E5FF),
            onClick = {
                onModelSelect("gemini-3.8-live", "⚡ Google AI Studio • Live Voice")
                onDismissRequest()
            }
        )

        HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 4.dp))

        // --- SECTION 2.5: CACTUS COMPUTE ON-DEVICE AI (100% OFFLINE) ---
        ModelDropdownSectionHeader("🌵 CACTUS ON-DEVICE AI (OFFLINE)")

        com.jarvisai.assistant.cactus.CactusModelCatalog.chatCapableModels().forEach { spec ->
            val installed = cactusModelStates[spec.id]?.isDownloaded == true
            val badgeText = when {
                cactusStatus.isServerRunning && installed -> "ONLINE"
                installed -> "READY"
                cactusStatus.isServerRunning -> "SERVER ON"
                else -> "${spec.estimatedDownloadMb}MB"
            }
            val badgeCol = when {
                cactusStatus.isServerRunning || installed -> Color(0xFF10B981)
                else -> Color(0xFF64748B)
            }
            ModelMenuItem(
                title = "🌵 ${spec.displayName}",
                subtitle = "${spec.parameterLabel} • ${spec.quantizationLabel} • ${spec.recommendedRamGb}GB+ RAM" +
                    if (spec.supportsToolCalling) " • Local Tools" else "",
                badge = badgeText,
                badgeColor = badgeCol,
                onClick = {
                    onModelSelect(spec.id, "🌵 Cactus • ${spec.displayName}")
                    onDismissRequest()
                }
            )
        }

        ModelMenuItem(
            title = "⚙️ Manage Cactus Engine & Models",
            subtitle = if (cactusStatus.isServerRunning) {
                "Server ONLINE • Download LLM, VLM Mirage, STT Whistle & Embeddings"
            } else {
                "Install ARM64 Engine, download offline models & configure Vector RAG"
            },
            badge = if (cactusStatus.isServerRunning) "ACTIVE" else "SETUP",
            badgeColor = Color(0xFF00E5FF),
            onClick = {
                onDismissRequest()
                onOpenCactusManager()
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
                onDismissRequest()
            }
        )

        ModelMenuItem(
            title = "🎭 Claude Code CLI Agent",
            subtitle = "Anthropic Claude Code Stream-JSON Agent in ~/projects",
            badge = "CLAUDE CLI",
            badgeColor = Color(0xFFA78BFA),
            onClick = {
                onModelSelect("claude-code-cli", "🎭 Claude Code Agent")
                onDismissRequest()
            }
        )

        ModelMenuItem(
            title = "🌐 OpenCode Multi-Provider Agent (:2240)",
            subtitle = "Isolated OpenCode Server (:2240) • Ollama, DeepSeek & Local",
            badge = "PORT 2240",
            badgeColor = Color(0xFF10B981),
            onClick = {
                onModelSelect("opencode-router", "🌐 OpenCode (:2240)")
                onDismissRequest()
            }
        )

        ModelMenuItem(
            title = "🧩 OpenAI Codex CLI Agent",
            subtitle = "Terminal Autonomous Coding Agent in ~/projects",
            badge = "CODEX CLI",
            badgeColor = Color(0xFFF59E0B),
            onClick = {
                onModelSelect("codex-cli-agent", "🧩 Codex CLI Agent")
                onDismissRequest()
            }
        )
    }
}

fun formatCompactModelTitle(rawDisplayName: String): String {
    return rawDisplayName
        .replace("☁️ Antigravity • ", "")
        .replace("⚡ Google AI Studio • ", "")
        .replace("🌵 Cactus • ", "")
        .replace("☁️ ", "")
        .replace("⚡ ", "")
        .replace("🌵 ", "")
        .replace("🎭 ", "")
        .replace("🌐 ", "")
        .replace("🧩 ", "")
        .trim()
        .ifBlank { "Gemini 3.8 Flash" }
}

fun resolveActiveModelQuotaPercent(
    activeModelId: String,
    antigravityModels: List<AntigravityModelInfo>
): Int? {
    if (antigravityModels.isEmpty()) return null
    val rawAgyId = activeModelId.removePrefix("agy:").trim()
    val matched = antigravityModels.firstOrNull {
        it.id.equals(rawAgyId, ignoreCase = true) ||
            it.thinkingVariantIds.values.any { v -> v.equals(rawAgyId, ignoreCase = true) }
    }
    return matched?.quotaPercent
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
