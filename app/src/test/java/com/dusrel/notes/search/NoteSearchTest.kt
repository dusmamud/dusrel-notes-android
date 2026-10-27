package com.dusrel.notes.search

import com.dusrel.notes.domain.model.Folder
import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.TodoItem
import com.dusrel.notes.domain.search.searchNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSearchTest {
    private val notes = listOf(
        Note(id = 1, title = "Shopping", content = "Buy milk and eggs"),
        Note(id = 2, title = "Trip plan", content = "Book train tickets", folderId = 10),
        Note(id = 3, title = "Ideas", content = "Nothing relevant here"),
    )
    private val todos = mapOf(3L to listOf(TodoItem(noteId = 3, text = "Call the dentist")))
    private val folders = mapOf(10L to Folder(id = 10, name = "Travel"))

    @Test fun matchesTitle() {
        assertEquals(listOf(1L), searchNotes(notes, todos, folders, "shop").map { it.id })
    }

    @Test fun matchesContentCaseInsensitively() {
        assertEquals(listOf(1L), searchNotes(notes, todos, folders, "MILK").map { it.id })
    }

    @Test fun matchesTodoItemText() {
        assertEquals(listOf(3L), searchNotes(notes, todos, folders, "dentist").map { it.id })
    }

    @Test fun matchesFolderName() {
        assertEquals(listOf(2L), searchNotes(notes, todos, folders, "travel").map { it.id })
    }

    @Test fun emptyQueryReturnsAll() {
        assertEquals(3, searchNotes(notes, todos, folders, "   ").size)
    }

    @Test fun noMatchReturnsEmpty() {
        assertTrue(searchNotes(notes, todos, folders, "zzz-no-match").isEmpty())
    }
}
