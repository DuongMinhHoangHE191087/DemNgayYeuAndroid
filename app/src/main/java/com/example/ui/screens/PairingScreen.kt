@file:Suppress("FunctionName")
package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.example.ui.components.InLoveDatePickerField
import com.example.ui.components.DatePickerPresets
import com.example.ui.components.animateDialogEntrance
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.OnlineStatus
import com.example.data.model.RelationshipStatus
import androidx.compose.material.icons.filled.HeartBroken
import com.example.data.repository.OnlineCoupleRepository
import com.example.ui.util.ProfileUtils
import com.example.ui.viewmodel.InLoveViewModel

@Composable
private fun pairingTextFieldColors() = OutlinedTextFieldDefaults.colors(
  focusedTextColor = Color(0xFF1E1E24),
  unfocusedTextColor = Color(0xFF1E1E24),
  focusedContainerColor = Color.White,
  unfocusedContainerColor = Color.White,
  disabledContainerColor = Color(0xFFF5F5F7),
  focusedBorderColor = Color(0xFFE91E63),
  unfocusedBorderColor = Color(0xFFC7C7CC),
  focusedLabelColor = Color(0xFFE91E63),
  unfocusedLabelColor = Color(0xFF424242),
  focusedPlaceholderColor = Color(0xFF757575),
  unfocusedPlaceholderColor = Color(0xFF9E9E9E),
  cursorColor = Color(0xFFE91E63)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PairingScreen(
  viewModel: InLoveViewModel,
  onNavigateBack: () -> Unit = {}
) {
  val context = LocalContext.current
  val currentUser by viewModel.currentOnlineUser.collectAsState()
  val partnerUser by viewModel.partnerOnlineUser.collectAsState()
  val relationshipStatus by viewModel.relationshipStatus.collectAsState()
  val incomingInvite by viewModel.incomingInvite.collectAsState()
  val outgoingInvite by viewModel.outgoingInvite.collectAsState()
  val mutualInterests by viewModel.mutualInterests.collectAsState()
  val appLanguage by viewModel.appLanguage.collectAsState()
  val isEnglish = appLanguage == com.example.ui.util.AppLanguage.EN

  // Search & Inspection State
  val searchQuery by viewModel.searchQuery.collectAsState()
  val searchedUser by viewModel.searchedUser.collectAsState()
  val isSearching by viewModel.isSearching.collectAsState()
  val selectedInviteForVerification by viewModel.selectedInviteForVerification.collectAsState()
  val showEditProfileDialog by viewModel.showEditProfileDialog.collectAsState()

  val todayFormatted = remember { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date()) }
  var proposedStartDateText by remember { mutableStateOf(todayFormatted) }
  var loveNoteInput by remember { mutableStateOf(if (isEnglish) "Let's build our sweet happiness together! 💕" else "Cùng anh/em xây dựng hạnh phúc Set Love nhé! 💕") }

  val calculatedDaysFromProposed by remember(proposedStartDateText) {
    derivedStateOf {
      val parsed = ProfileUtils.parseDateToMillis(proposedStartDateText)
      ProfileUtils.calculateLoveDays(parsed)
    }
  }

  fun copyToClipboard(text: String, label: String = "Couple Code") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    @Suppress("UsePropertyAccessSyntax")
    clipboard.setPrimaryClip(clip)
    viewModel.showToast(if (isEnglish) "Copied: $text" else "Đã sao chép: $text")
  }

  fun shareCoupleLink(code: String) {
    val shareLink = ProfileUtils.createShareLink(code)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(
        Intent.EXTRA_TEXT,
        if (isEnglish) "Connect 1-1 with me on InLove! Open link or enter code:\n$shareLink\nCouple code: $code ❤️"
        else "Cùng kết nối Set Love 1-1 với mình trên InLove nhé! Bấm vào link hoặc nhập mã:\n$shareLink\nMã ghép đôi: $code ❤️"
      )
    }
    context.startActivity(Intent.createChooser(shareIntent, if (isEnglish) "Share couple code via" else "Chia sẻ mã/link ghép đôi qua"))
  }

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("btn_pairing_back")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = if (isEnglish) "Back" else "Quay lại"
            )
          }
        },
        title = {
          Text(
            text = if (isEnglish) "Pairing" else "Ghép đôi",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
          )
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    }
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      contentPadding = PaddingValues(vertical = 16.dp)
    ) {
      // 1. INCOMING INVITATION CARD (LỜI MỜI SET LOVE TỪ NGƯỜI KHÁC - THÔNG TIN KỶ NIỆM THỐNG NHẤT)
      incomingInvite?.let { invite ->
        item {
          Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F5)),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF4081)),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("incoming_invite_card")
          ) {
            Column(
              modifier = Modifier.padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Favorite,
                  contentDescription = null,
                  tint = Color(0xFFE91E63),
                  modifier = Modifier.size(24.dp)
                )
                Text(
                  text = if (isEnglish) "Pairing invite" else "Lời mời ghép đôi",
                  fontWeight = FontWeight.Bold,
                  fontSize = 17.sp,
                  color = Color(0xFFD81B60)
                )
              }

              // Thông tin người đó: CHỈ ĐỌC, KHÔNG THỂ ĐIỀU CHỈNH
              Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                  model = invite.senderAvatar.ifEmpty {
                    "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200"
                  },
                  contentDescription = "Sender Avatar",
                  modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color(0xFFFF80AB), CircleShape),
                  contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = invite.effectiveSenderName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF880E4F)
                  )
                  Text(
                    text = "Mã: " + invite.senderCoupleCode,
                    fontSize = 13.sp,
                    color = Color.Gray
                  )
                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFFEBEE),
                    modifier = Modifier.padding(top = 2.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Lock,
                      contentDescription = if (isEnglish) "Partner profile, read only" else "Hồ sơ đối tác, chỉ đọc",
                      tint = Color(0xFFC2185B),
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).size(14.dp)
                    )
                  }
                }
              }

              // Thông tin kỷ niệm yêu thống nhất từ người tạo
              val incomingDateText = invite.proposedStartDateText.ifEmpty {
                ProfileUtils.formatDate(invite.proposedStartDate)
              }
              val incomingDays = ProfileUtils.calculateLoveDays(invite.proposedStartDate)

              Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCDD2)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier.padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = if (isEnglish) "Start date" else "Ngày bắt đầu", tint = Color(0xFFC2185B), modifier = Modifier.size(18.dp))
                    Text(text = incomingDateText, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC2185B))
                  }
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Icon(Icons.Default.Favorite, contentDescription = if (isEnglish) "Days together" else "Ngày bên nhau", tint = Color(0xFFD81B60), modifier = Modifier.size(18.dp))
                    Text(text = if (isEnglish) "$incomingDays days" else "$incomingDays ngày", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFD81B60))
                  }
                  if (invite.loveNote.isNotBlank()) {
                    Text(
                      text = "“${invite.loveNote}”",
                      fontSize = 12.sp,
                      fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                      color = Color(0xFF4A148C)
                    )
                  }
                }
              }

              // Nút hành động: Từ chối hoặc Đồng ý
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                OutlinedButton(
                  onClick = { viewModel.rejectSetLoveInvite(invite.inviteId) },
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.weight(1f).height(48.dp).testTag("reject_invite_button")
                ) {
                  Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Từ chối", fontSize = 14.sp)
                }

                Button(
                  onClick = { viewModel.acceptSetLoveInvite(invite.inviteId) },
                  shape = RoundedCornerShape(14.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                  modifier = Modifier.weight(1.3f).height(48.dp).testTag("accept_invite_button")
                ) {
                  Icon(imageVector = Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(if (isEnglish) "Accept" else "Đồng ý", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
              }
            }
          }
        }
      }

      // 2. MÃ KẾT NỐI SET LOVE CỦA BẠN (Không hiển thị thông tin cá nhân ở đây, hồ sơ điều chỉnh ở mục Cài đặt)
      item {
        Card(
          shape = RoundedCornerShape(22.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = if (isEnglish) "YOUR CODE" else "MÃ CỦA BẠN",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF880E4F)
              )

              Surface(
                shape = RoundedCornerShape(8.dp),
                color = when (relationshipStatus) {
                  OnlineStatus.COUPLED -> Color(0xFFE8F5E9)
                  OnlineStatus.PENDING_INVITE -> Color(0xFFFFF3E0)
                  else -> Color(0xFFF5F5F5)
                }
              ) {
                Text(
                  text = when (relationshipStatus) {
                    OnlineStatus.COUPLED -> "❤️"
                    OnlineStatus.PENDING_INVITE -> "⏳"
                    else -> "🕊️"
                  },
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = when (relationshipStatus) {
                    OnlineStatus.COUPLED -> Color(0xFF2E7D32)
                    OnlineStatus.PENDING_INVITE -> Color(0xFFE65100)
                    else -> Color.Gray
                  },
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFFFFB6C1), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = currentUser.coupleCode,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                color = Color(0xFFD81B60),
                modifier = Modifier.testTag("my_couple_code_text")
              )

              Row {
                IconButton(
                  onClick = { copyToClipboard(currentUser.coupleCode, "Couple Code") },
                  modifier = Modifier.size(48.dp).testTag("copy_couple_code_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = if (isEnglish) "Copy code" else "Sao chép mã",
                    tint = Color(0xFFD81B60),
                    modifier = Modifier.size(20.dp)
                  )
                }

                IconButton(
                  onClick = {
                    val link = ProfileUtils.createShareLink(currentUser.coupleCode)
                    copyToClipboard(link, "Couple Link")
                  },
                  modifier = Modifier.size(48.dp).testTag("copy_couple_link_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = if (isEnglish) "Copy link" else "Sao chép liên kết",
                    tint = Color(0xFF0288D1),
                    modifier = Modifier.size(20.dp)
                  )
                }

                IconButton(
                  onClick = { shareCoupleLink(currentUser.coupleCode) },
                  modifier = Modifier.size(48.dp).testTag("share_couple_code_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = if (isEnglish) "Share code/link" else "Chia sẻ mã/liên kết",
                    tint = Color(0xFFD81B60),
                    modifier = Modifier.size(20.dp)
                  )
                }
              }
            }

          }
        }
      }

      // 3. TÌM KIẾM ĐỐI PHƯƠNG & SET LOVE (CHỈ HIỂN THỊ THÔNG TIN SET LOVE SAU KHI ĐÃ CHỌN NGƯỜI)
      when (relationshipStatus) {
        OnlineStatus.COUPLED -> {
          item {
            Card(
              shape = RoundedCornerShape(22.dp),
              colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F5)),
              border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFFF80AB)),
              modifier = Modifier.fillMaxWidth().testTag("coupled_partner_status_card")
            ) {
              Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "HỒ SƠ ĐỐI TÁC CỦA BẠN",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF880E4F)
                  )
                  Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE8F5E9)
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Check,
                      contentDescription = if (isEnglish) "Coupled" else "Đã kết đôi",
                      tint = Color(0xFF2E7D32),
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp).size(16.dp)
                    )
                  }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                  AsyncImage(
                    model = partnerUser?.avatarUrl?.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200" }
                      ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200",
                    contentDescription = "Partner Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                      .size(68.dp)
                      .clip(CircleShape)
                      .border(2.dp, Color(0xFFFF4081), CircleShape)
                  )

                  Spacer(modifier = Modifier.width(14.dp))

                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = partnerUser?.effectiveDisplayName ?: "Người ấy",
                      fontWeight = FontWeight.Bold,
                      fontSize = 18.sp,
                      color = Color(0xFF880E4F)
                    )
                    Text(
                      text = "Mã: " + (partnerUser?.coupleCode ?: ""),
                      fontSize = 13.sp,
                      color = Color.Gray
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      if ((partnerUser?.age ?: 0) > 0) {
                        Text(text = partnerUser?.age.toString() + " tuổi", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFC2185B))
                      }
                      if (!partnerUser?.zodiac.isNullOrBlank()) {
                        Text(text = "• Cung " + partnerUser?.zodiac, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF880E4F))
                      }
                    }
                  }
                }

                // Ngày kỷ niệm yêu thống nhất
                val loveDays by viewModel.loveDays.collectAsState()
                val anniversaryDate by viewModel.anniversaryDate.collectAsState()
                val memories by viewModel.sharedMemories.collectAsState()

                // Companionship Tier (Cấp độ đồng hành chuẩn)
                val tierTitle = when {
                  loveDays >= 730 -> "👑 Tri Kỷ Trọn Đời"
                  loveDays >= 365 -> "🏆 Đồng Hành Vững Bền"
                  loveDays >= 100 -> "💎 Tri Kỷ Đồng Điệu"
                  loveDays >= 30 -> "🌿 Gắn Kết Sâu Sắc"
                  else -> "🌱 Khởi Đầu Ngọt Ngào"
                }

                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = Color(0xFFFFEBEE),
                  border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCDD2)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = if (isEnglish) "Time together" else "Thời gian đồng hành", tint = Color(0xFFE91E63), modifier = Modifier.size(18.dp))
                      }
                      Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE91E63)
                      ) {
                        Text(
                          text = tierTitle,
                          fontSize = 11.sp,
                          fontWeight = FontWeight.Bold,
                          color = Color.White,
                          modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                      }
                    }

                    Text(
                      text = (if (isEnglish) "$loveDays days" else "$loveDays ngày") + " • $anniversaryDate 💕",
                      fontSize = 15.sp,
                      fontWeight = FontWeight.ExtraBold,
                      color = Color(0xFFD81B60)
                    )
                  }
                }

                // Bộ Tương Tác Đồng Hành Chuẩn & Tốt Nhất
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = Color.White,
                  border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCDD2)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      // Gửi nhịp đập yêu thương
                      Button(
                        onClick = {
                          viewModel.triggerFloatingHearts()
                          viewModel.showToast("💖 Đã gửi nhịp đập yêu thương tới " + (partnerUser?.effectiveDisplayName ?: "người ấy") + "!")
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                        modifier = Modifier.weight(1.1f).height(44.dp)
                      ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nhịp tim", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                      }

                      // Mở tab Kỷ Niệm (album ảnh và video)
                      OutlinedButton(
                        onClick = {
                          viewModel.setTab(1)
                          onNavigateBack()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.1f).height(44.dp)
                      ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = if (isEnglish) "Memories" else "Kỷ niệm", modifier = Modifier.size(15.dp), tint = Color(0xFF00897B))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${memories.size}", fontSize = 12.sp)
                      }
                    }
                  }
                }

                val rel by viewModel.activeRelationship.collectAsState()
                val pendingBreakup = rel?.status == RelationshipStatus.PENDING_BREAKUP
                val byMe = pendingBreakup && rel?.breakupRequestedBy == currentUser.uid
                var confirmBreakup by remember { mutableStateOf(false) }
                val dangerColors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))

                if (!pendingBreakup) {
                  TextButton(
                    onClick = { confirmBreakup = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFC62828)),
                    modifier = Modifier.fillMaxWidth().testTag("request_breakup_button")
                  ) {
                    Icon(Icons.Default.HeartBroken, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isEnglish) "End Set Love" else "Hủy Set Love", fontSize = 13.sp)
                  }
                } else {
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFEBEE),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      Text(
                        text = if (byMe) {
                          if (isEnglish) "⏳ Waiting for confirmation…" else "⏳ Đang chờ người ấy xác nhận…"
                        } else {
                          if (isEnglish) "Your partner wants to end Set Love. Agree?" else "Người ấy muốn hủy Set Love. Bạn đồng ý?"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFFB71C1C)
                      )
                      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                          onClick = { viewModel.rejectBreakup() },
                          shape = RoundedCornerShape(10.dp),
                          modifier = Modifier.weight(1f).testTag("reject_breakup_button")
                        ) { Text(if (byMe) "Rút lại" else "Từ chối", fontSize = 12.sp) }
                        Button(
                          onClick = { if (byMe) viewModel.forceBreakup() else viewModel.confirmBreakup() },
                          colors = dangerColors,
                          shape = RoundedCornerShape(10.dp),
                          modifier = Modifier.weight(1f).testTag("confirm_breakup_button")
                        ) { Text(if (byMe) "Hủy ngay" else "Xác nhận", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                      }
                    }
                  }
                }

                if (confirmBreakup) {
                  AlertDialog(
                    onDismissRequest = { confirmBreakup = false },
                    modifier = Modifier.animateDialogEntrance(),
                    shape = RoundedCornerShape(28.dp),
                    containerColor = Color.White,
                    title = { Text(if (isEnglish) "Request to end Set Love?" else "Gửi yêu cầu hủy Set Love?", fontWeight = FontWeight.Bold) },
                    text = { Text(if (isEnglish) "If partner agrees, both return to Single and shared memories are locked." else "Nếu người ấy đồng ý, hai bạn trở về Độc thân và kỷ niệm chung bị khóa.") },
                    confirmButton = {
                      Button(onClick = { confirmBreakup = false; viewModel.requestBreakup() }, colors = dangerColors) { Text(if (isEnglish) "Send Request" else "Gửi yêu cầu") }
                    },
                    dismissButton = { OutlinedButton(onClick = { confirmBreakup = false }) { Text(if (isEnglish) "Cancel" else "Hủy bỏ") } }
                  )
                }
              }
            }
          }
        }

        OnlineStatus.PENDING_INVITE -> {
          item {
            Card(
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
              border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFFFB74D)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Icon(
                  imageVector = Icons.Default.HourglassTop,
                  contentDescription = null,
                  tint = Color(0xFFFFA000),
                  modifier = Modifier.size(36.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                  text = if (isEnglish) "Waiting for reply…" else "Đang chờ phản hồi…",
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  textAlign = TextAlign.Center,
                  color = Color(0xFFE65100)
                )

                outgoingInvite?.let { outInv ->
                  val outDays = ProfileUtils.calculateLoveDays(outInv.proposedStartDate)
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = outInv.targetCoupleCode + " • " + outInv.proposedStartDateText + " (" + outDays + (if (isEnglish) " days)" else " ngày)"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.DarkGray
                  )
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                  onClick = { viewModel.cancelSentInvite() },
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.height(48.dp).testTag("cancel_sent_invite_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = if (isEnglish) "Cancel invite" else "Hủy lời mời",
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
          }
        }

        else -> {
          // SINGLE: Search by Code/Link & Send Set Love
          item {
            Card(
              shape = RoundedCornerShape(22.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCDD2)),
              modifier = Modifier.fillMaxWidth().testTag("search_and_pair_card")
            ) {
              Column(modifier = Modifier.padding(18.dp)) {
                Text(
                  text = if (isEnglish) "Find partner" else "Tìm người ấy",
                  fontWeight = FontWeight.Bold,
                  fontSize = 16.sp,
                  color = Color(0xFF880E4F)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Ô tìm kiếm & Nút tìm
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it.uppercase()) },
                    placeholder = { Text("LOVE-9966", fontSize = 14.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = pairingTextFieldColors(),
                    trailingIcon = {
                      if (searchQuery.isNotEmpty()) {
                        IconButton(
                          onClick = {
                            viewModel.updateSearchQuery("")
                            viewModel.clearSearch()
                          },
                          modifier = Modifier.size(28.dp)
                        ) {
                          Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = if (isEnglish) "Clear" else "Xóa",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
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
                                viewModel.updateSearchQuery(clipText.uppercase())
                                viewModel.performSearch()
                              } else {
                                viewModel.showToast(if (isEnglish) "Clipboard is empty" else "Bộ nhớ tạm trống")
                              }
                            }
                        ) {
                          Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
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
                              fontSize = 11.sp,
                              fontWeight = FontWeight.Bold,
                              color = Color(0xFFE91E63)
                            )
                          }
                        }
                      }
                    },
                    modifier = Modifier
                      .weight(1f)
                      .testTag("input_search_code_or_link")
                  )

                  Button(
                    onClick = { viewModel.performSearch() },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                    modifier = Modifier
                      .height(52.dp)
                      .testTag("btn_perform_search")
                  ) {
                    if (isSearching) {
                      CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                      ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Tìm", modifier = Modifier.size(18.dp))
                        Text(if (isEnglish) "Search" else "Tìm", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                      }
                    }
                  }
                }

                // THÔNG TIN SET LOVE CHỈ HIỂN THỊ SAU KHI ĐÃ CHỌN NGƯỜI
                searchedUser?.let { target ->
                  Spacer(modifier = Modifier.height(16.dp))

                  Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F5)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF4081)),
                    modifier = Modifier.fillMaxWidth().testTag("found_user_preview_card")
                  ) {
                    Column(
                      modifier = Modifier.padding(16.dp),
                      verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Surface(
                          shape = RoundedCornerShape(8.dp),
                          color = Color(0xFFE91E63)
                        ) {
                          Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = if (isEnglish) "Partner found, read only" else "Đã tìm thấy đối tác, chỉ đọc",
                            tint = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp).size(16.dp)
                          )
                        }

                        IconButton(
                          onClick = { viewModel.clearSearch() },
                          modifier = Modifier.size(48.dp)
                        ) {
                          Icon(imageVector = Icons.Default.Close, contentDescription = if (isEnglish) "Deselect" else "Bỏ chọn", tint = Color.Gray)
                        }
                      }

                      Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                          model = target.avatarUrl.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200" },
                          contentDescription = "Found user avatar",
                          contentScale = ContentScale.Crop,
                          modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFFFF4081), CircleShape)
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                          Text(
                            text = target.effectiveDisplayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF880E4F)
                          )
                          Text(
                            text = (if (isEnglish) "Code: " else "Mã: ") + target.coupleCode,
                            fontSize = 13.sp,
                            color = Color.Gray
                          )
                          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (target.age > 0) {
                              Text(text = com.example.ui.util.ProfileUtils.formatAgeDisplay(target.age, isEnglish), fontSize = 14.sp, color = Color(0xFFC2185B), fontWeight = FontWeight.SemiBold)
                            }
                            if (target.zodiac.isNotBlank()) {
                              Text(text = "• " + com.example.ui.util.ProfileUtils.formatZodiacDisplay(target.zodiac, isEnglish), fontSize = 14.sp, color = Color(0xFF880E4F), fontWeight = FontWeight.SemiBold)
                            }
                          }
                        }
                      }

                      if (target.bio.isNotBlank()) {
                        Text(
                          text = target.bio,
                          fontSize = 13.sp,
                          fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                          color = Color.DarkGray
                        )
                      }

                      HorizontalDivider(color = Color(0xFFFFCDD2))

                      // Date Picker chọn ngày bắt đầu yêu (thống nhất từ người tạo)
                      InLoveDatePickerField(
                        value = proposedStartDateText,
                        onValueChange = { proposedStartDateText = it },
                        label = if (isEnglish) "Start date *" else "Ngày bắt đầu yêu *",
                        placeholder = "dd/MM/yyyy",
                        dialogTitle = if (isEnglish) "Select love start date" else "Chọn ngày bắt đầu yêu",
                        quickPresets = DatePickerPresets.relationshipStartDatePresets(isEnglish),
                        isEnglish = isEnglish,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "input_proposed_start_date"
                      )

                      // TỰ ĐỘNG TÍNH SỐ NGÀY YÊU (KHÔNG NHẬP TAY)
                      Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFEBEE),
                        modifier = Modifier.fillMaxWidth()
                      ) {
                        Row(
                          modifier = Modifier.padding(12.dp),
                          verticalAlignment = Alignment.CenterVertically
                        ) {
                          Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFE91E63),
                            modifier = Modifier.size(20.dp)
                          )
                          Spacer(modifier = Modifier.width(8.dp))
                          Text(
                            text = if (isEnglish) "$calculatedDaysFromProposed days" else "$calculatedDaysFromProposed ngày",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD81B60)
                          )
                        }
                      }

                      // Lời nhắn gửi đối phương
                      OutlinedTextField(
                        value = loveNoteInput,
                        onValueChange = { loveNoteInput = it },
                        label = { Text(if (isEnglish) "Message" else "Lời nhắn") },
                        maxLines = 2,
                        shape = RoundedCornerShape(14.dp),
                        colors = pairingTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("input_love_note")
                      )

                      // Nút Gửi Lời Mời Set Love
                      Button(
                        onClick = {
                          val parsedDate = ProfileUtils.parseDateToMillis(proposedStartDateText)
                          viewModel.sendSetLoveInvite(
                            targetCodeOrLink = target.coupleCode,
                            proposedStartDateMillis = parsedDate,
                            loveNote = loveNoteInput
                          )
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                        modifier = Modifier
                          .fillMaxWidth()
                          .height(50.dp)
                          .testTag("send_set_love_to_target_button")
                      ) {
                        Icon(imageVector = Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                          text = if (isEnglish) "Send invite" else "Gửi lời mời",
                          fontWeight = FontWeight.Bold,
                          fontSize = 15.sp
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
      // 4. PERSONAL INTERESTS SECTION
      item {
        Card(
          shape = RoundedCornerShape(22.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCDD2)),
          modifier = Modifier.fillMaxWidth().testTag("pairing_interests_card")
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
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
                  imageVector = Icons.Default.AutoAwesome,
                  contentDescription = null,
                  tint = Color(0xFFE91E63),
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = if (isEnglish) "Interests" else "Sở thích",
                  fontWeight = FontWeight.Bold,
                  fontSize = 16.sp,
                  color = Color(0xFF880E4F)
                )
              }

              Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFFF0F5),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFC0D3))
              ) {
                Text(
                  text = "${currentUser.interests.size}/${OnlineCoupleRepository.AVAILABLE_INTERESTS.size}",
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFD81B60),
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = if (isEnglish) "Tap to select your favorite hobbies and discover mutual vibes" else "Chọn sở thích để cùng khám phá điểm tương đồng với người ấy",
              fontSize = 12.sp,
              color = Color.Gray
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              val myInterests = currentUser.interests
              OnlineCoupleRepository.AVAILABLE_INTERESTS.forEach { (key, label) ->
                val isSelected = myInterests.contains(key)
                val emoji = label.substringAfterLast(' ')
                val name = label.substringBeforeLast(' ')

                Surface(
                  shape = RoundedCornerShape(16.dp),
                  color = if (isSelected) Color(0xFFFFEBEE) else Color(0xFFF9F9FB),
                  border = androidx.compose.foundation.BorderStroke(
                    if (isSelected) 1.5.dp else 1.dp,
                    if (isSelected) Color(0xFFE91E63) else Color(0xFFEAEAEA)
                  ),
                  modifier = Modifier
                    .clickable { viewModel.toggleInterest(key) }
                    .testTag("interest_chip_$key")
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                  ) {
                    Text(text = emoji, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = name,
                      fontSize = 12.5.sp,
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                      color = if (isSelected) Color(0xFFC2185B) else Color(0xFF424242)
                    )
                    if (isSelected) {
                      Spacer(modifier = Modifier.width(5.dp))
                      Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFFE91E63),
                        modifier = Modifier.size(13.dp)
                      )
                    }
                  }
                }
              }
            }

            val pickedNames = OnlineCoupleRepository.AVAILABLE_INTERESTS
              .filter { currentUser.interests.contains(it.first) }
              .joinToString(" · ") { it.second.substringBeforeLast(' ') }
            if (pickedNames.isNotEmpty()) {
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "Đã chọn: $pickedNames",
                fontSize = 12.sp,
                color = Color(0xFFC2185B),
                fontWeight = FontWeight.SemiBold
              )
            }

            if (mutualInterests.isNotEmpty()) {
              Spacer(modifier = Modifier.height(12.dp))
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFFFF3E0),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCC80)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("💖", fontSize = 16.sp)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = if (isEnglish) "Mutual interests: ${mutualInterests.joinToString(", ")}" else "Điểm chung ngọt ngào: ${mutualInterests.joinToString(", ")}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = Color(0xFFE65100)
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  // DIALOGS:
  // 1. Edit My Profile Dialog
  val dynamicPresetAvatars by viewModel.presetAvatars.collectAsState()
  if (showEditProfileDialog) {
    EditMyProfileDialog(
      currentUser = currentUser,
      presetAvatars = dynamicPresetAvatars,
      isEnglish = isEnglish,
      canEditPhoto = currentUser.uid.isNotBlank(),
      onPhotoPicked = { viewModel.saveLocalAvatar(it, false) },
      onPhotoDiscarded = { viewModel.deleteUnsavedAvatar(it) },
      onDismiss = { viewModel.closeEditProfileDialog() },
      onSave = { name, birthDate, avatarUrl, gender, bio ->
        viewModel.updateMyProfile(name, birthDate, avatarUrl, gender, bio)
      }
    )
  }

  // 2. Identity & Date Verification Dialog for Incoming Invite
  selectedInviteForVerification?.let { inv ->
    VerifyInviteDialog(
      invite = inv,
      isEnglish = isEnglish,
      onDismiss = { viewModel.dismissInspectInvite() },
      onAccept = { confirmedDateMillis ->
        viewModel.acceptSetLoveInvite(
          inviteId = inv.inviteId,
          confirmedStartDateMillis = confirmedDateMillis
        )
      },
      onReject = {
        viewModel.rejectSetLoveInvite(inv.inviteId)
      }
    )
  }
}
