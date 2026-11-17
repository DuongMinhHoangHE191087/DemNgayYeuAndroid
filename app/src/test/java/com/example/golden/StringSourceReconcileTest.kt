package com.example.golden

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.util.AppLanguage
import com.example.ui.util.LocalizedStrings
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * V-12: nguon chuoi chinh la resource (res/values*, xem docs strings-canonical.md); bang tinh trong LocalizedStrings
 * phai bang resource tung truong, placeholder vi/en phai khop, khong truong nao rong.
 * Helper dung chung: LocaleStringsSupport (LocaleStringsGoldenTest.kt).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StringSourceReconcileTest {
  private val S = LocaleStringsSupport
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  private fun assertStaticEqualsResource(lang: AppLanguage) {
    val static = S.asMap(LocalizedStrings.get(lang))
    val resource = S.fromResources(context, lang)
    val differing = S.fields.map { it.name }.filter { static.getValue(it) != resource.getValue(it) }
    assertTrue(
      "${differing.size} truong static != resource ($lang):\n" +
        differing.joinToString("\n") { "$it: static=${static.getValue(it)} resource=${resource.getValue(it)}" },
      differing.isEmpty(),
    )
  }

  @Test
  fun staticEqualsResource_vi() = assertStaticEqualsResource(AppLanguage.VI)

  @Test
  fun staticEqualsResource_en() = assertStaticEqualsResource(AppLanguage.EN)

  @Test
  fun placeholdersMatchBetweenViAndEn() {
    val resourceDiff = S.signatureDiff(S.fromResources(context, AppLanguage.VI), S.fromResources(context, AppLanguage.EN))
    val staticDiff = S.signatureDiff(S.asMap(LocalizedStrings.get(AppLanguage.VI)), S.asMap(LocalizedStrings.get(AppLanguage.EN)))
    assertTrue("Placeholder vi/en lech o resource: $resourceDiff", resourceDiff.isEmpty())
    assertTrue("Placeholder vi/en lech o static: $staticDiff", staticDiff.isEmpty())
  }

  @Test
  fun noFieldIsBlank() {
    val maps = linkedMapOf(
      "resourceVi" to S.fromResources(context, AppLanguage.VI),
      "resourceEn" to S.fromResources(context, AppLanguage.EN),
      "staticVi" to S.asMap(LocalizedStrings.get(AppLanguage.VI)),
      "staticEn" to S.asMap(LocalizedStrings.get(AppLanguage.EN)),
    )
    for ((name, map) in maps) {
      val blank = S.blankFields(map)
      assertTrue("Truong rong o $name: $blank", blank.isEmpty())
    }
  }
}
