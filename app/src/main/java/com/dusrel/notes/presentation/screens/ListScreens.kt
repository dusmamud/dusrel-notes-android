package com.dusrel.notes.presentation.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dusrel.notes.data.work.ExactAlarmScheduler
import com.dusrel.notes.data.work.cancelReminder
import com.dusrel.notes.data.work.scheduleReminder
import com.dusrel.notes.domain.list.TodoFilter
import com.dusrel.notes.domain.list.filterTodosByCompletion
import com.dusrel.notes.domain.list.listMetaLine
import com.dusrel.notes.domain.list.listSnippet
import com.dusrel.notes.domain.list.todoNotes
import com.dusrel.notes.domain.list.todoProgressLabel
import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.NoteType
import com.dusrel.notes.domain.model.SortOrder
import com.dusrel.notes.domain.model.TodoItem
import com.dusrel.notes.domain.model.sortNotes
import com.dusrel.notes.domain.reminder.ReminderAlarmScheduler
import com.dusrel.notes.domain.reminder.ReminderScheduler
import com.dusrel.notes.domain.search.searchNotes
import com.dusrel.notes.presentation.components.SymbolIcon
import com.dusrel.notes.presentation.components.Symbols
import com.dusrel.notes.presentation.theme.CardBgDark
import com.dusrel.notes.presentation.theme.CardBgLight
import com.dusrel.notes.presentation.theme.ThemeMainGold
import com.dusrel.notes.presentation.theme.TodoCheckboxOutline
import com.dusrel.notes.presentation.theme.getNoteCardTheme
import com.dusrel.notes.presentation.viewmodel.ListTab
import com.dusrel.notes.presentation.viewmodel.NotesViewModel
import kotlinx.coroutines.launch

enum class HomeTopTab { NOTES, TASKS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    viewModel: NotesViewModel,
    onOpenNote: (Long) -> Unit,
    onOpenPrivate: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFolders: () -> Unit,
    onOpenReminders: () -> Unit,
) {
    val notes by viewModel.visibleNotes.collectAsState()
    val allNotes by viewModel.notes.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val query by viewModel.query.collectAsState()
    val tab by viewModel.listTab.collectAsState()
    val sort by viewModel.sortOrder.collectAsState()
    val folderFilter by viewModel.folderFilterId.collectAsState()
    val todosByNote by viewModel.todosByNote.collectAsState()
    val isGrid by viewModel.isGridView.collectAsState()
    val isDark = isSystemInDarkTheme()
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current

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

    var selectedTopTab by remember { mutableStateOf(HomeTopTab.NOTES) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var actionNoteSheet by remember { mutableStateOf<Note?>(null) }
    var moveFolderNote by remember { mutableStateOf<Note?>(null) }
    var confirmDeleteNote by remember { mutableStateOf<Note?>(null) }
    var newQuickTaskText by remember { mutableStateOf("") }

    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    fun shareNote(note: Note) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, viewModel.noteToShareText(note, todosByNote[note.id].orEmpty()))
        }
        shareLauncher.launch(Intent.createChooser(intent, "Share note"))
    }

    val activeNotesCount = allNotes.count { !it.trashed && !it.archived && it.type != NoteType.TODO }
    val activeTasksCount = allNotes.count { !it.trashed && !it.archived && it.type == NoteType.TODO }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar / Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Segmented Top Switcher: Notes | Tasks
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Notes",
                            fontSize = if (selectedTopTab == HomeTopTab.NOTES) 28.sp else 20.sp,
                            fontWeight = if (selectedTopTab == HomeTopTab.NOTES) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTopTab == HomeTopTab.NOTES) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedTopTab = HomeTopTab.NOTES }
                                .padding(end = 16.dp, top = 4.dp, bottom = 4.dp)
                        )
                        Text(
                            text = "Tasks",
                            fontSize = if (selectedTopTab == HomeTopTab.TASKS) 28.sp else 20.sp,
                            fontWeight = if (selectedTopTab == HomeTopTab.TASKS) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTopTab == HomeTopTab.TASKS) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedTopTab = HomeTopTab.TASKS }
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }

                    // Top Action Icons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                            SymbolIcon(Symbols.SEARCH, size = 22.sp, color = MaterialTheme.colorScheme.onBackground)
                        }
                        if (selectedTopTab == HomeTopTab.NOTES) {
                            IconButton(onClick = { viewModel.toggleGridView() }) {
                                SymbolIcon(
                                    if (isGrid) Symbols.VIEW_LIST else Symbols.GRID_VIEW,
                                    size = 22.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            IconButton(onClick = onOpenFolders) {
                                SymbolIcon(Symbols.FOLDER, size = 22.sp, color = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                SymbolIcon(Symbols.MORE_VERT, size = 22.sp, color = MaterialTheme.colorScheme.onBackground)
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sort notes") },
                                    leadingIcon = { SymbolIcon(Symbols.SORT, size = 18.sp) },
                                    onClick = { showOverflowMenu = false; showSortDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Private Vault") },
                                    leadingIcon = { SymbolIcon(Symbols.LOCK, size = 18.sp) },
                                    onClick = { showOverflowMenu = false; onOpenPrivate() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reminders") },
                                    leadingIcon = { SymbolIcon(Symbols.ALARM, size = 18.sp) },
                                    onClick = { showOverflowMenu = false; onOpenReminders() }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (tab == ListTab.TRASH) "Show active notes" else "Trash") },
                                    leadingIcon = { SymbolIcon(Symbols.DELETE, size = 18.sp) },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.listTab.value = if (tab == ListTab.TRASH) ListTab.ACTIVE else ListTab.TRASH
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = { SymbolIcon(Symbols.SETTINGS, size = 18.sp) },
                                    onClick = { showOverflowMenu = false; onOpenSettings() }
                                )
                            }
                        }
                    }
                }

                // Expandable Search Bar Capsule
                AnimatedVisibility(visible = isSearchExpanded) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { viewModel.query.value = it },
                            placeholder = { Text("Search notes and tasks…", fontSize = 14.sp) },
                            leadingIcon = { SymbolIcon(Symbols.SEARCH, size = 18.sp) },
                            trailingIcon = {
                                if (query.isNotBlank()) {
                                    IconButton(onClick = { viewModel.query.value = "" }) {
                                        SymbolIcon(Symbols.CLOSE, size = 18.sp)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(22.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            singleLine = true
                        )
                    }
                }

                // Folder & Status Filter Strip
                if (selectedTopTab == HomeTopTab.NOTES && folders.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isAllSelected = folderFilter == null
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isAllSelected) ThemeMainGold else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { viewModel.folderFilterId.value = null }
                        ) {
                            Text(
                                "All (${allNotes.size})",
                                fontSize = 12.sp,
                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isAllSelected) Color(0xFF2D261A) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        folders.forEach { folder ->
                            val isSelected = folderFilter == folder.id
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) ThemeMainGold else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    viewModel.folderFilterId.value = if (isSelected) null else folder.id
                                }
                            ) {
                                Text(
                                    folder.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF2D261A) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                if (tab == ListTab.TRASH) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Trash view active", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = { viewModel.listTab.value = ListTab.ACTIVE }) {
                                Text("Back to Notes", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Main Content Area
            if (selectedTopTab == HomeTopTab.NOTES) {
                // Notes View
                val filteredNotes = notes.filter { it.type != NoteType.TODO }
                if (filteredNotes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SymbolIcon(Symbols.DESCRIPTION, size = 52.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            Text(
                                if (query.isNotBlank()) "No notes matching \"$query\""
                                else if (tab == ListTab.TRASH) "Trash is empty"
                                else "No notes here yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                            Text(
                                "Tap + below to create a quick note or sketch.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else if (isGrid) {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Adaptive(minSize = 160.dp),
                        verticalItemSpacing = 10.dp,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 100.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredNotes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                isGrid = true,
                                todos = todosByNote[note.id].orEmpty(),
                                folderName = folders.firstOrNull { it.id == note.folderId }?.name,
                                locale = locale,
                                onOpen = { onOpenNote(note.id) },
                                onLongPress = { actionNoteSheet = note },
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 100.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredNotes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                isGrid = false,
                                todos = todosByNote[note.id].orEmpty(),
                                folderName = folders.firstOrNull { it.id == note.folderId }?.name,
                                locale = locale,
                                onOpen = { onOpenNote(note.id) },
                                onLongPress = { actionNoteSheet = note },
                            )
                        }
                    }
                }
            } else {
                // Tasks / Todo View
                val taskNotes = notes.filter { it.type == NoteType.TODO }
                TasksView(
                    taskNotes = taskNotes,
                    todosByNote = todosByNote,
                    folders = folders,
                    locale = locale,
                    viewModel = viewModel,
                    reminderScheduler = reminderScheduler,
                    onOpenNote = onOpenNote,
                    newQuickTaskText = newQuickTaskText,
                    onNewQuickTaskTextChange = { newQuickTaskText = it },
                    onAddQuickTask = {
                        if (newQuickTaskText.isNotBlank()) {
                            val activeNote = taskNotes.firstOrNull { !it.trashed && !it.archived }
                            if (activeNote != null) {
                                viewModel.addTodo(activeNote.id, newQuickTaskText.trim())
                            } else {
                                viewModel.createNote(NoteType.TODO) { newId ->
                                    viewModel.addTodo(newId, newQuickTaskText.trim())
                                }
                            }
                            newQuickTaskText = ""
                        }
                    }
                )
            }
        }

        // Dual Floating Action Buttons (FAB) at Bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Companion Secondary FAB (Doodle / Sketch)
            if (selectedTopTab == HomeTopTab.NOTES) {
                FloatingActionButton(
                    onClick = { viewModel.createNote(NoteType.DOODLE, onOpenNote) },
                    shape = CircleShape,
                    containerColor = if (isDark) CardBgDark else CardBgLight,
                    contentColor = ThemeMainGold,
                    elevation = FloatingActionButtonDefaults.elevation(3.dp),
                    modifier = Modifier.size(52.dp)
                ) {
                    SymbolIcon(Symbols.DRAW, size = 22.sp, color = ThemeMainGold)
                }
            }

            // Primary Main FAB (+ New Note or Task)
            FloatingActionButton(
                onClick = {
                    if (selectedTopTab == HomeTopTab.NOTES) {
                        viewModel.createNote(NoteType.NOTE, onOpenNote)
                    } else {
                        viewModel.createNote(NoteType.TODO, onOpenNote)
                    }
                },
                shape = CircleShape,
                containerColor = ThemeMainGold,
                contentColor = Color(0xFF2D261A),
                elevation = FloatingActionButtonDefaults.elevation(4.dp),
                modifier = Modifier.size(58.dp)
            ) {
                SymbolIcon(Symbols.ADD, size = 30.sp, color = Color(0xFF2D261A))
            }
        }
    }

    // Sort Dialog
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

    // Note Action Bottom Sheet (Long-Press)
    actionNoteSheet?.let { note ->
        ModalBottomSheet(
            onDismissRequest = { actionNoteSheet = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(
                    text = note.title.ifBlank { "Untitled Note" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.togglePin(note)
                            actionNoteSheet = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SymbolIcon(Symbols.PUSH_PIN, size = 20.sp, filled = note.pinned)
                    Text(if (note.pinned) "  Unpin note" else "  Pin note", fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            moveFolderNote = note
                            actionNoteSheet = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SymbolIcon(Symbols.FOLDER, size = 20.sp)
                    Text("  Move to folder", fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setArchived(note, !note.archived)
                            actionNoteSheet = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SymbolIcon(if (note.archived) Symbols.UNARCHIVE else Symbols.ARCHIVE, size = 20.sp)
                    Text(if (note.archived) "  Unarchive" else "  Archive", fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            shareNote(note)
                            actionNoteSheet = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SymbolIcon(Symbols.SHARE, size = 20.sp)
                    Text("  Share note", fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (note.trashed) {
                                confirmDeleteNote = note
                            } else {
                                viewModel.moveToTrash(note)
                                reminderScheduler.cancelAll(note.id)
                            }
                            actionNoteSheet = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SymbolIcon(Symbols.DELETE, size = 20.sp, color = MaterialTheme.colorScheme.error)
                    Text(
                        if (note.trashed) "  Delete permanently" else "  Move to trash",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Move to folder dialog
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

    // Confirm Delete Dialog
    confirmDeleteNote?.let { note ->
        AlertDialog(
            onDismissRequest = { confirmDeleteNote = null },
            title = { Text("Delete permanently?") },
            text = { Text("This note cannot be recovered.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePermanently(note)
                    reminderScheduler.cancelAll(note.id)
                    confirmDeleteNote = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteNote = null }) { Text("Keep") } }
        )
    }
}

/**
 * Authentic Clean Paper Note Card.
 * Eliminates ugly row of buttons inside the card; long-press opens actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: Note,
    isGrid: Boolean = true,
    todos: List<TodoItem>,
    folderName: String? = null,
    locale: java.util.Locale,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val cardTheme = getNoteCardTheme(note.color, isDark)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardTheme.borderColor),
        colors = CardDefaults.cardColors(
            containerColor = cardTheme.containerColor,
            contentColor = cardTheme.onContainerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Title Header with Badges
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title.ifBlank { "(Untitled)" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                    color = cardTheme.onContainerColor,
                    maxLines = if (isGrid) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (note.pinned) {
                    SymbolIcon(
                        Symbols.PUSH_PIN,
                        size = 15.sp,
                        color = ThemeMainGold,
                        filled = true,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                if (note.isPrivate) {
                    SymbolIcon(
                        Symbols.LOCK,
                        size = 14.sp,
                        color = cardTheme.secondaryTextColor,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            // Body Snippet Text
            val snippet = listSnippet(note)
            if (snippet.isNotBlank() && note.type != NoteType.TODO) {
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 18.sp),
                    color = cardTheme.secondaryTextColor,
                    maxLines = if (isGrid) 6 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // Checklist preview if NoteType.TODO
            if (note.type == NoteType.TODO && todos.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    todos.take(if (isGrid) 3 else 4).forEach { todo ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.5.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (todo.done) ThemeMainGold else Color.Transparent)
                                    .border(
                                        width = if (todo.done) 0.dp else 1.5.dp,
                                        color = if (todo.done) Color.Transparent else TodoCheckboxOutline,
                                        shape = RoundedCornerShape(4.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (todo.done) {
                                    SymbolIcon(Symbols.CHECK, size = 10.sp, color = Color(0xFF2D261A))
                                }
                            }
                            Text(
                                text = todo.text,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.5.sp,
                                    textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None
                                ),
                                color = if (todo.done) cardTheme.secondaryTextColor.copy(alpha = 0.5f) else cardTheme.onContainerColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                    if (todos.size > (if (isGrid) 3 else 4)) {
                        Text(
                            "+${todos.size - (if (isGrid) 3 else 4)} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = cardTheme.secondaryTextColor.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // Bottom Metadata Line: Date/Time + Indicators
            val dateText = java.text.SimpleDateFormat("dd MMM", locale).format(java.util.Date(note.updatedAt))
            val meta = if (note.type == NoteType.TODO) listMetaLine(dateText, todoProgressLabel(todos)) else dateText
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = cardTheme.secondaryTextColor.copy(alpha = 0.75f)
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (note.type == NoteType.DOODLE) {
                        SymbolIcon(Symbols.DRAW, size = 13.sp, color = cardTheme.secondaryTextColor.copy(alpha = 0.75f))
                    }
                    if (note.reminderTime != null) {
                        SymbolIcon(Symbols.ALARM, size = 13.sp, color = ThemeMainGold)
                    }
                    if (!folderName.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SymbolIcon(Symbols.FOLDER, size = 12.sp, color = cardTheme.secondaryTextColor.copy(alpha = 0.75f))
                            Text(
                                text = folderName,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = cardTheme.secondaryTextColor.copy(alpha = 0.75f),
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tasks View matching reference Tasks / Todo layout with interactive checkboxes and strike-through.
 */
@Composable
private fun TasksView(
    taskNotes: List<Note>,
    todosByNote: Map<Long, List<TodoItem>>,
    folders: List<com.dusrel.notes.domain.model.Folder>,
    locale: java.util.Locale,
    viewModel: NotesViewModel,
    reminderScheduler: ReminderScheduler,
    onOpenNote: (Long) -> Unit,
    newQuickTaskText: String,
    onNewQuickTaskTextChange: (String) -> Unit,
    onAddQuickTask: () -> Unit,
) {
    var filter by remember { mutableStateOf(TodoFilter.ALL) }
    val allTodoItems = taskNotes.flatMap { todosByNote[it.id].orEmpty() }
    val uncompletedItems = allTodoItems.filter { !it.done }
    val completedItems = allTodoItems.filter { it.done }
    var showCompleted by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Quick task input field
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = newQuickTaskText,
                onValueChange = onNewQuickTaskTextChange,
                placeholder = { Text("Write a task…", fontSize = 14.sp) },
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.weight(1f).height(46.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onAddQuickTask,
                shape = CircleShape,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = ThemeMainGold,
                    contentColor = Color(0xFF2D261A)
                ),
                modifier = Modifier.size(46.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                SymbolIcon(Symbols.ADD, size = 24.sp, color = Color(0xFF2D261A))
            }
        }

        if (allTodoItems.isEmpty() && taskNotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SymbolIcon(Symbols.CHECKLIST, size = 52.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Text(
                        "No tasks yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        "Type a task above or tap + to start a checklist.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Open / Uncompleted Tasks
                items(uncompletedItems, key = { it.id }) { item ->
                    TaskItemRow(item = item, onToggle = { viewModel.toggleTodo(item) }, onDelete = { viewModel.deleteTodo(item) })
                }

                // Completed Tasks Section
                if (completedItems.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCompleted = !showCompleted }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                "Completed (${completedItems.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            SymbolIcon(
                                if (showCompleted) Symbols.EXPAND_LESS else Symbols.EXPAND_MORE,
                                size = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    if (showCompleted) {
                        items(completedItems, key = { it.id }) { item ->
                            TaskItemRow(item = item, onToggle = { viewModel.toggleTodo(item) }, onDelete = { viewModel.deleteTodo(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskSquircleCheckbox(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) ThemeMainGold else Color.Transparent)
            .border(
                width = if (checked) 0.dp else 2.dp,
                color = if (checked) Color.Transparent else TodoCheckboxOutline,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onCheckedChange),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            SymbolIcon(
                Symbols.CHECK,
                size = 14.sp,
                color = Color(0xFF2D261A),
            )
        }
    }
}

@Composable
private fun TaskItemRow(
    item: TodoItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isDark) CardBgDark else Color(0xFFEAEAEA)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            TaskSquircleCheckbox(
                checked = item.done,
                onCheckedChange = onToggle,
            )
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = if (item.done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                SymbolIcon(Symbols.CLOSE, size = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun FoldersScreen(viewModel: NotesViewModel, onOpenFolder: (Long) -> Unit) {
    val folders by viewModel.folders.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val unlocked by viewModel.privateUnlocked.collectAsState()
    val visibleForFolders = notes.filter { !it.isPrivate || unlocked }
    var newName by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = { showCreate = true },
            shape = RoundedCornerShape(14.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = ThemeMainGold,
                contentColor = Color(0xFF2D261A)
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(bottom = 4.dp)
        ) {
            SymbolIcon(Symbols.CREATE_NEW_FOLDER, size = 20.sp, color = Color(0xFF2D261A))
            Text(" New folder", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (folders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SymbolIcon(Symbols.FOLDER, size = 48.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Text("No folders yet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    Text("Create a folder to organize your notes.", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(folders, key = { it.id }) { folder ->
                    val count = visibleForFolders.count { it.folderId == folder.id && !it.trashed }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenFolder(folder.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            SymbolIcon(Symbols.FOLDER, size = 24.sp, color = ThemeMainGold)
                            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(folder.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("$count notes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { viewModel.deleteFolder(folder) }) {
                                SymbolIcon(Symbols.DELETE, size = 18.sp, color = MaterialTheme.colorScheme.error)
                            }
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
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.createFolder(newName.trim())
                        newName = ""
                        showCreate = false
                    }
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
                        error = ""
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
