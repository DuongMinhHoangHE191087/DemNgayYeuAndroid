package com.example.ui.viewmodel.controllers

import com.example.shared.calendar.AnniversaryBehaviorEngine
import com.example.shared.calendar.LunarCalendar2025To2040
import com.example.shared.calendar.LunarHolidayId
import com.example.shared.calendar.MonthiversaryOccurrence
import com.example.shared.calendar.NextOccasionReadiness
import com.example.shared.calendar.PreparationStage
import java.time.LocalDate as JvmLocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate as KmpLocalDate

/**
 * Pure controller bridging Android date strings with `:shared`'s [AnniversaryBehaviorEngine]
 * to expose next occasion readiness, sacred celebration-day state, monthiversaries, and Thất Tịch.
 */
class AnniversaryReadinessController {

  private val _nextReadiness = MutableStateFlow<NextOccasionReadiness?>(null)
  val nextReadiness: StateFlow<NextOccasionReadiness?> = _nextReadiness.asStateFlow()

  private val _upcomingReadiness = MutableStateFlow<List<NextOccasionReadiness>>(emptyList())
  val upcomingReadiness: StateFlow<List<NextOccasionReadiness>> = _upcomingReadiness.asStateFlow()

  private val _nextMonthiversary = MutableStateFlow<MonthiversaryOccurrence?>(null)
  val nextMonthiversary: StateFlow<MonthiversaryOccurrence?> = _nextMonthiversary.asStateFlow()

  private val _isSacredCelebrationToday = MutableStateFlow(false)
  val isSacredCelebrationToday: StateFlow<Boolean> = _isSacredCelebrationToday.asStateFlow()

  private val _nextThatTichIso = MutableStateFlow<String?>(null)
  val nextThatTichIso: StateFlow<String?> = _nextThatTichIso.asStateFlow()

  fun refresh(
    anniversaryDateText: String,
    partnerBirthDateText: String,
    region: String = "VN",
    includeMonthiversary: Boolean = true,
    todayOverride: KmpLocalDate? = null,
  ) {
    val today = todayOverride ?: currentKmpDate()
    val loveStart = parseKmpDate(anniversaryDateText)?.takeIf { it <= today }
    val partnerBirthday = parseKmpDate(partnerBirthDateText)
    val effectiveRegion = region.trim().ifBlank { "VN" }

    val upcoming = AnniversaryBehaviorEngine.upcomingOccasions(
      today = today,
      loveStartDate = loveStart,
      partnerBirthday = partnerBirthday,
      region = effectiveRegion,
      withinDays = 180,
      includeMonthiversary = includeMonthiversary,
    )
    _upcomingReadiness.value = upcoming
    _nextReadiness.value = AnniversaryBehaviorEngine.computeNextOccasionReadiness(
      today = today,
      loveStartDate = loveStart,
      partnerBirthday = partnerBirthday,
      region = effectiveRegion,
      includeMonthiversary = includeMonthiversary,
    )
    _nextMonthiversary.value = loveStart?.let { AnniversaryBehaviorEngine.nextMonthiversary(it, today) }
    _isSacredCelebrationToday.value = AnniversaryBehaviorEngine.isSacredCelebrationToday(
      today = today,
      loveStartDate = loveStart,
      partnerBirthday = partnerBirthday,
    )
    _nextThatTichIso.value = LunarCalendar2025To2040.nextOccurrence(LunarHolidayId.THAT_TICH, today)?.toString()
  }

  fun isTodayCelebration(): Boolean =
    _isSacredCelebrationToday.value ||
      _nextReadiness.value?.stage == PreparationStage.TODAY_CELEBRATION

  private fun currentKmpDate(): KmpLocalDate {
    val now = JvmLocalDate.now()
    return KmpLocalDate(now.year, now.monthValue, now.dayOfMonth)
  }

  internal fun parseKmpDate(raw: String): KmpLocalDate? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.equals("Hôm nay", ignoreCase = true) || trimmed.equals("Today", ignoreCase = true)) {
      return null
    }
    if ('/' in trimmed) {
      val parts = trimmed.split('/')
      if (parts.size == 3) {
        val d = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val y = parts[2].toIntOrNull() ?: return null
        return runCatching { KmpLocalDate(y, m, d) }.getOrNull()
      }
    }
    if ('-' in trimmed) {
      return runCatching { KmpLocalDate.parse(trimmed.take(10)) }.getOrNull()
    }
    return null
  }
}
