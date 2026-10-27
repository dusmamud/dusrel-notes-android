package com.dusrel.notes.data.local

import com.dusrel.notes.domain.model.Folder
import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.NoteType
import com.dusrel.notes.domain.model.TodoItem
import com.dusrel.notes.domain.repository.NotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private fun Note.toEntity() = NoteEntity(
    id, title, content, type.name, folderId, color, pinned, archived, trashed,
    isPrivate, reminderTime, doodleData, createdAt, updatedAt,
    richTextSpans, encryptedTitle, encryptedContent, encryptedRichTextSpans, isEncrypted, reminderExact
)
private fun NoteEntity.toDomain() = Note(
    id, title, content, runCatching { NoteType.valueOf(type) }.getOrDefault(NoteType.NOTE),
    folderId, color, pinned, archived, trashed, isPrivate, reminderTime, doodleData, createdAt, updatedAt,
    richTextSpans, encryptedTitle, encryptedContent, encryptedRichTextSpans, isEncrypted, reminderExact
)
private fun TodoItem.toEntity() = TodoEntity(id, noteId, text, done, position)
private fun TodoEntity.toDomain() = TodoItem(id, noteId, text, done, position)
private fun Folder.toEntity() = FolderEntity(id, name, createdAt)
private fun FolderEntity.toDomain() = Folder(id, name, createdAt)

class RoomNotesRepository(private val db: NotesDatabase) : NotesRepository {
    override fun observeNotes(): Flow<List<Note>> = db.noteDao().observeAll().map { list -> list.map { it.toDomain() } }
    override fun observeTodos(noteId: Long): Flow<List<TodoItem>> = db.todoDao().observeForNote(noteId).map { list -> list.map { it.toDomain() } }
    override fun observeFolders(): Flow<List<Folder>> = db.folderDao().observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun getNote(id: Long): Note? = db.noteDao().getById(id)?.toDomain()
    override suspend fun getTodos(noteId: Long): List<TodoItem> = db.todoDao().getForNote(noteId).map { it.toDomain() }
    override suspend fun getFolders(): List<Folder> = db.folderDao().getAll().map { it.toDomain() }
    override suspend fun upsertNote(note: Note): Long = db.noteDao().upsert(note.toEntity())
    override suspend fun deleteNotePermanently(id: Long) {
        db.todoDao().deleteForNote(id)
        db.noteDao().deleteById(id)
    }
    override suspend fun upsertTodo(item: TodoItem): Long = db.todoDao().upsert(item.toEntity())
    override suspend fun deleteTodo(id: Long) = db.todoDao().deleteById(id)
    override suspend fun deleteTodosForNote(noteId: Long) = db.todoDao().deleteForNote(noteId)
    override suspend fun upsertFolder(folder: Folder): Long = db.folderDao().upsert(folder.toEntity())
    override suspend fun deleteFolder(id: Long) = db.folderDao().deleteById(id)
    override suspend fun searchNotes(query: String): List<Note> = db.noteDao().search(query).map { it.toDomain() }
    override suspend fun replaceAll(notes: List<Note>, todos: List<TodoItem>, folders: List<Folder>) {
        db.noteDao().deleteAll(); db.todoDao().deleteAll(); db.folderDao().deleteAll()
        folders.forEach { db.folderDao().upsert(it.toEntity()) }
        notes.forEach { db.noteDao().upsert(it.toEntity()) }
        todos.forEach { db.todoDao().upsert(it.toEntity()) }
    }
}
