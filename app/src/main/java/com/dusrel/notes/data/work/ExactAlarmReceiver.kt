package com.dusrel.notes.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dusrel.notes.MainActivity
import com.dusrel.notes.R

/**
 * Fires when an exact reminder alarm goes off and posts the reminder
 * notification. Mirrors the notification built by ReminderWorker in
 * ReminderWork.kt (same channel id, icon, title string, notification id
 * scheme) so an exact and a fallback notification for the same note
 * replace rather than duplicate each other.
 *
 * Wiring (coordinator — manifest only, this file is complete as-is):
 * ```xml
 * <receiver
 *     android:name=".data.work.ExactAlarmReceiver"
 *     android:exported="false" />
 * ```
 * No intent-filter: it is only ever targeted by the explicit PendingIntent
 * built in [ExactAlarmScheduler], so it must stay non-exported.
 *
 * Tapping the notification opens [MainActivity] with [EXTRA_NOTE_ID];
 * opening that note from the extra is the coordinator's MainActivity work
 * (this receiver only delivers the id, it creates no activity).
 */
class ExactAlarmReceiver : BroadcastReceiver() {

    companion object {
        /** Long extra: the reminded note's id. */
        const val EXTRA_NOTE_ID = "note_id"

        /** String extra: the reminded note's title (notification text). */
        const val EXTRA_NOTE_TITLE = "note_title"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, 0L)
        val title = intent.getStringExtra(EXTRA_NOTE_TITLE) ?: "Note"

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID, "Note reminders", NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_NOTE_ID, noteId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            noteId.toInt(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_note)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(title)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()
        // Same id scheme as ReminderWorker (noteId as Int): an exact and a
        // fallback firing for the same note collapse into one notification.
        manager.notify(noteId.toInt(), notification)
    }
}
