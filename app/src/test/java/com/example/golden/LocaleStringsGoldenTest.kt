package com.example.golden

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.util.AppLanguage
import com.example.ui.util.AppStrings
import com.example.ui.util.LocalizedStrings
import java.io.File
import java.lang.reflect.Modifier
import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Helper dung chung voi StringPlaceholderParityTest (cung package, cung source set test).
 * Truong cua AppStrings duoc liet ke bang reflection va SAP XEP THEO TEN de ket qua xac dinh.
 */
internal object LocaleStringsSupport {
  private val literalPercent = Regex("%%")
  private val placeholder = Regex("%(\\d+\\$)?[sd]")

  /** Ten truong String cua AppStrings, sap theo ten. Truong khong phai String -> AssertionError. */
  val fields: List<java.lang.reflect.Field> = AppStrings::class.java.declaredFields
    .filter { !Modifier.isStatic(it.modifiers) }
    .sortedBy { it.name }
    .onEach {
      it.isAccessible = true
      if (it.type != String::class.java) throw AssertionError("Truong khong phai String: ${it.name} (${it.type.name})")
    }

  fun asMap(s: AppStrings): Map<String, String> = fields.associate { it.name to (it.get(s) as String) }

  /** Cac placeholder cua mot chuoi theo thu tu xuat hien; `%%` la ky tu `%` literal, khong dem. */
  fun placeholders(v: String): List<String> = placeholder.findAll(literalPercent.replace(v, "")).map { it.value }.toList()

  private val explicitIndex = Regex("^%(\\d+)\\$")

  /**
   * Chu ky so sanh: tap (vi tri doi so -> kieu s/d). Khong dung vi tri thi doi so dem tu 1 (doc lap voi so co vi tri,
   * nhu `java.util.Formatter`). `%1$d` va `%d` o cung vi tri la tuong duong; `%s %d` khac `%d %s`; `%2$s %1$s` bang `%s %s`.
   */
  fun signature(v: String): String {
    var next = 1
    return placeholders(v).map { p -> "${explicitIndex.find(p)?.groupValues?.get(1)?.toInt() ?: next++}${p.last()}" }.sorted().joinToString(",")
  }

  fun blankFields(m: Map<String, String>): List<String> = m.filterValues { it.isBlank() }.keys.toList()

  fun nonNfcFields(tag: String, m: Map<String, String>): List<String> =
    m.filterValues { !Normalizer.isNormalized(it, Normalizer.Form.NFC) }.keys.map { "$it:$tag" }

  /** Truong co chu ky placeholder khac nhau giua hai bang chuoi (cung tap truong, theo thu tu cua `a`). */
  fun signatureDiff(a: Map<String, String>, b: Map<String, String>): List<String> =
    a.keys.filter { signature(a.getValue(it)) != signature(b.getValue(it)) }

  /** Truong co chu ky khac nhau giua cac nguon: ten truong -> {nguon -> cac placeholder ngan cach bang dau cach}. */
  fun parityMismatches(sources: Map<String, Map<String, String>>, names: List<String>): Map<String, Any?> {
    val mismatches = linkedMapOf<String, Any?>()
    for (n in names) {
      val perSource = sources.mapValues { it.value.getValue(n) }
      if (perSource.values.map { signature(it) }.toSet().size > 1) {
        mismatches[n] = perSource.mapValues { placeholders(it.value).joinToString(" ") }
      }
    }
    return mismatches
  }

  fun fromResources(context: Context, lang: AppLanguage) = asMap(LocalizedStrings.fromContext(context, lang))

  /** Bao cao 4 nguon (viResource, viStatic, enResource, enStatic) theo `parityMismatches`. */
  fun parityReport(context: Context): Map<String, Any?> {
    val sources = linkedMapOf(
      "enResource" to fromResources(context, AppLanguage.EN),
      "enStatic" to asMap(LocalizedStrings.get(AppLanguage.EN)),
      "viResource" to fromResources(context, AppLanguage.VI),
      "viStatic" to asMap(LocalizedStrings.get(AppLanguage.VI)),
    )
    val mismatches = parityMismatches(sources, fields.map { it.name })
    return linkedMapOf("fieldCount" to fields.size.toLong(), "mismatchCount" to mismatches.size.toLong(), "mismatches" to mismatches)
  }
}

/**
 * GV-023: chuoi UI cua Android hien tai, de iOS doi chieu bang chuoi moi.
 * Cac ca (moi ca: id, input, expected, androidLegacy = null, divergence = null, optional = false):
 * - resource-vi / resource-en: ten truong -> gia tri tu LocalizedStrings.fromContext (res/values*).
 * - static-vi / static-en: ten truong -> gia tri cua vietnameseStatic / englishStatic (= LocalizedStrings.get).
 * - resource-vs-static-diff-per-field: CHI CHUP, khong assert bang nhau. Moi truong: viResource, viStatic, viDiffers,
 *   enResource, enStatic, enDiffers.
 * - every-field-non-blank-vi / -en: expected.blankFields = ten truong rong/blank o resource (resourceBlank) va static (staticBlank).
 * - placeholders-same-in-vi-and-en: danh sach truong co chu ky placeholder (vi tri -> kieu s/d, xem `signature`; `%1$d` bang `%d`)
 *   khac nhau giua vi va en, rieng cho resource va static. CHI CHUP.
 * - values-are-nfc: danh sach "truong:nguon" khong o dang NFC. CHI CHUP.
 * - field-count-equal: so truong String cua AppStrings (Long) va so truong moi ngon ngu.
 * - placeholder-parity-report: bao cao cua StringPlaceholderParityTest (xem LocaleStringsSupport.parityReport).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocaleStringsGoldenTest {
  private val goldenName = "GV-023-locale-strings.json"
  private val goldenDir = File("../shared/src/commonTest/resources/golden")
  private val S = LocaleStringsSupport

  private fun obj(vararg pairs: Pair<String, Any?>): LinkedHashMap<String, Any?> = linkedMapOf(*pairs)

  private fun case(id: String, input: Map<String, Any?>, expected: Any?) = obj(
    "id" to id,
    "input" to input,
    "expected" to expected,
    "androidLegacy" to null,
    "divergence" to null,
    "optional" to false,
  )

  @Test
  fun localeStringsGolden() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val resVi = S.fromResources(context, AppLanguage.VI)
    val resEn = S.fromResources(context, AppLanguage.EN)
    val staVi = S.asMap(LocalizedStrings.vietnameseStatic)
    val staEn = S.asMap(LocalizedStrings.englishStatic)
    // get(lang) tra dung doi tuong tinh.
    assertEquals(LocalizedStrings.vietnameseStatic, LocalizedStrings.get(AppLanguage.VI))
    assertEquals(LocalizedStrings.englishStatic, LocalizedStrings.get(AppLanguage.EN))

    val names = S.fields.map { it.name }
    for (m in listOf(resVi, resEn, staVi, staEn)) assertEquals(names, m.keys.toList())

    val diff = names.associateWith { n ->
      obj(
        "viResource" to resVi.getValue(n), "viStatic" to staVi.getValue(n), "viDiffers" to (resVi.getValue(n) != staVi.getValue(n)),
        "enResource" to resEn.getValue(n), "enStatic" to staEn.getValue(n), "enDiffers" to (resEn.getValue(n) != staEn.getValue(n)),
      )
    }

    val cases = listOf(
      case("resource-vi", obj("source" to "resource", "lang" to "vi"), resVi),
      case("resource-en", obj("source" to "resource", "lang" to "en"), resEn),
      case("static-vi", obj("source" to "static", "lang" to "vi"), staVi),
      case("static-en", obj("source" to "static", "lang" to "en"), staEn),
      case("resource-vs-static-diff-per-field", obj("languages" to listOf("vi", "en")), diff),
      case(
        "every-field-non-blank-vi", obj("lang" to "vi"),
        obj("resourceBlank" to S.blankFields(resVi), "staticBlank" to S.blankFields(staVi)),
      ),
      case(
        "every-field-non-blank-en", obj("lang" to "en"),
        obj("resourceBlank" to S.blankFields(resEn), "staticBlank" to S.blankFields(staEn)),
      ),
      case(
        "placeholders-same-in-vi-and-en", obj("compare" to "vi-vs-en"),
        obj("resourceDiffers" to S.signatureDiff(resVi, resEn), "staticDiffers" to S.signatureDiff(staVi, staEn)),
      ),
      case(
        "values-are-nfc", obj("form" to "NFC"),
        S.nonNfcFields("viResource", resVi) + S.nonNfcFields("viStatic", staVi) +
          S.nonNfcFields("enResource", resEn) + S.nonNfcFields("enStatic", staEn),
      ),
      case(
        "field-count-equal", obj("compare" to "vi-vs-en"),
        obj("fieldCount" to names.size.toLong(), "viFieldCount" to resVi.size.toLong(), "enFieldCount" to resEn.size.toLong()),
      ),
      case("placeholder-parity-report", obj("compare" to "four-sources"), S.parityReport(context)),
    )
    val ids = cases.map { it["id"] as String }
    assertEquals(11, cases.size)
    assertEquals(ids.size, ids.toSet().size)
    assertEquals(resVi.size, resEn.size)
    assertEquals(staVi.size, staEn.size)
    assertTrue(names.isNotEmpty())

    // Ket qua cua ca that, khong phai bang cua golden, nen khong assert bang nhau resource/static o day.
    GoldenWriter().check(goldenName, obj("schema" to 1L, "cases" to cases))
    val file = File(goldenDir, goldenName)
    assertTrue("Thieu golden $goldenName", file.isFile)
    val text = file.readText(Charsets.UTF_8).replace("\r\n", "\n")
    // Chi lay `id` cap ca: dong thut dung 6 dau cach trong khung cases.
    val committedIds = Regex("(?m)^ {6}\"id\": \"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList()
    assertEquals(ids, committedIds)
    assertEquals(11, committedIds.size)
    assertEquals(11, Regex("(?m)^ {6}\"optional\": false").findAll(text).count())
    assertEquals(11, Regex("(?m)^ {6}\"divergence\": null").findAll(text).count())
    assertEquals(11, Regex("(?m)^ {6}\"androidLegacy\": null").findAll(text).count())
  }
}
