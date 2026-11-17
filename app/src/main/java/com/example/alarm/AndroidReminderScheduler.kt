package com.example.alarm

import android.content.Context
import android.util.Log
import com.example.alarm.plan.ReminderCoverage
import com.example.alarm.plan.ReminderKey
import com.example.alarm.plan.ReminderScheduler
import com.example.alarm.plan.ReminderTestKind
import com.example.data.db.InLoveDao
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Đầu vào cột mốc yêu do lớp dữ liệu sở hữu; nhà cung cấp trả null nghĩa là bỏ qua cột mốc Firestore. */
data class LoveMilestoneArgs(val startDateMillis: Long, val startDateText: String, val partnerName: String)

/** Cài đặt Android của ReminderScheduler: chỉ gọi lại mã cũ, hành vi Android không đổi. */
class AndroidReminderScheduler(
  context: Context,
  private val dao: InLoveDao,
  private val milestoneArgs: () -> LoveMilestoneArgs?,
  scope: CoroutineScope
) : ReminderScheduler {

  private val context = context.applicationContext
  private val _coverage = MutableStateFlow(ReminderCoverage(0, 0, null))

  /** Độ phủ lịch sau lần đồng bộ gần nhất. */
  override val coverage: StateFlow<ReminderCoverage> = _coverage.asStateFlow()

  private val runner = ReminderCommandRunner(
    scope = scope,
    onSync = { resyncNow() },
    onCancelAll = { cancelEverything() },
    onError = { Log.e(TAG, "Reminder command failed", it) }
  )

  /** Số lệnh đã chạy xong, dùng cho test. */
  internal val completedRuns: StateFlow<Int> get() = runner.completed

  /** Xếp một lần lập lại lịch, nhiều yêu cầu liền nhau gộp thành một. */
  override fun requestResync() = runner.requestSync()

  /** Hủy mọi nhắc, không chờ kết quả. */
  override fun cancelAll() = runner.requestCancelAll()

  /** Hủy mọi nhắc và chỉ trả về khi đã hủy xong. */
  suspend fun cancelAllNow() = runner.cancelAllAndWait()

  /** Hủy từng PendingIntent theo mã và action cũ; chạy ngay, không xếp hàng. */
  override fun cancel(keys: List<ReminderKey>) {
    for (key in keys) {
      AlarmNotificationScheduler.cancelAlarm(context, legacyRequestCode(key).toLong(), legacyAction(key))
    }
  }

  /** Thân thật của đồng bộ: mã cũ chạy trước, rồi cột mốc Firestore nếu có đầu vào. */
  internal suspend fun resyncNow() {
    var scheduled = AlarmNotificationScheduler.scheduleAllAnniversariesFromDb(context, dao)
    val args = milestoneArgs()
    if (args != null) {
      scheduled += LoveAnniversaryMilestoneScheduler.scheduleMilestonesFromFirestore(
        context,
        args.startDateMillis,
        args.startDateText,
        args.partnerName
      )
    } else {
      LoveAnniversaryMilestoneScheduler.forgetBootCopy(context)
    }
    _coverage.value = ReminderCoverage(scheduled, 0, null)
  }

  /** Thân thật của hủy: hủy cả cột mốc Firestore và 400001 mà cancelOutgoingAccountAlarms bỏ sót. */
  internal suspend fun cancelEverything() {
    _coverage.value = ReminderCoverage(0, 0, null)
    // Quên bản sao khởi động trước mọi thao tác hủy: lần dựng lại lúc khởi động đang dở sẽ thấy nó đã mất và dừng.
    LoveAnniversaryMilestoneScheduler.forgetBootCopy(context)
    cancel(listOf(ReminderKey.CoupleAnniversary))
    AlarmNotificationScheduler.cancelAlarm(context, DAILY_CHECK_CODE, ReminderAlarmReceiver.ACTION_DAILY_ANNIVERSARY_CHECK)
    // ponytail: vét cạn đến năm kỷ niệm thứ 100, nâng mức này nếu cần.
    val loveIds = LoveAnniversaryMilestoneScheduler.STANDARD_DAY_MILESTONES.map { it.first.toLong() } +
      (1L..100L).map { 10_000L + it }
    cancel(
      loveIds.flatMap {
        listOf(
          ReminderKey.LoveMilestoneThreeDays(it),
          ReminderKey.LoveMilestoneOneDay(it),
          ReminderKey.LoveMilestoneDayOf(it)
        )
      }
    )
    cancel(dao.getAnniversaryDatesList().flatMap { listOf(ReminderKey.Advance(it.id), ReminderKey.DayOf(it.id)) })
    cancel(dao.getMilestonesList().map { ReminderKey.UserMilestone(it.id) })
    cancel(dao.getCustomRemindersList().map { ReminderKey.Custom(it.id) })
  }

  /** Gửi ngay một thông báo thử trên luồng của người gọi. */
  override fun sendTest(kind: ReminderTestKind) {
    when (kind) {
      ReminderTestKind.ANNIVERSARY -> AlarmNotificationScheduler.triggerInstantTest(context)
      ReminderTestKind.MILESTONE -> {
        val name = milestoneArgs()?.partnerName
        if (name != null) {
          LoveAnniversaryMilestoneScheduler.triggerInstantTestMilestone(context, partnerName = name)
        } else {
          LoveAnniversaryMilestoneScheduler.triggerInstantTestMilestone(context)
        }
      }
      ReminderTestKind.CUSTOM -> AlarmNotificationScheduler.triggerInstantTest(
        context,
        "Thử nghiệm báo thức tình yêu ❤️",
        "Chuông và thông báo kỷ niệm hoạt động rất tốt!"
      )
    }
  }

  /** Có quyền exact alarm hay không. */
  override fun isExactDeliveryAvailable(): Boolean = AlarmNotificationScheduler.exactAlarmsGranted(context)

  private companion object {
    const val TAG = "AndroidReminderScheduler"

    // Cùng hằng số 500001 với AlarmNotificationScheduler.scheduleDailyMorningCheck.
    const val DAILY_CHECK_CODE = 500001L
  }
}

/** Một bộ thu lệnh duy nhất, chạy tuần tự: đồng bộ gộp được, hủy thì không bao giờ gộp. */
internal class ReminderCommandRunner(
  scope: CoroutineScope,
  private val onSync: suspend () -> Unit,
  private val onCancelAll: suspend () -> Unit,
  private val onError: (Exception) -> Unit
) {
  private class Cmd(val sync: Boolean, val done: CompletableDeferred<Unit>? = null)

  private val commands = Channel<Cmd>(Channel.UNLIMITED)
  private val syncQueued = AtomicBoolean(false)
  private val _completed = MutableStateFlow(0)

  /** Số lệnh đã chạy xong, kể cả lệnh bị lỗi. */
  val completed: StateFlow<Int> = _completed.asStateFlow()

  // ponytail: nếu scope bị hủy thì lệnh đang chờ không bao giờ chạy và cancelAllAndWait() chờ mãi; nơi nối dây truyền scope của ứng dụng, nâng cấp = đóng channel trong finally của bộ thu và hoàn tất các done đang chờ.
  init {
    scope.launch {
      for (cmd in commands) {
        // Đặt lại cờ trước khi chạy: yêu cầu đến lúc thân đang treo sẽ xếp đúng một lần chạy nữa.
        if (cmd.sync) syncQueued.set(false)
        try {
          if (cmd.sync) onSync() else onCancelAll()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          onError(e)
        } finally {
          cmd.done?.complete(Unit)
          _completed.update { it + 1 }
        }
      }
    }
  }

  /** Xếp một lần đồng bộ; bỏ qua nếu đã có một lần đang chờ. */
  fun requestSync() {
    if (syncQueued.compareAndSet(false, true)) commands.trySend(Cmd(true))
  }

  /** Xếp một lệnh hủy; đặt lại cờ để đồng bộ đến sau nó không bị bỏ. */
  fun requestCancelAll() {
    syncQueued.set(false)
    commands.trySend(Cmd(false))
  }

  /** Như requestCancelAll nhưng chờ đến khi thân đã chạy xong, kể cả khi thân lỗi. */
  suspend fun cancelAllAndWait() {
    val done = CompletableDeferred<Unit>()
    syncQueued.set(false)
    commands.trySend(Cmd(false, done))
    done.await()
  }
}
