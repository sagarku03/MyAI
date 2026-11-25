package com.jarvisai.assistant.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.ui.antigravity.AntigravityPalette

data class SlashCommandItem(
    val command: String,
    val description: String,
    val icon: ImageVector,
    val category: String = "Commands"
)

/**
 * Floating Slash Command Picker above input bar.
 * Includes full Official Google Antigravity 2.0 & `agy` CLI slash commands
 * (`/plan`, `/fast`, `/browser`, `/resume`, `/rewind`, `/workflow`, `/antigravity-guide`, `/logout`).
 */
@Composable
fun SlashCommandOverlay(
    currentInput: String,
    isVisible: Boolean,
    isAntigravityActive: Boolean = false,
    onSelectCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val commands = remember {
        listOf(
            SlashCommandItem("/plan", "Antigravity Planning Mode: draft task.md & implementation plan", Icons.AutoMirrored.Filled.Assignment, "Antigravity"),
            SlashCommandItem("/fast", "Switch to Fast Mode for direct localized execution", Icons.Default.Bolt, "Antigravity"),
            SlashCommandItem("/browser", "Delegate live UI inspection & automation to Browser Subagent", Icons.Default.Public, "Antigravity"),
            SlashCommandItem("/resume", "Open Antigravity Inbox & resume any previous thread", Icons.Default.History, "Antigravity"),
            SlashCommandItem("/rewind", "Roll back the last conversation turn & reset context", Icons.Default.Undo, "Antigravity"),
            SlashCommandItem("/workflow", "Manage .agents/rules, SKILL.md workflows & autonomy policy", Icons.Default.Gavel, "Antigravity"),
            SlashCommandItem("/antigravity-guide", "Built-in official Antigravity 2.0 & agy CLI guide", Icons.AutoMirrored.Filled.HelpOutline, "Antigravity"),
            SlashCommandItem("/logout", "Clear saved Antigravity OAuth session & keyring", Icons.AutoMirrored.Filled.Logout, "Antigravity"),
            SlashCommandItem("/clear", "Clear chat history and start a fresh thread", Icons.Default.DeleteSweep, "Session"),
            SlashCommandItem("/artifacts", "Open typed Plans, Tasks, Diffs & Code Artifacts Hub", Icons.Default.Description, "Workspace"),
            SlashCommandItem("/terminal", "Open embedded Linux terminal session", Icons.Default.Terminal, "Workspace"),
            SlashCommandItem("/ide", "Open Sora IDE editor in workspace", Icons.Default.Code, "Workspace"),
            SlashCommandItem("/files", "Open Workspace File Explorer", Icons.Default.Folder, "Workspace"),
            SlashCommandItem("/skills", "List active agent tools and skills", Icons.Default.AutoAwesome, "Workspace"),
            SlashCommandItem("/model", "Switch active AI model or quota", Icons.Default.Psychology, "Session"),
            SlashCommandItem("/antigravity", "Manage Google Antigravity OAuth session", Icons.Default.Cloud, "Session"),
            SlashCommandItem("/bubble", "Toggle floating overlay ChatHead", Icons.Default.PictureInPictureAlt, "Workspace"),
            SlashCommandItem("/git", "Check git status and active branch", Icons.Default.Commit, "Workspace")
        )
    }

    val filtered = remember(currentInput, isAntigravityActive) {
        if (!currentInput.startsWith("/")) emptyList()
        else {
            val query = currentInput.removePrefix("/").trim().lowercase()
            val matches = commands.filter {
                it.command.removePrefix("/").contains(query) ||
                    it.description.lowercase().contains(query)
            }
            if (isAntigravityActive) matches else matches.sortedBy { it.category == "Antigravity" && query.isEmpty() }
        }
    }

    val accentColor = if (isAntigravityActive) AntigravityPalette.ElectricBlue else Color(0xFF00E5FF)
    val surfaceColor = if (isAntigravityActive) AntigravityPalette.SurfaceCard else Color(0xFF111827)

    AnimatedVisibility(
        visible = isVisible && filtered.isNotEmpty(),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier.testTag("slash_command_overlay")
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .heightIn(max = 240.dp),
            shape = RoundedCornerShape(14.dp),
            color = surfaceColor,
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
            tonalElevation = 8.dp
        ) {
            LazyColumn(modifier = Modifier.padding(6.dp)) {
                items(filtered, key = { it.command }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCommand(item.command) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (item.category == "Antigravity") AntigravityPalette.ElectricBlue else accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.command,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                )
                                if (item.category == "Antigravity") {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = AntigravityPalette.ElectricBlue.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = "agy",
                                            color = AntigravityPalette.ElectricBlue,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = item.description,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
