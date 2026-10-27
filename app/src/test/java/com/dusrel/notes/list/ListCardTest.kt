package com.dusrel.notes.list

import com.dusrel.notes.domain.list.listMetaLine
import com.dusrel.notes.domain.list.listSnippet
import com.dusrel.notes.domain.list.todoProgressLabel
import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.NoteType
import com.dusrel.notes.domain.model.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ListCardTest {

    @Test fun snippetIsFirstLineCappedAt120() {
        val note = Note(content = "First line here\nSecond line")
        assertEquals("First line here", listSnippet(note))
        val long = Note(content = "x".repeat(200))
        assertEquals(120, listSnippet(long).length)
    }

    @Test fun doodleWithEmptyContentGetsDoodleFallback() {
        assertEquals("Doodle", listSnippet(Note(type = NoteType.DOODLE, content = "")))
        assertEquals("Doodle", listSnippet(Note(type = NoteType.DOODLE, content = "   ")))
    }

    @Test fun textNoteWithEmptyContentStaysBlank() {
        assertEquals("", listSnippet(Note(type = NoteType.NOTE, content = "")))
    }

    @Test fun progressLabelCountsDone() {
        val items = listOf(TodoItem(done = true), TodoItem(done = false), TodoItem(done = true))
        assertEquals("2 of 3 done", todoProgressLabel(items))
    }

    @Test fun metaLineJoinsDateAndProgress() {
        assertEquals("12 Oct 2026", listMetaLine("12 Oct 2026", null))
        assertEquals("12 Oct 2026 • 2 of 3 done", listMetaLine("12 Oct 2026", "2 of 3 done"))
        assertEquals("12 Oct 2026", listMetaLine("12 Oct 2026", ""))
    }
}
