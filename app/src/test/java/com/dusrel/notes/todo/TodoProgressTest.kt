package com.dusrel.notes.todo

import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.SortOrder
import com.dusrel.notes.domain.model.TodoItem
import com.dusrel.notes.domain.model.sortNotes
import com.dusrel.notes.domain.model.todoProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoProgressTest {
    @Test fun progressCountsDoneOnly() {
        val items = listOf(
            TodoItem(text = "a", done = true),
            TodoItem(text = "b", done = false),
            TodoItem(text = "c", done = true),
        )
        assertEquals(2 to 3, todoProgress(items))
    }

    @Test fun progressEmptyIsZero() {
        assertEquals(0 to 0, todoProgress(emptyList()))
    }

    @Test fun progressAllDone() {
        assertEquals(3 to 3, todoProgress(List(3) { TodoItem(done = true) }))
    }

    @Test fun sortPinnedFirst() {
        val notes = listOf(
            Note(id = 1, title = "b", pinned = false, updatedAt = 300),
            Note(id = 2, title = "a", pinned = true, updatedAt = 100),
            Note(id = 3, title = "c", pinned = true, updatedAt = 200),
        )
        assertEquals(listOf(3L, 2L, 1L), sortNotes(notes, SortOrder.PINNED_FIRST).map { it.id })
    }

    @Test fun sortByTitleIsCaseInsensitive() {
        val notes = listOf(Note(id = 1, title = "banana"), Note(id = 2, title = "Apple"))
        assertEquals(listOf(2L, 1L), sortNotes(notes, SortOrder.TITLE_ASC).map { it.id })
    }

    @Test fun sortByUpdatedDesc() {
        val notes = listOf(Note(id = 1, updatedAt = 10), Note(id = 2, updatedAt = 30), Note(id = 3, updatedAt = 20))
        assertEquals(listOf(2L, 3L, 1L), sortNotes(notes, SortOrder.UPDATED_DESC).map { it.id })
    }
}
