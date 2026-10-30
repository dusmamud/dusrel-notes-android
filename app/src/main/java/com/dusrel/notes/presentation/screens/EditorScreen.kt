package com.dusrel.notes.presentation.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dusrel.notes.data.work.ExactAlarmScheduler
import com.dusrel.notes.data.work.cancelReminder
import com.dusrel.notes.data.work.scheduleReminder
import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.NoteType
import com.dusrel.notes.domain.model.RichDocument
import com.dusrel.notes.domain.model.TodoItem
import com.dusrel.notes.domain.model.migrateMarkersToSpans
import com.dusrel.notes.domain.model.richDocumentFromSpansJson
import com.dusrel.notes.domain.model.toSpansJson
import com.dusrel.notes.domain.reminder.ReminderAlarmScheduler
import com.dusrel.notes.domain.reminder.ReminderScheduler
import com.dusrel.notes.presentation.components.SymbolIcon
import com.dusrel.notes.presentation.components.Symbols
import com.dusrel.notes.presentation.theme.PoppinsFamily
import com.dusrel.notes.presentation.theme.ThemeMainGold
import com.dusrel.notes.presentation.theme.getNoteCardTheme
import com.dusrel.notes.presentation.viewmodel.NotesViewModel
import java.util.Calendar

@Composable
fun EditorScreen(
    noteId: Long,
    viewModel: NotesViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val folders by viewModel.folders.collectAsState()
    var note by remember { mutableStateOf<Note?>(null) }
    var title by remember { mutableStateOf("") }
    var doc by remember { mutableStateOf<RichDocument?>(null) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showReminderInfo by remember { mutableStateOf(false) }
    var reminderPathLabel by remember { mutableStateOf<String?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showColorPalette by remember { mutableStateOf(false) }
    var showFormatToolbar by remember { mutableStateOf(false) }

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
        if (loaded != null && loaded.richTextSpans.isBlank()
            && !(loaded.isPrivate && !viewModel.privateUnlocked.value)
        ) {
            viewModel.saveNoteNow(loaded.copy(content = loadedDoc.text, richTextSpans = loadedDoc.toSpansJson()))
        }
        doodleData = loaded?.doodleData ?: ""
        todos = viewModel.getTodos(noteId)
    }

    
    val current = note

    fun persist() {
        val d = doc ?: return
        current?.let {
            viewModel.saveNoteDebounced(
                it.copy(title = title, content = d.text, richTextSpans = d.toSpansJson(), doodleData = doodleData)
            )
        }
    }

    val undoStack = remember { mutableStateListOf<RichDocument>() }
    val redoStack = remember { mutableStateListOf<RichDocument>() }

    fun performUndo() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeAt(undoStack.lastIndex)
            doc?.let { redoStack.add(it) }
            doc = prev
            persist()
        }
    }

    fun performRedo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            doc?.let { undoStack.add(it) }
            doc = next
            persist()
        }
    }

    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && current != null) {
            val d = doc
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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading note…", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val n = current!!
    val unlocked by viewModel.privateUnlocked.collectAsState()
    if (n.isPrivate && !unlocked) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            SymbolIcon(Symbols.LOCK, size = 48.sp, color = MaterialTheme.colorScheme.primary)
            Text("This note is locked in Private Vault", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text("Back") }
        }
        return
    }

    val isDark = isSystemInDarkTheme()
    val cardTheme = getNoteCardTheme(n.color, isDark)
    val currentFolder = folders.firstOrNull { it.id == n.folderId }

    // Full Immersion Paper Canvas
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = cardTheme.containerColor,
        contentColor = cardTheme.onContainerColor
    ) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Row (Back, Folder Chip, Pin, More Menu)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            SymbolIcon(Symbols.ARROW_BACK, size = 22.sp, color = cardTheme.onContainerColor)
                        }
                        // Folder Selector Pill Chip
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cardTheme.borderColor.copy(alpha = 0.5f),
                            modifier = Modifier.clickable { showFolderDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                SymbolIcon(Symbols.FOLDER, size = 14.sp, color = cardTheme.onContainerColor)
                                Text(
                                    text = if (currentFolder != null) " ${currentFolder.name} ▾" else " Move to folder ▾",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = cardTheme.onContainerColor,
                                    modifier = Modifier.padding(start = 2.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Live Undo Button
                        IconButton(
                            onClick = { performUndo() },
                            enabled = undoStack.isNotEmpty()
                        ) {
                            SymbolIcon(
                                Symbols.UNDO,
                                size = 20.sp,
                                color = if (undoStack.isNotEmpty()) cardTheme.onContainerColor else cardTheme.secondaryTextColor.copy(alpha = 0.3f)
                            )
                        }
                        // Live Redo Button (Horizontally mirrored)
                        IconButton(
                            onClick = { performRedo() },
                            enabled = redoStack.isNotEmpty()
                        ) {
                            SymbolIcon(
                                Symbols.UNDO,
                                size = 20.sp,
                                modifier = Modifier.graphicsLayer(scaleX = -1f),
                                color = if (redoStack.isNotEmpty()) cardTheme.onContainerColor else cardTheme.secondaryTextColor.copy(alpha = 0.3f)
                            )
                        }

                        // Pin Toggle Icon
                        IconButton(onClick = {
                            val newPinned = !n.pinned
                            note = n.copy(pinned = newPinned)
                            viewModel.updateNote(n.copy(pinned = newPinned))
                        }) {
                            SymbolIcon(
                                Symbols.PUSH_PIN,
                                size = 20.sp,
                                filled = n.pinned,
                                color = if (n.pinned) ThemeMainGold else cardTheme.secondaryTextColor
                            )
                        }

                        // Direct Reminder Toggle Icon
                        IconButton(onClick = {
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
                            SymbolIcon(
                                Symbols.ALARM,
                                size = 20.sp,
                                color = if (n.reminderTime != null) ThemeMainGold else cardTheme.secondaryTextColor
                            )
                        }

                        // 3-dots More Menu
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                SymbolIcon(Symbols.MORE_VERT, size = 22.sp, color = cardTheme.onContainerColor)
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (n.isPrivate) "Unlock from Vault" else "Move to Vault") },
                                    leadingIcon = { SymbolIcon(Symbols.LOCK, size = 18.sp) },
                                    onClick = {
                                        showMoreMenu = false
                                        val newPrivate = !n.isPrivate
                                        note = n.copy(isPrivate = newPrivate)
                                        viewModel.updateNote(n.copy(isPrivate = newPrivate))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Set reminder") },
                                    leadingIcon = { SymbolIcon(Symbols.ALARM_ADD, size = 18.sp) },
                                    onClick = {
                                        showMoreMenu = false
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
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (n.archived) "Unarchive" else "Archive") },
                                    leadingIcon = { SymbolIcon(if (n.archived) Symbols.UNARCHIVE else Symbols.ARCHIVE, size = 18.sp) },
                                    onClick = {
                                        showMoreMenu = false
                                        note = n.copy(archived = !n.archived)
                                        viewModel.setArchived(n, !n.archived)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share note") },
                                    leadingIcon = { SymbolIcon(Symbols.SHARE, size = 18.sp) },
                                    onClick = {
                                        showMoreMenu = false
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, viewModel.noteToShareText(n.copy(title = title, content = doc?.text ?: ""), todos))
                                        }
                                        shareLauncher.launch(Intent.createChooser(intent, "Share note"))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export backup JSON") },
                                    leadingIcon = { SymbolIcon(Symbols.DOWNLOAD, size = 18.sp) },
                                    onClick = {
                                        showMoreMenu = false
                                        exportLauncher.launch("note-${n.id}.json")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete note", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { SymbolIcon(Symbols.DELETE, size = 18.sp, color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMoreMenu = false
                                        if (n.trashed) {
                                            showDeleteConfirm = true
                                        } else {
                                            viewModel.moveToTrash(n)
                                            reminderScheduler.cancelAll(n.id)
                                            onBack()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Borderless Scrollable Paper Body
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp)
                ) {
                    item {
                        // Borderless Title Input (22sp, bold, zero background boxes)
                        BasicTextField(
                            value = title,
                            onValueChange = { title = it; persist() },
                            textStyle = TextStyle(
                                fontFamily = PoppinsFamily,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = cardTheme.onContainerColor
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (title.isEmpty()) {
                                    Text(
                                        "Title",
                                        fontFamily = PoppinsFamily,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = cardTheme.secondaryTextColor.copy(alpha = 0.5f)
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        )

                        // Subtitle Meta Line (Timestamp + Character count)
                        val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", locale)
                        val charCount = (title.length + (doc?.text?.length ?: 0))
                        Text(
                            text = dateFormat.format(java.util.Date(n.updatedAt)) + " | $charCount characters",
                            fontSize = 12.sp,
                            color = cardTheme.secondaryTextColor.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                        )

                        if (n.reminderTime != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = cardTheme.borderColor.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SymbolIcon(Symbols.ALARM, size = 16.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(
                                            " Reminder: " + java.text.SimpleDateFormat("dd MMM, HH:mm", locale).format(java.util.Date(n.reminderTime!!)),
                                            fontSize = 12.sp,
                                            color = cardTheme.onContainerColor
                                        )
                                    }
                                    TextButton(onClick = {
                                        note = n.copy(reminderTime = null)
                                        viewModel.updateNote(n.copy(reminderTime = null))
                                        reminderScheduler.cancelAll(n.id)
                                    }) { Text("Clear", fontSize = 12.sp) }
                                }
                            }
                        }
                    }

                    // Doodle or Rich Text or Checklist
                    if (n.type == NoteType.DOODLE) {
                        item {
                            DoodleCanvas(
                                initialData = doodleData,
                                onStrokesChanged = { doodleData = it; persist() }
                            )
                        }
                    } else {
                        item {
                            RichTextEditor(
                                document = doc ?: RichDocument(""),
                                onChange = { newDoc ->
                                    doc?.let { prevDoc ->
                                        if (prevDoc.text != newDoc.text || prevDoc.paragraphs != newDoc.paragraphs || prevDoc.spans != newDoc.spans) {
                                            if (undoStack.size > 50) undoStack.removeAt(0)
                                            undoStack.add(prevDoc)
                                            redoStack.clear()
                                        }
                                    }
                                    doc = newDoc
                                    persist()
                                },
                                showToolbar = showFormatToolbar,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (n.type == NoteType.TODO) {
                        item {
                            Text(
                                "Checklist (${todos.count { it.done }}/${todos.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cardTheme.onContainerColor,
                                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                            )
                        }
                        items(todos, key = { it.id }) { item ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                TaskSquircleCheckbox(
                                    checked = item.done,
                                    onCheckedChange = {
                                        viewModel.toggleTodo(item)
                                        todos = todos.map { if (it.id == item.id) it.copy(done = !it.done) else it }
                                    }
                                )
                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    color = if (item.done) cardTheme.secondaryTextColor.copy(alpha = 0.5f) else cardTheme.onContainerColor,
                                    modifier = Modifier.weight(1f).padding(start = 10.dp)
                                )
                                IconButton(onClick = {
                                    viewModel.deleteTodo(item)
                                    todos = todos.filter { it.id != item.id }
                                }) { SymbolIcon(Symbols.CLOSE, size = 16.sp, color = cardTheme.secondaryTextColor) }
                            }
                        }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                BasicTextField(
                                    value = newTodoText,
                                    onValueChange = { newTodoText = it },
                                    textStyle = TextStyle(fontSize = 14.sp, color = cardTheme.onContainerColor),
                                    decorationBox = { inner ->
                                        if (newTodoText.isEmpty()) Text("+ Add item", fontSize = 14.sp, color = cardTheme.secondaryTextColor.copy(alpha = 0.5f))
                                        inner()
                                    },
                                    modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                                )
                                if (newTodoText.isNotBlank()) {
                                    TextButton(onClick = {
                                        viewModel.addTodo(n.id, newTodoText.trim())
                                        todos = todos + TodoItem(noteId = n.id, text = newTodoText.trim(), position = todos.size)
                                        newTodoText = ""
                                    }) { Text("Add") }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(110.dp)) }
                }

                // Color Palette Swatches (Popped above bottom bar)
                AnimatedVisibility(visible = showColorPalette) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            (0..4).forEach { themeId ->
                                val previewTheme = getNoteCardTheme(themeId, isDark)
                                val isSelected = (n.color % 5).let { if (it < 0) it + 5 else it } == themeId
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(previewTheme.containerColor)
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else previewTheme.borderColor,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            note = n.copy(color = themeId)
                                            viewModel.updateNote(n.copy(color = themeId))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        SymbolIcon(Symbols.CHECK, size = 15.sp, color = previewTheme.onContainerColor)
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Floating Toolbar
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        // Palette Tool
                        IconButton(onClick = { showColorPalette = !showColorPalette }) {
                            SymbolIcon(
                                Symbols.PALETTE,
                                size = 20.sp,
                                color = if (showColorPalette) ThemeMainGold else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Format Tool (Aa)
                        if (n.type != NoteType.DOODLE && n.type != NoteType.TODO) {
                            IconButton(onClick = { showFormatToolbar = !showFormatToolbar }) {
                                SymbolIcon(
                                    Symbols.TEXT_FIELDS,
                                    size = 20.sp,
                                    color = if (showFormatToolbar) ThemeMainGold else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        // Checklist Tool
                        IconButton(onClick = {
                            val newType = if (n.type == NoteType.TODO) NoteType.NOTE else NoteType.TODO
                            note = n.copy(type = newType)
                            viewModel.updateNote(n.copy(type = newType))
                        }) {
                            SymbolIcon(
                                Symbols.CHECKLIST,
                                size = 20.sp,
                                color = if (n.type == NoteType.TODO) ThemeMainGold else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Doodle Tool
                        IconButton(onClick = {
                            val newType = if (n.type == NoteType.DOODLE) NoteType.NOTE else NoteType.DOODLE
                            note = n.copy(type = newType)
                            viewModel.updateNote(n.copy(type = newType))
                        }) {
                            SymbolIcon(
                                Symbols.DRAW,
                                size = 20.sp,
                                color = if (n.type == NoteType.DOODLE) ThemeMainGold else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Move to folder dialog
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

    // Confirm Delete Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete permanently?") },
            text = { Text("This note cannot be recovered.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePermanently(n)
                    reminderScheduler.cancelAll(n.id)
                    showDeleteConfirm = false
                    onBack()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Keep") } }
        )
    }
}
