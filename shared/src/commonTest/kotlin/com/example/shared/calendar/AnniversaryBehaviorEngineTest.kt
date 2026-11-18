package com.example.shared.calendar

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnniversaryBehaviorEngineTest {

    @Test
    fun milestoneProgression_containsAllExpectedDayCounts() {
        val expected = listOf(
            7, 14, 22, 30, 50, 52, 99, 100, 200, 300, 365,
            500, 520, 730, 1000, 1095, 1314, 1825, 2000, 3650,
        )
        assertEquals(expected, AnniversaryBehaviorEngine.MILESTONE_DAYS)
        assertEquals(expected, AnniversaryBehaviorEngine.DAY_MILESTONE_SPECS.map { it.days })
    }

    @Test
    fun day1IsStartDate_soDayNFallsOnStartPlusNMinus1() {
        val start = LocalDate(2026, 1, 1)
        assertEquals(1, AnniversaryBehaviorEngine.loveDaysOn(start, start))
        assertEquals(start, AnniversaryBehaviorEngine.dateForMilestoneDay(start, 1))

        val day100 = AnniversaryBehaviorEngine.dateForMilestoneDay(start, 100)
        assertEquals(LocalDate(2026, 4, 10), day100)
        assertEquals(100, AnniversaryBehaviorEngine.loveDaysOn(start, day100))

        assertNull(AnniversaryBehaviorEngine.loveDaysOn(start, LocalDate(2025, 12, 31)))
    }

    @Test
    fun monthiversary_clampsEndOfMonthProperly() {
        val start = LocalDate(2026, 1, 31)
        val next = AnniversaryBehaviorEngine.nextMonthiversary(start, LocalDate(2026, 2, 1))
        assertEquals(1, next.months)
        assertEquals("2026-02-28", next.dateIso)
        assertEquals(27, next.daysUntil)

        val upcoming = AnniversaryBehaviorEngine.upcomingMonthiversaries(start, LocalDate(2026, 2, 1), count = 3)
        assertEquals(listOf("2026-02-28", "2026-03-31", "2026-04-30"), upcoming.map { it.dateIso })
    }

    @Test
    fun threeAxisOccasionProfiles_distinguishRelevanceExpectationAndDefaultReminder() {
        val profiles = AnniversaryBehaviorEngine.defaultOccasionProfiles("VN").associateBy { it.id }

        val thatTich = profiles.getValue("that_tich")
        assertEquals(CoupleRelevance.CORE, thatTich.coupleRelevance)
        assertEquals(GiftExpectation.SMALL, thatTich.giftExpectation)
        assertTrue(thatTich.defaultReminderOn)

        val vuLan = profiles.getValue("vu_lan")
        assertEquals(GiftExpectation.NONE, vuLan.giftExpectation)
        assertFalse(vuLan.defaultReminderOn)

        val ram = profiles.getValue("ram_thang_gieng")
        assertEquals(CoupleRelevance.NONE, ram.coupleRelevance)
        assertFalse(ram.defaultReminderOn)

        val birthday = profiles.getValue("partner_birthday")
        assertEquals(GiftExpectation.MAJOR, birthday.giftExpectation)
        assertTrue(birthday.bookingAdvised)
        assertTrue(14 in birthday.recommendedLeadDays)

        val idProfiles = AnniversaryBehaviorEngine.defaultOccasionProfiles("ID").associateBy { it.id }
        assertFalse(idProfiles.getValue("valentine").defaultReminderOn)
    }

    @Test
    fun preparationStage_coversAllFiveStages() {
        val birthday = AnniversaryBehaviorEngine.profileById("partner_birthday", "VN")!!
        val thatTich = AnniversaryBehaviorEngine.profileById("that_tich", "VN")!!

        assertEquals(PreparationStage.TODAY_CELEBRATION, AnniversaryBehaviorEngine.calculatePreparationStage(0, birthday))
        assertEquals(PreparationStage.URGENT_LAST_MINUTE, AnniversaryBehaviorEngine.calculatePreparationStage(2, birthday))
        assertEquals(PreparationStage.ACTION_NEEDED, AnniversaryBehaviorEngine.calculatePreparationStage(5, birthday))
        assertEquals(PreparationStage.PLAN_AHEAD_BOOKING, AnniversaryBehaviorEngine.calculatePreparationStage(14, birthday))
        assertEquals(PreparationStage.RELAXED, AnniversaryBehaviorEngine.calculatePreparationStage(25, birthday))
        assertEquals(PreparationStage.RELAXED, AnniversaryBehaviorEngine.calculatePreparationStage(14, thatTich))
    }

    @Test
    fun computeNextOccasionReadiness_andSacredCelebrationToday() {
        val today = LocalDate(2026, 2, 14)
        val readiness = AnniversaryBehaviorEngine.computeNextOccasionReadiness(
            today = today,
            loveStartDate = LocalDate(2025, 5, 1),
            partnerBirthday = LocalDate(2000, 9, 10),
            region = "VN",
        )
        assertNotNull(readiness)
        assertEquals("valentine", readiness.occasionId)
        assertEquals(0, readiness.daysRemaining)
        assertEquals(PreparationStage.TODAY_CELEBRATION, readiness.stage)
        assertTrue(AnniversaryBehaviorEngine.isSacredCelebrationToday(today, LocalDate(2025, 5, 1), LocalDate(2000, 9, 10)))
    }

    @Test
    fun milestoneAndReadinessCopy_isEmojiFreeAndCalm() {
        for (spec in AnniversaryBehaviorEngine.DAY_MILESTONE_SPECS) {
            assertTrue(spec.titleVi.first().isLetterOrDigit(), "Milestone titleVi should lead with text: ${spec.titleVi}")
            assertTrue(spec.titleEn.first().isLetterOrDigit(), "Milestone titleEn should lead with text: ${spec.titleEn}")
            assertFalse(spec.titleVi.contains('!'))
            assertFalse(spec.titleEn.contains('!'))
        }
        val month = AnniversaryBehaviorEngine.nextMonthiversary(LocalDate(2026, 1, 15), LocalDate(2026, 2, 1))
        assertTrue(month.titleVi.first().isLetterOrDigit())
        assertTrue(month.titleEn.first().isLetterOrDigit())
    }
}
