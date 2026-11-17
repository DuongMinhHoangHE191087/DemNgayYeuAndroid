package com.example.alarm.plan

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cổng chất lượng NOTIF-CAP-DST: giới hạn 60 thông báo và giờ 09:00 địa phương khi cửa sổ đi qua ngày đổi giờ. */
class ReminderPlannerNotificationDstTest {

  private val losAngeles = TimeZone.of("America/Los_Angeles")

  private fun dateText(date: LocalDate): String =
    "${date.day.toString().padStart(2, '0')}/${date.month.number.toString().padStart(2, '0')}/${date.year.toString().padStart(4, '0')}"

  private fun oneOff(id: Long, dateText: String) = AnniversaryItem(id, "Sinh nhật", dateText, false, true, 0)

  // Chuyển sang giờ mùa hè 2026-03-08 (02:00 PST thành 03:00 PDT): ngày đó chỉ có 23 giờ.
  @Test
  fun capOfSixtyIsKeptWhenAWindowCrossesSpringForward() {
    val start = LocalDate(2026, 3, 1)
    // Mã giảm dần (70 trước) để chứng minh thứ tự đầu vào không ảnh hưởng.
    val anniversaries = (70L downTo 1L).map { i -> oneOff(i, dateText(start.plus((i - 1).toInt(), DateTimeUnit.DAY))) }
    val result = ReminderPlanner.plan(ReminderPlanInput(anniversaries = anniversaries), 1772308800000L, losAngeles, 60)

    assertEquals((1..60).map { "dayof.$it" }, result.items.map { it.key.stableId })
    assertEquals(10, result.droppedCount)
    assertEquals(1777478400000L, result.horizonEpochMillis)

    val byId = result.items.associateBy { it.key.stableId }
    assertEquals(1772384400000L, byId.getValue("dayof.1").fireAtEpochMillis)
    assertEquals(1772902800000L, byId.getValue("dayof.7").fireAtEpochMillis)
    assertEquals(1772985600000L, byId.getValue("dayof.8").fireAtEpochMillis)
    assertEquals(82800000L, byId.getValue("dayof.8").fireAtEpochMillis - byId.getValue("dayof.7").fireAtEpochMillis)

    val gaps = result.items.zipWithNext { a, b -> b.fireAtEpochMillis - a.fireAtEpochMillis }
    assertEquals(59, gaps.size)
    assertEquals(1, gaps.count { it == 82800000L })
    assertEquals(58, gaps.count { it == 86400000L })
    for (item in result.items) {
      assertEquals(item.key.stableId, 9, item.fireAt.hour)
      assertEquals(item.key.stableId, 0, item.fireAt.minute)
    }
  }

  // Về giờ chuẩn 2026-11-01 (02:00 PDT thành 01:00 PST): ngày đó có 25 giờ nhưng thông báo vẫn 09:00 địa phương.
  @Test
  fun nineAmStaysLocalOnFallBackDay() {
    val anniversaries = listOf(oneOff(1, "31/10/2026"), oneOff(2, "01/11/2026"), oneOff(3, "02/11/2026"))
    val result = ReminderPlanner.plan(ReminderPlanInput(anniversaries = anniversaries), 1793386800000L, losAngeles, 60)
    val byId = result.items.associateBy { it.key.stableId }

    assertEquals(listOf("dayof.1", "dayof.2", "dayof.3"), result.items.map { it.key.stableId })
    assertEquals(1793462400000L, byId.getValue("dayof.1").fireAtEpochMillis)
    assertEquals(1793552400000L, byId.getValue("dayof.2").fireAtEpochMillis)
    assertEquals(1793638800000L, byId.getValue("dayof.3").fireAtEpochMillis)
    assertEquals(90000000L, byId.getValue("dayof.2").fireAtEpochMillis - byId.getValue("dayof.1").fireAtEpochMillis)
    assertEquals(86400000L, byId.getValue("dayof.3").fireAtEpochMillis - byId.getValue("dayof.2").fireAtEpochMillis)

    assertEquals(LocalDateTime(2026, 10, 31, 9, 0), byId.getValue("dayof.1").fireAt)
    assertEquals(LocalDateTime(2026, 11, 1, 9, 0), byId.getValue("dayof.2").fireAt)
    assertEquals(LocalDateTime(2026, 11, 2, 9, 0), byId.getValue("dayof.3").fireAt)
  }

  // Giờ 01:30 lặp hai lần ngày 2026-11-01 (PDT rồi PST): hai nhắc cùng giờ địa phương vẫn xếp theo thời điểm thật, không theo mã.
  @Test
  fun repeatedHourOnFallBackKeepsEpochOrder() {
    val pst = CustomReminderItem(1, "Uống thuốc", "Sau bữa ăn", 1793525400000L)
    val pdt = CustomReminderItem(2, "Uống thuốc", "Sau bữa ăn", 1793521800000L)
    val result = ReminderPlanner.plan(ReminderPlanInput(customReminders = listOf(pst, pdt)), 1793386800000L, losAngeles, 60)

    // Mã 2 (01:30 PDT) bắn trước mã 1 (01:30 PST) đúng một giờ dù mã 1 nhỏ hơn.
    assertEquals(listOf("custom.2", "custom.1"), result.items.map { it.key.stableId })
    assertEquals(listOf(1793521800000L, 1793525400000L), result.items.map { it.fireAtEpochMillis })
    for (item in result.items) assertEquals(item.key.stableId, LocalDateTime(2026, 11, 1, 1, 30), item.fireAt)
  }
}
