package com.example.data.seed

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters

/**
 * Bộ dữ liệu ngày lễ phương Tây / Mỹ — dành cho người dùng chọn ngôn ngữ tiếng Anh
 * ([com.example.ui.util.AppLanguage.EN]). Không đổi schema Room — xem KDoc của
 * [VietnameseHolidays] để biết cách nơi gọi sẽ map sang entity hiện có.
 *
 * Ngày cố định tính chắc chắn qua [java.time]. Ngày "thứ N trong tháng" (Mother's Day,
 * Father's Day, Thanksgiving, Sweetest Day) tính bằng [TemporalAdjusters] — không phải bảng
 * tra, vì quy tắc "Chủ Nhật thứ 2 của tháng 5" v.v. là quy tắc dương lịch cố định, tính đúng
 * cho MỌI năm mà không cần dữ liệu thiên văn (khác với âm lịch Việt Nam).
 *
 * Lễ Phục Sinh (Easter) CHƯA đưa vào: ngày Easter phụ thuộc thuật toán Computus (dựa trên chu
 * kỳ trăng nhà thờ) — không triển khai ở đây vì chưa kiểm chứng được bằng test; để lại như một
 * TODO cho lần sau có thể build/test.
 */
object WesternHolidays {

    data class FixedHoliday(
        val titleVi: String,
        val titleEn: String,
        val month: Int,
        val day: Int,
        val emoji: String,
        val suggestedGiftCategory: String,
        val isCoupleRelevant: Boolean
    )

    val fixedHolidays: List<FixedHoliday> = listOf(
        FixedHoliday("Tết Dương Lịch", "New Year's Day", 1, 1, "🎉", "Experience", false),
        FixedHoliday("Lễ Tình Nhân", "Valentine's Day", 2, 14, "💝", "Romantic", true),
        FixedHoliday("Ngày Thánh Patrick", "St. Patrick's Day", 3, 17, "🍀", "Experience", false),
        FixedHoliday("Ngày Bạn Trai Quốc Gia", "National Boyfriend Day", 10, 3, "💙", "Romantic", true),
        FixedHoliday("Ngày Quốc Khánh Mỹ", "Independence Day", 7, 4, "🎆", "Experience", false),
        FixedHoliday("Lễ Hội Ma Halloween", "Halloween", 10, 31, "🎃", "Experience", false),
        FixedHoliday("Đêm Giáng Sinh", "Christmas Eve", 12, 24, "🌟", "Romantic", true),
        FixedHoliday("Giáng Sinh", "Christmas Day", 12, 25, "🎄", "Meaningful", true),
        FixedHoliday("Giao Thừa Dương Lịch", "New Year's Eve", 12, 31, "🥂", "Romantic", true)
    )

    /** Ngày lễ tính theo quy tắc "thứ N trong tháng" — đúng công thức cho mọi năm. */
    data class RuleBasedHoliday(
        val titleVi: String,
        val titleEn: String,
        val emoji: String,
        val suggestedGiftCategory: String,
        val isCoupleRelevant: Boolean,
        val resolve: (year: Int) -> LocalDate
    )

    val ruleBasedHolidays: List<RuleBasedHoliday> = listOf(
        RuleBasedHoliday(
            titleVi = "Ngày Của Mẹ",
            titleEn = "Mother's Day",
            emoji = "💐",
            suggestedGiftCategory = "Meaningful",
            isCoupleRelevant = false
        ) { year -> nthWeekdayOfMonth(year, Month.MAY, DayOfWeek.SUNDAY, occurrence = 2) },
        RuleBasedHoliday(
            titleVi = "Ngày Của Cha",
            titleEn = "Father's Day",
            emoji = "👔",
            suggestedGiftCategory = "Meaningful",
            isCoupleRelevant = false
        ) { year -> nthWeekdayOfMonth(year, Month.JUNE, DayOfWeek.SUNDAY, occurrence = 3) },
        RuleBasedHoliday(
            titleVi = "Lễ Tạ Ơn",
            titleEn = "Thanksgiving",
            emoji = "🦃",
            suggestedGiftCategory = "Experience",
            isCoupleRelevant = false
        ) { year -> nthWeekdayOfMonth(year, Month.NOVEMBER, DayOfWeek.THURSDAY, occurrence = 4) },
        RuleBasedHoliday(
            titleVi = "Ngày Ngọt Ngào (Sweetest Day)",
            titleEn = "Sweetest Day",
            emoji = "🍬",
            suggestedGiftCategory = "Romantic",
            isCoupleRelevant = true
        ) { year -> nthWeekdayOfMonth(year, Month.OCTOBER, DayOfWeek.SATURDAY, occurrence = 3) }
    )

    /**
     * Ngày dương lịch thứ [occurrence] của [dayOfWeek] trong [month] của [year].
     * VD: nthWeekdayOfMonth(2026, MAY, SUNDAY, 2) = Chủ Nhật thứ 2 của tháng 5/2026 (Mother's Day).
     */
    private fun nthWeekdayOfMonth(year: Int, month: Month, dayOfWeek: DayOfWeek, occurrence: Int): LocalDate {
        val firstOfMonth = LocalDate.of(year, month, 1)
        var date = firstOfMonth.with(TemporalAdjusters.firstInMonth(dayOfWeek))
        repeat(occurrence - 1) { date = date.plusWeeks(1) }
        return date
    }
}
