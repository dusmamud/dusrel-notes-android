package com.dusrel.notes.domain.sync

import com.dusrel.notes.domain.model.Note

/**
 * Clean seam where a backend could be added later. This app is local-first
 * and ships with [NoOpSyncProvider] only; no sync is ever performed and
 * no account or cloud code exists.
 */
interface SyncProvider {
    val name: String
    suspend fun upload(note: Note): Boolean
    suspend fun download(): List<Note>
}

class NoOpSyncProvider : SyncProvider {
    override val name: String = "none"
    override suspend fun upload(note: Note): Boolean = false
    override suspend fun download(): List<Note> = emptyList()
}
