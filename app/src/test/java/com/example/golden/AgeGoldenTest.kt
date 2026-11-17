package com.example.golden

import com.example.ui.util.ProfileUtils
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * GV-006: tuoi tu ngay sinh "dd/MM/yyyy", ghi tu oracle `LegacyAgeOracle` (V-07), doc lap voi phan thay the cua U-27.
 * `expected` = phep so (thang, ngay) tinh bang java.time (doc lap voi oracle), hoac hang viet tay cho ca nhanh du phong.
 * Ma cu so DAY_OF_YEAR nen sai mot ngay quanh 28-02 giua nam nhuan va nam thuong: khi do ghi them `androidLegacy`
 * (ket qua oracle) va `divergence` = "age-day-of-year".
 */
class AgeGoldenTest {
  private val goldenName = "GV-006-age.json"
  private val goldenDir = File("../shared/src/commonTest/resources/golden")
  private val hcm = "Asia/Ho_Chi_Minh"
  private val la = "America/Los_Angeles"
  private val kiritimati = "Pacific/Kiritimati"
  private lateinit var previousLocale: Locale

  // Ghim tr-TR: ket qua khong duoc phu thuoc locale may chay.
  @Before fun pinLocale() {
    previousLocale = Locale.getDefault()
    Locale.setDefault(Locale.forLanguageTag("tr-TR"))
  }

  @After fun restoreLocale() {
    Locale.setDefault(previousLocale)
  }

  private fun obj(vararg pairs: Pair<String, Any?>): LinkedHashMap<String, Any?> = linkedMapOf(*pairs)

  // ---- Chung minh nguon goc cua oracle ----

  // Tai lap ORIGIN_SHA256 (dong 60-81 cua ProfileUtils.kt tai a4764f8, LF, noi bang "\n", khong co "\n" cuoi):
  //   $l = git show a4764f8:app/src/main/java/com/example/ui/util/ProfileUtils.kt | Select-Object -Skip 59 -First 22
  //   $b = [Text.Encoding]::UTF8.GetBytes(($l -join "`n")); ([Security.Cryptography.SHA256]::Create().ComputeHash($b) | % { $_.ToString('x2') }) -join ''
  private val ORIGIN_SHA256 = "89de5d5ccf3fd7b223729bb754738e984195e55a1ee029ace54a095e1f318f44"

  // SHA-256 cua phan giua hai marker BEGIN/END legacy-body trong LegacyAgeOracle.kt (LF, noi bang "\n").
  private val ORACLE_BODY_SHA256 = "11537e1ac4263e6c324b6b5bf254fb653b4ca224b48fbe2c485fbf409aa113cd"

  // Thay nguoc: mau trong oracle -> van ban goc. Moi mau phai xuat hien dung 1 lan.
  private val reverseReplacements = listOf(
    "fun calculateAge(birthDateStr: String, todayMillis: Long, zone: TimeZone): Int {" to "fun calculateAge(birthDateStr: String): Int {",
    "dateFormat(zone).parse(" to "dateFormat.parse(",
    "val dob = Calendar.getInstance(zone).apply { time = date }" to "val dob = Calendar.getInstance().apply { time = date }",
    "val today = Calendar.getInstance(zone).apply { timeInMillis = todayMillis }" to "val today = Calendar.getInstance()",
    "val currentYear = Calendar.getInstance(zone).apply { timeInMillis = todayMillis }.get(Calendar.YEAR)" to
      "val currentYear = Calendar.getInstance().get(Calendar.YEAR)",
  )

  private fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

  private fun count(text: String, needle: String): Int = text.split(needle).size - 1

  @Test
  fun legacyOracleBodyHashMatches() {
    // Gradle chay test voi thu muc lam viec la `app`, giong GoldenWriter.
    val file = File("src/test/java/com/example/golden/LegacyAgeOracle.kt")
    assertTrue("Thieu ${file.path}", file.isFile)
    val lines = file.readText(Charsets.UTF_8).replace("\r\n", "\n").split("\n")
    assertTrue("Dong dau phai la copied-from: ${lines[0]}", lines[0].startsWith("// copied-from: ProfileUtils.kt:60-81 @ a4764f8"))
    val begin = lines.indexOfFirst { it.trim() == "// BEGIN legacy-body" }
    val end = lines.indexOfFirst { it.trim() == "// END legacy-body" }
    assertTrue("Thieu marker legacy-body", begin >= 0 && end > begin)
    val body = lines.subList(begin + 1, end).joinToString("\n")
    // (a) Bat moi sua doi trong oracle.
    assertEquals("Than oracle bi sua", ORACLE_BODY_SHA256, sha256(body))
    // (b) Oracle chi khac ma goc o dung nam cho da khai bao.
    var origin = body
    for ((from, to) in reverseReplacements) {
      assertEquals("Mau phai xuat hien dung 1 lan: $from", 1, count(origin, from))
      origin = origin.replace(from, to)
    }
    assertEquals("Oracle khac ma goc a4764f8 ngoai cac thay the da khai bao", ORIGIN_SHA256, sha256(origin))
  }

  // ---- Oracle khop ham that tren ngay khong o vung bien ----

  private fun ageDayGap(a: LocalDate, b: LocalDate): Int {
    // Khoang cach (thang, ngay) theo vong tron, quy 29-02 ve 28-02.
    fun ref(d: LocalDate) = LocalDate.of(2001, d.monthValue, if (d.monthValue == 2 && d.dayOfMonth == 29) 28 else d.dayOfMonth)
    val gap = Math.abs(ChronoUnit.DAYS.between(ref(a), ref(b))).toInt()
    return Math.min(gap, 365 - gap)
  }

  // ponytail: neu may chay test dung luc qua nua dem giua hai lan doc dong ho (nay va ham that) thi co the lech mot ngay; chap nhan, chay lai.
  @Test
  fun legacyOracleAgreesWithProductionOnNonEdgeDates() {
    val zone = TimeZone.getDefault()
    val now = System.currentTimeMillis()
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone.toZoneId()).toLocalDate()
    val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    var compared = 0
    for (i in 0 until 20) {
      val years = 1L + i * 3
      val off = (5 + (i * 37) % 176).toLong() * (if (i % 2 == 0) 1 else -1)
      val birthDate = today.minusYears(years).plusDays(off)
      assertTrue("Ngay sinh $birthDate qua gan hom nay $today", ageDayGap(birthDate, today) >= 4)
      val birth = birthDate.format(fmt)
      assertEquals("birth=$birth today=$today", ProfileUtils.calculateAge(birth), LegacyAgeOracle.calculateAge(birth, now, zone))
      compared++
    }
    assertEquals(20, compared)
  }

  // ---- Golden GV-006 ----

  private class AgeCase(val id: String, val birth: String, val today: String, val zone: String, val hand: Long?)

  private fun v(id: String, birth: String, today: String, zone: String = hcm, hand: Long? = null) = AgeCase(id, birth, today, zone, hand)

  private val nl = 10.toChar()

  // `hand` = hang viet tay (hanh vi Android hien tai can giu) cho ca di nhanh du phong hoac parse nam 24;
  // cac ca con lai `expected` tinh bang java.time. Ngay tinh = 15-06-2025 (nam thuong) tru khi ghi khac.
  private val cases: List<AgeCase> = listOf(
    // 14 ten co dinh.
    v("birthday-today", "15/06/2001", "2025-06-15"),
    v("birthday-tomorrow", "16/06/2001", "2025-06-15"),
    v("birthday-yesterday", "14/06/2001", "2025-06-15"),
    v("leap-birthday-on-28-02", "29/02/2000", "2025-02-28"),
    v("leap-birthday-on-29-02-2024", "29/02/2000", "2024-02-29"),
    v("born-01-03-2000-evaluated-01-03-2025", "01/03/2000", "2025-03-01"),
    v("born-01-03-2001-evaluated-29-02-2024", "01/03/2001", "2024-02-29"),
    // ISO bi doc nhu "dd-MM-yyyy" nhanh du phong: phan "nam" la 15 nen ra 2025 - 15 = 2010 (FP-102, chi ghi nhan).
    v("iso-garbage-fallback", "2000-01-15", "2025-06-15", hand = 2010L),
    // Nam khong phai so -> nhanh du phong dung 2000: 2025 - 2000 = 25.
    v("non-numeric-year-gives-2000", "15/06/abcd", "2025-06-15", hand = 25L),
    v("future-birth", "15/06/2030", "2025-06-15"),
    v("blank", "", "2025-06-15"),
    // "24" doc la nam 24 sau Cong nguyen (khong phai 2024): 2025 - 24 = 2001. Chon thang 1 de khoi lech lich Julian.
    v("two-digit-year", "15/01/24", "2025-06-15", hand = 2001L),
    v("age-zero", "10/03/2025", "2025-06-15"),
    v("age-over-100", "15/06/1921", "2025-06-15"),

    // Ca them, ghim cac huong lech DAY_OF_YEAR.
    // Sinh nam nhuan sau 29-02, tinh dung ngay sinh nhat o nam thuong: ma cu thieu 1.
    v("leap-born-on-birthday-in-non-leap-year", "15/06/2000", "2025-06-15"),
    // Sinh nam thuong, tinh ngay lien truoc sinh nhat o nam nhuan sau 29-02: ma cu du 1.
    v("non-leap-born-day-before-birthday-in-leap-year", "15/06/2001", "2024-06-14"),
    v("born-29-02-2000-evaluated-28-02-2025", "29/02/2000", "2025-02-28"),
    v("born-29-02-2000-evaluated-01-03-2025", "29/02/2000", "2025-03-01"),
    // Sinh truoc 29-02 cung nam nhuan: khong lech.
    v("born-before-feb-29-in-leap-year-agrees", "15/01/2000", "2025-01-15"),
    // 15-06-2025 12:00 o Los_Angeles la 16-06 o Ho_Chi_Minh: neu zone khong duoc dung thi ra 24 thay vi 23.
    v("zone-los-angeles-uses-local-date", "16/06/2001", "2025-06-15", zone = la),
    // "\n...\n" chi qua duoc nho trim(): SimpleDateFormat tu bo dau cach va tab dau chuoi nhung khong bo xuong dong.
    v("birth-wrapped-in-newlines", "${nl}15/06/2001${nl}", "2025-06-15"),
    // Bo trim() thi parse loi, nhanh du phong van ra 2025 - 2001 = 24: ca tren khong phan biet duoc; truoc sinh nhat thi dung la 23.
    v("birth-newlines-before-birthday", "${nl}16/06/2001${nl}", "2025-06-15"),
    // 12:00 15-06-2025 o Los_Angeles: neu tinh "hom nay" theo gio Ho_Chi_Minh thi ra 14-06 va thieu 1 tuoi.
    v("zone-los-angeles-birthday-today", "15/06/2001", "2025-06-15", zone = la),
    // UTC+14: nua dem o day la ngay truoc o may UTC+7, nen ngay sinh doc sai zone se lech mot ngay.
    v("zone-kiritimati-dob-day-boundary", "16/06/2001", "2025-06-15", zone = kiritimati),

    // Nhanh du phong (parse that bai): ghi nhan hanh vi cu, ke ca khi vo nghia (nam - nam sinh, khong xet thang ngay).
    v("garbage-four-parts-gives-0", "ab/cd/ef/gh", "2025-06-15", hand = 0L),
    v("two-parts-gives-0", "15/06", "2025-06-15", hand = 0L),
    // "16.06.2001" va "16-06-2001" khong khop "dd/MM/yyyy": du phong cho 2025 - 2001 = 24 du chua toi sinh nhat.
    v("dot-separated-fallback", "16.06.2001", "2025-06-15", hand = 24L),
    v("dash-separated-fallback", "16-06-2001", "2025-06-15", hand = 24L),
    v("dash-future-year-fallback", "15-06-2030", "2025-06-15", hand = 0L),
    v("fallback-input-wrapped-in-newlines", "${nl}16-06-2001${nl}", "2025-06-15", hand = 24L),
    // 31/06 khong ton tai: khong lenient thi loi roi du phong (24); lenient se doc thanh 01/07 va ra 23.
    v("invalid-day-31-06-falls-back", "31/06/2001", "2025-06-15", hand = 24L),
    // Nam hien tai cua nhanh du phong phai tinh theo zone: 12:00 31-12-2025 o Los_Angeles da la nam 2026 o may UTC+7 (ra 25, khong phai 24).
    v("fallback-year-uses-zone-los-angeles-new-year-eve", "16-06-2001", "2025-12-31", zone = la, hand = 24L),
    // 12:00 01-01-2025 o Kiritimati (UTC+14) con la nam 2024 o may UTC (ra 23, khong phai 24); cap voi ca tren de bat ca may UTC va UTC+7.
    v("fallback-year-uses-zone-kiritimati-new-year-day", "16-06-2001", "2025-01-01", zone = kiritimati, hand = 24L),
  )

  private val fixedNames = listOf(
    "birthday-today",
    "birthday-tomorrow",
    "birthday-yesterday",
    "leap-birthday-on-28-02",
    "leap-birthday-on-29-02-2024",
    "born-01-03-2000-evaluated-01-03-2025",
    "born-01-03-2001-evaluated-29-02-2024",
    "iso-garbage-fallback",
    "non-numeric-year-gives-2000",
    "future-birth",
    "blank",
    "two-digit-year",
    "age-zero",
    "age-over-100",
  )

  // Ca ma oracle (DAY_OF_YEAR) phai khac phep so (thang, ngay), tinh tay.
  private val divergentIds = setOf(
    "born-01-03-2000-evaluated-01-03-2025",
    "born-01-03-2001-evaluated-29-02-2024",
    "leap-born-on-birthday-in-non-leap-year",
    "non-leap-born-day-before-birthday-in-leap-year",
  )

  private val strictFmt = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

  // Phep so (thang, ngay) bang java.time, doc lap voi oracle.
  private fun expectedAge(c: AgeCase): Long {
    c.hand?.let { return it }
    if (c.birth.isBlank()) return 0L
    val b = LocalDate.parse(c.birth.trim(), strictFmt)
    val t = LocalDate.parse(c.today)
    val beforeBirthday = t.monthValue < b.monthValue || (t.monthValue == b.monthValue && t.dayOfMonth < b.dayOfMonth)
    return (t.year - b.year - (if (beforeBirthday) 1 else 0)).coerceAtLeast(0).toLong()
  }

  // Dung 12:00 trua dia phuong cua zone de tranh khoang DST.
  private fun todayMillis(c: AgeCase): Long =
    LocalDate.parse(c.today).atTime(12, 0).atZone(ZoneId.of(c.zone)).toInstant().toEpochMilli()

  private fun legacyAge(c: AgeCase): Long =
    LegacyAgeOracle.calculateAge(c.birth, todayMillis(c), TimeZone.getTimeZone(c.zone)).toLong()

  private fun caseMaps(): List<Map<String, Any?>> = cases.map { c ->
    val expected = expectedAge(c)
    val legacy = legacyAge(c)
    val diverges = expected != legacy
    obj(
      "id" to c.id,
      "input" to obj("birth" to c.birth, "today" to c.today, "zone" to c.zone),
      "expected" to expected,
      "androidLegacy" to if (diverges) legacy else null,
      "divergence" to if (diverges) "age-day-of-year" else null,
      "optional" to false,
    )
  }

  @Test
  fun ageGolden() {
    val maps = caseMaps()
    val ids = cases.map { it.id }
    assertEquals(ids.size, ids.toSet().size)
    for (name in fixedNames) assertTrue("Thieu ca $name", name in ids)
    assertEquals(14, fixedNames.size)
    assertTrue("Can it nhat 6 ca phu", ids.size >= 14 + 6)
    for (m in maps) {
      val id = m["id"]
      if (m["divergence"] != null) {
        assertEquals("age-day-of-year", m["divergence"])
        assertNotNull("$id: divergence can androidLegacy", m["androidLegacy"])
        assertNotEquals("$id: androidLegacy phai khac expected", m["expected"], m["androidLegacy"])
      } else {
        assertEquals("$id: androidLegacy phai null khi khong lech", null, m["androidLegacy"])
      }
    }
    // Moi ca khong lech: expected == oracle.
    for (c in cases) if (c.id !in divergentIds) assertEquals("${c.id}: expected == oracle", expectedAge(c), legacyAge(c))
    val diverging = maps.filter { it["divergence"] != null }.map { it["id"] as String }.toSet()
    assertEquals(divergentIds, diverging)
    // Hai ca bat buoc co gia tri tinh tay.
    val byId = cases.associateBy { it.id }
    assertEquals(25L, expectedAge(byId.getValue("born-01-03-2000-evaluated-01-03-2025")))
    assertEquals(24L, legacyAge(byId.getValue("born-01-03-2000-evaluated-01-03-2025")))
    assertEquals(22L, expectedAge(byId.getValue("born-01-03-2001-evaluated-29-02-2024")))
    assertEquals(23L, legacyAge(byId.getValue("born-01-03-2001-evaluated-29-02-2024")))

    // Ghi (GOLDEN_UPDATE=1) hoac kiem file da commit, roi doc lai danh sach id.
    GoldenWriter().check(goldenName, obj("schema" to 1L, "zone" to hcm, "cases" to maps))
    val file = File(goldenDir, goldenName)
    assertTrue("Thieu golden $goldenName", file.isFile)
    val text = file.readText(Charsets.UTF_8).replace("\r\n", "\n")
    // Chi lay `id` cap ca: dong thut dung 6 dau cach trong khung cases.
    val committedIds = Regex("(?m)^ {6}\"id\": \"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList()
    assertEquals(ids, committedIds)
    for (name in fixedNames) assertTrue("Golden thieu ca $name", name in committedIds)
    val divergences = Regex("(?m)^ {6}\"divergence\": \"age-day-of-year\"").findAll(text).count()
    val legacies = Regex("(?m)^ {6}\"androidLegacy\": \\d+").findAll(text).count()
    val nullDivergences = Regex("(?m)^ {6}\"divergence\": null").findAll(text).count()
    val nullLegacies = Regex("(?m)^ {6}\"androidLegacy\": null").findAll(text).count()
    assertEquals(divergences, legacies)
    assertEquals(nullDivergences, nullLegacies)
    assertEquals(committedIds.size, divergences + nullDivergences)
    assertEquals(divergentIds.size, divergences)
  }
}
