package com.example.ui.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.golden.LocaleStringsSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The static tables (LocalizedStrings.get) must equal the Android resources
 * (LocalizedStrings.fromContext) in every field, for both languages.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StaticVsResourceStringsTest {
  @Test
  fun allFields_viStaticEqualsResource() = assertStaticEqualsResource(AppLanguage.VI)

  @Test
  fun allFields_enStaticEqualsResource() = assertStaticEqualsResource(AppLanguage.EN)

  @Test
  fun differencesReportsAChangedField() {
    val vi = LocalizedStrings.get(AppLanguage.VI)
    assertEquals(emptyList<String>(), differingFields(vi, vi.copy()))
    assertEquals(listOf("navHome"), differingFields(vi, vi.copy(navHome = "x")))
  }

  private fun assertStaticEqualsResource(lang: AppLanguage) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val staticStrings = LocalizedStrings.get(lang)
    val resource = LocalizedStrings.fromContext(context, lang)
    val diff = differingFields(staticStrings, resource)
    if (diff.isNotEmpty()) {
      val s = LocaleStringsSupport.asMap(staticStrings)
      val r = LocaleStringsSupport.asMap(resource)
      val lines = diff.map { "$it: static=${s.getValue(it)} resource=${r.getValue(it)}" }
      fail("${diff.size} field(s) of $lang differ:\n" + lines.joinToString("\n"))
    }
  }

  /** Names of the fields whose values differ between [a] and [b], in field-name order. */
  private fun differingFields(a: AppStrings, b: AppStrings): List<String> {
    val ma = LocaleStringsSupport.asMap(a)
    val mb = LocaleStringsSupport.asMap(b)
    return ma.keys.filter { ma.getValue(it) != mb.getValue(it) }
  }
}
