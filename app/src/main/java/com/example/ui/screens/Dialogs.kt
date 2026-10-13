@file:Suppress("FunctionName")
package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import com.example.ui.components.pressClickable
import com.example.ui.theme.AppDensity
import com.example.ui.theme.MinTouchBox
import androidx.compose.material.icons.filled.Search
import kotlinx.coroutines.launch
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.data.model.OnlineInviteEntity
import com.example.data.model.OnlineStatus
import com.example.data.model.OnlineUserEntity
import com.example.data.repository.OnlineCoupleRepository
import com.example.ui.viewmodel.InLoveViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.GiftIdeaEntity
import com.example.domain.content.GiftCatalog
import com.example.ui.theme.OnPrimaryFixed
import com.example.ui.theme.OnSurface
import com.example.ui.theme.OnSurfaceVariant
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryFixed
import com.example.ui.theme.RoseGradientEnd
import com.example.ui.theme.RoseGradientMid
import com.example.ui.theme.RoseGradientStart
import com.example.ui.theme.Secondary
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.theme.Tertiary
import com.example.ui.components.DatePickerPresets
import com.example.ui.components.DatePickerUtils
import com.example.ui.components.InLoveDatePickerDialog
import com.example.ui.components.InLoveDatePickerField
import com.example.ui.components.animateDialogEntrance
import com.example.ui.util.ProfileUtils

@Composable
fun dialogTextFieldColors() = OutlinedTextFieldDefaults.colors(
  focusedTextColor = Color(0xFF1E1E24),
  unfocusedTextColor = Color(0xFF1E1E24),
  focusedContainerColor = Color.White,
  unfocusedContainerColor = Color.White,
  disabledContainerColor = Color(0xFFF5F5F7),
  focusedBorderColor = Primary,
  unfocusedBorderColor = Color(0xFFC7C7CC),
  focusedLabelColor = Primary,
  unfocusedLabelColor = Color(0xFF424242),
  focusedPlaceholderColor = Color(0xFF757575),
  unfocusedPlaceholderColor = Color(0xFF9E9E9E),
  cursorColor = Primary
)

@Composable
fun AddReminderDialog(
  onDismiss: () -> Unit,
  onConfirm: (title: String, dateText: String, note: String) -> Unit,
  isEnglish: Boolean = false
) {
  var title by remember { mutableStateOf("") }
  var dateText by remember { mutableStateOf(if (isEnglish) "Saturday, 7:30 PM" else "Thứ Bảy, 19:30") }
  var note by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.animateDialogEntrance(),
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.Stars,
          contentDescription = null,
          tint = Primary
        )
        Text(
          text = if (isEnglish) "New Date Reminder" else "Tạo Lời Nhắc Hẹn",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp
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
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_reminder_note"),
          singleLine = true
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(title, dateText, note) },
        colors = ButtonDefaults.buttonColors(containerColor = Primary),
        modifier = Modifier.testTag("btn_save_reminder")
      ) {
        Text(if (isEnglish) "Save" else "Lưu lời nhắc", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(if (isEnglish) "Cancel" else "Hủy", color = OnSurfaceVariant)
      }
    },
    containerColor = Color.White,
    shape = RoundedCornerShape(24.dp)
  )
}

private const val DEFAULT_MILESTONE_IMAGE_URL =
  "https://lh3.googleusercontent.com/aida-public/AB6AXuANA2ChG6LS0d4msPLYL4g-4W2BU_q52b1udp8NDaY4NJSzyw4NZnx6e2qKT1oMKzYrc76_1-nMndDIrMSO7k1QXvz66V8WEt7D3GuZmigotLqTpeJbbAdYrKyOPyUV1W-RxHRZbCo09c24vQC-5ZIS2iG1PM6s7V5_nejLv9V0-tTQujYKsbrgGRbfxlS_JvPgXqa_zXWfABTSL3rCM-VVaw2iIyPJ43vA8jK5bjWCrVYznx4hzUS6_w"

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

  // The dialog window resets LocalDensity, so every slot re-applies the app's 0.85 scale.
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.animateDialogEntrance(),
    containerColor = Color.White,
    shape = MaterialTheme.shapes.extraLarge,
    title = {
      AppDensity {
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
            fontSize = 18.sp
          )
        }
      }
    },
    text = { AppDensity { AddMilestoneFormBody(form, presetPhotos, isEnglish) } },
    confirmButton = {
      AppDensity {
        Button(
          onClick = {
            val days = DatePickerUtils.daysUntil(form.dateText) ?: 30
            // Derived from the actual picked date, not a hardcoded "Chủ Nhật" that used to be
            // wrong for every date except an actual Sunday.
            val subtitle = if (isEnglish) (DatePickerUtils.getDayOfWeek(form.dateText, true) ?: "Milestone") else (DatePickerUtils.getVietnameseDayOfWeek(form.dateText) ?: "Kỷ Niệm")
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
          colors = ButtonDefaults.buttonColors(containerColor = Primary),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.testTag("btn_submit_milestone")
        ) {
          Text(if (isEnglish) "Create" else "Tạo", fontWeight = FontWeight.Bold)
        }
      }
    },
    dismissButton = {
      AppDensity {
        TextButton(onClick = onDismiss) {
          Text(if (isEnglish) "Cancel" else "Hủy", color = OnSurfaceVariant)
        }
      }
    }
  )
}

@Stable
internal class MilestoneFormState(initialImageUrl: String) {
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

// Body is its own composable so it can be tested without a Dialog window (Robolectric never
// goes idle with a text field inside one).
@Composable
internal fun AddMilestoneFormBody(
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
      modifier = Modifier.fillMaxWidth().testTag("input_milestone_title"),
      singleLine = true,
      shape = RoundedCornerShape(12.dp),
      colors = inLoveFieldColors()
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
          label = { Text(if (isEnglish) en else vi, fontSize = 12.sp) }
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

    // Icon-only category picker. The Vietnamese name is the stored tag (matches seed data);
    // only the contentDescription is localized.
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
          if (isSelected) Primary else SurfaceContainerHigh, label = "cat_bg"
        )
        val scale by animateFloatAsState(
          if (isSelected) 1.12f else 1f,
          spring(Spring.DampingRatioMediumBouncy),
          label = "cat_scale"
        )
        // Weighted cells keep the row inside the dialog width; each is at least MinTouchBox tall.
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
              tint = if (isSelected) Color.White else OnSurfaceVariant,
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
              .size(44.dp)
              .clip(RoundedCornerShape(8.dp))
              .border(
                width = if (form.imageUrl == url) 2.dp else 1.dp,
                color = if (form.imageUrl == url) Primary else Color(0xFFFFCDD2),
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
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = if (form.isImportant) Primary else OnSurfaceVariant)
        Text(
          text = if (isEnglish) "Important" else "Quan trọng",
          fontSize = 14.sp,
          color = OnSurfaceVariant
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

@Composable
fun AddChecklistDialog(
  onDismiss: () -> Unit,
  onConfirm: (text: String) -> Unit,
  isEnglish: Boolean = false
) {
  var text by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.animateDialogEntrance(),
    title = {
      Text(
        text = if (isEnglish) "Add Checklist Task" else "Thêm Việc Vào Checklist",
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
      )
    },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(if (isEnglish) "Task" else "Tên công việc") },
        placeholder = { Text(if (isEnglish) "e.g. Buy white baby roses" else "Ví dụ: Mua bó hoa hồng baby trắng") },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("input_checklist_task"),
        singleLine = true
      )
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(text) },
        colors = ButtonDefaults.buttonColors(containerColor = Primary),
        modifier = Modifier.testTag("btn_save_checklist_task")
      ) {
        Text(if (isEnglish) "Add" else "Thêm", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(if (isEnglish) "Cancel" else "Hủy", color = OnSurfaceVariant)
      }
    },
    containerColor = Color.White,
    shape = RoundedCornerShape(24.dp)
  )
}

@Composable
fun GiftDetailDialog(
  gift: GiftIdeaEntity,
  onDismiss: () -> Unit,
  onToggleFavorite: () -> Unit,
  isEnglish: Boolean = false
) {
  val localized = GiftCatalog.localized(gift, isEnglish)
  Dialog(onDismissRequest = onDismiss) {
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
      modifier = Modifier
        .widthIn(max = 380.dp)
        .fillMaxWidth(0.92f)
        .animateDialogEntrance()
        .padding(vertical = 16.dp)
    ) {
      Column(
        modifier = Modifier
          .verticalScroll(rememberScrollState())
          .padding(18.dp)
      ) {
        // Image with close button
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(18.dp))
        ) {
          AsyncImage(
            model = gift.imageUrl,
            contentDescription = localized.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )

          if (localized.isSeed) {
            IllustrationCaption(
              isEnglish = isEnglish,
              modifier = Modifier
                .padding(10.dp)
                .align(Alignment.BottomStart)
            )
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(4.dp)
              .size(48.dp)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.5f))
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = "Close",
              tint = Color.White,
              modifier = Modifier.size(18.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
          shape = RoundedCornerShape(12.dp),
          color = PrimaryFixed.copy(alpha = 0.7f)
        ) {
          Text(
            text = GiftCatalog.categoryLabel(localized.categoryKey, isEnglish),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = localized.title,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = OnSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = localized.description,
          fontSize = 13.sp,
          color = OnSurfaceVariant,
          lineHeight = 18.sp
        )

        GiftCatalog.priceLabel(gift.priceRange, isEnglish)?.let { price ->
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = price,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Primary
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
          shape = RoundedCornerShape(14.dp),
          color = SurfaceContainerLowest,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              text = if (isEnglish) "DETAILS" else "CHI TIẾT THỰC HIỆN",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = Primary,
              letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = localized.detailsSnippet.ifBlank {
                if (isEnglish) "A thoughtful gift that marks the moments you share together." else "Món quà tuyệt vời lưu lại khoảnh khắc gắn kết của hai bạn."
              },
              fontSize = 12.sp,
              color = OnSurface
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Surface(
            shape = RoundedCornerShape(50.dp),
            color = PrimaryFixed.copy(alpha = 0.5f),
            modifier = Modifier
              .weight(1f)
              .clickable { onToggleFavorite() }
          ) {
            Row(
              modifier = Modifier.padding(vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = when {
                  gift.isFavorited && isEnglish -> "Saved"
                  gift.isFavorited -> "Đã lưu"
                  isEnglish -> "Favorite"
                  else -> "Yêu thích"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Primary
              )
            }
          }

          Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            modifier = Modifier.weight(1f)
          ) {
            Text(if (isEnglish) "Got it" else "Đã rõ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
          }
        }
      }
    }
  }
}

@Composable
fun VipProposalDialog(
  onDismiss: () -> Unit,
  isEnglish: Boolean = false
) {
  Dialog(onDismissRequest = onDismiss) {
    Card(
      shape = RoundedCornerShape(26.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
      modifier = Modifier
        .widthIn(max = 380.dp)
        .fillMaxWidth(0.92f)
        .animateDialogEntrance()
        .padding(vertical = 16.dp)
    ) {
      Column(
        modifier = Modifier
          .verticalScroll(rememberScrollState())
          .padding(20.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = PrimaryFixed
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(14.dp)
              )
              Text(
                text = if (isEnglish) "PROPOSAL IDEAS" else "GỢI Ý ĐỀ XUẤT",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Primary
              )
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .background(SurfaceContainerHigh)
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = "Close",
              tint = OnSurfaceVariant,
              modifier = Modifier.size(18.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = if (isEnglish) "1,000-Day Celebration Ideas" else "Ý Tưởng Kỷ Niệm 1.000 Ngày",
          fontSize = 19.sp,
          fontWeight = FontWeight.Bold,
          color = OnSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = if (isEnglish) "A few itinerary ideas for an anniversary evening. Pick what suits you both." else "Vài gợi ý lịch trình cho một buổi tối kỷ niệm. Chọn những gì hợp với hai bạn.",
          fontSize = 13.sp,
          color = OnSurfaceVariant,
          lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Itinerary Timeline Steps
        val steps = if (isEnglish) listOf(
          Triple("18:30", "Meet up & a bouquet", "You could bring flowers with a handwritten note."),
          Triple("19:00", "A romantic dinner", "Pick a quiet place with a nice view to talk."),
          Triple("20:30", "Cake & candles", "Play your song and look back at your photos together."),
          Triple("21:30", "A walk & a small gift", "Give a small gift tied to the day you met.")
        ) else listOf(
          Triple("18:30", "Gặp nhau & một bó hoa", "Bạn có thể mang theo hoa kèm một lời nhắn tự viết tay."),
          Triple("19:00", "Bữa tối lãng mạn", "Chọn một quán yên tĩnh, có view đẹp để trò chuyện."),
          Triple("20:30", "Bánh kem & thổi nến", "Bật lại bài hát của hai bạn và cùng xem lại ảnh kỷ niệm."),
          Triple("21:30", "Dạo phố & một món quà nhỏ", "Tặng một món quà nhỏ gắn với ngày đầu hai bạn gặp nhau.")
        )

        steps.forEachIndexed { index, (time, title, desc) ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Surface(
                shape = CircleShape,
                color = Primary,
                modifier = Modifier.size(24.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = "${index + 1}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
              if (index < steps.size - 1) {
                Box(
                  modifier = Modifier
                    .width(2.dp)
                    .height(44.dp)
                    .background(PrimaryFixed)
                )
              }
            }

            Column(modifier = Modifier.padding(bottom = 12.dp)) {
              Text(
                text = "$time • $title",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Primary
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = desc,
                fontSize = 12.sp,
                color = OnSurfaceVariant,
                lineHeight = 16.sp
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
          onClick = onDismiss,
          shape = RoundedCornerShape(50.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Primary),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(if (isEnglish) "Got it" else "Đã rõ", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
fun EditCoupleDialog(
  viewModel: InLoveViewModel,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  // State from ViewModel
  val currentUser by viewModel.currentOnlineUser.collectAsState()
  val partnerUser by viewModel.partnerOnlineUser.collectAsState()
  val relationshipStatus by viewModel.relationshipStatus.collectAsState()
  val incomingInvite by viewModel.incomingInvite.collectAsState()
  val outgoingInvite by viewModel.outgoingInvite.collectAsState()
  val loveDays by viewModel.loveDays.collectAsState()
  val anniversaryDate by viewModel.anniversaryDate.collectAsState()

  val boyName by viewModel.boyName.collectAsState()
  val boyBirthDate by viewModel.boyBirthDate.collectAsState()
  val boyAvatarUrl by viewModel.boyAvatarUrl.collectAsState()
  val girlName by viewModel.girlName.collectAsState()
  val girlBirthDate by viewModel.girlBirthDate.collectAsState()
  val girlAvatarUrl by viewModel.girlAvatarUrl.collectAsState()
  val loveTitle by viewModel.loveTitle.collectAsState()

  val todayFormatted = remember { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date()) }

  val appLanguage by viewModel.appLanguage.collectAsState()
  val isEnglish = appLanguage == com.example.ui.util.AppLanguage.EN

  // Partner & Couple editable states
  var currentGirlName by remember(girlName) { mutableStateOf(girlName.ifEmpty { "Người thương" }) }
  var currentGirlBirth by remember(girlBirthDate) { mutableStateOf(girlBirthDate.ifEmpty { "20/11/2004" }) }
  var currentGirlAvatar by remember(girlAvatarUrl) { mutableStateOf(girlAvatarUrl) }

  var currentLoveTitle by remember(loveTitle) { mutableStateOf(loveTitle.ifEmpty { "Hành Trình Yêu Thương" }) }
  var currentAnniversary by remember(anniversaryDate) { mutableStateOf(anniversaryDate.ifEmpty { todayFormatted }) }

  val boyAge by remember(boyBirthDate) { derivedStateOf { ProfileUtils.calculateAge(boyBirthDate) } }
  val boyZodiac by remember(boyBirthDate) { derivedStateOf { ProfileUtils.calculateZodiac(boyBirthDate).first } }

  val girlAge by remember(currentGirlBirth) { derivedStateOf { ProfileUtils.calculateAge(currentGirlBirth) } }
  val girlZodiac by remember(currentGirlBirth) { derivedStateOf { ProfileUtils.calculateZodiac(currentGirlBirth).first } }

  val calculatedOfflineDays by remember(currentAnniversary) {
    derivedStateOf { ProfileUtils.calculateLoveDays(currentAnniversary) }
  }

  // Image picker & camera launcher for partner avatar
  val girlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    uri?.let {
      currentGirlAvatar = viewModel.saveLocalAvatar(it, isPartner = true)
    }
  }
  val partnerCaptureFile = remember { File(context.cacheDir, "camera/partner_avatar_capture.jpg") }
  val girlCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
    if (saved) {
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", partnerCaptureFile)
      currentGirlAvatar = viewModel.saveLocalAvatar(uri, isPartner = true)
    }
  }

  // Online search & invite states
  var searchQuery by remember { mutableStateOf("") }
  var isSearching by remember { mutableStateOf(false) }
  var searchError by remember { mutableStateOf<String?>(null) }
  var selectedPartner by remember { mutableStateOf<OnlineUserEntity?>(null) }
  var proposedStartDateText by remember { mutableStateOf(anniversaryDate.ifEmpty { todayFormatted }) }
  var loveNote by remember { mutableStateOf("") }
  val calculatedOnlineDays by remember(proposedStartDateText) {
    derivedStateOf { ProfileUtils.calculateLoveDays(proposedStartDateText) }
  }

  var activeTab by remember { mutableIntStateOf(0) } // 0: Offline, 1: Online

  fun copyToClipboard(text: String, label: String = "Couple Code") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    @Suppress("UsePropertyAccessSyntax")
    clipboard.setPrimaryClip(clip)
    viewModel.showToast("Đã sao chép: $text")
  }

  fun shareCoupleLink(code: String) {
    val shareLink = ProfileUtils.createShareLink(code)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(
        Intent.EXTRA_TEXT,
        "Cùng kết nối Set Love 1-1 với mình trên InLove nhé! Bấm vào link hoặc nhập mã:\n$shareLink\nMã ghép đôi: $code ❤️"
      )
    }
    context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ mã ghép đôi qua"))
  }

  val infiniteHeart = rememberInfiniteTransition(label = "couple_header_heart")
  val headerHeartScale by infiniteHeart.animateFloat(
    initialValue = 1f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
      animation = tween(850, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "header_heart_scale"
  )

  var showPartnerPhotoActionPicker by remember { mutableStateOf(false) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
      border = BorderStroke(1.dp, Color(0xFFFFDDE6)),
      modifier = Modifier
        .widthIn(max = 380.dp)
        .fillMaxWidth(0.92f)
        .animateDialogEntrance()
        .padding(vertical = 12.dp)
        .testTag("edit_couple_dialog")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // 1. HEADER
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
                .size(38.dp)
                .graphicsLayer {
                  scaleX = headerHeartScale
                  scaleY = headerHeartScale
                }
                .background(
                  brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color(0xFFFF4081), Color(0xFFD81B60))
                  ),
                  shape = CircleShape
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(19.dp)
              )
            }
            Text(
              text = if (isEnglish) "Couple" else "Cặp đôi",
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF880E4F)
            )
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(40.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = if (isEnglish) "Close" else "Đóng",
              tint = Color.Gray
            )
          }
        }

        // 2. TAB SELECTOR: Hồ sơ vs Ghép đôi
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = Color(0xFFFFF0F5),
          modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
        ) {
          Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            val tab0Bg by animateColorAsState(if (activeTab == 0) Color.White else Color.Transparent, label = "tab0_bg")
            val tab0Color by animateColorAsState(if (activeTab == 0) Color(0xFFE91E63) else Color(0xFF888888), label = "tab0_color")
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(tab0Bg)
                .clickable { activeTab = 0 }
                .padding(vertical = 9.dp),
              contentAlignment = Alignment.Center
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Favorite,
                  contentDescription = null,
                  tint = tab0Color,
                  modifier = Modifier.size(15.dp)
                )
                Text(
                  text = if (isEnglish) "Profile" else "Hồ sơ",
                  fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium,
                  fontSize = 13.sp,
                  color = tab0Color
                )
              }
            }

            val tab1Bg by animateColorAsState(if (activeTab == 1) Color.White else Color.Transparent, label = "tab1_bg")
            val tab1Color by animateColorAsState(if (activeTab == 1) Color(0xFFE91E63) else Color(0xFF888888), label = "tab1_color")
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(tab1Bg)
                .clickable { activeTab = 1 }
                .padding(vertical = 9.dp),
              contentAlignment = Alignment.Center
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.FavoriteBorder,
                  contentDescription = null,
                  tint = tab1Color,
                  modifier = Modifier.size(15.dp)
                )
                Text(
                  text = if (isEnglish) "Pairing" else "Ghép đôi",
                  fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium,
                  fontSize = 13.sp,
                  color = tab1Color
                )
              }
            }
          }
        }

        // 3. TAB CONTENT
        AnimatedContent(
          targetState = activeTab,
          transitionSpec = {
            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(150))
          },
          label = "couple_tabs_content"
        ) { currentTab ->
          if (currentTab == 0) {
            // --- TAB 0: HỒ SƠ & NGÀY YÊU ---
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              // THẺ ĐỐI TÁC (Người ấy)
              Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7F9)),
                border = BorderStroke(1.dp, Color(0xFFFFC0D3)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFE91E63), modifier = Modifier.size(16.dp))
                    Text(
                      text = if (isEnglish) "Partner" else "Người ấy",
                      fontWeight = FontWeight.Bold,
                      fontSize = 13.5.sp,
                      color = Color(0xFF880E4F)
                    )
                  }
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                      modifier = Modifier.size(68.dp),
                      contentAlignment = Alignment.Center
                    ) {
                      AsyncImage(
                        model = currentGirlAvatar.ifEmpty { "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=200" },
                        contentDescription = "Avatar Đối tác",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                          .size(62.dp)
                          .clip(CircleShape)
                          .border(2.dp, Color(0xFFE91E63), CircleShape)
                          .clickable { showPartnerPhotoActionPicker = !showPartnerPhotoActionPicker }
                      )
                      Surface(
                        shape = CircleShape,
                        color = Color(0xFFE91E63),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                          .size(24.dp)
                          .align(Alignment.BottomEnd)
                          .clickable { showPartnerPhotoActionPicker = !showPartnerPhotoActionPicker }
                          .testTag("btn_partner_pencil")
                      ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                          Icon(Icons.Default.Edit, contentDescription = "Đổi ảnh", tint = Color.White, modifier = Modifier.size(13.dp))
                        }
                      }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                      OutlinedTextField(
                        value = currentGirlName,
                        onValueChange = { currentGirlName = it },
                        label = { Text(if (isEnglish) "Name" else "Tên") },
                        placeholder = { Text(if (isEnglish) "Partner name" else "Tên người ấy") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = dialogTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("input_girl_name")
                      )
                    }
                  }

                  // Thao tác ảnh người ấy: Chỉ hiện khi bấm nút bút chì
                  AnimatedVisibility(
                    visible = showPartnerPhotoActionPicker,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                  ) {
                    Surface(
                      shape = RoundedCornerShape(14.dp),
                      color = Color(0xFFFFF0F5),
                      border = BorderStroke(1.dp, Color(0xFFFFB6C1)),
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                      ) {
                        Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.SpaceBetween,
                          verticalAlignment = Alignment.CenterVertically
                        ) {
                          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFFE91E63), modifier = Modifier.size(14.dp))
                            Text(if (isEnglish) "Partner photo" else "Ảnh người ấy", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF880E4F))
                          }
                          IconButton(
                            onClick = { showPartnerPhotoActionPicker = false },
                            modifier = Modifier.size(22.dp)
                          ) {
                            Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.Gray, modifier = Modifier.size(14.dp))
                          }
                        }

                        Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.spacedBy(6.dp),
                          verticalAlignment = Alignment.CenterVertically
                        ) {
                          Button(
                            onClick = {
                              showPartnerPhotoActionPicker = false
                              partnerCaptureFile.parentFile?.mkdirs()
                              girlCameraLauncher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", partnerCaptureFile))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFE91E63)),
                            border = BorderStroke(1.dp, Color(0xFFFFB6C1)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(36.dp).testTag("btn_partner_camera")
                          ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                              Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(13.dp))
                              Text(if (isEnglish) "Camera" else "Chụp ảnh", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                          }

                          Button(
                            onClick = {
                              showPartnerPhotoActionPicker = false
                              girlLauncher.launch("image/*")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFE91E63)),
                            border = BorderStroke(1.dp, Color(0xFFFFB6C1)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(36.dp).testTag("btn_partner_gallery")
                          ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                              Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(13.dp))
                              Text(if (isEnglish) "Gallery" else "Thư viện", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                          }

                          if (currentGirlAvatar.isNotBlank()) {
                            Button(
                              onClick = {
                                showPartnerPhotoActionPicker = false
                                currentGirlAvatar = ""
                              },
                              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE), contentColor = Color(0xFFD32F2F)),
                              border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                              shape = RoundedCornerShape(10.dp),
                              modifier = Modifier.weight(1f).height(36.dp).testTag("btn_partner_remove")
                            ) {
                              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(13.dp))
                                Text(if (isEnglish) "Delete" else "Xoá", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                              }
                            }
                          }
                        }
                      }
                    }
                  }

                  InLoveDatePickerField(
                    value = currentGirlBirth,
                    onValueChange = { currentGirlBirth = it },
                    label = if (isEnglish) "Birthday" else "Ngày sinh",
                    dialogTitle = if (isEnglish) "Select birthday" else "Chọn ngày sinh",
                    quickPresets = DatePickerPresets.birthDatePresets(isEnglish),
                    isEnglish = isEnglish,
                    modifier = Modifier.fillMaxWidth()
                  )

                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (girlAge > 0) {
                      Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFFEBEE),
                        border = BorderStroke(0.8.dp, Color(0xFFFFCDD2))
                      ) {
                        Text(
                          text = ProfileUtils.formatAgeDisplay(girlAge, isEnglish),
                          fontSize = 11.5.sp,
                          fontWeight = FontWeight.SemiBold,
                          color = Color(0xFFC2185B),
                          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                      }
                    }
                    if (girlZodiac.isNotBlank()) {
                      Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFFEBEE),
                        border = BorderStroke(0.8.dp, Color(0xFFFFCDD2))
                      ) {
                        Text(
                          text = ProfileUtils.formatZodiacDisplay(girlZodiac, isEnglish),
                          fontSize = 11.5.sp,
                          fontWeight = FontWeight.SemiBold,
                          color = Color(0xFFC2185B),
                          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                      }
                    }
                  }
                }
              }

              // KỶ NIỆM NGÀY YÊU & TÍNH NGÀY
              Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF5FF)),
                border = BorderStroke(1.dp, Color(0xFFE1BEE7)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color(0xFF7B1FA2), modifier = Modifier.size(16.dp))
                    Text(
                      text = if (isEnglish) "Anniversary" else "Ngày yêu",
                      fontWeight = FontWeight.Bold,
                      fontSize = 13.5.sp,
                      color = Color(0xFF5E35B1)
                    )
                  }

                  InLoveDatePickerField(
                    value = currentAnniversary,
                    onValueChange = { currentAnniversary = it },
                    label = if (isEnglish) "Start date" else "Bắt đầu yêu",
                    dialogTitle = if (isEnglish) "Select start date" else "Chọn ngày bắt đầu yêu",
                    quickPresets = DatePickerPresets.relationshipStartDatePresets(isEnglish),
                    isEnglish = isEnglish,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "input_anniversary_date"
                  )

                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFEBEE),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFE91E63), modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text = if (isEnglish) "✨ $calculatedOfflineDays days together 💕" else "✨ $calculatedOfflineDays ngày bên nhau 💕",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD81B60)
                      )
                    }
                  }
                }
              }

              // HÀNG NÚT: ĐÓNG & LƯU THAY ĐỔI
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                OutlinedButton(
                  onClick = onDismiss,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                ) {
                  Text(if (isEnglish) "Close" else "Đóng", fontSize = 14.5.sp, color = OnSurfaceVariant)
                }

                Button(
                  onClick = {
                    viewModel.saveCoupleProfile(
                      boy = boyName,
                      boyBirth = boyBirthDate,
                      boyAvatar = boyAvatarUrl,
                      boyA = boyAge,
                      boyZod = boyZodiac,
                      girl = currentGirlName,
                      girlBirth = currentGirlBirth,
                      girlAvatar = currentGirlAvatar,
                      girlA = girlAge,
                      girlZod = girlZodiac,
                      title = currentLoveTitle,
                      days = calculatedOfflineDays,
                      anniversary = currentAnniversary
                    )
                    viewModel.triggerFloatingHearts()
                    viewModel.showToast(if (isEnglish) "Profile saved! 💕" else "Đã lưu hồ sơ thành công! 💕")
                    onDismiss()
                  },
                  shape = RoundedCornerShape(14.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                  modifier = Modifier
                    .weight(1.2f)
                    .height(46.dp)
                    .testTag("btn_save_couple_offline")
                ) {
                  Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(17.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(if (isEnglish) "Save" else "Lưu", fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                }
              }
            }

          } else {
            // --- TAB 1: GHÉP ĐÔI (ONLINE) ---
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
              // 1. INCOMING INVITE (NẾU CÓ)
              incomingInvite?.let { invite ->
                Card(
                  shape = RoundedCornerShape(20.dp),
                  colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F5)),
                  border = BorderStroke(1.5.dp, Color(0xFFFF4081)),
                  modifier = Modifier.fillMaxWidth().testTag("dialog_incoming_invite_card")
                ) {
                  Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFE91E63), modifier = Modifier.size(20.dp))
                      Text(if (isEnglish) "Pairing request" else "Lời mời ghép đôi", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFD81B60))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                      AsyncImage(
                        model = invite.senderAvatar.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200" },
                        contentDescription = "Sender Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(54.dp).clip(CircleShape).border(2.dp, Color(0xFFFF80AB), CircleShape)
                      )
                      Spacer(modifier = Modifier.width(12.dp))
                      Column(modifier = Modifier.weight(1f)) {
                        Text(text = invite.effectiveSenderName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF880E4F))
                        Text(text = "Mã: " + invite.senderCoupleCode, fontSize = 12.sp, color = Color.Gray)
                      }
                    }

                    val incomingDateText = invite.proposedStartDateText.ifEmpty { ProfileUtils.formatDate(invite.proposedStartDate) }
                    val incomingDays = ProfileUtils.calculateLoveDays(invite.proposedStartDate)

                    Surface(
                      shape = RoundedCornerShape(12.dp),
                      color = Color.White,
                      border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = if (isEnglish) "Start date: $incomingDateText" else "Ngày yêu: $incomingDateText", fontSize = 13.sp, color = Color.DarkGray)
                        Text(text = if (isEnglish) "$incomingDays days 💕" else "$incomingDays ngày yêu 💕", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD81B60))
                      }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                      OutlinedButton(
                        onClick = { viewModel.rejectSetLoveInvite(invite.inviteId) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                      ) {
                        Text(if (isEnglish) "Decline" else "Từ chối")
                      }
                      Button(
                        onClick = {
                          viewModel.acceptSetLoveInvite(invite.inviteId)
                          onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                        modifier = Modifier.weight(1.2f)
                      ) {
                        Text(if (isEnglish) "Accept ❤️" else "Đồng ý ❤️", fontWeight = FontWeight.Bold)
                      }
                    }
                  }
                }
              }

              // 2. OUTGOING INVITE (NẾU CÓ)
              outgoingInvite?.takeIf { relationshipStatus != OnlineStatus.COUPLED }?.let { out ->
                Card(
                  shape = RoundedCornerShape(20.dp),
                  colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                  border = BorderStroke(1.2.dp, Color(0xFFFFB74D)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isEnglish) "Waiting for response..." else "Chờ xác nhận...", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color(0xFFE65100))
                    val outDays = ProfileUtils.calculateLoveDays(out.proposedStartDate)
                    Text(
                      text = if (isEnglish) "Code: ${out.targetCoupleCode} • $outDays days" else "Mã: ${out.targetCoupleCode} • $outDays ngày",
                      fontSize = 12.5.sp,
                      color = Color.DarkGray
                    )
                    OutlinedButton(
                      onClick = { viewModel.cancelSentInvite() },
                      shape = RoundedCornerShape(10.dp),
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Text(if (isEnglish) "Cancel request" else "Hủy lời mời")
                    }
                  }
                }
              }

              // 3. COUPLED STATUS (NẾU ĐÃ KẾT ĐÔI)
              if (relationshipStatus == OnlineStatus.COUPLED) {
                val partner = partnerUser
                Card(
                  shape = RoundedCornerShape(20.dp),
                  colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F5)),
                  border = BorderStroke(1.2.dp, Color(0xFFFF80AB)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (isEnglish) "Coupled ❤️" else "Đã kết đôi ❤️", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF880E4F))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      AsyncImage(
                        model = partner?.avatarUrl?.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200" } ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(50.dp).clip(CircleShape).border(2.dp, Color(0xFFFF4081), CircleShape)
                      )
                      Spacer(modifier = Modifier.width(12.dp))
                      Column {
                        Text(partner?.effectiveDisplayName ?: "Người ấy", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Mã: ${partner?.coupleCode ?: ""}", fontSize = 12.sp, color = Color.Gray)
                      }
                    }
                  }
                }
              }

              // 4. MÃ KẾT ĐÔI CỦA BẠN & TÌM KIẾM ĐỐI PHƯƠNG
              if (relationshipStatus != OnlineStatus.COUPLED && incomingInvite == null) {
                // Thẻ mã của bạn
                Card(
                  shape = RoundedCornerShape(20.dp),
                  colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F5)),
                  border = BorderStroke(1.dp, Color(0xFFFFB6C1)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                      text = if (isEnglish) "Your Code" else "Mã của bạn",
                      fontSize = 12.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFF880E4F)
                    )
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Text(
                        text = currentUser.coupleCode,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFD81B60),
                        letterSpacing = 1.sp
                      )
                      Row {
                        IconButton(onClick = { copyToClipboard(currentUser.coupleCode) }) {
                          Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFFD81B60), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { shareCoupleLink(currentUser.coupleCode) }) {
                          Icon(Icons.Default.Share, contentDescription = "Share", tint = Color(0xFFD81B60), modifier = Modifier.size(18.dp))
                        }
                      }
                    }
                  }
                }

                // Thẻ tìm kiếm & gửi lời mời
                Card(
                  shape = RoundedCornerShape(20.dp),
                  colors = CardDefaults.cardColors(containerColor = Color.White),
                  border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                      text = if (isEnglish) "Connect" else "Kết nối",
                      fontWeight = FontWeight.Bold,
                      fontSize = 13.sp,
                      color = Color(0xFF880E4F)
                    )

                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                          searchQuery = it.uppercase()
                          searchError = null
                        },
                        placeholder = { Text(if (isEnglish) "Enter code..." else "Nhập mã ghép đôi...", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = dialogTextFieldColors(),
                        trailingIcon = {
                          if (searchQuery.isNotEmpty()) {
                            IconButton(
                              onClick = {
                                searchQuery = ""
                                searchError = null
                              },
                              modifier = Modifier.size(26.dp)
                            ) {
                              Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (isEnglish) "Clear" else "Xóa",
                                tint = Color.Gray,
                                modifier = Modifier.size(15.dp)
                              )
                            }
                          } else {
                            Surface(
                              shape = RoundedCornerShape(8.dp),
                              color = Color(0xFFFFF0F5),
                              modifier = Modifier
                                .padding(end = 6.dp)
                                .clickable {
                                  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                  val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
                                  if (clipText.isNotEmpty()) {
                                    searchQuery = clipText.uppercase()
                                    searchError = null
                                  } else {
                                    viewModel.showToast(if (isEnglish) "Clipboard is empty" else "Bộ nhớ tạm trống")
                                  }
                                }
                            ) {
                              Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                              ) {
                                Icon(
                                  imageVector = Icons.Default.ContentCopy,
                                  contentDescription = null,
                                  tint = Color(0xFFE91E63),
                                  modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                  text = if (isEnglish) "Paste" else "Dán mã",
                                  fontSize = 10.5.sp,
                                  fontWeight = FontWeight.Bold,
                                  color = Color(0xFFE91E63)
                                )
                              }
                            }
                          }
                        },
                        modifier = Modifier.weight(1f).testTag("dialog_search_input")
                      )

                      Button(
                        onClick = {
                          val q = searchQuery.trim()
                          if (q.isNotBlank()) {
                            coroutineScope.launch {
                              isSearching = true
                              searchError = null
                              val res = viewModel.searchPartnerForSetLove(q)
                              isSearching = false
                              if (res == null) {
                                searchError = "Không tìm thấy người dùng với mã này"
                              } else if (res.uid == currentUser.uid) {
                                searchError = "Không thể gửi lời mời cho chính mình"
                              } else {
                                selectedPartner = res
                              }
                            }
                          }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                        modifier = Modifier.height(50.dp).testTag("dialog_btn_search")
                      ) {
                        if (isSearching) {
                          CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                          Icon(Icons.Default.Search, contentDescription = "Tìm")
                        }
                      }
                    }

                    if (searchError != null) {
                      Text(text = searchError.orEmpty(), color = Color(0xFFD32F2F), fontSize = 12.sp)
                    }

                    selectedPartner?.let { partner ->
                      Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F5)),
                        border = BorderStroke(1.dp, Color(0xFFFF4081)),
                        modifier = Modifier.fillMaxWidth()
                      ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                          Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                              model = partner.avatarUrl.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200" },
                              contentDescription = null,
                              contentScale = ContentScale.Crop,
                              modifier = Modifier.size(50.dp).clip(CircleShape).border(2.dp, Color(0xFFFF4081), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                              Text(partner.effectiveDisplayName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF880E4F))
                              Text(if (isEnglish) "Code: ${partner.coupleCode}" else "Mã: ${partner.coupleCode}", fontSize = 12.sp, color = Color.Gray)
                            }
                          }

                          InLoveDatePickerField(
                            value = proposedStartDateText,
                            onValueChange = { proposedStartDateText = it },
                            label = if (isEnglish) "Start date" else "Ngày yêu",
                            dialogTitle = if (isEnglish) "Select start date" else "Chọn ngày bắt đầu yêu",
                            quickPresets = DatePickerPresets.relationshipStartDatePresets(isEnglish),
                            isEnglish = isEnglish,
                            modifier = Modifier.fillMaxWidth()
                          )

                          OutlinedTextField(
                            value = loveNote,
                            onValueChange = { loveNote = it },
                            label = { Text(if (isEnglish) "Note (optional)" else "Lời nhắn (tùy chọn)") },
                            maxLines = 2,
                            shape = RoundedCornerShape(10.dp),
                            colors = dialogTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                          )

                          Button(
                            onClick = {
                              val parsedMillis = ProfileUtils.parseDateToMillis(proposedStartDateText)
                              viewModel.sendSetLoveInvite(
                                targetCodeOrLink = partner.coupleCode,
                                proposedStartDateMillis = parsedMillis,
                                loveNote = loveNote
                              )
                              onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                            modifier = Modifier.fillMaxWidth()
                          ) {
                            Text(if (isEnglish) "Send request ❤️" else "Gửi lời mời ❤️", fontWeight = FontWeight.Bold)
                          }
                        }
                      }
                    }
                  }
                }

                // Nút Đóng Dialog cho Tab Ghép đôi
                OutlinedButton(
                  onClick = onDismiss,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                ) {
                  Text(if (isEnglish) "Close" else "Đóng", fontSize = 14.sp, color = OnSurfaceVariant)
                }
              }
            }
          }
        }
      }
    }
  }
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
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(Color(0xFFFFEBEE)),
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
        color = Color(0xFF26071B)
      )
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = message,
          fontSize = 14.sp,
          lineHeight = 20.sp,
          color = OnSurfaceVariant
        )
        if (!itemName.isNullOrBlank()) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFFF0F5),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = itemName,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(0xFF26071B)
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.testTag("btn_confirm_delete")
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Icon(
            imageVector = Icons.Filled.DeleteForever,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Color.White
          )
          Text(if (isEnglish) "Delete" else "Xóa Vĩnh Viễn", fontWeight = FontWeight.Bold, color = Color.White)
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("btn_cancel_delete")
      ) {
        Text(if (isEnglish) "Cancel" else "Hủy bỏ", color = OnSurfaceVariant, fontWeight = FontWeight.Medium)
      }
    },
    containerColor = Color.White,
    shape = RoundedCornerShape(24.dp),
    modifier = Modifier.animateDialogEntrance().testTag("dialog_confirm_delete")
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

  // Selected preset index: 0 = 10 sec test, 1 = 9:00 AM, 2 = 19:30 PM, 3 = In 2 Hours, 4 = Custom
  var selectedPreset by remember { mutableIntStateOf(1) }
  var customHour by remember { mutableIntStateOf(9) }
  var customMinute by remember { mutableIntStateOf(0) }

  fun calculateTriggerMillis(preset: Int, hour: Int, minute: Int): Long {
    val now = System.currentTimeMillis()
    return when (preset) {
      0 -> now + 10_000L // 10 seconds (instant test)
      3 -> now + 2 * 3600 * 1000L // 2 hours
      else -> {
        val calendar = java.util.Calendar.getInstance()
        val targetH = if (preset == 1) 9 else if (preset == 2) 19 else hour
        val targetM = if (preset == 1) 0 else if (preset == 2) 30 else minute
        calendar.set(java.util.Calendar.HOUR_OF_DAY, targetH)
        calendar.set(java.util.Calendar.MINUTE, targetM)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        if (calendar.timeInMillis <= now) {
          calendar.add(java.util.Calendar.DAY_OF_YEAR, 1) // next day
        }
        calendar.timeInMillis
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(Color(0xFFFFF0F5)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Filled.NotificationsActive,
          contentDescription = null,
          tint = Primary,
          modifier = Modifier.size(26.dp)
        )
      }
    },
    title = {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = if (isEnglish) "Set Reminder Alarm" else "Cài Đặt Báo Thức Kỷ Niệm",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = Color(0xFF26071B)
        )
        Text(
          text = if (isEnglish) "Get a chime and notification right on time" else "Nhận chuông và thông báo vào thời gian chính xác",
          fontSize = 12.sp,
          color = OnSurfaceVariant
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
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_alarm_title"),
          singleLine = true
        )

        OutlinedTextField(
          value = message,
          onValueChange = { message = it },
          label = { Text(if (isEnglish) "Notification message" else "Lời nhắn kèm thông báo") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_alarm_message"),
          maxLines = 3
        )

        Text(
          text = if (isEnglish) "Choose alarm time:" else "Chọn thời gian báo thức:",
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          color = Color(0xFF26071B)
        )

        // Presets Chips
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
          presets.forEach { (label, index) ->
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (selectedPreset == index) Primary.copy(alpha = 0.12f) else Color(0xFFF7F2FA),
              border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (selectedPreset == index) Primary else Color(0xFFE7E0EC)
              ),
              modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedPreset = index }
                .testTag("preset_alarm_$index")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = label,
                  fontSize = 13.sp,
                  fontWeight = if (selectedPreset == index) FontWeight.Bold else FontWeight.Normal,
                  color = if (selectedPreset == index) Primary else Color(0xFF49454F)
                )
                if (selectedPreset == index) {
                  Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(14.dp)
                  )
                }
              }
            }
          }
        }

        // Custom time picker row if custom is selected
        if (selectedPreset == 4) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFFF0F5),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceAround
            ) {
              // Hours
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (isEnglish) "Hour" else "Giờ", fontSize = 11.sp, color = OnSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(
                    onClick = { customHour = (customHour - 1 + 24) % 24 },
                    modifier = Modifier.size(48.dp)
                  ) {
                    Text("-", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Primary)
                  }
                  Text(
                    text = String.format("%02d", customHour),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF26071B),
                    modifier = Modifier.padding(horizontal = 6.dp)
                  )
                  IconButton(
                    onClick = { customHour = (customHour + 1) % 24 },
                    modifier = Modifier.size(48.dp)
                  ) {
                    Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Primary)
                  }
                }
              }

              Text(":", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Primary)

              // Minutes
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (isEnglish) "Min" else "Phút", fontSize = 11.sp, color = OnSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(
                    onClick = { customMinute = (customMinute - 5 + 60) % 60 },
                    modifier = Modifier.size(48.dp)
                  ) {
                    Text("-", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Primary)
                  }
                  Text(
                    text = String.format("%02d", customMinute),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF26071B),
                    modifier = Modifier.padding(horizontal = 6.dp)
                  )
                  IconButton(
                    onClick = { customMinute = (customMinute + 5) % 60 },
                    modifier = Modifier.size(48.dp)
                  ) {
                    Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Primary)
                  }
                }
              }
            }
          }
        }

        // Test instant notification button
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color(0xFFFFF0F5),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onTestNow(title, message) }
            .testTag("btn_test_alarm_instant")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = Icons.Filled.NotificationsActive,
              contentDescription = null,
              tint = Primary,
              modifier = Modifier.size(16.dp)
            )
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
      Button(
        onClick = {
          val trigger = calculateTriggerMillis(selectedPreset, customHour, customMinute)
          onSchedule(title, message, trigger, reminderId)
        },
        colors = ButtonDefaults.buttonColors(containerColor = Primary),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.testTag("btn_confirm_schedule_alarm")
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Icon(
            imageVector = Icons.Filled.AlarmOn,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Color.White
          )
          Text(if (isEnglish) "Set Alarm" else "Đặt Báo Thức", fontWeight = FontWeight.Bold, color = Color.White)
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("btn_cancel_schedule_alarm")
      ) {
        Text(if (isEnglish) "Close" else "Đóng", color = OnSurfaceVariant)
      }
    },
    containerColor = Color.White,
    shape = RoundedCornerShape(24.dp),
    modifier = Modifier.animateDialogEntrance().testTag("dialog_set_alarm_reminder")
  )
}

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

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.animateDialogEntrance(),
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Filled.Favorite,
          contentDescription = null,
          tint = Color(0xFFFF2D75),
          modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (isEnglish) "Customize Love Title" else "Đổi Tên Hành Trình Yêu",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = Color(0xFF26071B)
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text = if (isEnglish) "Enter your custom title or choose a suggestion below:" else "Nhập tiêu đề bạn muốn hoặc chọn gợi ý bên dưới:",
          fontSize = 13.sp,
          color = OnSurfaceVariant
        )

        OutlinedTextField(
          value = titleInput,
          onValueChange = { titleInput = it },
          label = { Text(if (isEnglish) "Journey Title" else "Tiêu đề hành trình") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        Text(
          text = if (isEnglish) "Quick suggestions:" else "Gợi ý nhanh:",
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          color = Color(0xFF26071B)
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          suggestions.forEach { suggestion ->
            val isSelected = titleInput.trim() == suggestion
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = if (isSelected) Color(0xFFFFE4EC) else Color(0xFFF9F5F8),
              border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isSelected) Color(0xFFFF2D75) else Color(0xFFE8DEF8)
              ),
              modifier = Modifier
                .fillMaxWidth()
                .clickable { titleInput = suggestion }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = suggestion,
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) Color(0xFF880E4F) else Color(0xFF424242)
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val clean = titleInput.trim().ifEmpty { if (isEnglish) "Our Love Journey" else "Hành Trình Yêu Thương" }
          onConfirm(clean)
        },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D75)),
        shape = RoundedCornerShape(14.dp)
      ) {
        Text(if (isEnglish) "Save" else "Lưu Lại", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      OutlinedButton(
        onClick = onDismiss,
        shape = RoundedCornerShape(14.dp)
      ) {
        Text(if (isEnglish) "Cancel" else "Hủy")
      }
    },
    containerColor = Color.White,
    shape = RoundedCornerShape(24.dp)
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
  val todayFormatted = remember { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date()) }
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

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.Favorite,
          contentDescription = null,
          tint = Primary
        )
        Text(
          text = if (isEnglish) "New anniversary" else "Ngày kỷ niệm",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = OnSurface
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
          shape = RoundedCornerShape(12.dp),
          colors = inLoveFieldColors()
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

        // Icon-only type picker; the selected type's name shows once below.
        val typeIcons = mapOf(
          "LOVE" to Icons.Filled.Favorite,
          "FIRST_DATE" to Icons.Filled.LocalCafe,
          "FIRST_KISS" to Icons.Filled.Mood,
          "PROPOSAL" to Icons.Filled.Diamond,
          "WEDDING" to Icons.Filled.Celebration,
          "CUSTOM" to Icons.Filled.Star
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          typeOptions.forEach { (typeKey, label) ->
            val isSelected = selectedType == typeKey
            val bg by androidx.compose.animation.animateColorAsState(
              if (isSelected) Primary else SurfaceContainerHigh, label = "type_bg"
            )
            val scale by androidx.compose.animation.core.animateFloatAsState(
              if (isSelected) 1.12f else 1f,
              androidx.compose.animation.core.spring(androidx.compose.animation.core.Spring.DampingRatioMediumBouncy),
              label = "type_scale"
            )
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(40.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(bg)
                .clickable { selectedType = typeKey }
            ) {
              Icon(
                imageVector = typeIcons.getValue(typeKey),
                contentDescription = label,
                tint = if (isSelected) Color.White else OnSurfaceVariant,
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }
        Text(
          text = typeOptions.firstOrNull { it.first == selectedType }?.second.orEmpty(),
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = Primary,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
          shape = RoundedCornerShape(12.dp),
          colors = inLoveFieldColors()
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            Icons.Filled.Repeat,
            contentDescription = if (isEnglish) "Repeat every year" else "Lặp lại hàng năm",
            tint = if (isAnnual) Primary else OnSurfaceVariant
          )
          Switch(
            checked = isAnnual,
            onCheckedChange = { isAnnual = it },
            colors = SwitchDefaults.colors(
              checkedThumbColor = Color.White,
              checkedTrackColor = Primary
            )
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
            tint = if (notificationEnabled) Primary else OnSurfaceVariant
          )
          Switch(
            checked = notificationEnabled,
            onCheckedChange = { notificationEnabled = it },
            modifier = Modifier.testTag("switch_anniversary_notify"),
            colors = SwitchDefaults.colors(
              checkedThumbColor = Color.White,
              checkedTrackColor = Primary
            )
          )
        }

        if (notificationEnabled) {
          val reminderOptions = if (isEnglish) {
            listOf(0 to "Same Day", 1 to "1 Day", 3 to "3 Days", 7 to "7 Days")
          } else {
            listOf(0 to "Đúng ngày", 1 to "1 ngày", 3 to "3 ngày", 7 to "7 ngày")
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            reminderOptions.forEach { (days, label) ->
              val isSelected = reminderDaysBefore == days
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) Primary else SurfaceContainerHigh,
                modifier = Modifier
                  .weight(1f)
                  .clickable { reminderDaysBefore = days }
              ) {
                Text(
                  text = label,
                  fontSize = 10.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  color = if (isSelected) Color.White else OnSurface,
                  modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp),
                  textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                  maxLines = 1
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onConfirm(title, dateText, selectedType, description, isAnnual, notificationEnabled, reminderDaysBefore)
        },
        colors = ButtonDefaults.buttonColors(containerColor = Primary),
        modifier = Modifier.testTag("btn_save_anniversary_date")
      ) {
        Text(if (isEnglish) "Save" else "Lưu", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("btn_cancel_anniversary_date")
      ) {
        Text(if (isEnglish) "Cancel" else "Hủy", color = OnSurfaceVariant)
      }
    },
    containerColor = Color.White,
    shape = RoundedCornerShape(24.dp),
    modifier = Modifier.animateDialogEntrance().testTag("dialog_add_anniversary_date")
  )
}

@Composable
fun AddGiftReminderDialog(
  onDismiss: () -> Unit,
  onConfirm: (
    title: String,
    recipient: String,
    occasion: String,
    dueDateText: String,
    estimatedBudget: String,
    notes: String
  ) -> Unit,
  isEnglish: Boolean = false
) {
  var title by remember { mutableStateOf("") }
  var recipient by remember { mutableStateOf(if (isEnglish) "My love" else "Người ấy") }
  var occasion by remember { mutableStateOf(if (isEnglish) "Love anniversary" else "Kỷ niệm ngày yêu") }
  var dueDateText by remember {
    mutableStateOf(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")))
  }
  var estimatedBudget by remember { mutableStateOf("500.000đ") }
  var notes by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.CardGiftcard,
          contentDescription = null,
          tint = Primary
        )
        Text(
          text = if (isEnglish) "Add Gift Reminder" else "Thêm Nhắc Nhở Quà Tặng",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = OnSurface
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
          label = { Text(if (isEnglish) "Gift to prepare *" else "Món quà cần chuẩn bị *") },
          placeholder = { Text(if (isEnglish) "e.g. Eternal roses & card" else "Ví dụ: Bó hoa hồng vĩnh cửu & Thiệp") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_gift_reminder_title"),
          singleLine = true
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = recipient,
            onValueChange = { recipient = it },
            label = { Text(if (isEnglish) "For" else "Tặng cho") },
            placeholder = { Text(if (isEnglish) "My love" else "Người thương") },
            modifier = Modifier
              .weight(1f)
              .testTag("input_gift_reminder_recipient"),
            singleLine = true
          )

          OutlinedTextField(
            value = estimatedBudget,
            onValueChange = { estimatedBudget = it },
            label = { Text(if (isEnglish) "Budget" else "Dự chi") },
            placeholder = { Text("500.000đ") },
            modifier = Modifier
              .weight(1f)
              .testTag("input_gift_reminder_budget"),
            singleLine = true
          )
        }

        OutlinedTextField(
          value = occasion,
          onValueChange = { occasion = it },
          label = { Text(if (isEnglish) "Occasion" else "Dịp kỷ niệm / Sự kiện") },
          placeholder = { Text(if (isEnglish) "1,000-day anniversary" else "Kỷ niệm 1.000 ngày") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_gift_reminder_occasion"),
          singleLine = true
        )

        InLoveDatePickerField(
          value = dueDateText,
          onValueChange = { dueDateText = it },
          label = if (isEnglish) "Prepare by (dd/MM/yyyy)" else "Hạn hoàn thành chuẩn bị (dd/MM/yyyy)",
          placeholder = "dd/MM/yyyy",
          dialogTitle = if (isEnglish) "Pick gift date" else "Chọn ngày hẹn tặng quà",
          quickPresets = DatePickerPresets.upcomingAnniversaryPresets(isEnglish),
          isEnglish = isEnglish,
          modifier = Modifier.fillMaxWidth(),
          testTag = "input_gift_reminder_date"
        )

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text(if (isEnglish) "Gift notes" else "Ghi chú món quà") },
          placeholder = { Text(if (isEnglish) "Color, size, shop address..." else "Màu sắc, kích cỡ, địa chỉ cửa hàng...") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_gift_reminder_notes"),
          maxLines = 3
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onConfirm(title, recipient, occasion, dueDateText, estimatedBudget, notes)
        },
        colors = ButtonDefaults.buttonColors(containerColor = Primary),
        modifier = Modifier.testTag("btn_save_gift_reminder")
      ) {
        Text(if (isEnglish) "Save" else "Lưu Nhắc Nhở", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(if (isEnglish) "Cancel" else "Hủy", color = OnSurfaceVariant)
      }
    },
    containerColor = Color.White,
    shape = RoundedCornerShape(24.dp),
    modifier = Modifier.animateDialogEntrance().testTag("dialog_add_gift_reminder")
  )
}




@Composable
private fun inLoveFieldColors() = OutlinedTextFieldDefaults.colors(
  focusedTextColor = Color(0xFF1E1E24),
  unfocusedTextColor = Color(0xFF1E1E24),
  focusedContainerColor = Color.White,
  unfocusedContainerColor = Color.White,
  focusedBorderColor = Primary,
  unfocusedBorderColor = Color(0xFFC7C7CC),
  focusedLabelColor = Primary,
  unfocusedLabelColor = Color(0xFF424242),
  cursorColor = Primary
)
