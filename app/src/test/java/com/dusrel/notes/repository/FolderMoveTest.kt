package com.dusrel.notes.repository

import com.dusrel.notes.FakeNotesRepository
import com.dusrel.notes.domain.model.Folder
import com.dusrel.notes.domain.model.Note
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderMoveTest {
    @Test fun moveNoteToFolderAndBack() = runBlocking {
        val repo = FakeNotesRepository()
        val folderId = repo.upsertFolder(Folder(name = "Work"))
        val noteId = repo.upsertNote(Note(title = "Report"))
        repo.upsertNote(repo.getNote(noteId)!!.copy(folderId = folderId))
        assertEquals(folderId, repo.getNote(noteId)!!.folderId)
        repo.upsertNote(repo.getNote(noteId)!!.copy(folderId = null))
        assertNull(repo.getNote(noteId)!!.folderId)
    }

    @Test fun deletingFolderLeavesNoteButUnlinkedByCaller() = runBlocking {
        val repo = FakeNotesRepository()
        val folderId = repo.upsertFolder(Folder(name = "Temp"))
        val noteId = repo.upsertNote(Note(title = "x", folderId = folderId))
        repo.deleteFolder(folderId)
        assertEquals(0, repo.getFolders().size)
        // The note row itself survives; the UI moves such notes to "No folder".
        assertEquals(noteId, repo.getNote(noteId)!!.id)
    }

    @Test fun foldersListed() = runBlocking {
        val repo = FakeNotesRepository()
        repo.upsertFolder(Folder(name = "B"))
        repo.upsertFolder(Folder(name = "A"))
        assertEquals(2, repo.getFolders().size)
    }
}
