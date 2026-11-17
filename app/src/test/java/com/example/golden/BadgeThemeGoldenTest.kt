package com.example.golden

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.ui.components.getBadgeTheme
import java.io.File
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * GV-008: `getBadgeTheme(tier)` (kieu `BadgeTheme`) cho 6 hang + ten la.
 * Mau ghi la chuoi ARGB 8 chu so hex VIET HOA. `expected` lay tu bang viet tay duoi day (doc lap voi ma that),
 * ham that phai khop bang do. Ten la / rong / co dau cach / co dau thanh theme "Heart" mac dinh (ghi nhan hanh vi cu:
 * khong trim, khong bo dau). Khong ca nao lech nen androidLegacy/divergence luon null, optional luon false.
 */
class BadgeThemeGoldenTest {
  private val goldenName = "GV-008-badge-theme.json"
  private val goldenDir = File("../shared/src/commonTest/resources/golden")
  private lateinit var previousLocale: Locale

  // Ghim tr-TR: uppercase() cua Kotlin phai theo ROOT, "diamond" van ra "DIAMOND" (khong phai "D\u0130AMOND").
  @Before fun pinLocale() {
    previousLocale = Locale.getDefault()
    Locale.setDefault(Locale.forLanguageTag("tr-TR"))
  }

  @After fun restoreLocale() {
    Locale.setDefault(previousLocale)
  }

  private fun obj(vararg pairs: Pair<String, Any?>): LinkedHashMap<String, Any?> = linkedMapOf(*pairs)

  private class H(
    val primary: String,
    val secondary: String,
    val gradient: List<String>,
    val ribbon: String,
    val glow: String,
    val vi: String,
    val en: String,
  )

  // Bang tay. Do mo glow: 0.4 -> 66, 0.5 -> 80, 0.6 -> 99 (alpha * 255 lam tron).
  private val hand = mapOf(
    "BRONZE" to H("FF8D6E63", "FFD7CCC8", listOf("FFFFCCBC", "FFD7CCC8", "FFBCAAA4"), "FFA1887F", "66FFAB91", "Huy Hi\u1EC7u \u0110\u1ED3ng", "Bronze Badge"),
    "SILVER" to H("FF546E7A", "FFECEFF1", listOf("FFCFD8DC", "FFECEFF1", "FF90A4AE"), "FF78909C", "66B0BEC5", "Huy Hi\u1EC7u B\u1EA1c", "Silver Badge"),
    "GOLD" to H("FFFFA000", "FFFFF8E1", listOf("FFFFD54F", "FFFFF9C4", "FFFFB300"), "FFFF8F00", "80FFD54F", "Huy Hi\u1EC7u V\u00E0ng", "Gold Badge"),
    "RUBY" to H("FFE91E63", "FFFCE4EC", listOf("FFFF4081", "FFFF80AB", "FFC2185B"), "FFAD1457", "80FF4081", "Huy Hi\u1EC7u H\u1ED3ng Ng\u1ECDc", "Ruby Badge"),
    "DIAMOND" to H("FF00ACC1", "FFE0F7FA", listOf("FF26C6DA", "FFE0F7FA", "FF0097A7"), "FF00838F", "8000E5FF", "Huy Hi\u1EC7u Kim C\u01B0\u01A1ng", "Diamond Badge"),
    "COSMIC" to H("FF8E24AA", "FFF3E5F5", listOf("FFAB47BC", "FFEA80FC", "FF6A1B9A"), "FF4A148C", "99E040FB", "Huy Hi\u1EC7u V\u0169 Tr\u1EE5", "Cosmic Badge"),
  )

  // Theme mac dinh: Primary = FFE91E63, PrimaryContainer = FFFF2D75 (ui/theme/Color.kt).
  private val heart = H("FFE91E63", "FFFF2D75", listOf("FFFF4081", "FFFF80AB"), "FFE91E63", "66E91E63", "Huy Hi\u1EC7u Tr\u00E1i Tim", "Heart Badge")

  private fun handFor(tier: String): H = hand[tier.uppercase(Locale.ROOT)] ?: heart

  private class BCase(val id: String, val tier: String)

  private val cases: List<BCase> = buildList {
    for (t in listOf("BRONZE", "SILVER", "GOLD", "RUBY", "DIAMOND", "COSMIC")) {
      add(BCase("${t.lowercase(Locale.ROOT)}-upper", t))
      add(BCase("${t.lowercase(Locale.ROOT)}-lower", t.lowercase(Locale.ROOT)))
    }
    add(BCase("mixed-case", "GoLd"))
    add(BCase("unknown", "PLATINUM"))
    add(BCase("empty", ""))
    add(BCase("whitespace", "   "))
    // Khong trim: "GOLD " khong khop "GOLD" -> theme mac dinh.
    add(BCase("trailing-space", "GOLD "))
    add(BCase("diacritics-in-name", "\u0110\u1ED3ng"))
    add(BCase("very-long-name", "SILVER".repeat(11)))
    add(BCase("null-like-text", "null"))
  }

  private fun hex(c: Color): String = (c.toArgb().toLong() and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')

  private fun toMap(h: H): Map<String, Any?> = obj(
    "primaryColor" to h.primary,
    "secondaryColor" to h.secondary,
    "gradient" to h.gradient,
    "ribbonColor" to h.ribbon,
    "glowColor" to h.glow,
    "tierNameVi" to h.vi,
    "tierNameEn" to h.en,
  )

  private fun actual(tier: String): Map<String, Any?> {
    val t = getBadgeTheme(tier)
    return obj(
      "primaryColor" to hex(t.primaryColor),
      "secondaryColor" to hex(t.secondaryColor),
      "gradient" to t.gradient.map { hex(it) },
      "ribbonColor" to hex(t.ribbonColor),
      "glowColor" to hex(t.glowColor),
      "tierNameVi" to t.tierNameVi,
      "tierNameEn" to t.tierNameEn,
    )
  }

  @Test
  fun badgeThemeGolden() {
    val ids = cases.map { it.id }
    assertEquals(20, cases.size)
    assertEquals(ids.size, ids.toSet().size)
    for (name in listOf("mixed-case", "unknown", "empty", "whitespace", "trailing-space", "diacritics-in-name", "very-long-name", "null-like-text"))
      assertTrue("Thieu ca $name", name in ids)
    // Ham that == bang tay cho moi ca.
    for (c in cases) assertEquals("${c.id}: ham that", toMap(handFor(c.tier)), actual(c.tier))

    val maps = cases.map { c ->
      obj(
        "id" to c.id,
        "input" to obj("tier" to c.tier),
        "expected" to toMap(handFor(c.tier)),
        "androidLegacy" to null,
        "divergence" to null,
        "optional" to false,
      )
    }
    GoldenWriter().check(goldenName, obj("schema" to 1L, "cases" to maps))
    val file = File(goldenDir, goldenName)
    assertTrue("Thieu golden $goldenName", file.isFile)
    val text = file.readText(Charsets.UTF_8).replace("\r\n", "\n")
    // Chi lay `id` cap ca: dong thut dung 6 dau cach trong khung cases.
    val committedIds = Regex("(?m)^ {6}\"id\": \"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList()
    assertEquals(ids, committedIds)
    assertEquals(20, committedIds.size)
    assertEquals(20, Regex("(?m)^ {6}\"divergence\": null").findAll(text).count())
    assertEquals(20, Regex("(?m)^ {6}\"optional\": false").findAll(text).count())
  }
}
