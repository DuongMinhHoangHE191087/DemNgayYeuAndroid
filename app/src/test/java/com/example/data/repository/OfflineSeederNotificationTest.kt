package com.example.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.seed.VietnameseHolidays
import com.example.data.seed.WesternHolidays
import com.example.ui.util.AppLanguage
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineSeederNotificationTest {
  private val viCoupleRelevant: Map<String, Boolean> =
    (VietnameseHolidays.fixedHolidays.map { "${it.emoji} ${it.titleVi}" to it.isCoupleRelevant } +
      VietnameseHolidays.lunarHolidays.map { "${it.emoji} ${it.titleVi}" to it.isCoupleRelevant }).toMap()
  private val enCoupleRelevant: Map<String, Boolean> =
    (WesternHolidays.fixedHolidays.map { "${it.emoji} ${it.titleEn}" to it.isCoupleRelevant } +
      WesternHolidays.ruleBasedHolidays.map { "${it.emoji} ${it.titleEn}" to it.isCoupleRelevant }).toMap()

  private fun seededAnniversaries(language: AppLanguage) = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    try {
      val dao = db.inLoveDao()
      OfflineSeeder(dao, CloudPresetFetcher(dao), today = { LocalDate.of(2026, 10, 28) }).seedOfflineDataIfStillEmpty(language)
      dao.getAllAnniversaryDates().first()
    } finally {
      db.close()
    }
  }

  private fun seededCadences() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    try {
      val dao = db.inLoveDao()
      OfflineSeeder(dao, CloudPresetFetcher(dao), today = { LocalDate.of(2026, 10, 28) }).initializeDefaultDataIfEmpty()
      dao.getAllReminderCadences().first()
    } finally {
      db.close()
    }
  }

  private fun assertNotificationFollowsCoupleRelevance(language: AppLanguage, expected: Map<String, Boolean>) {
    val matched = seededAnniversaries(language).filter { it.title in expected }
    for (row in matched) {
      assertEquals("$language ${row.title}", expected.getValue(row.title), row.notificationEnabled)
    }
    assertTrue("$language: no matched row expects true", matched.any { expected.getValue(it.title) })
    assertTrue("$language: no matched row expects false", matched.any { !expected.getValue(it.title) })
  }

  @Test
  fun vietnameseHolidayNotificationsFollowIsCoupleRelevant() {
    assertNotificationFollowsCoupleRelevance(AppLanguage.VI, viCoupleRelevant)
  }

  @Test
  fun westernHolidayNotificationsFollowIsCoupleRelevant() {
    assertNotificationFollowsCoupleRelevance(AppLanguage.EN, enCoupleRelevant)
  }

  @Test
  fun exactDayCadenceLabelIsNineAmOnFreshInstall() {
    assertEquals("09:00 Ngày lễ", seededCadences().firstOrNull { it.key == "exact_day" }?.label)
  }
}
