package com.example.data.auth

import android.content.SharedPreferences

/**
 * Persisted brute-force limiter for the local app lock (PIN / password / provider re-auth).
 * A 4-digit PIN has only 10 000 values, so the lock-out must survive process death and grow
 * quickly; counters are keyed per account uid and only reset by a successful unlock.
 *
 * ponytail: uses wall-clock time with a roll-back guard (no persisted monotonic clock);
 * a rooted device that edits prefs can bypass it, which a hardware-backed key would be needed for.
 */
class UnlockAttemptStore(
  private val prefs: SharedPreferences,
  private val clock: () -> Long = System::currentTimeMillis
) {
  sealed interface Gate {
    data object Open : Gate
    data class Locked(val untilMillis: Long) : Gate
  }

  fun gate(uid: String): Gate {
    val now = clock()
    val lockUntil = prefs.getLong(key(uid, UNTIL), 0L)
    val lastAttempt = prefs.getLong(key(uid, LAST), 0L)
    // Clock moved backwards since the last attempt: keep the lock until the clock catches up.
    val until = if (now < lastAttempt) maxOf(lockUntil, lastAttempt) else lockUntil
    return if (now < until) Gate.Locked(until) else Gate.Open
  }

  fun failures(uid: String): Int = prefs.getInt(key(uid, FAILS), 0)

  /** Records a wrong attempt and returns the new gate (Locked once the threshold is reached). */
  fun recordFailure(uid: String): Gate {
    val now = clock()
    val failures = failures(uid) + 1
    val lock = lockoutMillis(failures)
    prefs.edit()
      .putInt(key(uid, FAILS), failures)
      .putLong(key(uid, LAST), now)
      .putLong(key(uid, UNTIL), if (lock > 0) now + lock else 0L)
      .commit()
    return if (lock > 0) Gate.Locked(now + lock) else Gate.Open
  }

  fun reset(uid: String) {
    prefs.edit().remove(key(uid, FAILS)).remove(key(uid, LAST)).remove(key(uid, UNTIL)).commit()
  }

  fun remainingBeforeLock(uid: String): Int = (FREE_ATTEMPTS - failures(uid)).coerceAtLeast(0)

  private fun key(uid: String, field: String) = "unlock_${field}_$uid"

  companion object {
    const val FREE_ATTEMPTS = 5
    private const val FAILS = "fails"
    private const val LAST = "last"
    private const val UNTIL = "until"

    /** 5th wrong try: 30 s, then 1 min, 5 min, 15 min, and 1 h from the 9th on. */
    fun lockoutMillis(failures: Int): Long = when {
      failures < FREE_ATTEMPTS -> 0L
      failures == 5 -> 30_000L
      failures == 6 -> 60_000L
      failures == 7 -> 5 * 60_000L
      failures == 8 -> 15 * 60_000L
      else -> 60 * 60_000L
    }
  }
}
