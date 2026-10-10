package com.example.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeResourcesTest {

  // The Android template purple/teal colors are unused by the InLove palette; keep them out of R.color.
  @Test
  fun templatePurpleAndTealColors_areNotDeclared() {
    val declared = Class.forName("com.example.R\$color").declaredFields.map { it.name }
    val leftover = declared.filter { it.startsWith("purple_") || it.startsWith("teal_") }
    assertTrue("Màu mẫu purple/teal vẫn còn trong colors.xml: $leftover", leftover.isEmpty())
  }
}
