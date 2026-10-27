package com.dusrel.notes.todo

import com.dusrel.notes.domain.list.TodoFilter
import com.dusrel.notes.domain.list.filterTodosByCompletion
import com.dusrel.notes.domain.list.todoNotes
import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.NoteType
import com.dusrel.notes.domain.model.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoListTest {

    @Test fun onlyActiveChecklistsWhenLocked() {
        val notes = listOf(
            Note(id = 1, type = NoteType.TODO),
            Note(id = 2, type = NoteType.NOTE),
            Note(id = 3, type = NoteType.DOODLE),
            Note(id = 4, type = NoteType.TODO, trashed = true),
            Note(id = 5, type = NoteType.TODO, archived = true),
            Note(id = 6, type = NoteType.TODO, isPrivate = true),
        )
        assertEquals(listOf(1L), todoNotes(notes, unlocked = false).map { it.id })
    }

    @Test fun lockedPrivateChecklistAppearsWhenUnlocked() {
        val notes = listOf(Note(id = 6, type = NoteType.TODO, isPrivate = true))
        assertEquals(listOf(6L), todoNotes(notes, unlocked = true).map { it.id })
    }

    @Test fun completionFilterDoneRequiresAtLeastOneItem() {
        val notes = listOf(
            Note(id = 1, type = NoteType.TODO),
            Note(id = 2, type = NoteType.TODO),
            Note(id = 3, type = NoteType.TODO),
        )
        val todos = mapOf(
            1L to listOf(TodoItem(done = true), TodoItem(done = true)),
            2L to listOf(TodoItem(done = true), TodoItem(done = false)),
            // Note 3 has no items: OPEN, not DONE.
        )
        assertEquals(listOf(1L), filterTodosByCompletion(notes, todos, TodoFilter.DONE).map { it.id })
        assertEquals(listOf(2L, 3L), filterTodosByCompletion(notes, todos, TodoFilter.OPEN).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), filterTodosByCompletion(notes, todos, TodoFilter.ALL).map { it.id })
    }
}
