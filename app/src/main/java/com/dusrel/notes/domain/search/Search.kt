package com.dusrel.notes.domain.search

import com.dusrel.notes.domain.model.Folder
import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.TodoItem

/**
 * Pure full-text search: matches note title/content, todo item text and
 * folder names. No Android classes, so it is unit-testable on the JVM.
 * An empty query returns every note.
 */
fun searchNotes(
    notes: List<Note>,
    todosByNote: Map<Long, List<TodoItem>>,
    foldersById: Map<Long, Folder>,
    query: String,
): List<Note> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return notes
    return notes.filter { note ->
        note.title.lowercase().contains(q) ||
            note.content.lowercase().contains(q) ||
            (todosByNote[note.id].orEmpty().any { it.text.lowercase().contains(q) }) ||
            (note.folderId?.let { foldersById[it]?.name?.lowercase()?.contains(q) } == true)
    }
}
