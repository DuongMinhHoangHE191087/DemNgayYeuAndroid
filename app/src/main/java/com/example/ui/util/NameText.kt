package com.example.ui.util

import java.text.Normalizer

private const val MAX_NAME_LENGTH = 50

/**
 * Canonical stored form of a person's name: NFC (so "ệ" is one letter, not e + marks), no emoji / symbols /
 * control / zero-width characters, single spaces, trimmed, at most 50 chars.
 * Apply when SAVING, never inside onValueChange: filtering while the IME is composing breaks Telex/VNI.
 */
fun cleanName(raw: String): String {
  val nfc = Normalizer.normalize(raw, Normalizer.Form.NFC)
  val kept = StringBuilder(nfc.length)
  nfc.codePoints().forEach { cp ->
    val drop = when (Character.getType(cp)) {
      Character.OTHER_SYMBOL.toInt(), Character.MODIFIER_SYMBOL.toInt(), Character.CONTROL.toInt(),
      Character.FORMAT.toInt(), Character.SURROGATE.toInt(), Character.PRIVATE_USE.toInt(),
      Character.UNASSIGNED.toInt() -> true
      Character.NON_SPACING_MARK.toInt() -> cp in 0xFE00..0xFE0F // emoji variation selectors
      else -> false
    }
    if (!drop) kept.appendCodePoint(cp)
  }
  return kept.toString().trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH).trim()
}
