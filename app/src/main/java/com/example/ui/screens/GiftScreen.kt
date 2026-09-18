@file:Suppress("FunctionName")
package com.example.ui.screens

import com.example.ui.util.AppLanguage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NaturePeople
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ChecklistItemEntity
import com.example.data.model.GiftIdeaEntity
import com.example.data.model.GiftReminderEntity
import com.example.ui.theme.OnPrimaryFixed
import com.example.ui.theme.OnSurface
import com.example.ui.theme.OnSurfaceVariant
import com.example.ui.theme.OutlineVariant
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.PrimaryFixed
import com.example.ui.theme.RoseGradientEnd
import com.example.ui.theme.RoseGradientMid
import com.example.ui.theme.RoseGradientStart
import com.example.ui.theme.Secondary
import com.example.ui.theme.SecondaryContainer
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
  val checklistItems by viewModel.checklistItems.collectAsState()
  val giftReminders by viewModel.giftReminders.collectAsState()
  val selectedCategory by viewModel.giftCategory.collectAsState()
  val mutualInterests by viewModel.mutualInterests.collectAsState()
  val partnerUser by viewModel.partnerOnlineUser.collectAsState()
  val relationshipStatus by viewModel.relationshipStatus.collectAsState()
  val appLanguage by viewModel.appLanguage.collectAsState()
  val isEnglish = appLanguage == AppLanguage.EN

  var giftItemToDelete by remember { mutableStateOf<ChecklistItemEntity?>(null) }
  var reminderToDelete by remember { mutableStateOf<GiftReminderEntity?>(null) }

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
  val progressPercent = if (totalCount > 0) (completedCount.toFloat() / totalCount.toFloat()) * 100f else 75f

  val filteredIdeas = remember(giftIdeas, selectedCategory) {
    when (selectedCategory) {
      "Tất cả", "All" -> giftIdeas
      "AI Đề Xuất ✨", "AI Suggestions ✨" -> giftIdeas.filter { it.isAiGenerated }
      else -> giftIdeas.filter { it.category.contains(selectedCategory, ignoreCase = true) }
    }
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

    // Mutual Interests Recommendation (interests_A ∩ interests_B)
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
                text = if (isEnglish) "Suggestions by Mutual Interests" else "Gợi Ý Theo Sở Thích Chung",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF880E4F)
              )
            }

            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFFFFEBEE)
            ) {
              Text(
                text = "interests_A ∩ B",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFC2185B),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

            // Suggestions based on enriched gift ideas from Cloud Firestore
            val suggestions = remember(giftIdeas) {
              if (giftIdeas.isNotEmpty()) {
                giftIdeas.take(3).map { it.title to it.category }
              } else {
                listOf(
                  "Ý tưởng hẹn hò lãng mạn" to "Địa điểm",
                  "Món quà kỷ vật tình yêu" to "Kỷ niệm"
                )
              }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              suggestions.forEach { (title, tag) ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.Favorite,
                      contentDescription = null,
                      tint = Color(0xFFFF4081),
                      modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                  }
                  Text(text = tag, fontSize = 10.sp, color = Color.Gray)
                }
              }
            }
          } else {
            Text(
              text = if (isEnglish) "Choose interests on the Pairing screen for automatic gift & date suggestions for both of you!" else "Hãy cùng chọn sở thích ở trang Ghép Đôi để hệ thống tự động gợi ý quà và lịch hẹn lý tưởng cho cả hai!",
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
              Text(if (isEnglish) "🤖 AI Assistant: Gift Suggestions" else "🤖 Trợ Lý AI Gợi Ý Quà Cho Đôi Ta", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
          }
        }
      }
    }

    // 2. Category Filter Tabs (Horizontal Scroll)
    item {
      val categories = if (isEnglish) {
        listOf(
          "All",
          "AI Suggestions ✨",
          "Romantic Gifts",
          "Jewelry & Perfume",
          "Handmade Keepsakes",
          "Date Locations",
          "Secret Surprises"
        )
      } else {
        listOf(
          "Tất cả",
          "AI Đề Xuất ✨",
          "Quà lãng mạn",
          "Trang sức & Nước hoa",
          "Kỷ vật Handmade",
          "Địa điểm hẹn hò",
          "Bất ngờ bí mật"
        )
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        categories.forEach { cat ->
          val isSelected = selectedCategory == cat
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
              .clickable { viewModel.setGiftCategory(cat) }
              .testTag("gift_tab_$cat")
          ) {
            Text(
              text = cat,
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
                  text = if (isEnglish) "VIP PROPOSAL BUNDLE" else "GÓI ĐỀ XUẤT VIP",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  letterSpacing = 0.5.sp
                )
              }
            }

            Surface(
              shape = RoundedCornerShape(20.dp),
              color = Color.Black.copy(alpha = 0.25f)
            ) {
              Text(
                text = if (isEnglish) "Most Popular" else "Được chọn nhiều nhất",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          Text(
            text = if (isEnglish) "1,000 Days Perfect Celebration Bundle" else "Gói Kỷ Niệm 1.000 Ngày Hoàn Hảo",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = if (isEnglish) "Preserved rose in crystal glass combined with candlelight rooftop dinner overlooking the whole city with handwritten love letter." else "Hoa hồng vĩnh cửu lồng kính pha lê kết hợp bữa tối Rooftop lung linh ánh nến ngắm toàn cảnh thành phố cùng thiệp thư tình viết tay.",
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
              Pair("Eternal Rose", Icons.Filled.LocalFlorist),
              Pair("Rooftop Table", Icons.Filled.DinnerDining),
              Pair("Handwritten Card", Icons.Filled.Mail)
            ) else listOf(
              Pair("Hoa Vĩnh Cửu", Icons.Filled.LocalFlorist),
              Pair("Bàn Rooftop", Icons.Filled.DinnerDining),
              Pair("Thiệp Viết Tay", Icons.Filled.Mail)
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
                text = if (isEnglish) "View perfect itinerary details" else "Xem chi tiết lịch trình hoàn hảo",
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

        // Secondary glass note button
        Surface(
          shape = RoundedCornerShape(50.dp),
          color = Color.White.copy(alpha = 0.9f),
          shadowElevation = 2.dp,
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              viewModel.showToast(if (isEnglish) "Opening partner's wishlist notebook..." else "Mở sổ tay sở thích của người ấy...")
            }
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
              text = if (isEnglish) "Partner's Wishlist Notebook" else "Lưu ý sổ tay sở thích của người ấy",
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
              color = OnSurface
            )
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
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("gift_idea_${idea.id}")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      // Photo with Badge & Heart button
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(170.dp)
          .clip(RoundedCornerShape(18.dp))
      ) {
        AsyncImage(
          model = idea.imageUrl,
          contentDescription = idea.title,
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
            val badgeIcon = when (idea.id) {
              1L -> Icons.Filled.Favorite
              2L -> Icons.Filled.AutoAwesome
              3L -> Icons.Filled.Restaurant
              else -> Icons.Filled.NaturePeople
            }
            Icon(
              imageVector = badgeIcon,
              contentDescription = null,
              tint = if (idea.id == 2L) Tertiary else Secondary,
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = idea.badgeText,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = if (idea.id == 2L) Tertiary else Secondary
            )
          }
        }

        // Heart Button Top Right
        IconButton(
          onClick = onFavoriteToggle,
          modifier = Modifier
            .padding(10.dp)
            .align(Alignment.TopEnd)
            .size(36.dp)
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
          text = idea.title,
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = OnSurface,
          modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = if (idea.id == 2L) SecondaryFixed.copy(alpha = 0.7f) else PrimaryFixed.copy(alpha = 0.6f)
        ) {
          Text(
            text = idea.tag,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (idea.id == 2L) Secondary else Primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = idea.description,
        fontSize = 12.sp,
        color = OnSurfaceVariant,
        lineHeight = 17.sp
      )

      Spacer(modifier = Modifier.height(10.dp))

      // Card Details Snippet Area
      when (idea.id) {
        1L -> {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Text(
                text = if (isEnglish) "PREPARATION CHECKLIST:" else "CHECKLIST CHUẨN BỊ:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Primary,
                letterSpacing = 0.5.sp
              )
              Spacer(modifier = Modifier.height(4.dp))
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(
                  imageVector = Icons.Filled.CheckCircle,
                  contentDescription = null,
                  tint = Primary,
                  modifier = Modifier.size(14.dp)
                )
                Text(
                  text = if (isEnglish) "Print 20 best couple photos" else "In 20 tấm ảnh đôi đẹp nhất của 2 đứa",
                  fontSize = 11.sp,
                  color = OnSurfaceVariant
                )
              }
              Spacer(modifier = Modifier.height(2.dp))
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(
                  imageVector = Icons.Filled.CheckCircle,
                  contentDescription = null,
                  tint = Primary,
                  modifier = Modifier.size(14.dp)
                )
                Text(
                  text = if (isEnglish) "Write wishes & memorable notes below each photo" else "Viết lời chúc & kỷ niệm đáng nhớ dưới mỗi ảnh",
                  fontSize = 11.sp,
                  color = OnSurfaceVariant
                )
              }
            }
          }
        }
        2L -> {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(
                  imageVector = Icons.Filled.Schedule,
                  contentDescription = null,
                  tint = Primary,
                  modifier = Modifier.size(18.dp)
                )
                Column {
                  Text(
                    text = if (isEnglish) "Engraving & delivery time:" else "Thời gian khắc & giao:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                  )
                  Text(
                    text = if (isEnglish) "Around 1 - 2 days (Order soon)" else "Khoảng 1 - 2 ngày (Nên đặt ngay)",
                    fontSize = 11.sp,
                    color = OnSurfaceVariant
                  )
                }
              }
              Icon(
                imageVector = Icons.Filled.Verified,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }
        3L -> {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Text(
                text = if (isEnglish) "SUGGESTED MENU:" else "THỰC ĐƠN GỢI Ý:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Primary,
                letterSpacing = 0.5.sp
              )
              Spacer(modifier = Modifier.height(4.dp))
              Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
              ) {
                val menuItems = if (isEnglish) listOf("🥩 Tenderloin Steak", "🍷 Rosé Wine", "🎂 Heart Cake") else listOf("🥩 Bò Steak Thăn Nội", "🍷 Rượu Vang Hồng", "🎂 Bánh Kem Trái Tim")
                menuItems.forEach { item ->
                  Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    shadowElevation = 1.dp
                  ) {
                    Text(
                      text = item,
                      fontSize = 11.sp,
                      color = OnSurface,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                  }
                }
              }
            }
          }
        }
        4L -> {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 2.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.LocationOn,
              contentDescription = null,
              tint = Secondary,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = if (isEnglish) "Suburban Pine Hill (45 mins drive from city center)" else "Đồi thông ngoại ô (cách trung tâm 45 phút lái xe)",
              fontSize = 11.sp,
              color = OnSurfaceVariant
            )
          }
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
          val actionIcon = when (idea.id) {
            1L -> Icons.Filled.PhotoLibrary
            2L -> Icons.Filled.Brush
            3L -> Icons.AutoMirrored.Filled.ReceiptLong
            else -> Icons.Filled.Map
          }
          Icon(
            imageVector = actionIcon,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = idea.actionText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Primary
          )
        }
      }
    }
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
            .size(28.dp)
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
            .size(28.dp)
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
        modifier = Modifier.size(32.dp)
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
