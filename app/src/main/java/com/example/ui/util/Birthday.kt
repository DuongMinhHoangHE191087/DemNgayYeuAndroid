package com.example.ui.util

/** Ngày sinh đã chuẩn hoá dd/MM/yyyy: nguồn duy nhất cho mọi chỗ nhập/đọc ngày sinh trong app. */
@JvmInline
value class Birthday private constructor(val text: String) {
  val age: Int get() = ProfileUtils.calculateAge(text)
  val zodiac: String get() = ProfileUtils.calculateZodiac(text).first

  companion object {
    /** Cận dưới của bộ chọn ngày/ô nhập MỚI; ngày đã lưu từ 1900 trở lên vẫn được giữ nguyên (không bị coi là sai). */
    const val MIN_YEAR = 1920

    fun parse(raw: String, nowMillis: Long = System.currentTimeMillis()): Birthday? =
      ProfileUtils.normalizeAnniversaryInput(raw.trim(), nowMillis)?.let { Birthday(it) }

    fun normalize(raw: String, nowMillis: Long = System.currentTimeMillis()): String =
      parse(raw, nowMillis)?.text.orEmpty()
  }
}
