package com.jarvisai.assistant.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.mcp.bridges.NoteItem
import com.jarvisai.assistant.mcp.bridges.NoteKind
import com.jarvisai.assistant.mcp.bridges.NotesMcpBridge
import com.jarvisai.assistant.mcp.bridges.TodoEntry

private enum class NotesFilter(val label: String) {
    ALL("All"),
    NOTES("Notes"),
    TODOS("To-Do Lists")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        NotesMcpBridge.init(context)
    }

    val allNotes by NotesMcpBridge.notes.collectAsState()
    var activeNoteId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(NotesFilter.ALL) }

    val selectedNote = remember(allNotes, activeNoteId) {
        activeNoteId?.let { id -> allNotes.firstOrNull { it.id == id } }
    }

    BackHandler {
        if (selectedNote != null) activeNoteId = null else onBack()
    }

    if (selectedNote != null) {
        NoteEditorView(
            note = selectedNote,
            onBack = { activeNoteId = null },
            onDelete = {
                NotesMcpBridge.deleteNote(selectedNote.id)
                activeNoteId = null
            }
        )
        return
    }

    val filteredNotes = remember(allNotes, searchQuery, filter) {
        allNotes.filter { note ->
            val kindMatch = when (filter) {
                NotesFilter.ALL -> true
                NotesFilter.NOTES -> note.kind == NoteKind.NOTE
                NotesFilter.TODOS -> note.kind == NoteKind.TODO
            }
            val queryMatch = searchQuery.isBlank() ||
                note.title.contains(searchQuery, ignoreCase = true) ||
                note.text.contains(searchQuery, ignoreCase = true) ||
                note.items.any { it.text.contains(searchQuery, ignoreCase = true) }
            kindMatch && queryMatch
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = Color(0xFF0A0F1A),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Agentra Notes & To-Do", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Synced live with notes_mcp tools", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("notes_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0E1525))
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        val created = NotesMcpBridge.createNote(kind = NoteKind.TODO, title = "")
                        activeNoteId = created.id
                    },
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color(0xFF38BDF8),
                    modifier = Modifier.testTag("notes_new_todo_fab")
                ) {
                    Icon(Icons.Default.CheckBox, contentDescription = "New To-Do List")
                }
                FloatingActionButton(
                    onClick = {
                        val created = NotesMcpBridge.createNote(kind = NoteKind.NOTE, title = "")
                        activeNoteId = created.id
                    },
                    containerColor = Color(0xFF38BDF8),
                    contentColor = Color(0xFF04101D),
                    modifier = Modifier.testTag("notes_new_note_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Note")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search notes & checklists...", color = Color(0xFF64748B), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search notes", tint = Color(0xFF64748B)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Color(0xFF94A3B8))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF111827),
                    unfocusedContainerColor = Color(0xFF111827),
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF1F2937),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notes_search_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NotesFilter.entries.forEach { entry ->
                    FilterChip(
                        selected = filter == entry,
                        onClick = { filter = entry },
                        label = { Text(entry.label, fontSize = 12.sp) },
                        modifier = Modifier.testTag("notes_filter_${entry.name.lowercase()}")
                    )
                }
            }

            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = if (searchQuery.isBlank()) "No notes or to-do lists yet" else "No matching notes",
                            color = Color(0xFFCBD5E1),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Tap + to create a note or checklist. Agentra agents can also read & update these via notes_mcp.",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        NoteSummaryCard(
                            note = note,
                            onOpen = { activeNoteId = note.id },
                            onTogglePin = { NotesMcpBridge.togglePin(note.id) },
                            onToggleTodo = { itemId -> NotesMcpBridge.toggleTodoItem(note.id, itemId) },
                            onDelete = { NotesMcpBridge.deleteNote(note.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteSummaryCard(
    note: NoteItem,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleTodo: (String) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF111827),
        border = BorderStroke(
            1.dp,
            if (note.pinned) Color(0xFF38BDF8).copy(alpha = 0.55f) else Color(0xFF1F2937)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("note_card_${note.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (note.kind == NoteKind.TODO) Icons.Outlined.CheckBox else Icons.Default.Description,
                    contentDescription = note.kind.wire,
                    tint = if (note.kind == NoteKind.TODO) Color(0xFF10B981) else Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = note.title.ifBlank { if (note.kind == NoteKind.TODO) "Untitled Checklist" else "Untitled Note" },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onTogglePin, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (note.pinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (note.pinned) "Unpin note" else "Pin note",
                        tint = if (note.pinned) Color(0xFF38BDF8) else Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete note",
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (note.text.isNotBlank()) {
                Text(
                    text = note.text,
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (note.kind == NoteKind.TODO && note.items.isNotEmpty()) {
                note.items.take(4).forEach { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleTodo(entry.id) }
                    ) {
                        Checkbox(
                            checked = entry.done,
                            onCheckedChange = { onToggleTodo(entry.id) },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF10B981)),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = entry.text.ifBlank { "Empty task" },
                            color = if (entry.done) Color(0xFF64748B) else Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            textDecoration = if (entry.done) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (note.items.size > 4) {
                    Text(
                        text = "+${note.items.size - 4} more items (${note.doneCount}/${note.items.size} done)",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                text = "Updated ${formatRelativeTime(note.updatedAt)}",
                color = Color(0xFF64748B),
                fontSize = 11.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditorView(
    note: NoteItem,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var pendingFocusItemId by remember { mutableStateOf<String?>(null) }

    fun insertItem(atIndex: Int) {
        val fresh = TodoEntry()
        NotesMcpBridge.updateNote(note.id) { current ->
            val next = current.items.toMutableList()
            next.add(atIndex.coerceIn(0, next.size), fresh)
            current.copy(items = next)
        }
        pendingFocusItemId = fresh.id
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = Color(0xFF0A0F1A),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (note.kind == NoteKind.TODO) "To-Do List" else "Note",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Notes", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { NotesMcpBridge.togglePin(note.id) }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = if (note.pinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin note",
                            tint = if (note.pinned) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                        )
                    }
                    IconButton(
                        onClick = {
                            val formatted = buildString {
                                if (note.title.isNotBlank()) appendLine(note.title)
                                if (note.text.isNotBlank()) appendLine(note.text)
                                note.items.forEach { appendLine("${if (it.done) "[x]" else "[ ]"} ${it.text}") }
                            }.trim()
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Agentra Note", formatted))
                            Toast.makeText(context, "Copied note", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy note", tint = Color(0xFFCBD5E1))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More note actions", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color(0xFF1E293B))
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (note.kind == NoteKind.TODO) "Convert to Plain Note" else "Convert to To-Do List",
                                        color = Color.White
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    NotesMcpBridge.updateNote(note.id) { current ->
                                        if (current.kind == NoteKind.TODO) {
                                            val merged = buildString {
                                                if (current.text.isNotBlank()) appendLine(current.text)
                                                current.items.filter { it.text.isNotBlank() }.forEach {
                                                    appendLine("${if (it.done) "[x]" else "[ ]"} ${it.text}")
                                                }
                                            }.trim()
                                            current.copy(kind = NoteKind.NOTE, text = merged, items = emptyList())
                                        } else {
                                            val lines = current.text.lines().filter { it.isNotBlank() }
                                            val entries = lines.map { line ->
                                                val done = line.trim().startsWith("[x]", ignoreCase = true)
                                                val clean = line.trim()
                                                    .removePrefix("[x]").removePrefix("[X]")
                                                    .removePrefix("[ ]").removePrefix("- ").trim()
                                                TodoEntry(text = clean, done = done)
                                            }.ifEmpty { listOf(TodoEntry()) }
                                            current.copy(kind = NoteKind.TODO, text = "", items = entries)
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color(0xFFF87171)) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0E1525))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SyncedNoteField(
                key = "${note.id}_title",
                remoteValue = note.title,
                currentRemote = { NotesMcpBridge.getNote(note.id)?.title },
                onValueChange = { v -> NotesMcpBridge.updateNote(note.id) { it.copy(title = v) } },
                placeholder = "Title",
                textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White),
                singleLine = true,
                imeAction = ImeAction.Next,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_editor_title")
            )

            HorizontalDivider(color = Color(0xFF1F2937))

            if (note.kind == NoteKind.NOTE || note.text.isNotEmpty()) {
                SyncedNoteField(
                    key = "${note.id}_body",
                    remoteValue = note.text,
                    currentRemote = { NotesMcpBridge.getNote(note.id)?.text },
                    onValueChange = { v -> NotesMcpBridge.updateNote(note.id) { it.copy(text = v) } },
                    placeholder = if (note.kind == NoteKind.TODO) "Details..." else "Start writing...",
                    textStyle = TextStyle(fontSize = 15.5.sp, lineHeight = 24.sp, color = Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_editor_body")
                )
            }

            if (note.kind == NoteKind.TODO) {
                note.items.forEachIndexed { idx, entry ->
                    key(entry.id) {
                        EditableTodoRow(
                            noteId = note.id,
                            entry = entry,
                            requestFocus = pendingFocusItemId == entry.id,
                            onFocused = { pendingFocusItemId = null },
                            onNext = { insertItem(idx + 1) },
                            onRemove = {
                                NotesMcpBridge.updateNote(note.id) { current ->
                                    current.copy(items = current.items.filterNot { it.id == entry.id })
                                }
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { insertItem(note.items.size) }
                        .padding(vertical = 10.dp)
                        .testTag("note_editor_add_item"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add checklist item", tint = Color(0xFF38BDF8))
                    Spacer(Modifier.width(10.dp))
                    Text("Add item", color = Color(0xFF94A3B8), fontSize = 14.5.sp)
                }

                if (note.items.isNotEmpty()) {
                    Text(
                        text = "${note.doneCount} of ${note.items.size} completed",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Edited ${formatRelativeTime(note.updatedAt)}",
                color = Color(0xFF64748B),
                fontSize = 11.5.sp
            )
        }
    }
}

@Composable
private fun EditableTodoRow(
    noteId: String,
    entry: TodoEntry,
    requestFocus: Boolean,
    onFocused: () -> Unit,
    onNext: () -> Unit,
    onRemove: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            runCatching { focusRequester.requestFocus() }
            onFocused()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = entry.done,
            onCheckedChange = { checked ->
                NotesMcpBridge.updateNote(noteId) { current ->
                    current.copy(items = current.items.map { if (it.id == entry.id) it.copy(done = checked) else it })
                }
            },
            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF10B981))
        )
        SyncedNoteField(
            key = entry.id,
            remoteValue = entry.text,
            currentRemote = { NotesMcpBridge.getNote(noteId)?.items?.firstOrNull { it.id == entry.id }?.text },
            onValueChange = { v ->
                NotesMcpBridge.updateNote(noteId) { current ->
                    current.copy(items = current.items.map { if (it.id == entry.id) it.copy(text = v) else it })
                }
            },
            placeholder = "Checklist item",
            textStyle = TextStyle(
                fontSize = 15.5.sp,
                color = if (entry.done) Color(0xFF64748B) else Color.White,
                textDecoration = if (entry.done) TextDecoration.LineThrough else null
            ),
            singleLine = true,
            imeAction = ImeAction.Next,
            onImeAction = onNext,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Remove item", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SyncedNoteField(
    key: String,
    remoteValue: String,
    currentRemote: () -> String?,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null
) {
    var localText by remember(key) { mutableStateOf(remoteValue) }
    LaunchedEffect(key, remoteValue) {
        val latest = currentRemote()
        if (latest != null && latest != localText) localText = latest
    }
    BasicTextField(
        value = localText,
        onValueChange = {
            localText = it
            onValueChange(it)
        },
        textStyle = textStyle,
        cursorBrush = SolidColor(Color(0xFF38BDF8)),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction?.invoke() ?: defaultKeyboardAction(ImeAction.Next) }
        ),
        modifier = modifier,
        decorationBox = { inner ->
            Box {
                if (localText.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle.copy(color = Color(0xFF475569), textDecoration = null)
                    )
                }
                inner()
            }
        }
    )
}

private fun formatRelativeTime(millis: Long): String {
    val now = System.currentTimeMillis()
    if (now - millis < DateUtils.MINUTE_IN_MILLIS) return "just now"
    return DateUtils.getRelativeTimeSpanString(
        millis,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}
