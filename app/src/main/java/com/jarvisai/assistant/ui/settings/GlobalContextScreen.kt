package com.jarvisai.assistant.ui.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import java.io.File

enum class GlobalContextProfile(val fileName: String, val label: String, val relativePath: String) {
    CLAUDE_MD("CLAUDE.md", "Claude Code (CLAUDE.md)", ".claude/CLAUDE.md"),
    AGENTS_MD("AGENTS.md", "Codex / OpenCode (AGENTS.md)", ".config/opencode/AGENTS.md"),
    GEMINI_MD("GEMINI.md", "Antigravity (GEMINI.md)", ".gemini/jarvis-antigravity/GEMINI.md")
}

object GlobalContextRepository {
    private val DEFAULT_TEMPLATE = """
        # Global AI Engineering Constitution (CLAUDE.md / AGENTS.md)
        
        ## Core Principles
        - Always write complete, production-grade, strongly-typed code with zero placeholder comments (`// TODO`, `// ...`).
        - Prefer Jetpack Compose Material 3, Kotlin Coroutines/Flows, and clean modular architecture.
        - Render mathematical expressions using LaTeX (${'$'}${'$'} ... ${'$'}${'$'} for display blocks and ${'$'} ... ${'$'} for inline formulas).
        - Before destructive filesystem or git operations, verify current working tree status (`git status --short`).
        
        ## Verification Standards
        - Run unit tests and compiler checks after structural edits.
        - Ensure all interactive touch targets are at least 48dp x 48dp with clear accessibility semantics.
    """.trimIndent()

    fun resolveFile(context: Context, profile: GlobalContextProfile): File {
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        val file = File(homeDir, profile.relativePath)
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            runCatching { file.writeText(DEFAULT_TEMPLATE) }
        }
        return file
    }

    fun readProfile(context: Context, profile: GlobalContextProfile): String {
        return runCatching {
            resolveFile(context, profile).readText()
        }.getOrDefault(DEFAULT_TEMPLATE)
    }

    fun writeProfile(context: Context, profile: GlobalContextProfile, content: String, mirrorToAll: Boolean) {
        val targets = if (mirrorToAll) GlobalContextProfile.entries else listOf(profile)
        targets.forEach { target ->
            runCatching {
                resolveFile(context, target).writeText(content)
            }
        }
        context.getSharedPreferences("jarvis_global_context", Context.MODE_PRIVATE)
            .edit()
            .putString("global_system_instructions", content)
            .apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalContextScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var activeProfile by remember { mutableStateOf(GlobalContextProfile.CLAUDE_MD) }
    var editorText by remember { mutableStateOf("") }
    var mirrorAllAgents by remember { mutableStateOf(true) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeProfile) {
        editorText = GlobalContextRepository.readProfile(context, activeProfile)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Global Context & Rules Editor",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFFF8FAFC),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = GlobalContextRepository.resolveFile(context, activeProfile).absolutePath,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF38BDF8),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("global_context_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFF8FAFC)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0F19))
            )
        },
        containerColor = Color(0xFF0B0F19)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlobalContextProfile.entries.forEach { profile ->
                    val selected = activeProfile == profile
                    FilterChip(
                        selected = selected,
                        onClick = {
                            activeProfile = profile
                            statusBanner = null
                        },
                        label = {
                            Text(
                                text = profile.fileName,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("context_profile_${profile.name}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF6366F1).copy(alpha = 0.24f),
                            selectedLabelColor = Color(0xFF38BDF8),
                            containerColor = Color(0xFF111827),
                            labelColor = Color(0xFF94A3B8)
                        )
                    )
                }
            }

            OutlinedTextField(
                value = editorText,
                onValueChange = {
                    editorText = it
                    statusBanner = null
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("global_context_markdown_editor"),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFFF8FAFC),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF070A12),
                    unfocusedContainerColor = Color(0xFF070A12),
                    focusedBorderColor = Color(0xFF6366F1),
                    unfocusedBorderColor = Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(14.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = mirrorAllAgents,
                    onClick = { mirrorAllAgents = !mirrorAllAgents },
                    label = { Text("Mirror across CLAUDE.md, AGENTS.md & GEMINI.md") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }

            if (statusBanner != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f))
                ) {
                    Text(
                        text = statusBanner.orEmpty(),
                        style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF10B981)),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Button(
                onClick = {
                    GlobalContextRepository.writeProfile(
                        context = context,
                        profile = activeProfile,
                        content = editorText,
                        mirrorToAll = mirrorAllAgents
                    )
                    statusBanner = if (mirrorAllAgents) {
                        "Saved & synchronized across CLAUDE.md, AGENTS.md, and GEMINI.md"
                    } else {
                        "Saved to ${activeProfile.relativePath}"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("save_global_context_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Global System Context", fontWeight = FontWeight.Bold)
            }
        }
    }
}
