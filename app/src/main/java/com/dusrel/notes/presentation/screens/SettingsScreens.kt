package com.dusrel.notes.presentation.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dusrel.notes.data.local.ThemeSetting
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.dusrel.notes.presentation.theme.ThemeMainGold
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: NotesViewModel, onOpenAbout: () -> Unit, onOpenPrivate: () -> Unit) {
    val context = LocalContext.current
    // Observable locale (see EditorScreen): recomposes on config change.
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    val theme by viewModel.theme.collectAsState()
    val isGrid by viewModel.isGridView.collectAsState()
    val isTaskToolboxEnabled by viewModel.isTaskToolboxEnabled.collectAsState()
    var message by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val json = viewModel.exportBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                message = "Backup exported."
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                viewModel.importBackupJson(json) { ok ->
                    message = if (ok) "Backup imported." else "Import failed: file is not a valid Notes backup."
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        // View Mode (phone_app_preferences.xml parity)
        Text("View mode", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            RadioButton(selected = !isGrid, onClick = { viewModel.setGridView(false) })
            Text("List view (Single column)")
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            RadioButton(selected = isGrid, onClick = { viewModel.setGridView(true) })
            Text("Grid view (Two columns)")
        }

        // Quick Functions & Task Toolbox (pref_title_todo_float_mode parity)
        Text("Quick functions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Use task toolbox", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Swipe from screen edge to quickly view or create tasks and notes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isTaskToolboxEnabled,
                onCheckedChange = { viewModel.setTaskToolboxEnabled(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = ThemeMainGold, checkedTrackColor = ThemeMainGold.copy(alpha = 0.5f))
            )
        }

        Text("Appearance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        listOf(ThemeSetting.LIGHT to "Light", ThemeSetting.DARK to "Dark", ThemeSetting.SYSTEM to "System default").forEach { (value, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RadioButton(selected = theme == value, onClick = { viewModel.setTheme(value) })
                Text(label)
            }
        }
        Text("Language", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        Text(
            "Current language: " + locale.displayLanguage +
                ". Notes follows your device language; no separate translations are shipped yet.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text("Private notes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        Button(onClick = onOpenPrivate) { Text("Open private notes") }
        OutlinedButton(onClick = { viewModel.lockPrivate() }, modifier = Modifier.padding(top = 8.dp)) { Text("Lock private notes") }
        Text("Backup (local file only)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        Text("Backups are JSON files on your device. Nothing is uploaded anywhere.", style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = { exportLauncher.launch("notes-backup.json") }) { Text("Export all") }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) { Text("Import") }
        }
        if (message.isNotBlank()) Text(message, modifier = Modifier.padding(top = 8.dp))
        Text("About", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        Button(onClick = onOpenAbout) { Text("About Notes") }
    }
}

@Composable
fun AboutScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("About Notes", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Notes is an original, local-first notes app. Your notes, checklists, " +
                "folders, doodles, and reminders live only on this device, in a local database. " +
                "There is no account, no cloud sync, and no backend: the app contains a " +
                "deliberately empty sync seam where a backend could be added in the future, " +
                "but nothing is sent anywhere today.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Version 1.0.0", style = MaterialTheme.typography.bodyMedium)
        Text("Made for keeping thoughts close at hand.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun GuideScreen(onDone: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val pages = listOf(
        "Welcome to Notes" to "Write text notes, checklists, and doodles — all stored only on this device.",
        "Organise your way" to "Pin what matters, file notes into folders, archive what is done, and keep sensitive notes behind a local PIN.",
        "Never miss a thought" to "Set reminders on any note, search everything at once, and export a local backup whenever you like.",
    )
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(pages[page].first, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(pages[page].second, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(32.dp))
        Text("Page ${page + 1} of 3", style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.padding(top = 16.dp)) {
            if (page > 0) {
                OutlinedButton(onClick = { page-- }) { Text("Back") }
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (page < 2) {
                Button(onClick = { page++ }) { Text("Next") }
            } else {
                Button(onClick = onDone) { Text("Start writing") }
            }
        }
    }
}
