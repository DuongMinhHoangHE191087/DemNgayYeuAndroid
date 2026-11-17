package com.example.golden

import com.example.ui.util.ProfileUtils
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GV-005: cung hoang dao tu ngay sinh "dd/MM/yyyy" (`calculateZodiac`) va tu (ngay, thang) (`calculateZodiacFromDayMonth`).
 * Cac ca hop le doi chieu voi bang moc ngay viet tay (doc lap voi ma that). Cac ca la (iso, nbsp, chu so unicode,
 * thang 0/13, ngay 0/32 ...) ghi nhan hanh vi cu, nen `expected` la hang viet tay ghim hanh vi do.
 * Khong ca nao lech nen androidLegacy/divergence luon null, optional luon false.
 */
class ZodiacGoldenTest {
  private val goldenName = "GV-005-zodiac.json"
  private val goldenDir = File("../shared/src/commonTest/resources/golden")

  private fun obj(vararg pairs: Pair<String, Any?>): LinkedHashMap<String, Any?> = linkedMapOf(*pairs)

  // Ten cung viet tay (Unicode dung escape de file nguon chi con ASCII).
  private val unknown = "Ch\u01B0a r\u00F5" to "\u2728"
  private val capricorn = "Ma K\u1EBFt" to "\u2651"
  private val aquarius = "B\u1EA3o B\u00ECnh" to "\u2652"
  private val pisces = "Song Ng\u01B0" to "\u2653"
  private val aries = "B\u1EA1ch D\u01B0\u01A1ng" to "\u2648"
  private val taurus = "Kim Ng\u01B0u" to "\u2649"
  private val gemini = "Song T\u1EED" to "\u264A"
  private val cancer = "C\u1EF1 Gi\u1EA3i" to "\u264B"
  private val leo = "S\u01B0 T\u1EED" to "\u264C"
  private val virgo = "X\u1EED N\u1EEF" to "\u264D"
  private val libra = "Thi\u00EAn B\u00ECnh" to "\u264E"
  private val scorpio = "B\u1ECD C\u1EA1p" to "\u264F"
  private val sagittarius = "Nh\u00E2n M\u00E3" to "\u2650"

  private class Start(val month: Int, val day: Int, val slug: String, val sign: Pair<String, String>)

  // Ngay bat dau moi cung, theo thu tu thang; truoc 20-01 la Ma Ket (cung cuoi nam).
  private val starts = listOf(
    Start(1, 20, "aquarius", aquarius),
    Start(2, 19, "pisces", pisces),
    Start(3, 21, "aries", aries),
    Start(4, 20, "taurus", taurus),
    Start(5, 21, "gemini", gemini),
    Start(6, 22, "cancer", cancer),
    Start(7, 23, "leo", leo),
    Start(8, 23, "virgo", virgo),
    Start(9, 23, "libra", libra),
    Start(10, 24, "scorpio", scorpio),
    Start(11, 23, "sagittarius", sagittarius),
    Start(12, 22, "capricorn", capricorn),
  )

  // Bang tay cho ngay hop le: cung la cung bat dau muon nhat khong qua (thang, ngay).
  private fun handSign(day: Int, month: Int): Pair<String, String> {
    var current = capricorn
    for (s in starts) if (month > s.month || (month == s.month && day >= s.day)) current = s.sign
    return current
  }

  private class ZCase(
    val id: String,
    val fn: String,
    val birth: String?,
    val day: Int?,
    val month: Int?,
    val expected: Pair<String, String>,
  )

  private fun birthCase(id: String, birth: String, expected: Pair<String, String>) = ZCase(id, "calculateZodiac", birth, null, null, expected)

  private fun dd(n: Int) = n.toString().padStart(2, '0')

  private val cases: List<ZCase> = buildList {
    // 24 ca ngay moc: ngay cuoi cua cung truoc va ngay dau cua cung moi, theo nam 2000.
    starts.forEachIndexed { i, s ->
      val prev = starts[(i + 11) % 12]
      add(birthCase("${prev.slug}-last-${dd(s.day - 1)}-${dd(s.month)}", "${dd(s.day - 1)}/${dd(s.month)}/2000", prev.sign))
      add(birthCase("${s.slug}-first-${dd(s.day)}-${dd(s.month)}", "${dd(s.day)}/${dd(s.month)}/2000", s.sign))
    }
    add(birthCase("leap-29-02", "29/02/2000", handSign(29, 2)))
    // Ghi nhan hanh vi cu: thang ngoai 1..12 -> "Chua ro"; ngay 0 va 32 khong bi chan, chi so voi moc.
    add(birthCase("month-0", "15/00/2000", unknown))
    add(birthCase("month-13", "15/13/2000", unknown))
    add(birthCase("day-0", "00/01/2000", capricorn))
    add(birthCase("day-32", "32/01/2000", aquarius))
    add(birthCase("blank", "", unknown))
    add(birthCase("no-separator", "15062000", unknown))
    add(birthCase("separator-slash", "21/06/2000", handSign(21, 6)))
    add(birthCase("separator-dash", "21-06-2000", handSign(21, 6)))
    add(birthCase("separator-dot", "21.06.2000", handSign(21, 6)))
    // Ghi nhan hanh vi cu: "yyyy-MM-dd" bi doc nhu "dd-MM-yyyy": ngay = 2000, thang = 6 -> ngay >= 22 -> Cu Giai.
    add(birthCase("iso-yyyy-MM-dd-misparsed", "2000-06-15", cancer))
    // Ghi nhan hanh vi cu: phan khong phai so -> 1 -> ngay 1 thang 1 -> Ma Ket.
    add(birthCase("non-numeric-gives-1-1", "ab/cd/2000", capricorn))
    // Ghi nhan hanh vi cu: NBSP nam TRONG phan thang (trim khong bo) nen thang khong doc duoc -> 1; ngay 15 -> Ma Ket.
    add(birthCase("nbsp-inside", "15/\u00A006/2000", capricorn))
    // Ghi nhan hanh vi cu: toIntOrNull nhan chu so Unicode (Arabic-Indic): 15 / 6 -> Song Tu.
    add(birthCase("unicode-digits", "\u0661\u0665/\u0660\u0666/2000", gemini))
    add(ZCase("direct-day-month-31-12", "calculateZodiacFromDayMonth", null, 31, 12, handSign(31, 12)))
    add(birthCase("single-digit-day-month", "5/6/2000", handSign(5, 6)))
    // Hai phan (khong co nam) van doc duoc; phan thua phia sau bi bo qua.
    add(birthCase("two-parts-15-06", "15/06", handSign(15, 6)))
    add(birthCase("four-parts-extra-ignored", "25/06/2000/9", handSign(25, 6)))
    // Chung minh trim(): khong trim thi "  25" khong doc duoc -> ngay 1 -> Song Tu, khac Cu Giai.
    add(birthCase("leading-whitespace-trimmed", "  25/06/2000  ", handSign(25, 6)))
  }

  private fun actual(c: ZCase): Pair<String, String> =
    if (c.fn == "calculateZodiac") ProfileUtils.calculateZodiac(c.birth!!) else ProfileUtils.calculateZodiacFromDayMonth(c.day!!, c.month!!)

  private fun caseMaps(): List<Map<String, Any?>> = cases.map { c ->
    val input = if (c.fn == "calculateZodiac") obj("fn" to c.fn, "birth" to c.birth)
    else obj("fn" to c.fn, "day" to c.day!!.toLong(), "month" to c.month!!.toLong())
    obj(
      "id" to c.id,
      "input" to input,
      "expected" to obj("name" to c.expected.first, "symbol" to c.expected.second),
      "androidLegacy" to null,
      "divergence" to null,
      "optional" to false,
    )
  }

  @Test
  fun zodiacGolden() {
    val ids = cases.map { it.id }
    assertEquals(43, cases.size)
    assertEquals(ids.size, ids.toSet().size)
    // Ham that phai tra dung gia tri expected cho moi ca.
    for (c in cases) assertEquals("${c.id}: ham that", c.expected, actual(c))
    // Ca moc: goi truc tiep theo (ngay, thang) cung ra cung ket qua.
    for (s in starts) {
      assertEquals(s.sign, ProfileUtils.calculateZodiacFromDayMonth(s.day, s.month))
      // Ham tay cung phai dung o chinh ngay moc (neu khong cac ca handSign o tren khong con y nghia).
      assertEquals(s.sign, handSign(s.day, s.month))
    }

    GoldenWriter().check(goldenName, obj("schema" to 1L, "cases" to caseMaps()))
    val file = File(goldenDir, goldenName)
    assertTrue("Thieu golden $goldenName", file.isFile)
    val text = file.readText(Charsets.UTF_8).replace("\r\n", "\n")
    // Chi lay `id` cap ca: dong thut dung 6 dau cach trong khung cases.
    val committedIds = Regex("(?m)^ {6}\"id\": \"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList()
    assertEquals(ids, committedIds)
    assertEquals(43, committedIds.size)
    assertEquals(43, Regex("(?m)^ {6}\"divergence\": null").findAll(text).count())
    assertEquals(43, Regex("(?m)^ {6}\"optional\": false").findAll(text).count())
  }
}
