package com.example.golden

import com.example.alarm.LoveAnniversaryMilestoneScheduler
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * GV-007: `parseStartDateMillis(startDateMillis, startDateText)`.
 * `expected` = Long millis (nua dem 00:00 gio Asia/Ho_Chi_Minh, tinh bang java.time, lich Gregorian + chu so ASCII,
 * khong phu thuoc locale may) hoac chuoi "NOW" khi ham roi ve dong ho (khong bao gio ghi gia tri dong ho vao golden;
 * test kiem ket qua nam trong [truoc, sau] lan goi). Ba ca locale (th-TH, ja-JP lich Nhat, ar-EG chu so Arap) la
 * `optional` vi ket qua that phu thuoc du lieu lich cua JVM: khi khac `expected` thi ghi `androidLegacy` + `divergence`.
 */
class StartDateFallbackGoldenTest {
  private val goldenName = "GV-007-start-date-fallback.json"
  private val goldenDir = File("../shared/src/commonTest/resources/golden")
  private val hcm = "Asia/Ho_Chi_Minh"
  private val now = "NOW"
  private lateinit var previousLocale: Locale
  private lateinit var previousZone: TimeZone

  // Ghim gio Ho_Chi_Minh (SimpleDateFormat dung mui gio mac dinh); locale duoc dat theo tung ca.
  @Before fun pinZone() {
    previousLocale = Locale.getDefault()
    previousZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone(hcm))
  }

  @After fun restore() {
    Locale.setDefault(previousLocale)
    TimeZone.setDefault(previousZone)
  }

  private fun obj(vararg pairs: Pair<String, Any?>): LinkedHashMap<String, Any?> = linkedMapOf(*pairs)

  private fun midnight(y: Int, m: Int, d: Int): Long =
    LocalDate.of(y, m, d).atStartOfDay(ZoneId.of(hcm)).toInstant().toEpochMilli()

  private class SCase(val id: String, val millis: Long, val text: String, val locale: String, val expected: Any, val optional: Boolean = false)

  private val tr = "tr-TR"

  private val cases: List<SCase> = listOf(
    SCase("millis-positive-wins", 1700000000000L, "15/06/2001", tr, 1700000000000L),
    SCase("blank-gives-NOW", 0L, "   ", tr, now),
    // 31/04 khong ton tai, khong lenient: moi dinh dang deu loi -> dong ho.
    SCase("invalid-31-04-gives-NOW", 0L, "31/04/2024", tr, now),
    SCase("locale-th-TH-buddhist", 0L, "15/06/2001", "th-TH", midnight(2001, 6, 15), optional = true),
    SCase("locale-ja-JP-japanese-calendar", 0L, "15/06/2001", "ja-JP-u-ca-japanese", midnight(2001, 6, 15), optional = true),
    SCase("arabic-indic-digits", 0L, "15/06/2001", "ar-EG-u-nu-arab", midnight(2001, 6, 15), optional = true),
    SCase("valid-dd-MM-yyyy", 0L, "15/06/2001", tr, midnight(2001, 6, 15)),
    SCase("valid-dd-MM-yyyy-dash", 0L, "15-06-2001", tr, midnight(2001, 6, 15)),
    SCase("valid-iso-yyyy-MM-dd", 0L, "2001-06-15", tr, midnight(2001, 6, 15)),
    SCase("single-digit-day-month", 0L, "5/6/2001", tr, midnight(2001, 6, 5)),
    SCase("whitespace-trimmed", 0L, "  15/06/2001  ", tr, midnight(2001, 6, 15)),
    // Ghi nhan hanh vi cu: SimpleDateFormat.parse(String) chi doc phan dau, van ban thua phia sau bi bo qua.
    SCase("trailing-text-ignored", 0L, "15/06/2001 abc", tr, midnight(2001, 6, 15)),
    // millis = 0 va am khong "thang": roi ve van ban.
    SCase("zero-millis-uses-text", 0L, "15/06/2001", tr, midnight(2001, 6, 15)),
    SCase("negative-millis-uses-text", -5L, "15/06/2001", tr, midnight(2001, 6, 15)),
    SCase("leap-29-02-2000", 0L, "29/02/2000", tr, midnight(2000, 2, 29)),
    // 2001 khong nhuan: 29/02 khong ton tai -> dong ho.
    SCase("invalid-29-02-2001-gives-NOW", 0L, "29/02/2001", tr, now),
    // Bien cua `> 0`: millis = 1 thang van ban.
    SCase("millis-one-wins", 1L, "15/06/2001", tr, 1L),
    // Chung minh trim(): SimpleDateFormat bo qua dau cach/tab dau chuoi nhung KHONG bo qua xuong dong; khong trim thi moi dinh dang loi -> dong ho.
    SCase("newline-wrapped-trimmed", 0L, """
15/06/2001
""", tr, midnight(2001, 6, 15)),
  )

  // Goi ham that voi locale cua ca; ket qua nam trong [truoc, sau] thi coi la dong ho va tra "NOW".
  private fun call(c: SCase): Any {
    Locale.setDefault(Locale.forLanguageTag(c.locale))
    val before = System.currentTimeMillis()
    val r = LoveAnniversaryMilestoneScheduler.parseStartDateMillis(c.millis, c.text)
    val after = System.currentTimeMillis()
    return if (r in before..after) now else r
  }

  private fun caseMaps(): List<Map<String, Any?>> = cases.map { c ->
    val legacy = call(c)
    val diverges = c.optional && legacy != c.expected
    obj(
      "id" to c.id,
      "input" to obj("startDateMillis" to c.millis, "startDateText" to c.text, "locale" to c.locale, "zone" to hcm),
      "expected" to c.expected,
      "androidLegacy" to if (diverges) legacy else null,
      "divergence" to if (diverges) "locale-calendar-digits" else null,
      "optional" to c.optional,
    )
  }

  @Test
  fun startDateFallbackGolden() {
    val ids = cases.map { it.id }
    assertEquals(18, cases.size)
    assertEquals(ids.size, ids.toSet().size)
    for (name in listOf("millis-positive-wins", "blank-gives-NOW", "invalid-31-04-gives-NOW", "locale-th-TH-buddhist",
      "locale-ja-JP-japanese-calendar", "arabic-indic-digits")) assertTrue("Thieu ca $name", name in ids)
    // Ca khong phu thuoc host: ham that == expected.
    for (c in cases.filter { !it.optional }) assertEquals("${c.id}: ham that", c.expected, call(c))
    assertEquals(3, cases.count { it.optional })
    assertNotEquals(now, cases.first { it.id == "millis-positive-wins" }.expected)

    GoldenWriter().check(goldenName, obj("schema" to 1L, "zone" to hcm, "cases" to caseMaps()))
    val file = File(goldenDir, goldenName)
    assertTrue("Thieu golden $goldenName", file.isFile)
    val text = file.readText(Charsets.UTF_8).replace("\r\n", "\n")
    // Chi lay `id` cap ca: dong thut dung 6 dau cach trong khung cases.
    val committedIds = Regex("(?m)^ {6}\"id\": \"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList()
    assertEquals(ids, committedIds)
    assertEquals(18, committedIds.size)
    assertEquals(3, Regex("(?m)^ {6}\"optional\": true").findAll(text).count())
    assertEquals(15, Regex("(?m)^ {6}\"optional\": false").findAll(text).count())
    val divergences = Regex("(?m)^ {6}\"divergence\": \"locale-calendar-digits\"").findAll(text).count()
    val legacies = Regex("(?m)^ {6}\"androidLegacy\": (?!null)").findAll(text).count()
    assertEquals(divergences, legacies)
    assertEquals(18 - divergences, Regex("(?m)^ {6}\"divergence\": null").findAll(text).count())
  }
}
