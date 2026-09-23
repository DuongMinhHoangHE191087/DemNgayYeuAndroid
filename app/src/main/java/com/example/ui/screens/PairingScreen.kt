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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerifiedUser
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
            text = if (isEnglish) "1-1 Couple Pairing (Set Love)" else "Ghép Đôi 1-1 (Set Love)",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
          )
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        ),
        actions = {
          IconButton(
            onClick = { shareCoupleLink(currentUser.coupleCode) },
            modifier = Modifier.padding(end = 4.dp).testTag("topbar_share_couple_code_button")
          ) {
            Icon(
              imageVector = Icons.Default.Share,
              contentDescription = if (isEnglish) "Share couple code" else "Chia sẻ mã ghép đôi",
              tint = Color(0xFFE91E63)
            )
          }
        }
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
      if (incomingInvite != null) {
        val invite = incomingInvite!!
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
                  text = "Lời Mời Set Love Đang Chờ Duyệt!",
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
                    Text(
                      text = "Hồ sơ đối tác • Chỉ đọc ✓",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = Color(0xFFC2185B),
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                    Text(text = "Ngày bắt đầu yêu thống nhất:", fontSize = 13.sp, color = Color.DarkGray)
                    Text(text = incomingDateText, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC2185B))
                  }
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(text = "Số ngày yêu tính tự động:", fontSize = 13.sp, color = Color.DarkGray)
                    Text(text = "$incomingDays ngày bên nhau 💕", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFD81B60))
                  }
                  if (invite.loveNote.isNotBlank()) {
                    Text(
                      text = """ + invite.loveNote + """,
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
                  Text("Đồng ý Set Love ❤️", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                text = "MÃ KẾT NỐI SET LOVE CỦA BẠN",
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
                    OnlineStatus.COUPLED -> "ĐÃ CÓ ĐÔI ❤️"
                    OnlineStatus.PENDING_INVITE -> "CHỜ GHÉP ĐÔI ⏳"
                    else -> "CHƯA KẾT NỐI 🕊️"
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
                  modifier = Modifier.size(36.dp).testTag("copy_couple_code_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    tint = Color(0xFFD81B60),
                    modifier = Modifier.size(20.dp)
                  )
                }

                IconButton(
                  onClick = {
                    val link = ProfileUtils.createShareLink(currentUser.coupleCode)
                    copyToClipboard(link, "Couple Link")
                  },
                  modifier = Modifier.size(36.dp).testTag("copy_couple_link_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = "Copy link",
                    tint = Color(0xFF0288D1),
                    modifier = Modifier.size(20.dp)
                  )
                }

                IconButton(
                  onClick = { shareCoupleLink(currentUser.coupleCode) },
                  modifier = Modifier.size(36.dp).testTag("share_couple_code_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share code/link",
                    tint = Color(0xFFD81B60),
                    modifier = Modifier.size(20.dp)
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color.White.copy(alpha = 0.8f),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.Settings,
                  contentDescription = null,
                  tint = Color(0xFF5C6BC0),
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Hồ sơ của bạn được điều chỉnh trong mục Cài đặt.",
                  fontSize = 12.sp,
                  color = Color(0xFF616161)
                )
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
                    Text(
                      text = "ĐÃ KẾT ĐÔI 1-1 ✓",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFF2E7D32),
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
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

                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = Color.White,
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(
                    text = "🔒 Thông tin đối tác là chỉ đọc, không thể điều chỉnh tại đây.",
                    fontSize = 12.sp,
                    color = Color(0xFF757575),
                    modifier = Modifier.padding(10.dp)
                  )
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
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFE91E63), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                          text = "Thời Gian Đồng Hành:",
                          fontSize = 13.sp,
                          fontWeight = FontWeight.SemiBold,
                          color = Color(0xFF880E4F)
                        )
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
                      text = "$loveDays ngày bên nhau • Bắt đầu: $anniversaryDate 💕",
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
                    Text(
                      text = "TƯƠNG TÁC ĐỒNG HÀNH TRỰC TIẾP",
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFF880E4F),
                      letterSpacing = 0.5.sp
                    )

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
                        Text("Gửi nhịp tim", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                      }

                      // Mở Album Cloudinary
                      OutlinedButton(
                        onClick = {
                          viewModel.setTab(1)
                          onNavigateBack()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.1f).height(44.dp)
                      ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF00897B))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kỷ niệm (${memories.size})", fontSize = 12.sp)
                      }
                    }
                  }
                }

                OutlinedButton(
                  onClick = {
                    viewModel.setTab(4)
                    onNavigateBack()
                  },
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text("Quản lý trạng thái Set Love trong Cài đặt")
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
                  text = "Đang chờ đối phương xác nhận lời mời Set Love...",
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  textAlign = TextAlign.Center,
                  color = Color(0xFFE65100)
                )

                if (outgoingInvite != null) {
                  val outDays = ProfileUtils.calculateLoveDays(outgoingInvite!!.proposedStartDate)
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = "Gửi tới: " + outgoingInvite!!.targetCoupleCode + " • Ngày yêu đề xuất: " + outgoingInvite!!.proposedStartDateText + " (" + outDays + " ngày)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.DarkGray
                  )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                  text = "Khi người ấy mở ứng dụng và bấm Đồng ý, hai bạn sẽ ngay lập tức được kết đôi 1-1!",
                  fontSize = 12.sp,
                  color = Color.DarkGray,
                  textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                  onClick = { viewModel.cancelSentInvite() },
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.testTag("cancel_sent_invite_button")
                ) {
                  Text("Hủy lời mời đã gửi")
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
                  text = "Tìm Kiếm Người Ấy Để Set Love",
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
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Mã hoặc link người ấy (vd: LOVE-9966)...", fontSize = 14.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = pairingTextFieldColors(),
                    modifier = Modifier
                      .weight(1f)
                      .testTag("input_search_code_or_link")
                  )

                  Button(
                    onClick = { viewModel.performSearch() },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    modifier = Modifier.testTag("btn_perform_search")
                  ) {
                    if (isSearching) {
                      CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                      Icon(imageVector = Icons.Default.Search, contentDescription = "Tìm")
                    }
                  }
                }

                // THÔNG TIN SET LOVE CHỈ HIỂN THỊ SAU KHI ĐÃ CHỌN NGƯỜI
                if (searchedUser != null) {
                  val target = searchedUser!!
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
                          Text(
                            text = if (isEnglish) "FOUND PARTNER • READ ONLY" else "ĐÃ TÌM THẤY ĐỐI TÁC • CHỈ ĐỌC",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                          )
                        }

                        IconButton(
                          onClick = { viewModel.clearSearch() },
                          modifier = Modifier.size(28.dp)
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
                              Text(text = if (isEnglish) "${target.age} yrs" else "${target.age} tuổi", fontSize = 14.sp, color = Color(0xFFC2185B), fontWeight = FontWeight.SemiBold)
                            }
                            if (target.zodiac.isNotBlank()) {
                              Text(text = if (isEnglish) "• Zodiac " + target.zodiac else "• Cung " + target.zodiac, fontSize = 14.sp, color = Color(0xFF880E4F), fontWeight = FontWeight.SemiBold)
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

                      Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth()
                      ) {
                        Text(
                          text = if (isEnglish) "🔒 Partner profile is read-only here." else "🔒 Hồ sơ người ấy là chỉ đọc, không thể chỉnh sửa tại đây.",
                          fontSize = 12.sp,
                          color = Color(0xFF757575),
                          modifier = Modifier.padding(8.dp)
                        )
                      }

                      HorizontalDivider(color = Color(0xFFFFCDD2))

                      // THIẾT LẬP KỶ NIỆM YÊU (THỐNG NHẤT TỪ NGƯỜI TẠO)
                      Text(
                        text = if (isEnglish) "Set Love Start Date:" else "Thiết Lập Ngày Bắt Đầu Yêu:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF880E4F)
                      )

                      // Date Picker chọn ngày bắt đầu yêu
                      InLoveDatePickerField(
                        value = proposedStartDateText,
                        onValueChange = { proposedStartDateText = it },
                        label = if (isEnglish) "Love start date (dd/MM/yyyy) *" else "Ngày bắt đầu yêu (dd/MM/yyyy) *",
                        placeholder = "dd/MM/yyyy",
                        dialogTitle = if (isEnglish) "Select love start date" else "Chọn ngày bắt đầu yêu",
                        quickPresets = DatePickerPresets.relationshipStartDatePresets(),
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
                            text = if (isEnglish) "✨ Calculated until today: $calculatedDaysFromProposed days together 💕" else "✨ Tính đến hôm nay: $calculatedDaysFromProposed ngày yêu nhau 💕",
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
                        label = { Text(if (isEnglish) "Message to partner (optional)" else "Lời nhắn gửi người ấy (tùy chọn)") },
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
                          text = if (isEnglish) "Send Set Love Invite to ${target.effectiveDisplayName} ❤️" else "Gửi Lời Mời Set Love Cho " + target.effectiveDisplayName + " ❤️",
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
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "Sở Thích Cá Nhân",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
              )
              Spacer(modifier = Modifier.weight(1f))
              Text(
                text = "Gợi ý quà tặng",
                fontSize = 11.sp,
                color = Color(0xFFE91E63),
                fontWeight = FontWeight.SemiBold
              )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Hệ thống sẽ lấy giao điểm sở thích của cả hai để gợi ý quà và lịch hẹn phù hợp nhất khi kết đôi.",
              fontSize = 11.sp,
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
                FilterChip(
                  selected = isSelected,
                  onClick = { viewModel.toggleInterest(key) },
                  label = { Text(text = label, fontSize = 12.sp) },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFFFEBEE),
                    selectedLabelColor = Color(0xFFC2185B)
                  )
                )
              }
            }

            if (mutualInterests.isNotEmpty()) {
              Spacer(modifier = Modifier.height(12.dp))
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF3E5F5),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Text(
                    text = "✨ Sở thích chung của hai bạn: ${mutualInterests.joinToString(", ")}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color(0xFF6A1B9A)
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
      onDismiss = { viewModel.closeEditProfileDialog() },
      onSave = { name, birthDate, avatarUrl, gender, bio ->
        viewModel.updateMyProfile(name, birthDate, avatarUrl, gender, bio)
      }
    )
  }

  // 2. Identity & Date Verification Dialog for Incoming Invite
  if (selectedInviteForVerification != null) {
    VerifyInviteDialog(
      invite = selectedInviteForVerification!!,
      onDismiss = { viewModel.dismissInspectInvite() },
      onAccept = { confirmedDateMillis ->
        viewModel.acceptSetLoveInvite(
          inviteId = selectedInviteForVerification!!.inviteId,
          confirmedStartDateMillis = confirmedDateMillis
        )
      },
      onReject = {
        viewModel.rejectSetLoveInvite(selectedInviteForVerification!!.inviteId)
      }
    )
  }
}
