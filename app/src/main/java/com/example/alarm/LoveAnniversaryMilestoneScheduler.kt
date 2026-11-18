package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.example.alarm.plan.LoveMilestoneRules
import com.example.alarm.plan.ReminderKind
import com.example.alarm.plan.ReminderTextArgs
import com.example.alarm.plan.ReminderTexts
import com.example.data.db.AccountDataVault
import com.example.data.db.InLoveDao
import com.example.data.model.RelationshipStatus
import com.example.ui.util.AppLanguage
import com.example.ui.util.LocaleManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Data model representing a calculated Love Anniversary Milestone.
 */
data class LoveMilestoneInfo(
  val id: Long,
  val title: String,
  val milestoneDays: Int,
  val targetDateMillis: Long,
  val formattedDate: String,
  val daysRemaining: Int,
  val isAnnual: Boolean = false,
  val emoji: String = "💖",
  val description: String = ""
)

/**
 * Local Notification Scheduler that alerts users about upcoming love anniversary
 * milestones based on the couple start date stored in Firestore.
 */
object LoveAnniversaryMilestoneScheduler {

  private const val TAG = "MilestoneScheduler"

  // Base ID offsets for notification alarms to avoid collision
  private const val ADVANCE_3_DAYS_OFFSET = 300000L
  private const val ADVANCE_1_DAY_OFFSET = 400000L
  private const val DAY_OF_OFFSET = 500000L

  // The clock of this scheduler (the day-count cutoff and the per-alarm checks); only tests move it.
  internal var nowMillis: () -> Long = { System.currentTimeMillis() }

  // Local midnight of the day that holds [millis].
  private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
  }.timeInMillis

  /**
   * Standard key day-count milestones to monitor
   */
  val STANDARD_DAY_MILESTONES = LoveMilestoneRules.DAY_MILESTONES.map { it.days to it.vi }

  /**
   * Parses start date into millis, supporting timestamp, date string ("dd/MM/yyyy", "yyyy-MM-dd", etc.)
   */
  fun parseStartDateMillis(startDateMillis: Long, startDateText: String): Long {
    if (startDateMillis > 0) return startDateMillis

    val trimmed = startDateText.trim()
    if (trimmed.isEmpty()) return System.currentTimeMillis()

    val formats = listOf("dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "d/M/yyyy")
    for (fmt in formats) {
      try {
        val sdf = SimpleDateFormat(fmt, Locale.ROOT).apply { isLenient = false }
        val date = sdf.parse(trimmed)
        if (date != null) return date.time
      } catch (_: Exception) {}
    }
    return System.currentTimeMillis()
  }

  /**
   * Computes all upcoming milestones from the Firestore start date for the next 2 years.
   */
  fun getUpcomingMilestones(
    startDateMillis: Long,
    startDateText: String,
    isEnglish: Boolean = false,
    forScheduling: Boolean = false
  ): List<LoveMilestoneInfo> {
    val startMillis = parseStartDateMillis(startDateMillis, startDateText)
    val now = nowMillis()
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)

    val results = mutableListOf<LoveMilestoneInfo>()

    // 1. Calculate Day-Count Milestones
    for (m in LoveMilestoneRules.DAY_MILESTONES) {
      val days = m.days
      val name = if (isEnglish) m.en else m.vi
      // Bộ đếm tính cả ngày bắt đầu (ngày 1), nên ngày thứ N là start + (N - 1) ngày.
      val targetMillis = Calendar.getInstance().apply {
        timeInMillis = startMillis
        add(Calendar.DAY_OF_YEAR, days - 1)
      }.timeInMillis
      // Scheduling keeps a milestone day until the day is over: its day-of alert is 08:30, later than the
      // midnight a text-parsed start gives. Every alarm is still checked against now when it is armed.
      val eligible = if (forScheduling) targetMillis >= startOfDay(now) else targetMillis > now
      if (eligible) {
        val daysRemaining = TimeUnit.MILLISECONDS.toDays(targetMillis - now).toInt().coerceAtLeast(0)
        results.add(
          LoveMilestoneInfo(
            id = days.toLong(),
            title = name,
            milestoneDays = days,
            targetDateMillis = targetMillis,
            formattedDate = sdf.format(Date(targetMillis)),
            daysRemaining = daysRemaining,
            isAnnual = false,
            emoji = if (days >= 365) "👑" else if (days >= 100) "💎" else "💖",
            description = if (isEnglish) {
              "Milestone of $days days together filled with memories"
            } else {
              "Cột mốc $days ngày bên nhau đong đầy kỷ niệm"
            }
          )
        )
      }
    }

    // 2. Calculate Annual Anniversaries for the next 5 years
    val startCal = Calendar.getInstance().apply { timeInMillis = startMillis }
    val startDay = startCal.get(Calendar.DAY_OF_MONTH)
    val startMonth = startCal.get(Calendar.MONTH) // 0-11
    val startYear = startCal.get(Calendar.YEAR)

    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    for (year in currentYear..(currentYear + 4)) {
      val targetCal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, startMonth)
        set(Calendar.DAY_OF_MONTH, startDay)
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }

      val targetMillis = targetCal.timeInMillis
      val yearsInLove = year - startYear
      if (yearsInLove > 0 && targetMillis > now) {
        val daysRemaining = TimeUnit.MILLISECONDS.toDays(targetMillis - now).toInt().coerceAtLeast(0)
        results.add(
          LoveMilestoneInfo(
            id = 10000L + yearsInLove,
            title = LoveMilestoneRules.annualName(yearsInLove, isEnglish),
            milestoneDays = yearsInLove * 365,
            targetDateMillis = targetMillis,
            formattedDate = sdf.format(Date(targetMillis)),
            daysRemaining = daysRemaining,
            isAnnual = true,
            emoji = "💍",
            description = if (isEnglish) {
              "Celebrating $yearsInLove years of lasting love and happiness"
            } else {
              "Tròn $yearsInLove năm tình yêu bền vững và hạnh phúc"
            }
          )
        )
      }
    }

    return results.sortedBy { it.targetDateMillis }
  }

  /**
   * Schedules alarms for upcoming milestones based on the Firestore start date.
   * Sets up 3 alerts per milestone:
   * 1. Advance: 3 days before at 09:00 AM
   * 2. Advance: 1 day before at 09:00 AM
   * 3. Day of: On the milestone day at 08:30 AM
   */
  fun scheduleMilestonesFromFirestore(
    context: Context,
    startDateMillis: Long,
    startDateText: String,
    partnerName: String = ""
  ): Int {
    synchronized(armLock) {
      bootCopyVersion++
      bootPrefs(context).edit {
        putLong(KEY_START_MILLIS, startDateMillis)
        putString(KEY_START_TEXT, startDateText)
        putString(KEY_PARTNER, partnerName)
        putString(KEY_TOKEN, AccountDataVault.currentToken(context))
      }
    }
    val isEnglish = LocaleManager.getInitialLanguage(context) == AppLanguage.EN
    val milestones = getUpcomingMilestones(startDateMillis, startDateText, isEnglish, forScheduling = true)
    var scheduledCount = 0

    val now = nowMillis()

    for (ms in milestones) {
      val targetCal = Calendar.getInstance().apply {
        timeInMillis = ms.targetDateMillis
      }
      val textArgs = ReminderTextArgs(title = ms.title, dateText = ms.formattedDate, partnerName = partnerName)
      val dayOfText = ReminderTexts.of(ReminderKind.LOVE_MILESTONE_DAY_OF, isEnglish, textArgs)
      val oneDayText = ReminderTexts.of(ReminderKind.LOVE_MILESTONE_ONE_DAY, isEnglish, textArgs)
      val threeDaysText = ReminderTexts.of(ReminderKind.LOVE_MILESTONE_THREE_DAYS, isEnglish, textArgs)

      // 1. Day-of Alert at 08:30 AM
      val dayOfCal = Calendar.getInstance().apply {
        set(Calendar.YEAR, targetCal.get(Calendar.YEAR))
        set(Calendar.MONTH, targetCal.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, targetCal.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 8)
        set(Calendar.MINUTE, 30)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }

      if (dayOfCal.timeInMillis > now) {
        val scheduled = AlarmNotificationScheduler.scheduleAlarm(
          context = context,
          reminderId = DAY_OF_OFFSET + ms.id,
          title = dayOfText.title,
          message = dayOfText.body,
          triggerAtMillis = dayOfCal.timeInMillis,
          action = ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM,
          channelId = ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_ID,
          targetTab = "home"
        )
        if (scheduled) scheduledCount++
      }

      // 2. Advance: 1 Day Before at 09:00 AM
      val oneDayBeforeCal = Calendar.getInstance().apply {
        timeInMillis = dayOfCal.timeInMillis
        add(Calendar.DAY_OF_YEAR, -1)
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
      }
      if (oneDayBeforeCal.timeInMillis > now) {
        val scheduled = AlarmNotificationScheduler.scheduleAlarm(
          context = context,
          reminderId = ADVANCE_1_DAY_OFFSET + ms.id,
          title = oneDayText.title,
          message = oneDayText.body,
          triggerAtMillis = oneDayBeforeCal.timeInMillis,
          action = ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM,
          channelId = ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_ID,
          targetTab = "calendar"
        )
        if (scheduled) scheduledCount++
      }

      // 3. Advance: 3 Days Before at 09:00 AM
      val threeDaysBeforeCal = Calendar.getInstance().apply {
        timeInMillis = dayOfCal.timeInMillis
        add(Calendar.DAY_OF_YEAR, -3)
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
      }
      if (threeDaysBeforeCal.timeInMillis > now) {
        val scheduled = AlarmNotificationScheduler.scheduleAlarm(
          context = context,
          reminderId = ADVANCE_3_DAYS_OFFSET + ms.id,
          title = threeDaysText.title,
          message = threeDaysText.body,
          triggerAtMillis = threeDaysBeforeCal.timeInMillis,
          action = ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM,
          channelId = ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_ID,
          targetTab = "calendar"
        )
        if (scheduled) scheduledCount++
      }
    }

    Log.d(TAG, "Scheduled $scheduledCount Firestore love anniversary milestone alerts.")
    return scheduledCount
  }

  /**
   * Cancels every love-milestone alarm (3 days, 1 day, day of) this object may have scheduled.
   * Used when the couple is unpaired: the ex's milestones must not fire. They are rebuilt by
   * [scheduleMilestonesFromFirestore] once a current relationship exists again.
   */
  fun cancelMilestones(context: Context) {
    forgetBootCopy(context)
    // ponytail: sweeps annual milestones up to year 100, raise if ever needed.
    val ids = STANDARD_DAY_MILESTONES.map { it.first.toLong() } + (1L..100L).map { 10_000L + it }
    for (id in ids) {
      for (offset in listOf(ADVANCE_3_DAYS_OFFSET, ADVANCE_1_DAY_OFFSET, DAY_OF_OFFSET)) {
        AlarmNotificationScheduler.cancelAlarm(context, offset + id, ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM)
      }
    }
  }

  internal data class BootCopy(val startMillis: Long, val startText: String, val partner: String, val token: String)

  private const val BOOT_PREFS = "love_milestone_boot"
  private const val KEY_START_MILLIS = "start_millis"
  private const val KEY_START_TEXT = "start_text"
  private const val KEY_PARTNER = "partner"
  private const val KEY_TOKEN = "token"

  // Guards the boot copy and its version. Every write and every forget of the copy bumps the version, and a replay arms only while
  // the version is still the one it read the copy at, so a cancel stops it even if an identical copy is written again afterwards.
  private val armLock = Any()
  private var bootCopyVersion = 0L

  private fun bootPrefs(context: Context) =
    context.applicationContext.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE)

  internal fun readBootCopy(context: Context): BootCopy? {
    val p = bootPrefs(context)
    val token = p.getString(KEY_TOKEN, null) ?: return null
    return BootCopy(
      p.getLong(KEY_START_MILLIS, 0L),
      p.getString(KEY_START_TEXT, "") ?: "",
      p.getString(KEY_PARTNER, "") ?: "",
      token
    )
  }

  internal fun forgetBootCopy(context: Context) {
    synchronized(armLock) {
      bootCopyVersion++
      bootPrefs(context).edit { clear() }
    }
  }

  // A stale replay may erase the copy only while nothing wrote or forgot it since it read it, so a newer account's copy survives.
  private fun forgetBootCopyIfUnchanged(context: Context, version: Long) {
    synchronized(armLock) { if (bootCopyVersion == version) forgetBootCopy(context) }
  }

  internal suspend fun rearmFromBootCopy(context: Context, dao: InLoveDao): Int {
    val (copy, version) = synchronized(armLock) { readBootCopy(context) to bootCopyVersion }
    if (copy == null) return 0
    if (copy.token != AccountDataVault.currentToken(context)) return 0
    val isGuest = copy.token == AccountDataVault.tokenOf(AccountDataVault.GUEST)
    val owner = if (isGuest) null else dao.getAllUserAccounts().first()
      .firstOrNull { AccountDataVault.tokenOf(AccountDataVault.scopeOf(it.email)) == copy.token }
    if (!isGuest && (owner == null || owner.sessionToken.isBlank())) {
      forgetBootCopyIfUnchanged(context, version)
      return 0
    }
    val mine = if (owner == null) emptyList() else dao.getAllRelationshipsSync()
      .filter { it.user1 == owner.uid || it.user2 == owner.uid }
    if (mine.isNotEmpty() && mine.all { it.status == RelationshipStatus.TERMINATED }) {
      forgetBootCopyIfUnchanged(context, version)
      return 0
    }
    val live = mine.filter { it.status != RelationshipStatus.TERMINATED }
    val backed = if (copy.startMillis > 0) {
      live.any { it.startDate == copy.startMillis }
    } else {
      copy.startText.isNotBlank() &&
        (dao.getCoupleProfileSync()?.anniversaryDate == copy.startText || live.any { it.startDateText == copy.startText })
    }
    if (!backed) {
      forgetBootCopyIfUnchanged(context, version)
      return 0
    }
    // A cancel or a newer copy bumps the version, a switch changes the token and a switch in progress raises the vault fence,
    // so a replay that lost the race, or that starts inside a switch, gives up here.
    return synchronized(armLock) {
      val stamp = AccountDataVault.transitionStamp()
      if (stamp < 0 || bootCopyVersion != version || copy.token != AccountDataVault.currentToken(context)) {
        0
      } else {
        var armed = scheduleMilestonesFromFirestore(context, copy.startMillis, copy.startText, copy.partner)
        var token = copy.token
        val adopted = AccountDataVault.currentToken(context)
        // A registration adopts the guest data without a transition and the data stays where it is, so the validated copy is still right:
        // arm it once more under the account that now owns it. That rewrites the copy and restamps every alarm with the new token.
        if (adopted != token && AccountDataVault.transitionStamp() == stamp) {
          token = adopted
          armed = scheduleMilestonesFromFirestore(context, copy.startMillis, copy.startText, copy.partner)
        }
        // The vault changes the scope without armLock. A switch that began, ran or ended while the loop armed leaves alarms stamped
        // with the new token and this copy's data, or alarms its cancel hook is about to sweep, so take it all back.
        if (AccountDataVault.transitionStamp() == stamp && AccountDataVault.currentToken(context) == token) {
          armed
        } else {
          cancelMilestones(context)
          0
        }
      }
    }
  }

  /**
   * Fires an immediate test milestone alert so the user can test their notifications,
   * sound, vibration, and banner.
   */
  fun triggerInstantTestMilestone(
    context: Context,
    partnerName: String = "người ấy",
    sampleMilestoneTitle: String = "💎 Bách Nhật Yêu (100 Ngày)"
  ) {
    val isEnglish = LocaleManager.getInitialLanguage(context) == AppLanguage.EN
    val (title, message) = if (isEnglish) {
      val localizedPartner = if (partnerName.isBlank() || partnerName == "người ấy") "your partner" else partnerName
      val localizedMilestone = if (sampleMilestoneTitle == "💎 Bách Nhật Yêu (100 Ngày)") "💎 100 Days in Love" else sampleMilestoneTitle
      "🔔 Test: $localizedMilestone ❤️" to
        "Your love milestone notifications are working! You'll get alerts 3 days before, 1 day before, and on the milestone day with $localizedPartner! ✨"
    } else {
      val name = partnerName.ifBlank { "người ấy" }
      "🔔 Thử Nghiệm: Cột Mốc $sampleMilestoneTitle ❤️" to
        "Thông báo nhắc nhở cột mốc kỷ niệm tình yêu từ Firestore hoạt động hoàn hảo! Bạn sẽ nhận được thông báo trước 3 ngày, 1 ngày và vào đúng ngày kỷ niệm cùng $name! ✨"
    }
    ReminderAlarmReceiver.showNotification(
      context = context,
      title = title,
      message = message,
      notificationId = 8888,
      channelId = ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_ID,
      targetTab = "calendar"
    )
  }
}
