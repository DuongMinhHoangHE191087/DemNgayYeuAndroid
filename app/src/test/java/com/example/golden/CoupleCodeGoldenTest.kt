package com.example.golden

import com.example.ui.util.ProfileUtils
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GV-024: ma cap doi. `extractCoupleCode` (tach ma tu ma tran / link), `generatePairLink`, `createShareLink`,
 * `generateRandomCoupleCode` (chi ghi regex + so mau, khong ghi ma ngau nhien).
 * `expected` lay tu loi goi that. Ky tu ngoai ASCII dung code point (file nguon chi ASCII).
 * Hai ca kelvin/long-s la optional: JVM va iOS gap ky tu hoa/thuong khac nhau, bo replay iOS bo qua.
 */
class CoupleCodeGoldenTest {
  private val goldenName = "GV-024-couple-code.json"
  private val goldenDir = File("../shared/src/commonTest/resources/golden")
  private val randomPattern = "^LOVE-[A-HJ-NP-Z2-9]{4}$"
  private val randomSamples = 1000

  private fun obj(vararg pairs: Pair<String, Any?>): LinkedHashMap<String, Any?> = linkedMapOf(*pairs)

  private fun cp(vararg codePoints: Int): String = String(codePoints, 0, codePoints.size)

  private class CCase(val id: String, val input: String, val optional: Boolean = false)

  private val fullWidthLove = cp(0xFF2C, 0xFF2F, 0xFF36, 0xFF25) // full-width L O V E
  private val kelvin = cp(0x212A)
  private val longS = cp(0x017F)

  private val extractCases = listOf(
    CCase("raw-code", "LOVE-9966"),
    CCase("query-code-param", "https://inlove.app/pair?code=LOVE-9966"),
    CCase("custom-scheme-inlove-pair", "inlove://pair/LOVE-9966"),
    CCase("percent-encoded-code", "https://inlove.app/pair?code=LOVE%2D9966"),
    CCase("garbage-gives-empty", "!!! ??? ###"),
    CCase("full-width-letters", fullWidthLove + "-9966"),
    CCase("kelvin-sign-u212a", "https://inlove.app/pair?code=LOVE-" + kelvin + "9", optional = true),
    CCase("long-s-u017f", "https://inlove.app/pair?code=LOVE-" + longS + "9", optional = true),
    CCase("lowercase-code", "love-ab12"),
    CCase("whitespace-around", "  \tLOVE-9966\n "),
    CCase("empty", ""),
    CCase("only-whitespace", " \t\n  "),
    CCase("second-param-ampersand", "https://inlove.app/pair?ref=share&code=love-ab12&x=1"),
    CCase("mixed-case-host-and-param", "HTTPS://InLove.App/Pair?CODE=love-Ab12"),
    CCase("path-with-trailing-junk", "https://inlove.app/pair/love-9966/extra?x=1"),
    CCase("path-mixed-case-pair-segment", "inlove://PAIR/love-9966"),
    CCase("code-equals-without-query-marker", "code=LOVE-9966"),
    CCase("query-before-path-precedence", "https://inlove.app/pair/AAAA?code=BBBB"),
    CCase("underscore-hyphen-code", "my_code-12"),
    CCase("pair-link-with-code", ProfileUtils.generatePairLink("LOVE-9966")),
  )

  private fun caseMaps(random: Map<String, Any?>, share: Map<String, Any?>): List<Map<String, Any?>> {
    fun case(id: String, input: Map<String, Any?>, expected: Any?, optional: Boolean = false) = obj(
      "id" to id, "input" to input, "expected" to expected, "androidLegacy" to null, "divergence" to null, "optional" to optional,
    )
    val out = mutableListOf<Map<String, Any?>>()
    for (c in extractCases) {
      out += case(c.id, obj("fn" to "extractCoupleCode", "input" to c.input), ProfileUtils.extractCoupleCode(c.input), c.optional)
      // share-link-equals-pair-link va random-code-format-1000-samples chen sau ca thu 6 de giu thu tu on dinh
      if (c.id == "full-width-letters") {
        out += case("share-link-equals-pair-link", obj("fn" to "createShareLink", "code" to "LOVE-9966"), share)
      }
    }
    out += case("random-code-format-1000-samples", obj("fn" to "generateRandomCoupleCode", "samples" to randomSamples.toLong()), random)
    return out
  }

  @Test
  fun coupleCodeGolden() {
    // 1000 ma ngau nhien deu dung dinh dang.
    val regex = Regex(randomPattern)
    val allMatch = (1..randomSamples).all { regex.matches(ProfileUtils.generateRandomCoupleCode()) }
    assertTrue("ma ngau nhien sai dinh dang", allMatch)
    val random = obj("pattern" to randomPattern, "samples" to randomSamples.toLong(), "allMatch" to allMatch)

    val pair = ProfileUtils.generatePairLink("LOVE-9966")
    val shareLink = ProfileUtils.createShareLink("LOVE-9966")
    assertEquals(pair, shareLink)
    val share = obj("shareLink" to shareLink, "pairLink" to pair, "equal" to (shareLink == pair))

    val cases = caseMaps(random, share)
    val ids = cases.map { it["id"] as String }
    assertEquals(22, cases.size)
    assertEquals(ids.size, ids.toSet().size)

    GoldenWriter().check(goldenName, obj("schema" to 1L, "cases" to cases))
    val file = File(goldenDir, goldenName)
    assertTrue("Thieu golden $goldenName", file.isFile)
    val text = file.readText(Charsets.UTF_8).replace("\r\n", "\n")
    // Chi lay `id` cap ca: dong thut dung 6 dau cach trong khung cases.
    val committedIds = Regex("(?m)^ {6}\"id\": \"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList()
    assertEquals(ids, committedIds)
    assertEquals(22, committedIds.size)
    assertEquals(22, Regex("(?m)^ {6}\"divergence\": null").findAll(text).count())
    assertEquals(22, Regex("(?m)^ {6}\"androidLegacy\": null").findAll(text).count())
    assertEquals(2, Regex("(?m)^ {6}\"optional\": true").findAll(text).count())
    assertEquals(20, Regex("(?m)^ {6}\"optional\": false").findAll(text).count())
  }
}
