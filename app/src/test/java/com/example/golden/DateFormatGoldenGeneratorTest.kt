package com.example.golden

import com.example.ui.components.DatePickerUtils
import com.example.ui.util.ProfileUtils
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.format.SignStyle
import java.time.temporal.ChronoField
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * GV-002: dinh dang va doc ngay dang chu, ghi tu oracle `LegacyDateOracle` (D-05a), doc lap voi `:shared`.
 *
 * Hinh dang mot ca: `input` = {op, convention, locale (the BCP 47), zone, text, millis}; moi ca chi co mot trong `text`
 * hoac `millis` (roundtrip va parse dung `text`, format dung `millis`), cai con lai la null.
 * `convention` = "device" (ProfileUtils: mot mau dd/MM/yyyy, mui gio may = `zone`) hoac "utc" (DatePickerUtils: bon mau, UTC).
 * Hinh dang `expected` (va `androidLegacy`) theo `op`:
 * - parse: {"millis": Long hoac null}. null = bi tu choi. Mui gio: `zone` (device) hoac UTC (utc); nua dem cua ngay do.
 * - format: {"text": String}. Ngay lich cua `millis` o mui gio tren, dang dd/MM/yyyy.
 * - roundtrip: {"text": String hoac null}. parse roi format; null khi parse bi tu choi.
 *
 * DECIDED: `expected` luon la lich Gregory proleptic voi chu so ASCII bat ke locale (chuoi nay luu Room, gui Firestore,
 * hai may cua mot cap doc lai). Nen `expected` duoc tinh bang `java.time` voi `Locale.ROOT`, khong goi oracle. Oracle chi cho
 * `androidLegacy` (ket qua tho tren JVM chu theo `Locale.getDefault()` cua ca). Khac nhau thi ghi them `androidLegacy` va
 * `divergence` ("locale-calendar-digits" khi locale la th-TH, ja-JP-u-ca-japanese, ar-EG-u-nu-arab; "julian-gregorian-cutover"
 * khi ngay truoc 15-10-1582 hoac nam it chu so, vi SimpleDateFormat dung lich hon hop Julian-Gregory). Ba locale tren ghi
 * `optional` true: hanh vi that cua SimpleDateFormat tren thiet bi Android la UNVERIFIED (chua chay tren may that).
 * Ca 01/01/1900 o Asia/Ho_Chi_Minh chi ghi chuoi khu hoi, khong ghi mili giay (truoc 1906 mui gio nay dung gio trung binh dia phuong).
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DateFormatGoldenGeneratorTest {
  private val goldenDir = File("../shared/src/commonTest/resources/golden")
  private val goldenName = "GV-002-date-format.json"
  private lateinit var previousLocale: Locale
  private lateinit var previousZone: TimeZone

  // Ghim tr-TR va ghi nho mui gio may: ket qua khong duoc phu thuoc may chay.
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

  private fun <T> inEnv(locale: String, zone: String, block: () -> T): T {
    val beforeLocale = Locale.getDefault()
    val beforeZone = TimeZone.getDefault()
    try {
      Locale.setDefault(Locale.forLanguageTag(locale))
      TimeZone.setDefault(TimeZone.getTimeZone(zone))
      return block()
    } finally {
      Locale.setDefault(beforeLocale)
      TimeZone.setDefault(beforeZone)
    }
  }

  private class DCase(
    val id: String,
    val op: String,
    val convention: String,
    val text: String?,
    val millis: Long?,
    val locale: String,
    val zone: String,
  )

  private val hcm = "Asia/Ho_Chi_Minh"
  private val la = "America/Los_Angeles"
  private val akl = "Pacific/Auckland"
  private val dev = "device"
  private val utc = "utc"
  private val vi = "vi-VN"

  // Ba locale ma lich hoac chu so cua JVM chu khac Gregory ASCII.
  private val hostDependentLocales = setOf("th-TH", "ja-JP-u-ca-japanese", "ar-EG-u-nu-arab")

  private fun p(id: String, conv: String, text: String, locale: String = vi, zone: String = hcm) =
    DCase(id, "parse", conv, text, null, locale, zone)

  private fun r(id: String, conv: String, text: String, locale: String = vi, zone: String = hcm) =
    DCase(id, "roundtrip", conv, text, null, locale, zone)

  private fun f(id: String, conv: String, millis: Long, locale: String = vi, zone: String = hcm) =
    DCase(id, "format", conv, null, millis, locale, zone)

  private fun midnight(y: Int, mo: Int, d: Int, zone: String): Long =
    LocalDate.of(y, mo, d).atStartOfDay(ZoneId.of(zone)).toInstant().toEpochMilli()

  private val jun15Hcm = midnight(2024, 6, 15, hcm)
  private val lateEvening = Instant.parse("2024-06-15T23:30:00Z").toEpochMilli()

  private val cases: List<DCase> = listOf(
    // 14 ten P8.
    p("valid-dd-MM-yyyy", dev, "15/06/2024"),
    p("leap-day-2024", dev, "29/02/2024"),
    p("non-leap-29-02-2023", dev, "29/02/2023"),
    p("day-31-in-30-day-month", dev, "31/04/2024"),
    p("month-13", dev, "15/13/2024"),
    p("single-digit-d-M-yyyy", utc, "5/3/2024"),
    p("surrounding-spaces-trim", dev, "  15/06/2024  "),
    p("iso-yyyy-MM-dd", utc, "2024-06-15"),
    p("two-digit-year-24", utc, "15/06/24"),
    p("year-1582-cutover", utc, "04/10/1582"),
    f("locale-th-TH-buddhist", dev, jun15Hcm, "th-TH"),
    f("locale-ja-JP-japanese-calendar", dev, jun15Hcm, "ja-JP-u-ca-japanese"),
    p("utc-midnight-vs-device-zone-shift", utc, "15/06/2024", zone = hcm),
    r("format-roundtrip", dev, "29/02/2024"),

    // Them: 1582.
    p("year-1582-cutover-10-10", utc, "10/10/1582"),
    p("year-1582-cutover-15-10", utc, "15/10/1582"),

    // Them: locale Thai, Nhat, Ai Cap.
    p("locale-th-TH-buddhist-parse", dev, "15/06/2024", "th-TH"),
    p("locale-ja-JP-japanese-calendar-parse", dev, "15/06/2024", "ja-JP-u-ca-japanese"),
    f("locale-ar-EG-arabic-indic-digits", dev, jun15Hcm, "ar-EG-u-nu-arab"),
    p("locale-ar-EG-arabic-indic-digits-parse", dev, "15/06/2024", "ar-EG-u-nu-arab"),
    p("locale-ja-JP-gregorian-agrees", dev, "15/06/2024", "ja-JP"),
    p("locale-en-US-valid", dev, "15/06/2024", "en-US"),
    p("locale-tr-TR-valid", dev, "15/06/2024", "tr-TR"),

    // Them: cung chuoi, utc so voi device o ba mui gio.
    p("utc-midnight-vs-device-zone-shift-device-hcm", dev, "15/06/2024", zone = hcm),
    p("utc-midnight-vs-device-zone-shift-device-los-angeles", dev, "15/06/2024", zone = la),
    p("utc-midnight-vs-device-zone-shift-device-auckland", dev, "15/06/2024", zone = akl),
    p("utc-midnight-vs-device-zone-shift-utc-los-angeles", utc, "15/06/2024", zone = la),
    p("utc-midnight-vs-device-zone-shift-utc-auckland", utc, "15/06/2024", zone = akl),

    // Them: format 23:30Z, ngay lich doi theo mui gio.
    f("format-late-evening-device-hcm", dev, lateEvening, zone = hcm),
    f("format-late-evening-device-los-angeles", dev, lateEvening, zone = la),
    f("format-late-evening-device-auckland", dev, lateEvening, zone = akl),
    f("format-late-evening-utc", utc, lateEvening, zone = hcm),

    // Them: 1900, 2000, quanh moc 0.
    p("year-1900-01-01", utc, "01/01/1900"),
    p("year-1900-02-29-rejected", utc, "29/02/1900"),
    p("year-2000-02-29-accepted", utc, "29/02/2000"),
    p("epoch-eve-31-12-1969", utc, "31/12/1969"),
    p("epoch-01-01-1970", utc, "01/01/1970"),
    p("epoch-01-01-1970-device-utc-zone", dev, "01/01/1970", zone = "UTC"),
    f("utc-format-epoch-0", utc, 0L),
    f("utc-format-minus-1ms", utc, -1L),
    r("year-1900-hcm-lmt-roundtrip", dev, "01/01/1900", zone = hcm),

    // Them: hinh dang chuoi vao.
    p("device-single-digit-accepted", dev, "5/3/2024"),
    p("device-rejects-iso", dev, "2024-06-15"),
    p("device-rejects-blank", dev, ""),
    p("utc-dash-dd-MM-yyyy", utc, "15-06-2024"),
    // SimpleDateFormat tu bo qua dau cach/tab dau chuoi nhung khong bo qua xuong dong: chi trim() moi cuu duoc ca nay.
    p("surrounding-newline-trim-device", dev, "\n15/06/2024\n"),
    p("surrounding-newline-trim-utc", utc, "\n15/06/2024\n"),
    p("iso-invalid-day-30-feb", utc, "2024-02-30"),
    p("locale-th-TH-buddhist-parse-utc", utc, "15/06/2024", "th-TH"),
    r("roundtrip-single-digit-normalised", utc, "5/3/2024"),
    r("roundtrip-invalid-gives-null", dev, "29/02/2023"),
    // Nua dem khong ton tai (DST bat dau 00:00): ca hai ben deu doi sang 01:00 thay vi bao loi.
    p("device-dst-gap-midnight-sao-paulo", dev, "04/11/2018", zone = "America/Sao_Paulo"),
    r("utc-roundtrip-29-02-2024", utc, "29/02/2024"),
  )

  private val p8Names = listOf(
    "valid-dd-MM-yyyy",
    "leap-day-2024",
    "non-leap-29-02-2023",
    "day-31-in-30-day-month",
    "month-13",
    "single-digit-d-M-yyyy",
    "surrounding-spaces-trim",
    "iso-yyyy-MM-dd",
    "two-digit-year-24",
    "year-1582-cutover",
    "locale-th-TH-buddhist",
    "locale-ja-JP-japanese-calendar",
    "utc-midnight-vs-device-zone-shift",
    "format-roundtrip",
  )

  private val cutoverUtcMillis = LocalDate.of(1582, 10, 15).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

  // ---- expected: java.time, Locale.ROOT, khong doc Locale/TimeZone mac dinh ----

  private fun dmy(sep: Char): DateTimeFormatter = DateTimeFormatterBuilder()
    .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE).appendLiteral(sep)
    .appendValue(ChronoField.MONTH_OF_YEAR, 1, 2, SignStyle.NOT_NEGATIVE).appendLiteral(sep)
    .appendValue(ChronoField.YEAR, 1, 9, SignStyle.NOT_NEGATIVE)
    .toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

  // Cac mau duoc dung lai moi lan goi (getter), de test doc lap locale thu duoc ca luc dung formatter.
  private val ymdDash: DateTimeFormatter get() = DateTimeFormatterBuilder()
    .appendValue(ChronoField.YEAR, 1, 9, SignStyle.NOT_NEGATIVE).appendLiteral('-')
    .appendValue(ChronoField.MONTH_OF_YEAR, 1, 2, SignStyle.NOT_NEGATIVE).appendLiteral('-')
    .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
    .toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

  private val dmySlash get() = dmy('/')
  private val dmyDash get() = dmy('-')
  private val outFormat: DateTimeFormatter get() = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT)

  // Mau cua mot convention, theo thu tu ma cu thu (d/M/yyyy trung dd/MM/yyyy vi so chu so tuy y).
  private fun parsers(conv: String): List<DateTimeFormatter> =
    if (conv == dev) listOf(dmySlash) else listOf(dmySlash, dmyDash, ymdDash)

  private fun zoneOf(c: DCase): ZoneId = if (c.convention == dev) ZoneId.of(c.zone) else ZoneOffset.UTC

  private fun parsedDate(c: DCase): LocalDate? {
    val text = c.text!!.trim()
    for (fmt in parsers(c.convention)) {
      try {
        return LocalDate.parse(text, fmt)
      } catch (_: Exception) {
      }
    }
    return null
  }

  private fun expectedValue(c: DCase): Any? = when (c.op) {
    "parse" -> parsedDate(c)?.atStartOfDay(zoneOf(c))?.toInstant()?.toEpochMilli()
    "format" -> outFormat.format(Instant.ofEpochMilli(c.millis!!).atZone(zoneOf(c)))
    else -> parsedDate(c)?.let { outFormat.format(it) }
  }

  // ---- androidLegacy: oracle tren JVM chu, voi locale va zone cua ca ----

  private val noValue = Long.MIN_VALUE

  private fun legacyParse(c: DCase): Long? =
    if (c.convention == dev) LegacyDateOracle.parseDateToMillis(c.text!!, noValue).takeIf { it != noValue }
    else LegacyDateOracle.parseDateToUtcMillis(c.text!!)

  private fun legacyFormat(c: DCase, millis: Long): String =
    if (c.convention == dev) LegacyDateOracle.formatDate(millis) else LegacyDateOracle.formatUtcMillisToDate(millis)

  private fun legacyValue(c: DCase): Any? = inEnv(c.locale, c.zone) {
    when (c.op) {
      "parse" -> legacyParse(c)
      "format" -> legacyFormat(c, c.millis!!)
      else -> legacyParse(c)?.let { legacyFormat(c, it) }
    }
  }

  // ---- ham that (ProfileUtils, DatePickerUtils) ----

  private fun realParse(c: DCase): Long? {
    val before = System.currentTimeMillis()
    val real = if (c.convention == dev) ProfileUtils.parseDateToMillis(c.text!!) else DatePickerUtils.parseDateToUtcMillis(c.text!!)
    val after = System.currentTimeMillis()
    // Ham device tra dong ho that khi loi.
    return if (real != null && real !in before..after) real else null
  }

  private fun realFormat(c: DCase, millis: Long): String =
    if (c.convention == dev) ProfileUtils.formatDate(millis) else DatePickerUtils.formatUtcMillisToDate(millis)

  private fun realValue(c: DCase): Any? = inEnv(c.locale, c.zone) {
    when (c.op) {
      "parse" -> realParse(c)
      "format" -> realFormat(c, c.millis!!)
      else -> realParse(c)?.let { realFormat(c, it) }
    }
  }

  private fun shape(c: DCase, value: Any?): Map<String, Any?> =
    if (c.op == "parse") obj("millis" to value) else obj("text" to value)

  private fun divergenceOf(c: DCase): String =
    if (c.locale in hostDependentLocales) "locale-calendar-digits" else "julian-gregorian-cutover"

  private fun caseMaps(): List<Map<String, Any?>> = cases.map { c ->
    val expected = expectedValue(c)
    val legacy = legacyValue(c)
    val diverges = expected != legacy
    obj(
      "id" to c.id,
      "input" to obj(
        "op" to c.op,
        "convention" to c.convention,
        "locale" to c.locale,
        "zone" to c.zone,
        "text" to c.text,
        "millis" to c.millis,
      ),
      "expected" to shape(c, expected),
      "androidLegacy" to if (diverges) shape(c, legacy) else null,
      "divergence" to if (diverges) divergenceOf(c) else null,
      "optional" to (c.locale in hostDependentLocales),
    )
  }

  private fun writeOrVerifyGolden() {
    GoldenWriter().check(goldenName, obj("schema" to 1L, "zone" to hcm, "cases" to caseMaps()))
  }

  // Test doc file da commit can file da duoc ghi: khi GOLDEN_UPDATE=1, ghi truoc de mot luot chay la du.
  private fun committedLines(): List<String> {
    if (System.getenv("GOLDEN_UPDATE") == "1") writeOrVerifyGolden()
    val file = File(goldenDir, goldenName)
    if (!file.isFile) throw AssertionError("Thieu golden $goldenName tai ${file.path} (chay voi GOLDEN_UPDATE=1)")
    return file.readText(Charsets.UTF_8).replace("\r\n", "\n").split("\n")
  }

  // Khoa cap ca nam o muc thut dung 6 dau cach trong khung cases.
  private fun caseKeyValues(key: String): List<String> {
    val prefix = "      \"$key\": "
    return committedLines().filter { it.startsWith(prefix) }.map { it.removePrefix(prefix).removeSuffix(",") }
  }

  private fun committedIds(): List<String> = caseKeyValues("id").map { it.removeSurrounding("\"") }

  @Test
  fun dateFormatGolden_matchesCommitted() {
    writeOrVerifyGolden()
  }

  @Test
  fun dateFormatGolden_coversEveryP8CaseName() {
    assertEquals(14, p8Names.size)
    val ids = committedIds()
    assertEquals(ids.size, ids.toSet().size)
    for (name in p8Names) assertTrue("Thieu ca P8 $name trong file golden", name in ids)
    assertEquals(cases.map { it.id }, ids)
  }

  @Test
  fun dateFormatGolden_hasAtLeast30Cases() {
    val count = committedIds().size
    assertTrue("Chi co $count ca", count >= 30)
  }

  @Test
  fun dateFormatGolden_everyDivergenceHasAndroidLegacy() {
    val allowed = setOf("locale-calendar-digits", "julian-gregorian-cutover")
    val maps = caseMaps()
    for (m in maps) {
      val id = m["id"]
      assertEquals("$id: divergence va androidLegacy phai di cung nhau", m["divergence"] != null, m["androidLegacy"] != null)
      if (m["divergence"] != null) {
        assertTrue("$id: divergence la ${m["divergence"]}", m["divergence"] in allowed)
        assertNotEquals("$id: androidLegacy phai khac expected", m["expected"], m["androidLegacy"])
      }
    }
    // Julian-gregorian chi cho ngay truoc 15-10-1582; mot ca lech khac (vd oracle doi isLenient) khong duoc mang nhan nay.
    for ((c, m) in cases.zip(maps)) {
      if (m["divergence"] != "julian-gregorian-cutover") continue
      val ref = c.millis ?: (expectedValue(c) as Long?)
      assertNotNull("${c.id}: julian nhung khong co moc thoi gian", ref)
      assertTrue("${c.id}: julian nhung ngay khong truoc 15-10-1582", ref!! < cutoverUtcMillis)
    }
    // Ca hai ben khop ca tren file da commit.
    val divergences = caseKeyValues("divergence").count { it != "null" }
    val legacies = caseKeyValues("androidLegacy").count { it != "null" }
    assertEquals(divergences, legacies)
    assertEquals(maps.count { it["divergence"] != null }, divergences)
    assertEquals(cases.size, caseKeyValues("divergence").size)
    // Ba ca P8 phai thuc su lech: nhan Julian va hai locale, tinh tay.
    val byId = cases.zip(maps).associate { (c, m) -> c.id to m }
    assertEquals("julian-gregorian-cutover", byId.getValue("year-1582-cutover")["divergence"])
    assertEquals("julian-gregorian-cutover", byId.getValue("year-1582-cutover-10-10")["divergence"])
    assertEquals("locale-calendar-digits", byId.getValue("locale-th-TH-buddhist")["divergence"])
    assertEquals("locale-calendar-digits", byId.getValue("locale-ja-JP-japanese-calendar")["divergence"])
    assertEquals(null, byId.getValue("year-1582-cutover-15-10")["divergence"])
    assertEquals(null, byId.getValue("non-leap-29-02-2023")["divergence"])
  }

  // expected phai bang nhau du Locale/TimeZone may la gi (th-TH, ja-JP lich Nhat, ar-EG chu so Ai Cap...).
  @Test
  fun dateFormatGolden_expectedIsLocaleIndependent() {
    val baseline = cases.map { shape(it, expectedValue(it)) }
    val locales = listOf("th-TH", "ja-JP-u-ca-japanese", "ar-EG-u-nu-arab", "tr-TR", "und")
    val zones = listOf(la, akl, "UTC", hcm)
    for (locale in locales) {
      for (zone in zones) {
        inEnv(locale, zone) {
          cases.forEachIndexed { i, c ->
            assertEquals("${c.id} expected doi theo $locale / $zone", baseline[i], shape(c, expectedValue(c)))
          }
        }
      }
    }
    // Chinh file golden cung dung baseline (khong phai ket qua oracle).
    assertEquals(baseline, caseMaps().map { it["expected"] })
  }

  // Cung chuoi, device (mui gio may) khac utc: lech dung bang offset cua mui gio tai nua dem UTC.
  @Test
  fun dateFormatGolden_deviceAndUtcDifferAroundMidnight() {
    val byId = cases.zip(caseMaps()).associate { (c, m) -> c.id to m }
    fun millis(id: String) = (byId.getValue(id)["expected"] as Map<*, *>)["millis"] as Long
    val utcMillis = millis("utc-midnight-vs-device-zone-shift")
    assertEquals(millis("utc-midnight-vs-device-zone-shift-utc-los-angeles"), utcMillis)
    assertEquals(millis("utc-midnight-vs-device-zone-shift-utc-auckland"), utcMillis)
    assertEquals(LocalDate.of(2024, 6, 15).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), utcMillis)
    val devices = mapOf(
      "utc-midnight-vs-device-zone-shift-device-hcm" to hcm,
      "utc-midnight-vs-device-zone-shift-device-los-angeles" to la,
      "utc-midnight-vs-device-zone-shift-device-auckland" to akl,
    )
    for ((id, zone) in devices) {
      val device = millis(id)
      assertNotEquals("$id phai khac utc", utcMillis, device)
      val offsetMillis = ZoneId.of(zone).rules.getOffset(Instant.ofEpochMilli(utcMillis)).totalSeconds * 1000L
      assertEquals("$id lech dung offset $zone", utcMillis - offsetMillis, device)
    }
    // Tinh tay: HCM +7 => 17:00Z hom truoc; LA -7 (PDT) => 07:00Z; Auckland +12 (NZST) => 12:00Z hom truoc.
    assertEquals(utcMillis - 7L * 3_600_000, millis("utc-midnight-vs-device-zone-shift-device-hcm"))
    assertEquals(utcMillis + 7L * 3_600_000, millis("utc-midnight-vs-device-zone-shift-device-los-angeles"))
    assertEquals(utcMillis - 12L * 3_600_000, millis("utc-midnight-vs-device-zone-shift-device-auckland"))
  }

  // Ca khong lech va khong optional: ham that (ProfileUtils/DatePickerUtils) phai tra dung expected.
  // Khi P4 doi ham that sang :shared van xanh vi chi so cac ca khong khac biet.
  @Test
  fun dateFormatGolden_oracleMatchesRealUtilsWhereNoDivergence() {
    val maps = caseMaps()
    var compared = 0
    for ((c, m) in cases.zip(maps)) {
      if (m["divergence"] != null || m["optional"] == true) continue
      assertEquals("${c.id}: ham that khac expected", shape(c, expectedValue(c)), shape(c, realValue(c)))
      compared++
    }
    assertTrue("Qua it ca so voi ham that: $compared", compared >= 25)
  }
}
