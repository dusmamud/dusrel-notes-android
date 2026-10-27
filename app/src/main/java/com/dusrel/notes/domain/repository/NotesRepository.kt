package com.dusrel.notes.domain.repository

import com.dusrel.notes.domain.model.Folder
import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.TodoItem
import kotlinx.coroutines.flow.Flow

interface NotesRepository {
    fun observeNotes(): Flow<List<Note>>
    fun observeTodos(noteId: Long): Flow<List<TodoItem>>
    fun observeFolders(): Flow<List<Folder>>
    suspend fun getNote(id: Long): Note?
    suspend fun getTodos(noteId: Long): List<TodoItem>
    suspend fun getFolders(): List<Folder>
    suspend fun upsertNote(note: Note): Long
    suspend fun deleteNotePermanently(id: Long)
    suspend fun upsertTodo(item: TodoItem): Long
    suspend fun deleteTodo(id: Long)
    suspend fun deleteTodosForNote(noteId: Long)
    suspend fun upsertFolder(folder: Folder): Long
    suspend fun deleteFolder(id: Long)
    suspend fun searchNotes(query: String): List<Note>
    suspend fun replaceAll(notes: List<Note>, todos: List<TodoItem>, folders: List<Folder>)
}
