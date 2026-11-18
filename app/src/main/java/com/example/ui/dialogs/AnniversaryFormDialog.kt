@file:Suppress("FunctionName")
package com.example.ui.dialogs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppAlertDialog
import com.example.ui.components.DatePickerPresets
import com.example.ui.components.InLoveDatePickerField
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.animateDialogEntrance
import com.example.ui.components.pressClickable
import com.example.ui.designsystem.InLoveColors
import com.example.ui.designsystem.InLoveRadii
import com.example.ui.designsystem.inLoveOutlinedTextFieldColors
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Primary
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum

/** Days before the date at which the reminder can fire. */
val REMINDER_DAYS_BEFORE_OPTIONS = listOf(0, 1, 3, 7, 14)

@Composable
fun EditLoveTitleDialog(
  currentTitle: String,
  isEnglish: Boolean = false,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit
) {
  var titleInput by remember { mutableStateOf(currentTitle) }

  val suggestions = if (isEnglish) {
    listOf(
      "Our Love Journey",
      "Forever In Love",
      "Together Forever",
      "Our Story",
      "Sweet Moments"
    )
  } else {
    listOf(
      "Hành Trình Yêu Thương",
      "Bên Nhau Trọn Đời",
      "Nhật Ký Tình Yêu",
      "Khoảnh Khắc Ngọt Ngào",
      "Mãi Mãi Bên Nhau"
    )
  }

  AppAlertDialog(
    onDismissRequest = onDismiss,
    scrollableText = true,
    modifier = Modifier.animateDialogEntrance(),
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Filled.Favorite,
          contentDescription = null,
          tint = InLoveColors.RoseBright,
          modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (isEnglish) "Customize Love Title" else "Đổi Tên Hành Trình Yêu",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = TextDarkPlum
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text = if (isEnglish) "Enter your custom title or choose a suggestion below:" else "Nhập tiêu đề bạn muốn hoặc chọn gợi ý bên dưới:",
          fontSize = 13.sp,
          color = TextSubtlePlum
        )

        OutlinedTextField(
          value = titleInput,
          onValueChange = { titleInput = it },
          label = { Text(if (isEnglish) "Journey Title" else "Tiêu đề hành trình") },
          singleLine = true,
          shape = RoundedCornerShape(InLoveRadii.Input),
          colors = inLoveOutlinedTextFieldColors(),
          modifier = Modifier.fillMaxWidth()
        )

        Text(
          text = if (isEnglish) "Quick suggestions:" else "Gợi ý nhanh:",
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          color = TextDarkPlum
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          suggestions.forEach { suggestion ->
            val isSelected = titleInput.trim() == suggestion
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = if (isSelected) Color(0xFFFFE4EC) else Color(0xFFF9F5F8),
              border = BorderStroke(
                1.dp,
                if (isSelected) InLoveColors.RoseBright else InLoveColors.LavenderBorder
              ),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchBox)
                .clickable { titleInput = suggestion }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = suggestion,
                  fontSize = 12.5.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) InLoveColors.RoseHighlight else TextDarkPlum
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      PrimaryGradientButton(
        text = if (isEnglish) "Save" else "Lưu Lại",
        onClick = {
          val clean = titleInput.trim().ifEmpty {
            if (isEnglish) "Our Love Journey" else "Hành Trình Yêu Thương"
          }
          onConfirm(clean)
        },
        modifier = Modifier.heightIn(min = MinTouchBox)
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
fun AddAnniversaryDateDialog(
  isEnglish: Boolean = false,
  initialDateText: String? = null,
  onDismiss: () -> Unit,
  onConfirm: (
    title: String,
    dateText: String,
    type: String,
    description: String,
    isAnnual: Boolean,
    notificationEnabled: Boolean,
    reminderDaysBefore: Int
  ) -> Unit
) {
  var title by remember { mutableStateOf("") }
  val todayFormatted = remember {
    java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ROOT).format(java.util.Date())
  }
  var dateText by remember(initialDateText) { mutableStateOf(initialDateText ?: todayFormatted) }
  var selectedType by remember { mutableStateOf("LOVE") }
  var description by remember { mutableStateOf("") }
  var isAnnual by remember { mutableStateOf(true) }
  var notificationEnabled by remember { mutableStateOf(true) }
  var reminderDaysBefore by remember { mutableIntStateOf(3) }

  val typeOptions = if (isEnglish) {
    listOf(
      "LOVE" to "Love Start Day",
      "FIRST_DATE" to "First Date",
      "FIRST_KISS" to "First Kiss",
      "PROPOSAL" to "Proposal",
      "WEDDING" to "Wedding Day",
      "CUSTOM" to "Special Date"
    )
  } else {
    listOf(
      "LOVE" to "Ngày Bắt Đầu Yêu",
      "FIRST_DATE" to "Hẹn Hò Đầu Tiên",
      "FIRST_KISS" to "Nụ Hôn Đầu Tiên",
      "PROPOSAL" to "Cầu Hôn",
      "WEDDING" to "Đám Cưới",
      "CUSTOM" to "Kỷ Niệm Riêng"
    )
  }

  val typeIcons = mapOf(
    "LOVE" to Icons.Filled.Favorite,
    "FIRST_DATE" to Icons.Filled.LocalCafe,
    "FIRST_KISS" to Icons.Filled.Mood,
    "PROPOSAL" to Icons.Filled.Diamond,
    "WEDDING" to Icons.Filled.Celebration,
    "CUSTOM" to Icons.Filled.Star
  )

  AppAlertDialog(
    onDismissRequest = onDismiss,
    scrollableText = false,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(imageVector = Icons.Filled.Favorite, contentDescription = null, tint = Primary)
        Text(
          text = if (isEnglish) "New anniversary" else "Ngày kỷ niệm",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = TextDarkPlum
        )
      }
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text(if (isEnglish) "Title" else "Tiêu đề") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_anniversary_title"),
          singleLine = true,
          shape = RoundedCornerShape(InLoveRadii.Input),
          colors = inLoveOutlinedTextFieldColors()
        )

        InLoveDatePickerField(
          value = dateText,
          onValueChange = { dateText = it },
          label = if (isEnglish) "Date" else "Ngày",
          placeholder = "dd/MM/yyyy",
          dialogTitle = if (isEnglish) "Select Date" else "Chọn ngày",
          quickPresets = DatePickerPresets.upcomingAnniversaryPresets(isEnglish),
          isEnglish = isEnglish,
          modifier = Modifier.fillMaxWidth(),
          testTag = "input_anniversary_date"
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          typeOptions.forEach { (typeKey, label) ->
            val isSelected = selectedType == typeKey
            val bg by animateColorAsState(
              if (isSelected) Primary else SurfaceContainerHigh,
              label = "type_bg"
            )
            val scale by animateFloatAsState(
              if (isSelected) 1.12f else 1f,
              spring(Spring.DampingRatioMediumBouncy),
              label = "type_scale"
            )
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .weight(1f)
                .heightIn(min = MinTouchBox)
                .clip(RoundedCornerShape(10.dp))
                .pressClickable(onClick = { selectedType = typeKey })
            ) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .size(40.dp)
                  .graphicsLayer { scaleX = scale; scaleY = scale }
                  .clip(CircleShape)
                  .background(bg)
              ) {
                Icon(
                  imageVector = typeIcons.getValue(typeKey),
                  contentDescription = label,
                  tint = if (isSelected) Color.White else TextSubtlePlum,
                  modifier = Modifier.size(20.dp)
                )
              }
            }
          }
        }
        Text(
          text = typeOptions.firstOrNull { it.first == selectedType }?.second.orEmpty(),
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = Primary,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text(if (isEnglish) "Note" else "Ghi chú") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_anniversary_desc"),
          maxLines = 3,
          shape = RoundedCornerShape(InLoveRadii.Input),
          colors = inLoveOutlinedTextFieldColors()
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            Icons.Filled.Repeat,
            contentDescription = if (isEnglish) "Repeat every year" else "Lặp lại hàng năm",
            tint = if (isAnnual) Primary else TextSubtlePlum
          )
          Switch(
            checked = isAnnual,
            onCheckedChange = { isAnnual = it },
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Primary)
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            Icons.Filled.Notifications,
            contentDescription = if (isEnglish) "Notify me" else "Nhắc tôi",
            tint = if (notificationEnabled) Primary else TextSubtlePlum
          )
          Switch(
            checked = notificationEnabled,
            onCheckedChange = { notificationEnabled = it },
            modifier = Modifier.testTag("switch_anniversary_notify"),
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Primary)
          )
        }

        if (notificationEnabled) {
          val reminderOptions = REMINDER_DAYS_BEFORE_OPTIONS.map { days ->
            val label = if (isEnglish) {
              when (days) {
                0 -> "Same Day"
                1 -> "1 Day"
                else -> "$days Days"
              }
            } else {
              when (days) {
                0 -> "Đúng ngày"
                else -> "$days ngày"
              }
            }
            days to label
          }
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            reminderOptions.forEach { (days, label) ->
              val isSelected = reminderDaysBefore == days
              Surface(
                shape = RoundedCornerShape(InLoveRadii.Badge),
                color = if (isSelected) Primary else SurfaceContainerHigh,
                modifier = Modifier
                  .heightIn(min = MinTouchBox)
                  .clip(RoundedCornerShape(InLoveRadii.Badge))
                  .clickable { reminderDaysBefore = days }
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else TextDarkPlum,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      PrimaryGradientButton(
        text = if (isEnglish) "Save" else "Lưu",
        onClick = {
          onConfirm(title, dateText, selectedType, description, isAnnual, notificationEnabled, reminderDaysBefore)
        },
        modifier = Modifier.heightIn(min = MinTouchBox),
        testTag = "btn_save_anniversary_date"
      )
    },
    dismissButton = {
      SecondaryButton(
        text = if (isEnglish) "Cancel" else "Hủy",
        onClick = onDismiss,
        modifier = Modifier.heightIn(min = MinTouchBox),
        testTag = "btn_cancel_anniversary_date"
      )
    },
    containerColor = Color.White,
    shape = RoundedCornerShape(InLoveRadii.Card),
    modifier = Modifier
      .animateDialogEntrance()
      .testTag("dialog_add_anniversary_date")
  )
}
