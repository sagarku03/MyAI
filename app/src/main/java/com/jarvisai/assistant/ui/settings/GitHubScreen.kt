package com.jarvisai.assistant.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.data.github.GitHubManager
import com.jarvisai.assistant.ui.components.GitCloneDialog
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun GitHubScreen(
    onProjectCloned: (File) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by GitHubManager.state.collectAsState()

    var patInput by remember { mutableStateOf("") }
    var gitName by remember { mutableStateOf("") }
    var gitEmail by remember { mutableStateOf("") }
    var repoFilter by remember { mutableStateOf("") }
    var cloneDialogUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        GitHubManager.init(context)
        GitHubManager.refresh()
    }

    LaunchedEffect(state.gitUserName, state.gitUserEmail) {
        if (gitName.isBlank() && state.gitUserName.isNotBlank()) gitName = state.gitUserName
        if (gitEmail.isBlank() && state.gitUserEmail.isNotBlank()) gitEmail = state.gitUserEmail
    }

    LaunchedEffect(state.signedIn) {
        if (state.signedIn && !state.reposLoaded && !state.reposLoading) {
            GitHubManager.loadRepos()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
            border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Code, contentDescription = null, tint = AgySettingsColors.GeminiBlue, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                state.checking -> "Checking GitHub CLI..."
                                state.ghInstalled == false -> "GitHub CLI (gh) not installed"
                                state.signedIn -> "Signed in as @${state.username.ifBlank { "GitHub User" }}"
                                else -> "GitHub CLI Ready · Not Signed In"
                            },
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgySettingsColors.TextPrimary
                        )
                        if (state.ghVersion.isNotBlank()) {
                            Text(state.ghVersion, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, color = AgySettingsColors.TextSecondary)
                        }
                    }
                    IconButton(
                        onClick = { scope.launch { GitHubManager.refresh() } },
                        modifier = Modifier.testTag("github_refresh_btn")
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh GitHub status", tint = AgySettingsColors.TextSecondary)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { cloneDialogUrl = "" },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("github_clone_any_btn")
                    ) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clone URL", fontSize = 12.sp, color = AgySettingsColors.TextPrimary)
                    }
                    if (state.signedIn) {
                        OutlinedButton(
                            onClick = { scope.launch { GitHubManager.signOut() } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sign Out", fontSize = 12.sp, color = Color(0xFFF87171))
                        }
                    }
                }
            }
        }

        state.message?.let { msg ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = (if (state.isError) Color(0xFFF87171) else AgySettingsColors.QuotaGreen).copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = msg,
                        fontSize = 12.sp,
                        color = if (state.isError) Color(0xFFF87171) else AgySettingsColors.QuotaGreen,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { GitHubManager.clearMessage() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss message", tint = AgySettingsColors.TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (state.ghInstalled == false) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
                border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Install GitHub CLI", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                    Text(
                        "Install `gh` and `git` into Agentra's embedded Linux environment to browse private repos, sync git auth, and clone with one tap.",
                        fontSize = 12.sp,
                        color = AgySettingsColors.TextSecondary
                    )
                    Button(
                        onClick = { scope.launch { GitHubManager.installGhCli() } },
                        enabled = !state.busyAction,
                        colors = ButtonDefaults.buttonColors(containerColor = AgySettingsColors.GeminiBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_install_gh_btn")
                    ) {
                        Text(if (state.busyAction) "Installing..." else "Install gh CLI")
                    }
                }
            }
        } else if (state.ghInstalled == true && !state.signedIn) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
                border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sign In with GitHub Token (PAT)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                    Text(
                        "Paste a GitHub Personal Access Token (classic `repo, read:org` or fine-grained). Stored directly in `gh` CLI config inside Agentra's isolated home.",
                        fontSize = 12.sp,
                        color = AgySettingsColors.TextSecondary
                    )
                    OutlinedTextField(
                        value = patInput,
                        onValueChange = { patInput = it },
                        label = { Text("ghp_... / github_pat_...") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AgySettingsColors.TextPrimary,
                            unfocusedTextColor = AgySettingsColors.TextPrimary,
                            focusedBorderColor = AgySettingsColors.GeminiBlue,
                            unfocusedBorderColor = AgySettingsColors.CardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_pat_input")
                    )
                    Button(
                        onClick = {
                            val t = patInput
                            patInput = ""
                            scope.launch { GitHubManager.signInWithToken(t) }
                        },
                        enabled = !state.busyAction && patInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AgySettingsColors.QuotaGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_signin_btn")
                    ) {
                        Text(if (state.busyAction) "Signing in..." else "Sign In & Configure Git")
                    }
                }
            }
        }

        // Git Commit Identity Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
            border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Git Commit Identity", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary)
                OutlinedTextField(
                    value = gitName,
                    onValueChange = { gitName = it },
                    label = { Text("git user.name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AgySettingsColors.TextPrimary,
                        unfocusedTextColor = AgySettingsColors.TextPrimary,
                        focusedBorderColor = AgySettingsColors.GeminiBlue,
                        unfocusedBorderColor = AgySettingsColors.CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = gitEmail,
                    onValueChange = { gitEmail = it },
                    label = { Text("git user.email") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AgySettingsColors.TextPrimary,
                        unfocusedTextColor = AgySettingsColors.TextPrimary,
                        focusedBorderColor = AgySettingsColors.GeminiBlue,
                        unfocusedBorderColor = AgySettingsColors.CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { scope.launch { GitHubManager.saveGitIdentity(gitName, gitEmail) } },
                    enabled = !state.busyAction && (gitName.isNotBlank() || gitEmail.isNotBlank()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_save_identity_btn")
                ) {
                    Text("Save Git Identity", color = AgySettingsColors.TextPrimary)
                }
            }
        }

        // Repositories Browser
        if (state.signedIn) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AgySettingsColors.CardBg),
                border = BorderStroke(1.dp, AgySettingsColors.CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Your Repositories (${state.repos.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AgySettingsColors.TextPrimary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { scope.launch { GitHubManager.loadRepos() } }) {
                            Text("Reload", color = AgySettingsColors.GeminiBlue, fontSize = 12.sp)
                        }
                    }

                    OutlinedTextField(
                        value = repoFilter,
                        onValueChange = { repoFilter = it },
                        placeholder = { Text("Filter repositories...", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AgySettingsColors.TextPrimary,
                            unfocusedTextColor = AgySettingsColors.TextPrimary,
                            focusedBorderColor = AgySettingsColors.GeminiBlue,
                            unfocusedBorderColor = AgySettingsColors.CardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (state.reposLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Loading repositories...", color = AgySettingsColors.TextSecondary, fontSize = 12.sp)
                        }
                    } else {
                        val visible = state.repos.filter {
                            repoFilter.isBlank() ||
                                it.fullName.contains(repoFilter, ignoreCase = true) ||
                                it.description.contains(repoFilter, ignoreCase = true)
                        }
                        if (visible.isEmpty()) {
                            Text("No repositories found.", color = AgySettingsColors.TextSecondary, fontSize = 12.sp)
                        } else {
                            visible.forEachIndexed { idx, repo ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (repo.isPrivate) Icons.Outlined.Lock else Icons.Outlined.Public,
                                        contentDescription = if (repo.isPrivate) "Private" else "Public",
                                        tint = if (repo.isPrivate) AgySettingsColors.Warning else AgySettingsColors.QuotaGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(repo.fullName, color = AgySettingsColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (repo.description.isNotBlank()) {
                                            Text(repo.description, color = AgySettingsColors.TextSecondary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    TextButton(onClick = { cloneDialogUrl = "${repo.url}.git" }) {
                                        Text("Clone", color = AgySettingsColors.GeminiBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (idx < visible.lastIndex) HorizontalDivider(color = AgySettingsColors.CardBorder)
                            }
                        }
                    }
                }
            }
        }
    }

    cloneDialogUrl?.let { url ->
        GitCloneDialog(
            initialUrl = url,
            onCloned = { dir ->
                cloneDialogUrl = null
                Toast.makeText(context, "Cloned ${dir.name}", Toast.LENGTH_SHORT).show()
                onProjectCloned(dir)
            },
            onDismiss = { cloneDialogUrl = null }
        )
    }
}
