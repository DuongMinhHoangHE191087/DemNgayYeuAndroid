package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.OnSurface
import com.example.ui.theme.OnSurfaceVariant
import com.example.ui.theme.Primary
import com.example.ui.theme.SurfaceContainerHigh
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Utility functions for DatePicker conversions between UTC Millis and localized date strings.
 */
object DatePickerUtils {

  fun formatUtcMillisToDate(utcMillis: Long, pattern: String = "dd/MM/yyyy"): String {
    val sdf = SimpleDateFormat(pattern, Locale.getDefault()).apply {
      timeZone = TimeZone.getTimeZone("UTC")
    }
    return sdf.format(Date(utcMillis))
  }

  fun parseDateToUtcMillis(dateStr: String): Long? {
    val formats = listOf("dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy", "yyyy-MM-dd")
    for (fmt in formats) {
      try {
        val sdf = SimpleDateFormat(fmt, Locale.getDefault()).apply {
          timeZone = TimeZone.getTimeZone("UTC")
          isLenient = false
        }
        val parsed = sdf.parse(dateStr.trim())
        if (parsed != null) return parsed.time
      } catch (_: Exception) {}
    }
    return null
  }

  /** Whole calendar days from today to the date (negative = past), or null if unparseable. */
  fun daysUntil(dateStr: String): Int? {
    val millis = parseDateToUtcMillis(dateStr) ?: return null
    val target = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
    return java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), target).toInt()
  }

  /** Localized day-of-week name for a "dd/MM/yyyy"-ish date string, or null if unparseable. */
  fun getDayOfWeek(dateStr: String, isEnglish: Boolean = false): String? {
    val millis = parseDateToUtcMillis(dateStr) ?: return null
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
    return if (isEnglish) {
      when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "Monday"
        Calendar.TUESDAY -> "Tuesday"
        Calendar.WEDNESDAY -> "Wednesday"
        Calendar.THURSDAY -> "Thursday"
        Calendar.FRIDAY -> "Friday"
        Calendar.SATURDAY -> "Saturday"
        else -> "Sunday"
      }
    } else {
      when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "Thứ Hai"
        Calendar.TUESDAY -> "Thứ Ba"
        Calendar.WEDNESDAY -> "Thứ Tư"
        Calendar.THURSDAY -> "Thứ Năm"
        Calendar.FRIDAY -> "Thứ Sáu"
        Calendar.SATURDAY -> "Thứ Bảy"
        else -> "Chủ Nhật"
      }
    }
  }

  /** Vietnamese day-of-week name for a "dd/MM/yyyy"-ish date string, or null if unparseable. */
  fun getVietnameseDayOfWeek(dateStr: String): String? = getDayOfWeek(dateStr, isEnglish = false)

  /** Short day-of-week symbol (T2 - CN or Mon - Sun). */
  fun getShortDayOfWeek(dateStr: String, isEnglish: Boolean = false): String {
    val millis = parseDateToUtcMillis(dateStr) ?: return ""
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
    return if (isEnglish) {
      when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "Mon"
        Calendar.TUESDAY -> "Tue"
        Calendar.WEDNESDAY -> "Wed"
        Calendar.THURSDAY -> "Thu"
        Calendar.FRIDAY -> "Fri"
        Calendar.SATURDAY -> "Sat"
        else -> "Sun"
      }
    } else {
      when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "T2"
        Calendar.TUESDAY -> "T3"
        Calendar.WEDNESDAY -> "T4"
        Calendar.THURSDAY -> "T5"
        Calendar.FRIDAY -> "T6"
        Calendar.SATURDAY -> "T7"
        else -> "CN"
      }
    }
  }

  /** Short Vietnamese day-of-week symbol (T2 - CN). */
  fun getShortVietnameseDayOfWeek(dateStr: String): String = getShortDayOfWeek(dateStr, isEnglish = false)

  /**
   * Generates a friendly relative time description (e.g. "Thứ Ba, 14/02/2023 • Cách đây 1000 ngày")
   */
  fun getFriendlyDateDescription(dateStr: String, isEnglish: Boolean = false): String {
    val millis = parseDateToUtcMillis(dateStr) ?: return ""
    val nowUtc = System.currentTimeMillis()
    val diffDays = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(kotlin.math.abs(nowUtc - millis))
    val isPast = millis <= nowUtc
    return if (isEnglish) {
      if (isPast) "$diffDays days in love" else "$diffDays days remaining"
    } else {
      if (isPast) "Đã qua $diffDays ngày yêu" else "Còn $diffDays ngày nữa"
    }
  }
}

/**
 * Quick date preset generators for relationship start dates and upcoming anniversaries.
 */
object DatePickerPresets {

  /**
   * Common quick presets for relationship start date.
   */
  fun relationshipStartDatePresets(isEnglish: Boolean = false): List<Pair<String, Long>> {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    val today = cal.timeInMillis

    cal.add(Calendar.MONTH, -1)
    val oneMonthAgo = cal.timeInMillis

    cal.timeInMillis = today
    cal.add(Calendar.DAY_OF_YEAR, -100)
    val hundredDaysAgo = cal.timeInMillis

    cal.timeInMillis = today
    cal.add(Calendar.MONTH, -6)
    val sixMonthsAgo = cal.timeInMillis

    cal.timeInMillis = today
    cal.add(Calendar.YEAR, -1)
    val oneYearAgo = cal.timeInMillis

    cal.timeInMillis = today
    cal.add(Calendar.YEAR, -2)
    val twoYearsAgo = cal.timeInMillis

    cal.timeInMillis = today
    cal.add(Calendar.YEAR, -3)
    val threeYearsAgo = cal.timeInMillis

    return if (isEnglish) {
      listOf(
        "Today" to today,
        "1 month ago" to oneMonthAgo,
        "100 days ago" to hundredDaysAgo,
        "6 months ago" to sixMonthsAgo,
        "1 year ago" to oneYearAgo,
        "2 years ago" to twoYearsAgo,
        "3 years ago" to threeYearsAgo
      )
    } else {
      listOf(
        "Hôm nay" to today,
        "1 tháng trước" to oneMonthAgo,
        "100 ngày trước" to hundredDaysAgo,
        "6 tháng trước" to sixMonthsAgo,
        "1 năm trước" to oneYearAgo,
        "2 năm trước" to twoYearsAgo,
        "3 năm trước" to threeYearsAgo
      )
    }
  }

  /**
   * Common quick presets for upcoming anniversary dates.
   */
  fun upcomingAnniversaryPresets(isEnglish: Boolean = false): List<Pair<String, Long>> {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    val today = cal.timeInMillis

    cal.add(Calendar.MONTH, 1)
    val oneMonthLater = cal.timeInMillis

    cal.timeInMillis = today
    cal.add(Calendar.DAY_OF_YEAR, 100)
    val hundredDaysLater = cal.timeInMillis

    cal.timeInMillis = today
    cal.add(Calendar.MONTH, 6)
    val sixMonthsLater = cal.timeInMillis

    cal.timeInMillis = today
    cal.add(Calendar.YEAR, 1)
    val oneYearLater = cal.timeInMillis

    return if (isEnglish) {
      listOf(
        "Today" to today,
        "+1 month" to oneMonthLater,
        "+100 days" to hundredDaysLater,
        "+6 months" to sixMonthsLater,
        "1 full year" to oneYearLater
      )
    } else {
      listOf(
        "Hôm nay" to today,
        "+1 tháng" to oneMonthLater,
        "+100 ngày" to hundredDaysLater,
        "+6 tháng" to sixMonthsLater,
        "Tròn 1 năm" to oneYearLater
      )
    }
  }

  /**
   * Common quick presets for birthday dates.
   */
  fun birthDatePresets(isEnglish: Boolean = false): List<Pair<String, Long>> {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val today = cal.timeInMillis

    cal.add(Calendar.YEAR, -18)
    val eighteenYearsAgo = cal.timeInMillis

    cal.timeInMillis = today
    cal.add(Calendar.YEAR, -20)
    val twentyYearsAgo = cal.timeInMillis

    cal.timeInMillis = today
    cal.add(Calendar.YEAR, -22)
    val twentyTwoYearsAgo = cal.timeInMillis

    cal.timeInMillis = today
    cal.add(Calendar.YEAR, -25)
    val twentyFiveYearsAgo = cal.timeInMillis

    return if (isEnglish) {
      listOf(
        "18 years old" to eighteenYearsAgo,
        "20 years old" to twentyYearsAgo,
        "22 years old" to twentyTwoYearsAgo,
        "25 years old" to twentyFiveYearsAgo
      )
    } else {
      listOf(
        "18 tuổi" to eighteenYearsAgo,
        "20 tuổi" to twentyYearsAgo,
        "22 tuổi" to twentyTwoYearsAgo,
        "25 tuổi" to twentyFiveYearsAgo
      )
    }
  }

  /**
   * Quick presets for memory dates (today, yesterday, 1 week ago, 1 month ago).
   */
  fun memoryDatePresets(isEnglish: Boolean = false): List<Pair<String, Long>> {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val today = cal.timeInMillis

    cal.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = cal.timeInMillis

    cal.timeInMillis = today
    cal.add(Calendar.DAY_OF_YEAR, -7)
    val weekAgo = cal.timeInMillis

    cal.timeInMillis = today
    cal.add(Calendar.MONTH, -1)
    val monthAgo = cal.timeInMillis

    return if (isEnglish) {
      listOf(
        "Today" to today,
        "Yesterday" to yesterday,
        "1 week ago" to weekAgo,
        "1 month ago" to monthAgo
      )
    } else {
      listOf(
        "Hôm nay" to today,
        "Hôm qua" to yesterday,
        "1 tuần trước" to weekAgo,
        "1 tháng trước" to monthAgo
      )
    }
  }
}

/** Computes the Monday-to-Sunday matrix of calendar days for a given year and month. */
private fun computeMonthMatrix(year: Int, month: Int): List<List<Calendar>> {
  val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
    set(Calendar.YEAR, year)
    set(Calendar.MONTH, month)
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    firstDayOfWeek = Calendar.MONDAY
  }

  val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
  val startDayOffset = (dayOfWeek - Calendar.MONDAY + 7) % 7

  val startCal = (cal.clone() as Calendar).apply {
    add(Calendar.DAY_OF_MONTH, -startDayOffset)
  }

  val matrix = mutableListOf<List<Calendar>>()
  val currentPointer = startCal.clone() as Calendar

  for (w in 0..5) {
    val week = mutableListOf<Calendar>()
    for (d in 0..6) {
      week.add(currentPointer.clone() as Calendar)
      currentPointer.add(Calendar.DAY_OF_MONTH, 1)
    }
    matrix.add(week)
    if (w >= 4 && currentPointer.get(Calendar.MONTH) != month) {
      break
    }
  }
  return matrix
}

/**
 * InLove DatePicker Dialog tailored with:
 * - Standard Vietnamese Day Names: "T2", "T3", "T4", "T5", "T6", "T7", "CN" (no more confusing "T T T T T T C")
 * - Smooth horizontal slide & fade month animations
 * - Animated day selection with romantic spring bounce
 * - 1-tap quick Year / Month selector grid (perfect for picking birth years)
 * - FlowRow wrapped presets to guarantee zero horizontal clipping and zero screen overflow
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InLoveDatePickerDialog(
  title: String? = null,
  initialDateStr: String? = null,
  initialDateMillis: Long? = null,
  quickPresets: List<Pair<String, Long>> = emptyList(),
  confirmButtonText: String? = null,
  dismissButtonText: String? = null,
  isEnglish: Boolean = false,
  onDateSelected: (utcMillis: Long, formattedDate: String) -> Unit,
  onDismiss: () -> Unit
) {
  val resolvedTitle = title ?: if (isEnglish) "Select date" else "Chọn ngày"
  val resolvedConfirm = confirmButtonText ?: if (isEnglish) "Confirm" else "Xác nhận"
  val resolvedDismiss = dismissButtonText ?: if (isEnglish) "Cancel" else "Hủy"

  val initialMillis = remember(initialDateStr, initialDateMillis) {
    when {
      initialDateMillis != null -> initialDateMillis
      !initialDateStr.isNullOrBlank() -> DatePickerUtils.parseDateToUtcMillis(initialDateStr) ?: System.currentTimeMillis()
      else -> System.currentTimeMillis()
    }
  }

  var selectedDateMillis by remember { mutableLongStateOf(initialMillis) }

  val initialCal = remember(initialMillis) {
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = initialMillis }
  }
  var displayYear by remember { mutableIntStateOf(initialCal.get(Calendar.YEAR)) }
  var displayMonth by remember { mutableIntStateOf(initialCal.get(Calendar.MONTH)) }
  var navDirection by remember { mutableIntStateOf(1) } // 1: next, -1: prev

  var isYearPickerOpen by remember { mutableStateOf(false) }

  val formattedCurrentDate = remember(selectedDateMillis) {
    DatePickerUtils.formatUtcMillisToDate(selectedDateMillis)
  }
  val dayOfWeekName = remember(formattedCurrentDate, isEnglish) {
    DatePickerUtils.getDayOfWeek(formattedCurrentDate, isEnglish) ?: ""
  }

  val todayCal = remember {
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
  }

  val monthNames = remember(isEnglish) {
    if (isEnglish) {
      listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
      )
    } else {
      listOf(
        "Tháng 1", "Tháng 2", "Tháng 3", "Tháng 4", "Tháng 5", "Tháng 6",
        "Tháng 7", "Tháng 8", "Tháng 9", "Tháng 10", "Tháng 11", "Tháng 12"
      )
    }
  }

  // Standard day-of-week abbreviations (T2 -> CN or Mon -> Sun)
  val weekdayNames = remember(isEnglish) {
    if (isEnglish) {
      listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    } else {
      listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Card(
      shape = RoundedCornerShape(26.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
      modifier = Modifier
        .widthIn(max = 360.dp)
        .fillMaxWidth(0.94f)
        .animateDialogEntrance()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 14.dp)
      ) {
        // 1. Header: Left = Icon + Title, Right = Formatted Selected Date + Day of Week
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.CalendarMonth,
              contentDescription = null,
              tint = Primary,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = resolvedTitle,
              fontSize = 14.5.sp,
              fontWeight = FontWeight.Bold,
              color = Primary
            )
          }

          AnimatedContent(
            targetState = "$dayOfWeekName, $formattedCurrentDate",
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
            label = "header_date_anim"
          ) { _ ->
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFFFFF0F5),
              border = BorderStroke(1.dp, Color(0xFFFFB6C1))
            ) {
              Text(
                text = formattedCurrentDate,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF880E4F),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Quick Preset Chips with spring interaction
        if (quickPresets.isNotEmpty()) {
          FlowRow(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
          ) {
            quickPresets.forEach { (label, presetMillis) ->
              val isSelected = DatePickerUtils.formatUtcMillisToDate(selectedDateMillis) ==
                DatePickerUtils.formatUtcMillisToDate(presetMillis)

              val chipScale by animateFloatAsState(
                targetValue = if (isSelected) 1.04f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "preset_chip_scale"
              )
              val chipBg by animateColorAsState(
                targetValue = if (isSelected) Primary else SurfaceContainerHigh,
                animationSpec = tween(150),
                label = "preset_chip_bg"
              )

              Surface(
                shape = RoundedCornerShape(12.dp),
                color = chipBg,
                border = if (isSelected) null else BorderStroke(0.5.dp, Color(0xFFE8D0D8)),
                modifier = Modifier
                  .graphicsLayer {
                    scaleX = chipScale
                    scaleY = chipScale
                  }
                  .clickable {
                    selectedDateMillis = presetMillis
                    val presetCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = presetMillis }
                    navDirection = if (presetCal.get(Calendar.YEAR) > displayYear || (presetCal.get(Calendar.YEAR) == displayYear && presetCal.get(Calendar.MONTH) > displayMonth)) 1 else -1
                    displayYear = presetCal.get(Calendar.YEAR)
                    displayMonth = presetCal.get(Calendar.MONTH)
                  }
                  .testTag("date_preset_$label")
              ) {
                Text(
                  text = label,
                  fontSize = 11.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  color = if (isSelected) Color.White else OnSurface,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
              }
            }
          }
        }

        // 3. Month & Year Navigation Row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Month/Year Title button with dropdown indicator
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isYearPickerOpen) Color(0xFFFFF0F5) else Color.Transparent,
            modifier = Modifier
              .clickable { isYearPickerOpen = !isYearPickerOpen }
              .testTag("btn_select_year_month")
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Text(
                text = if (isEnglish) "${monthNames[displayMonth]} $displayYear" else "${monthNames[displayMonth]} năm $displayYear",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF212121)
              )
              Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = if (isEnglish) "Select year" else "Chọn năm",
                tint = Primary,
                modifier = Modifier.size(20.dp)
              )
            }
          }

          // Prev / Next Month Buttons with smooth transition
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
              onClick = {
                navDirection = -1
                if (displayMonth == 0) {
                  displayMonth = 11
                  displayYear -= 1
                } else {
                  displayMonth -= 1
                }
              },
              modifier = Modifier.size(32.dp).testTag("btn_calendar_prev")
            ) {
              Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = if (isEnglish) "Previous month" else "Tháng trước",
                tint = Color(0xFF424242),
                modifier = Modifier.size(20.dp)
              )
            }

            IconButton(
              onClick = {
                navDirection = 1
                if (displayMonth == 11) {
                  displayMonth = 0
                  displayYear += 1
                } else {
                  displayMonth += 1
                }
              },
              modifier = Modifier.size(32.dp).testTag("btn_calendar_next")
            ) {
              Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = if (isEnglish) "Next month" else "Tháng sau",
                tint = Color(0xFF424242),
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 4. Calendar Body OR Year Picker Grid with smooth AnimatedContent
        AnimatedContent(
          targetState = isYearPickerOpen,
          transitionSpec = {
            fadeIn(tween(180)) togetherWith fadeOut(tween(150))
          },
          label = "year_picker_toggle_anim"
        ) { yearPickerActive ->
          if (yearPickerActive) {
            // --- YEAR & MONTH SELECTOR VIEW ---
            val currentSelectedYear = displayYear
            val yearsList = remember { (1950..2040).toList() }
            val gridState = rememberLazyGridState(
              initialFirstVisibleItemIndex = maxOf(0, yearsList.indexOf(currentSelectedYear) - 4)
            )

            Column(
              modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
            ) {
              Text(
                text = if (isEnglish) "Select year:" else "Chọn năm:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Primary,
                modifier = Modifier.padding(bottom = 6.dp)
              )
              LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                state = gridState,
                modifier = Modifier
                  .weight(1f)
                  .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                items(yearsList) { y ->
                  val isThisYear = y == displayYear
                  Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isThisYear) Primary else Color(0xFFF5F5F7),
                    modifier = Modifier
                      .clickable {
                        displayYear = y
                        isYearPickerOpen = false
                      }
                      .testTag("year_item_$y")
                  ) {
                    Text(
                      text = "$y",
                      fontSize = 12.5.sp,
                      fontWeight = if (isThisYear) FontWeight.Bold else FontWeight.Medium,
                      color = if (isThisYear) Color.White else Color(0xFF333333),
                      textAlign = TextAlign.Center,
                      modifier = Modifier.padding(vertical = 8.dp)
                    )
                  }
                }
              }
            }
          } else {
            // --- STANDARD CALENDAR DAY VIEW ---
            Column(modifier = Modifier.fillMaxWidth()) {
              // 4.1. Standard Vietnamese Weekday Names: T2, T3, T4, T5, T6, T7, CN
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround
              ) {
                weekdayNames.forEachIndexed { index, name ->
                  val color = when (index) {
                    5 -> Color(0xFFE91E63) // Thứ 7 (Pink/Primary)
                    6 -> Color(0xFFC2185B) // Chủ Nhật (Deep Pink)
                    else -> Color(0xFF616161) // Thứ 2 - Thứ 6
                  }
                  Text(
                    text = name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(36.dp)
                  )
                }
              }

              // 4.2. Animated Month Day Grid with slide/fade animation
              AnimatedContent(
                targetState = Pair(displayYear, displayMonth),
                transitionSpec = {
                  if (navDirection > 0) {
                    (slideInHorizontally { it / 3 } + fadeIn(tween(180))) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut(tween(150)))
                  } else {
                    (slideInHorizontally { -it / 3 } + fadeIn(tween(180))) togetherWith (slideOutHorizontally { it / 3 } + fadeOut(tween(150)))
                  }
                },
                label = "month_matrix_anim"
              ) { (y, m) ->
                val monthMatrix = remember(y, m) { computeMonthMatrix(y, m) }
                val selectedCal = remember(selectedDateMillis) {
                  Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = selectedDateMillis }
                }

                Column(
                  modifier = Modifier.fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  monthMatrix.forEach { weekList ->
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceAround,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      weekList.forEach { cellCal ->
                        val dayNum = cellCal.get(Calendar.DAY_OF_MONTH)
                        val isCurrentMonth = cellCal.get(Calendar.MONTH) == m
                        val isToday = cellCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                          cellCal.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH) &&
                          cellCal.get(Calendar.DAY_OF_MONTH) == todayCal.get(Calendar.DAY_OF_MONTH)
                        val isSelected = cellCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                          cellCal.get(Calendar.MONTH) == selectedCal.get(Calendar.MONTH) &&
                          cellCal.get(Calendar.DAY_OF_MONTH) == selectedCal.get(Calendar.DAY_OF_MONTH)

                        val cellScale by animateFloatAsState(
                          targetValue = if (isSelected) 1.12f else 1f,
                          animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                          ),
                          label = "day_cell_scale"
                        )
                        val cellBg by animateColorAsState(
                          targetValue = when {
                            isSelected -> Primary
                            isToday && isCurrentMonth -> Color(0xFFFFEBEE)
                            else -> Color.Transparent
                          },
                          animationSpec = tween(150),
                          label = "day_cell_bg"
                        )

                        val cellModifier = Modifier
                          .size(36.dp)
                          .graphicsLayer {
                            scaleX = cellScale
                            scaleY = cellScale
                          }
                          .clip(CircleShape)
                          .background(cellBg)
                          .let { mod ->
                            if (isToday && !isSelected) mod.border(1.dp, Primary, CircleShape) else mod
                          }
                          .clickable {
                            val utcDay = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                              set(Calendar.YEAR, cellCal.get(Calendar.YEAR))
                              set(Calendar.MONTH, cellCal.get(Calendar.MONTH))
                              set(Calendar.DAY_OF_MONTH, cellCal.get(Calendar.DAY_OF_MONTH))
                              set(Calendar.HOUR_OF_DAY, 0)
                              set(Calendar.MINUTE, 0)
                              set(Calendar.SECOND, 0)
                              set(Calendar.MILLISECOND, 0)
                            }
                            selectedDateMillis = utcDay.timeInMillis
                            if (!isCurrentMonth) {
                              displayYear = cellCal.get(Calendar.YEAR)
                              displayMonth = cellCal.get(Calendar.MONTH)
                            }
                          }
                          .testTag("cell_day_${cellCal.get(Calendar.DAY_OF_MONTH)}")

                        Box(
                          contentAlignment = Alignment.Center,
                          modifier = cellModifier
                        ) {
                          Text(
                            text = "$dayNum",
                            fontSize = 13.sp,
                            fontWeight = if (isSelected || (isToday && isCurrentMonth)) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                              isSelected -> Color.White
                              !isCurrentMonth -> Color(0xFFBDBDBD)
                              isToday -> Primary
                              else -> Color(0xFF212121)
                            }
                          )
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Action Buttons (Hủy & Xác nhận)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .height(42.dp)
              .testTag("date_picker_dismiss_btn")
          ) {
            Text(resolvedDismiss, color = OnSurfaceVariant, fontSize = 13.sp)
          }

          Button(
            onClick = {
              val formatted = DatePickerUtils.formatUtcMillisToDate(selectedDateMillis)
              onDateSelected(selectedDateMillis, formatted)
              onDismiss()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1.3f)
              .height(42.dp)
              .testTag("date_picker_confirm_btn")
          ) {
            Icon(
              imageVector = Icons.Filled.Favorite,
              contentDescription = null,
              modifier = Modifier.size(15.dp),
              tint = Color.White
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(resolvedConfirm, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
          }
        }
      }
    }
  }
}

/**
 * An integrated Material 3 DatePicker Input Field component.
 * Allows users to type directly or click the calendar icon to open the Material 3 DatePicker.
 */
@Composable
fun InLoveDatePickerField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  placeholder: String = "dd/MM/yyyy",
  modifier: Modifier = Modifier,
  dialogTitle: String? = null,
  quickPresets: List<Pair<String, Long>> = emptyList(),
  helperText: String? = null,
  singleLine: Boolean = true,
  isEnglish: Boolean = false,
  testTag: String = "date_picker_field"
) {
  var showDatePicker by remember { mutableStateOf(false) }

  Column(modifier = modifier) {
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      label = { Text(label) },
      placeholder = { Text(placeholder) },
      singleLine = singleLine,
      trailingIcon = {
        IconButton(
          onClick = { showDatePicker = true },
          modifier = Modifier.testTag("${testTag}_calendar_btn")
        ) {
          Icon(
            imageVector = Icons.Filled.CalendarMonth,
            contentDescription = if (isEnglish) "Open calendar" else "Mở lịch chọn ngày",
            tint = Primary
          )
        }
      },
      colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color(0xFF1E1E24),
        unfocusedTextColor = Color(0xFF1E1E24),
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        focusedBorderColor = Primary,
        unfocusedBorderColor = Color(0xFFC7C7CC),
        focusedLabelColor = Primary,
        unfocusedLabelColor = Color(0xFF424242),
        focusedPlaceholderColor = Color(0xFF757575),
        unfocusedPlaceholderColor = Color(0xFF9E9E9E),
        cursorColor = Primary
      ),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag(testTag)
    )

    if (!helperText.isNullOrBlank()) {
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = helperText,
        fontSize = 11.sp,
        color = OnSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp)
      )
    }
  }

  if (showDatePicker) {
    InLoveDatePickerDialog(
      title = dialogTitle ?: if (isEnglish) "Select date" else "Chọn ngày",
      initialDateStr = value,
      quickPresets = quickPresets,
      isEnglish = isEnglish,
      onDateSelected = { _, formattedDate ->
        onValueChange(formattedDate)
      },
      onDismiss = { showDatePicker = false }
    )
  }
}
