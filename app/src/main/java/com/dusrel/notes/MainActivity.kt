package com.dusrel.notes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.room.Room
import com.dusrel.notes.data.local.MIGRATION_1_2
import com.dusrel.notes.data.local.MIGRATION_2_3
import com.dusrel.notes.data.local.NotesDatabase
import com.dusrel.notes.data.local.PreferencesRepository
import com.dusrel.notes.data.local.RoomNotesRepository
import com.dusrel.notes.presentation.widgets.refreshListWidget
import com.dusrel.notes.domain.model.NoteType
import com.dusrel.notes.presentation.components.SymbolIcon
import com.dusrel.notes.presentation.components.Symbols
import com.dusrel.notes.presentation.screens.AboutScreen
import com.dusrel.notes.presentation.screens.EditorScreen
import com.dusrel.notes.presentation.screens.FoldersScreen
import com.dusrel.notes.presentation.screens.GuideScreen
import com.dusrel.notes.presentation.screens.NotesListScreen
import com.dusrel.notes.presentation.screens.PrivateScreen
import com.dusrel.notes.presentation.screens.RemindersScreen
import com.dusrel.notes.presentation.screens.SettingsScreen
import com.dusrel.notes.presentation.theme.NotesTheme
import com.dusrel.notes.presentation.viewmodel.NotesViewModel

object Routes {
    const val LIST = "list"
    const val EDITOR = "editor"
    const val FOLDERS = "folders"
    const val PRIVATE = "private"
    const val REMINDERS = "reminders"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val GUIDE = "guide"
    fun editor(noteId: Long) = "editor/$noteId"
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        // Best-effort widget refresh when returning to the app/home.
        refreshListWidget(this)
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val db = Room.databaseBuilder(applicationContext, NotesDatabase::class.java, "notes.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        val viewModel = NotesViewModel(
            RoomNotesRepository(db),
            PreferencesRepository(applicationContext),
        )
        setContent {
            // Refresh the live list widget whenever stored notes change.
            LaunchedEffect(Unit) {
                viewModel.notes.collect { refreshListWidget(applicationContext) }
            }
            val theme by viewModel.theme.collectAsState()
            NotesTheme(theme = theme) {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route
                val mainTabs = listOf(
                    Triple(Routes.LIST, "Notes", Symbols.HOME),
                    Triple(Routes.FOLDERS, "Folders", Symbols.FOLDER),
                    Triple(Routes.REMINDERS, "Reminders", Symbols.ALARM),
                    Triple(Routes.SETTINGS, "Settings", Symbols.SETTINGS),
                )
                val showBottomBar = currentRoute in mainTabs.map { it.first }
                var quickNoteConsumed by remember { mutableStateOf(false) }
                // Decide the start destination from the FIRST real DataStore
                // value, not from the StateFlow's initial false (which would
                // wrongly send returning users to the guide every launch).
                var startRoute by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    startRoute = if (viewModel.isGuideDone()) Routes.LIST else Routes.GUIDE
                }
                val privateUnlocked by viewModel.privateUnlocked.collectAsState()
                LaunchedEffect(quickNoteConsumed) {
                    if (!quickNoteConsumed && intent.getBooleanExtra("new_note", false)) {
                        quickNoteConsumed = true
                        viewModel.createNote(NoteType.NOTE) { id ->
                            navController.navigate(Routes.editor(id))
                        }
                    }
                    // Widget row tap / reminder notification tap: open that note,
                    // unless it is a locked private note — then go to Private.
                    val noteIdExtra = intent.getLongExtra("note_id", -1L)
                    if (!quickNoteConsumed && noteIdExtra > 0) {
                        quickNoteConsumed = true
                        val target = viewModel.getNote(noteIdExtra)
                        if (target?.isPrivate == true && !privateUnlocked) {
                            navController.navigate(Routes.PRIVATE)
                        } else {
                            navController.navigate(Routes.editor(noteIdExtra))
                        }
                    }
                }
                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                mainTabs.forEach { (route, label, icon) ->
                                    val selected = currentRoute == route
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(route) {
                                                popUpTo(Routes.LIST) { inclusive = false }
                                                launchSingleTop = true
                                            }
                                        },
                                        icon = { SymbolIcon(icon, filled = selected) },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                    },
                    topBar = {
                        if (!showBottomBar && currentRoute != null && currentRoute != Routes.GUIDE) {
                            androidx.compose.material3.TopAppBar(
                                title = { Text("Notes") },
                                navigationIcon = {
                                    IconButton(onClick = { navController.popBackStack() }) {
                                        SymbolIcon(Symbols.ARROW_BACK)
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    val start = startRoute
                    if (start == null) {
                        Text("Loading…", modifier = Modifier.padding(innerPadding))
                    } else {
                    NavHost(
                        navController = navController,
                        startDestination = start,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Routes.LIST) {
                            NotesListScreen(
                                viewModel = viewModel,
                                onOpenNote = { navController.navigate(Routes.editor(it)) },
                                onOpenPrivate = { navController.navigate(Routes.PRIVATE) },
                                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                            )
                        }
                        composable(
                            route = "editor/{noteId}",
                            arguments = listOf(navArgument("noteId") { type = NavType.LongType })
                        ) { entry ->
                            EditorScreen(
                                noteId = entry.arguments?.getLong("noteId") ?: 0L,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(Routes.FOLDERS) {
                            FoldersScreen(viewModel = viewModel, onOpenFolder = { folderId ->
                                viewModel.folderFilterId.value = folderId
                                viewModel.listTab.value = com.dusrel.notes.presentation.viewmodel.ListTab.ACTIVE
                                navController.navigate(Routes.LIST) { launchSingleTop = true }
                            })
                        }
                        composable(Routes.PRIVATE) {
                            PrivateScreen(viewModel = viewModel, onOpenNote = { navController.navigate(Routes.editor(it)) })
                        }
                        composable(Routes.REMINDERS) {
                            RemindersScreen(viewModel = viewModel, onOpenNote = { navController.navigate(Routes.editor(it)) })
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                viewModel = viewModel,
                                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                                onOpenPrivate = { navController.navigate(Routes.PRIVATE) },
                            )
                        }
                        composable(Routes.ABOUT) { AboutScreen() }
                        composable(Routes.GUIDE) {
                            GuideScreen(onDone = {
                                viewModel.markGuideDone()
                                navController.navigate(Routes.LIST) {
                                    popUpTo(Routes.GUIDE) { inclusive = true }
                                }
                            })
                        }
                    }
                    }
                }
            }
        }
    }
}
