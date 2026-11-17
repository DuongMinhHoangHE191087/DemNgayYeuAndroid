package com.example.alarm.plan

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlannerTest {

  private val hcm = TimeZone.of("Asia/Ho_Chi_Minh")
  private val newYork = TimeZone.of("America/New_York")
  private val losAngeles = TimeZone.of("America/Los_Angeles")

  // Mốc "bây giờ" của bộ A: 2026-01-01 08:00 ở Hồ Chí Minh.
  private val nowA = 1767229200000L

  // Mốc "bây giờ" của bộ B và M: 2026-10-09 08:00 ở Hồ Chí Minh.
  private val nowB = 1791507600000L

  private fun anniversary(
    id: Long,
    dateText: String,
    title: String = "Sinh nhật",
    annual: Boolean = false,
    daysBefore: Int = 0
  ) = AnniversaryItem(id, title, dateText, annual, true, daysBefore)

  private fun custom(id: Long, alarmEpochMillis: Long) =
    CustomReminderItem(id, "Uống thuốc", "Sau bữa ăn", alarmEpochMillis)

  private fun plan(
    input: ReminderPlanInput,
    now: Long,
    maxItems: Int = 60,
    zone: TimeZone = hcm
  ): ReminderPlan = ReminderPlanner.plan(input, now, zone, maxItems)

  private fun ids(plan: ReminderPlan): List<String> = plan.items.map { it.key.stableId }

  private fun byKey(plan: ReminderPlan): Map<String, PlannedNotification> = plan.items.associateBy { it.key.stableId }

  // Bộ A "window": 49 mốc tình yêu và ngày yêu nhau cộng 20 nhắc tùy chỉnh, mỗi ngày một cái, tổng 69.
  private fun fixtureA(redact: Boolean = false) = ReminderPlanInput(
    couple = CoupleItem("Hai đứa", "01/01/2026", "Linh"),
    customReminders = (1L..20L).map { k -> custom(k, 1956538800000L + (k - 1) * 86400000L) },
    redactContent = redact
  )

  // Bộ B "repeat": bốn ngày kỷ niệm hằng năm, liệt kê theo thứ tự mã.
  private fun fixtureB() = ReminderPlanInput(
    anniversaries = listOf(
      anniversary(1, "15/03/2020", annual = true),
      anniversary(2, "10/01/2019", annual = true),
      anniversary(3, "01/06/2018", annual = true),
      anniversary(4, "20/11/2025", annual = true)
    )
  )

  // Bộ M "mixed": đủ mọi loại nguồn, có ba nhắc tùy chỉnh trong quá khứ hoặc đúng bây giờ.
  private fun fixtureM(
    redact: Boolean = false,
    english: Boolean = false,
    switchOn: Boolean = true
  ) = ReminderPlanInput(
    anniversaries = listOf(
      anniversary(1, "25/12/2026", annual = true, daysBefore = 7),
      anniversary(2, "01/01/2020", title = "Cưới", daysBefore = 3)
    ),
    milestones = listOf(MilestoneItem(3, "Du lịch Đà Lạt", "05/02/2027", true, false)),
    couple = CoupleItem("Hai đứa", "15/06/2020", "Linh"),
    customReminders = listOf(
      custom(5, 1791601200000L),
      custom(6, 1791504000000L),
      custom(7, 1791507600000L)
    ),
    anniversaryNotificationsEnabled = switchOn,
    isEnglish = english,
    redactContent = redact
  )

  // 17 khóa của bộ M theo thứ tự giờ bắn.
  private val mKeys = listOf(
    "custom.5", "adv.1", "dayof.1", "ums.3",
    "lms3.10007", "lms1.10007", "lmsd.10007", "couple",
    "lms3.10008", "lms1.10008", "lmsd.10008",
    "lms3.10009", "lms1.10009", "lmsd.10009",
    "lms3.10010", "lms1.10010", "lmsd.10010"
  )

  private val mEpochs = listOf(
    1791601200000L, 1797559200000L, 1798164000000L, 1801792800000L,
    1812765600000L, 1812938400000L, 1813023000000L, 1813024800000L,
    1844388000000L, 1844560800000L, 1844645400000L,
    1875924000000L, 1876096800000L, 1876181400000L,
    1907460000000L, 1907632800000L, 1907717400000L
  )

  // Các mốc kiểm của bộ A: chỉ số trong danh sách đã sắp, khóa, giờ bắn.
  private val aCheckpoints = listOf(
    Triple(0, "lms3.30", 1769479200000L),
    Triple(1, "lms1.30", 1769652000000L),
    Triple(2, "lmsd.30", 1769736600000L),
    Triple(3, "lms3.50", 1771207200000L),
    Triple(4, "lms1.50", 1771380000000L),
    Triple(5, "lmsd.50", 1771464600000L),
    Triple(6, "lms3.100", 1775527200000L),
    Triple(7, "lms1.100", 1775700000000L),
    Triple(8, "lmsd.100", 1775784600000L),
    Triple(9, "lms3.150", 1779847200000L),
    Triple(10, "lms1.150", 1780020000000L),
    Triple(23, "lmsd.10001", 1798767000000L),
    Triple(24, "couple", 1798768800000L),
    Triple(25, "lms3.500", 1810087200000L),
    Triple(47, "lms1.2000", 1939860000000L),
    Triple(48, "lmsd.2000", 1939944600000L),
    Triple(49, "custom.1", 1956538800000L),
    Triple(59, "custom.11", 1957402800000L)
  )

  // ---- 10 tên bắt buộc theo dòng bảng của kế hoạch ----

  @Test
  fun windowCutsToSixtyInTimeOrder() {
    val result = plan(fixtureA(), nowA, maxItems = 60)
    assertEquals(60, result.items.size)
    val times = result.items.map { it.fireAtEpochMillis }
    for (i in 1 until times.size) assertTrue("index $i", times[i] > times[i - 1])
    for ((index, key, epoch) in aCheckpoints) {
      assertEquals("key at $index", key, result.items[index].key.stableId)
      assertEquals("epoch at $index", epoch, result.items[index].fireAtEpochMillis)
    }
    val keys = ids(result).toSet()
    assertTrue("custom.11" in keys)
    for (k in 12..20) assertFalse("custom.$k", "custom.$k" in keys)
  }

  @Test
  fun repeatingItemCountsOneAndSortsByNextOccurrence() {
    val expectedKeys = listOf("dayof.4", "dayof.2", "dayof.1", "dayof.3")
    val expectedEpochs = listOf(1795140000000L, 1799546400000L, 1805076000000L, 1811815200000L)

    val all = plan(fixtureB(), nowB, maxItems = 60)
    assertEquals(expectedKeys, ids(all))
    assertEquals(expectedEpochs, all.items.map { it.fireAtEpochMillis })
    assertTrue(all.items.all { it.repeatsYearly })
    assertEquals(0, all.droppedCount)

    val cut = plan(fixtureB(), nowB, maxItems = 3)
    assertEquals(expectedKeys.take(3), ids(cut))
    assertEquals(1, cut.droppedCount)
    assertEquals(1805076000000L, cut.horizonEpochMillis)
  }

  @Test
  fun keysAreUnique() {
    val a = plan(fixtureA(), nowA, maxItems = 100)
    assertEquals(69, a.items.size)
    assertEquals(69, ids(a).toSet().size)

    val m = plan(fixtureM(), nowB, maxItems = 60)
    assertEquals(17, m.items.size)
    assertEquals(17, ids(m).toSet().size)
  }

  @Test
  fun allFireTimesAreInTheFuture() {
    val m = plan(fixtureM(), nowB, maxItems = 60)
    assertEquals(mKeys, ids(m))
    assertTrue(m.items.all { it.fireAtEpochMillis > nowB })
    val keys = ids(m).toSet()
    for (past in listOf("adv.2", "dayof.2", "custom.6", "custom.7")) assertFalse(past, past in keys)

    val a = plan(fixtureA(), nowA, maxItems = 60)
    assertTrue(a.items.all { it.fireAtEpochMillis > nowA })
  }

  @Test
  fun droppedCountReportsTheCut() {
    val sixty = plan(fixtureA(), nowA, maxItems = 60)
    assertEquals(9, sixty.droppedCount)
    assertEquals(60, sixty.items.size)

    val all = plan(fixtureA(), nowA, maxItems = 69)
    assertEquals(0, all.droppedCount)
    assertEquals(69, all.items.size)

    val ten = plan(fixtureA(), nowA, maxItems = 10)
    assertEquals(59, ten.droppedCount)
    assertEquals(10, ten.items.size)
    for ((index, key, epoch) in aCheckpoints.filter { it.first < 10 }) {
      assertEquals(key, ten.items[index].key.stableId)
      assertEquals(epoch, ten.items[index].fireAtEpochMillis)
    }
  }

  @Test
  fun horizonIsLastScheduledFire() {
    val a = plan(fixtureA(), nowA, maxItems = 60)
    assertEquals(1957402800000L, a.horizonEpochMillis)
    assertEquals(a.items.last().fireAtEpochMillis, a.horizonEpochMillis)

    val m = plan(fixtureM(), nowB, maxItems = 60)
    assertEquals(1907717400000L, m.horizonEpochMillis)
    assertEquals(0, m.droppedCount)

    val b = plan(fixtureB(), nowB, maxItems = 3)
    assertEquals(1805076000000L, b.horizonEpochMillis)

    assertNull(plan(ReminderPlanInput(), nowB, maxItems = 60).horizonEpochMillis)
  }

  @Test
  fun redactContentHidesTitlesAndNames() {
    val forbidden = listOf(
      "Linh", "Hai đứa", "Sinh nhật", "Du lịch Đà Lạt", "Uống thuốc", "Sau bữa ăn", "Năm Yêu Nhau", "Year Love"
    )
    val vi = plan(fixtureM(redact = true), nowB)
    val en = plan(fixtureM(redact = true, english = true), nowB)
    assertEquals(17, vi.items.size)
    assertEquals(17, en.items.size)
    for (item in vi.items) {
      assertEquals(item.key.stableId, "Nhắc hẹn từ InLove", item.title)
      assertEquals(item.key.stableId, "Mở InLove để xem chi tiết", item.body)
    }
    for (item in en.items) {
      assertEquals(item.key.stableId, "Reminder from InLove", item.title)
      assertEquals(item.key.stableId, "Open InLove for details", item.body)
    }
    for (item in vi.items + en.items) {
      for (word in forbidden) {
        assertFalse("${item.key.stableId} title has $word", item.title.contains(word))
        assertFalse("${item.key.stableId} body has $word", item.body.contains(word))
      }
    }

    // Chứng minh phép kiểm có thể hỏng: bản không che chữ chứa đúng các chữ đó.
    val plain = byKey(plan(fixtureM(), nowB))
    assertTrue(plain.getValue("lmsd.10007").body.contains("Linh"))
    assertTrue(plain.getValue("dayof.1").title.contains("Sinh nhật"))
    assertEquals("Uống thuốc", plain.getValue("custom.5").title)
  }

  @Test
  fun zoneChangeKeepsNineAmLocal() {
    val input = ReminderPlanInput(
      anniversaries = listOf(anniversary(1, "25/12/2026")),
      couple = CoupleItem("Hai đứa", "15/06/2020", "Linh")
    )
    val inHcm = plan(input, nowB, zone = hcm)
    val inNewYork = plan(input, nowB, zone = newYork)

    val h = byKey(inHcm)
    assertEquals(1798164000000L, h.getValue("dayof.1").fireAtEpochMillis)
    assertEquals(1813024800000L, h.getValue("couple").fireAtEpochMillis)
    val n = byKey(inNewYork)
    assertEquals(1798207200000L, n.getValue("dayof.1").fireAtEpochMillis)
    assertEquals(1813064400000L, n.getValue("couple").fireAtEpochMillis)

    for (map in listOf(h, n)) {
      assertEquals(LocalDateTime(2026, 12, 25, 9, 0), map.getValue("dayof.1").fireAt)
      assertEquals(LocalDateTime(2027, 6, 15, 9, 0), map.getValue("couple").fireAt)
      for (key in listOf("lms3.10007", "lms1.10007")) {
        assertEquals(key, 9, map.getValue(key).fireAt.hour)
        assertEquals(key, 0, map.getValue(key).fireAt.minute)
      }
      assertEquals(8, map.getValue("lmsd.10007").fireAt.hour)
      assertEquals(30, map.getValue("lmsd.10007").fireAt.minute)
    }
    assertEquals(ids(inHcm), ids(inNewYork))
  }

  @Test
  fun dstDoesNotShiftTheDayCount() {
    val input = ReminderPlanInput(
      anniversaries = listOf(anniversary(1, "12/03/2026", title = "Họp mặt", daysBefore = 7)),
      couple = CoupleItem("Hai đứa", "07/02/2026", "Linh")
    )
    val items = byKey(plan(input, 1772308800000L, zone = losAngeles))

    assertEquals(1772730000000L, items.getValue("lms3.30").fireAtEpochMillis)
    assertEquals(LocalDateTime(2026, 3, 5, 9, 0), items.getValue("lms3.30").fireAt)
    assertEquals(1772902800000L, items.getValue("lms1.30").fireAtEpochMillis)
    assertEquals(LocalDateTime(2026, 3, 7, 9, 0), items.getValue("lms1.30").fireAt)
    assertEquals(1772983800000L, items.getValue("lmsd.30").fireAtEpochMillis)
    assertEquals(LocalDateTime(2026, 3, 8, 8, 30), items.getValue("lmsd.30").fireAt)

    val adv = items.getValue("adv.1")
    val dayOf = items.getValue("dayof.1")
    assertEquals(1772730000000L, adv.fireAtEpochMillis)
    assertEquals(LocalDateTime(2026, 3, 5, 9, 0), adv.fireAt)
    assertEquals(1773331200000L, dayOf.fireAtEpochMillis)
    assertEquals(LocalDateTime(2026, 3, 12, 9, 0), dayOf.fireAt)
    assertEquals(601200000L, dayOf.fireAtEpochMillis - adv.fireAtEpochMillis)
  }

  @Test
  fun englishFlagSwitchesTexts() {
    val vi = plan(fixtureM(), nowB)
    val en = plan(fixtureM(english = true), nowB)
    assertEquals(ids(vi), ids(en))
    assertEquals(vi.items.map { it.fireAtEpochMillis }, en.items.map { it.fireAtEpochMillis })

    val v = byKey(vi)
    assertTrue(v.getValue("adv.1").title.contains("Sắp Đến: Sinh nhật"))
    assertTrue(v.getValue("dayof.1").title.contains("Hôm Nay: Sinh nhật!"))
    assertTrue(v.getValue("ums.3").title.contains("Cột Mốc Tình Yêu: Du lịch Đà Lạt"))
    assertTrue(v.getValue("couple").title.contains("Kỷ Niệm Ngày Yêu Nhau: Hai đứa"))
    assertTrue(v.getValue("couple").body.contains("2557 ngày"))
    assertTrue(v.getValue("lmsd.10007").title.contains("Hôm Nay: "))
    assertTrue(v.getValue("lmsd.10007").title.contains("Kỷ Niệm 7 Năm Yêu Nhau"))
    assertTrue(v.getValue("lmsd.10007").body.contains("Linh"))
    assertTrue(v.getValue("lms1.10007").title.contains("Ngày Mai: "))
    assertTrue(v.getValue("lms3.10007").title.contains("Sắp Đến: "))

    val e = byKey(en)
    assertTrue(e.getValue("adv.1").title.contains("Coming Up: Sinh nhật"))
    assertTrue(e.getValue("dayof.1").title.contains("Today: Sinh nhật!"))
    assertTrue(e.getValue("ums.3").title.contains("Love Milestone: Du lịch Đà Lạt"))
    assertTrue(e.getValue("couple").title.contains("Our Love Anniversary: Hai đứa"))
    assertTrue(e.getValue("couple").body.contains("2557"))
    assertTrue(e.getValue("lmsd.10007").title.contains("Today: "))
    assertTrue(e.getValue("lmsd.10007").title.contains("7-Year Love Anniversary"))
    assertTrue(e.getValue("lmsd.10007").body.contains("Linh"))
    assertTrue(e.getValue("lms1.10007").title.contains("Tomorrow: "))
    assertTrue(e.getValue("lms3.10007").title.contains("Coming Up: "))

    // Nhắc tùy chỉnh không dịch; mọi mục còn lại đổi tiêu đề theo ngôn ngữ.
    assertEquals("Uống thuốc", v.getValue("custom.5").title)
    assertEquals("Sau bữa ăn", v.getValue("custom.5").body)
    assertEquals("Uống thuốc", e.getValue("custom.5").title)
    assertEquals("Sau bữa ăn", e.getValue("custom.5").body)
    for (key in mKeys.filter { it != "custom.5" }) {
      assertNotEquals(key, v.getValue(key).title, e.getValue(key).title)
    }
  }

  // ---- Hợp đồng bổ sung ----

  @Test
  fun droppedCountCountsTrimmed() {
    val five = plan(fixtureM(), nowB, maxItems = 5)
    assertEquals(listOf("custom.5", "adv.1", "dayof.1", "ums.3", "lms3.10007"), ids(five))
    assertEquals(12, five.droppedCount)
    assertEquals(1812765600000L, five.horizonEpochMillis)

    // Bốn ứng viên quá khứ bị bỏ từ trước nên không được đếm.
    assertEquals(0, plan(fixtureM(), nowB, maxItems = 60).droppedCount)
  }

  @Test
  fun equalTimesAreOrderedByStableId() {
    val two = anniversary(2, "14/02/2027")
    val ten = anniversary(10, "14/02/2027")
    for (order in listOf(listOf(two, ten), listOf(ten, two))) {
      val result = plan(ReminderPlanInput(anniversaries = order), nowB)
      assertEquals(listOf("dayof.10", "dayof.2"), ids(result))
      assertEquals(listOf(1802570400000L, 1802570400000L), result.items.map { it.fireAtEpochMillis })
    }
  }

  // Mốc "bây giờ" đi nguyên vẹn vào cả hai bộ luật: đúng ranh giới của bộ lọc tương lai chặt, lệch 1 ms là đổi kết quả.
  @Test
  fun nowIsPassedToBothRuleSetsUnchanged() {
    val customs = ReminderPlanInput(customReminders = listOf(custom(1, nowB - 1), custom(2, nowB), custom(3, nowB + 1)))
    val userResult = plan(customs, nowB)
    assertEquals(listOf("custom.3"), ids(userResult))
    assertEquals(listOf(nowB + 1), userResult.items.map { it.fireAtEpochMillis })

    // lms3.30 bắn lúc 1769479200000: còn ở tương lai khi "bây giờ" sớm 1 ms, và hết khi trùng đúng mốc.
    val couple = ReminderPlanInput(couple = CoupleItem("Hai đứa", "01/01/2026", "Linh"))
    val before = plan(couple, 1769479199999L)
    assertEquals("lms3.30", before.items.first().key.stableId)
    assertEquals(1769479200000L, before.items.first().fireAtEpochMillis)
    val atFire = plan(couple, 1769479200000L)
    assertEquals("lms1.30", atFire.items.first().key.stableId)
    assertEquals(1769652000000L, atFire.items.first().fireAtEpochMillis)
  }

  @Test
  fun maxItemsZeroOrNegativeYieldsAnEmptyPlan() {
    for (max in listOf(0, -5)) {
      val result = plan(fixtureM(), nowB, maxItems = max)
      assertTrue("max $max", result.items.isEmpty())
      assertEquals("max $max", 17, result.droppedCount)
      assertNull("max $max", result.horizonEpochMillis)
    }
  }

  @Test
  fun maxItemsLargerThanTheListKeepsEverything() {
    val result = plan(fixtureM(), nowB, maxItems = Int.MAX_VALUE)
    assertEquals(17, result.items.size)
    assertEquals(0, result.droppedCount)
    assertEquals(1907717400000L, result.horizonEpochMillis)
  }

  @Test
  fun emptyInputYieldsAnEmptyPlan() {
    val result = plan(ReminderPlanInput(), nowB, maxItems = 60)
    assertTrue(result.items.isEmpty())
    assertEquals(0, result.droppedCount)
    assertNull(result.horizonEpochMillis)
  }

  @Test
  fun masterSwitchOffKeepsOnlyCustomReminders() {
    val result = plan(fixtureM(switchOn = false), nowB)
    assertEquals(listOf("custom.5"), ids(result))
    assertEquals(0, result.droppedCount)
  }

  @Test
  fun redactionKeepsEveryOtherField() {
    val plain = plan(fixtureM(), nowB)
    val redacted = plan(fixtureM(redact = true), nowB)
    assertEquals(plain.items.size, redacted.items.size)
    for (i in plain.items.indices) {
      val p = plain.items[i]
      val r = redacted.items[i]
      assertEquals(p.key, r.key)
      assertEquals(p.kind, r.kind)
      assertEquals(p.fireAt, r.fireAt)
      assertEquals(p.fireAtEpochMillis, r.fireAtEpochMillis)
      assertEquals(p.route, r.route)
      assertEquals(p.repeatsYearly, r.repeatsYearly)
      assertEquals(p.isAbsolute, r.isAbsolute)
    }
    assertEquals(plain.droppedCount, redacted.droppedCount)
    assertEquals(plain.horizonEpochMillis, redacted.horizonEpochMillis)
  }

  @Test
  fun redactionDoesNotChangeTheCut() {
    val result = plan(fixtureA(redact = true), nowA, maxItems = 60)
    assertEquals(60, result.items.size)
    assertEquals(9, result.droppedCount)
    assertEquals(1957402800000L, result.horizonEpochMillis)
    assertEquals("lms3.30", result.items[0].key.stableId)
    assertEquals("custom.11", result.items[59].key.stableId)
  }
}
