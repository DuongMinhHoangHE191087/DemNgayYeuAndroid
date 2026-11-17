package com.example.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps personal rows (memories, reminders, profile, ...) of each account separate on a shared
 * device. Only one scope's data lives in the main tables at a time; on a scope change the live
 * rows are copied into a plain SQLite stash file (`filesDir/vault/<hash>.db`), removed from the
 * main tables, and the target scope's stash is copied back. Shared catalog rows keep their rows;
 * only the per-account column values on them ([OVERLAYS]) travel with the scope.
 *
 * Crash safety: a `pending` marker is persisted before any row moves; the stash is written to a
 * `.tmp` file and atomically renamed (rename == "stash complete") BEFORE the live rows are
 * deleted, and every step is idempotent, so an interrupted switch is finished on the next call.
 * No SQLite ATTACH is used (on Android ATTACH disables WAL and can throw with active readers).
 *
 * The first ever call adopts whatever is already in the tables for the requested scope, so an
 * existing install's data stays with the account that was signed in at upgrade time.
 *
 * ponytail: stash files are app-private but unencrypted; add SQLCipher only if rooted devices matter.
 */
class AccountDataVault(
  context: Context,
  private val db: RoomDatabase,
  private val beforeSwitch: suspend () -> Unit = {},
  private val afterSwitch: suspend () -> Unit = {},
  private val afterAdopt: suspend (epochBefore: Long, epochAfter: Long) -> Unit = { _, _ -> }
) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  private val dir = File(context.applicationContext.filesDir, "vault")
  private val mutex = Mutex()
  private val adoptMutex = Mutex()

  val currentScope: String get() = prefs.getString(KEY_SCOPE, null) ?: GUEST

  /**
   * Guest data becomes [scope]'s data without being moved (used right after registering), so there is nothing to cancel:
   * [afterAdopt] runs after the scope write and gets the epoch before and after it, which lets a caller that stamped its
   * state with the old epoch carry that state over. An interrupted switch finished first counts as a scope change and
   * gets [beforeSwitch] and [afterSwitch] instead. Adoptions run one at a time through their hooks, so the epoch pairs
   * reach [afterAdopt] in order; the hook runs outside [mutex], which the other operations keep using meanwhile.
   */
  suspend fun adoptCurrentInto(scope: String) = adoptMutex.withLock {
    var recovered = false
    var epochAfter = 0L
    mutex.withLock {
      recovered = recoverPending()
      epochAfter = epoch.incrementAndGet()
      file(scope).delete()
      prefs.edit().putString(KEY_SCOPE, scope).commit() // last on purpose: lint flags a commit whose result is unused
    }
    if (recovered) afterSwitch() else afterAdopt(epochAfter - 1, epochAfter)
  }

  /**
   * True khi bảng chính có nội dung người dùng tự tạo (kỷ niệm, nhắc hẹn, việc cần làm, mốc riêng).
   * ponytail: chỉ đếm vài bảng chính; hồ sơ/huy hiệu không tính vì có thể tồn tại mặc định.
   */
  suspend fun hasPersonalData(): Boolean = mutex.withLock {
    val sdb = db.openHelper.readableDatabase
    listOf(
      "shared_memories", "gift_reminders", "custom_reminders", "checklist_items",
      "milestones WHERE isUserCreated = 1"
    ).any { t -> sdb.query("SELECT 1 FROM $t LIMIT 1").use { it.moveToFirst() } }
  }

  /**
   * Moves live data to the old scope's stash and restores [scope]'s stash. No-op if unchanged, except that an interrupted
   * switch finished first counts as a change. The cancel hook and the scope write run inside the transition fence.
   */
  suspend fun switchTo(scope: String, discardCurrent: Boolean = false) {
    val changed = mutex.withLock {
      val recovered = recoverPending()
      val from = prefs.getString(KEY_SCOPE, null)
      if (from == null) {
        prefs.edit().putString(KEY_SCOPE, scope).commit()
        return@withLock recovered
      }
      if (from == scope && !discardCurrent) return@withLock recovered
      fenced {
        epoch.incrementAndGet()
        beforeSwitch()
        file(from).delete() // a stale stash of the live scope must never be mistaken for a finished one
        prefs.edit().putString(KEY_PENDING, "$from|$scope|$discardCurrent").commit()
        complete(from, scope, discardCurrent)
      }
      true
    }
    if (changed) afterSwitch()
  }

  /** Finishes an interrupted switch: cancel hook first, inside the fence, then the idempotent move. True if it ran. */
  private suspend fun recoverPending(): Boolean {
    val p = prefs.getString(KEY_PENDING, null)?.split("|") ?: return false
    if (p.size != 3) {
      prefs.edit().remove(KEY_PENDING).commit()
      return false
    }
    fenced {
      epoch.incrementAndGet()
      beforeSwitch()
      complete(p[0], p[1], p[2].toBoolean())
    }
    return true
  }

  /** Idempotent: safe to re-run from any point after the pending marker was written. */
  private fun complete(from: String, to: String, discard: Boolean) {
    val sdb = db.openHelper.writableDatabase
    if (prefs.getString(KEY_SCOPE, null) == from) {
      if (discard) file(from).delete() else if (!file(from).exists()) writeStash(sdb, from)
      clearLive(sdb)
      prefs.edit().putString(KEY_SCOPE, to).commit()
    }
    restore(sdb, to)
    prefs.edit().remove(KEY_PENDING).commit()
    epoch.incrementAndGet()
    db.invalidationTracker.refreshVersionsAsync()
  }

  private fun file(scope: String) = File(dir, tokenOf(scope) + ".db")

  private fun clearLive(sdb: SupportSQLiteDatabase) {
    sdb.beginTransaction()
    try {
      PERSONAL.forEach { sdb.execSQL("DELETE FROM ${it.table}${it.where}") }
      OVERLAYS.forEach { sdb.execSQL("UPDATE ${it.table} SET ${it.col} = ${it.default} WHERE ${it.changed}") }
      sdb.setTransactionSuccessful()
    } finally {
      sdb.endTransaction()
    }
  }

  private fun writeStash(sdb: SupportSQLiteDatabase, scope: String) {
    dir.mkdirs()
    val target = file(scope)
    val tmp = File(dir, target.name + ".tmp")
    tmp.delete()
    val stash = SQLiteDatabase.openOrCreateDatabase(tmp, null)
    try {
      stash.beginTransaction()
      try {
        PERSONAL.forEach { t ->
          val ddl = sdb.query("SELECT sql FROM sqlite_master WHERE type='table' AND name='${t.table}'").use {
            if (it.moveToFirst()) it.getString(0) else null
          } ?: return@forEach
          stash.execSQL(ddl)
          sdb.query("SELECT * FROM ${t.table}${t.where}").use { c ->
            while (c.moveToNext()) stash.insertOrThrow(t.table, null, values(c))
          }
        }
        OVERLAYS.forEach { o ->
          stash.execSQL("CREATE TABLE overlay_${o.table} (k TEXT, v INTEGER)")
          sdb.query("SELECT \"${o.key}\", ${o.col} FROM ${o.table} WHERE ${o.changed}").use { c ->
            while (c.moveToNext()) {
              stash.insertOrThrow("overlay_${o.table}", null, ContentValues().apply {
                put("k", c.getString(0))
                put("v", c.getLong(1))
              })
            }
          }
        }
        stash.setTransactionSuccessful()
      } finally {
        stash.endTransaction()
      }
    } finally {
      stash.close()
    }
    check(tmp.renameTo(target)) { "cannot finalize vault stash" }
  }

  private fun restore(sdb: SupportSQLiteDatabase, scope: String) {
    val f = file(scope)
    if (!f.exists()) return
    try {
      val stash = SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY)
      try {
        sdb.beginTransaction()
        try {
          PERSONAL.forEach { t ->
            val stashed = stash.rawQuery("PRAGMA table_info(${t.table})", null).use { names(it) }
            if (stashed.isEmpty()) return@forEach
            val live = sdb.query("PRAGMA table_info(${t.table})").use { names(it) }
            // Partial tables share the id space with catalog rows, so let SQLite assign fresh ids.
            val cols = (live intersect stashed.toSet()).filter { !t.partial || it != "id" }
            if (cols.isEmpty()) return@forEach
            stash.rawQuery("SELECT ${cols.joinToString(",") { "\"$it\"" }} FROM ${t.table}", null).use { c ->
              while (c.moveToNext()) sdb.insert(t.table, SQLiteDatabase.CONFLICT_REPLACE, values(c))
            }
          }
          OVERLAYS.forEach { o ->
            // A stash written before this overlay existed has no table for it; leave the live values alone.
            if (stash.rawQuery("PRAGMA table_info(overlay_${o.table})", null).use { names(it) }.isEmpty()) return@forEach
            stash.rawQuery("SELECT k, v FROM overlay_${o.table}", null).use { c ->
              while (c.moveToNext()) {
                sdb.execSQL("UPDATE ${o.table} SET ${o.col} = ? WHERE \"${o.key}\" = ?", arrayOf<Any?>(c.getLong(1), c.getString(0)))
              }
            }
          }
          sdb.setTransactionSuccessful()
        } finally {
          sdb.endTransaction()
        }
      } finally {
        stash.close()
      }
      f.delete()
    } catch (e: Exception) {
      // Keep the data recoverable instead of retrying/overwriting it.
      Log.e("AccountDataVault", "restore failed for ${f.name}: ${e.message}", e)
      f.renameTo(File(dir, f.name + ".failed"))
    }
  }

  private fun names(c: Cursor): List<String> = buildList { while (c.moveToNext()) add(c.getString(1)) }

  private fun values(c: Cursor) = ContentValues().apply {
    for (i in 0 until c.columnCount) {
      val k = c.getColumnName(i)
      when (c.getType(i)) {
        Cursor.FIELD_TYPE_INTEGER -> put(k, c.getLong(i))
        Cursor.FIELD_TYPE_FLOAT -> put(k, c.getDouble(i))
        Cursor.FIELD_TYPE_STRING -> put(k, c.getString(i))
        Cursor.FIELD_TYPE_BLOB -> put(k, c.getBlob(i))
        else -> putNull(k)
      }
    }
  }

  private class Part(val table: String, val where: String = "", val partial: Boolean = false)

  /** A per-account column on a row that stays in place; a stash keeps only the values that differ from [default]. */
  private class Overlay(val table: String, val key: String, val col: String, val default: Int, val extra: String = "") {
    val changed: String get() = "$col != $default$extra"
  }

  companion object {
    const val GUEST = "guest"

    /** Bumped before and after every scope switch; background work captures it to detect a stale session. */
    val epoch = java.util.concurrent.atomic.AtomicLong()

    // Low 32 bits: transitions running. Upper bits: transitions started. Lock-free on purpose: the cancel hook waits for the
    // milestone scheduler's lock, so a replay holding that lock must never have to wait on the vault.
    private val fence = java.util.concurrent.atomic.AtomicLong()
    private const val RUNNING = 1L
    private const val STARTED = 1L shl 32

    /** -1 while a scope switch or recovery is running; otherwise a value that changes whenever one begins. */
    fun transitionStamp(): Long {
      val v = fence.get()
      return if ((v and 0xFFFFFFFFL) != 0L) -1L else v
    }

    private inline fun <T> fenced(block: () -> T): T {
      fence.addAndGet(STARTED + RUNNING)
      try {
        return block()
      } finally {
        fence.addAndGet(-RUNNING)
      }
    }

    private const val PREFS = "inlove_account_scope"
    private const val KEY_SCOPE = "scope"
    private const val KEY_PENDING = "pending"

    /** Opaque per-scope tag. Alarms carry it, so a receiver can drop an alarm that another account set. */
    fun tokenOf(scope: String): String =
      MessageDigest.getInstance("SHA-256").digest(scope.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)

    /** Token of the account in force now; alarms are stamped with it when they are scheduled. */
    fun currentToken(context: Context): String =
      tokenOf(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SCOPE, null) ?: GUEST)

    fun scopeOf(email: String?): String =
      if (email.isNullOrBlank()) GUEST else "acct:" + email.trim().lowercase()

    private const val PERSONAL_GIFT =
      "remoteId LIKE 'local\\_%' ESCAPE '\\' OR remoteId LIKE 'tpl\\_%' ESCAPE '\\' OR remoteId LIKE 'legacy\\_%' ESCAPE '\\'"

    private val PERSONAL = listOf(
      Part("shared_memories"),
      Part("gift_reminders"),
      Part("custom_reminders"),
      Part("checklist_items"),
      Part("couple_profile"),
      Part("love_badges"),
      Part("sync_outbox"),
      Part("milestones", " WHERE isUserCreated = 1", partial = true),
      Part("anniversary_dates", " WHERE syncId != ''", partial = true),
      Part("gift_ideas", " WHERE $PERSONAL_GIFT", partial = true)
    )

    /** Shared rows: the per-account value on each one is stashed with the scope; the row itself stays. */
    private val OVERLAYS = listOf(
      Overlay("reminder_settings", "key", "isEnabled", 1),
      Overlay("gift_ideas", "remoteId", "isFavorited", 0, " AND NOT ($PERSONAL_GIFT)")
    )
  }
}
