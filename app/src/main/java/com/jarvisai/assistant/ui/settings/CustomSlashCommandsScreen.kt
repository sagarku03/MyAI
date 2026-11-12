package com.jarvisai.assistant.ui.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateListOf
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
import com.jarvisai.assistant.ui.components.bouncyOverscroll
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SlashCommandMacro(
    val command: String,
    val description: String,
    val template: String,
    val targetAgents: List<String> = listOf("all")
)

object SlashCommandStore {
    private const val PREFS_NAME = "jarvis_slash_commands"
    private const val KEY_MACROS = "macros_json"

    val DEFAULT_MACROS = listOf(
        SlashCommandMacro(
            command = "/audit",
            description = "Deep security, concurrency & memory leak audit on active git diff",
            template = "Perform a comprehensive architectural, thread-safety, and security audit on the current git diff (`git diff HEAD`). Highlight race conditions, nullability hazards, and suggest zero-regression fixes."
        ),
        SlashCommandMacro(
            command = "/refactor",
            description = "Clean Architecture & SOLID refactoring pass with zero behavior change",
            template = "Refactor the selected module for clean separation of concerns, strict Kotlin idiomatic types, and minimal recomposition overhead while preserving 100% of existing public API contracts."
        ),
        SlashCommandMacro(
            command = "/test-gen",
            description = "Generate deterministic JUnit + Robolectric unit tests for edge cases",
            template = "Write complete, self-contained JUnit4 and Robolectric unit tests covering happy paths, boundary values, and error recovery states for the modified classes."
        ),
        SlashCommandMacro(
            command = "/latex-proof",
            description = "Derive formal mathematical proof with step-by-step LaTeX equations",
            template = "Provide a rigorous step-by-step mathematical derivation using display LaTeX (\\$\\$ ... \\$\\$) and inline (\\$ ... \\$) notation, verifying dimensional consistency and asymptotic bounds."
        ),
        SlashCommandMacro(
            command = "/fix-build",
            description = "Run Gradle compile check, diagnose compiler errors & patch automatically",
            template = "Inspect the latest Gradle compiler diagnostics, identify the root symbol or type mismatch, apply the exact fix, and verify clean compilation."
        )
    )

    fun loadMacros(context: Context): List<SlashCommandMacro> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_MACROS, null) ?: return DEFAULT_MACROS
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        SlashCommandMacro(
                            command = obj.optString("command"),
                            description = obj.optString("description"),
                            template = obj.optString("template")
                        )
                    )
                }
            }.ifEmpty { DEFAULT_MACROS }
        }.getOrDefault(DEFAULT_MACROS)
    }

    fun saveMacros(context: Context, macros: List<SlashCommandMacro>) {
        val arr = JSONArray()
        macros.forEach { macro ->
            arr.put(
                JSONObject().apply {
                    put("command", macro.command)
                    put("description", macro.description)
                    put("template", macro.template)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MACROS, arr.toString())
            .apply()

        // Also sync to ~/.claude/commands/ inside Termux home so Claude Code CLI discovers them natively
        runCatching {
            val homeDir = LocalEnvironmentManager.getHomeDir(context)
            val claudeCmdDir = File(homeDir, ".claude/commands").apply { mkdirs() }
            macros.forEach { macro ->
                val slug = macro.command.removePrefix("/").replace(Regex("[^a-zA-Z0-9_-]"), "_")
                if (slug.isNotBlank()) {
                    File(claudeCmdDir, "$slug.md").writeText(
                        "# ${macro.command} - ${macro.description}\n\n${macro.template}\n"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSlashCommandsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val macros = remember { mutableStateListOf<SlashCommandMacro>() }
    var newCommand by remember { mutableStateOf("/") }
    var newDescription by remember { mutableStateOf("") }
    var newTemplate by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        macros.clear()
        macros.addAll(SlashCommandStore.loadMacros(context))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Custom Slash Commands",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFFF8FAFC),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Synced to Chat Bar & ~/.claude/commands/*.md",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8))
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("slash_commands_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFF8FAFC)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            macros.clear()
                            macros.addAll(SlashCommandStore.DEFAULT_MACROS)
                            SlashCommandStore.saveMacros(context, macros)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("slash_commands_restore_defaults")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = "Restore Default Slash Commands",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0F19))
            )
        },
        containerColor = Color(0xFF0B0F19)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .bouncyOverscroll(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF111827),
                    border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Create New Slash Macro",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = Color(0xFFF8FAFC),
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newCommand,
                                onValueChange = {
                                    newCommand = if (it.startsWith("/")) it else "/$it"
                                },
                                label = { Text("Command (e.g. /deploy)") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.42f)
                                    .testTag("slash_command_name_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color(0xFFF8FAFC),
                                    unfocusedTextColor = Color(0xFFF8FAFC)
                                )
                            )

                            OutlinedTextField(
                                value = newDescription,
                                onValueChange = { newDescription = it },
                                label = { Text("Short summary") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.58f)
                                    .testTag("slash_command_desc_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color(0xFFF8FAFC),
                                    unfocusedTextColor = Color(0xFFF8FAFC)
                                )
                            )
                        }

                        OutlinedTextField(
                            value = newTemplate,
                            onValueChange = { newTemplate = it },
                            label = { Text("Prompt expansion template (supports {{ARGUMENTS}})") },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("slash_command_template_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color(0xFFF8FAFC),
                                unfocusedTextColor = Color(0xFFF8FAFC)
                            )
                        )

                        Button(
                            onClick = {
                                val cmd = newCommand.trim()
                                val tpl = newTemplate.trim()
                                if (cmd.length > 1 && tpl.isNotEmpty()) {
                                    macros.removeAll { it.command.equals(cmd, ignoreCase = true) }
                                    macros.add(
                                        0,
                                        SlashCommandMacro(
                                            command = cmd,
                                            description = newDescription.trim().ifEmpty { "Custom prompt macro" },
                                            template = tpl
                                        )
                                    )
                                    SlashCommandStore.saveMacros(context, macros)
                                    newCommand = "/"
                                    newDescription = ""
                                    newTemplate = ""
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("add_slash_command_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6366F1),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save & Sync Slash Command", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            items(macros, key = { it.command }) { macro ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF111827),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = macro.command,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color(0xFF38BDF8),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = macro.description,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFF8FAFC),
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }

                            IconButton(
                                onClick = {
                                    macros.remove(macro)
                                    SlashCommandStore.saveMacros(context, macros)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("delete_slash_${macro.command}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete ${macro.command}",
                                    tint = Color(0xFFEF4444)
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF070A12),
                            border = BorderStroke(1.dp, Color(0xFF1E293B))
                        ) {
                            Text(
                                text = macro.template,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF94A3B8),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
