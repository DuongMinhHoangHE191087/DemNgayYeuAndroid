@file:Suppress("FunctionName")
package com.example.ui.screens

import com.example.ui.util.AppLanguage
import java.util.Calendar
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Stars
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.MilestoneEntity
import com.example.ui.components.DatePickerUtils
import com.example.ui.components.MilestoneBadgeDashboardCard
import com.example.ui.components.MilestoneTimelineCard
import com.example.ui.theme.OnPrimaryFixed
import com.example.ui.theme.OnPrimaryFixedVariant
import com.example.ui.theme.OnSurface
import com.example.ui.theme.OnSurfaceVariant
import com.example.ui.theme.OnTertiaryFixed
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.PrimaryFixed
import com.example.ui.theme.RoseGradientEnd
import com.example.ui.theme.RoseGradientMid
import com.example.ui.theme.RoseGradientStart
import com.example.ui.theme.Secondary
import com.example.ui.theme.SecondaryContainer
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerHighest
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.theme.Tertiary
import com.example.ui.theme.TertiaryFixed
import com.example.ui.viewmodel.InLoveViewModel

enum class CalendarViewMode {
  WEEK,
  MONTH
}

private fun matchesCalendarDay(dateText: String, day: Int, month: Int): Boolean {
  val trimmed = dateText.trim()
  if (trimmed.contains("/")) {
    val parts = trimmed.split("/")
    if (parts.size >= 2) {
      val d = parts[0].toIntOrNull()
      val m = parts[1].toIntOrNull()
      if (d == day && m == (month + 1)) return true
    }
  } else if (trimmed.contains("-")) {
    val parts = trimmed.split("-")
    if (parts.size >= 3) {
      val m = parts[1].toIntOrNull()
      val d = parts[2].toIntOrNull()
      if (d == day && m == (month + 1)) return true
    }
  }
  return false
}

private fun computeMonthMatrix(calendar: Calendar): List<List<Calendar>> {
  val cal = calendar.clone() as Calendar
  cal.set(Calendar.DAY_OF_MONTH, 1)
  cal.firstDayOfWeek = Calendar.MONDAY

  val startDayOffset = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
  val startCal = (cal.clone() as Calendar).apply {
    add(Calendar.DAY_OF_MONTH, -startDayOffset)
  }

  val matrix = mutableListOf<List<Calendar>>()
  var currentPointer = startCal.clone() as Calendar

  for (w in 0..5) {
    val week = mutableListOf<Calendar>()
    for (d in 0..6) {
      week.add(currentPointer.clone() as Calendar)
      currentPointer.add(Calendar.DAY_OF_MONTH, 1)
    }
    matrix.add(week)
    if (w >= 4 && currentPointer.get(Calendar.MONTH) != cal.get(Calendar.MONTH)) {
      break
    }
  }
  return matrix
}

@Composable
private fun CalendarDayCell(
  dayNum: Int,
  isToday: Boolean,
  isSelected: Boolean,
  isMilestoneDay: Boolean,
  isCurrentMonth: Boolean,
  isSunday: Boolean,
  onClick: () -> Unit
) {
  val alpha = if (isCurrentMonth) 1f else 0.35f
  Box(
    modifier = Modifier
      .width(38.dp)
      .height(48.dp)
      .clip(RoundedCornerShape(14.dp))
      .then(
        when {
          isMilestoneDay && isCurrentMonth -> Modifier
            .shadow(6.dp, RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(RoseGradientStart, RoseGradientMid)))
          isSelected && isCurrentMonth -> Modifier
            .background(PrimaryFixed.copy(alpha = 0.7f))
            .border(1.5.dp, Primary, RoundedCornerShape(14.dp))
          isToday && isCurrentMonth -> Modifier
            .background(SurfaceContainerLow)
            .border(1.dp, Primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
          else -> Modifier
        }
      )
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        text = String.format("%02d", dayNum),
        fontSize = 13.sp,
        fontWeight = if ((isMilestoneDay || isToday || isSelected) && isCurrentMonth) FontWeight.Bold else FontWeight.Medium,
        color = when {
          isMilestoneDay && isCurrentMonth -> Color.White
          isSunday && isCurrentMonth -> Secondary
          else -> OnSurface.copy(alpha = alpha)
        }
      )
      if (isMilestoneDay && isCurrentMonth) {
        Icon(
          imageVector = Icons.Filled.Favorite,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(10.dp)
        )
      } else if (isToday && isCurrentMonth) {
        Box(
          modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(Secondary)
        )
      } else {
        Spacer(modifier = Modifier.size(5.dp))
      }
    }
  }
}

@Composable
fun CalendarScreen(
  viewModel: InLoveViewModel,
  onNavigateToGifts: () -> Unit
) {
  val milestones by viewModel.milestones.collectAsState()
  val anniversaryDates by viewModel.anniversaryDates.collectAsState()
  val upcomingMilestones by viewModel.upcomingMilestones.collectAsState()
  val currentFilter by viewModel.calendarFilter.collectAsState()
  val loveBadges by viewModel.loveBadges.collectAsState()
  val loveDays by viewModel.loveDays.collectAsState()
  val appLanguage by viewModel.appLanguage.collectAsState()
  val isEnglish = appLanguage == AppLanguage.EN
  var activeViewMode by remember { mutableStateOf(CalendarViewMode.WEEK) }
  var calendarNavTick by remember { mutableIntStateOf(0) }
  var activeCalendar by remember {
    val c = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    mutableStateOf(c)
  }
  var selectedDateMillis by remember {
    val c = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    mutableLongStateOf(c.timeInMillis)
  }
  var milestoneToDelete by remember { mutableStateOf<MilestoneEntity?>(null) }
  var anniversaryToDelete by remember { mutableStateOf<AnniversaryDateEntity?>(null) }

  anniversaryToDelete?.let { ann ->
    DeleteConfirmationDialog(
      title = if (isEnglish) "Delete Anniversary?" else "Xóa ngày kỷ niệm?",
      message = if (isEnglish) "Are you sure you want to delete anniversary '${ann.title}' from the database?" else "Bạn có chắc chắn muốn xóa ngày kỷ niệm '${ann.title}' khỏi cơ sở dữ liệu không?",
      itemName = ann.title,
      onConfirm = {
        viewModel.deleteAnniversaryDate(ann.id)
        anniversaryToDelete = null
      },
      onDismiss = {
        anniversaryToDelete = null
      }
    )
  }

  milestoneToDelete?.let { milestone ->
    DeleteConfirmationDialog(
      title = if (isEnglish) "Delete Milestone?" else "Xóa ngày kỷ niệm?",
      message = if (isEnglish) "Are you sure you want to delete this milestone from the calendar? All reminders and saved data will be completely removed." else "Bạn có chắc chắn muốn xóa ngày kỷ niệm này khỏi lịch không? Lời nhắc và dữ liệu đã lưu sẽ bị xóa hoàn toàn khỏi thiết bị.",
      itemName = milestone.title,
      onConfirm = {
        viewModel.deleteMilestone(milestone.id)
        milestoneToDelete = null
      },
      onDismiss = {
        milestoneToDelete = null
      }
    )
  }

  val filteredMilestones = remember(milestones, currentFilter) {
    when (currentFilter) {
      "Sắp tới (3)", "Upcoming (3)" -> milestones.filter { !it.isPast }
      "Đã qua (1)", "Past (1)" -> milestones.filter { it.isPast }
      else -> milestones
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp),
      contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Month Bar & Navigation
      item {
        val displayYear = activeCalendar.get(Calendar.YEAR)
        val displayMonth = activeCalendar.get(Calendar.MONTH)
        val monthNamesEn = listOf(
          "January", "February", "March", "April", "May", "June",
          "July", "August", "September", "October", "November", "December"
        )
        val monthNamesVi = listOf(
          "Tháng 1", "Tháng 2", "Tháng 3", "Tháng 4", "Tháng 5", "Tháng 6",
          "Tháng 7", "Tháng 8", "Tháng 9", "Tháng 10", "Tháng 11", "Tháng 12"
        )
        val monthShortEn = listOf(
          "Jan", "Feb", "Mar", "Apr", "May", "Jun",
          "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
        val monthShortVi = listOf(
          "T1", "T2", "T3", "T4", "T5", "T6",
          "T7", "T8", "T9", "T10", "T11", "T12"
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.CalendarToday,
              contentDescription = null,
              tint = Primary,
              modifier = Modifier.size(24.dp)
            )
            Text(
              text = if (isEnglish) "${monthNamesEn[displayMonth]} $displayYear" else "${monthNamesVi[displayMonth]}, $displayYear",
              fontSize = 20.sp,
              fontWeight = FontWeight.Bold,
              color = OnSurface
            )
          }

          Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceContainer.copy(alpha = 0.8f),
            shadowElevation = 1.dp
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
              IconButton(
                onClick = {
                  if (activeViewMode == CalendarViewMode.MONTH) {
                    val nextCal = (activeCalendar.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                    activeCalendar = nextCal
                  } else {
                    val nextCal = (activeCalendar.clone() as Calendar).apply { add(Calendar.WEEK_OF_YEAR, -1) }
                    activeCalendar = nextCal
                  }
                  calendarNavTick++
                },
                modifier = Modifier.size(32.dp).testTag("btn_calendar_prev")
              ) {
                Icon(
                  imageVector = Icons.Filled.ChevronLeft,
                  contentDescription = if (isEnglish) "Previous" else "Trước",
                  tint = OnSurfaceVariant,
                  modifier = Modifier.size(18.dp)
                )
              }
              Text(
                text = if (isEnglish) monthShortEn[displayMonth] else monthShortVi[displayMonth],
                color = Primary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
              )
              IconButton(
                onClick = {
                  if (activeViewMode == CalendarViewMode.MONTH) {
                    val nextCal = (activeCalendar.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                    activeCalendar = nextCal
                  } else {
                    val nextCal = (activeCalendar.clone() as Calendar).apply { add(Calendar.WEEK_OF_YEAR, 1) }
                    activeCalendar = nextCal
                  }
                  calendarNavTick++
                },
                modifier = Modifier.size(32.dp).testTag("btn_calendar_next")
              ) {
                Icon(
                  imageVector = Icons.Filled.ChevronRight,
                  contentDescription = if (isEnglish) "Next" else "Sau",
                  tint = OnSurfaceVariant,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }
        }
      }

      // 2. Filter Pills Row
      item {
        val filterOptions = if (isEnglish) {
          listOf(
            Pair("All (4)", Icons.Filled.AutoAwesome),
            Pair("Badges (12)", Icons.Filled.EmojiEvents),
            Pair("Upcoming (3)", Icons.Filled.HourglassTop),
            Pair("Past (1)", Icons.Filled.HistoryEdu),
            Pair("Year 2026", Icons.Filled.Favorite)
          )
        } else {
          listOf(
            Pair("Tất cả (4)", Icons.Filled.AutoAwesome),
            Pair("Huy Hiệu (12)", Icons.Filled.EmojiEvents),
            Pair("Sắp tới (3)", Icons.Filled.HourglassTop),
            Pair("Đã qua (1)", Icons.Filled.HistoryEdu),
            Pair("Năm 2026", Icons.Filled.Favorite)
          )
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          filterOptions.forEach { (filterName, icon) ->
            val isSelected = currentFilter == filterName
            Surface(
              shape = RoundedCornerShape(24.dp),
              color = if (isSelected) Color.Transparent else SurfaceContainerLowest.copy(alpha = 0.85f),
              shadowElevation = if (isSelected) 4.dp else 1.dp,
              modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .then(
                  if (isSelected) {
                    Modifier.background(
                      Brush.horizontalGradient(
                        listOf(Primary, PrimaryContainer)
                      )
                    )
                  } else Modifier
                )
                .clickable { viewModel.setCalendarFilter(filterName) }
                .testTag("filter_pill_$filterName")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(
                  imageVector = icon,
                  contentDescription = null,
                  tint = if (isSelected) Color.White else OnSurfaceVariant,
                  modifier = Modifier.size(16.dp)
                )
                Text(
                  text = filterName,
                  fontSize = 13.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) Color.White else OnSurfaceVariant
                )
              }
            }
          }
        }
      }

      // 3. Dynamic Interactive Calendar (Week Mode & Month Mode with Prev/Next Navigation)
      item {
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
          elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
          modifier = Modifier.fillMaxWidth().testTag("interactive_calendar_card")
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            // 3.1 Mode Selector (Week vs Month) + Today Button
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              // Toggle: Week vs Month
              Surface(
                shape = RoundedCornerShape(50.dp),
                color = SurfaceContainerLow,
                border = BorderStroke(1.dp, Primary.copy(alpha = 0.15f)),
                modifier = Modifier.height(34.dp)
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(2.dp)
                ) {
                  // Week Mode Button
                  val isWeek = activeViewMode == CalendarViewMode.WEEK
                  Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = if (isWeek) Primary else Color.Transparent,
                    modifier = Modifier
                      .clip(RoundedCornerShape(50.dp))
                      .clickable { activeViewMode = CalendarViewMode.WEEK }
                      .testTag("btn_mode_week")
                  ) {
                    Text(
                      text = if (isEnglish) "Week" else "Tuần",
                      fontSize = 12.sp,
                      fontWeight = if (isWeek) FontWeight.Bold else FontWeight.Medium,
                      color = if (isWeek) Color.White else OnSurfaceVariant,
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                  }

                  // Month Mode Button
                  val isMonth = activeViewMode == CalendarViewMode.MONTH
                  Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = if (isMonth) Primary else Color.Transparent,
                    modifier = Modifier
                      .clip(RoundedCornerShape(50.dp))
                      .clickable { activeViewMode = CalendarViewMode.MONTH }
                      .testTag("btn_mode_month")
                  ) {
                    Text(
                      text = if (isEnglish) "Month" else "Tháng",
                      fontSize = 12.sp,
                      fontWeight = if (isMonth) FontWeight.Bold else FontWeight.Medium,
                      color = if (isMonth) Color.White else OnSurfaceVariant,
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                  }
                }
              }

              // Today Action Chip
              Surface(
                shape = RoundedCornerShape(50.dp),
                color = PrimaryFixed.copy(alpha = 0.4f),
                modifier = Modifier
                  .clip(RoundedCornerShape(50.dp))
                  .clickable {
                    val today = Calendar.getInstance().apply {
                      set(Calendar.HOUR_OF_DAY, 0)
                      set(Calendar.MINUTE, 0)
                      set(Calendar.SECOND, 0)
                      set(Calendar.MILLISECOND, 0)
                    }
                    activeCalendar = today
                    selectedDateMillis = today.timeInMillis
                    viewModel.showToast(if (isEnglish) "Jumped to Today" else "Đã về ngày hôm nay")
                  }
                  .testTag("btn_jump_today")
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Icon(
                    imageVector = Icons.Filled.CalendarToday,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(13.dp)
                  )
                  Text(
                    text = if (isEnglish) "Today" else "Hôm nay",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnPrimaryFixed
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Day Names Header
            val dayNames = if (isEnglish) listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun") else listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceAround
            ) {
              dayNames.forEachIndexed { i, name ->
                val textColor = when (i) {
                  4 -> Primary
                  6 -> Secondary
                  else -> OnSurfaceVariant.copy(alpha = 0.7f)
                }
                Text(
                  text = name,
                  fontSize = 11.sp,
                  fontWeight = if (i == 4 || i == 6) FontWeight.Bold else FontWeight.Medium,
                  color = textColor,
                  modifier = Modifier.width(36.dp),
                  textAlign = TextAlign.Center
                )
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3.2 Calendar Body: WEEK vs MONTH
            val todayCal = remember {
              Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
              }
            }
            val selectedCal = remember(selectedDateMillis) {
              Calendar.getInstance().apply {
                timeInMillis = selectedDateMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
              }
            }

            if (activeViewMode == CalendarViewMode.WEEK) {
              // --- WEEK VIEW ---
              val weekDays = remember(activeCalendar.timeInMillis, calendarNavTick) {
                val cal = activeCalendar.clone() as Calendar
                cal.firstDayOfWeek = Calendar.MONDAY
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                (0..6).map { offset ->
                  (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, offset) }
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
              ) {
                weekDays.forEach { dayCal ->
                  val dayNum = dayCal.get(Calendar.DAY_OF_MONTH)
                  val dayMonth = dayCal.get(Calendar.MONTH)
                  val isToday = dayCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                    dayCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
                  val isSelected = dayCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                    dayCal.get(Calendar.DAY_OF_YEAR) == selectedCal.get(Calendar.DAY_OF_YEAR)
                  val matchingMilestone = milestones.firstOrNull { matchesCalendarDay(it.dateText, dayNum, dayMonth) }
                  val matchingAnniversary = anniversaryDates.firstOrNull { matchesCalendarDay(it.dateText, dayNum, dayMonth) }
                  val isMilestoneDay = matchingMilestone != null || matchingAnniversary != null

                  CalendarDayCell(
                    dayNum = dayNum,
                    isToday = isToday,
                    isSelected = isSelected,
                    isMilestoneDay = isMilestoneDay,
                    isCurrentMonth = true,
                    isSunday = dayCal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY,
                    onClick = {
                      selectedDateMillis = dayCal.timeInMillis
                      activeCalendar = dayCal.clone() as Calendar
                      when {
                        matchingMilestone != null -> viewModel.showToast(
                          if (isEnglish) "${dayNum}/${dayMonth + 1}: ${matchingMilestone.title}!" else "${dayNum}/${dayMonth + 1}: ${matchingMilestone.title}!"
                        )
                        matchingAnniversary != null -> viewModel.showToast(
                          if (isEnglish) "Anniversary: ${matchingAnniversary.title} ❤️" else "Kỷ niệm: ${matchingAnniversary.title} ❤️"
                        )
                        isToday -> viewModel.showToast(
                          if (isEnglish) "Today: ${dayNum}/${dayMonth + 1}/${dayCal.get(Calendar.YEAR)}" else "Hôm nay: Ngày ${dayNum}/${dayMonth + 1}/${dayCal.get(Calendar.YEAR)}"
                        )
                        else -> viewModel.showToast(
                          if (isEnglish) "Selected: ${dayNum}/${dayMonth + 1}/${dayCal.get(Calendar.YEAR)}" else "Đã chọn: Ngày ${dayNum}/${dayMonth + 1}/${dayCal.get(Calendar.YEAR)}"
                        )
                      }
                    }
                  )
                }
              }
            } else {
              // --- MONTH VIEW ---
              val monthMatrix = remember(activeCalendar.timeInMillis, calendarNavTick) {
                computeMonthMatrix(activeCalendar)
              }

              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                monthMatrix.forEach { weekList ->
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    weekList.forEach { cellCal ->
                      val dayNum = cellCal.get(Calendar.DAY_OF_MONTH)
                      val dayMonth = cellCal.get(Calendar.MONTH)
                      val isCurrentMonth = dayMonth == activeCalendar.get(Calendar.MONTH)
                      val isToday = cellCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                        cellCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
                      val isSelected = cellCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                        cellCal.get(Calendar.DAY_OF_YEAR) == selectedCal.get(Calendar.DAY_OF_YEAR)
                      val matchingMilestone = milestones.firstOrNull { matchesCalendarDay(it.dateText, dayNum, dayMonth) }
                      val matchingAnniversary = anniversaryDates.firstOrNull { matchesCalendarDay(it.dateText, dayNum, dayMonth) }
                      val isMilestoneDay = matchingMilestone != null || matchingAnniversary != null

                      CalendarDayCell(
                        dayNum = dayNum,
                        isToday = isToday,
                        isSelected = isSelected,
                        isMilestoneDay = isMilestoneDay,
                        isCurrentMonth = isCurrentMonth,
                        isSunday = cellCal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY,
                        onClick = {
                          selectedDateMillis = cellCal.timeInMillis
                          activeCalendar = cellCal.clone() as Calendar
                          when {
                            matchingMilestone != null -> viewModel.showToast(
                              if (isEnglish) "${dayNum}/${dayMonth + 1}: ${matchingMilestone.title}!" else "${dayNum}/${dayMonth + 1}: ${matchingMilestone.title}!"
                            )
                            matchingAnniversary != null -> viewModel.showToast(
                              if (isEnglish) "Anniversary: ${matchingAnniversary.title} ❤️" else "Kỷ niệm: ${matchingAnniversary.title} ❤️"
                            )
                            isToday -> viewModel.showToast(
                              if (isEnglish) "Today: ${dayNum}/${dayMonth + 1}/${cellCal.get(Calendar.YEAR)}" else "Hôm nay: Ngày ${dayNum}/${dayMonth + 1}/${cellCal.get(Calendar.YEAR)}"
                            )
                            else -> viewModel.showToast(
                              if (isEnglish) "Selected: ${dayNum}/${dayMonth + 1}/${cellCal.get(Calendar.YEAR)}" else "Đã chọn: Ngày ${dayNum}/${dayMonth + 1}/${cellCal.get(Calendar.YEAR)}"
                            )
                          }
                        }
                      )
                    }
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3.3 Interactive Selected Day Schedule Card & Quick Reminder Upgrade
            val selDay = selectedCal.get(Calendar.DAY_OF_MONTH)
            val selMonth = selectedCal.get(Calendar.MONTH)
            val selYear = selectedCal.get(Calendar.YEAR)
            val formattedSelectedDate = String.format("%02d/%02d/%04d", selDay, selMonth + 1, selYear)
            val dayMatchingMilestones = milestones.filter { matchesCalendarDay(it.dateText, selDay, selMonth) }
            val dayMatchingAnniversaries = anniversaryDates.filter { matchesCalendarDay(it.dateText, selDay, selMonth) }

            Surface(
              shape = RoundedCornerShape(18.dp),
              color = Color(0xFFFFF0F5),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFC0D3)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                // Header: Selected Date Title & Event Count
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
                      imageVector = Icons.Filled.CalendarToday,
                      contentDescription = null,
                      tint = Primary,
                      modifier = Modifier.size(16.dp)
                    )
                    Text(
                      text = if (isEnglish) "Schedule for $formattedSelectedDate" else "Lịch hẹn ngày $formattedSelectedDate",
                      fontWeight = FontWeight.Bold,
                      fontSize = 13.sp,
                      color = Primary
                    )
                  }

                  val eventCount = dayMatchingMilestones.size + dayMatchingAnniversaries.size
                  Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = if (eventCount > 0) Primary else Color(0xFFE0E0E0)
                  ) {
                    Text(
                      text = if (isEnglish) "$eventCount events" else "$eventCount sự kiện",
                      fontSize = 10.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = if (eventCount > 0) Color.White else Color(0xFF616161),
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                  }
                }

                // Event list for this day if any
                if (dayMatchingMilestones.isNotEmpty() || dayMatchingAnniversaries.isNotEmpty()) {
                  dayMatchingMilestones.forEach { m ->
                    Surface(
                      shape = RoundedCornerShape(10.dp),
                      color = Color.White,
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                      ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFFF2D75), modifier = Modifier.size(15.dp))
                        Column(modifier = Modifier.weight(1f)) {
                          Text(m.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF26071B))
                          if (m.subtitle.isNotBlank()) {
                            Text(m.subtitle, fontSize = 11.sp, color = OnSurfaceVariant)
                          }
                        }
                      }
                    }
                  }

                  dayMatchingAnniversaries.forEach { a ->
                    Surface(
                      shape = RoundedCornerShape(10.dp),
                      color = Color.White,
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                      ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = Color(0xFF8E24AA), modifier = Modifier.size(15.dp))
                        Column(modifier = Modifier.weight(1f)) {
                          Text(a.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF26071B))
                          if (a.description.isNotBlank()) {
                            Text(a.description, fontSize = 11.sp, color = OnSurfaceVariant)
                          }
                        }
                      }
                    }
                  }
                } else {
                  Text(
                    text = if (isEnglish) "No reminders or milestones yet for this date." else "Chưa có lời nhắc hoặc kỷ niệm nào trong ngày này.",
                    fontSize = 11.5.sp,
                    color = OnSurfaceVariant
                  )
                }

                // Quick Action Button: Add Reminder with pre-filled selected date
                Button(
                  onClick = { viewModel.openAddAnniversaryDialog(formattedSelectedDate) },
                  shape = RoundedCornerShape(12.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D75)),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .testTag("btn_quick_add_reminder_date")
                ) {
                  Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = if (isEnglish) "Add Reminder for This Day" else "Thêm Nhắc Hẹn Cho Ngày Này",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                }
              }
            }
          }
        }
      }

      // Local Notification Scheduler for Love Milestones from Firestore
      item {
        FirestoreMilestonesSchedulerCard(
          upcomingMilestones = upcomingMilestones,
          isEnglish = isEnglish,
          onTestNotification = { viewModel.triggerTestMilestoneNotification() },
          onSyncFirestore = { viewModel.syncUpcomingMilestonesFromFirestore() }
        )
      }

      // Dedicated Room-Persisted Anniversary Dates Section
      item {
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("anniversary_dates_section")
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.12f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(20.dp)
                  )
                }
                Column {
                  Text(
                    text = if (isEnglish) "Your Anniversaries" else "Ngày Kỷ Niệm Của Hai Bạn",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                  )
                  Text(
                    text = if (isEnglish) "Stored in Room (${anniversaryDates.size} special dates)" else "Lưu trữ Room (${anniversaryDates.size} ngày đặc biệt)",
                    fontSize = 11.sp,
                    color = OnSurfaceVariant
                  )
                }
              }

              Surface(
                shape = RoundedCornerShape(20.dp),
                color = Primary.copy(alpha = 0.1f),
                modifier = Modifier
                  .clickable { viewModel.openAddAnniversaryDialog() }
                  .testTag("btn_add_anniversary_header")
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(15.dp)
                  )
                  Text(
                    text = if (isEnglish) "Add Date" else "Thêm ngày",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Notification quick action triggers
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = PrimaryFixed.copy(alpha = 0.45f),
                modifier = Modifier
                  .weight(1f)
                  .clickable { viewModel.triggerTestAnniversaryNotification() }
                  .testTag("btn_test_anniversary_notification")
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center
                ) {
                  Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(14.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = if (isEnglish) "Test Alarm 🔔" else "Thử chuông báo 🔔",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary
                  )
                }
              }

              Surface(
                shape = RoundedCornerShape(12.dp),
                color = SecondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier
                  .weight(1f)
                  .clickable { viewModel.resyncAllAnniversaryAlarms() }
                  .testTag("btn_resync_anniversary_alarms")
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center
                ) {
                  Icon(
                    imageVector = Icons.Filled.Alarm,
                    contentDescription = null,
                    tint = Secondary,
                    modifier = Modifier.size(14.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = if (isEnglish) "Resync ⏰" else "Đồng bộ lại ⏰",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Secondary
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (anniversaryDates.isEmpty()) {
              Text(
                text = if (isEnglish) "No custom anniversaries yet. Tap 'Add Date' to save your love story or special dates." else "Chưa có ngày kỷ niệm riêng. Nhấn 'Thêm ngày' để lưu ngày bắt đầu yêu, hẹn hò hoặc kỷ niệm đáng nhớ.",
                fontSize = 12.sp,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
              )
            } else {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                anniversaryDates.forEach { ann ->
                  AnniversaryDateRow(
                    item = ann,
                    isEnglish = isEnglish,
                    onToggleNotification = { viewModel.toggleAnniversaryNotification(ann) },
                    onDelete = { anniversaryToDelete = ann }
                  )
                }
              }
            }
          }
        }
      }

      // 4. Milestone Cards or Heart Badge Cards Stream
      if (currentFilter == "Huy Hiệu (12)" || currentFilter == "Badges (12)") {
        item {
          MilestoneBadgeDashboardCard(
            viewModel = viewModel,
            onOpenFullShowcase = { viewModel.openBadgeShowcase() }
          )
        }
        items(loveBadges, key = { "badge_${it.id}" }) { badge ->
          val isUnlocked = badge.targetDays <= loveDays
          MilestoneTimelineCard(
            badge = badge,
            isUnlocked = isUnlocked,
            currentLoveDays = loveDays,
            appLanguage = appLanguage,
            onClick = { viewModel.selectBadge(badge) }
          )
        }
      } else {
        items(filteredMilestones, key = { it.id }) { milestone ->
          MilestoneCard(
            milestone = milestone,
            isEnglish = isEnglish,
            onGiftClick = onNavigateToGifts,
            onNotificationToggle = { viewModel.toggleMilestoneNotification(milestone) },
            onEditClick = { viewModel.showToast(if (isEnglish) "Edit: ${milestone.title}" else "Chỉnh sửa: ${milestone.title}") },
            onAlbumClick = { viewModel.showToast(if (isEnglish) "Open photo album..." else "Mở album ảnh kỷ niệm...") },
            onDelete = { milestoneToDelete = milestone },
            onSetAlarm = {
              viewModel.openSetAlarmDialog(
                title = if (isEnglish) "Anniversary: ${milestone.title}" else "Kỷ niệm: ${milestone.title}",
                message = if (isEnglish) "Today is anniversary ${milestone.title}! ${milestone.subtitle}" else "Hôm nay là ngày kỷ niệm ${milestone.title}! ${milestone.subtitle}",
                reminderId = milestone.id
              )
            }
          )
        }
      }

      // 5. Sync Card
      item {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = SurfaceContainerLowest.copy(alpha = 0.7f),
          shadowElevation = 1.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f)
            ) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(SurfaceContainerHighest)
              ) {
                AsyncImage(
                  model = "https://lh3.googleusercontent.com/aida-public/AB6AXuCg-PmA8kAH3aEsx4nS5akuDkkWQeWMutmW8Lc76ASO-JMvtiwMNbsTfuYqBGpez7bHTAYekQNilJ5X5BHaP78pQf4tATX48UynvtOeQWG8kcCF-v9OqIdcm1OAjLGtZsO1ygTFLVd9qW-yngUnwCmOtlFWn_wzhCPvYfzMcFLVzeEoOX9NuP8fk911cjdlzd0yv0FvSo3h7qg26BWqyaqSpVwTuvKoKaZ20NJLOUWeBfHbRlqMZcIwfI1FL31cw1WGBrQ",
                  contentDescription = "Sync thumbnail",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
              }
              Column {
                Text(
                  text = if (isEnglish) "Timeline Data Synchronized" else "Đồng bộ dữ liệu mốc thời gian",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = Primary
                )
                Text(
                  text = if (isEnglish) "Migrated 4 events from previous timeline" else "Đã chuyển 4 sự kiện từ danh sách cũ",
                  fontSize = 11.sp,
                  color = OnSurfaceVariant
                )
              }
            }

            Icon(
              imageVector = Icons.Filled.Verified,
              contentDescription = "Verified",
              tint = Primary,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }

    // 6. Floating Action Buttons: "+ Nhắc Hẹn" & "+ Cột Mốc"
    Row(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 16.dp, bottom = 85.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // "+ Nhắc Hẹn" Button
      Surface(
        shape = RoundedCornerShape(50.dp),
        shadowElevation = 8.dp,
        color = Color(0xFFFF2D75),
        modifier = Modifier
          .clip(RoundedCornerShape(50.dp))
          .clickable { viewModel.openAddAnniversaryDialog() }
          .testTag("btn_fab_add_anniversary")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
          Icon(
            imageVector = Icons.Filled.NotificationsActive,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
          Text(
            text = if (isEnglish) "+ Reminder" else "+ Nhắc Hẹn",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
          )
        }
      }

      // "+ Cột Mốc" Button
      Surface(
        shape = RoundedCornerShape(50.dp),
        shadowElevation = 8.dp,
        modifier = Modifier
          .clip(RoundedCornerShape(50.dp))
          .background(
            Brush.horizontalGradient(
              listOf(RoseGradientStart, RoseGradientMid, RoseGradientEnd)
            )
          )
          .clickable { viewModel.openAddMilestoneDialog() }
          .testTag("btn_add_milestone")
      ) {
        Row(
          modifier = Modifier
            .background(
              Brush.horizontalGradient(
                listOf(RoseGradientStart, RoseGradientMid, RoseGradientEnd)
              )
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
          Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
          Text(
            text = if (isEnglish) "+ Milestone" else "+ Cột Mốc",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
          )
        }
      }
    }
  }
}

@Composable
fun AnniversaryDateRow(
  item: AnniversaryDateEntity,
  isEnglish: Boolean = false,
  onToggleNotification: () -> Unit,
  onDelete: () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = SurfaceContainerLowest,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("anniversary_item_${item.id}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f)
      ) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(PrimaryFixed.copy(alpha = 0.5f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Filled.CalendarToday,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(18.dp)
          )
        }
        Column {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = item.title,
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Bold,
              color = OnSurface
            )
            Text(
              text = item.dateText,
              fontSize = 11.5.sp,
              color = Primary,
              fontWeight = FontWeight.SemiBold
            )
            if (item.description.isNotBlank()) {
              Text(
                text = "• ${item.description}",
                fontSize = 11.sp,
                color = OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
          Text(
            text = if (item.notificationEnabled) {
              if (item.reminderDaysBefore > 0) {
                if (isEnglish) "🔔 Remind ${item.reminderDaysBefore} days before & on the day (09:00)" else "🔔 Nhắc trước ${item.reminderDaysBefore} ngày & đúng ngày (09:00)"
              } else {
                if (isEnglish) "🔔 Remind on anniversary date (09:00)" else "🔔 Nhắc đúng ngày kỷ niệm (09:00)"
              }
            } else {
              if (isEnglish) "🔕 Notification off" else "🔕 Thông báo đã tắt"
            },
            fontSize = 10.sp,
            color = if (item.notificationEnabled) Primary.copy(alpha = 0.85f) else OnSurfaceVariant.copy(alpha = 0.5f),
            fontWeight = if (item.notificationEnabled) FontWeight.Medium else FontWeight.Normal
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        IconButton(
          onClick = onToggleNotification,
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            imageVector = if (item.notificationEnabled) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications,
            contentDescription = if (isEnglish) "Anniversary notification" else "Thông báo kỷ niệm",
            tint = if (item.notificationEnabled) Primary else OnSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
          )
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = if (isEnglish) "Delete anniversary" else "Xóa ngày kỷ niệm",
            tint = OnSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
fun MilestoneCard(
  milestone: MilestoneEntity,
  isEnglish: Boolean = false,
  onGiftClick: () -> Unit,
  onNotificationToggle: () -> Unit,
  onEditClick: () -> Unit,
  onAlbumClick: () -> Unit,
  onDelete: () -> Unit,
  onSetAlarm: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("milestone_card_${milestone.id}")
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Top Tag Row + Notification & Alarm & Delete buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Category Tag
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = when {
              milestone.isImportant -> PrimaryFixed.copy(alpha = 0.5f)
              milestone.isPast -> TertiaryFixed.copy(alpha = 0.5f)
              else -> SurfaceContainerHigh
            }
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              val tagIcon = when {
                milestone.isImportant -> Icons.Filled.Stars
                milestone.categoryTag.contains("Sinh Nhật", ignoreCase = true) -> Icons.Filled.Cake
                milestone.isPast -> Icons.Filled.FlightTakeoff
                else -> Icons.Filled.VolunteerActivism
              }
              Icon(
                imageVector = tagIcon,
                contentDescription = null,
                tint = if (milestone.isImportant) Primary else if (milestone.isPast) Tertiary else Secondary,
                modifier = Modifier.size(13.dp)
              )
              Text(
                text = milestone.categoryTag,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (milestone.isImportant) Primary else if (milestone.isPast) OnTertiaryFixed else Secondary
              )
            }
          }

          // Secondary Tag
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceContainer
          ) {
            Text(
              text = milestone.secondaryTag,
              fontSize = 11.sp,
              color = OnSurfaceVariant,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }

          if (milestone.isUserCreated) {
            Surface(
              shape = RoundedCornerShape(20.dp),
              color = PrimaryFixed.copy(alpha = 0.9f)
            ) {
              Text(
                text = if (isEnglish) "Custom" else "Tự tạo",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }

          if (milestone.isPast) {
            // Computed from the real date, not a hardcoded "268 days ago" that used to show
            // for every past milestone regardless of when it actually happened.
            val daysAgo = remember(milestone.dateText) {
              DatePickerUtils.parseDateToUtcMillis(milestone.dateText)?.let { millis ->
                java.util.concurrent.TimeUnit.MILLISECONDS
                  .toDays(System.currentTimeMillis() - millis)
                  .coerceAtLeast(0)
              }
            }
            Surface(
              shape = RoundedCornerShape(20.dp),
              color = TertiaryFixed.copy(alpha = 0.8f)
            ) {
              Text(
                text = if (daysAgo != null) {
                  if (isEnglish) "$daysAgo days ago" else "Đã qua $daysAgo ngày"
                } else {
                  if (isEnglish) "In the past" else "Đã qua"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Tertiary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }
        }

        // Action Icons Row: Alarm + Notification + Delete
        Row(
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Set Alarm button
          IconButton(
            onClick = onSetAlarm,
            modifier = Modifier
              .size(32.dp)
              .clip(CircleShape)
              .background(SurfaceContainer)
              .testTag("btn_alarm_milestone_${milestone.id}")
          ) {
            Icon(
              imageVector = Icons.Filled.Alarm,
              contentDescription = if (isEnglish) "Set milestone alarm" else "Cài báo thức kỷ niệm",
              tint = Primary,
              modifier = Modifier.size(17.dp)
            )
          }

          // Notification Toggle
          if (!milestone.isPast) {
            IconButton(
              onClick = onNotificationToggle,
              modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(SurfaceContainer)
                .testTag("btn_notif_milestone_${milestone.id}")
            ) {
              Icon(
                imageVector = if (milestone.notificationEnabled) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications,
                contentDescription = "Notification",
                tint = if (milestone.notificationEnabled) Primary else OnSurfaceVariant,
                modifier = Modifier.size(17.dp)
              )
            }
          }

          // Delete milestone button
          IconButton(
            onClick = onDelete,
            modifier = Modifier
              .size(32.dp)
              .clip(CircleShape)
              .background(SurfaceContainer)
              .testTag("btn_delete_milestone_${milestone.id}")
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = if (isEnglish) "Delete milestone" else "Xóa kỷ niệm",
              tint = OnSurfaceVariant,
              modifier = Modifier.size(17.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Middle: Image + Title/Date + Countdown Pill
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Photo
        Box(
          modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .shadow(2.dp)
        ) {
          AsyncImage(
            model = milestone.imageUrl,
            contentDescription = milestone.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        }

        // Title and Date
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = milestone.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Spacer(modifier = Modifier.height(3.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = when {
                milestone.categoryTag.contains("Sinh Nhật") -> Icons.Filled.Cake
                milestone.isPast -> Icons.Filled.PhotoLibrary
                else -> Icons.Filled.CalendarToday
              },
              contentDescription = null,
              tint = Primary,
              modifier = Modifier.size(13.dp)
            )
            Text(
              text = "${milestone.dateText} • ${milestone.subtitle}",
              fontSize = 11.sp,
              color = OnSurfaceVariant
            )
          }
        }

        // Right side badge: Countdown Pill or Saved indicator
        if (milestone.isPast) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceContainer,
            modifier = Modifier.padding(2.dp)
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = if (isEnglish) "Saved" else "Đã lưu",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurfaceVariant
              )
            }
          }
        } else {
          // Countdown pill
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(16.dp))
              .then(
                if (milestone.isImportant) {
                  Modifier
                    .shadow(4.dp, RoundedCornerShape(16.dp))
                    .background(
                      Brush.verticalGradient(
                        listOf(RoseGradientStart, RoseGradientMid)
                      )
                    )
                } else {
                  Modifier.background(SurfaceContainerHigh)
                }
              )
              .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = if (isEnglish) "LEFT" else "CÒN",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (milestone.isImportant) Color.White.copy(alpha = 0.9f) else OnSurfaceVariant
              )
              Text(
                text = "${milestone.daysRemaining}",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (milestone.isImportant) Color.White else Primary,
                lineHeight = 26.sp
              )
              Text(
                text = if (isEnglish) "days" else "ngày",
                fontSize = 9.sp,
                color = if (milestone.isImportant) Color.White.copy(alpha = 0.9f) else OnSurfaceVariant
              )
            }
          }
        }
      }

      // Progress bar if present (e.g. 1000 days)
      if (milestone.progressPercent != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = if (isEnglish) "Milestone Progress" else "Tiến trình mốc lớn",
            fontSize = 11.sp,
            color = OnSurfaceVariant
          )
          Text(
            text = "${milestone.progressPercent}%",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Primary
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
          progress = { milestone.progressPercent / 100f },
          modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(50.dp)),
          color = Primary,
          trackColor = SurfaceContainerHighest,
          strokeCap = StrokeCap.Round
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Action Buttons inside Card. Branches on categoryTag, not milestone.id: id is a raw
      // Room autoincrement primary key, so branching on `id == 1L/2L` only "worked" for the 2
      // seed rows and would silently break once those are deleted or a real milestone gets a
      // new id. categoryTag is the actual semantic field (also user-editable in
      // AddMilestoneDialog) and matches the seed data's own "Cột Mốc"/"Kỷ Niệm" tags exactly.
      when {
        milestone.categoryTag == "Cột Mốc" -> {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Surface(
              shape = RoundedCornerShape(50.dp),
              color = PrimaryFixed.copy(alpha = 0.7f),
              modifier = Modifier
                .weight(1f)
                .clickable { onGiftClick() }
            ) {
              Row(
                modifier = Modifier.padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.CardGiftcard,
                  contentDescription = null,
                  tint = OnPrimaryFixedVariant,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (isEnglish) "Gift Ideas" else "Gợi ý quà tặng",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = OnPrimaryFixedVariant
                )
              }
            }

            Surface(
              shape = RoundedCornerShape(50.dp),
              color = SurfaceContainerHighest.copy(alpha = 0.7f),
              modifier = Modifier.clickable { onEditClick() }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.EditCalendar,
                  contentDescription = null,
                  tint = OnSurfaceVariant,
                  modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = if (isEnglish) "Edit" else "Chỉnh sửa",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Medium,
                  color = OnSurfaceVariant
                )
              }
            }
          }
        }
        milestone.categoryTag == "Kỷ Niệm" -> {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Surface(
              shape = RoundedCornerShape(50.dp),
              color = SurfaceContainerHighest.copy(alpha = 0.8f),
              modifier = Modifier
                .weight(1f)
                .clickable { onGiftClick() }
            ) {
              Row(
                modifier = Modifier.padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.CardGiftcard,
                  contentDescription = null,
                  tint = Primary,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (isEnglish) "Surprise Ideas & Gifts" else "Ý tưởng bất ngờ & Quà",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = OnSurface
                )
              }
            }

            Surface(
              shape = CircleShape,
              color = SurfaceContainerHighest.copy(alpha = 0.8f),
              modifier = Modifier
                .size(36.dp)
                .clickable { onEditClick() }
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Filled.MoreHoriz,
                  contentDescription = "More",
                  tint = OnSurfaceVariant,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }
        }
        milestone.isPast -> {
          Surface(
            shape = RoundedCornerShape(50.dp),
            color = SurfaceContainerLow,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onAlbumClick() }
          ) {
            Row(
              modifier = Modifier.padding(vertical = 9.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              Icon(
                imageVector = Icons.Filled.PhotoAlbum,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (isEnglish) "View memories album (12 photos)" else "Xem lại album kỷ niệm (12 ảnh)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Primary
              )
            }
          }
        }
        else -> {
          Surface(
            shape = RoundedCornerShape(50.dp),
            color = PrimaryFixed.copy(alpha = 0.7f),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onEditClick() }
          ) {
            Row(
              modifier = Modifier.padding(vertical = 9.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              Icon(
                imageVector = Icons.Filled.EditCalendar,
                contentDescription = null,
                tint = OnPrimaryFixedVariant,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (isEnglish) "Plan a date" else "Lên kế hoạch hẹn hò",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = OnPrimaryFixedVariant
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun FirestoreMilestonesSchedulerCard(
  upcomingMilestones: List<com.example.alarm.LoveMilestoneInfo>,
  isEnglish: Boolean = false,
  onTestNotification: () -> Unit,
  onSyncFirestore: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(
        width = 1.dp,
        brush = Brush.horizontalGradient(listOf(Primary.copy(alpha = 0.4f), Secondary.copy(alpha = 0.4f))),
        shape = RoundedCornerShape(24.dp)
      )
      .testTag("firestore_milestones_scheduler_card")
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(Brush.linearGradient(listOf(Primary, Secondary))),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Filled.NotificationsActive,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(22.dp)
            )
          }
          Column {
            Text(
              text = if (isEnglish) "Love Milestones Schedule" else "Lịch Báo Cột Mốc Tình Yêu",
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = OnSurface
            )
            Text(
              text = if (isEnglish) "Auto-scheduled from anniversary" else "Tự động lên lịch từ Ngày Yêu Firestore",
              fontSize = 11.sp,
              color = OnSurfaceVariant
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(20.dp),
          color = PrimaryContainer.copy(alpha = 0.8f)
        ) {
          Text(
            text = if (isEnglish) "3 Alert Tiers" else "3 Cấp Báo Thức",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Informative alert rhythm banner
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceAround,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text("🔔", fontSize = 12.sp)
            Text(if (isEnglish) "3 days before" else "Trước 3 ngày", fontSize = 11.sp, color = OnSurfaceVariant)
          }
          Text("•", color = OnSurfaceVariant.copy(alpha = 0.4f))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text("🌹", fontSize = 12.sp)
            Text(if (isEnglish) "1 day before" else "Trước 1 ngày", fontSize = 11.sp, color = OnSurfaceVariant)
          }
          Text("•", color = OnSurfaceVariant.copy(alpha = 0.4f))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text("🎉", fontSize = 12.sp)
            Text(if (isEnglish) "On the day" else "Đúng ngày", fontSize = 11.sp, color = Primary, fontWeight = FontWeight.Bold)
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // List of upcoming milestones
      if (upcomingMilestones.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = if (isEnglish) "Syncing upcoming milestones from anniversary... 💕" else "Đang đồng bộ các cột mốc tiếp theo từ ngày yêu Firestore... 💕",
            fontSize = 12.sp,
            color = OnSurfaceVariant
          )
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          upcomingMilestones.take(4).forEach { ms ->
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = SurfaceContainerLowest,
              border = BorderStroke(0.5.dp, Primary.copy(alpha = 0.2f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Text(text = ms.emoji, fontSize = 20.sp)
                  Column {
                    Text(
                      text = ms.title,
                      fontSize = 13.sp,
                      fontWeight = FontWeight.Bold,
                      color = OnSurface
                    )
                    Text(
                      text = "${ms.formattedDate} • ${ms.description}",
                      fontSize = 11.sp,
                      color = OnSurfaceVariant
                    )
                  }
                }

                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = if (ms.daysRemaining <= 7) Primary.copy(alpha = 0.15f) else SurfaceContainerHigh
                ) {
                  Text(
                    text = if (ms.daysRemaining == 0) (if (isEnglish) "Today! 🎉" else "Hôm nay! 🎉") else (if (isEnglish) "${ms.daysRemaining} days left" else "Còn ${ms.daysRemaining} ngày"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (ms.daysRemaining <= 7) Primary else OnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                  )
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Action Buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Button(
          onClick = onTestNotification,
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Primary),
          modifier = Modifier
            .weight(1f)
            .testTag("test_milestone_notification_button")
        ) {
          Icon(
            imageVector = Icons.Filled.NotificationsActive,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(text = if (isEnglish) "Test Alert" else "Thử Chuông Báo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
          onClick = onSyncFirestore,
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, Primary.copy(alpha = 0.5f)),
          modifier = Modifier
            .weight(1f)
            .testTag("resync_firestore_milestones_button")
        ) {
          Icon(
            imageVector = Icons.Filled.Alarm,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(text = if (isEnglish) "Sync Firestore" else "Đồng Bộ Firestore", fontSize = 12.sp, color = Primary, fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}
