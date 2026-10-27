package com.dusrel.notes.widget

import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.widget.WIDGET_SNIPPET_MAX_LENGTH
import com.dusrel.notes.domain.widget.widgetNotes
import com.dusrel.notes.domain.widget.widgetSnippet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetNotesTest {

    @Test fun excludesPrivateTrashedAndArchived() {
        val notes = listOf(
            Note(id = 1, title = "Visible", content = "ok"),
            Note(id = 2, title = "Private", content = "secret", isPrivate = true),
            Note(id = 3, title = "Trashed", content = "gone", trashed = true),
            Note(id = 4, title = "Archived", content = "old", archived = true),
        )
        assertEquals(listOf(1L), widgetNotes(notes).map { it.id })
    }

    @Test fun pinnedFirstThenUpdatedDesc() {
        val notes = listOf(
            Note(id = 1, title = "New unpinned", content = "a", updatedAt = 300),
            Note(id = 2, title = "Old pinned", content = "b", pinned = true, updatedAt = 100),
            Note(id = 3, title = "New pinned", content = "c", pinned = true, updatedAt = 200),
            Note(id = 4, title = "Old unpinned", content = "d", updatedAt = 50),
        )
        // Pinned first (new pinned before old pinned), then unpinned newest first.
        assertEquals(listOf(3L, 2L, 1L, 4L), widgetNotes(notes).map { it.id })
    }

    @Test fun snippetTruncatesAt80CharsWithNewlineCollapse() {
        val content = "First line\nSecond line\n" + "x".repeat(100)
        val snippet = widgetSnippet(content)
        assertEquals(WIDGET_SNIPPET_MAX_LENGTH, snippet.length)
        assertFalse(snippet.contains('\n'))
        assertFalse(snippet.contains('\r'))
        assertEquals(("First line Second line " + "x".repeat(100)).take(80), snippet)
    }

    @Test fun snippetShortContentUnchangedApartFromNewlines() {
        assertEquals("hello world", widgetSnippet("hello\nworld"))
        assertEquals("", widgetSnippet(""))
    }

    @Test fun emptyListYieldsZeroItems() {
        assertTrue(widgetNotes(emptyList()).isEmpty())
    }
}
