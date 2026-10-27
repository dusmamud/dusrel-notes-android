package com.dusrel.notes.presentation.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dusrel.notes.data.work.ExactAlarmScheduler
import com.dusrel.notes.data.work.cancelReminder
import com.dusrel.notes.data.work.scheduleReminder
import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.NoteType
import com.dusrel.notes.domain.model.RichDocument
import com.dusrel.notes.domain.model.migrateMarkersToSpans
import com.dusrel.notes.domain.model.richDocumentFromSpansJson
import com.dusrel.notes.domain.model.TodoItem
import com.dusrel.notes.domain.reminder.ReminderAlarmScheduler
import com.dusrel.notes.domain.reminder.ReminderScheduler
import com.dusrel.notes.presentation.components.SymbolIcon
import com.dusrel.notes.presentation.components.Symbols
import com.dusrel.notes.presentation.viewmodel.NotesViewModel
import java.util.Calendar

@Composable
fun EditorScreen(
    noteId: Long,
    viewModel: NotesViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val folders by viewModel.folders.collectAsState()
    var note by remember { mutableStateOf<Note?>(null) }
    var title by remember { mutableStateOf("") }
    var doc by remember { mutableStateOf<RichDocument?>(null) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var showReminderInfo by remember { mutableStateOf(false) }
    var reminderPathLabel by remember { mutableStateOf<String?>(null) }
    // Exact-alarm scheduler with the WorkManager fallback (kept, not deleted).
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
    var todos by remember { mutableStateOf<List<TodoItem>>(emptyList()) }
    var newTodoText by remember { mutableStateOf("") }
    var doodleData by remember { mutableStateOf("") }

    LaunchedEffect(noteId) {
        val loaded = viewModel.getNote(noteId)
        note = loaded
        title = loaded?.title ?: ""
        val loadedDoc = if (loaded != null && loaded.richTextSpans.isNotBlank()) {
            runCatching { richDocumentFromSpansJson(loaded.richTextSpans) }
                .getOrDefault(migrateMarkersToSpans(loaded.content))
        } else {
            migrateMarkersToSpans(loaded?.content ?: "")
        }
        doc = loadedDoc
        // One-way migration: persist migrated text + spans JSON once for old
        // marker/plain notes (content must match the spans, not stay marker text).
        // Never migrate-save a locked private note.
        if (loaded != null && loaded.richTextSpans.isBlank()
            && !(loaded.isPrivate && !viewModel.privateUnlocked.value)
        ) {
            viewModel.saveNoteNow(loaded.copy(content = loadedDoc.text, richTextSpans = loadedDoc.toSpansJson()))
        }
        doodleData = loaded?.doodleData ?: ""
        todos = viewModel.getTodos(noteId)
    }
    val current = note
    // Autosave is debounced in the ViewModel (500 ms) and its scope outlives
    // this screen, so the last keystroke is still saved after navigating back.

    fun persist() {
        val d = doc ?: return
        current?.let {
            viewModel.saveNoteDebounced(
                it.copy(title = title, content = d.text, richTextSpans = d.toSpansJson(), doodleData = doodleData)
            )
        }
    }

    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && current != null) {
            val d = doc
            // Single-note export now includes its folder (previously emptyList)
            // and strips stale ciphertext when exporting decrypted content.
            val exported = current!!.copy(
                title = title,
                content = d?.text ?: current!!.content,
                richTextSpans = d?.toSpansJson() ?: current!!.richTextSpans,
                encryptedTitle = "", encryptedContent = "", encryptedRichTextSpans = "",
                isEncrypted = false,
            )
            val json = com.dusrel.notes.domain.backup.BackupCodec.export(
                com.dusrel.notes.domain.backup.BackupData(
                    listOf(exported), todos,
                    folders.filter { it.id == current!!.folderId },
                )
            )
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        }
    }

    if (current == null) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("Loading note…", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    val n = current!!
    // Locked private notes must not open in the editor at all (note_id taps,
    // reminder taps, widget taps all land here). Unlock first, via Private.
    val unlocked by viewModel.privateUnlocked.collectAsState()
    if (n.isPrivate && !unlocked) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("This private note is locked.", style = MaterialTheme.typography.titleLarge)
            Text(
                "Unlock private notes first, then open this note again.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text("Back") }
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { SymbolIcon(Symbols.ARROW_BACK) }
                Text("Edit note", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, viewModel.noteToShareText(n.copy(title = title, content = doc?.text ?: ""), todos))
                    }
                    shareLauncher.launch(Intent.createChooser(intent, "Share note"))
                }) { SymbolIcon(Symbols.SHARE) }
                IconButton(onClick = { exportLauncher.launch("note-${n.id}.json") }) { SymbolIcon(Symbols.DOWNLOAD) }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; persist() },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Colour picker
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Colour: ", style = MaterialTheme.typography.bodyMedium)
                listOf(0, 0xFFFFF3CD.toInt(), 0xFFD4EDDA.toInt(), 0xFFD1ECF1.toInt(), 0xFFF8D7DA.toInt()).forEach { c ->
                    IconButton(onClick = {
                        note = n.copy(color = c); viewModel.updateNote(n.copy(color = c))
                    }) {
                        Text("●", color = if (c == 0) MaterialTheme.colorScheme.onSurface else Color(c), fontSize = 24.sp)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Private", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = n.isPrivate,
                    onCheckedChange = {
                        note = n.copy(isPrivate = it); viewModel.updateNote(n.copy(isPrivate = it))
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pinned", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = n.pinned,
                    onCheckedChange = {
                        note = n.copy(pinned = it); viewModel.updateNote(n.copy(pinned = it))
                    }
                )
            }
            Row {
                OutlinedButton(onClick = { showFolderDialog = true }) {
                    SymbolIcon(Symbols.FOLDER, size = 18.sp)
                    Text(" Folder", modifier = Modifier.padding(start = 4.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    val cal = Calendar.getInstance()
                    DatePickerDialog(context, { _, y, m, d ->
                        TimePickerDialog(context, { _, h, min ->
                            val picked = Calendar.getInstance().apply {
                                set(y, m, d, h, min, 0)
                            }.timeInMillis
                            val decision = reminderScheduler.schedule(
                                n.id, title.ifBlank { "Untitled note" }, picked,
                                ExactAlarmScheduler.canScheduleExactAlarms(context),
                            )
                            note = n.copy(reminderTime = picked, reminderExact = decision.label == "exact")
                            viewModel.updateNote(n.copy(reminderTime = picked, reminderExact = decision.label == "exact"))
                            reminderPathLabel = decision.label
                            showReminderInfo = true
                        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                }) {
                    SymbolIcon(Symbols.ALARM_ADD, size = 18.sp)
                    Text(" Reminder", modifier = Modifier.padding(start = 4.dp))
                }
            }
            if (n.reminderTime != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Reminder: " + java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(n.reminderTime!!)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = {
                        note = n.copy(reminderTime = null)
                        viewModel.updateNote(n.copy(reminderTime = null))
                        reminderScheduler.cancelAll(n.id)
                    }) { Text("Cancel") }
                }
            }
            if (showReminderInfo) {
                val pathText = if (reminderPathLabel == "exact") "exact" else "best-effort"
                Text("Reminder set ($pathText). You will get a notification at that time.", style = MaterialTheme.typography.bodyMedium)
                if (reminderPathLabel != "exact") {
                    OutlinedButton(onClick = {
                        context.startActivity(ExactAlarmScheduler.createExactAlarmSettingsIntent(context))
                    }) { Text("Allow exact alarms") }
                }
            }
        }

        if (n.type == NoteType.DOODLE) {
            item {
                Text("Doodle", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                DoodleCanvas(initialData = doodleData, onStrokesChanged = { doodleData = it; persist() })
            }
        } else {
            item {
                // Real spans, rendered in the editor itself — no marker preview.
                RichTextEditor(
                    document = doc ?: RichDocument(""),
                    onChange = { newDoc -> doc = newDoc; persist() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
        }

        if (n.type == NoteType.TODO) {
            item {
                Text("Checklist", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                val done = todos.count { it.done }
                Text("$done of ${todos.size} done", style = MaterialTheme.typography.bodyMedium)
            }
            items(todos, key = { it.id }) { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = item.done, onCheckedChange = {
                        viewModel.toggleTodo(item)
                        todos = todos.map { if (it.id == item.id) it.copy(done = !it.done) else it }
                    })
                    Text(item.text, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        viewModel.deleteTodo(item)
                        todos = todos.filter { it.id != item.id }
                    }) { SymbolIcon(Symbols.DELETE, size = 20.sp) }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTodoText,
                        onValueChange = { newTodoText = it },
                        label = { Text("New item") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        if (newTodoText.isNotBlank()) {
                            viewModel.addTodo(n.id, newTodoText.trim())
                            todos = todos + TodoItem(noteId = n.id, text = newTodoText.trim(), position = todos.size)
                            newTodoText = ""
                        }
                    }) { Text("Add") }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }

    if (showFolderDialog) {
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text("Move to folder") },
            text = {
                Column {
                    TextButton(onClick = {
                        viewModel.moveToFolder(n, null); note = n.copy(folderId = null); showFolderDialog = false
                    }) { Text("No folder") }
                    folders.forEach { folder ->
                        TextButton(onClick = {
                            viewModel.moveToFolder(n, folder.id); note = n.copy(folderId = folder.id); showFolderDialog = false
                        }) { Text(folder.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFolderDialog = false }) { Text("Close") } }
        )
    }
}
