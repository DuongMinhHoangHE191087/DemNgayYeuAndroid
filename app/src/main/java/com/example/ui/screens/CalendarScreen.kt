@file:Suppress("FunctionName")
package com.example.ui.screens

import com.example.ui.util.AppLanguage
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
  var selectedDay by remember { mutableIntStateOf(11) }
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
              text = if (isEnglish) "September 2026" else "Tháng 9, 2026",
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
                onClick = { viewModel.showToast(if (isEnglish) "View August 2026" else "Xem tháng 8/2026") },
                modifier = Modifier.size(32.dp)
              ) {
                Icon(
                  imageVector = Icons.Filled.ChevronLeft,
                  contentDescription = if (isEnglish) "Previous month" else "Tháng trước",
                  tint = OnSurfaceVariant,
                  modifier = Modifier.size(18.dp)
                )
              }
              Text(
                text = if (isEnglish) "Sep" else "T9",
                color = Primary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
              )
              IconButton(
                onClick = { viewModel.showToast(if (isEnglish) "View October 2026" else "Xem tháng 10/2026") },
                modifier = Modifier.size(32.dp)
              ) {
                Icon(
                  imageVector = Icons.Filled.ChevronRight,
                  contentDescription = if (isEnglish) "Next month" else "Tháng sau",
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

      // 3. Frosted Mini Calendar Strip
      item {
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.85f)),
          elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            // Day names row
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

            // Day numbers row (07 to 13)
            val days = listOf(7, 8, 9, 10, 11, 12, 13)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceAround,
              verticalAlignment = Alignment.CenterVertically
            ) {
              days.forEach { dayNum ->
                val isMilestoneDay = dayNum == 11
                val isToday = dayNum == 9
                val isSelected = selectedDay == dayNum

                Box(
                  modifier = Modifier
                    .width(38.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                      when {
                        isMilestoneDay -> Modifier
                          .shadow(6.dp, RoundedCornerShape(14.dp))
                          .background(
                            Brush.verticalGradient(
                              listOf(RoseGradientStart, RoseGradientMid)
                            )
                          )
                        isSelected && !isMilestoneDay -> Modifier
                          .background(PrimaryFixed.copy(alpha = 0.6f))
                        isToday -> Modifier
                          .background(SurfaceContainerLow)
                          .border(1.dp, Primary.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        else -> Modifier
                      }
                    )
                    .clickable {
                      selectedDay = dayNum
                      if (dayNum == 11) {
                        viewModel.showToast(if (isEnglish) "Sep 11: 1,000 Days Anniversary together!" else "11/09: Cột mốc 1.000 ngày bên nhau!")
                      } else if (dayNum == 9) {
                        viewModel.showToast(if (isEnglish) "Today: Sep 09, 2026" else "Hôm nay: Ngày 09/09/2026")
                      }
                    },
                  contentAlignment = Alignment.Center
                ) {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                  ) {
                    Text(
                      text = String.format("%02d", dayNum),
                      fontSize = 13.sp,
                      fontWeight = if (isMilestoneDay || isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                      color = when {
                        isMilestoneDay -> Color.White
                        dayNum == 13 -> Secondary
                        else -> OnSurface
                      }
                    )
                    if (isMilestoneDay) {
                      Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                      )
                    } else if (isToday) {
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
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Status Note inside Calendar
            Surface(
              shape = RoundedCornerShape(50.dp),
              color = PrimaryFixed.copy(alpha = 0.5f),
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  viewModel.showToast(if (isEnglish) "Approaching 1,000 days of love!" else "Sắp đến mốc 1.000 ngày yêu nhau!")
                }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Icon(
                    imageVector = Icons.Filled.Stars,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(16.dp)
                  )
                  Text(
                    text = if (isEnglish) "Only 3 days left until 1,000th Love Anniversary!" else "Chỉ còn 3 ngày đến Kỷ niệm 1.000 ngày yêu nhau!",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnPrimaryFixed
                  )
                }
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                  contentDescription = null,
                  tint = Primary,
                  modifier = Modifier.size(14.dp)
                )
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

    // 6. Floating Action Button "+ Thêm Kỷ Niệm"
    Box(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 16.dp, bottom = 85.dp)
    ) {
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
            .padding(horizontal = 18.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
          )
          Text(
            text = if (isEnglish) "Add Milestone" else "Thêm Kỷ Niệm",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
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
                text = if (isEnglish) "Custom" else "Tự tạo (Room)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }

          if (milestone.isPast) {
            Surface(
              shape = RoundedCornerShape(20.dp),
              color = TertiaryFixed.copy(alpha = 0.8f)
            ) {
              Text(
                text = if (isEnglish) "268 days ago" else "Đã qua 268 ngày",
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

      // Action Buttons inside Card
      when {
        milestone.id == 1L -> {
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
        milestone.id == 2L -> {
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
