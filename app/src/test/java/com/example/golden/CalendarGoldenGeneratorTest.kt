package com.example.golden

import com.example.golden.LegacyDateOracle.Mode
import com.example.ui.components.DatePickerUtils
import com.example.ui.components.LoveTimeBreakdown
import com.example.ui.components.calculateDetailedLoveTime
import com.example.ui.util.ProfileUtils
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * GV-001: số ngày yêu, ghi từ oracle `LegacyDateOracle` (D-05a), độc lập với `CalendarMath` của P4.
 * `expected` = đếm ngày lịch (oracle CALENDAR, thứ `:shared` phải trả). Mã cũ đếm theo mili giây đã trôi (ELAPSED),
 * nên trong giờ "ma" quanh lúc đồng hồ nhảy, hoặc khi ngày bắt đầu không phải nửa đêm, kết quả lệch một ngày; khi đó ghi thêm
 * `androidLegacy` (kết quả ELAPSED) và `divergence` = "calendar-day-count".
 * Mọi mốc thời gian được tính từ ngày giờ địa phương + zone lúc sinh, golden chỉ chứa số Long cố định.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CalendarGoldenGeneratorTest {
  private val goldenDir = File("../shared/src/commonTest/resources/golden")
  private val goldenName = "GV-001-love-days.json"
  private val rootZone = "Asia/Ho_Chi_Minh"
  private lateinit var previousLocale: Locale
  private lateinit var previousZone: TimeZone

  // Ghim tr-TR và ghi nhớ múi giờ máy: kết quả không được phụ thuộc máy chạy.
  @Before fun pinLocaleAndZone() {
    previousLocale = Locale.getDefault()
    previousZone = TimeZone.getDefault()
    Locale.setDefault(Locale.forLanguageTag("tr-TR"))
  }

  @After fun restoreLocaleAndZone() {
    Locale.setDefault(previousLocale)
    TimeZone.setDefault(previousZone)
  }

  private fun obj(vararg pairs: Pair<String, Any?>): LinkedHashMap<String, Any?> = linkedMapOf(*pairs)

  private fun <T> inZone(zone: String, block: () -> T): T {
    val before = TimeZone.getDefault()
    try {
      TimeZone.setDefault(TimeZone.getTimeZone(zone))
      return block()
    } finally {
      TimeZone.setDefault(before)
    }
  }

  private class LoveCase(
    val id: String,
    val zone: String,
    val startMillis: Long?,
    val startText: String?,
    val nowMillis: Long,
    val startLocal: String?,
    val nowLocal: String,
  )

  private val hcm = "Asia/Ho_Chi_Minh"
  private val la = "America/Los_Angeles"
  private val akl = "Pacific/Auckland"
  private val lon = "Europe/London"
  private val lh = "Australia/Lord_Howe"

  private fun at(zone: String, y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0, milli: Int = 0): ZonedDateTime =
    ZonedDateTime.of(y, mo, d, h, mi, 0, milli * 1_000_000, ZoneId.of(zone))

  private fun ms(id: String, start: ZonedDateTime, now: ZonedDateTime) = LoveCase(
    id,
    now.zone.id,
    start.toInstant().toEpochMilli(),
    null,
    now.toInstant().toEpochMilli(),
    start.toString(),
    now.toString(),
  )

  private fun txt(id: String, text: String, now: ZonedDateTime) =
    LoveCase(id, now.zone.id, null, text, now.toInstant().toEpochMilli(), null, now.toString())

  private val nowHcm = at(hcm, 2024, 6, 15, 12)

  private val cases: List<LoveCase> = listOf(
    // 14 tên P8.
    ms("start-equals-now", nowHcm, nowHcm),
    ms("minus-1ms", nowHcm.minus(1, ChronoUnit.MILLIS), nowHcm),
    ms("exactly-24h", nowHcm.minusHours(24), nowHcm),
    // now = 23:59:59.999 để start cùng ngày lịch với now: hai cách đếm trùng nhau.
    at(hcm, 2024, 6, 15, 23, 59, 999).plusSeconds(59).let { now -> ms("24h-minus-1ms", now.minusHours(24).plus(1, ChronoUnit.MILLIS), now) },
    ms("future-start-long-gives-0", nowHcm.plusDays(1), nowHcm),
    txt("blank-string-gives-1", "", nowHcm),
    txt("unparsable-string-gives-1", "not-a-date", nowHcm),
    // UTC để không phụ thuộc lịch sử múi giờ năm 1970; start = 1970-01-01T00:00Z.
    at("UTC", 2024, 6, 15, 12).let { now ->
      LoveCase("epoch-0", "UTC", 0L, null, now.toInstant().toEpochMilli(), at("UTC", 1970, 1, 1).toString(), now.toString())
    },
    txt("leap-day-start", "29/02/2024", at(hcm, 2024, 3, 1, 12)),
    ms("dst-los-angeles-spring", at(la, 2024, 3, 9), at(la, 2024, 3, 11, 0, 30)),
    ms("dst-los-angeles-fall", at(la, 2024, 11, 2), at(la, 2024, 11, 3, 23, 30)),
    ms("dst-auckland-spring", at(akl, 2024, 9, 28), at(akl, 2024, 9, 30, 0, 30)),
    ms("dst-auckland-autumn", at(akl, 2024, 4, 6), at(akl, 2024, 4, 7, 23, 30)),
    txt("ho-chi-minh-plain", "01/01/2024", nowHcm),

    // America/Los_Angeles: 10-03-2024 (nhảy tới) và 03-11-2024 (lùi).
    ms("dst-los-angeles-spring-outside-ghost-hour-agrees", at(la, 2024, 3, 9), at(la, 2024, 3, 11, 12)),
    ms("dst-los-angeles-spring-start-on-transition-day", at(la, 2024, 3, 10), at(la, 2024, 3, 11, 0, 30)),
    ms("dst-los-angeles-spring-same-day-agrees", at(la, 2024, 3, 10), at(la, 2024, 3, 10, 23)),
    ms("dst-los-angeles-fall-outside-ghost-hour-agrees", at(la, 2024, 11, 2), at(la, 2024, 11, 3, 12)),
    ms("dst-los-angeles-fall-start-on-transition-day", at(la, 2024, 11, 3), at(la, 2024, 11, 3, 23, 30)),
    ms("dst-los-angeles-fall-same-day-agrees", at(la, 2024, 11, 3), at(la, 2024, 11, 3, 12)),
    txt("start-text-dst-day-los-angeles", "10/03/2024", at(la, 2024, 3, 11, 0, 30)),

    // Pacific/Auckland: 29-09-2024 và 07-04-2024.
    ms("dst-auckland-spring-outside-ghost-hour-agrees", at(akl, 2024, 9, 28), at(akl, 2024, 9, 30, 12)),
    ms("dst-auckland-autumn-outside-ghost-hour-agrees", at(akl, 2024, 4, 6), at(akl, 2024, 4, 7, 12)),

    // Europe/London: 31-03-2024 và 27-10-2024.
    ms("dst-london-spring-ghost-hour", at(lon, 2024, 3, 30), at(lon, 2024, 4, 1, 0, 30)),
    ms("dst-london-autumn-ghost-hour", at(lon, 2024, 10, 26), at(lon, 2024, 10, 27, 23, 30)),
    ms("dst-london-spring-outside-ghost-hour-agrees", at(lon, 2024, 3, 30), at(lon, 2024, 4, 1, 12)),

    // Australia/Lord_Howe: 07-04-2024 và 06-10-2024, nhảy nửa giờ.
    ms("dst-lord-howe-spring-half-hour", at(lh, 2024, 10, 5), at(lh, 2024, 10, 7, 0, 10)),
    ms("dst-lord-howe-autumn-half-hour", at(lh, 2024, 4, 6), at(lh, 2024, 4, 7, 23, 45)),
    ms("dst-lord-howe-spring-outside-half-hour-agrees", at(lh, 2024, 10, 5), at(lh, 2024, 10, 7, 12)),

    // Năm nhuận, ngày 29-02, qua năm.
    ms("leap-2024-feb28-to-feb29", at(hcm, 2024, 2, 28), at(hcm, 2024, 2, 29, 12)),
    ms("leap-2024-feb28-to-mar01", at(hcm, 2024, 2, 28), at(hcm, 2024, 3, 1, 12)),
    ms("non-leap-2023-feb28-to-mar01", at(hcm, 2023, 2, 28), at(hcm, 2023, 3, 1, 12)),
    ms("leap-2028-feb29-to-mar01", at(hcm, 2028, 2, 29), at(hcm, 2028, 3, 1, 12)),
    ms("leap-2028-feb29-to-next-feb28", at(hcm, 2028, 2, 29), at(hcm, 2029, 2, 28, 12)),
    ms("cross-year-dec31-to-jan01", at(hcm, 2023, 12, 31), at(hcm, 2024, 1, 1, 12)),
    ms("cross-year-jan01-to-dec31-leap", at(hcm, 2024, 1, 1), at(hcm, 2024, 12, 31, 12)),

    // Ca chuỗi "dd/MM/yyyy".
    txt("start-text-lenient-rejected-31-feb", "31/02/2024", nowHcm),
    txt("start-text-padded", "  15/06/2024  ", at(hcm, 2024, 6, 16, 12)),
    txt("start-text-iso-format-gives-1", "2024-06-15", nowHcm),
    txt("start-text-single-digit-day-month", "1/2/2024", nowHcm),
    txt("start-text-future-gives-0", "01/01/2099", nowHcm),
  )

  // GV-003: ban phan ra thoi gian yeu. Input = {startText, nowMillis, zone}; start luon la nua dem dia phuong cua ngay trong chuoi.
  private val breakdownGoldenName = "GV-003-love-time-breakdown.json"

  private class BreakdownCase(val id: String, val zone: String, val startText: String, val nowMillis: Long)

  private fun bc(id: String, text: String, now: ZonedDateTime) = BreakdownCase(id, now.zone.id, text, now.toInstant().toEpochMilli())

  private val breakdownCases: List<BreakdownCase> = listOf(
    // 17 ten P8. Ca khong thuoc cua so DST chon now luc 12:00 dia phuong de hai cach dem trung nhau.
    bc("now-equals-start", "15/06/2024", at(hcm, 2024, 6, 15)),
    bc("one-second-before-start", "15/06/2024", at(hcm, 2024, 6, 14, 23, 59).plusSeconds(59)),
    bc("future-start", "01/01/2099", nowHcm),
    bc("blank", "", nowHcm),
    bc("invalid-text", "not-a-date", nowHcm),
    bc("start-31-01-now-01-03", "31/01/2024", at(hcm, 2024, 3, 1, 12)),
    bc("start-29-02-leap", "29/02/2024", at(hcm, 2025, 2, 28, 12)),
    bc("exactly-one-year", "15/06/2023", nowHcm),
    bc("exactly-100-days", "08/03/2024", nowHcm),
    // Los_Angeles 10-03-2024 (ngay 23h) va 03-11-2024 (ngay 25h): now trong gio cuoi ngay nen dem milli giay lech ngay lich.
    bc("dst-23h-day", "10/03/2024", at(la, 2024, 3, 11, 0, 30)),
    bc("dst-25h-day", "03/11/2024", at(la, 2024, 11, 3, 23, 30)),
    bc("year-end-rollover", "31/12/2023", at(hcm, 2024, 1, 1, 12)),
    bc("month-borrow", "20/03/2024", at(hcm, 2024, 6, 10, 12)),
    // Bon dinh dang nhap cung mot ngay 05-03-2024.
    bc("four-input-formats", "05/03/2024", nowHcm),
    bc("zone-ho-chi-minh", "01/01/2024", nowHcm),
    bc("zone-los-angeles", "01/01/2024", at(la, 2024, 6, 15, 12)),
    bc("zone-auckland", "01/01/2024", at(akl, 2024, 6, 15, 12)),

    // Ca them.
    bc("four-input-formats-d-m-yyyy", "5/3/2024", nowHcm),
    bc("four-input-formats-dd-mm-yyyy-dash", "05-03-2024", nowHcm),
    bc("four-input-formats-iso", "2024-03-05", nowHcm),
    bc("dst-23h-day-outside-ghost-hour-agrees", "09/03/2024", at(la, 2024, 3, 11, 12)),
    bc("dst-25h-day-outside-ghost-hour-agrees", "02/11/2024", at(la, 2024, 11, 3, 12)),
    bc("la-year-end-rollover", "31/12/2023", at(la, 2024, 1, 1, 12)),
    // Auckland 29-09-2024 (ngay 23h) va 07-04-2024 (ngay 25h).
    bc("auckland-dst-23h-day", "29/09/2024", at(akl, 2024, 9, 30, 0, 30)),
    bc("auckland-dst-25h-day", "07/04/2024", at(akl, 2024, 4, 7, 23, 30)),
    bc("auckland-dst-23h-day-outside-ghost-hour-agrees", "28/09/2024", at(akl, 2024, 9, 30, 12)),
    bc("auckland-dst-25h-day-outside-ghost-hour-agrees", "06/04/2024", at(akl, 2024, 4, 7, 12)),
    bc("auckland-month-borrow", "20/03/2024", at(akl, 2024, 6, 10, 12)),

    // Ngoai P8 (Claude them): cot moc lon. now = 15/06/2024 12:00 Ho_Chi_Minh; totalDays tinh tay: 365, 897, 1628, 5000, 5280.
    bc("exactly-365-days", "17/06/2023", nowHcm),
    bc("milestone-range-730-1000", "01/01/2022", nowHcm),
    bc("milestone-range-1500-1825", "01/01/2020", nowHcm),
    bc("exactly-5000-days", "08/10/2010", nowHcm),
    bc("beyond-5000-multiples-of-500", "01/01/2010", nowHcm),
    // Ngoai P8 (Claude them): dung tren cot moc 1349 / 2500 / 3650 (totalDays 1349, 2500, 3650) de golden tu bat viec bo cot moc.
    bc("exactly-1349-days", "06/10/2020", nowHcm),
    bc("exactly-2500-days", "12/08/2017", nowHcm),
    bc("exactly-3650-days", "19/06/2014", nowHcm),
    // Ngoai P8: 31/02 bi tu choi (khong lenient) -> toan so 0; xuong dong bao quanh chi qua duoc nho trim() (giong 05/03/2024).
    bc("lenient-rejected-31-02", "31/02/2024", nowHcm),
    bc("start-text-newline-wrapped", "${10.toChar()}05/03/2024${10.toChar()}", nowHcm),
  )

  private val p8BreakdownNames = listOf(
    "now-equals-start",
    "one-second-before-start",
    "future-start",
    "blank",
    "invalid-text",
    "start-31-01-now-01-03",
    "start-29-02-leap",
    "exactly-one-year",
    "exactly-100-days",
    "dst-23h-day",
    "dst-25h-day",
    "year-end-rollover",
    "month-borrow",
    "four-input-formats",
    "zone-ho-chi-minh",
    "zone-los-angeles",
    "zone-auckland",
  )

  private fun breakdown(c: BreakdownCase, mode: Mode): LegacyDateOracle.Breakdown =
    inZone(c.zone) { LegacyDateOracle.calculateDetailedLoveTime(c.startText, c.nowMillis, mode) }

  private fun progressText(value: Double): String = String.format(Locale.ROOT, "%.6f", value)

  private fun breakdownMap(b: LegacyDateOracle.Breakdown): LinkedHashMap<String, Any?> = obj(
    "totalDays" to b.totalDays,
    "years" to b.years.toLong(),
    "months" to b.months.toLong(),
    "days" to b.days.toLong(),
    "totalWeeks" to b.totalWeeks,
    "remainingDaysInWeek" to b.remainingDaysInWeek,
    "totalHours" to b.totalHours,
    "totalMinutes" to b.totalMinutes,
    "totalSeconds" to b.totalSeconds,
    "nextMilestoneDays" to b.nextMilestoneDays.toLong(),
    "daysUntilMilestone" to b.daysUntilMilestone.toLong(),
    "milestoneProgress" to progressText(b.milestoneProgress.toDouble()),
  )

  private fun breakdownCaseMaps(): List<Map<String, Any?>> = breakdownCases.map { c ->
    val expected = breakdownMap(breakdown(c, Mode.CALENDAR))
    val legacy = breakdownMap(breakdown(c, Mode.ELAPSED))
    val diverges = expected != legacy
    obj(
      "id" to c.id,
      "input" to obj("startText" to c.startText, "nowMillis" to c.nowMillis, "zone" to c.zone),
      "expected" to expected,
      // Giong GV-001: androidLegacy chi co khi lech; khong lech thi null.
      "androidLegacy" to if (diverges) legacy else null,
      "divergence" to if (diverges) "calendar-day-count" else null,
      "optional" to false,
    )
  }

  private fun writeOrVerifyBreakdownGolden() {
    GoldenWriter().check(breakdownGoldenName, obj("schema" to 1L, "zone" to rootZone, "cases" to breakdownCaseMaps()))
  }

  private val p8Names = listOf(
    "start-equals-now",
    "minus-1ms",
    "exactly-24h",
    "24h-minus-1ms",
    "future-start-long-gives-0",
    "blank-string-gives-1",
    "unparsable-string-gives-1",
    "epoch-0",
    "leap-day-start",
    "dst-los-angeles-spring",
    "dst-los-angeles-fall",
    "dst-auckland-spring",
    "dst-auckland-autumn",
    "ho-chi-minh-plain",
  )

  // Ca mà đếm theo mili giây và đếm theo ngày lịch phải cho kết quả khác nhau (tính tay, độc lập với oracle).
  private val divergentIds = setOf(
    "dst-los-angeles-spring",
    "dst-los-angeles-fall",
    "dst-auckland-spring",
    "dst-auckland-autumn",
    "dst-los-angeles-spring-start-on-transition-day",
    "dst-los-angeles-fall-start-on-transition-day",
    "start-text-dst-day-los-angeles",
    "dst-london-spring-ghost-hour",
    "dst-london-autumn-ghost-hour",
    "dst-lord-howe-spring-half-hour",
    "dst-lord-howe-autumn-half-hour",
  )

  private fun days(c: LoveCase, mode: Mode): Int = inZone(c.zone) {
    if (c.startText != null) {
      LegacyDateOracle.calculateLoveDays(c.startText, c.nowMillis, mode)
    } else {
      LegacyDateOracle.calculateLoveDays(c.startMillis!!, c.nowMillis, mode)
    }
  }

  private fun caseMaps(): List<Map<String, Any?>> = cases.map { c ->
    val expected = days(c, Mode.CALENDAR)
    val legacy = days(c, Mode.ELAPSED)
    val diverges = expected != legacy
    obj(
      "id" to c.id,
      "input" to obj(
        "zone" to c.zone,
        "nowMillis" to c.nowMillis,
        "nowLocal" to c.nowLocal,
        "startMillis" to c.startMillis,
        "startText" to c.startText,
        "startLocal" to c.startLocal,
      ),
      "expected" to obj("days" to expected.toLong()),
      "androidLegacy" to if (diverges) obj("days" to legacy.toLong()) else null,
      "divergence" to if (diverges) "calendar-day-count" else null,
      "optional" to false,
    )
  }

  private fun writeOrVerifyGolden() {
    GoldenWriter().check(goldenName, obj("schema" to 1L, "zone" to rootZone, "cases" to caseMaps()))
  }

  // Test đọc file đã commit cần file đã được ghi: khi GOLDEN_UPDATE=1, ghi trước để một lượt chạy là đủ.
  private fun committedText(name: String = goldenName): String {
    if (System.getenv("GOLDEN_UPDATE") == "1") {
      if (name == goldenName) writeOrVerifyGolden() else writeOrVerifyBreakdownGolden()
    }
    val file = File(goldenDir, name)
    if (!file.isFile) throw AssertionError("Thiếu golden $name tại ${file.path} (chạy với GOLDEN_UPDATE=1)")
    return file.readText(Charsets.UTF_8).replace("\r\n", "\n")
  }

  // Chỉ lấy `id` cấp ca: dòng thụt đúng 6 dấu cách trong khung cases.
  private fun committedIds(name: String = goldenName): List<String> =
    Regex("(?m)^ {6}\"id\": \"([^\"]+)\"").findAll(committedText(name)).map { it.groupValues[1] }.toList()

  @Test
  fun loveDaysGolden_matchesCommitted() {
    writeOrVerifyGolden()
  }

  @Test
  fun loveDaysGolden_coversAllP8CaseNames() {
    assertEquals(14, p8Names.size)
    val ids = committedIds()
    assertEquals(ids.size, ids.toSet().size)
    for (name in p8Names) assertTrue("Thiếu ca P8 $name trong file golden", name in ids)
    assertEquals(cases.map { it.id }, ids)
  }

  @Test
  fun loveDaysGolden_hasAtLeast30Cases() {
    val count = committedIds().size
    assertTrue("Chỉ có $count ca", count >= 30)
  }

  @Test
  fun loveDaysGolden_divergenceHasBothKeys() {
    for (case in caseMaps()) {
      assertEquals("${case["id"]}: divergence và androidLegacy phải đi cùng nhau", case["divergence"] != null, case["androidLegacy"] != null)
    }
    // Trên file đã commit: mỗi `divergence` khác null đi kèm đúng một `androidLegacy` là object, và ngược lại.
    val text = committedText()
    val divergences = Regex("(?m)^ {6}\"divergence\": \"calendar-day-count\"").findAll(text).count()
    val legacies = Regex("(?m)^ {6}\"androidLegacy\": \\{").findAll(text).count()
    val nullDivergences = Regex("(?m)^ {6}\"divergence\": null").findAll(text).count()
    val nullLegacies = Regex("(?m)^ {6}\"androidLegacy\": null").findAll(text).count()
    assertEquals(divergences, legacies)
    assertEquals(nullDivergences, nullLegacies)
    assertEquals(committedIds().size, divergences + nullDivergences)
    assertEquals(divergentIds.size, divergences)
  }

  @Test
  fun breakdownGolden_matchesCommitted() {
    writeOrVerifyBreakdownGolden()
  }

  @Test
  fun breakdownGolden_coversAllP8CaseNames() {
    assertEquals(17, p8BreakdownNames.size)
    val ids = committedIds(breakdownGoldenName)
    assertEquals(ids.size, ids.toSet().size)
    for (name in p8BreakdownNames) assertTrue("Thieu ca P8 $name trong GV-003", name in ids)
    assertEquals(breakdownCases.map { it.id }, ids)
  }

  @Test
  fun breakdownGolden_hasAtLeast20CasesAnd6PerZone() {
    val count = committedIds(breakdownGoldenName).size
    assertTrue("Chi co $count ca", count >= 20)
    // `zone` cua input nam o muc thut 8 dau cach.
    val zones = Regex("(?m)^ {8}\"zone\": \"([^\"]+)\"").findAll(committedText(breakdownGoldenName)).map { it.groupValues[1] }.toList()
    assertEquals(count, zones.size)
    for (zone in listOf(hcm, la, akl)) {
      val n = zones.count { it == zone }
      assertTrue("Zone $zone chi co $n ca", n >= 6)
    }
  }

  @Test
  fun breakdownGolden_divergenceHasBothKeys() {
    val maps = breakdownCaseMaps()
    for (case in maps) {
      assertEquals("${case["id"]}: divergence va androidLegacy phai di cung nhau", case["divergence"] != null, case["androidLegacy"] != null)
      if (case["divergence"] != null) assertEquals("calendar-day-count", case["divergence"])
    }
    val diverging = maps.filter { it["divergence"] != null }.map { it["id"] as String }.toSet()
    // Ca DST that su phai tao khac biet; ca P8 khac khong duoc tao khac biet gia.
    assertTrue("dst-23h-day hoac dst-25h-day phai lech", "dst-23h-day" in diverging || "dst-25h-day" in diverging)
    for (name in p8BreakdownNames) {
      if (name != "dst-23h-day" && name != "dst-25h-day") assertTrue("$name khong duoc co divergence", name !in diverging)
    }
    val text = committedText(breakdownGoldenName)
    val divergences = Regex("(?m)^ {6}\"divergence\": \"calendar-day-count\"").findAll(text).count()
    val legacies = Regex("(?m)^ {6}\"androidLegacy\": \\{").findAll(text).count()
    val nullDivergences = Regex("(?m)^ {6}\"divergence\": null").findAll(text).count()
    val nullLegacies = Regex("(?m)^ {6}\"androidLegacy\": null").findAll(text).count()
    assertEquals(divergences, legacies)
    assertEquals(nullDivergences, nullLegacies)
    assertEquals(committedIds(breakdownGoldenName).size, divergences + nullDivergences)
    assertEquals(diverging.size, divergences)
  }

  @Test
  fun oracle_calendarModeDiffersOnlyInDaysDerivedKeys() {
    for (c in breakdownCases) {
      val cal = breakdown(c, Mode.CALENDAR)
      val el = breakdown(c, Mode.ELAPSED)
      assertEquals("${c.id}: years", el.years, cal.years)
      assertEquals("${c.id}: months", el.months, cal.months)
      assertEquals("${c.id}: days", el.days, cal.days)
      assertEquals("${c.id}: totalSeconds", el.totalSeconds, cal.totalSeconds)
      // Neu totalDays trung thi moi khoa suy ra cung trung.
      if (el.totalDays == cal.totalDays) assertEquals("${c.id}: trung totalDays thi trung het", el, cal)
    }
  }

  @Test
  fun oracle_fourInputFormatsGiveSameResult() {
    val byId = breakdownCases.associateBy { it.id }
    val first = breakdown(byId.getValue("four-input-formats"), Mode.CALENDAR)
    assertTrue("Ca four-input-formats phai doc duoc ngay", first.totalDays > 0)
    for (id in listOf("four-input-formats-d-m-yyyy", "four-input-formats-dd-mm-yyyy-dash", "four-input-formats-iso", "start-text-newline-wrapped")) {
      assertEquals(id, first, breakdown(byId.getValue(id), Mode.CALENDAR))
    }
  }

  @Test
  fun milestoneProgress_sixDecimalsAgreeBetweenFloatAndDouble() {
    for (c in breakdownCases) {
      for (mode in Mode.values()) {
        val b = breakdown(c, mode)
        val prev = LegacyDateOracle.STANDARD_MILESTONES.lastOrNull { it <= b.totalDays } ?: 0
        val next = b.nextMilestoneDays
        val asDouble = if (next > prev) ((b.totalDays - prev).toDouble() / (next - prev).toDouble()).coerceIn(0.0, 1.0) else 1.0
        assertEquals("${c.id} $mode", progressText(asDouble), progressText(b.milestoneProgress.toDouble()))
      }
    }
  }

  // Ca khong lech: oracle ELAPSED phai bang ham that, tung truong, voi zone va locale cua ca.
  // Sau khi P4 doi ham that sang CalendarMath van xanh vi chi so cac ca khong khac biet.
  @Test
  fun oracle_breakdownMatchesRealLegacyWhereNoDivergence() {
    val diverging = breakdownCaseMaps().filter { it["divergence"] != null }.map { it["id"] }.toSet()
    var compared = 0
    for (c in breakdownCases) {
      if (c.id in diverging) continue
      val oracle = breakdown(c, Mode.ELAPSED)
      val real = inZone(c.zone) { calculateDetailedLoveTime(c.startText, c.nowMillis) }
      assertSameBreakdown(c.id, oracle, real)
      compared++
    }
    assertTrue("Qua it ca so voi ham that: $compared", compared >= 20)
  }

  // Quet tung so ngay 1..5601 o Ho_Chi_Minh (khong DST, now luc 12:00): oracle ELAPSED == CALENDAR == ham that, tung truong.
  // Ghim moi cot moc (365, 730, 1000, 1349, ..., 5000) va nhanh boi so cua 500, ke ca khi golden khong co ca nao o do.
  @Test
  fun oracle_sweepEveryDayCountMatchesRealLegacy() {
    val now = at(hcm, 2024, 6, 15, 12)
    val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    for (n in 0L..5600L) {
      val text = now.toLocalDate().minusDays(n).format(fmt)
      val nowMillis = now.toInstant().toEpochMilli()
      val oracle = inZone(hcm) { LegacyDateOracle.calculateDetailedLoveTime(text, nowMillis, Mode.ELAPSED) }
      val calendar = inZone(hcm) { LegacyDateOracle.calculateDetailedLoveTime(text, nowMillis, Mode.CALENDAR) }
      val real = inZone(hcm) { calculateDetailedLoveTime(text, nowMillis) }
      assertEquals("n=$n totalDays", n + 1, oracle.totalDays)
      assertEquals("n=$n CALENDAR == ELAPSED", oracle, calendar)
      assertSameBreakdown("n=$n $text", oracle, real)
    }
  }

  private fun assertSameBreakdown(id: String, o: LegacyDateOracle.Breakdown, r: LoveTimeBreakdown) {
    assertEquals("$id totalDays", o.totalDays, r.totalDays)
    assertEquals("$id years", o.years, r.years)
    assertEquals("$id months", o.months, r.months)
    assertEquals("$id days", o.days, r.days)
    assertEquals("$id totalWeeks", o.totalWeeks, r.totalWeeks)
    assertEquals("$id remainingDaysInWeek", o.remainingDaysInWeek, r.remainingDaysInWeek)
    assertEquals("$id totalHours", o.totalHours, r.totalHours)
    assertEquals("$id totalMinutes", o.totalMinutes, r.totalMinutes)
    assertEquals("$id totalSeconds", o.totalSeconds, r.totalSeconds)
    assertEquals("$id nextMilestoneDays", o.nextMilestoneDays, r.nextMilestoneDays)
    assertEquals("$id daysUntilMilestone", o.daysUntilMilestone, r.daysUntilMilestone)
    assertEquals("$id milestoneProgress", o.milestoneProgress.toRawBits(), r.milestoneProgress.toRawBits())
  }

  @Test
  fun oracle_leapDays() {
    fun count(zone: String, sy: Int, sm: Int, sd: Int, ny: Int, nm: Int, nd: Int) =
      inZone(zone) {
        LegacyDateOracle.calculateLoveDays(at(zone, sy, sm, sd).toInstant().toEpochMilli(), at(zone, ny, nm, nd, 12).toInstant().toEpochMilli(), Mode.CALENDAR)
      }
    assertEquals(1, count(hcm, 2024, 2, 28, 2024, 2, 28))
    assertEquals(2, count(hcm, 2024, 2, 28, 2024, 2, 29))
    assertEquals(3, count(hcm, 2024, 2, 28, 2024, 3, 1))
    assertEquals(2, count(hcm, 2023, 2, 28, 2023, 3, 1))
    assertEquals(2, count(hcm, 2028, 2, 29, 2028, 3, 1))
    assertEquals(366, count(hcm, 2028, 2, 29, 2029, 2, 28))
    assertEquals(366, count(hcm, 2024, 1, 1, 2024, 12, 31))
  }

  @Test
  fun oracle_dstWindowsKeepElapsedSemantics() {
    val byId = cases.associateBy { it.id }
    val spring = byId.getValue("dst-los-angeles-spring")
    val fall = byId.getValue("dst-los-angeles-fall")
    assertEquals(2, days(spring, Mode.ELAPSED))
    assertEquals(3, days(spring, Mode.CALENDAR))
    assertEquals(3, days(fall, Mode.ELAPSED))
    assertEquals(2, days(fall, Mode.CALENDAR))
    // Cùng cửa sổ nhưng now ngoài giờ ma: hai cách đếm trùng nhau.
    assertEquals(3, days(byId.getValue("dst-los-angeles-spring-outside-ghost-hour-agrees"), Mode.ELAPSED))
    assertEquals(2, days(byId.getValue("dst-los-angeles-fall-outside-ghost-hour-agrees"), Mode.ELAPSED))
  }

  @Test
  fun oracle_calendarModeDiffersOnlyInDivergenceCases() {
    val ids = cases.map { it.id }
    assertEquals(ids.size, ids.toSet().size)
    for (id in divergentIds) assertTrue("divergentIds có id lạ: $id", id in ids)
    for (c in cases) {
      val calendar = days(c, Mode.CALENDAR)
      val elapsed = days(c, Mode.ELAPSED)
      if (c.id in divergentIds) {
        assertEquals("${c.id}: lệch đúng một ngày", 1, Math.abs(calendar - elapsed))
      } else {
        assertEquals("${c.id}: không được lệch", calendar, elapsed)
      }
    }
    // Các ca P8 không phải DST không được tạo khác biệt giả.
    for (name in listOf("start-equals-now", "minus-1ms", "exactly-24h", "24h-minus-1ms", "epoch-0", "ho-chi-minh-plain")) {
      assertTrue("$name không được có divergence", name !in divergentIds)
    }
  }

  // Hàm thật dùng đồng hồ thật nên so ca "tương đối theo now thật" ở múi giờ không DST, không so ca golden.
  // Start căn nửa đêm địa phương: mã cũ chỉ trùng ngày lịch khi start là nửa đêm.
  // ponytail: nếu test chạy đúng lúc qua nửa đêm giờ Việt Nam thì có thể lệch một ngày; chấp nhận, chạy lại.
  @Test
  fun oracle_loveDaysMatchesRealLegacyWhereNoDivergence() {
    inZone(hcm) {
      val zone = ZoneId.of(hcm)
      val now = System.currentTimeMillis()
      val today = LocalDate.now(zone)
      for (n in listOf(0L, 1L, 2L, 7L, 365L)) {
        val start = today.minusDays(n).atStartOfDay(zone).toInstant().toEpochMilli()
        val real = ProfileUtils.calculateLoveDays(start)
        assertEquals("n=$n ELAPSED", real, LegacyDateOracle.calculateLoveDays(start, now, Mode.ELAPSED))
        assertEquals("n=$n CALENDAR", real, LegacyDateOracle.calculateLoveDays(start, now, Mode.CALENDAR))
        assertEquals((n + 1).toInt(), real)
      }
      val future = now + 24L * 3600 * 1000
      assertEquals(0, ProfileUtils.calculateLoveDays(future))
      assertEquals(0, LegacyDateOracle.calculateLoveDays(future, now, Mode.ELAPSED))
      assertEquals(0, LegacyDateOracle.calculateLoveDays(future, now, Mode.CALENDAR))

      val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
      // "\n...\n" chỉ qua được nhờ trim(): SimpleDateFormat tự bỏ dấu cách và tab đầu chuỗi, nhưng không bỏ xuống dòng.
      val strings = listOf("" to 1, "   " to 1, "abc" to 1, today.format(fmt) to 1, today.minusDays(1).format(fmt) to 2, "\n${today.minusDays(1).format(fmt)}\n" to 2)
      for ((s, want) in strings) {
        assertEquals("real '$s'", want, ProfileUtils.calculateLoveDays(s))
        assertEquals("ELAPSED '$s'", want, LegacyDateOracle.calculateLoveDays(s, now, Mode.ELAPSED))
        assertEquals("CALENDAR '$s'", want, LegacyDateOracle.calculateLoveDays(s, now, Mode.CALENDAR))
      }
    }
  }

  @Test
  fun oracle_dateReadersMatchRealLegacy() {
    val strings = listOf(
      "15/06/2024",
      "01/02/2024",
      "1/2/2024",
      "2024-02-29",
      "29-02-2024",
      "29/02/2024",
      "29/02/2023",
      "31/02/2024",
      "",
      "abc",
      "  15/06/2024  ", "\n15/06/2024\n",
      "15/6/2024",
      "15/06/24",
    )
    val millisSamples = listOf(0L, -1L, 1_709_164_800_000L, 1_718_409_600_000L, 1_709_251_199_999L)
    // Mã thật luôn ghi lịch Gregorian chữ số ASCII (DEC-75), nên oracle được đánh giá dưới Locale.ROOT; riêng th-TH mã thật chạy dưới th-TH.
    for (locale in listOf(Locale.forLanguageTag("tr-TR"), Locale.ROOT, Locale.forLanguageTag("th-TH"))) {
      Locale.setDefault(locale)
      val thai = locale.language == "th"
      fun <T> ora(block: () -> T): T {
        if (!thai) return block()
        Locale.setDefault(Locale.ROOT)
        try {
          return block()
        } finally {
          Locale.setDefault(locale)
        }
      }
      inZone("America/Los_Angeles") {
        for (s in strings) {
          assertEquals("DatePicker parse '$s' $locale", DatePickerUtils.parseDateToUtcMillis(s), ora { LegacyDateOracle.parseDateToUtcMillis(s) })
          // Mã thật trả đồng hồ thật khi lỗi: dùng -1 làm dấu hiệu lỗi cho oracle rồi so với cửa sổ đồng hồ.
          val before = System.currentTimeMillis()
          val real = ProfileUtils.parseDateToMillis(s)
          val after = System.currentTimeMillis()
          val oracle = ora { LegacyDateOracle.parseDateToMillis(s, -1L) }
          if (oracle == -1L) {
            assertTrue("ProfileUtils parse '$s' $locale phải trả đồng hồ thật: $real", real in before..after)
          } else {
            assertEquals("ProfileUtils parse '$s' $locale", real, oracle)
          }
        }
        if (thai) {
          assertNotEquals("oracle dưới th-TH phải ghi năm Phật lịch, khác mã thật", ProfileUtils.formatDate(1_709_164_800_000L), LegacyDateOracle.formatDate(1_709_164_800_000L))
        }
        for (m in millisSamples) {
          assertEquals("ProfileUtils format $m $locale", ProfileUtils.formatDate(m), ora { LegacyDateOracle.formatDate(m) })
          assertEquals("DatePicker format $m $locale", DatePickerUtils.formatUtcMillisToDate(m), ora { LegacyDateOracle.formatUtcMillisToDate(m) })
          assertEquals(
            "DatePicker format iso $m $locale",
            DatePickerUtils.formatUtcMillisToDate(m, "yyyy-MM-dd"),
            ora { LegacyDateOracle.formatUtcMillisToDate(m, "yyyy-MM-dd") },
          )
        }
      }
    }
    // Hai bộ đọc khác nhau thật: UTC (DatePicker) và múi giờ máy (ProfileUtils); sai múi giờ là sai ngày.
    inZone("America/Los_Angeles") {
      assertNotEquals(DatePickerUtils.parseDateToUtcMillis("15/06/2024"), ProfileUtils.parseDateToMillis("15/06/2024"))
    }
  }
}
