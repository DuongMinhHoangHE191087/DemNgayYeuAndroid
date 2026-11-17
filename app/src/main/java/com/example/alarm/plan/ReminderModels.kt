package com.example.alarm.plan

import kotlinx.datetime.LocalDateTime

/** Khóa nhắc hẹn. stableId ổn định qua các lần lập lịch lại, nhờ đó request cũ được thay hoặc hủy đúng. */
sealed interface ReminderKey {
  val stableId: String

  data class Advance(val id: Long) : ReminderKey {
    override val stableId: String get() = "adv.$id"
  }

  data class DayOf(val id: Long) : ReminderKey {
    override val stableId: String get() = "dayof.$id"
  }

  data class UserMilestone(val id: Long) : ReminderKey {
    override val stableId: String get() = "ums.$id"
  }

  data object CoupleAnniversary : ReminderKey {
    override val stableId: String get() = "couple"
  }

  data class LoveMilestoneThreeDays(val msId: Long) : ReminderKey {
    override val stableId: String get() = "lms3.$msId"
  }

  data class LoveMilestoneOneDay(val msId: Long) : ReminderKey {
    override val stableId: String get() = "lms1.$msId"
  }

  data class LoveMilestoneDayOf(val msId: Long) : ReminderKey {
    override val stableId: String get() = "lmsd.$msId"
  }

  data class Custom(val id: Long) : ReminderKey {
    override val stableId: String get() = "custom.$id"
  }
}

enum class ReminderKind {
  ADVANCE,
  DAY_OF,
  USER_MILESTONE,
  COUPLE_ANNIVERSARY,
  LOVE_MILESTONE_THREE_DAYS,
  LOVE_MILESTONE_ONE_DAY,
  LOVE_MILESTONE_DAY_OF,
  CUSTOM
}

// Tên tạm; tên cuối được chốt ở N-07a khi đọc lại nơi gọi.
enum class ReminderTestKind { ANNIVERSARY, MILESTONE, CUSTOM }

data class ReminderPlanInput(
  val anniversaries: List<AnniversaryItem> = emptyList(),
  val milestones: List<MilestoneItem> = emptyList(),
  val couple: CoupleItem? = null,
  val customReminders: List<CustomReminderItem> = emptyList(),
  val disabledCadenceKeys: Set<String> = emptySet(),
  val anniversaryNotificationsEnabled: Boolean = true,
  val soundEnabled: Boolean = true,
  val isEnglish: Boolean = false,
  val ownerId: String = "",
  // Bật khi PIN app đang khóa: thông báo chỉ dùng chữ chung, không lộ tên hay chữ người dùng nhập.
  val redactContent: Boolean = false
)

data class AnniversaryItem(
  val id: Long,
  val title: String,
  val dateText: String,
  val isAnnual: Boolean,
  val notificationEnabled: Boolean,
  val reminderDaysBefore: Int
)

data class MilestoneItem(
  val id: Long,
  val title: String,
  val dateText: String,
  val notificationEnabled: Boolean,
  val isPast: Boolean
)

data class CoupleItem(
  val title: String,
  val startDate: String,
  val partnerName: String
)

data class CustomReminderItem(
  val id: Long,
  val title: String,
  val details: String,
  val alarmEpochMillis: Long
)

data class PlannedNotification(
  val key: ReminderKey,
  val kind: ReminderKind,
  val fireAt: LocalDateTime,
  val fireAtEpochMillis: Long,
  val title: String,
  val body: String,
  // String vì Android hiện truyền EXTRA_TARGET_TAB dạng chuỗi, chưa có kiểu AppRoute.
  val route: String,
  val repeatsYearly: Boolean = false,
  val isAbsolute: Boolean = false
)

data class ReminderPlan(
  val items: List<PlannedNotification>,
  val droppedCount: Int,
  val horizonEpochMillis: Long?
)

data class ReminderCoverage(
  val scheduled: Int,
  val dropped: Int,
  val horizonEpochMillis: Long?
)

// Kiểu phẳng để Swift dùng dễ: chỉ số, chuỗi và bool, không enum hay LocalDateTime.
data class ScheduledReminder(
  val stableId: String,
  val fireYear: Int,
  val fireMonth: Int,
  val fireDay: Int,
  val fireHour: Int,
  val fireMinute: Int,
  val fireEpochMillis: Long,
  val repeatsYearly: Boolean,
  val isAbsolute: Boolean,
  val ownerId: String,
  val title: String,
  val body: String,
  val route: String,
  val kind: String
)
