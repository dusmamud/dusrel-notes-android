package com.dusrel.notes.domain.reminder

import com.dusrel.notes.domain.model.Note

/**
 * Pure-Kotlin reminder scheduling decision. No Android classes in this file,
 * so it (and its tests) run on a plain JVM.
 *
 * Two paths exist for a reminder:
 * - [ReminderPath.EXACT]: AlarmManager exact alarm, fires at the requested
 *   time even in Doze. Requires the SCHEDULE_EXACT_ALARM grant on API 31+.
 * - [ReminderPath.FALLBACK]: the existing WorkManager path
 *   ([scheduleReminder] in ReminderWork.kt, untouched by this change),
 *   best-effort: the OS may defer it under Doze / battery optimisation.
 *
 * The caller (coordinator / UI) is responsible for obtaining
 * [canScheduleExactAlarms] from [com.dusrel.notes.data.work.ExactAlarmScheduler]
 * and for persisting nothing extra: the path is NOT stored on the note, so
 * cancelling must cancel both paths (see [ReminderScheduler.cancelAll]).
 */

/** Which delivery path a reminder will use. */
enum class ReminderPath {
    EXACT,
    FALLBACK,
}

/**
 * Result of a scheduling decision. [label] is exactly what the reminder
 * creation UI should display: "exact" for [ReminderPath.EXACT],
 * "best-effort" for [ReminderPath.FALLBACK].
 */
data class ReminderScheduling(
    val noteId: Long,
    val title: String,
    val atMillis: Long,
    val path: ReminderPath,
) {
    val label: String
        get() = when (path) {
            ReminderPath.EXACT -> "exact"
            ReminderPath.FALLBACK -> "best-effort"
        }
}

/** Testable abstraction over one delivery path. Both the exact-alarm
 * implementation and a WorkManager-fallback adapter implement this. */
interface ReminderAlarmScheduler {
    fun schedule(noteId: Long, title: String, atMillis: Long)
    fun cancel(noteId: Long)
}

/** Minimal note shape the boot re-schedule needs. Keeps this file
 * independent of the Room entity: the coordinator maps NoteEntity -> this. */
data class ReminderCandidate(
    val noteId: Long,
    val title: String,
    val reminderTime: Long?,
    val trashed: Boolean,
)

/** One entry of the boot re-schedule list: a future reminder plus the path
 * it must be re-scheduled on. [label] mirrors [ReminderScheduling.label]. */
data class BootRescheduleEntry(
    val noteId: Long,
    val title: String,
    val atMillis: Long,
    val path: ReminderPath,
) {
    val label: String
        get() = when (path) {
            ReminderPath.EXACT -> "exact"
            ReminderPath.FALLBACK -> "best-effort"
        }
}

/** Pure decision: exact alarms only when the OS grant is present. */
fun decidePath(canScheduleExactAlarms: Boolean): ReminderPath =
    if (canScheduleExactAlarms) ReminderPath.EXACT else ReminderPath.FALLBACK

/**
 * Routes scheduling / cancelling to the exact or the fallback scheduler.
 *
 * @param exactScheduler the AlarmManager implementation.
 * @param fallbackScheduler an adapter over the existing WorkManager
 *   [scheduleReminder]/[cancelReminder] functions (coordinator supplies it;
 *   this class never touches WorkManager itself).
 */
class ReminderScheduler(
    private val exactScheduler: ReminderAlarmScheduler,
    private val fallbackScheduler: ReminderAlarmScheduler,
) {
    private fun schedulerFor(path: ReminderPath): ReminderAlarmScheduler =
        when (path) {
            ReminderPath.EXACT -> exactScheduler
            ReminderPath.FALLBACK -> fallbackScheduler
        }

    /**
     * Schedules on the exact path when [canScheduleExactAlarms] is true,
     * otherwise on the WorkManager fallback. Returns the decision, whose
     * [ReminderScheduling.label] ("exact" / "best-effort") is what the
     * reminder creation UI displays.
     */
    fun schedule(
        noteId: Long,
        title: String,
        atMillis: Long,
        canScheduleExactAlarms: Boolean,
    ): ReminderScheduling {
        val path = decidePath(canScheduleExactAlarms)
        schedulerFor(path).schedule(noteId, title, atMillis)
        return ReminderScheduling(noteId, title, atMillis, path)
    }

    /** Cancels on exactly one path — use when the path is known. */
    fun cancel(noteId: Long, path: ReminderPath) {
        schedulerFor(path).cancel(noteId)
    }

    /**
     * Cancels on BOTH paths. This is the recommended cancel for note
     * delete / reminder clear: the path is not persisted on the note, so
     * at cancel time the coordinator cannot know which path was used
     * (the grant may also have changed since scheduling).
     */
    fun cancelAll(noteId: Long) {
        exactScheduler.cancel(noteId)
        fallbackScheduler.cancel(noteId)
    }

    /**
     * Pure boot list: future reminders only. Excludes trashed notes,
     * notes with no reminder time, and reminders at or before [nowMillis]
     * (strictly future, matching the existing boot receiver's `> now`
     * filter). Each entry states which path applies under
     * [canScheduleExactAlarms]. This only builds the list; call
     * [rescheduleAfterBoot] to also (re-)schedule each entry.
     */
    fun buildBootRescheduleList(
        notes: List<ReminderCandidate>,
        nowMillis: Long,
        canScheduleExactAlarms: Boolean,
    ): List<BootRescheduleEntry> {
        val path = decidePath(canScheduleExactAlarms)
        return notes
            .filter { !it.trashed && it.reminderTime != null && it.reminderTime > nowMillis }
            .map { BootRescheduleEntry(it.noteId, it.title, it.reminderTime!!, path) }
    }

    /** Convenience overload over domain [Note]s (same filter rules). */
    fun buildBootRescheduleListFromNotes(
        notes: List<Note>,
        nowMillis: Long,
        canScheduleExactAlarms: Boolean,
    ): List<BootRescheduleEntry> = buildBootRescheduleList(
        notes.map { ReminderCandidate(it.id, it.title, it.reminderTime, it.trashed) },
        nowMillis,
        canScheduleExactAlarms,
    )

    /**
     * Builds the boot list and (re-)schedules every entry on its path.
     * Returns the entries that were scheduled.
     */
    fun rescheduleAfterBoot(
        notes: List<ReminderCandidate>,
        nowMillis: Long,
        canScheduleExactAlarms: Boolean,
    ): List<BootRescheduleEntry> {
        val entries = buildBootRescheduleList(notes, nowMillis, canScheduleExactAlarms)
        entries.forEach { schedulerFor(it.path).schedule(it.noteId, it.title, it.atMillis) }
        return entries
    }
}
