package com.example.golden

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.InLoveDao
import com.example.data.model.AnniversaryDateEntity
import com.example.data.seed.HolidayDates
import com.example.data.seed.VietnameseHolidays
import com.example.data.seed.WesternHolidays
import java.text.Normalizer
import java.time.LocalDate
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * GV-015: hanh vi CU cua HolidayDates.refreshExisting tren Room trong bo nho (Android la dac ta).
 * D-40 (commonMain) doc file golden nay de chung minh cung ket qua. Dem so lan ghi bang DAO uy quyen
 * `InLoveDao by realDao` ghi de updateAnniversaryDate. Tieu de le lay tu du lieu seed bang code
 * (file nay chi chua ASCII): chuoi "emoji + tieu de VI/EN".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HolidayRefreshGoldenTest {
  private val zone = "Asia/Ho_Chi_Minh"
  private lateinit var previousZone: TimeZone

  @Before fun pinZone() {
    previousZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone(zone))
  }

  @After fun restoreZone() = TimeZone.setDefault(previousZone)

  private data class Row(val title: String, val dateText: String, val isAnnual: Boolean)

  private data class Case(val id: String, val today: String, val rows: List<Row>, val secondPass: Boolean = false)

  private class Outcome(val rows: List<AnniversaryDateEntity>, val inserted: List<AnniversaryDateEntity>, val calls: Long, val callsSecondPass: Long?)

  private val lunar = VietnameseHolidays.lunarHolidays
  private val tet = lunar.first { it.titleEn.startsWith("Lunar New Year") }
  private val fullMoon = lunar.first { it.titleEn.startsWith("First Full Moon") }
  private val vuLan = lunar.first { it.titleEn.startsWith("Vu Lan") }
  private val midAutumn = lunar.first { it.titleEn.startsWith("Mid-Autumn") }
  private val mothers = WesternHolidays.ruleBasedHolidays.first { it.titleEn == "Mother's Day" }
  private val fathers = WesternHolidays.ruleBasedHolidays.first { it.titleEn == "Father's Day" }
  private val thanksgiving = WesternHolidays.ruleBasedHolidays.first { it.titleEn == "Thanksgiving" }
  private val sweetest = WesternHolidays.ruleBasedHolidays.first { it.titleEn == "Sweetest Day" }
  private val valentine = VietnameseHolidays.fixedHolidays.first { it.titleEn == "Valentine's Day" }

  private fun lunarVi(h: VietnameseHolidays.LunarHoliday) = "${h.emoji} ${h.titleVi}"
  private fun lunarEn(h: VietnameseHolidays.LunarHoliday) = "${h.emoji} ${h.titleEn}"
  private fun ruleVi(h: WesternHolidays.RuleBasedHoliday) = "${h.emoji} ${h.titleVi}"
  private fun ruleEn(h: WesternHolidays.RuleBasedHoliday) = "${h.emoji} ${h.titleEn}"

  private val nfcTitle = lunarVi(tet)
  private val nfdTitle = Normalizer.normalize(nfcTitle, Normalizer.Form.NFD)

  private val today = "2026-10-13"

  private val cases: List<Case> = listOf(
    Case("title-vi-with-emoji", today, listOf(Row(lunarVi(tet), "17/02/2026", true))),
    Case("title-en-with-emoji", today, listOf(Row(lunarEn(midAutumn), "25/09/2026", true))),
    // PF-022: hang cua nguoi dung trung tieu de le bi ghi de ngay va isAnnual.
    Case("custom-row-same-title-collision", today, listOf(Row(ruleEn(mothers), "20/06/2026", true))),
    Case(
      "unknown-title-untouched", today,
      listOf(Row("Our first date", "01/01/2024", true), Row("${valentine.emoji} ${valentine.titleVi}", "14/02/2026", true)),
    ),
    Case("lunar-no-date-ahead-keeps-text-not-annual", today, listOf(Row(lunarVi(vuLan), "27/08/2026", true))),
    Case("rule-based-moves-forward", today, listOf(Row(ruleVi(mothers), "10/05/2026", true))),
    Case("rule-based-this-year-still-ahead", today, listOf(Row(ruleEn(sweetest), "18/10/2025", true))),
    Case(
      "idempotent-second-call-no-writes", today,
      listOf(Row(lunarVi(tet), "17/02/2026", true), Row(ruleEn(thanksgiving), "27/11/2025", true), Row(lunarEn(vuLan), "27/08/2026", true)),
      secondPass = true,
    ),
    Case(
      "year-end-today", "2026-12-31",
      listOf(
        Row(lunarVi(tet), "17/02/2026", true),
        Row(lunarVi(midAutumn), "25/09/2026", true),
        Row(ruleVi(thanksgiving), "26/11/2026", true),
        Row(ruleEn(fathers), "21/06/2026", true),
      ),
    ),
    Case(
      "row-without-emoji-prefix-not-matched", today,
      listOf(Row(tet.titleVi, "17/02/2026", true), Row(mothers.titleEn, "10/05/2026", true)),
    ),
    // PF-001: tieu de NFD khong khop (giu dung hanh vi cu).
    Case("title-nfd-not-matched", today, listOf(Row(nfdTitle, "17/02/2026", true))),
    Case(
      "multiple-rows-one-pass", today,
      listOf(
        Row(lunarVi(tet), "17/02/2026", true),
        Row("Birthday", "05/05/2025", true),
        Row(lunarEn(fullMoon), "03/03/2026", false),
        Row(ruleVi(sweetest), "17/10/2026", false),
        Row(ruleEn(thanksgiving), "27/11/2025", true),
        Row(lunarVi(vuLan), "27/08/2026", true),
        Row(lunarVi(midAutumn), "15/09/2027", false),
      ),
    ),
    Case("empty-table", today, emptyList()),
    Case("emoji-only-title", today, listOf(Row(tet.emoji, "17/02/2026", true), Row("${mothers.emoji} ", "10/05/2026", true))),
    // Ngoai P8 (Claude them): bien today == ngay le. nextLunar loc `!isBefore`, nextRuleBased chi sang nam sau khi `isBefore`.
    Case("lunar-today-equals-date-stays", "2026-09-25", listOf(Row(lunarVi(midAutumn), "25/09/2025", true))),
    Case("rule-based-today-equals-date-stays", "2026-11-26", listOf(Row(ruleEn(thanksgiving), "27/11/2025", true))),
  )

  private val p8CaseNames = listOf(
    "title-vi-with-emoji", "title-en-with-emoji", "custom-row-same-title-collision", "unknown-title-untouched",
    "lunar-no-date-ahead-keeps-text-not-annual", "rule-based-moves-forward", "rule-based-this-year-still-ahead",
    "idempotent-second-call-no-writes", "year-end-today", "row-without-emoji-prefix-not-matched",
    "title-nfd-not-matched", "multiple-rows-one-pass", "empty-table", "emoji-only-title",
  )

  private val extraCaseNames = listOf("lunar-today-equals-date-stays", "rule-based-today-equals-date-stays")

  private fun run(case: Case): Outcome = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    try {
      val realDao = db.inLoveDao()
      var calls = 0L
      val dao = object : InLoveDao by realDao {
        override suspend fun updateAnniversaryDate(item: AnniversaryDateEntity) {
          calls++
          realDao.updateAnniversaryDate(item)
        }
      }
      // daysRemaining/updatedAt/pendingSync khong phai nhom doi: dat gia tri khac mac dinh de chung minh khong bi dung toi.
      val inserted = case.rows.mapIndexed { i, r ->
        val entity = AnniversaryDateEntity(
          title = r.title, dateText = r.dateText, isAnnual = r.isAnnual,
          daysRemaining = 7 + i, updatedAt = 1000L + i, pendingSync = true,
        )
        entity.copy(id = realDao.insertAnniversaryDate(entity))
      }
      val date = LocalDate.parse(case.today)
      HolidayDates.refreshExisting(dao, date)
      val first = calls
      var second: Long? = null
      if (case.secondPass) {
        HolidayDates.refreshExisting(dao, date)
        second = calls - first
      }
      Outcome(realDao.getAnniversaryDatesList(), inserted, first, second)
    } finally {
      db.close()
    }
  }

  private fun rowMap(title: String, dateText: String, isAnnual: Boolean) =
    linkedMapOf<String, Any?>("title" to title, "dateText" to dateText, "isAnnual" to isAnnual)

  private fun caseMap(case: Case): Map<String, Any?> {
    val out = run(case)
    val expected = linkedMapOf<String, Any?>(
      "rows" to out.rows.map { rowMap(it.title, it.dateText, it.isAnnual) },
      "updateCalls" to out.calls,
    )
    if (case.secondPass) expected["updateCallsSecondPass"] = out.callsSecondPass
    return linkedMapOf(
      "id" to case.id,
      "input" to linkedMapOf<String, Any?>(
        "today" to case.today,
        "rows" to case.rows.map { rowMap(it.title, it.dateText, it.isAnnual) },
      ),
      "expected" to expected,
      "androidLegacy" to expected,
      "divergence" to null,
      "optional" to false,
    )
  }

  private fun root(): Map<String, Any?> =
    linkedMapOf("schema" to 1L, "zone" to zone, "cases" to cases.map { caseMap(it) })

  @Test
  fun refreshGolden_matchesCommitted() {
    GoldenWriter().check("GV-015-holiday-refresh.json", root())
  }

  @Test
  fun refreshGolden_coversEveryP8CaseName() {
    assertEquals(14, p8CaseNames.size)
    assertEquals(p8CaseNames + extraCaseNames, cases.map { it.id })
    assertEquals(p8CaseNames + extraCaseNames, (root()["cases"] as List<*>).map { (it as Map<*, *>)["id"] })
    assertNotEquals("NFD phai khac NFC", nfcTitle, nfdTitle)
  }

  @Test
  fun refreshGolden_everyCaseRecordsUpdateCalls() {
    for (case in cases) {
      val m = caseMap(case)
      val expected = m["expected"] as Map<*, *>
      val calls = expected["updateCalls"]
      assertTrue("${case.id}: updateCalls phai la Long", calls is Long)
      assertEquals(case.secondPass, expected.containsKey("updateCallsSecondPass"))
      assertEquals(m["expected"], m["androidLegacy"])
      assertEquals(null, m["divergence"])
      // Ham chi doi dateText va isAnnual: moi cot khac cua moi hang giu nguyen, id/thu tu khong doi.
      val out = run(case)
      assertEquals(out.inserted.size, out.rows.size)
      out.rows.zip(out.inserted).forEach { (after, before) ->
        assertEquals("${case.id}: id", before.id, after.id)
        assertEquals("${case.id}: title", before.title, after.title)
        assertEquals("${case.id}: daysRemaining", before.daysRemaining, after.daysRemaining)
        assertEquals("${case.id}: updatedAt", before.updatedAt, after.updatedAt)
        assertEquals("${case.id}: pendingSync", before.pendingSync, after.pendingSync)
      }
    }
    val idem = run(cases.first { it.id == "idempotent-second-call-no-writes" })
    assertEquals(0L, idem.callsSecondPass)
    assertEquals(0L, run(cases.first { it.id == "empty-table" }).calls)
  }
}
