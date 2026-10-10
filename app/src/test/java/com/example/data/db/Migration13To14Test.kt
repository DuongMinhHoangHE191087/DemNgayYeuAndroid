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
class Migration13To14Test {

  @get:Rule
  val helper: MigrationTestHelper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    AppDatabase::class.java,
    emptyList(),
    FrameworkSQLiteOpenHelperFactory()
  )

  @Test
  fun migrate13To14_keepsCoupleProfileRow_andAddsPreferenceColumns() {
    val v13 = helper.createDatabase(TEST_DB, 13)
    v13.execSQL(
      """
      INSERT INTO couple_profile
        (id, partner1Name, partner1Birthday, partner1ProfilePicture, partner1Age, partner1Zodiac,
         partner2Name, partner2Birthday, partner2ProfilePicture, partner2Age, partner2Zodiac,
         loveTitle, loveDays, anniversaryDate, updatedAt)
      VALUES (1, 'A', '2000-01-01', '', 24, '', 'B', '2000-02-02', '', 24, '', '', 100, '2024-01-01', 0)
      """.trimIndent()
    )
    v13.close()

    val v14 = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)

    v14.query("SELECT likesCsv, budgetMaxVnd, occasionRegion FROM couple_profile WHERE id = 1").use { c ->
      assert(c.moveToFirst()) { "couple_profile row must survive the migration" }
      assert(c.getString(0) == "")
      assert(c.getLong(1) == 0L)
      assert(c.getString(2) == "")
    }
  }

  @Test
  fun migrate12To14_fullChain() {
    helper.createDatabase(TEST_DB, 12).close()
    helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_12_13, MIGRATION_13_14)
  }

  companion object {
    private const val TEST_DB = "migration-test-13-14"
  }
}
