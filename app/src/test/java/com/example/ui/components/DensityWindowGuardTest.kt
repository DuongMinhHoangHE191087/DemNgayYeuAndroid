package com.example.ui.components

// D13: every Dialog / AlertDialog / DropdownMenu / Popup must come from AppWindows.kt, which applies AppDensity once.
// A raw window renders ~18% too big against the 0.85-scaled app; a second AppDensity scales it by 0.85 twice.
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DensityWindowGuardTest {

  private val windows = "Dialog|AlertDialog|BasicAlertDialog|DropdownMenu|ExposedDropdownMenu|Popup"
  private val rawCall = Regex("""(?<![\w.])(androidx\.compose\.[\w.]+\.)?($windows)\s*\(""")
  private val rawImport = Regex("""^import androidx\.compose\.(material3|ui\.window)\.($windows)\b""")
  private val densityOwners = setOf("Theme.kt", "AppWindows.kt", "NotificationsSheet.kt")

  private fun isComment(trimmed: String) =
    trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")

  private fun isRawWindow(line: String): Boolean {
    val trimmed = line.trim()
    return !isComment(trimmed) && (rawCall.containsMatchIn(trimmed) || rawImport.containsMatchIn(trimmed))
  }

  private fun mainSources(): List<File> {
    val sources = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    assertTrue("src/main/java not found: run the test from the app module", sources.any { it.name == "AppWindows.kt" })
    return sources
  }

  private fun offenders(sources: List<File>, skip: (File) -> Boolean, hit: (String) -> Boolean): List<String> =
    sources.filterNot(skip).flatMap { file ->
      file.readLines().mapIndexedNotNull { index, line ->
        if (hit(line)) "${file.invariantSeparatorsPath}:${index + 1}: ${line.trim()}" else null
      }
    }

  @Test
  fun noRawWindowOutsideAppWindows() {
    val found = offenders(mainSources(), { it.name == "AppWindows.kt" }, ::isRawWindow)
    assertTrue("use AppDialog / AppAlertDialog / AppDropdownMenu from AppWindows.kt:\n" + found.joinToString("\n"), found.isEmpty())
  }

  @Test
  fun appDensityOnlyInThemeAppWindowsAndNotificationsSheet() {
    val found = offenders(mainSources(), { it.name in densityOwners }) { line ->
      !isComment(line.trim()) && "AppDensity" in line
    }
    assertTrue("a second AppDensity double-scales the window:\n" + found.joinToString("\n"), found.isEmpty())
  }

  @Test
  fun matcherFlagsRawWindowsAndIgnoresTheWrappers() {
    listOf(
      "Dialog(onDismissRequest = {}) {",
      "  AlertDialog(",
      "androidx.compose.ui.window.Dialog(",
      "DropdownMenu (expanded = open,",
      "ExposedDropdownMenu(expanded = open,",
      "Popup(alignment = Alignment.TopEnd) {",
      "import androidx.compose.material3.AlertDialog",
      "import androidx.compose.ui.window.Dialog as ComposeDialog"
    ).forEach { assertTrue("should match: $it", isRawWindow(it)) }

    listOf(
      "AppDialog(",
      "AppAlertDialog(",
      "AppDropdownMenu(",
      "EditCoupleDialog(",
      "DatePickerDialog(",
      "DropdownMenuItem(",
      "ExposedDropdownMenuBox(",
      "DialogProperties(usePlatformDefaultWidth = false)",
      "import androidx.compose.ui.window.DialogProperties",
      "import androidx.compose.material3.DropdownMenuItem",
      "// Dialog(",
      " * AlertDialog(",
      "/** Popup( */"
    ).forEach { assertFalse("should not match: $it", isRawWindow(it)) }
  }
}
