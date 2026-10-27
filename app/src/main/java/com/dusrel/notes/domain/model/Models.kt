package com.dusrel.notes.domain.model

enum class NoteType { NOTE, TODO, DOODLE }

enum class SortOrder { UPDATED_DESC, TITLE_ASC, PINNED_FIRST }

data class Note(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val type: NoteType = NoteType.NOTE,
    val folderId: Long? = null,
    /** ARGB colour int, 0 = default (no colour). */
    val color: Int = 0,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val trashed: Boolean = false,
    val isPrivate: Boolean = false,
    val reminderTime: Long? = null,
    val doodleData: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Rich-text spans JSON (see RichText.toSpansJson); plain text stays in [content]. */
    val richTextSpans: String = "",
    /** AES-GCM ciphertext (Base64) for private notes; empty when not encrypted. */
    val encryptedTitle: String = "",
    val encryptedContent: String = "",
    /** AES-GCM ciphertext of the rich-text spans JSON (spans JSON contains the body text). */
    val encryptedRichTextSpans: String = "",
    /** True when title/content/spans live in encrypted fields, not plaintext. */
    val isEncrypted: Boolean = false,
    /** True when the reminder was scheduled on the exact-alarm path (else best-effort). */
    val reminderExact: Boolean = false,
)

data class TodoItem(
    val id: Long = 0,
    val noteId: Long = 0,
    val text: String = "",
    val done: Boolean = false,
    val position: Int = 0,
)

data class Folder(
    val id: Long = 0,
    val name: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** Pure progress calculation, no Android classes. */
fun todoProgress(items: List<TodoItem>): Pair<Int, Int> {
    val done = items.count { it.done }
    return done to items.size
}

fun sortNotes(notes: List<Note>, order: SortOrder): List<Note> = when (order) {
    SortOrder.UPDATED_DESC -> notes.sortedByDescending { it.updatedAt }
    SortOrder.TITLE_ASC -> notes.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    SortOrder.PINNED_FIRST -> notes.sortedWith(
        compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt }
    )
}
