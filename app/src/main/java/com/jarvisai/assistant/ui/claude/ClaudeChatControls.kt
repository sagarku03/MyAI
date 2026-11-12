package com.jarvisai.assistant.ui.claude

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.agent.claude.ClaudeConfigManager
import com.jarvisai.assistant.agent.claude.ClaudeRateLimits
import com.jarvisai.assistant.agent.claude.ClaudeUsageInfo

/**
 * Phase 3: Control deck for Claude Code CLI model switching, thinking budget sliders,
 * 5h / 7d plan utilization bars, and account management triggers.
 */
@Composable
fun ClaudeChatControls(
    settings: ClaudeConfigManager.ClaudeSettingsState,
    usage: ClaudeUsageInfo,
    rateLimits: ClaudeRateLimits,
    activeAccountEmail: String?,
    onSelectModel: (String) -> Unit,
    onToggleAutoApprove: (Boolean) -> Unit,
    onSelectThinkingBudget: (Int) -> Unit,
    onOpenLoginDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fiveHourUsedPct = ((1.0 - rateLimits.fiveHourWindowRemainingFraction).coerceIn(0.0, 1.0) * 100.0).toInt()
    val sevenDayUsedPct = ((1.0 - rateLimits.sevenDayWindowRemainingFraction).coerceIn(0.0, 1.0) * 100.0).toInt()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("claude_chat_controls_deck"),
        color = Color(0xFF161B22),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CLAUDE CODE ENGINE",
                        color = Color(0xFFD97757),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = activeAccountEmail?.ifBlank { "Local CLI Session" } ?: "Not signed in",
                        color = Color(0xFFE6EDF3),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = "Accounts / OAuth",
                    color = Color(0xFF58A6FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0D1117))
                        .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
                        .clickable { onOpenLoginDialog() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("claude_accounts_button")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Model selector chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ClaudeConfigManager.AVAILABLE_MODELS.forEach { (modelId, label) ->
                    val isSelected = settings.selectedModel == modelId
                    Text(
                        text = label,
                        color = if (isSelected) Color(0xFFF0F6FC) else Color(0xFF8B949E),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFFD97757).copy(alpha = 0.25f) else Color(0xFF0D1117))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFFD97757) else Color(0xFF30363D),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectModel(modelId) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("claude_model_chip_$modelId")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Thinking token budgets + auto-approve switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Off", 4096 to "4K", 8192 to "8K", 16000 to "16K", 32000 to "32K").forEach { (budget, label) ->
                        val active = settings.thinkingBudgetTokens == budget
                        Text(
                            text = "Think: $label",
                            color = if (active) Color(0xFF00E5FF) else Color(0xFF8B949E),
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0D1117))
                                .border(
                                    1.dp,
                                    if (active) Color(0xFF00E5FF) else Color(0xFF30363D),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onSelectThinkingBudget(budget) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Auto-Run",
                        color = Color(0xFF8B949E),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Switch(
                        checked = settings.autoApproveBashInSession,
                        onCheckedChange = onToggleAutoApprove
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5h & 7d Quota Bars
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "5-Hour Window", color = Color(0xFF8B949E), fontSize = 11.sp)
                    Text(
                        text = "$fiveHourUsedPct% used • ${usage.totalTokens} tokens ($${String.format("%.4f", usage.totalCostUsd)})",
                        color = Color(0xFFE6EDF3),
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                LinearProgressIndicator(
                    progress = { (fiveHourUsedPct / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = if (fiveHourUsedPct >= 90) Color(0xFFF85149) else Color(0xFFD97757),
                    trackColor = Color(0xFF0D1117)
                )
            }

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "7-Day Plan Window", color = Color(0xFF8B949E), fontSize = 11.sp)
                    Text(
                        text = "$sevenDayUsedPct% used",
                        color = Color(0xFFE6EDF3),
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                LinearProgressIndicator(
                    progress = { (sevenDayUsedPct / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = if (sevenDayUsedPct >= 90) Color(0xFFF85149) else Color(0xFF58A6FF),
                    trackColor = Color(0xFF0D1117)
                )
            }
        }
    }
}
