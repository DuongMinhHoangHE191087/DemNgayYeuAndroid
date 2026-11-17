package com.example.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleDateFormatLocaleGuardTest {

  private val constructorCall = Regex("""\bSimpleDateFormat\(""")

  private fun argsOf(text: String, open: Int): String {
    var depth = 0
    var quoted = false
    for (i in open until text.length) {
      val c = text[i]
      if (c == '"') {
        quoted = !quoted
      } else if (!quoted && c == '(') {
        depth++
      } else if (!quoted && c == ')') {
        depth--
        if (depth == 0) return text.substring(open + 1, i)
      }
    }
    return ""
  }

  private fun offenderLines(text: String): List<Int> =
    constructorCall.findAll(text)
      .filterNot { argsOf(text, it.range.last).contains("Locale.ROOT") }
      .map { text.substring(0, it.range.first).count { c -> c == '\n' } + 1 }
      .toList()

  private fun mainSources(): List<File> {
    val sources = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    assertTrue("src/main/java not found: run the test from the app module", sources.any { it.name == "AppWindows.kt" })
    return sources
  }

  @Test
  fun simpleDateFormatPinsLocaleRoot() {
    val found = mainSources().flatMap { file ->
      offenderLines(file.readText()).map { "${file.invariantSeparatorsPath}:$it" }
    }
    assertTrue("pin SimpleDateFormat to Locale.ROOT (numeric patterns must not change under th, ar, ja):\n" + found.joinToString("\n"), found.isEmpty())
  }

  @Test
  fun matcherReadsTheWholeConstructorCall() {
    assertEquals(listOf(1), offenderLines("val a = SimpleDateFormat(\"dd/MM/yyyy\", Locale.getDefault())"))
    assertEquals(emptyList<Int>(), offenderLines("val a = SimpleDateFormat(\"dd/MM/yyyy\", Locale.ROOT)"))
    assertEquals(emptyList<Int>(), offenderLines("val a = SimpleDateFormat(\n  \"dd/MM/yyyy\",\n  Locale.ROOT\n)"))
    assertEquals(listOf(1), offenderLines("val a = SimpleDateFormat(\n  \"dd/MM/yyyy\",\n  Locale.getDefault()\n)\nval b = Locale.ROOT"))
    assertEquals(listOf(1), offenderLines("val a = java.text.SimpleDateFormat(\"(dd)\", java.util.Locale.getDefault())"))
  }
}
