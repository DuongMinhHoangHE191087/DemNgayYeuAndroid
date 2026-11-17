package com.example.alarm.plan

import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderKeyTest {

  @Test
  fun stableIdsMatchSpec() {
    assertEquals("adv.12", ReminderKey.Advance(12).stableId)
    assertEquals("dayof.12", ReminderKey.DayOf(12).stableId)
    assertEquals("ums.3", ReminderKey.UserMilestone(3).stableId)
    assertEquals("couple", ReminderKey.CoupleAnniversary.stableId)
    assertEquals("lms3.100", ReminderKey.LoveMilestoneThreeDays(100).stableId)
    assertEquals("lms1.100", ReminderKey.LoveMilestoneOneDay(100).stableId)
    assertEquals("lmsd.100", ReminderKey.LoveMilestoneDayOf(100).stableId)
    assertEquals("custom.7", ReminderKey.Custom(7).stableId)
  }

  @Test
  fun stableIdsAreUniqueAcrossKinds() {
    val ids = listOf(
      ReminderKey.Advance(1),
      ReminderKey.DayOf(1),
      ReminderKey.UserMilestone(1),
      ReminderKey.LoveMilestoneThreeDays(1),
      ReminderKey.LoveMilestoneOneDay(1),
      ReminderKey.LoveMilestoneDayOf(1),
      ReminderKey.Custom(1),
      ReminderKey.CoupleAnniversary
    ).map { it.stableId }
    assertEquals(8, ids.toSet().size)
    assertNotEquals(ReminderKey.Advance(1).stableId, ReminderKey.DayOf(1).stableId)
  }

  @Test
  fun planInputDefaultsAreNotRedactedAndNotAbsolute() {
    val input = ReminderPlanInput()
    assertFalse(input.redactContent)
    assertTrue(input.anniversaryNotificationsEnabled)
    assertEquals("", input.ownerId)
    assertTrue(input.soundEnabled)
    assertFalse(input.isEnglish)
    assertNull(input.couple)
    assertTrue(input.anniversaries.isEmpty())
    assertTrue(input.milestones.isEmpty())
    assertTrue(input.customReminders.isEmpty())
    assertTrue(input.disabledCadenceKeys.isEmpty())

    val planned = PlannedNotification(
      key = ReminderKey.CoupleAnniversary,
      kind = ReminderKind.COUPLE_ANNIVERSARY,
      fireAt = LocalDateTime(2026, 10, 15, 9, 0),
      fireAtEpochMillis = 0L,
      title = "t",
      body = "b",
      route = "calendar"
    )
    assertFalse(planned.isAbsolute)
    assertFalse(planned.repeatsYearly)
  }
}
