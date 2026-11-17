package com.example.alarm.plan

import kotlinx.datetime.TimeZone

object ReminderPlanner {

  /** Gộp luật I1-I8, sắp theo giờ bắn, cắt còn maxItems, che chữ khi redactContent (kế hoạch p5 mục 4.1.5 điểm 10 và 11). */
  fun plan(input: ReminderPlanInput, nowMillis: Long, zone: TimeZone, maxItems: Int): ReminderPlan {
    val sorted = (UserItemRules.plan(input, nowMillis, zone) + LoveMilestoneRules.plan(input, nowMillis, zone))
      .sortedWith(compareBy<PlannedNotification> { it.fireAtEpochMillis }.thenBy { it.key.stableId })
    val kept = sorted.take(maxItems.coerceAtLeast(0))
    // Che chữ sau khi cắt: không đổi số mục, số bị cắt hay đường chân trời.
    val items = if (input.redactContent) {
      val r = ReminderTexts.redacted(input.isEnglish)
      kept.map { it.copy(title = r.title, body = r.body) }
    } else {
      kept
    }
    return ReminderPlan(items, sorted.size - kept.size, kept.lastOrNull()?.fireAtEpochMillis)
  }
}
