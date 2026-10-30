package com.dusrel.notes.presentation.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dusrel.notes.data.work.ExactAlarmScheduler
import com.dusrel.notes.data.work.cancelReminder
import com.dusrel.notes.data.work.scheduleReminder
import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.NoteType
import com.dusrel.notes.domain.model.ParagraphType
import com.dusrel.notes.domain.model.RichDocument
import com.dusrel.notes.domain.model.SpanStyleKind
import com.dusrel.notes.domain.model.TodoItem
import com.dusrel.notes.domain.model.migrateMarkersToSpans
import com.dusrel.notes.domain.model.richDocumentFromSpansJson
import com.dusrel.notes.domain.model.toSpansJson
import com.dusrel.notes.domain.model.toggleParagraph
import com.dusrel.notes.domain.model.toggleSpan
import com.dusrel.notes.domain.reminder.ReminderAlarmScheduler
import com.dusrel.notes.domain.reminder.ReminderScheduler
import com.dusrel.notes.presentation.components.SymbolIcon
import com.dusrel.notes.presentation.components.Symbols
import com.dusrel.notes.presentation.theme.PoppinsFamily
import com.dusrel.notes.presentation.theme.ThemeMainGold
import com.dusrel.notes.presentation.theme.getNoteCardTheme
import com.dusrel.notes.presentation.viewmodel.NotesViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    noteId: Long,
    viewModel: NotesViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    val folders by viewModel.folders.collectAsState()
    var note by remember { mutableStateOf<Note?>(null) }
    var title by remember { mutableStateOf("") }
    var doc by remember { mutableStateOf<RichDocument?>(null) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showColorPalette by remember { mutableStateOf(false) }

    // Toolbar and interaction states
    var isEditing by remember { mutableStateOf(false) }
    var showRichTextDrawer by remember { mutableStateOf(false) }
    var showAiSheet by remember { mutableStateOf(false) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableStateOf(0) }

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

    // Audio recording timer loop
    LaunchedEffect(isRecordingAudio) {
        if (isRecordingAudio) {
            recordingSeconds = 0
            while (isRecordingAudio) {
                delay(1000L)
                recordingSeconds++
            }
        }
    }

    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val imageSnippet = "\n[🖼️ Photo attachment: ${uri.lastPathSegment ?: "image"}]\n"
            val updated = (doc ?: RichDocument("")).let { it.copy(text = it.text + imageSnippet) }
            doc = updated
            persist()
        }
    }

    if (current == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading note…", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val n = current
    val unlocked by viewModel.privateUnlocked.collectAsState()
    if (n.isPrivate && !unlocked) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SymbolIcon(Symbols.LOCK, size = 48.sp, color = MaterialTheme.colorScheme.primary)
            Text("This note is locked in Private Vault", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text("Back") }
        }
        return
    }

    val isDark = isSystemInDarkTheme()
    val cardTheme = getNoteCardTheme(n.color, isDark)
    val currentFolder = folders.firstOrNull { it.id == n.folderId }

    // Full Immersion Paper Canvas (phone_note_edit.xml Parity)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = cardTheme.containerColor,
        contentColor = cardTheme.onContainerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Action Bar Header (v12_phone_edit_header.xml Dual-Mode Parity)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                color = cardTheme.containerColor,
                border = BorderStroke(0.5.dp, cardTheme.borderColor.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back Arrow
                    IconButton(onClick = {
                        if (isEditing) {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            isEditing = false
                        } else {
                            onBack()
                        }
                    }) {
                        SymbolIcon(Symbols.ARROW_BACK, size = 22.sp, color = cardTheme.onContainerColor)
                    }

                    if (isEditing) {
                        // Edit Mode Right Icons: Undo, Redo, Done Checkmark
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                            IconButton(onClick = {
                                persist()
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                isEditing = false
                            }) {
                                SymbolIcon(
                                    Symbols.CHECK,
                                    size = 24.sp,
                                    color = ThemeMainGold
                                )
                            }
                        }
                    } else {
                        // View Mode Right Icons: Share, Theme, More 3-dots
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, viewModel.noteToShareText(n.copy(title = title, content = doc?.text ?: ""), todos))
                                }
                                shareLauncher.launch(Intent.createChooser(intent, "Share note"))
                            }) {
                                SymbolIcon(Symbols.SHARE, size = 20.sp, color = cardTheme.onContainerColor)
                            }

                            IconButton(onClick = { showColorPalette = !showColorPalette }) {
                                SymbolIcon(
                                    Symbols.PALETTE,
                                    size = 20.sp,
                                    color = if (showColorPalette) ThemeMainGold else cardTheme.onContainerColor
                                )
                            }

                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    SymbolIcon(Symbols.MORE_VERT, size = 22.sp, color = cardTheme.onContainerColor)
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
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
                                                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                                        }
                                    )
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
                                        text = { Text("Move to folder") },
                                        leadingIcon = { SymbolIcon(Symbols.FOLDER, size = 18.sp) },
                                        onClick = {
                                            showMoreMenu = false
                                            showFolderDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete note", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { SymbolIcon(Symbols.DELETE, size = 18.sp, color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.moveToTrash(n)
                                            reminderScheduler.cancelAll(n.id)
                                            onBack()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Scrollable Note Canvas Area
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                item {
                    // Folder Selector Pill Chip (note_edit_folder_layout.xml Parity - Above Title)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = cardTheme.borderColor.copy(alpha = 0.5f),
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 4.dp)
                            .clickable { showFolderDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            SymbolIcon(Symbols.FOLDER, size = 13.sp, color = cardTheme.onContainerColor)
                            Text(
                                text = if (currentFolder != null) " ${currentFolder.name} ▾" else " Unfiled ▾",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = cardTheme.onContainerColor,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    }

                    // Borderless Title Input (note_edit_title 22sp Bold)
                    BasicTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            isEditing = true
                            persist()
                        },
                        textStyle = TextStyle(
                            fontFamily = PoppinsFamily,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = cardTheme.onContainerColor
                        ),
                        cursorBrush = SolidColor(ThemeMainGold),
                        decorationBox = { innerTextField ->
                            if (title.isEmpty()) {
                                Text(
                                    "Title",
                                    fontFamily = PoppinsFamily,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cardTheme.secondaryTextColor.copy(alpha = 0.45f)
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .onFocusChanged { if (it.isFocused) isEditing = true }
                    )

                    // Subtitle Meta Line (note_extra_info_layout.xml Parity: Timestamp + Char count)
                    val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", locale)
                    val charCount = (title.length + (doc?.text?.length ?: 0))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                    ) {
                        Text(
                            text = dateFormat.format(java.util.Date(n.updatedAt)) + " | $charCount characters",
                            fontSize = 12.sp,
                            color = cardTheme.secondaryTextColor.copy(alpha = 0.7f)
                        )
                        if (n.isPrivate) {
                            SymbolIcon(
                                Symbols.LOCK,
                                size = 13.sp,
                                color = cardTheme.secondaryTextColor.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                        if (n.reminderTime != null) {
                            SymbolIcon(
                                Symbols.ALARM,
                                size = 13.sp,
                                color = ThemeMainGold,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }

                // Note Body: Doodle Canvas or Rich Text Editor or Checklist
                if (n.type == NoteType.DOODLE) {
                    item {
                        DoodleCanvas(
                            initialData = doodleData,
                            onStrokesChanged = {
                                doodleData = it
                                isEditing = true
                                persist()
                            }
                        )
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { if (it.isFocused) isEditing = true }
                        ) {
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
                                    isEditing = true
                                    persist()
                                },
                                showToolbar = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
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
                            }) {
                                SymbolIcon(Symbols.CLOSE, size = 16.sp, color = cardTheme.secondaryTextColor)
                            }
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

                item { Spacer(modifier = Modifier.height(130.dp)) }
            }

            // Theme Swatches Palette (Appears when Palette icon is tapped)
            AnimatedVisibility(
                visible = showColorPalette,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = cardTheme.containerColor,
                    border = BorderStroke(0.5.dp, cardTheme.borderColor.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (0..4).forEach { themeId ->
                            val previewTheme = getNoteCardTheme(themeId, isDark)
                            val isSelected = (n.color % 5).let { if (it < 0) it + 5 else it } == themeId
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(previewTheme.containerColor)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) ThemeMainGold else previewTheme.borderColor,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        note = n.copy(color = themeId)
                                        viewModel.updateNote(n.copy(color = themeId))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    SymbolIcon(Symbols.CHECK, size = 16.sp, color = previewTheme.onContainerColor)
                                }
                            }
                        }
                    }
                }
            }

            // Audio Recording Waves Panel (edit_audio_record_panel.xml Parity)
            AnimatedVisibility(
                visible = isRecordingAudio,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = cardTheme.containerColor,
                    border = BorderStroke(0.5.dp, cardTheme.borderColor.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val transition = rememberInfiniteTransition(label = "audioWave")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            (0..26).forEach { barIndex ->
                                val animatedHeight by transition.animateFloat(
                                    initialValue = 6f,
                                    targetValue = 24f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(durationMillis = 350 + (barIndex * 37) % 300),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "bar$barIndex"
                                )
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(animatedHeight.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(ThemeMainGold)
                                )
                            }
                        }

                        // Elapsed Time Counter
                        val minutes = recordingSeconds / 60
                        val secs = recordingSeconds % 60
                        Text(
                            text = String.format("%02d:%02d", minutes, secs),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        // Save audio chip to note
                        IconButton(onClick = {
                            val audioSnippet = "\n[🎙️ Voice note (${String.format("%02d:%02d", minutes, secs)})]\n"
                            val updated = (doc ?: RichDocument("")).let { it.copy(text = it.text + audioSnippet) }
                            doc = updated
                            persist()
                            isRecordingAudio = false
                        }) {
                            SymbolIcon(Symbols.CHECK, size = 22.sp, color = ThemeMainGold)
                        }
                    }
                }
            }

            // Docked Full-Width Bottom Toolbar (small_screen_mode_tool_group.xml & SmallScreenNoteEditToolbarController Parity)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
                color = cardTheme.containerColor,
                border = BorderStroke(0.5.dp, cardTheme.borderColor.copy(alpha = 0.35f)),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left 81% Section (Dual-state: Normal Tools vs 18 Rich Text Formatting Tools in the SAME 52dp row)
                    Box(
                        modifier = Modifier
                            .weight(0.81f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (!showRichTextDrawer) {
                            // State 1: 5 Main Edit Tools (@id/toolbar: AI, Voice, Gallery, Doodle, Checklist)
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // AI Assistant (@id/ai)
                                IconButton(onClick = { showAiSheet = true }) {
                                    SymbolIcon(
                                        Symbols.STAR,
                                        size = 22.sp,
                                        color = ThemeMainGold,
                                        filled = true
                                    )
                                }
                                // Voice Note Recorder (@id/audio)
                                IconButton(onClick = { isRecordingAudio = !isRecordingAudio }) {
                                    SymbolIcon(
                                        Symbols.MIC,
                                        size = 22.sp,
                                        color = if (isRecordingAudio) MaterialTheme.colorScheme.error else cardTheme.onContainerColor
                                    )
                                }
                                // Gallery Photo Picker (@id/gallery)
                                IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                                    SymbolIcon(
                                        Symbols.IMAGE,
                                        size = 22.sp,
                                        color = cardTheme.onContainerColor
                                    )
                                }
                                // Doodle Drawing Canvas (@id/edit_image)
                                IconButton(onClick = {
                                    val newType = if (n.type == NoteType.DOODLE) NoteType.NOTE else NoteType.DOODLE
                                    note = n.copy(type = newType)
                                    viewModel.updateNote(n.copy(type = newType))
                                }) {
                                    SymbolIcon(
                                        Symbols.DRAW,
                                        size = 22.sp,
                                        color = if (n.type == NoteType.DOODLE) ThemeMainGold else cardTheme.onContainerColor,
                                        filled = n.type == NoteType.DOODLE
                                    )
                                }
                                // Checklist Toggle (@id/check)
                                IconButton(onClick = {
                                    val newType = if (n.type == NoteType.TODO) NoteType.NOTE else NoteType.TODO
                                    note = n.copy(type = newType)
                                    viewModel.updateNote(n.copy(type = newType))
                                }) {
                                    SymbolIcon(
                                        Symbols.CHECKLIST,
                                        size = 22.sp,
                                        color = if (n.type == NoteType.TODO) ThemeMainGold else cardTheme.onContainerColor,
                                        filled = n.type == NoteType.TODO
                                    )
                                }
                            }
                        } else {
                            // State 2: Horizontally Scrollable 18 Formatting Tools (expand_icon_group.xml Parity)
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Heading 1 (@id/first_level)
                                TextButton(onClick = {
                                    doc?.let { d ->
                                        doc = toggleParagraph(d, 0, ParagraphType.HEADING)
                                        persist()
                                    }
                                }) {
                                    Text("H1", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cardTheme.onContainerColor)
                                }
                                // 2. Heading 2 (@id/second_level)
                                TextButton(onClick = {
                                    doc?.let { d ->
                                        doc = toggleParagraph(d, 0, ParagraphType.HEADING)
                                        persist()
                                    }
                                }) {
                                    Text("H2", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = cardTheme.onContainerColor)
                                }
                                // 3. Font Size A+ (@id/increase_font)
                                TextButton(onClick = {
                                    val newText = (doc?.text ?: "") + " "
                                    doc = (doc ?: RichDocument("")).copy(text = newText)
                                    persist()
                                }) {
                                    Text("A+", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cardTheme.onContainerColor)
                                }
                                // 4. Font Size A- (@id/reduce_font)
                                TextButton(onClick = { }) {
                                    Text("A-", fontWeight = FontWeight.Normal, fontSize = 12.sp, color = cardTheme.onContainerColor)
                                }
                                // 5. Bold (@id/bold)
                                IconButton(onClick = {
                                    doc?.let { d ->
                                        doc = toggleSpan(d, 0, d.text.length.coerceAtLeast(0), SpanStyleKind.BOLD)
                                        persist()
                                    }
                                }) {
                                    SymbolIcon(Symbols.FORMAT_BOLD, size = 19.sp, color = cardTheme.onContainerColor)
                                }
                                // 6. Italic (@id/italic)
                                IconButton(onClick = {
                                    doc?.let { d ->
                                        doc = toggleSpan(d, 0, d.text.length.coerceAtLeast(0), SpanStyleKind.ITALIC)
                                        persist()
                                    }
                                }) {
                                    SymbolIcon(Symbols.FORMAT_ITALIC, size = 19.sp, color = cardTheme.onContainerColor)
                                }
                                // 7. Underline (@id/underline)
                                IconButton(onClick = {
                                    doc?.let { d ->
                                        doc = toggleSpan(d, 0, d.text.length.coerceAtLeast(0), SpanStyleKind.UNDERLINE)
                                        persist()
                                    }
                                }) {
                                    SymbolIcon(Symbols.FORMAT_UNDERLINED, size = 19.sp, color = cardTheme.onContainerColor)
                                }
                                // 8. Strikethrough (@id/strikethrough)
                                TextButton(onClick = { }) {
                                    Text("S", textDecoration = TextDecoration.LineThrough, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cardTheme.onContainerColor)
                                }
                                // 9. Bullet List (@id/bullet)
                                IconButton(onClick = {
                                    doc?.let { d ->
                                        doc = toggleParagraph(d, 0, ParagraphType.BULLET)
                                        persist()
                                    }
                                }) {
                                    SymbolIcon(Symbols.FORMAT_LIST_BULLETED, size = 19.sp, color = cardTheme.onContainerColor)
                                }
                                // 10. Numbered List (@id/listbullet)
                                IconButton(onClick = {
                                    doc?.let { d ->
                                        doc = toggleParagraph(d, 0, ParagraphType.NUMBERED)
                                        persist()
                                    }
                                }) {
                                    SymbolIcon(Symbols.FORMAT_LIST_NUMBERED, size = 19.sp, color = cardTheme.onContainerColor)
                                }
                                // 11. Divider Line (@id/split_line)
                                TextButton(onClick = {
                                    val divider = "\n---\n"
                                    doc = (doc ?: RichDocument("")).let { it.copy(text = it.text + divider) }
                                    persist()
                                }) {
                                    Text("──", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cardTheme.onContainerColor)
                                }
                                // 12. Blockquote (@id/quote)
                                TextButton(onClick = {
                                    val quote = "\n> "
                                    doc = (doc ?: RichDocument("")).let { it.copy(text = it.text + quote) }
                                    persist()
                                }) {
                                    Text("❝", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cardTheme.onContainerColor)
                                }
                                // 13. Indent Right (@id/indentright)
                                IconButton(onClick = {
                                    val indented = "    "
                                    doc = (doc ?: RichDocument("")).let { it.copy(text = it.text + indented) }
                                    persist()
                                }) {
                                    SymbolIcon(Symbols.CHEVRON_RIGHT, size = 19.sp, color = cardTheme.onContainerColor)
                                }
                                // 14. Indent Left (@id/indentleft)
                                IconButton(onClick = { }) {
                                    SymbolIcon(Symbols.ARROW_BACK, size = 19.sp, color = cardTheme.onContainerColor)
                                }
                            }
                        }
                    }

                    // Vertical Divider Line (@id/rich_text_divide)
                    Box(
                        modifier = Modifier
                            .width(0.75.dp)
                            .height(26.dp)
                            .background(if (showRichTextDrawer) ThemeMainGold else cardTheme.borderColor.copy(alpha = 0.5f))
                    )

                    // Right 19% Section (@id/rich_text_switch: Toggles between Main Tools & Rich Text Toolbar in same bar)
                    Box(
                        modifier = Modifier
                            .weight(0.19f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = { showRichTextDrawer = !showRichTextDrawer }) {
                            SymbolIcon(
                                codePoint = if (showRichTextDrawer) Symbols.CLOSE else Symbols.TITLE,
                                size = 22.sp,
                                color = if (showRichTextDrawer) ThemeMainGold else cardTheme.onContainerColor
                            )
                        }
                    }
                }
            }
        }
    }

        // AI Writing Assistant Bottom Sheet (AiFloatingWindow Parity)
    if (showAiSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAiSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SymbolIcon(Symbols.STAR, size = 24.sp, color = ThemeMainGold, filled = true)
                    Text(
                        text = " AI Assistant",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Feature Action Tiles
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val currentText = doc?.text.orEmpty()
                            val summary = "\n\n📌 Summary:\n• " + currentText.lines().filter { it.isNotBlank() }.joinToString("\n• ")
                            doc = (doc ?: RichDocument("")).copy(text = currentText + summary)
                            persist()
                            showAiSheet = false
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📝 Summarize note into key takeaways", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val currentText = doc?.text.orEmpty()
                            val cleaned = currentText.replace(Regex("\\s+"), " ").trim()
                            doc = (doc ?: RichDocument("")).copy(text = cleaned)
                            persist()
                            showAiSheet = false
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✨ Proofread & polish grammar", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val currentText = doc?.text.orEmpty()
                            val professional = currentText + "\n\n[Refined with Professional tone]"
                            doc = (doc ?: RichDocument("")).copy(text = professional)
                            persist()
                            showAiSheet = false
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("👔 Rewrite in Professional Tone", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Move to Folder Dialog
    if (showFolderDialog) {
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text("Move to folder") },
            text = {
                Column {
                    TextButton(onClick = {
                        viewModel.moveToFolder(n, null)
                        note = n.copy(folderId = null)
                        showFolderDialog = false
                    }) { Text("Unfiled") }
                    folders.forEach { folder ->
                        TextButton(onClick = {
                            viewModel.moveToFolder(n, folder.id)
                            note = n.copy(folderId = folder.id)
                            showFolderDialog = false
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
