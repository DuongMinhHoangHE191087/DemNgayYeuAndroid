// copied-from: ProfileUtils.kt:60-81 @ a4764f8
package com.example.golden

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Ban sao bat bien cua `ProfileUtils.calculateAge` (V-07), oracle cho GV-006. Khong sua than ham: `AgeGoldenTest`
 * bam than ham va doi chieu voi ma goc tai a4764f8. Khac ma goc o dung 5 cho: chu ky nhan `todayMillis` va `zone`,
 * `dateFormat` thanh `dateFormat(zone)`, va ba lan `Calendar.getInstance()` thanh `Calendar.getInstance(zone)` (kem `todayMillis`).
 * Xoa khi U-27 xong va golden GV-006 da khoa hanh vi.
 */
object LegacyAgeOracle {
  // Ban sao cua ProfileUtils.dateFormat (cung mau, locale, khong lenient), them timeZone = zone.
  private fun dateFormat(zone: TimeZone) = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
    isLenient = false
    timeZone = zone
  }

  // BEGIN legacy-body
  fun calculateAge(birthDateStr: String, todayMillis: Long, zone: TimeZone): Int {
    if (birthDateStr.isBlank()) return 0
    return try {
      val date = dateFormat(zone).parse(birthDateStr.trim()) ?: return 0
      val dob = Calendar.getInstance(zone).apply { time = date }
      val today = Calendar.getInstance(zone).apply { timeInMillis = todayMillis }

      var age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
      if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
        age--
      }
      age.coerceAtLeast(0)
    } catch (e: Exception) {
      // Fallback parsing if needed
      val parts = birthDateStr.trim().split("/", "-", ".")
      if (parts.size == 3) {
        val year = parts[2].toIntOrNull() ?: 2000
        val currentYear = Calendar.getInstance(zone).apply { timeInMillis = todayMillis }.get(Calendar.YEAR)
        (currentYear - year).coerceAtLeast(0)
      } else 0
    }
  }
  // END legacy-body
}
