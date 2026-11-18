@file:Suppress("FunctionName")
package com.example.ui.dialogs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.components.AppAlertDialog
import com.example.ui.components.DatePickerPresets
import com.example.ui.components.DatePickerUtils
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

private const val DEFAULT_MILESTONE_IMAGE_URL =
  "https://lh3.googleusercontent.com/aida-public/AB6AXuANA2ChG6LS0d4msPLYL4g-4W2BU_q52b1udp8NDaY4NJSzyw4NZnx6e2qKT1oMKzYrc76_1-nMndDIrMSO7k1QXvz66V8WEt7D3GuZmigotLqTpeJbbAdYrKyOPyUV1W-RxHRZbCo09c24vQC-5ZIS2iG1PM6s7V5_nejLv9V0-tTQujYKsbrgGRbfxlS_JvPgXqa_zXWfABTSL3rCM-VVaw2iIyPJ43vA8jK5bjWCrVYznx4hzUS6_w"

@Stable
class MilestoneFormState(initialImageUrl: String) {
  var title by mutableStateOf("")
  var dateText by mutableStateOf(
    java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
  )
  var categoryTag by mutableStateOf("Hẹn Hò")
  var isImportant by mutableStateOf(false)
  var imageUrl by mutableStateOf(initialImageUrl)
}

/** Example titles: (Vietnamese, English, stored Vietnamese category tag). */
private val MilestoneExamples = listOf(
  Triple("Lần đầu gặp", "First met", "Kỷ Niệm"),
  Triple("Ngày tỏ tình", "Day we confessed", "Tình Yêu"),
  Triple("Chuyến đi đầu tiên", "First trip", "Du Lịch"),
  Triple("Sinh nhật người ấy", "Their birthday", "Sinh Nhật")
)

@Composable
fun AddMilestoneDialog(
  presetPhotos: List<String> = emptyList(),
  isEnglish: Boolean = false,
  onDismiss: () -> Unit,
  onConfirm: (
    title: String,
    dateText: String,
    subtitle: String,
    categoryTag: String,
    secondaryTag: String,
    imageUrl: String,
    daysRemaining: Int,
    isImportant: Boolean
  ) -> Unit
) {
  val form = remember { MilestoneFormState(presetPhotos.firstOrNull() ?: DEFAULT_MILESTONE_IMAGE_URL) }
  LaunchedEffect(presetPhotos) {
    form.imageUrl = presetPhotos.firstOrNull() ?: DEFAULT_MILESTONE_IMAGE_URL
  }

  AppAlertDialog(
    onDismissRequest = onDismiss,
    scrollableText = false,
    modifier = Modifier.animateDialogEntrance(),
    containerColor = Color.White,
    shape = MaterialTheme.shapes.extraLarge,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.CalendarMonth,
          contentDescription = null,
          tint = Primary
        )
        Text(
          text = if (isEnglish) "New milestone" else "Kỷ niệm mới",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = TextDarkPlum
        )
      }
    },
    text = { AddMilestoneFormBody(form, presetPhotos, isEnglish) },
    confirmButton = {
      PrimaryGradientButton(
        text = if (isEnglish) "Create" else "Tạo",
        onClick = {
          val days = DatePickerUtils.daysUntil(form.dateText) ?: 30
          val subtitle = if (isEnglish) {
            DatePickerUtils.getDayOfWeek(form.dateText, true) ?: "Milestone"
          } else {
            DatePickerUtils.getVietnameseDayOfWeek(form.dateText) ?: "Kỷ Niệm"
          }
          onConfirm(
            form.title,
            form.dateText,
            subtitle,
            form.categoryTag,
            if (isEnglish) "New milestone" else "Cột mốc mới",
            form.imageUrl,
            days,
            form.isImportant
          )
        },
        modifier = Modifier.heightIn(min = MinTouchBox),
        testTag = "btn_submit_milestone"
      )
    },
    dismissButton = {
      SecondaryButton(
        text = if (isEnglish) "Cancel" else "Hủy",
        onClick = onDismiss,
        modifier = Modifier.heightIn(min = MinTouchBox)
      )
    }
  )
}

@Composable
fun AddMilestoneFormBody(
  form: MilestoneFormState,
  presetPhotos: List<String>,
  isEnglish: Boolean
) {
  Column(
    modifier = Modifier.verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    OutlinedTextField(
      value = form.title,
      onValueChange = { form.title = it },
      label = { Text(if (isEnglish) "Title" else "Tiêu đề") },
      modifier = Modifier
        .fillMaxWidth()
        .testTag("input_milestone_title"),
      singleLine = true,
      shape = RoundedCornerShape(InLoveRadii.Input),
      colors = inLoveOutlinedTextFieldColors()
    )

    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      items(MilestoneExamples) { (vi, en, tag) ->
        SuggestionChip(
          onClick = {
            form.title = if (isEnglish) en else vi
            form.categoryTag = tag
          },
          label = { Text(if (isEnglish) en else vi, fontSize = 12.sp, color = TextDarkPlum) },
          modifier = Modifier.heightIn(min = MinTouchBox)
        )
      }
    }

    InLoveDatePickerField(
      value = form.dateText,
      onValueChange = { form.dateText = it },
      label = if (isEnglish) "Date" else "Ngày",
      placeholder = "15/11/2026",
      dialogTitle = if (isEnglish) "Pick a date" else "Chọn ngày",
      quickPresets = DatePickerPresets.upcomingAnniversaryPresets(isEnglish),
      isEnglish = isEnglish,
      modifier = Modifier.fillMaxWidth(),
      testTag = "input_milestone_date"
    )

    val categoryOptions = listOf(
      Triple("Hẹn Hò", "Date", Icons.Filled.LocalCafe),
      Triple("Du Lịch", "Travel", Icons.Filled.Flight),
      Triple("Sinh Nhật", "Birthday", Icons.Filled.Cake),
      Triple("Tình Yêu", "Love", Icons.Filled.Favorite),
      Triple("Kỷ Niệm", "Memory", Icons.Filled.Star)
    )
    Row(modifier = Modifier.fillMaxWidth()) {
      categoryOptions.forEach { (name, nameEn, icon) ->
        val isSelected = form.categoryTag == name
        val bg by animateColorAsState(
          if (isSelected) Primary else SurfaceContainerHigh,
          label = "cat_bg"
        )
        val scale by animateFloatAsState(
          if (isSelected) 1.12f else 1f,
          spring(Spring.DampingRatioMediumBouncy),
          label = "cat_scale"
        )
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .weight(1f)
            .heightIn(min = MinTouchBox)
            .clip(MaterialTheme.shapes.small)
            .pressClickable(onClick = { form.categoryTag = name })
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
              imageVector = icon,
              contentDescription = if (isEnglish) nameEn else name,
              tint = if (isSelected) Color.White else TextSubtlePlum,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }

    if (presetPhotos.isNotEmpty()) {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(presetPhotos) { url ->
          Box(
            modifier = Modifier
              .size(MinTouchBox)
              .clip(RoundedCornerShape(8.dp))
              .border(
                width = if (form.imageUrl == url) 2.dp else 1.dp,
                color = if (form.imageUrl == url) Primary else InLoveColors.DangerBorder,
                shape = RoundedCornerShape(8.dp)
              )
              .clickable { form.imageUrl = url }
          ) {
            AsyncImage(
              model = url,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          }
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          Icons.Filled.Star,
          contentDescription = null,
          tint = if (form.isImportant) Primary else TextSubtlePlum
        )
        Text(
          text = if (isEnglish) "Important" else "Quan trọng",
          fontSize = 14.sp,
          color = TextSubtlePlum
        )
      }
      Switch(
        checked = form.isImportant,
        onCheckedChange = { form.isImportant = it },
        colors = SwitchDefaults.colors(checkedTrackColor = Primary)
      )
    }
  }
}
