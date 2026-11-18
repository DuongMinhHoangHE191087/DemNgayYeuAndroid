@file:Suppress("FunctionName")
package com.example.ui.screens.gift

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChecklistItemEntity
import com.example.data.model.GiftReminderEntity
import com.example.domain.content.GiftCatalog
import com.example.ui.designsystem.InLoveColors
import com.example.ui.designsystem.InLoveRadii
import com.example.ui.theme.CardBorder
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.OnSurface
import com.example.ui.theme.OutlineVariant
import com.example.ui.theme.Primary
import com.example.ui.theme.RoseGradientMid
import com.example.ui.theme.RoseGradientStart
import com.example.ui.theme.Secondary
import com.example.ui.theme.SurfaceContainerHighest
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum

@Composable
internal fun GiftAmbientBannerCard(
  progressPercent: Float,
  isEnglish: Boolean,
) {
  Card(
    shape = RoundedCornerShape(InLoveRadii.Card),
    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
    border = BorderStroke(1.dp, CardBorder),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (isEnglish) "Gift & Surprise Ideas" else "Gợi Ý Quà & Bất Ngờ",
          fontSize = 17.sp,
          fontWeight = FontWeight.SemiBold,
          color = TextDarkPlum
        )
        Text(
          text = "${progressPercent.toInt()}%",
          fontSize = 12.5.sp,
          fontWeight = FontWeight.SemiBold,
          color = Primary
        )
      }
      Spacer(modifier = Modifier.height(10.dp))
      LinearProgressIndicator(
        progress = { progressPercent / 100f },
        modifier = Modifier
          .fillMaxWidth()
          .height(6.dp)
          .clip(RoundedCornerShape(InLoveRadii.Pill)),
        color = Primary,
        trackColor = SurfaceContainerHighest,
        strokeCap = StrokeCap.Round
      )
    }
  }
}

@Composable
internal fun GiftCategoryFilterRow(
  selectedCategory: String,
  isEnglish: Boolean,
  onSelectCategory: (String) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    GiftCatalog.chips(isEnglish).forEach { chip ->
      val isSelected = selectedCategory == chip.key
      Surface(
        shape = RoundedCornerShape(InLoveRadii.InputSoft),
        color = if (isSelected) Primary else Color.White.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, if (isSelected) Primary else InLoveColors.BorderSoftPink),
        modifier = Modifier
          .heightIn(min = MinTouchBox)
          .clip(RoundedCornerShape(InLoveRadii.InputSoft))
          .clickable { onSelectCategory(chip.key) }
          .testTag("gift_tab_${chip.key}")
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
          Text(
            text = chip.label,
            fontSize = 12.5.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSelected) Color.White else TextDarkPlum
          )
        }
      }
    }
  }
}

@Composable
internal fun GiftVipProposalBanner(
  isEnglish: Boolean,
  onOpenVipProposal: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(InLoveRadii.Card),
    colors = CardDefaults.cardColors(containerColor = InLoveColors.SoftBlushCard),
    border = BorderStroke(1.dp, CardBorder),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("vip_proposal_banner")
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Text(
        text = if (isEnglish) "1,000-Day Celebration Ideas" else "Ý Tưởng Kỷ Niệm 1.000 Ngày",
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextDarkPlum
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = if (isEnglish) {
          "Curated dinner plans, flowers, and a handwritten note to mark the day."
        } else {
          "Gợi ý hoa tươi, bữa tối ấm áp và thiệp viết tay cho cột mốc đáng nhớ."
        },
        fontSize = 12.5.sp,
        color = TextSubtlePlum,
        lineHeight = 18.sp
      )
      Spacer(modifier = Modifier.height(14.dp))
      Surface(
        shape = RoundedCornerShape(InLoveRadii.InputSoft),
        color = Primary,
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = MinTouchBox)
          .clip(RoundedCornerShape(InLoveRadii.InputSoft))
          .clickable(onClick = onOpenVipProposal)
          .testTag("btn_view_vip_proposal")
      ) {
        Row(
          modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Text(
            text = if (isEnglish) "View VIP plan" else "Xem gói VIP",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
          )
          Spacer(modifier = Modifier.width(6.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}

@Composable
internal fun GiftChecklistCard(
  checklistItems: List<ChecklistItemEntity>,
  completedCount: Int,
  totalCount: Int,
  isEnglish: Boolean,
  onOpenAddDialog: () -> Unit,
  onToggleItem: (ChecklistItemEntity) -> Unit,
  onRenameItem: (ChecklistItemEntity, String) -> Unit,
  onRequestDeleteItem: (ChecklistItemEntity) -> Unit,
) {
  var checklistExpanded by rememberSaveable { mutableStateOf(true) }
  val checklistChevron by animateFloatAsState(targetValue = if (checklistExpanded) 180f else 0f, label = "checklist_section_chevron")
  Card(
    shape = RoundedCornerShape(InLoveRadii.Card),
    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow.copy(alpha = 0.96f)),
    border = BorderStroke(1.dp, InLoveColors.BorderSoftPink),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("checklist_section")
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { checklistExpanded = !checklistExpanded },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = if (isEnglish) "Anniversary Gift Checklist" else "Checklist Quà Kỷ Niệm",
            fontSize = 15.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextDarkPlum
          )
          Text(
            text = "$completedCount/$totalCount",
            fontSize = 12.sp,
            color = TextSubtlePlum
          )
        }
        IconButton(
          onClick = onOpenAddDialog,
          modifier = Modifier
            .size(MinTouchBox)
            .testTag("btn_add_checklist_task")
        ) {
          Icon(imageVector = Icons.Filled.Add, contentDescription = if (isEnglish) "Add task" else "Thêm việc", tint = Primary)
        }
        Icon(
          imageVector = Icons.Filled.ExpandMore,
          contentDescription = if (isEnglish) "Expand" else "Mở rộng",
          tint = TextSubtlePlum,
          modifier = Modifier
            .size(20.dp)
            .rotate(checklistChevron)
        )
      }
      AnimatedVisibility(visible = checklistExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Spacer(modifier = Modifier.height(6.dp))
          checklistItems.forEach { item ->
            ChecklistRowContent(
              item = item,
              isEnglish = isEnglish,
              onToggle = { onToggleItem(item) },
              onEdit = { text -> onRenameItem(item, text) },
              onDelete = { onRequestDeleteItem(item) }
            )
          }
        }
      }
    }
  }
}

@Composable
internal fun GiftRemindersCard(
  giftReminders: List<GiftReminderEntity>,
  isEnglish: Boolean,
  onOpenAddDialog: () -> Unit,
  onToggleReminder: (GiftReminderEntity) -> Unit,
  onUpdateReminder: (GiftReminderEntity) -> Unit,
  onRequestDeleteReminder: (GiftReminderEntity) -> Unit,
) {
  var remindersExpanded by rememberSaveable { mutableStateOf(true) }
  val remindersChevron by animateFloatAsState(targetValue = if (remindersExpanded) 180f else 0f, label = "gift_reminders_chevron")
  Card(
    shape = RoundedCornerShape(InLoveRadii.Card),
    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
    border = BorderStroke(1.dp, InLoveColors.BorderSoftPink),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("gift_reminders_section")
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { remindersExpanded = !remindersExpanded },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = if (isEnglish) "Saved Gift Reminders" else "Lời Nhắc Quà Tặng Đã Lưu",
            fontSize = 15.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextDarkPlum
          )
          val completedReminders = giftReminders.count { it.isCompleted }
          Text(
            text = "$completedReminders/${giftReminders.size}",
            fontSize = 12.sp,
            color = TextSubtlePlum
          )
        }
        IconButton(
          onClick = onOpenAddDialog,
          modifier = Modifier
            .size(MinTouchBox)
            .testTag("btn_add_gift_reminder_header")
        ) {
          Icon(imageVector = Icons.Filled.Add, contentDescription = if (isEnglish) "Add reminder" else "Thêm nhắc quà", tint = Primary)
        }
        Icon(
          imageVector = Icons.Filled.ExpandMore,
          contentDescription = if (isEnglish) "Expand" else "Mở rộng",
          tint = TextSubtlePlum,
          modifier = Modifier
            .size(20.dp)
            .rotate(remindersChevron)
        )
      }
      AnimatedVisibility(visible = remindersExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Spacer(modifier = Modifier.height(6.dp))
          if (giftReminders.isEmpty()) {
            Icon(
              imageVector = Icons.Filled.CardGiftcard,
              contentDescription = if (isEnglish) "No gift reminders" else "Chưa có lời nhắc",
              tint = OutlineVariant,
              modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp)
                .size(28.dp)
            )
          } else {
            giftReminders.forEach { reminder ->
              GiftReminderRowContent(
                item = reminder,
                isEnglish = isEnglish,
                onToggle = { onToggleReminder(reminder) },
                onEdit = onUpdateReminder,
                onDelete = { onRequestDeleteReminder(reminder) }
              )
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun GiftBottomActions(
  isEnglish: Boolean,
  onOpenAlarm: () -> Unit,
  onOpenWishlist: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Surface(
      shape = RoundedCornerShape(InLoveRadii.InputSoft),
      shadowElevation = 3.dp,
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = MinTouchBox)
        .clip(RoundedCornerShape(InLoveRadii.InputSoft))
        .background(Brush.horizontalGradient(listOf(RoseGradientStart, RoseGradientMid, Secondary)))
        .clickable(onClick = onOpenAlarm)
        .testTag("btn_reminder_alarm")
    ) {
      Row(
        modifier = Modifier
          .background(Brush.horizontalGradient(listOf(RoseGradientStart, RoseGradientMid, Secondary)))
          .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Icon(imageVector = Icons.Filled.AlarmOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (isEnglish) "Gift reminder" else "Nhắc mua quà", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
      }
    }

    Surface(
      shape = RoundedCornerShape(InLoveRadii.InputSoft),
      color = Color.White.copy(alpha = 0.96f),
      border = BorderStroke(1.dp, InLoveColors.BorderSoftPink),
      shadowElevation = 1.dp,
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = MinTouchBox)
        .clip(RoundedCornerShape(InLoveRadii.InputSoft))
        .clickable(onClick = onOpenWishlist)
        .testTag("btn_preferences_notebook")
    ) {
      Row(modifier = Modifier.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(imageVector = Icons.Filled.BookmarkBorder, contentDescription = null, tint = TextSubtlePlum, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (isEnglish) "Wishlist" else "Yêu thích", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
      }
    }
  }
}
