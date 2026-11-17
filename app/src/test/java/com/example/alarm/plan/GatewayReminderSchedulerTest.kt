@file:OptIn(ExperimentalCoroutinesApi::class)

package com.example.alarm.plan

import com.example.alarm.plan.fakes.FakeNotificationGateway
import com.example.alarm.plan.fakes.FakeReminderInputSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GatewayReminderSchedulerTest {

  private companion object {
    // 2026-10-09 08:00 ở Hồ Chí Minh.
    const val NOW = 1791507600000L
    const val HOUR = 3_600_000L
    const val OWNER = "owner-1"
    val ZONE = TimeZone.of("Asia/Ho_Chi_Minh")
  }

  // Nhắc tùy chỉnh là mốc tuyệt đối nên mỗi cái đúng một mục, giờ bắn = alarmEpochMillis, stableId custom.<id>.
  private fun customs(n: Int) = (1..n).map { CustomReminderItem(it.toLong(), "Nhắc $it", "", NOW + it * HOUR) }

  private class Rig(scope: CoroutineScope) {
    val source = FakeReminderInputSource().also { it.input = ReminderPlanInput(ownerId = OWNER) }
    val gateway = FakeNotificationGateway()
    val errors = mutableListOf<Throwable>()

    // nowMillis được gọi đúng một lần mỗi lượt chạy, ngay trước khi lập kế hoạch: nowCalls cho biết lượt chạy có tới planner không.
    var nowCalls = 0
      private set
    var onNow: () -> Unit = {}
    val scheduler = GatewayReminderScheduler(source, gateway, scope, { nowCalls++; onNow(); NOW }, { ZONE }, { errors += it })
  }

  @Test
  fun threeCallsCoalesceIntoOneRunPlusOneRerun() = runTest {
    val rig = Rig(backgroundScope)
    val gate = CompletableDeferred<Unit>()
    rig.source.gate = gate
    rig.scheduler.requestResync()
    runCurrent()
    rig.scheduler.requestResync()
    rig.scheduler.requestResync()
    gate.complete(Unit)
    runCurrent()
    assertEquals(2, rig.source.loadCount, "load count")
    assertEquals(2, rig.gateway.replaceCalls.size, "replaceAll calls")
  }

  @Test
  fun waitsForGatewayDoneBeforeNextRun() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(customReminders = customs(1), ownerId = OWNER)
    rig.gateway.holdCallbacks = true
    rig.scheduler.requestResync()
    runCurrent()
    rig.scheduler.requestResync()
    runCurrent()
    assertEquals(1, rig.gateway.replaceCalls.size, "replaceAll calls while gateway is busy")
    assertEquals(1, rig.source.loadCount, "load count while gateway is busy")
    rig.gateway.completeNextReplace(5)
    runCurrent()
    assertEquals(2, rig.source.loadCount, "load count after first done")
    assertEquals(2, rig.gateway.replaceCalls.size, "replaceAll calls after first done")
    rig.gateway.completeNextReplace(7)
    runCurrent()
    assertEquals(7, rig.scheduler.coverage.value.scheduled, "scheduled")
  }

  @Test
  fun cancelAllClearsGatewayAndDropsPendingRerun() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(customReminders = customs(2), ownerId = OWNER)
    val gate = CompletableDeferred<Unit>()
    rig.source.gate = gate
    rig.scheduler.requestResync()
    runCurrent()
    rig.scheduler.requestResync()
    rig.scheduler.cancelAll()
    gate.complete(Unit)
    runCurrent()
    assertEquals(1, rig.source.loadCount, "load count")
    // Kết quả load đang bay bị bỏ vì cancelAll đã đổi tài khoản, nên không còn replaceAll.
    assertEquals(listOf("cancelAll"), rig.gateway.events, "gateway events")
    assertEquals(ReminderCoverage(0, 0, null), rig.scheduler.coverage.value, "coverage")
  }

  @Test
  fun gatewayFailureIsReportedAndNextCallRecovers() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(customReminders = customs(1), ownerId = OWNER)
    rig.gateway.failReplaceWith = IllegalStateException("boom")
    rig.scheduler.requestResync()
    runCurrent()
    assertEquals(1, rig.errors.size, "errors after failure")
    assertEquals(ReminderCoverage(0, 0, null), rig.scheduler.coverage.value, "coverage after failure")
    rig.gateway.failReplaceWith = null
    rig.scheduler.requestResync()
    runCurrent()
    assertEquals(ReminderCoverage(1, 0, NOW + HOUR), rig.scheduler.coverage.value, "coverage after recovery")
    assertEquals(1, rig.errors.size, "errors after recovery")
  }

  @Test
  fun inputLoadFailureKeepsTheOldSchedule() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(customReminders = customs(2), ownerId = OWNER)
    rig.scheduler.requestResync()
    runCurrent()
    val before = ReminderCoverage(2, 0, NOW + 2 * HOUR)
    assertEquals(before, rig.scheduler.coverage.value, "coverage after good run")
    assertEquals(1, rig.gateway.replaceCalls.size, "replaceAll calls after good run")
    rig.source.failWith = RuntimeException("x")
    rig.scheduler.requestResync()
    runCurrent()
    assertEquals(1, rig.errors.size, "errors")
    assertEquals(1, rig.gateway.replaceCalls.size, "replaceAll calls after load failure")
    assertEquals(0, rig.gateway.cancelAllCount, "cancelAll count")
    assertEquals(before, rig.scheduler.coverage.value, "coverage after load failure")
  }

  @Test
  fun passesAtMostSixtyItems() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(customReminders = customs(70), ownerId = OWNER)
    rig.scheduler.requestResync()
    runCurrent()
    assertEquals(60, GatewayReminderScheduler.MAX_ITEMS, "MAX_ITEMS")
    assertEquals(1, rig.gateway.replaceCalls.size, "replaceAll calls")
    val ids = rig.gateway.replaceCalls.single().items.map { it.stableId }
    assertEquals((1..60).map { "custom.$it" }, ids, "item ids")
  }

  @Test
  fun resyncAndWaitReturnsAfterTheGatewayIsDone() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(customReminders = customs(1), ownerId = OWNER)
    rig.gateway.holdCallbacks = true
    val waited = async { rig.scheduler.resyncAndWait() }
    runCurrent()
    assertFalse(waited.isCompleted, "waiter completed before the gateway was done")
    rig.gateway.completeNextReplace(3)
    runCurrent()
    assertTrue(waited.await(), "resyncAndWait result")
    assertEquals(3, rig.scheduler.coverage.value.scheduled, "scheduled")
  }

  @Test
  fun coveragePublishesDroppedCountAndHorizon() = runTest {
    val rig = Rig(backgroundScope)
    assertEquals(ReminderCoverage(0, 0, null), rig.scheduler.coverage.value, "initial coverage")
    rig.source.input = ReminderPlanInput(customReminders = customs(70), ownerId = OWNER)
    rig.scheduler.requestResync()
    runCurrent()
    assertEquals(ReminderCoverage(60, 10, NOW + 60 * HOUR), rig.scheduler.coverage.value, "coverage")
  }

  @Test
  fun resyncAndWaitWaitsForTheCoalescedRerunAndReportsItsOutcome() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(customReminders = customs(1), ownerId = OWNER)
    rig.gateway.holdCallbacks = true
    rig.scheduler.requestResync()
    runCurrent()
    val waited = async { rig.scheduler.resyncAndWait() }
    runCurrent()
    assertFalse(waited.isCompleted, "waiter completed while a rerun was pending")
    rig.source.failWith = RuntimeException("x")
    rig.gateway.completeNextReplace(1)
    runCurrent()
    assertFalse(waited.await(), "resyncAndWait result after failed rerun")
    assertEquals(2, rig.source.loadCount, "load count")
    assertEquals(1, rig.errors.size, "errors")
    assertEquals(1, rig.gateway.replaceCalls.size, "replaceAll calls")
  }

  @Test
  fun mapsPlannedItemsToFlatScheduledReminders() = runTest {
    val rig = Rig(backgroundScope)
    // Có details để body khác rỗng (customs() để details trống).
    rig.source.input = ReminderPlanInput(
      customReminders = listOf(CustomReminderItem(1, "Uống thuốc", "Sau bữa ăn", NOW + HOUR)),
      ownerId = "owner-1"
    )
    rig.scheduler.requestResync()
    runCurrent()
    val item = rig.gateway.replaceCalls.single().items.single()
    assertEquals("custom.1", item.stableId, "stableId")
    assertEquals(2026, item.fireYear, "year")
    assertEquals(10, item.fireMonth, "month")
    assertEquals(9, item.fireDay, "day")
    assertEquals(9, item.fireHour, "hour")
    assertEquals(0, item.fireMinute, "minute")
    assertEquals(1791511200000L, item.fireEpochMillis, "fireEpochMillis")
    assertFalse(item.repeatsYearly, "repeatsYearly")
    assertTrue(item.isAbsolute, "isAbsolute")
    assertEquals("owner-1", item.ownerId, "ownerId")
    assertEquals("calendar", item.route, "route")
    assertEquals("CUSTOM", item.kind, "kind")
    assertTrue(item.title.isNotEmpty(), "title not empty")
    assertTrue(item.body.isNotEmpty(), "body not empty")
  }

  @Test
  fun replaceAllReceivesSoundFlagAndLocalizedSnoozeTitle() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(customReminders = customs(1), isEnglish = true, soundEnabled = false, ownerId = OWNER)
    rig.scheduler.requestResync()
    runCurrent()
    assertEquals(false, rig.gateway.replaceCalls[0].soundEnabled, "english soundEnabled")
    assertEquals("Snooze 1h ⏰", rig.gateway.replaceCalls[0].snoozeTitle, "english snoozeTitle")
    rig.source.input = ReminderPlanInput(customReminders = customs(1), isEnglish = false, soundEnabled = true, ownerId = OWNER)
    rig.scheduler.requestResync()
    runCurrent()
    assertEquals(true, rig.gateway.replaceCalls[1].soundEnabled, "vietnamese soundEnabled")
    assertEquals("Nhắc lại sau 1h ⏰", rig.gateway.replaceCalls[1].snoozeTitle, "vietnamese snoozeTitle")
  }

  @Test
  fun cancelResyncsLikeRequestResync() = runTest {
    val rig = Rig(backgroundScope)
    rig.scheduler.cancel(listOf(ReminderKey.Custom(1)))
    runCurrent()
    assertEquals(1, rig.source.loadCount, "load count")
    assertEquals(1, rig.gateway.replaceCalls.size, "replaceAll calls")
  }

  @Test
  fun sendTestPostsALocalizedCalendarNotification() = runTest {
    val rig = Rig(backgroundScope)
    val expected = listOf(
      Triple(
        ReminderTestKind.ANNIVERSARY,
        false,
        "🔔 Thử nghiệm thông báo kỷ niệm ❤️" to "Sắp đến ngày kỷ niệm đặc biệt của hai bạn! Hãy chuẩn bị những bất ngờ ngọt ngào nhé! ✨"
      ),
      Triple(
        ReminderTestKind.ANNIVERSARY,
        true,
        "🔔 Anniversary notification test ❤️" to "Your special anniversary is coming up! Get ready for some sweet surprises! ✨"
      ),
      Triple(
        ReminderTestKind.CUSTOM,
        false,
        "Thử nghiệm báo thức tình yêu ❤️" to "Chuông và thông báo kỷ niệm hoạt động rất tốt!"
      ),
      Triple(
        ReminderTestKind.CUSTOM,
        true,
        "Love alarm test ❤️" to "The chime and anniversary notifications are working great!"
      ),
      Triple(
        ReminderTestKind.MILESTONE,
        false,
        "🔔 Thử Nghiệm: Cột Mốc 💎 Bách Nhật Yêu (100 Ngày) ❤️" to
          "Thông báo nhắc nhở cột mốc kỷ niệm tình yêu từ Firestore hoạt động hoàn hảo! " +
          "Bạn sẽ nhận được thông báo trước 3 ngày, 1 ngày và vào đúng ngày kỷ niệm cùng người ấy! ✨"
      ),
      Triple(
        ReminderTestKind.MILESTONE,
        true,
        "🔔 Test: Milestone 💎 100 Days of Love ❤️" to
          "Your love milestone reminders are working perfectly! " +
          "You'll be notified 3 days before, 1 day before and on the day itself, together with your partner! ✨"
      )
    )
    for ((index, case) in expected.withIndex()) {
      val (kind, english, texts) = case
      rig.source.input = ReminderPlanInput(isEnglish = english)
      rig.scheduler.sendTest(kind)
      runCurrent()
      assertEquals(index + 1, rig.gateway.posted.size, "posted count after $kind english=$english")
      assertEquals(
        FakeNotificationGateway.PostedTest(texts.first, texts.second, "calendar"),
        rig.gateway.posted.last(),
        "posted $kind english=$english"
      )
    }
  }

  @Test
  fun sendTestLoadFailureReportsAndPostsNothing() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.failWith = RuntimeException("x")
    rig.scheduler.sendTest(ReminderTestKind.CUSTOM)
    runCurrent()
    assertEquals(1, rig.errors.size, "errors")
    assertTrue(rig.gateway.posted.isEmpty(), "posted is empty")
  }

  @Test
  fun cancelAllRunsBeforeAResyncRequestedAfterIt() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(customReminders = customs(1), ownerId = OWNER)
    rig.gateway.holdCallbacks = true
    rig.scheduler.cancelAll()
    rig.scheduler.requestResync()
    runCurrent()
    assertEquals(listOf("cancelAll"), rig.gateway.events, "events while the cancel is not done")
    assertEquals(0, rig.source.loadCount, "load count while the cancel is not done")
    rig.gateway.completeNextCancel()
    runCurrent()
    assertEquals(listOf("cancelAll", "replaceAll"), rig.gateway.events, "events after the cancel is done")
    rig.gateway.completeNextReplace(1)
    runCurrent()
    assertEquals(ReminderCoverage(1, 0, NOW + HOUR), rig.scheduler.coverage.value, "coverage after the resync")
  }

  @Test
  fun cancelAllDropsAResyncThatHasNotStartedYet() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(customReminders = customs(1), ownerId = OWNER)
    rig.scheduler.requestResync()
    rig.scheduler.cancelAll()
    runCurrent()
    assertEquals(0, rig.source.loadCount, "load count")
    assertEquals(listOf("cancelAll"), rig.gateway.events, "gateway events")
  }

  @Test
  fun resyncAndWaitReportsFalseWhenACancelAllFollowsTheResync() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(customReminders = customs(1), ownerId = OWNER)
    rig.gateway.holdCallbacks = true
    val waited = async { rig.scheduler.resyncAndWait() }
    runCurrent()
    rig.scheduler.cancelAll()
    rig.gateway.completeNextReplace(1)
    runCurrent()
    assertFalse(waited.isCompleted, "waiter completed before the cancel was done")
    rig.gateway.completeNextCancel()
    runCurrent()
    assertFalse(waited.await(), "resyncAndWait result after a cancelAll")
    assertEquals(ReminderCoverage(0, 0, null), rig.scheduler.coverage.value, "coverage")
  }

  @Test
  fun cancelAllFailureIsReportedAndKeepsTheCoverage() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(customReminders = customs(2), ownerId = OWNER)
    rig.scheduler.requestResync()
    runCurrent()
    val before = ReminderCoverage(2, 0, NOW + 2 * HOUR)
    rig.gateway.failCancelWith = IllegalStateException("boom")
    rig.scheduler.cancelAll()
    runCurrent()
    assertEquals(1, rig.errors.size, "errors after the failed cancel")
    assertEquals(before, rig.scheduler.coverage.value, "coverage after the failed cancel")
    rig.gateway.failCancelWith = null
    rig.scheduler.cancelAll()
    runCurrent()
    assertEquals(1, rig.errors.size, "errors after the next cancel")
    assertEquals(ReminderCoverage(0, 0, null), rig.scheduler.coverage.value, "coverage after the next cancel")
  }

  @Test
  fun exactDeliveryIsAlwaysAvailable() = runTest {
    val rig = Rig(backgroundScope)
    assertTrue(rig.scheduler.isExactDeliveryAvailable(), "exact delivery")
  }

  @Test
  fun remindersAtOrBeforeNowAreNotScheduled() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(
      customReminders = listOf(
        CustomReminderItem(1, "Đã qua", "", NOW - HOUR),
        CustomReminderItem(2, "Sắp tới", "", NOW + HOUR)
      ),
      ownerId = OWNER
    )
    rig.scheduler.requestResync()
    runCurrent()
    assertEquals(listOf("custom.2"), rig.gateway.replaceCalls.single().items.map { it.stableId }, "item ids")
  }

  @Test
  fun annualAnniversaryIsMappedAsYearlyAndNotAbsolute() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(
      anniversaries = listOf(AnniversaryItem(1, "Sinh nhật", "20/10/2026", true, true, 0)),
      ownerId = OWNER
    )
    rig.scheduler.requestResync()
    runCurrent()
    val item = rig.gateway.replaceCalls.single().items.single()
    assertEquals("dayof.1", item.stableId, "stableId")
    assertEquals("DAY_OF", item.kind, "kind")
    assertEquals(2026, item.fireYear, "year")
    assertEquals(10, item.fireMonth, "month")
    assertEquals(20, item.fireDay, "day")
    assertEquals(9, item.fireHour, "hour")
    assertEquals(0, item.fireMinute, "minute")
    assertEquals(NOW + 11 * 24 * HOUR + HOUR, item.fireEpochMillis, "fireEpochMillis")
    assertTrue(item.repeatsYearly, "repeatsYearly")
    assertFalse(item.isAbsolute, "isAbsolute")
  }

  @Test
  fun logoutDuringSuspendedLoadDropsTheResult() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(customReminders = customs(2), ownerId = OWNER)
    val gate = CompletableDeferred<Unit>()
    rig.source.gate = gate
    rig.scheduler.requestResync()
    runCurrent()
    rig.scheduler.cancelAll()
    gate.complete(Unit)
    runCurrent()
    assertEquals(listOf("cancelAll"), rig.gateway.events, "gateway events")
    assertEquals(1, rig.source.loadCount, "load count")
    assertEquals(ReminderCoverage(0, 0, null), rig.scheduler.coverage.value, "coverage")
    assertTrue(rig.errors.isEmpty(), "errors is empty")
    assertEquals(0, rig.nowCalls, "the dropped run never reached the planner")
  }

  @Test
  fun logoutBetweenPlanningAndReplaceDropsTheResult() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(customReminders = customs(2), ownerId = OWNER)
    // Hook một lần: mô phỏng đăng xuất rơi vào giữa load và replaceAll.
    rig.onNow = {
      rig.onNow = {}
      rig.scheduler.cancelAll()
    }
    rig.scheduler.requestResync()
    runCurrent()
    assertEquals(listOf("cancelAll"), rig.gateway.events, "gateway events")
    assertEquals(ReminderCoverage(0, 0, null), rig.scheduler.coverage.value, "coverage")
    assertEquals(1, rig.nowCalls, "the planner ran, so the check before replaceAll dropped the run")
  }

  @Test
  fun logoutDuringReplaceNeverRecreatesOldOwnersReminders() = runTest {
    val rig = Rig(backgroundScope)
    rig.gateway.holdCallbacks = true
    rig.source.input = ReminderPlanInput(customReminders = customs(2), ownerId = "owner-A")
    rig.scheduler.requestResync()
    runCurrent()
    assertEquals(1, rig.gateway.replaceCalls.size, "replaceAll calls while the callback is held")
    rig.scheduler.requestResync()
    rig.scheduler.cancelAll()
    rig.gateway.completeNextReplace(2)
    runCurrent()
    assertEquals(ReminderCoverage(0, 0, null), rig.scheduler.coverage.value, "coverage after the stale onDone")
    assertEquals(listOf("replaceAll", "cancelAll"), rig.gateway.events, "events while the cancel is held")
    assertEquals(1, rig.source.loadCount, "load count while the cancel is held")
    rig.gateway.completeNextCancel()
    runCurrent()
    assertEquals(listOf("replaceAll", "cancelAll"), rig.gateway.events, "events after the cancel is done")
    assertEquals(1, rig.source.loadCount, "load count after the cancel is done")
    assertEquals(ReminderCoverage(0, 0, null), rig.scheduler.coverage.value, "coverage after the cancel is done")
  }

  @Test
  fun generationOnlyChangesOnCancelAll() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(customReminders = customs(1), ownerId = OWNER)
    val gate = CompletableDeferred<Unit>()
    rig.source.gate = gate
    rig.scheduler.requestResync()
    runCurrent()
    rig.scheduler.requestResync()
    rig.scheduler.cancel(listOf(ReminderKey.Custom(1)))
    rig.scheduler.sendTest(ReminderTestKind.CUSTOM)
    gate.complete(Unit)
    runCurrent()
    assertEquals(2, rig.gateway.events.count { it == "replaceAll" }, "replaceAll: the run plus one coalesced rerun")
    assertEquals(0, rig.gateway.cancelAllCount, "cancelAll count")
    assertEquals(1, rig.gateway.posted.size, "posted count")
    assertTrue(rig.errors.isEmpty(), "errors is empty")
  }

  @Test
  fun requestsCarryTheOwnerId() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(
      customReminders = customs(2),
      anniversaries = listOf(AnniversaryItem(1, "Sinh nhật", "20/10/2026", true, true, 0)),
      ownerId = "owner-7"
    )
    rig.scheduler.requestResync()
    runCurrent()
    val items = rig.gateway.replaceCalls.single().items
    assertEquals(3, items.size, "item count")
    assertEquals(listOf("owner-7", "owner-7", "owner-7"), items.map { it.ownerId }, "owner ids")
  }

  @Test
  fun blankOwnerSchedulesNothing() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(customReminders = customs(2), ownerId = "")
    rig.scheduler.requestResync()
    runCurrent()
    assertEquals(1, rig.source.loadCount, "load count for an empty owner")
    assertTrue(rig.gateway.events.isEmpty(), "events for an empty owner")
    assertEquals(ReminderCoverage(0, 0, null), rig.scheduler.coverage.value, "coverage for an empty owner")
    assertTrue(rig.errors.isEmpty(), "errors for an empty owner")
    assertEquals(0, rig.nowCalls, "planner calls for an empty owner")
    rig.source.input = ReminderPlanInput(customReminders = customs(2), ownerId = "  ")
    rig.scheduler.requestResync()
    runCurrent()
    assertEquals(2, rig.source.loadCount, "load count for a whitespace owner")
    assertTrue(rig.gateway.events.isEmpty(), "events for a whitespace owner")
    val waited = async { rig.scheduler.resyncAndWait() }
    runCurrent()
    assertTrue(waited.await(), "resyncAndWait result for a blank owner")
    assertTrue(rig.gateway.events.isEmpty(), "events after resyncAndWait for a blank owner")
    // Lượt chạy owner trống không bao giờ phá lịch đã đặt.
    rig.source.input = ReminderPlanInput(customReminders = customs(2), ownerId = "owner-A")
    rig.scheduler.requestResync()
    runCurrent()
    val before = ReminderCoverage(2, 0, NOW + 2 * HOUR)
    assertEquals(before, rig.scheduler.coverage.value, "coverage after the first run")
    rig.source.input = ReminderPlanInput(customReminders = customs(2), ownerId = "")
    rig.scheduler.requestResync()
    runCurrent()
    assertEquals(listOf("replaceAll"), rig.gateway.events, "events after the blank-owner resync")
    assertEquals(0, rig.gateway.cancelAllCount, "cancelAll count")
    assertEquals(before, rig.scheduler.coverage.value, "coverage after the blank-owner resync")
  }

  @Test
  fun resyncAfterAccountSwitchUsesNewOwner() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(customReminders = customs(2), ownerId = "owner-A")
    rig.scheduler.requestResync()
    runCurrent()
    assertEquals(listOf("owner-A", "owner-A"), rig.gateway.replaceCalls[0].items.map { it.ownerId }, "first owner ids")
    rig.scheduler.cancelAll()
    rig.source.input = ReminderPlanInput(customReminders = customs(1), ownerId = "owner-B")
    rig.scheduler.requestResync()
    runCurrent()
    assertEquals(listOf("replaceAll", "cancelAll", "replaceAll"), rig.gateway.events, "gateway events")
    assertEquals(listOf("owner-B"), rig.gateway.replaceCalls[1].items.map { it.ownerId }, "second owner ids")
    assertEquals(ReminderCoverage(1, 0, NOW + HOUR), rig.scheduler.coverage.value, "coverage")
  }

  @Test
  fun accountSwitchDuringSuspendedLoadSchedulesOnlyTheNewOwner() = runTest {
    val rig = Rig(backgroundScope)
    rig.source.input = ReminderPlanInput(customReminders = customs(2), ownerId = "owner-A")
    val gate = CompletableDeferred<Unit>()
    rig.source.gate = gate
    rig.scheduler.requestResync()
    runCurrent()
    rig.scheduler.cancelAll()
    rig.source.input = ReminderPlanInput(customReminders = customs(1), ownerId = "owner-B")
    rig.scheduler.requestResync()
    gate.complete(Unit)
    runCurrent()
    assertEquals(listOf("cancelAll", "replaceAll"), rig.gateway.events, "gateway events")
    assertEquals(listOf("owner-B"), rig.gateway.replaceCalls.single().items.map { it.ownerId }, "owner ids")
    assertEquals(2, rig.source.loadCount, "load count")
    assertEquals(ReminderCoverage(1, 0, NOW + HOUR), rig.scheduler.coverage.value, "coverage")
  }
}
