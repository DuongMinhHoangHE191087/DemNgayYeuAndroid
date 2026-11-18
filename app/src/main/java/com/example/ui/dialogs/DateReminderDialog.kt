@file:Suppress("FunctionName")
package com.example.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppAlertDialog
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.animateDialogEntrance
import com.example.ui.designsystem.InLoveRadii
import com.example.ui.designsystem.inLoveOutlinedTextFieldColors
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Primary
import com.example.ui.theme.TextDarkPlum

@Composable
fun AddReminderDialog(
  onDismiss: () -> Unit,
  onConfirm: (title: String, dateText: String, note: String) -> Unit,
  isEnglish: Boolean = false
) {
  var title by remember { mutableStateOf("") }
  var dateText by remember { mutableStateOf(if (isEnglish) "Saturday, 7:30 PM" else "Thứ Bảy, 19:30") }
  var note by remember { mutableStateOf("") }

  AppAlertDialog(
    onDismissRequest = onDismiss,
    scrollableText = true,
    modifier = Modifier.animateDialogEntrance(),
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(imageVector = Icons.Filled.Stars, contentDescription = null, tint = Primary)
        Text(
          text = if (isEnglish) "New Date Reminder" else "Tạo Lời Nhắc Hẹn",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = TextDarkPlum
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text(if (isEnglish) "Date / event name" else "Tên buổi hẹn / kỷ niệm") },
          placeholder = { Text(if (isEnglish) "e.g. Romantic dinner" else "Ví dụ: Hẹn ăn tối lãng mạn") },
          colors = inLoveOutlinedTextFieldColors(),
          shape = RoundedCornerShape(InLoveRadii.Input),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_reminder_title"),
          singleLine = true
        )
        OutlinedTextField(
          value = dateText,
          onValueChange = { dateText = it },
          label = { Text(if (isEnglish) "Time" else "Thời gian") },
          placeholder = { Text(if (isEnglish) "e.g. This weekend, 7 PM" else "Ví dụ: Cuối tuần này, 19:00") },
          colors = inLoveOutlinedTextFieldColors(),
          shape = RoundedCornerShape(InLoveRadii.Input),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_reminder_date"),
          singleLine = true
        )
        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          label = { Text(if (isEnglish) "Place / note" else "Địa điểm / Ghi chú") },
          placeholder = { Text(if (isEnglish) "e.g. Our usual cafe, bring flowers" else "Ví dụ: Quán cafe quen, nhớ mang hoa") },
          colors = inLoveOutlinedTextFieldColors(),
          shape = RoundedCornerShape(InLoveRadii.Input),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_reminder_note"),
          singleLine = true
        )
      }
    },
    confirmButton = {
      PrimaryGradientButton(
        text = if (isEnglish) "Save" else "Lưu lời nhắc",
        onClick = { onConfirm(title, dateText, note) },
        modifier = Modifier.heightIn(min = MinTouchBox),
        testTag = "btn_save_reminder"
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
