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
    val themeColors = com.example.ui.theme.JarvisTheme.colors
    val cactusStatus by com.jarvisai.assistant.cactus.CactusNativeRuntime.status.collectAsState()
    val cactusModelStates by com.jarvisai.assistant.cactus.CactusNativeRuntime.modelStates.collectAsState()
    val tierBadgeLabel = remember(antigravityTier) {
        formatAntigravityTierBadge(antigravityTier)
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .background(themeColors.surfaceElevated)
            .width(330.dp)
            .heightIn(max = 520.dp)
    ) {
        val thinkingTint = when (thinkingLevelLabel.lowercase()) {
            "off" -> themeColors.textMuted
            "low" -> themeColors.statusEmerald
            "high" -> themeColors.accent
            "extra" -> themeColors.statusAmber
            else -> themeColors.accent
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
                            color = themeColors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = thinkingTint.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = if (isThinkingEnabled) thinkingLevelLabel else "Off",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = thinkingTint,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

        HorizontalDivider(color = themeColors.divider, modifier = Modifier.padding(vertical = 4.dp))

        // --- SECTION 1: ANTIGRAVITY ACCOUNT MODELS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Antigravity",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = themeColors.textMuted
                )
                if (isAntigravityLoggedIn) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = themeColors.surfacePill
                    ) {
                        Text(
                            text = tierBadgeLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = themeColors.textSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (isAntigravityLoggedIn) {
                if (isRefreshingAntigravityModels) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = themeColors.accent
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Antigravity Models",
                        tint = themeColors.textSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .clickable { onRefreshAntigravityModels() }
                            .testTag("dropdown_refresh_agy_models")
                    )
                }
            }
        }

        if (!isAntigravityLoggedIn) {
            ModelMenuItem(
                title = "Sign in with Google Antigravity",
                subtitle = "Load models and quotas linked to your account",
                badge = "Sign in",
                badgeColor = themeColors.accent,
                onClick = {
                    onDismissRequest()
                    onOpenAntigravityLogin()
                }
            )
        } else if (antigravityModels.isEmpty()) {
            ModelMenuItem(
                title = if (isRefreshingAntigravityModels) {
                    "Loading Antigravity models..."
                } else {
                    "Sync Account Models"
                },
                subtitle = "Fetch supported models and quota for your account",
                badge = "Sync",
                badgeColor = themeColors.statusAmber,
                onClick = { onRefreshAntigravityModels() }
            )
        } else {
            antigravityModels.forEach { agyModel ->
                val quotaPct = agyModel.quotaPercent
                val quotaColor = when {
                    quotaPct <= 15 -> themeColors.statusRoseRed
                    quotaPct <= 40 -> themeColors.statusAmber
                    else -> themeColors.statusEmerald
                }
                val thinkingHint = if (agyModel.supportsThinking) {
                    "Thinking ($thinkingLevelLabel)"
                } else {
                    "Direct"
                }
                val tagExtra = agyModel.tagTitle.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()
                val badgeText = if (agyModel.isRecommended) "Rec • $quotaPct%" else "$quotaPct%"
                val cleanDisplay = agyModel.displayName.ifBlank { agyModel.id }

                ModelMenuItem(
                    title = cleanDisplay,
                    subtitle = "$thinkingHint • Quota $quotaPct%$tagExtra",
                    badge = badgeText,
                    badgeColor = quotaColor,
                    onClick = {
                        onModelSelect("agy:${agyModel.id}", "Antigravity • $cleanDisplay")
                        onDismissRequest()
                    }
                )
            }
        }

        HorizontalDivider(color = themeColors.divider, modifier = Modifier.padding(vertical = 4.dp))

        // --- SECTION 2: GOOGLE AI STUDIO ---
        ModelDropdownSectionHeader("Google AI Studio")

        ModelMenuItem(
            title = "Gemini 4 Hydrogen",
            subtitle = "High efficiency • Low / Med / High / xHigh thinking",
            badge = "Free",
            badgeColor = themeColors.statusEmerald,
            onClick = {
                onModelSelect("gemini-4-hydrogen", "Google AI Studio • Gemini 4 Hydrogen")
                onDismissRequest()
            }
        )

        ModelMenuItem(
            title = "Gemini 4 Argon",
            subtitle = "Deep reasoning & coding • 1M context",
            badge = "Gemini 4",
            badgeColor = themeColors.accent,
            onClick = {
                onModelSelect("gemini-4-argon", "Google AI Studio • Gemini 4 Argon")
                onDismissRequest()
            }
        )

        ModelMenuItem(
            title = "Gemini 3.8 Flash",
            subtitle = "Multimodal • Configurable thinking depth",
            badge = "Default",
            badgeColor = themeColors.accent,
            onClick = {
                onModelSelect("gemini-3.8-flash", "Google AI Studio • Gemini 3.8 Flash")
                onDismissRequest()
            }
        )

        ModelMenuItem(
            title = "Gemini 3.5 Flash Lite",
            subtitle = "Low-latency router • 30 RPM • Free tier",
            badge = "Fast",
            badgeColor = themeColors.statusEmerald,
            onClick = {
                onModelSelect("gemini-3.5-flash-lite", "Google AI Studio • Gemini 3.5 Lite")
                onDismissRequest()
            }
        )

        ModelMenuItem(
            title = "Gemini 3.1 Flash Lite",
            subtitle = "High-throughput workhorse • 1M context",
            badge = "Stable",
            badgeColor = themeColors.accent,
            onClick = {
                onModelSelect("gemini-3.1-flash-lite", "Google AI Studio • Gemini 3.1 Lite")
                onDismissRequest()
            }
        )

        ModelMenuItem(
            title = "Gemini 2.5 Pro",
            subtitle = "2M context window • Codebase analysis",
            badge = "2M ctx",
            badgeColor = themeColors.accent,
            onClick = {
                onModelSelect("gemini-2.5-pro", "Google AI Studio • Gemini 2.5 Pro")
                onDismissRequest()
            }
        )

        ModelMenuItem(
            title = "Gemini Live Voice",
            subtitle = "Low-latency bidirectional voice stream",
            badge = "Live",
            badgeColor = themeColors.accent,
            onClick = {
                onModelSelect("gemini-3.8-live", "Google AI Studio • Live Voice")
                onDismissRequest()
            }
        )

        HorizontalDivider(color = themeColors.divider, modifier = Modifier.padding(vertical = 4.dp))

        // --- SECTION 2.5: CACTUS ON-DEVICE AI ---
        ModelDropdownSectionHeader("On-Device (Offline)")

        com.jarvisai.assistant.cactus.CactusModelCatalog.chatCapableModels().forEach { spec ->
            val installed = cactusModelStates[spec.id]?.isDownloaded == true
            val badgeText = when {
                spec.isInProcessSdkModel && installed -> "Ready"
                installed && spec.runtime == com.jarvisai.assistant.cactus.CactusModelRuntime.NATIVE_V4_CACT -> "Ready"
                cactusStatus.isServerRunning && !spec.isInProcessSdkModel -> "Online"
                spec.isExternalServerOnly -> "External"
                else -> "${spec.estimatedDownloadMb} MB"
            }
            val badgeCol = when {
                (spec.isInProcessSdkModel && installed) || cactusStatus.isServerRunning -> themeColors.statusEmerald
                installed -> themeColors.accent
                else -> themeColors.textMuted
            }
            val runtimeTag = when (spec.runtime) {
                com.jarvisai.assistant.cactus.CactusModelRuntime.IN_PROCESS_SDK -> "In-App JNI"
                com.jarvisai.assistant.cactus.CactusModelRuntime.NATIVE_V4_CACT -> "Tool Router"
                com.jarvisai.assistant.cactus.CactusModelRuntime.EXTERNAL_OPENAI -> "External Server"
            }
            ModelMenuItem(
                title = spec.displayName,
                subtitle = "${spec.parameterLabel} • $runtimeTag • ${spec.recommendedRamGb}GB+ RAM" +
                    if (spec.supportsToolCalling) " • Local Tools" else "",
                badge = badgeText,
                badgeColor = badgeCol,
                onClick = {
                    onModelSelect(spec.id, "Cactus • ${spec.displayName}")
                    onDismissRequest()
                }
            )
        }

        ModelMenuItem(
            title = "Manage Offline Models",
            subtitle = when {
                cactusStatus.sdkSupported && cactusModelStates.values.any { it.isDownloaded } ->
                    "JNI runtime ready • Qwen3, Gemma 3, LFM2-VL, Whisper"
                cactusStatus.isServerRunning ->
                    "Server running • Manage LLM, VLM, STT and embeddings"
                else ->
                    "Download Qwen3 / LFM2-VL or configure Cactus runtime"
            },
            badge = if (cactusStatus.isServerRunning || (cactusStatus.sdkSupported && cactusModelStates.values.any { it.isDownloaded })) "Active" else "Setup",
            badgeColor = themeColors.accent,
            onClick = {
                onDismissRequest()
                onOpenCactusManager()
            }
        )

        HorizontalDivider(color = themeColors.divider, modifier = Modifier.padding(vertical = 4.dp))

        // --- SECTION 3: CODING AGENTS ---
        ModelDropdownSectionHeader("Workspace Coding Agents")

        ModelMenuItem(
            title = "Antigravity Agent (:2235)",
            subtitle = "Cascade Hub (:2235) + MCP (:2236/:2237) + Sora IDE",
            badge = ":2235",
            badgeColor = themeColors.accent,
            onClick = {
                onModelSelect("antigravity-agent", "Antigravity Agent")
                onDismissRequest()
            }
        )

        ModelMenuItem(
            title = "Claude Code CLI",
            subtitle = "Stream-JSON coding agent in ~/projects",
            badge = "CLI",
            badgeColor = themeColors.textSecondary,
            onClick = {
                onModelSelect("claude-code-cli", "Claude Code Agent")
                onDismissRequest()
            }
        )

        ModelMenuItem(
            title = "OpenCode Agent (:2240)",
            subtitle = "Multi-provider local server (:2240)",
            badge = ":2240",
            badgeColor = themeColors.statusEmerald,
            onClick = {
                onModelSelect("opencode-router", "OpenCode (:2240)")
                onDismissRequest()
            }
        )

        ModelMenuItem(
            title = "OpenAI Codex CLI",
            subtitle = "Terminal autonomous coding agent in ~/projects",
            badge = "CLI",
            badgeColor = themeColors.statusAmber,
            onClick = {
                onModelSelect("codex-cli-agent", "Codex CLI Agent")
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
        .replace("Antigravity • ", "")
        .replace("Google AI Studio • ", "")
        .replace("Cactus • ", "")
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
        t.contains("ultra") -> "Ultra"
        t.contains("pro") -> "Pro"
        t.contains("plus") -> "Plus"
        t.contains("enterprise") || t.contains("standard") -> "Enterprise"
        t.isBlank() || t.contains("free") -> "Free"
        else -> rawTier
    }
}

@Composable
private fun ModelDropdownSectionHeader(title: String) {
    val themeColors = com.example.ui.theme.JarvisTheme.colors
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = themeColors.textMuted,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
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
    val themeColors = com.example.ui.theme.JarvisTheme.colors
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
                        color = themeColors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = themeColors.textSecondary
                )
            }
        },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    )
}
