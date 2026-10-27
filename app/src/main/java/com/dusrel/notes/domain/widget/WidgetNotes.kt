package com.dusrel.notes.domain.widget

import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.SortOrder
import com.dusrel.notes.domain.model.sortNotes

/** Maximum snippet length for a widget row. Snippets are truncated, not ellipsised. */
const val WIDGET_SNIPPET_MAX_LENGTH = 80

/** One row of the live list widget. Pure data, no Android classes. */
data class WidgetNoteItem(
    val id: Long,
    val title: String,
    val snippet: String,
    /** Formatted updated date supplied by the caller ("" = unknown). */
    val dateText: String = "",
    val pinned: Boolean = false,
)

/** Row meta line: pin indicator plus date, e.g. "Pinned • 12 Oct 2026". */
fun widgetMetaLine(item: WidgetNoteItem): String {
    val parts = listOfNotNull(
        if (item.pinned) "Pinned" else null,
        item.dateText.ifBlank { null },
    )
    return parts.joinToString(" • ")
}

/**
 * Snippet for one note: all whitespace runs (including newlines) collapsed to a
 * single space, trimmed, then truncated to [WIDGET_SNIPPET_MAX_LENGTH] chars.
 */
fun widgetSnippet(content: String): String {
    val collapsed = content.replace(Regex("\\s+"), " ").trim()
    return if (collapsed.length <= WIDGET_SNIPPET_MAX_LENGTH) {
        collapsed
    } else {
        collapsed.substring(0, WIDGET_SNIPPET_MAX_LENGTH)
    }
}

/**
 * The notes shown in the live list widget: non-private, non-trashed,
 * non-archived only, pinned-first then updated-desc. The order reuses the
 * project's existing [sortNotes] with [SortOrder.PINNED_FIRST], which is
 * exactly pinned-desc then updatedAt-desc, so no second sort implementation.
 * Titles are passed through verbatim (no "Untitled" fallback invented here).
 */
/**
 * The [formatDate] caller formats [Note.updatedAt] for display; the default
 * yields no date so pure-JVM callers that do not care about dates stay simple.
 * The widget service passes a real formatter (see NotesWidgetService).
 */
fun widgetNotes(
    notes: List<Note>,
    formatDate: (Long) -> String = { "" },
): List<WidgetNoteItem> =
    sortNotes(
        notes.filter { !it.isPrivate && !it.trashed && !it.archived },
        SortOrder.PINNED_FIRST,
    ).map { note ->
        WidgetNoteItem(
            note.id, note.title, widgetSnippet(note.content),
            dateText = formatDate(note.updatedAt), pinned = note.pinned,
        )
    }
