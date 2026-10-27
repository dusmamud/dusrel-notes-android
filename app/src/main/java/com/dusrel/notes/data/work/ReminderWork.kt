package com.dusrel.notes.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dusrel.notes.R
import com.dusrel.notes.data.local.NotesDatabase
import com.dusrel.notes.domain.reminder.ReminderAlarmScheduler
import com.dusrel.notes.domain.reminder.ReminderCandidate
import com.dusrel.notes.domain.reminder.ReminderScheduler
import java.util.concurrent.TimeUnit

const val REMINDER_CHANNEL_ID = "note_reminders"

fun scheduleReminder(context: Context, noteId: Long, title: String, atMillis: Long) {
    val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0L)
    val request = OneTimeWorkRequestBuilder<ReminderWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .addTag("reminder_$noteId")
        .setInputData(workDataOf("noteId" to noteId, "title" to title))
        .build()
    WorkManager.getInstance(context).enqueue(request)
}

fun cancelReminder(context: Context, noteId: Long) {
    WorkManager.getInstance(context).cancelAllWorkByTag("reminder_$noteId")
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val noteId = inputData.getLong("noteId", 0L)
        val title = inputData.getString("title") ?: "Note"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID, "Note reminders", NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(applicationContext, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_note)
            .setContentTitle(applicationContext.getString(R.string.reminder_notification_title))
            .setContentText(title)
            .setAutoCancel(true)
            .build()
        manager.notify(noteId.toInt(), notification)
        return Result.success()
    }
}

/** Re-schedules future reminders after a reboot, on the correct path each time. */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        Thread {
            try {
                val db = Room.databaseBuilder(context, NotesDatabase::class.java, "notes.db")
                    .addMigrations(
                        com.dusrel.notes.data.local.MIGRATION_1_2,
                        com.dusrel.notes.data.local.MIGRATION_2_3,
                    ).build()
                kotlinx.coroutines.runBlocking {
                    val now = System.currentTimeMillis()
                    val notes = db.noteDao().getAll().map {
                        ReminderCandidate(it.id, it.title, it.reminderTime, it.trashed)
                    }
                    val scheduler = ReminderScheduler(
                        ExactAlarmScheduler(context),
                        object : ReminderAlarmScheduler {
                            override fun schedule(noteId: Long, title: String, atMillis: Long) =
                                scheduleReminder(context, noteId, title, atMillis)
                            override fun cancel(noteId: Long) = cancelReminder(context, noteId)
                        },
                    )
                    scheduler.rescheduleAfterBoot(
                        notes, now, ExactAlarmScheduler.canScheduleExactAlarms(context)
                    )
                    db.close()
                }
            } catch (_: Exception) {
                // Best effort: a missed re-schedule must never crash boot.
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
