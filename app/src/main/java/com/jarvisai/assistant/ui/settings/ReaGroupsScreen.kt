package com.jarvisai.assistant.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jarvisai.assistant.ui.components.bouncyOverscroll

data class ReaGroupSpec(
    val id: String,
    val title: String,
    val description: String,
    val concurrencyLimit: Int
)

private val BUILTIN_REA_GROUPS = listOf(
    ReaGroupSpec(
        id = "DevSecOps",
        title = "DevSecOps & Git Watchdogs",
        description = "Pre-commit secret scanning, dependency vulnerability checks, and branch policies.",
        concurrencyLimit = 2
    ),
    ReaGroupSpec(
        id = "CI/CD",
        title = "Autonomous Build & Test Runners",
        description = "Automatic compiler diagnostic capture, unit test execution, and lint auto-fixing.",
        concurrencyLimit = 1
    ),
    ReaGroupSpec(
        id = "Hardware",
        title = "Device Telemetry & Battery Guards",
        description = "Thermal throttling adaptation, wake-lock governance, and network fallback routing.",
        concurrencyLimit = 3
    ),
    ReaGroupSpec(
        id = "BrowserSwarm",
        title = "CDP Browser Research Swarm",
        description = "Scheduled documentation scraping and multi-tab synthesis.",
        concurrencyLimit = 2
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaGroupsScreen(
    onBack: () -> Unit
) {
    val activeGroups = remember {
        mutableStateMapOf(
            "DevSecOps" to true,
            "CI/CD" to true,
            "Hardware" to true,
            "BrowserSwarm" to false
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "REA Execution Groups",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFFF8FAFC),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Isolate concurrency & resource budgets per automation group",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8))
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("rea_groups_back")
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .bouncyOverscroll(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(BUILTIN_REA_GROUPS, key = { it.id }) { group ->
                val enabled = activeGroups[group.id] ?: true
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF111827),
                    border = BorderStroke(
                        1.dp,
                        if (enabled) Color(0xFF6366F1).copy(alpha = 0.5f) else Color(0xFF1E293B)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GroupWork,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = group.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = Color(0xFFF8FAFC),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Text(
                                text = group.description,
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                            )
                            Text(
                                text = "Max Parallel Agents: ${group.concurrencyLimit}",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF10B981))
                            )
                        }

                        Switch(
                            checked = enabled,
                            onCheckedChange = { activeGroups[group.id] = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF6366F1)
                            )
                        )
                    }
                }
            }
        }
    }
}
