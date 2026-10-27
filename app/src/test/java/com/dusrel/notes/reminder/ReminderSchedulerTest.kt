package com.dusrel.notes.reminder

import com.dusrel.notes.domain.model.Note
import com.dusrel.notes.domain.reminder.BootRescheduleEntry
import com.dusrel.notes.domain.reminder.ReminderAlarmScheduler
import com.dusrel.notes.domain.reminder.ReminderCandidate
import com.dusrel.notes.domain.reminder.ReminderPath
import com.dusrel.notes.domain.reminder.ReminderScheduler
import com.dusrel.notes.domain.reminder.decidePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake scheduler recording every call, standing in for the AlarmManager
 * (exact) and WorkManager (fallback) implementations. */
private class FakeScheduler : ReminderAlarmScheduler {
    data class Scheduled(val noteId: Long, val title: String, val atMillis: Long)

    val scheduled = mutableListOf<Scheduled>()
    val cancelled = mutableListOf<Long>()

    override fun schedule(noteId: Long, title: String, atMillis: Long) {
        scheduled.add(Scheduled(noteId, title, atMillis))
    }

    override fun cancel(noteId: Long) {
        cancelled.add(noteId)
    }
}

class ReminderSchedulerTest {

    private fun schedulerPair(): Triple<ReminderScheduler, FakeScheduler, FakeScheduler> {
        val exact = FakeScheduler()
        val fallback = FakeScheduler()
        return Triple(ReminderScheduler(exact, fallback), exact, fallback)
    }

    @Test fun decidePathExactWhenGranted() {
        assertEquals(ReminderPath.EXACT, decidePath(true))
    }

    @Test fun decidePathFallbackWhenNotGranted() {
        assertEquals(ReminderPath.FALLBACK, decidePath(false))
    }

    @Test fun exactChosenWhenPermissionGranted() {
        val (scheduler, exact, fallback) = schedulerPair()
        val result = scheduler.schedule(7L, "Buy milk", 5_000L, canScheduleExactAlarms = true)
        assertEquals(ReminderPath.EXACT, result.path)
        assertEquals("exact", result.label)
        assertEquals(listOf(FakeScheduler.Scheduled(7L, "Buy milk", 5_000L)), exact.scheduled)
        assertTrue(fallback.scheduled.isEmpty())
    }

    @Test fun fallbackChosenWhenPermissionNotGranted() {
        val (scheduler, exact, fallback) = schedulerPair()
        val result = scheduler.schedule(7L, "Buy milk", 5_000L, canScheduleExactAlarms = false)
        assertEquals(ReminderPath.FALLBACK, result.path)
        assertEquals("best-effort", result.label)
        assertEquals(listOf(FakeScheduler.Scheduled(7L, "Buy milk", 5_000L)), fallback.scheduled)
        assertTrue(exact.scheduled.isEmpty())
    }

    @Test fun cancelRoutesToExactScheduler() {
        val (scheduler, exact, fallback) = schedulerPair()
        scheduler.cancel(9L, ReminderPath.EXACT)
        assertEquals(listOf(9L), exact.cancelled)
        assertTrue(fallback.cancelled.isEmpty())
    }

    @Test fun cancelRoutesToFallbackScheduler() {
        val (scheduler, exact, fallback) = schedulerPair()
        scheduler.cancel(9L, ReminderPath.FALLBACK)
        assertEquals(listOf(9L), fallback.cancelled)
        assertTrue(exact.cancelled.isEmpty())
    }

    @Test fun cancelAllCancelsBothPaths() {
        // The path is not persisted on the note, so delete/clear must
        // cancel both — a reminder scheduled on one path must never survive
        // because the grant changed after scheduling.
        val (scheduler, exact, fallback) = schedulerPair()
        scheduler.cancelAll(9L)
        assertEquals(listOf(9L), exact.cancelled)
        assertEquals(listOf(9L), fallback.cancelled)
    }

    @Test fun bootListExcludesTrashedPastAndUntimed() {
        val (scheduler, _, _) = schedulerPair()
        val now = 10_000L
        val notes = listOf(
            ReminderCandidate(1L, "future", 20_000L, trashed = false),
            ReminderCandidate(2L, "trashed future", 20_000L, trashed = true),
            ReminderCandidate(3L, "past", 5_000L, trashed = false),
            ReminderCandidate(4L, "exactly now", 10_000L, trashed = false),
            ReminderCandidate(5L, "no time", null, trashed = false),
        )
        val entries = scheduler.buildBootRescheduleList(notes, now, canScheduleExactAlarms = true)
        assertEquals(
            listOf(BootRescheduleEntry(1L, "future", 20_000L, ReminderPath.EXACT)),
            entries,
        )
    }

    @Test fun bootListIncludesFutureWithCorrectPath() {
        val (scheduler, _, _) = schedulerPair()
        val notes = listOf(
            ReminderCandidate(1L, "a", 20_000L, trashed = false),
            ReminderCandidate(2L, "b", 30_000L, trashed = false),
        )
        val exactEntries =
            scheduler.buildBootRescheduleList(notes, 10_000L, canScheduleExactAlarms = true)
        assertEquals(ReminderPath.EXACT, exactEntries[0].path)
        assertEquals("exact", exactEntries[0].label)
        assertEquals(2, exactEntries.size)

        val fallbackEntries =
            scheduler.buildBootRescheduleList(notes, 10_000L, canScheduleExactAlarms = false)
        assertEquals(ReminderPath.FALLBACK, fallbackEntries[0].path)
        assertEquals("best-effort", fallbackEntries[0].label)
        assertEquals(2, fallbackEntries.size)
    }

    @Test fun bootListFromDomainNotesAppliesSameFilter() {
        val (scheduler, _, _) = schedulerPair()
        val notes = listOf(
            Note(id = 1L, title = "future", reminderTime = 20_000L, trashed = false),
            Note(id = 2L, title = "trashed", reminderTime = 20_000L, trashed = true),
            Note(id = 3L, title = "past", reminderTime = 5_000L, trashed = false),
            Note(id = 4L, title = "no time", reminderTime = null, trashed = false),
        )
        val entries =
            scheduler.buildBootRescheduleListFromNotes(notes, 10_000L, canScheduleExactAlarms = false)
        assertEquals(
            listOf(BootRescheduleEntry(1L, "future", 20_000L, ReminderPath.FALLBACK)),
            entries,
        )
    }

    @Test fun rescheduleAfterBootSchedulesEachEntryOnItsPath() {
        val (scheduler, exact, fallback) = schedulerPair()
        val notes = listOf(
            ReminderCandidate(1L, "a", 20_000L, trashed = false),
            ReminderCandidate(2L, "trashed", 20_000L, trashed = true),
        )
        val entries = scheduler.rescheduleAfterBoot(notes, 10_000L, canScheduleExactAlarms = true)
        assertEquals(1, entries.size)
        assertEquals(listOf(FakeScheduler.Scheduled(1L, "a", 20_000L)), exact.scheduled)
        assertTrue(fallback.scheduled.isEmpty())
    }
}
