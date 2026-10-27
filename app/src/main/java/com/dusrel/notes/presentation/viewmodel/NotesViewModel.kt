package com.dusrel.notes.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dusrel.notes.data.local.PreferencesRepository
import com.dusrel.notes.data.local.ThemeSetting
import com.dusrel.notes.domain.backup.BackupData
import com.dusrel.notes.domain.backup.BackupCodec
import com.dusrel.notes.domain.crypto.PrivateVault
import com.dusrel.notes.domain.model.Folder
import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.NoteType
import com.dusrel.notes.domain.model.SortOrder
import com.dusrel.notes.domain.model.TodoItem
import com.dusrel.notes.domain.model.sortNotes
import com.dusrel.notes.domain.repository.NotesRepository
import com.dusrel.notes.domain.search.searchNotes
import com.dusrel.notes.domain.sync.NoOpSyncProvider
import com.dusrel.notes.domain.sync.SyncProvider
import javax.crypto.SecretKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ListTab { ACTIVE, ARCHIVED, TRASH }

class NotesViewModel(
    private val repository: NotesRepository,
    private val preferences: PreferencesRepository,
    @Suppress("unused") private val sync: SyncProvider = NoOpSyncProvider(),
) : ViewModel() {

    val notes: StateFlow<List<Note>> = repository.observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val folders: StateFlow<List<Folder>> = repository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val theme: StateFlow<ThemeSetting> = preferences.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeSetting.SYSTEM)
    val guideDone: StateFlow<Boolean> = preferences.guideDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val query = MutableStateFlow("")
    val sortOrder = MutableStateFlow(SortOrder.PINNED_FIRST)
    val folderFilterId = MutableStateFlow<Long?>(null)
    val listTab = MutableStateFlow(ListTab.ACTIVE)
    val privateUnlocked = MutableStateFlow(false)
    /** Encryption key for private notes — memory only, never persisted, cleared on lock. */
    private val unlockedKey = MutableStateFlow<SecretKey?>(null)

    /** Raw notes with private notes decrypted in memory while unlocked. */
    val decryptedNotes: StateFlow<List<Note>> = combine(notes, unlockedKey) { list, key ->
        if (key == null) list else list.map { decryptForDisplay(it, key) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun decryptForDisplay(note: Note, key: SecretKey): Note {
        if (!note.isPrivate || !note.isEncrypted) return note
        val plain = PrivateVault.tryDecrypt(note.encryptedTitle, key)?.let { t ->
            PrivateVault.tryDecrypt(note.encryptedContent, key)?.let { c -> t to c }
        } ?: return note
        // Spans JSON contains the body text, so it is encrypted too; a legacy
        // note whose spans are still plaintext keeps them until re-encrypted.
        val spans = if (note.encryptedRichTextSpans.isNotBlank()) {
            PrivateVault.tryDecrypt(note.encryptedRichTextSpans, key) ?: note.richTextSpans
        } else {
            note.richTextSpans
        }
        return note.copy(title = plain.first, content = plain.second, richTextSpans = spans)
    }

    /** Encrypt a private note before storage when a key is held; otherwise leave as-is.
     *  Title, content AND spans JSON are all encrypted — spans JSON embeds the
     *  full body text, so leaving it plaintext would leak a locked note. */
    private fun encryptForStorage(note: Note): Note {
        val key = unlockedKey.value ?: return note
        if (!note.isPrivate) return note
        val enc = PrivateVault.encryptNote(note.title, note.content, key)
        val encSpans = if (note.richTextSpans.isNotBlank()) {
            PrivateVault.encrypt(note.richTextSpans, key)
        } else {
            ""
        }
        return note.copy(
            title = "", content = "", richTextSpans = "",
            encryptedTitle = enc.encryptedTitle, encryptedContent = enc.encryptedContent,
            encryptedRichTextSpans = encSpans,
            isEncrypted = true,
        )
    }

    /** Re-encrypt private notes after unlock (also heals legacy plaintext spans). */
    private suspend fun reEncryptPrivateNotes(key: SecretKey) {
        val raw = repository.searchNotes("")
        raw.filter { it.isPrivate }.forEach { stored ->
            val plain = decryptForDisplay(stored, key)
            repository.upsertNote(encryptForStorage(plain))
        }
    }
    private val todosCache = MutableStateFlow<Map<Long, List<TodoItem>>>(emptyMap())

    init {
        viewModelScope.launch {
            notes.collect { list ->
                val map = mutableMapOf<Long, List<TodoItem>>()
                list.forEach { map[it.id] = repository.getTodos(it.id) }
                todosCache.value = map
            }
        }
    }

    /** Visible notes for the list screen: tab, folder, privacy, search, sort. */
    val visibleNotes: StateFlow<List<Note>> = combine(
        decryptedNotes, folders, query, sortOrder, folderFilterId, listTab, privateUnlocked, todosCache
    ) { notesList, foldersList, q, sort, folderId, tab, unlocked, todos ->
        val foldersById = foldersList.associateBy { it.id }
        var filtered = notesList.filter { note ->
            when (tab) {
                ListTab.ACTIVE -> !note.trashed && !note.archived
                ListTab.ARCHIVED -> note.archived && !note.trashed
                ListTab.TRASH -> note.trashed
            } && (folderId == null || note.folderId == folderId)
                && (!note.isPrivate || unlocked)
        }
        if (q.isNotBlank()) filtered = searchNotes(filtered, todos, foldersById, q)
        sortNotes(filtered, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun todosFor(noteId: Long): StateFlow<List<TodoItem>> =
        repository.observeTodos(noteId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getNote(id: Long): Note? {
        val note = repository.getNote(id) ?: return null
        val key = unlockedKey.value ?: return note
        return decryptForDisplay(note, key)
    }
    suspend fun getTodos(id: Long): List<TodoItem> = repository.getTodos(id)

    fun createNote(type: NoteType, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = repository.upsertNote(Note(type = type, createdAt = now, updatedAt = now))
            onCreated(id)
        }
    }

    private var saveJob: Job? = null

    /** Debounced autosave for the editor (500 ms after the last keystroke). */
    fun saveNoteDebounced(note: Note) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            repository.upsertNote(encryptForStorage(note.copy(updatedAt = System.currentTimeMillis())))
        }
    }

    fun saveNoteNow(note: Note) {
        saveJob?.cancel()
        viewModelScope.launch {
            repository.upsertNote(encryptForStorage(note.copy(updatedAt = System.currentTimeMillis())))
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            repository.upsertNote(encryptForStorage(note.copy(updatedAt = System.currentTimeMillis())))
        }
    }

    fun togglePin(note: Note) = updateNote(note.copy(pinned = !note.pinned))
    fun setArchived(note: Note, archived: Boolean) = updateNote(note.copy(archived = archived))
    fun moveToTrash(note: Note) = updateNote(note.copy(trashed = true))
    fun restoreFromTrash(note: Note) = updateNote(note.copy(trashed = false))
    fun deletePermanently(note: Note) {
        viewModelScope.launch { repository.deleteNotePermanently(note.id) }
    }
    fun moveToFolder(note: Note, folderId: Long?) = updateNote(note.copy(folderId = folderId))

    fun addTodo(noteId: Long, text: String) {
        viewModelScope.launch {
            val existing = repository.getTodos(noteId)
            repository.upsertTodo(TodoItem(noteId = noteId, text = text, position = existing.size))
        }
    }
    fun toggleTodo(item: TodoItem) {
        viewModelScope.launch { repository.upsertTodo(item.copy(done = !item.done)) }
    }
    fun deleteTodo(item: TodoItem) {
        viewModelScope.launch { repository.deleteTodo(item.id) }
    }

    fun createFolder(name: String) {
        viewModelScope.launch { repository.upsertFolder(Folder(name = name)) }
    }
    fun deleteFolder(folder: Folder) {
        viewModelScope.launch { repository.deleteFolder(folder.id) }
    }

    fun setTheme(theme: ThemeSetting) {
        viewModelScope.launch { preferences.setTheme(theme) }
    }
    fun markGuideDone() {
        viewModelScope.launch { preferences.setGuideDone() }
    }
    /** First real DataStore value (not the StateFlow's initial false). */
    suspend fun isGuideDone(): Boolean = preferences.guideDone.first()
    suspend fun hasPin(): Boolean = preferences.hasPin()
    suspend fun lockoutRemainingSeconds(): Long = preferences.lockoutRemainingSeconds()

    /**
     * First-time PIN setup: creates the vault, unlocks, and re-encrypts any
     * existing private notes that were stored in plaintext before a PIN existed.
     */
    suspend fun setPin(pin: String) {
        val key = preferences.setPin(pin)
        unlockedKey.value = key
        privateUnlocked.value = true
        reEncryptPrivateNotes(key)
    }

    suspend fun unlockWithPin(pin: String): Boolean {
        val key = preferences.unlockWithPin(pin) ?: return false
        unlockedKey.value = key
        privateUnlocked.value = true
        reEncryptPrivateNotes(key)
        return true
    }
    fun lockPrivate() {
        unlockedKey.value = null
        privateUnlocked.value = false
    }

    suspend fun exportBackupJson(): String {
        // Private notes are EXCLUDED unless unlocked; when unlocked, the backup
        // carries their decrypted content (explicit user export) with stale
        // ciphertext stripped, never both plaintext and ciphertext.
        val key = unlockedKey.value
        val unlocked = privateUnlocked.value
        val snapshot = repository.searchNotes("").map { note ->
            if (note.isEncrypted && key != null) decryptForDisplay(note, key) else note
        }.filter { !it.isPrivate || unlocked }
            .map {
                if (it.isPrivate) it.copy(
                    encryptedTitle = "", encryptedContent = "", encryptedRichTextSpans = "",
                    isEncrypted = false,
                ) else it
            }
        val todos = mutableListOf<TodoItem>()
        snapshot.forEach { todos += repository.getTodos(it.id) }
        return BackupCodec.export(BackupData(snapshot, todos, repository.getFolders()))
    }

    fun importBackupJson(json: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val data = BackupCodec.import(json)
                repository.replaceAll(data.notes, data.todos, data.folders)
            }
            onDone(result.isSuccess)
        }
    }

    fun noteToShareText(note: Note, todos: List<TodoItem>): String {
        val sb = StringBuilder()
        if (note.title.isNotBlank()) sb.append(note.title).append("\n\n")
        sb.append(note.content)
        if (todos.isNotEmpty()) {
            sb.append("\n")
            todos.forEach { sb.append(if (it.done) "[x] " else "[ ] ").append(it.text).append("\n") }
        }
        return sb.toString().trim()
    }
}
