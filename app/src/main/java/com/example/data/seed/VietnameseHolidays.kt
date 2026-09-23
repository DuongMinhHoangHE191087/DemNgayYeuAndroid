package com.example.data.seed

import java.time.LocalDate

/**
 * Bộ dữ liệu ngày lễ / kỷ niệm Việt Nam (dương lịch cố định + âm lịch tra bảng).
 *
 * KHÔNG đổi schema Room — file này chỉ cung cấp dữ liệu thuần Kotlin. Nơi gọi (Task D4 trong
 * `docs/superpowers/plans/2026-09-24-master-hardening-and-relaunch.md`) sẽ map sang
 * `AnniversaryDateEntity`/`MilestoneEntity` đã có sẵn, chọn `titleVi`/`titleEn` theo
 * `AppLanguage` hiện tại lúc seed — không cần thêm cột bilingual vào Room.
 *
 * ═══ VÌ SAO ÂM LỊCH LÀ BẢNG TRA, KHÔNG PHẢI CÔNG THỨC ═══
 *
 * Quy đổi âm-dương lịch Việt Nam chính xác cần dữ liệu thiên văn (múi giờ UTC+7, điểm Sóc,
 * quy tắc tháng nhuận) — một thuật toán viết tay không kiểm chứng được dễ sai ngày Tết, và sai
 * ngày Tết trong một app tình yêu là lỗi mất uy tín nghiêm trọng. Bảng dưới đây lấy từ nguồn
 * lịch vạn niên công khai (đã đối chiếu qua tìm kiếm web ngày 2026-09-24), CHỈ phủ các năm đã
 * xác minh — không suy diễn thêm năm chưa có trong bảng. Cần bổ sung mỗi năm khi có lịch chính
 * thức năm tiếp theo (thường công bố cuối năm trước).
 */
object VietnameseHolidays {

    /** Ngày lễ dương lịch cố định — tính được chắc chắn cho MỌI năm. */
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
        FixedHoliday("Tết Dương Lịch", "New Year's Day", 1, 1, "🎉", "Trải Nghiệm", false),
        FixedHoliday("Lễ Tình Nhân Valentine", "Valentine's Day", 2, 14, "💝", "Lãng Mạn", true),
        FixedHoliday("Quốc Tế Phụ Nữ", "International Women's Day", 3, 8, "🌷", "Lãng Mạn", true),
        FixedHoliday("Ngày Quốc Tế Hạnh Phúc", "International Day of Happiness", 3, 20, "😊", "Trải Nghiệm", true),
        FixedHoliday("Ngày Sách Việt Nam", "Vietnam Book Day", 4, 21, "📚", "Ý Nghĩa", false),
        FixedHoliday("Giải Phóng Miền Nam", "Reunification Day", 4, 30, "🇻🇳", "Trải Nghiệm", false),
        FixedHoliday("Quốc Tế Lao Động", "International Workers' Day", 5, 1, "🎈", "Trải Nghiệm", false),
        FixedHoliday("Ngày Quốc Tế Gia Đình", "International Day of Families", 5, 15, "👨‍👩‍👧", "Ý Nghĩa", false),
        FixedHoliday("Quốc Tế Thiếu Nhi", "International Children's Day", 6, 1, "🧸", "Ý Nghĩa", false),
        FixedHoliday("Ngày Gia Đình Việt Nam", "Vietnamese Family Day", 6, 28, "🏡", "Ý Nghĩa", true),
        FixedHoliday("Ngày Thương Binh Liệt Sĩ", "War Invalids and Martyrs Day", 7, 27, "🕯️", "Ý Nghĩa", false),
        FixedHoliday("Quốc Khánh", "National Day", 9, 2, "🇻🇳", "Trải Nghiệm", false),
        FixedHoliday("Ngày Phụ Nữ Việt Nam", "Vietnamese Women's Day", 10, 20, "🌹", "Lãng Mạn", true),
        FixedHoliday("Ngày Nhà Giáo Việt Nam", "Vietnamese Teachers' Day", 11, 20, "🍎", "Ý Nghĩa", false),
        FixedHoliday("Ngày Thành Lập QĐND Việt Nam", "Vietnam People's Army Day", 12, 22, "🎖️", "Ý Nghĩa", false)
    )

    /** Ngày lễ âm lịch — bảng tra theo năm dương lịch, chỉ chứa năm đã xác minh nguồn. */
    data class LunarHoliday(
        val titleVi: String,
        val titleEn: String,
        val emoji: String,
        val suggestedGiftCategory: String,
        val isCoupleRelevant: Boolean,
        /** năm dương lịch -> ngày dương lịch tương ứng của ngày âm lịch này */
        val datesByYear: Map<Int, LocalDate>
    )

    val lunarHolidays: List<LunarHoliday> = listOf(
        LunarHoliday(
            titleVi = "Tết Nguyên Đán",
            titleEn = "Lunar New Year (Tết)",
            emoji = "🧧",
            suggestedGiftCategory = "Ý Nghĩa",
            isCoupleRelevant = true,
            datesByYear = mapOf(
                2025 to LocalDate.of(2025, 1, 29),
                2026 to LocalDate.of(2026, 2, 17),
                2027 to LocalDate.of(2027, 2, 6),
                2028 to LocalDate.of(2028, 1, 26)
            )
        ),
        LunarHoliday(
            titleVi = "Rằm Tháng Giêng",
            titleEn = "First Full Moon Festival",
            emoji = "🌕",
            suggestedGiftCategory = "Ý Nghĩa",
            isCoupleRelevant = false,
            datesByYear = mapOf(
                2026 to LocalDate.of(2026, 3, 3),
                2027 to LocalDate.of(2027, 2, 19)
            )
        ),
        LunarHoliday(
            titleVi = "Giỗ Tổ Hùng Vương",
            titleEn = "Hùng Kings' Commemoration Day",
            emoji = "🏯",
            suggestedGiftCategory = "Ý Nghĩa",
            isCoupleRelevant = false,
            datesByYear = mapOf(
                2025 to LocalDate.of(2025, 4, 7),
                2026 to LocalDate.of(2026, 4, 26),
                2027 to LocalDate.of(2027, 4, 16),
                2028 to LocalDate.of(2028, 4, 4)
            )
        ),
        LunarHoliday(
            titleVi = "Lễ Vu Lan",
            titleEn = "Vu Lan (Ancestors' Day)",
            emoji = "🪷",
            suggestedGiftCategory = "Ý Nghĩa",
            isCoupleRelevant = false,
            datesByYear = mapOf(
                2025 to LocalDate.of(2025, 9, 6),
                2026 to LocalDate.of(2026, 8, 27)
            )
        ),
        LunarHoliday(
            titleVi = "Tết Trung Thu",
            titleEn = "Mid-Autumn Festival",
            emoji = "🥮",
            suggestedGiftCategory = "Trải Nghiệm",
            isCoupleRelevant = true,
            datesByYear = mapOf(
                2025 to LocalDate.of(2025, 10, 6),
                2026 to LocalDate.of(2026, 9, 25),
                2027 to LocalDate.of(2027, 9, 15)
            )
        )
    )

    /** Ngày dương lịch của [holiday] trong [year], hoặc null nếu năm đó chưa có trong bảng tra. */
    fun resolvedDate(holiday: LunarHoliday, year: Int): LocalDate? = holiday.datesByYear[year]
}
