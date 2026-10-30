package com.dusrel.notes.presentation.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dusrel.notes.presentation.theme.getNoteCardTheme
import com.dusrel.notes.data.work.ExactAlarmScheduler
import com.dusrel.notes.data.work.cancelReminder
import com.dusrel.notes.data.work.scheduleReminder
import com.dusrel.notes.domain.list.TodoFilter
import com.dusrel.notes.domain.list.filterTodosByCompletion
import com.dusrel.notes.domain.list.listMetaLine
import com.dusrel.notes.domain.list.listSnippet
import com.dusrel.notes.domain.list.todoNotes
import com.dusrel.notes.domain.list.todoProgressLabel
import com.dusrel.notes.domain.search.searchNotes
import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.TodoItem
import com.dusrel.notes.domain.reminder.ReminderAlarmScheduler
import com.dusrel.notes.domain.reminder.ReminderScheduler
import com.dusrel.notes.domain.model.NoteType
import com.dusrel.notes.domain.model.SortOrder
import com.dusrel.notes.domain.model.sortNotes
import com.dusrel.notes.presentation.components.SymbolIcon
import com.dusrel.notes.presentation.components.Symbols
import com.dusrel.notes.presentation.viewmodel.ListTab
import com.dusrel.notes.presentation.viewmodel.NotesViewModel

@Composable
fun NotesListScreen(
    viewModel: NotesViewModel,
    onOpenNote: (Long) -> Unit,
    onOpenPrivate: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val notes by viewModel.visibleNotes.collectAsState()
    val allNotes by viewModel.notes.collectAsState()
    val context = LocalContext.current
    // Trashing/deleting must cancel the note's alarm on both paths, or it
    // would still fire for a trashed/deleted note.
    val reminderScheduler = remember {
        ReminderScheduler(
            ExactAlarmScheduler(context),
            object : ReminderAlarmScheduler {
                override fun schedule(noteId: Long, title: String, atMillis: Long) =
                    scheduleReminder(context, noteId, title, atMillis)
                override fun cancel(noteId: Long) = cancelReminder(context, noteId)
            },
        )
    }
    val folders by viewModel.folders.collectAsState()
    val query by viewModel.query.collectAsState()
    val tab by viewModel.listTab.collectAsState()
    val sort by viewModel.sortOrder.collectAsState()
    val folderFilter by viewModel.folderFilterId.collectAsState()
    val todosByNote by viewModel.todosByNote.collectAsState()
    // Observable locale (see EditorScreen): recomposes on config change.
    val locale = LocalConfiguration.current.locales[0]
    var showCreateDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    val isGrid by viewModel.isGridView.collectAsState()
    var confirmDelete by remember { mutableStateOf<Note?>(null) }
    var longPressNote by remember { mutableStateOf<Note?>(null) }
    var moveFolderNote by remember { mutableStateOf<Note?>(null) }
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    fun shareNote(note: Note) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                viewModel.noteToShareText(note, todosByNote[note.id].orEmpty()),
            )
        }
        shareLauncher.launch(Intent.createChooser(intent, "Share note"))
    }
    // Room's Flow starts empty, so an empty database looks like "loading".
    // Show the loading text only briefly, then fall through to the empty state.
    val hasLoaded = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(600)
        hasLoaded.value = true
    }
    LaunchedEffect(allNotes) { if (allNotes.isNotEmpty()) hasLoaded.value = true }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Notes", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenPrivate) { SymbolIcon(Symbols.LOCK) }
            IconButton(onClick = onOpenSettings) { SymbolIcon(Symbols.SETTINGS) }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            label = { Text("Search notes, lists, folders…") },
            leadingIcon = { SymbolIcon(Symbols.SEARCH, size = 20.sp) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            listOf(ListTab.ACTIVE to "Notes", ListTab.ARCHIVED to "Archived", ListTab.TRASH to "Trash").forEach { (t, label) ->
                val selected = tab == t
                if (selected) {
                    Button(onClick = { viewModel.listTab.value = t }, modifier = Modifier.padding(end = 8.dp)) { Text(label) }
                } else {
                    OutlinedButton(onClick = { viewModel.listTab.value = t }, modifier = Modifier.padding(end = 8.dp)) { Text(label) }
                }
            }
        }
        // Folder filter strip: reuses the existing ViewModel folderFilterId
        // (same filter the Folders destination sets). "All" clears it; the
        // Folders tab itself is unchanged and remains the management place.
        if (folders.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val allSelected = folderFilter == null
                if (allSelected) {
                    Button(onClick = { viewModel.folderFilterId.value = null }, modifier = Modifier.padding(end = 8.dp)) { Text("All folders") }
                } else {
                    OutlinedButton(onClick = { viewModel.folderFilterId.value = null }, modifier = Modifier.padding(end = 8.dp)) { Text("All folders") }
                }
                folders.forEach { folder ->
                    val selected = folderFilter == folder.id
                    if (selected) {
                        Button(onClick = { viewModel.folderFilterId.value = folder.id }, modifier = Modifier.padding(end = 8.dp)) { Text(folder.name) }
                    } else {
                        OutlinedButton(onClick = { viewModel.folderFilterId.value = folder.id }, modifier = Modifier.padding(end = 8.dp)) { Text(folder.name) }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = { showSortDialog = true }) {
                SymbolIcon(Symbols.SORT, size = 18.sp)
                Text(" Sort: " + when (sort) {
                    SortOrder.UPDATED_DESC -> "Recent"
                    SortOrder.TITLE_ASC -> "Title"
                    SortOrder.PINNED_FIRST -> "Pinned first"
                })
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { viewModel.toggleGridView() }) {
                SymbolIcon(if (isGrid) Symbols.VIEW_LIST else Symbols.GRID_VIEW, size = 20.sp)
            }
            if (folderFilter != null) {
                OutlinedButton(onClick = { viewModel.folderFilterId.value = null }) {
                    Text("Folder: " + (folders.firstOrNull { it.id == folderFilter }?.name ?: "") + " ✕")
                }
            }
        }
        if (!hasLoaded.value && notes.isEmpty()) {
            Text("Loading notes…", modifier = Modifier.padding(top = 24.dp), style = MaterialTheme.typography.bodyLarge)
        } else if (notes.isEmpty()) {
            if (query.isNotBlank()) {
                // Search no-results: distinct copy naming the query, with a
                // Clear-search action — not the generic "Nothing here yet".
                Column(modifier = Modifier.padding(top = 32.dp)) {
                    SymbolIcon(Symbols.SEARCH, size = 48.sp)
                    Text(
                        "No results for \"$query\"",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "Try a different word, or check another folder or tab.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = { viewModel.query.value = "" },
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("Clear search") }
                }
            } else {
                Column(modifier = Modifier.padding(top = 32.dp)) {
                    SymbolIcon(Symbols.DESCRIPTION, size = 48.sp)
                    Text("Nothing here yet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        when (tab) {
                            ListTab.TRASH -> "Trash is empty. Deleted notes can be restored from here."
                            ListTab.ARCHIVED -> "No archived notes."
                            else -> "Tap + below to write your first note, checklist, or doodle."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else if (isGrid) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(minSize = 160.dp),
                verticalItemSpacing = 10.dp,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).padding(top = 8.dp),
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        tab = tab,
                        isGrid = true,
                        todos = todosByNote[note.id].orEmpty(),
                        folderName = folders.firstOrNull { it.id == note.folderId }?.name,
                        locale = locale,
                        onOpen = { onOpenNote(note.id) },
                        onLongPress = { longPressNote = note },
                        onPin = { viewModel.togglePin(note) },
                        onArchive = { viewModel.setArchived(note, true) },
                        onUnarchive = { viewModel.setArchived(note, false) },
                        onTrash = { viewModel.moveToTrash(note); reminderScheduler.cancelAll(note.id) },
                        onRestore = { viewModel.restoreFromTrash(note) },
                        onDelete = { confirmDelete = note },
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).padding(top = 8.dp),
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        tab = tab,
                        isGrid = false,
                        todos = todosByNote[note.id].orEmpty(),
                        folderName = folders.firstOrNull { it.id == note.folderId }?.name,
                        locale = locale,
                        onOpen = { onOpenNote(note.id) },
                        onLongPress = { longPressNote = note },
                        onPin = { viewModel.togglePin(note) },
                        onArchive = { viewModel.setArchived(note, true) },
                        onUnarchive = { viewModel.setArchived(note, false) },
                        onTrash = { viewModel.moveToTrash(note); reminderScheduler.cancelAll(note.id) },
                        onRestore = { viewModel.restoreFromTrash(note) },
                        onDelete = { confirmDelete = note },
                    )
                }
            }
        }
        Button(onClick = { showCreateDialog = true }, modifier = Modifier.fillMaxWidth()) {
            SymbolIcon(Symbols.ADD, size = 20.sp)
            Text(" New note", modifier = Modifier.padding(start = 4.dp))
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create") },
            text = {
                Column {
                    TextButton(onClick = { showCreateDialog = false; viewModel.createNote(NoteType.NOTE, onOpenNote) }) { Text("Text note") }
                    TextButton(onClick = { showCreateDialog = false; viewModel.createNote(NoteType.TODO, onOpenNote) }) { Text("Checklist") }
                    TextButton(onClick = { showCreateDialog = false; viewModel.createNote(NoteType.DOODLE, onOpenNote) }) { Text("Doodle") }
                }
            },
            confirmButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } }
        )
    }
    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Sort by") },
            text = {
                Column {
                    TextButton(onClick = { viewModel.sortOrder.value = SortOrder.PINNED_FIRST; showSortDialog = false }) { Text("Pinned first") }
                    TextButton(onClick = { viewModel.sortOrder.value = SortOrder.UPDATED_DESC; showSortDialog = false }) { Text("Recently updated") }
                    TextButton(onClick = { viewModel.sortOrder.value = SortOrder.TITLE_ASC; showSortDialog = false }) { Text("Title A–Z") }
                }
            },
            confirmButton = { TextButton(onClick = { showSortDialog = false }) { Text("Close") } }
        )
    }
    confirmDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete permanently?") },
            text = { Text("This note cannot be recovered.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deletePermanently(note); reminderScheduler.cancelAll(note.id); confirmDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } }
        )
    }
    // Long-press actions: only actions that already exist elsewhere
    // (card buttons / editor), no new actions or permissions.
    longPressNote?.let { note ->
        AlertDialog(
            onDismissRequest = { longPressNote = null },
            title = { Text(note.title.ifBlank { "(Untitled)" }) },
            text = {
                Column {
                    when (tab) {
                        ListTab.ACTIVE -> {
                            TextButton(onClick = { viewModel.togglePin(note); longPressNote = null }) {
                                Text(if (note.pinned) "Unpin" else "Pin")
                            }
                            TextButton(onClick = { viewModel.setArchived(note, true); longPressNote = null }) { Text("Archive") }
                            TextButton(onClick = { viewModel.moveToTrash(note); reminderScheduler.cancelAll(note.id); longPressNote = null }) { Text("Move to trash") }
                        }
                        ListTab.ARCHIVED -> {
                            TextButton(onClick = { viewModel.setArchived(note, false); longPressNote = null }) { Text("Unarchive") }
                            TextButton(onClick = { viewModel.moveToTrash(note); reminderScheduler.cancelAll(note.id); longPressNote = null }) { Text("Move to trash") }
                        }
                        ListTab.TRASH -> {
                            TextButton(onClick = { viewModel.restoreFromTrash(note); longPressNote = null }) { Text("Restore") }
                            TextButton(onClick = { confirmDelete = note; longPressNote = null }) { Text("Delete permanently") }
                        }
                    }
                    if (tab != ListTab.TRASH) {
                        TextButton(onClick = { moveFolderNote = note; longPressNote = null }) { Text("Move to folder") }
                        TextButton(onClick = { shareNote(note); longPressNote = null }) { Text("Share") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { longPressNote = null }) { Text("Close") } }
        )
    }
    moveFolderNote?.let { note ->
        AlertDialog(
            onDismissRequest = { moveFolderNote = null },
            title = { Text("Move to folder") },
            text = {
                Column {
                    TextButton(onClick = { viewModel.moveToFolder(note, null); moveFolderNote = null }) { Text("No folder") }
                    folders.forEach { folder ->
                        TextButton(onClick = { viewModel.moveToFolder(note, folder.id); moveFolderNote = null }) { Text(folder.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { moveFolderNote = null }) { Text("Close") } }
        )
    }
}

/**
 * Dedicated Todo destination: a first-class page for checklist notes only.
 * No new database or table — it reuses NoteType.TODO notes and the existing
 * TodoItem rows. Listing, per-item done toggles, and inline add-item all use
 * the existing ViewModel methods; "New checklist" reuses the existing
 * create/checklist editor route via [onCreateChecklist]. Search here is
 * scoped to checklists (the shared [searchNotes] logic, applied after the
 * todo-only filter), so it is NOT the global notes search. Archived and
 * trashed checklists are excluded here on purpose — they stay in the
 * Archived / Trash tabs of the Notes list (see domain/list/TodoList.kt).
 */
@Composable
fun TodoScreen(
    viewModel: NotesViewModel,
    onOpenNote: (Long) -> Unit,
    onCreateChecklist: () -> Unit,
) {
    val notes by viewModel.decryptedNotes.collectAsState()
    val unlocked by viewModel.privateUnlocked.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val todosByNote by viewModel.todosByNote.collectAsState()
    val sort by viewModel.sortOrder.collectAsState()
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    // Same rule as the notes list: trashing must cancel the note's alarm on
    // both paths, or it would still fire for a trashed checklist.
    val reminderScheduler = remember {
        ReminderScheduler(
            ExactAlarmScheduler(context),
            object : ReminderAlarmScheduler {
                override fun schedule(noteId: Long, title: String, atMillis: Long) =
                    scheduleReminder(context, noteId, title, atMillis)
                override fun cancel(noteId: Long) = cancelReminder(context, noteId)
            },
        )
    }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(TodoFilter.ALL) }
    val foldersById = folders.associateBy { it.id }
    val base = sortNotes(todoNotes(notes, unlocked), sort)
    val filtered = filterTodosByCompletion(base, todosByNote, filter).let { list ->
        if (query.isBlank()) list else searchNotes(list, todosByNote, foldersById, query)
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Todo", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Text("${base.size} lists", style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search checklists…") },
            leadingIcon = { SymbolIcon(Symbols.SEARCH, size = 20.sp) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Row(modifier = Modifier.padding(top = 8.dp)) {
            listOf(TodoFilter.ALL to "All", TodoFilter.OPEN to "To do", TodoFilter.DONE to "Done").forEach { (f, label) ->
                val selected = filter == f
                if (selected) {
                    Button(onClick = { filter = f }, modifier = Modifier.padding(end = 8.dp)) { Text(label) }
                } else {
                    OutlinedButton(onClick = { filter = f }, modifier = Modifier.padding(end = 8.dp)) { Text(label) }
                }
            }
        }
        if (base.isEmpty()) {
            Column(modifier = Modifier.padding(top = 32.dp)) {
                SymbolIcon(Symbols.CHECKLIST, size = 48.sp)
                Text("No checklists yet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                Text("Tap New checklist below to start one, or make a checklist from the Notes list.", style = MaterialTheme.typography.bodyMedium)
            }
        } else if (filtered.isEmpty()) {
            Column(modifier = Modifier.padding(top = 32.dp)) {
                SymbolIcon(Symbols.SEARCH, size = 48.sp)
                Text(
                    if (query.isNotBlank()) "No results for \"$query\"" else "No ${when (filter) { TodoFilter.DONE -> "done"; TodoFilter.OPEN -> "open"; TodoFilter.ALL -> "" }} checklists",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text("Try a different word or another filter.", style = MaterialTheme.typography.bodyMedium)
                if (query.isNotBlank()) {
                    Button(onClick = { query = "" }, modifier = Modifier.padding(top = 12.dp)) { Text("Clear search") }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                items(filtered, key = { it.id }) { note ->
                    TodoCard(
                        note = note,
                        folders = folders,
                        locale = locale,
                        viewModel = viewModel,
                        reminderScheduler = reminderScheduler,
                        onOpen = { onOpenNote(note.id) },
                    )
                }
            }
        }
        Button(onClick = onCreateChecklist, modifier = Modifier.fillMaxWidth()) {
            SymbolIcon(Symbols.ADD, size = 20.sp)
            Text(" New checklist", modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun TodoCard(
    note: Note,
    folders: List<com.dusrel.notes.domain.model.Folder>,
    locale: java.util.Locale,
    viewModel: NotesViewModel,
    reminderScheduler: ReminderScheduler,
    onOpen: () -> Unit,
) {
    // Live per-note items from the repository (not the list's todos cache,
    // which only refreshes when the notes list itself changes).
    val todos by viewModel.todosFor(note.id).collectAsState()
    var newItemText by remember { mutableStateOf("") }
    val folderName = folders.firstOrNull { it.id == note.folderId }?.name
    val isDark = isSystemInDarkTheme()
    val cardTheme = getNoteCardTheme(note.color, isDark)
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpen() },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardTheme.borderColor),
        colors = CardDefaults.cardColors(
            containerColor = cardTheme.containerColor,
            contentColor = cardTheme.onContainerColor
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolIcon(Symbols.CHECKLIST, size = 20.sp)
                Text(
                    note.title.ifBlank { "(Untitled)" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                if (note.pinned) SymbolIcon(Symbols.PUSH_PIN, size = 18.sp)
                if (note.isPrivate) SymbolIcon(Symbols.LOCK, size = 18.sp)
                IconButton(onClick = { viewModel.moveToTrash(note); reminderScheduler.cancelAll(note.id) }) { SymbolIcon(Symbols.DELETE, size = 20.sp) }
            }
            val dateText = java.text.SimpleDateFormat("dd MMM yyyy", locale)
                .format(java.util.Date(note.updatedAt))
            Text(
                listMetaLine(dateText, todoProgressLabel(todos)),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (!folderName.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    SymbolIcon(Symbols.FOLDER, size = 14.sp)
                    Text(folderName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
                }
            }
            todos.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = item.done, onCheckedChange = { viewModel.toggleTodo(item) })
                    Text(item.text, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.deleteTodo(item) }) { SymbolIcon(Symbols.DELETE, size = 18.sp) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                OutlinedTextField(
                    value = newItemText,
                    onValueChange = { newItemText = it },
                    label = { Text("New item") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (newItemText.isNotBlank()) {
                        viewModel.addTodo(note.id, newItemText.trim())
                        newItemText = ""
                    }
                }) { Text("Add") }
            }
            TextButton(onClick = onOpen) { Text("Open in editor") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: Note,
    tab: ListTab,
    isGrid: Boolean = false,
    todos: List<TodoItem>,
    folderName: String? = null,
    locale: java.util.Locale,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val cardTheme = getNoteCardTheme(note.color, isDark)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .combinedClickable(onClick = { onOpen() }, onLongClick = { onLongPress() }),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardTheme.borderColor),
        colors = CardDefaults.cardColors(
            containerColor = cardTheme.containerColor,
            contentColor = cardTheme.onContainerColor
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolIcon(
                    when (note.type) {
                        NoteType.TODO -> Symbols.CHECKLIST
                        NoteType.DOODLE -> Symbols.DRAW
                        else -> Symbols.DESCRIPTION
                    },
                    size = 18.sp,
                    color = cardTheme.secondaryTextColor
                )
                Text(
                    note.title.ifBlank { "(Untitled)" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = cardTheme.onContainerColor,
                    maxLines = if (isGrid) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 6.dp)
                )
                if (note.pinned) {
                    SymbolIcon(
                        Symbols.PUSH_PIN,
                        size = 16.sp,
                        color = Color(0xFFE5A93C),
                        filled = true
                    )
                }
                if (note.isPrivate) {
                    SymbolIcon(
                        Symbols.LOCK,
                        size = 16.sp,
                        color = cardTheme.secondaryTextColor
                    )
                }
                if (note.reminderTime != null) {
                    SymbolIcon(
                        Symbols.ALARM,
                        size = 16.sp,
                        color = cardTheme.secondaryTextColor
                    )
                }
            }
            val preview = listSnippet(note)
            if (preview.isNotBlank()) {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cardTheme.secondaryTextColor,
                    maxLines = if (isGrid) 6 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (note.type == NoteType.TODO && todos.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    todos.take(if (isGrid) 3 else 4).forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 1.dp)
                        ) {
                            SymbolIcon(
                                if (item.done) Symbols.CHECK_CIRCLE else Symbols.CHECK,
                                size = 13.sp,
                                color = if (item.done) cardTheme.secondaryTextColor.copy(alpha = 0.5f) else cardTheme.secondaryTextColor,
                                filled = item.done
                            )
                            Text(
                                item.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (item.done) cardTheme.secondaryTextColor.copy(alpha = 0.5f) else cardTheme.onContainerColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    if (todos.size > (if (isGrid) 3 else 4)) {
                        Text(
                            "+${todos.size - (if (isGrid) 3 else 4)} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = cardTheme.secondaryTextColor.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
            val dateText = java.text.SimpleDateFormat("dd MMM yyyy", locale)
                .format(java.util.Date(note.updatedAt))
            val progress = if (note.type == NoteType.TODO) todoProgressLabel(todos) else null
            Text(
                listMetaLine(dateText, progress),
                style = MaterialTheme.typography.bodySmall,
                color = cardTheme.secondaryTextColor.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 6.dp),
            )
            if (!folderName.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    SymbolIcon(Symbols.FOLDER, size = 13.sp, color = cardTheme.secondaryTextColor)
                    Text(
                        folderName,
                        style = MaterialTheme.typography.bodySmall,
                        color = cardTheme.secondaryTextColor,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            Row(modifier = Modifier.padding(top = 4.dp)) {
                when (tab) {
                    ListTab.ACTIVE -> {
                        IconButton(onClick = onPin) {
                            SymbolIcon(
                                Symbols.PUSH_PIN,
                                size = 18.sp,
                                filled = note.pinned,
                                color = if (note.pinned) Color(0xFFE5A93C) else cardTheme.secondaryTextColor
                            )
                        }
                        IconButton(onClick = onArchive) {
                            SymbolIcon(Symbols.ARCHIVE, size = 18.sp, color = cardTheme.secondaryTextColor)
                        }
                        IconButton(onClick = onTrash) {
                            SymbolIcon(Symbols.DELETE, size = 18.sp, color = cardTheme.secondaryTextColor)
                        }
                    }
                    ListTab.ARCHIVED -> {
                        IconButton(onClick = onUnarchive) {
                            SymbolIcon(Symbols.UNARCHIVE, size = 18.sp, color = cardTheme.secondaryTextColor)
                        }
                        IconButton(onClick = onTrash) {
                            SymbolIcon(Symbols.DELETE, size = 18.sp, color = cardTheme.secondaryTextColor)
                        }
                    }
                    ListTab.TRASH -> {
                        IconButton(onClick = onRestore) {
                            SymbolIcon(Symbols.RESTORE_FROM_TRASH, size = 18.sp, color = cardTheme.secondaryTextColor)
                        }
                        IconButton(onClick = onDelete) {
                            SymbolIcon(Symbols.DELETE, size = 18.sp, color = cardTheme.secondaryTextColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FoldersScreen(viewModel: NotesViewModel, onOpenFolder: (Long) -> Unit) {
    val folders by viewModel.folders.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val unlocked by viewModel.privateUnlocked.collectAsState()
    // Locked private notes must not leak into folder counts or folder-delete moves.
    val visibleForFolders = notes.filter { !it.isPrivate || unlocked }
    var newName by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Folders", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = { showCreate = true }, modifier = Modifier.padding(top = 8.dp)) {
            SymbolIcon(Symbols.CREATE_NEW_FOLDER, size = 20.sp)
            Text(" New folder", modifier = Modifier.padding(start = 4.dp))
        }
        if (folders.isEmpty()) {
            Column(modifier = Modifier.padding(top = 32.dp)) {
                SymbolIcon(Symbols.FOLDER, size = 48.sp)
                Text("No folders yet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                Text("Create a folder, then move notes into it from the editor.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                items(folders, key = { it.id }) { folder ->
                    val count = visibleForFolders.count { it.folderId == folder.id && !it.trashed }
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpenFolder(folder.id) }) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            SymbolIcon(Symbols.FOLDER, size = 22.sp)
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(folder.name, style = MaterialTheme.typography.titleMedium)
                                Text("$count notes", style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = {
                                visibleForFolders.filter { it.folderId == folder.id }.forEach { viewModel.moveToFolder(it, null) }
                                viewModel.deleteFolder(folder)
                            }) { SymbolIcon(Symbols.DELETE, size = 20.sp) }
                        }
                    }
                }
            }
        }
    }
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Folder name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) viewModel.createFolder(newName.trim())
                    newName = ""; showCreate = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun PrivateScreen(viewModel: NotesViewModel, onOpenNote: (Long) -> Unit) {
    // Decrypted notes: raw notes store "" titles for encrypted private notes.
    val notes by viewModel.decryptedNotes.collectAsState()
    val unlocked by viewModel.privateUnlocked.collectAsState()
    var pinState by remember { mutableStateOf<String?>(null) } // null = unknown yet
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { pinState = if (viewModel.hasPin()) "enter" else "set" }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Private notes", style = MaterialTheme.typography.headlineMedium)
        when {
            unlocked -> {
                val privateNotes = notes.filter { it.isPrivate && !it.trashed }
                if (privateNotes.isEmpty()) {
                    // Same icon + title styling as the other empty states.
                    Column(modifier = Modifier.padding(top = 32.dp)) {
                        SymbolIcon(Symbols.LOCK, size = 48.sp)
                        Text("No private notes yet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                        Text("Mark a note Private in its editor.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                        items(privateNotes, key = { it.id }) { note ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpenNote(note.id) }) {
                                Text(note.title.ifBlank { "(Untitled)" }, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                Button(onClick = { viewModel.lockPrivate() }, modifier = Modifier.padding(top = 16.dp)) { Text("Lock private notes") }
            }
            pinState == "set" -> {
                Text("Set a PIN to protect private notes. The PIN itself is never stored — it derives an encryption key (AES-GCM, PBKDF2) that encrypts private notes on this device. If you forget the PIN, private notes cannot be recovered.", modifier = Modifier.padding(top = 16.dp))
                OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("New PIN") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = confirm, onValueChange = { confirm = it }, label = { Text("Confirm PIN") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = {
                    if (pin.length < 4) error = "PIN must be at least 4 characters."
                    else if (pin != confirm) error = "PINs do not match."
                    else {
                        // setPin is suspend; launch via LaunchedEffect-free approach:
                        error = ""
                        // Handled by caller below through a remembered flag.
                        pinState = "saving:$pin"
                    }
                }, modifier = Modifier.padding(top = 8.dp)) { Text("Set PIN") }
            }
            pinState != null && pinState!!.startsWith("saving:") -> {
                val toSave = pinState!!.removePrefix("saving:")
                LaunchedEffect(toSave) {
                    viewModel.setPin(toSave)
                    viewModel.unlockWithPin(toSave)
                }
                Text("Saving PIN…", modifier = Modifier.padding(top = 16.dp))
            }
            else -> {
                Text("Enter your PIN to view private notes.", modifier = Modifier.padding(top = 16.dp))
                OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("PIN") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = {
                    // Validation runs in the LaunchedEffect branch below via flag.
                    pinState = "checking:$pin"
                }, modifier = Modifier.padding(top = 8.dp)) { Text("Unlock") }
                if (pinState != null && pinState!!.startsWith("checking:")) {
                    val toCheck = pinState!!.removePrefix("checking:")
                    LaunchedEffect(toCheck) {
                        if (viewModel.unlockWithPin(toCheck)) {
                            pinState = "enter"
                        } else {
                            val remaining = viewModel.lockoutRemainingSeconds()
                            error = if (remaining > 0) {
                                "Too many wrong attempts. Try again in $remaining seconds."
                            } else {
                                "Wrong PIN."
                            }
                            pinState = "enter"
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RemindersScreen(viewModel: NotesViewModel, onOpenNote: (Long) -> Unit) {
    val notes by viewModel.notes.collectAsState()
    val unlocked by viewModel.privateUnlocked.collectAsState()
    val context = LocalContext.current
    // Observable locale (see EditorScreen): recomposes on config change.
    val locale = LocalConfiguration.current.locales[0]
    val reminderScheduler = remember {
        ReminderScheduler(
            ExactAlarmScheduler(context),
            object : ReminderAlarmScheduler {
                override fun schedule(noteId: Long, title: String, atMillis: Long) =
                    scheduleReminder(context, noteId, title, atMillis)
                override fun cancel(noteId: Long) = cancelReminder(context, noteId)
            },
        )
    }
    val reminders = notes.filter {
        it.reminderTime != null && !it.trashed && (!it.isPrivate || unlocked)
    }.sortedBy { it.reminderTime }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Reminders", style = MaterialTheme.typography.headlineMedium)
        if (reminders.isEmpty()) {
            Column(modifier = Modifier.padding(top = 32.dp)) {
                SymbolIcon(Symbols.ALARM, size = 48.sp)
                Text("No reminders", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                Text("Set a reminder on any note from its editor.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(reminders, key = { it.id }) { note ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpenNote(note.id) }) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(note.title.ifBlank { "(Untitled)" }, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", locale)
                                        .format(java.util.Date(note.reminderTime!!)),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    if (note.reminderExact) "exact" else "best-effort",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            IconButton(onClick = {
                                viewModel.updateNote(note.copy(reminderTime = null))
                                reminderScheduler.cancelAll(note.id)
                            }) { SymbolIcon(Symbols.DELETE, size = 20.sp) }
                        }
                    }
                }
            }
        }
    }
}
