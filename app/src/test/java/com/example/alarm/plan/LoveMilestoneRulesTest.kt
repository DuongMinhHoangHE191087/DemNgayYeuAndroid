package com.example.alarm.plan

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoveMilestoneRulesTest {

  private val zone = TimeZone.of("Asia/Ho_Chi_Minh")
  private val losAngeles = TimeZone.of("America/Los_Angeles")

  // Mốc "bây giờ" mặc định: 2026-01-01 08:00 ở Hồ Chí Minh.
  private val defaultNow = 1767229200000L

  private val nineAm = LocalTime(9, 0)
  private val eightThirty = LocalTime(8, 30)

  private fun at(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int = 0,
    second: Int = 0,
    inZone: TimeZone = zone
  ): Long = toEpochMillis(LocalDateTime(year, month, day, hour, minute, second), inZone)

  private fun input(
    start: String = "01/01/2026",
    partner: String = "Linh",
    english: Boolean = false,
    enabled: Boolean = true,
    redact: Boolean = false
  ) = ReminderPlanInput(
    couple = CoupleItem("Hai đứa", start, partner),
    anniversaryNotificationsEnabled = enabled,
    isEnglish = english,
    redactContent = redact
  )

  private fun plan(
    input: ReminderPlanInput,
    nowMillis: Long = defaultNow,
    inZone: TimeZone = zone
  ): List<PlannedNotification> = LoveMilestoneRules.plan(input, nowMillis, inZone)

  private fun List<PlannedNotification>.byKey(): Map<ReminderKey, PlannedNotification> = associateBy { it.key }

  private fun msIdOf(key: ReminderKey): Long? = when (key) {
    is ReminderKey.LoveMilestoneThreeDays -> key.msId
    is ReminderKey.LoveMilestoneOneDay -> key.msId
    is ReminderKey.LoveMilestoneDayOf -> key.msId
    else -> null
  }

  private fun List<PlannedNotification>.annual(): List<PlannedNotification> =
    filter { (msIdOf(it.key) ?: 0L) >= 10000L }

  private fun List<PlannedNotification>.forMs(msId: Long): List<PlannedNotification> =
    filter { msIdOf(it.key) == msId }

  private fun List<PlannedNotification>.annualIds(): List<Long> = annual().mapNotNull { msIdOf(it.key) }.distinct()

  private fun threeDays(id: Long) = ReminderKey.LoveMilestoneThreeDays(id)
  private fun oneDay(id: Long) = ReminderKey.LoveMilestoneOneDay(id)
  private fun dayOf(id: Long) = ReminderKey.LoveMilestoneDayOf(id)

  private fun keysOf(id: Long): List<ReminderKey> = listOf(threeDays(id), oneDay(id), dayOf(id))

  private data class Row(val days: Long, val day: LocalDate, val three: LocalDate, val one: LocalDate)

  private val table = listOf(
    Row(30, LocalDate(2026, 1, 30), LocalDate(2026, 1, 27), LocalDate(2026, 1, 29)),
    Row(50, LocalDate(2026, 2, 19), LocalDate(2026, 2, 16), LocalDate(2026, 2, 18)),
    Row(100, LocalDate(2026, 4, 10), LocalDate(2026, 4, 7), LocalDate(2026, 4, 9)),
    Row(150, LocalDate(2026, 5, 30), LocalDate(2026, 5, 27), LocalDate(2026, 5, 29)),
    Row(200, LocalDate(2026, 7, 19), LocalDate(2026, 7, 16), LocalDate(2026, 7, 18)),
    Row(300, LocalDate(2026, 10, 27), LocalDate(2026, 10, 24), LocalDate(2026, 10, 26)),
    Row(365, LocalDate(2026, 12, 31), LocalDate(2026, 12, 28), LocalDate(2026, 12, 30)),
    Row(500, LocalDate(2027, 5, 15), LocalDate(2027, 5, 12), LocalDate(2027, 5, 14)),
    Row(730, LocalDate(2027, 12, 31), LocalDate(2027, 12, 28), LocalDate(2027, 12, 30)),
    Row(1000, LocalDate(2028, 9, 26), LocalDate(2028, 9, 23), LocalDate(2028, 9, 25)),
    Row(1500, LocalDate(2030, 2, 8), LocalDate(2030, 2, 5), LocalDate(2030, 2, 7)),
    Row(2000, LocalDate(2031, 6, 23), LocalDate(2031, 6, 20), LocalDate(2031, 6, 22))
  )

  // ---- 5 tên bắt buộc theo dòng bảng của kế hoạch ----

  @Test
  fun thirtyDayMilestoneFallsOnStartPlusTwentyNineDays() {
    val dayOfItem = plan(input()).byKey().getValue(dayOf(30))
    assertEquals(LocalDateTime(2026, 1, 30, 8, 30), dayOfItem.fireAt)
    assertEquals(1769736600000L, dayOfItem.fireAtEpochMillis)
    assertTrue(dayOfItem.body.contains("30/01/2026"))
  }

  @Test
  fun eachMilestoneHasThreeAlertsAtNineNineAndEightThirty() {
    val items = plan(input())
    val byKey = items.byKey()
    assertEquals(1769479200000L, byKey.getValue(threeDays(30)).fireAtEpochMillis)
    assertEquals(1769652000000L, byKey.getValue(oneDay(30)).fireAtEpochMillis)
    assertEquals(1769736600000L, byKey.getValue(dayOf(30)).fireAtEpochMillis)
    assertEquals(LocalDateTime(2026, 1, 27, 9, 0), byKey.getValue(threeDays(30)).fireAt)
    assertEquals(LocalDateTime(2026, 1, 29, 9, 0), byKey.getValue(oneDay(30)).fireAt)
    assertEquals(LocalDateTime(2026, 1, 30, 8, 30), byKey.getValue(dayOf(30)).fireAt)

    assertEquals(48, items.size)
    for (item in items) {
      when (item.key) {
        is ReminderKey.LoveMilestoneDayOf -> assertEquals(eightThirty, item.fireAt.time)
        else -> assertEquals(nineAm, item.fireAt.time)
      }
    }
  }

  @Test
  fun pastAlertsAreDropped() {
    val items = plan(input(), nowMillis = at(2026, 1, 28, 10))
    val byKey = items.byKey()
    assertFalse(byKey.containsKey(threeDays(30)))
    assertTrue(byKey.containsKey(oneDay(30)))
    assertTrue(byKey.containsKey(dayOf(30)))
    assertEquals(3, items.forMs(50).size)

    val afterDayOf = plan(input(), nowMillis = at(2026, 1, 30, 9))
    assertTrue(afterDayOf.forMs(30).isEmpty())
  }

  @Test
  fun annualMilestonesCoverFiveYears() {
    val items = plan(input(start = "25/12/2022"), nowMillis = at(2026, 10, 9, 8))
    assertEquals(listOf(10004L, 10005L, 10006L, 10007L, 10008L), items.annualIds())
    assertEquals(15, items.annual().size)
    val byKey = items.byKey()
    for (i in 0..4) {
      val id = 10004L + i
      val year = 2026 + i
      assertEquals(LocalDateTime(year, 12, 22, 9, 0), byKey.getValue(threeDays(id)).fireAt)
      assertEquals(LocalDateTime(year, 12, 24, 9, 0), byKey.getValue(oneDay(id)).fireAt)
      assertEquals(LocalDateTime(year, 12, 25, 8, 30), byKey.getValue(dayOf(id)).fireAt)
    }

    // Năm bắt đầu 2026: yearsInLove = 0 bị bỏ, còn bốn năm.
    val sameYear = plan(input(start = "15/03/2026"), nowMillis = at(2026, 10, 9, 8))
    assertEquals(listOf(10001L, 10002L, 10003L, 10004L), sameYear.annualIds())
    assertEquals(12, sameYear.annual().size)
  }

  @Test
  fun blankOrUnparsableStartDateYieldsNothing() {
    for (start in listOf("", "   ", "abc", "32/01/2026", "15/13/2026", "00/05/2026")) {
      assertTrue("start=[$start]", plan(input(start = start)).isEmpty())
    }
  }

  // ---- Hợp đồng bổ sung ----

  @Test
  fun masterSwitchOffYieldsNothing() {
    assertTrue(plan(input(enabled = false)).isEmpty())
  }

  @Test
  fun noCoupleYieldsNothing() {
    assertTrue(plan(ReminderPlanInput(couple = null)).isEmpty())
  }

  @Test
  fun otherListsAndDisabledCadenceKeysAreIgnored() {
    val base = plan(input())
    assertEquals(48, base.size)
    val noisy = input().copy(
      anniversaries = listOf(AnniversaryItem(1, "Sinh nhật", "20/01/2026", true, true, 3)),
      milestones = listOf(MilestoneItem(2, "Du lịch", "25/01/2026", true, false)),
      customReminders = listOf(CustomReminderItem(3, "Uống thuốc", "Sau bữa ăn", at(2026, 1, 5, 8))),
      disabledCadenceKeys = setOf("exact_day", "7_days", "3_days", "1_day")
    )
    assertEquals(base, plan(noisy))
  }

  @Test
  fun startDateWithoutYearYieldsNothing() {
    assertTrue(plan(input(start = "15/06")).isEmpty())
  }

  @Test
  fun everyRowOfTheDayCountTableHasThreeAlertsOnTheRightDates() {
    val byKey = plan(input()).byKey()
    assertEquals(12, table.size)
    for (row in table) {
      assertEquals(
        "three ${row.days}",
        LocalDateTime(row.three, nineAm), byKey.getValue(threeDays(row.days)).fireAt
      )
      assertEquals(
        "one ${row.days}",
        LocalDateTime(row.one, nineAm), byKey.getValue(oneDay(row.days)).fireAt
      )
      assertEquals(
        "dayOf ${row.days}",
        LocalDateTime(row.day, eightThirty), byKey.getValue(dayOf(row.days)).fireAt
      )
      assertEquals(
        "epoch ${row.days}",
        toEpochMillis(LocalDateTime(row.day, eightThirty), zone), byKey.getValue(dayOf(row.days)).fireAtEpochMillis
      )
    }
  }

  @Test
  fun alertAtExactlyNowIsDropped() {
    assertFalse(plan(input(), nowMillis = 1769736600000L).byKey().containsKey(dayOf(30)))
    assertTrue(plan(input(), nowMillis = 1769736599999L).byKey().containsKey(dayOf(30)))
  }

  @Test
  fun eachAlertIsFilteredOnItsOwnTime() {
    val items = plan(input(), nowMillis = 1769731200000L)
    assertEquals(listOf<ReminderKey>(dayOf(30)), items.forMs(30).map { it.key })
  }

  @Test
  fun leapDayStartClampsAnnualDatesToFebruaryTwentyEight() {
    val items = plan(input(start = "29/02/2024"), nowMillis = at(2026, 10, 9, 8))
    assertEquals(listOf(10003L, 10004L, 10005L, 10006L), items.annualIds())
    val byKey = items.byKey()
    assertEquals(LocalDateTime(2027, 2, 28, 8, 30), byKey.getValue(dayOf(10003)).fireAt)
    assertEquals(LocalDateTime(2028, 2, 29, 8, 30), byKey.getValue(dayOf(10004)).fireAt)
    assertEquals(LocalDateTime(2029, 2, 28, 8, 30), byKey.getValue(dayOf(10005)).fireAt)
    assertEquals(LocalDateTime(2030, 2, 28, 8, 30), byKey.getValue(dayOf(10006)).fireAt)
  }

  @Test
  fun impossibleStartDayClampsToMonthEnd() {
    val items = plan(input(start = "31/04/2024"), nowMillis = at(2024, 5, 1, 8))
    val byKey = items.byKey()
    assertEquals(1716688800000L, byKey.getValue(threeDays(30)).fireAtEpochMillis)
    assertEquals(1716861600000L, byKey.getValue(oneDay(30)).fireAtEpochMillis)
    assertEquals(1716946200000L, byKey.getValue(dayOf(30)).fireAtEpochMillis)
    assertEquals(LocalDateTime(2024, 5, 26, 9, 0), byKey.getValue(threeDays(30)).fireAt)
    assertEquals(LocalDateTime(2024, 5, 28, 9, 0), byKey.getValue(oneDay(30)).fireAt)
    assertEquals(LocalDateTime(2024, 5, 29, 8, 30), byKey.getValue(dayOf(30)).fireAt)
    assertEquals(LocalDateTime(2025, 4, 30, 8, 30), byKey.getValue(dayOf(10001)).fireAt)
    assertTrue(byKey.getValue(dayOf(10001)).body.contains("30/04/2025"))
  }

  @Test
  fun dayOfBodyNamesThePartner() {
    assertTrue(plan(input()).byKey().getValue(dayOf(30)).body.contains("Linh"))
    assertTrue(plan(input(english = true)).byKey().getValue(dayOf(30)).body.contains("Linh"))
  }

  @Test
  fun blankPartnerNameGivesTheGenericWord() {
    assertTrue(plan(input(partner = "")).byKey().getValue(dayOf(30)).body.contains("người ấy"))
    assertTrue(plan(input(partner = "  ", english = true)).byKey().getValue(dayOf(30)).body.contains("your partner"))
  }

  @Test
  fun dateTextIsZeroPadded() {
    val byKey = plan(input()).byKey()
    assertTrue(byKey.getValue(threeDays(1500)).body.contains("08/02/2030"))
    assertTrue(byKey.getValue(dayOf(1500)).body.contains("08/02/2030"))
  }

  @Test
  fun dateTextPadsTheYearToFourDigits() {
    // Năm 99 chỉ để kiểm định dạng "yyyy"; dùng UTC cho khỏi dính offset lịch sử của múi giờ thật.
    val items = plan(input(start = "15/03/0099"), nowMillis = at(50, 1, 1, 8, inZone = TimeZone.UTC), inZone = TimeZone.UTC)
    assertTrue(items.byKey().getValue(dayOf(30)).body.contains("13/04/0099"))
  }

  @Test
  fun keysKindsRoutesAndFlagsOfTheThirtyDayMilestone() {
    val byKey = plan(input()).byKey()
    val three = byKey.getValue(ReminderKey.LoveMilestoneThreeDays(30L))
    val one = byKey.getValue(ReminderKey.LoveMilestoneOneDay(30L))
    val day = byKey.getValue(ReminderKey.LoveMilestoneDayOf(30L))
    assertTrue(three.key is ReminderKey.LoveMilestoneThreeDays)
    assertTrue(one.key is ReminderKey.LoveMilestoneOneDay)
    assertTrue(day.key is ReminderKey.LoveMilestoneDayOf)
    assertEquals(ReminderKind.LOVE_MILESTONE_THREE_DAYS, three.kind)
    assertEquals(ReminderKind.LOVE_MILESTONE_ONE_DAY, one.kind)
    assertEquals(ReminderKind.LOVE_MILESTONE_DAY_OF, day.kind)
    assertEquals("calendar", three.route)
    assertEquals("calendar", one.route)
    assertEquals("home", day.route)
    for (item in listOf(three, one, day)) {
      assertFalse(item.repeatsYearly)
      assertFalse(item.isAbsolute)
    }
    assertEquals("lms3.30", three.key.stableId)
    assertEquals("lms1.30", one.key.stableId)
    assertEquals("lmsd.30", day.key.stableId)
  }

  @Test
  fun vietnameseNamesOfAllDayCountMilestones() {
    val names = listOf(
      30L to "🌱 1 Tháng Yêu Nhau (30 Ngày)",
      50L to "🌸 50 Ngày Gắn Kết",
      100L to "💎 Bách Nhật Yêu (100 Ngày)",
      150L to "🎀 150 Ngày Đồng Điệu",
      200L to "🌹 200 Ngày Nồng Nàn",
      300L to "✨ 300 Ngày Chung Đôi",
      365L to "🎂 Tròn 1 Năm Yêu (365 Ngày)",
      500L to "🌿 500 Ngày Tri Kỷ",
      730L to "💍 Tròn 2 Năm Yêu (730 Ngày)",
      1000L to "👑 1000 Ngày Trọn Vẹn",
      1500L to "🏆 1500 Ngày Vững Bền",
      2000L to "🌟 2000 Ngày Hạnh Phúc"
    )
    val byKey = plan(input()).byKey()
    for ((id, name) in names) {
      assertTrue("vi $id", byKey.getValue(dayOf(id)).title.contains(name))
    }
    assertTrue(byKey.getValue(threeDays(30)).title.contains("Sắp Đến: 🌱 1 Tháng Yêu Nhau (30 Ngày)!"))
    assertTrue(byKey.getValue(oneDay(30)).title.contains("Ngày Mai: 🌱 1 Tháng Yêu Nhau (30 Ngày)!"))
    assertTrue(byKey.getValue(dayOf(30)).title.contains("Hôm Nay: 🌱 1 Tháng Yêu Nhau (30 Ngày)!"))
  }

  @Test
  fun englishNamesOfAllDayCountMilestones() {
    val names = listOf(
      30L to "🌱 1 Month in Love (30 Days)",
      50L to "🌸 50 Days Together",
      100L to "💎 100 Days in Love",
      150L to "🎀 150 Days in Harmony",
      200L to "🌹 200 Days of Passion",
      300L to "✨ 300 Days as a Couple",
      365L to "🎂 1 Year in Love (365 Days)",
      500L to "🌿 500 Days of Soulmates",
      730L to "💍 2 Years in Love (730 Days)",
      1000L to "👑 1000 Days of Fulfillment",
      1500L to "🏆 1500 Days Strong",
      2000L to "🌟 2000 Days of Happiness"
    )
    val byKey = plan(input(english = true)).byKey()
    for ((id, name) in names) {
      assertTrue("en $id", byKey.getValue(dayOf(id)).title.contains(name))
    }
    assertTrue(byKey.getValue(threeDays(30)).title.contains("Coming Up"))
    assertTrue(byKey.getValue(oneDay(30)).title.contains("Tomorrow"))
    assertTrue(byKey.getValue(dayOf(30)).title.contains("Today"))
  }

  @Test
  fun annualNamesCarryTheYearCount() {
    // Mốc 2025-12-01: năm 2026 là kỷ niệm thứ 5 của ngày bắt đầu 01/01/2021.
    val now = at(2025, 12, 1, 8)
    val vi = plan(input(start = "01/01/2021"), nowMillis = now).byKey()
    assertTrue(vi.getValue(dayOf(10005)).title.contains("🎉 Kỷ Niệm 5 Năm Yêu Nhau"))
    assertTrue(vi.getValue(threeDays(10005)).title.endsWith("🎉 Kỷ Niệm 5 Năm Yêu Nhau!"))
    val en = plan(input(start = "01/01/2021", english = true), nowMillis = now).byKey()
    assertTrue(en.getValue(dayOf(10005)).title.contains("🎉 5-Year Love Anniversary"))
    assertTrue(en.getValue(oneDay(10005)).title.endsWith("🎉 5-Year Love Anniversary!"))
  }

  @Test
  fun outputOrderIsTableThenAnnualYearsThreeAlertsEach() {
    val expected = listOf(30L, 50L, 100L, 150L, 200L, 300L, 365L, 500L, 730L, 1000L, 1500L, 2000L)
      .plus(listOf(10001L, 10002L, 10003L, 10004L))
      .flatMap { keysOf(it) }
    assertEquals(expected, plan(input()).map { it.key })
  }

  @Test
  fun fireTimesFollowDaylightSavingInTheZone() {
    val items = plan(input(start = "14/03/2020"), nowMillis = at(2026, 10, 9, 8, inZone = losAngeles), inZone = losAngeles)
    val byKey = items.byKey()
    assertEquals(1804784400000L, byKey.getValue(threeDays(10007)).fireAtEpochMillis)
    assertEquals(1804957200000L, byKey.getValue(oneDay(10007)).fireAtEpochMillis)
    assertEquals(1805038200000L, byKey.getValue(dayOf(10007)).fireAtEpochMillis)
    assertEquals(LocalDateTime(2027, 3, 11, 9, 0), byKey.getValue(threeDays(10007)).fireAt)
    assertEquals(LocalDateTime(2027, 3, 13, 9, 0), byKey.getValue(oneDay(10007)).fireAt)
    assertEquals(LocalDateTime(2027, 3, 14, 8, 30), byKey.getValue(dayOf(10007)).fireAt)
  }

  @Test
  fun currentYearComesFromTheZone() {
    // 2027-01-01 07:30 UTC: đã sang năm mới ở Hồ Chí Minh nhưng còn 2026-12-31 ở Los Angeles.
    val now = 1798788600000L
    val start = input(start = "15/06/2020")
    assertEquals(listOf(10007L, 10008L, 10009L, 10010L, 10011L), plan(start, now, zone).annualIds())
    assertEquals(listOf(10007L, 10008L, 10009L, 10010L), plan(start, now, losAngeles).annualIds())
  }

  @Test
  fun redactContentIsIgnored() {
    val day = plan(input(redact = true)).byKey().getValue(dayOf(30))
    assertTrue(day.title.contains("🌱 1 Tháng Yêu Nhau (30 Ngày)"))
    assertTrue(day.body.contains("Linh"))
  }
}
