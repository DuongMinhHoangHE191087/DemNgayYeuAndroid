package com.example.golden

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dem placeholder (`%s`, `%d`, `%1$s`, `%2$d`; `%%` la ky tu literal, khong dem) cua tung truong AppStrings
 * o 4 nguon: viResource, viStatic, enResource, enStatic. In bao cao cac truong lech nhau va DO MAU khi co bat ky
 * truong nao lech placeholder giua 4 nguon; cung bao cao nay duoc LocaleStringsGoldenTest ghi vao golden GV-023
 * (ca `placeholder-parity-report`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StringPlaceholderParityTest {
  @Test
  fun placeholderParityReport() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val report = LocaleStringsSupport.parityReport(context)
    val mismatches = report.getValue("mismatches") as Map<*, *>
    println("PLACEHOLDER PARITY: ${report["mismatchCount"]} / ${report["fieldCount"]} truong lech")
    for ((field, sources) in mismatches) println("  $field -> $sources")
    // Kiem tra tinh toan on dinh (khong phai kiem tra bang nhau).
    assertEquals(mismatches.size.toLong(), report["mismatchCount"])
    assertTrue("Placeholder lech: $mismatches", mismatches.isEmpty())
  }

  private val S = LocaleStringsSupport

  @Test
  fun signatureResolvesPositions() {
    // Co vi tri `1$` bang khong co vi tri o cung vi tri.
    assertEquals(S.signature("%d"), S.signature("%1\$d"))
    assertEquals(S.signature("a %s b %d"), S.signature("%1\$s - %2\$d"))
    // Thu tu doi so khac nhau -> khac.
    assertNotEquals(S.signature("%s %d"), S.signature("%d %s"))
    // Doi so co vi tri bi hoan vi cung kieu thi van la cung tap.
    assertEquals(S.signature("%s %s"), S.signature("%2\$s %1\$s"))
    // Kieu khac nhau -> khac.
    assertNotEquals(S.signature("%s"), S.signature("%d"))
    // Thieu / thua mot placeholder -> khac.
    assertNotEquals(S.signature("%s"), S.signature("%s %s"))
    assertNotEquals(S.signature("%s"), S.signature("khong co"))
    // Chi so thong thuong dem doc lap voi so co vi tri (java.util.Formatter): "%d %2$s %d" -> 1d, 2s, 2d.
    assertEquals("1d,2d,2s", S.signature("%d %2\$s %d"))
    assertEquals("1d,1s", S.signature("%s %1\$d"))
    // `%%` la `%` literal, khong dem.
    assertEquals("", S.signature("100%% %%d"))
    assertEquals(S.signature("%d"), S.signature("100%% %d"))
  }

  @Test
  fun parityMismatchesFlagsOnlyDifferingFields() {
    val sources = linkedMapOf(
      "a" to mapOf("x" to "%d", "y" to "%s %d", "z" to "khong"),
      "b" to mapOf("x" to "%1\$d", "y" to "%d %s", "z" to "none"),
    )
    val mismatches = S.parityMismatches(sources, listOf("x", "y", "z"))
    assertEquals(listOf("y"), mismatches.keys.toList())
    assertEquals(mapOf("a" to "%s %d", "b" to "%d %s"), mismatches["y"])
    assertEquals(listOf("y"), S.signatureDiff(sources.getValue("a"), sources.getValue("b")))
    assertTrue(S.parityMismatches(mapOf("a" to mapOf("x" to "%s"), "b" to mapOf("x" to "%1\$s")), listOf("x")).isEmpty())
  }

  @Test
  fun blankAndNfcHelpersReportOffenders() {
    assertEquals(listOf("b", "c"), S.blankFields(linkedMapOf("a" to "x", "b" to "", "c" to "  ")))
    // "e" + U+0301 (NFD) khong phai NFC; U+00E9 (NFC) thi phai.
    val nfd = "e" + 0x301.toChar()
    val nfc = 0xE9.toChar().toString()
    assertEquals(listOf("b:src"), S.nonNfcFields("src", linkedMapOf("a" to nfc, "b" to nfd)))
  }
}
