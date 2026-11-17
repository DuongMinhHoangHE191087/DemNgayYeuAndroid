package com.example.golden

import com.example.data.seed.HolidayDates
import com.example.data.seed.VietnameseHolidays
import com.example.data.seed.WesternHolidays
import java.io.File
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * GV-012, GV-013, GV-014: bảng ngày lễ VN (cố định, âm lịch) và ngày lễ phương Tây, cùng kết quả
 * của nextLunar / resolvedDate / resolve / nextRuleBased (Android là đặc tả). JUnit thuần JVM, không DB.
 * Ngày ghi dạng ISO (LocalDate.toString()) để test còn biên dịch sau khi D-11 đổi kiểu ngày.
 * Các ca theo `today` của hàng đã gieo nằm ở GV-204 (HolidaySeedGoldenTest, D-46); refreshExisting ở GV-015 (D-54).
 */
class HolidayGoldenTest {
  private val zone = "Asia/Ho_Chi_Minh"
  private val goldenDir = File("../shared/src/commonTest/resources/golden")
  private lateinit var previousZone: TimeZone
  private lateinit var previousLocale: Locale

  @Before fun pinZoneAndLocale() {
    previousZone = TimeZone.getDefault()
    previousLocale = Locale.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone(zone))
    Locale.setDefault(Locale.ROOT)
  }

  @After fun restoreZoneAndLocale() {
    TimeZone.setDefault(previousZone)
    Locale.setDefault(previousLocale)
  }

  private val fixedName = "GV-012-holiday-vn-fixed.json"
  private val lunarName = "GV-013-holiday-vn-lunar.json"
  private val westernName = "GV-014-holiday-western.json"

  // Tên ca P8 mục 4 giao cho D-02, chép cứng để test đỏ khi thiếu một tên.
  private val fixedP8Names = listOf("table-dump-15-entries", "title-nfc-emoji-prefix")
  private val lunarP8Names = listOf(
    "table-dump-5-entries", "today-equals-table-date", "after-last-date-of-a-year", "year-absent-gives-null",
    "vu-lan-no-upcoming-date-2026-10-13", "tet-last-year-2028", "after-horizon-2029-all-null",
    "gio-to-year-boundary", "trung-thu-after-date", "ram-thang-gieng-2026-2027",
  )
  private val westernP8Names = listOf(
    "table-dump-9-fixed-and-4-rule", "mothers-day-month-starts-on-sunday-2022", "mothers-day-2026-05-10",
    "fathers-day-five-sundays-month", "thanksgiving-fourth-thursday", "sweetest-day-third-saturday",
    "leap-year-2028", "today-equals-rule-date", "day-after-rule-date-next-year", "year-rollover-dec-31",
    "from-jan-1",
  )

  private val lunarSweep = listOf("2024-12-31", "2025-01-01", "2026-01-01", "2026-12-31", "2027-12-31", "2028-12-31")
  private val westernSweep = listOf(
    "2024-01-01", "2024-05-12", "2025-06-15", "2025-11-27", "2026-01-01", "2026-10-17",
    "2027-05-09", "2028-12-31", "2029-06-17", "2029-11-22", "2030-12-31",
  )

  private fun iso(d: LocalDate?): String? = d?.toString()

  private fun obj(vararg pairs: Pair<String, Any?>): LinkedHashMap<String, Any?> = linkedMapOf(*pairs)

  private fun case(id: String, input: Map<String, Any?>, expected: Map<String, Any?>): Map<String, Any?> = obj(
    "id" to id,
    "input" to input,
    "expected" to expected,
    "androidLegacy" to expected,
    "divergence" to null,
    "optional" to false,
  )

  private fun frame(cases: List<Map<String, Any?>>): Map<String, Any?> =
    obj("schema" to 1L, "zone" to zone, "cases" to cases)

  // ---------- GV-012 ----------

  private fun fixedCases(): List<Map<String, Any?>> {
    val table = VietnameseHolidays.fixedHolidays.map {
      obj(
        "emoji" to it.emoji,
        "titleVi" to it.titleVi,
        "titleEn" to it.titleEn,
        "month" to it.month.toLong(),
        "day" to it.day.toLong(),
        "suggestedGiftCategory" to it.suggestedGiftCategory,
        "isCoupleRelevant" to it.isCoupleRelevant,
      )
    }
    val titles = VietnameseHolidays.fixedHolidays.flatMap { h ->
      listOf("vi" to "${h.emoji} ${h.titleVi}", "en" to "${h.emoji} ${h.titleEn}").map { (language, text) ->
        obj(
          "titleVi" to h.titleVi,
          "language" to language,
          "text" to text,
          "isNfc" to Normalizer.isNormalized(text, Normalizer.Form.NFC),
          "firstCodePoint" to "U+" + Integer.toHexString(text.codePointAt(0)).uppercase(),
        )
      }
    }
    return listOf(
      case("table-dump-15-entries", obj("source" to "VietnameseHolidays.fixedHolidays"), obj("entries" to table)),
      case(
        "title-nfc-emoji-prefix",
        obj("templates" to listOf("\${emoji} \${titleVi}", "\${emoji} \${titleEn}")),
        obj("titles" to titles),
      ),
    )
  }

  // ---------- GV-013 ----------

  private val lunar get() = VietnameseHolidays.lunarHolidays

  private fun lunarNext(today: String): Map<String, Any?> = obj(
    "today" to today,
    "next" to lunar.associate { it.titleVi to iso(HolidayDates.nextLunar(it, LocalDate.parse(today))) },
  )

  private fun lunarCase(id: String, todays: List<String>): Map<String, Any?> =
    case(id, obj("todays" to todays), obj("results" to todays.map { lunarNext(it) }))

  private fun lunarHoliday(titleVi: String) = lunar.single { it.titleVi == titleVi }

  private fun lunarLookup(titleVi: String, year: Int): Map<String, Any?> = obj(
    "holiday" to titleVi,
    "year" to year.toLong(),
    "date" to iso(VietnameseHolidays.resolvedDate(lunarHoliday(titleVi), year)),
  )

  private fun lunarCases(): List<Map<String, Any?>> {
    val table = lunar.map { h ->
      obj(
        "titleVi" to h.titleVi,
        "titleEn" to h.titleEn,
        "emoji" to h.emoji,
        "suggestedGiftCategory" to h.suggestedGiftCategory,
        "isCoupleRelevant" to h.isCoupleRelevant,
        "datesByYear" to h.datesByYear.entries.sortedBy { it.key }.associate { it.key.toString() to iso(it.value) },
      )
    }
    val lookups = listOf(lunarLookup("Lễ Vu Lan", 2027), lunarLookup("Rằm Tháng Giêng", 2025))
    return buildList {
      add(case("table-dump-5-entries", obj("source" to "VietnameseHolidays.lunarHolidays"), obj("entries" to table)))
      add(lunarCase("today-equals-table-date", listOf("2026-02-17")))
      add(lunarCase("after-last-date-of-a-year", listOf("2026-02-18")))
      add(
        case(
          "year-absent-gives-null",
          obj("lookups" to lookups.map { obj("holiday" to it["holiday"], "year" to it["year"]) }),
          obj("results" to lookups),
        ),
      )
      add(lunarCase("vu-lan-no-upcoming-date-2026-10-13", listOf("2026-10-13")))
      add(lunarCase("tet-last-year-2028", listOf("2028-01-26", "2028-01-27")))
      add(lunarCase("after-horizon-2029-all-null", listOf("2029-01-01")))
      add(lunarCase("gio-to-year-boundary", listOf("2028-04-04", "2028-04-05")))
      add(lunarCase("trung-thu-after-date", listOf("2026-09-26")))
      add(lunarCase("ram-thang-gieng-2026-2027", listOf("2026-03-04", "2027-02-20")))
      lunarSweep.forEach { add(lunarCase("sweep-today-$it", listOf(it))) }
    }
  }

  // ---------- GV-014 ----------

  private val rule get() = WesternHolidays.ruleBasedHolidays

  private fun ruleYear(year: Int): Map<String, Any?> = obj(
    "year" to year.toLong(),
    "dates" to rule.associate { it.titleEn to iso(it.resolve(year)) },
  )

  private fun ruleNext(today: String): Map<String, Any?> = obj(
    "today" to today,
    "next" to rule.associate { it.titleEn to iso(HolidayDates.nextRuleBased(it, LocalDate.parse(today))) },
  )

  private fun yearsCase(id: String, years: List<Int>): Map<String, Any?> =
    case(id, obj("years" to years.map { it.toLong() }), obj("results" to years.map { ruleYear(it) }))

  private fun ruleNextCase(id: String, todays: List<String>): Map<String, Any?> =
    case(id, obj("todays" to todays), obj("results" to todays.map { ruleNext(it) }))

  private fun westernCases(): List<Map<String, Any?>> {
    val fixed = WesternHolidays.fixedHolidays.map {
      obj(
        "emoji" to it.emoji,
        "titleVi" to it.titleVi,
        "titleEn" to it.titleEn,
        "month" to it.month.toLong(),
        "day" to it.day.toLong(),
        "suggestedGiftCategory" to it.suggestedGiftCategory,
        "isCoupleRelevant" to it.isCoupleRelevant,
      )
    }
    val years = (2022..2030).toList()
    val ruleTable = rule.map { h ->
      obj(
        "emoji" to h.emoji,
        "titleVi" to h.titleVi,
        "titleEn" to h.titleEn,
        "suggestedGiftCategory" to h.suggestedGiftCategory,
        "isCoupleRelevant" to h.isCoupleRelevant,
        "resolvedByYear" to years.associate { it.toString() to iso(h.resolve(it)) },
      )
    }
    return buildList {
      add(
        case(
          "table-dump-9-fixed-and-4-rule",
          obj("sources" to listOf("WesternHolidays.fixedHolidays", "WesternHolidays.ruleBasedHolidays"), "years" to years.map { it.toLong() }),
          obj("fixed" to fixed, "rule" to ruleTable),
        ),
      )
      add(yearsCase("mothers-day-month-starts-on-sunday-2022", listOf(2022)))
      add(yearsCase("mothers-day-2026-05-10", listOf(2026)))
      add(yearsCase("fathers-day-five-sundays-month", listOf(2025)))
      add(yearsCase("thanksgiving-fourth-thursday", listOf(2025, 2026)))
      add(yearsCase("sweetest-day-third-saturday", listOf(2025)))
      add(
        case(
          "leap-year-2028",
          obj("years" to listOf(2028L), "todays" to listOf("2028-02-29")),
          obj("results" to listOf(ruleYear(2028)), "next" to listOf(ruleNext("2028-02-29"))),
        ),
      )
      add(ruleNextCase("today-equals-rule-date", listOf("2026-05-10")))
      add(ruleNextCase("day-after-rule-date-next-year", listOf("2026-05-11")))
      add(ruleNextCase("year-rollover-dec-31", listOf("2026-12-31")))
      add(ruleNextCase("from-jan-1", listOf("2027-01-01")))
      westernSweep.forEach { add(ruleNextCase("sweep-today-$it", listOf(it))) }
    }
  }

  // ---------- tests ----------

  @Test
  fun holidayGolden_matchesCommitted() {
    val writer = GoldenWriter()
    writer.check(fixedName, frame(fixedCases()))
    writer.check(lunarName, frame(lunarCases()))
    writer.check(westernName, frame(westernCases()))
  }

  private fun committedIds(name: String): List<String> {
    val file = File(goldenDir, name)
    if (!file.isFile) throw AssertionError("Thiếu golden $name tại ${file.path}")
    // Chỉ lấy `id` cấp ca: dòng thụt đúng 6 dấu cách trong khung cases.
    return Regex("(?m)^ {6}\"id\": \"([^\"]+)\"").findAll(file.readText(Charsets.UTF_8)).map { it.groupValues[1] }.toList()
  }

  @Test
  fun holidayGolden_coversEveryP8CaseNameAssignedToD02() {
    for ((name, expected) in listOf(fixedName to fixedP8Names, lunarName to lunarP8Names, westernName to westernP8Names)) {
      val ids = committedIds(name)
      for (p8 in expected) assertTrue("$name thiếu ca $p8", p8 in ids)
      assertEquals("$name có id trùng", ids.size, ids.toSet().size)
    }
  }

  @Test
  fun holidayGolden_countsMeetFloors() {
    assertEquals(2, committedIds(fixedName).size)
    assertTrue("GV-013 cần tối thiểu 16 ca", committedIds(lunarName).size >= 16)
    assertTrue("GV-014 cần tối thiểu 22 ca", committedIds(westernName).size >= 22)
    assertEquals(16, lunarCases().size)
    assertEquals(22, westernCases().size)
  }

  private val isoDate = Regex("""\d{4}-\d{2}-\d{2}""")

  private fun collectDigitStrings(v: Any?, out: MutableList<String>) {
    when (v) {
      is String -> if (v.firstOrNull()?.isDigit() == true) out += v
      is Map<*, *> -> v.values.forEach { collectDigitStrings(it, out) }
      is List<*> -> v.forEach { collectDigitStrings(it, out) }
    }
  }

  @Test
  fun holidayGolden_datesAreIso() {
    val strings = mutableListOf<String>()
    collectDigitStrings(fixedCases() + lunarCases() + westernCases(), strings)
    assertTrue("phải có nhiều ngày để kiểm", strings.size > 200)
    for (s in strings) {
      assertTrue("không phải ISO: $s", isoDate.matches(s))
      assertEquals(s, LocalDate.parse(s).toString())
    }
  }
}
