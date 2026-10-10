package com.example.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration12To13Test {

  @get:Rule
  val helper: MigrationTestHelper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    AppDatabase::class.java,
    emptyList(),
    FrameworkSQLiteOpenHelperFactory()
  )

  @Test
  fun migrate12To13_preservesExistingRows_andAddsSyncColumns() {
    val v12 = helper.createDatabase(TEST_DB, 12)
    v12.execSQL(
      """
      INSERT INTO shared_memories
        (id, title, dateText, note, photoUri, location, isFavorite, anniversaryTitle,
         createdAt, relationshipId, authorId, authorName, isSynced, mediaType, videoUri,
         cloudinaryPublicId, cloudinaryUrl, isCloudinaryStored, fileSizeFormatted,
         durationSeconds, privacyLevel)
      VALUES
        (1, 'First Date', '2024-01-01', 'note', 'file:///a.jpg', '', 0, 'Kỷ Niệm Ngày Yêu',
         1700000000000, NULL, '', 'Bạn', 1, 'IMAGE', NULL, NULL, NULL, 1, '', 0, 'COUPLE_ONLY')
      """.trimIndent()
    )
    // Two rows, both with the pre-migration column set (no remoteId) — a preset catalog
    // table like this realistically has many rows on any device that already has the app
    // installed, which is exactly what a naive migration's UNIQUE INDEX on a shared blank
    // default (see MIGRATION_12_13's backfill step) would fail on if it weren't backfilled
    // with a distinct value per row first.
    v12.execSQL(
      """
      INSERT INTO gift_ideas
        (id, title, category, badgeText, tag, description, imageUrl, isFavorited,
         detailsSnippet, actionText, isAiGenerated, targetInterests, suggestedOccasion, priceRange)
      VALUES
        (1, 'Nến thơm', 'Quà lãng mạn', 'Gợi ý', 'Ý nghĩa', '', '', 0, '', '', 0, '', '', ''),
        (2, 'Hoa hồng', 'Quà lãng mạn', 'Gợi ý', 'Ý nghĩa', '', '', 0, '', '', 0, '', '', '')
      """.trimIndent()
    )
    // v12 milestones has no DEFAULTs, so every NOT NULL column must be supplied.
    v12.execSQL(
      """
      INSERT INTO milestones
        (id, title, dateText, subtitle, categoryTag, secondaryTag, imageUrl, daysRemaining,
         isPast, isImportant, notificationEnabled, isSaved, alarmTimeFormatted, isUserCreated)
      VALUES
        (1, '100 ngày', '2024-04-10', '', 'Cột Mốc', '', '', 0, 1, 0, 0, 0, '', 0),
        (2, '1 năm', '2025-01-01', '', 'Cột Mốc', '', '', 0, 1, 0, 0, 0, '', 0)
      """.trimIndent()
    )
    v12.close()

    val v13 = helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13)

    v13.query("SELECT title, syncId, updatedAt, deleted, pendingSync FROM shared_memories WHERE id = 1").use { cursor ->
      assert(cursor.moveToFirst()) { "row 1 must survive the migration" }
      assert(cursor.getString(0) == "First Date")
      assert(cursor.getInt(3) == 0) { "deleted must default to false" }
      assert(cursor.getInt(4) == 0) { "pendingSync must default to false for pre-existing rows" }
    }
    // The migration itself succeeding (runMigrationsAndValidate did not throw) is already
    // the main regression test for the UNIQUE INDEX crash; these two assertions additionally
    // pin the exact backfilled values so a future edit can't silently reintroduce duplicates.
    v13.query("SELECT id, remoteId FROM gift_ideas ORDER BY id").use { cursor ->
      assert(cursor.moveToFirst())
      assert(cursor.getString(1) == "legacy_1") { "pre-existing row 1 gets a distinct placeholder remoteId" }
      assert(cursor.moveToNext())
      assert(cursor.getString(1) == "legacy_2") { "pre-existing row 2 gets a DIFFERENT placeholder remoteId — this is what the unique index requires" }
    }
    v13.query("SELECT id, remoteId FROM milestones ORDER BY id").use { cursor ->
      assert(cursor.moveToFirst())
      assert(cursor.getString(1) == "legacy_1")
      assert(cursor.moveToNext())
      assert(cursor.getString(1) == "legacy_2")
    }
    v13.query("SELECT COUNT(*) FROM sync_outbox").use { cursor ->
      cursor.moveToFirst()
      assert(cursor.getInt(0) == 0) { "sync_outbox table must exist and start empty" }
    }
  }

  companion object {
    private const val TEST_DB = "migration-test"
  }
}
