package com.jarvisai.assistant.ui.agents

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.agent.AgentKind

/**
 * Phase 3: Top-level polymorphic Agent Selector Deck allowing real-time switching between
 * Antigravity (`AGY`), Claude Code (`CLAUDE`), OpenCode (`OPENCODE`), and OpenAI Codex (`CODEX`).
 */
@Composable
fun AgentPicker(
    selectedAgent: AgentKind,
    busyAgents: Set<AgentKind> = emptySet(),
    onSelectAgent: (AgentKind) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("agent_picker_deck"),
        color = Color(0xFF0D1117),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MULTI-AGENT ENGINE",
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = selectedAgent.subtitle,
                    color = Color(selectedAgent.accentColorHex),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AgentKind.entries.forEach { agent ->
                    val isSelected = agent == selectedAgent
                    val isBusy = busyAgents.contains(agent)
                    val accent = Color(agent.accentColorHex)

                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) accent.copy(alpha = 0.18f) else Color(0xFF161B22),
                        label = "agentChipBg"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) accent else Color(0xFF30363D),
                        label = "agentChipBorder"
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgColor)
                            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable { onSelectAgent(agent) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("agent_chip_${agent.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isBusy) Color(0xFF3FB950) else accent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = agent.displayName,
                            color = if (isSelected) Color(0xFFF0F6FC) else Color(0xFF8B949E),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
