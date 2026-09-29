package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v12 -> v13: adds offline-sync columns (syncId/updatedAt/deleted/pendingSync) to
 * shared_memories and anniversary_dates, adds remoteId to gift_ideas/milestones to fix
 * duplicate rows on repeated preset sync, adds updatedAt/pendingSync to the pairing
 * entities, and creates sync_outbox.
 *
 * shared_memories/anniversary_dates are rebuilt (not ALTERed) because they also drop the
 * unused `isSynced` column, and SQLite's DROP COLUMN support (3.35+) is not guaranteed on
 * every OS version this app's minSdk (26) has to run on.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
  override fun migrate(db: SupportSQLiteDatabase) {
    // --- shared_memories: rebuild to drop isSynced, add sync columns ---
    // DEFAULT is given ONLY for columns the INSERT below does not populate (syncId,
    // deleted, pendingSync — there is nothing to copy from the old table for them). Every
    // other column, including updatedAt, is always given an explicit value by the INSERT,
    // so it carries no SQL-level DEFAULT here — matching each field's Kotlin declaration in
    // Entities.kt, which likewise has no @ColumnInfo(defaultValue=...) unless the field is
    // one of these three. A DEFAULT on both sides that Room's schema validator did not
    // expect (or a missing one it did) fails the migration test with a schema mismatch.
    db.execSQL(
      """
      CREATE TABLE shared_memories_new (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        title TEXT NOT NULL,
        dateText TEXT NOT NULL,
        note TEXT NOT NULL,
        photoUri TEXT NOT NULL,
        location TEXT NOT NULL,
        isFavorite INTEGER NOT NULL,
        anniversaryTitle TEXT NOT NULL,
        createdAt INTEGER NOT NULL,
        relationshipId TEXT,
        authorId TEXT NOT NULL,
        authorName TEXT NOT NULL,
        mediaType TEXT NOT NULL,
        videoUri TEXT,
        cloudinaryPublicId TEXT,
        cloudinaryUrl TEXT,
        isCloudinaryStored INTEGER NOT NULL,
        fileSizeFormatted TEXT NOT NULL,
        durationSeconds INTEGER NOT NULL,
        privacyLevel TEXT NOT NULL,
        syncId TEXT NOT NULL DEFAULT '',
        updatedAt INTEGER NOT NULL,
        deleted INTEGER NOT NULL DEFAULT 0,
        pendingSync INTEGER NOT NULL DEFAULT 0
      )
      """.trimIndent()
    )
    db.execSQL(
      """
      INSERT INTO shared_memories_new
        (id, title, dateText, note, photoUri, location, isFavorite, anniversaryTitle,
         createdAt, relationshipId, authorId, authorName, mediaType, videoUri,
         cloudinaryPublicId, cloudinaryUrl, isCloudinaryStored, fileSizeFormatted,
         durationSeconds, privacyLevel, updatedAt)
      SELECT
        id, title, dateText, note, photoUri, location, isFavorite, anniversaryTitle,
        createdAt, relationshipId, authorId, authorName, mediaType, videoUri,
        cloudinaryPublicId, cloudinaryUrl, isCloudinaryStored, fileSizeFormatted,
        durationSeconds, privacyLevel, createdAt
      FROM shared_memories
      """.trimIndent()
    )
    db.execSQL("DROP TABLE shared_memories")
    db.execSQL("ALTER TABLE shared_memories_new RENAME TO shared_memories")

    // --- anniversary_dates: same rebuild pattern, same DEFAULT-only-where-uncopied rule ---
    db.execSQL(
      """
      CREATE TABLE anniversary_dates_new (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        title TEXT NOT NULL,
        dateText TEXT NOT NULL,
        type TEXT NOT NULL,
        description TEXT NOT NULL,
        isAnnual INTEGER NOT NULL,
        notificationEnabled INTEGER NOT NULL,
        reminderDaysBefore INTEGER NOT NULL,
        daysRemaining INTEGER NOT NULL,
        createdAt INTEGER NOT NULL,
        relationshipId TEXT,
        syncId TEXT NOT NULL DEFAULT '',
        updatedAt INTEGER NOT NULL,
        deleted INTEGER NOT NULL DEFAULT 0,
        pendingSync INTEGER NOT NULL DEFAULT 0
      )
      """.trimIndent()
    )
    db.execSQL(
      """
      INSERT INTO anniversary_dates_new
        (id, title, dateText, type, description, isAnnual, notificationEnabled,
         reminderDaysBefore, daysRemaining, createdAt, relationshipId, updatedAt)
      SELECT
        id, title, dateText, type, description, isAnnual, notificationEnabled,
        reminderDaysBefore, daysRemaining, createdAt, relationshipId, createdAt
      FROM anniversary_dates
      """.trimIndent()
    )
    db.execSQL("DROP TABLE anniversary_dates")
    db.execSQL("ALTER TABLE anniversary_dates_new RENAME TO anniversary_dates")

    // --- additive columns: SQLite requires a DEFAULT to ADD a NOT NULL column to a table
    // that already has rows, so these six all carry a matching @ColumnInfo(defaultValue=...)
    // in Entities.kt (unlike the rebuilt tables above, where most columns intentionally
    // have none) ---
    db.execSQL("ALTER TABLE gift_ideas ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE milestones ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE online_relationships ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE online_relationships ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE online_invites ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE online_invites ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0")

    // --- backfill a distinct placeholder remoteId per pre-existing row BEFORE creating the
    // unique index below. Every row just got the SAME literal '' from the ALTER TABLE
    // DEFAULT above — unlike NULL, SQLite's unique index treats repeated '' values as
    // genuine duplicates, so on a real device (this table is a preset catalog, seeded with
    // many rows) the CREATE UNIQUE INDEX below would abort the migration with "UNIQUE
    // constraint failed" the moment two or more pre-existing rows share the same remoteId,
    // i.e. on every install that already has data. `id` is the table's own primary key, so
    // 'legacy_' || id is guaranteed distinct per row; the next real Firestore sync then
    // inserts fresh rows keyed by the actual remote document id (a placeholder never
    // matches a real one, so it is left in place as a harmless pre-existing row, not
    // merged away) ---
    db.execSQL("UPDATE gift_ideas SET remoteId = 'legacy_' || id WHERE remoteId = ''")
    db.execSQL("UPDATE milestones SET remoteId = 'legacy_' || id WHERE remoteId = ''")

    // --- unique indices backing the new remoteId upsert path ---
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_gift_ideas_remoteId ON gift_ideas(remoteId)")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_milestones_remoteId ON milestones(remoteId)")

    // --- new outbox table: no pre-existing rows to backfill, so no column needs a DEFAULT
    // (every insert always supplies a fully-constructed SyncOutboxEntity) ---
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS sync_outbox (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        entityType TEXT NOT NULL,
        syncId TEXT NOT NULL,
        operation TEXT NOT NULL,
        payloadJson TEXT NOT NULL,
        createdAt INTEGER NOT NULL,
        attemptCount INTEGER NOT NULL,
        lastError TEXT
      )
      """.trimIndent()
    )
  }
}
