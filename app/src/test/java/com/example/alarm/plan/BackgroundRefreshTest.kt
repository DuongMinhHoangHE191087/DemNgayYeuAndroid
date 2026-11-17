@file:OptIn(ExperimentalCoroutinesApi::class)

package com.example.alarm.plan

import com.example.alarm.plan.fakes.FakeBackgroundJob
import com.example.alarm.plan.fakes.FakeNotificationGateway
import com.example.alarm.plan.fakes.FakeReminderInputSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackgroundRefreshTest {

  private companion object {
    // 2026-10-09 08:00 ở Hồ Chí Minh.
    const val NOW = 1791507600000L
    const val HOUR = 3_600_000L
    val ZONE = TimeZone.of("Asia/Ho_Chi_Minh")

    // Nhắc tùy chỉnh là mốc tuyệt đối nên mỗi cái đúng một mục.
    fun customs(n: Int) = (1..n).map { CustomReminderItem(it.toLong(), "Nhắc $it", "", NOW + it * HOUR) }
  }

  private class Rig(schedulerScope: CoroutineScope, refreshScope: CoroutineScope) {
    val source = FakeReminderInputSource()
    val gateway = FakeNotificationGateway()
    val scheduler = GatewayReminderScheduler(source, gateway, schedulerScope, { NOW }, { ZONE })
    val job = FakeBackgroundJob()
    val refresh = BackgroundRefresh(scheduler, job, refreshScope)
    val done = mutableListOf<Boolean>()

    init {
      source.input = ReminderPlanInput(ownerId = "owner-1", customReminders = customs(1))
    }

    fun start(): BackgroundRun = refresh.run { done += it }
  }

  @Test
  fun remindersResyncRunsBeforeTheJob() = runTest {
    val rig = Rig(backgroundScope, backgroundScope)
    val gate = CompletableDeferred<Unit>()
    rig.source.gate = gate
    rig.start()
    runCurrent()
    assertEquals(0, rig.job.callCount, "job calls while resync is held")
    assertEquals(1, rig.source.loadCount, "load count while resync is held")
    gate.complete(Unit)
    runCurrent()
    assertEquals(1, rig.gateway.replaceCalls.size, "replaceAll calls")
    assertEquals(1, rig.job.callCount, "job calls after resync")
    assertEquals(listOf(true), rig.done, "done")
  }

  @Test
  fun jobReturningFalseReportsFalse() = runTest {
    val rig = Rig(backgroundScope, backgroundScope)
    rig.job.result = false
    rig.start()
    runCurrent()
    assertEquals(1, rig.gateway.replaceCalls.size, "replaceAll calls")
    assertEquals(listOf(false), rig.done, "done")
  }

  @Test
  fun jobTimeoutReturnsFalseAfterBudget() = runTest {
    val rig = Rig(backgroundScope, backgroundScope)
    rig.job.gate = CompletableDeferred()
    rig.start()
    advanceTimeBy(19_999)
    runCurrent()
    assertEquals(emptyList(), rig.done, "done before budget ends")
    assertEquals(1, rig.job.callCount, "job calls before budget ends")
    assertEquals(0, rig.job.cancelledCount, "job cancellations before budget ends")
    advanceTimeBy(1)
    runCurrent()
    assertEquals(listOf(false), rig.done, "done after budget ends")
    assertEquals(1, rig.job.cancelledCount, "job cancellations after budget ends")
  }

  @Test
  fun resyncFailureStillAttemptsTheJob() = runTest {
    val rig = Rig(backgroundScope, backgroundScope)
    rig.source.failWith = IllegalStateException("boom")
    rig.start()
    runCurrent()
    assertEquals(1, rig.job.callCount, "job calls")
    assertEquals(listOf(false), rig.done, "done")
  }

  @Test
  fun cancelStopsTheJobAndReportsFalseOnce() = runTest {
    val rig = Rig(backgroundScope, backgroundScope)
    val gate = CompletableDeferred<Unit>()
    rig.job.gate = gate
    val handle = rig.start()
    runCurrent()
    handle.cancel()
    runCurrent()
    assertEquals(listOf(false), rig.done, "done after cancel")
    assertEquals(1, rig.job.cancelledCount, "job cancellations")
    handle.cancel()
    gate.complete(Unit)
    advanceTimeBy(40_000)
    runCurrent()
    assertEquals(listOf(false), rig.done, "done after second cancel and a late gate")
  }

  @Test
  fun successReportsTrueExactlyOnce() = runTest {
    val rig = Rig(backgroundScope, backgroundScope)
    rig.start()
    runCurrent()
    assertEquals(listOf(true), rig.done, "done after success")
    advanceTimeBy(40_000)
    runCurrent()
    assertEquals(listOf(true), rig.done, "done after the budget would have elapsed")
  }

  @Test
  fun scopeCancellationIsNotSwallowed() = runTest {
    val parent = Job()
    val rig = Rig(backgroundScope, CoroutineScope(parent + StandardTestDispatcher(testScheduler)))
    rig.job.gate = CompletableDeferred()
    rig.start()
    runCurrent()
    parent.cancel()
    runCurrent()
    assertEquals(listOf(false), rig.done, "done after scope cancellation")
    assertEquals(1, rig.job.cancelledCount, "job cancellations")
    assertTrue(parent.children.none { it.isActive }, "no active child left in the cancelled scope")
  }

  @Test
  fun scopeCancellationDuringResyncNeverStartsTheJob() = runTest {
    val parent = Job()
    val rig = Rig(backgroundScope, CoroutineScope(parent + StandardTestDispatcher(testScheduler)))
    rig.source.gate = CompletableDeferred()
    rig.start()
    runCurrent()
    parent.cancel()
    runCurrent()
    assertEquals(0, rig.job.callCount, "job calls")
    assertEquals(listOf(false), rig.done, "done")
  }

  @Test
  fun budgetCoversTheResyncToo() = runTest {
    val rig = Rig(backgroundScope, backgroundScope)
    rig.source.gate = CompletableDeferred()
    rig.start()
    advanceTimeBy(20_000)
    runCurrent()
    assertEquals(listOf(false), rig.done, "done")
    assertEquals(0, rig.job.callCount, "job calls")
  }

  @Test
  fun jobErrorReportsFalseAndKeepsTheScopeAlive() = runTest {
    val parent = Job()
    val rig = Rig(backgroundScope, CoroutineScope(parent + StandardTestDispatcher(testScheduler)))
    rig.job.failWith = IllegalStateException("boom")
    rig.start()
    runCurrent()
    assertEquals(listOf(false), rig.done, "done after job error")
    assertTrue(parent.isActive, "scope still active")
    rig.job.failWith = null
    rig.start()
    runCurrent()
    assertEquals(listOf(false, true), rig.done, "done after second run")
  }
}
