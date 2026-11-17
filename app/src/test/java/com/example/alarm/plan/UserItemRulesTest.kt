package com.example.alarm.plan

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserItemRulesTest {

  private val zone = TimeZone.of("Asia/Ho_Chi_Minh")
  private val losAngeles = TimeZone.of("America/Los_Angeles")

  // Mốc "bây giờ" mặc định: 2026-10-09 08:00 ở Hồ Chí Minh.
  private val pinned = at(2026, 10, 9, 8)

  private fun at(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int = 0,
    second: Int = 0,
    inZone: TimeZone = zone
  ): Long = toEpochMillis(LocalDateTime(year, month, day, hour, minute, second), inZone)

  private fun anniversary(
    id: Long,
    dateText: String,
    title: String = "Sinh nhật",
    annual: Boolean = true,
    enabled: Boolean = true,
    daysBefore: Int = 0
  ) = AnniversaryItem(id, title, dateText, annual, enabled, daysBefore)

  private fun milestone(
    id: Long,
    dateText: String,
    title: String = "Du lịch",
    enabled: Boolean = true,
    isPast: Boolean = false
  ) = MilestoneItem(id, title, dateText, enabled, isPast)

  private fun custom(id: Long, alarmEpochMillis: Long, title: String = "Uống thuốc", details: String = "Sau bữa ăn") =
    CustomReminderItem(id, title, details, alarmEpochMillis)

  private fun plan(
    input: ReminderPlanInput,
    nowMillis: Long = pinned,
    inZone: TimeZone = zone
  ): List<PlannedNotification> = UserItemRules.plan(input, nowMillis, inZone)

  private fun List<PlannedNotification>.byKey(): Map<ReminderKey, PlannedNotification> = associateBy { it.key }

  private fun List<PlannedNotification>.keys(): Set<ReminderKey> = map { it.key }.toSet()

  // ---- 11 tên bắt buộc theo dòng bảng của kế hoạch ----

  @Test
  fun cadenceSwitchAppliesOnlyToSevenThreeOne() {
    val items = listOf(
      anniversary(1, "20/10/2026", daysBefore = 7),
      anniversary(2, "20/10/2026", daysBefore = 3),
      anniversary(3, "20/10/2026", daysBefore = 1)
    )
    val all = plan(ReminderPlanInput(anniversaries = items)).byKey()
    assertEquals(LocalDateTime(2026, 10, 13, 9, 0), all.getValue(ReminderKey.Advance(1)).fireAt)
    assertEquals(LocalDateTime(2026, 10, 17, 9, 0), all.getValue(ReminderKey.Advance(2)).fireAt)
    assertEquals(LocalDateTime(2026, 10, 19, 9, 0), all.getValue(ReminderKey.Advance(3)).fireAt)

    val no7 = plan(ReminderPlanInput(anniversaries = items, disabledCadenceKeys = setOf("7_days"))).keys()
    assertEquals(
      setOf(
        ReminderKey.Advance(2), ReminderKey.Advance(3),
        ReminderKey.DayOf(1), ReminderKey.DayOf(2), ReminderKey.DayOf(3)
      ),
      no7
    )
    val no3 = plan(ReminderPlanInput(anniversaries = items, disabledCadenceKeys = setOf("3_days"))).keys()
    assertEquals(
      setOf(
        ReminderKey.Advance(1), ReminderKey.Advance(3),
        ReminderKey.DayOf(1), ReminderKey.DayOf(2), ReminderKey.DayOf(3)
      ),
      no3
    )
    val no1 = plan(ReminderPlanInput(anniversaries = items, disabledCadenceKeys = setOf("1_day"))).keys()
    assertEquals(
      setOf(
        ReminderKey.Advance(1), ReminderKey.Advance(2),
        ReminderKey.DayOf(1), ReminderKey.DayOf(2), ReminderKey.DayOf(3)
      ),
      no1
    )
  }

  @Test
  fun twoDayAdvanceIsNotGatedByAnyCadenceRow() {
    val items = listOf(
      anniversary(1, "20/10/2026", daysBefore = 2),
      anniversary(2, "20/10/2026", daysBefore = 5)
    )
    val disabled = setOf("7_days", "3_days", "1_day")
    val result = plan(ReminderPlanInput(anniversaries = items, disabledCadenceKeys = disabled)).byKey()
    assertEquals(LocalDateTime(2026, 10, 18, 9, 0), result.getValue(ReminderKey.Advance(1)).fireAt)
    assertEquals(LocalDateTime(2026, 10, 15, 9, 0), result.getValue(ReminderKey.Advance(2)).fireAt)
  }

  @Test
  fun advanceIsDroppedWhenDaysBeforeIsZero() {
    val items = listOf(
      anniversary(1, "20/10/2026", daysBefore = 0),
      anniversary(2, "20/10/2026", daysBefore = -3)
    )
    val result = plan(ReminderPlanInput(anniversaries = items))
    assertEquals(setOf<ReminderKey>(ReminderKey.DayOf(1), ReminderKey.DayOf(2)), result.keys())
  }

  @Test
  fun exactDayOffDropsOnlyTheDayOfNotice() {
    val items = listOf(anniversary(1, "20/10/2026", daysBefore = 3))
    val result = plan(ReminderPlanInput(anniversaries = items, disabledCadenceKeys = setOf("exact_day")))
    assertEquals(1, result.size)
    assertEquals(ReminderKey.Advance(1), result[0].key)
    assertEquals(ReminderKind.ADVANCE, result[0].kind)
  }

  @Test
  fun annualDayOfRepeatsYearlyExceptFeb29() {
    val items = listOf(
      anniversary(1, "20/10/2026"),
      anniversary(2, "29/02/2024"),
      anniversary(3, "20/10/2026", annual = false)
    )
    val result = plan(ReminderPlanInput(anniversaries = items)).byKey()
    assertTrue(result.getValue(ReminderKey.DayOf(1)).repeatsYearly)
    assertFalse(result.getValue(ReminderKey.DayOf(2)).repeatsYearly)
    assertFalse(result.getValue(ReminderKey.DayOf(3)).repeatsYearly)
  }

  @Test
  fun pastNineAmIsDropped() {
    val items = listOf(
      anniversary(1, "01/10/2026", annual = false),
      anniversary(2, "09/10/2026", annual = false, daysBefore = 3)
    )
    val milestones = listOf(milestone(3, "01/10/2026"))
    val tenAm = at(2026, 10, 9, 10)
    val result = plan(ReminderPlanInput(anniversaries = items, milestones = milestones), nowMillis = tenAm)
    assertEquals(emptyList<PlannedNotification>(), result)
  }

  @Test
  fun masterSwitchOffDropsI1ToI4ButKeepsCustomAlarm() {
    val input = ReminderPlanInput(
      anniversaries = listOf(anniversary(1, "20/10/2026", daysBefore = 3)),
      milestones = listOf(milestone(2, "25/12/2026")),
      couple = CoupleItem("Hai đứa", "11/09/2023", "Bin"),
      customReminders = listOf(custom(3, at(2026, 10, 12, 21, 30))),
      anniversaryNotificationsEnabled = false
    )
    val result = plan(input)
    assertEquals(1, result.size)
    assertEquals(ReminderKey.Custom(3), result[0].key)
    assertEquals(ReminderKind.CUSTOM, result[0].kind)

    val withSwitchOn = plan(input.copy(anniversaryNotificationsEnabled = true)).keys()
    assertEquals(
      setOf(
        ReminderKey.Advance(1), ReminderKey.DayOf(1), ReminderKey.UserMilestone(2),
        ReminderKey.CoupleAnniversary, ReminderKey.Custom(3)
      ),
      withSwitchOn
    )
  }

  @Test
  fun customAlarmOnlyWhenInTheFuture() {
    val input = ReminderPlanInput(
      customReminders = listOf(
        custom(1, pinned + 1),
        custom(2, pinned),
        custom(3, pinned - 1)
      )
    )
    assertEquals(setOf<ReminderKey>(ReminderKey.Custom(1)), plan(input).keys())
  }

  @Test
  fun coupleAnniversaryUsesLoveDaysOnTheFireDate() {
    val input = ReminderPlanInput(couple = CoupleItem("Hai đứa", "11/09/2023", "Bin"))
    val first = plan(input).single()
    assertEquals(LocalDateTime(2027, 9, 11, 9, 0), first.fireAt)
    // 1461 ngày giữa hai ngày (có một ngày 29/2), cộng một vì bộ đếm tính cả ngày bắt đầu.
    assertTrue(first.body, first.body.contains("Chặng đường 1462 ngày"))

    // Cùng một ngày kỷ niệm nhưng "bây giờ" khác: số ngày chỉ phụ thuộc ngày kích hoạt.
    val later = plan(input, nowMillis = at(2027, 9, 11, 8)).single()
    assertEquals(LocalDateTime(2027, 9, 11, 9, 0), later.fireAt)
    assertTrue(later.body, later.body.contains("Chặng đường 1462 ngày"))

    // 2020-01-01 tới 2027-01-01: 2557 ngày (hai năm nhuận 2020, 2024), cộng một.
    val other = ReminderPlanInput(couple = CoupleItem("Hai đứa", "01/01/2020", ""))
    val second = plan(other).single()
    assertEquals(LocalDateTime(2027, 1, 1, 9, 0), second.fireAt)
    assertTrue(second.body, second.body.contains("Chặng đường 2558 ngày"))
  }

  @Test
  fun customAlarmIsAbsoluteWithEpochFromAlarmTime() {
    val alarm = at(2026, 10, 12, 21, 30)
    val result = plan(ReminderPlanInput(customReminders = listOf(custom(7, alarm)))).single()
    assertEquals(ReminderKey.Custom(7), result.key)
    assertEquals(ReminderKind.CUSTOM, result.kind)
    assertEquals(LocalDateTime(2026, 10, 12, 21, 30), result.fireAt)
    assertEquals(alarm, result.fireAtEpochMillis)
    assertTrue(result.isAbsolute)
    assertFalse(result.repeatsYearly)
    assertEquals("calendar", result.route)
  }

  @Test
  fun wallClockRulesAreNotAbsolute() {
    val input = ReminderPlanInput(
      anniversaries = listOf(anniversary(1, "20/10/2026", daysBefore = 3)),
      milestones = listOf(milestone(2, "25/12/2026")),
      couple = CoupleItem("Hai đứa", "11/09/2023", "Bin"),
      customReminders = listOf(custom(3, at(2026, 10, 12, 21, 30)))
    )
    val result = plan(input)
    assertEquals(5, result.size)
    for (n in result) {
      assertEquals(n.key.stableId, n.key !is ReminderKey.Custom, !n.isAbsolute)
    }
  }

  // ---- các trường hợp bổ sung ----

  @Test
  fun disabledOrPastItemsYieldNothing() {
    val input = ReminderPlanInput(
      anniversaries = listOf(anniversary(1, "20/10/2026", enabled = false, daysBefore = 3)),
      milestones = listOf(
        milestone(2, "25/12/2026", enabled = false),
        milestone(3, "25/12/2026", isPast = true)
      )
    )
    assertEquals(emptyList<PlannedNotification>(), plan(input))
  }

  @Test
  fun unparsableOrBlankDateYieldsNothing() {
    val input = ReminderPlanInput(
      anniversaries = listOf(
        anniversary(1, "", daysBefore = 3),
        anniversary(2, "   ", daysBefore = 3),
        anniversary(3, "không phải ngày", daysBefore = 3),
        anniversary(4, "32/01/2026", daysBefore = 3)
      ),
      milestones = listOf(milestone(5, ""), milestone(6, "abc"), milestone(7, "11/13/2026"))
    )
    assertEquals(emptyList<PlannedNotification>(), plan(input))
    // Sáng 01/01 trước 09:00: nếu ngày hỏng bị thay bằng 01/01 thì mục đó vẫn còn ở tương lai, nên chỉ mốc này mới lộ lỗi đó.
    assertEquals(emptyList<PlannedNotification>(), plan(input, nowMillis = at(2026, 1, 1, 8)))
  }

  @Test
  fun annualItemBoundariesAroundNineAm() {
    val input = ReminderPlanInput(anniversaries = listOf(anniversary(1, "09/10/2026")))

    val before = plan(input, nowMillis = at(2026, 10, 9, 8, 59)).single()
    assertEquals(LocalDateTime(2026, 10, 9, 9, 0), before.fireAt)

    val after = plan(input, nowMillis = at(2026, 10, 9, 10)).single()
    assertEquals(LocalDateTime(2027, 10, 9, 9, 0), after.fireAt)

    // Đúng 09:00:00.000: không còn ở tương lai và cũng không nhảy sang năm sau.
    assertEquals(emptyList<PlannedNotification>(), plan(input, nowMillis = at(2026, 10, 9, 9)))
  }

  @Test
  fun oneOffItemKeepsItsOwnYear() {
    val past = ReminderPlanInput(anniversaries = listOf(anniversary(1, "05/03/2025", annual = false)))
    assertEquals(emptyList<PlannedNotification>(), plan(past))

    val future = ReminderPlanInput(anniversaries = listOf(anniversary(1, "05/03/2027", annual = false)))
    assertEquals(LocalDateTime(2027, 3, 5, 9, 0), plan(future).single().fireAt)
  }

  @Test
  fun advanceFiresNDaysBeforeAndNeverRepeats() {
    val item = anniversary(1, "20/10/2026", daysBefore = 7)
    val result = plan(ReminderPlanInput(anniversaries = listOf(item))).byKey()
    val advance = result.getValue(ReminderKey.Advance(1))
    assertEquals(ReminderKind.ADVANCE, advance.kind)
    assertEquals(LocalDateTime(2026, 10, 13, 9, 0), advance.fireAt)
    assertEquals(at(2026, 10, 13, 9), advance.fireAtEpochMillis)
    assertFalse(advance.repeatsYearly)
    assertTrue(result.getValue(ReminderKey.DayOf(1)).repeatsYearly)
  }

  @Test
  fun advanceIsDroppedButDayOfKeptWhenAdvanceTimeHasPassed() {
    // Ngày D là ngày mai, nhắc trước 3 ngày đã qua: chỉ mất nhắc trước, không nhắc bù.
    val item = anniversary(1, "10/10/2026", daysBefore = 3)
    val result = plan(ReminderPlanInput(anniversaries = listOf(item)))
    assertEquals(1, result.size)
    assertEquals(ReminderKey.DayOf(1), result[0].key)
    assertEquals(LocalDateTime(2026, 10, 10, 9, 0), result[0].fireAt)
  }

  @Test
  fun dayOfClampsMissingDaysIntoTheMonth() {
    val feb29 = ReminderPlanInput(anniversaries = listOf(anniversary(1, "29/02/2024")))
    assertEquals(LocalDateTime(2027, 2, 28, 9, 0), plan(feb29).single().fireAt)
    assertEquals(LocalDateTime(2028, 2, 29, 9, 0), plan(feb29, nowMillis = at(2027, 10, 9, 8)).single().fireAt)

    val day31 = ReminderPlanInput(anniversaries = listOf(anniversary(2, "31/04/2024")))
    assertEquals(LocalDateTime(2027, 4, 30, 9, 0), plan(day31).single().fireAt)
  }

  @Test
  fun repeatsYearlyIsFalseWhenDayCanBeClampedToADifferentDay() {
    val items = listOf(
      anniversary(1, "31/04/2024"),
      anniversary(2, "29/02/2024"),
      anniversary(3, "30/02/2024"),
      anniversary(4, "31/02/2024"),
      anniversary(5, "28/02/2024"),
      anniversary(6, "31/04/2027", annual = false)
    )
    val result = plan(ReminderPlanInput(anniversaries = items)).byKey()
    assertTrue(result.getValue(ReminderKey.DayOf(1)).repeatsYearly)
    assertFalse(result.getValue(ReminderKey.DayOf(2)).repeatsYearly)
    assertFalse(result.getValue(ReminderKey.DayOf(3)).repeatsYearly)
    assertFalse(result.getValue(ReminderKey.DayOf(4)).repeatsYearly)
    assertTrue(result.getValue(ReminderKey.DayOf(5)).repeatsYearly)
    assertFalse(result.getValue(ReminderKey.DayOf(6)).repeatsYearly)
  }

  @Test
  fun userMilestoneIsOneOffAndNeedsEnabledAndNotPast() {
    val input = ReminderPlanInput(
      milestones = listOf(
        milestone(1, "25/12/2026"),
        milestone(2, "25/12/2026", enabled = false),
        milestone(3, "25/12/2026", isPast = true),
        milestone(4, "05/03/2027"),
        milestone(5, "05/03/2025")
      )
    )
    val result = plan(input).byKey()
    assertEquals(setOf<ReminderKey>(ReminderKey.UserMilestone(1), ReminderKey.UserMilestone(4)), result.keys)
    val first = result.getValue(ReminderKey.UserMilestone(1))
    assertEquals(ReminderKind.USER_MILESTONE, first.kind)
    assertEquals(LocalDateTime(2026, 12, 25, 9, 0), first.fireAt)
    assertEquals(at(2026, 12, 25, 9), first.fireAtEpochMillis)
    assertFalse(first.repeatsYearly)
    assertEquals(LocalDateTime(2027, 3, 5, 9, 0), result.getValue(ReminderKey.UserMilestone(4)).fireAt)
  }

  @Test
  fun milestoneWithoutYearUsesTheCurrentYear() {
    val input = ReminderPlanInput(milestones = listOf(milestone(1, "11/12"), milestone(2, "11 Tháng 12")))
    val result = plan(input).byKey()
    assertEquals(LocalDateTime(2026, 12, 11, 9, 0), result.getValue(ReminderKey.UserMilestone(1)).fireAt)
    assertEquals(LocalDateTime(2026, 12, 11, 9, 0), result.getValue(ReminderKey.UserMilestone(2)).fireAt)
  }

  @Test
  fun coupleFieldsAreFilledForTheFireDate() {
    val result = plan(ReminderPlanInput(couple = CoupleItem("Hai đứa", "11/09/2023", "Bin"))).single()
    assertEquals(ReminderKey.CoupleAnniversary, result.key)
    assertEquals(ReminderKind.COUPLE_ANNIVERSARY, result.kind)
    assertEquals(LocalDateTime(2027, 9, 11, 9, 0), result.fireAt)
    assertEquals(at(2027, 9, 11, 9), result.fireAtEpochMillis)
    assertEquals("home", result.route)
    assertFalse(result.isAbsolute)
    assertFalse(result.repeatsYearly)
  }

  @Test
  fun coupleStartToday() {
    // Ngày bắt đầu là hôm nay: kỷ niệm đầu tiên là một năm sau, ngày đếm là 366 (365 ngày cộng một).
    val input = ReminderPlanInput(couple = CoupleItem("Hai đứa", "09/10/2026", ""))
    val result = plan(input).single()
    assertEquals(LocalDateTime(2027, 10, 9, 9, 0), result.fireAt)
    assertTrue(result.body, result.body.contains("Chặng đường 366 ngày"))
  }

  @Test
  fun coupleStartInTheFuture() {
    // Ngày bắt đầu 15/03/2027: kỷ niệm là 15/03/2028; khoảng đó có 29/2/2028 nên 366 ngày, cộng một.
    val input = ReminderPlanInput(couple = CoupleItem("Hai đứa", "15/03/2027", ""))
    val result = plan(input).single()
    assertEquals(LocalDateTime(2028, 3, 15, 9, 0), result.fireAt)
    assertTrue(result.body, result.body.contains("Chặng đường 367 ngày"))
  }

  @Test
  fun coupleWithoutUsableStartDateYieldsNothing() {
    assertEquals(emptyList<PlannedNotification>(), plan(ReminderPlanInput(couple = null)))
    assertEquals(
      emptyList<PlannedNotification>(),
      plan(ReminderPlanInput(couple = CoupleItem("Hai đứa", "11/09", "Bin")))
    )
    assertEquals(
      emptyList<PlannedNotification>(),
      plan(ReminderPlanInput(couple = CoupleItem("Hai đứa", "abc", "Bin")))
    )
    assertEquals(
      emptyList<PlannedNotification>(),
      plan(ReminderPlanInput(couple = CoupleItem("Hai đứa", "", "Bin")))
    )
  }

  @Test
  fun hugeDaysBeforeDoesNotThrowAndKeepsTheDayOf() {
    val items = listOf(anniversary(1, "20/10/2026", daysBefore = Int.MAX_VALUE))
    val result = plan(ReminderPlanInput(anniversaries = items))
    assertEquals(1, result.size)
    assertEquals(ReminderKey.DayOf(1), result[0].key)
    assertEquals(ReminderKind.DAY_OF, result[0].kind)
  }

  @Test
  fun emptyInputYieldsEmptyList() {
    assertEquals(emptyList<PlannedNotification>(), plan(ReminderPlanInput()))
  }

  @Test
  fun keysKindsAndRoutesOfEveryKind() {
    val input = ReminderPlanInput(
      anniversaries = listOf(anniversary(1, "20/10/2026", daysBefore = 3)),
      milestones = listOf(milestone(2, "25/12/2026")),
      couple = CoupleItem("Hai đứa", "11/09/2023", "Bin"),
      customReminders = listOf(custom(3, at(2026, 10, 12, 21, 30)))
    )
    val result = plan(input).byKey()

    val advance = result.getValue(ReminderKey.Advance(1))
    assertEquals(ReminderKind.ADVANCE, advance.kind)
    assertEquals("calendar", advance.route)

    val dayOf = result.getValue(ReminderKey.DayOf(1))
    assertEquals(ReminderKind.DAY_OF, dayOf.kind)
    assertEquals("calendar", dayOf.route)
    assertEquals(at(2026, 10, 20, 9), dayOf.fireAtEpochMillis)

    val ms = result.getValue(ReminderKey.UserMilestone(2))
    assertEquals(ReminderKind.USER_MILESTONE, ms.kind)
    assertEquals("calendar", ms.route)

    val couple = result.getValue(ReminderKey.CoupleAnniversary)
    assertEquals(ReminderKind.COUPLE_ANNIVERSARY, couple.kind)
    assertEquals("home", couple.route)

    val custom = result.getValue(ReminderKey.Custom(3))
    assertEquals(ReminderKind.CUSTOM, custom.kind)
    assertEquals("calendar", custom.route)
  }

  @Test
  fun vietnameseTextsCarryEachKindsArgs() {
    val input = ReminderPlanInput(
      anniversaries = listOf(anniversary(1, "20/10/2026", title = "Sinh nhật", daysBefore = 3)),
      milestones = listOf(milestone(2, "25/12/2026", title = "Du lịch")),
      couple = CoupleItem("Hai đứa", "11/09/2023", "Bin"),
      customReminders = listOf(custom(3, at(2026, 10, 12, 21, 30), title = "Uống thuốc", details = "Sau bữa ăn"))
    )
    val result = plan(input).byKey()

    val advance = result.getValue(ReminderKey.Advance(1))
    assertTrue(advance.title, advance.title.endsWith("Sắp Đến: Sinh nhật"))
    assertTrue(advance.body, advance.body.contains("Còn 3 ngày nữa là đến ngày kỷ niệm 'Sinh nhật' (20/10/2026)"))

    val dayOf = result.getValue(ReminderKey.DayOf(1))
    assertTrue(dayOf.title, dayOf.title.endsWith("Hôm Nay: Sinh nhật!"))
    assertTrue(dayOf.body, dayOf.body.contains("'Sinh nhật' (20/10/2026)"))

    val ms = result.getValue(ReminderKey.UserMilestone(2))
    assertTrue(ms.title, ms.title.endsWith("Cột Mốc Tình Yêu: Du lịch"))
    assertTrue(ms.body, ms.body.contains("'Du lịch' (25/12/2026)"))

    val couple = result.getValue(ReminderKey.CoupleAnniversary)
    assertTrue(couple.title, couple.title.contains("Kỷ Niệm Ngày Yêu Nhau: Hai đứa"))
    assertTrue(couple.body, couple.body.contains("Chặng đường 1462 ngày"))

    val custom = result.getValue(ReminderKey.Custom(3))
    assertEquals("Uống thuốc", custom.title)
    assertEquals("Sau bữa ăn", custom.body)
  }

  @Test
  fun englishFlagSwitchesToEnglishTexts() {
    val input = ReminderPlanInput(
      anniversaries = listOf(anniversary(1, "20/10/2026", title = "Birthday", daysBefore = 3)),
      milestones = listOf(milestone(2, "25/12/2026", title = "Trip")),
      couple = CoupleItem("Us", "11/09/2023", "Bin"),
      customReminders = listOf(custom(3, at(2026, 10, 12, 21, 30), title = "Pills", details = "After meals")),
      isEnglish = true
    )
    val result = plan(input).byKey()

    val advance = result.getValue(ReminderKey.Advance(1))
    assertTrue(advance.title, advance.title.contains("Coming Up: Birthday"))
    assertTrue(advance.body, advance.body.contains("Only 3 days left until your anniversary 'Birthday' (20/10/2026)"))

    val dayOf = result.getValue(ReminderKey.DayOf(1))
    assertTrue(dayOf.title, dayOf.title.contains("Today: Birthday!"))

    val ms = result.getValue(ReminderKey.UserMilestone(2))
    assertTrue(ms.title, ms.title.contains("Love Milestone: Trip"))
    assertTrue(ms.body, ms.body.contains("'Trip' (25/12/2026)"))

    val couple = result.getValue(ReminderKey.CoupleAnniversary)
    assertTrue(couple.title, couple.title.contains("Our Love Anniversary: Us"))
    assertTrue(couple.body, couple.body.contains("1462 sweet days"))

    val custom = result.getValue(ReminderKey.Custom(3))
    assertEquals("Pills", custom.title)
    assertEquals("After meals", custom.body)
  }

  @Test
  fun theZoneIsHonoured() {
    val input = ReminderPlanInput(anniversaries = listOf(anniversary(1, "09/10/2026")))
    val inVietnam = plan(input, nowMillis = pinned, inZone = zone).single()
    val inLosAngeles = plan(input, nowMillis = pinned, inZone = losAngeles).single()

    assertEquals(LocalDateTime(2026, 10, 9, 9, 0), inVietnam.fireAt)
    assertEquals(at(2026, 10, 9, 9, inZone = zone), inVietnam.fireAtEpochMillis)
    assertEquals(LocalDateTime(2026, 10, 9, 9, 0), inLosAngeles.fireAt)
    assertEquals(at(2026, 10, 9, 9, inZone = losAngeles), inLosAngeles.fireAtEpochMillis)
    assertNotEquals(inVietnam.fireAtEpochMillis, inLosAngeles.fireAtEpochMillis)
  }

  @Test
  fun cadenceKeysDoNotAffectMilestoneCoupleOrCustom() {
    val input = ReminderPlanInput(
      milestones = listOf(milestone(1, "25/12/2026")),
      couple = CoupleItem("Hai đứa", "11/09/2023", "Bin"),
      customReminders = listOf(custom(2, at(2026, 10, 12, 21, 30))),
      disabledCadenceKeys = setOf("exact_day", "7_days", "3_days", "1_day")
    )
    assertEquals(
      setOf(ReminderKey.UserMilestone(1), ReminderKey.CoupleAnniversary, ReminderKey.Custom(2)),
      plan(input).keys()
    )
  }

  @Test
  fun outputOrderIsInputOrderThenMilestonesThenCoupleThenCustom() {
    val input = ReminderPlanInput(
      anniversaries = listOf(
        anniversary(1, "25/10/2026", daysBefore = 3),
        anniversary(2, "20/10/2026", daysBefore = 3)
      ),
      milestones = listOf(milestone(7, "25/12/2026"), milestone(6, "05/03/2027")),
      couple = CoupleItem("Hai đứa", "11/09/2023", "Bin"),
      customReminders = listOf(
        custom(9, at(2026, 12, 1, 8)),
        custom(8, at(2026, 10, 10, 8))
      )
    )
    val expected = listOf<ReminderKey>(
      ReminderKey.Advance(1),
      ReminderKey.DayOf(1),
      ReminderKey.Advance(2),
      ReminderKey.DayOf(2),
      ReminderKey.UserMilestone(7),
      ReminderKey.UserMilestone(6),
      ReminderKey.CoupleAnniversary,
      ReminderKey.Custom(9),
      ReminderKey.Custom(8)
    )
    assertEquals(expected, plan(input).map { it.key })
  }

  @Test
  fun planDoesNotRedactTexts() {
    // Việc che chữ là của bước sau; ở đây tiêu đề người dùng nhập vẫn nằm nguyên trong chữ thông báo.
    val input = ReminderPlanInput(
      anniversaries = listOf(anniversary(1, "20/10/2026", title = "Bí mật")),
      redactContent = true
    )
    val result = plan(input).single()
    assertTrue(result.title, result.title.contains("Bí mật"))
  }
}
