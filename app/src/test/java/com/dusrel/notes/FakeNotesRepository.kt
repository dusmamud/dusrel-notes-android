package com.dusrel.notes

import com.dusrel.notes.domain.model.Folder
import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.TodoItem
import com.dusrel.notes.domain.repository.NotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeNotesRepository : NotesRepository {
    private val notesFlow = MutableStateFlow<List<Note>>(emptyList())
    private val foldersFlow = MutableStateFlow<List<Folder>>(emptyList())
    private val todos = mutableMapOf<Long, MutableList<TodoItem>>()
    private var nextNoteId = 1L
    private var nextTodoId = 1L
    private var nextFolderId = 1L

    override fun observeNotes(): Flow<List<Note>> = notesFlow
    override fun observeTodos(noteId: Long): Flow<List<TodoItem>> =
        notesFlow.map { todos[noteId].orEmpty() }
    override fun observeFolders(): Flow<List<Folder>> = foldersFlow
    override suspend fun getNote(id: Long): Note? = notesFlow.value.firstOrNull { it.id == id }
    override suspend fun getTodos(noteId: Long): List<TodoItem> = todos[noteId].orEmpty().toList()
    override suspend fun getFolders(): List<Folder> = foldersFlow.value.toList()
    override suspend fun upsertNote(note: Note): Long {
        val id = if (note.id == 0L) nextNoteId++ else note.id
        val saved = note.copy(id = id)
        notesFlow.value = notesFlow.value.filter { it.id != id } + saved
        return id
    }
    override suspend fun deleteNotePermanently(id: Long) {
        notesFlow.value = notesFlow.value.filter { it.id != id }
        todos.remove(id)
    }
    override suspend fun upsertTodo(item: TodoItem): Long {
        val id = if (item.id == 0L) nextTodoId++ else item.id
        val list = todos.getOrPut(item.noteId) { mutableListOf() }
        list.removeAll { it.id == id }
        list += item.copy(id = id)
        return id
    }
    override suspend fun deleteTodo(id: Long) {
        todos.values.forEach { list -> list.removeAll { it.id == id } }
    }
    override suspend fun deleteTodosForNote(noteId: Long) { todos.remove(noteId) }
    override suspend fun upsertFolder(folder: Folder): Long {
        val id = if (folder.id == 0L) nextFolderId++ else folder.id
        val saved = folder.copy(id = id)
        foldersFlow.value = foldersFlow.value.filter { it.id != id } + saved
        return id
    }
    override suspend fun deleteFolder(id: Long) {
        foldersFlow.value = foldersFlow.value.filter { it.id != id }
    }
    override suspend fun searchNotes(query: String): List<Note> {
        val q = query.lowercase()
        return notesFlow.value.filter {
            it.title.lowercase().contains(q) || it.content.lowercase().contains(q)
        }
    }
    override suspend fun replaceAll(notes: List<Note>, todos: List<TodoItem>, folders: List<Folder>) {
        notesFlow.value = notes
        foldersFlow.value = folders
        this.todos.clear()
        todos.forEach { t -> this.todos.getOrPut(t.noteId) { mutableListOf() } += t }
    }
}
