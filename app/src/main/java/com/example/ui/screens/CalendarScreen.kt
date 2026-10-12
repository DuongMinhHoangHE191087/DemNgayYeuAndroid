@file:Suppress("FunctionName")
package com.example.ui.screens

import com.example.ui.util.AppLanguage
import java.util.Calendar
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stars
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import com.example.ui.components.pressClickable
import com.example.ui.theme.CalendarAccent
import com.example.ui.theme.MinTouchBox
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

/** Stored daysRemaining/isPast are snapshots from creation time; recompute from the date when it parses. */
private fun MilestoneEntity.withLiveDays(): MilestoneEntity {
  val days = DatePickerUtils.daysUntil(dateText) ?: return this
  return copy(daysRemaining = days, isPast = days < 0)
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
      isEnglish = isEnglish,
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
      isEnglish = isEnglish,
      onConfirm = {
        viewModel.deleteMilestone(milestone.id)
        milestoneToDelete = null
      },
      onDismiss = {
        milestoneToDelete = null
      }
    )
  }

  val liveMilestones = remember(milestones) { milestones.map { it.withLiveDays() } }
  val filteredMilestones = remember(liveMilestones, currentFilter) {
    val thisYear = java.time.LocalDate.now().year
    when (currentFilter) {
      "UPCOMING" -> liveMilestones.filter { !it.isPast }
      "PAST" -> liveMilestones.filter { it.isPast }
      "YEAR" -> liveMilestones.filter { ms ->
        DatePickerUtils.daysUntil(ms.dateText)?.let { java.time.LocalDate.now().plusDays(it.toLong()).year == thisYear } == true
      }
      else -> liveMilestones
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp),
      contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
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
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = if (isEnglish) "${monthNamesEn[displayMonth]} $displayYear" else "${monthNamesVi[displayMonth]}, $displayYear",
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold,
              color = OnSurface
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            // Add menu sits in the header so it never covers the list
            var menuOpen by remember { mutableStateOf(false) }
            Box {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .size(MinTouchBox)
                  .clip(CircleShape)
                  .pressClickable(onClick = { menuOpen = true })
                  .testTag("btn_fab_add_anniversary")
              ) {
                Surface(
                  shape = CircleShape,
                  shadowElevation = 2.dp,
                  color = CalendarAccent,
                  modifier = Modifier.size(40.dp)
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.Filled.Add,
                      contentDescription = if (isEnglish) "Add" else "Thêm",
                      tint = Color.White,
                      modifier = Modifier.size(22.dp)
                    )
                  }
                }
              }
              DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                  text = { Text(if (isEnglish) "Anniversary date" else "Ngày kỷ niệm") },
                  leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null, tint = Primary) },
                  onClick = { menuOpen = false; viewModel.openAddAnniversaryDialog() }
                )
                DropdownMenuItem(
                  text = { Text(if (isEnglish) "Milestone" else "Cột mốc") },
                  leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, tint = Primary) },
                  onClick = { menuOpen = false; viewModel.openAddMilestoneDialog() }
                )
              }
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
                  modifier = Modifier.size(MinTouchBox).testTag("btn_calendar_prev")
                ) {
                  Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = if (isEnglish) "Previous" else "Trước",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                  )
                }
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
                  modifier = Modifier.size(MinTouchBox).testTag("btn_calendar_next")
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
      }

      // 2. Filter Pills Row
      item {
        // Language-independent keys are the state; labels (with live counts) are display only.
        val year = java.time.LocalDate.now().year
        val filterOptions = listOf(
          Triple("ALL", (if (isEnglish) "All" else "Tất cả") + " (${liveMilestones.size})", Icons.Filled.AutoAwesome),
          Triple("BADGES", (if (isEnglish) "Badges" else "Huy Hiệu") + " (${loveBadges.size})", Icons.Filled.EmojiEvents),
          Triple("UPCOMING", (if (isEnglish) "Upcoming" else "Sắp tới") + " (${liveMilestones.count { !it.isPast }})", Icons.Filled.HourglassTop),
          Triple("PAST", (if (isEnglish) "Past" else "Đã qua") + " (${liveMilestones.count { it.isPast }})", Icons.Filled.HistoryEdu),
          Triple("YEAR", (if (isEnglish) "Year $year" else "Năm $year"), Icons.Filled.Favorite)
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          filterOptions.forEach { (filterKey, filterName, icon) ->
            val isSelected = currentFilter == filterKey
            Surface(
              shape = RoundedCornerShape(24.dp),
              color = if (isSelected) Color.Transparent else SurfaceContainerLowest.copy(alpha = 0.85f),
              shadowElevation = if (isSelected) 0.dp else 1.dp,
              modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .then(
                  if (isSelected) {
                    Modifier.background(
                      Brush.horizontalGradient(
                        listOf(Primary, CalendarAccent)
                      )
                    )
                  } else Modifier
                )
                .pressClickable(onClick = { viewModel.setCalendarFilter(filterKey) })
                .testTag("filter_pill_$filterKey")
            ) {
              Row(
                modifier = Modifier
                  .animateContentSize()
                  .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(
                  imageVector = icon,
                  contentDescription = filterName,
                  tint = if (isSelected) Color.White else OnSurfaceVariant,
                  modifier = Modifier.size(18.dp)
                )
                // Icon-only pills; only the selected one spells out its name.
                if (isSelected) {
                  Text(
                    text = filterName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                }
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

          }
        }
      }

      // Dedicated Room-Persisted Anniversary Dates Section (keyed: expand state must survive filter changes)
      item(key = "anniversaries") {
        // Collapsible: header tap toggles the list; expanded by default
        var anniversaryExpanded by rememberSaveable { mutableStateOf(true) }
        val anniversaryChevron by animateFloatAsState(
          targetValue = if (anniversaryExpanded) 180f else 0f,
          label = "anniversary_section_chevron"
        )
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
              modifier = Modifier
                .fillMaxWidth()
                .clickable { anniversaryExpanded = !anniversaryExpanded },
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
                    text = if (isEnglish) "${anniversaryDates.size} special dates" else "${anniversaryDates.size} ngày đặc biệt",
                    fontSize = 11.sp,
                    color = OnSurfaceVariant
                  )
                }
              }

              Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (isEnglish) "Expand" else "Mở rộng",
                tint = OnSurfaceVariant,
                modifier = Modifier
                  .size(22.dp)
                  .rotate(anniversaryChevron)
              )
            }

            AnimatedVisibility(
              visible = anniversaryExpanded,
              enter = expandVertically() + fadeIn(),
              exit = shrinkVertically() + fadeOut()
            ) {
              Column {
                Spacer(modifier = Modifier.height(12.dp))

                if (anniversaryDates.isEmpty()) {
                  Text(
                    text = if (isEnglish) "Tap + to add your first date" else "Nhấn + để thêm ngày đầu tiên",
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                  )
                } else {
                  // Soonest first; dates already passed go last, unparseable ones at the very end
                  val ordered = remember(anniversaryDates) {
                    anniversaryDates.sortedBy { a ->
                      DatePickerUtils.daysUntil(a.dateText)?.let { if (it < 0) it + 100_000 else it } ?: Int.MAX_VALUE
                    }
                  }
                  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ordered.forEach { ann ->
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
        }
      }

      // 4. Milestone Cards or Heart Badge Cards Stream
      if (currentFilter == "BADGES") {
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
                message = if (isEnglish) "Today is anniversary ${milestone.title}! ${milestone.subtitle}" else "Hôm nay là ngày kỷ niệm ${milestone.title}! ${milestone.subtitle}"
              )
            }
          )
        }
      }

      item { Spacer(modifier = Modifier.height(24.dp)) }
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
  // Tap the row to reveal the notification toggle and delete
  var expanded by remember { mutableStateOf(false) }
  val chevronRotation by animateFloatAsState(
    targetValue = if (expanded) 180f else 0f,
    label = "anniversary_chevron"
  )
  Surface(
    onClick = { expanded = !expanded },
    shape = RoundedCornerShape(16.dp),
    color = SurfaceContainerLowest,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("anniversary_item_${item.id}")
  ) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
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
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = item.title,
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Bold,
              color = OnSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = buildString {
                append(item.dateText)
                append(if (item.notificationEnabled) " · 🔔" else " · 🔕")
                if (item.notificationEnabled && item.reminderDaysBefore > 0) {
                  append(if (isEnglish) " ${item.reminderDaysBefore}d before" else " trước ${item.reminderDaysBefore} ngày")
                }
              },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              fontSize = 11.sp,
              color = if (item.notificationEnabled) Primary.copy(alpha = 0.85f) else OnSurfaceVariant.copy(alpha = 0.5f),
              fontWeight = if (item.notificationEnabled) FontWeight.Medium else FontWeight.Normal
            )
          }
        }

        Icon(
          imageVector = Icons.Filled.ExpandMore,
          contentDescription = if (isEnglish) "Expand" else "Mở rộng",
          tint = OnSurfaceVariant,
          modifier = Modifier
            .size(20.dp)
            .rotate(chevronRotation)
        )
      }

      AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = onToggleNotification,
            modifier = Modifier
              .size(MinTouchBox)
              .testTag("btn_anniversary_notify_${item.id}")
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
            modifier = Modifier
              .size(MinTouchBox)
              .testTag("btn_anniversary_delete_${item.id}")
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
  // Tap the card to reveal notification, alarm, edit and delete
  var expanded by remember { mutableStateOf(false) }
  Card(
    onClick = { expanded = !expanded },
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("milestone_card_${milestone.id}")
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
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
                // Same icons as the AddMilestoneDialog category picker.
                milestone.categoryTag == "Hẹn Hò" -> Icons.Filled.LocalCafe
                milestone.categoryTag == "Du Lịch" -> Icons.Filled.Flight
                milestone.categoryTag == "Tình Yêu" -> Icons.Filled.Favorite
                milestone.categoryTag == "Kỷ Niệm" -> Icons.Filled.Star
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

          // Secondary Tag. "Cột mốc mới" is only the placeholder AddMilestoneDialog writes; the
          // "Custom" chip below already says the same thing, so skip it.
          if (milestone.secondaryTag.isNotBlank() && milestone.secondaryTag != "Cột mốc mới") {
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

        // Chevron: shows that the card expands; the actions live in the expanded area below
        val chevronRotation by animateFloatAsState(
          targetValue = if (expanded) 180f else 0f,
          label = "milestone_chevron"
        )
        Icon(
          imageVector = Icons.Filled.ExpandMore,
          contentDescription = if (isEnglish) "Expand" else "Mở rộng",
          tint = OnSurfaceVariant,
          modifier = Modifier
            .size(22.dp)
            .rotate(chevronRotation)
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Middle: Image + Title/Date + Countdown Pill
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Photo
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PrimaryFixed.copy(alpha = 0.4f)) // visible placeholder while offline / loading
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
              imageVector = if (milestone.isPast) Icons.Filled.PhotoLibrary else Icons.Filled.CalendarToday,
              contentDescription = null,
              tint = Primary,
              modifier = Modifier.size(13.dp)
            )
            Text(
              text = milestone.dateText,
              fontSize = 11.sp,
              color = OnSurfaceVariant,
              maxLines = 1
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
        }
      }

      // Progress bar if present (e.g. 1000 days)
      if (milestone.progressPercent != null) {
        // Seed rows store a 0..1 fraction, the card expects 0..100; normalize so "0.1%" can't show.
        val pct = (if (milestone.progressPercent <= 1f) milestone.progressPercent * 100f else milestone.progressPercent)
          .coerceIn(0f, 100f)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          LinearProgressIndicator(
            progress = { pct / 100f },
            modifier = Modifier
              .weight(1f)
              .height(6.dp)
              .clip(RoundedCornerShape(50.dp)),
            color = Primary,
            trackColor = SurfaceContainerHighest,
            strokeCap = StrokeCap.Round
          )
          Text(
            text = "${pct.toInt()}%",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Primary
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Action Buttons inside Card. Branches on categoryTag, not milestone.id: id is a raw
      // Room autoincrement primary key, so branching on `id == 1L/2L` only "worked" for the 2
      // seed rows and would silently break once those are deleted or a real milestone gets a
      // new id. categoryTag is the actual semantic field (also user-editable in
      // AddMilestoneDialog) and matches the seed data's own "Cột Mốc"/"Kỷ Niệm" tags exactly.
      // Gift ideas on every card. Past custom dates show the album instead.
      // Edit and delete live in the expanded area below.
      when {
        milestone.isPast && milestone.categoryTag != "Cột Mốc" && milestone.categoryTag != "Kỷ Niệm" -> {
          Surface(
            shape = RoundedCornerShape(50.dp),
            color = SurfaceContainerLow,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onAlbumClick() }
          ) {
            Row(
              modifier = Modifier.padding(vertical = 7.dp),
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
                text = if (isEnglish) "Memories album" else "Album kỷ niệm",
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
              .clickable { onGiftClick() }
          ) {
            Row(
              modifier = Modifier.padding(vertical = 7.dp),
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
                text = if (isEnglish) "Gift ideas" else "Gợi ý quà",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = OnPrimaryFixedVariant
              )
            }
          }
        }
      }

      // Expanded area: tap the card to show the bell (notify + alarm), edit and delete
      AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
      ) {
        var menuOpen by remember { mutableStateOf(false) }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // One bell: its menu holds the notification toggle and the custom alarm
          Box {
            val bellOn = !milestone.isPast && milestone.notificationEnabled
            IconButton(
              onClick = { menuOpen = true },
              modifier = Modifier
                .size(MinTouchBox)
                .clip(CircleShape)
                .background(SurfaceContainer)
                .testTag("btn_bell_milestone_${milestone.id}")
            ) {
              Icon(
                imageVector = if (bellOn) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications,
                contentDescription = if (isEnglish) "Reminders" else "Nhắc nhở",
                tint = if (bellOn) Primary else OnSurfaceVariant,
                modifier = Modifier.size(17.dp)
              )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
              if (!milestone.isPast) {
                DropdownMenuItem(
                  modifier = Modifier.testTag("menu_notif_milestone_${milestone.id}"),
                  text = { Text(if (isEnglish) "Notify" else "Thông báo") },
                  leadingIcon = {
                    Icon(
                      imageVector = if (milestone.notificationEnabled) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications,
                      contentDescription = null
                    )
                  },
                  trailingIcon = {
                    if (milestone.notificationEnabled) {
                      Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = Primary)
                    }
                  },
                  onClick = {
                    menuOpen = false
                    onNotificationToggle()
                  }
                )
              }
              DropdownMenuItem(
                modifier = Modifier.testTag("menu_alarm_milestone_${milestone.id}"),
                text = { Text(if (isEnglish) "Alarm" else "Báo thức") },
                leadingIcon = { Icon(imageVector = Icons.Filled.Alarm, contentDescription = null) },
                onClick = {
                  menuOpen = false
                  onSetAlarm()
                }
              )
            }
          }

          IconButton(
            onClick = onEditClick,
            modifier = Modifier
              .size(MinTouchBox)
              .clip(CircleShape)
              .background(SurfaceContainer)
              .testTag("btn_edit_milestone_${milestone.id}")
          ) {
            Icon(
              imageVector = Icons.Filled.Edit,
              contentDescription = if (isEnglish) "Edit milestone" else "Chỉnh sửa",
              tint = OnSurfaceVariant,
              modifier = Modifier.size(17.dp)
            )
          }

          IconButton(
            onClick = onDelete,
            modifier = Modifier
              .size(MinTouchBox)
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
    }
  }
}
