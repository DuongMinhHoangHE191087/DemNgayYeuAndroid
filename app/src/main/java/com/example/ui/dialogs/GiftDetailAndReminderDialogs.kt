@file:Suppress("FunctionName")
package com.example.ui.dialogs

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.GiftIdeaEntity
import com.example.domain.content.GiftCatalog
import com.example.ui.components.AppAlertDialog
import com.example.ui.components.AppDialog
import com.example.ui.components.DatePickerPresets
import com.example.ui.components.InLoveDatePickerField
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.animateDialogEntrance
import com.example.ui.designsystem.InLoveRadii
import com.example.ui.designsystem.inLoveOutlinedTextFieldColors
import com.example.ui.screens.IllustrationCaption
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryFixed
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun GiftDetailDialog(
  gift: GiftIdeaEntity,
  onDismiss: () -> Unit,
  onToggleFavorite: () -> Unit,
  isEnglish: Boolean = false
) {
  val localized = GiftCatalog.localized(gift, isEnglish)
  AppDialog(onDismissRequest = onDismiss) {
    Card(
      shape = RoundedCornerShape(InLoveRadii.Card),
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
              .size(MinTouchBox)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.5f))
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = if (isEnglish) "Close" else "Đóng",
              tint = Color.White,
              modifier = Modifier.size(18.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Surface(shape = RoundedCornerShape(InLoveRadii.Badge), color = PrimaryFixed.copy(alpha = 0.7f)) {
          Text(
            text = GiftCatalog.categoryLabel(localized.categoryKey, isEnglish),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = localized.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDarkPlum)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = localized.description, fontSize = 13.sp, color = TextSubtlePlum, lineHeight = 18.sp)

        GiftCatalog.priceLabel(gift.priceRange, isEnglish)?.let { price ->
          Spacer(modifier = Modifier.height(6.dp))
          Text(text = price, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Primary)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Surface(shape = RoundedCornerShape(14.dp), color = SurfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
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
              color = TextDarkPlum
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Surface(
            shape = RoundedCornerShape(InLoveRadii.Pill),
            color = PrimaryFixed.copy(alpha = 0.5f),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = MinTouchBox)
              .clip(RoundedCornerShape(InLoveRadii.Pill))
              .clickable(onClick = onToggleFavorite)
          ) {
            Row(
              modifier = Modifier.padding(vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              Icon(imageVector = Icons.Filled.Favorite, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
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
          PrimaryGradientButton(
            text = if (isEnglish) "Got it" else "Đã rõ",
            onClick = onDismiss,
            modifier = Modifier
              .weight(1f)
              .heightIn(min = MinTouchBox)
          )
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
  AppDialog(onDismissRequest = onDismiss) {
    Card(
      shape = RoundedCornerShape(InLoveRadii.CardLarge),
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
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Surface(shape = RoundedCornerShape(InLoveRadii.Chip), color = PrimaryFixed) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(imageVector = Icons.Filled.Star, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
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
              .size(MinTouchBox)
              .clip(CircleShape)
              .background(SurfaceContainerHigh)
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = if (isEnglish) "Close" else "Đóng",
              tint = TextSubtlePlum,
              modifier = Modifier.size(18.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = if (isEnglish) "1,000-Day Celebration Ideas" else "Ý Tưởng Kỷ Niệm 1.000 Ngày",
          fontSize = 19.sp,
          fontWeight = FontWeight.Bold,
          color = TextDarkPlum
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = if (isEnglish) "A few itinerary ideas for an anniversary evening. Pick what suits you both." else "Vài gợi ý lịch trình cho một buổi tối kỷ niệm. Chọn những gì hợp với hai bạn.",
          fontSize = 13.sp,
          color = TextSubtlePlum,
          lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

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
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Surface(shape = CircleShape, color = Primary, modifier = Modifier.size(24.dp)) {
                Box(contentAlignment = Alignment.Center) {
                  Text("${index + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
              if (index < steps.size - 1) {
                Box(modifier = Modifier.width(2.dp).height(44.dp).background(PrimaryFixed))
              }
            }
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
              Text("$time • $title", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Primary)
              Spacer(modifier = Modifier.height(2.dp))
              Text(desc, fontSize = 12.sp, color = TextSubtlePlum, lineHeight = 16.sp)
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))
        PrimaryGradientButton(
          text = if (isEnglish) "Got it" else "Đã rõ",
          onClick = onDismiss,
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchBox)
        )
      }
    }
  }
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
    mutableStateOf(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
  }
  var estimatedBudget by remember { mutableStateOf("500.000đ") }
  var notes by remember { mutableStateOf("") }

  AppAlertDialog(
    onDismissRequest = onDismiss,
    scrollableText = false,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(imageVector = Icons.Filled.CardGiftcard, contentDescription = null, tint = Primary)
        Text(
          text = if (isEnglish) "Add Gift Reminder" else "Thêm Nhắc Nhở Quà Tặng",
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
          label = { Text(if (isEnglish) "Gift to prepare *" else "Món quà cần chuẩn bị *") },
          placeholder = { Text(if (isEnglish) "e.g. Eternal roses & card" else "Ví dụ: Bó hoa hồng vĩnh cửu & Thiệp") },
          colors = inLoveOutlinedTextFieldColors(),
          shape = RoundedCornerShape(InLoveRadii.Input),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_gift_reminder_title"),
          singleLine = true
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = recipient,
            onValueChange = { recipient = it },
            label = { Text(if (isEnglish) "For" else "Tặng cho") },
            placeholder = { Text(if (isEnglish) "My love" else "Người ấy") },
            colors = inLoveOutlinedTextFieldColors(),
            shape = RoundedCornerShape(InLoveRadii.Input),
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
            colors = inLoveOutlinedTextFieldColors(),
            shape = RoundedCornerShape(InLoveRadii.Input),
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
          colors = inLoveOutlinedTextFieldColors(),
          shape = RoundedCornerShape(InLoveRadii.Input),
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
          colors = inLoveOutlinedTextFieldColors(),
          shape = RoundedCornerShape(InLoveRadii.Input),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_gift_reminder_notes"),
          maxLines = 3
        )
      }
    },
    confirmButton = {
      PrimaryGradientButton(
        text = if (isEnglish) "Save" else "Lưu Nhắc Nhở",
        onClick = { onConfirm(title, recipient, occasion, dueDateText, estimatedBudget, notes) },
        modifier = Modifier.heightIn(min = MinTouchBox),
        testTag = "btn_save_gift_reminder"
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
    shape = RoundedCornerShape(InLoveRadii.Card),
    modifier = Modifier
      .animateDialogEntrance()
      .testTag("dialog_add_gift_reminder")
  )
}
