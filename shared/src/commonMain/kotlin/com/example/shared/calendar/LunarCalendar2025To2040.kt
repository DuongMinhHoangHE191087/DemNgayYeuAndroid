package com.example.shared.calendar

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
enum class LunarHolidayId(
    val lunarMonth: Int,
    val lunarDay: Int,
    val titleVi: String,
    val titleEn: String,
    val emoji: String,
    val isCoupleRelevant: Boolean,
) {
    TET(1, 1, "Tết Nguyên Đán", "Lunar New Year (Tết)", "🧧", true),
    RAM_THANG_GIENG(1, 15, "Rằm Tháng Giêng", "First Full Moon Festival", "🌕", false),
    GIO_TO_HUNG_VUONG(3, 10, "Giỗ Tổ Hùng Vương", "Hùng Kings' Commemoration Day", "🏯", false),
    THAT_TICH(7, 7, "Lễ Thất Tịch", "Qixi / Thất Tịch Love Day", "🌌", true),
    VU_LAN(7, 15, "Lễ Vu Lan", "Vu Lan (Filial Piety Day)", "🪷", false),
    TRUNG_THU(8, 15, "Tết Trung Thu", "Mid-Autumn Festival", "🥮", true),
}

/**
 * Verified 2025-2040 solar date lookup table for Vietnamese Lunar Holidays (UTC+7).
 * Source of truth: docs/research/data/lunar-2025-2040.json (Ho Ngoc Duc UTC+7 verified against HKO & VN sources).
 * Never extrapolates outside [MIN_YEAR]..[MAX_YEAR].
 */
object LunarCalendar2025To2040 {
    const val MIN_YEAR = 2025
    const val MAX_YEAR = 2040

    private val tetDates: Map<Int, LocalDate> = mapOf(
        2025 to LocalDate(2025, 1, 29),
        2026 to LocalDate(2026, 2, 17),
        2027 to LocalDate(2027, 2, 6),
        2028 to LocalDate(2028, 1, 26),
        2029 to LocalDate(2029, 2, 13),
        2030 to LocalDate(2030, 2, 2),
        2031 to LocalDate(2031, 1, 23),
        2032 to LocalDate(2032, 2, 11),
        2033 to LocalDate(2033, 1, 31),
        2034 to LocalDate(2034, 2, 19),
        2035 to LocalDate(2035, 2, 8),
        2036 to LocalDate(2036, 1, 28),
        2037 to LocalDate(2037, 2, 15),
        2038 to LocalDate(2038, 2, 4),
        2039 to LocalDate(2039, 1, 24),
        2040 to LocalDate(2040, 2, 12),
    )

    private val ramThangGiengDates: Map<Int, LocalDate> = mapOf(
        2025 to LocalDate(2025, 2, 12),
        2026 to LocalDate(2026, 3, 3),
        2027 to LocalDate(2027, 2, 20),
        2028 to LocalDate(2028, 2, 9),
        2029 to LocalDate(2029, 2, 27),
        2030 to LocalDate(2030, 2, 16),
        2031 to LocalDate(2031, 2, 6),
        2032 to LocalDate(2032, 2, 25),
        2033 to LocalDate(2033, 2, 14),
        2034 to LocalDate(2034, 3, 5),
        2035 to LocalDate(2035, 2, 22),
        2036 to LocalDate(2036, 2, 11),
        2037 to LocalDate(2037, 3, 1),
        2038 to LocalDate(2038, 2, 18),
        2039 to LocalDate(2039, 2, 7),
        2040 to LocalDate(2040, 2, 26),
    )

    private val gioToDates: Map<Int, LocalDate> = mapOf(
        2025 to LocalDate(2025, 4, 7),
        2026 to LocalDate(2026, 4, 26),
        2027 to LocalDate(2027, 4, 16),
        2028 to LocalDate(2028, 4, 4),
        2029 to LocalDate(2029, 4, 23),
        2030 to LocalDate(2030, 4, 12),
        2031 to LocalDate(2031, 4, 1),
        2032 to LocalDate(2032, 4, 19),
        2033 to LocalDate(2033, 4, 9),
        2034 to LocalDate(2034, 4, 28),
        2035 to LocalDate(2035, 4, 17),
        2036 to LocalDate(2036, 4, 6),
        2037 to LocalDate(2037, 4, 24),
        2038 to LocalDate(2038, 4, 13),
        2039 to LocalDate(2039, 4, 3),
        2040 to LocalDate(2040, 4, 20),
    )

    private val thatTichDates: Map<Int, LocalDate> = mapOf(
        2025 to LocalDate(2025, 8, 29),
        2026 to LocalDate(2026, 8, 19),
        2027 to LocalDate(2027, 8, 8),
        2028 to LocalDate(2028, 8, 26),
        2029 to LocalDate(2029, 8, 16),
        2030 to LocalDate(2030, 8, 5),
        2031 to LocalDate(2031, 8, 24),
        2032 to LocalDate(2032, 8, 12),
        2033 to LocalDate(2033, 8, 1),
        2034 to LocalDate(2034, 8, 20),
        2035 to LocalDate(2035, 8, 10),
        2036 to LocalDate(2036, 8, 28),
        2037 to LocalDate(2037, 8, 17),
        2038 to LocalDate(2038, 8, 7),
        2039 to LocalDate(2039, 8, 26),
        2040 to LocalDate(2040, 8, 14),
    )

    private val vuLanDates: Map<Int, LocalDate> = mapOf(
        2025 to LocalDate(2025, 9, 6),
        2026 to LocalDate(2026, 8, 27),
        2027 to LocalDate(2027, 8, 16),
        2028 to LocalDate(2028, 9, 3),
        2029 to LocalDate(2029, 8, 24),
        2030 to LocalDate(2030, 8, 13),
        2031 to LocalDate(2031, 9, 1),
        2032 to LocalDate(2032, 8, 20),
        2033 to LocalDate(2033, 8, 9),
        2034 to LocalDate(2034, 8, 28),
        2035 to LocalDate(2035, 8, 18),
        2036 to LocalDate(2036, 9, 5),
        2037 to LocalDate(2037, 8, 25),
        2038 to LocalDate(2038, 8, 15),
        2039 to LocalDate(2039, 9, 3),
        2040 to LocalDate(2040, 8, 22),
    )

    private val trungThuDates: Map<Int, LocalDate> = mapOf(
        2025 to LocalDate(2025, 10, 6),
        2026 to LocalDate(2026, 9, 25),
        2027 to LocalDate(2027, 9, 15),
        2028 to LocalDate(2028, 10, 3),
        2029 to LocalDate(2029, 9, 22),
        2030 to LocalDate(2030, 9, 12),
        2031 to LocalDate(2031, 10, 1),
        2032 to LocalDate(2032, 9, 19),
        2033 to LocalDate(2033, 9, 8),
        2034 to LocalDate(2034, 9, 26),
        2035 to LocalDate(2035, 9, 16),
        2036 to LocalDate(2036, 10, 4),
        2037 to LocalDate(2037, 9, 24),
        2038 to LocalDate(2038, 9, 13),
        2039 to LocalDate(2039, 10, 2),
        2040 to LocalDate(2040, 9, 20),
    )

    private val table: Map<LunarHolidayId, Map<Int, LocalDate>> = mapOf(
        LunarHolidayId.TET to tetDates,
        LunarHolidayId.RAM_THANG_GIENG to ramThangGiengDates,
        LunarHolidayId.GIO_TO_HUNG_VUONG to gioToDates,
        LunarHolidayId.THAT_TICH to thatTichDates,
        LunarHolidayId.VU_LAN to vuLanDates,
        LunarHolidayId.TRUNG_THU to trungThuDates,
    )

    fun isSupportedYear(year: Int): Boolean = year in MIN_YEAR..MAX_YEAR

    fun isDataExpiringSoon(today: LocalDate, warnWithinYears: Int = 1): Boolean =
        today.year >= MAX_YEAR - warnWithinYears

    fun solarDate(holiday: LunarHolidayId, year: Int): LocalDate? = table[holiday]?.get(year)

    fun allForYear(year: Int): Map<LunarHolidayId, LocalDate> =
        if (!isSupportedYear(year)) emptyMap()
        else LunarHolidayId.entries.mapNotNull { id -> solarDate(id, year)?.let { id to it } }.toMap()

    fun nextOccurrence(holiday: LunarHolidayId, fromDate: LocalDate): LocalDate? {
        val startYear = maxOf(fromDate.year, MIN_YEAR)
        for (year in startYear..MAX_YEAR) {
            val candidate = solarDate(holiday, year) ?: continue
            if (candidate >= fromDate) return candidate
        }
        return null
    }
}
