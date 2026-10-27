package com.dusrel.notes.domain.search

import com.dusrel.notes.domain.model.Folder
import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.TodoItem
import java.util.Locale

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
    // Locale-explicit lowercasing: the default locale (e.g. Turkish) would
    // change how "I" lowercases and break matching. Logic only, no UI change.
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return notes
    return notes.filter { note ->
        note.title.lowercase(Locale.ROOT).contains(q) ||
            note.content.lowercase(Locale.ROOT).contains(q) ||
            (todosByNote[note.id].orEmpty().any { it.text.lowercase(Locale.ROOT).contains(q) }) ||
            (note.folderId?.let { foldersById[it]?.name?.lowercase(Locale.ROOT)?.contains(q) } == true)
    }
}
