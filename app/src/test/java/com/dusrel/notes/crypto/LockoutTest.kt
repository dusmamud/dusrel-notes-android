package com.dusrel.notes.crypto

import com.dusrel.notes.domain.crypto.Lockout
import com.dusrel.notes.domain.crypto.LockoutState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockoutTest {

    /** Controllable clock: tests advance time explicitly, no sleeps. */
    private class FakeClock(var now: Long = 1_000_000L) {
        fun nowMillis(): Long = now
    }

    @Test fun lockoutAfterFiveFailures() {
        val clock = FakeClock()
        val lockout = Lockout(nowMillis = clock::nowMillis)
        assertFalse(lockout.isLocked())
        repeat(4) {
            lockout.recordFailure()
            assertFalse("Must not lock before 5 failures", lockout.isLocked())
        }
        assertEquals(4, lockout.effectiveAttempts())
        lockout.recordFailure()
        assertTrue("5th consecutive failure must lock", lockout.isLocked())
        assertEquals(5, lockout.effectiveAttempts())
        assertEquals(30, lockout.remainingSeconds())
    }

    @Test fun lockedAttemptsRejectedWithoutConsuming() {
        val clock = FakeClock()
        val lockout = Lockout(nowMillis = clock::nowMillis)
        repeat(5) { lockout.recordFailure() }
        assertTrue(lockout.isLocked())
        val stateBefore = lockout.state
        // Locked probes must not change state: no extension, no extra count.
        lockout.recordFailure()
        lockout.recordFailure()
        assertEquals(stateBefore, lockout.state)
        assertEquals(30, lockout.remainingSeconds())
    }

    @Test fun lockoutExpiresAfter30Seconds() {
        val clock = FakeClock()
        val lockout = Lockout(nowMillis = clock::nowMillis)
        repeat(5) { lockout.recordFailure() }
        assertTrue(lockout.isLocked())

        clock.now += 29_000
        assertTrue("Still locked at 29s", lockout.isLocked())
        assertEquals(1, lockout.remainingSeconds())

        clock.now += 1_000 // exactly 30s after lockout started
        assertFalse("Lockout must expire after 30s", lockout.isLocked())
        assertEquals(0, lockout.remainingSeconds())
        // Fresh budget after expiry: next failure is attempt 1, not 6.
        lockout.recordFailure()
        assertFalse(lockout.isLocked())
        assertEquals(1, lockout.effectiveAttempts())
    }

    @Test fun successResetsAttempts() {
        val clock = FakeClock()
        val lockout = Lockout(nowMillis = clock::nowMillis)
        repeat(3) { lockout.recordFailure() }
        assertEquals(3, lockout.effectiveAttempts())
        lockout.recordSuccess()
        assertEquals(0, lockout.effectiveAttempts())
        assertFalse(lockout.isLocked())
        // Failures after a success start from 1 again.
        lockout.recordFailure()
        assertEquals(1, lockout.effectiveAttempts())
    }

    @Test fun successClearsLockout() {
        val clock = FakeClock()
        val lockout = Lockout(nowMillis = clock::nowMillis)
        repeat(5) { lockout.recordFailure() }
        assertTrue(lockout.isLocked())
        lockout.recordSuccess()
        assertFalse(lockout.isLocked())
        assertEquals(LockoutState(), lockout.state)
    }

    @Test fun remainingSecondsIsZeroWhenNotLocked() {
        val lockout = Lockout(nowMillis = { 5_000L })
        assertEquals(0, lockout.remainingSeconds())
        lockout.recordFailure()
        assertEquals(0, lockout.remainingSeconds())
    }

    @Test fun stateSurvivesSerialisationRoundTrip() {
        val clock = FakeClock()
        val lockout = Lockout(nowMillis = clock::nowMillis)
        repeat(5) { lockout.recordFailure() }
        val stored = lockout.state.toStorageString()
        // Simulate process death: restore from the stored string.
        val restored = Lockout(
            initialState = LockoutState.fromStorageString(stored),
            nowMillis = clock::nowMillis,
        )
        assertTrue("Restored lockout must still be locked", restored.isLocked())
        assertEquals(5, restored.effectiveAttempts())
        assertEquals(30, restored.remainingSeconds())

        // And a mid-count state restores too.
        val partial = LockoutState.fromStorageString(LockoutState(3, 0).toStorageString())
        assertEquals(3, partial.failedAttempts)
        assertEquals(0L, partial.lockoutUntilMillis)
    }

    @Test fun malformedStoredStateFallsBackToDefault() {
        assertEquals(LockoutState(), LockoutState.fromStorageString(null))
        assertEquals(LockoutState(), LockoutState.fromStorageString(""))
        assertEquals(LockoutState(), LockoutState.fromStorageString("garbage"))
        assertEquals(LockoutState(), LockoutState.fromStorageString("3"))
        assertEquals(LockoutState(), LockoutState.fromStorageString("-1,0"))
        assertEquals(LockoutState(), LockoutState.fromStorageString("3,-5"))
    }

    @Test fun constantsMatchSpec() {
        assertEquals(5, Lockout.MAX_FAILED_ATTEMPTS)
        assertEquals(30_000L, Lockout.LOCKOUT_DURATION_MILLIS)
    }

    @Test fun defaultClockIsSystemTime() {
        // No injected clock: must not throw and must behave sanely.
        val lockout = Lockout()
        assertFalse(lockout.isLocked())
        lockout.recordFailure()
        assertEquals(1, lockout.effectiveAttempts())
    }
}
