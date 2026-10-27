package com.dusrel.notes.presentation.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dusrel.notes.data.work.ExactAlarmScheduler
import com.dusrel.notes.data.work.cancelReminder
import com.dusrel.notes.data.work.scheduleReminder
import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.reminder.ReminderAlarmScheduler
import com.dusrel.notes.domain.reminder.ReminderScheduler
import com.dusrel.notes.domain.model.NoteType
import com.dusrel.notes.domain.model.SortOrder
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
    var showCreateDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Note?>(null) }
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
            if (folderFilter != null) {
                OutlinedButton(onClick = { viewModel.folderFilterId.value = null }) {
                    Text("Folder: " + (folders.firstOrNull { it.id == folderFilter }?.name ?: "") + " ✕")
                }
            }
        }
        if (!hasLoaded.value && notes.isEmpty()) {
            Text("Loading notes…", modifier = Modifier.padding(top = 24.dp), style = MaterialTheme.typography.bodyLarge)
        } else if (notes.isEmpty()) {
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
        } else {
            LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        tab = tab,
                        onOpen = { onOpenNote(note.id) },
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
}

@Composable
private fun NoteCard(
    note: Note,
    tab: ListTab,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpen() },
        colors = CardDefaults.cardColors(
            containerColor = if (note.color != 0) Color(note.color) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolIcon(
                    when (note.type) {
                        NoteType.TODO -> Symbols.CHECKLIST
                        NoteType.DOODLE -> Symbols.DRAW
                        else -> Symbols.DESCRIPTION
                    }, size = 20.sp
                )
                Text(
                    note.title.ifBlank { "(Untitled)" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                if (note.pinned) SymbolIcon(Symbols.PUSH_PIN, size = 18.sp)
                if (note.isPrivate) SymbolIcon(Symbols.LOCK, size = 18.sp)
                if (note.reminderTime != null) SymbolIcon(Symbols.ALARM, size = 18.sp)
            }
            val preview = note.content.lines().firstOrNull()?.take(120) ?: ""
            if (preview.isNotBlank()) Text(preview, style = MaterialTheme.typography.bodyMedium)
            Row {
                when (tab) {
                    ListTab.ACTIVE -> {
                        IconButton(onClick = onPin) { SymbolIcon(Symbols.PUSH_PIN, size = 20.sp, filled = note.pinned) }
                        IconButton(onClick = onArchive) { SymbolIcon(Symbols.ARCHIVE, size = 20.sp) }
                        IconButton(onClick = onTrash) { SymbolIcon(Symbols.DELETE, size = 20.sp) }
                    }
                    ListTab.ARCHIVED -> {
                        IconButton(onClick = onUnarchive) { SymbolIcon(Symbols.UNARCHIVE, size = 20.sp) }
                        IconButton(onClick = onTrash) { SymbolIcon(Symbols.DELETE, size = 20.sp) }
                    }
                    ListTab.TRASH -> {
                        IconButton(onClick = onRestore) { SymbolIcon(Symbols.RESTORE_FROM_TRASH, size = 20.sp) }
                        IconButton(onClick = onDelete) { SymbolIcon(Symbols.DELETE, size = 20.sp) }
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
                    Text("No private notes yet. Mark a note Private in its editor.", modifier = Modifier.padding(top = 24.dp))
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
