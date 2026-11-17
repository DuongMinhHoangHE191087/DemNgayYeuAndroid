package com.example.alarm.plan

import kotlinx.coroutines.flow.StateFlow

/** Điểm vào lập lịch nhắc hẹn, dùng từ ViewModel, Activity và Swift. */
interface ReminderScheduler {
  /** Lập lại lịch từ dữ liệu hiện có; idempotent, gộp nhiều lần gọi, tự đọc dữ liệu nên không nhận đối số. */
  fun requestResync()

  /** Hủy nhắc theo khóa; Android hủy PendingIntent theo mã cũ, iOS tương đương requestResync(). */
  fun cancel(keys: List<ReminderKey>)

  /** Hủy mọi nhắc khi đổi tài khoản hoặc đăng xuất. */
  fun cancelAll()

  /** Gửi một thông báo thử cho nút thử trong Settings. */
  fun sendTest(kind: ReminderTestKind)

  /** Android: có quyền exact alarm hay không; iOS luôn trả về true. */
  fun isExactDeliveryAvailable(): Boolean

  /** Độ phủ lịch (số đã đặt, số bị cắt, mốc cuối); UI hiện dòng mở app để cập nhật khi có mục bị cắt. */
  val coverage: StateFlow<ReminderCoverage>
}

/** Cầu nối tới hệ thông báo gốc; Swift triển khai, không dùng suspend nên kết quả trả qua callback. */
interface NotificationGateway {
  /** Thay toàn bộ request do planner sở hữu (tiền tố "inlove.plan."). Không đụng request snooze. */
  fun replaceAll(
    items: List<ScheduledReminder>,
    soundEnabled: Boolean,
    snoozeTitle: String,
    onDone: (scheduled: Int) -> Unit
  )

  /** Hủy request do planner sở hữu, báo xong qua onDone. */
  fun cancelAll(onDone: () -> Unit)

  /** Đăng ngay một thông báo thử, dùng cho sendTest. */
  fun postTest(title: String, body: String, route: String)
}

/** Nguồn đọc dữ liệu đầu vào cho planner; P2 triển khai. */
interface ReminderInputSource {
  /** Đọc dữ liệu hiện tại thành ReminderPlanInput. */
  suspend fun load(): ReminderPlanInput
}
