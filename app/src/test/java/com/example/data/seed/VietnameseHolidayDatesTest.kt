package com.example.data.seed

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VietnameseHolidayDatesTest {

    @Test
    fun firstFullMoonFestival2027IsFebruary20() {
        val holiday = VietnameseHolidays.lunarHolidays.single { it.titleEn == "First Full Moon Festival" }
        assertEquals(
            "First Full Moon Festival (Ram Thang Gieng) 2027 should be 2027-02-20 (lunar 15/1)",
            LocalDate.of(2027, 2, 20),
            holiday.datesByYear[2027]
        )
    }

    @Test
    fun christmasEveIsOneFixedCoupleHolidayOnDecember24() {
        val holidays = VietnameseHolidays.fixedHolidays.filter { it.month == 12 && it.day == 24 }
        assertEquals("Christmas Eve (24/12) should be listed exactly once", 1, holidays.size)
        val holiday = holidays.single()
        assertEquals("Christmas Eve titleVi", "Đêm Giáng Sinh", holiday.titleVi)
        assertEquals("Christmas Eve titleEn", "Christmas Eve", holiday.titleEn)
        assertEquals("Christmas Eve emoji", "🌟", holiday.emoji)
        assertEquals("Christmas Eve gift category", "Lãng Mạn", holiday.suggestedGiftCategory)
        assertTrue("Christmas Eve should be couple-relevant", holiday.isCoupleRelevant)
    }

    private fun lunar(titleVi: String) = VietnameseHolidays.lunarHolidays.first { it.titleVi == titleVi }

    @Test
    fun everyLunarHolidayCoversEveryYearFrom2025To2040() {
        val years = (2025..2040).toList()
        for (holiday in VietnameseHolidays.lunarHolidays) {
            assertEquals(holiday.titleVi, years, holiday.datesByYear.keys.sorted())
            holiday.datesByYear.forEach { (year, date) -> assertEquals(holiday.titleVi, year, date.year) }
        }
    }

    @Test
    fun pinnedLunarDatesIncludingTheTimeZoneAndMidnightCells() {
        assertEquals(LocalDate.of(2030, 2, 2), lunar("Tết Nguyên Đán").datesByYear[2030])
        assertEquals(LocalDate.of(2030, 2, 16), lunar("Rằm Tháng Giêng").datesByYear[2030])
        assertEquals(LocalDate.of(2034, 9, 26), lunar("Tết Trung Thu").datesByYear[2034])
        assertEquals(LocalDate.of(2037, 4, 24), lunar("Giỗ Tổ Hùng Vương").datesByYear[2037])
        assertEquals(LocalDate.of(2038, 4, 13), lunar("Giỗ Tổ Hùng Vương").datesByYear[2038])
        assertEquals(LocalDate.of(2035, 8, 18), lunar("Lễ Vu Lan").datesByYear[2035])
    }

    @Test
    fun nothingIsInventedBeyondTheTable() {
        VietnameseHolidays.lunarHolidays.forEach {
            assertNull(it.titleVi, HolidayDates.nextLunar(it, LocalDate.of(2041, 1, 1)))
        }
    }
}
