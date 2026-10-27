package com.dusrel.notes.domain.crypto

/**
 * Lockout state machine for private-vault PIN attempts. Pure JVM:
 * no Android classes, injectable clock, serialisable state.
 *
 * Policy (fixed, documented — do not silently change):
 * - After [MAX_FAILED_ATTEMPTS] (5) consecutive failed PIN attempts,
 *   the vault locks out for [LOCKOUT_DURATION_MILLIS] (30 seconds).
 * - Attempts reset to zero on any success ([recordSuccess]).
 * - While locked, any attempt is REJECTED without consuming a
 *   further attempt and without deriving a key or attempting a
 *   decrypt: the coordinator must check [isLocked] BEFORE touching
 *   [PrivateVault], and [recordFailure] while locked is a no-op that
 *   returns the state unchanged (so locked probes can neither extend
 *   the lockout nor burn attempts, and cannot be used as a decryption
 *   oracle).
 * - After the 30 seconds expire, the vault unlocks with a fresh
 *   attempt budget: the next failure counts as attempt 1 again.
 *   Expiry is evaluated from the clock, so no timer or background
 *   work is needed; [effectiveAttempts] is what counts.
 *
 * Persistence: [LockoutState] is the whole state (attempt count +
 * lockout deadline). The coordinator persists it in DataStore under
 * key `private_lockout_state` as [LockoutState.toStorageString] after
 * every [recordSuccess]/[recordFailure], and restores it via
 * [LockoutState.fromStorageString] (or the [Lockout] constructor's
 * [initialState]) on process start. Until restored state is loaded,
 * the vault must be treated as locked-out-of-caution only if the
 * coordinator cannot read DataStore; a missing key means "no
 * failures yet" ([LockoutState] defaults).
 *
 * Clock injection: pass `nowMillis` (e.g. a fake clock in tests, or
 * `System.currentTimeMillis` in production). All methods also accept
 * an explicit `now` argument that overrides the injected clock for
 * that call, so callers can pass a single timestamp through a whole
 * unlock attempt and avoid time-skew between the lock check and the
 * failure recording.
 */
class Lockout(
    initialState: LockoutState = LockoutState(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {

    companion object {
        /** Consecutive failures before lockout. */
        const val MAX_FAILED_ATTEMPTS = 5

        /** Fixed lockout duration: 30 seconds, in milliseconds. */
        const val LOCKOUT_DURATION_MILLIS = 30_000L
    }

    /** Current state. Updated only by [recordSuccess]/[recordFailure]. */
    var state: LockoutState = initialState
        private set

    /**
     * True while the lockout deadline is in the future. A deadline in
     * the past (or zero) means not locked, regardless of the stored
     * attempt count — expiry resets the budget (see [effectiveAttempts]).
     */
    fun isLocked(now: Long = nowMillis()): Boolean =
        state.lockoutUntilMillis > now

    /**
     * Attempts that currently count against the budget: zero once a
     * lockout has expired, otherwise the stored count. This is the
     * number the coordinator should display (e.g. "3 of 5 attempts").
     * While locked the stored count (5) is returned; the budget only
     * resets after the deadline passes.
     */
    fun effectiveAttempts(now: Long = nowMillis()): Int =
        if (state.lockoutUntilMillis != 0L && now >= state.lockoutUntilMillis) 0
        else state.failedAttempts

    /**
     * Whole seconds remaining in the lockout, rounded UP (ceiling),
     * so the UI never shows 0 while the vault is still locked. Zero
     * when not locked.
     */
    fun remainingSeconds(now: Long = nowMillis()): Long {
        if (!isLocked(now)) return 0
        val remaining = state.lockoutUntilMillis - now
        return (remaining + 999) / 1000
    }

    /**
     * Records a successful unlock. Resets attempts and clears any
     * lockout deadline. Returns the new state (persist it).
     */
    fun recordSuccess(): LockoutState {
        state = LockoutState()
        return state
    }

    /**
     * Records a failed PIN attempt and returns the new state (persist
     * it). While locked this is a no-op returning the state unchanged
     * — locked attempts are rejected, not counted. On the 5th
     * consecutive failure the lockout deadline is set to
     * `now + 30_000ms`.
     */
    fun recordFailure(now: Long = nowMillis()): LockoutState {
        if (isLocked(now)) return state
        val attempts = effectiveAttempts(now) + 1
        state = if (attempts >= MAX_FAILED_ATTEMPTS) {
            LockoutState(
                failedAttempts = attempts,
                lockoutUntilMillis = now + LOCKOUT_DURATION_MILLIS,
            )
        } else {
            LockoutState(failedAttempts = attempts, lockoutUntilMillis = 0L)
        }
        return state
    }
}

/**
 * Serialisable lockout state: the failed-attempt count plus the
 * lockout deadline as epoch milliseconds (0 = no lockout).
 * Storage format is `"<failedAttempts>,<lockoutUntilMillis>"`
 * (e.g. `"3,0"` or `"5,1761550000000"`). Malformed input parses
 * leniently to the default state rather than crashing on launch —
 * a corrupt lockout record must never brick the vault; worst case
 * it grants a fresh attempt budget, and the PIN itself is still
 * protected by [PrivateVault]'s GCM authentication.
 */
data class LockoutState(
    val failedAttempts: Int = 0,
    val lockoutUntilMillis: Long = 0L,
) {
    fun toStorageString(): String = "$failedAttempts,$lockoutUntilMillis"

    companion object {
        fun fromStorageString(raw: String?): LockoutState {
            if (raw.isNullOrBlank()) return LockoutState()
            val parts = raw.split(",")
            if (parts.size != 2) return LockoutState()
            val attempts = parts[0].toIntOrNull() ?: return LockoutState()
            val until = parts[1].toLongOrNull() ?: return LockoutState()
            if (attempts < 0 || until < 0) return LockoutState()
            return LockoutState(failedAttempts = attempts, lockoutUntilMillis = until)
        }
    }
}
