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
 * Quy đổi âm-dương lịch Việt Nam cần dữ liệu thiên văn (múi giờ UTC+7, điểm Sóc, quy tắc tháng
 * nhuận), nên app giữ một bảng tra thay vì tự tính lúc chạy: sai ngày Tết trong một app tình yêu
 * là lỗi mất uy tín nghiêm trọng. Nguồn của bảng: 15 ngày có từ trước lấy từ lịch vạn niên công
 * khai (đối chiếu qua tìm kiếm web ngày 2026-09-24); 65 ngày bổ sung (đến năm 2040) tính bằng
 * thuật toán Hồ Ngọc Đức ở UTC+7, thuật toán này đã được kiểm chứng ở UTC+8 với bảng của Đài
 * Thiên văn Hồng Kông. Chưa có nguồn độc lập cho múi giờ UTC+7 (các trang lịch Việt Nam khớp
 * nhưng nhiều khả năng chạy cùng thuật toán). Hai ngày nằm sát nửa đêm nên có thể lệch một ngày:
 * Vu Lan 2035 (điểm Sóc sau nửa đêm 12 phút) và Giỗ Tổ 2038 (điểm Sóc trước nửa đêm 15 phút).
 * Bảng chỉ phủ 2025..2040, không suy diễn năm ngoài bảng.
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
        FixedHoliday("Ngày Thành Lập QĐND Việt Nam", "Vietnam People's Army Day", 12, 22, "🎖️", "Ý Nghĩa", false),
        FixedHoliday("Đêm Giáng Sinh", "Christmas Eve", 12, 24, "🌟", "Lãng Mạn", true)
    )

    /** Ngày lễ âm lịch — bảng tra theo năm dương lịch 2025..2040 (nguồn và giới hạn: ghi chú đầu file). */
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
                2028 to LocalDate.of(2028, 1, 26),
                2029 to LocalDate.of(2029, 2, 13),
                2030 to LocalDate.of(2030, 2, 2),
                2031 to LocalDate.of(2031, 1, 23),
                2032 to LocalDate.of(2032, 2, 11),
                2033 to LocalDate.of(2033, 1, 31),
                2034 to LocalDate.of(2034, 2, 19),
                2035 to LocalDate.of(2035, 2, 8),
                2036 to LocalDate.of(2036, 1, 28),
                2037 to LocalDate.of(2037, 2, 15),
                2038 to LocalDate.of(2038, 2, 4),
                2039 to LocalDate.of(2039, 1, 24),
                2040 to LocalDate.of(2040, 2, 12)
            )
        ),
        LunarHoliday(
            titleVi = "Rằm Tháng Giêng",
            titleEn = "First Full Moon Festival",
            emoji = "🌕",
            suggestedGiftCategory = "Ý Nghĩa",
            isCoupleRelevant = false,
            datesByYear = mapOf(
                2025 to LocalDate.of(2025, 2, 12),
                2026 to LocalDate.of(2026, 3, 3),
                2027 to LocalDate.of(2027, 2, 20),
                2028 to LocalDate.of(2028, 2, 9),
                2029 to LocalDate.of(2029, 2, 27),
                2030 to LocalDate.of(2030, 2, 16),
                2031 to LocalDate.of(2031, 2, 6),
                2032 to LocalDate.of(2032, 2, 25),
                2033 to LocalDate.of(2033, 2, 14),
                2034 to LocalDate.of(2034, 3, 5),
                2035 to LocalDate.of(2035, 2, 22),
                2036 to LocalDate.of(2036, 2, 11),
                2037 to LocalDate.of(2037, 3, 1),
                2038 to LocalDate.of(2038, 2, 18),
                2039 to LocalDate.of(2039, 2, 7),
                2040 to LocalDate.of(2040, 2, 26)
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
                2028 to LocalDate.of(2028, 4, 4),
                2029 to LocalDate.of(2029, 4, 23),
                2030 to LocalDate.of(2030, 4, 12),
                2031 to LocalDate.of(2031, 4, 1),
                2032 to LocalDate.of(2032, 4, 19),
                2033 to LocalDate.of(2033, 4, 9),
                2034 to LocalDate.of(2034, 4, 28),
                2035 to LocalDate.of(2035, 4, 17),
                2036 to LocalDate.of(2036, 4, 6),
                2037 to LocalDate.of(2037, 4, 24),
                2038 to LocalDate.of(2038, 4, 13),
                2039 to LocalDate.of(2039, 4, 3),
                2040 to LocalDate.of(2040, 4, 20)
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
                2026 to LocalDate.of(2026, 8, 27),
                2027 to LocalDate.of(2027, 8, 16),
                2028 to LocalDate.of(2028, 9, 3),
                2029 to LocalDate.of(2029, 8, 24),
                2030 to LocalDate.of(2030, 8, 13),
                2031 to LocalDate.of(2031, 9, 1),
                2032 to LocalDate.of(2032, 8, 20),
                2033 to LocalDate.of(2033, 8, 9),
                2034 to LocalDate.of(2034, 8, 28),
                2035 to LocalDate.of(2035, 8, 18),
                2036 to LocalDate.of(2036, 9, 5),
                2037 to LocalDate.of(2037, 8, 25),
                2038 to LocalDate.of(2038, 8, 15),
                2039 to LocalDate.of(2039, 9, 3),
                2040 to LocalDate.of(2040, 8, 22)
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
                2027 to LocalDate.of(2027, 9, 15),
                2028 to LocalDate.of(2028, 10, 3),
                2029 to LocalDate.of(2029, 9, 22),
                2030 to LocalDate.of(2030, 9, 12),
                2031 to LocalDate.of(2031, 10, 1),
                2032 to LocalDate.of(2032, 9, 19),
                2033 to LocalDate.of(2033, 9, 8),
                2034 to LocalDate.of(2034, 9, 26),
                2035 to LocalDate.of(2035, 9, 16),
                2036 to LocalDate.of(2036, 10, 4),
                2037 to LocalDate.of(2037, 9, 24),
                2038 to LocalDate.of(2038, 9, 13),
                2039 to LocalDate.of(2039, 10, 2),
                2040 to LocalDate.of(2040, 9, 20)
            )
        )
    )

    /** Ngày dương lịch của [holiday] trong [year], hoặc null nếu năm đó chưa có trong bảng tra. */
    fun resolvedDate(holiday: LunarHoliday, year: Int): LocalDate? = holiday.datesByYear[year]
}
