package com.dusrel.notes.presentation.widgets

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.room.Room
import com.dusrel.notes.R
import com.dusrel.notes.data.local.MIGRATION_1_2
import com.dusrel.notes.data.local.MIGRATION_2_3
import com.dusrel.notes.data.local.NoteEntity
import com.dusrel.notes.data.local.NotesDatabase
import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.model.NoteType
import com.dusrel.notes.domain.widget.WidgetNoteItem
import com.dusrel.notes.domain.widget.widgetMetaLine
import com.dusrel.notes.domain.widget.widgetNotes
import kotlinx.coroutines.runBlocking

/**
 * List-widget data source. The coordinator wires this into
 * NotesListWidgetProvider (setRemoteAdapter) and the manifest (service entry);
 * this file on its own changes no provider, manifest, or other widget.
 * The quick-note widget is untouched and unaffected: it has its own provider
 * and layout and never uses this service.
 */
class NotesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        NotesRemoteViewsFactory(applicationContext)
}

class NotesRemoteViewsFactory(private val context: Context) :
    RemoteViewsService.RemoteViewsFactory {

    private var db: NotesDatabase? = null
    private var items: List<WidgetNoteItem> = emptyList()

    override fun onCreate() {
        if (db == null) {
            // Same database name as MainActivity ("notes.db") so the widget
            // reads the same notes the app writes.
            db = Room.databaseBuilder(
                context.applicationContext,
                NotesDatabase::class.java,
                "notes.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        }
    }

    override fun onDataSetChanged() {
        // Room has no read-only builder flag; read-only is enforced by only
        // ever calling the SELECT query getAll() here, never upsert/delete.
        val database = db ?: Room.databaseBuilder(
            context.applicationContext,
            NotesDatabase::class.java,
            "notes.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { db = it }
        // Date formatting lives here (not in a composable), so the device
        // locale is fine; the pure widgetNotes() function stays locale-free.
        val dateFormat = java.text.SimpleDateFormat(
            "dd MMM yyyy", java.util.Locale.getDefault(),
        )
        items = widgetNotes(
            runBlocking { database.noteDao().getAll() }.map { it.toDomain() },
            formatDate = { millis -> dateFormat.format(java.util.Date(millis)) },
        )
    }

    override fun onDestroy() {
        db?.close()
        db = null
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_note_item)
        val item = items.getOrNull(position) ?: return views
        views.setTextViewText(R.id.widget_note_title, item.title)
        views.setTextViewText(R.id.widget_note_snippet, item.snippet)
        views.setTextViewText(R.id.widget_note_meta, widgetMetaLine(item))
        // Fill-in intent: the provider's PendingIntent template supplies the
        // MainActivity target; this carries only the clicked note's id.
        // Coordinator note: MainActivity must also handle the "note_id" extra
        // (it currently handles only "new_note") for the click to open the editor.
        val fillIn = Intent().apply { putExtra("note_id", item.id) }
        views.setOnClickFillInIntent(R.id.widget_note_row, fillIn)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    // Same entity -> domain mapping as RoomNotesRepository (private there,
    // so duplicated, not imported); unknown stored types fall back to NOTE.
    private fun NoteEntity.toDomain(): Note = Note(
        id, title, content,
        runCatching { NoteType.valueOf(type) }.getOrDefault(NoteType.NOTE),
        folderId, color, pinned, archived, trashed, isPrivate,
        reminderTime, doodleData, createdAt, updatedAt,
    )
}
