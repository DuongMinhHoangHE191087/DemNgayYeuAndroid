package com.example.golden

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GoldenWriterTest {
  @get:Rule val tmp = TemporaryFolder()

  private fun value(vararg cases: Any?): Map<String, Any?> =
    linkedMapOf("schema" to 1L, "zone" to "UTC", "cases" to cases.toList())

  private fun writer(update: Boolean) = GoldenWriter(tmp.root, update)

  private val expectedText = "{\n" +
    "  \"generatedBy\": \"GoldenWriter\",\n" +
    "  \"schema\": 1,\n" +
    "  \"zone\": \"UTC\",\n" +
    "  \"cases\": [\n" +
    "    \"a\",\n" +
    "    true,\n" +
    "    null,\n" +
    "    2\n" +
    "  ]\n" +
    "}\n"

  @Test
  fun verify_missingFile_fails() {
    val e = assertThrows(AssertionError::class.java) { writer(false).check("GV-X.json", value("a")) }
    assertTrue(e.message, e.message!!.contains("GV-X.json"))
  }

  @Test
  fun verify_staleFile_fails() {
    File(tmp.root, "GV-X.json").writeText(expectedText.replace("\"a\"", "\"b\""))
    assertThrows(AssertionError::class.java) { writer(false).check("GV-X.json", value("a", true, null, 2L)) }
  }

  @Test
  fun verify_crlfFile_passes() {
    File(tmp.root, "GV-X.json").writeText(expectedText.replace("\n", "\r\n"))
    writer(false).check("GV-X.json", value("a", true, null, 2L))
  }

  @Test
  fun update_writesLfFile() {
    writer(true).check("GV-X.json", value("a", true, null, 2L))
    val bytes = File(tmp.root, "GV-X.json").readBytes()
    assertEquals(expectedText, String(bytes, Charsets.UTF_8))
    assertTrue(bytes.none { it == '\r'.code.toByte() })
  }

  @Test
  fun rejectsFloat() {
    assertThrows(IllegalArgumentException::class.java) { writer(true).check("f.json", value(1.5f)) }
    assertThrows(IllegalArgumentException::class.java) { writer(true).check("d.json", value(1.5)) }
  }

  @Test
  fun escapesNonAsciiAndKeepsNfdDistinct() {
    val nfc = "\u00e9"
    val nfd = "e\u0301"
    writer(true).check("u.json", value(nfc, nfd, "\uD83D\uDE00", "q\"\\\n"))
    val text = File(tmp.root, "u.json").readText()
    assertTrue(text, text.contains("    \"\\u00e9\",\n"))
    assertTrue(text, text.contains("    \"e\\u0301\",\n"))
    assertTrue(text, text.contains("    \"\\ud83d\\ude00\",\n"))
    assertTrue(text, text.contains("    \"q\\\"\\\\\\u000a\"\n"))
    assertTrue(text.all { it.code in 0x20..0x7e || it == '\n' })
  }

  @Test
  fun escapesControlCharsIncludingDel() {
    writer(true).check("c.json", value("a\u007fb\u0000\t"))
    val text = File(tmp.root, "c.json").readText()
    assertTrue(text, text.contains("    \"a\\u007fb\\u0000\\u0009\"\n"))
    assertTrue(text.all { it.code in 0x20..0x7e || it == '\n' })
  }

  @Test
  fun rejectsNaNAndNonStringKeys() {
    assertThrows(IllegalArgumentException::class.java) { writer(true).check("n.json", value(Double.NaN)) }
    assertThrows(IllegalArgumentException::class.java) {
      writer(true).check("k.json", mapOf<Any, Any?>(1 to "a"))
    }
    assertThrows(IllegalArgumentException::class.java) {
      writer(true).check("k2.json", value(mapOf<Any, Any?>(1L to "a")))
    }
  }
}
