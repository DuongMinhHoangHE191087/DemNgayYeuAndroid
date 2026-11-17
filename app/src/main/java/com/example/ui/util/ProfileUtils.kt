package com.example.ui.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object ProfileUtils {

  // ponytail: new instance per access, SimpleDateFormat is not thread-safe. ThreadLocal if allocation shows in profiles.
  private val dateFormat
    get() = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
      isLenient = false
    }

  /**
   * Calculates Zodiac sign and emoji from birth date string formatted as "dd/MM/yyyy"
   */
  fun calculateZodiac(birthDateStr: String): Pair<String, String> {
    if (birthDateStr.isBlank()) return "Chưa rõ" to "✨"
    return try {
      val parts = birthDateStr.trim().split("/", "-", ".")
      if (parts.size >= 2) {
        val day = parts[0].toIntOrNull() ?: 1
        val month = parts[1].toIntOrNull() ?: 1
        calculateZodiacFromDayMonth(day, month)
      } else {
        "Chưa rõ" to "✨"
      }
    } catch (e: Exception) {
      "Chưa rõ" to "✨"
    }
  }

  /**
   * Calculates Zodiac sign name and symbol from day and month
   */
  fun calculateZodiacFromDayMonth(day: Int, month: Int): Pair<String, String> {
    return when (month) {
      1 -> if (day < 20) "Ma Kết" to "♑" else "Bảo Bình" to "♒"
      2 -> if (day < 19) "Bảo Bình" to "♒" else "Song Ngư" to "♓"
      3 -> if (day < 21) "Song Ngư" to "♓" else "Bạch Dương" to "♈"
      4 -> if (day < 20) "Bạch Dương" to "♈" else "Kim Ngưu" to "♉"
      5 -> if (day < 21) "Kim Ngưu" to "♉" else "Song Tử" to "♊"
      6 -> if (day < 22) "Song Tử" to "♊" else "Cự Giải" to "♋"
      7 -> if (day < 23) "Cự Giải" to "♋" else "Sư Tử" to "♌"
      8 -> if (day < 23) "Sư Tử" to "♌" else "Xử Nữ" to "♍"
      9 -> if (day < 23) "Xử Nữ" to "♍" else "Thiên Bình" to "♎"
      10 -> if (day < 24) "Thiên Bình" to "♎" else "Bọ Cạp" to "♏"
      11 -> if (day < 23) "Bọ Cạp" to "♏" else "Nhân Mã" to "♐"
      12 -> if (day < 22) "Nhân Mã" to "♐" else "Ma Kết" to "♑"
      else -> "Chưa rõ" to "✨"
    }
  }

  /**
   * Returns English name for a Zodiac sign
   */
  fun getZodiacEnglishName(zodiacVi: String): String {
    return when (zodiacVi.trim()) {
      "Bạch Dương" -> "Aries"
      "Kim Ngưu" -> "Taurus"
      "Song Tử" -> "Gemini"
      "Cự Giải" -> "Cancer"
      "Sư Tử" -> "Leo"
      "Xử Nữ" -> "Virgo"
      "Thiên Bình" -> "Libra"
      "Bọ Cạp", "Thiên Yết", "Hổ Cáp" -> "Scorpio"
      "Nhân Mã" -> "Sagittarius"
      "Ma Kết" -> "Capricorn"
      "Bảo Bình" -> "Aquarius"
      "Song Ngư" -> "Pisces"
      else -> zodiacVi.trim()
    }
  }

  /**
   * Returns the Zodiac astrological symbol emoji (♈, ♉, ♊...)
   */
  fun getZodiacSymbol(zodiacName: String): String {
    return when (zodiacName.trim()) {
      "Bạch Dương", "Aries" -> "♈"
      "Kim Ngưu", "Taurus" -> "♉"
      "Song Tử", "Gemini" -> "♊"
      "Cự Giải", "Cancer" -> "♋"
      "Sư Tử", "Leo" -> "♌"
      "Xử Nữ", "Virgo" -> "♍"
      "Thiên Bình", "Libra" -> "♎"
      "Bọ Cạp", "Thiên Yết", "Hổ Cáp", "Scorpio" -> "♏"
      "Nhân Mã", "Sagittarius" -> "♐"
      "Ma Kết", "Capricorn" -> "♑"
      "Bảo Bình", "Aquarius" -> "♒"
      "Song Ngư", "Pisces" -> "♓"
      else -> "✨"
    }
  }

  /**
   * Formats Zodiac for display cleanly without awkward mixing (e.g. "Scorpio ♏" or "Cung Bọ Cạp ♏")
   */
  fun formatZodiacDisplay(zodiacName: String, isEnglish: Boolean): String {
    if (zodiacName.isBlank() || zodiacName == "Chưa rõ") return ""
    val symbol = getZodiacSymbol(zodiacName)
    return if (isEnglish) {
      val engName = getZodiacEnglishName(zodiacName)
      "$engName $symbol"
    } else {
      val viName = when (zodiacName.trim()) {
        "Aries" -> "Bạch Dương"
        "Taurus" -> "Kim Ngưu"
        "Gemini" -> "Song Tử"
        "Cancer" -> "Cự Giải"
        "Leo" -> "Sư Tử"
        "Virgo" -> "Xử Nữ"
        "Libra" -> "Thiên Bình"
        "Scorpio" -> "Bọ Cạp"
        "Sagittarius" -> "Nhân Mã"
        "Capricorn" -> "Ma Kết"
        "Aquarius" -> "Bảo Bình"
        "Pisces" -> "Song Ngư"
        else -> zodiacName.trim()
      }
      "Cung $viName $symbol"
    }
  }

  /**
   * Formats age nicely without rough abbreviations
   */
  fun formatAgeDisplay(age: Int, isEnglish: Boolean): String {
    if (age <= 0) return ""
    return if (isEnglish) "$age years old" else "$age tuổi"
  }

  /**
   * Calculates age accurately from birth date string ("dd/MM/yyyy"); [todayMillis] lets tests pin "today"
   */
  fun calculateAge(birthDateStr: String, todayMillis: Long = System.currentTimeMillis()): Int {
    if (birthDateStr.isBlank()) return 0
    return try {
      val date = dateFormat.parse(birthDateStr.trim()) ?: return 0
      val dob = Calendar.getInstance().apply { time = date }
      val today = Calendar.getInstance().apply { timeInMillis = todayMillis }

      var age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
      // Compare (month, day), not DAY_OF_YEAR: after 29 Feb the Nth day of a leap and of a normal year are different dates.
      val monthDiff = today.get(Calendar.MONTH) - dob.get(Calendar.MONTH)
      if (monthDiff < 0 || (monthDiff == 0 && today.get(Calendar.DAY_OF_MONTH) < dob.get(Calendar.DAY_OF_MONTH))) {
        age--
      }
      age.coerceAtLeast(0)
    } catch (e: Exception) {
      // Fallback parsing if needed
      val parts = birthDateStr.trim().split("/", "-", ".")
      if (parts.size == 3) {
        val year = parts[2].toIntOrNull() ?: 2000
        val currentYear = Calendar.getInstance().apply { timeInMillis = todayMillis }.get(Calendar.YEAR)
        (currentYear - year).coerceAtLeast(0)
      } else 0
    }
  }

  /**
   * Calculates number of love days from start date in milliseconds
   */
  fun calculateLoveDays(startDateMillis: Long): Int {
    val now = System.currentTimeMillis()
    if (startDateMillis > now) return 0
    val diff = now - startDateMillis
    val days = TimeUnit.MILLISECONDS.toDays(diff).toInt()
    return (days + 1).coerceAtLeast(1)
  }

  /**
   * Calculates number of love days from start date string ("dd/MM/yyyy")
   */
  fun calculateLoveDays(startDateStr: String): Int {
    if (startDateStr.isBlank()) return 1
    return try {
      val date = dateFormat.parse(startDateStr.trim()) ?: return 1
      calculateLoveDays(date.time)
    } catch (e: Exception) {
      1
    }
  }

  /**
   * Formats timestamp millis to "dd/MM/yyyy"
   */
  fun formatDate(millis: Long): String {
    return dateFormat.format(Date(millis))
  }

  /**
   * Parses "dd/MM/yyyy" to epoch millis
   */
  fun parseDateToMillis(dateStr: String): Long {
    return try {
      dateFormat.parse(dateStr.trim())?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
      System.currentTimeMillis()
    }
  }

  /**
   * Parse ngày kỷ niệm theo 4 pattern của VM (non-lenient), cả chuỗi phải khớp và năm >= 1900.
   * Formatter tạo mới mỗi lần gọi vì SimpleDateFormat không thread-safe.
   */
  private fun parseAnniversaryMillis(text: String): Long? {
    val s = text.trim()
    if (s.isEmpty()) return null
    for (pattern in listOf("dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy", "yyyy-MM-dd")) {
      val sdf = SimpleDateFormat(pattern, Locale.ROOT)
      sdf.isLenient = false
      val pos = java.text.ParsePosition(0)
      val date = sdf.parse(s, pos) ?: continue
      if (pos.index != s.length) continue
      val year = Calendar.getInstance().apply { time = date }.get(Calendar.YEAR)
      if (year >= 1900) return date.time
    }
    return null
  }

  /**
   * Số ngày yêu tại [nowMillis] (ngày bắt đầu tính là ngày 1), cùng số với [calculateLoveDays].
   * Null khi trống, không parse được hoặc ở tương lai.
   */
  fun loveDaysOn(anniversaryDate: String, nowMillis: Long = System.currentTimeMillis()): Int? {
    val start = parseAnniversaryMillis(anniversaryDate) ?: return null
    if (start > nowMillis) return null
    return (TimeUnit.MILLISECONDS.toDays(nowMillis - start).toInt() + 1).coerceAtLeast(1)
  }

  /**
   * Chuẩn hoá ngày kỷ niệm nhập tay: d{1,2}[/-]d{1,2}[/-]d{4} hoặc yyyy-M-d thành "dd/MM/yyyy".
   * Null khi trống, sai, năm < 1900 hoặc sau hôm nay (so theo ngày).
   */
  fun normalizeAnniversaryInput(text: String, nowMillis: Long = System.currentTimeMillis()): String? {
    val s = text.trim()
    val dmy = Regex("""(\d{1,2})[/-](\d{1,2})[/-](\d{4})""").matchEntire(s)
    val ymd = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").matchEntire(s)
    val (d, m, y) = when {
      dmy != null -> dmy.destructured.let { (a, b, c) -> Triple(a.toInt(), b.toInt(), c.toInt()) }
      ymd != null -> ymd.destructured.let { (a, b, c) -> Triple(c.toInt(), b.toInt(), a.toInt()) }
      else -> return null
    }
    if (y < 1900) return null
    val cal = Calendar.getInstance().apply {
      isLenient = false
      clear()
      set(y, m - 1, d)
    }
    try {
      cal.timeInMillis // non-lenient: ngày/tháng sai (31/02) ném IllegalArgumentException
    } catch (e: IllegalArgumentException) {
      return null
    }
    val today = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val todayKey = today.get(Calendar.YEAR) * 10000 + (today.get(Calendar.MONTH) + 1) * 100 + today.get(Calendar.DAY_OF_MONTH)
    if (y * 10000 + m * 100 + d > todayKey) return null
    return String.format(Locale.ROOT, "%02d/%02d/%04d", d, m, y)
  }

  /**
   * Extracts couple code from user input (could be code or shared link)
   * Example:
   *   "LOVE-9966" -> "LOVE-9966"
   *   "https://inlove.app/pair?code=LOVE-9966" -> "LOVE-9966"
   *   "inlove://pair/LOVE-9966" -> "LOVE-9966"
   */
  fun extractCoupleCode(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ""

    // Check query param "code="
    val codeParamRegex = Regex("[?&]code=([a-zA-Z0-9_-]+)", RegexOption.IGNORE_CASE)
    val matchParam = codeParamRegex.find(trimmed)
    if (matchParam != null) {
      return matchParam.groupValues[1].uppercase()
    }

    // Check path /pair/XYZ
    val pathRegex = Regex("/pair/([a-zA-Z0-9_-]+)", RegexOption.IGNORE_CASE)
    val matchPath = pathRegex.find(trimmed)
    if (matchPath != null) {
      return matchPath.groupValues[1].uppercase()
    }

    // Direct code without link prefix
    return trimmed.replace(Regex("[^a-zA-Z0-9_-]"), "").uppercase()
  }

  /**
   * Generates shareable pair link
   */
  fun generatePairLink(coupleCode: String): String {
    return "https://inlove.app/pair?code=$coupleCode"
  }

  fun createShareLink(coupleCode: String): String = generatePairLink(coupleCode)

  fun generateRandomCoupleCode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    val randomPart = (1..4).map { chars.random() }.joinToString("")
    return "LOVE-$randomPart"
  }

  /** The old avatar file to delete after a save: only a `file:` URI directly inside [avatarsDir], and only when replaced. */
  fun staleLocalAvatar(oldUrl: String, newUrl: String, avatarsDir: java.io.File): java.io.File? {
    if (oldUrl == newUrl || !oldUrl.startsWith("file:")) return null
    return try {
      val file = java.io.File(java.net.URI(oldUrl)).canonicalFile
      file.takeIf { it.exists() && it.parentFile == avatarsDir.canonicalFile }
    } catch (e: Exception) {
      null
    }
  }

  /** A local `file:` path means nothing on the partner's phone, so never send it. */
  fun shareableAvatar(url: String): String = if (url.startsWith("file:")) "" else url
}
