package com.example.alarm.plan

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number

/**
 * Logic dùng chung của đường nhắc hẹn iOS: cổng thông báo gốc chỉ đặt danh sách có sẵn, lớp này quyết định đặt gì và khi nào.
 * Mỗi lượt chạy là load -> plan (tối đa MAX_ITEMS) -> gateway.replaceAll -> công bố coverage.
 * Tối đa một lượt chạy cùng lúc; các lệnh gọi trong lúc đang chạy gộp thành đúng một lượt chạy lại.
 * Lượt chạy đang bay mà gặp cancelAll (đăng xuất hoặc đổi tài khoản) thì bị bỏ, không đặt lại nhắc của tài khoản cũ.
 */
class GatewayReminderScheduler(
  private val source: ReminderInputSource,
  private val gateway: NotificationGateway,
  private val scope: CoroutineScope,
  private val nowMillis: () -> Long,
  private val zone: () -> TimeZone,
  private val onError: (Throwable) -> Unit = {}
) : ReminderScheduler {
  companion object {
    const val MAX_ITEMS = 60

    // Cùng route với thông báo thử hiện có (planner cũng giữ hằng riêng, vì hằng ở đó là private).
    private const val ROUTE_CALENDAR = "calendar"
  }

  // Cờ trong một giá trị duy nhất để đổi trạng thái nguyên tử, không cần khóa: lớp này sẽ chuyển sang commonMain.
  private data class Work(
    val running: Boolean = false,
    val resync: Boolean = false,
    val cancel: Boolean = false,
    // Kết quả của thao tác cuối, đặt trước khi chuyển sang rảnh để người chờ đọc đúng kết quả.
    val lastOk: Boolean = true,
    // Tăng mỗi lần cancelAll để lượt chạy đang bay biết tài khoản đã đổi.
    val generation: Int = 0
  )

  private val work = MutableStateFlow(Work())
  private val _coverage = MutableStateFlow(ReminderCoverage(0, 0, null))

  override val coverage: StateFlow<ReminderCoverage> = _coverage

  override fun requestResync() = submit { it.copy(running = true, resync = true) }

  // iOS thay toàn bộ danh sách nên hủy theo khóa cũng là dựng lại hết.
  override fun cancel(keys: List<ReminderKey>) = requestResync()

  // Bỏ lượt chạy lại đang chờ; không ngắt lượt đang chạy, lệnh hủy của cổng xếp sau nó.
  // Gọi SAU khi dữ liệu của tài khoản mới đã hiện ra cho load(), rồi mới requestResync().
  override fun cancelAll() =
    submit { it.copy(running = true, resync = false, cancel = true, generation = it.generation + 1) }

  override fun isExactDeliveryAvailable(): Boolean = true

  /** Chạy một lượt (kể cả lượt chạy lại gộp) rồi trả true khi thao tác cuối là resync thành công. */
  suspend fun resyncAndWait(): Boolean {
    requestResync()
    return work.first { !it.running }.lastOk
  }

  override fun sendTest(kind: ReminderTestKind) {
    scope.launch {
      try {
        val english = source.load().isEnglish
        val (title, body) = testText(kind, english)
        gateway.postTest(title, body, ROUTE_CALENDAR)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        onError(e)
      }
    }
  }

  private fun submit(change: (Work) -> Work) {
    // ponytail: scope bị hủy giữa chừng thì running kẹt true; scope là của cả vòng đời app.
    if (!work.getAndUpdate(change).running) scope.launch { drain() }
  }

  private suspend fun drain() {
    while (true) {
      val taken = work.getAndUpdate {
        if (it.resync || it.cancel) it.copy(resync = false, cancel = false) else it.copy(running = false)
      }
      if (!taken.resync && !taken.cancel) return
      // Hủy chạy trước resync trong cùng một vòng.
      if (taken.cancel) {
        runCancel()
        work.update { it.copy(lastOk = false) }
      }
      if (taken.resync) {
        val ok = runResync()
        work.update { it.copy(lastOk = ok) }
      }
    }
  }

  private fun isStale(generation: Int) = work.value.generation != generation

  private suspend fun runResync(): Boolean = try {
    val generation = work.value.generation
    val input = source.load()
    if (isStale(generation)) return false
    // Owner trống = chưa đăng nhập hoặc tài khoản chưa sẵn sàng: không đụng tới cổng (xóa lịch vì một lần trống tạm thời còn tệ hơn,
    // đăng xuất đã xóa qua cancelAll()); trả true vì không có gì lỗi, nền không báo Retry mãi cho người đã đăng xuất.
    if (input.ownerId.isBlank()) return true
    val plan = ReminderPlanner.plan(input, nowMillis(), zone(), MAX_ITEMS)
    val items = plan.items.map {
      ScheduledReminder(
        stableId = it.key.stableId,
        fireYear = it.fireAt.year,
        fireMonth = it.fireAt.month.number,
        fireDay = it.fireAt.day,
        fireHour = it.fireAt.hour,
        fireMinute = it.fireAt.minute,
        fireEpochMillis = it.fireAtEpochMillis,
        repeatsYearly = it.repeatsYearly,
        isAbsolute = it.isAbsolute,
        ownerId = input.ownerId,
        title = it.title,
        body = it.body,
        route = it.route,
        kind = it.kind.name
      )
    }
    val snoozeTitle = if (input.isEnglish) "Snooze 1h ⏰" else "Nhắc lại sau 1h ⏰"
    val done = CompletableDeferred<Int>()
    // ponytail: trên dispatcher đơn luồng không gì đổi được giữa load() và đây; kiểm tra này cho dispatcher đa luồng và rất rẻ.
    if (isStale(generation)) return false
    // ponytail: cổng không bao giờ gọi onDone thì vòng lặp treo; thêm withTimeout nếu chạy máy thật thấy vậy.
    gateway.replaceAll(items, input.soundEnabled, snoozeTitle) { done.complete(it) }
    // Số đã đặt do cổng báo (có thể 0 khi thiếu quyền thông báo), không phải items.size.
    val scheduled = done.await()
    // Không công bố onDone cũ của tài khoản đã bị cancelAll.
    if (isStale(generation)) return false
    _coverage.value = ReminderCoverage(scheduled, plan.droppedCount, plan.horizonEpochMillis)
    true
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    onError(e)
    false
  }

  private suspend fun runCancel() {
    try {
      val done = CompletableDeferred<Unit>()
      gateway.cancelAll { done.complete(Unit) }
      done.await()
      _coverage.value = ReminderCoverage(0, 0, null)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      onError(e)
    }
  }

  // ponytail: chữ thông báo thử để ở đây vì có hai nơi dùng; chuyển sang ReminderTexts khi có nơi thứ ba.
  private fun testText(kind: ReminderTestKind, english: Boolean): Pair<String, String> = when (kind) {
    ReminderTestKind.ANNIVERSARY ->
      if (english) {
        "🔔 Anniversary notification test ❤️" to
          "Your special anniversary is coming up! Get ready for some sweet surprises! ✨"
      } else {
        "🔔 Thử nghiệm thông báo kỷ niệm ❤️" to
          "Sắp đến ngày kỷ niệm đặc biệt của hai bạn! Hãy chuẩn bị những bất ngờ ngọt ngào nhé! ✨"
      }
    ReminderTestKind.CUSTOM ->
      if (english) {
        "Love alarm test ❤️" to "The chime and anniversary notifications are working great!"
      } else {
        "Thử nghiệm báo thức tình yêu ❤️" to "Chuông và thông báo kỷ niệm hoạt động rất tốt!"
      }
    ReminderTestKind.MILESTONE ->
      if (english) {
        "🔔 Test: Milestone 💎 100 Days of Love ❤️" to
          "Your love milestone reminders are working perfectly! " +
          "You'll be notified 3 days before, 1 day before and on the day itself, together with your partner! ✨"
      } else {
        "🔔 Thử Nghiệm: Cột Mốc 💎 Bách Nhật Yêu (100 Ngày) ❤️" to
          "Thông báo nhắc nhở cột mốc kỷ niệm tình yêu từ Firestore hoạt động hoàn hảo! " +
          "Bạn sẽ nhận được thông báo trước 3 ngày, 1 ngày và vào đúng ngày kỷ niệm cùng người ấy! ✨"
      }
  }
}
