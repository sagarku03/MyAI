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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.jarvisai.assistant.ui.components.bouncyOverscroll

data class ReaTriggerRule(
    val id: String,
    val name: String,
    val eventPattern: String,
    val actionPrompt: String,
    val groupId: String,
    val enabled: Boolean = true
)

private val DEFAULT_REA_RULES = listOf(
    ReaTriggerRule(
        id = "rea_git_commit",
        name = "Pre-Commit Static Security Audit",
        eventPattern = "git:pre-commit",
        actionPrompt = "Run `/audit` on staged files and block commit if high-severity secrets or race conditions are detected.",
        groupId = "DevSecOps",
        enabled = true
    ),
    ReaTriggerRule(
        id = "rea_build_fail",
        name = "Autonomous Gradle Failure Diagnosis",
        eventPattern = "ide:build-failed",
        actionPrompt = "Capture compiler stderr tail, resolve missing symbol/import, and propose inline patch.",
        groupId = "CI/CD",
        enabled = true
    ),
    ReaTriggerRule(
        id = "rea_battery_thermal",
        name = "Thermal Throttling Model Switch",
        eventPattern = "device:temp>43C",
        actionPrompt = "Switch local LLM threads to 4 and route heavy reasoning tasks to Claude Haiku / Gemini Flash.",
        groupId = "Hardware",
        enabled = true
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaScreen(
    onBack: () -> Unit,
    onOpenGroups: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("jarvis_rea_engine", Context.MODE_PRIVATE) }
    val rules = remember { mutableStateListOf<ReaTriggerRule>() }
    var newName by remember { mutableStateOf("") }
    var newPattern by remember { mutableStateOf("") }
    var newAction by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        rules.clear()
        rules.addAll(
            DEFAULT_REA_RULES.map { r ->
                r.copy(enabled = prefs.getBoolean("rea_enabled_${r.id}", r.enabled))
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "REA (Reactive Event Automation) Engine",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFFF8FAFC),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Event-Driven Autonomous Agent Triggers & Watchdogs",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8))
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("rea_screen_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFF8FAFC)
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = onOpenGroups,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .heightIn(min = 48.dp)
                            .testTag("rea_open_groups_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Icon(Icons.Default.GroupWork, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("REA Groups", fontSize = 12.sp)
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
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "New Reactive Event Rule",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = Color(0xFFF8FAFC),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Rule Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color(0xFFF8FAFC),
                                unfocusedTextColor = Color(0xFFF8FAFC)
                            )
                        )
                        OutlinedTextField(
                            value = newPattern,
                            onValueChange = { newPattern = it },
                            label = { Text("Event Trigger Pattern (e.g. fs:*.kt, webhook:github)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color(0xFFF8FAFC),
                                unfocusedTextColor = Color(0xFFF8FAFC)
                            )
                        )
                        OutlinedTextField(
                            value = newAction,
                            onValueChange = { newAction = it },
                            label = { Text("Autonomous Agent Action Prompt") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color(0xFFF8FAFC),
                                unfocusedTextColor = Color(0xFFF8FAFC)
                            )
                        )
                        Button(
                            onClick = {
                                if (newName.isNotBlank() && newPattern.isNotBlank() && newAction.isNotBlank()) {
                                    rules.add(
                                        0,
                                        ReaTriggerRule(
                                            id = "custom_${System.currentTimeMillis()}",
                                            name = newName.trim(),
                                            eventPattern = newPattern.trim(),
                                            actionPrompt = newAction.trim(),
                                            groupId = "Custom"
                                        )
                                    )
                                    newName = ""
                                    newPattern = ""
                                    newAction = ""
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("add_rea_rule_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Register Reactive Rule", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            items(rules, key = { it.id }) { rule ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF111827),
                    border = BorderStroke(
                        1.dp,
                        if (rule.enabled) Color(0xFF38BDF8).copy(alpha = 0.45f) else Color(0xFF1E293B)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = rule.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color(0xFFF8FAFC),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = "Trigger: ${rule.eventPattern} • Group: ${rule.groupId}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF38BDF8),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                }
                            }

                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { checked ->
                                    val idx = rules.indexOfFirst { it.id == rule.id }
                                    if (idx >= 0) {
                                        rules[idx] = rule.copy(enabled = checked)
                                        prefs.edit().putBoolean("rea_enabled_${rule.id}", checked).apply()
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF10B981)
                                )
                            )
                        }

                        Text(
                            text = rule.actionPrompt,
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                        )
                    }
                }
            }
        }
    }
}
