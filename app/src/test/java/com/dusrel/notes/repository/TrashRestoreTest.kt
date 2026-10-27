package com.dusrel.notes.repository

import com.dusrel.notes.FakeNotesRepository
import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.TodoItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashRestoreTest {
    @Test fun trashAndRestoreRoundTrip() = runBlocking {
        val repo = FakeNotesRepository()
        val id = repo.upsertNote(Note(title = "Keep me"))
        repo.upsertNote(repo.getNote(id)!!.copy(trashed = true))
        assertTrue(repo.getNote(id)!!.trashed)
        repo.upsertNote(repo.getNote(id)!!.copy(trashed = false))
        assertFalse(repo.getNote(id)!!.trashed)
    }

    @Test fun permanentDeleteRemovesNoteAndItsTodos() = runBlocking {
        val repo = FakeNotesRepository()
        val id = repo.upsertNote(Note(title = "Gone"))
        repo.upsertTodo(TodoItem(noteId = id, text = "item"))
        assertEquals(1, repo.getTodos(id).size)
        repo.deleteNotePermanently(id)
        assertNull(repo.getNote(id))
        assertTrue(repo.getTodos(id).isEmpty())
    }

    @Test fun archiveFlagIsIndependentOfTrash() = runBlocking {
        val repo = FakeNotesRepository()
        val id = repo.upsertNote(Note(title = "A", archived = true))
        assertTrue(repo.getNote(id)!!.archived)
        assertFalse(repo.getNote(id)!!.trashed)
    }
}
