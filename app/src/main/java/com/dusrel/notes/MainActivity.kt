package com.dusrel.notes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.dusrel.notes.presentation.widgets.refreshListWidget

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
            LaunchedEffect(Unit) {
                viewModel.notes.collect { refreshListWidget(applicationContext) }
            }
            val theme by viewModel.theme.collectAsState()
            NotesTheme(theme = theme) {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route

                var quickNoteConsumed by remember { mutableStateOf(false) }
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

                // Show top app bar only on secondary sub-screens (not on home or editor)
                val showSubScreenTopBar = currentRoute in listOf(
                    Routes.FOLDERS, Routes.PRIVATE, Routes.REMINDERS, Routes.SETTINGS, Routes.ABOUT
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        if (showSubScreenTopBar) {
                            TopAppBar(
                                title = {
                                    Text(
                                        when (currentRoute) {
                                            Routes.FOLDERS -> "Folders"
                                            Routes.PRIVATE -> "Private Notes"
                                            Routes.REMINDERS -> "Reminders"
                                            Routes.SETTINGS -> "Settings"
                                            Routes.ABOUT -> "About"
                                            else -> ""
                                        }
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { navController.popBackStack() }) {
                                        SymbolIcon(Symbols.ARROW_BACK)
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors()
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
                            modifier = Modifier.padding(if (showSubScreenTopBar) innerPadding else androidx.compose.foundation.layout.PaddingValues())
                        ) {
                            composable(Routes.LIST) {
                                NotesListScreen(
                                    viewModel = viewModel,
                                    onOpenNote = { navController.navigate(Routes.editor(it)) },
                                    onOpenPrivate = { navController.navigate(Routes.PRIVATE) },
                                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                                    onOpenFolders = { navController.navigate(Routes.FOLDERS) },
                                    onOpenReminders = { navController.navigate(Routes.REMINDERS) },
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
                                    navController.navigate(Routes.LIST) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
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
