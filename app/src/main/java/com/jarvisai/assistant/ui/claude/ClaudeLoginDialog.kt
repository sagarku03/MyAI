package com.jarvisai.assistant.ui.claude

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jarvisai.assistant.agent.claude.ClaudeCliStatus
import com.jarvisai.assistant.agent.claude.ClaudeLoginState

/**
 * Phase 3: Authentication & API Key / OAuth Login Dialog for Claude Code (`ClaudeLoginDialog`).
 */
@Composable
fun ClaudeLoginDialog(
    cliStatus: ClaudeCliStatus,
    loginState: ClaudeLoginState,
    onRefreshStatus: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    var apiKey by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("claude_login_dialog"),
            color = Color(0xFF161B22),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Claude Code Authentication",
                    color = Color(0xFFF0F6FC),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Connect via `~/.claude/.credentials.json` OAuth or Anthropic API Key.",
                    color = Color(0xFF8B949E),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D1117), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFF30363D), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    val statusSummary = when (loginState) {
                        is ClaudeLoginState.LoggedIn -> "Authenticated: ${loginState.email} (${loginState.plan})"
                        is ClaudeLoginState.AwaitingOAuthCallback -> "Awaiting OAuth: ${loginState.authUrl}"
                        is ClaudeLoginState.Checking -> "Checking ~/.claude credentials..."
                        is ClaudeLoginState.Error -> "Auth Error: ${loginState.message}"
                        ClaudeLoginState.LoggedOut -> "Status: Logged Out"
                    }
                    Text(
                        text = statusSummary,
                        color = Color(0xFFE6EDF3),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "CLI Installed: ${if (cliStatus.installed) "Yes (${cliStatus.version ?: "latest"})" else "Detecting..."}",
                        color = Color(0xFF8B949E),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Anthropic API Key (sk-ant-...)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("claude_api_key_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRefreshStatus,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Check OAuth", color = Color(0xFF58A6FF), fontSize = 12.sp)
                    }
                    if (loginState is ClaudeLoginState.LoggedIn) {
                        OutlinedButton(
                            onClick = onLogout,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Logout", color = Color(0xFFF85149), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close", color = Color(0xFF8B949E))
                    }
                    Button(
                        onClick = {
                            if (apiKey.isNotBlank()) {
                                onSaveApiKey(apiKey.trim())
                                apiKey = ""
                            }
                        },
                        enabled = apiKey.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97757)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("claude_save_api_key_button")
                    ) {
                        Text("Save Key", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
