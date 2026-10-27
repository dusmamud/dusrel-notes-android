package com.dusrel.notes.domain.list

import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.NoteType
import com.dusrel.notes.domain.model.TodoItem
import com.dusrel.notes.domain.model.todoProgress

/** Pure list-card helpers, no Android classes, so they are unit-testable on the JVM. */

/**
 * Card snippet: the first line of content, capped at 120 chars. A doodle
 * note with no text content gets a "Doodle" fallback instead of a blank line.
 */
fun listSnippet(note: Note): String {
    val first = note.content.lines().firstOrNull()?.take(120) ?: ""
    if (first.isNotBlank()) return first
    return if (note.type == NoteType.DOODLE) "Doodle" else ""
}

/** Progress label for a checklist note, e.g. "2 of 5 done". */
fun todoProgressLabel(items: List<TodoItem>): String {
    val (done, total) = todoProgress(items)
    return "$done of $total done"
}

/**
 * Second meta line under the snippet: the formatted updated date, plus the
 * checklist progress label when one applies. The caller formats the date
 * with the observable locale; this function only joins the parts.
 */
fun listMetaLine(dateText: String, progressLabel: String?): String =
    if (progressLabel.isNullOrBlank()) dateText else "$dateText • $progressLabel"
