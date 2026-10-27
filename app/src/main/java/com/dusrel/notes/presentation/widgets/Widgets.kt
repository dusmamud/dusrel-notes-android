package com.dusrel.notes.presentation.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dusrel.notes.MainActivity
import com.dusrel.notes.R

/** Ask the list widget to re-read Room data (call after notes change). */
fun refreshListWidget(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, NotesListWidgetProvider::class.java))
    if (ids.isNotEmpty()) {
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_notes_list_view)
    }
}

private fun launchIntent(context: Context, newNote: Boolean): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        if (newNote) putExtra("new_note", true)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    return PendingIntent.getActivity(
        context, if (newNote) 2 else 1, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/** Home-screen widget: live note list (RemoteViewsService) + entry buttons. */
class NotesListWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_notes_list)
            views.setOnClickPendingIntent(R.id.widget_open_button, launchIntent(context, false))
            views.setOnClickPendingIntent(R.id.widget_new_button, launchIntent(context, true))
            // Live list: adapter + click template (row fill-in carries note_id).
            views.setRemoteAdapter(
                R.id.widget_notes_list_view,
                Intent(context, NotesWidgetService::class.java),
            )
            views.setPendingIntentTemplate(
                R.id.widget_notes_list_view,
                PendingIntent.getActivity(
                    context, 3, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            manager.updateAppWidget(id, views)
        }
    }
}

/** Small widget with a single quick-note button (RemoteViews, no Glance). */
class QuickNoteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick_note)
            views.setOnClickPendingIntent(R.id.widget_quick_button, launchIntent(context, true))
            manager.updateAppWidget(id, views)
        }
    }
}
