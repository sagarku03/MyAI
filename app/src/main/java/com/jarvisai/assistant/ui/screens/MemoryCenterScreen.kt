package com.jarvisai.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisElevatedSurface
import com.example.ui.theme.JarvisGlassBorder
import com.example.ui.theme.JarvisGlassSurface
import com.example.ui.theme.JarvisNeonCyan
import com.example.ui.theme.JarvisNeonGreen
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet
import com.example.ui.theme.JarvisVioletBright
import com.example.ui.theme.StatusListeningEmerald
import com.example.ui.theme.StatusThinkingAmber
import com.jarvisai.assistant.data.local.EpisodicEventEntity
import com.jarvisai.assistant.data.local.MemoryEntity
import com.jarvisai.assistant.data.local.SemanticTripleEntity
import com.jarvisai.assistant.model.ActiveEntityFocusStack
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.CognitiveMemoryTab
import com.jarvisai.assistant.model.MemoryCategory
import com.jarvisai.assistant.model.ShortTermTurn
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.model.WorkingTaskPlanSnapshot
import com.jarvisai.assistant.model.WorkingTaskStepStatus
import com.jarvisai.assistant.ui.components.JarvisAIOrb
import com.jarvisai.assistant.ui.components.JarvisGlassPanel
import com.jarvisai.assistant.ui.components.JarvisModernDialog
import com.jarvisai.assistant.ui.components.JarvisPremiumTopBar
import com.jarvisai.assistant.ui.components.JarvisSearchBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryCenterScreen(
    settings: UserSettings,
    status: AssistantStatus,
    memories: List<MemoryEntity>,
    totalMemoriesCount: Int,
    searchQuery: String,
    selectedCategory: MemoryCategory?,
    onBack: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (MemoryCategory?) -> Unit,
    onToggleMemoryEnabled: (Boolean) -> Unit,
    onSaveMemory: (title: String, content: String, category: MemoryCategory, tags: String, isPinned: Boolean) -> Unit,
    onUpdateMemory: (MemoryEntity) -> Unit,
    onTogglePinMemory: (MemoryEntity) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onClearAllMemories: () -> Unit,
    // 5-Tier Cognitive Memory Extensions (defaulted for backward compatibility)
    selectedCognitiveTab: CognitiveMemoryTab = CognitiveMemoryTab.LTM_VAULT,
    onSelectCognitiveTab: (CognitiveMemoryTab) -> Unit = {},
    stmTurns: List<ShortTermTurn> = emptyList(),
    entityFocusStack: ActiveEntityFocusStack = ActiveEntityFocusStack(),
    workingMemoryPlan: WorkingTaskPlanSnapshot = WorkingTaskPlanSnapshot(),
    episodicEvents: List<EpisodicEventEntity> = emptyList(),
    semanticTriples: List<SemanticTripleEntity> = emptyList(),
    onClearShortTermMemory: () -> Unit = {},
    onAddWorkingMemoryStep: (String, String) -> Unit = { _, _ -> },
    onCompleteWorkingMemoryStep: (Int) -> Unit = {},
    onPutWorkingScratchpad: (String, String) -> Unit = { _, _ -> },
    onRemoveWorkingScratchpad: (String) -> Unit = {},
    onClearWorkingMemory: () -> Unit = {},
    onAddEpisodicEvent: (String, String, String, Int) -> Unit = { _, _, _, _ -> },
    onDeleteEpisodicEvent: (Long) -> Unit = {},
    onClearEpisodicEvents: () -> Unit = {},
    onSaveSemanticTriple: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onDeleteSemanticTriple: (Long) -> Unit = {},
    onClearSemanticTriples: () -> Unit = {},
    onConsolidateMemories: () -> Unit = {}
) {
    var showAddOrEditDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryEntity?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    var showAddWmStepDialog by remember { mutableStateOf(false) }
    var showAddWmVarDialog by remember { mutableStateOf(false) }
    var showAddEpisodicDialog by remember { mutableStateOf(false) }
    var showAddSemanticDialog by remember { mutableStateOf(false) }

    val pinnedCount = remember(memories) { memories.count { it.isPinned } }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            JarvisPremiumTopBar(
                title = "5-Tier Neural Memory",
                subtitle = "${selectedCognitiveTab.shortTag} • $totalMemoriesCount LTM • ${semanticTriples.size} Triples • ${episodicEvents.size} Episodes",
                status = status,
                onBackClick = onBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (selectedCognitiveTab) {
                        CognitiveMemoryTab.STM_LIVE -> onConsolidateMemories()
                        CognitiveMemoryTab.WORKING_TASK -> showAddWmStepDialog = true
                        CognitiveMemoryTab.EPISODIC_TIMELINE -> showAddEpisodicDialog = true
                        CognitiveMemoryTab.SEMANTIC_GRAPH -> showAddSemanticDialog = true
                        CognitiveMemoryTab.LTM_VAULT -> {
                            editingMemory = null
                            showAddOrEditDialog = true
                        }
                    }
                },
                containerColor = JarvisViolet,
                contentColor = Color.White,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .padding(bottom = 84.dp)
                    .testTag("fab_add_memory")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (selectedCognitiveTab == CognitiveMemoryTab.STM_LIVE) Icons.Default.Sync else Icons.Default.Add,
                        contentDescription = "Add Memory Item"
                    )
                    Text(
                        text = when (selectedCognitiveTab) {
                            CognitiveMemoryTab.STM_LIVE -> "Consolidate"
                            CognitiveMemoryTab.WORKING_TASK -> "Add Step"
                            CognitiveMemoryTab.EPISODIC_TIMELINE -> "Log Event"
                            CognitiveMemoryTab.SEMANTIC_GRAPH -> "Add Triple"
                            CognitiveMemoryTab.LTM_VAULT -> "Create"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // 1. DUAL RICH GRADIENT HEADER CARDS ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Card: 5-Tier Cognitive Core Toggle & Consolidate
                Box(
                    modifier = Modifier
                        .weight(1.15f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF321B54),
                                    Color(0xFF1E173A),
                                    Color(0xFF14182B)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(Color(0xFFC084FC).copy(alpha = 0.65f), Color(0xFFF472B6).copy(alpha = 0.45f))
                            ),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = Color(0xFFC084FC),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "5-Tier Neural Core",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFFF5D0FE),
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = "STM • Working • Episodic • Semantic • LTM",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextSecondary,
                            fontSize = 11.sp,
                            minLines = 2,
                            maxLines = 2
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFFC084FC), Color(0xFFF472B6))
                                        )
                                    )
                                    .clickable { onConsolidateMemories() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("btn_consolidate_memory")
                            ) {
                                Text(
                                    text = "Consolidate",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF14091E),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Switch(
                                checked = settings.memoryEnabled,
                                onCheckedChange = onToggleMemoryEnabled,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFFC084FC),
                                    uncheckedThumbColor = JarvisTextMuted,
                                    uncheckedTrackColor = JarvisGlassSurface
                                ),
                                modifier = Modifier.testTag("switch_memory_center_enabled")
                            )
                        }
                    }
                }

                // Right Card: Vault Telemetry & Purge
                Box(
                    modifier = Modifier
                        .weight(0.85f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF2D2414),
                                    Color(0xFF1B1812),
                                    Color(0xFF13151E)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Color(0xFFFBBF24).copy(alpha = 0.45f),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Tier Telemetry",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFFFDE68A),
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }

                        Text(
                            text = "$totalMemoriesCount LTM ($pinnedCount Pin)\n${semanticTriples.size} Facts • ${episodicEvents.size} Logs",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextSecondary,
                            fontSize = 11.sp,
                            minLines = 2,
                            maxLines = 2
                        )

                        if (totalMemoriesCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFFFBBF24), Color(0xFFD97706))
                                        )
                                    )
                                    .clickable { showClearConfirmDialog = true }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                    .testTag("btn_clear_all_memories")
                            ) {
                                Text(
                                    text = "Purge All",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF1A1103),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xFF2B2418))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Clean Vault",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFDE68A),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. 5-TIER COGNITIVE MEMORY ARCHITECTURE SELECTOR PILLS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CognitiveMemoryTab.entries.forEach { tab ->
                    val isSelected = selectedCognitiveTab == tab
                    val tabAccent = cognitiveTabAccentColor(tab)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (isSelected) tabAccent.copy(alpha = 0.22f)
                                else Color(0xFF131724)
                            )
                            .border(
                                1.dp,
                                if (isSelected) tabAccent else Color.White.copy(alpha = 0.1f),
                                RoundedCornerShape(999.dp)
                            )
                            .clickable { onSelectCognitiveTab(tab) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("cognitive_tab_${tab.name.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isSelected) tabAccent else JarvisTextMuted)
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) Color.White else JarvisTextSecondary,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. SEARCH BAR (Shared across LTM, Episodic & Semantic)
            JarvisSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = when (selectedCognitiveTab) {
                    CognitiveMemoryTab.STM_LIVE -> "Filter recent conversation turns or entities..."
                    CognitiveMemoryTab.WORKING_TASK -> "Filter active task steps or scratchpad keys..."
                    CognitiveMemoryTab.EPISODIC_TIMELINE -> "Search episodes ('kal raat', 'aaj subah', 'YouTube')..."
                    CognitiveMemoryTab.SEMANTIC_GRAPH -> "Search S-P-O facts ('brother', 'Rahul', 'WhatsApp')..."
                    CognitiveMemoryTab.LTM_VAULT -> "Search memories by title, content, or #tags..."
                },
                modifier = Modifier.fillMaxWidth(),
                testTag = "memory_search_input"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. ACTIVE TIER CONTENT VIEW
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedCognitiveTab) {
                    CognitiveMemoryTab.STM_LIVE -> {
                        StmLiveTierContent(
                            stmTurns = stmTurns,
                            entityFocusStack = entityFocusStack,
                            searchQuery = searchQuery,
                            onClearStm = onClearShortTermMemory,
                            onConsolidate = onConsolidateMemories
                        )
                    }
                    CognitiveMemoryTab.WORKING_TASK -> {
                        WorkingMemoryTierContent(
                            plan = workingMemoryPlan,
                            searchQuery = searchQuery,
                            onAddStepClick = { showAddWmStepDialog = true },
                            onAddVarClick = { showAddWmVarDialog = true },
                            onCompleteStep = onCompleteWorkingMemoryStep,
                            onRemoveVar = onRemoveWorkingScratchpad,
                            onClearWm = onClearWorkingMemory
                        )
                    }
                    CognitiveMemoryTab.EPISODIC_TIMELINE -> {
                        EpisodicTimelineTierContent(
                            episodes = episodicEvents,
                            searchQuery = searchQuery,
                            onAddEpisodeClick = { showAddEpisodicDialog = true },
                            onDeleteEpisode = onDeleteEpisodicEvent,
                            onClearAllEpisodes = onClearEpisodicEvents
                        )
                    }
                    CognitiveMemoryTab.SEMANTIC_GRAPH -> {
                        SemanticGraphTierContent(
                            triples = semanticTriples,
                            searchQuery = searchQuery,
                            onAddTripleClick = { showAddSemanticDialog = true },
                            onDeleteTriple = onDeleteSemanticTriple,
                            onClearAllTriples = onClearSemanticTriples
                        )
                    }
                    CognitiveMemoryTab.LTM_VAULT -> {
                        LtmVaultTierContent(
                            status = status,
                            memories = memories,
                            selectedCategory = selectedCategory,
                            onSelectCategory = onSelectCategory,
                            onCreateMemoryClick = {
                                editingMemory = null
                                showAddOrEditDialog = true
                            },
                            onTogglePinMemory = onTogglePinMemory,
                            onEditMemory = { mem ->
                                editingMemory = mem
                                showAddOrEditDialog = true
                            },
                            onDeleteMemory = onDeleteMemory
                        )
                    }
                }
            }
        }
    }

    if (showAddOrEditDialog) {
        MemoryEditorDialog(
            initialMemory = editingMemory,
            onDismiss = {
                showAddOrEditDialog = false
                editingMemory = null
            },
            onConfirm = { title, content, category, tags, isPinned ->
                val existing = editingMemory
                if (existing != null) {
                    onUpdateMemory(
                        existing.copy(
                            title = title,
                            content = content,
                            category = category.name,
                            tags = tags,
                            isPinned = isPinned
                        )
                    )
                } else {
                    onSaveMemory(title, content, category, tags, isPinned)
                }
                showAddOrEditDialog = false
                editingMemory = null
            }
        )
    }

    if (showAddWmStepDialog) {
        WorkingStepDialog(
            onDismiss = { showAddWmStepDialog = false },
            onConfirm = { title, param ->
                onAddWorkingMemoryStep(title, param)
                showAddWmStepDialog = false
            }
        )
    }

    if (showAddWmVarDialog) {
        WorkingScratchpadVarDialog(
            onDismiss = { showAddWmVarDialog = false },
            onConfirm = { k, v ->
                onPutWorkingScratchpad(k, v)
                showAddWmVarDialog = false
            }
        )
    }

    if (showAddEpisodicDialog) {
        EpisodicEventDialog(
            onDismiss = { showAddEpisodicDialog = false },
            onConfirm = { summary, type, apps, valence ->
                onAddEpisodicEvent(summary, type, apps, valence)
                showAddEpisodicDialog = false
            }
        )
    }

    if (showAddSemanticDialog) {
        SemanticTripleDialog(
            onDismiss = { showAddSemanticDialog = false },
            onConfirm = { sub, pred, obj, cat ->
                onSaveSemanticTriple(sub, pred, obj, cat)
                showAddSemanticDialog = false
            }
        )
    }

    if (showClearConfirmDialog) {
        JarvisModernDialog(
            title = "Purge Neural Memory Vault?",
            message = "Saari saved memories permanently delete ho jayengi. Ye action undo nahi ho sakta.",
            confirmLabel = "Purge Vault",
            isDestructive = true,
            onConfirm = {
                onClearAllMemories()
                showClearConfirmDialog = false
            },
            onDismiss = { showClearConfirmDialog = false }
        )
    }
}

// ============================================================================
// TIER 1: SHORT-TERM MEMORY (STM LIVE)
// ============================================================================
@Composable
private fun StmLiveTierContent(
    stmTurns: List<ShortTermTurn>,
    entityFocusStack: ActiveEntityFocusStack,
    searchQuery: String,
    onClearStm: () -> Unit,
    onConsolidate: () -> Unit
) {
    val filteredTurns = remember(stmTurns, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) stmTurns.reversed()
        else stmTurns.filter {
            it.text.lowercase().contains(q) ||
                it.activeAppName.lowercase().contains(q) ||
                it.intent.lowercase().contains(q)
        }.reversed()
    }
    val timeFmt = remember { SimpleDateFormat("hh:mm:ss a", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 148.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF131724))
                    .border(1.dp, JarvisNeonCyan.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = JarvisNeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Active Entity Focus Stack (Pronoun Resolver)",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(AlertCrimson.copy(alpha = 0.18f))
                            .clickable(onClick = onClearStm)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("btn_clear_stm")
                    ) {
                        Text(
                            text = "Clear STM",
                            style = MaterialTheme.typography.labelSmall,
                            color = AlertCrimson,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Resolves Hindi/Hinglish pronouns ('usko', 'unhe', 'wahan', 'woh wala') in real time:",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisTextSecondary,
                    fontSize = 11.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FocusEntityBadge(
                        label = "Person ('usko')",
                        value = entityFocusStack.lastMentionedPerson.ifBlank { "None" },
                        accent = JarvisNeonCyan,
                        modifier = Modifier.weight(1f)
                    )
                    FocusEntityBadge(
                        label = "App ('wahan')",
                        value = entityFocusStack.lastMentionedApp.ifBlank { "None" },
                        accent = StatusListeningEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    FocusEntityBadge(
                        label = "Topic ('woh')",
                        value = entityFocusStack.lastMentionedQueryOrItem.ifBlank { "None" },
                        accent = StatusThinkingAmber,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (entityFocusStack.rollingSessionSummary.isNotBlank()) {
                    Text(
                        text = "Rolling Micro-Summary: ${entityFocusStack.rollingSessionSummary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisVioletBright,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (filteredTurns.isEmpty()) {
            item {
                EmptyTierCard(
                    title = "Short-Term Conversation Buffer Empty",
                    subtitle = "Jaise hi aap Voice ya Chat me baat karenge, last 20 turns aur pronoun focus entities yahan live dikhenge.",
                    accentColor = JarvisNeonCyan
                )
            }
        } else {
            items(filteredTurns, key = { "${it.timestampMs}_${it.role}_${it.text.hashCode()}" }) { turn ->
                val isUser = turn.role.equals("USER", ignoreCase = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF111524))
                        .border(
                            1.dp,
                            if (isUser) JarvisNeonCyan.copy(alpha = 0.3f) else JarvisViolet.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isUser) JarvisNeonCyan.copy(alpha = 0.18f)
                                else JarvisViolet.copy(alpha = 0.22f)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isUser) "USER" else "JARVIS",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUser) JarvisNeonCyan else JarvisVioletBright,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = turn.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = timeFmt.format(Date(turn.timestampMs)),
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextMuted,
                                fontSize = 10.sp
                            )
                            if (turn.intent.isNotBlank()) {
                                Text(
                                    text = "Intent: ${turn.intent}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusListeningEmerald,
                                    fontSize = 10.sp
                                )
                            }
                            if (turn.activeAppName.isNotBlank()) {
                                Text(
                                    text = "App: ${turn.activeAppName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusThinkingAmber,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusEntityBadge(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A2035))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = JarvisTextMuted,
            fontSize = 9.sp,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ============================================================================
// TIER 2: WORKING MEMORY BLACKBOARD (TASK STEPS & SCRATCHPAD)
// ============================================================================
@Composable
private fun WorkingMemoryTierContent(
    plan: WorkingTaskPlanSnapshot,
    searchQuery: String,
    onAddStepClick: () -> Unit,
    onAddVarClick: () -> Unit,
    onCompleteStep: (Int) -> Unit,
    onRemoveVar: (String) -> Unit,
    onClearWm: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 148.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF131724))
                    .border(1.dp, StatusThinkingAmber.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = null,
                            tint = StatusThinkingAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = plan.rootGoal.ifBlank { "Working Memory Blackboard" },
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Status: ${plan.status} • Steps: ${plan.steps.count { it.status == WorkingTaskStepStatus.DONE }}/${plan.steps.size} • Loop Check: ${if (plan.isLoopDetected) "LOOP WARNING" else "OK"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (plan.isLoopDetected) AlertCrimson else StatusListeningEmerald,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(JarvisNeonCyan.copy(alpha = 0.16f))
                                .clickable(onClick = onAddVarClick)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("btn_add_wm_var")
                        ) {
                            Text(
                                text = "+ Variable",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisNeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(AlertCrimson.copy(alpha = 0.16f))
                                .clickable(onClick = onClearWm)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("btn_clear_wm")
                        ) {
                            Text(
                                text = "Reset",
                                style = MaterialTheme.typography.labelSmall,
                                color = AlertCrimson,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Scratchpad Key-Value Variables
                if (plan.scratchpadVariables.isNotEmpty()) {
                    Text(
                        text = "INTERMEDIATE SCRATCHPAD VARIABLES",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisNeonCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    plan.scratchpadVariables.entries.forEach { (key, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1A2035))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusThinkingAmber,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove Variable",
                                tint = AlertCrimson,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { onRemoveVar(key) }
                            )
                        }
                    }
                }
            }
        }

        if (plan.steps.isEmpty()) {
            item {
                EmptyTierCard(
                    title = "No Active Multi-Step Task in Working Memory",
                    subtitle = "Jab JARVIS multi-step autonomous task chalayega (jaise 'YouTube kholo -> search karo -> first video play karo') ya aap '+ Add Step' dabayenge, toh har step ka live status yahan track hoga.",
                    accentColor = StatusThinkingAmber
                )
            }
        } else {
            items(plan.steps, key = { it.stepIndex }) { step ->
                val statusColor = when (step.status) {
                    WorkingTaskStepStatus.DONE -> StatusListeningEmerald
                    WorkingTaskStepStatus.IN_PROGRESS -> StatusThinkingAmber
                    WorkingTaskStepStatus.FAILED -> AlertCrimson
                    WorkingTaskStepStatus.PENDING -> JarvisTextMuted
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF111524))
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .clickable { onCompleteStep(step.stepIndex) }
                        .padding(14.dp)
                        .testTag("wm_step_${step.stepIndex}"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(statusColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "#${step.stepIndex}",
                                style = MaterialTheme.typography.labelMedium,
                                color = statusColor,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Column {
                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            if (step.note.isNotBlank()) {
                                Text(
                                    text = step.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusColor.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = step.status.badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// TIER 3: EPISODIC MEMORY TIMELINE (TIME-STAMPED INTERACTIONS)
// ============================================================================
@Composable
private fun EpisodicTimelineTierContent(
    episodes: List<EpisodicEventEntity>,
    searchQuery: String,
    onAddEpisodeClick: () -> Unit,
    onDeleteEpisode: (Long) -> Unit,
    onClearAllEpisodes: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val filtered = remember(episodes, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) episodes
        else episodes.filter {
            it.summary.lowercase().contains(q) ||
                it.timeBucket.lowercase().contains(q) ||
                it.involvedApps.lowercase().contains(q) ||
                it.involvedEntities.lowercase().contains(q) ||
                it.eventType.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 148.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF131724))
                    .border(1.dp, StatusListeningEmerald.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = StatusListeningEmerald
                    )
                    Column {
                        Text(
                            text = "Autobiographical Episodic Timeline",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Supports 'kal raat', 'aaj subah', 'parso' temporal recall (${episodes.size} events)",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                if (episodes.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(AlertCrimson.copy(alpha = 0.16f))
                            .clickable(onClick = onClearAllEpisodes)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("btn_clear_episodic")
                    ) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelSmall,
                            color = AlertCrimson,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyTierCard(
                    title = "No Episodic Events Recorded Yet",
                    subtitle = "Har conversation aur phone action time-bucket (MORNING, AFTERNOON, EVENING, NIGHT) ke saath yahan log hota hai.",
                    accentColor = StatusListeningEmerald
                )
            }
        } else {
            items(filtered, key = { it.id }) { ep ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF111524))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                        .padding(14.dp)
                        .testTag("episodic_card_${ep.id}"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(StatusListeningEmerald.copy(alpha = 0.18f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = ep.timeBucket,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusListeningEmerald,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(JarvisElectricBlue.copy(alpha = 0.18f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = ep.eventType,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisNeonCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = dateFormat.format(Date(ep.timestampMs)),
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextMuted,
                                fontSize = 10.sp
                            )
                        }

                        Text(
                            text = ep.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            lineHeight = 18.sp
                        )

                        if (ep.involvedApps.isNotBlank() || ep.involvedEntities.isNotBlank()) {
                            Text(
                                text = buildString {
                                    if (ep.involvedApps.isNotBlank()) append("Apps: ${ep.involvedApps}  ")
                                    if (ep.involvedEntities.isNotBlank()) append("Entities: ${ep.involvedEntities}")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusThinkingAmber,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Episode",
                        tint = AlertCrimson,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onDeleteEpisode(ep.id) }
                    )
                }
            }
        }
    }
}

// ============================================================================
// TIER 4: SEMANTIC KNOWLEDGE GRAPH (SUBJECT - PREDICATE - OBJECT TRIPLES)
// ============================================================================
@Composable
private fun SemanticGraphTierContent(
    triples: List<SemanticTripleEntity>,
    searchQuery: String,
    onAddTripleClick: () -> Unit,
    onDeleteTriple: (Long) -> Unit,
    onClearAllTriples: () -> Unit
) {
    val filtered = remember(triples, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) triples
        else triples.filter {
            it.subject.lowercase().contains(q) ||
                it.predicate.lowercase().contains(q) ||
                it.objectValue.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 148.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF131724))
                    .border(1.dp, JarvisVioletBright.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = JarvisVioletBright
                    )
                    Column {
                        Text(
                            text = "Semantic Knowledge Graph (S-P-O Triples)",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Facts, Kinship ('Bhai -> Rahul') & App UI Concepts (${triples.size} nodes)",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                if (triples.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(AlertCrimson.copy(alpha = 0.16f))
                            .clickable(onClick = onClearAllTriples)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("btn_clear_semantic")
                    ) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelSmall,
                            color = AlertCrimson,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyTierCard(
                    title = "No Semantic Knowledge Triples Found",
                    subtitle = "Add structured Subject -> Predicate -> Object facts (e.g., User --[brother]--> Rahul) or speak 'Mera bhai Rahul hai' in Chat/Voice.",
                    accentColor = JarvisVioletBright
                )
            }
        } else {
            items(filtered, key = { it.id }) { triple ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF111524))
                        .border(1.dp, JarvisVioletBright.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                        .padding(14.dp)
                        .testTag("semantic_card_${triple.id}"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(JarvisViolet.copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = triple.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisVioletBright,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = "Confidence: ${(triple.confidence * 100).toInt()}% • ${triple.source}",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextMuted,
                                fontSize = 10.sp
                            )
                        }

                        Text(
                            text = "${triple.subject}  ──[${triple.predicate}]──►  ${triple.objectValue}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Triple",
                        tint = AlertCrimson,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onDeleteTriple(triple.id) }
                    )
                }
            }
        }
    }
}

// ============================================================================
// TIER 5: LONG-TERM MEMORY VAULT (HYBRID 4-FACTOR RECALL)
// ============================================================================
@Composable
private fun LtmVaultTierContent(
    status: AssistantStatus,
    memories: List<MemoryEntity>,
    selectedCategory: MemoryCategory?,
    onSelectCategory: (MemoryCategory?) -> Unit,
    onCreateMemoryClick: () -> Unit,
    onTogglePinMemory: (MemoryEntity) -> Unit,
    onEditMemory: (MemoryEntity) -> Unit,
    onDeleteMemory: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // GLOWING UNDERLINE CATEGORY TAB BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UnderlineCategoryTab(
                label = "All",
                selected = selectedCategory == null,
                accentColor = JarvisVioletBright,
                onClick = { onSelectCategory(null) },
                testTag = "memory_cat_all"
            )
            MemoryCategory.entries.forEach { cat ->
                UnderlineCategoryTab(
                    label = cat.label,
                    selected = selectedCategory == cat,
                    accentColor = categoryAccentColor(cat),
                    onClick = {
                        onSelectCategory(if (selectedCategory == cat) null else cat)
                    },
                    testTag = "memory_cat_${cat.name.lowercase()}"
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // NEURAL MEMORY FORGE BANNER ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF131724))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(JarvisViolet.copy(alpha = 0.35f), Color(0xFFEC4899).copy(alpha = 0.25f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = Color(0xFFF472B6),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "Neural Memory Forge (4-Factor LTM)",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Relevance + Importance + Recency + Access Frequency",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(JarvisViolet.copy(alpha = 0.14f))
                    .border(1.dp, JarvisVioletBright, RoundedCornerShape(999.dp))
                    .clickable(onClick = onCreateMemoryClick)
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "Create",
                    style = MaterialTheme.typography.labelMedium,
                    color = JarvisVioletBright,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (memories.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                JarvisGlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = JarvisViolet,
                    contentPadding = PaddingValues(28.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        JarvisAIOrb(
                            status = status,
                            modifier = Modifier.size(92.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No Memories in This Vault",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Store personal facts, preferences, or project notes for automatic recall during Live Voice and Text Chat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 148.dp)
            ) {
                items(memories, key = { it.id }) { memory ->
                    MemoryItemCard(
                        memory = memory,
                        onTogglePin = { onTogglePinMemory(memory) },
                        onEdit = { onEditMemory(memory) },
                        onDelete = { onDeleteMemory(memory.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTierCard(
    title: String,
    subtitle: String,
    accentColor: Color
) {
    JarvisGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        accentColor = accentColor,
        contentPadding = PaddingValues(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = JarvisTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun UnderlineCategoryTab(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) Color.White else JarvisTextMuted,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
        )
        Box(
            modifier = Modifier
                .width(if (selected) 22.dp else 0.dp)
                .height(3.5.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) accentColor else Color.Transparent)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryItemCard(
    memory: MemoryEntity,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val resolvedCategory = remember(memory.category) {
        MemoryCategory.entries.find { it.name.equals(memory.category, ignoreCase = true) }
            ?: MemoryCategory.GENERAL
    }
    val catColor = categoryAccentColor(resolvedCategory)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF111524))
            .border(
                1.dp,
                if (memory.isPinned) StatusThinkingAmber.copy(alpha = 0.5f)
                else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(20.dp)
            )
            .padding(14.dp)
            .testTag("memory_card_${memory.id}"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            catColor.copy(alpha = 0.35f),
                            JarvisViolet.copy(alpha = 0.22f)
                        )
                    )
                )
                .border(1.dp, catColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = memory.title.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = memory.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1B2136))
                        .border(1.dp, catColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = resolvedCategory.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = catColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (memory.isPinned) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(StatusThinkingAmber.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Pinned",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusThinkingAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodySmall,
                color = JarvisTextSecondary,
                lineHeight = 18.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = dateFormat.format(Date(memory.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextMuted,
                    fontSize = 10.sp
                )
                Text(
                    text = "Recalls: ${memory.accessCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusListeningEmerald,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (memory.tags.isNotBlank()) {
                    Text(
                        text = memory.tags.split(",").joinToString(" ") { "#${it.trim()}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisVioletBright,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A2035))
                    .clickable(onClick = onTogglePin),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (memory.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                    contentDescription = "Pin Memory",
                    tint = if (memory.isPinned) StatusThinkingAmber else JarvisTextSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A2035))
                    .clickable(onClick = onEdit)
                    .testTag("edit_memory_${memory.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Memory",
                    tint = JarvisNeonCyan,
                    modifier = Modifier.size(15.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AlertCrimson.copy(alpha = 0.14f))
                    .clickable(onClick = onDelete)
                    .testTag("delete_memory_${memory.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Memory",
                    tint = AlertCrimson,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

// ============================================================================
// DIALOGS FOR WORKING MEMORY, EPISODIC, SEMANTIC & LTM
// ============================================================================
@Composable
private fun WorkingStepDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, param: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var param by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisElevatedSurface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Add Working Memory Task Step",
                style = MaterialTheme.typography.titleMedium,
                color = JarvisTextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Step Goal / Action (e.g. Open WhatsApp)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("wm_step_dialog_title")
                )
                OutlinedTextField(
                    value = param,
                    onValueChange = { param = it },
                    label = { Text("Target Parameter (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("wm_step_dialog_param")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onConfirm(title, param) },
                modifier = Modifier.testTag("wm_step_dialog_save")
            ) {
                Text("Add Step", color = JarvisNeonCyan, fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = JarvisTextSecondary) }
        }
    )
}

@Composable
private fun WorkingScratchpadVarDialog(
    onDismiss: () -> Unit,
    onConfirm: (key: String, value: String) -> Unit
) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisElevatedSurface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Add Scratchpad Variable",
                style = MaterialTheme.typography.titleMedium,
                color = JarvisTextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Variable Key (e.g. extracted_otp)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("wm_var_dialog_key")
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Variable Value (e.g. 482910)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("wm_var_dialog_value")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (key.isNotBlank() && value.isNotBlank()) onConfirm(key, value) },
                modifier = Modifier.testTag("wm_var_dialog_save")
            ) {
                Text("Save Variable", color = JarvisNeonCyan, fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = JarvisTextSecondary) }
        }
    )
}

@Composable
private fun EpisodicEventDialog(
    onDismiss: () -> Unit,
    onConfirm: (summary: String, eventType: String, apps: String, valence: Int) -> Unit
) {
    var summary by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisElevatedSurface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Log Episodic Timeline Event",
                style = MaterialTheme.typography.titleMedium,
                color = JarvisTextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text("Event Summary (e.g. Watched Kotlin tutorial)") },
                    modifier = Modifier.fillMaxWidth().testTag("episodic_dialog_summary")
                )
                OutlinedTextField(
                    value = apps,
                    onValueChange = { apps = it },
                    label = { Text("Involved App (e.g. YouTube)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("episodic_dialog_apps")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (summary.isNotBlank()) onConfirm(summary, "APP_ACTION", apps, 3) },
                modifier = Modifier.testTag("episodic_dialog_save")
            ) {
                Text("Log Event", color = StatusListeningEmerald, fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = JarvisTextSecondary) }
        }
    )
}

@Composable
private fun SemanticTripleDialog(
    onDismiss: () -> Unit,
    onConfirm: (subject: String, predicate: String, objectValue: String, category: String) -> Unit
) {
    var subject by remember { mutableStateOf("User") }
    var predicate by remember { mutableStateOf("") }
    var objectValue by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("RELATIONSHIP") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisElevatedSurface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Add Semantic Knowledge Triple",
                style = MaterialTheme.typography.titleMedium,
                color = JarvisTextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject (e.g. User or WhatsApp)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("semantic_dialog_subject")
                )
                OutlinedTextField(
                    value = predicate,
                    onValueChange = { predicate = it },
                    label = { Text("Predicate / Relation (e.g. brother)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("semantic_dialog_predicate")
                )
                OutlinedTextField(
                    value = objectValue,
                    onValueChange = { objectValue = it },
                    label = { Text("Object / Value (e.g. Rahul)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("semantic_dialog_object")
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (IDENTITY, RELATIONSHIP, PREFERENCE, APP_UI_CONCEPT)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("semantic_dialog_category")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (subject.isNotBlank() && predicate.isNotBlank() && objectValue.isNotBlank()) {
                        onConfirm(subject, predicate, objectValue, category)
                    }
                },
                modifier = Modifier.testTag("semantic_dialog_save")
            ) {
                Text("Save Triple", color = JarvisVioletBright, fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = JarvisTextSecondary) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryEditorDialog(
    initialMemory: MemoryEntity?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String, category: MemoryCategory, tags: String, isPinned: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(initialMemory?.title ?: "") }
    var content by remember { mutableStateOf(initialMemory?.content ?: "") }
    var category by remember {
        mutableStateOf(
            MemoryCategory.entries.find { it.name.equals(initialMemory?.category, ignoreCase = true) }
                ?: MemoryCategory.PERSONAL
        )
    }
    var tags by remember { mutableStateOf(initialMemory?.tags ?: "") }
    var isPinned by remember { mutableStateOf(initialMemory?.isPinned ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisElevatedSurface,
        shape = RoundedCornerShape(26.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = JarvisNeonCyan
                )
                Text(
                    text = if (initialMemory != null) "Edit Neural Memory" else "Create Neural Memory",
                    style = MaterialTheme.typography.titleMedium,
                    color = JarvisTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Memory Title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisNeonCyan,
                        unfocusedBorderColor = JarvisGlassBorder
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memory_dialog_title")
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Memory Details / Fact") },
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisNeonCyan,
                        unfocusedBorderColor = JarvisGlassBorder
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memory_dialog_content")
                )

                Text(
                    text = "SELECT CATEGORY",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisNeonCyan,
                    fontWeight = FontWeight.Bold
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MemoryCategory.entries.forEach { cat ->
                        val isSelected = category == cat
                        val accent = categoryAccentColor(cat)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) accent.copy(alpha = 0.22f) else JarvisGlassSurface)
                                .border(
                                    1.dp,
                                    if (isSelected) accent else JarvisGlassBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { category = cat }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) accent else JarvisTextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma separated)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisNeonCyan,
                        unfocusedBorderColor = JarvisGlassBorder
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memory_dialog_tags")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(JarvisGlassSurface)
                        .clickable { isPinned = !isPinned }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = isPinned,
                        onCheckedChange = { isPinned = it },
                        colors = CheckboxDefaults.colors(checkedColor = StatusThinkingAmber)
                    )
                    Text(
                        text = "Pin high-priority memory for instant AI recall",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextPrimary
                    )
                }
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (title.isNotBlank() && content.isNotBlank()) {
                            Brush.horizontalGradient(listOf(Color(0xFFC084FC), Color(0xFFF472B6)))
                        } else {
                            Brush.horizontalGradient(listOf(JarvisGlassSurface, JarvisGlassSurface))
                        }
                    )
                    .clickable(enabled = title.isNotBlank() && content.isNotBlank()) {
                        onConfirm(title, content, category, tags, isPinned)
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .testTag("memory_dialog_save_btn")
            ) {
                Text(
                    text = "Save",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (title.isNotBlank() && content.isNotBlank()) Color(0xFF14091E) else JarvisTextMuted,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = JarvisTextSecondary
                )
            }
        }
    )
}

private fun cognitiveTabAccentColor(tab: CognitiveMemoryTab): Color {
    return when (tab) {
        CognitiveMemoryTab.STM_LIVE -> JarvisNeonCyan
        CognitiveMemoryTab.WORKING_TASK -> StatusThinkingAmber
        CognitiveMemoryTab.EPISODIC_TIMELINE -> StatusListeningEmerald
        CognitiveMemoryTab.SEMANTIC_GRAPH -> JarvisVioletBright
        CognitiveMemoryTab.LTM_VAULT -> Color(0xFFF472B6)
    }
}

private fun categoryAccentColor(category: MemoryCategory): Color {
    return when (category) {
        MemoryCategory.PERSONAL -> JarvisNeonCyan
        MemoryCategory.PREFERENCES -> StatusListeningEmerald
        MemoryCategory.WORK -> JarvisElectricBlue
        MemoryCategory.PROJECTS -> JarvisVioletBright
        MemoryCategory.IMPORTANT -> StatusThinkingAmber
        MemoryCategory.GENERAL -> JarvisNeonGreen
    }
}
