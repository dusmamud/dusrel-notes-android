package com.dusrel.notes.domain.backup

import com.dusrel.notes.domain.model.Folder
import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.NoteType
import com.dusrel.notes.domain.model.TodoItem
import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val notes: List<Note>,
    val todos: List<TodoItem>,
    val folders: List<Folder>,
)

/**
 * Local JSON backup codec (org.json). Files are only ever written/read
 * through the Storage Access Framework by the user; nothing is uploaded.
 */
object BackupCodec {
    fun export(data: BackupData): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("folders", JSONArray().apply {
            data.folders.forEach { f ->
                put(JSONObject().apply {
                    put("id", f.id); put("name", f.name); put("createdAt", f.createdAt)
                })
            }
        })
        root.put("notes", JSONArray().apply {
            data.notes.forEach { n ->
                put(JSONObject().apply {
                    put("id", n.id); put("title", n.title); put("content", n.content)
                    put("type", n.type.name); put("folderId", if (n.folderId == null) JSONObject.NULL else n.folderId)
                    put("color", n.color); put("pinned", n.pinned); put("archived", n.archived)
                    put("trashed", n.trashed); put("isPrivate", n.isPrivate)
                    put("reminderTime", if (n.reminderTime == null) JSONObject.NULL else n.reminderTime)
                    put("doodleData", n.doodleData); put("createdAt", n.createdAt); put("updatedAt", n.updatedAt)
                    put("richTextSpans", n.richTextSpans)
                    put("encryptedTitle", n.encryptedTitle); put("encryptedContent", n.encryptedContent)
                    put("encryptedRichTextSpans", n.encryptedRichTextSpans)
                    put("isEncrypted", n.isEncrypted); put("reminderExact", n.reminderExact)
                })
            }
        })
        root.put("todos", JSONArray().apply {
            data.todos.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("noteId", t.noteId); put("text", t.text)
                    put("done", t.done); put("position", t.position)
                })
            }
        })
        return root.toString()
    }

    fun import(json: String): BackupData {
        val root = JSONObject(json)
        val folders = mutableListOf<Folder>()
        root.optJSONArray("folders")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                folders += Folder(o.optLong("id"), o.optString("name"), o.optLong("createdAt"))
            }
        }
        val notes = mutableListOf<Note>()
        root.optJSONArray("notes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                notes += Note(
                    id = o.optLong("id"),
                    title = o.optString("title"),
                    content = o.optString("content"),
                    type = runCatching { NoteType.valueOf(o.optString("type")) }.getOrDefault(NoteType.NOTE),
                    folderId = if (o.isNull("folderId")) null else o.optLong("folderId"),
                    color = o.optInt("color"),
                    pinned = o.optBoolean("pinned"),
                    archived = o.optBoolean("archived"),
                    trashed = o.optBoolean("trashed"),
                    isPrivate = o.optBoolean("isPrivate"),
                    reminderTime = if (o.isNull("reminderTime")) null else o.optLong("reminderTime"),
                    doodleData = o.optString("doodleData"),
                    createdAt = o.optLong("createdAt"),
                    updatedAt = o.optLong("updatedAt"),
                    richTextSpans = o.optString("richTextSpans"),
                    encryptedTitle = o.optString("encryptedTitle"),
                    encryptedContent = o.optString("encryptedContent"),
                    encryptedRichTextSpans = o.optString("encryptedRichTextSpans"),
                    isEncrypted = o.optBoolean("isEncrypted"),
                    reminderExact = o.optBoolean("reminderExact"),
                )
            }
        }
        val todos = mutableListOf<TodoItem>()
        root.optJSONArray("todos")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                todos += TodoItem(o.optLong("id"), o.optLong("noteId"), o.optString("text"), o.optBoolean("done"), o.optInt("position"))
            }
        }
        return BackupData(notes, todos, folders)
    }
}
