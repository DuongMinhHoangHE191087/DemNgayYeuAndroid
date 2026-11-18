package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EditCalendar
import kotlin.time.Duration.Companion.minutes
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.InLoveFonts
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Primary
import com.example.ui.theme.TextDarkPlum
import com.example.ui.util.AppLanguage
import com.example.ui.util.ProfileUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

/**
 * Data representation of calculated love time elapsed between the anniversary
 * start date and the current moment.
 */
data class LoveTimeBreakdown(
  val totalDays: Long,
  val years: Int,
  val months: Int,
  val days: Int,
  val totalWeeks: Long,
  val remainingDaysInWeek: Long,
  val totalHours: Long,
  val totalMinutes: Long,
  val totalSeconds: Long,
  val nextMilestoneDays: Int,
  val daysUntilMilestone: Int,
  val milestoneProgress: Float
)

/**
 * Calculates detailed calendar and time breakdown from an anniversary start date
 * string to the current timestamp.
 */
fun calculateDetailedLoveTime(
  startDateStr: String,
  nowMillis: Long = System.currentTimeMillis()
): LoveTimeBreakdown {
  val formats = listOf("dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy", "yyyy-MM-dd")
  var startCal: Calendar? = null
  for (pattern in formats) {
    try {
      val sdf = SimpleDateFormat(pattern, Locale.ROOT)
      sdf.isLenient = false
      val date = sdf.parse(startDateStr.trim())
      if (date != null) {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        startCal = cal
        break
      }
    } catch (_: Exception) {}
  }

  val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
  if (startCal == null || startCal.after(nowCal)) {
    return LoveTimeBreakdown(
      totalDays = 0,
      years = 0,
      months = 0,
      days = 0,
      totalWeeks = 0,
      remainingDaysInWeek = 0,
      totalHours = 0,
      totalMinutes = 0,
      totalSeconds = 0,
      nextMilestoneDays = 100,
      daysUntilMilestone = 100,
      milestoneProgress = 0f
    )
  }

  val diffMillis = nowCal.timeInMillis - startCal.timeInMillis
  val totalDays = TimeUnit.MILLISECONDS.toDays(diffMillis) + 1
  val totalWeeks = totalDays / 7
  val remainingDaysInWeek = totalDays % 7
  val totalHours = totalDays * 24
  val totalMinutes = totalHours * 60
  val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(diffMillis)

  var years = nowCal.get(Calendar.YEAR) - startCal.get(Calendar.YEAR)
  var months = nowCal.get(Calendar.MONTH) - startCal.get(Calendar.MONTH)
  var days = nowCal.get(Calendar.DAY_OF_MONTH) - startCal.get(Calendar.DAY_OF_MONTH)

  if (days < 0) {
    months -= 1
    val prevMonthCal = nowCal.clone() as Calendar
    prevMonthCal.add(Calendar.MONTH, -1)
    days += prevMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
  }

  if (months < 0) {
    years -= 1
    months += 12
  }

  val standardMilestones = listOf(100, 200, 365, 500, 730, 1000, 1349, 1500, 1825, 2000, 2500, 3000, 3650, 5000)
  val nextMilestone = standardMilestones.firstOrNull { it > totalDays } ?: ((totalDays / 500 + 1) * 500).toInt()
  val daysUntilMilestone = (nextMilestone - totalDays).toInt().coerceAtLeast(0)
  val prevMilestone = standardMilestones.lastOrNull { it <= totalDays } ?: 0
  val milestoneProgress = if (nextMilestone > prevMilestone) {
    ((totalDays - prevMilestone).toFloat() / (nextMilestone - prevMilestone).toFloat()).coerceIn(0f, 1f)
  } else 1f

  return LoveTimeBreakdown(
    totalDays = totalDays,
    years = years.coerceAtLeast(0),
    months = months.coerceAtLeast(0),
    days = days.coerceAtLeast(0),
    totalWeeks = totalWeeks,
    remainingDaysInWeek = remainingDaysInWeek,
    totalHours = totalHours,
    totalMinutes = totalMinutes,
    totalSeconds = totalSeconds,
    nextMilestoneDays = nextMilestone,
    daysUntilMilestone = daysUntilMilestone,
    milestoneProgress = milestoneProgress
  )
}

/**
 * Romantic Love Days Calculator Widget for the Main Dashboard.
 * Calculates and displays the total number of days a couple has been together,
 * offering detailed time breakdowns (years, months, weeks, hours, minutes),
 * anniversary date picker, sync with main circular counter, and milestone progression.
 */
@Composable
fun LoveDaysCalculatorWidget(
  anniversaryDate: String,
  currentLoveDays: Int,
  boyName: String,
  girlName: String,
  language: AppLanguage,
  onRecalculateAndSync: (Int) -> Unit,
  onUpdateAnniversaryDate: (String) -> Unit,
  onTriggerHearts: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var showManualInputDialog by remember { mutableStateOf(false) }
  var showM3DatePicker by remember { mutableStateOf(false) }

  // Re-read the clock every minute so the counters roll over right after midnight
  var currentTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(1.minutes)
      currentTimestamp = System.currentTimeMillis()
    }
  }

  val breakdown = remember(anniversaryDate, currentTimestamp) {
    calculateDetailedLoveTime(anniversaryDate, currentTimestamp)
  }

  val isVietnamese = language == AppLanguage.VI

  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(
      containerColor = Color.White.copy(alpha = 0.98f)
    ),
    border = BorderStroke(1.2.dp, Color(0xFFFFC6DB)),
    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("love_days_calculator_widget")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (isVietnamese) "Bộ Đếm Ngày Yêu" else "Love Days",
          fontFamily = InLoveFonts.DisplaySerif,
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = TextDarkPlum
        )
      }

      // 2. Anniversary Start Date Banner
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFFF7FA),
        border = BorderStroke(1.dp, Color(0xFFFFD1DF)),
        modifier = Modifier
          .fillMaxWidth()
          .clickable { showM3DatePicker = true }
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.Favorite,
              contentDescription = null,
              tint = Color(0xFFFF4081),
              modifier = Modifier.size(15.dp)
            )
            Text(
              text = anniversaryDate.ifBlank { if (isVietnamese) "Chọn ngày bắt đầu yêu" else "Pick your start date" },
              fontSize = 13.sp,
              fontWeight = FontWeight.ExtraBold,
              color = Color(0xFFC2185B)
            )
          }

          Icon(
            imageVector = Icons.Filled.EditCalendar,
            contentDescription = if (isVietnamese) "Đổi ngày" else "Change date",
            tint = Color(0xFFFF4081),
            modifier = Modifier.size(18.dp)
          )
        }
      }

      // 3. Hero Metric: Total Number of Days Together
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFFFF0F5),
        border = BorderStroke(1.2.dp, Color(0xFFFFC6DB)),
        modifier = Modifier
          .fillMaxWidth()
          .clickable {
            onTriggerHearts()
          }
          .testTag("calculated_total_days_card")
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 12.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          // Prominent Big Number
          Text(
            text = "%,d".format(breakdown.totalDays),
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF1F0416),
            lineHeight = 38.sp
          )

          Text(
            text = if (isVietnamese) {
              "${breakdown.years} năm ${breakdown.months} tháng ${breakdown.days} ngày"
            } else {
              "${breakdown.years}y ${breakdown.months}m ${breakdown.days}d"
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF4A1934),
            textAlign = TextAlign.Center
          )
        }
      }

      // 4. Detailed Breakdown Grid (4 Units)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // Unit 1: Years - Months - Days
        TimeUnitItemCard(
          icon = Icons.Filled.CalendarMonth,
          title = if (isVietnamese) "Năm & Tháng" else "Years & Mos",
          value = if (isVietnamese) "${breakdown.years}N ${breakdown.months}T" else "${breakdown.years}y ${breakdown.months}m",
          sub = "${breakdown.days} ${if (isVietnamese) "ngày" else "days"}",
          tint = Color(0xFFE91E63),
          modifier = Modifier.weight(1f)
        )

        // Unit 2: Weeks
        TimeUnitItemCard(
          icon = Icons.Filled.ViewWeek,
          title = if (isVietnamese) "Số Tuần" else "Weeks",
          value = "${breakdown.totalWeeks}",
          sub = "+${breakdown.remainingDaysInWeek} ${if (isVietnamese) "ngày" else "days"}",
          tint = Color(0xFF9C27B0),
          modifier = Modifier.weight(1f)
        )

        // Unit 3: Hours
        TimeUnitItemCard(
          icon = Icons.Filled.Schedule,
          title = if (isVietnamese) "Số Giờ" else "Hours",
          value = "%,d".format(breakdown.totalHours),
          sub = if (isVietnamese) "giờ trọn vẹn" else "full hours",
          tint = Color(0xFF00ACC1),
          modifier = Modifier.weight(1f)
        )

        // Unit 4: Minutes
        TimeUnitItemCard(
          icon = Icons.Filled.Timer,
          title = if (isVietnamese) "Số Phút" else "Minutes",
          value = "%,d".format(breakdown.totalMinutes),
          sub = if (isVietnamese) "phút yêu" else "love mins",
          tint = Color(0xFFFF6F00),
          modifier = Modifier.weight(1f)
        )
      }

      // 5. Next Milestone Progression inside widget
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFFF7FA),
        border = BorderStroke(1.dp, Color(0xFFFFD1DF)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Celebration,
                contentDescription = null,
                tint = Color(0xFFFF4081),
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = if (isVietnamese) {
                  "Mốc: %,d ngày".format(breakdown.nextMilestoneDays)
                } else {
                  "Next: %,d days".format(breakdown.nextMilestoneDays)
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF26071B)
              )
            }

            Surface(
              shape = RoundedCornerShape(50.dp),
              color = Color(0xFFFF4081)
            ) {
              Text(
                text = if (isVietnamese) {
                  "Còn ${breakdown.daysUntilMilestone} ngày"
                } else {
                  "${breakdown.daysUntilMilestone}d left"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
              )
            }
          }

          LinearProgressIndicator(
            progress = { breakdown.milestoneProgress },
            modifier = Modifier
              .fillMaxWidth()
              .height(6.dp)
              .clip(RoundedCornerShape(50.dp)),
            color = Color(0xFFFF4081),
            trackColor = Color(0xFFFFD1DF)
          )
        }
      }

      // 6. Action Buttons Row: "Đồng bộ lên Vòng tròn chính" & "Nhập ngày tùy chỉnh"
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // Sync button to apply calculated days to the circular counter on home screen
        PrimaryGradientButton(
          text = if (isVietnamese) "Đồng bộ" else "Sync",
          onClick = {
            onRecalculateAndSync(breakdown.totalDays.toInt())
          },
          icon = Icons.Filled.Sync,
          modifier = Modifier
            .weight(1f)
            .heightIn(min = MinTouchBox),
          testTag = "btn_sync_days_to_counter"
        )

        // Quick button to open manual date input dialog
        SecondaryButton(
          text = if (isVietnamese) "Nhập ngày" else "Edit Date",
          onClick = { showManualInputDialog = true },
          icon = Icons.Filled.DateRange,
          modifier = Modifier
            .weight(1f)
            .heightIn(min = MinTouchBox),
          testTag = "btn_manual_anniversary_input"
        )
      }
    }
  }

  // Material 3 DatePicker Dialog for selecting relationship start date
  if (showM3DatePicker) {
    InLoveDatePickerDialog(
      title = if (isVietnamese) "Chọn Ngày Bắt Đầu Yêu" else "Select Relationship Start Date",
      initialDateStr = anniversaryDate,
      quickPresets = DatePickerPresets.relationshipStartDatePresets(),
      onDateSelected = { _, formattedDate ->
        onUpdateAnniversaryDate(formattedDate)
      },
      onDismiss = { showM3DatePicker = false }
    )
  }

  // Optional Manual Input Dialog for users wanting to type date directly
  if (showManualInputDialog) {
    ManualDateInputDialog(
      currentDate = anniversaryDate,
      isVietnamese = isVietnamese,
      onDismiss = { showManualInputDialog = false },
      onConfirm = { newDate ->
        onUpdateAnniversaryDate(newDate)
        showManualInputDialog = false
      }
    )
  }
}

@Composable
private fun TimeUnitItemCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  value: String,
  sub: String,
  tint: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = Color(0xFFFFF7FA),
    border = BorderStroke(1.dp, Color(0xFFFFD1DF)),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Box(
        modifier = Modifier
          .size(24.dp)
          .clip(CircleShape)
          .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = tint,
          modifier = Modifier.size(14.dp)
        )
      }

      Text(
        text = title,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFF6B2B50),
        textAlign = TextAlign.Center,
        maxLines = 1
      )

      Text(
        text = value,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color(0xFF26071B),
        textAlign = TextAlign.Center,
        maxLines = 1
      )

      Text(
        text = sub,
        fontSize = 9.sp,
        color = Color(0xFF8E24AA),
        textAlign = TextAlign.Center,
        maxLines = 1
      )
    }
  }
}

@Composable
private fun ManualDateInputDialog(
  currentDate: String,
  isVietnamese: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit
) {
  AppDialog(onDismissRequest = onDismiss) { ManualDateInputContent(currentDate, isVietnamese, onDismiss, onConfirm) }
}

// Split from the Dialog window so tests can compose it directly: Robolectric never goes idle with an OutlinedTextField inside a Dialog window.
@Composable
internal fun ManualDateInputContent(
  currentDate: String,
  isVietnamese: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit
) {
  var dateText by remember { mutableStateOf(currentDate) }
  var isError by remember { mutableStateOf(false) }

  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.2.dp, Color(0xFFFFC6DB)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.Favorite,
          contentDescription = null,
          tint = Color(0xFFFF4081)
        )
        Text(
          text = if (isVietnamese) "Chọn Ngày Bắt Đầu Yêu" else "Set Anniversary Date",
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp,
          color = Color(0xFF26071B)
        )
      }

      Text(
        text = if (isVietnamese) {
          "Nhập ngày hai bạn chính thức yêu nhau (định dạng ngày/tháng/năm, ví dụ: 14/02/2023)."
        } else {
          "Enter your relationship start date (format dd/MM/yyyy, e.g. 14/02/2023)."
        },
        fontSize = 12.sp,
        color = Color(0xFF6B2B50)
      )

      InLoveDatePickerField(
        value = dateText,
        onValueChange = {
          dateText = it
          isError = false
        },
        label = if (isVietnamese) "Ngày bắt đầu (dd/MM/yyyy) *" else "Start Date (dd/MM/yyyy) *",
        placeholder = "dd/MM/yyyy",
        dialogTitle = if (isVietnamese) "Chọn Ngày Bắt Đầu Yêu" else "Select Start Date",
        quickPresets = DatePickerPresets.relationshipStartDatePresets(),
        testTag = "input_anniversary_text"
      )
      if (isError) {
        Text(
          text = if (isVietnamese) "Nhập dd/MM/yyyy, không ở tương lai" else "Use dd/MM/yyyy, not in the future",
          fontSize = 11.sp,
          color = Color(0xFFD32F2F),
          modifier = Modifier.testTag("text_anniversary_error")
        )
      }

      // Presets
      Text(
        text = if (isVietnamese) "Hoặc chọn nhanh:" else "Or quick select:",
        fontSize = 11.sp,
        color = Color(0xFF8E24AA),
        fontWeight = FontWeight.SemiBold
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        val presets = listOf("14/02/2023", "20/10/2022", "08/03/2024")
        presets.forEach { preset ->
          Surface(
            shape = RoundedCornerShape(50.dp),
            color = Color(0xFFFFF0F5),
            border = BorderStroke(1.dp, Color(0xFFFFC6DB)),
            modifier = Modifier
              .clickable {
                dateText = preset
                isError = false
              }
              .padding(vertical = 2.dp)
          ) {
            Text(
              text = preset,
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              color = Color(0xFFC2185B),
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
      ) {
        SecondaryButton(
          text = if (isVietnamese) "Hủy" else "Cancel",
          onClick = onDismiss,
          modifier = Modifier.heightIn(min = MinTouchBox)
        )
        Spacer(modifier = Modifier.width(8.dp))
        PrimaryGradientButton(
          text = if (isVietnamese) "Lưu & Tính Ngày" else "Save & Calculate",
          onClick = {
            val normalized = ProfileUtils.normalizeAnniversaryInput(dateText)
            if (normalized == null) isError = true else onConfirm(normalized)
          },
          modifier = Modifier.heightIn(min = MinTouchBox)
        )
      }
    }
  }
}
