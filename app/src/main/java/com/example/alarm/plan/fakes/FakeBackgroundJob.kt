package com.example.alarm.plan.fakes

import com.example.alarm.plan.BackgroundJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/** Việc nền giả: gate giữ lượt chạy, failWith làm lượt chạy lỗi (ném sau gate), cancelledCount đếm lượt bị hủy. */
class FakeBackgroundJob : BackgroundJob {
  var result = true
  var failWith: Throwable? = null
  var gate: CompletableDeferred<Unit>? = null
  var callCount = 0
    private set
  var cancelledCount = 0
    private set

  override suspend fun run(): Boolean {
    callCount++
    try {
      gate?.await()
      failWith?.let { throw it }
      return result
    } catch (e: CancellationException) {
      cancelledCount++
      throw e
    }
  }
}
