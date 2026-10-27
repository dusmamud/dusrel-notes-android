package com.dusrel.notes.data.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.dusrel.notes.domain.reminder.ReminderAlarmScheduler

/**
 * Exact-alarm delivery path for reminders (AlarmManager), complementing the
 * existing WorkManager fallback in ReminderWork.kt (which stays untouched).
 *
 * Wiring (coordinator): construct with a Context, pass this as the
 * `exactScheduler` of [com.dusrel.notes.domain.reminder.ReminderScheduler],
 * and pass an adapter over [scheduleReminder]/[cancelReminder] as the
 * `fallbackScheduler`. Before scheduling, the coordinator checks
 * [canScheduleExactAlarms]; [ReminderScheduler.schedule] already routes to
 * the fallback when that check is false, so this class is only ever called
 * on the exact path.
 *
 * Behaviour notes:
 * - [schedule] throws SecurityException on API 31+ if the exact-alarm grant
 *   is absent at call time; callers must route via the decision first.
 * - A time in the past fires immediately (AlarmManager behaviour), matching
 *   the WorkManager fallback's coerce-to-now delay. Callers normally only
 *   schedule future times.
 * - [cancel] cancels the same PendingIntent [schedule] created (same
 *   component + same per-note request code; extras do not affect
 *   PendingIntent equality). It is also safe to call from the fallback
 *   cancel path — cancelling a non-existent alarm is a no-op — which is
 *   why [com.dusrel.notes.domain.reminder.ReminderScheduler.cancelAll]
 *   calls both schedulers.
 */
class ExactAlarmScheduler(private val context: Context) : ReminderAlarmScheduler {

    private fun alarmManager(): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun alarmIntent(noteId: Long, title: String): Intent =
        Intent(context, ExactAlarmReceiver::class.java).apply {
            putExtra(ExactAlarmReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(ExactAlarmReceiver.EXTRA_NOTE_TITLE, title)
        }

    /**
     * Unique request code per note. Room ids are auto-incrementing from 1,
     * so truncating to Int is unique for any realistic note count.
     */
    private fun requestCodeFor(noteId: Long): Int = noteId.toInt()

    private fun pendingIntent(noteId: Long, title: String, flags: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCodeFor(noteId),
            alarmIntent(noteId, title),
            flags,
        )

    override fun schedule(noteId: Long, title: String, atMillis: Long) {
        val pendingIntent = pendingIntent(
            noteId,
            title,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager().setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            atMillis,
            pendingIntent,
        )
    }

    override fun cancel(noteId: Long) {
        // FLAG_NO_CREATE: do not create a PendingIntent just to cancel it.
        // If none exists there is nothing to cancel.
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeFor(noteId),
            alarmIntent(noteId, ""),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager().cancel(pendingIntent)
        pendingIntent.cancel()
    }

    companion object {
        /**
         * Whether exact alarms may currently be scheduled.
         *
         * API 31+ (Android 12): governed by the SCHEDULE_EXACT_ALARM grant
         * (already declared in the manifest); returns
         * [AlarmManager.canScheduleExactAlarms]. Below API 31 there was no
         * such grant — exact alarms were always allowed — so this returns
         * true. Note: on API 31+ the grant can be revoked by the user at
         * any time, so check this fresh on every schedule, not once.
         */
        fun canScheduleExactAlarms(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
            val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return manager.canScheduleExactAlarms()
        }

        /**
         * Intent for the system exact-alarm permission screen, scoped to
         * this app via the package URI. The coordinator launches this from
         * the reminder creation screen when [canScheduleExactAlarms] is
         * false and the user wants exact reminders, e.g.:
         * `context.startActivity(ExactAlarmScheduler.createExactAlarmSettingsIntent(context))`.
         * It is a plain settings Intent, not a runtime permission request —
         * there is no permission launcher for SCHEDULE_EXACT_ALARM.
         */
        fun createExactAlarmSettingsIntent(context: Context): Intent =
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
    }
}
