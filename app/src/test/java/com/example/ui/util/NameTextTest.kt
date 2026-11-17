package com.example.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NameTextTest {
  @Test fun decomposedVietnameseBecomesComposed() {
    val nfd = java.text.Normalizer.normalize("Nguyễn Thị Hoàng", java.text.Normalizer.Form.NFD)
    assertEquals("Nguyễn Thị Hoàng", cleanName(nfd))
  }

  @Test fun emojiAreDropped() {
    assertEquals("Hoàng", cleanName("Hoàng 💕"))
    assertEquals("Linh", cleanName("❤️Linh✨"))
  }

  @Test fun spacesCollapseAndTrim() = assertEquals("An Bình", cleanName("  An   Bình "))

  @Test fun zeroWidthAndControlAreDropped() = assertEquals("Mai", cleanName("M‍a\u0007i"))

  @Test fun lettersDigitsAndPunctuationSurvive() = assertEquals("Mary-Ann O'Neil 2", cleanName("Mary-Ann O'Neil 2"))

  @Test fun capsAtFiftyChars() = assertEquals(50, cleanName("a".repeat(80)).length)

  @Test fun blankStaysBlank() = assertEquals("", cleanName(" 💕 "))
}
