package com.example.alarm.plan

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Việc nền cần làm sau resync nhắc hẹn: true là xong, false là engine đồng bộ báo Retry. */
fun interface BackgroundJob {
  suspend fun run(): Boolean
}

/** Tay cầm của một lượt chạy nền; cancel gọi nhiều lần cũng được. */
interface BackgroundRun {
  fun cancel()
}

/**
 * Lượt làm mới nền của iOS (BGAppRefreshTask): resync nhắc hẹn rồi chạy việc nền, chung một ngân sách thời gian.
 * onDone được gọi đúng một lần trên mọi đường (xong, lỗi, hết giờ, cancel, scope bị hủy) và chỉ nhận true khi cả hai đều thành công.
 */
class BackgroundRefresh(
  private val reminders: GatewayReminderScheduler,
  private val job: BackgroundJob,
  private val scope: CoroutineScope,
  private val budgetMillis: Long = 20_000
) {
  fun run(onDone: (Boolean) -> Unit): BackgroundRun {
    // ponytail: ok không volatile, invokeOnCompletion chạy sau khi coroutine kết thúc nên thấy giá trị cuối; đổi sang atomic nếu thêm đường ghi khác.
    var ok = false
    val coroutine = scope.launch {
      val result = withTimeoutOrNull(budgetMillis) {
        val resyncOk = reminders.resyncAndWait()
        // Resync hỏng vẫn thử việc nền; job ném lỗi thường chỉ tính là false, hủy thì ném tiếp.
        val jobOk = try {
          job.run()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          false
        }
        resyncOk && jobOk
      }
      ok = result == true
    }
    coroutine.invokeOnCompletion { onDone(ok) }
    return object : BackgroundRun {
      override fun cancel() = coroutine.cancel()
    }
  }
}
