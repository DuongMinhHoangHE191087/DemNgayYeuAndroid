package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarm.LoveMilestoneInfo
import com.example.data.model.CustomReminderEntity
import com.example.ui.theme.AppDensity
import com.example.ui.theme.OnSurface
import com.example.ui.theme.OnSurfaceVariant
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryFixed
import com.example.ui.util.AppLanguage
import com.example.ui.viewmodel.InLoveViewModel

internal data class NotificationItem(val emoji: String, val title: String, val whenText: String, val urgent: Boolean)

internal fun buildNotificationItems(
  milestones: List<LoveMilestoneInfo>,
  reminders: List<CustomReminderEntity>,
  isEnglish: Boolean
): List<NotificationItem> {
  val fromMilestones = milestones.map {
    val whenText = when (it.daysRemaining) {
      0 -> if (isEnglish) "Today" else "Hôm nay"
      1 -> if (isEnglish) "Tomorrow" else "Ngày mai"
      else -> if (isEnglish) "${it.daysRemaining}d · ${it.formattedDate}" else "${it.daysRemaining} ngày · ${it.formattedDate}"
    }
    NotificationItem(it.emoji, it.title, whenText, it.daysRemaining <= 3)
  }
  val fromReminders = reminders.map {
    NotificationItem("⏰", it.title, it.alarmTimeFormatted.ifBlank { it.dateText }, false)
  }
  return fromMilestones + fromReminders
}

internal fun topNotification(items: List<NotificationItem>): NotificationItem? =
  items.firstOrNull { it.urgent } ?: items.firstOrNull()

/** The most important unread notification, or null when nothing is pending / all read. */
@Composable
internal fun rememberTopNotification(viewModel: InLoveViewModel): NotificationItem? {
  val milestones by viewModel.upcomingMilestones.collectAsState()
  val reminders by viewModel.customReminders.collectAsState()
  val isAllRead by viewModel.isAllNotificationsRead.collectAsState()
  val isEnglish = viewModel.appLanguage.collectAsState().value == AppLanguage.EN
  return remember(milestones, reminders, isAllRead, isEnglish) {
    if (isAllRead) null else topNotification(buildNotificationItems(milestones, reminders, isEnglish))
  }
}

/** True when there is something to announce and the user has not yet marked it all read. */
@Composable
fun rememberHasUnreadNotifications(viewModel: InLoveViewModel): Boolean {
  val milestones by viewModel.upcomingMilestones.collectAsState()
  val reminders by viewModel.customReminders.collectAsState()
  val isAllRead by viewModel.isAllNotificationsRead.collectAsState()
  return !isAllRead && (milestones.isNotEmpty() || reminders.isNotEmpty())
}

/** Full notification list: upcoming love milestones plus the user's own reminders. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSheet(viewModel: InLoveViewModel, onDismiss: () -> Unit) {
  val milestones by viewModel.upcomingMilestones.collectAsState()
  val reminders by viewModel.customReminders.collectAsState()
  val isAllRead by viewModel.isAllNotificationsRead.collectAsState()
  val isEnglish = viewModel.appLanguage.collectAsState().value == AppLanguage.EN

  val items = remember(milestones, reminders, isEnglish) { buildNotificationItems(milestones, reminders, isEnglish) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    containerColor = Color.White
  ) {
    AppDensity {
    Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).testTag("notifications_sheet")) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
          Text(
            if (isEnglish) "Notifications" else "Thông báo",
            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnSurface
          )
          Text(
            text = if (isEnglish) "Sent to your device when events approach" else "Thông báo chỉ gửi đến bạn khi sắp đến ngày",
            fontSize = 12.sp,
            color = OnSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
          )
        }
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(PrimaryFixed.copy(alpha = if (isAllRead) 0.3f else 0.7f))
            .clickable(enabled = !isAllRead) { viewModel.markAllNotificationsRead() }
            .testTag("btn_mark_all_read")
        ) {
          Icon(
            Icons.Filled.DoneAll,
            contentDescription = if (isEnglish) "Mark all read" else "Đã đọc tất cả",
            tint = if (isAllRead) OnSurfaceVariant else Primary,
            modifier = Modifier.size(20.dp)
          )
        }
      }

      if (items.isEmpty()) {
        Column(
          Modifier.fillMaxWidth().padding(vertical = 40.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(Icons.Filled.NotificationsNone, null, tint = OnSurfaceVariant, modifier = Modifier.size(40.dp))
          Text(if (isEnglish) "All quiet" else "Chưa có thông báo nào", fontSize = 14.sp, color = OnSurfaceVariant)
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(items) { n ->
            Row(
              Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (n.urgent && !isAllRead) PrimaryFixed.copy(alpha = 0.55f) else Color(0xFFFFF5F8))
                .clickable { viewModel.markAllNotificationsRead() }
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Text(n.emoji, fontSize = 24.sp)
              Column(Modifier.weight(1f)) {
                Text(n.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                Text(n.whenText, fontSize = 12.sp, color = if (n.urgent) Primary else OnSurfaceVariant)
              }
              if (n.urgent && !isAllRead) {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = Primary.copy(alpha = 0.15f)
                ) {
                  Text(
                    text = if (isEnglish) "Soon" else "Sắp tới",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
}
