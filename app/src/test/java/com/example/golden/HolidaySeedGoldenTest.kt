package com.example.golden

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.repository.CloudPresetFetcher
import com.example.data.repository.OfflineSeeder
import com.example.ui.util.AppLanguage
import java.time.LocalDate
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * GV-204: các HÀNG ngày lễ mà seedOfflineDataIfStillEmpty ghi vào Room (Android là đặc tả).
 * D-37 đổi sang HolidayRules rồi chạy lại ở chế độ VERIFY; D-24b đọc file này ở commonTest cho iOS.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HolidaySeedGoldenTest {
  private val zone = "Asia/Ho_Chi_Minh"
  private val todays = listOf(
    "2024-01-01", "2027-12-31", "2028-01-01", "2028-02-14", "2028-02-28",
    "2028-02-29", "2028-03-01", "2028-07-15", "2030-12-31",
  )
  private val languages = listOf(AppLanguage.VI to "vi", AppLanguage.EN to "en")
  private lateinit var previousZone: TimeZone

  @Before fun pinZone() {
    previousZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone(zone))
  }

  @After fun restoreZone() = TimeZone.setDefault(previousZone)

  private fun seededRows(today: String, language: AppLanguage): List<Map<String, Any?>> = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    try {
      val dao = db.inLoveDao()
      OfflineSeeder(dao, CloudPresetFetcher(dao), today = { LocalDate.parse(today) }).seedOfflineDataIfStillEmpty(language)
      dao.getAnniversaryDatesList()
        .map {
          linkedMapOf<String, Any?>(
            "title" to it.title,
            "dateText" to it.dateText,
            "type" to it.type,
            "isAnnual" to it.isAnnual,
            "daysRemaining" to it.daysRemaining.toLong(),
          )
        }
        .sortedWith(compareBy({ it["title"] as String }, { it["dateText"] as String }))
    } finally {
      db.close()
    }
  }

  private fun cases(): List<Map<String, Any?>> = todays.flatMap { today ->
    languages.map { (language, tag) ->
      val rows = seededRows(today, language)
      linkedMapOf<String, Any?>(
        "id" to "$today-$tag",
        "input" to linkedMapOf<String, Any?>("today" to today, "language" to tag),
        "expected" to linkedMapOf<String, Any?>("rows" to rows),
        "androidLegacy" to linkedMapOf<String, Any?>("rows" to rows),
        "divergence" to null,
        "optional" to false,
      )
    }
  }

  @Test
  fun holidaySeedGolden_verifiesAgainstCurrentAndroid() {
    GoldenWriter().check(
      "GV-204-holiday-seed-rows.json",
      linkedMapOf("schema" to 1L, "zone" to zone, "cases" to cases()),
    )
  }

  @Test
  fun holidaySeedGolden_coversBothLanguagesAndNineTodays() {
    val all = cases()
    assertEquals(18, all.size)
    assertEquals(
      todays.flatMap { listOf("$it-vi", "$it-en") },
      all.map { it["id"] },
    )
  }

  @Test
  fun holidaySeedGolden_containsNonAnnualRowsInBothLanguages() {
    for ((language, _) in languages) {
      // 2028-01-01: còn lễ âm lịch đã xác minh ở phía trước (bảng âm lịch phủ 2025..2040).
      val rows = seededRows("2028-01-01", language)
      assertTrue("$language phải có hàng isAnnual=false", rows.any { it["isAnnual"] == false })
    }
  }

  // Ngày lễ rơi đúng vào `today` vẫn là hôm nay (không nhảy sang lần sau): biên mà chín `today` của golden không chạm tới.
  @Test
  fun holidaySeedGolden_holidayFallingOnTodayStaysToday() {
    val tet = seededRows("2028-01-26", AppLanguage.VI).single { (it["title"] as String).contains("Tết Nguyên Đán") }
    assertEquals("26/01/2028", tet["dateText"])
    assertEquals(0L, tet["daysRemaining"])
    val mothersDay = seededRows("2028-05-14", AppLanguage.EN).single { (it["title"] as String).contains("Mother's Day") }
    assertEquals("14/05/2028", mothersDay["dateText"])
    assertEquals(0L, mothersDay["daysRemaining"])
  }

  @Test
  fun holidaySeedGolden_valentineRollsToNextYear() {
    val expected = mapOf("2028-02-28" to 352L, "2028-02-29" to 351L, "2028-03-01" to 350L)
    for ((language, _) in languages) {
      for ((today, days) in expected) {
        val valentine = seededRows(today, language).single { (it["title"] as String).contains("Valentine") }
        assertEquals("$language $today", days, valentine["daysRemaining"])
        assertEquals("14/02/2029", valentine["dateText"])
      }
    }
  }
}
