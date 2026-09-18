package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

  /**
   * Generates a friendly relative time description (e.g. "Thứ Ba, 14/02/2023 • Cách đây 1000 ngày")
   */
  fun getFriendlyDateDescription(dateStr: String): String {
    val millis = parseDateToUtcMillis(dateStr) ?: return ""
    val nowUtc = System.currentTimeMillis()
    val diffDays = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(kotlin.math.abs(nowUtc - millis))
    val isPast = millis <= nowUtc
    return if (isPast) {
      "Đã qua $diffDays ngày yêu"
    } else {
      "Còn $diffDays ngày nữa"
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
  fun relationshipStartDatePresets(): List<Pair<String, Long>> {
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

    return listOf(
      "Hôm nay" to today,
      "1 tháng trước" to oneMonthAgo,
      "100 ngày trước" to hundredDaysAgo,
      "6 tháng trước" to sixMonthsAgo,
      "1 năm trước" to oneYearAgo,
      "2 năm trước" to twoYearsAgo,
      "3 năm trước" to threeYearsAgo
    )
  }

  /**
   * Common quick presets for upcoming anniversary dates.
   */
  fun upcomingAnniversaryPresets(): List<Pair<String, Long>> {
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

    return listOf(
      "Hôm nay" to today,
      "+1 tháng" to oneMonthLater,
      "+100 ngày" to hundredDaysLater,
      "+6 tháng" to sixMonthsLater,
      "Tròn 1 năm" to oneYearLater
    )
  }

  /**
   * Common quick presets for birthday dates.
   */
  fun birthDatePresets(): List<Pair<String, Long>> {
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

    return listOf(
      "18 tuổi" to eighteenYearsAgo,
      "20 tuổi" to twentyYearsAgo,
      "22 tuổi" to twentyTwoYearsAgo,
      "25 tuổi" to twentyFiveYearsAgo
    )
  }
}

/**
 * Material 3 DatePicker Dialog tailored for InLove application.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InLoveDatePickerDialog(
  title: String = "Chọn ngày",
  initialDateStr: String? = null,
  initialDateMillis: Long? = null,
  quickPresets: List<Pair<String, Long>> = emptyList(),
  confirmButtonText: String = "Xác nhận",
  dismissButtonText: String = "Hủy",
  onDateSelected: (utcMillis: Long, formattedDate: String) -> Unit,
  onDismiss: () -> Unit
) {
  val initialMillis = remember(initialDateStr, initialDateMillis) {
    when {
      initialDateMillis != null -> initialDateMillis
      !initialDateStr.isNullOrBlank() -> DatePickerUtils.parseDateToUtcMillis(initialDateStr) ?: System.currentTimeMillis()
      else -> System.currentTimeMillis()
    }
  }

  val datePickerState = rememberDatePickerState(
    initialSelectedDateMillis = initialMillis
  )

  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      Button(
        onClick = {
          val selected = datePickerState.selectedDateMillis ?: initialMillis
          val formatted = DatePickerUtils.formatUtcMillisToDate(selected)
          onDateSelected(selected, formatted)
          onDismiss()
        },
        colors = ButtonDefaults.buttonColors(containerColor = Primary),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.testTag("date_picker_confirm_btn")
      ) {
        Icon(
          imageVector = Icons.Filled.Favorite,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
          tint = Color.White
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(confirmButtonText, fontWeight = FontWeight.Bold, color = Color.White)
      }
    },
    dismissButton = {
      OutlinedButton(
        onClick = onDismiss,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.testTag("date_picker_dismiss_btn")
      ) {
        Text(dismissButtonText, color = OnSurfaceVariant)
      }
    },
    colors = DatePickerDefaults.colors(
      containerColor = Color.White
    )
  ) {
    Column {
      // Optional Quick Preset Chips Header
      if (quickPresets.isNotEmpty()) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
          Text(
            text = "Gợi ý chọn nhanh:",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Primary
          )
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            quickPresets.forEach { (label, presetMillis) ->
              val isSelected = datePickerState.selectedDateMillis?.let {
                DatePickerUtils.formatUtcMillisToDate(it) == DatePickerUtils.formatUtcMillisToDate(presetMillis)
              } ?: false

              Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) Primary else SurfaceContainerHigh,
                modifier = Modifier
                  .clickable {
                    datePickerState.selectedDateMillis = presetMillis
                  }
                  .testTag("date_preset_$label")
              ) {
                Text(
                  text = label,
                  fontSize = 11.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  color = if (isSelected) Color.White else OnSurface,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
              }
            }
          }
        }
      }

      DatePicker(
        state = datePickerState,
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.CalendarMonth,
              contentDescription = null,
              tint = Primary,
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = title,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = Primary
            )
          }
        },
        colors = DatePickerDefaults.colors(
          containerColor = Color.White,
          titleContentColor = Primary,
          headlineContentColor = Primary,
          weekdayContentColor = Color(0xFF880E4F),
          subheadContentColor = Color(0xFF424242),
          yearContentColor = Color(0xFF212121),
          currentYearContentColor = Primary,
          selectedYearContentColor = Color.White,
          selectedYearContainerColor = Primary,
          dayContentColor = Color(0xFF212121),
          selectedDayContainerColor = Primary,
          selectedDayContentColor = Color.White,
          todayContentColor = Primary,
          todayDateBorderColor = Primary
        )
      )
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
  dialogTitle: String = "Chọn ngày",
  quickPresets: List<Pair<String, Long>> = emptyList(),
  helperText: String? = null,
  singleLine: Boolean = true,
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
            contentDescription = "Mở lịch chọn ngày",
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
      title = dialogTitle,
      initialDateStr = value,
      quickPresets = quickPresets,
      onDateSelected = { _, formattedDate ->
        onValueChange(formattedDate)
      },
      onDismiss = { showDatePicker = false }
    )
  }
}
