package com.dusrel.notes.domain.list

import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.NoteType
import com.dusrel.notes.domain.model.TodoItem

/** Completion filter for the dedicated Todo destination. */
enum class TodoFilter { ALL, OPEN, DONE }

/**
 * Pure Todo-destination filtering, no Android classes, so it is
 * unit-testable on the JVM. Rules (original to this app, no new model):
 * - Only checklist notes (NoteType.TODO) appear here; text/doodle notes stay
 *   on the Notes list. No new table, no new note type.
 * - Trashed and archived checklists are excluded: trashed ones stay in the
 *   Trash tab and archived ones in the Archived tab of the Notes list, so
 *   the Todo destination never hides a note's lifecycle state.
 * - Locked private checklists are hidden until unlocked, same as the list.
 * - DONE means every item is done AND there is at least one item; an empty
 *   checklist is OPEN, not DONE. OPEN is everything not DONE.
 */
fun todoNotes(notes: List<Note>, unlocked: Boolean): List<Note> =
    notes.filter { note ->
        note.type == NoteType.TODO && !note.trashed && !note.archived &&
            (!note.isPrivate || unlocked)
    }

fun filterTodosByCompletion(
    notes: List<Note>,
    todosByNote: Map<Long, List<TodoItem>>,
    filter: TodoFilter,
): List<Note> {
    if (filter == TodoFilter.ALL) return notes
    return notes.filter { note ->
        val items = todosByNote[note.id].orEmpty()
        val done = items.isNotEmpty() && items.all { it.done }
        if (filter == TodoFilter.DONE) done else !done
    }
}
