package com.dusrel.notes.backup

import com.dusrel.notes.domain.backup.BackupCodec
import com.dusrel.notes.domain.backup.BackupData
import com.dusrel.notes.domain.model.Folder
import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.NoteType
import com.dusrel.notes.domain.model.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    @Test fun roundTripPreservesEverything() {
        val data = BackupData(
            notes = listOf(
                Note(id = 5, title = "T \"quoted\"", content = "line1\nline2 **bold**", type = NoteType.TODO,
                    folderId = 2, color = 123, pinned = true, isPrivate = true,
                    reminderTime = 1700000000000, doodleData = "[]", createdAt = 100, updatedAt = 200)
            ),
            todos = listOf(TodoItem(id = 7, noteId = 5, text = "do it", done = true, position = 0)),
            folders = listOf(Folder(id = 2, name = "Work", createdAt = 50)),
        )
        val restored = BackupCodec.import(BackupCodec.export(data))
        assertEquals(data, restored)
    }

    @Test fun roundTripPreservesSpansAndCiphertextFields() {
        val data = BackupData(
            notes = listOf(
                Note(
                    id = 9, title = "Secret", content = "body",
                    richTextSpans = """{"text":"body","spans":[{"start":0,"end":4,"style":"BOLD"}],"paragraphs":[]}""",
                    encryptedTitle = "AAA", encryptedContent = "BBB",
                    encryptedRichTextSpans = "CCC", isEncrypted = true, reminderExact = true,
                )
            ),
            todos = emptyList(), folders = emptyList(),
        )
        val restored = BackupCodec.import(BackupCodec.export(data))
        assertEquals(data, restored)
        assertEquals("CCC", restored.notes[0].encryptedRichTextSpans)
        assertTrue(restored.notes[0].reminderExact)
    }

    @Test fun nullFolderAndReminderSurvive() {
        val data = BackupData(listOf(Note(id = 1, title = "plain")), emptyList(), emptyList())
        val restored = BackupCodec.import(BackupCodec.export(data))
        assertNull(restored.notes[0].folderId)
        assertNull(restored.notes[0].reminderTime)
        assertEquals(NoteType.NOTE, restored.notes[0].type)
    }

    @Test fun emptyBackupRoundTrips() {
        val restored = BackupCodec.import(BackupCodec.export(BackupData(emptyList(), emptyList(), emptyList())))
        assertTrue(restored.notes.isEmpty() && restored.todos.isEmpty() && restored.folders.isEmpty())
    }

    @Test fun unknownNoteTypeDefaultsToNote() {
        val json = """{"version":1,"folders":[],"notes":[{"id":1,"title":"x","type":"BOGUS"}],"todos":[]}"""
        assertEquals(NoteType.NOTE, BackupCodec.import(json).notes[0].type)
    }
}
