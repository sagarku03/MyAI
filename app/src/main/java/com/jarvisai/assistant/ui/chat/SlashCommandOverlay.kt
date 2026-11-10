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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Terminal
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

data class SlashCommandItem(
    val command: String,
    val description: String,
    val icon: ImageVector,
    val category: String = "Commands"
)

/**
 * Floating Slash Command Picker above input bar.
 * Matches AntiGem's autocomplete dropdown when typing `/`.
 */
@Composable
fun SlashCommandOverlay(
    currentInput: String,
    isVisible: Boolean,
    onSelectCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val commands = remember {
        listOf(
            SlashCommandItem("/clear", "Clear chat history and reset context", Icons.Default.DeleteSweep),
            SlashCommandItem("/model", "Switch active AI model or quota", Icons.Default.Psychology),
            SlashCommandItem("/terminal", "Open embedded Linux terminal session", Icons.Default.Terminal),
            SlashCommandItem("/ide", "Open Sora IDE editor in workspace", Icons.Default.Code),
            SlashCommandItem("/files", "Open Workspace File Explorer", Icons.Default.Folder),
            SlashCommandItem("/artifacts", "Open generated code & markdown artifacts drawer", Icons.Default.Description),
            SlashCommandItem("/skills", "List active agent tools and skills", Icons.Default.AutoAwesome),
            SlashCommandItem("/antigravity", "Manage Google Antigravity OAuth session", Icons.Default.Cloud),
            SlashCommandItem("/bubble", "Toggle floating overlay ChatHead", Icons.Default.PictureInPictureAlt),
            SlashCommandItem("/git", "Check git status and active branch", Icons.Default.Commit)
        )
    }

    val filtered = remember(currentInput) {
        if (!currentInput.startsWith("/")) emptyList()
        else {
            val query = currentInput.removePrefix("/").trim().lowercase()
            commands.filter { it.command.removePrefix("/").contains(query) }
        }
    }

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
                .heightIn(max = 220.dp),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF111827),
            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
            tonalElevation = 8.dp
        ) {
            LazyColumn(modifier = Modifier.padding(6.dp)) {
                items(filtered) { item ->
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
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.command,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
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
