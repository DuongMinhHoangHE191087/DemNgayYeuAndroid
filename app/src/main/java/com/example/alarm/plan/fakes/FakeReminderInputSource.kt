package com.example.alarm.plan.fakes

import com.example.alarm.plan.ReminderInputSource
import com.example.alarm.plan.ReminderPlanInput
import kotlinx.coroutines.CompletableDeferred

/** Nguồn đầu vào giả: gate giữ load đang chạy, failWith làm load lỗi (kiểm tra sau gate để test giữ rồi mới làm lỗi). */
class FakeReminderInputSource : ReminderInputSource {
  var input = ReminderPlanInput()
  var failWith: Throwable? = null
  var gate: CompletableDeferred<Unit>? = null
  var loadCount = 0
    private set

  override suspend fun load(): ReminderPlanInput {
    loadCount++
    gate?.await()
    failWith?.let { throw it }
    return input
  }
}
