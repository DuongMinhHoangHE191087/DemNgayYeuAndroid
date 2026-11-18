package com.example.shared.calendar

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LunarCalendar2025To2040Test {

    @Test
    fun ramThangGieng2027_isExact2027Feb20() {
        assertEquals(
            LocalDate(2027, 2, 20),
            LunarCalendar2025To2040.solarDate(LunarHolidayId.RAM_THANG_GIENG, 2027),
        )
    }

    @Test
    fun allSixHolidays_2025To2040_matchVerifiedLunarJson() {
        val expectedTet = mapOf(
            2025 to "2025-01-29", 2026 to "2026-02-17", 2027 to "2027-02-06", 2028 to "2028-01-26",
            2029 to "2029-02-13", 2030 to "2030-02-02", 2031 to "2031-01-23", 2032 to "2032-02-11",
            2033 to "2033-01-31", 2034 to "2034-02-19", 2035 to "2035-02-08", 2036 to "2036-01-28",
            2037 to "2037-02-15", 2038 to "2038-02-04", 2039 to "2039-01-24", 2040 to "2040-02-12",
        )
        val expectedRam = mapOf(
            2025 to "2025-02-12", 2026 to "2026-03-03", 2027 to "2027-02-20", 2028 to "2028-02-09",
            2029 to "2029-02-27", 2030 to "2030-02-16", 2031 to "2031-02-06", 2032 to "2032-02-25",
            2033 to "2033-02-14", 2034 to "2034-03-05", 2035 to "2035-02-22", 2036 to "2036-02-11",
            2037 to "2037-03-01", 2038 to "2038-02-18", 2039 to "2039-02-07", 2040 to "2040-02-26",
        )
        val expectedGioTo = mapOf(
            2025 to "2025-04-07", 2026 to "2026-04-26", 2027 to "2027-04-16", 2028 to "2028-04-04",
            2029 to "2029-04-23", 2030 to "2030-04-12", 2031 to "2031-04-01", 2032 to "2032-04-19",
            2033 to "2033-04-09", 2034 to "2034-04-28", 2035 to "2035-04-17", 2036 to "2036-04-06",
            2037 to "2037-04-24", 2038 to "2038-04-13", 2039 to "2039-04-03", 2040 to "2040-04-20",
        )
        val expectedThatTich = mapOf(
            2025 to "2025-08-29", 2026 to "2026-08-19", 2027 to "2027-08-08", 2028 to "2028-08-26",
            2029 to "2029-08-16", 2030 to "2030-08-05", 2031 to "2031-08-24", 2032 to "2032-08-12",
            2033 to "2033-08-01", 2034 to "2034-08-20", 2035 to "2035-08-10", 2036 to "2036-08-28",
            2037 to "2037-08-17", 2038 to "2038-08-07", 2039 to "2039-08-26", 2040 to "2040-08-14",
        )
        val expectedVuLan = mapOf(
            2025 to "2025-09-06", 2026 to "2026-08-27", 2027 to "2027-08-16", 2028 to "2028-09-03",
            2029 to "2029-08-24", 2030 to "2030-08-13", 2031 to "2031-09-01", 2032 to "2032-08-20",
            2033 to "2033-08-09", 2034 to "2034-08-28", 2035 to "2035-08-18", 2036 to "2036-09-05",
            2037 to "2037-08-25", 2038 to "2038-08-15", 2039 to "2039-09-03", 2040 to "2040-08-22",
        )
        val expectedTrungThu = mapOf(
            2025 to "2025-10-06", 2026 to "2026-09-25", 2027 to "2027-09-15", 2028 to "2028-10-03",
            2029 to "2029-09-22", 2030 to "2030-09-12", 2031 to "2031-10-01", 2032 to "2032-09-19",
            2033 to "2033-09-08", 2034 to "2034-09-26", 2035 to "2035-09-16", 2036 to "2036-10-04",
            2037 to "2037-09-24", 2038 to "2038-09-13", 2039 to "2039-10-02", 2040 to "2040-09-20",
        )

        for (year in 2025..2040) {
            val tet = LunarCalendar2025To2040.solarDate(LunarHolidayId.TET, year)!!
            val ram = LunarCalendar2025To2040.solarDate(LunarHolidayId.RAM_THANG_GIENG, year)!!
            assertEquals(LocalDate.parse(expectedTet.getValue(year)), tet, "TET $year")
            assertEquals(LocalDate.parse(expectedRam.getValue(year)), ram, "RAM $year")
            assertEquals(tet.plus(14, DateTimeUnit.DAY), ram, "RAM must equal TET + 14 days in $year")
            assertEquals(
                LocalDate.parse(expectedGioTo.getValue(year)),
                LunarCalendar2025To2040.solarDate(LunarHolidayId.GIO_TO_HUNG_VUONG, year),
                "GIO_TO $year",
            )
            assertEquals(
                LocalDate.parse(expectedThatTich.getValue(year)),
                LunarCalendar2025To2040.solarDate(LunarHolidayId.THAT_TICH, year),
                "THAT_TICH $year",
            )
            assertEquals(
                LocalDate.parse(expectedVuLan.getValue(year)),
                LunarCalendar2025To2040.solarDate(LunarHolidayId.VU_LAN, year),
                "VU_LAN $year",
            )
            assertEquals(
                LocalDate.parse(expectedTrungThu.getValue(year)),
                LunarCalendar2025To2040.solarDate(LunarHolidayId.TRUNG_THU, year),
                "TRUNG_THU $year",
            )
        }
    }

    @Test
    fun coupleRelevance_andOutOfRangeYears() {
        assertTrue(LunarHolidayId.THAT_TICH.isCoupleRelevant)
        assertTrue(LunarHolidayId.TET.isCoupleRelevant)
        assertTrue(LunarHolidayId.TRUNG_THU.isCoupleRelevant)
        assertFalse(LunarHolidayId.RAM_THANG_GIENG.isCoupleRelevant)
        assertFalse(LunarHolidayId.GIO_TO_HUNG_VUONG.isCoupleRelevant)
        assertFalse(LunarHolidayId.VU_LAN.isCoupleRelevant)

        assertNull(LunarCalendar2025To2040.solarDate(LunarHolidayId.TET, 2024))
        assertNull(LunarCalendar2025To2040.solarDate(LunarHolidayId.TET, 2041))
        assertTrue(LunarCalendar2025To2040.allForYear(2024).isEmpty())
        assertEquals(6, LunarCalendar2025To2040.allForYear(2030).size)
    }

    @Test
    fun nextOccurrence_advancesAcrossYearBoundary_andWarnsNearExpiry() {
        val nextThatTich = LunarCalendar2025To2040.nextOccurrence(LunarHolidayId.THAT_TICH, LocalDate(2026, 8, 20))
        assertEquals(LocalDate(2027, 8, 8), nextThatTich)

        val sameDay = LunarCalendar2025To2040.nextOccurrence(LunarHolidayId.RAM_THANG_GIENG, LocalDate(2027, 2, 20))
        assertEquals(LocalDate(2027, 2, 20), sameDay)

        assertFalse(LunarCalendar2025To2040.isDataExpiringSoon(LocalDate(2027, 1, 1)))
        assertTrue(LunarCalendar2025To2040.isDataExpiringSoon(LocalDate(2039, 6, 1)))
    }
}
