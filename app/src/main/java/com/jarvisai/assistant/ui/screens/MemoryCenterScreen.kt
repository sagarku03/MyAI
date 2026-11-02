package com.jarvisai.assistant.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.StatusListeningEmerald
import com.example.ui.theme.StatusSpeakingAzure
import com.example.ui.theme.StatusThinkingAmber
import com.jarvisai.assistant.data.local.MemoryEntity
import com.jarvisai.assistant.model.AssistantStatus
import com.jarvisai.assistant.model.MemoryCategory
import com.jarvisai.assistant.model.UserSettings
import com.jarvisai.assistant.ui.components.HudCard
import com.jarvisai.assistant.ui.components.HudConfirmationDialog
import com.jarvisai.assistant.ui.components.HudTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onClearAllMemories: () -> Unit
) {
    BackHandler(onBack = onBack)

    var showCreateOrEditModal by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryEntity?>(null) }
    var showClearAllConfirm by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            HudTopBar(
                title = "NEURAL MEMORY CENTER",
                subtitle = "$totalMemoriesCount APPROVED MEMORIES // ROOM DB",
                status = status,
                onBackClick = onBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingMemory = null
                    showCreateOrEditModal = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black,
                modifier = Modifier.testTag("fab_add_memory")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Approved Memory")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .widthIn(max = 720.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Memory Context Status & Privacy Control Bar
            HudCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "USER-APPROVED MEMORY RETRIEVAL",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "JARVIS never auto-saves every conversation. Only memories you create or approve are stored in Room and retrieved for context.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = settings.memoryEnabled,
                        onCheckedChange = onToggleMemoryEnabled,
                        modifier = Modifier.testTag("switch_memory_center_enabled")
                    )
                }
            }

            // Search Bar & Clear All Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search memories by title, content, or tag...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear Search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("memory_search_input")
                )

                if (totalMemoriesCount > 0) {
                    IconButton(
                        onClick = { showClearAllConfirm = true },
                        modifier = Modifier.testTag("btn_clear_all_memories")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear All Memories",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text("ALL ($totalMemoriesCount)", style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("memory_cat_all")
                )
                MemoryCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = {
                            onSelectCategory(if (selectedCategory == cat) null else cat)
                        },
                        label = { Text(cat.label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("memory_cat_${cat.name.lowercase()}")
                    )
                }
            }

            // Memories List
            if (memories.isEmpty()) {
                HudCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (searchQuery.isNotBlank() || selectedCategory != null) {
                                "NO MATCHING MEMORIES FOUND"
                            } else {
                                "MEMORY VAULT IS EMPTY"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap the + button below to add personal preferences, project specs, or facts for JARVIS to remember.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(memories, key = { it.id }) { memory ->
                        MemoryItemCard(
                            memory = memory,
                            onPinToggle = { onTogglePinMemory(memory) },
                            onEdit = {
                                editingMemory = memory
                                showCreateOrEditModal = true
                            },
                            onDelete = { onDeleteMemory(memory.id) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateOrEditModal) {
        MemoryEditorDialog(
            initialMemory = editingMemory,
            onDismiss = {
                showCreateOrEditModal = false
                editingMemory = null
            },
            onSave = { title, content, category, tags, isPinned ->
                val existing = editingMemory
                if (existing != null) {
                    onUpdateMemory(
                        existing.copy(
                            title = title.trim(),
                            content = content.trim(),
                            category = category.name,
                            tags = tags.trim(),
                            isPinned = isPinned
                        )
                    )
                } else {
                    onSaveMemory(title, content, category, tags, isPinned)
                }
                showCreateOrEditModal = false
                editingMemory = null
            }
        )
    }

    if (showClearAllConfirm) {
        HudConfirmationDialog(
            title = "CLEAR ALL MEMORIES?",
            message = "Are you sure you want to permanently erase all $totalMemoriesCount user-approved memories from local Room storage?",
            confirmLabel = "CLEAR ALL",
            isDestructive = true,
            onConfirm = onClearAllMemories,
            onDismiss = { showClearAllConfirm = false }
        )
    }
}

@Composable
private fun MemoryItemCard(
    memory: MemoryEntity,
    onPinToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryEnum = MemoryCategory.entries.find { it.name == memory.category } ?: MemoryCategory.GENERAL
    val accent = when (categoryEnum) {
        MemoryCategory.IMPORTANT -> StatusThinkingAmber
        MemoryCategory.PROJECTS -> StatusSpeakingAzure
        MemoryCategory.PERSONAL -> StatusListeningEmerald
        else -> MaterialTheme.colorScheme.primary
    }

    HudCard(
        glowColor = if (memory.isPinned) StatusThinkingAmber else accent,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("memory_card_${memory.id}")
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
                Text(
                    text = categoryEnum.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
                if (memory.isPinned) {
                    Text(
                        text = "PINNED",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusThinkingAmber
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onPinToggle,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (memory.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "Pin Memory",
                        tint = if (memory.isPinned) StatusThinkingAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("edit_memory_${memory.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Memory",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("delete_memory_${memory.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Memory",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = memory.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = memory.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (memory.tags.isNotBlank()) {
                Text(
                    text = "TAGS: ${memory.tags}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            val dateStr = remember(memory.updatedAt) {
                SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(memory.updatedAt))
            }
            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MemoryEditorDialog(
    initialMemory: MemoryEntity?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, category: MemoryCategory, tags: String, isPinned: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(initialMemory?.title ?: "") }
    var content by remember { mutableStateOf(initialMemory?.content ?: "") }
    var category by remember {
        mutableStateOf(
            MemoryCategory.entries.find { it.name == initialMemory?.category } ?: MemoryCategory.PERSONAL
        )
    }
    var tags by remember { mutableStateOf(initialMemory?.tags ?: "") }
    var isPinned by remember { mutableStateOf(initialMemory?.isPinned ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = if (initialMemory == null) "CREATE APPROVED MEMORY" else "EDIT MEMORY",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Memory Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memory_dialog_title")
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Memory Fact / Context") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memory_dialog_content")
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma-separated, optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memory_dialog_tags")
                )

                Text(
                    text = "CATEGORY:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MemoryCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isPinned,
                        onCheckedChange = { isPinned = it }
                    )
                    Text(
                        text = "Pin priority memory (always prioritized for Gemini context)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onSave(title, content, category, tags, isPinned)
                    }
                },
                enabled = title.isNotBlank() && content.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("memory_dialog_save_btn")
            ) {
                Text("SAVE MEMORY")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        }
    )
}
