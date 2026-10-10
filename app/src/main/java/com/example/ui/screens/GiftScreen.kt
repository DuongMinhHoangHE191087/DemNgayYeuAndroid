@file:Suppress("FunctionName")
package com.example.ui.screens

import android.content.Intent
import com.example.ui.util.AppLanguage
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.NaturePeople
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.ChecklistItemEntity
import com.example.data.model.GiftIdeaEntity
import com.example.data.model.GiftReminderEntity
import com.example.domain.content.GiftCatalog
import com.example.domain.usecase.GetPersonalizedGiftSuggestionsUseCase
import com.example.ui.theme.OnPrimaryFixed
import com.example.ui.theme.OnSurface
import com.example.ui.theme.OnSurfaceVariant
import com.example.ui.theme.OutlineVariant
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.PrimaryFixed
import com.example.ui.theme.RoseGradientMid
import com.example.ui.theme.RoseGradientStart
import com.example.ui.theme.Secondary
import com.example.ui.theme.SecondaryFixed
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerHighest
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.theme.Tertiary
import com.example.ui.viewmodel.InLoveViewModel

@Composable
fun GiftScreen(
  viewModel: InLoveViewModel
) {
  val giftIdeas by viewModel.giftIdeas.collectAsState()
  val personalizedGifts by viewModel.personalizedGifts.collectAsState()
  val checklistItems by viewModel.checklistItems.collectAsState()
  val giftReminders by viewModel.giftReminders.collectAsState()
  val selectedCategory by viewModel.giftCategory.collectAsState()
  val mutualInterests by viewModel.mutualInterests.collectAsState()
  val partnerUser by viewModel.partnerOnlineUser.collectAsState()
  val relationshipStatus by viewModel.relationshipStatus.collectAsState()
  val appLanguage by viewModel.appLanguage.collectAsState()
  val isEnglish = appLanguage == AppLanguage.EN
  val coupleProfile by viewModel.coupleProfile.collectAsState()
  val context = LocalContext.current

  var giftItemToDelete by remember { mutableStateOf<ChecklistItemEntity?>(null) }
  var reminderToDelete by remember { mutableStateOf<GiftReminderEntity?>(null) }
  var showWishlistDialog by remember { mutableStateOf(false) }
  var sharePreview by remember { mutableStateOf<String?>(null) }

  // Xem trước rồi mới gửi: nội dung chỉ gồm tên quà và giá tham khảo, không kèm thông tin hồ sơ nào.
  // ponytail: chỉ Sharesheet của Android; bản ghi gợi ý riêng cho thành viên trong cặp (Firestore) làm sau khi có thời gian.
  sharePreview?.let { text ->
    AlertDialog(
      onDismissRequest = { sharePreview = null },
      title = { Text(if (isEnglish) "Share this suggestion?" else "Chia sẻ gợi ý này?", fontWeight = FontWeight.Bold) },
      text = { Text(text = text, fontSize = 14.sp) },
      confirmButton = {
        TextButton(
          onClick = {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
              type = "text/plain"
              putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(shareIntent, if (isEnglish) "Share suggestion via" else "Chia sẻ gợi ý qua"))
            sharePreview = null
          },
          modifier = Modifier.heightIn(min = 48.dp).testTag("btn_confirm_share_gift")
        ) { Text(if (isEnglish) "Share" else "Chia sẻ") }
      },
      dismissButton = {
        TextButton(
          onClick = { sharePreview = null },
          modifier = Modifier.heightIn(min = 48.dp)
        ) { Text(if (isEnglish) "Cancel" else "Hủy") }
      }
    )
  }

  reminderToDelete?.let { reminder ->
    DeleteConfirmationDialog(
      title = if (isEnglish) "Delete Gift Reminder?" else "Xóa lời nhắc quà tặng?",
      message = if (isEnglish) "Are you sure you want to delete reminder '${reminder.title}' from the database?" else "Bạn có chắc chắn muốn xóa lời nhắc '${reminder.title}' khỏi cơ sở dữ liệu không?",
      itemName = reminder.title,
      onConfirm = {
        viewModel.deleteGiftReminder(reminder.id)
        reminderToDelete = null
      },
      onDismiss = {
        reminderToDelete = null
      }
    )
  }

  giftItemToDelete?.let { item ->
    DeleteConfirmationDialog(
      title = if (isEnglish) "Delete Checklist Item?" else "Xóa việc chuẩn bị quà?",
      message = if (isEnglish) "Are you sure you want to delete this item from the gift checklist?" else "Bạn có chắc chắn muốn xóa mục này khỏi checklist quà tặng không?",
      itemName = item.text,
      onConfirm = {
        viewModel.deleteChecklistItem(item.id)
        giftItemToDelete = null
      },
      onDismiss = {
        giftItemToDelete = null
      }
    )
  }

  val completedCount = checklistItems.count { it.isCompleted }
  val totalCount = checklistItems.size
  val progressPercent = if (totalCount > 0) (completedCount.toFloat() / totalCount.toFloat()) * 100f else 0f

  val filteredIdeas = remember(giftIdeas, selectedCategory) {
    GiftCatalog.filterByChip(giftIdeas, selectedCategory)
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 1. Ambient Banner Card
    item {
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
          containerColor = Color.White.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .background(
              Brush.linearGradient(
                listOf(
                  SurfaceContainerHigh.copy(alpha = 0.8f),
                  SurfaceContainerLow.copy(alpha = 0.8f)
                )
              )
            )
            .padding(16.dp)
        ) {
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = PrimaryFixed.copy(alpha = 0.8f)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(14.dp)
              )
              Text(
                text = if (isEnglish) "SPECIAL ANNIVERSARY COMING" else "SẮP ĐẾN KỶ NIỆM ĐẶC BIỆT",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = OnPrimaryFixed,
                letterSpacing = 0.5.sp
              )
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          Text(
            text = if (isEnglish) "Gift & Surprise Ideas" else "Gợi Ý Quà & Bất Ngờ",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurface
          )

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = if (isEnglish) "1,000 days anniversary is coming in 3 days! Have you prepared anything special? 🎁" else "Kỷ niệm 1.000 ngày sắp đến trong 3 ngày nữa! Bạn đã chuẩn bị điều tuyệt vời gì chưa? 🎁",
            fontSize = 13.sp,
            color = OnSurfaceVariant,
            lineHeight = 18.sp
          )

          Spacer(modifier = Modifier.height(12.dp))

          // Progress Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Timer,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(15.dp)
              )
              Text(
                text = if (isEnglish) "Preparation Progress" else "Tiến độ chuẩn bị",
                fontSize = 12.sp,
                color = OnSurfaceVariant
              )
            }
            Text(
              text = if (isEnglish) "${progressPercent.toInt()}% Completed" else "${progressPercent.toInt()}% Hoàn tất",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = Primary
            )
          }

          Spacer(modifier = Modifier.height(6.dp))

          LinearProgressIndicator(
            progress = { progressPercent / 100f },
            modifier = Modifier
              .fillMaxWidth()
              .height(7.dp)
              .clip(RoundedCornerShape(50.dp)),
            color = Primary,
            trackColor = SurfaceContainerHighest,
            strokeCap = StrokeCap.Round
          )
        }
      }
    }

    // Personalized suggestions (shared interests + partner preferences + budget + upcoming occasion) with the preferences editor
    item {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7F9)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF80AB).copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().testTag("mutual_interests_gift_card")
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFFE91E63),
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (isEnglish) "Personalized suggestions" else "Gợi ý cá nhân hóa",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF880E4F)
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          if (mutualInterests.isNotEmpty()) {
            Text(
              text = if (isEnglish) "You both love: ${mutualInterests.joinToString(" • ")}" else "Hai bạn cùng yêu thích: ${mutualInterests.joinToString(" • ")}",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF4A148C)
            )
            Spacer(modifier = Modifier.height(8.dp))
          }

          if (personalizedGifts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              personalizedGifts.forEach { s ->
                PersonalizedGiftCard(
                  title = s.gift.title,
                  reason = s.reason,
                  preparation = s.preparation,
                  priceLabel = s.priceLabel,
                  onShare = { sharePreview = listOfNotNull(s.gift.title, s.priceLabel).joinToString("\n") },
                  shareLabel = if (isEnglish) "Share this suggestion" else "Chia sẻ gợi ý này"
                )
              }
            }
          } else {
            Text(
              text = if (isEnglish) "Choose interests on the Pairing screen, note what your partner likes below, or add your anniversary and birthdays, to see suggestions made for both of you. You can still browse the full catalog below." else "Hãy chọn sở thích ở trang Ghép Đôi, ghi lại điều đối tác thích ở bên dưới, hoặc nhập ngày yêu và ngày sinh, để xem gợi ý dành riêng cho hai bạn. Bạn vẫn có thể xem toàn bộ danh mục bên dưới.",
              fontSize = 12.sp,
              color = Color.Gray
            )
          }

          Spacer(modifier = Modifier.height(10.dp))
          androidx.compose.material3.Button(
            onClick = { viewModel.triggerAiGiftSuggestions() },
            shape = RoundedCornerShape(12.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
              containerColor = Color(0xFFC2185B)
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("btn_trigger_ai_gift_suggestions")
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(if (isEnglish) "✨ Add suggestions by your interests" else "✨ Gợi ý thêm theo sở thích", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
          }

          Spacer(modifier = Modifier.height(4.dp))
          PartnerPreferencesEditor(
            profileExists = coupleProfile != null,
            savedLikes = coupleProfile?.likesCsv.orEmpty(),
            savedBudgetVnd = coupleProfile?.budgetMaxVnd ?: 0L,
            savedRegion = coupleProfile?.occasionRegion.orEmpty(),
            isEnglish = isEnglish,
            onSave = viewModel::savePartnerPreferences
          )
        }
      }
    }

    // 2. Category Filter Tabs (Horizontal Scroll)
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        GiftCatalog.chips(isEnglish).forEach { chip ->
          val isSelected = selectedCategory == chip.key
          Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.85f),
            shadowElevation = if (isSelected) 4.dp else 1.dp,
            modifier = Modifier
              .clip(RoundedCornerShape(24.dp))
              .then(
                if (isSelected) {
                  Modifier.background(
                    Brush.horizontalGradient(
                      listOf(RoseGradientStart, RoseGradientMid)
                    )
                  )
                } else Modifier
              )
              .clickable { viewModel.setGiftCategory(chip.key) }
              .testTag("gift_tab_${chip.key}")
          ) {
            Text(
              text = chip.label,
              fontSize = 12.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) Color.White else OnSurfaceVariant,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
          }
        }
      }
    }

    // 3. VIP Featured Proposal Banner
    item {
      Card(
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("vip_proposal_banner")
      ) {
        Column(
          modifier = Modifier
            .background(
              Brush.linearGradient(
                listOf(Secondary, Primary, PrimaryContainer)
              )
            )
            .padding(18.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Surface(
              shape = RoundedCornerShape(20.dp),
              color = Color.White.copy(alpha = 0.25f)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(
                  imageVector = Icons.Filled.Star,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(13.dp)
                )
                Text(
                  text = if (isEnglish) "CELEBRATION IDEAS" else "GỢI Ý KỶ NIỆM",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  letterSpacing = 0.5.sp
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          Text(
            text = if (isEnglish) "1,000-Day Celebration Ideas" else "Ý Tưởng Kỷ Niệm 1.000 Ngày",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = if (isEnglish) "A few ideas for marking 1,000 days together: flowers, a shared dinner, and a note written by hand." else "Vài gợi ý để kỷ niệm 1.000 ngày bên nhau: hoa, bữa tối và một lời nhắn tự tay viết.",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.9f),
            lineHeight = 17.sp
          )

          Spacer(modifier = Modifier.height(12.dp))

          // 3 Highlights Box
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            val highlights = if (isEnglish) listOf(
              Pair("Roses", Icons.Filled.LocalFlorist),
              Pair("Dinner", Icons.Filled.DinnerDining),
              Pair("A Note", Icons.Filled.Mail)
            ) else listOf(
              Pair("Hoa Hồng", Icons.Filled.LocalFlorist),
              Pair("Bữa Tối", Icons.Filled.DinnerDining),
              Pair("Lời Nhắn", Icons.Filled.Mail)
            )

            highlights.forEach { (name, icon) ->
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.18f),
                modifier = Modifier.weight(1f)
              ) {
                Column(
                  modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                  horizontalAlignment = Alignment.CenterHorizontally
                ) {
                  Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          Surface(
            shape = RoundedCornerShape(50.dp),
            color = Color.White,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { viewModel.openVipProposal() }
              .testTag("btn_view_vip_proposal")
          ) {
            Row(
              modifier = Modifier.padding(vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              Text(
                text = if (isEnglish) "View itinerary ideas" else "Xem gợi ý lịch trình",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Primary
              )
              Spacer(modifier = Modifier.width(6.dp))
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }
    }

    // 4. Section Title
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Filled.Redeem,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(20.dp)
          )
          Text(
            text = if (isEnglish) "Gift Preparation Ideas" else "Ý Tưởng Chuẩn Bị Quà",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurface
          )
        }
        Text(
          text = if (isEnglish) "${filteredIdeas.size} matched ideas" else "${filteredIdeas.size} gợi ý phù hợp",
          fontSize = 12.sp,
          color = OnSurfaceVariant
        )
      }
    }

    // 5. Ideas Stream
    items(filteredIdeas, key = { it.id }) { idea ->
      GiftIdeaCard(
        idea = idea,
        isEnglish = isEnglish,
        onFavoriteToggle = { viewModel.toggleGiftFavorite(idea) },
        onActionClick = { viewModel.openGiftDetail(idea) }
      )
    }

    // 6. Interactive Checklist Section
    item {
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
          containerColor = SurfaceContainerLow.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("checklist_section")
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
                  .background(Primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.TaskAlt,
                  contentDescription = null,
                  tint = Primary,
                  modifier = Modifier.size(20.dp)
                )
              }
              Column {
                Text(
                  text = if (isEnglish) "Anniversary Gift Checklist" else "Checklist Quà Kỷ Niệm",
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold,
                  color = OnSurface
                )
                Text(
                  text = if (isEnglish) "Completed $completedCount/$totalCount tasks" else "Đã hoàn thành $completedCount/$totalCount việc",
                  fontSize = 11.sp,
                  color = OnSurfaceVariant
                )
              }
            }

            Text(
              text = if (isEnglish) "+ Add task" else "+ Thêm việc",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = Primary,
              modifier = Modifier
                .clickable { viewModel.openAddChecklistDialog() }
                .padding(4.dp)
                .testTag("btn_add_checklist_task")
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Task items
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            checklistItems.forEach { item ->
              ChecklistRow(
                item = item,
                isEnglish = isEnglish,
                onToggle = { viewModel.toggleChecklist(item) },
                onDelete = { giftItemToDelete = item }
              )
            }
          }
        }
      }
    }

    // 6b. Room-Persisted Gift Reminders Section
    item {
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
          containerColor = Color.White.copy(alpha = 0.92f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("gift_reminders_section")
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
                  imageVector = Icons.Filled.CardGiftcard,
                  contentDescription = null,
                  tint = Primary,
                  modifier = Modifier.size(20.dp)
                )
              }
              Column {
                Text(
                  text = if (isEnglish) "Saved Gift Reminders" else "Lời Nhắc Quà Tặng Đã Lưu",
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold,
                  color = OnSurface
                )
                val completedReminders = giftReminders.count { it.isCompleted }
                Text(
                  text = if (isEnglish) "Stored in Room: $completedReminders/${giftReminders.size} items ready" else "Lưu Room: $completedReminders/${giftReminders.size} món đã sẵn sàng",
                  fontSize = 11.sp,
                  color = OnSurfaceVariant
                )
              }
            }

            Surface(
              shape = RoundedCornerShape(20.dp),
              color = Primary.copy(alpha = 0.1f),
              modifier = Modifier
                .clickable { viewModel.openAddGiftReminderDialog() }
                .padding(4.dp)
                .testTag("btn_add_gift_reminder_header")
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
                  text = if (isEnglish) "Add reminder" else "Thêm nhắc quà",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = Primary
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          if (giftReminders.isEmpty()) {
            Text(
              text = if (isEnglish) "No gift reminders yet. Tap 'Add reminder' to schedule a thoughtful gift!" else "Chưa có lời nhắc quà tặng. Nhấn 'Thêm nhắc quà' để tạo lời nhắc chuẩn bị món quà ý nghĩa!",
              fontSize = 12.sp,
              color = OnSurfaceVariant,
              modifier = Modifier.padding(vertical = 8.dp)
            )
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              giftReminders.forEach { reminder ->
                GiftReminderRow(
                  item = reminder,
                  isEnglish = isEnglish,
                  onToggle = { viewModel.toggleGiftReminderCompleted(reminder) },
                  onDelete = { reminderToDelete = reminder }
                )
              }
            }
          }
        }
      }
    }

    // 7. Dual Bottom Action Buttons
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Gradient alarm button
        Surface(
          shape = RoundedCornerShape(50.dp),
          shadowElevation = 6.dp,
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50.dp))
            .background(
              Brush.horizontalGradient(
                listOf(RoseGradientStart, RoseGradientMid, Secondary)
              )
            )
            .clickable {
              viewModel.openSetAlarmDialog(
                title = if (isEnglish) "Gift Preparation Reminder 🎁" else "Nhắc nhở chuẩn bị quà tặng 🎁",
                message = if (isEnglish) "Don't forget to prepare a meaningful gift for your partner!" else "Đừng quên chuẩn bị món quà ý nghĩa tặng người ấy nhé!"
              )
            }
            .testTag("btn_reminder_alarm")
        ) {
          Row(
            modifier = Modifier
              .background(
                Brush.horizontalGradient(
                  listOf(RoseGradientStart, RoseGradientMid, Secondary)
                )
              )
              .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = Icons.Filled.AlarmOn,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (isEnglish) "Set gift reminder (In 2 hours)" else "Tạo nhắc nhở mua quà (Sau 2 tiếng)",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }
        }

        // Secondary glass note button — opens the real favorited-ideas list (WishlistNotebookDialog)
        Surface(
          shape = RoundedCornerShape(50.dp),
          color = Color.White.copy(alpha = 0.9f),
          shadowElevation = 2.dp,
          modifier = Modifier
            .fillMaxWidth()
            .clickable { showWishlistDialog = true }
            .testTag("btn_preferences_notebook")
        ) {
          Row(
            modifier = Modifier.padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = Icons.Filled.BookmarkBorder,
              contentDescription = null,
              tint = Secondary,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (isEnglish) "My Wishlist Notebook" else "Sổ Tay Yêu Thích Của Tôi",
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
              color = OnSurface
            )
          }
        }
      }
    }
  }

  if (showWishlistDialog) {
    WishlistNotebookDialog(
      giftIdeas = giftIdeas,
      isEnglish = isEnglish,
      onFavoriteToggle = { viewModel.toggleGiftFavorite(it) },
      onItemClick = { viewModel.openGiftDetail(it) },
      onDismiss = { showWishlistDialog = false }
    )
  }
}

// Shows the items *I've* favorited — gift ideas/favorites are plain local Room state, never
// synced to Firestore (unlike memories/anniversaries/pairing), so this cannot actually reflect
// what a partner favorited on their own device. Framed honestly as "my wishlist", not "partner's".
@Composable
fun WishlistNotebookDialog(
  giftIdeas: List<GiftIdeaEntity>,
  isEnglish: Boolean,
  onFavoriteToggle: (GiftIdeaEntity) -> Unit,
  onItemClick: (GiftIdeaEntity) -> Unit,
  onDismiss: () -> Unit
) {
  val favorites = remember(giftIdeas) { giftIdeas.filter { it.isFavorited } }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(28.dp),
      color = Color.White,
      shadowElevation = 16.dp,
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.85f)
    ) {
      Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.BookmarkBorder,
              contentDescription = null,
              tint = Secondary,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = if (isEnglish) "My Wishlist Notebook" else "Sổ Tay Yêu Thích Của Tôi",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface
              )
              Text(
                text = if (isEnglish) "${favorites.size} saved ideas" else "${favorites.size} ý tưởng đã lưu",
                fontSize = 11.sp,
                color = OnSurfaceVariant
              )
            }
          }
          IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = if (isEnglish) "Close" else "Đóng")
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (favorites.isEmpty()) {
          Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = Icons.Filled.BookmarkBorder,
              contentDescription = null,
              tint = OnSurfaceVariant.copy(alpha = 0.4f),
              modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = if (isEnglish) "No favorites yet" else "Chưa có ý tưởng yêu thích nào",
              fontWeight = FontWeight.SemiBold,
              color = OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = if (isEnglish) "Tap the heart on any gift idea to save it here." else "Nhấn vào biểu tượng trái tim trên món quà để lưu vào đây.",
              fontSize = 12.sp,
              color = OnSurfaceVariant,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 24.dp)
            )
          }
        } else {
          LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(favorites, key = { it.id }) { idea ->
              GiftIdeaCard(
                idea = idea,
                isEnglish = isEnglish,
                onFavoriteToggle = { onFavoriteToggle(idea) },
                onActionClick = { onItemClick(idea) }
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun GiftIdeaCard(
  idea: GiftIdeaEntity,
  isEnglish: Boolean = false,
  onFavoriteToggle: () -> Unit,
  onActionClick: () -> Unit
) {
  val gift = GiftCatalog.localized(idea, isEnglish)
  val accent = if (idea.isAiGenerated) Tertiary else Secondary

  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("gift_idea_${idea.id}")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      // Photo with Badge, Illustration Caption & Heart button
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(170.dp)
          .clip(RoundedCornerShape(18.dp))
      ) {
        AsyncImage(
          model = idea.imageUrl,
          contentDescription = gift.title,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )

        // Badge Pill Top Left
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = Color.White.copy(alpha = 0.85f),
          modifier = Modifier
            .padding(10.dp)
            .align(Alignment.TopStart)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = giftCategoryIcon(gift.categoryKey),
              contentDescription = null,
              tint = accent,
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = gift.badge,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = accent
            )
          }
        }

        // Seed photos are stock illustrations, not the gift itself
        if (gift.isSeed) {
          IllustrationCaption(
            isEnglish = isEnglish,
            modifier = Modifier
              .padding(10.dp)
              .align(Alignment.BottomStart)
          )
        }

        // Heart Button Top Right — 48dp touch target (AGENTS.md minimum), not the old 36dp.
        IconButton(
          onClick = onFavoriteToggle,
          modifier = Modifier
            .padding(2.dp)
            .align(Alignment.TopEnd)
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.85f))
        ) {
          Icon(
            imageVector = if (idea.isFavorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = "Favorite",
            tint = Primary,
            modifier = Modifier.size(18.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Title & Tag
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Text(
          text = gift.title,
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = OnSurface,
          modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = if (idea.isAiGenerated) SecondaryFixed.copy(alpha = 0.7f) else PrimaryFixed.copy(alpha = 0.6f)
        ) {
          Text(
            text = gift.tag,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (idea.isAiGenerated) Secondary else Primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = gift.description,
        fontSize = 12.sp,
        color = OnSurfaceVariant,
        lineHeight = 17.sp
      )

      GiftCatalog.priceLabel(idea.priceRange, isEnglish)?.let { price ->
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = price,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = Primary
        )
      }

      if (gift.detailsSnippet.isNotBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = SurfaceContainerLow,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = gift.detailsSnippet,
            fontSize = 11.sp,
            color = OnSurfaceVariant,
            lineHeight = 16.sp,
            modifier = Modifier.padding(10.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Action Button
      Surface(
        shape = RoundedCornerShape(50.dp),
        color = SurfaceContainer,
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onActionClick() }
      ) {
        Row(
          modifier = Modifier.padding(vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = giftCategoryIcon(gift.categoryKey),
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = gift.actionText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Primary
          )
        }
      }
    }
  }
}

/** Icon for a gift's category; the Room id is not a content key. */
private fun giftCategoryIcon(categoryKey: String) = when (categoryKey) {
  GiftCatalog.ROMANTIC -> Icons.Filled.Favorite
  GiftCatalog.JEWELRY -> Icons.Filled.AutoAwesome
  GiftCatalog.HANDMADE -> Icons.Filled.Brush
  GiftCatalog.DATES -> Icons.Filled.Restaurant
  GiftCatalog.SECRET -> Icons.Filled.CardGiftcard
  else -> Icons.Filled.NaturePeople
}

/**
 * Một gợi ý cá nhân hóa: tên quà, lý do, ghi chú chuẩn bị và giá tham khảo. Không nhận ViewModel để xem trước được.
 * [onShare] khác null thì hiện nút chia sẻ (đích tới ≥48dp); thẻ chỉ báo sự kiện, bản xem trước và Sharesheet do màn hình lo.
 */
@Composable
internal fun PersonalizedGiftCard(
  title: String,
  reason: String,
  preparation: String,
  priceLabel: String?,
  modifier: Modifier = Modifier,
  onShare: (() -> Unit)? = null,
  shareLabel: String = ""
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(Color.White, RoundedCornerShape(10.dp))
      .heightIn(min = 48.dp)
      .padding(horizontal = 10.dp, vertical = 8.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Default.Favorite,
        contentDescription = null,
        tint = Color(0xFFFF4081),
        modifier = Modifier.size(14.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
      if (onShare != null) {
        IconButton(
          onClick = onShare,
          modifier = Modifier
            .size(48.dp)
            .testTag("btn_share_gift_suggestion")
        ) {
          Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = shareLabel,
            tint = Color(0xFFC2185B),
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }
    Text(text = reason, fontSize = 12.sp, color = Color(0xFF4A148C), modifier = Modifier.padding(top = 2.dp))
    Text(text = preparation, fontSize = 12.sp, color = Color.DarkGray)
    if (priceLabel != null) {
      Text(text = priceLabel, fontSize = 11.sp, color = Color.Gray)
    }
  }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun PersonalizedGiftCardPreview() {
  PersonalizedGiftCard(
    title = "Bó hoa hồng bất ngờ",
    reason = "Hợp sở thích chung của hai bạn và dịp sắp tới.",
    preparation = "Còn khoảng hai tuần, đủ thời gian đặt và gói quà.",
    priceLabel = "Giá tham khảo: 200.000–500.000 ₫"
  )
}

/**
 * Ghi lại điều đối tác thích, ngân sách quà (VND) và vùng dịp lễ để gợi ý sát hơn. Lưu vào hồ sơ cặp đôi trên máy này,
 * nên cặp chưa ghép đôi vẫn dùng được. Hàng hồ sơ chỉ được cập nhật chứ không tạo ở đây: chưa có hồ sơ thì nút Lưu bị khoá kèm lời nhắc.
 *
 * ponytail: sở thích để trống ban đầu (không gieo sẵn); ngân sách 0/trống = không giới hạn; vùng chỉ VN/INTL (bấm lại chip để bỏ chọn)
 * và độc lập với ngôn ngữ giao diện. Chưa kiểm tra trùng/chính tả sở thích; thêm gợi ý chọn nhanh khi cần.
 */
@Composable
private fun PartnerPreferencesEditor(
  profileExists: Boolean,
  savedLikes: String,
  savedBudgetVnd: Long,
  savedRegion: String,
  isEnglish: Boolean,
  onSave: (String, Long, String) -> Unit
) {
  var expanded by remember { mutableStateOf(false) }
  var likes by remember(savedLikes) { mutableStateOf(savedLikes) }
  var budgetText by remember(savedBudgetVnd) { mutableStateOf(if (savedBudgetVnd > 0L) savedBudgetVnd.toString() else "") }
  var region by remember(savedRegion) { mutableStateOf(savedRegion) }

  Column(modifier = Modifier.fillMaxWidth()) {
    TextButton(
      onClick = { expanded = !expanded },
      modifier = Modifier
        .heightIn(min = 48.dp)
        .testTag("btn_toggle_partner_preferences")
    ) {
      Text(
        text = if (isEnglish) "Partner likes, budget and holidays" else "Sở thích đối tác, ngân sách và dịp lễ",
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFC2185B)
      )
    }
    if (expanded) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = likes,
          onValueChange = { likes = it.take(200) },
          label = { Text(if (isEnglish) "What your partner likes" else "Điều đối tác thích") },
          placeholder = { Text(if (isEnglish) "e.g. coffee, travel, books (comma-separated)" else "VD: cà phê, du lịch, sách (cách nhau bằng dấu phẩy)") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("partner_likes_field")
        )
        OutlinedTextField(
          value = budgetText,
          onValueChange = { budgetText = it.filter(Char::isDigit).take(12) },
          label = { Text(if (isEnglish) "Gift budget (VND)" else "Ngân sách quà (VND)") },
          supportingText = { Text(if (isEnglish) "Leave empty or 0 for no limit" else "Để trống hoặc 0 nếu không giới hạn") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("partner_budget_field")
        )
        Text(
          text = if (isEnglish) "Holiday region (independent of the app language)" else "Vùng dịp lễ (không phụ thuộc ngôn ngữ ứng dụng)",
          fontSize = 12.sp,
          color = Color.Gray
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(
            Triple(GetPersonalizedGiftSuggestionsUseCase.REGION_VN, if (isEnglish) "Vietnam" else "Việt Nam", "partner_region_vn"),
            Triple(GetPersonalizedGiftSuggestionsUseCase.REGION_INTL, if (isEnglish) "International" else "Quốc tế", "partner_region_intl")
          ).forEach { (key, label, tag) ->
            val isSelected = region == key
            FilterChip(
              selected = isSelected,
              onClick = { region = if (isSelected) "" else key },
              label = { Text(text = label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFFC2185B),
                selectedLabelColor = Color.White,
                containerColor = Color(0xFFFFF5F8),
                labelColor = Color(0xFF6B2B50)
              ),
              border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = isSelected,
                borderColor = if (isSelected) Color(0xFFC2185B) else Color(0xFFFFD1DC)
              ),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.testTag(tag)
            )
          }
        }
        if (!profileExists) {
          Text(
            text = if (isEnglish) "Create your couple profile first (names and dates) to save these notes." else "Hãy tạo hồ sơ cặp đôi (tên và ngày) trước để lưu được các ghi chú này.",
            fontSize = 11.sp,
            color = Color.Gray
          )
        }
        androidx.compose.material3.Button(
          onClick = {
            onSave(likes.trim(), budgetText.toLongOrNull() ?: 0L, region)
            expanded = false
          },
          enabled = profileExists,
          shape = RoundedCornerShape(12.dp),
          colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = Color(0xFFC2185B),
            contentColor = Color.White
          ),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("btn_save_partner_preferences")
        ) {
          Text(if (isEnglish) "Save" else "Lưu", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
internal fun IllustrationCaption(isEnglish: Boolean, modifier: Modifier = Modifier) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = Color.Black.copy(alpha = 0.35f),
    modifier = modifier
  ) {
    Text(
      text = if (isEnglish) "Illustration" else "Ảnh minh họa",
      fontSize = 10.sp,
      color = Color.White,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
    )
  }
}

@Composable
fun ChecklistRow(
  item: ChecklistItemEntity,
  isEnglish: Boolean = false,
  onToggle: () -> Unit,
  onDelete: () -> Unit
) {
  val iconVector = when (item.iconName) {
    "cake" -> Icons.Filled.Cake
    "history_edu" -> Icons.Filled.HistoryEdu
    "table_restaurant" -> Icons.Filled.TableRestaurant
    else -> Icons.Filled.Checklist
  }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = Color.White.copy(alpha = 0.8f),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("checklist_item_${item.id}")
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .weight(1f)
          .clickable { onToggle() }
      ) {
        // Check circle
        Box(
          modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (item.isCompleted) Primary else SurfaceContainerHighest),
          contentAlignment = Alignment.Center
        ) {
          if (item.isCompleted) {
            Icon(
              imageVector = Icons.Filled.Check,
              contentDescription = "Completed",
              tint = Color.White,
              modifier = Modifier.size(16.dp)
            )
          }
        }

        Text(
          text = item.text,
          fontSize = 13.sp,
          color = if (item.isCompleted) OnSurface.copy(alpha = 0.5f) else OnSurface,
          textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Icon(
          imageVector = iconVector,
          contentDescription = null,
          tint = OutlineVariant,
          modifier = Modifier.size(18.dp)
        )

        IconButton(
          onClick = onDelete,
          modifier = Modifier
            .size(48.dp)
            .testTag("btn_delete_checklist_${item.id}")
        ) {
          Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = if (isEnglish) "Delete task" else "Xóa việc",
            tint = OutlineVariant,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}

@Composable
fun GiftReminderRow(
  item: GiftReminderEntity,
  isEnglish: Boolean = false,
  onToggle: () -> Unit,
  onDelete: () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = if (item.isCompleted) SurfaceContainerHighest.copy(alpha = 0.4f) else SurfaceContainerLowest,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("gift_reminder_item_${item.id}")
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
        IconButton(
          onClick = onToggle,
          modifier = Modifier
            .size(48.dp)
            .testTag("btn_toggle_gift_reminder_${item.id}")
        ) {
          Icon(
            imageVector = if (item.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.TaskAlt,
            contentDescription = if (isEnglish) "Completed" else "Hoàn thành",
            tint = if (item.isCompleted) Primary else OnSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp)
          )
        }

        Column {
          Text(
            text = item.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (item.isCompleted) OnSurfaceVariant else OnSurface,
            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = if (isEnglish) "To: ${item.recipient}" else "Tặng: ${item.recipient}",
              fontSize = 11.sp,
              color = Primary,
              fontWeight = FontWeight.Medium
            )
            Text(
              text = "• ${item.dueDateText}",
              fontSize = 11.sp,
              color = OnSurfaceVariant
            )
            if (item.estimatedBudget.isNotBlank()) {
              Text(
                text = "• ${item.estimatedBudget}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Secondary
              )
            }
          }
          if (item.notes.isNotBlank()) {
            Text(
              text = item.notes,
              fontSize = 11.sp,
              color = OnSurfaceVariant.copy(alpha = 0.8f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier.size(48.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.Close,
          contentDescription = if (isEnglish) "Delete gift reminder" else "Xóa lời nhắc quà",
          tint = OnSurfaceVariant.copy(alpha = 0.6f),
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}
