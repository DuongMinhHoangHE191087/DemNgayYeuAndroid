package com.example.alarm.plan.fakes

import com.example.alarm.plan.ReminderCoverage
import com.example.alarm.plan.ReminderKey
import com.example.alarm.plan.ReminderScheduler
import com.example.alarm.plan.ReminderTestKind
import kotlinx.coroutines.flow.MutableStateFlow

/** Bộ lập lịch giả cho test màn hình: đếm lệnh gọi, coverage do test đặt. */
class FakeReminderScheduler : ReminderScheduler {
  var exactAvailable = true
  var resyncRequests = 0
    private set
  var cancelAllCalls = 0
    private set
  val cancelCalls = mutableListOf<List<ReminderKey>>()
  val testsSent = mutableListOf<ReminderTestKind>()
  override val coverage = MutableStateFlow(ReminderCoverage(0, 0, null))

  override fun requestResync() {
    resyncRequests++
  }

  override fun cancel(keys: List<ReminderKey>) {
    cancelCalls.add(keys)
  }

  override fun cancelAll() {
    cancelAllCalls++
  }

  override fun sendTest(kind: ReminderTestKind) {
    testsSent.add(kind)
  }

  override fun isExactDeliveryAvailable(): Boolean = exactAvailable
}
