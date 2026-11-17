package com.example.alarm.plan.fakes

import com.example.alarm.plan.NotificationGateway
import com.example.alarm.plan.ScheduledReminder

/** Cổng thông báo giả cho test: ghi lại lệnh gọi, tự báo xong hoặc giữ callback cho test tự hoàn tất. */
class FakeNotificationGateway : NotificationGateway {
  data class ReplaceCall(
    val items: List<ScheduledReminder>,
    val soundEnabled: Boolean,
    val snoozeTitle: String
  )

  data class PostedTest(val title: String, val body: String, val route: String)

  /** Thứ tự gọi: "replaceAll", "cancelAll", "postTest". */
  val events = mutableListOf<String>()
  val replaceCalls = mutableListOf<ReplaceCall>()
  val posted = mutableListOf<PostedTest>()
  var cancelAllCount = 0
    private set

  /** false: onDone được gọi ngay trong lệnh gọi; true: giữ lại, test gọi completeNextReplace/completeNextCancel. */
  var holdCallbacks = false

  /** Chế độ tự báo xong: null nghĩa là báo đúng số mục nhận được. */
  var doneCount: Int? = null
  var failReplaceWith: Throwable? = null
  var failCancelWith: Throwable? = null

  private val pendingReplace = ArrayDeque<(Int) -> Unit>()
  private val pendingCancel = ArrayDeque<() -> Unit>()

  override fun replaceAll(
    items: List<ScheduledReminder>,
    soundEnabled: Boolean,
    snoozeTitle: String,
    onDone: (scheduled: Int) -> Unit
  ) {
    events.add("replaceAll")
    replaceCalls.add(ReplaceCall(items, soundEnabled, snoozeTitle))
    failReplaceWith?.let { throw it }
    if (holdCallbacks) pendingReplace.addLast(onDone) else onDone(doneCount ?: items.size)
  }

  override fun cancelAll(onDone: () -> Unit) {
    events.add("cancelAll")
    cancelAllCount++
    failCancelWith?.let { throw it }
    if (holdCallbacks) pendingCancel.addLast(onDone) else onDone()
  }

  override fun postTest(title: String, body: String, route: String) {
    events.add("postTest")
    posted.add(PostedTest(title, body, route))
  }

  fun completeNextReplace(count: Int) {
    pendingReplace.removeFirst()(count)
  }

  fun completeNextCancel() {
    pendingCancel.removeFirst()()
  }
}
