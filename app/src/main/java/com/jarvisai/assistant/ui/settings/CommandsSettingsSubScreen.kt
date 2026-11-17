package com.jarvisai.assistant.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.agent.ArtifactReviewMode
import com.jarvisai.assistant.agent.CommandExecutionPolicy
import com.jarvisai.assistant.agent.CommandsPermissionManager
import com.jarvisai.assistant.agent.FileAccessPolicy
import com.jarvisai.assistant.agent.SecurityPreset
import com.jarvisai.assistant.ui.components.bouncyOverscroll

@Composable
fun CommandsSettingsSubScreen() {
    val context = LocalContext.current
    val policy by CommandsPermissionManager.state.collectAsState()
    var showAddRuleDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        CommandsPermissionManager.load(context)
    }

    val preset = policy.activePreset
    var customExpanded by remember(preset) { mutableStateOf(preset == SecurityPreset.CUSTOM) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .bouncyOverscroll()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text("Global Security & Policies", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text("Live policies enforced across chat tools, shell PTY & workspaces", fontSize = 11.5.sp, color = AgySettingsColors.TextSecondary)
        }

        // 1. Artifact Review Policy
        Column {
            Text("Artifact Review Policy", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AgySettingsColors.TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text("Controls whether the assistant asks before creating plans or editing artifacts.", fontSize = 11.5.sp, color = AgySettingsColors.TextSecondary)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val isAuto = policy.artifactReviewMode == ArtifactReviewMode.TURBO
            val isAlways = !isAuto

            Card(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { CommandsPermissionManager.setArtifactReviewMode(context, ArtifactReviewMode.TURBO) }
                    .testTag("policy_artifact_turbo"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (isAuto) AgySettingsColors.Terracotta.copy(alpha = 0.1f) else AgySettingsColors.CardBg),
                border = BorderStroke(if (isAuto) 1.5.dp else 1.dp, if (isAuto) AgySettingsColors.Terracotta else AgySettingsColors.CardBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡ Auto (Turbo)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isAuto) AgySettingsColors.Terracotta else AgySettingsColors.TextPrimary)
                        RadioButton(
                            selected = isAuto,
                            onClick = { CommandsPermissionManager.setArtifactReviewMode(context, ArtifactReviewMode.TURBO) },
                            colors = RadioButtonDefaults.colors(selectedColor = AgySettingsColors.Terracotta),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Edits & creates files immediately without blocking.", fontSize = 11.sp, color = AgySettingsColors.TextSecondary, lineHeight = 15.sp)
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { CommandsPermissionManager.setArtifactReviewMode(context, ArtifactReviewMode.ALWAYS) }
                    .testTag("policy_artifact_always"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (isAlways) AgySettingsColors.Terracotta.copy(alpha = 0.1f) else AgySettingsColors.CardBg),
                border = BorderStroke(if (isAlways) 1.5.dp else 1.dp, if (isAlways) AgySettingsColors.Terracotta else AgySettingsColors.CardBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("✋ Always Ask", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isAlways) AgySettingsColors.Terracotta else AgySettingsColors.TextPrimary)
                        RadioButton(
                            selected = isAlways,
                            onClick = { CommandsPermissionManager.setArtifactReviewMode(context, ArtifactReviewMode.ALWAYS) },
                            colors = RadioButtonDefaults.colors(selectedColor = AgySettingsColors.Terracotta),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Pause and ask for confirmation before changes.", fontSize = 11.sp, color = AgySettingsColors.TextSecondary, lineHeight = 15.sp)
                }
            }
        }

        // 2. Security Presets
        Text("Execution & Security Presets", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AgySettingsColors.TextPrimary)

        SecurityPresetCard(
            title = "🛡️ Default Mode (Safe & Guided)",
            subtitle = "Asks before running shell commands and before accessing files outside active workspace.",
            selected = preset == SecurityPreset.DEFAULT,
            onClick = { CommandsPermissionManager.applyPreset(context, SecurityPreset.DEFAULT) },
            testTag = "security_preset_default"
        )

        SecurityPresetCard(
            title = "💻 Full Machine Mode",
            subtitle = "Unrestricted file reading outside workspace, while still confirming shell commands.",
            selected = preset == SecurityPreset.FULL_MACHINE,
            onClick = { CommandsPermissionManager.applyPreset(context, SecurityPreset.FULL_MACHINE) },
            testTag = "security_preset_machine"
        )

        SecurityPresetCard(
            title = "⚡ Turbo Mode (Full Autonomy)",
            subtitle = "Auto-runs terminal commands and workspace operations without prompting (except deny rules).",
            selected = preset == SecurityPreset.TURBO,
            onClick = { CommandsPermissionManager.applyPreset(context, SecurityPreset.TURBO) },
            testTag = "security_preset_turbo"
        )

        // 3. Custom Granular Mode
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = if (preset == SecurityPreset.CUSTOM) AgySettingsColors.AccentTeal.copy(alpha = 0.08f) else AgySettingsColors.CardBg),
            border = BorderStroke(1.dp, if (preset == SecurityPreset.CUSTOM) AgySettingsColors.AccentTeal else AgySettingsColors.CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { customExpanded = !customExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("⚙️ Custom Granular Controls", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                        Text("Command Execution: ${policy.commandExecutionPolicy.label} · Files: ${policy.nonWorkspaceFileAccess.label}", fontSize = 11.sp, color = AgySettingsColors.TextSecondary)
                    }
                    Icon(if (customExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = AgySettingsColors.TextSecondary)
                }

                AnimatedVisibility(visible = customExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Terminal Execution Policy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AgySettingsColors.TextSecondary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CommandExecutionPolicy.entries.forEach { p ->
                                FilterChip(
                                    selected = policy.commandExecutionPolicy == p,
                                    onClick = { CommandsPermissionManager.setCommandExecutionPolicy(context, p) },
                                    label = { Text(p.label, fontSize = 11.5.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AgySettingsColors.Terracotta.copy(alpha = 0.2f),
                                        selectedLabelColor = AgySettingsColors.Terracotta
                                    )
                                )
                            }
                        }

                        Text("Non-Workspace File Access", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AgySettingsColors.TextSecondary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FileAccessPolicy.entries.forEach { fa ->
                                FilterChip(
                                    selected = policy.nonWorkspaceFileAccess == fa,
                                    onClick = { CommandsPermissionManager.setNonWorkspaceFileAccess(context, fa) },
                                    label = { Text(fa.label, fontSize = 11.5.sp) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Terminal Sandbox Isolation", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AgySettingsColors.TextPrimary)
                                Text("Restrict shell scripts from touching protected system directories.", fontSize = 11.sp, color = AgySettingsColors.TextSecondary)
                            }
                            Switch(
                                checked = policy.enableTerminalSandbox,
                                onCheckedChange = { CommandsPermissionManager.setTerminalSandbox(context, it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AgySettingsColors.QuotaGreen)
                            )
                        }
                    }
                }
            }
        }

        // 4. Granular Permission Grant Rules (allow / deny / ask)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
            border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = AgySettingsColors.Warning, modifier = Modifier.size(18.dp))
                        Text("Command Rules (${policy.allRules.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                    }
                    OutlinedButton(
                        onClick = { showAddRuleDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AgySettingsColors.CardBorder),
                        modifier = Modifier.heightIn(min = 38.dp).testTag("add_permission_rule_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Rule", fontSize = 12.sp)
                    }
                }

                policy.allRules.forEach { rule ->
                    val badgeColor = when (rule.decision) {
                        "ALLOW" -> AgySettingsColors.QuotaGreen
                        "DENY" -> AgySettingsColors.Danger
                        else -> AgySettingsColors.Warning
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AgySettingsColors.CardBgElevated,
                        border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(shape = RoundedCornerShape(6.dp), color = badgeColor.copy(alpha = 0.16f)) {
                                Text(rule.decision, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = badgeColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(rule.rawRule, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AgySettingsColors.TextPrimary, modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { CommandsPermissionManager.removeRule(context, rule.rawRule) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove rule", tint = AgySettingsColors.TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showAddRuleDialog) {
        var pattern by remember { mutableStateOf("") }
        var decision by remember { mutableStateOf("ALLOW") }
        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            containerColor = AgySettingsColors.CardBg,
            title = { Text("Add Command Rule", color = AgySettingsColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it },
                        label = { Text("Command wildcard (e.g. git *, npm test)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgySettingsColors.Terracotta,
                            unfocusedBorderColor = AgySettingsColors.CardBorder,
                            focusedTextColor = AgySettingsColors.TextPrimary,
                            unfocusedTextColor = AgySettingsColors.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ALLOW", "ASK", "DENY").forEach { d ->
                            FilterChip(
                                selected = decision == d,
                                onClick = { decision = d },
                                label = { Text(d, fontSize = 11.5.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        CommandsPermissionManager.addOrUpdateRule(context, "bash", pattern, decision)
                        showAddRuleDialog = false
                    },
                    enabled = pattern.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AgySettingsColors.Terracotta)
                ) { Text("Save Rule") }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialog = false }) { Text("Cancel", color = AgySettingsColors.TextSecondary) }
            }
        )
    }
}

@Composable
private fun SecurityPresetCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) AgySettingsColors.Terracotta.copy(alpha = 0.09f) else AgySettingsColors.CardBg),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) AgySettingsColors.Terracotta else AgySettingsColors.CardBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = if (selected) AgySettingsColors.Terracotta else AgySettingsColors.TextPrimary)
                Text(subtitle, fontSize = 11.5.sp, color = AgySettingsColors.TextSecondary, lineHeight = 16.sp)
            }
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = AgySettingsColors.Terracotta)
            )
        }
    }
}
