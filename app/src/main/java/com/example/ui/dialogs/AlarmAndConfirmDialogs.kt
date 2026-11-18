@file:Suppress("FunctionName")
package com.example.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.ui.components.AppAlertDialog
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.animateDialogEntrance
import com.example.ui.designsystem.InLoveColors
import com.example.ui.designsystem.InLoveRadii
import com.example.ui.designsystem.inLoveOutlinedTextFieldColors
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Primary
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum
import java.util.Calendar
import java.util.Locale

@Composable
fun AddChecklistDialog(
  onDismiss: () -> Unit,
  onConfirm: (text: String) -> Unit,
  isEnglish: Boolean = false
) {
  var text by remember { mutableStateOf("") }

  AppAlertDialog(
    onDismissRequest = onDismiss,
    scrollableText = true,
    modifier = Modifier.animateDialogEntrance(),
    title = {
      Text(
        text = if (isEnglish) "Add Checklist Task" else "Thêm Việc Vào Checklist",
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = TextDarkPlum
      )
    },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(if (isEnglish) "Task" else "Tên công việc") },
        placeholder = { Text(if (isEnglish) "e.g. Buy white baby roses" else "Ví dụ: Mua bó hoa hồng baby trắng") },
        colors = inLoveOutlinedTextFieldColors(),
        shape = RoundedCornerShape(InLoveRadii.Input),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("input_checklist_task"),
        singleLine = true
      )
    },
    confirmButton = {
      PrimaryGradientButton(
        text = if (isEnglish) "Add" else "Thêm",
        onClick = { onConfirm(text) },
        modifier = Modifier.heightIn(min = MinTouchBox),
        testTag = "btn_save_checklist_task"
      )
    },
    dismissButton = {
      SecondaryButton(
        text = if (isEnglish) "Cancel" else "Hủy",
        onClick = onDismiss,
        modifier = Modifier.heightIn(min = MinTouchBox)
      )
    },
    containerColor = Color.White,
    shape = RoundedCornerShape(InLoveRadii.Card)
  )
}

@Composable
fun DeleteConfirmationDialog(
  title: String,
  message: String,
  itemName: String? = null,
  isEnglish: Boolean = false,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  AppAlertDialog(
    onDismissRequest = onDismiss,
    scrollableText = true,
    icon = {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(InLoveColors.DangerBg),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Filled.WarningAmber,
          contentDescription = null,
          tint = Color(0xFFE53935),
          modifier = Modifier.size(28.dp)
        )
      }
    },
    title = {
      Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = TextDarkPlum
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
          text = message,
          fontSize = 14.sp,
          lineHeight = 20.sp,
          color = TextSubtlePlum
        )
        if (!itemName.isNullOrBlank()) {
          Surface(
            shape = RoundedCornerShape(InLoveRadii.Input),
            color = InLoveColors.RoseTint,
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(imageVector = Icons.Filled.Favorite, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
              Text(text = itemName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDarkPlum)
            }
          }
        }
      }
    },
    confirmButton = {
      SecondaryButton(
        text = if (isEnglish) "Delete" else "Xóa Vĩnh Viễn",
        onClick = onConfirm,
        icon = Icons.Filled.DeleteForever,
        isDestructive = true,
        filled = true,
        modifier = Modifier.heightIn(min = MinTouchBox),
        testTag = "btn_confirm_delete"
      )
    },
    dismissButton = {
      SecondaryButton(
        text = if (isEnglish) "Cancel" else "Hủy bỏ",
        onClick = onDismiss,
        modifier = Modifier.heightIn(min = MinTouchBox),
        testTag = "btn_cancel_delete"
      )
    },
    containerColor = Color.White,
    shape = RoundedCornerShape(InLoveRadii.Card),
    modifier = Modifier
      .animateDialogEntrance()
      .testTag("dialog_confirm_delete")
  )
}

@Composable
fun SetAlarmReminderDialog(
  initialTitle: String = "",
  initialMessage: String = "",
  reminderId: Long? = null,
  onDismiss: () -> Unit,
  onSchedule: (title: String, message: String, triggerMillis: Long, reminderId: Long?) -> Unit,
  onTestNow: (title: String, message: String) -> Unit,
  isEnglish: Boolean = false
) {
  var title by remember {
    mutableStateOf(initialTitle.ifBlank { if (isEnglish) "Sweet love anniversary ❤️" else "Kỷ niệm tình yêu ngọt ngào ❤️" })
  }
  var message by remember {
    mutableStateOf(
      initialMessage.ifBlank {
        if (isEnglish) "A special day has arrived! Send some love to your partner 🌸"
        else "Đã đến thời gian kỷ niệm đặc biệt! Hãy gửi lời yêu thương đến người ấy nhé 🌸"
      }
    )
  }
  var selectedPreset by remember { mutableIntStateOf(1) }
  var customHour by remember { mutableIntStateOf(9) }
  var customMinute by remember { mutableIntStateOf(0) }

  fun calculateTriggerMillis(preset: Int, hour: Int, minute: Int): Long {
    val now = System.currentTimeMillis()
    return when (preset) {
      0 -> now + 10_000L
      3 -> now + 2 * 3600 * 1000L
      else -> {
        val calendar = Calendar.getInstance()
        val targetH = when (preset) {
          1 -> 9
          2 -> 19
          else -> hour
        }
        val targetM = when (preset) {
          1 -> 0
          2 -> 30
          else -> minute
        }
        calendar.set(Calendar.HOUR_OF_DAY, targetH)
        calendar.set(Calendar.MINUTE, targetM)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (calendar.timeInMillis <= now) {
          calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        calendar.timeInMillis
      }
    }
  }

  AppAlertDialog(
    onDismissRequest = onDismiss,
    scrollableText = false,
    icon = {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(InLoveColors.RoseTint),
        contentAlignment = Alignment.Center
      ) {
        Icon(imageVector = Icons.Filled.NotificationsActive, contentDescription = null, tint = Primary, modifier = Modifier.size(26.dp))
      }
    },
    title = {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = if (isEnglish) "Set Reminder Alarm" else "Cài Đặt Báo Thức Kỷ Niệm",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = TextDarkPlum
        )
        Text(
          text = if (isEnglish) "Get a chime and notification right on time" else "Nhận chuông và thông báo vào thời gian chính xác",
          fontSize = 12.sp,
          color = TextSubtlePlum
        )
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text(if (isEnglish) "Alarm title" else "Tiêu đề báo thức") },
          colors = inLoveOutlinedTextFieldColors(),
          shape = RoundedCornerShape(InLoveRadii.Input),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_alarm_title"),
          singleLine = true
        )
        OutlinedTextField(
          value = message,
          onValueChange = { message = it },
          label = { Text(if (isEnglish) "Notification message" else "Lời nhắn kèm thông báo") },
          colors = inLoveOutlinedTextFieldColors(),
          shape = RoundedCornerShape(InLoveRadii.Input),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_alarm_message"),
          maxLines = 3
        )

        Text(
          text = if (isEnglish) "Choose alarm time:" else "Chọn thời gian báo thức:",
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          color = TextDarkPlum
        )

        val presets = if (isEnglish) listOf(
          "⏰ 9:00 AM (Morning)" to 1,
          "🌙 7:30 PM (Evening)" to 2,
          "⏳ In 2 hours" to 3,
          "⚡ In 10 seconds (Test)" to 0,
          "⚙️ Custom time" to 4
        ) else listOf(
          "⏰ 9:00 (Sáng)" to 1,
          "🌙 19:30 (Tối)" to 2,
          "⏳ Sau 2 tiếng" to 3,
          "⚡ Sau 10 giây (Thử chuông)" to 0,
          "⚙️ Tùy chọn giờ cụ thể" to 4
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          presets.forEach { (label, index) ->
            val selected = selectedPreset == index
            Surface(
              shape = RoundedCornerShape(InLoveRadii.Input),
              color = if (selected) Primary.copy(alpha = 0.12f) else InLoveColors.LavenderCardBg,
              border = BorderStroke(1.dp, if (selected) Primary else InLoveColors.LavenderBorder),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchBox)
                .clip(RoundedCornerShape(InLoveRadii.Input))
                .clickable { selectedPreset = index }
                .testTag("preset_alarm_$index")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = label,
                  fontSize = 13.sp,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                  color = if (selected) Primary else TextDarkPlum
                )
                if (selected) {
                  Icon(imageVector = Icons.Filled.Favorite, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
                }
              }
            }
          }
        }

        if (selectedPreset == 4) {
          Surface(shape = RoundedCornerShape(InLoveRadii.Input), color = InLoveColors.RoseTint, modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceAround
            ) {
              AlarmTimeSpinner(
                label = if (isEnglish) "Hour" else "Giờ",
                value = customHour,
                onDecrement = { customHour = (customHour - 1 + 24) % 24 },
                onIncrement = { customHour = (customHour + 1) % 24 }
              )
              Text(":", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Primary)
              AlarmTimeSpinner(
                label = if (isEnglish) "Min" else "Phút",
                value = customMinute,
                onDecrement = { customMinute = (customMinute - 5 + 60) % 60 },
                onIncrement = { customMinute = (customMinute + 5) % 60 }
              )
            }
          }
        }

        Surface(
          shape = RoundedCornerShape(InLoveRadii.Input),
          color = InLoveColors.RoseTint,
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchBox)
            .clip(RoundedCornerShape(InLoveRadii.Input))
            .clickable { onTestNow(title, message) }
            .testTag("btn_test_alarm_instant")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Icon(imageVector = Icons.Filled.NotificationsActive, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (isEnglish) "Test the alert now" else "Thử chuông thông báo ngay",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = Primary
            )
          }
        }
      }
    },
    confirmButton = {
      PrimaryGradientButton(
        text = if (isEnglish) "Set Alarm" else "Đặt Báo Thức",
        onClick = { onSchedule(title, message, calculateTriggerMillis(selectedPreset, customHour, customMinute), reminderId) },
        icon = Icons.Filled.AlarmOn,
        modifier = Modifier.heightIn(min = MinTouchBox),
        testTag = "btn_confirm_schedule_alarm"
      )
    },
    dismissButton = {
      SecondaryButton(
        text = if (isEnglish) "Close" else "Đóng",
        onClick = onDismiss,
        modifier = Modifier.heightIn(min = MinTouchBox),
        testTag = "btn_cancel_schedule_alarm"
      )
    },
    containerColor = Color.White,
    shape = RoundedCornerShape(InLoveRadii.Card),
    modifier = Modifier
      .animateDialogEntrance()
      .testTag("dialog_set_alarm_reminder")
  )
}

@Composable
private fun AlarmTimeSpinner(
  label: String,
  value: Int,
  onDecrement: () -> Unit,
  onIncrement: () -> Unit,
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, fontSize = 11.sp, color = TextSubtlePlum)
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onDecrement, modifier = Modifier.size(MinTouchBox)) {
        Text("-", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Primary)
      }
      Text(
        text = String.format(Locale.ROOT, "%02d", value),
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = TextDarkPlum,
        modifier = Modifier.padding(horizontal = 6.dp)
      )
      IconButton(onClick = onIncrement, modifier = Modifier.size(MinTouchBox)) {
        Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Primary)
      }
    }
  }
}
